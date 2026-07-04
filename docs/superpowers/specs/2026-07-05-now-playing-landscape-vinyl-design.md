# Now Playing Landscape Vinyl Design

## Goal

Redesign the expanded landscape Now Playing surface so tablet and wide-window layouts feel intentionally composed instead of like the portrait player stretched across the screen. The progress bar becomes the right-pane visual anchor, every related player block shares that center, the vinyl artwork gets a safer fit strategy for non-square covers, and LidaClips foreground video uses a framed media-slot contract so it cannot leave the vinyl area in a broken fallback state.

## Current Problems

The current landscape player is built as a 50/50 row:

- Left side: `NowPlayingMediaSlot` contains `NowPlayingArtworkPager`, and optionally overlays `NowPlayingLidaClipArtwork`.
- Right side: `NowPlayingControlsRow` owns title/artist, action buttons, transport controls, progress, duration labels, technical info, and Up Next.

That split is reasonable, but the right side does not have a single shared center. The title/artist row, transport buttons, technical chip, progress bar, and Up Next row each center inside their own local layout. In the overlay screenshot, the red arrows show the distance from each block center to the progress bar center; the different arrow lengths prove the controls are using multiple independent anchors.

The foreground clip path has a separate problem. `NowPlayingMediaSlot` always renders the artwork pager, then overlays `NowPlayingLidaClipArtwork` when a clip is promoted. The video is clipped to a shape, but it is not presented as a framed media variant with the same bounds, margins, and state-reset semantics as the vinyl artwork. If the clip is brought forward and the next song has no clip, stale or unresolved media state can expose the generated fallback art as a square panel rather than a vinyl record. The screenshot with `Mii Channel` shows that failure mode: the left slot is filled with generated cover art and no vinyl presentation, even though the player is in the wide vinyl layout.

## Design Principles

- One right-pane content coordinate system. The progress bar defines the playable-content center, and every player content block aligns to it.
- Window actions are not content anchors. Collapse, favorite, overflow, and bottom utility actions may live in safe-area corners, but they do not shift the title/control/progress center.
- The left media slot owns one visible foreground media variant at a time: vinyl artwork, framed foreground video, loading frame, or explicit fallback. The code should not leave video and artwork with competing layout contracts.
- Vinyl should remain vinyl even when real artwork is missing. Generated artwork may be drawn inside the disc or on a framed placeholder, but it must not silently replace the disc surface unless the user chooses a non-disc layout.
- The fisheye effect must be subtle and local to the disc image sampling. It should reduce harsh circular cutoff without making faces, text, or logos look obviously warped.
- All async work stays in ViewModels/repositories. Layout composables consume stable state and must not perform blocking artwork, clip, or label resolution.
- Do not add cancellation timeouts or fixed-delay correctness gates. State transitions should be driven by song id, clip id, render-ready callbacks, and player state.

## Landscape Layout Contract

### Breakpoint

Use the dedicated landscape layout only when the expanded Now Playing surface has enough horizontal room for the vinyl and right pane to breathe. The existing `maxWidth > maxHeight` check is a useful first gate, but implementation should add a minimum width guard so narrow foldable landscape windows keep the compact layout.

Suggested policy function:

- `nowPlayingUsesWideLandscapeLayout(width: Dp, height: Dp): Boolean`
- True when `width > height` and `width >= 900.dp`.

### Top-Level Regions

The wide layout has three conceptual regions:

- `mediaPane`: left visual anchor. Owns the vinyl/framed-video media slot.
- `contentPane`: right playback content. Owns title/artist, playback buttons, technical chip, progress, duration labels, and Up Next.
- `windowActions`: safe-area actions. Collapse/favorite/more remain near the true top-right corner; bottom utility buttons remain near the true bottom-right corner.

The current 50/50 row can remain as a starting point, but `contentPane` must expose one shared max width and center. The progress row should use that width, and all other content blocks should be placed inside the same parent.

### Right Pane Centering

Define a single right-pane content width:

- `progressWidth = contentPaneWidth.coerceIn(min = 560.dp, max = 760.dp)`, constrained by available width.
- `contentCenterX = contentPaneStart + progressWidth / 2` within the content pane.

Every content block uses that same width and center:

- Title/artist block: width `progressWidth`, centered text, max two title lines only if needed.
- Transport controls: width `progressWidth`, controls centered relative to the same parent.
- Technical chip: centered in the same parent.
- Progress row: width `progressWidth`.
- Duration labels: width `progressWidth`, left/current/right labels aligned to the progress bar.
- Up Next stack: width `min(progressWidth, 520.dp)` and centered under the progress row.

Do not center these blocks inside independently sized child rows. The red-arrow failure in the overlay exists because each child row picks a different local center.

### Up Next In Landscape

Landscape Up Next should stop using the current horizontal chip row. In the wide layout it becomes a compact vertical stack in the lower empty area of the content pane.

Rules:

- Show the label `Up next` above the cards.
- Render up to three items.
- Each item is a full-width row within the centered Up Next width.
- Use stable song ids as keys.
- Keep album art thumbnails square.
- Keep the queue action on tapping the section, but do not let the section width define the main player center.

Portrait and narrow landscape keep the existing horizontal `LazyRow`.

### Top-Right Actions

In wide layout, collapse, favorite, and overflow belong to the actual top-right safe-area corner, not inside the content block. They should:

- Use `WindowInsets.systemBars`/safe insets.
- Align to the top end of the sheet/window.
- Keep the existing tonal circular buttons.
- Stay independent from `contentPane` centering.

The content title should not move left just because action buttons exist on the same screen.

## Media Slot Contract

Introduce a media-slot state model for the left pane rather than layering unrelated surfaces directly:

```kotlin
sealed interface NowPlayingMediaSlotState {
	data class VinylArtwork(
		val songId: String,
		val artwork: PlaybackArtworkUiState,
		val generatedArtwork: GeneratedArtworkSpec,
		val isRotating: Boolean,
		val fitMode: NowPlayingDiscFitMode
	) : NowPlayingMediaSlotState

	data class ForegroundClip(
		val songId: String,
		val clip: DomainLidaClip,
		val frameStyle: NowPlayingClipFrameStyle
	) : NowPlayingMediaSlotState

	data class LoadingArtwork(val songId: String) : NowPlayingMediaSlotState
	data object Empty : NowPlayingMediaSlotState
}
```

This model is a design target, not a mandatory exact type name. The important requirement is that the slot decides one foreground mode per song. The composable should not show a stale clip surface over a pager that still thinks it owns the same area.

### State Reset Rules

When `song.id` changes:

- Clear `foregroundClipSongId` unless the new song has the same id.
- Clear foreground clip playback state keyed by old clip id.
- Render `VinylArtwork` immediately for the new song if artwork is enabled.
- Render a framed loading state only while the selected foreground mode is actually loading.
- Do not let a previous clip's rendered state suppress vinyl for the next song.

When clip lookup finishes with no clip:

- The media slot must be `VinylArtwork`.
- The music-video action may show unavailable/disabled state, but the left pane remains vinyl.

When clip playback errors:

- Recoverable clip errors clear foreground clip mode and return to `VinylArtwork`.
- Non-recoverable errors show an error frame only while the user is still explicitly in foreground clip mode.

### Foreground Clip Frame

Foreground clips should be boxed/framed, not raw video filling the vinyl bounds without UI context.

Frame rules:

- Use the same outer media-slot bounds as vinyl.
- Inside the media slot, draw a rounded rectangular video frame with a subtle border/scrim/shadow.
- Preserve video aspect ratio using the selected LidaClips foreground fit mode.
- Do not draw vinyl grooves over video.
- Show a small clip status/action affordance inside or near the frame so it is clear this is a promoted video surface.
- When leaving clip mode, dispose the video player and restore vinyl without layout jump.

The frame should make the clip feel intentional on top of the large left visual area. A raw borderless video over the disc looks accidental and makes state bugs harder to identify.

## Vinyl Artwork Strategy

### Fit Modes

Add a disc artwork fit policy that is separate from generic cover-art shape:

- `Crop`: current disc behavior; fill the circle and crop outside the bounds.
- `Fit`: fit the full cover inside the disc, accepting visible empty/rim area.
- `SoftEdgeCompress`: recommended default for wide vinyl; keep center mostly untouched and compress only the outer rim region.

This should be exposed later as a setting only if the visual experiment validates. The first implementation can ship `SoftEdgeCompress` as an internal wide-layout behavior with a fallback to `Crop` if the shader/canvas path is not ready.

### Soft Fisheye / Edge Compression

The requested fisheye should not be a strong lens warp. Use radial edge compression:

- Center 0-78% radius: sample nearly normally.
- Outer 78-100% radius: progressively compress source coordinates toward the center so edge-heavy artwork remains more visible inside the disc.
- Preserve the disc circle mask.
- Apply vinyl grooves, label/spindle, and gloss after the image transform.
- Keep text/logos/faces near center undistorted.

Suggested policy name:

- `NowPlayingDiscWarpPolicy`
- `NowPlayingDiscFitMode.SoftEdgeCompress`

If implemented with Compose Canvas, keep it deterministic and cheap. If implemented with shader/runtime effect, provide a non-shader fallback. Do not block player rendering while preparing the effect.

### Generated Artwork Inside Vinyl

For tracks without real cover art, generated artwork must still respect the selected media mode:

- Wide vinyl mode: generated artwork is rendered inside the disc frame, with vinyl overlay if rotating/disc mode is active.
- Non-disc cover mode: generated artwork renders as a normal generated cover card.
- Missing artwork should not cause a square generated cover to replace the disc in landscape if the current selected player style is vinyl.

This specifically covers the screenshot where `Mii Channel` rendered as a generated square panel instead of a vinyl record after foreground clip usage.

## Code Areas

Likely implementation files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt`
  - Replace the wide `Row` composition with named `mediaPane`, `contentPane`, and `windowActions` regions.
  - Drive `NowPlayingMediaSlot` from a resolved media-slot state.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/ControlsRow.kt`
  - Add a wide-layout controls variant or pass a layout profile so it uses one shared width.
  - Remove fixed-delay reveal as a correctness dependency; if entry animation remains, it must not affect media state.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/InfoRow.kt`
  - Split info content and top-right actions for wide layout. Actions should not be part of the centered title row.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/UpNextRow.kt`
  - Add a vertical wide-layout variant.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt`
  - Add the disc fit mode and soft edge compression hook.
  - Ensure generated artwork is wrapped by vinyl mode when applicable.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/LidaClipVideo.kt`
  - Render promoted clips inside a framed media-slot variant.
  - Ensure disposal/recovery returns to vinyl state.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingControlsLayoutPolicy.kt`
  - Add shared right-pane width/center policy functions.
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingArtworkSizePolicy.kt` or a new focused policy file
  - Add vinyl fit/warp policy if the existing file stays small.
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/*`
  - Add policy tests for centering math, Up Next variant selection, media-slot state transitions, and disc fit behavior.

## Failure Explanation For The Provided Screenshot

The image is best explained as a state ownership bug, not simply an unresolved cover.

Observed:

- The player is in landscape wide mode.
- The left pane shows generated artwork with a music-note fallback and no vinyl rendering.
- This happened after foreground trailer/clip mode had been enabled.
- The next song did not have trailer support.

Likely chain:

1. The user promotes a LidaClips foreground video.
2. `foregroundClipSongId` marks the current song as clip-in-artwork.
3. `NowPlayingMediaSlot` overlays `NowPlayingLidaClipArtwork` on top of `NowPlayingArtworkPager`.
4. On song change or clip miss, the foreground clip path clears late or incompletely.
5. The artwork pager resolves the new song, but the left slot no longer has a single explicit media-slot state saying "vinyl owns this area".
6. If real artwork is not immediately available, generated artwork renders through the generic cover path and appears as a square/card-style fallback instead of the disc/vinyl path.

The durable fix is to make the media slot state explicit and keyed by song id. Clip promotion should be a media-slot mode, and clip absence/error/song-change should synchronously restore `VinylArtwork` mode for the new song. The generated artwork renderer then feeds the vinyl renderer instead of bypassing it.

## Tests And Validation

Unit/source tests:

- `NowPlayingControlsLayoutPolicyTest`
  - Wide layout returns a single content width and center.
  - Header, buttons, technical chip, progress, durations, and Up Next receive the same center anchor.
  - Narrow landscape does not use the wide layout.
- `NowPlayingUpNextPolicyTest`
  - Wide layout selects vertical stack.
  - Portrait/narrow layout keeps horizontal row.
- `NowPlayingMediaSlotPolicyTest`
  - Song change clears foreground clip mode unless song id is unchanged.
  - Clip miss returns `VinylArtwork`.
  - Recoverable clip error returns `VinylArtwork`.
  - Missing real artwork still returns a vinyl-capable generated state in wide disc mode.
- `NowPlayingDiscFitPolicyTest`
  - `SoftEdgeCompress` leaves center sampling stable and only affects outer radius.
  - Fallback to `Crop` remains valid when the effect path is unavailable.

Device validation:

- Galaxy Tab S9 Ultra landscape expanded player with real square album art.
- Galaxy Tab S9 Ultra landscape expanded player with non-square/edge-heavy album art.
- Generated artwork track with no real cover should still show vinyl mode if vinyl is selected.
- Foreground LidaClip promoted, then next song with no clip: left pane returns to vinyl, not raw generated square cover.
- Foreground LidaClip promoted in landscape: video is visibly framed, not raw edge-to-edge over the disc.
- Compare screenshot centers: title, transport controls, codec chip, and Up Next stack should share the progress bar center.

## Non-Goals

- Do not redesign portrait Now Playing.
- Do not replace the existing LidaClips lookup/cache pipeline.
- Do not change audio focus, clip audio, or queue behavior.
- Do not introduce cancellation timeouts or fixed-delay recovery.
- Do not change Aurral/Navidrome artwork priority rules except where the media-slot state decides how a resolved or generated artwork is presented.
