package org.fossify.musicplayer.activities

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.helpers.EXTRA_NEXT_MEDIA_ID
import org.fossify.musicplayer.helpers.SimpleMediaController
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.models.toMediaItems
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.playback.PlaybackService.Companion.updatePlaybackInfo
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * Base class for activities that want to control the [Player].
 */
abstract class SimpleControllerActivity : SimpleActivity(), Player.Listener {
    private lateinit var controller: SimpleMediaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = SimpleMediaController.getInstance(this)
        maybePreparePlayer()
    }

    override fun onStart() {
        super.onStart()
        controller.addListener(this)
    }

    override fun onStop() {
        super.onStop()
        controller.removeListener(this)
    }

    override fun onResume() {
        super.onResume()
        maybePreparePlayer()
    }

    open fun onPlayerPrepared(success: Boolean) {}

    fun withPlayer(callback: MediaController.() -> Unit) = controller.withController(callback)

    fun prepareAndPlay(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0, startActivity: Boolean = true) {
        withPlayer {
            if (startActivity) {
                startActivity(
                    Intent(this@SimpleControllerActivity, TrackActivity::class.java)
                )
            }

            prepareUsingTracks(tracks = tracks, startIndex = startIndex, startPositionMs = startPositionMs, play = true) { success ->
                if (success) {
                    updatePlaybackInfo(this)
                }
            }
        }
    }

    fun maybePreparePlayer() {
        withPlayer {
            maybePreparePlayer(context = this@SimpleControllerActivity, callback = ::onPlayerPrepared)
        }
    }

    fun togglePlayback() = withPlayer { togglePlayback() }

    fun addTracksToQueue(tracks: List<Track>, callback: () -> Unit) {
        withPlayer {
            val currentMediaItemsIds = currentMediaItems.map { it.mediaId }
            val mediaItems = tracks.toMediaItems().filter { it.mediaId !in currentMediaItemsIds }
            addMediaItems(mediaItems)
            callback()
        }
    }

    fun removeQueueItems(tracks: List<Track>, callback: (() -> Unit)? = null) {
        withPlayer {
            var currentItemChanged = false
            tracks.forEach {
                val index = currentMediaItems.indexOfTrackOrNull(it)
                if (index != null) {
                    currentItemChanged = index == currentMediaItemIndex
                    removeMediaItem(index)
                }
            }

            if (currentItemChanged) {
                updatePlaybackInfo(this)
            }

            callback?.invoke()
        }
    }

    fun playNextInQueue(track: Track, callback: () -> Unit) {
        withPlayer {
            sendCommand(
                command = CustomCommands.SET_NEXT_ITEM,
                extras = bundleOf(EXTRA_NEXT_MEDIA_ID to track.mediaStoreId.toString())
            )
            callback()
        }
    }

    fun deleteTracks(tracks: List<Track>, callback: () -> Unit) {
        // Protect against stale menus after a preference change as well.
        if (!config.allowFileDeletion) return
        if (tracks.isEmpty()) {
            runOnUiThread(callback)
            return
        }
        val original = tracks.distinctBy { it.mediaStoreId to it.path }
        val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // A rescan may replace a manual track's synthetic ID. Keep both identities and
        // leave the caller's objects untouched if Android denies the deletion request.
        maybeRescanTrackPaths(original.map { it.copy() }) { resolved ->
            val pairs = original.zip(resolved)
            fun recordDeleted(deleted: List<Pair<Track, Track>>, next: () -> Unit) {
                ensureBackgroundThread {
                    val affected = deleted.flatMap { listOf(it.first, it.second) }.distinctBy { it.mediaStoreId }
                    getTracksDB().runInTransaction {
                        audioHelper.deleteTracks(affected)
                        audioHelper.removeInvalidAlbumsArtists()
                    }
                    runOnUiThread {
                        removeQueueItems(affected)
                        EventBus.getDefault().post(Events.RefreshFragments())
                        next()
                    }
                }
            }

            if (isRPlus()) {
                if (resolved.any { it.mediaStoreId <= 0 }) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return@maybeRescanTrackPaths
                }
                val batches = pairs.chunked(1000).iterator()
                fun requestNextBatch() {
                    if (!batches.hasNext()) {
                        callback()
                        return
                    }
                    val batch = batches.next()
                    val uris = batch.map { ContentUris.withAppendedId(contentUri, it.second.mediaStoreId) }.distinct()
                    deleteSDK30Uris(uris) { approved ->
                        if (approved) recordDeleted(batch, ::requestNextBatch)
                    }
                }
                runOnUiThread { requestNextBatch() }
            } else {
                ensureBackgroundThread {
                    val deleted = pairs.filter { (_, track) ->
                        try {
                            val removed = contentResolver.delete(contentUri, "${MediaStore.Audio.Media._ID} = ?",
                                arrayOf(track.mediaStoreId.toString()))
                            File(track.path).delete() || removed > 0
                        } catch (ignored: Exception) {
                            false
                        }
                    }
                    recordDeleted(deleted) {
                        if (deleted.size == pairs.size) callback()
                        else toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }

    fun refreshQueueAndTracks(trackToUpdate: Track? = null) {
        ensureBackgroundThread {
            val queuedTracks = audioHelper.getQueuedTracks()
            runOnUiThread {
                withPlayer {
                    // it's not yet directly possible to update metadata without interrupting the playback: https://github.com/androidx/media/issues/33
                    if (trackToUpdate == null || currentMediaItem.isSameMedia(trackToUpdate)) {
                        prepareUsingTracks(tracks = queuedTracks, startIndex = currentMediaItemIndex, startPositionMs = currentPosition, play = isReallyPlaying)
                    } else {
                        val trackIndex = currentMediaItems.indexOfTrack(trackToUpdate)
                        if (trackIndex > 0) {
                            removeMediaItem(trackIndex)
                            addMediaItem(trackIndex, trackToUpdate.toMediaItem())
                        }
                    }
                }
            }
        }

        EventBus.getDefault().post(Events.RefreshTracks())
    }
}
