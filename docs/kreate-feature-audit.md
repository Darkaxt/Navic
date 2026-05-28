# Kreate Feature Transplant Audit

Reference checked: `knighthat/Kreate` at `228c5e8` (`main`, pushed 2026-05-27).

## Already Adapted

* Audio focus behavior: Navic now exposes `Respect audio focus` and passes the preference into Media3 `setAudioAttributes(..., handleAudioFocus)`.
* Playback volume: Navic now adapts Kreate's separate player-volume control as an Android Playback slider. It defaults to 100% to preserve current behavior and combines with ReplayGain attenuation instead of replacing it.
* Video concept: Kreate's player exposes a configurable video action, but its implementation is YouTube iframe-specific. Navic's first LidaClips pass uses LidaClips lookup plus Media3 stream playback instead.
* Skip silence: Navic now exposes an Android playback toggle and applies it to Media3 when the playback service starts.
* Skip media on error: Navic now exposes an Android playback toggle and advances to the next queued media item when Media3 reports a playback error and a next item exists.
* Audio-device resume: Navic now exposes an Android playback toggle and resumes a paused queue when a supported output device is added.
* Player action visibility: Navic now exposes Now Playing settings for Lyrics, Queue, Music Video, Playback Speed, Sleep Timer, and Equalizer actions.
* Sleep timer action visibility: Navic already had a sleep timer; the Now Playing song menu now has a Kreate-style visibility setting for that action, while the root top-bar sleep-timer access remains available.
* Persistent queue and startup resume: Navic now exposes Playback settings to save/restore the queue and optionally resume playback on startup. Persistent queue defaults on to preserve Navic's existing restore behavior; resume-on-start defaults off.
* LidaClips Picture-in-Picture: Navic now exposes a LidaClips Android PiP toggle under Settings -> Data & Storage -> Music video clips. The Android video surface provides PiP source bounds, uses Android 12+ auto-enter params, and falls back to `onUserLeaveHint` on Android 8-11.
* Android system equalizer shortcut: Navic now exposes the system equalizer from Settings -> Playback and the now-playing song menu, passing the active Media3 audio session id when available.
* Now Playing artwork visibility: Navic now adapts Kreate's `PLAYER_SHOW_THUMBNAIL` as a default-on Now Playing layout setting. Hiding artwork also hides the artwork-tap lyrics setting and disables that tap action.
* Now Playing artwork size: Navic now adapts Kreate's player-thumbnail size options as a Now Playing layout setting with Small, Medium, Big, Biggest, and Expanded choices. Biggest is the default and preserves Navic's previous artwork padding.
* Now Playing pause shrink: Navic now exposes Kreate's `PLAYER_SHRINK_THUMBNAIL_ON_PAUSE` idea as `Shrink artwork on pause`, defaulting on to preserve Navic's previous animated paused/inactive artwork shrink.
* Pause between songs: Navic now exposes an Android Playback setting that pauses briefly after automatic track transitions while leaving manual skips immediate.
* Medley mode: Navic now adapts Kreate's playback-duration/medley behavior as an Android Playback setting. It defaults off for full-track playback and can auto-advance to the next queued song after 15, 30, 45, or 60 seconds.
* Smart rewind: Navic now exposes an Android Playback setting for the Previous-button threshold. The default remains Navic's old 1 second behavior, while 3 seconds matches Kreate's default.
* Pause on zero volume: Navic now exposes an Android Playback setting that pauses when Android media volume reaches zero and resumes only when Navic paused itself and media volume is restored.
* Audio fade: Navic now exposes an Android Playback setting for Kreate-style pause/resume fade durations while keeping immediate pause/resume as the default.
* Bass boost: Navic now exposes an Android Playback toggle and strength slider, then applies Android `BassBoost` to the active Media3 audio session when available.
* Reverb presets: Navic now exposes an Android Playback setting for Kreate-style `PresetReverb` choices and applies the selected auxiliary effect through the playback service's ExoPlayer session.
* ReplayGain loudness boost: Kreate uses YouTube loudness metadata plus Android `LoudnessEnhancer`. Navic now adapts the same Android effect path to Navidrome ReplayGain metadata: existing ReplayGain attenuation stays in player volume, while the optional Android Playback setting boosts only positive ReplayGain gain that would otherwise be capped at normal volume.
* Playback pitch: Navic now adapts Kreate's playback-parameter controls by adding Android pitch adjustment to the existing Playback Speed sheet. Saved player state now preserves and normalizes both speed and pitch.
* Queue auto-fill: Navic now adapts Kreate's queue auto-append concept as an Android Playback setting that appends songs when active playback gets near the end of a non-radio queue. It skips duplicates, skips radio items, exposes a target queue size, and can choose either random-library or current-song-similar refill sources. Similar mode prefers live Navidrome similar-song results when available, then falls back to local library similarity.
* Shuffle queue limit: Kreate exposes a max-songs-in-queue setting for large shuffle actions. Navic now adapts that as an optional Playback setting that caps collection shuffle queues after shuffling, so the default remains unlimited while large playlist/library shuffles can start with a smaller queue.
* Start song radio: Navic now adapts Kreate's song radio action as a song-sheet and now-playing action. It starts the selected song, prefers live Navidrome similar-song results in server order, and fills the rest of the queue from locally synced songs ranked by shared artist, album, genre, and mood metadata.
* Discover queue cleanup: Navic now adapts Kreate's Discover queue cleanup as a now-playing menu action. It removes upcoming queued songs that are already starred or present in any synced playlist, keeps the current song and queue history intact, and reports how many known songs were removed.
* Queue duration summary: Kreate exposes a queue total-duration display. Navic now keeps the Queue sheet's song count plus total-duration summary as an always-visible header for non-empty queues, with shared formatting coverage instead of a separate setting.
* Song row swipe actions: Navic now adapts Kreate's configurable swipe-action idea for song rows. The Playback settings preserve Navic's default swipe-right/add-to-queue and swipe-left/play-next behavior, while allowing either side to be swapped or disabled.
* Queue row swipe actions: Navic now adapts Kreate's configurable queue swipe-action idea for queue rows. The Playback settings preserve Navic's default remove-from-queue behavior on both swipe directions, while allowing either side to remove, move the queued song to play next, or be disabled.
* Shake to skip: Navic now adapts Kreate's accelerometer-based skip gesture as an Android Playback setting. It registers the accelerometer only while the app is open, logged in, and the setting is enabled, then skips to the next queued song after a cooldown-protected shake event.
* Volume keys skip tracks: Navic now adapts Kreate's volume-key track-change setting as an Android foreground-only Playback setting. When enabled, volume up maps to next and volume down maps to previous while Navic is open; repeat and key-up events are consumed without repeated skips.
* Now Playing seek buttons: Navic now adapts Kreate's optional seek-button setting as an opt-in Now Playing layout setting. It adds 10-second back/forward actions beside the time row while keeping the default player layout unchanged.
* Now Playing remaining time: Navic now adapts Kreate's remaining-song-time display as an opt-in Now Playing layout setting. It keeps the current elapsed/total duration labels by default and inserts a remaining-time label for finite tracks when enabled.
* Now Playing controls/timeline order: Navic now adapts Kreate's `PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED` as an opt-in Now Playing layout setting. It keeps Navic's existing timeline-above-buttons order by default and can move playback buttons above the progress/time block.
* Now Playing up-next preview: Navic now adapts Kreate's mini-queue settings as opt-in Now Playing layout settings. It shows the next queued songs below the progress row, can show Kreate-style cover thumbnails beside those songs, keeps the default player layout unchanged, and opens the full Queue when tapped.
* Search history controls: Navic now persists recent searches across app restarts, lets the user clear or remove entries from Search, and exposes a Data & Storage setting that hides history while stopping newly submitted queries from being recorded.
* Auto-download starred songs: Navic now adapts Kreate's auto-download-on-like behavior as a Data & Storage setting. It downloads a song when the user stars it while online and the song is not already downloaded; unstarring keeps existing downloads.
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
* Lyrics font size: Navic now adapts Kreate's lyrics-size preset idea as a Playback -> Lyrics setting. Medium preserves Navic's previous 32sp lyrics text size while Small, Large, and Extra large provide readable alternatives.
* Lyrics alignment: Navic now adapts Kreate's lyrics alignment option as a Playback -> Lyrics setting. Auto preserves Navic's current LTR/RTL-aware alignment while Start, Center, and End force an explicit alignment.
* Lyrics tap-to-seek: Navic now adapts Kreate's `LYRICS_JUMP_ON_TAP` option as Playback -> Lyrics -> `Tap lyrics to seek`. It defaults on to preserve Navic's existing synced-lyrics seeking behavior, and turning it off leaves lyrics share-selection taps unchanged.

## Best Next Transplants

1. Device-event controls
   * Shake-to-skip and foreground volume-key track changes are adapted. Any future background hardware-button handling would need a separate design because it has broader device behavior implications.
2. Broader Discover behavior
   * Auto-fill, Start song radio, and Discover queue cleanup now cover the useful queue-management pieces. Broader home/discovery-page behavior remains product work because Kreate's source is YouTube/InnerTube-specific rather than Navidrome-specific.

## Higher-Risk Candidates

* Volume buttons change song and shake to skip: useful for some users, but device-event handling increases background behavior complexity.
* Discover-style queue generation: Start song radio, similar auto-fill, and Discover queue cleanup now cover the queue-focused pieces with Navidrome data. Broader Discover-page behavior still needs product definition for a Navidrome library client.
* Visualizers, thumbnail animations, and extensive player layout variants: high UI churn and likely not worth transplanting until core playback/video behavior is stable.

## Recommended Order

1. Smoke-test bass boost, reverb, and the system equalizer launcher on a real Android device.
2. Smoke-test ReplayGain loudness boost with real Navidrome ReplayGain metadata on a device that supports Android `LoudnessEnhancer`.
3. Revisit device-event controls only after deciding how aggressive background input handling should be in this fork.
4. Design broader Discover-page behavior only if Navic should grow a dedicated discovery surface beyond queue actions.
