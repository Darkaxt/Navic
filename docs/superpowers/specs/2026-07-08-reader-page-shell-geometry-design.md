# Reader Page Shell Geometry And Cover Backdrop Design

## Status

Design specification. Do not implement production reader changes from this document until the static prototype and readerdev validation gates pass.

## Background

The current reader paper work improved the raw overlay assets, but the live tablet view still shows a structural mismatch:

- Paper, edge, stain, and gutter effects are painted as visual overlays after the EPUB layout is already decided.
- The center gutter is visual-only. In landscape spread mode, text is still positioned as if the visual gutter did not consume page space, so each page reads as off-center inside the final rendered sheet.
- The cover backdrop path does not behave like a complete book shell. The foreground cover can still appear cut off, and the surrounding space does not consistently become a blurred/diffuse cover field plus a simple tinted back cover.
- The current system can make the page look decorated, but it does not make the page content and the visual paper surface agree on geometry.

This spec replaces the overlay-only mental model with a shared geometry contract. The page shell, paper layers, gutter, cover backdrop, and text layout must all resolve from the same measurements.

## Goals

- Make the reader page look like a coherent book surface on tablets, foldables, and phones.
- Ensure text is centered inside the visual page content area, not inside the raw viewport half.
- Preserve the existing toggles:
  - Paper texture
  - Page edges
  - Paper stains
  - Cover backdrop
- Add a complete cover/back-cover shell model:
  - Full foreground cover remains visible.
  - Blurred/diffuse cover fills the black background areas.
  - A simple tinted back-cover plane appears when useful, with rounded corners and light wear.
- Validate the look in a static HTML prototype before changing the live reader.
- Validate the live implementation with readerdev, emulator, and the connected tablet before creating a public release.

## Non-Goals

- This is not the page-curl animation redesign.
- This does not change Whispersync, text highlighting, audio playback, or reader media overlay behavior.
- This does not change EPUB parsing or Foliate navigation semantics.
- This does not modify Bindery, music artwork, or Aurral behavior.
- This does not introduce hard timeouts. Verification can use polling/heartbeats, but no runtime cancellation timeout should be added.

## Design Principle

The reader must have one source of truth for page geometry.

Every surface that depends on page shape must consume the same geometry:

- EPUB content layout
- Paper base texture
- Outer page edge wear
- Center gutter shadow/highlight
- Paper stains
- Cover backdrop
- Foreground cover image
- Back-cover plane
- Selection/highlight overlays

If a layer needs a page rectangle, it must use the resolved page-shell geometry. It must not independently infer page size from viewport width, CSS column width, or image dimensions.

## Proposed Model

Introduce a pure reader-side model named `ReaderPageShellGeometry`.

The exact implementation language can be JS-only initially, but the model should be serializable for diagnostics and test assertions.

```ts
type ReaderPageShellMode = "single" | "spread" | "cover";

type ReaderRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

type ReaderPageShellGeometry = {
  mode: ReaderPageShellMode;
  viewportRect: ReaderRect;
  shellRect: ReaderRect;
  pageRects: {
    single?: ReaderRect;
    left?: ReaderRect;
    right?: ReaderRect;
  };
  contentRects: {
    single?: ReaderRect;
    left?: ReaderRect;
    right?: ReaderRect;
  };
  gutterRect?: ReaderRect;
  edgeInsets: {
    top: number;
    bottom: number;
    outer: number;
    inner: number;
  };
  cover?: {
    backdropRect: ReaderRect;
    foregroundRect: ReaderRect;
    backCoverRect?: ReaderRect;
  };
};
```

### Geometry Rules

- `spread` mode is used only when the reader already resolves a landscape spread.
- `single` mode is used for portrait and narrow widths.
- `cover` mode is used for the native shell cover or EPUB cover page presentation.
- The center gutter in `spread` mode is a real reserved region, not just a painted line.
- Content rectangles are computed after subtracting:
  - outer edge visual reservation
  - inner gutter visual reservation
  - theme/page padding
  - safe-area constraints
- The same geometry must be used for LTR and RTL. RTL may swap semantic left/right content, but it must not invert measurements ad hoc.
- The geometry helper must be deterministic and cheap. It should not perform image decoding, network access, DB access, or async work.

## Static Prototype Gate

Before production code changes, update the HTML prototype until it visually matches the intended target.

Required prototype modes:

1. Landscape spread
2. Portrait single page
3. Cover/back-cover page

Prototype requirements:

- Use the original paper texture source where practical, not a low-resolution approximation.
- The paper area must not shrink excessively compared with the live reader.
- Edge wear must look like worn edges, not a wide coffee-stain border.
- Edge intensity and edge width must be independently tunable in the prototype.
- The center gutter must read as a book fold, and the text area must be visibly centered inside each resulting page.
- The cover mode must show:
  - full foreground cover image with `contain` behavior
  - diffuse cover backdrop behind it
  - simple tinted back-cover plane with rounded corners and subtle wear
  - no black cutoff fields when cover backdrop is enabled
- Texture layers must be testable independently:
  - paper texture on/off
  - edge wear on/off
  - stains on/off
  - gutter on/off
  - cover backdrop on/off

No production reader implementation should begin until the prototype screenshots are accepted.

## Live Reader Architecture

### Shell Layout

Add a reader shell layout pass that resolves `ReaderPageShellGeometry` before visual layers are painted.

The layout pass should run when any of these change:

- viewport size
- orientation
- spread mode
- theme margins
- reader font/layout settings
- cover/backdrop mode
- paper/edge/stain settings

It must not depend on image loading completion. Real images can update the backdrop colors later, but the shell rectangles must be stable.

### Text Layout

The EPUB text layout must consume the content rectangles.

Implementation needs a small readerdev spike because Foliate can expose content in different forms depending on mode:

- single iframe/page
- two-page spread
- CSS columns
- synthetic spread built by the host

The accepted implementation must prove, through readerdev diagnostics, which insertion point controls content geometry reliably.

Allowed strategies:

- Apply side-aware CSS variables to the Foliate content root.
- Apply side-aware padding/margins to the rendered contents.
- Configure Foliate layout if an existing stable API exists.

Rejected strategy:

- Paint a gutter overlay while leaving content measured against the raw viewport.

### Visual Layer Order

The live reader should render layers in this order:

1. App/theme background
2. Book shell base or cover backdrop
3. Paper base texture
4. EPUB/Foliate content
5. Edge wear/rim
6. Center gutter shadow/highlight
7. Paper stains
8. Media overlays, text highlights, selections, and reader chrome

The cover foreground must never be below the diffuse backdrop or back-cover plane.

### Cover Mode

Cover mode uses the same geometry model, but with cover-specific rects.

Rules:

- Foreground cover uses contain behavior and remains fully visible.
- The diffuse cover backdrop uses cover behavior and blur/dimming behind the foreground.
- The back-cover plane is simple:
  - dominant cover color tint when available
  - sepia/brown fallback when unavailable
  - rounded corners
  - subtle discoloration and edge wear
  - no detailed fake artwork or text
- The foreground cover must not be cropped by backdrop or shell clipping.
- If the cover image is unresolved, show the back-cover plane and generated title fallback without black voids.

### Portrait Mode

Portrait single-page mode should feel like the right page of the spread:

- It may show a subtle left gutter hint.
- It must not show an inside cover slab on the left.
- Text is centered inside the content rectangle after edge and gutter reservations.
- The paper surface should keep the same texture, edge, and stain language as spread mode.

## Settings

Existing settings remain:

- Paper texture
- Page edges
- Paper stains
- Cover backdrop

Behavior:

- Paper texture toggles only the paper base texture.
- Page edges toggles outer edge wear and the center gutter visuals.
- Paper stains toggles stain overlays.
- Cover backdrop toggles diffuse cover/back-cover behavior.

The first production implementation should avoid adding user-facing intensity sliders unless the accepted prototype cannot be represented with stable defaults. Developer-only prototype controls are allowed in the HTML prototype.

## Diagnostics

Readerdev must expose a geometry diagnostic snapshot.

Suggested JS bridge/debug payload:

```json
{
  "type": "reader-shell-geometry",
  "mode": "spread",
  "viewportRect": { "x": 0, "y": 0, "width": 2960, "height": 1848 },
  "shellRect": { "x": 120, "y": 80, "width": 2720, "height": 1688 },
  "pageRects": {},
  "contentRects": {},
  "gutterRect": {},
  "settings": {
    "paperTextureEnabled": true,
    "pageEdgesEnabled": true,
    "paperStainsEnabled": true,
    "coverBackdropEnabled": true
  }
}
```

The diagnostic must be readable from readerdev/emulator automation and useful in ADB investigations.

## Test Plan

### Unit / Source Tests

Add tests for:

- Spread geometry reserves a real gutter.
- Spread content rectangles are centered inside visual page rectangles.
- Single-page geometry does not create a right-side blank slab.
- Cover geometry keeps the foreground cover contained and visible.
- RTL spread produces symmetric geometry without ad hoc sign inversions.
- Disabling paper texture removes only the paper texture layer.
- Disabling page edges removes both edge wear and gutter visuals.
- Disabling paper stains removes only stain layers.
- Cover backdrop cannot replace or crop the foreground cover.

Source guards:

- Gutter rendering must consume `ReaderPageShellGeometry.gutterRect`.
- Edge rendering must consume page rectangles from `ReaderPageShellGeometry`.
- Cover backdrop code must include distinct backdrop, foreground, and optional back-cover slots.
- Existing media overlay/highlight paths must not be modified by this work unless required for z-order compatibility.

### Static Prototype Validation

Generate screenshots for:

- Landscape spread with text
- Portrait page with text
- Cover/back-cover mode
- Each overlay toggle state

Prototype screenshots should be compared against the accepted target before production implementation.

### Readerdev / Emulator Validation

Use readerdev for fast iteration:

- Install readerdev build to emulator.
- Load a known EPUB sample.
- Capture screenshots in landscape and portrait.
- Capture the `reader-shell-geometry` diagnostic payload.
- Verify content rectangles align with visual page rectangles.

### Physical Tablet Validation

The Tab S9 Ultra is required before public release.

Validation must include:

- ADB screenshot in landscape spread mode.
- ADB screenshot in portrait mode.
- ADB screenshot of cover/back-cover mode.
- ADB or readerdev diagnostic snapshot for each mode.
- Confirmation that toggles work live:
  - Paper texture
  - Page edges
  - Paper stains
  - Cover backdrop

## Implementation Sequence

1. Update the static HTML prototype.
2. Get visual approval for spread, portrait, and cover modes.
3. Add `ReaderPageShellGeometry` helper and unit tests.
4. Add readerdev geometry diagnostics.
5. Wire visual layers to the geometry helper.
6. Wire content layout to the geometry helper.
7. Wire cover/back-cover mode to the geometry helper.
8. Add source guards and focused reader tests.
9. Validate with emulator readerdev screenshots.
10. Validate with physical Tab S9 Ultra ADB screenshots.
11. Commit and release only after the above validation is green.

## Acceptance Criteria

- Landscape spread visually resembles the accepted prototype.
- Text is centered inside each resulting page, accounting for the center gutter and edge reservations.
- The center gutter is part of layout, not just an overlay.
- Portrait page looks like a single book page, not a notepad.
- Cover page has no black cutoff areas when cover backdrop is enabled.
- Foreground cover remains fully visible.
- A simple tinted back-cover plane appears where expected.
- Paper texture, page edges, and stains can be independently toggled.
- No visible low-resolution banding on the Tab S9 Ultra.
- No new reader crashes, page-turn regressions, or Whispersync/highlight regressions.
- Public release is created only after readerdev/emulator and physical tablet validation pass.

## Risks

- Foliate may not expose a clean side-aware content layout hook in every mode.
- EPUB content with fixed layout or unusual CSS may resist margin injection.
- Applying geometry too late could cause visible layout jumps during orientation changes.
- Overly strong edge/burn effects can make the page look like a bordered notepad instead of a worn book.
- Cover dominant-color extraction must not block cover rendering.

## Open Implementation Questions

These are implementation spike questions, not owner questions:

- Which Foliate hook should own side-aware content margins in spread mode?
- Can readerdev reliably expose live page/content rectangles after every layout update?
- Should cover dominant color be computed from an existing image pipeline cache or from the loaded cover bitmap event?
- Which existing reader tests should become geometry tests versus visual source guards?
