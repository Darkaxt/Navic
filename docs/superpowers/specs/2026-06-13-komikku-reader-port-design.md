# Komikku Reader Port Design

Date: 2026-06-13

Status: active source of truth for reader shell work. This supersedes the older stabilization and parity plans where they conflict with Komikku source behavior.

## Current Branch Register

This document is the durable project register for the reader-shell replacement. If context is compacted, resumed, or handed to another thread, this file overrides chat memory and the older `2026-06-11-reader-stabilization-design.md` shell-fix plan.

Current implementation state:

- `ReaderScreen` has started moving away from the old `Scaffold(bottomBar = ...)` model.
- The first architectural test now rejects `Scaffold`, `bottomBar`, bottom-sheet-hosted settings, and content padding inherited from chrome.
- The common tap-zone model has started moving to Komikku semantics: normal regions are one third of the reader surface, smaller regions are one quarter, explicit navigation regions win before menu fallback, and visible tap-zone overlay regions are navigation-only.
- Reader chrome now has a dedicated side progress rail in the overlay layer. The old bottom chrome progress slider/linear progress ownership is no longer the intended structure.
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

## Objective

Replace Navic's current reader shell with a Komikku-equivalent reader architecture. This is not a "Komikku-style" visual pass and not another sequence of isolated fixes on the existing `ReaderScreen` scaffold.

The target is to port/adapt Komikku's reader ownership model:

- Rendered content is a full-window viewer surface.
- Reader gestures are owned by the reader/viewer layer, not by EPUB HTML.
- Tap-zone visualization is a separate visual overlay, not the input authority.
- Menus, page indicator, settings, brightness/filter overlays, and progress controls are Compose overlays above the viewer.
- Opening menus/settings never resizes, pads, zooms, or relayouts the content surface.
- Navic's Foliate/PDF/Bindery code remains the content backend, but it must sit behind the Komikku-equivalent shell contract.

Partial breakage of the current reader shell is acceptable during this port if it moves the architecture toward the Komikku model. The wrong path is continuing to patch the current shell one symptom at a time.

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
