# Komikku Reader Port Validation Log

This file holds concise test evidence for `2026-06-13-komikku-reader-port-design.md`.

The full historical log before compaction is preserved at:

- `docs/superpowers/specs/archive/2026-06-13-komikku-reader-port-design-full-log.md`

## 2026-06-18 Selection Action UI Wiring

Target:

- Host reader test suite.
- Spec gate: Anx selection payloads must produce controller/UI behavior, not only bridge/event types.
- Reference split: Anx owns selection/highlight/note behavior semantics; Komikku owns the native overlay surface.

Changes validated:

- `ReaderControllerState.selectionActions` now exposes native action availability from the current Anx selection payload.
- `SelectionCleared` clears controller selection instead of leaving an empty selection object that could keep stale UI state alive.
- `ReaderController.startSelectionNote`, `saveSelectionNote`, and `dismissSelectionNote` route note creation through controller state.
- `ReaderAnnotationState.addSelectionNote` stores note-bearing annotations and reuses the existing `ApplyAnnotations` engine command path.
- `KomikkuReaderSelectionActions` provides the native Highlight/Copy/Note overlay outside the WebView.
- `KomikkuReaderSelectionNoteDialog` provides a native note draft editor with save/dismiss routes.
- `ReaderScreen` wires Highlight and Note through `ReaderCoordinator`; Copy uses the Compose clipboard at the app boundary.

Red/green evidence:

- RED: focused host run failed because `ReaderSelectionActionState`, `selectionActions`, `ReaderSelectionNoteDraft`, and `startSelectionNote` did not exist.
- GREEN: focused host run passed for `selectionActionStateIsControllerOwnedAndClearedByEngine`, `selectionNotesStartNativeDraftWithoutEngineCommands`, and `commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted`.
- RED: focused host run failed because `saveSelectionNote` did not exist.
- GREEN: focused host run passed for `selectionActionStateIsControllerOwnedAndClearedByEngine`, `selectionNotesStartNativeDraftWithoutEngineCommands`, `selectionNotesSaveAsAnnotationsAndClearDraft`, and `commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted`.

Next required validation:

- Select normal EPUB text and footnote/reference text on Android, then verify the selection toolbar appears without toggling reader chrome.
- Verify Copy reaches the Android clipboard.
- Verify Highlight renders through Foliate annotations.
- Verify Note opens the native dialog, saves, and renders as an annotation; durable annotation persistence remains a separate follow-up unless covered by an existing store.
- Confirm logcat still shows `selectionChanged(footnote=..., pos=...)`.

## 2026-06-18 Phase 7 PDF/Font Parity Guards

Target:

- Host reader test suite.
- Spec gate: Phase 7 from `2026-06-17-anx-parity-7-phase-plan.md`.

Changes validated:

- Added `FoliatePdfAnxParityTest`, reading Anx `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614`, and anchored Navic `vendor/foliate-js/pdf.js` to that `makePDF(file)` contract.
- Added `ReaderFontSourceAnxParityTest`, reading Anx `lib/providers/fonts.dart`, `lib/service/font.dart`, and `lib/models/font_model.dart`.
- Recorded font source status honestly: local import/cache/WebView URL exists; remote font manifest fetch/download/cache/list/delete remains `Failing` and blocks full font-source parity.
- Updated stale shell-cover source guard to reflect the current first-readable Foliate sentinel offset, with behavior coverage in `ReaderChromeStateTest`.

Red/green evidence:

- RED: `FoliatePdfAnxParityTest.navicPdfRuntimeCitesTheAnxMakePdfContract` failed before the PDF runtime cited the Anx contract.
- RED: `ReaderFontSourceAnxParityTest.navicFontSourceParityIsLocalImportOnlyUntilRemoteManifestIsImplemented` failed before the Phase 7 plan documented remote manifest parity as `Failing`.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.FoliatePdfAnxParityTest" --tests "paige.navic.reader.ReaderFontSourceAnxParityTest"`.
- GREEN: `node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\pdf.js`.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderShellCoverTapsAndPreviousDoNotFallThroughToEpubCover" --tests "paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryInterceptsPreviousOnlyFromFirstReadablePage"`.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.*"` after the stale guard update.

Next required fix:

- Implement Anx-style remote font manifest support or keep it explicitly marked `Failing`; do not close Phase 7 as full font-source parity until remote fonts are implemented and guarded.

## 2026-06-18 Phase 7 Remote Font Cache Slice

Target:

- Host reader test suite.
- Spec gate: Phase 7 font-source parity from `2026-06-17-anx-parity-7-phase-plan.md`.
- Anx reference: `tmp\references\anx-reader\lib\providers\fonts.dart` and `tmp\references\anx-reader\lib\models\font_model.dart`.

Changes validated:

- `ReaderImportedFontCache` now fetches and parses an Anx-style remote font manifest through `ReaderRemoteFontManifestUrl`.
- Remote font entries can be downloaded through injected byte fetchers, staged through temp directories, moved into the reader font cache, listed, deleted, and exposed through WebView-safe `/reader-cache/fonts/remote/...` URLs.
- The Phase 7 parity guard now distinguishes cache-level support from full Settings/UI support. Full Anx font-source parity remains `Failing` because Settings does not yet expose remote font browsing/download/selection or per-file download progress.

Red/green evidence:

- RED: `ReaderImportedFontCacheTest.remoteFontManifestDownloadsCachesListsAndDeletesWebViewFonts` failed at compile because `fetchRemoteFontManifest`, `downloadRemoteFont`, `listRemoteFonts`, and `deleteRemoteFont` did not exist.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontCacheTest.remoteFontManifestDownloadsCachesListsAndDeletesWebViewFonts"`.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontCacheTest" --tests "paige.navic.reader.ReaderFontSourceAnxParityTest"`.

Next required fix:

- Add the Settings/UI remote font source flow before changing `Font remote manifest parity` from `Failing` to `Exists`.

## 2026-06-18 Phase 7 Remote Font Settings Slice

Target:

- Host reader test suite.
- Spec gate: Phase 7 font-source parity from `2026-06-17-anx-parity-7-phase-plan.md`.
- Anx reference: `tmp\references\anx-reader\lib\page\settings_page\subpage\fonts.dart` plus `tmp\references\anx-reader\lib\providers\fonts.dart`.

Changes validated:

- `ReaderFontImporter` now exposes remote font manifest entries, cached remote fonts, loading/error state, refresh, download, and delete hooks through the common settings boundary.
- Android `ReaderFontImporter` routes those hooks through `ReaderImportedFontCache.fetchRemoteFontManifest`, `downloadRemoteFont`, `listRemoteFonts`, and `deleteRemoteFont`.
- `SettingsEbooksScreen` now exposes remote font catalog refresh, remote font download rows, cached remote font selection, and per-font remote cache delete rows. Selecting a cached remote font sets `ReaderFontSourceCustom`, `readerCustomFontFamily`, and `readerCustomFontUrl`.
- iOS keeps explicit no-op unsupported hooks so the common interface remains platform-safe.
- `Font remote manifest parity` remains `Failing (UI partial)` because Anx-style per-file progress/pause/cancel is not surfaced yet.

Red/green evidence:

- RED: `ReaderFontSourceAnxParityTest.navicSettingsExposeRemoteFontManifestDownloadSelectionAndDeletion` failed because the common importer contract did not expose remote font list/download/delete hooks.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.ReaderFontSourceAnxParityTest.navicSettingsExposeRemoteFontManifestDownloadSelectionAndDeletion"`.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.ReaderFontSourceAnxParityTest"`.

Next required fix:

- Add download-state progress/pause/cancel equivalent to Anx `FontDownloads` before changing `Font remote manifest parity` from `Failing` to `Exists`.

## 2026-06-18 Post-Refactor Emulator Content Matrix

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 05:15:18`
- App focus: `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`

Command:

- `scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -ArtifactRoot captures\reader-komikku-matrix\post-refactor-content-check-20260618-054541 -NoLaunch -ContinueOnFailure`

Artifacts:

- Matrix root: `captures\reader-komikku-matrix\post-refactor-content-check-20260618-054541`
- Summary: `captures\reader-komikku-matrix\post-refactor-content-check-20260618-054541\reader-matrix-summary.csv`
- Failures: `captures\reader-komikku-matrix\post-refactor-content-check-20260618-054541\reader-matrix-failures.txt`

Passes:

- `baseline-current-reader`
- `center-tap-toggle`
- `native-long-press-center`
- `edge-tap-next`
- `drag-next`
- `texture-next-walk`
- `edge-tap-previous`
- `drag-previous`
- `texture-previous-walk`

Failures:

- `enter-readable-content`: the emulator was already in readable content, so no shell-cover swipe was captured.

Diagnosis:

- The refactored reader still passes the content-page interaction matrix on the installed emulator build: center tap, long press, edge next, drag next, texture next, edge previous, drag previous, and texture previous all passed.
- This run does not validate native shell-cover entry or cover drag. Those checks must start from a confirmed `baseline-native-cover` state, otherwise the harness now correctly rejects the precondition instead of producing a false cover result.

## 2026-06-18 Post-Refactor Emulator Cover Matrix Rerun

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 05:15:18`
- App focus: `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`

Precondition:

- `scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoBuild -NoInstall -RequireReaderLaunch -Capture`
- Direct launch selected a real EPUB target, `Alcatraz versus the Evil Librarians (epub)`.
- Launch capture: `captures\reader-dev\reader-dev-20260618-061523.png`
- The capture showed the native cover surface before the matrix.

Command:

- `scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -ArtifactRoot captures\reader-komikku-matrix\post-refactor-emulator-20260618-061612 -NoLaunch -IncludeCoverChecks -ContinueOnFailure`

Artifacts:

- Matrix root: `captures\reader-komikku-matrix\post-refactor-emulator-20260618-061612`
- Summary: `captures\reader-komikku-matrix\post-refactor-emulator-20260618-061612\reader-matrix-summary.csv`
- Failures: `captures\reader-komikku-matrix\post-refactor-emulator-20260618-061612\reader-matrix-failures.txt`

Passes:

- `baseline-current-reader`
- `baseline-native-cover`
- `cover-center-tap-toggle`
- `cover-drag-next`
- `center-tap-toggle`
- `native-long-press-center`
- `edge-tap-next`
- `drag-next`
- `texture-next-walk`
- `edge-tap-previous`
- `drag-previous`
- `texture-previous-walk`

Diagnostics:

- `reader-matrix-failures.txt`: `No matrix failures.`
- Cover and content tap diagnostics reported `nativeTapAction=True`, `explicitContentHandler=False`, `contentTapHandledEvent=False`, and no `hitType=5` image regression.
- Cover validation reported `nativeShellCoverVisible=True` before cover interactions and `nativeShellCoverVisible=False` after cover drag entered readable content.
- Texture direction checks reported `wrongTextureDirection=False` for both `next` and `previous` walks.

Interpretation:

- The installed post-refactor `readerdev` build still opens a real EPUB, renders the native cover, transitions from cover to readable content, routes tap/drag/long-press through the native Komikku controller path, and reports texture movement in the expected direction under the scripted matrix.
- This is installed-build evidence only. The current source tree is not emulator-buildable yet because the remote-font manifest TDD slice is intentionally red until the cache methods are implemented.
- Visual fidelity remains outside this matrix: progress rail proportions, settings-sheet density, texture strength, and drag-preview/page-curl polish still require design/parity work.

## 2026-06-18 Reordered Matrix Page-Turn Queue Wedge

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after dirty install: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 04:43:09`

Artifacts:

- Matrix root: `captures\reader-komikku-matrix\matrix-order-rerun-20260618-045515`
- Summary: `captures\reader-komikku-matrix\matrix-order-rerun-20260618-045515\reader-matrix-summary.csv`
- Failures: `captures\reader-komikku-matrix\matrix-order-rerun-20260618-045515\reader-matrix-failures.txt`

Passes:

- `baseline-current-reader`
- `cover-center-tap-toggle`
- `cover-drag-next`
- `center-tap-toggle`
- `native-long-press-center`
- `edge-tap-next`
- `drag-next`
- `texture-next-walk`
- `drag-previous`

Failures:

- `edge-tap-previous`: diagnostics did not capture moved texture samples for `previous`.
- `texture-previous-walk`: diagnostics did not capture moved texture samples for `previous`.

Diagnosis:

- The reordered matrix removed the earlier false dependency where previous tests could accidentally run from the native shell cover.
- The real failure is a native-controller lock wedge after a fast forward section transition. `texture-next-walk` logs `page-turn:start next` at the transition from `authorsforeword.xhtml` into `capitancebolleta01.xhtml`; Foliate emits `loadDoc(index=7)` and texture scroll samples, but Navic never logs `page-turn:done` or `page-turn:settled` for that turn.
- Every later previous tap logs only `page-turn:queued previous`, so the previous path is blocked behind the unresolved forward page-turn promise.

Follow-up host fix:

- Added `ReaderRuntimeNavigationFlowTest.androidReaderDoesNotLetReflowableFoliatePageTurnPromisesOwnTheNativeInputLock`.
- Red result: the focused guard failed while reflowable EPUB page turns still awaited `view.next()` / `view.prev()`.
- Runtime change: fixed-layout/PDF direct page targets still await `view.goTo(index)`, but reflowable EPUB page turns now issue the Foliate `next`/`prev` command through `issueReflowablePageTurn(...)`, attach async error reporting, and rely on controlled relocation fallback instead of letting Foliate's animation promise own the native input lock.
- Green result:
  - `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js`
  - `:composeApp:testAndroidHost --tests ReaderRuntimeNavigationFlowTest.androidReaderDoesNotLetReflowableFoliatePageTurnPromisesOwnTheNativeInputLock`
  - Adjacent navigation/texture host guards for fixed-layout direct targets, duplicate fixed-layout coalescing, sticky texture direction, and paginator scroll-drag texture sync.

Next required check:

- Install the dirty readerdev build and rerun the reordered Komikku matrix. The expected improvement is that `edge-tap-previous` and `texture-previous-walk` no longer get stuck behind a stale `pageTurnPromise`.

Post-fix emulator check:

- Dirty install evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 05:15:18`.
- Install root: `captures\reader-dev\page-turn-nonblocking-20260618-051407`. The APK installed and reached `publicationReady`; the helper exited non-zero only because the final screenshot pull failed.
- Matrix root: `captures\reader-komikku-matrix\page-turn-nonblocking-20260618-051609`.
- Result: full reordered matrix passed with no failures.
- Passing steps:
  - `baseline-current-reader`
  - `cover-center-tap-toggle`
  - `cover-drag-next`
  - `center-tap-toggle`
  - `native-long-press-center`
  - `edge-tap-next`
  - `drag-next`
  - `texture-next-walk`
  - `edge-tap-previous`
  - `drag-previous`
  - `texture-previous-walk`
- Focus evidence:
  - `edge-tap-previous` now logs `page-turn:start previous`, `surface-texture-scroll ... dir=previous`, `page-turn:done previous`, and `location-changed:posted page-turn:previous`.
  - `texture-previous-walk` reports `requiredTextureDirection=previous`, six moved texture samples, and `wrongTextureDirection=False`.
  - `texture-next-walk` reports `requiredTextureDirection=next`, eleven moved texture samples, and `wrongTextureDirection=False`.

Interpretation:

- Corrected successfully for the dirty emulator build: the native controller no longer wedges behind a stale reflowable Foliate page-turn promise during the Author's Foreword to Chapter 1 boundary.
- This is still a dirty-build validation, not a GitHub release candidate validation.

## 2026-06-18 Refactor Sanity Matrix On Existing Emulator Install

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 04:04:52`

Artifacts:

- Matrix root: `captures\reader-komikku-matrix\post-refactor-question-20260618-043313`
- Summary: `captures\reader-komikku-matrix\post-refactor-question-20260618-043313\reader-matrix-summary.csv`
- Failures: `captures\reader-komikku-matrix\post-refactor-question-20260618-043313\reader-matrix-failures.txt`

Command:

- `scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -ArtifactRoot captures\reader-komikku-matrix\post-refactor-question-20260618-043313 -NoLaunch -IncludeCoverChecks -ContinueOnFailure`

Passes:

- `baseline-current-reader`
- `cover-center-tap-toggle`
- `center-tap-toggle`
- `native-long-press-center`
- `drag-next`
- `drag-previous`

Failures:

- `cover-drag-next`: diagnostics did not capture the expected shell-cover swipe marker.
- `edge-tap-next`: diagnostics did not capture moved texture samples for `next`.
- `edge-tap-previous`: diagnostics did not capture moved texture samples for `previous`.
- `texture-next-walk`: diagnostics did not capture moved texture samples for `next`.
- `texture-previous-walk`: diagnostics did not capture moved texture samples for `previous`.

Interpretation:

- The refactor is not untested: the current installed dirty readerdev build launches and still routes basic native tap, long-press, and readable drag gestures.
- The current emulator result is not release-green. The remaining failures are concentrated around cover drag instrumentation/behavior and paper texture motion sampling after page moves.
- This confirms the next implementation work should stay on the Komikku controller/gesture surface and texture transition pipeline, not on unrelated UI micro-polish.

## 2026-06-18 Drag Preview Release Fix Emulator Check

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after dirty install: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 04:43:09`

## 2026-06-18 Post-Refactor Emulator Validation Check

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 05:15:18`

Commands / artifacts:

- Matrix command: `scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -ArtifactRoot captures\reader-komikku-matrix\post-refactor-validation-20260618-052342 -NoLaunch -IncludeCoverChecks -ContinueOnFailure`
- Matrix root: `captures\reader-komikku-matrix\post-refactor-validation-20260618-052342`
- Return-to-cover captures:
  - `captures\reader-smoke\return-to-cover-check-20260618-052652`
  - `captures\reader-smoke\return-to-cover-check-20260618-052738`
- Focused cover artifact: `captures\reader-komikku-matrix\post-refactor-cover-focused-20260618-052829`

Matrix result:

- Passed: `baseline-current-reader`, `cover-center-tap-toggle`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, `texture-previous-walk`.
- Failed: `cover-drag-next`.

Evidence / interpretation:

- The matrix was launched with `-NoLaunch` from the existing emulator state, and the baseline screenshot was already readable content at `5 / 270`, not the native cover.
- The failed `cover-drag-next` step logged native readable drag handling and a real `page-turn:next` to `page=5/270`, so the failure is an invalid cover-specific precondition for that step, not proof that readable dragging regressed.
- Readable-page short taps, long press, edge taps, readable drag, and both texture walks passed on this installed post-refactor build.
- Driving previous taps from readable content reached the native cover surface, but the boundary is still fragile: the focused cover attempt produced native cover evidence with top chrome visible after center taps, then the interrupted drag artifact showed the embedded/title cover document at `2 / 270`.

Next required check:

- Add or use a deterministic "reset to native cover" harness command before cover-only validations. Rerun `cover-center-tap-toggle` and `cover-drag-next` from a clean native-cover precondition before treating cover behavior as validated.
- Keep the native-cover/embedded-cover boundary on the active risk list; readable interaction checks are currently healthier than cover-state isolation.

Code/test evidence:

- Red guard before implementation: `ReaderRuntimePaperSurfaceTest.androidReaderRestoresAndClearsNativeDragPreviewOnReleaseBeforePageTurn` failed because `previewPageDrag(release)` had no explicit cleanup branch.
- Runtime fix: `navic-reader-page-turns.js` now restores Foliate's synthetic drag scroll, clears `nativePageDragPreview`, removes the page-drag underlay, and re-renders the paper layers when native drag preview reaches `release`.
- Green guard after implementation:
  - `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js`
  - `:composeApp:testAndroidHost --tests ReaderRuntimePaperSurfaceTest.androidReaderRestoresAndClearsNativeDragPreviewOnReleaseBeforePageTurn`
  - `:composeApp:testAndroidHost --tests ReaderRuntimePaperSurfaceTest.androidReaderSeedsTextureTurnDirectionFromNativeReadableDragPreview --tests ReaderRuntimePaperSurfaceTest.androidReaderRestoresAndClearsNativeDragPreviewOnReleaseBeforePageTurn`
  - `git diff --check` on touched runtime/test/log files

Artifacts:

- Install root: `captures\reader-dev\drag-release-fix-20260618-044226`
- Launch capture: `captures\reader-dev\drag-release-fix-20260618-044226\reader-dev-20260618-044320.png`
- Matrix root: `captures\reader-komikku-matrix\drag-release-fix-20260618-044408`
- Summary: `captures\reader-komikku-matrix\drag-release-fix-20260618-044408\reader-matrix-summary.csv`
- Failures: `captures\reader-komikku-matrix\drag-release-fix-20260618-044408\reader-matrix-failures.txt`

Matrix passes:

- `baseline-current-reader`
- `cover-center-tap-toggle`
- `cover-drag-next`
- `center-tap-toggle`
- `native-long-press-center`
- `edge-tap-next`
- `drag-previous`
- `texture-next-walk`
- `texture-previous-walk`

Matrix failures / remaining findings:

- `edge-tap-previous` failed the texture-motion assertion because the screenshot and logs show the native cover boundary, not a page texture transition.
- `drag-next` failed the readable-drag assertion because the prior step left the reader at the shell cover; diagnostics show `shellCoverDragCandidate=True`, `shellCoverSwipe=True`, and `shellCoverCommand=True`, not a missing native gesture.
- `edge-tap-next\screen.png` shows a split blank/texture page after a reported completed page turn. Later `texture-next-walk\screen.png` renders normally at `14 / 270`, so this looks concentrated around the early frontmatter/first-page transition rather than all text pages.

Interpretation:

- The drag-preview cleanup fix improved the emulator matrix materially: cover drag and both texture walks now pass, and the previous stuck-preview path no longer blocks the full matrix.
- The matrix is still not release-green. The next target should be first-readable/frontmatter transition correctness and matrix state isolation, not another release.
- The harness should stop treating the desired native-cover boundary as a texture-motion failure when a previous action crosses back to the shell cover.

## 2026-06-18 Dirty Emulator Post-Host-Fix Check

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after local dirty install: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 04:04:52`

Artifacts:

- Install root: `captures\reader-dev\emulator-validate-20260618-040142`
- Initial dirty launch capture: `captures\reader-dev\reader-dev-20260618-040504.png`
- Cover-boundary screenshots:
  - `captures\reader-dev\emulator-validate-20260618-040142\after-cover-next.png`
  - `captures\reader-dev\emulator-validate-20260618-040142\after-first-readable-prev.png`
  - `captures\reader-dev\emulator-validate-20260618-040142\cover-boundary-gesture.log`
- Matrix/harness evidence:
  - `captures\reader-komikku-matrix\post-refactor-emulator-20260618-040711`
  - `captures\reader-smoke\execout-capture-check-20260618-0410`
  - `captures\reader-smoke\execout-capture-check-20260618-0414`
- Drag-preview failure evidence:
  - `captures\reader-smoke\isolated-drag-prev-stuck-20260618-0421\screen.png`
  - `captures\reader-smoke\isolated-drag-prev-stuck-20260618-0421\logcat-reader.log`
  - `captures\reader-smoke\isolated-drag-prev-stuck-20260618-0421\reader-diagnostics-summary.txt`

Evidence:

- `scripts\install-reader-dev.ps1` built, installed, launched, and confirmed `Reader publication ready` for the current dirty APK.
- Native cover rendered correctly after launch.
- Cover drag to first readable page rendered real EPUB text at `1 / 270`.
- Previous tap from the first readable page returned to the native cover. This validates the host fix for the previous-from-first-readable suppressed-cover bug on the emulator.
- The matrix initially passed:
  - baseline current reader
  - cover center tap toggle
  - cover drag next
  - center tap toggle
  - native long press center
- The old smoke capture path failed at `edge-tap-next` with a partial `adb pull /sdcard/navic-reader-smoke.png` result. Manual pull from the same remote path succeeded immediately.
- `scripts\adb-reader-smoke.ps1` was hardened to capture screenshots with binary `adb exec-out screencap -p` instead of remote `screencap` plus `adb pull`. The first patch used `ProcessStartInfo.ArgumentList`, which failed under hidden Windows PowerShell; it was corrected to `ProcessStartInfo.Arguments`.
- Patched smoke capture now produces valid screenshots and artifact files.

Failures / gaps found:

- Full matrix rerun is blocked by a real drag-preview bug, not by the screenshot capture path.
- Isolated `drag-previous` from the EPUB interior leaves the reader stuck in a split/preview state even after waiting.
- Diagnostics for the stuck step show `readerNativeDragPreview=True`, `readerNativeDragCandidate=True`, and a native readable swipe action followed by `previousPage`.
- Logcat shows repeated `previewPageDrag(update)`, `Reader native readable swipe action=Left`, and `page-turn:queued previous`, but no relocation/settle event that clears the preview layer.

Interpretation:

- The previous-from-first-readable native-cover handoff is now emulator-validated.
- The refactor still supports cover launch, cover drag, center tap, and native long press on the dirty emulator build.
- The current P0 blocker is the drag preview state machine: committed previous drags can leave the preview surface stuck instead of settling or cancelling.
- Do not trust later texture/drag matrix results until this stuck-preview bug is fixed and the full matrix can run from a clean native-cover start.

Next required fix/check:

- Fix drag preview commit/settle/cancel behavior so a committed previous drag always either completes relocation or clears the preview layer.
- Rerun the full Komikku matrix from native cover with the patched smoke capture path.

## 2026-06-18 Dirty Emulator Phase 6 Refactor Check

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after local install: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 03:35:48`

Artifacts:

- Install log: `build\codex-logs\phase6-emulator-install.out.log`
- Initial launch capture: `captures\reader-dev\reader-dev-20260618-033610.png`
- Matrix root: `captures\reader-komikku-matrix\phase6-current-emulator-20260618-0336`
- Focused previous-walk rerun: `captures\reader-komikku-matrix\phase6-current-emulator-20260618-0336-rerun-texture-previous`

Evidence:

- `scripts\install-reader-dev.ps1` installed the current dirty `readerdev` APK, launched `darkaxt.navic.readerdev`, and confirmed `Reader publication ready`.
- Initial screenshot rendered the real EPUB cover and showed pagination preparation reaching `Pages ready: 270`.
- `scripts\adb-reader-komikku-matrix.ps1` reported PASS for:
  - baseline current reader
  - cover center tap toggle
  - cover drag next
  - center tap toggle
  - native long press center
  - edge tap next
  - edge tap previous
  - drag next
  - drag previous
  - texture next walk
- Tap diagnostics for center, cover, and edge taps showed `nativeTapAction=True`, `explicitContentHandler=False`, and `contentTapHandledEvent=False`.
- Drag diagnostics showed native drag routing through `readerNativeDragPreview=True` and `readerNativeDragCandidate=True`.
- Forward texture diagnostics showed `wrongTextureDirection=False` across the sampled forward walk.
- Focused previous-walk rerun logcat showed previous navigation reaching the native layer and texture movement logging `dir=previous` with positive X offset samples.

Failures / gaps found:

- The matrix failed at `texture-previous-walk` because `adb pull /sdcard/navic-reader-smoke.png ...\screen.png` returned exit code 1. Manual `adb pull` from the same device path succeeded immediately, so this is a repeatable smoke-harness capture problem, not evidence of an app crash.
- The focused previous-walk rerun exposed a real reader edge case: repeated previous taps at the beginning enter the suppressed WebView cover section. Logcat shows `cover-document:suppressed`, `location-changed:cover-skipped`, and content layout with `iframe=395,48,0x819`; the manual screenshot shows a blank/split paper page with only the page number. Back-navigation to the beginning must return to the native cover surface instead of leaving the WebView on a suppressed cover document.

Interpretation:

- The Phase 6 refactor still launches, opens a real EPUB, computes a deterministic pagination profile, and routes basic tap/drag interactions through the native Komikku controller path on the emulator.
- The run is not release-green: the previous-walk capture harness needs hardening, and the suppressed-cover back-navigation state is a real behavior bug.

Follow-up host fix:

- Added `ReaderControllerTest.previousFromFirstReadablePageReturnsToNativeCoverInsteadOfSuppressedWebViewCover` to reproduce the emulator finding at the controller boundary.
- Red result: the focused test failed before the fix because previous from the first readable `1 / 270` page still forwarded `ReaderEngineCommand.TurnPage(Previous)`.
- Change: `readerShouldReturnToNativeShellCover(...)` now treats `pageIndex <= 1` as the native-cover boundary, but still requires a cover URL, hidden shell cover, positive page count, and `readerLocatorCanRepresentNativeShellCoverBoundary(...)`.
- Green result: the focused controller test passed, and the adjacent `ReaderChromeStateTest.nativeShellCoverBoundary*` checks still passed.
- This is host-verified only. The next emulator run must confirm repeated previous taps return to the native cover surface instead of a blank/suppressed WebView cover document.

## 2026-06-17 Dirty Emulator Progress Rail Check

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-17 22:37:29`

Artifacts:

- `captures\reader-dev\reader-dev-20260617-current-before-rail.png`
- `captures\reader-dev\reader-dev-20260617-ch1-page1-before-buttons.png`
- `captures\reader-dev\reader-dev-20260617-ch1-page1-after-bottom-rail-button.png`
- `captures\reader-dev\reader-dev-20260617-ch2-page1-after-top-rail-button.png`
- `captures\reader-dev\reader-dev-20260617-ch1-page1-after-rail-endpoint-tap.png`

Evidence:

- Start state showed Chapter 1 at corrected endpoint `11 / 11`.
- DevTools command `goToChapterProgress(OEBPS/Text/capitancebolleta01.xhtml, 0)` moved Chapter 1 to `1 / 11`.
- Lower rail chapter button from Chapter 1 page `1 / 11` dispatched `goToHref(OEBPS/Text/capitancebolleta02.xhtml)` and moved to Chapter 2 page `1 / 11`.
- Upper rail chapter button from Chapter 2 page `1 / 11` dispatched `goToHref(OEBPS/Text/capitancebolleta01.xhtml)` and moved back to Chapter 1 page `1 / 11`.
- Rail endpoint tap emitted `goToChapterProgress(OEBPS/Text/capitancebolleta01.xhtml, 1.0)` and reported `chapterPageIndex=10`, `chapterPageCount=11`; screen displayed `11 / 11`.

Interpretation:

- Dirty emulator build validates the local sentinel-count and page-1 rail-button fixes.
- This does not yet prove the same behavior in the next GitHub/release candidate or on the exact device/package where the user observed the bug.

Next required fix/check:

- Validate the same path on a clean release candidate or user-installed package.
- Continue with persistence/resume after disrupted drag/app interruption.
- Recheck cover chrome layering on the installed APK.

## 2026-06-17 Host/Static Focused Checks

Commands:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderChapterRailSeekCommitsWithControlledReasonInsteadOfPassiveClamp" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomMenuDoesNotRenderOverShellCover" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest.androidReaderResolvesTocHrefNavigationBeforeCommittingLocation" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgeExposesProgressSeekCommand" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result:

- JS syntax check passed.
- Diff whitespace check passed.
- Focused Android host checks passed after adjusting stale navigation guard expectations to the controlled fallback path.

Scope proven:

- Controlled relocation reason is preserved over generic Foliate relocate bursts.
- Reflowable paginated text page count follows Foliate `pages - 2` sentinel-column contract.
- Bottom bar has a host guard against rendering over shell cover.
- TOC href navigation resolves before committing relocation state.
- Progress seek command and Komikku rail host guards are present.

Scope not proven:

- Clean APK/release candidate behavior.
- Physical device behavior.
- Resume persistence after app/window disruption.
- Drag preview black-void behavior.

## 2026-06-18 Current Source Dirty Emulator Refactor Smoke

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after rebuilding current dirty source: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 06:59:06`
- Bindery launch target discovered by `scripts\install-reader-dev.ps1`: `Alcatraz versus the Evil Librarians (epub)`

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -ArtifactRoot captures\reader-komikku-matrix\post-refactor-current-source-20260618-0659 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

Artifacts:

- Launch screenshot: `captures\reader-dev\reader-dev-20260618-065917.png`
- Matrix root: `captures\reader-komikku-matrix\post-refactor-current-source-20260618-0659`
- Matrix summary: `captures\reader-komikku-matrix\post-refactor-current-source-20260618-0659\reader-matrix-summary.csv`
- Matrix failures: `captures\reader-komikku-matrix\post-refactor-current-source-20260618-0659\reader-matrix-failures.txt`

Evidence:

- Current dirty source built, installed, launched directly into a real Bindery EPUB, and emitted `Reader bridge event: publicationReady`.
- WebView debugging sockets were present for the reader process.
- The Komikku matrix reported PASS for all scripted steps: baseline reader, native cover visibility, cover center tap toggle, cover drag next, content center tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk.
- `cover-drag-next` diagnostics captured `shellCoverDragCandidate=True`, `shellCoverSwipe=True`, and `shellCoverCommand=True`.
- `drag-next` diagnostics captured native drag preview/candidate and texture fields `pos`, `base`, `delta`, `dir`, `page`, and `href`; `wrongTextureDirection=False`.
- `texture-next-walk` and `texture-previous-walk` both captured texture direction samples with `wrongTextureDirection=False`.
- Failure file reported: `No matrix failures.`

Interpretation:

- The refactored reader controller/runtime still works on the emulator after rebuilding the current dirty source: EPUB launch, native shell cover, tap zones, drag gestures, long press routing, and scripted texture direction checks are alive.
- This is not a release-candidate validation and does not prove physical-device behavior.
- This run does not validate PDF, remote font Settings UI, visual Komikku fidelity, drag-preview black-void polish, or manual feel issues that are not captured by the current matrix assertions.

## 2026-06-18 Phase 7 Remote Font Download-State Slice

Target:

- Spec gate: Phase 7 font-source parity from `2026-06-17-anx-parity-7-phase-plan.md`.
- Anx reference: `tmp\references\anx-reader\lib\providers\fonts.dart` `FontDownloads`, `DownloadStatus`, and the settings page download controls.

Implementation evidence:

- `ReaderImportedFont.kt` now defines `ReaderRemoteFontDownloadState` and Anx-equivalent status values: `none`, `downloading`, `paused`, `completed`, and `failed`.
- `ReaderFontImporter` now exposes per-font remote download state plus `pauseRemoteFontDownload`, `resumeRemoteFontDownload`, and `cancelRemoteFontDownload`.
- Android importer now reads remote font files in chunks, updates progress, keeps active download jobs, and maps pause/resume/cancel to the remote font state.
- `SettingsEbooksScreen` now reads `fontImporter.remoteFontDownloads[remoteFont.id]` and routes download-row clicks to download, pause, resume, retry, or cancel.
- `2026-06-17-anx-parity-7-phase-plan.md` marks font remote manifest parity as `Exists` at the contract/routing level. Visual polish of the Settings surface remains separate Komikku fidelity work.

TDD evidence:

- RED: `ReaderFontSourceAnxParityTest.navicSettingsExposeAnxRemoteFontDownloadProgressPauseResumeAndCancel` failed at `ReaderFontSourceAnxParityTest.kt:231` because the common importer did not expose `ReaderRemoteFontDownloadState` or pause/resume/cancel methods.
- GREEN: focused rerun passed after adding the common contract, Android implementation, Settings rows, and parity-plan update.
- GREEN: full `ReaderFontSourceAnxParityTest` passed.
- GREEN: full reader host suite passed with `:composeApp:testAndroidHost --tests "paige.navic.reader.*"`.

Dirty emulator evidence:

- `scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture`
- Current dirty source built `androidApp:assembleReaderDev`, installed successfully on `emulator-5554`, launched `darkaxt.navic.readerdev`, opened the discovered Bindery EPUB target, and emitted `Reader bridge event: publicationReady`.
- Screenshot captured at `captures\reader-dev\reader-dev-20260618-072039.png`.

Scope not proven:

- Live network download progress on emulator/device.
- Visual quality of the remote font Settings rows.
- Full reader emulator matrix after this Settings-only change.

## 2026-06-18 Eta69 Installed Emulator Matrix Rerun

Trigger:

- User asked whether anything had been validated in the emulator after the refactor.
- This rerun validates the already-installed readerdev build in place; it is not a fresh release pipeline.

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 07:20:30`
- Focused window before matrix: `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`

Command:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -IncludeCoverChecks -ContinueOnFailure -NoLaunch
```

Artifacts:

- Matrix root: `captures\reader-komikku-matrix\20260618-072527`
- Matrix summary: `captures\reader-komikku-matrix\20260618-072527\reader-matrix-summary.csv`
- Matrix failures: `captures\reader-komikku-matrix\20260618-072527\reader-matrix-failures.txt`
- Spot-checked screenshots:
  - `captures\reader-komikku-matrix\20260618-072527\baseline-current-reader\screen.png`
  - `captures\reader-komikku-matrix\20260618-072527\cover-drag-next\screen.png`
  - `captures\reader-komikku-matrix\20260618-072527\drag-next\screen.png`
  - `captures\reader-komikku-matrix\20260618-072527\center-tap-toggle\screen.png`

Evidence:

- Matrix summary reported PASS for all scripted steps: baseline reader, native cover visibility, cover center tap toggle, cover drag next, center tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk.
- Failure file reported `No matrix failures.`
- WebView debug sockets were present for the active reader process during every matrix step.
- Visual spot-check confirmed the native cover was visible in the baseline screenshot and that cover drag moved into rendered EPUB text with organic page numbering.

Interpretation:

- The installed eta69 dirty build still passes the scripted emulator smoke for the refactored Komikku-controller path.
- This proves basic EPUB load, native cover, tap zones, drag gestures, long press routing, page counter rendering, and current texture-direction assertions still work in the emulator.
- This does not prove release APK behavior, physical device feel, PDF behavior, remote font live download UI, final Komikku visual fidelity, or black-void drag-preview polish.

## 2026-06-18 Phase 8 Adaptive Composition Dirty Emulator Check

Trigger:

- Phase 8 added the remaining Anx `BookStyle` adaptive composition fields: `maxColumnCount` and `columnThreshold`.
- User asked whether the emulator had validated that the refactor still works.

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after dirty install: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 08:02:01`
- Bindery launch target discovered by `scripts\install-reader-dev.ps1`: `Alcatraz versus the Evil Librarians (epub)`

Host verification:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination.js
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\paginator.js
```

Emulator commands:

```powershell
.\scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -IncludeCoverChecks -ContinueOnFailure -NoLaunch
```

Artifacts:

- Launch screenshot: `captures\reader-dev\reader-dev-20260618-080208.png`
- Matrix root: `captures\reader-komikku-matrix\20260618-080230`
- Matrix summary: `captures\reader-komikku-matrix\20260618-080230\reader-matrix-summary.csv`
- Matrix failures: `captures\reader-komikku-matrix\20260618-080230\reader-matrix-failures.txt`

Evidence:

- Current dirty source built `androidApp:assembleReaderDev`, installed successfully, launched into the Bindery EPUB target, and emitted `Reader bridge raw: {"type":"publicationReady"}`.
- Matrix summary reported PASS for baseline reader, native cover visibility, cover center tap toggle, cover drag next, center tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk.
- Failure file reported `No matrix failures.`
- Host parity is green for all Anx `BookStyle` dimensions, including Phase 8 `maxColumnCount` and `columnThreshold`.

Interpretation:

- The current refactored dirty reader still loads a real EPUB in the emulator after Phase 8, and the scripted Komikku controller behaviors still pass.
- This validates the refactor path at smoke/matrix level; it is not a release-candidate claim and does not prove physical-device feel.
- Still not proven here: visual/manual interaction of the new column controls, PDF behavior, remote font live download UI, final Komikku visual fidelity, and drag-preview black-void polish.

## 2026-06-18 Native Cover Chrome Overlay Guard

Target:

- Spec gate: native shell cover must not have reader diagnostics/chrome rendered over it.
- Files: `ReaderRoot.kt`, `ReaderRuntimeCommonChromeTest.kt`.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSuppressesPaginationProfileBadgeOverNativeCover"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Evidence:

- RED: the focused guard failed while `KomikkuComposeOverlay` always mounted `KomikkuPaginationProfileStatusBadge`, even when `controllerState.shellCoverVisible` was true.
- Change: `ReaderRoot.kt` now mounts `KomikkuPaginationProfileStatusBadge` only inside `if (!controllerState.shellCoverVisible)`.
- GREEN: focused guard passed.
- GREEN: full `ReaderRuntimeCommonChromeTest` passed.

Scope not proven:

- No APK install or emulator matrix rerun was done for this host-only slice.
- Physical/device visual cover chrome layering still needs validation before a release-candidate claim.

## 2026-06-18 Dirty Emulator Refactor Regression Check

Trigger:

- User asked whether anything had been validated on the emulator since the reader refactor.
- This check rebuilds and installs the current dirty `readerdev` APK instead of reusing the earlier 08:02 artifact.

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence after dirty install: `versionName=v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-18 08:32:44`
- Bindery launch target discovered by `scripts\install-reader-dev.ps1`: `Alcatraz versus the Evil Librarians (epub)`

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

Artifacts:

- Launch screenshot: `captures\reader-dev\reader-dev-20260618-083254.png`
- Matrix root: `captures\reader-komikku-matrix\20260618-083330`
- Matrix summary: `captures\reader-komikku-matrix\20260618-083330\reader-matrix-summary.csv`
- Matrix failures: `captures\reader-komikku-matrix\20260618-083330\reader-matrix-failures.txt`

Evidence:

- Current dirty source built `androidApp:assembleReaderDev`, installed successfully, launched into the Bindery EPUB target, and emitted `Reader bridge raw: {"type":"publicationReady"}`.
- Matrix summary reported PASS for baseline reader, native cover visibility, cover center tap toggle, cover drag next, center tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk.
- Failure file reported `No matrix failures.`
- Tap validation reported `nativeTapAction=True`, `explicitContentHandler=False`, and `contentTapHandledEvent=False` for both cover and readable center taps.
- Texture diagnostics reported `wrongTextureDirection=False` for next and previous drag/walk checks.
- Visual screenshot inspection confirmed the native cover is rendered as a full-screen black letterboxed surface without bottom menu overlay in the baseline capture, and readable pages render with organic page numbers such as `1 / 270`, `3 / 270`, and `7 / 270`.

Interpretation:

- The refactored dirty reader still opens a real EPUB in the emulator and passes the scripted Komikku controller matrix.
- The result is emulator smoke/matrix evidence, not a release-candidate claim and not a physical-device feel claim.
- Remaining visual risks observed in screenshots: some pages still show overly wide word spacing/justification, and Komikku visual fidelity/settings polish remain incomplete.

## 2026-06-18 Phase 7 PDF And Font Source Parity Guard

Trigger:

- The Anx/Foliate parity plan requires Phase 7 guards for PDF `makePDF(file)` parity and Anx-style font source parity.

Command:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliatePdfAnxParityTest" --tests "paige.navic.reader.ReaderFontSourceAnxParityTest"
```

Evidence:

- `FoliatePdfAnxParityTest` passed. It reads `tmp/references/anx-reader/assets/foliate-js/src/pdf.js` and verifies Navic's bundled `vendor/foliate-js/pdf.js` preserves the Anx/Foliate `makePDF(file)` contract.
- `ReaderFontSourceAnxParityTest` passed. It reads Anx font provider/service/model references and verifies Navic exposes local import, remote manifest fetch/download/cache/list/delete, WebView-safe font URLs, settings routes, and Anx-style remote download progress/pause/resume/cancel controls.

Interpretation:

- Phase 7 is host-verified at the contract/routing level.
- This is not visual validation of the settings surface and is not a PDF interaction-device claim.

## 2026-06-18 Controller-Owned Progress Persistence Guard

Trigger:

- Priority 0 risk: disrupted drag/app interruption must not reopen the reader at the first page or cover because a later Foliate cover/title/nav relocation overwrote saved readable progress.

Command:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.reader.ReaderControllerTest.engineRelocationsBuildControllerOwnedProgressSnapshotsAfterPublicationReady" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderCoordinatorStepConsumerTest"
```

Evidence:

- `ReaderProgressSaveGate` now treats cover/title/nav start placeholders as non-saveable whenever publication is ready, including after a readable location was already saved.
- `ReaderProgressSyncTest.progressSaveGateDoesNotLetCoverPlaceholdersOverwriteReadableResumeLocation` covers the specific regression: readable locator first, later cover locator second, no save intent for the cover.
- `ReaderControllerTest.engineRelocationsBuildControllerOwnedProgressSnapshotsAfterPublicationReady` verifies the controller keeps the existing saved progress instead of replacing it with a later cover placeholder.
- Focused progress/controller/coordinator tests passed.

Interpretation:

- The host-level controller boundary now rejects placeholder resume overwrites instead of letting the Foliate/WebView stream own resume truth.
- Still needs emulator or device validation for actual app interruption/reopen behavior before release-candidate claims.

## 2026-06-18 Emulator Refactor Gate: Page Drag Runtime Name Collision

Trigger:

- User asked whether anything still needed validation in the emulator after the reader refactor.
- A dirty reader-dev install exposed a real runtime regression during the Komikku matrix.

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed version: `versionName=v1.0.11-eta69`, `versionCode=402`
- Bindery launch target: `Alcatraz versus the Evil Librarians (epub)`

Failing evidence:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

- Failing matrix root: `captures\reader-komikku-matrix\20260618-095649`
- `edge-tap-next\screen.png` showed `this.pageDragPreviewTargetKey is not a function`.
- `edge-tap-next\reader-diagnostics-summary.txt` had no texture movement samples and no renderer content document.
- Reader logs showed native input did reach the runtime:
  - `Reader native tap action=RIGHT`
  - `page-turn:start next`
  - `surface-layout label=page-turn:next view=0,0,0x0 renderer=0,0,0x0`
  - `content-layout label=page-turn:next index=1 doc=missing`

Root cause:

- `NavicReaderRuntime` had an instance field named `pageDragPreviewTargetKey`.
- `navic-reader-page-turns.js` also exported a prototype method named `pageDragPreviewTargetKey`.
- JavaScript class fields shadow prototype methods, so `this.pageDragPreviewTargetKey(...)` crashed at runtime.

Fix:

- Added a host guard in `ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack`.
- Renamed the generator method to `buildPageDragPreviewTargetKey(...)`, leaving `pageDragPreviewTargetKey` as state only.

Host verification:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack"
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
```

- RED: focused guard failed before the rename.
- GREEN: focused guard passed after the rename.
- GREEN: `node --check` passed.

Emulator verification:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

- Dirty install built `androidApp:assembleReaderDev`, installed successfully, launched the Bindery EPUB target, and emitted `Reader bridge raw: {"type":"publicationReady"}`.
- Launch screenshot: `captures\reader-dev\reader-dev-20260618-100704.png`
- Passing matrix root: `captures\reader-komikku-matrix\20260618-100823`
- Matrix summary reported PASS for baseline reader, native cover, cover center tap toggle, cover drag next, center tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk.
- `edge-tap-next\reader-diagnostics-summary.txt` reported texture position/base/delta/direction/page/href samples with `wrongTextureDirection=False`.
- `drag-previous\reader-diagnostics-summary.txt` reported `readerNativeDragPreview=True`, texture movement samples, and `wrongTextureDirection=False`.

Interpretation:

- Yes, emulator validation was needed after the refactor; it caught a runtime API collision that host string guards did not previously cover.
- The dirty reader-dev build now passes the scripted Komikku matrix in the emulator after the fix.
- This is still not a GitHub release and not a physical-device feel claim.

## 2026-06-18 Emulator Anx Bridge Guard: Required Event Assertions

Trigger:

- After the refactor gate, the remaining risk was that Anx-style bridge behavior could be present in logs but not asserted by emulator scripts.
- The smoke script could capture `Reader bridge raw` and a few touch diagnostics, but it could not fail a run when a required Anx bridge event was missing.

Contract added:

- `scripts\adb-reader-smoke.ps1` now accepts `-RequireReaderBridgeEvent`.
- When `-CaptureReaderDiagnostics` is enabled, the script writes `reader-bridge-events.log`.
- `reader-diagnostics-summary.txt` now includes `requiredBridgeEvents=` and `bridgeEvent:<event>=True/False`.
- Missing required events fail the ADB run with `required bridge event '<event>' was not captured`.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics"
```

- RED: failed before the smoke script exposed `RequireReaderBridgeEvent`, `reader-bridge-events.log`, and summary assertions.
- GREEN: passed after the script contract was added.

Emulator target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed version: `versionName=v1.0.11-eta69`, `versionCode=402`

Emulator checks:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -CaptureReaderDiagnostics -TapFraction "0.90,0.50,900" -RequireReaderTapAction -RequireReaderBridgeEvent selectionCleared,loadDoc,overlayCreated,locationChanged
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -CaptureReaderDiagnostics -LongPressFraction "0.50,0.50,950,900" -RequireNativeLongTap -RequireReaderBridgeEvent selectionChanged
```

Artifacts:

- Section-transition tap: `captures\reader-smoke\20260618-102413`
- Native long press: `captures\reader-smoke\20260618-102443`

Observed bridge events:

- `selectionCleared`: captured before page transition.
- `loadDoc`: captured when moving from author foreword into chapter 1.
- `overlayCreated`: captured for the loaded chapter document.
- `locationChanged`: captured with `reason=page-turn:next` and `rangeCfi=...`.
- `selectionChanged`: captured with `footnote=false` and a position rectangle.

Important boundary:

- A first page-local edge tap at `captures\reader-smoke\20260618-102321` correctly failed when `loadDoc` was required, because `loadDoc` is a section-boundary event, not a per-page event.
- This proves the new guard is not just checking stale logs; it distinguishes page-local bridge traffic from section-transition bridge traffic.

Remaining Anx bridge validation still pending:

- `internalLink` from a physical direct chapter/link tap. The emulator-injected WebView flow is covered below.
- `externalLink`.
- `annotationClick` and `annotationDrawn`.
- `footnoteClose`.
- `pullUp`.

## 2026-06-18 Local Harness Phase 2 Guard: Internal Link Ownership

Trigger:

- Phase 2 was source/host verified, but the browser harness did not explicitly assert the Anx-style `internalLink` bridge event produced by Foliate's own cancelable `link` event.
- Without this guard, short-tap link suppression could regress while generic content-click suppression still looked green.

Contract added:

- `tools\reader-harness\src\run-reader-harness.mjs` now dispatches a cancelable Foliate `link` event against the live `foliate-view` in both native tap-zone mode and non-native mode.
- `tools\reader-harness\src\reader-trace-assertions.mjs` now fails unless:
  - native tap-zone mode cancels the Foliate link event;
  - native tap-zone mode posts `internalLink` with `prevented=true` and `source=native-short-tap`;
  - non-native mode leaves the Foliate link event uncanceled;
  - non-native mode posts `internalLink` with `prevented=false` and `source=foliate-link`.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
```

- RED: failed before the harness exposed `nativeTapZonesFoliateLinkDefaultPrevented`, `nativeTapZonesInternalLinkSources`, and `nonNativeInternalLinkSources`.
- GREEN: passed after adding the harness probes and assertions.

Verification:

```powershell
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tmp\reader-live\served-input.epub
```

- `node --check` passed for both changed harness files.
- Browser harness passed and wrote `tools\reader-harness\output\css-smoke.trace.json`.

Observed trace values:

- `nativeTapZonesFoliateLinkDefaultPrevented=true`
- `nativeTapZonesInternalLinkPreventedCount=1`
- `nativeTapZonesInternalLinkSources=["native-short-tap"]`
- `nonNativeFoliateLinkDefaultPrevented=false`
- `nonNativeInternalLinkAllowedCount=1`
- `nonNativeInternalLinkSources=["foliate-link"]`
- Long-press/direct activation remained intact:
  - `nativeTapZonesLongPressTextLinkNavigationTraceCount=1`
  - `nativeTapZonesCoordinateLongPressTextLinkNavigationTraceCount=1`

Remaining boundary:

- This is browser/WebView runtime evidence, not physical-device feel evidence.
- Android logcat still needed a real or injected `internalLink(...)` check before Phase 2 could be treated as release-ready on device; that injected check is recorded in the next section.

## 2026-06-18 Emulator Phase 2 DevTools Probe: Internal Link Debug Label

Trigger:

- After the Phase 2 browser harness passed, the remaining Android gap was whether the real WebView bridge emitted the same Anx-style `internalLink(...)` debug label into Android logcat.
- A first attempt using Playwright `chromium.connectOverCDP` failed against Android WebView with `Browser.setDownloadBehavior: Browser context management is not supported`.

Correction:

- `tools\reader-harness\src\adb-webview-eval.mjs` now talks directly to the Android WebView page target:
  - forwards `tcp:9223` to `webview_devtools_remote_<pid>`;
  - reads `http://127.0.0.1:9223/json/list`;
  - connects to the page `webSocketDebuggerUrl`;
  - runs `Runtime.evaluate` on the `Navic Reader` WebView page.
- `scripts\adb-reader-smoke.ps1` now accepts `-ReaderDevtoolsProbe internal-link-native` and writes `reader-devtools-probe.json`.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperInjectsReaderBridgeEventsThroughDevTools" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics"
node --check tools\reader-harness\src\adb-webview-eval.mjs
```

- RED: first failed on a missing repo-file fixture, then failed because the DevTools helper/script integration was absent.
- GREEN: passed after adding the direct-CDP helper and smoke-script probe hook.
- `node --check` passed for `adb-webview-eval.mjs`.

Emulator target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed version: `versionName=v1.0.11-eta69`, `versionCode=402`

Emulator check:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe internal-link-native -RequireReaderBridgeEvent internalLink
```

Artifact:

- `captures\reader-smoke\20260618-105750`

Observed probe output:

- `probe=internal-link-native`
- `href=#navic-adb-internal-link-probe`
- `defaultPrevented=true`
- `expectedSource=native-short-tap`
- `pageTitle=Navic Reader`
- `pageUrl=https://appassets.androidplatform.net/assets/reader/index.html`

Observed Android bridge log:

- `Reader bridge raw: {"type":"internalLink","href":"#navic-adb-internal-link-probe","prevented":true,"source":"native-short-tap"}`
- `Reader bridge event: internalLink(#navic-adb-internal-link-probe, prevented=true, source=native-short-tap)`

## 2026-06-18 Emulator Komikku Matrix: Post-Refactor Readable Surface

Trigger:

- The reader runtime was split and the WebView probe path changed. A broader emulator check was needed to verify readable-content tap, drag, long-press, and texture-direction behavior still works on the installed build.

Command:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta69 -NoLaunch -ContinueOnFailure
```

Artifact:

- `captures\reader-komikku-matrix\20260618-105919`

Result:

- PASS: `baseline-current-reader`
- FAIL: `enter-readable-content`
- PASS: `center-tap-toggle`
- PASS: `native-long-press-center`
- PASS: `edge-tap-next`
- PASS: `drag-next`
- PASS: `texture-next-walk`
- PASS: `edge-tap-previous`
- PASS: `drag-previous`
- PASS: `texture-previous-walk`

Interpretation:

- The single failure is context-bound: the emulator was already in readable content, so the cover-entry step expected shell-cover swipe diagnostics that could not exist on the current page.
- Readable-content checks passed after that failure:
  - edge next/previous produced native tap actions;
  - drag next/previous produced native drag preview and drag candidate diagnostics;
  - texture next/previous samples included `pos=`, `base=`, `delta=`, `dir=`, `page=`, and `href=`;
  - `wrongTextureDirection=False` for next and previous samples.

Remaining boundary:

- This does not validate current cover behavior because the emulator was not on the native cover surface for the matrix.
- Physical-device feel validation is still separate from emulator/logcat validation.

## 2026-06-18 Release Prep: v1.0.11-eta70

Trigger:

- Prepare a public phone release candidate after the Komikku reader backbone and Anx parity guard work.

Release version:

- `versionName=v1.0.11-eta70`
- `versionCode=403`

Local verification:

```powershell
scripts\verify-android-release-version.ps1 -ExpectedVersionName "v1.0.11-eta70"
node tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroid
.\gradlew.bat --no-daemon --no-parallel :androidApp:packageReaderDev
git diff --check
```

Result:

- PASS: version name matches the release tag.
- PASS: reader harness smoke.
- PASS: Android host tests.
- PASS: Android tests.
- PASS: readerDev APK packaging.
- PASS: whitespace check.

Emulator boundary:

- Installed `darkaxt.navic.readerdev` on `emulator-5554` reported `versionName=v1.0.11-eta70`, `versionCode=403`.
- The post-install reader DevTools probe was not counted as a reader validation pass because the app landed on the Books library, leaving no Navic Reader WebView page to attach to.
- The ADB WebView relocation probe was converted to a diagnostic snapshot command so future probes do not hang while waiting for synthetic navigation side effects.

Outcome:

- CANCELED as a phone release candidate before publication.
- Reason: the reader branch was current against the feature line but still behind `fork/master` by the Bindery cache/playback lifecycle commits.
- Do not install eta70 on the phone; eta71 is the master-synced candidate.

## 2026-06-18 Release Prep: v1.0.11-eta71

Trigger:

- Promote the Komikku reader backbone and Anx parity work onto the master release line before publishing a phone APK.

Release version:

- `versionName=v1.0.11-eta71`
- `versionCode=404`

Merge state:

- `fork/master` was merged cleanly into `codex/komikku-reader-backbone-eta64`.
- Included master commits:
  - `68a92b88` `Fix playback service task removal lifecycle`
  - `b2594013` `Show cached Bindery entities before refresh`
  - `6bf864f1` `Show cached Bindery book detail before refresh`

Required gate before tag publication:

```powershell
scripts\verify-android-release-version.ps1 -ExpectedVersionName "v1.0.11-eta71"
node tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroid
.\gradlew.bat --no-daemon --no-parallel :androidApp:packageReaderDev
git diff --check
```

Result:

- PASS: version name matches `v1.0.11-eta71`.
- PASS: JS syntax checks for the reader harness.
- PASS: reader harness smoke.
- PASS: whitespace check.
- PASS: Android host tests after merging `fork/master`.
- PASS: Android tests after merging `fork/master`.
- PASS: readerDev APK packaging after merging `fork/master`.

## 2026-06-18 Host Guard: Anx Bridge Events Must Reach Controller State

Trigger:

- GLM audit found that Phase 2-8 parity work had added bridge/engine event types, decode paths, and debug labels, but several Anx events were still discarded by `ReaderController`.

Red checks:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderControllerTest"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes"
```

Result:

- FAIL as expected before implementation: `ReaderEngineEvent.DocLoaded` had no `sectionId`; `ReaderLoadedDocument`, `ReaderLinkInteraction`, `ReaderAnnotationInteraction`, `ReaderOverlayInteraction`, and `ReaderEngineNavigationState` did not exist; controller state had no matching fields.

Implemented:

- `LoadDoc.sectionId` now survives `ReaderBridgeEvent -> ReaderEngineEvent.DocLoaded -> ReaderControllerState.loadedDocument`.
- `ReaderController` now stores Anx bridge event controller state for internal/external links, annotation click/draw events, overlay creation, footnote close, pull-up, and WebView navigation availability.
- `FoliateAnxParityTest` now rejects Phase 3 `Exists` entries that are still no-op'd in `ReaderController`.

Green check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.FoliateAnxParityTest"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost
git diff --check
```

Result:

- PASS: controller state behavior and the new source guard are green.
- PASS: adapter boundary mapping preserves `LoadDoc.sectionId`.
- PASS: full Android host suite.
- PASS: whitespace check.

Remaining:

- Build UI routes on top of the new controller state, especially the selection action menu.

## 2026-06-18 Host Guard: Reader Search Must Reach Komikku UI

Trigger:

- GLM audit identified the broader parity failure pattern: bridge symbols can be green while controller/UI behavior is absent. Search was the next visible case: `ReaderEngineCommand.Search`, Foliate search, and `SearchResults` existed, but no Komikku-owned reader search UI or clear-search route consumed them.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.searchDialogAndClearSearchAreControllerOwned" --tests "paige.navic.reader.ReaderControllerTest.searchResultNavigationIsControllerOwned" --tests "paige.navic.reader.ReaderCoordinatorTest.clearSearchAndSearchResultNavigationRouteThroughCurrentEngineAdapter" --tests "paige.navic.reader.ReaderBridgeProtocolTest.clearSearchCommandDispatchesAnxSearchClearIntent" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedEngineCapabilitiesAsFoliateBridgeCommands" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSearchIsKomikkuOverlayAndControllerRouted"
```

Result:

- FAIL as expected before implementation: unresolved `ReaderEngineCommand.ClearSearch`, `ReaderBridgeCommand.ClearSearch`, `ReaderControllerDialog.Search`, `openSearchDialog`, `closeSearchDialog`, and `navigateToSearchResult`.

Implemented:

- `ReaderController` now owns search dialog lifecycle, search clear state, and search-result navigation by CFI/HREF.
- `ReaderCoordinator` routes search dialog, clear-search, and result navigation to the active engine adapter.
- `ReaderBridgeCommand.ClearSearch`, `ReaderEngineCommand.ClearSearch`, `FoliateWebViewEngineAdapter`, Android debug labels, and `navic-reader.js` now carry a clear-search command down to Foliate `view.clearSearch()`.
- `KomikkuReaderSearchDialog` is a dedicated native overlay component under `ReaderRoot`.
- `ReaderAppBars` exposes search as a Komikku-style bottom reader action, not as Navic global top chrome.

Green check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.searchDialogAndClearSearchAreControllerOwned" --tests "paige.navic.reader.ReaderControllerTest.searchResultNavigationIsControllerOwned" --tests "paige.navic.reader.ReaderCoordinatorTest.clearSearchAndSearchResultNavigationRouteThroughCurrentEngineAdapter" --tests "paige.navic.reader.ReaderBridgeProtocolTest.clearSearchCommandDispatchesAnxSearchClearIntent" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedEngineCapabilitiesAsFoliateBridgeCommands" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSearchIsKomikkuOverlayAndControllerRouted"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroid
git diff --check
```

Result:

- PASS: focused search controller, coordinator, bridge, adapter, runtime source guard, and Komikku overlay guard.
- PASS: `navic-reader.js` syntax check.
- PASS: full Android host suite.
- PASS: Android unit test task.
- PASS: whitespace check.

Remaining:

- Android/emulator validation is still required: open search in a real EPUB, submit a term, verify results render, tap a result, verify navigation, dismiss search, and verify Foliate highlights clear.

## 2026-06-18 Host Guard: Annotation Click Must Reach Komikku UI

Trigger:

- GLM audit correctly identified the recurring risk that Anx bridge events can be typed and decoded while still producing no visible reader behavior.
- `AnnotationClick` already reached `ReaderControllerState.lastAnnotationInteraction`, but tapping an existing highlight/note had no Komikku-owned UI route.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.annotationClicksOpenControllerOwnedAnnotationPopup" --tests "paige.navic.reader.ReaderControllerTest.dismissAnnotationPopupClearsOnlyTheVisiblePopup" --tests "paige.navic.reader.ReaderCoordinatorTest.annotationPopupDismissalIsControllerOwnedAndDoesNotTouchTheEngine" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderAnnotationClickIsKomikkuOverlayAndControllerRouted"
```

Result:

- FAIL as expected before implementation: unresolved `ReaderAnnotationPopupState`, missing `annotationPopup`, missing `dismissAnnotationPopup`, and missing `ReaderAnnotationDialog.kt`.

Implemented:

- `ReaderControllerState.annotationPopup` now opens from `ReaderEngineEvent.AnnotationClicked`.
- `ReaderController.dismissAnnotationPopup` and `ReaderCoordinator.dismissAnnotationPopup` close the popup without emitting engine/WebView commands.
- `KomikkuReaderAnnotationDialog` is a dedicated native overlay component under `ReaderRoot`, not local `ReaderScreen` state.
- `ReaderRuntimeCommonChromeTest` now guards the Komikku overlay routing so `AnnotationClick` cannot regress back to hidden state-only handling.

Green check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.annotationClicksOpenControllerOwnedAnnotationPopup" --tests "paige.navic.reader.ReaderControllerTest.dismissAnnotationPopupClearsOnlyTheVisiblePopup" --tests "paige.navic.reader.ReaderCoordinatorTest.annotationPopupDismissalIsControllerOwnedAndDoesNotTouchTheEngine" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderAnnotationClickIsKomikkuOverlayAndControllerRouted"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroid
```

Result:

- PASS: focused controller/coordinator/Komikku overlay guard.
- PASS: full Android host suite.
- PASS: Android unit test task.

Remaining:

- Android/emulator validation is still required: create or load a highlight/note, tap the existing annotation, confirm the popup appears, dismiss it, and verify no menu/tap-zone regression.

## 2026-06-18 Host Guard: External Links Must Reach Native Komikku UI

Trigger:

- `ExternalLink` was decoded and retained as `lastLinkInteraction`, but it still had no visible controller-owned route.
- External links should not be opened directly by WebView-owned chrome. The reader shell must surface the event and hand confirmed URL opening to the app boundary.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.externalLinksOpenControllerOwnedExternalLinkPrompt" --tests "paige.navic.reader.ReaderControllerTest.dismissExternalLinkPromptClearsOnlyTheVisiblePrompt" --tests "paige.navic.reader.ReaderCoordinatorTest.externalLinkPromptDismissalIsControllerOwnedAndDoesNotTouchTheEngine" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderExternalLinksAreKomikkuOverlayAndNativeUriRouted"
```

Result:

- FAIL as expected before implementation: unresolved `ReaderExternalLinkPromptState`, missing `externalLinkPrompt`, missing `dismissExternalLinkPrompt`, and missing `ReaderExternalLinkDialog.kt`.

Implemented:

- `ReaderControllerState.externalLinkPrompt` now opens from `ReaderEngineEvent.ExternalLinkOpened`.
- `ReaderController.dismissExternalLinkPrompt` and `ReaderCoordinator.dismissExternalLinkPrompt` close the prompt without emitting engine/WebView commands.
- `KomikkuReaderExternalLinkDialog` is a dedicated native overlay component under `ReaderRoot`.
- `ReaderScreen` owns confirmed opening through `LocalUriHandler.current.openUri(url)` and clears the prompt after handing the URL to the native URI handler.
- `ReaderRuntimeCommonChromeTest` now guards the route so `ExternalLink` cannot regress back to hidden state-only handling.

Green check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.externalLinksOpenControllerOwnedExternalLinkPrompt" --tests "paige.navic.reader.ReaderControllerTest.dismissExternalLinkPromptClearsOnlyTheVisiblePrompt" --tests "paige.navic.reader.ReaderCoordinatorTest.externalLinkPromptDismissalIsControllerOwnedAndDoesNotTouchTheEngine" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderExternalLinksAreKomikkuOverlayAndNativeUriRouted"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroid
```

Result:

- PASS: focused controller/coordinator/Komikku overlay/native URI route guard.
- PASS: full Android host suite.
- PASS: Android unit test task.

Remaining:

- Android/emulator validation is still required: trigger a real external EPUB/PDF link, confirm the native prompt appears, verify Close does not open the browser, verify Open launches through Android, and verify no menu/tap-zone regression.
