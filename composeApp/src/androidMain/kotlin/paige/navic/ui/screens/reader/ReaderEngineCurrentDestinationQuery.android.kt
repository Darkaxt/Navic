package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import paige.navic.reader.ReaderCurrentDestinationContinuation
import paige.navic.reader.ReaderCurrentDestinationQueryOutcome
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticMutationStartEvidence
import paige.navic.reader.ReaderSemanticTransportRetirement
import paige.navic.reader.ReaderTransitionCommand

/** Original bound presentation/model/progress/WordSync producer. The Engine supplies the
 * genuine correlated location; only this original ingress can return an executor receipt.
 * Interface declaration is an integration contract, not proof that delivery is installed. */
internal fun interface ReaderBoundPageTurnSemanticIngress {
    fun ingest(
        source: ReaderInstalledCurlGestureSource,
        origin: ReaderPresentationEventOrigin.SemanticCommand,
        event: paige.navic.reader.ReaderBridgeEvent.LocationChanged
    ): paige.navic.reader.ReaderPresentationEventReceipt?
}

/** A capability of the existing Engine view, not another dispatcher or request registry. */
internal interface ReaderEngineCurrentDestinationQuerySource {
    fun createInstalledPageTurnSemanticRequest(
        source: ReaderInstalledCurlGestureSource,
        isAdmissionCurrent: () -> Boolean,
        ingress: ReaderBoundPageTurnSemanticIngress
    ): paige.navic.reader.ReaderSemanticExecutableRequest = { _, _, _ -> ReaderSemanticExecutableResult.Rejected }
    fun submitInstalledPageTurn(
        continuation: ReaderEngineInstalledPageTurnContinuation,
        dispatch: paige.navic.reader.ReaderBridgeDispatchCommand
    ): Boolean = false
    fun retireInstalledPageTurnExact(continuation: ReaderEngineInstalledPageTurnContinuation) = Unit

    fun createInitialDestinationSemanticRequest(
        reservation: ReaderReservedNeutralBootstrapRequest,
        version: ReaderPresentationReceiptVersion,
        publicationGeneration: Long,
        viewportGeneration: Long,
        profileGeneration: Long,
        isAdmissionCurrent: () -> Boolean,
        ingress: (paige.navic.reader.ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> paige.navic.reader.ReaderPresentationEventReceipt?
    ): paige.navic.reader.ReaderSemanticExecutableRequest = { _, _, _ -> ReaderSemanticExecutableResult.Rejected }
    fun createInitialDestinationConstructionSemanticRequest(
        reservation: ReaderReservedNeutralBootstrapRequest,
        version: ReaderPresentationReceiptVersion,
        publicationGeneration: Long,
        viewportGeneration: Long,
        profileGeneration: Long,
        isAdmissionCurrent: () -> Boolean,
        constructionCustody: ReaderEngineInitialDestinationConstructionCustody,
        ingress: (paige.navic.reader.ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> paige.navic.reader.ReaderPresentationEventReceipt?
    ): paige.navic.reader.ReaderSemanticExecutableRequest = { _, _, _ -> ReaderSemanticExecutableResult.Rejected }
    fun queryInitialDestination(
        origin: ReaderPresentationEventOrigin.SemanticCommand,
        context: ReaderSemanticMutationStartEvidence,
        isAdmissionCurrent: () -> Boolean,
        constructionCustody: ReaderEngineInitialDestinationConstructionCustody? = null,
        onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
    ): ReaderSemanticExecutableResult = ReaderSemanticExecutableResult.Rejected
    fun queryCurrentDestination(
        origin: ReaderPresentationEventOrigin.SemanticCommand,
        context: ReaderSemanticMutationStartEvidence,
        isAdmissionCurrent: () -> Boolean = { true },
        onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
    ): ReaderSemanticExecutableResult = ReaderSemanticExecutableResult.Rejected
    /** Read-only allocation-command custody, without rebinding a consumed semantic slot.
     * Null rejects; the original pending continuation owns exact transport cancellation.
     * Native generation/installation authority remains in the caller's admission predicate. */
    fun queryAllocationLayout(
        command: ReaderTransitionCommand.AllocateMaterialBinding,
        isAdmissionCurrent: () -> Boolean,
        onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
    ): ReaderSemanticTransportRetirement? = null
    fun admitsCurrentDestinationSession(foliateSessionId: String): Boolean = false
    /** Original successful query-delivery scope only. Source currency is distinct from
     * the consumed semantic execution and is not Foliate layout/presentation proof. */
    fun captureResolvedLayoutCurrency(): (() -> Boolean)? = null
    fun retireCurrentDestinationQueryExact(continuation: ReaderCurrentDestinationContinuation) = Unit
    fun closeCurrentDestinationQueries() = Unit
}

/** Exact unbound Native command-installation scope. Registration is construction custody,
 * not a semantic binding, visible owner, resolved layout or material readiness proof. */
internal class ReaderNativeInitialDestinationSemanticContext(
    val engine: WeakReference<WebView>,
    val installation: ReaderFramePhysicalOwnershipAdapter.Lease,
    val reporterEpoch: Long,
    val version: ReaderPresentationReceiptVersion,
    val width: Int,
    val height: Int,
    val publicationGeneration: Long,
    val viewportGeneration: Long,
    val profileGeneration: Long,
    val installedSnapshot: ReaderActivatedSessionSnapshot
) {
    override fun toString() = "ReaderNativeInitialDestinationSemanticContext(<redacted>)"
}

/** Numeric layout retained on the original physical installation after its validated
 * initial semantic receipt. CSS geometry stays distinct from Native measured pixels.
 * Allocation still requires current destination/configuration/physical-owner checks. */
internal class ReaderNativeInitialResolvedLayout(
    val scope: ReaderNativeInitialDestinationSemanticContext,
    val transitionId: paige.navic.reader.ReaderTransitionId,
    val binding: ReaderPresentationBinding,
    val layout: paige.navic.reader.ReaderFoliateResolvedLayout,
    val version: ReaderPresentationReceiptVersion,
    val sourceIsCurrent: () -> Boolean,
    val captureBasis: ReaderInitialCurrentDestinationCaptureBasis? = null
) {
    override fun toString() = "ReaderNativeInitialResolvedLayout(<redacted>)"
}

/** Immutable measured producers, not a material lease or a synthesized initial frame. */
internal class ReaderNativeCurrentDestinationSemanticContext(
    val engine: WeakReference<WebView>,
    val reporterEpoch: Long,
    val version: ReaderPresentationReceiptVersion,
    val binding: ReaderPresentationBinding,
    val width: Int,
    val height: Int,
    val visualPageIndex: Int,
    val profileEpoch: Long,
    val installedSnapshot: ReaderActivatedSessionSnapshot
) {
    override fun toString() = "ReaderNativeCurrentDestinationSemanticContext(<redacted>)"
}

/** One callback cell on one real pending entry. Cancellation clears authority on any thread. */
internal class ReaderEngineCurrentDestinationContinuation(
    view: WebView,
    val commandId: String,
    val expectedGeneration: Int,
    val expectedPublicationKey: String,
    val expectedFoliateSessionId: String,
    isAdmissionCurrent: () -> Boolean,
    onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit,
    val initialBootstrap: Boolean = false,
    // The genuine allocation command, not a fabricated semantic origin or second owner.
    val allocationCommand: ReaderTransitionCommand.AllocateMaterialBinding? = null
) : ReaderCurrentDestinationContinuation, ReaderSemanticTransportRetirement {
    private class Authority(val admission: () -> Boolean, val callback: (ReaderCurrentDestinationQueryOutcome) -> Unit)
    private val view = WeakReference(view)
    private val authority = AtomicReference<Authority?>(Authority(isAdmissionCurrent, onOutcome))
    private val wasRetired = AtomicBoolean(false)
    val retired: Boolean get() = wasRetired.get()
    val active: Boolean get() = authority.get() != null
    fun belongsTo(target: WebView): Boolean = view.get() === target

    // Original construction adoption supplies this temporary finite witness, not another
    // predicate/context/authority cell. Non-construction queries retain their exact path.
    internal var initialAdmissionWitness: ReaderEngineInitialDestinationConstructionCustody? = null

    fun admits(): Boolean {
        val witness = initialAdmissionWitness
        val captured = authority.get() ?: run {
            if (witness != null) {
                witness.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.AuthorityUnavailable
                if (witness.initialAdmissionFirstFailure == null) {
                    witness.initialAdmissionFirstFailure = ReaderInitialAdmissionStage.AuthorityUnavailable
                }
            }
            return false
        }
        val previouslyEvaluating = witness?.initialAdmissionEvaluationActive == true
        if (witness != null) {
            witness.initialAdmissionEvaluationActive = true
            witness.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ExecutionBefore
        }
        try {
            val result = runCatching { captured.admission() }
            val admitted = result.getOrDefault(false)
            if (!admitted) {
                if (witness != null && witness.initialAdmissionFirstFailure == null) {
                    witness.initialAdmissionFirstFailure = witness.initialAdmissionEvaluatedStage
                    witness.initialAdmissionFirstFailurePredicateThrew = result.isFailure
                }
                return false
            }
            // The external predicate can retire this very token reentrantly. Keep the
            // original second authority.get strictly on its admitted short-circuit branch.
            val survived = authority.get() === captured
            if (witness != null) {
                if (!survived && witness.initialAdmissionFirstFailure == null) {
                    witness.initialAdmissionFirstFailure = ReaderInitialAdmissionStage.AuthorityChanged
                }
                witness.initialAdmissionEvaluatedStage = if (survived) ReaderInitialAdmissionStage.Admitted
                    else ReaderInitialAdmissionStage.AuthorityChanged
            }
            return survived
        } finally {
            if (witness != null) witness.initialAdmissionEvaluationActive = previouslyEvaluating
        }
    }

    override fun complete(outcome: ReaderCurrentDestinationQueryOutcome) {
        val captured = authority.getAndSet(null) ?: return
        runCatching { captured.callback(outcome) }
    }

    override fun retire() {
        // Delivery may already have consumed the cell. The same token still records
        // cancellation during a reentrant model ingress before a receipt is forwarded.
        wasRetired.set(true)
        if (authority.getAndSet(null) == null) return // Synchronous, including off-thread cancellation.
        if (Looper.myLooper() == Looper.getMainLooper()) removeFromOwningLedger()
        else Handler(Looper.getMainLooper()).post { removeFromOwningLedger() }
    }

    private fun removeFromOwningLedger() {
        (view.get() as? ReaderEngineCurrentDestinationQuerySource)?.retireCurrentDestinationQueryExact(this)
    }
    override fun toString() = "ReaderEngineCurrentDestinationContinuation(<redacted>)"
}
