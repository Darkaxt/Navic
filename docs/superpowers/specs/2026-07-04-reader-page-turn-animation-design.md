# Reader Page-Turn Animation Design

**Status:** Revision 4 implemented and released in `v1.0.11-theta92`; superseded for destination-aware work by `2026-07-12-reader-destination-aware-page-turn-design.md`

**Original date:** 2026-07-04

**Revision date:** 2026-07-11

**Baseline:** `v1.0.11-theta89` (`6f8d6c80`)

## 1. Revision 4 Decision

The revision-3 architecture remains directionally useful, but its implementation branch is not mergeable into the current reader.

Revision 4 preserves:

- native animation above the WebView
- Foliate as the only pagination and navigation authority
- commit decision only on release
- a pure gesture state machine
- snapshotting the already-composited reader surface so paper, edge, stain, font, image, and highlight rendering remain identical

Revision 4 rejects:

- rebasing or cherry-picking the stale native WIP wholesale
- restoring simulated book-shell geometry
- capturing the entire reader host without an exact source rectangle
- attaching the animation overlay before the source snapshot is complete
- calling an incomplete Canvas renderer "WebGL"
- changing the public page-turn setting before the capture pipeline is proven

## 2. Current Reader Contract

Theta89 is the layout baseline.

- Foliate owns columns, margins, pagination, spread geometry, and current position.
- Navic's paper, edge, stain, gutter, cover-reveal, and Whispersync layers decorate Foliate's resolved page surface.
- Normal text pages must not be moved into a synthetic shell or resized to accommodate animation.
- Cover-page backdrop behavior remains independent from text-page animation.
- Existing native gesture suppression and exactly-once page-turn handoff remain authoritative.

The page-turn renderer may cover the reader temporarily, but it must never become a second pagination engine.

## 3. Gesture State Machine

States:

- `Idle`
- `Capturing`
- `Deforming`
- `Committing`
- `Relaxing`

Rules:

1. Gesture start records direction, pointer origin, resolved page/spread profile, and target page action.
2. Capture completes before the visible deformation overlay is attached.
3. Drag updates only the native overlay. Foliate does not scroll during the gesture.
4. Commit is decided only on release using distance or velocity.
5. Cancel always relaxes to the original page and performs no navigation.
6. A committed release issues one existing Foliate page-turn action as the sheet begins animating to the center binding, then holds the captured sheet at the binding if Foliate's page-turn promise has not settled yet.
7. After settlement, the captured sheet completes its sweep across the opposite page and detaches.
8. Every terminal path clears snapshots, overlay views, gesture state, and suppression flags.

No timeout cancels capture or animation. Completion is driven by capture callbacks and frame/animation completion events.

## 4. Stage A: Capture Feasibility Gate

No curl mathematics or public setting migration begins until this stage passes.

### Exact source ownership

The capture source is the current visible reader WebView/page surface, not the Compose/native host containing controls.

Before capture:

1. Read the WebView location in the Android window.
2. Read the current visible page or spread rectangle from the resolved reader layout profile.
3. Intersect that rectangle with the WebView bounds.
4. Convert it to window coordinates once.
5. Allocate a bitmap matching the resulting physical-pixel rectangle.
6. Request `PixelCopy` from the window with that exact rectangle.

The animation overlay must not exist in the captured region until the copy callback reports success.

Navic's exact `PixelCopy(Window, Rect, Bitmap, ...)` path is enabled on Android API 26+. API 24-25 retain the existing immediate page navigation without the Canvas overlay.

### Stage A proof

The spike must demonstrate on readerdev emulator and physical tablet when available:

- only the page/spread is captured
- no top controls, Whispersync icon, page history, navigation bar, or outer unused area is captured
- paper, edges, stains, EPUB images, and highlights appear exactly as on the live reader
- a static native overlay can display the bitmap without shifting Foliate
- removing the overlay returns to the unchanged reader position
- capture latency and allocation size are logged for diagnostics
- rotation invalidates old geometry and snapshots before another capture

The capture gate passed on the tablet-sized readerdev emulator before the public `canvas` mode was enabled.

## 5. Destination and Reverse-Face Contract

This remains the main unresolved design problem. PixelCopy directly provides only the currently visible composited pixels.

Revision 4 does not pretend that reusing the front image is a destination page.

The first accepted Canvas implementation uses:

- captured current page as the deforming front face
- a paper-tinted neutral reverse face derived from the active reader theme
- the existing live reader held static underneath during relax
- exactly one Foliate navigation action issued only after release commits the gesture
- a commit sweep that remains above the WebView until both the native animation and Foliate's page-turn promise settle

The destination page may render under the captured sheet during the commit sweep. Cancel and relax never navigate. Teardown is condition-driven by animation completion plus Foliate settlement; it does not use a delay or cancellation timeout.

A future destination-preview stage may be added only if Navic can obtain it without visibly moving Foliate or mutating the live EPUB DOM. Revision 4 does not create or maintain a second reader.

The next revision must prove destination-aware turning in landscape first:

- the untouched page in the current spread remains stable
- the deforming page uses the captured current-page pixels
- the folded reverse face shows the correct content for the physical back of that sheet
- the uncovered area shows the real incoming page before the native overlay detaches
- obtaining those pixels must not scroll, flash, resize, or otherwise move the live Foliate renderer

The implementation may evaluate an isolated offscreen render surface if Foliate cannot expose adjacent-page pixels directly, but it must first prove bounded lifetime, deterministic teardown, EPUB style parity, and no duplicate progress or media-overlay side effects. Portrait follows only after this destination-source contract is proven; it replaces a full page and must not inherit landscape spread assumptions unchanged.

## 6. Canvas Renderer

The first renderer is native Android Canvas. The same edge-origin geometry supports portrait pages and one selected page of a landscape spread.

- Use the exact captured page rectangle.
- Begin the fold at the exact vertical point where the gesture first crosses the outer page edge. Do not snap edge gestures to a corner.
- Use an edge-origin mesh deformation that leaves the binding edge fixed and keeps the unaffected page region substantially rigid.
- Derive a rigid fold plane from the exact grabbed edge point and live pointer. The folded side reflects across that plane while the unaffected side remains rigid; no cylindrical row-by-row roll is permitted.
- Let vertical pointer movement bias the fold upward or downward. Near the top or bottom, the same geometry naturally approaches a corner peel without changing renderer modes.
- Render the paper-tinted reverse face where the sheet turns over.
- Apply crease shading and edge highlight along the resulting fold boundary. A purely horizontal middle-edge drag may produce a vertical crease, but the page on either side remains planar rather than rolling around a cylinder.
- Keep the renderer outside the WebView and drive it from native frame callbacks.
- Relax and commit sweeps animate to completion; immediate teardown is not acceptable.

Acceptance requires visually continuous behavior on the tablet-sized readerdev emulator and usable behavior on the target tablet. Mesh density is selected from measured performance rather than hard-coded as a universal quality level.

## 7. Landscape and Spread Stage

Landscape does not inherit portrait assumptions unchanged.

- Use the current public reader layout profile to identify single-page versus spread.
- Curl one actual page, never the full spread as one sheet.
- Keep the center binding edge fixed.
- Preserve the accepted gutter, page bounds, paper layers, and back-cover reveal.
- Previous and next turns use mirrored geometry but the same signed-direction contract.
- Rotation or layout-profile changes cancel the overlay without navigation.

No four-bitmap requirement exists in revision 4. That revision-3 requirement assumed destination images that Navic cannot currently acquire safely.

## 8. OpenGL Stage

OpenGL is deferred.

It may be considered only after Canvas has proven:

- capture ownership
- gesture semantics
- page-turn handoff
- orientation behavior
- visual acceptance

The eventual renderer must have an independent implementation and capability probe. A code path that delegates to Canvas is not presented as OpenGL. Context loss downgrades for the session through lifecycle callbacks; it does not use cancellation timeouts or auto-promote mid-session.

## 9. Settings and Migration

The accepted Canvas implementation atomically migrates the existing `dragAnimationMode` value across Kotlin, bridge JSON, JavaScript, resources, settings search, per-book overrides, tests, and the reader harness.

Definitive mapping:

- `standard` -> `none`
- `curl` -> `canvas`

`none` is the default. `canvas` is the only public animated mode. The legacy values remain accepted only as migration inputs and are rewritten when preferences are read.

## 10. Failure and Fallback Behavior

- Capture failure: remove any pending overlay and leave the current page unchanged.
- Allocation failure: log diagnostics and leave the current page unchanged.
- Gesture cancellation during capture: discard the callback result and do not attach an overlay.
- Rotation/layout change: invalidate capture generation and page rectangle.
- Canvas renderer failure: return to `none` for the session and preserve navigation.
- Any failure before commit: no Foliate page-turn command is sent.

## 11. Testing

### Pure state tests

- release below threshold -> relax -> idle, no navigation
- release beyond distance threshold -> commit exactly once
- release beyond velocity threshold -> commit exactly once
- cancellation from capture/deform/relax -> idle, no navigation
- stale capture callback after cancellation or rotation is ignored

### Source and integration guards

- source rectangle is derived from WebView/window coordinates
- overlay attaches only after successful capture
- capture target excludes native reader controls
- no live Foliate scrolling during deformation
- commit invokes the existing page-turn action exactly once
- the native settle poll reads the JavaScript token directly; it must not double-encode the string with `JSON.stringify`
- text-page shell geometry remains absent
- no production OpenGL label delegates to Canvas

### Visual validation

1. Readerdev emulator, portrait Alcatraz chapter text.
2. Readerdev emulator, true wide landscape spread.
3. Physical tablet when emulator and tablet composition differ or a device-specific issue is suspected.
4. Forward, previous, cancel, slow drag, fast flick, rotation during capture, and boundary page behavior.

Screenshots prove geometry and capture ownership. A screen recording proves animation continuity and exactly-once navigation.

## 12. Stale Branch Disposition

The old `feat/page-turn-animation` branch is retained only as research evidence.

Reusable concepts may be reimplemented on a fresh branch from current master. Its native WIP, setting rename, renderer classes, and host changes are not merged wholesale because they predate the theta89 reader surface contract and leave capture, reverse-face, animation-completion, and landscape semantics unresolved.

## 13. Implementation Order

1. Stage A exact-rectangle capture spike.
2. Capture diagnostics and stale-callback guards.
3. Static overlay proof with no reader movement.
4. Pure gesture state machine on current master.
5. Edge-origin Canvas deform/relax/commit.
6. Portrait emulator acceptance.
7. Landscape/spread extension and mirrored-turn acceptance.
8. Atomic setting migration.
9. Full host tests, harness verification, and release build.
10. OpenGL investigation, if Canvas quality or performance justifies it.

Only completed and visually accepted stages are released. The capture spike and incomplete renderers remain debug-only.
