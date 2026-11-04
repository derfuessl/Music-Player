# Development Roadmap – Fossify Music Player Community Extensions

This document tracks proposed community extensions for the [Fossify Music Player](https://github.com/FossifyOrg/Music-Player) in the [derfuessl/Music-Player fork](https://github.com/derfuessl/Music-Player).

> **Status:** These features are proposals and development plans, not shipped functionality. The original Fossify project has not yet approved them for upstream contributions. The statuses below were checked on 2026-11-04 and may change; refer to the linked upstream issues for current information.

## Features and upstream issues

| Feature | Upstream issue | Proposed development branch | Current project status |
| --- | --- | --- | --- |
| Custom playlist covers and short descriptions | [FossifyOrg/Music-Player#520](https://github.com/FossifyOrg/Music-Player/issues/520) | `feature/playlist-covers` | Proposed; branch planned |
| Configurable playlist launcher home-screen widget | [FossifyOrg/Music-Player#521](https://github.com/FossifyOrg/Music-Player/issues/521) | `feature/playlist-widget` | Proposed; branch planned |
| Musical Statues / Stop Dance mode with tab and widget | [FossifyOrg/Music-Player#522](https://github.com/FossifyOrg/Music-Player/issues/522) | `feature/stop-dance` | Proposed; development branch created; implementation not yet completed |

As of 2026-11-04, all three upstream issues are open and labeled `feature request` and `needs triage`. None is labeled `help wanted`.

## Scope and goals

### 1. Custom playlist covers and descriptions (#520)

- Allow an optional cover image and short description for each playlist.
- Select or replace covers using Android's system image picker.
- Show covers and descriptions where playlists are displayed.
- Keep existing playlists and playback behavior intact; use a sensible fallback where no custom image exists.
- Preserve user data across any necessary database migration.

### 2. Playlist launcher widget (#521)

- Add a separate, configurable Android home-screen widget without replacing existing playback widgets.
- Choose and reorder playlists for each widget instance.
- Display playlist artwork, name, and optional description.
- Start a selected playlist directly with one tap, using the existing playback service.
- Support multiple widget sizes and independently configured instances.
- Treat #520 as a proposed enhancement; provide fallback presentation for playlists without custom artwork.

### 3. Musical Statues / Stop Dance (#522)

- Add a playlist-independent mode controlling the currently active playback queue.
- Choose a random music-phase duration between configurable minimum and maximum values (seconds).
- Pause for a configurable duration (seconds); randomized pause durations can be an optional enhancement.
- Resume the **same track at the saved position** after each pause.
- When the Stop Dance mode ends, **pause music playback** while retaining its position.
- Add a dedicated tab that can be hidden in app settings, plus a separate home-screen widget.
- Keep both controls synchronized with one mode controller within the existing playback architecture.
- Respect background playback constraints, audio focus, and external interruptions.

## Branches and contribution workflow

- `main`: keep aligned with the upstream Fossify project; do not develop features directly here.
- `docs/development-roadmap`: documentation changes only, including this file and a link from `README.md`.
- `feature/stop-dance`: created from `main` for the independent Stop Dance implementation.
- `feature/playlist-covers`: planned for the playlist metadata feature.
- `feature/playlist-widget`: planned for the launcher widget and expected to use playlist metadata work as available.

Each feature should be developed in a focused branch with small, reviewable commits, tests where practical, and clear references to its upstream issue. Preserve the project's conventions and avoid unrelated changes. Local Android Studio builds and CI/APK builds can be used to validate changes before proposing a pull request.

**Upstream contributions:** An issue being open or linked from this fork does not mean it is approved. Follow the [Fossify contribution guidance](https://github.com/FossifyOrg/General-Discussion) and wait for the required maintainer approval (such as `help wanted`) before opening an upstream implementation pull request.

## Current next steps

1. Review the existing playback service, tab configuration and Android widget code.
2. Implement and test the Stop Dance controller in `feature/stop-dance`.
3. Plan the playlist metadata migration and cover selection for #520.
4. Build the playlist launcher widget for #521, coordinating its metadata dependencies.
5. Track maintainer feedback on #520, #521 and #522 and adapt the upstream contribution plan accordingly.

This is a community-maintained planning document for this fork, not an official Fossify roadmap.
