# Reader PlayLikeCurl QA Remediation Design

**Status:** Approved design; implementation and runtime verification pending

**Date:** 2026-07-18

**Amends:**

- `docs/superpowers/specs/2026-07-17-reader-playlikecurl-raster-readiness-design.md`
- `docs/superpowers/specs/2026-07-15-reader-playlikecurl-library-integration-design.md`

**Canonical deformation engine:** [Darkaxt/PlayLikeCurl](https://github.com/Darkaxt/PlayLikeCurl)

**QA evidence scope:** Navic reader implementation states reviewed through commit
`0af656ba`, together with in-flight worktree changes observed on 2026-07-18.
Those changes are candidate implementation work, not evidence that a requirement
in this document is closed.

## 1. Authority and release verdict

This document is a normative remediation amendment. The amended specifications
remain authoritative except where this document strengthens or corrects a
lifecycle, ownership, interaction, recovery, or verification contract.

The implementation and release-readiness statements in Section 1.1 of the
2026-07-15 design describe the historical `v1.0.11-iota24` publication and its
recorded evidence. This document supersedes those statements as a verdict on the
current Navic implementation. They remain provenance, but they do not satisfy any
current behavior, device, or release gate defined here.

The current eBook PlayLikeCurl integration is **not release-ready** until every
mandatory gate in this document has behavior-level evidence. Source inspection,
source-string assertions, a candidate fix, or a passing isolated policy test is
not sufficient proof of runtime closure.

The verified failure mechanisms are independently capable of producing:

- ignored or prematurely cancelled gestures;
- curls that snap back or never complete;
- invisible input dead zones;
- interaction against an unprepared OpenGL generation;
- stale or overwritten Foliate relocation;
- WebView flashes or stale frames after settlement;
- incorrect RTL and boundary navigation;
- unnecessary WebView recapture despite valid persistent rasters;
- unbounded bitmap residency and incomplete teardown.

All findings remain normative even when implementation work already appears to
address one of them. Implementation status must be tracked separately as one of:

1. `Open`
2. `Candidate fix present`
3. `Behavior verified`
4. `Device verified`

Only behavior and device verification can satisfy a release gate.

## 2. Objective

Make the Foliate and PlayLikeCurl integration deterministic across raster
publication, decoded residency, OpenGL deck preparation, pointer ownership,
settlement, exact relocation, visual handoff, and teardown.

The remediation must:

- preserve PlayLikeCurl as the sole page-deformation engine;
- preserve Foliate as the sole authority for pagination, logical location,
  selection, annotations, progress, and Whispersync;
- reuse valid persistent rasters without WebView recapture;
- keep normal page turns independent from cold preparation;
- provide one stable gesture identity and exactly one terminal outcome;
- keep bitmap, texture, lease, callback, and scheduler ownership bounded;
- make every asynchronous completion generation- or token-aware;
- keep the GL settlement shield visible until WebView composition is confirmed;
- provide behavior-driven and device-level evidence for release.

## 3. Non-goals

This document does not:

- define a commit-by-commit implementation plan;
- authorize a Navic-owned replacement renderer, curl geometry, or settlement
  model;
- move pagination or reading-position authority out of Foliate;
- redesign unrelated reader features;
- allow WebView capture, disk I/O, bitmap decode, bitmap scaling, or texture
  upload on a gesture-frame path;
- treat source-string tests as substitutes for controller, host, renderer, or
  device behavior tests;
- remove the rollback path before all release gates pass.

If PlayLikeCurl needs new public direction or boundary metadata, that contract
must be implemented in the canonical library. Navic must not reproduce library
geometry or renderer state to work around a missing API.

## 4. Terms

### 4.1 Durable raster

A page raster whose required persistent cache write and manifest publication
have completed successfully for the active raster profile and generation.
Capturing or decoding a bitmap does not by itself make the raster durable.

### 4.2 Protected decoded window

The decoded pages that cannot be evicted because they are required for immediate
interaction:

- portrait: the current page plus two pages in each direction;
- landscape: the current spread plus two complete spreads in each direction.

The window is clipped at real chapter boundaries. Clipping must not manufacture
navigable duplicate pages.

### 4.3 Prepared texture generation

A PlayLikeCurl deck generation for which the renderer has delivered its
successful deck-prepared callback. Submission, decode completion, or Navic-side
materialization is not equivalent to renderer preparation.

### 4.4 Pointer sequence

The complete Android pointer stream beginning with `ACTION_DOWN` and ending in
`ACTION_UP`, `ACTION_CANCEL`, lifecycle cancellation, or another explicit
terminal result. One sequence owns one stable gesture ID.

### 4.5 Relocation token

A unique identifier assigned to one exact Foliate visual-page relocation. It is
carried through dispatch, JavaScript acknowledgement, location notification,
WebView visual-state confirmation, and GL handoff.

### 4.6 Candidate fix

Code that appears to address a requirement but has not passed the behavior and
runtime evidence required by this document.

## 5. Architectural correction: independent state machines

No shared readiness enum or presentation flag may substitute for the following
independent state machines. A coordinator may derive presentation and input
policy from them, but it must not erase their distinct meanings.

### 5.1 Raster publication state

Each logical raster target moves through states equivalent to:

```text
Unknown
  -> DurableHit
  -> CaptureQueued -> Capturing -> Persisting -> Durable
  -> Deferred
  -> Failed
  -> Invalidated
```

Required invariants:

- A cold current-chapter target is complete only in `Durable` or `DurableHit`.
- `Persisting` is not interactive cold readiness.
- `Deferred` has an explicit resumption event and visible presentation when no
  valid active deck exists.
- Every request and completion carries a raster generation or request epoch.
- Invalidated work cannot publish into or satisfy a newer generation.

### 5.2 Decoded residency state

Each durable raster moves through states equivalent to:

```text
Absent -> Hydrating -> ProtectedResident
                    -> EvictableResident -> Released
```

Required invariants:

- A decoded miss is not evidence that the persistent raster is missing.
- Persistent hydration is attempted before WebView recapture.
- The protected window advances before a turn refill requests its new far edge.
- Eviction is prohibited while any cache, active deck, pending deck, upload, or
  publication owner retains the bitmap.

### 5.3 Texture-deck state

Each renderer generation moves through states equivalent to:

```text
Absent -> Submitted -> Preparing -> Prepared -> Active
                                      |           |
                                      |           -> Settling
                                      -> Pending  -> Released
                                                  -> Failed
```

Required invariants:

- New gestures are accepted only against a prepared active generation.
- `BackgroundPrefetch` is not evidence of texture readiness.
- A repaired decoded set is not recovered until the required deck is submitted
  and PlayLikeCurl confirms preparation.
- Promotion cannot grant interaction to an unprepared generation.
- Active and pending deck ownership remains bounded by the PlayLikeCurl lease
  contract.

### 5.4 Pointer and settlement state

Each pointer sequence moves through states equivalent to:

```text
Provisional -> TapCandidate -> TapTerminal
            -> CurlOwned -> Settling -> TurnTerminal
            -> RejectedTerminal
            -> CancelledTerminal
```

Required invariants:

- The gesture ID is allocated once on `ACTION_DOWN` and reused for every later
  callback, including delayed single-tap confirmation.
- Native curl ownership remains provisional until touch slop and horizontal
  dominance are established.
- A classified curl drag cannot fall through to tap navigation.
- Settling rejects new pointer sequences while allowing the current settlement
  to continue.
- A terminal ledger uses compare-and-set semantics: only the first terminal
  transition publishes an outcome.

The coordinator must expose separate decisions for:

- whether a new pointer may begin;
- whether the current pointer may continue;
- whether the current settlement may continue;
- whether active work must be cancelled for a specific lifecycle reason.

A single `blocked` Boolean cannot represent these decisions.

### 5.5 Exact relocation state

Each accepted turn moves through states equivalent to:

```text
Queued(token)
  -> Dispatched(token)
  -> Acknowledged(token)
  -> ExactLocationObserved(token)
  -> VisualStateConfirmed(token)
  -> FrameBoundaryPassed(token)
  -> Complete(token)
```

Required invariants:

- Accepted settlement relocations are serialized and never silently overwritten.
- Completion matching uses the token and generation, not only the page ordinal.
- Stale acknowledgements cannot complete or clear a newer relocation.
- The GL surface remains visible through visual-state confirmation and one
  subsequent animation frame.

## 6. Cross-machine invariants

The following rules apply across every remediation workstream:

1. Raster generation, decoded residency, texture preparation, interaction, and
   exact relocation remain separately observable.
2. `Settling` never maps to an instruction to cancel the settlement that
   produced it.
3. Normal settlement never returns the reader to cold blocking preparation.
4. A valid persistent raster is hydrated before capture is considered.
5. Current-chapter rasters are captured at most once per raster profile unless
   explicit invalidation makes them invalid.
6. Background work cannot change the current gesture disposition when a valid
   prepared active deck exists.
7. Every asynchronous callback is fenced by the identity relevant to its
   subsystem: raster epoch, texture generation, gesture ID, or relocation token.
8. No terminal callback is published for an already-terminal gesture.
9. No bitmap is released while any declared owner still retains it.
10. Presentation follows state; presentation visibility must not be used as the
    source of lifecycle truth.

## 7. Normative remediation requirements

Severity controls implementation priority, not optionality. Every requirement
below is mandatory for release.

### QA-01 — Settlement self-cancellation

**Severity:** P0 interaction blocker

**Failure mechanism:** Renderer settlement enters a non-gesture-accepting state.
That state is converted to blocking preparation, and the native host responds by
cancelling the active PlayLikeCurl gesture. The settlement can therefore cancel
itself.

**Required design:**

- Replace the overloaded blocking signal with typed input and operation policy.
- During settlement, reject or consume new pointer sequences but continue the
  current settlement animator.
- Cancellation of active settlement is permitted only for an explicit lifecycle
  reason such as disposal, renderer replacement, reader exit, or invalidation
  that makes continuation unsafe.
- Cancellation reason and gesture ID must be logged.

**Acceptance:** A committed and a snap-back settlement each finish once without
`cancelActiveGesture` being triggered by their own settling readiness update.
No cold-preparation shield appears during normal settlement.

### QA-02 — Refill cannot use valid persistent rasters

**Severity:** P0 interaction blocker

**Failure mechanism:** Turn refill consults only a small retained-snapshot cache.
The protected window does not reliably advance first, so a far-edge memory miss
is treated as missing raster data even when the persistent raster is valid.

**Required design:**

- Advance the protected window to the destination page or spread before far-edge
  refill.
- Add a no-capture hydration path from the persistent raster cache.
- Resolve a refill in this order: retained decoded bitmap, persistent hydration,
  then explicit capture or repair only if the durable raster is absent or invalid.
- Preserve logical page and raster-profile identity across hydration.

**Acceptance:** Evict the new far-edge page from memory while leaving its
persistent file valid, perform turns in both directions, and observe successful
refill with zero WebView captures.

### QA-03 — Successful repair does not restore a renderer deck

**Severity:** P0 recovery blocker

**Failure mechanism:** Repair can replace Navic's decoded page set without
submitting that replacement as the active or pending PlayLikeCurl deck and
without restoring texture readiness.

**Required design:**

- Repair completion emits a typed recovery result that identifies the page set,
  center ordinal, raster generation, and intended deck role.
- If no valid prepared active deck exists, submit the repaired set as the active
  recovery deck.
- If a valid active deck exists, recovery may prepare a pending replacement
  without blocking current interaction.
- Recovery becomes interactive only after the matching `onDeckPrepared` callback.

**Acceptance:** Force a missing-page repair with no usable active deck. The
reader submits the repaired generation, remains non-interactive until the
prepared callback, then restores interaction without reopening the chapter.

### QA-04 — Unbounded adapter bitmap cache

**Severity:** P1 resource blocker

**Failure mechanism:** Materialized pages remain in the raster adapter cache for
the lifetime of a stable profile. Closing obsolete deck leases does not remove
the cache's ownership, so visited pages can accumulate indefinitely.

**Required design:**

- Bound adapter residency with a protected-window or LRU policy.
- Pin entries retained by active or pending renderer decks.
- Remove cache ownership when an entry is outside the configured window and is
  not pinned.
- Release the bitmap only when neither the adapter nor any deck owns it.
- Export current, peak, pinned, evicted, and released counts to diagnostics.

**Acceptance:** After one hundred consecutive turns, adapter bitmap residency is
within the configured bound, obsolete entries are released, active/pending deck
bitmaps remain valid, and residency does not grow monotonically.

### QA-05 — Readiness is published before durable persistence

**Severity:** P0 durability blocker

**Failure mechanism:** Batch completion can be recorded after capture or
hydration while persistent publication is still asynchronous. Publication
failure only logs, allowing cold preparation to report success without satisfying
the persistent current-chapter contract.

**Required design:**

- Distinguish capture completion from durable publication completion.
- Cold current-chapter readiness awaits successful persistence and manifest
  publication for every required target.
- Publication failure produces an explicit retry, repair, or visible failure
  state; it cannot silently count as ready.
- Asynchronous background publication is permitted only when the initial durable
  chapter contract was already satisfied.

**Acceptance:** Inject persistent-write failure during a cold open. The reader
must not become falsely ready. Restore storage and trigger the documented retry;
readiness then completes without duplicate capture of already durable pages.

### QA-06 — Invalidation-generation race in raster publication

**Severity:** P0 stale-data blocker

**Failure mechanism:** Staged values, scheduler work, and callbacks can share a
key digest across invalidation. Old completion can collide with or satisfy a new
request, and overwritten staged bitmaps may not have deterministic ownership.

**Required design:**

- Namespace staged rasters, callbacks, and in-flight scheduler work by cache key
  plus raster generation or request epoch.
- Cancel or replace invalidated scheduler work where supported; otherwise reject
  its completion by epoch.
- Never allow an old completion to invoke callbacks registered by a new request.
- Deterministically release superseded staged bitmaps.
- Log stale-completion rejection without publishing user content.

**Acceptance:** Pause a publication, invalidate and issue the same logical key in
a new epoch, then complete the old request first. Only the new request may
satisfy readiness, and all old staged ownership must be released.

### QA-07 — Deferred preparation can create an invisible deadlock

**Severity:** P0 availability blocker

**Failure mechanism:** A deferred initial preparation can leave gesture policy
blocking, remove its visible shield, and schedule no guaranteed retry.

**Required design:**

- When no valid prepared active deck exists, deferred preparation retains visible
  preparation presentation.
- Resumption is tied to an explicit event such as WebView readiness, stable
  viewport, layout completion, reader resume, or the deferred prerequisite.
- Fixed-delay polling is not the primary recovery mechanism.
- If a valid prepared active deck exists, deferred background work may enter a
  non-blocking recovery state without changing interaction readiness.

**Acceptance:** Force each supported deferral reason. The reader must either
remain visibly preparing and resume on the matching event, or remain interactive
with its existing prepared deck. It must never be invisible and input-blocked.

### QA-08 — Unprepared promoted deck accepts gestures

**Severity:** P0 renderer-readiness blocker

**Failure mechanism:** A promoted generation can be assigned a background-prefetch
interaction state that accepts gestures before the GL renderer has prepared that
generation.

**Required design:**

- Centralize the acceptance predicate around the active renderer generation's
  prepared status.
- Promotion to logical active state does not grant interaction until the matching
  deck-prepared callback.
- Background prefetch may be interactive only when the immediate active
  transition deck is already prepared and usable.
- Preparation failure leaves the previous valid deck active where possible or
  enters explicit recovery.

**Acceptance:** Delay the promoted generation's prepared callback and attempt a
pointer sequence. It is rejected once without mutating reader location. After the
callback, the same direction becomes interactive.

### QA-09 — Exact relocation can be overwritten

**Severity:** P0 navigation blocker

**Failure mechanism:** Native and JavaScript coordination retain only one pending
ordinal. A rapid second settlement can replace the first pending relocation, and
ordinal-only matching cannot distinguish stale completion.

**Required design:**

- Allocate a unique relocation token for every accepted settlement.
- Serialize accepted settlement relocations through a dedicated command queue.
- Renderer settlement completion ends the `Settling` input restriction. When the
  promoted active deck is prepared, the next pointer may be accepted even while
  the previous relocation is still awaiting WebView acknowledgement or visual
  handoff; its later accepted settlement is appended to the queue.
- Carry the token through JavaScript dispatch, acknowledgement, exact-location
  notification, visual-state confirmation, and completion.
- Reject stale token completions without clearing the active command.
- Reader teardown, profile invalidation, or authoritative external navigation
  cancels queued tokens explicitly and exactly once.

**Acceptance:** Complete one renderer settlement, delay its Foliate
acknowledgement or visual handoff, then accept a second turn against the prepared
promoted deck. Foliate applies both relocations in accepted order, each token
completes once, and reordered stale callbacks cannot hide the surface or alter
the final ordinal.

### QA-10 — RTL drag direction does not match logical navigation

**Severity:** P0 navigation-correctness blocker

**Failure mechanism:** Tap navigation is direction-aware, while raw PlayLikeCurl
pointer and fling mapping use physical left/right assumptions. The visual drag
can therefore commit the opposite logical direction in RTL.

**Required design:**

- Convert physical movement into logical previous/next direction at one explicit
  host or controller boundary.
- Apply the same direction contract to drag, fling, tap, portrait, and landscape.
- If the renderer requires direction configuration, add it to the canonical
  PlayLikeCurl API rather than copying settlement logic into Navic.
- Diagnostics record both physical direction and logical transition without
  recording rendered content.

**Acceptance:** In LTR and RTL, slow drag and fling toward logical next/previous
produce the same logical destination as the corresponding tap in portrait and
landscape.

### QA-11 — Native ownership is assigned before gesture arbitration

**Severity:** P0 input-correctness blocker

**Failure mechanism:** PlayLikeCurl can own the pointer stream on `ACTION_DOWN`
before touch slop, horizontal intent, vertical movement, selection, or long-press
behavior is known.

**Required design:**

- Allocate the gesture ID on `ACTION_DOWN` but keep ownership provisional.
- Classify a curl drag only after `abs(dx)` exceeds touch slop and
  `abs(dx) > abs(dy)`.
- Yield to the content path when vertical displacement establishes dominance.
- Preserve long press and selection until curl ownership is established.
- Once classified as a curl drag, consume the remainder of the sequence and
  prohibit tap fallback.

**Acceptance:** Horizontal, vertical, diagonal, long-press, selection, tiny-move,
and cancellation sequences each route to one owner. Classified drags never
trigger delayed tap navigation.

### QA-12 — Boundary deck duplication creates fake navigation

**Severity:** P0 boundary-correctness blocker

**Failure mechanism:** Clamping missing roles to the nearest valid ordinal can
produce decks such as `[0, 0, 1]`. The renderer sees a previous role even though
it represents the current logical page.

**Required design:**

- This requirement supersedes Section 5.2's deterministic boundary-duplication
  rule and Section 8.4's boundary-duplication allowance in the 2026-07-15 design.
- Represent unavailable previous and next transitions explicitly through nullable
  roles, boundary capability metadata, or an equivalent canonical library
  contract.
- Never use duplicate logical page identities to simulate a navigable role. The
  engine may reuse current-page pixels only as a non-navigable visual filler when
  explicit boundary metadata prevents that role from becoming a transition.
- Boundary attempts animate or settle back according to PlayLikeCurl's canonical
  behavior and terminate as `RejectedBoundary`.
- Rejected boundaries do not dispatch Foliate relocation.

**Acceptance:** At the first and last visual page or spread, outward drag and
fling remain stable, dispatch no exact relocation, preserve the current ordinal,
and publish one `RejectedBoundary` outcome.

### QA-13 — Adjacent chapters are classified but never scheduled

**Severity:** P1 readiness and performance blocker

**Failure mechanism:** Adjacent-chapter targets exist in planning and
classification, but no production batch consumes them after the current chapter
becomes ready.

**Required design:**

- Schedule previous- and next-chapter raster work after current-chapter
  interactive readiness.
- Use separate lower-priority, cancellable batches.
- Do not attach the cold-open preparation shield or change current gesture
  disposition.
- Pause or cancel adjacent work when foreground repair or current-chapter work
  needs the same constrained resource.
- Persist successful adjacent results under their correct chapter and profile
  identity.

**Acceptance:** After current readiness, adjacent work begins and produces
persistent raster evidence while continuous current-chapter gestures remain
accepted and no preparation shield appears.

### QA-14 — GL surface hides before WebView composition

**Severity:** P0 presentation blocker

**Failure mechanism:** Exact-location notification confirms logical location, not
that Android WebView has composited the destination frame. Hiding PlayLikeCurl at
that point can expose a stale or intermediate WebView frame.

**Required design:**

- Retain the GL settlement shield after exact-location acknowledgement.
- Register `postVisualStateCallback` for the matching relocation token.
- After visual-state confirmation, wait for `postOnAnimation` before hiding.
- Revalidate reader lifecycle, relocation token, raster generation, and expected
  ordinal immediately before handoff.
- If the visual barrier cannot complete, keep the shield and enter explicit
  recovery rather than hiding blindly.

**Acceptance:** Delay WebView composition after logical acknowledgement. The GL
surface remains visible until the matching visual callback and next frame, then
hides once with no stale-page flash.

### QA-15 — Raster cache is dropped without deterministic close

**Severity:** P1 teardown blocker

**Failure mechanism:** Reader source teardown can close the scheduler and cancel
its scope, then discard the raster-cache reference without invoking the cache's
bitmap-release operation.

**Required design:**

- Close the raster cache before clearing its reference.
- Make close idempotent and safe after partial initialization.
- Resolve or cancel publication callbacks and release staged values before scope
  teardown loses their ownership path.
- Record decoded, adapter, staged, deck, and texture ownership before and after
  close.

**Acceptance:** Repeatedly open and close the reader after raster hydration and
turning. Every cycle returns ownership counters to baseline and does not retain
released reader instances.

### QA-16 — Gesture IDs and terminal outcomes are not exactly once

**Severity:** P0 accounting and control-flow blocker

**Failure mechanism:** Delayed tap confirmation can allocate a second synthetic
ID after the original pointer sequence was cancelled, and unavailable owned
streams can attempt terminal publication on `DOWN`, `MOVE`, and `UP`.

**Required design:**

- Store the `ACTION_DOWN` gesture ID through tap confirmation or cancellation.
- Maintain one terminal ledger shared by host and controller for the sequence.
- `finishGesture` publishes only when it atomically changes an active,
  non-terminal sequence to a terminal state.
- After early rejection, later events in the same pointer stream are consumed or
  routed without publishing another result.
- Lifecycle cancellation and renderer callbacks use the same terminal gate.

**Acceptance:** For tap, committed turn, snap-back, unavailable renderer,
boundary rejection, lifecycle cancellation, and render failure, diagnostics show
one ID and exactly one terminal outcome. Replayed or late callbacks are ignored.

## 8. Required data flows

### 8.1 Cold chapter opening

1. Resolve the active raster profile and allocate its raster generation.
2. Query persistent current-chapter coverage.
3. Capture only missing or invalid current-chapter targets.
4. Await durable publication of all required targets.
5. Hydrate the protected decoded window.
6. Materialize and submit the initial renderer deck.
7. Await PlayLikeCurl's prepared-generation callback.
8. Remove the preparation shield and allow new gestures.
9. Start adjacent-chapter work without changing interaction readiness.

A persistent-write or deck-preparation failure cannot silently advance to
interactive readiness.

### 8.2 Warm reopen

1. Validate the profile, manifest, dimensions, page identity, and raster files.
2. Hydrate the protected window from persistent storage.
3. Submit and prepare the initial deck.
4. Enable interaction.
5. Schedule only missing background targets.

A valid warm reopen performs zero WebView captures.

### 8.3 Normal page turn

1. Allocate one gesture ID on `ACTION_DOWN`.
2. Keep ownership provisional until horizontal classification.
3. Transfer the same ID to PlayLikeCurl after classification.
4. Reject new pointers while allowing the accepted settlement to finish.
5. Promote only a prepared usable deck, or keep interaction unavailable until
   preparation completes.
6. Advance the protected window and hydrate the new far edge without capture
   when persistent data exists.
7. Queue a tokenized exact Foliate relocation.
8. Keep the GL surface visible through the matching WebView composition barrier.
9. Publish one terminal outcome and release obsolete ownership.

### 8.4 Background adjacent-chapter preparation

1. Begin only after the current chapter has a prepared interactive deck.
2. Run under a distinct background batch and cancellation policy.
3. Yield constrained resources to foreground repair and current-chapter work.
4. Persist valid outputs without changing the active interaction state.
5. End without attaching or removing the current reader's cold-open shield.

### 8.5 Teardown and invalidation

1. Stop accepting new pointer sequences.
2. Cancel the active pointer or settlement with one explicit lifecycle reason
   only when continuation is unsafe.
3. Cancel or fence relocation tokens.
4. Invalidate raster epochs and reject stale completions.
5. Release active and pending deck leases through PlayLikeCurl's contract.
6. Close adapter and raster caches.
7. Cancel remaining scheduler work and callbacks.
8. Verify ownership counters return to baseline.

## 9. Failure and recovery policy

| Failure | Required response | Forbidden response |
| --- | --- | --- |
| Decoded memory miss with valid persistent raster | Hydrate from persistent storage | Immediate WebView recapture |
| Missing durable raster | Schedule explicit capture or repair | Pretend the decoded set is complete |
| Cold persistent-write failure | Remain visibly preparing or enter explicit error/retry | Publish ready and only log a warning |
| Deferred prerequisite | Resume on the prerequisite event | Invisible blocking with no retry |
| Deck-preparation failure with old valid deck | Retain old deck and enter non-blocking recovery | Promote the failed generation |
| Deck-preparation failure without valid deck | Keep visible recovery and retry or fail explicitly | Accept gestures against unprepared content |
| Stale raster callback | Reject by epoch and release stale ownership | Satisfy a newer request |
| Stale relocation callback | Reject by token | Clear the current relocation by ordinal |
| Missing visual composition callback | Keep GL shield and recover explicitly | Hide after a timer without validation |
| Reader teardown | Cancel/fence work and close all owners | Drop references and rely on garbage collection |

Fixed-delay retry may be used only as a bounded backoff after an explicit retry
condition exists. It cannot be the source of lifecycle truth.

## 10. Ownership contract

Every bitmap must have explicit ownership in one or more of these categories:

- staged raster publication;
- decoded cache;
- raster adapter cache;
- active PlayLikeCurl deck lease;
- pending PlayLikeCurl deck lease;
- in-flight texture upload;
- transient validated copy.

Ownership transitions must satisfy:

1. Transfer is explicit; assignment to a collection is not an undocumented
   ownership transfer.
2. A deck release decrements only deck ownership. It does not imply cache
   eviction unless cache ownership also ends.
3. Cache eviction cannot release an active or pending deck bitmap.
4. Invalidation releases staged and transient values that cannot publish.
5. Teardown is idempotent and releases every remaining owner exactly once.
6. Diagnostic counters distinguish logical entries from unique bitmap identities.
7. Protected and maximum residency limits are centralized policy, not duplicated
   across controllers.

## 11. Gesture and direction contract

### 11.1 Arbitration

The native host owns provisional classification. Curl ownership begins only
when:

```text
abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)
```

If vertical movement establishes dominance first, the content path owns the
sequence. If neither direction establishes dominance before `ACTION_UP`, the
sequence remains eligible for tap confirmation using the original gesture ID.

### 11.2 Logical direction

Navigation commands use logical `Previous` and `Next`. Physical left/right
movement is converted according to reading direction before commit or boundary
validation. Portrait and landscape adapters consume the same logical result.

### 11.3 Terminal outcomes

This section supersedes the closed terminal-outcome list in Section 7.2 of the
2026-07-17 design. The implementation must preserve these exact semantic
outcomes, although type names may follow surrounding code conventions:

```text
CommittedForward
CommittedBackward
CompletedTapAction
CancelledByUser
CancelledLifecycle
RejectedPreparing
RejectedSettling
RejectedDirection
RejectedBoundary
RejectedRendererUnavailable
FailedRenderer
FailedRecovery
```

A tap that performs page navigation terminates as `CommittedForward` or
`CommittedBackward`; `CompletedTapAction` covers a successfully handled
non-navigation tap such as reader-chrome interaction. Every sequence has one and
only one terminal outcome. Adding or merging semantic outcomes requires an
explicit specification amendment.

## 12. Exact relocation and visual handoff contract

Accepted settlements use a serialized relocation queue. Each queue entry
contains at least:

- relocation token;
- gesture ID;
- raster generation;
- texture generation;
- source and destination visual ordinals;
- logical direction;
- expected Foliate session identity.

The entry remains active until visual handoff completes or explicit cancellation
occurs. Exact-location observation alone does not remove the GL surface.

The required Android handoff is:

```text
exact-location observation
  -> matching postVisualStateCallback
  -> matching postOnAnimation
  -> final token/generation/lifecycle validation
  -> hide GL surface
```

Queued accepted settlements may not be coalesced away. External authoritative
navigation may cancel them, but cancellation must be explicit and terminal.

## 13. Diagnostics and privacy

Diagnostics must make the independent state machines reconstructable. Events
must include relevant non-content identifiers such as:

- reader session identity;
- gesture ID and terminal outcome;
- relocation token and phase;
- raster generation or request epoch;
- texture generation and deck role;
- logical source and destination ordinal;
- physical and logical direction;
- preparation, defer, retry, invalidation, cancellation, and failure reason;
- decoded, adapter, staged, lease, and texture ownership counts;
- capture, hydration, persistence, deck preparation, settlement, and visual
  handoff duration.

Diagnostics must not contain:

- EPUB text;
- rendered bitmap pixels or encoded raster payloads;
- API credentials or authentication material;
- Whispersync transcript content;
- user annotations or selected text.

## 14. Verification strategy

### 14.1 Policy and state-machine tests

Use executable state-machine tests for:

- cold and warm raster readiness;
- durable publication and failure;
- deferred resumption;
- protected-window movement;
- bounded eviction with pins;
- texture preparation and promotion;
- settlement continuation versus new-pointer rejection;
- terminal compare-and-set behavior;
- relocation token ordering and stale-callback rejection.

Tests that search source text may remain as governance guards, but they cannot be
the only evidence for any behavior in this section.

### 14.2 Native host and controller behavior tests

Exercise real host/controller calls with controlled fakes for WebView,
PlayLikeCurl, persistent cache, and scheduler callbacks. Verify:

- settlement does not cancel itself;
- delayed tap confirmation retains the original ID;
- unavailable streams terminate once;
- horizontal and vertical arbitration;
- LTR and RTL logical direction;
- first/last boundary rejection;
- persistent-only far-edge refill;
- repaired-deck submission and prepared gating;
- unprepared promotion rejection;
- rapid relocation serialization;
- visual-state and frame-boundary handoff;
- invalidation while old publication is in flight;
- teardown after partial initialization and active work.

### 14.3 Resource and stress tests

At minimum, the one-hundred-turn test records before, peak, steady-state, and
after-close values for:

- decoded unique bitmaps;
- adapter unique bitmaps;
- protected and pinned entries;
- active and pending deck leases;
- staged publications;
- renderer textures;
- pending callbacks and relocation tokens.

After warmup, bounded categories must stay within configured limits and must not
show monotonic growth. After close, reader-owned counts return to baseline.

### 14.4 Runtime matrix

Behavior must be validated in:

- ReaderDev;
- an Android emulator;
- at least one physical Android device;
- portrait and landscape;
- LTR and RTL;
- first and last page/spread boundaries;
- cold open and valid warm reopen;
- rapid repeated turns;
- slow drag, fling, snap-back, and cancellation;
- rotation and resize;
- app background and resume;
- GL context recreation;
- persistence, repair, and delayed-callback fault injection.

Focused remediation suites must pass. The full host suite must introduce no new
failures relative to a frozen baseline; unrelated baseline failures do not waive
focused runtime requirements.

## 15. Mandatory release gates

### 15.1 Functional gates

- A settling gesture is never cancelled by its own readiness transition.
- New pointers are rejected during settlement without interrupting settlement.
- A valid warm reopen performs zero WebView captures.
- Persistent-only far-edge refill performs zero WebView captures.
- Cold current-chapter readiness waits for durable publication.
- Deferred initial preparation is visible and has a deterministic resume event.
- Repair without a valid deck submits a deck and waits for `onDeckPrepared`.
- No pointer is accepted against an unprepared active generation.
- Every pointer sequence uses one ID and has exactly one terminal outcome.
- Horizontal classification preserves vertical movement, long press, and
  selection before ownership transfer.
- LTR and RTL drag, fling, and tap share logical direction.
- Real boundaries cannot be represented by duplicate navigable page identities.
- Boundary rejection dispatches no Foliate relocation.
- Accepted relocations are tokenized, serialized, and stale-safe.
- GL remains visible until WebView visual composition and the next frame.
- Adjacent chapters are scheduled after readiness without blocking interaction.

### 15.2 Performance and ownership gates

- Gesture-frame paths perform no disk I/O, WebView capture, bitmap decode,
  bitmap scaling, or texture upload.
- Current-chapter rasters are captured at most once per valid profile generation.
- The portrait and landscape protected windows meet the required sizes.
- Adapter and decoded residency remain within centralized configured bounds.
- Active and pending deck leases remain bounded by the library contract.
- One hundred consecutive turns cause no unbounded bitmap, texture, lease,
  callback, relocation, or staged-publication growth.
- Reader teardown returns all reader-owned resource counters to baseline.

### 15.3 Evidence gates

- Focused state-machine and behavior suites pass on a frozen commit.
- PlayLikeCurl library tests pass for any canonical API change.
- ReaderDev and emulator validation pass.
- Physical-device validation passes for portrait, landscape, LTR, RTL, rapid
  turning, rotation, background/resume, and GL recreation.
- Evidence records the exact Navic commit, PlayLikeCurl commit or release,
  configuration, device, and observed diagnostics.

## 16. Implementation planning boundaries

The later implementation plan should decompose the work into independently
verifiable units aligned with these boundaries:

1. typed readiness and operation policy;
2. durable raster publication and invalidation epochs;
3. persistent hydration and protected-window movement;
4. repaired and promoted deck preparation;
5. provisional gesture arbitration, logical direction, and terminal ledger;
6. explicit boundary representation;
7. relocation queue and WebView visual handoff;
8. bounded adapter ownership and deterministic teardown;
9. adjacent-chapter scheduling;
10. behavior, stress, emulator, and device verification.

The plan must account for dependencies between these units, but it must not merge
them back into one controller flag or one untestable implementation step.

## 17. Status at specification approval

All requirements are `Open` or `Candidate fix present` and require revalidation.
The paused worktree contains in-flight reader changes, but this document does not
claim that any finding is closed. A future status update must cite behavior-level
evidence and must not replace the normative requirement with an implementation
description.

Until all mandatory gates pass on a frozen commit, Navic must retain a safe
rollback path to non-curl page navigation and must not represent the PlayLikeCurl
integration as release-ready.
