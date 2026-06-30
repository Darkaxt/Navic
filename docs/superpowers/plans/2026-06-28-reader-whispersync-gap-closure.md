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

Closed stages stay documented below but are no longer the active queue. The current queue starts from the first release-worthy milestone after `v1.0.11-theta16`.

1. **Stage 5C.5: Credential-Bootstrapped Whispersync Enjoyment Validation** - use `bindery-debug.env` and a debuggable APK (`darkaxt.navic.readerdev` or another explicitly debug-routable package) to launch the paired book/audiobook route directly. Lack of public-release login is not a reason to stop implementation validation.
2. **Stage 5C.6: Signed Release Whispersync Packaging Validation** - run the same paired flow on `darkaxt.navic` only when a logged-in physical release device or real ignored `navic-release-login.env` is available. This is the public release packaging proof, not the normal development blocker.
3. **Stage 6F: Physical Layout And Texture Acceptance Pass** - batch human/device visual judgment for phone, Fold, and Tab layouts: typography margins, paper/edge texture strength, settings density, rail feel, drag feel, curl snapshot feel, and whether the result is faithful enough to Komikku instead of a knock-off.

Recently closed:
- **Stage 7B: Theta17 Staged Release Candidate** - packaged the completed post-theta16 gap work as the Android-only theta17 release candidate.
- **Stage 8D: Theta17 Release Validation Baseline** - installed and validated the published release package until the release-login boundary.
- **Stage 5C.4: Current-Source Whispersync Enjoyment Validation Refresh** - passed the paired Bindery sidecar plus audiobook matrix on `darkaxt.navic.readerdev` using `bindery-debug.env`.
- **Stage 6E.4: Captured Page Curl Snapshot Preview** - completed after theta17 as a host/browser-harness-proven curl fidelity slice; physical acceptance remains covered by Stage 6F.
- **Stage 8M: Whispersync Gate Release Identity Guard** - keeps the enjoyment validation script aligned with the current Android release identity.

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

### Stage 2B: User-Driven Anx Interaction Validation

Status: complete.

Scope:
- Reinstall/launch current-source readerdev before collecting evidence; installed `darkaxt.navic.readerdev` theta14 is stale and cannot be used as current theta15 behavior proof.
- Validate the Anx-owned interaction routes that already have controller/UI implementations: selection actions, selection clear, annotation popup, external link prompt, history state, pull-up, and reader search.
- Treat this as a behavior gate. If a route is only type/source-green or a probe cannot exercise native UI deterministically, add the harness/UI guard in this stage rather than claiming success.
- Keep release package evidence separate: `darkaxt.navic` theta15 is login-blocked on emulator, so Stage 2B uses `darkaxt.navic.readerdev` for current-source implementation proof.

Main files and scripts:
- `scripts/install-reader-dev.ps1` - current-source readerdev install/launch.
- `scripts/adb-reader-smoke.ps1` - focused package ownership, post-probe native actions, and bridge/log assertions.
- `tools/reader-harness/src/adb-webview-eval.mjs` - deterministic WebView event probes.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt` and `ReaderCoordinator.kt` - interaction state ownership.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`, `ReaderSelectionActions.kt`, `ReaderAnnotationDialog.kt`, `ReaderExternalLinkDialog.kt`, `ReaderSearchDialog.kt`, `ReaderHistoryCapsule.kt` - native Komikku UI routes.
- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md` - concise Stage 2B results.

Executed commands and evidence:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall -RequireReaderLaunch -Capture
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -TapFraction '0.90,0.50,900' -ArtifactDir captures\reader-bridge-probes\stage2b-enter-readable-after-relaunch
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe phase3-events -RequireReaderBridgeEvent externalLink,annotationClick,annotationDrawn,overlayCreated,loadDoc,pushState,footnoteClose,pullUp -ArtifactDir captures\reader-bridge-probes\stage2b-phase3-events-final-current-source
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe visible-selection-payload -PostProbeAction 'tapText:Highlight,1200' -RequireReaderEngineCommand 'applyHighlights' -RequireReaderBridgeEvent annotationDrawn -ArtifactDir captures\reader-bridge-probes\stage2b-visible-selection-highlight-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe visible-selection-payload -PostProbeAction 'tapText:Copy,1200' -RequireReaderLog 'Reader selection copied length=' -ArtifactDir captures\reader-bridge-probes\stage2b-visible-selection-copy-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe visible-selection-payload -PostProbeAction 'tapText:Note,700|tapText:Annotation,500|text:Stage2B_visible_note,500|tapText:Save,1800' -RequireReaderLog 'Reader selection note save length=' -RequireReaderBridgeEvent annotationDrawn -ArtifactDir captures\reader-bridge-probes\stage2b-visible-selection-note-save-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe visible-selection-clear -RequireReaderBridgeEvent selectionCleared -ArtifactDir captures\reader-bridge-probes\stage2b-visible-selection-clear-clean-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe annotation-roundtrip -PostProbeAction 'tapText:Close,900' -RequireReaderBridgeEvent annotationDrawn,annotationClick -ArtifactDir captures\reader-bridge-probes\stage2b-annotation-popup-close-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe external-link-prompt -PostProbeAction 'tapText:Close,900' -RequireReaderBridgeEvent externalLink -ArtifactDir captures\reader-bridge-probes\stage2b-external-link-prompt-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe history-controls -PostProbeAction 'tapDesc:Close history controls,900' -ArtifactDir captures\reader-bridge-probes\stage2b-history-controls-readable
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -PostProbeAction 'tap:540,1190,400|text:the,600|tapDesc:Search,2500' -RequireReaderEngineCommand search -ArtifactDir captures\reader-bridge-probes\stage2b-search-dialog-query-focused-field
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta15 -NoLaunch -CaptureReaderDiagnostics -PostProbeAction 'tap:540,650,2500' -RequireReaderEngineCommand goToCfi -RequireReaderBridgeEvent locationChanged -ArtifactDir captures\reader-bridge-probes\stage2b-search-result-navigation-readable
```

Results:
- GREEN/READERDEV-LAUNCH: `scripts\install-reader-dev.ps1 -NoBuild -NoInstall -RequireReaderLaunch -Capture` reopened `darkaxt.navic.readerdev` directly into the discovered Bindery EPUB target `A Memory of Light (epub)` and captured `publicationReady` under PID `27436`.
- GREEN/PHASE3-BRIDGE: `stage2b-phase3-events-final-current-source` captured `externalLink`, `annotationClick`, `annotationDrawn`, `overlayCreated`, `loadDoc`, `pushState`, `footnoteClose`, and `pullUp` from the current-source readerdev runtime.
- GREEN/SELECTION-ACTIONS: `stage2b-visible-selection-highlight-readable`, `stage2b-visible-selection-copy-readable`, and `stage2b-visible-selection-note-save-readable` proved visible EPUB selection through native Highlight, Copy, and Note UI actions. Highlight/Note produced `annotationDrawn`; Copy logged `Reader selection copied length=`.
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanClearVisibleSelectionThroughTheRealDocument` failed before `visible-selection-clear` was added to `adb-webview-eval.mjs` and `adb-reader-smoke.ps1`.
- GREEN/SELECTION-CLEAR: `stage2b-visible-selection-clear-clean-readable` creates a visible real EPUB selection, clears that same content document, and logs `selectionCleared` without relying on a synthetic offscreen DOM node.
- GREEN/ANNOTATION-AND-LINKS: `stage2b-annotation-popup-close-readable` verified the native annotation popup and close route; `stage2b-external-link-prompt-readable` verified the native external-link prompt and close route.
- GREEN/HISTORY: `stage2b-history-controls-readable` verified PushState/native history capsule display and close behavior.
- GREEN/SEARCH-ROUTE: `stage2b-search-dialog-query-focused-field` dispatched the native search command, and delayed logs captured `searchResults(count=28029)` for the query `the`; `stage2b-search-result-navigation-readable` verified result navigation through `goToCfi` and `locationChanged`.
- CAVEAT/SEARCH-UX: the search field did not auto-focus, so the deterministic script had to tap into the input before typing. The broad query `the` took roughly 78 seconds to return 28,029 results, which is a performance/UX follow-up rather than a Stage 2B route failure.
- CAVEAT/RELEASE-PACKAGE: `darkaxt.navic` theta15 remains login-blocked on the emulator. These are current-source readerdev proofs, not release-package interaction proofs.

Closure:
- [x] Current-source readerdev installed and focused.
- [x] Phase 3 event bridge path proven with current-source readerdev.
- [x] Selection Highlight/Copy/Note routes proven through native UI.
- [x] Selection clear proven through a real visible EPUB document probe.
- [x] Annotation and external-link prompts proven through native UI.
- [x] Search dialog/result navigation proven through native UI, with UX/performance caveat recorded.
- [x] Validation log updated and focused/full Gradle checks run for any code changes.

### Stage 2C: Short-Tap Content Hit Ownership

Status: complete.

Scope:
- Fix the native paragraph hit-test failure surfaced by the CSS smoke harness after Stage 6D.2.
- Preserve Komikku shell ownership for short taps: center taps and ordinary text taps belong to the native reader frame/menu/tap-zone model.
- Preserve Anx/Foliate behavior for deliberate text selection: long press can still resolve ordinary text through content hit testing and route into selection actions.
- Keep image/link/media content interactive for short-tap diagnostics and fallback paths; only ordinary text is excluded from short-tap content hit testing.

Main files:
- `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeImageLinkTest.kt`
- `tools/reader-harness/src/reader-trace-assertions.mjs`

Executed commands and evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress --console=plain
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\paginator.js
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tmp\reader-live\book-3809-file-426.epub
```

Results:
- RED/HARNESS-FIRST: after Stage 6D.2, `css-smoke` failed with `Expected native center hit-test not to suppress ordinary paragraph text`; the trace showed ordinary EPUB text was still treated as a short-tap content hit.
- RED/HOST-FIRST: `ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress` was tightened to require `readerContentActionAtRootPoint` to skip `hit.kind === 'text'`.
- GREEN/HOST-FOCUSED: the focused guard passed after `readerContentActionAtRootPoint` continued past ordinary text hits while leaving long-press `readerContentActionInDocumentAtPoint` unchanged.
- GREEN/JS: touched reader-content JS and the already-involved paginator JS passed syntax checks.
- GREEN/HARNESS: `css-smoke` passed against `tmp\reader-live\book-3809-file-426.epub`; the trace now reports `paragraphNativeCenterContentHit=false`, `paragraphNativeScaledContentHit=false`, and retained Stage 6D.2 texture evidence (`surfaceTextureOpacity=0.66` with triple border-overlay backgrounds).

Closure:
- [x] Short-tap content hit testing no longer classifies ordinary EPUB text as interactive.
- [x] Deliberate long-press text selection remains on the content hit-test path.
- [x] Browser harness confirms the native short-tap paragraph path is no longer suppressed.

### Stage RC16: Theta16 Release Candidate

Status: public Android release published.

Scope:
- Package the completed staged reader fixes after `v1.0.11-theta15`: Komikku settings grouping, stronger paper/edge texture visibility, and Stage 2C native short-tap text ownership.
- Do not add new feature edits into the release candidate.
- Build the signed Android artifact through GitHub Actions; local release signing remains intentionally unavailable.

Commands and evidence:

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta16
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
git tag v1.0.11-theta16 fd7dc3ea
git push fork v1.0.11-theta16
gh run view 28376083603 --repo Darkaxt/Navic --json status,conclusion,url,jobs
gh release view v1.0.11-theta16 --repo Darkaxt/Navic --json tagName,url,publishedAt,assets
```

Results:
- GREEN/VERSION: Android release identity is `versionCode=444`, `versionName=v1.0.11-theta16`.
- GREEN/SUITE: `:composeApp:testAndroid` passed on the theta16 identity.
- GREEN/GITHUB-ACTIONS: run `28376083603` completed successfully; `Build Android APK` succeeded, `Verify release APK signing` succeeded, and iOS jobs were skipped.
- GREEN/RELEASE: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-theta16` was published with asset `Navic.apk` (`sha256:397cf69f18f242b03506b6913f2d4dbfad32b0d48274c105ca8000947b304eb0`).
- CAVEAT/RELEASE-SCRIPT: the background release watcher expects an existing local tag. The first run exited at tag lookup; the corrected flow created/pushed the tag, then monitored run `28376083603` directly.

Next gate:
- Install theta16 on a logged-in device or emulator state and run the release-package reader/Whispersync validation matrix before claiming release-level usability.

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

### Stage 5C.1: Bindery Whispersync Schema Drift Guard

**Purpose:** Make the updated Bindery Whispersync API contract executable in Navic before adding more reader/player behavior. This stage prevents the client from drifting behind `navic-opds-api-schema.md` again, especially around exact pair readiness, sidecar cue identity, audiobook detail fields, progress identity, and route selection.

**Authority:**
- `C:/Users/darka/Documents/Projects/Stremio Add-on Tester/github-export/bindery/docs/navic-opds-api-schema.md` (last updated 2026-06-29)
- `docs/superpowers/specs/2026-06-18-whispersync-design.md`

**Main files:**
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyDtoMapping.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/WhispersyncModels.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookPlayerPolicy.kt`

**Tests:**
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/BinderyWhispersyncSchemaContractTest.kt`
- Existing focused tests under `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/Bindery*Test.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/WhispersyncTimelineParserTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderProgressSyncTest.kt`

**Acceptance:**
- A failing host guard must prove that the current server contract date, required OPDS routes, exact ready-pair rule, sidecar cue fields, current resource audio fields, JSON audiobook detail fields, and current progress fields are all represented in either parser tests or production models.
- Ready Whispersync remains pair-scoped: book-level `whispersyncStatus` alone must never create an actionable route or cover badge.
- Sidecar cues must preserve `audioResourceId`, `audioTrackIndex`, `audioHref`, second-based `audioStart`/`audioEnd`, `ebookHref`, `spineIndex`, and local `ebookStart`/`ebookEnd`.
- Progress read/save must prefer current `resourceKey`/`href` identity and millisecond positions while remaining tolerant of legacy `resourceHref`.
- The stage must not add UI polish or release automation. It is a schema/model/route correctness gate.

**TDD steps:**
- [x] Add a failing schema contract test that reads the repo Whispersync spec and source files, then asserts coverage for the 2026-06-29 Bindery contract fields and routes.
- [x] If the guard exposes missing production representation, add the smallest parser/model/route change and a behavior test for that exact field. The RED guard exposed stale spec authority text and a guard that needed to inspect the API-client route boundary; no production parser change was required.
- [x] Run focused schema/parser/progress tests. Completed with the focused `BinderyWhispersyncSchemaContractTest` host guard plus the full `:composeApp:testAndroid` aggregate; this Gradle project does not accept `--tests` on the aggregate task.
- [x] Run `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain`.
- [x] Run `git diff --check`.
- [x] Update `docs/superpowers/specs/2026-06-18-whispersync-design.md` and the validation log only with real evidence.
- [x] Commit and push this stage before returning to release-device Whispersync validation. Completed in `d9eeee35`.

Results so far:
- RED/HOST-FIRST: `BinderyWhispersyncSchemaContractTest` failed while the Whispersync spec still named the older `2026-06-28` compatibility section and while the route guard inspected only repository-level symbols instead of the `BinderyApiClient`/`BinderyUrlPolicy` HTTP boundary.
- GREEN/HOST-FOCUSED: the focused guard passed after updating the spec to `Bindery API Compatibility As Of 2026-06-29`, adding `Last updated: 2026-06-29`, and tightening the guard to inspect the actual API-client route boundary.
- NOTE/GRADLE-SHAPE: `:composeApp:testAndroid --tests ...` is invalid in this project because `testAndroid` is an aggregate task. Use `:composeApp:testAndroidHostTest --tests ...` for source-reading host guards and full `:composeApp:testAndroid` for common/parser/progress coverage.
- GREEN/FULL-ANDROID: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed after Stage 5C.1 changes.
- GREEN/WHITESPACE: `git diff --check` passed.
- GREEN/HOST-FINAL: `.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.BinderyWhispersyncSchemaContractTest --console=plain` passed again after final plan/log edits.

### Stage 5C.2: Whispersync Readerdev Enjoyment Matrix

Status: implementation/runtime proof complete for the current production paired route; release-device proof still pending.

Purpose:
- Verify the current source against the real production Bindery paired ebook route instead of adding isolated UI polish.
- Keep release-package evidence honest: the public `darkaxt.navic` package still needs Navic/Navidrome login credentials before deep reader/Whispersync validation can run there, so this stage uses `darkaxt.navic.readerdev` as the seeded implementation lab.
- Fix validation harness brittleness before trusting the matrix: Whispersync probes must wait for the requested visible href rather than sampling stale saved state after a fixed number of animation frames.

Target:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Version: `v1.0.11-theta16`, `versionCode=444`, `lastUpdateTime=2026-06-29 18:32:25`
- Book: `3809`
- Ebook file: `426`, canonicalized to `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`
- Sidecar: `/opds/books/3809/sync/8`
- Audiobook: `34`
- Audiobook book file: `633`

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -ReaderPublicationUrl https://bindery.remaxku.eu/book/3809 -ReaderResourceHref "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -ReaderWhispersyncSidecarUrl /opds/books/3809/sync/8 -ReaderWhispersyncArtifactId 8 -ReaderWhispersyncAudiobookId 34 -ReaderWhispersyncAudiobookBookFileId 633 -ReaderWhispersyncAudiobookTitle "Bastille vs. the Evil Librarians" -RequireReaderLaunch -Capture
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncPageScopedControl --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncCompanionProgressPersistence --console=plain
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta16 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-page-scoped-control -ArtifactDir captures\reader-smoke\stage5c2-page-scoped-control-20260629-1841
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta16 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-audio-follow -ArtifactDir captures\reader-smoke\stage5c2-audio-follow-20260629-1841
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta16 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-char-offset-overlay -ArtifactDir captures\reader-smoke\stage5c2-char-offset-overlay-20260629-1842
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta16 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-companion-progress -ArtifactDir captures\reader-smoke\stage5c2-companion-progress-20260629-1842
```

Results:
- RED/READERDEV-FIRST: the first page-scoped probe failed because it expected `Authorforeword.xhtml` immediately after dispatching `goToHref`, but the visible range was still the saved reader state at `OEBPS/xhtml/chapter17.xhtml`.
- GREEN/API-CHECK: live Bindery sidecar `/opds/books/3809/sync/8` still exposes both `Authorforeword.xhtml` cues and later `chapter17.xhtml` cues, so the failure was harness sampling, not missing production sidecar coverage.
- RED/HOST-FIRST: focused `ReaderRuntimeAssetsTest` guards failed until `whispersync-page-scoped-control` and `whispersync-companion-progress` used a condition-based `waitForTargetVisibleRange` diagnostic snapshot loop instead of fixed-frame sampling.
- GREEN/HOST-FOCUSED: the focused host guards passed after the harness change.
- GREEN/READERDEV-LAUNCH: `install-reader-dev.ps1` rebuilt, installed, focused, and reached `publicationReady` for the production paired route.
- GREEN/PAGE-TO-AUDIO: `stage5c2-page-scoped-control-20260629-1841` reached `OEBPS/xhtml/Authorforeword.xhtml`, emitted `visibleTextRange`, activated `overlayFragmentActive`, logged `Whispersync audiobook seek ... positionMs=263360`, navigated to unsupported `OEBPS/xhtml/mini_toc.xhtml`, and dispatched `clearOverlay`.
- GREEN/AUDIO-TO-READER: `stage5c2-audio-follow-20260629-1841` emitted `visibleTextRange(... source=media-overlay-follow)`, preserving the feedback-loop suppression contract.
- GREEN/CHAR-OFFSET-OVERLAY: `stage5c2-char-offset-overlay-20260629-1842` marked a text-node range with `navic-active-overlay-fragment navic-media-overlay-range` and emitted `overlayFragmentActive`.
- GREEN/COMPANION-TARGET: `stage5c2-companion-progress-20260629-1842` resolved the cue-covered page and logged the exact audiobook target `positionMs=263360` with `overlayFragmentActive`.

Required before closure:
- [x] Run JS syntax check for `tools/reader-harness/src/adb-webview-eval.mjs`.
- [x] Run `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain`.
- [x] Run `git diff --check`.
- [x] Update the validation log.
- [x] Commit and push Stage 5C.2. Completed in `0bc15bcd`.

Remaining:
- This is current-source readerdev proof for a real production Bindery sidecar/audiobook route. It is not a public release-package proof until `darkaxt.navic` can log in or a logged-in physical device runs the same end-to-end flow.

### Stage 5C.3: Whispersync Enjoyment Gate Orchestrator

Status: complete for readerdev implementation/runtime proof; release-package proof still requires a logged-in release package or physical device.

Purpose:
- Replace scattered manual Whispersync probe commands with one executable gate that launches the paired production readerdev route and runs the full current enjoyment matrix.
- Keep this as implementation/runtime proof until a logged-in release package or physical device runs the same flow.
- Make the gate useful for future stage work: one pass/fail summary, per-probe artifacts, and no printed credentials.

Files:
- Add: `scripts/adb-whispersync-enjoyment.ps1`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt`
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`

Default target:
- Book: `3809`
- Ebook route: `https://bindery.remaxku.eu/book/3809`
- Ebook file: `https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426`
- Sidecar: `/opds/books/3809/sync/8`
- Audiobook: `34`
- Audiobook book file: `633`

Gate command:

```powershell
.\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554
```

Acceptance:
- The gate must launch `darkaxt.navic.readerdev` through `scripts\install-reader-dev.ps1`.
- The gate must run `scripts\adb-reader-smoke.ps1` for:
  - `whispersync-page-scoped-control`
  - `whispersync-audio-follow`
  - `whispersync-char-offset-overlay`
  - `whispersync-companion-progress`
- The gate must write `stage5c3-whispersync-enjoyment-summary.txt` and `probe-results.jsonl`.
- The gate must not print Bindery credential values or raw env-file contents.

TDD steps:
- [x] Add a failing source guard requiring `scripts/adb-whispersync-enjoyment.ps1`, production book defaults, all four probes, and summary artifacts.
- [x] Implement the orchestrator script with existing readerdev/smoke runners.
- [x] Run the focused source guard.
- [x] Run PowerShell parser validation for the new script.
- [x] Run the gate on emulator or record a concrete environment blocker.
- [x] Run `git diff --check`, update validation evidence, commit, and push.

Results:
- GREEN/HOST-FOCUSED: `ReaderDevEnvironmentContractTest.whispersyncEnjoymentGateRunsTheWholePairedReaderdevMatrix` passed after adding the orchestrator and Stage 5C.3 plan entry.
- GREEN/PARSER: PowerShell parser validation passed for `scripts\adb-whispersync-enjoyment.ps1`.
- GREEN/EMULATOR-GATE: `.\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall` passed against the existing `darkaxt.navic.readerdev` install and wrote artifacts under `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260629-194317`.
- GREEN/MATRIX: the single gate passed `whispersync-page-scoped-control`, `whispersync-audio-follow`, `whispersync-char-offset-overlay`, and `whispersync-companion-progress`; see `stage5c3-whispersync-enjoyment-summary.txt` and `probe-results.jsonl`.
- GREEN/FULL-ANDROID: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed after Stage 5C.3 changes.

Remaining:
- This closes the repeatable readerdev enjoyment gate. It is not a public-release claim until the same route is run against `darkaxt.navic` with real login/data or a logged-in physical device.

### Stage 5C.4: Current-Source Whispersync Enjoyment Validation Refresh

Status: complete for theta17 readerdev implementation/runtime proof; signed-release packaging proof remains Stage 5C.6.

Purpose:
- Correct the theta17 validation process after Stage 8D exposed that the public release package is not logged in on the emulator.
- Use the available Bindery-side env credentials correctly: `bindery-debug.env` is enough for `darkaxt.navic.readerdev` because the debug launcher injects the EPUB resource and Whispersync sidecar route directly.
- Keep the evidence boundary honest: this validates the actual current-source Whispersync runtime, not the public package's logged-in user journey.

Commands:

```powershell
.\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -ArtifactRoot captures\reader-whispersync-enjoyment
```

Results:
- GREEN/READERDEV-BUILD: `androidApp:assembleReaderDev` succeeded after the theta17 source changes.
- GREEN/READERDEV-INSTALL: the runner installed and launched `darkaxt.navic.readerdev`; `package-version.txt` reports `versionCode=445`, `versionName=v1.0.11-theta17`, and `lastUpdateTime=2026-06-29 21:31:15`.
- GREEN/LAUNCH: the debug launcher resolved the direct file URL to `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`, reached `publicationReady`, and loaded the readaloud plan for audiobook `34` / book file `633`.
- GREEN/MATRIX: the single gate passed `whispersync-page-scoped-control`, `whispersync-audio-follow`, `whispersync-char-offset-overlay`, and `whispersync-companion-progress`.
- ARTIFACTS: `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260629-212815\stage5c3-whispersync-enjoyment-summary.txt` and `probe-results.jsonl`.
- OBSERVED/NON-BLOCKING: logcat showed one early `Whispersync audiobook seek ignored; no playback plan match` before the playback plan loaded, then the plan loaded and all four probes passed. Track this only if it becomes user-visible during manual playback startup.

Remaining:
- Stage 5C.5 formalizes the credential-bootstrapped debuggable APK path as the required development validation route.
- Stage 5C.6 must run the same enjoyment flow against `darkaxt.navic` on a logged-in release package before claiming signed-release Whispersync proof.

### Stage 5C.5: Credential-Bootstrapped Whispersync Enjoyment Validation

Status: complete for current debug/readerdev validation policy; repeat after any Whispersync runtime change.

Purpose:
- Treat `bindery-debug.env` as the correct development validation authority for paired Bindery reader routes.
- Permit the debug APK to be purpose-built for validation: it may bypass the normal library/login journey by receiving the exact EPUB resource, Whispersync sidecar, artifact id, audiobook id, audiobook file id, and audiobook title through safe launcher extras.
- Prevent future work from using "release package is not logged in" as a reason to stop Whispersync implementation validation.
- Keep the boundary honest: credential-bootstrapped debug proof validates product behavior from current source; Stage 5C.6 validates signed public packaging and real user state.

Acceptance:
- The validation command uses an ignored env file and does not print credential values.
- The debuggable package launches the paired production Bindery route directly and reaches `publicationReady`.
- The enjoyment matrix proves page-scoped control, audio-follow suppression, character-offset overlay, and exact companion progress.
- A release-package login boundary cannot be recorded as an implementation blocker unless the same scenario also fails through the debug route.

Current evidence:
- GREEN/DEBUG-CREDENTIALS: Stage 5C.4 already ran `scripts\adb-whispersync-enjoyment.ps1` with `C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env`.
- GREEN/PAIRED-ROUTE: the debug launcher resolved book `3809` to `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`, loaded sidecar `/opds/books/3809/sync/8`, and prepared audiobook `34` / book file `633`.
- GREEN/MATRIX: `whispersync-page-scoped-control`, `whispersync-audio-follow`, `whispersync-char-offset-overlay`, and `whispersync-companion-progress` passed in `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260629-212815`.
- GREEN/RERUN: after the validation-boundary correction, `.\scripts\adb-whispersync-enjoyment.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -ArtifactRoot captures\reader-whispersync-enjoyment` passed again on current source. Artifacts: `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260629-235005\stage5c3-whispersync-enjoyment-summary.txt` and `probe-results.jsonl`.
- GREEN/RERUN-PROBES: the rerun passed `whispersync-page-scoped-control`, `whispersync-audio-follow`, `whispersync-char-offset-overlay`, and `whispersync-companion-progress` against `darkaxt.navic.readerdev` on `emulator-5554`.

Remaining:
- Re-run this stage after any Whispersync runtime, reader launcher, or audiobook playback change.
- Do not publish a public release solely because this debug validation is green; use it to justify moving toward a coherent release candidate.

### Stage 5C.6: Signed Release Whispersync Packaging Validation

Status: planned; final public-package proof only.

Purpose:
- Validate that the published `darkaxt.navic` APK can reach the same paired Whispersync behavior with real login/data.
- Keep this separate from implementation validation so missing release login state does not block debug/emulator progress.

Acceptance:
- Install the signed public APK and verify package version/build identity.
- Confirm the release package is logged in or submit credentials from an ignored `navic-release-login.env`.
- Run the paired Whispersync enjoyment flow on `darkaxt.navic` without substituting `darkaxt.navic.readerdev` evidence.
- Record any release-only failures as packaging/state issues unless the debug gate also fails.

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

### Stage 6F.1: Automated Visual Acceptance Prep Matrix

Status: complete as an automation prep pass; the captured artifacts are not valid for visual acceptance until Stage 6F.2 proves a neutral reader state.

Scope:
- Generate fresh current-source readerdev evidence for the post-theta17 reader shell before asking for physical/human visual acceptance.
- Use emulator viewport profiles that approximate Fold inner, Fold cover, and Tab S9 Ultra displays.
- Capture screenshots, window hierarchy, logcat diagnostics, and `page-box` DevTools probes for the same direct Bindery EPUB route.
- Do not claim physical acceptance from this stage. It is an automation prep gate for Stage 6F.

Files:
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- Use: `scripts/set-reader-dev-viewport.ps1`
- Use: `scripts/install-reader-dev.ps1`
- Use: `scripts/adb-reader-smoke.ps1`

Acceptance:
- `darkaxt.navic.readerdev` is built/installed from current source and launches the production Bindery EPUB route.
- Each viewport profile writes a screenshot and `page-box` probe under `captures/reader-smoke/stage6f-visual-prep/<profile>`.
- The captured package/version/logs prove the artifacts come from `darkaxt.navic.readerdev`, not the stale public package.
- Plan and validation log record what automation can and cannot prove.

TDD / validation steps:
1. [x] Set the emulator to `zfold7-inner`, launch readerdev directly to the paired EPUB route, and capture `page-box`.
2. [x] Repeat for `zfold7-cover`.
3. [x] Repeat for `tab-s9-ultra-portrait`.
4. [x] Reset emulator viewport after captures.
5. [x] Record artifacts and any failures before deciding whether Stage 6F needs implementation work or human visual review.

Results:
- GREEN/READERDEV-BUILD: `androidApp:assembleReaderDev` built the current-source debuggable package.
- GREEN/READERDEV-LAUNCH: all three profiles launched `darkaxt.navic.readerdev` to the production Bindery book `3809` route and reached `publicationReady`.
- GREEN/ARTIFACTS: artifacts were written under `captures\reader-smoke\stage6f-visual-prep\zfold7-inner`, `captures\reader-smoke\stage6f-visual-prep\zfold7-cover`, and `captures\reader-smoke\stage6f-visual-prep\tab-s9-ultra-portrait`.
- GREEN/RESET: the matrix reset the emulator viewport after the final capture.
- INVALID/VISUAL-STATE: at least one Stage 6F.1 screenshot was polluted by transient reader chrome/history/selection state. These captures can document the automation path, but they cannot be used as Komikku visual-acceptance evidence.
- OBSERVED/PAGINATION: the profiles reported different page totals (`6 / 481`, `6 / 294`, `6 / 124`). That remains useful evidence for adaptive page-composition review, but it is not enough by itself to prove a bug until the capture is neutral and the profile policy is documented.
- CORRECTED/COMPOSITION: `bodyToDocumentWidthRatio` reflects Foliate's paginated strip width and is not standalone proof that visible layout is broken. Future visual judgments must use renderer/document viewport metrics plus clean screenshots, not raw body strip width alone.

Remaining:
- Rerun Fold/Tab visual captures with Stage 6F.2 neutral-state validation before claiming profile-dependent typography/page-composition defects.
- Keep the Whispersync headset icon page-scoped, but make it visually paper-native instead of a UI badge during the same visual pass.

### Stage 6F.2: Neutral Visual Capture Guard

Status: complete as a validation guard; not release-worthy by itself.

Scope:
- Prevent visual acceptance captures from being accepted while native transient overlays or WebView media-overlay markers are visible.
- Make the ADB smoke script fail explicitly when a screenshot contains history controls, selection actions, active overlay fragments, media-overlay ranges, or selected text.
- Preserve the corrected Whispersync validation boundary: `bindery-debug.env` plus `darkaxt.navic.readerdev` is the required implementation validation path; signed release login is only the packaging proof gate.

Files:
- Modify: `scripts/adb-reader-smoke.ps1`
- Modify: `tools/reader-harness/src/adb-webview-eval.mjs`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt`
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

Acceptance:
- `adb-reader-smoke.ps1 -RequireNeutralReaderVisualState` requires a `page-box` DevTools probe and writes `reader-neutral-visual-state.txt`.
- The guard fails if exact native controls such as `History back`, `Close history controls`, `Highlight`, `Copy`, `Note`, or `Close selection actions` are visible.
- The guard fails if WebView `transientState.activeOverlayMarkerCount`, `activeMediaOverlayMarkerCount`, or `selectedTextLength` is nonzero.
- Host tests prevent the guard from disappearing from the readerdev contract.

TDD / validation steps:
1. [x] Add red host assertions for transient-state fields and the smoke-script neutral-state switch.
2. [x] Implement the read-only `page-box.transientState` probe.
3. [x] Implement exact native overlay detection in `adb-reader-smoke.ps1`.
4. [x] Prove the guard fails against a polluted live readerdev capture instead of recording it as visual evidence.

Results:
- RED/HOST-FIRST: focused `ReaderRuntimeAssetsTest` and `ReaderDevEnvironmentContractTest` failed before the transient-state probe and smoke-script guard existed.
- GREEN/FOCUSED: the same focused Gradle host tests passed after implementation.
- GREEN/SCRIPT-PARSE: PowerShell parser validation passed for `scripts\adb-reader-smoke.ps1`.
- GREEN/JS: `node --check tools\reader-harness\src\adb-webview-eval.mjs` passed.
- EXPECTED-FAIL/READERDEV: `adb-reader-smoke.ps1 -ReaderDevtoolsProbe page-box -RequireNeutralReaderVisualState` failed on a live polluted readerdev state with `History back`, `Close history controls`, `activeOverlayMarkerCount=1`, and `activeMediaOverlayMarkerCount=1`, writing evidence under `tmp\reader-neutral-visual-gate-check`.

Remaining:
- Dismiss history/selection/media overlay state, rerun Stage 6F visual captures, and only then decide whether profile-specific typography, page composition, texture strength, or headset-icon styling needs another implementation slice.
- Do not publish a release for Stage 6F.2 alone; it is validation infrastructure, not a user-visible fix.

### Stage 6F.3: Neutral Visual Capture Rerun

Status: complete for automated neutral visual evidence; human visual acceptance remains pending.

Scope:
- Rerun Fold/tablet visual captures with `-RequireNeutralReaderVisualState`.
- Separate visual layout captures from paired Whispersync behavior captures. A paired route may correctly show active `navic-media-overlay-range` markers; that is not a neutral visual state.
- Use the plain production EPUB route for typography, margin, texture, cover-surface, and page-box evidence.

Commands:

```powershell
.\scripts\set-reader-dev-viewport.ps1 -DeviceSerial emulator-5554 -Profile zfold7-inner
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -ReaderPublicationUrl https://bindery.remaxku.eu/book/3809 -ReaderResourceHref "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -ReaderTitle "Bastille vs. the Evil Librarians" -ReaderKind Ebook -ReaderFormat epub -RequireReaderLaunch -Capture -NoBuild -NoInstall
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta17 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe page-box -RequireNeutralReaderVisualState -ArtifactDir captures\reader-smoke\stage6f-neutral-visual\<profile>
```

Results:
- EXPECTED-FAIL/PAIRED-ROUTE: `captures\reader-smoke\stage6f-neutral-visual\zfold7-inner` failed neutrality with `activeOverlayMarkerCount=1` and `activeMediaOverlayMarkerCount=1`; the first visible span was `navic-active-overlay-fragment navic-media-overlay-range`. Root cause: the paired Whispersync route actively highlights the cue-covered page by design.
- GREEN/FOLD-INNER-PLAIN: `captures\reader-smoke\stage6f-neutral-visual\zfold7-inner-plain` passed with zero native overlay hits, zero active overlay markers, zero media-overlay markers, and zero selected text.
- GREEN/FOLD-COVER-PLAIN: `captures\reader-smoke\stage6f-neutral-visual\zfold7-cover-plain` passed the same neutral-state guard.
- GREEN/TAB-COVER-PLAIN: `captures\reader-smoke\stage6f-neutral-visual\tab-s9-ultra-portrait-plain` passed the same neutral-state guard and captured the native cover surface.
- GREEN/TAB-TEXT-PLAIN: `captures\reader-smoke\stage6f-neutral-visual\tab-s9-ultra-portrait-text-plain` passed the same neutral-state guard after a right-zone tap advanced from cover to readable text.
- METRICS: clean page-box probes report stable `documentToViewportWidthRatio=0.94` on Fold inner, Fold cover, and Tab S9 Ultra; `bodyToDocumentWidthRatio` remains Foliate strip-width evidence, not a visual defect by itself.
- RESET: the emulator viewport was reset after the capture sequence.

Remaining:
- Use plain-route neutral captures for layout/texture/typography decisions.
- Use paired-route captures for Whispersync status/headset/audio-text overlay behavior.
- Stage 6F still needs human/device visual judgment for whether the current typography density, paper texture strength, and cover treatment feel faithful enough.

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
- The first slice reduced one concrete source-backed density divergence. It did not complete settings visual parity; phone/fold/tablet screenshots still need to judge surface palette, tab comfort, and scroll feel.

### Stage 6C.2: Settings Overlay Faithfulness

Status: current-source implementation complete; release-package/manual visual judgment still pending.

Scope:
- Keep the Komikku modal shell and three-tab structure intact.
- Stop appending Anx-expanded EPUB style controls as one flat `General` list.
- Group the `General` controls into stable Komikku-style sections so Typography, Spacing, Page layout, and Theme/device settings can be scanned and tested independently.

Guards and evidence:
- RED/GUARD-FIRST: `ReaderRuntimeCommonChromeTest.commonReaderSettingsGeneralTabGroupsAnxControlsIntoKomikkuSections` was added before the implementation and required `ReaderSettingsDialog.kt` to expose a reusable `SettingsSection` primitive and section labels. The first Gradle wrapper attempt hit the shell command budget before producing a persisted red report, so this slice records guard-first ordering rather than a saved red artifact.
- GREEN/HOST-FOCUSED: the same guard passed after grouping the General tab into `Typography`, `Spacing`, `Page layout`, and `Theme and device`.
- GREEN/HOST-CHROME: full `ReaderRuntimeCommonChromeTest` passed after the grouping change.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed.
- GREEN/WHITESPACE: `git diff --check` passed.
- GREEN/READERDEV: current-source `darkaxt.navic.readerdev` was installed on `emulator-5554`, launched into `A Memory of Light (epub)`, and captured the settings sheet at `captures\reader-smoke\stage6c2-settings-general-sheet-settled`. The screenshot and `window.xml` both show the selected `General` tab with the new `Typography` section and grouped font controls.

Required before closure:
- [x] Compare against Komikku `SettingsItems.kt` and existing shell guards before changing Navic.
- [x] Add a failing source guard for the flat General tab.
- [x] Implement the sectioned General tab without changing runtime, rail, tap, or Whispersync code.
- [x] Run focused guard, full chrome host class, `:composeApp:testAndroid`, `git diff --check`, and readerdev settings-sheet capture.

Remaining:
- This does not complete settings visual design. Palette, slider density, tablet/fold proportions, and deeper scroll ergonomics remain visual-review work.

### Stage 6C.3: Settings Overlay Hierarchy And Slider Density

Status: complete for source-backed hierarchy/density; release/manual visual judgment pending.

Scope:
- Keep the Komikku tabbed modal shell, pager, chip rows, and checkbox primitives intact.
- Adapt the expanded Anx EPUB control set inside that shell by separating page headings, section headings, chip labels, and slider rows into distinct density classes.
- Reduce slider vertical density so the General tab does not behave like an oversized settings page docked into the reader.
- Do not change tap ownership, progress rail, texture movement, Whispersync, or runtime bridge behavior in this slice.

Guards and evidence:
- RED/HOST-FIRST: `ReaderRuntimeCommonChromeTest.commonReaderSettingsExpandedAnxControlsUseSeparateSectionAndSliderDensity` failed while `SettingsSection` reused `HeadingItem(title)` and sliders reused full item vertical padding.
- GREEN/HOST-FOCUSED: the same focused guard passed after adding `SettingsSectionHeading`, separate `SectionVertical` / `SliderVertical` paddings, and zero extra vertical spacing inside slider rows.
- GREEN/HOST-CHROME: full `ReaderRuntimeCommonChromeTest` passed after narrowing the older tab-density guard to the actual tab row instead of banning section-heading typography globally.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed after the Stage 6C.3 changes.
- GREEN/WHITESPACE: `git diff --check` passed.

Required before closure:
- [x] Compare the concrete settings primitives against Komikku before changing Navic.
- [x] Add a failing source guard for the overloaded heading/slider density.
- [x] Implement the hierarchy/density changes without touching runtime, rail, tap, texture, or Whispersync code.
- [x] Run focused guard and full chrome host class.
- [x] Run full `:composeApp:testAndroid`, `git diff --check`, update validation log, commit, and push.

Remaining:
- Palette, tablet/fold screenshot judgment, and deeper visual polish remain separate visual-review work. This slice only closes the source-backed hierarchy/density blocker.

### Stage 6D: Paper/Texture Visual System

Status: source/harness complete for the movement/crash slice; Stage 6D.2 current-source visual-strength slice complete; release-device visual judgment still pending.

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

#### Stage 6D.2: Paper/Texture Visual Strength

Status: current-source implementation complete; release-device visual judgment still pending.

Scope:
- Make the top-level paper texture and page-edge degradation visibly present on Android readerdev screenshots instead of merely passing asset-presence/statistical guards.
- Keep the texture architecture faithful to the existing Stage 6D contract: one fixed reader-window paper layer and one fixed reader-window border layer, never per-document pseudo-elements or per-EPUB-element stacking.
- Do not change texture identity, page numbering, tap ownership, drag preview, or Whispersync behavior in this slice.

Guards and evidence:
- RED/HOST-FIRST: `ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme` failed while sepia texture opacity was still `0.54` and border overlay compositing used two background layers.
- GREEN/HOST-FOCUSED: the same guard passed after sepia texture opacity was raised to `0.66`, light-theme texture opacity to `0.24`, sepia border filtering to `contrast(1.55) saturate(1.12)`, and border compositing to three backgrounds on the same fixed layer.
- GREEN/HOST-PAPER: full `ReaderRuntimePaperSurfaceTest` passed after updating the movement guard to require all three border-overlay backgrounds to share the same texture position.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed.
- GREEN/JS: `node --check` passed for `navic-reader-helpers.js` and `reader-trace-assertions.mjs`.
- GREEN/WHITESPACE: `git diff --check` passed.
- PARTIAL/HARNESS: `css-smoke` produced `surfaceTextureOpacity=0.66` and a three-layer `page-border-overlay` background image, but the full harness still exits later on an unrelated native paragraph hit-test assertion.
- GREEN/READERDEV: current-source `darkaxt.navic.readerdev` installed and launched on `emulator-5554`; `captures\reader-smoke\stage6d2-paper-strength-readable\screen.png` shows the paper surface and edge degradation visibly present on a readable EPUB page.

Required before closure:
- [x] Add and verify a failing source guard for paper/border visibility strength.
- [x] Implement the minimal helper change without changing texture placement or input ownership.
- [x] Run focused host guard, full paper host class, `:composeApp:testAndroid`, JS syntax checks, and `git diff --check`.
- [x] Capture current-source readerdev visual evidence on the emulator.

Remaining:
- Release-device visual judgment is still required before claiming final texture strength. The current-source screenshot proves the layer is no longer absent/subtle on readerdev, but the user's phone/tablet may still need tuning.
- `css-smoke` has an open unrelated native paragraph hit-test failure that should be routed into its own staged interaction/input slice, not hidden inside paper texture work.

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

#### Stage 6E.3: Mockup Curl Sheet Roles

Scope:
- Port the next safe subset of `D:\Downloads\Trash\navic_page_curl_toggle_mockup_single_clipped.html`: explicit turning-front, turning-back, underneath, and cast-shadow roles.
- Keep the roles mounted inside the existing native drag-preview layer so no new touch owner is introduced.
- Keep the adjacent target iframe as the underneath page and keep release/cancel cleanup authoritative.

Guards and evidence:
- RED/HOST-FIRST: `ReaderRuntimePaperSurfaceTest.androidReaderPortsMockupCurlSheetRolesToDragPreviewOnly` failed while the runtime only applied curl variables to one flat preview panel.
- GREEN/HOST-FOCUSED: the same guard passed after `ensurePageDragPreviewLayer()` created `data-navic-page-curl-sheet` children and `applyPageDragCurlSheet(...)` exposed `single`/`spread` mode plus front/back face opacity variables.
- GREEN/HARNESS: `epub-native-drag-preview-underlay` passed against `tmp\reader-live\book-3809-file-426.epub` and observed `curlSheetRoles=underneath,turning-front,turning-back,cast-shadow`, `curlSheetMode=single`, `curlFrontFaceOpacity=1`, and `curlBackFaceOpacity=0` during a real EPUB boundary drag.
- GREEN/HOST-SUITE: `ReaderRuntimePaperSurfaceTest` passed after the sheet-role changes.
- GREEN/JS: `node --check` passed for `navic-reader-page-turns.js` and `tools/reader-harness/src/run-reader-harness.mjs`.

Required before closure:
- [x] Add a failing source guard for mockup sheet roles and drag-only cleanup.
- [x] Implement sheet roles inside the existing preview layer without changing tap ownership or release handling.
- [x] Extend the browser harness to verify sheet roles during an actual EPUB drag.
- [x] Run focused host test, full paper host class, JS syntax checks, and EPUB drag-preview harness.
- [x] Run full `:composeApp:testAndroid`.
- [x] Run `git diff --check`, commit, and push.

Remaining:
- This still does not capture and render the current page front face or reverse page content as real snapshots. That must be a separate Stage 6E.4 slice, guarded by center-tap/menu/long-press non-regression and spread-mode evidence.

#### Stage 6E.4: Captured Page Curl Snapshot Preview

Status: complete for host/source and browser-harness proof; physical drag-feel acceptance remains part of Stage 6F.

Scope:
- Replace the current gradient-only `turning-front` and `turning-back` curl sheets with cloned page snapshots during active native drag preview.
- Keep the existing `underneath` iframe as the adjacent target page; the snapshot sheets must sit inside the same drag-preview layer and must not introduce a new touch owner.
- Capture the current page front face from the visible Foliate content document and, when spread mode is active and adjacent content is ready, capture the reverse face from the adjacent preview iframe.
- Keep release/cancel cleanup authoritative. Snapshot capture must run only from `updatePageDragPreviewLayer(...)`, never from tap handling, menu toggles, release, or cancel.

Files:
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- Test: `tools/reader-harness/src/run-reader-harness.mjs`
- Validate: `node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- Validate: `node --check tools/reader-harness/src/run-reader-harness.mjs`
- Validate: focused `ReaderRuntimePaperSurfaceTest`
- Validate: `epub-native-drag-preview-underlay` browser harness against the production EPUB fixture

Acceptance:
- During an EPUB boundary drag, `[data-navic-page-curl-snapshot="front"]` exists inside the `turning-front` sheet and contains visible cloned current-page content.
- In `spread` mode, `[data-navic-page-curl-snapshot="back"]` exists inside the `turning-back` sheet when the adjacent preview iframe is ready; in `single` mode, back-face absence is allowed but must be explicitly reported by the harness.
- Snapshot DOM must be clipped and pointer-transparent, preserving current page ownership and the native tap-zone overlay.
- Cancel and release remove the preview layer and all snapshots before dispatching the real page turn.
- Center tap/menu, long-press selection, image/link interaction routing, and shell cover behavior remain out of scope for this slice except as non-regression gates.

TDD steps:
1. [x] Add a failing host/source guard that requires a dedicated snapshot helper, observable `data-navic-page-curl-snapshot` markers, a call from `updatePageDragPreviewLayer(...)`, and no snapshot capture in release/cancel branches.
2. [x] Extend `epub-native-drag-preview-underlay` to inspect front/back snapshot presence, text length, dimensions, and cleanup after cancel.
3. [x] Implement snapshot cloning inside the existing drag-preview layer using cloned document/body content rather than moving or resizing the real Foliate renderer.
4. [x] Run focused host/source tests, JS syntax checks, the browser harness, and `git diff --check`.
5. [x] If those pass, run the full reader host suite or `:composeApp:testAndroid` before commit.

Guards and evidence:
- RED/HOST-FIRST: `:composeApp:testAndroid` failed only on `ReaderRuntimePaperSurfaceTest.androidReaderPortsCurlSnapshotsToDragPreviewOnly` before the runtime exposed page-curl snapshot markers; log `tmp/codex-validation/stage6e4-curl-snapshot-red-full-20260629-214854.err.log`.
- GREEN/HOST-SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed after implementation; log `tmp/codex-validation/stage6e4-curl-snapshot-green-full-20260629-221041.out.log`.
- GREEN/JS: `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js` and `node --check tools\reader-harness\src\run-reader-harness.mjs` passed.
- GREEN/WHITESPACE: `git diff --check` passed.
- GREEN/HARNESS: `epub-native-drag-preview-underlay` passed against `tmp\reader-live\book-3809-file-426.epub`; output `tools\reader-harness\output\epub-native-drag-preview-underlay.json` recorded `curlSnapshots=front`, `curlSnapshotFront=true`, a live front snapshot with `textLength=14`, and single-page mode suppressing the reverse snapshot.
- OBSERVED/FIXED: an intermediate harness run exposed stale layer-level snapshot readiness after `srcdoc` loaded; the runtime now refreshes layer dataset state from both iframe `onload` and a next-frame content probe.

Remaining:
- This host/browser proof shows the snapshot layer exists and is clipped/cleaned correctly. Physical-device acceptance still needs Stage 6F to judge whether the curl snapshot looks and feels faithful during touch drag on the phone/tablet.

### Stage 6D.3: Deterministic Texture Motion

**Purpose:** Close the remaining paper/edge texture transition weirdness as a renderer-owned movement contract, not another visual opacity tweak.

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-motion.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js` only if CSS position serialization changes.
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js` only if drag progress no longer exposes enough direction/progress state.
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- Test: `tools/reader-harness/src/run-reader-harness.mjs`
- Validate: `tools/reader-harness/src/reader-trace-assertions.mjs`

**Acceptance:**
- Texture and border texture movement must be a deterministic function of logical page identity plus drag/turn direction.
- During a same-direction next/previous walk, texture position must not invert sign when Foliate crosses section boundaries.
- During drag preview, texture movement must follow the visual page movement axis, not relocation event order.
- Texture reset is allowed only after a committed page identity changes; relocation noise during animation must not create a visible opposite-direction pulse.
- Opacity, asset selection, and paper visual strength are explicitly out of this stage unless a guard proves they are part of the movement bug.

**TDD steps:**
- [x] Add failing source/host guards for the stale hard-coded boundary probe and the transient paginator document/style failures that aborted texture page-turn validation.
- [x] Extend the browser harness probe so it discovers a real visible section boundary from the active EPUB fixture and asserts drag/turn texture deltas keep the commanded direction.
- [x] Implement the smallest runtime change needed for the now-honest harness: harden the bundled paginator against transient non-element `documentElement`/style targets during page-turn preloading.
- [x] Run focused host tests, JS syntax checks, and the texture harness probes.
- [x] Run full `:composeApp:testAndroid`, `git diff --check`, and append compact validation results to `2026-06-13-komikku-reader-port-validation-log.md`.

Results:
- RED/HOST-FIRST: `ReaderRuntimePaperSurfaceTest.readerHarnessTextureFrontmatterTransitionDiscoversFixtureBoundary` failed while `epub-texture-frontmatter-transition` still searched for the Hobbit `Author's Note` text.
- RED/HARNESS-FIRST: after removing the stale search, `epub-texture-page-turns` exposed `Failed to execute 'getComputedStyle' on 'Window': parameter 1 is not of type 'Element'.`
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.androidPaginatorDoesNotThrowWhenDocumentElementIsTemporarilyUnavailable` and `ReaderRuntimeAssetsTest.androidPaginatorStyleHelperSkipsTransientNonElements` failed before the paginator resolved a safe style root and tolerated null/non-element style targets.
- GREEN/HARNESS: `texture-offset-logic`, `epub-texture-scroll`, `epub-texture-page-turns`, and `epub-texture-frontmatter-transition` passed against `tmp\reader-live\book-3809-file-426.epub`.

Remaining:
- This is browser/host proof for deterministic texture movement on the current production EPUB fixture. Release-device visual judgment is still required for perceived texture strength, edge degradation visibility, and drag feel.

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

### Stage 7B: Theta17 Staged Release Candidate

Status: complete; GitHub release published.

Purpose:
- Publish a coherent Android release candidate for the completed post-theta16 work instead of continuing local microfixes.
- Keep this stage limited to release identity, validation, documentation, commit/push/tag, and GitHub Actions release publication.
- Do not add new reader behavior inside this stage. If validation finds a functional bug, stop this release stage and open a new file-scoped implementation stage for that bug.

Scope:
- Package the completed Stage 8C release-login automation, Stage 4/5C Bindery Whispersync API updates, Stage 5C.3 Whispersync enjoyment orchestrator, Stage 6E.3 curl sheet roles, Stage 6D.3 deterministic texture motion support, and Stage 6C.3 settings overlay hierarchy.
- Use `v1.0.11-theta17` / `versionCode=445` unless the repository already has a newer release identity by the time this stage executes.
- Build only the Android release artifact through GitHub Actions. Do not invoke iOS packaging.

Files:
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- Modify scripts only if the release pipeline itself fails.

Validation commands:

```powershell
git fetch --all --prune
git rev-list --left-right --count fork/master...HEAD
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta17
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
```

Publication commands:

```powershell
git tag v1.0.11-theta17
.\scripts\publish-github-release.ps1 -Tag v1.0.11-theta17 -Branch codex/komikku-reader-backbone-eta64 -Background
```

Required before closure:
- [x] Confirm the branch contains the current `fork/master` before release work. `git rev-list --left-right --count fork/master...HEAD` reported `0 54`.
- [x] Bump Android release identity to `v1.0.11-theta17` / `versionCode=445`.
- [x] Verify the Android version identity with `scripts\verify-android-release-version.ps1`.
- [x] Run the full `:composeApp:testAndroid` gate.
- [x] Run `git diff --check`.
- [x] Commit and push the release identity plus this Stage 7B evidence. Commit `a37cfcd4`.
- [x] Create and push the `v1.0.11-theta17` tag.
- [x] Trigger the Android-only GitHub Actions release path. Run `28390623899` completed successfully; Android APK succeeded and iOS jobs were skipped.
- [x] Record the release URL, workflow run, commit, and asset in the validation log.

### Stage 7C: Theta22 Staged Release Candidate

Status: complete; GitHub release published.

Purpose:
- Package the synced reader/Whispersync branch after merging current `fork/master` through `v1.0.11-theta21`.
- Include the post-theta17 reader harness and runtime work in one Android release candidate instead of publishing isolated docs-only checkpoints.
- Keep this stage limited to release identity, validation, documentation, commit/push/tag, and GitHub Actions release publication.

Scope:
- Merge the current public master fixes for generated mix artwork and release identity before packaging reader work.
- Use `v1.0.11-theta22` / `versionCode=450`, because `v1.0.11-theta21` is already the latest public release.
- Build only the Android release artifact through GitHub Actions. Do not invoke iOS packaging.

Files:
- Modify: `androidApp/build.gradle.kts`
- Modify: `scripts/adb-whispersync-enjoyment.ps1`
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- Modify scripts only if release or validation tooling itself fails.

Validation commands:

```powershell
git fetch fork master codex/komikku-reader-backbone-eta64
git merge fork/master --no-edit
git rev-list --left-right --count fork/master...HEAD
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta22
$tokens = $null; $parseErrors = $null; $null = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path .\scripts\adb-whispersync-enjoyment.ps1), [ref]$tokens, [ref]$parseErrors); if ($parseErrors.Count -gt 0) { $parseErrors | ForEach-Object { Write-Error $_.Message }; exit 1 } else { 'PowerShell parser OK' }
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
```

Results:
- GREEN/SYNC: `fork/master` merged cleanly into `codex/komikku-reader-backbone-eta64`; `git rev-list --left-right --count fork/master...HEAD` reported `0 11` after the merge.
- GREEN/PUSH-BRANCH: pushed the synced work branch to `fork/codex/komikku-reader-backbone-eta64`.
- GREEN/VERSION: Android release identity is `versionCode=450`, `versionName=v1.0.11-theta22`.
- GREEN/SCRIPT-PARSE: `scripts\adb-whispersync-enjoyment.ps1` parsed successfully after updating the default expected version.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed on the theta22 release identity.
- GREEN/DIFF: `git diff --check` passed.
- GREEN/GITHUB-RELEASE: GitHub Actions run `28405398651` completed successfully for tag `v1.0.11-theta22`; Android APK succeeded, iOS jobs were skipped, and the release was published at `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-theta22` with asset `Navic.apk` (`sha256:045c82d2e5f0fb92cd1f3a2cb33ff94f5f74ea2bb60b410dec94d614a734177c`).

Required before closure:
- [x] Confirm the branch contains the current `fork/master` before release work.
- [x] Bump Android release identity to `v1.0.11-theta22` / `versionCode=450`.
- [x] Verify the Android version identity with `scripts\verify-android-release-version.ps1`.
- [x] Run the full `:composeApp:testAndroid` gate.
- [x] Run `git diff --check`.
- [x] Commit and push the theta22 release identity plus this Stage 7C evidence. Commit `1c990b57`.
- [x] Create and push the `v1.0.11-theta22` tag.
- [x] Trigger the Android-only GitHub Actions release path. Run `28405398651` completed successfully; Android APK succeeded and iOS jobs were skipped.
- [x] Record the release URL, workflow run, commit, and asset in the validation log.

## Current First Focus

Use `v1.0.11-theta22` as the current public release baseline. Deep reader and Whispersync release-package behavior still requires either a logged-in release device/profile or the Stage 8C release-login automation path with real Navic/Navidrome credentials. That boundary does not block development validation: `bindery-debug.env` and `darkaxt.navic.readerdev` are the required path for seeded Bindery/Whispersync implementation runs. Keep readerdev/browser evidence labeled as implementation/runtime proof, not release proof.

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
- `bindery-debug.env` is sufficient for readerdev Whispersync validation because the debug launcher bypasses the library/login route and injects the Bindery resource/sidecar route directly.
- `navic-release-login.env` is only required for public `darkaxt.navic` release-package validation on a fresh emulator profile.
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

### Stage 8B: Theta16 Release Validation Baseline

Status: complete for release-package install/app-shell; deep reader/Whispersync release validation remains blocked by missing release login/data on the emulator.

Scope:
- Reuse the published `v1.0.11-theta16` `Navic.apk` from GitHub release assets.
- Install it on the connected emulator as `darkaxt.navic`.
- Verify `versionName=v1.0.11-theta16`, `versionCode=444`, foreground launch, screenshot capture, and WebView debug socket availability.
- Run only deterministic checks that are meaningful while the release package is not logged in.
- Record the release login boundary explicitly so the next product stages do not claim release reader behavior from readerdev evidence.

Commands:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ApkPath releases\v1.0.11-theta16\Navic.apk -ExpectedVersionName v1.0.11-theta16 -ArtifactDir captures\reader-smoke\theta16-release-install -CaptureReaderDiagnostics
```

Results:
- GREEN/RELEASE-INSTALL: published `v1.0.11-theta16` `Navic.apk` installed on `emulator-5554` as `darkaxt.navic`; `package-version.txt` reports `versionCode=444`, `versionName=v1.0.11-theta16`, and `lastUpdateTime=2026-06-29 16:55:20`.
- GREEN/RELEASE-SHELL: `focused-window.txt` confirms `darkaxt.navic/paige.navic.androidApp.MainActivity`.
- GREEN/WEBVIEW-SOCKET: the smoke runner found a WebView DevTools socket (`@webview_devtools_remote_28774`), so the package is debuggable enough for browser-side probes once a reader route is reachable.
- BLOCKED/RELEASE-READER: the release package on the emulator lands on the Navidrome login form. `reader-diagnostics-summary.txt` has zero reader/touch/texture events, which is correct for that state and cannot be used as reader evidence.

Closure:
- [x] Install the published theta16 APK and prove the installed release version.
- [x] Capture launch/shell evidence for `darkaxt.navic`.
- [x] Record that theta16 release-package reader/Whispersync validation still requires release login/data.
- [ ] Add a release-login automation path or validate on a logged-in physical device before claiming release-level reader/Whispersync usability.

### Stage 8C: Release Login And Reader Route Automation

Status: complete for automation/detection; real release-reader validation still requires real credentials in the ignored env file.

Purpose:
- Close the release-validation blocker where the public package `darkaxt.navic` can be installed and inspected but cannot reach the reader/Whispersync routes on the emulator because it lands on the Navidrome login page.
- Keep readerdev and release evidence separate: readerdev remains the seeded implementation lab, while release validation must either log in to the public package or fail with an explicit credential-boundary message.
- Do not expose credentials in logs, command output, screenshots, committed files, or validation docs.

Files:
- Add: `scripts/adb-release-login.ps1`
- Add: `navic-release-login.env.example`
- Modify: `.gitignore`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md` only after a real run.

Credential contract:
- Local real credentials live in an ignored env file, defaulting to `navic-release-login.env`.
- Accepted URL keys: `NAVIC_INSTANCE_URL`, `NAVIDROME_BASE_URL`, or `NAVIDROME_URL`.
- Accepted username keys: `NAVIC_USERNAME` or `NAVIDROME_USERNAME`.
- Accepted password keys: `NAVIC_PASSWORD` or `NAVIDROME_PASSWORD`.
- The script may print key names and artifact paths, but it must never print credential values.

TDD steps:
- [x] Add a failing source guard for the release-login script, ignored env file, example env file, and no-secret-print contract.
- [x] Implement `scripts/adb-release-login.ps1` with selected-device ADB routing, UIAutomator login-screen detection, field filling, and artifact capture.
- [x] Make the script fail loudly when required key groups are missing, listing the missing key names without printing values.
- [x] Run the focused host guard, PowerShell parser validation, `git diff --check`, and then a real emulator dry run that proves either login-screen detection or missing-credential reporting.
- [x] Record Stage 8C evidence in the validation log.
- [x] Commit and push Stage 8C before returning to reader/Whispersync behavior work.

Results:
- RED/HOST-FIRST: `ReaderDevEnvironmentContractTest.releasePackageReaderValidationHasCredentialSafeLoginAutomation` failed before the release-login script and env example existed.
- RED/HOST-FIRST: the same guard failed again until the script exposed detection-only mode so login-screen detection could run without submitting placeholder credentials.
- GREEN/HOST: the focused source guard passed after adding `scripts/adb-release-login.ps1`, `navic-release-login.env.example`, ignored `navic-release-login.env`, selected-serial routing, no-secret logging, and UIAutomator login-screen detection.
- GREEN/PARSER: PowerShell parser validation passed for `scripts\adb-release-login.ps1`.
- GREEN/ENV: `-ValidateEnvOnly` prints only redacted credential state for complete env files and fails with key names when required key groups are missing.
- GREEN/MULTIDEVICE: with both `RFCY80551LT` and `emulator-5554` connected, the script now fails before UI actions and tells the operator to pass `-DeviceSerial`.
- GREEN/EMULATOR-DETECT: `-DeviceSerial emulator-5554 -NoLaunch -DetectOnly` detected the public package login screen and wrote `captures\release-login\stage8c-detect-only\navic-release-login-window.xml`.

Remaining:
- To convert this from detection-ready to deep release-reader validation, provide an ignored `navic-release-login.env` or another env file with real Navic/Navidrome credentials, then run release login and the reader/Whispersync smoke probes against `darkaxt.navic`.

### Stage 8D: Theta17 Release Validation Baseline

Status: complete for release-package install/app-shell; deep reader/Whispersync release validation remains blocked by missing release login/data on the emulator.

Purpose:
- Convert the published `v1.0.11-theta17` release from "available" into a validated baseline.
- Keep release-package evidence separate from readerdev implementation evidence.
- Use this stage to decide whether the next implementation work is release-login routing, release reader launch, Whispersync behavior, or physical visual acceptance.

Files and scripts:
- Use: `releases\v1.0.11-theta17\Navic.apk`
- Use: `scripts\adb-reader-smoke.ps1`
- Use: `scripts\adb-release-login.ps1`
- Use: `scripts\adb-whispersync-enjoyment.ps1` only after `darkaxt.navic` can reach a paired reader route.
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- Modify: `docs/superpowers/specs/2026-06-18-whispersync-design.md` only if release evidence changes the Whispersync status.

Commands:

```powershell
gh release download v1.0.11-theta17 --repo Darkaxt/Navic --pattern Navic.apk --dir releases\v1.0.11-theta17 --clobber
adb devices
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial <serial> -ApkPath releases\v1.0.11-theta17\Navic.apk -ExpectedVersionName v1.0.11-theta17 -ArtifactDir captures\reader-smoke\theta17-release-install -CaptureReaderDiagnostics
.\scripts\adb-release-login.ps1 -DeviceSerial <serial> -Package darkaxt.navic -EnvFile navic-release-login.env -ArtifactDir captures\release-login\theta17-release-login
```

Credential boundary:
- If `navic-release-login.env` is absent, run `adb-release-login.ps1 -ValidateEnvOnly` and record the missing credential boundary.
- If the release package is already logged in on a physical device, skip credential submission and run smoke/Whispersync probes against that device.
- Do not claim release-level reader or Whispersync success from `darkaxt.navic.readerdev`.

Results:
- GREEN/RELEASE-DOWNLOAD: published `Navic.apk` was downloaded to `releases\v1.0.11-theta17\Navic.apk`.
- GREEN/RELEASE-INSTALL: `adb-reader-smoke.ps1` installed and foregrounded `darkaxt.navic` on `emulator-5554`; `package-version.txt` reports `versionCode=445`, `versionName=v1.0.11-theta17`, and `lastUpdateTime=2026-06-29 20:41:25`.
- GREEN/WEBVIEW-SOCKET: the release package exposed WebView DevTools sockets, so browser-side release probes are possible after a reader route is reachable.
- GREEN/LOGIN-DETECT: `adb-release-login.ps1 -DetectOnly` detected the Navidrome login screen and wrote `captures\release-login\theta17-release-detect-only\navic-release-login-window.xml`.
- BLOCKED/RELEASE-READER: the emulator release package is not logged in and remains on the login form. Reader shell, EPUB/PDF, selection, search, style, and Whispersync behavior cannot be claimed as release-package evidence from this emulator state.

Closure:
- [x] Download or reuse the published theta17 APK.
- [x] Install and prove `versionName=v1.0.11-theta17`, `versionCode=445`, foreground package, screenshot capture, and WebView debug socket availability.
- [x] Run release-login detection.
- [x] Record that deep release reader/Whispersync checks require login data or a logged-in physical device.
- [x] Record the result and next implementation stage in the validation log.
- [ ] Run release-package reader smoke/matrix checks once a release package can reach a reader route.
- [ ] Run release-package Whispersync enjoyment checks once a paired Whispersync route is reachable.

### Stage 8E: Theta22 Release Validation Baseline

Status: complete for release-package install/app-shell after smoke focus-gate correction; deep reader/Whispersync release validation remains blocked by missing release login/data on the emulator.

Purpose:
- Convert the published `v1.0.11-theta22` APK into a usable release baseline.
- Keep first-launch emulator ANR evidence, smoke-script harness behavior, and release-reader login boundaries separate.
- Avoid claiming release-reader or Whispersync behavior from a login-screen shell smoke.

Commands:

```powershell
gh release download v1.0.11-theta22 --repo Darkaxt/Navic --pattern Navic.apk --dir releases\v1.0.11-theta22 --clobber
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ApkPath releases\v1.0.11-theta22\Navic.apk -ExpectedVersionName v1.0.11-theta22 -ArtifactDir captures\reader-smoke\theta22-release-install -CaptureReaderDiagnostics
adb -s emulator-5554 shell dumpsys dropbox --print data_app_anr
adb -s emulator-5554 shell am force-stop darkaxt.navic.readerdev
adb -s emulator-5554 shell am force-stop darkaxt.navic
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ApkPath releases\v1.0.11-theta22\Navic.apk -ExpectedVersionName v1.0.11-theta22 -ArtifactDir captures\reader-smoke\theta22-release-clean-retry -CaptureReaderDiagnostics
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeFailsWhenFocusedWindowDoesNotBelongToRequestedPackage" --console=plain
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -DeviceSerial emulator-5554 -ApkPath releases\v1.0.11-theta22\Navic.apk -ExpectedVersionName v1.0.11-theta22 -ArtifactDir captures\reader-smoke\theta22-release-clean-retry-fixed-gate -CaptureReaderDiagnostics
```

Results:
- GREEN/RELEASE-DOWNLOAD: published `Navic.apk` was downloaded to `releases\v1.0.11-theta22\Navic.apk`; size is `21052843` bytes.
- GREEN/RELEASE-INSTALL: the first theta22 install wrote `package-version.txt` with `versionCode=450`, `versionName=v1.0.11-theta22`, and `lastUpdateTime=2026-06-30 01:13:27`.
- RED/FIRST-LAUNCH-ANR: initial release smoke hit an emulator ANR while opening `darkaxt.navic/paige.navic.androidApp.MainActivity`. DropBox recorded `Input dispatching timed out` with ErrorId `0b4f04f2-d7a9-44e1-a26b-698e42e83c6a`; the app main thread was in Android `HardwareRenderer.setStopped()` during first draw, while ART JIT/profile work and emulator CPU/IO pressure were high. This was recorded as launch evidence, not an app code fix target.
- RED/HARNESS-FOCUS: after stopping `darkaxt.navic.readerdev` and `darkaxt.navic`, the clean retry launched the activity but failed the smoke focus guard because `mCurrentFocus=null` while `mFocusedApp` already pointed at `darkaxt.navic/paige.navic.androidApp.MainActivity`.
- GREEN/HOST-FIRST: `ReaderRuntimeAssetsTest.adbReaderSmokeFailsWhenFocusedWindowDoesNotBelongToRequestedPackage` was made red for the missing `mFocusedApp` acceptance and then green after `adb-reader-smoke.ps1` matched both `mCurrentFocus` and `mFocusedApp`, consistent with `install-reader-dev.ps1`.
- GREEN/RELEASE-SHELL: `captures\reader-smoke\theta22-release-clean-retry-fixed-gate` passed. `focused-window.txt` confirms `mCurrentFocus=Window{... darkaxt.navic/paige.navic.androidApp.MainActivity}` and `mFocusedApp=ActivityRecord{... darkaxt.navic/paige.navic.androidApp.MainActivity ...}`. Full log scan found no `ANR`, `FATAL EXCEPTION`, `AndroidRuntime`, `Application Not Responding`, or `Input dispatching timed out`.
- BLOCKED/RELEASE-READER: screenshot `captures\reader-smoke\theta22-release-clean-retry-fixed-gate\screen.png` shows the Navidrome login form (`Log in`, `Instance URL`, `Username`, `Password`). `reader-diagnostics-summary.txt` has zero reader/touch/texture events. No release-package reader, EPUB/PDF, selection, search, style, or Whispersync behavior can be claimed from this emulator state.

Closure:
- [x] Download and install the published theta22 APK.
- [x] Prove installed release version and app-shell foreground ownership.
- [x] Diagnose the initial ANR using DropBox evidence before changing code.
- [x] Fix the smoke-script false negative with a focused red/green host test.
- [x] Record that deep release reader/Whispersync checks still require a logged-in release package or `navic-release-login.env`.

### Stage 8F: Explicit Start Native Cover And Prepared Matrix Route

Status: complete for readerdev implementation/runtime validation.

Purpose:
- Prevent explicit readerdev route starts, especially `StartProgress=0`, from being overridden by stale local resume state.
- Make failed native-cover smokes useful by capturing logcat before hard assertions throw.
- Let `adb-reader-komikku-matrix.ps1 -PrepareReaderLaunch` prepare a concrete production EPUB/Whispersync route instead of relying on whichever book/page the emulator currently has open.

Files:
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOpenRequest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/reader/ReaderOpenRequestFactoryTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt`
- Modify: `scripts/adb-reader-smoke.ps1`
- Modify: `scripts/adb-reader-komikku-matrix.ps1`
- Modify: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

Results:
- RED/HOST-FIRST: `ReaderOpenRequestFactoryTest.openRequestKeepsExplicitRouteStartProgressAtZeroOverLocalResume` failed because `bestReaderStartLocator(...)` let a later local resume locator override explicit route `startProgress=0`.
- FIXED: explicit route locators are authoritative in `Screen.Reader.toReaderEngineOpenRequest()`; fallback saved/local merging remains unchanged when the route does not provide start state.
- GREEN/HOST: focused `ReaderOpenRequestFactoryTest` passed.
- FIXED/HARNESS: `adb-reader-smoke.ps1` now writes full and reader-filtered logcat before native-cover/neutral-state assertion failures.
- RED/HOST-FIRST: `ReaderDevEnvironmentContractTest.komikkuMatrixCanPrepareNativeCoverStartStateBeforeCoverChecks` failed until the matrix script exposed concrete prepared route fields.
- FIXED/HARNESS: `adb-reader-komikku-matrix.ps1` now passes publication URL, resource href, book id, title, kind, format, start href/CFI, and Whispersync sidecar/audiobook fields into `install-reader-dev.ps1`.
- GREEN/EMULATOR: `captures\reader-smoke\stage8f-explicit-start0-native-cover-fixed-20260630` proved `nativeShellCoverVisible=True` after launching book `3809` with `StartProgress=0`.
- GREEN/MATRIX: `captures\reader-komikku-matrix\stage8f-prepared-bastille-start0-fixed-20260630` passed all 12 prepared readerdev matrix rows with `No matrix failures.`

Closure:
- [x] Reproduce the stale-progress native-cover failure from an explicit route start.
- [x] Add and pass a route-start host regression test.
- [x] Add and pass a prepared-matrix contract guard.
- [x] Run a real readerdev native-cover smoke against the paired Bindery route.
- [x] Run the prepared Komikku matrix against the same concrete route.
- [x] Record Stage 8F evidence in the validation log.

### Stage 8G: Theta23 Public Release

Status: complete.

Purpose:
- Publish Stage 8F as a public Android APK so device testing can validate the explicit-start/native-cover fix from a signed release.

Results:
- GREEN/VERSION: Android release identity is `v1.0.11-theta23` / `versionCode=451`.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed on the theta23 identity.
- GREEN/GITHUB-ACTIONS: tag push run `28411168672` succeeded. Android release build and signing passed; iOS jobs were skipped.
- GREEN/PUBLIC-RELEASE: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-theta23` published `Navic.apk` with `sha256:7d37003372e0eac35ad5d56249b803420e8dbaff3cd670ae557c7998a520e515`.

Closure:
- [x] Bump release identity from theta22 to theta23.
- [x] Commit and push validated source commit `cb43a0b9`.
- [x] Push tag `v1.0.11-theta23`.
- [x] Verify GitHub Actions release publication.

### Stage 8H: Font Size / Tablet Typography Evidence

Status: evidence-only complete; no production patch made.

Purpose:
- Re-check the Tab S9 Ultra complaint that the font-size control changed headings but not EPUB body text.
- Avoid another typography patch unless the current runtime actually reproduces the body-font ownership failure.

Commands:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
adb devices
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size-publisher-styles
```

Results:
- GREEN/SYNTAX: `navic-reader-typography.js` parses cleanly.
- GREEN/ADB: `emulator-5554` is connected and readerdev exposes WebView DevTools for PID `13561`.
- GREEN/FONT-SIZE: the readerdev WebView reported root, body, and paragraph font sizes moving from `16px` at 100% to `22.4px` at 140%.
- GREEN/EXISTING-EPUB-PROSE: ten existing EPUB paragraph elements, including `P.fut_toc1` and `P.fut_toc`, also moved from `16px` to `22.4px`; `existingProseDelta=6.4`.
- GREEN/PUBLISHER-STYLES: injected publisher inline and class-important prose both moved from `16px` to `22.4px`; `publisherParagraphDelta=6.4` and `publisherClassImportantDelta=6.4`.

Closure:
- [x] Do not patch font-size ownership from the old symptom alone; current source and readerdev runtime scale EPUB body prose correctly.
- [x] Continue with tablet page composition and natural margin diagnostics, since the remaining visual complaint is likely page box, column, or content layout rather than font-size propagation.

### Stage 8I: Anx Auto Column Composition

Status: implementation/runtime validation complete.

Purpose:
- Fix the tablet page composition regression where large portrait surfaces had enough room for more text, but Navic still forced one-column pagination before Foliate could apply Anx auto-column behavior.
- Keep Komikku in charge of shell layout, while preserving Anx/Foliate paging semantics for EPUB composition.

Root cause:
- Anx defaults `maxColumnCount=0` and `columnThreshold=720`; Foliate's paginator interprets `0` as automatic column selection based on available page size.
- Navic introduced `readerEffectiveMaxColumnCount(...)`, which converted auto mode into an explicit `1` in portrait and `2` in landscape.
- That pre-collapse blocked the Foliate paginator from combining same-section content on large portrait tablets, causing excessive unused width/height and unstable perceived margins.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs adaptive-page-box-logic
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest.everyAnxStyleDimensionIsDocumentedInKnownGaps" --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -ReaderPublicationUrl https://bindery.remaxku.eu/book/3809 -ReaderResourceHref "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -ReaderWhispersyncSidecarUrl /opds/books/3809/sync/8 -ReaderWhispersyncArtifactId 8 -ReaderWhispersyncAudiobookId 34 -ReaderWhispersyncAudiobookBookFileId 633 -ReaderWhispersyncAudiobookTitle "Bastille vs. the Evil Librarians" -RequireReaderLaunch -Capture
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe page-box
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe visible-page-content
```

Results:
- RED/HARNESS-FIRST: before the runtime patch, `adaptive-page-box-logic` failed because portrait auto composition returned `maxColumnCount="1"` for a `1232x1974` viewport instead of preserving Anx auto mode.
- FIXED/RUNTIME: `readerAdaptiveFoliatePageBox(...)` now passes `readerMaxColumnCountValue(settings)` through directly and removes `readerEffectiveMaxColumnCount(...)`.
- GREEN/HARNESS: `adaptive-page-box-logic` now expects and receives `maxColumnCount="0"` for phone portrait, Tab S9 Ultra portrait, and landscape auto mode; explicit overrides to `1` or `2` still pass through.
- GREEN/SYNTAX: `navic-reader-typography.js` passed `node --check`.
- GREEN/HOST: focused `FoliateAnxParityTest.everyAnxStyleDimensionIsDocumentedInKnownGaps` passed and now rejects reintroducing the pre-collapse helper.
- GREEN/READERDEV: `install-reader-dev.ps1` built and launched `darkaxt.navic.readerdev` on `emulator-5554`, reached `publicationReady`, and captured `captures\reader-dev\reader-dev-20260630-040407.png`.
- GREEN/WEBVIEW-PAGE-BOX: `page-box` probe on readerdev reported viewport `1232x1974`, full renderer rect `1232x1974`, `maxInlineSize="1232px"`, `maxBlockSize="1974px"`, `maxColumnCount="0"`, `columnThreshold="720px"`, `topMargin="90px"`, and `bottomMargin="50px"`.
- INCONCLUSIVE/CONTENT-PROBE: `visible-page-content` returned zero visible text while the current readerdev state was cover/shifted paginator; do not use that probe as evidence for text-page density until it is made route-aware.

Closure:
- [x] Preserve Anx auto-column semantics instead of resolving them inside Navic.
- [x] Add a source guard that rejects reintroducing `readerEffectiveMaxColumnCount(...)`.
- [x] Update the harness expectations so phone, tablet, and landscape auto mode remain delegated to Foliate.
- [x] Validate the patched runtime in readerdev with WebView page-box evidence.

### Stage 8J: Bindery Fullscreen Cover Contract

Status: host validation complete; waiting on Bindery to expose real derived cover assets for end-to-end validation.

Purpose:
- Prepare Navic for Bindery-owned AI/outpainted fullscreen covers without moving generation, caching, or provider calls into the Android client.
- Keep the existing native shell-cover reader surface as the only cover renderer: prefer an optional Bindery fullscreen cover URL when the manifest exposes one, otherwise keep the EPUB-extracted fallback.

Contract:
- Bindery may expose the derived cover through manifest properties such as `fullscreenCoverUrl`, `extendedCoverUrl`, `expandedCoverUrl`, or `shellCoverUrl`.
- Bindery may also expose the derived cover as an OPDS image/link with rel tokens such as `fullscreen-cover`, `extended-cover`, `expanded-cover`, or `shell-cover`.
- Regular `rel=cover` images remain ordinary covers and must not be treated as fullscreen shell covers.
- Navic does not call an AI service at runtime.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest.ebookVersionRowsCarryBinderyFullscreenCoverRenditionToReaderRoutes" --tests "paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest.regularCoverImagesDoNotBecomeFullscreenShellCoverRoutes" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidPublicationRuntimePrefersBinderyFullscreenCoverOverExtractedEpubCover"
```

Results:
- RED/HOST-FIRST: the focused route/runtime tests first failed because `BinderyBookVersionRow.fullscreenCoverHref` and `Screen.Reader.fullscreenCoverUrl` did not exist.
- GREEN/HOST: the same focused test set passed after adding route propagation and Android runtime preference.
- IMPLEMENTED: `binderyBookVersionRows(...)` now attaches the manifest fullscreen cover href to ebook/readaloud/audiobook rows, reader destinations carry the absolute `fullscreenCoverUrl`, and `ReaderPublicationRuntimeHost.android.kt` prefers that URL over `resolved.shellCoverUrl`.

Closure:
- [x] Navic can consume a Bindery-provided fullscreen shell-cover URL.
- [x] Navic falls back to the existing EPUB cover extraction when the fullscreen asset is absent.
- [x] Ordinary OPDS cover images are not promoted into fullscreen shell-cover routes.
- [ ] End-to-end release/device validation once Bindery exposes a real generated fullscreen cover for a book.

### Stage 8K: Theta24 Reader Release Candidate

Status: complete.

Purpose:
- Publish a coherent Android release candidate for the completed post-theta23 reader work instead of holding the tablet composition and fullscreen-cover contract behind local-only commits.
- Include Stage 8I Anx auto-column composition parity and Stage 8J Bindery fullscreen-cover route support.
- Keep release evidence honest: this release is worth device validation for tablet page composition and future Bindery fullscreen-cover assets, but it does not by itself close the still-open release-device Whispersync enjoyment gate.

Scope:
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`

Required checks:
- Verify `fork/master` has no missing commits before the release bump.
- Run `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta24` red before the bump and green after the bump.
- Run `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain`.
- Run focused host tests for the changed reader domains.
- Run `git diff --check`.
- Commit and push the theta24 source commit.
- Create and push tag `v1.0.11-theta24`.
- Verify the GitHub Actions Android release publishes `Navic.apk`.

Results:
- RED/VERSION-FIRST: `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta24` failed while `androidApp/build.gradle.kts` still declared `v1.0.11-theta23`.
- GREEN/SYNC: `fork/master` had no missing commits; `git rev-list --left-right --count fork/master...HEAD` reported `0 18`.
- GREEN/VERSION: after the release identity bump, `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta24` passed.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed on the theta24 identity.
- GREEN/FOCUSED-HOST: `.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest.everyAnxStyleDimensionIsDocumentedInKnownGaps" --tests "paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --console=plain` passed.
- GREEN/DIFF: `git diff --check` passed.
- GREEN/PUSH: source commit `3a0fe11f` was pushed to `fork/codex/komikku-reader-backbone-eta64`.
- GREEN/GITHUB-ACTIONS: tag push run `28415104206` succeeded. Android release build and signing passed; iOS jobs were skipped.
- GREEN/PUBLIC-RELEASE: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-theta24` published `Navic.apk` with `sha256:eb2c4e32d7da39e69cfb1bbaf622bc34bf57d111d0121e8f527c25f3d4bcdd9f`.

Closure:
- [x] Bump Android release identity to `v1.0.11-theta24` / `versionCode=452`.
- [x] Verify the Android version identity.
- [x] Run the required Gradle and diff checks.
- [x] Commit and push the theta24 release identity.
- [x] Create and push the `v1.0.11-theta24` tag.
- [x] Verify GitHub Actions release publication.

### Stage 8L: Native Chapter Rail Endpoint Gate

Status: host guard and isolated readerdev emulator gate complete; keep `-OnlyRailEndpointChecks` as the required rail endpoint runtime proof path.

Purpose:
- Close the remaining chapter-local progress-rail validation gap from the Komikku reader spec: the visible native `Chapter page slider` must be able to reach the first and last pages of the current chapter.
- Keep this as a harness/evidence improvement. No reader UI, controller, texture, tap, or Whispersync runtime behavior changes are part of this slice.
- Preserve the Stage 6B boundary: source/DevTools endpoint checks are useful, but the real gate must exercise the native rail control that the user touches.

Scope:
- Modify: `scripts/adb-reader-smoke.ps1`
- Modify: `scripts/adb-reader-komikku-matrix.ps1`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`

Results:
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.adbReaderSmokeCanRequireNativeChapterRailEndpointAfterPostActionProbe` failed while the smoke script had no post-action chapter endpoint assertion and the matrix had no native rail endpoint rows.
- FIXED/HARNESS: `adb-reader-smoke.ps1` now accepts `-RequirePostActionChapterPageEndpoint start|end`, reads the post-action `location-snapshot`, requires numeric `chapterPageIndex` and `chapterPageCount`, and fails if the native rail tap did not land on the requested endpoint.
- FIXED/MATRIX: `adb-reader-komikku-matrix.ps1` now exposes `-IncludeRailEndpointChecks` and adds `chapter-rail-native-start` / `chapter-rail-native-end` rows that tap the visible `Chapter page slider` at `0.0` and `1.0`, then assert the post-action location endpoint.
- RED/RUNTIME: the first full-matrix attempt with `-IncludeRailEndpointChecks` failed because the current EPUB state was a one-page copyright section with no visible `Chapter page slider`.
- RED/RUNTIME: the second full-matrix attempt failed after earlier tap/texture rows navigated the WebView into an external EPUB link (`Free Download Books https://oceanofpdf.com/`), proving rail endpoint validation must be isolated from the general tap walk.
- RED/RUNTIME: the first isolated prepared run reached the native shell cover, but the rail row's pre-probe swipe did not mount Foliate; `chapter-progress-endpoints` failed with `Missing foliate-view`.
- FIXED/MATRIX: `adb-reader-komikku-matrix.ps1` now exposes `-OnlyRailEndpointChecks`, runs only the baseline plus native rail endpoint rows, forces `chapter-progress-endpoints` before rail taps to select a multi-page chapter, and uses a pre-probe center tap to enter the Foliate content surface from the native cover.
- GREEN/FOCUSED-HOST: `.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanRequireNativeChapterRailEndpointAfterPostActionProbe" --console=plain` passed after the harness wiring.
- GREEN/BROADER-HOST: `.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest" --console=plain` passed after the plan edit.
- GREEN/SCRIPT-SYNTAX: both edited PowerShell scripts parsed cleanly through `System.Management.Automation.Language.Parser`.
- GREEN/DIFF: `git diff --check` passed.
- GREEN/READERDEV-EMULATOR: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -PrepareReaderLaunch -OnlyRailEndpointChecks -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\stage8l-rail-endpoints-only-current-retry3-20260630` passed on installed readerdev `v1.0.11-theta23`.
- GREEN/READERDEV-EVIDENCE: post-action probes landed on `OEBPS/Text/Chapter-37.xhtml` with `chapterPageIndex=0, chapterPageCount=81` for start and `chapterPageIndex=80, chapterPageCount=81` for end.

Closure:
- [x] Add a failing source guard for native rail endpoint matrix support.
- [x] Add smoke-script endpoint assertions against real post-action location snapshots.
- [x] Add opt-in matrix rows for native rail start/end endpoint checks.
- [x] Add isolated `-OnlyRailEndpointChecks` so endpoint validation cannot be polluted by external links or one-page sections.
- [x] Run the prepared readerdev emulator endpoint matrix from the native cover state.
- [x] Run the focused host guard.
- [x] Run a broader reader assets host guard and whitespace check before committing.
- [x] Run `adb-reader-komikku-matrix.ps1 -OnlyRailEndpointChecks` on emulator/device when the next runtime validation batch is needed.

### Stage 8M: Whispersync Gate Release Identity Guard

Status: complete; validation-infrastructure guard only, no public release.

Purpose:
- Prevent the Stage 5C Whispersync enjoyment runner from silently validating against an older installed APK after a reader release bump.
- Keep `scripts\adb-whispersync-enjoyment.ps1` default `ExpectedVersionName` derived from `androidApp/build.gradle.kts` so readerdev/release validation fails for stale package state instead of producing misleading probe evidence.

Scope:
- Modify: `scripts/adb-whispersync-enjoyment.ps1`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt`

Results:
- RED/HOST-FIRST: `ReaderDevEnvironmentContractTest.whispersyncEnjoymentGateDefaultExpectedVersionTracksAndroidReleaseIdentity` first failed while the Android app declared `v1.0.11-theta24` and the Whispersync enjoyment gate still defaulted to `v1.0.11-theta23`.
- RED/HOST-FIRST: the same guard then failed when the script merely hardcoded `v1.0.11-theta24`, proving the next release would drift again.
- FIXED/HARNESS: `adb-whispersync-enjoyment.ps1` now leaves `ExpectedVersionName` blank by default, derives it from `androidApp/build.gradle.kts`, and still lets callers override it explicitly.
- GREEN/FOCUSED-HOST: `.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderDevEnvironmentContractTest.whispersyncEnjoymentGateDefaultExpectedVersionTracksAndroidReleaseIdentity" --console=plain` passed.

Closure:
- [x] Add a failing source guard for stale Whispersync enjoyment gate package identity.
- [x] Update the runner to derive the default package identity from the Android release identity.
- [x] Run the focused host guard.
- [x] Run the broader contract host guard, PowerShell parser check, and `git diff --check` before committing.

### Stage 8N: Bindery Generated Fullscreen Cover Variants

Status: host guard complete; waiting for Bindery to expose real generated variant assets.

Purpose:
- Prepare Navic for Bindery-owned fullscreen/generated cover assets that may be emitted as multiple aspect-specific variants instead of a single `fullscreenCoverUrl`.
- Keep all AI/outpainting/generation/cache/provider work in Bindery. Navic only parses the OPDS/API metadata, chooses the closest generated cover for the target reader aspect when provided, and falls back to the current single fullscreen-cover URL or EPUB-extracted cover path.
- Preserve Stage 8J behavior: ordinary `rel=cover` images must not become native shell-cover routes.

Accepted metadata shape:
- Manifest `propertyValues.fullscreenCoverVariants`, `extendedCoverVariants`, `expandedCoverVariants`, or `shellCoverVariants` may contain string hrefs or objects.
- Object variants may expose `href`/`url` plus optional `width`/`widthPx`/`pixelWidth`, `height`/`heightPx`/`pixelHeight`, or direct `aspectRatio`.
- Existing manifest properties (`fullscreenCoverUrl`, `extendedCoverUrl`, `expandedCoverUrl`, `shellCoverUrl`) and image/link rels (`fullscreen-cover`, `extended-cover`, `expanded-cover`, `shell-cover`) remain supported.

Results:
- RED/HOST-FIRST: `BinderyBookVersionPolicyTest.ebookVersionRowsChooseClosestGeneratedFullscreenCoverVariantForReaderAspect` first failed because the reader destination builder had no `fullscreenCoverTargetAspectRatio` parameter and rows had no variant model.
- FIXED/POLICY: `BinderyBookVersionRow` now carries `fullscreenCoverVariants`, `binderyBookVersionRows(...)` parses generated variant arrays from manifest `propertyValues`, and row-to-reader route builders can choose the closest variant to an optional target aspect ratio.
- FIXED/COMPATIBILITY: normal ebook, readaloud, and Whispersync reader routes all use the same cover selection path; existing callers keep the current default behavior.
- GREEN/FOCUSED-HOST: `.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest.ebookVersionRowsChooseClosestGeneratedFullscreenCoverVariantForReaderAspect"` passed.
- GREEN/REGRESSION-HOST: the generated-variant test plus `ebookVersionRowsCarryBinderyFullscreenCoverRenditionToReaderRoutes` and `regularCoverImagesDoNotBecomeFullscreenShellCoverRoutes` passed.
- GREEN/BROADER-HOST: `.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest"` passed.
- GREEN/DIFF: `git diff --check` passed.

Closure:
- [x] Add a failing host guard for generated fullscreen cover variants.
- [x] Preserve single fullscreen-cover URL and rel support.
- [x] Reject ordinary cover images as fullscreen shell covers.
- [x] Support closest-variant selection when the route layer knows the target aspect ratio.
- [ ] Wire an actual layout-derived target aspect into production reader launches if/when the launch surface can provide it.
- [ ] End-to-end release/device validation once Bindery exposes real generated fullscreen cover variants for a book.
