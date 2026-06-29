# Komikku Reader Port Validation Log

This file holds concise test evidence for `2026-06-13-komikku-reader-port-design.md`.

The full historical log before compaction is preserved at:

- `docs/superpowers/specs/archive/2026-06-13-komikku-reader-port-design-full-log.md`

## 2026-06-19 Host Guard: Readable Drag Slop Split

Target:

- Source branch: `master`.
- Scope: fix the eta73/eta74 gesture split so readable-page mini-drift does not start a page-drag preview, while confirmed center taps remain native-owned.

Diagnosis:

- Eta73 guarded `onSingleTapConfirmed` with `!nativeTapCandidate`, which could reject legitimate center taps after GestureDetector confirmed them late in the touch lifecycle.
- Eta74 removed that guard and introduced `nativeTapCancelledByDrag`, but readable-page drag preview still used normal tap slop. That let tiny center-tap drift enter the page-drag preview path.
- Correct contract: shell-cover drag may use normal tap slop for responsiveness; readable EPUB/PDF page drag should use Android paging slop so the WebView/Foliate surface does not treat small tap drift as a page turn/preview.

Change:

- `KomikkuReaderNativeFrameHost.android.kt` now keeps `touchSlopPx` for shell-cover drag and adds `readablePageDragSlopPx = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()` for readable-page drag preview/release/logging.
- `onSingleTapConfirmed` still rejects only `nativeTapCancelledByDrag`; the eta73 `if (!nativeTapCandidate) return false` guard remains forbidden.
- `ReaderRuntimeShellProgressTest.nativeReadableDragPreviewUsesPagingSlopWithoutRestoringCandidateTapGuard` protects that split.

Evidence:

- RED: focused host test failed because production did not contain `readablePageDragSlopPx`.
- GREEN: `.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReadableDragPreviewUsesPagingSlopWithoutRestoringCandidateTapGuard"`.
- GREEN: `.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress"`.
- ADB limitation: the connected emulator release app is `v1.0.11-eta74` but blocked at login / `STOP, READ!`; the foreground readerdev task is `v1.0.11-eta72`. A controlled readerdev center tap did log `Reader native tap action=MENU`, proving ADB tap injection works, but it is not evidence for this HEAD fix.

Next required validation:

- Install a new release/dev candidate with this source and repeat real-reader checks: pure center tap, slight center-tap drift, intentional readable-page drag, cover center tap, and cover drag.

## 2026-06-18 Phone Corrupted Menu / Drag Preview Tap Leak

Target:

- Device: `RFCY80551LT` (`SM-F966B`, physical phone).
- Package: `darkaxt.navic`.
- App state supplied by user: EPUB already open in a corrupted menu state after a center tap on content.
- User report: text pages sometimes resize/split when opening chrome; short taps over images may advance/skip pages instead of opening chrome; suspected page-curl/drag work leaking into tap actions.

Evidence captured:

- Screenshot: `tmp\phone-corrupted-menu-current.png`.
- Visual state: Komikku chrome visible while the EPUB surface is split between an image column on the left and text on the right, showing page `8 / 273` and chapter rail `4 / 31`.
- DevTools snapshot from the live WebView showed a single Foliate `Paginator` renderer, no `data-navic-page-drag-preview-layer`, and the viewport positioned mid-column in one long content iframe. This means the corruption was not a stale preview overlay left in the DOM; the paginator itself had been left at an intermediate horizontal offset.
- Logcat around the gesture showed:
  - `Reader native drag candidate dx=26.277344 dy=-1.7060547 threshold=21.0`
  - multiple `Dispatching reader engine command: previewPageDrag(update)`
  - `Dispatching reader engine command: previewPageDrag(cancel)`
  - then `GestureDetector handleMessage TAP`
  - then `Reader native tap action=MENU ...`

Diagnosis:

- A small movement during what the user experiences as a tap crossed touch slop and started the native drag/curl preview path.
- The drag preview was cancelled, but `onSingleTapConfirmed` still accepted the same gesture as a normal center tap and toggled chrome.
- This violates the Komikku ownership rule: drag/curl preview belongs only to dragging gestures; a movement-cancelled drag candidate must not be reclassified as a reader tap.

Fix:

- `KomikkuReaderNativeFrameHost.android.kt` now rejects `onSingleTapConfirmed` when `nativeTapCandidate` has already been cleared by movement beyond slop.
- Added a host guard in `ReaderRuntimeShellProgressTest.nativeReaderSurfaceCenterMenuIsOwnedByNativeFrameInsteadOfWebViewHitTesting` requiring the `nativeTapCandidate` guard before tap-zone action classification.

Red/green evidence:

- RED: focused host test failed because `onSingleTapConfirmed` did not contain `if (!nativeTapCandidate) return false`.
- GREEN: `:composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress"`.
- GREEN: `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js`.
- GREEN: `git diff --check`.

Next required validation:

- Build/install a new Android candidate before claiming phone behavior fixed; the current phone APK predates this source change.
- On the physical phone, repeat: center tap over text, center tap over image, slight finger drift during center tap, and intentional drag/page turn. Expected result: slight-drift taps should not leave the paginator split or toggle chrome after a cancelled drag preview; intentional drags should still page normally.

## 2026-06-18 Eta73 Candidate Staging

Target:

- Release candidate version: `v1.0.11-eta73`, `versionCode=406`.
- Scope: gesture-leak guard, chapter-rail endpoint mapping changes already in the tree, and the matching host guards.

Checks:

- GREEN: `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta73`.
- GREEN: `:composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest"`.
- GREEN: broader reader host gate `:composeApp:testAndroidHost --tests "paige.navic.reader.*" --tests "paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest"`.
- GREEN: `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js`.
- GREEN: `git diff --check`.

Release note:

- Eta73 must be a new tag because `v1.0.11-eta72` already points at an older commit. Do not rebuild or republish eta72 for this fix.

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

## 2026-06-18 Host Guard: Anx Exists Entries Require Behavior Routes

Trigger:

- GLM audit correctly identified that a parity registry can go green while only proving symbols exist.
- The current implementation already routes the quoted controller no-ops into state/UI, but `FoliateAnxParityTest` still allowed high-risk `Exists` entries to be plain notes.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest.existsEntriesForAnxReaderBehaviorHaveVerifiedControllerOrUiRoutes"
```

Result:

- FAIL as expected before route metadata: `onRelocated` was marked `Exists` without any verified controller/UI behavior route.

Implemented:

- `GapStatus.Exists` now carries optional `behaviorRoute` metadata.
- High-risk Anx/Foliate behavior entries now point at both behavior tests and production controller/UI symbols.
- The guarded keys include relocation, TOC, search, annotations, internal/external links, selection clear/end, annotation click/draw, overlay creation, document load, push-state navigation, footnote close, and pull-up.

Green check:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest.existsEntriesForAnxReaderBehaviorHaveVerifiedControllerOrUiRoutes"
```

Result:

- PASS: each guarded `Exists` entry now has at least one verified controller or native UI route.

Remaining:

- Full host/unit verification is still required before committing this guard.

## 2026-06-18 Emulator Probe: Anx Relocation Payload Evidence

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence from `adb-reader-smoke`: PID `21759`, `versionName=v1.0.11-eta71`, `versionCode=404`, `lastUpdateTime=2026-06-18 16:15:18`.
- EPUB state: Bindery EPUB launched through `scripts\install-reader-dev.ps1`; native cover was dismissed with an edge tap and the WebView showed page `1 / 270`.

Trigger:

- GLM's audit correctly pushed against symbol-only Anx parity. The current controller/UI routes are no longer the quoted no-ops, but Phase 4 still needed runtime relocation payload evidence from a real WebView.
- The first relocation DevTools probe returned a valid runtime `locationChanged` payload but still failed because the harness waited for a monkey-patched Android JS bridge method that DevTools could not reliably replace.

Red checks:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperRelocationProbeReturnsEvidenceAfterDiagnosticDispatch" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderDiagnosticLocationSnapshotBypassesDuplicateSuppression"
```

Result:

- FAIL before implementation. The helper could still await `observedLocation`, and runtime diagnostic snapshots could be suppressed as duplicate relocations.

Implemented:

- `diagnosticLocationSnapshot` now forces a duplicate-safe post and returns the runtime `locationChanged` payload.
- `postCurrentLocationSnapshot` and `postLocationChanged` now return explicit posted/skipped status objects instead of fire-and-forget behavior.
- `adb-webview-eval.mjs` no longer waits indefinitely for a monkey-patched Android bridge method. It accepts the returned runtime `locationChanged` payload as evidence when `observedMessageCount` is zero.

Green checks:

```powershell
node --check tools\reader-harness\src\adb-webview-eval.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperRelocationProbeReturnsEvidenceAfterDiagnosticDispatch" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderDiagnosticLocationSnapshotBypassesDuplicateSuppression"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest"
```

Result:

- PASS: JS syntax for the DevTools helper and reader runtime.
- PASS: focused diagnostic-return host guards.
- PASS: `FoliateAnxParityTest`.

Emulator command:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe relocation-payload -ArtifactDir captures\reader-bridge-probes\20260618-161735-relocation
```

Artifact:

- `captures\reader-bridge-probes\20260618-161735-relocation\reader-devtools-probe.json`

Result:

- PASS: `locationSnapshotResult.posted=true`.
- PASS: returned `locationChanged` carried `href=OEBPS/Text/sinopsis.xhtml`, CFI `rangeCfi=epubcfi(/6/4!/4/2,,/4/1:586)`, `reason=adb-relocation-payload-probe`, `fraction=0.004370907849029098`, `pageCount=270`, `pageCountSource=pagination-profile`, and `paginationFingerprint=navic-pagination-v1:1062454681`.
- Note: `observedMessageCount=0` is expected for this probe path because DevTools cannot reliably monkey-patch the injected Android bridge method. The returned runtime payload is the evidence source.

Remaining:

- User-driven relocation flows still need runtime validation: tap, drag, progress rail, TOC, and resume must keep posting `reason` and CFI/null `rangeCfi`.
- Phase 3 still needs user-driven selection-clear and scrolled-edge pull-up gesture evidence before release-candidate claims; diagnostic bridge-path evidence is recorded in the later 2026-06-18 Phase 3 section.
- Phase 5 real-flow selection UI still needs emulator/device evidence before release-candidate claims.

## 2026-06-18 Emulator Probe: Phase 3 Bridge Events With Enforced Log Evidence

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence from `adb-reader-smoke`: `versionName=v1.0.11-eta71`, `versionCode=404`, `lastUpdateTime=2026-06-18 17:12:37`.
- EPUB state: dirty readerdev source rebuilt and installed, then launched through `scripts\install-reader-dev.ps1` until `Reader bridge raw: {"type":"publicationReady"}`.

Trigger:

- GLM's audit identified the recurring failure mode where bridge types/debug labels can exist while behavior is not actually consumed or validated.
- The first Phase 3 DevTools probe had the same shape: it appended `pullUp` to the probe JSON after synthetic touch dispatch, but the smoke script did not fail when Android logcat lacked `Reader bridge event: pullUp`.

Red checks:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderDiagnosticPullUpExercisesScrolledEdgeBridgePath"
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe phase3-events -ArtifactDir captures\reader-bridge-probes\20260618-continue-phase3-pullup-enforced
```

Result:

- FAIL as expected before implementation: the runtime did not expose `diagnosticScrolledEdgePullUp`.
- FAIL as expected with enforced smoke-script evidence: `Reader DevTools probe 'phase3-events' expected log label 'Reader bridge event: pullUp' was not captured`.

Implemented:

- `adb-reader-smoke.ps1` now parses `reader-devtools-probe.json` and fails when any probe-declared `expectedLogLabels` entry is absent from `logcat-reader.log`.
- `navic-reader.js` exposes a diagnostic `diagnosticScrolledEdgePullUp` command.
- `navic-reader-page-turns.js` implements that diagnostic command by temporarily placing the renderer at a scrolled bottom edge and invoking the real `turnScrolledEdgePage(-(ScrollEdgeTurnSwipeThreshold + 10))` path.
- `adb-webview-eval.mjs` now requires the diagnostic command to return `{ posted: true }` before it records `pullUp`.

Green checks:

```powershell
node --check tools\reader-harness\src\adb-webview-eval.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderDiagnosticPullUpExercisesScrolledEdgeBridgePath" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperInjectsReaderBridgeEventsThroughDevTools" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics"
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoLaunch -NoDiscoverPublication
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall -RequireReaderLaunch
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe phase3-events -ArtifactDir captures\reader-bridge-probes\20260618-phase3-pullup-diagnostic-command
```

Artifacts:

- `captures\reader-bridge-probes\20260618-phase3-pullup-diagnostic-command\reader-devtools-probe.json`
- `captures\reader-bridge-probes\20260618-phase3-pullup-diagnostic-command\logcat-reader.log`

Result:

- PASS: focused host guards for the diagnostic command, DevTools helper, and smoke-script expected-label enforcement.
- PASS: edited JS files parse with `node --check`.
- PASS: dirty readerdev APK installed and launched into a real EPUB with `publicationReady`.
- PASS: `reader-devtools-probe.json` records `external-link`, `draw-annotation`, `show-annotation`, `create-overlay`, `load`, `pushState`, `footnoteClose`, and `pullUp`.
- PASS: `diagnosticScrolledEdgePullUp` returned `posted=true`.
- PASS: `logcat-reader.log` contains all required Android bridge labels: `externalLink`, `annotationDrawn`, `annotationClick`, `overlayCreated`, `loadDoc`, `pushState`, `footnoteClose`, and `pullUp`.

Remaining:

- This is bridge-path evidence for the same scrolled-edge turn function used by the real listener, not manual proof that a user scrolled-edge gesture reliably reaches that function.
- Real-flow Phase 3 validation still needs user-driven or scripted selection-clear and scrolled-edge pull-up gestures without diagnostic commands.
- Phase 5 selection UI still needs Android/emulator proof: Highlight/Copy/Note must appear and behave without toggling reader chrome.

## 2026-06-18 Emulator Probe: Phase 5 Selection Payload and Native Action Overlay

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence from `adb-reader-smoke`: `versionName=v1.0.11-eta71`, `versionCode=404`, `lastUpdateTime=2026-06-18 17:34:00`.
- EPUB state: dirty readerdev build installed and launched into a real EPUB. The shell cover initially masked the selection action overlay, so the final evidence run first dismissed the shell cover through the native tap path.

Trigger:

- GLM's bridge audit correctly warned against symbol-only Anx parity. The current controller no longer discards the nine Phase 3 bridge events, but Phase 5 still needed proof that the selection payload reaches Android and produces user-visible selection actions.
- The first `selection-payload` probe showed a backend `selectionChanged` event but reported `footnote=false` even though the probe fixture set `role="doc-footnote"`.

Root cause:

- `selectionContextText()` and `selectionLooksLikeFootnote()` called `closestElement(node)` without a selector.
- `closestElement()` is selector-based, so the selectorless call returned null and footnote detection never started from the selected element.

Red check:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperSelectionProbeRequiresFootnoteEvidence"
```

Result:

- FAIL before implementation: the DevTools helper did not require `Reader bridge event: selectionChanged(footnote=true`.

Implemented:

- `navic-reader.js` now derives the selected element from `Range.commonAncestorContainer` through `selectionElement(range)` before reading context text or testing footnote selectors.
- `adb-webview-eval.mjs` now marks the selection probe successful only when Android logcat contains `Reader bridge event: selectionChanged(footnote=true`.
- `ReaderRuntimeAssetsTest` now guards that the probe fixture uses `role="doc-footnote"` and requires the footnote-positive Android log label.

Green checks:

```powershell
node --check tools\reader-harness\src\adb-webview-eval.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperSelectionProbeRequiresFootnoteEvidence"
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -TapFraction '0.90,0.50,900' -ReaderDevtoolsProbe selection-payload -ArtifactDir captures\reader-bridge-probes\20260618-selection-ui-after-cover-dismiss
```

Artifacts:

- `captures\reader-bridge-probes\20260618-selection-ui-after-cover-dismiss\reader-devtools-probe.json`
- `captures\reader-bridge-probes\20260618-selection-ui-after-cover-dismiss\logcat-reader.log`
- `captures\reader-bridge-probes\20260618-selection-ui-after-cover-dismiss\window.xml`
- `captures\reader-bridge-probes\20260618-selection-ui-after-cover-dismiss\screen.png`

Result:

- PASS: JS syntax for the DevTools helper and reader runtime.
- PASS: focused host guard for footnote-positive selection evidence.
- PASS: Android logcat contains `Reader bridge event: selectionChanged(footnote=true, pos=...)`.
- PASS: raw Android bridge payload contains `footnote:true`, CFI, `contextText`, and `pos` bounds.
- PASS: Android UI hierarchy contains native selection action controls: `Highlight`, `Copy`, and `Note`.

Remaining:

- This proves selection payload decoding and native action overlay presentation in a dirty emulator build. It does not yet prove the full action behavior for each button.
- Next Phase 5 validation should tap `Highlight`, `Copy`, and `Note`, then assert `ApplyHighlight`, clipboard write, note dialog/save, and `selectionCleared` behavior through controller-owned state.
- This is not a clean release APK validation.

## 2026-06-18 Emulator Probe: Native Highlight Selection Action

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence from `adb-reader-smoke`: `versionName=v1.0.11-eta71`, `versionCode=404`, `lastUpdateTime=2026-06-18 17:34:00`.

Trigger:

- The previous Phase 5 probe proved that the native `Highlight`, `Copy`, and `Note` overlay appears. It did not prove that tapping a native selection action reaches the engine adapter.
- `adb-reader-smoke.ps1` could not express this flow because all taps ran before DevTools probes. Selection-action validation needs the opposite order: create WebView selection through DevTools, then tap the native overlay.

Red check:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanTapNativeSelectionActionsAfterDevtoolsProbe"
```

Result:

- FAIL before implementation: the smoke script had no `PostProbeTap`, no `PostProbeTapFraction`, and no `RequireReaderEngineCommand` assertion.

Implemented:

- `adb-reader-smoke.ps1` now supports `-PostProbeTap` and `-PostProbeTapFraction`. These taps execute after the DevTools probe and before screenshot/logcat capture.
- `adb-reader-smoke.ps1` now supports `-RequireReaderEngineCommand`, asserting `Dispatching reader engine command: <command>` appears in captured Android logcat.

Green checks:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanTapNativeSelectionActionsAfterDevtoolsProbe"
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeTapFraction '0.28,0.06,1200' -RequireReaderEngineCommand applyHighlights -RequireReaderBridgeEvent annotationDrawn -ArtifactDir captures\reader-bridge-probes\20260618-selection-highlight-scripted-gate
```

Artifacts:

- `captures\reader-bridge-probes\20260618-selection-highlight-scripted-gate\reader-devtools-probe.json`
- `captures\reader-bridge-probes\20260618-selection-highlight-scripted-gate\logcat-reader.log`
- `captures\reader-bridge-probes\20260618-selection-highlight-scripted-gate\reader-diagnostics-summary.txt`
- `captures\reader-bridge-probes\20260618-selection-highlight-scripted-gate\window.xml`

Result:

- PASS: host guard for post-probe native selection action taps.
- PASS: DevTools probe created a footnote-positive selection with CFI and context payload.
- PASS: native Highlight tap dispatched `applyHighlights(count=2)` through `ReaderEngineWebViewHost`.
- PASS: Foliate emitted `annotationDrawn` for the selected CFI after the engine command.
- PASS: smoke-script bridge assertion recorded `bridgeEvent:annotationDrawn=True`.

Remaining:

- This proves the Highlight action reaches the engine and Foliate annotation draw path in a dirty emulator build.
- Superseded by the later `2026-06-18 Emulator Probe: Native Copy and Note Selection Actions`: Copy now has a repeatable native app-boundary assertion, and Note now has a repeatable native dialog/save assertion.
- Selection clear after action remains a UX behavior decision and is not yet validated.

## 2026-06-18 Host Check: GLM Types-Only Bridge Audit Reconciliation

Trigger:

- GLM reported that Anx Phase 2-8 work was types-only and that the nine new bridge/engine events were discarded in `ReaderController.kt`.

Inspection:

- The current branch no longer contains the quoted no-op controller branches.
- `ReaderController.kt` routes internal/external links, annotation click/draw, overlay creation, loaded document, navigation state, footnote close, and pull-up into controller state or UI-facing prompt/popup state.
- `FoliateAnxParityTest.kt` now requires behavior routes for Anx entries marked `Exists` and rejects the old no-op branch strings.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.FoliateAnxParityTest
```

Result:

- PASS: `ReaderControllerTest` and `FoliateAnxParityTest` completed through `testAndroidHostTest`.
- PASS: Gradle output reported `BUILD SUCCESSFUL in 7s`.

Conclusion:

- GLM's concrete no-op diagnosis is stale for the current branch.
- The underlying guardrail is valid and remains mandatory: an Anx entry must not be marked `Exists` unless it has a controller behavior route or native UI route.

Remaining:

- Clean release APK validation is still required for the high-priority reader bugs.
- Resume/persistence after disrupted drag/app recreation still needs device evidence.
- Superseded by the later `2026-06-18 Emulator Probe: Native Copy and Note Selection Actions`: Copy and Note now have button-level dirty-emulator smoke gates after the existing selection overlay and Highlight gates.

## 2026-06-18 Emulator Probe: Native Copy and Note Selection Actions

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence from `adb-reader-smoke`: `versionName=v1.0.11-eta71`, `versionCode=404`, `lastUpdateTime=2026-06-18 18:22:03`.

Trigger:

- GLM's bridge audit correctly warned that source-level Anx parity can still be behavior-empty.
- Previous Phase 5 evidence proved the selection payload, native action overlay, and Highlight action. Copy and Note still needed behavior gates at the native UI boundary.
- Coordinate-based post-probe taps were too brittle after menu/selection overlay movement. The smoke harness needed to tap visible Android UI nodes by `text` or `content-desc`.

Red check:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --rerun-tasks --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanTapNativeSelectionActionsAfterDevtoolsProbe --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted
```

Result:

- FAIL before implementation: `ReaderRuntimeAssetsTest.kt:287` because `adb-reader-smoke.ps1` had no `tapText:`, `tapDesc:`, `Get-AdbUiNodeCenter`, or `Invoke-PostProbeUiNodeAction` support.
- Earlier Copy evidence also showed that Android shell clipboard content is not a reliable assertion surface, so the app boundary needed an explicit log after `LocalClipboardManager.setText`.

Implemented:

- `adb-reader-smoke.ps1` now supports ordered pipe-delimited `-PostProbeAction` entries after a DevTools probe: `tap:`, `tapFraction:`, `tapText:`, `tapDesc:`, `text:`, and `keyevent:`.
- `tapText:` and `tapDesc:` dump the current Android hierarchy through `uiautomator`, resolve the matching node bounds, and tap the node center.
- `adb-reader-smoke.ps1` now supports `-RequireReaderLog` so native app-boundary logs can be required, not just bridge/debug-label logs.
- `ReaderScreen` logs `Reader selection copied length=<n>` immediately after the native clipboard write.

Green host check:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --rerun-tasks --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanTapNativeSelectionActionsAfterDevtoolsProbe --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted
```

Result:

- PASS: focused host gate completed through `testAndroidHostTest`.
- PASS: Gradle output reported `BUILD SUCCESSFUL in 2m 29s`.

Dirty emulator checks:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Note,1200' -ArtifactDir captures\reader-bridge-probes\20260618-selection-note-open-capture
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -PostProbeAction 'tapText:Annotation,700|text:Codex_note_gate,1000|tapText:Save,1200' -RequireReaderEngineCommand 'applyHighlights' -RequireReaderBridgeEvent 'annotationDrawn' -ArtifactDir captures\reader-bridge-probes\20260618-selection-note-save-node-gate
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Copy,1200' -RequireReaderLog 'Reader selection copied length=' -ArtifactDir captures\reader-bridge-probes\20260618-selection-copy-node-gate
```

Artifacts:

- `captures\reader-bridge-probes\20260618-selection-note-open-capture\window.xml`
- `captures\reader-bridge-probes\20260618-selection-note-open-capture\screen.png`
- `captures\reader-bridge-probes\20260618-selection-note-save-node-gate\logcat-reader.log`
- `captures\reader-bridge-probes\20260618-selection-note-save-node-gate\reader-diagnostics-summary.txt`
- `captures\reader-bridge-probes\20260618-selection-copy-node-gate\logcat-reader.log`
- `captures\reader-bridge-probes\20260618-selection-copy-node-gate\reader-devtools-probe.json`

Result:

- PASS: `tapText:Note` opens the native note dialog. The captured hierarchy contains `Note`, selected text, `Annotation`, `Cancel`, and disabled `Save`.
- PASS: after tapping `Annotation` and entering `Codex_note_gate`, tapping `Save` dispatches `applyHighlights(count=1)`.
- PASS: the note save path emits `annotationDrawn` through the WebView bridge.
- PASS: the Copy action receives a fresh footnote-positive `selectionChanged` payload and reaches the native clipboard boundary, proven by `Reader selection copied length=31`.

Important failed attempt:

- The combined Note flow `tapText:Note|tapText:Annotation|text:...|keyevent:KEYCODE_BACK|tapText:Save` failed because `KEYCODE_BACK` dismissed the native note dialog instead of only closing the IME. Do not use Back as a required part of this gate.

Remaining:

- This is dirty emulator evidence, not a clean release APK validation.
- User-driven normal text selection, selection clear, and real scrolled-edge pull-up still need validation.
- Copy's observable proof is the native app boundary log after `LocalClipboardManager.setText`; Android shell does not provide a reliable cross-app clipboard read for this assertion.

## 2026-06-18 Host/Emulator Check: GLM Audit Follow-Up and Selection Clear

Trigger:

- GLM repeated the bridge-events-are-types-only warning and cited stale `ReaderController` no-op branches.
- The remaining active gap was not controller wiring, but proving that selection clear reaches Android from a user-like action.

Host verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes --tests paige.navic.reader.FoliateAnxParityTest.existsEntriesForAnxReaderBehaviorHaveVerifiedControllerOrUiRoutes
```

Result:

- PASS: Gradle reported `BUILD SUCCESSFUL in 15s`.
- PASS: the current branch still rejects the quoted no-op Phase 3 controller branches and requires behavior routes for Anx `Exists` entries.

Dirty emulator check:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapFraction:0.12,0.82,1200' -RequireReaderBridgeEvent 'selectionCleared' -ArtifactDir captures\reader-bridge-probes\20260618-selection-clear-user-tap-gate
```

Artifacts:

- `captures\reader-bridge-probes\20260618-selection-clear-user-tap-gate\reader-diagnostics-summary.txt`
- `captures\reader-bridge-probes\20260618-selection-clear-user-tap-gate\logcat-reader.log`
- `captures\reader-bridge-probes\20260618-selection-clear-user-tap-gate\reader-devtools-probe.json`

Result:

- PASS: the probe produced a footnote-positive `selectionChanged` payload.
- PASS: the post-probe ADB tap produced `Reader bridge event: selectionCleared()`.
- PASS: diagnostics reported `readerCenterDispatch=False`, so clearing the selection did not toggle the reader menu.

Remaining:

- This is dirty-emulator evidence. The installed release APK still needs validation before a release-candidate claim.
- The selection was created by DevTools and cleared by an ADB tap. Real manual normal-text selection and real scrolled-edge pull-up still need validation without diagnostic setup.

## 2026-06-18 Dirty Emulator Matrix: eta71 Continuation

Trigger:

- Continue from the GLM audit follow-up after confirming the controller no-op finding was stale on the current branch.
- Re-run the scripted Komikku reader matrix on the installed dirty emulator build to check whether native tap/drag/texture paths still held after the Anx parity and selection-action work.

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta71`, `versionCode=404`, `lastUpdateTime=2026-06-18 18:22:03`.

Command:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta71 -NoLaunch -IncludeCoverChecks -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\eta71-continuation-20260618
```

Artifacts:

- `captures\reader-komikku-matrix\eta71-continuation-20260618\reader-matrix-summary.csv`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\reader-matrix-failures.txt`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\baseline-native-cover\screen.png`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\cover-drag-next\reader-diagnostics-summary.txt`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\drag-next\reader-diagnostics-summary.txt`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\drag-previous\reader-diagnostics-summary.txt`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\texture-next-walk\reader-diagnostics-summary.txt`
- `captures\reader-komikku-matrix\eta71-continuation-20260618\texture-previous-walk\reader-diagnostics-summary.txt`

Result:

- PASS: all matrix rows passed: `baseline-current-reader`, `baseline-native-cover`, `cover-center-tap-toggle`, `cover-drag-next`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk`.
- PASS: `reader-matrix-failures.txt` reported `No matrix failures`.
- PASS: cover drag used the shell-cover path: `shellCoverDragCandidate=True`, `shellCoverSwipe=True`, `shellCoverCommand=True`.
- PASS: normal page drag used the native drag-preview path in both directions: `readerNativeDragPreview=True` for `drag-next` and `drag-previous`.
- PASS: scripted texture direction sampling did not flag inversion: `wrongTextureDirection=False` for `drag-next`, `drag-previous`, `texture-next-walk`, and `texture-previous-walk`.
- PASS: visual inspection of `baseline-native-cover\screen.png` showed the native cover on a black cover surface without the bottom menu overlay.

Remaining:

- This is dirty-emulator evidence, not physical-phone release evidence.
- The matrix does not exercise progress rail endpoints, progress rail chapter buttons, resume after app/window interruption, real manual normal-text selection, or real scrolled-edge pull-up.
- Manual drag feel remains Priority 1 because the matrix only proves the native drag-preview log path and direction sampling, not the final perceptual quality.

## 2026-06-18 Host Check: Progress Rail Native Targeting Guard

Trigger:

- The progress rail still needs release/device validation for endpoint bugs (`10 / 12`, `2 / 4`, and page-1 chapter button behavior).
- Coordinate-based progress tests are too brittle because the Komikku rail is responsive and can be hidden for one-page or two-page sections.

Implemented:

- The chapter progress slider now exposes a merged native semantics descriptor: `Chapter page slider`.
- `adb-reader-smoke.ps1` can now resolve an Android UI node by content description and tap a fractional point inside it with `tapDescFraction:value,xFraction,yFraction,waitMs`.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderVerticalProgressRailUsesKomikkuSliderOwnedNavigator --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanTapNativeSelectionActionsAfterDevtoolsProbe
```

Result:

- PASS: Gradle reported `BUILD SUCCESSFUL in 2m 38s`.
- PASS: `scripts\adb-reader-smoke.ps1` parsed successfully with PowerShell's parser.
- PASS: `git diff --check` reported no whitespace errors.

Runtime note:

- A dirty emulator probe for `tapDescFraction:Chapter page slider,...` was attempted, but the current section had too few local pages and correctly did not render the slider. Do not count progress rail endpoints as fixed until a visible-slider section is targeted and first/last page navigation is verified from UI-node fractions.

## 2026-06-18 Dirty Emulator Matrix: eta72 Pre-Release

Trigger:

- Prepare `v1.0.11-eta72` only after host verification and an emulator gate.
- Confirm that the progress-rail targeting guard did not regress the Komikku-owned reader shell.

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta72`, `versionCode=405`, `lastUpdateTime=2026-06-18 20:23:48`.

Command:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta72 -NoLaunch -IncludeCoverChecks -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\eta72-pre-release-20260618
```

Artifacts:

- `captures\reader-komikku-matrix\eta72-pre-release-20260618\reader-matrix-summary.csv`
- `captures\reader-komikku-matrix\eta72-pre-release-20260618\reader-matrix-failures.txt`
- `captures\reader-komikku-matrix\eta72-pre-release-20260618\baseline-native-cover\screen.png`
- `captures\reader-komikku-matrix\eta72-pre-release-20260618\cover-drag-next\reader-diagnostics-summary.txt`
- `captures\reader-komikku-matrix\eta72-pre-release-20260618\drag-next\reader-diagnostics-summary.txt`
- `captures\reader-komikku-matrix\eta72-pre-release-20260618\texture-previous-walk\reader-diagnostics-summary.txt`

Result:

- PASS: all matrix rows passed: `baseline-current-reader`, `baseline-native-cover`, `cover-center-tap-toggle`, `cover-drag-next`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk`.
- PASS: `reader-matrix-failures.txt` reported `No matrix failures`.
- PASS: cover drag still uses the shell-cover path: `shellCoverDragCandidate=True`, `shellCoverSwipe=True`, `shellCoverCommand=True`.
- PASS: normal page drag still uses native drag preview: `readerNativeDragPreview=True` for `drag-next`.
- PASS: scripted texture direction sampling did not flag inversion: `wrongTextureDirection=False`.

Remaining:

- Progress rail endpoints are still not closed. Eta72 only makes the rail targetable by native semantics and ADB fractional node taps; endpoint behavior must still be validated on a visible-slider section and on the phone release.
- This is dirty readerdev emulator evidence, not physical-phone release evidence.

## 2026-06-18 Dirty Emulator Check: eta72 Progress Rail Endpoint

Trigger:

- Follow up the eta72 note that the current section did not render `Chapter page slider`.
- Classify whether the hidden rail was a rendering/control bug or expected chapter-local behavior.
- Exercise a visible-slider section before making any production change.

Target:

- Serial: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed evidence: `versionName=v1.0.11-eta72`, `versionCode=405`.

Evidence:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe relocation-payload
adb -s emulator-5554 shell input tap 997 2064
adb -s emulator-5554 shell input tap 996 1810
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe relocation-payload
```

Artifacts:

- `captures\reader-progress-rail\eta72-slider-bottom-current.png`

Result:

- PASS: Foreword hidden-slider behavior is expected for the current section. The live relocation payload reported `href=OEBPS/Text/authorsforeword.xhtml`, global `pageIndex=5`, global `pageCount=270`, but chapter-local `chapterPageIndex=0`, `chapterPageCount=2`; the Komikku rail intentionally hides the slider for chapters with fewer than 3 local pages.
- PASS: Native next-chapter button moved from Foreword to Chapter 1. The live relocation payload reported `href=OEBPS/Text/capitancebolleta01.xhtml`, `tocTitle=Chapter 1`, `chapterPageIndex=0`, `chapterPageCount=10`.
- PASS: On Chapter 1, the native hierarchy exposed `Chapter page slider` and the endpoint tap at the bottom of the rail dispatched `goToChapterProgress(OEBPS/Text/capitancebolleta01.xhtml, 1.0)`.
- PASS: The engine landed at the final local chapter page: relocation reported `chapterProgress=1`, `chapterPageIndex=9`, `chapterPageCount=10`, with global `pageIndex=17`, `pageCount=270`.

Remaining:

- This closes only the eta72 dirty-emulator visible-slider endpoint check for one known-good section.
- It does not close the phone/release report of `10 / 12`, `2 / 4`, or page-1 rail-button failures. Those still need release-device reproduction or a broader scripted matrix across multiple chapters.
- The test used direct ADB taps after identifying the native node bounds. A scripted `tapDescFraction:Chapter page slider,...` release-device check should be preferred for repeatable release validation.

## 2026-06-18 Host Guard: Anx PullUp Must Show Controller Chrome

Trigger:

- Continue the GLM audit follow-up: prove Anx bridge events are not only typed/decoded but routed into behavior.
- `onPullUp` was still weaker than the Anx reference. Anx `epub_player.dart` routes it to `widget.showOrHideAppBarAndBottomBar(true)`, while Navic only stored `ReaderOverlayInteraction.PullUp`.

Implemented:

- `ReaderController` now handles `ReaderEngineEvent.PullUp` by storing `ReaderOverlayInteraction.PullUp` and setting `menuVisible = true`.
- `FoliateAnxParityTest` now requires the `onPullUp` behavior route to include `menuVisible = true`, not only `ReaderOverlayInteraction.PullUp`.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Result:

- RED: before implementation, `ReaderControllerTest.anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded` failed at `ReaderControllerTest.kt:222` because `PullUp` did not show controller chrome.
- GREEN: after implementation, `:composeApp:testAndroid` passed with `BUILD SUCCESSFUL in 2m 24s`.

Remaining:

- This is host verification only. A real scrolled-edge pull-up gesture still needs emulator/device validation without diagnostic injection.

## 2026-06-18 Host Guard: Anx PushState Must Surface Native History Controls

Trigger:

- Continue the GLM types-only parity audit: Anx `onPushState` was decoded into state but did not expose the same user-facing history route as the reference.
- Anx `epub_player.dart` sets history visibility from `canGoBack || canGoForward` and renders a back/close/forward capsule; Foliate history actions call `reader.view.history.back()` / `forward()`.

Implemented:

- `ReaderEngineNavigationState` now carries `visible` and `ReaderController` updates it from `ReaderEngineEvent.NavigationStateChanged`.
- `KomikkuReaderHistoryCapsule` renders a native bottom-centered history capsule outside the WebView surface.
- `ReaderCoordinator` and `ReaderController` route history back/forward/dismiss actions through the controller boundary.
- `ReaderEngineCommand.NavigateHistory` maps through `FoliateEpubEngineAdapter` to `ReaderBridgeCommand.HistoryBack` / `HistoryForward`.
- `navic-reader.js` dispatches `historyBack` / `historyForward` to Foliate `view.history.back()` / `forward()`.
- `FoliateAnxParityTest` now requires controller behavior, UI route, bridge commands, adapter route, and runtime dispatch before `onPushState` can remain marked `Exists`.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

Result:

- RED: the first `:composeApp:testAndroid` run failed at compile time because the new behavior test referenced missing `ReaderEngineCommand.NavigateHistory`, `ReaderHistoryDirection`, `ReaderBridgeCommand.HistoryBack`, `ReaderBridgeCommand.HistoryForward`, history coordinator functions, and `ReaderEngineNavigationState.visible`.
- GREEN: after implementation, `:composeApp:testAndroid` passed with `BUILD SUCCESSFUL in 24s`.
- GREEN: `node --check` passed for `navic-reader.js`.
- GREEN: `git diff --check` passed.
- FRESH CHECK after design/log sync: `:composeApp:testAndroid` passed with `BUILD SUCCESSFUL in 9s`, `node --check composeApp\src\androidMain\assets\reader\navic-reader.js` passed, and `git diff --check` passed.

Remaining:

- This is host verification only. Emulator/device validation must still open a real EPUB, navigate through an internal link/search result that creates Foliate history, confirm the native capsule appears, tap back/forward, and confirm close hides only the capsule.

## 2026-06-18 Dirty Emulator Check: PushState History Capsule Route

Trigger:

- Validate the current uncommitted PushState/history capsule slice on the reader-dev package before committing it.
- Confirm whether the earlier missing `History back` / `Close history controls` hierarchy check was a real route failure or a blocked/covered UI state.

Environment:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed package evidence: `versionName=v1.0.11-eta72`, `versionCode=405`, `lastUpdateTime=2026-06-18 21:48:05`
- Reader state: real EPUB loaded through Bindery debug credentials, title `Alcatraz versus the Evil Librarians`

Commands:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe history-controls
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-history-after-probe.xml
adb -s emulator-5554 shell input tap 1010 1200
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-after-cover-edge.xml
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell input tap 478 2228
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-after-history-back.xml
adb -s emulator-5554 logcat -d -t 100
```

Artifacts:

- `captures\reader-history-controls\clean-reader-before-history-probe.png`
- `captures\reader-history-controls\history-controls-clean-after-probe.png`
- `captures\reader-history-controls\after-cover-edge-tap.png`
- `captures\reader-history-controls\after-history-back-tap.png`
- `tmp\reader-test-runs\navic-history-after-probe.xml`
- `tmp\reader-test-runs\navic-after-cover-edge.xml`
- `tmp\reader-test-runs\navic-after-history-back.xml`

Result:

- PASS: the WebView history probe returned `canGoBack=true`, `canGoForward=false`, and logcat emitted `Reader bridge event: pushState(back=true, forward=false)`.
- PASS: the first missing-capsule hierarchy check was explained by state, not by a broken route. The native shell cover was still visible, and `ReaderRoot.kt` intentionally suppresses `KomikkuReaderHistoryCapsule` while `controllerState.shellCoverVisible` is true.
- PASS: after dismissing the native cover through the reader surface, the native hierarchy exposed `History back` and `Close history controls`.
- PASS: tapping `History back` dispatched `Dispatching reader engine command: historyBack`, and Foliate emitted `Reader bridge event: pushState(back=false, forward=true)`.
- PASS: after the back command, the native hierarchy exposed `Close history controls` and `History forward`.

Remaining:

- This validates the dirty-emulator PushState/capsule command route only.
- It does not validate a user-driven internal-link/search-result flow, phone release behavior, or whether the capsule should also be visible above the native shell cover. The current implementation intentionally hides it while the shell cover is active.

Post-check verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\adb-webview-eval.mjs
git diff --check
```

Result:

- GREEN: `:composeApp:testAndroid` completed with `BUILD SUCCESSFUL in 16s`, `24 actionable tasks: 1 executed, 23 up-to-date`.
- GREEN: both `node --check` commands passed.
- GREEN: `git diff --check` passed.

## 2026-06-18 Host Guard: Bottom Toolbar Must Not Duplicate Settings

Trigger:

- User asked whether the bottom toolbar needs two buttons that effectively open the same settings window.
- Reference decision: no. Komikku-style bottom actions must be distinct controller actions. The Reading tab remains inside the settings sheet, but the bottom bar must not render a second book/reading-mode icon that opens the same dialog family as settings.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomToolbarDoesNotExposeDuplicateSettingsDialogs
```

Result:

- RED: the first targeted host run failed at `ReaderRuntimeCommonChromeTest.kt:586` because `KomikkuReaderBottomButton.NAVIC_SUPPORTED_DEFAULTS` still included `ReadingMode`.
- GREEN: after implementation, the same targeted host run completed with `BUILD SUCCESSFUL in 2m 32s`.
- FRESH BROADER CHECK: `.\gradlew.bat --no-daemon :composeApp:testAndroid` completed with `BUILD SUCCESSFUL in 28s`, `24 actionable tasks: 2 executed, 22 up-to-date`.

Implementation notes:

- `ReaderAppBars.kt` keeps Komikku's `ReaderBottomButton` model but no longer exposes `ReadingMode` in `NAVIC_SUPPORTED_DEFAULTS`.
- The bottom toolbar now exposes contents, search, and a single settings entry point.
- `ReaderControllerDialog.ReadingMode` remains available internally so the Reading tab route is not deleted; it is only removed as a duplicate bottom action.

Remaining:

- This is a host/source guard only. No release build was created for this micro-cleanup because it is not a major reader milestone.

## 2026-06-19 Host Guard: Loaded Document Resets Chapter Rail Anchor

Trigger:

- User-reported rail/navigation failures after jumping through contents/links: the UI could still look anchored to the previous section, making chapter arrows and page seeking target the wrong place.
- Reference decision: Anx/Foliate `LoadDoc` is an engine section-boundary signal. Navic must not treat it as debug-only metadata; the Komikku controller-owned rail should reset to the newly loaded document until relocation/page-count data catches up.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.loadedDocumentBecomesChapterNavigationAnchorBeforeRelocationCatchesUp" --tests "paige.navic.reader.ReaderControllerTest.loadedDocumentPreventsChapterPageSeekFromTargetingPreviousSection"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorArrowsUseTocChapterNavigationCallbacks" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost
```

Result:

- RED: the first targeted host run failed both new tests because `DocLoaded` only updated `loadedDocument`; `chapterProgress` remained anchored to the previous section.
- GREEN: after implementation, the targeted run completed with `BUILD SUCCESSFUL in 10s`.
- GREEN: the broader controller/chrome run completed with `BUILD SUCCESSFUL in 21s`.
- GREEN: full Android host suite completed with `BUILD SUCCESSFUL in 29s`, `1434 tests completed`.

Implementation notes:

- `ReaderController` now converts `ReaderEngineEvent.DocLoaded` into both `loadedDocument` state and a chapter-progress anchor update.
- If the loaded document changes section, the controller resets chapter progress to the new href/title with `pageIndex=0`, `pageCount=1`, and `progress=0.0` until the next relocation supplies the real page model.
- Same-document load events preserve the existing page model to avoid flicker.
- `FoliateAnxParityTest` now guards the stronger Anx `LoadDoc` behavior route instead of accepting a type-only `loadedDocument` assignment.

Remaining:

- This is a host/controller guard only. It does not close the clean-release rail endpoint validation item.
- Device/emulator matrix still needs to prove contents/link jumps, rail dragging, rail endpoint taps, and adjacent chapter buttons against a real EPUB runtime.

## 2026-06-19 Host + Readerdev: Mini-Drag Suppression Without Killing Center Tap

Trigger:

- User asked whether the intended mini-drag disablement had instead killed center tap.
- Prior eta73-style guard tied confirmed taps to `nativeTapCandidate`, which can be stale by the time Android `GestureDetector` confirms a single tap.
- Current HEAD already removed that guard and split readable-page drag slop from shell-cover drag slop, but ADB still showed sub-threshold movement leaking into Foliate's paginator `touchmove` path.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeSurfaceOwnsTaps"
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoDiscoverPublication
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall
adb shell input tap 1000 1200
adb shell input swipe 540 1200 570 1200 120
adb shell input tap 540 1200
adb shell input swipe 900 1200 180 1200 450
```

Result:

- RED: the new host guard failed before implementation because native tap-zone mode rendered the overlay but did not attach any Foliate touch suppressor.
- GREEN: after implementation, the focused host guard completed with `BUILD SUCCESSFUL in 12s`.
- GREEN: `node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js` passed.
- ADB before suppressor: 30px readable-page drift produced Foliate console errors from `vendor/foliate-js/paginator.js:990` (`Ignored attempt to cancel a touchmove event...`), proving sub-threshold movement still reached Foliate.
- ADB after suppressor: same 30px drift produced no matching `Reader native drag`, `Reader native readable swipe`, `relocate`, `loadDoc`, `Reader console ERROR`, or `paginator.js` log entries, and the screenshot stayed on page `1 / 1534`.
- ADB after suppressor: center tap on readable page still logged `Reader native tap action=MENU` and surfaced chrome.
- ADB after suppressor: full leftward drag still logged `Reader native drag candidate`, `Reader native drag preview`, and `Reader native readable swipe action=Right ... threshold=42.0`, then advanced to page `2 / 1534`.

Implementation notes:

- `attachReaderTapZoneGesture()` now installs `attachNativeTapZoneTouchSuppressor(target)` when `nativeTapZones === true`.
- The suppressor is JS-side and capture-phase. It stops Foliate's `touchstart/touchmove/touchend/touchcancel` propagation in native mode, and only calls `preventDefault()` for cancelable `touchmove`.
- Android center taps remain owned by `KomikkuReaderNativeFrameHost`; the forbidden `if (!nativeTapCandidate) return false` guard was not restored.

Artifacts:

- `tmp\readerdev-suppressor-ready.png`: native cover baseline after current readerdev launch.
- `tmp\readerdev-suppressor-readable.png`: readable page before post-fix gestures.
- `tmp\readerdev-suppressor-small-drift.png`: readable page after 30px drift, unchanged.
- `tmp\readerdev-suppressor-center-tap.png`: readable page after post-fix center tap, chrome visible.
- `tmp\readerdev-suppressor-real-drag.png`: readable page after full native drag, advanced to `2 / 1534`.

Remaining:

- This validates readerdev/emulator behavior only. It should be included in the next release candidate because it directly addresses a major touch regression class.

## 2026-06-19 Release Candidate: v1.0.11-eta75

Trigger:

- Publish the mini-drag suppressor and restored center-tap behavior as a proper Android release candidate because this is a major reader input regression class.

Local gates before tagging:

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta75
.\gradlew.bat --no-daemon :composeApp:testAndroidHost
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
git diff --check
```

Result:

- `verify-android-release-version.ps1`: `Android versionName matches v1.0.11-eta75`.
- `:composeApp:testAndroidHost`: `BUILD SUCCESSFUL in 16s`.
- `node --check`: passed.
- `git diff --check`: passed.
- Release metadata commit: `8def3bd1 Prepare eta75 Android release`.
- Release tag: `v1.0.11-eta75`.
- GitHub Actions run: `https://github.com/Darkaxt/Navic/actions/runs/27795532041`.
- Android release build completed successfully; release signing verification passed.
- iOS IPA job was skipped, as expected for dashed eta tags.
- GitHub release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta75`.
- APK asset: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta75/Navic.apk`.
- GitHub asset digest: `sha256:d0a0a6eabcd0f3dad683ebceaced436c5f5b0daa76ba603b2b284f93ff5bd4d5`.

Readerdev/emulator note:

- A readerdev reinstall attempt after the eta75 metadata bump did not complete inside the command-runner ceiling, and the emulator still reported `darkaxt.navic.readerdev` as `versionName=v1.0.11-eta74`.
- Do not count that attempted readerdev matrix as eta75 validation. The eta75 proof is the host gates plus GitHub Android release build/signing only.

Remaining:

- User/phone validation is still required for the restored center tap, mini-drag suppression, real drag paging, and the existing progress rail/open P0 items.

## 2026-06-19 eta75 Readerdev Emulator Follow-Up

Trigger:

- Supersede the earlier eta75 readerdev note after isolating the command-runner ceiling from the actual build/install/reader launch path.
- Verify the published eta75 source still opens a real Bindery EPUB and passes the Komikku native input matrix in the dirty readerdev environment.

Commands:

```powershell
.\gradlew.bat --no-daemon :androidApp:assembleReaderDev --stacktrace
adb -s emulator-5554 install -r androidApp\build\outputs\apk\readerDev\Navic.apk
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall -RequireReaderLaunch
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -RequireReaderLaunch
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -IncludeCoverChecks -ArtifactRoot tmp\reader-matrix-eta75-readerdev-20260619-0245
```

Result:

- PASS: `:androidApp:assembleReaderDev` completed successfully and produced `androidApp\build\outputs\apk\readerDev\Navic.apk`.
- PASS: direct ADB install returned `Success`.
- PASS: package manager reported `versionName=v1.0.11-eta75`, `versionCode=408`, and `lastUpdateTime=2026-06-19 02:43:07`.
- PASS: `install-reader-dev.ps1 -NoBuild -NoInstall -RequireReaderLaunch` discovered `A Memory of Light (epub)`, foregrounded `darkaxt.navic.readerdev`, and received `Reader bridge raw: {"type":"publicationReady"}`.
- PASS: full `install-reader-dev.ps1 -RequireReaderLaunch` completed after an up-to-date readerdev build/install and again received `publicationReady`.
- PASS: Komikku matrix artifact root `tmp\reader-matrix-eta75-readerdev-20260619-0245`.
- PASS: matrix rows `baseline-current-reader`, `baseline-native-cover`, `cover-center-tap-toggle`, `cover-drag-next`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` all reported `PASS`.
- PASS: `reader-matrix-failures.txt` reported `No matrix failures.`

Interpretation:

- The earlier eta75 note remains historically true for that failed command invocation, but it is no longer the latest readerdev/emulator state.
- eta75 now has dirty readerdev emulator evidence for restored center tap, mini-drag suppression, cover drag, readable drag, edge taps, and texture direction sampling.
- This still does not close physical-phone release validation, progress rail endpoint validation, resume after app/window interruption, real manual text selection, or real scrolled-edge pull-up.

## 2026-06-19 eta75 Phase 5 Selection Action Follow-Up

Trigger:

- Validate the current eta75 readerdev build against the active Phase 5 selection-action requirement before moving to the next reader-port gap.

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed version: `versionName=v1.0.11-eta75`, `versionCode=408`, `lastUpdateTime=2026-06-19 02:47:55`

Commands:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -TapFraction '0.90,0.50,900' -ReaderDevtoolsProbe selection-payload -ArtifactDir tmp\eta75-selection-ui-after-cover-dismiss
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Highlight,1200' -RequireReaderEngineCommand 'applyHighlights' -RequireReaderBridgeEvent 'annotationDrawn' -ArtifactDir tmp\eta75-selection-highlight-gate
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Copy,1200' -RequireReaderLog 'Reader selection copied length=' -ArtifactDir tmp\eta75-selection-copy-gate
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Note,1200' -ArtifactDir tmp\eta75-selection-note-open
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -PostProbeAction 'tapText:Annotation,700|text:Eta75_note_gate,1000|tapText:Save,1200' -RequireReaderEngineCommand 'applyHighlights' -RequireReaderBridgeEvent 'annotationDrawn' -ArtifactDir tmp\eta75-selection-note-save-gate
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -LongPressFraction '0.50,0.50,950,900' -RequireNativeLongTap -RequireReaderBridgeEvent selectionChanged -ArtifactDir tmp\eta75-selection-native-longpress-gate
```

Result:

- PASS: selection payload probe emitted `selectionChanged` with `footnote=true`, CFI, context text, and position bounds.
- PASS: Android hierarchy for `tmp\eta75-selection-ui-after-cover-dismiss` contained the native selection action overlay entries `Highlight`, `Copy`, and `Note`.
- PASS: Highlight action dispatched `applyHighlights(count=1)` and received `annotationDrawn`.
- PASS: Copy action reached the app boundary with `Reader selection copied length=31`.
- PASS: Note action opened the native note dialog; the hierarchy contained `Note`, selected text, `Annotation`, `Cancel`, and disabled `Save`.
- PASS: Note save action typed `Eta75_note_gate`, dispatched `applyHighlights`, and received `annotationDrawn`.
- PASS: native long press produced `Reader native long tap x=540.0 y=1200.0` and a real `selectionChanged` bridge event for selected text `Perrin` with `footnote=false`, CFI, context text, and bounds.

Interpretation:

- eta75 now has dirty readerdev emulator evidence that the Phase 5 selection action path is functional from native long press and from the scripted footnote fixture through native Highlight/Copy/Note UI actions.
- This still does not replace physical-phone/manual validation of Android selection handles, and it does not validate real scrolled-edge pull-up.

## 2026-06-19 Working Tree Follow-Up: Font Scaling And Annotation Reopen Guards

Trigger:

- Investigate the reported font-size controller regression where changing font size affected EPUB headings and chapter titles but not normal body text, causing larger titles to push unchanged ebook text down.
- Close the related annotation UX gap where Highlight/Note actions could dispatch engine commands without a visible Foliate highlight draw or a reopenable native note popup.

Changes under validation:

- Body text scaling is now anchored through `--reader-content-font-size` on `html`, `body { font-size: 1rem !important; }`, and text block rules using `1em`, so the controller scales normal EPUB text and headings from the same base instead of only changing title-like elements.
- Adaptive tablet page width no longer uses `columnThreshold` as a hard full-page inline cap. The threshold remains a column-splitting decision, not a reason to create an artificially narrow Tab S9 Ultra reading column.
- Annotation drawing now calls Foliate `Overlayer.highlight` before posting `annotationDrawn`, so Highlight and Note produce a visible mark in the rendered document.
- Annotation clicks now resolve the saved Navic annotation by CFI and surface selected text, note body, and color in the native annotation popup.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.annotationClicksResolveSavedNoteBodyFromControllerStore" --tests "paige.navic.reader.FoliateAnxParityTest.drawAnnotationRuntimePaintsFoliateOverlayBeforeReportingBridgeEvent" --tests "paige.navic.reader.FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract" --tests "paige.navic.reader.FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.*"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
```

Result:

- PASS: focused font-size, annotation draw, annotation reopen, and adaptive composition guards.
- PASS: broad reader host suite `paige.navic.reader.*`.
- PASS: full Android host suite `:composeApp:testAndroidHost`.
- PASS: JavaScript syntax checks for the changed reader runtime and reader harness files.
- PASS: whitespace check.

Remaining:

- This is not a packaged release candidate yet. Physical device validation is still required for the font-size control on the Tab S9 Ultra and for manual reopen of saved highlights/notes.

## 2026-06-19 Working Tree Follow-Up: Publisher Span-Wrapped Font Scaling

Trigger:

- User reported the font-size controller still behaved incorrectly: EPUB headings/title-like text changed size, but normal ebook text did not, so larger headings only pushed unchanged body text down.

Root cause:

- The earlier fix scaled inherited paragraph text through `--reader-content-font-size`, but publisher CSS could still fix the visible body glyphs on descendant `span` elements inside paragraphs. In that case the paragraph/root changed, while the displayed span stayed at its publisher font size.

Changes under validation:

- `readerContentCss` now normalizes `span` and `font` descendants inside paragraph-like body text containers to `font-size: 1em !important`, so the visible body glyphs inherit the reader font-size scale.
- `font-css-smoke` now includes a publisher-style span-wrapped paragraph regression: `10px -> 10px` was the failing red case before the fix, and now the same text scales with the controller.
- The Anx parity guard now requires the span/font descendant rule, not only root/body font-size wiring.
- `adb-webview-eval.mjs` now has a `font-size` probe for active WebView computed-style inspection.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\adb-webview-eval.mjs
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size
```

Result:

- FAIL before fix: `font-css-smoke` reported `Expected font-size control to scale publisher span-wrapped body text; observed 10px -> 10px`.
- PASS after fix: `font-css-smoke`.
- PASS: `FoliateAnxParityTest`.
- PASS: JavaScript syntax checks for the changed helper and harness files.
- PASS: emulator WebView probe on the updated readerdev showed inherited paragraph text scaling from `16px` at 100% to `22.4px` at 140%.
- PASS: the same probe sampled existing loaded EPUB paragraphs (`A Memory of Light`) and each sampled paragraph scaled from `16px` to `22.4px`, proving the installed runtime now changes real ebook body text, not only synthetic probes/headings.
- Remaining: physical/loaded-section validation is still required for the exact Tab S9 Ultra page/book that exposed the original symptom.

2026-06-19 follow-up check:

- User re-reported the same symptom on the tablet: the font-size controller affects headings/title-like text but not normal ebook body text.
- Re-ran `node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke`; result: PASS.
- Re-ran focused `FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract`; result: PASS.
- ADB-visible installed builds on the emulator are `darkaxt.navic` `v1.0.11-eta74` and `darkaxt.navic.readerdev` `v1.0.11-eta75`; both predate local commits `e8527e22` and `889462cb`.
- Diagnosis: the source-side fix exists on local `master` but is not present in the installed/released builds. Do not rework the CSS unless the same symptom reproduces after installing a build that includes `889462cb`.

## 2026-06-19 Working Tree Follow-Up: Komikku Rotated Vertical Progress Rail

Trigger:

- Replace the remaining non-faithful Navic vertical progress rail implementation. The surrounding reader chrome had Komikku structure, but the rail itself still used a custom Canvas, manual y-offset mapping, and pointer gesture detectors instead of Komikku's rotated shared slider.

Changes under validation:

- `ReaderChapterNavigator.kt` now implements the vertical page rail through `KomikkuChapterProgressSlider` with Komikku's `graphicsLayer { rotationZ = 90f; transformOrigin = TransformOrigin(0f, 0f) }` and swapped-constraint `layout` pattern.
- The custom `komikkuChapterRailPageForOffset` mapper, Canvas drawing, and rail-local tap/drag detectors were removed.
- The Navic-specific rule to hide the rail on cover/very short sections remains guarded by `readerShouldShowChapterProgressSlider(totalPages) >= 3`.
- Host source guards now reject the custom Canvas rail and require the rotated slider structure against the Komikku reference.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeCommonChromeTest
.\gradlew.bat --no-daemon :composeApp:testAndroidHost
.\scripts\install-reader-dev.ps1 -EnvFile "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env" -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -ArtifactRoot "captures\reader-komikku-matrix\vertical-rail-komikku-slider-20260619-043700" -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

Result:

- RED before fix: `ReaderRuntimeCommonChromeTest` failed on the old custom rail guard after tests were changed to require the Komikku rotated slider.
- PASS: focused `ReaderRuntimeCommonChromeTest`.
- PASS: full Android host suite `:composeApp:testAndroidHost`.
- PASS: dirty `readerdev` install and direct EPUB launch on `emulator-5554`; installed package reported `versionName=v1.0.11-eta75`, `versionCode=408`, `lastUpdateTime=2026-06-19 04:35:50`.
- PASS: direct launch selected and opened `A Memory of Light (epub)` and emitted `Reader publication ready`.
- PASS: Komikku matrix `captures\reader-komikku-matrix\vertical-rail-komikku-slider-20260619-043700` had no failures across baseline reader, native cover, cover tap, cover drag, center tap, long press, edge taps, drags, and texture walks.
- Remaining: this is dirty-emulator evidence only. Physical/release validation is still required before claiming the rail behavior is release-ready on the phone/tablet builds.

Follow-up corruption found after matrix:

- Manual/ADB inspection after repeated previous navigation captured a corrupted reader state at `captures\reader-dev\current-corruption-check-20260619-1.png`.
- Native tap logs still reported `KomikkuReaderNativeFrameHost: Reader native tap action=MENU`, so the top-level native tap overlay was not the broken layer.
- WebView DevTools showed `foliate-view` and the paginator at full viewport size, but the active Foliate content was `about:srcdoc` with `bodyTextLength=0` and `bodyRect=0x0`.
- Logcat showed the engine reached `cover-document:suppressed index=0 OEBPS/Text/cubierta.xhtml` after a previous turn from frontmatter; the runtime then skipped the cover relocation, leaving Foliate on an empty suppressed cover document instead of returning the controller to the native shell cover.
- Root cause: `readerShouldReturnToNativeShellCover` required global `pageIndex <= 1`. Real frontmatter can have several measured global pages before the first readable chapter (`page=10/1534`, `progress=0.0007927`, `href=OEBPS/Text/sinopsis.xhtml`), so the controller forwarded a previous page command to Foliate instead of reclaiming the native cover.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryAllowsFrontmatterWhenGlobalPageIndexIsAlreadyPastOne --tests paige.navic.reader.ReaderControllerTest.previousFromFrontmatterStartReturnsToNativeCoverEvenWhenGlobalPageIndexIsPastOne
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryAllowsFrontmatterWhenGlobalPageIndexIsAlreadyPastOne --tests paige.navic.reader.ReaderControllerTest.previousFromFrontmatterStartReturnsToNativeCoverEvenWhenGlobalPageIndexIsPastOne --tests paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryDoesNotTreatLaterChapterFirstPageAsBookStart --tests paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryDoesNotTrustLocalPageZeroWhenHrefIsLaterChapter --tests paige.navic.reader.ReaderControllerTest.previousFromFirstReadablePageReturnsToNativeCoverInsteadOfSuppressedWebViewCover
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderShellCoverTapsAndPreviousDoNotFallThroughToEpubCover
.\gradlew.bat --no-daemon :composeApp:testAndroidHost
.\scripts\install-reader-dev.ps1 -EnvFile "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env" -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
adb -s emulator-5554 shell screencap -p /sdcard/navic-reader-dev-direct.png
adb -s emulator-5554 pull /sdcard/navic-reader-dev-direct.png captures\reader-dev\reader-dev-direct-after-cover-boundary-fix-20260619.png
adb -s emulator-5554 shell input tap 1000 1200
adb -s emulator-5554 shell input tap 80 1200
adb -s emulator-5554 shell screencap -p /sdcard/navic-reader-cover-boundary-after-prev.png
adb -s emulator-5554 pull /sdcard/navic-reader-cover-boundary-after-prev.png captures\reader-dev\reader-dev-cover-boundary-after-prev-20260619.png
```

Result:

- RED before fix: both new frontmatter cover-boundary tests failed because the guard rejected `pageIndex=10` even though global progress was near zero.
- PASS after fix: the same frontmatter tests passed, and the neighboring later-chapter guards still passed.
- PASS: the Android host source guard was updated from the old page-index-only assertion to the new controller-owned boundary contract.
- PASS: full Android host suite `:composeApp:testAndroidHost`.
- PASS: dirty readerdev build/install completed, foregrounded `darkaxt.navic.readerdev`, and emitted `publicationReady`. The helper script then failed only while pulling its automatic screenshot artifact.
- PASS: direct screenshot `captures\reader-dev\reader-dev-direct-after-cover-boundary-fix-20260619.png` showed the native shell cover, not the suppressed blank WebView cover.
- PASS: narrow dirty-emulator gesture probe sent native RIGHT then LEFT taps; logs showed `Reader native tap action=RIGHT` and `Reader native tap action=LEFT`, and `captures\reader-dev\reader-dev-cover-boundary-after-prev-20260619.png` showed the native shell cover after previous.
- Remaining: this is still dirty-emulator evidence. Physical/release validation is required before claiming the fix is present on the phone/tablet builds.

## 2026-06-19 Working Tree Follow-Up: Font Size Control For Converted EPUB Body Blocks

Trigger:

- User reported that the ebook font-size control changes chapter titles/title-like text but not the main ebook body text, effectively pushing content downward while normal text remains too small.

Root-cause evidence:

- Existing dirty-emulator DevTools probe on `darkaxt.navic.readerdev` showed the bridge path itself is valid for normal paragraph content: real loaded EPUB paragraphs scaled from `16px` at 100% to `22.4px` at 140%.
- The Hobbit-style `served-input.epub` is a PDF-converted XHTML shape with direct body text, `<br/>` separators, and converted block structures rather than clean semantic paragraphs.
- RED harness case added to `font-css-smoke`: publisher body text wrapped in a styled `<div>` with `<br/>` separators stayed fixed at `10px -> 10px`. This reproduced the selector gap: `p span` was normalized, but line-break body blocks were not.

Change under validation:

- `readerTypographyCss` now treats `div:has(> br)` text blocks as ebook body-flow content for font-size/font-weight/text-indent scaling, while excluding image/svg/canvas wrapper divs so illustration layout is not pulled into body text styling.
- Nested `span`/`font` elements inside those line-break body blocks are normalized to `1em`, matching the existing paragraph descendant behavior.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract
git diff --check
```

Result:

- RED before fix: `font-css-smoke` failed with `Expected font-size control to scale publisher block-wrapped body text; observed 10px -> 10px`.
- PASS after fix: `font-css-smoke`.
- PASS: JavaScript syntax check for `navic-reader-helpers.js`.
- PASS: focused `FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract`.
- PASS: `git diff --check`.
- Remaining: physical/release validation is still required on the Tab S9 Ultra/Hobbit page after installing a build that includes this working-tree fix.

## 2026-06-19 Working Tree Follow-Up: Anx Top/Bottom Margin Consumption In Bundled Foliate

Trigger:

- User reported that the Tab S9 Ultra EPUB page composition had too much unused width and too little natural vertical breathing room, then asked whether real folio margins are usually equal.

Root-cause evidence:

- Real folio/page margins are normally asymmetric, and Anx models that explicitly with separate `topMargin` and `bottomMargin` style fields.
- Navic already serialized those Anx fields through `ReaderSettings` and applied them to Foliate with `renderer.setAttribute('top-margin', ...)` and `renderer.setAttribute('bottom-margin', ...)`.
- The bundled Foliate paginator ignored both attributes. It observed and consumed only the uniform `margin` attribute, so active page composition collapsed Anx's top/bottom margin model into one default `--_margin`.

Change under validation:

- Added a host parity guard requiring the bundled paginator to consume Anx `topMargin` and `bottomMargin` separately.
- Updated `vendor/foliate-js/paginator.js` to define `--_top-margin` and `--_bottom-margin`, observe `top-margin` and `bottom-margin`, and use those values for the page grid's top/bottom rows plus header/footer heights.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract
node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\paginator.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
.\gradlew.bat --no-daemon :composeApp:testAndroidHost
.\scripts\install-reader-dev.ps1 -EnvFile "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env" -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
```

Result:

- RED before fix: `FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract` failed because the bundled paginator did not contain `top-margin`, `bottom-margin`, `--_top-margin`, or `--_bottom-margin`.
- PASS after fix: focused Anx style-dimension parity test.
- PASS: JavaScript syntax checks for `paginator.js` and `navic-reader-helpers.js`.
- PASS: `font-css-smoke`.
- PASS: full Android host suite `:composeApp:testAndroidHost`.
- PASS: dirty `readerdev` build/install/launch on `emulator-5554`; the script reached foreground confirmation and `publicationReady`, then pulled `captures\reader-dev\reader-dev-20260619-053426.png`.
- Remaining: this is dirty-emulator/source evidence. Physical/release validation is still required on the Tab S9 Ultra page before claiming the margin composition is solved on the tablet.

## 2026-06-19 Working Tree Follow-Up: Font-Size Probe Cleanup

Trigger:

- User asked to re-check the font-size controller because it appeared to resize chapter/title text but not the main ebook body text.

Findings:

- Current source already includes the app-side font-size fixes for publisher span-wrapped paragraphs and converted EPUB `div:has(> br)` text blocks.
- `font-css-smoke` still passes against those known body-text markup shapes.
- The dirty emulator was on a native/frontmatter page, so the ADB font-size probe had no real EPUB body-text samples to measure.
- While probing, the DevTools helper itself inserted a visible `Navic font-size probe paragraph text.` node and did not remove it. That made the diagnostic unsafe to use repeatedly on a live reader page.

Change under validation:

- `adb-webview-eval.mjs` now removes the synthetic font-size probe node in a `finally` block and restores the original `fontSizePercent` through the same cleanup path.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperFontSizeProbeCleansSyntheticParagraph
node --check tools\reader-harness\src\adb-webview-eval.mjs
```

Result:

- PASS: source harness `font-css-smoke`.
- PASS: dirty-emulator synthetic font-size probe scaled root/body/probe paragraph text from `16px` at 100% to `22.4px` at 140%.
- RED before tool fix: `ReaderRuntimeAssetsTest.adbWebViewEvalHelperFontSizeProbeCleansSyntheticParagraph` failed because the helper did not contain a `probe.remove()` cleanup path.
- PASS after tool fix: the same focused host guard.
- PASS: JavaScript syntax check for `adb-webview-eval.mjs`.
- Remaining: a non-mutating ADB font-size check still needs to be run on a real loaded body-text page, not on native cover/frontmatter. If the same symptom reproduces after a build containing `de0ff62e` and `889462cb`, the next suspected missing markup shape is direct body-level styled spans outside paragraph/block containers.

## 2026-06-19 Working Tree Follow-Up: Frontmatter Previous Does Not Steal Native Cover

Trigger:

- The Komikku matrix previously failed at `edge-tap-previous` after walking through early frontmatter: a previous action from a later frontmatter/title page jumped to the native shell cover, so no previous-direction texture samples were captured.

Root-cause evidence:

- `readerShouldReturnToNativeShellCover` treated every locator with global progress `<= 0.02` as a native-cover boundary.
- Real EPUB frontmatter can span several measured pages before the first readable chapter. In the reproduced path, the first eligible post-cover locator was `pageIndex=10`, but a later title/frontmatter locator was still low-progress at `pageIndex=13`.
- The controller used the broad low-progress eligibility as the final Previous decision, so later frontmatter pages could also reclaim the native cover.

Change under validation:

- `ReaderControllerState` now records `nativeShellCoverReturnLocatorKey` from the first eligible EPUB-side locator after the native cover is dismissed.
- Previous returns to the native cover only when the current locator key matches that recorded first eligible locator; later low-progress frontmatter pages now dispatch `ReaderEngineCommand.TurnPage(Previous)` normally.
- The key resets on `open(...)` and `applySettings(...)`, because reader settings can change pagination coordinates.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderControllerTest.previousFromLaterFrontmatterPageUsesEngineInsteadOfJumpingBackToNativeCover
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests paige.navic.reader.ReaderControllerTest.previousFromLaterFrontmatterPageUsesEngineInsteadOfJumpingBackToNativeCover --tests paige.navic.reader.ReaderControllerTest.previousFromFrontmatterStartReturnsToNativeCoverEvenWhenGlobalPageIndexIsPastOne --tests paige.navic.reader.ReaderControllerTest.previousFromFirstReadablePageReturnsToNativeCoverInsteadOfSuppressedWebViewCover --tests paige.navic.reader.ReaderControllerTest.applySettingsKeepsControllerAsOwnerAndForwardsNormalizedSettingsToEngine
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost
node --check tools\reader-harness\src\adb-webview-eval.mjs
git diff --check
.\scripts\install-reader-dev.ps1 -EnvFile "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env" -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -IncludeCoverChecks -ContinueOnFailure -ArtifactRoot captures\reader-komikku-matrix\frontmatter-cover-boundary-key-20260619-0621
```

Result:

- RED before fix: `previousFromLaterFrontmatterPageUsesEngineInsteadOfJumpingBackToNativeCover` failed because the controller showed the native cover instead of dispatching `TurnPage(Previous)`.
- PASS after fix: the same focused regression, the first-frontmatter/native-cover neighbors, and the settings-reset guard.
- PASS: full Android host suite `:composeApp:testAndroidHost`.
- PASS: JavaScript syntax check for the modified DevTools helper and `git diff --check`.
- PARTIAL PASS: dirty readerdev build/install/launch reached foreground confirmation and `publicationReady`; the helper failed only while pulling its automatic screenshot.
- PASS: Komikku matrix `captures\reader-komikku-matrix\frontmatter-cover-boundary-key-20260619-0621` completed through `edge-tap-previous`, `drag-previous`, and `texture-previous-walk`.
- PASS: `edge-tap-previous/reader-diagnostics-summary.txt` captured `textureDirectionSamples=2` and `wrongTextureDirection=False`, proving the earlier no-sample jump-to-cover failure did not recur in this dirty-emulator run.
- Remaining: this is still dirty-emulator evidence. Physical/release validation is required before claiming the behavior is fixed on the phone/tablet release build.

## 2026-06-19 Working Tree Follow-Up: Direct Inline EPUB Body Font Scaling

Trigger:

- User reported that the reader font-size controller still behaved incorrectly: chapter/title text changed size, but the main ebook body text did not, which only pushed the body content downward.

Root-cause evidence:

- Existing source already covered publisher-styled body text when it was inside `<p><span>...</span></p>` and converted line-break blocks like `<div>...<br/>...</div>`.
- The missing EPUB shape was direct publisher-styled inline body text, e.g. `<body><span class="publisher-body-text">...</span></body>`.
- The focused browser harness reproduced the exact controller failure: the direct body text stayed `10px -> 10px` when `fontSizePercent` changed from `100` to `140`.

Change under validation:

- `readerTypographyCss` now normalizes direct body-level inline text containers with image/svg/canvas guards:
  - `body > span:not(:has(img)):not(:has(svg)):not(:has(canvas))`
  - `body > a:any-link:not(:has(img)):not(:has(svg)):not(:has(canvas))`
- The Anx style parity host guard now requires the direct body-level selector, so future CSS refactors cannot silently return to title-only scaling.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract
git diff --check
```

Result:

- RED before fix: `font-css-smoke` failed with `Expected font-size control to scale publisher direct body text; observed 10px -> 10px`.
- PASS after fix: `font-css-smoke`.
- PASS: JavaScript syntax checks for `run-reader-harness.mjs` and `navic-reader-helpers.js`.
- PASS: focused host parity guard `phase6StyleDimensionsMatchAnxBookStyleContract`.
- PASS: `git diff --check`.
- Note: an initial `--rerun-tasks` host-test attempt was killed by the command runner before producing a test result; the rerun without forced task invalidation completed successfully.
- Remaining: this is source/harness evidence. The fix still needs dirty-emulator or physical release validation on the real Tab S9 Ultra EPUB page where the body text stayed fixed.

## 2026-06-19 Working Tree Follow-Up: Portrait Tablet Folio Composition

Trigger:

- User reported that on the Tab S9 Ultra the EPUB page had too much unused horizontal space and cramped/unnatural vertical composition.
- User also questioned whether a folio page should have more balanced vertical and horizontal margins instead of a narrow text strip.

Root-cause evidence:

- `readerAdaptiveFoliatePageBox` preserved Anx `maxColumnCount=0` directly into the Foliate renderer.
- Bundled Foliate interprets `maxColumnCount=0` as automatic split from `columnThreshold`; with the Anx default `columnThreshold=720`, a portrait tablet/fold viewport wider than 720 can become a two-column spread calculation.
- The focused harness already encoded the expected Komikku shell behavior and failed before the fix:
  `Expected portrait single-page composition until same-section spread is explicit, got {"maxInlineSize":"1133px","maxBlockSize":"1846px","maxColumnCount":"0","columnThreshold":"720px","viewportWidth":1232,"viewportHeight":1974,"flowMode":"paged"}`.

Change under validation:

- `maxColumnCount=0` is still stored and persisted as the Anx automatic setting.
- The Komikku shell now resolves that automatic setting before mounting Foliate:
  - portrait phone/fold/tablet surfaces resolve to one folio page;
  - landscape/wide spread surfaces resolve to two columns when the viewport passes `columnThreshold`;
  - explicit user settings `maxColumnCount=1` and `maxColumnCount=2` are preserved.
- The active design spec now records this boundary so future work does not reintroduce portrait auto-spread by treating raw Anx defaults as UI ownership.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode adaptive-page-box-logic
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest.androidReaderAppliesAdaptiveViewportPageBoxToVisibleAndProfilingViews
git diff --check
```

Result:

- RED before fix: `adaptive-page-box-logic` failed because portrait tablet auto mode returned `maxColumnCount="0"` instead of resolving to the single-page Komikku shell behavior.
- PASS after fix: `adaptive-page-box-logic`.
- PASS: JavaScript syntax checks for `navic-reader-helpers.js` and `run-reader-harness.mjs`.
- PASS: focused host parity/navigation guards.
- PASS: `git diff --check`.
- Remaining: this is source/harness evidence. Dirty emulator and physical Tab S9 Ultra validation are still required before claiming the visual tablet margins are release-ready.

## 2026-06-19 Working Tree Follow-Up: Note Annotation Visual Cue

Trigger:

- User asked whether saving a note adds any visual cue and how the note can be found/opened later.

Root-cause evidence:

- `ReaderController.saveSelectionNote(...)` already stores note-bearing annotations and forwards them through `ReaderEngineCommand.ApplyAnnotations`.
- `ReaderController.onAnnotationClicked(...)` already resolves the saved note body back into `ReaderAnnotationPopupState`, so tapping an emitted annotation can reopen the note dialog.
- The missing behavior was in the Foliate draw path: `onAnnotationDrawn(...)` painted every annotation with the same `Overlayer.highlight`, which made note annotations indistinguishable from plain highlights.

Change under validation:

- `navic-reader.js` now draws note-bearing annotations through `readerDrawNoteAnnotation(...)`.
- Plain annotations still use Foliate `Overlayer.highlight`.
- Note annotations draw the same highlight plus a Foliate `Overlayer.squiggly` marker, tagged with `data-navic-note-annotation="true"`.
- The Anx/Foliate parity guard now requires the draw callback path to preserve plain highlight behavior and visibly differentiate annotations whose payload contains `annotation.note`.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.drawAnnotationRuntimePaintsFoliateOverlayBeforeReportingBridgeEvent
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Result:

- RED before fix: the strengthened parity guard failed because Navic only painted annotation ranges as plain highlights and had no note-specific visual cue.
- PASS after fix: focused host parity guard `drawAnnotationRuntimePaintsFoliateOverlayBeforeReportingBridgeEvent`.
- PASS: JavaScript syntax check for `navic-reader.js`.
- Remaining: this is source/host evidence. Dirty emulator or physical release validation still needs to confirm that a real saved note shows the cue, tapping it reopens `KomikkuReaderAnnotationDialog`, and plain highlights remain visually distinct.

## 2026-06-19 Working Tree Follow-Up: Controller-Owned Footnote Popup Route

Trigger:

- The Anx parity registry treated `onFootnoteClose` as implemented because the close event reached controller state, but there was no native route for opening or displaying footnote content.
- This repeated the "types-only/event-only" failure mode: Navic could observe that a footnote closed without providing a reader-owned footnote experience.

Root-cause evidence:

- Anx `book.js` uses `FootnoteHandler`, extracts the referenced footnote fragment into a footnote view, shows a footnote dialog, and emits `onFootnoteClose` when dismissed.
- Navic already carried footnote metadata in `SelectionChanged`, and already decoded `FootnoteClose`, but `ReaderControllerState` had no `footnotePopup` state and `ReaderRoot` had no `KomikkuReaderFootnoteDialog`.
- The first RED run failed at compile time on missing `ReaderBridgeEvent.FootnoteOpen`, `ReaderEngineEvent.FootnoteOpened`, `ReaderFootnotePopupState`, and footnote dialog symbols.

Change under validation:

- Added a typed `footnoteOpen` bridge event carrying `href`, extracted text, note type, and hidden state.
- Mapped `ReaderBridgeEvent.FootnoteOpen` through `FoliateEpubEngineAdapter` to `ReaderEngineEvent.FootnoteOpened`.
- Added `ReaderFootnotePopupState`, controller-owned open/close behavior, `ReaderCoordinator.dismissFootnotePopup()`, and a native `KomikkuReaderFootnoteDialog`.
- Added same-document footnote extraction in `navic-reader-content-interactions.js`, posting `readerContentTapHandled(action=footnote)` plus `footnoteOpen` before suppressing normal link navigation.
- Strengthened `FoliateAnxParityTest` so `onFootnoteClose` is not considered behavior-complete without the native open/close popup route and ADB-visible `footnoteOpen(...)` label.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest --tests paige.navic.reader.ReaderControllerTest.footnoteOpenShowsControllerOwnedFootnotePopupAndCloseClearsIt --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderFootnotesAreKomikkuOverlayAndControllerRouted --tests paige.navic.reader.FoliateAnxParityTest.everyAnxHandlerIsDocumentedInKnownGaps --tests paige.navic.reader.FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
```

Result:

- RED before fix: focused host compile failed on unresolved `FootnoteOpen`, `FootnoteOpened`, `ReaderFootnotePopupState`, and related popup-route symbols.
- PASS after fix: focused bridge/adapter/controller/common-UI/Anx-parity host suite.
- PASS: JavaScript syntax check for `navic-reader-content-interactions.js`.
- Remaining: this is source/host evidence. Dirty emulator and physical release validation still need to confirm a real EPUB footnote reference opens the native popup, the close action emits/clears the route, and cross-section footnotes either render or are explicitly tracked as a follow-up beyond the same-document extractor.

## 2026-06-19 Emulator Recheck: Font Size Controller Body Text Scaling

Trigger:

- User reported that the font size controller changes titles/headings but not ebook body text, which pushes the page down instead of resizing the actual reading text.

Root-cause evidence:

- The branch already contains commit `66395740 Fix direct EPUB body font scaling`.
- The reproduced failure shape was publisher EPUB markup with body text as direct `body > span` / `body > a` content instead of normal paragraph blocks. Before the fix, those direct body text nodes stayed at `10px -> 10px` while headings changed.
- Current runtime CSS in `readerTypographyCss(...)` now normalizes direct body-level inline text containers while excluding image/svg/canvas wrappers.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size
```

Result:

- PASS: `font-css-smoke` confirmed direct body text, span-wrapped paragraph text, block-wrapped text, and headings scale under the same font-size controller path.
- PASS on attached emulator WebView style path: the injected probe paragraph scaled from `16px` at `fontSizePercent=100` to `22.4px` at `fontSizePercent=140`; root/body/probe deltas were all `+6.4px`.
- Caveat: the emulator probe found no existing visible EPUB text elements on the current page, so it proves the installed WebView runtime style path is active but does not prove that the user's current physical-device EPUB page has the fixed asset version loaded.
- Remaining: confirm on a clean release APK/physical device containing `66395740` by changing font size on a direct-body-text page and checking that the main text reflows, not only the chapter title.

## 2026-06-19 Working Tree Follow-Up: Cross-Section Footnote Resolution

Trigger:

- The same-document footnote popup route was controller-owned, but it still diverged from Anx `FootnoteHandler`: Anx resolves footnote targets through `book.resolveHref(href)` and can render a referenced fragment from another spine section.
- Navic's first implementation only called `getElementById(...)` in the current loaded document, so it could open nearby/same-document notes while still missing Anx cross-section semantics.

Root-cause evidence:

- Anx reference `tmp/references/anx-reader/assets/foliate-js/src/footnotes.js` proves the expected route: `book.resolveHref(href)`, temporary `foliate-view`, `view.open(book)`, and `view.goTo(index)`.
- Foliate EPUB sections expose `createDocument()`, so Navic can load the resolved target section for extraction without moving the visible reader or giving the popup back to WebView ownership.

Change under validation:

- `navic-reader-content-interactions.js` now has `readerResolvedFootnoteOpenPayload(...)`.
- The helper first preserves the same-document fast path, then resolves the href through `book.resolveHref`, loads the target section via `section.createDocument()` when available, falls back to `section.load()` only if needed, accepts Element or Range anchor results, and posts the same native `footnoteOpen` bridge event.
- `FoliateAnxParityTest` now reads Anx `footnotes.js` directly and fails if Navic claims `onFootnoteClose` behavior without a cross-section target-resolution route.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.footnotePopupRouteResolvesCrossSectionTargetsLikeAnxFootnoteHandler
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest --tests paige.navic.reader.ReaderControllerTest.footnoteOpenShowsControllerOwnedFootnotePopupAndCloseClearsIt --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderFootnotesAreKomikkuOverlayAndControllerRouted --tests paige.navic.reader.FoliateAnxParityTest.footnotePopupRouteResolvesCrossSectionTargetsLikeAnxFootnoteHandler --tests paige.navic.reader.FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest
```

Result:

- RED before fix: `footnotePopupRouteResolvesCrossSectionTargetsLikeAnxFootnoteHandler` failed because Navic had no async resolved-footnote helper and no target-section load path.
- PASS after fix: targeted cross-section footnote parity guard.
- PASS: JavaScript syntax check for `navic-reader-content-interactions.js`.
- PASS: focused bridge/adapter/controller/common-UI/Anx-parity host suite.
- PASS: full `FoliateAnxParityTest`.
- Remaining: this is source/host evidence. A real EPUB with cross-section footnotes still needs emulator or physical-device validation to confirm the referenced section content appears in `KomikkuReaderFootnoteDialog`.

## 2026-06-19 Working Tree Follow-Up: Single Bottom Settings Route

Trigger:

- The active spec still listed duplicate bottom-toolbar settings entry points as a Komikku-shell Priority 1 issue.
- The visible bottom toolbar had already moved to distinct contents/search/settings actions, but the controller still exposed `ReaderControllerDialog.ReadingMode` and `ReaderControllerDialog.Settings`, both rendering `KomikkuReaderSettingsDialog`.

Root-cause evidence:

- `ReaderController.kt` had `ReaderControllerDialog.ReadingMode` and `ReaderControllerDialog.Settings`.
- `ReaderRoot.kt` rendered both dialog values as the same settings dialog, only changing `initialTab`.
- `ReaderControllerTest` and `ReaderCoordinatorTest` still encoded the duplicate dialog route as expected behavior.

Change under validation:

- Removed `ReaderControllerDialog.ReadingMode`.
- Removed `openReadingModeDialog()` from `ReaderController` and `ReaderCoordinator`.
- Removed the duplicate `ReaderControllerDialog.ReadingMode -> KomikkuReaderSettingsDialog(...)` branch from `ReaderRoot`.
- Strengthened controller/coordinator/backbone tests so a second settings route is treated as a regression.
- Kept Komikku's bottom button model value `ReadingMode("rm")` as a reference enum value, but it remains non-rendered by `KomikkuReaderBottomBar`.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderControllerTest.readerSettingsDialogHasSingleControllerRoute --tests paige.navic.reader.ReaderControllerTest.settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog --tests paige.navic.reader.ReaderCoordinatorTest.bottomBarDialogsRouteThroughControllerWithoutEngineCommands --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomToolbarDoesNotExposeDuplicateSettingsDialogs
```

Result:

- RED before fix: `ReaderControllerTest.readerSettingsDialogHasSingleControllerRoute` failed while `ReaderControllerDialog.ReadingMode` existed, and `ReaderKomikkuBackboneResetTest.activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton` failed while `ReaderRoot` still rendered the duplicate settings branch.
- PASS after fix: targeted controller/coordinator/backbone host suite.
- PASS: existing bottom-toolbar guard confirming contents/search/settings are distinct and `ReadingMode` is not rendered as a duplicate bottom action.
- Remaining: source/host evidence only. Physical/emulator visual validation can confirm the bottom bar still shows the intended three actions and opens only one settings surface.

## 2026-06-19 Follow-Up: Font Size Settings Reflow Ordering

Trigger:

- User reported again that the font-size controller affects chapter/title-like text, but normal EPUB body text remains effectively unchanged. The visual effect is that larger headings push the body content down instead of the reading text scaling with the heading.

Root-cause evidence:

- Existing selector fixes already covered direct body spans, span-wrapped paragraphs, and converted `div:has(> br)` body blocks.
- The release package WebView did not expose a DevTools socket on the emulator, so the live release page could not be measured through `adb-webview-eval.mjs`.
- Readerdev WebView probing did confirm the synthetic font-size path still scales root/body/probe text from `16px` to `22.4px`, but it had no loaded real EPUB body elements, so that was not enough evidence for the user's physical-page symptom.
- Source inspection found the remaining ordering fault in `applySettings`: Navic requested `applyReaderViewportLayout('settings')`, which explicitly calls Foliate `renderer.render()`, before installing `readerContentCss(settings)` into the renderer and loaded EPUB documents. That lets headings repaint from later CSS while the body/page geometry can remain based on the old content CSS.

Change under validation:

- `applySettings` now calls `renderer.setStyles(readerContentCss(settings))` and `applyThemeToLoadedContent(settings)` before `applyReaderViewportLayout('settings')`.
- Added `ReaderRuntimeSettingsBridgeTest.androidReaderAppliesFontCssBeforeSettingsReflow` so this ordering cannot regress.
- Expanded `font-css-smoke` with a fixed-size publisher paragraph sample; this already passed before the ordering fix, confirming this slice addresses reflow ordering rather than another missing selector.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderAppliesFontCssBeforeSettingsReflow
node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
```

Result:

- RED before fix: `ReaderRuntimeSettingsBridgeTest.androidReaderAppliesFontCssBeforeSettingsReflow` failed because `applyReaderViewportLayout('settings')` came before renderer/document CSS injection.
- PASS after fix: the same focused Android host guard passed.
- PASS: `node --check` for `navic-reader-appearance.js`.
- PASS: `node --check` for `run-reader-harness.mjs`.
- PASS: `font-css-smoke`, including direct body text, span-wrapped body text, converted block text, fixed-size paragraph text, and heading scaling.
- Remaining: physical release validation is still required on the Tab S9 Ultra page where the body text appeared fixed. If it still reproduces after this ordering fix, the next evidence to collect is a real computed-style sample from the physical release WebView with debugging enabled.

## 2026-06-19 Follow-Up: Anx Top/Bottom Margin Observation

Trigger:

- User challenged the tablet folio margins after Tab S9 Ultra screenshots showed excessive width reserve and cramped-looking vertical composition.
- Source inspection found that Navic was setting Foliate `top-margin` and `bottom-margin` attributes from Anx `BookStyle`, and the bundled paginator had `attributeChangedCallback` cases for those attributes, but `Paginator.observedAttributes` did not include them.

Root-cause evidence:

- A custom element only receives `attributeChangedCallback` for names listed in `static observedAttributes`.
- Before this slice, `Paginator.observedAttributes` listed `flow`, `gap`, `margin`, `max-inline-size`, `max-block-size`, `max-column-count`, and `column-threshold`, but not `top-margin` or `bottom-margin`.
- That meant the Komikku controller/Anx settings bridge could call `renderer.setAttribute('top-margin', ...)` and `renderer.setAttribute('bottom-margin', ...)`, while Foliate silently kept its default vertical margin CSS variables.

Change under validation:

- Added `top-margin` and `bottom-margin` to the bundled Foliate paginator's observed attributes.
- Added `FoliateAnxParityTest.foliatePaginatorObservesAnxVerticalMarginAttributes` so Anx vertical margin parity requires both setting and observing the attributes.
- Tightened `FoliateAnxParityTest.everyAnxStyleDimensionIsDocumentedInKnownGaps` to inspect the observed-attributes list, not only the attribute handler cases.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.foliatePaginatorObservesAnxVerticalMarginAttributes
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.foliatePaginatorObservesAnxVerticalMarginAttributes --tests paige.navic.reader.FoliateAnxParityTest.everyAnxStyleDimensionIsDocumentedInKnownGaps
```

Result:

- RED before fix: `FoliateAnxParityTest.foliatePaginatorObservesAnxVerticalMarginAttributes` failed because `top-margin` was absent from `Paginator.observedAttributes`.
- PASS after fix: the focused vertical-margin guard and broader Anx style-dimension parity guard passed.
- Remaining: physical tablet validation still needs a release/emulator check to judge whether the default values themselves need retuning after the attributes actually apply.

## 2026-06-19 Readerdev Emulator Recheck: eta75 After Font Reflow And Margin Observation

Trigger:

- Recheck the dirty readerdev environment after the font-size reflow-ordering fix and Foliate top/bottom margin observation fix.
- Confirm that the native Komikku shell still handles cover, center tap, edge tap, drag, and texture walk after reinstalling the current `readerdev` build.

Target:

- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Version: `versionName=v1.0.11-eta75`, `versionCode=408`
- Install timestamp observed by package manager: `lastUpdateTime=2026-06-19 08:44:06`
- Reader launch evidence: logcat showed `ready`, `publicationReady`, `paginationProfileStatus(cached)`, and `locationChanged(... reason=pagination-profile-cached ...)`.

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -ArtifactRoot captures\reader-komikku-matrix\20260619-084701-current-readerdev -NoLaunch -IncludeCoverChecks
```

Result:

- PASS: matrix summary reported no failures.
- PASS steps: `baseline-current-reader`, `baseline-native-cover`, `cover-center-tap-toggle`, `cover-drag-next`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk`.
- Artifact root: `captures\reader-komikku-matrix\20260619-084701-current-readerdev`.
- Limitation: this is dirty-emulator evidence for the current readerdev APK. It does not replace physical Tab S9 Ultra validation of the font-size controller on the reported page, and it does not prove phone/release behavior.

## 2026-06-19 Working Tree Follow-Up: Large Tablet Folio Page Box

Trigger:

- User reported that Tab S9 Ultra portrait EPUB margins did not feel like a normal folio: too much unused width and visually unbalanced vertical composition.
- User also asked whether folio defaults normally have broadly comparable vertical and horizontal margins. The target behavior is optical balance, with bottom/top allowed to differ but not a phone/fold hard cap wasting a large tablet viewport.

Root-cause evidence:

- `readerAdaptiveFoliatePageBox({ width: 1848, height: 2960 }, { marginPercent: 0 })` returned `maxInlineSize=1280px` and `maxBlockSize=2200px`.
- Those values came from helper hard caps intended to avoid cramped small surfaces, but on a large portrait tablet they reserve roughly `284px` horizontally per side and `380px` vertically per side before Foliate marginals, which is not a natural folio page box.
- The existing adaptive-page-box harness covered phone/fold/tablet-ish viewports but did not include a Tab S9 Ultra portrait class, so this regression was not guarded.

Change under validation:

- Added a Tab S9 Ultra portrait case to `adaptive-page-box-logic`. It requires large portrait tablet surfaces to avoid the `1280x2200` cap and keep horizontal/vertical reserves optically balanced.
- Removed the `1280px` inline hard cap and `2200px` block hard cap from `readerAdaptiveFoliatePageBox`, preserving the existing natural viewport reserve calculation.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode adaptive-page-box-logic
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract
```

Result:

- RED before fix: `adaptive-page-box-logic` failed with `Expected large tablet portrait EPUB surfaces to avoid phone/fold hard caps... got {"maxInlineSize":"1280px","maxBlockSize":"2200px",...}`.
- PASS after fix: `adaptive-page-box-logic` passed.
- PASS after fix: `node --check` for `navic-reader-helpers.js`.
- PASS after fix: focused `FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract` passed.
- After fix, the same Tab S9 Ultra portrait helper call returns `maxInlineSize=1600px`, `maxBlockSize=2768px`, `maxColumnCount=1`, which leaves a more folio-like reserve before Foliate header/footer marginals.
- Remaining: physical tablet validation is still required because this is helper/host evidence, not a rendered Tab S9 Ultra screenshot.

## 2026-06-19 Readerdev Emulator Follow-Up: Tab S9 Ultra Portrait Rendering Probe

Trigger:

- Turn the large-tablet page-box helper fix into runtime evidence on an Android emulator using the documented Tab S9 Ultra portrait viewport profile.
- Recheck the reported font-size controller behavior against real EPUB paragraphs in the same tablet-shaped readerdev WebView.

Setup:

- Applied viewport profile: `scripts\set-reader-dev-viewport.ps1 -Profile tab-s9-ultra-portrait -DeviceSerial emulator-5554`.
- ADB reported `Override size: 1848x2960`, `Override density: 240`.
- Installed and launched current dirty `readerdev` source with `scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch`.
- Installed package: `darkaxt.navic.readerdev`, `versionName=v1.0.11-eta75`, `versionCode=408`, `lastUpdateTime=2026-06-19 09:02:52`.
- Reader launch evidence: `Reader bridge raw: {"type":"publicationReady"}`.

Probe tooling change:

- Added a read-only `page-box` probe to `tools\reader-harness\src\adb-webview-eval.mjs`.
- Added `page-box` to `scripts\adb-reader-smoke.ps1` `ReaderDevtoolsProbe` values.
- The probe reports Foliate host/renderer rectangles, renderer attributes, loaded content rectangles, and whether the paginator shadow root is closed. It does not dispatch reader commands and does not inject DOM.

Commands:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ArtifactDir captures\reader-tablet-folio\tab-s9-portrait-current
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ArtifactDir captures\reader-tablet-folio\tab-s9-portrait-after-cover-tap
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe page-box -ArtifactDir captures\reader-tablet-folio\tab-s9-portrait-page-box-probe
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe font-size -ArtifactDir captures\reader-tablet-folio\tab-s9-portrait-font-size-probe
```

Result:

- PASS: native cover screenshot captured at `captures\reader-tablet-folio\tab-s9-portrait-current\screen.png`.
- PASS: after-cover EPUB screenshot captured at `captures\reader-tablet-folio\tab-s9-portrait-after-cover-tap\screen.png`.
- PASS: live page-box probe captured `captures\reader-tablet-folio\tab-s9-portrait-page-box-probe\reader-devtools-probe.json`.
- Important runtime detail: Android physical override is `1848x2960`, but WebView CSS viewport is `1232x1974` at `devicePixelRatio=1.5`.
- PASS: live Foliate renderer filled the CSS viewport: `rendererRect=1232x1974`.
- PASS: renderer attributes were `maxInlineSize=1133px`, `maxBlockSize=1846px`, `maxColumnCount=1`, `columnThreshold=720px`, `topMargin=90px`, `bottomMargin=50px`; this confirms the live renderer is not stuck at Foliate's `720x1440` default and is consuming the Anx top/bottom margin attributes.
- PASS: loaded EPUB content document existed with `bodyTextLength=1316`, `documentElementRect=1133x1834`, and visible paragraph body rect width `1061px`.
- PASS: live font-size probe captured `captures\reader-tablet-folio\tab-s9-portrait-font-size-probe\reader-devtools-probe.json`.
- PASS: real EPUB paragraphs scaled from `16px` to `22.4px` when the reader settings changed from `fontSizePercent=100` to `140`; every sampled paragraph had `delta=6.4px`.

Remaining:

- The after-cover page is frontmatter/synopsis, so the blank lower half is content-shortness, not by itself a page-box failure.
- This remains emulator/runtime evidence. A physical Tab S9 Ultra release check is still required for the exact Hobbit page and the user's real device settings.

## 2026-06-19 Readerdev Emulator Follow-Up: Selection Copy And Note Actions

Trigger:

- Continue closing the Priority 0 selection-action validation gap for the Komikku-native selection overlay.
- Verify that Copy and Note are not only controller/UI symbols: they must act through the native app boundary and the Anx/Foliate annotation bridge on the current dirty readerdev build.

Setup:

- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed package: `versionName=v1.0.11-eta75`, `versionCode=408`, `lastUpdateTime=2026-06-19 09:02:52`.
- The smoke runs used the DevTools `selection-payload` probe to create a deterministic selected text range, then used native UI-node taps for the Komikku selection actions.

Commands:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction "tapDesc:Copy,1200" -RequireReaderLog "Reader selection copied length=" -ArtifactDir captures\reader-selection\20260619-selection-copy-hidden-quoted
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta75 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe selection-payload -PostProbeAction "tapDesc:Note,1200|tapText:Annotation,400|text:Smoke note,400|tapText:Save,1400" -RequireReaderBridgeEvent annotationDrawn -RequireReaderEngineCommand applyHighlights -ArtifactDir captures\reader-selection\20260619-selection-note-hidden-quoted
```

Result:

- PASS: Copy run captured `selectionChanged(footnote=true, ...)` with CFI, context text, and bounding rect from the Anx-style payload.
- PASS: Copy run found the native `Highlight`, `Copy`, and `Note` actions in the UI hierarchy.
- PASS: Copy run reached the native app boundary: `Reader selection copied length=31`.
- PASS: Note run captured `selectionChanged(footnote=true, ...)` with CFI, context text, and bounding rect.
- PASS: Note run saved through the annotation path: `Dispatching reader engine command: applyHighlights(count=3)`.
- PASS: Note run received Foliate/Anx bridge confirmation through three `annotationDrawn` events, including the newly saved note CFI.

Artifacts:

- `captures\reader-selection\20260619-selection-copy-hidden-quoted\reader-devtools-probe.json`
- `captures\reader-selection\20260619-selection-copy-hidden-quoted\logcat-reader.log`
- `captures\reader-selection\20260619-selection-copy-hidden-quoted\window.xml`
- `captures\reader-selection\20260619-selection-note-hidden-quoted\reader-devtools-probe.json`
- `captures\reader-selection\20260619-selection-note-hidden-quoted\logcat-reader.log`
- `captures\reader-selection\20260619-selection-note-hidden-quoted\window.xml`

Remaining:

- This is dirty-emulator readerdev evidence using a DevTools-created selection. It closes the repeatable Copy/Note action-path smoke gate, but it does not replace physical release validation of user-driven text selection on the phone/tablet.

## 2026-06-19 Host Guard: Font Size Must Override Publisher Absolute Text Sizes

Trigger:

- User reported that the reader Font size controller changed titles/headings but not the actual ebook body text, pushing the content down instead of scaling the prose.
- The earlier eta75 emulator `font-size` probe proved normal paragraphs scale when publisher styles are off, so the next suspect was the publisher-style branch.

Diagnosis:

- `readerTypographyCss(settings)` returned an empty stylesheet whenever `publisherStyles === true`.
- That preserved book-authored absolute paragraph sizes while the reader root and headings could still change, matching the reported behavior.
- The policy was too broad: publisher styles may preserve book family/weight/spacing choices, but they must not disable user font-size and line-height controls.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes
```

Result:

- RED: the new host guard failed before the fix at `ReaderRuntimeSettingsBridgeTest.kt:358`, proving the test caught the old publisher-style early return.
- PASS: `readerTypographyCss` now keeps `html` reader font-size, `body` `1rem` sizing, line-height, and prose `font-size: 1em !important` active even when publisher styles are enabled.
- PASS: publisher styles still gate Navic overrides for family, font weight, letter spacing, word spacing, indent, and heading-size multipliers.
- PASS: `node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js`.
- PASS: focused Android host test after the fix.

Remaining:

- This is a source/host guard. The installed eta75 emulator and any physical release APK still contain the old assets until a new build is installed.
- Physical Tab S9 Ultra validation should repeat the exact reported page with Publisher styles on and off.

## 2026-06-19 Readerdev Runtime Probe: Publisher Styles Still Scale Body Text

Trigger:

- Close the gap left by the host-only publisher-style font-size guard.
- Prove the installed Android WebView runtime catches the old failure and passes after reinstalling a dirty readerdev build with the current assets.

Setup:

- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed package after dirty reinstall: `versionName=v1.0.11-eta75`, `versionCode=408`, `lastUpdateTime=2026-06-19 10:00:30`.
- New DevTools probe: `font-size-publisher-styles`.

Commands:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size-publisher-styles --local-port 9232
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size-publisher-styles --local-port 9233
```

Result:

- RED before reinstall/current assets: old installed runtime changed root font size from `16px` to `22.4px`, but the injected publisher-styled paragraph stayed fixed at `12px -> 12px`; the probe failed with `publisherParagraphDelta=0`.
- PASS after dirty reinstall/current assets: the injected publisher-styled paragraph scaled from `16px` at 100% to `22.4px` at 140%, with `publisherParagraphDelta=6.4` and `rootDelta=6.4`.
- PASS: the probe restores the original reader font size and publisher-style setting after measuring.
- PASS: this confirms the user Font size control now reaches visible body text even when publisher styles are enabled in the Android WebView path.

Remaining:

- This is dirty readerdev emulator evidence, not a packaged GitHub release claim.
- Physical Tab S9 Ultra validation should still repeat the exact Hobbit page after a release build containing this fix is installed.

## 2026-06-19 Release Candidate: v1.0.11-eta76

Trigger:

- Publish the reader font-size fix because it is a major visible reader-control bug: publisher-styled body text did not scale with the Font size controller.
- Include the Android/WebView `font-size-publisher-styles` probe that proved the old runtime failed and the current dirty readerdev runtime passed.

Commands and release flow:

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta76
git tag v1.0.11-eta76
gh api repos/Darkaxt/Navic/git/refs -f ref=refs/tags/v1.0.11-eta76 -f sha=15c473ce6b13388157e60948f584241e90b0208d
.\scripts\publish-github-release.ps1 -Tag v1.0.11-eta76 -Repo Darkaxt/Navic -Remote fork -Branch master -RunId 27811392478 -SkipPush -Background
```

Result:

- PASS: Android release workflow run `27811392478` completed successfully.
- PASS: `Build Android APK` completed successfully, including release Gradle build, release APK signing verification, and APK artifact upload.
- PASS: iOS jobs were skipped.
- PASS: GitHub release was published: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta76`.
- PASS: Android asset was uploaded: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta76/Navic.apk`.
- APK digest from GitHub release metadata: `sha256:61684c844ab121aa3e1f4804fed7e62ae44e7e8614c53bd246832560c999bdc7`.

Remaining:

- Physical Tab S9 Ultra validation is still required on the exact Hobbit page and settings that exposed the font-size issue.
- If the body text still fails to scale on the physical release build, collect a computed-style sample from the release WebView with Publisher styles enabled.

## 2026-06-19 Readerdev Runtime Probe: Tab S9 Page Box Diagnostics

Trigger:

- Follow up on the Tab S9 Ultra report where prose looked too narrow horizontally and cramped vertically.
- Separate stale APK/font-size behavior from page-box math and publisher/content CSS.

Setup:

- Device: `emulator-5554`.
- Viewport override: Galaxy Tab S9 Ultra portrait (`wm size 1848x2960`, `wm density 240`), observed WebView CSS viewport `1232x1974` at DPR `1.5`.
- Package after dirty reinstall: `darkaxt.navic.readerdev`, `versionName=v1.0.11-eta76`, `versionCode=409`, `lastUpdateTime=2026-06-19 10:39:28`.
- Loaded publication: `A Memory of Light (epub)`, section `OEBPS/Text/sinopsis.xhtml`.

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size-publisher-styles --local-port 9224
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe page-box --local-port 9226
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanReadRendererPageBoxWithoutMutatingContent
node --check tools\reader-harness\src\adb-webview-eval.mjs
```

Result:

- PASS: fresh readerdev install reached `publicationReady`.
- PASS: publisher-style font-size runtime probe still scales body prose from `16px` at 100% to `22.4px` at 140%, with `publisherParagraphDelta=6.4`.
- PASS: enhanced page-box probe remains read-only and reports prose diagnostics without dispatching reader commands or injecting DOM.
- PASS: page-box probe on the loaded text section reported `maxInlineSize=1133px`, renderer `1232x1974`, document width `1133px`, body width `1061px`, `documentToViewportWidthRatio=0.92`, and `bodyToDocumentWidthRatio=0.936`.
- PASS: first visible prose element had `fontSize=16px`, `lineHeight=24.8px`, `maxWidth=none`, and no inline margins.
- PASS: focused host guard and JS syntax check passed after adding these diagnostics.

Conclusion:

- On the current eta76 readerdev code path, Tab S9 portrait page-box math is not hard-capping prose to the 720px column threshold; the loaded text section uses most of the available folio width.
- The reported physical Tab S9 narrow-looking Hobbit page should be rechecked on eta76. If it persists, the next evidence to collect is the enhanced `page-box` output for that exact page plus publisher-style state, because the cause is likely page/book CSS, a stale APK, or a page-specific layout rule rather than the current adaptive page-box calculation.

## 2026-06-19 Progress Rail Endpoint Probe

Scope:
- Close part of the Priority 0 progress-rail validation gap with a repeatable DevTools probe that exercises the same `goToChapterProgress` command path used by the native Komikku chapter rail.
- Avoid false greens from frontmatter sections by scanning the EPUB spine and selecting the strongest multi-page candidate before validating chapter endpoint `0` and `1`.

Implementation:
- Added `chapter-progress-endpoints` to `tools\reader-harness\src\adb-webview-eval.mjs`.
- Added the probe to `scripts\adb-reader-smoke.ps1` so future phone/emulator runs can capture it through the normal artifact path.
- The probe pins its long-running CDP promise on `window.__navicChapterProgressProbePromise`; without this, Chromium could collect the awaited promise while Foliate swapped EPUB sections.
- The probe records candidate attempts, including sections that fail to emit a location snapshot, records all successful candidates, chooses the largest chapter candidate, and only passes when endpoint `0` reports `chapterPageIndex=0` and endpoint `1` reports `chapterPageIndex=chapterPageCount - 1`.

Verification:

```
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeChapterProgressEndpoints --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperInjectsReaderBridgeEventsThroughDevTools
node --check tools\reader-harness\src\adb-webview-eval.mjs
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe chapter-progress-endpoints --local-port 9238
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe chapter-progress-endpoints --local-port 9238 > tmp\chapter-progress-strongest-emulator.json
```

Results:
- RED before harness support: `adbWebViewEvalHelperCanProbeChapterProgressEndpoints` failed because the probe did not exist.
- RED before candidate discovery: live probe stopped on `sinopsis.xhtml` with `chapterPageCount=1`.
- RED before candidate-error capture: live probe aborted when `cubierta.xhtml` did not emit a `locationChanged` snapshot.
- RED before promise pinning: live probe failed with DevTools `Promise was collected` while navigating EPUB sections.
- RED before strongest-candidate selection: live probe selected `OEBPS/Text/TitlePage-01.xhtml` with only `chapterPageCount=2`, proving the previous check could pass on frontmatter rather than a real chapter.
- PASS: focused host checks passed.
- PASS: `node --check` passed.
- PASS: strengthened host guard requires `successfulCandidates`, `bestCandidate`, and largest-candidate comparison before the helper can be considered valid.
- PASS: live eta76 `readerdev` probe on `emulator-5554` scanned 65 candidates, recorded 64 successful candidates, selected `OEBPS/Text/Chapter-37.xhtml` with `chapterPageCount=45`, and verified endpoint `0 -> chapterPageIndex 0 / 45` and endpoint `1 -> chapterPageIndex 44 / 45`.
- PASS: the final endpoint used pagination profile `navic-pagination-v1:952170858`, `pageIndex=333`, `pageCount=388`.

Artifact:
- `tmp\chapter-progress-emulator.json`
- `tmp\chapter-progress-strongest-emulator.json`

Remaining:
- This is dirty-emulator readerdev evidence, not physical-phone release proof. The P0 still needs a clean release/phone run on the chapters/pages that previously showed `10 / 12`, `2 / 4`, or page-1 rail-button failures.

## 2026-06-19 Font Size Controller Recheck

Scope:
- Recheck the user report that the Font size controller changes chapter/title-like text but not normal EPUB body text, effectively pushing prose down instead of scaling it.

Evidence:
- Current master includes `a157a8f9 Fix reader font size under publisher styles` and eta76 includes that commit.
- `readerTypographyCss(settings)` no longer disables typography CSS when `publisherStyles === true`; it keeps `html` on `--reader-content-font-size`, forces `body` to `1rem`, and collapses prose containers/descendants back to `1em`.
- Covered EPUB body shapes include direct `body > span`, direct `body > a`, span-wrapped paragraphs, fixed-size paragraphs, and converted `<div>...<br/>...</div>` text blocks.

Verification:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs font-css-smoke
```

Results:
- PASS: `font-css-smoke` confirmed direct body text, span-wrapped paragraph text, block-wrapped text, fixed-size paragraph text, and headings all scale through the same Font size controller path.
- PASS: existing focused host result for `ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes` reports `tests="1" failures="0" errors="0"`.
- BLOCKED: live emulator WebView proof of the exact visible EPUB page was not meaningful in this check because the emulator was on the Books grid, not inside reader content (`captures\reader-font-size\eta76-current-font-size-context.png`).

Conclusion:
- The current source and eta76 assets address the known root cause: publisher styles preserving absolute body prose sizes while headings scale.
- If the physical Tab S9 Ultra still reproduces the symptom on eta76, collect a real computed-style sample from the exact visible Hobbit prose page. The remaining likely cause would be another page-specific EPUB markup shape not covered by the current body-prose selectors, not the older publisher-style early return.

## 2026-06-19 Font Size DevTools Probe Hardening

Trigger:
- The Android DevTools `font-size-publisher-styles` probe hung when the WebView target was listed with `visible=false`.
- Root cause: the font probes waited on `requestAnimationFrame`; Android WebView can pause animation frames for a hidden/non-visible target even though CDP evaluation still works.

Implementation:
- Added a host guard that forbids `requestAnimationFrame` usage inside `runFontSizeProbe` and `runPublisherStyleFontSizeProbe`.
- Replaced animation-frame waits with synchronous layout/style flushes after `NavicReaderBridge.dispatch(...)` completes.
- Added stale probe cleanup at the start of both font probes, removing `[data-navic-font-size-probe="true"]` and `[data-navic-publisher-font-size-probe="true"]` before measuring real EPUB content.
- The real-text sampler now ignores both probe node families, so killed diagnostic runs cannot pollute later evidence.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalFontSizeProbesDoNotDependOnAnimationFrames --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbePublisherStyleFontSizeOverride --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperFontSizeProbeCleansSyntheticParagraph
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperFontSizeProbeCleansSyntheticParagraph --rerun-tasks
node --check tools\reader-harness\src\adb-webview-eval.mjs
node tools\reader-harness\src\run-reader-harness.mjs font-css-smoke
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size-publisher-styles --local-port 9245
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size --local-port 9246
```

Results:
- RED before fix: `adbWebViewEvalFontSizeProbesDoNotDependOnAnimationFrames` failed because the probes used `requestAnimationFrame`.
- PASS: focused host XML reported `tests="3" failures="0" errors="0"` for the no-animation-frame guard, publisher probe guard, and cleanup guard.
- PASS: focused rerun for `adbWebViewEvalHelperFontSizeProbeCleansSyntheticParagraph` completed successfully.
- PASS: `node --check` and `font-css-smoke` passed.
- PASS: live eta76 readerdev `font-size-publisher-styles` probe returned instead of hanging and measured publisher paragraph/root scaling `16px -> 22.4px` with `delta=6.4`.
- PASS: live eta76 readerdev `font-size` probe measured real EPUB `blockquote`/`p` samples, excluding stale synthetic probe nodes; every sampled real text element scaled `16px -> 22.4px` with `delta=6.4`.

Remaining:
- This proves the current readerdev runtime and hidden WebView DevTools path can validate font-size scaling reliably. It still does not replace a physical Tab S9 Ultra computed-style sample on the exact Hobbit page if that device still shows body text not scaling.

## 2026-06-19 Font Size Publisher Wrapper Fix

Trigger:
- User reported again that the Font size controller changed chapter/title-like text but not the main ebook body text, pushing prose downward instead of scaling it.

Root cause:
- The previous CSS collapsed prose containers to `font-size: 1em !important`.
- That works when the parent is `body`, but fails when an EPUB publisher pins a wrapper such as `section`, `div`, or another container to a fixed size. In that case paragraphs inherit the wrapper's fixed `10px`/small size while headings can still repaint separately, matching the reported symptom.

Implementation:
- Added a focused browser-harness regression for a fixed-size publisher wrapper around paragraph body text.
- Changed prose block/container font sizing in `readerTypographyCss` from parent-relative `1em` to reader-root-relative `1rem`.
- Kept inline descendants (`span`, `font`) at `1em`, so inline formatting inherits from the corrected paragraph size instead of the publisher wrapper.
- Tightened the Android host source guard so block containers must reset to `1rem` before inline descendants inherit through `1em`.

Verification:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes
git diff --check
```

Results:
- RED before fix: `font-css-smoke` failed with `Expected font-size control to scale publisher nested-wrapper body text; observed 10px -> 10px`.
- PASS after fix: `font-css-smoke` passed, covering direct body text, span-wrapped paragraphs, block-wrapped text, nested fixed publisher wrappers, fixed-size paragraphs, and headings.
- PASS: JS syntax checks passed for the harness and helper assets.
- PASS: focused Android host check completed successfully without new failures. A forced `--rerun-tasks` attempt stopped making log progress after `compileAndroidMain` and was stopped, so the dynamic browser harness remains the red/green proof for this fix.
- PASS: `git diff --check` passed.

Remaining:
- This is source/browser-harness proof, not a physical Tab S9 Ultra release proof. The next release/phone validation should run the DevTools `font-size` probe on the exact visible Hobbit page if the symptom still appears.

## 2026-06-19 ReaderDev Start Progress Hook

Scope:
- Add a deterministic readerdev launch path for resume/progress validation using a route-level 0..1 progress fraction.
- This is an enabling hook for Priority 0 resume/persistence validation, not a claim that disrupted-drag resume is fully device-proven.

Implementation:
- `Screen.Reader` now accepts `startProgress`.
- `ReaderOpenRequest` converts explicit route progress into `ReaderEngineOpenRequest.startLocator`.
- Android readerdev launch extras accept `navic.dev.reader.start_progress` / `NAVIC_READER_DEV_START_PROGRESS`, clamp it to `0.0..1.0`, and pass it into the reader screen.
- `scripts\install-reader-dev.ps1` can pass `-StartProgress` or the env-file value into the launch intent.
- `navic-reader-dev.env.example` documents the optional progress fraction.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
git diff --check
```

Results:
- RED before implementation: `ReaderOpenRequestFactoryTest.openRequestCarriesExplicitRouteProgressForReaderDevResumeValidation` failed to compile because `Screen.Reader` had no `startProgress` parameter.
- PASS after implementation: `:composeApp:testAndroid` completed successfully with `BUILD SUCCESSFUL in 3m 13s`; the run executed `testAndroidHostTest` and `testAndroid` with `24 actionable tasks: 3 executed, 21 up-to-date`.
- PASS: `git diff --check` passed.
- Caveat: the first verification run stopped making log progress at `compileAndroidMain` while stale Gradle/Kotlin Java processes were present. After stopping the stale processes, the same logged run continued and completed successfully. The only warnings were existing Kotlin/daemon warnings unrelated to this hook.

Remaining:
- Use this hook in a readerdev/emulator or physical-device run to validate actual resume after disrupted drag/app interruption. Do not mark the P0 resume issue closed until that runtime flow is proven.

## 2026-06-19 ReaderDev Start Progress Runtime Check

Scope:
- Validate the new readerdev `-StartProgress` launch hook on a running Android emulator.
- This checks deterministic route-level progress injection and Foliate runtime relocation. It does not close the full disrupted-drag/app-kill resume P0.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Launch command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 `
  -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env `
  -Package darkaxt.navic.readerdev `
  -DeviceSerial emulator-5554 `
  -RequireReaderLaunch `
  -StartProgress 0.37
```

Results:
- PASS: readerdev discovered a Bindery EPUB target, built `androidApp:assembleReaderDev`, installed successfully, and launched `darkaxt.navic.readerdev`.
- PASS: Android focus confirmed `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS: runtime logs showed `progress-seek:start 0.37`, `loadDoc(index=25, sectionId=OEBPS/Text/Chapter-15.xhtml)`, cached pagination profile `navic-pagination-v1:952170858`, and `progress-seek:done 0.37`.
- PASS: runtime location emitted `progress=0.3704074801253666`, `pageIndex=147`, `pageCount=388`, `chapterPageIndex=3`, `chapterPageCount=9`, `tocTitle="15. Your Neck in a Cord"`.
- PASS: DevTools probe confirmed the same location from inside the WebView:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe relocation-payload --local-port 9234
```

- PASS: the probe returned `href=OEBPS/Text/Chapter-15.xhtml`, `progress=0.3704074801253666`, `pageIndex=147`, `pageCount=388`, and `fraction=0.3704074801253666`.
- OBSERVED: the captured screen still showed the native cover surface. That is consistent with the current shell policy of showing the cover before the resumed/sought location, so visual screenshot alone is not reliable evidence for the start-progress hook.
- OBSERVED: a center tap on the native cover did not dismiss it on this emulator. That is a cover interaction issue to track separately from the WebView start-progress hook.
- HARNESS GAP: `scripts\install-reader-dev.ps1` printed `Waiting for reader publicationReady bridge event before capture...` but did not append `Reader publication ready: ...` even though logcat clearly contained `Reader bridge event: publicationReady`. The wait condition or log-tail capture should be hardened before relying on that script line as the sole readiness signal.

Artifacts:
- `tmp\readerdev-start-progress\install-start-progress-out.log`
- `tmp\readerdev-start-progress\install-start-progress-err.log`
- `tmp\readerdev-start-progress\start-progress-screen.png`
- `tmp\readerdev-start-progress\after-cover-tap-screen.png`

Conclusion:
- The readerdev start-progress hook is runtime-proven on emulator for a deterministic progress launch into the EPUB WebView.
- The P0 resume issue remains open until the same infrastructure is used to validate interrupted drag/app-kill restore behavior.

## 2026-06-19 Native Top Chrome Overlay Runtime Check

Scope:
- Fix and validate the regression where center tap reached the native reader controller but did not render the Komikku chrome over the EPUB shell cover.
- Remove the global `STOP, READ!` sideloading dialog mount from the app shell.

Root cause:
- ADB logs showed the native tap handler and controller state were correct:
  `Reader native tap action=MENU`, then `menuVisible=false->true`.
- `ReaderRoot` also recomposed with `Reader chrome overlay visible=true`.
- The previous common Compose sibling/Popup overlay did not appear in screenshots or UI hierarchy above the Android reader surface.
- The working fix is to let the Android native frame host own a top `ComposeView` child above the WebView/shell-cover frame and feed it the real `KomikkuComposeOverlay` content.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Launch command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 `
  -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env `
  -Package darkaxt.navic.readerdev `
  -DeviceSerial emulator-5554 `
  -RequireReaderLaunch `
  -StartProgress 0.37
```

Verification:
- PASS: `ReaderKomikkuBackboneResetTest` passed with the new native-overlay contract.
- PASS: focused `ReaderRuntimeCommonChromeTest`, `ReaderControllerTest`, and `ReaderCoordinatorTest` passed.
- PASS: `git diff --check` passed.
- PASS: readerdev installed and launched with `Reader publication ready`.
- PASS: center tap on shell cover logged `Reader native tap action=MENU`, `menuVisible=false->true`, and `Reader chrome overlay visible=true`.
- PASS: UI hierarchy after center tap contained the top chrome nodes: `Back`, `A Memory of Light`, `Cover`, and `Bookmark`.
- PASS: screenshot after center tap visibly showed the top Komikku chrome over the shell cover.
- PASS: second center tap logged `menuVisible=true->false` and screenshot showed the cover without chrome, proving the top overlay did not trap the center tap.
- PASS: no `STOP, READ!` dialog appeared during readerdev launch.
- PASS: `SideloadingDialog.kt`, `showedSideloadingWarning`, the readerdev bypass assignment, and all `sideloading_warning_*` resource keys were removed.

Artifacts:
- `tmp\reader-overlay-native-host\center-tap.png`
- `tmp\reader-overlay-native-host\center-tap-hide.png`
- `tmp\reader-overlay-native-host\reader-native-overlay.xml`

Remaining:
- This validates shell-cover center tap show/hide on emulator. Physical phone release-package validation is still required before calling it user-accepted.
- There are no runtime code paths or resource keys left for the sideloading warning; remaining source hits are negative guard assertions only.

## 2026-06-19 Native Top Chrome Overlay on Normal EPUB Text Page

Scope:
- Validate that the native top chrome overlay is not only visible over the shell cover, but also above the actual EPUB WebView/text surface.
- Validate that the native overlay does not trap input after it appears; the next center tap must hide the menu.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Prior launch: same readerdev install from the native top chrome overlay check.

Steps:
- Started from the shell cover state proven in the previous section.
- Swiped from cover into the EPUB content.
- Waited for the page to settle on a normal text page.
- Center-tapped the text page at `924,1480`.
- Captured logs, screenshot, and UI hierarchy.
- Center-tapped the same point again.
- Captured logs, screenshot, and UI hierarchy.

Results:
- PASS: cover swipe committed into EPUB content with `Reader shell cover command action=Right`, then `Reader viewer action=TurnPage(direction=Next)`, `shellCover=true->false`.
- PASS: settled screenshot showed a normal EPUB text page at `148 / 388`.
- PASS: first center tap on the text page logged `Reader native tap action=MENU`, then `Reader viewer action=Menu menuVisible=false->true shellCover=false->false`.
- PASS: the WebView emitted `selectionCleared()` before the native tap, but it did not suppress native menu handling.
- PASS: `ReaderRoot` logged `Reader chrome overlay visible=true menu=true shellCover=false dialog=null`.
- PASS: screenshot showed Komikku top chrome, right progress rail, and bottom controls above the rendered text page.
- PASS: UI hierarchy contained `Back`, `A Memory of Light`, `15. Your Neck in a Cord`, `Bookmark`, `Previous chapter`, `Chapter page slider`, and bottom bar nodes.
- PASS: second center tap logged `Reader native tap action=MENU`, then `Reader viewer action=Menu menuVisible=true->false shellCover=false->false`.
- PASS: `ReaderRoot` logged `Reader chrome overlay visible=false menu=false shellCover=false dialog=null`.
- PASS: screenshot after the second tap showed the EPUB text page without chrome, proving the native overlay does not trap the next center tap.

Artifacts:
- `tmp\reader-overlay-text-page\after-cover-swipe-settled.png`
- `tmp\reader-overlay-text-page\text-center-tap-show.png`
- `tmp\reader-overlay-text-page\reader-text-overlay.xml`
- `tmp\reader-overlay-text-page\text-center-tap-hide.png`
- `tmp\reader-overlay-text-page\reader-text-overlay-hide.xml`

Remaining:
- This is emulator readerdev evidence. It still needs a physical/release candidate check before being treated as the user-facing fix.
- Image-page center tap behavior is tracked in the next section.

## 2026-06-19 Native Top Chrome Overlay on EPUB Image Page

Scope:
- Validate the reported image-page regression: a short tap on an EPUB image must route through the native tap-zone controller and show/hide menu chrome.
- It must not advance the page, skip the image page, or let the WebView image handler consume the short tap.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- EPUB: `A Memory of Light`
- Section: `OEBPS/Text/Chapter-15.xhtml`

Harness support:
- Added read-only DevTools probe `image-hit-targets` to `tools\reader-harness\src\adb-webview-eval.mjs`.
- The probe reports loaded image/media element bounds in native root coordinates, so ADB taps can target real image pixels instead of guessed screen positions.

Steps:
- From the previously validated normal text page, probed image targets:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --local-port 9234 --probe image-hit-targets
```

- The first probe found one image in the current content document, but off-screen to the left.
- Navigated backward/forward using native tap zones until the image target became visible.
- The visible target was:
  - `tagName=IMG`
  - `rootX=616`
  - `rootY=374`
  - `rootLeft=518`
  - `rootTop=255`
  - `width=197`
  - `height=237`
- Short-tapped the image center at `616,374`.
- Captured logs, screenshot, relocation payload, and image-hit probe output.
- Short-tapped the same image coordinate again to hide the chrome.
- Captured logs, screenshot, and relocation payload.

Results:
- PASS: first short tap on the visible image logged `Reader native tap action=MENU x=616.0 y=374.00098`, then `Reader viewer action=Menu menuVisible=false->true shellCover=false->false`.
- PASS: WebView emitted `selectionCleared()` before the native tap, but no `readerContentTapHandled` or image-claim event appeared; the image handler did not consume the short tap.
- PASS: `ReaderRoot` logged `Reader chrome overlay visible=true menu=true shellCover=false dialog=null`.
- PASS: screenshot showed top chrome, bottom controls, and right rail above the image page.
- PASS: relocation after the first tap remained on `pageIndex=144`, `chapterPageIndex=0`, `chapterPageCount=9`, so the short tap did not advance or skip the image page.
- PASS: `image-hit-targets` after the first tap still reported the same visible image target at `rootX=616`, `rootY=374`.
- PASS: second short tap on the same image coordinate logged `Reader native tap action=MENU`, then `Reader viewer action=Menu menuVisible=true->false shellCover=false->false`.
- PASS: `ReaderRoot` logged `Reader chrome overlay visible=false menu=false shellCover=false dialog=null`.
- PASS: screenshot after the second tap showed the same image page without chrome.
- PASS: relocation after the second tap still remained on `pageIndex=144`.

Artifacts:
- `tmp\reader-overlay-image-page\image-short-tap.png`
- `tmp\reader-overlay-image-page\image-short-tap-hide.png`

Remaining:
- This validates image-page short-tap behavior on emulator readerdev. It still needs physical/release package confirmation before closing the user-facing bug.
- Long-press image behavior and sepia overlay toggling were not validated in this section.

## 2026-06-19 Selection Action Validation During Bindery Maintenance

Scope:
- Continue Phase 5 selection-action validation on the already-open readerdev session without relaunching or reseeding the app.
- Bindery was under server maintenance, so this pass deliberately avoided OPDS, login, download, or app restart flows.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed version: `v1.0.11-eta76`
- Running PID: `19346`
- EPUB session already open before validation started.

Results:
- PASS: Copy action was validated sequentially with the `selection-payload` probe. The smoke run exited `0` and found the required `Reader selection copied length=` log.
- PARTIAL: Note action opened the Compose note dialog, accepted typed text in the `EditText`, and enabled `Save`.
- FAIL/UNVERIFIED: tapping `Save` closed the note dialog, but logcat did not show `applyHighlights`, `annotationDrawn`, or another observable write/engine command. This means the Note path cannot be called validated on eta76.
- INVALID SETUP: an earlier parallel Copy/Highlight/Note run produced collisions in UI hierarchy state. Only the sequential Copy result should be treated as evidence.

Artifacts:
- `captures\reader-selection\20260619-eta76-selection-copy-seq`
- `captures\reader-selection\20260619-eta76-selection-note-open-seq`

Follow-up:
- Add or expose reliable note-save evidence before closing Phase 5. The minimum acceptable evidence is a controller log, engine command log, bridge event, or persisted annotation UI route after `Save`.
- Re-run Highlight sequentially if this section is used as release-candidate evidence; the available Highlight pass came from the invalid parallel run and should remain advisory only.

## 2026-06-19 Bindery-Maintenance Safe Reader Probes

Scope:
- Continue validation without relying on Bindery while the server was under maintenance.
- Do not relaunch, reseed, download, or open OPDS flows.
- Use only the already-open readerdev WebView and host tests.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Installed version: `v1.0.11-eta76`
- Running PID: `19346`
- Foreground activity: `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`
- Display/app bounds: `1848x2960`, `sw1232dp`, fullscreen portrait.

Host evidence:
- Added executable coordinator coverage for Note Save routing:
  - `ReaderCoordinatorTest.selectionNotesSaveRouteThroughControllerAndCurrentEngineAdapter`
  - The focused test passed immediately.
- Interpretation: common controller/coordinator/engine-adapter code already routes Note Save into `ReaderBridgeCommand.ApplyHighlights`.
- Remaining Note Save failure layer is therefore Android UI click delivery, WebView command dispatch, or Foliate annotation draw/runtime acknowledgment, not the shared controller route.

Safe ADB/DevTools probes:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --probe page-box
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size-publisher-styles
```

Results:
- PASS: `page-box` found the already-loaded reader WebView without relaunching.
- PASS: renderer occupied the full viewport (`viewRect` and `rendererRect` both `1232x1974` CSS px).
- PASS: current renderer layout attributes were visible: `max-inline-size=1133px`, `max-block-size=1846px`, `max-column-count=1`, `top-margin=90px`, `bottom-margin=50px`.
- PASS: existing EPUB paragraph text scaled when font size changed from `100` to `140`: every sampled paragraph moved from `16px` to `22.4px`.
- PASS: the synthetic publisher-style paragraph also scaled from `16px` to `22.4px` with `publisherStyles=true`.
- PASS: the font-size probe restored `fontSizePercent=100` afterward.

Interpretation:
- The “titles resize but body text does not” failure is not reproducing on the currently installed eta76 session with `A Memory of Light`; existing body paragraphs and publisher-style text both scale.
- The Tab S9 Ultra screenshot complaint should be treated as page-box composition/margin/default-layout work, not as proof that the font-size command is globally ignored.
- The page-box sample shows large paginated body width because Foliate lays the current chapter into horizontal columns; the useful layout evidence is the current visible column (`firstProse.rect.width=1061`) against `max-inline-size=1133`.

Remaining:
- Reproduce the Tab S9 Ultra margin complaint on the actual tablet/package before changing defaults.
- Validate Note Save again only after installing a build that contains `Reader selection note save length=...`; eta76 cannot isolate whether the Save callback fired.

## 2026-06-19 Bindery-Safe Guard: Note Annotation Payload Durability

Scope:
- Continue note/annotation validation without touching Bindery during server maintenance.
- Clarify the difference between shared-code correctness and installed eta76 runtime uncertainty.

Reference behavior:
- Anx stores notes as annotation metadata (`note` / reader note data) and reopens them through the annotation click path.
- Navic intentionally keeps that behavior behind the controller boundary: the engine receives note-bearing annotations, Foliate paints them, and annotation clicks resolve into `ReaderAnnotationPopupState`.

Added guards:
- `ReaderAnnotationStateTest.noteAnnotationJsonRoundTripKeepsReaderNoteForMarkerAndPopup`
  - Proves a note-bearing annotation survives Navic persistence JSON with selected text, chapter label, and note body intact.
- `ReaderBridgeProtocolTest.applyHighlightsCommandDispatchesPersistedAnnotationBatch`
  - Strengthened to prove `ReaderBridgeCommand.ApplyHighlights` serializes the note payload, not only the CFI/color.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderAnnotationStateTest --tests paige.navic.reader.ReaderBridgeProtocolTest.applyHighlightsCommandDispatchesPersistedAnnotationBatch
git diff --check
```

Results:
- PASS: focused host tests passed.
- PASS: `git diff --check` passed.
- NOTE: Kotlin daemon access to `C:\Users\darka\AppData\Local\kotlin\daemon\...` was denied under the managed sandbox, but Gradle fell back to non-daemon compilation and the build completed successfully.

Interpretation:
- The shared annotation store and bridge command path preserve the note payload needed for the squiggly note marker and annotation popup.
- The installed eta76 failure remains a runtime/UI delivery question: whether the Save tap fires on device and whether the installed build dispatches/acknowledges the annotation command. It is not evidence that note metadata is lost in the shared model.

## 2026-06-19 Host Guard: Chapter Rail Drops Stale Page Counts On Section Change

Scope:
- Continue progress-rail work without Bindery or device relaunch while the server was unstable.
- Target the user-visible class of bugs where chapter-local rail state reports nonsensical pages after chapter/section transitions.

Root cause:
- `ReaderChapterProgressState.updatedFrom(locator, tocTitle)` reused the previous chapter's `pageIndex`, `pageCount`, and `progress` whenever a new relocation omitted `chapterPageIndex` / `chapterPageCount`.
- That means a relocation into a new chapter with only global Foliate page data could inherit stale chapter-local rail data from the previous section.

Fix:
- When the relocation href changes and no fresh chapter-local page metadata is present, reset the chapter rail to the new href at `pageIndex=0`, `pageCount=1`, `progress=0.0`.
- Relocations that do carry Anx/Navic chapter-local metadata still feed the rail normally.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.chapterHrefChangeWithoutLocalPageMetadataClearsStaleRailPages
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.chapterHrefChangeWithoutLocalPageMetadataClearsStaleRailPages --tests paige.navic.reader.ReaderControllerTest.engineRelocationFeedsChapterLocalProgressForKomikkuRail --tests paige.navic.reader.ReaderControllerTest.engineRelocationDoesNotFeedKomikkuRailFromGlobalPageModel --tests paige.navic.reader.ReaderControllerTest.loadedDocumentPreventsChapterPageSeekFromTargetingPreviousSection --tests paige.navic.reader.ReaderControllerTest.chapterNavigatorArrowsNavigateAdjacentTocEntriesInsteadOfTurningPages
```

Results:
- RED: the new stale-rail test failed before the fix.
- GREEN: the stale-rail test and nearby chapter rail/navigation host tests passed after the fix.
- NOTE: under the managed sandbox, Kotlin daemon access to `C:\Users\darka\AppData\Local\kotlin\daemon\...` was denied and Gradle fell back to non-daemon compilation. The build and tests still completed successfully.

Remaining:
- This is host/controller evidence only. It does not close the release-device progress rail endpoint bug by itself.
- The next runtime gate still needs emulator/device proof that the rail reaches first/last pages and adjacent chapter buttons work from page 1 and endpoints.

## 2026-06-19 Bindery Maintenance Guard: Offline Reader Validation Only

Scope:
- User reported active server maintenance and warned that Bindery may stop working.
- Avoided OPDS/login/download/reseed/relaunch validation paths that would turn server instability into false reader regressions.
- Continued only Bindery-independent host checks around the globally removed sideload/STOP popup and the EPUB font-size pipeline.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest --tests paige.navic.reader.ReaderRuntimeAssetsTest --tests paige.navic.reader.FoliateAnxParityTest.phase6StyleDimensionsMatchAnxBookStyleContract
node --check tools\reader-harness\src\adb-webview-eval.mjs
```

Results:
- PASS: focused host reader suite passed.
- PASS: readerdev/sideload guard still proves the old sideload/STOP popup is globally removed from the readerdev launch path.
- PASS: font-size guards still require full `readerContentCss(settings)` reinjection into Foliate renderer and already-loaded EPUB documents before settings reflow.
- PASS: publisher-style font-size guard still requires prose containers and inline descendants to collapse to reader-root sizing instead of letting absolute publisher spans pin body text.
- PASS: `adb-webview-eval.mjs` syntax check passed.

Interpretation:
- This does not close a phone/runtime complaint by itself; it only proves the current source still contains the expected offline safeguards.
- While Bindery is unstable, do not publish or validate release candidates through server-backed launch/download flows unless the reader is already loaded and the probe does not require network.

## 2026-06-19 Bindery-Safe Guard: Note Marker And Reopen Route

Scope:
- Continue Note/annotation work without relaunching or downloading books while Bindery was under maintenance.
- Recheck the user-facing question: after saving a note, there must be a visual marker and a way to reopen it later.

Source route:
- `ReaderController.saveSelectionNote(...)` stores a note-bearing `ReaderAnnotation`, clears the draft, and emits `ReaderEngineCommand.ApplyAnnotations`.
- `FoliateEpubEngineAdapter` maps that command to `ReaderBridgeCommand.ApplyHighlights`.
- `navic-reader.js` forwards note-bearing annotations into Foliate `addAnnotation`, paints them through `drawAnnotation`, sets `data-navic-note-annotation`, and uses a note-specific marker color.
- `AnnotationClicked` resolves saved note metadata from the controller store and opens `ReaderAnnotationPopupState`, which is rendered by `KomikkuReaderAnnotationDialog`.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.selectionNotesSaveAsAnnotationsAndClearDraft --tests paige.navic.reader.ReaderControllerTest.annotationClicksResolveSavedNoteBodyFromControllerStore --tests paige.navic.reader.ReaderCoordinatorTest.selectionNotesSaveRouteThroughControllerAndCurrentEngineAdapter --tests paige.navic.reader.ReaderAnnotationStateTest.noteAnnotationJsonRoundTripKeepsReaderNoteForMarkerAndPopup --tests paige.navic.reader.FoliateAnxParityTest
```

Results:
- PASS: focused controller/coordinator/model note tests passed.
- PASS: `FoliateAnxParityTest` passed, including the note-bearing annotation draw route and the controller-owned annotation popup route.

Interpretation:
- The offline source path for "save note -> visible annotation marker -> tap annotation -> open native note popup" is present and guarded.
- This still does not prove the Android runtime Save tap or Foliate draw acknowledgment on a physical/release APK. Runtime validation remains required after a build with `Reader selection note save length=...` diagnostics is installed and the book is already loaded without depending on Bindery.

## 2026-06-19 Bindery-Safe Runtime Probe: Loaded Readerdev Font/Page Box

Scope:
- User warned Bindery may be unavailable due to server maintenance.
- Did not relaunch, reseed, download, or open OPDS flows.
- Used the already-foreground `readerdev` activity and the already-loaded WebView only.

Environment:
- Device: `emulator-5554`
- Package: `darkaxt.navic.readerdev`
- Foreground activity: `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`
- Running PID: `19346`

Validation:

```powershell
adb devices
adb shell dumpsys activity activities
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe page-box --local-port 9251
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size --local-port 9252
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size-publisher-styles --local-port 9253
```

Results:
- PASS: ADB found `emulator-5554` and Navic readerdev was foreground.
- PASS: `page-box` found the loaded reader WebView without launch/download. Renderer and view rects both occupied `1232x1974` CSS px.
- PASS: renderer attributes were `max-inline-size=1133px`, `max-block-size=1846px`, `max-column-count=1`, `top-margin=90px`, `bottom-margin=50px`.
- PASS: real EPUB paragraph samples scaled from `16px` to `22.4px` when Font size changed from `100` to `140`; every sampled paragraph reported `delta=6.4`.
- PASS: synthetic publisher-styled paragraph also scaled from `16px` to `22.4px` with `publisherStyles=true`.

Interpretation:
- The current loaded readerdev runtime does not reproduce the "titles resize but body text does not" bug.
- This is emulator/readerdev evidence only. The physical Tab S9 Ultra page remains a separate validation target if the same symptom appears on the installed release package.
- Because Bindery was unstable, no release/open/download workflow was attempted.

## 2026-06-19 Bindery-Safe Guard: Note Batch Debug Labels

Scope:
- Improve the next device validation pass for Note Save without relying on Bindery.
- The prior source route proved notes are persisted and routed, but logcat could not distinguish a note-bearing annotation batch from a plain highlight batch because the WebView host logged only `applyHighlights(count=...)`.

Fix:
- `ReaderEngineWebViewHost.android.kt` now logs `applyHighlights(count=<n>, notes=<m>)`, where `notes` counts annotations with a non-blank `note`.
- Added a host guard so this ADB-visible label cannot regress back to count-only evidence.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.androidReaderNoteAnnotationBatchesAreVisibleInBridgeCommandLogs
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.androidReaderNoteAnnotationBatchesAreVisibleInBridgeCommandLogs --tests paige.navic.reader.ReaderControllerTest.selectionNotesSaveAsAnnotationsAndClearDraft --tests paige.navic.reader.ReaderCoordinatorTest.selectionNotesSaveRouteThroughControllerAndCurrentEngineAdapter --tests paige.navic.reader.ReaderAnnotationStateTest.noteAnnotationJsonRoundTripKeepsReaderNoteForMarkerAndPopup
```

Results:
- RED: the new host guard failed before the production change because `ApplyHighlights` labels were `applyHighlights(count=...)` only.
- GREEN: focused host guard and nearby note/controller/coordinator/model tests passed after the label included `notes=<m>`.
- NOTE: Kotlin daemon access was denied under the managed sandbox and Gradle fell back to non-daemon compilation; the build completed successfully.

Remaining:
- This is diagnostic plumbing, not proof that Note Save works on a physical/release APK. The next runtime validation should look for `Reader selection note save length=...` followed by `applyHighlights(count=..., notes=1)` and `annotationDrawn(...)`.

## 2026-06-19 Bindery-Safe Probe: Current Chapter Rail Endpoints

Scope:
- User warned Bindery may be unavailable due to server maintenance.
- Did not relaunch, reseed, download, or open OPDS flows.
- Used the already-foreground `readerdev` activity and the already-loaded WebView only.

Initial evidence:
- `relocation-payload` passed on the loaded readerdev WebView.
- `page-box` passed and confirmed the renderer/view still occupied the full `1232x1974` CSS px viewport.
- The older broad `chapter-progress-endpoints` probe did not return while scanning candidate spine hrefs. The reader UI stayed alive, so this was treated as a validation-tooling failure, not as proof of a reader endpoint regression.

Fix:
- Added `chapter-progress-current-endpoints`, a narrower DevTools probe that validates only the currently loaded chapter href from `diagnosticLocationSnapshot`.
- Added the probe to `adb-reader-smoke.ps1` so the documented smoke workflow can run it without scanning the whole spine.
- Added a host guard to ensure the current-chapter probe exists and does not call `Array.from(view?.book?.sections || [])`.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeCurrentChapterProgressEndpointsWithoutSpineScan
node --check tools\reader-harness\src\adb-webview-eval.mjs
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe chapter-progress-current-endpoints --local-port 9264
```

Results:
- RED: the host guard failed before the probe existed.
- GREEN: host guard passed after adding the probe and smoke-script wiring.
- PASS: `chapter-progress-current-endpoints` passed on the already-loaded readerdev WebView.
- PASS: current chapter href `OEBPS/Text/Chapter-37.xhtml` resolved endpoint 0 to `chapterPageIndex=0`, `chapterPageCount=45`.
- PASS: the same chapter resolved endpoint 1 to `chapterPageIndex=44`, `chapterPageCount=45`.
- PASS: global pagination profile remained stable at `paginationProfilePageCount=388`, `paginationProfileObservedChapterCount=64`.

Interpretation:
- Current-chapter rail endpoints are now validated in a Bindery-safe emulator flow.
- This does not replace physical/release validation for the original user-reported rail issues, but it gives a reliable probe that can be used when the reader is already loaded and the server is unavailable.
- The broad `chapter-progress-endpoints` probe should not be used as the first diagnostic while Bindery is unstable or when the current requirement is to validate the loaded chapter only.

## 2026-06-19 Controller Guard: Selection Clear Dismisses Note Draft

Scope:
- Bindery-independent controller boundary fix.
- Anx/Foliate owns the `selectionCleared` event; Navic/Komikku owns the native selection action UI and note draft state.
- A stale native note draft must not survive after the engine reports that the underlying selection has been cleared.

Fix:
- `ReaderController` now clears both `selection` and `selectionNoteDraft` on `ReaderEngineEvent.SelectionCleared`.
- Extended `ReaderControllerTest.selectionActionStateIsControllerOwnedAndClearedByEngine` to start a native note draft before sending `SelectionCleared`, then assert the draft is dismissed.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.selectionActionStateIsControllerOwnedAndClearedByEngine
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.selectionActionStateIsControllerOwnedAndClearedByEngine --tests paige.navic.reader.ReaderControllerTest.selectionNotesStartNativeDraftWithoutEngineCommands --tests paige.navic.reader.ReaderControllerTest.selectionNotesSaveAsAnnotationsAndClearDraft
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes
```

Results:
- RED: the extended controller test failed before the production change because `selectionNoteDraft` stayed non-null after `SelectionCleared`.
- GREEN: focused controller tests passed after clearing the draft with the selection.
- PASS: `FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes` passed, preserving the Anx phase-3 controller-route guard.
- NOTE: Kotlin daemon access was denied under the managed sandbox and Gradle fell back to non-daemon compilation; the build completed successfully.

Interpretation:
- Selection-cleared behavior is now owned consistently by the controller: native selection actions and note draft UI close when the engine clears the selection.
- This is host/controller proof only. Runtime validation of user-driven selection clear and Note Save remains required on emulator/device before release-readiness claims.

## 2026-06-19 Bindery-Safe Runtime Probe: Font Size Scaling

Scope:
- User warned Bindery may be unavailable due to server maintenance.
- Did not relaunch, reseed, download, open OPDS flows, or depend on server-backed state.
- Used the already-foreground `readerdev` activity and the already-loaded WebView only.
- Targeted the reported font-size concern: the control appeared to resize chapter/title text while leaving body prose unchanged.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbePublisherStyleFontSizeOverride --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalFontSizeProbesDoNotDependOnAnimationFrames
node --check tools\reader-harness\src\adb-webview-eval.mjs
adb devices
adb shell pidof darkaxt.navic.readerdev
adb shell dumpsys window | Select-String -Pattern "mCurrentFocus|mFocusedApp"
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size --local-port 9265
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe font-size-publisher-styles --local-port 9266
```

Results:
- PASS: focused host tests for font-size runtime probes and publisher absolute text-size override passed.
- PASS: JS syntax check passed for `tools/reader-harness/src/adb-webview-eval.mjs`.
- PASS: emulator device `emulator-5554` was connected, `darkaxt.navic.readerdev` was foreground, and the reader WebView was already loaded.
- PASS: `font-size` probe changed root/body/prose paragraph text from `16px` to `22.4px`, with `rootDelta=6.4`, `bodyDelta=6.4`, and `paragraphDelta=6.4`.
- PASS: the same probe sampled existing real document prose nodes from the loaded book and every sampled text node changed from `16px` to `22.4px`.
- PASS: `font-size-publisher-styles` probe forced a publisher-style paragraph with `font-size: 12px`; runtime CSS still scaled it from `16px` to `22.4px`, with `publisherParagraphDelta=6.4`.

Interpretation:
- The currently loaded emulator/runtime path does scale EPUB body prose and publisher-fixed prose when the reader font-size setting changes.
- This does not close the user's Tab S9 Ultra report. The remaining bug may be device, book, page, mode, or settings specific, and still needs physical-device validation when a reliable release candidate is available.
- The current generic CSS/bridge path is not proven broken by the Bindery-safe emulator probe.

## 2026-06-19 Bindery-Safe Runtime Probe: Chapter Opening Layout Diagnostics

Scope:
- User warned Bindery may be unavailable due to server maintenance.
- Did not relaunch, reseed, download, open OPDS flows, or depend on server-backed state.
- Used the already-foreground `readerdev` activity and the already-loaded WebView only.
- Targeted the tablet/foldable blank-space concern around chapter openings and page margins.

Change:
- Extended the read-only `page-box` DevTools probe to include per-content-document `chapterOpening` diagnostics.
- The probe now reports the first visible content element, first prose element, first heading, heading margin values, and whether the existing `data-navic-chapter-opening-margin-capped` normalization ran.
- The probe does not call `NavicReaderBridge.dispatch`, inject DOM, or mutate publication content.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanReadRendererPageBoxWithoutMutatingContent
node --check tools\reader-harness\src\adb-webview-eval.mjs
adb devices
adb shell pidof darkaxt.navic.readerdev
adb shell dumpsys window | Select-String -Pattern "mCurrentFocus|mFocusedApp"
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe page-box --local-port 9267
```

Results:
- RED: the host guard failed before the probe exposed `chapterOpening`, `data-navic-chapter-opening-margin-capped`, and heading `marginBlockStart` evidence.
- GREEN: the focused host guard passed after extending the probe.
- PASS: JS syntax check passed for `tools/reader-harness/src/adb-webview-eval.mjs`.
- PASS: emulator device `emulator-5554` was connected, `darkaxt.navic.readerdev` was foreground, and the reader WebView was already loaded.
- PASS: live `page-box` probe returned viewport `1232x1974`, `rendererRect=1232x1974`, `maxInlineSize=1133px`, `maxBlockSize=1846px`, `topMargin=90px`, and `bottomMargin=50px`.
- PASS: loaded content reported `chapterOpening.capped=true`; the first heading original margin was `96px`, capped to `83px`.
- PASS: first prose on the loaded chapter started at `y=516`; this probe shows the current blank area is not a collapsed renderer/page-box failure in this emulator state.

Interpretation:
- The current loaded emulator page has an active chapter-opening margin cap, and the renderer/page box is occupying the visible viewport.
- The remaining Tab S9 Ultra visual complaint is not closed. The blank-space source may be the publisher chapter-opening structure, heading block height, image/heading composition, or a device-specific viewport/settings combination.
- Future investigation should use this probe on the exact problematic device/page before changing CSS. A generic margin tweak would be guesswork without identifying which element creates the visible gap.

## 2026-06-19 Bindery-Safe Runtime Probe: Annotation Note Round Trip and Blank Endpoint Observation

Scope:
- User warned Bindery may be unavailable due to server maintenance.
- Did not relaunch, reseed, download, open OPDS flows, or depend on server-backed state.
- Used the already-running `readerdev` WebView only.
- Targeted the user question about Highlight/Note visibility and how notes can later be opened.

Change:
- Added a DevTools probe named `annotation-roundtrip` to `tools/reader-harness/src/adb-webview-eval.mjs`.
- The probe dispatches the real `applyHighlights` bridge command with a note-bearing annotation, instruments Foliate `addAnnotation`, and verifies the runtime `draw-annotation` and `show-annotation` bridge path.
- Added the probe to `scripts/adb-reader-smoke.ps1`.
- Added a host guard in `ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeAnnotationNoteRoundTrip`.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeAnnotationNoteRoundTrip
node --check tools\reader-harness\src\adb-webview-eval.mjs
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --local-port 9235 --probe annotation-roundtrip
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe annotation-roundtrip -ArtifactDir captures\reader-bridge-probes\20260619-annotation-roundtrip
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --local-port 9236 --probe page-box
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --local-port 9237 --probe chapter-progress-current-endpoints
```

Results:
- RED: the host guard failed before the `annotation-roundtrip` probe existed.
- GREEN: the focused host guard passed after adding the probe.
- PASS: JS syntax check passed for `tools/reader-harness/src/adb-webview-eval.mjs`.
- PASS: live `annotation-roundtrip` probe on eta76 readerdev captured `addAnnotation` with `note="Navic annotation roundtrip note"` and `value="epubcfi(/6/8!/4/2:12)"`.
- PASS: the runtime drew a note marker with `data-navic-note-annotation=true`, SVG tag `g`, and two child layers.
- PASS: the runtime emitted both `annotationDrawn` and `annotationClick` through the bridge.
- PASS: the smoke wrapper accepted and ran `-ReaderDevtoolsProbe annotation-roundtrip`.
- NOTE: the probe intentionally opened the native annotation dialog with a synthetic CFI. A screenshot after the probe showed the dialog and it was dismissed manually with ADB.
- FAIL/OPEN: after the dialog was dismissed, the current emulator page was still a blank texture-only reader page at Chapter 37 page `45/45`.
- Evidence: `chapter-progress-current-endpoints` reported `href=OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=44`, `chapterPageCount=45`, `chapterProgress=1`, `pageIndex=333`, and `pageCount=388`; the screenshot showed no prose.

Interpretation:
- Highlight/Note is not just a controller state change: the current runtime path can create a visible note marker and route annotation taps back to the native popup path.
- The user-facing note UX remains incomplete: there is no persistent notes list or obvious post-save affordance beyond tapping the in-document marker.
- The blank page is a separate progress/pagination endpoint bug. The current model can land on a terminal chapter page with no visible text. This should be fixed under the existing rail endpoint/pagination-profile work, not as an annotation issue.

Follow-up screen check:
- User reported the emulator screen looked weird: no text and only a popup.
- Captured `captures/reader-status/20260619-213823/screen.png` from the foreground `darkaxt.navic.readerdev` activity without relaunching the app or touching Bindery.
- PASS: no popup was visible in the captured state; the previous synthetic annotation dialog had already been dismissed.
- FAIL/OPEN: the captured page still showed only one line of prose, `"A world without Shadow."`, on a mostly blank paper-texture page with page number `334 / 388`.
- Interpretation: this confirms the active issue is the terminal-page pagination/packing endpoint, not a stuck dialog state. The loaded page should be investigated with the pagination profile/rail endpoint work before changing annotation or menu code.

Follow-up DevTools probes:
- Ran read-only `page-box` and `chapter-progress-current-endpoints` probes against the same foreground `readerdev` WebView.
- PASS: `page-box` reported a live loaded reader at `1232x1974`; `rendererRect=1232x1974`, `contentDocument=true`, `bodyTextLength=242809`, and the content body is not empty.
- PASS: `chapter-progress-current-endpoints` reported the current section as `OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=44`, `chapterPageCount=45`, `chapterProgress=1`, global `pageIndex=333`, `pageCount=388`, and `pageCountSource=pagination-profile`.
- FAIL/OPEN: the section endpoint maps to a nearly empty final visual page. The page is technically valid content, but it is not a good reader packing result.
- Interpretation: treat this as a pagination profile/page packing defect. It should be fixed by making terminal chapter pages pack or merge naturally when they only contain a tiny amount of text, not by changing annotation popups or Bindery loading.

Follow-up visible-page probe:
- Added read-only DevTools probe `visible-page-content` to measure the current renderer page, Foliate `renderer.page/pages/start/end/viewSize/containerPosition`, and viewport-intersecting content elements in renderer coordinates.
- Added host guard coverage and smoke-script support for `-ReaderDevtoolsProbe visible-page-content`.
- RED: host guard failed before the probe existed.
- GREEN: focused host guard passed after adding the probe, and `node --check tools\reader-harness\src\adb-webview-eval.mjs` passed.
- PASS: live probe at the terminal endpoint reported `rendererPage=45`, `rendererPages=47`, `rendererContainerPosition=50984`, `visibleTextLength=0`, and `visibleElementCount=0`.
- PASS: after one left-edge previous-page tap, the same probe reported `rendererPage=44`, `rendererPages=47`, and the only prose leaf on the page was `"A world without Shadow."`.
- Interpretation: Foliate exposes the current section with `pages=47`; Navic's current `pages - 2` section model counts page 45 as the final readable page, but page 45 is a blank terminal column and page 44 contains the final sentence. This confirms a trailing blank/sentinel column defect in chapter endpoint paging. Do not patch this by hiding page numbers or changing annotation/menu state.

Follow-up terminal blank-column fix:
- Changed reflowable pagination to keep Foliate's raw `pages - 2` math separate from Navic's visual/readable text page count.
- Navic now subtracts one terminal blank visual column from reflowable chapter page counts.
- Chapter progress endpoint seeks now normalize `progress=1` through `reflowableChapterProgressAnchor()` instead of passing Foliate `anchor=1` directly.
- Native boundary detection now treats the last visual text page as the edge, instead of waiting for Foliate's blank terminal column.
- RED: `ReaderRuntimeShellProgressTest.androidReaderSkipsTrailingBlankFoliateColumnForChapterEndpoints` failed before the runtime exposed raw/visual page-count split and normalized chapter-end anchors.
- GREEN: `ReaderRuntimeShellProgressTest` passed after the runtime change.
- GREEN: `node tools\reader-harness\src\run-reader-harness.mjs pagination-profile-logic` passed.
- GREEN: `node --check` passed for `navic-reader-pagination.js` and `navic-reader-page-turns.js`.
- GREEN: `git diff --check` passed.
- LIMITATION: this was host/harness verified only. The installed emulator APK still contains the prior packaged JS, so live `visible-page-content` proof requires a rebuild/install or release candidate.

## 2026-06-19 Emulator Current-Screen Check: History Capsule Over Live Text

Scope:
- User reported the current emulator screen looked weird, with no text and only a popup.
- Did not relaunch the app and did not touch Bindery-backed flows.
- Captured and probed the already-running `darkaxt.navic.readerdev` reader state.

Validation:

```powershell
adb devices
adb -s emulator-5554 shell screencap -p /sdcard/navic-current.png
adb -s emulator-5554 pull /sdcard/navic-current.png captures\reader-status\20260619-221701\screen.png
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe visible-page-content --local-port 9276
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe page-box --local-port 9277
adb -s emulator-5554 logcat -d -t 300
```

Results:
- PASS: foreground device is `emulator-5554`.
- PASS: screenshot `captures/reader-status/20260619-221701/screen.png` shows prose text rendering on the page.
- PASS: `page-box` reports a live reader WebView at `1232x1974`, `rendererRect=1232x1974`, `contentDocument=true`, and `bodyTextLength=242809`.
- PASS: `visible-page-content` reports `rendererPage=44`, `rendererPages=47`, visible section content, and a visible prose leaf `"A world without Shadow."`.
- PASS: recent logcat did not show a Navic reader crash or WebView exception around the capture.
- FAIL/OPEN: the bottom-center arrow/X popup is visible over the content. This is the native history capsule, not a crash dialog.

Interpretation:
- The current observed state is not a blank WebView. The reader is loaded and text is visible.
- The confusing popup is caused by the PushState/history capability route: `ReaderController` currently makes `engineNavigation.visible = event.canGoBack || event.canGoForward` whenever Foliate reports history availability.
- This makes the history capsule appear as a side effect of navigation state, instead of a deliberate user action. That behavior is intrusive and should be redesigned against the Komikku reference before being treated as accepted UI.

Follow-up fix:
- Changed `ReaderController` so `NavigationStateChanged` still records `canGoBack` and `canGoForward`, but leaves `engineNavigation.visible=false`.
- This prevents the arrow/X history capsule from appearing just because Foliate history is available.
- The existing history back/forward command path is still retained at controller level.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.pushStateUpdatesHistoryCapabilitiesWithoutShowingNativeCapsuleByDefault
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeRoutesAnxPushStateToKomikkuHistoryCapsule
git diff --check
```

Results:
- RED: `ReaderControllerTest.pushStateUpdatesHistoryCapabilitiesWithoutShowingNativeCapsuleByDefault` failed before the controller change because PushState made the capsule visible.
- GREEN: the focused controller test passed after the controller change.
- GREEN: `ReaderRuntimeCommonChromeTest.commonReaderChromeRoutesAnxPushStateToKomikkuHistoryCapsule` passed.
- GREEN: `git diff --check` passed.
- LIMITATION: this is host-verified only. The currently running emulator still has the old installed APK state until rebuilt/reinstalled.

## 2026-06-19 Eta76 Selection Action Validation and WebView Toolbar Clash

Scope:
- Followed the active spec's Phase 5 validation priority.
- Did not relaunch, reinstall, or call Bindery because the server may be under maintenance.
- Used the already-running emulator reader state for package `darkaxt.navic.readerdev`, version `v1.0.11-eta76`, `lastUpdateTime=2026-06-19 17:51:16`.

Validation:

```powershell
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ExpectedVersionName v1.0.11-eta76 -LongPressFraction '0.45,0.46,1200,1800' -CaptureReaderDiagnostics -RequireNativeLongTap -RequireReaderBridgeEvent selectionChanged -ArtifactDir captures\reader-selection\20260619-223237
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ExpectedVersionName v1.0.11-eta76 -Tap '955,190,1200' -CaptureReaderDiagnostics -RequireReaderLog 'Reader selection copied length=' -ArtifactDir captures\reader-selection\20260619-223446-copy
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted
```

Results:
- PASS: `captures/reader-selection/20260619-223237/screen.png` shows the native selection action strip with `Highlight`, `Copy`, and `Note`.
- PASS: logcat captured `Reader native long tap`.
- PASS: logcat captured `Reader bridge event: selectionChanged(footnote=false, ...)` with selected text `"head"`, CFI, context text, and position.
- PASS: tapping the native Copy action captured `Reader selection copied length=4`.
- FAIL/OPEN on eta76: `captures/reader-selection/20260619-223446-copy/screen.png` shows Android WebView's own system selection toolbar (`Copy`, `Share`, `Select all`, `Read aloud`) in addition to Navic's native selection action strip.
- Interpretation: the controller-owned selection path works, but the Android WebView still owns native long-click selection UI on the installed eta76 build. This violates the Komikku-controller boundary because WebView should render and emit events, not own reader action chrome.

Follow-up source fix:
- Added a host guard in `ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted`.
- Changed `ReaderEngineWebViewHost.android.kt` to set `isLongClickable=false` and consume `setOnLongClickListener`, logging that the native frame owns selection actions.
- RED: the focused host guard failed before the WebView long-click suppression existed.
- GREEN: the focused host guard passed after the WebView host change.
- LIMITATION: the fix has not been installed on the emulator yet. Rebuild/reinstall later, then rerun the same selection smoke and confirm the system WebView toolbar no longer appears while the native Highlight/Copy/Note strip still appears.

Follow-up emulator observation:

```powershell
adb -s emulator-5554 exec-out screencap -p
adb -s emulator-5554 shell dumpsys activity activities
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev
adb -s emulator-5554 logcat -d -v time -t 600
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe visible-page-content --local-port 9281
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe page-box --local-port 9282
```

Results:
- PASS: `captures/reader-weird-screen/20260619-225556/screen.png` shows the page text is still rendered; the screen is not a blank WebView.
- PASS: installed emulator build is still `darkaxt.navic.readerdev` `v1.0.11-eta76`, `lastUpdateTime=2026-06-19 17:51:16`.
- PASS: `visible-page-content` reports `rendererPage=44`, `rendererPages=47`, `visibleTextLength=235916`, visible item text beginning `CHAPTER 37 The Last Battle`, and a visible prose leaf `"A world without Shadow."`.
- PASS: `page-box` reports a live `1232x1974` renderer, `bodyTextLength=242809`, `maxColumnCount=1`, `topMargin=90px`, and `bottomMargin=50px`.
- PASS: the prior Highlight action dispatched `applyHighlights(count=4)` and emitted `annotationDrawn(value=epubcfi(/6/98!/4/2/2702,/1:65,/1:69))`.
- FAIL/OPEN on eta76: the screenshot still shows Android WebView's system selection toolbar and the bottom history capsule. These are expected on the installed eta76 APK because the source fixes for WebView long-click suppression and hidden-by-default history capsule have not been rebuilt/reinstalled yet.
- Interpretation: the current emulator visual defect is not a rendering failure. It is the already-known stale-installed-build overlay stack: controller selection strip + WebView system toolbar + history capsule.

Additional current-screen check:

```powershell
adb devices
adb shell dumpsys window
adb shell dumpsys package darkaxt.navic.readerdev
adb shell screencap -p /sdcard/navic_reader_current.png
adb pull /sdcard/navic_reader_current.png captures\reader-weird-screen\20260619-230711\screen.png
node tools\reader-harness\src\adb-webview-eval.mjs --probe visible-page-content
```

Results:
- PASS: `captures/reader-weird-screen/20260619-230711/screen.png` shows the same overlay stack while page text is still rendered.
- PASS: current focus is `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS: installed emulator build remains `v1.0.11-eta76`, `lastUpdateTime=2026-06-19 17:51:16`.
- PASS: `visible-page-content` reports `rendererPage=44`, `rendererPages=47`, `visibleTextLength=235916`, and visible prose text. The renderer is not empty.
- FAIL/OPEN on eta76: Android WebView's system selection toolbar remains visible with Navic's native selection strip because the newer WebView long-click suppression source fix is not installed on this emulator build.
- FAIL/OPEN on eta76: the bottom arrow/X history capsule remains visible because the newer hidden-by-default history capsule source fix is not installed on this emulator build.

## 2026-06-19 Font-Size Prose Scaling Guard

Scope:
- Investigated the reported behavior where the Font size control appears to affect chapter titles but not ebook prose.
- Used the already-running emulator reader state to avoid Bindery calls during server maintenance.

Evidence:
- The settings dialog control is wired to `ReaderSettings.fontSizePercent`, not `headingFontSize`.
- The controller route is `onSettingsChange -> applyReaderSettings -> coordinator.applySettings -> controller.applySettings -> ReaderEngineCommand.ApplySettings`.
- Runtime CSS applies `--reader-content-font-size` on the content document root and prose blocks inherit `1rem`.

Validation:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size
node tools\reader-harness\src\adb-webview-eval.mjs --probe font-size-publisher-styles
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperFontSizeProbeFailsWhenExistingProseDoesNotScale
node --check tools\reader-harness\src\adb-webview-eval.mjs
```

Results:
- PASS: direct runtime Font size dispatch on the current emulator page changes synthetic paragraph, body, root, and existing EPUB prose from `16px` to `22.4px`.
- PASS: the strengthened `font-size` DevTools probe now fails if existing book prose does not scale with reader Font size. It no longer only trusts synthetic paragraph scaling.
- PASS: live probe reported `existingProseDelta=6.4px` across visible `P` and `BLOCKQUOTE` elements.
- PASS: publisher-style probe also scaled an inline `font-size: 12px` paragraph from `16px` to `22.4px`.
- PASS: focused host guard passed after strengthening the probe.
- PASS: helper syntax passed `node --check`.
- OPEN: this does not yet prove the exact Tab S9/Hobbit state reported by the user. It proves the current emulator page and validation helper catch real-prose scaling regressions.

## 2026-06-19 Emulator Weird-Screen Recheck

Scope:
- Rechecked the emulator after the user reported that the current screen looked empty except for a popup.
- Did not reinstall or relaunch because Bindery maintenance may make relaunch/download unreliable, and the current reader instance is useful evidence.

Validation:

```powershell
adb devices
adb shell dumpsys package darkaxt.navic.readerdev
adb shell dumpsys window
adb exec-out screencap -p
node tools\reader-harness\src\adb-webview-eval.mjs --probe visible-page-content
```

Results:
- PASS: emulator focus is still `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS: installed emulator build remains `v1.0.11-eta76`, `lastUpdateTime=2026-06-19 17:51:16`.
- PASS: `captures/reader-weird-screen/latest/screen.png` shows rendered text plus three overlapping chrome layers: Navic selection actions, Android/WebView's system selection toolbar, and the old arrow/X history capsule.
- PASS: `visible-page-content` reports `rendererPage=44`, `rendererPages=47`, `visibleTextLength=235916`, and visible prose text. The renderer is not blank.
- FAIL/OPEN on eta76: WebView's own selection toolbar is still visible. Current source commit `18a708ed` suppresses that toolbar, but this fix is not installed in the emulator.
- FAIL/OPEN on eta76: the bottom arrow/X history capsule is still visible. Current source commit `3c9945e0` hides that capsule by default, but this fix is not installed in the emulator.
- Interpretation: the weird screen is stale installed overlay behavior, not a fresh no-text rendering regression. The next clean validation requires rebuilding/reinstalling a new APK after the user/server environment is ready.

## 2026-06-19 Emulator Weird-Screen Recheck 2

Scope:
- Rechecked the emulator after another report that the screen looked wrong, with no text and only a popup.
- Kept the current running reader instance alive because Bindery may be unavailable during server maintenance.

Validation:

```powershell
adb devices
adb shell dumpsys window
adb shell dumpsys package darkaxt.navic.readerdev
adb shell screencap -p /sdcard/navic_current_screen.png
adb pull /sdcard/navic_current_screen.png captures\emulator-current-screen\screen.png
node tools\reader-harness\src\adb-webview-eval.mjs --probe visible-page-content
```

Results:
- PASS: emulator focus is `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS: installed emulator build remains `v1.0.11-eta76`, `lastUpdateTime=2026-06-19 17:51:16`.
- PASS: `captures\emulator-current-screen\screen.png` shows the page text is rendered, but the UI is cluttered by three overlapping surfaces: Navic's selection action strip, Android/WebView's native text-selection toolbar, and the old bottom arrow/X history capsule.
- PASS: DevTools `visible-page-content` reports `rendererPage=44`, `rendererPages=47`, `visibleTextLength=235916`, and visible text beginning `CHAPTER 37 The Last Battle`.
- FAIL/OPEN on eta76: Android/WebView's native selection toolbar is still visible. Current source commit `18a708ed` suppresses this WebView chrome, but that source is not installed in eta76.
- FAIL/OPEN on eta76: the bottom arrow/X history capsule is still visible. Current source commit `3c9945e0` hides it by default, but that source is not installed in eta76.
- Interpretation: this is still stale-installed-build overlay collision, not an empty-renderer failure. The current source should be rebuilt/reinstalled before retesting this specific UI state.

## 2026-06-19 Reader Mark Persistence Slice

Scope:
- Closed the controller-store gap where highlights, notes, and bookmarks existed only in `ReaderControllerState` for the current reader process.
- This keeps Anx-style annotation/bookmark capability behind Navic controller/store ownership instead of treating WebView events as the durable source of truth.

Changes:
- Added `ReaderMarksPreference.kt` to decode/encode `ReaderAnnotationState` and `ReaderBookmarkState` through the existing `PreferenceManager.readerAnnotationsJson` and `PreferenceManager.readerBookmarksJson` fields.
- `ReaderScreen` now seeds controller annotations/bookmarks from preferences on reader entry.
- `ReaderScreen` now persists annotations/bookmarks only when a coordinator transition changes the controller store.
- Added `ReaderMarksPreferenceTest` for preference round-trip and transition persistence.
- Added a source guard proving `ReaderScreen` does not initialize marks as transient empty stores.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "*ReaderMarksPreferenceTest*"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --rerun-tasks --tests "*ReaderMarksPreferenceTest*" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderMarksAreSeededAndPersistedOutsideTheWebView" --tests "*ReaderAnnotationStateTest*" --tests "*ReaderBookmarkStateTest*"
git diff --check
```

Results:
- PASS: focused `ReaderMarksPreferenceTest` completed with `BUILD SUCCESSFUL in 26s`.
- PASS: uncached affected reader host run completed with `BUILD SUCCESSFUL in 3m 23s`.
- PASS: the uncached run executed the new preference tests, source guard, and existing annotation/bookmark state tests.
- PASS: `git diff --check` reported no whitespace errors.
- NOTE: Kotlin still printed its existing daemon temp-file `AccessDeniedException` after successful execution and fell back to non-daemon compilation. The Gradle exit code was 0.

## 2026-06-19 Post-Rebase Reader Validation

Scope:
- Rebasing local reader work onto the updated `fork/master` introduced conflicts in `App.kt` and `ReaderRoot.kt`.
- Resolved `App.kt` by keeping the global sideload popup removal while retaining snackbar wiring.
- Resolved `ReaderRoot.kt` by keeping the full-window native `Box`, overlay visibility gating, and logger from the chrome overlay fix while retaining the current vertical page-drag preview API.

Validation:

```powershell
git diff --check
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "*ReaderMarksPreferenceTest*" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderMarksAreSeededAndPersistedOutsideTheWebView" --tests "*ReaderKomikkuBackboneResetTest*"
```

Results:
- PASS: `git diff --check` reported no whitespace errors before rebase continuation.
- PASS: post-rebase focused reader host run completed with `BUILD SUCCESSFUL in 7m 34s`.
- PASS: the focused run covered the reader mark persistence test, the source guard for seeding/persisting marks outside WebView, and the Komikku backbone reset guards touched by `ReaderRoot.kt`.
- NOTE: Kotlin again printed the existing daemon temp-file `AccessDeniedException` after successful execution and fell back to non-daemon compilation. The Gradle exit code was 0.

## 2026-06-19 Emulator Weird-Screen Recheck 3

Scope:
- Rechecked the current emulator report that the reader looked blank with only a popup.
- Avoided Bindery navigation because server maintenance may interrupt remote book loading.

Validation:

```powershell
adb devices
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev
adb -s emulator-5554 shell dumpsys window
adb -s emulator-5554 shell screencap -p /sdcard/navic_current.png
adb -s emulator-5554 pull /sdcard/navic_current.png captures\emulator-current-screen\screen.png
node tools\reader-harness\src\adb-webview-eval.mjs --probe visible-page-content
adb -s emulator-5554 shell uiautomator dump /sdcard/navic_ui.xml
adb -s emulator-5554 pull /sdcard/navic_ui.xml captures\emulator-current-screen\ui.xml
```

Results:
- PASS: emulator focus is `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS: installed emulator build is still `v1.0.11-eta76`, `lastUpdateTime=2026-06-19 17:51:16`.
- PASS: `captures\emulator-current-screen\screen.png` shows rendered prose text behind the overlays.
- PASS: DevTools `visible-page-content` reports `rendererPage=44`, `rendererPages=47`, `visibleTextLength=235916`, and visible text beginning `CHAPTER 37 The Last Battle`.
- PASS: UI hierarchy contains the text nodes and confirms the active overlay labels `Highlight`, `Copy`, `Note`, plus Android/WebView's native `Copy`, `Share`, `Select all`, and `Read aloud`.
- FAIL/OPEN on eta76: the installed build still has overlapping native selection chrome and the old arrow/X history capsule. Current source commits `771c6f23` and `c50b665e` address these two pieces, but they are not installed in this emulator build.
- Interpretation: this is a stale installed-build overlay collision, not a blank EPUB renderer. Retest after rebuilding/reinstalling a newer APK before treating this as an active source regression.

## 2026-06-20 Saved Marks Contents Slice

Scope:
- Closed the reader-discoverability gap for saved highlights, notes, and bookmarks.
- The contents sheet no longer exposes only the table of contents; it now has `Contents`, `Bookmarks`, and `Notes` tabs.
- Saved marks are filtered to the current book in `ReaderRoot` and navigate through `ReaderCoordinator` / `ReaderController`, not through WebView-owned marker side effects.

Changes:
- Added `ReaderBookmark.toLocator()` trimming and `ReaderAnnotation.toLocator()` conversion for saved-mark navigation.
- Added `ReaderController.navigateToBookmark` and `ReaderController.navigateToAnnotation`, both producing `ReaderEngineCommand.NavigateTo`.
- Added `ReaderCoordinator` wrappers for bookmark and annotation navigation.
- Expanded `KomikkuReaderContentsDialog` with tabs and saved-mark rows.
- Wired `ReaderScreen` to route saved-mark rows through coordinator-owned navigation.
- Added a source guard so the contents dialog cannot regress back to TOC-only saved marks.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.savedBookmarkNavigationIsControllerOwned" --tests "paige.navic.reader.ReaderControllerTest.savedAnnotationNavigationIsControllerOwned"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.savedBookmarkNavigationIsControllerOwned" --tests "paige.navic.reader.ReaderControllerTest.savedAnnotationNavigationIsControllerOwned" --tests "paige.navic.reader.ReaderViewerTest.readerContentsDialogSurfacesSavedMarksThroughControllerRoutes"
git diff --check
```

Results:
- PASS: focused controller tests completed with `BUILD SUCCESSFUL in 6m 37s`.
- PASS: focused controller plus reader-shell source guard completed with `BUILD SUCCESSFUL in 1m 46s`.
- PASS: `git diff --check` reported no whitespace errors.
- NOTE: Kotlin still printed its existing daemon temp-file `AccessDeniedException` after successful execution and fell back to non-daemon compilation. The Gradle exit code was 0.
- OPEN: emulator/device validation was not rerun for this slice because the ADB emulator transport stopped responding after the stale eta76 overlay diagnosis.

## 2026-06-20 Emulator ADB Transport Blocker

Scope:
- Tried to continue emulator validation after the saved-marks contents slice.
- Checked whether the previously stale eta76 emulator state could be recaptured and reprobed.

Validation:

```powershell
adb devices
adb -s emulator-5554 shell screencap -p /sdcard/navic_current_after_marks.png
adb kill-server
adb start-server
adb devices
adb -s emulator-5554 shell echo ok
adb reconnect offline
adb devices
adb -s emulator-5554 shell echo ok
```

Results:
- PASS: initial `adb devices` listed `emulator-5554 device`.
- FAIL/BLOCKED: `adb -s emulator-5554 shell screencap -p /sdcard/navic_current_after_marks.png` did not return.
- FAIL/BLOCKED: after restarting the ADB server, `emulator-5554` moved to `offline`.
- FAIL/BLOCKED: `adb reconnect offline` did not restore shell access; follow-up shell command reported the device offline/not found.
- Interpretation: emulator validation is blocked by the emulator/ADB transport. Do not treat the current emulator screen as app evidence until the emulator reconnects as an online device with working shell access.

## 2026-06-20 EPUB Font-Size Table Wrapper Slice

Scope:
- Investigated the Tab S9 report that the font-size control affected chapter titles but not body prose.
- The likely EPUB shape is table-cell or old centered/table-like prose wrappers with fixed publisher `font-size`.
- Extended the renderer guard so table-cell prose must be reset to the reader root font size.

Changes:
- Added a `font-css-smoke` harness probe for table-cell body text.
- Added a JVM source guard requiring `readerTypographyCss` to include `td` in the root-size reset path.
- Added `td`, `th`, `main`, `section`, `article`, and `center` to the block prose reset selector.
- Added descendant resets for spans/fonts under those wrapper elements.

Validation:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs font-css-smoke
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node tools\reader-harness\src\run-reader-harness.mjs adaptive-page-box-logic
git diff --check
```

Results:
- BLOCKED: browser-backed `font-css-smoke` could not launch Chromium in this session: Playwright reported `browserType.launch: spawn EPERM`. No permission escalation was requested.
- RED: before the CSS fix, the focused JVM guard failed at `ReaderRuntimeSettingsBridgeTest.kt:366`, proving `td` was missing from the typography reset.
- PASS: after the CSS fix, the same focused JVM guard completed with `BUILD SUCCESSFUL in 12s`.
- PASS: `node --check` on `navic-reader-helpers.js` reported no syntax errors.
- PASS: pure `adaptive-page-box-logic` harness passed, so the Tab S9 page-box math was not regressed by this font-size slice.
- PASS: `git diff --check` reported no whitespace errors.

## 2026-06-20 Emulator Popup / Blank Screen Report

Scope:
- User reported the current emulator screen looked wrong: no text, only a popup.
- Attempted to collect ADB evidence before diagnosing the reader state.

Validation:

```powershell
adb devices -l
adb start-server
adb kill-server
adb connect 127.0.0.1:5555
adb connect localhost:5555
adb nodaemon server
```

Results:
- FAIL/BLOCKED: repeated `adb devices -l` calls started the daemon but returned no attached emulator or phone.
- FAIL/BLOCKED: `adb kill-server` reported no reachable daemon at `127.0.0.1:5037`.
- FAIL/BLOCKED: explicit reconnect attempts to `127.0.0.1:5555` / `localhost:5555` did not attach the running emulator.
- FAIL/BLOCKED: one reconnect attempt reported `could not read ok from ADB Server`.
- FAIL/BLOCKED: desktop screenshot fallback failed with Windows `CopyFromScreen` reporting `The handle is invalid`.
- PASS: Windows process inspection showed `emulator.exe` and `qemu-system-x86_64.exe` still running.
- Interpretation: the current emulator screen is not usable validation evidence from this session. ADB is detached/broken and the window could not be captured visually. Restart or reattach the emulator before treating the popup/blank screen as a Navic regression.

## 2026-06-20 EPUB Adaptive Prose Width Slice

Scope:
- Investigated the Tab S9 report that EPUB pages can render as a narrow text column with too much unused horizontal space.
- The existing adaptive page-box math already produced a wide tablet folio surface, so the stronger source-level suspect was publisher CSS pinning body/prose wrappers to fixed `width` or `max-width`.

Changes:
- Added a host guard requiring the EPUB runtime stylesheet to override publisher `width` / `max-width` on body and prose wrappers.
- Updated `readerTypographyCss` so body and prose containers yield to the adaptive Foliate page box.
- Added a prose-table rule so image-free table wrappers used by older EPUBs can fill the available folio width.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderLetsProseUseAdaptiveFolioWidth"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderLetsProseUseAdaptiveFolioWidth"
node tools\reader-harness\src\run-reader-harness.mjs adaptive-page-box-logic
git diff --check
```

Results:
- RED: before the CSS change, `androidReaderLetsProseUseAdaptiveFolioWidth` failed at `ReaderRuntimeSettingsBridgeTest.kt:385`, proving prose width normalization was missing.
- PASS: after the CSS change, the focused prose-width guard completed with `BUILD SUCCESSFUL in 12s`.
- PASS: `node --check` on `navic-reader-helpers.js` reported no syntax errors.
- PASS: the adjacent font-size/prose-width focused host tests completed with `BUILD SUCCESSFUL in 26s`.
- PASS: pure `adaptive-page-box-logic` harness still passed, so the page-box sizing model was not regressed.
- PASS: `git diff --check` reported no whitespace errors.
- OPEN: runtime visual confirmation on emulator/phone is still blocked until ADB reattaches or a clean release is installed and inspected.

## 2026-06-20 Emulator Popup Follow-Up / Font-Size Root-Cause Pass

Scope:
- User reported the emulator currently shows no ebook text and only a popup.
- User also reported the reader font-size control appears to affect chapter titles while body text stays effectively unchanged.

Validation:

```powershell
adb devices -l
adb connect 127.0.0.1:5555
Get-Process | Where-Object { $_.ProcessName -match 'emulator|qemu|adb|java' }
Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'emulator|qemu|adb' }
rg -n "STOP, READ|STOP READ|Stop, read|dev popup|readerdev|ReaderDev|READ!|STOP!" composeApp androidApp docs scripts tools
rg -n "STOP|READ|read|stop" composeApp\src\commonMain\composeResources composeApp\src\androidMain\res androidApp\src\main\res
```

Results:
- FAIL/BLOCKED: `adb devices -l` still starts the daemon but returns no attached emulator or phone.
- FAIL/BLOCKED: direct `adb connect 127.0.0.1:5555` returned connection refused in this session.
- PASS/BLOCKED-CONTEXT: process listing intermittently shows `emulator.exe` / `qemu-system-x86_64.exe`, but the QEMU PID is not consistently inspectable.
- FAIL/BLOCKED: `Get-CimInstance Win32_Process` returned `Access denied`, so the emulator command line / AVD identity could not be verified from this shell.
- FAIL/BLOCKED: recursive temp log discovery hit protected temp folders and did not produce usable emulator log evidence.
- PASS: source and resource searches did not find a literal app-owned `STOP, READ!` dialog string in the current tree; the known references are validation-log/spec references and readerdev tooling references.
- Root-cause note for font size: `readerContentCss` injects `--reader-content-font-size` and `readerTypographyCss` resets body/prose to `1rem`, which should scale through the `html` root. If runtime body text does not scale, likely causes are injected-style ordering, an uncovered publisher prose wrapper, or perceived shrinkage from wider adaptive line lengths rather than a missing settings bridge.
- OPEN: no runtime conclusion about the popup or font-size visual behavior is valid until ADB reattaches or a clean release/device check is run.

## 2026-06-20 Typewriter / Preformatted Prose Font-Size Slice

Scope:
- User reported that font-size controls affect headings while ebook body text can remain effectively unchanged.
- The Tab S9 screenshots show typewriter-like EPUB body text, which can be represented by publisher `pre`, `code`, `samp`, or `kbd` wrappers instead of normal paragraph tags.

Changes:
- Added a host guard for preformatted/typewriter prose coverage in `readerTypographyCss`.
- Added root-size reset coverage for `pre` and body-level `code` / `samp` / `kbd` wrappers.
- Added inherited-size coverage for nested `span`, `font`, `code`, `samp`, and `kbd` inside preformatted/prose blocks.
- Added `pre-wrap` / `overflow-wrap` to keep preformatted body prose inside the adaptive folio page box.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlScalesPreformattedTypewriterProse"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlScalesPreformattedTypewriterProse" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderLetsProseUseAdaptiveFolioWidth"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
```

Results:
- RED: before the CSS change, `androidReaderFontSizeControlScalesPreformattedTypewriterProse` failed at `ReaderRuntimeSettingsBridgeTest.kt:385`, proving preformatted/typewriter prose was not covered.
- PASS: after the CSS change, the focused guard completed with `BUILD SUCCESSFUL in 11s`.
- PASS: adjacent font-size/prose-width focused host guards completed with `BUILD SUCCESSFUL in 23s`.
- PASS: `node --check` on `navic-reader-helpers.js` returned exit code 0.
- OPEN: runtime visual confirmation is still blocked until ADB reattaches or the next clean release/device validation run.

## 2026-06-20 History Capsule / Popup Guard Drift

Scope:
- User reiterated that the emulator screen looked wrong with no text and only a popup.
- Rechecked ADB and the current source before making any runtime claim.
- The likely "arrow and X" popup is the native `KomikkuReaderHistoryCapsule`, but current HEAD should not show it automatically from Foliate PushState events.

Validation:

```powershell
adb devices -l
rg -n "ReaderHistoryCapsule|engineNavigation|navigateHistory|History" composeApp\src\commonMain composeApp\src\commonTest composeApp\src\androidHostTest
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.pushStateUpdatesHistoryCapabilitiesWithoutShowingNativeCapsuleByDefault"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded" --tests "paige.navic.reader.ReaderControllerTest.pushStateUpdatesHistoryCapabilitiesWithoutShowingNativeCapsuleByDefault"
```

Results:
- FAIL/BLOCKED: `adb devices -l` still starts the daemon but returns no attached emulator or phone, so no runtime screenshot or touch validation is valid from this session.
- PASS: source inspection shows `ReaderController` handles `NavigationStateChanged` by setting `engineNavigation.visible = false`.
- PASS: the dedicated PushState controller guard passed before edits, proving current production code already keeps the capsule hidden by default.
- RED: the broader Anx bridge event smoke test failed at `ReaderControllerTest.kt:210` because it still expected `ReaderEngineNavigationState(... visible = true)`, contradicting the dedicated guard and the current product decision.
- FIX: updated the stale test assertion to expect `visible = false`.
- PASS: the combined focused host run completed with `BUILD SUCCESSFUL in 1m 49s`.
- NOTE: the passing Gradle run still printed the known Kotlin daemon temp-file `AccessDeniedException`, then fell back to non-daemon compilation and returned exit code 0.
- OPEN: if the popup is still visible in the emulator, it is not proven against current HEAD. Reattach ADB or install a clean release before treating it as an active runtime regression.

## 2026-06-20 Pagination Profile Stale-Href / Spine Authority Slice

Scope:
- Investigated the report that chapter rail navigation could remain in the previous section after jumping through chapter navigation, e.g. "Chapter 14" being treated like page 2 of an earlier area and back navigation returning to cover/foreword incorrectly.
- The native rail math was already clamping chapter-local endpoints correctly; the stronger runtime suspect was relocation payload disagreement where `href` still points at the previous section while `spineIndex` points at the current section.

Changes:
- Added a browser-harness regression case where `href` still matches `OEBPS/Text/Hobbit_chap-13.html` but `spineIndex = 16` identifies Chapter XIV.
- Updated `readerPaginationPositionForLocator` so a current finite `spineIndex` outranks `href`, and `href` is only a fallback when spine lookup is unavailable.
- Split typography-specific reader helpers into `navic-reader-typography.js` so the focused support-module guard stays meaningful after the recent EPUB layout work.

Validation:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs pagination-profile-logic
node tools\reader-harness\src\run-reader-harness.mjs adaptive-page-box-logic
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination-model.js
node --check tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeAssetsTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderUsesStableLocationTotalsForReflowablePageNumbers" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderSkipsTrailingBlankFoliateColumnForChapterEndpoints"
adb devices -l
git diff --check
```

Results:
- RED: before the spine-first change, the new harness guard failed with `expected current spine index to outrank a stale but matching previous href`, proving the model could resolve the previous chapter when the stale `href` still matched.
- PASS: after the fix, `pagination-profile-logic` passed.
- PASS: `adaptive-page-box-logic` still passed after moving typography helpers into their own module.
- PASS: JavaScript syntax checks passed for `navic-reader-helpers.js`, `navic-reader-typography.js`, `navic-reader-pagination-model.js`, and `run-reader-harness.mjs`.
- PASS: `navic-reader-helpers.js` is now 743 lines, below the 1200-line focused-module guard.
- PASS: focused Android host tests completed with `BUILD SUCCESSFUL in 32s`.
- NOTE: the host Gradle run still printed the known Kotlin daemon temp-file `AccessDeniedException`, fell back to non-daemon compilation, and returned exit code 0.
- FAIL/BLOCKED: `adb devices -l` still starts the daemon but returns no attached emulator or phone, so this slice has not been runtime-validated on the current emulator screen.
- PASS: `git diff --check` reported no whitespace errors.

## 2026-06-20 Emulator Popup / ADB Transport Recheck

Scope:
- User reported the current emulator screen looks wrong: no ebook text, only a popup.
- User also reiterated that no approval/escalation requests are acceptable in this session.

Validation:

```powershell
adb devices -l
Get-Process | Where-Object { $_.ProcessName -match 'emulator|qemu|adb|studio|java' } | Select-Object ProcessName,Id,Path
netstat -ano | findstr ":555"
adb start-server
adb connect 127.0.0.1:5555
Test-NetConnection 127.0.0.1 -Port 5554
Test-NetConnection 127.0.0.1 -Port 5555
rg -n "KomikkuReader.*(Dialog|Popup|Capsule|Actions)|Reader.*Dialog|HistoryCapsule|SelectionActions|ExternalLinkDialog|AnnotationDialog|FootnoteDialog|SearchDialog|Note|Highlight" composeApp\src\commonMain\kotlin\paige\navic\ui\screens\reader composeApp\src\commonMain\kotlin\paige\navic\reader composeApp\src\commonTest composeApp\src\androidHostTest
rg -n "PaginationProfile|StatusBadge|paginationProfile|measuring|failed" composeApp\src\commonMain\kotlin\paige\navic\ui\screens\reader composeApp\src\commonMain\kotlin\paige\navic\reader composeApp\src\commonTest composeApp\src\androidHostTest
```

Results:
- FAIL/BLOCKED: `adb devices -l` repeatedly started the ADB daemon but returned no attached emulator or phone.
- FAIL/BLOCKED: starting `adb nodaemon server` as a hidden background process did not produce a stable server for `adb devices`; subsequent ADB calls still restarted and exited the daemon.
- FAIL/BLOCKED: `adb connect 127.0.0.1:5555` returned connection refused.
- PASS/PARTIAL: Windows process listing shows `emulator.exe` and `qemu-system-x86_64.exe` running for the local emulator.
- PASS/PARTIAL: `netstat -ano | findstr ":555"` initially showed `127.0.0.1:5554` and `127.0.0.1:5555` listening for the QEMU PID.
- PASS/PARTIAL: `Test-NetConnection 127.0.0.1 -Port 5554` succeeded, so the emulator console endpoint is reachable.
- FAIL/BLOCKED: `Test-NetConnection 127.0.0.1 -Port 5555` failed in the later check, so the ADB transport endpoint is not currently usable from this shell.
- FAIL/BLOCKED: process command-line and TCP listener inspection through privileged APIs returned `Access denied`; no escalation was requested.
- FAIL/BLOCKED: a complex emulator-console auth probe hit a local `CreateProcessAsUserW failed: 5` runner error; no escalation was requested.
- SOURCE DIAGNOSIS: current HEAD can show overlays without menu input from `selectionActions`, `annotationPopup`, `footnotePopup`, `externalLinkPrompt`, `engineNavigation`, or `paginationProfile.status`.
- SOURCE DIAGNOSIS: `ReaderController` now forces `engineNavigation.visible = false` on `NavigationStateChanged`, so the arrow/X history capsule should not appear automatically from PushState in current source.
- SOURCE DIAGNOSIS: `KomikkuPaginationProfileStatusBadge` is visible while `paginationProfile.status` is `measuring` or `failed`; if the WebView content is blank while profile measurement is active/failed, the user-facing symptom can look like "blank paper plus one popup".
- OPEN: no runtime screenshot, logcat, or touch validation was possible in this session because ADB transport did not attach.

## 2026-06-20 Selection Action Cleanup Slice

Scope:
- User reported that long-press selection actions show a native Highlight/Copy/Note popup, but Highlight did not appear to do anything.
- Source investigation found that successful Highlight and Note Save applied annotations but left `ReaderControllerState.selection` alive, so the native selection action strip could remain visible over the result.

Changes:
- `ReaderController.addSelectionHighlight` now clears controller-owned selection after a new annotation is successfully added.
- `ReaderController.saveSelectionNote` now clears controller-owned selection and the note draft after a note annotation is successfully added.
- Controller tests now assert that Highlight and Note Save return `ReaderSelectionActionState()` after success.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.selectionHighlightsAreControllerOwnedAndForwardedAsEngineCapabilities" --tests "paige.navic.reader.ReaderControllerTest.selectionNotesSaveAsAnnotationsAndClearDraft"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderCoordinatorTest.selectionHighlightsRouteThroughControllerAndCurrentEngineAdapter" --tests "paige.navic.reader.ReaderCoordinatorTest.selectionNotesSaveRouteThroughControllerAndCurrentEngineAdapter"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted"
git diff --check
```

Results:
- RED: before the controller change, both focused controller tests failed because `selection` remained non-null after Highlight/Note Save.
- NOTE: the first fresh controller verification attempt was cut off by the shell tool's default command cap before Gradle finished; the command was rerun to completion instead of treating the partial output as evidence.
- PASS: after the controller change, the focused controller tests completed with `BUILD SUCCESSFUL in 10s` on the fresh rerun.
- PASS: adjacent coordinator route tests completed with `BUILD SUCCESSFUL in 10s`, proving `ApplyAnnotations` still reaches the active engine adapter.
- PASS: the source-inspection route guard `ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted` completed with `BUILD SUCCESSFUL in 25s`.
- PASS: `git diff --check` reported no whitespace errors.
- NOTE: the long controller test run printed the known Kotlin daemon temp-file `AccessDeniedException`, then fell back to non-daemon compilation and returned exit code 0.
- OPEN: device/emulator visual validation is still blocked until ADB transport reattaches; this slice is host-verified only.

## 2026-06-20 Selection Note Overlay Ownership Slice

Scope:
- Continued the selection-action lifecycle audit after the cleanup slice.
- Root cause hypothesis: starting a Note from the native selection action strip copied the selection into a draft, but left the live controller selection intact. Because `selectionActions` is derived from `selection`, this allowed the selection action strip and the note dialog to be visible at the same time.

Changes:
- `ReaderController.startSelectionNote` now clears the live `selection` after creating `ReaderSelectionNoteDraft`.
- `ReaderControllerTest.selectionNotesStartNativeDraftWithoutEngineCommands` now asserts that starting a note leaves `ReaderSelectionActionState()` and a null live selection, while still emitting no engine/WebView command.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.selectionNotesStartNativeDraftWithoutEngineCommands"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.selectionHighlightsAreControllerOwnedAndForwardedAsEngineCapabilities" --tests "paige.navic.reader.ReaderControllerTest.selectionNotesStartNativeDraftWithoutEngineCommands" --tests "paige.navic.reader.ReaderControllerTest.selectionNotesSaveAsAnnotationsAndClearDraft"
```

Results:
- NOTE: the first two attempts to add the RED assertion landed in unrelated tests with generic `assertEquals(emptyList(), step.engineCommands)` endings. Those passing runs were discarded as invalid evidence.
- RED: after anchoring the assertion to `selectionNotesStartNativeDraftWithoutEngineCommands`, the focused test failed at `ReaderControllerTest.kt:1355` because `selection` remained non-null after `startSelectionNote`.
- PASS: after clearing `selection` in `startSelectionNote`, the focused test completed with `BUILD SUCCESSFUL in 5m 18s`.
- PASS: adjacent controller tests for Highlight, Note Start, and Note Save completed with `BUILD SUCCESSFUL in 24s`.
- NOTE: Gradle still printed the known Kotlin daemon temp-file `AccessDeniedException`, then fell back to non-daemon compilation and returned the reported exit codes.
- OPEN: device/emulator visual validation is still blocked until ADB transport reattaches; this slice is host-verified only.

## 2026-06-20 ReaderDev Popup / Notes Reopen Source Recheck

Scope:
- Rechecked the user-requested global removal of the `STOP, READ!`/sideloading interruption.
- Rechecked whether saved notes have a source-level route for later discovery and reopening.

Validation:

```powershell
rg -n "STOP, READ|STOP READ|Stop, read|READ!|STOP!|sideload|ReaderDev|readerdev|dev popup|debug popup" composeApp androidApp docs scripts tools
rg -n "sideload|STOP|readerdev" composeApp\src\androidHostTest composeApp\src\commonTest
adb devices -l
```

Results:
- PASS/SOURCE: current app source search did not find an app-owned `STOP, READ!` dialog path; remaining hits are readerdev tooling, specs/logs, and the guard test.
- PASS/SOURCE: `ReaderDevEnvironmentContractTest` still asserts that the sideloading modal is globally removed and cannot block readerdev validation.
- PASS/SOURCE: saved notes are listed through `KomikkuReaderContentsDialog` under `ReaderContentsTab.Notes`, and `KomikkuReaderAnnotationDialog` renders note text when annotation taps resolve a saved note.
- FAIL/BLOCKED: `adb devices -l` still started ADB but returned no attached devices, so no runtime screenshot/logcat validation was possible.

## 2026-06-20 Selection Copy Dismissal Slice

Scope:
- Continued the selection-action lifecycle audit after Highlight and Note ownership cleanup.
- Root cause hypothesis: Copy wrote the selected text to the clipboard, but did not clear controller-owned `selection`, so the native action strip could remain visible after the one-shot action completed.

Changes:
- Added `ReaderController.dismissSelectionActions()` to clear live selection without emitting engine/WebView commands.
- Added `ReaderCoordinator.dismissSelectionActions()` as the only UI route into that controller action.
- `ReaderScreen` now calls `coordinator.dismissSelectionActions()` after the copy callback writes to `LocalClipboardManager`.
- Source guard now requires the copy path to route through the coordinator instead of leaving the selection overlay alive.

Validation:

```powershell
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.selectionActionsDismissAfterCopyWithoutEngineCommands"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted"
```

Results:
- RED: before adding the controller/coordinator method, the new focused controller test failed to compile with `Unresolved reference 'dismissSelectionActions'`, proving the dismissal route did not exist.
- INVALID/DISCARDED: a parallel Gradle verification attempt failed with Kotlin build-output deletion collisions under `composeApp\build`; this was a process error from running host tests concurrently, not valid product evidence.
- PASS: after adding the controller/coordinator route and UI callback, `ReaderControllerTest.selectionActionsDismissAfterCopyWithoutEngineCommands` completed with `BUILD SUCCESSFUL in 5m 29s`.
- PASS: the source-inspection route guard `ReaderRuntimeCommonChromeTest.commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted` completed sequentially with `BUILD SUCCESSFUL in 19s`.
- NOTE: Gradle still printed the known Kotlin daemon temp-file `AccessDeniedException` during the long controller test, then fell back to non-daemon compilation and returned exit code 0.
- OPEN: device/emulator visual validation is still blocked until ADB transport reattaches; this slice is host-verified only.

## 2026-06-20 Inline Publisher Typography Normalization Slice

Scope:
- Investigated the Tab S9 Ultra report where the Font size setting appeared to scale chapter titles but not the ebook body text.
- Root cause hypothesis: the existing host probes covered publisher stylesheet rules, but not PDF-converted / fixed-layout-derived EPUB prose that carries inline `font-size` declarations with `!important`. Those inline declarations outrank the reader stylesheet, so headings can repaint while prose remains pinned.

Changes:
- Added an inline-important publisher body-text probe to the font CSS harness.
- Added a source guard requiring document-level inline typography normalization and the `applyDocumentTheme` call route.
- Added `normalizeReaderInlineTypography(doc, settings)` in the reader typography module. It detects prose-like inline font-size ownership, preserves original publisher values in `data-navic-*` attributes, removes legacy `font[size]`, and rewrites only font-size to reader-relative `1rem`/`1em` with `!important`.
- Wired the normalizer into `applyDocumentTheme` after the full reader stylesheet is injected and before chapter-opening margin normalization.
- Corrected the stale chapter-opening margin source guard to read the concatenated reader runtime text instead of the `navic-reader-helpers.js` barrel file.

Validation:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes"
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeAssetsTest.readerHarnessFontCssSmokeCoversInlineImportantPublisherProse" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanReadRendererPageBoxWithoutMutatingContent" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperIncludesPublisherStyleFontSizeProbe" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperWaitsForFontSizeReflowBeforeMeasuring" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest.androidReaderUsesAdaptiveFoliatePageBoxForLargeEpubViewports"
```

Results:
- BLOCKED: the Playwright font CSS harness did not reach the RED assertion because Chromium failed to launch with `browserType.launch: spawn EPERM`. Per the session rule, no escalation was requested; this remains a local tooling limitation.
- RED: the focused source guard failed at `ReaderRuntimeSettingsBridgeTest.kt:379` because `normalizeReaderInlineTypography` and its `applyDocumentTheme` route did not exist.
- PASS: after implementing and wiring the normalizer, the focused source guard completed with `BUILD SUCCESSFUL in 15s`.
- PASS: `node --check` completed with exit code 0 for both touched reader JS modules.
- PASS: the full `ReaderRuntimeSettingsBridgeTest` class completed with `BUILD SUCCESSFUL in 25s` after correcting the stale barrel-file source guard.
- PASS: adjacent source guards for the browser harness inline-important probe, page-box diagnostics, publisher font-size probes, font-size reflow waiting, and adaptive Foliate page boxes completed with `BUILD SUCCESSFUL in 44s`.
- NOTE: Gradle still printed the known Kotlin daemon temp-file `AccessDeniedException` in one run, then fell back to non-daemon compilation and returned exit code 0.
- OPEN: `adb devices -l` still reported no attached device earlier in the session, so the Tab S9 Ultra visual behavior and emulator blank/popup report are not runtime-validated by this slice.

## 2026-06-20 Emulator Transport Recovery Attempt

Scope:
- Investigated why the required emulator gate could not be run after the reader typography slice.
- Separated Codex command-routing behavior from Android emulator runtime state.

Validation:

```powershell
Write-Output "shell-ok"
git status --short
Start-Process powershell.exe -WindowStyle Hidden -RedirectStandardOutput tmp\codex-startprocess-probe.out ...
emulator.exe -version
adb devices -l
netstat -ano | findstr 555
Start-Process emulator.exe -ArgumentList @('-avd','NavicReaderLab','-port','5560','-read-only','-no-snapshot-load','-no-boot-anim','-verbose') -RedirectStandardOutput tmp\emulator-start.out -RedirectStandardError tmp\emulator-start.err ...
```

Results:
- PASS/TOOLING: ordinary shell commands, `git status`, hidden `Start-Process` with redirected output, and `emulator.exe -version` all ran successfully after the refreshed unrestricted session context.
- DIAGNOSIS: the earlier `exec command rejected by user` was a Codex/tool-routing state issue around the previous command classification, not an emulator binary or Windows admin-permission failure. After the refreshed context, the same command class no longer rejected.
- FAIL/ADB: `adb devices -l` starts the daemon but reports no attached devices.
- FAIL/EMULATOR: `netstat` shows stale QEMU PID `86952` listening on `127.0.0.1:5554` and `127.0.0.1:5555`, while ADB does not see an emulator transport.
- FAIL/RELAUNCH: launching `NavicReaderLab` on port `5560` with `-read-only` exits. The captured emulator log reports `Another emulator instance is running. Please close it or run all emulators with -read-only flag.`
- FAIL/RECOVERY: Windows process termination paths previously returned `Access denied`/stale PID races for the orphaned QEMU process, and the emulator console kill attempt failed with `Access is denied`.
- OPEN: emulator validation remains unavailable until the orphaned QEMU process is cleared outside this session or the Android emulator/Windows session is restarted.

## 2026-06-20 Emulator Transport Restart Recovery

Scope:
- Restarted the local `NavicReaderLab` emulator so runtime validation can resume after several host-only reader fixes.
- Avoided permission escalation and visible console popups.

Validation:

```powershell
adb kill-server
Stop-Process -Id 86952 -Force
taskkill /PID 86952 /T /F
wmic process where ProcessId=86952 call terminate
emulator.exe -list-avds
Start-Process emulator.exe -ArgumentList @('-avd','NavicReaderLab','-no-snapshot-load','-no-boot-anim','-verbose') ...
adb devices -l
adb -s emulator-5554 shell getprop sys.boot_completed
adb -s emulator-5554 shell wm size
adb -s emulator-5554 shell wm density
adb -s emulator-5554 shell screencap -p /sdcard/navic_emulator_restarted.png
adb -s emulator-5554 pull /sdcard/navic_emulator_restarted.png captures\emulator-restart\navic_emulator_restarted.png
adb -s emulator-5554 shell pm list packages
```

Results:
- PASS/RECOVERY: `wmic process where ProcessId=86952 call terminate` returned `ReturnValue = 0` and cleared the orphaned QEMU listener that had owned `127.0.0.1:5554/5555`.
- PASS/RECOVERY: `emulator.exe -list-avds` now reports `NavicReaderLab`.
- PASS/RECOVERY: `NavicReaderLab` relaunched as `emulator.exe` PID `116312` and `qemu-system-x86_64.exe` PID `117000`.
- PASS/ADB: `adb devices -l` reports `emulator-5554 device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64`.
- PASS/BOOT: `adb -s emulator-5554 shell getprop sys.boot_completed` returned `1`.
- PASS/SCREENSHOT: `adb screencap` and `adb pull` succeeded; latest capture is `captures\emulator-restart\navic_emulator_restarted.png`.
- PASS/PACKAGES: emulator has both `darkaxt.navic` and `darkaxt.navic.readerdev` installed.
- CURRENT STATE: emulator is focused on the Nexus launcher, not the reader. Runtime reader validation can resume by launching the appropriate package.

## 2026-06-20 Blind-Fix Validation Pass

Scope:
- Revalidated recent reader fixes that had been implemented mostly through source/host checks.
- Covered inline publisher typography normalization, pagination-profile logic, adaptive folio page-box logic, Anx parity route guards, and current ADB transport status.

Validation:

```powershell
node --check composeApp\src\androidMain\assets\reader\*.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs pagination-profile-logic
node tools\reader-harness\src\run-reader-harness.mjs adaptive-page-box-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
adb devices -l
netstat -ano | findstr ":555"
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest
.\gradlew.bat --no-daemon "-Dkotlin.compiler.execution.strategy=in-process" :composeApp:testAndroidHostTest --tests "paige.navic.reader.FoliateAnxParityTest.existsEntriesForAnxReaderBehaviorHaveVerifiedControllerOrUiRoutes"
git diff --check
```

Results:
- PASS: all reader JavaScript assets and `tools\reader-harness\src\run-reader-harness.mjs` passed `node --check`.
- PASS: `pagination-profile-logic` passed.
- PASS: `adaptive-page-box-logic` passed.
- PASS: browser-backed `font-css-smoke` passed in this session, including the inline-important publisher prose probe from the typography-normalization slice.
- FAIL/FOUND: the broad host suite initially failed `FoliateAnxParityTest.existsEntriesForAnxReaderBehaviorHaveVerifiedControllerOrUiRoutes`.
- ROOT CAUSE: the `onPushState` Anx parity registry still pointed to the removed `pushStateShowsNativeHistoryCapsuleAndRoutesHistoryCommandsThroughEngine` test name. Current behavior intentionally keeps the history capsule hidden by default to avoid the unwanted arrow/X popup while preserving history capabilities and commands.
- FIX: the parity registry now points at `pushStateUpdatesHistoryCapabilitiesWithoutShowingNativeCapsuleByDefault` and explicitly requires the controller route to contain `visible = false`.
- PASS: the focused Anx parity guard completed with `BUILD SUCCESSFUL in 38s` after the registry correction.
- PASS: the full `:composeApp:testAndroidHostTest` suite completed with `BUILD SUCCESSFUL in 33s` after the registry correction.
- PASS/ADB: after emulator recovery, `adb devices -l` reports `emulator-5554 device`.
- OPEN: full runtime reader behavior validation is not yet rerun on the fresh emulator; the emulator is online and ready for that next gate.

## 2026-06-20 eta76 Readerdev Runtime Matrix

Scope:
- Rebuilt and installed the current `readerDev` variant on the recovered `NavicReaderLab` emulator.
- Validated that the emulator path can launch a real Bindery EPUB without the old empty/popup-only state.
- Ran the Komikku reader matrix against the installed readerdev package.

Validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -IncludeCoverChecks -ContinueOnFailure -ArtifactRoot tmp\readerdev-runtime-validation-current\matrix-artifacts
node tools\reader-harness\src\run-reader-harness.mjs pagination-profile-logic
node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination.js
node --check tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest
```

Results:
- PASS/INSTALL: `:androidApp:assembleReaderDev` completed with `BUILD SUCCESSFUL in 5m 24s`; install returned `Success`.
- PASS/LAUNCH: `darkaxt.navic.readerdev` focused `paige.navic.androidApp.MainActivity` and emitted `publicationReady`.
- PASS/CAPTURE: screenshot pulled to `captures\reader-dev\reader-dev-20260620-071239.png`; it shows the loaded EPUB cover for `A Memory of Light`.
- PASS/MATRIX: baseline reader, native cover, cover center tap, cover drag next, center tap toggle, native long press, edge tap next, drag next, edge tap previous, drag previous, and texture previous walk ran.
- FAIL/MATRIX: `texture-next-walk` failed because `reader-texture-diagnostics.log` contained `surface-texture-update` entries but no moved `surface-texture-scroll` samples with `delta=`/`dir=`.
- ROOT CAUSE: `texture-next-walk` advanced page labels from `3/388` through `14/388` while `href=OEBPS/Text/TitlePage-01.xhtml`, `rangeCfi=epubcfi(/6/10!/4/2/2,,/2)`, and `chapterPageCount=1` stayed unchanged. `committedPageTurnPosition` was synthesizing `currentPageIndex + 1` for a one-page pagination-profile section even though Foliate had not moved the renderer.
- FIX: `committedPageTurnPosition` now trusts pagination-profile candidates for one-page sections instead of synthesizing additional global pages.
- PASS/TDD: `pagination-profile-logic` first failed with `expected page-turn override to leave a one-page section candidate unchanged; got {"pageIndex":5,...}` and passed after the fix.
- PASS: `node --check` passed for `navic-reader-pagination.js` and `run-reader-harness.mjs`.
- PASS: focused `ReaderRuntimeNavigationFlowTest` completed with `BUILD SUCCESSFUL in 31s`.
- OPEN: rebuild/reinstall readerdev and rerun the Komikku matrix to confirm the runtime `texture-next-walk` failure is fixed on the emulator.

## 2026-06-20 eta76 Adjacent Section Runtime Fix

Scope:
- Continued the eta76 emulator validation after the one-page pagination-profile fix.
- Targeted the remaining `texture-next-walk` failure without adding another blind texture workaround.
- Verified the fix against the recovered visible `NavicReaderLab` emulator.

Validation:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs pagination-profile-logic
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-location.js
node --check tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -IncludeCoverChecks -ContinueOnFailure -ArtifactRoot tmp\readerdev-runtime-validation-after-adjacent-fallback\matrix-artifacts
```

Results:
- FAIL/TDD: the new harness check initially failed with `handleDuplicatePageTurnRelocation helper is not exported`.
- ROOT CAUSE: after the one-page page-label fix, real emulator logs showed native right taps dispatching `nextPage`, but Foliate emitted a duplicate `page-turn:next` relocation for the same frontmatter section. The previous code suppressed the duplicate and never crossed into the next readable section.
- FIX: added `handleDuplicatePageTurnRelocation` in `navic-reader-page-turns.js` and called it from the duplicate-location branch in `navic-reader-location.js`. For `page-turn:*` duplicates only, it moves to `adjacentReadableSectionIndex(direction)` with a `page-turn:<direction>:adjacent` controlled-relocation reason.
- PASS/TDD: `pagination-profile-logic` now passes and logs `page-turn:duplicate-adjacent-fallback next from=4 to=5` in the synthetic regression.
- PASS: `node --check` passed for `navic-reader-page-turns.js`, `navic-reader-location.js`, and `run-reader-harness.mjs`.
- PASS: focused `ReaderRuntimeNavigationFlowTest` completed with `BUILD SUCCESSFUL in 13s`.
- PASS/INSTALL: readerdev reinstalled on `emulator-5554`; `publicationReady` was observed and screenshot pulled to `captures\reader-dev\reader-dev-20260620-074555.png`.
- PASS/MATRIX: `scripts\adb-reader-komikku-matrix.ps1` completed with exit code `0` for baseline reader, native cover, cover center tap, cover drag next, center tap toggle, native long press, edge taps, drag next/previous, and texture next/previous walks.
- PASS/RUNTIME: `texture-next-walk` diagnostics now include `textureScrollLines=7`, `textureUpdateLines=12`, `textureHasDelta=True`, `textureHasDirection=True`, `textureDirectionSamples=7`, and `wrongTextureDirection=False`.
- PASS/RUNTIME: `texture-previous-walk` diagnostics now include `textureScrollLines=7`, `textureUpdateLines=4`, `textureHasDelta=True`, `textureHasDirection=True`, `textureDirectionSamples=7`, and `wrongTextureDirection=False`.
- PASS/RUNTIME: emulator logs show the actual boundary fallback firing and posting real bridge locations, for example `page-turn:duplicate-adjacent-fallback next from=4 to=5` followed by `locationChanged(... reason=page-turn:next:adjacent ...)`.
- CURRENT STATE: the recovered emulator is usable, `darkaxt.navic.readerdev` is installed at `versionName=v1.0.11-eta76`, and the documented runtime matrix is green for this slice.

## 2026-06-20 eta76 Selection Action Runtime Validation

Scope:
- Followed the implementation-order gate for Phase 5 selection actions.
- Validated the existing installed `darkaxt.navic.readerdev` eta76 package on `emulator-5554`.
- Used a DevTools-created WebView selection to reach deterministic native UI state, then used Android UI taps for Highlight, Copy, and Note. This closes dirty-emulator action-path evidence, not clean release/manual selection evidence.

Validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Highlight,1500' -RequireReaderEngineCommand 'applyHighlights(count=1, notes=0)' -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-selection-actions\highlight
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Copy,1500' -RequireReaderLog 'Reader selection copied length=31' -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-selection-actions\copy
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Note,1500' -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-selection-actions\note-open
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe selection-payload -PostProbeAction 'tapText:Note,700|tapText:Annotation,500|text:Probe note,500|tapText:Save,1800' -RequireReaderLog 'Reader selection note save length=10' -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-selection-actions\note-save
```

Results:
- PASS/PAYLOAD: all probes captured `selectionChanged(footnote=true, ...)` from the Anx-style selection payload path.
- PASS/HIGHLIGHT: `tmp\readerdev-selection-actions\highlight\logcat-reader.log` captured `Dispatching reader engine command: applyHighlights(count=1, notes=0)` and `Reader bridge event: annotationDrawn(...)`.
- PASS/COPY: `tmp\readerdev-selection-actions\copy\logcat-reader.log` captured `Reader selection copied length=31` and the selection overlay dismissed.
- PASS/NOTE-OPEN: `tmp\readerdev-selection-actions\note-open\window.xml` shows the native `Note` dialog with selected text, `Annotation` input, `Cancel`, and disabled `Save`.
- PASS/NOTE-SAVE: `tmp\readerdev-selection-actions\note-save\logcat-reader.log` captured `Reader selection note save length=10`, `Dispatching reader engine command: applyHighlights(count=2, notes=1)`, and two `annotationDrawn` bridge events. Count was `2` because the same dirty emulator book state already contained the previous Highlight validation annotation; the relevant note evidence is `notes=1`.
- TOOLING NOTE: one Highlight assertion attempt used an invalid comma-joined bridge-event parameter and one Copy run exceeded the tool default output window, but both generated complete artifacts. The artifact logs above are the authoritative evidence.
- OPEN: clean release APK validation and a fully user-driven/manual text-selection path are still required before Phase 5 is release-ready.

## 2026-06-20 eta76 Chrome Tap-Zone Suppression Validation

Scope:
- Investigated the visible-chrome regression where tapping the right-side chapter rail/top controls could leak through the overlay and dispatch a native `RIGHT` page tap.
- Added a host guard before the production patch, then validated the patched Android native frame on the visible `NavicReaderLab` emulator.
- This is a dirty `readerDev` emulator validation of the native host behavior, not a release APK validation.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest.visibleReaderChromeBlocksNativeEdgeTapZonesButKeepsCenterMenuToggle
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe chapter-progress-endpoints -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-progress-rail\after-fix-restore-chapter37
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -PostProbeAction "tapDescFraction:Chapter page slider,0.5,0.0,1500" -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-progress-rail\ui-slider-start-after-fix
```

Results:
- FAIL/TDD: the new focused guard initially failed at `ReaderRuntimeShellProgressTest.kt:392`, proving the code had no `chromeOverlayVisible` native-host route and no suppression before `dispatchSingleTapAction(action)`.
- ROOT CAUSE: `KomikkuReaderNativeViewerContainer` receives taps after child dispatch, but it did not know whether the Compose chrome was visible. With the menu/rail open, a tap on the rail area still computed `KomikkuNavigationRegion.RIGHT` and dispatched `nextPage` behind the overlay.
- FIX: `KomikkuReaderNativeFrameHost` now receives `chromeOverlayVisible = controllerState.menuVisible`. Android stores that on the native viewer container and suppresses non-`MENU` native tap-zone actions while chrome is visible, logging `Reader native tap ignored under chrome action=...`. Center `MENU` taps still dispatch so the user can close the menu.
- PASS/TDD: the focused guard passed after the patch.
- PASS/HOST: `ReaderRuntimeShellProgressTest`, `ReaderRuntimeCommonChromeTest`, and `ReaderKomikkuBackboneResetTest` completed with `BUILD SUCCESSFUL in 26s`.
- PASS/INSTALL: readerdev rebuilt and installed on `emulator-5554`; install returned `Success`, foreground was confirmed, and `publicationReady` was observed.
- PASS/ENGINE: the `chapter-progress-endpoints` probe reached `OEBPS/Text/Chapter-37.xhtml` with `chapterPageCount=44` and endpoints `chapterPageIndex=0` and `chapterPageIndex=43`.
- FAIL/BEFORE-FIX BEHAVIOR: the same visible rail tap had previously logged `Reader native tap action=RIGHT x=1800.0 y=306.0` followed by `Dispatching reader engine command: nextPage` and a jump into `OEBPS/Text/Chapter-38.xhtml`.
- PASS/RUNTIME: after the patch, `tmp\readerdev-progress-rail\ui-slider-start-after-fix\logcat-reader.log` contains `Reader native tap ignored under chrome action=RIGHT x=1800.0 y=306.0 width=1848 height=2960` and contains no `nextPage` dispatch for that tap.
- PASS/RUNTIME: center tap still logs `Reader native tap action=MENU`, `Reader viewer action=Menu menuVisible=true->false shellCover=false->false`, and `Reader chrome overlay visible=false menu=false`.
- OPEN: this patch prevents wrong native page turns behind visible chrome. It does not yet prove every rail control has ideal direct-manipulation behavior; dedicated rail seek/drag UX validation remains separate.

## 2026-06-20 eta76 Visible Emulator Rail Coordinate Validation

Scope:
- Used the recovered visible `NavicReaderLab` emulator after the user confirmed the emulator window was visible again.
- Validated the installed dirty `darkaxt.navic.readerdev` eta76 package without rebuilding.
- Focused on whether the side chapter rail and chapter buttons consume real coordinate taps, because the previous `tapDescFraction:Chapter page slider,0.5,0.0` action had hit the top of the semantics region and was not proof of real rail behavior.

Validation:

```powershell
adb -s emulator-5554 shell screencap -p /sdcard/navic-current.png
adb -s emulator-5554 shell input tap 924 1480
adb -s emulator-5554 shell input tap 1800 1480
adb -s emulator-5554 shell input tap 1800 350
adb -s emulator-5554 shell input tap 1800 2600
adb -s emulator-5554 shell input tap 1800 215
adb -s emulator-5554 shell input tap 1800 2760
```

Results:
- PASS/STATE: emulator was focused on `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`, installed version was `v1.0.11-eta76`, and the captured reader page showed `A Memory of Light`, Chapter 37, page `333 / 388`.
- PASS/CENTER TAP: tapping the page center logged `Reader native tap action=MENU x=924.0 y=1480.0 width=1848 height=2960` and `Reader viewer action=Menu menuVisible=false->true shellCover=false->false`.
- PASS/RAIL MIDPOINT: tapping the visible rail track at `x=1800 y=1480` dispatched `goToChapterProgress(OEBPS/Text/Chapter-37.xhtml, 0.5116279069767442)` and relocated to `chapterPageIndex=23`, `chapterPageCount=44`, `reason=chapter-progress-seek`.
- PASS/RAIL START: tapping the visible rail track near the top at `x=1800 y=350` dispatched `goToChapterProgress(..., 0.023255813953488372)` on Chapter 37 and relocated near the start of that chapter. On Chapter 36, the same top coordinate dispatched `goToChapterProgress(..., 0.0)` and relocated to `chapterPageIndex=0`, `chapterPageCount=5`.
- PASS/RAIL END: tapping the visible rail track near the bottom at `x=1800 y=2600` dispatched `goToChapterProgress(..., 1.0)`. Chapter 37 returned `chapterPageIndex=43`, `chapterPageCount=44`; Chapter 36 returned `chapterPageIndex=4`, `chapterPageCount=5`. The displayed rail maps these zero-based runtime indices to the visible final page.
- PASS/CHAPTER BUTTONS: tapping the top vertical chapter button at `x=1800 y=215` dispatched `goToHref(OEBPS/Text/Chapter-36.xhtml)` and relocated to Chapter 36. Tapping the bottom vertical chapter button at `x=1800 y=2760` dispatched `goToHref(OEBPS/Text/Chapter-37.xhtml)` and relocated back to Chapter 37.
- ROOT CAUSE CLARIFICATION: the earlier bad `tapDescFraction:Chapter page slider,0.5,0.0` check was not a valid proof that the rail itself failed; it targeted the top of the merged semantics box, where the native host correctly suppresses page-zone passthrough while chrome is visible. Real coordinate taps on the visible rail track and buttons are consumed by Compose and reach the reader engine.
- OPEN: this does not close clean release validation on the user's physical phone, nor does it validate rail drag feel. It does close the dirty-emulator evidence for direct coordinate taps on rail middle/start/end and vertical chapter arrows.

## 2026-06-20 eta76 Dirty Emulator Resume Persistence Fallback Validation

Scope:
- Investigated the remaining Priority 0 resume-persistence gap on the visible `NavicReaderLab` emulator.
- Used installed `darkaxt.navic.readerdev` eta76 to reproduce the failure, then installed a dirty readerdev build with the local-progress fallback patch.
- This validates cached EPUB resume after forced relaunch on emulator. It is not clean release APK validation on the user's physical phone.

Baseline:
- Local prefs contained `readerReadingProgressJson` for book `3709`, resource `/opds/books/3709/resources/ebook-08aea0220318ac157c5f`, href `OEBPS/Text/Chapter-37.xhtml`, CFI `epubcfi(/6/98!/4/2,/1406,/1480/1:227)`, progress `0.8186814367781696`.
- The cached EPUB existed under `reader-publications/reader-d3048625266b3f885c38ab36/publication.epub`.

Validation:

```powershell
adb -s emulator-5554 exec-out run-as darkaxt.navic.readerdev cat /data/user/0/darkaxt.navic.readerdev/shared_prefs/darkaxt.navic.readerdev_preferences.xml > tmp\readerdev-resume-validation\readerdev_preferences.xml
adb -s emulator-5554 shell am start -S -n darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity [...Bindery OPDS/API extras from bindery-debug.env omitted...] --es navic.dev.reader.publication_url "[BINDERY_OPDS_BASE_URL]/opds/books/3709/resources/ebook-08aea0220318ac157c5f" --es navic.dev.reader.book_id 3709 --es navic.dev.reader.resource_href /opds/books/3709/resources/ebook-08aea0220318ac157c5f --es navic.dev.reader.kind ebook --es navic.dev.reader.format epub
.\gradlew.bat --no-daemon :composeApp:testAndroid
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -DeviceSerial emulator-5554 -NoDiscoverPublication -NoLaunch
adb -s emulator-5554 shell am start -S -n darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity [...Bindery OPDS/API extras from bindery-debug.env omitted...] --es navic.dev.reader.publication_url "[BINDERY_OPDS_BASE_URL]/opds/books/3709/resources/ebook-08aea0220318ac157c5f" --es navic.dev.reader.book_id 3709 --es navic.dev.reader.resource_href /opds/books/3709/resources/ebook-08aea0220318ac157c5f --es navic.dev.reader.kind ebook --es navic.dev.reader.format epub
adb -s emulator-5554 shell input tap 1200 1400
adb -s emulator-5554 shell input tap 1700 1400
```

Results:
- FAIL/BEFORE: relaunching the cached EPUB without an explicit start locator prepared the publication with `cache=hit`, but the first `locationChanged` was `OEBPS/Text/sinopsis.xhtml`, `pageIndex=0`, `progress=0.0012596938509564985`. The visual capture showed the shell cover/start path.
- ROOT CAUSE: `ReaderScreen` persisted `readerReadingProgressJson`, and `ReaderReadingProgressState.startLocatorFor(...)` existed, but `ReaderScreen` only passed remote `savedProgress` from `ReaderPublicationRuntimeHost` into `toReaderEngineOpenRequest(...)`. If Bindery progress was unavailable, cached EPUB relaunch had no local fallback and opened at the start.
- FIX: `Screen.Reader.toReaderEngineOpenRequest(...)` now accepts `localStartLocator`; `ReaderScreen` decodes `preferenceManager.readerReadingProgressJson`, resolves `ReaderReadingProgressState.startLocatorFor(bookId, resourceHref, kind)`, and passes it into the open request. The factory ranks route locator, remote saved progress, and local fallback through `bestReaderStartLocator(...)`.
- PASS/TDD: the new `ReaderOpenRequestFactoryTest.openRequestUsesLocalProgressWhenBinderySavedProgressIsUnavailable` guard failed before production changes because `localStartLocator` was missing, then passed after the patch.
- PASS/HOST: `:composeApp:testAndroid` completed and the `testAndroidHostTest` XML summary reported `1490` tests, `0` failures, `0` errors.
- PASS/INSTALL: dirty readerdev rebuilt and installed successfully on `emulator-5554`.
- PASS/RUNTIME: after reinstall and forced relaunch, publication preparation again reported `cache=hit`, then the first runtime document load was `loadDoc index=48 sectionId=OEBPS/Text/Chapter-37.xhtml`.
- PASS/RUNTIME: first `locationChanged` after the fix reported href `OEBPS/Text/Chapter-37.xhtml`, CFI `epubcfi(/6/98!/4/2,/1406,/1480/1:227)`, progress `0.8186814367781696`, `chapterPageIndex=23`, `chapterPageCount=44`, and page model `page=313/388`.
- PASS/VISUAL: the shell cover still appears first by design. After hiding chrome and tapping the right page zone, logs showed `shellCover=true->false`, and the screenshot rendered the saved Chapter 37 text page with visible page label `314 / 388`.
- OPEN: clean release/physical-device validation is still required. The shell-cover-first behavior remains intentional for now, but should be retested with the user's expected cover interaction flow.

## 2026-06-20 eta76 Visible Emulator Restart Validation

Scope:
- Verified that the recovered visible Android emulator window is usable for reader validation before continuing implementation.
- Used the installed dirty `darkaxt.navic.readerdev` eta76 package without rebuilding or publishing a release.
- Ran the documented Komikku reader matrix against the current foreground reader page with `-NoLaunch -ContinueOnFailure`.

Validation:

```powershell
adb devices
adb -s emulator-5554 shell dumpsys window | Select-String -Pattern 'mCurrentFocus|mFocusedApp'
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev | Select-String -Pattern 'versionName|versionCode'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ContinueOnFailure -ArtifactRoot .\tmp\readerdev-visible-window-matrix-20260620
```

Results:
- PASS/DEVICE: `adb devices` listed `emulator-5554 device`.
- PASS/FOCUS: Android focus was `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS/VERSION: installed reader-dev package was `versionName=v1.0.11-eta76`, `versionCode=409`.
- PASS/BASELINE: baseline capture rendered readable EPUB text, not a blank/loading screen, with visible page label `314 / 388`.
- PASS/MATRIX: `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` passed.
- PASS/CENTER TAP: logcat showed `Reader native tap action=MENU`, `Reader viewer action=Menu menuVisible=false->true`, then another center tap returned `menuVisible=true->false`.
- PASS/DRAG: `drag-next` and `drag-previous` diagnostics reported `readerNativeDragPreview=True` and `readerNativeDragCandidate=True`.
- PASS/TEXTURE DIRECTION: both drag and tap texture probes reported `wrongTextureDirection=False`.
- EXPECTED/STARTING STATE: `enter-readable-content` failed because the reader was already in readable content rather than on the native shell cover, so no shell-cover swipe could be captured.
- ARTIFACTS: `tmp\readerdev-visible-window-matrix-20260620`.
- OPEN: this is dirty-emulator evidence, not clean release or physical-device proof. Shell-cover entry/drag still needs a run that starts on the cover.

## 2026-06-20 eta76 Boundary Drag Preview Underlay Validation

Scope:
- Fixed the EPUB section-boundary drag-preview gap where the current page could drag over an unloaded adjacent section and expose a black/blank void.
- Kept native Komikku-style gesture ownership intact: the native drag preview remains authoritative, and the Foliate renderer is not scrolled until the adjacent preview surface is ready.
- Used a real cached EPUB pulled from readerdev, independent of Bindery availability.

Changes:
- Added pending drag-preview replay after adjacent iframe readiness.
- Hid not-ready boundary previews with `opacity=0`, `width=1px`, and `left=-1px` instead of exposing an unloaded surface.
- Added `page-drag-preview:underlay-waiting` tracing before adjacent section readiness.
- Hardened the content-layout logger against incomplete Foliate documents.
- Strengthened the `epub-native-drag-preview-underlay` harness so it rejects exposed blank underlays.

Validation:

```powershell
adb -s emulator-5554 exec-out run-as darkaxt.navic.readerdev cat cache/reader/reader-publications/reader-d3048625266b3f885c38ab36/publication.epub > .\tmp\reader-fixtures\publication.epub
node .\tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-preview-underlay --fixture .\tmp\reader-fixtures\publication.epub
node --check .\composeApp\src\androidMain\assets\reader\navic-reader.js
node --check .\composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
node --check .\tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroid --rerun-tasks
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ContinueOnFailure -ArtifactRoot .\tmp\readerdev-boundary-preview-matrix-20260620
```

Results:
- PASS/HARNESS: the first strengthened harness run failed red with the pre-fix behavior because the boundary preview exposed `ready=false`, `opacity=1`.
- PASS/HARNESS: after the runtime fix, the real EPUB harness passed with trace order `page-drag-preview:underlay-waiting` -> `page-drag-preview:underlay-loaded` -> `page-drag-preview:underlay` with `ready=true`.
- PASS/HARNESS: final adjacent iframe metrics were nonblank: `iframeTextLength=101`, `iframeBodyHeight=425.453125`.
- PASS/SYNTAX: `node --check` passed for the changed runtime and harness files.
- PASS/HOST: `:composeApp:testAndroid --rerun-tasks` exceeded the shell capture window, but the Gradle wrapper PID was waited and JUnit XML summary reported `1490` tests, `0` failures, `0` errors, `0` skipped.
- PASS/INSTALL: dirty readerdev rebuilt, installed on `emulator-5554`, launched `darkaxt.navic.readerdev`, and logged `publicationReady`.
- PASS/EMULATOR: the Komikku reader matrix completed successfully; center tap toggled menu, native long press worked, edge taps worked, drag next/previous reported `readerNativeDragPreview=True`, and texture probes reported `wrongTextureDirection=False`.
- ARTIFACTS: `tmp\readerdev-boundary-preview-matrix-20260620`.
- OPEN: this proves the dirty emulator boundary-preview behavior only. It does not claim clean physical release validation, and it does not implement full page-curl visuals.

## 2026-06-20 eta76 Matrix Already-Readable Start Validation

Scope:
- Removed a false-red validation path from the Komikku reader matrix when the reader is already in readable EPUB content.
- Explicit cover validation remains strict under `-IncludeCoverChecks`; the default `enter-readable-content` step is now a best-effort swipe so it does not fail a valid already-readable state.

Validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ContinueOnFailure -ArtifactRoot .\tmp\readerdev-post-boundary-followup-matrix-20260620
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ContinueOnFailure -ArtifactRoot .\tmp\readerdev-post-boundary-followup-matrix-after-script-fix-20260620
```

Results:
- RED/BEFORE: the first run failed only `enter-readable-content` with `no shell-cover swipe was captured`, while logs showed `shellCover=false` and the other content steps passed.
- ROOT CAUSE: the default matrix path required shell-cover swipe/command diagnostics even when `-IncludeCoverChecks` was not requested.
- FIX: removed the strict shell-cover assertions from the default `enter-readable-content` step. Cover-specific assertions still run in the explicit `-IncludeCoverChecks` branch.
- PASS/AFTER: the same already-readable emulator state completed the full default matrix with `No matrix failures`.
- PASS/AFTER: `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` all passed on `darkaxt.navic.readerdev` `v1.0.11-eta76`, `versionCode=409`, `lastUpdateTime=2026-06-20 10:21:29`.
- PASS/DIAGNOSTICS: `drag-next` and `drag-previous` reported `readerNativeDragPreview=True`, `readerNativeDragCandidate=True`, and `wrongTextureDirection=False`.
- ARTIFACTS: `tmp\readerdev-post-boundary-followup-matrix-after-script-fix-20260620`.
- OPEN: this is a validation-tooling fix only. It does not replace an explicit cover-start run with `-IncludeCoverChecks`.

## 2026-06-20 eta76 Android WebView Layout And Font Probe Validation

Scope:
- Runtime-validated the recent page-box and inline publisher typography fixes on the visible emulator through the actual reader WebView DevTools target.
- This targets the Tab S9/tablet-layout and font-size reports at the engine surface rather than relying only on source guards.

Validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe page-box -CaptureReaderDiagnostics -ArtifactDir .\tmp\readerdev-layout-probes-20260620\page-box
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ReaderDevtoolsProbe font-size-publisher-styles -CaptureReaderDiagnostics -ArtifactDir .\tmp\readerdev-layout-probes-20260620\font-size-publisher-styles
```

Results:
- PASS/PAGE-BOX: the probe attached to `webview_devtools_remote_10300` for `darkaxt.navic.readerdev` `v1.0.11-eta76`, `versionCode=409`.
- PASS/PAGE-BOX: viewport, `foliate-view`, and renderer rects were all `1232 x 1974`, proving the reader engine surface filled the WebView instead of leaving a smaller host box.
- PASS/PAGE-BOX: Foliate renderer attributes reported `maxInlineSize=1133px`, `maxBlockSize=1846px`, `maxColumnCount=1`, `columnThreshold=720px`, `topMargin=90px`, and `bottomMargin=50px`.
- PASS/PAGE-BOX: first prose text measured `fontSize=16px`, `lineHeight=24.8px`, and visible prose width `1061px` inside the current chapter content.
- PASS/FONT-SIZE: the publisher-style probe changed reader font size from `100%` to `140%` and measured root font size `16px -> 22.4px`.
- PASS/FONT-SIZE: the publisher paragraph font size also changed `16px -> 22.4px`, delta `6.4px`, proving the body text path scales on Android WebView rather than only headings.
- ARTIFACTS: `tmp\readerdev-layout-probes-20260620\page-box` and `tmp\readerdev-layout-probes-20260620\font-size-publisher-styles`.
- OPEN: this is emulator/WebView proof, not Tab S9 Ultra physical-device visual proof. It does not resolve subjective margin preference or settings dialog density.

## 2026-06-20 eta76 Visible Emulator Matrix Validation

Scope:
- Verified that the emulator window was not stale after restart/recovery and could be used for unattended reader validation.
- Ran the default Komikku reader matrix from the currently visible rendered EPUB page.

Validation:

```powershell
adb devices -l
adb -s emulator-5554 shell dumpsys window | Select-String -Pattern 'mCurrentFocus|mFocusedApp|mDreamingLockscreen|mShowingLockscreen'
adb -s emulator-5554 shell pidof darkaxt.navic.readerdev
adb -s emulator-5554 shell cat /proc/net/unix | Select-String -Pattern 'webview_devtools_remote'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ContinueOnFailure -ArtifactRoot .\tmp\readerdev-visible-emulator-matrix-20260620
```

Results:
- PASS/DEVICE: `emulator-5554` was attached and focused on `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`; lockscreen was not showing.
- PASS/WEBVIEW: reader process `10300` exposed `webview_devtools_remote_10300`.
- PASS/SCREENSHOT: ADB screenshot matched a live rendered EPUB text page with paper texture and page number `344 / 388`.
- PASS/MATRIX: the default matrix completed with `No matrix failures`.
- PASS/MATRIX: `baseline-current-reader`, `enter-readable-content`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` all passed.
- ARTIFACTS: `tmp\emulator-visible-check\navic-visible-check.png` and `tmp\readerdev-visible-emulator-matrix-20260620`.
- NOTE: the shell tool's default wrapper interrupted its own wait on the matrix command, but the spawned PowerShell validation process continued and wrote complete artifacts. Future long reader checks should be launched as hidden background jobs with file polling.

## 2026-06-20 eta76 Chapter Rail Endpoint Mapping Validation

Scope:
- Fixed the Komikku-style vertical chapter rail endpoint bug where the physical top endpoint could land on chapter page `2/44` instead of `1/44`.
- Preserved the rotated Material `Slider` visual as the Komikku-style rail, and added a transparent native Compose endpoint-mapping touch layer over the rail so physical Y coordinates map deterministically to chapter pages.
- Validated against the visible emulator using dirty `darkaxt.navic.readerdev` after installing the local patch.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidMain
.\gradlew.bat --no-daemon :composeApp:testAndroid
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -DeviceSerial emulator-5554 -NoBuild -NoInstall -NoDiscoverPublication -RequireReaderLaunch
adb -s emulator-5554 shell input tap 960 1440
adb -s emulator-5554 shell input tap 1700 1440
adb -s emulator-5554 shell input tap 960 1440
adb -s emulator-5554 shell input tap 1800 2588
adb -s emulator-5554 shell input tap 1800 330
```

Results:
- RED/BEFORE: on `A Memory of Light`, Chapter `37. The Last Battle`, native rail gesture from physical bottom to top landed on page `2/44`. DevTools captured `page=2/47` in `tmp\readerdev-native-rail-20260620\after-rail-top-visible-page-content.json`.
- ROOT CAUSE: the rotated Material `Slider` was visually correct, but Android hit mapping near the physical rail endpoints was not deterministic enough for the chapter page model.
- FIX: `KomikkuVerticalChapterProgressRail` now keeps the rotated Slider visual and overlays transparent tap/drag handling that maps `offset.y / railHeight` through `readerPageForVerticalChapterProgressOffset(...)`.
- PASS/HOST: `:composeApp:compileAndroidMain` passed after the rail patch.
- PASS/HOST: `:composeApp:testAndroid` passed after updating the source guard to allow the transparent endpoint-mapping layer while still rejecting custom Canvas rail replacements.
- PASS/COVER PATH: readerdev relaunched to the native cover first. Center tap hid chrome (`menuVisible=true->false`), then a right-region tap dispatched `TurnPage(direction=Next)` and changed `shellCover=true->false`.
- PASS/BOTTOM ENDPOINT: physical tap at the bottom rail area dispatched `goToChapterProgress(OEBPS/Text/Chapter-37.xhtml, 0.9767441860465116)` and bridge state reported `chapterPageIndex=43`, `chapterPageCount=44`, so the UI landed on page `44/44`.
- PASS/TOP ENDPOINT: physical tap at the top rail area dispatched `goToChapterProgress(OEBPS/Text/Chapter-37.xhtml, 0.0)` and bridge state reported `chapterPageIndex=0`, `chapterPageCount=44`, so the UI landed on page `1/44`.
- ARTIFACTS: `tmp\readerdev-native-rail-20260620\before.png`, `tmp\readerdev-native-rail-20260620\rail-after-bottom-tap.png`, `tmp\readerdev-native-rail-20260620\rail-after-top-tap.png`, and related UI XML/log captures in the same directory.
- OPEN: this is dirty-emulator proof for the chapter rail endpoint mapping. Clean release/physical-device validation is still required before treating this as release-grade.

## 2026-06-20 eta76 Resume Persistence After Interrupted Drag Validation

Scope:
- Validated the Priority 0 resume-persistence path on the visible Android emulator after a real page swipe and after an app interruption during a long drag.
- Targeted the `ReaderProgressSync`/readerdev persisted locator path that Whispersync will depend on.

Validation:

```powershell
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe relocation-payload
adb -s emulator-5554 shell input swipe 1650 1480 300 1480 450
adb -s emulator-5554 exec-out run-as darkaxt.navic.readerdev cat /data/user/0/darkaxt.navic.readerdev/shared_prefs/darkaxt.navic.readerdev_preferences.xml
adb -s emulator-5554 shell am force-stop darkaxt.navic.readerdev
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-resume-interruption-20260620\readerdev-no-start.env -DeviceSerial emulator-5554 -NoBuild -NoInstall -NoDiscoverPublication -RequireReaderLaunch
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe relocation-payload
adb -s emulator-5554 shell input tap 1700 1480
adb -s emulator-5554 shell input swipe 1650 1480 300 1480 5000
adb -s emulator-5554 shell am force-stop darkaxt.navic.readerdev
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-resume-interruption-20260620\readerdev-no-start.env -DeviceSerial emulator-5554 -NoBuild -NoInstall -NoDiscoverPublication -RequireReaderLaunch
node tools\reader-harness\src\adb-webview-eval.mjs --package darkaxt.navic.readerdev --device emulator-5554 --probe relocation-payload
```

Results:
- PASS/SETUP: emulator `emulator-5554` was focused on `darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity`.
- PASS/BASELINE: before moving, the WebView relocation snapshot was `OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=0`, `chapterPageCount=44`, `fraction=0.7527669076226654`.
- PASS/REAL SWIPE: a real ADB swipe moved the reader to `OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=2`, `chapterPageCount=44`, `fraction=0.7584986058101005`.
- PASS/PERSISTED PREF: `readerReadingProgressJson` stored book `3709`, resource `/opds/books/3709/resources/ebook-08aea0220318ac157c5f`, `textHref=OEBPS/Text/Chapter-37.xhtml`, and `progressFraction=0.7584986058101005`.
- PASS/NO-OVERRIDE RELAUNCH: after `am force-stop` and relaunching readerdev with the same publication but no `NAVIC_READER_DEV_START_*` override, `publicationReady` fired and the WebView relocation snapshot restored exactly to `OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=2`, `chapterPageCount=44`, `fraction=0.7584986058101005`.
- PASS/COVER SHELL: relaunch surfaced the native cover without bottom chrome. A right-region tap exited the cover and the WebView remained at the same restored locator instead of returning to the book start.
- PASS/INTERRUPTED DRAG: a long ADB swipe was started and the app was force-stopped while the gesture was active. Relaunching again with no start override restored the same stable persisted locator: `OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=2`, `chapterPageCount=44`, `fraction=0.7584986058101005`.
- ARTIFACTS: `tmp\readerdev-resume-interruption-20260620\before-relocation.json`, `after-real-swipe-relocation.json`, `prefs-before.xml`, `after-relaunch-relocation.json`, `after-cover-exit-relocation.json`, `after-disrupted-drag-relaunch-relocation.json`, and related screenshots/logs in the same directory.
- OPEN: this is dirty-emulator proof for readerdev `v1.0.11-eta76`. Clean release/physical-device validation is still required before closing the P0 at release grade.

## 2026-06-20 eta76 Post-Resume Komikku Matrix Validation

Scope:
- Re-ran the Komikku reader matrix after the resume/interrupted-drag validation to make sure the reader still passed the broad gesture/chrome/texture checks on the same visible emulator.
- This specifically guards against the recent center-tap and texture-direction regressions returning after relaunch/resume flows.

Validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -ContinueOnFailure -ArtifactRoot tmp\readerdev-post-resume-matrix-20260620
```

Results:
- PASS/DEVICE: matrix ran against `darkaxt.navic.readerdev` `versionName=v1.0.11-eta76`, `versionCode=409`, `lastUpdateTime=2026-06-20 11:49:01`.
- PASS/MATRIX: `reader-matrix-failures.txt` reported `No matrix failures.`
- PASS/STEPS: `baseline-current-reader`, `enter-readable-content`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` all completed.
- PASS/CENTER TAP: `center-tap-toggle\reader-tap-validation.txt` reported `nativeTapAction=True`, `explicitContentHandler=False`, and `contentTapHandledEvent=False`, meaning the center tap went through the native overlay path rather than the EPUB content tap path.
- PASS/TEXTURE NEXT: `drag-next\reader-texture-direction-validation.txt` sampled horizontal texture offsets with expected negative next-page movement and `wrong=False`.
- PASS/TEXTURE PREVIOUS: `drag-previous\reader-texture-direction-validation.txt` sampled horizontal texture offsets with expected positive previous-page movement and `wrong=False`.
- ARTIFACTS: `tmp\readerdev-post-resume-matrix-20260620`.
- OPEN: this matrix validates scripted emulator gestures. It does not replace physical-device feel testing for the cover touch behavior, page-drag preview quality, or settings dialog design.

## 2026-06-20 Readerdev Real Text Selection Long-Press Validation

Scope:
- Validated the previously synthetic-only selection flow with a real ADB long-press on visible EPUB text.
- This targets the Anx/Foliate bridge path where `SelectionChanged` existed, but normal text long-press did not create a DOM selection because native tap zones suppressed WebView long-click ownership.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-resume-interruption-20260620\readerdev-no-start.env -DeviceSerial emulator-5554 -NoDiscoverPublication
adb shell input tap 1720 1480
adb logcat -c
adb shell input swipe 640 760 640 760 900
adb exec-out screencap -p > tmp\emulator-window-check-20260620\after-patched-real-longpress.png
adb shell input tap 810 78
adb exec-out screencap -p > tmp\emulator-window-check-20260620\after-real-highlight.png
```

Results:
- RED/BEFORE: a real ADB long-press on normal text dispatched `ContentLongPressAt`, but the screenshot remained unchanged and the logs only showed `Reader WebView native long-click suppressed; native frame owns selection actions`. No `selectionChanged` event was emitted.
- FIX: native coordinate long-press now classifies plain selectable text and calls `selectReaderTextAtDocumentPoint(...)`, which uses the document caret range at the hit point, expands to a word range, applies the DOM selection, and dispatches `selectionchange`.
- PASS/HOST: `:composeApp:testAndroid` passed after adding the guard that requires native coordinate long-press to route plain text into text selection.
- PASS/JS: `node --check` passed for `navic-reader-content-interactions.js` and `navic-reader.js`.
- PASS/DEVICE: after dirty readerdev install on `emulator-5554`, a real ADB long-press at `640,760` selected the word `toward` and surfaced the native Highlight/Copy/Note toolbar.
- PASS/BRIDGE: logcat emitted `selectionChanged` with `text="toward"`, CFI `epubcfi(/6/98!/4/2/474,/1:44,/1:50)`, `footnote=false`, context text, and bounds.
- PASS/HIGHLIGHT: tapping Highlight produced a visible inline highlight over `toward` and logcat emitted `annotationDrawn` for the same CFI.
- ARTIFACTS: `tmp\emulator-window-check-20260620\after-patched-real-longpress.png`, `after-patched-real-longpress-log.txt`, `after-real-highlight.png`, and `after-real-highlight-log.txt`.
- OPEN: this is dirty-emulator proof for real long-press selection/highlight. Clean release/physical-device validation is still required, and note persistence/open-later UX remains a separate implementation gap.

## 2026-06-20 Readerdev Real Copy And Note Selection Validation

Scope:
- Continued the real-input Phase 5 validation after proving real long-press selection and Highlight.
- Validated Copy and Note Save from real ADB long-press selections, not DevTools-created selections.

Validation:

```powershell
adb logcat -c
adb shell input swipe 460 1110 460 1110 900
adb shell input tap 966 80
adb exec-out screencap -p > tmp\readerdev-real-selection-actions-20260620\copy-after-action.png
adb logcat -d -v time > tmp\readerdev-real-selection-actions-20260620\copy-log.txt

adb logcat -c
adb shell input swipe 360 1465 360 1465 900
adb shell input tap 1100 80
adb shell input tap 820 1488
adb shell input text Real_note
adb shell input tap 1190 1636

adb logcat -c
adb shell input swipe 230 1515 230 1515 900
adb shell input tap 1100 80
adb shell input tap 820 1488
adb shell input text Real_note_2
adb shell input tap 1190 1398
adb exec-out screencap -p > tmp\readerdev-real-selection-actions-20260620\note2-current-after-timeout.png
adb logcat -d -v time > tmp\readerdev-real-selection-actions-20260620\note2-current-log.txt
```

Results:
- PASS/COPY SELECTION: real ADB long-press at `460,1110` selected the visible word `right`, emitted `selectionChanged` with CFI `epubcfi(/6/98!/4/2/482,/1:180,/1:185)`, context text, and bounds, then surfaced the native action strip.
- PASS/COPY ACTION: tapping the native Copy action emitted `Reader selection copied length=5`, Android clipboard overlay suppression logs, and `Reader chrome overlay visible=false menu=false shellCover=false dialog=null`, proving the action completed and dismissed the selection strip.
- PASS/NOTE OPEN: real ADB long-press at `360,1465` selected visible text and tapping Note opened the native note dialog with selected text `"Go!"`, an `Annotation` field, Cancel, and disabled Save.
- RETRY/NOTE SAVE: the first Save tap used the pre-keyboard coordinate after the soft keyboard shifted the dialog; it dismissed the dialog without `Reader selection note save...` or `applyHighlights(...)` logs. This was an input-coordinate miss, not proof of a product save.
- PASS/NOTE SAVE: repeating the real long-press Note flow at `230,1515`, typing `Real_note_2`, and tapping the shifted Save coordinate emitted `Reader selection note save length=11`, then `Dispatching reader engine command: applyHighlights(count=4, notes=2)`, and `annotationDrawn` for `epubcfi(/6/98!/4/2/492,/1:8,/1:10)`.
- PASS/NOTE VISUAL: `tmp\readerdev-real-selection-actions-20260620\note2-current-after-timeout.png` shows the saved note annotation as a visible inline mark on the selected word `of`.
- ARTIFACTS: `tmp\readerdev-real-selection-actions-20260620\copy-selection-before-action.png`, `copy-after-action.png`, `copy-log.txt`, `note-dialog-open.png`, `note-dialog-ui.xml`, `note2-dialog-typed.png`, `note2-current-after-timeout.png`, and `note2-current-log.txt`.
- OPEN: this is dirty-emulator proof. Clean release/physical-device validation is still required, and durable annotation persistence/open-later UX still needs a dedicated release-grade check.

## 2026-06-20 Readerdev Scrolled Edge Pull-Up Harness And Real Gesture Validation

Scope:
- Validated the real scrolled-mode edge pull-up path that maps an upward swipe at the end of a scrolled EPUB section to the Anx-style `pullUp` bridge event and next-section navigation.
- Fixed a smoke-script false positive first: empty `Get-Content -Raw` output could cause required bridge-event checks to pass without evidence.

Validation:

```powershell
node --check tools\reader-harness\src\adb-webview-eval.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
.\gradlew.bat --no-daemon :composeApp:testAndroid
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -RequireReaderBridgeEvent __definitely_not_emitted__ -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-smoke-negative-fixed-20260620
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgePortsAnxStyleScrolledEdgePageTurns"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-resume-interruption-20260620\readerdev-no-start.env -DeviceSerial emulator-5554 -NoDiscoverPublication -RequireReaderLaunch -Capture
adb -s emulator-5554 shell input tap 1700 1480
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -SwipeFraction @("0.50,0.90,0.50,0.10,450,1600") -RequireReaderBridgeEvent pullUp -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-real-pullup-after-cover-exit-20260620
```

Results:
- RED/HARNESS: before the smoke-script fix, requiring `__definitely_not_emitted__` exited successfully and produced an empty `reader-bridge-events.log`; this was invalid evidence.
- FIX/HARNESS: `scripts\adb-reader-smoke.ps1` now normalizes raw text reads with `Get-TextFileRaw`, routes diagnostics through `Test-TextMatches`, and host tests reject direct `$bridgeDiagnosticsText -match/-notmatch` checks.
- PASS/HARNESS: after the fix, requiring `__definitely_not_emitted__` fails with `required bridge event '__definitely_not_emitted__' was not captured`.
- RED/PRODUCT: the new host guard for scrolled-edge gestures failed while `attachScrolledEdgeTurnGestures` used bubble-phase document listeners, which can be starved by native tap-zone touch suppression.
- FIX/PRODUCT: `touchstart`, `touchmove`, and `touchend` listeners in `attachScrolledEdgeTurnGestures` now listen in capture phase before the native tap-zone suppressor.
- PASS/HOST: targeted scrolled-edge host test passed after the JS fix; full `:composeApp:testAndroid` passed.
- PASS/INSTALL: dirty `darkaxt.navic.readerdev` rebuilt and installed on `emulator-5554`; `publicationReady` was captured; installed `versionName=v1.0.11-eta76`, `versionCode=409`, `lastUpdateTime=2026-06-20 14:11:24`.
- PASS/DEVICE: after exiting the native shell cover into visible EPUB text, a real ADB upward swipe from `0.50,0.90` to `0.50,0.10` emitted `Reader bridge event: pullUp()`, logged `page-turn:edge-swipe next`, and loaded `OEBPS/Text/Chapter-38.xhtml`.
- ARTIFACTS: `tmp\readerdev-smoke-negative-fixed-20260620`, `tmp\readerdev-real-pullup-after-cover-exit-20260620`, `tmp\readerdev-after-cover-exit-for-pullup-20260620.png`, and `captures\reader-dev\reader-dev-20260620-141136.png`.
- OPEN: this proves real scrolled-edge pull-up on dirty readerdev after leaving the native cover. Clean release/physical-device validation is still required before treating this as release-grade.

## 2026-06-20 Scrolled Edge Pull-Up Source Guard

Scope:
- Reproduced the user-reported regression class where a vertical drag to the next EPUB chapter auto-showed the Komikku menu.
- The old validation above proved a real `pullUp()` event crossed the bridge, but that event had no source, so the controller treated section-boundary drags the same as Anx's app-bar pull-up gesture.

Validation:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.scrolledEdgePullUpRecordsBridgeParityWithoutOpeningReaderMenu"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeAnxParityCallbacks"
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-resume-interruption-20260620\readerdev-no-start.env -DeviceSerial emulator-5554 -NoBuild -NoInstall -NoDiscoverPublication -RequireReaderLaunch -Capture
adb -s emulator-5554 shell input tap 1660 1480
& .\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -SwipeFraction @('0.50,0.90,0.50,0.10,450,700','0.50,0.90,0.50,0.10,450,700','0.50,0.90,0.50,0.10,450,1400') -RequireReaderBridgeEvent 'pullUp(source=scrolled-edge-swipe)' -CaptureReaderDiagnostics -ArtifactDir tmp\readerdev-scrolled-edge-source-20260620
```

Results:
- RED/BEFORE: the focused controller guard failed because `ReaderEngineEvent.PullUp` had no source and the controller unconditionally changed `menuVisible` to `true`.
- ROOT CAUSE: `navic-reader-page-turns.js` emitted the same `pullUp` bridge event for scrolled-edge next-section swipes that Anx uses to reveal the app bars. `ReaderController` could not tell an edge-turn from a menu pull-up.
- FIX: the scrolled-edge path now emits `{"type":"pullUp","source":"scrolled-edge-swipe"}`; bridge decoding and `FoliateEpubEngineAdapter` preserve the source; `ReaderController` records the `PullUp` interaction but preserves the existing menu state for `scrolled-edge-swipe`.
- PASS/HOST: focused controller, bridge protocol, and EPUB adapter tests passed. `node --check` passed for `navic-reader-page-turns.js`; `git diff --check` passed.
- PASS/DEVICE: dirty readerdev on `emulator-5554` launched a real cached EPUB into scrolled mode, exited the native cover, crossed from `OEBPS/Text/Chapter-42.xhtml` to `OEBPS/Text/Chapter-43.xhtml`, and captured `Reader bridge event: pullUp(source=scrolled-edge-swipe)`.
- PASS/DEVICE: every chrome line around the sourced pull-up stayed `Reader chrome overlay visible=false menu=false shellCover=false dialog=null`; the final screenshot shows Chapter 43 with no top or bottom menu.
- PASS/DEVICE: the standard smoke gate `-RequireReaderBridgeEvent 'pullUp(source=scrolled-edge-swipe)'` passed and stored artifacts in `tmp\readerdev-scrolled-edge-source-20260620`.
- OPEN: this is dirty-emulator proof for the regression. It should be included in the next release candidate, then repeated on a physical device before closing as release-grade.

## 2026-06-20 Scrolled EPUB Chapter Rail Pseudo-Pages

Scope:
- Follow-up to the user report that vertical/scrolled EPUB mode did not expose a Komikku-style middle progress rail even though Komikku webtoon/scrolled readers do.
- Kept the rail chapter-local: the middle slider must represent the current chapter/section, not the whole-book pagination profile.

Validation:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderPublishesScrolledSectionPseudoPagesForKomikkuChapterRail"
node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination.js
.\scripts\install-reader-dev.ps1 -Package 'darkaxt.navic.readerdev' -DeviceSerial 'emulator-5554' -NoLaunch
.\scripts\install-reader-dev.ps1 -Package 'darkaxt.navic.readerdev' -DeviceSerial 'emulator-5554' -NoBuild -NoInstall -RequireReaderLaunch -Capture
.\scripts\adb-reader-smoke.ps1 -Package 'darkaxt.navic.readerdev' -DeviceSerial 'emulator-5554' -NoLaunch -ReaderDevtoolsProbe runtime-state -CaptureReaderDiagnostics -ArtifactDir 'tmp\readerdev-scrolled-rail-runtime-20260620'
.\scripts\adb-reader-smoke.ps1 -Package 'darkaxt.navic.readerdev' -DeviceSerial 'emulator-5554' -NoLaunch -ReaderDevtoolsProbe relocation-payload -CaptureReaderDiagnostics -ArtifactDir 'tmp\readerdev-scrolled-rail-location-20260620'
.\scripts\adb-reader-smoke.ps1 -Package 'darkaxt.navic.readerdev' -DeviceSerial 'emulator-5554' -NoLaunch -ReaderDevtoolsProbe phase3-events -CaptureReaderDiagnostics -ArtifactDir 'tmp\readerdev-scrolled-edge-pullup-20260620'
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroid
```

Results:
- RED/BEFORE: the new `androidReaderPublishesScrolledSectionPseudoPagesForKomikkuChapterRail` guard failed because `reflowableScrolledSectionPagePosition()` did not exist and `reflowableSectionPagePosition()` returned `null` for `renderer.scrolled`.
- ROOT CAUSE: scrolled EPUB chapters had no chapter-local page model. `chapterPagePosition()` intentionally does not borrow whole-book pagination profile math, so Compose correctly had no middle rail value to display.
- FIX: `navic-reader-pagination.js` now derives scrolled-section pseudo-pages from `renderer.start`, `renderer.end`, `renderer.viewSize`, and the renderer/viewport size. It returns `pageIndex`, `pageCount`, and `pageCountSource: 'scrolled-section'` for scrolled reflowable sections.
- PASS/HOST: the new guard passed, `node --check` passed, and the full `:composeApp:testAndroid` aggregate passed after updating the stale source-inspection assertion that still required scrolled sections to return `null`.
- PASS/EMULATOR: dirty `darkaxt.navic.readerdev` was rebuilt/installed; installed package remained `versionName=v1.0.11-eta76`, `versionCode=409`, with `lastUpdateTime=2026-06-20 15:50:21`.
- PASS/RUNTIME: `runtime-state` on `emulator-5554` showed a scrolled renderer with `start=104.6666`, `end=2078.6666`, `viewSize=7848.52099609375`, and viewport height `1974`, which maps to 4 chapter pseudo-pages.
- PASS/BRIDGE: `relocation-payload` posted `chapterPageIndex=0`, `chapterPageCount=4`; a later Phase 3 probe after next-page movement posted `chapterPageIndex=1`, `chapterPageCount=4`.
- PASS/MENU REGRESSION: the same Phase 3 probe posted `pullUp(source=scrolled-edge-swipe)` and native chrome stayed `menu=false` before and after, confirming the sourced pull-up guard still prevents edge-drag chapter turns from auto-showing the menu.
- ARTIFACTS: `tmp\readerdev-scrolled-rail-runtime-20260620`, `tmp\readerdev-scrolled-rail-location-20260620`, `tmp\readerdev-scrolled-edge-pullup-20260620`, and `captures\reader-dev\reader-dev-20260620-155116.png`.
- OPEN: this is dirty-emulator proof only. It should be included in the next release candidate and repeated on a physical device before closing as release-grade.

## 2026-06-20 Whispersync Sidecar Foundation

Scope:
- Restored the missing `docs\superpowers\specs\2026-06-18-whispersync-design.md` into the current worktree and linked it to the Komikku reader-port authority document.
- Added the first self-contained Whispersync foundation slice: commonMain sidecar/timeline models, tolerant JSON parsing, audio-position lookup, visible-text-range seek lookup, and overlay-fragment projection.
- No reader shell UI, WebView, release APK, or live playback synchronization was changed in this slice.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid --tests "paige.navic.reader.WhispersyncTimelineParserTest"
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- TARGETED TASK NOTE: `:composeApp:testAndroid` does not accept `--tests` in this project; the targeted red command failed at task configuration with `Unknown command-line option '--tests'`.
- RED: aggregate `:composeApp:testAndroid` failed at `compileAndroidHostTest` because `decodeWhispersyncSidecar` and the related Whispersync model types did not exist. This proved the new tests were exercising missing behavior.
- FIX: added `WhispersyncModels.kt` with `WhispersyncSidecar`, `WhispersyncTimeline`, `WhispersyncSegment`, `WhispersyncAudioSeekTarget`, and `decodeWhispersyncSidecar`.
- FIX: parser accepts `segments`, `alignments`, or `clips`; audio ranges in ms or seconds; `audioResource`/`audioHref`/nested `audio.href`; `textHref`/`textResource`/`href`; `rangeCfi`; `fragmentId`; and missing `documentTextLength`.
- GREEN: rerunning aggregate `:composeApp:testAndroid` first exposed a Kotlin comparator nullability issue in `WhispersyncTimeline.seekTargetForVisibleTextRange`; after fixing the query pipeline, `:composeApp:testAndroid` passed.
- OPEN: this is domain-layer proof only. Fetching the sidecar from Bindery, wiring the reader/audio coordinator, live text highlighting, and physical-device validation remain future slices.

## 2026-06-20 Whispersync Sync Coordinator Foundation

Scope:
- Added the first Whispersync coordinator layer behind the Komikku/Anx reader boundary.
- The new state maps audiobook playback positions to `ReaderEngineCommand.ApplyMediaOverlay`, clears overlays outside active segments, maps visible text ranges to audio seek targets, suppresses repeated seek/overlay loops, and clears active overlays when sync is disabled.
- No reader shell UI, WebView runtime, Bindery network fetch, release APK, or live playback wiring was changed in this slice.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the new `ReaderWhispersyncSyncCoordinatorTest` failed at `compileAndroidHostTest` because `ReaderWhispersyncSyncState` and the sync coordinator methods did not exist.
- FIX: added `ReaderWhispersyncSyncCoordinator.kt` with `ReaderWhispersyncSyncState`, `ReaderWhispersyncVisibleRangeStep`, playback-position sync, visible-text-range seek targeting, sync toggle behavior, and stable engine-command dispatch keys.
- GREEN/COMPILE: rerunning `:composeApp:compileAndroidHostTest` passed.
- GREEN/AGGREGATE: the first aggregate `:composeApp:testAndroid` run executed 1500 tests and found one new test-contract issue: disabled sync preserves the last command while using the stable dispatch key to prove no new command should be sent. The test was updated to match the existing readaloud coordinator contract.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- OPEN: this is common/domain coordinator proof only. Bindery sidecar fetch, reader/audio runtime wiring, live highlighting, and clean APK/device validation remain future slices.

## 2026-06-20 Whispersync Bindery Sidecar Fetch Cache

Scope:
- Added the Bindery repository/API/cache entry point for Whispersync sidecar artifacts referenced by `docs\superpowers\specs\2026-06-18-whispersync-design.md`.
- The repository fetches sidecar JSON through the configured Bindery OPDS base URL and API key headers, decodes it through the tolerant Whispersync parser, and stores a canonical sidecar payload in the existing Bindery metadata cache.
- No reader shell UI, WebView runtime, audio playback wiring, release APK, or device validation was changed in this slice.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the new `whispersyncSidecarUsesConfiguredOpdsUrlApiKeyHeaderAndCache` repository test failed at `compileAndroidHostTest` because `FakeBinderyApiClient.whispersyncSidecarJson`, `BinderyRepository.getWhispersyncSidecar`, fake sidecar call probes, and `BinderyMetadataPayloadType.WhispersyncSidecar` did not exist.
- FIX: added `BinderyApiClient.fetchWhispersyncSidecarJson`, the Ktor JSON GET implementation, the repository `getWhispersyncSidecar(path)` cache wrapper, the sidecar metadata payload type, and fake-client call recording.
- GREEN/COMPILE: rerunning `:composeApp:compileAndroidHostTest` passed.
- RED/AGGREGATE: the first aggregate `:composeApp:testAndroid` run executed 1501 tests and found one cache round-trip issue: live sidecar JSON decoded root-level `segments`, but the canonical cached JSON encoded them under `timeline.segments`.
- FIX: extended `decodeWhispersyncSidecar` to accept canonical cached `timeline.segments` in addition to Bindery-style root-level `segments`, `alignments`, or `clips`.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- OPEN: this is repository/cache proof only. The sidecar is still not selected from concrete OPDS links, connected to the audiobook player, or validated in a clean APK/device flow.

## 2026-06-20 Whispersync Controller Timeline Attachment

Scope:
- Attached parsed Whispersync sidecars to the reader controller/coordinator state so later sync work can consume the timeline through the Komikku controller path.
- Opening a new publication now clears stale Whispersync state together with the existing reader-session state.
- This slice intentionally does not issue WebView commands, start playback sync, add live highlights, or add UI.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the new `whispersyncSidecarLoadsIntoControllerWithoutTouchingEngine` and `openingNewPublicationClearsWhispersyncSidecar` tests failed at `compileAndroidHostTest` because `ReaderCoordinator.loadWhispersyncSidecar`, controller Whispersync state, and `ReaderWhispersyncSessionState` did not exist.
- FIX: added `ReaderWhispersyncSessionState`, stored it in `ReaderControllerState`, reset it on publication open, and exposed controller/coordinator `loadWhispersyncSidecar(sidecar)` without engine commands.
- GREEN/COMPILE: rerunning `:composeApp:compileAndroidHostTest` passed.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- OPEN: this is controller-state proof only. The repository sidecar still needs to be selected from a concrete reader launch path and connected to the audiobook player before live Whispersync behavior exists.

## 2026-06-20 Whispersync Reader Launch Sidecar Attachment

Scope:
- Connected the existing `Screen.Reader` Whispersync route contract to the Bindery sidecar repository fetch path described in `docs\superpowers\specs\2026-06-18-whispersync-design.md`.
- Added a small launch policy that requires the complete selected-audiobook contract before fetching: sidecar path, artifact id, audiobook id, and audiobook book-file id.
- `ReaderScreen` now fetches the sidecar after the ebook publication opens, logs the selected artifact/audiobook identifiers, and attaches the parsed sidecar to the reader coordinator. This keeps the sidecar behind the Komikku controller boundary and does not add live sync UI, playback seeking, or WebView highlighting yet.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the new `ReaderWhispersyncLaunchPolicyTest` failed at `compileAndroidHostTest` because `ReaderWhispersyncLaunchAttachment` and `Screen.Reader.whispersyncLaunchAttachment()` did not exist.
- FIX: added `ReaderWhispersyncLaunchPolicy.kt` and wired `ReaderScreen` to fetch `binderyRepository.getWhispersyncSidecar(attachment.sidecarPath)` after `coordinator.open(...)`, then call `coordinator.loadWhispersyncSidecar(sidecar)` on success.
- GREEN/COMPILE: rerunning `:composeApp:compileAndroidHostTest` passed.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- OPEN: this is route-to-controller proof only. A live Bindery sidecar fetch, audiobook-player attachment, visible-range reporting, and release/device validation still need later Whispersync slices.

## 2026-06-20 Whispersync Visible Range Controller Handoff

Scope:
- Validated GLM's current-progress note against the live worktree. The report correctly describes the foundation-first Whispersync shape, but its claim that `ReaderScreen` does not consume Whispersync launch parameters is stale after the reader launch sidecar attachment slice.
- Added the next live-sync handoff behind the Komikku controller boundary: a `visibleTextRange` bridge event, engine event, adapter mapping, controller state update, overlay command dispatch, and coordinator-exposed audiobook seek target.
- This slice intentionally does not add the JavaScript visible-range emitter, start audiobook playback, or perform runtime seek/highlight validation.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroid
git diff --check
```

Results:
- RED: the first `:composeApp:compileAndroidHostTest` failed because `ReaderBridgeEvent.VisibleTextRange`, `ReaderEngineEvent.VisibleTextRange`, `ReaderWhispersyncVisibleTextRange`, and the coordinator/controller audio-seek handoff did not exist.
- FIX: added `visibleTextRange` bridge decoding with normalized start/end offsets, mapped it through `FoliateEpubEngineAdapter`, recorded the visible range in `ReaderWhispersyncSessionState`, projected the matching sidecar segment into `ReaderEngineCommand.ApplyMediaOverlay`, and exposed the transient `WhispersyncAudioSeekTarget` through `ReaderControllerStep` and `ReaderCoordinatorStep`.
- FIX: added Android bridge debug logging for `VisibleTextRange` so the new event is visible in ADB logs instead of becoming another silent bridge type.
- GREEN/COMPILE: rerunning `:composeApp:compileAndroidHostTest` passed after the debug-label exhaustiveness branch was added.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- GREEN/HYGIENE: `git diff --check` passed.
- OPEN: the reader runtime still needs a real JS visible-range emitter and audiobook-player seek consumption before this becomes user-visible Whispersync behavior.

## 2026-06-20 Whispersync Runtime Visible Text Range Emission

Scope:
- Added the real Foliate/WebView-side visible-text-range emitter required by `docs\superpowers\specs\2026-06-18-whispersync-design.md`.
- The runtime now extracts visible text offsets from rendered Foliate content documents after committed relocation snapshots, posts `visibleTextRange`, and deduplicates normal relocation repeats.
- Added a DevTools/ADB `visible-range` probe so this bridge path can be validated in a running Android WebView instead of only by source inspection.
- This slice intentionally does not add audiobook playback, audio seeking, word highlighting, or release-APK validation.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-location.js
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\scripts\install-reader-dev.ps1 -EnvFile 'C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env' -Package 'darkaxt.navic.readerdev' -DeviceSerial 'emulator-5554' -RequireReaderLaunch
node tools\reader-harness\src\adb-webview-eval.mjs --device emulator-5554 --package darkaxt.navic.readerdev --probe visible-range
adb -s emulator-5554 logcat -d -t 1000 | Select-String -Pattern "visibleTextRange|visible-text-range"
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED/RUNTIME: `androidReaderRuntimePostsVisibleTextRangeFromRenderedFoliateContent` first failed because the WebView runtime had no `postCurrentVisibleTextRange` path.
- FIX/RUNTIME: added a rendered-document extractor in `navic-reader-location.js` using `renderer.getContents?.()`, `createTreeWalker`, text-node ranges, `getBoundingClientRect`, caret sampling, and a `lastPostedVisibleTextRangeKey` duplicate guard.
- GREEN/RUNTIME: the focused `ReaderRuntimeAssetsTest` passed, JS syntax checks passed, and `:composeApp:testAndroid` passed.
- RED/DEVICE: the first emulator `visible-range` probe on the dirty readerdev APK showed the diagnostic snapshot returned `locationChanged` but did not emit a duplicate `visibleTextRange`; logcat proved the initial load had emitted `visibleTextRange`, so the diagnostic path was being suppressed by the new dedupe guard.
- FIX/DEVICE: threaded `forceDuplicatePost` into `postCurrentVisibleTextRange(detail, options)`, returned `visibleTextRangeResult` from `postLocationChanged`, and taught the DevTools probe to accept that runtime return because Android's Java bridge method is not reliably monkeypatchable from DevTools.
- GREEN/DEVICE: after reinstalling `darkaxt.navic.readerdev` on `emulator-5554`, `visible-range` returned `OEBPS/Text/Chapter-43.xhtml` with visible offsets `8-8160` and the current range CFI. Logcat confirmed `Reader bridge raw: {"type":"visibleTextRange"...}` and `Reader bridge event: visibleTextRange(OEBPS/Text/Chapter-43.xhtml, 8-8160)`.
- GREEN/AGGREGATE: the final `:composeApp:testAndroid` gate passed.
- OPEN: audiobook player consumption of `WhispersyncAudioSeekTarget`, audio-position-to-text highlighting, and release/physical-device validation remain pending.

## 2026-06-20 Whispersync Audiobook Seek Consumer

Scope:
- Validated GLM's current-progress attachment against the live worktree. Its foundation summary is broadly correct, but its claim that automatic audio seek is still absent is now stale after this slice.
- Added a controller-owned policy that maps `WhispersyncAudioSeekTarget.audioResource` to a `ReadaloudPlaybackPlan` track and produces `ReaderReadaloudPlaybackCommand.SeekToTrack`.
- `ReaderScreen` now loads the paired audiobook manifest for Whispersync reader routes, prepares the readaloud playback plan with Bindery headers/resume data, and dispatches the seek command through `AudiobookPlaybackManager` when page-visible text resolves to an audio seek target.
- This keeps the feature behind the Komikku controller shell and the existing audiobook playback boundary. It does not yet implement live audiobook-position-to-text highlighting.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\gradlew.bat --no-daemon :composeApp:testAndroid
git diff --check
```

Results:
- RED/POLICY: the new `ReaderWhispersyncPlaybackPolicyTest` failed in the aggregate Android host suite because `readerWhispersyncPlaybackCommandForSeekTarget(...)` did not exist.
- FIX/POLICY: added `ReaderWhispersyncPlaybackPolicy.kt` with relative/absolute audio resource matching, query/fragment stripping, path suffix matching, and `SeekToTrack` command creation.
- GREEN/POLICY: rerunning `:composeApp:testAndroid` passed after the policy implementation.
- RED/BOUNDARY: the new `readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary` source guard failed because `ReaderScreen` did not load a Whispersync audiobook playback plan or consume `step.whispersyncAudioSeekTarget`.
- FIX/BOUNDARY: wired `ReaderScreen` to load the selected audiobook manifest, build the readaloud playback plan, load it into `AudiobookPlaybackManager` without autoplay, and dispatch resolved seek commands on coordinator steps.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- GREEN/HYGIENE: `git diff --check` passed.
- OPEN: playback position from the audiobook player still needs to feed back into `ReaderWhispersyncSyncCoordinator.onAudiobookPlaybackPosition(...)` for live text highlighting; release/physical-device validation is still required before claiming end-to-end Whispersync.

## 2026-06-20 Whispersync Audiobook Playback Highlight Consumer

Scope:
- Completed the reverse Whispersync runtime direction: audiobook playback positions can now drive EPUB text overlay/highlight commands through the existing reader controller and Foliate engine adapter path.
- Extended `ReaderReadaloudPlaybackUiState` with an engine-neutral `audioResource` field so playback state can be matched against Bindery sidecar segment audio resources.
- `ReaderController.onReadaloudPlaybackState(...)` now keeps its existing chrome update behavior and, when a Whispersync sidecar is active, maps the playback resource/position through `ReaderWhispersyncSyncCoordinator.onAudiobookPlaybackPosition(...)`.
- `ReaderScreen` now observes the shared `AudiobookPlaybackManager.uiState` for the selected Whispersync audiobook, derives the correct audio resource from the loaded playback plan, and feeds normalized playback state back to the controller.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the new `whispersyncAudiobookPlaybackStateFeedsControllerHighlightOverlay` controller test failed at host-test compile because `ReaderReadaloudPlaybackUiState.audioResource` did not exist.
- FIX: added `audioResource` to `ReaderReadaloudPlaybackUiState`, taught the controller to resolve active Whispersync segments from playback state and emit `ReaderEngineCommand.ApplyMediaOverlay`, and added the `ReaderScreen` bridge from the shared audiobook mini-player state.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- OPEN: this is host-suite proof only. A clean release APK still needs to validate real Bindery sidecar playback, page-to-audio seek, audio-to-text overlay updates, and sync conflict behavior on-device before Whispersync can be called end-to-end usable.

## 2026-06-20 Whispersync Controller Status and Mismatch Badge

Scope:
- Reviewed GLM's current-progress attachment against the live worktree. The foundation summary is useful, but several "not done" items are stale after the visible-range, audiobook seek, and playback-highlight commits.
- Added controller-owned Whispersync status state for sidecar-ready, page-visible-range seek, audiobook playback, disabled sync, and mismatch conditions.
- Added a minimal native Komikku overlay badge only for mismatch status. Ready/playing states remain in controller state and do not add permanent reader chrome.
- This slice does not implement one-tap mismatch repair and does not claim release/device validation of end-to-end Whispersync.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the new controller tests failed at `compileAndroidHostTest` because `ReaderWhispersyncStatus`, `ReaderWhispersyncStatusKind`, and `ReaderWhispersyncSessionState.status` did not exist.
- FIX: added `ReaderWhispersyncStatus`, status-producing playback and visible-range coordinator steps, ready status on sidecar load, controller propagation, and a mismatch-only `KomikkuWhispersyncStatusBadge`.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed.
- GREEN/FINAL: rerunning `:composeApp:testAndroid` after documentation/formatting updates passed.
- GREEN/HYGIENE: `git diff --check` passed.
- OPEN: a clean release APK/device pass is still required before claiming usable end-to-end Whispersync.

## 2026-06-20 Whispersync Mismatch Repair Action

Scope:
- Validated GLM's current-progress attachment against the live worktree. Its foundation summary is stale on the current branch: visible range emission, audiobook seek consumption, playback-position highlighting, and mismatch status UI already exist.
- Added the missing one-tap repair path for Whispersync mismatch states.
- The repair action is controller-owned: `KomikkuWhispersyncStatusBadge` calls back through `ReaderRoot` and `ReaderScreen` into `ReaderCoordinator.repairWhispersyncMismatch()`, which recomputes the current visible text range against the sidecar timeline, reapplies the matching media overlay, and returns the existing `WhispersyncAudioSeekTarget` for `ReaderScreen` to dispatch through `AudiobookPlaybackManager`.
- The repair path intentionally no-ops without a current visible text range instead of guessing from page number or chapter label.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
```

Results:
- RED: the aggregate `:composeApp:testAndroid` failed at `compileAndroidHostTest` because `ReaderController.repairWhispersyncMismatch()` and `ReaderCoordinator.repairWhispersyncMismatch()` did not exist after adding the controller/coordinator/host-guard tests.
- FIX: added controller repair, coordinator routing, root/badge callback plumbing, and the `ReaderScreen` bridge into `applyCoordinatorStep(coordinator.repairWhispersyncMismatch())`.
- GREEN/AGGREGATE: rerunning `:composeApp:testAndroid` passed in 3m58s.
- OPEN: release/device validation of real paired Bindery sidecar playback, page-to-audio seek, audio-to-text overlay updates, and mismatch repair remains required before claiming end-to-end Whispersync usability.

## 2026-06-20 Whispersync Match Sheet Reader Launch

Scope:
- Reviewed GLM's current-progress attachment against the live worktree. The report correctly flagged the old `BinderyWhispersyncMatchesSheet.onOpenSidecar` route as a remaining no-op concern, but its statement that `ReaderScreen` does not consume Whispersync launch parameters is stale on the current branch.
- Removed the visible raw sidecar placeholder from the generic Whispersync matches sheet.
- Added a row-aware launch policy so both directions can build the same paired reader contract:
  - ebook row plus selected audiobook match
  - audiobook row plus selected ebook match
- Extended `BinderyWhispersyncMatch` with the matched ebook resource href, book-file id, and format so audiobook rows can launch the corresponding EPUB with the selected audiobook identity and sidecar.
- Updated `docs\superpowers\specs\2026-06-18-whispersync-design.md` to forbid user-facing inert sidecar actions.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicySourceTest
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false -Dkotlin.incremental=false -Dkotlin.daemon.enabled=false :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicySourceTest
```

Results:
- RED/BLOCKED: the first focused run failed before assertions because the Kotlin daemon left generated Android main class output in an inconsistent Windows file-lock state.
- RED/GUARD: after the first source-guard patch, host-test compile correctly failed on invalid `assertContains(String, String, "message")` usage; Kotlin treats the third positional parameter as `ignoreCase`.
- FIX/GUARD: switched the source-guard assertions to named `message = ...`.
- TOOLING/FIX: reran with fresh task execution, a single worker, disabled build/configuration cache, and disabled Kotlin incremental compilation to avoid stale class-output reuse.
- GREEN/FOCUSED: the focused Bindery policy/source tests passed. The log only contains the pre-existing `ModalBottomSheet` invisible-reference warning and a redundant `Json` test warning.
- AGGREGATE/BLOCKED: rerunning `:composeApp:testAndroid` reached host tests and failed only in `AndroidMediaPlayerViewModelSourceTest.playbackAssetPrefetchStateLivesOutsideAndroidMediaPlayerViewModel` and `AndroidMediaPlayerViewModelSourceTest.queueAutoFillJobStateLivesOutsideAndroidMediaPlayerViewModel`. Those failures are current-baseline media-player size/source-guard failures (`AndroidMediaPlayerViewModel.android.kt` is 1317 lines) and do not touch the Bindery Whispersync match-sheet launch path.
- OPEN: release/device validation of real paired Bindery sidecar playback, page-to-audio seek, audio-to-text overlay updates, and mismatch repair remains required before claiming end-to-end Whispersync usability.

## 2026-06-20 Whispersync Exact Companion Progress

Scope:
- Reviewed GLM's current-progress attachment against the live worktree. Its high-level foundation summary is useful, but the `onOpenSidecar` no-op finding is stale after the match-sheet launch fix, and the "progress save/restore with audio position" gap is now addressed for the local companion-progress path by this slice.
- Extended `BinderyWhispersyncCompanionProgress` with optional exact audio resource and millisecond position fields.
- `ReaderScreen` now passes the controller's current `WhispersyncAudioSeekTarget` into companion progress creation when saving ebook progress for a paired Whispersync route.
- Audiobook resume from companion progress now prefers the exact sidecar-derived audio resource and millisecond position before falling back to the older total-duration fraction estimate.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest.readerProgressCreatesWhispersyncCompanionProgressWithExactAudioTarget --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest.companionProgressUsesExactWhispersyncAudioPositionBeforeFractionEstimate --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest
```

Results:
- RED: the first focused host-test run failed at `compileAndroidHostTest` because `audioResource`, `audioPositionMs`, and the `audioSeekTarget` parameter did not exist.
- FIX: added exact companion-progress fields, normalized them during preference JSON updates, passed the current controller Whispersync audio seek target from `ReaderScreen`, and taught audiobook resume to match the exact resource before estimating from `progressFraction`.
- GREEN/FOCUSED: forced rerun of the focused host tests passed with all 24 tasks executed. The log contains only the pre-existing `ModalBottomSheet` invisible-reference warning and redundant `Json` warning.
- OPEN: this is host/source proof only. Release/device validation of real paired Bindery sidecar playback, page-to-audio seek, audio-to-text overlay updates, exact audiobook resume, and mismatch repair remains required before claiming end-to-end Whispersync usability.

## 2026-06-20 Whispersync Audiobook Resume Precedence

Scope:
- Fixed the next cross-media progress gap after exact companion-progress persistence: direct audiobook progress no longer always hides a newer Whispersync companion target.
- Added a pure resume policy so the audiobook player chooses the newer of direct audiobook progress and Whispersync companion-derived progress by `updatedAtMs`.
- Wired `BinderyAudiobookPlayerViewModel.rememberedProgress(...)` through that policy and tracks the selected resume source for autosave delta checks.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest.resumeProgressUsesNewestDirectOrWhispersyncCompanionProgress
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest
```

Results:
- RED: the focused test failed at `compileAndroidHostTest` because `binderyAudiobookBestResumeProgress(...)` did not exist.
- FIX: added `binderyAudiobookBestResumeProgress(...)` and changed the audiobook player ViewModel from direct-first resume selection to newest-progress resume selection.
- GREEN/FOCUSED: the new precedence test passed.
- GREEN/REGRESSION: the full `BinderyAudiobookPlayerPolicyTest` class plus `ReaderWhispersyncCompanionProgressSourceTest` passed. The run executed two tasks and reused existing compilation outputs.
- OPEN: this is host/source proof only. Release/device validation of real paired Bindery sidecar playback, page-to-audio seek, audio-to-text overlay updates, exact audiobook resume, and mismatch repair remains required before claiming end-to-end Whispersync usability.

## 2026-06-20 Whispersync Reader Launch Resume Policy

Scope:
- Closed the reader-side half of the exact companion-progress path: paired Whispersync reader sessions were still preparing their audiobook playback plan from direct audiobook progress only.
- Added a pure `binderyAudiobookResumeProgressForWhispersyncReader(...)` policy that reads direct audiobook progress and Whispersync companion progress, converts the newest matching companion entry through the selected audiobook manifest, and selects the newer resume source by `updatedAtMs`.
- Wired `ReaderScreen` paired Whispersync audiobook-plan creation through the shared policy so opening the ebook side cannot hide newer sidecar-derived audiobook progress behind stale direct audiobook progress.
- Added a source guard requiring the Whispersync reader launch path to keep using the companion-aware policy.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest.whispersyncReaderResumeProgressUsesNewestDirectOrCompanionStoreEntry --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest.readerScreenLoadsWhispersyncAudiobookPlanWithCompanionAwareResumeProgress
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest
```

Results:
- RED: the focused test failed at `compileAndroidHostTest` because `binderyAudiobookResumeProgressForWhispersyncReader(...)` did not exist.
- FIX: added the companion-aware reader resume helper and replaced `ReaderScreen`'s direct-only `binderyAudiobookSavedProgress(...)` lookup in the Whispersync audiobook playback-plan path.
- GREEN/FOCUSED: the new policy/source tests passed.
- GREEN/REGRESSION: the full `BinderyAudiobookPlayerPolicyTest` class plus `ReaderWhispersyncCompanionProgressSourceTest` passed in 11s.
- `git diff --check` passed.
- OPEN: this remains host/source proof. Release/device validation of a real paired Bindery sidecar/audiobook session is still required before claiming end-to-end Whispersync usability.

## 2026-06-20 Whispersync Load Failure Status

Scope:
- Closed a silent paired-reader failure path: Whispersync sidecar and paired-audiobook manifest failures were only logged from `ReaderScreen`.
- Added controller-owned `LoadFailed` status for Whispersync. It is attention-worthy, so the native Komikku status surface can show it, but it is not repairable through the mismatch-resync path.
- Added `ReaderController.reportWhispersyncLoadFailure(...)` / `ReaderCoordinator.reportWhispersyncLoadFailure(...)` and wired the sidecar and audiobook manifest failure branches in `ReaderScreen`.
- Updated `KomikkuWhispersyncStatusBadge` so the `Resync` action is only shown for repairable mismatch states, not load failures.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.whispersyncLoadFailureSurfacesControllerOwnedAttentionStatus --tests paige.navic.reader.ReaderControllerTest.repairWhispersyncMismatchNoopsForLoadFailureStatus --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest.readerScreenSurfacesWhispersyncLoadFailuresThroughControllerStatus
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest
```

Results:
- RED: the focused test failed at `compileAndroidHostTest` because `reportWhispersyncLoadFailure(...)` and `ReaderWhispersyncStatusKind.LoadFailed` did not exist.
- FIX: added the status kind, controller/coordinator method, non-repairable attention semantics, native badge gating, and `ReaderScreen` failure routing.
- GREEN/FOCUSED: the new controller/source tests passed.
- GREEN/REGRESSION: full `ReaderControllerTest` plus `ReaderWhispersyncCompanionProgressSourceTest` passed in 4m03s.
- `git diff --check` passed.
- WARNING: the regression log still contains the pre-existing `ModalBottomSheet.kt` invisible-reference warning; no new warning from this slice remains after removing the unnecessary `ReaderScreen` safe call.
- OPEN: this is host/source proof only. Release/device validation of the paired Bindery sidecar/audiobook flow remains required before claiming end-to-end Whispersync usability.

## 2026-06-20 GLM Current-Progress Validation Review

Scope:
- Reviewed GLM's current-progress attachment against the live `master` worktree after the newest Whispersync slices.
- The report is accurate as a historical overview of the foundation work, P0 reader stabilization, JS modularization, and emulator validation trend.
- The report is stale where it says `onOpenSidecar` is still a no-op, `ReaderScreen` does not consume Whispersync launch parameters, live playback-to-text highlighting is deferred, visible-range polling is not wired, audio seek on page changes is not wired, progress save/restore with audio position is not built, and load failures are silent.

Current branch corrections:
- `BinderyWhispersyncMatchesSheet` routes through `binderyWhispersyncReaderDestinationForRowMatch(...)`, guarded by `BinderyBookVersionPolicySourceTest`.
- `ReaderScreen` consumes `Screen.Reader.whispersyncLaunchAttachment()`, fetches the sidecar, loads it into `ReaderCoordinator`, prepares paired audiobook playback, dispatches Whispersync seek commands through the shared audiobook manager, persists exact companion audio progress, uses companion-aware reader resume policy, and surfaces sidecar/audiobook load failures through controller-owned status.
- `ReaderController.onReadaloudPlaybackState(...)` maps audiobook position into Whispersync overlay commands, and visible text range events map to audio seek targets.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.ReaderWhispersyncCompanionProgressSourceTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicySourceTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest
```

Results:
- GREEN/REGRESSION: the focused Whispersync route/controller/playback/companion-progress/load-failure suite passed in 26s.
- Log: `tmp\codex-gradle\glm-current-progress-whispersync-validation.log`.
- OPEN: no clean release/physical-device validation has been run for the full paired Bindery sidecar/audiobook session, so end-to-end Whispersync usability is not claimed yet.

## 2026-06-21 Whispersync Production Track Identity Matching

Scope:
- Registered the ASR matching POC folders from the user as diagnostic-only references in `docs\superpowers\specs\2026-06-18-whispersync-design.md`. A valid Bindery JSON sidecar remains authoritative.
- Validated production book `3809` against Bindery sidecar `/opds/books/3809/sync/3`, EPUB file `426`, and audiobook file `633`.
- Fixed the concrete production mismatch where sidecar cues identify the audio resource as the original internal `.m4b` path (`6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b`) while the reader playback plan identifies the playable media as a Bindery API-backed item (`audio-71ad8af54af0d403a1b5`).
- Extended sidecar parsing and timeline segments with optional audio resource id and track index metadata.
- Updated page-to-audio seek matching so it tries exact sidecar/playback resource candidates first, then sidecar track index, then the single-track playback-plan fallback.
- Updated audio-position-to-text matching so audiobook playback state can use track index when playback resource identifiers differ from sidecar cue resources.
- Prevented paused audiobook state from immediately clearing a visible-range Whispersync overlay after a successful page-to-audio seek.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest.productionSingleTrackBinderyPlanUsesSidecarTrackIndexWhenAudioHrefDiffers" --tests "paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest.playbackPositionUsesSidecarTrackIndexWhenPlaybackResourceDiffersFromAudioHref" --tests "paige.navic.reader.WhispersyncTimelineParserTest.productionBinderySidecarCuesParseIntoTimelineSegments"
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest" --tests "paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest" --tests "paige.navic.reader.WhispersyncTimelineParserTest"
```

Results:
- RED/COMPILE: `tmp\codex-gradle\whispersync-audio-track-match-red.log` failed at `compileAndroidHostTest` because the new tests referenced missing `audioResourceId`, `audioTrackIndex`, and playback-position track-index support.
- FIX: added parsed audio resource id/track index metadata, resource-candidate normalization, track-index-aware playback seek matching, and track-index-aware reverse overlay matching.
- GREEN/COMPILE: `tmp\codex-gradle\whispersync-audio-track-match-compile.log` passed.
- GREEN/FOCUSED: `tmp\codex-gradle\whispersync-audio-track-match-focused-green.log` passed the production parser/playback/sync matching tests.
- RED/REGRESSION: `tmp\codex-gradle\whispersync-paused-overlay-red.log` proved paused audiobook state could still clear a freshly applied visible-range overlay.
- FIX: `ReaderController.onReadaloudPlaybackState(...)` now preserves chrome/status updates for paused playback but only drives reverse audio-to-text overlay commands while playback is active.
- GREEN/FOCUSED: `tmp\codex-gradle\whispersync-paused-overlay-green.log` passed `ReaderControllerTest`, `ReaderWhispersyncPlaybackPolicyTest`, `ReaderWhispersyncSyncCoordinatorTest`, and `WhispersyncTimelineParserTest`.
- EMULATOR: installed readerdev `v1.0.11-eta76` and launched book `3809` with sidecar/artifact/audiobook route metadata.
- EMULATOR: `tmp\codex-gradle\readerdev-whispersync-paused-overlay-validation.log` shows `Whispersync sidecar loaded artifact=3 audiobook=34 bookFile=633 segments=4188`, `Whispersync audiobook plan loaded audiobook=34 items=1`, visible text range `OEBPS/xhtml/Authorforeword.xhtml`, and successful seek `positionMs=263360` for the sidecar `.m4b` cue.
- EMULATOR: the same validation log contains no `Whispersync audiobook seek ignored` after the fix and no immediate `clearOverlay` after the successful seek.
- SCREENSHOT: `captures\reader-dev\readerdev-3809-whispersync-paused-overlay-20260621-0233.png`.
- AGGREGATE/BLOCKED: `tmp\codex-gradle\whispersync-audio-track-match-green.log` reached host tests but failed only in the existing `AndroidMediaPlayerViewModelSourceTest` size/source-guard checks. Those baseline failures are unrelated to Whispersync track identity matching.
- OPEN: the top-left Whispersync headset affordance is only brainstormed. It must be implemented as native Komikku overlay state, hidden on pages with no cue coverage, low-opacity when ready, crossed when disabled/paused, and ringed while audio is still loading.

## 2026-06-21 Whispersync Companion Progress Track Index

Scope:
- Closed the resume side of the production track-identity fix. Page-to-audio seek already uses sidecar track index when the sidecar `.m4b` resource differs from the Bindery playback-plan API media identity; companion progress now persists that same sidecar track index.
- `BinderyWhispersyncCompanionProgress` now stores optional `audioTrackIndex` next to `audioResource` and `audioPositionMs`.
- `binderyWhispersyncCompanionProgressForReader(...)` copies `WhispersyncAudioSeekTarget.segment.audioTrackIndex` into stored companion progress.
- `binderyAudiobookProgressFromWhispersyncCompanion(...)` still prefers exact audio-resource matching, but falls back to stored track index for exact audiobook resume when the resource string cannot match the manifest reading-order href.
- Updated `docs\superpowers\specs\2026-06-18-whispersync-design.md` so exact companion progress is defined as resource + track index + millisecond position.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest.readerProgressCreatesWhispersyncCompanionProgressWithSidecarTrackIndex --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest.companionProgressUsesSidecarTrackIndexWhenAudioResourceDiffersFromManifest
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest
```

Results:
- RED: `tmp\codex-gradle\whispersync-companion-track-index-red.log` failed at `compileAndroidHostTest` because `BinderyWhispersyncCompanionProgress.audioTrackIndex` did not exist.
- FIX: added the stored field, normalized it during JSON update, copied it from the sidecar-derived seek target, and added track-index fallback to exact companion-progress audiobook resume.
- RED/BEHAVIOR: the first green attempt compiled but failed the new audiobook policy test because the fake fixture track was only 60 seconds and the production-sized `263_360ms` position was correctly clamped. The test input was corrected to stay inside the fixture duration while still proving track-index matching.
- GREEN/FOCUSED: `tmp\codex-gradle\whispersync-companion-track-index-green.log` passed the two new tests.
- GREEN/REGRESSION: `tmp\codex-gradle\whispersync-companion-track-index-policy-green.log` passed full `BinderyContinueShelfPolicyTest` and `BinderyAudiobookPlayerPolicyTest`.
- OPEN: release/device validation still needs to prove that exact companion progress restores the paired audiobook in a real book `3809` Whispersync session after app restart or route reopen.

## 2026-06-21 Whispersync Sidecar Load Visible-Range Replay

Scope:
- Emulator validation of the clean readerdev build exposed a startup race: Foliate could emit `visibleTextRange` before the Bindery sidecar finished loading.
- Before this fix, `ReaderController.loadWhispersyncSidecar(...)` replaced the whole Whispersync session and did not replay the already stored visible range through the newly loaded timeline. That meant a valid synced page could show no initial page-to-audio seek until a later relocation/page movement happened.
- `loadWhispersyncSidecar(...)` now preserves `visibleTextRange`, replays it through the loaded timeline, emits the same `ApplyMediaOverlay` command and `WhispersyncAudioSeekTarget` as a normal post-sidecar visible-range event, and keeps the controller-owned overlay/audio metadata state in sync.
- This is a controller-boundary fix; it does not move ownership into the WebView.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.loadingWhispersyncSidecarReplaysExistingVisibleTextRange
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest
.\scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-3809.env -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
```

Results:
- RED: `tmp\codex-gradle\whispersync-sidecar-visible-range-replay-red.log` failed the new controller test with no engine command when sidecar loading followed a visible text range.
- FIX: `ReaderController.loadWhispersyncSidecar(...)` now replays the existing visible range against the loaded sidecar timeline and returns the resulting overlay command and audio seek target.
- GREEN/FOCUSED: `tmp\codex-gradle\whispersync-sidecar-visible-range-replay-green.log` passed the new race test.
- GREEN/REGRESSION: `tmp\codex-gradle\whispersync-sidecar-visible-range-replay-regression-green.log` passed `ReaderControllerTest`, `ReaderWhispersyncSyncCoordinatorTest`, `ReaderWhispersyncPlaybackPolicyTest`, `BinderyContinueShelfPolicyTest`, and `BinderyAudiobookPlayerPolicyTest`.
- EMULATOR: `tmp\codex-gradle\readerdev-3809-visible-range-replay-install.log` built, installed, launched, and captured readerdev `v1.0.11-eta76` on `emulator-5554`.
- EMULATOR: `tmp\codex-gradle\readerdev-3809-visible-range-replay-validation.log` shows the paired production session loaded `segments=4188`, loaded audiobook `34`, received visible text range `OEBPS/xhtml/Authorforeword.xhtml`, emitted `Whispersync audiobook seek ... positionMs=263360`, dispatched `applyOverlayFragment`, and received `overlayFragmentActive`.
- EMULATOR/CLEAN-DATA: `tmp\codex-gradle\readerdev-3809-companion-track-clean-validation.log` shows a first-run clean-data launch on `OEBPS/xhtml/title.xhtml`; that page has no matching cue coverage, so no companion progress was written. This is expected and is not evidence for exact companion restore.
- EMULATOR/CLEAN-DATA/CUE-COVERED: from the same clean readerdev session, DevTools navigation to `OEBPS/xhtml/Authorforeword.xhtml` posted `visibleTextRange` `3-4923`, emitted `Whispersync audiobook seek ... positionMs=263360`, dispatched `applyOverlayFragment`, and received `overlayFragmentActive`.
- EMULATOR/CLEAN-DATA/PERSISTENCE: `shared_prefs/darkaxt.navic.readerdev_preferences.xml` persisted `binderyWhispersyncCompanionProgressJson` for `bookId=3809`, `audiobookId=34`, `audiobookBookFileId=633`, sidecar `.m4b` `audioResource`, `audioPositionMs=263360`, and `audioTrackIndex=0`.
- EMULATOR/REOPEN: a no-build/no-install paired readerdev route reopen preserved data and loaded the audiobook plan at `startTrack=0 startPositionMs=263360`, proving the companion progress is consumed for the paired route instead of falling back to zero.
- EMULATOR/REOPEN: the same reopen log then received `visibleTextRange(OEBPS/xhtml/Authorforeword.xhtml, 3-4923)`, emitted the same `Whispersync audiobook seek ... positionMs=263360`, dispatched `applyOverlayFragment`, and received `overlayFragmentActive`.
- OPEN: release/device validation still needs to prove the installed release APK consumes the same exact stored sidecar target.

## 2026-06-21 Whispersync Release-Candidate Regression Check

Scope:
- Re-ran the focused host regression set for the current dirty Whispersync implementation before considering any release candidate.
- Inspected release state: `androidApp` still declares `versionName = "v1.0.11-eta76"` / `versionCode = 409`, local tags include `v1.0.11-eta76`, and GitHub latest release is still `v1.0.11-eta76`.
- Did not publish a new GitHub release from this pass. The existing `scripts\publish-github-release.ps1` watcher uses bounded polling/timeouts; under the current project guardrail, release publication should either use an approved existing release watcher invocation or a revised watcher that does not encode timeout-based cancellation as the release control path.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest
git diff --check
```

Results:
- GREEN/REGRESSION: `tmp\codex-gradle\whispersync-release-candidate-regression.out.log` passed in 17s with 24 actionable tasks: 2 executed, 22 up-to-date.
- GREEN/WHITESPACE: `git diff --check` passed after the documentation update.
- OPEN: no eta77 or newer release exists yet for the Whispersync track-index/replay/reopen fixes.

## 2026-06-21 Release Watcher No-Timeout Guardrail

Scope:
- Aligned `scripts\publish-github-release.ps1` with the project guardrail that timeout-based cancellation must not control release publication.
- Removed `TimeoutMinutes`, deadline arithmetic, and timeout failure exits from the release watcher.
- The watcher now uses condition-based polling loops for GitHub Actions run discovery, workflow completion, and release visibility. It still emits heartbeat status through `PollSeconds`, but it does not cancel a release because a fixed wall-clock duration elapsed.

Validation:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest.releaseWatcherUsesConditionPollingWithoutTimeoutCancellation
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest.releaseWatcherUsesConditionPollingWithoutTimeoutCancellation --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest
git diff --check
```

Results:
- RED: `tmp\codex-gradle\release-watcher-timeout-red.log` failed the new contract test because the release script still exposed timeout/deadline cancellation.
- FIX: removed timeout parameters and deadline loops from the release watcher while preserving status polling.
- GREEN/FOCUSED: `tmp\codex-gradle\release-watcher-timeout-green.log` passed the new release watcher contract.
- GREEN/REGRESSION: `tmp\codex-gradle\release-watcher-and-whispersync-regression.log` passed the release watcher contract plus the focused Whispersync regression set.
- GREEN/RERUN: `tmp\codex-gradle\release-watcher-and-whispersync-regression-rerun.log` passed the same combined gate after the POC report references were added to the spec.
- GREEN/BINDERY-JSON: `tmp\codex-gradle\bindery-book-sync-json-green.log` passed `BinderyBookSyncJsonTest`, covering both the direct book-sync payload and the live Bindery OPDS `publications[].properties` shape used by book `3809`.
- GREEN/WHITESPACE: `git diff --check` passed after the script and documentation changes.

## 2026-06-21 Eta77 Readerdev Komikku Matrix

Target:
- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Version: `versionName=v1.0.11-eta77`, `versionCode=410`, `lastUpdateTime=2026-06-21 04:08:26`.
- Source release: `v1.0.11-eta77` / commit `56735db9`.

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -ArtifactRoot captures\reader-komikku-matrix\eta77-20260621-040936 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -ArtifactDir captures\reader-komikku-matrix\eta77-20260621-040936\probe-chapter-progress-current-endpoints -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe chapter-progress-current-endpoints
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -ArtifactDir captures\reader-komikku-matrix\eta77-20260621-040936\probe-chapter-progress-endpoints -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe chapter-progress-endpoints
```

Artifacts:
- Install/capture log: `tmp\codex-gradle\readerdev-eta77-install.log`.
- Matrix log: `tmp\codex-gradle\readerdev-eta77-komikku-matrix.log`.
- Matrix artifacts: `captures\reader-komikku-matrix\eta77-20260621-040936`.
- Startup screenshot: `captures\reader-dev\reader-dev-20260621-040840.png`.

Results:
- GREEN/INSTALL: readerdev built, installed, granted notification permission, launched an EPUB target discovered from `bindery-debug.env`, emitted `publicationReady`, and captured the startup screenshot.
- GREEN/MATRIX: all matrix steps passed: baseline current reader, native cover, cover center-tap toggle, cover drag next, center-tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk. `reader-matrix-failures.txt` reports `No matrix failures.`
- GREEN/RAIL-CURRENT: `chapter-progress-current-endpoints` reached the current chapter endpoints: progress `0` produced `chapterPageIndex=0` / `chapterPageCount=3`; progress `1` produced `chapterPageIndex=2` / `chapterPageCount=3`.
- GREEN/RAIL-BROAD: `chapter-progress-endpoints` selected `OEBPS/Text/Chapter-37.xhtml` with `chapterPageCount=44`; progress `0` produced `chapterPageIndex=0`, and progress `1` produced `chapterPageIndex=43`.
- LIMIT: this is readerdev/emulator evidence. The release package on the emulator was still `v1.0.11-eta74`, so this does not close the physical/installed release validation item by itself.

Next required fix:
- Continue with physical/release validation when the phone or release package is available. If no physical device is available, the next implementation slice remains Priority 1: drag-preview black void / texture movement polish, or the Whispersync release-device paired playback validation after eta77 is installed.

## 2026-06-21 Readerdev Whispersync Page Control Validation

Scope:
- Implemented and validated the controller-owned top-left Whispersync audiobook control for production book `3809`.
- Verified the control uses the selected paired audiobook route instead of relying on WebView interaction.
- Added a touch shield guard so the visible control area does not leak disabled/loading taps into page navigation.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile tmp\readerdev-3809-eta77.env -RequireReaderLaunch
adb -s emulator-5554 shell input swipe 850 1200 200 1200 500
adb -s emulator-5554 shell input tap 78 78
adb -s emulator-5554 shell input tap 78 78
```

Artifacts:
- Install log: `tmp\codex-gradle\readerdev-whispersync-control-shield-install.out.log`.
- Screenshots:
  - `captures\reader-dev\navic-whispersync-shield-installed-after-cover-swipe.png`
  - `captures\reader-dev\navic-whispersync-shield-installed-after-play-tap.png`
  - `captures\reader-dev\navic-whispersync-shield-installed-after-pause-tap.png`

Results:
- RED/GREEN: the new source guard first failed because the Whispersync control did not include the same pointer shield pattern as other Komikku chrome controls, then passed after adding `modifier.pointerInput(Unit)`.
- GREEN/HOST: focused Whispersync playback policy plus shell wiring tests passed in 10s with 24 actionable tasks: 2 executed, 22 up-to-date.
- GREEN/INSTALL: readerdev `v1.0.11-eta77` / `versionCode=410` installed at `2026-06-21 05:46:28`, launched, and emitted `publicationReady`.
- GREEN/PLAY: after leaving the shell cover, tapping the top-left control at the density-correct center coordinate `78,78` moved readaloud playback to `PLAYING(3)` at sidecar position `263360ms`.
- GREEN/PAUSE: tapping the same control again moved readaloud playback to `PAUSED(2)` at the current audiobook position `284962ms`.
- CORRECTION: an earlier tap at `52,52` was too close to the control edge after the emulator's 1.5 px/dp scaling. The control bounds are approximately `42..114` pixels in each axis with menus hidden, so the density-correct center is `78,78`.
- OPEN: this validates readerdev/emulator behavior, not a public release APK on the user's phone.

## 2026-06-21 Whispersync Audio-Follow Source Guard

Scope:
- Prevented playback-position-driven reader navigation from feeding back into a fresh reader-to-audio seek.
- Added a source field to `visibleTextRange` bridge events and preserved it through `ReaderBridgeEvent`, `ReaderEngineEvent`, and `ReaderWhispersyncVisibleTextRange`.
- Marked WebView relocations caused by `applyOverlayFragment()` as `media-overlay-follow`.
- Controller behavior now stores `media-overlay-follow` visible ranges but does not emit a new `WhispersyncAudioSeekTarget`, preserving audio-owned overlay state.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeVisibleTextRangeForWhispersync --tests paige.navic.reader.ReaderControllerTest.audioFollowVisibleRangeDoesNotSeekAudiobookBackToReaderViewport
.\gradlew.bat --no-daemon --no-parallel --rerun-tasks :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeVisibleTextRangeForWhispersync --tests paige.navic.reader.ReaderControllerTest.audioFollowVisibleRangeDoesNotSeekAudiobookBackToReaderViewport
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-location.js
git diff --check
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile tmp\readerdev-3809-eta77.env -RequireReaderLaunch
adb -s emulator-5554 shell input swipe 850 1200 200 1200 500
adb -s emulator-5554 shell input tap 78 78
```

Artifacts:
- Install log: `tmp\codex-gradle\readerdev-media-overlay-source-install.log`.

Results:
- RED: focused tests first failed because `ReaderBridgeEvent.VisibleTextRange`, `ReaderEngineEvent.VisibleTextRange`, and `ReaderWhispersyncVisibleTextRange` did not carry `source`.
- GREEN/HOST: focused bridge/controller gate passed with `--rerun-tasks` in 4m17s.
- GREEN/JS: `node --check` passed for `navic-reader.js` and `navic-reader-location.js`.
- GREEN/WHITESPACE: `git diff --check` passed.
- GREEN/INSTALL: readerdev `v1.0.11-eta77` / `versionCode=410` installed at `2026-06-21 06:17:28`, launched, and emitted `publicationReady`.
- EMULATOR: after leaving the native cover and starting playback, logcat showed `Dispatching reader engine command: applyOverlayFragment`, `controlled-relocate:begin media-overlay-follow`, `viewport-layout label=media-overlay-follow`, and `overlayFragmentActive` for production book `3809`.
- LIMIT: the observed emulator run did not emit a new `visibleTextRange(source=media-overlay-follow)` bridge event because the target location was a duplicate. The native suppression branch is therefore host-test-proven and the WebView source marker is emulator-proven, but a non-duplicate audio-follow visible-range event remains open for full device observation.

## 2026-06-21 Whispersync Audio-Follow DevTools Probe

Scope:
- Added a dedicated `whispersync-audio-follow` DevTools smoke probe to `tools\reader-harness\src\adb-webview-eval.mjs`.
- Added the probe to `scripts\adb-reader-smoke.ps1` so it can be run through the standard ADB artifact path.
- Added an Android-host source guard proving the smoke script and DevTools helper expose the probe.
- The probe dispatches `applyOverlayFragment`, then forces a diagnostic location snapshot with reason `media-overlay-follow` so the WebView must return a `visibleTextRange` carrying that source.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncAudioFollowVisibleRangeSource --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperInjectsReaderBridgeEventsThroughDevTools
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile tmp\readerdev-3809-eta77.env -NoBuild -NoInstall -RequireReaderLaunch
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-audio-follow -ArtifactDir tmp\readerdev-whispersync-audio-follow-probe -RequireReaderBridgeEvent visibleTextRange -RequireReaderLog source=media-overlay-follow
```

Artifacts:
- `tmp\readerdev-whispersync-audio-follow-probe\reader-devtools-probe.json`
- `tmp\readerdev-whispersync-audio-follow-probe\reader-bridge-events.log`
- `tmp\readerdev-whispersync-audio-follow-probe\logcat-reader.log`

Results:
- GREEN/HOST: focused Android-host harness guards passed.
- GREEN/JS: `node --check` passed for `tools\reader-harness\src\adb-webview-eval.mjs`.
- GREEN/READERDEV: `darkaxt.navic.readerdev` `versionName=v1.0.11-eta77` was open on production book `3809`.
- GREEN/BRIDGE: the probe moved from `OEBPS/xhtml/chapter1.xhtml` to `OEBPS/xhtml/mini_toc.xhtml` and captured `visibleTextRange(OEBPS/xhtml/mini_toc.xhtml, 1-486, source=media-overlay-follow)` in Android logcat.
- GREEN/LOOP-GUARD: the same artifact contains no `Whispersync audiobook seek` after the `media-overlay-follow` visible range, proving the controller suppression path held for a non-duplicate WebView bridge event.
- Remaining: this is readerdev/emulator evidence. Release-device validation of the paired Bindery sidecar/audiobook flow is still required.

## 2026-06-21 Whispersync Page-Scoped Playback Control

Scope:
- Tightened the top-left Whispersync audiobook affordance so it represents cue coverage on the current page, not merely the presence of a loaded sidecar.
- `ReaderWhispersyncStatusKind.Ready` now hides the playback control; cue-covered pages use `SeekingAudio` as the visible loading state while the paired audiobook plan/player is not available.
- Moving from a synced visible text range to an unsupported visible range now demotes the controller status back to `Ready`, clears the active media overlay, clears the audio metadata label, and dispatches `ClearMediaOverlay` when needed.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.ReaderControllerTest.visibleTextRangeWithoutCueDemotesWhispersyncToReadyAndClearsOverlay
.\gradlew.bat --no-daemon --no-parallel --rerun-tasks :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.ReaderControllerTest.visibleTextRangeWithoutCueDemotesWhispersyncToReadyAndClearsOverlay
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeVisibleTextRangeForWhispersync
```

Results:
- RED: the policy test first failed because `Ready` still rendered the control.
- RED: the controller test first failed because an unsupported visible range preserved the previous Whispersync status/overlay instead of returning to sidecar-ready state.
- GREEN/FOCUSED: the forced focused run passed after updating `ReaderWhispersyncPlaybackPolicy` and `ReaderWhispersyncSyncCoordinator`.
- GREEN/REGRESSION: the broader reader/Whispersync host set passed.
- Remaining: this is host-level proof. Release-device validation still needs to confirm the icon appears only on cue-covered pages for a real paired Bindery sidecar/audiobook session.

## 2026-06-21 Whispersync Page-Scoped Control Emulator Check

Scope:
- Validated the page-scoped Whispersync audiobook affordance on the running readerdev emulator against production book `3809` and sidecar `/opds/books/3809/sync/3`.
- Covered three states: native cover, unsupported EPUB page, cue-covered EPUB page, then unsupported page after an active cue.

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile tmp\readerdev-3809-eta77.env -RequireReaderLaunch
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-window.xml
adb -s emulator-5554 exec-out screencap -p > tmp\page-scoped-control-initial.png
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe runtime-state -ArtifactDir tmp\readerdev-page-scoped-control-initial-runtime
adb -s emulator-5554 shell input swipe 850 1200 200 1200 500
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe runtime-state -ArtifactDir tmp\readerdev-page-scoped-control-after-cover-swipe
```

Additional DevTools validation:
- Dispatched `goToHref` to `OEBPS/xhtml/Authorforeword.xhtml`, then `diagnosticLocationSnapshot(reason=page-scoped-control-cue-covered)`.
- Dispatched `goToHref` to `OEBPS/xhtml/mini_toc.xhtml`, then `diagnosticLocationSnapshot(reason=page-scoped-control-unsupported)`.
- Captured UI hierarchy, screenshots, and filtered logcat after each state change.

Artifacts:
- `tmp\page-scoped-control-initial.png`
- `tmp\page-scoped-control-window-initial.xml`
- `tmp\page-scoped-control-after-cover-swipe.png`
- `tmp\page-scoped-control-window-after-cover-swipe.xml`
- `tmp\page-scoped-control-cue-covered.png`
- `tmp\page-scoped-control-window-cue-covered.xml`
- `tmp\page-scoped-control-unsupported-after-cue.png`
- `tmp\page-scoped-control-window-unsupported-after-cue.xml`
- `tmp\readerdev-page-scoped-control-initial-runtime\reader-devtools-probe.json`
- `tmp\readerdev-page-scoped-control-after-cover-swipe\reader-devtools-probe.json`

Results:
- GREEN/READERDEV: readerdev `v1.0.11-eta77` installed, launched, emitted `publicationReady`, and remained debuggable on PID `31692`.
- GREEN/COVER: the native cover UI dump had no `Whispersync` / `audiobook` control.
- GREEN/UNSUPPORTED-FIRST-PAGE: after swiping off the cover to `mini_toc.xhtml`, the UI dump had no `Whispersync` / `audiobook` control.
- GREEN/CUE-COVERED: the DevTools jump to `OEBPS/xhtml/Authorforeword.xhtml` produced `visibleTextRange(OEBPS/xhtml/Authorforeword.xhtml, 3-4923, source=page-scoped-control-cue-covered)`.
- GREEN/CUE-CONTROL: the cue-covered UI dump contained `content-desc="Play Whispersync audiobook"` at the top-left control bounds.
- GREEN/AUDIO-MATCH: logcat showed `Whispersync audiobook seek audio=6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b positionMs=263360`, proving the sidecar source-path identity now resolves to the actual audiobook playback plan.
- GREEN/OVERLAY: logcat showed `applyOverlayFragment` and `overlayFragmentActive` for `OEBPS/xhtml/Authorforeword.xhtml` at `clipBeginSeconds=263.36`.
- GREEN/DEMOTION: jumping from the cue-covered page back to `OEBPS/xhtml/mini_toc.xhtml` produced `visibleTextRange(OEBPS/xhtml/mini_toc.xhtml, 1-486, source=page-scoped-control-unsupported)`, removed the native Whispersync control from the UI dump, and dispatched `clearOverlay`.
- Remaining: this is emulator/readerdev evidence. A clean release APK on a physical device still needs to confirm the same page-scoped control behavior.

## 2026-06-21 Whispersync Page-Scoped Control Smoke Probe

Scope:
- Promoted the manual cue/unsupported Whispersync page-scoped control DevTools validation into the standard `adb-reader-smoke.ps1` probe path.
- Added `whispersync-page-scoped-control` to `tools\reader-harness\src\adb-webview-eval.mjs` and to the smoke script probe whitelist.
- Added a host asset guard so the probe name, cue href, unsupported href, expected log labels, and clear-overlay assertion cannot silently disappear.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncPageScopedControl
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta77 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-page-scoped-control -ArtifactDir tmp\readerdev-whispersync-page-scoped-control-probe
```

Artifacts:
- `tmp\readerdev-whispersync-page-scoped-control-probe\reader-devtools-probe.json`
- `tmp\readerdev-whispersync-page-scoped-control-probe\logcat-reader.log`
- `tmp\readerdev-whispersync-page-scoped-control-probe\reader-bridge-events.log`
- `tmp\readerdev-whispersync-page-scoped-control-probe\reader-diagnostics-summary.txt`

Results:
- RED: the focused host guard first failed because `whispersync-page-scoped-control` was absent from the smoke script and DevTools helper.
- GREEN/HOST: the focused host guard passed after adding the probe.
- GREEN/JS: `node --check tools\reader-harness\src\adb-webview-eval.mjs` passed.
- GREEN/EMULATOR: the smoke probe passed against readerdev `v1.0.11-eta77` / `versionCode=410` on emulator PID `31692`.
- GREEN/CUE: probe JSON captured `OEBPS/xhtml/Authorforeword.xhtml` with `visibleStart=3`, `visibleEnd=4923`, and `source=page-scoped-control-cue-covered`.
- GREEN/AUDIO: Android logcat captured `Whispersync audiobook seek ... positionMs=263360`.
- GREEN/OVERLAY: Android logcat captured `overlayFragmentActive`.
- GREEN/UNSUPPORTED: probe JSON captured `OEBPS/xhtml/mini_toc.xhtml` with `visibleStart=1`, `visibleEnd=486`, and `source=page-scoped-control-unsupported`.
- GREEN/CLEAR: Android logcat captured `Dispatching reader engine command: clearOverlay`.
- Remaining: this probe automates the emulator/readerdev evidence. It still does not replace clean release APK validation on a physical device.

## 2026-06-21 eta78 Whispersync Release-Candidate Preflight

Scope:
- Prepare a release candidate for the post-eta77 Whispersync milestone: native page-scoped top-left playback control, source-aware audio-follow loop suppression, and repeatable DevTools smoke probes.
- Bump Android release metadata from `v1.0.11-eta77` / `versionCode=410` to `v1.0.11-eta78` / `versionCode=411`.

Commands:

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta78
git diff --check
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeVisibleTextRangeForWhispersync --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncAudioFollowVisibleRangeSource --tests paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncPageScopedControl --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest --tests paige.navic.ui.screens.bindery.BinderyAudiobookPlayerPolicyTest
```

Results:
- GREEN/VERSION: `Android versionName matches v1.0.11-eta78`.
- GREEN/WHITESPACE: `git diff --check` exited `0`.
- GREEN/HOST: focused reader/Whispersync regression set passed with `BUILD SUCCESSFUL in 18s`, 24 actionable tasks, 2 executed.
- GREEN/RELEASE: tag `v1.0.11-eta78` was pushed and GitHub Actions run `27893761015` completed successfully.
- GREEN/ANDROID: `Build Android APK`, `Verify release APK signing`, `Upload APK artifact`, and `Create GitHub Release` all completed successfully.
- GREEN/SCOPE: `Build iOS IPA` and `Attach iOS IPA to GitHub Release` were skipped.
- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta78`
- APK asset: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta78/Navic.apk`
- APK SHA-256: `998897CE5C2FF1BDE1C50AAB011B065DEFEB9FF1375853963FB4CC1B8FC63AFF`.
- GREEN/EMULATOR-BASIC: downloaded `Navic.apk`, installed it on `emulator-5554`, launched `darkaxt.navic`, and verified `versionCode=411`, `versionName=v1.0.11-eta78`, `lastUpdateTime=2026-06-21 07:53:19`.
- Basic smoke artifacts: `tmp\release-eta78-basic-smoke`.
- LIMIT: the release app opened to the Navidrome login screen with Android's notification permission dialog, so this smoke does not validate the reader or paired Whispersync route.
- Remaining: physical release validation still needs to confirm the paired Bindery sidecar/audiobook path with the installed APK.

## 2026-06-21 Boundary Drag Preview Loading Fallback

Scope:
- Address the Priority 1 black-void/drag-feel risk where a section-boundary native drag waited for the adjacent preview iframe before moving the current page.
- Keep Komikku-native drag ownership intact: the current page now continues moving while the adjacent preview is still loading, records a `boundary-preview-loading` diagnostic source, and swaps to the real underlay when ready.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
git diff --check
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta78 -NoLaunch -IncludeCoverChecks -ArtifactRoot tmp\readerdev-boundary-preview-matrix-20260621
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-preview-underlay --fixture tmp\reader-fixtures\publication.epub
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimePostsVisibleTextRangeFromRenderedFoliateContent --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest.androidReaderResolvesTocHrefNavigationBeforeCommittingLocation --tests paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderClampsDelayedPassiveReflowableRelocationsAfterPageTurns --tests paige.navic.shared.AndroidMediaPlayerViewModelSourceTest.playbackAssetPrefetchStateLivesOutsideAndroidMediaPlayerViewModel --tests paige.navic.shared.AndroidMediaPlayerViewModelSourceTest.queueAutoFillJobStateLivesOutsideAndroidMediaPlayerViewModel
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest
```

Artifacts:
- `tmp\readerdev-boundary-preview-matrix-20260621`
- `tools\reader-harness\output\epub-native-drag-preview-underlay.json`

Results:
- RED/HOST: the new `androidReaderKeepsCurrentPageMovingWhileBoundaryPreviewLoads` guard first failed because the boundary-preview loading branch returned before current-page movement.
- GREEN/HOST: `ReaderRuntimePaperSurfaceTest` passed after removing the early return and adding the `boundary-preview-loading` source.
- GREEN/JS: `node --check` for `navic-reader-page-turns.js` passed.
- GREEN/WHITESPACE: `git diff --check` exited `0`.
- GREEN/READERDEV: `install-reader-dev.ps1` built, installed, launched `darkaxt.navic.readerdev` on `emulator-5554`, and observed `publicationReady`; installed evidence was `versionName=v1.0.11-eta78`, `versionCode=411`, `lastUpdateTime=2026-06-21 08:06:50`.
- GREEN/MATRIX: all Komikku matrix steps passed with `No matrix failures.` The `drag-next` and `drag-previous` steps reported `readerNativeDragPreview=True` and `wrongTextureDirection=False`.
- GREEN/BROWSER-HARNESS: `epub-native-drag-preview-underlay` forced a section-boundary drag and captured `page-drag-preview` with `source=boundary-preview-loading`, renderer movement from `start=722` to `start=863`, then a ready adjacent preview underlay.
- GREEN/TEXTURE-LOGIC: `texture-offset-logic` passed.
- GREEN/STALE-GUARDS: the five previously stale host guards passed after updating them to the current runtime signatures and direct media-helper state ownership checks.
- GREEN/FULL-HOST: `:composeApp:testAndroidHostTest` passed with `BUILD SUCCESSFUL in 29s`.
- Remaining: this proves the loading fallback and standard matrix on readerdev/emulator. Manual release/physical validation is still needed for perceived drag feel on the user's devices.

## 2026-06-21 Tiny-Section Chapter Rail Suppression

Scope:
- Tighten Komikku chapter navigator behavior so the chapter-local progress rail is hidden for tiny sections with fewer than three pages.
- This keeps cover/one-page sections and two-page sections from showing a mostly decorative rail while preserving previous/next chapter buttons.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest.chapterProgressSliderOnlyShowsForSectionsWithEnoughPages
.\gradlew.bat --no-daemon --no-parallel --rerun-tasks :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest.chapterProgressSliderOnlyShowsForSectionsWithEnoughPages
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorUsesKomikkuTabletPaddingContract --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorArrowsUseTocChapterNavigationCallbacks --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuChapterNavigatorUsesChapterLocalControllerProgressInsteadOfBookProgress
```

Results:
- RED/HOST: the focused common mapping test first failed because `readerShouldShowChapterProgressSlider(totalPages = 2)` still returned true.
- GREEN/HOST: the forced focused run passed after changing the threshold to `totalPages > 2`.
- GREEN/CHROME-GUARDS: the related Komikku chapter navigator mapping/source guards passed.
- Remaining: this is a host-level UI policy guard. Emulator/device validation is still needed for the exact visual result in short chapters.

## 2026-06-21 Readerdev Chapter Rail Endpoint Validation

Scope:
- Validate the chapter-local progress rail on the installed readerdev APK after the tiny-section rail policy and boundary-preview fixes.
- Confirm both WebView endpoint math and native Komikku rail button routing on a real emulator surface.

Environment:
- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed version: `versionName=v1.0.11-eta78`, `versionCode=411`, `lastUpdateTime=2026-06-21 08:06:50`.
- Reader process: `32609`.
- Surface: Android reported `1848x2960` override resolution. Coordinates below use that full surface, not the scaled desktop preview.

Commands:

```powershell
.\scripts\adb-reader-smoke.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -ExpectedVersionName v1.0.11-eta78 -ReaderDevtoolsProbe chapter-progress-endpoints -CaptureReaderDiagnostics -NoLaunch -ArtifactDir tmp\readerdev-progress-endpoints-20260621-a
.\scripts\adb-reader-smoke.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -ExpectedVersionName v1.0.11-eta78 -ReaderDevtoolsProbe chapter-progress-current-endpoints -CaptureReaderDiagnostics -NoLaunch -ArtifactDir tmp\readerdev-progress-current-endpoints-20260621-a
adb -s emulator-5554 shell input tap 924 1480
adb -s emulator-5554 shell input tap 1824 2760
adb -s emulator-5554 shell input tap 1796 210
```

Artifacts:
- `tmp\readerdev-progress-endpoints-20260621-a`
- `tmp\readerdev-progress-current-endpoints-20260621-a`
- `tmp\readerdev-progress-ui-direct-20260621-a\before-bottom-arrow.png`
- `tmp\readerdev-progress-ui-direct-20260621-a\after-bottom-arrow-realcoords.png`
- `tmp\readerdev-progress-ui-direct-20260621-a\bottom-arrow-realcoords-logcat.log`
- `tmp\readerdev-progress-ui-direct-20260621-a\before-top-arrow-realcoords.png`
- `tmp\readerdev-progress-ui-direct-20260621-a\after-top-arrow-realcoords.png`
- `tmp\readerdev-progress-ui-direct-20260621-a\top-arrow-realcoords-logcat.log`

Results:
- GREEN/DEVTOOLS-ENDPOINTS: `chapter-progress-endpoints` selected `OEBPS/Text/Chapter-37.xhtml` as the best candidate and verified endpoint mapping for a 44-page chapter.
- GREEN/DEVTOOLS-CURRENT: `chapter-progress-current-endpoints` verified the current Chapter 37 rail from `chapterPageIndex=0` through `chapterPageIndex=43` with `chapterPageCount=44`.
- GREEN/PAGINATION-PROFILE: both probes used deterministic pagination profile data: `pageCountSource=pagination-profile`, `paginationFingerprint=navic-pagination-v1:952170858`, `pageCount=332`, and `paginationProfileObservedChapterCount=64`.
- GREEN/WEBVIEW-COVER-SUPPRESSION: the probe's cover attempt reported `location-changed:cover-skipped`, which is the expected suppressed-webview-cover behavior.
- GREEN/NATIVE-NEXT-CHAPTER: the lower rail button tap at `x=1824 y=2760` dispatched `goToHref(OEBPS/Text/Chapter-38.xhtml)` and landed on Chapter 38 with `chapterPageCount=1`, `pageIndex=291`, `pageCount=332`.
- GREEN/NATIVE-PREVIOUS-CHAPTER: the upper rail button tap at `x=1796 y=210` dispatched `goToHref(OEBPS/Text/Chapter-37.xhtml)` and landed on Chapter 37 with `chapterPageIndex=0`, `chapterPageCount=44`, `pageIndex=247`, `pageCount=332`.
- GREEN/TINY-SECTION-VISUAL: Chapter 38 is a one-page section and rendered only previous/next chapter buttons, with no decorative progress rail.
- NOTE/COORDINATES: an earlier tap near `x=1215 y=1830` toggled the reader menu because it was inside the page area on the actual `1848x2960` surface. That was a coordinate error in the validation pass, not a rail failure.
- Remaining: this validates the readerdev emulator path. Physical release validation is still needed for the user's phone/tablet feel and for any release-only regressions.

## 2026-06-21 Whispersync Character-Offset Overlay Guard

Scope:
- Close GLM's confirmed gap where Bindery ASR sidecar segments carried `ebookStart`/`ebookEnd` character offsets, but `WhispersyncSegment.toReaderOverlayFragment()` dropped them and the Foliate runtime only highlighted EPUB fragment ids.
- Preserve the existing fragment-id path for Storyteller/media-overlay style cues while adding a fallback for ASR cues with no DOM id.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderBridgeProtocolTest
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.ReaderRuntimeAssetsTest
git diff --check
```

Results:
- RED/HOST: the new parser/bridge assertions first failed because `ReaderOverlayFragment` had no `textStart` or `textEnd` fields.
- FIX: `ReaderOverlayFragment` now carries `textStart`/`textEnd`, bridge command/event serialization preserves them, and `WhispersyncSegment.toReaderOverlayFragment()` forwards sidecar offsets.
- FIX/RUNTIME: `applyOverlayFragment()` now falls back to `highlightMediaOverlayTextRange()` when no EPUB fragment id is highlighted. The helper walks the same raw content text-node offset space used by visible text range reporting, wraps the requested sentence range with the existing media-overlay CSS class, and unwraps old range markers in `clearOverlay()`.
- GREEN/FOCUSED: `WhispersyncTimelineParserTest`, `ReaderBridgeProtocolTest`, and `ReaderRuntimeAssetsTest` passed after the fix.
- GREEN/JS: `node --check composeApp\src\androidMain\assets\reader\navic-reader.js` passed.
- GREEN/WHITESPACE: `git diff --check` exited `0`.
- Remaining: this is host/source proof only. A readerdev or release-device probe still needs to prove that a production Bindery cue with `ebookStart`/`ebookEnd` visibly highlights the expected sentence in the WebView.

## 2026-06-21 Readerdev Whispersync Character-Offset Overlay Probe

Scope:
- Promote the Whispersync ASR character-offset overlay fallback from host/source proof to live Android WebView proof.
- Validate that the runtime can highlight a raw text-node character range when there is no EPUB fragment id, which is the Bindery ASR sidecar shape GLM flagged.
- Keep this as readerdev/emulator validation; it does not claim release-device paired playback is complete.

Commands:

```powershell
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncCharacterOffsetOverlay"
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-char-offset-overlay -RequireReaderBridgeEvent overlayFragmentActive -ArtifactDir tmp\readerdev-whispersync-char-offset-overlay-20260621-r3
```

Artifacts:
- `tmp\readerdev-whispersync-char-offset-overlay-20260621-r3`
- `tmp\readerdev-whispersync-char-offset-overlay-20260621-r3\reader-devtools-probe.json`
- `tmp\readerdev-whispersync-char-offset-overlay-20260621-r3\logcat-reader.log`

Results:
- RED/HARNESS: the first live smoke probe failed because it required the DevTools monkey-patch to observe `overlayFragmentActive` directly, even though Android logcat is the authoritative bridge event sink for this script.
- FIX/HARNESS: the probe now verifies the DOM marker directly inside the WebView and lets `adb-reader-smoke.ps1 -RequireReaderBridgeEvent overlayFragmentActive` validate the Android bridge event through logcat.
- RED/HARNESS: the second live smoke probe failed because it expected `Dispatching reader engine command: clearOverlay`, which only appears for Android-controller dispatched commands. This probe clears through `NavicReaderBridge.dispatch(...)`, whose correct runtime evidence is `[NavicReader] dispatch clearOverlay`.
- GREEN/HOST: `node --check tools\reader-harness\src\adb-webview-eval.mjs` passed.
- GREEN/HOST: focused `ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncCharacterOffsetOverlay` passed.
- GREEN/EMULATOR: readerdev `v1.0.11-eta78` / `versionCode=411` on `emulator-5554` ran the `whispersync-char-offset-overlay` probe successfully.
- GREEN/RANGE: the probe observed `visibleTextRange(OEBPS/Text/Chapter-37.xhtml, 8-3432, source=whispersync-char-offset-overlay-initial)`, targeted characters `32-80`, and found a live marker with class `navic-active-overlay-fragment navic-media-overlay-range`.
- GREEN/MARKER: the wrapped text snippet was `ttle\n\n    \n      Dawn broke that morning on Polo`, proving raw text-node range wrapping rather than fragment-id lookup.
- GREEN/BRIDGE: logcat captured `Reader bridge raw: {"type":"overlayFragmentActive","textHref":"OEBPS/Text/Chapter-37.xhtml","textStart":32,"textEnd":80,...}` and `Reader bridge event: overlayFragmentActive()`.
- GREEN/CLEAR: the probe dispatched `clearOverlay` and verified no `data-navic-media-overlay-range="true"` markers remained in rendered Foliate contents.
- Remaining: release-device validation still needs to prove the same character-offset sentence highlight during real paired audiobook playback.

## 2026-06-21 Komikku Whispersync Player Chrome Surface

Scope:
- Add the first controller-owned audioebook player surface inside the Komikku reader shell.
- Keep the page-scoped headset affordance as-is, but add a bottom-bar audiobook action when Whispersync/readaloud playback is available.
- Route the action through `ReaderController` / `ReaderCoordinator` into `ReaderControllerDialog.WhispersyncPlayer`.
- Reuse `ReaderReadaloudPlaybackUiState` and `ReaderReadaloudPlaybackCommand` for play/pause, seek, scrubber, speed, and sync toggle without embedding the full Bindery audiobook screen in the reader.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.whispersyncPlayerDialogIsControllerOwnedLikeKomikkuReaderChrome --tests paige.navic.reader.ReaderCoordinatorTest.bottomBarDialogsRouteThroughControllerWithoutEngineCommands --tests paige.navic.reader.ReaderViewerTest.whispersyncPlayerSurfaceIsKomikkuOwnedReaderChrome
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderCoordinatorTest --tests paige.navic.reader.ReaderViewerTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest
git diff --check
```

Results:
- RED/HOST: the focused player-surface tests first failed at compile time because `ReaderControllerDialog.WhispersyncPlayer` and `openWhispersyncPlayerDialog()` did not exist.
- FIX/CONTROLLER: `ReaderController` now owns the Whispersync player dialog route and keeps the reader menu visible without emitting engine commands.
- FIX/COORDINATOR: `ReaderCoordinator.openWhispersyncPlayerDialog()` routes the bottom-chrome action through controller state, matching the Komikku shell ownership model.
- FIX/UI: `ReaderAppBars` exposes a conditional audiobook bottom-bar action, and `ReaderRoot` renders `KomikkuWhispersyncPlayerDialog` as reader chrome instead of embedding `BinderyAudiobookPlayerScreen`.
- GREEN/FOCUSED: the three focused controller/coordinator/source-guard tests passed.
- GREEN/READER-SLICE: `ReaderControllerTest`, `ReaderCoordinatorTest`, `ReaderViewerTest`, and `ReaderWhispersyncPlaybackPolicyTest` passed together.
- RED/FULL-SUITE: the first full `testAndroidHostTest` pass exposed a stale `ReaderRuntimeCommonChromeTest` source guard still expecting the old `totalPages > 1` chapter-slider rule.
- FIX/GUARD: the guard now matches the current tiny-section rule, `readerShouldShowChapterProgressSlider(totalPages) == totalPages > 2`, so one/two-page sections remain uncluttered.
- GREEN/GUARD: focused `ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail` passed after the guard correction.
- GREEN/FULL-SUITE: full `:composeApp:testAndroidHostTest` passed after the guard correction.
- GREEN/WHITESPACE: `git diff --check` exited `0`.
- Remaining: this is host/source proof only. Readerdev or release-device validation still needs to prove the player opens from a real Whispersync-capable page, commands reach `AudiobookPlaybackManager`, and the UI matches the Komikku overlay feel on phone/tablet surfaces.

## 2026-06-21 Readerdev Whispersync Player Chrome Runtime Probe

Scope:
- Promote the Komikku Whispersync player chrome from host/source proof to live Android readerdev proof.
- Validate the production Bindery book 3809 sidecar/audio pairing path in the emulator.
- Prove the native bottom-bar player action appears only after the EPUB reader page is active, opens the controller-owned player dialog, and sends play/pause commands to the readaloud MediaSession.

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -EnvFile tmp\readerdev-whispersync-3809.env -DeviceSerial emulator-5554 -RequireReaderLaunch
adb -s emulator-5554 shell input tap 924 1480
adb -s emulator-5554 shell input swipe 1600 1480 400 1480 500
adb -s emulator-5554 shell input tap 924 1480
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-window.xml
adb -s emulator-5554 pull /sdcard/navic-window.xml tmp\navic-window.xml
adb -s emulator-5554 shell input tap 1105 2858
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-window-dialog.xml
adb -s emulator-5554 pull /sdcard/navic-window-dialog.xml tmp\navic-window-dialog.xml
adb -s emulator-5554 shell input tap 924 1460
adb -s emulator-5554 logcat -d -v time
adb -s emulator-5554 shell input tap 924 1460
adb -s emulator-5554 logcat -d -v time
```

Artifacts:
- `tmp\whispersync-before-menu.png`
- `tmp\whispersync-after-cover-tap.png`
- `tmp\whispersync-after-cover-swipe.png`
- `tmp\whispersync-page-menu.png`
- `tmp\navic-window.xml`
- `tmp\whispersync-player-dialog.png`
- `tmp\navic-window-dialog.xml`
- `tmp\whispersync-after-play.png`
- `tmp\navic-window-after-play.xml`

Results:
- GREEN/INSTALL: readerdev `v1.0.11-eta78` / `versionCode=411` installed on `emulator-5554` and launched directly into the Whispersync-capable book 3809.
- GREEN/SIDECAR: logcat before the focused UI probe showed `Bindery metadata fetched type=whispersync-sidecar path=/opds/books/3809/sync/3`, `Whispersync sidecar loaded artifact=3 audiobook=34 bookFile=633 segments=4188`, and `Whispersync audiobook plan loaded audiobook=34 items=1`.
- GREEN/COVER: the reader initially rendered the native cover. A center tap on the cover routed through `KomikkuReaderNativeFrameHost` as `Reader native tap action=MENU` and changed `menuVisible=false->true` while preserving `shellCover=true`; it did not discard the cover.
- GREEN/EPUB-PAGE: a horizontal swipe from the native cover entered the EPUB reader at `3 / 79`.
- GREEN/BOTTOM-ACTION: after opening page chrome, `tmp\navic-window.xml` contained the bottom-bar action with `content-desc="Whispersync player"` at bounds roughly `[1069,2822][1141,2894]`.
- GREEN/DIALOG: tapping that action opened `KomikkuWhispersyncPlayerDialog`; `tmp\navic-window-dialog.xml` contained the audiobook metadata, seekbar, `Play audiobook`, `Seek back 10 seconds`, `Seek forward 10 seconds`, `Speed`, speed chips, and `Audio sync`.
- GREEN/PLAY: tapping `Play audiobook` changed Android MediaSession `darkaxt.navic.readerdev/androidx.media3.session.id.navic-readaloud/34` to `PLAYING(3)` at position `263360`, and the dialog changed to `Pause audiobook`.
- GREEN/PAUSE: tapping the same control again changed the same MediaSession to `PAUSED(2)` at position `300794`.
- Remaining: this is readerdev/emulator proof. A release-device pass is still needed before publishing a user-facing release claim, and the visual design of the dialog still needs Komikku-style polish after the feature path is stable.

## 2026-06-21 Whispersync Headset Glyph Visual Correction

Scope:
- Correct the page-level Whispersync affordance after user feedback that the top-left indicator must not look like a mobile UI button.
- Replace the audiobook/library glyph with a dedicated headset glyph.
- Remove the circular button/progress-ring treatment from the page-level affordance while preserving its touch shield.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:generateValkyrieImageVector --rerun-tasks
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
git diff --check
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -RequireReaderLaunch -Capture
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile tmp\readerdev-whispersync-3809.env -NoBuild -NoInstall -RequireReaderLaunch -Capture
adb -s emulator-5554 shell input swipe 1050 1100 220 1100 300
adb -s emulator-5554 shell screencap -p /sdcard/navic-reader-headset-check-2.png
adb -s emulator-5554 pull /sdcard/navic-reader-headset-check-2.png captures\reader-dev\readerdev-3809-headset-check-2.png
```

Artifacts:
- `captures\reader-dev\reader-dev-20260621-183205.png`
- `captures\reader-dev\reader-dev-20260621-183352.png`
- `captures\reader-dev\readerdev-3809-headset-check-2.png`

Results:
- RED/HOST: the first focused guard failed because it expected literal `alpha = 0.52f` / `alpha = 0.34f` strings, while production used `copy(alpha = if (control.enabled) 0.52f else 0.34f)`.
- FIX/ICON: added `composeApp/src/commonMain/valkyrieResources/outlined/ic_headset.svg` and switched `KomikkuWhispersyncPlaybackControl` from `Icons.Outlined.Audiobooks` to `Icons.Outlined.Headset`.
- FIX/VISUAL: the page-level control now renders only a low-opacity headset glyph plus the crossed state line when muted/paused; no `Surface`, `RoundedCornerShape`, `CircularProgressIndicator`, or Material background is allowed in that control body.
- GREEN/HOST: focused `ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary` passed.
- GREEN/WHITESPACE: `git diff --check` exited `0`.
- GREEN/EMULATOR: dirty readerdev installed on `emulator-5554`, launched the production book `3809` route, swiped from the cover into `Author's Foreword`, and `captures\reader-dev\readerdev-3809-headset-check-2.png` shows a bare low-opacity headset glyph in the top-left corner with no surrounding circle/ring.

## 2026-06-21 Whispersync Player Route Belongs To Page Headset

Scope:
- Remove the Navic-specific Whispersync audiobook shortcut from the Komikku bottom chrome.
- Keep the full Whispersync player dialog route available, but anchor it to the page-level headset control instead of growing the bottom action row.
- Preserve the user's requested short-tap behavior: tapping the headset toggles audiobook playback; long-pressing the headset opens the controller-owned player dialog.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomBarUsesKomikkuBottomButtonActionModel --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
.\gradlew.bat --stop
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomBarUsesKomikkuBottomButtonActionModel --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
```

Results:
- RED/HOST: the focused guard failed while `KomikkuReaderBottomBar` still accepted `showWhispersyncPlayer`, rendered `Icons.Outlined.Audiobooks`, and exposed `contentDescription = "Whispersync player"`.
- RED/HOST: the focused headset guard failed while `KomikkuWhispersyncPlaybackControl` had no `onOpenPlayer` route.
- FIX/CHROME: `KomikkuReaderBottomBar` now keeps the Komikku-supported bottom actions only: chapters, search, and settings. It no longer receives or renders Whispersync player state.
- FIX/HEADSET: `KomikkuWhispersyncPlaybackControl` now uses the bare headset as the single page-level audioebook affordance: short tap dispatches play/pause and long press opens the existing controller-owned `ReaderControllerDialog.WhispersyncPlayer`.
- ENV: the first green rerun hit a stale Kotlin compile-cache deletion lock left by the killed Gradle wrapper process; `.\gradlew.bat --stop` cleared the stale daemon before rerunning the same focused checks.
- GREEN/HOST: the focused Komikku bottom-bar guard and Whispersync headset-route guard passed after the production update.
- Remaining: this is host/source proof. Readerdev/emulator validation still needs to confirm short tap toggles playback and long press opens the player dialog on the real page-level headset surface.

## 2026-06-21 Readerdev Page-Level Whispersync Headset Runtime Probe

Scope:
- Validate the current readerdev build after the page-level Whispersync control was moved out of the bottom chrome.
- Confirm the affordance is only a low-opacity headset glyph in the top-left page area, with no circular button, ring, capsule, or progress container.
- Confirm short tap toggles the readaloud session, long press opens the controller-owned Whispersync player dialog, and the Komikku bottom bar keeps only its normal reader actions.

Commands:

```powershell
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev
adb -s emulator-5554 shell screencap -p /sdcard/navic-headset-live.png
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-headset-live.xml
adb -s emulator-5554 shell input tap 78 78
adb -s emulator-5554 shell dumpsys media_session
adb -s emulator-5554 shell input swipe 78 78 78 78 900
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-after-headset-longpress.xml
adb -s emulator-5554 shell input keyevent BACK
adb -s emulator-5554 shell input tap 900 1200
adb -s emulator-5554 shell screencap -p /sdcard/navic-bottom-chrome-visible.png
adb -s emulator-5554 shell uiautomator dump /sdcard/navic-bottom-chrome-visible.xml
```

Artifacts:
- `tmp\navic-headset-live.png`
- `tmp\navic-headset-live.xml`
- `tmp\navic-after-headset-short.xml`
- `tmp\navic-after-headset-longpress.xml`
- `tmp\navic-bottom-chrome-visible.png`
- `tmp\navic-bottom-chrome-visible.xml`

Results:
- GREEN/BUILD-EVIDENCE: emulator `emulator-5554` was running `darkaxt.navic.readerdev` `versionName=v1.0.11-eta78`, `versionCode=411`, `lastUpdateTime=2026-06-21 19:05:02`.
- GREEN/GLYPH: `tmp\navic-headset-live.png` shows only a small low-opacity headset glyph at the top-left of the page. The playback-control source still rejects `Surface`, `RoundedCornerShape`, `CircularProgressIndicator`, and Material background usage inside `KomikkuWhispersyncPlaybackControl`.
- GREEN/SHORT-TAP: tapping the top-left headset at roughly `(78, 78)` changed the accessibility description from `Play Whispersync audiobook` to `Pause Whispersync audiobook`; Android MediaSession `androidx.media3.session.id.navic-readaloud` moved to `PLAYING(3)` for `Bastille vs. the Evil Librarians`.
- GREEN/LONG-PRESS: long-pressing the same top-left glyph opened the controller-owned Whispersync player dialog. The hierarchy contained `Audio sync`, `Pause audiobook`, the audiobook metadata, and `Close`; the MediaSession remained `PLAYING(3)`.
- GREEN/BOTTOM-CHROME: after closing the player and showing reader chrome, `tmp\navic-bottom-chrome-visible.xml` exposed bottom actions `Chapters`, `Search`, and `Settings`. There was no bottom-bar `Whispersync player` or `Audio sync` shortcut; the only audiobook-related node remained the top-left headset glyph.
- Remaining: this is readerdev/emulator proof. A signed release-device pass is still required before claiming the physical phone release has the same no-circle headset treatment and playback behavior.

## 2026-06-21 Anx Default Typography Scale Alignment

Scope:
- Align Navic reader defaults with the Anx `BookStyle` readable prose baseline instead of the older compact Navic defaults.
- Move the default font scale from `100%` to `140%` and line height from `1.55` to `1.8`.
- Keep the defaults consistent across Kotlin reader state, persisted app-level ebook settings, in-reader settings UI, Settings search rows, WebView CSS, pagination profile hashing, and the ADB WebView font-size probe.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsPreserveAnxReadableProseScale
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-pagination-model.js
node --check tools\reader-harness\src\adb-webview-eval.mjs
git diff --check
```

Results:
- RED/HOST: the new `ReaderSettingsDefaultsTest.readerSettingsDefaultsPreserveAnxReadableProseScale` guard initially failed against the legacy `100%` / `1.55` defaults.
- FIX/STATE: `defaultReaderSettings`, `ReaderChromeState.adjustFontSize`, and preference fallback paths now use `DefaultReaderFontSizePercent = 140` and `DefaultReaderLineHeight = 1.8`.
- FIX/UI: the in-reader settings dialog, Settings > Ebooks rows, and Settings search fallbacks now show the same Anx-aligned defaults; the line-height selector includes `180%`.
- FIX/RUNTIME: the WebView typography CSS and pagination profile fallback now use the same readable default scale, and the ADB font-size probe restores to `140%` when no original value is available.
- GREEN/HOST: the focused Android host test report recorded `ReaderSettingsDefaultsTest` with `tests="1"`, `failures="0"`, including `readerSettingsDefaultsPreserveAnxReadableProseScale`.
- GREEN/JS: all edited reader runtime/probe JS files passed `node --check`.
- GREEN/WHITESPACE: `git diff --check` exited `0`.
- Remaining: existing installations with already-persisted custom reader values will continue honoring those values. A migration from legacy persisted `100/155` to `140/180` should be handled explicitly if we decide old saved defaults must be upgraded in place.

## 2026-06-21 Readerdev eta78 Komikku Matrix After Headset/Typography Updates

Scope:
- Verify the installed readerdev build after the page-level Whispersync headset and Anx typography-default changes.
- Confirm the headset control remains a bare top-left glyph with no circular container.
- Record current Komikku matrix pass/fail state before any further reader-shell work.

Commands:

```powershell
adb devices
adb -s emulator-5554 shell dumpsys package darkaxt.navic.readerdev
.\scripts\adb-reader-komikku-matrix.ps1 `
  -Package darkaxt.navic.readerdev `
  -DeviceSerial emulator-5554 `
  -ExpectedVersionName v1.0.11-eta78 `
  -NoLaunch `
  -IncludeCoverChecks `
  -ContinueOnFailure `
  -ArtifactRoot C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-bindery-cache-master\captures\reader-komikku-matrix\20260621-201932-eta78-headset
```

Artifacts:
- `captures\reader-komikku-matrix\20260621-201932-eta78-headset\reader-matrix-summary.csv`
- `captures\reader-komikku-matrix\20260621-201932-eta78-headset\reader-matrix-failures.txt`
- `captures\reader-komikku-matrix\20260621-201932-eta78-headset\baseline-current-reader\screen.png`

Results:
- GREEN/INSTALL: emulator `emulator-5554` was running `darkaxt.navic.readerdev` `versionName=v1.0.11-eta78`, `versionCode=411`, `lastUpdateTime=2026-06-21 19:05:02`.
- GREEN/HEADSET: source inspection of `KomikkuWhispersyncPlaybackControl` showed a transparent 48dp tap target with a 25dp low-opacity headset glyph and crossed-state slash only. The control body does not use `Surface`, `RoundedCornerShape`, `CircularProgressIndicator`, `IconButton`, `.background`, `.border`, or `.clip`.
- GREEN/VISUAL: `baseline-current-reader\screen.png` showed the top-left Whispersync affordance as only the small headset icon; no circle, ring, pill, or Material background was visible around it.
- GREEN/MATRIX: `baseline-current-reader`, `cover-center-tap-toggle`, `center-tap-toggle`, `native-long-press-center`, `drag-next`, and `drag-previous` passed on the installed eta78 readerdev build.
- FAIL/COVER-START: `baseline-native-cover` failed because the run started from readable content rather than the native shell cover. `cover-drag-next` also failed because no shell-cover swipe could be captured in that state.
- FAIL/TEXTURE-DIAGNOSTICS: `edge-tap-next`, `texture-next-walk`, `edge-tap-previous`, and `texture-previous-walk` failed because captured texture diagnostics did not include the expected `pos=` marker.
- OBSERVED/COMPOSITION: the baseline screenshot still shows unrelated EPUB composition problems on the Bastille test book, including visible word fragmentation and awkward line/page layout. This remains separate from the no-circle headset request.

## 2026-06-21 Readerdev eta78 Headset No-Circle Confirmation

Scope:
- Re-check the user-facing Whispersync page control after clarification that it must be only a headset icon with no circle around it.
- Distinguish the top-left headset from unrelated circular buttons in the right chapter rail.

Commands:

```powershell
adb devices
adb shell dumpsys package darkaxt.navic.readerdev
adb exec-out screencap -p > captures\reader-headset-current\screen.png
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
```

Artifacts:
- `captures\reader-headset-current\screen.png`

Results:
- GREEN/INSTALL: emulator `emulator-5554` is still running `darkaxt.navic.readerdev` `versionName=v1.0.11-eta78`, `versionCode=411`, `lastUpdateTime=2026-06-21 19:05:02`.
- GREEN/HOST-GUARD: the focused host guard passed and still rejects `Surface`, `RoundedCornerShape`, `CircularProgressIndicator`, `IconButton`, `.background`, `.border`, `.clip`, and Material background usage inside `KomikkuWhispersyncPlaybackControl`.
- GREEN/VISUAL: `captures\reader-headset-current\screen.png` shows the Whispersync affordance as a bare low-opacity headset icon at the top-left of the page. There is no circle, ring, pill, or visible tap container around the headset.
- NOTE: the visible circular controls in the same screenshot belong to the right-side chapter rail, not to the Whispersync headset control.

## 2026-06-21 Headset No-Circle Recheck And Bastille Line-Fragment Dirty Validation

Scope:
- Re-check the clarified Whispersync headset requirement: the page-level audioebook affordance must be just the headset glyph, with no circular container.
- Validate the EPUB line-fragment normalizer against the real Bastille EPUB that was visibly splitting source/OCR line fragments into separate paragraphs.
- Record the validation blocker where the native cover overlay stayed visible even after WebView navigation.

Commands:

```powershell
adb devices
adb exec-out screencap -p > captures\reader-headset-latest\screen.png
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary
node tools\reader-harness\src\run-reader-harness.mjs --mode line-fragment-prose-smoke
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
.\scripts\install-reader-dev.ps1 -NoLaunch -NoDiscoverPublication -DeviceSerial emulator-5554
adb -s emulator-5554 shell am start -S -n darkaxt.navic.readerdev/paige.navic.androidApp.MainActivity ...
node tools\reader-harness\src\adb-webview-eval.mjs --device emulator-5554 --package darkaxt.navic.readerdev --probe chapter-progress-endpoints
```

Artifacts:
- `captures\reader-headset-latest\screen.png`
- `captures\reader-line-fragment-dirty\screen-after-launch.png`
- `captures\reader-line-fragment-dirty\screen-after-cover-taps.png`
- `captures\reader-line-fragment-dirty\screen-after-chapter-progress.png`
- `tmp\reader-headset-guard.out.log`
- `tmp\reader-line-fragment-host.out.log`
- `tmp\readerdev-install-line-fragment.out.log`
- `tmp\chapter-progress-endpoints.out.log`

Results:
- GREEN/HEADSET-SOURCE: `KomikkuWhispersyncPlaybackControl` still renders a transparent 48dp tap area with `Icons.Outlined.Headset`, a 25dp glyph, and optional crossed-state slash only. It does not render a `Surface`, `IconButton`, `RoundedCornerShape`, `CircularProgressIndicator`, background, border, clip, pill, ring, or visible tap container.
- GREEN/HEADSET-VISUAL: `captures\reader-headset-latest\screen.png` again shows the top-left Whispersync affordance as only the bare headset glyph. The visible round controls in the image are the right-side chapter rail, not the headset.
- GREEN/HEADSET-GUARD: `ReaderKomikkuBackboneResetTest.readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary` passed against the current source.
- RED/HARNESS: the new `line-fragment-prose-smoke` harness initially failed before implementation because `normalizeReaderLineFragmentParagraphs` did not exist.
- GREEN/HARNESS: `line-fragment-prose-smoke` passed after adding the normalizer. The synthetic fixture merges split-word visual lines such as `th` + `e`, `extra-spe` + `cial`, and `wishe` + `d`, while preserving source-leading whitespace where it represents a real word gap.
- GREEN/RUNTIME-WIRING: `ReaderRuntimeSettingsBridgeTest.androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes` passed after adding a host guard that requires `normalizeReaderLineFragmentParagraphs(doc, settings)` to run before `applyReaderParagraphSpacing(doc, settings)`.
- GREEN/JS: edited reader runtime and harness files passed `node --check`; `font-css-smoke` still passed; `git diff --check` was clean.
- GREEN/DIRTY-INSTALL: `scripts\install-reader-dev.ps1 -NoLaunch -NoDiscoverPublication -DeviceSerial emulator-5554` built and installed a dirty readerDev APK successfully, including the changed reader assets.
- GREEN/REAL-EPUB-DOM: DevTools inspection of the dirty readerDev WebView after jumping to `OEBPS/xhtml/chapter4.xhtml` showed the real EPUB content document with `data-navic-line-fragments-normalized="true"` and merged paragraphs:
  - `If you remember... I didn't even know him...`
  - `The point is... I'm not the warmest cinnamon roll... extra-special effort...`
  - `All my life, I'd wished to be an Oculator... with enhanced skills...`
- BLOCKED/VISUAL: screenshot validation of the fixed text page is still blocked because the native cover overlay remained visible after direct WebView navigation and did not dismiss after two right-edge taps. The WebView document was correctly navigated and normalized behind it, but `screen-after-chapter-progress.png` still shows the native cover.
- FOLLOW-UP: treat the stuck native cover overlay as a separate cover-shell/controller issue. Do not weaken the line-fragment normalizer to work around the cover overlay.

## 2026-06-21 Controller-Owned Cover Dismissal Red/Green

Scope:
- Fix the cover-shell/controller issue found during Bastille line-fragment validation: the native cover overlay could remain mounted above real EPUB content after an explicit engine navigation moved the WebView underneath it.
- Preserve the intended startup behavior where passive/default relocation does not immediately dismiss the native cover.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.explicitReadableRelocationDismissesControllerOwnedShellCover"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.explicitReadableRelocationDismissesControllerOwnedShellCover" --tests "paige.navic.reader.ReaderControllerTest.startupReadableRelocationDoesNotDismissControllerOwnedShellCover"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundary*"
git diff --check
```

Artifacts:
- `build\codex-logs\reader-controller-shell-cover-red.out.log`
- `build\codex-logs\reader-controller-shell-cover-green.out.log`
- `build\codex-logs\reader-controller-shell-cover-broad.out.log`

Results:
- RED/HOST-GUARD: `ReaderControllerTest.explicitReadableRelocationDismissesControllerOwnedShellCover` failed before implementation at `ReaderControllerTest.kt:117`; `shellCoverVisible` stayed `true` after an explicit `chapter-progress-seek` relocation to readable content.
- GREEN/FIXED: the same scenario now clears `shellCoverVisible` and `menuVisible`, while `startupReadableRelocationDoesNotDismissControllerOwnedShellCover` keeps the shell cover mounted for passive `relocate-committed` startup relocation.
- GREEN/BROAD: full `ReaderControllerTest` plus `ReaderChromeStateTest.nativeShellCoverBoundary*` passed, preserving previous-from-first-readable-page native-cover return behavior.
- GREEN/WHITESPACE: `git diff --check` passed.
- NOTE: this is host/controller validation. The next dirty readerDev/emulator validation should verify that TOC/progress/Whispersync chapter navigation no longer leaves the native cover visually mounted over the WebView.

## 2026-06-21 Controller-Owned Cover Dismissal Dirty Emulator Validation

Scope:
- Validate the controller-owned cover dismissal fix on the emulator after installing a dirty readerDev APK.
- Confirm explicit chapter-progress navigation reaches visible EPUB content instead of leaving the native cover mounted above the WebView.

Commands:

```powershell
.\scripts\install-reader-dev.ps1 -NoLaunch -NoDiscoverPublication -DeviceSerial emulator-5554
.\scripts\install-reader-dev.ps1 -NoBuild -NoInstall -DeviceSerial emulator-5554 -RequireReaderLaunch
node tools\reader-harness\src\adb-webview-eval.mjs --device emulator-5554 --probe runtime-state
node tools\reader-harness\src\adb-webview-eval.mjs --device emulator-5554 --probe chapter-progress-endpoints
adb -s emulator-5554 shell screencap -p /sdcard/navic-cover-dismiss-probe-after.png
adb -s emulator-5554 pull /sdcard/navic-cover-dismiss-probe-after.png captures\reader-cover-dismiss-dirty\probe-after.png
```

Artifacts:
- `build\codex-logs\readerdev-install-cover-dismiss.out.log`
- `build\codex-logs\readerdev-install-cover-dismiss.err.log`
- `build\codex-logs\chapter-progress-endpoints-cover-dismiss.out.log`
- `build\codex-logs\chapter-progress-endpoints-cover-dismiss.err.log`
- `captures\reader-cover-dismiss-dirty\before.png`
- `captures\reader-cover-dismiss-dirty\after.png`
- `captures\reader-cover-dismiss-dirty\probe-after.png`

Results:
- GREEN/DIRTY-INSTALL: `scripts\install-reader-dev.ps1 -NoLaunch -NoDiscoverPublication -DeviceSerial emulator-5554` built and installed readerDev successfully.
- GREEN/DEVTOOLS: `runtime-state` connected to `darkaxt.navic.readerdev` WebView `https://appassets.androidplatform.net/assets/reader/index.html`; the runtime reported `flowMode=paged`, `rendererFlow=paginated`, viewport `1232x1974`, and one content document.
- GREEN/CHAPTER-ENDPOINTS: `chapter-progress-endpoints` selected `OEBPS/Text/Chapter-37.xhtml` with `chapterPageCount=82`; the endpoint probe reached page `461 / 623` at chapter progress `0` and page `542 / 623` at chapter progress `1`.
- GREEN/VISUAL: `captures\reader-cover-dismiss-dirty\probe-after.png` shows readable EPUB content at `543 / 623`, not the native cover. This confirms the explicit navigation path no longer leaves the native cover visually mounted over the WebView in the dirty emulator build.
- NOTE: the foreground launch command hit the shell tool's foreground ceiling while waiting for `publicationReady`; the app had already launched and was then validated through DevTools and screenshots. Future long readerDev launches should use the hidden captured-process pattern used for Gradle and DevTools probes.

## 2026-06-21 Komikku Effective Nav-Bar Guard

Scope:
- Match Komikku's rule that vertical seekbars belong to vertical viewer modes only. Normal paged EPUB must use the bottom navigator even if the stored nav-bar preference is vertical.
- Replace the failed foreground/`Start-Process` launch pattern with a hidden captured-process pattern for long Gradle validation.

Commands:

```powershell
# RED and GREEN were both run through a hidden ProcessStartInfo wrapper with CreateNoWindow=true,
# stdout/stderr redirected to build\codex-logs, and PID files beside the logs.
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.komikkuVerticalRailIsEffectiveOnlyForVerticalViewerModes"
.\gradlew.bat --no-daemon --no-parallel "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest"
git diff --check
```

Artifacts:
- `build\codex-logs\komikku-effective-nav-red.out.log`
- `build\codex-logs\komikku-effective-nav-green.out.log`
- `build\codex-logs\reader-viewer-tests.out.log`
- `build\codex-logs\reader-viewer-tests-rerun.out.log`

Results:
- RED/HOST-GUARD: `ReaderViewerTest.komikkuVerticalRailIsEffectiveOnlyForVerticalViewerModes` failed before production code because `readerEffectiveNavBarTypeFor` did not exist.
- GREEN/FIXED: `readerEffectiveNavBarTypeFor` now resolves the effective chrome from the active viewer mode: paged readers force `bottom`, while scrolled/webtoon and vertical-paged readers can use the stored vertical-left/right preference.
- GREEN/CHROME: `KomikkuReaderAppBars` now consumes the effective nav-bar type instead of the raw stored setting, preventing the full-height vertical rail from appearing in normal paged EPUB.
- GREEN/BROAD: `ReaderViewerTest` passed after updating a stale Whispersync source-inspection guard to the current root-owned headset overlay route (`KomikkuWhispersyncPlaybackControl` -> `onOpenPlayer = onWhispersyncPlayer` -> `KomikkuWhispersyncPlayerDialog`).
- PROCESS: the working hidden wrapper pattern is `cmd.exe /d /c call "<repo>\gradlew.bat" ... > "<out.log>" 2> "<err.log>"`, launched with `ProcessStartInfo.UseShellExecute=false` and `CreateNoWindow=true`. Do not use foreground shells or `Start-Process` directly on `.bat` files for long reader validation.

## 2026-06-21 App Back Navigation Policy

Scope:
- Validate GLM's back-navigation report against source and fix the basic app experience before continuing reader/Whispersync integrations.
- Unify Android system back, visible top-left back affordances, reader back, and bottom-tab switching around a single policy.

Diagnosis:
- CONFIRMED: `App.kt` system back removed the last stack entry whenever the stack was non-empty, so a detail screen opened as root could drain the app to Android home.
- CONFIRMED: `BottomBar.kt` tab clicks cleared the whole stack and added only the selected root destination, so returning from a tab switch discarded the previous book/collection/detail context.
- CONFIRMED: `NestedTopBar.kt` used a stricter local rule (`backStack.size > 1`) than system back and also hid several settings arrows on medium+ width.
- CONFIRMED: `ReaderScreen.kt` used the same local `backStack.size > 1` rule, so a reader launched as root could not fall back to its owning Bindery book.
- CONFIRMED: `AndroidManifest.xml` did not opt into Android's predictive back callback.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.ui.navigation.NavBackPolicyTest"
git diff --check
.\gradlew.bat --no-daemon :androidApp:assembleDebug
```

Artifacts:
- `tmp\codex-logs\navback-policy-red2.out.log`
- `tmp\codex-logs\navback-policy-green.out.log`
- `tmp\codex-logs\navback-policy-green2.out.log`
- `tmp\codex-logs\navback-full-host.out.log`
- `tmp\codex-logs\navback-assemble-debug.out.log`

Results:
- RED/HOST-GUARD: `NavBackPolicyTest` failed before implementation because `NavBackAction`, `navBackActionFor`, and `navBackStackAfterTabSelection` did not exist.
- GREEN/POLICY: the first focused rerun passed after adding `NavBackPolicy.kt` and wiring `App.kt`, `NestedTopBar.kt`, `RootTopBar.kt`, `BottomBar.kt`, and `ReaderScreen.kt` through the shared policy.
- FIXED: root-level detail screens now fall back to useful roots instead of exiting: reader -> owning `BinderyBook`, `BinderyBook` -> `BinderyBooks`, collection -> `BinderyCollections`, author -> `BinderyAuthors`, settings details -> `Settings.Root`.
- FIXED: bottom-tab selection appends the destination when it changes instead of clearing history, preserving the previous window for back navigation.
- FIXED: visible root/detail back affordances now use the same policy as system back, and nested bars can still show a back affordance when the policy has a fallback even if a medium-width screen requested `hideBack`.
- FIXED: Android manifest opts into `android:enableOnBackInvokedCallback="true"`.
- GREEN/RERUN: the second focused rerun passed after adding the root-back-affordance assertion (`BUILD SUCCESSFUL in 3m 10s`).
- GREEN/WHITESPACE: `git diff --check` passed.
- GREEN/APK-COMPILE: `:androidApp:assembleDebug` passed (`BUILD SUCCESSFUL in 2m 38s`), including the manifest change.
- RED/FULL-HOST-RESIDUAL: full `:composeApp:testAndroidHostTest` failed with three reader-only assertions outside this change's files: `ReaderChromeStateTest.typographyControlsUpdateReaderSettings`, `ReaderRuntimeCommonChromeTest.commonReaderProgressRailPlacementIsControllerSettingNotHardcoded`, and `ReaderRuntimeNavigationFlowTest.androidReaderMapsExplicitReadingFlowModesToFoliateRuntime`.
- GREEN/RESIDUAL-TARGETED: `:composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderChromeStateTest.typographyControlsUpdateReaderSettings" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderProgressRailPlacementIsControllerSettingNotHardcoded" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest.androidReaderMapsExplicitReadingFlowModesToFoliateRuntime"` passed after updating stale guards to the current adaptive-reader defaults, effective nav-bar helper, and extracted `EbookReaderSettingOptions.kt` flow-option source.
- GREEN/FULL-HOST-RERUN: full `:composeApp:testAndroidHostTest` passed (`BUILD SUCCESSFUL in 26s`).
- GREEN/WHITESPACE-RERUN: `git diff --check` passed after the residual test-maintenance patch.
- ARTIFACTS: `tmp\codex-logs\reader-host-targeted-after-navback.out.log`, `tmp\codex-logs\reader-host-full-after-navback.out.log`.

## 2026-06-22 PullUp Does Not Implicitly Surface Chrome

Scope:
- Address the user-reported vertical-drag regression where moving to the next chapter can surface the menu without an explicit center/menu action.
- Preserve Anx `PullUp` bridge parity while keeping Komikku shell ownership of reader chrome visibility.

Diagnosis:
- CONFIRMED: `ReaderController` routed every source-less `ReaderEngineEvent.PullUp()` to `menuVisible=true`.
- CONFIRMED: the runtime's intentional scrolled-edge path posts `pullUp` with `source='scrolled-edge-swipe'`, and the controller already preserved menu state for that source.
- ROOT CAUSE: ambiguous/default pull-up events were treated as hidden menu commands instead of bridge events feeding controller overlay state.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.ambiguousPullUpRecordsBridgeParityWithoutOpeningReaderMenu"
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.FoliateAnxParityTest"
```

Artifacts:
- `tmp\codex-logs\pullup-ambiguous-red.out.log`
- `tmp\codex-logs\pullup-ambiguous-green.out.log`
- `tmp\codex-logs\pullup-controller-parity.out.log`

Results:
- RED/HOST-GUARD: `ReaderControllerTest.ambiguousPullUpRecordsBridgeParityWithoutOpeningReaderMenu` failed before implementation because source-less `PullUp()` forced `menuVisible=true`.
- FIXED: `ReaderEngineEvent.PullUp` now records `ReaderOverlayInteraction.PullUp` and preserves the existing menu state instead of opening chrome.
- FIXED: `FoliateAnxParityTest` now requires the `PullUp` route to preserve controller menu state, preventing future reintroduction of hidden chrome toggling.
- GREEN/FOCUSED: `ReaderControllerTest.ambiguousPullUpRecordsBridgeParityWithoutOpeningReaderMenu`, `anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded`, `scrolledEdgePullUpRecordsBridgeParityWithoutOpeningReaderMenu`, and `FoliateAnxParityTest.everyAnxHandlerIsDocumentedInKnownGaps` passed.
- GREEN/BROAD: full `ReaderControllerTest` and `FoliateAnxParityTest` passed together (`BUILD SUCCESSFUL in 16s`).
- NOTE: no ADB device was connected for a live vertical-drag smoke; this closes the controller-level cause but still needs emulator/device confirmation in the next readerdev matrix.

## 2026-06-22 Boundary Drag Paper Fallback

Scope:
- Address the drag-preview black void at EPUB section/chapter boundaries without changing the tap-zone or release-navigation paths.
- Keep the current page moving during the gesture, but ensure the exposed adjacent area is paper-colored while the next section iframe is still loading.

Diagnosis:
- CONFIRMED: `updatePageDragPreviewLayer` kept the active renderer moving during boundary drag, but hid the adjacent preview layer when `pageDragPreviewReadyKey` did not match the target section.
- ROOT CAUSE: the not-ready branch collapsed the preview surface to a transparent offscreen 1px layer (`left='-1px'`, `width='1px'`, `opacity='0'`), exposing the native/root background until the adjacent iframe finished loading.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderShowsPaperFallbackWhileBoundaryPreviewLoads"
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack"
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
git diff --check
```

Artifacts:
- `tmp\codex-logs\drag-preview-fallback-red2.out.log`
- `tmp\codex-logs\drag-preview-fallback-green2.out.log`
- `tmp\codex-logs\drag-preview-fallback-broad.out.log`

Results:
- RED/HOST-GUARD: `ReaderRuntimePaperSurfaceTest.androidReaderShowsPaperFallbackWhileBoundaryPreviewLoads` failed before implementation because the runtime did not mark or display any paper fallback in the not-ready boundary preview branch.
- FIXED: the not-ready branch now sizes the preview layer to the actually exposed drag area, keeps it visible with the reader paper palette, and records `navicPageDragPreviewFallback='paper'` plus exposed dimensions for diagnostics.
- FIXED: the ready branch explicitly clears fallback state and restores iframe opacity, so the real adjacent preview replaces the paper fallback as soon as it is available.
- GREEN/FOCUSED: `ReaderRuntimePaperSurfaceTest.androidReaderShowsPaperFallbackWhileBoundaryPreviewLoads` passed (`BUILD SUCCESSFUL in 14s`).
- GREEN/BROAD: `ReaderRuntimePaperSurfaceTest` plus the existing Komikku readable-drag guard passed (`BUILD SUCCESSFUL in 23s`).
- GREEN/JS: `node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js` passed.
- GREEN/WHITESPACE: `git diff --check` passed.
- NOTE: no ADB device was connected for a live chapter-boundary drag; this closes the renderer fallback cause, but the next readerdev matrix still needs a real gesture pass.

## 2026-06-22 Whispersync Icon Compose Vector Crash

Scope:
- Fix the CrashActivity failure on opening a Whispersync-capable Bindery book detail screen.
- Keep the fix to the resource-parser root cause before resuming launcher work.

Diagnosis:
- CONFIRMED: the emulator crash stack reported `java.lang.IllegalArgumentException: Invalid color value @android:color/transparent` from Compose's vector parser while loading `Res.drawable.ic_whispersync` in `BinderyBookScreen`.
- ROOT CAUSE: `composeResources/drawable/ic_whispersync.xml` used framework color references (`@android:color/transparent`) inside a common Compose resource. The Compose resource parser accepts literal color values here, not Android framework color resource references.

Commands:

```powershell
rg "@android:color/transparent" composeApp\src\commonMain\composeResources\drawable
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests "paige.navic.ui.components.common.ComposeVectorResourceSourceTest.composeVectorDrawablesUseLiteralTransparentColors"
git diff --check -- composeApp\src\commonMain\composeResources\drawable\ic_whispersync.xml composeApp\src\androidHostTest\kotlin\paige\navic\ui\components\common\ComposeVectorResourceSourceTest.kt
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell input tap 925 462
adb -s emulator-5554 logcat -d -v brief
```

Artifacts:
- `captures\reader-dev\navic-vector-before.png`
- `captures\reader-dev\navic-vector-after-card.png`
- Verification worktree guard log: `C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-vector-crash-verify\tmp\codex-logs\vector-crash-verify-install.out.log`

Results:
- FIXED: all `@android:color/transparent` fills in `ic_whispersync.xml` were replaced with literal `#00000000`.
- GUARD: `ComposeVectorResourceSourceTest.composeVectorDrawablesUseLiteralTransparentColors` now scans common Compose drawables and fails if this parser-incompatible value returns.
- GREEN/HOST-GUARD: the focused guard passed in the clean verification worktree (`BUILD SUCCESSFUL in 5m 6s`, `GuardExitCode=0`).
- GREEN/INSTALL: the patched readerDev APK installed on `emulator-5554`.
- GREEN/EMULATOR: after dismissing the update sheet and tapping the visible `Bastille vs. the Evil Librarians` card, the book detail screen rendered with Whispersync icons instead of CrashActivity.
- GREEN/LOGCAT: the focused logcat check after the tap contained `Bindery book loaded bookId=3809 ... syncPairs=1`, and no `Invalid color value`, `XmlVectorParser`, `CrashActivity`, or app `FATAL EXCEPTION` entry for the repro path.
- NEXT: fix the readerdev launcher so this route can be opened deterministically without manual tap coordinates or null intent extras.

## 2026-06-22 Readerdev Launcher Explicit Target Overrides

Scope:
- Replace manual `am start` reader launches with a deterministic `install-reader-dev.ps1` path.
- Avoid env-file edits when a validation run needs a specific EPUB/PDF, start location, or Whispersync sidecar target.

Diagnosis:
- CONFIRMED: the current `bindery-debug.env` has Bindery credentials but no nonblank `BINDERY_TEST_PUBLICATION_ID` / `BINDERY_TEST_RESOURCE_ID` direct target.
- CONFIRMED: a manual direct launch with blank values can shift `am start --es` arguments and fall back to the Books grid instead of opening the reader.
- ROOT CAUSE: `install-reader-dev.ps1` could discover from Bindery or read env-driven targets, but had no explicit CLI reader-target overrides for validation fixtures.

Commands:

```powershell
.\gradlew.bat --no-daemon --no-parallel :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderDevEnvironmentContractTest.readerDevScriptsAndSecretFilesAreDocumentedAndIgnored"
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -NoBuild -NoInstall -NoDiscoverPublication -RequireReaderLaunch -PublicationUrl "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -ResourceHref "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -BookId "3809" -Title "Bastille vs. the Evil Librarians" -Format epub -Capture
git diff --check -- scripts\install-reader-dev.ps1 composeApp\src\androidHostTest\kotlin\paige\navic\reader\ReaderDevEnvironmentContractTest.kt
```

Artifacts:
- `tmp\codex-logs\launcher-contract-red.out.log`
- `tmp\codex-logs\launcher-contract-green.out.log`
- `tmp\codex-logs\launcher-direct-bastille.out.log`
- `captures\reader-dev\reader-dev-20260622-013122.png`

Results:
- RED/HOST-GUARD: `ReaderDevEnvironmentContractTest.readerDevScriptsAndSecretFilesAreDocumentedAndIgnored` failed before implementation because the launcher had no explicit reader-target CLI overrides.
- FIXED: `install-reader-dev.ps1` now accepts explicit aliases for `-PublicationUrl`, `-ResourceHref`, `-BookId`, `-Title`, `-Kind`, `-Format`, `-StartHref`, `-StartCfi`, and Whispersync sidecar/audiobook fields.
- FIXED: explicit CLI values override env-file values, then flow through the existing null-safe `Add-ShellStringExtra` path so blank values are not emitted as shifted `am start` arguments.
- GREEN/HOST-GUARD: the focused launcher contract passed after the script change (`BUILD SUCCESSFUL in 14s`).
- GREEN/LAUNCHER: the no-build/no-install launcher run opened `darkaxt.navic.readerdev` directly into production book `3809` using file `426`, waited for `Reader bridge raw: {"type":"publicationReady"}`, and pulled a rendered reader screenshot.
- GREEN/LOGCAT: the direct launch produced real reader bridge data (`loadDoc`, cached pagination profile, `locationChanged`, `visibleTextRange`, `publicationReady`) for `OEBPS/xhtml/chapter4.xhtml`.
- GREEN/WHITESPACE: `git diff --check` passed for the launcher and contract-test changes.
- GREEN/COMBINED-GUARD: the focused rerun covering both `ComposeVectorResourceSourceTest.composeVectorDrawablesUseLiteralTransparentColors` and `ReaderDevEnvironmentContractTest.readerDevScriptsAndSecretFilesAreDocumentedAndIgnored` passed together (`BUILD SUCCESSFUL in 4m 7s`).

## 2026-06-22 Publisher Important Font-Size Ownership

Scope:
- Address the reported reader Font size behavior where headings/titles can react while body text appears pinned.
- Keep the fix in the Foliate document normalization path, not in Komikku shell UI.

Diagnosis:
- CONFIRMED: the existing isolated font CSS smoke handled simple publisher classes and inline font-size cases.
- CONFIRMED: the old normalizer only rewrote elements with inline `font-size` / `font` declarations or `<font size>`.
- ROOT CAUSE: EPUB publisher CSS with higher specificity and `!important` can beat the reader `p { font-size: 1rem !important }` stylesheet, pinning body prose while root/headings still scale.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\adb-webview-eval.mjs
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest
git diff --check
```

Results:
- RED/BROWSER-HARNESS: after adding a high-specificity `.publisher-important-wrapper ... { font-size: 10px !important; }` case, `font-css-smoke` failed as expected with `observed 10px -> 10px`.
- FIXED: `normalizeReaderInlineTypography` now gives prose elements relative inline ownership (`1rem` for block prose, `1em` for inline prose), not only elements that already had inline font-size declarations.
- FIXED: the normalizer records `data-navic-had-inline-font-size` for diagnostics, so rewritten class-based prose can still be distinguished from original inline-font prose.
- GUARD: the browser harness now covers class-important body prose, and the ADB WebView `font-size-publisher-styles` probe now checks the same failure mode on a live reader WebView.
- GREEN/BROWSER-HARNESS: `font-css-smoke` passed after the runtime change.
- GREEN/JS: all touched JS files passed `node --check`.
- GREEN/HOST-GUARD: `ReaderRuntimeAssetsTest` plus `ReaderRuntimeSettingsBridgeTest` passed (`BUILD SUCCESSFUL in 34s`).
- GREEN/WHITESPACE: `git diff --check` passed.
- NOTE: emulator probe before this source change showed current simple body prose scaling correctly (`16px -> 22.4px`), so this fix targets the stronger publisher cascade case. It still needs a rebuilt readerdev APK before the new ADB class-important probe can validate on device.

## 2026-06-22 Komikku Multi-Page Rail Visibility

Scope:
- Align Navic's chapter progress rail visibility with Komikku's `ChapterNavigator`: show the slider for any section with more than one page.
- Keep this as a narrow source-level parity fix; do not refactor the custom vertical rail input mapper in this slice.

Diagnosis:
- CONFIRMED: the Komikku reference uses `totalPages > 1` for both horizontal and vertical chapter sliders.
- CONFIRMED: Navic had drifted to `totalPages > 2`, hiding the chapter progress slider for 2-page sections.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile
git diff --check
```

Results:
- RED/HOST-GUARD: `ReaderChapterNavigatorMappingTest.chapterProgressSliderFollowsKomikkuVisibilityRule` failed before the production change because `totalPages = 2` returned false.
- FIXED: `readerShouldShowChapterProgressSlider(totalPages)` now returns true for `totalPages > 1`.
- GUARD: `ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile` now locks the Komikku threshold in the dedicated reader navigator source file.
- GREEN/HOST-GUARD: the focused mapping test plus Android source guard passed (`BUILD SUCCESSFUL in 5m 51s`).
- GREEN/WHITESPACE: `git diff --check` passed.
- NOTE: this was not device/emulator validated because the behavior is a source-level rail visibility threshold. The separate custom vertical rail input mapper still needs a dedicated Komikku parity pass.

## 2026-06-22 Komikku Vertical Rail Input Ownership

Scope:
- Remove Navic's custom vertical rail pointer/tap/drag mapper and return rail input ownership to the shared slider primitive.
- Keep this as a Komikku-parity correction; do not change chapter/page math in this slice.

Diagnosis:
- CONFIRMED: the vertical rail still had custom `pointerInput`, `detectTapGestures`, `detectDragGestures`, and canvas hit-mapping code after the Komikku split.
- ROOT CAUSE: the rail looked closer to Komikku visually, but the input path was still Navic-specific and could diverge from the reference behavior.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.reader.ReaderChapterNavigatorMappingTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile
git diff --check
```

Artifacts:
- `tmp\reader-vertical-rail-green.out.log`

Results:
- FIXED: the vertical rail no longer owns custom pointer input; it uses the rotated shared slider path instead.
- GUARD: source checks now reject `pointerInput`, `detectTapGestures`, `detectDragGestures`, custom rail offset mapping, and canvas rail rendering in the vertical rail component.
- GREEN/HOST-GUARD: the focused rail mapping/source guard passed (`BUILD SUCCESSFUL in 6m 1s`).
- GREEN/WHITESPACE: `git diff --check` passed after the later Aurral slice.
- Remaining: physical release validation is still required for rail feel on the user's devices.

## 2026-06-22 Aurral-First Artwork Authority

Scope:
- Enforce the user's current artwork rule: when Aurral is enabled, Aurral has priority everywhere and Navidrome is fallback only.
- Close the regression where artist surfaces could briefly or permanently keep Navidrome artwork even after Aurral cache data arrived.

Diagnosis:
- CONFIRMED: existing policy paths still honored `NativeFirst`/`NativeOnly` even when Aurral was enabled.
- CONFIRMED: Android now-playing and Media3 metadata paths were using Navidrome-first helpers or raw `coverArtId` data instead of the shared resolved playback artwork.
- ROOT CAUSE: Aurral fallback policy was split across UI, playback, Android notification/broadcast, and shortcut paths; not every path passed through the same resolver.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroid
git diff --check
rg "activeArtworkUrl\(|externalFallbackArtworkUrl\(" composeApp\src\androidMain\kotlin composeApp\src\commonMain\kotlin -g "*.kt"
```

Artifacts:
- RED: `tmp\aurral-first-red.out.log`
- GREEN: `tmp\aurral-first-green.out.log`

Results:
- RED/HOST-GUARD: `PlaybackArtworkPolicyTest.resolvedPlaybackArtworkForcesAurralFirstWhenAurralIsEnabled`, `ArtistDetailLayoutPolicyTest.headingOnlyUsesVerifiedExternalImageUrl`, `ArtistDetailLayoutPolicyTest.headingUsesPersistentArtistPhotoCacheWhenAurralIsEnabledEvenIfNativeIsFirst`, and `MostPlayedShortcutArtworkPolicyTest.artistShortcutsPreferAurralWhenAurralIsEnabledEvenIfNativeIsFirst` failed before implementation.
- FIXED: `effectiveArtworkSourcePriority(...)` now normalizes any selected artwork priority to `AurralFirst` whenever Aurral is enabled.
- FIXED: playback artwork, artist detail heading/cache, and most-played shortcuts use that effective priority, so Navidrome cover art cannot beat Aurral while Aurral is enabled.
- FIXED: Android Media3 metadata and now-playing broadcast now resolve through `AndroidPlaybackArtworkResolver`, so notification/system artwork follows the same Aurral-first policy.
- FIXED: the Android playback resolver observes the persistent artist photo cache and forces a now-playing refresh when late Aurral cache entries arrive, addressing the stale Jason Ross/IU-style path.
- GREEN/BROAD: `.\gradlew.bat --no-daemon :composeApp:testAndroid` passed (`BUILD SUCCESSFUL in 5m 40s`).
- GREEN/WHITESPACE: `git diff --check` passed.
- GREEN/SOURCE: `rg "activeArtworkUrl\(|externalFallbackArtworkUrl\(" composeApp\src\androidMain\kotlin composeApp\src\commonMain\kotlin -g "*.kt"` now finds only the helper definitions in `PlaybackArtworkPolicy.kt`, not Android/UI usage.
- Remaining: emulator/phone validation should still confirm the visible now-playing, artist, shortcut, Android notification, and lock-screen artwork all switch to Aurral after cache refresh.

## 2026-06-22 Komikku Viewer Lifecycle Source Authority

Scope:
- Lock the Navic `ReaderViewer` lifecycle boundary to explicit Komikku source files instead of undocumented local invention.
- This is a source-authority guard only; it does not change runtime viewer behavior.

Diagnosis:
- CONFIRMED: `ReaderViewer.kt` already had a retained viewer slot and renderer descriptor boundary, but it did not cite Komikku's `ReaderActivity.updateViewer()` or `Viewer.kt` lifecycle contract.
- RISK: without source citations, future work could keep the shape while drifting away from Komikku's create/destroy/mount contract.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerViewerLifecycleCitesKomikkuUpdateViewerAndViewerContract
git diff --check
```

Artifacts:
- RED: `tmp\reader-viewer-citation-red2.out.log`
- GREEN: `tmp\reader-viewer-citation-green.out.log`

Results:
- RED/HOST-GUARD: `ReaderKomikkuBackboneResetTest.readerViewerLifecycleCitesKomikkuUpdateViewerAndViewerContract` failed before the production citation because `ReaderViewer.kt` did not cite Komikku's viewer lifecycle sources.
- FIXED: `ReaderViewer.kt` now cites `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt updateViewer()` and `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt`.
- GREEN/HOST-GUARD: the focused citation guard passed (`BUILD SUCCESSFUL in 5m 3s`).
- GREEN/WHITESPACE: `git diff --check` passed.
- Remaining: host/source validation only. Tablet visual validation should confirm perceived body text scaling across normal text pages and generated/typewriter-style pages before a release claim.


## 2026-06-22 Reader Spec Stale-Item Cleanup

Scope:
- Reconcile `2026-06-13-komikku-reader-port-design.md` with already-recorded guard and emulator evidence so future compactions do not revive closed implementation items.

Evidence used:
- 2026-06-21 boundary-preview validation: host guard, browser harness, readerdev matrix, and `texture-offset-logic` all passed after the section-boundary loading fallback kept the current page moving instead of showing a black void.
- 2026-06-22 reader host guard refresh: focused reader guards and full `:composeApp:testAndroidHostTest` passed after WebView debugging and tap-zone visibility moved to Developer Options and Settings search routed them there.

Result:
- The active spec now treats the drag-preview black-void fallback and developer diagnostics placement as host/emulator-closed.
- Remaining work is still explicit: physical/release drag-feel validation, texture transition polish, settings overlay density, paper texture visibility, and lower-priority page-curl exploration.

Commands:

```powershell
git diff --check -- docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md
```

Results:
- GREEN/WHITESPACE: the focused spec diff check passed.


## 2026-06-22 Komikku Custom-Filter Dim Amount Parity

Scope:
- Match Komikku's reader settings dialog behavior for the custom-filter tab: normal settings tabs keep the modal dim at `0.5f`, while the custom-filter tab sets dialog dim to `0f` so the filter can be adjusted against the live reader surface.

Commands:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogMatchesKomikkuCustomFilterDimAmount
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest --tests paige.navic.reader.ReaderRuntimeNavigationFlowTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
git diff --check
```

Results:
- RED/HOST-FIRST: `commonReaderSettingsDialogMatchesKomikkuCustomFilterDimAmount` first failed because Navic hid chrome on `CustomFilter` but did not carry Komikku's `window?.setDimAmount(0f)` / `0.5f` behavior through the adaptive sheet.
- GREEN/COMMON: `KomikkuReaderSettingsDialog` now computes `settingsDimAmount` from the active settings tab and passes it through `KomikkuTabbedDialog`.
- GREEN/ANDROID: `KomikkuAdaptiveSheet.android.kt` now applies `dimAmount` to the active Dialog window through `DialogWindowProvider`, matching the Komikku source behavior.
- GREEN/HOST-FOCUSED: the new dim parity guard passed.
- GREEN/HOST-RELATED: `ReaderRuntimeCommonChromeTest`, `ReaderRuntimeNavigationFlowTest`, and `ReaderRuntimeSettingsBridgeTest` passed.
- GREEN/FULL-ANDROID-HOST: `:composeApp:testAndroid` passed with `BUILD SUCCESSFUL in 24s`.
- GREEN/WHITESPACE: `git diff --check` passed.
- Remaining: no emulator/device visual validation was run for this UI dim behavior.


## 2026-06-22 Publisher Styles Settings Placement Parity

Scope:
- Keep Komikku's Custom filter page visual-filter-only and move the EPUB `Publisher styles` toggle into General, beside typography/theme controls.

Commands:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderPublisherStylesBelongsToGeneralInsteadOfCustomFilter
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeCommonChromeTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
git diff --check
```

Results:
- RED/HOST-FIRST: `commonReaderPublisherStylesBelongsToGeneralInsteadOfCustomFilter` first failed because `Publisher styles` still lived under `KomikkuSettingsTab.CustomFilter`.
- GREEN/REFERENCE: the guard reads Komikku `ColorFilterPage.kt` and asserts that no publisher-style control exists in that visual-filter-only page.
- GREEN/UI: `Publisher styles` now renders in the General settings page after Theme and before Rotation; Custom filter now contains only dim/color/grayscale/invert controls.
- GREEN/HOST-FOCUSED: the new placement guard passed.
- GREEN/HOST-RELATED: `ReaderRuntimeCommonChromeTest` and `ReaderRuntimeSettingsBridgeTest` passed.
- GREEN/FULL-ANDROID-HOST: `:composeApp:testAndroid` passed with `BUILD SUCCESSFUL in 24s`.
- GREEN/WHITESPACE: `git diff --check` passed.
- Remaining: no emulator/device visual validation was run for this small settings-placement parity fix.


## 2026-06-22 Readerdev No-Console Gradle Launcher and eta81 Readable Matrix

Scope:
- Fix the readerdev install pipeline regression where `install-reader-dev.ps1` still invoked `gradlew.bat`, spawning `cmd.exe`/`conhost` windows during validation.
- Refresh the emulator to the eta81 readerdev build and run the documented readable-content gesture matrix without relaunching out of the current EPUB page.

Commands:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest.readerDevScriptsAndSecretFilesAreDocumentedAndIgnored
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -NoBuild -NoInstall -NoLaunch -NoDiscoverPublication -PublicationUrl https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426 -ResourceHref OEBPS/xhtml/Authorforeword.xhtml -Title Probe -Kind epub
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta81 -NoLaunch -ArtifactRoot tmp\readerdev-matrix-eta81-readable-20260622-213611 -ContinueOnFailure
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroid
git diff --check
```

Results:
- RED/HOST-FIRST: the focused readerdev environment guard failed before implementation because the installer had no direct Gradle wrapper jar invocation guard and still allowed `gradlew.bat`/`cmd.exe` paths.
- GREEN/INSTALLER: `scripts\install-reader-dev.ps1` now uses `Invoke-GradleWrapper`, launching `java -jar gradle\wrapper\gradle-wrapper.jar` through `ProcessStartInfo` with `UseShellExecute=false` and `CreateNoWindow=true`.
- GREEN/HOST-FOCUSED: `ReaderDevEnvironmentContractTest.readerDevScriptsAndSecretFilesAreDocumentedAndIgnored` passed after the installer fix.
- GREEN/SMOKE: the no-build/no-install script parse and argument path completed with exit code 0.
- GREEN/INSTALL: the emulator package refreshed to `darkaxt.navic.readerdev` `versionName=v1.0.11-eta81`, `versionCode=414`, `lastUpdateTime=2026-06-22 21:33:13`.
- GREEN/MATRIX: readable-content matrix artifact `tmp\readerdev-matrix-eta81-readable-20260622-213611` passed `baseline-current-reader`, `enter-readable-content`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` with no matrix failures.
- GREEN/HOST-CONTRACT: full `ReaderDevEnvironmentContractTest` passed.
- GREEN/FULL-ANDROID-HOST: `:composeApp:testAndroid` passed with `BUILD SUCCESSFUL in 26s`.
- GREEN/WHITESPACE: `git diff --check` passed.
- NOTE: this matrix started from a readable EPUB page, not the native/shell cover. Cover-state checks still need a dedicated launch or manual setup before claiming cover behavior.


## 2026-06-22 Native Cover Pagination-Profile Retention

Scope:
- Fix a native/shell cover regression where EPUB pagination-profile relocation events could dismiss the controller-owned cover immediately after launch, leaving the reader on a later saved text location instead of the cover.
- Validate the patched eta81 readerdev install with a cover-inclusive emulator gesture matrix.

Commands:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.paginationProfileReadyRelocationDoesNotDismissControllerOwnedShellCover
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderRuntimeImageLinkTest
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -RequireReaderLaunch -ReaderPublicationUrl https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426 -ReaderResourceHref api/v1/book/3809/file?bookFileId=426 -ReaderBookId 3809 -ReaderTitle "Bastille vs. the Evil Librarians" -ReaderKind ebook -ReaderFormat epub -Capture
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-eta81 -NoLaunch -IncludeCoverChecks -ArtifactRoot tmp\readerdev-matrix-eta81-cover-20260622-220105 -ContinueOnFailure
```

Results:
- RED/HOST-FIRST: `paginationProfileReadyRelocationDoesNotDismissControllerOwnedShellCover` first failed because `ReaderController` treated `reason=pagination-profile-ready` as an explicit readable relocation and dismissed `shellCoverVisible`.
- GREEN/CONTROLLER: pagination-profile relocation reasons are now treated like non-user navigation (`initial-resume` / `relocate-committed`) while the native cover is visible, so page-count/profile updates do not steal the cover.
- GREEN/HOST-FOCUSED: the new cover-retention guard passed.
- GREEN/HOST-RELATED: `ReaderControllerTest`, `ReaderRuntimeShellProgressTest`, and `ReaderRuntimeImageLinkTest` passed.
- GREEN/INSTALL: patched readerdev APK installed on `emulator-5554` as `darkaxt.navic.readerdev` `versionName=v1.0.11-eta81`, `versionCode=414`, `lastUpdateTime=2026-06-22 21:58:52`.
- GREEN/LOGCAT: after launch, the WebView emitted `pagination-profile-cached` and `relocate-committed` for `OEBPS/xhtml/Authorforeword.xhtml`, and `KomikkuReaderRoot` remained `shellCover=true`.
- GREEN/VISUAL: capture `captures\reader-dev\reader-dev-20260622-215859.png` and matrix `baseline-native-cover\screen.png` show the native cover still visible after pagination profiling.
- GREEN/MATRIX: cover-inclusive matrix artifact `tmp\readerdev-matrix-eta81-cover-20260622-220105` passed `baseline-current-reader`, `baseline-native-cover`, `cover-center-tap-toggle`, `cover-drag-next`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` with no matrix failures.
- Remaining: this validates emulator readerdev behavior, not a public release APK on the user's phone.
- Remaining: this does not prove runtime gesture or page-swap fidelity; those still require emulator/device validation against the Komikku viewer behavior.


## 2026-06-27 Master Sync and Bindery OPDS Whispersync Schema Guard

Scope:
- Merge the current optimized `fork/master` baseline into `codex/komikku-reader-backbone-eta64`.
- Check `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\github-export\bindery\docs\navic-opds-api-schema.md` against Navic's Bindery/Whispersync launch policy.

Schema finding:
- The schema states that Whispersync UI/launch affordances must be based on exact ready pairs, not book-level inference: `whispersync.status == "ready"` and a nonblank `artifactHref` are required for the exact `ebookBookFileId` + `audiobookBookFileId` pair.
- RED: `BinderyBookVersionPolicy` previously accepted `ready` + `artifactId` and generated a fallback `/opds/books/{bookId}/sync/{artifactId}` sidecar path when `artifactHref` was missing.

Commands:

```powershell
git merge --no-edit fork/master
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest.readyWhispersyncPairsWithoutArtifactHrefDoNotCreateLaunchCandidates
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.ui.screens.bindery.BinderyBookVersionPolicyTest
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.BinderyBookSyncJsonTest --tests paige.navic.ui.screens.reader.ReaderWhispersyncLaunchPolicyTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest
```

Results:
- GREEN/MERGE: merged `fork/master` after resolving the validation-log append conflict by preserving both branches' evidence notes.
- RED/HOST-FIRST: the new artifact-href guard failed before implementation because a ready pair without `artifactHref` still produced ebook/audiobook sync matches.
- GREEN/POLICY: `BinderySyncPair.hasReadyWhispersync()` now requires ready status, `artifactId`, and nonblank `artifactHref`; `toWhispersyncMatch()` no longer invents a fallback sidecar path.
- GREEN/HOST-FOCUSED: full `BinderyBookVersionPolicyTest` passed.
- GREEN/HOST-ADJACENT: `BinderyBookSyncJsonTest`, `ReaderWhispersyncLaunchPolicyTest`, and `BinderyContinueShelfPolicyTest` passed.
- Remaining: the top-left cover headset badge remains design-proposed pending explicit user confirmation; it should use the same exact-pair readiness rule.


## 2026-06-28 Whispersync Tiny Boundary Gap Snap

Scope:
- Address `2026-06-22-whispersync-implementation-review.md` finding 5 without changing real timeline-gap behavior.
- Stabilize the Anx/Foliate capability layer so tiny ASR/JSON timing gaps do not clear the active cue between adjacent segments.

Commands:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.WhispersyncTimelineParserTest.activeSegmentSnapsTinyAudioBoundaryGapToNearestCue
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest
```

Results:
- RED/HOST-FIRST: `activeSegmentSnapsTinyAudioBoundaryGapToNearestCue` first failed on the synced baseline because `activeSegment()` returned `null` for a 1 ms gap before the next cue.
- GREEN/MODEL: `WhispersyncTimeline.activeSegment()` now exact-matches first, then snaps only tiny boundary gaps for the same audio resource/track to the nearest cue boundary.
- GREEN/GAP-GUARD: the regression test also verifies a wider real unmapped gap remains inactive, preserving the existing neutral `NoActiveCue` path.
- GREEN/HOST-FOCUSED: the focused boundary test passed.
- GREEN/HOST-RELATED: `WhispersyncTimelineParserTest`, `ReaderWhispersyncSyncCoordinatorTest`, and `ReaderWhispersyncPlaybackPolicyTest` passed.
- Remaining: no emulator/device run was needed because this slice is pure timeline/model behavior; runtime playback UI validation remains separate.


## 2026-06-28 Bindery Whispersync API Refresh

Scope:
- Adapt Navic's Bindery/Whispersync parsing to the updated 2026-06-27 API documentation.
- Preserve exact-pair Whispersync launch behavior while accepting the newer sidecar/job metadata shape.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.domain.repositories.BinderyBookSyncJsonTest --tests paige.navic.domain.repositories.BinderyRepositoryCatalogJsonTest --tests paige.navic.domain.repositories.BinderyBookVersionPolicyTest --tests paige.navic.reader.ReaderWhispersyncLaunchPolicyTest --console=plain
git diff --check
```

Results:
- GREEN/API-MODEL: `WhispersyncSidecar` accepts top-level `score`, `coverage`, `audioCoverage`, and `ebookCoverage`.
- GREEN/JOB-MODEL: `BinderyWhispersyncJob` accepts the updated `state`, fractional `progressPercent`, and `updatedAt` fields.
- GREEN/HOST-FOCUSED: `WhispersyncTimelineParserTest`, `BinderyBookSyncJsonTest`, `BinderyRepositoryCatalogJsonTest`, `BinderyBookVersionPolicyTest`, and `ReaderWhispersyncLaunchPolicyTest` passed.
- GREEN/WHITESPACE: `git diff --check` passed.
- Committed and pushed as `6f3812fc fix: preserve updated bindery whispersync schema`.
- Remaining: this validates parser/launch-policy compatibility with the updated API contract, not end-to-end audiobook playback on device.


## 2026-06-28 Chapter Rail Exact Target Bridge

Scope:
- Fix a host-proven lossy bridge contract where native Komikku chapter rail seeks computed an exact `chapterPageIndex`/`chapterPageCount`, but the Foliate bridge discarded those fields and sent only a fraction to the WebView.
- Keep this as host evidence only: it closes the bridge payload gap, not the full physical-device rail endpoint bug.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderChapterRailSeekPreservesNativeTargetPageThroughBridge --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest --console=plain
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
git diff --check
```

Results:
- RED/HOST-FIRST: `androidReaderChapterRailSeekPreservesNativeTargetPageThroughBridge` first failed because `navic-reader.js` dispatched `goToChapterProgress(command.href, command.progress)` and `navic-reader-page-turns.js` accepted only `(href, progress)`.
- GREEN/BRIDGE: `ReaderBridgeCommand.GoToChapterProgress` now serializes `chapterPageIndex` and `chapterPageCount` when the native shell has them.
- GREEN/ADAPTER: `FoliateEpubEngineAdapter` now preserves `ReaderLocator.chapterPageIndex/chapterPageCount` when translating native rail navigation to a Foliate bridge command.
- GREEN/RUNTIME: `navic-reader.js` and `navic-reader-page-turns.js` now carry those exact target fields through the WebView boundary and derive the chapter-local target fraction from the page index/count when present.
- GREEN/HOST-FOCUSED: the new bridge preservation guard passed after implementation.
- GREEN/HOST-RELATED: `ReaderRuntimeShellProgressTest`, `ReaderBridgeProtocolTest`, and `FoliateEpubEngineAdapterTest` passed.
- GREEN/JS: `node --check` passed for `navic-reader.js` and `navic-reader-page-turns.js`.
- GREEN/WHITESPACE: `git diff --check` passed.
- Remaining: this does not prove physical-device progress rail endpoint behavior; the clean release/device rail validation item remains open.


## 2026-06-28 Bindery Progress API Refresh

Scope:
- Adapt Navic reading-progress decode, resume, save, Continue Reading, and Whispersync companion progress paths to the 2026-06-27 Bindery OPDS progress schema in `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\github-export\bindery\docs\navic-opds-api-schema.md`.
- Preserve compatibility with legacy local `resourceHref` while accepting current `href`, `resourceKey`, seconds/milliseconds position fields, `completed`, and numeric `bookId`.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.BinderyRepositoryProgressCacheTest --tests paige.navic.reader.ReaderProgressSyncTest --tests paige.navic.ui.screens.bindery.BinderyContinueShelfPolicyTest --console=plain
```

Results:
- RED/API-MODEL: `progressJsonDecodesCurrentBinderyProgressSchema` first failed because `BinderyReadingProgress` did not expose `resourceKey`, `href`, `position`, `duration`, or `completed`.
- GREEN/API-MODEL: `BinderyReadingProgress` now decodes numeric `bookId`, `alias`, `resourceKey`, `href`, seconds and millisecond positions, `completed`, and `updatedAt` using the shared Bindery JSON configuration.
- RED/RESUME: `binderyProgressMatchesCurrentBinderyResourceKeyWhenHrefIsAbsent` first proved that a progress entry with only `resourceKey` could incorrectly match the wrong reader resource.
- GREEN/RESUME: progress resume identity now prefers legacy `resourceHref`, then current `href`, then `/opds/books/{bookId}/resources/{resourceKey}`.
- RED/SHELF: Continue Reading and Whispersync companion progress ignored current progress entries that used `href` without legacy `resourceHref`.
- GREEN/SHELF: shelf and companion progress now resolve current `href` payloads.
- GREEN/SAVE: reader saves now include current `resourceKey` and `href` while still preserving legacy `resourceHref`.
- GREEN/HOST-FOCUSED: `BinderyRepositoryProgressCacheTest`, `ReaderProgressSyncTest`, and `BinderyContinueShelfPolicyTest` passed together.
- Remaining: this validates host/source compatibility with the updated progress schema, not release-device proof of live progress sync.


## 2026-06-28 Stage 1 Reader Shell Matrix Baseline

Scope:
- Execute the Stage 1 reader shell blocker gate from `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md` against the currently installed readerdev APK.
- Validate native tap ownership, drag ownership, edge tap paging, and texture direction before choosing the next blocker to patch.

Environment:
- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed version: `v1.0.11-theta13`, `versionCode=441`.
- Matrix artifacts: `captures/reader-komikku-matrix/stage1-baseline-20260628-222250`.

Commands:

```powershell
scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactRoot captures\reader-komikku-matrix\stage1-baseline-20260628-222250 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

Results:
- GREEN/MATRIX: `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk` passed.
- GREEN/IMAGE-TAP-GUARD: `plainImageHitType5Regression=False`, `nativeTapAction=True`, `explicitContentHandler=False`, and `contentTapHandledEvent=False`, so short image taps were not routed through the old WebView/image handler path in this run.
- GREEN/TEXTURE-DIRECTION-GUARD: the next and previous texture direction samples were classified with `wrongTextureDirection=False`.
- INVALID/COVER-SETUP: `baseline-native-cover` and `cover-drag-next` failed because the matrix was launched with `-NoLaunch` while the emulator was already inside normal reader pages, not on the native shell cover. This is a test setup failure, not proof of a cover regression.

Remaining:
- Add or use a deterministic matrix setup path that returns the reader to the native shell cover before cover lifecycle checks.
- Do not patch cover behavior from this run alone; rerun cover-specific validation only after the start state is controlled.


## 2026-06-28 Stage 1 Prepared Cover Matrix Gate

Scope:
- Add and validate a deterministic readerdev launch path for the Komikku matrix so cover lifecycle checks start from the native shell cover instead of stale emulator state.
- Prove native cover, cover tap, cover drag, readable tap/drag, edge taps, and texture direction in one prepared run.

Environment:
- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed version: `v1.0.11-theta13`, `versionCode=441`.
- Prepared launch target discovered from Bindery: `A Memory of Light (epub)`.
- Matrix artifacts: `captures/reader-komikku-matrix/stage1-cover-prepared-20260628-223828`.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest.komikkuMatrixCanPrepareNativeCoverStartStateBeforeCoverChecks --console=plain
scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactRoot captures\reader-komikku-matrix\stage1-cover-prepared-20260628-223828 -PrepareReaderLaunch -IncludeCoverChecks -ContinueOnFailure
```

Results:
- RED/HOST-FIRST: `komikkuMatrixCanPrepareNativeCoverStartStateBeforeCoverChecks` first failed because the matrix had no controlled prepare path and relied on the current emulator screen.
- GREEN/HOST: the focused matrix prepare guard passed after adding `-PrepareReaderLaunch` and `-PrepareStartProgress 0`.
- GREEN/MATRIX: all 12 matrix rows passed: `baseline-current-reader`, `baseline-native-cover`, `cover-center-tap-toggle`, `cover-drag-next`, `center-tap-toggle`, `native-long-press-center`, `edge-tap-next`, `drag-next`, `texture-next-walk`, `edge-tap-previous`, `drag-previous`, and `texture-previous-walk`.
- GREEN/COVER-DRAG: `cover-drag-next` captured `Reader shell cover drag candidate`, `Reader shell cover swipe action=Right`, and `Reader shell cover command action=Right`.
- GREEN/TEXTURE-DIRECTION-GUARD: previous direction samples were classified with `wrongTextureDirection=False`; next direction also passed through the matrix summary.

Remaining:
- This proves the current installed readerdev cover/tap/drag/texture baseline on the emulator. It does not replace final physical-device validation for release APK touch feel or visual judgment.


## 2026-06-28 Stage 2/6 Font Size Runtime Probe

Scope:
- Validate the reported font-size behavior against the current readerdev WebView runtime before patching typography.
- Confirm whether the font-size control scales real loaded EPUB prose, body inheritance, and publisher-important prose, not only headings.

Environment:
- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed version: `v1.0.11-theta13`, `versionCode=441`.
- Reader session: prepared `A Memory of Light (epub)` session from the Stage 1 matrix.
- Artifacts:
  - `captures/reader-smoke/font-size-probe-20260628-224841`
  - `captures/reader-smoke/font-size-publisher-probe-20260628-224841`

Commands:

```powershell
scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactDir captures\reader-smoke\font-size-probe-20260628-224841 -NoLaunch -ReaderDevtoolsProbe font-size -CaptureReaderDiagnostics
scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactDir captures\reader-smoke\font-size-publisher-probe-20260628-224841 -NoLaunch -ReaderDevtoolsProbe font-size-publisher-styles -CaptureReaderDiagnostics
```

Results:
- GREEN/RUNTIME: the `font-size` probe passed and measured real existing EPUB paragraph text changing from `16px` at 100% to `22.4px` at 140%.
- GREEN/BODY-INHERITANCE: the same probe measured root, body, and probe paragraph deltas of `6.4px`.
- GREEN/PUBLISHER-STYLES: the publisher-style probe passed; publisher paragraph and class-important prose both changed from `16px` to `22.4px`.

Remaining:
- Current readerdev does not reproduce the “headings scale but body text does not” issue. Treat future reports as either release-version drift or a specific EPUB/layout case and capture that exact book/page before changing typography.


## 2026-06-28 Stage 5 Whispersync Track Identity Contract

Scope:
- Close the resource/track ambiguity gap from the Whispersync implementation review.
- Ensure explicit sidecar track identity is not silently overridden by stale or generic resource names.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest.sidecarTrackIndexWinsOverStaleResourceMatch" --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.WhispersyncTimelineParserTest.activeSegmentDoesNotLetStaleResourceOverrideExplicitTrackIndex" --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
```

Results:
- RED/GREEN: `sidecarTrackIndexWinsOverStaleResourceMatch` first failed because a stale resource match selected track `0`; after the policy change, sidecar `audioTrackIndex=1` selected track `1`.
- RED/GREEN: `activeSegmentDoesNotLetStaleResourceOverrideExplicitTrackIndex` first failed because audio-follow accepted a same-resource segment even though playback reported a conflicting track index; after the model change, explicit track disagreement rejects that segment.
- GREEN/SUITE: `:composeApp:testAndroid` passed.
- GREEN/WHITESPACE: `git diff --check` passed.

Remaining:
- This is host/model proof. It does not replace the release/device enjoyment pass for a real paired ebook/audiobook session.


## 2026-06-29 Stage 5 Whispersync Exact Pair Playback Gate

Scope:
- Close the live Bindery sidecar playback blocker for book `3809`, where the sidecar exposes `bookId=3809`, `ebookBookFileId=426`, and `audiobookBookFileId=633` but no `resources.audiobookManifestHref`.
- Validate that exact-pair manifest resolution, page-to-audio seek, audio-to-reader visible-range follow, and char-offset overlay activation work in readerdev before release packaging.

Environment:
- Device: `emulator-5554`.
- Package: `darkaxt.navic.readerdev`.
- Installed version: `v1.0.11-theta13`, `versionCode=441`, `lastUpdateTime=2026-06-29 00:38:01`.
- Bindery book: `https://bindery.remaxku.eu/book/3809`.
- Ebook file: `https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426`.
- Audiobook file: `https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=633`.
- Sidecar artifact: `/opds/books/3809/sync/8`.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyRepositoryTest.whispersyncAudiobookManifestFallsBackToExactBookFilePair" --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.BinderyRepositoryTest --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderControllerTest.whispersyncOverlayFragmentActiveFeedsAudioSeekTarget" --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderWhispersyncSyncCoordinatorTest --tests paige.navic.reader.ReaderWhispersyncPlaybackPolicyTest --tests paige.navic.reader.WhispersyncTimelineParserTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.domain.repositories.BinderyRepositoryTest --console=plain
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -PublicationUrl "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -BookId 3809 -Title "Bastille vs. the Evil Librarians" -Kind ebook -Format EPUB -ResourceHref "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -WhispersyncSidecarHref "/opds/books/3809/sync/8" -WhispersyncAudiobookBookFileId 633 -ExpectedPackage darkaxt.navic.readerdev -ExpectedVersionName v1.0.11-theta13 -WaitForReaderReady
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactDir captures\reader-smoke\whispersync-page-scoped-control-20260629-004034 -NoLaunch -ReaderDevtoolsProbe whispersync-page-scoped-control -CaptureReaderDiagnostics
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactDir captures\reader-smoke\whispersync-audio-follow-20260629-004817 -NoLaunch -ReaderDevtoolsProbe whispersync-audio-follow -CaptureReaderDiagnostics
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactDir captures\reader-smoke\whispersync-char-offset-overlay-20260629-005355 -NoLaunch -ReaderDevtoolsProbe whispersync-char-offset-overlay -CaptureReaderDiagnostics
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest --console=plain
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
```

Results:
- RED/HOST-FIRST: `whispersyncAudiobookManifestFallsBackToExactBookFilePair` first failed because `BinderyRepository` had no exact-pair manifest fallback.
- GREEN/API-PAIR: `BinderyRepository.getWhispersyncAudiobookManifest()` now resolves in priority order: explicit sidecar manifest href, explicit audiobook id, then exact `bookId + audiobookBookFileId` through the audiobook versions endpoint.
- GREEN/HOST-FOCUSED: the exact-pair repository test passed, then full `BinderyRepositoryTest` passed.
- RED/CONTROLLER-FIRST: `whispersyncOverlayFragmentActiveFeedsAudioSeekTarget` first failed because `MediaOverlayActive` stored the active overlay but did not create a Whispersync audio seek target.
- GREEN/CONTROLLER: `ReaderController` now converts direct `MediaOverlayActive` fragments with clip timing into `WhispersyncAudioSeekTarget` when sync is available and enabled.
- GREEN/HOST-RELATED: `ReaderWhispersyncSyncCoordinatorTest`, `ReaderWhispersyncPlaybackPolicyTest`, `WhispersyncTimelineParserTest`, `ReaderControllerTest`, and `BinderyRepositoryTest` passed together after the controller fix.
- GREEN/READERDEV-INSTALL: readerdev installed and opened the book `3809` session with package version `v1.0.11-theta13`, `versionCode=441`, `lastUpdateTime=2026-06-29 00:38:01`, and `publicationReady`.
- GREEN/PAGE-TO-AUDIO: `captures\reader-smoke\whispersync-page-scoped-control-20260629-004034\logcat-reader.log` showed `overlayFragmentActive` followed by `Whispersync audiobook seek audio=6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b positionMs=263360`. The older `no playback plan match` message did not appear.
- GREEN/PAGE-SCOPED-CONTROL: the same page-scoped probe moved to an unsupported page, emitted `page-scoped-control-unsupported`, and dispatched `clearOverlay`.
- GREEN/AUDIO-FOLLOW: `captures\reader-smoke\whispersync-audio-follow-20260629-004817\reader-devtools-probe.json` reported `currentHref=OEBPS/xhtml/Authorforeword.xhtml`, `reason=media-overlay-follow`, and `visibleStart=3`, `visibleEnd=2442`. The corresponding log contained `Reader bridge event: visibleTextRange(... source=media-overlay-follow)`.
- GREEN/CHAR-OFFSET-OVERLAY: `captures\reader-smoke\whispersync-char-offset-overlay-20260629-005355\reader-devtools-probe.json` found an exact `navic-active-overlay-fragment` marker for text offsets `27-75`; `logcat-reader.log` showed `overlayFragmentActive` and `Whispersync audiobook seek audio=navic-whispersync-char-offset-overlay-probe positionMs=1000`.
- GREEN/JS: `node --check tools\reader-harness\src\adb-webview-eval.mjs` exited cleanly.
- GREEN/HOST-RUNTIME-ASSET: `ReaderRuntimeAssetsTest` passed.
- GREEN/SUITE: `:composeApp:testAndroid` passed.
- GREEN/WHITESPACE: `git diff --check` passed.

Observed warnings:
- Readerdev probes still logged `Bindery reading progress returned HTTP 400` while saving probe-driven visible ranges. This did not block manifest resolution or Whispersync audio seek, but it should be handled as a separate progress-write compatibility follow-up if it repeats during normal user navigation.

Remaining:
- This closes the exact-pair playback gate in readerdev. It does not yet replace a final release APK pass on a physical phone/tablet for audio feel, highlighting comfort, resume ownership, or miniplayer coexistence.


## 2026-06-29 Readerdev Bindery Resource Identity Gate

Scope:
- Investigate the `Bindery reading progress returned HTTP 400` warning seen during readerdev Whispersync probes.
- Prevent emulator validation from launching direct Bindery file URLs with the invalid progress identity `resourceKey=file`.

Root cause:
- Readerdev was launched with `publicationUrl/resourceHref=https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426`.
- Navic canonicalized that direct API URL to `/api/v1/book/3809/file`, then derived `resourceKey=file`.
- Live Bindery rejects that progress write with `400 {"error":"resource not found"}`.
- The same book's valid OPDS resource is `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`, which stores progress successfully.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest.readerDevScriptsAndSecretFilesAreDocumentedAndIgnored --console=plain
.\scripts\install-reader-dev.ps1 -NoBuild -NoInstall -NoLaunch -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -PublicationUrl "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -ResourceHref "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -BookId 3809 -Title "Bastille vs. the Evil Librarians" -Kind ebook -Format EPUB -WhispersyncSidecarUrl "/opds/books/3809/sync/8" -WhispersyncAudiobookBookFileId 633
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderDevEnvironmentContractTest --console=plain
git diff --check
```

Results:
- RED/HOST-FIRST: `readerDevScriptsAndSecretFilesAreDocumentedAndIgnored` failed before the install script had explicit direct-file canonicalization (`tmp/codex-validation/readerdev-resource-canonical-red-20260629-011417.out.log`).
- GREEN/HOST-FOCUSED: the same guard passed after adding `Resolve-ReaderDevExplicitBinderyResource`, `Get-ReaderDevBookFileIdFromUrl`, and the `properties.bookFileId` OPDS match path (`tmp/codex-validation/readerdev-resource-canonical-green-20260629-011950.out.log`).
- GREEN/SCRIPT-PROOF: the no-build/no-install/no-launch command resolved the direct file URL to `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`.
- GREEN/HOST-CONTRACT: full `ReaderDevEnvironmentContractTest` passed (`tmp/codex-validation/readerdev-contract-green-20260629-012144.out.log`).
- GREEN/WHITESPACE: `git diff --check` passed.

Remaining:
- If progress-save 400s repeat after an OPDS resource launch, treat that as a production progress schema bug. The known readerdev false-positive path is now closed.


## 2026-06-29 Whispersync Media-Follow Navigation Ownership Gate

Scope:
- Keep paired ebook/audiobook playback from hijacking explicit user/probe reader navigation.
- Preserve direct sidecar launches where Bindery provides `/opds/books/{bookId}/sync/{artifactId}` but the reader route does not carry a separate artifact id.
- Keep the readerdev page-scoped control probe faithful to Foliate behavior by snapshotting navigation results without waiting on a `goToHref` promise that may not settle.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderDoesNotLetMediaOverlayFollowInterruptUserRelocation --console=plain
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -ReaderPublicationUrl "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -ReaderWhispersyncSidecarUrl "/opds/books/3809/sync/8" -ReaderWhispersyncAudiobookBookFileId 633 -RequireReaderLaunch
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -ExpectedVersionName v1.0.11-theta13 -ArtifactDir captures\reader-smoke\whispersync-media-follow-defer-20260629-022114 -NoLaunch -ReaderDevtoolsProbe whispersync-page-scoped-control -CaptureReaderDiagnostics
.\gradlew.bat --no-daemon :composeApp:testAndroid
git diff --check
```

Results:
- RED/HOST-FIRST: `ReaderRuntimeAssetsTest.androidReaderDoesNotLetMediaOverlayFollowInterruptUserRelocation` failed before the runtime had a source guard ahead of `await this.goTo(targetHref, 'media-overlay-follow')` (`tmp/codex-validation/20260629-media-follow-red-host.out.log`).
- GREEN/HOST-FOCUSED: the same guard passed after adding `mediaOverlayFollowShouldDeferForUserRelocation()` and the pre-`goTo` defer path (`tmp/codex-validation/20260629-media-follow-green-host.out.log`).
- GREEN/LAUNCH-POLICY: `ReaderWhispersyncLaunchPolicyTest.readerWhispersyncLaunchAttachmentDerivesArtifactIdFromSidecarPathWhenMissing` passed after deriving artifact id `8` from `/opds/books/3809/sync/8` when the route omits `whispersyncArtifactId`.
- GREEN/HARNESS: `ReaderRuntimeAssetsTest.adbWebViewEvalHelperCanProbeWhispersyncPageScopedControl` passed after the probe stopped awaiting `goToHref` for diagnostic page jumps.
- GREEN/READERDEV-INSTALL: readerdev installed and launched package `darkaxt.navic.readerdev`, `versionName=v1.0.11-theta13`, `versionCode=441`, `lastUpdateTime=2026-06-29 02:17:50`, and reached `publicationReady`.
- GREEN/PAGE-TO-AUDIO: `captures\reader-smoke\whispersync-media-follow-defer-20260629-022114\logcat-reader.log` contains one `Whispersync audiobook seek` at `positionMs=263360`, three `overlayFragmentActive` lines, and zero `seek ignored` lines.
- GREEN/PAGE-SCOPED-CONTROL: `captures\reader-smoke\whispersync-media-follow-defer-20260629-022114\reader-devtools-probe.json` reports the cue page as `OEBPS/xhtml/Authorforeword.xhtml`, then the unsupported probe page as `OEBPS/xhtml/mini_toc.xhtml` with visible range `1-486`. That verifies audio-follow no longer pulls the unsupported page back to the previous cue section.
- GREEN/CLEAR-OVERLAY: the same log contains `Dispatching reader engine command: clearOverlay`.
- GREEN/JS: both touched JS files passed `node --check`.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid` passed (`tmp/codex-validation/stage5-testAndroid-20260629-022427.out.log`).
- GREEN/WHITESPACE: `git diff --check` passed.

Remaining:
- Exact companion progress reopen is still a separate Stage 5 validation item.
- This slice validates emulator readerdev behavior, not a public release APK or real-device audio feel.


## 2026-06-29 Whispersync Exact Companion Progress Reopen Gate

Scope:
- Preserve exact paired audiobook position when an ebook page is opened from Whispersync companion progress.
- Prevent an initial fraction-only reader progress save or later `media-overlay-follow` relocation from downgrading the companion progress entry.
- Validate reopen on production book `3809`, sidecar `/opds/books/3809/sync/8`, paired audiobook `34`, audiobook book file `633`.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --console=plain
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -PublicationUrl "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -BookId 3809 -Title "Bastille vs. the Evil Librarians" -Kind Ebook -WhispersyncSidecarUrl "/opds/books/3809/sync/8" -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -WhispersyncAudiobookTitle "Bastille vs. the Evil Librarians" -RequireReaderLaunch
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -DeviceSerial emulator-5554 -NoLaunch -CaptureReaderDiagnostics -ReaderDevtoolsProbe whispersync-companion-progress -ArtifactDir captures\reader-smoke\whispersync-companion-progress-after-step-target-save-20260629-current -RequireReaderLog @("Whispersync audiobook seek","positionMs=263360")
.\scripts\install-reader-dev.ps1 -NoBuild -NoInstall -RequireReaderLaunch -DeviceSerial emulator-5554 -Package darkaxt.navic.readerdev -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env -PublicationUrl "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426" -BookId 3809 -Title "Bastille vs. the Evil Librarians" -Kind Ebook -WhispersyncSidecarUrl "/opds/books/3809/sync/8" -WhispersyncAudiobookId 34 -WhispersyncAudiobookBookFileId 633 -WhispersyncAudiobookTitle "Bastille vs. the Evil Librarians"
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
```

Results:
- RED/HOST-FIRST: `ReaderWhispersyncCompanionProgressSourceTest.readerScreenPersistsCompanionProgressWithLatestWhispersyncAudioTarget` failed before `ReaderScreen` persisted companion progress with `step.whispersyncAudioSeekTarget` (`tmp/codex-validation/stage5-reader-screen-step-target-red-20260629-current.out.log`).
- GREEN/HOST-FOCUSED: the same guard passed after `ReaderScreen` started using `step.whispersyncAudioSeekTarget ?: coordinator.controller.state.whispersync.audioSeekTarget` (`tmp/codex-validation/stage5-reader-screen-step-target-green-20260629-current.out.log`).
- GREEN/READERDEV-INSTALL: readerdev installed and launched package `darkaxt.navic.readerdev`, `versionName=v1.0.11-theta13`, `versionCode=441`, `lastUpdateTime=2026-06-29 05:13:38`, and reached `publicationReady`.
- GREEN/EXACT-PROBE: `captures\reader-smoke\whispersync-companion-progress-after-step-target-save-20260629-current\logcat-reader.log` contains `Whispersync audiobook seek ... positionMs=263360`.
- GREEN/PERSISTENCE: the emulator preference entry for book `3809`, artifact `8`, OPDS resource `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe` persisted `audioPositionMs=263360` after the probe.
- GREEN/REOPEN: the no-build/no-install relaunch reached `publicationReady`; clean logcat shows `Loaded readaloud playback plan ... startPositionMs=263360` (`tmp/codex-validation/stage5-companion-relaunch-after-step-target-save-20260629-current.out.log` plus emulator logcat).
- GREEN/NO-DOWNGRADE: after reopen and `media-overlay-follow`, the same companion preference entry remained `audioPositionMs=263360`.
- GREEN/JS: `node --check tools\reader-harness\src\adb-webview-eval.mjs` exited cleanly.
- GREEN/SUITE: `.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain` passed (`tmp/codex-validation/stage5-full-testandroid-after-step-target-save-20260629-current.out.log`).
- GREEN/WHITESPACE: `git diff --check` passed.

Remaining:
- This closes the readerdev exact companion progress reopen gate. Release-device validation is still required before claiming the full paired audiobook/ebook experience is ready for a public APK.


## 2026-06-29 Stage 6A EPUB Typography And Viewport Layout Gate

Scope:
- Replace the old Navic body-margin/page-box shrink behavior with Anx/Foliate-owned renderer margins.
- Keep large phone, fold, and tablet EPUB page boxes using the full viewport before `gap`, `top-margin`, and `bottom-margin` are applied.
- Keep `columnThreshold` as the spread-splitting threshold, not the maximum inline page size.
- Remove the duplicate legacy `Margins` UI/search entry in favor of Side margin, Top margin, and Bottom margin.

Commands:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.FoliateAnxParityTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderUsesAnxMarginAttributesInsteadOfLegacyBodyMargins --rerun-tasks --console=plain
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\paginator.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode adaptive-page-box-logic
.\gradlew.bat --no-daemon :composeApp:testAndroid --console=plain
git diff --check
```

Results:
- RED/HOST-FIRST: `FoliateAnxParityTest.phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract` failed before the runtime/paginator dropped legacy margin shrink math and stopped using `columnThreshold` as max width.
- RED/HOST-FIRST: `ReaderRuntimeSettingsBridgeTest.androidReaderUsesAnxMarginAttributesInsteadOfLegacyBodyMargins` failed before legacy `Margins` UI/search rows were removed.
- GREEN/FOCUSED: the focused Android host rerun passed (`tmp/codex-validation/stage6a-focused-rerun.out.log`).
- GREEN/HARNESS: `adaptive-page-box-logic` passed after the harness was updated to assert full-viewport page boxes and ignored legacy `marginPercent`.
- GREEN/JS: all touched JS files passed `node --check`.
- GREEN/SUITE: `:composeApp:testAndroid` passed (`tmp/codex-validation/stage6a-testAndroid.out.log`).
- GREEN/READERDEV-INSTALL: readerdev installed and launched package `darkaxt.navic.readerdev`, reached `publicationReady`, and loaded book `3809` through the canonical OPDS resource `/opds/books/3809/resources/ebook-28501fd8c0cb40a558fe`.
- GREEN/TABLET: Tab S9 Ultra portrait evidence is under `captures\reader-smoke\stage6a-layout-emulator\tab-s9-ultra-portrait`; `page-box.json` reports renderer `1232x1974` CSS px, `maxColumnCount=1`, `topMargin=90px`, `bottomMargin=50px`, and `documentToViewportWidthRatio=0.94`.
- GREEN/FOLD: Z Fold7 inner evidence is under `captures\reader-smoke\stage6a-layout-emulator\zfold7-inner`; `page-box-text.json` reports renderer `856x950` CSS px, `maxColumnCount=1`, and `documentToViewportWidthRatio=0.94`.
- GREEN/PHONE: Z Fold7 cover evidence is under `captures\reader-smoke\stage6a-layout-emulator\zfold7-cover`; `page-box-text.json` reports renderer `410x956` CSS px, `maxColumnCount=1`, and `documentToViewportWidthRatio=0.939`.
- GREEN/WHITESPACE: `git diff --check` passed before documentation updates.

Remaining:
- Stage 6A is eligible to be part of a later coherent release candidate, but it should not trigger a public release by itself. Remaining visual systems include settings density, progress rail fidelity, texture strength, theme palette, and page-curl/drag preview behavior.
