# Reader Aged Paper Safe Rebuild Design

## Status

Approved design specification for the continuation of the theta77 reader-surface rollback. Production implementation must follow the staged plan in `docs/superpowers/plans/2026-07-10-reader-aged-paper-safe-rebuild.md`.

## Goal

Keep the restored theta77 Foliate layout authority while making the optional old-book presentation visibly closer to the accepted HTML proof of concept. Correct the landscape spread centering, add a distinct Aged Paper theme, preserve the validated cover backdrop, and define a portrait treatment that is designed as a single page rather than a reduced landscape spread.

## Evidence From The Physical Tablet Gate

The reader-safe branch was installed as `darkaxt.navic.readerdev` on the Tab S9 Ultra and tested with Alcatraz.

Text spread evidence:

- Capture: `captures/reader-dev/reader-tablet-text.png`
- WebView viewport: `1691x1056` CSS pixels at `1.75` device pixel ratio.
- Foliate renderer: paginated, two columns, no `readerPageShellGeometry` data.
- Current Foliate spread gap: vendor default `7%`.
- Current gap between the measured prose columns: approximately `102px`.
- Paper, edge, stain, and gutter layers are all present and enabled.
- Sepia opacities observed at runtime: paper `0.46`, edge `0.64`, stain `0.72`, gutter `0.88`.

Cover evidence:

- Capture: `captures/reader-dev/reader-tablet-cover.png`
- Diffuse backdrop is visible.
- Foreground cover uses contained geometry and remains uncropped.
- The unrequested green/back-cover plane is absent from the cover screen.

The rollback safety criteria passed, but the text spread did not pass the visual improvement criterion: the center text gap remains too narrow and the resulting page still reads much like theta77 rather than the accepted aged-paper PoC.

## Design Decisions

### 1. Aged Paper Is A Separate Reader Theme

Sepia already supplies a warm color palette. Adding another unconditional warm wash to Sepia would double-tint existing users and conflate color preference with paper aging.

Add a new theme key:

```text
aged-paper
```

User-facing label:

```text
Aged Paper
```

Behavior:

- Existing Sepia behavior remains unchanged.
- Existing saved themes require no migration.
- Aged Paper uses the same readable foreground family and image treatment as Sepia.
- Aged Paper uses a warmer paper base, stronger edge definition, visible but restrained stains, and deeper gutter modeling.
- Existing `Paper texture`, `Page edges`, and `Paper stains` toggles remain authoritative over their individual layers.
- Aged Paper is selectable globally and from the live reader settings panel.

The implementation must introduce semantic helpers for warm reader themes rather than spreading `theme == sepia || theme == aged-paper` checks across the codebase.

### 2. Foliate Remains The Geometry Authority

Normal text pages must not restore any of the discarded shell-geometry mechanisms:

- no `readerPageShellGeometry`
- no simulated cover slab around text pages
- no extra shell/page/body margin stack
- no renderer relocation into a synthetic page rectangle
- no white content window over a separate paper surface

Navic may configure documented Foliate layout inputs. It must not independently reposition the renderer.

### 3. Landscape Page And Content Gaps

The landscape two-page spread uses two independent Foliate-owned layout inputs:

- `gap = 2%` controls the real renderer frame and therefore the physical page edges.
- `content-gap = 6%` controls document padding, prose-column width, and the gap between prose columns.

Targets:

```text
physical gap: 2%
content gap: 6%
```

Rules:

- Apply only to paginated horizontal two-page landscape spreads.
- Do not apply to portrait, single-page, vertical-paged, or scrolled flows.
- Remove both explicit attributes when the layout no longer qualifies so Foliate returns to its normal behavior.
- The gutter overlay must remain centered on the resolved spread seam.
- The gap change is a layout correction, not an Aged Paper-only decoration. It applies to every reader theme in qualifying landscape spreads.

The targets are deliberately independent. The physical gap keeps the page surfaces wide and the back-cover reveal thin. The content gap restores normal book-page text margins without widening the cover reveal or introducing an unused shell band.

For a qualifying spread, the physical geometry is `x = 1%` at each outer edge and `2x = 2%` across the complete spread seam. The real page surfaces are `49%` wide. The independent `6%` content gap is resolved inside Foliate's paginator into equal page-relative prose margins and a center prose gap twice either individual margin. On the readerdev Tab S9 Ultra landscape profile, the expected values are approximately `61.74px` left/right document padding and `123.48px` between prose columns. Navic must preserve both relationships:

- paper, edge, and stain artwork begins at the resolved Foliate page boundary instead of the viewport edge
- the complete `1%` outer inset reveals the back cover, leaving no unused strip between the cover and page
- the band inherits a darkened, desaturated dominant tint resolved once from the cached native cover; theme color is the fallback when no cover tint is available
- the cover remains a slight oversize rim of an opened book rather than a second frame
- the reveal is painted through the existing decorative backing/overlay pipeline; it is not a shell node or a layout rectangle
- Navic does not reposition the text, renderer, or iframe independently; Foliate recalculates pagination from the two explicit layout inputs
- the two-sided landscape reveal is absent from portrait, scrolled, fixed-layout, and cover modes; paginated portrait uses the separate right-only reveal defined below

This keeps the thin physical `1% / 2% / 1%` surface geometry while restoring natural prose margins. The physical cover dimension never derives from `content-gap`.

### 4. Landscape Aged Paper Composition

The landscape spread is composed over the real Foliate viewport and columns:

1. Theme background.
2. Per-page warm paper base and fiber texture.
3. Foliate EPUB content.
4. Per-page edge wear/rim.
5. Center gutter shadow and highlight aligned to the spread seam.
6. Per-page stains/patina.
7. Whispersync, annotations, selection, and reader chrome.

The Aged Paper base should reproduce the PoC's depth using theme-aware CSS composition plus the existing 4K raster assets:

- warm base close to the PoC's parchment family, not the cleaner Sepia cream
- a subtle page-local tonal gradient
- a narrow edge treatment that reads as wear, not a coffee-stain border
- no circular corner dots, holes, or window-like shapes
- no large runtime blur or generated bitmap work

The left and right pages retain deterministic independent texture variants. The settled spread uses one center gutter; it does not paint a duplicate seam per page.

All page-local decorative layers use the same resolved physical page bounds. Paper, edge wear, and stains must not disagree about where the left or right page begins. The narrow outer back-cover reveal remains beneath those page-local layers, consumes the complete `1%` outer inset, and never appears on the shell cover screen. Increasing the independent `6%` content gap must not increase this reveal. Its tint is derived once from the cached native shell-cover file during publication preparation and carried through the existing open command; ordinary page rendering must not request or decode the cover again.

### 5. Portrait Is A Separate Single-Page Composition

Landscape geometry must not be copied directly into portrait.

Portrait rules:

- one Foliate page surface
- no explicit landscape `2%` physical gap or `6%` content gap
- no centered spread-gutter overlay
- no right-side empty page or inside-cover slab
- a fixed `1%` right-only external back-cover reveal, tinted from the cached cover color with enough opacity to remain visibly cover-derived at that narrow width
- no back-cover reveal on the left binding edge
- outer edge wear on the visible page boundary
- an optional subtle binding hint on the left edge when `Page edges` is enabled
- one paper and stain variant covering the page
- content remains centered by Foliate's normal single-page layout

The portrait binding hint and right cover reveal are decoration only. Foliate and its iframe remain `100%` wide and retain their native padding and pagination. The shared paper, edge, and stain bounds end at `99%`, exposing the already-resolved backing tint in the final `1%`; no page shell, renderer resize, or content offset is allowed. The reveal is absent from scrolled, vertical-paged, fixed-layout, and cover modes.

### 6. Cover Mode Remains Independent

The validated cover path is preserved:

- blurred/diffuse cover backdrop fills unused space
- foreground cover uses contain geometry
- foreground cover stays above the backdrop
- foreground cover is not cropped
- no green/back-cover square on the cover page
- paper, edge, stain, and landscape gutter changes do not affect cover mode

Any implementation that changes the cover screenshot must be treated as a regression unless the change is explicitly required by this specification.

## Settings Contract

Theme choices:

- Light
- Sepia
- Aged Paper
- Dusk
- Dark
- Black

Layer toggles:

- `Paper texture`: controls paper fibers only.
- `Page edges`: controls outer edge wear and the relevant landscape gutter or portrait binding hint.
- `Paper stains`: controls stains/patina only.
- `Cover backdrop`: controls the diffuse cover background only.

No new opacity sliders are added. The Aged Paper theme is the stable visual preset.

## Performance And State Rules

- Theme and layer projections are pure and synchronous calculations over already-loaded settings.
- No image, DB, Bindery, network, or filesystem work runs from composition or a frame callback.
- Texture variants remain deterministic per publication, section, page, and layer suffix.
- Active, previous, and next slots remain bounded.
- Existing layer nodes are reused.
- Large images are prepackaged; no runtime large-image blur is added to text pages.
- No cancellation timeout is introduced.

## Diagnostics

Extend readerdev diagnostics to report:

```json
{
  "theme": "aged-paper",
  "spreadMode": "spread",
  "foliateGap": "2%",
  "foliateContentGap": "6%",
  "paperTextureEnabled": true,
  "pageEdgesEnabled": true,
  "paperStainsEnabled": true,
  "paperLayerPresent": true,
  "edgeLayerPresent": true,
  "stainLayerPresent": true,
  "gutterLayerPresent": true
}
```

Portrait diagnostics must report `spreadMode: single`, no explicit landscape physical/content gaps, no spread gutter, and `backCoverEdge: right` with a fixed `1%` reveal for paginated horizontal text pages.

## Validation Strategy

### Code-Level Gates

- Kotlin theme normalization, cycling, preference round-trip, display labels, and settings options include Aged Paper.
- JS theme palette and warm-theme semantics include Aged Paper.
- Foliate's `gap` and `content-gap` attributes are set only for qualifying landscape spreads and removed otherwise.
- Existing shell-geometry negative guards remain green.
- Layer toggles independently remove only their owned visual layer.
- Cover containment guards remain green.
- Node syntax checks and focused Android host tests pass after each implementation stage.

### Emulator Visual Gates

Use `readerdev` on the emulator after each visual stage.

Required captures:

- landscape Sepia spread
- landscape Aged Paper spread
- portrait Aged Paper page
- Aged Paper with paper texture off
- Aged Paper with page edges off
- Aged Paper with paper stains off
- cover page with backdrop on
- cover page with backdrop off

The emulator is the normal visual approval environment. Compare captures to the accepted HTML PoC and the theta77 baseline.

### Physical Tablet Escalation

Do not wait for the tablet by default. Require the tablet only when one of these is true:

- emulator and prior tablet captures disagree materially
- high-DPI texture sharpness or banding cannot be judged reliably in the emulator
- the Foliate viewport or system-inset geometry differs enough to affect the spread gap
- the final emulator capture is ambiguous against the acceptance criteria

If none apply, complete code and visual validation on the emulator and proceed.

## Acceptance Criteria

Landscape:

- center prose margins are visibly wider than theta77 and balanced around the gutter
- the gap comes from Foliate's native layout input
- the Aged Paper theme is visibly warmer and more worn than Sepia
- edge wear is narrow and continuous, not a framed window
- gutter depth is clear without covering text
- no shell geometry, white content window, or triple-margin stack returns

Portrait:

- page reads as one page, not half of a spread
- no center seam or second-page artifact
- optional left binding hint does not move content
- paper, edges, and stains remain independently controllable

Cover:

- diffuse backdrop remains visible
- foreground cover remains completely visible and uncropped
- no back-cover plane appears on the cover page

Release:

- focused tests and release build pass
- emulator captures pass the full matrix
- tablet validation is completed only if an escalation condition is met
- public release is created only after the applicable gates are green

## Non-Goals

- Page curl or page-turn animation redesign
- Whispersync/highlight behavior changes
- EPUB parsing changes
- Publisher font or typography redesign
- Runtime paper-generation controls
- Reintroduction of synthetic book-shell geometry
