# Aurral First-Resolved Loading Design

Date: 2026-07-10
Branch: master

## Problem

Several Aurral surfaces still use aggregate loading calls that wait for broad, unrelated data before publishing a narrower result to the UI. This makes simple facts feel blocked by long-running catalog, discovery, image, Flow, or acquisition refreshes.

The clearest user-visible failure is artist monitoring. Whether an artist is monitored in Aurral should resolve from cached library state or the focused artist endpoint as soon as possible. It must not wait for a full artist enrichment, full discovery refresh, service status, Weekly Flow status, request queue, preview tracks, similar artists, or image hydration.

Timeouts are not the solution. The loading model should be structured so slow sections are still useful when they finish, but they cannot hold back sections that already resolved.

## Current Blocking Issues

### Native Aurral Artist Page

`composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralArtistScreen.kt`

`AurralArtistScreen` resolves local catalog rows, then calls `aurralRepository.getArtistEnrichment(artist)`. The UI only publishes Aurral release groups, request status, preview tracks, similar artists, monitoring confirmation, and recommended albums after the combined enrichment and discovery paths return.

This is the same design problem that was already fixed in the regular Navidrome artist detail path. `ArtistDetailViewModel` publishes `getArtistCoreEnrichment()` first, then refreshes album requests, preview tracks, and similar artists independently.

### Aurral Missing Album Page

`composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt`

`AurralMissingAlbumScreen` calls `getArtistEnrichment(localArtist)` before resolving the current release group, preview tracks for that release group, cover fallback, and request progress. The album page should be able to show the route-provided album identity and cached/local progress immediately, then refresh focused facts independently.

### Discovery Aggregation

`composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt`

`fetchDiscovery()` waits for `/api/discover`, `/api/library/recent`, and `/api/library/recent-releases` before returning a single `AurralDiscoverySummary`. The base discovery payload can be ready before optional recently-added or recent-release sections. Those optional sections should not block the base recommendations, global top, based-on, tags, or genre rows.

### Hub Refresh Path

`composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubViewModel.kt`

`refreshDiscovery(hydrateMissingImages = false)` is used by hub, discover list, and library surfaces to avoid blocking on image hydration. However, `refreshServiceStatus()` calls `loadServiceStatus()`, which can call `loadDiscovery()` with the default `hydrateMissingImages = true`. A refresh button can therefore reintroduce heavier discovery/image work right after the lightweight path was requested.

### Acquisition Request Refresh

`composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`

`refreshAlbumRequests()` derives request status by calling full `getServiceStatus()`. That full status path includes health, auth, Weekly Flow status, and requests. Album and collection request-status refreshes only need acquisition/request information, so they should not wait for unrelated service sections.

## Goals

- Render every available Aurral fact as soon as it resolves.
- Keep full refresh triggers available, but make them fan out into independent async refreshes.
- Preserve cached and stale-but-known data while fresh data loads.
- Make monitoring state instant when it is already known locally.
- Make monitoring state focused when it must hit the network: one artist/library lookup, not full artist enrichment.
- Keep slow or failing optional sections from blocking core page identity, ownership, monitoring, or local catalog rows.
- Avoid timeout-based cancellation. Stale results should be ignored by generation checks, not killed by arbitrary clocks.

## Non-Goals

- No visual redesign of Aurral pages.
- No change to Aurral server APIs is required for the first implementation pass.
- No new polling loop or hard cancellation timeout.
- No symlink-based workspace or cache changes.

## Model: First Resolved, First Served

Each Aurral surface should be modeled as a set of independent facets. A facet is one small piece of data with its own loading, success, stale, and error state.

Core facets:

- Artist identity: route/local artist id, name, MBID, hero image candidate.
- Artist monitoring: monitored, unmonitored, pending, failed, or unknown.
- Artist profile: Aurral image, bio, genres, external links, release groups.
- Album ownership and requests: missing/owned/requested/processing/failed state.
- Preview tracks: artist-level or release-group-level preview rows.
- Similar artists: related artist rows and local-library matching.
- Discovery base: recommendations, global top, based-on, tags, genre rows.
- Discovery secondary: recently added artists and recent releases.
- Image hydration: missing artist or release-group image lookups.
- Service status: health/auth/capabilities.
- Flow status: Flow definitions, shared playlists, and Flow stats.

The UI renders from a reducer-owned state object. Each facet can update the state independently:

1. Publish route/local/cache state immediately.
2. Start focused facet refreshes in parallel.
3. Merge each successful facet into the current state as soon as it resolves.
4. Mark only the failed facet as failed when a request fails.
5. Keep existing data visible when a refresh is in progress.
6. Ignore stale results from older generations when route/configuration changes.

Full refresh triggers are allowed. A full refresh means "start every relevant facet refresh now"; it does not mean "await every refresh before publishing anything." The refresh button can set a global activity indicator, but page content must keep updating facet by facet.

## Monitoring State Contract

Monitoring state is a focused first-class facet.

Resolution order:

1. Confirmation queue: if a monitor/unmonitor action is pending or failed, show that immediately.
2. Local repository state: use cached Aurral library artists or `libraryArtistMonitorStates` by MBID/name.
3. Route/discovery/search source: if the current `AurralDiscoverArtist` or navigation payload carries `monitored`, publish it.
4. Focused network refresh: call a narrow artist monitoring lookup such as `fetchLibraryArtistMonitoring()` through a repository method dedicated to one artist.
5. Fallback state: render `unknown` without blocking the rest of the page.

The monitoring facet must not wait for:

- `getArtistEnrichment()`
- full `getServiceStatus()`
- discovery refresh
- Weekly Flow status
- album request refresh
- preview tracks
- similar artists
- cover or artist-photo hydration

When the user changes monitoring, the UI should publish an optimistic pending state through the existing confirmation queue. The focused confirmation worker can later replace pending with confirmed or failed.

## Repository Shape

The repository should expose focused methods whose names make their scope obvious:

- `getArtistCoreEnrichment(artist)`
- `getArtistMonitoring(artist)`
- `getArtistAlbumRequests(artist)`
- `getArtistPreviewTracks(artist)`
- `getArtistSimilarArtists(artist)`
- `getDiscoveryBase()`
- `getDiscoveryRecentlyAdded()`
- `getDiscoveryRecentReleases()`
- `getAcquisitionRequests()`
- `getServiceHealthAndAuth()`
- `getFlowStatus()`

Existing aggregate methods may remain for compatibility, but UI surfaces should not call aggregate methods when a narrower method exists. Aggregate methods should be implemented as fan-out helpers over focused methods, not as a reason for UI callers to block.

## Surface-Specific Design

### Aurral Artist Screen

Initial paint:

- Build local catalog on `Dispatchers.IO`.
- Publish local artist, local albums, route image, known monitoring cache, and route-provided identity.

Parallel refreshes:

- Core enrichment for release groups/profile.
- Focused monitoring lookup.
- Album requests for ownership/status.
- Preview tracks.
- Similar artists.
- Discovery base for recommended albums and image candidates.
- Background image/cover hydration.

Core enrichment should publish release-group album rows before preview/similar/request sections finish.

### Aurral Missing Album Screen

Initial paint:

- Publish route album title, release-group id, cover URL if supplied, route request status if supplied, and local artist/more-by rows.

Parallel refreshes:

- Core artist enrichment to refine the release group.
- Focused acquisition request lookup for this album.
- Artist preview tracks filtered to this release group.
- Release-group cover lookup only if the route and core profile do not provide one.

The page should not wait for full artist similar artists or unrelated artist preview tracks before showing album identity and acquisition state.

### Aurral Hub And Discover Lists

Initial paint:

- Publish cached discovery if available.
- Refresh base discovery without image hydration.

Parallel refreshes:

- Service health/auth.
- Flow status.
- Recently added artists.
- Recent releases.
- Library artist monitoring states.
- Image hydration.

Refreshing the hub should not call a default path that silently turns image hydration back on. Heavy image hydration remains a background refresh, not a prerequisite for rendering discovery rows.

### Album And Collection Request Status

Album list and collection detail refreshes should call a focused acquisition request method. They must not use full service status just to update request dots, ownership badges, or acquisition progress.

## Error Handling

Errors are facet-local.

- A failed similar-artist refresh does not clear artist profile data.
- A failed preview-track refresh does not hide album ownership.
- A failed service-status refresh does not prevent cached discovery from rendering.
- A failed monitoring lookup renders monitoring as unknown or preserves the last known value with a warning indicator.
- Full refresh can expose an aggregate "some Aurral sections failed" indicator, but individual sections should retain their own errors.

## Cache And Freshness Rules

Cached data can be used immediately even when stale. A fresh refresh can replace it later.

Suggested state shape:

```kotlin
data class AurralFacetState<T>(
    val value: T?,
    val loading: Boolean,
    val stale: Boolean,
    val error: Throwable?,
    val generation: Long
)
```

The exact type can follow existing `UiState` patterns, but it must support retaining old values while a new refresh is loading. Route/configuration changes should increment a generation so older async results cannot overwrite newer screen state.

## Validation Requirements

Add source-level or unit guards for these contracts:

- `AurralArtistScreen` does not call `getArtistEnrichment()`.
- `AurralMissingAlbumScreen` does not call `getArtistEnrichment()`.
- Aurral artist monitoring is loaded through a focused method before preview, similar, discovery, or request refreshes can complete.
- Hub and discover list refreshes call discovery with image hydration disabled for first paint and pull refresh.
- `refreshServiceStatus()` does not force image-hydrated discovery.
- Album and collection acquisition refreshes do not call full `getServiceStatus()`.
- Full refresh launches independent facet refreshes and does not await all facets before publishing the first successful facet.

Manual verification should include a slow Aurral deployment or instrumented fake repository where preview/similar/discovery/request paths are delayed while monitoring returns quickly. The artist page should show monitoring state and core album rows immediately while slow sections continue loading.

## Success Criteria

- Artist monitoring state appears from cache or focused lookup without waiting on full enrichment.
- Aurral artist pages show route/local/core content while preview, similar, request, and discovery sections continue to refresh.
- Missing-album pages show album identity and acquisition state while preview and cover fallback refresh independently.
- Hub/discover rows render from base discovery before optional recent sections or image hydration finish.
- Full refresh remains available and useful, but it behaves as async fan-out with first-resolved publication.
- No new cancellation timeouts are introduced.

