# PlayLikeCurl Raster Readiness And Gesture Reliability Design

**Status:** Proposed corrective design

**Date:** 2026-07-17

**Navic audit baseline:** `a7a2c899dfb2f634b29616826ded927fdac8ac40`

**Related specification:**

- `docs/superpowers/specs/2026-07-15-reader-playlikecurl-library-integration-design.md`

**Amends:**

- Section 6.4, "Preparation policy", of the PlayLikeCurl integration design.
- Section 6.5, "Preparation UI", of the PlayLikeCurl integration design.
- Section 8.2, "Commit sequence", where the current implementation rebuilds
  readiness instead of rotating prepared state.

The imported PlayLikeCurl renderer remains the sole deformation engine. This
document changes Navic's raster preparation, cache residency, deck lifecycle,
gesture gating, and diagnostics. It does not introduce a second renderer or
change PlayLikeCurl geometry.

## 1. Problem Statement

The ReaderDev emulator records drag input that appears to do nothing. The input
is not being lost by Android. It reaches Navic, but Navic cancels it while the
raster pipeline moves back through a blocking preparation state.

The retained emulator log for the failing Alcatraz session recorded:

- 6 `Reader native drag candidate` events
- 6 `Reader native drag preview` events
- 6 `PreviewPageDrag(... phase=Cancel)` actions
- 32 preparation states with `gestures=ConsumeWhilePreparing`
- 3 `deck-load-failed` events
- 3 `Missing Foliate raster` events

One failure attempted to prepare portrait pages `27,28,29` while the source
reported only pages `24,25,26,27,28` as cached. Page 29 was missing, the deck
load failed, `interactionReady` became false, and an input sequence ended in a
cancel rather than a curl.

This is a lifecycle defect, not a PlayLikeCurl deformation defect:

1. A completed turn relocates Foliate exactly.
2. Exact relocation requests another prewarm and prepared deck refresh.
3. The preparation controller publishes a blocking `Preparing` state.
4. The PlayLikeCurl controller temporarily clears `interactionReady` while it
   submits a replacement deck.
5. A required adjacent raster can be absent from the current decoded window.
6. The gesture policy consumes input while preparation is incomplete.
7. The user receives neither a page turn nor a reason.

The cover/preparation shield is only presentation. It cannot correct missing
rasters, repeated readiness transitions, or a controller that disables input
after every settlement. In the failing session the preparation presentation was
`Hidden` because the reader had prepared successfully before, while gestures
were still `ConsumeWhilePreparing`. That creates an invisible dead zone.

## 2. Objective

Make PlayLikeCurl interaction continuously available after the initial chapter
preparation completes.

For a stable publication and reader raster profile:

- generate each current-chapter page raster at most once
- persist generated rasters in the managed reader cache
- reuse cached rasters across turns, reader reopen, and app restart
- keep enough decoded pages and uploaded textures around the current position
  to accept consecutive forward and backward turns
- rotate or remap prepared page roles after settlement instead of rerunning the
  raster preparation pipeline
- prefetch future pages without changing the visible reader's gesture policy
- account for every pointer sequence with one explicit terminal outcome
- never let a drag silently degrade into a tap or disappear

The page animation must remain responsive even when deeper chapter or adjacent
chapter prefetch is still running.

## 3. Non-Goals

- Reimplementing PlayLikeCurl geometry in Navic.
- Changing Foliate's authority over pagination, navigation, progress, selection,
  annotations, or Whispersync.
- Holding an entire chapter as decoded Android bitmaps or GPU textures.
- Baking transient Whispersync progress into persistent page rasters.
- Using fixed delays or timeout-based cancellation.
- Hiding lifecycle defects behind a longer cover animation.

## 4. Current Architecture Audit

### 4.1 Persistent raster cache

`ReaderPageRasterCache` already supports app-private persistent PNG storage, a
manifest, a 384 MiB disk limit, and an in-memory decoded LRU. The decoded LRU is
currently limited to five entries.

The persistent key includes publication, pagination, spine, chapter page,
global visual ordinal, viewport, layout, decoration, quality, and schema. This
is sufficient for chapter reuse if generations remain stable and preparation
actually schedules all chapter pages.

### 4.2 Active PlayLikeCurl deck

`readerPlayLikeCurlLibraryDeckPageIndices` prepares:

- portrait: previous, current, and next page
- landscape: the current spread plus two leaves before and two leaves after

This bounded renderer deck is correct as a deformation input, but it is not a
chapter cache. The controller currently treats preparation of this small deck as
if it were reader readiness itself.

### 4.3 Permissive preparation policy

The previous specification allowed either full-chapter generation or a rolling
window based on calibration. That made a performance implementation choice part
of observable behavior. The current rolling-window path can fail to contain the
next required page and then blocks gestures while recovering.

This document removes that ambiguity: the current chapter is a persistent
preparation unit. Memory and GPU residency remain bounded independently.

### 4.4 Settlement lifecycle

`ReaderPlayLikeCurlFoliateController` currently clears interaction readiness on
deck submission and after exact settlement. It then requests prewarm and refresh
again when Foliate reports the exact page. Ordinary settlement therefore
re-enters setup code intended for initial activation or invalidation.

The imported renderer already knows the settled logical page. Navic must use
that callback to rotate prepared roles immediately, then reconcile Foliate in
the background without invalidating the usable raster generation.

## 5. Required State Model

Raster generation, decoded residency, texture residency, and user interaction
must be represented as separate state machines.

### 5.1 Raster generation state

One `ChapterRasterSet` represents one spine resource under one immutable
`ReaderPageRasterProfile`:

```text
ChapterRasterSet
  publicationHash
  profile
  spineIndex
  hrefHash
  firstVisualOrdinal
  pageCount
  generatedPages
  failedPages
  generationState
```

`generationState` is one of:

```text
NotScheduled
Generating
Ready
Failed
Invalidated
```

Changing a real pixel or geometry input creates a new profile and therefore a
new chapter raster set. Page settlement does not create a new profile.

### 5.2 Decoded working set

The decoded bitmap cache is a bounded LRU over persistent chapter rasters. Its
minimum protected window is:

- portrait: current page plus two pages in each direction
- landscape: current spread plus two complete spreads in each direction

The protected window is larger than the active deformation deck so the next
deck can be assembled without disk decode after settlement.

The current fixed five-entry decoded limit is therefore only sufficient for
portrait. The cache must enforce a profile-aware protected minimum of five
decoded pages in portrait and ten decoded leaves in a two-page landscape
spread. Configuration or memory trimming may reduce unprotected overhead, but
must not evict entries inside the immediate protected window while interaction
is `Ready`.

Decoded pages outside the protected window may be released. Their persistent
rasters remain valid and must not be regenerated.

### 5.3 Texture residency

PlayLikeCurl owns a bounded active texture deck and an optional prepared
replacement. Texture residency is not raster readiness.

After settlement:

1. Remap the settled pages to current roles.
2. Keep already uploaded source and destination textures alive.
3. Promote the prepared replacement when required by the library contract.
4. Fill the new far edge from the decoded protected window asynchronously.
5. Do not clear interaction readiness when the immediate next and previous
   transitions are already available.

If the imported library cannot remap or replace a deck without a readiness gap,
the fork API must be extended. Navic must not compensate with gesture
cancellation or a parallel renderer.

### 5.4 Interaction state

Interaction state is one of:

```text
BlockingInitialPreparation
Ready
Settling
BackgroundPrefetch
BlockingProfileRegeneration
Failed
```

`BackgroundPrefetch` is interactive. Only initial preparation, a real profile
change, or an unrecoverable loss of the required transition pages may block a
new gesture.

An ordinary page settlement must transition `Settling -> Ready`; it must not
transition through `BlockingInitialPreparation`.

## 6. Chapter Preparation Contract

### 6.1 Cold open

On a cold open or raster-profile change:

1. Resolve the chapter page manifest from Foliate.
2. Read all matching current-chapter rasters from the persistent cache.
3. Generate only cache misses.
4. Persist each successful raster atomically.
5. Complete the persistent raster set for the entire current chapter.
6. Decode and upload the protected interactive window.
7. Enable interaction only when the chapter raster set is `Ready` and current,
   previous, next, and one-turn lookahead pages are resident.

The loading cover and progress surface remain visible until the entire current
chapter is persisted. This is the one chapter-level preparation event. Once the
reader becomes interactive, no remaining work for that chapter may later block
the user or require another WebView capture.

### 6.2 Chapter completion

Each page raster is generated at most once per profile. A failed or corrupted
entry may be regenerated after its invalid cache file is removed. Normal LRU
decode eviction never authorizes regeneration.

When a chapter is larger than the configured disk budget at the selected
quality, Navic deterministically lowers raster quality for that chapter until
the complete chapter fits. It must not alternate between eviction and
regeneration while the user reads.

If the complete active chapter still exceeds the nominal disk limit at the
lowest supported quality, that chapter becomes a protected temporary overflow.
The cache evicts unrelated least-recently-used chapter sets first, permits the
active set to exceed the limit while the book is open, and returns below the
configured limit after the protected chapter is closed or superseded. It must
not repeatedly delete and recapture pages from the active chapter to satisfy a
hard byte limit.

### 6.3 Adjacent chapter prefetch

After the current chapter's interactive window is ready, schedule:

1. first interactive window of the next chapter
2. first interactive window of the previous chapter
3. remainder of the next chapter when the device is idle
4. remainder of the previous chapter when the device is idle

Adjacent chapter work is background-only. It cannot change current gesture
disposition.

### 6.4 Reopen behavior

Reopening a book under the same profile reads the chapter manifest and existing
rasters. It must perform zero WebView page captures for valid cached pages.

The loading cover may remain while the protected decoded/texture window is
hydrated, but the progress must represent reads, decodes, and uploads rather
than falsely reporting raster generation.

## 7. Gesture Contract

### 7.1 Per-pointer identity

Every pointer-down receives a monotonically increasing `gestureId`. All native,
Navic-controller, raster-readiness, and PlayLikeCurl events for that sequence
carry the same ID.

### 7.2 Terminal outcomes

Every `gestureId` produces exactly one terminal outcome:

```text
CommittedForward
CommittedBackward
CancelledByUser
RejectedPreparing
RejectedSettling
RejectedDirection
RejectedBoundary
RejectedRendererUnavailable
FailedRenderer
```

There is no unlabelled cancel and no missing terminal event.

### 7.3 Drag versus tap

- Crossing touch slop permanently classifies the sequence as a drag.
- A classified drag can never fall through to native tap navigation.
- Horizontal intent is based on the initial movement trend, not a late single
  sample.
- Vertical displacement, pointer coordinate transforms, and viewport changes
  are logged with the gesture ID when they cause direction rejection.
- Rejected input is consumed and reported; it does not trigger another reader
  action.

The native frame's current candidate and preview logs remain, but they must be
augmented with acceptance and terminal reason.

### 7.4 Input during blocking preparation

Input during a genuinely blocking state is consumed, logged as
`RejectedPreparing`, and produces immediate visual feedback through the existing
preparation indicator. It must not disappear silently.

Once the reader has become ready for a profile, background generation or
prefetch cannot produce `RejectedPreparing` on ordinary pages.

## 8. Preparation Presentation

### 8.1 Cold preparation

The book cover or current-page artwork is the visible preparation background
during cold open and real profile regeneration. It includes a determinate
progress indicator once work cardinality is known.

The shield is removed only when:

- the complete current-chapter raster set is persisted
- the live Foliate page is composited
- the PlayLikeCurl active deck is prepared
- the protected one-turn lookahead is decoded
- gesture state is `Ready`

### 8.2 Warm reading

After the cold shield is removed:

- ordinary turns never show the book cover again
- passive prefetch does not attach a shield
- passive prefetch does not publish a blocking gesture disposition
- a cache failure affecting a future page is repaired before it enters the
  protected window

At a chapter boundary whose target window is unexpectedly unavailable, retain
the current page as the preparation surface and show progress. Do not blank the
reader, expose a black GL surface, or consume gestures invisibly.

## 9. Failure And Recovery

### 9.1 Missing raster

A missing raster inside the protected window is a readiness invariant failure.
Record the cache key, chapter, page, profile generation, and scheduling history.
Regenerate the exact missing page once, without invalidating unrelated valid
pages.

`deck-load-failed` must not trigger repeated full-window preparation requests.

### 9.2 Corrupt persistent entry

Delete the corrupt raster and manifest entry, regenerate that page, and rewrite
the manifest atomically. Do not repeatedly decode the same corrupt file.

### 9.3 Memory pressure

Memory pressure may shrink decoded and GPU residency outside the immediate
interactive window. It does not delete valid persistent current-chapter rasters
or increment the raster profile generation.

If Android forces the immediate window out of memory, enter
`BlockingProfileRegeneration` only long enough to decode and upload the required
cached pages. Do not recapture the WebView.

### 9.4 Layout invalidation

Rotation, viewport change, font/layout change, theme pixel change, decoration
change, quality change, and publication revision create a new raster profile.
Stale callbacks release their leases and cannot alter the new state.

## 10. Google Play Books Comparison

Google Play Books demonstrates that this interaction can be continuous because
its pagination, page-image preparation, deformation renderer, and navigation
state are designed as one coordinated pipeline. Its exact proprietary internals
are not asserted here; the observed behavior shows that preparation is completed
before the gesture surface is exposed and that settlement does not visibly
rebuild the book.

Navic currently bridges a live Foliate WebView to an external GL renderer after
pagination. That additional boundary is the engineering challenge, but it is not
an excuse for a dead gesture surface. The solution is to establish the same
runtime invariant locally: immutable prepared page images, bounded resident
textures, and navigation reconciliation after animation without invalidating
prepared state.

## 11. Diagnostics

Add structured logs for:

- chapter raster-set creation and profile identity
- cache hit, miss, corrupt entry, generation, write, decode, and upload
- decoded protected-window contents
- active and pending texture-deck page identities
- interaction-state transitions with reasons
- gesture ID, pointer action, transformed coordinates, candidate direction,
  acceptance, release decision, and terminal outcome
- settlement page identity, exact Foliate relocation, composited-frame
  confirmation, role rotation, and readiness preservation
- invariant violations such as a protected page missing from cache

Logs must not include EPUB text, rendered bitmap pixels, API credentials, or
Whispersync transcript content.

The diagnostics must make these questions answerable from one retained session:

1. Did Android deliver the pointer sequence?
2. Did Navic classify it as a drag or tap?
3. Was PlayLikeCurl ready?
4. Which prepared page identities were available?
5. Why was the gesture accepted or rejected?
6. Did settlement preserve readiness for the next gesture?

## 12. Implementation Boundaries

### 12.1 Navic-owned components

- chapter raster manifest and scheduler
- persistent raster cache and decoded working set
- Foliate page identity and exact relocation
- preparation presentation and progress
- gesture routing and diagnostics
- PlayLikeCurl bitmap/deck adapter

### 12.2 PlayLikeCurl-owned components

- gesture deformation and release animation
- active texture ownership while leased
- page role rotation/remapping
- settlement callbacks
- recoverable deck replacement API

If the imported API lacks role rotation or gap-free replacement, update
`Darkaxt/PlayLikeCurl`, release a new immutable version, update its README and
changelog, and then update Navic's pinned snapshot. Do not add a Navic-only
geometry or renderer workaround.

## 13. Test Requirements

### 13.1 Unit and host tests

- raster profile remains unchanged across exact page settlement
- every current-chapter page is scheduled once per profile
- a persistent cache hit never invokes WebView capture
- decoded eviction never deletes or regenerates the persistent raster
- portrait protected window contains current plus two pages in both directions
- landscape protected window contains current plus two complete spreads in both
  directions
- settlement rotates/remaps prepared roles without entering blocking
  preparation
- background prefetch never changes gesture disposition from `Ready`
- every gesture ID receives exactly one terminal outcome
- a classified drag never invokes tap navigation
- missing or corrupt raster repairs only the affected page
- chapter-boundary preparation cannot wrap to an unrelated chapter page
- memory-pressure recovery decodes cached pages without WebView recapture

### 13.2 ReaderDev emulator validation

Use the Alcatraz EPUB at tablet portrait and landscape dimensions.

Cold-cache scenario:

1. Clear only the managed reader raster cache.
2. Open the book and record initial preparation progress.
3. Verify current and adjacent interaction becomes available before cover
   removal.
4. Turn forward 20 pages, backward 20 pages, then alternate directions 20 times.
5. Perform ten rapid consecutive gestures after settlement.
6. Verify no accepted drag produces no visual response.
7. Verify no `Missing Foliate raster`, `deck-load-failed`, or invisible
   `ConsumeWhilePreparing` state occurs after initial readiness.

Warm-cache scenario:

1. Close and reopen the same book without changing settings or viewport.
2. Verify zero raster generation for valid current-chapter pages.
3. Verify preparation work consists only of manifest read, decode, and texture
   upload.
4. Repeat rapid forward/backward gestures.

Chapter-boundary scenario:

1. Navigate to the last two pages of a chapter.
2. Verify next-chapter lookahead is prepared in the background.
3. Cross the boundary in both directions.
4. Verify no unrelated spine resource or visual ordinal appears.

### 13.3 Device validation

Repeat the cold, warm, rapid-gesture, rotation, background/resume, low-memory,
and chapter-boundary scenarios on the tablet before public release.

Record an MP4 and retain the aligned gesture/state log. Device validation is a
release gate because WebView composition, GPU texture limits, and memory
pressure differ from the emulator.

## 14. Performance And Acceptance Gates

The implementation is accepted only when all of these are true:

1. Current-chapter page rasters are generated at most once per profile.
2. Reopening the same chapter/profile performs zero WebView captures for valid
   cached pages.
3. Normal page settlement does not increment raster profile generation.
4. Normal page settlement does not enter blocking preparation.
5. The immediate next gesture is accepted within one rendered frame after
   settlement when its boundary page exists.
6. No gesture-frame path performs filesystem, database, WebView capture, bitmap
   decode, bitmap scaling, or texture upload.
7. Every pointer sequence has exactly one terminal diagnostic outcome.
8. No drag falls through to tap navigation.
9. No black surface, cover blink, hidden preparation shield, or unlabelled dead
   zone appears during ordinary reading.
10. One hundred consecutive turns produce no unbounded bitmap, texture, or
    lease growth.
11. Persistent cache size remains within the configured limit without evicting
    the active chapter into a regeneration loop.
12. Portrait and landscape preserve the imported PlayLikeCurl reference
    deformation and leaf boundaries.

## 15. Staged Delivery

### Stage 1 - Gesture accounting

Add gesture IDs, explicit acceptance/rejection reasons, and exactly-one terminal
outcome. Validate that manual emulator actions can be reconciled one-for-one
with logs before changing cache behavior.

### Stage 2 - Separate readiness state machines

Split raster generation, decoded residency, texture residency, and interaction
state. Remove blocking gesture disposition from background prefetch.

### Stage 3 - Chapter raster scheduling

Make the current chapter the persistent generation unit. Generate cache misses
once, protect the active chapter from disk eviction, and hydrate the protected
decoded window by priority.

### Stage 4 - Gap-free deck lifecycle

Rotate/remap page roles after settlement, retain immediate textures, and fill
the far edge from decoded cache. Extend and release the PlayLikeCurl fork API if
required. Eliminate per-turn deck readiness loss.

### Stage 5 - Preparation presentation and recovery

Restrict the cover shield to cold preparation/profile regeneration, add truthful
progress phases, and implement single-page missing/corrupt raster recovery.

### Stage 6 - Automated and visual validation

Run focused tests, ReaderDev cold/warm cache scenarios, MP4 comparison, rapid
gestures, chapter boundaries, rotation, app resume, and memory pressure. Do not
create a public release until emulator and tablet acceptance gates pass.

## 16. Release Policy

- Intermediate stages use ReaderDev/debug builds only.
- No public release is created merely because the code compiles or focused unit
  tests pass.
- The first public candidate requires clean emulator and tablet evidence for
  every acceptance gate in Section 14.
- Release notes must state whether current-chapter cache generation, warm reopen,
  rapid consecutive gestures, and chapter boundaries were validated.
