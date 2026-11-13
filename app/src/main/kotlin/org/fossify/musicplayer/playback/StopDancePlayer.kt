package org.fossify.musicplayer.playback

import android.os.Handler
import android.os.Bundle
import android.os.Looper
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
    private var positionRevision = 0L
    private data class Position(val mediaId: String?, val index: Int, val revision: Long)
    // During a freeze only the selection moves. Seeking is deferred until the original deadline.
    private data class Selection(val index: Int, val mediaId: String)
    private var pending: Selection? = null
    private fun pendingIndex(): Int? = pending?.takeIf {
        it.index in 0 until player.mediaItemCount && player.getMediaItemAt(it.index).mediaId == it.mediaId
    }?.index
    private fun selectedIndex(): Int = pendingIndex().takeIf { controller.phase == StopDanceController.Phase.PAUSED } ?: player.currentMediaItemIndex

    private fun neighbour(forward: Boolean): Int {
        val timeline = player.currentTimeline
        val index = selectedIndex()
        if (index !in 0 until timeline.windowCount) return androidx.media3.common.C.INDEX_UNSET
        val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        return if (forward) timeline.getNextWindowIndex(index, mode, player.shuffleModeEnabled)
            else timeline.getPreviousWindowIndex(index, mode, player.shuffleModeEnabled)
    }

    private val canStart: Boolean
        get() = player.currentMediaItem != null && player.playerError == null &&
            player.playbackState == Player.STATE_READY &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

    /** Atomic snapshot for the UI. This method is called only through the service's player thread. */
    fun stateExtras() = Bundle().apply {
        putString(CustomCommands.TRACK_TITLE, selectedIndex().takeIf { it in 0 until player.mediaItemCount }?.let { player.getMediaItemAt(it).mediaMetadata.title?.toString() })
        putBoolean(CustomCommands.HAS_PREVIOUS, neighbour(false) != androidx.media3.common.C.INDEX_UNSET)
        putBoolean(CustomCommands.HAS_NEXT, neighbour(true) != androidx.media3.common.C.INDEX_UNSET)
        putString(CustomCommands.STOP_DANCE_PHASE, controller.phase.name)
        putBoolean(CustomCommands.STOP_DANCE_CAN_START, canStart)
        putBoolean(CustomCommands.STOP_DANCE_IS_PLAYING, player.isPlaying)
        putInt(CustomCommands.MIN_PLAY_SECONDS, controller.currentSettings.minPlaySeconds)
        putInt(CustomCommands.MAX_PLAY_SECONDS, controller.currentSettings.maxPlaySeconds)
        putInt(CustomCommands.PAUSE_SECONDS, controller.currentSettings.pauseSeconds)
    }

    val controller = StopDanceController(
        object : StopDanceController.Playback {
            override val canStart: Boolean
                // Phase 1 controls an already prepared queue. It never loads a playlist or
                // implicitly restarts an ended track; callers must select/prepare media first.
                get() = this@StopDancePlayer.canStart
            override val canResume get() = player.currentMediaItem != null && player.playerError == null &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            override val isPlaying get() = player.isPlaying
            // ExoPlayer may refine currentPosition after pause() as its playback thread catches up.
            // Comparing raw milliseconds would treat that normal settling as a user seek and end
            // the game on its first pause. Track actual position discontinuities instead. We never
            // seek ourselves: the prepared Player retains the audio position during each pause.
            override fun snapshot(): Any = Position(player.currentMediaItem?.mediaId, player.currentMediaItemIndex, positionRevision)
            override fun play() = changePlayer {
                pendingIndex()?.let { player.seekToDefaultPosition(it) }
                pending = null
                player.play()
            }
            override fun pause() = changePlayer {
                pending = null
                player.pause()
            }
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

    fun changeTrack(forward: Boolean) {
        val target = neighbour(forward)
        if (target == androidx.media3.common.C.INDEX_UNSET) return
        if (controller.phase == StopDanceController.Phase.PAUSED) pending = Selection(target, player.getMediaItemAt(target).mediaId)
        else player.seekToDefaultPosition(target)
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

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // Even a seek to nearly the same millisecond invalidates a paused run. Natural track
        // transitions during music remain allowed because the controller checks only while paused.
        positionRevision++
        controller.onPositionChanged()
    }

    fun release() {
        // Invalidate scheduled work before detaching listeners or releasing the underlying player.
        controller.stop()
        player.onPauseOrStopRequested = null
        player.removeListener(this)
    }
}
