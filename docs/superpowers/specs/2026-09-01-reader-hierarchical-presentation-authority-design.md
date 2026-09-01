# Reader Hierarchical Presentation Authority Design

**Status:** Stage 6 corrective specification and release blocker

**Parent specification:**
`docs/superpowers/specs/2026-08-23-reader-raster-isolation-and-whispersync-stabilization-design.md`

**Active staged plan:**
`docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

## 1. Purpose

Replace independent visibility, preparation, renderer, shell-cover, and input
decisions with one hierarchical presentation authority. The authority selects one
visual owner, derives the compatible input policy from that owner, and keeps
background work from presenting itself as foreground UI.

This design is required because component-local state machines have prevented
stale mutation but have not guaranteed a coherent user-visible transition across
page settlement, TOC relocation, shell-cover entry and dismissal, or Android
visibility interruption.

The implementation remains part of Stage 6. It does not start Stage 7.

## 2. Verified Failure Pattern

The post-canonical-mapping tablet probes established three distinct authority
failures:

1. A completed exact-turn acknowledgement survived an unrelated same-session TOC
   relocation. Foliate and reader chrome moved to Chapter 1 while the native raster
   remained at the prior spread and the next-page action became ineffective. The
   acknowledgement was incorrectly retained as session state instead of consumed
   as an event-scoped receipt.
2. `TRIM_MEMORY_UI_HIDDEN` was classified by numeric comparison as active memory
   pressure. Ordinary Home/restore retired preparation work and surfaced a false
   memory-pressure failure even though the publication remained open.
3. Returning from the first spread to the native shell cover invalidated ready
   raster/deck proof and restarted preparation. The stable cover and the
   context-free preparation overlay both claimed presentation, so `Preparing
   pages` appeared over the cover for off-screen rebuilding.

A frame-by-frame shell-cover recording showed complete page frames followed by
complete cover frames; it did not prove mixed WebView or back-face pixels. The
existing hide-before-cover-publication ordering remains a credible race, but this
specification does not claim that a mixed frame was reproduced.

These failures share one cause: several components can independently publish
visibility or interaction consequences without a single owner deciding which
layer is authoritative.

## 3. Scope

This design governs:

- the visual owner of the reader frame
- native shell-cover entry and dismissal
- active curl, settlement, and retained raster presentation
- explicit live-engine exposure and handback
- blocking versus background preparation presentation
- input admission while each visual owner is active
- fail-visible diagnostic and Retry behavior
- lifecycle and resource-pressure effects on presentation

It preserves the existing semantic boundaries:

- live Foliate remains the only publication, pagination, DOM-range, visible-range,
  and committed-destination authority
- PlayLikeCurl remains the only page-deformation authority
- passive Foliate remains a raster worker and cannot become semantic authority
- Compose remains the owner of chrome, accessibility, diagnostics, and Retry

## 4. Non-Goals

- Replacing Foliate location or DOM authority with native inference.
- Letting presentation priority validate semantic destination identity.
- Treating passive receipts as live presentation receipts.
- Starting Stage 7 or broadening tablet acceptance beyond the configured pair's
  first two Chapter 1 pages in landscape.
- Fixing an unobserved mixed curl/back-face frame through an arbitrary delay.
- Adding another visibility Boolean, inherited acknowledgement, or independent
  overlay exception as the durable architecture.
- Hiding preparation failures merely because a stable cover or raster is visible.

## 5. Authority Domains

Authority is hierarchical within a concern, not one global ordering across
unrelated concerns.

### 5.1 Reader lifecycle authority

The reader session decides whether a publication is active, suspended, terminal,
or closed. No presentation owner may keep a reader alive after terminal closure,
and ordinary visibility loss may not masquerade as closure or memory pressure.

Lifecycle events are typed by meaning. At minimum:

- `VisibilityLost`
- `VisibilityRestored`
- `RunningMemoryPressure`
- `BackgroundMemoryPressure`
- `RendererLost`
- `PublicationClosed`

Android trim constants cannot be interpreted as a linear severity scale across
semantic categories. `TRIM_MEMORY_UI_HIDDEN` maps only to `VisibilityLost`.

### 5.2 Semantic destination authority

Live Foliate exclusively owns current semantic destination, spine interpretation,
visible range, DOM ranges, and exact relocation acknowledgements. Native code may
compare opaque identities but may not infer EPUB positions.

Relocation and settlement receipts are one-shot event facts. An untagged event
cannot inherit an exact-turn acknowledgement from previous state, even within the
same Foliate session.

### 5.3 Visual presentation authority

One central arbiter selects exactly one underlying visual owner. Independent hosts
may report facts but cannot directly decide final layer visibility.

### 5.4 Interaction authority

Input policy is derived from the selected visual owner and its transaction phase.
Presentation and interaction cannot be computed by separate owners that can
disagree.

### 5.5 Diagnostic authority

Failures and user-requested blocking progress are Compose diagnostics above the
underlying visual owner. Diagnostics do not become page-frame authority. A stable
frame may remain visible under an actionable failure.

## 6. Presentation Authority Model

Names may follow repository conventions, but the model must express these states
without nullable-flag combinations:

```text
ReaderPresentationAuthority
  Unavailable
  ShellCover
  ShellCoverCommitPending
  CurlGesture
  CurlSettlementPending
  SettledNativePage
  LiveEngineHandoffPending
  LiveEngineExposed
  BlockingPreparation
```

Each non-terminal authority carries the identity required to reject stale
callbacks. Depending on the state, that includes:

- Foliate session identity
- publication and viewport/profile generation
- committed destination identity
- raster and texture generation
- gesture or settlement token
- shell-cover request/commit token
- live-engine exposure/handoff token
- preparation generation

The public output is one immutable decision:

```text
ReaderPresentationDecision
  authority
  layerVisibility
  inputPolicy
  preparationPresentation
  diagnosticPresentation
  requiredTransition
```

`layerVisibility`, `inputPolicy`, and progress visibility are projections of the
same decision. They are not separately mutable state.

## 7. Hierarchy And Selection Rules

The arbiter applies the following rules in order.

### 7.1 Terminal lifecycle wins

A terminal or closed publication cannot expose stale native, cover, or WebView
content. Non-terminal visibility loss retains the logical reader session and its
latest valid identity.

### 7.2 A committed shell cover owns the frame

Once the native host confirms the requested cover is valid, sized, visible, and
committed, `ShellCover` owns the underlying frame. Raster preparation, passive
capture, and renderer rebuilding cannot override that frame.

Ordinary preparation behind a committed cover is hidden. A real failure remains
visible as a diagnostic with Retry.

### 7.3 Cover entry is a transaction

A page-boundary request creates `ShellCoverCommitPending`; it does not immediately
hide the existing native page surface. The current stable page or terminal curl
frame remains authoritative until the native host returns a matching
`ShellCoverCommitted` receipt.

The receipt must match the request token, reader session, publication generation,
and cover identity. The host may acknowledge only after the cover asset, geometry,
visibility, and a committed draw/presentation boundary are current.

Only then may the prior page surface be hidden and authority become `ShellCover`.
A stale receipt is released/ignored and cannot alter visibility.

### 7.4 Cover dismissal is also transactional

A forward action from the cover creates a page-entry request. The cover remains
visible until the destination native page or explicit engine handoff has matching
validated presentation proof. The implementation cannot hide the cover first and
wait on a later asynchronous page publication.

If preparation is still running when the user requests entry, the request becomes
`BlockingPreparation` with visible progress and a reachable automatic continuation
when readiness arrives. Repeated input coalesces into the same request.

### 7.5 Active curl owns deformation

During an accepted gesture, `CurlGesture` owns all moving page pixels, reverse
material, clipping, borders, shadow, and page-attached masks. No shell, WebView,
or preparation layer may replace part of the frame mid-gesture.

At gesture terminal, authority moves to `CurlSettlementPending` and retains a
complete source or destination frame until the exact matching Foliate relocation
and native presentation proof complete.

### 7.6 Settled native raster owns normal reading

A validated current native raster/deck owns the stable page frame. Background
refill, prewarm, and passive repair are presentation-silent while this authority
remains valid.

Showing the shell cover does not itself invalidate semantically current cached
raster proof. Invalidation requires a real identity change such as publication,
profile, geometry, orientation, Foliate session, destination, or generation
replacement, or explicit resource eviction.

### 7.7 Live WebView exposure is explicit

The live engine may become visible only through `LiveEngineHandoffPending` followed
by a matching host exposure receipt. Native presentation remains visible until
exposure commits. Handback to native presentation follows the same proof-before-
hide rule.

There is no transparent WebView fallback for uncertain or failed native state.

### 7.8 Blocking preparation is the last visual fallback

Preparation may own foreground presentation only when no higher stable visual
owner can truthfully remain visible, or after a user action explicitly requests a
page whose presentation is not ready.

Initial startup may show compact blocking progress over the shell cover because
first page entry is not yet available. Ordinary returned-cover prewarm is hidden
until the user requests entry.

## 8. Preparation And Failure Presentation

Preparation reports work facts; it does not decide UI visibility.

| Situation | Underlying owner | Progress | Input |
|---|---|---|---|
| Initial page preparation | shell cover or neutral shield | visible | cover entry deferred |
| Returned cover, no user request | shell cover | hidden | cover actions remain owned by cover |
| Returned cover, forward requested before ready | shell cover | visible blocking diagnostic | duplicate request coalesced |
| Stable page background refill | settled native page | hidden | page input follows deck readiness |
| Active curl background work | curl gesture | hidden | existing gesture only |
| Preparation failure with stable frame | stable frame | actionable failure/Retry | unsafe page input rejected |
| Preparation failure with no stable frame | neutral blocking owner | actionable failure/Retry | Retry/chrome only |

A failure can never be converted to `Hidden` solely because a cover or raster is
visible. Every blocked user action must have a reachable completion, Retry,
cancellation, or terminal failure transition.

## 9. Interaction Projection

The arbiter derives input from authority:

- `ShellCover`: cover dismissal/forward request and chrome actions only.
- `ShellCoverCommitPending`: no new page gesture; the matching cover transaction
  may complete or fail visibly.
- `CurlGesture`: only the already-claimed gesture stream; new pointers reject with
  a terminal reason.
- `CurlSettlementPending`: new page gestures reject while exact settlement,
  timeout recovery, or visible failure remains reachable.
- `SettledNativePage`: page gestures admit only when the matching deck generation
  is ready; chrome remains available.
- `LiveEngineHandoffPending`: new page gestures reject until matching exposure or
  recovery.
- `LiveEngineExposed`: input follows the explicit engine-owned interaction mode.
- `BlockingPreparation`: Retry, cancellation where safe, and chrome remain
  available; a pending user request continues automatically after readiness.
- `Unavailable`: only terminal navigation/recovery controls remain.

No host may accept a pointer because its local surface is visible when the arbiter
selected a different owner.

## 10. Transition And Liveness Contract

Every deferred state names its authority, success event, failure event, and retry
or cancellation event.

| Deferred state | Success | Failure | Reachable recovery |
|---|---|---|---|
| Shell-cover commit pending | matching native cover commit | asset/commit timeout or host failure | retain page, visible Retry or cancel |
| Cover page-entry pending | matching deck/page proof | preparation or renderer failure | retain cover, Retry |
| Curl settlement pending | matching Foliate relocation plus native proof | stale ack, timeout, renderer failure | restore stable frame, visible Retry |
| Live-engine exposure pending | matching exposure receipt | host/renderer failure or timeout | retain native frame, retry/rebuild |
| Blocking preparation | matching current generation ready | terminal generation failure | fresh-generation Retry |
| Visibility suspended | visibility restored | terminal lifecycle event | restore from current identities or persisted progress |

Timeouts classify a state as failed; they cannot silently fall back to another
owner or leave a permanent pending token.

## 11. Component Boundaries

### 11.1 Common authority reducer

A platform-independent reducer accepts typed facts/events and publishes
`ReaderPresentationDecision`. It owns priority, transaction identity, one-shot
receipt consumption, and liveness outcomes.

### 11.2 Android native host

The host reports layer capability and commit receipts. It applies only the layer
visibility selected by the arbiter. It does not infer that preparation should be
foreground because raster proof was locally reset.

### 11.3 Reader controller

The controller remains responsible for semantic shell-cover intent and reader
navigation. It requests presentation transitions but does not directly hide
native surfaces.

### 11.4 PlayLikeCurl controller

The controller reports gesture, settlement, deck, and failure facts. It cannot
hide its surface before the arbiter authorizes handoff to a committed successor.

### 11.5 Raster preparation controller

The controller reports generation-scoped phase, readiness, progress, and failure.
It does not choose `Cover`, `Compact`, or `Hidden` without the arbiter's current
presentation context.

### 11.6 Compose root

Compose renders chrome and the arbiter-selected diagnostic. It cannot independently
render a preparation overlay from raw preparation state when a different owner is
active.

## 12. Migration Rules

The migration must avoid replacing one race with a flag-compatible shadow race.

1. Introduce the pure authority decision and table-test it before routing production
   visibility through it.
2. Feed existing lifecycle, cover, renderer, raster, Foliate, and exposure facts
   into the decision without changing their semantic owners.
3. In a bounded shadow phase, compare legacy layer/input output with the new
   decision using privacy-safe enums and Booleans.
4. Route layer visibility, preparation UI, and input admission through the same
   authority decision.
5. Add transactional cover commit/dismiss receipts and proof-before-hide ordering.
6. Remove superseded visibility Booleans, inherited receipts, and independent
   overlay exceptions after all callers use the arbiter.

The uncommitted `pageTurnPreparationPresentationVisible` prototype is not an
approved endpoint. Its RED tests document the returned-cover requirement, but a
context Boolean at the host merge does not satisfy this design.

## 13. Automated Verification

Tests must be grouped by behavior and use one focused RED boundary followed by one
focused GREEN boundary per coherent implementation checkpoint.

### 13.1 Pure authority matrix

Cover every authority pair that can be simultaneously reported and prove exactly
one winner. At minimum:

- committed cover over background preparation and stale raster
- active curl over ordinary preparation and WebView visibility
- stable raster over background refill
- explicit engine exposure only after matching handoff receipt
- blocking preparation only when no stable owner exists or user entry is pending
- visible failure over hidden background progress without replacing the frame

### 13.2 Transaction ordering

Prove:

- page surface remains until matching cover commit
- cover remains until matching page proof
- stale cover/exposure receipts cannot hide the current owner
- destination raster remains through exact Foliate settlement
- every renderer-owned stale deck is released exactly once

### 13.3 Interaction consistency

For each authority, assert that layer visibility and input policy come from the
same decision. No test may set independent expected visibility and pointer gates
without an authority state.

### 13.4 Failure and liveness

Prove each deferred state has its success, failure, timeout, and Retry/cancel path.
A failed returned-cover preparation must remain visible and retryable. Hidden
background failure must become visible if a user action requires the failed work.

### 13.5 Lifecycle semantics

Prove `UI_HIDDEN` changes visibility only, genuine running/background pressure
executes the appropriate eviction policy, and Home/restore retains the publication
and latest valid presentation identity.

### 13.6 Integrated bounded sequence

One deterministic host/integration sequence must cover:

```text
page turn
→ completed exact-turn acknowledgement
→ same-session TOC relocation to Chapter 1
→ next page turn
→ return to native cover
→ dismiss native cover
→ Home / UI_HIDDEN
→ restore
```

Assertions include semantic/raster agreement, effective input, one visual owner,
no foreground UI for off-screen work, visible failures, and no indefinite pending
state.

## 14. Runtime Acceptance

Runtime validation remains bounded to the configured pair and the first two Chapter
1 pages in landscape.

A production-signed candidate must demonstrate:

- TOC relocation after a completed page turn updates both semantic and raster state
- the next-page action advances once
- page-to-cover and cover-to-page transitions never expose a blank or mixed owner
- ordinary returned-cover prewarm does not show foreground progress
- a forward request made before readiness either completes automatically or shows
  truthful blocking progress
- Home/restore does not classify `UI_HIDDEN` as memory pressure
- restore retains the publication and latest valid spread
- any actual preparation/renderer failure remains visible and retryable

Raw visual artifacts remain local under `.codex-validation`; no OCR or protected
publication payload may enter retained diagnostics or commits.

## 15. Release Gate

This specification is a Stage 6 blocker. Stage 6 cannot exit and Stage 7 cannot
begin until:

- presentation and interaction are derived from one authority decision
- shell-cover entry and dismissal are proof-before-hide transactions
- one-shot acknowledgements cannot survive unrelated events
- typed lifecycle events distinguish visibility from memory pressure
- the integrated bounded sequence passes automated and signed tablet acceptance
- no local visibility exception remains as an untracked correctness dependency

## 16. Acceptance Summary

- [ ] Exactly one underlying visual authority is selected at all times.
- [ ] Presentation and input policy derive from the same immutable decision.
- [ ] Live Foliate remains exclusive semantic destination authority.
- [ ] Shell-cover entry retains the page until matching cover commit.
- [ ] Shell-cover dismissal retains the cover until matching page proof.
- [ ] Active curl owns every moving page pixel and attached material.
- [ ] Stable native presentation suppresses background progress.
- [ ] User-requested unavailable work becomes visible and eventually completes or
      fails.
- [ ] Real failures remain visible and retryable over a stable frame.
- [ ] `UI_HIDDEN` is never treated as memory pressure.
- [ ] Event-scoped receipts are consumed once and cannot be inherited.
- [ ] Every pending state has a reachable success, failure, and recovery event.
- [ ] The bounded integrated sequence passes before another release candidate is
      accepted.
