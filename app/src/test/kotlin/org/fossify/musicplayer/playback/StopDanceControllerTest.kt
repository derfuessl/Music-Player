package org.fossify.musicplayer.playback

import org.junit.Assert.*
import org.junit.Test

/** Virtual time keeps these regression tests deterministic and independent of Android/real audio. */
class StopDanceControllerTest {
    private class FakePlayback : StopDanceController.Playback {
        override var canStart = true
        override var isPlaying = false
        var item = "track-a"
        var position = 12345L
        var playCalls = 0
        override fun snapshot(): Any = item to position
        override fun play() { playCalls++; isPlaying = true }
        override fun pause() { isPlaying = false }
    }

    private class FakeScheduler : StopDanceController.Scheduler {
        data class Task(val delay: Long, val action: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            val task = Task(delayMs, action)
            tasks += task
            return { task.cancelled = true }
        }
        fun fire() {
            val task = tasks.last()
            check(!task.cancelled)
            task.action()
        }
    }

    private val playback = FakePlayback()
    private val scheduler = FakeScheduler()
    private val controller = StopDanceController(playback, scheduler) { min, max ->
        assertEquals(2, min)
        assertEquals(4, max)
        max.toLong()
    }
    private val settings = StopDanceController.Settings(2, 4, 3)

    @Test fun cyclesWithoutChangingTrackOrPosition() {
        assertTrue(controller.start(settings))
        assertEquals(4000L, scheduler.tasks.last().delay)
        scheduler.fire()
        assertEquals(StopDanceController.Phase.PAUSED, controller.phase)
        assertFalse(playback.isPlaying)
        assertEquals(3000L, scheduler.tasks.last().delay)
        scheduler.fire()
        assertTrue(playback.isPlaying)
        assertEquals("track-a" to 12345L, playback.snapshot())
        assertEquals(4000L, scheduler.tasks.last().delay)
    }

    @Test fun stopDuringPauseCancelsEvenAnAlreadyQueuedCallback() {
        controller.start(settings)
        scheduler.fire()
        val stale = scheduler.tasks.last()
        controller.stop()
        assertTrue(stale.cancelled)
        stale.action() // Simulate a scheduler delivering a cancelled task anyway.
        assertFalse(playback.isPlaying)
        assertEquals(1, playback.playCalls)
        assertEquals(StopDanceController.Phase.STOPPED, controller.phase)
    }

    @Test fun stopDuringMusicPausesAndIsIdempotent() {
        controller.start(settings)
        val stale = scheduler.tasks.last()
        controller.stop()
        controller.stop()
        stale.action()
        assertFalse(playback.isPlaying)
        assertEquals(1, scheduler.tasks.size)
    }

    @Test fun repeatedStartReplacesTimerAndSettings() {
        controller.start(settings)
        val stale = scheduler.tasks.last()
        controller.start(settings.copy(pauseSeconds = 8))
        stale.action()
        assertEquals(StopDanceController.Phase.PLAYING, controller.phase)
        scheduler.fire()
        assertEquals(8000L, scheduler.tasks.last().delay)
    }

    @Test fun restartDuringPauseInvalidatesOldResume() {
        controller.start(settings)
        scheduler.fire()
        val stale = scheduler.tasks.last()
        controller.start(settings)
        stale.action()
        assertEquals(2, playback.playCalls)
        assertEquals(StopDanceController.Phase.PLAYING, controller.phase)
    }

    @Test fun changedTrackDuringPauseStopsWithoutRestoringOldTrack() {
        controller.start(settings)
        scheduler.fire()
        playback.item = "track-b"
        controller.onPositionChanged()
        assertEquals(StopDanceController.Phase.STOPPED, controller.phase)
        assertEquals("track-b", playback.item)
    }

    @Test fun changedPositionIsAlsoCheckedBeforeResume() {
        controller.start(settings)
        scheduler.fire()
        playback.position = 99L
        scheduler.fire()
        assertFalse(playback.isPlaying)
        assertEquals(99L, playback.position)
    }

    @Test fun naturalTrackTransitionDuringMusicKeepsRunning() {
        controller.start(settings)
        playback.item = "track-b"
        controller.onPositionChanged()
        scheduler.fire()
        scheduler.fire()
        assertTrue(playback.isPlaying)
        assertEquals("track-b", playback.item)
    }

    @Test fun bufferingCancelsMusicTimerUntilActuallyPlaying() {
        controller.start(settings)
        playback.isPlaying = false
        controller.onPlayingChanged()
        assertTrue(scheduler.tasks.last().cancelled)
        playback.isPlaying = true
        controller.onPlayingChanged()
        controller.onPlayingChanged()
        assertEquals(2, scheduler.tasks.size)
    }

    @Test fun unavailablePlaybackRejectsStartAndResume() {
        playback.canStart = false
        assertFalse(controller.start(settings))
        assertTrue(scheduler.tasks.isEmpty())
        playback.canStart = true
        controller.start(settings)
        scheduler.fire()
        playback.canStart = false
        scheduler.fire()
        assertFalse(playback.isPlaying)
        assertEquals(StopDanceController.Phase.STOPPED, controller.phase)
    }

    @Test fun invalidSettingsAreRejected() {
        listOf(Triple(0, 4, 3), Triple(4, 2, 3), Triple(2, 4, 0), Triple(-1, 4, 3)).forEach { (min, max, pause) ->
            assertThrows(IllegalArgumentException::class.java) { StopDanceController.Settings(min, max, pause) }
        }
    }

    @Test fun equalBoundsAndLargeSecondsAreSafe() {
        val fixed = StopDanceController(playback, scheduler)
        fixed.start(StopDanceController.Settings(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE.toLong() * 1000, scheduler.tasks.last().delay)
        scheduler.fire()
        assertEquals(Int.MAX_VALUE.toLong() * 1000, scheduler.tasks.last().delay)
    }
}
