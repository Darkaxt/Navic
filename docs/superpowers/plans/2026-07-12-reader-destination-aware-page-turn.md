# Destination-Aware Reader Page-Turn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Rev 4's neutral reverse face, overlapping landscape navigation, half-fold hold, and post-animation blink with real destination-aware page surfaces in landscape and physically coherent alternating slide/turn behavior in portrait.

**Architecture:** A pure JavaScript page model resolves exact visual ordinals and transition roles from the complete pagination profile. One passive Foliate renderer stages the exact final page or spread without mutating the live reader; native Android freezes the current live surface, captures the staged final surface, and renders an immutable bitmap bundle while the live reader performs one exact target relocation. Landscape uses one real leaf and a two-page target; portrait uses a virtual spread with alternating camera slide and leaf-turn transitions.

**Tech Stack:** Kotlin Multiplatform, Android WebView and Canvas, Foliate JS, JavaScript ES modules, Node test runner, Kotlin test, Gradle Android host tests, readerdev emulator, ADB and Chrome DevTools Protocol.

---

## File Structure

### New files

- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
  - Pure visual-page locator, side resolver, and transition planner. No DOM access.
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
  - Owns the single passive Foliate renderer, role staging, readiness generations, and teardown.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
  - Parses JavaScript plans and owns Android bitmap bundle lifecycle.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
  - Orchestrates current PixelCopy, passive WebView draw capture, role cropping, generation validation, and bounded cache.
- `tools/reader-harness/src/page-turn-model.test.mjs`
  - Executes the pure JavaScript page model with Node's test runner.
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
  - Guards passive-renderer isolation, exact relocation, no mid-fold hold, cache bounds, and absence of timeouts.

### Modified files

- `composeApp/src/androidMain/assets/reader/navic-reader-pagination-model.js`
  - Re-exports or delegates inverse page lookup to the focused page-turn model.
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
  - Provides exact visual-page navigation and passive renderer layout parity.
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
  - Adds exact target dispatch and removes relative navigation from destination-aware commits.
- `composeApp/src/androidMain/assets/reader/navic-reader.js`
  - Mixes in passive-preview methods and exposes generation-based bridge functions.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt`
  - Keeps exact current-base PixelCopy and adds reusable page-rectangle conversion helpers.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
  - Orchestrates asynchronous bundle preparation, continuous commit, exact settlement, and lifecycle cancellation.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnCurlView.android.kt`
  - Draws current base, real underneath, front, reverse, final base, and portrait camera offset.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
  - Passes orientation and lifecycle changes and delegates portrait transition kind.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
  - Adds preparation and settlement semantics and removes the mid-fold destination gate.
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnStateMachineTest.kt`
  - Locks release-during-preparation and continuous commit behavior.
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`
  - Replaces Rev 4 hold assertions with final-base settlement assertions.
- `tools/reader-harness/package.json`
  - Adds the pure model test command.

## Task 1: Pure Visual-Page Model

**Files:**
- Create: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Create: `tools/reader-harness/src/page-turn-model.test.mjs`
- Modify: `tools/reader-harness/package.json`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-pagination-model.js`

- [ ] **Step 1: Write failing Node tests for inverse page lookup**

Create fixtures with adjacent chapters and assert that visual indices at the start, middle, end, and chapter boundary resolve to the exact locator:

```js
assert.deepEqual(readerPageLocatorForVisualIndex(profile, 4), {
  pageIndex: 4,
  pageCount: 12,
  spineIndex: 7,
  href: 'chapter-2.xhtml',
  chapterPageIndex: 1,
  chapterPageCount: 3,
  anchor: 0.5,
})
```

- [ ] **Step 2: Run the model test and verify RED**

Run:

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
```

Expected: FAIL because `navic-reader-page-turn-model.js` and the test script command do not exist.

- [ ] **Step 3: Implement inverse lookup and side resolution**

Implement these exports:

```js
export const ReaderPhysicalPageLeft = 'left'
export const ReaderPhysicalPageRight = 'right'
export const ReaderPhysicalPageCenter = 'center'

export function readerPageLocatorForVisualIndex(profile, requestedIndex) { /* exact chapter lookup */ }
export function readerPhysicalPageSide({ pageIndex, explicitSide, readerDirection, coverSide }) { /* metadata then parity */ }
```

The anchor is `chapterPageIndex / (chapterPageCount - 1)` when the chapter has multiple pages and `0` otherwise.

- [ ] **Step 4: Add failing landscape planner tests**

Assert LTR forward from spread start `16` produces:

```js
{
  kind: 'landscape-leaf',
  sourcePageIndex: 16,
  turningFrontPageIndex: 17,
  turningReversePageIndex: 18,
  underneathPageIndex: 19,
  targetPageIndex: 18,
}
```

Assert previous from `16` produces front `16`, reverse `15`, underneath `14`, target `14`. Add mirrored physical roles for RTL without changing logical ordinals.

- [ ] **Step 5: Implement `readerPageTurnPlan` for landscape**

```js
export function readerPageTurnPlan({
  currentPageIndex,
  pageCount,
  layoutMode,
  logicalDirection,
  currentPageSide,
  readerDirection,
}) { /* immutable role plan or null at boundary */ }
```

Reject a plan if any required visual ordinal is outside the publication.

- [ ] **Step 6: Add failing portrait planner tests**

For LTR:

- left + next -> `portrait-slide`, target `+1`
- right + next -> `portrait-leaf`, reverse `+1`, underneath `+2`
- right + previous -> `portrait-slide`, target `-1`
- left + previous -> `portrait-leaf`, reverse `-1`, underneath `-2`

Add the RTL mirror and explicit `page-spread-*` override cases.

- [ ] **Step 7: Implement portrait transition planning**

The planner must return source and target physical sides so decoration and camera motion use the same decision.

- [ ] **Step 8: Run model tests and verify GREEN**

Run:

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js
```

Expected: all model tests pass and syntax check exits `0`.

- [ ] **Step 9: Commit Task 1**

```powershell
git add composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js composeApp/src/androidMain/assets/reader/navic-reader-pagination-model.js tools/reader-harness
git commit -m "feat(reader): model physical page transitions"
```

## Task 2: Exact Target Navigation and Settlement

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader.js`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [ ] **Step 1: Write failing source tests for exact navigation**

Assert production contains a `goToVisualPage` bridge command, calls `readerPageLocatorForVisualIndex`, navigates once through `renderer.goTo({ index, anchor })`, and does not call relative `view.next()` or `view.prev()` in the destination-aware commit path.

- [ ] **Step 2: Run focused host test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

Expected: FAIL because the exact target command is absent.

- [ ] **Step 3: Implement exact target relocation**

Add:

```js
async function goToVisualPage(pageIndex, settleToken = '') {
  const locator = readerPageLocatorForVisualIndex(this.paginationProfile, pageIndex)
  if (!locator) throw new Error(`Visual page ${pageIndex} is unavailable`)
  this.beginControlledRelocation('page-turn:exact')
  await this.view.renderer.goTo({ index: locator.spineIndex, anchor: locator.anchor })
  this.view.history?.pushState?.({
    href: locator.href,
    chapterPageIndex: locator.chapterPageIndex,
    chapterPageCount: locator.chapterPageCount,
  })
  return locator
}
```

Settlement state includes both token and settled visual page index. The bridge returns a JSON object rather than a string token only.

- [ ] **Step 4: Add settlement mismatch tests**

Assert the native contract cannot treat token equality as sufficient when the reported page index differs from the requested target.

- [ ] **Step 5: Run focused tests and syntax checks**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
node --check composeApp/src/androidMain/assets/reader/navic-reader-pagination.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
node --check composeApp/src/androidMain/assets/reader/navic-reader.js
```

Expected: focused tests and all syntax checks pass.

- [ ] **Step 6: Commit Task 2**

```powershell
git add composeApp/src/androidMain/assets/reader composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt
git commit -m "feat(reader): navigate page turns to exact targets"
```

## Task 3: Passive Renderer Feasibility Gate

**Files:**
- Create: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- Modify: `tools/reader-harness/src/reader-trace-assertions.mjs`

- [x] **Step 1: Write failing isolation guards**

Assert the passive renderer:

- creates one `foliate-view` marked `data-navic-page-turn-preview`
- opens the publication once per reader session
- applies the same viewport and document theme methods
- has no live `relocate`, history, selection, highlight, progress, or overlay-post listener
- closes and removes itself on publication close

- [x] **Step 2: Run focused test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

Expected: FAIL because the passive renderer module is absent.

- [x] **Step 3: Implement passive renderer lifecycle**

Export a focused mixin with these methods:

```js
ensurePageTurnPreviewRenderer()
preparePageTurnPreview(token, plan)
exposePageTurnPreviewFinal(token)
pageTurnPreviewState(token)
restorePageTurnLiveComposition(token)
destroyPageTurnPreviewRenderer(reason)
```

Use one generation per preparation. Apply current settings and layout before and after navigation. Ready state is emitted only after the target relocation and two animation frames have produced stable page geometry; the frames are readiness observations, not time-based cancellation.

- [x] **Step 4: Add a debug-only bridge feasibility path**

Expose synchronous bridge methods that start preparation and query state. Do not connect them to production gestures yet.

- [x] **Step 5: Build readerdev once for the feasibility gate**

```powershell
.\gradlew.bat :androidApp:assembleReaderDev
adb -s emulator-5554 install -r androidApp/build/outputs/apk/readerDev/Navic.apk
```

Expected: build and install exit `0`.

- [x] **Step 6: Prove visual parity on emulator**

With Alcatraz open in tablet landscape:

1. Capture the live spread.
2. Prepare the same visual page in the passive renderer.
3. Expose it beneath a native/current freeze.
4. Capture the staged spread.
5. Compare page rectangles and screenshots.

Acceptance:

- same text lines and headings
- same font and spacing
- same paper/edge/stain overlays
- no live location or progress message
- no visible flash
- passive renderer count remains one

Store evidence under `captures/page-turn-destination-aware/feasibility/`.

Measured 2026-07-12 on `emulator-5554` with Alcatraz visual page index 5: the live and decorated passive 2960 x 1848 captures differed in 0.4125% of pixels with mean absolute RGB error below 0.38, attributable to independent WebView text antialiasing. Page geometry, text, paper, edges, gutter, back-cover slivers, and page numbers matched. Raw captures remain local under `.codex-evidence/page-turn-preview/` to avoid committing roughly 14 MB of redundant PNG data.

- [x] **Step 7: Run tests and commit only if the gate passes**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js
git add composeApp/src/androidMain/assets/reader tools/reader-harness composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt captures/page-turn-destination-aware/feasibility
git commit -m "feat(reader): add isolated page-turn preview renderer"
```

Expected: tests pass, visual gate evidence is accepted, and commit succeeds. If parity fails, revise this task without changing production gesture behavior.

## Task 4: Immutable Android Bitmap Bundle

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [x] **Step 1: Write failing parser and lifecycle source tests**

Assert the bundle owns `currentBase`, `turningFront`, `turningReverse`, `underneath`, and `finalBase`; all bitmaps recycle exactly once; cache capacity is three bundles; stale generations recycle results.

- [x] **Step 2: Run focused test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [x] **Step 3: Implement immutable plan parsing**

Parse transition kind, all page indices, source/target sides, target index, rectangles, direction, and generation from the JavaScript JSON plan. Reject missing required fields before allocating bitmaps.

- [x] **Step 4: Implement current and staged capture**

- current base: exact-window `PixelCopy`
- staged final: `webView.draw(canvas)` clipped and translated to the exact WebView page/spread rectangle while the current-base native freeze is opaque
- turning front: crop from current base
- reverse and underneath: crop from final base according to role mapping

Every draw runs on the main thread and reports completion through a callback. No polling delay or cancellation timeout is introduced.

- [x] **Step 5: Implement bounded cache and invalidation**

Use an access-ordered map with maximum three bundles. Eviction, rotation, settings change, backgrounding, destroy, and stale generation recycle every owned bitmap.

- [x] **Step 6: Run focused test and commit**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt
git commit -m "feat(reader): capture destination page bundles"
```

## Task 5: Continuous Commit State Machine

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnStateMachineTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`

- [x] **Step 1: Write failing state tests**

Add tests for:

- latest pointer replaces prior pending pointer during preparation
- release during preparation records exactly one commit or relax decision
- commit animation completes before Foliate navigation begins
- completed animation enters `Settling` with `finalBase` visible, then starts exact navigation on the next rendered frame
- exact destination settlement after navigation detaches once; premature settlement is ignored
- wrong settled page index remains in `Settling`

- [x] **Step 2: Run state tests and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnStateMachineTest"
```

Expected: FAIL because `Preparing` and `Settling` do not exist and Rev 4 still holds at half fold.

- [x] **Step 3: Implement state transitions**

Replace the Rev 4 hold gate with:

```text
Preparing -> Deforming -> Committing -> Settling -> Idle
Preparing -> Relaxing -> Idle
```

`animationFinished()` marks native completion, emits `ShowFinalBase`, and then requests exact navigation. `destinationSettled(targetIndex)` marks exact live completion only after that request; detach emits once after settlement.

- [x] **Step 4: Update controller to animate continuously**

Remove `CommitHoldProgress`, `commitHoldReached`, and the binding pause. Animate from release progress through the fully turned final state in one `ValueAnimator`. At completion, switch the view to static `finalBase`, render that opaque frame, then start exact Foliate navigation behind it and await settlement.

- [x] **Step 5: Update Rev 4 source test**

Replace `committedTurnHoldsTheFoldUntilFoliateSettlesThenFinishes` with assertions that no hold constant exists and delayed settlement displays `finalBase`.

- [x] **Step 6: Run tests and commit**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnStateMachineTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnStateMachineTest.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt
git commit -m "fix(reader): keep committed page turns continuous"
```

## Task 6: Landscape Real Leaf Integration

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnCurlView.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader.js`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`

- [x] **Step 1: Write failing native composition guards**

Assert the renderer receives five bundle surfaces, draws underneath before front/reverse, draws `finalBase` after native completion, and the controller dispatches target page index instead of a relative action for landscape Canvas commits.

- [x] **Step 2: Run focused tests and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [x] **Step 3: Render real surfaces**

Keep current-base as the stable background. Replace only the source-side page with underneath. Map turning-front and turning-reverse bitmaps through the accepted edge-fold geometry. Once progress reaches the final state, draw final-base without a mesh.

- [x] **Step 4: Wire controller preparation and exact commit**

The controller starts bundle preparation on the first gesture update, stores latest pointer state, attaches only after a valid bundle, and sends `goToVisualPage(targetPageIndex, token)` exactly once at commit start.

- [x] **Step 5: Run focused tests and readerdev build**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
.\gradlew.bat :androidApp:assembleReaderDev
```

- [x] **Step 6: Validate landscape on emulator**

Record Alcatraz transitions:

- forward same chapter
- forward across chapter boundary
- previous
- cancel
- slow drag
- fast flick

Acceptance: correct reverse and underneath text, final spread advances two page ordinals, no half-fold freeze, no final blink, and one live relocation.

Validated 2026-07-12 on `emulator-5554` with Alcatraz. Same-chapter forward, cross-chapter forward, previous, short-drag cancel, slow drag, and fast flick all used real destination/reverse surfaces. Committed turns reported the exact requested visual index, cancel emitted no relocation, and the native overlay detached after exact spine/chapter-page settlement. The frame contact sheet is retained locally under `.codex-evidence/page-turn-preview/settlement-fix-contact.png`.

- [x] **Step 7: Commit accepted landscape integration**

```powershell
git add composeApp/src/androidMain composeApp/src/androidHostTest captures/page-turn-destination-aware/landscape
git commit -m "feat(reader): render destination-aware landscape leaves"
```

## Task 7: Portrait Virtual-Spread Motion

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnCurlView.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [x] **Step 1: Write failing portrait source guards**

Assert portrait distinguishes `portrait-slide` and `portrait-leaf`, advances one ordinal, never creates a landscape gutter, and applies binding/reveal side from the same target-side plan.

- [x] **Step 2: Run focused test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [x] **Step 3: Implement virtual camera slide**

For left-to-right LTR movement, draw source and target pages on a virtual two-page canvas and translate the camera smoothly between them. No fold geometry is active.

- [x] **Step 4: Implement portrait leaf and camera return**

For right-to-next-left LTR movement, render current right as front, target left as reverse, following right as underneath, then settle the camera on target left. Previous and RTL use the planner's mirrored roles.

- [x] **Step 5: Alternate decoration side**

Drive the portrait binding hint and thin cover reveal from `sourcePageSide` during drag and `targetPageSide` after settlement. The cover reveal stays on the outer edge and preserves its accepted thin width.

- [x] **Step 6: Validate portrait on emulator**

Use a portrait tablet-sized readerdev viewport and record four consecutive forward and four previous actions. Acceptance: transitions alternate slide/leaf, text is correct, decoration swaps sides, and no landscape gutter appears.

Validated the production planner and renderer on `emulator-5554` at 1848 x 2960. Consecutive forward drags reported `PortraitSlide` then `PortraitLeaf`, advanced exactly one visual page each, and the leaf bundle used distinct reverse and underneath captures. The portrait decoration now follows the same physical-side calculation as the transition plan; a live null-to-zero coercion found during validation was fixed and guarded. The cold-bundle recordings also measured a 2.6-second slide preparation and 4.3-second leaf preparation, which is intentionally carried into Task 8 as a blocking lifecycle/performance defect: Task 8 must prewarm adjacent bundles and use immediate exact navigation when a released gesture has no ready bundle.

- [x] **Step 7: Run tests and commit**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
git add composeApp/src/androidMain composeApp/src/androidHostTest captures/page-turn-destination-aware/portrait
git commit -m "feat(reader): alternate portrait slide and leaf turns"
```

## Task 8: RTL, Boundary, Fixed-Layout, and Lifecycle Safeguards

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `tools/reader-harness/src/page-turn-model.test.mjs`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [x] **Step 1: Add failing edge-case tests**

Cover:

- first and final publication boundaries
- one-page chapter boundaries
- explicit center page
- RTL physical mirroring
- fixed-layout section page spreads
- rotation during preparation and deformation
- background/destroy during capture
- stale passive readiness after settings change
- memory pressure recycling

- [x] **Step 2: Run focused model and host tests and verify RED**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [x] **Step 3: Implement explicit fallbacks**

If a valid complete bundle cannot be produced, restore live composition and send the correct direct exact target navigation. Do not display a neutral reverse page or disable all later animation for a role-specific failure.

- [x] **Step 4: Wire lifecycle generations**

Increment generation and recycle transient bundles on size change, settings fingerprint change, pause, detach, destroy, publication change, and memory pressure. Late callbacks compare generation before storing any bitmap or changing UI.

- [x] **Step 5: Run focused and full source guards**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [x] **Step 6: Commit safeguards**

```powershell
git add composeApp tools/reader-harness
git commit -m "fix(reader): harden destination page-turn lifecycle"
```

Evidence recorded before commit:

- the page-turn model suite passed all 13 cases, including RTL portrait previous and single-page/center fallbacks;
- `ReaderPageTurnNativeSourceTest` and `ReaderPageTurnStateMachineTest` passed together;
- readerdev portrait leaf validation logged drag preview at `10:56:43.548` and overlay attachment at `10:56:43.562`;
- frame capture showed continuous deformation into the real incoming page without the former half-fold hold;
- shell-cover visibility now invalidates and suppresses prewarm, preventing native cover pixels from contaminating a resumed text-page bundle.

Follow-up live validation closed two compositor/lifecycle gaps:

- staged destination capture now waits for `WebView.postVisualStateCallback` and one animation frame, preventing portrait `finalBase` from retaining the outgoing page;
- rotation prewarm now waits until native dimensions and JavaScript `layoutMode` agree, preventing stale landscape bundles in portrait and stale portrait bundles in landscape;
- background/foreground invalidation returned to the same spread and rebuilt both adjacent bundles asynchronously;
- a slow sub-threshold edge drag attached a warm overlay, relaxed to the identical spread, and emitted no exact navigation.

## Task 9: Full Verification and Visual Acceptance

**Files:**
- Modify only if verification exposes a regression.

- [x] **Step 1: Run JavaScript syntax checks**

```powershell
Get-ChildItem composeApp/src/androidMain/assets/reader -Filter 'navic-reader*.js' | ForEach-Object { node --check $_.FullName }
```

Expected: every command exits `0`.

- [x] **Step 2: Run focused model and host suites**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
```

- [x] **Step 3: Run complete Android host suite**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
```

Expected: no new failures compared with the theta92 baseline. Any environmental/reference failures are listed exactly rather than described as green.

Final merged-tree result: 2,195 tests executed with 29 pre-existing baseline/environment failures and no destination-aware or paper-surface failures. The remaining set is three unmocked Android `BitmapFactory` calls, one existing Foliate known-gap assertion, five missing Komikku reference-file cases, eighteen missing Komikku common-UI reference cases, one existing ADB texture-probe assertion, and one missing Anx paginator reference.

- [x] **Step 4: Run reader harness**

```powershell
npm --prefix tools/reader-harness run smoke
```

Expected: smoke scenarios pass, including the page-turn migration and navigation assertions.

- [x] **Step 5: Build and install final readerdev candidate**

```powershell
.\gradlew.bat :androidApp:assembleReaderDev
adb -s emulator-5554 install -r androidApp/build/outputs/apk/readerDev/androidApp-readerDev.apk
```

- [x] **Step 6: Capture final visual matrix**

Capture screenshots and recordings for:

- portrait left-to-right slide
- portrait right-to-left leaf
- landscape forward and previous leaf
- cross-section turn
- cancel
- slow drag
- fast flick
- rotate during preparation

Save under `captures/page-turn-destination-aware/final/` and inspect frame continuity, text identity, page-number progression, and absence of blink.

Validated on `emulator-5554` at tablet landscape and portrait resolutions. Forward and reverse landscape leaves, portrait slide and leaf transitions, cross-section relocation, cancel, slow drag, fast flick, rotation, and background/foreground restoration all retained real destination content. The terminal fold remained frame-continuous, `finalBase` covered exact relocation, cancellation emitted no relocation, and lifecycle invalidation rebuilt only layout-compatible adjacent bundles. Large recordings and frame sheets remain local under `.codex-evidence/capture-readiness/` rather than being committed.

- [ ] **Step 7: Run diff and worktree checks**

```powershell
git diff --check
git status --short
git log --oneline master..HEAD
```

Expected: clean diff check, only intentional evidence or source changes before final commit.

- [ ] **Step 8: Commit verification evidence**

```powershell
git add captures/page-turn-destination-aware docs/superpowers
git commit -m "test(reader): verify destination-aware page turns"
```

## Task 10: Sync, Release, and Public Verification

**Files:**
- Modify release/version files following the repository's current theta release procedure.

- [ ] **Step 1: Fetch and integrate current master**

```powershell
git fetch --all --prune
git merge fork/master
```

Resolve conflicts by preserving newer master behavior and reapplying the destination-aware contract. Re-run Task 9 after any conflict resolution.

- [ ] **Step 2: Merge the feature into local master**

From the authoritative master worktree:

```powershell
git merge --no-ff feat/destination-aware-page-turns
```

- [ ] **Step 3: Prepare the next theta version**

Increment from the current public release using the next numeric theta suffix. Update release notes with:

- real reverse and underneath page content
- exact two-page landscape spread commits
- alternating portrait slide/leaf behavior
- removal of mid-fold freeze and post-turn blink
- passive-renderer isolation and bounded snapshot cache

- [ ] **Step 4: Run the release build once after all implementation is final**

Use the repository's release Gradle task and signing environment. Do not run repeated full release builds during development.

- [ ] **Step 5: Commit, push master, tag, and publish**

Push `master` and the annotated release tag to `fork`. Create the GitHub release and upload the signed `Navic.apk`.

- [ ] **Step 6: Verify public release**

Verify:

- tag points at pushed master
- GitHub release is public
- uploaded APK is the signed release artifact, not readerdev/debug
- APK package, version name, and version code match the release
- asset SHA-256 is recorded
- remote master is not missing the release commit

- [ ] **Step 7: Mark the active goal complete only after public verification**

Report the release URL, tag, commit, version code, APK SHA-256, test results, emulator evidence path, and any residual device-only risk.
