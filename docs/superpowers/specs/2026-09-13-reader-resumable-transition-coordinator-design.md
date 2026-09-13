# Reader Resumable Transition Coordinator Design

**Status:** Stage 6 architecture replacement specification and release blocker

**Supersedes the Android control-plane implementation of:**
`docs/superpowers/specs/2026-09-01-reader-hierarchical-presentation-authority-design.md`

**Preserves its visual-policy model and semantic authority boundaries.**

**Active staged plan:**
`docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

## 1. Purpose

Replace the distributed Android reader transition control plane with one resumable
transition coordinator. Every accepted reader event must reach a terminal success,
terminal failure/cancellation, or a persisted deferral with a named wake source.
No callback may leave the reader indefinitely in `Preparing`,
`BlockingInitialPreparation`, or an equivalent non-terminal state.

This remains Stage 6 work. It does not start Stage 7 and does not add iOS, macOS,
or Native implementation requirements.

## 2. Decision Evidence

The hierarchical presentation model improved stale-event rejection, release
accounting, and diagnostics, but the Android implementation distributed transition
ownership among the host, common controller receipts, raster preparation, renderer
deck admission, presentation bridges, and callback-local continuations.

The final bounded callback-level correction passed focused Android host tests and
ReaderDev assembly, then failed the decisive cold-start emulator gate:

- the configured cover rendered;
- raster preparation completed all six targets;
- renderer deck loading completed all six targets;
- no matching prepared/deck-ready presentation was committed;
- the reader remained in blocking preparation;
- two separately verified forward taps could not turn the cover.

The remaining landscape, portrait, reflow, and lifecycle sequence was intentionally
not attempted because the consecutive gate had already failed.

This proves that stricter component-local authority and better diagnostics are not
sufficient. The current Android architecture is not accepted, and no additional
callback-level publication patch is permitted.

## 3. Preserved Authority Boundaries

The redesign preserves these authorities:

- **Live Foliate** exclusively owns publication semantics, committed destinations,
  pagination, DOM ranges, and visible-range meaning.
- **Passive Foliate/raster preparation** produces material only. It cannot select a
  semantic destination or publish a visible owner.
- **PlayLikeCurl** owns page deformation, renderer-thread resources, and the
  mechanics of renderer/deck callbacks.
- **Compose** owns chrome, accessibility, progress, diagnostics, and Retry UI.
- **Common presentation policy** continues to select one visual owner and derive
  compatible input and diagnostic projections.

The coordinator owns sequencing and terminal outcomes. It does not rasterize, draw,
interpret EPUB locations, manipulate the DOM, or perform curl deformation.

## 4. Core Invariant

`ReaderResumableTransitionCoordinator` is the sole Android control-plane owner for
accepted reader presentation events.

Only the coordinator may:

- create or supersede a reader transition;
- issue cross-domain work commands;
- commit a successor visual owner;
- grant or revoke an input lease;
- schedule a transition deadline;
- retain a resumable deferral;
- request release of transition-owned resources;
- publish a terminal transition outcome.

Raster, deck, frame, Foliate, lifecycle, and Compose callbacks enqueue typed facts.
They never directly advance presentation, mutate input policy, initiate another
cross-domain transition, release a resource, or recursively dispatch
common-controller effects. A stale callback reports any resource it still owns to
the coordinator; only the coordinator's release ledger may issue the matching
release command.

## 5. Transition Identity

Every accepted operation has a `ReaderTransitionId` containing:

- reader-session generation;
- coordinator epoch;
- monotonically increasing transition sequence;
- operation kind;
- immutable expected presentation binding;
- optional parent transition identity for a settlement, reflow, restore, or recovery
  successor.

Renderer generations, raster generations, gesture IDs, callback IDs, and relocation
tokens are evidence attached to the transition. None is sufficient by itself to
identify the transition.

Operation kinds are:

- bootstrap native page;
- shell-cover commit;
- cover-to-page entry;
- curl claim and settlement;
- native-to-live handoff;
- live-to-native handback;
- external semantic relocation;
- reflow/profile replacement;
- visibility restore;
- renderer recovery;
- publication close.

## 6. Phase And Outcome Model

Each active transition has exactly one phase:

```text
Accepted
AwaitingPrerequisites
CommandIssued
AwaitingProof
Committing
```

Every physical attempt then terminates as one of:

```text
Succeeded(committedOwner, binding)
Failed(reason, retryability, retainedOwner)
Cancelled(reason, retainedOwner)
Deferred(resumeRecord, retainedOwner)
```

A deferral is terminal for its physical attempt. It preserves a logical demand that
can create a fresh transition only when its named wake event arrives.

Every non-terminal phase must name:

- the proof it awaits;
- its deadline owner;
- its cancellation/supersession behavior;
- its retained truthful visual owner;
- its input lease;
- its exact wake or callback source.

A phase without all six is invalid.

## 7. Single Advancement Loop

The coordinator owns a main-thread mailbox and a non-reentrant `advance()` drain.

1. Normalize ingress into a typed fact.
2. Reject or stale it against the current transition identity.
3. Update common presentation policy through the existing pure reducer.
4. Derive one immutable presentation decision.
5. Record the transition phase or resumable deferral before issuing asynchronous
   work.
6. Issue narrow commands through ports.
7. Enqueue callbacks back into the mailbox.
8. Commit visual owner and input lease atomically after matching proof.
9. Publish exactly one terminal outcome and release obsolete resources exactly once.

Transition registration always precedes a port command. Synchronous renderer
callbacks are therefore queued against an existing transition rather than racing a
separate host/Compose receipt.

## 8. Typed Facts And Commands

Minimum ingress facts:

- user page-turn, cover, or external semantic-relocation intent;
- Foliate destination/relocation receipt;
- viewport/profile replacement;
- raster preparation progress, proof, deferral, or failure;
- renderer reservation and deck ownership callback;
- prepared-frame proof;
- shell-cover post-draw proof;
- WebView handoff proof;
- visibility loss/restore;
- renderer/WebView/resource loss;
- deadline expiry;
- Retry;
- publication close.

Minimum commands:

- request semantic synchronization;
- request raster preparation;
- reserve/build/release a deck;
- request cover or native-frame presentation;
- request live-engine exposure or handback;
- apply an input lease;
- cancel transition-owned work;
- publish diagnostics and terminal outcome.

Commands carry `ReaderTransitionId` and the minimum matching evidence. Ports may
reject stale commands but cannot substitute another transition.

### 8.1 One-shot semantic facts

Foliate destination commits and settlement acknowledgements are distinct one-shot
event facts, not session state.

- An untagged `FoliateDestinationCommitted` remains authoritative semantic input. It
  updates the current destination and supersedes/cancels any incompatible pending
  curl settlement, including within the same Foliate session.
- A `FoliateSettlementAcknowledged` may satisfy a curl transition only when both its
  transition identity and expected binding match the active settlement demand.
  Untagged destination facts never inherit or satisfy an old exact-turn
  acknowledgement.
- Admission of a matching settlement acknowledgement atomically records its
  consumption key before any presentation, input, or renderer consequence is issued.

An app-originated TOC/search/jump intent creates `ExternalSemanticRelocation` before
issuing `RequestSemanticSynchronization`. An unsolicited authoritative
`FoliateDestinationCommitted` creates the same operation directly in `Accepted`
with semantic proof already satisfied. Both routes revoke page input, retain the
pre-relocation frame only as a noninteractive transition shield, request fresh
raster/deck/frame material for the committed destination, and commit only matching
prepared-frame proof. The predecessor deck remains ledger-owned until successor
commit; it is then released exactly once. Failure retains it only as the diagnostic
shield, while Retry, another relocation, publication replacement, or close either
reuses it as predecessor or releases it through the ledger.

A duplicate settlement fact or tagged fact for another transition is ignored after
reporting only a privacy-safe mismatch category. No acknowledgement may be inherited
by a later transition. Deterministic coverage must prove exact-once consumption,
duplicate delivery, an unrelated untagged same-session TOC relocation that clears
the old settlement and updates destination, callback before command return, and a
stale receipt after supersession.

### 8.2 Operation liveness matrix

The durations below are production defaults owned by `ReaderTransitionClock`. Tests
inject a deterministic clock. Each active phase has one coordinator deadline record
containing `hardExpiresAt`, `lastProgressAt`, and an optional no-progress interval.
It schedules one cancellable callback for the earlier of hard expiry and
`lastProgressAt + noProgressInterval`. Matching progress updates `lastProgressAt`
and reschedules that one callback; it never changes `hardExpiresAt`. Retry always
creates a new transition identity; no timeout starts an unbounded automatic retry
loop.

| Operation | Truthful retained owner / input | Required proof | Deadline | Timeout outcome | Named wake or recovery |
|---|---|---|---|---|---|
| Bootstrap native page | shell cover or neutral shield / chrome only | current raster proof + admitted deck + prepared-frame proof | 10 s without progress, 30 s hard | `Failed(MaterialTimeout, retryable)` | `Wake.Retry` |
| Shell-cover commit | prior native/live frame / no new page pointer | matching sized post-draw cover proof | 2 s hard | `Failed(CoverCommitTimeout, retryable)` | `Wake.HostAvailable` or `Wake.Retry` |
| Cover-to-page entry | shell cover / duplicate entry coalesces | current material proof + prepared-frame proof | 10 s without progress, 30 s hard | `Failed(PageEntryTimeout, retryable)` | `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Curl claim and settlement | claimed curl stream / matching pointer only | one-shot Foliate settlement + destination prepared-frame proof | 5 s hard | `Failed(SettlementTimeout, retryable)` with stable terminal frame | `Wake.FoliateDestinationCommitted` or `Wake.Retry` |
| Native-to-live handoff | native frame / no new page pointer | matching WebView content and exposure proof | 2 s hard | `Failed(LiveExposureTimeout, retryable)` | `Wake.WebViewAvailable` or `Wake.Retry` |
| Live-to-native handback | live frame / no new page pointer | matching native prepared-frame proof | 2 s hard | `Failed(NativeHandbackTimeout, retryable)` | `Wake.RendererCapacityAvailable` or `Wake.Retry` |
| External semantic relocation | pre-relocation frame retained only as a noninteractive transition shield / chrome only | authoritative `FoliateDestinationCommitted` + fresh destination raster/deck + prepared-frame proof | 10 s without progress, 30 s hard | `Failed(ExternalRelocationTimeout, retryable)` with visible diagnostic over the retained shield | `Wake.FoliateDestinationCommitted`, `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Reflow/profile replacement | last valid cover/native/live frame / chrome only | fresh-profile raster + deck + prepared-frame proof | 10 s without progress, 30 s hard | `Failed(ReflowTimeout, retryable)` | `Wake.PaginationProfileReady`, `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Visibility loss | latest valid frame identity retained logically / no physical input | persisted deferral accepted | immediate terminal deferral | `Deferred(VisibilityRestore)` | `Wake.VisibilityRestored` or `Wake.Restored` |
| Visibility restore | retained owner if still provable, otherwise shield / chrome only | fresh host, semantic, profile, material, and frame proofs | 10 s without progress, 30 s hard | `Failed(RestoreTimeout, retryable)` | `Wake.HostAvailable`, `Wake.WebViewAvailable`, `Wake.PaginationProfileReady`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Renderer recovery | last truthful non-renderer owner or shield / chrome only | fresh renderer generation + deck + prepared-frame proof | 10 s without progress, 30 s hard | `Failed(RendererRecoveryTimeout, retryable)` | `Wake.RendererCapacityAvailable` or `Wake.Retry` |
| Publication close | no visual owner / navigation only | cancellation acknowledgements and empty release ledger | 2 s drain budget | `Failed(CloseDrainTimeout, nonretryable)` with the reader already visually closed | none; late facts can only enter the closed release ledger |

`AwaitingPrerequisites` uses the listed no-progress/hard deadline record.
`CommandIssued` must advance synchronously to `AwaitingProof` after the port accepts
the command, or fail immediately if the port rejects it. `AwaitingProof` carries the
same transition `hardExpiresAt` with a phase-specific `lastProgressAt`.
`Committing` uses at most the corresponding 2 s frame-proof budget without extending
the transition hard expiry. Publication close is exempt from frame proof and uses
its dedicated 2 s release-drain record. Deadline scheduling failure enqueues an
immediate `DeadlineExpired` fact.

A deferred logical demand has no hidden running timer or command. It is bounded by
the resume-record rules in Section 11. If its named wake never arrives, no physical
attempt remains pending; the retained owner and visible Retry/close path remain
truthful.

## 9. Ports And Component Boundaries

The coordinator depends on narrow ports:

| Port | Responsibility |
|---|---|
| `ReaderSemanticFactsPort` | Foliate facts and semantic synchronization |
| `ReaderRasterPreparationPort` | Generation-scoped raster work and proof |
| `ReaderDeckPort` | Renderer reservation, deck construction, ownership facts, and prepared proof; it never releases independently |
| `ReaderFramePresentationPort` | Cover, native frame, and WebView handoff proof |
| `ReaderInputLeasePort` | Apply coordinator-issued physical input leases |
| `ReaderTransitionResourcePort` | Execute release commands from the coordinator-owned release ledger for decks, callbacks, rasters, and handoff resources |
| `ReaderTransitionWakeStore` | Persist and atomically consume opaque resumable demand |
| `ReaderTransitionClock` | Own one cancellable deadline per active phase |

This avoids a god object: the coordinator owns ordering and outcomes only. Existing
components retain specialized work and expose facts/commands through adapters.

## 10. Required File Boundaries

Create:

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt`
  — transition identity, operation, phase, outcome, facts, pure policy, and
  resume-record schema.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
  — mailbox, advancement loop, journal, deadlines, command issuance, and terminal
  publication.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt`
  — narrow port contracts and adapters.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStore.android.kt`
  — SavedState-backed opaque wake demand only.

Refactor as adapters:

- `ReaderPresentationAuthority.kt` retains pure visual policy and proof validation.
- `ReaderPresentationController.kt` and `ReaderPresentationReceipt.kt` retain common
  state mutation; only the coordinator consumes Android receipts.
- `KomikkuReaderNativeFrameHost.android.kt` becomes a view/port host and stops
  maintaining an authoritative presentation replica.
- `ReaderPresentationHostBridge.android.kt` produces draw/handoff proofs but does not
  initiate transitions or own independent deadlines.
- `ReaderPageRasterPreparationController.android.kt` produces generation-scoped
  facts and performs coordinator commands; it no longer publishes presentation
  consequences directly.
- `ReaderPlayLikeCurlFoliateController.android.kt` retains raster leases, curl,
  renderer callbacks, and exact Foliate settlement; host-local admission currency
  and direct presentation publication are removed.
- `ReaderDeckAdmission.android.kt` retains renderer ownership but accepts a
  coordinator-issued lease keyed by `ReaderTransitionId`.
- `ReaderPageInputSettlementHostController.android.kt` applies coordinator-issued
  leases only.
- `ReaderPresentationLifecycleDelivery.android.kt` is superseded by the coordinator
  mailbox/journal.
- `ReaderProcessState.kt` and `ReaderProcessStateViewModel.kt` retain their unrelated
  UI restoration responsibilities but do not persist coordinator transitions.
  `ReaderTransitionWakeStore.android.kt` separately persists only opaque resumable
  wake demand.
- `ReaderRoot.kt`, `ReaderScreen.kt`, and `ReaderPlatformHosts.kt` render the
  coordinator decision and submit intents/facts through its gateway.

### 10.1 Exhaustive legacy-writer replacement map

Every row is a migration and audit obligation. A slice may complete only when the
listed legacy symbol can no longer write the consequence, the coordinator accepts
the replacement fact, and the port accepts commands only from the coordinator.

| Legacy writer / current consequence | Slice and replacement fact → command | Proof/deadline → terminal outcome | Release rule / wake |
|---|---|---|---|
| `ReaderPresentationAuthority.kt:readerPresentationReduce` mutates authority, binding, diagnostics, input, transition, and cleanup effects | 1 shadow; 3 coordinator-only policy mutation; 5 delete legacy effect dispatch. All normalized facts → `CommitDecision` / `ApplyInputLease` / operation command | Operation-matrix proof and deadline → one typed terminal outcome | Coordinator ledger only / operation-specific fact |
| `ReaderPresentationController.kt:ReaderPresentationControllerReducer.onPresentationEvent` and `onViewerAction` admit input, mutate cover state, and emit Foliate commands | 1 shadow; 3 intent-only gateway; 5 remove presentation branches. Page-turn, cover, external relocation, Retry, and cancel intents → semantic/cover/frame commands | Destination plus frame proof under entry/turn/relocation deadline → commit successor, retain predecessor failure, or cancel | No resources / Foliate commit, material proof, Retry |
| `KomikkuReaderNativeFrameHost.android.kt:ReaderPresentationBindingReporter.update`, `reserve`, `classifyReceipt`, and `commitReceipt` maintain an Android authority replica and deck currency | 1 fact source; 2 replace deck currency; 3 remove replica; 5 delete. Binding, relocation, receipt, and deck observations → register transition / reserve deck | Binding and receipt are proof only under requesting operation deadline → admit once or stale fact | Stale deck enters ledger / matching binding or receipt |
| `ReaderPresentationLifecycleDelivery.android.kt:ReaderPresentationReceiptDispatcher`, `ReaderPresentationHostEffectApplier`, and `KomikkuReaderNativeFrameHost.android.kt:ReaderPresentationEffectHandler` reorder receipts and directly apply Retry/release effects | 1 shadow; 3 mailbox replaces queues; 5 delete. Port receipt or command failure → commit owner / input lease / retry command | Command acknowledgement or failure under operation deadline → success, failure, or host-attached deferral | Ledger confirms release / host attached or decision applied |
| `ReaderPresentationLifecycleDelivery.android.kt:ReaderPresentationLifecycleDelivery.observe`/`retry` and `KomikkuReaderNativeFrameHost.android.kt:retryPresentationLifecycleDelivery` own pending lifecycle ordering | 1 shadow; 4 sole lifecycle ingress; 5 delete. Visibility, pressure, renderer-loss, and close facts → cancel / persist demand / fresh facts | Lifecycle and restore matrix deadlines → cancelled/deferred loss, restored success/failure, or closed terminal | Cancel registrations once / visibility, host, renderer, `Wake.Restored` |
| `ReaderPresentationHostBridge.android.kt:ReaderPresentationHostBridge.update`, `beginCoverCommit`, and `updateLiveEngineExposure` autonomously start transitions and local timeouts | 3 fact/command adapter; 5 delete starters/state. Host, cover post-draw, and WebView facts → cover / exposure / handback commands | Matching cover/WebView proof under 2 s deadline → atomic owner+lease success or predecessor-retaining failure | Unregister only on ledger command / post-draw, WebView, Retry |
| `ReaderPresentationHostBridge.android.kt:ReaderNativePagePresentationPublisher.update`/`onPresentedFrame` polls candidates, owns deduplication and handback timeout, and publishes native presentation | 3 frame port; 5 delete autonomous `update`. Prepared-frame or frame-failure fact → request native frame | Matching presented-frame proof under 2 s deadline → native commit or predecessor-retaining failure | Cancel request only on coordinator command / frame, host, Retry |
| `ReaderPresentationTransitionTimeout.android.kt` and `ReaderPageRelocationDispatchTimeout.android.kt` independently arm deadlines | 3/4 replace; 5 delete. Scheduler can emit only `DeadlineExpired`; coordinator issues schedule/cancel | One matrix deadline → `Failed` or bounded `Deferred`, never local Retry | Cancel once on supersession/loss/reflow/close / named fresh fact or Retry |
| `KomikkuReaderNativeFrameHost.android.kt:applyPresentationFrameOwner`, `dispatchPresentationEvent`, `reportPresentationIdentityIfAvailable`, and `reportPresentationPreparationFacts` apply layers and publish presentation consequences | 1 observed facts; 3 render-only host; 4 lifecycle/reflow facts; 5 delete direct authority. Host binding, preparation, native frame, viewport → coordinator gateway | Matching transition binding and proof → host never chooses outcome | Execute only ledger release commands / attach, profile, frame |
| `ReaderPageInputSettlementHostController.android.kt:updateInputPolicies`, `dispatchPointer`, and `onLifecycleEvent` locally admit input and cancel gesture work | 3 input-lease consumer; 4 cancellation facts; 5 remove policy. Pointer/gesture facts → apply lease / cancel transition | Gesture claim/terminal plus settlement deadline → success or cancellation retaining frame | Ordered cancellation only / semantic acknowledgement, frame, cancellation terminal |
| `ReaderPageRasterPreparationController.android.kt:publishPreparationState`, `prewarmAdjacent`, `finishPrewarm`, `deferPrewarm`, and `retryPreparation` own generation, deferral, Retry, and readiness consequences | 1 facts; 2 command-driven raster port; 4 typed deferrals; 5 delete transition consequences. Raster progress/proof/deferred/failure → prepare/cancel | Exact generation/profile/binding under material deadline → deck command, `Deferred`, or retryable failure | Release passive attempt on ledger command / profile, passive host, canonical commit, Retry |
| `ReaderPageRasterDeferredRetryCoordinator.android.kt` retains deferred closures and resumes from host events | 2/4 replace; 5 delete. Raster-deferral reason → persist demand or cancel | Section 11 one-consumption/15-minute policy → fresh transition or visible Retry | Clear on all terminal/replacement paths / exact reason wake only |
| `ReaderDeckAdmission.android.kt:ReaderDeckAdmission` and `ReaderDeckAdmissionLeaseHost` own reservation, callback ordering, promotion, and release | 2 transition-ID lease; 5 remove host currency. Deck reserved/owned/prepared/rejected/released/capacity → reserve/build or ledger release | Ownership plus prepared proof under material deadline → success, capacity deferral, or renderer failure | One ledger key per reservation / renderer ownership or capacity |
| `ReaderPlayLikeCurlFoliateController.android.kt:onRendererDeckPrepared`, `completeObservedDeckAdmission`, `retryAwaitingDeckAdmission`, `synchronizePresentationDecision`, `onPreparationStateChanged`, `onRasterProofReady`, and release methods own deck readiness, recovery, settlement, and direct release | 2 deck/curl facts; 3 remove presentation publication; 5 remove local admission/recovery writers. Deck/curl/receipt/renderer facts → deck, semantic, frame, or release commands | Exact ownership/deck proof; curl also requires one-shot settlement and frame proof → successor, deferral, failure, or cancel | Adapter drains only on ledger command / renderer, Foliate, capacity, resume |
| `ReaderPageDeckRecoveryCoordinator.android.kt` independently owns repaired-deck build, capacity, prepared state, and generation release | 2 fold into deck transition; 5 delete state machine. Repair/deck/capacity facts → reserve/build or release | Repaired-window plus deck proof under renderer-recovery deadline → success, deferral, failure, or cancel | Ledger owns submitted and unsubmitted cleanup / repair, capacity, Retry |
| `ReaderProcessState.kt` and `ReaderProcessStateViewModel.kt` currently persist broader publication/UI state | 4 add a separate opaque transition wake store; 5 remove legacy transition persistence without deleting unrelated UI restoration. `Wake.Restored` → consume demand / request fresh facts | Fresh-fact restore deadline; never accept restored deck/callback proof → success, failure, or new bounded deferral | Clear demand per Section 11 / host, WebView, profile, renderer, Retry |
| `ReaderRoot.kt`, `ReaderScreen.kt`, and `ReaderPlatformHosts.kt` pass raw events/effects and invoke Retry directly | 3 immutable-decision renderer and intent gateway; 4 restored-wake gateway; 5 remove legacy plumbing. UI intents → coordinator; decision/outcome → Compose | No Compose deadline or proof → render coordinator terminal state only | No release ownership / explicit Retry |

The implementation plan must preserve this table verbatim as its migration ledger,
then add exact tests and deletion checks per row. Discovery of another writer blocks
the active slice until it is added here and assigned a single replacement route.

## 11. Persistence And Wake Contract

A resume record stores only:

- operation category;
- typed retry/deferral reason;
- a cryptographically random 128-bit session correlation nonce that is unrelated to
  publication or user identity;
- issued-at and expiry timestamps from an injected clock;
- a one-consumption restoration budget;
- required wake kind.

It never stores publication text, URLs, hrefs, CFIs, book identifiers, selections,
annotations, raster payloads, bitmap data, renderer handles, WebView state, or
Whispersync content. The correlation nonce and timestamps are internal matching
material and are prohibited from logs, diagnostics, analytics, screenshots, and
crash metadata.

`ReaderTransitionWakeStore` provides atomic `replace`, `consumeIfCurrent`, and
`clearIfMatching` operations. It clears a matching record on success, failure,
cancellation, publication close/replacement, and immediately after a restored
consumption attempt. A record expires after 15 minutes or one restoration attempt,
whichever comes first. Expiry clears it and publishes fresh Retry/close affordance;
it never resumes work automatically.

On recreation, `ReaderTransitionWakeStore.bootstrapRestoredOwner()` atomically takes
the SavedState restoration envelope and binds the recreated coordinator to its
stored correlation nonce before any new reader-session nonce is generated. That
one-time handoff is available only when Android actually restores that SavedState
owner; ordinary navigation, publication replacement, and newly created reader
sessions cannot obtain it and instead generate a fresh nonce after clearing any old
record. The bound coordinator may then call `consumeIfCurrent` exactly once.

Successful consumption creates a fresh transition identity and obtains fresh
semantic, raster, and renderer facts. Failure to obtain the one-time restored-owner
handoff rejects and clears the demand without comparing or storing a book identity.
No proof, deck, callback token, or renderer handle is resurrected.

The finite `ReaderTransitionWakeKind` set is:

- `Wake.Restored`;
- `Wake.HostAvailable`;
- `Wake.WebViewAvailable`;
- `Wake.PaginationProfileReady`;
- `Wake.FoliateDestinationCommitted`;
- `Wake.RasterProofAvailable`;
- `Wake.RendererCapacityAvailable`;
- `Wake.PresentationCommandApplied`;
- `Wake.VisibilityRestored`;
- `Wake.Retry`.

Every operation-liveness row and resume record must name one or more values from
this set. Migration-ledger prose identifies the producing component but its
implementation-plan task must resolve that source to one of these enum values. Facts
may update current state without being eligible to resume a deferred logical
demand.

## 12. Timeout, Cancellation, And Release Invariants

- One deadline exists per active phase. Bridge-, publisher-, and curl-local deadlines
  become fact producers or are removed.
- Deadline expiry terminates as `Failed` or `Deferred`; it cannot leave a pending
  Boolean or `Preparing` phase.
- Supersession, visibility loss, reflow, renderer loss, publication replacement, and
  close cancel deadlines and callback registrations exactly once.
- The coordinator owns one `ReaderTransitionReleaseLedger`. Every admitted or
  callback-discovered deck, raster, registration, and handoff resource has one
  ledger key and one state: `Owned`, `ReleaseCommandIssued`, or `Released`. Only a
  transition-resource command may move `Owned` to `ReleaseCommandIssued`; only a
  matching port fact may move it to `Released`.
- Stale, replaced, failed, and closed resources receive exactly one release command.
  Duplicate and late callback facts may register an unrecorded resource or confirm
  release, but callbacks never execute release themselves. After publication close,
  the terminal coordinator retains a release-only mailbox/ledger until every
  registered callback is detached and every known resource is `Released`; it cannot
  publish presentation or input state. Exceeding the close drain budget records
  `CloseDrainTimeout` but does not disable this release sink.
- Slice 2 activation first freezes legacy deck admissions, inventories every legacy
  renderer-owned deck, and imports each token into the release ledger. A current
  valid deck may be adopted as the truthful predecessor; pending, stale, failed, or
  unprovable decks are drained before coordinator admission opens. If inventory or
  drain proof is incomplete, Slice 2 does not activate and the legacy writer remains
  the sole writer for that reader session.
- A committed cover/native frame remains retained until a matching successor proof
  succeeds or terminal lifecycle invalidates it.
- Reflow invalidates material identity and input leases, not Foliate semantic
  authority or a valid committed cover.
- A callback for an old transition can enqueue only a stale fact and any resource
  identity it owns. The current transition remains unchanged; the coordinator then
  applies the release ledger.

## 13. Input Contract

Input is an epoch-scoped coordinator lease derived from the same decision as the
visual owner.

- New physical pointers are denied unless the current committed owner and phase
  authorize them.
- An already claimed curl stream survives only for its matching gesture transition.
- Blocking preparation cannot admit page input merely because raster or deck work
  completed locally.
- A successful visual commit and its input lease become visible atomically.
- Failure retains truthful predecessor input behavior or denies input with an
  actionable diagnostic; it never silently accepts and drops a turn.

## 14. Migration Slices

Each slice has one writer. Legacy and coordinator paths may be observed together but
may never both mutate the same consequence.

1. **Shadow journal:** record facts and predict enum outcomes while legacy behavior
   remains authoritative. Compare privacy-safe phase/outcome/mismatch facts only.
2. **Deck-admission cutover:** after the Section 12 freeze/inventory/adoption/drain
   protocol succeeds, coordinator leases own reserve, callback, prepared, ready,
   stale, and release transitions. The activation is atomic per reader session; no
   fallback to the legacy writer is allowed after coordinator admission opens. This
   removes the demonstrated cold-start deadlock class.
3. **Presentation/input cutover:** cover, native-frame, handoff, and input changes
   consume coordinator commands only. Remove the host binding-reporter authority
   replica and host-local recovery continuations.
4. **Lifecycle/reflow cutover:** visibility, restoration, profile replacement, and
   saved-state wakes enter only through the coordinator.
5. **Deletion:** remove compatibility flags, duplicate queues, independent timeout
   owners, and callback-driven transition starters once no adapter can publish a
   visual/input consequence directly.

Each slice requires deterministic callback-before-ownership,
ownership-before-callback, supersession, timeout, restore, and release-once coverage.
Passing tests do not replace the bounded running-app gate.

## 15. Failed Patch Disposition

Retain conceptually:

- exact renderer/deck admission identity;
- callback-before-ownership and ownership-before-callback handling;
- stale callback release-once fences;
- raster proof plus matching active deck as readiness prerequisites;
- cover post-draw proof;
- fresh material generation after visibility restore/reflow;
- adversarial reentrant callback coverage.

Supersede:

- `ReaderPresentationBindingReporter` as a second authoritative state machine;
- separate receipt and lifecycle queues as transition owners;
- host-local deferred recovery observations;
- bridge `update()` calls that initiate transitions from observed view state;
- direct presentation publication from raster/deck/curl callbacks;
- duplicated bridge, native-frame, and curl deadlines;
- any callback patch intended solely to force a later `deck-prepared` or `Ready`
  publication.

The current failed uncommitted Task382 patch remains evidence. It must not be
committed or treated as a successful checkpoint.

## 16. Exact Consecutive Acceptance Gate

One continuous Android ReaderDev run against the configured EPUB must pass in this
order:

1. **Cold landscape start** — configured cover commits; initial raster/deck work
   reaches terminal coordinator success or a visible retryable failure. Completed
   raster/deck work without committed frame proof is a failure.
2. **Landscape cover → page → cover** — each action produces one transition and one
   committed result, without blank/mixed ownership or off-screen blocking progress.
3. **Portrait reflow** — a new profile transition replaces material identity while
   retaining a truthful predecessor frame.
4. **Portrait cover → page → cover** — replacement deck proof commits before input;
   no old-profile proof is reused.
5. **Minimize/restore** — every in-flight transition terminates or persists a named
   wake; the book remains open and restores a truthful frame.
6. **Post-restore page turn** — one right-side action advances exactly once.

Any freeze, indefinitely blocking phase, invalid input rejection, dropped accepted
turn, stale proof reuse, duplicate advancement, or lost resource owner fails the
architecture. The response to failure is coordinator-level correction, not a return
to distributed callback patches.

### 16.1 Authoritative validation and delivery order

1. Task384 completes all five migration slices. The public synthetic fixture must
   then pass deterministic common/Android host coverage for the same transition
   ordering, all operation-liveness rows, one-shot semantic facts, persistence
   expiry, and release-ledger adoption/drain. Then run the final consolidated
   JavaScript, common, Android host, renderer, build, and lint gates.
2. After those gates pass, Task382 closes only as the preserved failed/superseded
   callback attempt; none of its superseded patch is admitted into the coordinator
   checkpoint. Commit and push the verified Task384 implementation to `fork`. Build
   and freeze ReaderDev from that exact pushed commit.
3. Task 339 runs the Section 16 consecutive configured-EPUB gate on an explicitly
   owned emulator. ReaderDev is permitted only here; captures remain local under
   `.codex-validation`, with no OCR or protected reader payload retention.
4. Only after Task 339 passes may Task 338 create the GitHub-managed
   production-signed candidate from the same accepted source checkpoint. Verify its
   provenance, package/version, hash, and pinned signing certificate before device
   use.
5. Task 282 installs only that production-signed candidate on an explicitly owned
   approved tablet and performs the existing bounded configured-pair acceptance:
   first two landscape pages of Chapter 1. It does not broaden into portrait,
   chapter-by-chapter, or ReaderDev tablet testing.
6. Task 283 reconciles every Stage 6 requirement, legacy-writer deletion, deferral,
   failure, test result, runtime artifact, source commit, signed artifact, and tablet
   observation. Stage 6 closes only when the ledger has no unclassified gap. Stage 7
   remains blocked until then.

A failure at any step returns to the owning coordinator migration slice and repeats
only the affected lower gates before one final consolidated pass. Existing release
assets remain immutable.

## 17. Non-Goals

- Stage 7 or WordSync deferral.
- iOS, macOS, or Native implementation or validation.
- Replacing Foliate semantic authority.
- Replacing PlayLikeCurl deformation mechanics.
- Transparent WebView fallback for missing native authority.
- Broad chapter-by-chapter acceptance.
- Persisting reader content or private semantic locations.
- Adding compatibility flags without a named deletion slice.

## 18. Architecture Completion Criteria

The implementation plan must carry every Section 10.1 legacy-writer row as a
migration ledger and, for each task, name the coordinator fact, command, deadline,
proof, terminal outcome, release rule, wake source, test gate, and legacy deletion
check. An unmapped writer or dual consequence path blocks implementation.

Implementation is release-ready only after:

- all coordinator transition identities and outcomes are typed;
- no Android adapter directly publishes visual/input consequences;
- no accepted transition lacks a deadline and terminal route;
- release accounting is exactly once;
- the exact consecutive emulator gate passes;
- the bounded production-signed tablet gate passes afterward.
