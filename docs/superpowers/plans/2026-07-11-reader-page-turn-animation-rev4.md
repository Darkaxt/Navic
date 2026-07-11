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
- A release above distance or velocity threshold commits only after the commit animation finishes.
- Every navigation effect is emitted exactly once.

## Stage 3 - Portrait Canvas Renderer

- Reuse the bounded mesh concept, not the old controller.
- Draw the captured page as the front face and an active-theme paper color as the reverse face.
- Keep the binding edge fixed.
- Animate relax and commit to completion before teardown.
- Keep the live Foliate page static throughout the drag.

## Stage 4 - Landscape Spread

- Select the physical left or right page from the resolved spread rectangles based on drag direction.
- Curl one page only; never treat the spread as one sheet.
- Keep the center binding fixed and preserve the accepted gutter and back-cover decoration.

## Stage 5 - Settings Migration

- After renderer acceptance, atomically migrate `standard/curl` to `none/canvas` across preferences, per-book overrides, bridge JSON, resources, settings search, and tests.
- Preserve the legacy live-drag path only as migration input, not as a user-facing mode.

## Stage 6 - Verification And Release

- Run focused common and Android host tests plus JS syntax checks.
- Install `readerdev` on the emulator and validate Alcatraz in portrait and tablet-like landscape.
- Record forward, previous, cancel, slow drag, fast flick, rotation, and boundary behavior.
- Use the physical tablet only if emulator capture/composition differs or before the public release gate.
- Commit each accepted stage, merge current `master`, push, and publish the next theta release only after visual acceptance.

