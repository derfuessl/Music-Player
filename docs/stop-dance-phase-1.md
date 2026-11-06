# Stop Dance: service core (Phase 1)

This branch adds no tab, widget, playlist storage or settings screen. The existing
`PlaybackService` owns one controller for its current player/queue. Settings are
per start request and are not persisted across service/process restarts.

## Media3 command contract

The existing trusted-controller connection policy advertises two new commands:

- `CustomCommands.START_STOP_DANCE.sessionCommand`: requires three `Bundle.putInt`
  values, using `MIN_PLAY_SECONDS`, `MAX_PLAY_SECONDS`, and `PAUSE_SECONDS`.
  All must be positive; maximum must be at least minimum. The random music
  duration is an integer number of seconds, inclusive of both bounds.
- `CustomCommands.STOP_STOP_DANCE.sessionCommand`: no arguments; cancels all
  pending work and pauses playback, retaining the current item and position.

Start returns `ERROR_BAD_VALUE` for missing/invalid values and
`ERROR_INVALID_STATE` unless the current item is prepared (`STATE_READY`),
error-free and not suppressed. Select a track normally before starting. Successful
requests return `RESULT_SUCCESS` after processing on the player thread.
Repeated start replaces the active settings/timer; repeated stop is safe.

Example from a connected in-app `MediaController` (or debugger Evaluate Expression
where that controller is available):

```kotlin
val args = android.os.Bundle().apply {
    putInt(CustomCommands.MIN_PLAY_SECONDS, 2)
    putInt(CustomCommands.MAX_PLAY_SECONDS, 4)
    putInt(CustomCommands.PAUSE_SECONDS, 3)
}
controller.sendCustomCommand(CustomCommands.START_STOP_DANCE.sessionCommand, args)
// Later:
controller.sendCustomCommand(CustomCommands.STOP_STOP_DANCE.sessionCommand, android.os.Bundle.EMPTY)
```

These are Media3 session commands, not exported broadcast intents or adb actions.
There is intentionally no production UI entry point in Phase 1.

## Timing and interruption rules

- Music intervals begin only when `isPlaying` is true. Buffering cancels the
  interval and a fresh random interval starts when actual playback resumes.
- Natural track transitions during music follow the existing queue/repeat/shuffle
  behavior. A dance pause uses only pause/play; it never seeks or replaces media.
- During a dance pause, a change of item, queue index or position cancels the mode
  rather than restoring stale media over the user's edit.
- An external pause/stop cancels even if already paused. An external play during
  the dance pause cancels and leaves playback paused; play again for normal playback.
- Audio-focus loss, playback suppression, player errors and idle/ended state
  cancel the mode and pause. Focus regain does not restart a cancelled cycle.
  ExoPlayer retains responsibility for focus acquisition and ducking; the mode
  does not request separate focus or bypass suppression.
- Service close, sleep-timer expiry and service destruction invalidate pending
  callbacks. No mode resumes after process restart.
- Timers use the player looper's Handler. Android sleep/background restrictions
  may delay a callback; Phase 1 does not add exact alarms or a separate wake lock.

## Validation

Run from Android Studio's terminal on `feature/stop-dance`:

```powershell
.\gradlew.bat :app:testFossDebugUnitTest :app:assembleFossDebug
```

`StopDanceControllerTest` uses fake playback and virtual time to test cycling,
same-item/position retention, cancellation including stale callbacks, repeated
starts/stops, buffering, changed media, invalid settings, equal bounds and overflow.
These JVM tests do not simulate Android focus routing or real Media3 callbacks.

On a device/emulator, select and play a track, then send the commands above using
a connected in-app controller/debugger. Check several cycles, then stop in both
phases and wait longer than the configured interval. Also check:

1. Notification/headset pause during a dance pause: no automatic resume.
2. Seek/change track during a pause: no restoration of the old item/position.
3. Queue/shuffle/repeat transitions during music still work normally.
4. Another app taking audio focus in each phase, focus regain, and headphone removal.
5. Sleep timer, player close, service destruction, empty queue and end of queue.
6. Screen off/background playback on the target Android version.

The real-device interruption/background checks remain necessary before shipping UI.
