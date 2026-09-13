# Reader Resumable Transition Coordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every delegated worker must be told: **“Do not spawn subagents.”**

**Goal:** Replace the distributed Android reader transition control plane with one resumable coordinator that gives every accepted event one terminal outcome, one deadline owner, one input lease, one named wake route, and exact-once resource release.

**Architecture:** Live Foliate remains semantic authority, passive Foliate remains material-only, PlayLikeCurl remains deformation and renderer-resource authority, and the common presentation reducer remains pure visual policy. A new Android main-thread coordinator owns transition identity, a non-reentrant mailbox, command ordering, deadlines, deferrals, release accounting, and terminal publication; callbacks become typed fact producers and command-only adapters.

**Tech Stack:** Kotlin Multiplatform, Android View/WebView and SavedState Registry APIs, Compose Multiplatform, PlayLikeCurl, `kotlin.test`, Robolectric Android host tests, Gradle, and JavaScript source-contract gates.

---

## Governing documents

- Specification: `docs/superpowers/specs/2026-09-13-reader-resumable-transition-coordinator-design.md`
- Active Stage 6 ledger: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`
- Superseded implementation record: `docs/superpowers/plans/2026-09-01-reader-hierarchical-presentation-authority.md`

This is one tightly coupled Stage 6 replacement. Do not activate deck,
presentation, input, lifecycle, deadline, persistence, or release ownership as
independent competing control planes.

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

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt` — common identity, operation, phase, outcome, fact, command, finite wake, liveness, resume-record, and pure journal types.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt` — main-thread mailbox, journal advancement, deadlines, one-shot consumption, release ledger, terminal outcomes, and closed release sink.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt` — semantic, raster, deck, frame, input, resource, presentation, wake, and clock ports.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStore.android.kt` — SavedStateRegistry-backed opaque wake records and restored-owner bootstrap.
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionModelTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionLivenessTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinatorTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionReleaseLedgerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmissionCutoverTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderTransitionWakeStoreTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionIntegratedSequenceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionSourceAuditTest.kt`

### Refactor into fact/command adapters

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationReceipt.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
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
    ViewportProfileReplaced,
    RasterProgress,
    RasterProven,
    RasterDeferred,
    RasterFailed,
    ResourceObserved,
    DeckPrepared,
    DeckRejected,
    ResourceReleased,
    RendererCapacityAvailable,
    PreparedFrame,
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

enum class ReaderTransitionCapabilityKind { Host, WebView, Renderer }

data class ReaderTransitionResourceKey(
    val transitionId: ReaderTransitionId,
    val kind: ReaderTransitionResourceKind,
    val opaqueId: Long
)

enum class ReaderTransitionProofKind {
    SemanticDestination,
    Raster,
    DeckOwnership,
    DeckPrepared,
    PreparedFrame,
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
        val gestureId: Long
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
sealed interface ReaderSemanticSynchronizationIntent : ReaderTransitionUserIntent

data class ReaderPageTurnIntent(
    val direction: ReaderPageTurnDirection
) : ReaderSemanticSynchronizationIntent

data class ReaderExternalRelocationIntent(
    val source: ReaderExternalRelocationSource
) : ReaderSemanticSynchronizationIntent

data object ReaderCoverEntryIntent : ReaderSemanticSynchronizationIntent
data object ReaderCoverReturnIntent : ReaderTransitionUserIntent
data object ReaderRetryIntent : ReaderTransitionUserIntent
data object ReaderCancelIntent : ReaderTransitionUserIntent

sealed interface ReaderTransitionFrameRequest {
    data class ShellCover(val binding: ReaderPresentationBinding) : ReaderTransitionFrameRequest
    data class NativePage(val binding: ReaderPresentationBinding) : ReaderTransitionFrameRequest
    data class LiveExposure(val binding: ReaderPresentationBinding) : ReaderTransitionFrameRequest
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

    data class RasterProgress(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
    data class RasterProven(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
    data class RasterDeferred(
        override val transitionId: ReaderTransitionId,
        val reason: ReaderTransitionDeferralReason
    ) : ReaderTransitionFact
    data class RasterFailed(
        override val transitionId: ReaderTransitionId,
        val reason: ReaderTransitionFailureReason
    ) : ReaderTransitionFact

    data class ResourceObserved(
        override val transitionId: ReaderTransitionId,
        val key: ReaderTransitionResourceKey
    ) : ReaderTransitionFact
    data class DeckPrepared(
        override val transitionId: ReaderTransitionId,
        val key: ReaderTransitionResourceKey
    ) : ReaderTransitionFact
    data class DeckRejected(
        override val transitionId: ReaderTransitionId,
        val key: ReaderTransitionResourceKey
    ) : ReaderTransitionFact
    data class ResourceReleased(
        override val transitionId: ReaderTransitionId,
        val key: ReaderTransitionResourceKey
    ) : ReaderTransitionFact
    data class RendererCapacityAvailable(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact

    data class PreparedFrame(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding
    ) : ReaderTransitionFact
    data class CoverPostDraw(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding
    ) : ReaderTransitionFact
    data class WebViewExposure(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding
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
    val phase: ReaderTransitionPhase
)

data class ReaderTransitionJournal(
    val active: ReaderActiveTransition? = null,
    val consumedSettlements: Set<ReaderSettlementConsumptionKey> = emptySet(),
    val lastOutcome: ReaderTransitionOutcome? = null
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
fact. It records settlement consumption in the returned state before returning any
commands.

The common command hierarchy uses only common types:

```kotlin
sealed interface ReaderTransitionCommand {
    val transitionId: ReaderTransitionId

    data class RequestSemanticSynchronization(
        override val transitionId: ReaderTransitionId,
        val intent: ReaderSemanticSynchronizationIntent
    ) : ReaderTransitionCommand

    data class RequestRasterPreparation(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding
    ) : ReaderTransitionCommand

    data class ReserveDeck(
        override val transitionId: ReaderTransitionId,
        val binding: ReaderPresentationBinding,
        val role: ReaderTransitionDeckRole
    ) : ReaderTransitionCommand

    data class RequestFramePresentation(
        override val transitionId: ReaderTransitionId,
        val request: ReaderTransitionFrameRequest
    ) : ReaderTransitionCommand

    data class ApplyInputLease(
        override val transitionId: ReaderTransitionId,
        val lease: ReaderTransitionInputLease
    ) : ReaderTransitionCommand

    data class ReleaseResource(
        override val transitionId: ReaderTransitionId,
        val key: ReaderTransitionResourceKey
    ) : ReaderTransitionCommand

    data class CancelOwnedWork(
        override val transitionId: ReaderTransitionId
    ) : ReaderTransitionCommand
}
```

Facts cover every specification ingress: user intent, destination commit, settlement
acknowledgement, viewport/profile replacement, raster progress/proof/deferral/failure,
deck reservation/ownership/prepared/rejected/released/capacity, prepared frame,
cover post-draw, WebView proof, visibility, resource loss, deadline expiry, Retry,
and publication close.

A settlement is consumed before consequences:

```kotlin
data class ReaderSettlementConsumptionKey(
    val transitionId: ReaderTransitionId,
    val expectedBinding: ReaderExpectedPresentationBinding,
    val acknowledgement: ReaderPageTurnSettlementAck
)
```

## Migration ledger

Every row is a Task384 implementation and audit obligation.

| Legacy writer | Package | Replacement fact → command | Deadline/proof/outcome | Release/wake and deletion check |
|---|---:|---|---|---|
| `ReaderPresentationAuthority.kt:readerPresentationReduce` | 1, 5, 10 | all normalized facts → decision/lease/operation commands | operation matrix → one terminal outcome | coordinator ledger; no legacy effect dispatch |
| `ReaderPresentationControllerReducer.onPresentationEvent/onViewerAction` | 5, 10 | UI intent → semantic/cover/frame command | destination + frame → success/failure/cancel | no Android consequence in controller |
| `ReaderPresentationBindingReporter.update/reserve/classifyReceipt/commitReceipt` | 2, 3, 7, 10 | binding/receipt/deck facts → register/reserve | requesting transition only | stale deck ledger; class deleted |
| Receipt dispatcher and host effect handlers | 2, 7, 10 | port receipt/failure → commit/lease/retry | command acknowledgement → terminal or host deferral | `HostAvailable`/`PresentationCommandApplied`; queues deleted |
| Lifecycle delivery and retry queue | 8, 10 | visibility/pressure/loss/close → cancel/persist/recover | lifecycle matrix | exact cancellation; delivery queue deleted |
| Host bridge transition starters | 7, 10 | host/draw/WebView proof → cover/exposure/handback | 2 seconds → commit or retained failure | registration ledger; autonomous starters deleted |
| Native page publisher | 7, 10 | prepared-frame/failure → present frame | 2 seconds → commit or retained failure | callback ledger; autonomous update deleted |
| Presentation and relocation timeout owners | 8, 10 | deadline expiry → coordinator clock | one phase record → fail/defer | cancel once; timeout files deleted |
| Native host direct presentation writers | 2, 7, 8, 10 | host/profile/frame facts → gateway/render commands | matching ID/binding | ledger only; named writers removed |
| Input settlement host controller | 7, 8, 10 | pointer/gesture/cancel → apply lease/cancel | gesture + settlement deadline | no local grant policy |
| Raster preparation controller | 4, 8, 10 | progress/proof/defer/failure → prepare/cancel | exact generation/profile/binding, 10/30 seconds | passive-resource ledger; no readiness consequence |
| Deferred raster retry coordinator | 8, 10 | typed deferral → persist/consume/cancel | 15 minutes or one restoration | exact finite wake; file deleted |
| Deck admission and lease host | 3, 4, 10 | reserved/owned/prepared/rejected/released/capacity → reserve/build/release | material deadline | one ledger key; host currency deleted |
| PlayLikeCurl Foliate controller local transition writers | 4, 5, 7, 10 | deck/curl/settlement/renderer → deck/semantic/frame/release | exact ownership; 5-second settlement | ledger; listed writers absent |
| Deck recovery coordinator | 4, 10 | repair/deck/capacity → reserve/build/release | 10/30 seconds | submitted/unsubmitted ledger; file deleted |
| Process state and ViewModel | 8, 10 | `Restored` → consume/request fresh facts | 15 minutes/one use | no coordinator fields in UI snapshot |
| Compose root/screen/platform callbacks | 2, 5, 7, 8, 10 | intent/restored wake → gateway | render immutable outcome only | no release or direct Retry/effect plumbing |

---

### Task 1: Define the pure transition model and liveness table

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderResumableTransition.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionModelTest.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderResumableTransitionLivenessTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

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
    val first = journalAwaitingSettlement().reduce(matchingSettlementFact())
    assertEquals(1, first.state.consumedSettlements.size)
    assertTrue(first.commands.none { it is ReaderTransitionCommand.ApplyInputLease })
    val duplicate = first.state.reduce(matchingSettlementFact())
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
replacement/close.

- [ ] **Step 4: Run focused GREEN**

Run the Step 2 command. Expected: both classes pass with every operation mapped to
proof, deadline, retained owner, input lease, terminal outcome, and finite wake.

- [ ] **Step 5: MAIN audits, documents, commits, and pushes**

Record Slice 1 as shadow-only. MAIN stages only the five paths above, runs
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

Also cover FIFO ingress, stale classification, synchronous port callbacks, and one
scheduled callback per active phase.

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

### Task 3: Centralize release accounting and cut over deck admission

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
fun staleResourceReceivesOneReleaseCommandAndConfirmation() {
    val ledger = ReaderTransitionReleaseLedger()
    val key = deckKey(transitionId(), opaqueId = 41L)
    assertTrue(ledger.register(key))
    assertNotNull(ledger.requestRelease(key))
    assertNull(ledger.requestRelease(key))
    assertTrue(ledger.confirmReleased(key))
    assertFalse(ledger.confirmReleased(key))
    assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
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

- [ ] **Step 3: Implement exact release states and activation order**

```kotlin
internal enum class ReaderTransitionResourceState { Owned, ReleaseCommandIssued, Released }

internal class ReaderTransitionReleaseLedger {
    private val states = linkedMapOf<ReaderTransitionResourceKey, ReaderTransitionResourceState>()

    fun register(key: ReaderTransitionResourceKey): Boolean =
        states.putIfAbsent(key, ReaderTransitionResourceState.Owned) == null

    fun requestRelease(key: ReaderTransitionResourceKey): ReaderTransitionCommand.ReleaseResource? {
        if (states[key] != ReaderTransitionResourceState.Owned) return null
        states[key] = ReaderTransitionResourceState.ReleaseCommandIssued
        return ReaderTransitionCommand.ReleaseResource(key.transitionId, key)
    }

    fun confirmReleased(key: ReaderTransitionResourceKey): Boolean {
        if (states[key] != ReaderTransitionResourceState.ReleaseCommandIssued) return false
        states[key] = ReaderTransitionResourceState.Released
        return true
    }
}
```

Activation order is freeze legacy admission → inventory all owned/pending/discovered
resources → import ledger keys → adopt at most one provable predecessor → release and
confirm all others → atomically close legacy admission and open coordinator
admission. Never fall back during that reader session.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: exact-once release and no dual writer.

- [ ] **Step 5: MAIN records privacy-safe counts, commits, and pushes**

Record only inventory/adoption/release counts and activation outcome. Commit
`feat(reader): centralize deck admission ownership` and push.

### Task 4: Convert raster, renderer callbacks, and recovery into ports

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderResumableTransitionPorts.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderDeckAdmission.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`
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
port boundary. Renderer callbacks emit facts only. Release methods execute only
coordinator `ReleaseResource`; preparation phase/proof emits facts only.
`completeObservedDeckAdmission`, `retryAwaitingDeckAdmission`, and standalone local
recovery progression become unreachable.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: pass; material completion without frame proof cannot publish
`Ready` or admit input.

- [ ] **Step 5: MAIN reconciles Slice 2 rows, commits, and pushes**

Commit `refactor(reader): route raster decks through coordinator` and push.

### Task 5: Cut over one-shot semantic facts and external relocation

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

- [ ] **Step 3: Perform semantic cutover**

Route page turn, shell-cover entry, TOC, search, bookmark, annotation, jump, Retry,
and cancel through `ReaderTransitionGateway`. App-originated relocation registers
`ExternalSemanticRelocation` before `RequestSemanticSynchronization`; unsolicited
`FoliateDestinationCommitted` creates the same operation with semantic proof already
satisfied. Consumption is persisted before any consequence.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: all semantic tests pass.

- [ ] **Step 5: MAIN records predecessor release/reuse paths, commits, and pushes**

Account for successor commit, Retry, subsequent relocation/publication replacement,
and close. Commit `refactor(reader): coordinate semantic relocation` and push.

### Task 6: Cut over frame presentation and physical input together

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderNativePagePresentationPublisherTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostControllerTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt`
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`

- [ ] **Step 1: Write grouped RED presentation/input tests**

```kotlin
@Test
fun frameCommitPublishesOwnerAndLeaseInOneDrainTurn() {
    val fixture = presentationFixture(retainedOwner = coverOwner())
    fixture.enqueue(pageEntryIntent())
    fixture.proveRaster()
    fixture.proveDeck()
    fixture.provePreparedFrame()
    assertEquals(nativeOwner(), fixture.decision.frameOwner)
    assertEquals(nativeInputLease(), fixture.inputLease)
    assertEquals(1, fixture.commitPublicationCount)
    assertEquals(1, fixture.maxAdvanceDepth)
}

@Test
fun materialWithoutFrameProofCannotGrantInputOrHideCover() {
    val fixture = presentationFixture(retainedOwner = coverOwner())
    fixture.enqueue(pageEntryIntent())
    fixture.proveRaster()
    fixture.proveDeck()
    assertEquals(coverOwner(), fixture.decision.frameOwner)
    assertEquals(ReaderTransitionInputLease.ChromeOnly, fixture.inputLease)
    assertFalse(fixture.coverHidden)
}
```

Also test cover post-draw, native/live handoffs, one claimed gesture stream, stale
frame proof, retained input on failure, and supersession.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderPresentationHostBridgeTest" \
  --tests "paige.navic.ui.screens.reader.ReaderNativePagePresentationPublisherTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageInputSettlementHostControllerTest" \
  --tests "paige.navic.ui.screens.reader.KomikkuReaderNativeFrameHostTest"
```

Expected: bridge/publisher starts a transition or input is granted locally.

- [ ] **Step 3: Convert presentation and input adapters**

`ReaderPresentationHostBridge` accepts coordinator frame commands and emits only
post-draw/handoff facts. `ReaderPageInputSettlementHostController` accepts an exact
lease, may veto for local safety, and cannot broaden it. `KomikkuReaderNativeFrameHost`
renders the immutable coordinator decision and stops maintaining a second
presentation state machine.

```kotlin
fun applyLease(command: ReaderTransitionCommand.ApplyInputLease)
fun dispatchPointer(event: ReaderPageHostPointerEvent): ReaderPageHostPointerDispatchResult
fun reportCancellation(
    transitionId: ReaderTransitionId,
    reason: ReaderPageLifecycleCancellationReason
)
```

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: owner and lease commit atomically; stale proof changes neither.

- [ ] **Step 5: MAIN proves one writer, commits, and pushes**

Commit `refactor(reader): coordinate frame and input authority` and push.

### Task 7: Centralize lifecycle, reflow, and deadline ownership

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
fun visibilityLossTerminatesPhysicalAttemptAsDeferral() {
    val fixture = activePageEntryFixture()
    fixture.enqueue(visibilityLost())
    assertNull(fixture.activeTransition)
    assertIs<ReaderTransitionOutcome.Deferred>(fixture.lastOutcome)
    assertEquals(ReaderTransitionWakeKind.VisibilityRestored, fixture.lastResumeRecord.requiredWake)
}
```

Also cover scheduler rejection, supersession cancellation once, reflow retaining
semantic authority, renderer loss, fresh restore proof, 2-second close drain, and the
continuing release sink after `CloseDrainTimeout`.

- [ ] **Step 2: Run focused RED**

```bash
./gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderResumableTransitionCoordinatorTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPresentationLifecycleTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPresentationTransitionTimeoutTest"
```

Expected: independent lifecycle/deadline ownership remains.

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

Each phase replacement cancels one prior clock registration and schedules one
callback at `nextExpiry()`. Progress changes only `lastProgressAt`. Scheduler failure
enqueues immediate expiry. `Committing` cannot extend hard expiry; close uses its
separate drain record.

- [ ] **Step 4: Run focused GREEN**

Run Step 2. Expected: one callback per phase and every lifecycle attempt terminates.
Do not delete old files until Task 10’s source audit proves every route moved.

- [ ] **Step 5: MAIN reconciles lifecycle rows, commits, and pushes**

Commit `refactor(reader): centralize transition liveness` and push.

### Task 8: Add bounded opaque wake persistence

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

Also assert only the coordinator originates release commands and input leases.

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

- [ ] **Step 5: MAIN reconciles all 17 rows, commits, and pushes**

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

Verify all 11 operations, 10 finite wakes, terminal outcomes, one-shot settlement,
external relocation shield, one deadline record, exact-once release after close,
restore bootstrap before nonce generation, no direct adapter presentation/input
writer, all 17 migration rows, Task382 patch absence, evidence-worktree preservation,
and no Bindery/device/emulator/ADB/`.codex-validation`/project-Task9/Stage-7 work.

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

- [ ] Five migration slices and all 17 writer rows are complete.
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
