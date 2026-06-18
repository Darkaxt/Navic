# Anx/Foliate Middleware Behavior Parity — 8-Phase Implementation Plan

Date: 2026-06-17
Revised: 2026-06-18 (Codex review blockers 1-10 fixed, findings 1-6 fixed)
Companion to: `2026-06-17-anx-middleware-complaint-brief.md`, `2026-06-17-komikku-reader-port-status-audit.md`
Branch: `codex/komikku-reader-backbone-eta64`

## Workflow Per Phase

```
1. Implement Phase N (GLM or Codex)
2. Validate Phase N — host suite green + emulator gate
3. Ask Codex to review Phase N and make its own improvements
4. Review Codex's actions (GLM verifies against reference + guard tests)
5. Move to Phase N+1
```

**Hard rule:** every phase must leave the full host suite green. No committed red tests. The parity guard is a green "known gaps registry" that fails only on undocumented gaps.

## Deriving The Expected Version

Every validation command in this plan uses `$ExpectedVersionName` as a placeholder. Derive it before running:

```powershell
$ExpectedVersionName = (Select-String -Path 'androidApp\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches.Groups[1].Value
```

Do not hardcode `v1.0.11-eta69` — it will rot after the first rebuild.

## Pre-Flight Findings (Verified 2026-06-17)

### Environment

- Emulator: `emulator-5554` with `darkaxt.navic.readerdev` installed (`v1.0.11-eta69`, `versionCode=402`, `lastUpdateTime=2026-06-17 22:37:29`)
- Phone: `RFCY80551LT` — no readerdev installed
- Bindery env: `C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env`
- Validation scripts: `scripts\install-reader-dev.ps1`, `scripts\adb-reader-komikku-matrix.ps1`
- Install: `.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env`
- Matrix: `.\scripts\adb-reader-komikku-matrix.ps1 -DeviceSerial emulator-5554 -ExpectedVersionName $ExpectedVersionName -NoLaunch -IncludeCoverChecks -ContinueOnFailure`

### JS Module Layout (after Codex's 2026-06-17 refactor)

| File | Lines | KB | Responsibility |
| --- | --- | --- | --- |
| `navic-reader.js` | 1259 | 48.6 | Main runtime, openPublication, bridge dispatch, event listeners, ES module imports |
| `navic-reader-content-interactions.js` | 895 | 31.4 | Link/image/sepia/long-press/suppression — exports `NavicReaderContentInteractionMethods` |
| `navic-reader-helpers.js` | 1589 | 61.0 | Navigation regions, pagination, texture, utilities |
| `navic-reader-pagination.js` | 998 | 40.2 | Pagination profile, page math |
| `navic-reader-page-turns.js` | 765 | 27.1 | Page turn, drag preview, scroll |
| `navic-reader-appearance.js` | 529 | 19.8 | Theme, texture, paper surface, fonts |
| `navic-reader-settings.js` | 53 | 1.5 | Settings normalization |

### ADB Debug Label Contract

`ReaderEngineWebViewHost.android.kt:307-323` defines `engineDebugLabel()` — a `when` branch per `ReaderBridgeEvent`. Every new event variant MUST add a branch here or ADB validation is blind. Each phase that adds bridge events includes this as an explicit step.

### Anx Callback Catalog (16 handlers in `epub_player.dart:627-879`)

| # | Anx callback | Anx source line | Navic counterpart | Status |
| --- | --- | --- | --- | --- |
| 1 | `onLoadEnd` | 628 | `PublicationReady` (bare) | PARTIAL — no doc/index payload |
| 2 | `onRelocated` | 634 | `LocationChanged` | EXISTS but thinner payload (Phase 4) |
| 3 | `onClick` | 667 | `ContentTapHandled` with typed `ReaderContentAction` | BEHAVIOR EXISTS — gesture path differs by design (Komikku long-press, not ordinary click) |
| 4 | `onExternalLink` | 673 | Collapsed into `ContentTapHandled(action=Link)` | MISSING as distinct event (Phase 3) |
| 5 | `onSetToc` | 680 | `Toc` | EXISTS |
| 6 | `onSelectionEnd` | 687 | `SelectionChanged` | EXISTS with full payload (Phase 5 host-verified) |
| 7 | `onSelectionCleared` | 716 | `SelectionCleared` | EXISTS (Phase 3 host-verified) |
| 8 | `onAnnotationClick` | 726 | None | MISSING (Phase 3) |
| 9 | `onSearch` | 769 | `SearchResults` | EXISTS |
| 10 | `renderAnnotations` | 784 | `ApplyAnnotations` (command) | EXISTS as command |
| 11 | `onPushState` | 790 | None | MISSING (Phase 3) |
| 12 | `onImageClick` | 802 | `ContentLongPressAt` → `toggleSepiaImageOverlayFromEvent` | PRODUCT DIVERGENCE — Anx opens image viewer; Navic toggles sepia overlay. Different behavior, not parity. See divergences section. |
| 13 | `onFootnoteClose` | 815 | None | MISSING (Phase 3 — requires porting footnote close hook) |
| 14 | `onPullUp` | 821 | None | MISSING (Phase 3 — requires porting scroll-end hook) |
| 15 | `handleBookmark` | 827 | `ReaderController.toggleCurrentBookmark()` | BEHAVIOR EXISTS at controller layer — no bridge event because Komikku owns bookmark UI/control surface |
| 16 | `translateText` | 864 | None | OUT OF SCOPE — Anx-specific translation service integration (text → translation API). Not a reader behavior parity item. Documented as out-of-scope in the known-gaps registry with rationale. |

### Foliate `view.js` Emit Gap (Anx vs Navic's bundled fork)

| Anx event | Navic's fork emits? | `navic-reader.js` listens? | Posted to bridge? |
| --- | --- | --- | --- |
| `relocate` | Yes (view.js:337) | Yes (navic-reader.js:342) | Yes as `locationChanged` (thinner) |
| `load` | Yes (view.js:348) | Yes (navic-reader.js:343) | No — used internally only |
| `external-link` | Yes (view.js:360) | Yes (navic-reader.js:344) | No — only `preventDefault()` |
| `link` | Yes (view.js:363) | **No — BUG** | No |
| `draw-annotation` | Yes (view.js:393) | No | No |
| `show-annotation` | Yes (view.js:411,428) | No | No |
| `create-overlay` | Yes (view.js:418) | No | No |
| `click-image` | **No** — Navic's fork omits this | No | No — PRODUCT DIVERGENCE (see below) |
| `click-view` | **No** — Navic's fork omits this | No | No — PRODUCT DIVERGENCE (see below) |

### Product Divergences (Different Behavior, Not Parity — Must Be Guarded)

User ruling: Komikku owns UI/control surface; Anx owns reader behavior. These Anx callbacks have **different behavior** in Navic, not just a different layer. They are accepted product divergences, but the parity guard must verify the intended Navic behavior exists and is reachable — not just that a symbol exists.

1. **`onClick` (Anx `epub_player.dart:667`) → `ContentTapHandled` with typed `ReaderContentAction`:**
   - **Anx behavior:** Ordinary WebView click → `onClick(location)` → context menu / content action.
   - **Navic behavior:** Komikku-native `onSingleTapConfirmed` → content hit test → typed `ReaderContentAction` claim → `ContentTapHandled` bridge event. Content interaction is long-press, not ordinary click.
   - **Divergence type:** Gesture trigger differs by design (Komikku owns short taps). Behavior (content click classification) is parity.
   - **Guard (route verification, not symbol existence):** Assert the full route: `KomikkuReaderNativeFrameHost.android.kt` calls `dispatchSingleTapAction` → `readerContentActionAtPoint` hit test → `ReaderBridgeEvent.ContentTapHandled` is decoded in `ReaderBridgeProtocol.kt` → mapped to `ReaderEngineEvent.ContentActionClaimed` in `FoliateEpubEngineAdapter.kt`. The test must read each file and verify the call chain connects, not just that the symbols exist.

2. **`onImageClick` (Anx `epub_player.dart:802`) → `ContentLongPressAt` → `toggleSepiaImageOverlayFromEvent`:**
   - **Anx behavior:** Ordinary image click → `onImageClick(image)` → opens `ImageViewer` (full-screen image viewer with book title).
   - **Navic behavior:** Komikku long-press → `ContentLongPressAt` bridge command → `toggleSepiaImageOverlayFromEvent` → toggles sepia overlay on/off for that image.
   - **Divergence type:** **Product divergence, NOT parity.** Anx opens an image viewer; Navic toggles a sepia filter. These are different features. The sepia toggle is the user's intended behavior for long-press on images. The image viewer is not implemented.
   - **Guard (route + behavior verification):** Assert the full route: `KomikkuReaderNativeFrameHost.android.kt` `onLongTapConfirmed` → `onContentLongPress` → `ReaderViewerAction.ContentLongPressAt` → `ReaderEngineCommand.ContentLongPressAt` → `ReaderBridgeCommand.ContentLongPressAt` → `navic-reader.js` `handleNativeTapZoneContentLongPressAt` → `toggleSepiaImageOverlayFromEvent`. The test must read each file and verify the call chain connects AND that `toggleSepiaImageOverlayFromEvent` actually toggles `dataset.navicSepiaOverlay`.
   - **If full-screen image viewer is desired later:** Document as a separate feature, not an Anx parity item.

3. **`handleBookmark` (Anx `epub_player.dart:827`) → `ReaderController.toggleCurrentBookmark()`:**
   - **Anx behavior:** JS bookmark annotation toggle → `handleBookmark(detail, remove)` → persists bookmark data → applies bookmark back to WebView as annotation.
   - **Navic behavior:** Komikku top-bar bookmark/page-mark icon → `coordinator.toggleCurrentBookmark()` → `ReaderController.toggleCurrentBookmark()` → updates `ReaderControllerState.currentLocationBookmarked`. No WebView annotation.
   - **Divergence type:** Layer differs by design (Komikku owns bookmark UI/control). Behavior (bookmark toggle at current location) is parity. The WebView annotation rendering of bookmarks is not implemented.
   - **Guard (route verification):** Assert the full route: `ReaderAppBars.kt` bookmark icon → `coordinator.toggleCurrentBookmark()` → `ReaderController.toggleCurrentBookmark()` → `ReaderControllerState.currentLocationBookmarked` is computed from `chrome.currentLocator`. The test must read each file and verify the call chain connects.

4. **`click-image` / `click-view` (Anx `view.js:258,285,314,325,332`):**
   - **Anx behavior:** Foliate emits `click-image { img }` on image click and `click-view { x, y }` on view click. These feed Anx's `onImageClick` and `onClick` handlers.
   - **Navic behavior:** Navic's bundled Foliate fork **omits** these emits. Image/view click classification is handled at the `navic-reader.js` wrapper level via `readerContentTapHandled` with typed `source` fields, and at the native Komikku layer via `onSingleTapConfirmed` / `onLongTapConfirmed`.
   - **Divergence type:** **Product divergence, NOT parity.** Anx's ordinary-click-as-content model conflicts with Komikku's short-tap-as-navigation model. The omission is correct by design.
   - **Guard (verify omission is intentional and behavior is covered elsewhere):** Assert Navic's `vendor/foliate-js/view.js` does NOT emit `click-image` or `click-view` (confirms the omission), AND assert `navic-reader-content-interactions.js` handles image/view interaction via `readerContentTapHandled` with `source: 'image'` / `source: 'link'` (confirms behavior coverage at the wrapper layer).

### Bug Found During Audit: Internal `link` Events Not Suppressed in Native Mode

**Root cause:** Navic's bundled Foliate fork has `#handleLinks` (`vendor/foliate-js/view.js:350-366`) which adds a `click` listener to every content document during `#onLoad`. On ordinary short tap of an internal link, Foliate's listener fires first → `e.preventDefault()` → emits `link` event (cancelable) → calls `this.goTo(href)` → EPUB navigates. Navic's own link click handler (`navic-reader-content-interactions.js:693`) fires second → `suppressReaderNativeTapZoneContentActivation` → too late.

**Impact:** In native tap zone mode, ordinary short-tapping an internal EPUB link navigates to that link's target — violating the user ruling "long press for content, short tap for navigation."

**Fix (Phase 2):** Add `this.view.addEventListener('link', event => { if (this.nativeTapZones) event.preventDefault() })` in `navic-reader.js` near line 344. Long-press link navigation is unaffected because `activateReaderLinkFromEvent` (`navic-reader-content-interactions.js:698`) calls `this.view.goTo(href)` directly, not through the `link` event.

---

## Phase 1 — Citations + Green Known-Gaps Registry

**Grievances:** 1 (no citations), 2 (test locks own mapping, not parity)
**Goal:** Create the structural foundation that prevents the duct-tape pattern. A green parity guard that documents every known gap with its target phase. Fails only on undocumented gaps.
**Risk:** Lowest — comment-only production changes + one new test file.

### Files to touch

- `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt` — add source citation comment
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` — add source citation comment
- `composeApp/src/androidMain/assets/reader/navic-reader.js` — add source citation comment in header
- `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js` — add source citation comment
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt` — NEW FILE

### Steps

1. Add `// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:627-879` (callback catalog, including `translateText` at 864) + `// tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194` (relocation) + `:216-327` (link/image taxonomy) + `:335-397` (annotations) to the top of each adapter/protocol/JS file above.

2. Create `FoliateAnxParityTest.kt` as a **green known-gaps registry** using the Komikku disk-reading pattern from `ReaderRuntimeCommonChromeTest.kt:485-528`:
```kotlin
val anxPlayerText = listOf(
    File("tmp/references/anx-reader/lib/page/book_player/epub_player.dart"),
    File("../tmp/references/anx-reader/lib/page/book_player/epub_player.dart")
).firstOrNull { it.isFile }?.readText() ?: error("Could not locate Anx epub_player.dart reference")
```

3. The test contains a `knownGaps` registry — a map of each Anx callback/emit to its status:
```kotlin
val knownGaps = mapOf(
    // Product divergence — behavior is different, not just at a different layer.
    // Guard must verify the full route, not just symbol existence.
    "onClick" to GapStatus.ProductDivergence(
        navicRoute = listOf(
            "KomikkuReaderNativeFrameHost.android.kt:dispatchSingleTapAction",
            "KomikkuReaderNativeFrameHost.android.kt:readerContentActionAtPoint",
            "ReaderBridgeProtocol.kt:ReaderBridgeEvent.ContentTapHandled",
            "FoliateEpubEngineAdapter.kt:ReaderEngineEvent.ContentActionClaimed"
        ),
        rationale = "Komikku owns short taps; content interaction is long-press. ContentTapHandled carries typed ReaderContentAction."
    ),
    "onImageClick" to GapStatus.ProductDivergence(
        navicRoute = listOf(
            "KomikkuReaderNativeFrameHost.android.kt:onLongTapConfirmed",
            "KomikkuReaderNativeFrameHost.android.kt:onContentLongPress",
            "ReaderViewerAction.ContentLongPressAt",
            "ReaderEngineCommand.ContentLongPressAt",
            "ReaderBridgeCommand.ContentLongPressAt",
            "navic-reader.js:handleNativeTapZoneContentLongPressAt",
            "navic-reader-content-interactions.js:toggleSepiaImageOverlayFromEvent"
        ),
        rationale = "Anx opens ImageViewer; Navic toggles sepia overlay. Different behavior, not parity. Sepia toggle is the user's intended long-press behavior."
    ),
    "handleBookmark" to GapStatus.ProductDivergence(
        navicRoute = listOf(
            "ReaderAppBars.kt:bookmark icon -> coordinator.toggleCurrentBookmark",
            "ReaderController.toggleCurrentBookmark",
            "ReaderControllerState.currentLocationBookmarked"
        ),
        rationale = "Komikku owns bookmark UI/control. No WebView annotation. Behavior (bookmark toggle at current location) is parity."
    ),
    "click-image" to GapStatus.ProductDivergence(
        navicRoute = listOf(
            "vendor/foliate-js/view.js: NO click-image emit (intentional omission)",
            "navic-reader-content-interactions.js:readerContentTapHandled with source: 'image'"
        ),
        rationale = "Anx's click-as-content conflicts with Komikku's short-tap-as-navigation. Omission is correct by design."
    ),
    "click-view" to GapStatus.ProductDivergence(
        navicRoute = listOf(
            "vendor/foliate-js/view.js: NO click-view emit (intentional omission)",
            "KomikkuReaderNativeFrameHost.android.kt:onSingleTapConfirmed -> tap zone classification"
        ),
        rationale = "Same as click-image. Navic handles view taps at the native Komikku tap-zone layer."
    ),

    // Out of scope — Anx-specific product feature, not reader behavior parity
    "translateText" to GapStatus.OutOfScope(
        rationale = "Anx-specific translation service integration (text -> translation API). Not a reader behavior parity item."
    ),

    // Existing parity
    "onRelocated" to GapStatus.Exists("LocationChanged — payload thinner, Phase 4 extends"),
    "onSetToc" to GapStatus.Exists("Toc"),
    "onSearch" to GapStatus.Exists("SearchResults"),
    "renderAnnotations" to GapStatus.Exists("ApplyAnnotations command"),
    "relocate" to GapStatus.Exists("locationChanged — payload thinner, Phase 4 extends"),

    // Known missing — target phase documented
    "onLoadEnd" to GapStatus.Missing(targetPhase = 3, note = "LoadDoc event with serializable payload"),
    "onExternalLink" to GapStatus.Missing(targetPhase = 3, note = "ExternalLink distinct event"),
    "onSelectionCleared" to GapStatus.Missing(targetPhase = 3, note = "SelectionCleared distinct event"),
    "onAnnotationClick" to GapStatus.Missing(targetPhase = 3, note = "AnnotationClick from show-annotation"),
    "onPushState" to GapStatus.Missing(targetPhase = 3, note = "PushState event"),
    "onFootnoteClose" to GapStatus.Missing(targetPhase = 3, note = "FootnoteClose — requires porting footnote close hook"),
    "onPullUp" to GapStatus.Missing(targetPhase = 3, note = "PullUp — requires porting scroll-end hook"),
    "link" to GapStatus.Missing(targetPhase = 2, note = "InternalLinkRequested — link suppression bug fix"),
    "load" to GapStatus.Missing(targetPhase = 3, note = "LoadDoc event"),
    "external-link" to GapStatus.Missing(targetPhase = 3, note = "ExternalLink event"),
    "draw-annotation" to GapStatus.Missing(targetPhase = 3, note = "AnnotationDrawn event"),
    "show-annotation" to GapStatus.Missing(targetPhase = 3, note = "AnnotationClick event"),
    "create-overlay" to GapStatus.Missing(targetPhase = 3, note = "OverlayCreated event"),
    "onSelectionEnd" to GapStatus.Exists("SelectionChanged carries Anx text/cfi/footnote/contextText/pos payload"),
    "maxColumnCount" to GapStatus.Exists("ReaderSettings.maxColumnCount — Phase 8 adaptive composition"),
    "columnThreshold" to GapStatus.Exists("ReaderSettings.columnThreshold — Phase 8 adaptive composition"),
)
```

4. The test asserts:
   - Every `handlerName:` in `epub_player.dart:627-879` is in `knownGaps` (fails if an Anx callback is NOT documented — this catches new Anx callbacks we missed).
   - Every `#emit(...)` in `assets/foliate-js/src/view.js` is in `knownGaps` (fails if a Foliate emit is NOT documented).
   - **For `ProductDivergence` entries:** the test reads each file in `navicRoute` and verifies the route connects — each file contains the referenced symbol, and the call chain is intact (e.g., `KomikkuReaderNativeFrameHost.android.kt` contains `dispatchSingleTapAction` AND calls `readerContentActionAtPoint`; `ReaderBridgeProtocol.kt` contains `ContentTapHandled`; `FoliateEpubEngineAdapter.kt` maps it to `ContentActionClaimed`). This is route verification, not symbol existence.
   - **For `OutOfScope` entries:** the test verifies the rationale string is non-empty and the Anx callback exists in the reference.
   - `FoliateEpubEngineAdapter.kt` contains an Anx source citation comment.
   - `ReaderBridgeProtocol.kt` contains an Anx source citation comment.
   - Every style field in `book_style.dart:3-17` is in `knownGaps`.

5. The test is **GREEN** because every gap is documented. As each phase closes a gap, the registry entry is updated to `Exists`. If a new gap appears without documentation, the test fails.

### Validation

**Host:**
```powershell
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"
```
- `FoliateAnxParityTest` is GREEN (all gaps documented).
- Existing 402+ tests remain green (citations are comment-only).

**Emulator:** No emulator gate — no behavior change.

### Codex review focus

- Are the `knownGaps` entries accurate and complete?
- Is the `ProductDivergence` route verification correct — does it actually read each file in `navicRoute` and verify the call chain connects, not just that symbols exist?
- Are there any Anx callbacks or Foliate emits I missed?

---

## Phase 2 — Link Suppression Bug Fix + Internal Link Behavior Event

**Bug:** Internal `link` events not suppressed in native mode (found during audit).
**Goal:** Fix the race condition. Add `InternalLinkRequested` as a behavior event for BOTH short-tap suppression and long-press navigation, with `prevented` and `source` fields so the controller can distinguish them.
**Risk:** Low — one new event listener + one new bridge event variant.

### Semantics

- **Short tap on link in native mode:** `preventDefault()` stops Foliate's `goTo`. Post `internalLink` with `prevented: true`, `source: 'short-tap-suppressed'`. No navigation happened.
- **Long press on link:** `activateReaderLinkFromEvent` calls `this.view.goTo(href)`. Navigation happens. Post `internalLink` with `prevented: false`, `source: 'long-press'`.
- Both paths emit the same behavior event. The controller receives behavior metadata regardless of whether navigation occurred.

### Files to touch

- `composeApp/src/androidMain/assets/reader/navic-reader.js` — add `link` event listener near line 344
- `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js` — post `internalLink` from `activateReaderLinkFromEvent` with `prevented: false`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` — add `InternalLinkRequested` bridge event + decode
- `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt` — map to engine event
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt` — add `engineDebugLabel()` branch
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt` — update: `link` gap → `Exists`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/FoliateEpubEngineAdapterTest.kt` — add round-trip test

### Steps

1. In `navic-reader.js` near line 344, add:
```js
this.view.addEventListener('link', event => {
    if (this.nativeTapZones) {
        event.preventDefault()
        post({ type: 'internalLink', href: event.detail?.href, anchorHref: event.detail?.a?.getAttribute?.('href'), prevented: true, source: 'short-tap-suppressed' })
    }
})
```

2. In `navic-reader-content-interactions.js` `activateReaderLinkFromEvent` (line 698), post the behavior event before the direct `this.goTo(href)` call:
```js
post({ type: 'internalLink', href, prevented: false, source })
```

3. In `ReaderBridgeProtocol.kt`, add:
```kotlin
data class InternalLinkRequested(
    val href: String? = null,
    val prevented: Boolean = false,
    val source: String? = null
) : ReaderBridgeEvent
```
Add decode case: `"internalLink" -> ReaderBridgeEvent.InternalLinkRequested(...)`

4. In `FoliateEpubEngineAdapter.kt` `onBridgeEvent`, map:
```kotlin
is ReaderBridgeEvent.InternalLinkRequested -> ReaderEngineEvent.InternalLinkRequested(
    href = event.href,
    prevented = event.prevented,
    source = event.source
)
```

5. In `ReaderEngineWebViewHost.android.kt:307-323`, add:
```kotlin
is ReaderBridgeEvent.InternalLinkRequested -> "internalLink(${href.engineUrlLabel()}, prevented=$prevented, source=$source)"
```

6. Update `FoliateAnxParityTest.kt`: change `"link"` from `Missing(targetPhase = 2)` to `Exists("InternalLinkRequested with prevented/source semantics")`.

7. Add bridge decode and adapter mapping tests for both `prevented=true` and `prevented=false` cases.

### Status

Host-implemented on 2026-06-18:

- `navic-reader.js` listens to Foliate `link`, prevents default navigation when `nativeTapZones === true`, and posts `internalLink(prevented=true, source='native-short-tap')`.
- `navic-reader-content-interactions.js` posts `internalLink(prevented=false, source=...)` before direct internal link `goTo`.
- `ReaderBridgeProtocol.kt`, `FoliateEpubEngineAdapter.kt`, `ReaderEngine.kt`, and `ReaderEngineWebViewHost.android.kt` carry the event across the bridge/engine boundary.
- `FoliateAnxParityTest.kt` now marks `link` as `Exists` and verifies the suppression/bridge/debug-label route.
- Android/emulator validation is still pending; do not call Phase 2 release-ready until the ADB checks below pass.

### Validation

**Host:**
```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"
```
- All GREEN.

### Status

Host-implemented and verified on 2026-06-18:

- `ReaderLocator` now carries `rangeCfi`, `reason`, `fraction`, `size`, `tocItemLabel`, and `pageItemLabel`.
- `decodeReaderBridgeEvent` decodes those fields from `locationChanged`.
- `ReaderLocator.toJsonObject()` serializes the same fields for bridge commands/start locators.
- `navic-reader.js` posts those fields in the `locationChanged` message while preserving Navic page-model diagnostics.
- `rangeCfi` is taken from `detail.cfi` and static parity guards reject DOM `Range` stringification.
- `ReaderEngineWebViewHost.android.kt` exposes `reason` and `rangeCfi` in the `locationChanged(...)` debug label for ADB validation.
- Focused Phase 4 tests passed: `ReaderBridgeProtocolTest.bridgeEventsDecodeReaderLocationAndOverlayEvents`, `FoliateEpubEngineAdapterTest.mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome`, and `FoliateAnxParityTest.phase4RelocationPayloadMatchesAnxLastLocationContract`.
- Reader host suite passed with `:composeApp:testAndroidHost --tests paige.navic.reader.*`.

Android/emulator validation is still pending. Do not treat Phase 4 as release-ready until logcat confirms real `locationChanged` posts include `reason` and CFI-or-null `rangeCfi`.

**Emulator:**
```powershell
$ExpectedVersionName = (Select-String -Path 'androidApp\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches.Groups[1].Value
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env
.\scripts\adb-reader-komikku-matrix.ps1 -DeviceSerial emulator-5554 -ExpectedVersionName $ExpectedVersionName -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```
- Verify: short-tapping an internal EPUB link does NOT navigate — triggers Komikku tap-zone action.
- Verify: long-pressing an internal EPUB link still navigates.
- Verify: logcat shows `internalLink(..., prevented=true, source=native-short-tap)` on short tap and `internalLink(..., prevented=false, source=...)` on long press/direct activation.
- No regressions vs. 2026-06-17 baseline.

### Codex review focus

- Does `activateReaderLinkFromEvent` post the event at the right point — after `goTo` resolves, or before?
- Should `source` be an enum instead of a string?
- Is `event.detail?.a` safe across Foliate fork versions?

---

## Phase 3 — Missing Bridge Events

**Grievance:** 3 (missing bridge events)
**Goal:** Add every missing `ReaderBridgeEvent` variant. Add JS listeners for Foliate `view.js` emits. Split into events with clear insertion points and events requiring hook porting.
**Risk:** Medium — additive only, existing events unchanged.

### Missing events — clear insertion points

| Anx callback | Foliate emit | New `ReaderBridgeEvent` | New `ReaderEngineEvent` |
| --- | --- | --- | --- |
| `onExternalLink` | `external-link` | `ExternalLink(href, anchorHref)` | `ExternalLinkOpened(href)` |
| `onSelectionCleared` | (Navic-side) | `SelectionCleared` | `SelectionCleared` |
| `onAnnotationClick` | `show-annotation` | `AnnotationClick(value, index, rangeCfi?)` | `AnnotationClicked(value, index, rangeCfi?)` |
| (Foliate internal) | `draw-annotation` | `AnnotationDrawn(value, index, rangeCfi?)` | `AnnotationDrawn(value, index, rangeCfi?)` |
| (Foliate internal) | `create-overlay` | `OverlayCreated(index)` | `OverlayCreated(index)` |
| `onLoadEnd` | `load` | `LoadDoc(index, href?, title?, sectionId?)` | `DocLoaded(index, href?, title?)` |
| `onPushState` | (Navic-side) | `PushState(canGoBack, canGoForward)` | `NavigationStateChanged(canGoBack, canGoForward)` |

**Critical:** `LoadDoc` carries serializable fields only — `index: Int`, `href: String?`, `title: String?`, `sectionId: String?`. NOT a DOM `doc` object, which cannot cross the Android WebView bridge.

**Critical:** `AnnotationClick` and `AnnotationDrawn` use `rangeCfi: String?` — the CFI of the annotation range, not a DOM Range object. If the CFI is unavailable, set to `null`.

### Missing events — require porting hooks

| Anx callback | New `ReaderBridgeEvent` | Hook to port |
| --- | --- | --- |
| `onFootnoteClose` | `FootnoteClose` | Navic's `footnotes.js` dispatches `render` and `before-render` but has NO close event. Anx's footnote popup closes via `removeOverlay()`. Port: add a `close` event to `footnotes.js` or detect footnote-popup dismissal in `navic-reader-content-interactions.js` and post `footnoteClose`. |
| `onPullUp` | `PullUp` | `navic-reader-page-turns.js:738` has `atEnd` scroll-edge detection but no pull-up emission. Port: when `atEnd` is true and the user continues scrolling down (overscroll), post `pullUp`. Reference: Anx `epub_player.dart:821` calls `widget.showOrHideAppBarAndBottomBar(true)` on pull-up. |

### Files touched

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` — add 9 event variants + decode cases
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt` — add 9 `ReaderEngineEvent` variants
- `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt` — map 9 events
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt` — add 9 `engineDebugLabel()` branches
- `composeApp/src/androidMain/assets/reader/navic-reader.js` — add Foliate event listeners, history listener, `selectionCleared`, `loadDoc`, and `footnoteClose` emission on overlay clearing
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js` — add `pullUp` emission at scroll-end/overscroll
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt` — update: all event gaps → `Exists`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/FoliateEpubEngineAdapterTest.kt` — add mapping tests
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderBridgeProtocolTest.kt` — add decode tests

### Steps

1. Add 9 `ReaderBridgeEvent` variants in `ReaderBridgeProtocol.kt` with decode cases. Use serializable fields only — no DOM objects.

2. Add 9 `ReaderEngineEvent` variants in `ReaderEngine.kt`.

3. Map all 9 in `FoliateEpubEngineAdapter.kt` `onBridgeEvent`.

4. Add 9 branches in `ReaderEngineWebViewHost.android.kt:307-323` `engineDebugLabel()`.

5. In `navic-reader.js` near the Foliate view setup, add/replace listeners:
```js
this.view.addEventListener('external-link', event => this.onExternalLink(event))
this.view.addEventListener('draw-annotation', event => this.onAnnotationDrawn(event.detail || {}))
this.view.addEventListener('show-annotation', event => this.onAnnotationClick(event.detail || {}))
this.view.addEventListener('create-overlay', event => this.onOverlayCreated(event.detail || {}))
this.view.history?.addEventListener?.('index-change', () => this.postNavigationState('history-index-change'))
```
Note: `rangeCfi` is computed with Foliate `view.getCFI(index, range)` when a DOM `Range` is present; otherwise it is omitted rather than serializing the DOM object.

6. Update `onLoad` (`navic-reader.js:1294`) to post `loadDoc` with concrete extraction — no placeholders:
```js
// In onLoad(detail = {}), after existing internal handling:
const index = detail.index
const section = this.view?.book?.sections?.[index]
const href = section?.href || null
const title = section?.title || null
const sectionId = section?.id || null
post({ type: 'loadDoc', index, href, title, sectionId })
```
The `section` object is available on `this.view.book.sections[index]` — this is the Foliate book model, not a DOM object. `href`, `title`, and `id` are serializable strings. If the section or book is unavailable, the fields are `null` and `index` is still posted.

7. In `navic-reader.js`, change the bare `selectionChanged` post at the selection-clear point to post `selectionCleared` instead.

8. **Port footnote close hook:** In `navic-reader.js`, post `footnoteClose` when `clearOverlay()` actually removes overlay fragments. Reference: Anx `epub_player.dart:815` calls `removeOverlay()` on footnote close. A richer footnote-popup UI remains a separate feature.

9. **Port pull-up hook:** In `navic-reader-page-turns.js`, when `atEnd` (line 738) is true and the user continues scrolling down (overscroll detection), post `pullUp`. Reference: Anx `epub_player.dart:821`.

10. Update `FoliateAnxParityTest.kt` — convert ONLY the Phase 3 implemented event gaps to `Exists`: `onLoadEnd`/`load`→`LoadDoc`, `onExternalLink`/`external-link`→`ExternalLink`, `onSelectionCleared`→`SelectionCleared`, `onAnnotationClick`/`show-annotation`→`AnnotationClick`, `draw-annotation`→`AnnotationDrawn`, `create-overlay`→`OverlayCreated`, `onPushState`→`PushState`, `onFootnoteClose`→`FootnoteClose`, `onPullUp`→`PullUp`. Do NOT convert `click-image`, `click-view`, `onImageClick`, `handleBookmark`, or `translateText` — those remain as `ProductDivergence` or `OutOfScope`.

11. Add round-trip tests in `FoliateEpubEngineAdapterTest.kt` for each new event.

### Status

Host-implemented on 2026-06-18:

- `ReaderBridgeProtocol.kt` decodes `externalLink`, `selectionCleared`, `annotationClick`, `annotationDrawn`, `overlayCreated`, `loadDoc`, `pushState`, `footnoteClose`, and `pullUp`.
- `ReaderEngine.kt` and `FoliateEpubEngineAdapter.kt` expose matching engine events while leaving visible chrome ownership to the Komikku/native controller layer.
- `ReaderEngineWebViewHost.android.kt` logs ADB-visible labels for every new Phase 3 event.
- `navic-reader.js` bridges Foliate `external-link`, `draw-annotation`, `show-annotation`, `create-overlay`, `load`, and history `index-change`; selection clear now posts `selectionCleared`; overlay clearing posts `footnoteClose`.
- `navic-reader-page-turns.js` emits `pullUp` on scrolled-edge overscroll before advancing.
- `FoliateAnxParityTest.kt` marks only the Phase 3 event gaps as `Exists`; `click-image`, `click-view`, `onImageClick`, `handleBookmark`, and `translateText` remain divergence/out-of-scope entries.
- Android/emulator validation is still pending; do not call Phase 3 release-ready until logcat confirms the new labels under real reader flows.

### Validation

**Host:**
```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-page-turns.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"
```
- All GREEN. `FoliateAnxParityTest` event assertions pass.

**Emulator:**
```powershell
$ExpectedVersionName = (Select-String -Path 'androidApp\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches.Groups[1].Value
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env
.\scripts\adb-reader-komikku-matrix.ps1 -DeviceSerial emulator-5554 -ExpectedVersionName $ExpectedVersionName -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```
- Verify: no regressions in tap/drag/texture/cover behavior.
- Verify: logcat shows new event labels (`externalLink`, `loadDoc`, `annotationClick`, `annotationDrawn`, `overlayCreated`, `selectionCleared`, `footnoteClose`, `pullUp`).
- Verify: long-press on an existing highlight posts `annotationClick`.
- Verify: opening a book posts `loadDoc` with index/href payload.

### Codex review focus

- Are the event payload shapes correct? Compare each to the Anx callback's `args[0]` structure.
- Is `rangeCfi` correctly using the annotation `value` (which is a CFI string in Foliate), not `String(range)`?
- Does the footnote close hook fire at the right time?
- Does the pull-up hook fire on overscroll, not on every scroll?
- Should `ExternalLink` and `InternalLinkRequested` share a sealed base?
- Is `PullUp` the right name, or should it be `ScrollEndReached`?

---

## Phase 4 — Relocation Payload Parity

**Grievance:** 4 (relocation payload thinner than Anx's)
**Goal:** Extend `ReaderLocator` and the `locationChanged` bridge message to carry every field Anx's `#onRelocate` builds, while keeping Navic-specific extensions.
**Risk:** Medium — touches the core relocation path. Must not regress page numbering, rail, or progress persistence.

### Anx's relocation payload (`view.js:329-337`)

```js
lastLocation = { ...progress, tocItem, pageItem, cfi, range }
```
Built from `{ reason, range, index, fraction, size }`.

### Missing from Navic

| Anx field | Purpose | Navic extension | Serialization |
| --- | --- | --- | --- |
| `range` | DOM range for annotation positioning | `ReaderLocator.rangeCfi: String?` | **CFI string, NOT `String(range)`** — use `detail.cfi` (already computed by Foliate as `getCFI(index, range)`) or `null` if unavailable |
| `reason` | Relocation reason | `ReaderLocator.reason: String?` | String — already in JS scope, just not posted (bridge leak) |
| `fraction` | Renderer section fraction | `ReaderLocator.fraction: Double?` | Double |
| `size` | Renderer section size | `ReaderLocator.size: Double?` | Double |
| `tocItem` (full) | Full TOC item | `ReaderLocator.tocItemLabel: String?` | String (label/title) |
| `pageItem` (full) | Full page item | `ReaderLocator.pageItemLabel: String?` | String (label/text) |

**Critical:** `range` must NOT be `String(detail.range)`. A DOM Range object serialized via `String()` produces `"[object Range]"`. Use the CFI (`detail.cfi`) which is already computed by Foliate's `#onRelocate` as `cfi = this.getCFI(index, range)` — the CFI IS the serializable representation of the range. If `detail.cfi` is unavailable, set `rangeCfi = null`.

### Files to touch

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` — extend `ReaderLocator` + decode
- `composeApp/src/androidMain/assets/reader/navic-reader.js` — extend `locationChanged` message (~line 1057)
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt` — update `LocationChanged` debug label if needed
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt` — add relocation payload assertion; update `onRelocated` gap → `Exists`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/FoliateEpubEngineAdapterTest.kt` — update relocation test with full payload
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt` — update if relocation assertions check payload shape

### Steps

1. Extend `ReaderLocator` (`ReaderBridgeProtocol.kt:22-31`) with 6 new fields: `rangeCfi: String?`, `reason: String?`, `fraction: Double?`, `size: Double?`, `tocItemLabel: String?`, `pageItemLabel: String?`.

2. Add decode cases in `decodeReaderBridgeEvent` for the new fields.

3. In `navic-reader.js` `onRelocate` (~line 1057), extend the `locationChanged` message:
```js
const message = {
    type: 'locationChanged',
    // ... existing fields ...
    rangeCfi: detail.cfi || null,  // CFI is the serializable range representation
    reason: reason || null,         // already in JS scope, now propagated across bridge
    fraction: optionalNumber(detail.fraction),
    size: optionalNumber(detail.size),
    tocItemLabel: tocItem.label || tocItem.title || null,
    pageItemLabel: pageItem?.label || pageItem?.text || null,
    ...pageModelDiagnostics,
}
```

4. Add parity guard in `FoliateAnxParityTest.kt` that reads `view.js:175-194` and asserts every field on Anx's `lastLocation` has a `ReaderLocator` counterpart.

5. Update `FoliateEpubEngineAdapterTest.kt` relocation test with the new fields.

6. Check `ReaderRuntimeShellProgressTest.kt` for any relocation payload assertions that need updating.

### Validation

**Host:**
```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest" --tests "paige.navic.reader.FoliateEpubEngineAdapterTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"
```
- All GREEN.

**Emulator:**
```powershell
$ExpectedVersionName = (Select-String -Path 'androidApp\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches.Groups[1].Value
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -EnvFile C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env
.\scripts\adb-reader-komikku-matrix.ps1 -DeviceSerial emulator-5554 -ExpectedVersionName $ExpectedVersionName -NoLaunch -IncludeCoverChecks -ContinueOnFailure
```
- Verify: `reason` field appears in logcat `locationChanged` posts.
- Verify: `rangeCfi` is a CFI string (e.g. `epubcfi(...)`) NOT `[object Range]`.
- Verify: no regressions in page numbering, rail, or progress persistence.

### Codex review focus

- Is `rangeCfi = detail.cfi` correct, or should it be a separate start/end CFI pair?
- Should `reason` be an enum (`RelocationReason.Link`, `.ProgressSeek`, `.RelocateCommitted`) instead of a string?
- Does the `reason` field propagation close the bridge leak where the controller must infer relocation reason from JS-side state?
- Are the Navic-specific extensions (`chapterProgress`, `pageModelDiagnostics`) preserved alongside the new Anx-parity fields?

---

## Phase 5 — Selection Payload Parity

**Grievance:** Part of 3 + 4 (selection-specific payload gap)
**Goal:** Extend `SelectionChanged` to carry the full Anx `onSelectionEnd` payload. Make `SelectionCleared` a distinct event (added in Phase 3) with correct semantics.
**Risk:** Low — selection is not on the critical navigation path.

### Anx's `onSelectionEnd` payload (`epub_player.dart:687-714`)

```dart
String cfi = location['cfi'];
String text = location['text'];
bool footnote = location['footnote'];
String? contextText = location['contextText'];
double left = location['pos']['left'];
double top = location['pos']['top'];
double right = location['pos']['right'];
double bottom = location['pos']['bottom'];
```

### Navic's current `SelectionChanged` payload

```kotlin
data class SelectionChanged(
    val text: String? = null,
    val cfi: String? = null,
    val href: String? = null
) : ReaderBridgeEvent
```

### Missing from Navic

| Anx field | Purpose | Navic extension |
| --- | --- | --- |
| `footnote` | Whether the selection is a footnote reference | `SelectionChanged.footnote: Boolean?` |
| `contextText` | Surrounding context text | `SelectionChanged.contextText: String?` |
| `pos.left/top/right/bottom` | Selection bounding box for context menu positioning | `SelectionChanged.posLeft/posTop/posRight/posBottom: Double?` |

### Files to touch

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` — extend `SelectionChanged` + decode
- `composeApp/src/androidMain/assets/reader/navic-reader.js` — extend `selectionChanged` post (~line 1285)
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt` — update `SelectionChanged` debug label
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt` — update `onSelectionEnd` → `Exists`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/FoliateEpubEngineAdapterTest.kt` — update selection test

### Steps

1. Extend `SelectionChanged` with `footnote: Boolean?`, `contextText: String?`, `posLeft/posTop/posRight/posBottom: Double?`.

2. Add decode cases in `decodeReaderBridgeEvent`.

3. Update `engineDebugLabel()` for the richer `SelectionChanged`.

4. In `navic-reader.js` selection handler (~line 1285), extend the post with `footnote` (detect from anchor/section type), `contextText` (surrounding text from range context), and `pos` fields (from `getBoundingClientRect()` of the selection range).

5. Add parity guard in `FoliateAnxParityTest.kt` that reads `epub_player.dart:687-714` and asserts every field has a `SelectionChanged` counterpart.

6. Update `FoliateEpubEngineAdapterTest.kt` selection test.

### Validation

**Host:** GREEN on 2026-06-18.

- RED was captured in `build/codex-logs/phase5-red.err.log`: compile failed because `footnote`, `contextText`, and `posLeft/posTop/posRight/posBottom` did not exist on `SelectionChanged`, `ReaderEngineEvent.SelectionChanged`, and `ReaderSelection`.
- Focused Phase 5 test run passed in `build/codex-logs/phase5-focused.out.log`: `ReaderBridgeProtocolTest.selectionChangedDecodesAnxSelectionEndPayload`, `FoliateEpubEngineAdapterTest.mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome`, `ReaderControllerTest.engineCapabilityEventsFeedControllerStateWithoutOwningChrome`, and `FoliateAnxParityTest.phase5SelectionPayloadMatchesAnxSelectionEndContract`.
- Reader host suite passed in `build/codex-logs/phase5-reader-suite.out.log`.
- `node --check` passed for `navic-reader.js`, `navic-reader-content-interactions.js`, and `navic-reader-page-turns.js`.

**Android/emulator:** still pending. Do not treat Phase 5 as release-ready until a real reader selection flow confirms logcat emits `selectionChanged(footnote=..., pos=...)` for normal and footnote/reference selections.

### Codex review focus

- How should `footnote` be detected? Does Navic's Foliate fork expose footnote annotation type?
- Should `pos` be a nested object or flat fields?
- Does `selectionchange` fire too frequently? Should Navic debounce or only post on `selectionend`?

---

## Phase 6 — Style Dimension Parity (First 8 Fields)

**Grievance:** 5 (8 style dimensions missing from `ReaderSettings`)
**Goal:** Add the first 8 missing Anx `book_style.dart` style dimensions. `maxColumnCount` and `columnThreshold` were intentionally split to Phase 8 because they touch pagination profile math.
**Risk:** Medium — touches settings normalization, dialog UI, JS renderer.

### Fields covered by Phase 6

| Anx field | Anx default | Navic | Phase |
| --- | --- | --- | --- |
| `fontWeight` | 400 | — | Yes |
| `letterSpacing` | 0.0 | — | Yes |
| `wordSpacing` | 0.0 | — | Yes |
| `sideMargin` | 6.0 | `marginPercent` (one value) | Yes |
| `topMargin` | 90.0 | (collapsed) | Yes |
| `bottomMargin` | 50.0 | (collapsed) | Yes |
| `indent` | 0 | — | Yes |
| `headingFontSize` | 1.0 | — | Yes |
| `maxColumnCount` | 0 | — | Phase 8 |
| `columnThreshold` | 720.0 | — | Phase 8 |

### Files to touch

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` — add 8 fields + bridge serialization
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPreferenceSettings.kt` — normalization + persistence
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt` — Komikku settings dialog controls
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js` — JS-side application
- `composeApp/src/androidMain/assets/reader/navic-reader-settings.js` — settings normalization
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/FoliateAnxParityTest.kt` — 8 style gaps → `Exists`; the 2 adaptive composition fields remained Phase 8 work at this stage
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderSettingsDefaultsTest.kt` — update
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPreferenceSettingsTest.kt` — update

### Validation

**Host + Emulator:** Same pattern. At Phase 6 completion, `FoliateAnxParityTest` style assertions passed for the first 8 style dimensions. Phase 8 later closed the remaining 2 adaptive composition fields.

### 2026-06-18 Implementation Notes

- Added `fontWeight`, `letterSpacing`, `wordSpacing`, `sideMargin`, `topMargin`, `bottomMargin`, `indent`, and `headingFontSize` to `ReaderSettings`, default normalization, app preferences, per-book override merge/persistence, bridge serialization, pagination render metadata, runtime typography CSS, Foliate renderer margin/gap attributes, and the Komikku settings dialog.
- Preserved Anx defaults from `tmp/references/anx-reader/lib/models/book_style.dart`: `400`, `0`, `0`, `6`, `90`, `50`, `0`, and `1`.
- Updated `FoliateAnxParityTest` so those eight fields are `Exists`; `maxColumnCount` and `columnThreshold` remained Phase 8 work until the later adaptive composition slice.
- Focused Phase 6 host checks passed for bridge serialization, default normalization, preference round-trip, and Anx style-source parity.
- Full `:composeApp:testAndroidHost` passed after Phase 6.
- `node --check` passed for `navic-reader.js`, `navic-reader-helpers.js`, `navic-reader-pagination.js`, `navic-reader-appearance.js`, `navic-reader-content-interactions.js`, `navic-reader-page-turns.js`, and `navic-reader-pdf.js`.
- Emulator/device validation is pending; do not treat Phase 6 as release-ready until the controls visibly affect real EPUB layout without destabilizing pagination.

### Codex review focus

- Should `sideMargin`/`topMargin`/`bottomMargin` be in `dp`, `px`, or `%`? Anx uses raw numbers (interpreted as `px` in the WebView). Navic currently uses `marginPercent`.
- Should `fontWeight` be a `Double` or a sealed enum?
- Are the JS-side CSS variable names correct for Foliate's renderer?

---

## Phase 7 — PDF + Font Source Parity

**Grievances:** 6 (PDF parity unverified), 7 (font source unanchored)
**Goal:** Add parity guards for PDF and font source. If font source is missing remote manifest support, implement it (product work, not just test work).
**Risk:** Low for PDF guard. Medium for font source if remote manifest implementation is needed.

### Step 7a: PDF parity guard

1. Create `FoliatePdfAnxParityTest.kt` that reads `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614` and asserts Navic's `vendor/foliate-js/pdf.js` exposes the same `makePDF(file)` contract: sections, outline/TOC resolution, page lookup, cover.

2. If diverged, document as `Temporary adapter` with rationale. Do not patch unless it's a real bug.

### Step 7b: Font source parity guard + implementation

1. Create `ReaderFontSourceAnxParityTest.kt` that reads `lib/providers/fonts.dart:16-103`, `lib/service/font.dart:22-24`, `lib/models/font_model.dart:28-33` and asserts `ReaderImportedFont` + `ReaderImportedFontCache` cover:
   - Remote font manifest (fetch, download, cache, list)
   - Local font import (pick, cache, register)
   - WebView-accessible font URL serving
   - Font deletion

2. **Strict acceptance condition — no false-green:** The parity test must pass only if ALL of the following are true:
   - `ReaderImportedFont` model covers remote manifest entries (URL, name, family) AND local import entries (file path, name, family).
   - `ReaderImportedFontCache` (or equivalent) implements: remote manifest fetch, remote font download + cache, local font import + cache, WebView-accessible URL serving for both remote and local fonts, and font deletion for both.
   - Settings UI exposes both remote font source selection and local font import.

3. **If any of the above is missing** (likely — current `ReaderImportedFont` appears to cover local import only):
   - Local red during TDD is allowed (write the test, watch it fail, then implement).
   - But Phase 7 cannot be committed/closed until the font parity test is green, OR the gap is explicitly accepted by the user as a deferred feature with a follow-up item in this plan.
   - The gap is documented as `Failing` per the spec's status labels.
   - Implementation is product work:
     - Remote manifest fetch + parse (reference: `lib/providers/fonts.dart`)
     - Font download + cache (reference: same)
     - WebView-accessible URL serving for remote fonts (reference: `lib/models/font_model.dart`)
     - Settings UI for remote font sources (reference: Anx settings)
     - Font deletion for both local and remote fonts
   - **No committed red tests:** the hard rule at the top of this plan applies. The test may be red locally during TDD, but must not be committed red. If implementation is deferred, the registry entry stays as `Missing`/`Failing` (which is green in the known-gaps registry because the gap is documented), not committed as a failing test.

4. **Do not mark font parity as `Exists` in the known-gaps registry until the test is green.** If remote manifest support is not implemented, the registry entry stays as `Missing` or `Failing`.

### 2026-06-18 Phase 7 Guard Status

- PDF makePDF parity: Exists. `FoliatePdfAnxParityTest` reads `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614` and verifies Navic's bundled `vendor/foliate-js/pdf.js` preserves the Anx/Foliate `makePDF(file)` book contract: metadata, outline/TOC, sections, page lookup, split TOC hrefs, TOC fragment, and cover rendering. Navic's Android PDF.js loader, diagnostics, image page metadata, `spread: 'none'`, and adjacent-page prefetch remain local adapter extensions.
- Font local import parity: Partial. `ReaderFontSourceAnxParityTest` reads Anx `lib/service/font.dart` and `lib/models/font_model.dart` and verifies Navic covers local file import, cache storage, WebView-safe `/reader-cache/fonts/` URLs, imported font CSS, storage reporting, and clear-all deletion.
- Font remote manifest parity: Exists. Cache parser/downloader/list/delete is implemented in `ReaderImportedFontCache` for Anx-style `fontManifestUrl`, manifest entries, temp-to-final cache moves, WebView-safe remote font URLs, remote font listing, and deletion. Settings UI can refresh, download, select, and delete remote fonts. The common `ReaderFontImporter` boundary now exposes Anx-style per-font download state plus pause, resume, and cancel controls; Android backs this with active download jobs and chunk-level progress updates.
- Phase 7 focused guards passed on 2026-06-18 with `FoliatePdfAnxParityTest` and `ReaderFontSourceAnxParityTest`. Visual polish of the settings surface remains separate Komikku fidelity work; do not treat this as a finished Komikku settings redesign.

### Validation

**Host + Emulator:** Same pattern. `FoliateAnxParityTest` PDF and font source assertions pass (or gaps are documented as `Failing` with a follow-up item).

### Codex review focus

- Does Navic's `pdf.js` match Anx's `makePDF` contract?
- Does `ReaderImportedFont` support remote manifests, or only local import?
- If remote manifest support is needed, is the implementation plan complete?

---

## Phase 8 — Adaptive EPUB Page Composition

**Goal:** Add `maxColumnCount` and `columnThreshold` to `ReaderSettings` with multi-column pagination math.
**Risk:** High — touches the deterministic pagination profile system that the working `11/11` rail endpoint fix depends on.

### Why this was separated from Phase 6

`maxColumnCount` and `columnThreshold` are not just settings fields — they require multi-column pagination math in Foliate's paginator, which affects:
- Page count per section (the deterministic pagination profile)
- Page-number rendering (organic `# / #` layer)
- Chapter-local rail progress
- Section boundary drag preview

Bundling these into Phase 6 would mix risk levels and could destabilize the working pagination for a feature that isn't blocking Phases 1-7.

### 2026-06-18 Implementation Notes

- Added `maxColumnCount` and `columnThreshold` to `ReaderSettings`, default normalization, preference persistence, per-book overrides, bridge JSON, and pagination profile metadata.
- Mirrored Anx automatic-column semantics: `maxColumnCount == 0` means automatic, not disabled. The paginator computes up to two columns from viewport size and `columnThreshold`.
- Added Foliate paginator support for the `column-threshold` attribute and CSS variable so the same threshold drives rendered layout and pagination profile math.
- Added Komikku settings dialog controls for Columns (`Auto`, `Single`, `Double`) and Column threshold (`400..1200 px`).
- Added global Ebook settings for the same defaults.
- Moved `maxColumnCount` and `columnThreshold` from known Phase 8 gaps to `Exists` in `FoliateAnxParityTest`.

### Validation

**Host:** GREEN on 2026-06-18.

Commands executed:

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

**Emulator:** GREEN on 2026-06-18 with dirty `readerdev` install and full Komikku matrix rerun. See `2026-06-13-komikku-reader-port-validation-log.md`, entry `2026-06-18 Phase 8 Adaptive Composition Dirty Emulator Check`.

---

## Post-Phase 8: Full Parity Gate

After all 8 phases are complete:

```powershell
$ExpectedVersionName = (Select-String -Path 'androidApp\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches.Groups[1].Value
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.FoliateAnxParityTest"
.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"
```

This test must be fully green. It proves:
- Every Anx `epub_player.dart` callback (all 16) has a Navic counterpart: bridge event, product divergence with route-verified guard, or out-of-scope with rationale.
- Every Foliate `view.js` emit (all 9, including `click-image`/`click-view` omissions) is documented in the registry with route verification.
- Every Anx relocation/selection payload field has a Navic counterpart.
- All 10 Anx style dimensions are in `ReaderSettings`.
- PDF integration matches Anx's `makePDF` contract or divergences are documented.
- Font source model matches Anx's surfaces (remote manifest + local import + WebView URL + deletion) or gaps are documented as `Failing` (not false-green).
- Every `ReaderBridgeEvent` has an `engineDebugLabel()` branch for ADB visibility.
- Every `ProductDivergence` entry has a verified route chain, not just symbol existence.

**After Phase 8, the style parity gate is all 10 Anx `BookStyle` dimensions represented in `ReaderSettings`.** The remaining parity work is no longer allowed to treat style dimensions as deferred known gaps.

## Deferred Work (Not Part Of 8 Phases)

- **Annotation UI:** Phase 3 adds the `AnnotationClick`/`AnnotationDrawn` bridge events, but the UI for tapping an existing highlight to open its note is a separate feature.
- **In-book back-stack UI:** Phase 3 adds the `PushState`/`NavigationStateChanged` bridge events, but the back/forward UI is a separate feature.
- **Footnote popup UI:** Phase 3 adds the `FootnoteClose` bridge event, but the footnote popup UI is a separate feature.
