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

**Task384 Task 6 activation amendment:** Task 1 through Task 5 are inactive,
preparatory checkpoints. Their published 541-test Task 5 evidence remains unchanged,
but preflight proved that production cannot truthfully activate through their current
interfaces. Task 6 therefore replaces the former frame/input-only package with one
atomic per-session activation package. Production remains `Shadow`/`LegacyOnly`; no
production coordinator route is active at the time of this amendment.

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

### 4.1 Per-session activation state and publication barrier

One Android-main-thread `ReaderSessionActivationCoordinator` owns:

```kotlin
internal enum class ReaderSessionActivationState {
    Legacy,
    Freezing,
    DrainingLegacy,
    ReadyToCommit,
    RestoringLegacy,
    ActivationBlocked,
    Activated,
    ReleaseOnly
}
```

`Legacy` means every production consequence still uses the legacy routes.
`Freezing`, `DrainingLegacy`, and `ReadyToCommit` are inactive preparation states:
coordinator command egress remains closed. `RestoringLegacy` keeps both consequence
route sets closed while a pre-drain checkpoint is rebuilt. `ActivationBlocked` is a
coherent fail-visible state with the proven predecessor (if any), chrome/navigation
input only, and release handling; it is never represented as restored legacy.
`Activated` and command egress can become visible only through the successful atomic
installation below. From `Activated`, the only terminal state is `ReleaseOnly`, never
`Legacy`.

The installation input contains the complete initial publication:

```kotlin
data class ReaderInitialActivationDecision(
    val seed: ReaderAdoptedPredecessorSeed,
    val adoptedResource: ReaderImportedLegacyResourceRegistration?,
    val requestedLease: ReaderTransitionInputLease,
    val physicalLease: ReaderTransitionInputLease
)

data class ReaderActivatedSessionInstallation(
    val ports: ReaderActivatedSessionPorts,
    val initialDecision: ReaderInitialActivationDecision
)

data class ReaderActivatedSessionSnapshot(
    val ports: ReaderActivatedSessionPorts,
    val initialDecision: ReaderInitialActivationDecision,
    val state: ReaderSessionActivationState = ReaderSessionActivationState.Activated,
    val commandEgressOpen: Boolean = true
)

fun installActivatedSession(
    installation: ReaderActivatedSessionInstallation
): ReaderActivationInstallResult
```

`ReaderInitialActivationDecision` requires neutral iff seed identity/kind/binding and
`adoptedResource` are absent. Otherwise the imported wrapper's physical identity equals
the seed identity; its registration owner is `AdoptedPredecessor(seed.id)`; its kind
equals `seed.resourceKind`; and its retirement session/epoch equal the seed. Native-page
or curl seeds therefore accept only `Deck`; shell-cover or live-engine seeds only
`FrameHandoff`. No binding-only inference or universal `FrameHandoff` default exists.

`ReaderActivatedSessionPorts` is not a public completeness claim. Production installation
accepts only a capability-authenticated package constructed inside
`KomikkuReaderNativeFrameHost` from the exhaustive real gateway, semantic, allocation,
raster, deck, exact-frame, sole owner/input publication, input-safety, resource,
release-sink, lifecycle-fact, and fact-only-timer adapters. No public Boolean, generic
`complete()` factory, production-visible no-op, or all-rejecting package can mint that
capability. Test fixtures use an unmistakably test-only package type that is not accepted
by the production installation signature. Successful installation also proves that no
`Shadow`/`LegacyOnly` route and no legacy semantic/material/frame/input consequence
writer remains reachable from the installed composition root.

Before this call, the frozen input adapter computes `physicalLease`, verifies that it
is no broader than `requestedLease`, and includes both in the immutable payload. The
barrier side-effect-freely validates every route; the adopted/neutral owner; the
selected row's exact resource kind, binding, complete physical identity, and provenance;
the optional imported registration; and both leases. It then constructs one immutable
`ReaderActivatedSessionSnapshot` and replaces the composition root's single snapshot
reference in one non-callback, non-suspending Android-main-thread write. Gateway,
visible-owner, physical-input, activation-state, and command-egress readers all use
that same reference; no route or publication has a separate install field. The write
simultaneously:

- switches gateway semantic routing and the command-bound semantic dispatcher;
- switches material-generation allocation and raster/deck admission;
- switches frame request/proof and successor owner/input publication;
- switches fact-only lifecycle compatibility and selects the sole existing command-scoped
  fact-only physical-timer source after overlapping legacy consequence/timer writers are
  fenced; for narrowing operations that source binds exactly one registration only after
  `Applied(Retained)` and before first timer-requiring work, while the production
  coordinator clock remains inactive;
- publishes the seed's initial visible owner, binding, and physical input lease;
- switches exact resource observation and the permanent release-only sink;
- marks the session `Activated` and makes coordinator command egress visible.

There is no public `publishInitial` or `openCommandEgress` step. Observers can see only
the complete legacy snapshot or the complete activated snapshot with its initial owner
and physical lease; an installed-but-unpublished state is unrepresentable. Failure
before any destructive drain may cancel the freeze and remain `Legacy`. Failure after
destructive drain starts must enter `RestoringLegacy`, and may return to `Legacy` only
after the restoration protocol in Section 12 commits a complete legacy snapshot. A
restoration failure enters `ActivationBlocked`. A failure after successful atomic
installation remains coordinator-owned and fails closed without active-session
fallback.

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

Legacy resources never retroactively receive a fabricated `ReaderTransitionId`.
Resource ownership is instead explicit:

```kotlin
@JvmInline
value class ReaderAdoptedPredecessorSeedId internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

sealed interface ReaderTransitionResourceOwnerId {
    data class TransitionOwned(
        val transitionId: ReaderTransitionId
    ) : ReaderTransitionResourceOwnerId

    data class AdoptedPredecessor(
        val seedId: ReaderAdoptedPredecessorSeedId
    ) : ReaderTransitionResourceOwnerId
}

data class ReaderTransitionResourceKey(
    val ownerId: ReaderTransitionResourceOwnerId,
    val kind: ReaderTransitionResourceKind,
    val opaqueId: Long
) {
    init {
        require(opaqueId > 0L)
    }
}

data class ReaderResourceRetirementOrder(
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val sequence: Long
) {
    init {
        require(readerSessionGeneration > 0L)
        require(coordinatorEpoch > 0L)
        require(sequence > 0L)
    }
}

data class ReaderTransitionResourceRegistration(
    val key: ReaderTransitionResourceKey,
    val retirementOrder: ReaderResourceRetirementOrder
)

data class ReaderImportedLegacyResourceRegistration(
    val physicalIdentity: ReaderLegacyPhysicalIdentity,
    val registration: ReaderTransitionResourceRegistration
) {
    init {
        require(
            physicalIdentity.domain.readerSessionGeneration ==
                registration.retirementOrder.readerSessionGeneration
        )
    }
}
```

A transition-owned resource key must name the issuing transition. A legacy physical
identity imported at handoff keeps `AdoptedLegacy` provenance and uses the single
adopted-predecessor seed identity; it does not masquerade as work created by an
operation. Import allocates a collision-free coordinator-global positive key `opaqueId`;
it never reuses the source-local opaque token. The imported wrapper preserves the exact
complete legacy identity beside that key. The release ledger allocates
`ReaderResourceRetirementOrder` exactly once when it first registers a complete physical
identity, including during legacy import. Duplicate observations of that same complete
identity reuse the registration; equal source-local values under another source or
domain do not. Retirement comparison, bounded tombstone advancement, and late-
confirmation rejection use this session/epoch/sequence order, never
`ReaderTransitionId`, owner kind, operation kind, or a currently active transition. A
release issuer may be the owning transition or the session activation/close authority,
so releasing an adopted predecessor never requires borrowing or fabricating a transition
identity.

Operation kinds are exactly eleven and remain:

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

A phase without all six is invalid. `ReaderTransitionProofKind` includes
`FrameTargetPreparation` and `OwnerAndInputPublicationAcknowledgement`. An awaiting-
target phase awaits only the former. Every successor `Committing` phase awaits only the
latter and lists only `OwnerAndInputPublicationApplied` and
`OwnerAndInputPublicationRejected` callback sources; consumed `PreparedFrame` is no
longer awaited. Retained-owner input publication uses the same acknowledgement proof but
matching `Applied(Retained)` returns the active operation to pre-work rather than
publishing success.

## 7. Single Advancement Loop

The coordinator owns a main-thread mailbox and a non-reentrant `advance()` drain.

1. Normalize ingress into a typed fact.
2. Reject or stale it against the current transition identity.
3. Update common presentation policy through the existing pure reducer.
4. Derive one immutable presentation decision.
5. If accepted work narrows/revokes input, publish retained-publication waiting state and
   issue only that transaction; exact `Applied(Retained)` returns to pre-work, while
   rejection terminates before successor work or timer binding.
6. Record every later transition phase before commands. After prerequisites, coordinator
   selects/allocates the exact registration, publishes awaiting-target, and issues
   `PrepareFrameTarget`; only matching FIFO `FrameTargetPrepared` permits presentation.
7. Convert every synchronous port result into a typed fact and append it to the same
   mailbox while `advancing`; callbacks follow the same queue and never reduce recursively.
8. Consume exact prepared-frame proof, remove it from awaited proofs, publish successor
   `Committing` awaiting only publication acknowledgement, and issue only the combined
   successor transaction.
9. Only exact matching `Applied(Successor)` may publish success/committed; publish terminal
   state before timer cancellation and predecessor release. Rejection publishes failure
   first and releases successor only.

Transition registration always precedes a port command. Synchronous renderer callbacks
and synchronous combined-publication results are therefore queued against an existing
transition rather than racing a separate host/Compose receipt or recursively advancing
the journal.

## 8. Typed Facts And Commands

Minimum ingress facts:

- user page-turn, cover, or external semantic-relocation intent;
- Foliate destination/relocation receipt;
- material-binding allocation proof;
- viewport/profile replacement;
- raster preparation progress, proof, deferral, or failure;
- renderer reservation and deck ownership callback;
- frame-target prepared or rejected fact for the coordinator-supplied registration;
- prepared-frame proof echoing one immutable prepared target;
- exact owner/input combined-publication applied or rejected acknowledgement;
- shell-cover post-draw proof;
- WebView handoff proof;
- visibility loss/restore;
- renderer/WebView/resource loss;
- deadline expiry;
- Retry;
- publication close.

Minimum commands:

- request semantic synchronization;
- allocate one exact material binding;
- request raster preparation;
- reserve/build/release a deck;
- prepare one exact sealed frame target against a coordinator-selected/allocated
  registration, then request presentation only after matching target-prepared fact;
- atomically publish either one successor owner/input pair or one retained-owner/
  transitional-input pair through the sole owner/input publication port;
- cancel transition-owned work;
- release an exact resource;
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

An app-originated TOC/search/jump intent creates `ExternalSemanticRelocation`, then
publishes retained owner/input narrowing and waits for exact `Applied(Retained)` before
issuing `RequestSemanticSynchronization`. An unsolicited authoritative
`FoliateDestinationCommitted` creates the same operation with semantic proof already
satisfied but still performs the retained publication gate before successor physical
work. Both retain the pre-relocation frame as a noninteractive shield, allocate fresh
material, request raster/deck work, run coordinator-owned target preparation then exact
presentation, consume `PreparedFrame`, enter `Committing`, and commit only after exact
`Applied(Successor)`. The predecessor deck remains ledger-owned
until acknowledged successor commit; it is then released exactly once. Failure retains it only as
the diagnostic
shield, while Retry, another relocation, publication replacement, or close either
reuses it as predecessor or releases it through the ledger.

A duplicate settlement fact or tagged fact for another transition is ignored after
reporting only a privacy-safe mismatch category. No acknowledgement may be inherited
by a later transition. Deterministic coverage must prove exact-once consumption,
duplicate delivery, an unrelated untagged same-session TOC relocation that clears
the old settlement and updates destination, callback before command return, and a
stale receipt after supersession.

### 8.2 Executable semantic handles and causally isolated receipts

Every coordinator-issued semantic command carries a content-free, session-local,
in-memory capability:

```kotlin
@JvmInline
value class ReaderSemanticRequestHandle internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

sealed interface ReaderSemanticSynchronizationIntent {
    val requestHandle: ReaderSemanticRequestHandle
}

data class ReaderExternalRelocationIntent(
    val source: ReaderExternalRelocationSource,
    override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

@JvmInline
value class ReaderSemanticCommandSlotId internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

internal interface ReaderSemanticCommandPort {
    fun synchronize(
        command: ReaderTransitionCommand.RequestSemanticSynchronization,
        onReceipt: (ReaderPresentationEventReceipt) -> Unit
    ): ReaderPortCommandResult
}
```

Page turn and cover-entry semantic intents carry the same handle in addition to their
public direction/gesture metadata. The handle resolves exactly once in a bounded
Android session registry to a private executable closure containing the real Foliate
destination/action. Hrefs, CFIs, selections, annotation payloads, and other semantic
content never enter common state, persistence, diagnostics, equality, or logs. A
missing, already-taken, expired, superseded, or wrong-session handle rejects the
command before legacy dispatch or Foliate mutation; it is never reconstructed from
`source`.

Before taking the executable request, the port reserves one dedicated command slot
containing `ReaderSemanticCommandSlotId`, the exact coordinator-issued
`ReaderTransitionId`, and that request handle. The limits are eight active command
slots and sixteen pending request handles per reader session. Each private execution
receives a callback closure capturing only its own slot ID; there is no shared
"next callback" slot and an unsolicited Foliate callback cannot consume a command
slot. The slot is consumed at most once and supplies its identity to the common
receipt/controller path:

```kotlin
sealed interface ReaderPresentationEventOrigin {
    data object NonSemantic : ReaderPresentationEventOrigin
    data object UnsolicitedFoliate : ReaderPresentationEventOrigin
    data class SemanticCommand(
        val transitionId: ReaderTransitionId,
        val slotId: ReaderSemanticCommandSlotId
    ) : ReaderPresentationEventOrigin
}

data class ReaderPresentationEventReceipt(
    val event: ReaderPresentationEvent,
    val preVersion: ReaderPresentationReceiptVersion,
    val version: ReaderPresentationReceiptVersion,
    val disposition: ReaderPresentationEventDisposition,
    val postState: ReaderPresentationState,
    val effects: List<ReaderPresentationEffect>,
    val origin: ReaderPresentationEventOrigin
) {
    val originatingTransitionId: ReaderTransitionId?
        get() = (origin as? ReaderPresentationEventOrigin.SemanticCommand)?.transitionId
}

fun ReaderPresentationControllerReducer.onPresentationEvent(
    controller: ReaderController,
    event: ReaderPresentationEvent,
    origin: ReaderPresentationEventOrigin
): ReaderControllerStep

fun readerPresentationEventTransition(
    preState: ReaderPresentationState,
    preVersion: ReaderPresentationReceiptVersion,
    shellCoverVisible: Boolean,
    event: ReaderPresentationEvent,
    origin: ReaderPresentationEventOrigin
): ReaderPresentationEventTransition
```

The receipt's `originatingTransitionId` is copied only from the consumed
`SemanticCommand` slot; no reducer, gateway, host, or callback may infer it. Slots and
handles retire on accepted callback, command rejection/throw, supersession, deadline,
publication replacement, or close. A slot-retirement fence stores one contiguous
retired-through sequence and at most 32 out-of-order sequences; active slots take
precedence, overflow rejects new semantic work fail-closed, and no tombstone collection
can grow without bound. If Foliate calls back before `synchronize` returns, the tagged
receipt is appended to the existing coordinator FIFO and is never reduced recursively.
Wrong-slot, duplicate, stale, or untagged settlement receipts cannot satisfy exact
settlement proof. Unsolicited untagged relocation remains authoritative under Task 5's
existing same-publication arbitration and uses `UnsolicitedFoliate`.

### 8.3 Material causality and allocation

Semantic bindings do not carry authority to invent renderer generations. After exact
semantic proof, the journal requests one fresh allocation:

```kotlin
data class ReaderMaterialGenerationAllocation(
    val transitionId: ReaderTransitionId,
    val allocatedBinding: ReaderPresentationBinding,
    val preparationGeneration: Long,
    val rasterGeneration: Long,
    val textureGeneration: Long
)

sealed interface ReaderTransitionFact {
    val transitionId: ReaderTransitionId?

    data class MaterialBindingAllocated(
        override val transitionId: ReaderTransitionId,
        val allocation: ReaderMaterialGenerationAllocation
    ) : ReaderTransitionFact
}

sealed interface ReaderPortCommandResult {
    data object Accepted : ReaderPortCommandResult
    data class Rejected(
        val reason: ReaderTransitionFailureReason
    ) : ReaderPortCommandResult
}

internal interface ReaderMaterialGenerationAllocationPort {
    fun allocate(
        command: ReaderTransitionCommand.AllocateMaterialBinding,
        onFact: (ReaderTransitionFact.MaterialBindingAllocated) -> Unit
    ): ReaderPortCommandResult
}
```

`AllocateMaterialBinding` carries the exact semantic binding. The port allocates fresh
monotonic preparation, raster, and texture generations and returns an
`allocatedBinding` containing all three. Before emitting the fact it verifies the
transition, Foliate session, publication generation, viewport/profile generation,
and exact semantic destination. `RequestRasterPreparation` and `ReserveDeck` carry
`ReaderMaterialGenerationAllocation`, accept only its exact allocated binding, and
return a typed rejection before any physical prepare or reserve when validation
fails. Raster/deck code cannot copy nullable generations from an unallocated semantic
binding. `MaterialBindingAllocation` is a required proof for every operation that can
request raster or deck work; it does not add an operation kind.

### 8.4 Coordinator-owned target preparation and two-phase owner/input publication

A binding is not a frame target, and an adapter cannot mint resource ownership. Frame
presentation uses two coordinator-issued commands. After material/deck prerequisites,
the coordinator first selects the exact registration: native/curl use the already
admitted `Deck`; shell/live receive a transition-owned `FrameHandoff` allocated by the
coordinator release ledger. The journal publishes an awaiting-target `AwaitingProof`
phase whose contract awaits exactly
`ReaderTransitionProofKind.FrameTargetPreparation` and lists only
`FrameTargetPrepared` and `FrameTargetPreparationRejected` callback sources. It then
issues:

```kotlin
data class PrepareFrameTarget(
    override val transitionId: ReaderTransitionId,
    val specification: ReaderTransitionFrameTargetSpecification,
    val registration: ReaderTransitionResourceRegistration
) : ReaderTransitionCommand
```

The responsible adapter binds immutable physical state to that coordinator-supplied
registration and returns `FrameTargetPrepared(transitionId, target)` through the same
FIFO. It cannot allocate/register ownership or retirement order. A synchronous prepare
rejection becomes `FrameTargetPreparationRejected` queued while `advancing`, never a
recursive reduction. Only a matching fact
stores `frameTarget`, consumes the target-preparation proof, and permits
`RequestFramePresentation(target)`. Rejection, supersession, and close retire any
returned one-shot handle and release the exact registration once. A stale or wrong
target fact cannot advance the transition and can only report its exact resource to the
release ledger. This proof/fact/command does not add a twelfth operation.

The common contract is sealed by kind rather than forcing impossible fields onto every
target:

```kotlin
sealed interface ReaderTransitionFrameTargetSpecification {
    val transitionId: ReaderTransitionId
    val readerSessionGeneration: Long
    val publicationGeneration: Long
    val binding: ReaderPresentationBinding
    val geometry: ReaderTransitionFrameGeometry
    val requestSequence: Long

    data class ShellCover(
        override val transitionId: ReaderTransitionId,
        override val readerSessionGeneration: Long,
        override val publicationGeneration: Long,
        override val binding: ReaderPresentationBinding,
        val hostToken: ReaderShellCoverHostToken,
        val coverGeneration: Long,
        val viewportGeneration: Long,
        override val geometry: ReaderTransitionFrameGeometry,
        override val requestSequence: Long
    ) : ReaderTransitionFrameTargetSpecification

    data class NativePage(
        override val transitionId: ReaderTransitionId,
        override val readerSessionGeneration: Long,
        override val publicationGeneration: Long,
        override val binding: ReaderPresentationBinding,
        val allocation: ReaderMaterialGenerationAllocation,
        val hostToken: ReaderNativePageHostTokenState,
        val deckTarget: ReaderPlayLikeCurlDeckTargetIdentity,
        override val geometry: ReaderTransitionFrameGeometry,
        override val requestSequence: Long
    ) : ReaderTransitionFrameTargetSpecification

    data class CurlSettlementTerminalFrame(
        override val transitionId: ReaderTransitionId,
        override val readerSessionGeneration: Long,
        override val publicationGeneration: Long,
        override val binding: ReaderPresentationBinding,
        val allocation: ReaderMaterialGenerationAllocation,
        val gestureId: ReaderTransitionGestureId,
        val settlement: ReaderPageTurnSettlementAck,
        val deckTarget: ReaderPlayLikeCurlDeckTargetIdentity,
        override val geometry: ReaderTransitionFrameGeometry,
        override val requestSequence: Long
    ) : ReaderTransitionFrameTargetSpecification

    data class LiveWebView(
        override val transitionId: ReaderTransitionId,
        override val readerSessionGeneration: Long,
        override val publicationGeneration: Long,
        override val binding: ReaderPresentationBinding,
        val handoffToken: ReaderLiveHandoffToken,
        val direction: ReaderLiveHandoffDirection,
        val claimIdentity: ReaderLiveHandoffClaimIdentity,
        val viewportGeneration: Long,
        override val geometry: ReaderTransitionFrameGeometry,
        override val requestSequence: Long
    ) : ReaderTransitionFrameTargetSpecification
}

sealed interface ReaderTransitionFrameTarget {
    val handle: ReaderTransitionFrameTargetHandle
    val specification: ReaderTransitionFrameTargetSpecification
    val resource: ReaderTransitionResourceRegistration

    data class ShellCover(/* exact ShellCover specification + FrameHandoff */) :
        ReaderTransitionFrameTarget
    data class NativePage(/* exact NativePage specification + admitted Deck */) :
        ReaderTransitionFrameTarget
    data class CurlSettlementTerminalFrame(
        /* exact Curl specification + admitted Deck */
    ) : ReaderTransitionFrameTarget
    data class LiveWebView(/* exact LiveWebView specification + FrameHandoff */) :
        ReaderTransitionFrameTarget
}
```

Shell requires a typed host token and exact cover/publication/viewport/profile geometry;
it does not pretend to own native material generations. Native carries exact material
allocation, explicit `Present(token)` or `AuthoritativeAbsent`, exact PlayLikeCurl deck
identity, and geometry. Curl carries exact allocation, gesture, settlement, deck
identity, and geometry. Live carries typed handoff token, direction, claim identity, and
exact publication/viewport/profile geometry. Every request sequence is positive. There
is no pre-command `frameSequence`; callback-produced presented-frame sequence exists only
inside `PreparedFrame.frameOwner` proof evidence.

Admission validates exact transition/session/publication/binding, kind-specific identity,
resource owner and kind, geometry generations, session/publication-scoped one-shot
handle, positive request sequence, and legal token presence/authoritative absence.
`PreparedFrame` echoes the same target handle and registration. The host bridge, native
publisher, and PlayLikeCurl adapter cannot poll current candidate/decision, select a
same-binding target, infer/fabricate a token/resource, replace registration, or derive
retirement order. Curl may finish on stable native ownership without another operation
only while preserving exact gesture/settlement/PlayLikeCurl deck identity.

For every operation that narrows or revokes input, acceptance first publishes an
`AwaitingProof` retained-publication phase awaiting exactly
`OwnerAndInputPublicationAcknowledgement` and listing only
`OwnerAndInputPublicationApplied` and `OwnerAndInputPublicationRejected`. It emits only
`PublishRetainedOwnerAndInputLease`. Before exact `Applied(Retained)`, semantic,
allocation, raster, deck, target-preparation, frame-presentation, and timer-requiring
physical commands are prohibited. `Applied(Retained)` returns the same active operation
to its appropriate pre-work `Accepted`/`AwaitingPrerequisites` phase without success;
`Rejected(Retained)` terminates before successor work. No activated `ApplyInputLease`
route exists.

Consuming a matching `PreparedFrame` removes `PreparedFrame` from awaited proofs and
publishes successor `Committing`. Every successor `Committing` phase awaits exactly
`ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement`, lists exactly
`OwnerAndInputPublicationApplied` and `OwnerAndInputPublicationRejected` as callback
sources, retains predecessor truth, timer, target, and successor resource, and emits only
`CommitOwnerAndInputLease`.

```kotlin
sealed interface ReaderOwnerAndInputPublicationSubject {
    data class Successor(
        val targetHandle: ReaderTransitionFrameTargetHandle,
        val preparedFrameResource: ReaderTransitionResourceRegistration
    ) : ReaderOwnerAndInputPublicationSubject
    data class Retained(
        val retainedResource: ReaderTransitionResourceRegistration
    ) : ReaderOwnerAndInputPublicationSubject
}

sealed interface ReaderOwnerAndInputPublicationResult {
    data class Applied(
        val transitionId: ReaderTransitionId,
        val subject: ReaderOwnerAndInputPublicationSubject,
        val publishedOwner: ReaderPresentationFrameOwner,
        val publishedBinding: ReaderPresentationBinding,
        val finalPhysicalInputLease: ReaderTransitionInputLease,
        val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
    ) : ReaderOwnerAndInputPublicationResult

    data class Rejected(
        val transitionId: ReaderTransitionId,
        val subject: ReaderOwnerAndInputPublicationSubject,
        val publicationIdentity: ReaderOwnerAndInputPublicationIdentity,
        val reason: ReaderTransitionFailureReason
    ) : ReaderOwnerAndInputPublicationResult
}
```

The sole Android-main-thread publication port is synchronous, no-callback, and
non-suspending. It validates every exact field, lets the input controller only narrow or
veto physical safety, and either replaces one immutable owner/input snapshot or returns
`Rejected(AtomicPublicationRejected)`/documented narrower reason with a no-write
guarantee. Unit, Boolean, accepted-only, and callback acknowledgements are forbidden.
The dispatcher immediately converts the result into the exact Applied/Rejected fact and
appends it while `advancing`; it never reduces recursively.

Only exact `Applied(Successor)` may publish `Succeeded`/committed, then cancel the timer,
then release predecessor. Rejected successor publishes failure first, retains
predecessor owner/input, and releases successor only. Wrong, stale, duplicate,
cross-transition, cross-resource, or untagged acknowledgements are inert and cannot
release predecessor. Initial installation remains the distinct Section 8.5 one-write
routes/initial-owner/input/`Activated`/egress transaction with no acknowledgement gap.
Journal state always precedes asynchronous effects: awaiting-target precedes preparation,
`Committing` precedes commit, and terminal state precedes release.

Raw frame-target handles, host/handoff-token values, handoff-claim identities,
publication identities, request/presented-frame sequences, and resource registrations
are in-memory only and forbidden from logs, diagnostics, analytics, screenshots, crash
metadata, equality diagnostics, and persistence. Only bounded kind/state/mismatch
categories and counts may be exposed.

### 8.5 Exact activation sequence

For each reader session, Task 6 must execute exactly this order:

1. build the coordinator and every adapter inactive;
2. verify semantic-handle/slot, allocation, deck/raster, frame, owner/input,
   collision-safe inventory/drain/restoration, retained fact-only timer/lifecycle
   compatibility, resource-release, and release-sink prerequisites;
3. freeze the complete legacy control plane under one token;
4. obtain the first exhaustive inventory after every source fence, capture the complete
   pre-drain restoration checkpoint, and continue versioned inventory maintenance;
5. select at most one directly proven visible predecessor and begin destructive drain
   of every other complete physical identity through identity-exact confirmations until
   the fixed point;
6. carry the selected row's exact owner, truthful resource kind, binding, complete
   physical identity, and provenance into the adopted predecessor seed; allocate a
   collision-free imported key and owner-independent retirement order; and compute the
   safety-narrowed physical initial lease;
7. call `installActivatedSession` once with every route/port and the complete immutable
   initial owner/input decision; successful return is the first observable
   `Activated` state and command egress is already open.

No seed/key cycle exists: inventory supplies the complete composite physical identity;
the handoff mints the seed ID and preserves the row's exact owner/kind/binding/
provenance; ledger import allocates a new collision-free coordinator-global key plus
retirement order; and only then is the immutable installation payload formed. The
source-local opaque token is never reused as that key. Failure before step 5 reaches `Legacy` only after accepted complete
unfreeze; failed unfreeze enters frozen `ActivationBlocked`. Failure from step 5 through atomic install enters `RestoringLegacy`; only a complete
restoration commit may return to `Legacy`, otherwise the session enters
`ActivationBlocked`. A failure after successful installation remains coordinator-owned
and fails closed. Publication close calls `closeToReleaseOnly` and enters `ReleaseOnly`
directly.

### 8.6 Operation liveness matrix

The durations below are immutable production command contracts. During Task 6, each
activated attempt retains exactly one existing command-scoped physical timer. For an
operation requiring retained-owner narrowing, no timer is bound while that synchronous
publication is pending; exact `Applied(Retained)` returns to pre-work, then the timer
binds to exact `ReaderTransitionId` before any timer-requiring physical work starts. Its only
output is the exact typed `DeadlineExpired`/scheduler-failure fact enqueued through the
non-reentrant FIFO. It cannot mutate presentation, input, or release; perform local
`Retry`; or extend/rearm outside the current command contract. A listed no-progress
interval permits rearm only for matching progress and never changes hard expiry.
Matching progress may rearm only as specified below. The timer remains owned through
`Committing`; only terminal applied/rejected acknowledgement, supersession, or close
cancels the exact registration once. A matching `PreparedFrame` alone cannot cancel it.
The production `ReaderTransitionClock` scheduling port remains inactive until Task 7.
Task 7 atomically imports each live `snapshotForTask7Transfer` transition, next expiry,
and remaining hard/no-progress bounds,
publishes the equivalent coordinator-clock registration, and retires/deletes the
retained physical scheduler as one serialized ownership change. A missing/invalid
transfer snapshot or coordinator-scheduler rejection leaves the retained timer as sole
owner and enqueues the exact typed failure fact; it is not a zero-owner fallback. There
is no zero-owner or two-owner interval during activation, supersession, close, or
transfer. Tests inject
deterministic Task 6 timers and, separately, the Task 7 coordinator clock. Retry always
creates a new transition identity; no expiry starts an unbounded automatic retry loop.

```kotlin
@JvmInline
value class ReaderTask6FactOnlyTimerRegistrationId internal constructor(
    val value: Long
) {
    init {
        require(value > 0L)
    }
}

data class ReaderTask6FactOnlyTimerRegistration(
    val id: ReaderTask6FactOnlyTimerRegistrationId,
    val transitionId: ReaderTransitionId,
    val physicalIdentity: ReaderLegacyPhysicalIdentity,
    val hardExpiresAtMillis: Long,
    val noProgressIntervalMillis: Long?,
    val permitsMatchingProgressRearm: Boolean
) {
    init {
        require(physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration)
        require(hardExpiresAtMillis >= 0L)
        require(noProgressIntervalMillis == null || noProgressIntervalMillis > 0L)
        require(permitsMatchingProgressRearm == (noProgressIntervalMillis != null))
    }
}

data class ReaderTask6FactOnlyTimerTransferSnapshot(
    val registration: ReaderTask6FactOnlyTimerRegistration,
    val nextExpiresAtMillis: Long
) {
    init {
        require(nextExpiresAtMillis >= 0L)
        require(nextExpiresAtMillis <= registration.hardExpiresAtMillis)
    }
}

interface ReaderTask6FactOnlyTimerPort {
    fun bindBeforeWork(
        transitionId: ReaderTransitionId,
        onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
    ): ReaderTask6FactOnlyTimerRegistration?

    fun matchingProgress(
        registration: ReaderTask6FactOnlyTimerRegistration,
        nowMillis: Long
    ): ReaderPortCommandResult

    fun snapshotForTask7Transfer(
        registration: ReaderTask6FactOnlyTimerRegistration
    ): ReaderTask6FactOnlyTimerTransferSnapshot?

    fun cancel(registration: ReaderTask6FactOnlyTimerRegistration): ReaderPortCommandResult
}
```

A null/rejected bind is translated by the coordinator into the exact typed immediate
failure fact before physical work; it is not permission to run without a timer.
`snapshotForTask7Transfer` returns the immutable registration plus its current exact next
expiry bounded by hard expiry; it neither transfers ownership nor arms the coordinator
clock by itself. For initial activation, deadline-requiring physical work cannot start
until one bound registration ID is accepted as owner. For supersession and close, main-
thread code binds the successor registration with its callback admission-gated, then
atomically replaces the accepted registration ID and fences the predecessor before
physically cancelling it. Before that commit only the predecessor callback is
admissible; afterward only the successor callback is admissible. Bind failure leaves the
predecessor sole owner and rejects/terminates successor work. This is the no-zero/no-two
ownership protocol; stale physical callbacks have no consequence authority.

| Operation | Truthful retained owner / input | Required proof | Deadline | Timeout outcome | Named wake or recovery |
|---|---|---|---|---|---|
| Bootstrap native page | shell cover or neutral shield / chrome only | retained-owner publication ack → allocation + raster + admitted Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(MaterialTimeout, retryable)` | `Wake.Retry` |
| Shell-cover commit | prior native/live frame / no new page pointer | retained-owner publication ack → coordinator FrameHandoff + shell target preparation → sized post-draw/prepared frame → successor publication ack | 2 s hard | `Failed(CoverCommitTimeout, retryable)` | `Wake.HostAvailable` or `Wake.Retry` |
| Cover-to-page entry | shell cover / duplicate entry coalesces | retained-owner publication ack → allocation + raster + admitted Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(PageEntryTimeout, retryable)` | `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Curl claim and settlement | claimed curl stream / matching pointer only | retained-owner publication ack → one-shot settlement + destination allocation/raster/Deck → curl target preparation → prepared terminal frame → successor publication ack | 5 s hard | `Failed(SettlementTimeout, retryable)` with stable terminal frame | `Wake.FoliateDestinationCommitted` or `Wake.Retry` |
| Native-to-live handoff | native frame / no new page pointer | retained-owner publication ack → coordinator FrameHandoff + live target preparation → matching WebView exposure/prepared frame → successor publication ack | 2 s hard | `Failed(LiveExposureTimeout, retryable)` | `Wake.WebViewAvailable` or `Wake.Retry` |
| Live-to-native handback | live frame / no new page pointer | retained-owner publication ack → admitted Deck + native target preparation → prepared frame → successor publication ack | 2 s hard | `Failed(NativeHandbackTimeout, retryable)` | `Wake.RendererCapacityAvailable` or `Wake.Retry` |
| External semantic relocation | pre-relocation frame retained only as a noninteractive transition shield / chrome only | retained-owner publication ack → destination semantic/allocation/raster/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(ExternalRelocationTimeout, retryable)` with visible diagnostic over the retained shield | `Wake.FoliateDestinationCommitted`, `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Reflow/profile replacement | last valid cover/native/live frame / chrome only | retained-owner publication ack → fresh-profile allocation/raster/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(ReflowTimeout, retryable)` | `Wake.PaginationProfileReady`, `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Visibility loss | latest valid frame identity retained logically / no physical input | persisted deferral accepted | immediate terminal deferral | `Deferred(VisibilityRestore)` | `Wake.VisibilityRestored` or `Wake.Restored` |
| Visibility restore | retained owner if still provable, otherwise shield / chrome only | retained-owner publication ack → fresh host/semantic/profile/allocation/raster/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(RestoreTimeout, retryable)` | `Wake.HostAvailable`, `Wake.WebViewAvailable`, `Wake.PaginationProfileReady`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Renderer recovery | last truthful non-renderer owner or shield / chrome only | retained-owner publication ack → fresh renderer/allocation/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(RendererRecoveryTimeout, retryable)` | `Wake.RendererCapacityAvailable` or `Wake.Retry` |
| Publication close | no visual owner / navigation only | cancellation acknowledgements and empty release ledger | 2 s drain budget | `Failed(CloseDrainTimeout, nonretryable)` with the reader already visually closed | none; late facts can only enter the closed release ledger |

`AwaitingPrerequisites` uses the listed no-progress/hard bounds. Retained publication
waiting precedes timer binding and all successor work; `Applied(Retained)` returns to
pre-work, and only then may the attempt timer bind before its first timer-requiring
command. Target preparation is an `AwaitingProof` phase whose sole proof/callback pair is
`FrameTargetPreparation`/prepared-or-rejected. `CommandIssued` must advance
synchronously to its matching `AwaitingProof` after acceptance or fail immediately.
Consuming `PreparedFrame` removes that proof. `Committing` retains predecessor truth,
successor resource, and timer while awaiting exactly
`OwnerAndInputPublicationAcknowledgement` from exactly Applied/Rejected callbacks, and
uses only the operation's remaining budget without extending hard expiry. Prepared-frame
receipt is progress, not settlement or success.
Publication close is exempt from frame proof and uses its dedicated 2 s release-drain
bound. Task 6 retained-timer binding failure and Task 7 coordinator-clock scheduling
failure each enqueue the exact immediate typed deadline failure fact; neither invokes a
local consequence or retry.

A deferred logical demand has no hidden running timer or command. It is bounded by
the resume-record rules in Section 11. If its named wake never arrives, no physical
attempt remains pending; the retained owner and visible Retry/close path remain
truthful.

## 9. Ports And Component Boundaries

The coordinator depends on narrow ports:

| Port | Responsibility |
|---|---|
| `ReaderSemanticCommandPort` | Resolve one opaque executable request handle, execute it through its own bounded one-shot slot, and return only that slot's command-bound receipt |
| `ReaderSemanticFactsPort` | Admit unsolicited Foliate facts without fabricating command identity or consuming a command slot |
| `ReaderMaterialGenerationAllocationPort` | Allocate and prove fresh exact preparation/raster/texture generations before physical work |
| `ReaderRasterPreparationPort` | Execute generation-scoped raster commands against an allocation and emit proof |
| `ReaderDeckPort` | Execute allocation-scoped renderer reservation/deck commands and ownership facts; it never releases independently |
| `ReaderFramePresentationPort` | Bind physical state to the coordinator-supplied Deck/FrameHandoff via `PrepareFrameTarget`, emit prepared/rejected target fact, then consume only that matching target for presentation; never allocate ownership/retirement, poll, or substitute by binding |
| `ReaderOwnerAndInputPublicationPort` | Sole synchronous no-callback/no-suspend transaction for successor or retained-owner/input publication; return an exact typed applied/rejected payload that the dispatcher queues as a fact; initial publication exists only inside `installActivatedSession` |
| `ReaderInputLeasePort` | Narrow/veto physical safety only inside the sole atomic publication transaction; never expose a separate activated input mutation |
| `ReaderLegacyFreezeAndInventoryPort` | Fence all legacy acquisition/dispatch, enumerate exact resources and subordinate owners under one token, drain with exact confirmation, and restore from the complete pre-drain checkpoint |
| `ReaderTransitionResourcePort` | Execute owner-independent ordered release registrations/commands for decks, callbacks, rasters, and handoff resources |
| `ReaderTask6LifecycleFactPort` | Keep the existing lifecycle normalizer as sole Task 6 ingress owner but emit inert ordered facts only; no direct presentation/input/material consequence |
| `ReaderTask6FactOnlyTimerPort` | Retain exactly one existing command-scoped physical timer per activated attempt; after any required `Applied(Retained)`, bind exact transition ID before first timer-requiring work and enqueue only exact expiry/failure fact; no presentation/input/release mutation, local Retry, or out-of-contract rearm |
| `ReaderTransitionClock` | Remain production-inactive during Task 6; Task 7 atomically receives all scheduling ownership, preserves live registration bounds with no zero/two-owner interval, and deletes retained physical schedulers |
| `ReaderTransitionWakeStore` | Persist and atomically consume opaque resumable demand; activated only in Task 7 |

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
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivation.android.kt`
  — per-session activation state, opaque freeze and collision-safe physical identities,
  exhaustive inventory, adopted predecessor construction, prerequisite validation,
  atomic port installation, and permanent release-only routing.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStore.android.kt`
  — SavedState-backed opaque wake demand only.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivationTest.kt`
  — activation state, freeze/inventory/adoption/drain, and close-phase contracts.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionAtomicCutoverTest.kt`
  — complete-port installation, semantic/material/frame/input transaction, and
  post-install no-fallback integration.

Refactor as adapters:

- `ReaderPresentationAuthority.kt` retains pure visual policy and proof validation.
- `ReaderPresentationController.kt` and `ReaderPresentationReceipt.kt` retain common
  state mutation; the semantic command path supplies explicit origin identity and
  only the coordinator consumes activated Android receipts.
- `KomikkuReaderNativeFrameHost.android.kt` becomes the sole production activation
  composition root, exhaustive legacy-resource reporter, immutable decision renderer,
  and sole combined owner/input publication host. It constructs a capability-authenticated
  package from real adapters, performs exhaustive installation, proves no installed
  `Shadow`/`LegacyOnly` or legacy consequence route remains reachable, and cannot use a
  no-op/rejecting fixture package; it stops maintaining an authority replica.
- `ReaderPresentationHostBridge.android.kt` and
  `ReaderNativePagePresentationPublisher` bind coordinator-selected Deck or ledger-
  allocated FrameHandoff registrations to immutable physical targets, emit target facts,
  then execute only matching one-shot presentation commands and emit prepared-frame/
  draw/handoff proof; they do not allocate ownership/retirement, poll, initiate
  transitions, substitute same-binding candidates, fabricate/replace registrations,
  commit owners, or own deadlines.
- `ReaderPageRasterPreparationController.android.kt` exposes fresh monotonic material
  allocation, generation-scoped facts, frozen inventory, and coordinator commands;
  it no longer invents generations or publishes presentation consequences.
- `ReaderPageTurnBundleSource.android.kt` contributes collision-safe composite identities
  for its snapshot
  cache, descriptor and pending-callback owners, hydration requests/recipients/workers,
  publication ledger entries/callbacks/values/capacity listener/completion entries/
  workers, raster persistence jobs,
  generation scheduler work, bitmap-source presented/live capture ownership and retained
  candidates, Handler runnables, presented-frame request IDs, PixelCopy writes, and
  evaluate-JavaScript/visual-state/draw callbacks, live-validation capture/worker/
  final-fence handles, persistent-store operations, decoded-cache/encode-pin/pending-
  release owners, and teardown work. `ReaderPageTurnBitmapSource`,
  `ReaderPageTurnPresentedCaptureOwnership`, `ReaderPageTurnLiveCaptureOwnership`,
  `ReaderPageRasterHydrationScheduler`,
  `ReaderPageRasterPublicationScheduler`, `ReaderPageRasterPublicationLedger`,
  `ReaderPageRasterScheduler`, `ReaderPagePendingCallbackOwners`, and
  `ReaderPageTurnBundleTeardown` expose freeze/snapshot/drain/restoration adapters;
  aggregate counts alone are not inventory.
- `ReaderForegroundWebViewOwnership.android.kt` contributes its passive lease,
  `cancelAndRestore` callback, restoration lease/callback, live and exclusive claims,
  pending readiness callbacks, and current mutation claim; freeze prevents new claims
  or mutations and restoration must settle before legacy can reopen.
- `ReaderPlayLikeCurlFoliateController.android.kt` retains raster leases, curl,
  renderer callbacks, and Foliate execution; after activation it accepts semantic,
  allocated deck, and frame commands and emits command-bound exact facts only.
- `ReaderDeckAdmission.android.kt` retains renderer ownership, contributes complete
  freeze inventory, and accepts only coordinator-issued allocated bindings.
- `ReaderPageInputSettlementHostController.android.kt` consumes the lease installed by
  the combined owner/input barrier and may narrow or veto only for physical safety.
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
| `ReaderPresentationAuthority.kt:readerPresentationReduce` mutates authority, binding, diagnostics, input, transition, and cleanup effects | 1 shadow; 3 one atomic cutover; 5 delete legacy effect dispatch. All normalized facts → `CommitOwnerAndInputLease` / operation command | Operation-matrix proof and deadline → one typed terminal outcome | Coordinator ledger only / operation-specific fact |
| `ReaderTransitionGateway` is Shadow-only and every accepted route currently continues to legacy dispatch | 2 inactive observation; 3 `installActivatedSession` accepts only the capability-authenticated real-adapter package, switches semantic intent/receipt routing with all material/exact-frame/sole-owner-input/resource routes, publishes initial owner/lease, and makes no Shadow/LegacyOnly or no-op legacy consequence route reachable. Intent or unsolicited Foliate fact → coordinator mailbox | Installation succeeds only as one complete package with initial publication and open egress; a public Boolean/`complete()`/all-rejecting package cannot satisfy completeness; any post-install failure remains coordinator-owned | `closeToReleaseOnly` permanently rejects non-release routes / no legacy fallback |
| `ReaderPresentationControllerReducer.onPresentationEvent/onViewerAction` admits input, mutates cover state, emits Foliate commands, and currently derives receipts without an executable handle or isolated command slot | 1/2 shadow; 3 intent-only gateway plus `ReaderSemanticRequestHandle`, dedicated bounded slot, and explicit `ReaderPresentationEventOrigin`; 5 remove legacy presentation branches. Command slot → tagged receipt; unsolicited Foliate origin → untagged authoritative fact | Exact handle, slot, command tag, and destination/frame proof → one terminal outcome; wrong/duplicate/stale/untagged settlement cannot satisfy proof | Retire handle/slot on every terminal path; no resources / Foliate callback or unsolicited relocation |
| `KomikkuReaderNativeFrameHost.android.kt:ReaderPresentationBindingReporter.update`, `reserve`, `classifyReceipt`, and `commitReceipt` maintain an Android authority replica and deck currency | 1 fact source; 2 inactive inventory protocol; 3 exhaustive freeze reporter, capability-authenticated production composition, and sole synchronous combined publication barrier; 5 delete replica. Exact composite physical identity/resource kind/binding/visible owner → adopted seed; exact target `PreparedFrame` → `Committing` → `CommitOwnerAndInputLease` → queued typed applied/rejected acknowledgement | One directly proven predecessor with truthful owner-kind relation; exact target/registration proof; only matching applied acknowledgement may succeed; ambiguity or mismatch blocks install/commit | Every non-adopted complete identity drains exactly; rejected successor alone releases; adopted identity enters ledger with `AdoptedLegacy` provenance |
| `ReaderPresentationLifecycleDelivery.android.kt:ReaderPresentationReceiptDispatcher`, `ReaderPresentationHostEffectApplier`, and `KomikkuReaderNativeFrameHost.android.kt:ReaderPresentationEffectHandler` reorder receipts and directly apply Retry/release effects | 1/2 shadow queues; 3 semantic callbacks and synchronous publication results enqueue typed facts into the existing coordinator FIFO while `advancing`; release routes enter the permanent sink; 5 delete. Port receipt/result/failure → fact; coordinator journal → combined commit or release command | Command-bound receipt and exact-target prepared proof reach `Committing`; only exact queued applied acknowledgement reaches success; callback/result-before-return remains non-reentrant | Journal terminal publication precedes timer cancellation/release; ledger confirms exact release / exact callback only |
| `ReaderPresentationLifecycleDelivery.android.kt:ReaderPresentationLifecycleDelivery.observe`/`retry` and `KomikkuReaderNativeFrameHost.android.kt:retryPresentationLifecycleDelivery` own pending lifecycle ordering | 3 install a compatibility fact adapter: the existing normalizer remains the sole ingress owner, but every overlapping legacy presentation/input/material consequence is suppressed and its ordered output can only enqueue an inert safety fact; 4 coordinator becomes sole lifecycle ingress and policy owner; 5 delete. | Task 6 fact may cancel/fence unsafe physical work but cannot create restore/reflow/recovery or wake policy; Task 7 lifecycle matrix owns those outcomes | Cancel registrations once / Task 7 visibility, host, renderer, `Wake.Restored` |
| `ReaderPresentationHostBridge.android.kt:ReaderPresentationHostBridge.update`, `beginCoverCommit`, and `updateLiveEngineExposure` autonomously start transitions and local timeouts | 3 two-step target/presentation adapter; 5 delete starters/state. Coordinator selects Deck or ledger-allocates FrameHandoff → `PrepareFrameTarget` → FIFO target fact → matching `RequestFramePresentation` → target-echoing proof | Awaiting-target proof precedes presentation; adapter cannot allocate ownership/retirement; prepared frame reaches successor `Committing` retaining predecessor truth; exact queued `Applied(Successor)` alone succeeds | Reject/supersede/close retire handle and ledger releases exact registration once / exact callbacks |
| `ReaderPresentationHostBridge.android.kt:ReaderNativePagePresentationPublisher.update`/`onPresentedFrame` polls candidates, owns deduplication and handback timeout, and publishes native presentation | 3 coordinator-supplied admitted-Deck target preparation plus command-only publisher; 5 delete autonomous `update` | No current-candidate polling, same-binding selection, token/resource fabrication, or registration replacement; target preparation and `PreparedFrame` are not success; successor `Committing` retaining predecessor truth awaits exact queued Applied/Rejected | Frozen callback/frame tokens inventory and ledger release / exact callbacks |
| `ReaderPresentationTransitionTimeout.android.kt` and `ReaderPageRelocationDispatchTimeout.android.kt` independently arm deadlines | 3 retain one existing timer source; after any required `Applied(Retained)`, bind exact transition before first timer-requiring command and route only expiry/failure facts while coordinator clock stays inactive; 4 atomically transfer scheduling; 5 delete hidden owner | Unchanged command bounds; exactly one owner/registration/fact and no zero/two-owner interval → typed failure/deferral, never local Retry | Cancel exact registration on terminal path / named fresh fact or Retry |
| `KomikkuReaderNativeFrameHost.android.kt:applyPresentationFrameOwner`, `dispatchPresentationEvent`, `reportPresentationIdentityIfAvailable`, and `reportPresentationPreparationFacts` apply layers and publish presentation consequences | 1/2 observed facts; 3 production composition root and sole publication host; 4 lifecycle/reflow facts; 5 delete direct authority. Operation acceptance → retained publication; exact `Applied(Retained)` → pre-work; target preparation/presentation → successor `Committing` retaining predecessor truth; synchronous result → FIFO acknowledgement | Neither commit command nor `PreparedFrame` succeeds; only exact queued `Applied(Successor)` is the successor success gate | Execute only ledger release commands / frame or lifecycle fact |
| `ReaderPageInputSettlementHostController.android.kt:updateInputPolicies`, `dispatchPointer`, and `onLifecycleEvent` locally admit input and cancel gesture work | 3 sole publication-port consumer; 4 cancellation facts; 5 remove local grant policy. Accept narrowing operation → retained-owner publication only → exact `Applied(Retained)` returns to pre-work; final successor uses same protocol | Zero semantic/allocation/raster/deck/target/frame/timer work before retained Applied; rejected retained terminates; no separate `ApplyInputLease`; local safety only narrows/vetoes | Ordered cancellation only / exact semantic or physical terminal fact |
| `ReaderPageRasterPreparationController.android.kt:publishPreparationState`, `prewarmAdjacent`, `finishPrewarm`, `deferPrewarm`, and `retryPreparation` own generation, deferral, Retry, and readiness consequences | 1 facts; 2 inactive raster port; 3 material-allocation command plus allocation-only raster commands and exhaustive freeze inventory; 4 typed lifecycle deferrals; 5 delete transition consequences. Semantic proof → `AllocateMaterialBinding` → `MaterialBindingAllocated` → raster command | Exact fresh monotonic generations and publication/profile/session validation before physical work; prepared frame still required | Frozen and stale raster attempts enter ledger / Task 7 owns Retry wake policy |
| `ReaderPageTurnBundleSource.android.kt`, `ReaderPageTurnBitmapSource.android.kt`, and their hydration/publication/generation schedulers, publication ledger, pending-callback owner, cache/store, live-validation ownership, and teardown task retain subordinate physical work and bitmap/callback owners not represented by preparation state | 3 add synchronous `freezeForTransitionActivation`, repeated exact `snapshotFrozenOwnership`, composite-identity `drainFrozenOwnership`, and `restoreFromActivationCheckpoint`; no aggregate-count substitution. Every snapshot-cache bitmap, descriptor request/recipient, hydration job/recipient, pending callback lease, publication entry/callback/value/job, persistence-init job, generation work item, bitmap-source presented/live capture ownership, retained candidate, Handler runnable, presented-frame request, PixelCopy write, evaluate-JavaScript/visual-state/draw callback, live-validation capture/worker/final fence, store operation, decoded cache/encode pin/pending release, and teardown owner receives a `(domain, source, source-local opaque token)` identity and registration | Each source fence acknowledges before fixed point; drain/restoration confirmations match complete identity and are bounded; late discoveries join the frozen epoch; equal local IDs from different sources never coalesce | Owner-independent retirement order and release sink / restored source confirmation or `ActivationBlocked` |
| `ReaderForegroundWebViewOwnership.android.kt` owns passive leases, `cancelAndRestore`, restoration callbacks, live/exclusive claims, readiness callbacks, and current mutation claims | 3 freeze acquisition/mutation synchronously, snapshot each owner/callback with source-qualified composite identities, settle or drain them, and restore from checkpoint before reopening legacy; activated path accepts only coordinator command/fact use | No live/passive mutation crosses the barrier; a pending restoration callback must terminate before fixed point or restoration commit | Exact identity registration/release; no callback-owned publication / restoration confirmation or `ActivationBlocked` |
| `ReaderPageRasterDeferredRetryCoordinator.android.kt` retains deferred closures and resumes from host events | 2/4 replace; 5 delete. Raster-deferral reason → persist demand or cancel | Section 11 one-consumption/15-minute policy → fresh transition or visible Retry | Clear on all terminal/replacement paths / exact reason wake only |
| `ReaderDeckAdmission.android.kt:ReaderDeckAdmission` and `ReaderDeckAdmissionLeaseHost` own reservation, callback ordering, promotion, and release | 2 inactive exact-key protocol; 3 exhaustive composite-identity inventory plus allocated coordinator lease; 5 remove host currency. `MaterialBindingAllocated` → `ReserveDeck`; callbacks → exact owned/prepared/rejected/released facts | Exact allocation and ownership proof; any incomplete inventory/drain blocks atomic install | One collision-free imported key per complete physical identity; only release ledger commands / capacity fact |
| `ReaderPlayLikeCurlFoliateController.android.kt:onRendererDeckPrepared`, `completeObservedDeckAdmission`, `retryAwaitingDeckAdmission`, `synchronizePresentationDecision`, `onPreparationStateChanged`, `onRasterProofReady`, and release methods own deck readiness, recovery, settlement, and direct release | 2 inactive deck/curl facts; 3 semantic/allocation/deck plus coordinator-registration target-preparation/presentation adapter; 5 remove local writers. Only after `Applied(Retained)`: commands → work; exact callbacks → FIFO facts | Target preparation and `PreparedFrame` lead to successor `Committing` retaining predecessor truth; neither is success; exact queued `Applied(Successor)` alone succeeds | Frozen/live resources release only through ledger / Task 7 owns recovery wake policy |
| `ReaderPageDeckRecoveryCoordinator.android.kt` independently owns repaired-deck build, capacity, prepared state, and generation release | 2 fold into deck transition; 5 delete state machine. Repair/deck/capacity facts → reserve/build or release | Repaired-window plus deck proof under renderer-recovery deadline → success, deferral, failure, or cancel | Ledger owns submitted and unsubmitted cleanup / repair, capacity, Retry |
| `ReaderProcessState.kt` and `ReaderProcessStateViewModel.kt` currently persist broader publication/UI state | 4 add a separate opaque transition wake store; 5 remove legacy transition persistence without deleting unrelated UI restoration. `Wake.Restored` → consume demand / request fresh facts | Fresh-fact restore deadline; never accept restored deck/callback proof → success, failure, or new bounded deferral | Clear demand per Section 11 / host, WebView, profile, renderer, Retry |
| `ReaderRoot.kt`, `ReaderScreen.kt`, and `ReaderPlatformHosts.kt` pass raw events/effects and invoke Retry directly | 3 immutable-decision renderer and intent gateway; 4 restored-wake gateway; 5 remove legacy plumbing. UI intents → coordinator; decision/outcome → Compose | No Compose deadline or proof → render coordinator terminal state only | No release ownership / explicit Retry |

The implementation plan must carry every row and exact replacement obligation in
its migration ledger, then add exact tests and deletion checks per row. Discovery of
another writer blocks the active slice until it is added here and assigned a single
replacement route.

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

## 12. Atomic Freeze, Inventory, Adoption, Drain, And Release Invariants

Task 6 replaces the former deck-only activation language. One token covers every
legacy consequence and resource source:

```kotlin
@JvmInline
value class ReaderLegacyFreezeToken internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

enum class ReaderLegacyInventorySource {
    Deck,
    RasterPreparation,
    RasterSnapshotCache,
    RasterDescriptorAndPendingCallback,
    RasterHydration,
    RasterPublication,
    RasterGenerationAndPersistence,
    RasterCaptureAndVisualState,
    RasterLiveValidation,
    RasterStoreAndCache,
    ForegroundWebViewOwnership,
    SemanticCommandSlot,
    LifecycleDelivery,
    DeadlineRegistration,
    FrameOrHandoff,
    Input
}

data class ReaderLegacyPhysicalDomain(
    val readerSessionGeneration: Long,
    val freezeToken: ReaderLegacyFreezeToken
) {
    init {
        require(readerSessionGeneration > 0L)
    }
}

@JvmInline
value class ReaderLegacySourceLocalOpaqueToken internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

data class ReaderLegacyPhysicalIdentity(
    val domain: ReaderLegacyPhysicalDomain,
    val source: ReaderLegacyInventorySource,
    val sourceLocalToken: ReaderLegacySourceLocalOpaqueToken
)

enum class ReaderLegacyResourceOrigin {
    Owned,
    Pending,
    Discovered
}

enum class ReaderLegacyResourceState {
    Reserved,
    Running,
    Registered,
    RendererOwned,
    Prepared,
    Visible,
    ReleaseRequested,
    Released
}

data class ReaderFrozenLegacyResource(
    val freezeToken: ReaderLegacyFreezeToken,
    val physicalIdentity: ReaderLegacyPhysicalIdentity,
    val kind: ReaderTransitionResourceKind,
    val binding: ReaderPresentationBinding?,
    val visibleOwner: ReaderPresentationFrameOwner?,
    val origin: ReaderLegacyResourceOrigin,
    val state: ReaderLegacyResourceState,
    val mayBeCommittedPredecessor: Boolean,
    val provenance: ReaderTransitionResourceProvenance =
        ReaderTransitionResourceProvenance.AdoptedLegacy
) {
    init {
        require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
        require(physicalIdentity.domain.freezeToken == freezeToken)
        require(!mayBeCommittedPredecessor || state == ReaderLegacyResourceState.Visible)
        require(!mayBeCommittedPredecessor || (binding != null && visibleOwner != null))
        require(!mayBeCommittedPredecessor ||
            kind == readerAdoptedResourceKindFor(requireNotNull(visibleOwner)))
    }
}

fun readerAdoptedResourceKindFor(
    owner: ReaderPresentationFrameOwner
): ReaderTransitionResourceKind? = when (owner) {
    ReaderPresentationFrameOwner.Neutral -> null
    is ReaderPresentationFrameOwner.NativePage,
    is ReaderPresentationFrameOwner.Curl -> ReaderTransitionResourceKind.Deck
    is ReaderPresentationFrameOwner.ShellCover,
    is ReaderPresentationFrameOwner.LiveEngine ->
        ReaderTransitionResourceKind.FrameHandoff
}

sealed interface ReaderLegacyResourceInventory {
    data object Incomplete : ReaderLegacyResourceInventory
    data class Complete(
        val freezeToken: ReaderLegacyFreezeToken,
        val snapshotSequence: Long,
        val discoveryVersion: Long,
        val completedSources: Set<ReaderLegacyInventorySource>,
        val resources: List<ReaderFrozenLegacyResource>
    ) : ReaderLegacyResourceInventory {
        init {
            require(snapshotSequence > 0L)
            require(discoveryVersion > 0L)
            require(completedSources == ReaderLegacyInventorySource.entries.toSet())
        }
    }
}
```

Composite identity equality is exact Kotlin data-class equality over positive reader-
session generation, the exact positive freeze token, one of the 16 source discriminators,
and a positive source-local opaque token. Equal local values under different sources,
freeze tokens, or session generations are distinct; only the same complete composite
identity may deduplicate. Inventory and late-discovery collections are bounded by each
source's existing ownership capacities and the 16-source fixed-point protocol; overflow
or an unrepresentable identity blocks activation rather than dropping a row. Raw session
generation, freeze token, source-local token, imported resource key, and retirement
order are memory-only and prohibited from logs, diagnostics, analytics, screenshots,
crash metadata, and persistence. Privacy-safe diagnostics may expose only bounded
source/state enums, counts, and mismatch categories.

`ReaderLegacyFreezeAndInventoryPort.freeze()` runs synchronously on Android main. It
fences, before returning, all deck reservation; raster, prewarm, repair, capture,
hydration, publication, generation, and persistence starts; renderer, draw, WebView,
Foliate, visual-state, and pending-owner callback registration; foreground passive/
live claims and mutation; new physical pointer admission; and legacy semantic
dispatch. `inventory(freezeToken)` must report all physical owners created or retained
by every completed source in the enum, not aggregate counts.

Source inspection makes these subordinate owners mandatory. `ReaderPageTurnBundleSource`
must expose complete composite identities for `snapshotCache`; `pendingDescriptorOwners`; descriptor
requests and recipients; in-flight hydration recipients/jobs and the hydration
scheduler's queued/active jobs; publication-ledger entries, callbacks, retained values,
capacity listener, publication-completion entries, and the publication scheduler's
jobs; raster-persistence initialization jobs;
`ReaderPageRasterScheduler` queued/pending/active work; bitmap-source presented/live
capture ownership and retained candidates; pending Handler runnables and presented-
frame requests; in-flight PixelCopy writes; evaluate-JavaScript, visual-state, and draw
callbacks; active live-validation capture, worker, and final-fence handles; persistent-
store active operations; decoded-cache owners, encode pins, and pending decoded releases; and an already-started teardown.
`ReaderForegroundWebViewOwnership` must expose its passive lease,
`cancelAndRestore` closure, restoration lease/callback, live and exclusive claims,
readiness callbacks, retired-claim terminal delivery, and current mutation claim. The legacy lifecycle-delivery queue/
registration and each retained existing command-scoped deadline registration are
separate frozen sources. Atomic installation selects the timer source; for each narrowing
attempt, exact `Applied(Retained)` returns to pre-work before its transition ID is bound,
and binding precedes the first timer-requiring command. The production
coordinator clock remains inactive. The existing `ownershipMetrics()` and `snapshot()`
counts are validation aids only and cannot satisfy inventory.

Each participating owner implements the same four exact operations:

- `freezeForTransitionActivation(freezeToken)` synchronously closes acquisition and
  callback registration and returns its source-fence acknowledgement;
- `snapshotFrozenOwnership(freezeToken)` returns rows carrying complete composite physical
  identities plus a monotonically increasing discovery version;
- `drainFrozenOwnership(freezeToken, physicalIdentity, onConfirmed)` rejects new use and
  returns asynchronous release/cancellation confirmation carrying that same complete
  identity;
- `restoreFromActivationCheckpoint(checkpoint, source, onConfirmed)` rebuilds its legacy
  admission state from that source's opaque pre-drain restart handle and confirms
  restored or failed.

A callback or ownership discovery after freeze joins that exact frozen inventory with its
complete composite identity and must drain; it cannot enter coordinator admission. Each
inventory source must report completion after its fence, and `ReadyToCommit` requires
two complete snapshots with successive `snapshotSequence` values and identical
`discoveryVersion`, source set, and resources after all drain callbacks have settled.
Duplicates of the same complete composite identity converge to one inventory identity
and compatible state. Equal local opaque IDs under different sources or domains remain
distinct rows, drain requests, confirmations, imports, and release-once registrations.
An incomplete source, invalid resource, wrong-domain/source/local confirmation, ambiguous
predecessor, failed release command, or missing exact release confirmation prevents
activation.

At most one inventory row may become the adopted predecessor. It must be the one frame
directly reported as physically visible at the handoff, and its exact complete identity,
resource kind, binding, owner, and provenance must be preserved. Zero is valid only when
the truthful initial owner is neutral; neutral has no adopted physical resource. Native-
page and curl/native-material predecessors use `Deck`; shell-cover and live/WebView
predecessors use `FrameHandoff`. Any mismatched owner-kind pair blocks installation. A
shell cover is a real predecessor and must retain its directly reported identity, kind,
and binding. Zero does not permit a journal guess. The coordinator must never infer
visibility or resource kind from binding alone, active journal state, binding similarity,
gesture state, renderer generations, or acknowledgements.

At `ReadyToCommit`, mint exactly one `ReaderAdoptedPredecessorSeedId` for the handoff
epoch and construct:

```kotlin
data class ReaderAdoptedPredecessorSeed(
    val id: ReaderAdoptedPredecessorSeedId,
    val physicalIdentity: ReaderLegacyPhysicalIdentity?,
    val resourceKind: ReaderTransitionResourceKind?,
    val binding: ReaderPresentationBinding?,
    val owner: ReaderPresentationFrameOwner,
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val provenance: ReaderTransitionResourceProvenance =
        ReaderTransitionResourceProvenance.AdoptedLegacy
) {
    init {
        require((owner == ReaderPresentationFrameOwner.Neutral) ==
            (physicalIdentity == null && resourceKind == null && binding == null))
        require(owner == ReaderPresentationFrameOwner.Neutral ||
            (physicalIdentity != null && resourceKind != null && binding != null))
        require(resourceKind == readerAdoptedResourceKindFor(owner))
        require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
        require(physicalIdentity == null ||
            physicalIdentity.domain.readerSessionGeneration == readerSessionGeneration)
    }
}
```

When a directly proven predecessor exists, the seed uses its selected row's exact
complete identity, resource kind, rendered binding, visible owner type, reader-session
generation, and coordinator epoch. After the seed is minted, and not before, the ledger
allocates a collision-free coordinator-global resource key and its independent
`ReaderResourceRetirementOrder`; the source-local token never becomes key `opaqueId`.
For a truthful neutral start, seed identity/kind/binding and adopted registration are all
absent. A truthful shell cover keeps its directly reported shell identity,
`FrameHandoff` kind, and binding rather than inventing a native resource; native-page or
curl material keeps `Deck`, and live/WebView handoff keeps `FrameHandoff`. Minting at
handoff is truthful because the identity describes the coordinator's adoption event,
not the resource's historical creation. It preserves the reported composite identity,
kind, binding, owner, and `AdoptedLegacy` provenance and therefore satisfies
`T5-EXACT-SEED` without assigning a fake transition operation to legacy work.

Before the first destructive drain, the freeze port must return a complete checkpoint:

```kotlin
@JvmInline
value class ReaderLegacyRestorationCheckpointId internal constructor(val value: Long)

data class ReaderLegacySourceRestartHandle(
    val source: ReaderLegacyInventorySource,
    val opaqueId: Long
)

data class ReaderLegacyRestorationCheckpoint(
    val id: ReaderLegacyRestorationCheckpointId,
    val freezeToken: ReaderLegacyFreezeToken,
    val routeGeneration: Long,
    val completedSources: Set<ReaderLegacyInventorySource>,
    val restartHandles: Map<ReaderLegacyInventorySource, ReaderLegacySourceRestartHandle>,
    val initialOwner: ReaderPresentationFrameOwner,
    val initialBinding: ReaderPresentationBinding?,
    val initialPhysicalIdentity: ReaderLegacyPhysicalIdentity?,
    val initialResourceKind: ReaderTransitionResourceKind?,
    val initialProvenance: ReaderTransitionResourceProvenance?,
    val requestedLease: ReaderTransitionInputLease,
    val physicalLease: ReaderTransitionInputLease
)
```

The checkpoint validates positive IDs/generation, exact all-source handle coverage,
handle/source equality, neutral iff binding/identity/kind/provenance are all absent,
non-neutral provenance exactly `AdoptedLegacy`, exact
`readerAdoptedResourceKindFor(initialOwner)`, matching session/freeze domain, and a
physical lease no broader than the requested lease. It stores the frozen route-table
generation, the directly proven initial owner/kind/binding/identity/physical lease, the
current sole lifecycle registration and exact retained fact-only timer registrations,
and one opaque in-memory restart handle from every `ReaderLegacyInventorySource`.
Handles may reference commands or factories needed to restart work, but never EPUB
locations or persisted content. Missing handles block drain.

The rollback protocol is exact:

1. A failure before the first `drainFrozenOwnership` call uses
   `cancelFreezeBeforeDrain`; only an accepted complete unfreeze atomically exposes the
   unchanged `Legacy` snapshot. Rejection enters `ActivationBlocked` with frozen routes
   rather than claiming legacy restoration.
2. Once any drain begins, every failure moves to `RestoringLegacy`; routes and command
   egress remain closed, new pointer input is denied, the proven predecessor remains
   visible when physically valid, and the release sink remains available.
3. `restoreFromActivationCheckpoint` runs for every source. Each source must either
   confirm the checkpoint ID and rebuilt admission state or report failure. The
   restoration fence has one 3-second hard deadline owned by the activation state
   machine and a finite source set; it cannot loop or wake from SavedState. Issuing all
   requests, or receiving only synchronous accepted returns, does not cancel this
   deadline: it remains active until every exact asynchronous source confirmation and
   the final atomic `commitRestoredLegacy` applied result have both been observed.
4. Only after all exact confirmations may one Android-main-thread, no-callback,
   no-suspend `commitRestoredLegacy` transaction restore the legacy route table,
   lifecycle/deadline registrations, visible owner, and physical lease together. Its
   typed applied result enters `Legacy` and then cancels the restoration deadline; a
   rejected result guarantees no restoration snapshot write.
5. Any failed/missing confirmation, deadline expiry, invalid predecessor, or atomic
   restoration-publication rejection enters `ActivationBlocked`. That state exposes a
   diagnostic plus Retry/close chrome, denies page input, never claims legacy is whole,
   and can only retry this in-memory restoration checkpoint or close to `ReleaseOnly`;
   it cannot activate the coordinator or invoke an active-session fallback.

Release remains exact, ordered, bounded, and permanent. The sink is created before
freeze and remains reachable through the activation coordinator during drain,
restoration, and blocked states; this does not activate any semantic/presentation
consequence. Atomic installation binds the activated gateway to the same sink, and
close narrows the session to it:

- one `ReaderTransitionReleaseLedger` records every coordinator-owned registration and
  every adopted `ReaderImportedLegacyResourceRegistration` as `Owned`,
  `ReleaseCommandIssued`, or `Released`; legacy observation, drain, import, release, and
  confirmation carry the same complete composite identity;
- equal source-local IDs from different sources/domains allocate distinct imports and
  each releases once; duplicate observations of one complete identity reuse one
  registration and cannot issue or confirm a second release;
- `ReaderResourceRetirementOrder` is allocated at first physical registration and is
  independent of resource owner and transition identity;
- only `ReleaseResource` may issue physical release, and only confirmation matching
  both key and retirement order may reach `Released`;
- the retirement fence stores `contiguousReleasedThrough` plus at most 32
  out-of-order released sequences per session/epoch. Active registrations take
  precedence; overflow rejects new ownership and keeps the sink fail-closed rather
  than evicting an unconfirmed fence;
- stale, replaced, failed, newly discovered frozen, restoring, blocked, and closed
  resources receive exactly one release command;
- publication close calls `closeToReleaseOnly()` before physical cancellation;
- `ReleaseOnly` is permanent and accepts only resource observation, release
  confirmation, and `ReleaseResource`; it rejects semantic, material, deck, frame,
  input, presentation, Retry, restoration, and transition consequences even after
  `CloseDrainTimeout`;
- timeout records the failure but never disables the sink or reopens legacy routes.

A committed cover/native/live frame remains retained until exact queued
`Applied(Successor)` acknowledges the combined owner/input publication, or terminal lifecycle invalidates
it. Reflow invalidates material identity and input leases, not Foliate semantic
authority or a valid committed cover. An old callback can report only its exact stale
resource identity; the coordinator leaves the current transition unchanged and
applies the release ledger.

## 13. Input Contract

Input is an epoch-scoped coordinator lease published by the same sole snapshot
transaction as the initial adopted owner, every later committed visual owner, and every
transitional retained-owner narrowing/revocation. `ApplyInputLease` cannot be an
activated mutation: a transitional change re-publishes the exact retained owner,
binding, resource, and physical lease atomically through the owner/input port. An
operation requiring this change first awaits exact `Applied(Retained)` and emits nothing
else; semantic/allocation/raster/deck/target/frame/timer work is impossible before it.
Applied returns the operation to pre-work without success; Rejected terminates it.

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
2. **Inactive preparation:** build and test the release ledger, complete cutover
   protocol, raster/deck adapters, semantic gateway, and receipt fences without
   opening a production coordinator consequence route. Task 1 through Task 5 remain
   preparatory regardless of passing tests.
3. **One atomic per-session cutover:** Task 6 freezes and exhaustively inventories all
   legacy semantic/material/deck/frame/input/resource sources and subordinate physical
   owners, captures a complete restoration checkpoint, drains all but at most one
   directly proven predecessor, constructs its adopted registration, and atomically
   installs semantic command, allocation/deck, coordinator-led target-preparation and
   exact presentation, release-sink, initial visible owner/physical lease, retained-input
   and two-phase successor publication, and retained fact-only timer routes from a
   capability-authenticated real-adapter package while production coordinator-clock
   scheduling remains inactive. Retained `Applied` gates all successor work; coordinator
   selects/allocates target registration before `PrepareFrameTarget`; matching target
   fact gates presentation; consuming `PreparedFrame` reaches `Committing` awaiting only
   publication acknowledgement; exact queued `Applied(Successor)` alone succeeds. Earlier wording
   that implied separate route installation, initial publication, egress opening, or
   deck activation followed by frame/input activation is superseded. Destructive-drain
   failure restores a complete legacy snapshot or enters `ActivationBlocked`; there is
   no direct rollback or active-session fallback.
4. **Lifecycle/reflow cutover:** Task 7 owns lifecycle normalization, visibility and
   recreation, reflow/profile replacement, renderer-loss recovery policy, all deadline
   scheduling and policy, and SavedState wake persistence/consumption. It atomically
   transfers every live retained Task 6 timer to the coordinator clock and deletes the
   retained physical schedulers without a zero- or two-owner interval.
5. **Deletion:** remove compatibility flags, duplicate queues, independent timeout
   owners, and callback-driven transition starters once no adapter can publish a
   semantic/material/deck/visual/input consequence directly.

### 14.1 Task 6 / Task 7 boundary

Task 6 owns only activation and the activated steady-state routes required for
executable semantic commands, command-bound receipts, material allocation, raster/deck
admission, coordinator-owned target registration selection/allocation,
`PrepareFrameTarget`/FIFO target proof/exact presentation, atomic initial publication,
retained-owner acknowledgement before successor work, two-phase successor owner/input
publication acknowledgement, adopted predecessor ownership, exact
resource release, and the permanent release-only sink. It must nevertheless avoid a
lifecycle gap or a duplicate deadline writer while those routes are active. Production
composition is incomplete until `KomikkuReaderNativeFrameHost` installs only the
capability-authenticated real-adapter package and no `Shadow`/`LegacyOnly`, legacy
consequence writer, no-op `complete()`, or rejecting activated package is reachable.

Task 7 retains lifecycle policy, visibility/recreation handling, reflow/profile
replacement, renderer-loss recovery policy, all deadline scheduling/policy,
SavedState wakes, and wake-driven resumption. Task 6 nevertheless retains exactly one
existing command-scoped fact-only physical timer per activated attempt until Task 7 can
perform the atomic ownership transfer. The following rows are mandatory Task 6 carry-
forward evidence:

- `T6-LIFECYCLE-FACT-ONLY`: at `installActivatedSession`, suppress every legacy
  lifecycle consequence that could mutate an activated semantic/material/deck/frame/
  owner/input route. The existing lifecycle normalizer remains the sole ordered
  lifecycle ingress until Task 7, but its compatibility adapter can only enqueue inert
  facts that cancel or fence unsafe Task 6 physical work. Task 6 cannot reinterpret
  those facts, create restore/reflow/recovery operations, persist demand, or choose a
  wake. Task 7 replaces this adapter and owns policy.
- `T6-NO-DEADLINE-TRANSFER`: Task 7 retains deadline ownership and policy. Task 6 keeps
  production coordinator-clock scheduling inactive and selects exactly one existing
  command-scoped physical timer for each activated attempt. For narrowing operations no
  timer is bound while retained publication is pending; exact `Applied(Retained)` returns
  to pre-work, then the timer binds the exact `ReaderTransitionId` and immutable Section
  8.6 bounds before the first timer-requiring command.
  Its only output is the matching typed `DeadlineExpired`/failure fact enqueued through
  FIFO; it cannot mutate presentation/input/release, invoke local `Retry`, or extend/
  rearm except for matching progress explicitly permitted by that command contract.
  Activation, supersession, settlement, rejection, and close serialize selection and
  cancellation so deadline-requiring work has neither zero nor two owners. Task 7 later
  atomically imports each live registration into the coordinator clock, fences and
  retires the retained registration in the same ownership commit, and deletes all
  retained physical schedulers. Pre-commit callbacks belong only to the retained timer;
  post-commit callbacks belong only to the coordinator. Transfer racing expiry,
  supersession, or close is classified once at the mailbox boundary, with no zero- or
  two-owner interval.
- `T6-NO-WAKE-TRANSFER`: Task 6 neither persists nor consumes resumable wake demand;
  Task 7 owns `ReaderTransitionWakeStore` and every SavedState wake route. The
  activation restoration checkpoint is in-memory safety state, not a wake record.

Each slice requires deterministic callback-before-ownership,
ownership-before-callback, supersession, timeout, restore, and release-once coverage.
Task 6 additionally requires grouped RED coverage for atomic installation including
neutral, shell-cover, native-page, curl/native-material, and live/WebView predecessor
owner-kind cases plus mismatched-kind rejection; complete-identity subordinate-owner
inventory, cross-source equal-local-ID drain/confirmation/import/release-once races;
adopted-seed and owner-independent retirement provenance; executable semantic handles
and isolated bounded slots; material allocation; retained publication acknowledgement
before any successor work/timer binding; coordinator-selected Deck or ledger-allocated
FrameHandoff followed by awaiting-target `PrepareFrameTarget` and FIFO target fact;
sealed kind-specific target validation with no pre-command frame sequence, adapter-owned
registration, lookup/polling, or fabrication; consumed `PreparedFrame` followed by
successor `Committing` retaining predecessor truth awaiting only exact synchronous publication acknowledgement and
terminal-before-release ordering; capability-authenticated
production composition with no Shadow/no-op route; one retained fact-only timer with
inactive coordinator clock and lifecycle compatibility; restoration deadline through
all asynchronous confirmations and final atomic result; restoration/blocked activation;
no fallback; and close in every activation phase. Task 7 RED coverage must prove atomic timer transfer and deletion under expiry,
supersession, and close races with no zero/two-owner interval. Passing tests do not
replace the bounded running-app gate.

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
   then pass deterministic common/Android host coverage for atomic installation with
   initial owner/input publication and truthful neutral/shell/native/curl/live owner-kind
   mapping, complete composite-identity subordinate-owner inventory and cross-source
   collision release races, adopted-seed and owner-independent retirement provenance,
   executable semantic handles and isolated bounded command slots, command-bound
   synchronous receipts, material allocation, retained acknowledgement before successor
   work, coordinator-selected/ledger-allocated registration, awaiting-target
   `PrepareFrameTarget` plus FIFO target fact, sealed kind-specific target validation,
   exact presentation proof, consumed `PreparedFrame`, successor `Committing` awaiting
   only publication acknowledgement, terminal-before-release ordering,
   capability-authenticated production composition with no Shadow/no-op route, exactly one retained Task 6 fact-only timer with inactive production
   coordinator clock, atomic Task 7 timer transfer, lifecycle fact-only compatibility,
   restoration/blocked activation, no fallback, close in every activation phase, the
   same transition ordering, all operation-liveness rows, one-shot semantic facts,
   persistence expiry, and release-ledger adoption/drain. Then run the final consolidated
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

- `installActivatedSession` atomically installs every route from a capability-authenticated
  package constructed by `KomikkuReaderNativeFrameHost` from real adapters, publishes the
  initial owner/physical lease, marks `Activated`, and exposes command egress, with no
  public post-install publication/open step and no reachable `Shadow`/`LegacyOnly`,
  legacy consequence writer, production no-op `complete()`, or rejecting fixture port;
- freeze/inventory/drain covers every named subordinate physical owner under one token
  with collision-safe `(session/freeze domain, source, source-local opaque token)`
  identity; equal local IDs from different sources remain distinct through drain,
  confirmation, import, and release-once handling;
- the adopted seed preserves the selected row's exact identity, truthful owner/resource
  kind, binding, and provenance without binding-only inference, a fabricated transition
  identity, or a seed/key cycle;
- destructive-drain failure restores one complete legacy snapshot or enters coherent
  `ActivationBlocked`, never direct partial `Legacy`;
- semantic requests carry executable opaque handles and each command owns one isolated,
  bounded, exhaustively retired callback slot;
- every physical raster/deck request follows an exact fresh material allocation;
- operations requiring input narrowing publish retained owner/input first; before exact
  `Applied(Retained)` no semantic/allocation/raster/deck/target/frame/timer work occurs,
  Applied returns to pre-work without success, and Rejected terminates;
- coordinator selects admitted Deck or ledger-allocates FrameHandoff, publishes an
  awaiting-target phase, and issues `PrepareFrameTarget`; only matching FIFO
  `FrameTargetPrepared` stores target and permits presentation, while adapters cannot
  allocate ownership/retirement;
- sealed shell/native/curl/live contracts validate exact legal token state, generation/
  geometry/request evidence, session/publication/binding, and registration owner/kind;
  no pre-command frame sequence, binding lookup, candidate polling, token/resource
  fabrication, replacement, or default retirement exists;
- consuming prepared-frame proof removes it and publishes `Committing` awaiting exactly
  `OwnerAndInputPublicationAcknowledgement` with exactly Applied/Rejected callback
  sources; only exact queued `Applied(Successor)` succeeds before timer cancellation and
  predecessor release, while Rejected retains predecessor and releases successor only;
- all raw frame-target/token/claim/publication identities, request/presented-frame
  sequences, and resource registrations remain in-memory and absent from logs,
  diagnostics, analytics, screenshots, crash metadata, equality diagnostics, and
  persistence;
- the restoration deadline survives request issuance and synchronous returns until all
  exact asynchronous source confirmations and final atomic restoration application;
- resource retirement order and bounded fences are independent of transition/resource
  owner identity;
- every Task 6 activated attempt has exactly one retained command-scoped fact-only timer;
  for narrowing operations it remains unbound until exact `Applied(Retained)` returns the
  operation to pre-work, then binds before the first timer-requiring command while
  production coordinator-clock scheduling is inactive; Task 7 atomically transfers all
  scheduling to that clock and deletes retained schedulers with no zero/two-owner
  interval; lifecycle compatibility remains fact-only with no overlapping consequence
  writer, and Task 7 retains lifecycle/reflow/recreation/deadline/wake policy;
- post-install failures never return to legacy and close retains the permanent
  release-only sink;
- all coordinator transition identities and outcomes are typed;
- no Android adapter directly publishes visual/input consequences;
- no accepted transition lacks a deadline and terminal route;
- release accounting is exactly once;
- the exact consecutive emulator gate passes;
- the bounded production-signed tablet gate passes afterward.
