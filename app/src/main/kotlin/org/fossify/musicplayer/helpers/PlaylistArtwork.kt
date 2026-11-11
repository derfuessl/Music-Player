package org.fossify.musicplayer.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.util.LruCache
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.tracksDAO
import org.fossify.musicplayer.extensions.playlistDAO
import org.fossify.musicplayer.models.Track
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Playlist-only artwork. MP3 files are read, never edited. An explicit choice is copied to private
 * app storage, so it survives deletion/movement of the original image or song. Automatic collages
 * remain derived data; changing playlist membership does not require a database migration.
 *
 * Decoding and database access happen on workers. The small, byte-bounded cache contains immutable
 * bitmaps (including negative results), not Activities. Recycled rows cancel queued work and only
 * accept the result of their latest request, preventing wrong covers after fast scrolling.
 */
object PlaylistArtwork {
    private const val SIZE = 512
    private data class CachedImage(val bitmap: Bitmap?)
    private class Request(var future: Future<*>? = null)
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private val filesLock = Any()
    private val cache = object : LruCache<String, CachedImage>(12 * 1024 * 1024) {
        // A missing picture still retains a map entry and its key. Account for those bytes,
        // otherwise millions of misses could fit into the nominal 12 MiB budget.
        override fun sizeOf(key: String, value: CachedImage) = 128 + 2 * key.length + (value.bitmap?.allocationByteCount ?: 0)
    }

    // Separate small launcher images from 512px album art. Rebinding widgets after rotation or
    // a home-screen page change can now skip the track query, file stats and collage decoding.
    private val widgetCache = object : LruCache<Long, CachedImage>(6 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: CachedImage) = 64 + (value.bitmap?.allocationByteCount ?: 0)
    }
    private val widgetGeneration = AtomicLong()

    fun invalidateWidgetThumbnails() {
        widgetGeneration.incrementAndGet()
        widgetCache.evictAll()
    }

    fun widgetThumbnail(context: Context, playlistId: Int): Bitmap? {
        val key = (widgetGeneration.get() shl 32) or (playlistId.toLong() and 0xffffffffL)
        widgetCache.get(key)?.let { return it.bitmap }
        val image = loadPlaylist(context, playlistId)?.let { Bitmap.createScaledBitmap(it, 128, 128, true) }
        if (!Thread.currentThread().isInterrupted) widgetCache.put(key, CachedImage(image))
        return image
    }

    fun invalidate() {
        invalidateWidgetThumbnails()
        generation.incrementAndGet()
        cache.evictAll()
    }

    private fun coverFile(context: Context, playlistId: Int): AtomicFile {
        require(playlistId > 0)
        return AtomicFile(File(context.filesDir, "playlist_covers/$playlistId.jpg"))
    }

    fun hasCustom(context: Context, playlistId: Int): Boolean = synchronized(filesLock) {
        exists(coverFile(context, playlistId))
    }

    private fun exists(file: AtomicFile) = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    /** Must run off the main thread. AtomicFile retains the previous image if encoding fails. */
    fun save(context: Context, playlistId: Int, bitmap: Bitmap) = synchronized(filesLock) {
        check(!Thread.currentThread().isInterrupted) { "Image selection cancelled" }
        check(context.playlistDAO.getPlaylistWithId(playlistId) != null) { "Playlist no longer exists" }
        val file = coverFile(context, playlistId)
        file.baseFile.parentFile!!.mkdirs()
        val stream = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "Image could not be saved" }
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        invalidate()
    }

    fun reset(context: Context, playlistId: Int) = synchronized(filesLock) {
        check(!Thread.currentThread().isInterrupted) { "Image selection cancelled" }
        val file = coverFile(context, playlistId)
        file.delete()
        check(!exists(file)) { "Image could not be removed" }
        invalidate()
    }

    fun displayPlaylist(view: ImageView, playlistId: Int, placeholder: Drawable) {
        val app = view.context.applicationContext
        display(view, placeholder) { loadPlaylist(app, playlistId) }
    }

    fun displayTrack(view: ImageView, track: Track, placeholder: Drawable) {
        val app = view.context.applicationContext
        display(view, placeholder) { loadTrack(app, track) }
    }

    fun cancel(view: ImageView) {
        val request = view.getTag(R.id.playlist_artwork_request) as? Request
        request?.future?.let {
            it.cancel(true)
            if (it is Runnable) executor.remove(it)
        }
        view.setTag(R.id.playlist_artwork_request, null)
    }

    private fun display(view: ImageView, placeholder: Drawable, load: () -> Bitmap?) {
        cancel(view)
        view.setImageDrawable(placeholder)
        val request = Request()
        val target = WeakReference(view)
        view.setTag(R.id.playlist_artwork_request, request)
        request.future = executor.submit {
            val bitmap = try { load() } catch (ignored: Exception) { null }
            if (!Thread.currentThread().isInterrupted) mainHandler.post {
                target.get()?.let { image ->
                    if (image.getTag(R.id.playlist_artwork_request) === request && bitmap != null) {
                        image.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    /** For a picked image, Glide performs downsampling, EXIF rotation and center-cropping. */
    fun loadImage(context: Context, uri: Uri): Bitmap? = decode(context, uri)

    private fun decode(context: Context, source: Any): Bitmap? {
        val target = Glide.with(context.applicationContext).asBitmap().load(source)
            // Our cache includes modification times. Glide's default File key is only the path,
            // which would otherwise return the old image after replacing a custom cover in place.
            .skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
            .override(SIZE, SIZE).centerCrop().submit()
        return try {
            // Glide owns the returned resource. Copy before clearing the request so the cache,
            // collage canvas or save operation never holds a bitmap recycled by Glide.
            target.get().copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (ignored: Exception) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        } finally {
            Glide.with(context.applicationContext).clear(target)
        }
    }

    private fun trackKey(track: Track): String {
        val file = File(track.path)
        return "${track.mediaStoreId}:${track.path}:${file.lastModified()}:${file.length()}:${track.coverArt}"
    }

    /** Prefer the image embedded in this exact file, not a shared MediaStore album thumbnail. */
    fun loadTrack(context: Context, track: Track): Bitmap? {
        val key = "${generation.get()}:track:${trackKey(track)}"
        cache.get(key)?.let { return it.bitmap }
        if (Thread.currentThread().isInterrupted) return null
        val retriever = MediaMetadataRetriever()
        val embedded = try {
            retriever.setDataSource(context, track.getUri())
            retriever.embeddedPicture?.takeIf { it.size <= 20 * 1024 * 1024 }?.let { decode(context, it) }
        } catch (ignored: Exception) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        } finally {
            try { retriever.release() } catch (ignored: Exception) { }
        }
        val bitmap = embedded ?: if (track.coverArt.isNotEmpty() && !Thread.currentThread().isInterrupted) {
            decode(context, Uri.parse(track.coverArt))
        } else null
        if (!Thread.currentThread().isInterrupted) cache.put(key, CachedImage(bitmap))
        return bitmap
    }

    /** Called on a worker. Sorting by stable track identity keeps collages stable across UI sorts. */
    fun loadPlaylist(context: Context, playlistId: Int): Bitmap? {
        val epoch = generation.get()
        val custom = try { synchronized(filesLock) {
            val file = coverFile(context, playlistId)
            if (exists(file)) {
                // openRead first lets AtomicFile recover a previous image after an interrupted write.
                file.openRead().close()
                val key = "$epoch:custom:$playlistId:${file.baseFile.lastModified()}:${file.baseFile.length()}"
                cache.get(key)?.bitmap ?: decode(context, file.baseFile)?.also { cache.put(key, CachedImage(it)) }
            } else null
        } } catch (ignored: Exception) { null }
        if (custom != null) return custom
        val tracks = context.tracksDAO.getTracksFromPlaylist(playlistId).sortedWith(compareBy({ it.mediaStoreId }, { it.path }))
        // Hash the track metadata incrementally. A cached playlist key should occupy the same
        // space whether it describes ten songs or ten thousand. NUL separates filesystem paths.
        val digest = MessageDigest.getInstance("SHA-256")
        for (track in tracks) {
            if (Thread.currentThread().isInterrupted) return null
            digest.update(trackKey(track).toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        val key = "$epoch:playlist:$playlistId:${android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP)}"
        cache.get(key)?.let { return it.bitmap }
        val images = CoverMosaic.firstDistinct(tracks.asSequence().takeWhile { !Thread.currentThread().isInterrupted }
            .mapNotNull { loadTrack(context, it) }) { fingerprint(it) }
        if (Thread.currentThread().isInterrupted) return null
        val bitmap = mosaic(images)
        cache.put(key, CachedImage(bitmap))
        return bitmap
    }

    private fun fingerprint(bitmap: Bitmap): String {
        // Hash normalized pixels rather than JPEG bytes: the same embedded image may have different
        // tags/encodings in several MP3s. Repeated album covers should occupy only one tile.
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        if (small !== bitmap) small.recycle()
        val bytes = ByteBuffer.allocate(pixels.size * 4)
        pixels.forEach { bytes.putInt(it) }
        return MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    }

    private fun mosaic(images: List<Bitmap>): Bitmap? {
        if (images.isEmpty()) return null
        if (images.size == 1) return images.first()
        val result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        CoverMosaic.cells(images.size).forEachIndexed { index, cell ->
            val target = RectF(cell.left * SIZE, cell.top * SIZE, cell.right * SIZE, cell.bottom * SIZE)
            val source = images[index]
            val scale = maxOf(target.width() / source.width, target.height() / source.height)
            val width = source.width * scale
            val height = source.height * scale
            canvas.save()
            canvas.clipRect(target)
            canvas.drawBitmap(source, null, RectF(target.centerX() - width / 2, target.centerY() - height / 2,
                target.centerX() + width / 2, target.centerY() + height / 2), paint)
            canvas.restore()
        }
        return result
    }
}
