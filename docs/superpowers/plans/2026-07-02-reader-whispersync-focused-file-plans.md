# Reader Whispersync Focused File Plans Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the Komikku reader and Whispersync work into file-owned, testable slices that can be completed, committed, and validated with debug/readerdev builds first, without publishing public APKs for microfixes.

**Architecture:** Komikku owns native reader shell, tap ownership, chrome, rail, and settings. Anx/Foliate owns EPUB/PDF behavior, locators, selection, annotations, visible ranges, and media-overlay semantics. Bindery owns OPDS/API data, sidecars, resources, progress, generated cover assets, and audiobook identity.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android WebView, Foliate/PDF.js reader assets, Gradle host/common tests, readerdev emulator, ADB probes, Playwright reader harness, Bindery OPDS/API schema.

---

## Release Rule

- Debug iteration means local `darkaxt.navic.readerdev` or another debuggable APK installed for emulator/device validation.
- Public release means GitHub tag/prerelease/APK upload through `scripts/publish-github-release.ps1`.
- Do not call emulator/debug artifacts "releases" in implementation notes. They are debug builds, readerdev installs, or validation artifacts.
- Do not create a public release to collect routine user feedback. User/phone testing happens only after a coherent feature or major fix is already deployed and validated as a debug/readerdev candidate.
- Plans A-D must use debug/readerdev APKs only. They may build, install, probe, commit, and push source changes, but they must not create GitHub tags, prereleases, or public APK assets.
- Public release is only allowed in Plan E, after a coherent feature or major fix has passed its file-owned plan gates and is ready for physical-device acceptance.
- Do not publish for isolated green probes, diagnostics, one-file visual tweaks, or partial fixes. A public release is a final candidate for a deployed feature/fix, not a normal iteration mechanism.
- 2026-07-02 explicit user rule: use debug/readerdev builds for emulator iteration. Generate final public releases only after a new feature or fix has been fully implemented, deployed in debug/readerdev, validated through its gate, and is ready as the next public candidate.
- If a change still needs emulator iteration, physical-device judgement, or known follow-up patches before it is useful, it is not release-worthy.

## Focused Plan A: Reader Surface Fidelity

**Purpose:** Fix the remaining visual/page-turn surface issues: tablet landscape spread, page-number font parity, one full-surface paper owner, visible border overlays, and standard/curl isolation.

**Main files:**
- `composeApp/src/androidMain/assets/reader/index.html`
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-typography.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-location.js`
- `tools/reader-harness/src/run-reader-harness.mjs`
- `tools/reader-harness/src/adb-webview-eval.mjs`

**Tests and guards:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeNavigationFlowTest.kt`

- [x] **A1: Add or tighten failing source/harness guards**
  - Guard tablet landscape against one-word/min-content columns using the existing `adaptive-page-box-logic` and `page-box` probes.
  - Guard standard drag so preview uses the current rendered page and release commits exactly one page.
  - Guard page-number font so the root organic page number resolves the selected reader font before publisher font sampling.
  - Guard paper and border layers so paper texture is not applied per EPUB document element and border overlays stay visible.

- [x] **A2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest
    ```
  - Expected: the new or tightened guard fails before production runtime changes, unless the behavior is already correctly implemented.

- [x] **A3: Implement runtime changes**
  - Keep Foliate/Anx as the layout core.
  - Keep standard drag separate from optional curl preview.
  - Keep root surface texture ownership in the reader root, not in EPUB documents.
  - Let page textures move with the page preview/commit path instead of swapping after text settles.
  - Make tablet landscape use a readable spread/page box, not a narrow centered column.

- [x] **A4: Validate JS and browser harness**
  - Run:
    ```powershell
    node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
    node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
    node --check composeApp\src\androidMain\assets\reader\navic-reader-viewport.js
    node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
    node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination.js
    node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
    node --check composeApp\src\androidMain\assets\reader\navic-reader-location.js
    node --check tools\reader-harness\src\run-reader-harness.mjs
    node --check tools\reader-harness\src\adb-webview-eval.mjs
    node tools\reader-harness\src\run-reader-harness.mjs --mode adaptive-page-box-logic --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-single-commit --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-standard-no-curl --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    ```
  - Expected: syntax checks pass; harness rejects collapsed landscape and passes one-page drag commit plus texture boundary movement.

- [x] **A5: Validate in readerdev**
  - Run readerdev install/open with production book `3809`, ebook file `426`, sidecar `/opds/books/3809/sync/8`, audiobook `34`, audiobook file `633`.
  - Run `adb-reader-smoke.ps1` probes: `page-box`, `texture-slots`, `page-number-font`, `native-drag-preview-texture`.
  - Expected: `publicationReady`, readable page box, page-number font parity, moving texture slots, no reader console errors.

- [x] **A6: Commit and audit**
  - Run focused Gradle host test from A2 and `git diff --check`.
  - Commit message: `Stabilize reader surface fidelity`.
  - Audit docs for remaining `landscape`, `texture`, `page-number`, `curl`, and `drag` references; every remaining item must be physical acceptance or a named later slice.

Plan A status on 2026-07-02:
- The current branch already contained the tightened source and harness guards for landscape page boxes, standard drag isolation, page-number font parity, and root-owned paper/border layers.
- Focused host tests passed from hidden Gradle log `artifacts\gradle\plan-a-reader-surface-host\gradle-20260702-031313.out.log`.
- JS syntax checks passed for the Plan A reader modules and harness scripts.
- Browser harness passed `adaptive-page-box-logic`, `epub-native-drag-single-commit`, `epub-native-drag-standard-no-curl`, and `epub-texture-frontmatter-transition` against `tmp\reader-live\book-3809-file-426.epub` at tablet landscape viewport `1974x1232`.
- Readerdev APK `darkaxt.navic.readerdev` rebuilt and installed on `emulator-5554` as `v1.0.11-theta38`, reached `publicationReady`, and captured `captures\reader-dev\reader-dev-20260702-032712.png`.
- Readerdev WebView probes passed `page-box`, `texture-slots`, `page-number-font`, and `native-drag-preview-texture`; smoke artifacts are in `captures\reader-bridge-probes\plan-a-reader-surface-smoke-20260702-033240`.
- Plan A validation was recorded in commit `92cdedb5 Record reader surface debug validation`.
- No public release was created. This is local debug/readerdev evidence only.

## Focused Plan B: Native Komikku Shell And Controls

**Purpose:** Keep input/chrome faithful to Komikku: native short tap/drag ownership, content long press for WebView actions, deterministic cover/back behavior, practical chapter rail, and no duplicate settings controls.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderTabletUi.android.kt`

**Tests and guards:**
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeNavigationFlowTest.kt`

- [x] **B1: Add or tighten failing guards**
  - Back from readable EPUB content must return to native shell cover before leaving reader.
  - Center tap toggles chrome through native overlay, not WebView.
  - Short tap over images/links must not accidentally trigger content action or page skip.
  - Long press is the content-action path.
  - Progress rail uses controller navigation and reaches first/last chapter pages.
  - Settings must have one primary entry path, not duplicated bottom/top controls.

- [x] **B2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeCommonChromeTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest
    ```
  - Expected: supported KMP tasks pass; `testAndroid` covers the common/controller tests because this project does not expose filtered `:composeApp:test --tests ...`.

- [x] **B3: Implement shell/controller changes**
  - Route app-bar back and Android back through `ReaderController.onNavigateBack()`.
  - Keep native overlay above WebView for short tap, center tap, edge tap, and drag.
  - Let WebView receive long press and explicit content actions.
  - Keep settings dialog as overlay instead of resizing reader content.
  - Keep Whispersync headset as dim paper-integrated glyph, not circular Material chrome.

- [x] **B4: Validate readerdev shell behavior**
  - Run:
    ```powershell
    .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -PrepareReaderLaunch -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\focused-plan-b-native-shell
    .\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall -BookId 3809 -ResourceHref /api/v1/book/3809/file?bookFileId=426 -Kind ebook -Format epub -StartHref OEBPS/xhtml/chapter1.xhtml -WhispersyncSidecarUrl /opds/books/3809/sync/8 -WhispersyncArtifactId 8 -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -RequireReaderLaunch
    .\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe chapter-progress-current-endpoints -RequireNoReaderConsoleErrors -ArtifactDir captures\reader-bridge-probes\focused-plan-b-progress
    ```
  - Expected: center tap toggles chrome, drag next/previous works, rail endpoints work, no reader console errors.

- [x] **B5: Commit and audit**
  - Run both focused Gradle commands from B2 and `git diff --check`.
  - Commit message: `Align reader shell controls with Komikku ownership`.
  - Audit docs for `center tap`, `cover drag`, `progress rail`, `back`, `duplicate settings`, and `bottom toolbar`.

Plan B status on 2026-07-02:
- The current branch already contained the native Komikku shell ownership path; no production code changes were needed in this pass.
- Tightened `ReaderRuntimeAssetsTest` to guard exact native chapter rail endpoint calls instead of the older lossy `endpoint(href, 0/1)` assertions.
- Host gates passed:
  - `:composeApp:testAndroid` from `artifacts\gradle\plan-b-native-shell\test-android-20260702-035042.out.log`.
  - `:composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeCommonChromeTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest` from `artifacts\gradle\plan-b-native-shell\shell-controls-20260702-034929.out.log`.
  - Focused rail asset guard rerun from `artifacts\gradle\plan-b-native-shell\reader-assets-rail-20260702-034541.out.log`.
- Readerdev matrix passed 10/10 steps for `darkaxt.navic.readerdev` on `emulator-5554` using production book `3809`; artifacts: `captures\reader-komikku-matrix\focused-plan-b-native-shell-20260702-035337`.
- Deterministic chapter rail probe passed after relaunching directly into `OEBPS/xhtml/chapter1.xhtml`; artifacts: `captures\reader-bridge-probes\focused-plan-b-progress-chapter1-20260702-040037`.
- Rail probe evidence: Chapter 1 reported `chapterPageCount=9`; endpoint 0 resolved to `chapterPageIndex=0`; endpoint 1 resolved to `chapterPageIndex=8`.
- `git diff --check` passed.
- No public release was created. This remains local debug/readerdev validation only.

## Focused Plan C: Bindery API And Schema Parity

**Purpose:** Keep Navic aligned with `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\github-export\bindery\docs\navic-opds-api-schema.md`.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyDtoMapping.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyDtoJsonAccessors.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyUrlPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyMetadataCache.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyWhispersyncCoverOverlay.kt`

**Tests and guards:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/BinderyWhispersyncSchemaContractTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyBookSyncJsonTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryCatalogJsonTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryResourceJsonTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryProgressCacheTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogDisplayPolicyTest.kt`

- [x] **C1: Add or tighten schema drift guards**
  - Guard ready-pair rule: actionable only when exact pair has `status == ready` and non-empty `artifactHref`.
  - Guard current resource identity: `resourceKey`, current `href`, legacy `resourceHref`.
  - Guard current progress payload: `alias`, `resourceKey`, `href`, ms/seconds positions, `completed`, `updatedAt`.
  - Guard current audio quality sort: `qualityScore`, bitrate, sample rate, duration.
  - Guard generated cover asset shape: `type="readerShellCover"` and shell/fullscreen variant rels.

- [x] **C2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.BinderyWhispersyncSchemaContractTest
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
    ```
  - Expected: host schema guard plus common Android tests cover the current Bindery models, JSON parsing, resource identity, progress payload, version rows, and catalog badges.

- [x] **C3: Implement schema parity**
  - Keep route resolution in `BinderyUrlPolicy`.
  - Keep pair launch alive from `ebookBookFileId + audiobookBookFileId + artifactHref` even without preloaded audiobook row.
  - Do not compare display titles as audio identity.
  - Do not show catalog headset badge for summary-only `whispersyncStatus`.

- [x] **C4: Commit and audit**
  - Run C2 commands and `git diff --check`.
  - Commit message: `Track current Bindery Whispersync schema`.
  - Audit docs for `syncPairs`, `artifactHref`, `resourceKey`, `progress`, `qualityScore`, `readerShellCover`, and `navic-opds-api-schema`.

### Plan C Result - 2026-07-02

- Current production mapping already matches the 2026-06-29 Bindery schema for exact Whispersync pairs, resource/progress identity, audio quality metadata, and generated reader-shell cover assets.
- Tightened the documented Plan C Gradle gate to use the real supported KMP commands.
- `BinderyWhispersyncSchemaContractTest` passed from `artifacts\gradle\plan-c-bindery-schema\schema-host-20260702-041327.out.log`.
- `:composeApp:testAndroid` passed from `artifacts\gradle\plan-c-bindery-schema\test-android-20260702-041453.out.log`.
- No public release was created. This is local schema/debug validation only.

## Focused Plan D: Whispersync Runtime Enjoyment Path

**Purpose:** Prove the paired ebook/audiobook experience in readerdev: launch ready pair, fetch sidecar, resolve visible text to audio, tap headset to play/pause, follow audiobook position back to text, highlight character-offset cues, and save exact companion progress.

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/WhispersyncModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncPlaybackPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncLaunchPolicy.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader-media.js`
- `tools/reader-harness/src/adb-webview-eval.mjs`
- `scripts/adb-whispersync-enjoyment.ps1`

**Tests and guards:**
- `composeApp/src/commonTest/kotlin/paige/navic/reader/WhispersyncTimelineParserTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinatorTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncPlaybackPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderProgressSyncTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncLaunchPolicyTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderWhispersyncCompanionProgressSourceTest.kt`

- [x] **D1: Add or tighten pure-domain guards**
  - Visible range inside a sidecar segment resolves to expected audio resource, track index, and millisecond position.
  - Audio playback position resolves to one text overlay segment and rejects stale track/resource mismatches.
  - Already-visible media-overlay follow highlights in place and does not issue another reader relocation.
  - Companion progress stores exact resource identity, track index, and millisecond position.

- [x] **D2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest
    ```
  - Expected: current pure-domain and host source guards pass once the runtime path is already implemented. Add a narrower failing guard first only when a concrete Plan D behavior is missing.

- [x] **D3: Implement runtime path**
  - Use href, CFI/text range, resource key/href, track index, and millisecond position.
  - Do not use page number alone as Whispersync identity.
  - Page-scoped headset remains hidden when current visible page has no cue.
  - Headset tap routes through native paired audiobook playback, not WebView.
  - `media-overlay-follow` visible-range events must not trigger a new page-to-audio seek.
  - Character-offset cues highlight without fragment ids.

- [x] **D4: Validate readerdev enjoyment gate**
  - Run:
    ```powershell
    .\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -BookId 3809 -BookFileId 426 -WhispersyncSidecarUrl /opds/books/3809/sync/8 -WhispersyncArtifactId 8 -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -WhispersyncAudiobookTitle "Bastille vs. the Evil Librarians"
    .\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -NoBuild -NoInstall
    ```
  - Expected: page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes pass.

  2026-07-02 result: `scripts\adb-whispersync-enjoyment.ps1` rebuilt/installed `darkaxt.navic.readerdev` on `emulator-5554` using `bindery-debug.env` and production book `3809`; all four probes passed in `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260702-042528`.

- [x] **D5: Commit and audit**
  - Run D2, `:composeApp:testAndroidHost --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest`, JS syntax for touched JS, and `git diff --check`.
  - Commit message: `Complete readerdev Whispersync enjoyment path`.
  - Audit docs for `Whispersync`, `headset`, `audio-follow`, `companion progress`, `sidecar`, and `visible range`.

## Focused Plan E: Public Release Candidate Gate

**Purpose:** Publish one public APK only after the candidate is coherent and debug/readerdev evidence proves it is worth physical-device acceptance.

**Main files:**
- `androidApp/build.gradle.kts`
- `composeApp/build.gradle.kts`
- `scripts/publish-github-release.ps1`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`
- `docs/superpowers/plans/2026-07-02-reader-whispersync-focused-file-plans.md`

- [x] **E1: Verify Plans A-D are complete for the release scope**
  - Run:
    ```powershell
    rg -n "\[ \] \*\*Step [A-D]" docs\superpowers\plans\2026-07-02-reader-whispersync-focused-file-plans.md
    ```
  - Expected: no unchecked A-D steps remain for the release candidate. If a slice is excluded, add a dated exclusion note naming why it does not block this candidate.

- [x] **E2: Run full local validation**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost
    node --check tools\reader-harness\src\run-reader-harness.mjs
    node --check tools\reader-harness\src\adb-webview-eval.mjs
    git diff --check
    ```
  - Expected: all pass.

  2026-07-02 result: `:composeApp:testAndroid` passed from `artifacts\gradle\plan-e-release-gate\test-android-20260702-043312.out.log`; `:composeApp:testAndroidHost` passed from `artifacts\gradle\plan-e-release-gate\test-android-host-20260702-043116.out.log`; `node --check` passed for both reader harness scripts; `git diff --check` passed.

- [x] **E3: Run final readerdev acceptance matrix**
  - Run:
    ```powershell
    .\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -BookId 3809 -BookFileId 426 -WhispersyncSidecarUrl /opds/books/3809/sync/8 -WhispersyncArtifactId 8 -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -WhispersyncAudiobookTitle "Bastille vs. the Evil Librarians"
    .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -PrepareReaderLaunch -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\final-candidate-readerdev
    .\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -NoBuild -NoInstall
    ```
  - Expected: Komikku matrix and Whispersync enjoyment gate pass on the same candidate.

  2026-07-02 result: Komikku matrix passed all 10 rows in `captures\reader-komikku-matrix\final-candidate-readerdev-bastille-20260702-043832`; Whispersync enjoyment gate passed all four probes in `captures\reader-whispersync-enjoyment\final-candidate-readerdev-20260702-044132\stage5c3-whispersync-enjoyment-20260702-044133`.

- [x] **E4: Bump version and commit candidate**
  - Only after E2 and E3 pass, update Android version fields in `androidApp/build.gradle.kts`.
  - Commit message: `Prepare reader Whispersync release candidate`.
  - Push to GitHub.

  2026-07-02 result: Android release metadata was bumped to `v1.0.11-theta39` / versionCode `467`, committed as `04443f15 Prepare reader Whispersync release candidate`, and pushed to GitHub with tag `v1.0.11-theta39`.

- [x] **E5: Publish with explicit readiness note**
  - Run:
    ```powershell
    $versionLine = Select-String -Path androidApp\build.gradle.kts -Pattern 'versionName\s*=\s*"([^"]+)"' | Select-Object -First 1
    $tag = $versionLine.Matches[0].Groups[1].Value
    .\scripts\publish-github-release.ps1 -Tag $tag -Repo Darkaxt/Navic -Remote fork -AllowPublicRelease -ReleaseReadinessNote "Reader/Whispersync candidate passed common tests, Android host tests, readerdev Komikku matrix, and readerdev Whispersync enjoyment gate for production book 3809."
    ```
  - Expected: Android APK release is published, iOS jobs are skipped, and the tag matches the committed Android version.

  2026-07-02 result: GitHub Actions run `28559751405` completed successfully, Android APK build succeeded, iOS IPA jobs were skipped, and release `v1.0.11-theta39` was published with explicit Plan E readiness note.

- [x] **E6: Record release evidence**
  - Update validation log and plans with tag, GitHub run id, APK asset URL, Gradle commands, readerdev capture paths, and remaining physical-device acceptance items.
  - Commit message: `Record reader Whispersync release evidence`.

  2026-07-02 result: release evidence recorded for `v1.0.11-theta39`; remaining validation is physical-device/human acceptance only, not more public-release iteration.

## Completion Audit

- `2026-06-13-komikku-reader-port-design.md` active reader requirements map to Plan A, B, or E.
- `2026-06-18-whispersync-design.md` active Whispersync requirements map to Plan C, D, or E.
- Bindery API changes map to Plan C and the external schema file.
- Public release maps only to Plan E.
- Navidrome credentials are not required for ebook/audiobook implementation validation.
- Foliate/Anx core is not replaced by Turn.js or another flipbook core.
- Page number alone is never Whispersync identity.

## Focused Plan F: Debug-Only Page-Turn And Texture Stabilization

**Purpose:** Address the remaining reader drag/page-turn/texture defects without publishing public APKs for partial animation work. This plan exists because physical testing after `v1.0.11-theta39` still reported inconsistent texture motion, page preview/commit mismatch, curl-like preview leakage, and section-boundary page jumps.

**Release rule:** Plan F is debug/readerdev only until the full page-turn/texture behavior is coherent. Do not run `scripts/publish-github-release.ps1` for an isolated Plan F sub-slice. A public release is allowed only after all Plan F gates pass and the candidate is ready for physical-device acceptance as a major reader fix.

**Main files:**
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-location.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`
- `tools/reader-harness/src/run-reader-harness.mjs`
- `tools/reader-harness/src/reader-trace-assertions.mjs`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

**Initial red evidence:**
- `epub-native-drag-standard-no-curl` fails with browser console error `Cannot read properties of null (reading 'getBoundingClientRect')`, proving Standard mode can still enter an invalid preview/snapshot path.
- `epub-texture-frontmatter-transition` fails because `previousPage` from `Authorforeword.xhtml` stays at `5/90` while emitting repeated `relocate:ignored-unchanged-page-turn` events instead of returning before the section boundary.
- Existing passing guard `epub-native-drag-single-commit` proves an interior page can still advance by one page; do not break that while fixing boundary cases.

- [x] **F1: Lock Standard mode out of curl/snapshot-only paths**
  - Add or tighten a host/source guard that Standard mode never requires a curl snapshot iframe or missing preview layer geometry.
  - Use the failing `epub-native-drag-standard-no-curl` harness row as the red check.
  - Root-cause and patch the null `getBoundingClientRect` path rather than hiding console errors.
  - 2026-07-02 result: added `standardDragPreviewDoesNotConstructCurlSnapshots`, saw the expected RED failure in `artifacts\gradle\plan-f-page-turn\red-paper-surface.out.log`, then gated curl sheets/snapshot iframes behind explicit curl mode in `navic-reader-page-turns.js`.

- [x] **F2: Make page preview and committed page identity agree**
  - Add a harness assertion that the page shown during native drag preview is the same logical target page committed on release.
  - Preserve the already-passing one-page commit behavior from `epub-native-drag-single-commit`.
  - Do not switch the reader core to Turn.js or another flipbook core.
  - 2026-07-02 result: `epub-native-drag-single-commit` passed after the preview ownership split; standard mode now keeps the normal underneath preview frame instead of constructing curl-only snapshots.

- [x] **F3: Stabilize frontmatter/section-boundary reverse navigation**
  - Fix the repeated unchanged relocation loop where `previousPage` from `Authorforeword.xhtml` remains on `5/90`.
  - Require reverse movement across `dedication.xhtml` / `map.xhtml` / `Authorforeword.xhtml` to update page index, section key, and texture key together.
  - Use `epub-texture-frontmatter-transition` as the red/green guard.
  - 2026-07-02 result: no separate location patch was required in this slice; once Standard mode stopped sharing curl/snapshot-only state, `epub-texture-frontmatter-transition` passed against the production-derived fixture.

- [x] **F4: Keep paper texture motion tied to the moving page surface**
  - Texture motion must follow the same axis and direction as the text page during drag.
  - Texture updates after relocation must not flash, invert direction, or switch independently from the committed text page.
  - Border/gradient texture visibility can be adjusted only after motion identity is correct.
  - 2026-07-02 result: harness texture rows passed for the debug slice, but physical-device visual judgment remains open for intensity, border/shadow feel, and whether transitions feel natural under touch.

- [x] **F5: Validate debug-only**
  - Run focused harness rows:
    ```powershell
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-standard-no-curl --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-single-commit --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-page-turns --fixture tmp\reader-live\book-3809-file-426.epub --viewport-width 1974 --viewport-height 1232 --device-scale-factor 3
    ```
  - Run JS syntax and focused host tests for touched modules.
  - If emulator is available, install only `darkaxt.navic.readerdev` and run the relevant ADB matrix/probes.
  - Append results to the validation log.
  - 2026-07-02 result: all four harness rows, JS syntax, focused `ReaderRuntimePaperSurfaceTest`, and `git diff --check` passed for this sub-slice.

- [x] **F6: Commit, but do not publish**
  - Commit only after F1-F5 pass for the completed sub-slice.
  - Public release remains blocked until Plan F is coherent enough to be a major reader fix candidate.
  - 2026-07-02 result: this sub-slice is being committed as debug stabilization only. No GitHub release, tag, or public APK is created for it.

## Focused Plan G: Debug-Only Curl Guard Hardening

**Purpose:** Tighten stale page-turn source guards after Plan F changed the drag-preview factory from `ensurePageDragPreviewLayer()` to `ensurePageDragPreviewLayer({ curlEnabled = false } = {})`. This is validation hardening only; it does not justify a public APK.

**Release rule:** Plan G is debug/test-only. Do not publish a GitHub release, tag, or public APK for this guard update.

**Main files:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

- [x] **G1: Prove stale marker guards fail**
  - Add a strict source-slice helper so a missing marker fails instead of returning the whole source file.
  - Keep the old `function ensurePageDragPreviewLayer() {` marker for the red run.
  - 2026-07-02 result: the two targeted curl tests failed as expected with `Missing source marker for page drag preview layer factory`; log `artifacts\gradle\plan-g-curl-guard-red\red-curl-guards.out.log`.

- [x] **G2: Retarget guards to the current factory**
  - Change the curl sheet and snapshot guards to slice from `function ensurePageDragPreviewLayer({ curlEnabled = false } = {}) {`.
  - 2026-07-02 result: the same two focused tests passed; log `artifacts\gradle\plan-g-curl-guard-green\green-curl-guards.out.log`.

- [x] **G3: Run final lightweight validation**
  - Run `git diff --check`.
  - Commit this as a debug-only validation hardening change after the check passes.
  - 2026-07-02 result: `git diff --check` passed. This slice is ready to commit without creating a public release.

## Focused Plan H: Debug-Only Adjacent Preview/Commit Stabilization

**Purpose:** Close the remaining page-turn defect where late-section drags can preview one logical page but commit a different page, especially around adjacent section boundaries in production EPUBs. This continues Plan F and is not a separate release candidate.

**Release rule:** Plan H is debug/readerdev only. Do not create a GitHub tag, public prerelease, public APK asset, or `publish-github-release.ps1` workflow for any isolated Plan H sub-slice. A public release is allowed only after the page-turn/texture stack is coherent as a major reader fix, passes browser/host/readerdev gates, and is worth physical-device acceptance.

**Main files:**
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- `tools/reader-harness/src/run-reader-harness.mjs`
- `tools/reader-harness/src/reader-trace-assertions.mjs`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

**Current red evidence:**
- Production book `3809` at a later chapter target reproduced a section-boundary stale relocation loop in `epub-native-drag-single-commit`: the adjacent fallback repeatedly emitted unchanged same-section relocations instead of entering the next section.
- After routing the adjacent fallback through Foliate view navigation, the stale-loop blocker cleared, but the same production fixture still fails the preview/commit identity assertion: the release commits the expected numeric page while the preview iframe samples mismatched visible text. This makes the remaining task preview geometry, not public packaging.

- [x] **H1: Stop bypassing Foliate view navigation at adjacent section boundaries**
  - Add a host guard proving duplicate adjacent page-turn fallback uses `view.goTo(targetIndex)` and does not call raw `renderer.goTo({ index })`.
  - 2026-07-02 result: focused host test went RED, then GREEN after changing the fallback to use Foliate view navigation.

- [x] **H2: Make adjacent preview iframe geometry match the committed page**
  - Root-cause why the preview iframe maps the target scroll to different visible text than the live committed Foliate page.
  - Fix the preview document geometry/mapping instead of lowering overlap thresholds.
  - Rerun `epub-native-drag-single-commit` against production book `3809` at the late target that reproduced the mismatch.
  - 2026-07-02 result: the failing production target proved that synthetic preview iframes could not reliably reproduce Foliate's live paginator geometry. The fix now reuses the live Foliate strip for same-section drags, snaps the renderer on release, and suppresses the redundant native page command that follows the snap. The original red target and nearby targets 10/11/12 now pass the browser harness without publishing a public APK.

- [x] **H3: Recheck texture/page motion after preview identity is stable**
  - Only after H2 passes, rerun the texture transition harness rows and readerdev emulator probes.
  - Do not adjust texture intensity, border gradients, or visual polish while the page identity is still unstable.
  - 2026-07-02 result: `epub-texture-frontmatter-transition` first exposed that `previousPage()` could resolve before the duplicate adjacent fallback navigation posted the previous section. The page-turn transaction now waits for reflowable navigation and any adjacent fallback promise before settling. `epub-native-drag-standard-no-curl` then exposed that the live-strip shortcut was incorrectly active under Curl mode because it checked the wrong runtime field; the shortcut is now gated to `readerDragAnimationModeValue !== 'curl'`. Final browser rows passed for frontmatter transition, standard no-curl, texture page turns, and the original single-commit target. Readerdev active-page probes also passed for page box, texture slots, and next-direction texture motion under `captures\reader-bridge-probes\plan-h-h3-readerdev-skip-cover2`. This remains debug/browser/readerdev evidence only, not a public release trigger.

- [x] **H4: Commit debug-only evidence**
  - Append validation results to the reader validation log.
  - Commit source and docs for the completed debug slice.
  - Do not publish a public release for Plan H unless all page-turn/texture gates become coherent enough for a major reader-fix candidate.
  - 2026-07-02 result: Plan H source was committed as debug stabilization (`01cf91a7`, `b9a50fb2`) and readerdev evidence was recorded without creating a GitHub tag, public prerelease, public APK asset, or release workflow.

## Focused Plan I: Debug-Only Page-Curl / Turn.js Architecture Decision

**Purpose:** Decide whether the current curl animation is equivalent to Turn.js and whether importing Turn.js is a viable near-term replacement for the current page-turn stack.

**Release rule:** Plan I is documentation/architecture only. Do not create a debug APK, GitHub tag, public prerelease, public APK asset, or release workflow for this decision record.

**Main files and references:**
- `D:\Downloads\Trash\navic_page_curl_toggle_mockup_single_clipped.html`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- `tools/reader-harness/src/run-reader-harness.mjs`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- Turn.js references: `http://www.turnjs.com/`, `https://github.com/blasten/turn.js/`, and `https://www.turnjs.com/turnjs4-api-docs.pdf`

- [x] **I1: Compare current Navic curl against the local mockup**
  - Confirm whether Navic imports the mockup's sheet/snapshot model or delegates to a third-party flipbook library.
  - 2026-07-02 result: current Navic curl is a custom mockup-derived snapshot overlay. It creates `data-navic-page-curl-sheet` roles, snapshot iframes, CSS variables, and per-drag lifecycle state inside `navic-reader-page-turns.js`. It is not Turn.js.

- [x] **I2: Check Turn.js fit**
  - Check whether Turn.js can be a drop-in animation layer on top of Foliate's live renderer.
  - 2026-07-02 result: Turn.js is a jQuery flipbook plugin that owns a page container, page elements, single/double display mode, and its own page event lifecycle. It is better suited to pre-split magazine/catalog pages than to taking over Foliate's active EPUB renderer mid-drag.

- [x] **I3: Record the decision**
  - Do not import Turn.js for the current reader stabilization path.
  - Keep Standard mode as the default and keep curl as opt-in/experimental until page identity, texture motion, and landscape layout are stable.
  - If Turn.js is revisited later, treat it as a separate rendered-page adapter that consumes deterministic page snapshots, not as a patch inside `navic-reader-page-turns.js`.
  - 2026-07-02 result: decision recorded in the reader spec and validation log. No code or release artifact was created.

## Focused Plan J: Debug-Only Landscape Rotation Layout Guard

**Purpose:** Close the evidence gap behind the tablet landscape complaint where rotating a text EPUB can collapse the page into a narrow centered column. Direct landscape loads already pass the math and CSS smoke harness; this plan verifies the harder path: portrait load first, then runtime viewport rotation/reflow.

**Release rule:** Plan J is debug/harness only. Do not create a GitHub tag, public prerelease, public APK asset, or release workflow for this guard or any isolated layout tweak.

**Main files:**
- `tools/reader-harness/src/run-reader-harness.mjs`
- `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-typography.js`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeNavigationFlowTest.kt`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

**Current evidence:**
- `adaptive-page-box-logic` passes for a direct `1974x1232` landscape viewport.
- `css-smoke` passes for a direct `1974x1232` landscape EPUB render with `bodyWidthAt100=1737.125`, `htmlWidthAt100=1855.5625`, and `paragraphWidthAt100=809.34375`.
- Missing evidence: a rendered EPUB must stay wide after the viewport changes from portrait to landscape during the same reader session.

- [x] **J1: Add rendered rotation harness**
  - Add a harness mode that opens the EPUB in portrait, waits for rendered content, switches the viewport to tablet landscape, forces the same runtime resize path Android uses, and records the final renderer/page-box/content measurements.
  - Fail if `max-column-count` is not `2`, the renderer page box is not the landscape viewport, the content body stays phone-width, or a probe paragraph collapses below a natural column width.
  - 2026-07-02 result: added `epub-landscape-rotation-layout` to the reader harness. It opens production book `3809` file `426` in portrait `1232x1974`, rotates to landscape `1974x1232`, and records the before/after renderer and content document measurements.

- [x] **J2: Patch only if the rendered rotation guard fails**
  - If the guard fails, patch the layout owner that failed: viewport resize application, adaptive page-box math, or Foliate renderer attributes.
  - Do not change texture, curl, page number, Whispersync, or release code in this slice.
  - 2026-07-02 result: the guard passed against current source, so no production runtime patch was made. Direct landscape and portrait-to-landscape rotation both render as a wide two-column spread in the harness.

- [x] **J3: Validate and record**
  - Run the new rendered rotation harness, direct landscape `css-smoke`, direct `adaptive-page-box-logic`, JS syntax for touched harness/runtime modules, and focused host tests only if production reader code changes.
  - Append results to the validation log.
  - Commit and push. Do not publish a public release.
  - 2026-07-02 result: new rendered rotation harness passed in `artifacts\reader-harness\plan-j-landscape-rotation\epub-landscape-rotation-layout.out.log`; direct landscape `css-smoke` and `adaptive-page-box-logic` passed in `artifacts\reader-harness\plan-j-landscape-probe`; `node --check tools\reader-harness\src\run-reader-harness.mjs` and `git diff --check` passed. No Gradle run was needed because this slice changed only the Node harness and docs.

## Focused Plan K: Debug-Only Texture Surface Scaling

**Purpose:** Fix the page texture snap/swap class where Foliate moves text by its renderer page stride while the paper/border texture slots are full-window surfaces. This plan does not change the release cadence, curl mode, page-numbering, Whispersync, or public APK packaging.

**Release rule:** Plan K is debug/browser/host evidence only. Do not create a GitHub tag, public prerelease, public APK asset, or release workflow. Use only debug/readerdev builds if emulator evidence is needed after this source gate.

**Main files:**
- `composeApp/src/androidMain/assets/reader/navic-reader-motion.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `tools/reader-harness/src/run-reader-harness.mjs`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

- [x] **K1: Add a red texture stride guard**
  - Reproduce the bug as math first: a renderer stride of `1444px` inside a `1536px` viewport should move the texture surface by `1536px`, not by `1444px`.
  - 2026-07-02 result: `texture-offset-logic` failed red with expected `{"x":-1536,"y":0}` and actual `{"x":-1444,"y":0}`.

- [x] **K2: Scale renderer movement to the full texture surface**
  - Keep paper ownership as one static color backing plus moving paper/border slots.
  - Pass Foliate `renderer.size` into `readerSurfacePaperTextureScrollOffset` and scale `containerPosition - baseOffset` to the full viewport-width or viewport-height surface.
  - 2026-07-02 result: `navic-reader-motion.js` now scales texture movement by `maxOffset / rendererPageSize`; `navic-reader-appearance.js` passes `rendererPageSize` into scroll-offset diagnostics and runtime calls.

- [x] **K3: Prove live preview and release snap use full-surface motion**
  - Extend the harness state capture to include texture slot keys, assets, transforms, and recent texture trace events.
  - Assert that current and next paper/border slots move by the scaled full-surface amount during live preview, and that release snap reaches one full surface before the committed texture update.
  - 2026-07-02 result: Hobbit target page 11 at `1536x2048` passed with live current texture `x=-686`, next texture `x=850`, and release snap `x=-1536` before the texture update. Production Bindery book `3809` file `426` at `1974x1232` also passed the same debug gate.

## Focused Plan L: Debug-Only Organic Page-Number Typography Parity

**Purpose:** Address the physical feedback that organic EPUB page numbers still do not visually follow the selected ebook font, especially with the `Dys` font. The goal is not to turn the page number into a Material overlay or change its size, but to make its typography dimensions come from the same reader setting path as visible EPUB prose.

**Release rule:** Plan L is debug/browser/host evidence only. Do not create a GitHub tag, public prerelease, public APK asset, or release workflow.

**Main files:**
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- `tools/reader-harness/src/run-reader-harness.mjs`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

- [x] **L1: Add a computed-style guard**
  - Open a real EPUB with Navic `Dys`, non-default font weight, letter spacing, and word spacing.
  - Compare the organic root page-number layer against the first visible EPUB prose element, not against source strings or the document body container.
  - 2026-07-02 result: the new `epub-page-number-font-parity` harness first failed red because the page number reported `letter-spacing=normal` while EPUB prose reported `2px`.

- [x] **L2: Reuse reader typography dimensions**
  - Keep the organic root page number and `# / #` label.
  - Keep the page-number size intentionally small.
  - Apply the selected reader font weight, letter spacing, and word spacing to the page-number layer beside the existing font-family resolution.
  - 2026-07-02 result: `navic-reader-pagination.js` now styles the page-number layer with `readerFontWeightValue`, `readerLetterSpacingValue`, and `readerWordSpacingValue`.

- [x] **L3: Validate**
  - Run JS syntax, the new page-number parity row on Hobbit and production Bindery fixtures, the existing font CSS smoke row, and the focused reader shell progress host test.
  - 2026-07-02 result: Hobbit at `1536x2048` and production Bindery book `3809` file `426` at `1974x1232` both passed with page number and EPUB prose sharing the Dys stack, `font-weight=500`, `letter-spacing=2px`, and `word-spacing=6px`.

## Focused Plan M: Debug-Only Native Rail Endpoint Harness Stabilization

**Purpose:** Close the current-source debug gate for native chapter rail endpoint taps without using public release builds. The failing path was not the Foliate endpoint command itself: direct DevTools rail commands reached Chapter 1 page `0/9` and `8/9`, while the native matrix failed because the automation tapped the exact slider right bound and because Whispersync audio-follow was active during a rail-only check.

**Release rule:** Plan M is debug/readerdev and test-harness only. Do not create a GitHub tag, public prerelease, public APK asset, or release workflow. A public release is only allowed later if this is part of a coherent major reader candidate that has passed the full debug gate.

**Main files:**
- `scripts/adb-reader-smoke.ps1`
- `scripts/adb-reader-komikku-matrix.ps1`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

- [x] **M1: Preserve the red reproduction**
  - Current-source readerdev with production book `3809` file `426` failed `chapter-rail-native-end`: the post-action snapshot remained `chapterPageIndex=0`, `chapterPageCount=9`.
  - Evidence showed the direct WebView command reached `chapterPageIndex=8`, then a later `media-overlay-follow` relocation and a right-bound tap fallthrough invalidated the native UI check.

- [x] **M2: Guard the harness failure modes**
  - `ReaderRuntimeAssetsTest.adbReaderSmokeCanRequireNativeChapterRailEndpointAfterPostActionProbe` now requires fractional UI-node taps to clamp inside exported bounds instead of tapping exact right/bottom bounds.
  - The same guard requires rail-only matrix preparation to ignore Whispersync launch arguments so audio-follow cannot relocate the page under test.

- [x] **M3: Patch the automation layer only**
  - `adb-reader-smoke.ps1` now clamps `tapDescFraction` coordinates inside UI node bounds.
  - `adb-reader-komikku-matrix.ps1` now strips Whispersync prepare args when `-OnlyRailEndpointChecks` is active and logs that choice.

- [x] **M4: Validate on emulator without public release**
  - Focused host guard passed with `:composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanRequireNativeChapterRailEndpointAfterPostActionProbe"`.
  - The rail matrix passed on `emulator-5554` with the same Whispersync args still present on the command line; the matrix logged that it ignored them for rail-only mode.
  - Artifacts: `captures\reader-komikku-matrix\plan-m-current-rail-endpoints-fixed`.
  - Endpoint evidence: start snapshot `chapterPageIndex=0`, `chapterPageCount=9`; end snapshot `chapterPageIndex=8`, `chapterPageCount=9`; no public release was created.
