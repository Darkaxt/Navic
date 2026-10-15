package paige.navic.ui.screens.reader

import android.os.Looper
import android.webkit.WebView
import java.lang.ref.WeakReference
import paige.navic.reader.*

/** Temporary source-local categories for the original initial admission evaluation only.
 * No payload, callback, activation authority, or admission decision is stored here. Remove
 * after the source-causal repair; the original opening-custody regression remains. */
internal enum class ReaderInitialAdmissionStage {
    NotEvaluated, ExecutionBefore, ConstructionInactive, NativePhysicalEligibility,
    CandidateReservationTransitionEligibility, ExecutionAtActivation, InstallationStructure,
    InstallationCurrentness, InstallationCurrency, InstallationCurrencyCandidateStore,
    InstallationCurrencyExecution, EngineAncestry, ReporterVersion, ReporterGenerationValidity,
    ExecutionFinal, CapturedScopeIdentity, CapturedScopeVersion, CapturedScopeDimensions,
    CapturedScopeGenerations, ExecutionAfter, AuthorityUnavailable, AuthorityChanged, Admitted
}

/** Temporary synchronous event categories, not geometry, caller identity or readiness.
 * Only the original host fixture writes the active category; Native's actual size callback
 * records the first category after authentic initial scope capture. Remove with r24 probes. */
internal enum class ReaderInitialGeometryWitnessBoundary {
    OutsideFixture, CompositionFrame, OuterLayout, MaterialLayout, LooperDrain
}

/** One exact executor transport, from Native construction through its original Engine query.
 * The existing foreground source reports actual configure completion. It is not JS Ready,
 * Foliate/layout/material success, another callback registry, or another command dispatcher. */
internal class ReaderEngineInitialDestinationConstructionCustody(
    private val origin: ReaderPresentationEventOrigin.SemanticCommand,
    private val context: ReaderSemanticMutationStartEvidence,
    onInitialized: (WebView) -> Unit
) : ReaderSemanticTransportRetirement {
    private sealed interface Phase {
        class Waiting(val callback: (WebView) -> Unit) : Phase
        data object Starting : Phase
        class Query(val token: ReaderEngineCurrentDestinationContinuation) : Phase {
            var layoutOffered = false
            var layout: Pair<ReaderPresentationEventReceipt, ReaderFoliateResolvedLayout>? = null
        }
        data object Retired : Phase
    }
    private val lock = Any()
    private var phase: Phase = Phase.Waiting(onInitialized)
    private var mounted = false
    private var bindingAttempted = false
    private var executorBound = false
    private var initializedEngine: WeakReference<WebView>? = null
    val active: Boolean get() = synchronized(lock) { phase !== Phase.Retired }

    // Written only around the adopted token's authentic predicate invocation. The first
    // failure survives later admission calls and cleanup; these fields never authorize work.
    internal var initialAdmissionEvaluationActive = false
    internal var initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.NotEvaluated
    internal var initialAdmissionFirstFailure: ReaderInitialAdmissionStage? = null
    internal var initialAdmissionFirstFailurePredicateThrew = false
    internal var initialGeometryScopeCaptured = false
    internal var initialGeometryFirstChange: ReaderInitialGeometryWitnessBoundary? = null

    fun bindExecutor(): ReaderSemanticTransportBindingResult {
        synchronized(lock) {
            if (bindingAttempted) return ReaderSemanticTransportBindingResult.Rejected
            bindingAttempted = true
        }
        // Only the actual executor's successful handoff creates adoption authority.
        val result = try { context.bindTransportRetirement(origin, this) }
        catch (failure: Throwable) { retire(); throw failure }
        if (result == ReaderSemanticTransportBindingResult.Bound) {
            synchronized(lock) { executorBound = phase !== Phase.Retired }
            deliverInitialization()
        } else retire()
        return result
    }

    fun runtimeInitialized(engine: WebView) {
        check(Looper.myLooper() == Looper.getMainLooper())
        synchronized(lock) {
            if (phase !is Phase.Waiting) return
            val previous = initializedEngine?.get()
            if (previous != null && previous !== engine) return
            initializedEngine = WeakReference(engine)
        }
        deliverInitialization()
    }

    fun mountCompleted() {
        check(Looper.myLooper() == Looper.getMainLooper())
        synchronized(lock) { mounted = true }
        deliverInitialization()
    }

    private fun deliverInitialization() {
        val delivery = synchronized(lock) {
            val waiting = phase as? Phase.Waiting ?: return
            val engine = initializedEngine?.get() ?: return
            if (!mounted || !executorBound) return
            phase = Phase.Starting // Consume construction callback before external work.
            initializedEngine = null
            waiting.callback to engine
        }
        try { delivery.first(delivery.second) }
        catch (_: Throwable) { fail(ReaderSemanticAsyncFailure.Threw) }
    }

    /** The executor already owns this custody. Adopt its one real pending query without
     * rebinding the executor or replacing/wrapping the authentic mutation evidence. */
    fun adoptQuery(
        candidateOrigin: ReaderPresentationEventOrigin.SemanticCommand,
        candidateContext: ReaderSemanticMutationStartEvidence,
        token: ReaderEngineCurrentDestinationContinuation
    ): ReaderSemanticTransportBindingResult {
        if (candidateOrigin !== origin || candidateContext !== context || !token.initialBootstrap)
            return ReaderSemanticTransportBindingResult.Rejected
        val result = synchronized(lock) {
            if (!executorBound && phase !== Phase.Retired) return ReaderSemanticTransportBindingResult.Rejected
            when (phase) {
                Phase.Starting -> {
                    token.initialAdmissionWitness = this
                    phase = Phase.Query(token)
                    ReaderSemanticTransportBindingResult.Bound
                }
                Phase.Retired -> ReaderSemanticTransportBindingResult.RetiredAndDisposed
                else -> ReaderSemanticTransportBindingResult.Rejected
            }
        }
        if (result == ReaderSemanticTransportBindingResult.RetiredAndDisposed) token.retire()
        return result
    }

    /** Subordinate numeric handoff only, after the original producer validates its
     * consumed result and receipt. Neither retention nor taking allocates material,
     * upgrades a profile, creates a binding or transfers executor retirement. */
    fun retainResolvedLayout(
        token: ReaderEngineCurrentDestinationContinuation,
        receipt: ReaderPresentationEventReceipt,
        layout: ReaderFoliateResolvedLayout
    ): Boolean = synchronized(lock) {
        val query = phase as? Phase.Query ?: return@synchronized false
        val event = receipt.event as? ReaderPresentationEvent.FoliateRelocated ?: return@synchronized false
        if (query.token !== token || token.retired || query.layoutOffered || receipt.origin !== origin ||
            receipt.semanticReceipt?.transitionId != origin.transitionId || receipt.semanticReceipt?.binding != event.binding ||
            event.binding.foliateSessionId != token.expectedFoliateSessionId ||
            receipt.preVersion.publicationIdentity != null || receipt.effects.isNotEmpty()) return@synchronized false
        query.layoutOffered = true
        query.layout = receipt to layout
        true
    }

    fun takeResolvedLayout(receipt: ReaderPresentationEventReceipt): ReaderFoliateResolvedLayout? = synchronized(lock) {
        val query = phase as? Phase.Query ?: return@synchronized null
        val retained = query.layout ?: return@synchronized null
        if (query.token.retired || retained.first !== receipt) return@synchronized null
        query.layout = null // Consume before the Native owner can invoke another callback.
        retained.second
    }

    fun fail(failure: ReaderSemanticAsyncFailure) {
        val wasActive = retireExact()
        if (wasActive) context.reportAsyncFailure(origin, failure)
    }
    override fun retire() { retireExact() }
    private fun retireExact(): Boolean {
        val previous = synchronized(lock) {
            val previous = phase
            phase = Phase.Retired
            initializedEngine = null
            previous
        }
        // No executor/transport disposal or caller callback under this custody lock.
        (previous as? Phase.Query)?.token?.retire()
        return previous !== Phase.Retired
    }
    override fun toString() = "ReaderEngineInitialDestinationConstructionCustody(<redacted>)"
}
