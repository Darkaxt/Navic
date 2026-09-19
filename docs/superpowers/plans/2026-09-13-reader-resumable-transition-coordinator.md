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
material/deck, frame, input, exhaustive inventory/drain, adopted predecessor, and
release-sink ownership. Task 7 separately migrates lifecycle, reflow, deadlines, and
wakes. No subsystem may activate as an independent competing control plane.

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

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt` — common identity, exactly eleven operations, phases/outcomes, retained-publication and target-preparation proofs/facts/commands, sealed frame targets, two-phase successor acknowledgement, finite wake, liveness, resume-record, and pure journal types.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt` — main-thread non-reentrant FIFO, journal advancement through successor `Committing` retaining predecessor truth, synchronous result-to-fact conversion, retained fact-only timers, one-shot consumption, release ledger, terminal-before-release outcomes, and closed release sink.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt` — semantic, allocation, raster, deck, coordinator-registration target preparation/exact presentation, sole typed-result owner/input publication, capability-authenticated production package, inventory, resource, wake, and clock ports.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivation.android.kt` — one main-thread per-session activation state machine, opaque freeze/composite physical identities, exhaustive inventory/drain, owner-independent registration, atomic route/initial-owner/input/egress snapshot installation, bounded restoration/blocked states, and permanent release-only sink.
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
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt`
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
    data class Exact(
        val binding: ReaderPresentationBinding
    ) : ReaderExpectedPresentationBinding

    data class SemanticSuccessor(
        val predecessor: ReaderPresentationBinding,
        val requestSequence: Long
    ) : ReaderExpectedPresentationBinding
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
    Retry,
    PublicationClosed
}

data class ReaderTransitionPhaseContract(
    val awaitedProofs: Set<ReaderTransitionProofKind>,
    val deadlineOwner: ReaderTransitionId,
    val supersession: ReaderTransitionSupersession,
    val retainedOwner: ReaderPresentationFrameOwner,
    val inputLease: ReaderTransitionInputLease,
    val callbackSources: Set<ReaderTransitionFactKind>
) {
    init {
        require(awaitedProofs.isNotEmpty())
        require(callbackSources.isNotEmpty())
    }
}

data class ReaderTransitionPhase(
    val kind: ReaderTransitionPhaseKind,
    val contract: ReaderTransitionPhaseContract
)
```

Every successor `Committing` contract has
`awaitedProofs == setOf(OwnerAndInputPublicationAcknowledgement)` and
`callbackSources == setOf(OwnerAndInputPublicationApplied,
OwnerAndInputPublicationRejected)`. Consuming `PreparedFrame` removes
`PreparedFrame` from awaited proof before `Committing` is published. An awaiting-target
contract uses exactly `FrameTargetPreparation` and the two target-preparation fact kinds.
A retained-owner publication waiting contract uses exactly
`OwnerAndInputPublicationAcknowledgement` and the two publication acknowledgement fact
kinds, but its matching `Applied(Retained)` returns to the operation's pre-work
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
    WebViewExposure,
    Cancellation,
    ReleaseDrain
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
frame sequences, and resource registrations are in-memory capability material. They are
forbidden from logs, diagnostics, analytics, screenshots, crash metadata, equality
diagnostics, and persistence. Only bounded frame kind, phase/state, mismatch-category,
and count values may be exposed.

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
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionFact
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
    data class DeadlineExpired(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
    data class Retry(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
    data class PublicationClosed(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
}
```

The pure journal API used by common and Android tests is:

```kotlin
data class ReaderActiveTransition(
    val id: ReaderTransitionId,
    val phase: ReaderTransitionPhase,
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
)

data class ReaderCommittedTransition(
    val id: ReaderTransitionId,
    val owner: ReaderPresentationFrameOwner,
    val binding: ReaderPresentationBinding,
    val resource: ReaderTransitionResourceRegistration
)

data class ReaderTransitionJournal(
    val active: ReaderActiveTransition? = null,
    val lastOutcome: ReaderTransitionOutcome? = null,
    val committed: ReaderCommittedTransition? = null
) {
    fun reduce(
        fact: ReaderTransitionFact,
        nowMillis: Long = 0L
    ): ReaderTransitionReduction = readerTransitionJournalReduce(this, fact, nowMillis)
}

data class ReaderTransitionReduction(
    val state: ReaderTransitionJournal,
    val commands: List<ReaderTransitionCommand>
)
```

`readerTransitionJournalReduce` is pure and exhaustive over operation, phase, and
fact. It records at most one settlement consumption key on the active transition
before returning consequences, clears that key with the physical attempt, and never
inherits it into a successor transition. Resource-bearing facts are classified as
registration/observation, release confirmation, accepted proof, or rejected
resource disposition; one exact-key helper deduplicates release commands while
protecting only the truthful predecessor or committed successor.

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
        val registration: ReaderTransitionResourceRegistration
    ) : ReaderTransitionCommand {
        override val transitionId: ReaderTransitionId?
            get() = (issuer as? ReaderResourceReleaseIssuer.Transition)?.transitionId
    }

    data class CancelOwnedWork(
        override val transitionId: ReaderTransitionId
    ) : ReaderTransitionCommand
}
```

Facts cover every specification ingress: user intent, destination commit, settlement
acknowledgement, exact material-binding allocation, viewport/profile replacement,
raster progress/proof/deferral/failure, deck reservation/ownership/prepared/rejected/
released/capacity, frame-target prepared/rejected, exact-target prepared frame,
synchronous combined-publication applied/rejected acknowledgement, cover post-draw, WebView proof, visibility,
resource loss, deadline expiry, Retry, and publication close.

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
}

internal data class ReaderLegacyPhysicalDomain(
    val readerSessionGeneration: Long,
    val freezeToken: ReaderLegacyFreezeToken
) {
    init {
        require(readerSessionGeneration > 0L)
    }
}

@JvmInline
internal value class ReaderLegacySourceLocalOpaqueToken internal constructor(
    val value: Long
) {
    init {
        require(value > 0L)
    }
}

internal data class ReaderLegacyPhysicalIdentity(
    val domain: ReaderLegacyPhysicalDomain,
    val source: ReaderLegacyInventorySource,
    val sourceLocalToken: ReaderLegacySourceLocalOpaqueToken
)

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
}

internal data class ReaderLegacySourceRestartHandle(
    val source: ReaderLegacyInventorySource,
    val opaqueId: Long
) {
    init {
        require(opaqueId > 0L)
    }
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
    val requestedLease: ReaderTransitionInputLease,
    val physicalLease: ReaderTransitionInputLease
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
        require(readerInputLeaseIsNoBroaderThan(physicalLease, requestedLease))
        require(readerInputLeaseIsCompatibleWithOwner(
            owner = initialOwner,
            binding = initialBinding,
            lease = physicalLease
        ))
    }
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

Select at most one predecessor only from a directly reported `Visible` inventory row
whose exact owner and binding are physically confirmed. Zero is legal only for a
truthful neutral state; a shell cover is a real predecessor and still carries its
directly reported composite physical identity and binding. Never infer the predecessor
from journal state, binding similarity, gestures, renderer generations, or
acknowledgements. Copy the selected row's exact owner, binding, resource kind, and
`ReaderLegacyPhysicalIdentity`. The required owner-kind relation is neutral → no
resource, native page/curl → `Deck`, and shell cover/live engine → `FrameHandoff`; a
mismatch blocks installation. At handoff mint one seed ID, then import the complete
identity under `AdoptedPredecessor(seedId)`. The import registry allocates a collision-
free coordinator key `opaqueId` rather than reusing the source-local token, and assigns
owner-independent `ReaderResourceRetirementOrder`. The immutable initial decision is
formed only after that import and physical-lease safety narrowing. Thus the order is
complete physical identity → selected row kind/binding/owner → seed ID → collision-safe
legacy import → retirement registration → initial decision; no seed/key cycle or
binding-only inference exists. The seed names adoption, not legacy creation, and closes
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

internal interface ReaderSemanticCommandPort {
    fun synchronize(
        command: ReaderTransitionCommand.RequestSemanticSynchronization,
        onReceipt: (ReaderPresentationEventReceipt) -> Unit
    ): ReaderPortCommandResult
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
    fun closeToReleaseOnly()
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
        command: ReaderTransitionCommand.ReleaseResource,
        imported: ReaderImportedLegacyResourceRegistration,
        onConfirmed: (
            ReaderLegacyPhysicalIdentity,
            ReaderTransitionFact.ResourceReleased
        ) -> Unit
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
}

internal data class ReaderInitialActivationDecision(
    val seed: ReaderAdoptedPredecessorSeed,
    val adoptedResource: ReaderImportedLegacyResourceRegistration?,
    val requestedLease: ReaderTransitionInputLease,
    val physicalLease: ReaderTransitionInputLease
) {
    init {
        val neutral = seed.owner == ReaderPresentationFrameOwner.Neutral
        require(neutral == (adoptedResource == null))
        require(
            adoptedResource == null ||
                adoptedResource.physicalIdentity == seed.physicalIdentity
        )
        require(
            adoptedResource == null ||
                adoptedResource.registration.key.ownerId ==
                ReaderTransitionResourceOwnerId.AdoptedPredecessor(seed.id)
        )
        require(
            adoptedResource == null ||
                adoptedResource.registration.key.kind == seed.resourceKind
        )
        require(
            adoptedResource == null ||
                adoptedResource.registration.retirementOrder.readerSessionGeneration ==
                seed.readerSessionGeneration
        )
        require(
            adoptedResource == null ||
                adoptedResource.registration.retirementOrder.coordinatorEpoch ==
                seed.coordinatorEpoch
        )
        require(readerInputLeaseIsNoBroaderThan(physicalLease, requestedLease))
        require(readerInputLeaseIsCompatibleWithOwner(
            owner = seed.owner,
            binding = seed.binding,
            lease = physicalLease
        ))
    }
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
)

internal data class ReaderActivatedSessionSnapshot(
    val ports: ReaderProductionActivatedSessionPorts,
    val initialDecision: ReaderInitialActivationDecision,
    val state: ReaderSessionActivationState = ReaderSessionActivationState.Activated,
    val commandEgressOpen: Boolean = true
) {
    init {
        require(state == ReaderSessionActivationState.Activated)
        require(commandEgressOpen)
    }
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
    fun closeToReleaseOnly()
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
initial decision. The input
adapter has already produced `physicalLease` and proved it is no broader than
`requestedLease`. In one non-callback, non-suspending commit, the barrier constructs
one immutable `ReaderActivatedSessionSnapshot` and replaces the composition root's
single installation-snapshot reference. Gateway, semantic/material/frame/resource
ports, the retained fact-only timer route, visible-owner projection, physical input
dispatch, activation-state reads, and command-egress checks all dereference that same
snapshot; none has a separate mutable install field. The snapshot therefore switches every route; publishes initial visible
owner/binding and physical input lease; marks `Activated`; and exposes command egress
as one write. There is no
`publishInitial`, `openCommandEgress`, or mutable per-port setter. Observers see only a
complete legacy snapshot or a complete activated snapshot with initial publication.
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
work starts until one bound registration ID is accepted as owner. For supersession and
close, main-thread code first binds the successor registration but gates its callback as
inert, then atomically replaces the accepted registration ID and fences the predecessor;
only afterward does it physically cancel the predecessor. Before the commit only the
predecessor callback is admissible, after it only the successor callback is admissible.
Bind failure leaves the predecessor sole owner and rejects/terminates the successor
before work. Thus physical callback races cannot create a logical zero- or two-owner
interval.

The exact activation order is: construct inactive coordinator/adapters; verify all
ports and prerequisites, including an inactive production coordinator clock and the
retained fact-only timer route; freeze; obtain the first complete collision-safe
inventory after all source fences and capture its pre-drain checkpoint; maintain
versioned inventory; select at most one directly proven predecessor; begin destructive
drain of every other complete physical identity with matching confirmations to the
fixed point; carry the selected row's exact identity, owner, resource kind, binding, and
provenance into the seed; allocate its collision-free imported key and owner-independent
retirement order; compute and validate the narrowed physical lease; then call
`installActivatedSession` once with the complete payload. For a narrowing operation, the
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
closed without fallback. Close invokes `closeToReleaseOnly` before physical
cancellation.

The permanent release sink is created before freeze and is reachable through the
activation coordinator during drain, restoration, and blocked states; it is not a
production semantic/presentation consequence route. Atomic installation binds the
activated gateway to that same sink, and close narrows the session to it. The sink
accepts only exact resource observations, exact release confirmations, and
`ReleaseResource`. It rejects semantic, material, deck, frame, input, presentation,
Retry, restoration, and transition consequences, including after a close timeout.

The release ledger registers a `ReaderTransitionResourceRegistration` exactly once per
coordinator physical identity and a `ReaderImportedLegacyResourceRegistration` exactly
once per complete legacy composite identity, allocating monotonically increasing
`ReaderResourceRetirementOrder` independently of transition/resource owner. Equal local
IDs under different sources/domains import independently and each releases once; only a
duplicate of the same complete identity reuses a registration. Adopted resources use
`ReaderResourceReleaseIssuer.Session`; they never borrow an active transition.
Confirmation must match complete legacy identity, key, and order. Per session/epoch, the bounded
retirement fence stores `contiguousReleasedThrough` plus at most 32 out-of-order
released sequences. Active registrations take precedence; capacity exhaustion rejects
new ownership fail-closed and cannot evict an unconfirmed fence.

### Command-bound receipts and material/frame causality

Every `ReaderSemanticSynchronizationIntent`, including page turn, cover entry, and
TOC/search/bookmark/annotation/jump, carries a content-free
`ReaderSemanticRequestHandle`. A bounded session registry (maximum 16 pending handles)
resolves it exactly once to `ReaderExecutableSemanticRequest`, an in-memory closure
that privately retains the actual Foliate action/destination. Common state,
persistence, diagnostics, and logs never contain or reconstruct hrefs, CFIs,
selections, annotation payloads, or other destination content. Missing, duplicate,
expired, superseded, or wrong-session handles reject before any mutation or legacy
dispatch.

Before taking and invoking that executable request,
`ReaderSemanticCommandPort.synchronize` reserves a dedicated slot containing exact
slot ID, transition ID, and request handle. Each executable receives a callback closure
capturing only its own slot; there is no shared next-callback slot and unsolicited
Foliate delivery uses a separate facts port. At most eight slots are active. Accepted
callback, rejection/throw, supersession, deadline, publication replacement, and close
all retire both slot and handle. The slot fence retains one contiguous retired-through
sequence plus at most 32 out-of-order sequences; active slots win, and overflow rejects
new commands fail-closed rather than growing or evicting live causality. The receipt
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
authoritative under Task 5 arbitration. A callback before command return appends the
slot-tagged receipt to the existing FIFO and never reduces recursively. Wrong-slot,
duplicate, stale, or untagged settlement receipts do not satisfy proof.

After semantic proof, `AllocateMaterialBinding` allocates fresh monotonic preparation,
raster, and texture generations. `MaterialBindingAllocated` is admitted only after
exact transition, Foliate-session, publication-generation, viewport/profile, and
semantic-binding verification. `RequestRasterPreparation` and `ReserveDeck` carry
that allocation and reject before physical work if any exact field differs.

For every operation that narrows or revokes input, acceptance first publishes an
`AwaitingProof` retained-publication contract awaiting exactly
`OwnerAndInputPublicationAcknowledgement`, with only
`OwnerAndInputPublicationApplied`/`Rejected` as callbacks, and emits only
`PublishRetainedOwnerAndInputLease`. Before exact `Applied(Retained)`, the coordinator
may issue no semantic, allocation, raster, deck, frame-target, frame-presentation, or
other timer-requiring physical command and binds no attempt timer for that work.
`Applied(Retained)` records the narrowed lease and returns the same active operation to
its appropriate pre-work `Accepted`/`AwaitingPrerequisites` phase; it is not successor
success. `Rejected(Retained)` publishes terminal failure before any successor work.

After later semantic/material/deck prerequisites, the coordinator—not an adapter—selects
one exact registration. Native/curl select the already admitted `Deck`; shell/live
allocate a transition-owned `FrameHandoff` through the coordinator release ledger. The
journal publishes an `AwaitingProof` target phase awaiting exactly
`FrameTargetPreparation`, with `FrameTargetPrepared` and
`FrameTargetPreparationRejected` as callback sources, and emits
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
`OwnerAndInputPublicationAcknowledgement` and names only
`OwnerAndInputPublicationApplied` and `OwnerAndInputPublicationRejected` callback
sources. It retains active transition, predecessor owner/input truth, timer, target, and
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
| `ReaderTransitionGateway` Shadow route followed by legacy dispatch | 2, 5, 6, 10 | Task 6 `installActivatedSession` accepts only a capability-authenticated real-adapter package, atomically switches every consequence route, and publishes initial owner/physical lease with egress in the same commit | complete installation snapshot with no reachable Shadow/LegacyOnly/no-op route, or unchanged/restored legacy; post-install fail closed | `closeToReleaseOnly`; no installed-but-unpublished state or active-session fallback |
| `ReaderPresentationControllerReducer.onPresentationEvent/onViewerAction` and receipt construction | 5, 6, 10 | opaque executable handle + dedicated bounded command slot + explicit origin → exact tagged receipt or separate authoritative unsolicited relocation | exact handle/slot identity plus semantic/frame proof | retire handle/slot on all terminal paths; no inferred origin or Android consequence in controller |
| `ReaderPresentationBindingReporter.update/reserve/classifyReceipt/commitReceipt` | 2, 3, 6, 10 | frozen inventory → seed/owner-independent registration/initial decision; exact-target prepared frame → `Committing`; synchronous publication result → queued exact acknowledgement | direct visible proof plus matching applied acknowledgement only; ambiguity blocks install; rejected commit retains predecessor and releases successor; post-drain failure restores complete checkpoint or blocks | non-adopted composite identities drain; replica deleted |
| Receipt dispatcher and host effect handlers | 2, 6, 10 | command callback or synchronous publication result → existing FIFO fact while advancing; journal → combined commit/release | callback/result-before-return remains non-reentrant; terminal state precedes release | release sink only; duplicate queues deleted |
| Lifecycle delivery and retry queue | 6, 7, 10 | Task 6 keeps existing normalization as sole ordered ingress but suppresses overlapping legacy consequences and emits safety facts only; Task 7 migrates normalization/recovery | no Task 6 restore/reflow/recovery/wake policy; Task 7 lifecycle matrix | exact cancellation; compatibility writer deleted after Task 7 |
| Host bridge transition starters | 6, 10 | coordinator-selected registration + exact kind specification → `PrepareFrameTarget` → FIFO `FrameTargetPrepared` → `RequestFramePresentation` → target-echoing `PreparedFrame`/failure | awaiting-target proof precedes presentation; adapter cannot allocate/register ownership; no binding lookup, token fabrication, autonomous commit, or local deadline | reject/supersede/close retire handle and release exact registration once; starters deleted |
| Native page publisher | 6, 10 | coordinator supplies exact admitted PlayLikeCurl `Deck` + native specification → prepared target → presentation proof | no `currentCandidate` polling/same-binding selection; target preparation then exact prepared proof; neither is success | frame/callback inventory and ledger; autonomous `update` deleted |
| Presentation and relocation timeout owners | 6, 7, 10 | Task 6 selects one retained timer source; after required `Applied(Retained)`, binds exact transition before first timer-requiring command and routes only expiry facts while coordinator clock stays inactive; Task 7 transfers/deletes | unchanged duration; exactly one owner/registration/fact with no zero/two-owner interval | cancel exact registration terminally; delete files only in Task 7 |
| Native viewer container direct presentation writers | 2, 6, 7, 10 | production composition root plus sole publication port; `Applied(Retained)` gates all successor work and exact queued `Applied(Successor)` is the sole success gate | retained-publication waiting → pre-work; target preparation → presentation → successor `Committing` retaining predecessor truth; neither commit command nor `PreparedFrame` succeeds | ledger only; direct writers/Shadow/LegacyOnly/no-op packages unreachable |
| Input settlement host controller | 6, 7, 10 | operation acceptance → retained-owner publication only; exact `Applied(Retained)` → pre-work; final successor publication uses same protocol | zero semantic/material/raster/deck/target/frame/timer work before retained Applied; rejected retained terminates; no `ApplyInputLease` or local grant/broadening | ordered cancellation only; local mutation removed |
| Raster preparation controller | 4, 6, 7, 10 | semantic proof → allocation → allocation-scoped raster facts | exact fresh generations before physical work; frame proof still required | exhaustive frozen inventory; Task 7 owns deferral/wake policy |
| `ReaderPageTurnBundleSource`/`ReaderPageTurnBitmapSource` plus hydration/publication/generation schedulers, publication ledger, pending-callback and capture ownership, cache/store, live validations, and teardown | 6, 10 | synchronous source freeze → collision-safe composite-identity repeated snapshot → exact drain or checkpoint restoration | all subordinate bitmap/callback/job/store owners reach fixed point; counts never substitute | owner-independent registrations and bounded retirement fence; direct hidden ownership fails source audit |
| `ReaderForegroundWebViewOwnership` passive/restoration/live-claim state | 6, 10 | freeze acquisition/mutation → exact lease/claim/callback snapshot → settle/drain or checkpoint restore | no mutation crosses cutover; restoration callback terminal before fixed point | exact release/restoration; no callback publication or reopen before complete commit |
| Deferred raster retry coordinator | 7, 10 | typed deferral → persist/consume/cancel | 15 minutes or one restoration | exact finite wake; file deleted |
| Deck admission and lease host | 3, 4, 6, 10 | exhaustive inventory plus `MaterialBindingAllocated` → allocated reserve/build facts | complete drain and exact allocation/ownership proof | one composite physical identity/imported key; host currency deleted |
| PlayLikeCurl Foliate controller local transition writers | 4, 5, 6, 7, 10 | after `Applied(Retained)`, semantic/material/deck work → facts; coordinator supplies admitted Deck + exact curl specification to `PrepareFrameTarget`; adapter returns target/proof only | target preparation and `PreparedFrame` lead to successor `Committing` retaining predecessor truth; only exact queued `Applied(Successor)` succeeds, never commit command or frame proof | ledger only; Task 7 owns recovery policy |
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
fun everyNonTerminalPhaseNamesAllSixLivenessFields() {
    operationFixtures().forEach { fixture ->
        fixture.nonTerminalPhases.forEach { phase ->
            assertTrue(phase.contract.awaitedProofs.isNotEmpty())
            assertEquals(fixture.id, phase.contract.deadlineOwner)
            assertNotNull(phase.contract.supersession)
            assertNotNull(phase.contract.retainedOwner)
            assertNotNull(phase.contract.inputLease)
            assertTrue(phase.contract.callbackSources.isNotEmpty())
        }
    }
}

@Test
fun matchingSettlementIsConsumedOnceBeforeConsequences() {
    val fixture = journalAwaitingSettlement()
    val fact = matchingSettlementFact(fixture)
    val first = fixture.journal.reduce(fact)
    assertEquals(fixture.id, first.state.active?.consumedSettlement?.transitionId)
    assertTrue(first.commands.none { it is ReaderTransitionCommand.CommitOwnerAndInputLease })
    val duplicate = first.state.reduce(fact)
    assertTrue(duplicate.commands.isEmpty())
    assertEquals(first.state, duplicate.state)
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
    assertNotNull(ledger.requestRelease(issuer, registration))
    assertNull(ledger.requestRelease(issuer, registration))
    assertTrue(ledger.confirmReleased(registration))
    assertFalse(ledger.confirmReleased(registration))
    assertEquals(1L, ledger.retirementFence().contiguousReleasedThrough)
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
    Released
}

internal data class ReaderResourceRetirementFenceSnapshot(
    val readerSessionGeneration: Long,
    val coordinatorEpoch: Long,
    val contiguousReleasedThrough: Long,
    val outOfOrderReleasedSequences: Set<Long>
) {
    init {
        require(contiguousReleasedThrough >= 0L)
        require(outOfOrderReleasedSequences.size <= 32)
        require(outOfOrderReleasedSequences.all { it > contiguousReleasedThrough })
    }
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
        registration: ReaderTransitionResourceRegistration
    ): ReaderTransitionCommand.ReleaseResource?

    fun confirmReleased(
        registration: ReaderTransitionResourceRegistration
    ): Boolean

    fun confirmLegacyReleased(
        physicalIdentity: ReaderLegacyPhysicalIdentity,
        registration: ReaderTransitionResourceRegistration
    ): Boolean

    fun retirementFence(): ReaderResourceRetirementFenceSnapshot
}
```

`register` allocates the next positive retirement sequence for a new coordinator
resource key. `importLegacy` keys its bijection by the complete
`ReaderLegacyPhysicalIdentity`, allocates a coordinator-global positive `opaqueId` for
the common resource key, and returns the prior import only for the same complete
identity. Equal source-local token values under different sources or freeze/session
domains always receive distinct imports and release state. `requestRelease` transitions
only `Owned` → `ReleaseCommandIssued`. Legacy confirmation must match both the complete
physical identity and imported key/order; generic confirmation matches key/order.
Confirmation advances `contiguousReleasedThrough` across consecutive orders, stores at
most 32 gaps, and ignores exact duplicates at/below the fence. Active registrations
take precedence. Gap overflow rejects new registration/release admission fail-closed
and cannot discard an unconfirmed gap. Raw domain, freeze, local-token, import-ID, and
retirement-order values are memory-only and prohibited from logs, diagnostics,
analytics, screenshots, crash metadata, and persistence; report only bounded source
enums, counts, states, and mismatch categories.

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

Run Step 2. Expected: exact-once release and cutover protocol pass while the
production `LegacyOnly` policy keeps legacy as the sole writer.

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
evidence and `Shadow`/`LegacyOnly` classification unchanged.

**Create:**
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivation.android.kt` — activation states, freeze token, exact subordinate inventory/drain, adopted registration, pre-drain checkpoint, bounded restoration/blocked protocol, atomic route/initial-owner/input/egress snapshot installation, and permanent release sink.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionActivationTest.kt` — activation, inventory, drain, seed, failure, and close-phase tests.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionAtomicCutoverTest.kt` — semantic-through-frame integrated cutover and no-fallback tests.

**Modify common model and routing:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt` — adopted ownership, allocation-scoped raster/deck, retained-publication ordering, `FrameTargetPreparation` command/facts/proof, sealed kind targets, successor `Committing` acknowledgement, and publication commands; retain exactly eleven operations.
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
  timer, sole successor/retained-owner publication, and resource routes; publishes the
  initial owner and physical lease; marks `Activated`; and opens egress in one
  transaction. No public Boolean/`complete()`/all-no-op package, installed
  `Shadow`/`LegacyOnly` route, partial observation, or active-session fallback is legal.
- `T5-EXACT-SEED`: close with the one-domain exhaustive subordinate-owner inventory,
  direct physical predecessor proof, one adopted-at-handoff seed carrying the selected
  row's complete composite identity and truthful resource kind, collision-safe legacy
  import, owner-independent retirement registration, `AdoptedLegacy` provenance, and
  command-bound Foliate callback identity. Preserve composite identity → selected
  kind/binding/owner → seed → import key → retirement registration → initial decision
  ordering; never infer from binding, journal state, gesture, renderer generation, or
  acknowledgement.
- `T6-LIFECYCLE-FACT-ONLY`: Task 6 suppresses all overlapping legacy lifecycle
  consequences at installation. Existing normalization remains the sole ordered
  lifecycle ingress until Task 7 and may emit safety facts only; Task 6 cannot create
  visibility/restore/reflow/recovery or wake policy.
- `T6-NO-DEADLINE-TRANSFER`: Task 7 retains deadline ownership. Task 6 keeps the
  production coordinator clock inactive and selects exactly one existing command-scoped
  physical timer for each activated attempt. For narrowing operations no timer binds
  while retained publication is pending; exact `Applied(Retained)` returns to pre-work,
  then it binds exact `ReaderTransitionId` before the first timer-requiring command. It
  emits only the matching typed `DeadlineExpired` fact through FIFO and cannot
  mutate consequences, Retry, or extend/rearm outside that command registration. All
  competing timers are inactive. The Task 6 port must expose the exact
  `snapshotForTask7Transfer(registration)` snapshot contract while neither invoking it as
  a transfer nor enabling coordinator-clock scheduling. Task 7 later atomically transfers every active/new
  registration to the coordinator clock and deletes the retained schedulers with no
  zero-owner or two-owner interval.
- `T6-NO-WAKE-TRANSFER`: Task 6 neither persists nor consumes SavedState wake demand.
  Its restoration checkpoint is bounded in-memory activation safety state; Task 7 owns
  all wake storage and resumption.

- [ ] **Step 1: Write the grouped deterministic RED suite**

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
        listOf(
            legacyInstallationSnapshot(),
            activatedInstallationSnapshot(
                owner = fixture.seed.owner,
                physicalLease = fixture.narrowedInitialLease,
                commandEgressOpen = true
            )
        ),
        fixture.installationSnapshots
    )
    assertEquals(1, fixture.atomicInstallationCommitCount)
    assertFalse(fixture.observedInstalledButUnpublished)
}
```

Also prove table-driven atomic installation for neutral (no binding, physical identity,
kind, or adopted registration), shell-cover (`FrameHandoff`), native-page (`Deck`), curl/
native material (`Deck`), and live/WebView predecessor (`FrameHandoff`) decisions. Each
non-neutral case must preserve the selected frozen row's exact owner, resource kind,
binding, complete physical identity, and adopted-legacy provenance through the one
snapshot write. Reject a mismatched owner/kind, identity, provenance, binding, imported
owner ID, session, epoch, seed, or broadened physical lease before that write. Prove the
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
with no cycle and no binding-only inference. No legacy identity receives
`TransitionOwned`, and the coordinator-global resource key never reuses a source-local
opaque token. Prove adopted release uses a session issuer without an active transition;
retirement ordering ignores owner/operation IDs; duplicate/early/out-of-order
confirmations advance `contiguousReleasedThrough` correctly; at most 32 out-of-order
tombstones exist; active registrations take precedence; and overflow rejects new
ownership without evicting an unconfirmed fence. Equal local IDs under different sources
import independently, each exact composite identity confirms and releases once, and a
duplicate of the same complete identity cannot allocate or release a second registration.

Group D, synchronous semantic receipt identity:

```kotlin
@Test
fun synchronousFoliateReceiptUsesItsExecutableHandleAndDedicatedSlotOnce() {
    val fixture = semanticFixture(callbackBeforeReturn = true)
    fixture.synchronize(commandFor(fixture.transitionId, fixture.requestHandle))
    assertEquals(fixture.transitionId, fixture.singleReceipt.originatingTransitionId)
    assertEquals(fixture.slotId, fixture.singleReceipt.semanticCommandOrigin.slotId)
    assertEquals(1, fixture.coordinator.maxAdvanceDepth)
    assertEquals(
        listOf("reserve-slot", "take-handle", "invoke-private-request", "consume-slot", "enqueue", "return", "reduce"),
        fixture.trace
    )
    assertEquals(0, fixture.activeHandleCount)
    assertEquals(0, fixture.activeSlotCount)
}
```

Also prove every page/cover/TOC/search/bookmark/annotation/jump command executes the
private request selected by its opaque handle after legacy dispatch is suppressed;
source metadata alone cannot execute or reconstruct a destination. Missing, duplicate,
expired, superseded, and wrong-session handles reject before mutation. Concurrent
commands receive distinct callback closures/slots; cross-order callbacks cannot consume
each other; unsolicited callbacks consume none. Wrong-slot, duplicate, stale, and
untagged settlement receipts cannot satisfy proof, while unsolicited untagged
same-publication relocation remains authoritative. Prove 16-handle, 8-active-slot, and
32-out-of-order-tombstone limits and retirement on callback, rejection/throw,
supersession, deadline, replacement, and close.

Group E, material allocation: prove semantic proof emits
`AllocateMaterialBinding`, not raster/deck work; allocation returns strictly fresh
monotonic preparation/raster/texture generations; publication, profile, session, or
binding mismatch yields `MaterialAllocationRejected`; `RequestRasterPreparation` and
`ReserveDeck` accept only the allocation's exact binding; and every rejection occurs
with zero physical prepare/reserve calls.

Group F, exact frame target and two-phase combined publication:

```kotlin
@Test
fun preparedFrameDoesNotPublishSuccessBeforeCombinedCommitAcknowledgement() {
    val fixture = activatedPageEntryFixture()
    fixture.completeSemanticAllocationRasterAndDeckAndPrepareExactTarget()
    fixture.reportMatchingPreparedFrame()
    assertEquals(ReaderTransitionPhaseKind.Committing, fixture.activePhase)
    assertNull(fixture.lastOutcome)
    assertEquals(fixture.predecessorOwner, fixture.visibleOwner)
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
only `OwnerAndInputPublicationAcknowledgement`, with Applied/Rejected as its exact two
callback sources, then issues only combined commit. Exact `Applied(Successor)` publishes
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
and no two-owner interval. Prove wrong-ID/stale/duplicate timer callbacks are inert. Prove
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

Group J, close in every activation phase: close from `Legacy`, `Freezing`,
`DrainingLegacy`, `ReadyToCommit`, `RestoringLegacy`, `ActivationBlocked`, `Activated`,
and repeated `ReleaseOnly`. Before physical cancellation, `closeToReleaseOnly` must
publish permanent `ReleaseOnly`. Only resource observation, release confirmation, and
`ReleaseResource` are accepted; all other facts/commands/restoration attempts are
rejected before and after `CloseDrainTimeout`.

The following named RED tests are release blockers and map one-to-one to the amended
contract:

| Test | Exact expected behavior |
|---|---|
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
queuing an exact applied/rejected fact; retained input publication is not ordered before
successor semantic/material/physical/timer work and transitional input has a separate
unwired mutation; restoration can cancel its deadline after synchronous request returns;
production package completeness can be claimed by no-op/rejecting fixtures and
`KomikkuReaderNativeFrameHost` has not proved all Shadow/LegacyOnly writers unreachable;
initial route/owner/input/egress publication and successor owner/input are separate
consequences; and active Task 6 routes would have timer/lifecycle gaps or dual writers.
Record these as design prerequisites, not test flakiness.

- [ ] **Step 3: Implement common identity, receipt, material, and journal causality**

Implement the shared signatures above. Migrate `ReaderTransitionResourceKey` and the
ledger to `ReaderTransitionResourceOwnerId` plus
`ReaderTransitionResourceRegistration`; coordinator-created resources use
`TransitionOwned`, legacy resources can use only the handoff seed, and every first
physical registration receives owner-independent monotonic retirement order. Release
issuer, tombstone advancement, and late confirmation must not require a current
transition. Add `MaterialBindingAllocated` to fact classification and liveness proof
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
`Committing` awaiting exactly `OwnerAndInputPublicationAcknowledgement`, with Applied and
Rejected as exact callback sources, then emits only `CommitOwnerAndInputLease`. Only
matching `Applied(Successor)` may publish success/committed, cancel timer, and then release
predecessor. Rejection retains predecessor truth and releases successor only. Keep exactly
eleven operations: target preparation and publication acknowledgement are proofs/facts/
commands, not operations.

Create an opaque executable request handle for every semantic intent and a bounded
session registry whose values are private Foliate closures. Reserve a dedicated slot
before taking/invoking the handle; pass slot ID plus transition ID through
`ReaderPresentationEventOrigin.SemanticCommand`; never derive either from active state
or let unsolicited delivery consume a slot. Synchronous callbacks append to the
coordinator FIFO while `advancing` remains true. Retire handles/slots and bounded
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
match each complete composite identity, including post-freeze discoveries. Carry the
selected row's exact owner, truthful resource kind, binding, identity, and provenance
through seed, collision-free imported key, owner-independent registration, and narrowed
initial lease in that acyclic order. `KomikkuReaderNativeFrameHost` must authenticate and
construct all real production adapters; the production installation type cannot be
created by a public Boolean/`complete()` factory, no-op/rejecting fixture ports, or any
package retaining a reachable `Shadow`/`LegacyOnly` consequence writer. Call one
`installActivatedSession` with that package plus the complete initial decision; no
public initial publication, egress open, or mutable port setter exists. If failure
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
later synchronous no-callback/no-suspend owner/input publication port. Its typed result
is converted immediately into a FIFO fact while `advancing`; never reduce recursively
or discard it as `Unit`/Boolean. Publish `Committing` before the call and terminal state
before timer cancellation/release. `ReaderPageInputSettlementHostController` may only
narrow/veto inside that transaction; remove every separate activated `ApplyInputLease`.

In that same installation, select exactly one existing command-scoped physical timer
source. For narrowing operations, do not bind while retained publication is pending;
exact `Applied(Retained)` returns to pre-work, then bind exact `ReaderTransitionId` before
the first timer-requiring command. Its
only callback enqueues the exact typed `DeadlineExpired` fact through the FIFO; it cannot
mutate presentation/input/release, perform local `Retry`, or extend/rearm outside the
immutable command contract. Keep the production coordinator clock scheduling port
inactive until Task 7. Implement exact `snapshotForTask7Transfer` output without arming
the coordinator clock or transferring ownership in Task 6. Installation, supersession, and close must cancel/select as one
serialized ownership change so deadline-requiring work never has zero timer owners and
never has two. Suppress every legacy lifecycle consequence that overlaps activated
routes; keep existing lifecycle normalization as the sole ordered ingress through a
safety-fact-only adapter until Task 7. Do not create Task 7 restore/reflow/recovery/
deadline/wake policy.

Call `closeToReleaseOnly` before cancellation on every publication close path. Keep
the sink permanently able to register/release owner-independently ordered late
resources after timeout while rejecting every non-release consequence. Implement the
three Task 6/7 boundary rows.

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
initial owner/physical lease, `Activated`, and open egress; executable semantic handles
and isolated bounded slots remain FIFO/non-reentrant; every subordinate physical owner
is frozen/inventoried/drained or restored with collision-safe identity; physical work
follows exact allocation; retained input acknowledgement gates all successor work;
coordinator-led awaiting-target preparation supplies exact registration before any
presentation, target facts flow through FIFO, and adapters allocate no ownership; sealed
kind contracts carry legal tokens/generations/geometry/request evidence with no
pre-command frame sequence or binding-only polling/inference; adopted predecessor kind matches
its truthful owner; resource retirement is owner-independent and bounded; matching
prepared proof is removed before successor `Committing` retaining predecessor truth, whose sole awaited proof is the
publication acknowledgement and whose exact callback sources are Applied/Rejected;
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
all original preflight/amendment gaps plus the independently confirmed two-phase
publication and exact-frame-target defects and incomplete production composition are
closed; every named RED release blocker above passes; all eight activation states have
close tests; the common enum still has exactly eleven operations; proof enum and every
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
co-author footer, and pushes. Do not claim production activation before this Task 6
implementation and its gates actually complete; this amended plan itself is not
activation evidence.

### Task 7: Centralize lifecycle, reflow, and deadline ownership

Task 7 begins only after Task 6's atomic activation routes pass. It replaces the Task 6
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
    assertEquals(fixture.transitionId, fixture.clock.singleRegistration.transitionId)
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
and the continuing release sink after `CloseDrainTimeout`. Every case must keep exactly
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
    assertEquals(nonceA(), store.bootstrapRestoredOwner())
    assertNull(store.bootstrapRestoredOwner())
    assertEquals(nonceA(), store.consumeIfCurrent(wakeRestored(), issuedAt + 1)?.nonce)
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

Verify all 11 operations, eight activation states, one atomic installation containing
every route plus initial visible owner/physical lease/Activated/egress publication,
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
exactly one retained fact-only Task 6 timer per active attempt with exact transfer
snapshot exposure and the production coordinator clock inactive, atomic Task 7 timer transfer with no zero/two-owner interval,
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
- [ ] `installActivatedSession` accepts only the capability-authenticated real-adapter package from `KomikkuReaderNativeFrameHost`, publishes every Task 6 route, initial owner/physical lease, `Activated`, and open egress atomically; no installed-but-unpublished state, Shadow/LegacyOnly/no-op route, legacy consequence writer, or post-install fallback exists.
- [ ] One freeze token inventories every named subordinate owner with composite `(session/freeze domain, source, source-local opaque token)` identity; two versioned fixed-point snapshots precede install.
- [ ] Equal source-local IDs from different sources/domains never coalesce; only equal complete identities deduplicate, and drain/import/confirmation/release-once all use that complete identity.
- [ ] Post-drain failure restores one complete checkpoint or enters `ActivationBlocked`; direct partial `Legacy` rollback is impossible.
- [ ] The adopted seed preserves the selected row's exact physical identity, truthful owner/resource kind, binding, and provenance without a fabricated transition ID, binding-only inference, or seed/key cycle.
- [ ] Every semantic command executes a private request through an opaque handle and its own bounded one-shot slot.
- [ ] Resource retirement and bounded fences are owner/transition-independent.
- [ ] Fresh material allocation precedes every raster/deck physical command.
- [ ] For narrowing operations, acceptance emits only retained-owner publication; exact `Applied(Retained)` returns to pre-work, rejection terminates, and semantic/material/raster/deck/target/frame/timer work remains zero before acknowledgement.
- [ ] Coordinator selects admitted Deck or ledger-allocates FrameHandoff, publishes an awaiting-target phase, and issues `PrepareFrameTarget`; only matching FIFO `FrameTargetPrepared` stores target and permits presentation, while adapters never allocate ownership/retirement identity.
- [ ] Sealed shell/native/curl/live targets validate exact token state, generations, geometry, request sequence, owner/kind registration, session/publication/binding, and handle; no pre-command frame sequence or binding lookup/polling/inference/fabrication/replacement exists.
- [ ] Consuming matching `PreparedFrame` removes that proof and publishes `Committing` awaiting exactly `OwnerAndInputPublicationAcknowledgement` with Applied/Rejected callback sources; only exact queued `Applied(Successor)` succeeds before timer cancellation/predecessor release.
- [ ] Raw frame-target/token/claim/publication identities, request/presented-frame sequences, and registrations remain in-memory and absent from logs, diagnostics, analytics, screenshots, crash metadata, equality diagnostics, and persistence.
- [ ] Restoration deadline remains active until every exact asynchronous source confirmation and final atomic `commitRestoredLegacy` result.
- [ ] `ReleaseOnly` is permanent and rejects every non-release consequence after timeout.
- [ ] `T6-LIFECYCLE-FACT-ONLY`, `T6-NO-DEADLINE-TRANSFER`, and `T6-NO-WAKE-TRANSFER` prove exactly one retained fact-only timer per Task 6 attempt, exact `snapshotForTask7Transfer` exposure without transfer, an inactive production coordinator clock, no lifecycle dual writer, and preserved Task 7 lifecycle/reflow/recreation/deadline/wake policy.
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
- [ ] Project Task9 and Stage 7 remain unstarted.
