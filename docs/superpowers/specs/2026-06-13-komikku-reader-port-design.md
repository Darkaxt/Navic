# Komikku Reader Port Design

Date: 2026-06-13

Status: active source of truth for reader shell work. This supersedes the older stabilization and parity plans where they conflict with Komikku source behavior.

## Current Branch Register

This document is the durable project register for the reader-shell replacement. If context is compacted, resumed, or handed to another thread, this file overrides chat memory and the older `2026-06-11-reader-stabilization-design.md` shell-fix plan.

Current implementation state:

- 2026-06-13 hard reset: the old active `ReaderScreen` implementation has been removed from the active path and copied into `vault/reader/2026-06-13-pre-komikku-reset/`.
- The active `ReaderScreen` is now a Komikku-backbone skeleton. It does not mount `ReaderWebViewHost`, `ReaderPublicationRuntimeHost`, or `ReaderReadaloudRuntimeHost`.
- EPUB/PDF/readaloud loading is intentionally detached until the reader shell has a solid Komikku-derived root/viewer/overlay contract.
- The active root now follows Komikku's `reader_activity.xml` ownership order: full-size reader container, full-size viewer container, passive navigation overlay, full-size Compose overlay.
- 2026-06-13 follow-up: Android now implements that ownership order with a real native host in `KomikkuReaderNativeFrameHost.android.kt`: `FrameLayout` root, `readerContainer`, `viewerContainer`, passive `KomikkuReaderNativeNavigationOverlayView`, and `ComposeView` overlay. Common `ReaderScreen` routes through the platform host instead of emulating the hierarchy with Compose-only `Box` nodes.
- iOS currently has a compile-safe Compose fallback for `KomikkuReaderNativeFrameHost`; it is not a native Komikku-equivalent reader root.
- The ported navigation contract lives in `KomikkuViewerNavigation.kt` and carries Komikku's `ViewerNavigation`, region presets, constant top menu fallback, smaller-zone sizing, and tapping inversion model.
- The common tap-zone model must route through the ported `KomikkuReaderNavigator`. Do not reintroduce old Navic tap helpers as the authority.
- Reader chrome now follows Komikku's overlay model: app bars, chapter navigator/progress rail, content overlay, and page indicator are overlay siblings that never resize the viewer.
- `ReaderSurfaceHost` now follows the Komikku pager ordering for the Android source path: `super.dispatchTouchEvent(event)` sends the stream to the child renderer first, then the native surface sends the same stream to a `GestureDetector.SimpleOnGestureListener` and dispatches reader-wide actions from `onSingleTapConfirmed`.
- The shell-cover drag/swipe path is still native-owned, but now observes the already-dispatched child stream instead of bypassing the cover WebView before it sees events.
- The shell cover now renders through native `ReaderShellCoverView`, not `readerCoverWebView` or `readerShellCoverHtml(...)`.
- The content/chrome/settings split is only the first step. It is not the completed Komikku port.
- The next required step is Android device validation of the corrected reader surface over cover image, cover margins, EPUB text pages, EPUB image pages, links, and PDF pages. Do not resume cover CSS, texture, or page-number micro-fixes as a substitute.

Current branch divergence to resolve:

- Navic still has legacy reader-wide tap behavior in the Foliate/WebView runtime that must become non-authoritative.
- The native gesture layer must sit above content as the reader input owner, including over cover images and WebView/PDF surfaces.
- The native gesture layer must be phone-validated against cover image taps, cover drags, EPUB text pages, EPUB image pages, links, and PDF pages.
- The shell-cover WebView/HTML path has been removed from production and is now rejected by host tests. Do not reintroduce it.
- The WebView runtime now applies native tap-zone settings before `foliate-view.open(...)`, so loaded content documents see `nativeTapZones = true` before their load handlers can attach JS reader-wide tap handlers.
- Older stabilization notes and some host tests now conflict with the Komikku source. The accepted final behavior is a native reader/viewer surface that owns confirmed tap classification while forwarding the stream to its renderer child so drags, selection, PDF gestures, links, images, and media controls keep working.

Verification register:

- `node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js`: passed.
- Direct JS helper check with a minimal DOM shim: passed for `0.33/0.25` sizing, L-shaped top previous, Kindle top menu fallback, and visual overlay region count.
- Focused Gradle test run is currently blocked before reader tests by unrelated audiobook/Bindery compile errors in `androidMain`.
- 2026-06-13 follow-up: focused host tests passed for Komikku child-stream-first dispatch, confirmed-tap gesture dispatch, and shell-cover swipe observing the already-dispatched stream:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderSurfaceObservesConfirmedTapsAfterChildDispatchLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readableContentTapsAreObservedByNativeSurfaceAfterChildDispatchLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"`: passed.
- 2026-06-13 native cover red check failed as expected while production still used `shellCoverWebView` and `readerShellCoverHtml(...)`:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverIsRenderedByReaderShellViewNotWebViewHtml" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidWebViewIsWrappedBySingleNativeReaderSurfaceGestureManager" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.commonReaderUsesNativeShellCoverSurfaceWhenResolverProvidesCoverUrl" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderShellCoverTapsAndPreviousDoNotFallThroughToEpubCover"`: failed on all 4 tests.
- 2026-06-13 native cover green check passed after replacing the shell-cover WebView with `ReaderShellCoverView`:
  - same 4-test command: passed.
- 2026-06-13 affected reader host test classes passed:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest"`: passed.
- `git diff --check`: passed after the native cover replacement.
- 2026-06-13 native tap-zone ordering red check failed before `applySettings(settings)` moved ahead of `await this.view.open(url)`:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeSurfaceOwnsTaps"`: failed.
- 2026-06-13 native tap-zone ordering green check passed after the runtime ordering change:
  - same single-test command: passed.
- 2026-06-13 reader host class regression check after the ordering change passed:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"`: passed.
- `node --check composeApp\src\androidMain\assets\reader\navic-reader.js`: passed after the ordering change.
- `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :androidApp:assembleDebug`: passed after the native cover and native tap-zone ordering changes.
- `adb devices`: returned no connected devices. Phone validation remains pending.
- 2026-06-13 hard-reset backbone focused host test passed before the second Komikku-root tightening pass:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"`: passed.
- 2026-06-13 hard-reset Komikku-root tightening pass:
  - active `ReaderScreen` no longer mounts old WebView/Foliate/readaloud hosts.
  - active root is now `reader_container` -> `viewer_container` -> passive `navigation_overlay` -> `compose_overlay`.
  - viewer container owns the ported Komikku tap dispatch.
  - app bars, chapter navigator, content overlay, and page indicator are overlay siblings that do not resize content.
  - `git diff --check`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :androidApp:assembleDebug`: passed.
- 2026-06-13 native FrameLayout host pass:
  - red check failed first while `KomikkuReaderNativeFrameHost.android.kt` did not exist.
  - Android now provides `KomikkuReaderNativeFrameHost.android.kt` with `AndroidView`, native `FrameLayout`, nested `readerContainer`/`viewerContainer`, passive native navigation overlay, and `ComposeView` overlay.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy"`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"`: passed.
  - `git diff --check`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :androidApp:assembleDebug`: passed.

## 2026-06-13 Working Register: What Is Actually Verified

This section is the handoff register for the current implementation thread. Keep it updated before changing direction.

### Verified In Code

`composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`

- `ReaderSurfaceHost.dispatchTouchEvent(...)` now matches the Komikku pager event order for the Android source path:
  - capture `shellCoverWasVisible`
  - cancel stale pending center-tap callbacks on new gestures
  - call `super.dispatchTouchEvent(event)` first so the child renderer sees the stream
  - call `handleReaderSurfaceTouch(event)` after child dispatch
  - call `readerGestureDetector.onTouchEvent(event)` after child dispatch
  - return `true` for visible shell-cover gestures so the shell owns the cover page
- `readerGestureDetector` is a native `GestureDetector.SimpleOnGestureListener`.
- Reader-wide taps are dispatched only from `onSingleTapConfirmed(...)`.
- `handleReaderSurfaceTouch(...)` no longer turns normal EPUB/PDF pages from raw `ACTION_UP`; for non-cover content it only clears state on cancel.
- The shell-cover swipe path still observes move events for cover drag/page-turn behavior.
- The reader still queries content-specific claims before opening chrome:
  - direct WebView `hitTestResult`
  - explicit `markContentTapHandled()`
  - runtime `window.NavicReaderBridge.readerContentActionAtPoint(...)`
- `ReaderShellCoverView` is the native cover renderer inside `ReaderSurfaceHost`.
- `ReaderShellCoverView.updateCover(...)` maps the resolved appassets cover URL back to the local publication cache file.
- `ReaderShellCoverView` decodes the cached cover through `BitmapFactory.decodeFile(...)` and draws it with `Canvas.drawBitmap(...)` using a `contain` fit inside the full native view bounds.
- The old shell-cover HTML builder and second cover WebView have been removed from production.
- `openPublication(...)` now calls `applySettings(settings)` before `await this.view.open(url)`.
- This ordering is required because Foliate can emit document `load` events during `view.open(...)`; without early settings, `attachContentDocumentBehaviors(...)` can run while `nativeTapZones` is still false and attach JS reader-wide tap handlers before Android owns reader taps.

Host tests currently locking this source path:

- `ReaderRuntimeSettingsBridgeTest.androidReaderSurfaceObservesConfirmedTapsAfterChildDispatchLikeKomikku`
- `ReaderRuntimeShellProgressTest.readableContentTapsAreObservedByNativeSurfaceAfterChildDispatchLikeKomikku`
- `ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags`
- `ReaderRuntimeShellProgressTest.nativeShellCoverTouchStreamSharesKomikkuChildFirstGestureOwner`
- `ReaderRuntimeShellProgressTest.nativeShellCoverIsRenderedByReaderShellViewNotWebViewHtml`
- `ReaderRuntimeShellProgressTest.androidWebViewIsWrappedBySingleNativeReaderSurfaceGestureManager`
- `ReaderRuntimeImageLinkTest.commonReaderUsesNativeShellCoverSurfaceWhenResolverProvidesCoverUrl`
- `ReaderRuntimeImageLinkTest.androidReaderShellCoverTapsAndPreviousDoNotFallThroughToEpubCover`
- `ReaderRuntimeSettingsBridgeTest.androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeSurfaceOwnsTaps`

### Removed Legacy Cover Path

`ReaderWebViewHost.android.kt` no longer creates a second WebView for the shell cover. These symbols are now blocked by host tests:

- `shellCoverWebView`
- `readerCoverWebView`
- `readerShellCoverHtml`
- shell-cover `loadDataWithBaseURL(...)`

The native replacement is `ReaderShellCoverView`, mounted as a child of `ReaderSurfaceHost` above `readerWebView`. It is visual content only; `ReaderSurfaceHost` remains the event owner.

### Cover Resource Path

`composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderPublicationResource.android.kt`

- EPUB downloads are cached under `readerPublicationCacheRoot(context)`.
- The cache root resolves to `File(context.cacheDir, "reader")`.
- Publications are stored under `reader-publications/$cacheKey/publication.epub`.
- Extracted shell covers are stored next to the cached publication as `cover.<extension>`.
- The public cover URL is generated as:

```text
https://appassets.androidplatform.net/reader-cache/reader-publications/$cacheKey/cover.<extension>
```

The native cover implementation maps the known asset-loader URL back to the local cache file:

```text
https://appassets.androidplatform.net/reader-cache/<relative-path>
  -> readerPublicationCacheRoot(context)/<relative-path>
```

`ReaderShellCoverView` draws that decoded image directly inside the shell host.

### Completed Native Cover TDD Slice

This slice removed the shell-cover renderer WebView.

1. Host tests now fail while `ReaderWebViewHost.android.kt` contains any of these cover-rendering symbols:
   - `shellCoverWebView`
   - `readerCoverWebView`
   - `readerShellCoverHtml`
   - `loadDataWithBaseURL(... readerShellCoverHtml ...)`
2. Host tests require the native replacement contract:
   - `ReaderShellCoverView`
   - `updateCover(coverUrl: String?, title: String)`
   - a helper that maps appassets `/reader-cache/` URLs to local cache files
   - `BitmapFactory.decodeFile(...)`
   - `Canvas.drawBitmap(...)`
3. The focused red check failed against the old WebView cover path.
4. The implementation replaced the cover WebView with a native full-parent cover view inside `ReaderSurfaceHost`.
5. The focused green check and affected reader host test classes passed.

### Completed Native Tap-Zone Ordering TDD Slice

This slice fixed the race where JS reader tap-zone listeners could attach before Android native tap-zone ownership was enabled.

1. Host test now requires `openPublication(...)` to call `applySettings(settings)` before `await this.view.open(url)`.
2. The red check failed while settings were only applied after `view.open(...)`.
3. `navic-reader.js` now applies settings immediately after creating and mounting the `foliate-view`, before opening the publication.
4. The existing post-open `applySettings(settings)` remains to refresh renderer/content styling after Foliate has created its renderer.
5. The single ordering test, affected reader host classes, JS syntax check, and Android debug APK assembly passed.

### Next Required Validation Slice

Only after the native-cover and native-tap-ordering tests pass, install/release an APK and validate with adb on the phone:

- cover taps work over the image, not only margins
- cover horizontal drag changes page
- cover uses the full reader window with `contain`, no CSS margins involved
- normal EPUB pages still tap, drag, link, and image-toggle correctly
- PDF tap and drag behavior still routes through the same shell model
- logcat does not show JS reader tap-zone dispatch competing with `ReaderSurfaceHost`

Current adb status: no connected devices were available during the 2026-06-13 local validation pass.

### Current Risk

The current child-first gesture path and native cover renderer are closer to Komikku than the previous native-first/WebView-cover path, but phone behavior is not proven yet. Until a release is installed and adb-validated, cover-only input, PDF input, and renderer-surface ordering remain runtime risks.

## Phone Validation: 2026-06-14 eta64 ADB Matrix

Device and build:

- Device: Samsung `SM_F966B`, ADB serial `RFCY80551LT`.
- Package: `darkaxt.navic`.
- Installed build: `versionName=v1.0.11-eta64`, `versionCode=397`.
- Focused activity during tests: `darkaxt.navic/paige.navic.androidApp.MainActivity`.
- Evidence directory: `tmp/eta64-adb-validation/`.

Important correction:

- The relaunch/open-book observation is not counted as an app resume result because the user manually changed the phone state. Do not use that observation as evidence for or against automatic resume/cover behavior.

Validation matrix:

| Area | Test | Evidence | Result | Verdict |
| --- | --- | --- | --- | --- |
| EPUB page number | Current readable page after user placed app in EPUB | `current.png` | Title page renders, but bottom page number is drawn twice: blue foreground and gray shadow/copy. | FAIL: duplicate organic page number layer is still active. |
| Normal EPUB menu | Center tap on text page, then center tap again | `text-center-tap-1.png`, `text-center-tap-2.png` | Chrome/menu opens and hides reliably on normal text pages. | PASS for normal text center tap. |
| Normal EPUB navigation | Right/left edge taps on normal text pages | `text-right-tap-1.png`, `text-left-tap-1.png` | Right edge advances one page; left edge returns one page. | PASS for ordinary text-page tap navigation. |
| Normal EPUB drag | Swipe left/right on normal text pages | `text-swipe-left-1.png`, `text-swipe-right-1.png`, matching logs | Swipe left advances one page; swipe right returns one page. Logs still contain repeated Foliate `touchmove cancelable=false` warnings. | PASS with warning noise. |
| Suppressed cover handoff | Left-edge tap from first readable page | `left-from-page1.png`, `left-from-page1.log` | Native command dispatches `previousPage`; WebView loads `OEBPS/Text/cover.xhtml`; runtime logs `cover-document:suppressed`; screen becomes blank with only `1 / 1748`. | FAIL: going backward from first readable page reaches suppressed EPUB cover without handing off to native cover page `-1`. |
| Suppressed cover recovery | Right-edge tap from blank suppressed-cover state | `right-from-suppressed-cover.png`, matching log | Reader returns to visible title page. | PASS only as recovery; does not fix the blank suppressed-cover state. |
| Frontmatter numbering | Sequential right-edge tap walk from title/frontmatter | `frontmatter-walk/front-01.png` through `frontmatter-walk/front-05.png`, `frontmatter-walk.log` | Visible sequence includes `2 / 1748`, `3 / 1748`, `5 / 1748`, `6 / 1748`, then `15 / 1748`. | FAIL: page numbering and/or committed visible page state still skips through frontmatter/map sections. |
| Texture direction | Same frontmatter walk across title -> TOC -> maps -> Author's Note | `frontmatter-walk/frontmatter-walk.log`, `map-extreme-right.log` | Logs show `dir=next` with positive texture offsets and negative deltas at section transitions, for example `x=750 delta=-1395 dir=next` and `x=698 delta=-698 dir=next`. | FAIL: texture movement can invert during area/spine transitions. |
| Map/image edge navigation | Repeated right-edge taps on map pages | `frontmatter-walk/front-05.png` through `front-14.png`, `frontmatter-walk.log` | After reaching the map page, repeated controlled taps stopped producing `nextPage` commands, while an extreme-right tap at x=1950 later advanced to Author's Note. | FAIL: image-heavy pages still do not have reliable Komikku-equivalent top-level edge-zone ownership. |
| Image center interaction | Center tap on map image | `image-before-center.png`, `image-after-center.png`, `image-center-tap.log` | Chrome did not open, which is correct. One tap emitted five duplicate `readerContentTapHandled` bridge events. No visible image-treatment change was captured on this map. | PARTIAL: menu suppression works here, but content tap signaling is noisy/duplicated. |
| Native cover tap | Center tap on native cover | `cover-center-tap-current.png`, `cover-center-tap-hide-current.png` | Center tap opens reader chrome; second center tap hides it. It does not immediately discard the cover. | PASS for cover center tap toggle. |
| Native cover drag | Swipe left on native cover | `cover-before-drag.png`, `cover-after-drag-left.png`, `cover-drag-left.log` | Cover remains unchanged. Log shows raw motion through `KomikkuReaderNativeViewerContainer`, but no page-turn command or bridge event. | FAIL: shell/native cover has no working drag/swipe page transition. |
| Cover layout | Native cover frame | `cover-before-drag.png` | Cover uses full height with black side gutters; no sepia top/bottom margin is visible in eta64 cover state. | PASS for this cover-frame sizing case; still not evidence for all covers/aspects. |

Current prioritized eta64 defects:

1. Native cover drag/swipe is still missing. Cover tap toggles chrome, but drag does not dispatch a page transition.
2. Going backward from the first readable EPUB page lands on a blank suppressed-cover WebView page instead of native cover page `-1`.
3. Page numbering still has duplicate visual layers and unstable frontmatter jumps.
4. Texture movement still inverts at area/spine transitions, proven by ADB logs with `dir=next` and opposite-sign texture deltas.
5. Image-heavy/frontmatter pages still do not have reliable top-level edge-zone ownership; normal text pages work, but map/image pages can swallow normal edge taps.
6. Content tap signaling is duplicated on image taps and should be reduced to one explicit content-owned action per tap.

Retest script for user confirmation:

1. Start from native cover. Center tap should show chrome; center tap again should hide it.
2. Start from native cover. Drag left should currently do nothing; this is expected to reproduce the fail.
3. Tap right edge from cover into the title page. Confirm whether the page number is duplicated.
4. From the first readable title page, tap left edge. Current eta64 result should become a blank page with only the duplicated `1 / 1748`; this confirms the suppressed-cover handoff bug.
5. Return forward and tap through Contents -> Maps -> Author's Note. Watch for page jumps and texture movement inversion.
6. On a map/image page, tap the normal right edge and then the extreme right edge. If only the extreme edge works, the native top-level edge zone is still not owning image-heavy pages correctly.

## User Acceptance Analysis: 2026-06-14 eta64 Komikku Delta

This section records the user's eta64 analysis as product acceptance criteria. It supersedes treating eta64 as a release-ready reader shell. The next work must address these as architecture/model gaps, not as isolated visual tweaks.

User-observed failures and requirements:

| Area | Observation | Required direction |
| --- | --- | --- |
| Progress rail | The scrollbar is far from Komikku: it uses almost the full vertical space; next/previous buttons sit against the top and bottom menu edges; the rail covers the whole book instead of the current chapter. | Rebuild the rail as a Komikku-equivalent chapter navigator: shorter vertical track, practical previous/next controls, and chapter-local progress by default. Book-global progress can remain a secondary value, not the main thumb scale. |
| Page number rendering | Page numbers are duplicated: the organic injected number uses the book font, but a blue overlay sits on top. | Establish one page-number visual owner. User preference is the organic reader-surface number because it feels printed with the book and can inherit ebook fonts; remove the duplicate Compose/mobile overlay for WebView publications. |
| Page number model | Page numbers are not linear: observed sequence includes `1, 2, 4, 5, 14, 15, 16, 17, 26`. | Replace raw Foliate/frontmatter position exposure with a committed visible-page sequence. It must be monotonic for sequential navigation and must not jump across image/frontmatter pages unless a real multi-page relocation is intentionally requested. |
| Paper texture movement | Page textures still move randomly and do not consistently follow the page movement axis. | Tie texture movement to the same viewer-owned transition model as the page content. The texture layer must move with the visible page, including across spine/area transitions. |
| Hyperlink navigation and drag | Dragging stops working after using a hyperlink to jump directly to chapter 1. | Hyperlink jumps must reinitialize the active viewer gesture/drag state. A link relocation cannot leave the pager in a non-draggable state. |
| Cover architecture | Native cover may be unnecessary after the Komikku shell exists; it was originally added to work around the old renderer. | Re-evaluate native cover as a temporary compatibility feature. Target state should be a viewer-owned synthetic cover page or cover mode that uses the Komikku shell and can render full-window without a special WebView-era workaround. Do not keep two cover models if one controller-owned cover state can satisfy layout, input, resume, and page index `-1`. |
| Interactive content | Normal taps must belong to the native reader container. EPUB/WebView content actions should not fire from ordinary short taps. | Treat this as a fixed ownership rule: short taps and drags are native reader/controller gestures; WebView content actions only receive deliberate long-press input or a future explicit content-interaction mode. Links/images must not fight page turns or menu toggles. |
| Popup settings sheet | Popup menu is suboptimal: font size is too large, tab titles wrap, theme cannot be changed, and no General settings are interactable. | Rebuild the settings sheet closer to Komikku: compact typography, fixed-width/non-wrapping tabs or icon tabs, working theme controls, and functional General controls. Opening the sheet must overlay content without resizing/zooming the viewer. |
| Bottom/menu buttons | Chapters button does nothing. Ebook button does nothing. | Either wire these controls to real chapter/reader actions or remove them from the active chrome until implemented. Dead controls are not acceptable in release candidates. |
| Bookmark affordance | Favorite star should probably be replaced by Komikku's page-mark/bookmark UI. Star belongs to music, not ebook. | Replace ebook favorite/star affordance with a bookmark/page-mark icon and behavior. Keep music favorite semantics separate from reader bookmark semantics. |
| Sepia image/theme behavior | Sepia color filter is still disabled. | Restore sepia theme/filter behavior as part of the renderer/viewer theme contract, including image treatment that does not break transparent or formatted image backgrounds. |
| Font selection | The `Dyx` font type that resembles classic writing machines is not applied yet. | Fix font-source registration and runtime application so selected ebook fonts actually apply to content and organic page-number text. |
| Texture assets | Page textures may need replacement without recompiling the APK. | Add a future texture asset source abstraction. Default packaged textures remain bundled, but a custom folder/provider should be able to override them for local testing or user replacement. |

Priority order for the next implementation plan:

1. Fix the viewer/controller model: committed page sequence, chapter-local progress rail, hyperlink relocation state, native cover vs synthetic cover decision, and single page-number owner.
2. Fix input ownership to match the Komikku model: normal tap/drag reliability across cover, text, image, and post-link pages; content activation belongs to long press or an explicit content-interaction mode, not ordinary short taps.
3. Fix texture movement as a viewer transition layer, not a DOM-side decoration.
4. Replace the progress rail and chrome controls with a closer Komikku port, including page-mark/bookmark semantics and no dead buttons.
5. Rebuild the settings sheet so the current controls are actually usable, compact, and non-wrapping.
6. Restore reader theme/font behavior: sepia, image treatment, `Dyx`, and custom font/source handling.
7. Add configurable/custom texture sources after the packaged texture pipeline is stable.

Do not publish another release for isolated cosmetic changes from this list. The next release should be justified by one of the major acceptance failures above being fixed and verified.

2026-06-14 page-number slice:

- Decision: keep the organic reader-surface page number and remove the duplicate Compose/mobile page indicator for WebView publications.
- Rationale: the user prefers the page number that looks printed into the book surface, not a mobile UI overlay, especially because it can inherit the ebook font. The controller still owns normalized page/progress state; this is a visual ownership exception, not permission for WebView/Foliate to own progress UI.
- Code guardrail: `shouldShowNativeReaderPageIndicator(...)` returns false for `WebViewPublicationReaderViewer`, and `ReaderViewerTest.webViewPublicationKeepsOrganicPageIndicatorInsideReaderSurface` prevents reintroducing the duplicate native overlay.

2026-06-14 bookmark/page-mark slice:

- Source reference: Komikku `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt` uses `Icons.Outlined.Bookmark` / `Icons.Outlined.BookmarkBorder`, a `bookmarked` state, and `onToggleBookmarked`.
- Decision: reader chrome uses a page-mark/bookmark affordance, not Navic's music favorite star. Music favorite semantics stay out of ebook chrome.
- Code path: `ReaderScreen` now routes the top-right page mark through `coordinator.toggleCurrentBookmark()`, `controllerState.currentLocationBookmarked`, and `controllerState.canBookmarkCurrentLocation`.
- Verification:
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerTopChromeUsesKomikkuBookmarkPageMarkInsteadOfMusicStar`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBookmarkStateTest --tests paige.navic.reader.ReaderControllerTest.currentBookmarksAreControllerOwnedAndDoNotEmitEngineCommands --tests paige.navic.reader.ReaderCoordinatorTest.currentBookmarkTogglesRouteThroughControllerWithoutEngineBridgeCommands`: passed.

2026-06-14 engine renderer boundary slice:

- Decision: `ReaderViewerHost` no longer receives the concrete `ReaderViewer` or branches on `WebViewPublicationReaderViewer`. Komikku viewers keep lifecycle and tap-action ownership, while content mounting receives only a render-only `ReaderEngineRenderer` descriptor.
- Code path: `ReaderViewer.engineRenderer` exposes `ReaderEngineRenderer.Empty` or `ReaderEngineRenderer.FoliatePublication.from(viewState)`. `ReaderScreen` passes `engineRenderer = viewer.engineRenderer` into `ReaderViewerHost`; the host maps `ReaderEngineRenderer.FoliatePublication` to `ReaderEngineWebViewHost`.
- Rationale: this makes the boundary closer to the target `ReaderController` / `ReaderEngine` split. Foliate-backed EPUB/PDF remains an engine capability mounted inside Komikku's viewer container, not a concrete viewer class that the renderer host can inspect or control.
- Verification:
  - Red check first failed on `ReaderKomikkuBackboneResetTest.readerViewerHostConsumesEngineRendererDescriptorInsteadOfConcreteViewerClass` because `ReaderViewerHost` still inspected `WebViewPublicationReaderViewer`.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.readerViewerHostConsumesEngineRendererDescriptorInsteadOfConcreteViewerClass`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest --tests paige.navic.reader.ReaderViewerTest --tests paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest`: passed.
  - `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest --tests paige.navic.reader.ReaderCoordinatorTest.activeEngineIsSelectedThroughAdapterContractInsteadOfFoliateSpecialCase --tests paige.navic.reader.ReaderCoordinatorTest.pdfPublicationRoutesThroughDefaultPdfEngineAdapter`: passed.

## Objective

Replace Navic's current reader shell with a Komikku-equivalent reader architecture. This is not a "Komikku-style" visual pass and not another sequence of isolated fixes on the existing `ReaderScreen` scaffold.

The target is to port/adapt Komikku's reader ownership model:

- Rendered content is a full-window viewer surface.
- Reader gestures are owned by the reader/viewer layer, not by EPUB HTML.
- Tap-zone visualization is a separate visual overlay, not the input authority.
- Menus, settings, brightness/filter overlays, and progress controls are Compose overlays above the viewer. Page-number state is controller-normalized, but the preferred visual treatment for EPUB/PDF is the organic reader-surface number, not a mobile chrome overlay.
- Opening menus/settings never resizes, pads, zooms, or relayouts the content surface.
- Navic's Foliate/PDF/Bindery code remains the content backend, but it must sit behind the Komikku-equivalent shell contract.

Partial breakage of the current reader shell is acceptable during this port if it moves the architecture toward the Komikku model. The wrong path is continuing to patch the current shell one symptom at a time.

## Long-Term Reader Objective

The target product shape is:

```text
Komikku reader frontend/controller
  + Navic library, Bindery, cache, playback, and settings stores
  + EPUB/PDF/content engines adapted behind controller interfaces
  + Anx Reader/Foliate ebook capabilities where they improve the engine layer
  + later audiobook/ebook sync labels as a controller-owned media overlay feature
```

Komikku is the source for the reader's bones and muscles: root view stack, viewer mounting, gesture ownership, overlay chrome, progress navigator, tap-zone visualization, and settings dialog behavior.

Anx Reader is not the source for Navic's reader shell. It is a Flutter app whose reader UI and `EpubPlayerState` directly own the WebView, progress, settings, notes, search, TTS, and UI callbacks. Copying that object model raw would recreate the same clash we are trying to remove. Anx should be used as an ebook capability reference and Foliate integration reference, not as the top-level Android interaction architecture.

## Controller Ownership Boundary

Navic's reader must have a controller boundary before EPUB/PDF features are restored.

Controller-owned state:

- menu visibility and system bar visibility
- current publication identity and active engine
- current logical location: page index, total pages, chapter title, href/cfi/locator, percentage
- cover/shell-cover state, including whether the synthetic cover is visible
- tap-zone preset, inversion, smaller zones, and tap-zone visual overlay visibility
- progress rail state and page-number state; EPUB/PDF page-number visuals may be rendered on the reader surface when that is the only active page-number layer
- active settings scope: global defaults versus per-book overrides
- content-action claims: link, image, selection, form/media control, annotation, footnote
- readaloud/audiobook sync state and active audio metadata labels

Engine-owned behavior:

- opening and rendering EPUB/PDF/image/comic content inside the viewer container
- resolving href/cfi/locator/page commands into renderer movement
- emitting relocation/search/selection/link/image/annotation/media-cue events to the controller
- applying controller settings to renderer-specific CSS/native attributes
- exposing explicit content-action claims so reader-wide tap handling can be suppressed only when content actually handled the gesture

Blocked ownership leaks:

- EPUB HTML, PDF.js HTML, or Foliate JS may not decide global menu visibility.
- Engine code may not mount permanent reader chrome.
- Engine code may not resize the root viewer to make room for settings, progress, or menus.
- Engine code may not own page numbering as final state truth; it reports raw relocation data and the controller normalizes it. A renderer-surface page-number layer is acceptable only as the chosen visual presentation of controller-fed/engine-reported page state and only if no Compose duplicate is active.
- Audio/TTS/media overlay code may not own player chrome; it reports cues/labels and commands through the controller.

## 2026-06-14 Focus Lock: Komikku Frontend, Anx Capabilities

The product target is not a Komikku-looking Navic screen. The target is a Komikku reader frontend/controller with ebook capabilities restored behind adapters. If a future change makes the UI look more like Komikku but keeps Navic/Foliate/WebView as the owner of taps, chrome, progress, or settings, that change is off-target.

What we want:

- A full-window native reader stack that behaves like Komikku for content mounting, gesture ownership, overlay chrome, settings, progress, and tap-zone visualization.
- EPUB, PDF, search, notes, highlights, bookmarks, image handling, fonts, TTS/readaloud, and media-overlay labels restored as engine/domain capabilities that report into the controller.
- Navic/Bindery/cache/audio integration preserved outside the reader shell so private Bindery features and the public fork boundary remain intact.
- A path where comic/image reading, EPUB reading, PDF reading, and later synced audiobook-ebook labels all use the same reader controller and chrome contract.

Komikku modules to port as the front-end backbone:

- Root stack: `tmp/references/komikku/app/src/main/res/layout/reader_activity.xml:1-31` defines the `FrameLayout` root, `reader_container`, `viewer_container`, passive `navigation_overlay`, and full-window `compose_overlay`.
- Viewer lifecycle: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:941-967` creates the active viewer, destroys/removes the previous viewer, clears `viewerContainer`, and mounts `newViewer.getView()` directly.
- Viewer contract: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt:12-22` defines the minimal viewer interface: Android `View`, destroy, chapters, movement, and input hooks.
- Gesture stream: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt:73-78` dispatches to the child first, then runs the gesture detector on the same stream. This is the model Navic must preserve for WebView/PDF/image content.
- Pager action mapping: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:130-134` maps `MENU`, `NEXT`, `PREV`, `RIGHT`, and `LEFT` inside the viewer, not inside HTML.
- Webtoon action mapping: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:144-146` keeps menu actions in the viewer and maps next/previous regions to scroll movement.
- Navigation regions: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt:12-52` defines region types, inversion, constant top menu area, region lookup, and menu fallback.
- Tap-zone overlay: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt:19-65` draws regions only. It is visual/debug UI, not input authority.
- Compose overlay mount: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:317-390` mounts `ReaderPageIndicator` and `ReaderSettingsDialog` above the viewer.
- Content/chrome overlays: `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:632-686` mounts `ReaderContentOverlay` and `ReaderAppBars` as overlay UI, not as layout that changes viewer size.
- Reader chrome: `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt:50`, `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:53`, and `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt:23` are the source structures for app bars, progress navigator, and dense tabbed settings.

Anx modules to adapt as engine/domain capabilities:

- Foliate event and location plumbing: `tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194` wires relocation, overlays, media overlays, CFI, progress, and load events. Navic should consume equivalent events through `ReaderEngineEvent`.
- Link/image/view click taxonomy: `tmp/references/anx-reader/assets/foliate-js/src/view.js:216-327` distinguishes internal links, external links, image clicks, and view clicks. Navic should translate this into typed content-action claims so chrome does not open when real content handled a gesture.
- Annotation overlay hooks: `tmp/references/anx-reader/assets/foliate-js/src/view.js:335-380` adds/removes/draws annotations and emits annotation clicks. Navic should adapt this into notes/highlights/bookmark domain stores, not WebView-owned UI.
- PDF book integration: `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614` builds a Foliate-compatible PDF book with sections, outline/TOC resolution, page lookup, and cover behavior. This belongs behind a `PdfEngineAdapter`.
- Reader bridge event catalog: `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:667-804` registers callbacks for click, TOC, selection, annotation, search, and image handling. The callback list is useful; its Flutter `EpubPlayerState` ownership model is not.
- Style catalog: `tmp/references/anx-reader/lib/models/book_style.dart:4-17`, `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:181-206`, and `tmp/references/anx-reader/assets/foliate-js/src/book.js:1673-1707` cover font size/family/weight, line height, paragraph spacing, margins, column count, writing mode, background, custom CSS, and heading scale. These become controller settings mapped into engine adapters.
- Font sources: `tmp/references/anx-reader/lib/providers/fonts.dart:16-122`, `tmp/references/anx-reader/lib/service/font.dart:22-24`, and `tmp/references/anx-reader/lib/models/font_model.dart:28-33` are references for remote font manifests, local font import, and WebView font URLs.
- Search/TTS/readaloud capability hooks: `tmp/references/anx-reader/assets/foliate-js/src/book.js:1862-1960` exposes TTS and search commands. Navic should expose equivalent commands through engine adapters while audio/readaloud playback and synced labels stay controller/Navic-owned.

Clash-prevention rules:

- Komikku owns the shell. Anx/Foliate can supply renderer capabilities but cannot own root layout, menu visibility, page/chapter progress UI, settings UI, or tap zones. Page-number presentation is the one explicit exception: the user prefers the organic reader-surface visual, while normalized page state still belongs to the controller.
- The controller normalizes raw EPUB/PDF relocation into user-facing page/progress state. Engine events are evidence, not final UI truth.
- Content actions must be explicit and typed: link, image, selection, form control, media control, annotation, footnote. A generic WebView hit must not suppress center-tap menu behavior.
- Settings from Anx/Readest/Navic can expand the catalog, but the surface must remain the Komikku tabbed overlay model.
- PDF must be a separate engine adapter sharing the same Komikku input/chrome contract, not a special-case screen.
- Readaloud/audiobook metadata labels are controller-owned media-overlay state. They can consume Foliate media-overlay/TTS cues, but they must not create a separate floating reader chrome.
- Texture, page-curl, paper, and cover visual polish stay below the ownership work. They are not substitutes for the Komikku root/viewer/overlay/controller split.

## 2026-06-14 Capability Ownership Map

This section is the guardrail for the Komikku frontend / Anx capability direction. A feature is accepted only when it lands in the correct layer. If the quickest implementation puts a feature in the wrong owner, it is a regression even if it appears to work on the phone.

### Root, Viewer, And Lifecycle

Owner: Komikku-derived reader frontend.

Source references:

- `tmp/references/komikku/app/src/main/res/layout/reader_activity.xml:1-31`: full-window `FrameLayout` root, nested viewer container, passive navigation overlay, and full-window Compose overlay.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:941-967`: `updateViewer()` destroys the old viewer, clears `viewerContainer`, and mounts the new viewer view directly.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt:12-22`: minimal viewer contract.

Navic target:

- `KomikkuReaderNativeFrameHost` is the only root stack authority.
- `ReaderViewer` is the lifecycle boundary for EPUB, PDF, paged, vertical-paged, scrolled/webtoon, and future spread/page-curl viewers.
- The remaining Compose-hosted WebView bridge is an implementation gap, not the final model. The target is concrete viewer adapters mounted into the native viewer slot, with renderer details hidden behind `ReaderEngine`.

Blocked:

- No `Scaffold` content slot, bottom sheet, or legacy WebView wrapper may own reader surface size.
- No cover/image/PDF/EPUB special case may bypass the viewer lifecycle boundary.

### Input, Tap Zones, And Drag

Owner: Komikku-derived viewer/navigation layer.

Source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt:73-78`: pager dispatches the stream to children first, then observes the same stream with a gesture detector.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:130-134`: pager viewers map `MENU`, `NEXT`, `PREV`, `RIGHT`, and `LEFT`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:144-146`: webtoon viewers map next/previous regions to scroll.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt:12-52`: region presets, inversion, top menu strip, and menu fallback.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt:19-65`: tap-zone overlay is visual only.

Anx capability reference:

- `tmp/references/anx-reader/assets/foliate-js/src/view.js:216-327`: Foliate distinguishes internal links, external links, image clicks, and view clicks.

Navic target:

- Viewer/tap-zone actions become neutral `KomikkuNavigationRegion` results, then viewer-owned navigation actions, then controller commands.
- Foliate/WebView may suppress shell taps only by emitting an explicit typed `ReaderContentAction` claim.
- Drag must stay with the renderer/viewer stream; the shell must observe confirmed taps without stealing drag.

Blocked:

- No generic WebView hit test can suppress menu/navigation.
- No content HTML can own global menu visibility.
- The visual tap-zone overlay must never become the input layer.

### Chrome, Settings, Progress, And Page Indicator

Owner: Komikku-derived Compose overlay/controller.

Source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt:50-265`: overlay app bars, animated top/bottom bars, and navigator placement.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt:16-55`: top title/back/bookmark chrome.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt:29-190`: centered bottom action row and settings entry.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:53-276`: horizontal/vertical reader progress navigator.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt:23-68`: tabbed reader settings dialog.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:29-356`, `GeneralSettingsPage.kt:35-151`, and `ColorFilterPage.kt:23-111`: settings groups and control density.

Anx catalog references:

- `tmp/references/anx-reader/lib/models/book_style.dart:3-33`: style dimensions.
- `tmp/references/anx-reader/lib/widgets/reading_page/style_widget.dart:143-320`: font, page-turn, line-height, paragraph spacing, and font-size controls.
- `tmp/references/anx-reader/lib/widgets/reading_page/more_settings/reading_settings.dart:131-213`: column count and column threshold controls.
- `tmp/references/anx-reader/lib/widgets/reading_page/more_settings/style_settings.dart:91-330`: indent, side margins, letter spacing, heading scale, and related style controls.

Navic target:

- Komikku supplies the overlay shape and behavior: top/bottom bars, centered bottom action row, side or bottom progress navigator, tabbed settings dialog, and page indicator over content.
- Anx supplies the settings catalog only. Its Flutter widgets are not copied as the UI shell.
- Reader settings are controller state and are emitted to engines as typed `ReaderEngineCommand.ApplySettings`.

Blocked:

- No reader option list may grow into a docked settings page that resizes the content.
- No Foliate/WebView runtime may decide progress rail behavior. Page-number visual rendering may live in the reader surface only to preserve the organic printed-on-page look, and must not create a second native/mobile overlay.

### EPUB, PDF, Search, Notes, And Style Engine Capabilities

Owner: Anx/Foliate-derived engine adapters behind Navic controller contracts.

Source references:

- `tmp/references/anx-reader/assets/foliate-js/src/book.js`: command surface, style application, search/TTS hooks, navigation, and book opening.
- `tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194`: relocation and progress event construction.
- `tmp/references/anx-reader/assets/foliate-js/src/view.js:335-397`: annotation drawing and annotation click hooks.
- `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614`: PDF-as-book integration.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:667-804`: bridge callback catalog for click, TOC, selection, annotation, search, and image events.
- `tmp/references/anx-reader/lib/service/book_player/book_player_server.dart:99-127`: local font/static asset serving model for WebView rendering.

Navic target:

- EPUB and PDF engines implement `ReaderEngine`.
- Engine adapters translate typed controller commands into renderer-specific commands and translate renderer callbacks into `ReaderEngineEvent`.
- Notes, highlights, bookmarks, search state, TOC state, and page/progress state are controller/domain state first; renderer annotation drawing is an engine capability second.

Blocked:

- No `EpubPlayerState`-style object may become the owner of reader UI state.
- No direct bridge command may escape from the engine adapter into `ReaderScreen`.

### Fonts And Reader Style Sources

Owner: Navic settings/domain store plus Anx-derived engine capability.

Source references:

- `tmp/references/anx-reader/lib/providers/fonts.dart:16-103`: remote manifest and font download flow.
- `tmp/references/anx-reader/lib/providers/font_list.dart:19-44`: local font enumeration and deletion.
- `tmp/references/anx-reader/lib/service/font.dart:22-24`: local font import.
- `tmp/references/anx-reader/lib/models/font_model.dart:28-33`: WebView-accessible font URLs.

Navic target:

- Font sources become a Navic settings/domain feature: built-in fonts, local imports, optional remote manifests, and WebView-accessible font URLs.
- The settings UI lives in the Komikku settings dialog; the engine adapter only receives normalized font/style settings.

### Audio Metadata Labels And Synced Readaloud

Owner: Navic controller/domain layer.

Source references:

- `tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194`: relocation and media-overlay-capable event path.
- `tmp/references/anx-reader/lib/widgets/reading_page/tts_widget.dart` and `tmp/references/anx-reader/lib/service/tts`: TTS capability references only.

Navic target:

- Audio metadata labels, active cue labels, readaloud highlight state, and audiobook/ebook sync state are controller-owned media-overlay state.
- Foliate/Anx media overlay data can feed the controller through typed events.
- Bindery/private server metadata remains Navic-owned and is not replaced by Anx sync code.

Current focus rule:

- Do not prioritize Storyteller client UI or Whispersync-style cross-matching in this port. The reader must first have the Komikku controller backbone; audiobook/ebook sync labels come back through the controller once the Bindery-side contract is ready.

### Cache, Download, And Persistence

Owner: Navic/Bindery domain services.

Navic target:

- Download/cache lifecycle, progress persistence, bookmarks/notes storage, OPDS actions, and public/private feature boundaries remain outside the renderer.
- The controller emits save/cache/bookmark intents. Domain services persist them. Engines never decide server/cache truth.

Immediate implementation priority from this map:

1. Persist `ReaderCoordinatorStep.progressToSave` through Navic's Bindery/domain layer without exposing bridge details to `ReaderScreen`.
2. Continue closing legacy WebView escape hatches so active EPUB/PDF hosts only communicate through `ReaderEngineHostCommand` and `ReaderEngineHostEvent`.
3. Replace the current reader options surface with a Komikku `ReaderSettingsDialog`-equivalent container before adding more Anx settings.
4. Rebuild the progress rail/page indicator from Komikku `ChapterNavigator` semantics, then feed it controller-normalized EPUB/PDF progress.
5. Only after those ownership pieces are stable, resume cover animation, textures, page-curl/spread mode, and synced audiobook label polish.

Immediate focus after this register update:

1. Keep the native Komikku frame/root as the only shell authority.
2. Continue restoring EPUB/PDF capabilities through typed controller and engine commands/events without letting WebView/PDF.js own chrome.
3. Expand controller-owned ebook domain state for annotations, bookmarks, progress, cache, settings defaults, and readaloud labels.
4. Only after the ownership boundary is stable, resume visual polish such as textures, cover animation, and optional page-curl/spread behavior.

## Modules To Port Or Adapt

### From Komikku: Port As Backbone

Use the local source at `tmp/references/komikku` as the behavior source for these modules:

- `reader_activity.xml`: exact full-window `FrameLayout` stack with `reader_container`, `viewer_container`, passive `navigation_overlay`, and `compose_overlay`.
- `ReaderActivity.updateViewer()`: create/destroy/swap viewer instances and mount the active viewer directly into `viewer_container`.
- `Viewer.kt`: viewer lifecycle contract: `getView()`, `destroy()`, `setChapters(...)`, movement, key events, generic motion events.
- `ViewerNavigation.kt` and `viewer/navigation/*.kt`: normalized region semantics, tap-zone presets, smaller zone sizing, inversion, and menu fallback.
- `ReaderNavigationOverlayView`: visual-only tap-zone overlay; never input authority.
- `PagerViewer`, `Pager`, `WebtoonViewer`, and `WebtoonRecyclerView`: child-first gesture stream, confirmed-tap dispatch, drag/scroll suppression, page movement semantics, double-page/orientation concepts.
- `ReaderAppBars`, `ReaderTopBar`, `ReaderBottomBar`: overlay chrome that slides/fades over content.
- `ChapterNavigator`: vertical/bottom progress navigator with previous/next actions, current/total labels, slider, and haptic movement.
- `ReaderSettingsDialog` and reading/general/filter pages: dense tabbed overlay settings model.
- `ReaderPageIndicator` and `ReaderContentOverlay`: organic page number/overlay surfaces that do not resize the content.

### From Anx Reader: Adapt As Engine Capabilities

Use the local source at `tmp/references/anx-reader` as a capability reference for these modules:

- `assets/foliate-js/src/book.js`: Foliate command surface, relocation payload, annotations, bookmark hooks, search callbacks, style application, href/cfi/percent navigation, page-turn style switching, reading feature hooks.
- `assets/foliate-js/src/view.js` and `progress.js`: progress/location calculation and CFI relocation plumbing.
- `assets/foliate-js/src/pdf.js` plus bundled `vendor/pdfjs`: PDF-as-book integration, TOC/outline mapping, cover extraction, page rendering cache.
- `lib/page/book_player/epub_player.dart`: useful event taxonomy and bridge shape only; do not copy its UI ownership. Its handlers show what events Navic's engine adapter should emit: load, relocated, click, external link, TOC, selection, annotation, search, image click, bookmark, translation.
- `lib/models/book_style.dart`: style dimensions worth mapping into Navic settings: font size, family, weight, line height, letter/word spacing, paragraph spacing, side/top/bottom margins, indent, max column count, heading scale, column threshold.
- `lib/models/reading_rules.dart`: optional reading transforms such as Chinese conversion and bionic reading, implemented later as engine settings.
- `lib/service/font.dart`, `lib/models/font_model.dart`, `lib/providers/fonts.dart`: font import/source model to adapt into Navic's font source support.
- `lib/widgets/reading_page/more_settings/*.dart`: settings catalog reference, not layout source. Convert the settings into Komikku's tabbed overlay model.
- `lib/widgets/reading_page/progress_widget.dart`: progress data reference, not UI source.
- `lib/widgets/reading_page/tts_widget.dart`, `tts_fab.dart`, and `lib/service/tts/*`: TTS/service capability reference. For Navic, this belongs behind readaloud/audiobook sync controller events, not a separate floating player owner.
- `lib/dao/book_note.dart`, `lib/models/book_note.dart`, `lib/providers/book_notes.dart`, `lib/constants/note_annotations.dart`: note/highlight/bookmark persistence ideas to adapt into Navic's domain stores.
- `lib/service/sync/*`: sync architecture reference only; Navic's Bindery/OPDS model remains the source of server truth.

### Navic-Owned Integrations

These remain Navic responsibility and must not be replaced by Anx or Komikku:

- Bindery credentials, OPDS actions, publication download, and cache lifecycle.
- Music and audiobook player arbitration and miniplayer area rules.
- Storyteller/merged ebook-audio publication metadata ingestion when needed.
- Public fork/private Bindery feature boundary.
- Existing app settings/search integration.
- Release pipeline and Android packaging.

## Capability Integration Sequence

Restore features in this order so the reader shell does not collapse back into runtime patches:

1. Define `ReaderController`, `ReaderEngine`, `ReaderEngineEvent`, and `ReaderEngineCommand` contracts.
2. Keep the Komikku root/viewer/overlay stack active with a fake engine until menu, progress rail, tap zones, settings overlay, and page indicator are stable.
3. Reattach EPUB through a `FoliateEpubEngineAdapter` mounted inside `viewer_container`.
4. Move current Navic/Anx-style bridge events into controller events: relocation, TOC, search, selection, link, image, annotation, load/error.
5. Normalize page numbering and cover suppression in the controller, not in scattered Foliate callbacks.
6. Add publication cache/progress persistence as Navic domain services consumed by the controller.
7. Add PDF as a separate `PdfEngineAdapter`; it must share the same Komikku input/chrome contract.
8. Add font sources, themes, paragraph spacing, margins, writing direction, column count, and custom CSS as controller settings mapped into engine adapters.
9. Add notes/highlights/bookmarks/search UI as controller overlays and domain stores.
10. Add readaloud/audiobook sync labels and media-overlay cue events; the engine emits cue positions, Navic audio owns playback.
11. Only after the above, evaluate optional page-curl/spread animations as viewer-mode enhancements.

## Komikku Source Evidence

The source reference is the local clone at:

```text
tmp/references/komikku
```

The implementation files that define the behavior we are porting:

- `app/src/main/res/layout/reader_activity.xml`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- `app/src/main/java/eu/kanade/presentation/reader/ReaderContentOverlay.kt`
- `app/src/main/java/eu/kanade/presentation/reader/ReaderPageIndicator.kt`
- `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`
- `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt`
- `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt`
- `app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt`
- `app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`
- `app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/navigation/*.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`

## Documented Source Findings

These are the exact behaviors to port. They are not visual inspiration.

### Root View Stack

`tmp/references/komikku/app/src/main/res/layout/reader_activity.xml:1-31`

Komikku's reader root is a `FrameLayout` containing:

```text
FrameLayout root
  FrameLayout reader_container
    FrameLayout viewer_container
  ReaderNavigationOverlayView navigation_overlay
  ComposeView compose_overlay
```

The viewer is `match_parent` width/height. The navigation overlay is `match_parent`, `clickable=false`, and `focusable=false`. Compose is another full-size sibling. There is no scaffold content slot, no `innerPadding`, and no bottom bar that changes the viewer size.

Navic implication:

- `ReaderScreen` must remain a full-window stack.
- Any menu, settings, progress rail, tap-zone visualizer, title bar, paper texture, or page number is a sibling overlay above the renderer.
- EPUB, PDF, and cover rendering must not be measured inside chrome.

### Viewer Creation Boundary

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:941-968`

`ReaderActivity.updateViewer()` creates a viewer from reading mode, destroys/removes the previous viewer, calls `viewModel.onViewerLoaded(newViewer)`, then mounts `newViewer.getView()` directly into `binding.viewerContainer`.

Navic implication:

- Navic needs an explicit `ReaderViewerHost` boundary.
- Foliate EPUB, PDF.js/native PDF, and shell cover should be viewer adapters behind that host.
- The shell cover must not be a one-off Compose/WebView island with separate touch behavior.

### Gesture Stream Ownership

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt:73-79`

Komikku's pager receives `dispatchTouchEvent`, calls `super.dispatchTouchEvent(ev)` first, then passes the same stream to `GestureDetectorWithLongTap`. The gesture detector only emits confirmed taps/long taps. It does not steal the drag stream from the pager.

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:120-135`

The pager viewer converts the confirmed tap into normalized `x/y` coordinates and calls `config.navigator.getAction(pos)`. Actions are then dispatched to `activity.toggleMenu()`, `moveToNext()`, `moveToPrevious()`, `moveRight()`, or `moveLeft()`.

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonRecyclerView.kt:72-79` and `:242-248`

The webtoon recycler feeds events to its detector and suppresses taps during manual scroll. This is why scrolling does not accidentally become a menu/page action.

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:134-147`

Webtoon mode uses the same normalized navigation model, but dispatches next/previous to scroll down/up instead of pager page turns.

Navic implication:

- The input owner is the native reader/viewer surface, not Foliate HTML and not a Compose `pointerInput` layer.
- The stream must still reach the renderer so drag/swipe/selection/content gestures work.
- Reader-wide actions should be produced from confirmed taps only, not raw `ACTION_DOWN`.
- Content-action claims must be explicit: links, images, text selection, media controls, and editable nodes suppress reader-wide menu/page actions for that gesture.

### Navigation Model

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt:45-60`

Komikku applies explicit navigation regions first. If no explicit region matches, the top constant menu strip can return menu; otherwise the default fallback is menu. Region size is `0.33` normally and `0.25` when smaller tap zones are enabled.

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/navigation/LNavigation.kt:18-35`

L-shaped maps top plus left-middle to previous, right-middle plus bottom to next, and center to menu fallback.

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/navigation/KindlishNavigation.kt:18-27`

Kindle-ish maps left/lower to previous, lower/right to next, and top/center to menu fallback.

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/navigation/EdgeNavigation.kt:18-31`

Edge maps left/right edges to next and the bottom center to previous.

Navic implication:

- `ReaderChromeState.kt` and `navic-reader-helpers.js` must stay byte-for-byte behaviorally aligned with those region maps.
- The debug overlay must draw only explicit navigation regions, not menu fallback regions.
- Menu fallback is behavior, not a visible zone.

### Visual Tap-Zone Overlay

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt:19-110`

The navigation overlay stores the current `ViewerNavigation`, draws its regions in `onDraw`, and fades/hides itself. It is declared non-clickable/non-focusable in the root layout and does not decide actions.

Navic implication:

- "Show tap zones" is a diagnostic/training overlay only.
- It must be non-interactive.
- It must never be the page-turn implementation.

### Chrome And Progress

`tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:337-353` and `:621-720`

The Compose overlay is a full-size `Box`. It shows `ReaderPageIndicator`, `ReaderContentOverlay`, and `ReaderAppBars` above the viewer. `ReaderAppBars` receives reader state and callbacks but does not own the viewer's measured size.

`tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt:108-270`

The top bar slides/fades from the top. The navigator can be vertical left, vertical right, or bottom. The bottom action bar is compact and icon/action oriented.

`tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:52-80` and `:192-275`

`ChapterNavigator` chooses horizontal or vertical mode. Vertical mode is a side rail with previous/next buttons, current page text, slider, and total page label.

Navic implication:

- Reader chrome must appear over content, not dock content.
- The side progress rail is part of the reader chrome layer.
- Opening chrome must not zoom, shrink, crop, or repaginate EPUB/PDF/cover content.

### Settings Dialog

`tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt:22-70`

Settings are a tabbed overlay dialog with Reading mode, General, and Custom filter tabs. The dialog height is capped to 75% of the window and it coordinates menu visibility without resizing the viewer.

`tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:29-122`

Reading settings are dense chip groups/toggles/sliders for reading mode, rotation, tap zones, image scale, zoom start, page layout, and smaller tap zones. Webtoon and pager modes expose different controls.

Navic implication:

- Navic reader settings should be a tabbed overlay, not a docked list.
- More eBook controls can be added only if they fit this overlay structure.
- The global Settings > Ebooks page remains for defaults; the in-reader dialog is for active reading behavior and per-book overrides.

## Komikku Behavior Contract To Port

The behavior to replicate is architectural, not cosmetic.

### Root Stack

Komikku uses Android view layering:

```text
reader root
  viewer container
    active viewer view
  navigation overlay view
  compose overlay
```

Important consequences:

- The viewer consumes the full reader window.
- The overlay does not give the viewer `innerPadding`.
- Opening app bars, settings, or progress controls does not shrink, zoom, crop, or relayout the viewer.
- Reader chrome is a sibling above content, not a layout parent around content.

Navic acceptance rule: the EPUB/PDF/cover renderer must be mounted as a full-window content surface. Any Compose or Android overlay must be a sibling above it.

### Input Ownership

Komikku input is viewer-owned and normalized:

1. The active viewer receives a tap from the pager/recycler layer.
2. The tap position is converted to normalized `x/y` coordinates in `0..1`.
3. The selected `ViewerNavigation` preset returns a semantic action.
4. The viewer dispatches that action to menu, next, previous, left, right, scroll up, or scroll down.
5. The visual navigation overlay is not the decision-maker.

Navic acceptance rule: Foliate HTML, PDF.js HTML, shell-cover HTML, and Compose debug overlays are not allowed to own reader-wide taps. They can claim real content actions such as links, image tint toggles, media controls, text selection, or form controls. If they do not explicitly claim the touch, the reader-level navigation model handles it.

### Navigation Presets

Komikku navigation presets are region maps. Navic must match their semantics before adding extra controls.

Default/L-shaped:

```text
top strip          -> previous
left middle strip  -> previous
right middle strip -> next
bottom strip       -> next
unassigned center  -> menu fallback
```

Kindle-ish:

```text
top strip          -> menu fallback
left lower area    -> previous
right/lower area   -> next
```

Edge:

```text
left edge          -> next
right edge         -> next
bottom center      -> previous
unassigned center  -> menu fallback
```

Right and left:

```text
left side          -> left
right side         -> right
center             -> menu fallback
```

Disabled:

```text
all unassigned     -> menu fallback
```

Komikku uses a normal region size of about one third of the screen and a smaller region size of about one quarter. Navic must not keep the previous `0.25 / 0.20` model as the default behavior.

### Menu Overlay

Komikku's menu is a chrome overlay:

- It appears above content.
- It does not resize the page.
- The top bar contains navigation/title/bookmark-type actions.
- Progress controls can appear as a side rail or bottom rail depending on mode/orientation/preferences.
- Settings open as an overlay dialog/panel with tabs, not as a docked settings page that consumes reader viewport height.

Navic acceptance rule: menu visibility changes may affect overlay alpha/position, but they must not change the measured size of the content renderer.

### Progress Rail

Komikku's progress control is not a generic bottom application bar. In the screenshots and source behavior, it is a reader navigator:

- It can be vertical on the side.
- It carries current/total labels.
- It is present only as part of reader chrome.
- It does not force the content surface to resize.

Navic acceptance rule: page/progress UI should be moved out of the old bottom chrome surface and into the overlay chrome model.

### Settings Overlay

Komikku's settings are grouped by reader concerns:

- Reading mode
- General
- Custom filter

The settings surface is dense, tabbed, and temporary. It overlays the reader. It is not an always-visible list stacked below content.

Navic acceptance rule: new reader options from Anx Reader, Komikku, Readest, or Navic-specific EPUB/audio features must be organized into the overlay settings model, not appended into a growing docked list.

## Current Navic Divergences

These are known deviations that must be fixed as part of the port:

- The old Navic reader shell treated chrome as layout, not overlay.
- The previous center-tap handling depended on WebView/HTML paths too often.
- The cover path has repeatedly behaved differently from regular EPUB pages because the input layer did not own the entire visible surface.
- Tap-zone constants and priority order drifted from Komikku.
- Content actions and reader-wide actions were mixed together; this caused images, links, and center-menu taps to interfere with each other.
- Debug/visible tap zones were treated too close to input authority.
- Progress/page-number work was patched around renderer relocation behavior instead of being fed by a stable reader-shell state model.

## Required Next Work

The next implementation work must be done in this order:

1. Make the Komikku navigation model explicit in Navic common code and tests.
2. Replace Navic's old tap-zone sizes and priority order with the Komikku-equivalent model.
3. Make Android's native reader input surface use that common navigation model.
4. Ensure Foliate/PDF/WebView content can only suppress reader navigation through explicit content-action claims.
5. Keep the shell/content/chrome split intact while doing this. Do not reintroduce `Scaffold(bottomBar = ...)`, content `innerPadding`, or a docked settings panel.
6. After that, continue to progress rail, settings dialog density, and cover/PDF polish.

## Explicitly Blocked Regressions

Do not reintroduce any of these patterns:

- A reader bottom bar that changes the content viewport size.
- Reader settings mounted as a layout-consuming bottom sheet.
- A WebView-only tap handler as the source of truth for page turns.
- Shell-cover HTML/CSS as the primary fix for cover touch behavior.
- Visible tap-zone overlay deciding navigation.
- Page texture/page-number work before the input ownership model is stable.

## Actual Komikku Architecture

Komikku's reader root is a view stack, not a Compose scaffold:

```text
FrameLayout root
  FrameLayout reader_container
    FrameLayout viewer_container
  ReaderNavigationOverlayView navigation_overlay
  ComposeView compose_overlay
```

This matters. The viewer content is mounted as a full-size Android view in `viewer_container`. The Compose overlay is a sibling above it, not a parent that controls the viewer's available size.

`ReaderActivity.setComposeOverlay()` mounts a `Box(Modifier.fillMaxSize())` and places reader UI above the viewer:

- `ReaderPageIndicator` when menus are hidden and page numbers are enabled.
- `ReaderContentOverlay` for brightness/color-filter overlays.
- `ReaderAppBars` for top chrome, side/bottom navigator, and bottom actions.
- Dialogs such as `ReaderSettingsDialog`, reading-mode selection, orientation selection, page actions, and chapter list.

`ReaderActivity.updateViewer()` creates the active viewer from the reading mode, removes the previous viewer, and adds the new viewer's Android view directly into `viewer_container`. This is the core boundary Navic must mirror: the EPUB/PDF renderer is a viewer implementation, not the owner of reader chrome.

## Gesture Ownership

Komikku maps tap actions inside the viewer layer:

- `PagerViewer` receives tap events from its pager, normalizes the tap position to `0..1`, calls `config.navigator.getAction(pos)`, and dispatches menu/next/previous/left/right.
- `WebtoonViewer` does the same with its recycler view and dispatches menu/scroll-down/scroll-up.
- `ViewerNavigation.getAction(PointF)` maps normalized positions against a selected navigation-region model.

Komikku's navigation-region presets are real source code, not approximate behavior:

- Default/L-shaped: `LNavigation`
- Kindle-ish: `KindlishNavigation`
- Edge: `EdgeNavigation`
- Right and left: `RightAndLeftNavigation`
- Disabled: `DisabledNavigation`

`ReaderNavigationOverlayView` only draws the zones and fades them out on touch. It does not decide the action. That separation is important: visible tap zones are diagnostics/training UI, while the viewer owns input.

For Navic, this means the reader-wide tap model must not live inside Foliate HTML. It also should not be a Compose child that is accidentally below Android WebView drawing. The port must provide a reader-owned input layer with the same normalized navigation model and make the WebView/PDF renderer a content surface behind it.

## Chrome And Progress Behavior

Komikku's `ReaderAppBars` is overlay chrome:

- Top app bar slides/fades from the top.
- Chapter/page navigator is either bottom, vertical right, or vertical left.
- Bottom actions are icon buttons and do not consume content height.
- Chrome visibility is controlled by reader state (`menuVisible`) and viewer actions (`activity.toggleMenu()`).

The page/progress control is `ChapterNavigator`:

- Bottom mode shows previous/next buttons, `currentPageText`, slider, and total pages in a rounded track.
- Vertical mode shows previous/next buttons rotated vertically with a side slider and current/total labels.
- Vertical side rail is selected based on orientation/preferences and viewer type.

Navic's current bottom chrome is not equivalent because it is attached as a `Scaffold(bottomBar = ...)`, which changes the content slot height. The Komikku port must replace that structure, not restyle it.

## Settings Behavior

Komikku opens reader settings through `ReaderSettingsDialog`, a tabbed dialog with:

- Reading mode
- General
- Custom filter

`ReadingModePage` exposes reading mode, rotation, tap zones, inversion, scale/zoom options, page layout, crop borders, transitions, split/dual-page options, and related pager/webtoon controls. This is the settings density and organization Navic should adapt, not an endless docked list inside the reader viewport.

Navic can reuse its existing `ReaderOptionsPanel` internals only if the container behavior matches Komikku: overlay dialog/panel above content, no content resize, no bottom-sheet layout dependency that changes the reader surface.

## Navic Replacement Boundaries

The port should introduce a new reader-shell boundary instead of continuing to expand `ReaderScreen`:

```text
NavicReaderShell
  content layer
    ReaderViewerHost
      FoliateEpubViewerAdapter
      PdfViewerAdapter
      ShellCoverViewerAdapter
  native/input layer
    KomikkuNavigationModel
    ReaderGestureSurface
  visual overlay layer
    ReaderTapZoneDebugOverlay
    ReaderContentOverlay
    ReaderPageIndicator
    ReaderAppBars
    ReaderSettingsDialog
```

The key rule is ownership:

- `ReaderViewerHost` renders content and reports page/location state.
- `ReaderGestureSurface` maps reader-wide taps/drags to actions.
- Content-specific actions such as links, text selection, image tint toggles, and media controls must explicitly claim the touch before reader-wide menu/page actions fire.
- Overlays display and control state but never shrink the viewer.

## Initial Port Sequence

1. Add Navic tests that reject the current architecture:
   - Reader screen must not use `Scaffold(bottomBar = ...)` to host reader chrome.
   - Reader content host must be full-window/full-parent.
   - Settings must be overlay/dialog behavior, not a docked viewport-consuming panel.
   - Tap-zone presets must match Komikku region mappings.

2. Add Komikku navigation model to Navic:
   - Default/L-shaped, Kindle-ish, Edge, Right-and-left, Disabled.
   - Inversion modes.
   - Smaller tap-zone option.
   - Debug overlay uses the same region model but is visual only.

3. Replace the current reader shell:
   - Full-screen root `Box` or Android view stack equivalent.
   - Content layer always fills the whole reader window.
   - Chrome/page indicator/settings are overlay siblings above content.
   - No `innerPadding` from chrome is allowed to reach the content viewer.

4. Adapt Navic content renderers behind the shell:
   - EPUB/Foliate as a content viewer adapter.
   - PDF as a content viewer adapter.
   - Shell cover as a content viewer adapter or first-class shell page.
   - Keep Bindery/cache/progress/resource resolution outside the shell.

5. Validate on phone:
   - Cover taps and drags work across the image, not only margins.
   - Normal EPUB taps and drags work.
   - Links/images claim content actions without opening chrome.
   - Menus/settings overlay content without zooming/resizing it.
   - Progress rail/page indicator follow Komikku behavior.

## Explicit Non-Goals For This Port Slice

- Do not implement page-curl animation during the shell port.
- Do not tune paper textures as the primary task.
- Do not rework EPUB pagination heuristics unless needed to feed the new progress UI.
- Do not add another shell-cover CSS micro-fix as a substitute for replacing the shell ownership model.

## Acceptance Criteria

The port is not considered aligned until these are true:

- Navic has a documented Komikku-equivalent reader stack with content, input, visual overlay, and chrome separated.
- The main reader content surface remains the same size whether chrome/settings are visible or hidden.
- Reader-wide taps are mapped by Komikku navigation regions at the reader layer.
- Tap-zone debug visualization can be enabled without changing input ownership.
- The old `Scaffold(bottomBar = ...)` reader-shell pattern is gone.
- A phone release demonstrates tap/drag/chrome/settings behavior that matches Komikku's interaction model closely enough for further EPUB/PDF polishing to happen on top.

## Eta61 Phone Feedback

Validated release: `v1.0.11-eta61`, `versionCode=394`, installed on device at `2026-06-13 19:15`.

User feedback after installing the release:

- Book load appears faster.
- Shortcut/link interactions and image interactions still work.
- Center tap does not show/hide the reader menu.
- Touching the cover immediately discards it, without finger feedback.
- There is no way to return to the reader-managed cover after it is dismissed.
- Texture transitions are still broken.
- The current layout is only an early adaptation and is still far from a proper Komikku clone.
- The right-side progress rail is wrong for large EPUB page counts: for roughly 400 pages, the handle becomes about 1 cm tall instead of behaving like Komikku's usable chapter/page rail.
- The menu shows duplicate bookmark/star controls: one at the bottom and one at the top right.
- The bottom menu layout still does not match Komikku: Komikku centers the lower action row and distributes the buttons evenly around the center, while Navic still reads like a stretched control strip.

These are not polish issues. They show the port is still failing the Komikku ownership model:

- The reader shell does not yet treat cover as a real navigable state.
- The native gesture layer is not consistently converting center taps into menu toggles.
- The progress rail is still using a generic scrollbar mental model rather than Komikku's reader navigator model.
- The chrome action model has duplicate affordances instead of one coherent overlay.
- The bottom chrome layout is still structurally different from Komikku's centered/distributed action row.

Next debugging priority:

1. Reproduce center tap failure and cover dismissal with adb logs on `eta61`.
2. Trace the native touch path from `ReaderSurfaceHost` to reader chrome state.
3. Add failing tests for menu toggle, cover state, and duplicate bookmark affordance.
4. Fix the ownership model before touching texture animation or visual polish again.

## Eta62 Candidate Scope

`v1.0.11-eta62` is the next phone candidate after `eta61`. The phone is not expected to be connected while this candidate is prepared, so the release must be judged first by host-side gates and then by a later device test.

Changes to validate on device:

- Center taps on image-heavy EPUB pages should no longer be suppressed by raw Android WebView `IMAGE_TYPE` hits. Only explicit content-side interactive handling should block the chrome toggle.
- The reader-managed cover should become a returnable shell state: tapping/turning next from the shell cover arms a previous action back to the shell cover before Foliate/location state catches up.
- The right progress rail should stop behaving like a tiny rotated Material scrollbar. It should use a fixed-size vertical handle and tap/drag mapping closer to Komikku's reader rail.
- The bottom menu action row should be centered/distributed and should no longer duplicate the bookmark/star action already shown in top chrome.
- Paper texture movement should not invert at EPUB area/frontmatter boundaries when the renderer coordinate system wraps. Known `next`/`previous` direction now dominates raw renderer coordinate sign.

Host-side release gates for this candidate:

- `node tools/reader-harness/src/run-reader-harness.mjs --mode texture-offset-logic`
- `:composeApp:testAndroidHost` focused on reader shell/chrome/image-link/paper-surface tests
- JS syntax checks for changed reader harness/runtime files
- `git diff --check`
- Android version metadata check for `v1.0.11-eta62`
- `:androidApp:assembleDebug` as the local compile gate; local `assembleRelease` requires signing secrets and is expected to be built by the GitHub release workflow.

## Eta63 Candidate Scope

`v1.0.11-eta63` is the next candidate prepared while the phone is unavailable. It keeps the Komikku-port direction and only includes host-testable reader-shell fixes:

- Native/JS shell-cover ownership now gates EPUB-cover resume locators explicitly. If a saved locator points at the EPUB cover and a shell cover exists, Foliate is sent to the first readable content instead of rendering the EPUB cover behind the reader-managed cover surface.
- Non-cover saved locators still load normally behind the shell cover.
- Initial resume snapshots are not posted for EPUB-cover locators converted into the shell-cover virtual page.
- Paper texture movement now distinguishes normal small inverted renderer coordinates from full-page Android area-boundary wraps. Normal movement still trusts the known next/previous direction, but full-page sign conflicts follow bounded renderer delta so the texture counter-moves the rendered page at frontmatter/chapter boundaries.
- The texture trace assertion layer now allows those full-page renderer-wrap samples while still failing ordinary forward/previous inversions.

Fresh local evidence before release:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderShowsShellCoverBeforeSavedResumeLocationWithoutLoadingEpubCoverBehindIt"
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSyncsSurfaceTextureWithPaginatorScrollDrags" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessTextureFrontmatterTransitionValidatesTracePayloadDirection"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Results so far:

- Shell-cover resume locator red check failed before implementation and passed after the runtime started using `shouldStartAtShellCover`.
- Texture offset logic red check failed on the exact Android boundary shape and passed after boundary-wrap handling.
- Focused paper-surface host tests passed.
- Real Hobbit frontmatter texture transition harness passed.

Phone validation target after eta63:

- Reopen the Hobbit EPUB from a saved cover/frontmatter locator and confirm the WebView does not show the EPUB cover behind the native shell cover.
- Move from maps/frontmatter into Author's Note and back; ADB `surface-texture-scroll` logs should no longer show same-sign renderer delta and texture x/y offset for known-direction full-page boundary wraps.
- Re-check center tap/menu and cover drag separately; eta63 does not claim phone validation for native touch ownership because no device was attached during local work.

## Controller/Engine Boundary Checkpoint

The current reset no longer treats the temporary fake reader page counter as the active architecture. The active path now has an explicit common-code boundary:

- `ReaderController` owns publication identity, chrome/menu state, settings, shell-cover visibility, and one-shot content-action claims.
- `ReaderCoordinator` applies controller steps to the active engine adapter and keeps the current `ReaderEngineViewState`.
- `FoliateEpubEngineAdapter` translates controller commands into WebView publication state and maps bridge events back into engine events.
- 2026-06-14 neutral engine contract slice: generic renderer contracts now live in `ReaderEngine.kt`: `ReaderEngine`, `ReaderEngineCommand`, `ReaderEngineEvent`, `ReaderEngineViewState`, and `ReaderEngineStep`.
- `ReaderController.kt` no longer owns the renderer command/event model, and `FoliateEpubEngineAdapter.kt` no longer defines the shared view-state or engine interface. Foliate implements `ReaderEngine`; future PDF/readaloud-capable engines should implement the same contract.
- `ReaderScreen` now routes publication resolution into `ReaderCoordinator.open(...)` and mounts `ReaderViewerHost` inside the Komikku viewer slot.
- 2026-06-14 viewer-host boundary slice: `ReaderViewerHost.kt` is the common UI boundary that translates `ReaderEngineViewState` into concrete renderer mounts. `ReaderScreen.kt` must not select `ReaderEngineWebViewHost`, inspect `ReaderEngineViewState.WebViewPublication`, or reintroduce renderer-specific host wiring inline.
- 2026-06-14 viewer lifecycle slice: `ReaderViewer.kt` now defines the Komikku-equivalent viewer boundary for the active common UI path: `ReaderViewer`, `ReaderViewerKind`, `ReaderViewerKey`, and `readerViewerFor(...)`.
- `ReaderViewerHost.kt` now derives a viewer key from the engine view state, remembers the active viewer by that key, and disposes it through `destroy()` when Compose swaps or removes it. This is the first Navic equivalent of Komikku's `updateViewer()`/`Viewer.destroy()` lifecycle, even though the concrete Android native view mounting still needs to keep moving closer to Komikku.
- 2026-06-14 native viewer-slot swap slice: `KomikkuReaderNativeFrameHost(...)` now receives the active `ReaderViewerKey`.
- Android `KomikkuReaderNativeFrameHost.android.kt` no longer keeps one permanent viewer `ComposeView` in `viewer_container`. It remembers `currentViewerKey`, disposes the old renderer composition, and replaces the renderer child inside a stable native viewer container when the key changes. The stable viewer container remains the gesture owner while renderer children are swapped under it.
- The current mounted child is still a Compose-hosted renderer bridge, not a direct Android `Viewer.getView()` implementation yet. That remaining gap is intentional and should be closed by future concrete EPUB/PDF viewer adapters rather than by putting UI ownership back into Foliate.
- 2026-06-14 mode-aware viewer identity slice: `ReaderViewerKey` now includes `ReaderViewerMode`, derived from normalized reader flow settings.
- Paged, vertical paged, and scrolled EPUB viewer modes now produce distinct viewer keys, so changing reading mode can trigger the same native viewer-slot replacement path as changing the publication. This mirrors Komikku's `ReadingMode.toViewer(...)` behavior at the identity/lifecycle boundary before concrete pager/webtoon movement is fully ported.
- 2026-06-14 viewer-owned movement-action slice: `ReaderViewer` now exposes `viewerActionFor(KomikkuNavigationRegion)`, and `ReaderScreen.kt` no longer owns any `KomikkuNavigationRegion` movement mapper.
- This aligns with Komikku's `PagerViewer`/`WebtoonViewer` model where the active viewer interprets navigation-region results and dispatches menu/next/previous/left/right movement. Navic's first pass remains behavior-equivalent for all viewer modes, but future pager/webtoon/PDF viewers can diverge behind `ReaderViewer` without changing `ReaderScreen`.
- 2026-06-14 legacy controller-navigation cleanup: `ReaderControllerNavigationAction`, `ReaderController.onReaderNavigationAction(...)`, and `ReaderCoordinator.onNavigationAction(...)` were removed. Reader movement now enters the controller only as `ReaderViewerAction`, so the controller cannot reclaim LEFT/RIGHT/scroll semantics that should belong to the active viewer.
- `ReaderEngineWebViewHost` is the Komikku EPUB content mount. It configures the same Foliate WebView entrypoint and command bridge, but does not construct the legacy `ReaderSurfaceHost`, `ReaderShellCoverView`, or reader-wide tap fallback layer.
- 2026-06-14 active host protocol slice: the active `ReaderEngineWebViewHost` common/Android/iOS signatures now use `ReaderEngineHostCommand` and `ReaderEngineHostEvent` wrappers instead of raw `ReaderBridgeCommand` and `ReaderBridgeEvent`.
- `ReaderEngineViewState.WebViewPublication.command` now carries `ReaderEngineHostCommand`. `FoliateEpubEngineAdapter` wraps raw Foliate commands as `ReaderEngineHostCommand.FoliateBridge(...)`; the Android WebView engine host unwraps them locally at the renderer boundary.
- `ReaderScreen` consumes `ReaderEngineHostEvent` from the active engine host and forwards it through `ReaderCoordinator.onEngineHostEvent(...)`. The shell no longer sees Foliate bridge events directly.
- This keeps the active Komikku shell/controller boundary aligned with Komikku's viewer ownership model: chrome and navigation live above the viewer, while renderer-specific bridge details stay inside the engine host.
- 2026-06-14 public bridge-event closure: `ReaderCoordinator` no longer exposes `onBridgeEvent(...)`, and the `ReaderEngine` contract does not expose `onBridgeEvent(ReaderBridgeEvent)`. All renderer callbacks enter the controller through `ReaderEngineHostEvent`.
- `FoliateEpubEngineAdapter` still decodes `ReaderBridgeEvent` internally because Foliate is the EPUB renderer, but that helper is private adapter logic. The coordinator and shell only see the resulting `ReaderEngineEvent`.
- `ReaderWebViewHost` has been removed from the active source tree and remains only in the pre-Komikku vault. Active EPUB/PDF rendering enters through `ReaderEngineWebViewHost`, while shell, cover, navigation, and gesture ownership stay above it in the Komikku native frame/controller path.
- `ReaderBridgeEvent.CenterTap` is ignored by the EPUB adapter. Komikku-native navigation owns menu taps.
- `ReaderBridgeEvent.ContentTapHandled(...)` is translated inside the Foliate engine into a typed content-action claim, suppressing only the next shell navigation action once it reaches the controller as `ReaderEngineEvent.ContentActionClaimed(action)`.
- 2026-06-14 typed content-action slice: `readerContentTapHandled` bridge events now decode `source`/`action` into `ReaderContentAction` values instead of collapsing every event into `Generic`. Sources already emitted by `navic-reader.js` now map as follows: `link` and `link-touch` -> `Link`, `image` -> `Image`, and `media-touch`/`media-anchor` -> `MediaControl`.
- This keeps Anx/Foliate-style link/image/media interaction as an engine capability while preserving the Komikku rule that only explicit content claims can suppress reader-wide menu/page actions.
- The controller-owned shell cover is a virtual reader state. Advancing from it hides the shell cover locally without sending `NextPage` to Foliate.
- 2026-06-14 capability-boundary slice: the controller now also owns search state, TOC state, selection state, active media-overlay fragment state, and the current audio metadata label.
- `ReaderEngineCommand` now has typed capability commands for `Search(...)` and `NavigateTo(...)`. These sit above raw Foliate/WebView commands so shell code can ask for ebook behavior without becoming a bridge owner.
- `FoliateEpubEngineAdapter` translates typed capability commands into Foliate bridge commands: search to `ReaderBridgeCommand.Search`, CFI navigation to `GoToCfi`, href navigation to `GoToHref`, and percent navigation to `GoToProgress`.
- `FoliateEpubEngineAdapter` now maps bridge capability events back into engine events for search results, TOC, selection, active media overlay, and inactive media overlay. The controller consumes those events into state instead of letting the WebView own UI decisions.
- 2026-06-14 engine-slot slice: `ReaderEngine` owns command handling through `onCommand(...)` and typed renderer callback intake through `onHostEvent(...)`.
- `ReaderCoordinator` now keeps engines keyed by `ReaderPublicationFormat` and routes the active publication format through that engine contract. Foliate remains the default EPUB engine, but the coordinator no longer has an EPUB-only `epubAdapter` field.
- `ReaderCoordinatorTest.activeEngineIsSelectedThroughAdapterContractInsteadOfFoliateSpecialCase` uses an injected fake PDF engine to prove the controller/coordinator path can dispatch open commands and consume host events through a non-Foliate engine slot.
- 2026-06-14 progress-navigation slice: the Komikku shell progress rail now calls `ReaderCoordinator.navigateTo(ReaderLocator(progress = ...))` instead of dispatching `ReaderBridgeCommand.GoToProgress` directly.
- `ReaderController.navigateTo(...)` emits `ReaderEngineCommand.NavigateTo(...)`; the active engine adapter translates that typed navigation request into Foliate-specific CFI, href, or percent bridge commands.
- `ReaderRuntimeShellProgressTest.androidReaderBridgeExposesProgressSeekCommand` now rejects raw `ReaderBridgeCommand.GoToProgress` and `coordinator.dispatchBridgeCommand(...)` usage in `ReaderScreen`, so progress navigation stays above EPUB/PDF bridge details.
- 2026-06-14 page-turn/settings slice: page turns and settings updates are now typed engine commands instead of controller-emitted bridge commands.
- `ReaderController` emits `ReaderEngineCommand.TurnPage(ReaderPageTurnDirection...)` for previous/next and tap-zone movement, and `ReaderEngineCommand.ApplySettings(...)` for normalized settings changes.
- `FoliateEpubEngineAdapter` translates typed page-turn and settings commands into `ReaderBridgeCommand.PreviousPage`, `NextPage`, and `ApplySettings`. This keeps Foliate/WebView command details inside the EPUB adapter rather than the Komikku shell/controller path.
- `ReaderRuntimeShellProgressTest.commonReaderChromeExposesPageTurnControls` now rejects raw `ReaderBridgeCommand.PreviousPage` and `ReaderBridgeCommand.NextPage` usage in `ReaderScreen`.
- 2026-06-14 media-overlay/readaloud sync slice: Storyteller/Readaloud sync state now emits typed `ReaderEngineCommand.ApplyMediaOverlay(...)` and `ReaderEngineCommand.ClearMediaOverlay` instead of raw `ReaderBridgeCommand.ApplyOverlayFragment(...)` and `ReaderBridgeCommand.ClearOverlay`.
- `ReaderMediaOverlaySyncState` returns `ReaderMediaOverlaySyncStep.engineCommand`, and `ReaderReadaloudSyncState` tracks `engineCommand`/`engineCommandKey`. This keeps synced audiobook labels and highlight commands in the controller/engine capability layer.
- `MediaOverlayTimeline.engineCommandForAudioPosition(...)` replaces the old bridge-level helper for direct audio-position-to-overlay mapping.
- The legacy Android `ReaderReadaloudRuntimeHost` still converts those typed overlay commands back to bridge commands locally because that old host has not been removed yet. The active Komikku path must continue moving readaloud through controller/coordinator rather than resurrecting that host as a shell owner.
- 2026-06-14 raw bridge escape-hatch closure: `ReaderEngineCommand.DispatchBridgeCommand` and `ReaderCoordinator.dispatchBridgeCommand(...)` have been removed.
- `ReaderController` and `ReaderChromeState` no longer contain `ReaderBridgeCommand` or `ReaderBridgeEvent` references. `ReaderCoordinator` remains the typed engine-host event ingress into reader-domain events, but controller/chrome state no longer rebuilds bridge protocol events internally.
- `ReaderKomikkuBackboneResetTest.commonControllerCoordinatorAndChromeDoNotExposeRawFoliateBridgeCommands` locks the current boundary: controller/chrome cannot expose raw bridge commands or bridge events, and coordinator cannot expose raw bridge command dispatch.
- `ReaderChromeState` maps tap-zone actions to typed `ReaderPageTurnDirection` through `readerTapZonePageTurnDirectionFor(...)`. Foliate/WebView-specific page-turn commands are translated only inside adapter/host code.
- The legacy Android WebView host still translates typed page-turn directions back to `ReaderBridgeCommand.PreviousPage`/`NextPage` locally because it is a WebView host. This is allowed as a temporary legacy adapter concern, not as shell/controller ownership.
- `ReaderChromeState` now updates from reader-domain methods (`onLocationChanged(...)`, `onTocItemChanged(...)`) instead of `onReaderEvent(ReaderBridgeEvent...)`.

Fresh host-side evidence for this checkpoint:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
git diff --check
```

Result: passed on 2026-06-14 for the focused reader boundary suite and diff whitespace check.

Fresh host-side evidence for the 2026-06-14 capability-boundary slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest"
```

Fresh host-side evidence for the 2026-06-14 engine-slot slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Result: passed.

Fresh host-side evidence for the 2026-06-14 progress-navigation slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgeExposesProgressSeekCommand"
```

Result: passed.

Fresh host-side evidence for the 2026-06-14 page-turn/settings slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgeExposesProgressSeekCommand" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.commonReaderChromeExposesPageTurnControls"
```

Result: passed.

Fresh host-side evidence for the 2026-06-14 media-overlay/readaloud sync slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.StorytellerMediaOverlayParserTest.mapsAudioPositionsToReaderOverlayAndTextFragmentsBackToAudioSeek" --tests "paige.navic.reader.ReaderMediaOverlaySyncTest" --tests "paige.navic.reader.ReaderReadaloudSyncCoordinatorTest"
```

Result: passed.

Fresh host-side evidence for the 2026-06-14 raw bridge escape-hatch closure:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.commonControllerCoordinatorAndChromeDoNotExposeRawFoliateBridgeCommands"
```

Result: failed before the production change while `ReaderController` still contained `ReaderBridgeCommand`.

After extending the same test to reject `ReaderBridgeEvent` inside controller/chrome, the red check failed again while `ReaderController` still rebuilt `ReaderBridgeEvent.LocationChanged`.

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.commonControllerCoordinatorAndChromeDoNotExposeRawFoliateBridgeCommands" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderChromeStateTest"
```

Result: passed after removing raw bridge-command dispatch and replacing controller/chrome bridge-event updates with reader-domain chrome update methods.

Fresh host-side evidence for the 2026-06-14 active host protocol slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellAndViewStateDoNotExposeRawFoliateBridgeProtocol" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderCoordinatorTest"
```

Result: failed before the production change while `ReaderScreen`, `ReaderEngineWebViewHost`, and `ReaderEngineViewState.WebViewPublication` still exposed raw Foliate bridge protocol. Passed after introducing `ReaderEngineHostCommand`/`ReaderEngineHostEvent` wrappers and moving bridge unwrap logic into the active WebView engine host.

Fresh host-side evidence for the 2026-06-14 public bridge-event closure:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.commonControllerCoordinatorAndChromeDoNotExposeRawFoliateBridgeCommands" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellAndViewStateDoNotExposeRawFoliateBridgeProtocol" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderCoordinatorTest"
```

Result: failed before the production change while `ReaderCoordinator` still contained `ReaderBridgeEvent` and `ReaderEngineAdapter` still exposed `onBridgeEvent(ReaderBridgeEvent)`. Passed after removing the public raw bridge-event API and routing coordinator/adapter tests through `ReaderEngineHostEvent.FoliateBridge(...)`.

Fresh host-side evidence for the 2026-06-14 neutral engine contract slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.genericReaderEngineContractIsNotOwnedByFoliateAdapter" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellAndViewStateDoNotExposeRawFoliateBridgeProtocol" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest"
```

Result: failed before the production change because `ReaderEngine.kt` did not exist and the generic engine abstractions were split between `ReaderController.kt` and `FoliateEpubEngineAdapter.kt`. Passed after moving the shared contracts into `ReaderEngine.kt` and making Foliate implement `ReaderEngine`.

Fresh host-side evidence for the 2026-06-14 viewer-host boundary slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenMountsViewerHostInsteadOfSelectingRendererViewsInline"
```

Result: failed before the production change because `ReaderViewerHost.kt` did not exist and `ReaderScreen.kt` still selected renderer view state inline. Passed after moving renderer selection, shell-cover rendering, and viewer status rendering into `ReaderViewerHost.kt`.

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderChromeStateTest"
```

Result: passed after updating the reset guard to require the new boundary instead of the temporary inline viewer host name.

Fresh host-side evidence for the 2026-06-14 viewer lifecycle slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerViewerHostUsesKomikkuViewerLifecycleBoundary"
```

Result: failed before the production change because `ReaderViewer.kt` did not exist. Passed after adding the viewer lifecycle boundary and routing `ReaderViewerHost.kt` through `readerViewerFor(...)` plus `DisposableEffect { onDispose { destroy() } }`.

Fresh host-side evidence for the 2026-06-14 native viewer-slot swap slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostSwapsViewerContentByReaderViewerKeyLikeKomikkuUpdateViewer"
```

Result: failed before the production change because the platform host API did not receive a viewer key and Android kept a permanent `viewerComposeView` child in `viewer_container`. Passed after threading `ReaderViewerKey` through common/Android/iOS host signatures and making Android remove/add the viewer child when that key changes.

Fresh host-side evidence for the 2026-06-14 mode-aware viewer identity slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.readerViewerKeyChangesWhenReadingModeRequiresDifferentViewerImplementation"
```

Result: failed before the production change because `ReaderViewerMode` and `ReaderViewerKey.mode` did not exist. Passed after adding mode derivation from reader flow settings and making paged/scrolled states produce different viewer keys.

Fresh host-side evidence for the 2026-06-14 viewer-owned navigation action slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.readerViewerOwnsKomikkuNavigationRegionMapping"
```

Result: failed before the production change because `ReaderViewer.navigationActionFor(...)` did not exist. Passed after adding the viewer-owned region mapper.

Fresh host-side evidence for the 2026-06-14 single active viewer ownership slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerRootKeepsSingleActiveViewerForHostAndNavigationActions"
```

Result: failed before the production change while `ReaderScreen.kt` still created a throwaway viewer for native tap actions and `ReaderViewerHost.kt` still created/disposed a second viewer lifecycle. Passed after moving viewer creation, `remember(viewerKey)`, `withViewState(...)`, and `DisposableEffect { destroy() }` into `KomikkuReaderRoot`, passing `viewer.key` to `KomikkuReaderNativeFrameHost`, routing native frame actions through the retained viewer boundary, and passing the same retained `viewer` into `ReaderViewerHost`.

This aligns the active path with Komikku's `updateViewer(...)` ownership rule: the reader root owns the active viewer instance, the native viewer slot is keyed by that instance, and the mounted viewer host renders only the supplied viewer. `ReaderViewerHost` must not call `readerViewerFor(...)` or own a second `DisposableEffect` lifecycle.

Fresh regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderViewerTest"
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; `git diff --check` returned clean.

Fresh host-side evidence for the 2026-06-14 concrete viewer variant slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.readerViewerFactoryCreatesConcreteKomikkuViewerVariantsFromReadingMode"
```

Result: failed before the production change because `PagedPublicationReaderViewer`, `VerticalPagedPublicationReaderViewer`, and `WebtoonPublicationReaderViewer` did not exist. Passed after replacing the single concrete `WebViewPublicationReaderViewer` class with a sealed publication-viewer base and concrete paged, vertical paged, and webtoon publication viewer implementations selected by `readerViewerFor(...)`.

Source reference for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt:70-104`: Komikku's `ReadingMode.toViewer(...)` maps reading-mode preference to concrete `L2RPagerViewer`, `R2LPagerViewer`, `VerticalPagerViewer`, and `WebtoonViewer` instances.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewers.kt:9-65`: pager modes are separate viewer classes, with vertical paging creating a vertical pager.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:35-75`: webtoon/continuous modes are a separate `Viewer` implementation backed by recycler/webtoon behavior.

Navic implication: reading mode is no longer only metadata on one generic WebView viewer. `ReaderViewer` now has concrete classes that give future pager, vertical-pager, webtoon, and PDF movement behavior a real owner without changing `ReaderScreen` or letting Foliate own UI/controller state.

Regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderViewerTest"
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; `git diff --check` returned clean.

Fresh host-side evidence for the 2026-06-14 webtoon scroll-action boundary slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.webtoonViewerMapsNavigationRegionsToScrollActionsLikeKomikku" --tests "paige.navic.reader.ReaderControllerTest.scrollNavigationIsControllerOwnedAndForwardedAsEngineCapability" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedViewportScrollAsRendererLocalNavigationCommand"
```

Result: failed before the production change because the controller only had page-turn/navigation actions and no typed viewport-scroll capability. Passed after adding typed viewport-scroll movement, `ReaderEngineCommand.ScrollViewport(...)`, `ReaderViewportScrollDirection`, and a `WebtoonPublicationReaderViewer` override that maps `NEXT`/`RIGHT` to scroll down and `PREV`/`LEFT` to scroll up. The initial implementation used a temporary controller-navigation action; that compatibility layer was removed by the later 2026-06-14 legacy controller-navigation cleanup so viewer-owned movement remains the only live path. The Foliate EPUB adapter currently translates those scroll requests into renderer-local previous/next navigation commands because the remaining active EPUB renderer is still Foliate-backed.

Source reference for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:130-134`: Komikku pager viewers map `MENU`, `NEXT`, `PREV`, `RIGHT`, and `LEFT` to menu toggling and page/pager movement.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:144-146`: Komikku webtoon viewers keep `MENU` as menu toggling but map `NEXT`/`RIGHT` to `scrollDown()` and `PREV`/`LEFT` to `scrollUp()`.

Navic implication: pager and webtoon movement are now viewer-owned differences instead of one generic WebView page-turn rule. The shell still sends neutral navigation regions, the active `ReaderViewer` interprets those regions, the controller emits typed engine capabilities, and only the Foliate adapter translates that capability into current renderer commands.

Regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderViewerTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code; no reader-viewer warning remained after moving the `open` override point to the sealed publication-viewer base.

Fresh host-side evidence for the 2026-06-14 viewport-scroll bridge slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.viewportScrollCommandDispatchesReaderScrollIntent" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedViewportScrollAsRendererScrollCommand" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgeExposesViewportScrollCommandSeparateFromPageTurns"
```

Result: failed before the production change because `ReaderBridgeCommand.ScrollViewport(...)` did not exist. Passed after adding the typed bridge command, serializing it as `{"type":"scrollViewport","direction":"up|down"}`, and making `FoliateEpubEngineAdapter` emit that bridge command instead of collapsing `ReaderEngineCommand.ScrollViewport(...)` into `NextPage`/`PreviousPage`.

Runtime behavior added in `navic-reader.js`:

- `case 'scrollViewport'` dispatches to `scrollViewport(command.direction)`.
- `scrollViewport(...)` uses renderer-local scrolling when Foliate is in `renderer.scrolled` mode.
- The scroll distance is `0.75` of the current renderer viewport, matching Komikku's webtoon tap-scroll distance.
- Only non-scrolled renderers fall back to `nextPage()`/`previousPage()`.

Source reference for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:65`: Komikku webtoon mode uses `displayMetrics.heightPixels * 3 / 4` as `scrollDistance`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:337-388`: `scrollUp()` and `scrollDown()` call recycler scroll methods with that distance instead of page-turn commands.
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js:893-940`: Foliate's renderer exposes `scrolled`, `size`, `sideProp`, and `scrollBy(dx, dy)`, which is the correct adapter target for continuous EPUB scrolling.

Navic implication: the previous webtoon viewer slice is no longer cosmetic at the engine boundary. Webtoon/continuous tap zones now produce `ScrollViewport`, the controller forwards a typed scroll capability, the Foliate adapter emits a typed bridge command, and the runtime scrolls the renderer viewport in scrolled mode. Pager navigation and webtoon scrolling are now separate all the way down to the WebView bridge.

Regression evidence for the same slice:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.viewportScrollCommandDispatchesReaderScrollIntent" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedViewportScrollAsRendererScrollCommand" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgeExposesViewportScrollCommandSeparateFromPageTurns" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderViewerTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Fresh host-side evidence for the 2026-06-14 default PDF engine-slot slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorTest.pdfPublicationRoutesThroughDefaultPdfEngineAdapter"
```

Result: failed before the production change because `ReaderCoordinator()` only registered the EPUB adapter by default, so opening a `ReaderPublicationFormat.Pdf` request produced no active WebView publication state. Passed after registering a default PDF slot with the same Foliate-backed engine boundary and making `FoliateEpubEngineAdapter` carry its `ReaderPublicationFormat` as constructor state.

2026-06-14 follow-up: the first PDF slot fix still used `FoliateEpubEngineAdapter(format = ReaderPublicationFormat.Pdf)`, which was a boundary shortcut. A later red check tightened the requirement so `ReaderCoordinatorTest.pdfPublicationRoutesThroughDefaultPdfEngineAdapter` asserts the default PDF slot is `FoliatePdfEngineAdapter`. The production code now has:

- `FoliateEpubEngineAdapter`: EPUB-specific concrete adapter.
- `FoliatePdfEngineAdapter`: PDF-specific concrete adapter.
- `FoliateWebViewEngineAdapter`: shared Foliate/WebView command/event implementation used by both.
- `ReaderCoordinator`: default registry maps `ReaderPublicationFormat.Pdf` to `FoliatePdfEngineAdapter()`.

This makes implementation sequence item 7 true at the adapter boundary: PDF is a separate adapter while still sharing the same Komikku input/chrome contract and Foliate/PDF.js rendering path.

Focused and boundary verification after the separate PDF adapter extraction:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorTest.pdfPublicationRoutesThroughDefaultPdfEngineAdapter"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest"
```

Result: passed on 2026-06-14. The boundary Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Fresh non-cached verification for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorTest.pdfPublicationRoutesThroughDefaultPdfEngineAdapter"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Coordinator/viewer boundary regression evidence after the PDF slot change:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; the JS syntax and whitespace checks returned clean.

Source reference for this slice:

- `tmp/references/anx-reader/assets/foliate-js/src/book.js:625-628`: Anx opens PDF files by importing `./pdf.js` and creating the book through `makePDF(file)`.
- `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614`: Anx's Foliate PDF integration builds a book object from PDF.js pages and exposes sections, TOC resolution, page lookup, and cover rendering.
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/view.js:108-110`: Navic's bundled Foliate runtime already follows the same PDF detection/import boundary.
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/pdf.js:617-683`: Navic's bundled Foliate runtime already has a `makePDF(file)` path that creates sections, resolves outline links, and exposes a first-page cover.

Navic implication: PDF is now restored as a default engine capability behind the controller instead of being a missing format in the coordinator. This is not a claim that PDF interaction, centering, tap responsiveness, or page rendering polish is complete. Those remain viewer/runtime behavior work under the Komikku shell contract. The important architecture point is that EPUB and PDF are both selected through `ReaderPublicationFormat` and emit the same controller-owned navigation capabilities, while Foliate/PDF.js remains an adapter detail.

Fresh host-side evidence for the 2026-06-14 native-frame shell-cover ownership slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverIsOwnedByNativeFrameHostNotCommonViewerCompose"
```

Result: failed before the production change because `ReaderViewerHost.kt` still imported Coil `AsyncImage`, rendered `ReaderShellCoverSurface`, and hid the controller shell-cover state inside common Compose viewer content. Passed after moving shell-cover inputs into the `KomikkuReaderNativeFrameHost(...)` contract, routing `controllerState.shellCoverVisible` and the engine cover URL from `ReaderScreen.kt`, removing the common Compose cover renderer, and adding Android `KomikkuReaderNativeShellCoverView`.

The same slice also added a viewer-boundary guard:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.publicationViewerExposesShellCoverMetadataWithoutScreenInspectingEngineViewState"
```

Result: failed before the production change because `ReaderViewer` did not expose shell-cover metadata. Passed after adding `shellCoverUrl` and `shellCoverTitle` to the viewer contract and changing `ReaderScreen.kt` to consume those values from the retained active viewer instead of casting `ReaderEngineViewState.WebViewPublication`.

Regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Source reference for this slice:

- `tmp/references/komikku/app/src/main/res/layout/reader_activity.xml:1-29`: Komikku's root is a full-window `FrameLayout` with `reader_container`, nested `viewer_container`, passive `ReaderNavigationOverlayView`, and full-window `ComposeView` overlay.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:941-967`: `updateViewer()` destroys the old viewer, clears `viewerContainer`, and adds the new viewer view into that native slot.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt:19`: the navigation overlay is a native `View`, not the input owner or content renderer.

Navic implication: the synthetic ebook shell cover is no longer a common Compose image layered inside `ReaderViewerHost`. The controller still owns whether the shell cover is visible, but Android now renders it as a native frame-host layer between the viewer and the passive navigation/Compose overlays. This removes another renderer island from the active Komikku path. It does not claim phone validation of cover drag/tap behavior yet; that still requires an installed build and adb validation.

## 2026-06-14 Shell Cover Inside Viewer Gesture Owner

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/res/layout/reader_activity.xml:1-31`: the input-capable content view is mounted inside `viewer_container`; only `ReaderNavigationOverlayView` and `compose_overlay` sit above it as root siblings.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt:73-78`: the viewer-owned native view receives the touch stream, dispatches to its children first, then sends the same stream through the gesture detector.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:129-135`: confirmed tap regions are interpreted by the active viewer, not by a renderer HTML layer or a root sibling overlay.

Navic implication:

- The previous Navic native shell-cover pass still placed `KomikkuReaderNativeShellCoverView` as a root sibling above `viewerContainer`. That made the cover visually native, but it did not make it part of the same native viewer input owner.
- Android now mounts the synthetic shell cover inside `KomikkuReaderNativeViewerContainer`, above a dedicated `viewerContentContainer`.
- Viewer swaps now replace only `viewerContentContainer` children through `replaceViewerContent(viewerView)`. They no longer call `viewerContainer.removeAllViews()`, because that would detach the cover from the gesture-owning viewer container.
- The shell cover is clickable while visible so the renderer behind it does not receive cover touches, but the parent viewer container still observes the same stream with its `GestureDetector`, matching Komikku's child-first/native-viewer observation model.
- Root-level overlays remain limited to the passive tap-zone visualization and Compose chrome. The cover is treated as viewer-owned content under controller state, not as a separate root overlay.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeShellCoverIsMountedInsideViewerContainerSoKomikkuGestureOwnerSeesCoverTouches"
```

Result: failed before the production change because `shellCoverView` was still added as a root sibling, and viewer swaps still removed/replaced the whole `viewerContainer`.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeShellCoverIsMountedInsideViewerContainerSoKomikkuGestureOwnerSeesCoverTouches"
```

Result: passed on 2026-06-14 after adding `viewerContentContainer`, moving shell-cover mounting into `KomikkuReaderNativeViewerContainer`, and replacing renderer content without removing the full viewer container.

Adjacent native-frame/viewer lifecycle evidence:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.newBackboneUsesPortedKomikkuViewerNavigationInsteadOfOnlyNavicTapHelpers" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverIsOwnedByNativeFrameHostNotCommonViewerCompose" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeShellCoverIsMountedInsideViewerContainerSoKomikkuGestureOwnerSeesCoverTouches" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostSwapsViewerContentByReaderViewerKeyLikeKomikkuUpdateViewer" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostDisposesOldViewerCompositionWhenSwappingLikeKomikkuUpdateViewer" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerRootKeepsSingleActiveViewerForHostAndNavigationActions" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

This checkpoint does not claim feature parity yet. The remaining work is still to replace the residual old WebView content host internals with a clean engine host, restore real EPUB/PDF/search/annotations/readaloud capabilities through adapters, and continue porting Komikku's viewer/chrome/settings behavior rather than patching around Foliate/WebView side effects.

## 2026-06-14 Controller-Owned Settings Dialog Entry

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:372-390`: `ReaderViewModel.Dialog.Settings` renders `ReaderSettingsDialog` from the Compose overlay, not inside the content viewer.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:714-725`: `ReaderAppBars` receives `onClickSettings = viewModel::openSettingsDialog`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt:23-68`: the settings surface is a tabbed dialog with Reading mode, General, and Custom filter pages.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt:182-189`: the bottom settings icon delegates to the supplied settings callback.

Navic implication:

- `ReaderControllerState` now owns `dialog: ReaderControllerDialog?`, with `ReaderControllerDialog.Settings` as the first controller-owned overlay dialog.
- `ReaderController.openSettingsDialog()` sets `dialog = Settings` and keeps menus visible; `closeDialog()` clears the dialog and keeps menus visible, matching Komikku's dismiss/show-menu behavior for settings.
- `ReaderCoordinator` exposes `openSettingsDialog()` and `closeDialog()` so active UI code stays on the controller boundary.
- `KomikkuReaderBottomBar` now receives `onSettings` and the settings icon opens `coordinator.openSettingsDialog()` instead of keeping an empty callback.
- `KomikkuComposeOverlay` renders `KomikkuReaderSettingsDialog` only when `controllerState.dialog == ReaderControllerDialog.Settings`.
- The dialog is a Komikku-shaped tabbed overlay container with Reading mode, General, and Custom filter tabs. It is intentionally not the old `ReaderOptionsPanel` and it does not resize the viewer. Detailed settings controls still need to be moved into this container in future slices.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton"
```

Result: failed before the production change because `ReaderController.openSettingsDialog()`, `ReaderControllerDialog`, and the active shell settings callback did not exist.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton"
```

Result: passed on 2026-06-14 after adding controller dialog state, coordinator wrappers, settings-button wiring, and the initial `KomikkuReaderSettingsDialog` overlay container.

Adjacent controller/shell evidence:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

This checkpoint does not claim settings parity. It only makes the settings surface controller-owned and Komikku-shaped so later Anx/Navic settings capabilities can move into the correct overlay container instead of reattaching the old docked reader options panel.

## 2026-06-14 Settings Dialog Applies Tap-Zone Overlay Setting

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:76-82`: pager reader settings collect the active tap-zone preference and pass preference setters into `TapZonesItems(...)`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:337-361`: `TapZonesItems(...)` renders selectable tap-zone controls and tapping-inversion controls as settings-page controls, not as local chrome state.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:122` and `:261`: Komikku keeps smaller tap-zone behavior as a settings-page checkbox in both pager and webtoon settings.

Navic implication:

- The active `KomikkuReaderSettingsDialog` is no longer only a static display shell. It accepts `onSettingsChange: (ReaderSettings) -> Unit`.
- `ReaderScreen` wires that callback to `coordinator.applySettings(settings)`, so the setting flows through `ReaderController.applySettings(...)` and then into `ReaderEngineCommand.ApplySettings(...)`.
- The first migrated control is `Show tap zones`, because it directly supports the Komikku visual tap-zone overlay model and is low risk: the setting already exists in `ReaderSettings` and the native frame host already consumes it for `navigationOverlayVisible`.
- This does not claim settings parity. Reading mode selection, rotation/orientation, tap-zone preset chips, inversion, scale/zoom, page layout, crop borders, transitions, and split/dual-page controls still need to migrate into this dialog using the same callback pattern.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogAppliesTapZoneOverlayThroughControllerSettingsCommand"
```

Result: failed before the production change at `ReaderKomikkuBackboneResetTest.kt:603` because `KomikkuReaderSettingsDialog` did not accept an `onSettingsChange` callback.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogAppliesTapZoneOverlayThroughControllerSettingsCommand" --tests "paige.navic.reader.ReaderControllerTest.applySettingsKeepsControllerAsOwnerAndForwardsNormalizedSettingsToEngine" --tests "paige.navic.reader.ReaderControllerTest.settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog"
```

Result: passed on 2026-06-14 after callback wiring and the `Show tap zones` switch were added. The Gradle run executed 24 tasks with `--rerun-tasks`; existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Adjacent controller/shell evidence:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; existing warnings remained outside this slice in modal bottom sheet and Bindery test code. `git diff --check` returned clean.

## 2026-06-14 Komikku Tap-Zone Presets In Settings Dialog

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt:270-277`: Komikku's reader navigation preset catalog is ordered as Default, L-shaped, Kindle-ish, Edge, Right and Left, Disabled.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:337-350`: Komikku renders those navigation presets as `FilterChip` controls through `TapZonesItems(...)`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:122` and `:261`: Komikku exposes the smaller tap-zone setting as a settings-page checkbox for pager and webtoon modes.

Navic implication:

- `KomikkuReaderSettingsDialog` now uses an explicit `KomikkuTapZoneOptions` list in Komikku order instead of relying on `ReaderSupportedTapZones`, whose order exists for normalization and does not match the Komikku settings catalog.
- The Reading mode tab now renders `Tap zones` as `FilterChip` controls and applies changes through `onSettingsChange(settings.copy(tapZone = tapZone))`.
- The Reading mode tab now renders `Smaller tap zones` as a real switch and applies changes through `onSettingsChange(settings.copy(smallerTapZone = smallerTapZone))`.
- `Show tap zones` remains as a diagnostic/training overlay switch. It is not a Komikku navigation preset.
- This still does not claim settings parity. Tapping inversion, orientation/rotation, scale/zoom, crop, transition, and dual-page controls remain pending.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogUsesKomikkuTapZonePresetControlsInsteadOfStaticLabels"
```

Result: failed before the production change at `ReaderKomikkuBackboneResetTest.kt:648` because the active dialog had no `KomikkuTapZoneOptions` catalog and still displayed tap zones as a static label.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogUsesKomikkuTapZonePresetControlsInsteadOfStaticLabels"
```

Result: passed on 2026-06-14 after adding the explicit Komikku-order tap-zone catalog, chip row helper, tap-zone preset controls, and smaller-zone switch. The Gradle run executed 24 tasks.

## 2026-06-14 Komikku Reading-Mode Presets In Settings Dialog

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt:33-41`: Komikku renders every `ReadingMode.entries` value as a `FilterChip` and applies changes through `screenModel.onChangeReadingMode(it)`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt:22-57`: Komikku's reading-mode catalog is Default, Left to right, Right to left, Vertical, Webtoon, and Continuous vertical.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt:70-107`: reading mode chooses the active viewer type: horizontal pager, vertical pager, webtoon, or continuous vertical webtoon.

Navic implication:

- `KomikkuReaderSettingsDialog` now uses an explicit `KomikkuReadingModeOptions` list in Komikku order instead of displaying `flowMode` and `direction` as static text.
- The Reading mode tab maps those options into Navic settings:
  - Default -> paged horizontal with default direction.
  - Paged left-to-right -> `ReaderFlowPaged`, `paged = true`, `ReaderDirectionLtr`.
  - Paged right-to-left -> `ReaderFlowPaged`, `paged = true`, `ReaderDirectionRtl`.
  - Paged vertical -> `ReaderFlowPagedVertical`, `paged = true`, default direction.
  - Long strip -> `ReaderFlowScrolled`, `paged = false`, default direction.
  - Long strip with gaps -> `ReaderFlowScrolledGaps`, `paged = false`, default direction.
- Changes are applied through `onSettingsChange(settings.copy(...))`, preserving the controller/settings path instead of giving the renderer or dialog local ownership.
- This still does not claim settings parity. Orientation/rotation, tapping inversion, scale/zoom, crop, transitions, and dual-page controls remain pending.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogUsesKomikkuReadingModePresetControlsInsteadOfStaticLabels"
```

Result: failed before the production change at `ReaderKomikkuBackboneResetTest.kt:710` because the active dialog had no `KomikkuReadingModeOptions` catalog and still displayed reading mode/direction as static labels.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogUsesKomikkuReadingModePresetControlsInsteadOfStaticLabels"
```

Result: passed on 2026-06-14 after adding the Komikku-order reading-mode catalog, selected-mode resolver, and settings chips that write `flowMode`, `paged`, and `direction`.

Fresh host-side evidence for the 2026-06-14 typed content-action claim slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeTypedContentActionClaims" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome" --tests "paige.navic.reader.ReaderCoordinatorTest.bridgeEventsFlowFromAdapterIntoControllerWithoutBridgeOwningMenu"
```

Result: failed before the production change because `ReaderBridgeEvent.ContentTapHandled` was a singleton object and could not carry a `ReaderContentAction` payload. Passed after changing it to `ReaderBridgeEvent.ContentTapHandled(action)`, decoding bridge `source`/`action` fields into typed content actions, and mapping those actions through `FoliateWebViewEngineAdapter` into `ReaderEngineEvent.ContentActionClaimed(action)`. The non-cached green run executed 24 tasks with `--rerun-tasks`.

Boundary regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderMarksContentHandledOnReaderSurfaceThread"
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; existing warnings remained outside this slice in modal bottom sheet and Bindery test code. `git diff --check` returned clean.

Source reference for this slice:

- `tmp/references/anx-reader/assets/foliate-js/src/view.js:216-327`: Anx/Foliate distinguishes internal links, external links, image clicks, and view clicks instead of treating all view taps as one reader action.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:667-804`: Anx registers separate callbacks for click, TOC, selection, annotation, search, and image events; Navic adapts this as typed engine events rather than copying the UI ownership model.
- `composeApp/src/androidMain/assets/reader/navic-reader.js:1151`, `:1164`, `:1406`, `:1440`, and `:1479`: Navic already emits `readerContentTapHandled` with concrete sources for media touch, link touch, media anchor, link, and image interactions.

Navic implication: real content interactions can now tell the controller what claimed the tap. This is a step toward Komikku-faithful input ownership: the shell remains the reader-wide tap/menu owner, and Foliate/WebView only suppresses shell navigation when it emits an explicit typed content action.

Fresh host-side evidence for the 2026-06-14 controller-owned annotation capability slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.selectionHighlightsAreControllerOwnedAndForwardedAsEngineCapabilities" --tests "paige.navic.reader.ReaderCoordinatorTest.selectionHighlightsRouteThroughControllerAndCurrentEngineAdapter" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedEngineCapabilitiesAsFoliateBridgeCommands"
```

Result: failed before the production change because `ReaderController.addSelectionHighlight(...)`, `ReaderCoordinator.addSelectionHighlight(...)`, and `ReaderEngineCommand.ApplyAnnotations(...)` did not exist. Passed after adding controller-owned annotation state, a typed `ApplyAnnotations` engine command, coordinator forwarding, and Foliate adapter serialization to the existing `ReaderBridgeCommand.ApplyHighlights(...)` bridge. A non-cached green run with `--rerun-tasks` executed 24 tasks.

Boundary regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderAnnotationStateTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest"
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; existing warnings remained outside this slice in modal bottom sheet and Bindery test code. `git diff --check` returned clean.

Source reference for this slice:

- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:262-295`: Anx routes notes and bookmarks into the WebView by calling `addAnnotation(...)` with id, type, CFI value, color, and note, and removes them through `removeAnnotation(...)`.
- `tmp/references/anx-reader/assets/foliate-js/src/view.js:335-397`: Anx/Foliate resolves annotation CFIs, draws them through the overlayer, emits `draw-annotation`, supports `deleteAnnotation(...)`, and emits `show-annotation` on hit-tested annotation clicks.
- `composeApp/src/androidMain/assets/reader/navic-reader.js:1597-1608`: Navic already exposes `applyHighlight(...)` and `applyHighlights(...)` against Foliate `view.addAnnotation(...)`.

Navic implication: highlights now cross the Komikku controller boundary as an ebook capability instead of letting Foliate/WebView own the feature. The controller stores `ReaderAnnotationState`, selection state is converted into a `ReaderAnnotation`, and the active engine receives `ReaderEngineCommand.ApplyAnnotations(...)`. The Foliate adapter remains the only place that knows this becomes `applyHighlights` in the WebView bridge. This slice restores the capability direction needed for Anx-style notes/bookmarks/highlights without copying Anx's Flutter page controller or giving the WebView menu/chrome ownership.

Fresh host-side evidence for the 2026-06-14 controller-owned bookmark slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.currentBookmarksAreControllerOwnedAndDoNotEmitEngineCommands" --tests "paige.navic.reader.ReaderCoordinatorTest.currentBookmarkTogglesRouteThroughControllerWithoutEngineBridgeCommands"
```

Result: failed before the production change because `ReaderController.toggleCurrentBookmark(...)`, `ReaderCoordinator.toggleCurrentBookmark(...)`, `ReaderControllerState.bookmarks`, `ReaderControllerState.canBookmarkCurrentLocation`, and `ReaderControllerState.currentLocationBookmarked` did not exist. Passed after adding controller-owned bookmark state, computed current-location bookmark flags, and a coordinator toggle that intentionally emits no engine/WebView command.

Boundary regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderBookmarkStateTest" --tests "paige.navic.reader.ReaderAnnotationStateTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Source reference for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt:963-982`: Komikku toggles bookmark state in the reader viewmodel and updates reader state; the renderer does not own bookmark chrome.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:687-695`: Komikku passes bookmarked state and `onToggleBookmarked` into overlay app bars.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:276-286`: Anx can render a bookmark as a Foliate annotation by calling `addAnnotation(...)` with `type: 'bookmark'`.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:827-858`: Anx receives `handleBookmark`, persists/removes bookmark data, and then applies the bookmark back to the WebView.

Navic implication: bookmark truth now starts at the Komikku-style controller boundary. The current location can be bookmarked from `ReaderControllerState.chrome.currentLocator`, reflected through `currentLocationBookmarked`, and toggled by the coordinator without dispatching any WebView command. Rendering bookmark glyphs inside EPUB content can still be added later as an engine capability, but it must consume controller/domain bookmark state rather than owning the bookmark feature.

Fresh host-side evidence for the 2026-06-14 controller-owned progress/page-state slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.engineRelocationsBuildControllerOwnedProgressSnapshotsAfterPublicationReady" --tests "paige.navic.reader.ReaderCoordinatorTest.relocationsRouteProgressSaveIntentThroughControllerWithoutEngineBridgeCommands"
```

Result: failed before the production change because `ReaderControllerState.readingProgress`, `ReaderControllerStep.progressToSave`, and `ReaderCoordinatorStep.progressToSave` did not exist. Passed after adding controller-owned `ReaderReadingProgressState`, routing `ReaderEngineEvent.PublicationReady` and `ReaderEngineEvent.Relocated` through `ReaderProgressSaveGate`, converting saved locators to `BinderyReadingProgress`, and forwarding the save intent through the coordinator without dispatching a WebView command.

Additional red check for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.engineRelocationsBuildControllerOwnedProgressSnapshotsAfterPublicationReady"
```

Result: failed after the first implementation because relocation updated the chrome and progress snapshot but did not carry the updated `ReaderProgressSaveGate` forward. This meant a later cover relocation after a readable page was still treated as a startup placeholder. Passed after storing `progressSaveGate = decision.state` on relocated controller copies.

Boundary regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.reader.ReaderBookmarkStateTest" --tests "paige.navic.reader.ReaderAnnotationStateTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest"
git diff --check
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`; existing warnings remained outside this slice in modal bottom sheet and Bindery test code. `git diff --check` returned clean.

Source reference for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:1148-1168`: Komikku receives active page changes from the viewer, computes the page label, and delegates the selected page to the view model.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt:807-825`: Komikku updates reader state, requested page, saved page index, and persisted last-read page from the page-selected event.
- `tmp/references/anx-reader/assets/foliate-js/src/view.js:175-194`: Anx/Foliate builds relocation data from section progress, TOC/page progress, CFI, and renderer page counts before emitting `relocate`.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:634-663`: Anx consumes `onRelocated`, updates CFI, percentage, chapter title/href, current/total chapter pages, bookmark state, provider state, and then calls `saveReadingProgress()`.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:962-966`: Anx persists `lastReadPosition` and reading percentage from the relocation-derived state.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt:160-165`: Navic now adapts engine-level ready/relocated events into the existing save-gate decision without making `ReaderBridgeEvent` the controller API.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt:38`, `:66`, and `:96-121`: Navic stores reading progress at the controller boundary and emits a `BinderyReadingProgress` save intent from relocation.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt:6-7` and `:62-63`: Navic forwards that save intent upward while leaving the engine/WebView command stream untouched.

Navic implication: page/progress truth now belongs to the Komikku-style controller path. Foliate/Anx remains the source of relocation capability data, but the controller owns whether a relocation is saveable, the canonical Bindery progress snapshot, and the save intent that the app layer can persist. This prevents the WebView runtime from becoming the owner of resume/progress state while still recycling the proven relocation data that Anx/Foliate already exposes.

App-boundary follow-up for the same slice:

- Before this follow-up, `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt:110-112` applied a coordinator step by assigning only `coordinator = step.coordinator`. That dropped `ReaderCoordinatorStep.progressToSave`, so the controller/coordinator could produce valid Bindery progress while the app layer never persisted it.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt:232-234` is the existing app/domain boundary for saving `BinderyReadingProgress`.
- `composeApp/src/commonMain/kotlin/paige/navic/di/RepositoryModule.kt:35` already exposes `BinderyRepository` through Koin; the reader screen now uses that dependency as the save sink instead of making the controller or engine know about repositories.

Required ownership rule: `ReaderScreen` may consume `ReaderCoordinatorStep.progressToSave` as an app-boundary side effect, but `ReaderController`, `ReaderCoordinator`, `ReaderEngine`, `FoliateEpubEngineAdapter`, and `navic-reader.js` must stay repository-free. The only acceptable flow is:

```text
Anx/Foliate relocation data
  -> ReaderEngineEvent.Relocated
  -> ReaderController progress/save-gate decision
  -> ReaderCoordinatorStep.progressToSave
  -> ReaderScreen app boundary
  -> BinderyRepository.putReadingProgress(...)
```

This was the next implementation target before adding more reader controls. It closes the resume/progress persistence loop without drifting back into WebView-owned state or a one-off UI workaround.

Fresh host-side evidence for the app-boundary progress persistence handoff:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorStepConsumerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenPersistsControllerProgressIntentThroughBinderyBoundary"
```

Result: failed before the production change because `applyReaderCoordinatorStep(...)` did not exist and `ReaderScreen.applyCoordinatorStep(...)` reduced each step to `coordinator = step.coordinator`. Passed after adding a pure `ReaderCoordinatorStepConsumer` helper, wiring `ReaderScreen` to consume `progressToSave`, and launching `BinderyRepository.putReadingProgress(progress)` from the app boundary.

Boundary regression evidence for the same slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderCoordinatorStepConsumerTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Additional ownership check for the same slice:

```powershell
rg -n "BinderyRepository|putReadingProgress|koinInject<BinderyRepository>|applyReaderCoordinatorStep" composeApp/src/commonMain/kotlin/paige/navic/reader composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt -S
```

Result: `BinderyRepository` and `putReadingProgress(progress)` appear in `ReaderScreen.kt`; the `paige.navic.reader` package contains only the pure `applyReaderCoordinatorStep(...)` helper and remains repository-free.

App-boundary restore follow-up for the same slice:

- Before this follow-up, `ReaderPublicationRuntimeHost.android.kt` resolved the publication URL but did not load saved Bindery progress, and `Screen.Reader.toReaderEngineOpenRequest(...)` could only build a start locator from explicit route `startCfi`/`startHref`. That left the open path unable to resume from the saved progress written by the controller handoff above.
- Restore is now intentionally app-boundary owned: Android loads `BinderyRepository.getReadingProgress(bookId)` in `ReaderPublicationRuntimeHost.android.kt`, filters it through `toReaderStartLocatorForReader(...)`, passes it through `ReaderPublicationRuntimeHost` as `savedProgress`, and lets `ReaderScreen.kt` combine route and saved progress with `bestReaderStartLocator(...)` when building `ReaderEngineOpenRequest.startLocator`.
- Explicit route locators still win when they represent a real requested location. Saved Bindery progress wins over startup placeholders/cover-like route starts when it is a later readable location. The engine receives only a `ReaderLocator`; it does not know about repositories or Bindery.
- iOS keeps the same public host callback shape but passes `null` for saved progress until an equivalent platform repository boundary is implemented there.

Required ownership rule for opening/resume:

```text
BinderyRepository.getReadingProgress(...)
  -> ReaderPublicationRuntimeHost.android app boundary
  -> ReaderScreen route/saved start-locator arbitration
  -> ReaderEngineOpenRequest.startLocator
  -> ReaderEngine.open(...)
```

Forbidden ownership drift: `ReaderController`, `ReaderCoordinator`, `ReaderEngine`, `FoliateEpubEngineAdapter`, and `navic-reader.js` must not query `BinderyRepository` or parse persisted progress directly. They may consume only the start locator and relocation events.

Fresh host-side evidence for saved progress restore on open:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.ui.screens.reader.ReaderOpenRequestFactoryTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerPublicationRuntimeLoadsSavedBinderyProgressBeforeOpeningEngine"
```

Result: failed before the production change because `Screen.Reader.toReaderEngineOpenRequest(...)` had no `savedProgress` parameter. Passed after adding `savedProgress` to the open-request factory, loading saved progress in the Android publication runtime host, threading it through `onPublicationReady`, and asserting that explicit route start locators override older saved progress.

Boundary regression evidence for the same restore slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.ui.screens.reader.ReaderOpenRequestFactoryTest" --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderCoordinatorStepConsumerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

Source reference for this restore slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt:356-365`: Komikku restores the current chapter's requested page from saved state or `last_page_read` before the viewer opens that chapter.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt:1027-1037` and `:1061-1071`: Komikku keeps current-page state at the viewmodel boundary when reader mode/orientation changes force a reload.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:1254-1257`: Anx opens Foliate with `initialCfi` from the route-provided CFI or `book.lastReadPosition`.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt:45-52`: Navic converts saved Bindery progress into a reader start locator only when it matches the current reader resource/kind or has a usable progress-only fallback.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt:100-116`: Navic chooses between route and saved locators without letting a cover/start placeholder mask a later saved reading location.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPublicationRuntimeHost.android.kt:116-134`: Android loads saved Bindery progress at the platform app boundary and rejects progress that cannot be converted to a locator for the current reader.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt:698-728`: `Screen.Reader.toReaderEngineOpenRequest(...)` now accepts `savedProgress` and emits the final `ReaderEngineOpenRequest.startLocator`.

## 2026-06-14 Explicit Viewer Lifecycle Slot

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:941-967`: `updateViewer()` creates the new viewer, calls `prevViewer.destroy()`, clears `viewerContainer`, stores the new viewer, and mounts `newViewer.getView()`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt:12-22`: `Viewer` is an open interface with a `destroy()` lifecycle hook, not a sealed enum of renderer branches.

Navic implication:

- `ReaderViewer` is now an open interface, matching the Komikku contract shape. This lets the viewer boundary grow into concrete EPUB/PDF/image/spread/page-curl viewers instead of staying a sealed inline switch.
- `ReaderViewerLifecycleSlot` owns the active viewer instance. It updates the current viewer when the key is unchanged, destroys the previous viewer when the key changes, creates the replacement through `readerViewerFor(...)`, and destroys the active viewer when the root leaves.
- `KomikkuReaderRoot` now keeps this lifecycle slot with `remember { ReaderViewerLifecycleSlot() }`, updates it from `viewState`, passes `viewer.key` to the native frame host, routes native tap regions through `viewer.viewerActionFor(...)`, and disposes the slot from `DisposableEffect`.
- `ReaderViewerHost` remains only the renderer mount boundary. It receives the active viewer and translates that viewer into `ReaderEngineWebViewHost`; it does not create, swap, or dispose viewers itself.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
```

Result: failed before the production change because `ReaderViewerLifecycleSlot` did not exist and `ReaderViewer` was still sealed, so the test could not create a recording viewer to verify lifecycle semantics.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
```

Result: passed after adding `ReaderViewerLifecycleSlot`, opening `ReaderViewer` as an interface, and wiring `KomikkuReaderRoot` through the slot.

Adjacent viewer/backbone check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerViewerHostUsesKomikkuViewerLifecycleBoundary" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerRootKeepsSingleActiveViewerForHostAndNavigationActions" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostSwapsViewerContentByReaderViewerKeyLikeKomikkuUpdateViewer"
```

Result: passed on 2026-06-14.

## 2026-06-14 Organic Page Number And Passive Relocation Clamp

User direction for this slice:

- The preferred page number is the one that looks like part of the book/page surface, not a mobile UI overlay.
- The `# / #` design should remain, but only in the reader-surface layer that inherits ebook typography and blends into the paper.
- The blue/native duplicate overlay must stay disabled for WebView-backed publications.

Observed root cause for the non-linear sequence:

- Eta64 ADB evidence in `tmp/eta64-adb-validation/frontmatter-walk/frontmatter-walk.log` showed explicit `page-turn:next` posts advancing to page 5 in `OEBPS/Text/Hobbit_map-1.html`.
- A delayed passive `relocate-committed` event in the same section then posted page 13 before the next user page turn.
- That means Foliate's passive relocation stream can overwrite the explicit one-page turn sequence with a raw section/global estimate. This is a model/state bug, not a visual page-number style issue.

Navic implication:

- `ReaderScreen.kt` no longer draws the Compose/mobile page-number overlay for `WebViewPublicationReaderViewer`; the organic `navic-reader.js` page-number layer is the single visual owner for EPUB/PDF WebView publications.
- `navic-reader.js` now tracks the last committed relocation detail and clamps only passive same-section `relocate-committed` jumps larger than one page.
- Explicit page turns still use `page-turn:next` / `page-turn:previous` clamping.
- Link and progress jumps mark their relocation reason as `go-to` or `progress-seek`, so deliberate long jumps are not collapsed into one-page movement.
- The clamp intentionally does not claim to solve chapter-local rail sizing, texture direction inversion, or native touch/menu ownership. Those remain active Komikku-backbone acceptance items.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
```

Result: failed before the page-number visual-owner production change because `shouldShowNativeReaderPageIndicator(...)` did not exist.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderClampsDelayedPassiveReflowableRelocationsAfterPageTurns
```

Result: failed before the relocation production change because `passiveCommittedRelocationPosition(pagePosition, detail, reason)` did not exist.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon :composeApp:compileAndroidHostTest
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderViewerTest
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderClampsDelayedPassiveReflowableRelocationsAfterPageTurns
```

Result: passed on 2026-06-14.

Broader shell contract status at this point in the sequence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest
```

Result: still failed on two older architecture assertions. This status was superseded later on 2026-06-14 by the "Reader Shell Contract Realignment" slice below.

- `readerChromeIsImmersiveAndDrivenByNativeReaderSurfaceTaps`: `ReaderScreen.kt` still contains `ReaderNativeTapOverlay(`.
- `androidReaderPreservesProgressOnlyResumeLocatorsForFixedLayoutPublications`: the current `ReaderScreen.kt` path still does not contain the expected `startProgress = resumeStartLocator?.progress` wiring.

These two failures are not fixed by the organic page-number/passive-relocation slice and remain part of the active Komikku frontend/controller backbone work.

## 2026-06-14 Reader Shell Contract Realignment

User direction for this slice:

- Keep the page number that looks like part of the book/page surface, not a native mobile UI overlay.
- Do not reintroduce raw Foliate bridge handling inside `ReaderScreen` as a shortcut. The Komikku shell/controller boundary must stay intact.

Navic implication:

- `ReaderScreen` forwards engine-host events through `coordinator.onEngineHostEvent(...)`; it must not handle `ReaderBridgeEvent.CenterTap` directly.
- `ReaderCoordinator` and the active `ReaderEngine` adapter own the `ReaderEngineHostEvent` to `ReaderEngineEvent` boundary.
- `ReaderViewerHost` owns the concrete renderer host wiring, including `startProgress = viewer.viewState.startLocator?.progress`, instead of pushing resume-progress plumbing back into `ReaderScreen`.
- The fullscreen/system-bars source contract now checks the current controller model: `systemBarsVisible = controllerState.menuVisible || settings.fullscreen == false`. Opening reader settings sets `menuVisible = true`, so this preserves the Komikku-like fullscreen behavior without reviving an old `optionsVisible` variable.
- The Compose/native page indicator remains suppressed for `WebViewPublicationReaderViewer`; EPUB/PDF WebView publications keep the organic reader-surface page number as the single visual owner.

Focused checks:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest
```

Result: both passed on 2026-06-14 after correcting stale source-contract assertions. No release was triggered.

## 2026-06-14 Viewer-Owned Movement Actions

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:129-135`: the pager viewer receives a navigation region and invokes `activity.toggleMenu()`, `moveToNext()`, `moveToPrevious()`, `moveRight()`, or `moveLeft()` inside the viewer.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:352-384`: default paged movement maps next/previous to right/left movement and then lets the pager pan or change pages.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewers.kt:40-50`: the R2L pager viewer overrides next/previous movement inside the viewer implementation, not in the activity/controller.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:143-147`: the webtoon viewer maps menu to `toggleMenu()`, next/right to `scrollDown()`, and previous/left to `scrollUp()`.

Navic implication:

- Native tap classification still belongs to the native frame host and `KomikkuReaderNavigator`, but region-to-movement semantics now belong to the active `ReaderViewer`.
- `ReaderViewer` exposes `viewerActionFor(region)` and returns a neutral `ReaderViewerAction`: `Menu`, `TurnPage(direction)`, or `ScrollViewport(direction)`.
- `PagedPublicationReaderViewer` maps menu/next/previous/physical left/physical right to page-turn actions and uses reader direction for physical left/right, matching Komikku's pager-variant ownership model.
- `WebtoonPublicationReaderViewer` maps next/right to viewport down and previous/left to viewport up, matching Komikku's webtoon viewer behavior.
- `ReaderController` and `ReaderCoordinator` now execute `ReaderViewerAction` through `onViewerAction(...)`. `ReaderControllerNavigationAction` has been removed rather than kept as a compatibility adapter, because retaining it creates a second movement API that can drift away from the active viewer.
- `ReaderScreen` now routes native tap regions as `onViewerAction(viewer.viewerActionFor(action))`, so the retained active viewer owns movement semantics before the controller executes engine capabilities.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
```

Result: failed before the production change because `ReaderViewerAction`, `viewerActionFor(...)`, and `readerViewerActionFor(...)` did not exist, and the test recording viewer still had to implement the old `navigationActionFor(...)` controller leak.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest" --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsSuppressOnlyTheNextReaderMenuAction" --tests "paige.navic.reader.ReaderControllerTest.viewerScrollActionsAreControllerOwnedAndForwardedAsEngineCapability" --tests "paige.navic.reader.ReaderCoordinatorTest.viewerActionsDispatchThroughCurrentEngineAdapter"
```

Result: passed on 2026-06-14 after adding `ReaderViewerAction`, replacing `navigationActionFor(...)` with `viewerActionFor(...)`, wiring `ReaderScreen` through `coordinator.onViewerAction(...)`, and moving the primary controller/coordinator tests onto the viewer-action path.

Adjacent viewer-action regression evidence:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderCoordinatorStepConsumerTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.commonReaderChromeExposesPageTurnControls"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

## 2026-06-14 Remove Legacy Controller Navigation Compatibility

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt:129-135`: navigation-region handling enters the current pager viewer, which chooses menu, next, previous, right, or left movement.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt:143-147`: the webtoon viewer handles the same region set but maps movement to viewport scrolling.

Navic implication:

- The old `ReaderControllerNavigationAction` enum and the wrapper APIs `ReaderController.onReaderNavigationAction(...)` and `ReaderCoordinator.onNavigationAction(...)` are removed from production common code.
- `ReaderScreen` still receives neutral native `KomikkuNavigationRegion` values, but it must first ask the active `ReaderViewer` for a `ReaderViewerAction`.
- `ReaderController.onViewerAction(...)` remains the only movement entrypoint. This keeps pager, vertical/webtoon, PDF, and future page-curl movement differences behind the active viewer instead of letting the controller own a generic LEFT/RIGHT/page-turn rule.
- `ReaderKomikkuBackboneResetTest.readerMovementApiExposesViewerActionsInsteadOfLegacyControllerNavigationActions` now guards against reintroducing the removed controller-navigation enum or wrappers.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerMovementApiExposesViewerActionsInsteadOfLegacyControllerNavigationActions"
```

Result: failed before the production removal because `ReaderControllerNavigationAction`, `ReaderController.onReaderNavigationAction(...)`, and `ReaderCoordinator.onNavigationAction(...)` still existed.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerMovementApiExposesViewerActionsInsteadOfLegacyControllerNavigationActions"
```

Result: passed on 2026-06-14 after removing the compatibility enum/wrappers and leaving only the viewer-action movement path.

Adjacent viewer/controller regression evidence:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderCoordinatorStepConsumerTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.commonReaderChromeExposesPageTurnControls"
```

Result: passed on 2026-06-14. The Gradle run executed 24 tasks with `--rerun-tasks`. Existing warnings remained outside this slice in modal bottom sheet and Bindery test code.

## 2026-06-14 Native Viewer Composition Teardown

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:956-962`: before mounting a new viewer, Komikku calls `prevViewer.destroy()` and clears `viewerContainer`.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt:17-22`: `destroy()` is the viewer lifecycle hook called when leaving or swapping viewers.

Navic implication:

- The common `ReaderViewerLifecycleSlot` now destroys the logical viewer on key changes, but Android also has to dispose the old renderer `ComposeView` before replacing the renderer child inside the native viewer container. Otherwise the removed composition can keep the renderer host and WebView cleanup tied to the root lifecycle rather than the viewer swap.
- `KomikkuReaderNativeFrameHost.android.kt` now calls `currentViewerComposeView?.disposeComposition()` before replacing viewer content, and disposes both active viewer and overlay compositions in `onDetachedFromWindow()`.
- This keeps the current Compose-hosted WebView bridge closer to Komikku's updateViewer semantics while the longer-term target remains concrete native viewer adapters in the viewer slot.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostDisposesOldViewerCompositionWhenSwappingLikeKomikkuUpdateViewer"
```

Result: failed before the production change because the Android native frame removed old viewer children without disposing the previous viewer composition first.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostDisposesOldViewerCompositionWhenSwappingLikeKomikkuUpdateViewer"
```

Result: passed after disposing the old viewer composition before removal and disposing active compositions on detach.

Adjacent native/viewer lifecycle check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostSwapsViewerContentByReaderViewerKeyLikeKomikkuUpdateViewer" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostDisposesOldViewerCompositionWhenSwappingLikeKomikkuUpdateViewer" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerRootKeepsSingleActiveViewerForHostAndNavigationActions" --tests "paige.navic.ui.screens.reader.ReaderViewerLifecycleSlotTest"
```

Result: passed on 2026-06-14.

## 2026-06-14 Content Action Claim Metadata

Anx/Foliate source behavior for this slice:

- `tmp/references/anx-reader/assets/foliate-js/src/view.js:213-327`: Foliate distinguishes document links, internal/external links, image/view clicks, history, and relocation events at the view boundary.
- `tmp/references/anx-reader/lib/page/book_player/epub_player.dart:667-804`: Anx keeps link/navigation, selection, spoken text, search, and metadata events as typed callbacks instead of flattening them into generic taps.

Navic implication:

- WebView content remains a renderer/engine capability. It may report that content consumed a tap, but it must not own reader chrome or menu visibility.
- `readerContentTapHandled` now carries a typed claim with action, source, href, src, text/alt, cfi, and tap coordinates when available.
- `ReaderBridgeEvent.ContentTapHandled`, `ReaderEngineEvent.ContentActionClaimed`, `FoliateEpubEngineAdapter`, and `ReaderControllerState.lastContentActionClaim` preserve that claim instead of collapsing it to only `Link` / `Image` / `MediaControl`.
- This keeps the Komikku shell in charge of menu suppression while giving later controller features enough metadata for proper link, image, annotation, footnote, and media behavior.
- Page-number visual ownership remains unchanged: the preferred page number is the organic `# / #` rendered as part of the book/page surface, not a Compose/native mobile UI overlay. Do not reintroduce a duplicate blue or native overlay while working on content claims.

Fresh red check for this slice:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeContentActionClaimMetadataFromFoliateLikeClicks --tests paige.navic.reader.FoliateEpubEngineAdapterTest.mapsBridgeContentClaimsWithMetadataToEngineEvents --tests paige.navic.reader.ReaderControllerTest.contentActionClaimsKeepMetadataInControllerState
```

Result: failed before the production changes because `ReaderContentActionClaim` did not exist and content-action bridge/engine events exposed only the coarse action enum.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderCoordinatorTest
```

Result: passed on 2026-06-14 after preserving content-action claim metadata through JS bridge decoding, the Foliate adapter, and controller state.

## 2026-06-14 Organic Page Number Ownership Hardening

User direction for this slice:

- Hard preference: keep the page number that looks like part of the book/page surface over any native/mobile UI overlay.
- Keep the `# / #` design only in the organic reader-surface layer that can inherit ebook typography and sit inside the paper/texture context.
- Treat the organic page number as part of the ebook visual language, not as reader chrome. The native shell can own canonical page state, but it must not render a visible competing page badge.

Navic implication:

- `ReaderScreen.kt` no longer contains `KomikkuReaderPageIndicator` or any Compose/native page-number overlay call.
- `ReaderViewer.kt` no longer exposes `shouldShowNativeReaderPageIndicator(...)`; keeping a disabled policy hook would make the duplicate overlay easy to re-enable later.
- Page-number presentation for WebView-backed EPUB/PDF remains the reader-surface/engine-rendered layer. The controller still owns normalized page state, but the shell does not draw a competing page badge.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderViewerTest.composeShellDoesNotOwnReaderPageNumberOverlay
```

Result: first failed while `ReaderScreen.kt` still contained `KomikkuReaderPageIndicator`, then failed again while `ReaderViewer.kt` still contained `shouldShowNativeReaderPageIndicator(...)`.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderViewerTest.composeShellDoesNotOwnReaderPageNumberOverlay
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderViewerTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderCoordinatorTest --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest
```

Result: passed on 2026-06-14 after deleting the Compose page indicator implementation, removing the native page-indicator policy hook, and leaving page-number visuals to the book surface.

## 2026-06-14 Bottom Bar Dialog Ownership

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt`: bottom-bar icons dispatch callbacks such as `onClickChapterList`, `onClickReadingMode`, and `onClickSettings`; they are not visual-only buttons.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`: those callbacks are supplied by the reader layer alongside the chapter navigator and settings controls.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`: reader settings are a tabbed overlay dialog above content, not a docked panel that resizes the viewer.

Navic implication:

- Page-number visual ownership remains fixed: the preferred page number is the organic `# / #` mark that looks printed/layered into the book page surface. Do not replace it with a native/mobile UI overlay while reshaping the Komikku chrome.
- `ReaderControllerDialog` now has `Contents`, `ReadingMode`, and `Settings`, all controller-owned and engine-command-free.
- `ReaderCoordinator` exposes `openContentsDialog()`, `openReadingModeDialog()`, and `openSettingsDialog()` as controller routes.
- `ReaderScreen` no longer renders dead bottom-bar `IconButton(onClick = {})` actions for Contents or Reading mode, and the top-bar Back action is routed through the app `LocalNavStack`.
- Contents opens a Komikku-style overlay listing the controller-owned TOC and navigates by emitting a controller/coordinator `NavigateTo(ReaderLocator(href = ...))` command.
- Reading mode opens the tabbed settings dialog on tab 0; Settings opens the same dialog on tab 1. This keeps the overlay model unified while preventing the bottom buttons from becoming fake chrome.
- Reader chrome actions may not be decorative-only. Source tests now guard against `IconButton(onClick = {})` in `ReaderScreen.kt`.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog --tests paige.navic.reader.ReaderCoordinatorTest.bottomBarDialogsRouteThroughControllerWithoutEngineCommands --tests paige.navic.reader.ReaderViewerTest.komikkuBottomBarActionsAreNotDeadButtons
```

Result: failed before the production changes because `openContentsDialog`, `ReaderControllerDialog.Contents`, and `ReaderControllerDialog.ReadingMode` did not exist.

Additional red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderViewerTest.komikkuReaderChromeDoesNotKeepDeadIconButtons
```

Result: failed while the top-bar Back icon was still `IconButton(onClick = {})`.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest.settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog --tests paige.navic.reader.ReaderCoordinatorTest.bottomBarDialogsRouteThroughControllerWithoutEngineCommands --tests paige.navic.reader.ReaderViewerTest.komikkuBottomBarActionsAreNotDeadButtons
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderViewerTest.komikkuReaderChromeDoesNotKeepDeadIconButtons
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderCoordinatorTest --tests paige.navic.reader.ReaderViewerTest --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest --tests paige.navic.ui.screens.reader.ReaderOpenRequestFactoryTest
```

Result: passed on 2026-06-14 after routing Contents, Reading mode, and Settings through controller-owned dialogs, replacing bottom-bar no-op buttons with real callbacks, and wiring top-bar Back through the app nav stack.

## 2026-06-14 Legacy Options Panel Removal

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`: reader settings are overlay dialogs above the viewer.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt`: settings pages are dialog content driven by reader/controller state.

Navic implication:

- The pre-reset `ReaderOptionsPanel.kt` remains available in `vault/reader/2026-06-13-pre-komikku-reset/...` for reference.
- The active common source tree no longer keeps `ReaderOptionsPanel.kt`. Keeping that file in active source preserved the old docked settings model and raw `ReaderBridgeCommand` / `ReaderBridgeEvent` imports as an easy escape hatch.
- Current settings entrypoints stay in `ReaderControllerDialog.Contents`, `ReaderControllerDialog.ReadingMode`, and `ReaderControllerDialog.Settings`.
- Page-number visual ownership remains unchanged: organic book-surface `# / #`, no Compose/native page-number overlay.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeSourceTreeDoesNotKeepLegacyReaderOptionsPanel"
```

Result: failed while `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt` still existed in the active source tree.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeSourceTreeDoesNotKeepLegacyReaderOptionsPanel"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Result: passed on 2026-06-14 after deleting the active `ReaderOptionsPanel.kt` file and updating stale reset assertions so they reject Compose page-number ownership and recognize the controller-owned settings dialog route.

## 2026-06-14 Legacy WebView Host Removal

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`: Komikku owns the reader frame, app bars, and viewer container outside the concrete viewer implementation.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt`: concrete viewers are replaceable content engines under the reader frame, not owners of the whole reader shell.

Navic implication:

- The active common platform contract no longer exposes `ReaderWebViewHost(...)`.
- The active Android and iOS source trees no longer contain `ReaderWebViewHost.android.kt` or `ReaderWebViewHost.ios.kt`.
- The vaulted pre-Komikku Android host remains available at `vault/reader/2026-06-13-pre-komikku-reset/composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`.
- Active EPUB/PDF WebView rendering must continue through `ReaderEngineWebViewHost`. It can own Foliate/WebView command translation, but it must not reintroduce shell-cover rendering, reader-wide tap fallbacks, or page-number chrome.
- Page-number visual ownership remains unchanged: organic book-surface `# / #`, no Compose/native page-number overlay.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeSourceTreeDoesNotExposeLegacyReaderWebViewHost"
```

Result: failed while `ReaderPlatformHosts.kt` still exposed `expect fun ReaderWebViewHost(...)` and the Android/iOS actual files still existed.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeSourceTreeDoesNotExposeLegacyReaderWebViewHost"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Result: passed on 2026-06-14 after deleting the active legacy WebView host expect/actual files and adding a source guard that keeps the old host vaulted-only.

Full-suite note:

- `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost` is currently not green on this reset branch. It reports stale tests that still assert the removed docked options panel and pre-Komikku `ReaderWebViewHost` internals. Do not satisfy those tests by resurrecting the old host or the old options panel; migrate or delete stale assertions as the Komikku backbone replaces those responsibilities.

## 2026-06-14 Native Cover Swipe Through Komikku Frame

Komikku source behavior for this slice:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt`: Komikku dispatches the event to child/page content first, then runs the viewer gesture detector.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt`: confirmed gestures are translated through `ViewerNavigation.NavigationRegion` into viewer actions such as menu, next, previous, left, and right.

Navic implication:

- `KomikkuReaderNativeViewerContainer.dispatchTouchEvent(...)` remains child-first: it calls `super.dispatchTouchEvent(event)` before native gesture handling.
- Horizontal shell-cover drags now observe `ACTION_DOWN`/`ACTION_MOVE`/`ACTION_UP` with Android `ViewConfiguration.scaledTouchSlop`.
- A left drag over the shell cover emits `KomikkuNavigationRegion.NEXT`; a right drag emits `KomikkuNavigationRegion.PREV`. The common shell still translates those regions through the active `ReaderViewer.viewerActionFor(...)`.
- This slice intentionally scopes native swipe dispatch to the shell-cover view while EPUB/PDF WebView pages still own their current drag stream. That avoids double page turns on normal text pages until the concrete EPUB/PDF viewer fully owns pager movement.
- Native cover swipe still requires device validation in an installed APK. The source-level change proves the callback path exists; it does not claim that eta64 or any already-installed build has working cover drag.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameDispatchesHorizontalSwipesThroughViewerActionBoundary"
```

Result: first failed while the native frame had no horizontal swipe dispatch, then failed again after adding broad dispatch because the test requires shell-cover scoping to avoid double-turning WebView pages.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameDispatchesHorizontalSwipesThroughViewerActionBoundary"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests paige.navic.reader.ReaderViewerTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderCoordinatorTest --tests paige.navic.reader.FoliateEpubEngineAdapterTest
```

Result: passed on 2026-06-14 after adding cover-scoped native horizontal swipe observation to `KomikkuReaderNativeViewerContainer`.

## 2026-06-14 Controller Cover Return And Frontmatter Page-Number Clamp

User/device evidence addressed in this slice:

- Eta64 could go backward from the first readable EPUB page into the suppressed EPUB cover, leaving a blank reader page with only `1 / n`.
- Eta64 frontmatter page numbers could jump nonlinearly across section boundaries, for example `2`, `3`, `5`, `6`, `15`.

Navic implication:

- The controller now retains `nativeShellCoverUrl` and `canReturnToShellCover` from `ReaderEngineOpenRequest`.
- `ReaderController.turnPage(Previous)` intercepts the first-readable-page boundary using `readerShouldReturnToNativeShellCover(...)`; it shows the controller-owned shell cover and emits no Foliate/WebView `PreviousPage` command.
- The organic page number remains the only visible page number for WebView-backed publications. No Compose/native page-number overlay was restored.
- `navic-reader.js` now tracks one `recentPageTurnDirection` and lets the next passive `relocate-committed` event clamp across EPUB section/frontmatter boundaries after a real sequential page turn.
- Explicit links and progress seeks still schedule `go-to` / `progress-seek`, clear the recent page-turn direction, and are allowed to jump.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderCoordinatorTest.previousFromFirstReadablePageReturnsToControllerOwnedShellCoverWithoutFoliateCommand
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderClampsDelayedPassiveReflowableRelocationsAfterPageTurns
```

Results:

- The controller/coordinator test failed before the production change because the controller emitted a Foliate previous-page command instead of restoring shell-cover state.
- The runtime shell progress test failed before the JS change because passive relocation clamping only allowed same-section clamps and did not preserve a recent sequential page-turn direction.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderCoordinatorTest.previousFromFirstReadablePageReturnsToControllerOwnedShellCoverWithoutFoliateCommand
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderControllerTest --tests paige.navic.reader.ReaderCoordinatorTest --tests paige.navic.reader.ReaderChromeStateTest
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderClampsDelayedPassiveReflowableRelocationsAfterPageTurns
```

Results: passed on 2026-06-14.

Broader status:

- `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellProgressTest` is still red on older source-contract assertions that target the removed pre-reset WebView host/native surface symbols. Do not make those assertions pass by restoring `ReaderWebViewHost` or the old reader surface. Migrate those assertions to the Komikku frame/controller model before treating the full class as a release gate.

## 2026-06-14 Komikku Rail Slot And Native Short-Tap Ownership

User/device evidence addressed in this slice:

- Eta64's side progress rail looked like a mobile scrollbar pinned to the full screen edge; it could run under the top and bottom chrome instead of occupying Komikku's middle reader-appbar slot.
- EPUB/image/link interaction could still race the reader menu because the WebView saw a normal touch first and JS could claim content ownership before the native tap-zone action.
- The accepted behavior for this slice is: normal taps are reader-owned native region actions; WebView content interaction should be deliberate long-press behavior instead of ordinary short-tap behavior.

Komikku source behavior used:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`: app bars are a full-height `Column`; vertical chapter navigation lives in a weighted middle region between top and bottom bars.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/GestureDetectorWithLongTap.kt`: Komikku uses a custom gesture detector to distinguish confirmed short taps from long taps.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt`: confirmed short taps route to reader navigation, while confirmed long taps are a separate viewer/content path.
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`: Komikku has a `readWithLongTap()` reader control; the default is enabled.

Navic implication:

- `KomikkuReaderAppBars(...)` now uses the Komikku full-height `Column` structure instead of a free-floating full-screen `Box`.
- The vertical `KomikkuChapterNavigator(...)` now sits in the weighted middle chrome slot via `Modifier.weight(1f).align(Alignment.End)`, so it cannot occupy the top/bottom chrome bands.
- `KomikkuReaderNativeViewerContainer` now ports a `KomikkuGestureDetectorWithLongTap`-equivalent class.
- The native viewer container intercepts the final `ACTION_UP` for short tap candidates before WebView turns them into link/image clicks.
- If the press survives long enough to be confirmed as a long tap, the native container marks the stream as long-press content behavior and does not intercept the final `UP`.
- `navic-reader.js` no longer posts early `readerContentTapHandled` claims from `touchstart` / `pointerdown` / `mousedown` when `nativeTapZones === true`; those early claims conflict with native-owned short taps.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuAppBarsOwnSideNavigatorInMiddleWeightedChromeSlot
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent
```

Results:

- The rail-slot test failed before the production change because the vertical navigator was mounted with `Modifier.align(Alignment.CenterEnd).fillMaxHeight()` inside a full-screen `Box`.
- The native short-tap ownership test failed before the production change because the native frame used a plain child-first tap fallback and JS still claimed content touches in native-tap-zone mode.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuAppBarsOwnSideNavigatorInMiddleWeightedChromeSlot
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuAppBarsOwnSideNavigatorInMiddleWeightedChromeSlot --tests paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

Results: passed on 2026-06-14.

Release status:

- This slice has not been compiled into a release APK and has not been device-validated. Do not claim eta64/eta65 behavior from these source checks.
- Do not trigger a GitHub release for this slice by itself unless it is bundled with enough major reader behavior fixes to justify the pipeline time.

## 2026-06-14 Texture Direction Contract Correction

User clarification:

- The texture inversion is not solved by following raw renderer coordinate wraps. The visible page-turn direction is the user-facing truth.
- The failure signature already logged on device was `dir=next` with positive horizontal texture offsets during maps/frontmatter transitions, for example `x=750 delta=-1395 dir=next`. That must be treated as the bug, not as expected boundary behavior.

Root cause:

- The eta63 helper contract accepted known-direction renderer wraps and intentionally used the bounded raw renderer delta for those wraps.
- That codified the observed phone failure: when Foliate/Android wrapped renderer coordinates at an area transition, `next` could produce positive `x`, making the paper texture move opposite the page turn.

Corrected contract:

- If `pageTurnDirection` is `next`, the surface texture offset must stay left/negative for horizontal paged mode, even if the renderer position delta is negative because of an area/spine wrap.
- If `pageTurnDirection` is `previous`, the surface texture offset must stay right/positive for horizontal paged mode, even if the renderer position delta is positive because of an area/spine wrap.
- Directionless large renderer jumps still neutralize to zero; passive relocation noise must not invent a texture movement direction.
- Trace assertions must reject positive `next` offsets and negative `previous` offsets without exempting renderer-wrap samples.

Fresh red check:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
```

Result: failed before the helper change because `forward area boundary keeps known next texture direction` expected `{"x":-698,"y":0}` but observed `{"x":698,"y":0}`.

Focused green checks:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node --check tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSyncsSurfaceTextureWithPaginatorScrollDrags" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessTextureFrontmatterTransitionValidatesTracePayloadDirection"
```

Results: passed on 2026-06-14. The regenerated Hobbit frontmatter trace reported `badNext=0` and `badPrevious=0` when scanned for directed texture-offset inversions.

Release status:

- This is a source-level correction only. It has not been compiled into a release APK and has not been device-validated.
- Do not publish a release for this texture fix alone unless it is bundled with another major reader behavior fix.

## 2026-06-14 Chapter-Local Komikku Rail Ownership

User clarification:

- The right-side Komikku rail should not behave like a whole-book mobile scrollbar.
- The rail is a current-chapter navigator. Its previous/next arrows and slider only make sense if the rail is scoped to the current chapter/page set.
- This does not change the organic book-surface page number preference. The visible page number should still look printed/layered into the book surface, not like a Compose/mobile overlay.

Komikku source behavior used:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt`: chapter navigator takes current page, current page text, total pages, and page-index changes for the current chapter.
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`: app bars own the chapter navigator as reader chrome, not as engine/WebView content.

Navic implication:

- `ReaderLocator` now carries chapter-local fields independently from whole-book progress: `chapterProgress`, `chapterPageIndex`, and `chapterPageCount`.
- `navic-reader.js` now posts `chapterProgress`, `chapterPageIndex`, and `chapterPageCount` with `locationChanged`.
- `ReaderControllerState` now includes `ReaderChapterProgressState`, fed by engine relocation events.
- `ReaderScreen` now wires the Komikku rail through `onGoToChapterPage(pageIndex)` and `ReaderCoordinator.navigateToChapterPage(pageIndex)`.
- `FoliateEpubEngineAdapter` translates chapter rail seeks into `ReaderBridgeCommand.GoToChapterProgress(href, progress)`, not `GoToProgress`.
- `navic-reader.js` resolves the chapter `href` to a Foliate section index and calls `renderer.goTo({ index, anchor: fraction })`. Do not use `view.goTo({ href, fraction })`; Foliate treats any object with `fraction` as a whole-book fraction target and ignores `href`.
- `ReaderEngineWebViewHost.android.kt` logs `goToChapterProgress(...)` so adb traces can distinguish chapter rail seeks from full-book progress seeks.
- The active source guard rejects reusing `locator?.pageIndex`, `locator?.pageCount`, or `onGoToProgress(...)` inside `KomikkuReaderAppBars`.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeChapterLocalPagePositionForKomikkuRail" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesChapterLocalRailSeekAsFoliateChapterProgressCommand" --tests "paige.navic.reader.ReaderControllerTest.engineRelocationFeedsChapterLocalProgressForKomikkuRail" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuChapterNavigatorUsesChapterLocalControllerProgressInsteadOfBookProgress"
```

Result: failed before production changes because `ReaderLocator` had no chapter-local fields, `ReaderBridgeCommand.GoToChapterProgress` did not exist, `ReaderControllerState.chapterProgress` did not exist, and the active `ReaderScreen` still converted rail page indexes into global book progress.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodeChapterLocalPagePositionForKomikkuRail" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesChapterLocalRailSeekAsFoliateChapterProgressCommand" --tests "paige.navic.reader.ReaderControllerTest.engineRelocationFeedsChapterLocalProgressForKomikkuRail" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuChapterNavigatorUsesChapterLocalControllerProgressInsteadOfBookProgress" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderBridgeExposesProgressSeekCommand"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

Results: passed on 2026-06-14.

Release status:

- This is a source-level controller/chrome correction only. It has not been compiled into a release APK and has not been device-validated.
- Do not publish a release for this rail ownership fix alone unless it is bundled with enough major reader behavior fixes to justify the release pipeline.

## 2026-06-14 Native Short-Tap / Long-Press Ownership Audit

User clarification:

- Normal reader taps should be handled by the native container detector.
- WebView content should receive only deliberate long-press interaction, not ordinary short taps.

Current source audit:

- `KomikkuReaderNativeViewerContainer.onInterceptTouchEvent(...)` treats the final `ACTION_UP` as native-owned when the stream is still a short-tap candidate and no long tap was confirmed.
- `KomikkuGestureDetectorWithLongTap.onLongTapConfirmed(...)` marks `nativeTapLongConfirmed = true` and provides long-press haptic feedback.
- `KomikkuReaderNativeViewerContainer.dispatchTouchEvent(...)` still sends the stream to the child first, then runs swipe/tap detection, but returns `handled || nativeShortTapIntercepted` so confirmed short taps remain native-owned.
- `navic-reader.js` keeps `claimReaderInteractiveContentTouch(...)` inert when `nativeTapZones === true`, so JS does not post early link/image touch claims that would suppress native short-tap behavior.

Verification:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent"
```

Result: passed on 2026-06-14.

Runtime risk:

- This is source-level evidence only. It does not prove an installed eta build has the behavior until a release is built and adb-validates short tap, long press, link, image, cover, and PDF interaction paths.

## 2026-06-15 Native Viewer Action Ownership After Content Claims

User clarification:

- Native reader taps are the source of truth for normal menu/page actions.
- WebView content interaction metadata must not behave like an old delayed center-tap suppressor that swallows the next native action.
- If content interaction remains useful, it can be recorded as metadata, but controller movement and menu actions must still execute through the Komikku viewer-action boundary.

Navic implication:

- `ReaderController.onEngineEvent(ContentActionClaimed(...))` still records `lastContentActionClaim`.
- `ReaderController.onViewerAction(...)` now clears any stale content claim while continuing to execute the native viewer action.
- Menu actions toggle the controller-owned menu immediately even if a prior link/image content claim was reported.
- Page-turn and scroll actions still dispatch through `ReaderEngineCommand` when a prior content claim exists.
- This keeps short-tap ownership in `KomikkuReaderNativeViewerContainer` instead of letting WebView bridge messages own subsequent native input.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsDoNotOwnNativeViewerActions" --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsKeepMetadataInControllerState" --tests "paige.navic.reader.ReaderCoordinatorTest.bridgeEventsFlowFromAdapterIntoControllerWithoutBridgeOwningMenu"
```

Result: failed before production changes because `ReaderController.onViewerAction(...)` cleared `lastContentActionClaim` by returning early, so the first menu action after a content claim was swallowed and did not toggle the menu.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsDoNotOwnNativeViewerActions" --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsKeepMetadataInControllerState" --tests "paige.navic.reader.ReaderCoordinatorTest.bridgeEventsFlowFromAdapterIntoControllerWithoutBridgeOwningMenu"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Results: passed on 2026-06-15.

Release status:

- This is a source-level controller ownership correction only. It has not been compiled into a release APK and has not been device-validated.
- Do not publish a release for this fix alone unless it is bundled with enough major reader behavior fixes to justify the release pipeline.

## 2026-06-15 Native Frame Contract Alignment And Cover Swipe Drift

User clarification:

- The active objective remains the Komikku-derived reader backbone, not another round of WebView-host micro-fixes.
- Normal reader input must stay owned by the native frame/controller boundary.
- A release candidate should wait until enough meaningful reader behavior has changed to justify the pipeline; tonight is source work only.

Root cause:

- Several Android host contract tests still asserted the pre-backbone design where `ReaderEngineWebViewHost.android.kt` owned `ReaderSurfaceHost`, native tap arbitration, and shell-cover input.
- That was stale after the Komikku backbone moved input, cover, and overlay ownership into `KomikkuReaderNativeFrameHost.android.kt`.
- While retargeting those contracts, one real native-frame mismatch surfaced: cover swipes reimplemented a strict horizontal-dominance check instead of using the shared `readerShellCoverSwipeAction(...)` behavior, so natural vertical drift could prevent cover dragging.

Navic implication:

- `ReaderRuntimeImageLinkTest`, `ReaderRuntimeShellProgressTest`, and `ReaderRuntimeSettingsBridgeTest` now protect the current split:
  - `KomikkuReaderNativeFrameHost.android.kt` owns native short taps, shell cover view, cover swipe, tap-zone overlay, and viewer actions.
  - `ReaderScreen.kt` wires the native frame and Komikku dialog/settings surface.
  - `ReaderEngineWebViewHost.android.kt` stays renderer-only and forwards Foliate bridge events into the engine adapter.
- `KomikkuReaderNativeViewerContainer.dispatchHorizontalSwipeViewerAction(...)` now uses `readerShellCoverSwipeAction(deltaX, deltaY, touchSlopPx)` so cover-only drags tolerate vertical drift.
- `KomikkuReaderNativeViewerContainer` now logs one `Reader shell cover drag candidate` message when cover drag crosses touch slop, before swipe dispatch, so adb can show why cover dragging does or does not dispatch.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
```

Result: failed before the native-frame change because the retargeted cover-drag contracts expected `readerShellCoverSwipeAction(...)` and drag diagnostics inside the native frame, but the native frame still used a local `absoluteX <= touchSlopPx || absoluteX <= absoluteY` gate and had no cover-drag log.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Results: passed on 2026-06-15.

Release status:

- This is source-level contract alignment plus a native cover-swipe drift correction. It has not been compiled into a release APK and has not been device-validated.
- Do not publish a release for this slice alone. Bundle it into the next morning release candidate only after finishing enough reader-facing fixes to justify the pipeline time.

## 2026-06-15 Overnight Settings And Input Restoration

User direction:

- Continue source work overnight.
- Do not publish a GitHub release candidate tonight.
- In the morning, compile one release candidate and run the full adb validation matrix against the connected device.

Root cause:

- The active Komikku backbone had correctly removed the old docked `ReaderOptionsPanel.kt`, but several runtime contract tests still asserted that deleted surface.
- The active `ReaderScreen.kt` also lost useful app-boundary behavior during the reset:
  - reader defaults were coming from `defaultReaderSettings()` instead of `PreferenceManager`;
  - per-book settings scope was not wired through the active dialog;
  - General/PDF/filter settings were placeholder text lines instead of real controls;
  - volume-key page turns were no longer handled by the active reader root.

Navic implication:

- `ReaderScreen.kt` now initializes the controller with `PreferenceManager.readerDefaultSettings()` or `readerSettingsForBook(reader.bookId)`, depending on the selected scope.
- Reader settings changes are normalized, persisted as either global defaults or book-scoped overrides, and then routed through `ReaderCoordinator.applySettings(...)`.
- The active Komikku dialog now exposes real controls for:
  - settings scope: Global / For this book / Reset book;
  - reading mode, direction, tap zones, smaller tap zones, and visible tap zones;
  - font family, font source, font size, line height, paragraph spacing, margins;
  - theme, rotation, fullscreen, keep screen on, volume-key page turns;
  - PDF/Image: page fit, crop borders, page gap;
  - Custom filter: dim overlay and publisher styles.
- Volume Up / Volume Down page turns are handled at the `KomikkuReaderRoot` focus boundary, not inside the WebView renderer.
- `ReaderRuntimeCommonChromeTest` and `ReaderRuntimeNavigationFlowTest` now enforce the active Komikku backbone instead of the vaulted `ReaderOptionsPanel.kt`.
- Readaloud runtime capability tests now verify that the runtime/controller primitives remain available, while the legacy readaloud host stays out of active `ReaderScreen.kt` until it can be mounted through the new controller adapter.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
```

Result: failed before production changes because the active reader had no preference-backed defaults, no active font/source/dim/orientation/direction/PDF controls, no volume-key page-turn handler, and no per-book settings scope in the Komikku dialog.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost
```

Results: passed on 2026-06-15.

Release status:

- No release APK was built or published for this overnight source slice.
- Morning candidate should include this slice plus any additional high-priority reader fixes completed overnight, then run the adb matrix against EPUB cover, EPUB text, image/link interaction, dialog controls, texture movement, PDF navigation, and reader resume.

## 2026-06-15 Overnight Native Drag Ownership And Harness Parity

User direction:

- Continue source work overnight.
- Keep the release pipeline idle until there is a meaningful morning release candidate.
- Focus on the Komikku-derived backbone, especially native input ownership, instead of another WebView micro-fix cycle.

Root cause under investigation:

- Host browser probes can paginate the real Hobbit EPUB close to the phone layout when launched with a near-device viewport, but they still do not reproduce the phone-only texture inversion during the sequential maps/frontmatter transition.
- The existing deep texture probe jumps by search to Author's Note, so it is useful for page-count and rendering sanity but not sufficient proof for the observed sequential transition bug.
- The Android native frame still let readable EPUB/PDF horizontal drags fall through to the WebView/Foliate path after the Komikku reset. That violates the current reader contract: native frame/controller owns normal short taps and drags, while WebView content interaction should be explicit content metadata or long-press behavior, not the main reader gesture owner.

Navic implication:

- `tools/reader-harness/src/run-reader-harness.mjs` now accepts viewport overrides:
  - `--viewport-width`
  - `--viewport-height`
  - `--device-scale-factor`
- The harness now accepts both `--mode epub-frontmatter` and positional `epub-frontmatter`; before this correction, positional mode silently ran default `smoke`, which could create false confidence.
- The real EPUB harness was run against the local untracked fixture `tmp/reader-live/served-input.epub` at Android-like dimensions. At `500x960` with device scale factor `3`, the fixture paginated to 409 pages, close to the phone's reported 411 pages.
- `readerNativeReaderSwipeAction(...)` now defines the readable-page native swipe contract:
  - readable pages require horizontal dominance;
  - shell cover keeps the more permissive `readerShellCoverSwipeAction(...)` drift tolerance.
- `KomikkuReaderNativeViewerContainer` now intercepts horizontal readable-page drags above the WebView after touch slop and dispatches the viewer action through the native controller boundary.
- This makes the top manager own EPUB/PDF readable drags again instead of leaving readable-page swipes to Foliate/WebView.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessCanRunTextureProbesAtAndroidViewportParity"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessSupportsPositionalModeArgument"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.nativeReaderSwipeActionRequiresHorizontalDominanceOutsideShellCover"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsReadableHorizontalDragsAboveWebView"
```

Results: failed before the changes because the harness viewport was fixed, positional harness mode was ignored, readable-page swipe semantics did not exist in common code, and the native frame only dispatched shell-cover swipes.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessCanRunTextureProbesAtAndroidViewportParity"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessSupportsPositionalModeArgument"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.nativeReaderSwipeActionRequiresHorizontalDominanceOutsideShellCover"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsReadableHorizontalDragsAboveWebView"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
```

Results: passed on 2026-06-15.

Manual harness observations:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-frontmatter --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results:

- `epub-frontmatter` passed with 409 pages at the near-device viewport.
- `epub-texture-frontmatter-transition` passed at the same viewport, but this is not enough to close the user's phone report because the probe still does not prove the Android native-frame event path that produced the observed sequential maps-to-Author's-Note inversion.
- `epub-full-traversal` passed across 409 pages at the same viewport, with progress checkpoints from page 1 through page 401 before completion. This gives browser-path coverage for monotonic visible page labels, no consecutive duplicate locations, and cover suppression, but still does not replace adb validation of the native Android input frame.
- EPUB-only near-device matrix also passed at the same viewport:
  - `epub-page-boundary`
  - `epub-shell-cover`
  - `epub-external-shell-cover`
  - `epub-native-tap-zone-open`
  - `css-smoke`
  - `epub-texture-scroll`
  - `epub-texture-page-turns`
- `epub-shell-cover` specifically reported: native shell visible at page 0, first next hides the shell without advancing content, second next advances to page 1, previous from content page 0 restores the native shell. This is the desired browser/controller behavior; morning adb must verify the Android native cover and native input frame match it.

Release status:

- No release APK was built or published for this source slice.
- Morning release candidate should validate whether native readable-page drag ownership fixes:
  - center/menu tap reliability;
  - cover and normal-page drag behavior;
  - texture direction during sequential frontmatter/page-area transitions;
  - PDF page navigation under the same native controller path.

## 2026-06-15 Overnight Native Input Diagnostics For Morning ADB

User direction:

- Morning validation must not be blind. The release candidate needs useful logging before adb checks start.
- The adb checks must follow the active Komikku native-frame implementation, not the old `ReaderSurfaceHost` log vocabulary.

Root cause:

- `scripts/adb-reader-smoke.ps1` still recognized mostly legacy `Reader surface ...` diagnostics.
- The active `KomikkuReaderNativeFrameHost.android.kt` only logged shell-cover drag candidates, so adb could not clearly tell whether a tap/swipe was owned by the native frame, the shell cover, or the WebView.

Navic implication:

- `KomikkuReaderNativeViewerContainer` now logs:
  - `Reader native tap action=...`
  - `Reader native long tap ...`
  - `Reader native swipe action=...`
  - `Reader shell cover swipe action=...`
  - `Reader shell cover command action=...`
- `scripts/adb-reader-smoke.ps1` now captures and summarizes the active native-frame diagnostics alongside the older surface logs.
- `-ValidateReaderTaps -RequireReaderTapAction` now accepts `Reader native tap action=` as a valid native tap proof.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics"
```

Result: failed before production changes because the native frame did not emit the required tap/swipe diagnostics and the adb script did not recognize them.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsReadableHorizontalDragsAboveWebView" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost
```

Results: passed on 2026-06-15.

Release status:

- No release APK was built or published for this source slice.
- Morning adb artifacts should include `reader-touch-diagnostics.log` and `reader-diagnostics-summary.txt` so the user can see whether failures are native-frame input, WebView content, or controller dispatch failures.

## 2026-06-15 Morning ADB Matrix Preparation

User direction:

- After compiling the next release candidate, validation should be repeatable and visible rather than ad hoc.
- The matrix must preserve the already-open reader state between checks; relaunching into the library invalidates reader interaction tests.

Navic implication:

- Added `scripts/adb-reader-komikku-matrix.ps1`, a wrapper around `scripts/adb-reader-smoke.ps1`.
- The matrix captures named artifact folders under `captures/reader-komikku-matrix/<timestamp>` unless an `-ArtifactRoot` is provided.
- The default matrix includes:
  - `baseline-current-reader`
  - `center-tap-toggle`
  - `edge-tap-next`
  - `edge-tap-previous`
  - `drag-next`
  - `drag-previous`
- Optional `-IncludeCoverChecks` adds `cover-drag-next` with shell-cover swipe and command validation.
- `scripts/adb-reader-smoke.ps1` now supports `-RequireNativeSwipeAction`, which fails if a drag never reaches `Reader native swipe action=...`.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Result: failed before the script existed.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

PowerShell parser check:

```powershell
$files = @('scripts\adb-reader-smoke.ps1','scripts\adb-reader-komikku-matrix.ps1')
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($file, [ref] $tokens, [ref] $errors) | Out-Null
  if ($errors.Count -gt 0) { throw $errors[0].Message }
}
```

Results: passed on 2026-06-15.

Release status:

- No release APK was built or published for this script slice.
- Morning use after installing/opening the release candidate:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch
```

- If the reader is positioned on the native cover and cover checks are desired:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch -IncludeCoverChecks
```

## 2026-06-15 Overnight Komikku Side Rail Constraint

User direction:

- Keep source work focused on the Komikku reader backbone.
- Do not publish a GitHub release candidate for isolated minor visual corrections.
- The visible reader progress rail should stop looking like a full-height mobile slider and move closer to Komikku's centered overlay behavior.

Root cause:

- Navic had copied the vertical `ChapterNavigator` shape but mounted it inside a different overlay slot than Komikku.
- The active `KomikkuChapterNavigatorVertical` still called `fillMaxHeight()` with no constraint, so the rail could occupy the whole middle column and visually collide with the top and bottom chrome areas.

Navic implication:

- `ReaderScreen.kt` now keeps `KomikkuChapterNavigatorVertical` but mounts the left/right vertical rail inside a centered `Box`.
- The visible rail is constrained by `KomikkuReaderVerticalRailHeightFraction = 0.68f`.
- This preserves the Komikku source component while adapting the mount point to Navic's different overlay stack.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: failed before production changes because the active reader had no named vertical rail height fraction, no centered side-rail mount, and no constrained rail modifier.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: passed on 2026-06-15. The shell tool stream closed before Gradle returned, but the still-running wrapper process completed and `composeApp/build/test-results/testAndroidHostTest/TEST-paige.navic.reader.ReaderRuntimeCommonChromeTest.xml` reported one test, zero failures.

Release status:

- No release APK was built or published for this isolated layout correction.
- Morning adb validation should still inspect the rail visually because this is source-contract coverage, not a device screenshot proof.

## 2026-06-15 Overnight Native Drag Diagnostic Split

User direction:

- Morning adb checks should clearly identify which reader layer owned an interaction.
- Continue source work without publishing a release for isolated diagnostics.

Root cause:

- `KomikkuReaderNativeViewerContainer` logged `Reader shell cover drag candidate` for every horizontal drag candidate, including ordinary EPUB/PDF readable pages.
- That made adb artifacts ambiguous: a normal-page drag could look like a shell-cover drag in `reader-touch-diagnostics.log`.

Navic implication:

- Native drag candidate logs now split by visible surface:
  - `Reader shell cover drag candidate ...` when the native cover view is visible.
  - `Reader native drag candidate ...` on normal readable pages.
- `scripts/adb-reader-smoke.ps1` captures both patterns and reports `readerNativeDragCandidate=` separately from `shellCoverDragCandidate=`.
- `Reader native swipe action=...` remains the actual dispatch proof for readable-page swipes; the candidate line is only pre-dispatch evidence.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics"
```

Result: failed before production changes because neither the Android host nor the adb smoke summary contained `Reader native drag candidate`.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

PowerShell parser check:

```powershell
$files = @('scripts\adb-reader-smoke.ps1','scripts\adb-reader-komikku-matrix.ps1')
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($file, [ref] $tokens, [ref] $errors) | Out-Null
  if ($errors.Count -gt 0) { throw $errors[0].Message }
}
```

Results: passed on 2026-06-15.

Release status:

- No release APK was built or published for this diagnostic-only slice.
- Morning validation should use the split fields to tell whether a drag failure is native-frame recognition, shell-cover-only behavior, or viewer-action dispatch.

## 2026-06-15 Overnight Dyx Typewriter Font Registration

User direction:

- Keep overnight source work focused on locally verifiable reader gaps.
- The `Dyx` font type that resembles classic typewriter text should be selectable and actually reach the EPUB runtime.
- Do not publish a release candidate for this isolated settings/runtime correction.

Root cause:

- The Komikku-port issue list mentioned `Dyx`, but the active reader only exposed sans, serif, bundled book serif, humanist, dyslexic, monospace, and publisher fonts.
- The repo currently has no Dyx/typewriter font asset under `composeApp/src/androidMain/assets/reader/fonts`.
- The WebView helper collapsed any `System` font stack containing `ui-monospace` into generic monospace, so a typewriter-style stack would not survive system-source normalization.

Navic implication:

- `ReaderTypewriterFontFamily` is now registered as `"American Typewriter", "Courier Prime", "Courier New", ui-monospace, monospace`.
- The family is included in `ReaderSupportedFontFamilies`, in-reader Komikku chip groups, Settings > Ebooks, and Settings search.
- The short reader label is `Dyx`; the Settings label is `Dyx typewriter`.
- `readerEffectiveFontFamily` now preserves the typewriter stack when the selected font source is `System`.
- This is a system-font stack, not a bundled Dyx font file. A real user-supplied Dyx font should go through the existing imported custom-font path unless a vetted font asset is added later.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepExpandedFontSources"
```

Result: failed before production changes at `ReaderSettingsDefaultsTest.kt:141` because `ReaderSupportedFontFamilies` did not include the typewriter/Dyx family.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepExpandedFontSources" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.androidReaderPackagesBundledFontSourcesForWebViewRendering"
```

Results: passed on 2026-06-15.

Release status:

- No release APK was built or published for this isolated font registration.
- Morning device validation should verify that selecting `Dyx` changes EPUB text and the organic page-number text on a normal content page.

## 2026-06-15 Overnight Komikku Progress Slider Wrapper

User direction:

- Keep replacing knock-off reader behavior with Komikku-derived behavior.
- The reader progress rail should behave closer to Komikku instead of exposing raw Material mobile slider semantics.
- Do not publish a release candidate for isolated source-level rail work.

Root cause:

- Komikku's `ChapterNavigator` uses `tachiyomi.presentation.core.components.material.Slider`, an integer wrapper around Material3 `Slider`, and wires drag haptics through `MutableInteractionSource.collectIsDraggedAsState()`.
- Navic's port used raw Material3 `Slider` directly with `Float` values and `toInt()` flooring.
- That made rail movement less faithful to Komikku's page-based model and skipped the haptic drag path.

Navic implication:

- `ReaderScreen.kt` now has a local `KomikkuChapterProgressSlider(...)` wrapper equivalent to Tachiyomi's integer slider behavior.
- Horizontal and vertical `KomikkuChapterNavigator` routes now pass `value = currentPage`, `valueRange = 1..totalPages`, and `onPageIndexChange(page - 1)`.
- Both navigator modes now create a `MutableInteractionSource`, observe `collectIsDraggedAsState()`, and perform `HapticFeedbackType.TextHandleMove` while dragging.
- This is still not a full visual clone of Tachiyomi's slider internals or Komikku's final reader rail appearance; morning device validation must inspect thumb/track proportions and color in the actual APK.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: failed before production changes at `ReaderRuntimeCommonChromeTest.kt:237` because Navic had no `KomikkuChapterProgressSlider`, no `MutableInteractionSource`, no drag haptic path, and the side rail used `Float`/`toInt()` slider handling.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: passed on 2026-06-15.

Release status:

- No release APK was built or published for this isolated rail behavior correction.
- Morning device validation should specifically check whether dragging the side rail now feels page-snapped and whether the visual rail still reads as a raw Material slider.

## 2026-06-15 Overnight Native Short-Tap Content Suppression

User direction:

- The Komikku-native reader surface owns ordinary short taps and drags.
- WebView content actions must not race the native menu/page-turn detector.
- Links/images should not be activated by the same ordinary short tap that the native reader uses for tap zones. Deliberate content interaction belongs to the long-press/explicit-content path, not the default tap-zone path.
- Do not publish a release candidate for this isolated source-level correction.

Root cause:

- Android already passed `nativeTapZones = true` into the Foliate runtime and disabled JS reader-wide tap-zone listeners.
- The WebView runtime still allowed ordinary content click handlers to run:
  - `attachLinkNavigation(...)` could navigate links from a normal click.
  - `attachSepiaImageOverlayToggle(...)` could toggle image sepia overlays from a normal click/touchend.
- CSS smoke only exercised the old WebView-owned fallback path, so local browser checks could pass while missing the Android-native ownership mode.
- The image handler was attached before link navigation, so a broad image/content guard could accidentally swallow plain text links before the link handler could classify them.

Navic implication:

- `navic-reader.js` now has `suppressReaderNativeTapZoneContentActivation(...)`.
- When `nativeTapZones === true`, ordinary link clicks are suppressed before href resolution/navigation.
- Ordinary image clicks/touchend events are suppressed before the sepia image overlay changes state, but only after proving the target is actual media. Plain text links continue to reach the link handler for suppression/classification rather than being mislabeled as `image-click`.
- CSS smoke now exercises both paths:
  - fallback/non-native content behavior still validates link styling, link navigation, image overlay toggles, and content-action metadata;
  - native-mode probes set `nativeTapZones: true` and require ordinary image/link short taps to emit `native-tap-zones:content-click-suppressed` without `link:navigate`, `image:sepia-overlay`, or `readerContentTapHandled` posts.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderSuppressesOrdinaryContentClicksWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
```

Results:

- Failed before production/harness changes because the runtime had no conditional media-only native suppression in the image handler.
- Failed before harness changes because CSS smoke had no `nativeTapZones: true` probe and no native-mode suppression assertions.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderSuppressesOrdinaryContentClicksWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
```

Results: passed on 2026-06-15.

Morning adb validation:

- Verify that center/edge taps on EPUB text pages still map to native menu/next/previous.
- Verify that tapping links/images no longer triggers WebView navigation or sepia-image toggle from the ordinary short-tap path.
- Verify that deliberate long press still allows content interaction or at least does not dispatch native tap-zone action.
- Verify that this change does not regress the user-visible sepia image toggle path expected for deliberate image interaction. If long-press is not enough in practice, the next slice should add an explicit content-interaction mode rather than re-enabling ordinary WebView clicks.

Release status:

- No release APK was built or published for this isolated source correction.

## 2026-06-15 Overnight ADB Texture Direction Gate

User direction:

- The paper texture issue must be diagnosed by adb instead of by visual guessing.
- The known failure is not "texture logs are missing"; it is texture movement inverting during page/frontmatter transitions.
- Do not publish a release candidate for validation-only script work.

Root cause:

- Browser/controller texture harnesses already check that next/forward movement counter-moves the paper texture left and previous/backward movement counter-moves it right.
- `scripts/adb-reader-smoke.ps1` only required `surface-texture-scroll` fields such as `pos=`, `base=`, `delta=`, `dir=`, `page=`, and `href=`.
- That meant Android could still pass the morning matrix while moving the texture in the wrong direction.

Navic implication:

- `scripts/adb-reader-smoke.ps1` now supports `-RequireTextureDirection next|previous`.
- When direction is required, the script parses `surface-texture-scroll` samples for matching `dir=...`, selects the dominant moved axis, and fails if:
  - no moved texture sample was captured for that direction; or
  - `next` moved positive/right/down instead of negative/left/up; or
  - `previous` moved negative/left/up instead of positive/right/down.
- The script writes `reader-texture-direction-validation.txt` and adds `textureDirectionSamples=` plus `wrongTextureDirection=` to `reader-diagnostics-summary.txt`.
- `scripts/adb-reader-komikku-matrix.ps1` now passes:
  - `-RequireTextureDirection "next"` for `edge-tap-next` and `drag-next`;
  - `-RequireTextureDirection "previous"` for `edge-tap-previous` and `drag-previous`.
- `scripts/adb-reader-komikku-matrix.ps1` also runs end-of-matrix texture walks:
  - `texture-next-walk`: twelve right-edge taps with `-RequireTextureDirection "next"`;
  - `texture-previous-walk`: six left-edge taps with `-RequireTextureDirection "previous"`.
- The walk steps exist because the reported inversion appears after several frontmatter/page-area transitions, not necessarily on the first page turn.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Result: failed before script changes because the ADB smoke/matrix scripts did not expose direction-specific texture validation.

Focused green checks:

```powershell
$files = @('scripts\adb-reader-smoke.ps1','scripts\adb-reader-komikku-matrix.ps1')
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($file, [ref] $tokens, [ref] $errors) | Out-Null
  if ($errors.Count -gt 0) { throw $errors[0].Message }
}
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-page-turns --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results: passed on 2026-06-15.

Local baseline note:

- The combined browser harness passed 15 checks on 2026-06-15:
  - EPUB trace/frontmatter/page-boundary/shell-cover/external-shell-cover/native-tap-zone/css;
  - texture offset/scroll/page-turn/frontmatter-transition;
  - full EPUB traversal with strictly advancing labels;
  - PDF smoke, fast sequential turns, and PDF image/settings.
- This does not prove Android phone behavior. It means the next meaningful failure evidence should come from the native ADB matrix or from an Android-only rendering/input divergence.

Morning adb validation:

- After installing/opening the release candidate on an EPUB page, run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch
```

- Texture transition failures should now point to `reader-texture-direction-validation.txt` under the failed matrix step.

Release status:

- No release APK was built or published for this validation-only slice.

## 2026-06-15 Overnight Link-Jump Drag Harness Gate

User direction:

- Keep source work on the Komikku reader backbone without publishing another release for isolated local changes.
- The reported bug "dragging does not work after using a hyperlink/chapter jump" must be reproducible locally where possible before another APK cycle.
- Browser/local validation cannot replace Android ADB validation, but it should separate Foliate/WebView renderer failures from Android-native input-owner failures.

Root cause:

- `phase1-stabilization` already covered direct bridge jumps and frontmatter texture transitions, including `goToHref`/`goToCfi` followed by drags.
- It did not explicitly exercise Navic's in-document link handler (`link:navigate`) followed by a drag.
- The first harness implementation failed on the real Hobbit fixture because Foliate sections expose `id` rather than `href`, and a raw `OEBPS/Text/3.html` probe link was resolved relative to `OEBPS/Text/1.html` as `OEBPS/Text/OEBPS/Text/3.html`.

Navic implication:

- `tools/reader-harness/src/run-reader-harness.mjs` now has `epub-link-jump-drag`.
- The mode opens the real EPUB fixture, hides the shell cover, injects a same-folder EPUB link into the current content document, clicks it so the real `attachLinkNavigation(...)` path emits `link:navigate`, then performs a touch drag and requires the visible page index to advance.
- The probe converts same-folder section identifiers into a real EPUB-relative link such as `3.html`, matching how in-book links should resolve.
- `phase1-stabilization` now includes `epub-link-jump-drag`, so the overnight/local baseline fails if link relocation leaves the renderer in a stale non-draggable state.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCoversLinkRelocationBeforeDrag"
```

Result: failed before harness changes because `epub-link-jump-drag`, `linkJumpDrag`, and the specific "Expected link-jump drag to advance" failure gate did not exist.

Focused green checks:

```powershell
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-link-jump-drag --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCoversLinkRelocationBeforeDrag"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results: passed on 2026-06-15.

Local baseline note:

- The combined browser harness now passes 16 checks, adding `epub-link-jump-drag` to the previous 15-check local baseline.
- This proves the browser/Foliate path can navigate through an in-document EPUB link and then drag from the relocated state.
- It does not prove Android native input ownership after a real UI chapter/list action. If the phone still fails after a link/chapter jump, the next investigation should focus on the native frame/action routing or release/runtime divergence, not the browser renderer alone.

Release status:

- No release APK was built or published for this harness-only slice.

## 2026-06-15 Overnight Native Long-Press Content Path

User direction:

- Ordinary taps and drags should belong to the native Komikku-style reader container.
- Interactable EPUB content should not trigger the menu or page turn from the same ordinary short tap.
- Content interaction still needs a deliberate path; the user specifically called out long-press as the likely boundary.
- Do not publish a release candidate for this isolated source-level correction.

Root cause:

- The previous native short-tap suppression slice correctly stopped ordinary link/image clicks from racing the native reader tap zones.
- That made the default Android-native mode safer, but it did not provide a replacement deliberate content activation path for links or images.
- The CSS smoke harness proved fallback click behavior and native short-tap suppression, but did not require native-mode long-press activation.

Navic implication:

- `navic-reader.js` now handles `contextmenu` as the browser/WebView long-press signal when `nativeTapZones === true`.
- Text-link long press routes through `handleNativeTapZoneContentLongPress(...)` and then the same EPUB link navigation path, emitting `link:navigate`.
- Image/media long press routes through the same deliberate content path and toggles the sepia image overlay, emitting `image:sepia-overlay`.
- Ordinary native-mode clicks/touchend events are still suppressed and must not emit `link:navigate`, `image:sepia-overlay`, or Android content-action posts.
- This deliberately uses the platform `contextmenu`/long-press signal instead of adding a product-side timeout.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsContextMenuContentActionsWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
```

Result: failed before production/harness changes because the runtime had no `handleNativeTapZoneContentLongPress(...)`, no `contextmenu` handlers for native-mode link/image activation, and CSS smoke did not assert native-mode long-press behavior.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsContextMenuContentActionsWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results: passed on 2026-06-15.

Morning adb validation:

- Verify ordinary short taps on text, links, and images still trigger only the native reader tap-zone behavior.
- Verify long-press on an image toggles the sepia image overlay without opening the reader menu.
- Verify long-press on a text link navigates/activates the link without opening the reader menu.
- If Android WebView does not emit `contextmenu` for one of these gestures on-device, the next slice should bridge Android long-tap coordinates into the runtime explicitly rather than re-enabling ordinary click activation.

Release status:

- No release APK was built or published for this source-level correction.

## 2026-06-15 Overnight ADB Native Long-Press Gate

User direction:

- Morning validation needs a repeatable ADB array, not only screenshots or manual impressions.
- Ordinary short taps remain native Komikku tap-zone input.
- Deliberate content interaction should be validated as long press before deciding whether Android needs an explicit coordinate bridge.
- Do not publish a release candidate for this validation-only script slice.

Root cause:

- `KomikkuReaderNativeFrameHost.android.kt` already logs `Reader native long tap`.
- The WebView runtime already has the source-level long-press/content path from the previous slice.
- `scripts/adb-reader-smoke.ps1` could capture the log text only incidentally; it had no long-press gesture input, no `readerNativeLongTap=` summary field, and no gate that fails when the native long tap is missing.
- `scripts/adb-reader-komikku-matrix.ps1` therefore could not prove the morning APK recognizes deliberate long press separately from center short tap.

Navic implication:

- `adb-reader-smoke.ps1` now accepts:
  - `-LongPress x,y,durationMs,waitMs`
  - `-LongPressFraction xFraction,yFraction,durationMs,waitMs`
  - `-RequireNativeLongTap`
- Fractional long press is converted to Android `input swipe x y x y durationMs`, which is the ADB-supported long-press gesture.
- Reader diagnostics summary now writes `readerNativeLongTap=...`.
- `adb-reader-smoke.ps1` fails with `no native reader long tap was captured` when `-RequireNativeLongTap` is set and no `Reader native long tap` log exists.
- `adb-reader-komikku-matrix.ps1` now has a `native-long-press-center` step using `-LongPressFraction @("0.50,0.50,950,900")`.
- That matrix step also passes `-RequireNoReaderCenterDispatch` so a long press cannot silently pass by becoming the short-tap menu action.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Result: failed before script changes because long-press gesture input, `RequireNativeLongTap`, `readerNativeLongTap=`, and the `native-long-press-center` matrix step did not exist.

Focused green checks:

```powershell
$files = @('scripts\adb-reader-smoke.ps1','scripts\adb-reader-komikku-matrix.ps1')
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($file, [ref] $tokens, [ref] $errors) | Out-Null
  if ($errors.Count -gt 0) { throw $errors[0].Message }
}
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Results: passed on 2026-06-15.

Morning adb validation:

- After installing/opening the release candidate on an EPUB page, run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch
```

- Inspect `native-long-press-center/reader-diagnostics-summary.txt` for `readerNativeLongTap=True`.
- If that step passes but image/link long press still does not activate content on-device, the next slice should bridge Android long-tap coordinates into `handleNativeTapZoneContentLongPress(...)` explicitly.

Release status:

- No release APK was built or published for this validation-only slice.

## 2026-06-15 Overnight Explicit Native Coordinate Long-Press Path

User direction:

- Ordinary taps and drags belong to the native Komikku-style reader container.
- EPUB/PDF content interaction must be deliberate and must not rely on ordinary WebView short clicks.
- The previous source-level `contextmenu` path was useful, but Android-native long press must enter the same controller/engine/bridge path as other reader actions instead of being a log-only side effect.
- Do not publish a release candidate for this isolated source correction.

Root cause:

- `KomikkuReaderNativeFrameHost.android.kt` detected native long taps and logged `Reader native long tap`, but the event stopped there.
- `ReaderViewerAction`, `ReaderController`, `ReaderEngineCommand`, and `ReaderBridgeCommand` had no typed content-long-press command.
- Therefore Android content activation still depended on the WebView independently emitting `contextmenu`, which is not the Komikku-style controller ownership model.

Navic implication:

- `ReaderViewerAction.ContentLongPressAt(x, y, viewWidth, viewHeight)` now represents deliberate native content activation.
- `ReaderController` forwards that action as `ReaderEngineCommand.ContentLongPressAt(...)` without toggling the menu.
- If the controller-owned native shell cover is visible, the same action is ignored so the hidden EPUB cover/runtime cannot steal shell-cover input.
- `FoliateWebViewEngineAdapter` maps the engine command to `ReaderBridgeCommand.ContentLongPressAt(...)`.
- `ReaderBridgeCommand.ContentLongPressAt(...)` serializes as:

```json
{"type":"contentLongPressAt","x":250.0,"y":500.0,"viewWidth":500.0,"viewHeight":1000.0}
```

- `KomikkuReaderNativeFrameHost.android.kt` now exposes `onContentLongPress(x, y, width, height)` from the native long-tap detector and `ReaderScreen` routes it back through `ReaderViewerAction.ContentLongPressAt(...)`.
- `navic-reader.js` now handles `contentLongPressAt` by converting native root coordinates to runtime CSS coordinates, hit-testing loaded content documents, and deliberately activating either:
  - image/media behavior through `toggleSepiaImageOverlayFromEvent(...)`; or
  - text-link navigation through `activateReaderLinkFromEvent(...)`.
- The runtime still suppresses ordinary native-mode short clicks and touchend activation.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.viewerLongPressContentActionsAreControllerOwnedAndForwardedAsEngineCapability" --tests "paige.navic.reader.ReaderControllerTest.viewerLongPressContentActionsAreIgnoredWhileNativeShellCoverOwnsTheSurface" --tests "paige.navic.reader.ReaderCoordinatorTest.viewerLongPressContentActionDispatchesThroughCurrentEngineAdapter" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedContentLongPressAsRendererContentCommand" --tests "paige.navic.reader.ReaderBridgeProtocolTest.contentLongPressCommandDispatchesNativeCoordinateIntent" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsNativeCoordinateLongPressContentActionsWhenNativeTapZonesOwnShortTaps"
```

Result: failed before production changes because `ContentLongPressAt` did not exist in the action, engine, bridge, native-frame callback, or runtime dispatch paths.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.viewerLongPressContentActionsAreControllerOwnedAndForwardedAsEngineCapability" --tests "paige.navic.reader.ReaderControllerTest.viewerLongPressContentActionsAreIgnoredWhileNativeShellCoverOwnsTheSurface" --tests "paige.navic.reader.ReaderCoordinatorTest.viewerLongPressContentActionDispatchesThroughCurrentEngineAdapter" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedContentLongPressAsRendererContentCommand" --tests "paige.navic.reader.ReaderBridgeProtocolTest.contentLongPressCommandDispatchesNativeCoordinateIntent" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsNativeCoordinateLongPressContentActionsWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
git diff --check
```

Results: passed on 2026-06-15.

Local baseline note:

- `phase1-stabilization` passed all 16 checks after this change.
- `css-smoke` now validates both WebView `contextmenu` long press and explicit native-coordinate `contentLongPressAt` activation for images and text links.
- This still does not prove Android WebView/device behavior until a release candidate is installed and the ADB matrix runs.

Morning adb validation:

- Compile/install one release candidate, then run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch
```

- Validate these specific behaviors manually after the matrix:
  - ordinary short taps on text, images, and links use native menu/previous/next behavior only;
  - long press on an image toggles the sepia image overlay without opening chrome;
  - long press on a text link activates navigation without opening chrome;
  - shell cover still ignores content-long-press commands and remains controller-owned;
  - texture direction checks still pass across frontmatter transitions.

Release status:

- No release APK was built or published for this source-level correction.

## 2026-06-15 Overnight Local Baseline Before Morning RC

User direction:

- Keep working overnight without publishing another GitHub release.
- In the morning, compile one release candidate and run the full adb validation matrix against the connected device.
- Do not substitute local/browser checks for phone validation, but do use them to avoid shipping known source/runtime regressions into the morning candidate.

Current branch state:

- Branch: `codex/komikku-reader-backbone-eta64`.
- Latest pushed commit at the start of this baseline: `1a3600ff Add native coordinate reader long press path`.
- Tracked source was clean before this documentation update. `releases/` and `tmp/` remain intentionally untracked and must not be staged.
- No release APK was built or published during this overnight pass.

Local verification run:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results:

- JavaScript syntax checks passed for the active reader runtime, helper module, harness runner, and trace assertions.
- The focused Android-host reader suite passed.
- `phase1-stabilization` passed all 16 browser/Foliate harness checks:
  - trace smoke;
  - EPUB frontmatter;
  - EPUB page boundary;
  - EPUB shell cover;
  - EPUB external shell cover;
  - EPUB native tap-zone open;
  - CSS/theme/image/link smoke;
  - EPUB link relocation followed by drag;
  - texture offset logic;
  - EPUB texture scroll;
  - EPUB texture page turns;
  - EPUB texture frontmatter transition;
  - full EPUB traversal;
  - PDF smoke;
  - PDF fast sequential turns;
  - PDF image/settings.
- Current local Hobbit fixture pagination at `500x960` / DPR `3` reported `505` pages in the harness. Treat this as the current browser baseline only; it is not a phone page-count promise.

What this proves:

- The source/runtime baseline is internally consistent enough to justify one morning RC build.
- The browser/Foliate path can traverse the real EPUB, suppress the EPUB cover in favor of shell cover semantics, keep page labels monotonic locally, apply sepia/theme/image/link behavior, validate texture movement in the local probes, and exercise PDF page navigation/settings.
- The controller/native-frame source contracts for Komikku ownership, long press, readable-page drag ownership, settings, rail, and bookmark/page-mark behavior pass host tests.

What this does not prove:

- Android WebView/native input behavior on the phone.
- Whether the release APK contains the same source as this branch.
- Whether cover drag works over the native cover image on-device.
- Whether edge taps work over image-heavy pages on-device.
- Whether texture movement still inverts during the phone-only maps/frontmatter transition.
- Whether PDF tap responsiveness and horizontal centering feel acceptable on the device.
- Whether `Dyx`, sepia, and settings changes are visually correct on the phone.

Morning release candidate gate:

1. Build exactly one release candidate from this branch unless the source has changed again overnight.
2. Install it on the connected phone and open the reader to a known EPUB.
3. Run the matrix without relaunching away from the already-open reader:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch
```

4. If the phone is positioned on the native cover and cover input must be checked in the same run, use:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch -IncludeCoverChecks
```

5. Inspect these artifacts first:
   - `reader-diagnostics-summary.txt`
   - `reader-touch-diagnostics.log`
   - `reader-texture-direction-validation.txt`
   - screenshots for `center-tap-toggle`, `edge-tap-next`, `edge-tap-previous`, `drag-next`, `drag-previous`, `native-long-press-center`, and cover checks when included.

Manual confirmation after the matrix:

- Native cover center tap toggles chrome without discarding the cover.
- Native cover drag advances from cover to the first readable page.
- Left edge from the first readable page returns to the native/synthetic shell cover, not to a blank suppressed EPUB cover.
- Text-page center tap opens and hides the menu consistently.
- Text-page left/right taps and swipes move exactly one logical step.
- Image-heavy/frontmatter pages respond to the same top-level edge zones as text pages.
- Long press on images toggles sepia treatment without opening chrome.
- Long press on text links navigates without opening chrome.
- Ordinary short taps on links/images do not trigger content navigation/tint or menu accidentally.
- Texture movement follows the page movement axis across Contents, maps, Author's Note, and normal chapter pages.
- The side rail is chapter-scoped, visually shorter than the full screen, and usable.
- The organic page number is the only visible page number and stays monotonic.
- PDF pages are horizontally centered and tap/drag movement is responsive.

Release rule:

- Do not publish a GitHub release unless this RC fixes at least one major acceptance failure and the adb matrix/manual checks support that claim.

## 2026-06-15 Overnight ADB Matrix Full-Diagnostics Mode

User direction:

- The morning run should perform the whole adb array of checks so remaining failures can be compared in one pass.
- One broken gesture must not hide whether the other gestures, texture checks, long-press path, PDF path, or cover checks improved or regressed.
- This is validation tooling only. It does not publish another release candidate.

Root cause:

- `scripts/adb-reader-komikku-matrix.ps1` was fail-fast only.
- That was useful for CI-style gating, but poor for the morning review because a first failed center tap or texture check would stop the script before collecting later screenshots/logs.

Navic implication:

- The matrix now accepts `-ContinueOnFailure`.
- Default behavior remains fail-fast.
- With `-ContinueOnFailure`, every named matrix step still runs and writes its own artifact folder.
- The matrix root now writes:
  - `reader-matrix-summary.csv`
  - `reader-matrix-failures.txt`
- If any step fails during a full-diagnostics run, the script still exits non-zero at the end with `Komikku reader matrix failed: <n> step(s).`

Morning command shape:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch -ContinueOnFailure
```

If the reader is on the native cover:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Result: failed before the script change because the matrix had no `-ContinueOnFailure`, no root `reader-matrix-summary.csv`, and no root `reader-matrix-failures.txt`.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Result: passed on 2026-06-15.

## 2026-06-15 Overnight ADB Matrix PDF And Cover Coverage

User direction:

- The morning validation should collect the whole practical adb array, not stop at the first EPUB gesture failure.
- PDF behavior is a known acceptance area, but the current script cannot safely navigate the app from the EPUB under test to an arbitrary PDF without relying on fragile library UI coordinates.
- Cover behavior needs its own named center-tap artifact because cover center tap and cover drag have failed independently.

Root cause:

- `adb-reader-komikku-matrix.ps1` previously had ordinary EPUB tap/drag/texture checks, native long press, and optional cover drag only.
- It had no `cover-center-tap-toggle` artifact.
- It had no PDF-specific path and `adb-reader-smoke.ps1` had no way to fail when the current reader state was not actually PDF-backed.

Navic implication:

- `adb-reader-smoke.ps1` now accepts `-RequirePdfDiagnostics`.
- Reader diagnostics summary now writes `pdfRuntimeDiagnostics=...`.
- `adb-reader-smoke.ps1` fails with `no PDF runtime diagnostics were captured` when `-RequirePdfDiagnostics` is requested and the captured logcat does not show the Foliate/PDF.js runtime path.
- `adb-reader-komikku-matrix.ps1` now accepts:
  - `-IncludePdfChecks`: append optional PDF checks to the current run.
  - `-OnlyPdfChecks`: skip EPUB texture/gesture steps and run only baseline plus PDF tap/drag checks.
- The cover matrix now includes `cover-center-tap-toggle` before `cover-drag-next` when `-IncludeCoverChecks` is supplied.

Morning command shape:

1. Open the known EPUB, preferably on the native cover if cover behavior must be included, then run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

2. Open a known PDF in the reader, then run the PDF-only matrix:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName "<version>" -NoLaunch -OnlyPdfChecks -ContinueOnFailure
```

Do not run `-IncludePdfChecks` against an EPUB state and interpret the expected PDF failures as app failures. It is available for cases where the current reader state is already PDF and the operator also wants the non-PDF generic input checks.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Result: failed before the script change because `-RequirePdfDiagnostics`, `pdfRuntimeDiagnostics=`, `-IncludePdfChecks`, the PDF matrix steps, and `cover-center-tap-toggle` did not exist.

Focused green checks:

```powershell
$files = @('scripts\adb-reader-smoke.ps1','scripts\adb-reader-komikku-matrix.ps1')
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $file), [ref]$tokens, [ref]$errors) | Out-Null
  if ($errors.Count -gt 0) { throw $errors[0].Message }
}
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.adbKomikkuReaderMatrixRunsNamedNativeFrameChecks"
```

Results: passed on 2026-06-15.

Release status:

- No release APK was built or published for this validation-tooling slice.

## 2026-06-15 Morning RC Command Register

This register is for the next connected-phone validation pass. It is not permission to publish tonight.

Current branch state:

- Branch: `codex/komikku-reader-backbone-eta64`.
- Current Android version before the morning RC bump: `versionCode = 398`, `versionName = "v1.0.11-eta65"` in `androidApp/build.gradle.kts`.
- Latest pushed source commit before the morning RC bump: `2d45a1f2 Expand reader adb validation matrix`.
- `releases/` and `tmp/` remain local/untracked and must not be staged.

Release candidate rule:

- Build exactly one Android release candidate in the morning, after confirming the phone is ready for ADB validation.
- Suggested next version if no other release has happened meanwhile: `v1.0.11-eta66`, `versionCode = 399`.
- Do not run a workflow-dispatch iOS build. Eta tags contain `-`, so `.github/workflows/build.yml` skips `build-ipa` automatically for a normal tag push.

Morning release sequence:

```powershell
# 1. Update androidApp/build.gradle.kts:
#    versionCode = 399
#    versionName = "v1.0.11-eta66"

.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta66
git add -- androidApp/build.gradle.kts
git commit -m "Bump Android reader candidate to eta66"
git tag v1.0.11-eta66
.\scripts\publish-github-release.ps1 -Tag v1.0.11-eta66 -Branch codex/komikku-reader-backbone-eta64 -Background
```

After GitHub publishes `Navic.apk`, install/update the phone, open the target EPUB, and run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName v1.0.11-eta66 -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```

Then open the target PDF and run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -ExpectedVersionName v1.0.11-eta66 -NoLaunch -OnlyPdfChecks -ContinueOnFailure
```

First artifacts to inspect:

- `reader-matrix-summary.csv`
- `reader-matrix-failures.txt`
- `baseline-current-reader/reader-diagnostics-summary.txt`
- `center-tap-toggle/reader-touch-diagnostics.log`
- `native-long-press-center/reader-touch-diagnostics.log`
- `texture-next-walk/reader-texture-direction-validation.txt`
- `texture-previous-walk/reader-texture-direction-validation.txt`
- `cover-center-tap-toggle/screen.png`
- `cover-drag-next/reader-touch-diagnostics.log`
- `pdf-baseline/reader-diagnostics-summary.txt`
- `pdf-drag-next/reader-touch-diagnostics.log`

## 2026-06-15 Overnight Komikku Settings Tab Header Slice

User direction:

- Continue working overnight without publishing a release candidate.
- Prioritize changes that move the reader away from the old docked/settings-page feel and toward the actual Komikku reader overlay structure.
- Do not spend a release pipeline on minor UI cleanup; leave the next APK for the morning RC gate.

Root cause:

- `KomikkuReaderSettingsDialog(...)` still hand-rolled the tab header as a `Row` of title-sized `Text` nodes.
- That made long labels such as `Reading mode` and `Custom filter` vulnerable to wrapping and made the settings surface feel like another Navic settings page instead of Komikku's compact tabbed dialog.

Navic implication:

- The dialog now routes the header through `KomikkuSettingsTabRow(...)`.
- `KomikkuSettingsTabRow(...)` uses Material3 `PrimaryTabRow` and `Tab`, matching the same component family used by Komikku's `TabbedDialog`.
- Tab labels use `MaterialTheme.typography.labelLarge`, `maxLines = 1`, and `TextOverflow.Ellipsis`.
- The settings dialog body no longer owns raw `tabs.forEachIndexed` text-tab markup.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs"
```

Result: failed before production changes because `KomikkuSettingsTabRow(...)` did not exist and the settings dialog hand-rolled `tabs.forEachIndexed`.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderShellUsesKomikkuEquivalentOverlayStack" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsUseKomikkuStyleChipGroups" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## Phone Validation: 2026-06-16 eta67 ADB Matrix

Device and build:

- Device: Samsung `SM_F966B`, ADB serial `RFCY80551LT`.
- Package: `darkaxt.navic`.
- Installed build: `versionName=v1.0.11-eta67`, `versionCode=400`.
- Focused activity during tests: `darkaxt.navic/paige.navic.androidApp.MainActivity`.
- Evidence directory: `captures/eta67-adb-manual/`.

Validation matrix:

| Area | Test | Evidence | Result | Verdict |
| --- | --- | --- | --- | --- |
| Baseline | Current reader state after user opened EPUB | `captures/eta67-adb-manual/baseline-current-reader` | Smoke script passed, confirmed `versionName=v1.0.11-eta67`, PID `19980`, and WebView devtools socket `@webview_devtools_remote_19980`. | PASS: correct candidate is installed and inspectable. |
| Menu tap | Center tap twice on current EPUB page | `captures/eta67-adb-manual/center-tap-toggle` | Smoke script passed with `-ValidateReaderTaps` and `-RequireReaderTapAction`; native reader tap actions were observed. | PASS at smoke level: center tap is routed through the native reader surface on this page. |
| Edge navigation | Right-edge tap with required texture diagnostics | `captures/eta67-adb-manual/edge-tap-next` | Native tap was observed as `Reader native tap action=RIGHT x=1771.0 y=1092.0 width=1968 height=2184`, but `reader-texture-diagnostics.log` was empty and the smoke parser failed because there were no texture samples. | PARTIAL/FAIL: native right-edge tap reaches the top reader surface, but texture/page-model diagnostics did not appear in the capture window. |
| Edge navigation diagnostics | Right-edge tap with longer capture window | `captures/eta67-adb-manual/edge-tap-next-diagnostics` | Native tap emitted `Reader native tap action=RIGHT`, texture scroll/update logs appeared, and `location-page-model` reported `source=pagination-profile page=1/1757 chapter=1/3 profile=1757 observed=1 raw=0/411 href=OEBPS/Text/Hobbit_title-1.html`. Screenshot shows visible footer `2 / 1757`. | FAIL: eta67 uses the profile source, but the profile denominator is wrong because it is estimated from one observed frontmatter/title section. Raw Foliate total was `411`, so the profile model is still not acceptable. |

Manual user validation on eta67:

| Area | Test | Evidence | Result | Verdict |
| --- | --- | --- | --- | --- |
| Drag input | Manual page drag in EPUB | User report after eta67 install. | Dragging is fixed at the input/commit level. | PASS for gesture dispatch only: the native upper layer now recognizes drag well enough to change pages. |
| Drag preview | Manual page drag in EPUB | User report after eta67 install. | While dragging a page, the next page is not visible; the exposed area is black until the next page loads. | FAIL: Navic is not yet using a Komikku-style adjacent-page surface during drag. The gesture commits, but the drag visual is still wrong. |
| Texture transition | Manual page drag/page load in EPUB | User report after eta67 install. | Texture appears to follow the page during drag, then performs odd side-intensity animations when the new page loads. | FAIL: texture motion and committed page update are still fighting each other. Treat this as a surface/transition layering bug, not an input bug. |
| EPUB page count | Manual current reader observation | User report after eta67 install. | The EPUB now shows `373` pages. | IMPROVED/PENDING: `373` is close to the local harness raw total of `372` and much better than `1757`, but it is not yet proven stable across chapter boundaries or reloads. |

Host follow-up after the eta67 phone result:

| Area | Test | Evidence | Result | Verdict |
| --- | --- | --- | --- | --- |
| EPUB pagination profile | `node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3` | Local real-Hobbit EPUB traversal, Android-like viewport. | Command failed with `Expected stable page count during full traversal; observed 410, 396, 381, 387, 390, 391`. | FAIL: the denominator instability is reproducible without the phone. Stop broad ADB testing until this local traversal is stable; the page model is the current blocker. |

## 2026-06-15 Deterministic Pagination Profile Diagnostics And Spine Fallback Slice

User-visible bug:

- Later EPUB chapters could display impossible page states such as Chapter XIV showing as page 2, or totals jumping back to raw Foliate values like `1748`.
- The earlier native-cover boundary fix prevents local page zero from returning to the cover, but it does not by itself prove the page model is using the deterministic cached pagination profile for every chapter.

Root cause:

- Navic already records a deterministic pagination profile keyed by a render fingerprint. The fingerprint includes publication/render state such as viewport size, device scale factor, orientation, spread/flow mode, font source/family/size, line height, paragraph spacing, margin, publisher CSS, direction, and runtime version.
- Each profile stores total `pageCount`, per-chapter `pageStartIndex`, per-chapter `pageCount`, `href`, `spineIndex`, and whether the chapter count is observed or estimated.
- `readerPaginationPositionForLocator(...)` only matched cached chapters by href. If a later relocation had a missing, stale, or differently normalized href while still carrying the section/spine index, the profile lookup returned `null`.
- After that miss, the runtime could fall through to weaker page sources such as raw `detail.location.total`, which is how chapter-local states can surface with whole-book-looking totals such as `1748`.

Navic implication:

- `locationChanged` messages now include explicit page-model diagnostics:
  - `pageCountSource`
  - `paginationFingerprint`
  - `paginationProfilePageCount`
  - `paginationProfileObservedChapterCount`
  - `paginationProfileEstimatedChapterCount`
  - `rawLocationCurrent`
  - `rawLocationTotal`
- `readerPaginationPositionForLocator(...)` still prefers href matches when available, but now falls back to `spineIndex` when href is stale or missing.
- `readerPaginationProfilePosition(...)` passes the runtime section index as `spineIndex` into the locator and logs it with `pagination-profile:position`.
- This does not change the intended deterministic model. It makes the existing profile usable in more of the real Foliate relocation states.

Fresh red checks:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome"
```

Results:

- The JS harness failed because a Chapter XIV locator with stale href and valid `spineIndex = 16` returned `null`.
- The Android host test failed because `readerPaginationProfilePosition(...)` did not compute/pass `sectionIndex` as `spineIndex`.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-pagination-profile --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderPostsPageModelDiagnosticsWithLocationChanges"
```

Results: passed on 2026-06-15.

Host/harness evidence:

```json
{
  "pageIndex": 190,
  "pageCount": 377,
  "chapterPageIndex": 55,
  "chapterPageCount": 72,
  "pageCountSource": "pagination-profile",
  "paginationFingerprint": "navic-pagination-v1:380459720",
  "paginationProfilePageCount": 377,
  "paginationProfileObservedChapterCount": 2,
  "paginationProfileEstimatedChapterCount": 4,
  "rawLocationCurrent": 185,
  "rawLocationTotal": 372
}
```

Device validation still required:

- Install a release containing this slice and confirm later chapters no longer show raw Foliate totals.
- Confirm Chapter XIV/other later chapters move backward to previous readable pages, not the cover.
- Confirm page count remains stable across chapter links, edge taps, drags, orientation changes, and reopen.
- Use the new `location-page-model` logs to identify whether any remaining bad state uses `pagination-profile`, `page-list`, `stable-book`, or raw `location`.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Native Readable Drag Preview Slice

User-visible bug:

- EPUB/PDF drags still felt like abrupt page pops, not Komikku-style page movement.
- On image-heavy pages, horizontal movement could still be classified too late, so the user saw little or no drag feedback before the page turn.

Root cause:

- The native Komikku frame owned taps and shell-cover drag, but readable EPUB/PDF horizontal drags were still left to the renderer child.
- That meant the native container could not provide a consistent top-level page-drag preview, and device behavior depended on whether Foliate/WebView consumed the gesture stream.

Navic implication:

- `KomikkuReaderNativeViewerContainer` now intercepts horizontal readable-page drags after touch slop, the same way it already does for the shell cover.
- Readable drag `ACTION_MOVE` updates `viewerContentContainer.translationX`, logs `Reader native drag preview`, and cancels pending long-tap detection before any content long-press can fire.
- Readable drag `ACTION_UP` dispatches through `readerNativeReaderSwipeAction(...)` and maps the physical direction to `KomikkuNavigationRegion.RIGHT` or `KomikkuNavigationRegion.LEFT`.
- Shell-cover drag still uses `readerShellCoverSwipeAction(...)` and preserves the cover-specific NEXT/PREV mapping.
- `scripts/adb-reader-smoke.ps1` now looks for `Reader native drag preview`, so ADB validation can prove the native top-level manager saw the drag stream.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsReadableDragPreviewWhileNativeFrameOwnsTaps" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeAndNativeReadableDragPreview" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverDragFollowsMoveAndDispatchesPageTurnOnlyOnRelease" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics"
```

Result: failed before production changes because readable drags were still explicitly delegated, no `updateReadableViewerDragOffset(dx)` existed, and ADB diagnostics still tracked `Reader native drag delegated`.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsReadableDragPreviewWhileNativeFrameOwnsTaps" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeAndNativeReadableDragPreview" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverDragFollowsMoveAndDispatchesPageTurnOnlyOnRelease" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics"
```

Result: passed on 2026-06-15.

Device validation still required:

- Confirm normal EPUB text-page drags visibly move the page surface before release.
- Confirm drags over images stay classified as drags, not long presses.
- Confirm shell-cover drag still follows the finger and only commits on release.
- Confirm native ADB diagnostics include `Reader native drag preview` during readable-page drags.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Pagination Profile Cache Retention Slice

User-visible bug:

- Page numbers could still change denominator after reopen or later relocation, even when the same viewport/settings fingerprint was already cached.
- The local harness reproduced this with a real EPUB: the first pass cached `pageCount = 484` with `observedChapterCount = 2`; after reopening the same EPUB and same render fingerprint, the runtime immediately rewrote the total to `502` while still reporting `observedChapterCount = 2`.

Root cause:

- The cached pagination profile stored render metadata, but chapter measurements did not carry stable `spineIndex` or `source` metadata.
- On cache hit, `observedChapterPageCounts` was reset and not hydrated from the cached profile.
- After reopening, the runtime rebuilt the profile from the current section and accepted a fresh estimate even when it had learned no new chapter measurement. That let unobserved chapter estimates shift based only on whichever section was current.

Navic implication:

- `readerBuildPaginationProfile(...)` now stores `spineIndex`, `source`, `observedChapterCount`, and `estimatedChapterCount`.
- `readerPaginationObservedChapterEntries(...)` exposes cached observed entries with the same `spineIndex:href` key used by runtime observations.
- `readerEnsurePaginationProfile(...)` hydrates `observedChapterPageCounts` from a cache hit before building a fresh profile.
- Fresh profiles are only written over the current profile when they add observed chapters or change observed chapter measurements. Estimate-only denominator drift is retained as `pagination-profile:retained` instead of overwriting the cache.

Fresh red checks:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-pagination-profile --fixture tmp\reader-live\served-input.epub
```

Results:

- `pagination-profile-logic` first failed because `readerPaginationObservedChapterEntries` was not exported.
- `epub-pagination-profile` then failed because the cache-hit profile rewrote `484 -> 502` with no new observed chapter.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-pagination-profile --fixture tmp\reader-live\served-input.epub
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimeUsesDeterministicPaginationProfileForPageNumbers" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderUsesStableLocationTotalsForReflowablePageNumbers"
```

Results: passed on 2026-06-15.

Trace evidence:

- Reopen now logs `pagination-profile:cache-hit` with `pageCount = 484`, followed by `pagination-profile:retained` with `freshPageCount = 502`; the visible `pagination-profile:position` stays on `pageCount = 484`.

Device validation still required:

- Reopen the EPUB after navigating into later chapters and confirm the denominator does not change unless a new chapter measurement is learned.
- From Chapter XIV or later, verify the vertical rail/page label does not regress to a local chapter count.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Native Menu Content Hit-Test Slice

User-visible bug:

- Tapping real interactive EPUB content, especially images and links, could bring the reader menu/chrome back.
- The intended Komikku ownership split is that ordinary short taps and drags remain native reader gestures, while deliberate content interaction goes through long press or a future explicit content-interaction mode.

Root cause:

- The controller already suppresses the next native viewer action when it receives a typed content claim, but native-tap-zone mode intentionally prevents JS short-touch claims.
- That means the native frame could classify a center tap as `MENU` without checking whether the coordinate is a real image/link hit.
- The old WebViewHost delayed-center-tap suppression path is not acceptable because it moves ownership back into the renderer host.

Navic implication:

- `KomikkuReaderNativeViewerContainer` now routes confirmed taps through `dispatchSingleTapAction(...)`.
- Previous/next edge actions still dispatch synchronously through the native Komikku navigator.
- Only `KomikkuNavigationRegion.MENU` on readable content calls `dispatchMenuActionAfterContentHitTest(...)`.
- The native frame finds the active child `WebView` and evaluates `window.NavicReaderBridge.readerContentActionAtPoint(x, y, width, height)`.
- If that typed runtime hit test returns `true`, the menu action is suppressed and ADB logs `Reader native menu suppressed by content hit`.
- If no WebView exists or the hit test returns false, the normal menu toggle still happens.
- This does not re-enable short-tap WebView content activation; the runtime still keeps ordinary native-tap-zone short clicks suppressed, and long press remains the content activation path.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderSuppressesMenuChromeWithNativeFrameRuntimeContentHitTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderQueriesRuntimeContentHitOnlyForMenuActions"
```

Result: failed before production changes because the native frame had no `dispatchSingleTapAction(...)`, no menu-only content hit-test dispatcher, and no native-frame call to `readerContentActionAtPoint`.

Focused and adjacent green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderSuppressesMenuChromeWithNativeFrameRuntimeContentHitTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderQueriesRuntimeContentHitOnlyForMenuActions"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceCenterMenuIsNotSuppressedByRawImageHitType" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndRoutesLongPressToContent"
```

Results: passed on 2026-06-15.

Device validation still required:

- Center tap on normal text should still show/hide the menu.
- Center tap on a real EPUB image/link should not open the menu.
- Long press on images/links should still reach content behavior.
- Edge taps over image-heavy pages should still page previous/next rather than being blanket-blocked by image hit type.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Side Rail Height Tuning Slice

User-visible feedback:

- The vertical progress rail felt slightly too short after the Komikku port work.

Navic implication:

- `KomikkuReaderVerticalRailHeightFraction` was raised from `0.74f` to `0.78f`.
- The rail still lives in the weighted middle chrome slot, so it should not run under the top or bottom menu bars.
- This is a small source-level UX tuning slice, not a release candidate by itself.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: failed before the source change because the chrome test still expected the taller rail while production used `0.74f`.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Pagination Profile Render-Metadata Cache Slice

User-visible bug/risk:

- Chapter-local positions could still be confused with whole-book page positions when debugging page labels such as Chapter XIV showing as an early global page.
- The deterministic pagination profile already existed, but the stored cache payload did not explicitly retain the screen/render inputs that created the page math.
- Without stored render dimensions/settings, ADB or harness traces could prove a fingerprint existed but not what phone state it represented.

Navic implication:

- `readerPaginationRenderMetadata()` now builds the full render-state object used for pagination fingerprinting.
- `readerPaginationRenderFingerprint()` hashes that render metadata instead of building the hash payload inline.
- Cached pagination profiles now store `render` metadata alongside:
  - global page count;
  - per-chapter href/title;
  - per-chapter page count;
  - per-chapter global start index.
- The runtime rejects old/stale cached profiles that do not contain `profile.render.viewportWidth` and `profile.render.viewportHeight`.
- The first implementation still stores deterministic counts/start indices, not full CFI anchors. Navigation can keep using existing Foliate/chapter progress until the next anchor-storage slice.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimeUsesDeterministicPaginationProfileForPageNumbers"
```

Result: failed before production changes because the runtime had no `readerPaginationRenderMetadata()` method and cached profiles did not validate stored viewport dimensions.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimeUsesDeterministicPaginationProfileForPageNumbers"
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
```

Results: passed on 2026-06-15.

Device validation still required:

- Open the same EPUB in the same phone posture twice and confirm `pagination-profile:cache-hit` includes the expected global count.
- Rotate the phone and confirm a new fingerprint/profile is used instead of reusing the portrait profile.
- Jump to Chapter XIV and confirm the page label is based on the cached global profile, not the section-local page index.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Paper Texture Visibility Slice

User-visible bug:

- On eta64, the paper texture was almost invisible on sepia pages.
- The page-edge degradation layer was not visibly present enough; the screenshot showed mostly flat sepia paper with very weak pores/wrinkles and no convincing border wear.

Source-alpha check:

- `paper-texture-1.png` sampled average alpha about `2.62`, `paper-texture-2.png` about `2.29`, and `paper-texture-3.png` about `2.02`.
- `page-border-overlay-*.png` sampled average alpha about `6.88` to `7.61`, with max sampled alpha around `39` to `41`.
- With sepia texture opacity at `0.24`, the effective average texture contribution was too low to be noticed on-device.
- Border opacity was already `1`, so simply raising CSS opacity was not available.

Navic implication:

- Sepia paper texture opacity is raised to `0.42` while keeping the texture as a single fixed reader-window layer.
- The page-border overlay now composites the same selected border PNG twice inside the same fixed surface layer:
  - it does not reintroduce document-scoped texture layers;
  - it does not create stacked per-EPUB-element opacity;
  - it keeps the existing deterministic page variant selection.
- Paging, drag ownership, cover behavior, and texture movement direction logic are unchanged by this slice.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme"
```

Result: failed before production changes because sepia still returned `0.24` and the border overlay did not have the fixed-layer double-composite helper.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
```

Results: passed on 2026-06-15.

Device validation still required:

- Confirm sepia paper pores/wrinkles are visible but not distracting.
- Confirm page-edge degradation is visible on normal text pages, image pages, and chapter boundary pages.
- Confirm this did not reintroduce the old opacity-stacking mess.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Side Rail Height Tuning Slice

User direction:

- The vertical progress rail feels slightly too short after the Komikku rail migration.
- Keep this as local polish, not a release trigger.

Root cause:

- The rail was intentionally constrained away from full height, but the `0.68f` height fraction left the control visually shorter than the Komikku reference on the tested device.

Navic implication:

- `KomikkuReaderVerticalRailHeightFraction` is now `0.74f`.
- The rail still sits inside the top/bottom chrome overlay column and remains centered, so previous/next controls do not reach the screen edges.
- The chapter-local progress model, haptic drag feedback, and left/right rail placement settings are unchanged.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: failed before production changes because the rail height fraction was still `0.68f`.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Settings Dialog Responsive Width Slice

User direction:

- The settings overlay is still visually suboptimal: tab labels are truncated, font/content rows are cut awkwardly, and the panel feels like a narrow mobile dialog rather than the Komikku overlay.
- Keep improving this locally without publishing a release for a small isolated UI change.

Root cause:

- `KomikkuTabbedDialog(...)` used `BasicAlertDialog` with the platform default width behavior.
- The surface then applied `fillMaxWidth(0.78f)` inside that already-capped platform dialog window, so tablet screens still got a narrow panel and tab labels such as `Reading...` / `Custom...`.

Navic implication:

- `KomikkuTabbedDialog(...)` now uses `DialogProperties(usePlatformDefaultWidth = false)`.
- `KomikkuReaderSettingsDialog(...)` computes a responsive width fraction from the available window: `0.92f` below `720.dp`, `0.62f` at tablet/wide widths.
- The shared tabbed dialog surface receives `widthFraction` and applies `modifier.fillMaxWidth(widthFraction)`.
- The previous scroll-edge fade remains in place, so the wider dialog and fade work together instead of hiding overflow with a hard cut.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesResponsiveWidthInsteadOfPlatformDialogCap"
```

Result: failed before production changes because the dialog had no `DialogProperties(usePlatformDefaultWidth = false)`, no responsive width fraction, and still used the old fixed `0.78f` width.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesResponsiveWidthInsteadOfPlatformDialogCap"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuTabbedPagerContent" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesReusableKomikkuTabbedDialogPrimitive" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesScrollEdgeFadeInsteadOfAbruptCutoff" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesResponsiveWidthInsteadOfPlatformDialogCap" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsUseKomikkuStyleChipGroups"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Readable Drag Delegation Slice

User direction:

- Dragging EPUB pages should not just pop the next page; content should follow the finger like Komikku.
- Dragging over images must not become a long press.
- Stop treating small source-only progress as a release-worthy APK; keep this local until it is part of a meaningful RC.

Komikku source reference:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt`

Root cause:

- Komikku gets finger-follow page motion from the actual pager widget (`DirectionalViewPager`).
- Navic's current hybrid reader does not yet have a native ViewPager-equivalent for EPUB/PDF. The closest current pager is still Foliate inside the WebView.
- The previous native-frame implementation intercepted readable horizontal drags after slop and immediately emitted `Reader native swipe action=...`, which cancelled the child/page stream and produced the reported pop/no-drag behavior.

Navic implication:

- The native frame still owns reader-wide taps and long-tap routing.
- Shell-cover drags remain native because the cover is a native layer; the cover view now receives a horizontal translation during drag before the shell-cover command is dispatched.
- Readable EPUB/PDF drag motion is no longer converted into immediate native page-turn actions. The native frame logs `Reader native drag delegated...` and leaves the move stream with the active pager engine so the page can follow the finger.
- `scripts/adb-reader-smoke.ps1 -RequireNativeSwipeAction` is kept for compatibility but now requires the readable drag delegation diagnostic rather than the removed native swipe shortcut.

Deliberate limitation:

- This is the correct hybrid-reader stopgap, not the final Komikku endpoint. The final architecture should replace the WebView/Foliate page-motion layer with a native pager/controller equivalent. Until then, stealing readable drag motion in the native frame makes the experience worse.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameDispatchesShellCoverSwipesThroughViewerActionBoundary" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameLetsReadablePagerOwnDragMotionWhileNativeFrameOwnsTaps" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Result: failed before production changes because the native frame still logged `Reader native swipe action=...`, still used `readerNativeReaderSwipeAction(...)` in the dispatch path, and the smoke script still required the old diagnostic.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameDispatchesShellCoverSwipesThroughViewerActionBoundary" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameLetsReadablePagerOwnDragMotionWhileNativeFrameOwnsTaps" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameEmitsAdbReadableInputDiagnostics" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Settings Dialog Scroll Edge Fade Slice

User direction:

- The in-reader settings dialog still cuts off scrollable content abruptly, as shown by the partially clipped `Font size` row in `D:/Downloads/Screenshot_20260615_182601_Navic.jpg`.
- Keep improving the Komikku overlay surface locally without publishing another APK for small isolated fixes.

Root cause:

- `KomikkuReaderSettingsDialog(...)` used a normal `verticalScroll(...)` content column inside the bounded dialog.
- That preserved the bounded Komikku-style overlay, but gave no visual affordance that content continued above or below the current viewport.

Navic implication:

- `KomikkuReaderSettingsDialog(...)` now owns its `ScrollState` and applies `komikkuVerticalScrollEdgeFade(...)` before `verticalScroll(...)`.
- The fade is drawn with an offscreen compositing layer and top/bottom `BlendMode.DstIn` gradients only when there is content hidden in that direction.
- This is visual chrome only. It does not resize the reader, change settings ownership, or alter the engine bridge.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesScrollEdgeFadeInsteadOfAbruptCutoff"
```

Result: failed before production changes because the settings dialog did not use a scroll-edge fade modifier and the scrollable content was hard-cut.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesScrollEdgeFadeInsteadOfAbruptCutoff"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Paper Texture Visibility Boost Slice

User direction:

- On eta66 the paper texture is almost invisible, the edge-degradation textures are not visible enough, and the page lacks pores/wrinkles.
- Improve this locally as a contained source/test slice; do not publish a release for this alone.

Root cause:

- Sepia paper texture opacity was only `0.14`, which looked absent on-device after blending through the reader surface.
- The border-degradation overlay used full opacity but no filter boost, so the edge layer could still disappear depending on the base theme and asset contrast.

Navic implication:

- `readerSurfacePaperTextureOpacity(...)` now uses stronger but still bounded opacity values: sepia `0.24`, dark/dusk `0.12`, and default `0.16`; black remains `0`.
- `readerSurfacePageBorderOverlayFilter(...)` now provides a theme-aware contrast/saturation boost.
- `updateReaderSurfaceBorderOverlayLayer(...)` applies that filter on the fixed top-level border overlay surface.
- This does not move texture ownership back into EPUB content. Texture and border overlays remain reader-window surfaces.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme"
```

Result: failed before production changes because sepia opacity still returned `0.14` and the border updater had no `readerSurfacePageBorderOverlayFilter(...)`.

Focused green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderMirrorsPaperTextureOnTopLevelSurface" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureAtReaderWindowSurfaceOnly"
```

Results: passed on 2026-06-15. The first Gradle rerun without `--rerun-tasks` reused cached results, so the recorded green check is the forced rerun that actually executed the assertions.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Deterministic Pagination Profile Slice

User direction:

- Record render dimensions, global page count, and per-chapter page math on first book load.
- Fingerprint the render state so the same book can keep separate page profiles for different phone states.
- Use the cached math for deterministic ebook page numbering instead of raw Foliate totals such as `1748` or section-local counters such as `2 / 14`.

Implementation:

- Added `readerPaginationFingerprint(...)`, `readerBuildPaginationProfile(...)`, and `readerPaginationPositionForLocator(...)` in the reader helper layer.
- The fingerprint includes publication identity, spine/content key, viewport width/height, device pixel ratio, orientation/spread mode, flow mode, font source/family/custom font URL, font size, line height, paragraph spacing, margin, publisher-CSS mode, direction, and runtime profile version.
- The runtime now builds a `paginationProfile` from readable non-cover sections only, stores it in `localStorage` under `navic-reader-pagination-profile:<fingerprint>`, and reloads it on the same render state.
- The organic ebook page number layer now prefers `pagination-profile` positions before the old whole-book estimate, location, page-list, or section fallback chain.
- `locationChanged` messages use the same profile-backed page index and count as the organic page label, while the side rail remains chapter-local through `chapterPageIndex/chapterPageCount`.
- Profile state is reset on publication close, settings changes, and fingerprint changes so portrait/landscape, font, margin, and theme/layout variants do not contaminate each other.
- The browser harness `epub-pagination-profile` mode is now fixture-agnostic. It opens a real EPUB, forces a relocation, verifies `pagination-profile:updated`, verifies `locationChanged` uses the profile position, verifies the profile cache key exists, reopens the same EPUB, and verifies `pagination-profile:cache-hit`.
- The phase-one harness list now includes `pagination-profile-logic` and `epub-pagination-profile`.

Current limitation:

- The first profile uses the current rendered chapter as the visual calibration point and estimates unvisited chapter counts from spine sizes. As chapters are visited, exact observed counts replace estimates and the cache is updated. A later refinement can pre-measure every chapter up front if we need fully exact first-open counts, but this slice removes the raw-total and chapter-local denominator regressions without blocking the reader on a full-book scan.

Fresh red checks:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimeUsesDeterministicPaginationProfileForPageNumbers"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderUsesStableLocationTotalsForReflowablePageNumbers"
```

Results: failed before production/test-source updates because the pagination helpers were not exported, the runtime did not use a `paginationProfile`, and existing source guards still required the old fallback order.

Focused and wider green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode pagination-profile-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-pagination-profile --fixture tmp\reader-live\served-input.epub
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderUsesStableLocationTotalsForReflowablePageNumbers" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimeUsesDeterministicPaginationProfileForPageNumbers"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroid
git diff --check
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 eta66 Device Validation Matrix

Purpose:

- Validate `v1.0.11-eta66` on the connected Android device using ADB, with the EPUB already opened by the user.
- Record pass/fail results here as they are observed so context compaction cannot erase the current validation state.
- Treat these checks as device evidence, not as proof that the Komikku reader port is complete.

Release candidate:

- Version: `v1.0.11-eta66`
- Android `versionCode`: `399`
- Commit/tag: `1138af38` / `v1.0.11-eta66`
- GitHub Actions run: `27553931486`
- Release URL: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta66`
- Asset: `Navic.apk`, SHA-256 digest from GitHub release metadata `aa5dedc0a535731639f6131fee55614ceab77d3754089ec7334ef6532272d2ae`
- Workflow result: Android APK build/signing/release succeeded; iOS IPA job was skipped for the hyphenated eta tag.

Pre-device verification before release:

- `scripts/verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta66`: passed.
- `node --check composeApp/src/androidMain/assets/reader/navic-reader.js`: passed.
- `git diff --check`: passed.
- `tools/reader-harness/src/run-reader-harness.mjs --mode phase1-stabilization ...`: passed 16 checks.
- Focused Android host reader subset: passed.

ADB environment:

- Device: `RFCY80551LT`
- Package: `darkaxt.navic`
- Installed version captured by ADB: `versionCode=399`, `versionName=v1.0.11-eta66`
- Running PID during validation: `1920`
- WebView devtools socket exposed: `@webview_devtools_remote_1920`

Device checks:

| Check | Status | Evidence | Notes |
| --- | --- | --- | --- |
| Baseline open EPUB state | Observed | `captures/manual-eta66-adb/baseline-current-reader` | App was on the native cover. Cover image fills height better than old builds but still has black side bars because the image aspect ratio is narrower than the device. |
| Cover center tap | Improved / pass | `captures/manual-eta66-adb/cover-center-tap-toggle` | Two center taps logged `Reader native tap action=MENU` and did not discard the cover. |
| Cover drag next | Improved / pass | `captures/manual-eta66-adb/cover-drag-next` | Logged `Reader shell cover drag candidate`, `Reader shell cover swipe action=Right`, and `Reader shell cover command action=Right`; it entered the EPUB. |
| First EPUB page after cover drag | Needs investigation | `captures/manual-eta66-adb/cover-drag-next/screen.png` | The page indicator shows `1 / 1748`. Local harness reports 505 pages for the same fixture geometry, so device page-number semantics still need investigation. |
| EPUB center tap toggle, two taps | Pass for native input | `captures/manual-eta66-adb/epub-center-tap-toggle` | Logged two `Reader native tap action=MENU` events. Final state was chrome-hidden, consistent with show then hide. |
| EPUB center tap, one tap | Improved / pass with UI issues | `captures/manual-eta66-adb/epub-center-single-tap-show-menu` | One center tap shows top bar, side rail, and bottom bar. Remaining issue: organic `1 / 1748` page number remains visible behind/under the bottom bar while the side rail shows a local `1..3` range. |
| Side progress rail height | Needs tuning | User feedback during eta66 validation | The rail no longer occupies the whole vertical space, but now feels a little too short. Tune toward Komikku's visual balance without letting next/previous buttons sit under top/bottom chrome. |
| EPUB edge tap next | Pass with page-count issue | `captures/manual-eta66-adb/epub-edge-tap-next` | Native log captured `Reader native tap action=RIGHT`; engine location moved to `pageIndex=1`, `chapterPageIndex=1`, `chapterPageCount=3`; texture log captured `dir=next x=-698`, which is the expected horizontal sign for next. Device still reports global `pageCount=1748`. |
| EPUB edge tap previous | Pass with page-count issue | `captures/manual-eta66-adb/epub-edge-tap-previous` | Native log captured `Reader native tap action=LEFT`; engine location moved back to `pageIndex=0`, `chapterPageIndex=0`, `chapterPageCount=3`; texture log captured `dir=previous x=698`, which is the expected horizontal sign for previous. Device still reports global `pageCount=1748`. |
| EPUB drag next | Pass with gesture-noise issue | `captures/manual-eta66-adb/epub-drag-next` | Native log captured `Reader native swipe action=Right`; engine location moved to `pageIndex=1`, `chapterPageIndex=1`, `chapterPageCount=3`; texture log captured `dir=next x=-698`, expected for next. Issue: the same swipe also logged `Reader native long tap`, so drag/long-press classification is still noisy. |
| EPUB drag previous | Pass with gesture-noise issue | `captures/manual-eta66-adb/epub-drag-previous` | Native log captured `Reader native swipe action=Left`; engine location moved back to `pageIndex=0`, `chapterPageIndex=0`, `chapterPageCount=3`; texture log captured `dir=previous x=698`, expected for previous. Issue: the same swipe also logged `Reader native long tap`, matching the drag-next gesture-noise risk. |
| EPUB texture next walk through frontmatter | Improved / pass for direction, weak visually | `captures/manual-eta66-adb/epub-texture-next-walk` | Repeated next taps crossed Title Page, Table of Contents, Maps, Author's Note, and Chapter I. All captured next texture-scroll samples used negative x, so the prior map/Author's Note inversion did not reproduce in this pass. Remaining visual issue: texture is almost imperceptible; edge degradation layer is not visible, and paper pores/wrinkles are effectively absent. |
| EPUB image normal tap | Fail | `captures/manual-eta66-adb/epub-image-content-tap` | Tapping the image area did not emit `readerContentTapHandled`; native layer logged `Reader native tap action=MENU` and opened chrome. Interactive content still needs separation from normal menu taps, likely via long-press/content-specific path. |
| Final state for user feeling test | Ready | `captures/manual-eta66-adb/final-clean-reader-state` | Chrome hidden after validation; device left inside EPUB on Chapter I page image/text area. |

Manual feeling-test results after the ADB round:

- Cover drag still fails visually. ADB proved a cover swipe command is dispatched, but the user-visible behavior is a pop/discard of the cover, not a Komikku-like drag-follow animation.
- Returning to index `0` restores the native shell cover. This is an improvement and must be preserved.
- Normal page drag still fails visually. It changes pages by popping to the next/previous page; it does not drag the page surface under the finger. This also means the texture-transition defect is harder to judge because the dragged page/texture relationship is not exposed.
- Long-press on images works, but dragging across an image is still misclassified as long-press/content interaction. Dragging must be owned by the native upper layer and must not devolve into image long-press.
- Page numbers appear more coherent than older builds, but the device reports `1748` pages while prior builds/harness runs reported roughly `411` to `505`. Page-count semantics remain unresolved.
- The settings dialog remains suboptimal: the layout wastes space, tab labels are truncated, `Font size` is visibly cut by the scroll viewport, and scrolling should use a fade/gradient edge instead of a hard cutoff. Evidence screenshot: `D:/Downloads/Screenshot_20260615_182601_Navic.jpg`.
- The ebook theme should eventually move away from the generic blue accent toward a leather/sepia/brown reader palette. This is lower priority than input, pagination, and layout correctness.

Additional eta66 regression captured after the manual pass:

| Check | Status | Evidence | Notes |
| --- | --- | --- | --- |
| Chapter XIV first page reports as `2 / 1748` | Fail | `captures/manual-eta66-adb/chapter14-page2-regression/screen.png` | Screenshot shows `Chapter XIV: Fire and Water` while the organic footer shows `2 / 1748`. Bridge logs for the same chapter show mixed page semantics: `pageIndex=1`, `pageCount=1748`, `chapterPageIndex=0`, `chapterPageCount=14`. The organic footer is using the unstable/global-looking `pageIndex/pageCount`; the Komikku rail uses chapter-local state. |
| Previous from first page of later chapter can route through the native-cover boundary | Fail / needs focused reproduction | `captures/manual-eta66-adb/chapter14-page2-regression/logcat-reader.txt` | The controller's native-cover return rule is based on `locator.pageIndex <= 0`. This is only safe if `locator.pageIndex` is a stable whole-book index. In eta66 the bridge can emit low or reset page indices for later chapters, so the shell-cover boundary can be triggered from a later chapter instead of moving to the previous chapter/page. The same log also contains a direct left-tap sequence that did reach Chapter XIII, so the failure likely depends on which navigation path is used: raw previous-page command versus rail/index navigation. |

Deterministic pagination profile decision:

- The reader must stop treating Foliate's raw `location.current`, `location.total`, and transient `pageIndex/pageCount` values as the user-visible book model.
- On first layout for a render context, Navic should fingerprint the render state and cache the measured page math for that fingerprint. A single book can therefore have multiple valid pagination profiles on the same phone.
- The render fingerprint must include at least: publication identity/cache key, EPUB content identity where available, viewport width/height, device scale factor, orientation/spread mode, flow mode, font source, font family, custom font identity, font size, line height, paragraph spacing, margin, publisher CSS mode, direction, and the Navic/Foliate reader runtime version.
- The cached pagination profile should store: global page count, chapter href/title sequence, per-chapter page count, per-chapter global start index, and enough page anchors/CFI/progress values to navigate deterministically.
- The organic footer should render from this profile: `globalPageIndex + 1 / globalPageCount`.
- The Komikku side rail should render from the same profile but chapter-local: `chapterPageIndex + 1 / chapterPageCount`.
- Native cover return must become an explicit absolute-book boundary (`bookIndex == -1` or equivalent shell-cover state), not a heuristic based on `locator.pageIndex <= 0`.
- If the fingerprint changes because the user rotates the device, changes font/margins/spacing/theme-relevant layout inputs, switches spread mode, or changes the WebView/Foliate layout version, the cached profile is not reused. A new profile is measured and stored next to the old one.
- Until full CFI anchor storage exists, the first implementation slice may cache deterministic counts and start indices only, then use existing chapter progress for navigation. That is acceptable only if display numbering and cover-boundary decisions no longer use unstable raw page values.

Next checks to run in this same validation block:

- Build a focused laptop harness case that enters a later chapter and asserts the organic footer, chapter rail, and shell-cover return predicate use the deterministic pagination profile instead of mixing raw global and chapter-local page models.
- Reproduce the Chapter XIV `2 / 1748` state on-device after a clean open and verify whether edge-left, rail previous, and scrub-to-zero use different navigation paths.
- Implement actual drag-follow animation/page movement for native cover and normal EPUB pages before spending more time on texture-transition perception.
- Fix drag-vs-long-press classification, especially over images.
- Investigate device page count `1748` versus the expected roughly `411` to `505` range.
- Redesign the settings dialog layout with compact readable tabs, better spacing, and scroll fade/gradient edges.
- Tune the side progress rail height and next/previous button positioning against the Komikku reference.
- Strengthen paper texture and edge-degradation visibility; the current effect is nearly invisible.
- PDF checks only after the device is explicitly placed in a PDF reader state.

## 2026-06-15 Overnight Embedded Cover Initial Location Slice

User direction:

- Keep working locally overnight and save meaningful source progress for the morning RC/ADB gate.
- Do not publish another APK for isolated changes.

Root cause:

- The browser/Foliate phase-1 harness failed in `epub-frontmatter` because the first posted visible WebView location was `pageIndex=1`.
- The underlying sequence was that `suppressLoadedEmbeddedCoverPage(...)` hid the embedded cover and re-rendered, but only refreshed the organic page number. It did not schedule a committed `locationChanged` for the first visible page.
- When the next page command arrived, `startPageTurn(...)` cancelled the pending initial relocation, so the first externally visible location became the first turned page.

Navic implication:

- Embedded cover suppression now schedules `scheduleCommittedRelocation(this.lastRelocateDetail, 'embedded-cover-suppressed')` after the rerender and page-number refresh.
- This preserves the controller/browser contract that the first visible readable page is emitted as page `0` before subsequent page turns.

Fresh red checks:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.embeddedCoverSuppressionPostsInitialVisibleLocationBeforeFirstPageTurn"
```

Results: the harness failed at `epub-frontmatter` with `Expected first visible WebView pageIndex to be 0; observed 1`. The focused source guard failed before production changes because the embedded-cover suppression path did not schedule a committed relocation.

Focused and wider green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.embeddedCoverSuppressionPostsInitialVisibleLocationBeforeFirstPageTurn"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-frontmatter --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
```

Results: passed on 2026-06-15. The full phase-1 browser harness completed 16 checks after the fix, including EPUB frontmatter, page boundary, shell cover, external shell cover, link-jump drag, texture probes, full EPUB traversal, PDF smoke, PDF fast sequential turns, and PDF image settings.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Readaloud Adapter Reattachment Slice

User direction:

- Continue source-level Komikku reader backbone work overnight without publishing another APK.
- Keep restoring EPUB/PDF/readaloud capability behind controller/coordinator adapters instead of reviving the old screen-local reader runtime.

Root cause:

- The Komikku reset detached `ReaderReadaloudRuntimeHost(...)` from the active reader screen.
- The old readaloud path emitted legacy `ReaderBridgeCommand` values and consumed raw `ReaderBridgeEvent` values at the screen boundary, which conflicts with the Komikku backbone rule that the active screen must not own Foliate/WebView protocol directly.

Navic implication:

- `ReaderScreen(...)` mounts `ReaderReadaloudRuntimeHost(...)` again, but only through typed `ReaderEngineHostEvent` fanout and `ReaderEngineCommand` output.
- `ReaderReadaloudRuntimeHost` receives `readerHostEvent: ReaderEngineHostEvent?` and unwraps `ReaderEngineHostEvent.FoliateBridge` inside the Android runtime adapter.
- Readaloud media-overlay commands now enter `ReaderCoordinator.onReadaloudEngineCommand(...)`.
- Readaloud playback state now enters `ReaderCoordinator.onReadaloudPlaybackState(...)` and `ReaderController.onReadaloudPlaybackState(...)`, updating `ReaderChromeState.readaloudPlayback`.
- The active reader screen remains protected from raw `ReaderBridgeEvent`/`ReaderBridgeCommand` types.

Deliberate limitation:

- This slice reattaches the readaloud runtime and controller state path. It does not yet restore visible readaloud playback controls, miniplayer behavior, or user-facing play/pause commands in the Komikku shell.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.readaloudPlaybackStateIsControllerOwnedAndDoesNotEmitEngineCommands" --tests "paige.navic.reader.ReaderCoordinatorTest.readaloudAdapterCommandsRouteThroughControllerBeforeCurrentEngineAdapter" --tests "paige.navic.reader.ReaderCoordinatorTest.readaloudPlaybackStateRoutesThroughCoordinatorWithoutEngineCommand" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeReaderScreenReattachesReadaloudThroughCoordinatorAdapterBoundary"
```

Result: failed before production changes because the controller/coordinator readaloud entry points and active runtime mount did not exist.

Focused and wider green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReadaloudRuntimeCapabilitiesRemainAvailableBehindControllerBoundary"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderNormalizesReadableTapZonesThroughNativeReaderSurfaceLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readerChromeIsImmersiveAndDrivenByNativeReaderSurfaceTaps"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderViewerTest" --tests "paige.navic.reader.ReaderReadaloudSyncCoordinatorTest" --tests "paige.navic.reader.ReaderMediaOverlaySyncTest" --tests "paige.navic.reader.ReadaloudModelsTest"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Local Harness Re-Run After Bottom Bar Slice

Purpose:

- Establish a laptop-side baseline before the morning release-candidate build.
- Validate the source/runtime paths that do not require ADB: shell cover sequencing, external shell cover bypass, native tap-zone open state, link-jump drag recovery, page-boundary numbering, and texture movement harnesses.
- Keep the result separate from device validation. Passing these harnesses does not prove Android WebView/native touch behavior is correct.

Commands:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs epub-link-jump-drag --fixture tmp\reader-live\served-input.epub
node tools\reader-harness\src\run-reader-harness.mjs epub-shell-cover --fixture tmp\reader-live\served-input.epub
node tools\reader-harness\src\run-reader-harness.mjs epub-external-shell-cover --fixture tmp\reader-live\served-input.epub
node tools\reader-harness\src\run-reader-harness.mjs epub-native-tap-zone-open --fixture tmp\reader-live\served-input.epub
node tools\reader-harness\src\run-reader-harness.mjs epub-page-boundary --fixture tmp\reader-live\served-input.epub
node tools\reader-harness\src\run-reader-harness.mjs epub-texture-page-turns --fixture tmp\reader-live\served-input.epub
node tools\reader-harness\src\run-reader-harness.mjs epub-texture-scroll --fixture tmp\reader-live\served-input.epub
```

Results: passed on 2026-06-15.

Morning ADB matrix still required:

- Cover taps and drags over the native shell cover.
- Center tap menu toggle on cover, text pages, image pages, and after link/chapter jumps.
- Image/link interactions must not open menus on short tap.
- Texture movement must be checked on the phone because the browser harness did not reproduce the reported inversion.
- PDF navigation and centering still need device validation.

Release status:

- No APK was built or published for this baseline run.

## 2026-06-15 Overnight Bottom Bar Width Correction

User direction:

- Keep working overnight without publishing another release candidate.
- Continue tightening the Komikku-derived shell instead of adding release churn for minor source fixes.

Root cause:

- `KomikkuReaderBottomBar` used `Arrangement.SpaceEvenly`, but its inner `Row` only measured to intrinsic content width because it did not call `fillMaxWidth()`.
- That made the bottom actions behave like a compact Material row instead of Komikku's centered, evenly distributed bottom action strip.

Navic implication:

- The bottom action row now fills the available width before applying `Arrangement.SpaceEvenly`.
- The existing bottom actions remain Contents, Reading mode, and Settings; bookmark stays in the top bar.

Texture note from this pass:

- The laptop-side `texture-offset-logic` and `epub-texture-frontmatter-transition` harness modes passed against `tmp/reader-live/served-input.epub`.
- No texture math was changed in this slice because the local harness did not reproduce the phone-side inversion. The morning ADB matrix still needs to validate texture movement on the device.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction"
```

Result: failed before production changes because the bottom action row did not contain `.fillMaxWidth()`.

Focused green and harness checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction"
node tools\reader-harness\src\run-reader-harness.mjs texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs epub-texture-frontmatter-transition --fixture tmp\reader-live\served-input.epub
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Shell Cover Side-Zone Slice

User-visible bug:

- The synthetic shell cover could disappear on ordinary cover touches because it reused the active EPUB reading tap-zone shape. In the L-shaped/default style, large top/bottom parts of the cover classify as previous/next instead of menu.

Root cause:

- `KomikkuReaderNativeViewerContainer` classified shell-cover taps with the same `navigator` used for readable EPUB/PDF pages.
- `ReaderScreen` then mapped physical LEFT/RIGHT through the active reader direction, so RTL text direction could make the cover's right edge behave as previous/no-op.

Navic implication:

- The native Android frame now switches to a cover-specific `KomikkuRightAndLeftNavigation` while the shell cover view is visible.
- The common shell maps cover LEFT/RIGHT physically with `readerShellCoverViewerActionFor(...)`: right/next enters the readable book, left/previous remains at the cover boundary, center/menu toggles chrome.
- Normal readable EPUB/PDF tap-zone mapping remains controlled by the active reader settings and text direction.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.shellCoverUsesPhysicalSideZonesWithoutReaderDirectionInversion" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeShellCoverUsesCoverSpecificSideZoneNavigation"
```

Result: failed before production changes because `readerShellCoverViewerActionFor(...)`, the shell-cover navigator, and active shell-cover navigator switch did not exist.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderViewerTest.shellCoverUsesPhysicalSideZonesWithoutReaderDirectionInversion" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeShellCoverUsesCoverSpecificSideZoneNavigation"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Content-Claim Arbitration Slice

User-visible bug:

- Interacting with EPUB images or links could still bring the reader menu back because the native Komikku surface emitted a reader action after the content runtime reported that the tap belonged to interactive content.

Root cause:

- `ReaderController.onViewerAction(...)` cleared `lastContentActionClaim` and then continued executing the same native viewer action.
- That made `readerContentTapHandled` useful as metadata but useless as arbitration, so a content image/link tap could also toggle the menu or page-turn.
- The fix stays at the typed controller boundary. It does not restore the old WebView-host delayed tap arbitration.

Navic implication:

- A content action claim now suppresses exactly the next native viewer action and then clears.
- The following independent native action still runs normally, so this does not create sticky disabled navigation after an image/link interaction.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsSuppressOnlyTheNextNativeViewerAction" --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsKeepMetadataInControllerState"
```

Result: failed before production changes because both tests still saw `menuVisible == true` after an image/link content claim.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsSuppressOnlyTheNextNativeViewerAction" --tests "paige.navic.reader.ReaderControllerTest.contentActionClaimsKeepMetadataInControllerState" --tests "paige.navic.reader.ReaderCoordinatorTest.bridgeContentClaimsSuppressNextNativeActionWithoutBridgeOwningMenu"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Post-Rail Pre-RC Baseline Refresh

User direction:

- Keep working overnight without publishing another Android release candidate.
- In the morning, compile one new release candidate and run the full ADB validation array.
- Refresh local source and browser-path evidence after each committed backbone slice so the morning APK is not the first verification point.

Verified source baseline after `a7b8435a`:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest"
git diff --check
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-settings.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
```

Results: passed on 2026-06-15.

Verified real EPUB/PDF browser harness baseline after `a7b8435a`:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Result: passed on 2026-06-15. The combined harness completed 16 checks: trace smoke, EPUB frontmatter, EPUB page boundary, shell cover, external shell cover, native tap-zone open, CSS smoke, link-jump drag, texture offset logic, texture scroll, texture page turns, texture frontmatter transition, full EPUB traversal, PDF smoke, PDF fast sequential turns, and PDF image settings.

Important limitation:

- This is still local source/browser evidence only. It does not prove Android native-frame behavior for cover drag, center tap/menu toggling, image/link long press, texture movement on the actual phone, or PDF tap/drag responsiveness. Those remain morning release-candidate ADB checks.

Release status:

- No APK was built or published for this baseline.

## 2026-06-15 Overnight Komikku Progress Rail Placement Slice

User direction:

- Keep replacing shortcut reader UI with Komikku-derived controller structure.
- Do not publish another APK overnight for source-only UI backbone work.
- Avoid hardcoded "looks like Komikku" behavior when the original implementation exposes reader preferences.

Komikku source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ReaderAppBars.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`

Root cause:

- Navic's active Komikku reader app bars still contained a private `KomikkuNavBarType` enum and hardcoded `val navBarType = KomikkuNavBarType.VerticalRight`.
- That meant the controller could not choose left, right, or bottom progress controls, even though Komikku models progress/navigation placement through reader preferences such as vertical/left seekbar choices.

Navic implication:

- `ReaderSettings` now carries `navBarType` with supported values `vertical-right`, `vertical-left`, and `bottom`.
- Global and per-book reader settings persist, normalize, and restore the rail placement.
- `KomikkuReaderAppBars(...)` now derives placement from `normalizedReaderNavBarType(controllerState.chrome.settings.navBarType)` instead of a private hardcoded enum.
- The in-reader Reading tab exposes a `Progress rail` chip row using the shared supported values and labels.
- Settings > Ebooks exposes the same default as `Progress rail`, and settings search exposes it as `ebooks.nav-bar-type`.
- The bridge serializes `navBarType` so the setting remains part of the engine command payload and can be consumed by later capability adapters.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderProgressRailPlacementIsControllerSettingNotHardcoded" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepKomikkuNavBarTypes" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripNavBarTypePreference" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Result: failed before production changes because `navBarType` settings/constants/preferences/search rows did not exist and the active app bars hardcoded `KomikkuNavBarType.VerticalRight`.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderProgressRailPlacementIsControllerSettingNotHardcoded" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepKomikkuNavBarTypes" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripNavBarTypePreference" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Result: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice. The next APK should be a morning release candidate after source verification, followed by the full ADB matrix.

## 2026-06-15 Overnight Komikku RTL Chapter Navigator Slice

User direction:

- Continue source-level Komikku backbone work overnight.
- Keep the morning APK/release candidate as the next device validation gate.
- Prefer real Komikku behavior over superficial layout matching.

Komikku source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`

Root cause:

- Navic already had a reader direction setting and passed LTR/RTL into the EPUB renderer.
- The visible Komikku-style chapter/page navigator ignored that direction and always treated the left button as previous and the right button as next.
- Komikku explicitly derives `isRtl`, keeps the outer control row LTR, applies RTL layout direction to the slider row, and swaps the enabled/onClick behavior of the previous/next buttons.

Navic implication:

- `KomikkuReaderAppBars(...)` now derives `isRtl` from `ReaderSettings.direction`.
- `KomikkuChapterNavigator(...)` receives `isRtl` and mirrors Komikku's button/action inversion for horizontal progress navigation.
- The horizontal navigator wraps the outer row with `LocalLayoutDirection provides LayoutDirection.Ltr` and the slider row with `LocalLayoutDirection provides layoutDirection`.
- Vertical rail behavior stays aligned with Komikku's vertical navigator path and does not invent separate RTL handling.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku"
```

Result: failed before production changes because the app bars did not derive/pass `isRtl`, and the navigator had no RTL layout/action inversion.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsUseKomikkuStyleChipGroups" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.androidReaderExposesKomikkuStyleOrientationControl"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Komikku Native Viewer Layer Filters Slice

User direction:

- Continue source-level Komikku backbone work overnight.
- Do not publish another APK/release candidate until the morning phone validation gate.
- Recycle proven Komikku behavior instead of approximating it through visual overlays.

Komikku source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ColorFilterPage.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`

Root cause:

- The previous Navic custom-filter port deliberately skipped grayscale and inverted-colors because Komikku does not implement those as page CSS or as a fake UI tint.
- Komikku applies grayscale and inverted colors with a `ColorMatrixColorFilter` on the native `viewerContainer` hardware layer.
- If Navic implemented those toggles as a Compose overlay, it would again be a lookalike instead of the actual reader/viewer ownership model.

Navic implication:

- `ReaderSettings` now carries `grayscaleEnabled` and `invertedColors`.
- Global preferences, per-book override persistence, and bridge serialization include both fields.
- The in-reader Custom filter tab exposes `Grayscale` and `Inverted colors` switches next to the existing color filter controls.
- Settings > Ebooks exposes app-level defaults for both switches and both are searchable.
- Android `KomikkuReaderNativeFrameHost` passes those fields to the native frame root.
- `KomikkuReaderNativeFrameRoot` applies the combined `ColorMatrixColorFilter` to the viewer container layer, matching Komikku's native layer-paint approach.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameAppliesViewerLayerColorMatrixLikeKomikku" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepKomikkuColorFilterModel" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripKomikkuColorFilter" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Result: failed before production changes because `grayscaleEnabled`, `invertedColors`, `readerGrayscaleEnabled`, `readerInvertedColors`, native layer-paint support, Ebooks rows, and settings-search entries did not exist.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderDefaultSettingsRememberKeyTracksReaderPreferenceInputs" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameAppliesViewerLayerColorMatrixLikeKomikku" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepKomikkuColorFilterModel" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripKomikkuColorFilter" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Komikku Reusable Tabbed Dialog Primitive Slice

User direction:

- Keep moving toward the actual Komikku reader architecture, not a visual knock-off.
- Do not publish another release candidate for source-only UI backbone work.

Komikku source reference:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`

Root cause:

- Navic's settings dialog had adopted Komikku-like tabs and pager content, but the dialog still hand-built its own `BasicAlertDialog`, `Surface`, `KomikkuSettingsTabRow`, and `HorizontalPager` directly in `KomikkuReaderSettingsDialog(...)`.
- That kept the settings shell as another one-off reader surface instead of the reusable Komikku `TabbedDialog` pattern.

Navic implication:

- Added `KomikkuTabbedDialog(...)` as the reusable settings/dialog primitive for the reader shell.
- `KomikkuTabbedDialog(...)` owns the `BasicAlertDialog`, rounded `Surface`, compact tab row, `HorizontalPager`, and footer slot.
- `KomikkuReaderSettingsDialog(...)` now delegates shell, tabs, and pager ownership to `KomikkuTabbedDialog(...)`.
- Settings page content, scroll behavior, tab selection, Custom filter menu hiding, and all existing settings mutations are preserved.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesReusableKomikkuTabbedDialogPrimitive"
```

Result: failed before production changes because `KomikkuReaderSettingsDialog(...)` still owned `BasicAlertDialog`, `Surface`, `KomikkuSettingsTabRow`, and `HorizontalPager` directly.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesReusableKomikkuTabbedDialogPrimitive"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Komikku Custom Filter Menu Visibility Slice

User direction:

- Continue implementing the Komikku reader backbone without publishing a release while ADB is unavailable.
- Prefer source-level Komikku behavior over another visual knock-off.

Komikku source reference:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`
- The relevant source behavior is the settings dialog `LaunchedEffect(pagerState.currentPage)`: Custom filter hides menus and removes dialog dimming; other tabs show menus again.

Root cause:

- Navic's settings dialog was already tabbed and bounded, but selecting the Custom filter tab did not alter reader chrome visibility.
- That made the filter/customization surface behave like a generic Navic dialog rather than Komikku's reader settings overlay, where the page/filter preview is the focus and top/bottom reader bars are hidden.

Navic implication:

- `ReaderController` now exposes `showMenus()` and `hideMenus()` as controller-owned state transitions. They do not close the active dialog and do not emit engine commands.
- `ReaderCoordinator` forwards those menu visibility transitions through the same controller boundary as the rest of the reader shell.
- `KomikkuReaderSettingsDialog(...)` accepts `onShowMenus` and `onHideMenus`.
- A `LaunchedEffect(pagerState.currentPage)` hides menus when the current tab is `KomikkuSettingsTab.CustomFilter` and shows menus on the other settings tabs.
- `ReaderScreen` wires the dialog callbacks to `coordinator.showMenus()` and `coordinator.hideMenus()`.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.settingsDialogCanHideReaderChromeForKomikkuCustomFilterWithoutClosingDialog" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogHidesChromeOnCustomFilterLikeKomikku"
```

Result: failed before production changes because `hideMenus()` did not exist and the settings dialog had no `onShowMenus`/`onHideMenus` tab effect.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest.settingsDialogCanHideReaderChromeForKomikkuCustomFilterWithoutClosingDialog" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogHidesChromeOnCustomFilterLikeKomikku"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice because ADB currently has no connected device.

## 2026-06-15 Overnight Native Compose Overlay Passive Guardrail

User direction:

- Keep tightening the Komikku reader backbone overnight without publishing another APK.
- Focus on the reader/controller structure rather than more visual-only micro-fixes.

Root cause:

- The Android root already used the Komikku-equivalent native view stack: full-window reader container, full-window viewer container, passive navigation overlay, and full-window Compose overlay.
- The visual tap-zone overlay was explicitly passive at the Android view level, but the full-window `ComposeView` overlay was not. That left an avoidable structural ambiguity: hidden chrome could still sit above the native viewer container without an explicit passive Android view contract.

Navic implication:

- `KomikkuReaderNativeFrameHost.android.kt` now marks the full-window `composeOverlay` as `isClickable = false` and `isFocusable = false`.
- Visible Compose controls still own their own pointer/click handlers. The guardrail is only for the full-window overlay host, so the native viewer container remains the input owner when chrome is hidden or non-interactive.
- This is a structural input-ownership fix. It is not claimed as proof that phone tap/drag behavior is fixed until the morning APK is installed and checked over ADB.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy"
```

Result: failed before production changes because `composeOverlay.isClickable = false` and `composeOverlay.isFocusable = false` were not present in the Android native frame root.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderSurfaceObservesConfirmedTapsAfterChildDispatchLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readableContentTapsAreObservedByNativeSurfaceAfterChildDispatchLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Pre-RC Source And Harness Baseline

User direction:

- Keep working overnight without publishing another Android release candidate.
- In the morning, compile one new release candidate and run the full ADB validation array.
- Use the local browser/WebView-style harness where possible so the morning APK is not the first place obvious reader regressions appear.

Verified source baseline:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
git diff --check
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-settings.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
```

Results: passed on 2026-06-15.

Verified real EPUB/PDF browser harness baseline:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-frontmatter --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-link-jump-drag --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-tap-zone-open --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-shell-cover --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture tmp\reader-live\served-input.epub --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf" --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results: passed on 2026-06-15. The combined `phase1-stabilization` pass completed 16 checks, including real EPUB frontmatter, page boundary, shell cover, native tap-zone open, link-jump drag, texture-scroll/page-turn/frontmatter-transition, full EPUB traversal, and PDF smoke/fast-sequential-turn/image-settings.

Important limitation:

- These checks prove the source contracts and browser-path renderer behavior. They do not close the Android-native acceptance bugs by themselves. The morning release candidate still needs ADB validation over the installed APK for native frame input, cover behavior, texture movement during real phone transitions, EPUB image/link long-press behavior, settings dialogs, and PDF navigation.

Release status:

- No APK was built or published for this baseline.

## 2026-06-15 Overnight Komikku Settings Pager Slice

User direction:

- Keep moving the reader shell toward real Komikku structure, not surface-level styling.
- Avoid publishing another release candidate for source-only UI backbone work.

Investigation note:

- The current branch already routes `chapterPageIndex` and `chapterPageCount` from `navic-reader.js` through `ReaderLocator` into `ReaderChapterProgressState`.
- `KomikkuReaderAppBars(...)` feeds the side rail from `controllerState.chapterProgress`, not directly from whole-book `chrome.pageCount`.
- This means the current source is already materially different from the eta64 complaint that the rail was whole-book-only; phone validation still has to prove the installed APK reflects this source.

Root cause:

- The settings dialog header had been moved to a compact `PrimaryTabRow`, but the content still used local mutable `selectedTab` state and a raw `when (tabs[selectedTab])` content swap.
- Komikku's `TabbedDialog` uses pager state and `HorizontalPager` for tabbed content, so the remaining Navic dialog still had a shortcut architecture.

Navic implication:

- `KomikkuReaderSettingsDialog(...)` now owns a `rememberPagerState(...)` whose initial page maps to the caller's requested tab.
- `KomikkuSettingsTabRow(...)` selects `pagerState.currentPage`.
- Selecting a tab calls `pagerState.animateScrollToPage(index)`.
- Settings content is rendered inside `HorizontalPager(...)` with `when (tabs[page])`.
- The existing Reading, General, PDF/Image, and Custom filter rows were preserved; this slice changes the tab/content ownership model, not the settings catalog.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuTabbedPagerContent"
```

Result: failed before production changes because the dialog had no `rememberPagerState(...)`, no `HorizontalPager(...)`, still used `var selectedTab by remember`, and swapped content through `when (tabs[selectedTab])`.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuTabbedPagerContent"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderShellUsesKomikkuEquivalentOverlayStack" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuTabbedPagerContent" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsUseKomikkuStyleChipGroups"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Komikku Bounded Settings Dialog Slice

User direction:

- Continue source-level Komikku reader work overnight without publishing another release.
- Avoid adding more docked/mobile-settings-page behavior to the reader.
- Use the actual Komikku reader implementation as the source reference where possible.

Komikku source reference:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`
- The relevant source pattern is `BoxWithConstraints { TabbedDialog(modifier = Modifier.heightIn(max = maxHeight * 0.75f), ...) { page -> Column(... verticalScroll(...)) { ... } } }`.

Root cause:

- Navic had already moved the settings dialog to a compact tab row and `HorizontalPager`, but the dialog surface was still hand-built as an unconstrained fixed-width surface.
- The pager page body was not independently scrollable. That left the settings overlay vulnerable to growing into a page-like panel again as more reader controls were added.

Navic implication:

- `KomikkuReaderSettingsDialog(...)` now wraps the dialog surface in `BoxWithConstraints`.
- The settings surface is capped with `Modifier.heightIn(max = maxHeight * 0.75f)`, matching Komikku's reader settings viewport limit.
- Pager pages now wrap their page content in a scrollable `Column` using the shared `TabbedDialogPaddingsVertical` padding constant.
- The old `Modifier.animateContentSize()` pager body was removed so tab content is bounded and scrolls internally instead of pushing the reader overlay toward a docked settings page.
- Existing Reading, General, PDF/Image, and Custom filter controls are preserved.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract"
```

Result: failed before production changes because the dialog had no `BoxWithConstraints`, no 75% viewport cap, no per-page scroll wrapper, and still used `Modifier.animateContentSize()` for pager content.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderShellUsesKomikkuEquivalentOverlayStack" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuTabbedPagerContent" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsUseKomikkuStyleChipGroups"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Komikku Contents Lazy List Slice

User direction:

- Keep replacing shortcut reader UI with Komikku-derived structure.
- Avoid publishing another APK until the morning RC.
- Preserve ebook navigation behavior while improving the controller-owned shell.

Komikku source reference:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/ChapterListDialog.kt`
- The relevant source pattern is `rememberLazyListState(...)` plus a bounded `LazyColumn(modifier = Modifier.heightIn(min = 200.dp, max = 500.dp), ...)`.

Root cause:

- Navic's contents dialog still eagerly composed every TOC item inside a scrollable `Column`.
- Komikku's chapter selector is a bounded lazy list, which is the right primitive for large chapter/contents collections and keeps the dialog from becoming a static alert full of reader rows.

Navic implication:

- `KomikkuReaderContentsDialog(...)` now owns a `rememberLazyListState()`.
- Non-empty TOC entries render through `LazyColumn(...)` with `Modifier.heightIn(min = 200.dp, max = 500.dp)` and vertical content padding.
- TOC item click behavior and indentation are preserved.
- Empty TOCs still render the existing `No table of contents available` message.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentsDialogUsesKomikkuLazyChapterListContract"
```

Result: failed before production changes because the contents dialog did not use `rememberLazyListState(...)`, `LazyColumn(...)`, bounded `heightIn(min = 200.dp, max = 500.dp)`, keyed `items(...)`, and still used `.verticalScroll(rememberScrollState())`.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentsDialogUsesKomikkuLazyChapterListContract"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderShellUsesKomikkuEquivalentOverlayStack" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentsDialogUsesKomikkuLazyChapterListContract" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Komikku Custom Filter RGBA Slice

User direction:

- Continue moving the reader toward a Komikku-derived controller/frontend backbone without publishing another APK overnight.
- Recycle proven Komikku behavior where it fits Navic's current reader shell.
- Avoid fake implementations that look like Komikku but do not respect the original ownership model.

Komikku source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ColorFilterPage.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`

Root cause:

- Navic's Custom filter tab existed, but it only exposed `Dim overlay` and `Publisher styles`.
- Komikku's equivalent page includes a controller-owned color-filter path: enable/disable, RGBA value controls, and a blend-mode selector.
- Navic already had a full-window Compose overlay primitive equivalent to Komikku's `ReaderContentOverlay`, but the color channel was hard-coded to `null`.

Navic implication:

- `ReaderSettings` now carries `colorFilterEnabled`, `colorFilterArgb`, and `colorFilterMode`.
- Global and per-book reader settings persist and restore those fields.
- Reader bridge serialization includes those fields so settings remain complete across engine commands.
- `KomikkuReaderContentOverlay(...)` now receives a decoded Compose `Color` and a mapped Compose `BlendMode`.
- The Custom filter tab now exposes `Color filter`, Red/Green/Blue/Alpha channel steppers, and a mode chip row with Default, Multiply, Screen, Overlay, Lighten, and Darken.

Deliberate limitation:

- Komikku's grayscale and inverted-colors toggles are not ported in this slice. In Komikku those use an Android layer paint on the viewer container, not a top-level tint. Porting them correctly belongs to the native viewer-frame work, not a fake overlay.

Fresh red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepKomikkuColorFilterModel" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripKomikkuColorFilter" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Result: failed before production changes because the settings fields, preferences, bridge serialization, overlay hook, and Custom filter controls did not exist.

Focused and wider green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepKomikkuColorFilterModel" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripKomikkuColorFilter" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderCoordinatorTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest"
git diff --check
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Overnight Komikku Tapping Inversion Slice

User direction:

- Continue source-level Komikku reader backbone work overnight without publishing another APK.
- Recycle proven Komikku behavior rather than adding another Navic-only tap workaround.

Komikku source references:

- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`
- `tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt`

Root cause:

- Navic already had the ported Komikku `KomikkuTappingInvertMode` geometry model, but it was dormant.
- `ReaderSettings` had no persisted tap-inversion field, the reader settings sheet did not expose Komikku's inversion chips, and `komikkuNavigatorForReaderSettings(...)` never assigned `navigation.invertMode`.

Navic implication:

- `ReaderSettings` now carries `tapZoneInvertMode` with supported values `none`, `horizontal`, `vertical`, and `both`.
- Global preferences, per-book reader overrides, and bridge serialization persist the setting.
- The in-reader Reading tab exposes a Komikku-order `Tapping inversion` chip row when tap zones are not disabled.
- Settings > Ebooks and settings search expose the same app-level default.
- `komikkuNavigatorForReaderSettings(...)` maps the setting to the ported `KomikkuTappingInvertMode`, so region mirroring is handled by the Komikku navigation model instead of a Navic tap-action shortcut.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderReadingTabPortsKomikkuTappingInversionControl" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerUsesPortedKomikkuNavigationModelAsInputAuthority" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepValidTapZonePresets" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripTapZonePreference" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Result: failed before production changes because `tapZoneInvertMode`, `ReaderTapZoneInvert*`, `readerTapZoneInvertMode`, settings UI/search rows, and navigator wiring did not exist.

Focused green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderReadingTabPortsKomikkuTappingInversionControl" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerUsesPortedKomikkuNavigationModelAsInputAuthority" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepValidTapZonePresets" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripTapZonePreference" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.portedKomikkuNavigationMirrorsTapRegionsWhenInverted" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderReadingTabPortsKomikkuTappingInversionControl" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerUsesPortedKomikkuNavigationModelAsInputAuthority" --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepValidTapZonePresets" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripTapZonePreference" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge"
```

Results: passed on 2026-06-15.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Drag Cancels Pending Long-Tap Slice

User-visible bug:

- Dragging across EPUB image/content pages could degrade into native long-press behavior instead of staying a reader/page drag.
- Earlier ADB evidence also showed swipe/drag diagnostics and `Reader native long tap` diagnostics in the same gesture, which is the wrong Komikku ownership split.

Root cause:

- `KomikkuReaderNativeViewerContainer` observed move events after child dispatch, but once movement crossed touch slop it did not explicitly disarm the custom `KomikkuGestureDetectorWithLongTap` pending callback.
- In paths where the frame stopped forwarding the stream to the gesture detector after drag/swipe classification, the pending long-tap runnable could survive longer than the gesture's actual classification.

Navic implication:

- `KomikkuGestureDetectorWithLongTap` now exposes `cancelPendingLongTap()`.
- `KomikkuReaderNativeViewerContainer` calls `cancelPendingLongTapForDrag(dx, dy)` as soon as drag motion crosses Android touch slop.
- The cancellation happens before shell-cover swipe dispatch and before readable EPUB/PDF drag delegation, so a drag cannot keep an armed native long-press callback.
- This does not change page-turn semantics. It only tightens the native gesture classifier around Komikku's separation of drag, short tap, and long tap.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe"
```

Result: failed before production changes because there was no explicit `cancelPendingLongTap()` hook and the drag path did not cancel pending long-tap detection before swipe/delegation.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe"
```

Result: passed on 2026-06-15.

Device validation still required:

- Confirm cover drag no longer emits a native long-tap diagnostic.
- Confirm EPUB image-page drag no longer emits native long-tap/content activation while the finger moves.
- Confirm deliberate long press on an image/link still reaches the content long-press path.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Shell Cover Drag Commit-On-Release Slice

User-visible bug:

- Cover drag appeared to discard/pop the cover instead of letting the cover follow the finger.
- The user could tap the cover, but dragging did not feel like a Komikku-style page gesture.

Root cause:

- `KomikkuReaderNativeViewerContainer.handleSwipeTouchEvent(...)` handled `ACTION_MOVE` and `ACTION_UP` in the same branch.
- As soon as a shell-cover move crossed touch slop, the branch called `dispatchHorizontalSwipeViewerAction(...)`, which emitted the page-enter command during movement.
- That made the shell cover disappear before the user could perceive drag-follow feedback.

Navic implication:

- Shell-cover `ACTION_MOVE` now only cancels pending long-tap detection, logs drag diagnostics, and calls `updateShellCoverDragOffset(dx)`.
- Shell-cover `ACTION_UP` now keeps the final offset and then calls `dispatchHorizontalSwipeViewerAction(...)`.
- Readable EPUB/PDF `ACTION_MOVE` remains delegated to the active renderer; this slice does not reintroduce native readable-page swipe shortcuts.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverDragFollowsMoveAndDispatchesPageTurnOnlyOnRelease"
```

Result: failed before production changes because shell-cover swipe dispatch still happened inside the move path. The first red assertion also exposed an overly broad test parser that matched `onInterceptTouchEvent`; the test was narrowed to `handleSwipeTouchEvent(...)` before the green check.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverDragFollowsMoveAndDispatchesPageTurnOnlyOnRelease" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe"
```

Result: passed on 2026-06-15.

Device validation still required:

- Confirm cover drag visibly follows the finger on the phone before releasing into the book.
- Confirm a cancelled/short cover drag snaps back without entering the EPUB.
- Confirm cover center tap still toggles chrome and does not discard the cover.

Release status:

- No APK was built or published for this slice.

## 2026-06-15 Native Cover Boundary Href Guard Slice

User-visible bug:

- From a later chapter, pressing previous could route back to the native cover instead of the previous chapter/page.
- The observed case was a later chapter page that displayed as a low/local page number, then jumped to the cover boundary.

Root cause:

- `readerShouldReturnToNativeShellCover(...)` treated `progress == null` as acceptable for the cover boundary.
- If Foliate/bridge emitted a chapter-local `pageIndex = 0` for a later chapter and omitted global progress, the controller could mistake that local page zero for the whole-book start.

Navic implication:

- The native-cover return predicate still requires:
  - shell cover URL present;
  - shell cover not already visible;
  - page index at the start;
  - positive page count.
- When global progress is present, it must still be near the start (`<= 0.02`).
- When global progress is missing but an href is present, the href must look like a start/frontmatter boundary (`cover`, `title`, `toc`, `contents`, or `frontmatter`) before previous can show the native cover.
- Later chapter hrefs such as `Hobbit_chap-14.html` no longer qualify just because their chapter-local page index is zero.

Fresh red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryDoesNotTrustLocalPageZeroWhenHrefIsLaterChapter"
```

Result: failed before production changes because `progress = null`, `pageIndex = 0`, and `pageCount > 0` still returned `true` for a later chapter href.

Focused green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryInterceptsPreviousOnlyFromFirstReadablePage" --tests "paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryDoesNotTreatLaterChapterFirstPageAsBookStart" --tests "paige.navic.reader.ReaderChromeStateTest.nativeShellCoverBoundaryDoesNotTrustLocalPageZeroWhenHrefIsLaterChapter" --tests "paige.navic.reader.ReaderCoordinatorTest.previousFromFirstReadablePageReturnsToControllerOwnedShellCoverWithoutFoliateCommand"
```

Result: passed on 2026-06-15.

Device validation still required:

- From Chapter XIV/local page 1 or 2, previous should go to Chapter XIII/previous readable page, not the native cover.
- From the first actual readable/frontmatter page, previous should still return to the native cover.

Release status:

- No APK was built or published for this slice.

## 2026-06-16 eta67 Phone And Host Validation

Installed build:

- `v1.0.11-eta67`, `versionCode=400`.
- User opened the EPUB before ADB validation.

Phone observations:

- User confirmed readable-page dragging now dispatches instead of being ignored.
- User reported the EPUB total changed to `373` pages. ADB logs agree that the runtime now reports `source=pagination-profile`, `profile=373`, `observed=5`, `raw=11/411`, `href=OEBPS/Text/Hobbit_chap-1.html`.
- This is a major improvement over the prior bad first profile (`1/1757` from title/frontmatter sampling), but it is not accepted as stable yet because the host full traversal still proves denominator drift.
- User reported drag preview is still incomplete: dragging changes pages, but the next page is not visible during drag; a black void is visible until the next page loads.
- User reported texture transitions still feel wrong: the texture follows the page, then flashes/intensifies at the side edges during the new-page settle.

ADB evidence:

- Native readable drag logs are present, for example `Reader native drag preview dx=-31...` followed by `Reader native readable swipe action=Right dx=-1072...`.
- Texture scroll logs still show duplicated/boundary scroll updates around frontmatter and chapter transitions, including page/href changes while the texture offset is still being updated.
- The current readable drag preview is still a single-surface translation. It is not equivalent to Komikku's pager because no adjacent page surface is mounted behind the active page.

Host evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Result: failed on 2026-06-16 because the profile denominator is still rebuilt during traversal:

```text
Expected stable page count during full traversal; observed 410, 396, 381, 387, 390, 391
```

Root cause:

- The active pagination profile is replaced whenever the observed chapter count increases.
- The replacement profile estimates unobserved chapter pages from the current section's units-per-page, so frontmatter, image-heavy sections, and prose chapters can all produce different global denominators.
- This makes totals jump even though the bridge reports `pagination-profile`.

Required correction:

- Reject implausible first profiles when they are far outside the raw Foliate page total range.
- Once a plausible profile is active for a viewport/settings fingerprint, do not replace the visible denominator simply because another chapter was observed.
- Continue recording observed chapter math in the cache, but only publish a new visible denominator after a full deterministic profile or a bounded/plausible profile replacement rule.

## 2026-06-16 Pagination Sampling Progress Requirement

User-visible issue:

- Deterministic page profiling can take time on large EPUBs, especially when building or refreshing a profile for a new viewport/font/settings fingerprint.
- The current reader can look idle or broken while profiling because it has no explicit progress state.
- Worse, it can expose unstable intermediate totals as if they were final page counts.

Required behavior:

- When a cached pagination profile exists for the current book + viewport + relevant reader settings, load it immediately and show stable page labels.
- When no cache exists, the reader must enter a visible `measuring pages` state instead of pretending the first sampled total is authoritative.
- The visible state should be subtle and reader-native: a small progress label/bar near the page number or Komikku progress rail, not a blocking modal.
- During measurement, the UI may show current page movement, but global totals should be marked provisional or hidden until the active profile is plausible.
- Sampling progress should report at least observed chapter count and total spine/readable section count when available.
- The final cached fingerprint must include screen dimensions, orientation/spread mode, engine/profile version, and reader settings that affect layout: font source/family, font size, line height, margins, paragraph spacing, publisher-style policy, flow/paged mode, and direction.

Implementation direction:

- Add a bridge event for pagination profile progress, separate from `locationChanged`.
- Native reader state should keep `PaginationProfileStatus`: idle, measuring, cached, or ready.
- The page-count label should prefer cached/ready totals; if status is measuring, show progress feedback and avoid locking the UI to unstable totals.
- The existing full-traversal harness must verify both stable published totals and progress/cache events.

Texture source note:

- `C:\Users\darka\Pictures\Resource Boy - Kraft Paper Textures\05.jpg` is a better page texture candidate than the generated transparent pore layers: it has real paper fiber/noise and no checkerboard artifact.
- The original file is too large to ship directly (`37 MB`). If used, generate a reader-sized derived asset and use runtime variants from mirror-X/mirror-Y/both rather than bundling several full-resolution originals.

Texture crop-pack direction:

- `05.jpg` is `10048 x 14185`, close to a natural portrait paper ratio.
- Instead of one global downsample, treat it as a high-resolution source sheet and cut it into same-ratio page crops.
- A 3x3 source grid would create nine distinct `3349 x 4728` crops with different fibers, pores, stains, and edge density.
- Each crop can then produce four deterministic runtime variants: normal, mirrored horizontally, mirrored vertically, and mirrored both axes.
- This gives `9 x 2 x 2 = 36` visible page variants from one source image while preserving deterministic page-to-texture assignment.
- The shipped assets should still be reader-sized derived files, not the original full-resolution JPEG. The pipeline should crop first, then resize/compress each crop to the reader texture target.
- Prefer neutral grayscale/luminance-derived texture assets so the theme background controls paper color. The texture should add fiber and border wear without fighting Sepia/Dark/Light themes.
- Preserve believable pore/fiber scale. The crop pipeline must not downsample so aggressively that paper fibers merge into large blobs, and it must not upscale small regions until pores read like oversized spots. Validate texture scale against a phone-sized reader screenshot before shipping a release.

## 2026-06-16 Complete Pagination Profile Guard

Problem:

- Host full traversal could become internally stable while still using the wrong complete-profile result.
- Fresh instrumentation showed the hidden profiler built a complete `392` page profile, then the visible relocation profile rebuilt the same fingerprint as `391`.
- That explains phone-visible totals changing across apparently equivalent reader states: a complete profile was not authoritative.

Red check:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

First result after adding the stricter trace assertion:

```text
Expected page labels to use complete pagination profile count 392; observed 391 at 0/391
```

Code changes:

- `tools/reader-harness/src/run-reader-harness.mjs` now persists `pagination-profile:*` trace events in the full traversal result.
- `tools/reader-harness/src/reader-trace-assertions.mjs` now fails if a complete pagination profile is later replaced by another complete profile with a different total for the same fingerprint.
- `composeApp/src/androidMain/assets/reader/navic-reader.js` now treats a complete profile (`estimatedChapterCount == 0`) as authoritative for its render fingerprint.
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js` now guards deferred background replacement when Foliate has already destroyed the current view. This removes the host `docBackground` page error caused by the hidden profiler teardown.

Green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\paginator.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Result:

- Syntax checks passed.
- Full traversal passed with stable `392` pages.
- Trace summary: initial partial update `410`, complete update `392`, later visible fresh profiles retained as `freshPageCount = 391` instead of replacing the complete profile.

Release status:

- No release was built or published. This is a meaningful local fix, but phone validation is deferred until the next release candidate.

## 2026-06-16 Paper Texture Crop Pack

Problem:

- eta67 phone validation showed the paper texture was almost invisible.
- The previous base textures were tiny generated transparent overlays (`paper-texture-1.png` etc.), which made pores and wrinkles too subtle on device.
- The previous border overlays had average alpha around `7` and max alpha around `40`, so page-edge wear was also easy to miss.

Source decision:

- Used `C:\Users\darka\Pictures\Resource Boy - Kraft Paper Textures\05.jpg` as the source sheet.
- The original source is not copied into the app. It is `10048 x 14185` and too large to ship directly.
- Generated nine neutral reader-sized crops from a 3x3 source grid.
- Runtime mirroring still provides four transforms per crop, so the visible variant space is now `9 x 2 x 2 = 36` base paper variants.

Code changes:

- Added nine generated assets:
  - `paper-textures/paper-texture-01.jpg` through `paper-textures/paper-texture-09.jpg`.
- Replaced the runtime texture list in `navic-reader-helpers.js` with those nine JPG assets.
- Regenerated the four transparent page-border overlay PNGs at `1536 x 2169`, with smoother edge alpha and max alpha capped at `68`.
- Increased Sepia surface paper texture opacity from `0.42` to `0.54`.

Asset stats:

- Base texture crops: `1536 x 2169`, about `708 KB` to `743 KB` each.
- Sampled average luminance: about `219.7` to `221.3`.
- Sampled luminance standard deviation: about `4.56` to `5.17`, enough to expose pores/fibers without turning them into oversized spots.
- Border overlays: `1536 x 2169`, alpha average about `11.47`, max alpha `68`.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderPackagesDeterministicPaperTextureVariants"
```

Result before asset/code changes:

```text
ReaderRuntimePaperSurfaceTest > androidReaderPackagesDeterministicPaperTextureVariants FAILED
```

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderPackagesDeterministicPaperTextureVariants" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderPackagesDeterministicPaperBorderOverlayVariants" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme"
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-page-turns --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results:

- Host paper texture/border tests passed.
- JS helper syntax check passed.
- Browser texture page-turn probe passed and trace confirmed `paper-texture-##.jpg` assets are selected.

Release status:

- No release was built or published. The texture change is ready for the next release candidate, but device validation is deferred.

## 2026-06-16 Pagination Profiling Progress Bridge

Problem:

- The web reader emits pagination profiling progress, but native Kotlin did not decode or surface it.
- On large EPUBs this makes the reader look idle while the deterministic page profile is being measured.
- The visible UI needs a subtle progress signal without blocking reading or resizing the content.

Code changes:

- Added `ReaderPaginationProfileStatus` to the bridge protocol with status, fingerprint, completed/total section counts, page count, message, label, and progress fraction.
- Added `ReaderBridgeEvent.PaginationProfileStatusChanged` and decoded the JavaScript `paginationProfileStatus` bridge event.
- Mapped the bridge event through `FoliateWebViewEngineAdapter` into `ReaderEngineEvent.PaginationProfileStatusChanged`.
- Stored the latest profile status in `ReaderControllerState.paginationProfile` and reset it when opening a new publication.
- Added Android host logging for `paginationProfileStatus(...)` bridge events.
- Added `KomikkuPaginationProfileStatusBadge(...)` to the reader overlay. It appears only while measuring or failed, uses `LinearProgressIndicator`, and floats over the content without changing reader layout.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodePaginationProfileStatus" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome" --tests "paige.navic.reader.ReaderControllerTest.paginationProfileStatusFeedsControllerStateWithoutNavigationCommands" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderShowsPaginationProfilingStatusInKomikkuOverlay"
```

Initial result:

```text
Unresolved reference 'ReaderPaginationProfileStatus'
Unresolved reference 'PaginationProfileStatusChanged'
Unresolved reference 'paginationProfile'
```

Green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderBridgeProtocolTest.bridgeEventsDecodePaginationProfileStatus" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome" --tests "paige.navic.reader.ReaderControllerTest.paginationProfileStatusFeedsControllerStateWithoutNavigationCommands" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidReaderShowsPaginationProfilingStatusInKomikkuOverlay"
```

Result:

- Focused bridge, engine, controller, and reader-shell tests passed.
- No release was built or published. This is local-only and should be included in the next meaningful release candidate with device validation.

## 2026-06-16 Final Host Snapshot Before Device Validation

Scope:

- User deferred phone/device validation until tomorrow.
- No release was built or published after eta67.
- The purpose of this snapshot is to preserve what is locally verified before the next release candidate.

Host checks:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-page-turns --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results:

- Full EPUB traversal passed and stayed on the deterministic `392` page profile.
- Texture page-turn probe passed with the new JPG crop texture pack selected by the runtime.
- Tomorrow's device release candidate should validate the same areas on the phone: profiling status badge visibility, page-count stability, texture visibility, texture motion, drag preview, cover behavior, and Komikku menu behavior.

## 2026-06-16 Native Short-Tap Ownership Slice

Problem:

- The native Komikku frame still let WebView content veto short menu-zone taps by calling `readerContentActionAtPoint(...)` before opening reader chrome.
- That preserved the old ownership leak where EPUB links/images could decide whether the global menu appears.
- The accepted direction is stricter: short taps and drags belong to the native reader container; renderer content activation belongs to deliberate long press or a future explicit content-interaction mode.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderShortTapRegionsNeverQueryRuntimeContentHitTesting"
```

Initial result:

```text
ReaderRuntimeImageLinkTest > androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress FAILED
ReaderRuntimeImageLinkTest > androidReaderShortTapRegionsNeverQueryRuntimeContentHitTesting FAILED
```

Code changes:

- Removed the native-frame `findReaderWebView()` / `dispatchMenuActionAfterContentHitTest(...)` path.
- Removed `WebView` dependency from `KomikkuReaderNativeFrameHost.android.kt`.
- `dispatchSingleTapAction(...)` now dispatches `MENU`, `NEXT`, `PREV`, `LEFT`, and `RIGHT` directly through native viewer/controller actions.
- The existing native long-press path still calls `onContentLongPress(event.x, event.y, width, height)`, which routes into `ReaderViewerAction.ContentLongPressAt` and then through the engine bridge.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsShortTapMenuNativeAndLeavesContentHitTestingForLongPress" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderShortTapRegionsNeverQueryRuntimeContentHitTesting"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderSuppressesOrdinaryContentClicksWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsContextMenuContentActionsWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsNativeCoordinateLongPressContentActionsWhenNativeTapZonesOwnShortTaps" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderKeepsPointerAndMouseContentClaimsBehindNativeGuard"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
```

Results:

- Focused short-tap ownership tests passed.
- Long-press/content-action adjacent tests passed.
- Full `ReaderRuntimeImageLinkTest` host class passed.

Release status:

- No APK was built or published. Device validation is still required tomorrow, especially for links/images, center-tap menu reliability, and long-press content activation.

## 2026-06-16 Renderer-Driven Readable Drag Preview Slice

Problem:

- eta67 fixed native drag ownership enough for page turns to commit, but the visual drag preview still exposed a black void.
- Root cause: `KomikkuReaderNativeFrameHost.android.kt` moved the entire Android WebView container with `viewerContentContainer.translationX = deltaX`.
- Moving the whole WebView cannot reveal Foliate's adjacent paginated columns; it only reveals the native parent background.
- Komikku's manga reader avoids this class because its pager keeps adjacent pages mounted during the gesture.

Design decision:

- Keep native gesture ownership.
- Do not let Foliate own the touch stream directly.
- Route readable drag preview deltas through the reader controller/engine/bridge as a typed command, then let the Foliate runtime move its own paginator using `renderer.scrollBy(...)`.
- Keep shell-cover dragging native, because the shell cover is a native bitmap surface and not a Foliate page.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack"
```

Initial result:

```text
ReaderKomikkuBackboneResetTest > readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack FAILED
```

Code changes:

- Added `ReaderPageDragPreviewPhase` with `Update`, `Release`, and `Cancel`.
- Added `ReaderViewerAction.PreviewPageDrag`, `ReaderEngineCommand.PreviewPageDrag`, and `ReaderBridgeCommand.PreviewPageDrag`.
- Added `onReadableDragPreview(...)` to `KomikkuReaderNativeFrameHost`.
- Replaced readable `viewerContentContainer.translationX` with preview callbacks.
- Native move emits `Update`; release emits `Release` before the normal page-turn command; cancel/pointer interruption emits `Cancel`.
- `navic-reader.js` now handles `previewPageDrag` and applies absolute native deltas incrementally through `renderer.scrollBy(-incrementalDeltaX, 0)`.
- JS cancel unwinds the last preview delta with `renderer.scrollBy(previousDeltaX, 0)`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack" --tests "paige.navic.reader.ReaderBridgeProtocolTest.pageDragPreviewCommandDispatchesRendererPreviewIntent" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest.dispatchesTypedViewportScrollAsRendererScrollCommand" --tests "paige.navic.reader.ReaderControllerTest.viewerReadableDragPreviewIsControllerOwnedAndForwardedAsEngineCapability"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsReadableDragPreviewWhileNativeFrameOwnsTaps" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverDragFollowsMoveAndDispatchesPageTurnOnlyOnRelease" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeAndNativeReadableDragPreview"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Results:

- Fresh focused host tests passed with `--rerun-tasks`.
- Adjacent native-frame drag/tap tests passed with `--rerun-tasks`.
- Full `ReaderRuntimeShellProgressTest` host class passed with `--rerun-tasks` after updating the stale menu-hit-test assertion to the native-owned short-tap rule.
- JS syntax check passed.

Release status:

- No APK was built or published.
- Tomorrow's device validation must verify whether Foliate renderer preview removes the black void and whether release/cancel phases avoid page jumps or double-advances under real Android WebView timing.

## 2026-06-16 Native Drag Preview Texture Direction Slice

Problem:

- eta67 device feedback said dragging was functional again, but the paper texture still felt unstable: it followed the page during drag, then pulsed or shifted oddly as the new page loaded.
- The new native-readable-drag preview path sends `previewPageDrag` through the controller/engine bridge and moves Foliate with `renderer.scrollBy(...)`.
- The older texture direction detector was attached inside EPUB documents, so native-owned drags could bypass the document `touchmove` tracker and leave texture motion using stale or directionless Foliate coordinates.

Design decision:

- Keep native reader input as the owner of short taps and drags.
- Do not return drag ownership to the WebView.
- Make the bridge drag-preview command seed the same sticky paper-texture direction used by explicit `nextPage` / `previousPage` commands and document drag detection.
- Clear the sticky direction when a drag preview is canceled after unwinding the preview scroll.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSeedsTextureTurnDirectionFromNativeReadableDragPreview"
```

Initial result:

```text
ReaderRuntimePaperSurfaceTest > androidReaderSeedsTextureTurnDirectionFromNativeReadableDragPreview FAILED
```

Code changes:

- `previewPageDrag(command)` now calls `readerPaperTextureDragDirection(...)` before moving Foliate.
- When a logical direction is detected, `surfacePaperTextureTurnDirection` is set to that value before `renderer.scrollBy(...)`.
- The runtime emits `readerTrace('texture:drag-direction', { source: 'native-preview', ... })` so ADB and harness traces can distinguish native-preview direction seeding from document-owned drag tracking.
- Canceling a drag preview clears `surfacePaperTextureTurnDirection` after restoring the preview offset and re-renders the texture layer.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSeedsTextureTurnDirectionFromNativeReadableDragPreview"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Results:

- Targeted native-preview texture-direction test passed.
- Full `ReaderRuntimePaperSurfaceTest` host class passed.
- JS syntax check passed.

Release status:

- No APK was built or published.
- Tomorrow's device validation should specifically check whether native drags now keep texture movement direction stable during the drag and immediately after the committed page loads.

## 2026-06-16 Settings Ownership Cleanup

Decision:

- Keep the page-curl animation sample as a deferred quick-win reference, not an active stabilization task.
- The concrete local sample is `D:\Downloads\Trash\navic_page_curl_toggle_mockup_single_clipped.html`.
- It should be revisited as a future `ReaderViewer` mode enhancement after the Komikku input/chrome/controller backbone is stable: portrait uses the clipped single-page animation model; rotated/spread mode uses the dual-page/spread animation model.
- Move developer diagnostics out of the reader-experience settings surface. `WebView debugging` belongs in `Settings > Developer`, not `Settings > Ebooks`.
- Move `Show tap zones` to `Settings > Developer` as well. It is a diagnostic/training visualizer for validating the native Komikku tap regions, not a normal reader default.
- Reader-facing controls such as tap-zone shape and smaller tap zones remain in Ebooks because they affect real reading interaction behavior.

Follow-up on 2026-06-16:

- The existing visible tap-zone guard was tightened so `option_ebook_reader_show_tap_zones` must not appear in `EbooksScreen.kt`.
- The global `Show tap zones` setting now appears in `DeveloperScreen.kt`, and settings search routes it through `developer.show-tap-zones`.
- The in-reader overlay switch remains available in the active reader menu because it is useful during device validation.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderExposesVisibleTapZoneOverlayControl"
```

Initial result:

```text
ReaderRuntimeSettingsBridgeTest > androidReaderExposesVisibleTapZoneOverlayControl FAILED
```

Green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderExposesVisibleTapZoneOverlayControl"
```

Result:

- Targeted host test passed after the Developer Options move.

Follow-up verification on 2026-06-16:

- `WebView debugging` and global `Show tap zones` remain Developer Options controls, not Ebooks controls.
- The in-reader `Show tap zones` overlay row now lives in `ReaderSettingsDialog.kt` after the Komikku settings-dialog extraction, so the structural guard was retargeted from `ReaderScreen.kt` to the dedicated dialog component.
- Fresh focused verification passed:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderWebViewDebuggingIsControlledByDeveloperSetting" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderExposesVisibleTapZoneOverlayControl"
```

Release status:

- This is a local cleanup/documentation slice only. It does not justify a standalone release candidate.

## 2026-06-16 Komikku Settings Density Slice

Problem:

- The active settings overlay had the right Komikku tabbed-dialog backbone, but the visible density still looked like a docked settings page: large row text, cramped tabs, and truncated `Reading...` / `Custom...` labels.
- Komikku's source uses a reusable `TabbedDialog`, compact `TabText`, and `TabbedDialogPaddings.Vertical = 8.dp`; Navic was still using larger local padding and full tab labels in the visible tab strip.

Decision:

- Keep the existing controller-owned `KomikkuReaderSettingsDialog` and `KomikkuTabbedDialog` structure.
- Do not touch WebView/Foliate runtime, pagination, input ownership, texture movement, or APK release flow in this slice.
- Make the settings overlay denser and less truncation-prone:
  - page vertical padding now matches Komikku's `8.dp`;
  - visible tab labels use compact labels (`Reading`, `General`, `PDF/Image`, `Filter`) while the enum keeps full semantic labels;
  - tab typography uses `labelMedium`;
  - dialog width is wider (`0.96f` on narrow screens, `0.72f` on wide screens);
  - dialog shell padding and row typography are reduced so the visible page reads like a temporary reader overlay instead of a full settings screen.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndCompactTabLabels"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndCompactTabLabels FAILED
```

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndCompactTabLabels"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Targeted density guard passed.
- Full `ReaderRuntimeCommonChromeTest` passed after aligning stale width/tab expectations to the new compact contract.

Release status:

- No APK was built or published. This is useful UI progress, but it is not a standalone major bug fix.

## 2026-06-16 Komikku Vertical Progress Rail Visual Slice

Problem:

- The right-side progress rail was controller-owned and chapter-local, but it still looked like a rotated Material slider.
- User feedback after eta67 said the vertical progress bar felt slightly too short and still looked like a knock-off of Komikku rather than a copied reader rail behavior.

Decision:

- Keep the existing controller/chapter seek path.
- Do not move progress back into the WebView or bottom chrome.
- Replace the visible vertical rail with a custom Komikku-like pill/dot/handle layer while leaving an almost-transparent Slider underneath to preserve the already-tested seek and haptic behavior.
- Increase the vertical rail height fraction from `0.78f` to `0.82f`; this makes the rail a little more useful without letting the previous/next buttons sit under the top or bottom bars.
- Bottom navigation can keep the shared horizontal slider for now; this slice only changes the vertical overlay rail.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderVerticalProgressRailUsesKomikkuDottedRailVisuals"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderVerticalProgressRailUsesKomikkuDottedRailVisuals FAILED
```

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderVerticalProgressRailUsesKomikkuDottedRailVisuals" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused vertical rail tests passed.
- Full `ReaderRuntimeCommonChromeTest` passed.

Release status:

- No APK was built or published. This is local Komikku shell progress, not a standalone release candidate.

## 2026-06-16 Komikku Chapter Navigator Component Boundary Slice

Problem:

- The reader shell had started copying Komikku's chapter/progress navigator behavior, but the implementation still lived inline in `ReaderScreen.kt`.
- Komikku keeps the chapter navigator as a separate `ChapterNavigator.kt` component, so leaving the rail, slider, RTL logic, haptic behavior, and page labels inside `ReaderScreen` kept Navic's reader shell structurally closer to the old monolithic implementation.

Decision:

- Extract Navic's Komikku chapter/progress navigator into `ReaderChapterNavigator.kt`.
- Keep the behavior unchanged in this slice:
  - `ReaderScreen.kt` still decides where the rail appears and passes controller actions.
  - `KomikkuChapterNavigator` still owns horizontal/vertical navigator rendering, RTL inversion, haptic drag feedback, dotted vertical rail rendering, and page-index conversion.
  - `KomikkuReaderVerticalRailHeightFraction` stays at `0.82f`, now owned by the navigator component.
- Do not touch WebView/Foliate runtime, pagination math, drag behavior, paper textures, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile FAILED
```

Code changes:

- Added `readerCommonUiFile(...)` host-test fixture helper for source guards against common reader UI components.
- Added `ReaderChapterNavigator.kt` under `paige.navic.ui.screens.reader`.
- Moved `KomikkuChapterNavigator`, `KomikkuChapterNavigatorVertical`, `KomikkuVerticalChapterProgressRail`, `KomikkuChapterProgressSlider`, and `KomikkuReaderVerticalRailHeightFraction` out of `ReaderScreen.kt`.
- Retargeted existing side-rail, dotted-rail, and RTL source guards so implementation assertions read `ReaderChapterNavigator.kt`; `ReaderScreen.kt` remains checked only for call-site placement and controller wiring.

Green check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderVerticalProgressRailUsesKomikkuDottedRailVisuals" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndCompactTabLabels"
```

Result:

- Focused chapter navigator boundary and rail tests passed.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 eta68 Progress Rail Overlay Diagnosis And Local Fix

Device observation:

- Installed build: `v1.0.11-eta68`, `versionCode = 401`.
- Device `R52W60CFTRL` was manually unlocked and left on an EPUB reader surface.
- ADB center tap (`adb shell input tap 1480 924`) was handled by the native reader layer:

```text
KomikkuReaderNativeFrameHost: Reader native tap action=MENU x=1480.0 y=924.0 width=2960 height=1848
```

- Capture `captures\reader-eta68\unlocked-matrix\01-center-tap.png` showed the menu toggled, but the side progress rail was rendered as a large centered/mobile overlay instead of a Komikku-style side rail.

Root cause:

- `ReaderAppBars.kt` mounted the top bar, progress rail, and bottom bar inside a weighted vertical layout.
- That let the middle rail slot become the dominant centered content rather than an independent full-window side overlay.
- This is a native chrome layout bug, not a WebView/Foliate pagination bug.

Red checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuAppBarsOwnSideNavigatorAsIndependentOverlaySibling"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderChromeUsesKomikkuEquivalentSideProgressRail FAILED
ReaderKomikkuBackboneResetTest > komikkuAppBarsOwnSideNavigatorAsIndependentOverlaySibling FAILED
```

Code changes:

- Changed `KomikkuReaderAppBars` to use `Box(modifier = modifier.fillMaxSize())` as the root overlay surface.
- Anchored top chrome with `Modifier.align(Alignment.TopCenter)`.
- Anchored side rails with `Modifier.align(Alignment.CenterStart)` and `Modifier.align(Alignment.CenterEnd)`.
- Anchored bottom chrome with `Modifier.align(Alignment.BottomCenter)`.
- Updated source guards so the root app-bars body cannot regress to `Column(modifier = modifier.fillMaxHeight())` or root `.weight(1f)`.
- Narrowed the guard to the root app-bars body so legitimate title-row weights inside `KomikkuReaderTopBar` are not falsely rejected.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuAppBarsOwnSideNavigatorAsIndependentOverlaySibling"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results:

- Full `ReaderRuntimeCommonChromeTest`: passed.
- Focused Komikku app-bars overlay sibling reset test: passed.
- Full real-EPUB traversal harness: passed with stable `392` pages.
- Full `ReaderKomikkuBackboneResetTest`: still red by design, with 19 of 38 tests failing for broader unfinished Komikku backbone acceptance items including native frame hierarchy, long-press-only WebView input, drag-preview renderer ownership, settings preset controls, and active viewer abstraction.

Release status:

- No APK was built or published for this local fix yet.
- eta68 on-device remains useful as evidence of the bug, but it cannot validate this fix until the next Android APK is built and installed.

## 2026-06-16 Pagination Provisional Total Suppression Slice

Problem:

- The complete pagination profiler can take long enough that visible relocation events fire while the hidden profiler is still measuring.
- Before this slice, those relocation events could build and publish a partial estimated profile for the same fingerprint.
- In the real Hobbit EPUB harness, the visible reader published a provisional `410` page total before the complete deterministic profile reported `392`.
- That violates the current requirement: while measuring, the reader may show progress, but it must not expose unstable intermediate totals as authoritative page counts.

Red check:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Initial result after adding the stricter trace assertion:

```text
Expected pagination measurement not to publish provisional totals before the complete profile; observed provisional 410
```

Code changes:

- Added `paginationProfileMeasurementInProgress` to the WebView runtime state.
- Added `isCompletePaginationProfile(...)` as the single completeness predicate: a profile is complete only when it has chapters and `estimatedChapterCount == 0`.
- While the full hidden profiler is measuring, visible relocation profile building may still record observations, but it no longer promotes incomplete profiles into `paginationProfile` or the localStorage cache.
- Complete cached profiles and complete profiler results remain authoritative and still update page labels.
- Added a harness assertion that fails when any incomplete `pagination-profile:updated` event publishes a finite page total before the first complete profile update.

Green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-full-traversal --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Results:

- Syntax checks passed.
- Full real-EPUB traversal passed with stable `392` pages.
- The stricter trace assertion no longer observed a provisional page-count publication before the complete profile.

Release status:

- No APK was built or published in this slice. This fix is source-verified and needs the next Android release candidate before phone validation.

ADB retry status for installed `v1.0.11-eta68`:

- Device `R52W60CFTRL` became usable briefly with Navic focused and keyguard hidden.
- Baseline capture `captures\reader-eta68\retry-opened\screen.png` showed the reader cover in landscape.
- After a center tap, the device entered `Dozing`; the next screenshot was black and `dumpsys window` returned `NotificationShade` / `isKeyguardShowing=true`.
- `adb shell svc power stayon true` successfully set `mHoldingDisplaySuspendBlocker=true`, but waking the device reached Android `Bouncer`, so secure unlock is still required.
- No further tap/drag reader matrix results are valid until the tablet is manually unlocked again and left on the reader surface.

## 2026-06-16 Reader Settings Session Boundary Slice

Problem:

- `ReaderScreen.kt` still owned reader settings persistence and scope switching directly after the Komikku shell/root extractions.
- That kept global-vs-book preference lookup, book override creation, normalized persistence, and reset-to-global behavior inside the screen entry point.
- The Komikku port needs `ReaderScreen.kt` to be an app-boundary coordinator, not the settings-store authority. The settings session logic must live behind a small boundary before the dialog/controller can be tightened further.

Decision:

- Extract reader settings scope/session helpers into `ReaderSettingsSession.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still chooses the initial settings scope and passes `ReaderSettings` into the controller/runtime.
  - `ReaderSettingsSession.kt` owns `readerHasBookSettings`, `readerInitialSettingsScope`, scoped settings lookup, scoped persistence, selected-scope switching, and reset-to-global behavior.
  - Existing `PreferenceManager` reader settings APIs stay in place behind the session boundary.
- Do not touch WebView/Foliate runtime, PDF runtime, native input dispatch, drag animation, page-count profiling, paper textures, or release behavior in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsStoreRulesLiveOutsideReaderScreen"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderSettingsStoreRulesLiveOutsideReaderScreen FAILED
```

Code changes:

- Added `ReaderSettingsSession.kt` under `paige.navic.ui.screens.reader`.
- Moved direct global/book settings-store decisions out of `ReaderScreen.kt`.
- Updated source guards so `ReaderScreen.kt` is checked for using the session boundary while `ReaderSettingsSession.kt` is checked for the actual preference-store API usage.
- Retargeted the default-settings remember-key guard so it no longer requires the screen file to call `readerSettingsForBook(...)` directly.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsStoreRulesLiveOutsideReaderScreen" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsSupportKomikkuStylePerBookSettingsScope"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused settings-session/per-book-scope checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed after the default-settings guard was retargeted to the new session owner.

Release candidate:

- The user connected another Android device and explicitly requested a new release/install/test pass.
- Next Android release candidate: `v1.0.11-eta68`, `versionCode = 401`.
- eta68 must be installed on the connected device and validated with the ADB reader matrix before its behavior is described as improved.

## 2026-06-16 eta68 Release And Device Validation Attempt

Release:

- Commit: `9030c287` (`Advance Komikku reader backbone candidate`).
- Tag: `v1.0.11-eta68`.
- GitHub release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta68`.
- Android artifact: `Navic.apk`.
- GitHub Actions run: `27605462810`.
- Workflow result: Android APK build succeeded; iOS IPA job was skipped.

Local pre-release checks:

```powershell
git diff --check
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta68
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- `git diff --check`: passed.
- Android version guard: passed, `versionName = v1.0.11-eta68`.
- JS syntax checks: passed.
- `ReaderRuntimeCommonChromeTest`: passed.

Device:

- Connected device: `R52W60CFTRL`, model `SM_X910`.
- APK install command succeeded:

```powershell
adb install -r releases\v1.0.11-eta68\Navic.apk
```

- Installed package verification from ADB smoke capture:

```text
versionCode=401
versionName=v1.0.11-eta68
lastUpdateTime=2026-06-16 11:53:17
```

Validation blocker:

- The device stayed behind Android keyguard/notification shade after the install.
- `dumpsys window` showed:

```text
mCurrentFocus=Window{... u0 NotificationShade}
mFocusedApp=ActivityRecord{... u0 darkaxt.navic/paige.navic.androidApp.MainActivity ...}
isKeyguardShowing=true
```

- ADB attempts made:
  - `adb shell wm dismiss-keyguard`
  - `adb shell cmd statusbar collapse`
  - `adb shell input keyevent KEYCODE_WAKEUP`
  - `adb shell input keyevent KEYCODE_MENU`
  - `adb shell input keyevent KEYCODE_BACK`
  - portrait and landscape unlock swipes
  - `adb shell monkey -p darkaxt.navic 1`
- Result: Navic remained the focused app behind `NotificationShade`, but the app window was `isOnScreen=false`.

Captured artifacts:

- `captures\reader-eta68\baseline-launch`
- `captures\reader-eta68\baseline-after-unshade`
- `captures\reader-eta68\baseline-after-unlock-attempts`

Reader validation status:

- The ADB reader matrix was not run because the active screen was not Navic reader content.
- Running tap/drag/texture checks while keyguard is visible would produce false failures and hide the actual reader behavior.
- Next valid step: unlock the tablet and leave Navic open on an EPUB reader page, then run the matrix against `v1.0.11-eta68` without relaunching into an invalid screen.

Fresh ADB re-check after the user connected the tablet:

- `adb devices -l`: connected `R52W60CFTRL`, model `SM_X910`.
- `adb shell dumpsys package darkaxt.navic`: installed `versionCode=401`, `versionName=v1.0.11-eta68`, `lastUpdateTime=2026-06-16 11:53:17`.
- Initial focus remained `NotificationShade` with `isKeyguardShowing=true`.
- Wake/dismiss/statusbar/back did not expose Navic.
- A coordinate unlock swipe changed focus to `Window{... u0 Bouncer}` while `isKeyguardShowing=true`; this confirms the device is at the credential prompt.
- Result: eta68 remains installed, but the reader matrix is still blocked until the tablet is manually unlocked and left in the EPUB reader.

## 2026-06-16 Reader Engine Open Request Boundary Slice

Problem:

- `ReaderScreen.kt` still constructed `ReaderEngineOpenRequest` directly from the route reader, resolved shell-cover URL, saved Bindery progress, and reader settings.
- That kept start-locator precedence, saved-progress lookup, shell-cover availability, publication identity construction, and engine-open fields inside the app screen entry point.
- The Komikku port objective requires renderer capabilities to sit behind controlled adapters; the screen should call a mapper/factory, not own engine-open construction.

Decision:

- Extract the `Screen.Reader.toReaderEngineOpenRequest(...)` mapper into `ReaderOpenRequest.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still calls `reader.toReaderEngineOpenRequest(...)` from publication and readaloud runtime readiness callbacks.
  - `ReaderOpenRequest.kt` owns route locator creation, saved Bindery progress conversion, `bestReaderStartLocator(...)`, `ReaderPublicationIdentity`, `nativeShellCoverUrl`, and `canReturnToShellCover`.
  - The mapper remains package-internal to the reader UI boundary until the `Screen.Reader` route model is separated from engine-open input.
- Do not touch WebView/Foliate runtime, PDF runtime, EPUB page math, drag animations, paper textures, deterministic paging cache, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderEngineOpenRequestFactoryLivesOutsideReaderScreen"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderEngineOpenRequestFactoryLivesOutsideReaderScreen FAILED
```

The expected red failure was missing `ReaderOpenRequest.kt`.

Code changes:

- Added `ReaderOpenRequest.kt` under `paige.navic.ui.screens.reader`.
- Moved `Screen.Reader.toReaderEngineOpenRequest(...)` out of `ReaderScreen.kt`.
- Added a source guard that keeps route/progress/start-locator construction out of `ReaderScreen.kt`.
- Retargeted native shell-cover image/link tests so they check `ReaderOpenRequest.kt` for `nativeShellCoverUrl`/`canReturnToShellCover` and `ReaderRoot.kt` for native frame host wiring.
- Kept the existing call sites in `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderEngineOpenRequestFactoryLivesOutsideReaderScreen" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused open-request/image-link checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku App Bars Component Boundary Slice

Problem:

- The active reader shell already mounted app bars as overlay chrome, but `ReaderScreen.kt` still owned `KomikkuReaderAppBars`, `KomikkuReaderTopBar`, and `KomikkuReaderBottomBar`.
- Komikku keeps reader app bars as dedicated appbar components (`ReaderAppBars.kt`, `ReaderTopBar.kt`, `ReaderBottomBar.kt`), so leaving the top bar, bottom bar, rail placement, animation specs, bookmark action, and bottom action strip inline preserved the old monolithic Navic reader shape.

Decision:

- Extract Navic's Komikku reader app bars into `ReaderAppBars.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still mounts `KomikkuReaderAppBars(...)` from the Compose overlay and passes controller callbacks.
  - `ReaderAppBars.kt` owns the appbar animation specs, top title/bookmark chrome, bottom action strip, nav-bar placement, RTL direction derivation, and chapter navigator placement.
  - `ReaderChapterNavigator.kt` still owns the actual progress rail and slider implementation.
- Do not touch WebView/Foliate runtime, EPUB/PDF pagination, drag behavior, paper textures, settings behavior, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderAppBarsLiveInDedicatedKomikkuComponentFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderAppBarsLiveInDedicatedKomikkuComponentFile FAILED
```

Code changes:

- Added `ReaderAppBars.kt` under `paige.navic.ui.screens.reader`.
- Moved `KomikkuReaderAppBars`, `KomikkuReaderTopBar`, `KomikkuReaderBottomBar`, and the appbar slide/fade animation specs out of `ReaderScreen.kt`.
- Added a local pagination-profile badge fade spec in `ReaderScreen.kt`; the badge remains a screen-local overlay until it gets its own component boundary.
- Retargeted existing chrome source guards so appbar behavior is asserted against `ReaderAppBars.kt`; `ReaderScreen.kt` remains checked for mount placement and full-window overlay ownership.
- Retargeted the chapter navigator boundary guard so `ReaderAppBars.kt` is the component that owns calls to `KomikkuChapterNavigator(...)`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderAppBarsLiveInDedicatedKomikkuComponentFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderShellUsesKomikkuEquivalentOverlayStack" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeUsesKomikkuEquivalentSideProgressRail" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderProgressRailPlacementIsControllerSettingNotHardcoded" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeSeparatesTopPanelFromBottomActions" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused appbar/chrome boundary tests passed.
- Full `ReaderRuntimeCommonChromeTest` passed after retargeting the chapter navigator source guard to the new appbar owner.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku Settings Dialog Component Boundary Slice

Problem:

- The active reader settings overlay had Komikku-like behavior and density, but its implementation still lived inline in `ReaderScreen.kt`.
- That kept `ReaderScreen.kt` responsible for the settings tab model, tabbed dialog shell, scrolling fade, chip rows, switch rows, steppers, reading-mode options, custom filter controls, and PDF/image controls.
- Komikku keeps reader settings as a dedicated overlay component (`ReaderSettingsDialog.kt`), so leaving all of that in the screen file preserved Navic's old monolithic reader shape.

Decision:

- Extract Navic's Komikku reader settings overlay into `ReaderSettingsDialog.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still decides when to show reading-mode/settings dialogs and passes controller callbacks.
  - `ReaderSettingsDialog.kt` owns the tab list, `KomikkuReaderSettingsDialog`, `KomikkuTabbedDialog`, tab row, scroll-edge fade, chip/switch/stepper rows, reading-mode options, PDF/image controls, and custom filter controls.
  - The contents dialog remains in `ReaderScreen.kt` for now; it reuses the now-shared `KomikkuSettingsDialogLine`.
- Do not touch WebView/Foliate runtime, PDF renderer behavior, pagination math, drag behavior, paper textures, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogLivesInDedicatedKomikkuComponentFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderSettingsDialogLivesInDedicatedKomikkuComponentFile FAILED
```

Code changes:

- Added `ReaderSettingsDialog.kt` under `paige.navic.ui.screens.reader`.
- Moved `TabbedDialogPaddingsVertical`, `KomikkuSettingsTab`, `KomikkuReadingModeOptions`, `KomikkuReaderSettingsDialog`, `KomikkuTabbedDialog`, `KomikkuSettingsTabRow`, `KomikkuSettingsDialogPage`, `KomikkuSettingsDialogLine`, `KomikkuSettingsChipRow`, `KomikkuSettingsSwitchRow`, `KomikkuSettingsStepperRow`, scroll-edge fade, and color-filter helper logic out of `ReaderScreen.kt`.
- Retargeted existing settings overlay source guards so dialog behavior is asserted against `ReaderSettingsDialog.kt`; `ReaderScreen.kt` remains checked for call-site wiring, menu show/hide callbacks, and full-window overlay ownership.
- Cleaned settings-only imports from `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogLivesInDedicatedKomikkuComponentFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndCompactTabLabels" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuTabbedPagerContent" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesReusableKomikkuTabbedDialogPrimitive" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogHidesChromeOnCustomFilterLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesResponsiveWidthInsteadOfPlatformDialogCap" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderSettingsDialogUsesScrollEdgeFadeInsteadOfAbruptCutoff"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused settings dialog boundary/density/pager checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed after retargeting older source guards to the new component boundary.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku Contents Dialog Component Boundary Slice

Problem:

- The table-of-contents overlay still lived inline in `ReaderScreen.kt` after the appbar and settings-dialog extractions.
- That kept chapter list rendering, empty-state text, dialog container ownership, and navigation callbacks in the monolithic screen file.
- Komikku keeps chapter-list UI behind dedicated dialog/component boundaries, so the Navic reader shell should not keep contents-dialog implementation in the screen once the Komikku backbone is being rebuilt.

Decision:

- Extract Navic's Komikku contents dialog into `ReaderContentsDialog.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still decides when to show the contents dialog and still passes `ReaderTocItem` callbacks.
  - `ReaderContentsDialog.kt` owns `KomikkuReaderContentsDialog`, the `BasicAlertDialog` shell, lazy TOC list, empty TOC line, and per-item navigation click behavior.
  - `ReaderSettingsDialog.kt` continues to expose the shared compact dialog line used by the empty-state row.
- Do not touch WebView/Foliate runtime, EPUB/PDF pagination, drag behavior, paper textures, deterministic paging cache, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentsDialogLivesInDedicatedKomikkuComponentFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderContentsDialogLivesInDedicatedKomikkuComponentFile FAILED
```

Code changes:

- Added `ReaderContentsDialog.kt` under `paige.navic.ui.screens.reader`.
- Moved `KomikkuReaderContentsDialog` out of `ReaderScreen.kt`.
- Retargeted the existing contents-dialog source guard so lazy list behavior is asserted against `ReaderContentsDialog.kt`; `ReaderScreen.kt` remains checked for call-site ownership only.
- Cleaned contents-dialog-only imports from `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentsDialogLivesInDedicatedKomikkuComponentFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentsDialogUsesKomikkuLazyChapterListContract"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused contents dialog boundary/lazy-list checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed after retargeting the overlay-stack alignment assertion to the extracted appbar owner and matching the actual chained modifier marker.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku Content Overlay Component Boundary Slice

Problem:

- The dim/color-filter overlay was still implemented inline in `ReaderScreen.kt`.
- Komikku treats `ReaderContentOverlay` as an overlay sibling above the viewer, not as screen-local decoration or renderer-owned CSS.
- Leaving the drawing and filter mapping helpers in the screen kept reader chrome/filter responsibilities inside the monolithic root.

Decision:

- Extract Navic's Komikku content/filter overlay into `ReaderContentOverlay.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still mounts `KomikkuReaderContentOverlay(...)` as a full-window overlay via `Modifier.matchParentSize()`.
  - `ReaderContentOverlay.kt` owns the dim overlay, color-filter overlay, blend-mode mapping, and ARGB-to-Compose color conversion.
  - The overlay remains Compose chrome above the viewer; it does not resize content and does not change WebView/PDF runtime behavior.
- Do not touch pagination, gestures, paper textures, reader settings semantics, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentOverlayLivesInDedicatedKomikkuComponentFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderContentOverlayLivesInDedicatedKomikkuComponentFile FAILED
```

Code changes:

- Added `ReaderContentOverlay.kt` under `paige.navic.ui.screens.reader`.
- Moved `KomikkuReaderContentOverlay`, `readerColorFilterColor`, and `readerColorFilterBlendMode` out of `ReaderScreen.kt`.
- Retargeted dim-overlay source guards so the screen is checked for mount placement and the dedicated component is checked for drawing implementation.
- Cleaned content-overlay imports from `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentOverlayLivesInDedicatedKomikkuComponentFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeExposesDimOverlayControl" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused content-overlay/dim/color-filter checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku Pagination Profile Badge Component Boundary Slice

Problem:

- The pagination profiling status badge was still implemented inline in `ReaderScreen.kt`.
- This badge is transient overlay chrome for deterministic page-profile measurement state, not screen root logic.
- The appbar extraction explicitly left this as a screen-local temporary leftover; keeping it there would preserve another reader-overlay responsibility in the monolithic root.

Decision:

- Extract `KomikkuPaginationProfileStatusBadge` into `ReaderPaginationProfileBadge.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still decides where the badge appears and anchors it above/below menu chrome.
  - `ReaderPaginationProfileBadge.kt` owns the fade spec, measuring/failed visibility predicate, label, and progress indicator.
  - The badge remains overlay-only and does not affect viewer size, input ownership, or pagination math.
- Do not touch the pagination profiling algorithm, page counts, drag preview, textures, WebView/PDF runtime, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderPaginationProfileBadgeLivesInDedicatedKomikkuOverlayFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderPaginationProfileBadgeLivesInDedicatedKomikkuOverlayFile FAILED
```

Code changes:

- Added `ReaderPaginationProfileBadge.kt` under `paige.navic.ui.screens.reader`.
- Moved `KomikkuPaginationProfileStatusBadge` and `readerPaginationBadgeFadeAnimationSpec` out of `ReaderScreen.kt`.
- Added a source guard that prevents resurrecting the badge implementation in the screen root.
- Cleaned pagination-badge imports from `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderPaginationProfileBadgeLivesInDedicatedKomikkuOverlayFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderContentOverlayLivesInDedicatedKomikkuComponentFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderChromeExposesDimOverlayControl" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderCustomFilterPortsKomikkuColorFilterControls"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused pagination-badge/content-overlay checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku Reader Navigation Mapping Boundary Slice

Problem:

- `ReaderScreen.kt` still converted Navic `ReaderSettings` tap-zone values into Komikku navigation classes.
- That made the screen root responsible for choosing `KomikkuLNavigation`, `KomikkuKindlishNavigation`, edge navigation, right/left navigation, disabled navigation, smaller-zone behavior, and tapping inversion.
- The spec's ownership rule says the common tap-zone model must route through the ported `KomikkuReaderNavigator`, and old Navic tap helpers must not become the authority again. Keeping the settings-to-Komikku mapping in the screen root made that boundary weaker.

Decision:

- Extract the Navic-settings-to-Komikku-navigation mapping into `ReaderNavigation.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still remembers a navigator from `settings.tapZone`, `settings.tapZoneInvertMode`, `settings.smallerTapZone`, and `settings.flowMode`.
  - `ReaderNavigation.kt` owns `komikkuNavigatorForReaderSettings(...)` and `komikkuTappingInvertMode(...)`.
  - The mapping still resolves default tap-zone mode from flow mode, applies `smallerTapZone`, maps all Komikku tap-zone presets, and applies tapping inversion before returning `KomikkuReaderNavigator`.
- Do not touch Android native input dispatch, WebView/Foliate runtime, PDF runtime, gestures, page counts, textures, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderNavigatorSettingsMappingLivesWithKomikkuNavigationModel"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderNavigatorSettingsMappingLivesWithKomikkuNavigationModel FAILED
```

Code changes:

- Added `ReaderNavigation.kt` under `paige.navic.ui.screens.reader`.
- Moved `komikkuNavigatorForReaderSettings` and `komikkuTappingInvertMode` out of `ReaderScreen.kt`.
- Retargeted the tapping-inversion and Komikku-navigation source guards so `ReaderScreen.kt` is checked only for using the mapper, while `ReaderNavigation.kt` is checked for mapping implementation.
- Cleaned tap-zone/navigation-mapping imports from `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderNavigatorSettingsMappingLivesWithKomikkuNavigationModel" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderReadingTabPortsKomikkuTappingInversionControl" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.newBackboneUsesPortedKomikkuViewerNavigationInsteadOfOnlyNavicTapHelpers"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused navigation mapping/tapping-inversion guards passed.
- Full `ReaderRuntimeCommonChromeTest` passed.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Komikku Reader Root Boundary Slice

Problem:

- `ReaderScreen.kt` still contained the active Komikku shell root after the app bars, dialogs, overlays, badge, and navigation mapper were extracted.
- That meant the screen entry point still owned the retained `ReaderViewerLifecycleSlot`, native frame host mounting, shell-cover title fallback, native shell-cover action remapping, readable drag-preview dispatch, long-press dispatch, viewer host slot, and Compose overlay stack.
- The port objective says the active reader should be a Komikku-derived frontend/controller backbone; leaving the root implementation in `ReaderScreen.kt` kept the screen as a monolithic shell instead of an app-boundary coordinator.

Decision:

- Extract the Komikku shell root into `ReaderRoot.kt`.
- Keep behavior unchanged in this slice:
  - `ReaderScreen.kt` still owns dependency injection, preference/default-setting selection, coordinator step application, runtime host wiring, back-stack navigation, volume-key routing, and the call into `KomikkuReaderRoot(...)`.
  - `ReaderRoot.kt` now owns `KomikkuReaderRoot`, the retained viewer lifecycle slot, `shellCoverTitleFor`, `KomikkuReaderNativeFrameHost`, `ReaderViewerHost`, shell-cover action remapping, drag-preview/long-press viewer actions, and `KomikkuComposeOverlay`.
  - `KomikkuComposeOverlay` continues to mount content dim/filter overlay, app bars, pagination profile badge, contents dialog, and settings dialog as full-window overlay siblings instead of resizing reader content.
- Do not touch WebView/Foliate runtime, PDF runtime, EPUB page math, drag animations, paper textures, deterministic paging cache, or release flow in this slice.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderRootAndComposeOverlayLiveInDedicatedKomikkuShellFile"
```

Initial result:

```text
ReaderRuntimeCommonChromeTest > commonReaderRootAndComposeOverlayLiveInDedicatedKomikkuShellFile FAILED
```

The first attempt to run this check hit the shell tool's default execution cap before the test result was produced, so it was discarded as non-evidence. The rerun produced the expected red failure because `ReaderRoot.kt` did not exist yet.

Code changes:

- Added `ReaderRoot.kt` under `paige.navic.ui.screens.reader`.
- Moved `KomikkuReaderRoot`, `KomikkuComposeOverlay`, and `shellCoverTitleFor` out of `ReaderScreen.kt`.
- Added a source guard that prevents the shell root, shell-cover title mapping, retained viewer slot, native frame host wiring, and overlay stack from being resurrected inside `ReaderScreen.kt`.
- Retargeted older common chrome guards so they check `ReaderRoot.kt` for overlay-stack implementation and `ReaderScreen.kt` only for the app-boundary call site.
- Cleaned stale root/overlay imports from `ReaderScreen.kt`.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderRootAndComposeOverlayLiveInDedicatedKomikkuShellFile" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderShellUsesKomikkuEquivalentOverlayStack"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Results:

- Focused root/overlay-stack checks passed.
- Full `ReaderRuntimeCommonChromeTest` passed.

Release status:

- No APK was built or published. This is a structural Komikku backbone slice, not a standalone user-validation release candidate.

## 2026-06-16 Native Drag Preview Underlay Slice

Problem:

- eta67 fixed native drag ownership enough that readable-page dragging no longer disappeared into pure tap handling.
- The next visible defect was still Komikku-critical: when dragging across a section boundary, the current page moved but the adjacent page was not visible yet. The exposed area showed reader/native background instead of the next page.
- This is not a full page-curl/spread renderer issue. The immediate root cause is that Foliate only has the current section's columns loaded, so `renderer.scrollBy(...)` can expose same-section columns but cannot reveal the next XHTML section at the boundary.

Decision:

- Keep native drag ownership unchanged.
- Keep using Foliate's `renderer.scrollBy(-incrementalDeltaX, 0)` for same-section drag preview.
- Add a clipped adjacent-section preview underlay only when the native drag reaches the loaded section boundary.
- Load the next/previous readable non-cover section into a passive iframe preview strip, theme it, clip it to the exposed side, and remove it on cancel or real relocation.
- This is a pragmatic bridge toward Komikku-quality drag feedback. It removes the black/empty exposed strip, but it is not yet the final page-curl or dual-page spread implementation.

Red check:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-preview-underlay --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
```

Initial result:

```text
Expected native drag preview to mount a clipped adjacent-page underlay; layer=false iframe=false
```

Code changes:

- `navic-reader.js` now tracks a page-drag preview layer/frame/load token.
- `previewPageDrag(...)` still scrolls the active renderer for the live drag offset, then asks `updatePageDragPreviewLayer(...)` whether an adjacent-section strip is needed.
- `currentLoadedSectionIndex(...)` identifies the current loaded section.
- `adjacentReadableSectionIndex(...)` skips suppressed cover-like sections when looking for the adjacent preview section.
- `nativeDragPreviewAtSectionBoundary(...)` limits the underlay to boundary drags, avoiding extra iframe work for ordinary same-section page drags.
- The harness gained `epub-native-drag-preview-underlay` and includes it in the phase-1 stabilization sequence.
- The Komikku backbone source guard now requires both renderer scrolling and the clipped adjacent-section underlay path so this cannot regress back to "scroll the current WebView over black".

Green checks:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-drag-preview-underlay --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-link-jump-drag --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture tmp\reader-live\served-input.epub --viewport-width 500 --viewport-height 960 --device-scale-factor 3
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSeedsTextureTurnDirectionFromNativeReadableDragPreview"
```

Results:

- JS syntax checks passed.
- `epub-native-drag-preview-underlay` passed on the real Hobbit EPUB fixture. Evidence showed the preview strip mounted on the right side with `targetIndex=1`, `direction=next`, `width=180px`, and themed background `rgb(243, 234, 215)`.
- `epub-link-jump-drag` passed, so the previous hyperlink jump drag guard did not regress.
- `epub-texture-frontmatter-transition` passed, so the existing automated texture transition guard did not regress.
- The focused Android host guards passed.

Release status:

- No APK was built or published for this slice yet.
- This should be bundled with the already-pushed progress side-rail fix into the next release candidate, then phone-validated against readable-page dragging, section-boundary dragging, link-jump dragging, texture movement, and cover behavior.

## 2026-06-16 Komikku Backbone Acceptance Guard Realignment Slice

Problem:

- `ReaderKomikkuBackboneResetTest` had become partially stale after the Komikku component-boundary extractions.
- Several guards still required shell/root/appbar/settings/viewer symbols to live inside `ReaderScreen.kt`.
- That contradicted the active architecture: `ReaderScreen.kt` is now the app-boundary coordinator, while the Komikku shell work lives in `ReaderRoot.kt`, `ReaderAppBars.kt`, `ReaderSettingsDialog.kt`, `ReaderViewerHost.kt`, `ReaderViewer.kt`, and `KomikkuReaderNativeFrameHost.android.kt`.
- Leaving those stale guards red would reward moving code back into the monolithic screen file, which is explicitly against the port objective.

Decision:

- Do not move production code back into `ReaderScreen.kt`.
- Retarget source guards to the extracted owner files:
  - root/viewer lifecycle, shell-cover mapping, native frame host mounting, viewer-key routing, and organic overlay ownership -> `ReaderRoot.kt`
  - top/bottom app bars, bookmark/page-mark icon, and chapter-local progress rail -> `ReaderAppBars.kt`
  - tap-zone chips, reading-mode chips, show-tap-zones toggle, smaller tap zones, and tapping inversion -> `ReaderSettingsDialog.kt`
  - renderer descriptor mounting -> `ReaderViewerHost.kt`
  - native frame hierarchy, color matrix, native tap/drag ownership, and shell-cover native rendering -> `KomikkuReaderNativeFrameHost.android.kt`
- Keep `ReaderScreen.kt` assertions only for the app-boundary responsibilities it still owns: dependency injection, coordinator application, settings persistence, publication/runtime hosts, readaloud host, volume keys, and the call into `KomikkuReaderRoot(...)`.

Red check:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Initial result:

```text
38 tests completed, 18 failed
```

Representative stale failures:

- `currentReaderImplementationIsVaultedAndNoLongerTheActiveEntryPoint`
- `readerTopChromeUsesKomikkuBookmarkPageMarkInsteadOfMusicStar`
- `komikkuChapterNavigatorUsesChapterLocalControllerProgressInsteadOfBookProgress`
- `androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy`
- `readerMovementApiExposesViewerActionsInsteadOfLegacyControllerNavigationActions`
- `settingsDialogUsesKomikkuTapZonePresetControlsInsteadOfStaticLabels`
- `settingsDialogUsesKomikkuReadingModePresetControlsInsteadOfStaticLabels`
- `readerRootKeepsSingleActiveViewerForHostAndNavigationActions`

Code changes:

- Updated only `ReaderKomikkuBackboneResetTest.kt`.
- Retargeted stale assertions from `ReaderScreen.kt` to the extracted Komikku owner files.
- Kept negative guards preventing raw WebView/Foliate bridge protocol, legacy navigation enums, legacy `ReaderOptionsPanel`, old `ReaderWebViewHost`, and inline renderer selection from re-entering the active shell.

Green checks:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.currentReaderImplementationIsVaultedAndNoLongerTheActiveEntryPoint" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerViewerHostConsumesEngineRendererDescriptorInsteadOfConcreteViewerClass" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerTopChromeUsesKomikkuBookmarkPageMarkInsteadOfMusicStar" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.komikkuChapterNavigatorUsesChapterLocalControllerProgressInsteadOfBookProgress"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.newBackboneUsesPortedKomikkuViewerNavigationInsteadOfOnlyNavicTapHelpers" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameAppliesViewerLayerColorMatrixLikeKomikku" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerMovementApiExposesViewerActionsInsteadOfLegacyControllerNavigationActions" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerScreenMountsViewerHostInsteadOfSelectingRendererViewsInline" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.shellCoverIsOwnedByNativeFrameHostNotCommonViewerCompose" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogAppliesTapZoneOverlayThroughControllerSettingsCommand" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogUsesKomikkuTapZonePresetControlsInsteadOfStaticLabels" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.settingsDialogUsesKomikkuReadingModePresetControlsInsteadOfStaticLabels" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.readerRootKeepsSingleActiveViewerForHostAndNavigationActions" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeFrameHostSwapsViewerContentByReaderViewerKeyLikeKomikkuUpdateViewer" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest.nativeShellCoverUsesCoverSpecificSideZoneNavigation"
.\gradlew.bat --no-daemon --no-build-cache --rerun-tasks "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
```

Results:

- First focused stale-guard batch passed.
- Second focused stale-guard batch passed.
- Full `ReaderKomikkuBackboneResetTest` passed: 38 tests, 0 failures.

Release status:

- No APK was built or published. This slice changes acceptance guards only; it does not alter installed reader behavior.
- The value is architectural: future work now has a green Komikku backbone acceptance suite that protects the extracted shell/controller boundaries instead of forcing code back into `ReaderScreen.kt`.
