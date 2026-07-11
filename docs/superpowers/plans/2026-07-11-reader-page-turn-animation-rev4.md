# Reader Page-Turn Animation Rev 4 Implementation Plan

**Baseline:** `v1.0.11-theta91` (`aa507181`)

**Research source:** `feat/page-turn-animation` at `8340a4b8` (read-only)

## Stage 1 - Capture Contract

- Expose the resolved page rectangles from the live reader runtime without adding a second pagination model.
- Query the geometry at gesture start and convert CSS pixels to physical window pixels once.
- Capture only the selected physical page using `PixelCopy(Window, Rect, ...)` on API 26+.
- Keep API 24-25 on the existing navigation path.
- Reject stale capture callbacks after cancel, rotation, or a newer gesture.
- Attach no native animation view until capture succeeds.

## Stage 2 - Release-Only State Machine

- Reimplement the useful release-only idea from the research branch as a pure common state machine.
- Track `Idle`, `Capturing`, `Deforming`, `Committing`, and `Relaxing`.
- A release below both thresholds relaxes without navigation.
- A release above distance or velocity threshold commits exactly once as the commit animation starts; the overlay remains above the live reader until the fold reaches the binding and Foliate reports settlement.
- Every navigation effect is emitted exactly once.

## Stage 3 - Portrait Canvas Renderer

- Replace the rejected X-only cylindrical mesh with a pure, testable edge-origin fold geometry.
- Preserve the exact outer-edge touch Y and the live pointer Y through capture, drag, relax, and commit.
- Derive the fold plane from the exact outer-edge origin and live pointer, without snapping to a corner.
- Reflect the folded sheet across that plane so the page remains planar on either side of the crease instead of forming a cylindrical roll or crescent notch.
- Normalize drag progress against the selected captured page width, not the full host/spread width.
- Keep the unaffected page region substantially rigid and deform both mesh axes around the fold boundary.
- Draw the captured page as the front face and an active-theme paper color as the reverse face.
- Keep the binding edge fixed.
- Draw a tapered reverse face, fold-following shadow, and edge highlight rather than full-height strips.
- Animate relax and commit to completion before teardown.
- On a committed release, dispatch Foliate navigation once at commit-animation start and keep the overlay until the existing page-turn promise reports settlement.
- Remove the overlay only after both Foliate settlement and native animation completion, in either arrival order; use no timing delay.
- Read the settle token directly from `evaluateJavascript`; WebView owns the single JSON encoding of that result.
- Keep the live Foliate page static throughout the drag.

## Stage 4 - Landscape Spread

- Select the physical left or right page from the resolved spread rectangles based on drag direction.
- Curl one page only; never treat the spread as one sheet.
- Keep the center binding fixed and preserve the accepted gutter and back-cover decoration.

## Stage 5 - Settings Migration

- After renderer acceptance, atomically migrate `standard/curl` to `none/canvas` across preferences, per-book overrides, bridge JSON, resources, settings search, and tests.
- Preserve the legacy live-drag path only as migration input, not as a user-facing mode.
- Rewrite persisted global and per-book legacy values when they are read.

## Stage 6 - Verification And Release

- Run focused common and Android host tests plus JS syntax checks.
- Install `readerdev` on the emulator and validate Alcatraz in portrait and tablet-like landscape.
- Record forward, previous, cancel, slow drag, fast flick, rotation, and boundary behavior.
- Use the physical tablet only if emulator capture/composition differs or before the public release gate.
- Commit each accepted stage, merge current `master`, push, and publish the next theta release only after visual acceptance.

### Completed validation

- Focused state-machine, capture, renderer, migration, settings, bridge, and reader-surface tests pass.
- JavaScript syntax checks pass for the four edited reader runtime modules.
- The reader harness `drag-animation-mode-migration` scenario passes.
- The full Android host suite runs 2,147 tests with 30 failures, all matching the theta91 baseline environmental/reference or Android Bitmap-stub groups; no new failure remains.
- Fresh readerdev evidence on `emulator-5554` uses the production Alcatraz resource in tablet-like landscape. Forward and reverse commits settle and detach, a below-threshold drag returns to the byte-identical page, and a fast flick commits.
- Final forward evidence is stored in `captures/page-turn-rigid-fold/final-page-turn.mp4` and `final-page-turn-contact.png`; the bridge emits one `page-turn:start next` and settles on the next page state.
- Host resize now cancels an active overlay. PixelCopy callbacks from obsolete capture generations recycle successful bitmaps and cannot disable Canvas mode after a stale failure.
