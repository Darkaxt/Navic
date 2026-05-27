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
* Kreate-inspired playback toggles for `Skip silence` and `Skip media on error`.
* Optional resume of a paused queue when headphones, USB audio, or Bluetooth audio connect.
* Optional pause between songs after automatic track changes.
* Kreate-inspired `Smart rewind` setting for when the Previous button restarts the current song instead of jumping back.
* Optional pause/resume behavior when Android media volume is muted to zero and then restored.
* Kreate-inspired Android audio fade durations for smoother pause and resume.
* Kreate-inspired Android bass boost with an adjustable strength slider.
* Persistent queue controls, including optional playback resume on startup.
* Android system equalizer shortcut in Playback settings and the now-playing song menu.
* Now Playing action visibility toggles for Lyrics, Queue, Music Video, Playback Speed, and Equalizer actions.
* Optional Now Playing artwork tap action that opens Lyrics.

### Library and playlist reliability

* Playlist detail pages auto-refresh when local playlist metadata exists but the song cache has not been hydrated yet.
* Playlist list and library play actions refresh empty or partially cached playlists before handing them to the player.

### Data and privacy controls

* Kreate-inspired search history controls: recent searches persist across app restarts, can be cleared from Search, and `Pause search history` under Data & Storage hides history while stopping newly submitted queries from being recorded.

### LidaClips music videos

* Optional LidaClips integration for matching current Navidrome tracks to music-video clip streams.
* The LidaClips URL defaults to blank, must start with `http://` or `https://`, and is validated before connection tests or clip lookup.
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
* The Kreate transplant tracking notes live in [docs/kreate-feature-audit.md](docs/kreate-feature-audit.md).

### Traefik Basic Auth setup

1. Open the login screen and tap `Custom server headers`.
2. Enable `Reverse proxy Basic Auth`.
3. Enter the Traefik Basic Auth username and password.
4. Return to login and enter the normal Navidrome URL, username, and password.

Advanced users can leave the Basic Auth toggle off and add a manual `Authorization: Basic <base64(username:password)>` custom header instead.

### WhatsApp training audio setup

Open Settings -> Playback and turn `Respect audio focus` off. Restart music playback if the player service was already running; LidaClips video playback uses the setting when the clip screen opens.

### Smart rewind setup

Open Settings -> Playback -> `Smart rewind` to choose the point where the Previous button restarts the current song. The default is `1s`, matching Navic's previous hardcoded behavior. Raising it to `3s` matches Kreate's default.

### Audio fade setup

Open Settings -> Playback -> `Audio fade` to choose a pause/resume fade duration. The default is `Off`, preserving Navic's immediate pause/resume behavior.

### Bass boost setup

Open Settings -> Playback and turn `Bass boost` on. Use `Bass boost strength` to adjust the effect from 0% to 100%; the default stored strength is 50%, but the effect stays off until enabled. Android device audio-effect support can vary.

### LidaClips setup

1. Open Settings -> Data & Storage -> Music video clips.
2. Enable `LidaClips`.
3. Enter your LidaClips base URL with `http://` or `https://`, for example `https://clips.remaxku.eu`.
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
