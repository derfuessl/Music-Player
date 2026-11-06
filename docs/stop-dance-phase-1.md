# Stop Dance: service core and S1 controls

The existing `PlaybackService` owns one controller for its current player/queue.
The S1 tab adds input fields, a service-backed status display and Start/Stop controls.
No widget or separate playlist storage is added. Validated input values persist;
a running game is never restored automatically after service/process death.

## Using the S1 tab

Select and play a song in any existing library tab, then open **Stop Dance**
(**Stopptanz** in German). Enter minimum and maximum music duration and a fixed
pause duration, all in whole seconds. The defaults are 10, 30 and 5 seconds.
Press **Start**. The status alternates between **Dance!** and **Freeze!**;
**Stop** cancels the game and pauses music at its current position.

Inputs are disabled while a game is running; stop before changing durations.
The screen queries the service instead of maintaining its own game timer. It also
reflects external interruptions and reconnects when recreated. Switching tabs or
leaving the screen does not stop an active game. A disconnected or older service
without these commands leaves the buttons disabled instead of pretending to start.

The new tab is enabled once for existing installations while preserving their other
tab choices. It can then be hidden through Settings / Manage visible tabs. Hiding
a tab is a visibility preference and does not stop an active game.

## Media3 command contract

The existing trusted-controller connection policy advertises these commands:

- `CustomCommands.START_STOP_DANCE.sessionCommand`: requires three `Bundle.putInt`
  values, using `MIN_PLAY_SECONDS`, `MAX_PLAY_SECONDS`, and `PAUSE_SECONDS`.
  All must be positive; maximum must be at least minimum. The random music
  duration is an integer number of seconds, inclusive of both bounds.
- `CustomCommands.STOP_STOP_DANCE.sessionCommand`: no arguments; cancels all
  pending work and pauses playback, retaining the current item and position.
- `CustomCommands.GET_STOP_DANCE_STATE.sessionCommand`: returns phase, readiness,
  actual playback state and active timing values in `SessionResult.extras`.
  Start/Stop responses also return this snapshot. Every snapshot is created on
  the player thread; the UI never accesses the service player directly.

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
The S1 tab is now the normal UI entry point; the example remains useful for clients.

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

On a device/emulator, select and play a track, then use the Stop Dance tab with
2–4 seconds of music and a 3-second pause. Check several cycles, then stop in both
phases and wait longer than the configured interval. Also check:

1. Notification/headset pause during a dance pause: no automatic resume.
2. Seek/change track during a pause: no restoration of the old item/position.
3. Queue/shuffle/repeat transitions during music still work normally.
4. Another app taking audio focus in each phase, focus regain, and headphone removal.
5. Sleep timer, player close, service destruction, empty queue and end of queue.
6. Screen off/background playback on the target Android version.
7. Invalid/empty/overflowing values and minimum above maximum: errors must be
   shown at the corresponding fields, without starting a game.
8. Rotation, leaving/reopening the app, changing tabs, light/dark themes, German/
   English, landscape/portrait and small screens: controls remain readable and
   reflect service state; saved values survive reopening.
9. Hide/show the tab in Settings: existing tab choices remain intact and the new
   tab is not forced visible again after explicitly hiding it.

Real-device interruption/background checks remain necessary before release.
