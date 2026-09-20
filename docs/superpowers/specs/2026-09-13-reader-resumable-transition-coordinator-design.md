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

**Task389 initial-origin amendment:** activation adoption is not a transition operation.
The activated journal begins from a typed initial committed-presentation origin, never a
fabricated `ReaderTransitionId`. This amendment is a release blocker before Task 6 source
and port completion can reopen or production activation can occur. Task #385 is reopened
for real-owner/source/port wiring against this contract. Task #386 remains fail-closed
until this amendment is published and Task #385 closes; preparatory adapters are not
complete production wiring.

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

The installation input contains the complete validated initial publication origin from
which the barrier derives the exact common journal baseline. The origin is not an
operation identity. Its input lease is a separate common type that structurally cannot
carry `ReaderTransitionId`:

```kotlin
enum class ReaderTransitionResourceProvenance {
    CoordinatorIssued,
    AdoptedLegacy
}

sealed interface ReaderInitialPresentationInputLease {
    data object None : ReaderInitialPresentationInputLease
    data object ChromeOnly : ReaderInitialPresentationInputLease
    data object CoverActions : ReaderInitialPresentationInputLease

    data class NativePage(
        val binding: ReaderPresentationBinding,
        val textureGeneration: Long
    ) : ReaderInitialPresentationInputLease {
        init {
            require(textureGeneration >= 0L)
        }

        override fun toString(): String =
            "ReaderInitialPresentationInputLease.NativePage(<redacted>)"
    }
}

sealed interface ReaderInitialCommittedPresentationOrigin {
    val readerSessionGeneration: Long
    val coordinatorEpoch: Long
    val requestedLease: ReaderInitialPresentationInputLease
    val physicalLease: ReaderInitialPresentationInputLease

    data class AdoptedPredecessor(
        val seedId: ReaderAdoptedPredecessorSeedId,
        override val readerSessionGeneration: Long,
        override val coordinatorEpoch: Long,
        val owner: ReaderPresentationFrameOwner,
        val binding: ReaderPresentationBinding,
        val resource: ReaderTransitionResourceRegistration,
        override val requestedLease: ReaderInitialPresentationInputLease,
        override val physicalLease: ReaderInitialPresentationInputLease,
        val provenance: ReaderTransitionResourceProvenance =
            ReaderTransitionResourceProvenance.AdoptedLegacy
    ) : ReaderInitialCommittedPresentationOrigin {
        init {
            require(readerSessionGeneration > 0L && coordinatorEpoch > 0L)
            require(owner != ReaderPresentationFrameOwner.Neutral)
            require(owner.hasBinding(binding))
            require(resource.key.ownerId ==
                ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId))
            require(resource.key.kind == owner.resourceKind())
            require(resource.retirementOrder.readerSessionGeneration ==
                readerSessionGeneration)
            require(resource.retirementOrder.coordinatorEpoch == coordinatorEpoch)
            require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
            require(readerInitialLeaseIsNoBroaderThan(physicalLease, requestedLease))
            require(readerInitialLeaseIsCompatibleWithOwner(
                owner = owner,
                binding = binding,
                lease = requestedLease
            ))
            require(readerInitialLeaseIsCompatibleWithOwner(
                owner = owner,
                binding = binding,
                lease = physicalLease
            ))
        }

        override fun toString(): String =
            "ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(<redacted>)"
    }

    data class Neutral(
        override val readerSessionGeneration: Long,
        override val coordinatorEpoch: Long,
        override val requestedLease: ReaderInitialPresentationInputLease,
        override val physicalLease: ReaderInitialPresentationInputLease
    ) : ReaderInitialCommittedPresentationOrigin {
        init {
            require(readerSessionGeneration > 0L && coordinatorEpoch > 0L)
            require(requestedLease == ReaderInitialPresentationInputLease.None ||
                requestedLease == ReaderInitialPresentationInputLease.ChromeOnly)
            require(physicalLease == ReaderInitialPresentationInputLease.None ||
                physicalLease == ReaderInitialPresentationInputLease.ChromeOnly)
            require(readerInitialLeaseIsNoBroaderThan(physicalLease, requestedLease))
        }

        override fun toString(): String =
            "ReaderInitialCommittedPresentationOrigin.Neutral(<redacted>)"
    }
}

data class ReaderReservedNeutralBootstrapRequest(
    val handle: ReaderSemanticRequestHandle,
    val readerSessionGeneration: Long
) {
    init {
        require(readerSessionGeneration > 0L)
    }

    override fun toString(): String =
        "ReaderReservedNeutralBootstrapRequest(<redacted>)"
}

data class ReaderInitialActivationDecision(
    val origin: ReaderInitialCommittedPresentationOrigin,
    val adoptedSeed: ReaderAdoptedPredecessorSeed?,
    val adoptedResource: ReaderImportedLegacyResourceRegistration?,
    val neutralBootstrapReservation: ReaderReservedNeutralBootstrapRequest?
) {
    init {
        when (origin) {
            is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor ->
                require(neutralBootstrapReservation == null)
            is ReaderInitialCommittedPresentationOrigin.Neutral -> {
                val reservation = requireNotNull(neutralBootstrapReservation)
                require(reservation.readerSessionGeneration ==
                    origin.readerSessionGeneration)
            }
        }
    }

    override fun toString(): String = "ReaderInitialActivationDecision(<redacted>)"
}

data class ReaderActivatedSessionInstallation(
    val ports: ReaderProductionActivatedSessionPorts,
    val initialDecision: ReaderInitialActivationDecision
) {
    override fun toString(): String = "ReaderActivatedSessionInstallation(<redacted>)"
}

data class ReaderActivatedSessionSnapshot(
    val ports: ReaderProductionActivatedSessionPorts,
    val initialDecision: ReaderInitialActivationDecision,
    val reservedNeutralBootstrap: ReaderReservedNeutralBootstrapRequest?,
    val journal: ReaderTransitionJournal,
    val state: ReaderSessionActivationState = ReaderSessionActivationState.Activated,
    val commandEgressOpen: Boolean = true
) {
    init {
        require(state == ReaderSessionActivationState.Activated)
        require(commandEgressOpen)
        require(reservedNeutralBootstrap ===
            initialDecision.neutralBootstrapReservation)
        require(journal.committed ==
            ReaderCommittedPresentation.Initial(initialDecision.origin))
        require(journal.lastTransitionSequence == 0L)
        require(journal.lastIssuedTransitionIdentity == null)
        require(journal.releaseOnlyCleanup == null)
    }

    override fun toString(): String = "ReaderActivatedSessionSnapshot(<redacted>)"
}

fun installActivatedSession(
    installation: ReaderActivatedSessionInstallation
): ReaderActivationInstallResult
```

`ReaderTransitionResourceProvenance` moves from
`ReaderDeckAdmission.android.kt` to
`composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt`.
Android deck/adoption code imports that exact common enum; no Android declaration shadows
it. This makes the common origin source-set legal while preserving exact provenance.

For `AdoptedPredecessor`, `adoptedSeed` and `adoptedResource` are both present. The
origin's seed ID, exact session/epoch, owner, binding, registration, both identity-free
requested and physical input leases, and `AdoptedLegacy` provenance must equal the
seed/import decision. Breadth ordering is necessary but not sufficient: both leases must
independently be compatible with that exact owner/binding in the origin factory,
restoration checkpoint, activation decision, and installation barrier. The imported wrapper's complete physical identity equals the seed identity, its registration
owner is `AdoptedPredecessor(seed.id)`, and its kind equals `seed.resourceKind`. Native-
page seeds may use exact matching identity-free `NativePage`; its authoritative initial
texture generation is non-negative, so generation zero is lawful. This is an invariant
correction, not a migration claim. Shell-cover may use
`CoverActions`; live-engine uses at most `ChromeOnly`. Curl adoption fences/cancels every
legacy claimed gesture before inventory reaches its fixed point and uses only `None` or
`ChromeOnly`; no initial lease can encode a gesture or transition identity. For
`AdoptedPredecessor`, `neutralBootstrapReservation` is absent. For `Neutral`, both adopted
values are absent, there is no seed ID, binding, resource, or visible owner, both leases
are lawful identity-free no-owner `None`/`ChromeOnly` values, and one exact session-
matching neutral-bootstrap reservation is mandatory before `ReadyToCommit`. No binding-
only inference, universal
`FrameHandoff` default, or neutral stand-in resource exists. Normal
`ReaderTransitionInputLease.ClaimedGesture` becomes available only after an authentic real
operation exists.

For a neutral origin, activation constructs the private current-Foliate-destination closure
and synchronously calls `ReaderSemanticRequestRegistry.register` while routes and command
egress are still legacy/closed. Exact non-null reservation is a `ReadyToCommit`
prerequisite. A null return creates no transition and must not call
`installActivatedSession`, switch a route, publish owner/input, or open egress. The
activation protocol releases every activation-owned registration/import/reservation exactly
once and then performs the existing pre-drain unfreeze or post-drain restoration; failed
unfreeze/restoration enters `ActivationBlocked`. An adopted origin does not reserve a
bootstrap request.

`ReaderProductionActivatedSessionPorts` is not a public completeness claim. Production
installation accepts only a capability-authenticated package constructed inside
`KomikkuReaderNativeFrameHost` from the exhaustive real gateway, semantic, allocation,
raster, deck, exact-frame, sole owner/input publication, input-safety, resource, owned-work
cancellation, release-sink, lifecycle-fact, and fact-only-timer adapters. No public Boolean, generic
`complete()` factory, production-visible no-op, or all-rejecting package can mint that
capability. Test fixtures use an unmistakably test-only package type that is not accepted
by the production installation signature. Successful installation also proves that no
`Shadow`/`LegacyOnly` route and no legacy semantic/material/frame/input consequence
writer remains reachable from the installed composition root.

Before this call, the frozen input adapter computes both identity-free leases, verifies
`physicalLease` is no broader than `requestedLease`, and independently validates each
against the exact adopted owner/binding; neutral validates both as lawful no-owner
`None`/`ChromeOnly`. For neutral, the registry has also returned the exact reserved private
bootstrap handle. The barrier side-
effect-freely validates every route; the adopted or neutral origin; the selected row's
exact resource kind, binding, complete physical identity, and common provenance; the
optional seed/import; both leases and their owner/no-owner compatibility; activation state
`ReadyToCommit`; an empty pre-install release-sink cleanup slot; and the exact neutral
reservation/adopted absence. Inside the barrier, after all validation and before
the sole write, it derives
`ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(origin),
releaseOnlyCleanup = null, lastTransitionSequence = 0L,
lastIssuedTransitionIdentity = null)`; callers cannot supply
or mutate that journal. It then constructs one immutable
`ReaderActivatedSessionSnapshot` and replaces the composition root's single snapshot
reference in one no-callback, non-suspending Android-main-thread write. Gateway, optional
visible-owner/binding/resource projection, physical-input, deck-writer, reserved-neutral-
bootstrap capability, activation-state, journal, and command-egress readers all use that same reference; no route, journal
baseline, or publication has a separate install field. The write simultaneously:

- switches gateway semantic routing and the command-bound semantic dispatcher;
- switches material-generation allocation and raster/deck admission;
- switches frame request/proof and successor owner/input publication;
- switches fact-only lifecycle compatibility and selects the sole existing command-scoped
  fact-only physical-timer source after overlapping legacy consequence/timer writers are
  fenced; for narrowing operations that source binds exactly one registration only after
  `Applied(Retained)` and before first timer-requiring work, while the production
  coordinator clock remains inactive;
- publishes the origin's optional initial visible owner/binding/resource and exact
  identity-free physical input lease, with all three presentation fields absent for
  neutral;
- installs the barrier-derived journal with that origin as its committed baseline;
- transfers the exact pre-reserved neutral-bootstrap capability into the installed snapshot
  for neutral, or proves it absent for adopted;
- switches exact resource observation, the keyed owned-work cancellation port, and the
  permanent release-only sink;
- marks the session `Activated` and makes coordinator command egress visible.

There is no public `publishInitial`, `openCommandEgress`, or caller-supplied journal step.
Observers can see only the complete legacy snapshot or the complete
activated snapshot with its typed baseline and truthful optional presentation/lease; an
installed-but-unpublished state is unrepresentable. Installation rejection discards the
exact reserved neutral handle through the registry's one-shot retirement path before
restoration/unfreeze and releases other activation-owned state exactly once. Close before
bootstrap consumption performs that same exact discard; successful sequence-1 bootstrap
`take` consumes it and leaves only its bounded retirement tombstone, so later close cannot
release or execute it again. Failure before any destructive drain
may cancel the freeze and remain `Legacy`. Failure after destructive drain starts must
enter `RestoringLegacy`, and may return to `Legacy` only after the restoration protocol in
Section 12 commits a complete legacy snapshot. A restoration failure enters
`ActivationBlocked`. A failure after successful atomic installation remains coordinator-
owned and fails closed without active-session fallback.

## 5. Transition Identity

Every accepted operation has a `ReaderTransitionId` containing:

- reader-session generation;
- coordinator epoch;
- monotonically increasing transition sequence;
- operation kind;
- immutable expected presentation binding, including explicit
  `NoCommittedPresentation` for neutral close and `FoliateAuthoritativeInitial` while a
  neutral bootstrap awaits its first authentic receipt;
- optional parent transition identity for causal ancestry.

The activated journal also has one committed presentation that is deliberately distinct
from operation identity:

```kotlin
sealed interface ReaderCommittedPresentation {
    val readerSessionGeneration: Long
    val coordinatorEpoch: Long

    data class Initial(
        val origin: ReaderInitialCommittedPresentationOrigin
    ) : ReaderCommittedPresentation {
        override val readerSessionGeneration: Long = origin.readerSessionGeneration
        override val coordinatorEpoch: Long = origin.coordinatorEpoch
        override fun toString(): String =
            "ReaderCommittedPresentation.Initial(<redacted>)"
    }

    data class Transition(
        val committed: ReaderCommittedTransition
    ) : ReaderCommittedPresentation {
        override val readerSessionGeneration: Long = committed.id.readerSessionGeneration
        override val coordinatorEpoch: Long = committed.id.coordinatorEpoch
        override fun toString(): String =
            "ReaderCommittedPresentation.Transition(<redacted>)"
    }
}

data class ReaderCommittedTransition(
    val id: ReaderTransitionId,
    val owner: ReaderPresentationFrameOwner,
    val binding: ReaderPresentationBinding,
    val resource: ReaderTransitionResourceRegistration
) {
    override fun toString(): String = "ReaderCommittedTransition(<redacted>)"
}

data class ReaderTransitionJournal(
    val active: ReaderActiveTransition? = null,
    val releaseOnlyCleanup: ReaderReleaseOnlyCleanupState? = null,
    val lastOutcome: ReaderTransitionOutcome? = null,
    val committed: ReaderCommittedPresentation,
    val retryableTransition: ReaderRetryableTransition? = null,
    val lastTransitionSequence: Long = 0L,
    val lastIssuedTransitionIdentity: ReaderTransitionParentIdentity? = null,
    val lastPublicationSequence: Long = 0L
) {
    override fun toString(): String = "ReaderTransitionJournal(<redacted>)"
}
```

`Initial` is the journal baseline. It carries no `ReaderTransitionId`, operation, parent,
synthetic expected binding, prepared-frame proof, or transition-owned registration.
`Transition` is created only by exact `Applied(Successor)` for a real operation and keeps
the existing operation ID/binding/resource invariants. Before atomic install, the inactive
production coordinator container has no active journal; it must not construct a default
empty journal and reject the first fact. The installed snapshot supplies the first
complete journal and origin together. Initial installation requires
`releaseOnlyCleanup == null`. A non-null cleanup record requires `active == null`, no
retryable transition, and a cleanup key whose session generation and coordinator epoch match
`committed`; it is the sole bounded authority for cleanup outcomes after permanent
`ReleaseOnly`, not a hidden active phase.

The journal stores exact baseline session/epoch plus `lastTransitionSequence` and
`lastIssuedTransitionIdentity`. The first real operation in that baseline uses sequence
`1` and `parent = null`. Every later operation increments monotonically and, when a prior
real operation exists, uses exactly that operation's `parentIdentity()`; abort, timeout,
Retry, restore, supersession, and close never reuse a sequence. Presentation predecessor
and causal parent remain separate: an aborted first operation can leave `Initial` as the
truthful presentation while its Retry receives sequence `2` and the aborted operation as
parent. No reducer derives session, epoch, sequence, or parent from a fabricated baseline
operation.

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

    override fun toString(): String =
        "ReaderAdoptedPredecessorSeedId(<redacted>)"
}

sealed interface ReaderTransitionResourceOwnerId {
    data class TransitionOwned(
        val transitionId: ReaderTransitionId
    ) : ReaderTransitionResourceOwnerId {
        override fun toString(): String =
            "ReaderTransitionResourceOwnerId.TransitionOwned(<redacted>)"
    }

    data class AdoptedPredecessor(
        val seedId: ReaderAdoptedPredecessorSeedId
    ) : ReaderTransitionResourceOwnerId {
        override fun toString(): String =
            "ReaderTransitionResourceOwnerId.AdoptedPredecessor(<redacted>)"
    }
}

data class ReaderTransitionResourceKey(
    val ownerId: ReaderTransitionResourceOwnerId,
    val kind: ReaderTransitionResourceKind,
    val opaqueId: Long
) {
    init {
        require(opaqueId > 0L)
    }

    override fun toString(): String = "ReaderTransitionResourceKey(<redacted>)"
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

    override fun toString(): String = "ReaderResourceRetirementOrder(<redacted>)"
}

data class ReaderTransitionResourceRegistration(
    val key: ReaderTransitionResourceKey,
    val retirementOrder: ReaderResourceRetirementOrder
) {
    override fun toString(): String =
        "ReaderTransitionResourceRegistration(<redacted>)"
}

// Android-only import wrapper; it is not part of the common release identity.
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

    override fun toString(): String =
        "ReaderImportedLegacyResourceRegistration(<redacted>)"
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

This list is exhaustive. Initial activation, neutral-bootstrap request admission, frame-
target preparation, and retained/successor publication acknowledgement are protocols,
facts, proofs, or commands and never operation values.

A neutral baseline starts physical presentation through exactly the existing
`BootstrapNativePage` operation. After atomic install, the gateway reads the exact
pre-reserved handle from the installed snapshot and enqueues one
`ReaderBootstrapNativePageIntent`; it performs no nullable registry call and does not run
inside `installActivatedSession`. The operation is sequence `1`, has no parent, and uses
`ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(requestSequence = 1)`.
Its private executable request asks live Foliate for the current authoritative semantic
destination. Only the command-bound `FoliateDestinationCommitted` receipt replaces that
expectation with an exact binding; only then may existing material allocation, raster,
deck, target preparation, frame presentation, and two-phase publication proceed. Neutral
bootstrap cannot construct a binding from seed, viewport, renderer, or publication state.

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
- its exact wake or callback source;
- its finite admissible command-stage set.

A phase without all seven is invalid. `ReaderTransitionProofKind` includes
`FrameTargetPreparation` and `OwnerAndInputPublicationAcknowledgement`. An awaiting-
target phase awaits only the former, admits `FrameTargetPreparation`, and lists
`FrameTargetPrepared`, `FrameTargetPreparationRejected`, and `CommandRejected` callback
sources. Every successor `Committing` phase awaits only the latter, admits
`SuccessorPublication`, and lists only `OwnerAndInputPublicationApplied`,
`OwnerAndInputPublicationRejected`, and `CommandRejected` callback sources; consumed
`PreparedFrame` is no longer awaited. Retained-owner input publication uses the same
acknowledgement proof, admits `RetainedPublication`, and includes matching Applied,
Rejected, and `CommandRejected` callback sources, but matching `Applied(Retained)` returns
the active operation to pre-work rather than publishing success.

## 7. Single Advancement Loop

The coordinator owns a main-thread mailbox and a non-reentrant `advance()` drain.

1. Normalize ingress into a typed fact.
2. Allocate operation identity only from the journal's immutable baseline session/epoch,
   monotonic last-issued sequence, and prior authentic operation identity. Sequence `1`
   has no parent; neither initial-origin variant can be cast or copied into an operation.
3. Reject or stale the fact against the current authentic transition identity.
4. Update common presentation policy through the existing pure reducer.
5. Derive one immutable presentation decision from either the initial baseline or a
   committed real transition.
6. If accepted work narrows/revokes an adopted predecessor's input, publish retained-
   publication waiting state and issue only that transaction; exact `Applied(Retained)`
   returns to pre-work, while rejection terminates before successor work or timer binding.
   Neutral has no retained owner/binding/resource transaction to fabricate.
7. Record every later transition phase and its exact singleton pending command stage before
   emitting that command. After prerequisites, coordinator
   selects/allocates the exact registration, publishes awaiting-target, and issues
   `PrepareFrameTarget`; only matching FIFO `FrameTargetPrepared` permits presentation.
8. Convert every synchronous port result into a typed fact and append it to the same
   mailbox while `advancing`; callbacks follow the same queue and never reduce recursively.
9. Consume exact prepared-frame proof, remove it from awaited proofs, publish successor
   `Committing` awaiting only publication acknowledgement, and issue only the combined
   successor transaction.
10. Only exact matching `Applied(Successor)` may replace the initial/transition committed
    presentation, publish success, and then cancel timer/release predecessor. For an
    adopted initial baseline this is its first legal release point. Rejection publishes
    failure first and releases successor only.

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
- typed command-stage rejection;
- Retry;
- publication replacement;
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

Commands carry `ReaderTransitionId` and the minimum matching evidence. Ports may reject
stale commands but cannot substitute another transition. Every synchronous rejection is
converted to a typed fact and appended to the same FIFO while `advancing`; no dispatcher
may terminate directly, silently return, throw past the mailbox, or substitute a timeout.

### 8.0.1 Typed command-stage rejection

The common model defines bounded, content-free stages and reasons:

```kotlin
enum class ReaderTransitionCommandStage {
    SemanticSynchronization,
    MaterialAllocation,
    RasterPreparation,
    DeckReservation,
    FrameTargetPreparation,
    FramePresentation,
    RetainedPublication,
    SuccessorPublication,
    TimerBinding
}

enum class ReaderTransitionCommandRejectionReason {
    MissingHandle,
    ExpiredHandle,
    ConsumedHandle,
    WrongSessionHandle,
    SemanticSlotCapacity,
    SemanticRegistryRejected,
    SemanticExecutionRejected,
    MaterialAllocationRejected,
    RasterPreparationRejected,
    DeckReservationRejected,
    FrameTargetRejected,
    FramePresentationRejected,
    PublicationRejected,
    TimerBindingRejected,
    TimerOwnershipConflict,
    CommandPortRejected,
    CommandThrew
}

@JvmInline
value class ReaderSemanticInvocationId internal constructor(val value: Long) {
    init { require(value > 0L) }
    override fun toString(): String =
        "ReaderSemanticInvocationId(<redacted>)"
}

enum class ReaderSaturatingCallbackCount {
    Zero,
    One,
    Two,
    ThreeOrMore;

    fun increment(): ReaderSaturatingCallbackCount = when (this) {
        Zero -> One
        One -> Two
        Two, ThreeOrMore -> ThreeOrMore
    }
}

enum class ReaderSemanticBufferedViolationStatus {
    None,
    DuplicateCallback
}

enum class ReaderSemanticPortContractViolationReason {
    CallbackThenRejected,
    CallbackThenThrew,
    RejectedThenLateCallback,
    DuplicateCallback,
    DuplicateCallbackThenRejected,
    DuplicateCallbackThenThrew,
    RejectedAfterMutationStarted,
    ThrowAfterMutationStarted
}

@JvmInline
value class ReaderPhysicalReleaseAttemptId internal constructor(val value: Long) {
    init { require(value > 0L) }
    override fun toString(): String =
        "ReaderPhysicalReleaseAttemptId(<redacted>)"
}

enum class ReaderReleaseCommandRejectionReason {
    PortRejectedNoEffect,
    OwnerUnavailableNoEffect,
    RegistrationRejectedNoEffect
}

enum class ReaderReleaseCommandAmbiguityReason {
    ThrowBeforeEffectBoundaryWithoutProof,
    ThrowAfterPhysicalEffectMayHaveOccurred
}

enum class ReaderReleasePortContractViolationReason {
    CallbackThenRejected,
    CallbackThenThrew,
    LateCallbackAfterRejected,
    LateCallbackAfterAmbiguousFailure,
    DuplicateConfirmation,
    MultipleViolations
}

enum class ReaderReleaseBufferedViolationStatus {
    None,
    DuplicateConfirmation
}

data class ReaderReleaseCommandIdentity(
    val attemptId: ReaderPhysicalReleaseAttemptId,
    val registration: ReaderTransitionResourceRegistration
) {
    override fun toString(): String =
        "ReaderReleaseCommandIdentity(<redacted>)"
}

data class ReaderBufferedReleaseCallbackState(
    val latestAuthoritativeConfirmation: ReaderTransitionFact.ResourceReleased?,
    val callbackCount: ReaderSaturatingCallbackCount,
    val violationStatus: ReaderReleaseBufferedViolationStatus
) {
    fun record(
        confirmation: ReaderTransitionFact.ResourceReleased
    ): ReaderBufferedReleaseCallbackState {
        val nextCount = callbackCount.increment()
        return ReaderBufferedReleaseCallbackState(
            latestAuthoritativeConfirmation = confirmation,
            callbackCount = nextCount,
            violationStatus = if (nextCount == ReaderSaturatingCallbackCount.One) {
                ReaderReleaseBufferedViolationStatus.None
            } else {
                ReaderReleaseBufferedViolationStatus.DuplicateConfirmation
            }
        )
    }

    override fun toString(): String =
        "ReaderBufferedReleaseCallbackState(<redacted>)"
}

`ReaderTransitionPhaseContract` adds
`admissibleCommandStages: Set<ReaderTransitionCommandStage>` and
`ReaderActiveTransition` adds
`pendingCommandStages: Set<ReaderTransitionCommandStage>`. Both sets are enum-bounded;
`pendingCommandStages` has cardinality zero or one and must be a subset of the current
phase's admissible set. A phase with non-empty admissible stages includes
`ReaderTransitionFactKind.CommandRejected` in `callbackSources`; a phase with an empty
admissible set cannot carry a pending stage. Command emission is illegal unless the pending
set is empty; the reducer publishes the new phase/active state with the exact singleton
stage before returning that command.

sealed interface ReaderTransitionFact {
    data class CommandRejected(
        override val transitionId: ReaderTransitionId,
        val stage: ReaderTransitionCommandStage,
        val reason: ReaderTransitionCommandRejectionReason
    ) : ReaderTransitionFact

    data class SemanticPortContractViolated(
        override val transitionId: ReaderTransitionId,
        val invocationId: ReaderSemanticInvocationId,
        val reason: ReaderSemanticPortContractViolationReason,
        val callbackCount: ReaderSaturatingCallbackCount
    ) : ReaderTransitionFact {
        override fun toString(): String =
            "ReaderTransitionFact.SemanticPortContractViolated(<redacted>)"
    }

    data class PublicationReplaced(
        override val transitionId: ReaderTransitionId?
    ) : ReaderTransitionFact

    data class OwnedWorkCancellationApplied(
        val cleanupKey: ReaderReleaseOnlyCleanupKey,
        override val transitionId: ReaderTransitionId
    ) : ReaderTransitionFact {
        override fun toString(): String =
            "ReaderTransitionFact.OwnedWorkCancellationApplied(<redacted>)"
    }

    data class OwnedWorkCancellationRejected(
        val cleanupKey: ReaderReleaseOnlyCleanupKey,
        override val transitionId: ReaderTransitionId,
        val reason: ReaderReleaseOnlyCleanupFailureReason
    ) : ReaderTransitionFact {
        init {
            require(
                reason == ReaderReleaseOnlyCleanupFailureReason.CancellationRejected ||
                    reason ==
                        ReaderReleaseOnlyCleanupFailureReason.CancellationCommandThrew
            )
        }

        override fun toString(): String =
            "ReaderTransitionFact.OwnedWorkCancellationRejected(<redacted>)"
    }

    data class ResourceReleased(
        override val transitionId: ReaderTransitionId?,
        val identity: ReaderReleaseCommandIdentity
    ) : ReaderTransitionFact {
        override fun toString(): String =
            "ReaderTransitionFact.ResourceReleased(<redacted>)"
    }

    data class ReleaseOnlyCleanupDeadlineElapsed(
        val cleanupKey: ReaderReleaseOnlyCleanupKey
    ) : ReaderTransitionFact {
        override val transitionId: ReaderTransitionId? = null
        override fun toString(): String =
            "ReaderTransitionFact.ReleaseOnlyCleanupDeadlineElapsed(<redacted>)"
    }

    data class ReleaseCommandRejected(
        val cleanupKey: ReaderReleaseOnlyCleanupKey?,
        val identity: ReaderReleaseCommandIdentity,
        val reason: ReaderReleaseCommandRejectionReason
    ) : ReaderTransitionFact {
        override val transitionId: ReaderTransitionId? = null
        override fun toString(): String =
            "ReaderTransitionFact.ReleaseCommandRejected(<redacted>)"
    }

    data class ReleaseCommandThrew(
        val cleanupKey: ReaderReleaseOnlyCleanupKey?,
        val identity: ReaderReleaseCommandIdentity,
        val reason: ReaderReleaseCommandAmbiguityReason
    ) : ReaderTransitionFact {
        override val transitionId: ReaderTransitionId? = null
        override fun toString(): String =
            "ReaderTransitionFact.ReleaseCommandThrew(<redacted>)"
    }

    data class ReleasePortContractViolated(
        val cleanupKey: ReaderReleaseOnlyCleanupKey?,
        val identity: ReaderReleaseCommandIdentity,
        val reason: ReaderReleasePortContractViolationReason,
        val callbackCount: ReaderSaturatingCallbackCount
    ) : ReaderTransitionFact {
        override val transitionId: ReaderTransitionId? = null
        override fun toString(): String =
            "ReaderTransitionFact.ReleasePortContractViolated(<redacted>)"
    }
}
```

`ReaderTransitionFactKind` includes exact `SemanticPortContractViolated`,
`OwnedWorkCancellationApplied`, `OwnedWorkCancellationRejected`,
`ReleaseOnlyCleanupDeadlineElapsed`, `ReleaseCommandRejected`, `ReleaseCommandThrew`, and
`ReleasePortContractViolated`
entries matching this hierarchy; exhaustive kind switches may not collapse them into
`CommandRejected`, ordinary timer expiry, or resource confirmation.

Before issuing each command, the reducer records its exact stage as the active transition's
singleton pending set and ensures the phase contract admits it. Synchronous rejection leaves
that stage pending until its queued fact reduces. Exact typed success/acceptance clears the
stage before phase advance or before another command is emitted; async commands retain it
until their exact success/rejection callback. A phase advance requires the old pending set
to be empty. Terminal outcome, abort, supersession, and Retry-source retirement clear any
remnant before resource cleanup; Retry creates a new active transition with an empty set.
Close/replacement first copies the exact active ID and required cancellation into the atomic
release-only cleanup record, then clears active/pending state in that same reduction. Only
`CommandRejected` with the active ID and a currently
pending exact stage may consume that singleton and terminate the attempt.
`RequestSemanticSynchronization` is one reducer command and therefore has exactly one
reducer-visible stage, `SemanticSynchronization`. Every phase that may emit it includes
exactly that semantic stage in `admissibleCommandStages` and `CommandRejected` in callback
sources; it cannot admit an internal semantic substage. Before emitting it, the reducer publishes
that singleton pending stage. Handle lookup/take, registry validation, isolated-slot
reservation, and executable-request invocation remain private synchronous work inside the
port; they never advance reducer stage state. Missing, expired, consumed, or wrong-session
handle, registry rejection, slot capacity, and execution rejection/throw proven before any
Foliate mutation or callback each return bounded `Rejected` and become
`CommandRejected(transitionId, SemanticSynchronization, boundedReason)` before command
dispatch returns. Once mutation begins, callback ingress retains only the latest authoritative
receipt, saturating count, and fatal status until result return. Callback-then-rejection/throw,
rejection/throw after mutation start, late callback after rejection, and arbitrary duplicates
replace latest evidence without collection growth or callback exception, then queue the final
receipt exactly once plus one bounded session-fatal `SemanticPortContractViolated` safety fact.
A contract violation never consumes
or clears the stage as a normal rejection. The bounded reason may distinguish the internal
failure class, but no internal substage exists in the phase contract, active state, fact,
diagnostics, or tests. Exact semantic receipt/success or matching pre-mutation rejection
consumes the singleton; terminal advancement also clears it. A delayed normal rejection after
that clearance is inert.
Material-allocation rejection, timer-bind null/rejection/throw, and every equivalent
synchronous command-stage rejection follow the same one-command/one-stage rule.
Specialized `FrameTargetPreparationRejected` and
`OwnerAndInputPublicationRejected` remain exact protocol acknowledgements; their reducer
branches have the same terminal-before-release and fail-visible rules rather than being
collapsed into an untyped result.

An admitted command rejection consumes the already allocated transition sequence,
publishes `Failed(mappedReason, Retryable, truthfulRetainedOwner)` as a terminal outcome,
clears command-owned handles/slots/timer reservations and releases only successor/work
resources. The adopted baseline remains retained; neutral remains ownerless. UI projection
must expose Retry and close. Retry creates the next authentic operation identity and
reacquires every required semantic/material/timer fact. Timer-binding rejection terminates
immediately before timer-requiring work and never arms work without a timer, waits for a
timeout, or manufactures `DeadlineExpired`. A delayed rejection after exact success cleared
the stage and advanced phase is inert. The first matching rejection consumes the pending
stage while terminating; a duplicate therefore finds no active pending stage and is inert.
Wrong-ID/stage, stale, or post-terminal rejection facts are likewise inert except exact
resource disposition.

Close and replacement use an irreversible release-only cleanup transaction rather than
trying to admit cancellation against a cleared active phase:

```kotlin
enum class ReaderReleaseOnlyCleanupTrigger {
    PublicationClose,
    PublicationReplacement,
    ProcessClose
}

enum class ReaderReleaseOnlyCancellationStatus {
    NotRequired,
    Pending,
    Applied,
    Rejected,
    ProcessClosedPending
}

enum class ReaderReleaseOnlyCleanupFailureReason {
    CancellationRejected,
    CancellationCommandThrew,
    ProcessClosedBeforeCancellationAcknowledgement
}

enum class ReaderReleaseOnlyCleanupDeadlineStatus {
    Armed,
    CancelledAfterTerminalAccounting,
    Elapsed,
    BindingRejected,
    BindingThrew,
    CancellationRejected,
    CancellationThrew,
    ProcessClosed
}

enum class ReaderReleaseLedgerCleanupStatus {
    Open,
    EmptyReleased,
    TerminalFailure
}

@JvmInline
value class ReaderReleaseOnlyCleanupId internal constructor(val value: Long) {
    init { require(value > 0L) }
    override fun toString(): String =
        "ReaderReleaseOnlyCleanupId(<redacted>)"
}

@JvmInline
value class ReaderReleaseOnlyCleanupGeneration internal constructor(val value: Long) {
    init { require(value > 0L) }
    override fun toString(): String =
        "ReaderReleaseOnlyCleanupGeneration(<redacted>)"
}

data class ReaderReleaseOnlyCleanupKey(
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val cleanupId: ReaderReleaseOnlyCleanupId,
    val generation: ReaderReleaseOnlyCleanupGeneration
) {
    init {
        require(readerSessionGeneration > 0L)
        require(coordinatorEpoch > 0L)
    }

    override fun toString(): String =
        "ReaderReleaseOnlyCleanupKey(<redacted>)"
}

data class ReaderReleaseOnlyCleanupState(
    val key: ReaderReleaseOnlyCleanupKey,
    val trigger: ReaderReleaseOnlyCleanupTrigger,
    val closeOperationId: ReaderTransitionId?,
    val preCloseTransitionId: ReaderTransitionId?,
    val cancellationStatus: ReaderReleaseOnlyCancellationStatus,
    val cancellationFailure: ReaderReleaseOnlyCleanupFailureReason? = null,
    val deadlineStatus: ReaderReleaseOnlyCleanupDeadlineStatus
) {
    val isCancellationTerminal: Boolean
        get() = cancellationStatus != ReaderReleaseOnlyCancellationStatus.Pending

    fun shouldFenceDeadline(releaseLedgerStatus: ReaderReleaseLedgerCleanupStatus): Boolean =
        isCancellationTerminal &&
            releaseLedgerStatus != ReaderReleaseLedgerCleanupStatus.Open

    fun isTerminal(releaseLedgerStatus: ReaderReleaseLedgerCleanupStatus): Boolean =
        shouldFenceDeadline(releaseLedgerStatus) &&
            deadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.Armed

    fun isSuccessful(releaseLedgerStatus: ReaderReleaseLedgerCleanupStatus): Boolean =
        (
            cancellationStatus == ReaderReleaseOnlyCancellationStatus.NotRequired ||
                cancellationStatus == ReaderReleaseOnlyCancellationStatus.Applied
        ) &&
            releaseLedgerStatus == ReaderReleaseLedgerCleanupStatus.EmptyReleased &&
            deadlineStatus ==
                ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting

    init {
        require(trigger != ReaderReleaseOnlyCleanupTrigger.PublicationReplacement ||
            closeOperationId == null)
        require(closeOperationId == null ||
            closeOperationId.operation == ReaderTransitionOperation.PublicationClose)
        require((preCloseTransitionId == null) ==
            (cancellationStatus == ReaderReleaseOnlyCancellationStatus.NotRequired))
        require(
            when (cancellationStatus) {
                ReaderReleaseOnlyCancellationStatus.Rejected ->
                    cancellationFailure ==
                        ReaderReleaseOnlyCleanupFailureReason.CancellationRejected ||
                        cancellationFailure ==
                        ReaderReleaseOnlyCleanupFailureReason.CancellationCommandThrew
                ReaderReleaseOnlyCancellationStatus.ProcessClosedPending ->
                    cancellationFailure ==
                        ReaderReleaseOnlyCleanupFailureReason.ProcessClosedBeforeCancellationAcknowledgement
                else -> cancellationFailure == null
            }
        )
        require(closeOperationId == null ||
            closeOperationId.readerSessionGeneration == key.readerSessionGeneration)
        require(closeOperationId == null ||
            closeOperationId.coordinatorEpoch == key.coordinatorEpoch)
        require(preCloseTransitionId == null ||
            preCloseTransitionId.readerSessionGeneration == key.readerSessionGeneration)
        require(preCloseTransitionId == null ||
            preCloseTransitionId.coordinatorEpoch == key.coordinatorEpoch)
        require(closeOperationId == null || preCloseTransitionId == null ||
            closeOperationId != preCloseTransitionId)
    }

    override fun toString(): String =
        "ReaderReleaseOnlyCleanupState(<redacted>)"
}
```

An activated journal carries zero or one `releaseOnlyCleanup` record outside
`ReaderActiveTransition`. Before atomic installation there is intentionally no journal; the
activation coordinator's already-created release sink instead owns the same zero-or-one
cleanup slot for close from `Legacy`, `Freezing`, `DrainingLegacy`, `ReadyToCommit`,
`RestoringLegacy`, or `ActivationBlocked`. Those authorities are mutually exclusive by
activation state: installation requires the pre-install slot empty, and no path may create a
journal merely to represent pre-activation close. On accepted close or replacement, the
coordinator first prepares one exact two-second Task 6 physical fact-only deadline
registration; one atomic no-callback/no-suspend reduction then closes all normal ingress,
snapshots the exact active ID before clearing active/pending stage state, records `Pending`
cancellation for that ID (or `NotRequired` when no operation was active), installs the
cleanup ID plus generation and `Armed` deadline status, enters permanent `ReleaseOnly`, and
returns at most one `CancelOwnedWork(cleanupKey, preCloseTransitionId)`. The cleanup key is
opaque and redacted, is independent of operation identity, and is not an operation, parent,
or ninth activation state. The existing `PublicationClose` operation remains the only close
operation. An activated close stores that operation separately as `closeOperationId` so its
outcome can terminate after cancellation and release accounting become terminal and the
deadline is cancelled or elapsed; replacement and pre-activation close store no close
operation ID. Its cleanup target, when present, is the operation that was active before close
admission, never the close operation itself. `PublicationReplaced` allocates no operation or
transition sequence.

`CancelOwnedWork` produces dedicated FIFO
`OwnedWorkCancellationApplied(cleanupKey, transitionId)` or
`OwnedWorkCancellationRejected(cleanupKey, transitionId, boundedReason)` facts and matching
`ReaderTransitionFactKind` values. The keyed command and both facts render fixed redacted
constants. The cancellation port exposes only dedicated applied/rejected callbacks. A
callback delivered before `cancel` returns requires `Accepted`; a synchronous `Rejected`
return guarantees no callback and the dispatcher converts it to exact
`OwnedWorkCancellationRejected(..., CancellationRejected)`, while an escaping throw becomes
`OwnedWorkCancellationRejected(..., CancellationCommandThrew)`, all through the FIFO before
command dispatch returns. No outcome may be dropped or translated to active
`CommandRejected`. A rejected fact permits only
`CancellationRejected` or `CancellationCommandThrew`; process teardown alone writes
`ProcessClosedBeforeCancellationAcknowledgement` directly into the cleanup state.
Cancellation
is therefore not a `ReaderTransitionCommandStage` and never depends on active-phase
`pendingCommandStages`. Command dispatch recognizes `CancelOwnedWork` as cleanup-owned,
validates its key/ID against the installed `Pending` record rather than active phase state,
and emits it once. The release-only sink admits an outcome only when both cleanup key
and captured transition ID match its sole pending record. Exact applied marks `Applied`;
exact rejected records bounded fail-closed `Rejected`. Both are terminal cancellation
statuses, issue no Retry, and leave exact resource release progressing through the ledger.
Duplicate, stale, wrong-key, wrong-ID, and post-terminal cancellation outcomes are inert except
lawful exact resource observation/release. They cannot replace the record or emit cancellation
again. Cleanup accounting becomes terminal only when `isCancellationTerminal`, the exact
release ledger reports `EmptyReleased` or `TerminalFailure`, and the exact deadline is no
longer `Armed`. The dispatcher requests deadline cancellation only after the first two
conditions hold; exact accepted cancellation marks
`CancelledAfterTerminalAccounting`. Rejection or throw from deadline cancellation records
`CancellationRejected` or `CancellationThrew` respectively, remains fail-closed, and cannot reopen/fallback. For a stored close operation, successful cancellation
plus `EmptyReleased` publishes its existing successful close outcome; rejected cancellation,
terminal release failure, deadline binding/cancellation failure, or elapsed deadline publishes
the corresponding nonretryable fail-closed cleanup outcome. Replacement has no operation
outcome. Exact deadline elapsed marks `Elapsed`, records `CloseDrainTimeout`, terminalizes
unresolved release obligations without reissue, and leaves the release-only sink live for
lawful late exact confirmations.

`PublicationReplaced` remains a distinct fact and
`ReaderTransitionFactKind.PublicationReplaced`. It is not `PublicationClosed`, Retry, or an
operation. It uses the same cleanup transaction over active/no-active and adopted/neutral/
real committed baselines, retires semantic handles/slots/timer registrations, and releases
active/successor/committed resources exactly once. An adopted initial or real committed
resource releases through its lawful session/transition issuer; neutral issues no committed-
resource release. Replacement while already `ReleaseOnly` cannot overwrite the cleanup
record or emit `CancelOwnedWork` again. A new publication receives a new activation/session
baseline and inherits no old origin, cleanup record, proof, sequence, parent, Retry record,
or resource.

Process close invokes this same transaction if normal close has not begun, routing through the
existing `PublicationClose` operation admission when a close operation is required; it never
adds a kind. If already `ReleaseOnly`, it freezes the existing cleanup record, emits no
duplicate cancellation or release, fences/cancels the exact armed cleanup deadline, and drains
only already-queued exact cleanup outcomes and release confirmations within the fixed close
budget. A still-`Pending` cancellation becomes `ProcessClosedPending` with
`ProcessClosedBeforeCancellationAcknowledgement`; unresolved rejected/ambiguous release rows
become `ProcessClosedUnreleased` with bounded tombstones, and the deadline becomes
`ProcessClosed`. The existing close outcome records
`ProcessClosedBeforeCleanupCompletion` when observable. No Task 6 cleanup record, timer,
wake, or release failure is persisted, retried, or reconstructed after process death; no
physical release command is reissued.

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

An app-originated TOC/search/jump intent creates `ExternalSemanticRelocation`. From an
adopted or real committed predecessor it publishes retained owner/input narrowing and
waits for exact `Applied(Retained)` before issuing `RequestSemanticSynchronization`.
An unsolicited authoritative `FoliateDestinationCommitted` creates the same operation
with semantic proof already satisfied. From an adopted baseline it still performs the
retained publication gate; from a neutral baseline it skips that impossible gate because
there is no owner, binding, or resource to republish and proceeds from the authoritative
fact's exact binding. In either baseline the first such operation is sequence `1` with no
parent. Both paths allocate fresh material, request raster/deck work, run coordinator-owned
target preparation then exact presentation, consume `PreparedFrame`, enter `Committing`,
and commit only after exact `Applied(Successor)`. A real predecessor or adopted baseline
resource remains ledger-owned until that acknowledgement and then releases exactly once.
Failure from an adopted baseline retains it only as the diagnostic shield; failure from
neutral retains no visible owner/resource and exposes Retry/close chrome. Retry, another
relocation, publication replacement, or close either reuses the truthful committed
presentation or releases it through the ledger. No path invents a retained publication,
parent, operation, or binding for neutral.

A duplicate settlement fact or tagged fact for another transition is ignored after
reporting only a privacy-safe mismatch category. No acknowledgement may be inherited
by a later transition. Deterministic coverage must prove exact-once consumption,
duplicate delivery, an unrelated untagged same-session TOC relocation that clears
the old settlement and updates destination, callback before command return, and a
stale receipt after supersession.

#### 8.1.1 Initial-baseline reducer contract

Every reducer branch is exhaustive over both initial-origin variants:

| Trigger | Adopted predecessor baseline | Neutral baseline | Identity/release invariant |
|---|---|---|---|
| First bootstrap or semantic intent | Retain exact owner/binding/resource; narrow through existing retained publication when required | No retained publication; bootstrap must await authentic Foliate binding | Sequence `1`, no parent; no activation operation |
| Unsolicited authoritative relocation | Treat fact binding as semantic proof, acknowledge retained narrowing, then fresh physical successor | Treat fact binding as semantic proof and proceed directly to fresh physical successor | Existing `ExternalSemanticRelocation`; no synthetic binding or parent |
| Abort, typed command rejection, or timeout | Keep initial committed presentation and imported registration; release successor/work only | Keep truthful no-owner baseline; release successor/work only | Retryable terminal outcome precedes release; sequence remains consumed; rejection never waits for timeout |
| Retry | Create a fresh operation against the same committed baseline | Create a fresh operation against neutral and reacquire semantic binding | Next sequence and exact prior real-operation parent; no proof reuse |
| Visibility/restoration fact | Task 6 may only fence/cancel through its fact-only compatibility boundary; Task 7 must reacquire all semantic/material/frame proof before a new commit | Same, with no owner/resource resurrection | Initial origin is never persisted or reconstructed; this row does not move Task 7 implementation into Task 6 |
| Publication replacement | Atomically capture exact active cancellation target when present, enter permanent `ReleaseOnly`, emit keyed cancellation once, and release adopted/active/committed resources exactly once | Same cleanup transaction, with no baseline resource release | No operation/sequence; no-active replacement emits no cancellation; only matching cancellation/deadline/release-failure/resource facts remain admissible |
| Publication close | Existing close operation atomically captures only the pre-close active cancellation target, then enters permanent `ReleaseOnly`; release imported registration once by session authority | Same; issue no predecessor release | No-active close may be sequence `1`/no parent but emits no fabricated cancellation; cleanup never borrows the close operation identity |

Stale, duplicate, or mismatched facts cannot replace the baseline or release its resource.
Resource observation/confirmation remains admissible only through the release ledger/sink.
No equality or mismatch path may format the origin, seed, registration, physical identity,
session/epoch, binding, or lease; it emits only the bounded diagnostic projection in
Section 8.4.

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

sealed interface ReaderSemanticInvocationState {
    data object BeforeMutation : ReaderSemanticInvocationState
    data object InvokingWithoutCallback : ReaderSemanticInvocationState
    data class InvokingWithBufferedCallback(
        val latestAuthoritativeReceipt: ReaderPresentationEventReceipt,
        val callbackCount: ReaderSaturatingCallbackCount,
        val violationStatus: ReaderSemanticBufferedViolationStatus
    ) : ReaderSemanticInvocationState {
        fun record(receipt: ReaderPresentationEventReceipt): InvokingWithBufferedCallback {
            val nextCount = callbackCount.increment()
            return copy(
                latestAuthoritativeReceipt = receipt,
                callbackCount = nextCount,
                violationStatus = if (nextCount == ReaderSaturatingCallbackCount.One) {
                    ReaderSemanticBufferedViolationStatus.None
                } else {
                    ReaderSemanticBufferedViolationStatus.DuplicateCallback
                }
            )
        }

        override fun toString(): String =
            "ReaderSemanticInvocationState.InvokingWithBufferedCallback(<redacted>)"
    }
    data object AwaitingAsynchronousCallback : ReaderSemanticInvocationState
    data object ReceiptQueued : ReaderSemanticInvocationState
    data object RejectedWithoutCallback : ReaderSemanticInvocationState
    data object ContractViolated : ReaderSemanticInvocationState
}

data class ReaderSemanticInvocationRecord(
    val id: ReaderSemanticInvocationId,
    val transitionId: ReaderTransitionId,
    val state: ReaderSemanticInvocationState
) {
    override fun toString(): String =
        "ReaderSemanticInvocationRecord(<redacted>)"
}

sealed interface ReaderSemanticCommandResult {
    data object Accepted : ReaderSemanticCommandResult

    data class Rejected(
        val reason: ReaderTransitionCommandRejectionReason
    ) : ReaderSemanticCommandResult {
        init {
            require(
                reason == ReaderTransitionCommandRejectionReason.MissingHandle ||
                    reason == ReaderTransitionCommandRejectionReason.ExpiredHandle ||
                    reason == ReaderTransitionCommandRejectionReason.ConsumedHandle ||
                    reason == ReaderTransitionCommandRejectionReason.WrongSessionHandle ||
                    reason == ReaderTransitionCommandRejectionReason.SemanticSlotCapacity ||
                    reason == ReaderTransitionCommandRejectionReason.SemanticRegistryRejected ||
                    reason == ReaderTransitionCommandRejectionReason.SemanticExecutionRejected ||
                    reason == ReaderTransitionCommandRejectionReason.CommandThrew
            )
        }
    }
}

internal interface ReaderSemanticCommandPort {
    fun synchronize(
        command: ReaderTransitionCommand.RequestSemanticSynchronization,
        onReceipt: (ReaderPresentationEventReceipt) -> Unit
    ): ReaderSemanticCommandResult
}
```

Page turn, cover-entry, and relocation intents register their private requests before their
normal operation admission. Neutral bootstrap is different only in reservation timing: its
private current-destination request is registered synchronously before atomic installation,
transferred in the installed snapshot, and then carried by the post-install intent without
a second or nullable registration branch. All semantic intents otherwise use the same
one-shot handle and slot protocol. The bootstrap intent has no EPUB location and resolves
to the pre-reserved private live-Foliate request for
its current authoritative destination. The handle resolves exactly once in a bounded
Android session registry to a private executable closure containing the real Foliate
destination/action. Hrefs, CFIs, selections, annotation payloads, and other semantic
content never enter common state, persistence, diagnostics, equality, or logs. Before taking
the executable request, the port reserves one dedicated command slot containing
`ReaderSemanticCommandSlotId`, exact `ReaderTransitionId`, request handle, and a fresh
redacted `ReaderSemanticInvocationId`. The limits are eight active command slots and sixteen
pending request handles per reader session. The production adapter installs
`BeforeMutation`, performs every rejectable lookup/validation there, changes to
`InvokingWithoutCallback` immediately before the first possible Foliate mutation, and never
exposes a synchronous callback while `synchronize` is on the stack. First callback stores
`latestAuthoritativeReceipt` with count `One`. Every later callback replaces it, advances
`One → Two → ThreeOrMore → ThreeOrMore`, and marks duplicate violation. This total callback
path has no collection, size precondition, callback exception, or loss of final authority;
FIFO publication waits until invocation result return.

The ordering matrix is executable and exhaustive:

| Observed order | Required handling |
|---|---|
| `Accepted` after exactly one buffered callback | Change to `ReceiptQueued` and flush the latest exact receipt once. |
| `Accepted` after two or 3+ buffered callbacks | Flush only latest exact authority once, then one bounded duplicate violation with saturated count; close fail-visible. |
| `Accepted` without callback | Change to `AwaitingAsynchronousCallback`; first later callback queues once. If none arrives, the existing exact operation deadline emits `DeadlineExpired`; no new timer exists. |
| `Rejected(reason)` in `BeforeMutation` | Guarantee no mutation/callback, retain `RejectedWithoutCallback`, and queue exact `CommandRejected(transitionId, SemanticSynchronization, reason)`. |
| Throw in `BeforeMutation` | Guarantee no mutation/callback and queue the same exact rejection with `CommandThrew`. |
| One callback then `Rejected` or throw | Flush latest exact receipt once, no normal rejection, then one exact callback/result violation. |
| Two or 3+ callbacks then `Rejected` or throw | Flush only latest exact receipt once, then one bounded combined duplicate/result violation carrying saturated count. |
| Callback after `RejectedWithoutCallback` | Queue late latest exact receipt as authority, then one `RejectedThenLateCallback` violation. |
| Further callback after fatal marking | Replace retained latest authority and saturate metadata without allocation/throw; one coalesced drain publishes latest once and no second fatal fact. |
| `Rejected` or throw after mutation began without callback | Queue `RejectedAfterMutationStarted`/`ThrowAfterMutationStarted` violation instead of normal rejection. |

An exact contract-violation safety fact matches session/epoch/invocation tombstone rather than
the active pending stage. It atomically blocks every later semantic command for that session,
preserves the latest queued receipt/controller evidence, retires handle/slot once, and drives the
same fail-visible `PublicationClose` release-only transaction without fallback. Receipt FIFO
order is authoritative: a later rejection or safety fact cannot clear, replace, or outvote a
receipt. Repeated violations after the gate closes are inert. A normal accepted callback,
pre-mutation rejection/throw, supersession, deadline, replacement, or close retires both slot
and handle. The bounded slot/invocation fence stores one contiguous retired-through sequence
plus at most 32 out-of-order redacted tombstones; active slots take precedence and overflow
rejects new semantic work fail-closed. The slot is consumed at most once and supplies its
identity to the common receipt/controller path:

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
`SemanticCommand` slot; no reducer, gateway, host, or callback may infer it. A callback before
`synchronize` returns is buffered and flushed only after `Accepted`, never reduced
recursively. Wrong-slot, stale, or untagged settlement receipts cannot satisfy exact proof.
Duplicate/3+ callbacks retain only the latest exact authoritative receipt plus saturating
bounded metadata, enqueue that latest receipt once, and enter the fatal contract-violation path
without collection growth or callback throw. Unsolicited untagged relocation remains authoritative under Task 5's existing same-
publication arbitration and uses `UnsolicitedFoliate`.

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
`ReaderTransitionProofKind.FrameTargetPreparation`, admits exactly
`ReaderTransitionCommandStage.FrameTargetPreparation`, and lists
`FrameTargetPrepared`, `FrameTargetPreparationRejected`, and `CommandRejected` callback
sources. It then issues:

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
`OwnerAndInputPublicationAcknowledgement`, admits exactly `RetainedPublication`, and lists
`OwnerAndInputPublicationApplied`, `OwnerAndInputPublicationRejected`, and
`CommandRejected`. It emits only
`PublishRetainedOwnerAndInputLease`. Before exact `Applied(Retained)`, semantic,
allocation, raster, deck, target-preparation, frame-presentation, and timer-requiring
physical commands are prohibited. `Applied(Retained)` returns the same active operation
to its appropriate pre-work `Accepted`/`AwaitingPrerequisites` phase without success;
`Rejected(Retained)` terminates before successor work. No activated `ApplyInputLease`
route exists.

Consuming a matching `PreparedFrame` removes `PreparedFrame` from awaited proofs and
publishes successor `Committing`. Every successor `Committing` phase awaits exactly
`ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement`, admits exactly
`SuccessorPublication`, lists exactly `OwnerAndInputPublicationApplied`,
`OwnerAndInputPublicationRejected`, and `CommandRejected` as callback sources, retains
predecessor truth, timer, target, and successor resource, and emits only
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
publication identities, request/presented-frame sequences, resource registrations, and
all initial-origin seed/session/epoch/owner/binding/resource values are in-memory only and
forbidden from logs, diagnostics, analytics, screenshots, crash metadata, equality
diagnostics, and persistence. Production equality/mismatch reporting accepts only this
projection, never either origin object or its `toString()`:

```kotlin
enum class ReaderInitialOriginKind { AdoptedPredecessor, Neutral }
enum class ReaderInitialOriginOwnerKind { ShellCover, NativePage, Curl, LiveEngine }
enum class ReaderInitialOriginMismatchKind {
    Variant,
    Session,
    Epoch,
    Seed,
    Owner,
    Binding,
    ResourceOwner,
    ResourceKind,
    RetirementDomain,
    RequestedLease,
    PhysicalLease,
    Provenance
}

data class ReaderInitialOriginEqualityDiagnostic(
    val originKind: ReaderInitialOriginKind,
    val ownerKind: ReaderInitialOriginOwnerKind?,
    val resourceKind: ReaderTransitionResourceKind?,
    val mismatch: ReaderInitialOriginMismatchKind
)
```

All three enums are finite and content-free. Every newly introduced sensitive value type—
including initial lease/origin, adopted seed, composite physical identity,
imported registration, restoration checkpoint, decision, installation, committed
presentation/transition, journal, activated snapshot, semantic invocation ID/record,
release-only cleanup ID/generation/key/state, cleanup deadline registration/fact,
physical release attempt ID/identity/imported-legacy dispatch, fixed semantic/release callback state,
release rejection/throw/port-violation facts, bounded release-failure attempt rows/tombstones,
explicit retirement-fence snapshot/sanitized projection, and keyed cancellation command/outcome facts—
overrides `toString()` with a
constant redacted string or is an explicit non-data-class with equivalent redacted
rendering/equality. Production and test
code must not pass whole sensitive values to `assertEquals`, assertion messages, string
templates, exception messages, logs, or snapshot diff renderers. Tests compare individual
non-sensitive enum/state/count fields and use sanitized projections for sensitive equality;
failing messages are fixed bounded text. RED coverage must invoke direct `toString()` on
each wrapper and intentionally fail sanitized equality helpers to prove no seed/session/
epoch/binding/resource/lease/port/cleanup-key/close-operation/pre-close-transition value appears. Only bounded kind/state/mismatch
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
6. when a predecessor exists, carry the selected row's exact owner, truthful resource
   kind, binding, complete physical identity, and common provenance into a non-neutral
   adopted seed; fence/cancel every legacy claimed gesture; allocate a collision-free
   imported key and owner-independent retirement order; form `AdoptedPredecessor` from
   that exact seed/import after validating both requested and physical leases against its
   exact owner/binding and breadth ordering. With no predecessor, form `Neutral` with exact
   session/epoch and lawful identity-free no-owner leases but no
   seed, visible owner, binding, physical identity, kind, or registration;
7. for neutral only, synchronously register the private current-Foliate-destination request
   while routes/egress remain closed. A null handle fails before `ReadyToCommit`, performs
   exact-once activation-state cleanup, and follows pre-drain unfreeze or post-drain
   restoration/blocking; adopted carries no bootstrap reservation;
8. call `installActivatedSession` once with the production ports and complete immutable
   initial decision, including the exact neutral reservation when applicable. Inside the
   barrier, revalidate both leases and reservation ownership, derive the zero-sequence/no-
   parent initial journal, and include journal plus reservation in the sole snapshot write;
   successful return is the first observable
   `Activated` state, journal, optional owner/binding/resource, deck writer, physical
   input, reserved bootstrap capability, and open command egress;
9. only after successful install, enqueue `ReaderBootstrapNativePageIntent` with the exact
   pre-reserved handle when the origin is neutral. This starts the existing bootstrap
   operation through normal FIFO routing and live Foliate authority; there is no nullable
   post-install `register()` branch and it is not part of activation.

No seed/key cycle exists: inventory supplies the complete composite physical identity;
only a selected non-neutral row mints a seed ID and preserves its exact owner/kind/binding/
provenance; ledger import allocates a new collision-free coordinator-global key plus
retirement order; and only then is the adopted origin formed. Neutral mints neither seed
nor resource key. The source-local opaque token is never reused as a coordinator key.
Failure before step 5 reaches `Legacy` only after accepted complete unfreeze; rejected
unfreeze enters frozen `ActivationBlocked`. Failure from step 5 through atomic install
enters `RestoringLegacy`; only a complete restoration commit may return to `Legacy`,
otherwise the session enters `ActivationBlocked`. A failure after successful installation
remains coordinator-owned and fails closed. Publication close/replacement uses the atomic release-only cleanup
transaction: capture any exact pre-close active cancellation target, install its bounded
cleanup record, close ingress, and enter `ReleaseOnly` in the same reduction before
emitting at most one keyed cancellation command.

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

data class ReaderReleaseOnlyCleanupDeadlineRegistration(
    val id: ReaderTask6FactOnlyTimerRegistrationId,
    val cleanupKey: ReaderReleaseOnlyCleanupKey,
    val physicalIdentity: ReaderLegacyPhysicalIdentity,
    val hardExpiresAtMillis: Long
) {
    init {
        require(physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration)
        require(hardExpiresAtMillis >= 0L)
    }

    override fun toString(): String =
        "ReaderReleaseOnlyCleanupDeadlineRegistration(<redacted>)"
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

    fun prepareCleanupDeadline(
        cleanupKey: ReaderReleaseOnlyCleanupKey,
        onElapsed: (ReaderTransitionFact.ReleaseOnlyCleanupDeadlineElapsed) -> Unit
    ): ReaderReleaseOnlyCleanupDeadlineRegistration?

    fun matchingProgress(
        registration: ReaderTask6FactOnlyTimerRegistration,
        nowMillis: Long
    ): ReaderPortCommandResult

    fun snapshotForTask7Transfer(
        registration: ReaderTask6FactOnlyTimerRegistration
    ): ReaderTask6FactOnlyTimerTransferSnapshot?

    fun cancel(registration: ReaderTask6FactOnlyTimerRegistration): ReaderPortCommandResult

    fun cancelCleanupDeadline(
        registration: ReaderReleaseOnlyCleanupDeadlineRegistration
    ): ReaderPortCommandResult
}
```

Before `bind`, the reducer publishes exact pending `TimerBinding` in a phase whose
admissible stages include it and whose callback sources include `CommandRejected`.
Accepted binding clears that pending stage before timer-requiring work. A
null/rejected/throwing bind is translated before physical work into
`CommandRejected(transitionId, TimerBinding, TimerBindingRejected)` (or the bounded
`TimerOwnershipConflict` reason) and appended to FIFO; it is not permission to run without
a timer and does not synthesize deadline expiry.
`snapshotForTask7Transfer` returns the immutable registration plus its current exact next
expiry bounded by hard expiry; it neither transfers ownership nor arms the coordinator
clock by itself. For initial activation, deadline-requiring physical work cannot start until
one bound registration ID is accepted as owner. Supersession uses the existing gated-successor
swap. Close/replacement instead calls `prepareCleanupDeadline` on that same retained Task 6
physical timer source with the fresh cleanup ID/generation and an immutable two-second hard
expiry. Its callback is admission-gated until one atomic release-only commit installs the
cleanup record with `deadlineStatus = Armed`, replaces/fences any transition-timer owner, and
activates the cleanup registration. Before that commit only the predecessor timer callback is
admissible; afterward only exact `ReleaseOnlyCleanupDeadlineElapsed(cleanupKey)` is. Physical
predecessor cancellation follows the logical swap, so no zero- or two-owner interval exists.
No Task 7 coordinator-clock scheduling participates.

A null/rejected/throwing cleanup-deadline preparation cannot prevent visual close or reopen
legacy. The same atomic release-only commit records `BindingRejected`/`BindingThrew`, records
nonretryable `CloseDrainTimeout`, and terminalizes currently unresolved release entries for
bounded fail-closed accounting. While `Armed`, the release-only sink admits deadline elapsed
only for the sole cleanup ID and exact generation. The first match records `Elapsed`,
nonretryable `CloseDrainTimeout`, and timeout state for every unresolved release obligation;
stale ID, stale generation, duplicate, or post-completion delivery is inert. The deadline is
logically fenced and physically cancelled exactly once only after cancellation is terminal
and the release ledger is `EmptyReleased` or `TerminalFailure`; accepted cancellation records
`CancelledAfterTerminalAccounting`, `Rejected` records `CancellationRejected`, and throw
records `CancellationThrew`. Completed or fail-closed terminal cleanup rejects all later timer
delivery. Process close fences/cancels an armed deadline, marks `ProcessClosed`,
converts unresolved cleanup/release entries to non-persisted process-closed terminal state,
and creates no wake, recreation, or persisted deadline. This is the no-zero/no-two ownership
protocol; stale physical callbacks have no consequence authority.

| Operation or lifecycle trigger | Truthful retained owner / input | Required proof | Deadline | Timeout outcome | Named wake or recovery |
|---|---|---|---|---|---|
| Bootstrap native page | adopted shell/native/live predecessor, or no owner/resource from neutral / chrome only | adopted only: retained-owner publication ack; neutral: command-bound live-Foliate authoritative destination; then allocation + raster + admitted Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(MaterialTimeout, retryable)` retaining adopted baseline or truthful neutral | `Wake.Retry` |
| Shell-cover commit | prior native/live frame / no new page pointer | retained-owner publication ack → coordinator FrameHandoff + shell target preparation → sized post-draw/prepared frame → successor publication ack | 2 s hard | `Failed(CoverCommitTimeout, retryable)` | `Wake.HostAvailable` or `Wake.Retry` |
| Cover-to-page entry | shell cover / duplicate entry coalesces | retained-owner publication ack → allocation + raster + admitted Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(PageEntryTimeout, retryable)` | `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Curl claim and settlement | claimed curl stream / matching pointer only | retained-owner publication ack → one-shot settlement + destination allocation/raster/Deck → curl target preparation → prepared terminal frame → successor publication ack | 5 s hard | `Failed(SettlementTimeout, retryable)` with stable terminal frame | `Wake.FoliateDestinationCommitted` or `Wake.Retry` |
| Native-to-live handoff | native frame / no new page pointer | retained-owner publication ack → coordinator FrameHandoff + live target preparation → matching WebView exposure/prepared frame → successor publication ack | 2 s hard | `Failed(LiveExposureTimeout, retryable)` | `Wake.WebViewAvailable` or `Wake.Retry` |
| Live-to-native handback | live frame / no new page pointer | retained-owner publication ack → admitted Deck + native target preparation → prepared frame → successor publication ack | 2 s hard | `Failed(NativeHandbackTimeout, retryable)` | `Wake.RendererCapacityAvailable` or `Wake.Retry` |
| External semantic relocation | pre-relocation frame retained only as a noninteractive transition shield / chrome only | retained-owner publication ack → destination semantic/allocation/raster/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(ExternalRelocationTimeout, retryable)` with visible diagnostic over the retained shield | `Wake.FoliateDestinationCommitted`, `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Reflow/profile replacement | last valid cover/native/live frame / chrome only | retained-owner publication ack → fresh-profile allocation/raster/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(ReflowTimeout, retryable)` | `Wake.PaginationProfileReady`, `Wake.RasterProofAvailable`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Visibility-loss fact (not an operation) | latest valid frame identity retained logically / no physical input | persisted deferral accepted | immediate terminal deferral | `Deferred(VisibilityRestore)` | `Wake.VisibilityRestored` or `Wake.Restored` |
| Visibility restore | retained owner if still provable, otherwise shield / chrome only | retained-owner publication ack → fresh host/semantic/profile/allocation/raster/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(RestoreTimeout, retryable)` | `Wake.HostAvailable`, `Wake.WebViewAvailable`, `Wake.PaginationProfileReady`, `Wake.RendererCapacityAvailable`, or `Wake.Retry` |
| Renderer recovery | last truthful non-renderer owner or shield / chrome only | retained-owner publication ack → fresh renderer/allocation/Deck → target preparation → prepared frame → successor publication ack | 10 s without progress, 30 s hard | `Failed(RendererRecoveryTimeout, retryable)` | `Wake.RendererCapacityAvailable` or `Wake.Retry` |
| Publication close | no visual owner / navigation only | atomic release-only cleanup capture; exact keyed owned-work cancellation terminal; release ledger `EmptyReleased` or `TerminalFailure`; exact cleanup deadline cancelled or elapsed | exact retained Task 6 2 s physical cleanup deadline | `Failed(CloseDrainTimeout, nonretryable)` with the reader already visually closed, unresolved release rows terminalized without reissue, and bounded failure evidence retained | none; permanent sink admits only exact cancellation/deadline/release-failure/resource facts, while stale or duplicate cleanup generations are inert |

`AwaitingPrerequisites` uses the listed no-progress/hard bounds. When a truthful
predecessor resource exists, retained publication waiting precedes timer binding and all
successor work; `Applied(Retained)` returns to pre-work, and only then may the attempt
timer bind before its first timer-requiring command. A neutral initial origin has nothing
to republish, so the reducer skips that gate rather than fabricating its subject and binds
the first real operation's timer before its first timer-requiring semantic command.
Target preparation is an `AwaitingProof` phase whose sole proof is
`FrameTargetPreparation`, whose admissible command stage is
`FrameTargetPreparation`, and whose callback sources are prepared, specialized rejected,
and `CommandRejected`. `CommandIssued` must advance
synchronously to its matching `AwaitingProof` after acceptance or fail immediately.
Consuming `PreparedFrame` removes that proof. `Committing` retains predecessor truth,
successor resource, and timer while awaiting exactly
`OwnerAndInputPublicationAcknowledgement`, admitting only `SuccessorPublication`, and
receiving exactly Applied/Rejected protocol callbacks plus `CommandRejected`; it
uses only the operation's remaining budget without extending hard expiry. Prepared-frame
receipt is progress, not settlement or success.
Publication close is exempt from frame proof and uses its dedicated 2 s release-drain
bound. Task 6 retained-timer binding failure enqueues exact `CommandRejected` and Task 7
coordinator-clock scheduling failure enqueues its exact typed scheduling fact; neither
invokes a local consequence, substitutes a timeout, or retries automatically.

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
  — transition identity, exactly eleven operations, common resource provenance, identity-
  free initial input leases, typed initial committed-presentation origin and baseline,
  typed command-stage rejection and publication replacement facts, phase, outcome, pure
  policy, and resume-record schema.
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
  freeze inventory, imports common `ReaderTransitionResourceProvenance` after deleting its
  Android-only declaration, and accepts only coordinator-issued allocated bindings.
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
| `ReaderTransitionGateway` is Shadow-only and every accepted route currently continues to legacy dispatch | 2 inactive observation; 3 `installActivatedSession` accepts only the capability-authenticated real-adapter package, switches semantic intent/receipt routing with all material/exact-frame/sole-owner-input/resource routes, installs the typed adopted/neutral journal baseline with its optional initial owner/resource and exact physical lease, and makes no Shadow/LegacyOnly or no-op legacy consequence route reachable. Intent or unsolicited Foliate fact → coordinator mailbox | Installation succeeds only as one complete package with initial publication and open egress; a public Boolean/`complete()`/all-rejecting package cannot satisfy completeness; any post-install failure remains coordinator-owned | `closeToReleaseOnly` permanently rejects non-release routes / no legacy fallback |
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
annotations, raster payloads, bitmap data, renderer handles, WebView state, Whispersync
content, an initial-origin variant, seed ID, baseline session/epoch, owner, binding,
resource registration, or physical identity. The correlation nonce and timestamps are
internal matching
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

    override fun toString(): String = "ReaderLegacyFreezeToken(<redacted>)"
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

    override fun toString(): String = "ReaderLegacyPhysicalDomain(<redacted>)"
}

@JvmInline
value class ReaderLegacySourceLocalOpaqueToken internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }

    override fun toString(): String =
        "ReaderLegacySourceLocalOpaqueToken(<redacted>)"
}

data class ReaderLegacyPhysicalIdentity(
    val domain: ReaderLegacyPhysicalDomain,
    val source: ReaderLegacyInventorySource,
    val sourceLocalToken: ReaderLegacySourceLocalOpaqueToken
) {
    override fun toString(): String = "ReaderLegacyPhysicalIdentity(<redacted>)"
}

// Android-only envelope: common ReleaseResource remains source-set independent.
data class ReaderImportedLegacyReleaseDispatch(
    val command: ReaderTransitionCommand.ReleaseResource,
    val imported: ReaderImportedLegacyResourceRegistration
) {
    init {
        require(command.identity.registration == imported.registration)
    }

    override fun toString(): String =
        "ReaderImportedLegacyReleaseDispatch(<redacted>)"
}

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

    override fun toString(): String = "ReaderFrozenLegacyResource(<redacted>)"
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
resource kind, binding, owner, and provenance must be preserved. Zero rows is valid only
for `ReaderInitialCommittedPresentationOrigin.Neutral`, which publishes no visible owner,
binding, or physical resource. Native-
page and curl/native-material predecessors use `Deck`; shell-cover and live/WebView
predecessors use `FrameHandoff`. Any mismatched owner-kind pair blocks installation. A
shell cover is a real predecessor and must retain its directly reported identity, kind,
and binding. Zero does not permit a journal guess. The coordinator must never infer
visibility or resource kind from binding alone, active journal state, binding similarity,
gesture state, renderer generations, or acknowledgements.

Before entering `ReadyToCommit`, construct the candidate for one of the two initial origins;
neutral must also acquire its exact non-null reservation before the state may advance to
`ReadyToCommit`. Mint
`ReaderAdoptedPredecessorSeedId` only when a directly proven non-neutral predecessor
exists:

```kotlin
data class ReaderAdoptedPredecessorSeed(
    val id: ReaderAdoptedPredecessorSeedId,
    val physicalIdentity: ReaderLegacyPhysicalIdentity,
    val resourceKind: ReaderTransitionResourceKind,
    val binding: ReaderPresentationBinding,
    val owner: ReaderPresentationFrameOwner,
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val provenance: ReaderTransitionResourceProvenance =
        ReaderTransitionResourceProvenance.AdoptedLegacy
) {
    init {
        require(owner != ReaderPresentationFrameOwner.Neutral)
        require(resourceKind == readerAdoptedResourceKindFor(owner))
        require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
        require(physicalIdentity.domain.readerSessionGeneration == readerSessionGeneration)
    }

    override fun toString(): String = "ReaderAdoptedPredecessorSeed(<redacted>)"
}
```

When a directly proven predecessor exists, the seed uses its selected row's exact
complete identity, resource kind, rendered binding, visible owner type, reader-session
generation, and coordinator epoch. After the seed is minted, and not before, the ledger
allocates a collision-free coordinator-global resource key and independent
`ReaderResourceRetirementOrder`; the source-local token never becomes key `opaqueId`.
The resulting common `AdoptedPredecessor` origin copies the seed ID/session/epoch/owner/
binding/provenance, the imported registration, and the validated leases. A truthful
neutral start creates `ReaderInitialCommittedPresentationOrigin.Neutral` directly and
has no seed, physical identity, kind, binding, visible owner, or adopted registration.
A truthful shell cover keeps its directly reported shell identity, `FrameHandoff` kind,
and binding rather than inventing a native resource; native-page or curl material keeps
`Deck`, and live/WebView handoff keeps `FrameHandoff`. The seed describes only the
coordinator's non-neutral adoption event, never historical creation or an operation.
Neither origin assigns a fake transition identity, so both satisfy `T5-EXACT-SEED`.

Before the first destructive drain, the freeze port must return a complete checkpoint:

```kotlin
@JvmInline
value class ReaderLegacyRestorationCheckpointId internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }

    override fun toString(): String =
        "ReaderLegacyRestorationCheckpointId(<redacted>)"
}

data class ReaderLegacySourceRestartHandle(
    val source: ReaderLegacyInventorySource,
    val opaqueId: Long
) {
    init {
        require(opaqueId > 0L)
    }

    override fun toString(): String =
        "ReaderLegacySourceRestartHandle(<redacted>)"
}

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
    val requestedLease: ReaderInitialPresentationInputLease,
    val physicalLease: ReaderInitialPresentationInputLease
) {
    override fun toString(): String =
        "ReaderLegacyRestorationCheckpoint(<redacted>)"
}
```

The checkpoint validates positive IDs/generation, exact all-source handle coverage,
handle/source equality, neutral iff binding/identity/kind/provenance are all absent,
non-neutral provenance exactly `AdoptedLegacy`, exact
`readerAdoptedResourceKindFor(initialOwner)`, matching session/freeze domain, breadth
ordering, and independent requested-plus-physical lease compatibility with the exact
non-neutral owner/binding. Neutral validates both as lawful identity-free no-owner
`None` or `ChromeOnly`; curl restoration/adoption first fences and cancels legacy claimed gestures
and likewise permits only those two identity-free values. It stores the frozen route-table
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

- one `ReaderTransitionReleaseLedger` records every coordinator/imported registration and owns
  a fresh opaque `ReaderPhysicalReleaseAttemptId` → exact registration row before every
  physical command, in `ReleaseCommandIssued`, `RejectedNoEffect`, `AmbiguousFailure`,
  `Released`, `TimedOutUnreleased`, or `ProcessClosedUnreleased`; `Owned` registrations have
  no attempt yet;
- `ReleaseResource` always carries mandatory attempt identity and optional cleanup grouping.
  Imported-legacy dispatch additionally carries complete physical identity in its Android
  envelope. Equal source-local IDs from different sources/domains remain distinct; one
  registration can create at most one attempt/physical command;
- ordinary successor, abort, supersession, timeout, and terminal release attempts exist
  independently of cleanup. Close/replacement atomically attaches every unresolved ordinary
  attempt to the cleanup key without changing attempt/resource/state or reissuing a command;
- generic and legacy release adapters buffer fixed state only: latest exact confirmation,
  saturating `Zero/One/Two/ThreeOrMore` callback category, and bounded violation status. Before
  dispatch that state is exactly `latestAuthoritativeConfirmation = null`, count `Zero`, and
  violation `None`. Every callback replaces latest authority and is total/non-throwing with no
  collection growth;
- `Accepted` plus callback flushes exact attempt-keyed confirmation to `Released`; Accepted
  without callback awaits the existing confirmation/deadline. `Rejected` without callback
  guarantees no effect/callback and records `RejectedNoEffect`; throw without callback records
  `AmbiguousFailure`; neither can reissue;
- callback then Rejected/throw flushes latest confirmation first to `Released`, then one bounded
  `ReleasePortContractViolated`; result failure cannot override physical evidence. Late
  callback after rejected/ambiguous likewise promotes exact attempt to `Released` plus one
  violation. Duplicate/3+ confirmations remain idempotent, retain latest exact authority,
  saturate metadata, throw nothing, and emit at most one coalesced violation per drain;
- exact cleanup timeout converts unconfirmed issued/rejected/ambiguous attempts to
  `TimedOutUnreleased`; process close converts them to `ProcessClosedUnreleased`.
  Rejected/ambiguous causes additionally receive bounded attempt-keyed tombstones. Active
  attempt rows are bounded by the existing owned-registration ledger capacity and outrank at most
  32 tombstones; overflow blocks admission. Wrong attempt,
  registration/order, cleanup generation, or legacy identity is inert;
- exact late confirmation may promote issued/rejected/ambiguous/timeout/process-closed attempt
  to `Released` without reissue. Legacy confirmation must also match complete physical
  identity. `ReaderResourceRetirementOrder` remains independent of owner/transition;
- `ReaderResourceRetirementFenceSnapshot` is an explicit non-data class with private raw
  sequence fields, exact explicit equality, constant non-data-derived hash, fixed redacted
  `toString()`, internal `confirms(order)`, and a sanitized projection exposing only released-
  prefix presence, bounded gap count, and capacity category. Raw sequence values are forbidden
  in assertion operands/messages and diagnostic rendering;
- publication close/replacement atomically captures any exact pre-close active ID and
  outstanding owned-work cancellation in one `ReaderReleaseOnlyCleanupState`, attaches every
  unresolved ordinary release attempt without reissue, prepares and
  installs the exact two-second Task 6 cleanup deadline, emits `CancelOwnedWork` at most
  once, and enters `ReleaseOnly`; no-active close records `NotRequired` and fabricates no
  cancellation;
- `ReleaseOnly` is permanent and accepts only exact attempt-keyed resource observation/
  confirmation, release rejection/throw/port-violation, cancellation applied/rejected, and
  `ReleaseOnlyCleanupDeadlineElapsed` matching the sole cleanup ID plus generation. It
  rejects semantic, material, deck, frame, input, presentation, ordinary timer, Retry,
  restoration, and transition consequences;
- cancellation and release accounting remain independent. The cleanup deadline is cancelled
  only when cancellation is terminal and the ledger is `EmptyReleased` or
  `TerminalFailure`; deadline elapsed records nonretryable `CloseDrainTimeout`, terminalizes
  unresolved release accounting, never retries, and leaves the sink permanently live for
  lawful late confirmations;
- replacement/repeated close cannot overwrite the record, re-emit cancellation/release, or
  create a second cleanup timer. Process close fences the armed deadline, marks unresolved
  cancellation/release state process-closed, destroys rather than persists it, and creates no
  Task 6 wake/recreation behavior.

An adopted initial baseline or later committed cover/native/live frame remains retained
until exact queued `Applied(Successor)` acknowledges the combined owner/input publication,
or terminal lifecycle/close invalidates it. The first successor's `PreparedFrame`, commit
command return, timeout, abort, retry, or rejected/stale acknowledgement cannot release an
adopted baseline. Exact applied acknowledgement first replaces
`ReaderCommittedPresentation.Initial` with `Transition`, publishes terminal success, then
cancels the timer and issues the adopted resource's single session-authority release. A
neutral baseline has no release command. Reflow invalidates material identity and input
leases, not Foliate semantic authority or a valid committed cover. An old callback can
report only its exact stale resource identity; the coordinator leaves the current
transition unchanged and applies the release ledger.

## 13. Input Contract

Input is an epoch-scoped coordinator lease published by the same sole snapshot
transaction as the typed initial origin, every later committed visual owner, and every
transitional retained-owner narrowing/revocation. The adopted origin publishes its exact
owner/binding/resource and lease; neutral publishes no visible presentation and a lease
that admits no page or cover pointer. `ApplyInputLease` cannot be an
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
   exact presentation, release-sink, typed adopted/neutral initial committed-presentation
   origin, its optional visible owner/resource and exact physical lease, baseline-aware
   pure journal, retained-input and two-phase successor publication, and retained fact-
   only timer routes from a capability-authenticated real-adapter package while production
   coordinator-clock scheduling remains inactive. The first real operation is sequence `1`
   with no parent; neutral physical bootstrap uses the existing `BootstrapNativePage`
   operation and a command-bound live-Foliate destination before allocation. Retained
   `Applied` gates successor work only when a predecessor resource exists; coordinator
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
`PrepareFrameTarget`/FIFO target proof/exact presentation, atomic installation of the typed
initial origin and baseline journal, retained-owner acknowledgement before successor work
when such an owner exists, two-phase successor owner/input publication acknowledgement,
adopted predecessor ownership, exact
resource release, and the permanent release-only sink. It must nevertheless avoid a
lifecycle gap or a duplicate deadline writer while those routes are active. Production
composition is incomplete until `KomikkuReaderNativeFrameHost` installs only the
capability-authenticated real-adapter package and no `Shadow`/`LegacyOnly`, legacy
consequence writer, no-op `complete()`, or rejecting activated package is reachable.

Task 7 retains lifecycle policy, visibility/recreation handling, reflow/profile
replacement, renderer-loss recovery policy, all coordinator-clock deadline scheduling/policy,
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
- `T6-NO-DEADLINE-TRANSFER`: Task 7 retains coordinator-clock deadline ownership and policy.
  Task 6 keeps
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
  two-owner interval. The sole close-only exception is not a Task 7 transfer: Task 6 reuses
  that retained physical fact-only timer to prepare one exact two-second cleanup deadline
  keyed by opaque cleanup ID plus generation. Its callback may enqueue only
  `ReleaseOnlyCleanupDeadlineElapsed`; it creates no persisted wake/recreation and is fenced
  on cleanup completion or process close.
- `T6-NO-WAKE-TRANSFER`: Task 6 neither persists nor consumes resumable wake demand;
  Task 7 owns `ReaderTransitionWakeStore` and every SavedState wake route. The
  activation restoration checkpoint is in-memory safety state, not a wake record.

Each slice requires deterministic callback-before-ownership,
ownership-before-callback, supersession, timeout, restore, and release-once coverage.
Task 6 additionally requires these grouped named RED release blockers before existing
activation coverage:

- `adoptedBaselineFirstSuccessorStartsAtOneWithoutParentAndReleasesAfterApplied`;
- `neutralBaselineBootstrapUsesFoliateBindingAndExistingOperation`;
- `unsolicitedRelocationFromAdoptedBaselineRetainsThenReleasesExactlyOnce`;
- `unsolicitedRelocationFromNeutralBaselineNeedsNoRetainedPublication`;
- `initialOriginCannotFabricateOperationOrParent`;
- `transitionSequenceIsMonotonicAcrossAbortRetryAndRestore`;
- `retryAndRestorationPreserveTruthfulInitialBaseline`;
- `adoptedBaselineReleaseWaitsForSuccessorPublicationAcknowledgement`;
- `closeFromAdoptedBaselineReleasesOnceWithoutTransitionBorrowing`;
- `closeFromNeutralBaselineEntersReleaseOnlyWithoutResourceCommand`;
- `initialLeaseCannotCarryClaimedGestureOrTransitionId`;
- `curlAdoptionFencesLegacyGestureAndUsesIdentityFreeLease`;
- `neutralBootstrapRegistrationNullBlocksActivationBeforeAtomicInstall`;
- `failedNeutralBootstrapSemanticResolutionQueuesTypedRetryableFact`;
- `semanticMissingHandleRejectsSingleSynchronizationStage`;
- `semanticExpiredHandleRejectsSingleSynchronizationStage`;
- `semanticConsumedHandleRejectsSingleSynchronizationStage`;
- `semanticWrongSessionHandleRejectsSingleSynchronizationStage`;
- `semanticRegistryFailureRejectsSingleSynchronizationStage`;
- `semanticSlotCapacityRejectsSingleSynchronizationStage`;
- `semanticExecutionFailureRejectsSingleSynchronizationStage`;
- `semanticSynchronousCallbackThenAcceptedBuffersAndFlushesReceipt`;
- `semanticAcceptedThenAsynchronousCallbackQueuesOneReceipt`;
- `semanticRejectedWithoutCallbackQueuesExactSynchronizationRejection`;
- `semanticThrowBeforeMutationQueuesExactSynchronizationRejection`;
- `semanticCallbackThenRejectedPreservesReceiptAndClosesFailVisible`;
- `semanticCallbackThenThrowPreservesReceiptAndClosesFailVisible`;
- `semanticRejectedThenLateCallbackIsFatalContractViolation`;
- `semanticDuplicateCallbackIsFatalContractViolation`;
- `semanticThreeOrMoreSynchronousCallbacksSaturateWithoutThrowing`;
- `semanticDifferingDuplicateCallbacksRetainLatestAuthority`;
- `semanticCallbackCountSaturatesAtThreeOrMore`;
- `semanticDuplicateCallbackPathNeverThrows`;
- `semanticFatalFlushesLatestReceiptOnce`;
- `semanticAcceptedWithoutCallbackUsesExistingExactTimeout`;
- `delayedSemanticSynchronizationRejectionAfterStageClearIsInert`;
- `duplicateSemanticSynchronizationRejectionAfterStageClearIsInert`;
- `retryAfterFailedNeutralBootstrapUsesNextAuthenticIdentity`;
- `materialAllocationRejectionQueuesTypedFactAndTerminatesRetryable`;
- `timerBindingRejectionQueuesTypedFactWithoutTimeoutSubstitution`;
- `delayedCommandRejectionAfterStageAdvanceIsInert`;
- `duplicateCommandRejectionIsInertAfterPendingStageConsumed`;
- `timerBindingRejectionRequiresPendingTimerBindingStage`;
- `mismatchedRequestedLeaseRejectedForShellAdoption`;
- `mismatchedRequestedLeaseRejectedForNativeAdoption`;
- `mismatchedRequestedLeaseRejectedForCurlAdoption`;
- `mismatchedRequestedLeaseRejectedForLiveAdoption`;
- `initialNativePageLeaseAllowsTextureGenerationZero`;
- `publicationReplacementFromAdoptedBaselineClosesAndReleasesOnce`;
- `publicationReplacementFromNeutralBaselineClosesWithoutBaselineRelease`;
- `closeDuringSequenceOneOperationCapturesCleanupAndCancelsOnce`;
- `releaseOnlyCancellationRejectionRecordsFailClosedCleanupFailure`;
- `duplicateReleaseOnlyCancellationOutcomeIsInert`;
- `staleReleaseOnlyCancellationOutcomeIsInert`;
- `closeWithNoActiveOperationEmitsNoCancellation`;
- `releaseOnlyRejectsWorkAndCannotReopenTransition`;
- `replacementDuringActiveOperationUsesSameReleaseOnlyCleanupTransaction`;
- `replacementCannotReplaceExistingReleaseOnlyCleanupRecord`;
- `processCloseDoesNotReemitPendingCleanupCancellation`;
- `releaseOnlyCleanupDeadlineFiresAndRecordsCloseDrainTimeout`;
- `releaseOnlyCleanupDeadlineCancelsAfterCancellationAndLedgerTerminal`;
- `releaseOnlyCleanupDeadlineBindFailureFailsClosed`;
- `staleReleaseOnlyCleanupDeadlineGenerationIsInert`;
- `duplicateReleaseOnlyCleanupDeadlineElapsedIsInert`;
- `processCloseFencesReleaseOnlyCleanupDeadlineWithoutWake`;
- `completedCleanupCannotReceiveDeadlineElapsed`;
- `releaseCommandRejectedRecordsNoEffectWithoutReissue`;
- `releaseCommandThrowBeforeEffectBoundaryRecordsAmbiguousFailure`;
- `releaseCommandThrowAfterPossibleEffectRecordsAmbiguousFailure`;
- `ordinaryActivatedSuccessorReleaseRejectedTracksAttemptWithoutCleanup`;
- `ordinaryTerminalReleaseThrowTracksAttemptWithoutCleanup`;
- `unresolvedOrdinaryReleaseAttemptTransfersIntoCleanupWithoutReissue`;
- `genericReleaseOrderingMatrixIsAttemptExact`;
- `legacyReleaseOrderingMatrixIsAttemptAndPhysicalIdentityExact`;
- `releaseCallbackThenRejectedKeepsReleasedAndReportsViolation`;
- `releaseCallbackThenThrowKeepsReleasedAndReportsViolation`;
- `releaseLateCallbackPromotesRejectedOrAmbiguousAttempt`;
- `releaseDuplicateAndManyCallbacksSaturateWithoutThrowing`;
- `releaseLatestConfirmationRemainsAuthoritative`;
- `duplicateOrStaleReleaseFailureOutcomeIsInert`;
- `ambiguousReleaseFailureNeverIssuesSecondPhysicalRelease`;
- `releaseFailureBlocksCompletionUntilCleanupTimeout`;
- `resourceRetirementFenceRenderingIsContentFree`;
- `resourceRetirementFenceFailingEqualityUsesSanitizedProjection`;
- `resourceRetirementFenceAssertionsNeverRenderRawSequences`;
- `sensitiveInitialWrappersRenderOnlyRedactedConstants`;
- `sensitiveInitialEqualityFailureUsesSanitizedProjection`;
- `initialOriginEqualityDiagnosticsExposeOnlyBoundedCategories`.

The same RED group must table-drive atomic installation including
neutral, shell-cover, native-page (including texture generation zero), curl/native-material,
and live/WebView predecessor owner-kind cases; independently mismatched requested and
physical lease rejection for every adopted owner; neutral pre-install handle reservation,
null-registration cleanup/restoration with no snapshot write; complete-identity subordinate-owner
inventory, cross-source equal-local-ID drain/confirmation/import/release-once races;
adopted-seed and owner-independent retirement provenance; executable semantic handles
and isolated bounded slots behind one reducer-visible `SemanticSynchronization` stage with
all internal failure mappings, fixed latest-authoritative callback state, a saturating
`Zero`/`One`/`Two`/`ThreeOrMore` count, accepted/rejected/throw ordering, exact timeout,
and delayed/late/arbitrarily-many duplicate coverage proving no collection growth or callback
exception; material allocation; retained publication acknowledgement
before any successor work/timer binding; coordinator-selected Deck or ledger-allocated
FrameHandoff followed by awaiting-target `PrepareFrameTarget` and FIFO target fact;
sealed kind-specific target validation with no pre-command frame sequence, adapter-owned
registration, lookup/polling, or fabrication; consumed `PreparedFrame` followed by
successor `Committing` retaining predecessor truth awaiting only exact synchronous publication acknowledgement and
terminal-before-release ordering; capability-authenticated
production composition with no Shadow/no-op route; one retained fact-only timer with
inactive coordinator clock and lifecycle compatibility; restoration deadline through
all asynchronous confirmations and final atomic result; restoration/blocked activation;
no fallback; and close/replacement in every activation phase with exact pre-close cleanup
capture, keyed applied/rejected outcomes, cleanup ID/generation and retained two-second
fact-only deadline, no-active/duplicate/stale/process-close fencing; every ordinary or
cleanup-grouped physical release has a mandatory opaque attempt ID plus exact registration,
unresolved ordinary attempts transfer into cleanup without command reissue, and generic and
imported-legacy adapters obey the complete callback/result/throw matrix with fixed
latest-confirmation state, bounded saturated violation metadata, callback-authoritative release,
late promotion, optional cleanup grouping, imported Android physical-identity matching,
bounded active rows/tombstones with active-attempt precedence, and content-free explicit
retirement-fence rendering/equality diagnostics; permanent work rejection and no
Task 6 wake/recreation. Task 7 RED coverage must prove atomic timer transfer and deletion under expiry,
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

1. Task389 publishes this initial-origin amendment and its independent review. Task #385
   then completes the reopened real-owner/source/port wiring; Task #386 stays fail-closed
   until both are closed. Only then may Task384 complete the remaining migration slices.
   The public synthetic fixture must pass deterministic common/Android host coverage for
   typed adopted/neutral journal baselines, first-operation sequence/parent rules,
   neutral Foliate-authoritative bootstrap, unsolicited relocation from both baselines,
   retry/restoration and close, atomic installation with
   initial owner/input publication and truthful neutral/shell/native/curl/live owner-kind
   mapping, complete composite-identity subordinate-owner inventory and cross-source
   collision release races, adopted-seed and owner-independent retirement provenance,
   executable semantic handles and isolated bounded command slots, command-bound fixed
   latest-authoritative synchronous callback buffering and exhaustive Accepted/Rejected/throw/
   late/arbitrarily-many-duplicate/timeout receipt ordering with saturating metadata and no
   callback-path exception, material allocation, retained acknowledgement before successor
   work, coordinator-selected/ledger-allocated registration, awaiting-target
   `PrepareFrameTarget` plus FIFO target fact, sealed kind-specific target validation,
   exact presentation proof, consumed `PreparedFrame`, successor `Committing` awaiting
   only publication acknowledgement, terminal-before-release ordering,
   capability-authenticated production composition with no Shadow/no-op route, exactly one retained Task 6 fact-only timer with inactive production
   coordinator clock, atomic Task 7 timer transfer, lifecycle fact-only compatibility,
   restoration/blocked activation, no fallback, close in every activation phase with exact
   cleanup ID/generation, two-second retained fact-only deadline and process-close fencing,
   mandatory attempt-exact ordinary and cleanup-grouped release accounting, no-reissue
   transfer of unresolved ordinary attempts into cleanup, complete generic/imported-legacy
   callback/result/throw ordering, bounded active rows and tombstones, and sanitized
   retirement-fence diagnostics, with no physical command reissue; the
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

- `ReaderCommittedPresentation.Initial` represents adopted or neutral baseline without a
  `ReaderTransitionId`, operation, parent, binding/resource for neutral, or prepared-frame
  proof; first real operation is sequence `1` with no parent, later identities are
  monotonic and preserve authentic causal parent identity across abort/Retry/restore;
- neutral bootstrap uses only `BootstrapNativePage`; its private current-destination handle
  is reserved synchronously before `ReadyToCommit`, transferred by atomic install, consumed
  by sequence 1 after install, obtains binding from a command-bound live-Foliate receipt,
  and then uses existing allocation/resource/frame protocols. Null registration prevents
  installation and every route/publication/egress switch, retires activation-owned state
  exactly once, and follows the existing unfreeze/restoration/`ActivationBlocked`
  protocol;
- initial requested/physical leases use `ReaderInitialPresentationInputLease`, cannot
  encode `ReaderTransitionId` or `ClaimedGesture`, preserve breadth ordering, and are each
  independently owner/binding-compatible at origin, checkpoint, decision, and barrier;
  neutral validates both as lawful no-owner leases, curl fences/cancels every legacy
  claimed gesture, and initial native texture generation accepts zero without a migration
  claim;
- commonMain owns the exact two-value `ReaderTransitionResourceProvenance`; Android
  deletes its duplicate declaration and imports the common type;
- `installActivatedSession` atomically installs every route from an exact
  `ReaderProductionActivatedSessionPorts` package constructed by
  `KomikkuReaderNativeFrameHost` from real adapters, derives the zero-sequence/no-parent
  journal inside the validated barrier, and installs the
  typed baseline journal, exact pre-reserved neutral-bootstrap capability when applicable,
  optional initial owner/resource, and exact identity-free physical lease,
  marks `Activated`, and exposes command egress, with no
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
  bounded, exhaustively retired callback slot; the neutral bootstrap handle alone is
  reserved pre-install and is discarded exactly once on installation rejection or close if
  not consumed; successful sequence-1 `take` consumes it and leaves only a bounded
  tombstone;
- every active transition stores a zero-or-one `pendingCommandStages` set constrained by
  its phase's finite admissible set; `CommandRejected` is admitted only for matching active
  identity plus currently pending stage; exact success/rejection/phase advance/terminal cleanup,
  Abort, Retry, and supersession consume or clear it; replacement/close first captures the
  exact active cancellation target in the cleanup record and then clears it, so delayed,
  duplicate, stale, and wrong-stage rejection are inert;
- every physical raster/deck request follows an exact fresh material allocation;
- `RequestSemanticSynchronization` exposes exactly one reducer-visible
  `SemanticSynchronization` stage recorded before emission; handle lookup/take, registry,
  slot, and execution remain private port internals. The adapter buffers synchronous callback
  evidence until `synchronize` returns: `Accepted` flushes exactly one buffered receipt or
  awaits exactly one asynchronous receipt under the existing transition timeout; pre-mutation
  `Rejected`/throw queues the exact typed stage rejection and guarantees no callback. Callback
  ingress uses fixed state only: the first callback stores `latestAuthoritativeReceipt`; every
  later callback replaces it, marks fatal duplicate status, and increments a bounded count that
  saturates at `ThreeOrMore`. Callback then rejected/throw, rejection then late callback,
  mutation-started rejection/throw, or any duplicate callback queues the final latest exact
  receipt once plus one bounded `SemanticPortContractViolated`, blocks later semantic commands
  for the session, and enters fail-visible close/release-only with no fallback. Ingress never
  grows a collection, throws from a size precondition, propagates a callback exception, or loses
  the final authoritative receipt;
- every synchronous semantic internal, allocation, timer-bind, or equivalent command-stage
  rejection queues exact `CommandRejected` through FIFO, reduces to a
  retryable fail-visible terminal outcome, and never substitutes silence or timeout;
- `PublicationReplaced` exists in fact kind and fact hierarchies, is exhaustive over both
  baselines, allocates no operation, and uses the same irreversible release-only cleanup
  transaction as close: capture exact pre-close active ID in one bounded record before
  clearing active state, emit one keyed cancellation only when required, and release only
  lawful exact resources once;
- the permanent release-only sink keeps any admitted `PublicationClose` ID separate from the
  pre-close cancellation target and admits only exact cancellation applied/rejected, cleanup
  deadline elapsed, attempt-keyed release confirmation/rejection/throw/port violation, and
  lawful resource observation matching either its sole cleanup ID plus generation or an
  ordinary unresolved attempt atomically transferred into that cleanup. Transfer changes only
  optional cleanup grouping and emits no second release command. Cancellation and release
  accounting become terminal independently; the two-second retained Task 6 physical deadline
  is cancelled only
  when cancellation is terminal and the ledger is `EmptyReleased` or `TerminalFailure`.
  Exact elapsed records `CloseDrainTimeout`, terminalizes unresolved release entries, and
  never reopens or falls back; stale/duplicate/post-completion generations are inert. Process
  close fences the timer and creates no persisted wake/recreation. No-active close emits no
  cancellation; replacement/repeated/process close cannot overwrite or re-emit;
- before every generic or imported-legacy physical release dispatch, the ledger allocates one
  opaque `ReaderPhysicalReleaseAttemptId` and installs an active attempt row keyed by that ID
  plus exact registration; cleanup grouping is optional and the Android legacy envelope also
  carries complete physical identity. One registration can issue at most one command. Fixed
  adapter state starts with null latest confirmation, count `Zero`, and violation `None`.
  `Accepted` plus callback flushes the latest exact confirmation; accepted without callback
  waits for confirmation/deadline. `Rejected` without callback records `RejectedNoEffect`;
  throw without callback records `AmbiguousFailure`; neither reissues. Callback then rejected
  or throw remains `Released` and adds one bounded port violation. A late exact callback
  promotes rejected/ambiguous state to `Released`. Duplicate and arbitrarily many callbacks are
  idempotent, retain only latest evidence, saturate metadata at `ThreeOrMore`, and never throw.
  Confirmation/rejection/throw/violation facts match attempt ID plus exact registration, and
  imported legacy additionally matches complete physical identity. Active attempt rows outrank
  tombstones throughout ordinary operation, cleanup transfer, timeout, and process close;
  active rows and tombstones have bounded retention, and retirement-fence snapshots have fixed
  redacted rendering, explicit exact equality, a constant non-data-derived hash, and only
  sanitized bounded assertion projections with no raw retirement sequence in values, messages,
  or diagnostics;
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
  `OwnerAndInputPublicationAcknowledgement`, admitting only `SuccessorPublication`, with
  exactly Applied/Rejected protocol callback sources plus `CommandRejected`; only exact
  queued `Applied(Successor)` succeeds before timer cancellation and
  predecessor release, while Rejected retains predecessor and releases successor only;
- an adopted initial baseline follows that identical acknowledgement gate for its first
  successor and is never released by preparation, abort, timeout, Retry, stale/rejected
  acknowledgement, or diagnostic equality; neutral emits no predecessor release;
- raw initial-origin seed/session/epoch/resource identities, semantic invocation identities,
  release-only cleanup ID/generation/key/close-operation/pre-close IDs, cleanup deadline
  registrations/facts, physical release attempt IDs/identities/imported-legacy dispatches, fixed callback states, release
  rejection/throw/port-violation facts, active attempt rows/tombstones, retirement-fence raw
  sequences, and all frame-target/token/claim/publication identities, request/presented-frame
  sequences, and resource registrations remain in-memory and absent from logs, diagnostics,
  analytics,
  screenshots, crash metadata, equality diagnostics, and persistence; every newly
  introduced sensitive value type (lease/origin/seed/composite identity/import/checkpoint/
  decision/installation/committed transition/journal/snapshot/semantic invocation/release-only
  cleanup ID/generation/key/state/cleanup deadline registration and fact/physical release
  attempt and command identity/fixed callback state/rejection, throw, and port-violation fact/
  active attempt row and bounded tombstone/explicit retirement-fence snapshot and sanitized
  projection/keyed cancellation command and outcome facts) renders a constant redacted string
  or uses explicit non-data-class redacted rendering and exact equality with a safe hash policy,
  whole sensitive values are forbidden in assertions, raw retirement sequences never appear in
  assertion values/messages/diagnostics, and failing sanitized comparisons expose only
  bounded origin kind, owner/resource-kind, and mismatch categories;
- the restoration deadline survives request issuance and synchronous returns until all
  exact asynchronous source confirmations and final atomic restoration application;
- resource retirement order and bounded fences are independent of transition/resource
  owner identity;
- every Task 6 activated attempt has exactly one retained command-scoped fact-only timer;
  for narrowing operations it remains unbound until exact `Applied(Retained)` returns the
  operation to pre-work, then binds before the first timer-requiring command while
  production coordinator-clock scheduling is inactive. Release-only atomically replaces/
  fences that attempt owner with one exact two-second cleanup deadline keyed by cleanup ID plus
  generation; only its exact elapsed fact is admitted, and terminal accounting/process close
  fences it without persistence, wake, or recreation. Task 7 atomically transfers all other
  scheduling to that clock and deletes retained schedulers with no zero/two-owner
  interval; lifecycle compatibility remains fact-only with no overlapping consequence
  writer, and Task 7 retains lifecycle/reflow/recreation/deadline/wake policy;
- post-install failures never return to legacy and close retains the permanent release-only
  sink plus its bounded cleanup tombstone until completion or process teardown;
- all coordinator transition identities and outcomes are typed;
- no Android adapter directly publishes visual/input consequences;
- no accepted transition lacks a deadline and terminal route;
- release accounting is exactly once;
- the exact consecutive emulator gate passes;
- the bounded production-signed tablet gate passes afterward.
