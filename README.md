<div align="center">

# Navic

A personalized Navidrome client fork with Android playback controls, LidaClips music videos, and reverse-proxy auth.

[![Add to Obtainium](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/obtainium.svg)][ADD_TO_OBTAINIUM]
[![AltSource provides links for most sideloading apps, like Feather](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/altsource.svg)][ALTSOURCE]
[![Link to the latest release where you can download the APK or IPA directly](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/direct_download.svg)][LATEST_RELEASE]
[![Discord](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/discord.svg)](https://discord.gg/TBcnNX66PH)
[![Translate](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/translate.svg)](#translating)
[![Codeberg](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/codeberg.svg)](https://codeberg.org/paige/Navic)

</div>

## Features

* Customisable: large selection of settings and tweaks
* Secure & private: zero permissions, telemetry, or analytics
* Integrated: shows up on the lock screen + quick settings
* Lightweight & fast: zero bloat
* Feature rich: covers almost the entirety of the Subsonic API
* Works offline: syncs your entire library locally, and allows you to download songs

## Fork additions

This fork keeps upstream Navic as the base client and adds features for reverse-proxy setups, Android playback control, and LidaClips music-video playback.

### Reverse proxy and server access

* Reverse proxy Basic Auth fields for Navidrome/Subsonic servers behind Traefik or another proxy.
* Generated `Authorization: Basic ...` headers override a manual custom `Authorization` header only while the Basic Auth toggle is enabled.
* Existing custom server headers remain available for advanced setups.
* Server headers are applied to login, API calls, streaming, downloads, artwork, notifications, lyrics sharing, and now-playing artwork/color loading.
* The Android package id is `darkaxt.navic`, so the fork can install separately from upstream Navic.

### Android playback controls

* `Respect audio focus` can be turned off so Navic music and LidaClips video playback can keep playing while WhatsApp or another app plays audio.
* Kreate-inspired `Pause listening history` temporarily stops Navidrome now-playing and scrobble submissions without changing the rest of the scrobbling setup.
* Kreate-inspired `Playback volume` scales Navic's player volume separately from Android media volume.
* Kreate-inspired playback toggles for `Skip silence` and `Skip media on error`.
* Optional resume of a paused queue when headphones, USB audio, or Bluetooth audio connect.
* Optional pause between songs after automatic track changes.
* Kreate-inspired `Medley mode` can auto-advance to the next queued song after 15, 30, 45, or 60 seconds of playback.
* Kreate-inspired `Smart rewind` setting for when the Previous button restarts the current song instead of jumping back.
* Optional pause/resume behavior when Android media volume is muted to zero and then restored.
* Kreate-inspired Android audio fade durations for smoother pause and resume.
* Kreate-inspired Android bass boost with an adjustable strength slider.
* Kreate-inspired Android reverb presets for room, hall, and plate effects.
* Kreate-inspired Android ReplayGain loudness boost can use `LoudnessEnhancer` to raise quiet ReplayGain tracks above normal volume.
* Kreate-inspired Android playback pitch control in the existing Playback Speed sheet.
* Kreate-inspired `Auto-fill queue` can append synced Navidrome songs when playback reaches the end of the current queue, with random-library, current-song-similar, or recent-genre sources. Similar mode prefers live Navidrome similar-song results when available.
* Kreate-inspired `Shuffle queue limit` can cap how many songs a collection shuffle starts with, while keeping the default unlimited behavior.
* Kreate-inspired `Start song radio` builds a fresh queue from a selected song, preferring Navidrome similar-song results before falling back to locally synced songs with similar artist, album, genre, or mood metadata.
* Kreate-inspired `Discover queue` action removes upcoming songs that are already starred or present in a synced playlist, while keeping the current song and playback history intact.
* Kreate-inspired queue duration summary shows the total runtime beside the song count in the Queue sheet.
* Kreate-inspired `Now Playing indicator` setting can hide the animated waveform beside the current song in lists and the queue.
* Kreate-inspired `Playlist indicator` setting can mark synced songs that already exist in one of your playlists.
* Kreate-inspired song-row swipe actions can keep the default swipe-right/add-to-queue and swipe-left/play-next gestures, swap them, or disable either side.
* Kreate-inspired queue-row swipe actions can keep the default remove-from-queue gestures, move a queued song to play next, or disable either side.
* Kreate-inspired `Shake to skip` can skip to the next queued song when the phone is shaken while Navic is open.
* Kreate-inspired `Volume keys skip tracks` can use Android volume up/down for next/previous while Navic is open.
* Persistent queue controls, including optional playback resume on startup.
* Android system equalizer shortcut in Playback settings and the now-playing song menu.
* Now Playing action visibility toggles for Lyrics, Queue, More, Music Video, Playback Speed, Sleep Timer, Start Radio, Discover Queue, Download, Add to Playlist, and Equalizer actions.
* Sleep timer access from the now-playing song menu and root top-bar menu.
* Kreate-inspired configurable Now Playing dynamic background blur and dim strength.
* Kreate-inspired optional bottom gradient for the dynamic Now Playing background.
* Kreate-inspired optional main Now Playing artwork visibility, artwork size, and pause-shrink behavior.
* Kreate-inspired Now Playing artwork swipe control for disabling horizontal artwork swipes without disabling mini-player swipes.
* Kreate-inspired optional rotating artwork for the active Now Playing cover.
* Kreate-inspired optional Now Playing seek buttons beside the time row. Tap seeks 10 seconds; long-press seeks 30 seconds.
* Kreate-inspired optional Now Playing remaining-time label between elapsed and total duration.
* Kreate-inspired Now Playing progress width selector for shorter, default-width, or full-width playback timelines.
* Kreate-inspired optional Now Playing control/timeline order swap.
* Kreate-inspired optional Now Playing playback-button spacing for compact or evenly spread controls.
* Kreate-inspired Now Playing play button speed label when playback speed is not `1.0x`, plus long-press access to Playback Speed.
* Kreate-inspired optional Now Playing controls swipe-up gesture for opening Queue.
* Kreate-inspired optional Now Playing controls tap gesture for opening Queue.
* Kreate-inspired Now Playing toolbar position can be Top, Bottom, or Hidden for a cleaner fullscreen player.
* Kreate-inspired Now Playing song info style can keep the compact artist line or add album context.
* Kreate-inspired optional album/artist icons can make Now Playing title and artist navigation more discoverable.
* Kreate-inspired Now Playing technical info style can keep the compact format/sample-rate/bitrate row or add source stats such as bit depth, channel count, file size, and ReplayGain.
* Kreate-inspired optional Now Playing shuffle and repeat control visibility.
* Kreate-inspired optional Now Playing `Up next` preview for upcoming queued songs, with optional artwork thumbnails.
* Kreate-inspired optional mini-player queue button for opening Queue directly from the mini player.
* Kreate-inspired queue auto-fill can refill with random library songs, Navidrome/local current-song similarity, or songs that match recent queue genres and moods.
* Optional Now Playing artwork tap action that opens Lyrics.
* Kreate-inspired lyrics font size selector for the lyrics screen.
* Kreate-inspired lyrics alignment selector with Auto, Start, Center, and End options.
* Kreate-inspired `Tap lyrics to seek` setting can turn lyric-line seeking off while keeping lyrics sharing selection intact.
* Kreate-inspired accent background toggle for the lyrics screen.

### Library and playlist reliability

* Playlist detail pages auto-refresh when local playlist metadata exists but the song cache has not been hydrated yet.
* Playlist list and library play actions refresh empty or partially cached playlists before handing them to the player.

### Data and privacy controls

* Kreate-inspired search history controls: recent searches persist across app restarts, can be cleared from Search, and `Pause search history` under Data & Storage hides history while stopping newly submitted queries from being recorded.
* Kreate-inspired `Auto-download starred songs` under Data & Storage downloads a song when you star it while online. Unstarring does not delete existing downloads.

### LidaClips music videos

* Optional LidaClips integration for matching current Navidrome tracks to music-video clip streams.
* The LidaClips URL defaults to blank, must start with `http://` or `https://`, include a host, use a valid numeric port when a port is present, and omit query or fragment parts; it is validated before connection tests or clip lookup.
* LidaClips API keys are sent as `X-Api-Key` for connection checks, clip lookups, and same-origin Android video stream playback.
* LidaClips settings show backend service counts, health-check failures, recent backend sync failures, and can pause or resume scheduled backend clip syncs; status refreshes after URL or API-key changes settle without embedding the raw API key in refresh keys.
* Now-playing clip lookups are cached briefly and prefetched by LidaClips URL, API-key/header fingerprint, and Navidrome song id, so backend sync changes can appear without restarting Navic.
* The Music Video action stays visible whenever LidaClips is enabled and configured, so cached misses do not hide the refreshable clip screen.
* The no-clip player state has a Refresh action that bypasses the temporary lookup cache after backend sync changes.
* Android Media3 playback shows retryable stream errors with diagnostic error codes, and LidaClips API failures include clearer API-key guidance when the backend returns unauthorized.
* Android Picture-in-Picture, optional landscape video mode, video fit/crop mode, keep-screen-on, remembered clip positions, Playback audio-focus behavior, and Feishin-style music pause/resume are available from existing Playback and LidaClips settings.
* iOS currently shows an unsupported message until a native video player is added.

### Maintenance

* GitHub Actions permissions and vulnerable transitive build dependencies were hardened for the fork's Security & Quality findings.
* The in-app update prompt prefers the release `Navic.apk`, downloads it inside Navic on Android, and launches the system package installer instead of sending the APK URL to a browser.
* Android release builds require the fork's stable release signing secrets and reject `Android Debug` certificates, so public APK updates keep the same package signature.
* GitHub tag releases publish the signed Android APK as soon as the Android job finishes; the optional iOS IPA is attached later only if its packaging job succeeds.
* The Kreate transplant tracking notes live in [docs/kreate-feature-audit.md](docs/kreate-feature-audit.md).

### Traefik Basic Auth setup

1. Open the login screen and tap `Custom server headers`.
2. Enable `Reverse proxy Basic Auth`.
3. Enter the Traefik Basic Auth username and password.
4. Return to login and enter the normal Navidrome URL, username, and password.

Advanced users can leave the Basic Auth toggle off and add a manual `Authorization: Basic <base64(username:password)>` custom header instead.

### WhatsApp training audio setup

Open Settings -> Playback and turn `Respect audio focus` off. Restart music playback if the player service was already running; LidaClips video playback uses the setting when the clip screen opens.

Use Settings -> Playback -> `Playback volume` to lower Navic music without changing Android media volume for WhatsApp or other apps.

### Listening history setup

Open Settings -> Playback -> Behaviour and turn `Pause listening history` on to temporarily stop Navic from sending now-playing and scrobble updates to Navidrome. Turn it off again to resume normal scrobbling without changing your percentage or minimum-duration settings.

### Auto-fill queue setup

Open Settings -> Playback -> Queue and turn `Auto-fill queue` on. Use `Auto-fill queue target` to choose how many songs Navic should keep queued when it refills. `Random library` shuffles across synced songs, `Similar to current song` prefers Navidrome similar-song results and local artist/album/genre/mood matches, and `Recent genres` prefers local songs sharing genres or moods with the recent queue history. Navic skips duplicate queued songs and live radio streams.

### ReplayGain loudness boost setup

Open Settings -> Playback, choose a `ReplayGain` mode, then turn `ReplayGain loudness boost` on. The default stays off; when enabled on Android, Navic keeps normal ReplayGain attenuation in player volume and uses Android loudness enhancement only for positive ReplayGain gain that would otherwise be capped at normal volume. Android device audio-effect support can vary.

### Smart rewind setup

Open Settings -> Playback -> `Smart rewind` to choose the point where the Previous button restarts the current song. The default is `1s`, matching Navic's previous hardcoded behavior. Raising it to `3s` matches Kreate's default.

### Audio fade setup

Open Settings -> Playback -> `Audio fade` to choose a pause/resume fade duration. The default is `Off`, preserving Navic's immediate pause/resume behavior.

### Medley mode setup

Open Settings -> Playback -> `Medley mode` to choose how long each queued song plays before Navic skips to the next song. `Off` is the default and keeps full-track playback.

### Bass boost setup

Open Settings -> Playback and turn `Bass boost` on. Use `Bass boost strength` to adjust the effect from 0% to 100%; the default stored strength is 50%, but the effect stays off until enabled. Android device audio-effect support can vary.

### Audio reverb setup

Open Settings -> Playback -> `Audio reverb` and choose a preset. `Off` is the default. Android device audio-effect support can vary, and the setting applies to the active player service.

### Shuffle queue limit setup

Open Settings -> Playback -> `Shuffle queue limit` to choose how many songs a collection shuffle should start with. `Unlimited` is the default and preserves current behavior. Limits are applied after shuffling, so large playlists and libraries can still surface different songs on later shuffles.

### Song radio setup

Open a song's action sheet or the now-playing menu and choose `Start song radio`. Navic starts the selected song, then queues up to 49 songs. It asks Navidrome for similar-song results first and keeps that server order, then fills any remaining space from locally synced songs ranked by artist, album, genre, and mood metadata. This does not require a new setting.

### Discover queue setup

Open the now-playing menu and choose `Discover queue`. Navic removes upcoming queued songs that are already starred or included in any synced playlist, and shows how many known songs were removed. The current song and earlier queue history are left alone.

### Song row swipe setup

Open Settings -> Playback and turn `Song row swipe actions` on or off. When enabled, `Swipe right` and `Swipe left` can be set to `Add to queue`, `Play next`, or `Disabled`. Defaults preserve Navic's previous behavior: right adds to queue, left plays next.

### Now Playing indicator setup

Open Settings -> Playback and turn `Now Playing indicator` off to hide the animated waveform beside the current song in song lists and the queue. It is on by default to preserve Navic's existing behavior.

### Shake to skip setup

Open Settings -> Playback and turn `Shake to skip` on. Shake detection is available on Android while Navic is open and logged in; it is off by default.

### Volume keys skip tracks setup

Open Settings -> Playback and turn `Volume keys skip tracks` on. While Navic is open on Android, volume up skips to the next queued song and volume down goes to the previous song. It is off by default.

### Sleep timer setup

Open the now-playing song menu or the root top-bar menu and choose `Sleep timer`. Use Settings -> Now Playing -> `Show sleep timer action` to hide or show it in the Now Playing song menu.

### Now Playing song actions setup

Open Settings -> Now Playing -> Actions to hide or show Kreate-style song menu items such as `Download`, `Add to playlist`, `Start song radio`, and `Discover queue`. `Show more action` controls the More button beside the Now Playing title and artist. The Download action uses the same offline download state as song lists, and is hidden for transient radio streams.

### Now Playing background setup

Open Settings -> Now Playing and keep `Background style` set to `Dynamic` to use the animated cover-art background. `Background blur` and `Background dim` adjust the blur strength and dark overlay; defaults are 80dp and 40%, matching the previous Navic look. Turn `Bottom background gradient` on to fade the lower part of the dynamic background into the app background behind the controls. It is off by default.

### Now Playing artwork setup

Open Settings -> Now Playing and turn `Show artwork` off to hide the main cover artwork on the fullscreen player. The setting is on by default, matching current Navic behavior and Kreate's default. Use `Artwork size` to choose Small, Medium, Big, Biggest, or Expanded; `Biggest` is the default and matches the previous Navic layout. `Shrink artwork on pause` is on by default to preserve Navic's existing paused/inactive artwork shrink, and can be turned off to keep the selected artwork size. `Tap artwork for lyrics` is shown only while artwork is enabled.

Turn `Swipe artwork to change songs` off to stop horizontal swipes on the main Now Playing artwork from changing tracks while leaving other swipe controls available.

Turn `Rotate playing artwork` on to spin the active cover while music is playing. It is off by default and does not rotate paused, inactive, or placeholder artwork.

### Now Playing Up next setup

Open Settings -> Now Playing and turn `Show Up next` on. Use `Show Up next artwork` to control whether upcoming songs include cover thumbnails, and use `Up next count` to choose how many upcoming queued songs appear under the progress row. Tapping the preview opens the full Queue sheet. The preview is off by default; artwork is on when the preview is enabled.

### Now Playing seek buttons setup

Open Settings -> Now Playing and turn `Show seek buttons` on. Navic adds back and forward buttons beside the time row. Tap either button for a 10-second seek, or long-press for a 30-second seek. The setting is off by default, so the current Now Playing layout stays unchanged unless you enable it.

### Now Playing remaining time setup

Open Settings -> Now Playing and turn `Show remaining time` on. Navic adds the time left between elapsed time and total duration for finite tracks. Live streams and unknown durations keep the existing two-label display.

### Now Playing controls layout setup

Open Settings -> Now Playing and use `Song info style` to choose between Navic's compact title/artist display and an album-and-artist supporting line. Turn `Show album/artist icons` on to show small album and artist icons beside clickable Now Playing text; it is off by default to keep the existing clean layout. Keep `Technical info style` on Compact for the existing format/sample-rate/bitrate row, or choose Detailed to add source stats such as bit depth, channel count, file size, and ReplayGain when the server provides them. Use `Progress width` to choose a shorter timeline or Expanded full-width timeline; `Biggest` is the default and preserves Navic's existing per-slider spacing. Turn `Swap controls and timeline` on to move the playback buttons above the progress bar and time row. It is off by default, so Navic keeps the existing progress-then-buttons layout unless you change it. Turn `Space playback controls evenly` on to spread the main playback buttons across the row instead of using the compact weighted layout. When Playback Speed is not `1.0x`, the main play button shows the current speed automatically; long-press the same play button to open Playback Speed. Turn `Swipe up controls for queue` on to open Queue from an upward swipe on the controls area, or turn `Tap controls for queue` on to open Queue by tapping empty space there. Use `Toolbar position` to place the Now Playing toolbar at the top or bottom, or choose Hidden for a cleaner fullscreen player. `Show shuffle control` and `Show repeat control` are on by default and can hide those edge buttons while leaving Previous, Play/Pause, and Next visible.

### Mini-player queue button setup

Open Settings -> Bottom Bar -> Mini Player and turn `Show queue button` on. Navic adds a Queue button beside the mini-player playback buttons when a song is active. It is off by default, so the existing mini-player layout stays unchanged unless you enable it.

### Song row indicators setup

Open Settings -> Playback and use `Now Playing indicator` to show or hide the animated waveform beside the current song. Turn `Playlist indicator` on to mark songs that are already present in one of your synced playlists. The playlist marker is hidden inside playlist detail screens, is off by default, and refreshes after in-app add-to-playlist actions.

### Lyrics display setup

Open Settings -> Playback -> Lyrics -> `Lyrics font size` to choose Small, Medium, Large, or Extra large. Medium preserves Navic's previous lyrics text size.

Use `Lyrics alignment` to keep the current automatic LTR/RTL-aware alignment or force Start, Center, or End.

Turn `Tap lyrics to seek` off if tapping synced lyric lines should not jump playback. Lyrics share-selection mode still uses taps to select lines.

Turn `Accent lyrics background` on to tint the lyrics screen with the app accent color.

### LidaClips setup

1. Open Settings -> Data & Storage -> Music video clips.
2. Enable `LidaClips`.
3. Enter your LidaClips base URL with `http://` or `https://`, for example `https://clips.remaxku.eu`. Use only the base origin/path, with a numeric port only when needed; do not include `?query` or `#fragment` text.
4. Enter the LidaClips API key and use `Test connection`.
5. Review `Service status` to see active, official, fallback, backend health, and recent backend failure diagnostics. The status refreshes after URL or API-key edits settle; use `Pause LidaClips sync` if you want the backend to stop scheduled clip processing.
6. Optionally enable `Picture-in-Picture`, `Landscape video mode`, or `Video fit` crop mode on Android. `Pause music while clips play`, `Remember clip position`, and `Keep screen on` are on by default and can be turned off.
7. From the now-playing song menu, use `Play music video` when a matching clip exists.

Android plays the clip in-app through Media3. Navic caches clip lookup results for the active LidaClips URL/API-key fingerprint and prefetches the current now-playing song, so repeated video opens avoid another lookup while still expiring stale hits and misses after a short window without embedding the raw API key in internal cache keys. The now-playing Music Video action remains visible whenever LidaClips is enabled, configured, and not hidden in Now Playing settings; if a track initially has no clip, the music-video screen's Refresh action bypasses the temporary lookup cache so a newly synced backend match can be checked immediately. The LidaClips settings screen reads `/api/v1/health`, `/api/v1/dashboard`, and `/api/v1/control` to show backend health, clip counts, recent sync failures, and sync state, refreshes status after configured URL/API-key changes settle using a fingerprinted key, and writes `/api/v1/control` when `Pause LidaClips sync` changes. Degraded LidaClips health responses are still shown as diagnostics when the backend returns dependency-check details. Lookup, status, and control errors now call out the LidaClips API key when the backend returns unauthorized. LidaClips stream requests include the API key only when the resolved stream URL is on the configured LidaClips origin; absolute external stream URLs do not receive the key. If the video stream fails, the player shows a retryable error with the Media3 error code. LidaClips video playback follows Settings -> Playback -> `Respect audio focus`, so turning that setting off also lets clip audio continue when another app takes audio focus. With Picture-in-Picture enabled, the video can stay visible when you leave Navic. `Landscape video mode` rotates the clip screen to landscape and hides system bars until you leave the screen. `Video fit` defaults to Fit for the full frame; Crop fills the player and may trim edges. By default, Navic follows the Feishin clip-tab behavior: it pauses the active song when the clip opens and resumes that same song when you leave the clip. Turn `Pause music while clips play` off to keep Navic music playing under clip audio. `Remember clip position` resumes the last watched position when reopening the same video, while avoiding positions near the beginning or end. `Keep screen on` keeps the display awake while the clip screen is open. iOS currently shows an unsupported message until a native video player is added.

## Screenshots

|                                       Library                                        |                                       Player                                        |                                       Lyrics                                        |                                       Albums                                        |
|:------------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------:|
| ![](https://github.com/NavicApp/Branding/blob/main/screenshots/library.png?raw=true) | ![](https://github.com/NavicApp/Branding/blob/main/screenshots/player.png?raw=true) | ![](https://github.com/NavicApp/Branding/blob/main/screenshots/lyrics.png?raw=true) | ![](https://github.com/NavicApp/Branding/blob/main/screenshots/albums.png?raw=true) |

## Translating

You can help translate Navic by contributing on [Weblate](https://hosted.weblate.org/engage/navic/).

[![Translation status](https://hosted.weblate.org/widget/navic/navic/svg-badge.svg?threshold=0)](https://hosted.weblate.org/engage/navic/)

## Star History

<a href="https://star-history.com/#ssalggnikool/Navic&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ssalggnikool/Navic&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=ssalggnikool/Navic&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=ssalggnikool/Navic&type=Date" />
 </picture>
</a>

[ADD_TO_OBTAINIUM]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22darkaxt.navic%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FDarkaxt%2FNavic%22%2C%22author%22%3A%22Darkaxt%22%2C%22name%22%3A%22Navic%20Darkaxt%20Fork%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22Navic.%2A%5C%5C.apk%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3Anull%7D "Add to Obtainium"

[ALTSOURCE]: https://stikstore.app/altdirect/?url=https://raw.githubusercontent.com/ssalggnikool/Navic/refs/heads/master/app-repo.json

[LATEST_RELEASE]: https://github.com/Darkaxt/Navic/releases/latest
