# Aurral-First Artist Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Navic artist detail pages Aurral-first when Aurral is enabled, with Navidrome used only for local ownership, playable tracks, and fallback when Aurral is disabled or unresolved.

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

**Files:**
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileStatePolicyTest.kt`
- Create or modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt`
- Modify as needed: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`

- [ ] Add a monitor-state test where an Aurral MBID plus missing library artist yields `VerifiedUnmonitored`, and the action is visible and enabled.
- [ ] Add a monitor-state test where auth/config failure yields a visible error state, not a silently missing button.
- [ ] Add a John Powell ownership test: one local `Test Drive` track maps to a partial How to Train Your Dragon release group.
- [ ] Add a split-row test: owned/partial release groups are separate from missing release groups.
- [ ] Add a cache-seeding test: cached Aurral profile data can render before fresh network sections finish.

Expected RED command:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.artist.*Aurral*" --tests "paige.navic.domain.models.*Aurral*"
```

---

### Task 2: Define The Aurral-First UI State

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileState.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`

- [ ] Add an explicit `AurralArtistProfileUiState` with independent section states:
  - `profile`
  - `monitor`
  - `ownership`
  - `previewTracks`
  - `similarArtists`
  - `requests`
  - `localPlayback`
- [ ] Add an explicit monitor enum:

```kotlin
internal enum class AurralArtistMonitorUiState {
    UnknownResolving,
    VerifiedMonitored,
    VerifiedUnmonitored,
    Updating,
    Error
}
```

- [ ] Keep the monitor button visible for `UnknownResolving`, `VerifiedMonitored`, `VerifiedUnmonitored`, `Updating`, and `Error` whenever a candidate MBID exists.
- [ ] Disable only while updating or while no actionable MBID/auth context exists.
- [ ] Keep Navidrome-only rendering as fallback when Aurral is disabled or no Aurral identity can be resolved.

---

### Task 3: Fetch Full Aurral Artist Profile Data

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`
- Modify related DTO/model files under `composeApp/src/commonMain/kotlin/paige/navic/domain/models/`

- [ ] Keep lightweight/core fetches for list surfaces.
- [ ] Add a full artist-profile fetch for artist detail pages, including:
  - name
  - image/proxy URL
  - bio
  - tags/genres
  - external links/relations
  - release groups
  - preview tracks
  - similar artists
  - request/download status
  - library monitor status
- [ ] Preserve bearer/basic auth handling already used by the Aurral integration.
- [ ] Treat Aurral 404 library lookup as `VerifiedUnmonitored`.
- [ ] Treat Aurral profile success plus library absence as a valid profile, not a failure.
- [ ] Do not block artist page rendering on preview, similar, request, or release-group detail calls.

---

### Task 4: Build Navidrome Ownership Overlay

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`

- [ ] Match Navidrome local albums/tracks to Aurral release groups in this order:
  1. MusicBrainz release-group ID when present.
  2. Normalized album title plus normalized artist name.
  3. Track evidence fallback: local track title appears in a lazily loaded Aurral release tracklist and the album title is a plausible soundtrack/edition match.
- [ ] Produce ownership buckets:
  - `Owned`
  - `Partial`
  - `Missing`
  - `Requested`
  - `Failed`
  - `Processing`
- [ ] Use green/yellow-orange/red status dots:
  - green: owned/complete
  - yellow-orange: partial/local evidence exists
  - red: missing/failed
- [ ] For John Powell, classify the How to Train Your Dragon release group as `Partial` with one local playable track, not as fully owned.
- [ ] Keep local playable track IDs attached to owned/partial rows so tapping can play through Navidrome.

---

### Task 5: Smart Cache And Async Refresh

**Files:**
- Modify or create cache support under `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`

- [ ] Seed the artist page immediately from the best cached data:
  - Aurral profile cache keyed by Aurral MBID
  - Aurral release-group cache keyed by artist MBID
  - Aurral cover URL cache keyed by image/proxy URL or MBID
  - Navidrome local artist/albums/tracks cache keyed by artist ID and music DB revision
- [ ] Refresh each section independently after initial render.
- [ ] Do not clear already rendered Aurral rows while refreshing.
- [ ] Invalidate ownership overlay when Navidrome local album/song data changes.
- [ ] Invalidate monitor state immediately after monitor/unmonitor action succeeds.
- [ ] Keep failed section refreshes local to that section; do not collapse the whole artist page.

---

### Task 6: Render Aurral-First Artist Page

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt`
- Modify or create components under `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/components/`
- Modify strings: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] Hero uses Aurral image, name, bio, tags, and links when available.
- [ ] Navidrome title/image/bio appear only as initial placeholder or fallback.
- [ ] Add or restore these sections:
  - `Owned & partial albums`
  - `Missing albums`
  - `Preview tracks`
  - `Similar artists`
  - `External links`
  - local playable tracks/frequently played evidence
- [ ] Split albums into owned/partial and missing rows. Do not merge them into one ambiguous row.
- [ ] Show per-section loading placeholders when data is resolving.
- [ ] Keep row titles visible before images/results finish, so the page does not look empty.
- [ ] Preserve the existing music player and mini-player behavior.

---

### Task 7: Fix Monitor/Unmonitor Action

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`
- Modify tests from Task 1.

- [ ] The monitor button directly calls Aurral monitor/unmonitor.
- [ ] Add/monitor action uses Aurral MBID and profile payload, even when the artist is absent from Aurral/Lidarr library.
- [ ] Unmonitor keeps the existing confirmation dialog.
- [ ] The overflow menu may include `Open in Aurral`; it must not replace the direct monitor action.
- [ ] Aurral config/auth errors should show a visible message and service-status icon, not hide the action silently.

---

### Task 8: Device Verification

**Files:** no production files unless a bug is found.

- [ ] Build a debug APK for device validation.
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

Do not publish until:

- [ ] Focused Aurral artist tests pass.
- [ ] Existing artwork/caching tests still pass.
- [ ] Device verification passes for John Powell.
- [ ] No unreviewed unrelated dirty files are included.
- [ ] Master is synced with remotes before commit, push, and release.
