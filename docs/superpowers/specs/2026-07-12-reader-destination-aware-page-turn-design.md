# Destination-Aware Reader Page-Turn Design

**Status:** Approved for staged implementation

**Date:** 2026-07-12

**Baseline:** `v1.0.11-theta92` (`93290009`)

**Supersedes:** Revision 4 of `2026-07-04-reader-page-turn-animation-design.md`

## 1. Objective

Navic must render page turns as physical book transitions backed by the actual EPUB pages involved in the transition.

The animation must show:

- the current page on the front of the moving sheet
- the correct following or preceding page on the reverse of that sheet
- the correct page underneath the moving sheet
- the correct final page or spread before the native overlay is removed

The live Foliate reader remains the only navigation, pagination, history, selection, progress, annotation, and Whispersync authority. A passive renderer may reproduce pages for capture, but it must never become a second interactive reader.

This revision also defines portrait as a physical two-sided book viewed through a one-page camera. Portrait does not reuse landscape spread behavior blindly.

## 2. Non-Negotiable Invariants

1. Foliate owns live layout and final navigation.
2. The live Foliate renderer does not scroll or relocate during drag.
3. Commit is decided only on release.
4. Cancel never navigates.
5. Every committed gesture issues one exact target relocation.
6. A landscape leaf turn advances one sheet, which changes the visible spread by two page ordinals.
7. A portrait navigation advances one page ordinal and alternates between slide and leaf-turn transitions.
8. Native animation is not removed until its animation and the expected live Foliate target have both settled.
9. Incorrect, stale, or incomplete destination pixels are never shown as if they were valid.
10. No timeout cancels rendering, capture, navigation, or settlement. Generation tokens and lifecycle events cancel obsolete work.
11. No progress, history, selection, annotation, Whispersync, or media-overlay event may originate from the passive renderer.
12. No page-turn path may recreate the rejected synthetic reader shell or alter Foliate text geometry.
13. A committed animation never pauses at the binding or at a half-folded state while Foliate loads. It runs continuously to the captured final page or spread, which may remain statically above Foliate until exact target settlement.

## 3. Page Identity

### 3.1 Visual page ordinal

The complete pagination profile is the source of truth for reflowable visual page ordinals. Navic adds the inverse of the current locator-to-position mapping:

```text
visual page index
  -> pagination profile chapter
  -> spineIndex + href
  -> chapterPageIndex + chapterPageCount
  -> renderer anchor
```

The inverse resolver clamps only at publication boundaries. It does not silently substitute an adjacent page.

### 3.2 Page side

Each visual page resolves to `left`, `right`, or `center`.

Priority:

1. EPUB `page-spread-left`, `page-spread-right`, or `page-spread-center` metadata.
2. Fixed-layout section `pageSpread` metadata.
3. Cover-side anchor plus visual page parity.
4. LTR fallback: cover/right, then alternating left/right.
5. RTL fallback: mirror LTR.

The side resolver is shared by landscape selection, portrait camera position, binding decoration, and cover-reveal placement.

### 3.3 Exact relocation

Committed animation sends a target visual page index, not a relative `next()` or `prev()` command.

The live runtime resolves that target to one renderer navigation:

```text
renderer.goTo({ index: spineIndex, anchor })
```

It then pushes one history state and reports the expected target page index in the settlement token. Overlay removal requires the reported settled index to match the target.

## 4. Transition Bundle

Each gesture uses an immutable `ReaderPageTurnBundle`:

```text
key
  publication identity
  pagination fingerprint
  viewport and device scale
  reader settings fingerprint
  current visual page index
  direction
  layout mode
  text direction

surfaces
  currentBase
  turningFront
  turningReverse
  underneath
  finalBase

navigation
  source visual page index
  target visual page index
  transition kind
  physical direction
  source and target page sides
```

All surfaces include their exact pixel rectangle and logical role. A bundle is valid only while every key component still matches the live reader.

### 4.1 Landscape forward, LTR

For a visible spread `N / N+1`:

- current base: `N / N+1`
- turning front: `N+1`
- turning reverse: `N+2`
- underneath: `N+3`
- final base: `N+2 / N+3`
- target visual page index: `N+2`

### 4.2 Landscape previous, LTR

For a visible spread `N / N+1`:

- current base: `N / N+1`
- turning front: `N`
- turning reverse: `N-1`
- underneath: `N-2`
- final base: `N-2 / N-1`
- target visual page index: `N-2`

RTL mirrors the physical page roles while preserving logical reading direction.

### 4.3 Landscape boundaries

At publication boundaries:

- missing ordinals are represented only by explicit cover, inside-cover, or neutral paper surfaces defined by the book-side model
- the target index is clamped before bundle preparation
- a turn is suppressed when no logical target exists
- center pages use a direct transition unless the publication supplies a valid leaf-side relationship

## 5. Passive Preview Renderer

### 5.1 Ownership

Navic creates at most one passive `foliate-view` for the active publication. It is opened once, reused, and destroyed with the reader session.

It receives:

- the same publication URL
- the same viewport and spread profile
- the same typography and publisher-CSS policy
- the same theme, paper, edge, stain, and cover decoration settings
- the same text direction

It does not receive:

- progress restoration or persistence
- history listeners
- selection or annotation listeners
- Whispersync or media-overlay listeners
- reader interaction handlers
- native message posting for live location state

### 5.2 Staging and capture

The passive renderer remains non-interactive and outside the visible composition during ordinary reading.

At capture time:

1. Native code captures and attaches an opaque current-base overlay.
2. JavaScript stages one requested visual page or spread in the passive renderer.
3. JavaScript emits a role-specific ready generation only after layout, fonts, images, and reader decoration are settled.
4. Native code draws only the staged WebView page rectangle into the role bitmap.
5. JavaScript stages the next role using the same passive renderer.
6. After all required role bitmaps are captured, JavaScript restores the live WebView composition beneath the still-opaque native overlay.
7. The latest stored drag position is applied and visible deformation begins.

This capture sequence must pass a feasibility gate on readerdev before the animated integration proceeds.

### 5.3 Capture fidelity gate

The passive capture is accepted only if it matches the live page for:

- font family, weight, size, line height, spacing, and margins
- publisher headings and embedded fonts
- images and image scaling
- active theme and paper color
- paper, edge, stain, and gutter overlays
- RTL direction
- fixed-layout page assets

The gate records capture duration and bitmap allocation but does not impose a cancellation deadline.

## 6. Native Composition

The native overlay owns animation only. It does not paginate.

Layer order:

1. `currentBase`
2. `underneath` replacing the origin-side page behind the moving sheet
3. cast shadow
4. deforming `turningFront`
5. deforming `turningReverse`
6. crease shadow and edge highlight

The current-base companion page remains stable until the reverse face covers it naturally. The final-base pixels must agree with the destination visible beneath the overlay before detachment.

The existing edge-origin fold geometry remains the initial renderer. This revision changes its bitmap inputs and settlement semantics, not the accepted arbitrary-edge gesture behavior.

## 7. Landscape Behavior

### 7.1 Gesture

- An outer-edge drag selects the physical page at that edge.
- Drag origin Y remains exact and may begin anywhere along the edge.
- The binding edge remains fixed.
- The page front, reverse, and underneath surfaces are real page captures.

### 7.2 Commit

- Release threshold is evaluated by the pure state machine.
- A committed leaf turn targets the next or previous spread start, two visual page ordinals away.
- Navigation starts at commit-animation start and runs concurrently with the uninterrupted native fold.
- The native fold crosses the binding and completes at the captured final spread without waiting for Foliate.
- If Foliate is still settling when native motion completes, the overlay holds the fully rendered `finalBase`, never the half-folded sheet.
- The native overlay remains opaque until Foliate reports the same target page index.
- Detachment occurs on frame completion plus exact target settlement, in either arrival order.

### 7.3 Cancel

- The moving sheet relaxes to the current spread.
- No target relocation is sent.
- All destination bitmaps are retained only if their bundle key still matches the settled location.

## 8. Portrait Behavior

Portrait is a one-page camera over a virtual physical spread.

### 8.1 Left page to right page

For LTR forward navigation when the current page is physically left:

- no leaf is turned
- the camera slides from the left page to the actual adjacent right page
- the target advances by one visual page ordinal
- the page surface, gutter cue, and thin cover reveal move to the right-page state

### 8.2 Right page to next left page

For LTR forward navigation when the current page is physically right:

- the right sheet turns toward the left
- the current right page is the turning front
- the next left page is the turning reverse
- the following right page is visible underneath
- after the leaf settles, the virtual camera returns to the new left page
- the target advances by one visual page ordinal

### 8.3 Previous and RTL

Previous navigation mirrors the slide and leaf transitions. RTL mirrors physical sides while preserving the same logical rules.

### 8.4 Portrait decoration

- A left page has its binding cue on the right.
- A right page has its binding cue on the left.
- The thin cover-tinted reveal appears only at the outer edge.
- The reveal never grows to fill unused viewport space.
- Portrait does not inherit the landscape center gutter or two-page geometry.

## 9. Preparation, Reuse, and Cache

All page preparation is asynchronous.

The passive renderer may prepare the next likely role after a settled relocation, but native capture occurs only behind an opaque current-base overlay.

The cache is bounded:

- current transition bundle
- previous transition bundle
- next transition bundle

No unbounded bitmap or DOM cache is allowed.

Invalidation occurs on:

- publication change
- pagination fingerprint change
- viewport size, density, orientation, or spread change
- typography, theme, publisher CSS, paper, edge, stain, or direction change
- renderer generation change
- Android memory pressure
- reader background, close, or destruction

Every invalidation increments a generation. Late JavaScript readiness, draw completion, image completion, or navigation settlement from an older generation is ignored and its bitmap recycled.

## 10. Gesture State Machine

States:

- `Idle`
- `Preparing`
- `Deforming`
- `Committing`
- `Relaxing`
- `Settling`

Rules:

1. Touch-down records origin, physical direction candidate, location generation, and latest pointer.
2. Preparation captures the current base and resolves the transition bundle.
3. Pointer updates during preparation only replace the latest pending pointer state.
4. Deformation starts only with a valid bundle.
5. Release during preparation records the release decision; it does not start an incorrect animation.
6. Commit emits one exact target navigation.
7. Relax emits no navigation.
8. Commit animation proceeds continuously to its final visual state independently of Foliate settlement.
9. Settlement requires native completion and exact Foliate target confirmation. A completed native animation waits by displaying `finalBase`.
10. Cancel, rotation, backgrounding, destruction, or generation change clears all transient state synchronously.

## 11. Failure Behavior

- Current-base capture failure: use direct navigation and disable animated page turns for the session.
- Passive renderer unavailable: use direct navigation for that gesture and retain animation availability for later valid bundles.
- Destination role mismatch or stale generation: discard the bundle and use direct navigation.
- Image or font load failure in passive renderer: surface the same fallback the live reader uses; never substitute another page.
- Exact target relocation failure: keep the native current/final overlay stable, report diagnostics, and return to the last confirmed live location through the existing reader recovery path.
- Unsupported flow mode: direct navigation.

No failure path waits for a timeout or leaves a native overlay attached indefinitely. Completion and cancellation are driven by explicit callbacks, lifecycle, renderer generation, and navigation settlement.

## 12. Diagnostics

Opt-in reader diagnostics record:

- bundle key and generation
- source, front, reverse, underneath, final, and target page indices
- resolved spine indices and chapter page positions
- passive renderer role-ready events
- current-base and role capture durations and bitmap sizes
- gesture side and transition kind
- commit target and settled target
- cancellation or invalidation reason
- bitmap allocation and recycle totals

Normal operation does not log per-frame geometry or throwable stacks.

## 13. Testing

### 13.1 Pure model tests

- visual page index resolves to exact chapter locator
- chapter boundary mapping in both directions
- cover and publication boundary clamping
- explicit page-spread metadata overrides parity
- LTR and RTL side resolution
- landscape forward and previous bundle formulas
- portrait slide/turn alternation
- target index differs by two in landscape and one in portrait

### 13.2 State tests

- preparation stores only the latest pointer update
- release before preparation completion commits or relaxes once bundle is ready
- cancel during every phase performs no navigation
- stale readiness and capture callbacks are ignored
- commit emits one exact target relocation
- commit animation never enters a mid-fold hold state
- delayed Foliate settlement leaves the fully rendered final base visible
- native completion before Foliate and Foliate before native both detach exactly once
- wrong settled page index does not detach

### 13.3 Source guards

- Canvas mode does not call live `renderer.scrollBy` during drag
- animated landscape commits do not call relative `next()` or `prev()`
- passive renderer cannot post live progress, history, selection, annotation, or Whispersync events
- normal text pages do not use synthetic shell geometry
- no timeout cancels page-turn work
- bitmap caches have fixed bounds and explicit recycling paths

### 13.4 Visual validation

Use the production Alcatraz EPUB in readerdev.

Landscape:

- forward leaf with correct front, reverse, underneath, and final text
- previous leaf
- same-section and cross-section transitions
- slow drag, fast flick, cancel, and edge-origin Y variation
- no post-animation tap blink
- final spread advances by two pages

Portrait:

- left-to-right slide
- right-to-left leaf and camera return
- mirrored previous transitions
- alternating binding cue and cover reveal
- no landscape gutter leakage

Lifecycle:

- rotate during preparation, deformation, and settlement
- background and resume during each phase
- settings change invalidates stale bundles
- memory-pressure cleanup

The physical tablet is required only when emulator and device composition differ or before the public release gate.

## 14. Staged Implementation

1. Add exact page locator, side resolver, bundle planner, and tests.
2. Add exact target relocation and settlement-index contract.
3. Implement the passive renderer and prove one staged page matches the live reader.
4. Implement bounded role capture and bitmap lifecycle.
5. Integrate real landscape front/reverse/underneath/final surfaces.
6. Replace relative landscape navigation with exact two-page target commits.
7. Implement portrait virtual-spread slide/turn behavior and alternating decoration.
8. Add RTL, boundary, fixed-layout, lifecycle, and failure fallbacks.
9. Run focused and full tests, JavaScript syntax checks, and reader harness checks.
10. Validate readerdev recordings at portrait and tablet landscape dimensions.
11. Sync current master, resolve conflicts without replacing newer reader behavior, build once, publish the next theta release, and verify the public APK metadata and asset.

Each stage is committed only after its tests pass. No public release is created from a partially integrated destination pipeline.
