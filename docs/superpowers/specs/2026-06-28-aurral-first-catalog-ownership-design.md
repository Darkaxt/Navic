# Aurral-First Catalog Ownership Design

## Problem

Navic still has several mixed catalog paths where Navidrome owns the artist or album page and Aurral is treated as an enrichment layer. That is the wrong model for the current fork.

The product rule is:

> Aurral owns catalog metadata. Navidrome only provides local ownership, playback, download, and history evidence.

The broken John Powell example shows the failure clearly. Aurral knows the canonical artist and album/release-group shape, while Navidrome only has local playable files. The current album page starts from the Navidrome album, recovers an Aurral match, then appends local songs that were not matched. That can duplicate `Test Drive` and still leave the page looking like a Navidrome album page instead of an Aurral-owned page with local ownership badges.

Previous fixes patched overlay behavior, request status, and artwork fallbacks, but they did not remove the core architectural mismatch.

## Goals

- Make Aurral the source of truth for artist and album catalog metadata whenever Aurral is enabled and an Aurral identity is known.
- Use Navidrome only as evidence for what is owned, playable, downloaded, favorited, rated, recently played, or in progress.
- Keep a complete Navidrome fallback when Aurral is disabled, unconfigured, unauthorized, or unavailable.
- Fix album detail pages explicitly, including the duplicate-track failure on `How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]`.
- Keep all Aurral work asynchronous and cache-backed so navigation, scrolling, playback, and page opening do not block on network resolution.
- Avoid duplicating the same catalog decision across view models, UI policies, and render components.

## Non Goals

- Do not rewrite Navidrome metadata or require a Navidrome fork.
- Do not make playback depend on Aurral. Playback still uses local Navidrome songs when ownership evidence exists.
- Do not block page rendering while Aurral links, tags, images, monitor status, or request state resolve.
- Do not touch the ebook reader or Bindery flows as part of this design.

## Recent Bug Classes To Prevent

The latest fork commits and source guards show that this design must prevent more than the visible duplicate-album-row bug. The implementation must preserve the performance and stability lessons from the recent refactors.

| Bug Class | Recent Evidence | Required Design Rule |
| --- | --- | --- |
| UI-thread DB, file, or network work | `27e6ae94`, `668c1e84`, `9561c31f`, `LibraryStartupAsyncSourceTest` | Repository, DAO, filesystem, image-cache, and Aurral work must cross an IO or Default dispatcher boundary before doing real work. |
| Cache reload on tab return | `2e60b39c`, `493b2e5d`, `ffe25257`, `5a548f1f`, `57a8314a`, `0690eabd`, `343d37a2` | A rendered page or tab must seed from retained ViewModel/cache state and refresh in the background without flashing empty/loading rows. |
| Recomposition churn | `e0d4c85e`, `bd6de318`, `b34a5d88`, `7758396c`, `f043229b`, `4ea659c0`, `PerformanceAntiRegressionGuardTest` | Use stable keys, stable row models, narrow state collection, and remembered derived strings. Do not rebuild broad lists or maps on every recomposition. |
| Per-row Flow collectors | `d55002d1`, `2026-06-27-performance-opportunity-audit.md`, `LibraryStartupAsyncSourceTest` | Do not create one download/playback/artwork Flow collector per rendered row. ViewModels should expose snapshots; rows derive display state from those snapshots. |
| Image memory and logging pressure | `b5a5a593`, `25bd53dc`, `c6eb56df`, `bd0ff18b` | Use the shared bounded image pipeline, avoid original-size decodes in grid/cache paths, and never log full throwable stacks during normal scroll-time image failures. |
| Direct projection during composition | `65d53a28`, `LibraryStartupAsyncSourceTest` | Expensive Aurral discovery, artist-photo-cache, tag, and album-row projections must run in the ViewModel or `produceState` on Default, not inline in composables. |
| Timeout crutches | `c7361039` | Do not add cancellation timeouts to make slow Aurral work "safe". Use reactive state, progress, optimistic UI, retries where appropriate, and visible failure states. |
| Silent errors | `f5c36741`, `CollectionAurralRequestFailureSourceTest`, `AurralFirstArtistPageSourceTest` | Failed Aurral requests and non-success API responses must surface as section/action errors, not empty sections or silent no-ops. |
| Local/Aurral ownership inversion | `d311ae4b`, `f54d6222`, `af5bc018`, existing `AurralAlbumRecoveryPolicyTest` local-only row behavior | Aurral catalog rows must be primary. Local-only rows are evidence or diagnostics, not normal catalog rows once an Aurral album match exists. |

## Performance Contract

Every implementation phase must satisfy this contract.

### Async Boundaries

- No composable should directly perform DAO reads, filesystem scans, network calls, image-cache projections, broad `groupBy` work, or large list joins.
- ViewModels may publish cached state immediately, then refresh from repositories on IO or Default depending on whether the work is I/O-bound or CPU-bound.
- CPU-heavy joins, such as matching Aurral release groups to local albums or mapping artist-photo caches, must run off the UI dispatcher.
- A section failure must not block unrelated sections. Artist profile, owned albums, missing albums, preview tracks, similar artists, links, tags, and request status should be independently refreshable.
- A slow section should render as loading while other cached or resolved sections remain visible.

### Object And Render Stability

- Row models should be produced once per source-state change in the ViewModel or policy layer, not rebuilt inside item composables.
- Use stable canonical keys:
  - Artist rows: Aurral artist id or MusicBrainz artist id, then normalized fallback.
  - Album rows: Aurral release group id or MusicBrainz release group id, then normalized fallback.
  - Track rows: Aurral track id or recording id, then disc/track/title fallback.
- Do not key rows by list index when a stable catalog or local id exists.
- Do not subscribe every row to broad player, download, image-cache, or request-status flows. Screens should collect narrow snapshots and pass primitive row state down.
- Derived display strings, subtitle text, ownership labels, and split artist-credit labels must be remembered or precomputed when their inputs change.
- Progress ticks and current playback changes must not recreate album, artist, discovery, or track row objects.

### Cache Reuse

- Opening a page for a second time should reuse cached Aurral metadata and local ownership evidence.
- Returning to a tab should not restart every Aurral resolution pipeline or clear resolved rows.
- Cache invalidation should be targeted:
  - Aurral metadata invalidation clears Aurral catalog data and translation mappings.
  - Navidrome refresh updates local ownership/playback evidence.
  - Request/monitor actions update only acquisition or monitor state.
- Unknown future row ids or sections must remain visible by default and append at the end of configured row ordering.

### Image Pipeline

- Use the shared image loader and shared artwork color cache.
- Do not add second network palette loaders or uncached artwork fetchers.
- Do not request original-size images for grid, row, or offline cache warming paths.
- Full-screen artwork may use higher quality, but list and carousel contexts need bounded request sizes.
- Normal image failures during scrolling should update callbacks/state without logging full throwable stacks unless diagnostics are explicitly enabled.

### No Timeout-Based Control Flow

- Do not use timeouts to cancel Aurral requests, cache loads, artwork resolution, or page hydration.
- Use idempotent refresh keys, in-flight request dedupe, condition-based state transitions, and visible progress/failure states instead.
- Monitoring heartbeats may use time-based checks, but user-facing work should not hard-crash or cancel because an arbitrary duration elapsed.

## Current Violating Paths

These paths still say "Navidrome owns the page; Aurral may enrich it" instead of "Aurral owns the page; Navidrome supplies ownership evidence".

| Area | File | Current Problem |
| --- | --- | --- |
| Album detail source | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt` | Starts with `repository.getCollectionFlow(...)` and builds the page from a local `DomainAlbum`. |
| Album track rows | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicy.kt` | Maps recovered Aurral rows, then appends unmatched local songs, which can duplicate visible tracks. |
| More by artist | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt` | Uses Navidrome `repository.getOtherAlbums(...)` as the album source. |
| Normal artist detail | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt` | Starts from local artist, local albums, and local songs, then enriches with Aurral. |
| Artist local catalog policy | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailLayoutPolicy.kt` | Builds catalog sections from Navidrome evidence first. |
| Aurral artist route | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralArtistScreen.kt` | Loads local albums and merges them into the Aurral page instead of projecting local evidence onto Aurral release groups. |
| Aurral ownership rows | `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralArtistEnrichmentPolicy.kt` | Iterates local albums first, then attaches Aurral state, which reverses the intended ownership direction. |
| Album cards | Library, album list, and search surfaces | Render `DomainAlbum` rows as primary and decorate them with Aurral status or artwork, rather than rendering Aurral albums with local ownership evidence. |

## Target Model

Introduce a single Aurral-first catalog model used by artist pages, album pages, album rows, and search/library projections.

### Catalog Entities

`AurralCatalogArtist`

- Aurral artist id and MusicBrainz artist id when available.
- Canonical name, sort name, image, biography, genres/tags, external links.
- Monitor status and request/action status.
- Release groups, previews, similar artists, popular tracks.

`AurralCatalogAlbum`

- Aurral release group id and MusicBrainz release group id when available.
- Canonical title, artist identity, release year/date, primary type, secondary types.
- Aurral cover URL and request/download/acquisition status.
- Canonical track list when Aurral can provide it.

`AurralCatalogTrack`

- Recording id when available.
- Disc number, track number, title, duration, preview URL.
- Any Aurral-side popularity or preview state.

`LocalOwnershipEvidence`

- Matched Navidrome album id, song id, cover art id, and stream id.
- Download status, local availability, favorite/rating/play count, last played, and progress.
- Match confidence and diagnostic fields for ambiguous matches.

`CatalogAlbumRow` and `CatalogTrackRow`

- Always start with the Aurral catalog object.
- Attach zero or one primary local ownership match.
- Carry extra local conflicts only for diagnostics, not as duplicate normal rows.

## Album Detail Page

The album page must be fixed first because it is the clearest user-visible break.

### Page Source

When opening an album from a local Navidrome album id:

1. Render a local fallback shell immediately from the existing `DomainAlbum`.
2. Resolve the best Aurral release group asynchronously.
3. Once the Aurral release group is available, switch the page model to the Aurral-owned album model.
4. Keep Navidrome data only as local ownership/playback evidence.

When opening an album from an Aurral release group id:

1. Render from cached Aurral release-group data immediately when available.
2. Resolve local ownership evidence asynchronously.
3. Never require local ownership before showing the Aurral album page.

When Aurral is disabled or unavailable:

1. Keep the existing Navidrome album page behavior.
2. Show a small integration error/status indicator only when Aurral is expected to be active.

The local fallback shell must not be treated as the final page if an Aurral match later resolves. The page model should have an explicit source state such as `LocalFallback`, `AurralResolved`, `AurralAmbiguous`, or `AurralUnavailable` so UI decisions do not silently continue using local-first assumptions.

### Header

The album header should use:

- Aurral title.
- Aurral artist identity.
- Aurral cover art.
- Aurral year/date and type.

Navidrome title, artist, year, and cover are fallback-only fields.

### Tracks

Track rows must be generated from the Aurral canonical track list when available.

For each Aurral track:

- If a local Navidrome song matches, show the normal play/download/favorite state using that local song.
- If no local song matches but Aurral has a preview, show the preview action.
- If no local song and no preview exist, show a missing/request state.

Do not append unmatched local songs to the canonical track list. If unmatched local files are useful for debugging, show them only in a collapsed diagnostic section such as `Local unmatched files`, not in the normal track list.

This replaces the existing local-only insertion behavior covered by current `AurralAlbumRecoveryPolicyTest` cases such as `displayRowsKeepLocalOnlySongsWhenAurralHasPartialAlbumData`. That older behavior should remain only for the Navidrome fallback path or an explicit diagnostics section. It must not run once the album page has an Aurral release-group identity.

### Duplicate Prevention

The `Test Drive` case should produce one visible row per canonical Aurral track.

If two local Navidrome songs match the same Aurral track:

- Choose one primary playback candidate deterministically.
- Prefer playable, downloaded, higher-quality, then stable id ordering.
- Keep the other candidate as diagnostic conflict evidence.
- Do not create a second visible `Test Drive` row unless Aurral itself has two distinct canonical tracks with different disc/track/recording identity.

### Album Page Performance Contract

- Aurral release-group lookup, track-list loading, local ownership matching, request-status lookup, and cover hydration must be asynchronous.
- The first cached/local shell render should not wait for Aurral network calls.
- The Aurral-resolved page should be cached and reused on return.
- Matching must pre-index local songs by useful keys instead of repeatedly scanning the full song list for every Aurral track.
- Track rows must be stable and keyed by canonical Aurral track identity.
- Playback state should be passed into rows as narrow primitives such as `isCurrentTrack` and `isPlaying`; rows must not subscribe to full player state.
- Download/request state should be supplied as a screen-level snapshot; rows must not each start their own status Flow.
- Dynamic color extraction should use the resolved artwork color cache, not a second image/palette pipeline.

### More By Artist

`More by John Powell` and similar album sections must come from Aurral release groups.

Rows should be split as:

- `Owned albums`: Aurral albums with green or yellow local evidence.
- `Missing albums`: Aurral albums with no local evidence.

Use ownership dots:

- Green: owned and locally playable enough to represent the album.
- Yellow/orange: partially owned or mixed ownership.
- Red: missing from the local library.

The red dot must be used for missing albums. A yellow dot means some local ownership evidence exists.

## Artist Pages

The normal artist route and the Aurral artist route should converge on the same Aurral-first page model.

### Header

The artist header should use:

- Aurral artist name.
- Aurral image.
- Aurral biography.
- Aurral links.
- Aurral monitor status.

The Aurral monitor/unmonitor button must appear as soon as Aurral identity is known. It should trigger the monitor/unmonitor action directly and update the local cache optimistically while the network operation completes in the background.

### Tags And Links

Artist tags should behave like the bottom labels in the library:

- Resolve asynchronously.
- Render cached values immediately.
- Show loading placeholders if needed.
- Never block the artist page while tags resolve.

External links should appear once, above the play button, as a horizontal row:

- Show the most useful few links first.
- Include `More` to expand hidden links.
- Include `Less` to collapse them again.
- Do not duplicate the links under the play button.

### Sections

Recommended order:

1. `Frequently played`
2. `Most popular tracks`
3. `Owned albums`
4. `Missing albums`
5. `Similar artists`
6. Optional previews or discovery rows when available

`Frequently played` and `Most popular tracks` are allowed to use Navidrome play history and local playback evidence, but artist/album metadata should still be projected through the Aurral catalog model when possible.

### Synthetic Artists From Split Credits

When Navic splits combined artist credits into synthetic artist entries, the synthetic entry must preserve the resolved Aurral identity.

That identity is required for:

- Artist image.
- Artist profile route.
- Monitor/unmonitor action.
- Aurral release groups.
- Similar artists and links.

A synthetic `0 albums` artist is still a valid Aurral artist if it was resolved from Aurral. It should not show an empty local-only page.

## Album, Library, And Search Surfaces

Album cards and rows should use the same Aurral-first projection.

### Album Cards

When an Aurral match exists:

- Show Aurral title, cover, year, and artist.
- Show Navidrome ownership/download/playback state as badges or dots.
- Navigate to the Aurral-backed album detail route.

When no Aurral match exists:

- Show the Navidrome album as fallback.
- Continue resolving Aurral identity asynchronously.
- Replace with Aurral metadata once a confident match is available.

### Library Rows

Rows like recently played, most played, quick picks, and local album grids can still be driven by local playback history or local library membership. Their displayed metadata and artwork should be projected through the Aurral catalog cache when a match is available.

Library row projection must preserve the recent cache/re-entry fixes:

- Keep previously resolved Aurral rows visible while refreshing.
- Do not re-run full discovery or image hydration on every tab entry when the loaded configuration already matches.
- Do not map the full artist-photo cache directly in composition.
- Show row titles and cached metadata before slower image/link/tag hydration completes.

### Search

Search should distinguish:

- Local results from Navidrome.
- Catalog results from Aurral.

If a local album result has an Aurral match, opening it should use the Aurral-backed album route. If there are multiple plausible Aurral matches, show a candidate selector instead of silently picking a low-confidence match.

## Matching Rules

### Artist Matching

Preferred order:

1. MusicBrainz artist id.
2. Aurral artist id already stored in the local translation cache.
3. Exact normalized name.
4. Split-credit candidate name plus album/track evidence.
5. Aurral search result with confidence threshold.

### Album Matching

Preferred order:

1. MusicBrainz release group id.
2. Aurral/Lidarr foreign album id.
3. Stored local translation cache entry.
4. Track evidence: recording ids, title set, disc/track count, durations.
5. Normalized album title plus artist identity plus year/type.
6. Manual candidate selection.

### Track Matching

Preferred order:

1. MusicBrainz recording id.
2. ISRC when available.
3. Disc number, track number, title, and duration.
4. Title and duration.
5. Title-only fallback with low confidence.

One canonical Aurral track can have many local candidates internally, but only one primary local song may be rendered in the normal UI.

## Cache And Async Requirements

All Aurral-first catalog data must be cache-backed.

### Cache Keys

Use stable keys that include the Aurral base URL:

- Artist details by Aurral artist id and MusicBrainz artist id.
- Release groups by Aurral release group id and MusicBrainz release group id.
- Track lists by release group id.
- Search matches by normalized query plus context.
- Local translation entries by Navidrome artist id, album id, song id, and file path where useful.

### Render Rules

- Render cached Aurral data immediately when present.
- Render local fallback immediately only while Aurral data is unavailable.
- Refresh Aurral data asynchronously.
- Update only the affected fields when refresh results arrive.
- Do not drop already-rendered rows to an empty/loading state on tab return.

### Invalidation

- Aurral cache invalidation from settings clears Aurral metadata and match caches.
- Navidrome rescans should update local ownership evidence without invalidating Aurral metadata.
- Monitor/unmonitor and request-album actions should update cached status optimistically and reconcile with the server response later.

## Failure Handling

If Aurral is enabled but an endpoint fails:

- Keep cached Aurral data if available.
- Show the failed section as an error, not as empty.
- Do not collapse the whole page.
- Do not fall back to duplicate local rows.

If Aurral returns multiple plausible matches:

- Show a candidate picker.
- Store the selected match in the translation cache.
- Use that mapping for future page loads.

If album request or monitor actions fail:

- Show visible feedback.
- Revert optimistic status only if the server confirms failure.
- Leave the button tappable for retry.

## Test Requirements

Add tests before the implementation changes.

### Album Detail Tests

- Given an Aurral album with one canonical `Test Drive` track and two matching local Navidrome songs, the album page renders one visible `Test Drive` row.
- Given an Aurral album match, the album header uses Aurral title, artist, cover, and year.
- Given unmatched local songs, they are not appended to the normal canonical track list.
- Given current local-only insertion behavior, it is limited to Navidrome fallback or diagnostics and cannot run for an Aurral-resolved album page.
- Given no Aurral match and Aurral disabled, the existing Navidrome album page still renders.
- Given a track row list refresh, stable canonical keys prevent duplicate rows and preserve scroll identity.

### Artist Page Tests

- Given an Aurral artist identity, the monitor button is visible and calls the monitor action.
- Given Aurral release groups and local ownership evidence, `Owned albums` and `Missing albums` are derived by iterating Aurral release groups first.
- Given a missing album with no local songs, the status dot is red.
- Given a split-credit synthetic artist with an Aurral identity, opening the artist page renders Aurral profile data instead of an empty local page.

### Cache And Async Tests

- Cached Aurral data renders before refresh completes.
- Tag/link resolution does not block initial artist page state.
- Returning to a rendered library tab does not reset Aurral rows to empty/loading.
- Returning to an album detail page reuses the cached Aurral release group and local ownership evidence.
- Aurral album/artist sections refresh independently and do not collapse unrelated sections on failure.
- No Aurral implementation path uses timeout-based cancellation.

### Source Guard Tests

- Album detail page policy must not append unmatched local songs to canonical Aurral rows.
- Artist ownership rows must iterate Aurral albums first, not local albums first.
- Album cards must route to Aurral-backed detail when an Aurral match exists.
- Album and artist detail must not call repository/cache work from composables or constructors.
- Album and artist detail rows must not collect per-row player, download, or request-status flows.
- Aurral catalog projections must run off the UI dispatcher.
- Normal artwork failures must not log throwable stacks during scrolling.

## Migration Plan

### Phase 1: Shared Catalog Model

Create the Aurral-first catalog row models and matching policies:

- `AurralCatalogArtist`
- `AurralCatalogAlbum`
- `AurralCatalogTrack`
- `LocalOwnershipEvidence`
- `CatalogAlbumRow`
- `CatalogTrackRow`

Add unit tests for matching, dedupe, ownership status, and fallback behavior.

### Phase 2: Fix Album Detail

Rework album detail to build from the Aurral release group when available.

Required changes:

- Resolve Aurral release group for a local album id.
- Build header from Aurral.
- Build canonical track rows from Aurral.
- Attach local playback evidence per track.
- Remove normal UI appending of unmatched local songs.
- Move unmatched local files to diagnostics only.

This phase is the release gate for the John Powell duplicate-row bug.

### Phase 3: Unify Artist Pages

Route normal artist detail and Aurral artist detail through the same Aurral-first model.

Required changes:

- Header from Aurral.
- Monitor button from Aurral identity.
- Async tags and links.
- Horizontal links with `More` and `Less`.
- Aurral release groups split into `Owned albums` and `Missing albums`.

### Phase 4: Rewire Album Cards And Search

Update library, album list, and search cards to display Aurral metadata when matched while keeping local ownership evidence.

Required changes:

- Use Aurral title/art/year/artist for matched album cards.
- Use local evidence only for ownership and playback badges.
- Navigate to the Aurral-backed album detail route for matched rows.
- Keep Navidrome fallback for unmatched or Aurral-disabled rows.

### Phase 5: Diagnostics And Device Validation

Add diagnostics for:

- Aurral match id.
- Local ownership match id.
- Track match confidence.
- Local conflicts hidden from normal UI.
- Section refresh/error state.

Validate on device with:

- John Powell artist page.
- `How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]`.
- The duplicated `Test Drive` row case.
- A split-credit synthetic artist with zero Navidrome albums but resolved Aurral identity.

## Acceptance Criteria

- The John Powell album page is Aurral-owned when Aurral is enabled.
- `Test Drive` does not appear twice unless Aurral has two distinct canonical tracks.
- Aurral release groups drive `Owned albums` and `Missing albums`.
- Missing albums use red dots.
- Owned or partially owned albums use green/yellow dots based on local evidence.
- The artist monitor/unmonitor button appears once Aurral identity is resolved.
- External links appear once, horizontally, with `More` and `Less`.
- Tags resolve asynchronously and do not block the page.
- Album cards use Aurral metadata when matched and Navidrome only as fallback.
- With Aurral disabled, the current Navidrome-only behavior still works.

## Open Questions

- Should unmatched local files be completely hidden in production, or exposed behind a diagnostics toggle?
- Should low-confidence album matches open a required candidate selector, or can they render local fallback until the user manually resolves the mapping?
- Should `Most popular tracks` use Aurral popularity only, or merge Aurral popularity with Navidrome play counts?
