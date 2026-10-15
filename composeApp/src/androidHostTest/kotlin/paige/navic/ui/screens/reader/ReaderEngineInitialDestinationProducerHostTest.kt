package paige.navic.ui.screens.reader

import android.os.Build
import android.webkit.WebView
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual Engine/bridge/ledger and owning executor; transport ingress is synthetic,
 * not Foliate execution, layout/display proof, or production installation acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
        Task398CanonicalStoragePathShadow::class])
class ReaderEngineInitialDestinationProducerHostTest {
    @Test fun initialBindingComesOnlyFromConsumedCommandResultWithoutBoundNativePredecessor() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = InitialProducerFixture(bench)
            try {
                assertNull(bench.nativeBinding())
                assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
                val query = bench.expectDispatch("resolveCurrentDestination")
                bench.post(bench.envelope("commandAck", query.id))
                bench.post(bench.envelope("publicationReady"))
                bench.post(buildJsonObject {
                    put("type", "locationChanged"); put("commandId", query.id)
                    put("foliateSessionId", query.session); put("destinationFoliateSessionId", query.session)
                    put("destinationCommitSequence", 1L); put("pageIndex", 0)
                })
                assertEquals(0, f.receipts.size)
                assertEquals(1, f.executor.activeSlotCount)
                bench.post(f.result(query))
                val receipt = f.receipts.single()
                assertSame(f.origin, receipt.origin)
                assertTrue(receipt.event is ReaderPresentationEvent.FoliateRelocated)
                val semantic = assertNotNull(receipt.semanticReceipt)
                assertTrue(semantic.binding.destinationCommitIdentity?.foliateSessionId == query.session)
                assertTrue(semantic.binding.destinationCommitIdentity?.commitSequence == 1L)
                assertNull(semantic.binding.rasterGeneration)
                assertNull(semantic.binding.textureGeneration)
                assertNull(semantic.binding.preparationGeneration)
                assertEquals(ReaderPresentationFrameOwner.Neutral, readerPresentationDecision(receipt.postState).frameOwner)
                assertTrue(receipt.effects.isEmpty())
                assertTrue(f.ledgerConsumedAtIngress)
                assertEquals(0, f.registry.activeHandleCount)
                assertEquals(0, f.executor.activeSlotCount)
                bench.post(f.result(query))
                assertEquals(1, f.receipts.size)
            } finally { f.close() }
        }
    }

    @Test fun obsoleteInitialContextCannotPublishAndSameHandleRetryUsesNewOrigin() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = InitialProducerFixture(bench)
            try {
                f.current = false
                assertTrue(f.start() is ReaderSemanticCommandResult.RejectedBeforeMutation)
                assertEquals(0, f.receipts.size)
                f.registration.retireForRetry()
                f.current = true
                assertEquals(ReaderSemanticCommandResult.Accepted, f.start(sequence = 2L))
                val query = bench.expectDispatch("resolveCurrentDestination")
                bench.post(f.result(query))
                assertEquals(1, f.receipts.size)
                assertTrue(f.receipts.single().originatingTransitionId?.sequence == 2L)
            } finally { f.close() }
        }
    }

    @Test fun cancelInsideInitialModelIngressNeverForwardsReceiptOrReexecutes() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = InitialProducerFixture(bench)
            try {
                f.cancelInsideIngress = true
                assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
                val query = bench.expectDispatch("resolveCurrentDestination")
                bench.post(f.result(query))
                assertEquals(0, f.receipts.size)
                assertEquals(0, f.executor.activeSlotCount)
                assertEquals(0, f.registry.activeHandleCount)
                bench.post(f.result(query))
                assertEquals(0, f.receipts.size)
            } finally { f.close() }
        }
    }

    @Test fun wrongSessionAndOldRuntimeCannotManufactureInitialBinding() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = InitialProducerFixture(bench)
            try {
                assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
                val query = bench.expectDispatch("resolveCurrentDestination")
                bench.post(bench.result(query, session = "synthetic-other-session"))
                assertEquals(0, f.receipts.size)
                assertEquals(1, f.executor.activeSlotCount)
                bench.retireRenderer()
                assertEquals(0, f.receipts.size)
                assertEquals(0, f.executor.activeSlotCount)
            } finally { f.close() }
        }
    }

    @Test fun initialProducerRejectsExternalRelocationOriginBeforeTransport() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = InitialProducerFixture(bench)
            try {
                val script = bench.lastScript()
                assertTrue(f.start(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
                    is ReaderSemanticCommandResult.RejectedBeforeMutation)
                assertEquals(0, f.receipts.size)
                assertTrue(bench.lastScript() == script)
            } finally { f.close() }
        }
    }

    private class InitialProducerFixture(val bench: CurrentDestinationEngineBench) {
        val opened = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-initial", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        var model = ReaderCoordinator(controller = opened)
        val registry = ReaderSemanticExecutableRequestRegistry(opened.state.readerSessionGeneration)
        val executor = ReaderSemanticCommandExecutor(registry)
        val receipts = mutableListOf<ReaderPresentationEventReceipt>()
        var current = true
        var cancelInsideIngress = false
        var ledgerConsumedAtIngress = false
        lateinit var origin: ReaderPresentationEventOrigin.SemanticCommand
        lateinit var registration: ReaderSemanticCommandRegistration
        lateinit var request: ReaderSemanticExecutableRequest
        val handle = registry.register { o, c, callback -> origin = o; request(o, c, callback) }
        init {
            val factory = bench.webView().javaClass.methods.singleOrNull {
                it.name == "createInitialDestinationSemanticRequest"
            }
            assertNotNull(factory, "The actual Engine must own a distinct initial semantic producer, not the already-bound Native query")
            factory.isAccessible = true
            val ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt? = { event, commandOrigin ->
                ledgerConsumedAtIngress = bench.pending().pendingCommands.none {
                    it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
                }
                val step = model.reduceSemanticDestination(event, commandOrigin)
                model = step.coordinator
                if (cancelInsideIngress) registration.retire()
                step.presentationReceipt
            }
            @Suppress("UNCHECKED_CAST")
            request = factory.invoke(bench.webView(), ReaderReservedNeutralBootstrapRequest(handle, opened.state.readerSessionGeneration),
                opened.presentationVersion, 1L, 1L, 1L, { current }, ingress) as ReaderSemanticExecutableRequest
        }
        fun start(sequence: Long = 1L, operation: ReaderTransitionOperation = ReaderTransitionOperation.BootstrapNativePage): ReaderSemanticCommandResult {
            val id = ReaderTransitionId(opened.state.readerSessionGeneration, 1L, sequence, operation,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L),
                if (sequence == 1L) null else ReaderTransitionId(opened.state.readerSessionGeneration, 1L, 1L,
                    ReaderTransitionOperation.BootstrapNativePage, ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L)).parentIdentity())
            val intent = ReaderBootstrapNativePageIntent(handle)
            return executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(id, intent, handle),
                onRegistration = { registration = it; it.bindAsyncOutcome { registration.retireForRetry() } },
                onReceipt = receipts::add)
        }
        fun result(query: EngineObservedDispatch): JsonObject = JsonObject(bench.result(query) + mapOf("pageIndex" to JsonPrimitive(0)))
        fun close() { executor.clear(); registry.clear() }
    }
}
