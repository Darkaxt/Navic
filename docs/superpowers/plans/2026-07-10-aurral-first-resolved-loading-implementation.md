# Aurral First-Resolved Loading Implementation Plan

Spec: `docs/superpowers/specs/2026-07-10-aurral-first-resolved-loading-design.md`

## Scope

- Convert Aurral artist and missing-album detail pages from full-enrichment gating to focused, independently published refreshes.
- Add focused repository/API entry points for artist monitoring, acquisition requests, and discovery subsections.
- Keep full refresh triggers available, but make them publish the first resolved facet instead of waiting for every optional section.
- Do not touch reader/ebook surfaces.

## Test-First Changes

1. Add source-contract tests that fail while `AurralArtistScreen` and `AurralMissingAlbumScreen` call `getArtistEnrichment()`.
2. Add source-contract tests for instant/focused monitoring, focused request refresh, base-first discovery, and service-status refresh not escalating to hydrated discovery.
3. Add repository tests proving global acquisition refresh uses `fetchAlbumRequests()` without `fetchServiceStatus()`, and focused artist monitoring uses cached/library monitoring without full enrichment.

## Implementation Steps

1. Split Aurral repository requests:
   - `getArtistMonitoring(artist)`
   - `getAcquisitionRequests()`
   - `getDiscoveryBase()`
   - `getDiscoveryRecentlyAdded()`
   - `getDiscoveryRecentReleases()`
2. Split Aurral API client discovery:
   - `fetchDiscoveryBase()`
   - `fetchRecentlyAddedArtists()`
   - `fetchRecentReleases()`
   - keep `fetchDiscovery()` as a compatibility aggregate.
3. Update `AurralArtistScreen`:
   - local catalog renders first.
   - monitoring starts independently and updates `monitorConfirmed`.
   - core enrichment publishes profile/release rows first.
   - requests, preview tracks, similar artists, and recommended albums refresh as independent facets.
4. Update `AurralMissingAlbumScreen`:
   - route/local album identity renders first.
   - core enrichment resolves the release group and cover seed.
   - preview tracks, request progress, and cover fallback refresh independently.
5. Update `AurralHubViewModel`:
   - lightweight discovery publishes base summary first.
   - recently-added and recent-release sections merge in as they resolve.
   - service-status refresh does not trigger hydrated discovery by default.
6. Run focused common tests, Android host tests, source whitespace checks, then version/release verification.

## Verification

- `./gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.library.LibraryStartupAsyncSourceTest --tests paige.navic.domain.repositories.AurralRepositoryTest`
- `./gradlew.bat --no-daemon :composeApp:testAndroidHostTest` scoped to explicit Aurral test classes.
- `git diff --check`
- `powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta84`
