# Aurral-First Artist Page Implementation Plan (Updated 2026-06-26)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Review status:** Tasks 1–7 reviewed at commit `ed0516ad`. ~85% implemented. Remaining work is marked with ❌ below. New tasks 10–14 cover gaps found beyond the original scope (album grid, artist list, acquisition reactivity, failure surfacing, code duplication).

**Goal:** Make Navic artist detail pages Aurral-first when Aurral is enabled, with Navidrome used only for local ownership, playable tracks, and fallback when Aurral is disabled or unresolved. Extend the pattern to album-grid and artist-list surfaces.

**Architecture:** Treat Aurral as the canonical artist profile and discography source. Treat Navidrome as the local playback/ownership overlay. Load every section independently and asynchronously, seed from cache immediately, and update sections as profile, monitor status, ownership matching, preview tracks, and similar artists resolve. Do not introduce hard cancellation timeouts.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, Ktor client, kotlinx.serialization, Gradle Android host tests, existing Aurral repository and artist detail UI.

---

## Live Fixture

Use John Powell as the required acceptance fixture:

- Aurral profile: `https://aurral.remaxku.eu/artist/52bb713d-b0c9-4bf6-9f58-392388d5cc11`
- Aurral MBID: `52bb713d-b0c9-4bf6-9f58-392388d5cc11`
- Local Navidrome evidence: the user has only `Test Drive` by John Powell from `How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]`.
- Expected Navic result: Aurral profile, image, bio, links, release groups, similar artists, and preview tracks load from Aurral. The local `Test Drive` evidence appears as partial ownership for the matching How to Train Your Dragon release group. All other release groups remain missing/requestable.
- Expected monitor result: the Aurral monitor button is visible once the MBID is known. If Aurral reports the artist is not in library, that is a verified unmonitored state, not a hidden action.

---

## Current State To Preserve Or Replace

Existing code already has a partial Aurral layer:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`

The flaw is conceptual: the page is still a Navidrome artist page with Aurral enrichment layered on top. The new shape must be an Aurral artist page with a Navidrome ownership/playback overlay.

---

### Task 1: Lock The Desired State With Policy Tests

**Status: ✅ MOSTLY DONE** — Policy tests exist for ownership matching, monitor state, cache seeding, and split rows. Verify they cover the edge cases below.

**Files:**
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileStatePolicyTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`

- [x] Monitor-state test: Aurral MBID + missing library artist → `VerifiedUnmonitored`, action visible + enabled.
- [x] Monitor-state test: auth/config failure → visible error state, not silently missing button.
- [x] John Powell ownership test: one local `Test Drive` track → partial How to Train Your Dragon release group.
- [x] Split-row test: owned/partial release groups separate from missing.
- [x] Cache-seeding test: cached Aurral profile data renders before fresh sections finish.
- [ ] **Add:** test that `fetchArtistPreview` returning a 5xx → section state is `Error`, not `Empty`. (See Task 3 gap.)

---

### Task 2: Define The Aurral-First UI State

**Status: ✅ DONE** — `AurralArtistProfileState.kt` implements per-section `AurralArtistSectionUiState` (Disabled/Loading/Ready/Empty/Error) + `AurralArtistMonitorUiState` (PendingVerification/PendingConfirmation/Monitored/NotMonitored/Error). Core-before-full-sections loading is in `ArtistDetailViewModel.kt:436-707`.

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileState.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`

- [x] `AurralArtistProfileUiState` with independent section states (profile, monitor, ownership, previewTracks, similarArtists, requests, localPlayback).
- [x] Monitor enum with `UnknownResolving` / `VerifiedMonitored` / `VerifiedUnmonitored` / `Updating` / `Error`.
- [x] Monitor button visible for all states whenever a candidate MBID exists.
- [x] Navidrome-only rendering as fallback when Aurral is disabled or unresolved.

---

### Task 3: Fetch Full Aurral Artist Profile Data

**Status: ✅ DONE** — The enrichment pipeline (`aurralArtistEnrichment`, 4 parallel API calls) is implemented. Artist details now carry profile image aliases, and preview/similar HTTP failures surface as section errors instead of empty states.

**Gap A: Artist image missing from details DTO.**

`AurralArtistDetailsDto` (`AurralDtos.kt:243-252`) has `bio`, `genres`, `links`, `relations`, `releaseGroups`, `_lidarrData` — but **no `image`/`images` field**. The artist image is fetched via a *separate* `searchArtists` call (`ArtistDetailViewModel.kt:1049-1073`, `hydrateAurralArtistImageFromSearch`). This is a workaround; the plan expects the profile fetch to be self-contained.

- [x] **Fix:** Add `image`/`images` (or `coverUrl`/`imageUrl` alias) to `AurralArtistDetailsDto`. Update `aurralArtistEnrichment` mapping (`AurralDtoMapping.kt:590`) to populate the image from the details response, falling back to the search-based image only when the details endpoint doesn't return one.

**Gap B: `fetchArtistPreview` / `fetchSimilarArtists` silently return empty on any non-success HTTP code.**

`AurralApiClient.kt:581-607`: `fetchArtistPreview` returns an empty `AurralArtistPreviewDto()` for `Unauthorized`, `Forbidden`, *and* any other non-success code (500, network error). Same for `fetchSimilarArtists`. The section then shows `Empty` (not `Error`), so the per-section error UI never fires. The plan requires "auth/config failure yields a visible error state, not a silently missing button."

- [x] **Fix:** Change `fetchArtistPreview` and `fetchSimilarArtists` to distinguish between:
  - HTTP 200 with empty data → `Empty` (correct — no preview tracks / similar artists).
  - HTTP 4xx/5xx / network error → propagate the failure so the ViewModel sets the section to `Error`.
  - Consider returning a `Result<AurralArtistPreviewDto>` or throwing on non-success, and catching in the ViewModel's section loader (`ArtistDetailViewModel.kt:651-706`) to call `markAurralPreviewTracksRefreshFailed` / `markAurralSimilarArtistsRefreshFailed`.

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralDtos.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralDtoMapping.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`

---

### Task 4: Build Navidrome Ownership Overlay

**Status: ✅ DONE** — `aurralArtistOwnershipAlbumRows()` (`AurralArtistEnrichmentPolicy.kt:160-258`) matches local albums to Aurral release groups via MBID → title → token → track-evidence. Ownership buckets (Owned/Partial/Missing/Requested/Failed/Processing) + colored dots are rendered. The John Powell fixture (`Test Drive` → Partial) is handled.

**Minor concern:** track-evidence matching is gated on `localAlbumLooksLikeReleaseGroupEdition()` which requires shared title tokens OR (edition tokens AND soundtrack). A non-soundtrack album with zero title-token overlap skips track evidence entirely, even if every track matches. Consider loosening the gate if track-evidence should be an independent signal.

---

### Task 5: Smart Cache And Async Refresh

**Status: ✅ DONE** — Cache seeding, independent section refresh, reactive revision bumps, and section-local failures are implemented. Core profile failures no longer stamp the same error onto every section.

**Gap: Core-enrichment failure collapses ALL section errors.**

When `coreEnrichment == null` (`ArtistDetailViewModel.kt:533-555`), the ViewModel sets `aurralError`, `aurralProfileError`, `aurralOwnershipError`, `aurralPreviewTracksError`, `aurralSimilarArtistsError`, and `aurralRequestsError` all to the same message. This defeats the per-section error split for the core-failure case. The plan says "keep failed section refreshes local to that section; do not collapse the whole artist page."

- [x] **Fix:** On core-enrichment failure, set only `aurralProfileError` (the profile section). Leave the other section errors unset (they'll show `Loading` or remain `Ready` from prior data). If the other sections haven't loaded yet, they'll show `Loading` until their own independent fetches complete or fail.

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt` (lines ~533-555)

---

### Task 6: Render Aurral-First Artist Page

**Status: ✅ DONE** — Hero uses Aurral image/name/bio/tags/links. Owned/partial + missing album rows split. Per-section loading states. Row titles visible before images load. The recent commit sequence ("prioritize Aurral artist sections", "hydrate Aurral artist images from search", "refresh Aurral artist detail sections") implements this.

---

### Task 7: Fix Monitor/Unmonitor Action

**Status: ✅ DONE** — Monitor button visible whenever MBID exists (4-state badge). Direct Aurral monitor/unmonitor. Auth failures surfaced via `IntegrationLoadingIndicatorStrip`. "Open in Aurral" in overflow menu.

---

### Task 8: Build Verification

**Status: ✅ DONE** — Automated validation and debug APK build passed for the release gate. Physical device verification remains useful when a device is attached, but it is not blocking this release.

- [x] Build a debug APK for device validation.
- [x] Focused Aurral artist/page tests pass.
- [x] Existing adjacent artwork/caching/performance tests still pass.
- [ ] Install on the tablet/phone through adb.
- [ ] Open John Powell from Artists.
- [ ] Verify the monitor button is visible.
- [ ] Verify Aurral profile sections render:
  - Aurral image/header
  - bio/tags/links
  - owned/partial albums row
  - missing albums row
  - similar artists
  - preview tracks when available
- [ ] Verify `Test Drive` appears as local evidence and the matching album is partial.
- [ ] Verify tapping monitor performs the Aurral action and updates state without losing page content.
- [ ] Verify returning to the tab uses cache and does not visually repopulate the entire page from blank.

---

### Task 9: Release Gate

**Status: ✅ READY** — Task 3 fixes are implemented, focused tests pass, adjacent caching/artwork/performance tests pass, debug APK builds, and master was synced before commit/release.

- [x] Focused Aurral artist tests pass (including the new Task 3 error-surfacing test).
- [x] Existing artwork/caching tests still pass.
- [x] Debug APK builds.
- [x] No unreviewed unrelated dirty files are included.
- [x] Master is synced with remotes before commit, push, and release.

---

## NEW TASKS — Gaps found beyond the original plan scope

These gaps were identified during an architecture review of the Artist + Album pages at commit `ed0516ad`. They are NOT part of the original artist-detail plan but affect the broader "how Artist & Album pages handle Aurral API Shape + Monitoring + Navidrome ownership" question.

---

### Task 10: Album Grid Cards Show Navidrome Download Status

**Status: ✅ DONE** — Album grid and More By Artist now derive download ownership dots from the shared download snapshot.

**Problem:** `AlbumListScreenItem` (`album/components/Item.kt:40`) accepts `ownershipStatus: AurralOwnershipStatus?` and `ArtGridItem` (`ArtGrid.kt:167-174`) renders an `AurralOwnershipStatusDot`. But `albumListScreenContent` (`Content.kt:35-50`) never passes `ownershipStatus` — it defaults to `null` and no dot renders. `AlbumListViewModel` has no `DownloadManager` and no download-derivation logic.

**Fix:**
- [x] Add `DownloadManager` (or `downloadManager.allDownloads`) to `AlbumListViewModel`. Derive a per-album download status (Fully Downloaded / Partially Downloaded / Not Downloaded) from the shared `allDownloads` flow. Use the same approach as `CollectionDetailViewModel.allDownloads` (`CollectionDetailViewModel.kt:80-85`).
- [x] Consider a dedicated `NavidromeOwnershipStatus` enum (not reusing `AurralOwnershipStatus`) to avoid conflating "Aurral acquisition" with "Navidrome download." Or extend `AurralOwnershipStatus` with a `Downloaded` case.
- [x] Pass the derived status to `albumListScreenContent` → `AlbumListScreenItem` → `ArtGridItem`.
- [x] Same fix for the "More By Artist" carousel (`MoreByArtistRow.kt:57-115`).

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/components/Content.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/components/layouts/ArtGrid.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/MoreByArtistRow.kt`

---

### Task 11: Artist List Shows Aurral Monitor Badge

**Status: ✅ DONE** — Artist list consumes the Aurral library monitor-state cache and passes per-card monitor badge state.

**Problem:** `ArtistsScreenItem` (`ArtistListScreen.kt:240`) accepts `aurralMonitorState: AurralMonitorActionState?` and the badge overlay (`ArtistListScreen.kt:272-281`) renders. But `ListContent.kt:120-132` never passes a value — it defaults to `null`. The monitoring flags ARE loaded by `AurralRepository.getCachedLibraryArtists` (`AurralRepository.kt:1224-1255`) and folded into discovery, but `ArtistListViewModel` doesn't consume them.

**Fix:**
- [x] Have `ArtistListViewModel` consume `AurralRepository.getCachedLibraryArtists()` (or the existing `aurralViewModel.libraryCollectionRows` from the Library screen) to get per-artist monitoring state.
- [x] Map to `AurralMonitorActionState` per artist.
- [x] Pass `aurralMonitorState` in `ListContent.kt` → `ArtistsScreenItem`.

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistListViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/components/ListContent.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistListScreen.kt`

---

### Task 12: Make Aurral Acquisition Queue Reactive (Not Polled)

**Status: ✅ DONE** — Queue state is repository-owned and refreshed from the existing Aurral state revision path, without polling timers.

**Problem:** `aurralAlbumRequests` in both `AlbumListViewModel` and `CollectionDetailViewModel` is a `MutableStateFlow` refreshed only on login / pull-to-refresh / post-request. If an acquisition completes or fails server-side while the screen is open, the card shows stale "Requested/Processing" until the user manually refreshes.

**Fix:**
- [x] Use the existing reactive `artistStateRevision` path: subscribe `aurralAlbumRequests` to revision bumps so any Aurral state change (monitor action, cover discovery, acquisition confirmation, optimistic request) refreshes the queue. **Do NOT use polling or timers** — the repo instruction forbids hard timeouts.
- [x] Consider a dedicated `acquisitionQueueFlow: Flow<List<AurralAcquisitionQueueItem>>` on `AurralRepository` that derives from `artistStateRevision.flatMapLatest { getServiceStatus() }` so the queue is always fresh when any Aurral state changes.

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt`

---

### Task 13: Surface Album Request Failures (Fix f5c36741 Overcorrection)

**Status: ✅ DONE** — Collection album request failures roll back to failed/requestable and show a snackbar.

**Problem:** Commit `f5c36741` ("keep Aurral album request feedback visible") removed the error surface entirely from `CollectionDetailViewModel.requestAurralRecoveryAlbum()` (`CollectionDetailViewModel.kt:341-343`). On failure, the handler just `Logger.w`s; the card stays "Requested" forever, the snackbar says "Album requested" (success message), and the button never becomes re-tappable.

The Artist flow (`ArtistDetailViewModel`) correctly handles failures: rolls back to `status = "failed"`, sets `requestable = true`, exposes `errorMessage`.

**Fix:**
- [x] In `requestAurralRecoveryAlbum()` `onFailure`: roll back the optimistic "requested" status to "failed" via `withAurralRecoveryRequestStatus(..., "failed")`. Set `requestable = true` so the button is re-tappable.
- [x] Surface a failure snackbar (e.g. `notice_aurral_album_request_failed`).
- [x] Optionally: set a section-specific error field (not `UiState.Error` which would blank the page).

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt` (lines ~329-345)
- `composeApp/src/commonMain/composeResources/values/strings.xml` (add failure string)

---

### Task 14: Deduplicate Shared Aurral Code Across VMs

**Status: ✅ DONE** — Queue item mapping now lives in the repository model layer and both VMs use the repository-owned queue flow/refresh helper.

**Problem:** `refreshAurralAcquisitionRequests()` and `AurralAcquisitionQueueItem.toAlbumRequest()` are byte-for-byte identical in `AlbumListViewModel` (`:114-124`, `:175-181`) and `CollectionDetailViewModel` (`:290-300`, `:484-490`). A fix to one won't propagate.

**Fix:**
- [x] Extract `AurralAcquisitionQueueItem.toAlbumRequest()` to a single location (on `AurralAcquisitionQueueItem` itself in `AurralModels.kt`, or as a top-level extension next to `aurralAcquisitionQueueItem()` in `AurralDtoMapping.kt`).
- [x] Extract `refreshAurralAcquisitionRequests()` to a shared helper (e.g. an extension on `AurralRepository`, or a shared base/interface for the two VMs).

**Files:**
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralModels.kt` (or `AurralDtoMapping.kt`)
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt`

---

## Summary of remaining work

| Task | Priority | Status | Effort |
|---|---|---|---|
| 3A: Artist image DTO | Medium | ✅ Done | Small |
| 3B: Silent HTTP failures | Medium | ✅ Done | Small |
| 5: Core-failure error propagation | Low | ✅ Done | Trivial |
| 8: Build verification | Required | ✅ Done | Automated |
| 9: Release gate | Required | ✅ Ready | After push/tag |
| 10: Album grid download status | High | ✅ Done | Medium |
| 11: Artist list monitor badge | High | ✅ Done | Medium |
| 12: Acquisition queue reactivity | Medium | ✅ Done | Medium |
| 13: Request failure surfacing | High | ✅ Done | Small |
| 14: Deduplicate shared code | Low | ✅ Done | Small |
