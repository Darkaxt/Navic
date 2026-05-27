# Kreate Feature Transplant Audit

Reference checked: `knighthat/Kreate` at `228c5e8` (`main`, pushed 2026-05-27).

## Already Adapted

* Audio focus behavior: Navic now exposes `Respect audio focus` and passes the preference into Media3 `setAudioAttributes(..., handleAudioFocus)`.
* Video concept: Kreate's player exposes a configurable video action, but its implementation is YouTube iframe-specific. Navic's first LidaClips pass uses LidaClips lookup plus Media3 stream playback instead.
* Skip silence: Navic now exposes an Android playback toggle and applies it to Media3 when the playback service starts.
* Skip media on error: Navic now exposes an Android playback toggle and advances to the next queued media item when Media3 reports a playback error and a next item exists.
* Audio-device resume: Navic now exposes an Android playback toggle and resumes a paused queue when a supported output device is added.
* Player action visibility: Navic now exposes Now Playing settings for Lyrics, Queue, Music Video, Playback Speed, and Equalizer actions.
* Persistent queue and startup resume: Navic now exposes Playback settings to save/restore the queue and optionally resume playback on startup. Persistent queue defaults on to preserve Navic's existing restore behavior; resume-on-start defaults off.
* LidaClips Picture-in-Picture: Navic now exposes a LidaClips Android PiP toggle under Settings -> Data & Storage -> Music video clips. The Android video surface provides PiP source bounds, uses Android 12+ auto-enter params, and falls back to `onUserLeaveHint` on Android 8-11.
* Android system equalizer shortcut: Navic now exposes the system equalizer from Settings -> Playback and the now-playing song menu, passing the active Media3 audio session id when available.
* Pause between songs: Navic now exposes an Android Playback setting that pauses briefly after automatic track transitions while leaving manual skips immediate.
* Smart rewind: Navic now exposes an Android Playback setting for the Previous-button threshold. The default remains Navic's old 1 second behavior, while 3 seconds matches Kreate's default.
* Pause on zero volume: Navic now exposes an Android Playback setting that pauses when Android media volume reaches zero and resumes only when Navic paused itself and media volume is restored.
* Search history controls: Navic now persists recent searches across app restarts, lets the user clear or remove entries from Search, and exposes a Data & Storage setting that hides history while stopping newly submitted queries from being recorded.
* LidaClips clip lookup cache/prefetch: Navic now briefly caches clip lookup hits and misses by LidaClips base URL, API-key/header fingerprint, and Navidrome song id, expires stale entries so backend sync changes can surface without app restart, prefetches the now-playing song while LidaClips is enabled, and avoids embedding the raw API key in internal cache/prefetch keys.
* LidaClips action availability policy: Navic now keeps the now-playing Music Video action visible whenever LidaClips is enabled, configured, and not hidden by the user, so cached misses still let the user open the refreshable clip screen.
* LidaClips no-clip refresh: Navic now shows a Refresh action on the music-video screen when no clip is found and bypasses the short lookup cache for that manual retry.
* LidaClips playback diagnostics/retry: Navic now surfaces Android Media3 video stream failures as retryable errors in the LidaClips screen and recreates the player on retry.
* LidaClips music-session coordination: Navic now defaults to Feishin-style clip playback on Android by pausing Navic music while a clip is open and resuming the same paused song when the clip screen closes; a setting can opt out and keep music under clip audio.
* LidaClips remembered clip position: Kreate remembers the last YouTube video id and current second. Navic now adapts that as an Android LidaClips setting that resumes the last watched position for the same clip while avoiding near-start and near-end positions.
* LidaClips landscape video mode: Navic now exposes an Android opt-in setting that rotates the clip screen to landscape and hides system bars while the LidaClips player is active, then restores the prior activity orientation and bars when the screen closes.
* LidaClips video fit mode: Navic now exposes an Android LidaClips setting that keeps the full video frame by default or crops to fill the player using Media3's zoom resize mode.
* LidaClips keep screen on: Navic now exposes a LidaClips setting that keeps the display awake while the music-video clip screen is open, using the existing cross-platform keep-screen-on hook already used by lyrics.
* LidaClips service status/control: Navic now reads LidaClips health/dashboard/control endpoints from Settings -> Data & Storage -> Music video clips, shows backend health-check failures, active/official/fallback clip counts, recent backend sync failures, and sync runtime state, and can pause or resume scheduled backend clip sync.
* LidaClips settings status refresh: Navic now refreshes backend service status after enabled/base URL/API-key changes settle, so setup changes do not leave stale service diagnostics on screen, and fingerprints API-key material in the refresh identity.
* LidaClips stream header scoping: Navic now sends the LidaClips API key to Android video stream requests only when the resolved stream URL is on the configured LidaClips origin, avoiding API-key leakage to absolute external stream URLs while preserving the normal authenticated stream endpoint.
* LidaClips audio focus: Navic now applies the existing `Respect audio focus` playback setting to the Android LidaClips Media3 player, so clip audio follows the same WhatsApp/co-playback choice as normal music playback.
* LidaClips API diagnostics: Navic now centralizes LidaClips repository HTTP error messages and explains unauthorized lookup/status/control failures as likely API-key problems instead of only surfacing raw HTTP 401s.
* LidaClips cache-key privacy: Navic now fingerprints sensitive LidaClips key material before using it in in-memory lookup cache and now-playing prefetch keys, while still separating cache entries for different API keys.
* Tap artwork for lyrics: Kreate exposes a thumbnail-tap lyrics option. Navic now adapts that as an opt-in Now Playing setting so tapping the artwork opens Lyrics without changing the default artwork behavior.

## Best Next Transplants

1. LidaClips playback/session polish
   * The current player is functional and retryable, defaults to Navic music pause/resume while clips play, follows the existing audio-focus preference, remembers the last clip position, keeps the screen awake by default, has opt-in landscape and video crop modes, refreshes stale clip lookup availability, and exposes backend service status/control with health, recent backend failure diagnostics, and clearer API-key error messages that refresh after settings changes settle.
   * These should be based on real-device testing so the video player does not fight the music session or Android PiP behavior.

## Higher-Risk Candidates

* Loudness normalization, bass boost, and reverb: Kreate uses Android audio effects and/or track loudness metadata. Navic already has ReplayGain support, so this needs design work to avoid conflicting gain paths.
* Volume buttons change song and shake to skip: useful for some users, but device-event handling increases background behavior complexity.
* Queue auto-append and Discover-style queue generation: valuable, but product behavior needs more definition for a Navidrome library client.
* Visualizers, thumbnail animations, and extensive player layout variants: high UI churn and likely not worth transplanting until core playback/video behavior is stable.

## Recommended Order

1. Smoke-test LidaClips playback, PiP, and the system equalizer launcher on a real Android device.
2. Use real-device LidaClips testing to decide whether the dedicated Navic screen should evolve toward Feishin's full-screen Clips tab model.
3. Revisit higher-risk playback device-event options only after core LidaClips playback has been tested on-device.
