# Reader Whispersync Focused File Plans Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the Komikku reader and Whispersync work into file-owned, testable slices that can be completed, committed, and validated without publishing public APKs for microfixes.

**Architecture:** Komikku owns native reader shell, tap ownership, chrome, rail, and settings. Anx/Foliate owns EPUB/PDF behavior, locators, selection, annotations, visible ranges, and media-overlay semantics. Bindery owns OPDS/API data, sidecars, resources, progress, generated cover assets, and audiobook identity.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android WebView, Foliate/PDF.js reader assets, Gradle host/common tests, readerdev emulator, ADB probes, Playwright reader harness, Bindery OPDS/API schema.

---

## Release Rule

- Debug iteration means local `darkaxt.navic.readerdev` or another debuggable APK installed for emulator/device validation.
- Public release means GitHub tag/prerelease/APK upload through `scripts/publish-github-release.ps1`.
- Public release is only allowed after a coherent feature or major fix has passed its file-owned plan gates and is ready for physical-device acceptance.
- Do not publish for isolated green probes, diagnostics, one-file visual tweaks, or partial fixes.

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

- [ ] **A1: Add or tighten failing source/harness guards**
  - Guard tablet landscape against one-word/min-content columns using the existing `adaptive-page-box-logic` and `page-box` probes.
  - Guard standard drag so preview uses the current rendered page and release commits exactly one page.
  - Guard page-number font so the root organic page number resolves the selected reader font before publisher font sampling.
  - Guard paper and border layers so paper texture is not applied per EPUB document element and border overlays stay visible.

- [ ] **A2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest
    ```
  - Expected: the new or tightened guard fails before production runtime changes, unless the behavior is already correctly implemented.

- [ ] **A3: Implement runtime changes**
  - Keep Foliate/Anx as the layout core.
  - Keep standard drag separate from optional curl preview.
  - Keep root surface texture ownership in the reader root, not in EPUB documents.
  - Let page textures move with the page preview/commit path instead of swapping after text settles.
  - Make tablet landscape use a readable spread/page box, not a narrow centered column.

- [ ] **A4: Validate JS and browser harness**
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

- [ ] **A5: Validate in readerdev**
  - Run readerdev install/open with production book `3809`, ebook file `426`, sidecar `/opds/books/3809/sync/8`, audiobook `34`, audiobook file `633`.
  - Run `adb-reader-smoke.ps1` probes: `page-box`, `texture-slots`, `page-number-font`, `native-drag-preview-texture`.
  - Expected: `publicationReady`, readable page box, page-number font parity, moving texture slots, no reader console errors.

- [ ] **A6: Commit and audit**
  - Run focused Gradle host test from A2 and `git diff --check`.
  - Commit message: `Stabilize reader surface fidelity`.
  - Audit docs for remaining `landscape`, `texture`, `page-number`, `curl`, and `drag` references; every remaining item must be physical acceptance or a named later slice.

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

- [ ] **B1: Add or tighten failing guards**
  - Back from readable EPUB content must return to native shell cover before leaving reader.
  - Center tap toggles chrome through native overlay, not WebView.
  - Short tap over images/links must not accidentally trigger content action or page skip.
  - Long press is the content-action path.
  - Progress rail uses controller navigation and reaches first/last chapter pages.
  - Settings must have one primary entry path, not duplicated bottom/top controls.

- [ ] **B2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:test --tests paige.navic.reader.ReaderControllerTest
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeCommonChromeTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest
    ```
  - Expected: new guard fails until shell/controller ownership is correct, unless behavior is already implemented.

- [ ] **B3: Implement shell/controller changes**
  - Route app-bar back and Android back through `ReaderController.onNavigateBack()`.
  - Keep native overlay above WebView for short tap, center tap, edge tap, and drag.
  - Let WebView receive long press and explicit content actions.
  - Keep settings dialog as overlay instead of resizing reader content.
  - Keep Whispersync headset as dim paper-integrated glyph, not circular Material chrome.

- [ ] **B4: Validate readerdev shell behavior**
  - Run:
    ```powershell
    .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -PrepareReaderLaunch -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\focused-plan-b-native-shell
    .\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -CaptureReaderDiagnostics -DevtoolsProbe chapter-progress-current-endpoints -RequireNoReaderConsoleErrors -ArtifactDir captures\reader-bridge-probes\focused-plan-b-progress
    ```
  - Expected: center tap toggles chrome, drag next/previous works, rail endpoints work, no reader console errors.

- [ ] **B5: Commit and audit**
  - Run both focused Gradle commands from B2 and `git diff --check`.
  - Commit message: `Align reader shell controls with Komikku ownership`.
  - Audit docs for `center tap`, `cover drag`, `progress rail`, `back`, `duplicate settings`, and `bottom toolbar`.

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

- [ ] **C1: Add or tighten schema drift guards**
  - Guard ready-pair rule: actionable only when exact pair has `status == ready` and non-empty `artifactHref`.
  - Guard current resource identity: `resourceKey`, current `href`, legacy `resourceHref`.
  - Guard current progress payload: `alias`, `resourceKey`, `href`, ms/seconds positions, `completed`, `updatedAt`.
  - Guard current audio quality sort: `qualityScore`, bitrate, sample rate, duration.
  - Guard generated cover asset shape: `type="readerShellCover"` and shell/fullscreen variant rels.

- [ ] **C2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests paige.navic.reader.BinderyWhispersyncSchemaContractTest
    .\gradlew.bat --no-daemon --console=plain :composeApp:test --tests paige.navic.domain.repositories.BinderyBookSyncJsonTest --tests paige.navic.domain.repositories.BinderyRepositoryCatalogJsonTest --tests paige.navic.domain.repositories.BinderyRepositoryResourceJsonTest --tests paige.navic.domain.repositories.BinderyRepositoryProgressCacheTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest --tests paige.navic.ui.screens.bindery.BinderyCatalogDisplayPolicyTest
    ```
  - Expected: any missing current-schema behavior fails before production mapping changes.

- [ ] **C3: Implement schema parity**
  - Keep route resolution in `BinderyUrlPolicy`.
  - Keep pair launch alive from `ebookBookFileId + audiobookBookFileId + artifactHref` even without preloaded audiobook row.
  - Do not compare display titles as audio identity.
  - Do not show catalog headset badge for summary-only `whispersyncStatus`.

- [ ] **C4: Commit and audit**
  - Run C2 commands and `git diff --check`.
  - Commit message: `Track current Bindery Whispersync schema`.
  - Audit docs for `syncPairs`, `artifactHref`, `resourceKey`, `progress`, `qualityScore`, `readerShellCover`, and `navic-opds-api-schema`.

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

- [ ] **D1: Add or tighten pure-domain guards**
  - Visible range inside a sidecar segment resolves to expected audio resource, track index, and millisecond position.
  - Audio playback position resolves to one text overlay segment and rejects stale track/resource mismatches.
  - Already-visible media-overlay follow highlights in place and does not issue another reader relocation.
  - Companion progress stores exact resource identity, track index, and millisecond position.

- [ ] **D2: Run focused red check**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:test --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.ReaderProgressSyncTest --tests paige.navic.ui.screens.reader.ReaderWhispersyncLaunchPolicyTest
    ```
  - Expected: new behavior guard fails before production changes, unless already implemented.

- [ ] **D3: Implement runtime path**
  - Use href, CFI/text range, resource key/href, track index, and millisecond position.
  - Do not use page number alone as Whispersync identity.
  - Page-scoped headset remains hidden when current visible page has no cue.
  - Headset tap routes through native paired audiobook playback, not WebView.
  - `media-overlay-follow` visible-range events must not trigger a new page-to-audio seek.
  - Character-offset cues highlight without fragment ids.

- [ ] **D4: Validate readerdev enjoyment gate**
  - Run:
    ```powershell
    .\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -BookId 3809 -BookFileId 426 -WhispersyncSidecarUrl /opds/books/3809/sync/8 -WhispersyncArtifactId 8 -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -WhispersyncAudiobookTitle "Bastille vs. the Evil Librarians"
    .\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -NoBuild -NoInstall
    ```
  - Expected: page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes pass.

- [ ] **D5: Commit and audit**
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

- [ ] **E1: Verify Plans A-D are complete for the release scope**
  - Run:
    ```powershell
    rg -n "\[ \] \*\*Step [A-D]" docs\superpowers\plans\2026-07-02-reader-whispersync-focused-file-plans.md
    ```
  - Expected: no unchecked A-D steps remain for the release candidate. If a slice is excluded, add a dated exclusion note naming why it does not block this candidate.

- [ ] **E2: Run full local validation**
  - Run:
    ```powershell
    .\gradlew.bat --no-daemon --console=plain :composeApp:test
    .\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost
    node --check tools\reader-harness\src\run-reader-harness.mjs
    node --check tools\reader-harness\src\adb-webview-eval.mjs
    git diff --check
    ```
  - Expected: all pass.

- [ ] **E3: Run final readerdev acceptance matrix**
  - Run:
    ```powershell
    .\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -BookId 3809 -BookFileId 426 -WhispersyncSidecarUrl /opds/books/3809/sync/8 -WhispersyncArtifactId 8 -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -WhispersyncAudiobookTitle "Bastille vs. the Evil Librarians"
    .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -PrepareReaderLaunch -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\final-candidate-readerdev
    .\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -NoBuild -NoInstall
    ```
  - Expected: Komikku matrix and Whispersync enjoyment gate pass on the same candidate.

- [ ] **E4: Bump version and commit candidate**
  - Only after E2 and E3 pass, update Android version fields in `androidApp/build.gradle.kts`.
  - Commit message: `Prepare reader Whispersync release candidate`.
  - Push to GitHub.

- [ ] **E5: Publish with explicit readiness note**
  - Run:
    ```powershell
    $versionLine = Select-String -Path androidApp\build.gradle.kts -Pattern 'versionName\s*=\s*"([^"]+)"' | Select-Object -First 1
    $tag = $versionLine.Matches[0].Groups[1].Value
    .\scripts\publish-github-release.ps1 -Tag $tag -Repo Darkaxt/Navic -Remote fork -AllowPublicRelease -ReleaseReadinessNote "Reader/Whispersync candidate passed common tests, Android host tests, readerdev Komikku matrix, and readerdev Whispersync enjoyment gate for production book 3809."
    ```
  - Expected: Android APK release is published, iOS jobs are skipped, and the tag matches the committed Android version.

- [ ] **E6: Record release evidence**
  - Update validation log and plans with tag, GitHub run id, APK asset URL, Gradle commands, readerdev capture paths, and remaining physical-device acceptance items.
  - Commit message: `Record reader Whispersync release evidence`.

## Completion Audit

- `2026-06-13-komikku-reader-port-design.md` active reader requirements map to Plan A, B, or E.
- `2026-06-18-whispersync-design.md` active Whispersync requirements map to Plan C, D, or E.
- Bindery API changes map to Plan C and the external schema file.
- Public release maps only to Plan E.
- Navidrome credentials are not required for ebook/audiobook implementation validation.
- Foliate/Anx core is not replaced by Turn.js or another flipbook core.
- Page number alone is never Whispersync identity.
