# Recommended Upstream Ports Implementation Plan

**Goal:** Port the five useful upstream fixes into Navic without importing the 34-commit upstream batch or regressing fork-specific behavior.

**Design:** `docs/superpowers/specs/2026-07-21-upstream-recommended-ports-design.md`

**Branch:** `fix/upstream-recommended-ports`

## Task 1: LRCLIB policy tests

**Files**

- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/LrcLibLookupPolicyTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/lyrics/LyricsConfigTest.kt`

**Steps**

- [x] Add tests for default `/api/search`, legacy `/api/get` migration, custom search endpoint preservation, parenthetical-title cleanup, punctuation/case normalization, whole-second duration, deterministic candidate ranking, and rejection of lyric-less or mismatched candidates.
- [x] Run `./gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.LrcLibLookupPolicyTest" --tests "paige.navic.domain.models.lyrics.LyricsConfigTest"` and record the expected RED result.

## Task 2: LRCLIB search and exact fallback

**Files**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/lyrics/LyricsConfig.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/LrcLibLookupPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/LyricsRepository.kt`

**Steps**

- [x] Add the serializable candidate model and pure endpoint/title/scoring policy.
- [x] Search with relaxed title and artist metadata.
- [x] Serialize the best credible candidate into the existing parser contract.
- [x] Fall back to `/api/get` with original metadata and `duration.inWholeSeconds` after a failed or unusable search.
- [x] Rerun the focused tests and `LyricsRepositoryPolicyTest`; require GREEN.
- [x] Commit as `fix(lyrics): make LRCLIB lookup tolerant and deterministic`.

## Task 3: Playlist-integrity tests

**Files**

- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/PlaylistSongIntegrityPolicyTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/repositories/PlaylistSongIntegritySourceTest.kt`

**Steps**

- [x] Test that playlist IDs are unioned into an authoritative library keep-set and that a suppressed deletion plan remains suppressed.
- [x] Add source-contract assertions requiring Room `@Upsert`, an all-playlist-song-ID query, and one transactional replacement call with no repository-level delete/reinsert sequence.
- [x] Run the two focused classes and record the expected RED result.

## Task 4: Atomic playlist refresh and safe deletion

**Files**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/SongDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/PlaylistDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt`

**Steps**

- [x] Replace song `REPLACE` writes with Room `@Upsert`; retain conflict-ignore only where explicitly intended.
- [x] Add `PlaylistDao.getAllPlaylistSongIds()`.
- [x] Add a pure keep-set policy and union playlist IDs only when authoritative song deletion is allowed.
- [x] Upsert playlist songs, construct ordered cross-references, and call `replacePlaylistSongs` once, including for an empty playlist.
- [x] Run focused tests plus `LibrarySyncDeletionPolicyTest` and `DbRepositoryAlbumSyncPolicyTest`; require GREEN.
- [x] Commit as `fix(playlists): preserve membership during song refresh`.

## Task 5: Permission-ordering tests

**Files**

- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/manager/LocalNetworkPermissionSourceTest.kt`

**Steps**

- [ ] Assert that `MainActivity` registers the permission launcher before `setContent`.
- [ ] Assert that login awaits `requestLocalNetworkPermission()` and returns before `login()` on denial.
- [ ] Assert that the Android manager handles pre-37, already-granted, absent-launcher, callback, and cancellation paths without a non-null assertion or timeout.
- [ ] Run the focused class and record the expected RED result.

## Task 6: Android 17 permission gate

**Files**

- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PermissionManager.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/domain/manager/PermissionManager.android.kt`
- Create: `composeApp/src/iosMain/kotlin/paige/navic/domain/manager/PermissionManager.ios.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/paige/navic/di/PlatformModule.ios.kt`
- Modify: `androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/util/core/PlatformContext.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/util/core/PlatformContext.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/paige/navic/util/core/PlatformContext.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/login/pages/Content.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Steps**

- [ ] Add a KMP permission-manager contract and a compile-only iOS no-op actual.
- [ ] Implement Android Activity Result registration and a cancellation-safe suspended request serialized by a mutex.
- [ ] Register the launcher before Compose starts and fail closed if registration is unavailable.
- [ ] Remove the fire-and-forget `PlatformContext` permission API.
- [ ] Await permission before calling login; show denial details and open application settings.
- [ ] Run the focused test and login-related host tests; require GREEN.
- [ ] Commit as `fix(login): await Android local network permission`.

## Task 7: Navigation lifecycle tests

**Files**

- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/navigation/NavigationViewModelLifecycleSourceTest.kt`

**Steps**

- [ ] Assert that `NavDisplay` installs saveable-state and ViewModel-store entry decorators.
- [ ] Assert that the lifecycle dependency resolves to `lifecycle-viewmodel-navigation3`.
- [ ] Assert that no `PersistentViewModelStoreOwner` is introduced.
- [ ] Run the focused class and record the expected RED result.

## Task 8: Entry-scoped ViewModels

**Files**

- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`

**Steps**

- [ ] Switch the lifecycle ViewModel module to the Navigation3 integration artifact without changing the pinned Navigation3 version.
- [ ] Add saveable-state and ViewModel-store entry decorators to `NavDisplay`.
- [ ] Compile and run the focused test. If the pinned Navigation3 API is incompatible, stop this stage and document the dependency constraint rather than silently upgrading the navigation stack.
- [ ] Run `NavigationSceneContractSourceTest` and navigation policy tests; require GREEN.
- [ ] Commit as `fix(navigation): scope ViewModels to back stack entries`.

## Task 9: Integrated verification

- [ ] Run `git diff --check` and confirm the worktree contains only this task's files.
- [ ] Run all non-reader focused classes from Tasks 1-8 in one Gradle invocation.
- [ ] Run `./gradlew.bat --no-daemon :composeApp:testAndroidHostTest` and compare any failures with clean `master`.
- [ ] Run `./gradlew.bat --no-daemon :androidApp:assembleDebug :androidApp:assembleRelease`.
- [ ] Confirm required lineage includes `9c619f10`.
- [ ] Install and launch the debug APK on an available ADB Android target; inspect launch logs for crashes.

## Task 10: Version, sync, and release

**Files**

- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-21-upstream-recommended-ports.md`

**Steps**

- [ ] Refresh `fork/master`; rebase the task branch if it moved and rerun integrated verification.
- [ ] Bump to `versionName = "v1.0.11-iota26"` and `versionCode = 553`.
- [ ] Run repository version/release gates and inspect APK package, manifest version, SHA-256, and signing certificate.
- [ ] Commit as `release: prepare iota26 upstream compatibility fixes`.
- [ ] Verify the dirty master worktree has no path overlap, fast-forward local `master`, and push `fork/master`.
- [ ] Create and push tag `v1.0.11-iota26`; wait for the Android release workflow and verify iOS is skipped.
- [ ] Download or inspect published release assets and record evidence in this plan.
- [ ] Remove only `navic-upstream-recommended-ports` and its merged local branch; preserve ebook/reader worktrees.
