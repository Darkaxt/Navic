# Reader Resumable Transition Coordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every delegated worker must be told: **“Do not spawn subagents.”**

**Goal:** Replace the distributed Android reader transition control plane with one resumable coordinator that activates once per session through a complete semantic/material/deck/frame/input/inventory/release barrier and gives every accepted event one terminal outcome, one deadline owner, one input lease, one named wake route, and exact-once resource release.

**Architecture:** Live Foliate remains semantic authority, passive Foliate remains material-only, PlayLikeCurl remains deformation and renderer-resource authority, and the common presentation reducer remains pure visual policy. A new Android main-thread session activation coordinator freezes and inventories every legacy source, adopts at most one directly proven predecessor, installs all Task 6 routes atomically, and then owns fail-closed/release-only routing. The resumable transition coordinator owns transition identity, a non-reentrant mailbox, command ordering, deadlines, deferrals, release accounting, and terminal publication; callbacks become typed fact producers and command-only adapters.

**Tech Stack:** Kotlin Multiplatform, Android View/WebView and SavedState Registry APIs, Compose Multiplatform, PlayLikeCurl, `kotlin.test`, Robolectric Android host tests, Gradle, and JavaScript source-contract gates.

---

## Governing documents

- Specification: `docs/superpowers/specs/2026-09-13-reader-resumable-transition-coordinator-design.md`
- Active Stage 6 ledger: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`
- Superseded implementation record: `docs/superpowers/plans/2026-09-01-reader-hierarchical-presentation-authority.md`

This is one tightly coupled Stage 6 replacement. Task 1 through Task 5 are inactive
preparation. Task 6 performs the sole atomic per-session activation of semantic,
material/deck, frame, input, exhaustive inventory/drain, typed adopted-or-neutral journal
baseline, and release-sink ownership. Task 7 separately migrates lifecycle, reflow,
deadlines, and wakes. Task389 is a release-blocking documentation amendment: Task #385 is
reopened for real-owner/source/port wiring against the initial-origin contract, and Task
#386 remains fail-closed until Task389 is published and #385 closes. Existing preparatory
adapters are not complete production wiring. No subsystem may activate as an independent
competing control plane.

## Execution constraints

- Do not start project Task9 or Stage 7.
- Android-only: do not perform iOS, macOS, or Native implementation, compilation, or validation.
- Do not modify Bindery code.
- Do not access devices, emulators, ADB, `.codex-validation`, or the protected crash log during Task384 host work.
- Automated tests are guardrails, not runtime acceptance.
- MAIN alone stages, commits, pushes, and dispatches CI. Delegated workers modify and test only their assigned package.
- Stage exact paths only. Never use `git add .`, `git add -A`, `git commit -a`, reset, restore, clean, amend, force-push, or destructive checkout.
- Do not log or persist publication text, URLs, hrefs, CFIs, book IDs, selections, annotations, raster payloads, wake nonce values, or wake timestamps.
- Each package uses one coherent RED group, one focused RED command, minimal GREEN implementation, one focused GREEN command, specification audit, then a MAIN-only commit and push.
- Focused GREEN never substitutes for the package’s broader affected gate or the final consolidated gate.

## Mandatory isolated-worktree preflight

The evidence worktree contains six rejected Task382 files and one protected crash log:

```text
C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-playlist-pattern-fix
 M composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt
 M composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt
 M composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt
 M composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt
 M composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt
 M composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt
?? composeApp/hs_err_pid29500.log
```

Task384 changes the same files. Preserve that worktree byte-for-byte as Task382
evidence and create the implementation worktree under the preferred disposable root:

```bash
EVIDENCE_ROOT='C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-playlist-pattern-fix'
TASK384_ROOT='D:/Temp/navic-task384-reader-resumable-transition-coordinator'

git -C "$EVIDENCE_ROOT" status --short --branch
git -C "$EVIDENCE_ROOT" rev-parse HEAD
git -C "$EVIDENCE_ROOT" rev-parse refs/remotes/fork/fix/foreground-webview-handoff-ownership
git -C "$EVIDENCE_ROOT" worktree add \
  -b task384/reader-resumable-transition-coordinator \
  "$TASK384_ROOT" \
  refs/remotes/fork/fix/foreground-webview-handoff-ownership
git -C "$TASK384_ROOT" status --short --branch
```

Expected: the evidence worktree retains all seven protected paths; the Task384
worktree is clean. If the target path or branch already exists, stop and inspect it;
do not overwrite, remove, reset, or reuse an unclassified worktree. Keep the
Task384 worktree through Task283. Do not remove it with shell deletion; use the
repository’s transactional cleanup policy after final closure.

## File responsibility map

### Create

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt` — common identity, exactly eleven operations, common resource provenance, identity-free initial leases, typed initial committed-presentation origin/baseline, first-operation sequence/parent allocation, command-granular admissible/pending stage state with one `SemanticSynchronization` stage, typed command rejection/publication replacement/semantic and release port violations, fixed latest-authority callback metadata, mandatory physical release attempt/registration identity with optional cleanup grouping, bounded release-only cleanup ID/generation/key/state and keyed cancellation/deadline/release facts/outcomes, phases/outcomes, target/publication protocols, finite wake, liveness, resume-record, and pure journal types.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt` — main-thread non-reentrant FIFO, journal advancement through successor `Committing` retaining predecessor truth, synchronous result-to-fact conversion, retained fact-only timers, one-shot consumption, attempt-keyed release ledger with ordinary-to-cleanup transfer, bounded active/tombstone retention, explicit privacy-safe retirement fence, terminal-before-release outcomes, and closed release sink.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt` — fixed non-throwing semantic and physical-release callback adapters, allocation, raster, deck, coordinator-registration target preparation/exact presentation, sole typed-result owner/input publication, capability-authenticated production package, inventory, resource, wake, and clock ports.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivation.android.kt` — one main-thread per-session activation state machine, opaque freeze/composite physical identities, exhaustive inventory/drain, owner-independent registration, pre-install neutral-bootstrap reservation, and one atomic production-ports/origin/barrier-derived-journal/reserved-bootstrap/optional-owner/identity-free-input/deck-writer/egress snapshot installation with bounded restoration/blocked states and permanent release-only sink.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStore.android.kt` — SavedStateRegistry-backed opaque wake records and restored-owner bootstrap.
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionModelTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionLivenessTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinatorTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionReleaseLedgerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmissionCutoverTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivationTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionAtomicCutoverTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStoreTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionIntegratedSequenceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionSourceAuditTest.kt`

### Refactor into fact/command adapters

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationReceipt.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt` — sole capability-authenticated production composition root and exhaustive installer; no Shadow/LegacyOnly/no-op/legacy consequence route survives install.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt` — bind coordinator-specified exact registrations to immutable physical frame targets, return target facts, and consume prepared targets without polling/substitution.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt` — delete the Android-only `ReaderTransitionResourceProvenance` declaration, import the exact common enum, retain renderer execution, and expose real freeze/inventory/command wiring.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`

`ReaderProcessState.kt` and `ReaderProcessStateViewModel.kt` retain unrelated UI
restoration and must not gain coordinator fields.

### Delete only after replacement gates pass

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycleDelivery.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeout.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationDispatchTimeout.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterDeferredRetryCoordinator.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageDeckRecoveryCoordinator.android.kt`

## Shared type contract

Create these common types before Android wiring. Android adapters map
`ReaderTransitionDeckRole` to the existing Android-only `ReaderDeckSubmissionRole`.
No common type may reference an Android source-set declaration.

```kotlin
data class ReaderTransitionParentIdentity(
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val sequence: Long
)

sealed interface ReaderExpectedPresentationBinding {
    data object NoCommittedPresentation : ReaderExpectedPresentationBinding

    data class Exact(
        val binding: ReaderPresentationBinding
    ) : ReaderExpectedPresentationBinding

    data class SemanticSuccessor(
        val predecessor: ReaderPresentationBinding,
        val requestSequence: Long
    ) : ReaderExpectedPresentationBinding

    data class FoliateAuthoritativeInitial(
        val requestSequence: Long
    ) : ReaderExpectedPresentationBinding {
        init {
            require(requestSequence > 0L)
        }
    }
}

enum class ReaderTransitionOperation {
    BootstrapNativePage,
    ShellCoverCommit,
    CoverToPageEntry,
    CurlClaimAndSettlement,
    NativeToLiveHandoff,
    LiveToNativeHandback,
    ExternalSemanticRelocation,
    ReflowProfileReplacement,
    VisibilityRestore,
    RendererRecovery,
    PublicationClose
}

data class ReaderTransitionDeadlinePolicy(
    val noProgressMillis: Long?,
    val hardMillis: Long
)

fun ReaderTransitionOperation.deadlinePolicy(): ReaderTransitionDeadlinePolicy = when (this) {
    ReaderTransitionOperation.BootstrapNativePage,
    ReaderTransitionOperation.CoverToPageEntry,
    ReaderTransitionOperation.ExternalSemanticRelocation,
    ReaderTransitionOperation.ReflowProfileReplacement,
    ReaderTransitionOperation.VisibilityRestore,
    ReaderTransitionOperation.RendererRecovery -> ReaderTransitionDeadlinePolicy(10_000L, 30_000L)

    ReaderTransitionOperation.ShellCoverCommit,
    ReaderTransitionOperation.NativeToLiveHandoff,
    ReaderTransitionOperation.LiveToNativeHandback -> ReaderTransitionDeadlinePolicy(null, 2_000L)

    ReaderTransitionOperation.CurlClaimAndSettlement -> ReaderTransitionDeadlinePolicy(null, 5_000L)
    ReaderTransitionOperation.PublicationClose -> ReaderTransitionDeadlinePolicy(null, 2_000L)
}

data class ReaderTransitionId(
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val sequence: Long,
    val operation: ReaderTransitionOperation,
    val expectedBinding: ReaderExpectedPresentationBinding,
    val parent: ReaderTransitionParentIdentity? = null
) {
    init {
        require(readerSessionGeneration > 0L)
        require(coordinatorEpoch > 0L)
        require(sequence > 0L)
    }
}
```

```kotlin
enum class ReaderTransitionWakeKind {
    Restored,
    HostAvailable,
    WebViewAvailable,
    PaginationProfileReady,
    FoliateDestinationCommitted,
    RasterProofAvailable,
    RendererCapacityAvailable,
    PresentationCommandApplied,
    VisibilityRestored,
    Retry
}

enum class ReaderTransitionPhaseKind {
    Accepted,
    AwaitingPrerequisites,
    CommandIssued,
    AwaitingProof,
    Committing
}

enum class ReaderTransitionFactKind {
    Intent,
    FoliateDestinationCommitted,
    SettlementAcknowledged,
    MaterialBindingAllocated,
    ViewportProfileReplaced,
    RasterProgress,
    RasterProven,
    RasterDeferred,
    RasterFailed,
    ResourceObserved,
    DeckReserved,
    DeckOwned,
    DeckPrepared,
    DeckRejected,
    ResourceReleased,
    RendererCapacityAvailable,
    HostAvailable,
    PaginationProfileReady,
    RendererGenerationReady,
    FrameTargetPrepared,
    FrameTargetPreparationRejected,
    PreparedFrame,
    OwnerAndInputPublicationApplied,
    OwnerAndInputPublicationRejected,
    CoverPostDraw,
    WebViewExposure,
    VisibilityChanged,
    ResourceLost,
    DeadlineExpired,
    CommandRejected,
    SemanticPortContractViolated,
    OwnedWorkCancellationApplied,
    OwnedWorkCancellationRejected,
    ReleaseOnlyCleanupDeadlineElapsed,
    ReleaseCommandRejected,
    ReleaseCommandThrew,
    ReleasePortContractViolated,
    Retry,
    PublicationReplaced,
    PublicationClosed
}

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

data class ReaderTransitionPhaseContract(
    val awaitedProofs: Set<ReaderTransitionProofKind>,
    val deadlineOwner: ReaderTransitionId,
    val supersession: ReaderTransitionSupersession,
    val retainedOwner: ReaderPresentationFrameOwner,
    val inputLease: ReaderTransitionInputLease,
    val callbackSources: Set<ReaderTransitionFactKind>,
    val admissibleCommandStages: Set<ReaderTransitionCommandStage>
) {
    init {
        require(awaitedProofs.isNotEmpty())
        require(callbackSources.isNotEmpty())
        require(admissibleCommandStages.size <= ReaderTransitionCommandStage.entries.size)
        require(
            (ReaderTransitionFactKind.CommandRejected in callbackSources) ==
                admissibleCommandStages.isNotEmpty()
        )
    }
}

data class ReaderTransitionPhase(
    val kind: ReaderTransitionPhaseKind,
    val contract: ReaderTransitionPhaseContract
)
```

Every successor `Committing` contract has
`awaitedProofs == setOf(OwnerAndInputPublicationAcknowledgement)`,
`admissibleCommandStages == setOf(SuccessorPublication)`, and
`callbackSources == setOf(OwnerAndInputPublicationApplied,
OwnerAndInputPublicationRejected, CommandRejected)`. Consuming `PreparedFrame` removes
`PreparedFrame` from awaited proof before `Committing` is published. An awaiting-target
contract uses exactly `FrameTargetPreparation`, admits only that command stage, and names
the two target-preparation fact kinds plus `CommandRejected`. A retained-owner publication
waiting contract uses exactly `OwnerAndInputPublicationAcknowledgement`, admits only
`RetainedPublication`, and names the two publication acknowledgement fact kinds plus
`CommandRejected`, but its matching `Applied(Retained)` returns to the operation's pre-work
`Accepted`/`AwaitingPrerequisites` phase rather than publishing successor success.

```kotlin
sealed interface ReaderTransitionOutcome {
    data class Succeeded(
        val committedOwner: ReaderPresentationFrameOwner,
        val binding: ReaderPresentationBinding
    ) : ReaderTransitionOutcome

    data class Failed(
        val reason: ReaderTransitionFailureReason,
        val retryability: ReaderTransitionRetryability,
        val retainedOwner: ReaderPresentationFrameOwner
    ) : ReaderTransitionOutcome

    data class Cancelled(
        val reason: ReaderTransitionCancellationReason,
        val retainedOwner: ReaderPresentationFrameOwner
    ) : ReaderTransitionOutcome

    data class Deferred(
        val resumeRecord: ReaderTransitionResumeRecord,
        val retainedOwner: ReaderPresentationFrameOwner
    ) : ReaderTransitionOutcome
}

enum class ReaderTransitionDeckRole {
    Initial,
    PageEntry,
    Settlement,
    Reflow,
    Recovery
}

enum class ReaderTransitionResourceKind {
    Deck,
    Raster,
    CallbackRegistration,
    FrameHandoff
}

enum class ReaderTransitionResourceProvenance {
    CoordinatorIssued,
    AdoptedLegacy
}

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

    override fun toString(): String =
        "ReaderTransitionResourceKey(<redacted>)"
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

    override fun toString(): String =
        "ReaderResourceRetirementOrder(<redacted>)"
}

data class ReaderTransitionResourceRegistration(
    val key: ReaderTransitionResourceKey,
    val retirementOrder: ReaderResourceRetirementOrder
) {
    override fun toString(): String =
        "ReaderTransitionResourceRegistration(<redacted>)"
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
            // Current authoritative initial-page proof permits generation zero.
            // This invariant correction is not a migration claim.
            require(textureGeneration >= 0L)
        }

        override fun toString(): String =
            "ReaderInitialPresentationInputLease.NativePage(<redacted>)"
    }
}

internal fun readerInitialLeaseIsNoBroaderThan(
    physical: ReaderInitialPresentationInputLease,
    requested: ReaderInitialPresentationInputLease
): Boolean

internal fun readerInitialLeaseIsCompatibleWithOwner(
    owner: ReaderPresentationFrameOwner,
    binding: ReaderPresentationBinding,
    lease: ReaderInitialPresentationInputLease
): Boolean

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
            require(readerInitialLeaseIsCompatibleWithOwner(owner, binding, requestedLease))
            require(readerInitialLeaseIsCompatibleWithOwner(owner, binding, physicalLease))
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

enum class ReaderTransitionCapabilityKind { Host, WebView, Renderer }

enum class ReaderTransitionProofKind {
    SemanticDestination,
    SettlementAcknowledgement,
    HostAvailable,
    PaginationProfile,
    RendererGeneration,
    MaterialBindingAllocation,
    Raster,
    DeckOwnership,
    DeckPrepared,
    FrameTargetPreparation,
    PreparedFrame,
    OwnerAndInputPublicationAcknowledgement,
    CoverPostDraw,
    WebViewExposure
}

enum class ReaderTransitionSupersession {
    CancelAndRetainOwner,
    CancelAndReleaseOwner,
    RejectSuccessor
}

sealed interface ReaderTransitionInputLease {
    data object None : ReaderTransitionInputLease
    data object ChromeOnly : ReaderTransitionInputLease
    data object CoverActions : ReaderTransitionInputLease
    data class NativePage(
        val binding: ReaderPresentationBinding,
        val textureGeneration: Long
    ) : ReaderTransitionInputLease
    data class ClaimedGesture(
        val transitionId: ReaderTransitionId,
        val gestureId: ReaderTransitionGestureId
    ) : ReaderTransitionInputLease
}

internal fun readerInputLeaseIsNoBroaderThan(
    physical: ReaderTransitionInputLease,
    requested: ReaderTransitionInputLease
): Boolean

enum class ReaderTransitionFailureReason {
    MaterialTimeout,
    CoverCommitTimeout,
    PageEntryTimeout,
    SettlementTimeout,
    LiveExposureTimeout,
    NativeHandbackTimeout,
    ExternalRelocationTimeout,
    ReflowTimeout,
    RestoreTimeout,
    RendererRecoveryTimeout,
    CloseDrainTimeout,
    CloseCleanupRejected,
    ProcessClosedBeforeCleanupCompletion,
    ActivationPrerequisiteMissing,
    InventoryIncomplete,
    InvalidLegacyResource,
    AmbiguousPredecessor,
    LegacyDrainFailed,
    PartialInstallationRejected,
    MaterialAllocationRejected,
    AtomicPublicationRejected,
    PortRejected,
    StaleProof
}

enum class ReaderTransitionRetryability { Retryable, NonRetryable }

enum class ReaderTransitionCancellationReason {
    Superseded,
    VisibilityLost,
    ReflowStarted,
    RendererLost,
    PublicationReplaced,
    PublicationClosed,
    UserCancelled
}

enum class ReaderTransitionDeferralReason {
    VisibilityRestore,
    HostUnavailable,
    WebViewUnavailable,
    PaginationUnavailable,
    RendererCapacityUnavailable
}

data class ReaderTransitionNonce(val high: Long, val low: Long)

data class ReaderTransitionResumeRecord(
    val operation: ReaderTransitionOperation,
    val reason: ReaderTransitionDeferralReason,
    val nonce: ReaderTransitionNonce,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val remainingRestorations: Int,
    val requiredWake: ReaderTransitionWakeKind
) {
    init {
        require(expiresAtMillis > issuedAtMillis)
        require(remainingRestorations == 1)
    }
}

enum class ReaderExternalRelocationSource { Toc, Search, Bookmark, Annotation, Jump }

sealed interface ReaderTransitionUserIntent

@JvmInline
value class ReaderSemanticRequestHandle internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

@JvmInline
value class ReaderSemanticCommandSlotId internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }
}

sealed interface ReaderSemanticSynchronizationIntent : ReaderTransitionUserIntent {
    val requestHandle: ReaderSemanticRequestHandle
}

@JvmInline
value class ReaderTransitionGestureId(val value: Long) {
    init {
        require(value > 0L)
    }
}

data class ReaderPageTurnIntent(
    val direction: ReaderPageTurnDirection,
    val gestureId: ReaderTransitionGestureId,
    override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data class ReaderExternalRelocationIntent(
    val source: ReaderExternalRelocationSource,
    override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data class ReaderCoverEntryIntent(
    override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data class ReaderBootstrapNativePageIntent(
    override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data object ReaderCoverReturnIntent : ReaderTransitionUserIntent
data object ReaderRetryIntent : ReaderTransitionUserIntent
data object ReaderCancelIntent : ReaderTransitionUserIntent

internal const val ReaderMaximumPendingFrameTargets = 8

data class ReaderTransitionFrameTargetHandle internal constructor(
    val readerSessionGeneration: Long,
    val publicationGeneration: Long,
    val opaqueId: Long
) {
    init {
        require(readerSessionGeneration > 0L)
        require(publicationGeneration > 0L)
        require(opaqueId > 0L)
    }
}

@JvmInline
value class ReaderShellCoverHostToken internal constructor(val value: Long) {
    init { require(value > 0L) }
}

@JvmInline
value class ReaderNativePageHostToken internal constructor(val value: Long) {
    init { require(value > 0L) }
}

sealed interface ReaderNativePageHostTokenState {
    data class Present(
        val token: ReaderNativePageHostToken
    ) : ReaderNativePageHostTokenState
    data object AuthoritativeAbsent : ReaderNativePageHostTokenState
}

@JvmInline
value class ReaderLiveHandoffToken internal constructor(val value: Long) {
    init { require(value > 0L) }
}

data class ReaderTransitionFrameGeometry(
    val viewportGeneration: Long,
    val layoutProfileGeneration: Long,
    val targetLeftPx: Int,
    val targetTopPx: Int,
    val targetWidthPx: Int,
    val targetHeightPx: Int
) {
    init {
        require(viewportGeneration > 0L)
        require(layoutProfileGeneration > 0L)
        require(targetWidthPx > 0)
        require(targetHeightPx > 0)
    }
}

data class ReaderPlayLikeCurlDeckTargetIdentity(
    val rendererGeneration: Long,
    val deckGeneration: Long,
    val role: ReaderTransitionDeckRole
)

enum class ReaderLiveHandoffDirection { NativeToLive, LiveToNative }

@JvmInline
value class ReaderLiveHandoffClaimIdentity internal constructor(val value: Long) {
    init { require(value > 0L) }
}

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

    data class ShellCover(
        override val handle: ReaderTransitionFrameTargetHandle,
        override val specification: ReaderTransitionFrameTargetSpecification.ShellCover,
        override val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFrameTarget

    data class NativePage(
        override val handle: ReaderTransitionFrameTargetHandle,
        override val specification: ReaderTransitionFrameTargetSpecification.NativePage,
        override val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFrameTarget

    data class CurlSettlementTerminalFrame(
        override val handle: ReaderTransitionFrameTargetHandle,
        override val specification:
            ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame,
        override val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFrameTarget

    data class LiveWebView(
        override val handle: ReaderTransitionFrameTargetHandle,
        override val specification: ReaderTransitionFrameTargetSpecification.LiveWebView,
        override val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFrameTarget
}

// The coordinator selects an admitted Deck for NativePage/CurlSettlementTerminalFrame
// or allocates a transition-owned FrameHandoff through its ledger for ShellCover/
// LiveWebView before PrepareFrameTarget. The adapter binds physical state to that exact
// supplied registration and may not allocate ownership or retirement order. The bounded
// registry rejects its ninth pending target and consumes each session/publication handle
// once. requestSequence is positive. There is no pre-command frameSequence; a callback-
// produced presented-frame sequence exists only inside PreparedFrame.frameOwner proof
// evidence.

Raw frame-target handles, shell/native host-token values, live handoff-token values,
handoff-claim identities, owner/input publication identities, request and presented-
frame sequences, resource registrations, and initial-origin seed/session/epoch/owner/
binding/resource values are in-memory capability material. They are forbidden from logs,
diagnostics, analytics, screenshots, crash metadata, equality diagnostics, and
persistence. Add the exact finite content-free diagnostic model:

```kotlin
enum class ReaderInitialOriginKind { AdoptedPredecessor, Neutral }
enum class ReaderInitialOriginOwnerKind { ShellCover, NativePage, Curl, LiveEngine }
enum class ReaderInitialOriginMismatchKind {
    Variant, Session, Epoch, Seed, Owner, Binding, ResourceOwner, ResourceKind,
    RetirementDomain, RequestedLease, PhysicalLease, Provenance
}
data class ReaderInitialOriginEqualityDiagnostic(
    val originKind: ReaderInitialOriginKind,
    val ownerKind: ReaderInitialOriginOwnerKind?,
    val resourceKind: ReaderTransitionResourceKind?,
    val mismatch: ReaderInitialOriginMismatchKind
)
```

Comparison/reporting APIs accept that projection only and must never format an origin,
seed, registration, or binding. Only bounded frame/origin kind, phase/state, mismatch-
category, and count values may be exposed.

data class ReaderMaterialGenerationAllocation(
    val transitionId: ReaderTransitionId,
    val allocatedBinding: ReaderPresentationBinding,
    val preparationGeneration: Long,
    val rasterGeneration: Long,
    val textureGeneration: Long
) {
    init {
        require(preparationGeneration > 0L)
        require(rasterGeneration > 0L)
        require(textureGeneration > 0L)
        require(allocatedBinding.preparationGeneration == preparationGeneration)
        require(allocatedBinding.rasterGeneration == rasterGeneration)
        require(allocatedBinding.textureGeneration == textureGeneration)
    }
}

@JvmInline
value class ReaderOwnerAndInputPublicationIdentity internal constructor(val value: Long) {
    init { require(value > 0L) }
}

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

sealed interface ReaderPresentationEventOrigin {
    data object NonSemantic : ReaderPresentationEventOrigin
    data object UnsolicitedFoliate : ReaderPresentationEventOrigin
    data class SemanticCommand(
        val transitionId: ReaderTransitionId,
        val slotId: ReaderSemanticCommandSlotId
    ) : ReaderPresentationEventOrigin
}
```

The fact hierarchy is also common and content-free:

```kotlin
sealed interface ReaderTransitionFact {
    val transitionId: ReaderTransitionId?

    data class Intent(
        override val transitionId: ReaderTransitionId?,
        val intent: ReaderTransitionUserIntent
    ) : ReaderTransitionFact

    data class FoliateDestinationCommitted(
        override val transitionId: ReaderTransitionId?,
        val binding: ReaderPresentationBinding
    ) : ReaderTransitionFact

    data class SettlementAcknowledged(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding,
        val acknowledgement: ReaderPageTurnSettlementAck
    ) : ReaderTransitionFact

    data class ViewportProfileReplaced(
        override val transitionId: ReaderTransitionId?,
        val profileGeneration: Long
    ) : ReaderTransitionFact

    data class MaterialBindingAllocated(
        override val transitionId: ReaderTransitionId,
        val allocation: ReaderMaterialGenerationAllocation
    ) : ReaderTransitionFact

    data class RasterProgress(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
    data class RasterProven(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
    data class RasterDeferred(
        override val transitionId: ReaderTransitionId,
        val reason: ReaderTransitionDeferralReason,
        val resumeRecord: ReaderTransitionResumeRecord
    ) : ReaderTransitionFact
    data class RasterFailed(
        override val transitionId: ReaderTransitionId,
        val reason: ReaderTransitionFailureReason
    ) : ReaderTransitionFact

    data class ResourceObserved(
        override val transitionId: ReaderTransitionId?,
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
    data class DeckReserved(
        override val transitionId: ReaderTransitionId,
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
    data class DeckOwned(
        override val transitionId: ReaderTransitionId,
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
    data class DeckPrepared(
        override val transitionId: ReaderTransitionId,
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
    data class DeckRejected(
        override val transitionId: ReaderTransitionId,
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
    data class ResourceReleased(
        override val transitionId: ReaderTransitionId?,
        val identity: ReaderReleaseCommandIdentity
    ) : ReaderTransitionFact {
        override fun toString(): String =
            "ReaderTransitionFact.ResourceReleased(<redacted>)"
    }
    data class RendererCapacityAvailable(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
    data class HostAvailable(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
    data class PaginationProfileReady(
        override val transitionId: ReaderTransitionId,
        val profileGeneration: Long
    ) : ReaderTransitionFact
    data class RendererGenerationReady(
        override val transitionId: ReaderTransitionId,
        val rendererGeneration: Long
    ) : ReaderTransitionFact

    data class FrameTargetPrepared(
        override val transitionId: ReaderTransitionId,
        val target: ReaderTransitionFrameTarget
    ) : ReaderTransitionFact

    data class FrameTargetPreparationRejected(
        override val transitionId: ReaderTransitionId,
        val specification: ReaderTransitionFrameTargetSpecification,
        val resource: ReaderTransitionResourceRegistration,
        val reason: ReaderTransitionFailureReason
    ) : ReaderTransitionFact

    data class PreparedFrame(
        override val transitionId: ReaderTransitionId,
        val target: ReaderTransitionFrameTarget,
        val frameOwner: ReaderPresentationFrameOwner,
        val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact

    data class OwnerAndInputPublicationApplied(
        override val transitionId: ReaderTransitionId,
        val subject: ReaderOwnerAndInputPublicationSubject,
        val publishedOwner: ReaderPresentationFrameOwner,
        val publishedBinding: ReaderPresentationBinding,
        val finalPhysicalInputLease: ReaderTransitionInputLease,
        val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
    ) : ReaderTransitionFact

    data class OwnerAndInputPublicationRejected(
        override val transitionId: ReaderTransitionId,
        val subject: ReaderOwnerAndInputPublicationSubject,
        val publicationIdentity: ReaderOwnerAndInputPublicationIdentity,
        val reason: ReaderTransitionFailureReason
    ) : ReaderTransitionFact
    data class CoverPostDraw(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding,
        val frameOwner: ReaderPresentationFrameOwner,
        val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
    data class WebViewExposure(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding,
        val frameOwner: ReaderPresentationFrameOwner,
        val resource: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact

    data class VisibilityChanged(
        override val transitionId: ReaderTransitionId?,
        val visible: Boolean
    ) : ReaderTransitionFact
    data class ResourceLost(
        override val transitionId: ReaderTransitionId?,
        val kind: ReaderTransitionCapabilityKind
    ) : ReaderTransitionFact
    data class DeadlineExpired(
        override val transitionId: ReaderTransitionId
    ) : ReaderTransitionFact
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
    data class Retry(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
    data class PublicationReplaced(
        override val transitionId: ReaderTransitionId?
    ) : ReaderTransitionFact
    data class PublicationClosed(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
}
```

The pure journal API used by common and Android tests is:

```kotlin
data class ReaderActiveTransition(
    val id: ReaderTransitionId,
    val phase: ReaderTransitionPhase,
    val pendingCommandStages: Set<ReaderTransitionCommandStage> = emptySet(),
    val preparedFrameOwner: ReaderPresentationFrameOwner? = null,
    val resolvedSuccessorBinding: ReaderPresentationBinding? = null,
    val predecessorResource: ReaderTransitionResourceRegistration? = null,
    val ownedResources: Set<ReaderTransitionResourceRegistration> = emptySet(),
    val admittedDeck: ReaderTransitionResourceRegistration? = null,
    val pendingPreparedDeck: ReaderTransitionResourceRegistration? = null,
    val frameTarget: ReaderTransitionFrameTarget? = null,
    val successorResource: ReaderTransitionResourceRegistration? = null,
    val pendingPublicationIdentity: ReaderOwnerAndInputPublicationIdentity? = null,
    val consumedSettlement: ReaderSettlementConsumptionKey? = null
) {
    init {
        require(pendingCommandStages.size <= 1)
        require(pendingCommandStages.all {
            it in phase.contract.admissibleCommandStages
        })
        require(pendingCommandStages.isEmpty() ||
            ReaderTransitionFactKind.CommandRejected in phase.contract.callbackSources)
    }

    override fun toString(): String = "ReaderActiveTransition(<redacted>)"
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
    init {
        require(lastTransitionSequence >= 0L)
        require(lastPublicationSequence >= 0L)
        require((lastTransitionSequence == 0L) ==
            (lastIssuedTransitionIdentity == null))
        require(lastIssuedTransitionIdentity == null ||
            lastIssuedTransitionIdentity.readerSessionGeneration ==
                committed.readerSessionGeneration)
        require(lastIssuedTransitionIdentity == null ||
            lastIssuedTransitionIdentity.coordinatorEpoch == committed.coordinatorEpoch)
        require(lastIssuedTransitionIdentity == null ||
            lastIssuedTransitionIdentity.sequence == lastTransitionSequence)
        require(releaseOnlyCleanup == null || active == null)
        require(releaseOnlyCleanup == null || retryableTransition == null)
        require(releaseOnlyCleanup == null ||
            releaseOnlyCleanup.key.readerSessionGeneration ==
                committed.readerSessionGeneration)
        require(releaseOnlyCleanup == null ||
            releaseOnlyCleanup.key.coordinatorEpoch == committed.coordinatorEpoch)
    }

    fun reduce(
        fact: ReaderTransitionFact,
        nowMillis: Long = 0L
    ): ReaderTransitionReduction = readerTransitionJournalReduce(this, fact, nowMillis)

    override fun toString(): String = "ReaderTransitionJournal(<redacted>)"
}

data class ReaderTransitionReduction(
    val state: ReaderTransitionJournal,
    val commands: List<ReaderTransitionCommand>
)
```

`readerTransitionJournalReduce` is pure and exhaustive over initial-origin variant,
operation, phase, and fact. The inactive production coordinator stores no journal before
the atomic activation snapshot; delete/default-construction paths that create an empty
journal and reject the first semantic fact. Test shadow journals must name an explicit
fixture origin. `ReaderCommittedPresentation.Initial` is the required activated journal
baseline and never has a `ReaderTransitionId`, operation, parent, synthetic binding,
transition-owned registration, or prepared-frame proof. The journal is created with exact
baseline session/epoch, `lastTransitionSequence = 0`, and no last-issued identity. The
first accepted real operation receives sequence `1` and `parent = null`; each later
accepted operation increments the sequence and uses exactly the prior real operation's
`parentIdentity()`. Abort, timeout, Retry, restore, supersession, unsolicited relocation,
and close cannot reuse a sequence. Presentation predecessor remains independently typed:
a failed first operation may leave `Initial` committed while its Retry is sequence `2`
with the failed operation as causal parent.

The reducer records at most one settlement consumption key on the active transition
before returning consequences, clears that key with the physical attempt, and never
inherits it into a successor transition. Resource-bearing facts are classified as
registration/observation, release confirmation, accepted proof, or rejected
resource disposition; one exact-key helper deduplicates release commands while
protecting only the truthful adopted baseline or committed successor. Exact
`Applied(Successor)` is the only reduction that replaces `Initial` with `Transition`; it
publishes terminal success before timer cancellation and predecessor release. A neutral
baseline has no predecessor release, while an adopted baseline releases exactly once by
session authority after its first acknowledged successor.

Before each command, the reducer requires an empty pending set and publishes the active
phase with the exact singleton `pendingCommandStages` value, which must belong to that
phase's finite `admissibleCommandStages`, before returning the command. Applicable phase
contracts include `CommandRejected` ingress. Synchronous rejection leaves the singleton
pending until its queued fact reduces. Exact success/accepted result clears it before phase
advance or before another command; async commands retain it through their exact success or
rejection callback. Phase advance requires the old set empty. Terminal outcome, abort, and
supersession clear remnants before cleanup; Retry creates a fresh active transition with an
empty set. Close/replacement first captures exact active ID and outstanding cancellation in
the atomic release-only cleanup record, then clears active/pending state in that reduction.

`RequestSemanticSynchronization` is one reducer command and admits only the singleton
`SemanticSynchronization` stage. Every phase that may emit it includes that stage and
`CommandRejected` ingress and cannot admit an internal semantic substage. The reducer records
it before emission; the port performs
handle lookup/take, registry validation, isolated-slot reservation, and executable-request
invocation synchronously without reducer-visible substage advancement. Missing, expired,
consumed, or wrong-session handle, registry rejection, slot capacity, and execution rejection/
throw proven before any mutation or callback each enqueue exact
`CommandRejected(transitionId, SemanticSynchronization, boundedReason)`. After mutation
starts, callback ingress retains only the latest authoritative receipt, a saturating count, and
fatal status until result return. Callback/result conflict, late callback after rejection,
unprovable post-mutation throw, or arbitrary duplicates replace latest evidence without
collection growth or callback exception, then queue the final receipt once plus one bounded
`SemanticPortContractViolated`, block later session semantic work, and close fail-visible. The
safety fact is not a command-stage rejection and cannot erase final authority. The bounded reason may retain the internal failure
class, but no handle/registry/slot/execution substage exists in phase state or tests. Exact
semantic success/receipt, matching pre-mutation rejection, or terminal
advancement clears the one stage; delayed and duplicate rejection after clearance is inert.
Every material-allocation rejection, timer-binding null/rejection/throw, and equivalent
one-command-stage rejection follows the same FIFO rule. The reducer exhaustively accepts
only exact active
ID plus a currently pending exact stage, consumes that singleton, publishes a retryable
`Failed` outcome with truthful retained owner, clears command-owned
handles/slots/timer reservations, releases successor/work only, and exposes Retry/close.
A delayed rejection after exact success cleared the stage and advanced phase is inert; the
first matching rejection consumes the stage while terminating, so a duplicate is inert.
Wrong-ID, wrong-stage, stale, or post-terminal facts are inert. Timer rejection occurs before
physical work and cannot run timerless or substitute `DeadlineExpired`/timeout. Specialized
target/publication rejections retain their exact fact branches and identical terminal-
before-release behavior.

Close and replacement execute one irreversible release-only cleanup transaction. The adapter
first prepares one exact two-second retained Task 6 physical fact-only deadline registration;
the same atomic reduction closes semantic/material/frame/input/ordinary-timer ingress,
snapshots the exact active transition ID before clearing active/pending stage state, stores one
bounded `ReaderReleaseOnlyCleanupState` with the truthful cancellation requirement and opaque
cleanup ID plus generation, atomically attaches every unresolved ordinary release-attempt row
to that cleanup key without command reissue, marks the deadline `Armed`, enters permanent
`ReleaseOnly`, and
returns at most one `CancelOwnedWork(cleanupKey, preCloseTransitionId)`. With no active
operation it stores `NotRequired` and emits no cancellation; it never substitutes a separately
admitted `PublicationClose` operation identity for the cancellation target or fabricates a
target. Activated close stores that separate admitted identity as `closeOperationId` for close
outcome completion; pre-activation close and `PublicationReplaced` store none. Replacement
remains fact-only and allocates no operation/sequence.

Cancellation uses dedicated FIFO `OwnedWorkCancellationApplied`/`Rejected` facts keyed by
both cleanup key and captured transition ID, not active `pendingCommandStages` and not a
`Cancellation` transition stage. The dispatcher treats `CancelOwnedWork` as cleanup-owned,
validates its key/ID against the installed `Pending` record rather than active phase state,
and emits it once. The cancellation port has only the two dedicated outcome callbacks. A
callback delivered before `cancel` returns requires `Accepted`; a synchronous `Rejected`
return guarantees no callback and is converted by the dispatcher to exact
`OwnedWorkCancellationRejected(..., CancellationRejected)`, while an escaping throw becomes
`OwnedWorkCancellationRejected(..., CancellationCommandThrew)`, all through the FIFO before
command dispatch returns. A rejected fact permits only those two reasons, while process
teardown writes `ProcessClosedBeforeCancellationAcknowledgement` directly into cleanup state.
Only the sole `Pending` exact record admits an outcome.
Applied makes cancellation terminal; rejected makes cancellation terminal while recording a
bounded fail-closed failure. Duplicate, stale,
wrong-key, wrong-ID, and post-terminal outcomes are inert and cannot re-emit cancellation,
reopen, or Retry. Both terminal cancellation outcomes leave exact resource release
progressing through the ledger. The deadline remains armed until cancellation is terminal and
the ledger reports `EmptyReleased` or `TerminalFailure`; only then may exact cancellation of
the deadline record `CancelledAfterTerminalAccounting`. A cancellation-port `Rejected`
records `CancellationRejected`; an escaping throw records `CancellationThrew`; neither
reopens, retries, or masks terminal accounting. Exact elapsed instead records
`CloseDrainTimeout`, terminalizes unresolved release accounting without reissue, and leaves
the permanent sink live for lawful late confirmation. A stored close operation publishes its
existing successful outcome only for successful cancellation plus `EmptyReleased`; rejected
cancellation, terminal release failure, deadline binding/cancellation failure, or elapsed
deadline publishes the corresponding nonretryable fail-closed outcome. Replacement has no
operation outcome.

`PublicationReplaced` is exhaustive over active/no-active and adopted/neutral/real-
committed presentation. It uses that transaction, retires handles/slots/timers, and releases
all exact old-publication resources once. Adopted baseline uses session release authority;
neutral has no baseline release. Replacement arriving after `ReleaseOnly` cannot overwrite
the existing cleanup record or emit cancellation again. A new publication starts a new
activation/session and inherits no origin, cleanup record, proof, identity, Retry record, or
resource. Process close invokes the same transaction through the existing
`PublicationClose` admission if close has not begun; otherwise it freezes the existing record,
emits no duplicate cancellation/release, and fences/cancels the exact cleanup deadline. It
drains only already-queued exact cleanup/release facts within the close budget, then converts
unresolved `Pending` cancellation to bounded fail-closed `ProcessClosedPending`, unresolved
rejected/ambiguous release rows to `ProcessClosedUnreleased` plus bounded tombstones, and the
deadline to `ProcessClosed`; it records `ProcessClosedBeforeCleanupCompletion` when an
observable close outcome remains. No cleanup record, timer, wake, failure, or release attempt
is persisted, reconstructed, or reissued.

The common command hierarchy uses only common types:

```kotlin
sealed interface ReaderResourceReleaseIssuer {
    data class Transition(
        val transitionId: ReaderTransitionId
    ) : ReaderResourceReleaseIssuer

    data class Session(
        val readerSessionGeneration: Long,
        val coordinatorEpoch: Long
    ) : ReaderResourceReleaseIssuer
}

sealed interface ReaderTransitionCommand {
    val transitionId: ReaderTransitionId?

    data class RequestSemanticSynchronization(
        override val transitionId: ReaderTransitionId,
        val intent: ReaderSemanticSynchronizationIntent
    ) : ReaderTransitionCommand

    data class AllocateMaterialBinding(
        override val transitionId: ReaderTransitionId,
        val semanticBinding: ReaderPresentationBinding
    ) : ReaderTransitionCommand

    data class RequestRasterPreparation(
        override val transitionId: ReaderTransitionId,
        val allocation: ReaderMaterialGenerationAllocation
    ) : ReaderTransitionCommand

    data class ReserveDeck(
        override val transitionId: ReaderTransitionId,
        val allocation: ReaderMaterialGenerationAllocation,
        val role: ReaderTransitionDeckRole
    ) : ReaderTransitionCommand

    data class PrepareFrameTarget(
        override val transitionId: ReaderTransitionId,
        val specification: ReaderTransitionFrameTargetSpecification,
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionCommand

    data class RequestFramePresentation(
        override val transitionId: ReaderTransitionId,
        val target: ReaderTransitionFrameTarget
    ) : ReaderTransitionCommand

    data class CommitOwnerAndInputLease(
        override val transitionId: ReaderTransitionId,
        val targetHandle: ReaderTransitionFrameTargetHandle,
        val owner: ReaderPresentationFrameOwner,
        val binding: ReaderPresentationBinding,
        val preparedFrameResource: ReaderTransitionResourceRegistration,
        val requestedLease: ReaderTransitionInputLease,
        val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
    ) : ReaderTransitionCommand

    data class PublishRetainedOwnerAndInputLease(
        override val transitionId: ReaderTransitionId,
        val retainedOwner: ReaderPresentationFrameOwner,
        val retainedBinding: ReaderPresentationBinding,
        val retainedResource: ReaderTransitionResourceRegistration,
        val requestedLease: ReaderTransitionInputLease,
        val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
    ) : ReaderTransitionCommand

    data class ReleaseResource(
        val issuer: ReaderResourceReleaseIssuer,
        val identity: ReaderReleaseCommandIdentity,
        val cleanupKey: ReaderReleaseOnlyCleanupKey? = null
    ) : ReaderTransitionCommand {
        override val transitionId: ReaderTransitionId?
            get() = (issuer as? ReaderResourceReleaseIssuer.Transition)?.transitionId
        override fun toString(): String =
            "ReaderTransitionCommand.ReleaseResource(<redacted>)"
    }

    data class CancelOwnedWork(
        val cleanupKey: ReaderReleaseOnlyCleanupKey,
        override val transitionId: ReaderTransitionId
    ) : ReaderTransitionCommand {
        override fun toString(): String =
            "ReaderTransitionCommand.CancelOwnedWork(<redacted>)"
    }
}
```

Facts cover every specification ingress: user intent, destination commit, settlement
acknowledgement, exact material-binding allocation, viewport/profile replacement,
raster progress/proof/deferral/failure, deck reservation/ownership/prepared/rejected/
released/capacity, frame-target prepared/rejected, exact-target prepared frame,
synchronous combined-publication applied/rejected acknowledgement, cover post-draw,
WebView proof, visibility, resource loss, deadline expiry, typed active command-stage rejection,
semantic and physical-release port-contract violations, attempt-keyed release confirmation/
rejection/throw with optional cleanup grouping, dedicated release-only cancellation applied/
rejected outcome, Retry, publication replacement, and publication close.

A settlement is consumed before consequences:

```kotlin
data class ReaderSettlementConsumptionKey(
    val transitionId: ReaderTransitionId,
    val expectedBinding: ReaderExpectedPresentationBinding,
    val acknowledgement: ReaderPageTurnSettlementAck
)
```

## Task 6 activation contract

Task 6 supersedes the former frame/input-only file map and package. It closes the six
verified preflight gaps together—atomic production activation, truthful predecessor,
complete inventory/drain, command-bound semantic identity, material allocation, and
combined owner/input publication—and the six independent-review defects in the first
amendment: initial publication outside installation, deadline/lifecycle gaps or dual
writers, non-executable semantic intents/shared unbounded slots, transition-dependent
adopted retirement, omitted subordinate physical owners, and unsafe direct rollback
after destructive drain. It also closes the latest three rereview defects: Task 6
coordinator-clock promotion despite Task 7 deadline ownership, universal
`FrameHandoff` adoption, and source-local physical-identity collisions across the 16
inventory sources. None may be hidden, waived, or deferred.

### Activation and freeze types

Create these Android-only types in `ReaderTransitionActivation.android.kt`:

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

internal sealed interface ReaderPortCommandResult {
    data object Accepted : ReaderPortCommandResult
    data class Rejected(
        val reason: ReaderTransitionFailureReason
    ) : ReaderPortCommandResult
}

@JvmInline
internal value class ReaderLegacyFreezeToken internal constructor(val value: Long) {
    init {
        require(value > 0L)
    }

    override fun toString(): String = "ReaderLegacyFreezeToken(<redacted>)"
}

internal data class ReaderLegacyPhysicalDomain(
    val readerSessionGeneration: Long,
    val freezeToken: ReaderLegacyFreezeToken
) {
    init {
        require(readerSessionGeneration > 0L)
    }

    override fun toString(): String = "ReaderLegacyPhysicalDomain(<redacted>)"
}

@JvmInline
internal value class ReaderLegacySourceLocalOpaqueToken internal constructor(
    val value: Long
) {
    init {
        require(value > 0L)
    }

    override fun toString(): String =
        "ReaderLegacySourceLocalOpaqueToken(<redacted>)"
}

internal data class ReaderLegacyPhysicalIdentity(
    val domain: ReaderLegacyPhysicalDomain,
    val source: ReaderLegacyInventorySource,
    val sourceLocalToken: ReaderLegacySourceLocalOpaqueToken
) {
    override fun toString(): String = "ReaderLegacyPhysicalIdentity(<redacted>)"
}

internal enum class ReaderLegacyResourceOrigin {
    Owned,
    Pending,
    Discovered
}

internal enum class ReaderLegacyResourceState {
    Reserved,
    Running,
    Registered,
    RendererOwned,
    Prepared,
    Visible,
    ReleaseRequested,
    Released
}

internal data class ReaderFrozenLegacyResource(
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

internal enum class ReaderLegacyInventorySource {
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

internal sealed interface ReaderLegacyResourceInventory {
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

@JvmInline
internal value class ReaderLegacyRestorationCheckpointId internal constructor(
    val value: Long
) {
    init {
        require(value > 0L)
    }

    override fun toString(): String =
        "ReaderLegacyRestorationCheckpointId(<redacted>)"
}

internal data class ReaderLegacySourceRestartHandle(
    val source: ReaderLegacyInventorySource,
    val opaqueId: Long
) {
    init {
        require(opaqueId > 0L)
    }

    override fun toString(): String =
        "ReaderLegacySourceRestartHandle(<redacted>)"
}

internal data class ReaderLegacyRestorationCheckpoint(
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
    init {
        require(routeGeneration > 0L)
        require(completedSources == ReaderLegacyInventorySource.entries.toSet())
        require(restartHandles.keys == completedSources)
        require(restartHandles.all { (source, handle) -> handle.source == source })
        require((initialOwner == ReaderPresentationFrameOwner.Neutral) ==
            (initialBinding == null && initialPhysicalIdentity == null &&
                initialResourceKind == null && initialProvenance == null))
        require(initialResourceKind == readerAdoptedResourceKindFor(initialOwner))
        require(initialProvenance == if (
            initialOwner == ReaderPresentationFrameOwner.Neutral
        ) null else ReaderTransitionResourceProvenance.AdoptedLegacy)
        require(initialPhysicalIdentity == null ||
            initialPhysicalIdentity.domain.freezeToken == freezeToken)
        require(readerInitialLeaseIsNoBroaderThan(physicalLease, requestedLease))
        if (initialOwner == ReaderPresentationFrameOwner.Neutral) {
            require(requestedLease == ReaderInitialPresentationInputLease.None ||
                requestedLease == ReaderInitialPresentationInputLease.ChromeOnly)
            require(physicalLease == ReaderInitialPresentationInputLease.None ||
                physicalLease == ReaderInitialPresentationInputLease.ChromeOnly)
        } else {
            require(readerInitialLeaseIsCompatibleWithOwner(
                owner = initialOwner,
                binding = requireNotNull(initialBinding),
                lease = requestedLease
            ))
            require(readerInitialLeaseIsCompatibleWithOwner(
                owner = initialOwner,
                binding = requireNotNull(initialBinding),
                lease = physicalLease
            ))
            if (initialOwner is ReaderPresentationFrameOwner.Curl) {
                require(requestedLease == ReaderInitialPresentationInputLease.None ||
                    requestedLease == ReaderInitialPresentationInputLease.ChromeOnly)
                require(physicalLease == ReaderInitialPresentationInputLease.None ||
                    physicalLease == ReaderInitialPresentationInputLease.ChromeOnly)
            }
        }
    }

    override fun toString(): String =
        "ReaderLegacyRestorationCheckpoint(<redacted>)"
}

internal sealed interface ReaderLegacyRestorationResult {
    data object Restored : ReaderLegacyRestorationResult
    data class Failed(
        val reason: ReaderTransitionFailureReason
    ) : ReaderLegacyRestorationResult
}

internal sealed interface ReaderLegacyCommitRestoredResult {
    data object Applied : ReaderLegacyCommitRestoredResult
    data class Rejected(
        val reason: ReaderTransitionFailureReason
    ) : ReaderLegacyCommitRestoredResult
}

internal interface ReaderLegacyFreezeAndInventoryPort {
    fun freeze(): ReaderLegacyFreezeToken
    fun checkpointBeforeDrain(
        token: ReaderLegacyFreezeToken
    ): ReaderLegacyRestorationCheckpoint?
    fun inventory(token: ReaderLegacyFreezeToken): ReaderLegacyResourceInventory
    fun drain(
        token: ReaderLegacyFreezeToken,
        physicalIdentity: ReaderLegacyPhysicalIdentity,
        onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
    ): ReaderPortCommandResult
    fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken): ReaderPortCommandResult
    fun restoreFromActivationCheckpoint(
        checkpoint: ReaderLegacyRestorationCheckpoint,
        source: ReaderLegacyInventorySource,
        onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
    ): ReaderPortCommandResult
    fun commitRestoredLegacy(
        checkpoint: ReaderLegacyRestorationCheckpoint
    ): ReaderLegacyCommitRestoredResult
}
```

`ReaderLegacyPhysicalIdentity` uses Kotlin data-class equality over all three composite
parts: positive reader-session plus exact freeze token domain, inventory source enum,
and positive source-local opaque token. Hashing is only for in-memory maps. Inventory
and late-discovery collections are bounded by each source's existing ownership capacity
and the 16-source fixed-point protocol; overflow or an unrepresentable identity blocks
activation rather than dropping a row. Raw session, freeze, local-token, imported-key,
and retirement-order values must never appear in logs, diagnostics, analytics,
screenshots, crash metadata, or persisted state; diagnostics may expose only bounded source/state enums, counts, and mismatch categories. Duplicate discovery
converges only when the entire identity is equal. Equal local values from different
sources or domains are distinct owners and must receive distinct drain, confirmation,
import, and release-once state.

`freeze()` is synchronous on Android main and must fence deck reserve;
raster/prewarm/repair/capture/hydration/publication/generation/persistence starts;
renderer/draw/WebView/Foliate/visual-state and pending-owner callback registration;
foreground passive/live acquisition and mutation; new pointer admission; and legacy
semantic dispatch before returning. `checkpointBeforeDrain` must succeed before any
release and capture the complete in-memory restart contract for all 16 inventory
sources. `inventory` is repeated until two consecutive complete snapshots with
successive `snapshotSequence` values have identical `discoveryVersion`, source set,
and resources after all drain callbacks settle. Every resource row
carries its complete physical identity, kind, optional exact binding/visible owner,
origin, state, and exact `AdoptedLegacy` provenance plus direct predecessor proof. Discoveries after freeze join the frozen inventory,
cannot be admitted, and must drain. Only equal complete composite identities converge;
equal local IDs from different sources/domains remain distinct. Incomplete inventory,
invalid rows, more than one visible predecessor, failed drain, or missing exact
confirmation prevents activation.

Source inspection makes aggregate `ownershipMetrics()`/`snapshot()` insufficient.
`ReaderPageTurnBundleSource` must assign complete source-qualified composite identities to snapshot-cache owners;
pending descriptor leases; descriptor requests/recipients; in-flight hydration
recipients/jobs and hydration-scheduler jobs; publication-ledger entries/callbacks/
retained bitmap values/capacity listener, publication-completion entries, and
publication-scheduler jobs; raster-persistence jobs;
raster-scheduler queued/pending/active work; bitmap-source presented/live capture
ownership and retained candidates; Handler runnables and presented-frame requests;
in-flight PixelCopy writes; evaluate-JavaScript/visual-state/draw callbacks;
live-validation capture/worker/final-fence handles; persistent-store active operations;
decoded cache/encode pins/pending decoded releases; and teardown work. Its
`ReaderPageTurnBitmapSource`, `ReaderPageTurnPresentedCaptureOwnership`,
`ReaderPageTurnLiveCaptureOwnership`, `ReaderPageRasterHydrationScheduler`,
`ReaderPageRasterPublicationScheduler`, `ReaderPageRasterPublicationLedger`,
`ReaderPageRasterScheduler`, `ReaderPagePendingCallbackOwners`, and
`ReaderPageTurnBundleTeardown` need synchronous freeze, exact snapshot, composite-
identity drain/confirmation, and checkpoint restoration adapters. `ReaderForegroundWebViewOwnership` must do
the same for its passive lease, `cancelAndRestore`, restoration callback/lease,
live/exclusive claims, readiness callbacks, retired-claim terminal delivery, and
current mutation claim. The existing
lifecycle-delivery registration/queue and every current local timeout registration are
exact sources too. The atomic installation designates one existing command-scoped fact-
only timer registration for each activated attempt and fences every competing physical
timer in the same serialized ownership change; deadline-requiring work can observe
neither zero nor two owners. The coordinator clock remains production-inactive until
Task 7.

### Truthful adopted predecessor

```kotlin
internal fun readerAdoptedResourceKindFor(
    owner: ReaderPresentationFrameOwner
): ReaderTransitionResourceKind? = when (owner) {
    ReaderPresentationFrameOwner.Neutral -> null
    is ReaderPresentationFrameOwner.NativePage,
    is ReaderPresentationFrameOwner.Curl -> ReaderTransitionResourceKind.Deck
    is ReaderPresentationFrameOwner.ShellCover,
    is ReaderPresentationFrameOwner.LiveEngine ->
        ReaderTransitionResourceKind.FrameHandoff
}

internal data class ReaderAdoptedPredecessorSeed(
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

Select at most one predecessor only from a directly reported `Visible` inventory row
whose exact owner and binding are physically confirmed. Zero rows creates
`ReaderInitialCommittedPresentationOrigin.Neutral` with exact session/epoch and leases
but no seed ID, visible owner, binding, resource, or physical identity; a shell cover is a
real predecessor and still carries its directly reported composite physical identity and
binding. Never infer the predecessor
from journal state, binding similarity, gestures, renderer generations, or
acknowledgements. Copy the selected row's exact owner, binding, resource kind, and
`ReaderLegacyPhysicalIdentity`. The required owner-kind relation is neutral → no
resource, native page/curl → `Deck`, and shell cover/live engine → `FrameHandoff`; a
mismatch blocks installation. For a selected predecessor, mint one seed ID at handoff,
then import the complete
identity under `AdoptedPredecessor(seedId)`. The import registry allocates a collision-
free coordinator key `opaqueId` rather than reusing the source-local token, and assigns
owner-independent `ReaderResourceRetirementOrder`. The immutable initial decision is
formed only after that import and physical-lease safety narrowing. Any selected curl
predecessor first fences and cancels every legacy in-flight claimed gesture through the
frozen legacy input source. Its adopted initial lease is identity-free and limited to
`None` or `ChromeOnly`; `ClaimedGesture(ReaderTransitionId, ...)` exists only for an
authentic coordinator transition and is structurally impossible in an initial origin.
Thus the order is
complete physical identity → selected row kind/binding/owner → seed ID → collision-safe
legacy import → retirement registration → adopted initial origin; no seed/key cycle or
binding-only inference exists. Neutral bypasses seed/import/retirement entirely. The seed
names non-neutral adoption, not legacy creation, and closes
`T5-EXACT-SEED` without fabricating a `ReaderTransitionId`.

If failure occurs before the first destructive `drain`, only an accepted complete
`cancelFreezeBeforeDrain` may expose the unchanged snapshot as `Legacy`; cancellation
rejection enters frozen `ActivationBlocked`. After any drain starts, direct rollback is forbidden: enter `RestoringLegacy`, keep both route
sets/egress closed, deny page input, retain the proven predecessor if valid, and keep
the release sink active. Invoke `restoreFromActivationCheckpoint` for all 16 checkpoint
sources under one 3-second activation-restoration deadline. Issuing all restoration
requests, or receiving only synchronous accepted returns, never cancels that deadline.
It remains active until every exact asynchronous source confirmation and the final
synchronous atomic `commitRestoredLegacy` result are observed. Only exact success
confirmations permit that no-callback/no-suspend transaction to publish the route table,
complete legacy lifecycle/deadline registrations, visible owner, and physical lease.
`ReaderLegacyCommitRestoredResult.Applied` enters `Legacy` and only then cancels the
deadline; `Rejected` guarantees no restoration snapshot write. Any failure,
missing confirmation, deadline expiry, or commit rejection enters `ActivationBlocked`
with diagnostic plus Retry/close chrome, no page input, no coordinator activation, and
no legacy claim. Retry may only repeat the in-memory checkpoint restoration; close
enters `ReleaseOnly`. This checkpoint is not SavedState or Task 7 wake policy.

### Active ports and one publication barrier

```kotlin
internal const val ReaderMaximumActiveSemanticCommandSlots = 8
internal const val ReaderMaximumPendingSemanticRequestHandles = 16
internal const val ReaderMaximumOutOfOrderSemanticSlotTombstones = 32

internal fun interface ReaderExecutableSemanticRequest {
    fun execute(
        onEvent: (ReaderPresentationEvent) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderSemanticRequestRegistry {
    fun register(
        request: ReaderExecutableSemanticRequest
    ): ReaderSemanticRequestHandle?
    fun take(
        handle: ReaderSemanticRequestHandle
    ): ReaderExecutableSemanticRequest?
    fun discard(handle: ReaderSemanticRequestHandle): Boolean
    fun pendingCount(): Int
}

internal data class ReaderReservedNeutralBootstrapRequest(
    val handle: ReaderSemanticRequestHandle,
    val readerSessionGeneration: Long
) {
    init {
        require(readerSessionGeneration > 0L)
    }

    override fun toString(): String =
        "ReaderReservedNeutralBootstrapRequest(<redacted>)"
}

internal data class ReaderSemanticCommandSlot(
    val id: ReaderSemanticCommandSlotId,
    val transitionId: ReaderTransitionId,
    val requestHandle: ReaderSemanticRequestHandle
)

internal data class ReaderSemanticCommandSlotFenceSnapshot(
    val contiguousRetiredThrough: Long,
    val outOfOrderRetiredSequences: Set<Long>,
    val activeSlotCount: Int
) {
    init {
        require(contiguousRetiredThrough >= 0L)
        require(outOfOrderRetiredSequences.size <=
            ReaderMaximumOutOfOrderSemanticSlotTombstones)
        require(outOfOrderRetiredSequences.all { it > contiguousRetiredThrough })
        require(activeSlotCount in 0..ReaderMaximumActiveSemanticCommandSlots)
    }
}

internal interface ReaderSemanticCommandSlotRegistry {
    fun reserve(
        transitionId: ReaderTransitionId,
        requestHandle: ReaderSemanticRequestHandle
    ): ReaderSemanticCommandSlot?
    fun consume(id: ReaderSemanticCommandSlotId): ReaderSemanticCommandSlot?
    fun retire(id: ReaderSemanticCommandSlotId): Boolean
    fun snapshot(): ReaderSemanticCommandSlotFenceSnapshot
}

internal sealed interface ReaderSemanticInvocationState {
    data object BeforeMutation : ReaderSemanticInvocationState
    data object InvokingWithoutCallback : ReaderSemanticInvocationState
    data class InvokingWithBufferedCallback(
        val latestAuthoritativeReceipt: ReaderPresentationEventReceipt,
        val callbackCount: ReaderSaturatingCallbackCount,
        val violationStatus: ReaderSemanticBufferedViolationStatus
    ) : ReaderSemanticInvocationState {
        fun record(
            receipt: ReaderPresentationEventReceipt
        ): InvokingWithBufferedCallback {
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

internal data class ReaderSemanticInvocationRecord(
    val id: ReaderSemanticInvocationId,
    val transitionId: ReaderTransitionId,
    val state: ReaderSemanticInvocationState
) {
    override fun toString(): String =
        "ReaderSemanticInvocationRecord(<redacted>)"
}

internal sealed interface ReaderSemanticCommandResult {
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

internal interface ReaderMaterialGenerationAllocationPort {
    fun allocate(
        command: ReaderTransitionCommand.AllocateMaterialBinding,
        onFact: (ReaderTransitionFact.MaterialBindingAllocated) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderActivatedGatewayPort {
    fun routeIntent(fact: ReaderTransitionFact.Intent): ReaderPortCommandResult
    fun routeReceipt(receipt: ReaderPresentationEventReceipt): ReaderPortCommandResult
    fun closeToReleaseOnly(
        trigger: ReaderReleaseOnlyCleanupTrigger
    ): ReaderPortCommandResult
}

internal interface ReaderActivatedRasterPreparationPort {
    fun prepare(
        command: ReaderTransitionCommand.RequestRasterPreparation,
        onFact: (ReaderTransitionFact) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderActivatedDeckPort {
    fun reserve(
        command: ReaderTransitionCommand.ReserveDeck,
        onFact: (ReaderTransitionFact) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderFramePresentationPort {
    fun prepareTarget(
        command: ReaderTransitionCommand.PrepareFrameTarget,
        onFact: (ReaderTransitionFact) -> Unit
    ): ReaderPortCommandResult

    fun present(
        command: ReaderTransitionCommand.RequestFramePresentation,
        onFact: (ReaderTransitionFact) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderInputLeasePort {
    fun narrowOrVetoInitial(
        lease: ReaderInitialPresentationInputLease
    ): ReaderInitialPresentationInputLease

    fun narrowOrVeto(
        lease: ReaderTransitionInputLease
    ): ReaderTransitionInputLease
}

internal interface ReaderTransitionResourcePort {
    fun release(
        command: ReaderTransitionCommand.ReleaseResource,
        onFact: (ReaderTransitionFact.ResourceReleased) -> Unit
    ): ReaderPortCommandResult

    fun releaseLegacy(
        dispatch: ReaderImportedLegacyReleaseDispatch,
        onConfirmed: (
            ReaderLegacyPhysicalIdentity,
            ReaderTransitionFact.ResourceReleased
        ) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderOwnedWorkCancellationPort {
    fun cancel(
        command: ReaderTransitionCommand.CancelOwnedWork,
        onApplied: (ReaderTransitionFact.OwnedWorkCancellationApplied) -> Unit,
        onRejected: (ReaderTransitionFact.OwnedWorkCancellationRejected) -> Unit
    ): ReaderPortCommandResult
}

internal interface ReaderReleaseOnlySinkPort {
    fun observe(fact: ReaderTransitionFact.ResourceObserved): ReaderPortCommandResult
    fun observeLegacy(
        imported: ReaderImportedLegacyResourceRegistration
    ): ReaderPortCommandResult
    fun confirm(fact: ReaderTransitionFact.ResourceReleased): ReaderPortCommandResult
    fun confirmLegacy(
        physicalIdentity: ReaderLegacyPhysicalIdentity,
        fact: ReaderTransitionFact.ResourceReleased
    ): ReaderPortCommandResult
    fun observeCleanupApplied(
        fact: ReaderTransitionFact.OwnedWorkCancellationApplied
    ): ReaderPortCommandResult
    fun observeCleanupRejected(
        fact: ReaderTransitionFact.OwnedWorkCancellationRejected
    ): ReaderPortCommandResult
    fun observeCleanupDeadlineElapsed(
        fact: ReaderTransitionFact.ReleaseOnlyCleanupDeadlineElapsed
    ): ReaderPortCommandResult
    fun observeReleaseRejected(
        fact: ReaderTransitionFact.ReleaseCommandRejected
    ): ReaderPortCommandResult
    fun observeReleaseThrew(
        fact: ReaderTransitionFact.ReleaseCommandThrew
    ): ReaderPortCommandResult
    fun observeReleasePortViolation(
        fact: ReaderTransitionFact.ReleasePortContractViolated
    ): ReaderPortCommandResult
    fun release(command: ReaderTransitionCommand.ReleaseResource): ReaderPortCommandResult
}

internal interface ReaderOwnerAndInputPublicationPort {
    // Both overloads use one synchronous Android-main-thread, no-callback, no-suspend
    // exact-result protocol. Rejected guarantees no owner/input snapshot write.
    fun publish(
        command: ReaderTransitionCommand.CommitOwnerAndInputLease
    ): ReaderOwnerAndInputPublicationResult

    fun publish(
        command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
    ): ReaderOwnerAndInputPublicationResult
}

internal data class ReaderImportedLegacyResourceRegistration(
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

internal data class ReaderImportedLegacyReleaseDispatch(
    val command: ReaderTransitionCommand.ReleaseResource,
    val imported: ReaderImportedLegacyResourceRegistration
) {
    init {
        require(command.identity.registration == imported.registration)
    }

    override fun toString(): String =
        "ReaderImportedLegacyReleaseDispatch(<redacted>)"
}

internal data class ReaderInitialActivationDecision(
    val origin: ReaderInitialCommittedPresentationOrigin,
    val adoptedSeed: ReaderAdoptedPredecessorSeed?,
    val adoptedResource: ReaderImportedLegacyResourceRegistration?,
    val neutralBootstrapReservation: ReaderReservedNeutralBootstrapRequest?
) {
    init {
        when (val initial = origin) {
            is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> {
                val seed = requireNotNull(adoptedSeed)
                val imported = requireNotNull(adoptedResource)
                require(initial.seedId == seed.id)
                require(initial.readerSessionGeneration == seed.readerSessionGeneration)
                require(initial.coordinatorEpoch == seed.coordinatorEpoch)
                require(initial.owner == seed.owner)
                require(initial.binding == seed.binding)
                require(initial.provenance == seed.provenance)
                require(imported.physicalIdentity == seed.physicalIdentity)
                require(initial.resource == imported.registration)
                require(imported.registration.key.ownerId ==
                    ReaderTransitionResourceOwnerId.AdoptedPredecessor(seed.id))
                require(imported.registration.key.kind == seed.resourceKind)
                require(imported.registration.retirementOrder.readerSessionGeneration ==
                    seed.readerSessionGeneration)
                require(imported.registration.retirementOrder.coordinatorEpoch ==
                    seed.coordinatorEpoch)
                require(neutralBootstrapReservation == null)
                require(readerInitialLeaseIsCompatibleWithOwner(
                    owner = initial.owner,
                    binding = initial.binding,
                    lease = initial.requestedLease
                ))
                require(readerInitialLeaseIsCompatibleWithOwner(
                    owner = initial.owner,
                    binding = initial.binding,
                    lease = initial.physicalLease
                ))
                if (initial.owner is ReaderPresentationFrameOwner.Curl) {
                    require(initial.requestedLease == ReaderInitialPresentationInputLease.None ||
                        initial.requestedLease == ReaderInitialPresentationInputLease.ChromeOnly)
                    require(initial.physicalLease == ReaderInitialPresentationInputLease.None ||
                        initial.physicalLease == ReaderInitialPresentationInputLease.ChromeOnly)
                }
            }
            is ReaderInitialCommittedPresentationOrigin.Neutral -> {
                require(adoptedSeed == null)
                require(adoptedResource == null)
                val reservation = requireNotNull(neutralBootstrapReservation)
                require(reservation.readerSessionGeneration ==
                    initial.readerSessionGeneration)
                require(initial.requestedLease == ReaderInitialPresentationInputLease.None ||
                    initial.requestedLease == ReaderInitialPresentationInputLease.ChromeOnly)
                require(initial.physicalLease == ReaderInitialPresentationInputLease.None ||
                    initial.physicalLease == ReaderInitialPresentationInputLease.ChromeOnly)
            }
        }
        require(readerInitialLeaseIsNoBroaderThan(
            origin.physicalLease,
            origin.requestedLease
        ))
    }

    override fun toString(): String =
        "ReaderInitialActivationDecision(<redacted>)"
}

@JvmInline
internal value class ReaderTask6FactOnlyTimerRegistrationId internal constructor(
    val value: Long
) {
    init {
        require(value > 0L)
    }
}

internal data class ReaderTask6FactOnlyTimerRegistration(
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

internal data class ReaderReleaseOnlyCleanupDeadlineRegistration(
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

internal data class ReaderTask6FactOnlyTimerTransferSnapshot(
    val registration: ReaderTask6FactOnlyTimerRegistration,
    val nextExpiresAtMillis: Long
) {
    init {
        require(nextExpiresAtMillis >= 0L)
        require(nextExpiresAtMillis <= registration.hardExpiresAtMillis)
    }
}

internal interface ReaderTask6FactOnlyTimerPort {
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

    fun cancel(
        registration: ReaderTask6FactOnlyTimerRegistration
    ): ReaderPortCommandResult

    fun cancelCleanupDeadline(
        registration: ReaderReleaseOnlyCleanupDeadlineRegistration
    ): ReaderPortCommandResult
}

internal enum class ReaderTask6LifecycleSafetyKind {
    VisibilityLost,
    HostDetached,
    WebViewDetached,
    RendererUnavailable,
    PublicationClosed
}

internal data class ReaderTask6LifecycleSafetyFact(
    val deliverySequence: Long,
    val kind: ReaderTask6LifecycleSafetyKind
) {
    init {
        require(deliverySequence > 0L)
    }
}

internal interface ReaderTask6LifecycleFactPort {
    fun enqueueSafetyFact(
        fact: ReaderTask6LifecycleSafetyFact
    ): ReaderPortCommandResult
}

internal sealed interface ReaderProductionActivatedPortCapability

// Constructor and capability minting live in KomikkuReaderNativeFrameHost.android.kt.
// No public Boolean/complete() factory exists. Every field is a real adapter capability;
// production no-op/rejecting implementations cannot produce this type.
internal class ReaderProductionActivatedSessionPorts private constructor(
    val gateway: ReaderActivatedGatewayPort,
    val semantic: ReaderSemanticCommandPort,
    val materialAllocation: ReaderMaterialGenerationAllocationPort,
    val raster: ReaderActivatedRasterPreparationPort,
    val deck: ReaderActivatedDeckPort,
    val frame: ReaderFramePresentationPort,
    val ownerAndInput: ReaderOwnerAndInputPublicationPort,
    val inputSafety: ReaderInputLeasePort,
    val resources: ReaderTransitionResourcePort,
    val ownedWorkCancellation: ReaderOwnedWorkCancellationPort,
    val releaseSink: ReaderReleaseOnlySinkPort,
    val lifecycleFacts: ReaderTask6LifecycleFactPort,
    val factOnlyTimer: ReaderTask6FactOnlyTimerPort,
    private val productionCapability: ReaderProductionActivatedPortCapability
)

// Unmistakably test-only fixtures are accepted only by a test harness overload and
// cannot be supplied to ReaderActivatedSessionInstallation.
internal data class ReaderTestActivatedSessionPorts(/* test doubles only */)

internal data class ReaderActivatedSessionInstallation(
    val ports: ReaderProductionActivatedSessionPorts,
    val initialDecision: ReaderInitialActivationDecision
) {
    override fun toString(): String =
        "ReaderActivatedSessionInstallation(<redacted>)"
}

internal data class ReaderActivatedSessionSnapshot(
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

    override fun toString(): String =
        "ReaderActivatedSessionSnapshot(<redacted>)"
}

internal sealed interface ReaderActivationInstallResult {
    data object Installed : ReaderActivationInstallResult
    data class Rejected(
        val reason: ReaderTransitionFailureReason
    ) : ReaderActivationInstallResult
}

internal interface ReaderSessionActivationCoordinator {
    val state: ReaderSessionActivationState
    fun installActivatedSession(
        installation: ReaderActivatedSessionInstallation
    ): ReaderActivationInstallResult
    fun closeToReleaseOnly(
        trigger: ReaderReleaseOnlyCleanupTrigger
    ): ReaderPortCommandResult
}
```

The one Android-main-thread `installActivatedSession` barrier accepts only
`ReaderProductionActivatedSessionPorts` minted by the production
`KomikkuReaderNativeFrameHost` composition root after authenticating every real adapter
capability. Package completeness is construction/type authority, not a Boolean,
production-visible `complete()`, or a generic factory that can fill fields with no-op or
always-rejecting ports. Test fixtures use a distinct type that the production install
signature cannot accept. Successful construction also audits that no installed
`Shadow`/`LegacyOnly` route and no legacy semantic/material/frame/input consequence
writer remains reachable.

The barrier side-effect-freely validates every route plus the complete adopted/neutral
initial origin, its optional non-neutral seed/import, breadth ordering, and independent
requested-plus-physical lease compatibility with the exact adopted owner/binding; neutral
must carry lawful no-owner leases. It also requires activation state `ReadyToCommit`, an
empty pre-install release-sink cleanup slot, and validates adopted reservation absence or the
neutral decision's exact pre-reserved session-matching bootstrap capability. Callers cannot supply a journal.
After validating the origin, the barrier alone derives
`ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(origin),
releaseOnlyCleanup = null, lastTransitionSequence = 0L,
lastIssuedTransitionIdentity = null)`. The input
adapter has already produced both leases, proved `physicalLease` is no broader, and
validated each lease against the owner/binding or neutral no-owner rules. In one non-callback, non-suspending commit, the barrier constructs
one immutable `ReaderActivatedSessionSnapshot` and replaces the composition root's
single installation-snapshot reference. Gateway, semantic/material/frame/resource/owned-
work-cancellation ports, retained fact-only timer route, optional visible-owner/binding/resource projection,
physical input dispatch, deck-writer selection, reserved-neutral-bootstrap capability,
initial journal, activation-state reads,
and command-egress checks all dereference that same
snapshot; none has a separate mutable install field. The snapshot therefore switches
every route; installs `ReaderCommittedPresentation.Initial(origin)` and the exact pre-
reserved neutral bootstrap capability when applicable; publishes the
origin's optional initial visible presentation and exact physical input lease; marks
`Activated`; and exposes command egress
as one write. There is no
`publishInitial`, `openCommandEgress`, or mutable per-port setter. Observers see only a
complete legacy snapshot or a complete activated snapshot with its typed baseline and
initial publication. For neutral, all visible-presentation fields are absent. Its private
current-Foliate-destination request was registered synchronously before `ReadyToCommit`;
a null `register()` result never calls the barrier or switches routes/egress and follows
exact cleanup plus existing unfreeze/restoration/blocking. After successful install, and
never inside the barrier, the gateway reads that exact reserved handle from the snapshot
and enqueues `ReaderBootstrapNativePageIntent` without a second/nullable registration
branch. The existing
`BootstrapNativePage` operation receives sequence `1`, no parent, and
`FoliateAuthoritativeInitial(1)`; only its command-bound live-Foliate destination receipt
supplies the exact binding before material/resource/frame allocation. No synthetic binding
or twelfth operation is permitted. Installation rejection discards an unconsumed reserved
handle exactly once before restoration/unfreeze and releases every other activation-owned
registration exactly once. Close before bootstrap consumption uses the same discard path;
successful sequence-1 `take` consumes the handle and leaves a bounded retirement tombstone,
so close cannot execute or discard it twice.
The production `ReaderTransitionClock` scheduling port remains inactive in Task 6.
The installed timer route retains exactly one existing command-scoped physical timer
per activated attempt. For narrowing operations it binds no attempt timer while retained
publication is pending; exact `Applied(Retained)` returns the operation to pre-work, then
it binds the exact `ReaderTransitionId` before the first timer-requiring physical command.
Its callback can only enqueue that ID's typed `DeadlineExpired` through
the coordinator FIFO. It cannot mutate presentation/input/release, perform Retry, or
extend/rearm except matching progress explicitly permitted by that same immutable
command registration. `snapshotForTask7Transfer` returns that registration plus its
current exact next expiry, bounded by hard expiry; it does not transfer ownership or arm
the coordinator clock by itself. For initial activation, no deadline-requiring physical
work starts until one bound registration ID is accepted as owner. Supersession uses the
existing gated-successor swap. Close/replacement prepares a two-second cleanup registration
from that same retained Task 6 physical timer source, keyed by the fresh redacted cleanup ID
and generation, while its callback remains admission-gated. The atomic release-only commit
installs the cleanup record with `Armed`, replaces/fences any transition-timer owner, and
activates only exact `ReleaseOnlyCleanupDeadlineElapsed(cleanupKey)` admission; physical
predecessor cancellation follows. No Task 7 coordinator-clock scheduling participates.

Null/rejected/throwing preparation cannot prevent close: the commit enters permanent
`ReleaseOnly`, records `BindingRejected`/`BindingThrew` plus nonretryable
`CloseDrainTimeout`, and terminalizes unresolved release accounting fail-closed. An exact
elapsed fact records `Elapsed`, the same timeout, and unresolved release timeout state.
Stale ID/generation, duplicate, and post-completion delivery are inert. The cleanup deadline
is logically fenced and physically cancelled once only when cancellation is terminal and the
release ledger is `EmptyReleased` or `TerminalFailure`; accepted cancellation records
`CancelledAfterTerminalAccounting`, rejected records `CancellationRejected`, and throw records
`CancellationThrew`. None admits later delivery or reopens/falls back. Process close fences/cancels an armed cleanup deadline, records `ProcessClosed`, terminalizes
unresolved cleanup/release state without persistence, and creates no wake or recreation.
Thus physical callback races create no logical zero- or two-owner interval.

The exact activation order is: construct inactive coordinator/adapters; verify all
ports and prerequisites, including an inactive production coordinator clock and the
retained fact-only timer route; freeze; obtain the first complete collision-safe
inventory after all source fences and capture its pre-drain checkpoint; maintain
versioned inventory; select at most one directly proven predecessor; begin destructive
drain of every other complete physical identity with matching confirmations to the
fixed point; when present, carry the selected row's exact identity, owner, resource kind,
binding, and provenance through a non-neutral seed, collision-free imported key, owner-
independent retirement order, and `AdoptedPredecessor` origin; otherwise create `Neutral`
with exact session/epoch and no seed/owner/binding/resource; fence and cancel every legacy
claimed gesture before curl adoption; compute breadth-ordered identity-free requested and
physical leases and validate both against the exact adopted owner/binding or neutral no-
owner rules. For neutral, synchronously register the private current-Foliate-destination
request while routes/egress remain closed; exact non-null reservation is required for
`ReadyToCommit`. Null registration performs exact-once activation-state cleanup and follows
pre-drain unfreeze or post-drain restoration/blocking without calling install. Then call
`installActivatedSession` once with only the complete production ports and validated initial
decision. The barrier revalidates both leases and reservation ownership, derives the
sequence-zero/no-parent baseline journal internally, and publishes journal plus the exact
reservation in the one atomic snapshot; no caller constructs or supplies that journal.
Only after successful return may neutral enqueue its bootstrap intent with that exact
reserved handle and no registration call. For a narrowing operation, the
first activated attempt leaves its retained timer unbound until exact
`Applied(Retained)` returns to pre-work, then binds it before the first timer-requiring
physical command; timer designation/fencing is serialized so no deadline-requiring work
sees zero or two owners. Failure before the
first drain reaches `Legacy` only after accepted complete unfreeze;
a failed unfreeze enters `ActivationBlocked`. Failure after drain starts enters
`RestoringLegacy` and may return to `Legacy`
only after every source confirms and `commitRestoredLegacy` atomically republishes a
complete legacy route/owner/lease/deadline/lifecycle snapshot. Otherwise it enters
`ActivationBlocked`. After successful install, state can only progress from
`Activated` to permanent `ReleaseOnly`; failures remain coordinator-owned and fail
closed without fallback. Close/replacement calls `closeToReleaseOnly(trigger)` to atomically
capture any exact pre-close active cancellation target in the bounded cleanup record, clear
active/pending stage state, close ingress, and enter `ReleaseOnly` before returning at most
one keyed `CancelOwnedWork` command.

The permanent release sink is created before freeze and is reachable through the
activation coordinator during drain, restoration, and blocked states; it is not a
production semantic/presentation consequence route. Before installation, when no journal
exists, that sink owns the sole zero-or-one cleanup slot for pre-activation close. After
installation, the journal's `releaseOnlyCleanup` slot is canonical. Activation state makes
those authorities mutually exclusive; install requires the pre-install slot empty, and close
never fabricates a journal merely to hold cleanup. Atomic installation binds the
activated gateway to that same sink, and close narrows the session to it. The sink accepts only
attempt-keyed resource observation/confirmation, cleanup-grouped `ReleaseResource`, cancellation
applied/rejected matching the sole pending cleanup key plus captured transition ID, cleanup
deadline elapsed matching the sole cleanup ID plus generation, and release rejection/throw/port-
violation facts matching mandatory attempt ID plus exact registration for an existing row grouped
or transferred into that cleanup. It rejects semantic, material, deck, frame, input,
presentation, ordinary timer, Retry, restoration, and transition consequences, including
after `CloseDrainTimeout`. Matching cancellation consumes `Pending` once; matching elapsed
consumes `Armed` once; matching release result advances only the corresponding attempt-ledger
row. Duplicate/stale/wrong-key/wrong-ID/wrong-generation/wrong-attempt/wrong-registration outcomes, later
replacement, and repeated/process close cannot overwrite the record or emit work twice.

The release ledger registers a `ReaderTransitionResourceRegistration` exactly once per
coordinator physical identity and a `ReaderImportedLegacyResourceRegistration` exactly
once per complete legacy composite identity, allocating monotonically increasing
`ReaderResourceRetirementOrder` independently of transition/resource owner. Equal local IDs
under different sources/domains import independently; only a duplicate complete identity
reuses a registration. `requestRelease` installs one opaque attempt → registration row and
returns a command carrying mandatory `ReaderPhysicalReleaseAttemptId`; cleanup grouping is
optional. Ordinary successor/terminal attempts later transfer into cleanup unchanged and
without reissue. Generic and legacy adapters buffer only latest exact confirmation plus
saturating count/violation status until result return. Callback evidence always wins over
Rejected/throw, late callback promotes that attempt to `Released`, and duplicate/3+ callbacks
remain idempotent, bounded, non-throwing diagnostics. Active attempt rows outrank bounded
tombstones; overflow blocks admission. Adopted resources use
`ReaderResourceReleaseIssuer.Session`; they never borrow an active transition. Legacy
confirmation additionally matches complete physical identity. The private retirement fence
stores bounded raw order only internally and exposes fixed redacted rendering plus a sanitized
count/category projection; raw sequences never enter assertion output.

### Command-bound receipts and material/frame causality

Every `ReaderSemanticSynchronizationIntent`, including neutral bootstrap, page turn,
cover entry, and TOC/search/bookmark/annotation/jump, carries a content-free
`ReaderSemanticRequestHandle`. A bounded session registry (maximum 16 pending handles)
resolves it exactly once to `ReaderExecutableSemanticRequest`, an in-memory closure
that privately retains the actual Foliate action/destination. Neutral bootstrap alone
reserves this handle synchronously before `ReadyToCommit`, transfers it through the atomic
snapshot, and consumes it after sequence-1 admission; post-install bootstrap performs no
nullable `register()`. Common state,
persistence, diagnostics, and logs never contain or reconstruct hrefs, CFIs,
selections, annotation payloads, or other destination content. Missing, duplicate,
expired, superseded, or wrong-session handles reject before any mutation or legacy
dispatch.

Before emitting `RequestSemanticSynchronization`, the reducer records the sole
`SemanticSynchronization` pending stage. `ReaderSemanticCommandPort.synchronize` then
performs handle lookup/take, registry validation, dedicated slot reservation, and executable
invocation as private synchronous internals; none is a reducer stage. Thus, before taking
and invoking that executable request,
`ReaderSemanticCommandPort.synchronize` reserves a dedicated slot containing exact
slot ID, transition ID, and request handle. Each executable receives a callback closure
capturing only its own slot and a fresh redacted `ReaderSemanticInvocationId`; there is no
shared next-callback slot and unsolicited Foliate delivery uses a separate facts port. At
most eight slots are active. The production adapter installs `BeforeMutation`, performs all
rejectable validation there, changes to `InvokingWithoutCallback` immediately before the
first possible Foliate mutation, and does not expose a synchronous callback to the coordinator
while `synchronize` remains on the stack. The first callback creates
`InvokingWithBufferedCallback(latestAuthoritativeReceipt, One, None)`. Every callback thereafter
uses total non-throwing `record`: replace `latestAuthoritativeReceipt`, advance the saturating
count `One → Two → ThreeOrMore → ThreeOrMore`, and mark `DuplicateCallback`. No callback
allocates or grows a collection, validates a size with `require`, invokes coordinator code, or
throws. Differing receipts deliberately replace earlier evidence because the latest callback is
the final authoritative semantic observation. FIFO publication waits until result return.

The result/callback matrix is exhaustive:

| Observed order | Adapter reduction and FIFO output |
|---|---|
| `Accepted` with exactly one buffered callback | Change to `ReceiptQueued`, then enqueue the latest exact receipt once. |
| `Accepted` with two or 3+ buffered callbacks | Enqueue only `latestAuthoritativeReceipt` once, then one bounded `SemanticPortContractViolated(DuplicateCallback)` carrying the saturated count; fail-visible close follows. |
| `Accepted` without a callback | Change to `AwaitingAsynchronousCallback`; the first later callback queues once. If no callback arrives, the existing exact operation deadline emits `DeadlineExpired`; no new timer exists. |
| `Rejected(reason)` in `BeforeMutation` | Guarantee no Foliate mutation and no callback, retain `RejectedWithoutCallback`, and enqueue exact `CommandRejected(transitionId, SemanticSynchronization, reason)`. |
| Throw in `BeforeMutation` | Guarantee no mutation/callback, retain the same tombstone, and enqueue exact `CommandRejected(..., CommandThrew)`. |
| One callback followed by `Rejected` or throw | Never enqueue normal rejection. Enqueue the latest exact receipt once, then one exact `SemanticPortContractViolated(CallbackThenRejected/CallbackThenThrew)`. |
| Two or 3+ callbacks followed by `Rejected` or throw | Enqueue only the latest exact receipt once, then one bounded combined `DuplicateCallbackThenRejected`/`DuplicateCallbackThenThrew` violation with saturated count. |
| Callback after `RejectedWithoutCallback` | Enqueue the late exact receipt as authority, then one `SemanticPortContractViolated(RejectedThenLateCallback)`. |
| Additional callback after return/fatal marking | Replace retained latest authority and saturate metadata without throwing or collection growth; one coalesced adapter drain queues the latest receipt once and does not duplicate the already-recorded fatal violation. |
| `Rejected` or throw after `InvokingWithoutCallback` began without callback | Queue bounded `RejectedAfterMutationStarted`/`ThrowAfterMutationStarted` violation instead of normal rejection. |

On every exact contract-violation fact, the session-level semantic gate atomically refuses all
later semantic commands, preserves already queued receipts and their authoritative controller
reductions, retires the slot/handle once, and invokes the same fail-visible
`closeToReleaseOnly(PublicationClose)` transaction. The safety fact is admitted by exact
session/epoch/invocation tombstone even if the receipt already cleared
`SemanticSynchronization` or the active transition advanced; it never clears, replaces, or
outvotes a receipt. There is no legacy fallback. Repeated violation facts are inert after the
semantic gate closes. A normal accepted callback, pre-mutation rejection/throw,
supersession, deadline, publication replacement, or close retires both slot and handle.
The slot/invocation fence retains one contiguous retired-through sequence and at most 32
out-of-order redacted tombstones; active slots win, and overflow rejects new commands
fail-closed rather than growing or evicting live causality. The receipt
and common APIs become:

```kotlin
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

Only a consumed `ReaderPresentationEventOrigin.SemanticCommand` slot supplies
`originatingTransitionId`; `NonSemantic` cannot produce a semantic receipt, and
unsolicited relocation uses `UnsolicitedFoliate`, consumes no slot, and remains
authoritative under Task 5 arbitration. A callback before command return is buffered by the
invocation state machine and flushed only after `Accepted`; it never reduces recursively.
Wrong-slot, stale, or untagged settlement receipts do not satisfy proof. Duplicate callbacks replace
one fixed latest-authority field, saturate bounded metadata, and enter the fatal path that queues
only the final receipt once plus one contract violation.

After semantic proof, `AllocateMaterialBinding` allocates fresh monotonic preparation,
raster, and texture generations. `MaterialBindingAllocated` is admitted only after
exact transition, Foliate-session, publication-generation, viewport/profile, and
semantic-binding verification. `RequestRasterPreparation` and `ReserveDeck` carry
that allocation and reject before physical work if any exact field differs.

For every operation that narrows or revokes an existing predecessor's input, acceptance
first publishes an
`AwaitingProof` retained-publication contract awaiting exactly
`OwnerAndInputPublicationAcknowledgement`, admits only `RetainedPublication`, with
`OwnerAndInputPublicationApplied`, `OwnerAndInputPublicationRejected`, and
`CommandRejected` as callback sources, and emits only
`PublishRetainedOwnerAndInputLease`. Before exact `Applied(Retained)`, the coordinator
may issue no semantic, allocation, raster, deck, frame-target, frame-presentation, or
other timer-requiring physical command and binds no attempt timer for that work.
`Applied(Retained)` records the narrowed lease and returns the same active operation to
its appropriate pre-work `Accepted`/`AwaitingPrerequisites` phase; it is not successor
success. `Rejected(Retained)` publishes terminal failure before any successor work. A
neutral baseline has no retained owner/binding/resource subject, so this gate is
unrepresentable and skipped; its first timer binds before its first semantic command.

After later semantic/material/deck prerequisites, the coordinator—not an adapter—selects
one exact registration. Native/curl select the already admitted `Deck`; shell/live
allocate a transition-owned `FrameHandoff` through the coordinator release ledger. The
journal publishes an `AwaitingProof` target phase awaiting exactly
`FrameTargetPreparation`, admits only `FrameTargetPreparation`, with
`FrameTargetPrepared`, `FrameTargetPreparationRejected`, and `CommandRejected` as callback
sources, and emits
`PrepareFrameTarget(transitionId, specification, registration)`. The adapter binds its
physical target to that supplied registration and returns `FrameTargetPrepared` through
the FIFO; it cannot allocate/register ownership or retirement order. A synchronous
prepare rejection is converted immediately to `FrameTargetPreparationRejected` and
queued while `advancing`, never reduced recursively. Only a matching fact stores `frameTarget`, consumes `FrameTargetPreparation`, publishes the subsequent
presentation-command phase, and permits `RequestFramePresentation(target)`.
Preparation rejection, supersession, or close retires any returned handle and releases
the exact registration once; stale/wrong target facts are inert except for ledger-owned
release.

The target and specification are sealed by kind. Shell carries a typed host token,
cover/publication/viewport generation, exact binding/profile geometry, positive request
sequence, and coordinator-allocated `FrameHandoff`. Native carries exact material
allocation, explicit `Present(token)` or `AuthoritativeAbsent`, exact PlayLikeCurl deck
identity, geometry, positive request sequence, and the admitted `Deck`. Curl carries
exact allocation, gesture plus settlement, exact PlayLikeCurl deck identity, geometry,
positive request sequence, and admitted `Deck`. Live carries typed handoff token,
direction, claim identity, publication/viewport/profile geometry, positive request
sequence, and coordinator-allocated `FrameHandoff`. There is no pre-command
`frameSequence`; callback-produced presented-frame sequence exists only in
`PreparedFrame.frameOwner` proof evidence.

Target admission validates exact transition/session/publication/binding, kind identity,
resource owner/kind, geometry generations, session/publication-scoped handle, positive
request sequence, and legal token presence/authoritative absence. The bridge, native
publisher, and curl adapter cannot poll `currentCandidate`/current decision, select a
same-binding replacement, infer/fabricate a token or resource, replace registration, or
derive retirement order. `PreparedFrame` echoes the same target handle and registration;
same binding with another target is stale. Curl may settle to stable native ownership
without a twelfth operation only while preserving exact gesture/settlement/deck identity.

Consuming a matching `PreparedFrame` removes `PreparedFrame` from awaited proofs and
publishes successor `Committing` whose contract awaits exactly
`OwnerAndInputPublicationAcknowledgement`, admits `SuccessorPublication`, and names
`OwnerAndInputPublicationApplied`/`OwnerAndInputPublicationRejected` as its exact protocol
callbacks plus `CommandRejected` as typed command-stage ingress. It retains active transition, predecessor owner/input truth, timer, target, and
successor resource and emits only `CommitOwnerAndInputLease`; it cannot publish success,
cancel timer, or release predecessor. The synchronous no-callback/no-suspend port either
atomically replaces the immutable snapshot or returns
`Rejected(AtomicPublicationRejected)`/documented narrower reason with a no-write
guarantee.

The dispatcher converts the result immediately to the exact acknowledgement fact and
appends it to the FIFO while `advancing`; no recursive reduction or Unit/Boolean/
accepted-only/callback acknowledgement path exists. Only exact `Applied(Successor)` may
publish success/committed before timer cancellation and predecessor release. Rejected
successor publishes failure first, retains predecessor truth, and releases successor
only. Wrong/stale/duplicate/cross-transition/cross-resource/untagged acknowledgement is
inert and cannot release predecessor. Initial installation remains the distinct one-write
routes/owner/input/`Activated`/egress transaction with no acknowledgement gap.

## Migration ledger

Every row is a Task384 implementation and audit obligation.

| Legacy writer | Package | Replacement fact → command | Deadline/proof/outcome | Release/wake and deletion check |
|---|---:|---|---|---|
| `ReaderPresentationAuthority.kt:readerPresentationReduce` | 1, 6, 10 | normalized facts → operation commands; exact-target prepared frame → successor `Committing` retaining predecessor truth + `CommitOwnerAndInputLease`; exact queued applied/rejected acknowledgement → terminal state | only matching applied acknowledgement succeeds; terminal publication precedes release | coordinator ledger; no legacy effect dispatch |
| `ReaderTransitionGateway` Shadow route followed by legacy dispatch | 2, 5, 6, 10 | neutral pre-reserves private bootstrap handle before ReadyToCommit; Task 6 `installActivatedSession` accepts only exact `ReaderProductionActivatedSessionPorts` plus validated initial decision, atomically switches every consequence route, derives the sequence-zero/no-parent journal, and installs the typed adopted/neutral baseline plus exact reservation when neutral, optional initial owner/resource, and exact identity-free physical lease with egress in the same commit | null registration performs no install/switch and restores or blocks; complete installation snapshot has no caller-supplied journal, fabricated operation/parent, or reachable Shadow/LegacyOnly/no-op route; post-install fail closed | consume/discard reserved handle exactly once; close/replacement atomically captures a bounded release-only cleanup record before one keyed cancellation; no installed-but-unpublished state or active-session fallback |
| `ReaderPresentationControllerReducer.onPresentationEvent/onViewerAction` and receipt construction | 5, 6, 10 | opaque executable handle + dedicated bounded command slot + explicit origin → exact tagged receipt or separate authoritative unsolicited relocation | exact handle/slot identity plus semantic/frame proof | retire handle/slot on all terminal paths; no inferred origin or Android consequence in controller |
| `ReaderPresentationBindingReporter.update/reserve/classifyReceipt/commitReceipt` | 2, 3, 6, 10 | frozen inventory → typed baseline/registration; every ordinary or cleanup release allocates mandatory opaque attempt ID before generic/imported dispatch; fixed adapter buffers latest confirmation plus saturating metadata | Accepted/rejected/throw × zero/one/two/3+ callback matrix; callback evidence wins, late callback promotes, attempt/resource/legacy identity must match | unresolved ordinary attempts attach to cleanup without reissue; active attempt precedence, bounded tombstones, sanitized retirement-fence projection; replica deleted |
| Receipt dispatcher and host effect handlers | 2, 6, 10 | reducer publishes singleton stage; semantic adapter stores only latest receipt + saturating count/fatal status until result, then latest once + at most one violation; release adapters use analogous attempt-keyed latest confirmation state; close attaches unresolved attempts and installs cleanup timer | all callback paths are bounded/non-throwing; Accepted/Rejected/throw and late/duplicate/3+ matrices preserve latest physical/semantic authority; exact attempt/resource/cleanup matching | retire handles/slots once; no receipt/confirmation list; sink admits attempt-keyed release violation/failure/confirmation; delete duplicate queues |
| Lifecycle delivery and retry queue | 6, 7, 10 | Task 6 keeps existing normalization as sole ordered ingress but suppresses overlapping legacy consequences and emits safety facts only; Task 7 migrates normalization/recovery | no Task 6 restore/reflow/recovery/wake policy; Task 7 lifecycle matrix | exact cancellation; compatibility writer deleted after Task 7 |
| Host bridge transition starters | 6, 10 | coordinator-selected registration + exact kind specification → `PrepareFrameTarget` → FIFO `FrameTargetPrepared` → `RequestFramePresentation` → target-echoing `PreparedFrame`/failure | awaiting-target proof precedes presentation; adapter cannot allocate/register ownership; no binding lookup, token fabrication, autonomous commit, or local deadline | reject/supersede/close retire handle and release exact registration once; starters deleted |
| Native page publisher | 6, 10 | coordinator supplies exact admitted PlayLikeCurl `Deck` + native specification → prepared target → presentation proof | no `currentCandidate` polling/same-binding selection; target preparation then exact prepared proof; neither is success | frame/callback inventory and ledger; autonomous `update` deleted |
| Presentation and relocation timeout owners | 6, 7, 10 | Task 6 selects one retained fact-only timer owner per attempt; after required `Applied(Retained)`, pending TimerBinding precedes exact transition bind; release-only atomically replaces/fences it with one two-second cleanup deadline keyed by cleanup ID/generation; Task 7 transfers/deletes coordinator-clock attempt scheduling only | ordinary rejection requires active ID + pending TimerBinding; cleanup admits only exact `ReleaseOnlyCleanupDeadlineElapsed`; cancellation waits for terminal cancellation plus `EmptyReleased`/`TerminalFailure`; no zero/two-owner interval | cancel/fence exact registration on terminal/process close with no Task 6 wake/recreation; delete retained schedulers only in Task 7 |
| Native viewer container direct presentation writers | 2, 6, 7, 10 | production composition root plus sole publication port; `Applied(Retained)` gates all successor work and exact queued `Applied(Successor)` is the sole success gate | retained-publication waiting → pre-work; target preparation → presentation → successor `Committing` retaining predecessor truth; neither commit command nor `PreparedFrame` succeeds | ledger only; direct writers/Shadow/LegacyOnly/no-op packages unreachable |
| Input settlement host controller | 6, 7, 10 | operation acceptance → retained-owner publication only; exact `Applied(Retained)` → pre-work; final successor publication uses same protocol | zero semantic/material/raster/deck/target/frame/timer work before retained Applied; rejected retained terminates; no `ApplyInputLease` or local grant/broadening | ordered cancellation only; local mutation removed |
| Raster preparation controller | 4, 6, 7, 10 | semantic proof → allocation → allocation-scoped raster facts | exact fresh generations before physical work; frame proof still required | exhaustive frozen inventory; Task 7 owns deferral/wake policy |
| `ReaderPageTurnBundleSource`/`ReaderPageTurnBitmapSource` plus hydration/publication/generation schedulers, publication ledger, pending-callback and capture ownership, cache/store, live validations, and teardown | 6, 10 | synchronous source freeze → collision-safe composite-identity repeated snapshot → exact drain or checkpoint restoration | all subordinate bitmap/callback/job/store owners reach fixed point; counts never substitute | owner-independent registrations and bounded retirement fence; direct hidden ownership fails source audit |
| `ReaderForegroundWebViewOwnership` passive/restoration/live-claim state | 6, 10 | freeze acquisition/mutation → exact lease/claim/callback snapshot → settle/drain or checkpoint restore | no mutation crosses cutover; restoration callback terminal before fixed point | exact release/restoration; no callback publication or reopen before complete commit |
| Deferred raster retry coordinator | 7, 10 | typed deferral → persist/consume/cancel | 15 minutes or one restoration | exact finite wake; file deleted |
| Deck admission and lease host | 3, 4, 6, 10 | exhaustive inventory plus `MaterialBindingAllocated` → allocated reserve/build facts | complete drain and exact allocation/ownership proof | one composite physical identity/imported key; host currency deleted |
| PlayLikeCurl Foliate controller local transition writers | 4, 5, 6, 7, 10 | freeze fences/cancels legacy claimed gestures; initial curl adoption uses identity-free None/ChromeOnly; after `Applied(Retained)`, semantic/material/deck work → facts; coordinator supplies admitted Deck + exact curl specification to `PrepareFrameTarget`; adapter returns target/proof only | target preparation and `PreparedFrame` lead to successor `Committing` retaining predecessor truth; only exact queued `Applied(Successor)` succeeds, never commit command or frame proof | ledger only; Task 7 owns recovery policy |
| Deck recovery coordinator | 7, 10 | repair/deck/capacity → Task 7 recovery transition | 10/30 seconds | submitted/unsubmitted ledger; file deleted |
| Process state and ViewModel | 8, 10 | `Restored` → consume/request fresh facts | 15 minutes/one use | no coordinator fields in UI snapshot |
| Compose root/screen/platform callbacks | 2, 5, 6, 7, 10 | intent/receipt → installed gateway; immutable decision → render | no Compose proof/deadline | no release ownership or direct Retry/effect plumbing |

---

### Task 1: Define the pure transition model and liveness table

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionModelTest.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionLivenessTest.kt`
- Verify only: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`
- Modify: `docs/superpowers/plans/2026-09-13-reader-resumable-transition-coordinator.md`

- [ ] **Step 1: Write grouped RED tests**

```kotlin
@Test
fun everyNonTerminalPhaseNamesAllSevenLivenessFields() {
    operationFixtures().forEach { fixture ->
        fixture.nonTerminalPhases.forEach { phase ->
            assertTrue(phase.contract.awaitedProofs.isNotEmpty())
            assertTrue(
                fixture.id == phase.contract.deadlineOwner,
                "phase deadline owner identity mismatch"
            )
            assertNotNull(phase.contract.supersession)
            assertNotNull(phase.contract.retainedOwner)
            assertNotNull(phase.contract.inputLease)
            assertTrue(phase.contract.callbackSources.isNotEmpty())
            assertTrue(
                phase.contract.admissibleCommandStages.isEmpty() ||
                    ReaderTransitionFactKind.CommandRejected in
                        phase.contract.callbackSources
            )
        }
    }
}

@Test
fun matchingSettlementIsConsumedOnceBeforeConsequences() {
    val fixture = journalAwaitingSettlement()
    val fact = matchingSettlementFact(fixture)
    val first = fixture.journal.reduce(fact)
    assertTrue(
        fixture.id == first.state.active?.consumedSettlement?.transitionId,
        "consumed settlement transition identity mismatch"
    )
    assertTrue(first.commands.none { it is ReaderTransitionCommand.CommitOwnerAndInputLease })
    val duplicate = first.state.reduce(fact)
    assertTrue(duplicate.commands.isEmpty())
    assertTrue(
        first.state == duplicate.state,
        "duplicate settlement must preserve reducer state"
    )
}

@Test
fun untaggedDestinationCreatesExternalRelocationAndRevokesPageInput() {
    val result = journalAwaitingSettlement().reduce(
        ReaderTransitionFact.FoliateDestinationCommitted(
            transitionId = null,
            binding = tocSuccessorBinding()
        )
    )
    assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, result.state.active!!.id.operation)
    assertEquals(ReaderTransitionInputLease.ChromeOnly, result.state.active!!.phase.contract.inputLease)
}
```

Assert the exact defaults: material operations use 10-second no-progress and
30-second hard expiry; cover/live/native commits use 2 seconds; settlement uses
5 seconds; close drain uses 2 seconds; Retry is never automatic; all deferral wakes
belong to `ReaderTransitionWakeKind`.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.reader.ReaderResumableTransitionModelTest" \
  --tests "paige.navic.reader.ReaderResumableTransitionLivenessTest"
```

Expected: compilation fails because the transition model does not exist.

- [ ] **Step 3: Implement the shared contract and table-driven pure journal**

Use the shared signatures above. `Deferred` removes the active physical attempt and
retains only `ReaderTransitionResumeRecord`. External relocation revokes page input,
retains the predecessor as a noninteractive shield, demands fresh raster/deck/frame
proof, and releases the predecessor only after successor commit or terminal
replacement/close. Matching current reservation/observation facts register exact
resource keys; `ResourceReleased` is confirmation-only; rejected or stale
resource-bearing facts use one deduplicating disposition rule that never releases the
truthful predecessor shield. `PublicationClose` rejects successors, and tagged close
acknowledgements require the exact active close identity. Settlement consumption is
one active-transition key, never a session-history collection.

- [ ] **Step 4: Run focused GREEN**

Run the Step 2 command. Expected: both classes pass with every operation mapped to
proof, deadline, retained owner, input lease, terminal outcome, and finite wake.

- [ ] **Step 5: MAIN audits, documents, commits, and pushes**

Record Slice 1 as shadow-only. MAIN stages only the three Task 1 Kotlin files and
the two plan documents above, runs
`git diff --cached --check`, commits `feat(reader): define resumable transition model`
with the required co-author footer, and pushes to
`fork/fix/foreground-webview-handoff-ownership`.

### Task 2: Add the Android mailbox, ports, and shadow journal

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinatorTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED mailbox tests**

```kotlin
@Test
fun callbackBeforeCommandReturnIsQueuedAfterRegistration() {
    val fixture = coordinatorFixture(callbackInsideDeckCommand = true)
    fixture.coordinator.enqueue(pageEntryIntent())
    assertEquals(1, fixture.snapshot.activeTransitionsRegistered)
    assertEquals(1, fixture.snapshot.maxAdvanceDepth)
    assertEquals(
        listOf("register", "issue-deck", "enqueue-callback", "admit-callback"),
        fixture.trace
    )
}

@Test
fun shadowModeIssuesNoMutatingCommands() {
    val fixture = coordinatorFixture(mode = ReaderTransitionMode.Shadow)
    fixture.coordinator.enqueue(rasterProofFact())
    assertTrue(fixture.mutatingCommands.isEmpty())
    assertEquals(1, fixture.snapshot.shadowPredictions.size)
}
```

Also cover FIFO ingress, stale classification, synchronous port callbacks, deterministic
deadline calculation, and at most one test-only clock callback per active fixture phase.
The test registration does not activate production coordinator-clock scheduling.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionCoordinatorTest"
```

Expected: coordinator and ports are absent.

- [ ] **Step 3: Implement a non-reentrant main-thread drain**

```kotlin
internal enum class ReaderTransitionMode { Shadow, Active }

internal fun interface ReaderTransitionClockRegistration {
    fun cancel()
}

internal interface ReaderTransitionClock {
    fun nowMillis(): Long
    fun schedule(atMillis: Long, action: () -> Unit): ReaderTransitionClockRegistration?
}

internal interface ReaderResumableTransitionPorts {
    val clock: ReaderTransitionClock
    fun issue(
        command: ReaderTransitionCommand,
        onFact: (ReaderTransitionFact) -> Unit
    )
}

internal class ReaderResumableTransitionCoordinator(
    private val ports: ReaderResumableTransitionPorts,
    private val mode: ReaderTransitionMode,
    private var journal: ReaderTransitionJournal
) {
    private val mailbox = ArrayDeque<ReaderTransitionFact>()
    private var advancing = false

    fun enqueue(fact: ReaderTransitionFact) {
        check(Looper.myLooper() == Looper.getMainLooper())
        mailbox.addLast(fact)
        if (!advancing) advance()
    }

    private fun advance() {
        if (advancing) return
        advancing = true
        try {
            while (mailbox.isNotEmpty()) {
                val reduction = journal.reduce(mailbox.removeFirst(), ports.clock.nowMillis())
                journal = reduction.state
                if (mode == ReaderTransitionMode.Active) {
                    reduction.commands.forEach { command -> ports.issue(command, ::enqueue) }
                }
            }
        } finally {
            advancing = false
        }
    }
}
```

`nowMillis()` is the coordinator's injected time source. The `schedule` member is
preparatory/test infrastructure and is not wired or invoked for production attempts in
Tasks 2–6. Task 6 activated composition supplies `ReaderTask6FactOnlyTimerPort` instead;
Task 7 performs the atomic transfer before enabling production clock scheduling.

Persist transition/phase/deadline state before issuing a command. Every port callback
must call `enqueue`; none may recursively advance a controller. Wire a shadow-only
`ReaderTransitionGateway` through Root/Screen/platform hosts while legacy behavior
remains the sole writer.

- [ ] **Step 4: Run GREEN and affected authority gate**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionCoordinatorTest" \
  --tests "paige.navic.reader.ReaderPresentationAuthoritySequenceTest"
```

Expected: pass; shadow mode has no mutating commands and diagnostics contain only
bounded enums/counts.

- [ ] **Step 5: MAIN records shadow evidence, commits, and pushes**

Stage exact changed paths, verify the cached diff, commit
`feat(reader): add transition coordinator shadow journal`, and push.

### Task 3: Centralize release accounting and prepare deck-admission cutover

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionReleaseLedgerTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmissionCutoverTest.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED release/cutover tests**

Name and implement `releaseCommandRejectedRecordsNoEffectWithoutReissue`,
`releaseCommandThrowBeforeEffectBoundaryRecordsAmbiguousFailure`,
`releaseCommandThrowAfterPossibleEffectRecordsAmbiguousFailure`,
`duplicateOrStaleReleaseFailureOutcomeIsInert`,
`ambiguousReleaseFailureNeverIssuesSecondPhysicalRelease`,
`releaseFailureBlocksCompletionUntilCleanupTimeout`,
`ordinaryActivatedSuccessorReleaseRejectedTracksAttemptWithoutCleanup`,
`ordinaryTerminalReleaseThrowTracksAttemptWithoutCleanup`,
`unresolvedOrdinaryReleaseAttemptTransfersIntoCleanupWithoutReissue`,
`genericReleaseOrderingMatrixIsAttemptExact`,
`legacyReleaseOrderingMatrixIsAttemptAndPhysicalIdentityExact`,
`releaseCallbackThenRejectedKeepsReleasedAndReportsViolation`,
`releaseCallbackThenThrowKeepsReleasedAndReportsViolation`,
`releaseLateCallbackPromotesRejectedOrAmbiguousAttempt`,
`releaseDuplicateAndManyCallbacksSaturateWithoutThrowing`,
`releaseLatestConfirmationRemainsAuthoritative`,
`resourceRetirementFenceRenderingIsContentFree`,
`resourceRetirementFenceFailingEqualityUsesSanitizedProjection`, and
`resourceRetirementFenceAssertionsNeverRenderRawSequences`. Table-drive both generic and imported-
legacy release ports, every Accepted/Rejected/throw × zero/one/two/3+ callback ordering,
callback-before-result and callback-after-result confirmation, differing latest confirmation,
wrong attempt/cleanup ID/generation/registration/order/legacy physical identity, ordinary-to-
cleanup transfer, timeout, process close, active-attempt precedence over bounded tombstones,
content-free retirement-fence diagnostics, and no second physical release.

```kotlin
@Test
fun staleResourceReceivesOneOwnerIndependentReleaseAndConfirmation() {
    val ledger = releaseLedger()
    val registration = assertNotNull(
        ledger.register(
            key = deckKey(transitionId(), opaqueId = 41L),
            readerSessionGeneration = 7L,
            coordinatorEpoch = 3L
        )
    )
    val issuer = ReaderResourceReleaseIssuer.Transition(transitionId())
    val command = assertNotNull(ledger.requestRelease(issuer, registration))
    assertNull(ledger.requestRelease(issuer, registration))
    val confirmation = ReaderTransitionFact.ResourceReleased(
        transitionId = command.transitionId,
        identity = command.identity
    )
    assertTrue(ledger.confirmReleased(confirmation))
    assertFalse(ledger.confirmReleased(confirmation))
    assertTrue(
        ledger.retirementFence().confirms(registration.retirementOrder),
        "retirement fence must confirm the exact registration"
    )
    assertEquals(
        0,
        ledger.retirementFence().sanitizedProjection().outOfOrderReleasedCount,
        "retirement fence gap count mismatch"
    )
}

@Test
fun incompleteLegacyInventoryKeepsLegacyAsSoleWriter() {
    val cutover = deckCutover(ReaderLegacyDeckInventory.Incomplete)
    assertFalse(cutover.activate())
    assertFalse(cutover.coordinatorAdmissionOpen)
    assertTrue(cutover.legacyAdmissionOpen)
}
```

Also prove complete inventory adopts at most one truthful predecessor, drains every
other deck before activation, handles callback-before-ownership and
ownership-before-callback, and retains the release-only sink after close timeout.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderTransitionReleaseLedgerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderDeckAdmissionCutoverTest"
```

Expected: no coordinator ledger exists and deck currency remains host-local.

- [ ] **Step 3: Implement exact release states and the inactive cutover protocol**

The following preserves Task 3's published preparatory ledger behavior while defining
the Task 6 owner-independent extension. The adopted key never obtains a fabricated
transition identity.

```kotlin
internal enum class ReaderTransitionResourceState {
    Owned,
    ReleaseCommandIssued,
    RejectedNoEffect,
    AmbiguousFailure,
    Released,
    TimedOutUnreleased,
    ProcessClosedUnreleased
}

internal enum class ReaderReleaseFailureTombstoneReason {
    RejectedNoEffectTimedOut,
    AmbiguousFailureTimedOut,
    RejectedNoEffectProcessClosed,
    AmbiguousFailureProcessClosed
}

internal data class ReaderReleaseFailureTombstone(
    val cleanupKey: ReaderReleaseOnlyCleanupKey,
    val identity: ReaderReleaseCommandIdentity,
    val reason: ReaderReleaseFailureTombstoneReason
) {
    override fun toString(): String =
        "ReaderReleaseFailureTombstone(<redacted>)"
}

internal data class ReaderReleaseFailureFenceSnapshot(
    val tombstones: Set<ReaderReleaseFailureTombstone>
) {
    init { require(tombstones.size <= 32) }
    override fun toString(): String =
        "ReaderReleaseFailureFenceSnapshot(<redacted>)"
}

internal enum class ReaderResourceRetirementFenceCapacityStatus {
    Available,
    Full
}

internal data class ReaderResourceRetirementFenceSanitizedProjection(
    val hasContiguousReleasedPrefix: Boolean,
    val outOfOrderReleasedCount: Int,
    val capacityStatus: ReaderResourceRetirementFenceCapacityStatus
)

internal class ReaderResourceRetirementFenceSnapshot internal constructor(
    private val readerSessionGeneration: Long,
    private val coordinatorEpoch: Long,
    private val contiguousReleasedThrough: Long,
    private val outOfOrderReleasedSequences: Set<Long>
) {
    init {
        require(contiguousReleasedThrough >= 0L)
        require(outOfOrderReleasedSequences.size <= 32)
        require(outOfOrderReleasedSequences.all { it > contiguousReleasedThrough })
    }

    internal fun confirms(order: ReaderResourceRetirementOrder): Boolean =
        order.readerSessionGeneration == readerSessionGeneration &&
            order.coordinatorEpoch == coordinatorEpoch &&
            (
                order.sequence <= contiguousReleasedThrough ||
                    order.sequence in outOfOrderReleasedSequences
            )

    internal fun sanitizedProjection(): ReaderResourceRetirementFenceSanitizedProjection =
        ReaderResourceRetirementFenceSanitizedProjection(
            hasContiguousReleasedPrefix = contiguousReleasedThrough > 0L,
            outOfOrderReleasedCount = outOfOrderReleasedSequences.size,
            capacityStatus = if (outOfOrderReleasedSequences.size == 32) {
                ReaderResourceRetirementFenceCapacityStatus.Full
            } else {
                ReaderResourceRetirementFenceCapacityStatus.Available
            }
        )

    override fun equals(other: Any?): Boolean =
        other is ReaderResourceRetirementFenceSnapshot &&
            readerSessionGeneration == other.readerSessionGeneration &&
            coordinatorEpoch == other.coordinatorEpoch &&
            contiguousReleasedThrough == other.contiguousReleasedThrough &&
            outOfOrderReleasedSequences == other.outOfOrderReleasedSequences

    // Constant by policy: equality remains exact, while hashes cannot expose raw sequence state.
    override fun hashCode(): Int = 0x52524653

    override fun toString(): String =
        "ReaderResourceRetirementFenceSnapshot(<redacted>)"
}

internal interface ReaderTransitionReleaseLedger {
    fun register(
        key: ReaderTransitionResourceKey,
        readerSessionGeneration: Long,
        coordinatorEpoch: Long
    ): ReaderTransitionResourceRegistration?

    fun importLegacy(
        physicalIdentity: ReaderLegacyPhysicalIdentity,
        ownerId: ReaderTransitionResourceOwnerId.AdoptedPredecessor,
        kind: ReaderTransitionResourceKind,
        coordinatorEpoch: Long
    ): ReaderImportedLegacyResourceRegistration?

    fun requestRelease(
        issuer: ReaderResourceReleaseIssuer,
        registration: ReaderTransitionResourceRegistration,
        cleanupKey: ReaderReleaseOnlyCleanupKey? = null
    ): ReaderTransitionCommand.ReleaseResource?

    fun transferOutstandingAttemptsToCleanup(
        cleanupKey: ReaderReleaseOnlyCleanupKey
    ): Int

    fun recordRejectedNoEffect(
        fact: ReaderTransitionFact.ReleaseCommandRejected
    ): Boolean

    fun recordAmbiguousFailure(
        fact: ReaderTransitionFact.ReleaseCommandThrew
    ): Boolean

    fun recordPortContractViolation(
        fact: ReaderTransitionFact.ReleasePortContractViolated
    ): Boolean

    fun markCleanupDeadlineElapsed(
        cleanupKey: ReaderReleaseOnlyCleanupKey
    ): Int

    fun markProcessClosed(
        cleanupKey: ReaderReleaseOnlyCleanupKey
    ): Int

    fun cleanupStatus(
        cleanupKey: ReaderReleaseOnlyCleanupKey
    ): ReaderReleaseLedgerCleanupStatus

    fun confirmReleased(
        fact: ReaderTransitionFact.ResourceReleased
    ): Boolean

    fun confirmLegacyReleased(
        physicalIdentity: ReaderLegacyPhysicalIdentity,
        fact: ReaderTransitionFact.ResourceReleased
    ): Boolean

    fun retirementFence(): ReaderResourceRetirementFenceSnapshot
    fun failureFence(): ReaderReleaseFailureFenceSnapshot
}
```

`register` allocates the next positive retirement sequence for a new coordinator resource key.
`importLegacy` keys its bijection by complete `ReaderLegacyPhysicalIdentity`, allocates a
coordinator-global positive `opaqueId`, and returns the prior import only for the same complete
identity. Equal source-local tokens under different sources/domains remain distinct.

`requestRelease` allocates one fresh opaque `ReaderPhysicalReleaseAttemptId`, installs the
attempt → exact registration row before dispatch, transitions only `Owned` →
`ReleaseCommandIssued`, and returns `ReleaseResource(identity = attempt + registration,
cleanupKey = optional grouping)`. The imported-legacy dispatch additionally carries the exact
composite physical identity beside that common identity. Every ordinary successor, abort,
timeout, supersession, and terminal release therefore has attempt authority before cleanup
exists. Once any attempt exists for a registration, no issued/failure/released state can
allocate or emit another. `transferOutstandingAttemptsToCleanup` atomically adds the cleanup
key to every unresolved ordinary issued/rejected/ambiguous attempt in the closing session; it
changes no attempt ID/resource/state and emits no release command.

Both generic and legacy adapters install a fixed `ReaderBufferedReleaseCallbackState` before
calling the physical port, initialized with `latestAuthoritativeConfirmation = null`,
`callbackCount = ReaderSaturatingCallbackCount.Zero`, and
`violationStatus = ReaderReleaseBufferedViolationStatus.None`. Each callback only replaces `latestAuthoritativeConfirmation`,
saturating `callbackCount` at `ThreeOrMore` and marking bounded duplicate status; callback
handling allocates no collection, throws no exception, and never loses the latest exact
attempt/resource evidence. Result handling is exhaustive:

| Physical release order | Attempt-ledger/FIFO result |
|---|---|
| `Accepted` plus one callback | Flush the exact buffered `ResourceReleased(identity)` and advance the attempt to `Released`. |
| `Accepted` without callback | Keep `ReleaseCommandIssued` and await its existing confirmation/cleanup deadline; no new timer or reissue. |
| `Rejected` without callback | Guarantee no physical effect/callback; queue exact `ReleaseCommandRejected(optionalCleanupKey, identity, reason)` and record `RejectedNoEffect`. |
| Throw without callback | Queue exact `ReleaseCommandThrew(optionalCleanupKey, identity, reason)`, record `AmbiguousFailure`, and never reissue. |
| Callback then `Rejected` or throw | Flush the latest exact confirmation to `Released`, then queue one bounded `ReleasePortContractViolated`; rejection/throw cannot overwrite released truth or create unreleased/ambiguous state. |
| Callback after `RejectedNoEffect` or `AmbiguousFailure` | Promote that exact attempt to `Released`, then queue one bounded late-callback violation; never issue another command. |
| Duplicate or 3+ callbacks before/after return | Keep replacing latest exact confirmation, saturate bounded count/status, publish latest authority once per coalesced adapter drain, keep release idempotent, and queue at most one bounded violation fact for that drain. |

If callback count and result conflict produce multiple defects, one bounded
`ReleasePortContractViolated(..., MultipleViolations, callbackCount)` summarizes them after the
latest confirmation. The adapter never invokes user/reducer code while the physical call is on
stack. Generic confirmation matches attempt ID plus registration key/order. Legacy confirmation
also matches complete physical identity; a physical identity never enters common state but is
validated by the imported-attempt envelope. Wrong attempt, registration/order, or legacy
identity is inert.

An exact rejection changes only the matching attempt's `ReleaseCommandIssued` to
`RejectedNoEffect`; exact throw changes it to `AmbiguousFailure`. Both block
`EmptyReleased`, remain non-reissuable, and transfer unchanged into cleanup. The first exact
cleanup timeout converts every still-unconfirmed issued/rejected/ambiguous attempt to
`TimedOutUnreleased`; rejected/ambiguous rows receive bounded tombstones preserving attempt and
failure class. Process close performs equivalent `ProcessClosedUnreleased` conversion without
persistence/reissue. Active attempt rows are bounded by the existing owned-registration ledger
capacity and outrank at most 32 tombstones; overflow blocks new
release admission rather than evicting evidence. Exact late confirmation can promote issued,
rejected, ambiguous, timed-out, or process-closed attempt state to `Released`; contradiction is
reported only through the bounded violation fact and never causes another physical call.

Confirmation advances the private retirement fence and keeps at most 32 gaps. The non-data-
class `ReaderResourceRetirementFenceSnapshot` exposes raw sequence state only through internal
`confirms(order)`, renders a fixed content-free constant, uses exact explicit equality and a
constant non-data-derived hash, and offers only `ReaderResourceRetirementFenceSanitizedProjection`
for assertions: released-prefix presence, bounded gap count, and capacity category. Tests and
diagnostics never render or `assertEquals` raw retirement sequences. Active registrations take
precedence; gap overflow blocks admission. Raw attempt IDs, domain/freeze/local/import/order
values and fence internals are memory-only and barred from logs, diagnostics, analytics,
screenshots, crash metadata, equality failure output, and persistence.

Implement and test freeze legacy admission → inventory all owned/pending/discovered
resources → import ledger keys → adopt at most one provable predecessor → release and
confirm all others → prove the final deck-writer flip only in the inactive protocol
fixture. Task 3 does not invoke this protocol from production or open coordinator
admission: the production policy remains `LegacyOnly`, with legacy
as the sole writer. The former statement that Task 4 would perform deck activation is
superseded. Tasks 4 and 5 remain inactive preparation; Task 6 alone can assign
truthful transition/adopted ownership and atomically activate every route. After that
install succeeds, never fall back during the reader session.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: exact-once release, rejection/throw accounting, no reissue, bounded
failure fencing, and cutover protocol pass while the production `LegacyOnly` policy keeps
legacy as the sole writer.

- [ ] **Step 5: MAIN records privacy-safe protocol counts, commits, and pushes**

Record only inventory/adoption/release counts and the preparatory protocol outcome.
Do not claim production activation. Commit
`feat(reader): prepare deck admission cutover` and push.

### Task 4: Add inactive raster, renderer-callback, and recovery ports

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmissionCutoverTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionReleaseLedgerTest.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED adapter tests**

Prove raster proof cannot publish readiness, deck prepared cannot commit a frame,
raster+deck still require prepared-frame proof, stale callbacks only register
resources, capacity rejection becomes `Deferred(RendererCapacityAvailable)`,
recovery uses a fresh generation, and Task382’s late callbacks settle through the
ledger rather than `RasterProofDeckAttempt`.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderDeckAdmissionCutoverTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest"
```

Expected: a current local readiness/admission/recovery writer violates the new tests.

- [ ] **Step 3: Convert adapters**

```kotlin
internal data class ReaderDeckLease(
    val transitionId: ReaderTransitionId,
    val binding: ReaderPresentationBinding,
    val role: ReaderDeckSubmissionRole,
    val preparationGeneration: Long,
    val rasterGeneration: Long,
    val textureGeneration: Long
)
```

Map common `ReaderTransitionDeckRole` to Android `ReaderDeckSubmissionRole` at the
port boundary. Typed renderer and raster adapters can emit exact coordinator facts;
physical release ports execute only coordinator `ReleaseResource`. Reject malformed
fact/resource identities before mailbox insertion, reject duplicate or fenced
registration before physical reserve/prepare. Task 4's preparatory lifecycle/
transition-sequence tombstones remain inactive and are not sufficient for adopted
resources. Task 6 replaces them with bounded owner-independent
`ReaderResourceRetirementOrder` fences; active registered resources take precedence so
no live lease becomes unreleasable.

Task 4 does not remove the production legacy writers or activate coordinator deck
admission. Production lacks an active semantic transition source that can assign an
exact `ReaderTransitionId` to every live callback, so invoking freeze → inventory →
adopt/drain here would fabricate identity or create a dual-writer interval. The
`LegacyOnly` production policy remains in force. Task 5 supplies only the inactive
semantic adapter and receipt fences. Task 6 must supply the active command-bound
semantic source and exact production callback identities, then perform one atomic
cutover after the inventory, close-path, and release-only-sink prerequisites are
true.

- [ ] **Step 4: Run focused GREEN**

Run Step 2 plus the coordinator, release-ledger, source-contract, and common authority
sequence suites. Expected: pass; material completion without frame proof cannot
publish `Ready` or admit input, resource release remains exact-once and bounded, and
production stays `LegacyOnly`.

- [ ] **Step 5: MAIN reconciles Slice 2 rows, commits, and pushes**

Record that this checkpoint supplies inactive typed adapters and physical release
ports but no production activation. Commit
`refactor(reader): add transition resource adapters` and push.

### Task 5: Build the inactive preparatory semantic-adapter checkpoint

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationController.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationReceipt.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncLifecycleReducerTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionModelTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinatorTest.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED semantic tests**

Cover exact-once settlement, duplicate delivery, wrong-transition receipt,
callback-before-command-return, stale receipt after supersession, untagged
same-session TOC relocation, app-originated TOC/search/bookmark/annotation/jump,
unsolicited destination commit, predecessor retention until successor frame, and
failed relocation’s noninteractive diagnostic shield.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.reader.ReaderResumableTransitionModelTest" \
  --tests "paige.navic.reader.ReaderControllerTest" \
  --tests "paige.navic.reader.ReaderWhispersyncLifecycleReducerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionCoordinatorTest"
```

Expected: an external route issues a consequence before coordinator acceptance or a
settlement remains reusable.

- [ ] **Step 3: Build the inactive semantic adapter**

Route page turn, shell-cover entry, TOC, search, bookmark, annotation, jump, Retry,
and cancel through the shadow `ReaderTransitionGateway` before the corresponding
legacy dispatch. App-originated relocation registers `ExternalSemanticRelocation`
before the predicted `RequestSemanticSynchronization`; unsolicited same-publication
`FoliateDestinationCommitted` creates the same operation with semantic proof already
satisfied. Consumption is persisted before any predicted consequence. Task 5 remains
an inactive preparatory checkpoint: production stays `Shadow`/`LegacyOnly`, legacy
remains the sole writer, and no semantic, deck, frame, input, inventory, or release
consequence port activates here. Untagged settlement acknowledgement cannot satisfy
proof; exact callback identity remains a Task 6 command-bound prerequisite.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: all semantic tests pass.

- [ ] **Step 5: MAIN records the preparatory checkpoint and carry-forward rows**

Account for successor commit, Retry, subsequent relocation/publication replacement,
and close. Record `T5-ACTIVE-SOURCE` and `T5-EXACT-SEED` under Task 6 and Stage 6;
do not claim Task 5 production cutover. Independent specification and code-quality
reviews must approve the inactive boundary. MAIN must force-rerun the Task 5,
Task 1–4 regression, and affected authority/source/routing groups before committing.

Checkpoint: independent specification and code-quality reviews approved the inactive
preparatory boundary. MAIN's forced consolidated Android host rerun passed 541 unique
tests with zero failures, errors, or skips: Task 5 passed 239, Task 1–4 regression
passed 250, and the affected authority/source/routing group passed 428. Production
remains `Shadow`/`LegacyOnly`; Task 6 retains `T5-ACTIVE-SOURCE` and `T5-EXACT-SEED`.
Commit `refactor(reader): coordinate semantic relocation` and push.

### Task 6: Perform one atomic per-session semantic, material/deck, frame, input, inventory, and release-sink cutover

The former frame/input-only Task 6 map is superseded by this amendment, not
completed. Task 6 is the first package permitted to activate production coordinator
consequences, and it must activate the complete package exactly once per session.
Task 1 through Task 5 remain preparatory; preserve Task 5's published 541-test
evidence and `Shadow`/`LegacyOnly` classification unchanged. Task389's initial-origin
contract is a release blocker before this package's source/port completion or activation.
Task #385 owns the reopened real-owner/source/port wiring below; Task #386 must remain
fail-closed until Task389 is published and #385 closes. Do not mark adapters complete from
interface presence or preparatory tests.

**Create:**
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivation.android.kt` — activation states, freeze token, exact subordinate inventory/drain, non-neutral adopted registration or neutral origin, pre-drain checkpoint, bounded restoration/blocked protocol, atomic route/baseline/optional-owner/input/deck-writer/egress snapshot installation, and permanent release sink.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivationTest.kt` — activation, inventory, drain, seed, failure, and close-phase tests.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionAtomicCutoverTest.kt` — semantic-through-frame integrated cutover and no-fallback tests.

**Modify common model and routing:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt` — typed adopted/neutral initial origin and committed-presentation baseline, authentic first/later identity allocation, adopted ownership, allocation-scoped raster/deck, retained-publication ordering, `FrameTargetPreparation` command/facts/proof, sealed kind targets, successor `Committing` acknowledgement, and publication commands; retain exactly eleven operations.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt` — validate immutable owner/binding/lease compatibility without creating a second writer.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationReceipt.kt` — construct semantic receipt identity only from explicit command origin.
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationController.kt` — accept `ReaderPresentationEventOrigin`; activated intents do not continue to legacy effects.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt` — add inactive/activated gateway attachment and make installation all-or-nothing.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt` — render only the installed coordinator decision and route intents through the gateway.
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt` — suppress legacy semantic dispatch only through the successful installation barrier.

**Modify Android coordinator and hosts:**
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt` — activation-aware FIFO, retained-input gate, allocation sequencing, coordinator registration selection/allocation, awaiting-target issuance, prepared-target storage, successor `Committing` retaining predecessor truth, synchronous publication-result-to-fact conversion, fail-closed state, and release-only filtering.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt` — capability-authenticated package, semantic slot, allocation, coordinator-registration target-preparation/exact-presentation port, sole synchronous publication protocol, and owner-aware release.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt` — replace deck-only cutover with exhaustive token contribution and accept only exact allocated leases after activation.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt` — fence and inventory raster/prewarm/repair work, allocate fresh monotonic generations, and reject unallocated commands before physical work.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt` and `ReaderPageTurnBitmapSource.android.kt` — freeze starts/registrations and expose collision-safe composite-identity inventory/drain/restoration for snapshots, descriptors, hydration, publication, persistence, generation, presented/live capture ownership, retained candidates, Handler/renderer/PixelCopy/JavaScript/visual-state/draw callbacks, validation, store/cache, and teardown ownership.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterHydrationScheduler.android.kt`, `ReaderPageRasterPublicationScheduler.android.kt`, `ReaderPageRasterPublicationLedger.android.kt`, `ReaderPageRasterScheduler.android.kt`, `ReaderPagePendingCallbackOwners.android.kt`, and `ReaderPageTurnBundleTeardown.android.kt` — contribute exact subordinate source fences, snapshots, token drains, and restoration confirmations; counts alone do not qualify.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnership.android.kt` — freeze and account for passive leases, restoration closure/callback, live/exclusive claims, readiness callbacks, and current mutation claim; restore before legacy reopening.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt` — command-bound Foliate invocation, exact callback tags, frozen callback/deck inventory, command-only raster/deck/frame execution, and ledger-only release.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt` — make `ReaderPresentationHostBridge` and `ReaderNativePagePresentationPublisher` bind coordinator-selected Deck/ledger-allocated FrameHandoff registrations to immutable shell/native/live physical targets, emit target-prepared facts, and become command-driven target-echoing prepared-frame producers with no independent ownership allocation, binding-only polling, starters, deadlines, commits, or resource replacement.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt` — sole production composition root: capability-authenticate every real adapter, perform exhaustive installation with no reachable Shadow/LegacyOnly/no-op route, report exact visible/frozen resources, and apply initial or successor/retained-owner input snapshots atomically.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt` — narrow/veto only inside the sole atomic retained/successor owner-input transaction; expose no separate activated input mutation.

**Modify focused tests:**
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionModelTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncLifecycleReducerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinatorTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionReleaseLedgerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmissionCutoverTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderNativePagePresentationPublisherTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostControllerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterHydrationSchedulerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPublicationLedgerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPublicationSchedulerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterSchedulerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnershipTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`

**Modify governing documents:**
- `docs/superpowers/specs/2026-09-13-reader-resumable-transition-coordinator-design.md`
- `docs/superpowers/plans/2026-09-13-reader-resumable-transition-coordinator.md`
- `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

**Mandatory carry-forward rows:**

- `T5-ACTIVE-SOURCE`: close only when `KomikkuReaderNativeFrameHost` constructs one
  capability-authenticated package from real adapters and the one
  `installActivatedSession` barrier suppresses every legacy semantic dispatch; activates
  semantic, allocation/deck, coordinator-registration target preparation/presentation,
  lifecycle-fact, retained fact-only
  timer, sole successor/retained-owner publication, and resource routes through exact
  `ReaderProductionActivatedSessionPorts`; validates both leases and the typed initial
  origin, requires/transfers the exact pre-reserved neutral bootstrap capability when
  applicable, internally derives its sequence-zero/no-parent baseline journal, publishes only its truthful optional owner/
  resource plus physical lease, marks `Activated`; and opens egress in one
  transaction. Null neutral registration never reaches install, and callers cannot supply the journal. No public Boolean/`complete()`/all-no-op package, installed
  `Shadow`/`LegacyOnly` route, partial observation, or active-session fallback is legal.
- `T5-EXACT-SEED`: close with the one-domain exhaustive subordinate-owner inventory,
  direct physical predecessor proof, one adopted-at-handoff seed carrying the selected
  row's complete composite identity and truthful resource kind, collision-safe legacy
  import, owner-independent retirement registration, and `AdoptedLegacy` provenance when
  non-neutral; or an exact neutral origin with no seed/binding/resource/visible owner.
  Preserve composite identity → selected kind/binding/owner → seed → import key →
  retirement registration → adopted origin ordering; never infer from binding, journal
  state, gesture, renderer generation, or acknowledgement. Initial leases use the
  identity-free initial-only type; both requested and physical leases independently match
  exact adopted owner/binding while preserving breadth ordering; native generation zero is
  lawful without migration meaning; curl adoption fences/cancels legacy gestures and may
  carry only `None` or `ChromeOnly`. Install either origin as a
  committed-presentation baseline without fabricating a transition identity or parent.
  Neutral bootstrap reserves its private request before `ReadyToCommit`, transfers it in
  the atomic snapshot, and obtains its first exact binding only through a command-bound live-
  Foliate callback under the existing `BootstrapNativePage` operation; registration null
  prevents activation and invokes exact cleanup/restoration.
- `T6-LIFECYCLE-FACT-ONLY`: Task 6 suppresses all overlapping legacy lifecycle
  consequences at installation. Existing normalization remains the sole ordered
  lifecycle ingress until Task 7 and may emit safety facts only; Task 6 cannot create
  visibility/restore/reflow/recovery or wake policy.
- `T6-NO-DEADLINE-TRANSFER`: Task 7 retains coordinator-clock deadline ownership. Task 6 keeps the
  production coordinator clock inactive and selects exactly one existing command-scoped
  physical timer for each activated attempt. For narrowing operations no timer binds
  while retained publication is pending; exact `Applied(Retained)` returns to pre-work,
  then it publishes pending `TimerBinding`, binds exact `ReaderTransitionId`, clears the
  stage on exact success, and only then permits the first timer-requiring command. A
  rejection must match active ID plus pending TimerBinding. It
  emits only the matching typed `DeadlineExpired` fact through FIFO and cannot
  mutate consequences, Retry, or extend/rearm outside that command registration. All
  competing timers are inactive. The Task 6 port must expose the exact
  `snapshotForTask7Transfer(registration)` snapshot contract while neither invoking it as
  a transfer nor enabling coordinator-clock scheduling. Task 7 later atomically transfers every active/new
  registration to the coordinator clock and deletes the retained schedulers with no
  zero-owner or two-owner interval. The sole Task 6 close-only use is not Task 7 policy:
  release-only atomically replaces/fences the attempt owner with one exact two-second
  physical fact-only cleanup deadline keyed by opaque cleanup ID plus generation. It emits
  only `ReleaseOnlyCleanupDeadlineElapsed`, has no persisted wake/recreation, and is fenced
  after terminal cancellation plus terminal release accounting or process close.
- `T6-NO-WAKE-TRANSFER`: Task 6 neither persists nor consumes SavedState wake demand.
  Its restoration checkpoint is bounded in-memory activation safety state; Task 7 owns
  all wake storage and resumption.

- [ ] **Step 1: Write the grouped deterministic RED suite**

Group 0, authentic initial committed-presentation baseline:

- `adoptedBaselineFirstSuccessorStartsAtOneWithoutParentAndReleasesAfterApplied` proves
  install creates `Initial(AdoptedPredecessor)`, the first real operation is sequence `1`
  with no parent, and its imported resource is retained through preparation/commit command
  until exact `Applied(Successor)`, then released once by session authority.
- `neutralBaselineBootstrapUsesFoliateBindingAndExistingOperation` proves neutral has no
  owner/binding/resource/seed, reserves its private current-destination handle before
  `ReadyToCommit`, transfers that exact capability through atomic install, and post-install
  bootstrap uses exactly `BootstrapNativePage` with no nullable `register()` branch; its
  command consumes the reserved handle and obtains a command-bound live-Foliate binding,
  and allocation/raster/deck/frame work starts only afterward.
- `neutralBootstrapRegistrationNullBlocksActivationBeforeAtomicInstall` proves nullable
  `register()` failure creates no transition, never calls install, never switches any route/
  owner/input/egress field, discards/releases activation-owned state exactly once, and
  truthfully completes pre-drain unfreeze or post-drain restoration/`ActivationBlocked`.
- `unsolicitedRelocationFromAdoptedBaselineRetainsThenReleasesExactlyOnce` and
  `unsolicitedRelocationFromNeutralBaselineNeedsNoRetainedPublication` prove an untagged
  authoritative destination creates `ExternalSemanticRelocation` from both variants;
  adopted waits for retained acknowledgement, neutral fabricates no retained subject, and
  each follows existing allocation/frame/successor acknowledgement.
- `initialOriginCannotFabricateOperationOrParent` and
  `transitionSequenceIsMonotonicAcrossAbortRetryAndRestore` prove no activation/seed/
  baseline conversion can create a `ReaderTransitionId`; first is `1`/no parent and every
  later abort/Retry/restore/relocation/close identity increments and names the prior real
  operation exactly.
- `retryAndRestorationPreserveTruthfulInitialBaseline` proves timeout, abort, Retry, and
  Task 7 restoration facts cannot convert neutral to adopted, lose an adopted registration,
  resurrect proof, or reuse an identity. This is a common model contract only; Task 7
  lifecycle/wake implementation remains excluded from Task 6.
- `adoptedBaselineReleaseWaitsForSuccessorPublicationAcknowledgement` proves prepared
  frame, commit return, rejected/stale acknowledgement, timeout, abort, and Retry do not
  release the baseline.
- `closeFromAdoptedBaselineReleasesOnceWithoutTransitionBorrowing` and
  `closeFromNeutralBaselineEntersReleaseOnlyWithoutResourceCommand` prove permanent close
  from either baseline, including close as the first real operation, with no fake binding,
  parent, registration, or release issuer.
- `initialOriginEqualityDiagnosticsExposeOnlyBoundedCategories`,
  `sensitiveInitialWrappersRenderOnlyRedactedConstants`, and
  `sensitiveInitialEqualityFailureUsesSanitizedProjection` prove direct `toString()` for
  every initial lease/origin/seed/composite physical identity/imported registration/
  restoration checkpoint/decision/installation/snapshot/committed transition/journal,
  semantic invocation ID/record, release-only cleanup ID/generation/key/state, cleanup
  deadline registration/fact, physical release attempt ID/identity/imported dispatch/buffered callback state/
  rejection/throw/port-violation facts, bounded active release-attempt rows/tombstones,
  explicit retirement-fence snapshot/sanitized projection, and keyed cancellation command/outcome facts; each
  value renders a fixed redacted constant (or explicit non-data-
  class equivalent), and failed assertions compare only a separately
  sanitized bounded category projection. Tests never pass whole sensitive values to
  `assertEquals`, interpolation, assertion messages, logs, snapshots, or exception text;
  seed ID, session/epoch, complete physical identity, registration, binding, lease
  internals, and all opaque values remain absent from diagnostics, persistence, analytics,
  screenshots, and crash metadata.
- `initialLeaseCannotCarryClaimedGestureOrTransitionId` and
  `curlAdoptionFencesLegacyGestureAndUsesIdentityFreeLease` prove initial origin fields use
  only `ReaderInitialPresentationInputLease`, no constructor can carry
  `ClaimedGesture`/`ReaderTransitionId`, and curl adoption cancels/fences every legacy
  gesture before installing only `None` or `ChromeOnly`.
- `failedNeutralBootstrapSemanticResolutionQueuesTypedRetryableFact` and
  `retryAfterFailedNeutralBootstrapUsesNextAuthenticIdentity` prove post-admission semantic
  synchronization failure (not pre-install registration failure) is queued as exact
  `CommandRejected(..., SemanticSynchronization, boundedReason)` rather than a timeout,
  terminates fail-visible and retryable, and Retry allocates the next authentic operation
  identity without reusing sequence 1 or fabricating a parent.
- `semanticMissingHandleRejectsSingleSynchronizationStage`,
  `semanticExpiredHandleRejectsSingleSynchronizationStage`,
  `semanticConsumedHandleRejectsSingleSynchronizationStage`,
  `semanticWrongSessionHandleRejectsSingleSynchronizationStage`,
  `semanticRegistryFailureRejectsSingleSynchronizationStage`,
  `semanticSlotCapacityRejectsSingleSynchronizationStage`, and
  `semanticExecutionFailureRejectsSingleSynchronizationStage` prove every private handle/
  registry/slot failure and every execution failure proven in `BeforeMutation` preserves its
  bounded reason but maps to the one pending
  `SemanticSynchronization` stage for the exact transition; no internal reducer substage is
  representable.
- `semanticSynchronousCallbackThenAcceptedBuffersAndFlushesReceipt` and
  `semanticAcceptedThenAsynchronousCallbackQueuesOneReceipt` prove callback evidence stays
  adapter-buffered until `synchronize` returns and `Accepted` permits exactly one FIFO receipt.
- `semanticRejectedWithoutCallbackQueuesExactSynchronizationRejection` and
  `semanticThrowBeforeMutationQueuesExactSynchronizationRejection` prove validation/rejection
  before the mutation boundary performs no Foliate mutation, permits no callback, and queues
  the exact one-stage bounded rejection.
- `semanticCallbackThenRejectedPreservesReceiptAndClosesFailVisible`,
  `semanticCallbackThenThrowPreservesReceiptAndClosesFailVisible`, and
  `semanticRejectedThenLateCallbackIsFatalContractViolation` prove contradictory result order
  never clears receipt evidence: after invocation return it queues the final latest exact
  receipt once plus one bounded `SemanticPortContractViolated`, blocks later semantic commands for the session, and enters
  fail-visible close/release-only without fallback.
- `semanticDuplicateCallbackIsFatalContractViolation`,
  `semanticThreeOrMoreSynchronousCallbacksSaturateWithoutThrowing`,
  `semanticDifferingDuplicateCallbacksRetainLatestAuthority`,
  `semanticCallbackCountSaturatesAtThreeOrMore`,
  `semanticDuplicateCallbackPathNeverThrows`, and
  `semanticFatalFlushesLatestReceiptOnce` prove arbitrary duplicate callbacks replace one
  fixed latest-authority field, saturate bounded metadata, allocate no receipt collection,
  throw nothing, enqueue only the latest exact receipt once plus one violation, consume the
  success slot at most once, and take the same fatal path.
- `semanticAcceptedWithoutCallbackUsesExistingExactTimeout` proves silence after `Accepted`
  remains governed by the operation's existing exact `DeadlineExpired`, never a second timer.
- `delayedSemanticSynchronizationRejectionAfterStageClearIsInert` and
  `duplicateSemanticSynchronizationRejectionAfterStageClearIsInert` prove exact semantic
  success/rejection/terminal advancement clears the singleton before delayed or duplicate
  rejection can affect another phase or transition.
- `materialAllocationRejectionQueuesTypedFactAndTerminatesRetryable` and
  `timerBindingRejectionQueuesTypedFactWithoutTimeoutSubstitution` prove synchronous
  command rejection enters FIFO with exact transition/stage/bounded reason, receives an
  exhaustive terminal reducer outcome, preserves retained truth, and never disappears or
  substitutes `DeadlineExpired`.
- `delayedCommandRejectionAfterStageAdvanceIsInert`,
  `duplicateCommandRejectionIsInertAfterPendingStageConsumed`, and
  `timerBindingRejectionRequiresPendingTimerBindingStage` prove every emitted command first
  publishes a singleton pending stage admitted by its phase; exact success clears before
  phase advance, matching rejection consumes it terminally, and delayed/duplicate/wrong-
  stage facts are inert.
- `mismatchedRequestedLeaseRejectedForShellAdoption`,
  `mismatchedRequestedLeaseRejectedForNativeAdoption`,
  `mismatchedRequestedLeaseRejectedForCurlAdoption`, and
  `mismatchedRequestedLeaseRejectedForLiveAdoption` prove breadth ordering cannot mask an
  owner-incompatible requested lease at origin, checkpoint, decision, or barrier.
- `initialNativePageLeaseAllowsTextureGenerationZero` proves zero satisfies the current
  authoritative initial-page proof at all four validation boundaries; this is not migration
  evidence.
- `publicationReplacementFromAdoptedBaselineClosesAndReleasesOnce` and
  `publicationReplacementFromNeutralBaselineClosesWithoutBaselineRelease` prove
  replacement allocates no operation, atomically captures any pre-close cancellation target,
  enters permanent `ReleaseOnly`, emits one keyed cancellation only when active work existed,
  retires command ownership, releases an adopted resource once by session authority, and
  creates no baseline release for neutral.
- `closeDuringSequenceOneOperationCapturesCleanupAndCancelsOnce` proves close atomically
  captures sequence-1 as the cleanup target before clearing active state and emits exactly
  one keyed `CancelOwnedWork`; the existing close operation is never the cancellation target.
- `releaseOnlyCancellationRejectionRecordsFailClosedCleanupFailure` table-drives an exact
  rejected callback, synchronous `Rejected` return, and escaping throw; each makes
  cancellation terminal with its bounded reason while exact-once resource release continues
  without Retry or reopen; successful cleanup requires `EmptyReleased`, while failure
  accounting remains incomplete until `TerminalFailure` and deadline fencing are recorded.
- `duplicateReleaseOnlyCancellationOutcomeIsInert` and
  `staleReleaseOnlyCancellationOutcomeIsInert` prove consumed, wrong-key, wrong-ID,
  replaced, and post-completion outcomes cannot mutate or replace the sole cleanup record.
- `closeWithNoActiveOperationEmitsNoCancellation` proves neutral/adopted no-active close may
  use the existing `PublicationClose` operation but records `NotRequired` and fabricates no
  cancellation target or command.
- `releaseOnlyRejectsWorkAndCannotReopenTransition` proves permanent release-only admits only
  exact cleanup/release outcomes and rejects all semantic/material/frame/input/timer/Retry
  work before and after timeout.
- `replacementDuringActiveOperationUsesSameReleaseOnlyCleanupTransaction`,
  `replacementCannotReplaceExistingReleaseOnlyCleanupRecord`, and
  `processCloseDoesNotReemitPendingCleanupCancellation` prove replacement and process close
  share the transaction, never create another operation/state/cleanup record, never re-emit
  cancellation, and convert unresolved process-teardown cleanup to bounded
  `ProcessClosedPending`/`ProcessClosedUnreleased` without persistence.
- `releaseOnlyCleanupDeadlineFiresAndRecordsCloseDrainTimeout`,
  `releaseOnlyCleanupDeadlineCancelsAfterCancellationAndLedgerTerminal`, and
  `releaseOnlyCleanupDeadlineBindFailureFailsClosed` prove the retained Task 6 timer starts
  atomically with cleanup, is cancelled only after cancellation and release accounting are
  terminal, and every binding/elapsed failure remains closed with no fallback.
- `staleReleaseOnlyCleanupDeadlineGenerationIsInert`,
  `duplicateReleaseOnlyCleanupDeadlineElapsedIsInert`,
  `processCloseFencesReleaseOnlyCleanupDeadlineWithoutWake`, and
  `completedCleanupCannotReceiveDeadlineElapsed` prove ID-plus-generation fencing, exact-once
  delivery, terminal cancellation, and no persisted wake/recreation.
- `releaseCommandRejectedRecordsNoEffectWithoutReissue`,
  `releaseCommandThrowBeforeEffectBoundaryRecordsAmbiguousFailure`,
  `releaseCommandThrowAfterPossibleEffectRecordsAmbiguousFailure`,
  `ordinaryActivatedSuccessorReleaseRejectedTracksAttemptWithoutCleanup`,
  `ordinaryTerminalReleaseThrowTracksAttemptWithoutCleanup`,
  `unresolvedOrdinaryReleaseAttemptTransfersIntoCleanupWithoutReissue`,
  `genericReleaseOrderingMatrixIsAttemptExact`,
  `legacyReleaseOrderingMatrixIsAttemptAndPhysicalIdentityExact`,
  `releaseCallbackThenRejectedKeepsReleasedAndReportsViolation`,
  `releaseCallbackThenThrowKeepsReleasedAndReportsViolation`,
  `releaseLateCallbackPromotesRejectedOrAmbiguousAttempt`,
  `releaseDuplicateAndManyCallbacksSaturateWithoutThrowing`,
  `releaseLatestConfirmationRemainsAuthoritative`,
  `duplicateOrStaleReleaseFailureOutcomeIsInert`,
  `ambiguousReleaseFailureNeverIssuesSecondPhysicalRelease`, and
  `releaseFailureBlocksCompletionUntilCleanupTimeout` table-drive generic and imported-legacy
  ports before and during cleanup. Every dispatch has a mandatory opaque attempt ID plus exact
  registration; imported legacy also matches complete Android physical identity, while cleanup
  grouping is optional. They prove ordinary successor/terminal rejection and throw, transfer of
  unresolved attempts into cleanup without command reissue, the complete accepted/rejected/
  throw with callback-before/result-before/late/duplicate/3+ matrix, latest confirmation
  authority, `Released` precedence, bounded saturated violation facts, truthful
  `RejectedNoEffect` versus `AmbiguousFailure`, terminal timeout/process-close conversion,
  bounded active rows/tombstones, and active-attempt precedence.
- `resourceRetirementFenceRenderingIsContentFree`,
  `resourceRetirementFenceFailingEqualityUsesSanitizedProjection`, and
  `resourceRetirementFenceAssertionsNeverRenderRawSequences` prove the non-data fence has fixed
  rendering, explicit exact equality, a constant non-data-derived hash, and assertions compare
  only sanitized bounded projections; raw sequence values never enter expected/actual values,
  messages, snapshots, or diagnostics.

Group A, activation atomicity:

```kotlin
@Test
fun missingAnyActivatedPortRejectsTheWholeInstallAndRestoresLegacy() {
    val fixture = activationFixture(missing = ActivatedPort.Frame)
    assertEquals(ReaderActivationInstallResult.Rejected(
        ReaderTransitionFailureReason.ActivationPrerequisiteMissing
    ), fixture.activate())
    assertEquals(ReaderSessionActivationState.Legacy, fixture.state)
    assertTrue(fixture.routes.allLegacy)
    assertFalse(fixture.routes.anyCoordinatorConsequence)
}

@Test
fun routesInitialOwnerPhysicalLeaseActivatedStateAndEgressAppearInOneSnapshot() {
    val fixture = activationFixture()
    fixture.activate()
    assertEquals(
        listOf(ReaderSessionActivationState.Legacy, ReaderSessionActivationState.Activated),
        fixture.installationSnapshots.map { it.sanitizedProjection.activationState }
    )
    assertTrue(
        fixture.installedSnapshot.initialDecision === fixture.initialDecision,
        "installed decision must be the validated decision"
    )
    assertEquals(
        readerInitialLeaseKind(fixture.initialDecision.origin.physicalLease),
        fixture.installedSnapshot.sanitizedProjection.physicalLeaseKind
    )
    assertTrue(
        fixture.installedSnapshot.journal.committed is ReaderCommittedPresentation.Initial,
        "barrier journal must contain an initial committed presentation"
    )
    val initial = fixture.installedSnapshot.journal.committed as
        ReaderCommittedPresentation.Initial
    assertTrue(
        initial.origin === fixture.initialDecision.origin,
        "barrier journal must retain the validated origin"
    )
    assertEquals(0L, fixture.installedSnapshot.journal.lastTransitionSequence)
    assertNull(fixture.installedSnapshot.journal.lastIssuedTransitionIdentity)
    assertTrue(
        fixture.installedSnapshot.reservedNeutralBootstrap ===
            fixture.initialDecision.neutralBootstrapReservation,
        "snapshot must own the validated neutral bootstrap reservation"
    )
    assertTrue(fixture.installedSnapshot.commandEgressOpen)
    assertEquals(1, fixture.atomicInstallationCommitCount)
    assertFalse(fixture.observedInstalledButUnpublished)
}
```

Also prove table-driven atomic installation for neutral (no seed ID, visible owner,
binding, physical identity, kind, or adopted registration; exact pre-reserved bootstrap),
shell-cover (`FrameHandoff`), native-page (`Deck`, including texture generation zero),
curl/native material (`Deck`), and live/WebView predecessor
(`FrameHandoff`) decisions. Each
non-neutral case must preserve the selected frozen row's exact owner, resource kind,
binding, complete physical identity, and adopted-legacy provenance through the one
snapshot write. Reject a mismatched owner/kind, identity, provenance, binding, imported
owner ID, session, epoch, seed, broadened physical lease, or owner-incompatible requested
or physical lease before that write. Reject missing/wrong-session neutral reservation and
any adopted reservation. Prove the
composition root exposes no per-route/per-owner/per-input install setter and the
no-callback/no-suspend snapshot replacement has no fallible work after its first write.

Group B, subordinate-owner inventory and release races: prove `freeze()` fences deck
reserve; raster/prewarm/repair/capture/hydration/publication/generation/persistence
starts; renderer/draw/WebView/Foliate/visual-state and pending-owner callback
registration; foreground passive/live acquisition and mutation; new pointer admission;
and legacy semantic dispatch before return. Prove one freeze token and stable physical
rows
cover all 16 `ReaderLegacyInventorySource` values and, specifically, every
`ReaderPageTurnBundleSource` snapshot-cache owner, pending descriptor lease, descriptor
request/recipient, hydration recipient/job/scheduler job, publication entry/callback/
retained value/capacity listener/completion entry/scheduler job, raster-persistence job, raster-scheduler queued/pending/
active work, bitmap-source presented/live capture ownership and retained candidates,
Handler runnables, presented-frame requests, PixelCopy writes, JavaScript/visual-state/
draw callbacks, live-validation capture/worker/final fence, store operation, decoded
cache/encode pin/pending release, teardown owner, plus every
`ReaderForegroundWebViewOwnership` passive lease, cancel/restore closure, restoration
callback/lease, live/exclusive claim, readiness callback, retired-claim terminal
delivery, and mutation claim, plus each
legacy lifecycle-delivery and local deadline registration. Two complete snapshots with
successive snapshot sequences and identical discovery version/source set/resources
after callbacks establish the fixed point; aggregate counts do not qualify. Prove
post-freeze discovery preserves the complete `(domain, source, source-local token)`
identity, joins, and drains. Equal local opaque IDs from two sources or freeze/session
domains remain two rows, two drain requests, and two exact confirmations; same complete
identity duplicates converge to one row and registration. A confirmation carrying only
the local ID, wrong source, or wrong domain cannot confirm either row. Prove callback-
before-release and release-before-callback converge, and missing source/row/confirmation
yields `InventoryIncomplete`/`InvalidLegacyResource`/`LegacyDrainFailed` without
installation.

Group C, adopted-seed and owner-independent retirement provenance: prove at most one
directly reported visible predecessor, ambiguous proof yields `AmbiguousPredecessor`,
and neutral zero predecessor is truthful. Table-drive shell-cover `FrameHandoff`, native-
page `Deck`, curl/native-material `Deck`, and live/WebView `FrameHandoff`; reject every
mismatched owner/kind pair. The selected row's exact complete physical identity,
resource kind, binding, owner, and `AdoptedLegacy` provenance pass through frozen row →
seed → collision-free imported ledger key → retirement registration → initial decision,
with no cycle and no binding-only inference. For shell/native/curl/live, reject both a
mismatched requested lease and a mismatched physical lease independently even when breadth
ordering passes; neutral permits only lawful no-owner `None`/`ChromeOnly`. Native initial
`textureGeneration = 0` is accepted at origin/checkpoint/decision/barrier and is not a
migration signal. No legacy identity receives
`TransitionOwned`, and the coordinator-global resource key never reuses a source-local
opaque token. Prove adopted release uses a session issuer without an active transition;
retirement ordering ignores owner/operation IDs; duplicate/early/out-of-order confirmations
advance the private exact fence correctly; its sanitized projection exposes only prefix
presence, bounded gap count, and capacity category; at most 32 gaps/tombstones exist; active
attempts/registrations take precedence; and overflow rejects new
ownership without evicting an unconfirmed fence. Equal local IDs under different sources
import independently, each exact composite identity confirms and releases once, and a
duplicate of the same complete identity cannot allocate or release a second registration.

Group D, synchronous semantic receipt identity:

```kotlin
@Test
fun synchronousFoliateReceiptUsesItsExecutableHandleAndDedicatedSlotOnce() {
    val fixture = semanticFixture(callbackBeforeReturn = true)
    fixture.synchronize(commandFor(fixture.transitionId, fixture.requestHandle))
    assertTrue(
        fixture.transitionId == fixture.singleReceipt.originatingTransitionId,
        "semantic receipt transition identity mismatch"
    )
    assertTrue(
        fixture.slotId == fixture.singleReceipt.semanticCommandOrigin.slotId,
        "semantic receipt slot identity mismatch"
    )
    assertEquals(1, fixture.coordinator.maxAdvanceDepth)
    assertEquals(
        listOf("reserve-slot", "take-handle", "invoke-private-request", "buffer-callback", "return-accepted", "consume-slot", "enqueue", "reduce"),
        fixture.trace
    )
    assertEquals(0, fixture.activeHandleCount)
    assertEquals(0, fixture.activeSlotCount)
}
```

Also prove every page/cover/TOC/search/bookmark/annotation/jump command executes the
private request selected by its opaque handle after legacy dispatch is suppressed. Neutral
bootstrap alone registers before `ReadyToCommit`; null registration performs no install or
partial switch and exact cleanup reaches complete restoration or `ActivationBlocked`, while
successful install transfers and consumes that exact handle with no second registration.
Source metadata alone cannot execute or reconstruct a destination. Before the single
`RequestSemanticSynchronization` command, the reducer records exactly
`SemanticSynchronization`; missing, duplicate/consumed, expired, superseded, wrong-session,
registry, slot-capacity, and execution failures preserve bounded reasons but reject only
that stage before mutation. The production adapter installs `BeforeMutation`, moves to
`InvokingWithoutCallback` immediately before the first possible Foliate mutation, buffers one
fixed latest receipt plus saturating callback category/violation status while `synchronize`
remains on-stack, and interprets the result before exposing anything. `Accepted` with one
callback flushes once or waits for one later callback under the existing exact deadline;
two/3+ callbacks flush only latest authority once plus one bounded fatal fact. Pre-mutation
`Rejected`/throw produces only typed rejection. Callback then result conflict, rejection then
late callback, post-mutation failure, or arbitrary duplicates replace latest evidence,
saturate without collection growth/throw, and queue one fatal violation before fail-visible
release-only. Concurrent
commands receive distinct callback closures/slots; cross-order callbacks cannot consume
each other; unsolicited callbacks consume none. Wrong-slot, duplicate, stale, and
untagged settlement receipts cannot satisfy proof, while unsolicited untagged
same-publication relocation remains authoritative. Prove 16-handle, 8-active-slot, and
32-out-of-order-tombstone limits and retirement on callback, rejection/throw,
supersession, deadline, replacement, and close.

Group E, material allocation and typed synchronous rejection: prove semantic proof emits
`AllocateMaterialBinding`, not raster/deck work; allocation returns strictly fresh
monotonic preparation/raster/texture generations; publication, profile, session, or
binding mismatch yields `MaterialAllocationRejected`; `RequestRasterPreparation` and
`ReserveDeck` accept only the allocation's exact binding; and every rejection occurs
with zero physical prepare/reserve calls. Every private semantic handle/registry/slot failure
and every execution failure proven before the mutation boundary maps synchronously to
`CommandRejected(transitionId, SemanticSynchronization, boundedInternalReason)` for the one
encapsulating command; those internals are never phase stages. Post-mutation uncertainty uses
the fatal Group D contract instead. Material allocation rejection,
timer binding rejection/null/throw, and each equivalent command-stage failure appends one
exact `CommandRejected(transitionId, stage, boundedReason)` to FIFO while `advancing`. The
reducer must exhaustively validate active identity and a currently pending exact stage,
consume that singleton, publish a retryable
fail-visible terminal outcome before cleanup/release, preserve truthful retained owner,
and expose Retry/close. Each applicable phase admits a finite stage set and includes
`CommandRejected` ingress; emission records a zero-or-one pending set before the call,
exact success clears it before phase advance, and terminal/abort/close/Retry cleanup leaves
none. It may not drop rejection, reduce recursively, continue without a
timer, infer a generic timeout, or substitute `DeadlineExpired`; delayed-after-advance,
wrong-stage, stale, and duplicate rejection facts are inert.

Group F, exact frame target and two-phase combined publication:

```kotlin
@Test
fun preparedFrameDoesNotPublishSuccessBeforeCombinedCommitAcknowledgement() {
    val fixture = activatedPageEntryFixture()
    fixture.completeSemanticAllocationRasterAndDeckAndPrepareExactTarget()
    fixture.reportMatchingPreparedFrame()
    assertEquals(ReaderTransitionPhaseKind.Committing, fixture.activePhase)
    assertNull(fixture.lastOutcome)
    assertTrue(
        fixture.predecessorOwner == fixture.visibleOwner,
        "prepared frame must retain predecessor owner"
    )
    assertEquals(1, fixture.issuedCombinedCommitCount)
    assertFalse(fixture.timerCancelled)
    assertFalse(fixture.predecessorReleased)
}

@Test
fun synchronousCombinedCommitAcknowledgementIsQueuedNonReentrantly() {
    val fixture = activatedPageEntryFixture(publicationResultBeforeReturn = true)
    fixture.completeThroughMatchingPreparedFrame()
    assertEquals(1, fixture.coordinator.maxAdvanceDepth)
    assertEquals(
        listOf("publish-committing", "issue-commit", "return-applied", "enqueue-applied", "reduce-applied"),
        fixture.publicationTrace
    )
}
```

Prove the journal first publishes an awaiting-target contract whose only proof is
`FrameTargetPreparation`. The coordinator selects admitted Deck or ledger-allocates
FrameHandoff and emits `PrepareFrameTarget` with exact sealed kind specification and
registration. The adapter binds physical state to that registration, allocates no
ownership/retirement identity, and returns `FrameTargetPrepared` through FIFO. Only its
exact match stores target and permits `RequestFramePresentation`; rejection,
supersession, and close retire handle and release registration once.

Prove the kind contracts: shell uses typed host token, cover/publication/viewport/profile
identity, geometry, positive request sequence, and FrameHandoff; live uses typed handoff
token, direction/claim/publication/viewport/profile identity, geometry, positive request
sequence, and FrameHandoff; native/curl use exact allocation and admitted Deck, with
native explicit token-state and curl exact gesture/settlement. No pre-command frame
sequence exists; only callback `frameOwner` evidence has presented-frame sequence. Exact
transition/session/publication/binding/kind/resource owner+kind/geometry/handle/request
validation is mandatory. No adapter polls, substitutes, fabricates, replaces, or defaults
retirement order.

Consuming exact `PreparedFrame` removes that proof and publishes `Committing` awaiting
only `OwnerAndInputPublicationAcknowledgement`, admitting only `SuccessorPublication`,
with Applied/Rejected as its exact protocol callbacks plus `CommandRejected` stage ingress,
then issues only combined commit. Exact `Applied(Successor)` publishes
success before timer cancellation and predecessor release. Rejected retains predecessor
and releases successor only; wrong acknowledgements are inert.

For operations requiring input revocation/narrowing, prove acceptance first emits only
retained-owner publication and awaits the same acknowledgement proof. Before
`Applied(Retained)`, semantic/allocation/raster/deck/target/frame and attempt-timer calls
are all zero. Applied returns to pre-work phase without successor success; Rejected
terminates before any successor work. No activated `ApplyInputLease` exists.

Group G, retained fact-only timer and lifecycle compatibility: after activation, each
attempt has exactly one retained existing command-scoped physical timer using the
unchanged Section 8.6 duration. For narrowing operations it is not bound while retained
publication is pending; exact `Applied(Retained)` returns to pre-work, then the timer
binds to exact `ReaderTransitionId` before the first timer-requiring command. Its callback
can only enqueue that attempt's typed `DeadlineExpired` fact through FIFO; it cannot
mutate presentation/input/release,
perform local `Retry`, or extend/rearm except when the immutable command contract
explicitly permits matching progress. The production `ReaderTransitionClock`
scheduling port remains inactive. Prove `task6TimerExposesTransferSnapshotWithoutEnablingCoordinatorClock`:
`snapshotForTask7Transfer` returns the exact registration/current next expiry and does
not arm or transfer to the coordinator clock. Prove expiry enqueues one exact fact, supersession,
settlement, rejection, and close cancel the one registration, and activation,
supersession, and close have no zero-owner interval while deadline-requiring work exists
and no two-owner interval. Prove wrong-ID/stale/duplicate timer callbacks are inert. On
release-only entry, atomically replace/fence the attempt registration with one exact two-second
cleanup deadline under opaque cleanup ID plus generation. Its sole callback queues
`ReleaseOnlyCleanupDeadlineElapsed` through the release-only sink; it is cancelled only after
cancellation and ledger accounting are terminal, and process close fences it without a Task 6
persisted wake or recreation. Prove
the existing lifecycle normalizer remains the sole ordered ingress owner, every
overlapping legacy presentation/input/material consequence is suppressed in the atomic
install, and its compatibility adapter emits safety facts only. Those facts may
fence/cancel unsafe physical work but cannot create restore/reflow/recovery, change
deadline/wake policy, or mutate owner/input directly.

Group H, destructive-drain restoration: failure before first drain exposes unchanged
`Legacy` only after accepted complete unfreeze; unfreeze rejection enters frozen
`ActivationBlocked`. Failure after the first drain enters
`RestoringLegacy`, keeps both consequence routes and egress closed, denies page input,
retains only a valid proven predecessor, and invokes all 16 checkpoint sources. Prove
one 3-second restoration deadline that remains active after request issuance and
synchronous accepted returns until every exact asynchronous source confirmation and the
final atomic `commitRestoredLegacy` applied result. This is covered by
`restorationDeadlineSurvivesUntilAllAsynchronousConfirmations`. Prove the one complete
snapshot contains legacy routes, lifecycle/deadline owners,
visible owner, and physical lease. Missing/failed confirmation, expiry, invalid
predecessor, or commit rejection enters `ActivationBlocked`, never `Legacy`; Retry can
only repeat the in-memory restoration and close enters `ReleaseOnly`.

Group I, no fallback and production composition: `productionCompositionContainsNoShadowOrNoOpActivatedPort`
proves `KomikkuReaderNativeFrameHost` can install only the capability-authenticated
real-adapter package and that public Boolean/`complete()`/no-op/rejecting fixture
packages cannot cross the production installation type. `postInstallLegacyConsequenceWriterIsUnreachable`
proves no `Shadow`/`LegacyOnly` route or legacy semantic/material/frame/input consequence
writer remains reachable. Inject semantic, allocation, raster, deck, frame, successor
publication, and input failures after install and prove state never returns to
`Legacy`, no legacy handler runs, retained owner/diagnostic remains truthful, and
resources continue to the sink. `ActivationBlocked` is reachable only from failed
pre-install restoration and cannot activate coordinator routes.

Group J, close and replacement in every activation phase: close from `Legacy`, `Freezing`,
`DrainingLegacy`, `ReadyToCommit`, `RestoringLegacy`, `ActivationBlocked`, `Activated`,
and repeated `ReleaseOnly`. Prepare one exact retained physical cleanup deadline, then one
atomic reduction captures the exact pre-close active ID and outstanding owned-work cancellation
in `ReaderReleaseOnlyCleanupState`, installs opaque cleanup ID plus generation and `Armed`
deadline status, clears active and pending stages, closes normal ingress, publishes permanent
`ReleaseOnly`, and emits at most one `CancelOwnedWork(cleanupKey, preCloseTransitionId)`.
No-active close records `NotRequired` and emits no fabricated cancellation; close during
sequence 1 targets sequence 1, never the existing `PublicationClose` identity. Dedicated
applied/rejected cleanup facts must match the sole pending key and ID. Applied/rejected makes
cancellation terminal; release accounting independently reaches `EmptyReleased` or
`TerminalFailure`. Only after both are terminal may deadline cancellation record
`CancelledAfterTerminalAccounting`; exact elapsed records `CloseDrainTimeout`, terminalizes
unresolved release attempts without reissue, and leaves lawful late confirmations admissible.
Duplicate/stale/wrong-key/wrong-ID/wrong-generation/wrong-attempt/post-completion facts are
inert. The sink admits only exact cleanup outcomes, deadline elapsed, attempt-keyed release
confirmation/rejection/throw/port violation, resource observation, and `ReleaseResource`; all
semantic/material/frame/input/ordinary-timer/Retry/restoration work is rejected.

Every physical release command, ordinary or cleanup-owned, carries mandatory
`ReaderPhysicalReleaseAttemptId` and exact registration; cleanup key is optional grouping.
Close atomically attaches all unresolved ordinary successor/terminal attempts to its key and
never emits another command. Generic and imported-legacy adapters use fixed latest-confirmation/
saturating-count/violation state. Accepted+callback confirms Released; Rejected without
callback proves no effect; throw without callback is ambiguous. Callback before rejection/
throw remains authoritative Released plus one bounded violation; late callback promotes
rejected/ambiguous; duplicate/3+ confirmation is idempotent, latest-authoritative, bounded,
non-throwing, and collection-free. Legacy additionally matches complete physical identity.
Active attempt rows outrank bounded tombstones; exact late confirmation never causes another
physical release. Retirement-fence rendering/equality diagnostics expose only sanitized
prefix-presence/count/capacity categories, never raw sequence values.

`PublicationReplaced` is a fact, never a twelfth operation, and uses the same transaction.
It retires handles/slots/timers and releases old-publication resources once; adopted uses
session authority and neutral emits no baseline release. Replacement or process close after
`ReleaseOnly` cannot replace the cleanup record or emit cancellation/release again. Process
close fences/cancels the exact cleanup deadline, drains only already-queued exact cleanup/
release facts within budget, records unresolved pending cancellation as
`ProcessClosedPending` and unresolved release rows as `ProcessClosedUnreleased`, and never
persists/reconstructs cleanup, timer, wake, or failure state. A replacement publication begins
a fresh session/baseline with no inherited cleanup state.

The following named RED tests are release blockers and map one-to-one to the amended
contract:

| Test | Exact expected behavior |
|---|---|
| `adoptedBaselineFirstSuccessorStartsAtOneWithoutParentAndReleasesAfterApplied` | Adopted origin is not an operation; first real successor is sequence 1/no parent and exact Applied replaces baseline before one session-authority release. |
| `neutralBaselineBootstrapUsesFoliateBindingAndExistingOperation` | Neutral has no owner/binding/resource; private handle is reserved pre-install and transferred atomically; existing bootstrap operation consumes it and obtains binding only from command-bound live Foliate before allocation. |
| `neutralBootstrapRegistrationNullBlocksActivationBeforeAtomicInstall` | Null pre-install register creates no operation/snapshot write/route or egress switch; activation-owned state retires once and existing unfreeze/restoration either restores truthfully or blocks. |
| `unsolicitedRelocationFromAdoptedBaselineRetainsThenReleasesExactlyOnce` | Authoritative untagged relocation retains adopted baseline through retained/successor acknowledgements and releases it once after Applied. |
| `unsolicitedRelocationFromNeutralBaselineNeedsNoRetainedPublication` | Authoritative untagged relocation starts sequence 1/no parent and uses its exact binding without fabricating retained owner/resource publication. |
| `initialOriginCannotFabricateOperationOrParent` | Neither origin, seed, installation, nor activation protocol can mint an operation ID or parent. |
| `transitionSequenceIsMonotonicAcrossAbortRetryAndRestore` | Every later real operation gets the next sequence and prior authentic operation parent; no terminal/restoration path reuses identity. |
| `retryAndRestorationPreserveTruthfulInitialBaseline` | Retry/restoration preserves adopted or neutral truth and never resurrects resource/frame proof; Task 7 implementation remains excluded. |
| `adoptedBaselineReleaseWaitsForSuccessorPublicationAcknowledgement` | Preparation, command return, rejection, timeout, abort, and Retry cannot release adopted baseline before exact Applied. |
| `closeFromAdoptedBaselineReleasesOnceWithoutTransitionBorrowing` | Close enters permanent ReleaseOnly and uses session release authority, not fabricated transition ownership. |
| `closeFromNeutralBaselineEntersReleaseOnlyWithoutResourceCommand` | Neutral close creates no binding/resource/release fiction and remains permanently release-only. |
| `initialOriginEqualityDiagnosticsExposeOnlyBoundedCategories` | Equality checks use a sanitized category projection rather than whole sensitive baseline values; no opaque identity leaks. |
| `sensitiveInitialWrappersRenderOnlyRedactedConstants` | Direct rendering of every sensitive lease/origin/semantic invocation/cleanup/deadline/physical-release-attempt/adapter-state/failure-tombstone/retirement-fence/keyed-cancellation type is fixed redacted output; fence assertions use sanitized projections only. |
| `sensitiveInitialEqualityFailureUsesSanitizedProjection` | A deliberately failed sanitized-projection assertion contains bounded categories only; whole sensitive values are forbidden as expected/actual/message operands. |
| `initialLeaseCannotCarryClaimedGestureOrTransitionId` | The initial-only sealed lease has no claimed-gesture or transition-identity case; ordinary transition leases cannot populate an origin. |
| `curlAdoptionFencesLegacyGestureAndUsesIdentityFreeLease` | Freeze fences/cancels every legacy curl gesture before adoption, which installs only identity-free None or ChromeOnly. |
| `failedNeutralBootstrapSemanticResolutionQueuesTypedRetryableFact` | After sequence-1 admission, any private handle/registry/slot failure or execution failure proven pre-mutation queues exact CommandRejected at the one SemanticSynchronization stage, terminates visibly as retryable, and is not translated to timeout; pre-install register failure uses the activation gate instead. |
| `semanticMissingHandleRejectsSingleSynchronizationStage` | Missing handle preserves its bounded reason but rejects exact transition at pending SemanticSynchronization; no handle-resolution stage exists. |
| `semanticExpiredHandleRejectsSingleSynchronizationStage` | Expired handle maps to pending SemanticSynchronization with bounded ExpiredHandle reason. |
| `semanticConsumedHandleRejectsSingleSynchronizationStage` | Already-consumed handle maps to pending SemanticSynchronization without another stage or execution. |
| `semanticWrongSessionHandleRejectsSingleSynchronizationStage` | Wrong-session handle maps to the exact command's SemanticSynchronization stage and performs no Foliate mutation. |
| `semanticRegistryFailureRejectsSingleSynchronizationStage` | Registry rejection is an internal reason under SemanticSynchronization, not reducer-visible advancement. |
| `semanticSlotCapacityRejectsSingleSynchronizationStage` | Slot-capacity failure preserves its reason under SemanticSynchronization and retires any acquired handle/slot exactly once. |
| `semanticExecutionFailureRejectsSingleSynchronizationStage` | Pre-mutation executable rejection/throw maps synchronously to SemanticSynchronization and exact bounded reason with no mutation/callback. |
| `semanticSynchronousCallbackThenAcceptedBuffersAndFlushesReceipt` | Synchronous receipt remains buffered until Accepted returns, then queues exactly once through FIFO. |
| `semanticAcceptedThenAsynchronousCallbackQueuesOneReceipt` | Accepted without synchronous receipt awaits and accepts one later callback for that invocation. |
| `semanticRejectedWithoutCallbackQueuesExactSynchronizationRejection` | Rejected proves no mutation/callback and queues exact CommandRejected at SemanticSynchronization. |
| `semanticThrowBeforeMutationQueuesExactSynchronizationRejection` | Proven pre-mutation throw becomes the bounded exact synchronization rejection and permits no later callback. |
| `semanticCallbackThenRejectedPreservesReceiptAndClosesFailVisible` | Exact receipt is preserved/queued before fatal typed safety fact; rejection cannot clear it, later semantics are blocked, and release-only begins. |
| `semanticCallbackThenThrowPreservesReceiptAndClosesFailVisible` | Callback evidence survives ambiguous throw and drives the same no-fallback fatal close. |
| `semanticRejectedThenLateCallbackIsFatalContractViolation` | Late receipt is preserved and exact violation closes the session rather than reviving rejected work. |
| `semanticDuplicateCallbackIsFatalContractViolation` | Fixed state retains latest exact authority, consumes success at most once, emits one bounded fatal fact, and never stores a receipt list. |
| `semanticThreeOrMoreSynchronousCallbacksSaturateWithoutThrowing` | Third and later callbacks replace latest receipt and saturate at ThreeOrMore without constructor/callback exception. |
| `semanticDifferingDuplicateCallbacksRetainLatestAuthority` | Differing callback payloads leave the final callback as the one exact receipt queued before fatal close. |
| `semanticCallbackCountSaturatesAtThreeOrMore` | Arbitrarily many callbacks keep fixed state and bounded count/category. |
| `semanticDuplicateCallbackPathNeverThrows` | Callback ingress is total and cannot propagate a size/precondition exception into Foliate. |
| `semanticFatalFlushesLatestReceiptOnce` | After result return, latest exact receipt queues once followed by one bounded violation, never every historical receipt. |
| `semanticAcceptedWithoutCallbackUsesExistingExactTimeout` | Accepted silence terminates only through the existing exact operation deadline. |
| `delayedSemanticSynchronizationRejectionAfterStageClearIsInert` | Exact semantic success or terminal advancement clears SemanticSynchronization before delayed rejection arrives. |
| `duplicateSemanticSynchronizationRejectionAfterStageClearIsInert` | First exact rejection consumes SemanticSynchronization; duplicate rejection cannot affect later state. |
| `retryAfterFailedNeutralBootstrapUsesNextAuthenticIdentity` | Retry after failed first bootstrap allocates the next sequence and authentic prior-operation parent without reusing/fabricating identity. |
| `materialAllocationRejectionQueuesTypedFactAndTerminatesRetryable` | Synchronous material rejection queues exact stage/reason through FIFO and publishes retryable failure before successor cleanup. |
| `timerBindingRejectionQueuesTypedFactWithoutTimeoutSubstitution` | Bind null/rejection/throw queues TimerBinding rejection before work; no timerless continuation or DeadlineExpired substitution occurs. |
| `delayedCommandRejectionAfterStageAdvanceIsInert` | Exact success clears the singleton pending stage before phase advance, so a delayed rejection cannot terminate the later phase. |
| `duplicateCommandRejectionIsInertAfterPendingStageConsumed` | First matching rejection consumes pending stage and terminates; a duplicate finds no matching active stage and is inert. |
| `timerBindingRejectionRequiresPendingTimerBindingStage` | Timer rejection is admitted only for active identity plus pending TimerBinding; wrong-stage and already-cleared facts cannot affect work. |
| `mismatchedRequestedLeaseRejectedForShellAdoption` | Shell requested and physical leases are each checked against exact shell owner/binding; breadth alone cannot admit NativePage. |
| `mismatchedRequestedLeaseRejectedForNativeAdoption` | Native requested and physical leases are each checked against exact native owner/binding; breadth alone cannot admit CoverActions. |
| `mismatchedRequestedLeaseRejectedForCurlAdoption` | Curl requested/physical leases are both owner-compatible and restricted to None/ChromeOnly after gesture fencing. |
| `mismatchedRequestedLeaseRejectedForLiveAdoption` | Live requested and physical leases are each checked against exact live owner/binding and remain no broader than ChromeOnly. |
| `initialNativePageLeaseAllowsTextureGenerationZero` | Initial native-page generation zero is lawful at origin/checkpoint/decision/barrier under current authoritative proof and creates no migration claim. |
| `publicationReplacementFromAdoptedBaselineClosesAndReleasesOnce` | Replacement creates no operation, atomically captures active cleanup when present, enters permanent ReleaseOnly, emits keyed cancellation once, and releases adopted baseline once by session authority. |
| `publicationReplacementFromNeutralBaselineClosesWithoutBaselineRelease` | Neutral replacement creates no operation or resource fiction, uses the same keyed cleanup transaction when active work exists, and remains permanently release-only. |
| `closeDuringSequenceOneOperationCapturesCleanupAndCancelsOnce` | Close captures sequence-1 before clearing active state and emits one cleanup-keyed cancellation; the close identity is not its own target. |
| `releaseOnlyCancellationRejectionRecordsFailClosedCleanupFailure` | Matching rejected callback or synchronous Rejected/throw consumes Pending once, makes cancellation terminal with bounded reason, performs no Retry/reopen, and cannot report successful cleanup. |
| `duplicateReleaseOnlyCancellationOutcomeIsInert` | Duplicate applied/rejected outcome after Pending is consumed cannot mutate cleanup or emit cancellation/release twice. |
| `staleReleaseOnlyCancellationOutcomeIsInert` | Wrong key/ID, replaced-session, or post-completion cleanup outcome is inert except separately lawful resource disposition. |
| `closeWithNoActiveOperationEmitsNoCancellation` | No-active adopted/neutral close records NotRequired and emits no fabricated CancelOwnedWork even if close is sequence 1. |
| `releaseOnlyRejectsWorkAndCannotReopenTransition` | ReleaseOnly admits only matching cancellation/deadline/release-failure/resource outcomes and rejects semantic/material/frame/input/ordinary-timer/Retry work permanently. |
| `replacementDuringActiveOperationUsesSameReleaseOnlyCleanupTransaction` | Replacement allocates no operation and captures/emits the same one-record keyed cleanup transaction as close. |
| `replacementCannotReplaceExistingReleaseOnlyCleanupRecord` | Replacement after ReleaseOnly cannot overwrite the first cleanup record or re-emit cancellation. |
| `processCloseDoesNotReemitPendingCleanupCancellation` | Process close freezes existing cleanup, emits no duplicate cancellation/release, fences the deadline, and converts unresolved accounting without persistence/wake. |
| `releaseOnlyCleanupDeadlineFiresAndRecordsCloseDrainTimeout` | Exact current-generation elapsed fact records nonretryable timeout, terminalizes unresolved releases, and never reopens/falls back. |
| `releaseOnlyCleanupDeadlineCancelsAfterCancellationAndLedgerTerminal` | Deadline cancellation is impossible until cancellation is terminal and ledger is EmptyReleased or TerminalFailure; Accepted, Rejected, and throw become the exact cancelled/fail-closed deadline statuses without reopen. |
| `releaseOnlyCleanupDeadlineBindFailureFailsClosed` | Binding null/rejection/throw cannot prevent visual close and records bounded fail-closed deadline state. |
| `staleReleaseOnlyCleanupDeadlineGenerationIsInert` | Wrong cleanup ID or generation cannot time out current cleanup. |
| `duplicateReleaseOnlyCleanupDeadlineElapsedIsInert` | First exact elapsed consumes Armed once; duplicate delivery cannot mutate accounting again. |
| `processCloseFencesReleaseOnlyCleanupDeadlineWithoutWake` | Process close cancels/fences current deadline and creates no persisted Task 6 wake/recreation. |
| `completedCleanupCannotReceiveDeadlineElapsed` | Post-completion elapsed callback is admission-fenced and inert. |
| `releaseCommandRejectedRecordsNoEffectWithoutReissue` | Rejected guarantees no physical effect/callback, records attempt-keyed RejectedNoEffect with optional cleanup grouping, and never issues again. |
| `releaseCommandThrowBeforeEffectBoundaryRecordsAmbiguousFailure` | Unproved pre-effect throw records attempt-keyed AmbiguousFailure and never marks released. |
| `releaseCommandThrowAfterPossibleEffectRecordsAmbiguousFailure` | Post-possible-effect throw remains attempt-keyed ambiguous; late exact confirmation is authoritative without reissue. |
| `duplicateOrStaleReleaseFailureOutcomeIsInert` | Wrong attempt/key/generation/registration/order and duplicate post-terminal failure facts cannot alter active accounting. |
| `ambiguousReleaseFailureNeverIssuesSecondPhysicalRelease` | Ambiguous attempt permanently blocks a second generic or legacy release command. |
| `releaseFailureBlocksCompletionUntilCleanupTimeout` | Failure prevents EmptyReleased; deadline terminalizes it to TerminalFailure with bounded attempt tombstone and no retry. |
| `ordinaryActivatedSuccessorReleaseRejectedTracksAttemptWithoutCleanup` | Ordinary successor rejection is truthful before cleanup, with mandatory attempt ID and null cleanup grouping. |
| `ordinaryTerminalReleaseThrowTracksAttemptWithoutCleanup` | Ordinary terminal throw records AmbiguousFailure under its attempt without inventing cleanup. |
| `unresolvedOrdinaryReleaseAttemptTransfersIntoCleanupWithoutReissue` | Close attaches cleanup key to unresolved attempt row and emits no second release command. |
| `genericReleaseOrderingMatrixIsAttemptExact` | Generic Accepted/Rejected/throw crossed with zero/one/two/3+ callbacks follows exact attempt matrix. |
| `legacyReleaseOrderingMatrixIsAttemptAndPhysicalIdentityExact` | Same exhaustive matrix additionally requires exact imported physical identity. |
| `releaseCallbackThenRejectedKeepsReleasedAndReportsViolation` | Callback evidence advances Released; subsequent Rejected adds one bounded violation and cannot regress state. |
| `releaseCallbackThenThrowKeepsReleasedAndReportsViolation` | Callback evidence advances Released; throw adds one bounded violation and cannot create ambiguity. |
| `releaseLateCallbackPromotesRejectedOrAmbiguousAttempt` | Late exact callback promotes either failure state to Released plus bounded violation, without reissue. |
| `releaseDuplicateAndManyCallbacksSaturateWithoutThrowing` | Arbitrary callbacks retain fixed latest evidence, saturate at ThreeOrMore, remain idempotent, and never throw/grow a list. |
| `releaseLatestConfirmationRemainsAuthoritative` | Differing confirmations retain and flush latest exact attempt/resource evidence once per coalesced drain. |
| `resourceRetirementFenceRenderingIsContentFree` | Direct snapshot rendering is a fixed redacted constant with no session/epoch/sequence values. |
| `resourceRetirementFenceFailingEqualityUsesSanitizedProjection` | Deliberate failure renders only prefix-presence/gap-count/capacity categories. |
| `resourceRetirementFenceAssertionsNeverRenderRawSequences` | Tests use confirms(order) booleans and sanitized projections, never raw sequence assertEquals operands/messages. |
| `preparedFrameDoesNotPublishSuccessBeforeCombinedCommitAcknowledgement` | Matching proof publishes successor `Committing` retaining predecessor truth and only the commit command; no success, committed replacement, timer cancellation, or predecessor release. |
| `synchronousCombinedCommitAcknowledgementIsQueuedNonReentrantly` | A synchronous port result becomes an immediate FIFO fact while `advancing`; maximum reduce depth remains one. |
| `rejectedCombinedCommitRetainsPredecessorAndReleasesOnlySuccessor` | Rejected guarantees no snapshot write, publishes terminal failure first, retains predecessor owner/input, and releases only prepared successor. |
| `acceptedCombinedCommitPublishesSuccessBeforePredecessorRelease` | Exact matching `Applied` publishes success/committed, then cancels the timer and issues exactly one predecessor release. |
| `staleOrWrongCombinedCommitAcknowledgementIsInert` | Wrong/stale/duplicate/cross-transition/cross-resource/untagged acknowledgement cannot alter state or release predecessor. |
| `bindingOnlyFrameRequestIsUnrepresentable` | Shared API requires coordinator-issued `PrepareFrameTarget` and matching prepared sealed target before presentation; no binding-only request constructor/path exists. |
| `shellCoverCommandCarriesExactTokenGenerationAndFrameResource` | Coordinator-led `PrepareFrameTarget` supplies exact FrameHandoff; prepared shell target carries typed host token, cover/publication/viewport/profile identity, geometry, positive request sequence, and no material-allocation fiction. |
| `liveExposureCommandCarriesExactHandoffTokenAndFrameResource` | Coordinator-led preparation supplies exact FrameHandoff; live target carries typed handoff token, direction/claim/publication/viewport/profile identity, geometry, and positive request sequence. |
| `nativeFrameCommandConsumesExactDeckTargetWithoutPolling` | Native consumes the exact admitted `Deck` target and never reads current candidate/decision or substitutes same binding. |
| `curlSettlementFrameCommandPreservesGestureAndExactDeckTarget` | Curl command/proof preserve exact gesture, settlement, and PlayLikeCurl deck target even when stable native becomes owner. |
| `sameBindingDifferentFrameTargetCannotSatisfyPreparedFrame` | Same binding with a different handle/token/kind identity/geometry/registration is stale and cannot reach commit. |
| `preparedFrameCannotFabricateResourceRegistration` | Prepared proof must echo the pre-command registration; no transition-sequence retirement default or replacement registration is accepted. |
| `transitionalInputChangeUsesAtomicRetainedOwnerPublication` | Acceptance emits only retained-owner publication; zero semantic/allocation/raster/deck/target/frame/timer work occurs before exact `Applied(Retained)`, which returns to pre-work without success; rejection terminates. |
| `restorationDeadlineSurvivesUntilAllAsynchronousConfirmations` | Three-second deadline remains active through request returns and ends only after every exact source confirmation plus final atomic restoration result. |
| `task6TimerExposesTransferSnapshotWithoutEnablingCoordinatorClock` | Exact transfer snapshot is observable, but Task 6 neither arms the coordinator clock nor transfers scheduling. |
| `productionCompositionContainsNoShadowOrNoOpActivatedPort` | Production host constructs only capability-authenticated real adapters; Shadow/LegacyOnly, no-op `complete()`, and rejecting fixture packages cannot install. |
| `postInstallLegacyConsequenceWriterIsUnreachable` | Every post-install semantic/material/frame/input path resolves only to coordinator adapters or release sink; no legacy writer/fallback is reachable. |

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderTransitionActivationTest" \
  --tests "paige.navic.ui.screens.reader.ReaderTransitionAtomicCutoverTest" \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionCoordinatorTest" \
  --tests "paige.navic.ui.screens.reader.ReaderTransitionReleaseLedgerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderDeckAdmissionCutoverTest" \
  --tests "paige.navic.reader.ReaderResumableTransitionModelTest" \
  --tests "paige.navic.reader.ReaderControllerTest"
```

Expected RED reasons: the activation unit/tests and atomic installation-with-initial-
publication barrier are absent; the gateway is Shadow-only; destructive drain has no
restoration/blocked protocol; physical inventory is deck-level and omits bundle-source
and foreground-ownership subowners; legacy resources use transition-dependent key/
retirement fences; external semantic commands lack executable handles and isolated
bounded slots; semantic receipts have no exact slot tag; material commands consume
nullable generations; frame requests are binding-only, lack coordinator-led
`PrepareFrameTarget`/awaiting-target proof, and let adapters allocate or substitute target
ownership; native publication polls current candidates or fabricates registration
identity; `Committing` does not await the explicit publication-acknowledgement proof and
matching `PreparedFrame` publishes success before the fallible physical transaction is
acknowledged; the generic dispatcher discards a `Unit`/accepted-only result instead of
queuing an exact applied/rejected fact; other synchronous command rejection is dropped or
misreported as timeout instead of entering FIFO as typed stage/reason; semantic synchronization
incorrectly exposes handle/slot/execution as separate pending stages even though one
`RequestSemanticSynchronization` command has no reducer-visible substage advancement, and it
exposes synchronous callbacks before interpreting Accepted/Rejected/throw, loses contradictory
receipt evidence, lacks session-fatal duplicate/late callback handling, uses a bounded receipt
list whose third callback can throw, loses latest differing authority, and does not use the
existing timeout for Accepted silence;
active state has no
represented pending stage, phase contracts omit `CommandRejected`, and delayed/duplicate/
wrong-stage rejection can terminate the wrong phase; neutral bootstrap performs nullable
registration only after install, so null cannot fail activation truthfully; initial leases can
carry transition-scoped claimed gestures, validate only physical rather than both requested
and physical owner compatibility, reject authoritative initial native texture generation
zero, and curl adoption does not prove legacy gesture
fencing; resource provenance is declared only in Android despite common origin use;
publication replacement is missing from exhaustive common fact/reducer behavior; close/
replacement clears active cancellation authority before `CancelOwnedWork`, while the
release-only sink rejects its outcome and has no bounded keyed cleanup record, retained
physical two-second cleanup deadline, cleanup generation fencing, or process-close deadline
contract; physical release lacks mandatory attempt identity outside cleanup, ordinary unresolved
attempt transfer, fixed callback/result buffering, callback-authoritative conflict promotion,
and generic/legacy late/duplicate/3+ ordering; Rejected/throw has no truthful no-reissue
attempt state or bounded timeout/process-close tombstone accounting; retirement-fence data-
class rendering/equality and raw sequence assertions leak values; sensitive
data-class rendering/assertions expose whole values; the caller can construct/supply the
initial journal and production signatures use a weaker port-package name; retained input
publication is not ordered before
successor semantic/material/physical/timer work and transitional input has a separate
unwired mutation; restoration can cancel its deadline after synchronous request returns;
production package completeness can be claimed by no-op/rejecting fixtures and
`KomikkuReaderNativeFrameHost` has not proved all Shadow/LegacyOnly writers unreachable;
initial route/owner/input/egress publication and successor owner/input are separate
consequences; and active Task 6 routes would have timer/lifecycle gaps or dual writers.
Record these as design prerequisites, not test flakiness.

- [ ] **Step 3: Implement common identity, receipt, material, and journal causality**

Implement the shared signatures above. First replace nullable/operation-only committed
state with `ReaderCommittedPresentation.Initial`/`Transition`. Define the origin-derived
sequence-zero/no-last-issued baseline shape, but make the activation barrier its sole
constructor/installer; no call site or `ReaderActivatedSessionInstallation` can provide a
journal. Centralize ID
allocation in one helper: next sequence is `lastTransitionSequence + 1`; parent is
`lastIssuedTransitionIdentity`; first therefore receives `1`/null, and every accepted
operation atomically advances both fields before commands. Add
`NoCommittedPresentation`, `FoliateAuthoritativeInitial`, and
`ReaderBootstrapNativePageIntent`; neutral close uses the explicit absence discriminant,
while bootstrap resolves its expected
binding only from its exact command-bound `FoliateDestinationCommitted` receipt. Make
all reducers branch explicitly on adopted versus neutral baseline for retained
publication, resource protection/release, unsolicited relocation, Retry, timeout, abort,
restoration facts, publication replacement, and publication close. Close/replacement first
prepares one exact retained Task 6 two-second physical deadline, then atomically captures the
exact pre-close active target in one bounded `ReaderReleaseOnlyCleanupState` keyed by opaque
cleanup ID plus generation, clears active/pending state, installs `Armed`, enters permanent
`ReleaseOnly`, and emits keyed `CancelOwnedWork` once only when a target exists. Dedicated
cancellation applied/rejected facts match cleanup key plus captured ID after active is gone;
release confirmation/rejection/throw/port-violation facts always match mandatory attempt ID plus
exact registration and may have null cleanup grouping before close. Close atomically attaches
unresolved ordinary attempts to its cleanup key without dispatching again; exact deadline elapsed
then matches that cleanup key. Cancellation and release ledger terminality are independent; cancel the
deadline only after both settle, while elapsed terminalizes unresolved release rows without
reissue. `PublicationReplaced` releases exact old-publication resources without allocating an
operation; branch adopted versus neutral baseline explicitly. Process close fences the timer,
converts unresolved accounting, and persists/reconstructs nothing. Do not add an operation
enum value or activation state.

Move the single `ReaderTransitionResourceProvenance` enum into commonMain and delete the
Android-only declaration so every common origin reference is source-set legal. Add
`ReaderInitialPresentationInputLease` as the only origin/checkpoint lease type; it has no
claimed-gesture or transition-identity variant. Keep `ReaderTransitionInputLease` and its
`ClaimedGesture` case for authentic operations only. Implement fixed redacted `toString()`
or explicit non-data-class redacted rendering/equality for every newly introduced sensitive
value type in the initial graph, including seed, composite physical identity, imported registration,
restoration checkpoint, decision, installation, committed transition, and journal; provide
a separate bounded
sanitized category projection for assertions; never compare/interpolate whole sensitive
values in diagnostics.

Migrate `ReaderTransitionResourceKey` and the
ledger to `ReaderTransitionResourceOwnerId` plus
`ReaderTransitionResourceRegistration`; coordinator-created resources use
`TransitionOwned`, legacy resources can use only the handoff seed, and every first
physical registration receives owner-independent monotonic retirement order. Release
issuer, tombstone advancement, and late confirmation must not require a current
transition. Make every `ReleaseResource` carry ledger-allocated mandatory
`ReaderPhysicalReleaseAttemptId` plus exact registration and optional cleanup grouping.
Install attempt state before generic/imported dispatch; ordinary successor/terminal attempts
exist before cleanup and transfer into it without reissue. Implement fixed latest-confirmation/
saturating-count/violation adapter state for both ports and the exhaustive Accepted/Rejected/
throw × callback ordering matrix. Callback evidence promotes to `Released` before any bounded
violation; rejected/ambiguous/timeout/process-close states remain non-reissuable and may be
promoted by late exact confirmation. Add bounded attempt tombstones with active precedence.
Replace retirement-fence data-class rendering with private raw state, exact explicit equality,
constant hash, fixed redacted rendering, `confirms(order)`, and sanitized projection only. Add
`MaterialBindingAllocated` to fact classification and liveness proof
without adding a twelfth operation. For operations requiring input narrowing, acceptance
first emits only retained publication and awaits
`OwnerAndInputPublicationAcknowledgement`; exact `Applied(Retained)` returns to pre-work,
while rejection terminates before semantic/allocation/raster/deck/frame/timer work.
Semantic proof then requests allocation; allocation may request raster; raster may request
allocated deck work.

After deck/frame prerequisites, publish an awaiting-target phase with only
`FrameTargetPreparation`. Coordinator selects admitted Deck or ledger-allocates
FrameHandoff, then emits `PrepareFrameTarget` with sealed exact specification and
registration. Matching FIFO `FrameTargetPrepared` stores target and alone permits
`RequestFramePresentation`; reject/supersede/close retires handle and releases exact
registration once. The adapter cannot allocate ownership/retirement order. Implement the
four sealed kinds, exact token-state and generation validation, positive request sequence,
and no pre-command frame sequence.

Consuming matching `PreparedFrame` removes that awaited proof and publishes retained
`Committing` awaiting exactly `OwnerAndInputPublicationAcknowledgement`, admitting
`SuccessorPublication`, with Applied/Rejected as exact protocol callbacks plus
`CommandRejected` ingress, then emits only `CommitOwnerAndInputLease`. Only
matching `Applied(Successor)` may publish success/committed, cancel timer, and then release
predecessor. Rejection retains predecessor truth and releases successor only. Keep exactly
eleven operations: target preparation and publication acknowledgement are proofs/facts/
commands, not operations.

Create an opaque executable request handle for every semantic intent and a bounded
session registry whose values are private Foliate closures. Reserve a dedicated slot
before taking/invoking the handle; pass slot ID plus transition ID through
`ReaderPresentationEventOrigin.SemanticCommand`; never derive either from active state
or let unsolicited delivery consume a slot. Add finite phase
`admissibleCommandStages` and zero-or-one active `pendingCommandStages`. Before command
emission publish the exact pending singleton and include `CommandRejected` in applicable
phase ingress. `RequestSemanticSynchronization` admits only one pending
`SemanticSynchronization` stage; handle lookup/take, registry validation, slot reservation,
and execution remain synchronous port internals whose bounded, proven pre-mutation failure
reasons map to that stage. Add the bounded `ReaderSemanticInvocationRecord` state machine: perform every
rejectable check in `BeforeMutation`, enter `InvokingWithoutCallback` immediately before the
first possible Foliate mutation, and buffer only fixed `latestAuthoritativeReceipt`, saturating
callback category, and violation status until `synchronize` returns. Every callback replaces
latest evidence and is collection-free/non-throwing. `Accepted` with one flushes once or waits
under existing deadline; two/3+ flush latest once plus one violation. Proven pre-mutation
`Rejected`/throw queues only exact `CommandRejected`; callback/result conflict, late callback,
post-mutation failure, or arbitrary duplicates preserve latest exact authority, saturate bounded
metadata, queue one `SemanticPortContractViolated`, block session semantic commands, retire
slot/handle once, and enter fail-visible release-only without fallback. A rejection never
clears/overrides an authoritative receipt. Exact semantic receipt/rejection/terminal advance
clears the single reducer stage; delayed ordinary rejection is inert. Other exact success
clears before phase advance, exact matching rejection consumes its stage terminally, and
terminal/abort/supersession clears active remnants before cleanup. Replacement/close first
captures the exact active cancellation target in the release-only record and then clears active
stage state. Retry starts empty. Every proven pre-mutation semantic internal failure, material
allocation rejection, timer binding rejection/null/throw, and equivalent command-stage
failure must append exact
`CommandRejected(transitionId, stage, boundedReason)` to FIFO while `advancing` remains
true. Add exhaustive reducer handling that validates active ID plus a currently pending
exact stage, consumes it once, publishes retryable
failure before cleanup/release, preserves truthful retained owner, and never substitutes
timeout/`DeadlineExpired` or proceeds timerless. Non-semantic synchronous callbacks append to
the coordinator FIFO while `advancing` remains true; semantic callbacks first obey the
invocation buffer/result matrix above. Retire handles/slots and bounded
out-of-order tombstones on every terminal path.

- [ ] **Step 4: Implement exhaustive activation, adoption, and installation**

Implement `ReaderSessionActivationCoordinator` and the exact sequence in the Task 6
activation contract. Aggregate collision-safe `(freeze/session domain, source, source-
local opaque token)` identities from binding reporter, deck host, raster preparation,
every named `ReaderPageTurnBundleSource` subordinate owner/scheduler/ledger/cache/store/
validation/teardown, `ReaderForegroundWebViewOwnership`, PlayLikeCurl renderer/Foliate
callbacks, host bridge, native publisher, native viewer container, semantic slot
registry, and input controller. Freeze every listed start, claim, mutation, admission,
dispatch, and callback-registration path before returning; repeat exact snapshots to a
versioned fixed point.

Before first drain, capture restart descriptors for every source. Drain every resource
except at most one directly proven visible predecessor and wait for confirmations that
match each complete composite identity, including post-freeze discoveries. If selected,
carry the row's exact owner, truthful resource kind, binding, identity, and provenance
through non-neutral seed, collision-free imported key, owner-independent registration,
and `AdoptedPredecessor` origin in that acyclic order. Validate both requested and physical
leases independently against its exact owner/binding as well as breadth ordering. Before
adopting curl, fence/cancel
every legacy claimed gesture and restrict both initial-only leases to `None` or
`ChromeOnly`. Initial native `textureGeneration` is non-negative and zero remains lawful
under current proof; do not describe this as migration. If absent, create `Neutral`
directly with exact session/epoch and safety-narrowed identity-free no-owner leases and assert seed/import/owner/
binding/resource are absent. Before `ReadyToCommit`, neutral synchronously registers its
private current-Foliate-destination closure. A null handle prevents install and route/egress
switching, retires every activation-owned state item once, and follows pre-drain unfreeze or
post-drain restoration/blocking. Adopted reserves no bootstrap handle.
`KomikkuReaderNativeFrameHost` must authenticate and
construct exact `ReaderProductionActivatedSessionPorts`; the production installation type cannot be
created by a public Boolean/`complete()` factory, no-op/rejecting fixture ports, or any
package retaining a reachable `Shadow`/`LegacyOnly` consequence writer. Call one
`installActivatedSession` with only that package plus the complete initial decision. After
side-effect-free revalidation of both leases and exact bootstrap reservation ownership, the barrier alone derives
`ReaderTransitionJournal(committed = Initial(origin), releaseOnlyCleanup = null,
lastTransitionSequence = 0,
lastIssuedTransitionIdentity = null)` and includes it in the single no-callback/no-suspend
snapshot write that installs gateway, optional
visible owner/binding/resource, physical input, reserved neutral bootstrap capability, deck writer, activation state, journal,
and egress. No public initial publication, egress open, or mutable port setter exists.
After successful return only, neutral enqueues `ReaderBootstrapNativePageIntent` with the
exact installed reservation and performs no `register()`. Installation rejection or close
before consumption discards that exact handle once; successful sequence-1 take consumes it,
so it cannot leak or execute twice. If failure
follows any drain, enter `RestoringLegacy`, run every bounded checkpoint restoration,
and keep the restoration deadline active after synchronous request returns until every
exact asynchronous source confirmation and final typed atomic `commitRestoredLegacy`
result. Publish legacy routes/owner/lease/lifecycle/deadline registrations only through
that one complete restoration commit; otherwise enter
`ActivationBlocked`. Direct post-drain `Legacy` rollback is forbidden. After successful
installation, fail closed.

- [ ] **Step 5: Convert activated steady-state hosts and permanent close**

Make semantic, allocation/raster/deck, coordinator-led target preparation/presentation,
two-phase successor
publication, atomic retained-owner transitional input, and resource routes command/fact-
only after activation. For narrowing operations, issue only retained-owner publication;
prohibit semantic/material/target/frame/timer work until exact `Applied(Retained)` returns
the active operation to pre-work. The coordinator selects every target registration and
issues `PrepareFrameTarget`; `ReaderPresentationHostBridge`,
`ReaderNativePagePresentationPublisher`, and PlayLikeCurl bind physical state to that
supplied registration, return target facts, and consume only matching prepared targets.
They lose binding-only requests, candidate polling, autonomous ownership/retirement
allocation, starters/commits, fabricated/replaced registrations, and defaults.
`KomikkuReaderNativeFrameHost` owns the single initial installation transaction and sole
later synchronous no-callback/no-suspend owner/input publication port. Its typed result,
and every other synchronous command rejection, is converted immediately into an exact
FIFO fact while `advancing`; the command's pending stage is published first and retained
until exact success or matching rejection consumes it. `RequestSemanticSynchronization`
uses only `SemanticSynchronization`; its handle/registry/slot/execution internals contribute
bounded reasons, never separate stages. Buffer synchronous semantic callbacks until the return
value is known; enforce the exact Accepted/pre-mutation-Rejected/pre-mutation-throw matrix,
preserve receipts across every contradictory result, and route callback-result conflicts,
late/duplicate callbacks, and post-mutation failures through session-fatal
`SemanticPortContractViolated` plus no-fallback release-only. Accepted silence uses only the
existing transition timeout. Never reduce recursively, discard a result as `Unit`/Boolean,
collapse stage/reason, or substitute a timeout. Publish `Committing` before the call and terminal state
before timer cancellation/release. On the first acknowledged successor, atomically replace
`Initial` with `Transition`, publish success, then cancel timer and release an adopted
baseline once through session authority; neutral issues no predecessor release. Rejected,
stale, timeout, abort, and Retry paths preserve the truthful baseline. Unsolicited
relocation branches on both initial variants as specified in Group 0.
`ReaderPageInputSettlementHostController` may only
narrow/veto inside that transaction; remove every separate activated `ApplyInputLease`.

In that same installation, select exactly one existing command-scoped physical timer
source. For narrowing operations, do not bind while retained publication is pending;
exact `Applied(Retained)` returns to pre-work, then publish pending `TimerBinding` and bind
exact `ReaderTransitionId` before
the first timer-requiring command. Exact bind success clears that stage before work; only
a rejection matching active ID plus pending `TimerBinding` may terminate. Its
only callback enqueues the exact typed `DeadlineExpired` fact through the FIFO; it cannot
mutate presentation/input/release, perform local `Retry`, or extend/rearm outside the
immutable command contract. Keep the production coordinator clock scheduling port
inactive until Task 7. Implement exact `snapshotForTask7Transfer` output without arming
the coordinator clock or transferring ownership in Task 6. Installation, supersession, and close must cancel/select as one
serialized ownership change so deadline-requiring work never has zero timer owners and
never has two. Release-only reuses that retained physical timer for one exact two-second
cleanup deadline, atomically replacing/fencing any attempt owner and keying admission by
opaque cleanup ID plus generation. Its only callback enqueues
`ReleaseOnlyCleanupDeadlineElapsed`; cancellation waits for terminal cancellation plus
`EmptyReleased`/`TerminalFailure`, and process close fences it without persistence, wake, or
recreation. Suppress every legacy lifecycle consequence that overlaps activated
routes; keep existing lifecycle normalization as the sole ordered ingress through a
safety-fact-only adapter until Task 7. Do not create Task 7 restore/reflow/recovery/
deadline/wake policy.

Use `closeToReleaseOnly(trigger)` on every publication/process close and replacement path,
including close as the first operation from either initial origin. Prepare the exact cleanup
deadline before the commit; in one reduction, close normal ingress, capture the exact pre-close
active ID in a fresh bounded cleanup record with ID/generation and `Armed` deadline, attach all
unresolved ordinary release-attempt rows without command reissue, clear
active/pending stage state, publish permanent `ReleaseOnly`, and emit one keyed
`CancelOwnedWork` only if that captured operation exists. No-active close records
`NotRequired`; the existing close operation is never its own cancellation target.
`PublicationReplaced` remains a separate FIFO fact with no operation/sequence allocation but
uses the same transaction. Both paths retire handles/slots/attempt timers and discard an
unconsumed neutral bootstrap reservation through one-shot retirement exactly once.

Route `CancelOwnedWork` through the real cancellation port and queue dedicated keyed applied/
rejected outcomes. The release-only sink accepts only exact cancellation applied/rejected,
cleanup deadline elapsed, attempt-keyed release confirmation/rejection/throw/port violation,
resource observation, and release commands matching its sole cleanup ID plus generation. Cancellation
and ledger accounting settle independently. Cancel the deadline only after terminal
cancellation and `EmptyReleased`/`TerminalFailure`; exact elapsed records
`CloseDrainTimeout`, terminalizes unresolved releases, and never retries/reopens/falls back.
Duplicate/stale/wrong-key/wrong-ID/wrong-generation/post-completion facts are inert.
Replacement/repeated close cannot overwrite the record or re-emit. Process close fences the
deadline, drains already-queued exact facts, converts unresolved cancellation/release rows to
process-closed states, and persists/reconstructs no cleanup/timer/wake/failure.

Every physical release, ordinary or cleanup-owned, first installs a mandatory attempt ID →
exact registration row and dispatches generic/imported release at most once; cleanup grouping is
optional. Close attaches unresolved ordinary rows without reissue. Implement fixed adapter
state and exhaustive callback/result/throw matrix: callback evidence reaches `Released` before
any violation; rejected/ambiguous may be promoted by late callback; duplicate/3+ is latest-
authoritative, saturated, idempotent, collection-free, and non-throwing. Imported release also
matches exact physical identity. Retain bounded timeout/process-close attempt tombstones with
active-row precedence. Replace retirement-fence default data-class rendering/equality use with
fixed redaction, explicit exact equality/constant hash, and sanitized assertion projection.
Adopted close/replacement releases by session
authority exactly once; neutral creates no baseline resource command. Neither path fabricates
a binding, parent, prepared-frame proof, or transition owner for baseline state. Keep the sink
permanently able to register/release owner-independently ordered late resources after timeout
while rejecting every semantic/material/frame/input/ordinary-timer/Retry/transition
consequence. Implement the three Task 6/7 boundary rows.

- [ ] **Step 6: Run focused GREEN and affected adapter gates**

Run Step 2, then:

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderPresentationHostBridgeTest" \
  --tests "paige.navic.ui.screens.reader.ReaderNativePagePresentationPublisherTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageInputSettlementHostControllerTest" \
  --tests "paige.navic.ui.screens.reader.KomikkuReaderNativeFrameHostTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRasterHydrationSchedulerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRasterPublicationLedgerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRasterPublicationSchedulerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRasterSchedulerTest" \
  --tests "paige.navic.ui.screens.reader.ReaderForegroundWebViewOwnershipTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSourceTest" \
  --tests "paige.navic.reader.ReaderWhispersyncLifecycleReducerTest"
```

Expected: every group passes; observers see one atomic installation containing routes,
typed adopted/neutral baseline with identity-free initial lease, optional initial owner/resource, physical lease, deck
writer, `Activated`, and open egress; barrier derives its own sequence-zero/no-parent
journal and callers cannot supply one; neutral reserves its private bootstrap handle before
`ReadyToCommit`, null registration causes no install/partial switch and exact restoration or
blocking, successful install transfers/consumes it without a second registration, and
rejection/close cannot leak it; first authentic operation is sequence 1/no parent,
later operations are monotonic, neutral bootstrap obtains its binding from live Foliate,
unsolicited relocation works from both baselines, retry/restoration preserves baseline,
and adopted release waits for exact successor acknowledgement; both requested and physical
leases independently match exact adopted owner/binding, neutral uses lawful no-owner leases,
and initial native generation zero is accepted without migration meaning; curl adoption fences legacy
gestures and cannot import claimed-gesture identity; every synchronous command rejection
queues exact stage/reason and reaches retryable failure without timeout substitution;
`RequestSemanticSynchronization` has exactly one `SemanticSynchronization` stage while all
handle/registry/slot/execution distinctions remain bounded reasons; synchronous callbacks use
fixed latest-authority state until result return, Accepted permits one receipt, pre-mutation
rejection is typed, and contradictory/late/arbitrarily-many duplicate callbacks retain the final
receipt, saturate bounded metadata without collection growth or callback throw, queue that receipt
once plus one violation, then close session-fatal. Accepted silence uses the existing exact timeout; active
state carries only a phase-admitted zero-or-one pending stage, exact success clears before
advance, and delayed/duplicate/wrong-stage facts are inert. Close/replacement atomically
captures an exact keyed release-only cleanup record before clearing active state, emits
cancellation at most once, admits exact cancellation/deadline/release-failure/resource facts
after `ReleaseOnly`, arms/cancels or consumes the retained two-second deadline with exact
generation fencing, and makes duplicate/stale/no-active/replacement/process-close cases
deterministic without reopening or Task 6 wake/recreation. Every ordinary or cleanup-grouped
physical release has a mandatory opaque attempt ID plus exact registration, and imported legacy
also requires complete Android physical identity. Ordinary successor/terminal rejection or throw
is accounted before cleanup; unresolved rows transfer into cleanup without command reissue.
Generic and legacy callback/result/throw matrices preserve callback-authoritative `Released`,
promote rejected/ambiguous attempts on late exact confirmation, coalesce duplicate/3+ callbacks
with fixed latest evidence and saturated violations, and never reissue or throw. Timeout/process
close retains bounded active-row/tombstone accounting with active precedence. Retirement-fence
rendering is fixed redacted, equality/hash are explicit and privacy-safe, and assertions expose
only sanitized projections with no raw sequence values.
`PublicationReplaced` allocates no operation and
uses that permanent cleanup path from both baseline variants;
sensitive wrapper rendering and failed assertions remain redacted; executable semantic handles
and isolated bounded slots remain FIFO/non-reentrant; every subordinate physical owner
is frozen/inventoried/drained or restored with collision-safe identity; physical work
follows exact allocation; retained input acknowledgement gates all successor work;
coordinator-led awaiting-target preparation supplies exact registration before any
presentation, target facts flow through FIFO, and adapters allocate no ownership; sealed
kind contracts carry legal tokens/generations/geometry/request evidence with no
pre-command frame sequence or binding-only polling/inference; adopted predecessor kind matches
its truthful owner; resource retirement is owner-independent and bounded; matching
prepared proof is removed before successor `Committing` retaining predecessor truth, whose sole awaited proof is the
publication acknowledgement, whose exact protocol callbacks are Applied/Rejected plus
`CommandRejected` stage ingress;
synchronous results queue non-reentrantly,
only exact applied acknowledgement publishes success before predecessor release, and
rejection retains predecessor while releasing successor only; transitional input uses
atomic retained-owner publication; production composition contains only authenticated
real adapters with no reachable Shadow/no-op/legacy writer; each active attempt has exactly one retained fact-only physical timer while the
production coordinator clock stays inactive, and lifecycle has no dual consequence
writer; restoration deadline survives through every asynchronous confirmation and final
atomic result; post-drain failure restores completely or blocks; post-install failure never
calls legacy; and close remains a permanent release sink. Passing this gate proves host
behavior only; it does not claim runtime acceptance.

- [ ] **Step 7: Audit Task 6 scope and governing documents**

Prove the 20-row migration ledger maps the current gateway, controller receipt path,
binding reporter, deck admission host, raster controller, every inspected bundle-source
subordinate scheduler/ledger/cache/store/validation/teardown owner, foreground WebView
ownership, PlayLikeCurl controller, native publisher, host bridge, native viewer
container, lifecycle compatibility path, deadline writers, and input controller. Verify
all original preflight/amendment gaps plus Task389's initial-origin defect and the
independently confirmed two-phase publication and exact-frame-target defects are closed;
every named RED release blocker above passes; both baseline variants and all eight
activation states have close tests; the common enum still has exactly eleven operations;
first/later identity allocation cannot fabricate or reuse an operation/parent; neutral
binding comes only from authentic Foliate; common origin provenance resolves from the
commonMain enum and the Android-only duplicate is deleted; both requested and physical
initial leases are validated at origin/checkpoint/decision/barrier, cannot carry a
transition identity, accept authoritative native generation zero, curl legacy gestures are
fenced, and origin/seed/session/epoch/resource values do
not leak through direct rendering or failed sanitized equality diagnostics; typed command
rejections and `PublicationReplaced` are exhaustive in kind/fact/reducer/tests and never
masquerade as timeout or an operation; semantic synchronization exposes only one pending
`SemanticSynchronization` stage and maps every internal handle/registry/slot or proven pre-
mutation execution failure reason to it; adapter callback/result ordering uses fixed latest-
authority state, replaces evidence on every callback, saturates at `ThreeOrMore`, never grows a
collection or throws from callback ingress, queues the final receipt once plus one bounded fatal
fact after return, and uses the existing timeout for Accepted silence; each applicable phase admits `CommandRejected`,
active state represents a zero-or-one exact pending stage, and delayed/duplicate/wrong-
stage facts are inert. Close/replacement atomically captures a separate bounded cleanup
record before active state is cleared; only matching cleanup ID/generation plus pre-close ID
can complete/reject cancellation in `ReleaseOnly`; the retained two-second cleanup deadline
starts with that atomic close, cancels only after cancellation/release accounting is terminal,
and exact elapsed/stale/duplicate/process-close behavior is deterministic with no Task 6 wake.
Every release command installs a mandatory opaque attempt ID plus exact registration before
physical dispatch; cleanup grouping is optional, imported legacy additionally carries complete
Android physical identity, and ordinary successor/terminal rejection or throw remains truthful
without cleanup. Unresolved ordinary attempts transfer into cleanup without reissue. Generic and
legacy adapters satisfy every accepted/rejected/throw crossed with callback-before/after/zero/
duplicate/3+ permutation: callback evidence wins, late exact evidence promotes to `Released`,
metadata saturates, and one bounded violation records conflicts without collection growth or throw.
Active attempt rows and timeout/process-close tombstones are bounded with active precedence.
Retirement-fence rendering is content-free, equality/hash are explicit and privacy-safe, and tests
use only sanitized bounded projections with no raw sequence assertion values/messages/diagnostics.
No-active emits no cancellation,
replacement cannot overwrite, and process close cannot re-emit or persist cleanup; neutral pre-reservation gates `ReadyToCommit`, null registration
cannot partially install, and install rejection/close/consumption retire the exact handle
once; the barrier alone derives the initial journal from
the validated origin and every signature uses `ReaderProductionActivatedSessionPorts`; proof enum and every
phase contract include target preparation/publication acknowledgement exactly; no
adapter-owned target registration, impossible all-kind target shape, pre-command frame
sequence, binding-only request/currentCandidate polling/fabricated registration,
Unit/Boolean commit result, successor work before `Applied(Retained)`, separate activated
input mutation, raw capability identity diagnostics/persistence, synchronous-only
restoration deadline, production no-op package, or post-install legacy writer remains; no Task 7 lifecycle/reflow/
recreation/deadline/wake policy moved; and Task 5's published 541-test evidence remains
preparatory and unmodified.

- [ ] **Step 8: MAIN independently reviews, commits, and pushes**

MAIN reviews the complete unstaged package and test evidence, stages exact approved
paths only, runs `git diff --cached --check`, commits
`refactor(reader): activate transition coordinator atomically` with the required
co-author footer, and pushes. Task389 publication and a separately commissioned review
precede this
checkpoint; Task #385 real-owner/source/port wiring must close before Task #386 can leave
fail-closed state. Do not claim production activation or adapter completion before this
Task 6 implementation and its gates actually complete; this amended plan itself is not
activation evidence.

### Task 7: Centralize lifecycle, reflow, and deadline ownership

Task 7 begins only after Task389 is published, Task #385 closes real-owner/source/port
wiring, Task #386 leaves fail-closed state through the accepted gate, and Task 6's atomic
activation routes pass. It replaces the Task 6
compatibility boundary and owns lifecycle normalization/policy,
visibility/recreation, reflow/profile replacement, renderer-loss recovery policy, all
deadline scheduling and any deadline-policy change, and SavedState wake integration.
Task 6 guarantees exactly one retained existing command-scoped fact-only physical timer
per activated attempt while the production coordinator clock is inactive; Task 7 must
atomically transfer every live registration to the coordinator clock and delete those
retained physical schedulers while preserving the same transition binding and remaining
hard/no-progress bounds. The serialized transfer publishes the coordinator registration
and retires the retained registration as one ownership change: before commit only the
retained timer can fire, after commit only the coordinator can fire, and no deadline-
requiring work may observe zero or two owners during transfer, concurrent supersession,
or close. Task 7
also removes compatibility owners, consumes Task 6 facts and permanent release sink,
and never reopens legacy routes. It must verify `T6-LIFECYCLE-FACT-ONLY`,
`T6-NO-DEADLINE-TRANSFER`, and `T6-NO-WAKE-TRANSFER` before replacement.

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinatorTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycleTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeoutTest.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED lifecycle/deadline tests**

```kotlin
@Test
fun progressReschedulesOneCallbackWithoutExtendingHardExpiry() {
    val fixture = deadlineFixture(now = 1_000L)
    fixture.startMaterialTransition(noProgress = 10_000L, hard = 30_000L)
    fixture.advanceTo(9_000L)
    fixture.reportMatchingProgress()
    assertEquals(31_000L, fixture.deadline.hardExpiresAt)
    assertEquals(9_000L, fixture.deadline.lastProgressAt)
    assertEquals(19_000L, fixture.clock.singleScheduledAt)
}

@Test
fun task7TransfersRetainedTimerToCoordinatorClockAtomically() {
    val fixture = task6AttemptWithRetainedTimer()
    fixture.transferDeadlineSchedulingToTask7()
    assertEquals(1, fixture.maximumDeadlineOwnerCount)
    assertEquals(1, fixture.minimumDeadlineOwnerCountWhileWorkRequired)
    assertTrue(fixture.retainedTimerDeleted)
    assertTrue(
        fixture.transitionId == fixture.clock.singleRegistration.transitionId,
        "transferred deadline transition identity mismatch"
    )
}

@Test
fun visibilityLossTerminatesPhysicalAttemptAsDeferral() {
    val fixture = activePageEntryFixture()
    fixture.enqueue(visibilityLost())
    assertNull(fixture.activeTransition)
    assertIs<ReaderTransitionOutcome.Deferred>(fixture.lastOutcome)
    assertEquals(ReaderTransitionWakeKind.VisibilityRestored, fixture.lastResumeRecord.requiredWake)
}
```

Also cover transfer racing expiry, supersession, settlement, and close; stale retained
callbacks after transfer; scheduler rejection; supersession cancellation once; reflow
retaining semantic authority; renderer loss; fresh restore proof; 2-second close drain;
and the continuing release sink after `CloseDrainTimeout`, including exact keyed cleanup
outcome admission and permanent work rejection. Every case must keep exactly
one deadline owner while work requires one and must prove retained scheduler deletion
only after the transfer commit.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionCoordinatorTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPresentationLifecycleTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPresentationTransitionTimeoutTest"
```

Expected: Task 6's fact-only lifecycle compatibility owner and retained fact-only timer
have not yet been replaced by Task 7; the production coordinator clock remains inactive.

- [ ] **Step 3: Implement one deadline record**

```kotlin
internal data class ReaderTransitionDeadlineRecord(
    val transitionId: ReaderTransitionId,
    val hardExpiresAt: Long,
    val lastProgressAt: Long,
    val noProgressIntervalMillis: Long?,
    val scheduledAt: Long
) {
    init {
        require(hardExpiresAt >= 0L)
        require(lastProgressAt >= 0L)
        require(noProgressIntervalMillis == null || noProgressIntervalMillis > 0L)
    }

    fun nextExpiry(): Long = minOf(
        hardExpiresAt,
        noProgressIntervalMillis?.let { interval -> lastProgressAt.addClamped(interval) }
            ?: hardExpiresAt
    )

    private fun Long.addClamped(delta: Long): Long =
        if (this > Long.MAX_VALUE - delta) Long.MAX_VALUE else this + delta
}
```

Before enabling coordinator-clock scheduling for any production attempt, atomically
import each Task 6 timer's `snapshotForTask7Transfer` exact transition ID, next expiry,
and hard/no-progress bounds, publish one equivalent coordinator registration, fence/
cancel the retained registration, and mark its physical scheduler deletable in the same
serialized ownership commit.
Pre-commit callbacks belong only to the retained timer; post-commit callbacks belong only
to the coordinator registration. A callback racing the commit is classified once by the
mailbox boundary. A missing/invalid transfer snapshot or coordinator scheduler rejection
leaves the retained registration as sole owner and enqueues the exact typed failure fact;
it never creates a zero-owner fallback. Supersession and close serialize with the
transfer.

After transfer, each phase replacement cancels one prior coordinator-clock registration
and schedules one callback at `nextExpiry()`. Progress changes only `lastProgressAt` and
may rearm only where the command contract permits it. Scheduler failure enqueues
immediate expiry. `Committing` cannot extend hard expiry; close uses its separate drain
record. Delete all retained Task 6 physical scheduler implementations only after source
audit proves every registration transferred or terminally canceled.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: the atomic transfer keeps exactly one callback owner through every
race, then one coordinator-clock callback per phase; retained Task 6 scheduler files are
deleted after their Task 7 transfer audit, and every lifecycle attempt terminates. Task
10 still audits that no hidden deadline route survived.

- [ ] **Step 5: MAIN reconciles lifecycle rows, commits, and pushes**

Commit `refactor(reader): centralize transition liveness` and push.

### Task 8: Add bounded opaque wake persistence

This numbered implementation package is subordinate to Task 7's lifecycle ownership;
it cannot activate a wake route independently. Task 7 remains the owner of SavedState
wake policy, normalization, and resumption.

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStore.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStoreTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Verify only: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProcessState.kt`
- Verify only: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProcessStateViewModel.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED persistence tests**

```kotlin
@Test
fun restoredOwnerNonceIsTakenBeforeFreshNonceGeneration() {
    val store = wakeStore(restoredEnvelope = encodedRecord(nonceA()), generatedNonce = nonceB())
    assertTrue(
        nonceA() == store.bootstrapRestoredOwner(),
        "restored wake owner mismatch"
    )
    assertNull(store.bootstrapRestoredOwner())
    assertTrue(
        nonceA() == store.consumeIfCurrent(wakeRestored(), issuedAt + 1)?.nonce,
        "consumed wake nonce mismatch"
    )
    assertEquals(0, store.freshNonceGenerationCount)
}

@Test
fun ordinaryNavigationCannotAcquireRestoredDemand() {
    val store = wakeStore(restoredEnvelope = null, generatedNonce = nonceB())
    store.replace(record(nonceA()))
    assertNull(store.bootstrapRestoredOwner())
    assertNull(store.consumeIfCurrent(wakeRestored(), issuedAt + 1))
    assertTrue(store.isEmpty)
}
```

Cover atomic replace/consume/clear, 15-minute expiry, one use, success/failure/cancel,
publication replacement/close, prohibited-field absence, and no proof resurrection.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderTransitionWakeStoreTest" \
  --tests "paige.navic.reader.ReaderProcessStateRecoveryTest"
```

Expected: wake store is absent while process-state preservation tests still pass.

- [ ] **Step 3: Implement SavedStateRegistry storage**

Use a dedicated registry key and Android `Bundle`; do not add serialization or
process-state fields. `bootstrapRestoredOwner()` consumes the restored state exactly
once before `SecureRandom` nonce generation. Encode only operation, reason, nonce
halves, issued/expiry timestamps, one-use budget, and finite wake enum. Never emit
nonce/timestamp values to diagnostics.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: one-use/expiry and unrelated UI restoration pass.

- [ ] **Step 5: MAIN records schema audit, commits, and pushes**

Commit `feat(reader): persist bounded transition wakes` and push.

### Task 10: Delete superseded writers and run deterministic integration

This Slice 5 package is intentionally numbered 10; it is not authorization to start
project Task9.

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionIntegratedSequenceTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionSourceAuditTest.kt`
- Delete: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycleDelivery.android.kt`
- Delete: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeout.android.kt`
- Delete: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationDispatchTimeout.android.kt`
- Delete: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterDeferredRetryCoordinator.android.kt`
- Delete: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageDeckRecoveryCoordinator.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationController.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationReceipt.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write RED source-audit tests**

Assert absence of these symbols:

```text
ReaderPresentationEffectHandler
ReaderPresentationReceiptDispatcher
ReaderPresentationLifecycleDelivery
ReaderPresentationTransitionTimeout
ReaderPageRelocationDispatchTimeout
ReaderPageRasterDeferredRetryCoordinator
ReaderPageDeckRecoveryCoordinator
ReaderDeckAdmissionCurrency
ReaderDeckAdmissionLeaseHost
ReaderPresentationBindingReporter
retryPresentationLifecycleDelivery
retryPendingPresentationRecoveryObservation
RasterProofDeckAttempt
completeObservedDeckAdmission
retryAwaitingDeckAdmission
```

Also assert only the coordinator originates release commands and input leases. Assert
that `ReaderPageTurnBundleSource`, `ReaderPageTurnBitmapSource`, and every named
subordinate scheduler/ledger/cache/store/validation/teardown owner expose the activation freeze/snapshot/drain/restoration
adapter and that `ReaderForegroundWebViewOwnership` exposes every passive/restoration/
live-claim owner; an aggregate-only source fails. Assert no `publishInitial`,
`openCommandEgress`, transition-ID retirement fence, shared semantic callback slot, or
direct post-drain `Legacy` assignment remains.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionSourceAuditTest"
```

Expected: remaining legacy symbols are enumerated.

- [ ] **Step 3: Delete or narrow every legacy writer**

Translate Task382 evidence rather than copying its patch:

- raster/deck completion still requires prepared-frame proof;
- pause/detach becomes terminal deferral plus exact finite wake;
- late callbacks become stale-fact registration plus exact-once release;
- callback-local recovery observations, `RasterProofDeckAttempt`, refresh loops, and direct release bookkeeping remain absent.

- [ ] **Step 4: Run focused GREEN and integrated sequence**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionSourceAuditTest" \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionIntegratedSequenceTest"
```

Expected: no legacy writer remains; the sequence ends with no active transition,
deadline, callback registration, unconsumed settlement, or obsolete unreleased
resource.

- [ ] **Step 5: MAIN reconciles all 20 rows, commits, and pushes**

Every row becomes `Deleted` or `Fact/command-only adapter` with named test evidence.
Discovery of another writer blocks the commit and requires adding it to the spec and
plan. Commit `refactor(reader): remove distributed transition writers` and push.

## Final consolidated host gate

- [ ] **Step 1: Run source/privacy scripts**

```bash
node --check composeApp/src/androidMain/assets/reader/navic-reader-location.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
node scripts/test-reader-relocation-bridge.mjs
pwsh -NoProfile -File scripts/test-reader-vendor-assets-verifier.ps1
pwsh -NoProfile -File scripts/test-playlikecurl-snapshot-verifier.ps1
pwsh -NoProfile -File scripts/test-reader-privacy-safe-evidence.ps1
```

Expected: every command exits zero.

- [ ] **Step 2: Run full Android host/common and renderer gates**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest
./gradlew.bat --no-daemon --console=plain :third_party:playlikecurl:karackencurllib:test
```

Expected: zero failures, errors, or required-test skips.

- [ ] **Step 3: Run Android compile, ReaderDev assembly, and lint**

```bash
./gradlew.bat --no-daemon --console=plain \
  :composeApp:compileAndroidMain \
  :androidApp:assembleReaderDev \
  :androidApp:lintReaderDev
```

Expected: all tasks succeed. No iOS, macOS, or Native task is permitted.

- [ ] **Step 4: Audit the complete specification**

Verify all 11 operations, typed adopted/neutral committed-presentation baselines with no
fabricated operation/parent, first sequence 1 and monotonic later parent identity, neutral
pre-install bootstrap reservation with null-registration no-install cleanup and exact
consume/discard ownership, live-Foliate bootstrap, unsolicited relocation/retry/restoration/close from both baselines,
adopted first-successor release only after exact acknowledgement, both-lease owner
compatibility and lawful initial native generation zero, one reducer-visible
`SemanticSynchronization` stage plus fixed latest-authoritative callback state with a saturating
count and total non-throwing ingress, exact Accepted/pre-mutation-Rejected/pre-mutation-throw
behavior, final-receipt-once plus one bounded violation for conflict/late/arbitrarily-many
callbacks, semantic session blocking, and existing-timeout silence;
represented pending command-stage matching with delayed/duplicate ordinary rejection inertness,
a distinct bounded release-only cleanup record capturing exact pre-close ID before active
clear, cleanup ID plus generation, keyed applied/rejected cancellation and deadline/release-
failure/port-violation fact admission, two-second retained Task 6 deadline ownership/cancellation/stale/
duplicate/process-close behavior; mandatory attempt IDs on ordinary and cleanup-grouped releases,
ordinary successor/terminal failure accounting and no-reissue cleanup transfer, complete generic/
imported-legacy callback/result/throw ordering with Android physical-identity matching, callback-
authoritative late promotion, fixed saturated duplicate handling, truthful non-reissuable
`RejectedNoEffect` and `AmbiguousFailure`, bounded active rows/tombstones with active precedence,
and content-free explicit retirement-fence rendering/equality/hash plus sanitized assertions;
no-active/replacement/permanent-no-reopen behavior, privacy-safe equality diagnostics, all eight
activation states, and one atomic installation containing every route plus baseline/pre-reserved neutral bootstrap/optional initial visible owner and
resource/physical lease/deck
writer/Activated/egress publication,
exhaustive collision-safe composite-identity subordinate-owner inventory/drain, bounded
restoration or coherent activation block, truthful adopted-seed owner/kind/binding/
identity provenance, owner-independent retirement order and bounded fences, executable
semantic handles and isolated bounded command slots, command-bound receipts, exact
material allocation, retained-publication acknowledgement before any successor work,
coordinator-led awaiting-target proof with selected/ledger-allocated registration,
sealed kind-specific target contracts and no pre-command frame sequence, target-prepared
FIFO fact before presentation, exact target proof, successor `Committing` retaining predecessor truth awaiting only
queued owner/input publication acknowledgement, terminal-before-release ordering,
capability-authenticated production composition with no Shadow/no-op/legacy route,
restoration deadline through all asynchronous confirmations/final atomic result,
exactly one retained fact-only Task 6 timer owner per active attempt or one exact retained
physical two-second release-only deadline replacing/fencing that owner, with ID/generation
admission, no persisted wake/recreation, exact transfer snapshot exposure, and the production
coordinator clock inactive; atomic Task 7 timer transfer with no zero/two-owner interval,
lifecycle fact-only compatibility without stealing Task 7 policy, permanent release-only
behavior, 10 finite wakes, terminal outcomes, one-shot settlement, external relocation
shield, exact-once release after close, restore bootstrap before nonce generation, no
direct adapter semantic/material/deck/presentation/input writer, all 20 migration rows,
Task382 patch absence, evidence-worktree preservation, and no Bindery/device/emulator/
ADB/`.codex-validation`/project-Task9/Stage-7 work.

- [ ] **Step 5: MAIN publishes Task384 automated completion**

Update the Stage 6 ledger with exact commands/counts, stage exact files, verify the
cached diff, commit, and push. Do not claim runtime acceptance.

## Mandatory runtime and release order

After Task384’s automated gates pass:

1. Close Task382 only as the preserved failed/superseded callback attempt; none of its dirty patch enters Task384.
2. Verify the Task384 commit matches `fork/fix/foreground-webview-handoff-ownership` and freeze ReaderDev from that exact commit.
3. Pause for explicit thread-scoped emulator ownership.
4. Task339 runs one continuous configured-EPUB sequence: cold landscape; landscape cover → page → cover; portrait reflow; portrait cover → page → cover; minimize/restore; one post-restore right-side action advancing exactly once.
5. Any freeze, non-terminal blocking phase, dropped accepted action, stale proof, duplicate advance, invalid input rejection, or lost resource owner returns to the owning coordinator package.
6. Only after Task339 passes may Task338 create and verify the GitHub-managed production-signed candidate from the same source checkpoint.
7. Only after explicit physical-device ownership and Task338 success may Task282 install that production-signed candidate and test the configured pair’s first two landscape pages of Chapter 1.
8. Task283 reconciles source, migration deletion, deferrals, failures, automated results, emulator evidence, signed provenance, tablet evidence, and Stage 6 closure.
9. Stage 7 remains blocked.

## Final acceptance checklist

- [ ] Five migration slices and all 20 writer/resource-owner rows are complete.
- [ ] One `ReaderSessionActivationCoordinator` owns the eight-state per-session boundary.
- [ ] Exactly eleven `ReaderTransitionOperation` values remain; activation, bootstrap request admission, target preparation, and publication acknowledgement are protocols/facts, never operations.
- [ ] `ReaderCommittedPresentation.Initial` represents adopted or neutral baseline without a transition ID or parent; first real operation is sequence 1/no parent and every later operation is monotonic with authentic prior-operation parent identity.
- [ ] Neutral has no visible owner/binding/resource/seed; it synchronously reserves its private current-destination handle before `ReadyToCommit`, null registration prevents install/partial switching and follows exact cleanup plus restoration/blocking, and successful atomic install transfers the handle for sequence-1 existing-operation consumption with no second registration.
- [ ] `installActivatedSession` accepts only exact `ReaderProductionActivatedSessionPorts` plus the validated initial decision from `KomikkuReaderNativeFrameHost`; the barrier revalidates both leases and reservation ownership, alone derives sequence-zero/no-parent journal state, and publishes every Task 6 route, typed baseline, exact reserved neutral bootstrap when applicable, optional initial owner/resource, identity-free physical lease, deck writer, `Activated`, and open egress atomically. Callers cannot supply a journal; no installed-but-unpublished state, Shadow/LegacyOnly/no-op route, legacy consequence writer, or post-install fallback exists.
- [ ] One freeze token inventories every named subordinate owner with composite `(session/freeze domain, source, source-local opaque token)` identity; two versioned fixed-point snapshots precede install.
- [ ] Equal source-local IDs from different sources/domains never coalesce; only equal complete identities deduplicate, and drain/import/confirmation/release-once all use that complete identity.
- [ ] Post-drain failure restores one complete checkpoint or enters `ActivationBlocked`; direct partial `Legacy` rollback is impossible.
- [ ] The adopted seed preserves the selected row's exact physical identity, truthful owner/resource kind, binding, and provenance without a fabricated transition ID, binding-only inference, or seed/key cycle.
- [ ] `ReaderTransitionResourceProvenance` is declared in commonMain and the Android-only duplicate is deleted; no common type references an Android source-set declaration.
- [ ] Every initial origin/checkpoint uses `ReaderInitialPresentationInputLease`, which cannot carry `ClaimedGesture` or `ReaderTransitionId`; breadth ordering is preserved and both requested and physical leases independently match exact adopted owner/binding at origin/checkpoint/decision/barrier, while neutral accepts only lawful no-owner leases and curl fences/cancels gestures before installing `None` or `ChromeOnly`.
- [ ] Initial native-page `textureGeneration >= 0L`; generation zero passes every initial validation boundary under current authoritative proof and is not reported as migration.
- [ ] Every semantic command executes a private request through an opaque handle and its own bounded one-shot slot; the exact neutral pre-install reservation transfers into the installed snapshot, is discarded exactly once on install rejection or close if unconsumed, and a successful sequence-1 `take` leaves only a bounded tombstone, so it cannot leak or execute twice.
- [ ] `RequestSemanticSynchronization` has exactly one reducer-visible `SemanticSynchronization` stage recorded before emission; handle lookup/take, registry, slot, and execution remain port internals. Callback ingress holds only `latestAuthoritativeReceipt`, a `Zero`/`One`/`Two`/`ThreeOrMore` count, and violation status: every callback replaces latest evidence, metadata saturates, and no collection/precondition/callback exception can grow or escape. Accepted permits one exact buffered or later asynchronous receipt under the existing timeout, while proven pre-mutation Rejected/throw queues exact stage rejection with no mutation/callback. Callback-result conflict, rejected-then-late callback, post-mutation rejection/throw, and arbitrary duplicates queue the final authoritative receipt exactly once plus one bounded `SemanticPortContractViolated`, block later session semantics, and close fail-visible to permanent release-only without fallback.
- [ ] Every synchronous command-stage rejection becomes exact FIFO `CommandRejected`; each applicable phase includes its ingress, active state carries a phase-admitted zero-or-one pending stage recorded before emission, and only matching active ID plus pending stage terminates. Exact success/rejection/phase advance/terminal cleanup, Abort, Retry, and supersession consume or clear stage authority; replacement/close first captures the exact active cancellation target in the release-only record and then clears it. Delayed, duplicate, stale, and wrong-stage facts are inert without timeout substitution or timerless work.
- [ ] Resource retirement and bounded fences are owner/transition-independent.
- [ ] Fresh material allocation precedes every raster/deck physical command.
- [ ] For narrowing operations, acceptance emits only retained-owner publication; exact `Applied(Retained)` returns to pre-work, rejection terminates, and semantic/material/raster/deck/target/frame/timer work remains zero before acknowledgement.
- [ ] Coordinator selects admitted Deck or ledger-allocates FrameHandoff, publishes an awaiting-target phase, and issues `PrepareFrameTarget`; only matching FIFO `FrameTargetPrepared` stores target and permits presentation, while adapters never allocate ownership/retirement identity.
- [ ] Sealed shell/native/curl/live targets validate exact token state, generations, geometry, request sequence, owner/kind registration, session/publication/binding, and handle; no pre-command frame sequence or binding lookup/polling/inference/fabrication/replacement exists.
- [ ] Consuming matching `PreparedFrame` removes that proof and publishes `Committing` awaiting exactly `OwnerAndInputPublicationAcknowledgement`, admitting only `SuccessorPublication`, with Applied/Rejected protocol callbacks plus `CommandRejected` ingress; only exact queued `Applied(Successor)` succeeds before timer cancellation/predecessor release.
- [ ] The adopted initial baseline is retained through abort, timeout, Retry, preparation, commit return, and rejected/stale acknowledgement, then releases once after its first exact Applied; neutral never emits a predecessor release.
- [ ] Unsolicited authoritative relocation, Retry, restoration facts, `PublicationReplaced`, permanent release-only close, and diagnostics are exhaustive over both initial variants without leaking opaque identity.
- [ ] `PublicationReplaced` is a fact, never an operation, and shares the irreversible close transaction: exact pre-close active ID is captured in one redacted `ReaderReleaseOnlyCleanupState` before active/pending state clears, one keyed cancellation is emitted only when required, and adopted/neutral resources release lawfully once.
- [ ] `ReleaseOnly` is permanent: any admitted `PublicationClose` ID remains separate from the pre-close cancellation target; only exact cancellation outcomes, cleanup deadline elapsed, attempt-keyed release confirmation/rejection/throw/port violation, release commands, and lawful resource observation matching the sole cleanup ID plus generation or an ordinary attempt transferred into it are admitted. Transfer changes optional grouping only and emits no second command. Cancellation and release accounting settle independently; the retained two-second Task 6 cleanup deadline cancels only after terminal cancellation plus `EmptyReleased`/`TerminalFailure`, while exact elapsed records `CloseDrainTimeout`, terminalizes unresolved rows, and never reopens/falls back. Stale/duplicate/post-completion generations are inert; no-active close emits no cancellation; replacement/repeated/process close cannot overwrite/re-emit; process close fences the timer and persists no cleanup/wake/recreation.
- [ ] Every ordinary or cleanup-owned generic/imported physical release installs a fresh opaque `ReaderPhysicalReleaseAttemptId` plus exact registration before dispatch; imported legacy additionally carries complete Android physical identity and cleanup grouping is optional. One registration issues at most one command, and unresolved ordinary successor/terminal attempts transfer into cleanup without reissue. Fixed adapter state starts null/`Zero`/`None`; Accepted+callback confirms, Accepted without callback waits, Rejected without callback records `RejectedNoEffect`, and throw without callback records `AmbiguousFailure`. Callback then Rejected/throw stays `Released` plus one bounded violation; late exact callback promotes rejected/ambiguous state; duplicate/3+ callbacks remain idempotent, latest-authoritative, saturated, collection-free, and non-throwing. All facts key by attempt plus registration, legacy also matches physical identity, and bounded active rows outrank timeout/process-close tombstones.
- [ ] `ReaderResourceRetirementFenceSnapshot` is a non-data class with private raw values, fixed redacted rendering, explicit exact equality, and a constant non-data-derived hash. Tests use only `confirms(order)` booleans and sanitized prefix-presence/gap-count/capacity projections; raw retirement sequence values never appear in `assertEquals` operands, messages, snapshots, or diagnostics.
- [ ] Every newly introduced sensitive value type—including lease/origin, seed, composite physical identity, imported registration, restoration checkpoint, decision, installation, committed transition, journal, snapshot, semantic invocation ID/record, cleanup ID/generation/key/state, deadline registration/fact, physical release attempt/command identity/imported dispatch, fixed callback state, release rejection/throw/port-violation facts, active attempt rows/tombstones, explicit retirement-fence snapshot/sanitized projection, and keyed cancellation facts—renders a fixed redacted constant or explicit non-data-class equivalent with exact safe equality/hash policy; assertions use only bounded sanitized projections.
- [ ] Raw initial-origin seed/session/epoch/physical/resource values, semantic invocation identities, cleanup ID/generation/key/close-operation/pre-close IDs, cleanup deadline registrations/facts, release attempt/command identities/facts/active rows/tombstones, retirement sequences, frame-target/token/claim/publication identities, request/presented-frame sequences, and registrations remain in-memory and absent from logs, diagnostics, analytics, screenshots, crash metadata, equality diagnostics, assertion values/messages, and persistence.
- [ ] Restoration deadline remains active until every exact asynchronous source confirmation and final atomic `commitRestoredLegacy` result.
- [ ] `ReleaseOnly` remains available after timeout until keyed cleanup and exact-once resource accounting reach a terminal fail-closed record.
- [ ] `T6-LIFECYCLE-FACT-ONLY`, `T6-NO-DEADLINE-TRANSFER`, and `T6-NO-WAKE-TRANSFER` prove exactly one retained fact-only timer owner per Task 6 attempt or release-only cleanup, exact two-second cleanup generation fencing, exact `snapshotForTask7Transfer` exposure without transfer, an inactive production coordinator clock, no Task 6 persisted wake/recreation, no lifecycle dual writer, and preserved Task 7 lifecycle/reflow/recreation/deadline/wake policy.
- [ ] Task 7 atomically transfers all timer scheduling to the coordinator clock and deletes retained physical schedulers with no zero- or two-owner interval during transfer, supersession, or close.
- [ ] Foliate remains exclusive semantic authority.
- [ ] PlayLikeCurl remains deformation and renderer-resource executor.
- [ ] Passive Foliate remains material-only.
- [ ] Compose renders coordinator output and submits intents only.
- [ ] One-shot settlement and external relocation semantics pass.
- [ ] Visual owner and input lease commit together.
- [ ] One active-phase deadline record exists.
- [ ] Deferrals have no hidden timer or command.
- [ ] Resume records expire after 15 minutes or one restore attempt.
- [ ] Resource release is exact-once, including stale and post-close facts.
- [ ] Task382 evidence and crash log remain protected.
- [ ] Consolidated Android-only automated gates pass.
- [ ] Automated evidence is not represented as runtime acceptance.
- [ ] Task384 → Task382 superseded closure → Task339 → Task338 → Task282 → Task283 ordering is preserved.
- [ ] Task389 is published and separately reviewed; Task #385 real-owner/source/port wiring is closed before Task #386 leaves fail-closed state. No adapter-complete claim relies on preparatory interfaces/tests.
- [ ] Project Task9 and Stage 7 remain unstarted.
