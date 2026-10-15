package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.webkit.WebView
import androidx.activity.ComponentActivity
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual original executor/custody phase qualification with synthetic layout inputs.
 * This is not actual Foliate layout, Native allocation, construction or display proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderInitialResolvedLayoutCustodyHostTest {
    @Test fun exactAdoptedQueryHandsNumericLayoutToItsExactReceiptOnce() = useFixture { f ->
        f.adopt()
        val receipt = f.receipt()
        assertTrue(f.retain(f.child, receipt))
        assertSame(f.layout, f.take(receipt))
        assertNull(f.take(receipt))
    }
    @Test fun waitingConstructionCannotClaimResolvedLayout() = useFixture { f ->
        assertFalse(f.retain(f.child, f.receipt()))
        assertNull(f.take(f.receipt()))
    }
    @Test fun differentQueryCannotBorrowAdoptedLayoutCustody() = useFixture { f ->
        f.adopt()
        val other = f.newChild()
        assertFalse(f.retain(other, f.receipt()))
        assertNull(f.take(f.receipt()))
    }
    @Test fun equalButRecreatedReceiptCannotTakeOrConsumeExactLayout() = useFixture { f ->
        f.adopt()
        val receipt = f.receipt()
        assertTrue(f.retain(f.child, receipt))
        assertNull(f.take(receipt.copy()))
        assertSame(f.layout, f.take(receipt))
    }
    @Test fun exactRetirementDiscardsUnconsumedLayoutAndRejectsLateRetention() = useFixture { f ->
        f.adopt()
        val receipt = f.receipt()
        assertTrue(f.retain(f.child, receipt))
        f.registration.retire()
        assertTrue(f.child.retired)
        assertNull(f.take(receipt))
        assertFalse(f.retain(f.child, receipt))
    }
    private inline fun useFixture(block: (Fixture) -> Unit) {
        val f = Fixture()
        try { block(f) } finally { f.close() }
    }
    private class Fixture {
        private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        private val engine = Class.forName("paige.navic.ui.screens.reader.ReaderEngineWebView")
            .getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(activity.get()) as WebView
        val registry = ReaderSemanticExecutableRequestRegistry(1L)
        val executor = ReaderSemanticCommandExecutor(registry)
        val layout = ReaderFoliateResolvedLayout(7L, 3L, 5L, 0, 0, 8, 560.0, 420.0)
        lateinit var custody: ReaderEngineInitialDestinationConstructionCustody
        lateinit var origin: ReaderPresentationEventOrigin.SemanticCommand
        lateinit var evidence: ReaderSemanticMutationStartEvidence
        lateinit var registration: ReaderSemanticCommandRegistration
        private val children = mutableListOf<ReaderEngineCurrentDestinationContinuation>()
        val child: ReaderEngineCurrentDestinationContinuation
        init {
            val handle = registry.register { o, c, _ ->
                origin = o; evidence = c
                custody = ReaderEngineInitialDestinationConstructionCustody(o, c) {}
                assertEquals(ReaderSemanticTransportBindingResult.Bound, custody.bindExecutor())
                ReaderSemanticExecutableResult.Accepted
            }
            val id = ReaderTransitionId(1L, 1L, 1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            assertEquals(ReaderSemanticCommandResult.Accepted, executor.synchronize(
                ReaderTransitionCommand.RequestSemanticSynchronization(id, ReaderBootstrapNativePageIntent(handle), handle),
                { registration = it }, {}))
            child = newChild()
        }
        fun newChild() = ReaderEngineCurrentDestinationContinuation(engine, "public-synthetic-layout-query", 0,
            "public-synthetic-layout", "public-synthetic-session", { true }, {}, initialBootstrap = true).also { children += it }
        fun adopt() {
            custody.runtimeInitialized(engine); custody.mountCompleted()
            assertEquals(ReaderSemanticTransportBindingResult.Bound, custody.adoptQuery(origin, evidence, child))
        }
        fun receipt(): ReaderPresentationEventReceipt {
            val opened = ReaderController().open(ReaderEngineOpenRequest(
                ReaderPublicationIdentity("public-synthetic-layout", resourceHref = "fixture.epub"),
                "https://example.invalid/fixture.epub")).controller
            val binding = ReaderPresentationBinding("public-synthetic-session", 1L, 1L, 0L,
                ReaderDestinationCommitIdentity("public-synthetic-session", 1L))
            return assertNotNull(ReaderCoordinator(controller = opened).reduceSemanticDestination(
                ReaderPresentationEvent.FoliateRelocated(binding, null), origin).presentationReceipt)
        }
        fun retain(token: ReaderEngineCurrentDestinationContinuation, receipt: ReaderPresentationEventReceipt): Boolean {
            val method = custody.javaClass.declaredMethods.singleOrNull { it.name == "retainResolvedLayout" }
            assertNotNull(method, "Original construction custody must retain only its exact query and validated receipt layout")
            return method.apply { isAccessible = true }.invoke(custody, token, receipt, layout) as Boolean
        }
        fun take(receipt: ReaderPresentationEventReceipt): ReaderFoliateResolvedLayout? {
            val method = custody.javaClass.declaredMethods.singleOrNull { it.name == "takeResolvedLayout" }
            assertNotNull(method, "Original custody needs exact once-only numeric layout handoff before executor retirement")
            return method.apply { isAccessible = true }.invoke(custody, receipt) as ReaderFoliateResolvedLayout?
        }
        fun close() {
            executor.clear(); registry.clear()
            custody.retire(); children.forEach { it.retire() }
            engine.destroy(); activity.pause().stop().destroy()
        }
    }
}
