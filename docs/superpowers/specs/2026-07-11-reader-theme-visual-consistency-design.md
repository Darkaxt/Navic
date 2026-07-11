# Reader Theme Visual Consistency Design

**Status:** Approved design, ready for implementation planning

**Date:** 2026-07-11

**Baseline:** `v1.0.11-theta89` (`6f8d6c80`)

## Objective

Make the reader's native and WebView decorations use one visual theme contract:

- Landscape and portrait back-cover reveals retain the same cover-derived hue.
- The Whispersync headset control uses the active reader theme's normal text color.
- Neither change mutates Foliate pagination, page bounds, reader shell geometry, or the accepted thin reveal dimensions.

## Problem Statement

### Back-cover hue diverges by orientation

`readerSurfaceBackCoverBackground()` currently resolves portrait and landscape through different color pipelines.

- Portrait starts from `readerReadableCoverTintChannels(coverTint)` and preserves the sampled cover hue.
- Landscape starts from `readerDesaturatedColorChannels(coverTint, 0.28)` and then mixes heavily toward the reader foreground.

For the Alcatraz cover, portrait therefore exposes a visible green/blue tint while landscape becomes mostly neutral grey. Orientation should only change the reveal geometry, not the source color identity.

### Whispersync control ignores the reader palette

`KomikkuWhispersyncPlaybackControl()` currently uses Material `onSurface` at `0.42` alpha. The icon is a native Compose overlay above the WebView, so it does not inherit the EPUB foreground color. On Sepia and Aged Paper this produces a pale icon with poor contrast.

## Design

### Shared back-cover color source

Introduce one pure back-cover palette resolver in `navic-reader-helpers.js`.

Inputs:

- sampled `coverTint`
- active reader theme palette
- warm-paper treatment flag

Outputs:

- readable base tint
- highlight tint
- middle tint
- outer/edge tint

Both portrait and landscape call this resolver. The orientation branches remain responsible only for gradient placement:

- portrait: one right-side reveal
- landscape spread: symmetric left and right reveals

The landscape resolver may darken its outer edge slightly more than portrait, but it must preserve the base hue. It must not call a separate desaturation-first path.

### Reader foreground contract for native controls

Add a pure Kotlin `readerThemeForegroundColor(theme)` resolver mirroring the reader WebView foreground palette:

| Theme | Foreground |
|---|---|
| Light | `#1d1b18` |
| Sepia | `#2b2118` |
| Aged Paper | `#261b10` |
| Dusk | `#ece7f6` |
| Dark | `#f2f0ea` |
| Black | `#f3f3f3` |

`ReaderRoot` passes the active reader theme into `KomikkuWhispersyncPlaybackControl`. The headset glyph and disabled slash use the resolved foreground at `0.86` alpha.

The control retains:

- 48 dp touch target
- 22 dp glyph
- no pill, card, or chrome background
- existing tap and long-press behavior
- existing visibility rules

## Boundaries

This change must not:

- alter back-cover reveal width or page dimensions
- add back-cover decoration to actual cover pages
- change paper texture, stain, edge, or gutter opacity
- alter Whispersync enable/disable behavior
- add a new reader setting
- use Material app colors as a fallback for a recognized reader theme

Unknown reader themes normalize to the existing Light reader theme contract.

## Testing

### JavaScript source and behavior tests

- A dark teal cover produces hue-visible portrait and landscape gradients.
- Portrait and landscape gradients share the same readable base tint.
- Landscape output is symmetric.
- Missing cover tint falls back to the active reader palette.
- Back-cover visibility and reveal geometry remain unchanged.

### Kotlin tests

- Each supported reader theme resolves to the exact foreground value above.
- Unknown/null themes resolve to Light.
- The Whispersync control consumes the reader foreground resolver.
- The old `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)` path is absent.
- Glyph and slash use the same color.

## Visual Acceptance

Use the readerdev emulator with Alcatraz:

1. Sepia, portrait: the thin right reveal visibly inherits the cover's green/blue family.
2. Sepia, landscape spread: both outer reveals use that same hue instead of grey.
3. Sepia, Whispersync enabled: headset reads like normal Sepia text.
4. Sepia, Whispersync disabled: headset and slash remain legible without a background pill.
5. Rotate portrait to landscape and back: no stale orientation color remains.

Capture screenshots in both orientations. Verify true wide emulator bounds before accepting the landscape result.

## Delivery

Implement on a clean branch from current `fork/master`, using test-first commits. Merge only after focused tests, reader host tests, JavaScript syntax checks, emulator screenshots, and a release build succeed.
