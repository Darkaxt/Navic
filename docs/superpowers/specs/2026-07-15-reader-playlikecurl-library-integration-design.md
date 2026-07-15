# PlayLikeCurl Library Integration Design

**Status:** Proposed integration contract after source audit

**Date:** 2026-07-15

**Navic audit baseline:** `e582400ff4e81fffe5c8980fbf2d8e4a75cdb78a`

**PlayLikeCurl audit baseline:** `c0d5a3645fc6d5062b18e7866df2f04a5e7621f1`

**Canonical engine:** [Darkaxt/PlayLikeCurl](https://github.com/Darkaxt/PlayLikeCurl)

**Related specifications:**

- `docs/superpowers/specs/2026-07-14-reader-playlikecurl-opengl-design.md`
- `docs/superpowers/specs/2026-07-12-reader-destination-aware-page-turn-design.md`
- `docs/superpowers/specs/2026-07-04-reader-page-turn-animation-design.md`

**Supersedes:**

- Section 3.1, "Internal modern port", of the 2026-07-14 PlayLikeCurl design.
- Any implementation plan that copies PlayLikeCurl geometry, model, renderer, or gesture code into Navic-owned classes.
- The failed Canvas renderer and the current Navic-specific PlayLikeCurl geometry/renderer prototypes.

The page identity, Foliate authority, release-only commit, exact relocation, raster cache, and settlement-shield contracts from the earlier specifications remain normative unless this document explicitly replaces them.

## 1. Objective

Integrate `Darkaxt/PlayLikeCurl` as Navic's single page-deformation engine without allowing a second Navic-owned implementation to drift from it.

The integration must:

- preserve the fork's reference portrait geometry, interaction, draw order, direction-specific page roles, and settlement behavior
- preserve the fork's four-leaf landscape adapter without allowing a leaf to cross the center binding
- feed Navic's asynchronously prepared page rasters into the engine through a production adapter
- keep Foliate as the sole authority for pagination, layout, navigation, progress, selection, annotations, and Whispersync
- prevent decoding, file I/O, database work, or unbounded allocation on the UI or GL render threads
- avoid blank frames, black surfaces, transparent pages, stale text, mirrored-front-text errors, and post-turn WebView flashes
- remain deterministic across rotation, resizing, app backgrounding, GL context loss, and rapid repeated gestures
- make the page bitmap quality configurable through the existing 25/50/75/100 percent setting
- retain a clear rollback path until the integrated renderer has passed ReaderDev, emulator, and device validation

The integration is not allowed to reinterpret "faithful" as "similar". The fork owns deformation. Navic owns page preparation and reader settlement.

## 2. Audit Findings

### 2.1 What the fork already proves

At the audited commit, the fork is six commits ahead of the archived upstream project and contains:

- a modern Android/Gradle toolchain
- a GLES2 renderer replacing fixed-function GL1 calls
- the original three-page portrait model and 300 ms settlement
- a four-leaf landscape proof of concept
- midpoint commit behavior for slow releases
- a cast-shadow pass that follows the fold edge
- unit/source guards for model roles, geometry, endpoints, texture order, and the GLES2 boundary

The fork's own verification command succeeds:

```powershell
.\gradlew.bat :karackencurllib:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

This proves that the standalone proof of concept builds and its current tests pass. It does not prove that the library is ready for production Navic bitmaps or lifecycle behavior.

### 2.2 Fork gaps that block direct production use

The current public library API is still sample-oriented:

1. `PageCurlAdapter` accepts `String[]` asset paths instead of client-owned page images.
2. `PageRenderer` decodes assets with `context.getAssets().open(...)` from the render path.
3. Texture objects are stored in an unbounded `ConcurrentHashMap`.
4. There is no complete public dispose/release contract for GL textures and retained bitmaps.
5. Decode and shader failures throw from the renderer and can become a black surface or process crash.
6. Rendering is continuous even when no gesture or settlement is active.
7. Touches received during settlement are discarded without an explicit readiness or retry contract.
8. `performClick()` is called on release even after a drag, allowing gesture/tap ambiguity.
9. Replacing the adapter or landscape mode can reset the model to position zero.
10. Context recreation resets GL handles but has no client page rehydration contract.
11. There is no stable Maven publication, release API version, or immutable consumer lock.
12. The public contract exposes asset identity, not logical page identity and generation identity.

These gaps must be fixed in the fork before Navic consumes it. Navic must not work around them by copying the renderer again.

### 2.3 Navic assets worth retaining

Navic already has useful integration infrastructure:

- `ReaderPageRasterCache`
- `ReaderPageRasterManifest`
- `ReaderPageRasterScheduler`
- `ReaderPageRasterBatchController`
- `ReaderPageRasterDescriptor`
- `ReaderPageTurnBundleSource`
- managed `reader-page-rasters` storage
- generation-aware page capture and target identity
- configurable `ReaderPageBitmapQuality` values at 25/50/75/100 percent
- ReaderDev sample assets and an isolated reference activity
- third-party attribution and MIT license records
- exact Foliate relocation and post-navigation composition signals

These components remain Navic-owned. They prepare and identify page images; they do not deform them.

### 2.4 Navic code that must not become production architecture

Navic currently contains copied or parallel PlayLikeCurl implementations such as:

- `ReaderPlayLikeCurlReferenceModel.android.kt`
- `ReaderPlayLikeCurlReferenceGeometry.android.kt`
- `ReaderPlayLikeCurlReferenceRenderer.android.kt`
- `ReaderPlayLikeCurlReferenceView.android.kt`
- the uncommitted `ReaderPlayLikeCurlProductionAdapter.android.kt`
- the uncommitted `ReaderPlayLikeCurlProductionView.android.kt`

They are useful only as forensic evidence and migration references. They cannot remain an alternative production renderer after the fork is integrated.

Production source guards must fail if PlayLikeCurl deformation equations, page-role state machines, shader programs, or gesture settlement logic are reintroduced outside the imported library.

### 2.5 Current production state

The audited Navic master intentionally safety-disables the failed animation path:

- `ReaderRoot` passes `pageTurnCanvasEnabled = false`.
- `ReaderChromeState` normalizes unsupported animation modes to `none`.
- current guards prevent exposing Canvas controls before a verified replacement exists.

This remains the release-safe default until every gate in this document passes.

## 3. Selected Integration Approach

### 3.1 Canonical fork plus locked source snapshot

`Darkaxt/PlayLikeCurl` is the canonical source repository. Navic consumes an exact, immutable fork release as a mechanically generated source snapshot under a dedicated third-party Gradle module.

Proposed Navic layout:

```text
third_party/playlikecurl/
  karackencurllib/
  LICENSE.txt
  provenance.json
tools/update-playlikecurl.ps1
```

`provenance.json` must contain at least:

```json
{
  "repository": "https://github.com/Darkaxt/PlayLikeCurl",
  "commit": "<40-character commit>",
  "tag": "<immutable release tag>",
  "module": "karackencurllib",
  "sourceDigest": "<sha256 of the normalized imported tree>",
  "licenseDigest": "<sha256 of LICENSE.txt>"
}
```

The update script must:

1. accept an explicit tag or 40-character commit
2. download or clone into a disposable temporary directory
3. verify that the tag resolves to the requested commit
4. copy only the allowlisted library module and license
5. reject dirty or unexpected generated files
6. compute and write the provenance digests
7. never create symlinks, hidden sibling dependencies, or context-free backups
8. leave reviewable Git changes in Navic

Navic code must never edit the imported engine directly. Any engine fix is made and validated in `Darkaxt/PlayLikeCurl`, tagged, then re-imported.

### 3.2 Why this approach is selected

This provides:

- reproducible offline Navic builds
- no GitHub Packages authentication requirement
- no JitPack availability or remote-build dependency
- no Git submodule initialization failures
- no machine-specific sibling checkout
- source-level review and profiling
- an explicit update diff and provenance record
- a mechanical anti-drift boundary between the fork and Navic

A Maven Central publication may replace the snapshot later, but it is not a prerequisite for this integration.

### 3.3 Rejected approaches

1. **Continue the Navic internal port.** Rejected because it has already diverged from the reference and creates two geometry owners.
2. **Use JitPack or an unpinned branch dependency.** Rejected because remote availability, remote toolchains, and mutable branch state make builds nondeterministic.
3. **Use a local sibling checkout or Gradle path dependency.** Rejected because CI and other machines cannot reproduce it.
4. **Use a Git submodule.** Rejected because missing initialization and detached submodule state are avoidable integration hazards.
5. **Vendor a binary AAR only.** Rejected for the first integration because it hides the renderer source from Navic tests, profiling, and code review.

## 4. Ownership Boundaries

### 4.1 PlayLikeCurl owns

- portrait three-page model
- forward and backward deformation equations
- page-role and texture-slot rotation
- GLES2 shaders, mesh buffers, projection, and draw order
- fold shadow geometry and rendering
- four-leaf landscape transition model
- midpoint/fling settlement decisions
- gesture progress mapping after Navic grants horizontal page-turn ownership
- GL texture upload, active-deck replacement, and deletion
- GL context recreation for a submitted deck
- renderer readiness, settlement, and failure callbacks

### 4.2 Navic owns

- reader settings and mode selection
- Foliate page and spread identity
- horizontal-versus-vertical/selection gesture ownership
- raster generation, persistence, quality, and invalidation
- current/previous/next logical page selection
- preparation UI and progress
- cover, fixed-layout, image, paper, edge, gutter, and annotation composition before submission
- exact Foliate relocation after a committed animation
- post-relocation composition detection and final shield removal
- Whispersync playback and highlight state
- accessibility, lifecycle, diagnostics, and release gating

### 4.3 Forbidden overlap

- PlayLikeCurl must not call Foliate or know EPUB hrefs, CFIs, spine indexes, or WebView state.
- Navic must not calculate PlayLikeCurl vertices, texture coordinates, page-role transitions, or settlement curves.
- Neither side may independently classify the same completed gesture as both a tap and a drag.
- The library must not own a persistent ebook cache.
- Navic must not maintain a second GL renderer as a fallback.

## 5. Required Production API In The Fork

The exact Java/Kotlin names may vary, but the behavior below is normative.

### 5.1 Logical page identity

Every submitted page image carries an opaque client identity:

```text
generationId
logicalPageId
ordinal
widthPx
heightPx
bitmap
```

The engine treats `logicalPageId` as opaque. It must never infer page order from asset names or array positions alone.

### 5.2 Prepared page decks

Portrait submission contains exactly the logical previous, current, and next pages, with deterministic boundary duplication when one neighbor does not exist.

Landscape submission contains exactly:

```text
previous-left
previous-right
current-left
current-right
next-left
next-right
```

Each landscape bitmap represents one half-width physical leaf. The engine must not split a full-spread bitmap internally.

The submission API must be generation-aware. A stale generation cannot replace a newer deck or emit callbacks into it.

### 5.3 Asynchronous preparation contract

Navic decodes raster files away from the UI and GL threads, then submits immutable bitmap references. The engine queues GL upload and reports one of:

```text
prepared(generationId)
rejected(generationId, reason)
failed(generationId, recoverable, reason)
```

The renderer cannot become visible or accept a page-turn gesture until `prepared` has been emitted for the active generation.

No asset decoding, filesystem access, HTTP access, Room access, or bitmap scaling may occur in `onDrawFrame` or a touch callback.

### 5.4 Gesture input contract

Navic's frame host decides whether a touch belongs to:

- horizontal page turn
- vertical reader movement
- text selection/Whispersync long press
- toolbar/center tap

After horizontal ownership is granted, the original PlayLikeCurl gesture controller receives the complete down/move/up/cancel coordinate stream in leaf-local coordinates. Navic does not convert drag distance into its own curl progress.

Required behavior:

- a drag never calls `performClick()`
- a tap never starts a partially deformed frame
- cancel restores the current page and never navigates
- release decides commit exactly once
- slow release commits after the reference midpoint
- fling behavior remains the reference behavior
- the finger may continue to the natural endpoint; the animation cannot freeze at midpoint waiting for release
- the next gesture can be accepted on the first frame after settlement if its deck is prepared
- touches during settlement are either queued as one next intent or explicitly rejected to Navic; they are never silently transformed into native taps

### 5.5 Render lifecycle contract

The library must expose explicit lifecycle operations equivalent to:

```text
attach
submitDeck
setViewport
setVisible
cancelGesture
releaseDeck
dispose
```

Requirements:

- use render-when-dirty, requesting frames only for upload, drag, settlement, resize, or diagnostics
- retain at most the active portrait or landscape deck and a bounded replacement deck
- delete superseded GL textures with `glDeleteTextures`
- release retained bitmap references after upload when the client contract permits it
- recreate active textures after GL context loss without re-reading files on the GL thread
- preserve logical position when the viewport, adapter, or orientation changes
- never reset to page zero because a new deck is submitted
- make `dispose` idempotent

### 5.6 Error contract

Shader, upload, unsupported-size, and context errors must be callbacks, not uncaught renderer exceptions.

On a recoverable error:

1. stop drawing the failed generation
2. keep or restore the final opaque Navic shield
3. cancel the gesture without navigating
4. release failed GL resources
5. report a structured reason

On a non-recoverable session error, Navic disables PlayLikeCurl for the current reader session and shows one concise message: `Page animation unavailable`.

There are no cancellation timeouts. Generation changes, lifecycle events, and explicit cancellation make old work obsolete.

## 6. Navic Raster And Cache Contract

### 6.1 Raster ownership

Navic's existing managed raster cache remains the only persistent page-image cache. The imported library receives already decoded images and holds only the bounded active texture deck.

### 6.2 Cache key

The key must change when any input capable of changing page pixels or geometry changes, including:

- publication identity and content revision
- resource href/spine identity
- chapter-local page ordinal and global visual ordinal
- portrait/landscape and leaf side
- viewport pixel dimensions and density
- Foliate pagination generation
- font family, font size, weight, line height, margins, and column gap
- theme and page background
- paper, page-edge, stain, gutter, and cover-backdrop settings
- fixed-layout or reflowable mode
- bitmap quality
- raster schema version

Transient playback position is not part of the persistent key.

### 6.3 Quality setting

The existing values remain:

```text
Low      25%
Balanced 50% (default)
High     75%
Native   100%
```

Changing quality invalidates only animation rasters and textures, not the EPUB extraction or Bindery cache.

The selected quality controls raster dimensions, not the logical page bounds used by gesture geometry.

### 6.4 Preparation policy

Priority order remains:

1. current page/spread
2. next transition deck
3. previous transition deck
4. remainder of current chapter
5. next chapter
6. previous chapter

The current page cannot become interactive in PlayLikeCurl mode until current, next, and previous transition decks that exist at the boundary are decoded and uploaded.

If full-chapter generation is cheap enough under the existing calibration policy, Navic may precompute the chapter. Otherwise it maintains a rolling window. The user sees the same behavior; only preparation depth differs.

### 6.5 Preparation UI

When required pages are not prepared:

- keep the current Foliate page stable
- show the book cover or current-page artwork as the preparation background
- show real determinate progress for known work
- use an indeterminate indicator only before work cardinality is known
- do not expose a curl gesture that cannot complete
- do not fall through to an unanimated native page tap
- automatically enable the interaction when the required deck is prepared

### 6.6 Cache bounds and cleanup

- persistent raster storage has a configurable byte limit and LRU eviction
- active publication rasters are protected while its reader session exists
- old layout generations are pruned after the new generation becomes usable
- clear-reader-cache removes raster files, manifests, and in-memory decks
- corrupted entries are deleted and regenerated, not repeatedly retried
- no backup copy is made during routine invalidation

## 7. Page Composition Requirements

### 7.1 Base raster

The raster represents the actual visible physical leaf at the accepted reader geometry. It must include:

- EPUB text and images
- selected reader font and typography
- page background and theme
- enabled paper texture, page edges, and stains
- actual cover composition for cover pages
- persistent annotations that are part of the stable document view

It must not include:

- toolbars
- selection handles or context menus
- diagnostic labels
- the native page-turn preview
- the PlayLikeCurl overlay itself

### 7.2 Dynamic Whispersync highlighting

Whispersync progress changes too frequently to invalidate persistent page rasters.

Navic must therefore keep the base page raster stable and provide an ephemeral highlight overlay for pages participating in the active transition. The overlay:

- is generated from the same resolved text ranges used by the live reader
- is held only for the current transition generation
- is composited above the base page in the renderer
- freezes at gesture start and remains visually stable during settlement
- is discarded after Foliate settles and resumes live highlighting

If an ephemeral overlay cannot be prepared, the turn remains available using the base page, but it may not show stale highlight pixels baked into an old raster.

### 7.3 Cover and fixed-layout pages

- foreground covers remain uncropped
- the cover backdrop may be included only behind the foreground cover
- image-only and fixed-layout pages preserve aspect ratio and page bounds
- a cover or image page cannot be stretched merely to fill a texture
- transparent page regions resolve to the configured paper/page background before upload

### 7.4 Landscape spread

- each half-spread leaf has its own raster
- the center binding is a hard viewport boundary
- the forward sequence is `1 | 2` to `3 | 4` using the fork's two-phase leaf mapping
- while page 2 deforms, pages 1, 2, and 4 are visible
- after crossing the binding handoff, page 3 grows over page 1 while pages 1, 3, and 4 are visible
- the deformation never extends from an outer edge through the opposite leaf
- backward behavior is symmetric
- the existing Foliate gutter remains authoritative; PlayLikeCurl does not change text margins or column gap

## 8. Navigation And Settlement

### 8.1 Passive visual renderer

PlayLikeCurl never changes Foliate while the finger is down or while the 300 ms release animation is running.

### 8.2 Commit sequence

For a committed gesture:

1. PlayLikeCurl completes the reference settlement using the prepared source and destination leaves.
2. The renderer holds its final frame as an opaque shield.
3. Navic sends exactly one exact Foliate relocation for the target page/spread identity.
4. Navic waits for both the expected target token and a composited WebView frame.
5. Navic detaches the shield.
6. Navic rotates the prepared logical deck and immediately reports readiness for the next gesture if adjacent pages are uploaded.

There is no fixed delay and no timeout-based teardown.

### 8.3 Cancel sequence

For a cancelled gesture:

1. PlayLikeCurl settles back to the current page.
2. No Foliate relocation is issued.
3. The overlay is removed after the current live page is confirmed composited.
4. The current generation remains usable.

### 8.4 Boundaries

- first and last page gestures settle back without navigation
- missing adjacent pages use deterministic current-page duplication only inside the engine where the reference requires it
- Navic still reports the true boundary to accessibility and diagnostics
- a boundary gesture cannot wrap to another chapter unless Foliate's exact target resolver explicitly identifies that chapter page

## 9. Input, Accessibility, And Reader Modes

### 9.1 Supported initial scope

The first production release supports:

- horizontally paginated reflowable EPUB
- portrait one-page mode
- landscape two-leaf spread mode
- LTR and RTL direction mapping
- tap-to-turn and drag/fling interaction

### 9.2 Explicit exclusions

- vertical-scroll mode bypasses PlayLikeCurl
- continuous scrolling does not fabricate pages for this renderer
- text selection and Whispersync long press take precedence over page turning
- unsupported writing modes fall back to `none` with no hidden Canvas path

### 9.3 RTL

RTL changes logical previous/next edge mapping only. It does not mirror shader geometry ad hoc. Direction-specific tests must prove both portrait and spread transitions.

### 9.4 Reduced motion and animator scale

If system or app accessibility settings request reduced motion:

- drag feedback may still follow the finger if the user explicitly chose PlayLikeCurl
- release settlement may complete immediately
- exact navigation and shield teardown must still run
- animator scale zero cannot leave the renderer in `settling`

### 9.5 Touch conflict rules

- center tap continues to toggle reader chrome
- edge tap initiates a prepared page turn
- horizontal drag owns the gesture only after native touch slop and direction arbitration
- vertical movement remains with the reader
- long press in Whispersync mode seeks audio and never becomes a curl
- long press in normal mode remains text selection

## 10. Lifecycle And Concurrency

### 10.1 Generation tokens

Every asynchronous operation carries the current reader generation. Rotation, font/layout changes, publication changes, reader close, and cache clear advance the generation. Older callbacks are ignored and release their resources.

### 10.2 Rotation, resizing, and multi-window

- stop accepting new gestures
- cancel or finish the active gesture deterministically
- preserve the logical Foliate location
- create a new viewport/layout generation
- prepare new rasters for the new leaf bounds
- keep the old opaque shield only until the new live page is composited
- never stretch an old-orientation texture into the new viewport

### 10.3 App backgrounding

- pause settlement frame production
- preserve the logical gesture outcome without issuing duplicate navigation
- release or retain the active GL context according to Android lifecycle events
- rehydrate the submitted deck after context recreation
- do not decode on resume's UI callback

### 10.4 Single-writer rules

- one coordinator owns active reader generation
- one raster scheduler writes each cache key
- one GL surface owns the submitted texture deck
- one settlement token can navigate Foliate
- duplicate callbacks are idempotent

## 11. Performance Requirements

The implementation is rejected if it only looks correct in still screenshots.

Required targets on the tablet-class validation profile:

- no filesystem, database, image decode, or bitmap scaling work on UI/GL gesture frames
- touch-to-first-deformed-frame: no more than 33 ms at the 95th percentile after preparation
- settlement animation: reference 300 ms behavior unless reduced motion is active
- next gesture acceptance: within one rendered frame after settlement when the adjacent deck is prepared
- no 1.5 to 2.5 second post-turn dead zone
- no 13 to 15 second adjacent-page warmup after the current page is interactive
- no unbounded texture or bitmap growth during 100 consecutive turns
- no bitmap, float-array, index-array, or direct-buffer allocation per draw frame
- no continuous GL rendering while idle
- no full throwable logging per frame or per failed image

Frame timing and memory evidence must be captured, not inferred from subjective smoothness.

## 12. Diagnostics

Normal logging is event-based and bounded. It records:

- reader generation
- logical current and target page IDs
- portrait/spread mode and direction
- deck preparation/upload start and completion
- gesture ownership and release decision
- settlement start/completion
- Foliate relocation token
- target composited-frame confirmation
- context loss/restoration
- renderer fallback/error reason

Per-frame geometry logging is available only under an explicit reader diagnostic flag.

The crash logger must persist uncaught GL/integration failures, but the renderer should normally convert expected resource failures into callbacks before they become uncaught exceptions.

## 13. Settings And Migration

### 13.1 User-facing modes

```text
Page turn animation
- None
- PlayLikeCurl
```

Do not expose `Canvas` as a parallel mode.

### 13.2 Migration

- persisted `canvas` values migrate to `none`
- unknown values normalize to `none`
- `playlikecurl` is introduced only after the production gate passes
- the first public integration release keeps `none` as the default
- the bitmap-quality value is retained

### 13.3 Visibility

The PlayLikeCurl option remains hidden or debug-only until the cutover gate passes. ReaderDev can force it without changing production preferences.

## 14. Repository And Attribution Requirements

### 14.1 Fork repository

Before Navic import, `Darkaxt/PlayLikeCurl` must have:

- a production API commit on `master`
- all fork tests passing
- a clean demo APK with portrait and landscape MP4 evidence
- MIT license retained
- a changelog describing production API and lifecycle changes
- an immutable integration tag
- no open Navic-specific patches living only in a side branch

### 14.2 Navic repository

- import the tagged module mechanically
- update third-party attribution to name both the original project and the Darkaxt modernization fork
- record repository URL, tag, commit, and digest
- add source guards preventing edits inside the imported snapshot without a provenance update
- add source guards preventing copied PlayLikeCurl geometry outside the imported module

## 15. Staged Implementation Plan

Each tranche ends with a recorded MP4 comparison against the fork's reference demo. An unexplained difference blocks the next tranche. Slow full-suite or device verification is grouped at meaningful feature gates rather than repeated after every small source edit.

### Tranche 0 - Clean baselines and evidence

1. Start from a clean Navic master worktree.
2. Preserve the current dirty prototype worktree for forensic comparison only; do not merge it.
3. Record Navic and fork commits.
4. Build and record the fork's portrait and landscape reference MP4s.
5. Record touch coordinates, progress, direction, and settlement timing used by those captures.

**Gate:** repeatable reference MP4s and clean baselines exist.

### Tranche 1 - Fork production API

1. Replace asset-string submission with generation-aware bitmap page decks.
2. Remove asset decoding from the renderer.
3. Add bounded texture ownership and explicit deletion.
4. Switch idle rendering to when-dirty.
5. Add readiness, failure, settlement, and disposal callbacks.
6. Separate drag from click behavior.
7. Preserve logical position across deck and viewport changes.
8. Add context-loss rehydration.
9. Add tests for every lifecycle rule.

**Gate:** demo uses client-provided decoded bitmaps and matches baseline portrait/landscape MP4s.

### Tranche 2 - Immutable fork release and Navic snapshot

1. Tag the fork.
2. Implement the snapshot update script and provenance manifest.
3. Import only the library module and license.
4. Add the third-party Gradle module.
5. Add digest and anti-drift guards.

**Gate:** a clean Navic clone builds the imported module offline after dependencies are cached, and the snapshot digest matches the fork tag.

### Tranche 3 - ReaderDev library parity

1. Replace ReaderDev's Navic-owned reference model/renderer with the imported library.
2. Keep the original reference assets and gesture script.
3. Validate portrait forward/backward drag, fling, slow midpoint release, and cancel.
4. Validate four-leaf landscape forward/backward sequences and center boundary.
5. Validate fold shadow and transparent-region composition.

**Gate:** ReaderDev MP4s match the fork demo; no Navic geometry code participates.

### Tranche 4 - Navic raster adapter

1. Adapt `ReaderPageRasterScheduler` output to production page decks.
2. Decode and scale on background dispatchers.
3. Submit current/adjacent decks with generation IDs.
4. Implement preparation progress and cover-backed loading state.
5. Implement bounded bitmap reference ownership.
6. Exercise cache hit, cache miss, corruption, quality change, and context recreation.

**Gate:** ReaderDev runs exclusively from the managed raster cache with no visible capture or GL-thread decode.

### Tranche 5 - Portrait Foliate integration

1. Integrate the imported view behind a debug-only production flag.
2. Route horizontal gestures through the library controller.
3. Keep Foliate static during drag/settlement.
4. Implement exact release navigation and composited-frame shield teardown.
5. Validate cover, text, image, chapter boundary, first page, and last page.
6. Validate rapid repeated forward/backward gestures.
7. Validate Whispersync and long-press arbitration.

**Gate:** portrait emulator recording has correct pages, no flash, no dead zone, and exact one-page navigation.

### Tranche 6 - Landscape Foliate integration

1. Map Foliate's actual left/right leaf identities to the six-page deck.
2. Preserve the fork's deformation unchanged.
3. Apply only half-viewport placement and center clipping.
4. Validate `1 | 2` to `3 | 4` and the symmetric backward path.
5. Validate odd/even section and chapter boundaries.
6. Validate rotation into and out of an active spread.

**Gate:** tablet-size emulator recording shows no leaf crossing, duplicated text, transparent page, or wrong destination.

### Tranche 7 - Dynamic overlays and lifecycle

1. Add ephemeral Whispersync highlight composition.
2. Validate annotation and selection exclusions.
3. Validate background/foreground, multi-window resize, font/theme change, and GL context loss.
4. Validate animator scale zero and reduced motion.
5. Run 100-turn memory and frame timing tests.

**Gate:** lifecycle matrix passes with bounded memory and no stale-generation callbacks.

### Tranche 8 - Cutover and cleanup

1. Remove the failed Canvas production path.
2. Remove Navic-owned PlayLikeCurl model, geometry, shaders, and gesture settlement code.
3. Retain only Navic cache, adapter, coordinator, and ReaderDev integration code.
4. Replace stale tests with dependency-boundary and production-contract tests.
5. Expose `PlayLikeCurl` in settings.
6. Keep production default `none` for the first release.

**Gate:** source audit finds exactly one deformation implementation: the imported fork module.

### Tranche 9 - Release validation

1. Run focused reader unit, host, and source-guard tests.
2. Run the complete Android host suite.
3. Build and install ReaderDev on phone and tablet-size emulator.
4. Capture portrait and landscape MP4s.
5. Install a debug/release-candidate APK on the real tablet when available.
6. Validate Alcatraz and at least one image-heavy/fixed-layout book.
7. Validate Whispersync playback while turning pages.
8. Verify clean Git state and exact fork provenance.
9. Only then create the public Navic release.

## 16. Test Matrix

### 16.1 Fork tests

- reference constants and sampled geometry
- forward/backward page roles
- texture-slot rotation
- slow midpoint commit and rollback
- fling settlement
- portrait and landscape draw order
- landscape center clipping
- cast-shadow geometry
- generation replacement
- bounded active textures
- dispose idempotence
- context recreation
- drag never clicks
- no GL-thread asset decode

### 16.2 Navic unit/host tests

- raster key sensitivity and cache reuse
- chapter/spread deck mapping
- stale-generation rejection
- exact one relocation per commit
- no relocation on cancel
- shield remains until target composited frame
- portrait/landscape rotation
- first/last page boundary
- LTR/RTL mapping
- bitmap quality migration
- no Canvas mode exposure
- no copied PlayLikeCurl geometry outside the imported module
- import digest and attribution

### 16.3 Visual cases

- portrait forward/backward drag from top, middle, and bottom edge contact
- portrait tap turn
- slow release before and after midpoint
- fast repeated turns
- landscape forward and backward four-leaf sequence
- cover to first page
- chapter boundary
- text-heavy page
- image-heavy page
- sepia/paper texture page
- Whispersync active highlight
- app rotate during preparation and after settlement

### 16.4 Failure cases

- missing raster
- corrupted raster
- bitmap decode failure
- texture upload failure
- GL context loss
- app background during drag
- app background during settlement
- layout generation changes during preparation
- cache clear during idle reader session
- renderer failure after release but before navigation

## 17. Acceptance Criteria

The integration is complete only when all are true:

1. The fork is the only source of page deformation and interaction behavior.
2. The imported source is tied to an immutable fork tag and verified digest.
3. No Navic code duplicates PlayLikeCurl geometry, shaders, or page-role state.
4. No page decode, I/O, or database work occurs on UI/GL gesture frames.
5. Portrait and landscape recordings match the accepted fork reference behavior.
6. Forward and backward gestures render the correct front, reverse, underneath, and destination pages.
7. Landscape leaves never cross the center binding.
8. No WebView flash, black frame, transparent page, mirrored-front-text error, or native tap fallback occurs.
9. A committed gesture relocates Foliate exactly once; a cancelled gesture never relocates it.
10. The next prepared gesture is available within one frame after settlement.
11. Rotation, resize, app background, and context loss do not corrupt position or resources.
12. Memory remains bounded over the 100-turn test.
13. Existing reader interactions, selection, annotations, and Whispersync remain functional.
14. The production setting stays hidden until all release gates pass.
15. A public release is created only after emulator and real-device evidence is accepted.

## 18. Rollback

Until final cutover, `none` remains the production default and the existing stable tap navigation remains available.

If the integrated renderer fails a release gate:

- keep the imported fork and failing work behind the debug flag
- keep production normalized to `none`
- do not restore Canvas or the Navic-owned PlayLikeCurl renderer
- fix the canonical fork or adapter boundary, update the immutable snapshot, and repeat the blocked gate

Rollback means disabling the integration, not reviving a second deformation implementation.
