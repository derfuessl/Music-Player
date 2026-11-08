package org.fossify.musicplayer.playback.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MediaType
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.MoreExecutors
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.helpers.TAB_ALBUMS
import org.fossify.musicplayer.helpers.TAB_ARTISTS
import org.fossify.musicplayer.helpers.TAB_FOLDERS
import org.fossify.musicplayer.helpers.TAB_GENRES
import org.fossify.musicplayer.helpers.TAB_PLAYLISTS
import org.fossify.musicplayer.helpers.TAB_TRACKS
import org.fossify.musicplayer.models.QueueItem
import org.fossify.musicplayer.models.toMediaItems
import java.util.concurrent.Executors

private const val STATE_CREATED = 1
private const val STATE_INITIALIZING = 2
private const val STATE_INITIALIZED = 3
private const val STATE_ERROR = 4

private const val SMP_ROOT_ID = "__ROOT__"
private const val SMP_PLAYLISTS_ROOT_ID = "__PLAYLISTS__"
private const val SMP_FOLDERS_ROOT_ID = "__FOLDERS__"
private const val SMP_ARTISTS_ROOT_ID = "__ARTISTS__"
private const val SMP_ALBUMS_ROOT_ID = "__ALBUMS__"
private const val SMP_TRACKS_ROOT_ID = "__TRACKS__"
private const val SMP_GENRES_ROOT_ID = "__GENRES__"

/**
 * This is not (yet) used internally and currently only required (mostly) for media browser's outside the app.
 */
@UnstableApi
internal class MediaItemProvider(private val context: Context) {
    private val executor by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private class MediaItemNode(val item: MediaItem) {
        private val children: MutableList<MediaItem> = ArrayList()
        fun addChild(child: MediaItem) = children.add(child)
        fun getChildren() = children.toList()
    }

    private class LibraryTree {
        val treeNodes = mutableMapOf<String, MediaItemNode>()
        val titleMap = mutableMapOf<String, MediaItemNode>()
    }

    // Build a complete replacement on the library worker. Controller threads keep reading
    // the previous snapshot until publication, including while a playlist is being deleted.
    @Volatile private var tree = LibraryTree().apply { buildRoot() }
    @Volatile private var generation = 0L
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    @Volatile private var state = STATE_CREATED
    private val audioHelper = context.audioHelper

    fun whenReady(performAction: (Boolean) -> Unit): Boolean {
        val ready = synchronized(onReadyListeners) {
            if (state == STATE_CREATED || state == STATE_INITIALIZING) {
                onReadyListeners.add(performAction)
                return false
            }
            state == STATE_INITIALIZED
        }
        performAction(ready)
        return true
    }

    private fun finishLoad(request: Long, replacement: LibraryTree?) {
        // Drain once. Keeping these closures across rescans retains controllers and
        // dispatches a growing number of obsolete callbacks on every library update.
        val pending = synchronized(onReadyListeners) {
            if (generation != request) return
            if (replacement != null) tree = replacement
            state = if (replacement != null) STATE_INITIALIZED else STATE_ERROR
            onReadyListeners.toList().also { onReadyListeners.clear() }
        }
        Handler(Looper.getMainLooper()).post { pending.forEach { it(replacement != null) } }
    }

    operator fun get(mediaId: String): MediaItem? {
        val mediaItem = getNode(mediaId)?.item
        if (mediaItem == null) {
            // assume it's a track
            val mediaStoreId = mediaId.toLongOrNull() ?: return null
            return audioHelper.getTrack(mediaStoreId)?.toMediaItem()
        }

        return mediaItem
    }

    fun getRootItem() = get(SMP_ROOT_ID)!!

    fun getChildren(id: String) = getNode(id)?.getChildren()

    fun getRandomItem() = getDefaultQueue()?.randomOrNull()

    fun getItemFromSearch(searchQuery: String): MediaItem? {
        val titleMap = tree.titleMap
        var mediaItem = titleMap[searchQuery]?.item
        if (mediaItem == null) {
            val partialMatch = titleMap.keys.find { it.contains(searchQuery) } ?: return null
            mediaItem = titleMap[partialMatch]?.item
        }

        return mediaItem
    }

    fun getDefaultQueue() = getChildren(SMP_TRACKS_ROOT_ID)

    fun getRecentItemsLazily(callback: (itemsWithStartPosition: MediaItemsWithStartPosition) -> Unit) {
        if (state == STATE_INITIALIZED) {
            callback(getRecentItemsWithStartPosition())
        } else {
            audioHelper.getQueuedTracksLazily { tracks, startIndex, startPositionMs ->
                callback(MediaItemsWithStartPosition(tracks.toMediaItems(), startIndex, startPositionMs))
            }
        }
    }

    fun getRecentItemsWithStartPosition(): MediaItemsWithStartPosition {
        val recentItems = context.queueDAO.getAll().mapNotNull { getMediaItemFromQueueItem(it) }
        var startPosition = 0L
        val currentItem = context.queueDAO.getCurrent()?.let {
            val mediaItem = getMediaItemFromQueueItem(it) ?: return@let null
            val metadata = mediaItem.mediaMetadata.buildUpon().build()
            startPosition = it.lastPosition * 1000L
            mediaItem.buildUpon().setMediaMetadata(metadata).build()
        }

        val startIndex = maxOf(recentItems.indexOf(currentItem), 0)
        return MediaItemsWithStartPosition(recentItems, startIndex, startPosition)
    }

    fun saveRecentItemsWithStartPosition(mediaItems: List<MediaItem>, current: MediaItem, startPosition: Long) {
        if (mediaItems.isEmpty()) {
            return
        }

        executor.execute {
            val trackId = current.mediaId.toLong()
            val queueItems = mediaItems.mapIndexed { index, mediaItem ->
                QueueItem(trackId = mediaItem.mediaId.toLong(), trackOrder = index, isCurrent = false, lastPosition = 0)
            }

            audioHelper.resetQueue(queueItems, trackId, startPosition)
        }
    }

    fun reload() {
        val request = synchronized(onReadyListeners) {
            state = STATE_INITIALIZING
            ++generation
        }
        executor.execute {
            if (generation != request) return@execute
            val replacement = LibraryTree()
            try {
                replacement.buildRoot()
                replacement.buildPlaylists()
                replacement.buildFolders()
                replacement.buildArtists()
                replacement.buildAlbums()
                replacement.buildTracks()
                replacement.buildGenres()
            } catch (e: Exception) {
                finishLoad(request, null)
                return@execute
            }
            finishLoad(request, replacement)
        }
    }

    private fun LibraryTree.buildRoot() {
        val root = buildMediaItem(
            title = context.getString(org.fossify.commons.R.string.root),
            mediaId = SMP_ROOT_ID,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
        val rootChildren = RootCategories.buildRootChildren(context)
        addNodeAndChildren(item = root, children = rootChildren)
    }

    private fun LibraryTree.buildPlaylists() = with(audioHelper) {
        for (batch in getAllPlaylists().chunked(128)) {
            val tracksById = getPlaylistTracksById(batch.map { it.id })
            for (playlist in batch) {
                addNodeAndChildren(SMP_PLAYLISTS_ROOT_ID, playlist.toMediaItem(), tracksById[playlist.id].orEmpty().map { it.toMediaItem() })
            }
        }
    }

    private fun LibraryTree.buildFolders() = with(audioHelper) {
        getAllFolders().forEach { folder ->
            addNodeAndChildren(SMP_FOLDERS_ROOT_ID, folder.toMediaItem(), getFolderTracks(folder.title).map { it.toMediaItem() })
        }
    }

    private fun LibraryTree.buildArtists() = with(audioHelper) {
        getAllArtists().forEach { artist ->
            addNodeAndChildren(SMP_ARTISTS_ROOT_ID, artist.toMediaItem(), getArtistAlbums(artist.id).map { it.toMediaItem() })
        }
    }

    private fun LibraryTree.buildAlbums() = with(audioHelper) {
        getAllAlbums().forEach { album ->
            addNodeAndChildren(SMP_ALBUMS_ROOT_ID, album.toMediaItem(), getAlbumTracks(album.id).map { it.toMediaItem() })
        }
    }

    private fun LibraryTree.buildTracks() = with(audioHelper) {
        getAllTracks().forEach { track ->
            addNodeAndChildren(SMP_TRACKS_ROOT_ID, track.toMediaItem())
            titleMap[track.title.lowercase()] = MediaItemNode(track.toMediaItem())
        }
    }

    private fun LibraryTree.buildGenres() = with(audioHelper) {
        getAllGenres().forEach { genre ->
            addNodeAndChildren(SMP_GENRES_ROOT_ID, genre.toMediaItem(), getGenreTracks(genre.id).map { it.toMediaItem() })
        }
    }

    private fun getNode(id: String) = tree.treeNodes[id]

    private fun LibraryTree.addNodeAndChildren(parentId: String? = null, item: MediaItem, children: List<MediaItem>? = null) {
        val itemNode = MediaItemNode(item)
        treeNodes[item.mediaId] = itemNode

        children?.forEach { child ->
            treeNodes[child.mediaId] = MediaItemNode(child)
            itemNode.addChild(child)
        }

        if (parentId != null) {
            treeNodes[parentId]?.addChild(item)
        }
    }

    private fun getMediaItemFromQueueItem(queueItem: QueueItem) = getNode(queueItem.trackId.toString())?.item
}

private enum class RootCategories(@StringRes val titleRes: Int, @DrawableRes val drawableRes: Int, val mediaId: String, val mediaType: @MediaType Int) {
    PLAYLISTS(R.string.playlists, R.drawable.ic_playlist_vector, SMP_PLAYLISTS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
    FOLDERS(R.string.folders, R.drawable.ic_folders_vector, SMP_FOLDERS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
    ARTISTS(R.string.artists, org.fossify.commons.R.drawable.ic_person_vector, SMP_ARTISTS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
    ALBUMS(R.string.albums, R.drawable.ic_album_vector, SMP_ALBUMS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
    TRACKS(R.string.tracks, R.drawable.ic_music_note_vector, SMP_TRACKS_ROOT_ID, MediaMetadata.MEDIA_TYPE_PLAYLIST),
    GENRES(R.string.genres, R.drawable.ic_genre_vector, SMP_GENRES_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES);

    companion object {
        fun buildRootChildren(context: Context): List<MediaItem> {
            val rootChildren = mutableListOf<MediaItem>()
            values().forEach {
                val flag = getTabFlag(it.mediaId)
                if (context.isTabVisible(flag)) {
                    rootChildren += buildMediaItem(
                        title = context.getString(it.titleRes),
                        artworkUri = buildDrawableUri(context, it.drawableRes),
                        mediaId = it.mediaId,
                        mediaType = it.mediaType
                    )
                }
            }

            return rootChildren
        }

        private fun getTabFlag(mediaId: String): Int {
            return when (mediaId) {
                SMP_PLAYLISTS_ROOT_ID -> TAB_PLAYLISTS
                SMP_FOLDERS_ROOT_ID -> TAB_FOLDERS
                SMP_ARTISTS_ROOT_ID -> TAB_ARTISTS
                SMP_ALBUMS_ROOT_ID -> TAB_ALBUMS
                SMP_TRACKS_ROOT_ID -> TAB_TRACKS
                SMP_GENRES_ROOT_ID -> TAB_GENRES
                else -> throw IllegalArgumentException("Invalid media id: $mediaId")
            }
        }

        private fun buildDrawableUri(context: Context, drawableRes: Int): Uri? {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.resources.getResourcePackageName(drawableRes))
                .appendPath(context.resources.getResourceTypeName(drawableRes))
                .appendPath(context.resources.getResourceEntryName(drawableRes))
                .build()
        }
    }
}
