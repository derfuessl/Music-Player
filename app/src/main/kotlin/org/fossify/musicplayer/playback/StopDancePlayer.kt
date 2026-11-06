package org.fossify.musicplayer.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.fossify.musicplayer.playback.player.SimpleMusicPlayer

/**
 * Media3 adapter for one service/player lifetime. Handler callbacks, listener callbacks and command
 * execution share applicationLooper; no timer thread is allowed to access the Player directly.
 * ExoPlayer remains the sole audio-focus owner. We cancel the mode on suppression/focus loss,
 * rather than letting a dance timer compete with ExoPlayer's interruption handling.
 */
@UnstableApi
internal class StopDancePlayer(private val player: SimpleMusicPlayer) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private var internalChange = false
    private data class Position(val item: MediaItem?, val index: Int, val positionMs: Long)

    val controller = StopDanceController(
        object : StopDanceController.Playback {
            override val canStart: Boolean
                // Phase 1 controls an already prepared queue. It never loads a playlist or
                // implicitly restarts an ended track; callers must select/prepare media first.
                get() = player.currentMediaItem != null && player.playerError == null &&
                    player.playbackState == Player.STATE_READY &&
                    player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            override val isPlaying get() = player.isPlaying
            override fun snapshot(): Any = Position(player.currentMediaItem, player.currentMediaItemIndex, player.currentPosition)
            override fun play() = changePlayer { player.play() }
            override fun pause() = changePlayer { player.pause() }
        },
        StopDanceController.Scheduler { delayMs, action ->
            val task = Runnable { action() }
            handler.postDelayed(task, delayMs)
            val cancel: () -> Unit = { handler.removeCallbacks(task) }
            cancel
        }
    )

    init {
        check(Looper.myLooper() == player.applicationLooper)
        player.addListener(this)
        // A pause while already paused produces no Player event, but must cancel auto-resume.
        player.onPauseOrStopRequested = {
            if (!internalChange && controller.phase != StopDanceController.Phase.STOPPED) controller.stop()
        }
    }

    private fun changePlayer(action: () -> Unit) {
        // The wrapper reports even redundant external pause requests. Exclude our own mutations
        // and preserve the previous flag in case Media3 invokes listeners reentrantly.
        val previous = internalChange
        internalChange = true
        try {
            action()
        } finally {
            internalChange = previous
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Events may arrive after play()/pause() returned, so use the expected phase, not just
        // internalChange. Non-user reasons include focus loss and unplugged headphones.
        if (controller.phase == StopDanceController.Phase.STOPPED) return
        if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
            (!playWhenReady && controller.phase == StopDanceController.Phase.PLAYING) ||
            (playWhenReady && controller.phase == StopDanceController.Phase.PAUSED)
        ) controller.stop()
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        // Transient focus loss may suppress playback without changing playWhenReady at all.
        // Explicitly pause when cancelling, preventing focus regain from restarting this run.
        if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            controller.phase != StopDanceController.Phase.STOPPED
        ) controller.stop()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = controller.onPlayingChanged()

    override fun onPlaybackStateChanged(playbackState: Int) {
        if ((playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) &&
            controller.phase != StopDanceController.Phase.STOPPED
        ) controller.stop()
    }

    override fun onPlayerError(error: PlaybackException) {
        if (controller.phase != StopDanceController.Phase.STOPPED) controller.stop()
    }

    override fun onEvents(player: Player, events: Player.Events) = controller.onPositionChanged()

    fun release() {
        // Invalidate scheduled work before detaching listeners or releasing the underlying player.
        controller.stop()
        player.onPauseOrStopRequested = null
        player.removeListener(this)
    }
}
