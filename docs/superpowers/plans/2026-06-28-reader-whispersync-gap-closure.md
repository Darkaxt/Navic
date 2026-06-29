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

## Remaining Gap Queue

Work must proceed through coherent gap stages, not through isolated UI or runtime symptoms. If a stage turns out to be already green, record the evidence and move to the next stage; do not manufacture a patch just to show activity.

1. **Stage 2B: User-Driven Anx Interaction Validation** - close release-readiness gaps for selection actions, selection clear, annotation popup, external link prompt, history capsule, pull-up, and reader search through native UI or deterministic ADB/DevTools probes.
2. **Stage 5C: Whispersync Enjoyment Release Gate** - validate a real paired Bindery sidecar plus audiobook session end to end on the current release baseline: headset affordance, playback start/stop, page-to-audio seek, audio-to-text follow, char-offset highlight, and exact companion resume.
3. **Stage 6C.2: Settings Overlay Faithfulness** - finish the Komikku settings modal parity pass beyond the first density slice: compact tab readability, scroll gradients, control grouping, usable sliders/chips on phone/fold/tablet, and no duplicate entry points.
4. **Stage 6D.2: Paper/Texture Visual Strength** - make page texture and border degradation visible enough on release screenshots while keeping deterministic page identity and correct movement direction.
5. **Stage 6E.3: True Drag Curl Preview** - evaluate and, if viable, port the page-curl mockup's single/spread snapshot behavior for drag gestures only, after center taps and long-press content actions stay stable.

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

### Stage 3A: PDF And Fixed-Layout Interaction

Status: host and readerdev emulator pass complete; release-device/manual PDF feel still pending.

Scope:
- Make readerdev/local PDF launches use the same resolver/cache-backed publication resource path as remote OPDS resources, instead of asking the Android WebView to fetch local loopback PDFs directly.
- Prove PDF rendering through a DevTools `pdf-visible-page` probe that validates fixed-layout mode, PDF section shape, visible renderer dimensions, renderer index, and PDF renderer attributes.
- Make fixed-layout/PDF page turns call Foliate with a resolved object target (`goTo({ index })`) instead of a raw number that Foliate treats as an unresolved no-op.
- Suppress the synthetic shell-cover overlay for fixed-layout/PDF publications so page 1 is not consumed as a fake EPUB-style cover before the first real PDF turn.
- Tighten the PDF matrix so tap/drag next must land on renderer index `1`, and tap/drag previous must return to renderer index `0`.

Guards and evidence:
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.androidFixedLayoutPageTurnsUseFoliateResolvedIndexTarget` failed while `navic-reader-page-turns.js` still called `this.view.goTo(directFixedLayoutPageTarget)` (`tmp/codex-validation/stage3a-fixed-layout-goTo-red2.out.log`).
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.androidFixedLayoutPublicationsDoNotCreateSyntheticShellCoverOverlay` failed before `navic-reader.js` gated shell-cover creation with `shellCoverAllowed` for fixed-layout publications (`tmp/codex-validation/stage3a-fixed-layout-shell-cover-red.out.log`).
- GREEN/FOCUSED: the three focused `ReaderRuntimeAssetsTest` guards passed through the hidden no-console Gradle run, exit `0` (`tmp/codex-validation/stage3a-pdf-focused-green3-hidden.out.log`, `tmp/codex-validation/stage3a-pdf-focused-green3-hidden.exit.txt`).
- GREEN/JS: `node --check` passed for `navic-reader.js`, `navic-reader-page-turns.js`, and `tools/reader-harness/src/adb-webview-eval.mjs`.
- GREEN/SCRIPT-PARSE: `adb-reader-smoke.ps1` and `adb-reader-komikku-matrix.ps1` parsed cleanly after adding `pdf-visible-page` and `RequirePdfRendererIndex`.
- GREEN/READERDEV-PDF-MATRIX: `scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -OnlyPdfChecks -ArtifactRoot captures\reader-komikku-matrix\stage3a-pdf-local-index-asserted` passed all six rows.
- GREEN/PDF-BASELINE: `captures\reader-komikku-matrix\stage3a-pdf-local-index-asserted\pdf-baseline\reader-devtools-probe.json` reports fixed-layout `foliate-fxl`, `sectionCount=5`, renderer index `0`, and renderer rect `412x915`.
- GREEN/PDF-NEXT: `pdf-edge-tap-next` and `pdf-drag-next` post-action probes both report renderer index `1`; summaries show native tap and native drag paths respectively.
- GREEN/PDF-PREVIOUS: `pdf-edge-tap-previous` and `pdf-drag-previous` post-action probes both report renderer index `0`.
- GREEN/FULL-SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed after updating stale source-inspection guards (`tmp/codex-validation/stage3a-final-testAndroid-after-stale-guards.out.log`, exit `0`).
- GREEN/FINAL-CHECKS: touched JS files passed `node --check`; touched PowerShell smoke/matrix scripts parsed cleanly; `git diff --check` passed.

Required before closure:
- [x] Add a failing guard for the next PDF/fixed-layout behavior gap before touching runtime code.
- [x] Implement the adapter/runtime behavior behind `ReaderEngine`.
- [x] Validate with focused host tests, JS syntax checks, and emulator PDF matrix coverage.
- [x] Run final `:composeApp:testAndroid`, `git diff --check`, and commit the completed PDF slice.

## Stage 4: Whispersync API And Launch Gate

**Purpose:** Keep Navic aligned with the current Bindery schema and ensure ready pairs launch a paired reader session with the correct sidecar and audiobook manifest.

**Current completed slices:**
- Current Bindery audio resource/source-release fields, audiobook quality ordering, Aurral-first source routing support, and playback error notifier extraction were validated with `.\gradlew.bat --no-daemon :composeApp:testAndroid` and committed in `b1e20000`.
- Current Bindery resource-link provenance fields now survive OPDS/resource JSON parsing and propagate into readaloud track descriptors/extras: `kind`, `format`, `artifactType`, `bookFileId`, `size`, `deliveryPolicy`, `origin`, `version`, `sourceUrl`, `findingId`, and `findingHref`. The slice was guarded red-first in `BinderyRepositoryResourceJsonTest` and `ReadaloudModelsTest`, then validated with focused parser/readaloud tests, the Stage 4/Whispersync host batch, `:composeApp:testAndroid`, and `git diff --check`.
- Exact ready-pair launch and cover-badge gating are current-green against embedded sync pairs, `/sync` endpoint rows, missing audiobook rows, pending pairs, summary-only ready status, and ready pairs without `artifactHref`. The badge false-positive cases were added to `BinderyCatalogDisplayPolicyTest`, and the Stage 4 launch/badge batch passed with `BinderyBookVersionPolicyTest`, `BinderyCatalogDisplayPolicyTest.bookCardsExposeWhispersyncBadgeOnlyForReadyPairsWithArtifactHref`, and `ReaderWhispersyncLaunchPolicyTest`.

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

- [x] Add fixture coverage from the current Bindery schema before changing parser code.
- [x] Keep exact ready pairs launchable from embedded sync pairs and `/sync` endpoint responses.
- [x] Keep book cover headset badges gated by exact ready pairs with artifact href.
- [x] Run focused Bindery tests and `:composeApp:testAndroid`.
- [x] Commit API alignment only after all parser/launch tests pass.

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
- Whispersync companion progress now derives its artifact id from sidecar paths such as `/opds/books/3809/sync/8` when the reader route omits the explicit artifact id. This aligns progress persistence with reader launch identity and is guarded by `BinderyContinueShelfPolicyTest.readerProgressDerivesWhispersyncCompanionArtifactIdFromSidecarPathWhenMissing`; full exact companion reopen remains a separate readerdev validation gate.
- Exact Whispersync companion progress reopen is now readerdev-proven for production book `3809`: visible-range cue lookup writes exact `audioPositionMs=263360`, relaunch loads the readaloud playback plan with `startPositionMs=263360`, and `media-overlay-follow` relocation no longer overwrites the saved exact companion target. The slice is guarded by red-first controller/source tests and readerdev artifacts under `captures/reader-smoke/whispersync-companion-progress-after-step-target-save-20260629-current`.

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
- [x] Validate exact companion progress reopen in readerdev. Completed with `captures/reader-smoke/whispersync-companion-progress-after-step-target-save-20260629-current`, `tmp/codex-validation/stage5-companion-relaunch-after-step-target-save-20260629-current.out.log`, and preference evidence showing `audioPositionMs=263360`.
- [x] Commit only after host tests and readerdev evidence pass. Completed after red/green `ReaderWhispersyncCompanionProgressSourceTest`, `:composeApp:testAndroid`, `node --check tools/reader-harness/src/adb-webview-eval.mjs`, `git diff --check`, and the readerdev reopen gate.

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

### Stage 6A: EPUB Typography And Viewport Layout

Status: host/harness and dirty-readerdev emulator pass complete; release-device visual judgment still pending.

Scope:
- Make the adaptive EPUB page box use the full reader viewport on phone, fold, and tablet profiles.
- Leave folio margins to Anx/Foliate-style renderer attributes: `gap`, `top-margin`, and `bottom-margin`.
- Stop the legacy Navic `marginPercent` setting from shrinking the page box or applying body `margin-inline`.
- Remove the duplicate legacy `Margins` row from reader settings, Settings > Ebooks, and settings search so the UI exposes only Side margin, Top margin, and Bottom margin.
- Keep `columnThreshold` as a spread-splitting threshold instead of a hard maximum page width.

Guards and evidence:
- RED/HOST-FIRST: `FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract` failed before the runtime/paginator dropped legacy `marginPercent` page-box math and stopped treating `columnThreshold` as max width.
- RED/HOST-FIRST: `ReaderRuntimeSettingsBridgeTest.androidReaderUsesAnxMarginAttributesInsteadOfLegacyBodyMargins` failed before the legacy margin UI/search entries were removed.
- GREEN/FOCUSED: `.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.FoliateAnxParityTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderUsesAnxMarginAttributesInsteadOfLegacyBodyMargins --rerun-tasks --console=plain` passed via `tmp/codex-validation/stage6a-focused-rerun.out.log`.
- GREEN/HARNESS: `node tools\reader-harness\src\run-reader-harness.mjs --mode adaptive-page-box-logic` passed after updating the harness to enforce full-viewport page boxes and legacy margin suppression.
- GREEN/JS: `node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js`, `node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\paginator.js`, and `node --check tools\reader-harness\src\run-reader-harness.mjs` passed.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed via `tmp/codex-validation/stage6a-testAndroid.out.log`.
- GREEN/READERDEV-INSTALL: `scripts\install-reader-dev.ps1` installed and launched `darkaxt.navic.readerdev`, reached `publicationReady`, and loaded production book `3809` through `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`.
- GREEN/EMULATOR-TABLET: Tab S9 Ultra portrait screenshot/probe captured under `captures\reader-smoke\stage6a-layout-emulator\tab-s9-ultra-portrait`; `page-box.json` reported renderer `maxInlineSize=1232px`, `maxBlockSize=1974px`, `maxColumnCount=1`, `topMargin=90px`, `bottomMargin=50px`, and `documentToViewportWidthRatio=0.94`.
- GREEN/EMULATOR-FOLD: Z Fold7 inner screenshot/probe captured under `captures\reader-smoke\stage6a-layout-emulator\zfold7-inner`; `page-box-text.json` reported renderer `maxInlineSize=856px`, `maxBlockSize=950px`, `maxColumnCount=1`, and `documentToViewportWidthRatio=0.94`.
- GREEN/EMULATOR-PHONE: Z Fold7 cover screenshot/probe captured under `captures\reader-smoke\stage6a-layout-emulator\zfold7-cover`; `page-box-text.json` reported renderer `maxInlineSize=410px`, `maxBlockSize=956px`, `maxColumnCount=1`, and `documentToViewportWidthRatio=0.939`.
- GREEN/WHITESPACE: `git diff --check` passed before and after documentation updates.

Remaining:
- Do not publish a public release for Stage 6A until a coherent next release candidate is worth real-device visual judgment. Stage 6A closes the page-box/margin model, not the remaining settings-density, texture-strength, progress-rail, or page-curl gaps.

### Stage 6B: Komikku Rail Fidelity

Status: complete; automation-green, no production rail code changed.

Scope:
- Keep the rail structurally faithful to Komikku's `ChapterNavigator`: filled previous/next chapter buttons, weighted chapter-local slider capsule, current/total page labels, haptics while dragging, rotated vertical mode, and no custom rail touch layer.
- Prove the rail's behavior from the actual native UI layer, not only through bridge/source assertions.
- Verify first and last page endpoints, current-chapter endpoint mapping, previous/next chapter buttons at chapter boundaries, and slider interaction on the visible native `Chapter page slider`.
- Treat a passing endpoint probe as evidence, not as a reason to change rail proportions. Rail height or width changes require source-backed Komikku divergence evidence.

Guards and evidence:
- GREEN/SOURCE-COMPARISON: `ReaderChapterNavigator.kt` currently matches the Komikku reference shape in `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt`: weighted slider capsule, `FilledIconButton` previous/next controls, rotated vertical slider, page labels, and haptic drag feedback.
- GREEN/EMULATOR-CURRENT-ENDPOINTS: `node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe chapter-progress-current-endpoints` produced `captures\reader-smoke\stage6b-rail-emulator\current-endpoints.json`; current chapter `OEBPS/xhtml/chapter17.xhtml` started at `25 / 38`, progress `0` reported `chapterPageIndex=0`, and progress `1` reported `chapterPageIndex=37` with `chapterPageCount=38`.
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeLocationSnapshotWithoutNavigation` failed before the DevTools helper exposed a non-mutating `location-snapshot` probe. The red log is `tmp/codex-validation/stage6b-location-snapshot-red-full.out.log`.
- GREEN/HOST: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed after adding `location-snapshot`, post-action probe support, and smoke-summary strict-mode fixes. The green log is `tmp/codex-validation/stage6b-location-snapshot-green.out.log`.
- GREEN/JS: `node --check tools\reader-harness\src\adb-webview-eval.mjs` passed after adding `runLocationSnapshotProbe`.
- GREEN/EMULATOR-NATIVE-RAIL: `scripts\adb-reader-smoke.ps1` ran against `darkaxt.navic.readerdev` on `emulator-5554` with `-ReaderDevtoolsProbe location-snapshot`, `-PostProbeAction 'tapDescFraction:Chapter page slider,0.75,0.5,1500'`, and `-PostActionReaderDevtoolsProbe location-snapshot`. Artifacts are under `captures\reader-smoke\stage6b-rail-emulator\ui-rail-snapshot-gate`; the pre-action snapshot was `chapterPageIndex=9` / `chapterPageCount=38`, and the post-action snapshot was `chapterPageIndex=29` / `chapterPageCount=38`.
- GREEN/SMOKE-SUMMARY: `captures\reader-smoke\stage6b-rail-emulator\ui-rail-snapshot-gate\reader-diagnostics-summary.txt` records `bridgeEvent:locationChanged=True`; `logcat-reader.log` records `Dispatching reader engine command: goToChapterProgress(OEBPS/xhtml/chapter17.xhtml, 0.7567567567567568)` and `location-page-model reason=chapter-progress-seek ... chapter=29/38`.
- GREEN/UI-EVIDENCE: `captures\reader-smoke\stage6b-rail-emulator\ui-rail-snapshot-gate\window.xml` contains the native `Chapter page slider`, `Previous chapter`, and `Next chapter` controls, confirming the probe exercised the Komikku-style native rail rather than a hidden bridge-only path.
- GREEN/FINAL-SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed from hidden process output `tmp/codex-validation/stage6b-final-testAndroid.out.log`.
- GREEN/FINAL-DIFF: `git diff --check` passed before final suite execution and must pass again immediately before commit.

Required before closure:
- [x] Add or reuse an emulator/UI gate that taps the visible `Chapter page slider` and previous/next chapter buttons, then captures a post-action location snapshot proving the native UI path reaches the intended chapter-local page.
- [x] Run the UI gate against `darkaxt.navic.readerdev` on `emulator-5554`.
- [x] If the UI gate fails, add a failing host/source guard for the exact cause before changing production code. The UI gate passed, so no production rail patch was made.
- [x] If the UI gate passes, record Stage 6B as automation-green and move to Stage 3A PDF/fixed-layout without changing production rail code.
- [x] Run final `git diff --check` and commit the Stage 6B evidence or fix.

### Stage 6C: Settings Overlay Density And Scroll Treatment

Status: first source-backed density slice complete; emulator/physical visual judgment still pending.

Scope:
- Keep the reader settings overlay faithful to Komikku's `ReaderSettingsDialog` / `SettingsItems` primitives.
- Remove Navic-only density that makes wrapped chip rows taller than the reference and easier to clip in the bounded modal.
- Preserve the existing bounded overlay, compact tabs, edge-fade scroll treatment, and no-footer dialog behavior.

Guards and evidence:
- RED/HOST-FIRST: `ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndReferenceTabLabels` failed while Navic's `SettingsChipRow` still added `verticalArrangement = Arrangement.spacedBy(6.dp)` between wrapped chips (`tmp/codex-validation/stage6c-settings-chip-gap-red.out.log`, exit `1`).
- GREEN/HOST-FOCUSED: the same guard passed after removing the extra chip-row vertical spacing (`tmp/codex-validation/stage6c-settings-chip-gap-green.out.log`, exit `0`).
- GREEN/HOST-CHROME: full `ReaderRuntimeCommonChromeTest` passed (`tmp/codex-validation/stage6c-reader-runtime-common-chrome-green.out.log`, exit `0`).
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed (`tmp/codex-validation/stage6c-final-testAndroid.out.log`, exit `0`).
- GREEN/WHITESPACE: `git diff --check` passed after the Stage 6C edits.
- GREEN/EMULATOR-BASELINE: installed readerdev `v1.0.11-theta14` passed the prepared Komikku matrix on `emulator-5554` at `captures\reader-komikku-matrix\theta14-stage-gate-20260629-122023`; `reader-matrix-failures.txt` reported `No matrix failures.` The matrix covered native cover, cover tap/drag, normal center tap, native long press, edge next/previous, drag next/previous, and texture next/previous walks. This is base input evidence, not visual parity closure.

Required before closure:
- [x] Compare the concrete setting primitive against Komikku before changing Navic.
- [x] Add a failing source guard for the non-faithful density behavior.
- [x] Implement the smallest settings primitive change.
- [x] Run focused guard, full chrome host class, `:composeApp:testAndroid`, and `git diff --check`.
- [x] Commit the Stage 6C density slice.

Remaining:
- This slice reduces one concrete source-backed density divergence. It does not complete settings visual parity; phone/fold/tablet screenshots still need to judge surface palette, row grouping, tab comfort, and scroll feel.

### Stage 6D: Paper/Texture Visual System

Status: source/harness complete for the current slice; release-device visual judgment still pending.

Scope:
- Keep paper texture and page-border degradation as one top-level reader-window surface, not per-document/pseudo-element stacking.
- Keep texture identity deterministic per rendered page locator while tolerating chapter-local page numbering.
- Keep texture movement counter-aligned with renderer movement through scroll, page turns, and the Hobbit frontmatter -> Author's Note boundary.
- Prevent transient body-less EPUB documents from throwing during Foliate pagination, because those console errors abort the texture page-turn harness and can destabilize real page turns.

Guards and evidence:
- RED/HARNESS-FIRST: `epub-texture-page-turns` initially failed because the harness still assumed global monotonic `pageIndex`; the current reader reports chapter-local page positions, so boundary page changes can reset to page `0`.
- RED/HARNESS-FIRST: after the harness waited on `(href, pageIndex, cfi)` identity, `epub-texture-page-turns` exposed a real browser console error: `Failed to execute 'getComputedStyle' on 'Window': parameter 1 is not of type 'Element'.`
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.androidPaginatorDoesNotThrowWhenBodyIsTemporarilyUnavailable` failed before `vendor/foliate-js/paginator.js` had a body-safe `documentStyleRoot(doc)` fallback.
- GREEN/HOST-FOCUSED: `ReaderRuntimeAssetsTest.androidPaginatorDoesNotThrowWhenBodyIsTemporarilyUnavailable` passed after the paginator stopped calling `getComputedStyle(doc.body)` directly.
- GREEN/HOST-PAPER: `ReaderRuntimePaperSurfaceTest` plus the new paginator guard passed, confirming the existing surface texture, border overlay, per-page texture identity, and texture-direction source guards still hold.
- GREEN/JS: `node --check` passed for `vendor/foliate-js/paginator.js` and `tools/reader-harness/src/run-reader-harness.mjs`.
- GREEN/HARNESS: texture offset logic, `epub-texture-scroll`, `epub-texture-page-turns` on production book `3809`, and Hobbit `epub-texture-frontmatter-transition` all passed.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed from hidden-process output `tmp/codex-validation/stage6d-full-testAndroid.out.log`.
- GREEN/WHITESPACE: `git diff --check` passed.

Required before closure:
- [x] Reproduce a concrete Stage 6D failure through the texture harness.
- [x] Fix the stale validation assumption without changing product pagination semantics back to global page numbers.
- [x] Add a host/source guard for the runtime crash layer exposed by the harness.
- [x] Patch the paginator to tolerate body-less transient documents.
- [x] Run focused host tests, texture harnesses, JS syntax checks, full `:composeApp:testAndroid`, and `git diff --check`.

Remaining:
- This is source and browser-harness evidence. Physical release validation is still needed for perceived texture strength, edge degradation visibility, and drag feel on the user's phone/tablet.
- No opacity or asset tuning was made in this slice because the packaged textures and overlays already pass visibility/statistical guards; if a release-device screenshot still looks too subtle, the next slice should start with screenshot-sensitive evidence rather than changing constants blindly.

### Stage 6E: Page Drag Preview And Curl

Status: first drag-preview stability slice complete; first curl-metrics slice complete; full curl visual parity still pending.

Scope:
- Stabilize the existing native drag preview before adding curl visuals from `D:\Downloads\Trash\navic_page_curl_toggle_mockup_single_clipped.html`.
- Keep normal tap ownership unchanged; this slice touches drag preview ordering only.
- Ensure the adjacent-page underlay is mounted before the renderer is moved during a boundary drag, because moving first can make the boundary probe go false and remove the preview layer.

Guards and evidence:
- RED/HARNESS-FIRST: `node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-preview-underlay --fixture tmp\reader-live\book-3809-file-426.epub` failed with `layer=false iframe=false` and trace entries `page-drag-preview:underlay-waiting` / `boundary-preview-loading`.
- ROOT CAUSE: `previewPageDrag(command)` moved the current renderer with `renderer.scrollBy(-incrementalDelta.x, -incrementalDelta.y)` before calling `updatePageDragPreviewLayer(...)`; after that movement, the boundary test could stop matching and remove the layer.
- RED/HOST-FIRST: `ReaderRuntimePaperSurfaceTest.androidReaderKeepsCurrentPageMovingWhileBoundaryPreviewLoads` failed before the source guard required `updatePageDragPreviewLayer(...)` to appear before the renderer scroll.
- GREEN/HOST-FOCUSED: the same guard passed after reordering the preview update before renderer movement.
- GREEN/JS: `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js` passed.
- GREEN/HARNESS: `epub-native-drag-preview-underlay` passed against the production book `3809` fixture and wrote `tools/reader-harness/output/epub-native-drag-preview-underlay.json`.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed from hidden-process output `tmp/codex-validation/stage6e-full-testAndroid.out.log`.

Required before closure:
- [x] Reproduce the boundary drag preview underlay failure with the browser harness.
- [x] Add a failing host/source guard for the exact ordering bug.
- [x] Reorder drag preview mounting ahead of renderer movement without changing tap handling.
- [x] Run focused host tests, JS syntax check, EPUB drag-preview harness, full `:composeApp:testAndroid`, and `git diff --check`.

Remaining:
- This slice only fixes the adjacent-page underlay disappearing during boundary drag. The page-curl mockup port remains pending and must start with a failing guard proving curl visuals are drag-only and do not run on taps, releases without drag, or native menu toggles.

#### Stage 6E.2: Drag-Only Curl Metrics

Scope:
- Port the first safe subset of `D:\Downloads\Trash\navic_page_curl_toggle_mockup_single_clipped.html`: non-linear progress, sinusoidal curl width/shadow, and drag-direction angle.
- Attach curl state to the existing clipped adjacent-page underlay instead of adding another touch/overlay owner.
- Keep release/cancel behavior clearing the preview layer so curl state cannot leak into taps or menu toggles.

Guards and evidence:
- RED/HOST-FIRST: `ReaderRuntimePaperSurfaceTest.androidReaderPortsCurlMetricsToDragPreviewLayerOnly` failed before the runtime exposed mockup-style curl metrics or CSS variables.
- GREEN/HOST-FOCUSED: the same guard passed after adding `readerPageDragCurlMetrics(...)` and `applyPageDragCurlMetrics(...)`.
- GREEN/HARNESS: `epub-native-drag-preview-underlay` now asserts real runtime curl state during an EPUB drag: `curl=true`, `curlProgress=0.359`, `curlAngle=-22.66deg`, `curlWidth=48.5px`, and `curlTransform=perspective(1800px) rotateY(-22.66deg)`.
- GREEN/HOST-SUITE: `ReaderRuntimePaperSurfaceTest` and `ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack` passed.
- GREEN/JS: `node --check` passed for `navic-reader-page-turns.js` and `tools/reader-harness/src/run-reader-harness.mjs`.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed from hidden-process output `tmp/codex-validation/stage6e-curl-full-testAndroid.out.log`.

Required before closure:
- [x] Add a failing host/source guard for drag-only curl state.
- [x] Implement mockup-derived curl metrics on the existing underlay path.
- [x] Extend the browser harness to verify real curl state during an EPUB drag.
- [x] Run focused host tests, JS syntax checks, browser harness, full `:composeApp:testAndroid`, and `git diff --check`.

Remaining:
- This is not yet the full mockup's dual/single-page snapshot animation. It gives the drag preview a curl-derived transform/shadow while preserving current page ownership. A later slice should only attempt true snapshot sheet animation after a guard proves no regression to center taps, menu toggles, cover behavior, or adjacent-page loading.

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

### Stage 7A: Theta15 Staged Release Candidate

Status: complete; GitHub release published.

Scope:
- Package the completed Stage 3A, Stage 4, Stage 5, Stage 6A, Stage 6B, Stage 6C, Stage 6D, and Stage 6E slices as one coherent release candidate instead of publishing isolated microfixes.
- Use `v1.0.11-theta15` / `versionCode=443`, because `v1.0.11-theta14` already points at an older commit.
- Build only the Android release artifact. Do not invoke iOS packaging.

Preflight evidence:
- GREEN/SYNC: after `git fetch --all --prune`, `git rev-list --left-right --count fork/master...HEAD` reported `0 36`, so this branch contains the current `fork/master` before release work.
- RED/VERSION-FIRST: `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta15` failed while `androidApp/build.gradle.kts` still declared `v1.0.11-theta14`.
- GREEN/VERSION: after the release identity bump, `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta15` passed.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed on the theta15 release identity.
- GREEN/HOST-FOCUSED: `.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests ... --console=plain` passed for the reader shell, runtime assets, paper surface, navigation flow, Komikku reset, Anx parity, PDF parity, font-source parity, and Bindery source-policy guards.
- RED/LOCAL-RELEASE-BUILD: `.\gradlew.bat --no-daemon :androidApp:assembleRelease --console=plain` stopped at the repository signing gate because local `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`, and `SIGNING_STORE_FILE` are not configured. The release APK must therefore be produced by the GitHub Actions Android release path with its configured signing secrets.
- GREEN/GITHUB-RELEASE: GitHub Actions run `28363555884` completed successfully for tag `v1.0.11-theta15`; `Build Android APK` succeeded, iOS jobs were skipped, and the release was published at `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-theta15` with asset `Navic.apk`.

Required before closure:
- [x] Run final `git diff --check`.
- [x] Commit the theta15 release identity and Stage 7A evidence.
- [x] Create and push the `v1.0.11-theta15` tag after the commit.
- [x] Trigger/watch the GitHub Actions Android release path with `scripts\publish-github-release.ps1`.
- [x] Record the published release URL/assets once GitHub confirms the artifact exists.

## Current First Focus

Use `v1.0.11-theta15` as the current device-validation baseline. The next implementation stage should be selected from the remaining queue only after theta15 feedback identifies the next highest-impact blocker.

## Stage 8: Release Validation Baseline Gate

**Purpose:** Convert the published release from "available" into an actionable validation baseline, then route any failure into the next file-scoped implementation plan.

**Files and scripts:**
- `scripts/adb-reader-smoke.ps1` - release package smoke runner, screenshots, logs, DevTools probes, and APK install support.
- `scripts/adb-reader-komikku-matrix.ps1` - matrix runner for reader shell, cover, drag, texture, PDF, and release-package checks.
- `scripts/install-reader-dev.ps1` - readerdev-only launcher for deep Bindery route setup; do not confuse this with release APK validation.
- `tools/reader-harness/src/adb-webview-eval.mjs` - deterministic DevTools probes for runtime state, page boxes, location snapshots, PDF visibility, and Whispersync checks.
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md` - compact validation results.
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md` - update only when release evidence changes the operating contract.
- `docs/superpowers/specs/2026-06-18-whispersync-design.md` - update only when release evidence changes Whispersync status.

**Validation target split:**
- The release package is `darkaxt.navic` and proves published APK install/version/app-shell behavior.
- The readerdev package is `darkaxt.navic.readerdev` and remains the correct path for deterministic seeded Bindery reader sessions when the release package lacks login/data.
- A release result must not be claimed for a deep reader/Whispersync behavior unless the probe actually ran against `darkaxt.navic`; readerdev evidence may only prove implementation/runtime behavior.

### Stage 8A: Theta15 Release Validation Baseline

Status: complete for release-package baseline; deep reader/Whispersync release validation remains blocked by missing release login/data on the emulator.

Scope:
- Download or reuse the published `v1.0.11-theta15` `Navic.apk` from GitHub release assets.
- Install it on the connected emulator as `darkaxt.navic`.
- Verify `versionName=v1.0.11-theta15`, `versionCode=443`, foreground launch, and screenshot capture.
- Run the release package through every deterministic gate that does not require pre-existing user login/data.
- Run readerdev probes for any seeded-reader behavior that release package state cannot reach, and label those results as readerdev implementation evidence rather than release evidence.
- Record open gaps as the next Stage 2B/5C/6C.2/6D.2/6E.3 plan input.

Commands:

```powershell
gh release download v1.0.11-theta15 --repo Darkaxt/Navic --pattern Navic.apk --dir releases\v1.0.11-theta15 --clobber
adb devices
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ApkPath releases\v1.0.11-theta15\Navic.apk -ExpectedVersionName v1.0.11-theta15 -ArtifactDir captures\reader-smoke\theta15-release-install -CaptureReaderDiagnostics
adb -s emulator-5554 shell pm grant darkaxt.navic android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell input keyevent BACK
adb -s emulator-5554 shell monkey -p darkaxt.navic 1
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -ArtifactDir captures\reader-smoke\theta15-release-focused-after-focus-guard -CaptureReaderDiagnostics -NoLaunch
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -ArtifactRoot captures\reader-komikku-matrix\theta15-release-baseline -NoLaunch -ContinueOnFailure
```

Results:
- GREEN/RELEASE-INSTALL: published `v1.0.11-theta15` `Navic.apk` installed on `emulator-5554` as `darkaxt.navic`; `package-version.txt` reports `versionCode=443`, `versionName=v1.0.11-theta15`, and `lastUpdateTime=2026-06-29 13:12:28`.
- GREEN/FOCUS-GUARD: `adb-reader-smoke.ps1` now fails before screenshot/probe capture if `mCurrentFocus` does not belong to the requested package. This prevents the previously observed false release evidence where `darkaxt.navic` was version-checked but `darkaxt.navic.readerdev` was foreground.
- GREEN/RELEASE-SHELL: corrected release smoke artifacts are under `captures\reader-smoke\theta15-release-focused-after-focus-guard`; `focused-window.txt` confirms `darkaxt.navic/paige.navic.androidApp.MainActivity`.
- BLOCKED/RELEASE-READER: the release package on the emulator is not logged in and lands on the Navidrome login form (`Log in`, `Instance URL`, `Username`, `Password`). Reader shell, EPUB/PDF, selection, search, style, and Whispersync behavior cannot be claimed as release evidence from this emulator state.

Closure:
- [x] Install the published `Navic.apk` and prove the installed release version.
- [x] Capture launch/shell evidence for `darkaxt.navic`.
- [x] Run deterministic release-package checks that do not require login/data.
- [x] Record that deep release reader/Whispersync checks require release login/data; keep readerdev implementation evidence separate.
- [x] Append concise validation evidence and open gaps to the validation log.
- [x] Commit the Stage 8A plan/evidence when the stage is complete.
