package paige.navic.ui.screens.reader

import android.webkit.WebView
import java.lang.ref.WeakReference
import paige.navic.reader.*

/** Initial semantic producer on the actual Engine. It uses the existing owning open/query
 * ledger, never the Native already-bound confirmation factory or a renderer binding seed.
 * Layout generations are captured Native scope; provisional profile zero stays provisional.
 * No frame/material readiness, input publication, installation, or receipt is synthesized. */
internal fun readerEngineInitialDestinationSemanticRequest(
    engine: WebView,
    reservation: ReaderReservedNeutralBootstrapRequest,
    version: ReaderPresentationReceiptVersion,
    publicationGeneration: Long,
    viewportGeneration: Long,
    profileGeneration: Long,
    isAdmissionCurrent: () -> Boolean,
    constructionCustody: ReaderEngineInitialDestinationConstructionCustody? = null,
    ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
): ReaderSemanticExecutableRequest {
    val capturedEngine = WeakReference(engine)
    return request@{ origin, context, onReceipt ->
        val target = capturedEngine.get() ?: return@request ReaderSemanticExecutableResult.Rejected
        val source = target as? ReaderEngineCurrentDestinationQuerySource
            ?: return@request ReaderSemanticExecutableResult.Rejected
        val id = origin.transitionId
        if (reservation.readerSessionGeneration != version.readerSessionGeneration || version.publicationIdentity != null ||
            id.readerSessionGeneration != reservation.readerSessionGeneration ||
            id.operation != ReaderTransitionOperation.BootstrapNativePage ||
            id.expectedBinding !is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
            publicationGeneration <= 0L || viewportGeneration <= 0L || profileGeneration < 0L
        ) return@request ReaderSemanticExecutableResult.Rejected
        fun current(): Boolean {
            val witness = constructionCustody?.takeIf { it.initialAdmissionEvaluationActive }
            witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ExecutionBefore
            if (!context.isExecutionCurrent(origin, reservation.handle)) return false
            if (!isAdmissionCurrent()) return false
            witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ExecutionAfter
            return context.isExecutionCurrent(origin, reservation.handle)
        }
        if (!current()) return@request ReaderSemanticExecutableResult.Rejected
        // Query submission binds the exact executor retirement before touching its pending
        // command entry. Query ACK/Ready/location observations never reach this callback.
        source.queryInitialDestination(origin, context, ::current, constructionCustody) outcome@{ outcome ->
            when (outcome) {
                is ReaderCurrentDestinationQueryOutcome.Failed -> context.reportAsyncFailure(origin,
                    if (outcome.reason == ReaderCurrentDestinationQueryFailure.SubmissionThrew) ReaderSemanticAsyncFailure.Threw
                    else ReaderSemanticAsyncFailure.Rejected)
                is ReaderCurrentDestinationQueryOutcome.Resolved -> {
                    val token = outcome.carrier as? ReaderEngineCurrentDestinationContinuation
                    if (token == null || !token.initialBootstrap || !token.belongsTo(target)) {
                        context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Rejected)
                        return@outcome
                    }
                    if (token.retired) return@outcome
                    val event = outcome.event
                    if (!current() || !source.admitsCurrentDestinationSession(event.foliateSessionId) ||
                        event.foliateSessionId != token.expectedFoliateSessionId ||
                        event.destinationCommitIdentity.foliateSessionId != token.expectedFoliateSessionId ||
                        event.locator.pageIndex?.let { it < 0 } == true
                    ) {
                        context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Rejected)
                        return@outcome
                    }
                    val binding = ReaderPresentationBinding(event.foliateSessionId, publicationGeneration,
                        viewportGeneration, profileGeneration, event.destinationCommitIdentity)
                    val initial = ReaderPresentationEvent.FoliateRelocated(binding, null)
                    val receipt = try { ingress(initial, origin) } catch (_: Throwable) {
                        if (!token.retired) context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Threw)
                        return@outcome
                    }
                    // Ingress may cancel/close or replace the exact model synchronously. A
                    // recreated/stale receipt, even equal-valued, never supplies authority.
                    if (token.retired) return@outcome
                    if (!current() || receipt == null || receipt.event !== initial || receipt.origin !== origin ||
                        receipt.preVersion != version || receipt.postState.binding != binding || receipt.effects.isNotEmpty() ||
                        receipt.version.readerSessionGeneration != version.readerSessionGeneration ||
                        receipt.version.publicationIdentity != binding.publicationIdentity ||
                        receipt.semanticReceipt?.binding != binding || receipt.semanticReceipt?.transitionId != id
                    ) {
                        context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Rejected)
                        return@outcome
                    }
                    // Carry validated layout and the original query basis through construction
                    // custody. Native takes them for this exact receipt before executor retirement;
                    // absent/malformed layout remains absent, never configuration readiness.
                    event.resolvedLayout?.let { constructionCustody?.retainResolvedLayout(token, receipt, event) }
                    // Existing executor forwards the receipt FIFO before finally retiring its
                    // captured transport outside locks. This producer never disposes that owner.
                    onReceipt(receipt)
                }
            }
        }
    }
}
