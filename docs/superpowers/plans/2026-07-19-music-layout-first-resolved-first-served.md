# Music Layout First-Resolved-First-Served Implementation Plan

> **Execution:** Implement this plan task-by-task, using isolated subagents only for independent file sets. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent library-scale memory amplification and make genre refresh publish local and incremental results without waiting for complete sync work.

**Architecture:** Summary and detail routes receive separate Room projections. Detail flows are keyed by route identity and remain subscribed while the serialized sync actor writes updates. Whole-library joins used only for sorting or artwork fallback are replaced by narrow metadata/identity projections, and library sync uses a fixed worker set.

**Tech Stack:** Kotlin, Compose Multiplatform for Android, Room 3, Kotlin coroutines/Flow, Koin, Gradle Android host tests, ADB.

---

### Task 1: Lock the crash-prevention contracts

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/repositories/MusicLayoutDataLoadingSourceTest.kt`
- Test: existing genre grouping/detail policy tests

- [x] Add source-contract assertions for every acceptance criterion that can be proven structurally: no all-library genre detail, no album-song relation in SongRepository, no all-song Artist Detail scan, no unfiltered Most Played artwork flow, and no per-album coroutine map.
- [x] Run `./gradlew :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.MusicLayoutDataLoadingSourceTest" --console=plain`.
- [x] Confirm failures identify the current production patterns rather than test setup errors.

### Task 2: Introduce lightweight genre summaries

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/DomainGenreSummary.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/AlbumDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/GenreGroupingPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/GenreRepository.kt`
- Modify: genre list ViewModel, screen content, and card types
- Test: `GenreGroupingPolicyTest.kt`

- [x] Add failing policy tests proving summaries expand compound genres, deduplicate albums, count songs without retaining them, and expose at most two cover IDs.
- [x] Add an `AlbumGenreMetadata` projection (`albumId`, `genre`, `genres`, `coverArtId`, `songCount`) and reactive DAO flow that never joins `SongEntity`.
- [x] Map metadata to immutable `DomainGenreSummary` values and migrate genre list surfaces.
- [x] Run the focused policy and source-contract tests, then commit the independently buildable summary boundary.

### Task 3: Make genre detail targeted and reactive

**Files:**
- Modify: `AlbumDao.kt`, `GenreRepository.kt`, `GenreDetailPolicy.kt`, `GenreDetailViewModel.kt`, `GenreListViewModel.kt`, and Koin bindings if constructor dependencies change
- Test: `GenreDetailPolicyTest.kt` and the source-contract test

- [x] Add failing tests proving candidate albums are filtered by exact normalized genre membership and detail songs/artists/duration are derived from one prepared album/song set.
- [x] Expose `observeGenreByName(name)` from the repository using `AlbumDao.getAlbumsByGenre(name)` followed by exact domain filtering.
- [x] Have both genre ViewModels retain Room state while `SyncManager.syncNow()` runs. Refresh errors overlay existing data; successful Room writes appear through the active flow.
- [x] Run focused tests and compile, then commit the targeted reactive detail boundary.

### Task 4: Remove SongRepository's nested album-song graph

**Files:**
- Modify: `AlbumDao.kt`, `SongRepository.kt`, `SortUtils.kt`, and `QuickPicksPolicy.kt`
- Test: `QuickPicksPolicyTest.kt` and the source-contract test

- [x] Add a failing test proving Quick Picks/Newest consume an `albumId -> createdAt` map with unchanged ordering.
- [x] Add an `AlbumSongSortMetadata` DAO flow containing only `albumId` and `createdAt`.
- [x] Replace every SongRepository album relation read with the metadata map; retain downloads and song table reactivity.
- [x] Run focused tests and compile, then commit.

### Task 5: Restrict Artist Detail and Most Played lookups

**Files:**
- Modify: `SongDao.kt`, `ArtistDao.kt`, `AlbumDao.kt`, `SongRepository.kt`, `ArtistDetailViewModel.kt`, and `MostPlayedShortcutsViewModel.kt`
- Test: artist detail layout/policy tests, Most Played artwork/navigation tests, and source-contract test

- [x] Add failing tests for escaped contributor candidate patterns and visible artist-shortcut identity extraction.
- [x] Add targeted DAO queries for direct artist IDs/names plus contributor candidates; retain exact Kotlin credit matching after the prefilter.
- [x] Change Artist Detail to load only candidate credit songs.
- [x] Derive artist shortcut identities first and switch Most Played to targeted artist/album/song artwork flows with `flatMapLatest`.
- [x] Run focused tests and compile, then commit.

### Task 6: Bound album sync work creation

**Files:**
- Modify: `DbRepository.kt`
- Test: source-contract test plus existing sync policy tests

- [x] Verify the source test fails on `allAlbumSummaries.map { launch { ... } }`.
- [x] Introduce a bounded summary channel and exactly `LIBRARY_SYNC_NETWORK_CONCURRENCY` fetch workers. Close the output channel only after all workers finish.
- [x] Preserve serialization-skip behavior, progress increments, batched writes, valid-ID tracking, and deletion gates.
- [x] Run focused tests and compile, then commit.

### Task 7: Validate the staged release

**Files:**
- Modify: specification/plan only for observed evidence and final disposition

- [x] Run `git diff --check`.
- [x] Run all newly added and affected focused tests.
- [x] Run `./gradlew :composeApp:testAndroidHostTest --console=plain` and classify every failure against clean baseline.
- [x] Run `./gradlew :androidApp:assembleDebug --console=plain` and the repository's Android release validation tasks.
- [ ] Install the APK on the connected phone, clear logcat, open Classical Crossover from Most Played, and sample process PSS/RSS during loading. The phone was absent from ADB at release time; upgrade install, launch, and an 88,143 KiB startup PSS sample passed on the logged-out emulator, so the authenticated route remains the post-release canary.

### Task 8: Integrate, publish, verify, and clean

**Files:**
- Modify: `androidApp/build.gradle.kts` for the next unused padded iota version
- Modify: specification/plan with release evidence

- [x] Confirm the feature lineage contains current `master` and required commit `9c619f10`, then rerun the validation matrix.
- [x] Fast-forward local `master` without staging any uncommitted ebook files.
- [x] Commit the release bump, push `master`, tag `v1.0.11-iota25`, and publish Android only.
- [x] Verify GitHub workflow success, release asset digest, APK signature certificate, embedded version, upgrade install, and launch. Authenticated route behavior remains the explicit post-release canary above.
- [x] Commit/push release evidence.
- [x] Remove only `navic-music-layout-memory` and `fix/music-layout-memory`; preserve all reader worktrees and artifacts owned by other tasks.

## Validation Record

- Focused policy, DAO/source-contract, and imported PlayLikeCurl tests pass.
- Full Android host suite: 2,506 tests, 74 failures. Detached baseline: 2,491 tests, the same 74 failures. Net result: 15 added passing tests and no branch-owned failures.
- Public workflow: `29704807067`, Android success, iOS skipped.
- Release: `v1.0.11-iota25` at `999a52ca7148d0877cb09c85d363da8fb43bbc0d`.
- APK: version code `552`, SHA-256 `18a290114547810755e9a116e16b5c00d9de42d8a2187c138b7467a4cb539654`, expected release certificate verified.
- ADB: `emulator-5554` upgraded from `iota23` to `iota25` and launched; no phone was connected and the emulator has no authenticated library session.
