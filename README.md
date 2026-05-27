<div align="center">

# Navic

A fork of Navic with reverse-proxy Basic Auth support and configurable Android audio focus.

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

* Reverse proxy Basic Auth for Navidrome/Subsonic servers behind Traefik or another proxy
* Server headers are applied to login, API calls, streaming, downloads, artwork, notifications, lyrics sharing, and now-playing artwork/color loading
* Android playback setting to respect or ignore audio focus, useful when music should keep playing while WhatsApp or another app plays audio
* Playlist detail pages auto-refresh if local playlist metadata is present but the song cache has not been hydrated yet
* Optional LidaClips integration for matching current Navidrome tracks to music-video clip streams
* Android playback toggles adapted from Kreate: skip silence and skip to the next queued song when a stream fails
* Android can optionally resume a paused queue when headphones, USB audio, or Bluetooth audio connect
* Kreate-style Now Playing action visibility toggles for Lyrics, Queue, and Music Video actions

### Traefik Basic Auth setup

1. Open the login screen and tap `Custom server headers`.
2. Enable `Reverse proxy Basic Auth`.
3. Enter the Traefik Basic Auth username and password.
4. Return to login and enter the normal Navidrome URL, username, and password.

Advanced users can leave the Basic Auth toggle off and add a manual `Authorization: Basic <base64(username:password)>` custom header instead.

### WhatsApp training audio setup

Open Settings -> Playback and turn `Respect audio focus` off. Restart playback if the player service was already running.

### LidaClips setup

1. Open Settings -> Data & Storage -> Music video clips.
2. Enable `LidaClips`.
3. Keep `https://clips.remaxku.eu` or enter your LidaClips base URL.
4. Enter the LidaClips API key and use `Test connection`.
5. From the now-playing song menu, use `Play music video` when a matching clip exists.

Android plays the clip in-app through Media3. iOS currently shows an unsupported message until a native video player is added.

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

[ADD_TO_OBTAINIUM]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22darkaxt.navic%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FDarkaxt%2FNavic%22%2C%22author%22%3A%22Darkaxt%22%2C%22name%22%3A%22Navic%20Reverse%20Proxy%20Fork%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22Navic.%2A%5C%5C.apk%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3Anull%7D "Add to Obtainium"

[ALTSOURCE]: https://stikstore.app/altdirect/?url=https://raw.githubusercontent.com/ssalggnikool/Navic/refs/heads/master/app-repo.json

[LATEST_RELEASE]: https://github.com/Darkaxt/Navic/releases/latest
