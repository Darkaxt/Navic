# Reader Snapshot-Wave Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` or `executing-plans` to implement this plan task-by-task. Every production change follows RED/GREEN/REFACTOR and every completed task is committed before starting the next one.

**Goal:** Replace the curl/reverse-face Canvas page turn with a Google Play Books-style snapshot wave that supports immediate consecutive portrait page turns and page-aware landscape spread turns while Foliate settles exact targets in the background.

**Architecture:** Reuse the existing exact visual-page locator, passive Foliate renderer, half-resolution capture, generation invalidation, exact relocation, five-snapshot rolling cache, and serialized settlement. Compose source/destination snapshots through one bounded reusable front-leaf mesh whose stable interior stays at 1:1 scale and whose deformation is localized beside the moving edge. Portrait treats the page as one leaf. Landscape keeps complete-spread snapshots for cache/final shields but splits composition at the resolved gutter so only the active physical leaf deforms.

**Tech Stack:** Kotlin Multiplatform, Android View/Canvas, Android WebView, Foliate JavaScript, Node test runner, Kotlin test, Gradle Android host tests, readerdev emulator, ADB, Chrome DevTools Protocol.

**Release rule:** Debug and readerdev APKs are allowed throughout implementation. Do not publish a final release until the corrective Tasks 13-17 pass and the user accepts the visual result. The earlier rigid-renderer evidence from Tasks 8-12 is not a release gate.

**Rev 2 correction (2026-07-13):** Emulator acceptance rejected the Task 3 rigid translation and Task 9 full-spread moving-card behavior. Tasks 1-7 and 10 remain valid foundation. The rendering portions of Tasks 3, 8, and 9 are superseded by Tasks 13-17 below. No release gate may use the old rigid renderer as evidence.

---

## Existing Components To Reuse

- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
  - exact visual-page lookup, side resolution, and source/target planning
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
  - passive Foliate renderer creation and exact-page staging
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt`
  - current surface capture and foreground validation
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
  - passive draw capture, generation validation, and half-resolution bitmap helpers
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
  - touch lifecycle, release animation, exact relocation, and final shield
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
  - release-only commit and generation-safe state transitions
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
  - Android gesture ownership and WebView cancellation after touch slop

## New Files

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageSlideCoordinator.kt`
  - pure visual-vs-settled position and coalesced settlement state
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageSlideCoordinatorTest.kt`
  - pure coordinator behavior and multi-turn settlement tests
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt`
  - persistent two-snapshot Canvas renderer with one reusable active-leaf wave mesh
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideViewSourceTest.kt`
  - draw-order, progress-contract, and no-curl source guards
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnSlidePerformanceSourceTest.kt`
  - persistent preview, rolling-cache bounds, and no-timeout guards

## Files Replaced Or Removed At Final Cutover

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnCurlView.android.kt`
  - retained only during debug comparison, then removed or made unreachable
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnEdgeFoldGeometry.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnEdgeFoldGeometryTest.kt`
  - remove when no production caller remains

## Modified Files

- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnStateMachineTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleTest.kt`
- `tools/reader-harness/src/page-turn-model.test.mjs`

---

## Task 1: Lock The Flat-Slide Contract

**Files:**
- Modify: `tools/reader-harness/src/page-turn-model.test.mjs`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [ ] **Step 1: Add failing model tests for the simplified transition plan**

Assert that portrait transitions expose only source, target, direction, and layout:

```js
assert.deepEqual(readerPageTurnPlan({
  currentPageIndex: 6,
  direction: 'next',
  layoutMode: 'single',
  pageCount: 20,
}), {
  kind: 'portrait-slide',
  sourcePageIndex: 6,
  targetPageIndex: 7,
  logicalDirection: 'next',
  physicalDirection: 'toward-left',
})
```

Add landscape assertions for `6 -> 8` and `6 -> 4`. Assert absence of `turningFrontPageIndex`, `turningReversePageIndex`, and `underneathPageIndex`.

- [ ] **Step 2: Run the pure model test and verify RED**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
```

Expected: FAIL because current plans still expose leaf/reverse-face roles.

- [ ] **Step 3: Simplify the JavaScript planner**

Keep exact locator and boundary behavior. Return only source, target, logical/physical direction, and `portrait-slide` or `landscape-spread-slide`.

- [ ] **Step 4: Add a source guard against production curl roles**

In `ReaderPageTurnDestinationSourceTest`, assert that the active planner and controller path do not require these identifiers:

```text
turningReversePageIndex
underneathPageIndex
LandscapeLeaf
PortraitLeaf
```

- [ ] **Step 5: Run tests and verify GREEN**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js tools/reader-harness/src/page-turn-model.test.mjs composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt
git commit -m "test(reader): lock snapshot slide transition contract"
```

## Task 2: Replace Transition Bundles With Reusable Snapshots

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleTest.kt`

- [ ] **Step 1: Write failing snapshot model tests**

Test:

- one bitmap plus exact physical rectangle per snapshot
- transition references source and destination snapshots
- closing a transition does not double-close a snapshot retained by the cache
- no reverse-face or underneath bitmap fields remain
- progress-facing transition kind is `PortraitSlide` or `LandscapeSpreadSlide`

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleTest"
```

- [ ] **Step 3: Implement the simplified data model**

Introduce:

```kotlin
internal data class ReaderPageSlideSnapshotKey(/* parity inputs + visual index */)
internal class ReaderPageSlideSnapshot(/* key, bitmap, physical rect, color */)
internal data class ReaderPageSlideTransition(/* source, destination, direction */)
```

Snapshot ownership remains with the cache. A transition borrows snapshots and never closes them directly.

- [ ] **Step 4: Run and verify GREEN**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleTest"
```

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleTest.kt
git commit -m "refactor(reader): model page turns as reusable snapshots"
```

## Task 3: Add The Snapshot Renderer Foundation (Rigid Drawing Superseded By Task 14)

**Status:** Completed as historical foundation. Do not reimplement its rejected rigid drawing path. Task 14 replaces production drawing while retaining the persistent view, bitmap ownership, physical surface rectangles, and allocation guards established here.

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideViewSourceTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`

- [x] Add one persistent renderer view for the reader session.
- [x] Draw source and destination snapshots in physical surface rectangles.
- [x] Keep progress normalized to `0..1` and reachable at both endpoints while the finger remains down.
- [x] Allocate no bitmap, JSON object, shader, path, or vertex buffer inside `onDraw`.
- [x] Route readerdev `canvas` mode through the snapshot renderer.
- [ ] Replace the rejected complete-surface translation with the bounded leaf mesh in Task 14.

## Task 4: Convert Capture Into A Five-Snapshot Rolling Cache

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnSlidePerformanceSourceTest.kt`

- [ ] **Step 1: Write failing cache tests and source guards**

Assert:

- maximum five retained snapshots
- immediate previous/current/immediate next survive far-edge eviction
- duplicate snapshot requests share one in-flight capture
- stale generation results close their bitmap and do not enter the cache
- passive preview teardown is absent from routine prewarm completion
- `ReaderPageTurnAnimationBitmapScale` remains `0.5f`
- no timeout API appears in capture or preview code

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnSlidePerformanceSourceTest"
```

- [ ] **Step 3: Implement snapshot capture**

Refactor bundle capture into `snapshotFor(visualPageIndex, generation)`. Reuse current PixelCopy for the currently settled live surface and passive-renderer draw capture for other exact targets.

Always draw half-resolution bitmap pixels into the exact physical destination rect. Do not use bitmap width as a physical drag width.

- [ ] **Step 4: Keep passive preview alive**

In `navic-reader-page-turn-preview.js`, split routine staging completion from lifecycle teardown. Teardown only on publication/settings/layout/session/memory invalidation.

- [ ] **Step 5: Implement rolling prewarm**

After visual index `N`, request in priority order:

```text
N
N - step
N + step
N - 2 * step
N + 2 * step
```

where `step` is one in portrait and two in landscape.

- [ ] **Step 6: Run and verify GREEN**

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnSlidePerformanceSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [ ] **Step 7: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnSlidePerformanceSourceTest.kt
git commit -m "perf(reader): keep a rolling page snapshot cache"
```

## Task 5: Decouple Visual Position From Foliate Settlement

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageSlideCoordinator.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageSlideCoordinatorTest.kt`

- [ ] **Step 1: Write failing coordinator tests**

Cover:

1. Visual commit `6 -> 7` updates visual index before settlement.
2. A second visual commit `7 -> 8` is accepted while `7` is settling.
3. Pending target coalesces to `8`, not `[7, 8]`.
4. Settlement at `7` dispatches exact target `8`.
5. Settlement at `8` permits final-shield removal.
6. Stale generation settlement is ignored.
7. Cancel does not change either index.
8. Boundary attempts do not dispatch settlement.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageSlideCoordinatorTest"
```

- [ ] **Step 3: Implement the pure coordinator**

Expose effects instead of performing I/O:

```kotlin
sealed interface ReaderPageSlideCoordinatorEffect {
    data class SettleExact(val pageIndex: Int) : ReaderPageSlideCoordinatorEffect
    data object RemoveFinalShield : ReaderPageSlideCoordinatorEffect
}
```

Track `visualPageIndex`, `settledPageIndex`, `activeSettlementTarget`, `pendingTargetPageIndex`, and generation.

- [ ] **Step 4: Run and verify GREEN**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageSlideCoordinatorTest"
```

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageSlideCoordinator.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageSlideCoordinatorTest.kt
git commit -m "feat(reader): coordinate visual and settled page positions"
```

## Task 6: Integrate Serialized Exact Settlement

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnStateMachineTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`

- [ ] **Step 1: Add failing controller/state tests**

Assert:

- visual completion returns to gesture-ready state before Foliate settlement
- exactly one live relocation is active
- a newer visual target replaces the pending target
- final shield remains until the latest target is settled and renderable
- release duration scales with remaining `0..1` distance
- no fixed post-animation delay or cancellation timeout exists

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnStateMachineTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [ ] **Step 3: Integrate the coordinator**

On visual completion, update the snapshot window and re-enable gesture input. Dispatch exact JS relocation only from `SettleExact`. On JavaScript settlement, feed the reported visual index back into the coordinator.

- [ ] **Step 4: Preserve the opaque final shield**

The destination snapshot remains visible while live Foliate catches up. Remove it only after `RemoveFinalShield` and a successful live renderability check.

- [ ] **Step 5: Run and verify GREEN**

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnStateMachineTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageTurnStateMachineTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt
git commit -m "feat(reader): settle snapshot slides behind visual continuity"
```

## Task 7: Make Gesture Ownership Deterministic

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`

- [ ] **Step 1: Add failing no-tap and endpoint tests**

Assert:

- crossing touch slop sends `ACTION_CANCEL` to the WebView path exactly once
- a claimed cold drag stays claimed while capture prepares
- progress may reach `1f` before `ACTION_UP`
- releasing at `1f` commits without a midpoint hold
- releasing at `0f` cancels immediately
- a gesture accepted after visual completion does not wait for live settlement

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [ ] **Step 3: Implement deterministic claim**

Preserve the existing 12 px drag claim from `8074d22e`, but route all claimed motion to the slide controller. Retain the latest displacement and release state if a cold snapshot is still preparing.

- [ ] **Step 4: Run and verify GREEN**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt
git commit -m "fix(reader): keep claimed page drags out of tap fallback"
```

## Task 8: Complete Portrait Snapshot/Caching Behavior (Rendering Superseded By Task 15)

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Modify: `tools/reader-harness/src/page-turn-model.test.mjs`

- [ ] **Step 1: Add failing portrait direction tests**

Test LTR and RTL forward/backward source/destination draw ownership. Test adjacent chapter boundary and cover-to-first-page transitions using exact visual indices.

- [ ] **Step 2: Run and verify RED**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
```

- [ ] **Step 3: Implement portrait snapshot staging**

Always capture one exact visual page per snapshot. Do not fabricate a hidden second page or use landscape gutter geometry.

- [ ] **Step 4: Run and verify GREEN**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js tools/reader-harness/src/page-turn-model.test.mjs
git commit -m "feat(reader): complete portrait snapshot page slides"
```

## Task 9: Complete Landscape Spread Identity (Rigid Rendering Superseded By Task 16)

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Modify: `tools/reader-harness/src/page-turn-model.test.mjs`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [ ] **Step 1: Add failing complete-spread tests**

Assert:

- forward target is `source + 2`
- previous target is `source - 2`
- capture rectangle is the complete Foliate spread
- gutter is part of the captured bitmap, not a moving native overlay
- terminal one-page spread uses Foliate's resolved rectangle
- RTL mirrors translation direction without changing logical targets

- [ ] **Step 2: Run and verify RED**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

- [ ] **Step 3: Implement full-spread snapshots**

Capture and draw one bitmap per complete spread. Remove all per-leaf reverse/underneath capture branches.

- [ ] **Step 4: Run and verify GREEN**

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageTurnSlideViewSourceTest"
```

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js tools/reader-harness/src/page-turn-model.test.mjs composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt
git commit -m "feat(reader): slide complete landscape spreads"
```

## Task 10: Lifecycle, Failure, And Memory Hardening

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnSlidePerformanceSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`

- [ ] **Step 1: Add failing lifecycle tests**

Cover publication switch, font/theme change, viewport change, rotation, app pause/resume, explicit cache clear, capture failure, passive-renderer failure, and memory pressure.

Assert stale callbacks cannot attach and all obsolete bitmaps close exactly once.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnSlidePerformanceSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

- [ ] **Step 3: Implement generation and fallback behavior**

For cold capture failure, keep an opaque current/final shield, perform exact target navigation, and reveal only after renderable settlement. Do not call tap navigation and do not add timeouts.

- [ ] **Step 4: Remove active curl dependencies**

Once all slide tests pass:

- route production `canvas` to `ReaderPageTurnSlideView`
- remove the debug comparison flag
- delete `ReaderPageTurnCurlView.android.kt` and edge-fold geometry if no caller remains
- keep preference value `canvas`

- [ ] **Step 5: Run focused and broad verification**

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src
git commit -m "fix(reader): harden snapshot slides across lifecycle changes"
```

## Task 11: Readerdev Emulator Acceptance

**Files:**
- Modify: `docs/superpowers/plans/2026-07-13-reader-snapshot-slide.md` only to record evidence

- [ ] **Step 1: Build and install readerdev only after code tasks are green**

Use the repository's existing readerdev installer and the single active emulator. Do not launch a second instance of the same AVD.

```powershell
.\scripts\install-reader-dev.ps1
adb devices -l
```

- [ ] **Step 2: Validate portrait on Alcatraz**

Record forward and backward videos covering:

- slow full-width drag while finger remains down
- short committed fling
- cancel below threshold
- rapid three-page sequence
- Author's Foreword -> Chapter 1
- chapter boundary backward
- cold snapshot path

Required evidence:

- destination visible during drag
- correctly oriented text that deforms only with its paper surface
- visible intermediate wave curvature with flat endpoint geometry
- no midpoint freeze
- no tap fallback
- no post-release blink
- next gesture accepted immediately after visible completion when cached

- [ ] **Step 3: Validate tablet-like landscape resolution**

Set the emulator to the tablet landscape dimensions used by readerdev and repeat forward, backward, rapid, boundary, and rotation tests.

Verify one gesture settles one complete spread while only the active physical leaf deforms. The gutter must stay fixed, the inactive leaf must not translate with the active leaf, and page decoration must deform with its text.

- [ ] **Step 4: Measure diagnostics**

Extract timestamps for:

```text
release -> visual completion
visual completion -> next gesture accepted
visual completion -> live settlement
```

The ready-cache next gesture must be accepted within one rendered frame. A slow live settlement is acceptable only while the final shield and subsequent cached gestures remain correct.

- [ ] **Step 5: Record artifacts and commit evidence**

Add exact APK commit, emulator serial, viewport, video/screenshot paths, and measured timings to this plan.

```powershell
git add docs/superpowers/plans/2026-07-13-reader-snapshot-slide.md
git commit -m "docs(reader): record snapshot slide emulator evidence"
```

## Task 12: Physical Device Acceptance And Release Decision

**Files:**
- Modify: `docs/superpowers/plans/2026-07-13-reader-snapshot-slide.md` only to record evidence

- [ ] **Step 1: Install debug build on Fold and Tab S9 Ultra**

Do this only when the devices are available. Do not block earlier code work on device availability.

- [ ] **Step 2: Validate Fold portrait**

Check touch latency, directional layering, consecutive turns, app task switching, and orientation changes.

- [ ] **Step 3: Validate Tab S9 Ultra landscape**

Check gutter-bounded leaf deformation, high-resolution rendering, memory, repeated turns, rotation, and Whispersync playback/highlight coexistence.

- [ ] **Step 4: Run final repository verification**

Run Gradle once after all implementation work is finished, not repeatedly during visual observation:

```powershell
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest :composeApp:assembleRelease
git diff --check
git status --short --branch
```

- [ ] **Step 5: Apply the release gate**

Publish only if:

- emulator and device evidence satisfy the specification
- no known page-turn crash or navigation regression remains
- exact target navigation is reliable
- memory stays bounded
- the user considers the implementation visually credible

If any gate fails, keep the work on debug/readerdev and continue the staged implementation. Do not create a final release merely because the code compiles.

- [ ] **Step 6: Commit evidence, sync, and release only after acceptance**

```powershell
git add docs/superpowers/plans/2026-07-13-reader-snapshot-slide.md
git commit -m "docs(reader): record snapshot slide device acceptance"
git push fork master
```

Then follow the repository's standard version/tag/release workflow. The exact theta number is chosen from current release state at that time, not hardcoded in this plan.

## Task 13: Replace The Rigid Renderer Contract With Leaf Geometry

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Modify: `tools/reader-harness/src/page-turn-model.test.mjs`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`

- [ ] **Step 1: Write failing geometry-contract tests**

Assert that portrait transitions expose one full-page leaf and landscape transitions expose `leftLeafRect`, `rightLeafRect`, and `gutterRect`. Assert that the gutter comes from resolved page geometry, remains within the snapshot surface, and is not inferred from the device screen.

- [ ] **Step 2: Verify RED**

Run only the page-turn model and destination/native source tests. The tests must fail because the current transition carries only complete-spread snapshots.

- [ ] **Step 3: Add immutable leaf geometry**

Add normalized or bitmap-relative leaf rectangles to the transition. Preserve complete-spread snapshot keys and ownership. Terminal one-page spreads expose one leaf and an absent opposite leaf; they never invent a second page.

- [ ] **Step 4: Verify GREEN and commit**

Run the same focused tests, `node --check`, and `git diff --check`, then commit the geometry contract separately.

## Task 14: Add A Reusable Front-Leaf Wave Mesh

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnWaveGeometry.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnWaveGeometryTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideViewSourceTest.kt`

- [ ] **Step 1: Write failing pure geometry tests**

Lock these invariants:

- binding vertices never move
- outer-edge displacement follows normalized progress
- intermediate progress has non-zero curvature
- progress `0` and `1` have no residual wave curvature
- mirrored direction uses the same geometry function
- vertices stay inside the active leaf plus the bounded shadow allowance
- stable interior vertices remain at their source x-coordinate
- compression is confined to the moving-edge band and retired texture collapses at the boundary
- no vertex or index arrays are allocated from `onDraw`

- [ ] **Step 2: Verify RED**

Run the new geometry test and renderer source test. They must fail because the current renderer calls `canvas.translate()` on the complete bitmap.

- [ ] **Step 3: Implement the minimal mesh**

Use one reusable strip/grid mesh over the front bitmap only. Start with a bounded 16-column mesh and increase only if emulator evidence shows visible faceting. Preserve source coordinates before the rigid limit, compress only the bounded source intake beside the moving edge, and collapse retired source columns at the edge. Use `Canvas.drawBitmapMesh` or the equivalent reusable vertex path. Do not add a reverse surface, third snapshot, WebView call, or draw-time allocation.

- [ ] **Step 4: Remove rigid production translation**

The production landscape path must not translate the complete spread. Retain a debug-only flat fallback only until wave acceptance; it cannot satisfy the release gate.

- [ ] **Step 5: Verify GREEN and commit**

Run the geometry and renderer tests, then commit the reusable mesh independently.

## Task 15: Integrate Portrait Snapshot Waves

**Files:**
- Modify: `ReaderPageTurnSlideView.android.kt`
- Modify: `ReaderPageTurnController.android.kt`
- Modify: `ReaderPageTurnStateMachine.kt`
- Modify corresponding focused tests

- [ ] **Step 1: Write failing portrait draw-order tests**

Forward retracts the source over a stationary destination. Backward expands the destination over a stationary source. Both use the same mirrored wave field and one normalized `0..1` progress.

- [ ] **Step 2: Implement and verify**

Progress follows the active page width, reaches both endpoints while the finger is down, and cannot fall through to tap after claim. Keep current cache and settlement behavior unchanged.

- [ ] **Step 3: ReaderDev portrait gate**

Capture start, quarter, midpoint, three-quarter, and end frames in both directions. Reject straight rigid translation, uniform whole-page text compression, mirrored text, transparent paper, endpoint residue, tap fallback, a white/dark double edge stroke, and post-settlement blink.

- [ ] **Step 4: Commit accepted portrait behavior**

## Task 16: Integrate Gutter-Bounded Landscape Waves

**Files:**
- Modify: `ReaderPageTurnSlideView.android.kt`
- Modify: `ReaderPageTurnBundleSource.android.kt`
- Modify: `ReaderPageTurnController.android.kt`
- Modify corresponding focused tests

- [ ] **Step 1: Write failing landscape composition tests**

Forward retracts only the source right leaf toward the gutter. Backward expands only the destination left leaf from the gutter. The inactive leaf is separately composed and never translated. The gutter x-coordinate is invariant for every progress sample.

- [ ] **Step 2: Add the late inactive-leaf handoff**

Blend only the inactive source leaf to the inactive destination leaf during the late portion of the gesture. At `progress = 1`, the composed frame must be pixel-equivalent to the destination spread so the final shield cannot blink.

- [ ] **Step 3: Preserve spread identity and exact settlement**

Visual and settled indices still move by two in normal landscape spreads. Snapshot cache keys and final shields remain complete-spread based. No reverse-face capture is introduced.

- [ ] **Step 4: ReaderDev landscape gate**

At tablet resolution capture both directions and verify:

- complete spread never moves as one card
- active deformation stops at the gutter
- inactive leaf remains stationary until its explicit handoff
- text and paper share the same deformation
- visible text outside the moving-edge band remains at 1:1 scale
- no transparent gap, stale shield, or destination blink
- rapid consecutive gestures remain accepted

- [ ] **Step 5: Commit accepted landscape behavior**

## Task 17: Corrective Performance And Release Gate

- [ ] **Step 1: Measure wave draw cost**

Record frame skips and draw timing with diagnostics enabled. The mesh must not trigger new WebView capture, bitmap allocation, or passive-renderer rebuilds.

- [ ] **Step 2: Re-run cache and settlement acceptance**

Repeat the full prewarm-window pixel sampling after forward/backward/rapid turns. Confirm the live spread remains stable while distant snapshots are prepared.

- [ ] **Step 3: Run focused and broad verification once**

Run the page-turn model, geometry, state-machine, coordinator, native source, destination source, renderer, and runtime asset tests. Run the full Gradle verification only after visual behavior converges.

- [ ] **Step 4: Physical-device decision**

Use the Tab S9 Ultra if emulator GPU composition, touch latency, or mesh filtering differs materially. Do not block code-only stages on device availability.

- [ ] **Step 5: Release decision**

Do not publish while any rigid spread movement, missing wave deformation, gesture dead zone, or destination blink remains. ReaderDev/debug artifacts are allowed.

---

## Continuous Validation Checklist

Run after every task that changes the corresponding layer:

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
npm --prefix tools/reader-harness run test:page-turn-model
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnStateMachineTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageSlideCoordinatorTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnSlidePerformanceSourceTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageTurnSlideViewSourceTest"
git diff --check
```

Do not run the full Gradle suite after every visual tweak. Run focused tests per task and the full suite once after implementation converges, before device acceptance and release.
