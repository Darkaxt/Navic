# Aurral-First Catalog Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Aurral the catalog source of truth for artist and album pages while Navidrome supplies only local ownership, playback, and history evidence.

**Architecture:** Implement this in focused commits. The first completed plan fixes album detail because it is the active broken UI. Later plans reuse the same Aurral-first row model for artist pages, library/search cards, and regression guards. Every plan must preserve asynchronous rendering, cached re-entry, stable row keys, and narrow state collection.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Room, Coroutines/Flow, Coil, Gradle.

---

## Planning Baseline

This plan is based on `fork/master` after commit `bd0ff18b` and the design spec:

- `docs/superpowers/specs/2026-06-28-aurral-first-catalog-ownership-design.md`

The checked-out `navic-upstream-sync-master` worktree may be behind `fork/master`; inspect and sync before implementation.

## Global Rules For Every Plan

- No production code without a failing test first.
- No Gradle run after every tiny edit. Run focused tests during red/green work, then run the plan's Gradle validation only after that plan is complete.
- Commit only after a full plan is complete and validated.
- After each plan, re-check the design spec and search the touched files for remaining local-first catalog paths.
- Do not introduce timeout-based cancellation. Use cached state, idempotent refresh keys, visible loading/error state, and background work.
- Do not create per-row Flow collectors for playback, download, request, or artwork state.
- Do not map large caches or perform broad joins directly in composables.
- Do not clear already-rendered rows to empty/loading on tab return.

## File Map

### Album Detail Ownership

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicy.kt`
  - Owns album recovery matching, Aurral canonical track rows, display rows, and duplicate prevention.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt`
  - Resolves Aurral album identity, loads Aurral tracks, refreshes request status, and publishes cached state.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailScreen.kt`
  - Renders album header/buttons/track rows and must not rebuild canonical rows unnecessarily.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/MoreByArtistRow.kt`
  - Will eventually render Aurral release groups instead of local Navidrome `otherAlbums`.
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicyTest.kt`
  - Primary behavior tests for canonical track rows and duplicate prevention.
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/CollectionAurralRequestFailureSourceTest.kt`
  - Source guard for request failure feedback.

### Artist Ownership And Page Shape

- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt`
  - Builds Aurral artist ownership album rows and missing album rows.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`
  - Loads core Aurral profile, request state, previews, similar artists, and local ownership evidence.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt`
  - Renders Aurral-first header, external links, monitor action, local evidence tracks, owned albums, and missing albums.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailLayoutPolicy.kt`
  - Contains page layout helpers and header link policy.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileState.kt`
  - Determines monitor action visibility/enabled state.
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralFirstArtistPageSourceTest.kt`
  - Source guards for profile image aliases, failure surfacing, and monitor state.
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralArtistDetailScreenLayoutPolicyTest.kt`
  - Source guards for local evidence ordering and external-link rendering.
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt`
  - Behavior tests for ownership/missing release-group rows.

### Aurral Data And Cache

- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`
  - Aurral data access, cached payloads, optimistic request state, release-group cover cache.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralModels.kt`
  - Public Aurral repository models, search items, track items, and request payloads.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralDtos.kt`
  - API DTOs for artist, album, track, request, and service payloads.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralDtoMapping.kt`
  - Maps Aurral album search and track DTOs.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralServiceDtoMapping.kt`
  - Maps service profile release groups and request queue data.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepositorySupport.kt`
  - Cache keys and helper functions.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralMetadataCache.kt`
  - Cache payload type definitions.
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`
  - Repository-level cache and mapping behavior.

### Library, Search, And Album Cards

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/AlbumListScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryAurralDisplayPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search/SearchScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search/viewmodels/SearchViewModel.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/library/LibraryStartupAsyncSourceTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/album/AlbumListViewModelSourceTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/PerformanceAntiRegressionGuardTest.kt`

## Focus Plan 1: Album Detail Canonical Aurral Rows

**Goal:** When an Aurral album match exists, the album detail page renders Aurral canonical track rows only, attaches local ownership evidence to those rows, and does not append local-only rows into the normal track list.

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicyTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/PerformanceAntiRegressionGuardTest.kt`

- [ ] **Step 1: Add a failing duplicate-prevention test**

Add this test to `AurralAlbumRecoveryPolicyTest`:

```kotlin
@Test
fun displayRowsDoNotAppendLocalOnlyDuplicatesWhenAurralTracksExist() {
	val localSong = song(
		title = "Test Drive",
		musicBrainzId = "recording-test-drive",
		trackNumber = 1,
		durationSeconds = 164
	)
	val duplicateLocalSong = song(
		title = "Test Drive",
		musicBrainzId = null,
		trackNumber = 2,
		durationSeconds = 164
	)
	val rows = aurralAlbumDisplayRows(
		album = album(
			name = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
			songs = listOf(localSong, duplicateLocalSong)
		),
		recoveryRows = listOf(
			AurralAlbumRecoveryTrackRow(
				track = AurralAlbumRecoveryTrack(
					id = "aurral-test-drive",
					title = "Test Drive",
					recordingMbid = "recording-test-drive",
					trackNumber = 1,
					durationMs = 164_000
				),
				localSong = localSong,
				ownershipStatus = AurralOwnershipStatus.Owned
			)
		)
	)

	assertEquals(listOf("Test Drive"), rows.map { it.title })
	assertEquals(localSong, rows.single().localSong)
}
```

- [ ] **Step 2: Convert the old local-only insertion test**

Rename `displayRowsKeepLocalOnlySongsWhenAurralHasPartialAlbumData` to `displayRowsKeepLocalOnlySongsOnlyWithoutAurralRecovery` and make it pass `recoveryRows = emptyList()`. Expected rows should remain local-only in the Navidrome fallback path.

- [ ] **Step 3: Run focused red test**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.collection.AurralAlbumRecoveryPolicyTest"
```

Expected: the new duplicate-prevention test fails because `aurralAlbumDisplayRows` appends the duplicate local row.

- [ ] **Step 4: Implement canonical Aurral display rows**

In `aurralAlbumDisplayRows`, keep the existing local fallback branch for `recoveryRows.isEmpty()`. In the non-empty branch, remove `localOnlyRows` from the returned normal rows:

```kotlin
return recoveryRows.map { row ->
	AurralAlbumDisplayRow(
		track = row.track,
		localSong = row.localSong,
		ownershipStatus = row.ownershipStatus,
		title = row.track.title,
		artistName = row.track.artistName ?: row.localSong?.artistName,
		discNumber = row.track.discNumber ?: row.localSong?.discNumber,
		trackNumber = row.track.trackNumber ?: row.localSong?.trackNumber,
		durationMs = row.track.durationMs ?: row.localSong?.duration?.inWholeMilliseconds,
		previewUrl = row.track.previewUrl
	)
}.sortedWith(
	compareBy<AurralAlbumDisplayRow>(
		{ aurralAlbumDisplayDiscKey(it) },
		{ it.trackNumber ?: Int.MAX_VALUE },
		{ it.title }
	)
)
```

Keep `aurralAlbumLocalOnlyDisplayRows` only if a later diagnostics section uses it. If no production code uses it after this change, remove it and its tests in this plan.

- [ ] **Step 5: Preserve stable keys in the album track list**

In `CollectionDetailScreen.kt`, replace `itemsIndexed(group.value)` for Aurral album rows with a stable key. Use:

```kotlin
itemsIndexed(
	items = group.value,
	key = { _, row ->
		row.track?.id?.takeIf { it.isNotBlank() }
			?: row.track?.recordingMbid?.takeIf { it.isNotBlank() }
			?: row.localSong?.id
			?: "${row.discNumber ?: 1}:${row.trackNumber ?: Int.MAX_VALUE}:${row.title}"
	}
) { index, row ->
	// existing row body
}
```

Do the same for playlist/local-only song lists if the current list still uses index-only keys.

- [ ] **Step 6: Add source guard for no local-only append**

Add a test to `PerformanceAntiRegressionGuardTest`:

```kotlin
@Test
fun aurralAlbumDisplayRowsDoNotAppendLocalOnlyRowsWhenRecoveryRowsExist() {
	val source = File("src/commonMain/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicy.kt").readText()
	val functionBody = source.substringAfter("fun aurralAlbumDisplayRows(")
		.substringBefore("fun aurralAlbumHeaderActionStatus")

	assertFalse(
		"localOnlyRows" in functionBody,
		"Aurral-resolved album display rows must not append unmatched local-only songs to the canonical Aurral track list."
	)
}
```

- [ ] **Step 7: Run focused green tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.collection.AurralAlbumRecoveryPolicyTest" --tests "paige.navic.ui.PerformanceAntiRegressionGuardTest"
```

Expected: focused tests pass.

- [ ] **Step 8: Verify no obvious remaining album duplicate path**

Run:

```powershell
rg -n "localOnlyRows|aurralAlbumLocalOnlyDisplayRows|itemsIndexed\\(group\\.value\\)" composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection
```

Expected: no normal album-detail path appends local-only rows after Aurral recovery; no index-only key remains for the Aurral row list.

- [ ] **Step 9: Plan-level Gradle validation**

Run after all Plan 1 steps pass:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
```

Expected: no new failures. If known environmental failures appear, record them with exact names and prove the focused Plan 1 tests pass.

- [ ] **Step 10: Commit Plan 1**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicy.kt `
	composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailScreen.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicyTest.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/PerformanceAntiRegressionGuardTest.kt
git commit -m "fix: keep Aurral album rows canonical"
```

## Focus Plan 2: Album Detail Aurral Header And More-By Rows

**Goal:** Album detail should use Aurral metadata when a match exists, and `More by artist` should be sourced from Aurral release groups with local ownership evidence.

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/MoreByArtistRow.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/AurralAlbumRecoveryPolicyTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/CollectionAurralRequestFailureSourceTest.kt`

- [ ] **Step 1: Add an album page source-state model**

Create a small UI model in `CollectionDetailViewModel.kt` or extract to `AurralAlbumPageState.kt` if the file grows too large:

```kotlin
data class AurralAlbumPageState(
	val source: AurralAlbumPageSource,
	val match: AurralAlbumSearchItem?,
	val rows: List<AurralAlbumRecoveryTrackRow>,
	val loading: Boolean,
	val candidates: List<AurralAlbumSearchItem>
)

enum class AurralAlbumPageSource {
	LocalFallback,
	AurralResolved,
	AurralAmbiguous,
	AurralUnavailable
}
```

- [ ] **Step 2: Test source state transitions**

Add policy tests showing:

- no match and Aurral disabled -> `LocalFallback`
- match found -> `AurralResolved`
- candidates but no confident match -> `AurralAmbiguous`
- endpoint failure with no cache -> `AurralUnavailable`

- [ ] **Step 3: Render Aurral header fields when resolved**

In `CollectionDetailScreen.kt`, derive a displayed header model:

- title from `aurralAlbumRecoveryMatch.title`
- cover from `aurralAlbumRecoveryMatch.coverUrl`
- year/type from `aurralAlbumRecoveryMatch.releaseDate`, `primaryType`, `secondaryTypes`
- local album values only as fallback

- [ ] **Step 4: Replace local `otherAlbums` source for Aurral-resolved artist**

When `aurralAlbumRecoveryMatch.artistMbid` or resolved artist identity is available, load release groups through the existing Aurral artist enrichment path and project local ownership onto them. Do not use `repository.getOtherAlbums(...)` as the primary row source in the Aurral-resolved path.

- [ ] **Step 5: Keep all Aurral album detail work async**

Ensure all Aurral lookup, track loading, release-group loading, and request-status refresh stays inside `viewModelScope.launch(Dispatchers.IO)`. Ensure local evidence matching and row projection that scans or joins lists runs inside `withContext(Dispatchers.Default)` before publishing immutable row state to Compose.

- [ ] **Step 6: Focused validation**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.collection.*" --tests "paige.navic.domain.models.AurralArtistOwnershipPolicyTest"
```

- [ ] **Step 7: Plan-level Gradle validation**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
```

- [ ] **Step 8: Commit Plan 2**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection `
	composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection `
	composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt
git commit -m "feat: make album detail Aurral-first"
```

## Focus Plan 3: Artist Page Ownership And Monitor Reliability

**Goal:** Artist pages iterate Aurral release groups first, then attach local ownership evidence. Synthetic split-credit artists with an Aurral identity must show the full Aurral profile and monitor action.

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileState.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralFirstArtistPageSourceTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileStatePolicyTest.kt`

- [ ] **Step 1: Add failing ownership-order tests**

Test that `aurralArtistOwnershipAlbumRows(...)` iterates Aurral release groups first. A local album without a release-group match should not create a normal owned/missing Aurral row.

- [ ] **Step 2: Add failing synthetic artist profile test**

Test that a state with no local albums but a resolved Aurral identity has:

- monitor action visible
- Aurral header data
- missing release groups
- no empty local-only profile

- [ ] **Step 3: Implement Aurral-first ownership projection**

Change `AurralArtistEnrichmentPolicy.kt` so release groups are the primary source:

- For each Aurral release group, find matching local album evidence.
- Compute green/yellow/red ownership from evidence and request state.
- Do not start by mapping local albums.

- [ ] **Step 4: Preserve async section refresh**

Keep the current independent section refresh shape in `ArtistDetailViewModel.kt`:

- core profile publishes before previews/similar/request sections
- cover hydration happens after row titles are visible
- failures affect only their section

- [ ] **Step 5: Validate**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.AurralArtistOwnershipPolicyTest" --tests "paige.navic.ui.screens.artist.*"
.\gradlew.bat :composeApp:testAndroidHostTest
```

- [ ] **Step 6: Commit Plan 3**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt `
	composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist `
	composeApp/src/commonTest/kotlin/paige/navic/domain/models/AurralArtistOwnershipPolicyTest.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist
git commit -m "feat: project artist pages from Aurral release groups"
```

## Focus Plan 4: Album Cards, Library, And Search Projection

**Goal:** Local-history surfaces may remain locally driven, but displayed album/artist metadata and navigation should prefer Aurral matches and keep existing cache/re-entry performance fixes.

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/AlbumListScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryAurralDisplayPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search/SearchScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search/viewmodels/SearchViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/library/LibraryStartupAsyncSourceTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/album/AlbumListViewModelSourceTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/PerformanceAntiRegressionGuardTest.kt`

- [ ] **Step 1: Add source guards for Aurral-backed album navigation**

Tests should assert that matched album cards navigate to the Aurral-backed album detail route or pass Aurral identity into collection detail.

- [ ] **Step 2: Add source guards for no tab-return reset**

Keep existing `libraryCollectionRows` cache behavior and add guards for album/search projection caches if new state is added.

- [ ] **Step 3: Implement matched album display projection**

ViewModels should expose album rows with:

- local album id
- Aurral release group id when known
- displayed title/cover/year/artist from Aurral when matched
- local ownership/download/playback evidence

- [ ] **Step 4: Keep projection off the UI dispatcher**

Any join between local albums and Aurral cache must run in ViewModel/repository or `produceState` with `withContext(Dispatchers.Default)`.

- [ ] **Step 5: Validate**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.library.LibraryStartupAsyncSourceTest" --tests "paige.navic.ui.screens.album.*" --tests "paige.navic.ui.PerformanceAntiRegressionGuardTest"
.\gradlew.bat :composeApp:testAndroidHostTest
```

- [ ] **Step 6: Commit Plan 4**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/ui/screens/album `
	composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library `
	composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/library `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/album `
	composeApp/src/commonTest/kotlin/paige/navic/ui/PerformanceAntiRegressionGuardTest.kt
git commit -m "feat: project album surfaces through Aurral"
```

## Focus Plan 5: Aurral Route Identity Propagation

**Goal:** Every Aurral-aware album surface that already knows a release group must open album detail with that Aurral identity preserved. Plain `Screen.CollectionDetail(album.id, ...)` is allowed only for genuinely local-only fallback surfaces, playlist/station routes, or song-context routes where no Aurral album identity exists.

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralArtistScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/MoreByArtistRow.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt`
- Modify only if needed: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubRoutes.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileStatePolicyTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/CollectionAurralAlbumPageSourceTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/aurral/AurralHubDisplayPolicyTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/AlbumSortPolicyTest.kt`

- [x] **Step 1: Add source guards for route identity**

Tests should assert that:

- Artist owned/partial album rows do not route through plain `Screen.CollectionDetail(album.id, "artist")` when the row has a release group.
- Aurral artist album rows route local matches through `aurralAlbumCollectionDetailRoute(...)`.
- More-by-artist resolved rows route local matches through the same Aurral-backed route helper.
- Aurral missing album local-match rows preserve release-group metadata when they open local album detail.

- [x] **Step 2: Implement route helper reuse**

Use the existing `aurralAlbumCollectionDetailRoute(AurralAlbumSearchItem, libraryAlbumId, tab)` helper rather than creating another route encoding path. Add narrow conversion helpers only where the source row is not already an `AurralAlbumSearchItem`.

- [x] **Step 3: Keep navigation work cheap**

Route construction must be pure string/object projection. Do not add Aurral network calls, per-row Flow collectors, or broad cache joins to composables.

- [x] **Step 4: Validate**

Evidence from this pass:

- RED route guards failed first: 4 focused route-identity tests failed on the old direct plain route paths.
- GREEN route guards and row policy passed:
  `.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.artist.AurralArtistProfileStatePolicyTest.artistDetailOwnedAlbumRowsNavigateWithAurralReleaseGroupIdentity" --tests "paige.navic.ui.screens.collection.CollectionAurralAlbumPageSourceTest.moreByArtistLocalMatchesNavigateWithAurralReleaseGroupIdentity" --tests "paige.navic.ui.screens.aurral.AurralHubDisplayPolicyTest.missingAlbumScreenLocalMatchesNavigateWithAurralReleaseGroupIdentity" --tests "paige.navic.ui.screens.aurral.AurralHubDisplayPolicyTest.aurralArtistScreenLocalMatchesNavigateWithAurralReleaseGroupIdentity" --tests "paige.navic.ui.screens.aurral.AurralHubDisplayPolicyTest.aurralArtistLocalAlbumRoutesPreserveMatchedReleaseGroup" --tests "paige.navic.domain.models.AlbumSortPolicyTest.localAurralArtistAlbumRowsPreserveMatchedReleaseGroup"`
  passed.
- Focused Plan 5 suite passed:
  `.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.artist.AurralArtistProfileStatePolicyTest" --tests "paige.navic.ui.screens.collection.CollectionAurralAlbumPageSourceTest" --tests "paige.navic.ui.screens.aurral.AurralHubDisplayPolicyTest" --tests "paige.navic.domain.models.AlbumSortPolicyTest"`
  passed.
- Full host audit remains at the known baseline: 1848 tests completed, 36 failed, all in reader reference/parity tests and `AndroidMediaPlayerViewModelSourceTest`.

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.artist.AurralArtistProfileStatePolicyTest" --tests "paige.navic.ui.screens.collection.CollectionAurralAlbumPageSourceTest" --tests "paige.navic.ui.screens.aurral.AurralHubDisplayPolicyTest"
.\gradlew.bat :composeApp:testAndroidHostTest
```

- [x] **Step 5: Commit Plan 5**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt `
	composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral `
	composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/MoreByArtistRow.kt `
	composeApp/src/commonMain/kotlin/paige/navic/domain/models/AurralArtistEnrichmentPolicy.kt `
	composeApp/src/commonTest/kotlin/paige/navic/domain/models/AlbumSortPolicyTest.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/artist/AurralArtistProfileStatePolicyTest.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/collection/CollectionAurralAlbumPageSourceTest.kt `
	composeApp/src/commonTest/kotlin/paige/navic/ui/screens/aurral/AurralHubDisplayPolicyTest.kt `
	docs/superpowers/plans/2026-06-28-aurral-first-catalog-ownership-implementation-plan.md
git commit -m "fix: preserve Aurral identity across album routes"
```

## Focus Plan 6: Final Audit, Sync, And Release

**Goal:** Prove the full spec is satisfied, sync to GitHub master, and publish a public release.

- [ ] **Step 1: Requirement audit**

Read the design spec and create a checklist covering:

- Aurral owns album detail metadata.
- Aurral owns artist profile metadata.
- Navidrome supplies local ownership/playback evidence only.
- `Test Drive` duplicate row is fixed.
- Missing albums are red.
- Monitor action is visible for resolved Aurral artists.
- Tags/links are async.
- No timeout-based cancellation was added.
- No per-row Flow collectors were introduced.
- Cached rows remain visible on return.

- [ ] **Step 2: Source search audit**

Run:

```powershell
rg -n "runBlocking|HttpTimeout|collectAsState\\(|getCollectionDownloadStatus\\(|localOnlyRows|aurralAlbumLocalOnlyDisplayRows" composeApp/src/commonMain/kotlin
rg -n "repository\\.getOtherAlbums|state\\.albums\\.ifEmpty|aurralOwnedOrPartialAlbums\\.ifEmpty" composeApp/src/commonMain/kotlin/paige/navic/ui/screens
```

Expected: every remaining hit is either outside the Aurral-first catalog path, intentionally allowed fallback, or covered by a source guard.

- [ ] **Step 3: Full Gradle validation**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
.\gradlew.bat :androidApp:assembleRelease
```

Expected: tests/build pass or only documented pre-existing environmental reader-reference failures remain. Do not publish a release if the release APK does not build.

- [ ] **Step 4: Sync master**

```powershell
git fetch --all --prune
git status --short --branch
git log --oneline --decorate -5
git push fork master
```

- [ ] **Step 5: Publish release**

Use the existing release script and continue the current `v1.0.11-thetaXX` sequence:

```powershell
.\scripts\publish-github-release.ps1
```

If the script requires explicit version input, use the next theta patch after the current highest published theta tag.
