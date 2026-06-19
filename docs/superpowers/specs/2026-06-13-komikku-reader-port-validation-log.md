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
