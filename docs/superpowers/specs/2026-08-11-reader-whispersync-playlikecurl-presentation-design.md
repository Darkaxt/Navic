# Whispersync And PlayLikeCurl Presentation Reliability Design

**Status:** Proposed corrective design

**Date:** 2026-08-11

**Navic audit baseline:** `415a7b2e050c6664157b2a64c038d4e953c385d3`

**Validated release:** `v1.0.11-iota56` (`versionCode 583`)

**Related specifications:**

- `docs/superpowers/specs/2026-07-17-reader-playlikecurl-raster-readiness-design.md`
- `docs/superpowers/specs/2026-08-04-reader-paginator-commit-receipts-design.md`

This document consolidates the physical-tablet findings from iota55 and iota56
and defines the presentation contract required to correct the remaining reader
regressions. It does not change Foliate's authority, add another paginator, or
replace PlayLikeCurl's deformation engine. It is a specification, not an
implementation plan.

## 1. Problem Statement

The iota56 reader fixed two release-blocking defects found in iota55:

1. A second turn could supersede an unfinished exact relocation and visual
   handoff. The superseded handoff failed, the renderer latched `Failed`, and
   later input was rejected.
2. `PageSurfaceView` used an above-window layer and physically occluded Compose
   reader chrome.

The first defect was corrected by fencing new turns with
`ReaderPageRelocationQueue.hasInFlightHead()`. The second was corrected by
changing the surface from `setZOrderOnTop(true)` to
`setZOrderMediaOverlay(true)`.

Physical testing of iota56 then exposed four presentation defects:

- the fixed back-cover edge disappears while a curl is visible
- the Whispersync transport cannot reliably be seen or used in immersive
  reading, although it becomes usable when the full reader menu is shown
- highlighting can remain invisible and then catch up in a batch
- word highlighting visibly trails speech within a sentence

The tested publication is not limited to sentence timing. Privacy-safe logs
recorded 263 playback highlight updates, 262 updates with word-progress
endpoints, and 262 distinct word-progress endpoints. The current publication
therefore supports word-level synchronization. The regression is in scheduling
and visible presentation, not missing word timing data.

The existing implementation sends transient highlighting into Foliate's live
WebView overlay. PlayLikeCurl presents a page raster captured before those
mutations. The two visible planes can therefore disagree: the semantically
correct WebView advances word by word while the user continues to see an older
native raster. Current playback updates also arrive on an approximately 500 ms
cadence, with an observed slow tail above one second. Both mechanisms can add
latency, and surface/WebView handoffs can make accumulated progress appear all
at once.

## 2. Evidence And Confidence

The following labels are normative for interpreting this document:

- **Confirmed**: source and retained runtime evidence establish the causal
  mechanism.
- **Strongly supported**: the mechanism matches source and the observed interval,
  but the retained evidence does not directly record the final boundary.
- **Candidate**: source permits the mechanism, but another runtime observation is
  required to distinguish it.
- **Ruled out**: retained evidence contradicts the explanation.

SurfaceFlinger evidence recorded the PlayLikeCurl surface at relative `z=1` in
iota55 and relative `z=-1` in iota56. The latter restored normal Compose chrome
composition, while the user-observed back-cover edge loss began during the curl
interval. This correlation supports, but does not by itself prove, the edge
mechanism below.

| Finding | Confidence | Evidence and interpretation |
| --- | --- | --- |
| iota55 complete reader freeze | Confirmed and corrected in iota56 | A second turn was admitted while the first relocation remained in visual handoff. The superseded handoff produced `PresentationFailed`, latched renderer failure, and caused later `RejectedRendererUnavailable` outcomes. The iota56 queue admission fence prevented recurrence across ten observed committed turns. |
| Back-cover edge loss during curl | Strongly supported | The portrait WebView leaves a one-percent strip for a DOM back-cover backing plane. The PlayLikeCurl leaf raster excludes that strip and clears unused GL regions transparent. With the curl surface in media-overlay Z mode, the missing strip depends on the WebView being composed beneath a transparent SurfaceView region. A DOM z-index cannot control SurfaceControl ordering. |
| Whispersync control disappearance | Unresolved; compositor/state candidates remain | Normal playback policy does not hide the control after playback starts. It keeps the control visible for ready, seeking, loading, and playing states; playing maps the action to `StopAndReset`. Menu visibility changes placement but not policy visibility. A status reset to unavailable, shell-cover activation, capability loss, or platform-level surface occlusion/input interception must be distinguished. |
| Highlight absent while an active curl is visible | Confirmed architectural limitation | Highlighting mutates Foliate's WebView overlay. It is not injected into an already prepared PlayLikeCurl raster, so the raster cannot show a later word update. |
| Highlight absent after the curl settles | Candidate separate bridge/painter failure | After a successful WebView handoff, a valid DOM overlay should be visible. Persistent absence requires checking `overlayFragmentActive` versus `overlayFragmentInactive`, current ownership, and whether the painter rejected or cleared the range. It is not explained solely by the stale curl raster. |
| Batched visible highlight catch-up | Strongly supported | Native synchronization emitted continuous word-progress updates while visible presentation remained stale. Exposing the updated WebView, or finally repainting its active overlay, can reveal accumulated progress at once. Historical updates must not be replayed visibly after a stall. |
| Word highlight trailing speech | Confirmed timing limitation | The observed update interval had a 500 ms median, approximately 1.082 s p95, and 1.325 s maximum. A fixed coarse playback-position sample cannot present a boundary more accurately than its sampling interval, even with exact word timestamps. |
| Publication lacks word sync | Ruled out | The session produced distinct word-progress endpoints for 262 of 263 playback updates. |
| Whispersync `ClearOverlay` caused the iota55 failure latch | Ruled out | Overlay clear can interleave with relocation, but it does not own or advance the foreground WebView mutation generation that failed the handoff. |

The back-cover causal assessment applies when the edge is absent only while the
curl surface is presented and returns after handoff. If the edge remains absent
while idle, that observation is a separate defect and must not be attributed to
SurfaceView transparency without new evidence.

## 3. Objectives

The corrected reader shall:

- present each word boundary near its audio timestamp instead of waiting for a
  coarse position poll
- keep semantic text-range and pagination authority in Foliate
- keep PlayLikeCurl as the sole page-deformation renderer
- present transient Whispersync progress without recapturing a full page bitmap
  for every word
- make page-attached highlighting agree with the page currently visible through
  PlayLikeCurl and through the settled WebView
- render the page edge and other page material throughout every curl without
  depending on a transparent SurfaceView hole exposing WebView DOM
- guarantee an accessible stop/reset action whenever synchronized audio is
  playing
- preserve the iota56 in-flight relocation admission fence
- fail closed when a highlight anchor or presentation receipt is stale
- coalesce delayed progress to the newest valid word instead of replaying a
  backlog
- diagnose state, timing, and ownership without recording publication content

## 4. Non-Goals

- Replacing Foliate pagination, layout, navigation, selection, annotations, or
  exact text-position resolution.
- Reimplementing PlayLikeCurl geometry in Compose.
- Capturing or uploading a complete page raster for each spoken word.
- Persisting word rectangles, text fingerprints, sidecar text, hrefs, CFIs, or
  publication identifiers.
- Inferring word timestamps when a sidecar contains only fragment-level timing.
- Making passive chapter prewarm part of Whispersync correctness. The separate
  "resume prewarm after destination deck" task remains independent.
- Reopening the iota56 second-turn admission fix.
- Hiding the regression with a longer animation, fixed sleep, fixed frame count,
  or delayed control reveal.
- Treating DOM z-index as an Android surface-layer contract.
- Requiring reader chrome to be fully expanded before audio can be stopped.

## 5. Authority Model

### 5.1 Foliate authority

Foliate remains the sole authority for:

- publication parsing and spine order
- pagination and exact destination commitment
- DOM text ranges and whether a sidecar endpoint resolves in the current layout
- selection, annotations, links, and semantic reading position
- a current destination receipt after navigation or layout replacement

A sidecar timestamp may select a candidate text endpoint. It may not directly
invent a page, rectangle, locator, or visual ordinal. Geometry used by native
presentation is valid only when derived from a current Foliate range.

### 5.2 Audio timeline authority

The audio player timeline is the authority for current playback position,
playback rate, pause/resume state, seek discontinuities, and track transition.
The word-boundary scheduler predicts a wake-up from that timeline, then verifies
the current player position before publication. A scheduled wall-clock delay is
never itself proof that the boundary is current.

### 5.3 Visible presentation authority

When PlayLikeCurl is active, it remains the sole deformation renderer and owns
the curled leaf, its page material, and its page-attached decorations. At rest,
Foliate's WebView may be exposed only after the existing causal visual-handoff
contract completes. A lightweight native Whispersync decoration plane may sit
above either presentation, but every decoration must carry ownership proving it
belongs to the exact visible page and layout.

Compose remains the authority for global reader chrome, dialogs, and playback
transport. Page-attached visual effects must not be implemented as global
chrome, and playback controls must not be baked into page rasters.

## 6. Required Presentation State

The implementation shall derive, not independently guess, the following state:

```text
ReaderWhispersyncPresentationState
  Disabled
  AwaitingTimeline
  AwaitingCurrentAnchor
  Presenting(anchorReceipt, boundary)
  SuspendedForCurl(anchorReceipt, latestBoundary)
  AwaitingDestination(relocationToken, latestBoundary)
  Paused(anchorReceipt?)
  Failed(reason)
```

The state is governed by these rules:

- `Disabled` owns no timer, anchor, native decoration, or WebView overlay.
- `AwaitingTimeline` may show a loading transport but no stale highlight.
- `AwaitingCurrentAnchor` clears any previous-page decoration before resolving.
- `Presenting` requires a current timeline boundary and current anchor receipt.
- `SuspendedForCurl` may retain one latest valid boundary, not a queue of missed
  boundaries.
- `AwaitingDestination` cannot publish a highlight until Foliate has committed
  the destination and a current anchor has been derived from it.
- `Paused` cancels boundary wake-ups and freezes or clears the current decoration
  according to the existing playback visual policy; it never advances progress.
- `Failed` clears page-attached presentation and leaves a visible recovery or
  stop action. It never leaves audio playing without transport.

The coordinator must derive whether a turn is admissible from the canonical
relocation queue. It must not add a second Boolean that can disagree with
`hasInFlightHead()`.

## 7. Word-Boundary Scheduling

### 7.1 Boundary model

A word boundary is an ephemeral record containing only data required in memory:

```text
WordBoundary
  segmentOrdinal
  wordOrdinalWithinSegment
  audioStartMs
  audioEndMs?
  sidecarEndpoint
```

The endpoint is protected publication data and must never be logged or
persisted. Runtime diagnostics use only opaque monotonic sequence numbers and
result enums.

### 7.2 Scheduling contract

The scheduler shall:

1. Select the next boundary strictly after the verified current player position.
2. Convert audio-time delta to monotonic elapsed time using the current playback
   speed.
3. Hold at most one scheduled wake-up.
4. On wake-up, re-read player position and playback state.
5. Emit the newest boundary whose start is at or before the verified position.
6. Schedule the next future boundary.
7. Cancel and rebuild on seek, track change, playback-speed change, pause,
   stop/reset, media replacement, or session replacement.

The scheduler must not use a 500 ms poll as its timing authority. A low-frequency
health poll may remain for player recovery only if it cannot advance highlight
state. Player events plus monotonic boundary wake-ups drive normal progress.

If execution is delayed across several words, the scheduler emits only the
newest current boundary. It records a privacy-safe coalesced-count metric and
must not animate or apply every missed historical word.

### 7.3 Timing budget

With a stable foreground reader and no active page transition:

- deterministic virtual-clock tests shall dispatch at the exact simulated
  boundary
- scheduler dispatch shall be within 75 ms p95 of the expected boundary on the
  supported Android emulator gate
- visible word progress shall be within 150 ms p95 and 250 ms maximum on the
  supported physical-tablet acceptance session
- no recurring cadence near 500 ms may remain in visible progress

These are measurement gates, not authority mechanisms. A fixed delay may not be
added merely to satisfy the numbers.

## 8. Foliate Anchor Receipts

### 8.1 Ephemeral geometry request

For the current sidecar endpoint, the WebView bridge shall ask Foliate to resolve
the exact DOM range and return page-local geometry for the current layout. The
response may contain bounding rectangles and writing-direction metadata, but it
must not return or log the range's text.

Synthetic fixture text may be used in automated tests. Protected publication
text may not be captured as test output.

### 8.2 Receipt identity

Every geometry response shall be wrapped in a receipt containing at least:

```text
ReaderWhispersyncAnchorReceipt
  foliateSessionId
  publicationSessionGeneration
  layoutGeneration
  viewGeneration
  presentationMutationGeneration
  readerSettingsRasterKey
  spineIndex
  visualPageOrdinal
  destinationCommitToken
  anchorGeneration
  boundarySequence
  pageLocalRects
```

Names may follow existing repository types, but the identity must cover these
invariants. The receipt is valid only when all ownership fields still match the
current reader and when the destination token is the current committed Foliate
destination.

The receipt and rectangles live in memory only. They are cleared on reader
close, publication replacement, renderer detach, or terminal playback reset.

### 8.3 Invalidation

Any of the following invalidates the receipt before another decoration is
published:

- typography, margin, line-height, theme, writing-mode, or pagination change
- orientation, viewport, inset, density, or display-size change
- Foliate session or WebView replacement
- page/spread change
- raster/deck generation replacement
- destination relocation reservation, cancellation, or failure
- mutation-generation change not owned by the current anchor request
- media item, sidecar, seek epoch, or playback session replacement

Invalidation clears the visible decoration synchronously. Re-resolution happens
only after the new Foliate layout and destination are committed. A stale anchor
must never be scaled heuristically onto a new page.

## 9. Page-Attached Highlight Presentation

### 9.1 Native decoration plane

Word highlighting shall use a lightweight page-attached native decoration plane
rather than full-page bitmap recapture. Its input is a validated anchor receipt
and the newest current boundary. Its output is an atomic set of display-space
rectangles, style, opacity, and page ownership.

The plane must:

- transform Foliate page-local rectangles through the exact current page/spread
  display transform
- support portrait, landscape spreads, right-to-left pagination, and vertical
  writing without deriving semantic positions itself
- swap rectangle sets atomically on the render thread
- reject receipts for any non-current page or generation
- clear rather than display uncertain geometry
- allocate bounded reusable geometry or mask buffers
- avoid retaining WebView objects, DOM handles, bitmap bytes, or publication text

The existing WebView overlay may remain as Foliate's semantic confirmation and
settled-WebView fallback, but it is not sufficient as the only visible plane
while a raster is active.

### 9.2 Curl behavior

A highlight belongs to page content. During a curl it must obey the same page
transform and occlusion as the printed text. The implementation may satisfy this
by supplying PlayLikeCurl with a per-leaf highlight mask or renderer-native
rectangles. It may not draw a flat screen-space highlight over a curled page.

At gesture admission, the renderer snapshots the latest validated decoration for
the participating leaf. Word boundaries arriving during the curl are coalesced.
They may update a renderer-supported deformed decoration only when ownership is
unchanged; otherwise they remain suspended. At settlement, the coordinator
waits for the exact destination receipt, resolves the latest boundary on that
destination, and publishes it on the first causally subsequent valid
presentation. It must not replay intermediate words accumulated during the
turn.

A boundary for the source page must never appear on the destination page, and a
boundary for the destination must never appear before destination commitment.

### 9.3 Fragment-level fallback

Capability is explicit:

- `WordTimed`: publish word progress from word endpoints.
- `FragmentTimed`: publish only the exact timed fragment supplied by the
  sidecar.
- `AudioOnly`: do not claim synchronized highlighting.

The reader may retain audio playback for fragment-timed content, but it must not
fabricate word boundaries by evenly dividing a sentence. A current word endpoint
that fails Foliate resolution may fall back to the current validated fragment
only when that fragment has its own exact range receipt. Otherwise the
highlight clears and synchronization reports an actionable failure.

## 10. Page Material And Layer Stack

### 10.1 Logical stack

The reader shall enforce the following bottom-to-top logical stack:

1. app-window background
2. live Foliate WebView and its semantic overlay
3. native page backing and page material
4. page raster plus page-attached Whispersync decoration
5. PlayLikeCurl deformation, fold lighting, shadow, and curled back material
6. transition/preparation shield when valid ownership requires it
7. Compose reader chrome, persistent playback transport, dialogs, and system
   accessibility surfaces

Page-attached decoration participates in the transformed leaf rather than acting
as unrestricted global layer 7 chrome.

### 10.2 Back-cover edge contract

The visible back-cover strip and any fixed paper edge required during a curl
shall be supplied by the native PlayLikeCurl presentation for the complete curl
interval. It may be part of the submitted deck material or an explicit
renderer-owned backing primitive. It may not depend on a transparent region
revealing a WebView DOM element beneath the SurfaceView.

The edge shall:

- remain visible from the first accepted drag frame through final settlement
- retain the same width, color role, and side as the settled Foliate page
- mirror correctly for reading direction and writing mode
- remain stable during slow drags, cancellation, snap-back, and completed turns
- never cover text or global reader chrome

The current one-percent DOM reveal can remain as the settled WebView appearance,
but the native curl must provide an equivalent page-material representation of
its own.

### 10.3 Android surface contract

Android implementation details must satisfy the logical stack:

- an above-window `SurfaceView` that can occlude Compose chrome is prohibited
- changing a DOM z-index is not a valid fix for a SurfaceControl ordering defect
- if a media-overlay surface contains transparent regions, every visible region
  beneath it must be supplied by a layer whose composition order is proven on
  the supported Android versions
- the curl surface's Z mode must remain stable across attach, detach, surface
  recreation, orientation change, and app resume
- the Compose transport must remain visible and hit-testable while the curl
  surface is active
- the layer contract must be verified behaviorally; a source assertion for a
  particular `setZOrder...` call is insufficient by itself

No further one-line Z-order change is acceptable without a test that verifies
edge, curl, WebView, shield, and Compose-control composition together.

## 11. Persistent Playback Transport

### 11.1 Availability invariant

There must be no reader state in which synchronized audio is playing while the
user lacks an accessible stop/reset action.

When playback is active:

- a compact transport remains composed above native reader presentation even
  when normal reader chrome is hidden
- its primary action dispatches the current `StopAndReset` behavior, or an
  explicitly modelled pause action if the product state is changed later
- it is not gated by whether the current page has a confirmed cue
- it is not hidden by a transient `SeekingAudio`, loading, or anchor-resolution
  state
- it has a stable accessibility description and minimum touch target
- its hit target takes priority over page-turn gesture interception
- the expanded reader menu may reposition or duplicate the affordance, but is
  not required to reach it

When playback is available but stopped, the compact start control may follow the
existing capability and confirmed-cue policy. The stronger invariant applies
once audio is playing.

### 11.2 Shell and capability transitions

If shell-cover activation, engine replacement, or capability loss would remove
the transport, the reader must first stop/reset playback or keep a transport
above that presentation. It may not hide the control while leaving audio active.

A state transition to `ReaderWhispersyncStatus.Unavailable` during active audio
is an error transition, not a normal visibility decision. It must be logged as a
privacy-safe state reason and end in either visible recovery controls or stopped
audio.

### 11.3 Runtime distinction for the current regression

Diagnostics must distinguish these outcomes without recording content:

- control state was not composed because status was unavailable
- control was suppressed by shell-cover or capability state
- control was composed and visible according to Compose
- pointer reached the control and dispatched `StopAndReset`
- pointer was intercepted before the control
- command reached the playback manager

This evidence is required before assigning the current control disappearance to
Whispersync policy or SurfaceView composition. Menu visibility alone must not be
used as a causal explanation because current source only changes placement.

## 12. Deterministic Interleavings

### 12.1 Boundary during idle presentation

A valid boundary resolves against the current Foliate destination, publishes one
atomic rectangle set, and replaces the prior boundary. No raster recapture or
page relocation occurs.

### 12.2 Multiple boundaries during a curl

The source-page decoration remains correctly transformed or is suspended. The
coordinator retains only the newest timeline boundary. On settlement it resolves
that boundary against the committed destination and publishes one result. It
never applies the missed sequence in a burst.

### 12.3 Page turn during finalizing handoff

The turn is rejected or held by the existing
`ReaderPageRelocationQueue.hasInFlightHead()` admission fence. Highlight work may
coalesce, but it may not complete, cancel, or supersede the relocation's visual
handoff ownership.

### 12.4 Audio crosses a page boundary

The coordinator reserves an exact Foliate relocation if audio-follow policy
requires navigation. It clears source geometry, waits for the destination commit
receipt and causal presentation acknowledgement, resolves the current endpoint
in the new layout, and then publishes. Audio highlight cannot use a predicted
visual ordinal as proof.

### 12.5 Seek and playback-speed change

A seek increments a playback epoch, cancels the prior wake-up, clears stale
geometry, selects the newest boundary at the verified target, and resolves it.
A speed change reschedules from current player position without changing
semantic endpoints.

### 12.6 Pause, resume, stop, and reset

Pause cancels the timer and prevents progress. Resume verifies current position
before scheduling. Stop/reset cancels all timers, clears native and WebView
highlight state, releases ephemeral anchors, and leaves no pending bridge
command able to restore the old decoration.

### 12.7 Layout or viewport invalidation during playback

The reader clears the decoration before applying the invalidation. It waits for
the new Foliate layout and current destination receipt, then resolves the newest
current boundary. Old rectangles are never rescaled into the new viewport.

### 12.8 Surface loss or app backgrounding

Surface loss clears renderer-owned decoration and preserves only non-sensitive
playback sequence state needed for recovery. On resume, the control becomes
available before synchronized playback can continue. Native presentation is
restored through normal generation and handoff proofs, not a timer.

## 13. Failure And Recovery

### 13.1 Anchor resolution failure

If Foliate cannot resolve the current endpoint or reports non-visible geometry:

- clear any prior-page decoration
- reject the receipt
- avoid advancing confirmed active-overlay state
- use an exact fragment fallback only when independently valid
- otherwise pause synchronized following and expose recovery through the
  persistent transport/player

The reader must not preserve a plausible-looking stale word highlight.

### 13.2 Presentation rejection

Generation mismatch, wrong page, stale destination token, renderer detach, or
mutation supersession rejects the update without changing current presentation.
The coordinator re-evaluates only the newest boundary against current state.
Repeated rejection must not grow a command queue.

### 13.3 WebView painter rejection

`overlayFragmentInactive` after an apply request is a semantic painter failure,
not proof of surface occlusion. It clears native decoration for that receipt and
follows the existing pause/recovery policy. Diagnostics record the rejection
class but not the endpoint, text, locator, or painter payload.

### 13.4 Renderer presentation failure

Existing fail-closed handoff ownership remains. A renderer failure may block page
input, but it must not hide active audio transport. Recovery cannot clear the
failure latch until the current presentation generation is rebuilt and proven.

### 13.5 Missed timing deadline

A late scheduler wake-up coalesces to current progress and increments bounded
privacy-safe timing counters. It does not replay history, skip generation checks,
or shorten the next interval below its actual audio timestamp.

## 14. Diagnostics And Privacy

### 14.1 Permitted diagnostics

The implementation may record:

- opaque session-local sequence numbers
- playback state and speed category
- scheduled-versus-observed timing delta
- count of coalesced boundaries
- anchor result enum and rectangle count
- current/non-current generation equality as Booleans
- presentation-state transition and rejection reason enum
- page-plane state such as idle, curl, handoff, or shield
- control composed/visible/hit/command-dispatched state
- overlay active/inactive event type
- surface Z classification and lifecycle transition

Counters must be bounded and reset with the reader session.

### 14.2 Prohibited diagnostics and persistence

The implementation must not log, persist, upload, or place in test reports:

- EPUB text or text fragments
- word endpoints, sidecar transcript content, or timestamps paired with text
- hrefs, URLs, CFIs, selectors, or DOM serialization
- book IDs, publication identifiers, user identifiers, or annotations
- selected text or notes
- range fingerprints, even when hashed
- raster pixels, bitmap bytes, highlight masks, screenshots, videos, or visual
  derivatives
- credentials, tokens, or protected environment paths

Anchor receipts and geometry are ephemeral memory only. Local physical evidence
remains under an immutable `.codex-validation` evidence root and is never
committed.

## 15. Test Requirements

### 15.1 Pure scheduler tests

Use a virtual monotonic clock and fake player timeline to prove:

- exact dispatch at consecutive word boundaries
- playback-speed conversion
- pause cancels and resume rebuilds one wake-up
- seek invalidates the old playback epoch
- delayed execution coalesces to the newest boundary
- track replacement cannot publish an old boundary
- health polling cannot advance highlight state

These tests must not depend on real sleeps.

### 15.2 Anchor and ownership tests

Using synthetic content, prove:

- current Foliate range produces page-local rectangles and a complete receipt
- wrong session, layout, view, mutation, raster, page, destination, or boundary
  generation is rejected
- typography and orientation invalidation clear before re-resolution
- source-page receipt cannot paint the destination page
- destination receipt cannot paint before Foliate commit
- stop/reset prevents late bridge acknowledgement from restoring decoration
- receipts and rectangles are not serialized or logged

### 15.3 Presentation coordinator tests

Prove the full state transitions for:

- idle word advance
- boundary during curl
- several boundaries during curl with one newest result after settlement
- audio-follow page boundary during exact relocation
- relocation failure and renderer failure
- painter inactive response
- layout replacement during playback
- background/resume and surface recreation

Use actual queue/coordinator types where possible. Source-string tests alone do
not establish lifecycle behavior.

### 15.4 Layer and input tests

Android host/instrumented tests shall prove:

- Compose transport is above and hit-testable over an active curl surface
- the page-material edge is supplied during every curl frame, including
  transparent renderer regions
- slow drag, cancellation, snap-back, and completed turn preserve the edge
- surface recreation preserves the same logical ordering
- shell-cover and preparation shields do not strand active playback without a
  control
- a transport tap is not interpreted as a reader page gesture

The test contract names logical layers and observable behavior. It must not lock
the implementation to `setZOrderMediaOverlay` if another Android primitive
satisfies the same contract more reliably.

### 15.5 JavaScript bridge tests

With synthetic fixture content, prove:

- a word endpoint resolves to the intended exact range and rectangles
- invisible or detached ranges reject cleanly
- stale anchor request IDs cannot activate an overlay
- active/inactive acknowledgements correspond to actual painter outcomes
- no bridge diagnostic contains source text or endpoint payload

### 15.6 Regression preservation

The existing iota56 tests for unresolved visual-handoff admission remain
mandatory. Full reader verification must continue to prove that a second turn
cannot supersede an in-flight relocation and that accepted content waits for
committed WebView exposure.

## 16. Emulator Acceptance

A frozen ReaderDev APK built from one tested commit shall pass focused portrait
and landscape probes using synthetic or approved fixture content:

- enable synchronized playback with immersive chrome hidden
- verify the compact transport remains visible and can stop/reset playback
- verify word boundaries advance without a 500 ms staircase
- perform a slow forward curl and confirm the page-material edge remains present
- cross several word boundaries during the curl and confirm only current progress
  appears after settlement
- cancel a curl and confirm source-page highlight and edge recover correctly
- rotate or replace typography during playback and confirm no stale rectangle is
  shown
- perform consecutive turns and confirm zero `PresentationFailed` and zero
  `RejectedRendererUnavailable` outcomes caused by handoff supersession

Acceptance uses privacy-safe event and timing metrics. Visual automation uses
synthetic fixtures and may not OCR or export protected publication content.

## 17. Physical-Tablet Acceptance

After local and emulator gates pass, install the signed production APK over the
existing app with data preserved. Physical-device access requires explicit
thread-scoped ownership. Do not clear Logcat, inject unrelated gestures, or touch
another connected device.

Run a focused session, not a chapter-by-chapter barrage:

1. Open the known word-timed publication and begin synchronized playback.
2. Hide reader chrome and verify a visible, accessible stop/reset transport.
3. Confirm at least 30 consecutive word boundaries track speech within the
   timing budget without batching.
4. Perform slow forward and backward curls while playback crosses boundaries.
5. Confirm the back-cover edge remains present through drag, settle, and
   cancellation.
6. Confirm no source-page highlight appears on the destination and no historical
   word sequence is replayed after settlement.
7. Perform ten consecutive mixed-direction turns and confirm no recurrence of
   the iota55 failure latch.
8. Change orientation and one typography setting during playback; verify stale
   geometry clears and current progress returns only after the new layout commits.
9. Stop/reset playback from immersive reading without first opening the full
   reader menu.

Required privacy-safe evidence includes timing distributions, state-transition
counts, command outcomes, presentation failures, input rejections, and
SurfaceFlinger layer classification. Screenshots and recordings are not required
for this investigation and protected content must not be captured.

## 18. Release Gates

A production release is permitted only when:

- focused RED/GREEN tests for each corrected mechanism were observed
- scheduler, anchor, coordinator, JavaScript, Android host, and focused
  instrumented gates pass
- the complete reader host gate passes with generated manifest prerequisites
- Android build and lint gates required by the repository pass
- emulator acceptance passes from a frozen APK tied to the tested commit
- focused physical-tablet acceptance passes without protected-content evidence
- no regression appears in exact relocation admission or WebView handoff
- the release APK is signed with the persistent GitHub-managed production
  certificate
- release commit, version, APK SHA-256, and signer certificate SHA-256 are
  recorded and verified after download

A release must not be approved solely because the icon is visible in one static
frame. Edge continuity, control hit testing, boundary timing, causal handoff, and
stale-anchor rejection are separate gates.

## 19. Implementation Boundaries

The implementation plan should preserve focused responsibilities rather than add
more policy to the existing large controller.

Expected responsibility boundaries are:

- `ReaderWhispersyncSyncCoordinator.kt`: semantic synchronization decisions and
  current sidecar boundary selection; no Android drawing
- `ReaderWhispersyncPlaybackPolicy.kt`: transport availability and command
  policy; active playback cannot yield an invisible control
- a focused common scheduler type: virtual-clock-testable word-boundary timing
- a focused anchor/receipt type: identity, validity, and ephemeral geometry
- a focused presentation coordinator: curl, handoff, invalidation, and coalescing
- `FoliateEpubEngineAdapter.kt`, `ReaderEngineWebViewHost.android.kt`, and
  `navic-reader.js`: exact range/geometry request and acknowledgement
- a focused Android page-decoration host: native rectangle/mask presentation and
  display transforms
- `ReaderPlayLikeCurlFoliateController.android.kt` and the PlayLikeCurl adapter:
  curl lifecycle and renderer decoration snapshot, without owning text semantics
- `ReaderPlayLikeCurlFoliateRasterSource.android.kt`: complete page-material
  inputs, including the edge required under transparent surface regions
- `KomikkuReaderNativeFrameHost.android.kt`: enforce logical surface/chrome/shield
  order and pointer priority
- `ReaderRoot.kt` and `ReaderWhispersyncStatusBadge.kt`: persistent compact
  transport and expanded-player entry
- `navic-reader-paper-surface.js` and
  `navic-reader-page-turn-preview.js`: settled-page geometry and matching native
  page-material metadata, not Android Z-order policy

If PlayLikeCurl requires a renderer hook for a transformed highlight mask, that
hook belongs in the imported renderer boundary and must remain generic
page-decoration input. It must not depend on Whispersync sidecar or Foliate
classes.

The implementation plan should split scheduling, anchor receipts, page-attached
presentation, edge material, and persistent transport into independently tested
checkpoints. Surface ordering and page material must be corrected as one
behavioral contract rather than as unrelated Z-order tweaks.

## 20. Acceptance Summary

The work is complete only when all of the following are true:

- [ ] Current word-timed content advances visibly at word boundaries within the
      stated timing budget.
- [ ] Delayed execution coalesces to current progress and never replays a visible
      backlog.
- [ ] No word update requires full-page raster recapture.
- [ ] Every highlight is backed by a current Foliate anchor and destination
      receipt.
- [ ] Highlight is page-attached and transforms or suspends correctly during a
      curl.
- [ ] No source-page highlight appears on a committed destination page.
- [ ] The native page-material edge remains visible throughout every curl path.
- [ ] Compose chrome and persistent playback transport remain above native page
      presentation.
- [ ] Active playback always has an accessible stop/reset action with immersive
      chrome hidden.
- [ ] Menu visibility is no longer required to reach playback transport.
- [ ] Layout, viewport, session, deck, and playback invalidation clear stale
      anchors before reuse.
- [ ] The canonical in-flight relocation fence continues to prevent overlapping
      visual handoffs.
- [ ] Painter failure, renderer failure, and capability loss fail closed without
      stranding active audio.
- [ ] Diagnostics distinguish timing, anchor, layer, and control failures without
      protected content.
- [ ] Local, emulator, and focused signed physical-tablet gates pass from the
      exact release commit.
