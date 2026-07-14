# PlayLikeCurl OpenGL Reader Page-Turn Design

**Status:** Rev 2 approved direction; renderer implementation reset for fidelity

**Date:** 2026-07-14

**Baseline:** `master` at `4b8b213a`

**References:**

- [PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl), an archived MIT-licensed Android/OpenGL reference
- [Stack Overflow discussion describing the Google Play Books-style sine mesh](https://stackoverflow.com/questions/35586822/flip-page-animation-as-in-google-play-books)
- `docs/superpowers/specs/2026-07-12-reader-destination-aware-page-turn-design.md`

**Supersedes:** The Canvas renderer, wave geometry, and capture-cache implementation portions of:

- `docs/superpowers/specs/2026-07-12-reader-destination-aware-page-turn-design.md`
- `docs/superpowers/plans/2026-07-13-reader-snapshot-slide.md`

The page-identity, passive-renderer, exact-relocation, release-only commit, and exact-settlement contracts from the destination-aware design remain normative.

## 1. Objective

Replace Navic's current page-turn implementation with a faithful modern port of PlayLikeCurl. "Modern" applies only to Android and OpenGL APIs. It does not permit redesigning the page model, geometry, projection, draw order, gesture mapping, direction semantics, settlement animation, or page-identity lifecycle.

The renderer currently present on `master` is a failed prototype, not a partial fidelity baseline. It copied selected equations into a custom two-texture renderer, custom projection, and custom landscape projection. Those choices are not accepted as PlayLikeCurl parity and must not constrain the direct port.

The reader must:

- deform only the active physical page or leaf
- use separate forward and backward geometry instead of reversing one animation
- display the correct current, reverse, underneath, and final page pixels
- avoid visible WebView capture, blank frames, bitmap swaps, and post-animation flashes
- prepare page rasters asynchronously and persist them in a bounded cache
- expose page-animation bitmap quality as a setting so performance and sharpness can be tuned without code changes
- keep Foliate as the sole navigation, pagination, progress, annotation, selection, and Whispersync authority
- show a cover-backed preparation screen with real determinate progress when required rasters are not ready

The design deliberately accepts a loading stage over showing incorrect pixels or falling through to a native tap transition.

## 2. Non-Negotiable Invariants

1. Foliate owns live layout and final navigation.
2. The live Foliate surface does not scroll, resize, or relocate during drag.
3. Commit is decided only on release.
4. Cancel never navigates.
5. Every committed gesture issues one exact target relocation.
6. Portrait advances one visual page ordinal. Landscape advances one active leaf while preserving the companion leaf.
7. Forward and backward turns use direction-specific geometry.
8. The OpenGL overlay is attached only after every texture required for that transition is valid.
9. The overlay stays opaque until animation completion and exact Foliate target settlement have both occurred.
10. The overlay is removed only after the settled Foliate target has produced a composited frame.
11. Page capture and raster generation never occur visibly in the interactive WebView.
12. Missing or stale textures produce a preparation state, not a blank surface, native tap fallback, or guessed page.
13. No timeout cancels capture, cache generation, navigation, animation, or settlement. Generation tokens and lifecycle events invalidate obsolete work.
14. No OpenGL or passive-renderer path emits progress, history, selection, annotation, or Whispersync events.
15. No renderer path recreates the rejected synthetic reader shell or changes Foliate's text geometry.
16. Rendering does not allocate bitmaps, vertex arrays, index arrays, or direct buffers per frame.
17. The first fidelity gate excludes shadow and speculative geometry improvements. Reference parity comes first.
18. The first accepted ReaderDev implementation uses the original PlayLikeCurl portrait and landscape demo textures, page roles, progress values, draw order, and gestures before any Foliate bitmap is connected.
19. Every implementation tranche ends with a recorded comparison against the actual PlayLikeCurl reference animation. Any unexplained difference blocks the next tranche.
20. Landscape leaf bounds may be added only after portrait and reference-demo parity. Bounds may clip or place an otherwise unchanged page model; they may not rewrite its deformation.

### 2.1 Failed-prototype disposition

The following current implementation choices are explicitly non-normative and must be removed or bypassed by the faithful port:

- the custom two-texture source/destination renderer
- the custom z-dependent perspective approximation
- the custom full-viewport/active-leaf projection that changes mesh coordinates
- diagnostic checker textures used as evidence of reference parity
- tests that prove only sampled equations while omitting the three-page lifecycle
- the previous Gate A conclusion that geometry parity had passed

Raster caching, asynchronous capture, bitmap-quality settings, exact Foliate relocation, and settlement shielding are integration infrastructure. They may be retained only behind a narrow adapter after standalone reference parity is proven.

## 3. Reference Geometry Contract

### 3.1 Internal modern port

Navic will maintain an internal, first-party port of PlayLikeCurl rather than depend on the archived Android library at runtime. The source page model and interaction lifecycle are the behavioral specification, not suggestions.

The port must preserve the MIT attribution and license notice in Navic's third-party records. Obsolete Android activity/view plumbing and fixed-function OpenGL calls are modernized, but their observable behavior is preserved. In particular:

- `PageFront`, `PageLeft`, and `PageRight` remain distinct persistent page objects
- the original texture-slot rotation and previous/current/next identity lifecycle remain intact
- the original draw order, stationary depths, 45-degree perspective, aspect correction, endpoint values, and 300 ms settlement behavior remain intact
- GL1 matrix, vertex-array, and texture calls are translated to GLES2 shaders and buffers without changing geometry or interaction
- ReaderDev replaces the sample Activity and asset loader, but not the sample's gesture or page lifecycle

### 3.2 Fidelity constants

The initial implementation locks these reference values:

```text
GRID = 25
vertices per axis = GRID + 1 = 26
RADIUS = 0.18
```

The mesh therefore contains 26 x 26 vertices. The implementation may use indexed triangles instead of the original draw organization, but sampled vertex positions and texture coordinates must match the reference formulas within floating-point tolerance.

### 3.3 Three page roles and texture lifecycle

The port retains three geometry roles:

- `PageFront`: the active forward-turning page
- `PageLeft`: the active backward-turning page
- `PageRight`: the stationary page or underneath page

`PageLeft` is not `PageFront` played in reverse. It has its own vertex mapping and progress domain.

The standalone parity renderer owns exactly three texture slots:

```text
previous -> PageLeft
current  -> PageFront
next     -> PageRight
```

At a publication boundary, the missing adjacent slot duplicates the current texture exactly as the reference does. A committed transition rotates these identities only after the 300 ms settlement animation completes. A cancelled transition restores the active page position without rotating identities.

The draw order is normative:

```text
PageLeft, then PageFront, then PageRight
```

The stationary depth values are normative: `PageLeft=-0.001`, `PageFront=-0.002`, and `PageRight=-0.003`.

### 3.4 Progress domains

The initial fidelity contract preserves the reference endpoints:

```text
forward start:  GRID = 25.0
forward commit: PAGE_RGHT = -GRID * 0.05 = -1.25

backward start: PAGE_RGHT = -1.25
backward commit: PAGE_LEFT = GRID = 25.0
```

Gesture distance maps monotonically into the matching domain. Release animation continues from the current value to the selected endpoint without pausing at the binding or waiting for Foliate.

### 3.5 Scope of first parity gate

The first geometry gate includes:

- the sine deformation
- active-page horizontal compression and translation
- correct texture-coordinate mapping
- separate forward and backward roles
- correct stationary-page composition

It excludes:

- page shadow
- specular highlights
- edge thickness
- perspective embellishment
- adaptive mesh density

Those may be designed after parity and performance are proven.

### 3.6 Reference-demo parity gate

Before Foliate bitmaps are accepted, ReaderDev must reproduce the archived demo with its original portrait and landscape page images. The gate records synchronized reference and ReaderDev frames at the same input positions and transition progress values.

Parity includes:

- page placement and aspect correction
- visible deformation and texture orientation
- previous/current/next composition
- forward and backward direction semantics
- drag response and release behavior
- settlement duration and interpolation
- page identity rotation after commit
- cancellation without identity rotation

The comparison is visual and behavioral. Numeric vertex samples alone cannot pass this gate.

## 4. Renderer Architecture

### 4.1 Persistent OpenGL surface

Navic adds one persistent `GLSurfaceView` hosted by the reader frame. It uses OpenGL ES 2.0 programmable shaders and `RENDERMODE_WHEN_DIRTY`.

The surface is created with the reader host, not for every gesture. During normal reading it is transparent and non-interactive. During a prepared transition it becomes the opaque visual authority above Foliate.

### 4.2 Renderer responsibilities

The standalone parity renderer receives the original page position, active page role, viewport ratio, and three texture identities. It must not receive Navic-specific leaf projection or source/destination abstractions until the reference-demo gate passes.

After parity, the Foliate adapter may translate a prepared transition into immutable state:

```kotlin
data class ReaderPageCurlFrame(
    val direction: ReaderPageTurnDirection,
    val progress: Float,
    val viewport: ReaderPageViewport,
    val activeLeafBounds: ReaderPageBounds,
    val companionLeafBounds: ReaderPageBounds?,
    val textures: ReaderPageTurnTextureSet,
)
```

It may:

- upload prepared textures
- update a progress uniform or preallocated position buffer
- draw the stationary companion, underneath page, and active mesh
- request another frame while release animation is active

It may not:

- navigate Foliate
- capture a WebView
- resolve page identities
- read preferences or network state
- mutate page-turn state-machine ownership

### 4.3 Buffer and texture lifetime

Vertex, texture-coordinate, and index buffers are allocated once per renderer configuration. Geometry state is updated in place. Texture objects are reused when dimensions and format are compatible.

Texture upload occurs when a prepared bundle changes, not during every frame. GPU texture residency is bounded to the current transition plus a small adjacent-page window.

### 4.4 Context lifecycle

Context loss and recreation are event-driven:

- renderer state remains represented in Kotlin
- GPU handles are discarded when the surface is recreated
- valid cached bitmaps are reuploaded on the next surface event
- no timer guesses whether context recovery succeeded
- no context-loss event promotes a stale texture to ready

If OpenGL initialization fails, Navic keeps the stable Foliate page visible and reports the error. It does not silently re-enable the rejected Canvas implementation.

### 4.5 Portrait composition

Portrait treats the viewport as one active physical page:

- current page is the active/front texture
- target page is the underneath/final texture
- backward movement uses `PageLeft`
- forward movement uses `PageFront`

The renderer does not reuse landscape spread offsets.

### 4.6 Landscape composition

Landscape resolves the real Foliate gutter and active leaf bounds:

- only the active leaf deforms
- the companion leaf stays static
- the underneath texture is clipped to the active leaf
- the full final spread may remain as the settlement shield
- geometry origin and texture coordinates are relative to the active leaf, not the full two-page viewport

This prevents the previous failure where both columns moved as one page.

Landscape composition is an integration stage after standalone parity. It must place or scissor the unchanged reference page model inside the resolved leaf bounds. `ReaderPageCurlLeafProjection`-style coordinate rewriting is forbidden because it changes the deformation being ported.

## 5. Page Identity And Texture Bundle

### 5.1 Preserved source of truth

The existing visual-page index, page-side resolver, and passive Foliate renderer remain the source of page identity and pixels.

The transition bundle retains these roles:

```text
currentBase
turningFront
turningReverse
underneath
finalBase
```

The OpenGL renderer consumes these roles; it does not infer adjacent pages from texture order.

### 5.2 Reverse-face behavior

When a distinct reverse capture is available, it is used according to the destination-aware bundle contract. The first PlayLikeCurl parity renderer may use the reference's single-face visual behavior while the reverse is staged, but it must never mirror front text as if it were valid reverse content.

Invalid reverse content is represented as neutral paper until the correct reverse texture is ready. A mirrored front-page texture is forbidden.

### 5.3 Exact settlement

On committed release:

1. OpenGL animation continues uninterrupted to its final progress.
2. The controller dispatches one exact Foliate relocation for the target visual page.
3. The final opaque texture remains above Foliate.
4. Foliate reports the expected target token.
5. The host observes one composited target frame.
6. The OpenGL overlay detaches.

Animation completion and Foliate settlement are independent gates. Neither waits synchronously on the other.

## 6. Asynchronous Page Raster Cache

### 6.1 Purpose

The cache removes visible capture, avoids rebuilding the same chapter pages after every open, and ensures the next transition is immediately available when preparation has completed.

It stores animation rasters, not publication resources. Foliate remains responsible for the EPUB itself.

### 6.2 Storage location

The cache lives under Navic's managed reader cache, for example:

```text
reader-page-rasters/v1/{publication-key}/{pagination-key}/{page-key}.png
```

It integrates with `ReaderManagedStorage` and the existing Clear Bindery Cache operation. It does not create a separate unmanaged directory and does not use symlinks.

### 6.3 Cache key

Every raster key includes:

- publication fingerprint
- pagination profile fingerprint
- spine index and canonical href
- chapter page index and visual page ordinal
- viewport width and height
- orientation and spread mode
- resolved active-page bounds
- theme, font, line-height, margin, and column settings that affect pixels
- paper, edge, stain, and cover-decoration settings that are baked into the capture
- bitmap quality scale
- raster format/schema version

Changing any pixel-affecting input produces a new key. Stale entries may remain until LRU cleanup, but are never returned for a mismatched key.

### 6.4 Bitmap quality setting

The hard-coded `ReaderPageTurnAnimationBitmapScale = 0.5f` is replaced by a persisted setting:

**Label:** Page animation bitmap quality

**Options:**

- `25%` - lowest memory and storage use
- `50%` - balanced, default
- `75%` - sharper on large tablets
- `100%` - native capture resolution

The selected value controls capture dimensions and cache identity. It does not control cache capacity.

Changing quality:

1. updates the preference immediately
2. increments the raster-generation profile token
3. invalidates in-memory and GPU rasters for the old profile
4. leaves the stable Foliate page visible
5. regenerates required pages asynchronously
6. removes old-profile disk files through managed cache cleanup

The setting appears in global Ebooks settings, reader settings, and settings search. It uses the existing reader-default and per-book override serialization so the behavior is consistent with other reader display settings.

### 6.5 File format and writes

Initial storage uses lossless PNG page files. Page files are independently addressable; a corrupt page does not invalidate an entire chapter atlas.

Manifest and raster writes are atomic:

- write to a same-directory temporary file
- flush and close
- atomically replace the final path
- update the manifest only after the raster is durable

Temporary files have deterministic names and are removed during recovery scanning.

### 6.6 Cache tiers

The cache has three bounded tiers:

1. GPU textures for the current transition and immediate neighbors
2. decoded bitmap memory LRU for the current page, both adjacent pages, and settlement shield
3. disk LRU for persisted chapter/page rasters

Disk capacity is an internal bounded policy during this migration. It is intentionally separate from bitmap quality. A later storage setting may expose capacity after real measurements exist.

### 6.7 Single-flight generation

Only one generation job may own a raster key at a time. Multiple consumers await the same result.

Jobs carry publication, pagination, and quality profile tokens. A token change makes the result obsolete and prevents publication to the cache. It does not rely on a cancellation timeout.

### 6.8 Preparation order

Generation priority is:

1. current page or spread
2. next transition bundle
3. previous transition bundle
4. remaining pages in the current chapter
5. next chapter
6. previous chapter

All successfully generated pages are persisted. The scheduler may precompute the complete current chapter when measured cost is acceptable.

### 6.9 Calibration policy

For the first uncached chapter, Navic measures the first three representative raster operations:

- passive page stage and capture
- scale and encode
- disk write
- disk read and decode
- GPU upload

The scheduler uses measured throughput and estimated chapter size to choose:

- eager complete-chapter preparation, or
- rolling-window preparation with current/next/previous priority

The decision changes scheduling only. It never cancels a valid operation after an arbitrary duration.

## 7. Preparation User Experience

### 7.1 Initial preparation

If required current and adjacent rasters are missing, the reader shows:

- the book cover, uncropped
- the existing blurred/diffused cover backdrop
- a determinate progress bar
- concise preparation status

The progress bar reports completed required raster work over the selected preparation target. It does not use simulated progress.

### 7.2 Readiness levels

```text
interactive-ready:
  current page plus forward and backward transition requirements are valid

chapter-ready:
  every page selected by the current chapter policy is persisted
```

The reader opens when `interactive-ready` is reached. Remaining chapter preparation continues asynchronously.

### 7.3 Cache hits

When required rasters are valid cache hits, the preparation screen is skipped. Disk decode and GPU upload happen before the page-turn control reports ready.

### 7.4 User outruns preparation

If the requested adjacent page is not ready:

- keep the current Foliate page visible and interactive for non-turn actions
- show a bounded preparation indicator
- do not accept the page-turn gesture as a tap
- do not start an incomplete animation
- begin or reprioritize the missing single-flight job

Once ready, a new gesture can start immediately.

## 8. Gesture And State-Machine Contract

### 8.1 Ownership

The native frame host owns touch-slop classification. A gesture promoted to page turn cancels the WebView gesture once and remains owned by the page-turn controller through release or cancel.

### 8.2 Drag

During drag:

- progress follows the finger through the full geometry domain
- the animation is not artificially clamped at the midpoint
- Foliate remains stationary
- tap navigation is suppressed for the owned gesture

### 8.3 Release

Release chooses commit or cancel from distance and velocity. The release animator starts from the exact current progress and continues to the selected endpoint.

The controller does not become tap-ready until the gesture is fully cleared. Prepared subsequent gestures may start while the previous Foliate target is settling if the slide coordinator can represent them safely; otherwise input is visibly gated, never reinterpreted as a tap.

### 8.4 Rapid turns

The rolling visual position may advance ahead of settled Foliate position. Exact target requests remain serialized and coalesced by the existing coordinator. The final shield always corresponds to the latest accepted visual target.

## 9. Diagnostics And Failure Handling

Diagnostics are opt-in and structured. They record:

- publication and pagination profile hashes
- bitmap quality
- raster key and cache tier hit/miss
- capture, encode, decode, and upload durations
- generation token changes and stale-result rejection
- transition direction, page identities, progress endpoints, and commit result
- OpenGL context creation/recreation
- exact target dispatch, settlement token, composited-frame confirmation, and overlay detach

Normal scrolling and drawing do not log per-frame values or full throwables.

Failure behavior:

- corrupt disk raster: delete that entry and regenerate
- passive capture failure: retain stable page and expose retryable preparation error
- OpenGL shader/program failure: retain stable page and report renderer unavailable
- context recreation: reupload valid cached textures
- stale generation: discard result without replacing current cache entry

## 10. Settings And Migration

The user-facing page-turn mode remains the existing `Canvas` selection during the internal migration to avoid a preference churn before acceptance. After the OpenGL renderer replaces Canvas, labels are updated atomically to describe the accepted behavior, for example `Page turn` or `Play-style page turn`.

Legacy preference behavior:

- `none` remains `none`
- `canvas` selects the new OpenGL renderer after cutover
- historical `curl` continues to normalize to the animated mode
- historical `standard` continues to normalize to `none`

Bitmap quality defaults to `50%` when absent or invalid. Existing books therefore retain the current half-resolution intent.

## 11. Source Boundaries

### 11.1 New production files

- `ReaderPageCurlGeometry.android.kt` - faithful pure geometry and mesh generation
- `ReaderPageCurlGlView.android.kt` - persistent Android view and surface lifecycle
- `ReaderPageCurlGlRenderer.android.kt` - shaders, buffers, textures, and frame drawing
- `ReaderPageRasterCache.android.kt` - memory/disk raster storage
- `ReaderPageRasterManifest.android.kt` - versioned atomic manifest
- `ReaderPageRasterScheduler.android.kt` - priority, single-flight, calibration, and generation tokens
- `ReaderPageRasterPolicy.kt` - common quality and scheduling policy
- `ReaderPagePreparationPolicy.kt` - common readiness and determinate-progress policy
- `ReaderPagePreparationOverlay.kt` - cover-backed determinate preparation UI

### 11.2 Existing production files modified

- `ReaderPageTurnController.android.kt`
- `ReaderPageTurnBundle.android.kt`
- `ReaderPageTurnBundleSource.android.kt`
- `ReaderPageTurnBitmapSource.android.kt`
- `KomikkuReaderNativeFrameHost.android.kt`
- `ReaderPageTurnStateMachine.kt`
- `ReaderPageSlideCoordinator.kt`
- `navic-reader-page-turn-model.js`
- `navic-reader-page-turn-preview.js`
- `navic-reader-page-turns.js`
- reader preference, settings UI, settings search, storage, and strings files

### 11.3 Production files removed after cutover

- `ReaderPageTurnSlideView.android.kt`
- `ReaderPageTurnWaveGeometry.android.kt`

Their tests are replaced by OpenGL geometry and lifecycle tests. They are not retained as silent fallbacks.

## 12. Validation Strategy

Expensive visual validation happens only at meaningful feature-complete gates.

### Gate A: geometry renderer

Run ReaderDev after:

- reference geometry tests pass
- GLES2 renderer draws dynamic Navic textures
- forward and backward modes work in portrait and landscape active-leaf bounds

Acceptance:

- shape follows PlayLikeCurl reference frames
- backward motion is not a reversed forward animation
- landscape deforms one leaf
- no mirrored-front text is presented as reverse content

### Gate B: live cache integration

Run ReaderDev after:

- raster cache and scheduler are active
- preparation UI is determinate
- controller uses OpenGL bundle through exact settlement

Acceptance:

- no visible capture
- no blank or transparent page
- no post-animation WebView blink
- correct current, underneath, and final page text
- consecutive page turns do not become taps
- cached reopen skips preparation when keys match

### Gate C: final release acceptance

Run the complete ReaderDev matrix and physical tablet gate:

- portrait forward/backward
- landscape left/right active leaf
- chapter and spine boundaries
- cover and first content page
- rapid consecutive turns
- rotation and window resize
- cache hit, cache miss, quality change, and cache clear
- app process recreation and OpenGL context recreation

Only after Gate C passes may the release version be incremented and published.

## 13. Acceptance Criteria

The migration is complete only when:

1. The current Canvas/wave production renderer is unreachable and removed.
2. Sampled OpenGL geometry matches PlayLikeCurl's reference formulas.
3. Forward and backward transitions have correct direction-specific geometry.
4. Portrait and landscape use correct page/leaf bounds.
5. Every visible texture corresponds to the planned page role.
6. No page capture or texture replacement is visible.
7. No animation pauses at half-page waiting for Foliate.
8. No completed animation flashes or performs a second native page turn.
9. Bitmap quality is configurable at 25%, 50%, 75%, and 100%, default 50%.
10. Quality changes invalidate only page-animation rasters and regenerate asynchronously.
11. Raster writes are atomic, cache capacity is bounded, and stale jobs cannot publish.
12. Preparation progress is real and determinate.
13. Cache clearing removes page-animation rasters through managed reader storage.
14. Third-party attribution and license verification pass.
15. ReaderDev emulator and tablet acceptance gates pass before public release.

## 14. Deferred Work

These are intentionally outside the initial migration:

- page shadow matching Google Play Books
- edge thickness and specular lighting
- user-configurable disk cache capacity
- adaptive mesh density
- non-EPUB publication format expansion
- replacing Foliate as pagination or navigation authority

They must not block a faithful, stable, non-blinking PlayLikeCurl migration.
