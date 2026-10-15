package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Scripted bridge transport only; no Foliate/render/material or positive Native acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, CarrierQueryWebViewShadow::class, Task398CanonicalStoragePathShadow::class])
class ReaderEngineCurrentDestinationCarrierHostTest {
    @Test fun publicationReplacementRetiresQueryWhileActualRuntimeDispatchGateIsUnavailable() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes); val query = bench.expectDispatch("resolveCurrentDestination")
            val view = bench.webView()
            view.loadUrl("https://synthetic.invalid/synthetic-runtime-unavailable")
            check(!shouldDispatchReaderCommandsToWebRuntime(true, view.url, ReaderWebRuntime.entrypointUrl))
            bench.replacePublication()
            assertEquals(1, outcomes.size)
            assertTrue((outcomes.single() as? ReaderCurrentDestinationQueryOutcome.Failed)?.reason == ReaderCurrentDestinationQueryFailure.PublicationReplaced)
            assertTrue(bench.pending().pendingCommands.none { it.currentDestinationContinuation != null })
            bench.post(bench.result(query)); assertEquals(1, outcomes.size)
            view.loadUrl(ReaderWebRuntime.entrypointUrl)
            bench.post(bench.envelope("ready")); bench.expectDispatch("openPublication")
            f.close()
        }
    }

    @Test fun workerRetirementClearsCallbackAuthorityBeforeEarlierQueuedResultDelivery() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes); val query = bench.expectDispatch("resolveCurrentDestination")
            val token = assertNotNull(bench.pending().pendingCommands.singleOrNull()?.currentDestinationContinuation)
            bench.bridge().postMessage(bench.result(query).toString())
            val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
            try { worker.submit { (token as ReaderSemanticTransportRetirement).retire() }.get() }
            finally { worker.shutdown() }
            bench.drain()
            assertTrue(outcomes.isEmpty())
            assertTrue(bench.pending().pendingCommands.none { it.currentDestinationContinuation != null })
            f.close()
        }
    }

    @Test fun genericFailureRetiresQueryBeforeReplacingRuntimeAndOldTokenIsInert() = runTest {
        withCurrentDestinationEngine { bench ->
            bench.submit(ReaderBridgeCommand.PreviousPage); val head = bench.expectDispatch("previousPage")
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes)
            val token = assertNotNull(bench.pending().pendingCommands.singleOrNull { it.currentDestinationContinuation != null }?.currentDestinationContinuation)
            val bridge = bench.bridge(); val old = bench.webView()
            bench.post(bench.envelope("commandFailed", head.id))
            assertEquals(1, outcomes.size)
            assertTrue((outcomes.single() as? ReaderCurrentDestinationQueryOutcome.Failed)?.reason == ReaderCurrentDestinationQueryFailure.RuntimeRetired)
            assertTrue(old !== bench.webView())
            bench.post(bench.envelope("ready")); val open = bench.expectDispatch("openPublication")
            bench.post(bench.envelope("commandAck", open.id))
            // The original ordinary request is replayed by the unchanged Engine generation policy.
            val ordinary = bench.pending().pendingCommands.firstOrNull()
            if (ordinary != null) bench.post(bench.envelope("commandAck", ordinary.dispatch.id))
            f.retry(); bench.expectDispatch("resolveCurrentDestination")
            (token as ReaderSemanticTransportRetirement).retire()
            bridge.postMessage(bench.envelope("commandFailed", head.id).toString()); bench.drain()
            assertEquals(1, bench.pending().pendingCommands.count { it.currentDestinationContinuation != null })
            assertEquals(1, outcomes.size)
            f.close()
        }
    }

    @Test fun actualRootClosesQueryBeforeNativeDetachRecipient() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            var nativeDetachObserved = false; var retiredBeforeNativeDetach = false
            val engine = bench.webView()
            engine.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) = Unit
                override fun onViewDetachedFromWindow(view: android.view.View) {
                    nativeDetachObserved = true
                    retiredBeforeNativeDetach = outcomes.size == 1 && bench.pending().pendingCommands.none { it.currentDestinationContinuation != null }
                }
            })
            f.query(bench, outcomes); bench.expectDispatch("resolveCurrentDestination")
            bench.closeRoot()
            assertTrue(nativeDetachObserved, "Actual Native detach recipient must execute")
            assertTrue(retiredBeforeNativeDetach, "Query custody must be retired before Native detach recipient")
            assertEquals(1, outcomes.size)
            f.close()
        }
    }

    @Test fun submissionThrowConsumesExactQueryAndReportsThrewWithoutReplacingView() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            val view = bench.webView()
            org.robolectric.shadow.api.Shadow.extract<CarrierQueryWebViewShadow>(view).throwNextQuery = true
            f.query(bench, outcomes); f.drain()
            assertEquals(1, outcomes.size)
            assertTrue((outcomes.single() as? ReaderCurrentDestinationQueryOutcome.Failed)?.reason == ReaderCurrentDestinationQueryFailure.SubmissionThrew)
            assertTrue(bench.pending().pendingCommands.isEmpty() && bench.webView() === view)
            assertTrue(f.coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed && !f.coordinator.snapshot().releaseOnlySink)
            assertEquals(1, f.registry.activeHandleCount)
            f.retry(); bench.expectDispatch("resolveCurrentDestination")
            assertEquals(1, f.handles.size)
            f.close()
        }
    }

    @Test fun duplicateBindingRejectsBeforeEffectsAndCannotReplaceExistingOwner() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); var cleanups = 0; var unexpectedCallbacks = 0
            var result: ReaderSemanticExecutableResult? = null
            var bound = false
            val before = bench.lastScript()
            f.dispatch { call ->
                bound = call.context.bindTransportRetirement(call.origin, ReaderSemanticTransportRetirement { cleanups++ }) == ReaderSemanticTransportBindingResult.Bound
                result = (bench.webView() as ReaderEngineCurrentDestinationQuerySource).queryCurrentDestination(call.origin, call.context) { unexpectedCallbacks++ }
                ReaderSemanticExecutableResult.Accepted
            }
            assertTrue(bound && result == ReaderSemanticExecutableResult.Rejected)
            assertEquals(0, unexpectedCallbacks)
            assertTrue(before == bench.lastScript() && bench.pending().pendingCommands.isEmpty())
            assertEquals(0, cleanups)
            f.cancel(); assertEquals(1, cleanups)
            f.close()
        }
    }

    @Test fun publicationReplacementConsumesContinuationBeforeCallback() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            var absent = false
            f.query(bench, outcomes, onOutcome = { absent = bench.pending().pendingCommands.none { it.currentDestinationContinuation != null } })
            val query = bench.expectDispatch("resolveCurrentDestination")
            bench.replacePublication()
            assertTrue(absent)
            assertEquals(1, outcomes.size)
            assertTrue((outcomes.single() as? ReaderCurrentDestinationQueryOutcome.Failed)?.reason == ReaderCurrentDestinationQueryFailure.PublicationReplaced)
            val open = bench.expectDispatch("openPublication")
            assertTrue(open.session != query.session)
            bench.post(bench.result(query)); assertEquals(1, outcomes.size)
            f.close()
        }
    }

    @Test fun actualViewQueryBindsBeforeJavascriptAndCompletesOnce() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture()
            val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            val before = bench.lastScript()
            var bindingBeforeEffects = false
            val call = f.dispatch { current ->
                (bench.webView() as ReaderEngineCurrentDestinationQuerySource).queryCurrentDestination(current.origin, current.context,
                    isAdmissionCurrent = {
                        if (bench.pending().pendingCommands.isEmpty()) {
                            bindingBeforeEffects = bench.lastScript() == before && current.context.bindTransportRetirement(current.origin,
                                ReaderSemanticTransportRetirement {}) == ReaderSemanticTransportBindingResult.Rejected
                        }
                        true
                    }) { outcomes += it }
            }
            val query = bench.expectDispatch("resolveCurrentDestination")
            assertTrue(bindingBeforeEffects)
            val pending = bench.pending().pendingCommands.single()
            assertNotNull(pending.currentDestinationContinuation)
            assertTrue(call.origin.transitionId.readerSessionGeneration == f.opened.state.readerSessionGeneration)
            // The exact executor already owns one transport binding: a second cannot replace it.
            var rejectedCleanup = 0
            assertTrue(call.context.bindTransportRetirement(call.origin, ReaderSemanticTransportRetirement { rejectedCleanup++ }) == ReaderSemanticTransportBindingResult.Rejected)
            bench.post(bench.result(query)); bench.post(bench.result(query))
            assertEquals(1, outcomes.size)
            assertTrue(outcomes.single() is ReaderCurrentDestinationQueryOutcome.Resolved)
            assertTrue(bench.pending().pendingCommands.isEmpty())
            assertEquals(0, rejectedCleanup)
            assertEquals(0, f.receipts)
            f.close()
        }
    }

    @Test fun consumeBeforeCallbackAndReentrantSuccessorAreExact() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            var absent = false; var successorNotStarted = false; var reentrantDispatchDeferred = false
            var retiredDuringClaimedCallback = false
            var firstCarrier: ReaderEngineCurrentDestinationContinuation? = null
            var scriptBeforeCompletion: String? = null
            var next: CarrierCall? = null
            val first = f.query(bench, outcomes) {
                absent = bench.pending().pendingCommands.none { it.currentDestinationContinuation != null }
                successorNotStarted = bench.pending().pendingCommands.firstOrNull()?.lastDispatchedGeneration == null
                f.cancel()
                retiredDuringClaimedCallback = firstCarrier?.retired == true
                next = f.query(bench, mutableListOf())
                reentrantDispatchDeferred = bench.lastScript() == scriptBeforeCompletion
            }
            val firstToken = assertNotNull(bench.pending().pendingCommands.singleOrNull()?.currentDestinationContinuation)
            firstCarrier = firstToken as ReaderEngineCurrentDestinationContinuation
            val query = bench.expectDispatch("resolveCurrentDestination")
            bench.submit(ReaderBridgeCommand.NextPage)
            scriptBeforeCompletion = bench.lastScript()
            bench.post(bench.result(query)); bench.post(bench.result(query))
            assertTrue(absent && successorNotStarted && reentrantDispatchDeferred)
            assertTrue(retiredDuringClaimedCallback)
            assertTrue((outcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Resolved)?.carrier === firstToken)
            assertEquals(1, outcomes.size)
            assertTrue(assertNotNull(next).origin != first.origin)
            assertEquals(2, f.calls.size)
            (firstToken as ReaderSemanticTransportRetirement).retire()
            assertEquals(1, bench.pending().pendingCommands.count { it.currentDestinationContinuation != null })
            bench.expectDispatch("nextPage")
            f.close()
        }
    }

    @Test fun ackUnrelatedUndispatchedAndWrongProofNeverComplete() = runTest {
        withCurrentDestinationEngine { bench ->
            bench.submit(ReaderBridgeCommand.PreviousPage)
            val head = bench.expectDispatch("previousPage")
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            val call = f.query(bench, outcomes)
            val pending = assertNotNull(bench.pending().pendingCommands.singleOrNull { it.currentDestinationContinuation != null })
            assertNull(pending.lastDispatchedGeneration)
            val query = EngineObservedDispatch(pending.dispatch.id, bench.openDispatch.session)
            bench.post(bench.result(query)); assertTrue(outcomes.isEmpty())
            bench.post(bench.envelope("commandAck", head.id)); bench.expectDispatch("resolveCurrentDestination")
            listOf(bench.envelope("commandAck", query.id), bench.envelope("ready"), bench.envelope("publicationReady"),
                bench.result(query, id = head.id), bench.result(query, session = "synthetic-wrong-session")).forEach(bench::post)
            assertTrue(outcomes.isEmpty())
            assertTrue(bench.pending().pendingCommands.single().currentDestinationContinuation === pending.currentDestinationContinuation)
            assertTrue(f.calls.single() === call)
            bench.post(bench.result(query)); assertEquals(1, outcomes.size)
            f.close()
        }
    }

    @Test fun commandFailureIsPremutationAndRetryRetainsSameHandleAndView() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes)
            val token = assertNotNull(bench.pending().pendingCommands.singleOrNull()?.currentDestinationContinuation)
            val first = bench.expectDispatch("resolveCurrentDestination"); val view = bench.webView()
            bench.post(bench.envelope("commandFailed", first.id)); f.drain()
            assertEquals(1, outcomes.size)
            assertTrue(outcomes.single() is ReaderCurrentDestinationQueryOutcome.Failed)
            assertTrue(view === bench.webView())
            assertTrue(f.coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed && !f.coordinator.snapshot().releaseOnlySink)
            assertEquals(1, f.registry.activeHandleCount); assertEquals(0, f.executor.activeSlotCount)
            f.retry(); val second = bench.expectDispatch("resolveCurrentDestination")
            assertTrue(second.id != first.id && f.calls[0].origin != f.calls[1].origin)
            assertEquals(1, f.handles.size)
            (token as ReaderSemanticTransportRetirement).retire()
            bench.post(bench.result(first)); bench.post(bench.envelope("commandFailed", first.id))
            assertEquals(1, outcomes.size)
            assertEquals(1, bench.pending().pendingCommands.count { it.currentDestinationContinuation != null })
            assertEquals(1, f.processed.count { it == ReaderTransitionFactKind.CommandRejected })
            f.close()
        }
    }

    @Test fun cancelQueuedQueryPreservesOrdinaryHeadAndOldTokenCannotRemoveFreshQuery() = runTest {
        withCurrentDestinationEngine { bench ->
            bench.submit(ReaderBridgeCommand.PreviousPage); val head = bench.expectDispatch("previousPage")
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes)
            val old = assertNotNull(bench.pending().pendingCommands.singleOrNull { it.currentDestinationContinuation != null })
            f.cancel()
            assertEquals(1, bench.pending().pendingCommands.size)
            assertTrue(bench.pending().pendingCommands.single().dispatch.id == head.id)
            f.query(bench, outcomes)
            val fresh = assertNotNull(bench.pending().pendingCommands.singleOrNull { it.currentDestinationContinuation != null })
            (old.currentDestinationContinuation as ReaderSemanticTransportRetirement).retire()
            assertTrue(bench.pending().pendingCommands.last().currentDestinationContinuation === fresh.currentDestinationContinuation)
            bench.post(bench.result(EngineObservedDispatch(old.dispatch.id, bench.openDispatch.session)))
            assertTrue(outcomes.isEmpty())
            bench.post(bench.envelope("commandAck", head.id)); bench.expectDispatch("resolveCurrentDestination")
            f.cancel(); assertTrue(bench.pending().pendingCommands.isEmpty()); assertTrue(outcomes.isEmpty())
            f.close()
        }
    }

    @Test fun reentrantAdmissionCancellationDoesNotEnqueueOrStartJavascript() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val before = bench.lastScript()
            var calls = 0; var unexpectedCallbacks = 0
            f.dispatch { call ->
                (bench.webView() as ReaderEngineCurrentDestinationQuerySource).queryCurrentDestination(call.origin, call.context,
                    isAdmissionCurrent = { calls++; f.executor.clear(); true }) { unexpectedCallbacks++ }
            }
            bench.drain()
            assertTrue(calls > 0)
            assertEquals(0, unexpectedCallbacks)
            assertTrue(bench.lastScript() == before && bench.pending().pendingCommands.isEmpty())
            f.close()
        }
    }

    @Test fun delayedHeadRevalidatesAdmissionAndRejectsWithoutQueryJavascript() = runTest {
        withCurrentDestinationEngine { bench ->
            bench.submit(ReaderBridgeCommand.PreviousPage); val head = bench.expectDispatch("previousPage")
            val f = CarrierSemanticFixture(); var current = true
            val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes, admission = { current })
            assertEquals(1, bench.pending().pendingCommands.count { it.currentDestinationContinuation != null })
            current = false
            bench.post(bench.envelope("commandAck", head.id))
            assertEquals(1, outcomes.size)
            assertTrue(outcomes.single() is ReaderCurrentDestinationQueryOutcome.Failed)
            assertTrue(bench.pending().pendingCommands.isEmpty())
            bench.expectDispatch("previousPage")
            f.close()
        }
    }

    @Test fun rootCloseRetiresActualQueryPromptlyAndOldBridgeIsInert() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes); val query = bench.expectDispatch("resolveCurrentDestination")
            val bridge = bench.bridge(); val view = bench.webView()
            bench.closeRoot()
            assertEquals(1, outcomes.size)
            assertTrue(outcomes.single() is ReaderCurrentDestinationQueryOutcome.Failed)
            val count = outcomes.size
            bridge.postMessage(bench.result(query).toString()); bench.drain()
            assertEquals(count, outcomes.size)
            var late = 0
            f.dispatch { call -> (view as ReaderEngineCurrentDestinationQuerySource).queryCurrentDestination(call.origin, call.context) { late++ } }
            assertEquals(0, late)
            f.close()
        }
    }

    @Test fun rendererRetirementConsumesLedgerBeforeExternalEvent() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes); val query = bench.expectDispatch("resolveCurrentDestination")
            val bridge = bench.bridge(); val old = bench.webView()
            var retiredBeforeEvent = false
            bench.onObservedEvent = { event ->
                if (event is ReaderEngineHostEvent.FoliateBridge && event.event is ReaderBridgeEvent.Error) {
                    retiredBeforeEvent = outcomes.size == 1 && bench.pending().pendingCommands.none { it.currentDestinationContinuation != null }
                }
            }
            bench.retireRenderer()
            assertTrue(retiredBeforeEvent)
            assertTrue(bench.webView() !== old)
            bridge.postMessage(bench.result(query).toString()); bench.drain()
            assertEquals(1, outcomes.size)
            f.close()
        }
    }

    @Test fun publicationReplacementRetiresQueuedQueryBeforeNextReady() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            f.query(bench, outcomes); bench.expectDispatch("resolveCurrentDestination")
            // Genuine runtime interruption removes the previous generation before its replacement Ready.
            bench.retireRenderer()
            assertEquals(1, outcomes.size)
            val before = bench.events.size
            bench.replacePublication()
            assertEquals(before, bench.events.size)
            assertTrue(bench.pending().pendingCommands.none { it.currentDestinationContinuation != null })
            bench.post(bench.envelope("ready")); bench.expectDispatch("openPublication")
            f.close()
        }
    }

    @Test fun sequentialOwnerLateResolvedCannotConsumeSuccessorOnRetainedEngine() = runTest {
        assertSequentialOwnerStaleEventIsInert(staleFailure = false)
    }

    @Test fun sequentialOwnerLateFailureCannotConsumeSuccessorOnRetainedEngine() = runTest {
        assertSequentialOwnerStaleEventIsInert(staleFailure = true)
    }

    private suspend fun assertSequentialOwnerStaleEventIsInert(staleFailure: Boolean) {
        withCurrentDestinationEngine { bench ->
            val firstOwner = CarrierSemanticFixture()
            val firstOutcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
            val firstCall = firstOwner.query(bench, firstOutcomes)
            val firstQuery = bench.expectDispatch("resolveCurrentDestination")
            val firstPending = bench.pending().pendingCommands.single()
            val firstToken = assertNotNull(firstPending.currentDestinationContinuation)
            val retainedView = bench.webView()
            val retainedBridge = bench.bridge()
            val retainedState = bench.pending()
            assertTrue(firstCall.origin.slotId.value == 1L, "First authentic executor must allocate its first slot")
            assertTrue(firstPending.dispatch.id == firstQuery.id && firstQuery.session == bench.openDispatch.session,
                "First actual Javascript query must match the owning ledger and open session")
            assertTrue(firstPending.lastDispatchedGeneration == retainedState.runtimeGeneration,
                "First query must actually be dispatched in the retained runtime")
            firstOwner.close() // Retire the real activated gateway/registry/executor, not the Engine root.
            assertEquals(0, firstOwner.registry.activeHandleCount, "First request registry must be retired")
            assertEquals(0, firstOwner.executor.activeSlotCount, "First executor slot must be retired")
            assertTrue((firstToken as ReaderEngineCurrentDestinationContinuation).retired,
                "First actual transport token must lose callback authority")
            assertTrue(bench.pending().pendingCommands.isEmpty() && firstOutcomes.isEmpty(),
                "Owner cleanup must retire the query without publishing an outcome")

            // Neutral host transport scaffolding only: not a Native production epoch/snapshot.
            val successorOwner = CarrierSemanticFixture(firstOwner.opened, hostTransportCoordinatorEpoch = 2L)
            try {
                val successorOutcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
                val successorCall = successorOwner.query(bench, successorOutcomes)
                val successorQuery = bench.expectDispatch("resolveCurrentDestination")
                val successorPending = bench.pending().pendingCommands.single()
                val successorToken = assertNotNull(successorPending.currentDestinationContinuation)
                assertTrue(successorOwner.registry !== firstOwner.registry && successorOwner.executor !== firstOwner.executor,
                    "Successor must own a fresh real registry and executor")
                assertTrue(successorCall.origin.slotId.value == 1L && successorCall.origin.transitionId.sequence == firstCall.origin.transitionId.sequence,
                    "Sequential authentic owners must reuse the first slot and transition sequence")
                assertTrue(successorCall.origin.transitionId.coordinatorEpoch != firstCall.origin.transitionId.coordinatorEpoch,
                    "Actual executor-created origins must carry different coordinator epochs")
                assertTrue(successorOwner.opened === firstOwner.opened &&
                    successorCall.origin.transitionId.readerSessionGeneration == firstCall.origin.transitionId.readerSessionGeneration &&
                    firstCall.origin.transitionId.readerSessionGeneration == firstOwner.opened.state.readerSessionGeneration,
                    "Both owners must retain the single actual Controller open reader session")
                assertTrue(bench.webView() === retainedView && bench.bridge() === retainedBridge &&
                    bench.pending().runtimeGeneration == retainedState.runtimeGeneration &&
                    bench.pending().publicationSequence == retainedState.publicationSequence &&
                    bench.pending().publicationKey == retainedState.publicationKey && successorQuery.session == firstQuery.session,
                    "Successor query must retain the exact Engine, bridge, publication, generation and Foliate session")
                assertTrue(successorPending.dispatch.id == successorQuery.id &&
                    successorPending.lastDispatchedGeneration == retainedState.runtimeGeneration,
                    "Successor actual Javascript query must be the dispatched ledger head")
                (firstToken as ReaderSemanticTransportRetirement).retire()
                bench.drain()
                assertTrue(bench.pending().pendingCommands.singleOrNull()?.currentDestinationContinuation === successorToken,
                    "Old exact token retirement must leave the successor query intact")
                val privateEventCount = bench.events.size
                retainedBridge.postMessage((if (staleFailure) bench.envelope("commandFailed", firstQuery.id)
                    else bench.result(firstQuery)).toString())
                bench.drain()
                assertEquals(0, successorOutcomes.size, "Late prior-owner event must not call the successor outcome")
                assertTrue(bench.pending().pendingCommands.singleOrNull()?.currentDestinationContinuation === successorToken,
                    "Late prior-owner event must not consume the successor query head")
                assertTrue(firstQuery.id != successorQuery.id,
                    "Wire correlation must distinguish sequential authentic coordinator origins")
                assertEquals(privateEventCount, bench.events.size, "Prior-owner result or failure must remain private")
                assertTrue(firstOutcomes.isEmpty(), "Retired first owner must remain inert")
                bench.post(bench.result(successorQuery))
                bench.post(bench.result(successorQuery))
                bench.post(bench.result(firstQuery))
                bench.post(bench.envelope("commandFailed", firstQuery.id))
                assertEquals(1, successorOutcomes.size, "Only the genuine successor result may deliver once")
                assertTrue((successorOutcomes.single() as? ReaderCurrentDestinationQueryOutcome.Resolved)?.carrier === successorToken,
                    "Genuine successor resolution must retain the exact new transport token")
                assertTrue(bench.pending().pendingCommands.isEmpty() && bench.webView() === retainedView &&
                    bench.bridge() === retainedBridge, "Completion must drain the query without replacing the Engine")
                assertEquals(privateEventCount, bench.events.size, "Both owners' query events must stay private")
                assertEquals(0, successorOwner.receipts, "Host transport must not fabricate common Accepted receipts")
            } finally { successorOwner.close() }
        }
    }
}

@org.robolectric.annotation.Implements(android.webkit.WebView::class)
internal class CarrierQueryWebViewShadow : org.robolectric.shadows.ShadowWebView() {
    @org.robolectric.annotation.RealObject private lateinit var actual: android.webkit.WebView
    var throwNextQuery = false
    @org.robolectric.annotation.Implementation
    override fun evaluateJavascript(script: String?, callback: android.webkit.ValueCallback<String>?) {
        if (throwNextQuery && script?.contains("\"type\":\"resolveCurrentDestination\"") == true) {
            throwNextQuery = false
            throw IllegalStateException("Synthetic query submission failure")
        }
        super.evaluateJavascript(script, callback)
    }
    @org.robolectric.annotation.Implementation
    fun postVisualStateCallback(requestId: Long, callback: android.webkit.WebView.VisualStateCallback) {
        actual.post { callback.onComplete(requestId) }
    }
}

internal class CarrierCall(val origin: ReaderPresentationEventOrigin.SemanticCommand,
    val context: ReaderSemanticMutationStartEvidence, val onReceipt: (ReaderPresentationEventReceipt) -> Unit)

/** Real Controller.open epoch; neutral common policy only, never installed as a Native snapshot. */
internal class CarrierSemanticFixture(
    val opened: ReaderController = ReaderController().open(ReaderEngineOpenRequest(
        publication = ReaderPublicationIdentity(bookId = "synthetic-carrier", resourceHref = "synthetic.epub"),
        url = "https://synthetic.invalid/engine-bench.epub")).controller,
    hostTransportCoordinatorEpoch: Long = 1L
) {
    val registry = ReaderSemanticExecutableRequestRegistry(opened.state.readerSessionGeneration)
    val executor = ReaderSemanticCommandExecutor(registry)
    val calls = mutableListOf<CarrierCall>()
    val handles = mutableListOf<ReaderSemanticRequestHandle>()
    val processed = mutableListOf<ReaderTransitionFactKind>()
    var receipts = 0
    val coordinator = ReaderResumableTransitionCoordinator(
        ports = object : ReaderResumableTransitionPorts {
            override val clock = object : ReaderTransitionClock {
                override fun nowMillis() = 0L
                override fun schedule(atMillis: Long, action: () -> Unit) = ReaderTransitionClockRegistration {}
            }
            override val semanticCommand = executor
            override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) = Unit
        }, mode = ReaderTransitionMode.Active,
        journal = ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(
            ReaderInitialCommittedPresentationOrigin.Neutral(opened.state.readerSessionGeneration, hostTransportCoordinatorEpoch,
                ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly))),
        onObservation = { if (it.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed) it.factKind?.let(processed::add) })
    val gateway = ReaderTransitionGateway()
    val registration = gateway.attachActivated(enqueue = coordinator::enqueue, enqueueReceipt = coordinator::enqueue,
        retireSemanticRequests = registry::clear, registerSemanticRequest = registry::register)
    fun dispatch(request: (CarrierCall) -> ReaderSemanticExecutableResult): CarrierCall {
        gateway.dispatchSemanticActivatedOrLegacy(
            intent = { handles += it; ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, it) },
            request = { origin, context, callback ->
                val call = CarrierCall(origin, context) { receipts++; callback(it) }; calls += call; request(call)
            }, activatedResult = { Unit }, legacyDispatch = { error("Legacy route forbidden") })
        drain(); return calls.last()
    }
    fun query(bench: CurrentDestinationEngineBench, outcomes: MutableList<ReaderCurrentDestinationQueryOutcome>,
        admission: () -> Boolean = { true }, onOutcome: (() -> Unit)? = null): CarrierCall = dispatch { call ->
        (bench.webView() as ReaderEngineCurrentDestinationQuerySource).queryCurrentDestination(call.origin, call.context, admission) { outcome ->
            outcomes += outcome
            onOutcome?.invoke()
            if (outcome is ReaderCurrentDestinationQueryOutcome.Failed) call.context.reportAsyncFailure(call.origin,
                if (outcome.reason == ReaderCurrentDestinationQueryFailure.SubmissionThrew) ReaderSemanticAsyncFailure.Threw else ReaderSemanticAsyncFailure.Rejected)
        }
    }
    fun drain() = shadowOf(Looper.getMainLooper()).idle()
    fun cancel() { gateway.enqueue(ReaderTransitionFact.Intent(null, ReaderCancelIntent)); drain() }
    fun retry() { gateway.enqueue(ReaderTransitionFact.Retry(null)); drain() }
    fun close() { registration.close(); executor.clear(); registry.clear(); drain() }
}
