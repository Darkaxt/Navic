# Reader Page Texture And Landscape Gutter Design

## Goal

Improve the ebook reader presentation on large tablets by replacing the current low-resolution page-edge look with high-resolution, varied paper overlays and by adding a landscape spread divider so two-page layouts read as two physical pages instead of one continuous parchment surface.

The visual target is the tablet PoC captured on 2026-07-05: high-detail aged paper, clear but subtle outer page wear, and a center gutter made from layered shadow/highlight rather than a hard line.

## Current State

The reader already has three independent overlay concepts:

- Paper texture: `paperTextureEnabled`
- Page edges: `pageEdgesEnabled`
- Paper stains: `paperStainsEnabled`

The Android reader assets define:

- `ReaderPaperTextureAssets`
- `ReaderPageEdgeOverlayAssets`
- `ReaderPageStainOverlayAssets`
- `ReaderPageBorderOverlayAssets = ReaderPageEdgeOverlayAssets`

The weakness is mostly asset and composition quality, not the presence of the feature. Page edges currently use four small `page-edge-overlay-*.png` files. On a Tab S9 Ultra-size surface those assets upscale visibly and read as blurry indentation/smudging instead of worn paper.

## Design

### Texture Families

Replace the single-purpose low-resolution edge set with three explicit 4K-capable families:

1. `paper-base`
   - Existing parchment/fiber grain role.
   - Must remain low contrast and non-directional enough to support text.
   - Variants: at least 8.

2. `page-edge`
   - Outer and inner page wear: yellowing, fiber breakup, tiny stains, frayed tone shifts.
   - Must be crisp at tablet resolution.
   - Must avoid obvious large repeated blobs.
   - Variants: at least 8 per edge profile.

3. `page-stain`
   - Sparse stains, speckles, watermarks, and mild aging.
   - Must be optional and weaker than edge wear.
   - Variants: at least 8.

All generated texture files should be 4K-class raster assets. Preferred sizes:

- Full-page texture: `3840x2160` landscape-safe or `2160x3840` portrait-safe.
- Edge overlays: `3840x2160` transparent PNG/WebP when covering the full page surface, or a compact 4K strip atlas if the renderer supports edge slicing.
- Stains: transparent PNG/WebP with alpha.

Do not use SVG or CSS gradients as the primary texture source. The issue is visual texture resolution; the fix needs raster detail.

### Variant Selection

Variant selection must stay deterministic per book/page so the texture does not shimmer during recomposition or relocation.

Use the existing seed shape:

- publication URL
- section href/id
- page index when available
- slot name (`current`, `previous`, `next`)

Each layer should derive from a distinct seed suffix:

- `|paper-base`
- `|page-edge`
- `|page-stain`
- `|spread-gutter`

This prevents the same random variant index from accidentally aligning all overlays.

### Landscape Spread Gutter

Add a fourth visual layer only when the resolved layout is a two-page landscape spread.

Name: `spread-gutter`

Composition:

- A narrow vertical center crease.
- Soft shadow falling onto both inner page edges.
- Very faint highlight ridge near the crease.
- Optional tiny fiber/dirt detail along the center fold.

The gutter must not be a hard divider. It should be readable as page separation while preserving a clean reading surface.

Recommended visual stack for a two-page spread:

1. Theme background.
2. Per-page `paper-base`.
3. Per-page text/content.
4. Per-page `page-edge`.
5. Center `spread-gutter`.
6. Per-page `page-stain`.
7. Whispersync/highlight overlays and selection overlays above paper effects.

The gutter should follow the visible spread center, not a hard viewport center when the reader is in a single-page or vertical flow mode. In RTL mode the visual is symmetric, so no content-direction inversion is required.

### Per-Page Surface Model

The current screenshot shows a shared parchment plane across both columns. The target behavior is two separate paper surfaces:

- Left page receives its own base/edge/stain variant.
- Right page receives its own base/edge/stain variant.
- The central gutter covers only the seam between them.

This can be implemented either by extending the existing texture slot model or by adding a spread-aware layer that paints left/right halves separately. The implementation should prefer the smallest change that avoids duplicating texture selection logic.

### Settings

Keep the existing reader settings:

- `Paper texture`
- `Page edges`
- `Paper stains`

Do not add a new user-facing setting for the gutter initially. The gutter is part of `Page edges` because it is visual page-boundary treatment. If `Page edges` is off, the outer edges and center gutter should both disappear.

Future optional setting:

- `Page separation`: Off / Subtle / Strong

Do not add it in the first implementation unless the default cannot satisfy tablet and phone layouts.

### Asset Generation Requirements

Generated assets must be made as reusable texture families, not one-off screenshots.

Rules:

- No visible text.
- No illustrations or decorative objects.
- No high-contrast blobs near common text columns.
- No repeating tile seams at tablet scale.
- Alpha overlays must have transparent background where appropriate.
- Edge overlays should be sharper than current assets but still low contrast.
- The center gutter must work over sepia, light, dusk, and dark themes through opacity control.

For the first asset set:

- `page-edge-overlay-01..08`
- `page-stain-overlay-01..08`
- `spread-gutter-overlay-01..04`

The base paper texture family can be replaced later if the existing base textures are acceptable.

### Performance

This must remain cheap during page turns and scrolling:

- Do not generate textures at runtime.
- Do not blur large images at runtime.
- Do not recompute variant selection every frame.
- Keep all overlay movement driven by the existing texture slot movement pipeline.
- Preload only the active, previous, and next slot overlays.
- Reuse the same layer elements where possible.

The assets can be 4K-class, but the number of simultaneous overlays must stay bounded. A two-page spread should not create unbounded per-column DOM nodes.

### Interaction With Page-Turn Animation

The texture/gutter system must be compatible with the page-turn animation redesign:

- Static page view and animated page capture must use the same enabled layers.
- Curl/canvas/native capture must preserve the gutter if the captured view is a two-page spread.
- During drag, page texture and edge overlays must move with their page surface.
- The center gutter is static while the spread is settled, and should not be painted onto a single moving page unless the renderer captures a full spread.

If this conflicts with the page-turn branch, the texture implementation should land after the page-turn animation architecture settles, but asset preparation can start immediately.

### Tests And Guards

Add source guards for:

- `ReaderSpreadGutterOverlayAssets` exists and is not aliased to page-edge assets.
- `Page edges` disables both edge overlays and spread gutter.
- Landscape/two-page profile activates the gutter; single-page and vertical modes do not.
- Texture variant keys include distinct suffixes for paper, edge, stain, and gutter.
- Moving/captured page surfaces use the same overlay toggles as static pages.

Add unit tests for:

- Deterministic variant selection.
- Different left/right page variants in spread mode.
- No gutter in single-page portrait mode.
- No gutter when `pageEdgesEnabled == false`.

### Acceptance Criteria

On the Tab S9 Ultra:

- Page edges no longer look visibly upscaled or blurry.
- Landscape spreads have a visible but subtle center gutter.
- Text remains readable with paper texture, edge, and stain layers enabled.
- Disabling `Page edges` removes outer edge wear and the center gutter.
- Disabling `Paper stains` removes stain overlays only.
- Page-turn and drag behavior do not desync overlays from page content.

## Implementation Notes

The most likely code areas are:

- `navic-reader-settings-core.js` for asset families and variant counts.
- `navic-reader-helpers.js` for layer rendering, opacity, and slot transforms.
- `navic-reader-appearance.js` for spread-aware layout/profile checks.
- `ReaderBridgeProtocol.kt`, `ReaderChromeState.kt`, and `ReaderPreferenceSettings.kt` only if additional setting fields become necessary.

Bindery and music artwork are out of scope.
