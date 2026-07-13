# Reader Snapshot-Wave Page-Turn Design

**Status:** Rev 2 approved for staged correction after emulator rejection of the rigid spread slide

**Date:** 2026-07-13

**Baseline:** `master` at `e0306bc7`

**Rev 2 correction:** The snapshot cache, passive renderer, visual/settled position split, and serialized Foliate settlement remain accepted. The flat full-spread translation is rejected. Rendering now uses a lightweight page-aware wave over the active physical leaf.

**Supersedes:** The curl, folded reverse-face, and alternating portrait-leaf portions of `2026-07-12-reader-destination-aware-page-turn-design.md`. The exact visual-page locator, passive-renderer isolation, half-resolution capture, generation invalidation, and exact Foliate settlement remain valid and are reused.

## 1. Objective

Replace Navic's curl-based Canvas page turn with the responsive snapshot-wave interaction observed in Google Play Books:

- portrait moves one visual page at a time
- landscape settles one complete visible spread at a time but animates relative to the physical page split and center binding
- the destination page or spread is visible during the drag
- paper, text, texture, stains, and edge decoration deform together as one bitmap surface
- no reverse face, mirrored text, or simulated page-back content is rendered
- the visible animation follows the finger continuously and may reach either endpoint before release
- the next gesture becomes available when the visible animation completes, not when Foliate finishes background relocation

The feature must preserve Navic's existing EPUB behavior. Foliate remains the sole authority for pagination, navigation, history, progress, selection, annotations, media overlays, and Whispersync. Native snapshots are temporary visual replicas only.

No public release is permitted until the debug/readerdev implementation passes the code, emulator, and physical-device gates in this specification.

## 2. Why The Previous Design Is Replaced

The destination-aware curl implementation proved several useful primitives, but the folded-page model itself created unacceptable complexity and failure modes:

1. The moving mesh required separate front, reverse, underneath, and final surfaces.
2. Mirrored or incorrectly transformed text appeared on the reverse face.
3. Some drag directions exposed transparent or visually corrupt paper.
4. Progress was temporarily split between a `0..1` state-machine contract and a `0..2` renderer contract, causing the fold to stop halfway.
5. Half-resolution bitmaps were initially interpreted using physical-resolution geometry, distorting the fold.
6. Visible completion remained coupled to Foliate settlement, producing a measured 1.47-2.44 second interaction dead zone.
7. Rebuilding the passive renderer and both adjacent transition bundles took approximately 13-15 seconds.
8. Android's delayed gesture detector could reinterpret an intended drag as a tap when preparation was slow.

The replacement keeps the successful capture and exact-navigation work while removing the reverse face and multi-surface curl simulation. Rev 2 reintroduces only a bounded front-surface mesh: a cheap strip/mesh warp of one already-captured page leaf, with no extra WebView capture and no page-back rendering.

## 3. Reference Behavior

The accepted visual reference is Google Play Books on Android.

### 3.1 Portrait forward

- The destination page is stationary underneath.
- The current page retracts toward the fixed inner edge while following the finger.
- Deformation is zero at the fixed edge and grows smoothly toward the dragged outer edge.
- A narrow curved shadow and edge highlight follow the deformed boundary.
- Releasing beyond the commit threshold completes the translation.
- Releasing before the threshold returns the current page to its origin.

### 3.2 Portrait backward

- The current page remains stationary underneath.
- The previous page expands from its fixed inner edge toward the outer edge with the finger.
- The same wave field is mirrored; it is not a separate animation implementation.
- A narrow curved shadow and edge highlight remain attached to the expanding boundary.
- Commit and cancel use the same release rules as forward.

### 3.3 Landscape

Landscape uses a complete spread for identity, caching, and exact settlement, but not as one rigid moving card.

- The center gutter is the fixed binding axis and is read from resolved reader geometry.
- Forward: only the source right leaf retracts from the outer-right edge toward the gutter. The destination spread is already underneath. The source left leaf hands off to the destination left leaf late in the gesture so final completion is continuous.
- Backward: the destination left leaf expands from the gutter toward the outer-left edge over the source spread. The source right leaf hands off to the destination right leaf late in the gesture.
- Wave displacement is zero at the gutter and increases toward the active outer edge.
- The non-active leaf never translates as part of the active mesh.
- The gutter itself remains stationary and is not duplicated inside the moving leaf.
- One committed gesture still advances or reverses by two visual page ordinals, clamped at publication boundaries.

RTL mirrors physical direction while preserving logical next/previous behavior.

## 4. Non-Negotiable Invariants

1. Foliate owns live reader state and final navigation.
2. The live Foliate renderer does not scroll or relocate during an active drag.
3. Commit is decided only on release.
4. Cancel never navigates.
5. Every committed visual target is eventually settled with exact visual-page relocation.
6. Portrait targets one adjacent visual page; landscape targets one adjacent complete spread.
7. Text may be geometrically warped only as part of the same front-surface bitmap as its paper. It is never mirrored, reversed, redrawn independently, or used as a simulated back face.
8. Visible progress uses one normalized `0..1` contract in JavaScript, Kotlin, tests, and rendering.
9. A claimed drag can never fall through to the tap page-turn path.
10. The destination snapshot is visible before or during motion; there is no post-animation destination blink.
11. The next gesture is governed by visual readiness, not live Foliate settlement.
12. Passive rendering never emits history, progress, selection, annotation, Whispersync, or media-overlay events.
13. No timeout cancels capture, rendering, navigation, or settlement. Obsolete work is invalidated with generation tokens and lifecycle events.
14. No page-turn path changes Foliate's text geometry or recreates a synthetic reader shell.
15. Snapshot memory is explicitly bounded and recycled.
16. Landscape deformation is bounded to one resolved leaf rectangle; the complete spread is never translated as one surface.
17. The binding coordinate remains stationary throughout a landscape gesture.

## 5. Public Preference Contract

The persisted and JavaScript bridge value remains `canvas` for backward compatibility. Existing users do not need a preference migration.

The user-facing label may change from `Canvas` to `Page slide` after implementation acceptance. Internally, new Kotlin types use `Slide` terminology; compatibility parsing continues to map the persisted `canvas` value to the slide renderer.

The old curl renderer is removed from the active `canvas` path. It may remain temporarily in source behind a debug-only comparison flag during early stages, but it must not remain as a production fallback after the slide path passes the release gate.

## 6. Visual Page And Spread Identity

The existing inverse visual-page locator remains authoritative:

```text
visual page index
  -> pagination profile chapter
  -> spineIndex + href
  -> chapterPageIndex + chapterPageCount
  -> exact renderer anchor
```

The source index is the latest **visual** index, not necessarily the currently settled Foliate index.

Target selection:

```text
portrait next      source + 1
portrait previous  source - 1
landscape next     source + 2
landscape previous source - 2
```

Targets clamp only at publication boundaries. Landscape boundary handling may produce a one-page terminal spread, but it must not substitute an unrelated page.

## 7. Snapshot Contract

### 7.1 Immutable snapshot

Each cached visual state is represented by a `ReaderPageSlideSnapshot`:

```text
key
  publication identity
  pagination fingerprint
  reader settings fingerprint
  viewport size and device scale
  layout mode
  text direction
  visual page index

surface
  bitmap
  exact physical surface rectangle
  opaque background color
  logical page or spread role
```

The bitmap is captured at `0.5` physical resolution for animation. The surface rectangle remains expressed in physical view coordinates, and drawing scales bitmap pixels to that rectangle exactly once. No geometry calculation may mix bitmap dimensions with physical view dimensions.

### 7.2 Transition

A `ReaderPageSlideTransition` references two snapshots and resolved leaf geometry:

```text
sourceSnapshot
destinationSnapshot
logicalDirection
physicalDirection
sourceVisualPageIndex
targetVisualPageIndex
layoutMode
surfaceRect
leftLeafRect
rightLeafRect
gutterRect
```

There is no `turningReverse`, reverse-face color, page-back snapshot, or curl geometry. The active front leaf is a crop of the source or destination spread snapshot and is warped through a reusable bounded mesh.

### 7.3 Snapshot composition

Snapshots capture the complete accepted reader visual surface for the page or spread:

1. page content
2. theme background or sepia treatment
3. paper texture when enabled
4. page-edge decoration when enabled
5. stains when enabled
6. landscape gutter when applicable
7. thin cover-tinted reveal when applicable

Touch controls, selection handles, transient menus, Whispersync controls, and app chrome are excluded.

## 8. Rendering Contract

`ReaderPageTurnSlideView` is a persistent native overlay view. It performs bitmap composition, one bounded front-leaf mesh warp, and edge-shadow drawing. Mesh vertices and draw buffers are allocated when geometry changes, not in `onDraw`.

### 8.1 Forward rendering

Portrait:

```text
draw destinationSnapshot at rest
warp sourceSnapshot toward its fixed inner edge
draw curved boundary highlight and shadow
```

Landscape:

```text
draw destination spread at rest
draw source left leaf at rest with late handoff alpha
warp source right leaf inside rightLeafRect toward gutterRect
draw curved boundary highlight and shadow inside rightLeafRect
```

### 8.2 Backward rendering

Portrait:

```text
draw sourceSnapshot at rest
expand destinationSnapshot from its fixed inner edge
draw curved boundary highlight and shadow
```

Landscape:

```text
draw source spread at rest
draw destination right leaf with late handoff alpha
expand destination left leaf inside leftLeafRect from gutterRect
draw curved boundary highlight and shadow inside leftLeafRect
```

`progress` is always clamped to `0..1`. It is derived from signed drag displacement divided by the active **leaf** width, never the complete spread width in landscape. The dragged outer boundary follows the finger after touch slop and may reach the binding before release.

The wave field is monotonic and cheap:

```text
u = distanceFromBinding / leafWidth
edgeWeight = smoothstep(0, 1, u)
primaryDisplacement = signedProgress * leafWidth * edgeWeight
waveAmplitude = maxAmplitude * progress * (1 - progress) * edgeWeight
```

The binding row/column has `edgeWeight = 0`; the outer edge has `edgeWeight = 1`. The same vertices transform the complete front bitmap, so text cannot remain straight while paper bends.

### 8.3 Release animation

Release selects a target endpoint from distance and velocity. The remaining animation duration is proportional to remaining distance, capped by the accepted motion duration. A nearly complete drag finishes quickly; a short committed fling may use the full duration.

The visual animation is considered complete when the moving bitmap reaches its endpoint. It must not wait for Foliate relocation.

### 8.4 Cancel animation

Cancel returns the moving snapshot to its origin and performs no navigation. Once the return animation ends, the overlay hides and the unchanged live reader remains authoritative.

## 9. Visual And Settled Positions

The controller tracks two independent positions:

- `visualPageIndex`: the page or spread currently shown by the native snapshot layer
- `settledPageIndex`: the page or spread currently reported by live Foliate

On committed visual completion:

1. `visualPageIndex` becomes the transition target.
2. The target snapshot remains visible as an opaque shield.
3. The next adjacent snapshots may be used immediately for another gesture.
4. Exact Foliate settlement continues in the background.
5. The shield is removed only when live Foliate reports the latest visual target and the live frame is renderable.

If the user commits additional turns while Foliate is settling, those turns update `visualPageIndex` immediately. Settlement coalesces to the latest visual target rather than replaying every intermediate relocation visibly.

## 10. Serialized Foliate Settlement

Settlement is single-flight and generation-aware:

1. At most one `renderer.goTo({ index, anchor })` is active.
2. A commit stores or replaces `pendingTargetPageIndex` with the latest visual target.
3. When the active relocation settles, the controller compares the reported page with the latest visual target.
4. If they differ, it performs one exact relocation to the latest target.
5. If they match and the live frame is renderable, it removes the final snapshot shield.

Intermediate visual targets do not create an unbounded command queue. One history/progress update is accepted for each live exact relocation, but the user never sees Foliate jump through stale intermediate frames.

Generation changes invalidate active and pending settlement without time-based cancellation.

## 11. Persistent Passive Renderer

The passive Foliate renderer remains alive for the reader session. It is not destroyed after routine prewarm completion.

It is recreated only when one of these changes invalidates visual parity:

- publication or resource identity
- pagination fingerprint
- viewport or density
- orientation or spread mode
- reader theme, font, spacing, or layout settings
- paper, edge, stain, or cover-decoration settings
- text direction
- explicit cache clear
- memory-pressure teardown
- reader session close

The passive renderer remains non-interactive and suppresses all reader side effects.

## 12. Rolling Snapshot Cache

The cache is keyed by the snapshot contract in Section 7 and centered on `visualPageIndex`.

Target window:

- current visual page or spread
- two previous visual units
- two next visual units

This is a maximum of five half-resolution snapshots. It is comparable to the previous five-surface transition bundle but reusable across multiple turns.

Rules:

1. The current and immediate adjacent snapshots have highest retention priority.
2. After a committed visual turn, the old far edge is evicted and the new far edge is staged.
3. Staging may run while the user drags an already-ready transition.
4. Duplicate requests for the same key share one in-flight capture.
5. Generation invalidation closes stale bitmaps and drops stale callbacks.
6. Cache insertion and eviction are serialized on the owning Android thread.
7. No composable or draw pass performs capture, navigation, or bitmap allocation.

## 13. Cold And Failure Behavior

### 13.1 Cold adjacent snapshot

Once movement exceeds Android touch slop, Navic claims the gesture and sends `ACTION_CANCEL` to the WebView gesture path. It can never become a tap.

If the required destination snapshot is not ready:

- retain the latest drag displacement and release decision
- show no corrupt or unrelated destination
- stage the exact snapshot asynchronously
- attach the transition when ready and continue from the retained state

If capture fails, perform exact target navigation behind an opaque copy of the current snapshot, then reveal the live reader only after target settlement. This fallback is a flat shielded navigation, never a tap and never a curl.

### 13.2 Boundary

At publication boundaries, drag resistance may be shown, but release always returns to the source. No navigation or history entry is produced.

### 13.3 Lifecycle interruption

Pause, configuration change, reader close, resource switch, or memory pressure invalidates the active generation, closes obsolete snapshots, and restores a coherent source or final shield. No stale callback may reattach an old bitmap.

## 14. Portrait And Landscape Separation

Portrait and landscape share caching, gesture ownership, settlement, wave math, and bitmap ownership, but not leaf geometry or page-step assumptions.

### 14.1 Portrait

- one page occupies the visual surface
- one gesture changes the visual index by one
- forward and backward use the asymmetric Google Play Books layering described in Section 3
- no gutter or synthetic second page is added
- the fixed edge is the edge opposite the active dragged boundary
- the entire visible page is one active leaf

### 14.2 Landscape

- the complete Foliate spread is captured as one bitmap for cache and final-shield continuity
- one gesture changes the visual index by two
- resolved left/right leaf rectangles and the gutter are stored with the transition
- only the active leaf crop is warped; the other leaf is composed separately
- the spread's gutter and page decorations remain present but the gutter is excluded from active-leaf warping
- no per-leaf reverse content is fabricated
- terminal one-page spreads remain centered according to Foliate's resolved layout

Landscape rules must not be copied into portrait by inferring a hidden second page.

## 15. Gesture And Motion Rules

1. Touch slop is evaluated before claiming the gesture.
2. After claim, horizontal drag remains owned by the page-turn controller until release or cancel.
3. Vertical movement beyond the direction-lock ratio cancels page-turn claim before deformation begins.
4. Progress is based on the relevant leaf width, not full-screen or full-spread width when those differ.
5. The moving surface can reach `progress = 1` while the finger remains down.
6. Release at `progress = 1` commits immediately; release at `progress = 0` cancels immediately.
7. Opposite-direction movement reverses progress naturally within `0..1`.
8. A completed visual turn may accept the next gesture within the next rendered frame when its adjacent snapshot is ready.
9. Tap page turning remains available only for gestures that never crossed touch slop.

## 16. Diagnostics

Structured diagnostics remain opt-in and record:

- gesture id and generation
- logical and physical direction
- source, target, visual, and settled page indices
- cache hit, shared in-flight request, or cold capture
- passive-renderer generation and preparation duration
- drag claim and WebView cancellation
- release decision, start progress, velocity, and remaining duration
- visual completion timestamp
- exact relocation dispatch and settlement timestamp
- final-shield removal
- stale callback rejection and bitmap eviction

Diagnostics must make these intervals directly measurable:

```text
gesture claim -> destination ready
release -> visual completion
visual completion -> next gesture accepted
visual completion -> live settlement
```

## 17. Performance Targets

### Required

- Drag response begins within one rendered frame after touch slop.
- Ready-cache next gesture is accepted within 16-33 ms after visual completion.
- No routine 1.47-2.44 second interaction lock after visible completion.
- No routine 13-15 second passive-renderer rebuild after each turn.
- Animation bitmaps remain at half physical resolution unless device evidence proves a lower safe size.
- Maximum cached window is five snapshots plus at most one in-flight capture bitmap.
- Draw performs no WebView calls, JSON parsing, database work, or network work.
- Mesh buffers are reused and bounded to the active leaf; `onDraw` allocates no bitmap, path, array, shader, or JSON object.
- Landscape uses the same two cached spread snapshots as the rejected rigid slide; no extra reverse-face capture is introduced.

### Visual

- no mirrored or backward text
- no transparent moving page
- no rigid complete-spread translation in landscape
- no deformation crossing the center binding
- no straight text over visibly bent paper
- no midpoint freeze
- no post-release tap animation
- no destination blink after animation
- no stale page under the final shield
- no change to text pagination or page geometry

## 18. Accessibility And Reduced Motion

Android's global animator scale must not break navigation. Navic's page-turn preference remains authoritative for the visual style.

When reduced motion is explicitly requested by Navic settings, the same source/destination snapshots may use a short crossfade or immediate shielded exact navigation. The gesture and settlement architecture remains unchanged.

## 19. Staged Delivery

### Stage 0: Contract and guards

- Add source guards forbidding curl/reverse-face surfaces in the production slide path.
- Lock normalized progress, one-page portrait steps, and spread landscape steps in tests.

### Stage 1: Snapshot renderer foundation behind debug

- Add the simplified snapshot and transition models.
- Implement `ReaderPageTurnSlideView` behind a debug comparison flag.
- Prove source/destination draw order with synthetic bitmaps.

### Stage 2: Portrait integration

- Route `canvas` to the snapshot-wave renderer in readerdev only.
- Prove finger tracking, endpoint reach, commit/cancel, no tap leak, and destination visibility.

### Stage 3: Persistent preview and rolling cache

- Keep passive Foliate alive for the reader session.
- Replace two transition-bundle cache entries with five reusable snapshots.
- Prove bounded memory and generation invalidation.

### Stage 4: Decoupled visual continuity

- Separate visual and settled indices.
- Add serialized/coalesced exact settlement and final shield.
- Prove multiple consecutive visual turns while live Foliate catches up.

### Stage 5: Landscape spread identity integration

- Capture complete current and destination spreads.
- Prove `+/-2` exact navigation, gutter continuity, RTL mirroring, and terminal boundaries.

### Stage 5A: Page-aware wave correction

- Replace rigid translation with reusable bounded mesh deformation.
- Add resolved leaf/gutter geometry to transitions.
- Prove portrait front-surface wave behavior.
- Prove forward right-leaf retraction and backward left-leaf expansion in landscape.
- Prove the inactive leaf does not translate and the final spread does not blink.

### Stage 6: Failure and lifecycle hardening

- Cover cold capture, capture failure, rotation, background/foreground, settings changes, resource changes, and memory pressure.

### Stage 7: Emulator acceptance

- Use readerdev with Alcatraz at phone portrait and tablet landscape sizes.
- Capture forward, backward, rapid consecutive, cancel, cold, and boundary videos.
- Inspect frame sequences for the visual invariants in Section 17.

### Stage 8: Physical-device acceptance

- Validate on the Fold portrait screen and Tab S9 Ultra landscape.
- Confirm touch latency, memory, consecutive turns, orientation changes, and Whispersync coexistence.

### Stage 9: Release gate

- Remove the debug comparison path only after the implementation is credible.
- Run focused and full tests.
- Publish a final theta release only after emulator and physical-device evidence passes. Until then, create debug/readerdev artifacts only.

## 20. Acceptance Criteria

The implementation is complete only when all of the following are true:

1. Portrait forward and backward visually match the Google Play Books wave/layering model.
2. Landscape advances complete spreads without reverse-face simulation while visibly respecting the center binding and physical leaf split.
3. The destination is visible during every ready transition.
4. Text remains correctly oriented and deforms only with its paper surface.
5. The active page leaf follows the finger through its full width; the complete landscape spread never moves as one card.
6. Release never freezes at a midpoint.
7. Claimed drags never trigger tap navigation.
8. Repeated turns remain responsive while Foliate settles in the background.
9. Exact target navigation reaches Author's Foreword, Chapter 1, and adjacent pages reliably in the Alcatraz fixture.
10. The overlay hides only after the latest visual target is live and renderable.
11. Memory remains bounded and stale snapshots are recycled.
12. No final release is published before emulator and physical-device evidence is accepted.
13. Emulator frame evidence shows the landscape gutter at one invariant x-coordinate from gesture start through completion.
14. Emulator frame evidence shows non-zero wave curvature at intermediate progress and zero curvature at both endpoints.
