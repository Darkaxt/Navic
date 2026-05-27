# Kreate Feature Transplant Audit

Reference checked: `knighthat/Kreate` at `228c5e8` (`main`, pushed 2026-05-26).

## Already Adapted

* Audio focus behavior: Navic now exposes `Respect audio focus` and passes the preference into Media3 `setAudioAttributes(..., handleAudioFocus)`.
* Video concept: Kreate's player exposes a configurable video action, but its implementation is YouTube iframe-specific. Navic's first LidaClips pass uses LidaClips lookup plus Media3 stream playback instead.
* Skip silence: Navic now exposes an Android playback toggle and applies it to Media3 when the playback service starts.
* Skip media on error: Navic now exposes an Android playback toggle and advances to the next queued media item when Media3 reports a playback error and a next item exists.
* Audio-device resume: Navic now exposes an Android playback toggle and resumes a paused queue when a supported output device is added.
* Player action visibility: Navic now exposes Now Playing settings for Lyrics, Queue, and Music Video actions.
* Persistent queue and startup resume: Navic now exposes Playback settings to save/restore the queue and optionally resume playback on startup. Persistent queue defaults on to preserve Navic's existing restore behavior; resume-on-start defaults off.
* LidaClips Picture-in-Picture: Navic now exposes a LidaClips Android PiP toggle under Settings -> Data & Storage -> Music video clips. The Android video surface provides PiP source bounds, uses Android 12+ auto-enter params, and falls back to `onUserLeaveHint` on Android 8-11.
* Android system equalizer shortcut: Navic now exposes the system equalizer from Settings -> Playback and the now-playing song menu, passing the active Media3 audio session id when available.
* LidaClips clip lookup cache/prefetch: Navic now caches clip lookup hits and misses by LidaClips base URL, API key/header set, and Navidrome song id, and prefetches the now-playing song while LidaClips is enabled.
* LidaClips action availability state: Navic now keeps the now-playing Music Video action visible while availability is unknown, keeps it visible for cached hits, and hides it for cached misses.

## Best Next Transplants

1. LidaClips playback diagnostics and retry affordances
   * The video screen currently reports a missing clip, but stream/playback failures rely mostly on platform player behavior.
   * A small retry/open-again path and clearer failed-stream state would make LidaClips easier to test on-device.

## Higher-Risk Candidates

* Loudness normalization, bass boost, and reverb: Kreate uses Android audio effects and/or track loudness metadata. Navic already has ReplayGain support, so this needs design work to avoid conflicting gain paths.
* Pause on volume zero, volume buttons change song, and shake to skip: useful for some users, but device-event handling increases background behavior complexity.
* Queue auto-append and Discover-style queue generation: valuable, but product behavior needs more definition for a Navidrome library client.
* Visualizers, thumbnail animations, and extensive player layout variants: high UI churn and likely not worth transplanting until core playback/video behavior is stable.

## Recommended Order

1. Smoke-test LidaClips playback, PiP, and the system equalizer launcher on a real Android device.
2. Add LidaClips playback diagnostics/retry affordances if on-device testing shows stream failures that are hard to recover from.
3. Revisit higher-risk playback device-event options only after core LidaClips playback has been tested on-device.
