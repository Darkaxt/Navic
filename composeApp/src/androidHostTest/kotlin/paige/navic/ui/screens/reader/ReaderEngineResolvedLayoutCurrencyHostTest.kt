package paige.navic.ui.screens.reader

import android.os.Build
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Original Engine source currentness with synthetic transport inputs, not Live
 * Foliate/layout, Native material allocation or physical presentation proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderEngineResolvedLayoutCurrencyHostTest {
    @Test fun noQueryDeliveryCannotCapturePostSemanticLayoutSourceCurrency() = runTest {
        withCurrentDestinationEngine { bench ->
            assertNull(capture(bench))
        }
    }
    @Test fun physicalSourceCurrencySurvivesSemanticRetirementWithoutReopeningCommandAuthority() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve()
                assertEquals(0, f.executor.activeSlotCount)
                assertEquals(0, f.registry.activeHandleCount)
                assertTrue(assertNotNull(f.currency)())
                assertNull(capture(bench), "Only actual original query callback may capture source scope")
            } finally { f.close() }
        }
    }
    @Test fun newerOriginalLocationInvalidatesRetainedLayoutSourceBeforeComposeEcho() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve()
                val currency = assertNotNull(f.currency)
                assertTrue(currency())
                bench.post(buildJsonObject {
                    put("type", "locationChanged"); put("foliateSessionId", bench.openDispatch.session)
                    put("destinationFoliateSessionId", bench.openDispatch.session)
                    put("destinationCommitSequence", 2L); put("pageIndex", 1)
                })
                assertFalse(currency())
            } finally { f.close() }
        }
    }
    @Test fun queuedNavigationCannotBorrowUnchangedObservedLocatorAsCurrentLayout() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve()
                val currency = assertNotNull(f.currency)
                assertTrue(currency())
                bench.submit(ReaderBridgeCommand.NextPage)
                assertFalse(currency())
            } finally { f.close() }
        }
    }
    @Test fun replacedPublicationCannotBorrowOldRuntimeLayoutSourceCurrency() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve()
                val currency = assertNotNull(f.currency)
                assertTrue(currency())
                bench.replacePublication()
                assertFalse(currency())
            } finally { f.close() }
        }
    }
    @Test fun retiredRendererCannotBorrowOldLayoutSourceCurrency() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve()
                val currency = assertNotNull(f.currency)
                assertTrue(currency())
                bench.retireRenderer()
                assertFalse(currency())
            } finally { f.close() }
        }
    }
    @Test fun sameLocatorWithNewCommitCannotBorrowRetainedSourceCurrency() = runTest {
        withCurrentDestinationEngine { bench ->
            fun location(commit: Long) = buildJsonObject {
                put("type", "locationChanged"); put("foliateSessionId", bench.openDispatch.session)
                put("destinationFoliateSessionId", bench.openDispatch.session)
                put("destinationCommitSequence", commit)
                put("href", "synthetic-chapter.xhtml"); put("cfi", "epubcfi(/6/2!/4/2:0)")
            }
            bench.post(location(1L))
            val f = Fixture(bench)
            try {
                f.resolve(); val current = assertNotNull(f.currency); assertTrue(current())
                bench.post(location(2L)); assertFalse(current(), "Equal locator values cannot erase original commit replacement")
            } finally { f.close() }
        }
    }
    @Test fun acknowledgedNavigationBeforeDecorationCannotReopenOldSourceCurrency() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve(); val current = assertNotNull(f.currency); assertTrue(current())
                bench.submit(ReaderBridgeCommand.NextPage)
                assertFalse(current())
                val navigation = bench.expectDispatch("nextPage")
                bench.post(bench.envelope("commandAck", navigation.id))
                bench.submit(ReaderBridgeCommand.ClearOverlay)
                assertFalse(current(), "Decoration input cannot revive a source invalidated by original navigation")
            } finally { f.close() }
        }
    }
    @Test fun firstCurrencyReadAfterNavigationAndDecorationStillRejectsOldSource() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve(); val current = assertNotNull(f.currency)
                // Deliberately do not invoke retained currency while navigation is pending.
                bench.submit(ReaderBridgeCommand.NextPage)
                val navigation = bench.expectDispatch("nextPage")
                bench.post(bench.envelope("commandAck", navigation.id))
                bench.submit(ReaderBridgeCommand.ClearOverlay)
                assertFalse(current(), "Original mutation identity cannot depend on polling a scope predicate")
            } finally { f.close() }
        }
    }
    @Test fun continuingDecorationInputDoesNotRequireIdleQueueForSourceProgress() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = Fixture(bench)
            try {
                f.resolve(); val current = assertNotNull(f.currency)
                repeat(16) {
                    bench.submit(ReaderBridgeCommand.ClearOverlay)
                    val decoration = bench.expectDispatch("clearOverlay")
                    assertTrue(current(), "Continuing decoration input must not invalidate destination/layout source")
                    bench.post(bench.envelope("commandAck", decoration.id))
                }
            } finally { f.close() }
        }
    }

    companion object {
        private fun capture(bench: CurrentDestinationEngineBench): (() -> Boolean)? {
            val method = bench.webView().javaClass.methods.singleOrNull {
                it.name == "captureResolvedLayoutCurrency" && it.parameterCount == 0
            }
            assertNotNull(method, "Original Engine must expose distinct post-semantic source currency, not retired command currency")
            @Suppress("UNCHECKED_CAST")
            return method.apply { isAccessible = true }.invoke(bench.webView()) as (() -> Boolean)?
        }
    }
    private class Fixture(val bench: CurrentDestinationEngineBench) {
        private val opened = ReaderController().open(ReaderEngineOpenRequest(
            ReaderPublicationIdentity("public-synthetic-source-currency", resourceHref = "fixture.epub"),
            "https://example.invalid/fixture.epub")).controller
        private var model = ReaderCoordinator(controller = opened)
        val registry = ReaderSemanticExecutableRequestRegistry(opened.state.readerSessionGeneration)
        val executor = ReaderSemanticCommandExecutor(registry)
        var currency: (() -> Boolean)? = null
        private lateinit var request: ReaderSemanticExecutableRequest
        private val handle = registry.register { origin, context, callback ->
            request(origin, context) { receipt ->
                currency = capture(bench)
                callback(receipt)
            }
        }
        init {
            val factory = bench.webView().javaClass.methods.single { it.name == "createInitialDestinationSemanticRequest" }
            val ingress: ReaderInitialSemanticReceiptIngress = { event, origin ->
                val step = model.reduceSemanticDestination(event, origin); model = step.coordinator; step.presentationReceipt
            }
            @Suppress("UNCHECKED_CAST")
            request = factory.apply { isAccessible = true }.invoke(bench.webView(),
                ReaderReservedNeutralBootstrapRequest(handle, opened.state.readerSessionGeneration),
                opened.presentationVersion, 1L, 1L, 0L, { true }, ingress) as ReaderSemanticExecutableRequest
        }
        fun resolve() {
            // Assert API availability outside callbacks: original callback totality must
            // not swallow a qualification failure or fabricate a source witness.
            assertNull(capture(bench))
            val id = ReaderTransitionId(opened.state.readerSessionGeneration, 1L, 1L,
                ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            assertEquals(ReaderSemanticCommandResult.Accepted, executor.synchronize(
                ReaderTransitionCommand.RequestSemanticSynchronization(id, ReaderBootstrapNativePageIntent(handle), handle), {}, {}))
            val query = bench.expectDispatch("resolveCurrentDestination")
            bench.post(bench.result(query))
        }
        fun close() { executor.clear(); registry.clear() }
    }
}
