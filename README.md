<div align="center">

# Navic

A personalized Navidrome client fork with Android playback controls, LidaClips music videos, Aurral integration, and reverse-proxy auth.

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

This fork keeps upstream Navic as the base client and adds features for reverse-proxy setups, Android playback control, LidaClips music-video playback, and self-hosted Aurral discovery/acquisition workflows.

### Settings discovery

* Settings has a search field that filters matching settings into live, editable result rows, with each result showing its Settings path.
* The filtered rows cover common Appearance, Now Playing, Bottom Bar, Playback, Data & Storage, Integrations, LidaClips, Aurral, and Developer preferences without forcing navigation into the owning settings page.

### Reverse proxy and server access

* Reverse proxy Basic Auth fields for Navidrome/Subsonic servers behind Traefik or another proxy.
* Generated `Authorization: Basic ...` headers override a manual custom `Authorization` header only while the Basic Auth toggle is enabled.
* Existing custom server headers remain available for advanced setups.
* Server headers are applied to login, API calls, streaming, downloads, artwork, notifications, lyrics sharing, and now-playing artwork/color loading.
* The Android package id is `darkaxt.navic`, so the fork can install separately from upstream Navic.

### Android playback controls

* `Respect audio focus` can be turned off so Navic music and LidaClips video playback can keep playing while WhatsApp or another app plays audio.
* Kreate-inspired Android media notification action slots can add Shuffle and Repeat buttons to the system media notification while keeping the default notification unchanged until configured.
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
* Kreate-inspired `Auto-fill queue` can append synced Navidrome songs when playback reaches the end of the current queue, defaulting to recent-genre refill while still offering random-library and current-song-similar sources. Recent-genre refill keeps additions inside matching genres or moods whenever matches exist, and Similar mode prefers live Navidrome similar-song results when available.
* Auto-downloading a newly favorited song no longer swaps the active ExoPlayer media item from stream to local file mid-playback, avoiding a sudden restart when the download completes.
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
* LidaClips matches are cached locally before Now Playing video features activate. Once cached, clips can play as a muted cropped Now Playing video background by default; the movie button beside Lyrics promotes the clip into the artwork area with a short artwork-to-video crossfade, starts it at the equivalent song-progress percentage, mutes the underlying song, and lets the video provide audio while Navic's normal controls remain visible.
* Sleep timer access from the now-playing song menu and root top-bar menu.
* Kreate-inspired configurable Now Playing dynamic background blur and dim strength.
* Kreate-inspired optional bottom gradient for the dynamic Now Playing background.
* Kreate-inspired optional main Now Playing artwork visibility, artwork size, and pause-shrink behavior.
* Kreate-inspired Now Playing artwork swipe control for disabling horizontal artwork swipes without disabling mini-player swipes.
* Kreate-inspired optional rotating artwork for the active Now Playing cover, shown as a vinyl-style cover with a static groove/label overlay while the cover spins.
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
* Kreate-inspired optional Now Playing `Up next` preview for upcoming queued songs, with optional artwork thumbnails. On Android it follows Media3's current shuffle/repeat order instead of only the raw queue order.
* Kreate-inspired optional mini-player queue button for opening Queue directly from the mini player.
* Kreate-style mini-player progress can be hidden, shown as a passive bar, or made seekable from Bottom Bar settings.
* Kreate-inspired queue auto-fill can refill with random library songs, Navidrome/local current-song similarity, or songs that match recent queue genres and moods.
* Optional Now Playing artwork tap action can open Lyrics or the MusicBrainz info sheet. When artwork tap already opens MusicBrainz info, the duplicate Now Playing info action is hidden.
* Kreate-inspired lyrics font size selector for the lyrics screen.
* Kreate-inspired `Animate active lyric size` toggle can turn off the active-line grow/shrink effect while keeping lyric highlighting.
* Kreate-inspired lyrics alignment selector with Auto, Start, Center, and End options.
* Kreate-inspired `Tap lyrics to seek` setting can turn lyric-line seeking off while keeping lyrics sharing selection intact.
* Kreate-inspired accent background toggle for the lyrics screen.
* Kreate-inspired optional lyrics artwork can show the current cover above lyrics without changing the default lyrics layout.
* Navidrome/Subsonic lyrics are now the default first lyrics source. Translated LRC files that use duplicate timestamps are merged into one original-plus-translation lyric block, and stale cached external lyrics no longer block a higher-priority server lyrics check.

### Library and playlist reliability

* Kreate-inspired `Quick Picks` on the Library screen surfaces synced Navidrome songs from frequent plays, ratings, and recently added albums, with a fallback to the rest of the local song cache. Appearance settings can hide the row, change how many Quick Picks songs are kept, or exclude tracks shorter than a chosen minimum duration.
* Playlist detail pages auto-refresh when local playlist metadata exists but the song cache has not been hydrated yet.
* Playlist list and library play actions refresh empty or partially cached playlists before handing them to the player.
* Writable normal playlists can be deleted from the playlist detail overflow menu. Aurral `[A]` station playlists stay protected from that detail delete action.
* Playlists whose names start with `[A] ` are shown separately as `Stations`, with the marker hidden in station cards, station lists, playlist details, and action sheets.
* Synthetic `[Unknown Artist]` entries are hidden from artist rows, artist lists, and artist search results while the underlying songs and albums remain available.
* Album browsing defaults to release-year ordering from newest to oldest, with unknown years kept after dated albums. The same year ordering is used for artist album rows, More by Artist rows, and Aurral-merged local/missing album rows.
* Artist lists now have a Kreate-style sort sheet for alphabetical, starred, and random artist views, including ascending/descending direction.
* Playlist and station detail pages now have a Kreate-style song sort sheet for manual order, title, artist, album, and duration while preserving manual playlist order by default.

### Data and privacy controls

* Optional MusicBrainz/Cover Art Archive support can fetch public recording metadata when a song starts playing, even when Navidrome already has artwork. Cover Art Archive image fallback runs when a song has no synced cover, no synced album cover, or the app detects that the server cover failed to load in Now Playing, Lyrics, or the mini-player. Navidrome/Subsonic artwork and album-cover fallback remain first, successful and missing external lookups are cached locally, expired cached entries are not exposed to UI artwork or MusicBrainz info metadata, duplicate cache entries are normalized so the newest song entry wins for playback reuse and UI state, and cached fallback artwork/metadata are hidden while the MusicBrainz setting is off without deleting the cache. Settings -> Integrations contains the `MusicBrainz and Cover Art Archive` toggle, while Settings -> Data & Storage shows a MusicBrainz cache readout with cached song, artwork, metadata, and missing-result counts. Settings -> Data & Storage -> Danger Zone also has a dedicated `Clear MusicBrainz cache` action for cached MusicBrainz metadata, Cover Art Archive lookup results, and missing-result entries. Cached fallback artwork is reused by the now-playing artwork/background colors, mini-player, lyrics artwork/share preview, Android now-playing broadcast, and the MusicBrainz info screen. When synced MBIDs are missing or malformed, Navic can conservatively search MusicBrainz by title and artist and only accepts high-confidence recording matches; synthetic `[Unknown Artist]` labels are not used for fallback search, while valid synced MBIDs still resolve directly. Malformed synced recording, release, or release-group MBIDs are ignored for direct MusicBrainz and Cover Art Archive lookups instead of being sent to external services. Recording lookups request release, release-group, URL, selected-release, selected-release-group, and linked-work relationship metadata, so the Now Playing MusicBrainz info action can show local track/file details, release-group fields, release-group type, MusicBrainz disambiguation notes, and safe external links such as Discogs, Songfacts, Wikipedia, and Wikidata when MusicBrainz exposes them on the recording, linked work, selected release, or selected release-group. MusicBrainz and external URLs are shown as compact buttons near the artwork instead of dominant metadata rows, and the implementation hides the old source-priority row. Older cached MusicBrainz metadata schemas are refreshed on playback so newly supported fields are not hidden by stale cache entries. If the local album title is known, Navic tries an album-specific MusicBrainz recording search before the broad title/artist fallback; when multiple MusicBrainz releases are available, local album-title matches are also tried first for artwork and metadata.
* Settings search filters matching preferences into live setting rows, so switches, selectors, sliders, and text fields remain usable directly from the result list while each result shows its owning Settings path.
* Kreate-inspired Data & Storage cache/readout labels now use one human-readable byte formatter, so small image caches are shown as bytes or KB instead of disappearing into `0 MB`.
* Kreate-inspired search history controls: recent searches persist across app restarts, can be cleared from Search, and `Pause search history` under Data & Storage hides history while stopping newly submitted queries from being recorded.
* Kreate-inspired `Auto-download starred songs` under Data & Storage downloads a song when you star it while online. Unstarring does not delete existing downloads.
* Kreate-inspired `Auto-download starred albums` under Data & Storage downloads missing album songs when you star an album while online. Unstarring does not delete existing downloads.
* All song downloads route through the same download queue, including individual songs, albums, playlists, stations, artists, full-library downloads, retries, and startup recovery. If the app process restarts while songs are queued, Navic resumes queued songs that still exist in the synced library and clears stale queued entries. The queue dialog can inspect queued, downloading, and failed songs, cancel pending work, and retry failed downloads that still exist locally. `Parallel downloads` defaults to 3 songs at a time and can be adjusted under Data & Storage.
* Data & Storage shows persistent LidaClips offline video clips separately from the temporary video cache. Clearing all downloads also clears those persistent clips because they are paired with offline music.
* Destructive Data & Storage danger-zone actions now require confirmation before clearing caches, pending sync actions, downloads, or the local database.

### LidaClips music videos

* Optional LidaClips integration for matching current Navidrome tracks to music-video clip streams.
* The LidaClips URL defaults to blank, must start with `http://` or `https://`, include a host, use a valid numeric port when a port is present, and omit embedded credentials, query, or fragment parts; it is validated before connection tests or clip lookup.
* LidaClips API keys are sent as `X-Api-Key` for connection checks, clip lookups, video cache downloads, and Android video stream playback under the configured LidaClips origin/path.
* LidaClips settings show backend service counts, recent indexed clips, health-check failures, recent backend sync failures, and can pause or resume scheduled backend clip syncs; status refreshes after URL or API-key changes settle without embedding the raw API key in refresh keys. Recent clip and failure diagnostics trim noisy values and omit synthetic `[Unknown Artist]` labels while keeping track, album, quality, and Lidarr fallback context visible.
* Now-playing clip lookups are cached briefly and prefetched by LidaClips URL, API-key/header fingerprint, Navidrome song id, and lookup mode, so backend sync changes can appear without restarting Navic.
* Now Playing background and promoted artwork video only activate after the matching clip has been fully cached into Navic's temporary LidaClips video cache. The `Video cache size` setting defaults to 512 MB, can be turned off, and is separate from offline song downloads. LidaClips can also use cached blurred clip backgrounds on Lyrics and MusicBrainz info screens through independent settings; both screens fall back to the normal artwork background unless a cached clip is ready and has rendered its first frame.
* When `Download clips with music` is enabled, completed song downloads try to resolve and save a matching LidaClips clip into persistent app storage; clip failures do not fail the song download. Navic also saves a matching clip when one is discovered for an already-downloaded song, prefers persistent clips before the temporary cache or network stream, and clears a song's persistent clip files when that offline song is deleted.
* LidaClips clip lookup now falls back from Navidrome song-id matching to the backend's artist/album/track search endpoint when the direct id mapping is missing and the local song has a real title plus non-synthetic artist; direct-id misses discovered during fallback are cached separately without suppressing metadata fallback.
* The Music Video action stays visible whenever LidaClips is enabled and configured, so cached misses do not hide the refreshable clip screen.
* The Play music video action is available from normal song action sheets, not only the Now Playing menu, while transient radio entries are filtered out.
* The no-clip player state has a Refresh action that bypasses the temporary lookup cache after backend sync changes.
* Android Media3 playback shows retryable stream errors with diagnostic error codes, and LidaClips API failures include clearer API-key guidance when the backend returns unauthorized.
* Android Picture-in-Picture, optional landscape video mode, video fit/crop mode, video cache size, keep-screen-on, remembered clip positions, Playback audio-focus behavior, and Feishin-style music pause/resume are available from existing Playback and LidaClips settings.
* iOS currently shows an unsupported message until a native video player is added.

### Aurral integration

* Settings -> Integrations now has a native `Aurral` screen for the self-hosted Aurral backend instead of treating it as an external link.
* The Aurral URL defaults to blank, must start with `http://` or `https://`, include a host, use a valid numeric port when present, and omit embedded credentials, query, or fragment parts.
* Optional Aurral username/password fields generate a Basic Auth `Authorization` header for Aurral API requests. The credentials are separate from Navidrome reverse-proxy Basic Auth.
* Aurral workflows are integrated into the standard Library, Artists, and Stations surfaces instead of a separate Library shortcut. Settings -> Integrations keeps one `Aurral` configuration entry for setup and diagnostics.
* The Aurral settings screen can test connectivity and show service diagnostics including backend health, version, authentication state, signed-in user, native-action permissions, Lidarr configuration, discovery recommendation count, Flow counts, shared playlist count, request count, current Flow track state, and a compact virtual acquisition queue.
* The bottom `Activity` tab centralizes live integration work across Navic song downloads, Aurral album acquisitions and Flow track states, and LidaClips recent failures or failed health checks.
* Native Aurral discovery loads recommendations from `/api/discover` into a normal Library `Discover` artist row after the local Artists row. The Library row links to the native Aurral hub, where the Discover section can expand beyond the first visible recommendations. Missing Discover artist artwork is hydrated from exact Aurral artist search matches without replacing the recommendation identity. The Artists page has a `+` action for native Aurral artist search backed by `/api/search/artists`, and the search rows use Aurral-resolved artwork plus recommendation/catalog context. Recommended artists that do not exist locally open into the native Aurral artist/catalog page and can be sent to Aurral monitoring when the configured Aurral user has artist-add permission. Aurral album search backed by `/api/search?scope=album` remains available in the native hub and opens album results into the same album-like missing-album detail page used by artist catalogs. Album search results are deduped and sorted by release year from newest to oldest, with undated results last. Stations has a `+` action for creating new Aurral Flows, while the hub remains available internally for detailed Flow operations: enable or disable existing Flows, trigger a Flow run when the backend exposes the required permissions, play the matching synced Navidrome `[A]` Station directly when it has songs, play ready Flow jobs directly from Aurral while the Station has not synced yet, and jump to that Station detail page when one already exists locally.
* Discovery artist recommendations jump to the native local artist page when the recommended artist already exists in the Navidrome library. Aurral album recommendations are folded into artist recommendation rows, and each matching artist page shows a dynamic `Recommendations` album row only after Aurral discovery data has been verified.
* Artist pages enrich local Navidrome artists with Aurral data by trying the local MusicBrainz artist id before weaker Aurral Discover name matches, preventing ambiguous Discover identities from taking over an existing local artist. Local albums and Aurral-only missing albums are merged into one newest-first Albums row, local albums win duplicate matches, missing albums use grayscale Aurral-resolved covers, existing request status is shown, small acquisition progress strips appear over requested and locally matched album covers, an eye action monitors the artist in Aurral after the current monitor state is confirmed, and one unified similar-artists row merges Aurral image/match data with local Navidrome similar artists. Monitor confirmation checks Aurral's library artist endpoint as a fallback, so existing Aurral artists should resolve to monitored or unmonitored instead of staying in the disabled question-mark state. Similar artists already in the Navidrome library open the local artist page, while external-only artists open the native Aurral artist/catalog page with Aurral artwork, missing albums, previews, and acquisition actions.
* Missing Aurral albums now open an album-like detail page instead of exposing a cramped request button in the artist carousel. The detail page resolves Aurral release-group cover JSON into a real cached image URL, sends Aurral Basic Auth headers only to Aurral-hosted image URLs, shows matching 30-second previews for that release group, and uses the primary action to trigger Aurral acquisition.
* Album tiles can show Aurral acquisition progress strips by matching current Aurral requests to local albums by MusicBrainz release-group id first, then normalized artist plus album name. The album list, Library recent-albums row, Search album results, and album-detail `More by artist` row refresh that acquisition state once at the screen level, avoiding per-tile Aurral polling.
* Album requests from missing-album pages use Aurral's acquisition pipeline and update the page to `requested` immediately. Existing Aurral request rows are normalized into queued, active, available, or failed acquisition states so artist pages and settings show the same pipeline state.
* Direct Aurral Flow playback uses Aurral session or short stream query tokens for media URLs instead of sharing Navidrome proxy headers with ExoPlayer. Synthetic Aurral Flow song ids are kept out of Navidrome scrobbling/listening-history writes.
* Settings search includes live Aurral rows for enabling the integration, URL, username, and password.

### Maintenance

* GitHub Actions permissions and vulnerable transitive build dependencies were hardened for the fork's Security & Quality findings.
* The in-app update prompt prefers the release `Navic.apk`, downloads it inside Navic on Android with visible progress, verifies the GitHub asset SHA-256 digest when present, and launches the system package installer instead of sending the APK URL to a browser. Tapping About -> version forces an update check; if no newer release exists, Navic confirms that you are already on the latest version.
* Android registers the Coil image-loader singleton from the `Application` before Compose starts, avoiding the duplicate singleton-factory crash seen when early artwork/media-session loads race the UI bootstrap.
* `v1.0.10-beta1` intentionally jumps the patch number as an updater bridge for alpha builds whose older updater mis-sorted beta tags.
* Android release builds require the fork's stable release signing secrets and pin the expected release certificate fingerprint, so public APK updates keep the same package signature.
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

### Media notification setup

Open Settings -> Playback and set `Notification action 1` / `Notification action 2` to `Shuffle`, `Repeat`, or `Disabled`. The settings affect the Android media notification after playback restarts; duplicate actions are ignored.

### Listening history setup

Open Settings -> Playback -> Behaviour and turn `Pause listening history` on to temporarily stop Navic from sending now-playing and scrobble updates to Navidrome. Turn it off again to resume normal scrobbling without changing your percentage or minimum-duration settings.

### Auto-fill queue setup

Open Settings -> Playback -> Queue and turn `Auto-fill queue` on. Use `Auto-fill queue target` to choose how many songs Navic should keep queued when it refills. New configurations default to `Recent genres`, which uses local songs sharing genres or moods with the recent queue history whenever matches exist; it falls back only when no synced candidates match the recent listening context. `Similar to current song` prefers Navidrome similar-song results and local artist/album/genre/mood matches, while `Random library` shuffles across synced songs. Navic skips duplicate queued songs and live radio streams.

### Download queue setup

Use the normal Download action on songs, albums, playlists, stations, or artist pages. Navic marks pending songs as queued and routes every song through the same queue before any network download starts. Settings -> Data & Storage shows a `Download queue` count for queued or currently downloading songs; tap it to inspect queued, downloading, and failed rows, retry failed downloads that still exist locally, or cancel pending downloads. Use `Parallel downloads` to limit concurrent song downloads; the default is 3. Restart recovery resumes queued songs that still exist locally, but it does not yet preserve the original per-collection grouping after the app process is killed.

### MusicBrainz metadata and artwork setup

Open Settings -> Integrations and turn `MusicBrainz and Cover Art Archive` on. Navic fetches public MusicBrainz recording/release/release-group metadata only when a song starts playing or when the current song's MusicBrainz info screen opens, including songs that already have Navidrome artwork. Cover Art Archive image fallback remains stricter: it only tries external cover art when the currently playing song has no song cover, no synced album cover, or a server cover failed to load. Navic caches found and missing results locally, uses at most one MusicBrainz/CAA request per second, does not scan the full library, and uses a public read-only User-Agent rather than bundled OAuth credentials. If the song does not have a valid synced recording MBID, Navic searches MusicBrainz by title and artist and accepts only high-confidence recording matches before trying Cover Art Archive release artwork; `[Unknown Artist]` is treated as synthetic local metadata and is skipped for fallback search. Malformed synced recording, release, or release-group MBIDs are ignored for direct lookup decisions so bad local tags do not produce malformed MusicBrainz/CAA requests. When the local album title is available, Navic first searches MusicBrainz recordings constrained to releases with that album title, then falls back to the broader title/artist search. When MusicBrainz returns several releases for a recording, Navic tries releases whose title or release-group title matches the local album title before falling back to MusicBrainz order. Cached MusicBrainz metadata appears from the Now Playing `MusicBrainz info` action or the Now Playing artwork Track Info tap action after the song has been resolved during playback, combining local track/file details with MusicBrainz disambiguation notes, release-group type, and safe external relation links such as Discogs, Songfacts, Wikipedia, and Wikidata when MusicBrainz has them on the recording, linked work, selected release, or selected release-group. MusicBrainz and external URLs are exposed as compact buttons near the cover. Older cached misses without metadata are refreshed when metadata lookup is now possible, older metadata schemas are refreshed once when playback needs the newer fields, expired cache entries are hidden from UI artwork and MusicBrainz info until playback refreshes them, and turning the MusicBrainz setting off immediately hides cached fallback artwork/metadata without clearing the cache. Settings -> Data & Storage -> Danger Zone -> `Clear MusicBrainz cache` clears cached MusicBrainz metadata, Cover Art Archive lookup results, and missing-result entries without clearing the normal Coil image cache.

### Aurral setup

Open Settings -> Integrations -> `Aurral`, enable the integration, enter your Aurral URL, and optionally enter the Aurral username/password if your backend requires auth. Use `Test connection` to verify the endpoint, then check `Service status` and `Acquisition queue` for health, Flow, discovery, Lidarr, and request diagnostics. Once configured, Library shows Aurral Discover artists after the local Artists row; tap `See all` to open the hub and expand beyond the first visible recommendations. A recommended artist that already exists locally opens the native artist page, with Aurral album recommendations and monitor state resolved there from the local artist identity before weaker Discover matches. Open Artists and tap `+` beside sort to search Aurral artists and optionally monitor them. Open Stations and tap `+` to create a new Aurral Flow that will sync back as a `[A]` Station after Navidrome refreshes. Artist pages with MusicBrainz artist ids or verified Aurral discovery matches can show local and missing albums in one deduped newest-first Albums row, monitoring, acquisition progress strips, request status, and unified similar artists from this configured connection. Tap a missing album to open its album-like page with resolved cover art, 30-second previews when Aurral can match them, and an acquisition action. Album grids and the Library recent-albums row can also show acquisition progress when a local album matches an Aurral request.

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

### Quick Picks setup

The Library screen shows `Quick Picks` when synced songs are available. Tap a card to play that song immediately, long-press it for the normal song actions, or use `See all` to open the full Quick Picks song list. Open Settings -> Appearance -> Library to hide the Quick Picks row, choose whether Navic keeps 10, 20, 30, or 50 songs in Quick Picks, or set `Quick Picks minimum duration` to filter out short intros, interludes, or skits.

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

Open Settings -> Now Playing and turn `Show artwork` off to hide the main cover artwork on the fullscreen player. The setting is on by default, matching current Navic behavior and Kreate's default. Use `Artwork size` to choose Small, Medium, Big, Biggest, or Expanded; `Biggest` is the default and matches the previous Navic layout. Use `Artwork tap action` to choose whether a tap opens nothing, Lyrics, or Track Info. `Shrink artwork on pause` is on by default to preserve Navic's existing paused/inactive artwork shrink, and can be turned off to keep the selected artwork size.

Turn `Swipe artwork to change songs` off to stop horizontal swipes on the main Now Playing artwork from changing tracks while leaving other swipe controls available.

Turn `Rotate playing artwork` on to spin the active cover while music is playing. It is off by default and does not rotate paused, inactive, or placeholder artwork. When active, Navic temporarily renders the cover as a round disc with a center marker so the motion is visible even for square album art.

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

### Mini-player progress setup

Open Settings -> Bottom Bar -> Mini Player -> `Mini player progress style`. `Hidden` removes the progress strip, `Visible` shows playback progress, and `Seekable` lets you drag the strip to seek within the current song.

### Song row indicators setup

Open Settings -> Playback and use `Now Playing indicator` to show or hide the animated waveform beside the current song. Turn `Playlist indicator` on to mark songs that are already present in one of your synced playlists. The playlist marker is hidden inside playlist detail screens, is off by default, and refreshes after in-app add-to-playlist actions.

### Lyrics display setup

Navic defaults to Settings -> Playback -> Lyrics provider priority with `Subsonic` first, so lyrics stored beside your Navidrome tracks are preferred before external providers. If the old default order was saved before this release, Navic migrates that untouched order to Subsonic-first while preserving deliberate custom ordering.

Translated `.lrc` files that repeat the same timestamp for original and translated text are shown as a single lyric block, so both lines advance together.

Open Settings -> Playback -> Lyrics -> `Lyrics font size` to choose Small, Medium, Large, or Extra large. Medium preserves Navic's previous lyrics text size. Turn `Animate active lyric size` off if you want synced lyrics to keep a stable line size while they highlight.

Use `Lyrics alignment` to keep the current automatic LTR/RTL-aware alignment or force Start, Center, or End.

Turn `Tap lyrics to seek` off if tapping synced lyric lines should not jump playback. Lyrics share-selection mode still uses taps to select lines.

Turn `Accent lyrics background` on to tint the lyrics screen with the app accent color.

Turn `Show lyrics artwork` on to show the current song cover above the lyrics when cover art is available. It is off by default.

### LidaClips setup

1. Open Settings -> Integrations -> Music video clips.
2. Enable `LidaClips`.
3. Enter your LidaClips base URL with `http://` or `https://`, for example `https://clips.remaxku.eu`. Use only the base origin/path, with a numeric port only when needed; do not include embedded credentials, `?query`, or `#fragment` text.
4. Enter the LidaClips API key and use `Test connection`.
5. Review `Service status` to see active, official, fallback, recent clips, backend health, and recent backend failure diagnostics. The status refreshes after URL or API-key edits settle; use `Pause LidaClips sync` if you want the backend to stop scheduled clip processing.
6. Use `Now Playing background` to choose Off, Blurred, or Normal. Blurred is the default; background video is muted and cropped.
7. Use `Video cache size` to choose how much temporary storage Now Playing clips may use. The default is 512 MB; `Off` disables Now Playing video activation until this is turned back on.
8. Optionally enable `Download clips with music` if offline song downloads should also save matching clips into persistent offline storage.
9. Optionally enable `Picture-in-Picture`, `Landscape video mode`, or `Video fit` crop mode on Android. `Pause music while clips play`, `Remember clip position`, and `Keep screen on` are on by default and can be turned off.
10. When a matching clip has finished caching, the Now Playing toolbar shows a movie button beside Lyrics. Tap it to move the clip into the artwork area with video audio; tap it again to restore the normal artwork/background mode and song audio.

Android plays clips in-app through Media3. Navic caches clip lookup results for the active LidaClips URL/API-key fingerprint, Navidrome song id, and lookup mode, and refreshes the current Now Playing song through the same cache-aware lookup path. Clip lookup tries `/api/v1/navidrome/{songId}/clip` first, then falls back to `/api/v1/clips` with local artist, album, and title only when the song has a real title and non-synthetic artist. LidaClips stream requests include the API key only when the resolved stream URL is on the configured LidaClips origin; absolute external stream URLs do not receive the key. Now Playing video uses the local cached file, keeps the normal cover/background visible until Media3 renders the first video frame, crossfades the ready video over the artwork, and prunes older temporary clips after each cache write. Foreground clips use Fit or Crop from `Video fit`, background clips always use Crop, and foreground promotion seeks to the same percentage through the clip as the current song progress. Foreground promotion hides embedded video controls, mutes the underlying song only after the first video frame is ready, follows Navic's main play/pause state, and uses Settings -> Playback -> `Respect audio focus`; background video is muted and does not request audio focus. iOS currently shows an unsupported message until a native video player is added.

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
