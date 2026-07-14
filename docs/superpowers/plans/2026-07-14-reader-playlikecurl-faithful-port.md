# Reader PlayLikeCurl Faithful Port Plan

**Status:** Active execution plan

**Goal:** Replace the failed custom page-turn prototype with a behaviorally faithful GLES2 port of PlayLikeCurl, prove the original demo in ReaderDev first, then connect Navic page rasters through a narrow adapter without changing the renderer.

**Reference:** `C:\Users\darka\Documents\Projects\Android\.codex-temp\reference-playlikecurl` at `915a5a33773b1b2534134a56cdab00303b29a442`.

**Release rule:** ReaderDev/debug builds are allowed. No public release is allowed until every tranche parity gate, Foliate integration gate, emulator matrix, and physical-device acceptance gate passes.

## Mandatory Parity Protocol

Every tranche ends with all of the following:

1. record the exact Navic commit, ReaderDev APK SHA256, emulator profile, and reference commit
2. capture the same transition directions and progress checkpoints in ReaderDev and the PlayLikeCurl reference
3. compare page geometry, texture orientation, draw order, visible page identities, gesture response, settlement timing, and post-settlement state
4. document every difference, including expected GLES2 rasterization differences
5. block the next tranche for any unexplained difference

Tests that compare only equations or source strings cannot satisfy this protocol.

## Tranche 0: Quarantine The Failed Prototype

- [x] Inventory the current custom renderer, projection, diagnostics, and tests.
- [x] Mark custom two-texture rendering, z perspective, and leaf-coordinate rewriting as failed-prototype code.
- [x] Preserve asynchronous raster cache, bitmap quality, preparation UI, Foliate relocation, and settlement shielding as orthogonal infrastructure.
- [x] Validate and commit any already-dirty orthogonal preparation work separately.
- [x] Add source guards preventing failed-prototype classes from being accepted by the reference-demo path.

**Gate:** code audit and tests prove that the upcoming ReaderDev reference path does not instantiate the custom renderer or `ReaderPageCurlLeafProjection`.

## Tranche 1: Lock The Complete Reference Model

- [x] Add failing tests for three persistent page roles, exact stationary depths, draw order, aspect correction, endpoint values, active-role transitions, and texture-slot rotation.
- [x] Add failing tests for down/move/release/fling behavior, 300 ms settlement, interpolator selection, cancellation, and boundary duplication.
- [x] Implement a pure `ReaderPlayLikeCurlReferenceModel` directly from the audited Java source.
- [x] Keep names and formulas traceable to `Page`, `PageFront`, `PageLeft`, `PageRight`, `PageRenderer`, `PageSurfaceView`, and `AnimateCounter`.
- [x] Remove or isolate tests that mistake custom leaf projection for reference fidelity.

**Gate:** fresh red-green test evidence plus a line-by-line model mapping to the reference source. No visual claim yet.

## Tranche 2: Faithful GLES2 Demo In ReaderDev

- [x] Add the original PlayLikeCurl portrait and landscape sample textures to a ReaderDev-only asset path with MIT attribution.
- [x] Implement three persistent GLES2 page objects matching the reference mesh, UVs, depths, projection, and draw order.
- [x] Translate fixed-function matrices and client arrays to GLES2 uniforms, attributes, VBOs, and shaders without changing resulting coordinates.
- [x] Reproduce previous/current/next texture assignment and post-animation texture rotation.
- [x] Reproduce the original gesture mapping, release/fling decision, settlement duration, and interpolators.
- [x] Do not connect Foliate, Navic bitmaps, leaf bounds, or settlement shielding in this tranche.

**Gate:** record the GitHub demo and ReaderDev side by side at start, 25%, 50%, 75%, and completion for forward, backward, commit, and cancel. Any unexplained difference blocks Tranche 3.

## Tranche 3: Narrow Raster Adapter

- [ ] Define an adapter from Navic's cached `previous/current/next` page images to the proven renderer's three texture slots.
- [ ] Keep renderer geometry, gesture mapping, timing, and page lifecycle unchanged.
- [ ] Upload half-resolution cached rasters by default, using the existing configurable quality setting.
- [ ] Keep raster capture, decode, and upload asynchronous and single-flight.
- [ ] Show cover-backed determinate preparation instead of capturing in the foreground.
- [ ] Preserve the proven reference-texture harness beside the adapter for regression comparison.

**Gate:** run the same motion twice, once with reference textures and once with Navic diagnostic page images. Geometry and timing must be identical; only texture pixels may differ.

## Tranche 4: Portrait Foliate Integration

- [ ] Map Foliate's current, previous, and next visual-page identities into the adapter.
- [ ] Keep Foliate stationary during drag and authoritative for final relocation.
- [ ] Dispatch exactly one target relocation after a committed animation.
- [ ] Keep the final raster shield until target-token and composited-frame settlement.
- [ ] Prevent tap fallback while a turn gesture, animation, preparation, or settlement owns input.
- [ ] Verify chapter and spine boundaries, including Author's Foreword to Chapter 1.

**Gate:** ReaderDev portrait video proves forward, backward, cancel, rapid repeated turns, and chapter boundaries with no visible capture, blink, mirrored text, or dead tap interval. Compare the deformation and timing again against the reference demo.

## Tranche 5: Landscape Leaf Boundaries

- [ ] Resolve the active Foliate leaf and stationary companion leaf.
- [ ] Place/scissor the unchanged reference renderer within the active leaf.
- [ ] Never allow the deforming page to cross the gutter or move both leaves as one page.
- [ ] Do not alter the mesh formulas, progress mapping, projection, or gesture semantics.
- [ ] Keep the companion leaf static and use the correct underneath page for the active leaf.

**Gate:** landscape forward and backward recordings prove one-leaf deformation, gutter containment, static companion content, and unchanged reference deformation. Any mesh change requires returning to Tranche 2 parity.

## Tranche 6: Cache, Recovery, And Performance

- [ ] Complete bounded memory/disk/GPU cache integration and stale-profile invalidation.
- [ ] Restore raster state after GLES context recreation using lifecycle events, never cancellation timeouts.
- [ ] Ensure no per-frame bitmap, vertex-array, index-array, or direct-buffer allocation.
- [ ] Validate all bitmap quality settings and cache clearing.
- [ ] Measure preparation, cache-hit reopen, upload, frame cadence, and memory on emulator and tablet.

**Gate:** no visible capture or post-turn blink, repeat turns remain available immediately after settlement, and measured frame/memory behavior is recorded. Repeat reference motion comparison after performance changes.

## Tranche 7: Final Acceptance And Release

- [ ] Run focused model, renderer, cache, state-machine, settlement, and source-guard tests.
- [ ] Run the full Android host suite once after all feature work is complete.
- [ ] Build and hash one ReaderDev APK.
- [ ] Validate portrait and landscape on the ReaderDev emulator.
- [ ] Validate the exact same APK on the physical tablet with Alcatraz.
- [ ] Review every parity record and unresolved-difference list.
- [ ] Sync `master` with `fork/master` only after all gates pass.
- [ ] Publish the next public release only after device acceptance.

**Gate:** all automated tests pass, every parity gate is documented, emulator and tablet recordings satisfy the specification, the worktree is clean, and the published APK is traced to the accepted commit and hash.

## Current Implementation Classification

- `ReaderPageCurlGeometry.android.kt`: equation extraction only; reusable as audited fixtures, not sufficient as a renderer model.
- `ReaderPageCurlGlRenderer.android.kt`: failed custom two-texture renderer; not the faithful port.
- `ReaderPageCurlLeafProjection`: post-parity integration experiment; forbidden in the standalone reference path.
- existing raster cache and preparation work: potentially reusable infrastructure after independent validation.
- previous `playlikecurl-gate-a` evidence: invalidated because it compared Navic diagnostic output against selected formulas, not the complete reference animation and lifecycle.
