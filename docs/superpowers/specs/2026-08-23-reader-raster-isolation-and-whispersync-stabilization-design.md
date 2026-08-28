# Reader Raster Isolation And Whispersync Stabilization Design

**Status:** Approved architecture; implementation pending

**Date:** 2026-08-23

**Audit baseline:** `1a0d8d2b7c1b79eb9778685dfc805dc24e80472a`
(`v1.0.11-iota63`, `versionCode 590`)

**Related specifications:**

- `docs/superpowers/specs/2026-07-17-reader-playlikecurl-raster-readiness-design.md`
- `docs/superpowers/specs/2026-08-11-reader-whispersync-playlikecurl-presentation-design.md`
- `docs/superpowers/specs/2026-08-22-reader-live-authority-restoration-design.md`

This specification replaces the shared-foreground-WebView passive-capture model
as Navic's steady-state architecture. It retains the combined live Foliate
presentation-and-commit authority snapshot introduced for iota63, preserves
PlayLikeCurl as the sole deformation renderer, and defines the remaining
Whispersync, preparation recovery, and curl-composition corrections as one
stabilization program.

The implementation must remain test-driven. It must not infer EPUB semantic
positions in native code, recapture a complete page for every highlighted word,
or hide a failed lifecycle behind retries that resubmit stale state.

## 1. Verified Problems

Production testing of iota63 established five independent defects and one shared
architectural cause.

### 1.1 Shared capture invalidates live authority

The foreground Android WebView currently contains both the live Foliate view and
a passive preview Foliate view. Passive raster capture hides the live
composition, exposes the preview, clears the live receipt, captures, and then
restores the live composition. `ReaderForegroundWebViewOwnership` serializes
those mutations and correctly fails closed, but it cannot make the shared
mutation non-mutating.

Consequently, valid cue and progress events can continue while the native
highlight has no current live receipt or anchor. Restoration patches can repair
one invalidation edge, but the next intentional passive capture can invalidate
live authority again.

### 1.2 Playback eligibility is coupled to visible highlighting

Manual Stop clears the active media overlay. The UI currently treats that
overlay as evidence that playback is available. Start then becomes unavailable
until a page event reconstructs a cue. Visual presentation state is therefore
incorrectly serving as playback-target state.

### 1.3 Maintenance events can seek audio

Reader-originated `locationChanged` and `visibleTextRange` events produced while
settling presentation are not reliably distinguished from deliberate user
navigation. The reducer can translate those generic range events into a text-to-
audio seek. Near the end of the second visible page this creates a deterministic
loop back to the first cue of the spread, clears/reapplies highlighting, and
restarts the audio.

### 1.4 Retry reuses poisoned preparation state

A raster-preparation failure can leave the current attempt, deck, callbacks, or
capture state unusable. Retry currently submits replacement work into that same
failed lifecycle and returns to `raster-preparation-failed`. Fully closing and
reopening the publication succeeds because teardown creates fresh state that
Retry does not.

### 1.5 Curl material is not an atomic presentation input

The back-cover strip, borders, and paper backing are not guaranteed to be owned
by the native curl presentation for the complete animation. Transparent or
unpopulated regions can depend on the WebView below, causing material to vanish
or turn black while a curl is active.

### 1.6 Individual cue mismatch remains lower priority

Some word/sentence jumps may still originate in WordSync data or exact endpoint
mapping. That investigation is not allowed to delay lifecycle, looping,
preparation, retry, or curl-frame correctness. Exact cue alignment becomes a
release blocker only after the stable presentation path can show which layer is
wrong.

## 2. Objectives

The corrected reader shall:

- keep one live Foliate WebView as the sole semantic, pagination, visible-range,
  DOM-range, and committed-destination authority
- move off-screen raster preparation to a dedicated passive Android WebView and
  Foliate session
- admit passive rasters only through an immutable live-issued capture manifest
  and strict non-semantic parity proof
- prevent passive capture from changing live WebView composition, mutation
  generation, receipt, anchor, overlay, or committed location
- separate user playback intent, prepared visible-page target, engine state, and
  active visual highlight
- make manual Stop durable and make Start available again on the same spread
- prepare audio for each user-committed destination and resume only when the
  authoritative interaction contract permits it
- prohibit generic maintenance events from seeking or restarting audio
- rebuild failed raster preparation in a fresh generation
- submit page raster, page material, geometry, and highlight ownership as one
  coherent PlayLikeCurl presentation
- preserve exact WordSync when valid and progressive `cue-v1-dom-utf16` fallback
  when exact initialization or endpoint resolution fails
- pause after the final sentence intersecting the visible spread without turning
  the page
- retain privacy-safe evidence sufficient to identify the failing subsystem

## 3. Non-Goals

- Replacing Foliate parsing, pagination, layout, navigation, selection, or DOM
  range resolution.
- Reimplementing PlayLikeCurl deformation in Navic or Compose.
- Allowing the passive WebView to become reader or playback authority.
- Computing EPUB locators, pages, ranges, or cue positions in native code.
- Baking transient highlights into persistent page rasters.
- Capturing a complete page bitmap for each word boundary.
- Keeping shared-foreground preview capture as a silent production fallback.
- Reopening isolated backend cue mismatches before the stable frontend can
  distinguish data errors from presentation errors.
- Expanding physical acceptance into a chapter-by-chapter barrage.
- Changing the persistent chapter-raster scheduling policy defined by the 2026-07-17
  raster-readiness specification. This design changes where capture runs and how
  it is admitted.

## 4. Authority And Ownership Invariants

### 4.1 Live Foliate authority

The live WebView is the sole source of:

- publication and spine interpretation
- current pagination and layout
- visible content range
- exact committed destination
- DOM ranges and page-local highlight geometry
- live presentation receipt
- Whispersync overlay acknowledgements and active-anchor republication

The iota63 atomic authority operation remains mandatory: Foliate validates one
live target and returns its presentation receipt, canonical text-page commit,
and active anchor from the same JavaScript authority snapshot. Native code may
compare identities and transform validated page-local geometry, but may not
reconstruct semantic positions.

### 4.2 Passive raster authority

The passive WebView is a renderer worker only. It may:

- open the same controlled publication
- apply an exact live-issued raster profile
- commit an opaque live-issued capture target
- render page pixels
- return a passive receipt and bitmap

It may not:

- publish reader location or visible-range events
- publish media-overlay, WordSync, selection, or navigation events
- issue a live presentation receipt
- update playback state
- claim or replace the live committed destination
- expose a general `NavicReaderBridge` semantic API

A passive receipt proves only that a raster matches its manifest. It can never be
promoted into live authority.

### 4.3 PlayLikeCurl authority

PlayLikeCurl remains the sole deformation renderer. It owns the active leaf,
fold, back material, shadows, and page-attached highlight transformation during
a curl. It consumes only admitted immutable presentation inputs; it does not
interpret EPUB semantics.

### 4.4 Compose authority

Compose owns reader chrome, the Start/Stop affordance, error/retry surfaces, and
accessibility. These controls remain above the native page presentation and
must not be baked into a page raster.

## 5. Target Component Boundaries

### 5.1 Live reader host

The existing live Android WebView continues to host the full reader runtime. It
may capture the currently committed live surface when the current page itself
needs a snapshot. It does not expose or hide a passive preview for prewarm,
repair, or background capture.

### 5.2 Passive raster capture host

A focused Android host owns the passive WebView lifecycle:

- attached off-screen sizing at the exact reader viewport
- initialization and publication opening
- one active capture at a time
- pause/resume and cancellation
- profile/session replacement
- low-memory eviction
- teardown

The host exposes a raster-only port. No semantic event callback is wired from
this WebView into the reader reducer.

### 5.3 Passive JavaScript runtime

A minimal passive asset shares only the Foliate and profile helpers necessary to
commit and capture a manifest target. It omits playback, WordSync, selection,
reader navigation publication, live receipts, and location publication.

### 5.4 Raster admission boundary

`ReaderPageTurnBundleSource` remains the single admission point for live current-
page captures, passive captures, cache entries, and PlayLikeCurl decks. It must
reject a stale or mismatched passive result without mutating the live WebView,
clearing a live anchor, or launching restoration navigation.

### 5.5 Narrowed foreground ownership

After migration, `ReaderForegroundWebViewOwnership` governs only genuine live
mutations such as user navigation, settings/layout replacement, and exact live
settlement. Passive raster preparation no longer acquires its leases or advances
its mutation generation.

## 6. Passive Capture Manifest Contract

### 6.1 Manifest

The live runtime may issue a `PassiveRasterCaptureManifest` only from a current
canonical live commit. Names may follow repository conventions, but the manifest
must carry enough immutable identity to validate:

```text
PassiveRasterCaptureManifest
  manifestSequence
  captureEpoch
  liveFoliateSessionId
  publicationSessionGeneration
  destinationCommitToken
  opaqueCaptureTarget
  visualPageOrdinal
  rasterProfileKey
  paginationFingerprint
  layoutFingerprint
  decorationFingerprint
  viewportAndCaptureGeometry
  rasterGeneration
```

`opaqueCaptureTarget` is created and interpreted by Foliate JavaScript. Native
code relays and compares it as opaque data. The manifest, target, and commit token
are ephemeral and must not be logged or persisted.

### 6.2 Passive receipt

The passive runtime returns:

```text
PassiveRasterCaptureReceipt
  passiveSessionId
  echoedManifestIdentity
  observedCaptureTarget
  observedVisualPageOrdinal
  observedRasterProfileKey
  observedPaginationFingerprint
  observedLayoutFingerprint
  observedDecorationFingerprint
  observedViewportAndCaptureGeometry
  passiveCommitSequence
```

The bitmap and receipt are one result. A receipt without its bitmap, or a bitmap
without its receipt, is never admitted.

### 6.3 Admission

Native admission requires exact equality for every manifest/receipt identity,
the current capture epoch, the active publication generation, and the current
raster generation. The bundle source also applies existing physical geometry,
cache, and deck-generation checks.

Any mismatch:

- rejects the result
- releases its bitmap and capture lease
- leaves live authority untouched
- records only a privacy-safe rejection enum and equality Booleans
- re-evaluates only through a newly issued manifest if the target is still needed

### 6.4 Parity gate

Production routing is prohibited until a prototype proves:

- reliable attached off-screen WebView capture at full reader dimensions
- exact pagination/profile/geometry parity with the live session
- deterministic stale-result rejection across settings, font, orientation, and
  session changes
- acceptable memory, CPU, and thermal cost on supported hardware
- no path from the passive runtime to live semantic event handling

A parity failure is an architectural blocker, not permission to silently restore
the shared-foreground capture path.

## 7. Whispersync State Model

The reader must represent these concepts separately:

```text
playbackIntent
  UserStopped
  Enabled

transportPhase
  Unavailable
  Preparing
  Ready
  Playing
  BoundaryPaused
  Seeking
  Failed

preparedVisibleTarget
  destinationCommitIdentity
  firstVisibleCue
  preparationGeneration

activePresentationCue
  current cue/boundary
  current live anchor receipt
  current native mask ownership
```

`activePresentationCue` is visual state. Its absence cannot make a valid
`preparedVisibleTarget` unavailable.

### 7.1 Start

Start is available when the current spread has a prepared visible target,
regardless of whether an active highlight exists. Starting shall:

1. retain or set `playbackIntent = Enabled`
2. verify the target against the current committed live destination
3. publish the initial valid exact or progressive highlight proof
4. seek/confirm audio at the prepared cue
5. begin playback

A bridge `overlayFragmentActive` event is not by itself visual success. Native
mask admission must match the current live receipt and presentation bundle.

### 7.2 Manual Stop

Manual Stop shall:

1. set `playbackIntent = UserStopped`
2. stop playback and cancel pending boundary work
3. clear active native and WebView highlighting
4. prepare/seek audio to the first cue of the current visible spread
5. preserve the prepared target so Start remains available on the same spread

A page turn may replace the prepared target while stopped, but may not re-enable
intent or start audio.

### 7.3 Page-boundary pause

After the final sentence intersecting the visible spread finishes, playback
enters `BoundaryPaused` while `playbackIntent` remains `Enabled`. Audio never
turns the page. The next manual page turn resumes only after curl settlement,
live destination commit, target preparation, and destination presentation proof.

### 7.4 Manual page turn

At an accepted turn, active audio is suspended without changing enabled intent.
On cancellation, the valid source target resumes if intent remains enabled. On
commit, the reader prepares the first corresponding cue for the committed
destination. It plays only after the curl and live destination presentation have
both completed and only if intent remains enabled.

### 7.5 Explicit content seek

While intent is enabled, an explicit sentence/word interaction may seek to its
Foliate-derived cue and update the highlight. Ordinary taps, drags, range
publication, layout settlement, and raster preparation are not explicit cue
selection.

## 8. Relocation And Event Provenance

Every event capable of preparing or seeking audio must carry typed causal
provenance. The minimum intents are:

```text
UserNavigation
ExplicitCueSelection
PresentationMaintenance
AudioProgress
```

- `UserNavigation` is issued by a real user navigation action and is consumed by
  the causally matching committed destination.
- `ExplicitCueSelection` is issued only by the sentence/word interaction path.
- `PresentationMaintenance` covers same-destination settlement, live-authority
  rearming, layout recomposition, raster admission, preparation, and repair.
- `AudioProgress` advances only the current cue/highlight.

A generic `visibleTextRange` or `locationChanged` event updates observed reader
state but cannot seek audio by itself. The reducer may prepare or seek only when
a current one-shot user intent causally matches the committed destination.
Unknown or stale provenance fails closed as maintenance.

Internal settlements must not synthesize user provenance. A `reader` event-source
string alone is insufficient evidence of user intent.

This contract forbids the observed loop:

```text
audio progress
  -> internal live/raster settlement
  -> generic visible-range event
  -> text-to-audio seek
  -> first cue of spread
```

## 9. Preparation Generations And Retry

Every preparation attempt owns a monotonic generation and cancellation scope.
Callbacks, manifests, bitmaps, cache admissions, and deck submissions carry that
generation.

### 9.1 Failure

A failed generation is terminal. Its callbacks may release resources but cannot
publish state, submit a deck, clear current live authority, or become Ready.

The current visible content remains intact behind an honest preparation/recovery
surface. A failed or rebuilding curl deck must not expose a black page.

### 9.2 Retry

Retry shall:

1. cancel and retire the failed generation
2. release its pending capture results and replacement deck
3. discard only cache entries proven corrupt or mismatched
4. recreate the passive capture session if its health failed
5. allocate a new preparation generation
6. request fresh manifests from the current live commit
7. rebuild and admit a complete current deck
8. publish Ready only after the new generation is proven

Retry preserves the live publication session, committed location, user playback
intent, and valid prepared audio target where their authority remains current.
It must not require closing and reopening the eBook.

Repeated Retry actions while one fresh attempt is active coalesce into that
attempt; they do not create overlapping generations.

## 10. Atomic Curl Presentation

Each submitted PlayLikeCurl presentation must bind:

```text
ReaderCurlPresentationBundle
  deckGeneration
  admitted page raster receipts
  physical page/spread geometry
  reading direction and page roles
  front paper material
  back-cover/back-page material
  fixed border/edge material
  clipping/background behavior
  page-attached highlight mask ownership
```

A bundle cannot mix a raster from one generation with geometry, material, or
highlight ownership from another.

### 10.1 Backing and borders

The native curl presentation supplies every visible paper, back-cover, border,
and edge region from the first accepted drag frame through cancellation or final
settlement. It must not depend on a transparent SurfaceView region revealing a
DOM element below. Unused regions render the specified page material rather than
black.

### 10.2 Highlight updates

Word progress updates only a bounded page-local mask or renderer-native rectangle
set. It never recaptures the page raster. The mask carries the live receipt,
destination commit, deck generation, and boundary generation needed for strict
admission.

During a curl, the highlight deforms and clips with its leaf, or is suspended if
the renderer cannot update it without violating ownership. Boundaries are
coalesced to the newest valid value. Source-page highlighting cannot appear on
the destination page, and historical boundaries are never replayed after
settlement.

### 10.3 Surface and chrome

Compose chrome, Start/Stop, Retry, and accessibility remain above the curl
surface and hit-testable. Surface recreation, orientation changes, and app resume
must restore the same logical ordering.

## 11. Core Data Flows

### 11.1 Open and prepare

1. Live Foliate opens and commits the current destination.
2. Live Foliate issues manifests for required off-screen rasters.
3. Passive Foliate captures them without touching live composition.
4. The bundle source strictly admits matching results.
5. PlayLikeCurl receives one atomic presentation bundle.
6. Live Foliate supplies the current visible cue and anchor.
7. Start becomes available when the visible target is prepared.

### 11.2 Background prewarm

1. Scheduling requests a new manifest from the current live commit/profile.
2. Passive capture runs independently.
3. Admission updates only raster/cache/deck preparation state.
4. Live receipt, anchor, overlay, location, and foreground mutation generation do
   not change.

### 11.3 User page turn while enabled

1. A user-navigation token is created at accepted gesture admission.
2. Audio suspends while the curl uses its immutable bundle.
3. PlayLikeCurl settles the destination.
4. Live Foliate performs the exact destination commit.
5. The matching user-navigation token permits preparation of the destination cue.
6. Atomic live authority and native presentation proofs complete.
7. Audio resumes; no generic range event can trigger another seek.

### 11.4 User page turn while stopped

The same destination preparation occurs, but `UserStopped` remains durable and
audio does not resume. Start plays from the newly prepared destination.

### 11.5 Page end

The final intersecting sentence completes. The next wholly outside cue causes
`BoundaryPaused`. No relocation is initiated. A manual page turn follows the
flow above.

## 12. Failure And Degradation Rules

- **Passive host unavailable:** keep the current live page and truthful recovery
  UI. Do not mutate the live WebView through a hidden production fallback.
- **Passive parity mismatch:** reject and reissue only from a current live
  manifest; repeated mismatch fails preparation visibly.
- **Live authority absent:** clear native highlight and obtain authority through
  the atomic live operation. Do not use passive proof or native semantic
  reconstruction.
- **Exact WordSync unavailable:** use progressive `cue-v1-dom-utf16`; do not paint
  a whole sentence for the cue lifetime.
- **No resolvable exact or progressive cue:** leave audio capability truthful and
  expose a privacy-safe synchronization failure; do not display stale geometry.
- **Preparation failure:** keep the current page visible and offer fresh-generation
  Retry.
- **Renderer failure:** stop admitting gestures, retain Start/Stop access, and
  rebuild the renderer generation before clearing failure.
- **Unknown event provenance:** update observation only; never seek.
- **Passive resource pressure:** evict or suspend passive work before sacrificing
  the live reader. Recreate passive state later from a fresh manifest.

## 13. Diagnostics And Privacy

Permitted diagnostics include:

- session-local generations and sequence numbers
- manifest/receipt equality Booleans and rejection enums
- live/passive lifecycle state
- capture, admission, preparation, and retry outcome enums
- playback intent and transport phase
- event provenance and whether a user token matched
- overlay event type, anchor-present Boolean, rectangle count, and mask-admission
  result
- curl bundle generation and material-presence Booleans
- bounded timing and resource counters

The implementation must not log, persist, upload, or include in test reports:

- EPUB or transcript text
- selected text or annotations
- hrefs, URLs, CFIs, selectors, locators, or opaque capture targets
- book, publication, user, or destination identifiers
- credentials, environment paths, or tokens
- raster pixels, bitmap bytes, masks, screenshots, videos, or visual derivatives

Manifest targets, receipts, DOM ranges, and page-local geometry remain ephemeral
in memory. Any approved local visual evidence remains uncommitted under
`.codex-validation`; protected paired-book acceptance uses privacy-safe counters
and human observation without OCR.

### 13.1 Production cue-map diagnostics

The production reader provides an opt-in cue-map control beside the existing eye
control whenever a Whispersync sidecar is available. This is not restricted to
ReaderDev: a tablet user must be able to capture an actionable report from the
same signed build that exhibits a cue jump.

Each parsed cue retains its raw sidecar ordinal before filtering or sorting. For
the same sidecar revision, that ordinal is stable across sessions and devices. A
short content-free sidecar revision digest disambiguates reports after sidecar
regeneration without exposing a book or publication identifier.

When enabled, the map projects only cues intersecting the current Foliate-owned
visible text range through the production normalization, DOM-range, and
page-local overlay path. Each cue start receives a custom tiny circled ordinal,
visually comparable to `℗` with the number replacing `P`. The marker is offset
from the text baseline and retains its ordinal while mapped, prepared/requested,
audio-active, and rendered-highlight states receive distinct stroke/fill styles.
The implementation cannot use a parallel semantic mapper, full-page raster
capture, or passive-session publication.

A bounded in-memory transition trail may retain only cue ordinals, sidecar
revision digest, state enums, generations, and causal outcome enums. It exists to
make a forward-cue-then-back sequence reportable without EPUB text, hrefs, CFIs,
backend cue IDs, or publication identifiers.

While the map is enabled, pressing over a cue starts a roughly one-second
progress ring at the touch point. Releasing early, leaving touch slop, pointer
cancellation, chrome interception, or curl start cancels the request. Completion
seeks the exact Foliate-resolved cue. A still-pending transport acknowledgement
changes the ring to indeterminate and cannot issue a duplicate seek.

Markers and pending holds are cleared and rebuilt on destination, layout,
profile, orientation, sidecar-revision, or presentation-generation replacement.

## 14. Automated Verification

Test-first coverage must prove at least:

### 14.1 Playback and provenance

- Stop leaves Start available on the same spread and resets to its first cue
- Stop remains durable across a page turn
- enabled boundary pause resumes only after a manual turn and destination commit
- explicit cue selection seeks while enabled
- maintenance and audio-progress events cannot seek
- stale/unknown user-navigation tokens cannot seek
- a generic reader-origin visible-range event cannot restart the spread
- active highlight absence does not remove a prepared target

### 14.2 Passive isolation

- passive capture has no semantic event channel
- manifest creation requires a current canonical live commit
- every receipt field is strictly validated
- stale session/profile/orientation/settings/generation results are rejected
- passive prewarm does not change live mutation generation, receipt, anchor,
  overlay, or location
- passive receipt cannot satisfy live authority APIs
- current-live capture remains distinct from off-screen passive capture

### 14.3 Preparation and Retry

- a failed generation cannot publish late callbacks or decks
- Retry creates a new generation and fresh manifests
- Retry recreates an unhealthy passive session
- duplicate Retry input coalesces
- valid cache entries survive targeted recovery
- successful Retry reaches Ready without publication reopen

### 14.4 Curl presentation

- every accepted bundle has matching raster, geometry, material, and mask ownership
- missing/mismatched material rejects before animation
- borders and back material are defined for forward, backward, cancellation, and
  settlement frames
- highlight masks update without raster capture
- source and destination masks cannot cross deck ownership
- chrome controls remain hit-testable over the active surface

### 14.5 Regression preservation

- iota63 atomic live authority and active-anchor republication tests remain green
- exact WordSync and progressive cue fallback remain green
- page-spanning final sentence completes before outside-cue pause
- in-flight relocation admission remains serialized
- reader JavaScript harness and consolidated Android host gates pass

### 14.6 Cue-map diagnostics

- raw source ordinals survive cue filtering and remain revision-stable
- visible projection reuses production resource/range resolution and does not
  reorder labels to conceal non-monotonic sidecar or mapping output
- circled markers bind exact cue starts, clear on every authority-generation
  replacement, and never cause base-raster capture
- mapped, prepared/requested, audio-active, and rendered identities remain
  independently observable by ordinal
- the bounded transition trail records forward-then-back ordinal sequences without
  protected content
- hold completion seeks exactly once; release, movement, pointer cancellation,
  chrome interception, curl start, and generation replacement cancel it
- normal-release source and privacy gates prevent production diagnostics from
  logging or retaining EPUB content and identifiers

## 15. Runtime Acceptance

### 15.1 Prototype acceptance

Use synthetic fixtures to prove passive capture feasibility across portrait,
landscape spread, typography/theme changes, orientation replacement, and chapter
boundary identities. This gate measures parity and resource behavior; it does
not make the passive session semantic authority.

### 15.2 Emulator acceptance

From one frozen ReaderDev APK tied to the tested commit, verify:

- before starting audio, the production cue-map toggle renders numbered markers on
  the current spread
- visible marker ordinals follow DOM reading order; any non-monotonic sequence is
  retained as bounded ordinal-only evidence rather than corrected by display sort
- selecting one numbered cue through hold-to-seek keeps requested, audio-active,
  and rendered identities observable by ordinal
- repeated passive prewarm leaves live authority and highlighting unchanged
- initial Start produces a visible native progressive or exact highlight without
  page navigation
- Stop leaves Start usable on the same spread
- maintenance settlements produce zero audio seek commands
- page-end pause and post-turn resume follow the interaction contract
- a forced preparation failure is recoverable through one fresh Retry
- forward/backward/cancelled curls retain page borders and backing
- no black page, historical highlight replay, or full-page capture per word occurs

Use synthetic/approved fixtures for visual automation. Save local artifacts only
under `.codex-validation`.

### 15.3 Focused physical-tablet acceptance

After automated and emulator gates, use explicit thread-scoped ownership of the
approved tablet. Preserve app data, do not clear Logcat, and do not access any
other device. Validate only the first two landscape pages of Chapter 1 of the
configured paired book:

1. Enable the production cue map and confirm circled ordinals render at visible cue
   starts and progress in reading order before starting audio.
2. Hold one numbered cue and confirm requested, audio-active, and rendered ordinal
   states identify the same cue without an early-release seek.
3. Initial Start highlights the correct current cue and progresses by exact word
   or progressive fallback.
4. Manual Stop clears highlighting, resets/prepares the first visible cue, and
   leaves Start available without page switching.
5. While stopped, a manual turn prepares but does not play the destination.
6. While enabled or boundary-paused, a manual turn resumes only after curl and
   live destination commit.
7. The final intersecting sentence completes, the next outside cue pauses, and no
   full-spread restart occurs.
8. Retry recovers one induced/reproduced preparation failure without closing the
   eBook.
9. Backing, borders, and highlighting remain coherent during forward, backward,
   cancelled, and completed curls.
10. Privacy-safe evidence records zero maintenance-origin seeks, zero passive-to-
    live authority promotion, and zero protected payloads.

Individual cue mismatches are recorded by safe outcome category. They block
release only when they demonstrate a frontend contract violation or prevent the
bounded lifecycle acceptance from being evaluated.

## 16. Release Gates

A signed production correction is permitted only when:

- every specification requirement used by the release path is implemented or
  explicitly classified as a non-blocking deferral with a latest required stage
- no deferred item weakens authority, privacy, Start/Stop, event provenance,
  Retry, or curl-material invariants
- focused RED/GREEN evidence exists for every corrected mechanism
- passive parity and resource feasibility pass
- the shared-foreground preview path is unreachable in production passive work
- consolidated JavaScript and Android reader gates pass
- frozen-APK emulator acceptance passes
- focused signed physical-tablet acceptance passes
- the production APK uses the persistent GitHub-managed certificate with SHA-256
  `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`
- release commit, version, APK hash, workflow result, and signer hash are verified

## 17. Acceptance Summary

- [ ] Live Foliate is the only semantic and committed-destination authority.
- [ ] Passive raster work runs in a dedicated WebView/session with no semantic
      event path.
- [ ] Passive results are admitted only by strict live-manifest parity proof.
- [ ] Passive work never changes live receipt, anchor, overlay, location, or
      mutation generation.
- [ ] Start/Stop behavior follows the authoritative interaction contract.
- [ ] Active highlight is no longer playback eligibility.
- [ ] Maintenance events cannot seek or restart audio.
- [ ] Page-end pause never auto-turns and resumes only after a manual committed
      destination when intent remains enabled.
- [ ] Retry rebuilds a fresh preparation generation and does not require book
      reopen.
- [ ] Exact WordSync remains preferred and progressive fallback never regresses to
      whole-sentence highlighting.
- [ ] The production cue map renders stable circled source ordinals for visible
      cues, exposes requested/audio/rendered divergence, and retains no protected
      content.
- [ ] Hold-to-seek confirms one exact cue and cancels safely before completion.
- [ ] Word updates change only page-local masks, not full-page rasters.
- [ ] Curl rasters, geometry, backing, borders, and highlight ownership are atomic.
- [ ] No curl frame depends on transparent exposure of the live WebView.
- [ ] Diagnostics prove subsystem outcomes without protected content.
- [ ] Automated, frozen-emulator, and bounded physical-tablet gates pass before
      release.
