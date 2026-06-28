# Reader And Whispersync Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining reader and Whispersync gaps through staged, source-backed milestones instead of isolated microfixes.

**Architecture:** Komikku remains authoritative for native reader shell, tap ownership, menu/chrome behavior, progress rail, and settings layout. Anx/Foliate remains authoritative for EPUB/PDF behavior, bridge events, locators, annotations, visible ranges, media overlays, and style/font behavior. Whispersync work must build on those boundaries and must not bypass controller-owned state.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android WebView, Foliate/PDF.js reader assets, Gradle host/common tests, readerdev emulator, ADB matrix scripts, GitHub release scripts.

---

## Stage Gates

Each stage is a complete deliverable:

- It starts with a failing host/source guard or an emulator reproduction.
- It edits only the files listed for that stage unless the failing evidence proves a dependency.
- It ends with focused Gradle validation, `node --check` for touched JS, `git diff --check`, and a concise validation-log entry when emulator or ADB was used.
- It is committed after the stage passes.
- A public release is published only after a stage fixes a major user-visible blocker or after the final release-candidate gate.

## Stage 0: Plan And Spec Alignment

**Purpose:** Make this staged plan the active execution artifact so context compaction does not collapse work back into microfixes.

**Files:**
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- Modify: `docs/superpowers/specs/2026-06-18-whispersync-design.md`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md` only when an actual validation run occurs.

- [x] Add references from both specs to this plan.
- [x] Run `git diff --check`.
- [x] Commit the planning alignment before code stages. Completed in `7442f5c8`.

## Stage 1: Reader Shell Blocker Gate

**Purpose:** Stabilize the behavior Whispersync depends on: native tap/drag ownership, cover lifecycle, chapter-local progress rail endpoints, resume persistence, and visible-range reporting.

**Main files:**
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPublicationRuntimeHost.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderChapterNavigator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/KomikkuViewerNavigation.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-location.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-shell-cover.js`

**Tests and tools:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderKomikkuBackboneResetTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeNavigationFlowTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderCoordinatorTest.kt`
- `scripts/install-reader-dev.ps1`
- `scripts/adb-reader-komikku-matrix.ps1`

- [ ] Add or tighten a host/source guard for the exact blocker being addressed.
- [ ] Run the focused reader host test that proves the guard fails before implementation.
- [ ] Implement the smallest controller/native-shell/runtime change that satisfies Komikku ownership and Anx bridge contracts.
- [ ] Run the focused reader host tests listed above.
- [ ] Run `node --check` for each touched `navic-reader-*.js` module.
- [ ] Run `.\gradlew.bat --no-daemon :composeApp:testAndroid`.
- [x] Run the readerdev emulator gate with `scripts\adb-reader-komikku-matrix.ps1` after every major shell change. Baseline run `captures/reader-komikku-matrix/stage1-baseline-20260628-222250` passed normal reader tap, drag, edge tap, and texture direction rows; cover rows were invalid because the run started away from the native cover.
- [x] Run the prepared-cover readerdev gate. `captures/reader-komikku-matrix/stage1-cover-prepared-20260628-223828` passed all 12 rows after adding `-PrepareReaderLaunch`, including native cover visible, cover tap, cover drag, content tap/drag, edge taps, and texture direction.
- [x] Append one concise result to `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`.
- [ ] Commit the completed blocker slice.

## Stage 2: Anx Behavior Completion Gate

**Purpose:** Make remaining Anx/Foliate behavior real at the controller/UI boundary, not just type-compatible.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderAnnotations.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBookmarks.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSelectionActions.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSelectionNoteDialog.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAnnotationDialog.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderFootnoteDialog.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-media.js`
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart`
- `tmp/references/anx-reader/assets/foliate-js/src/view.js`

**Tests:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderAnnotationStateTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderBridgeProtocolTest.kt`

- [ ] Pick one Anx event or interaction at a time: selection, annotation, footnote, overlay, link, or history.
- [ ] Add a source-reading guard that proves the Anx route and the Navic controller/UI route both exist.
- [ ] Implement controller state and UI behavior when the guard proves a gap.
- [ ] Run focused tests, `:composeApp:testAndroid`, and JS syntax checks for touched modules.
- [ ] Commit each completed behavior route.

## Stage 3: PDF And Fixed-Layout Gate

**Purpose:** Bring PDF/image behavior under the same Komikku shell and Anx/Foliate engine boundary as EPUB.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngineHostProtocol.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-pdf.js`
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/pdf.js`
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/fixed-layout.js`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt`
- `tmp/references/anx-reader/assets/foliate-js/src/pdf.js`

**Tests:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliatePdfAnxParityTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeNavigationFlowTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderChromeStateTest.kt`

- [ ] Add a failing guard for the next PDF/fixed-layout behavior gap before touching runtime code.
- [ ] Implement the adapter/runtime behavior behind `ReaderEngine`.
- [ ] Validate with focused host tests, JS syntax checks, and emulator PDF matrix coverage.
- [ ] Commit the completed PDF slice.

## Stage 4: Whispersync API And Launch Gate

**Purpose:** Keep Navic aligned with the current Bindery schema and ensure ready pairs launch a paired reader session with the correct sidecar and audiobook manifest.

**Current completed slice:** Current Bindery audio resource/source-release fields, audiobook quality ordering, Aurral-first source routing support, and playback error notifier extraction were validated with `.\gradlew.bat --no-daemon :composeApp:testAndroid` and committed in `b1e20000`.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyDtoMapping.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyWhispersyncCoverOverlay.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncLaunchPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOpenRequest.kt`
- `C:/Users/darka/Documents/Projects/Stremio Add-on Tester/github-export/bindery/docs/navic-opds-api-schema.md`

**Tests:**
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyBookSyncJsonTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryCatalogJsonTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryProgressCacheTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicyTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicySourceTest.kt`

- [ ] Add fixture coverage from the current Bindery schema before changing parser code.
- [ ] Keep exact ready pairs launchable from embedded sync pairs and `/sync` endpoint responses.
- [ ] Keep book cover headset badges gated by exact ready pairs with artifact href.
- [ ] Run focused Bindery tests and `:composeApp:testAndroid`.
- [ ] Commit API alignment only after all parser/launch tests pass.

## Stage 5: Whispersync Enjoyment Gate

**Purpose:** Make the paired ebook/audiobook experience usable: page-to-audio seek, audio-to-text follow, visual cue overlay, exact resume, and playback ownership.

**Current completed slices:**
- Explicit sidecar track identity now wins over stale or generic audio resource names in both page-to-audio seek command creation and audio-follow active segment matching. The slice was guarded by red-first tests in `ReaderWhispersyncPlaybackPolicyTest` and `WhispersyncTimelineParserTest`, then validated with `:composeApp:testAndroid`.
- Bindery sidecars that expose only the exact audiobook `bookFileId` now resolve the audiobook manifest through the exact book/audiobook file pair before opening playback. The slice was guarded by a red-first `BinderyRepositoryTest` and validated against readerdev on book `3809`.
- Direct Foliate media-overlay activation now feeds a controller-owned audio seek target instead of waiting for a later visible-range follow event. The slice was guarded by a red-first `ReaderControllerTest` and validated with readerdev page-scoped, audio-follow, and char-offset overlay probes.
- Readerdev explicit direct Bindery file launches now canonicalize `/api/v1/book/{bookId}/file?bookFileId=...` to the matching `/opds/books/{bookId}/resources/{resourceKey}` href before launch. This keeps emulator Whispersync/progress probes aligned with production OPDS identity and prevents false `resourceKey=file` progress-save 400s from masking reader behavior.
- Reader launch now derives the Whispersync artifact id from sidecar paths such as `/opds/books/3809/sync/8` when the route omits the explicit artifact id, guarded by `ReaderWhispersyncLaunchPolicyTest`.
- The page-scoped Whispersync probe no longer awaits Foliate `goToHref` promises for diagnostic jumps, because Foliate can emit `loadDoc`/relocation state without settling the promise. This keeps the probe from reporting false failures while still snapshotting real visible ranges.
- Media-overlay audio-follow now refuses to start a second runtime relocation while a user/probe relocation is already active. The slice is guarded by `ReaderRuntimeAssetsTest.androidReaderDoesNotLetMediaOverlayFollowInterruptUserRelocation` and validated by readerdev evidence that unsupported page navigation remains on `OEBPS/xhtml/mini_toc.xhtml` after a prior audio-follow overlay.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/WhispersyncModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncPlaybackPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReadaloudModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncPlayerDialog.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidAudiobookPlaybackManager.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader-media.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-location.js`

**Tests and probes:**
- `composeApp/src/commonTest/kotlin/paige/navic/reader/WhispersyncTimelineParserTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinatorTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncPlaybackPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderProgressSyncTest.kt`
- `scripts/adb-reader-komikku-matrix.ps1`
- Existing DevTools probes under `scripts` or captured probe paths referenced by `docs/superpowers/specs/2026-06-18-whispersync-design.md`.

- [x] Add a failing coordinator or progress test for the next concrete playback-sync gap.
- [x] Implement pure model/coordinator changes before Android playback glue.
- [x] Validate page-to-audio and audio-to-reader direction separately in readerdev. Completed for the current book `3809` readerdev session with artifacts `captures/reader-smoke/whispersync-page-scoped-control-20260629-004034`, `captures/reader-smoke/whispersync-audio-follow-20260629-004817`, and `captures/reader-smoke/whispersync-char-offset-overlay-20260629-005355`.
- [x] Validate that playback-driven media-overlay follow does not steal explicit reader navigation. Completed with `captures/reader-smoke/whispersync-media-follow-defer-20260629-022114`.
- [ ] Validate exact companion progress reopen in readerdev.
- [ ] Commit only after host tests and readerdev evidence pass.

## Stage 6: Komikku Visual Parity Gate

**Purpose:** Improve layout fidelity only after blocker behavior is stable.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderChapterNavigator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/KomikkuIntegerSlider.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderTabletUi.android.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader-paper-surface.js` if introduced by the stage.
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `composeApp/src/androidMain/assets/reader/paper-textures/*`

**Tests:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderChromeStateTest.kt`

- [ ] Compare against Komikku source before redesigning a control.
- [ ] Add source-reading or screenshot-sensitive guards where practical.
- [ ] Implement one visual system at a time: rail proportions, settings density, textures, margins, or theme palette.
- [ ] Validate on emulator phone/fold/tablet dimensions before asking for human visual judgment.
- [ ] Commit each completed visual system.

## Stage 7: Release Candidate Gate

**Purpose:** Publish only when a coherent milestone is ready for user validation.

**Files and scripts:**
- `scripts/verify-android-release-version.ps1`
- `scripts/publish-github-release.ps1`
- GitHub Actions release workflow files if the release pipeline itself fails.

- [ ] Sync with the current GitHub default branch before release work.
- [ ] Resolve merge conflicts without reverting unrelated user work.
- [ ] Run `.\gradlew.bat --no-daemon :composeApp:testAndroid`.
- [ ] Run focused host suites for changed domains.
- [ ] Run `git diff --check`.
- [ ] Build the release APK through the repository's Android release path, not iOS.
- [ ] Verify the APK version with `scripts\verify-android-release-version.ps1`.
- [ ] Publish the GitHub release only after the release artifact exists and the milestone is worth device validation.
- [ ] Record the release tag and exact commit in the relevant spec validation log.

## Current First Focus

Start with Stage 0, then Stage 1. Stage 5 Whispersync work can continue only where it is pure model/coordinator work or where Stage 1 shell behavior has already been validated by host and readerdev evidence. Stage 6 visual polish waits until Stage 1 and the directly dependent Whispersync gate are not actively broken.
