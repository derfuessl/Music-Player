package org.fossify.musicplayer.playback

import kotlin.random.Random

/**
 * Playlist-independent music/pause state machine. The playback port deliberately exposes no
 * queue mutation or seek operation: a dance pause retains the player's existing item and position.
 *
 * All methods and scheduled callbacks must run on the player's application thread. Keeping the
 * state machine free of Android classes allows cancellation and timing to be tested with virtual
 * time. The service adapter owns thread dispatch, Player events and listener cleanup.
 */
internal class StopDanceController(
    private val playback: Playback,
    private val scheduler: Scheduler,
    private val randomSeconds: (Int, Int) -> Long = { min, max -> Random.nextLong(min.toLong(), max.toLong() + 1) }
) {
    data class Settings(val minPlaySeconds: Int, val maxPlaySeconds: Int, val pauseSeconds: Int) {
        init {
            // Reject zero/negative delays (busy loops) and inverted ranges before changing state.
            // Convert to Long before milliseconds conversion, so even large Int inputs are safe.
            require(minPlaySeconds > 0 && maxPlaySeconds >= minPlaySeconds && pauseSeconds > 0)
        }
    }

    interface Playback {
        val canStart: Boolean
        val isPlaying: Boolean
        /** Opaque identity and position; a changed snapshot must never be restored over user edits. */
        fun snapshot(): Any
        fun play()
        fun pause()
    }

    fun interface Scheduler {
        /** Schedule asynchronously on the owning thread; return a function cancelling this task. */
        fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
    }

    enum class Phase { STOPPED, PLAYING, PAUSED }

    var phase = Phase.STOPPED
        private set
    private var settings = Settings(10, 30, 5)
    // Read only on the owning player thread; the service serializes this for remote UI clients.
    val currentSettings: Settings get() = settings
    private var cancelPending: (() -> Unit)? = null
    private var generation = 0L
    private var pausedSnapshot: Any? = null

    fun start(settings: Settings): Boolean {
        // A repeated start replaces the previous cycle. Failed starts leave that cycle untouched.
        if (!playback.canStart) return false
        cancelTimer()
        this.settings = settings
        pausedSnapshot = null
        phase = Phase.PLAYING
        playback.play()
        onPlayingChanged()
        return phase != Phase.STOPPED
    }

    fun stop() {
        // Mark stopped before pausing: Player listeners can be called synchronously by pause().
        // Do not call Player.stop(), which would discard the prepared playback state.
        cancelTimer()
        phase = Phase.STOPPED
        pausedSnapshot = null
        playback.pause()
    }

    /** Buffering does not count as music time; start a fresh interval once playing again. */
    fun onPlayingChanged() {
        if (phase != Phase.PLAYING) return
        if (!playback.isPlaying) {
            cancelTimer()
        } else if (cancelPending == null) {
            schedule(randomSeconds(settings.minPlaySeconds, settings.maxPlaySeconds) * 1000L) {
                // Set PAUSED first so our own pause event cannot be mistaken for an interruption.
                phase = Phase.PAUSED
                playback.pause()
                if (phase == Phase.PAUSED) {
                    pausedSnapshot = playback.snapshot()
                    schedule(settings.pauseSeconds * 1000L) { resume() }
                }
            }
        }
    }

    fun onPositionChanged() {
        if (phase == Phase.PAUSED && pausedSnapshot != null && pausedSnapshot != playback.snapshot()) stop()
    }

    private fun resume() {
        // Never seek back over a user's track/position edit. Cancel instead if anything changed
        // during the pause, including changes whose Player event has not been delivered yet.
        if (!playback.canStart || pausedSnapshot != playback.snapshot()) {
            stop()
            return
        }
        pausedSnapshot = null
        phase = Phase.PLAYING
        playback.play()
        onPlayingChanged()
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        cancelTimer()
        // Cancellation alone is insufficient if a callback was already queued by a scheduler.
        // Each new timer/stop invalidates older generations, including those from prior starts.
        val expectedGeneration = generation
        cancelPending = scheduler.schedule(delayMs) {
            if (generation == expectedGeneration) {
                cancelPending = null
                action()
            }
        }
    }

    private fun cancelTimer() {
        generation++
        cancelPending?.invoke()
        cancelPending = null
    }
}
