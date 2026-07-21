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

- [x] Assert that `MainActivity` registers the permission launcher before `setContent`.
- [x] Assert that login awaits `requestLocalNetworkPermission()` and returns before `login()` on denial.
- [x] Assert that the Android manager handles pre-37, already-granted, absent-launcher, callback, and cancellation paths without a non-null assertion or timeout.
- [x] Run the focused class and record the expected RED result.

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

- [x] Add a KMP permission-manager contract and a compile-only iOS no-op actual.
- [x] Implement Android Activity Result registration and a cancellation-safe suspended request serialized by a mutex.
- [x] Register the launcher before Compose starts and fail closed if registration is unavailable.
- [x] Remove the fire-and-forget `PlatformContext` permission API.
- [x] Await permission before calling login; show denial details and open application settings.
- [x] Run the focused test and login-related host tests; require GREEN.
- [x] Commit as `fix(login): await Android local network permission`.

## Task 7: Navigation lifecycle tests

**Files**

- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/navigation/NavigationViewModelLifecycleSourceTest.kt`

**Steps**

- [x] Assert that `NavDisplay` installs saveable-state and ViewModel-store entry decorators.
- [x] Assert that the lifecycle dependency resolves to `lifecycle-viewmodel-navigation3`.
- [x] Assert that no `PersistentViewModelStoreOwner` is introduced.
- [x] Run the focused class and record the expected RED result.

## Task 8: Entry-scoped ViewModels

**Files**

- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`

**Steps**

- [x] Switch the lifecycle ViewModel module to the Navigation3 integration artifact without changing the pinned Navigation3 version.
- [x] Add saveable-state and ViewModel-store entry decorators to `NavDisplay`.
- [x] Compile and run the focused test. If the pinned Navigation3 API is incompatible, stop this stage and document the dependency constraint rather than silently upgrading the navigation stack.
- [x] Run `NavigationSceneContractSourceTest` and navigation policy tests; require GREEN.
- [x] Commit as `fix(navigation): scope ViewModels to back stack entries`.

## Task 9: Integrated verification

- [x] Run `git diff --check` and confirm the worktree contains only this task's files.
- [x] Run all non-reader focused classes from Tasks 1-8 in one Gradle invocation.
- [x] Run `./gradlew.bat --no-daemon :composeApp:testAndroidHostTest` and compare any failures with clean `master`.
- [x] Run the debug build locally and the signed release build in CI. The local release task correctly refused to run without the signing environment instead of producing an unsigned release artifact.
- [x] Confirm required lineage includes `9c619f10`.
- [x] Install and launch the debug APK on an available ADB Android target; inspect launch logs for crashes.

## Task 10: Version, sync, and release

**Files**

- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-21-upstream-recommended-ports.md`

**Steps**

- [x] Refresh `fork/master`; rebase the task branch if it moved and rerun integrated verification.
- [x] Bump to `versionName = "v1.0.11-iota26"` and `versionCode = 553`.
- [x] Run repository version/release gates and inspect APK package, manifest version, SHA-256, and signing certificate.
- [x] Commit as `release: prepare iota26 upstream compatibility fixes`.
- [x] Verify the dirty ebook worktrees have no path overlap, advance local `master`, and push `fork/master`.
- [x] Create and push tag `v1.0.11-iota26`; wait for the Android release workflow and verify iOS is skipped.
- [x] Download and inspect the published Android release asset and record evidence in this plan.
- [x] Remove only `navic-upstream-recommended-ports` and its merged local branch after this evidence commit; preserve ebook/reader worktrees.

## Release evidence

- Release source: `c35a1f428c3280fed8622794df4de58f211a6221`; required commit `9c619f10` is an ancestor.
- Focused regression suite: all 10 targeted classes passed, including 21 tests added by this plan.
- Full Android host suite: 2,527 tests with the same 74 pre-existing failures reproduced by a fresh detached `198eb932` baseline (2,506 tests, 74 failures); this port introduced no additional failures.
- Local Android validation: debug compile/assemble, version gate, reader-vendor self-test, and attribution checks passed. ADB install and cold launch succeeded on `emulator-5554` with version `553` / `v1.0.11-iota26` and no fatal exception or ANR in the launched process.
- GitHub Actions run: `29831855769` completed successfully. The signed Android release build, signing check, packaged-reader governance, and release publication passed; the iOS build and IPA attachment were skipped.
- Public release: `v1.0.11-iota26`, published 2026-07-21 with Android asset `Navic.apk` (46,359,080 bytes).
- Published APK: package `darkaxt.navic`, version code `553`, version name `v1.0.11-iota26`, and `android.permission.ACCESS_LOCAL_NETWORK` present.
- Published APK SHA-256: `422bfe0cac7f87d3abc270508975d92cf476913fdc728432b41fe90f87fdc885`.
- Published APK signing certificate SHA-256: `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7` (`CN=Darkaxt Navic Release`).
- Published APK governance: all 30 reader vendor files and the Anx Reader, foliate-js, PDF.js, and PlayLikeCurl acknowledgements passed packaged verification.
