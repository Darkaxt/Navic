# Aurral Integration Design

## Goal

Make Aurral a first-class Android integration in this Navic fork, alongside LidaClips and MusicBrainz, without importing YouTube Music or InnerTube-specific Kreate behavior. The target product is a selfhosted Spotify-like client backed by Navidrome/Subsonic for playback/library state, LidaClips for music videos, MusicBrainz/Cover Art Archive for metadata enrichment, and Aurral for discovery, acquisition, and Flow generation.

## Current State

LidaClips is functionally close to complete for Android now-playing video use: settings, diagnostics, clip lookup, local temporary video cache, background video, foreground promotion, Media3 playback, audio handoff, and app controls are implemented. Persistent/offline clip ownership is implemented: clips discovered for already-downloaded songs are saved under separate app storage, users can opt into saving matching clips after song downloads complete, persistent clips are preferred before the temporary cache, visible in Data & Storage, and cleared with offline music.

The useful Kreate player feature set is mostly adapted. Remaining Kreate work should be finishing player-quality gaps rather than copying more settings: real-device smoke testing for Android effects, shuffle/repeat-aware Up Next behavior, and any discovery UI that can be backed by Navidrome or Aurral instead of YouTube Music.

Aurral exposes the required native API surfaces:

- `/api/health` and `/api/health/bootstrap` for service status, auth requirements, configured providers, discovery state, and user/permission context.
- `/api/auth/login` and `/api/auth/me` for bearer sessions.
- `/api/search`, `/api/search/artists`, `/api/artists/:mbid`, `/api/artists/release-group/:mbid/tracks`, and cover/similar/preview endpoints for catalog browsing.
- `/api/library/artists`, `/api/library/albums`, `/api/library/tracks`, `/api/library/albums/request`, and download/search endpoints for acquisition actions.
- `/api/requests` for acquisition status.
- `/api/discover` and related/tag endpoints for recommendation browsing.
- `/api/weekly-flow/status`, `/api/weekly-flow/start/:flowId`, `/api/weekly-flow/jobs/:flowId`, `/api/weekly-flow/stream/:jobId`, and `/api/weekly-flow/artwork/:playlistId` for Flow state and playback.

## Approach Options

### Option A: Navidrome-only Aurral

Use only the playlists that Aurral writes into Navidrome, such as `[A]` stations. This keeps all playback as normal Subsonic songs, so favorites, downloads, artwork, scrobbling, and queue behavior stay simple.

Tradeoff: it hides most of Aurral's value inside the existing playlist row and depends on Aurral downloads plus Navidrome scans before anything is playable. It does not feel comparable to LidaClips as a native integration.

### Option B: Aurral-direct Client

Build native screens around Aurral and play Flow jobs directly from `/api/weekly-flow/stream/:jobId`.

Tradeoff: this provides immediate Flow playback, but those tracks are transient external streams from Navic's point of view. Favorite, Navidrome download, playlist membership, and normal library actions are not always available until Navidrome has scanned the files.

### Option C: Hybrid Native Aurral

Use Aurral directly for browsing, acquisition, status, and immediate Flow playback, while treating Navidrome `[A]` Stations as the canonical long-lived playback/library representation when available.

This is the recommended approach. It preserves Navidrome as the source of truth for normal music playback and still makes Aurral feel native instead of forcing the user to wait for a scan or open a web UI.

## Selected Design

### Settings and Auth

Add `Settings -> Integrations -> Aurral` with:

- Enable Aurral.
- Aurral base URL, default blank.
- Username and password fields for Aurral local-user or legacy Basic Auth.
- Connection test.
- Service status/diagnostics: version, auth mode, signed-in user, key permissions, Lidarr configured, discovery configured, Flow counts, request counts, and latest failure message when available.

The Aurral URL rules should mirror LidaClips: require `http://` or `https://`, require a host, allow an optional path prefix, reject embedded credentials, query, and fragment values, and avoid sending credentials to unrelated origins.

Navic should not persist Aurral bearer tokens. It should store the configured username/password in the same preferences layer used for LidaClips and reverse-proxy settings, then keep bearer sessions in memory. API calls should:

1. Use a cached bearer token when one exists.
2. If username/password are present and no valid token exists, call `/api/auth/login`.
3. Fall back to direct Basic Auth for simple API requests if login is unavailable.
4. Allow blank credentials for Aurral deployments that intentionally use trusted local-network bypass or proxy-auth behavior.

For Flow media URLs, prefer bearer-token query URLs: `/api/weekly-flow/stream/{jobId}?token={sessionToken}` and `/api/weekly-flow/artwork/{playlistId}?token={sessionToken}`. This avoids an `Authorization` header collision with Navidrome reverse-proxy Basic Auth in ExoPlayer and Coil. If only short stream tokens are available later, those should be refreshed before seek/retry because Aurral stream tokens currently expire quickly.

### Native Surfaces

Add an Aurral hub reachable from the Library screen and from the Integrations settings row. The Library entry should be visible when Aurral is enabled and configured; settings remains the place for credentials and diagnostics.

### Artwork Authority

When Aurral is enabled and configured, Aurral artwork is authoritative over Navidrome artwork for every user-facing music surface that can resolve both. Navidrome/Subsonic cover art remains the fallback and playback/library identity source, but it must not be rendered directly from song or artist rows when an Aurral artist image is known or can be resolved from the shared artist-photo cache.

Required pipeline:

1. Resolve cached Aurral artist photos first, using Aurral request headers for protected image URLs.
2. Fall back to Navidrome cover art only when no Aurral artist image is available or Aurral is disabled.
3. Keep direct Aurral screens on their native Aurral image URLs.
4. Treat new direct `song.coverArtId` or `artist.coverArtId` UI rendering as a regression unless it is only an input to an Aurral-first resolver.

Guard: `AurralFirstArtworkSourceTest` must include any new song or artist artwork surface. It should fail when a user-facing surface renders raw `song.coverArtId`, raw `artist.coverArtId`, or stale `artist.artistImageUrl` instead of going through the shared Aurral-first artwork resolver.

Hydration is part of the same contract. When Aurral is enabled, background artist-photo hydration must still run even if the stored artwork preference is `NativeOnly` or `NativeFirst`; those preferences only matter again after Aurral is disabled. `MostPlayedShortcutArtworkPolicyTest` guards this so Navidrome cannot block a later Aurral photo from replacing an initial server image.

The hub should have three native sections:

- Discover: Aurral recommendations and tag/related discovery, with artist/album cards.
- Requests: queued, processing, available, and failed acquisition items.
- Flows: dynamic Flow and static/shared playlist cards, with status, artwork, queued/done/failed counts, refresh/start actions, and play/open actions.

Artist and album acquisition should be native:

- Search Aurral catalog by artist, album, or tag.
- Open an Aurral artist detail screen with catalog/release groups, similar artists, library state, and acquisition actions.
- Add artist to Lidarr through `/api/library/artists`.
- Request album through `/api/library/albums/request`.
- Show request state through `/api/requests` and library lookup endpoints.

### Flow Playback

Aurral Flow playback should be hybrid:

- If a matching Navidrome `[A]` Station playlist exists and has songs, prefer the existing Navic playlist/station playback path.
- If the Aurral Flow has ready `done` jobs before Navidrome has scanned them, offer direct playback using generated Aurral stream URLs.
- Direct Aurral tracks should use a stable synthetic id prefix such as `aurral_flow_`, carry track/artist/album metadata, use Aurral artwork where available, and clearly disable or hide actions that require stable Navidrome song ids.
- Once the same track exists in Navidrome, normal song actions should be available through the Navidrome item instead of the transient Aurral item.

This requires a small extension to Navic's Android player media-item policy. Today only radio items can use a direct `filePath`/stream URL. Aurral direct playback should add an explicit external stream path instead of overloading radio identity.

### LidaClips Completion

LidaClips should be considered complete for the current video playback surface after one Android release cycle of smoke testing. Offline clip ownership is now implemented:

- Let users persist a clip alongside an offline song/album when a matching clip exists.
- Keep persistent clips separate from the temporary Now Playing cache.
- Use the persistent clip before network lookup/cache fetch.
- Show clear storage usage under Data & Storage.
- Optionally resolve and save matching clips after song downloads complete.

This is not required before the first Aurral slice unless the Aurral work touches shared integration storage UI.

### Kreate Completion

The Kreate work should stop being a broad transplant audit and become a short player-quality checklist:

- Make Up Next preview match the actual upcoming order for shuffle and repeat-all modes.
- Smoke-test bass boost, reverb, system equalizer, and ReplayGain loudness boost on the Android device.
- Treat Aurral Discover/Flows as the selfhosted replacement for broader Kreate discovery features.
- Do not import YouTube, InnerTube, YouTube Music auth, or YouTube-specific video/player code.

### Error Handling

Aurral UI should distinguish:

- Not configured: settings needed.
- Unreachable: network/base URL problem.
- Unauthorized: username/password/session problem.
- Forbidden: logged in but missing permissions such as `accessFlow`, `addArtist`, or `addAlbum`.
- Not ready: Flow exists but tracks are still queued/downloading.
- Empty Flow: generation ran but found no matching tracks.
- Navidrome pending: Aurral has files but Navidrome has not scanned the `[A]` Station yet.

Failures should be shown inline on the relevant screen and surfaced in settings diagnostics, not only as generic snackbars.

### Testing

Pure policy tests should cover:

- Aurral URL normalization and invalid URL rejection.
- Auth header/session precedence and credential fingerprinting without raw secret exposure.
- Stream URL generation using bearer tokens and correct path scoping.
- Aurral Flow playback eligibility: prefer Navidrome station when available, use direct jobs when ready, hide actions for transient jobs.
- Up Next ordering for normal, repeat-one, repeat-all, and shuffle mode.

Integration-level smoke tests should cover:

- Settings connection test against the configured Aurral instance.
- Catalog search, artist detail, add artist, album request, and request status.
- Flow start/status, ready job listing, direct playback, and Navidrome Station fallback after refresh.
- Release APK install only, never the debug package.

### Release and Platform Policy

Android is the target platform for this work. iOS should not block Aurral, LidaClips, or Kreate-player releases. iOS can remain unsupported for Aurral direct playback unless a major release explicitly schedules it.

Every app-changing slice should be committed, pushed to `Darkaxt/Navic`, released with the normal Android APK, and installed to the phone only as the release package `darkaxt.navic` when device validation is needed.

## Implementation Slices

1. Aurral foundation: preferences, URL/auth policy, repository/client, settings screen, diagnostics, settings search entries, tests.
2. Aurral hub: Library entry, native status cards, Discover/Requests/Flows tabs, refresh states, empty/error states.
3. Acquisition: catalog search, artist detail, album/release-group detail, add/request actions, permissions-aware buttons.
4. Flow playback: Flow detail, start/status polling, ready job cards, Aurral direct-stream media items, Navidrome Station preference, queue integration.
5. Polish: offline/persistent LidaClips clip storage, shuffle/repeat-aware Up Next, README updates, Android smoke-test checklist, GitHub release.
