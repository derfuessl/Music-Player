package org.fossify.musicplayer.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.playlistDAO
import org.fossify.musicplayer.extensions.prepareUsingTracks
import org.fossify.musicplayer.helpers.PLAYLIST
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.playback.PlaybackService

/** A user-initiated widget tap may start playback even when the app is cold. This transparent
 * entry point stays in the foreground until it dispatches the commands, then returns to Home. */
class PlaylistWidgetActionActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var request = 0
    private var connection: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        // If the child taps another cover while a slow database lookup is in flight, only the
        // most recent tap should replace the queue. IDs are resolved anew after edits/deletions.
        val version = ++request
        val id = intent.getIntExtra(PLAYLIST, -1)
        val play = intent.getBooleanExtra(PLAY, false)
        ensureBackgroundThread {
            val playlist = playlistDAO.getPlaylistWithId(id)
            val tracks = if (playlist != null && play) audioHelper.getPlaylistTracks(id) else emptyList()
            runOnUiThread {
                if (!active(version)) return@runOnUiThread
                when {
                    playlist == null || (play && tracks.isEmpty()) -> fail(R.string.widget_no_tracks)
                    play -> startMusic(tracks, id, version)
                    else -> {
                        startActivity(Intent(this, TracksActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(PLAYLIST, Gson().toJson(playlist)))
                        finish()
                    }
                }
            }
        }
    }

    private fun startMusic(tracks: List<Track>, playlistId: Int, version: Int) {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        connection?.let(MediaController::releaseFuture)
        val future = MediaController.Builder(this, token).buildAsync()
        connection = future
        future.addListener({
            if (!active(version)) return@addListener
            val controller = try { future.get() } catch (e: Exception) {
                fail(R.string.widget_play_failed)
                return@addListener
            }
            // setMediaItems is asynchronous across the Media3 connection. Keep this foreground
            // entry point alive until the service has prepared the requested song. Otherwise a
            // cold service can lose its last controller before it posts its media notification.
            val firstId = tracks.first().mediaStoreId.toString()
            controller.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (active(version) && player.currentMediaItem?.mediaId == firstId &&
                        player.playbackState == Player.STATE_READY) finish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (active(version)) fail(R.string.widget_play_failed)
                }
            })
            // Always return control to the launcher, including after a disconnected service.
            handler.postDelayed({ if (active(version)) finish() }, 15_000)
            controller.prepareUsingTracks(tracks, play = true, playlistId = playlistId)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun active(version: Int) = version == request && !isFinishing && !isDestroyed

    private fun fail(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        connection?.let(MediaController::releaseFuture)
        super.onDestroy()
    }

    companion object {
        const val PLAY = "widget_play_playlist"
    }
}
