package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import paige.navic.reader.*

/** One token shared by the ORIGINAL Curl waiting cell and ORIGINAL Engine command ledger.
 * Renderer observations with no completion can drive transport, never terminal-frame proof. */
internal class ReaderEngineInstalledPageTurnContinuation(
    engine: ReaderEngineWebView,
    val source: ReaderInstalledCurlGestureSource,
    val origin: ReaderPresentationEventOrigin.SemanticCommand,
    private val context: ReaderSemanticMutationStartEvidence,
    private val isAdmissionCurrent: () -> Boolean,
    private val ingress: ReaderBoundPageTurnSemanticIngress,
    private val onReceipt: (ReaderPresentationEventReceipt) -> Unit
) : ReaderInstalledPageTurnContinuation, ReaderSemanticTransportRetirement {
    private val engine = WeakReference(engine)
    private val retired = AtomicBoolean(false)
    private val delivered = AtomicBoolean(false)
    private var mutation: ReaderForegroundWebViewOwnership.InstalledMutation? = null
    private var ordinal: Int? = null
    private var commandId: String? = null
    private var primitiveStarted = false

    fun admits(): Boolean = !retired.get() && source.work.installationIsCurrent() &&
        engine.get()?.runtimeRetired == false && context.isExecutionCurrent(origin, source.intent.requestHandle) &&
        runCatching(isAdmissionCurrent).getOrDefault(false) && !retired.get() &&
        context.isExecutionCurrent(origin, source.intent.requestHandle)

    fun onOriginalSettlement(observation: ReaderInstalledCurlSettlementObservation) {
        if (retired.get() || observation.source !== source) return
        if (!admits() || observation.stage != ReaderInstalledCurlSettlementStage.Completed ||
            observation.rendererDeckGeneration != source.deck.textureGeneration) { fail(); return }
        val settled = observation.settledOrdinal?.takeIf { it >= 0 } ?: run { fail(); return }
        val target = engine.get() ?: run { fail(); return }
        val owned = source.reserveOriginalForegroundMutation(this) ?: run { fail(); return }
        mutation = owned
        // Admission can cancel this token reentrantly. Return the exact just-reserved owner.
        if (!admits()) { retire(); return }
        val id = "reader-turn-${origin.transitionId.readerSessionGeneration}-${origin.transitionId.coordinatorEpoch}-${origin.transitionId.sequence}-${origin.slotId.value}"
        ordinal = settled
        commandId = id
        val predecessor = source.deck.binding.destinationCommitIdentity ?: run { fail(); return }
        val command = ReaderBridgeCommand.GoToVisualPage(settled, id, source.intent.gestureId.value,
            source.deck.binding.foliateSessionId, source.deck.rasterGeneration, source.deck.textureGeneration,
            owned.generation.value, predecessor.commitSequence)
        if (!target.submitInstalledPageTurn(this, ReaderBridgeDispatchCommand(id, command))) fail()
    }

    /** Called adjacent to the original evaluateJavascript primitive, not when queued. */
    fun invokeNavigation(invoke: () -> Unit): Boolean {
        val owned = mutation ?: return false
        if (!admits() || primitiveStarted) return false
        return source.invokeOriginalForegroundMutation(owned) {
            primitiveStarted = true
            context.mutationStarted()
            invoke()
        }
    }

    override fun complete(event: ReaderBridgeEvent.LocationChanged?) {
        if (retired.get() || !delivered.compareAndSet(false, true)) return
        if (event == null || !primitiveStarted || !admits() || event.commandId != commandId ||
            event.locator.pageIndex != ordinal || event.foliateSessionId != source.deck.binding.foliateSessionId ||
            event.destinationCommitIdentity?.foliateSessionId != event.foliateSessionId) { fail(); return }
        val receipt = try { ingress.ingest(source, origin, event) }
        catch (_: Throwable) { fail(ReaderSemanticAsyncFailure.Threw); return }
        if (retired.get()) return // Owning model ingress can synchronously cancel/close.
        val semantic = receipt?.semanticReceipt
        if (!admits() || receipt == null || receipt.origin !== origin ||
            semantic?.transitionId != origin.transitionId ||
            semantic.binding.destinationCommitIdentity != event.destinationCommitIdentity ||
            semantic.binding.foliateSessionId != event.foliateSessionId || receipt.effects.isNotEmpty()) { fail(); return }
        if (!source.retainOriginalSemanticReceipt(this, receipt)) { fail(); return }
        onReceipt(receipt) // Executor retires this same token after its FIFO receipt delivery.
    }

    fun fail(reason: ReaderSemanticAsyncFailure = ReaderSemanticAsyncFailure.Rejected) {
        if (retired.get()) return
        try { context.reportAsyncFailure(origin, reason) }
        finally { retire() }
    }

    override fun retire() {
        if (!retired.compareAndSet(false, true)) return
        fun remove() {
            source.retireOriginalSemanticTransportExact(this)
            mutation?.let(source::releaseOriginalForegroundMutation)
            mutation = null
            engine.get()?.retireInstalledPageTurnExact(this)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) remove()
        else Handler(Looper.getMainLooper()).post { remove() }
    }
    override fun toString() = "ReaderEngineInstalledPageTurnContinuation(<redacted>)"
}

internal fun readerEngineInstalledPageTurnSemanticRequest(
    engine: ReaderEngineWebView,
    source: ReaderInstalledCurlGestureSource,
    isAdmissionCurrent: () -> Boolean,
    ingress: ReaderBoundPageTurnSemanticIngress
): ReaderSemanticExecutableRequest {
    val captured = WeakReference(engine)
    return request@{ origin, context, onReceipt ->
        val target = captured.get() ?: return@request ReaderSemanticExecutableResult.Rejected
        if (Looper.myLooper() != Looper.getMainLooper() || target.runtimeRetired ||
            origin.transitionId.operation != ReaderTransitionOperation.CurlClaimAndSettlement ||
            origin.transitionId.readerSessionGeneration != source.work.domain.readerSessionGeneration ||
            origin.transitionId.coordinatorEpoch != source.work.lease.transitionId.coordinatorEpoch ||
            (origin.transitionId.expectedBinding as? ReaderExpectedPresentationBinding.SemanticSuccessor)?.predecessor != source.deck.binding ||
            !source.work.installationIsCurrent() ||
            !context.isExecutionCurrent(origin, source.intent.requestHandle) || !isAdmissionCurrent())
            return@request ReaderSemanticExecutableResult.Rejected
        val token = ReaderEngineInstalledPageTurnContinuation(target, source, origin, context, isAdmissionCurrent, ingress, onReceipt)
        when (context.bindTransportRetirement(origin, token)) {
            ReaderSemanticTransportBindingResult.Rejected -> { token.retire(); return@request ReaderSemanticExecutableResult.Rejected }
            ReaderSemanticTransportBindingResult.RetiredAndDisposed -> return@request ReaderSemanticExecutableResult.Rejected
            ReaderSemanticTransportBindingResult.Bound -> Unit
        }
        if (!token.admits() || !target.connectInstalledTurnRetirement(token) ||
            !source.bindOriginalSemanticTransport(token, token::onOriginalSettlement)) {
            token.retire()
            return@request ReaderSemanticExecutableResult.Rejected
        }
        ReaderSemanticExecutableResult.Accepted
    }
}
