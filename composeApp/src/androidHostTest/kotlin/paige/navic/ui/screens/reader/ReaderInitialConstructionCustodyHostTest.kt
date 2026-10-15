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

/** Actual executor custody only. Explicit initialization calls here are negative/unit
 * phase inputs, never evidence of actual Engine configure, Foliate, layout or presentation. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderInitialConstructionCustodyHostTest {
    @Test fun unboundCellCannotDeliverInitialization() {
        val f = Fixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start(bind = false))
            f.initialize()
            assertEquals(0, f.deliveries, "Unbound construction must not deliver even with real executor evidence")
        } finally { f.close() }
    }
    @Test fun unboundConstructionCannotBorrowRealExecutorEvidenceForQueryAdoption() {
        val f = Fixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start(bind = false))
            f.initialize()
            val child = f.child()
            assertEquals(ReaderSemanticTransportBindingResult.Rejected, f.custody.adoptQuery(f.origin, f.evidence, child),
                "A construction cell must be owned by this exact executor before acquiring query custody")
            assertFalse(child.retired) // Rejection remains caller-owned.
        } finally { f.close() }
    }
    @Test fun oneAuthenticExecutorBindingOwnsItsQueryRetirementWithoutSecondBinding() {
        val f = Fixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start(bind = true))
            f.initialize()
            assertEquals(1, f.deliveries)
            val child = f.child()
            assertEquals(ReaderSemanticTransportBindingResult.Bound, f.custody.adoptQuery(f.origin, f.evidence, child))
            f.registration.retire()
            assertTrue(child.retired); assertFalse(f.custody.active)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
        } finally { f.close() }
    }
    @Test fun lateBindingAfterExactSlotRetirementCannotDeliverInitialization() {
        val f = Fixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start(bind = false))
            f.registration.retire()
            assertEquals(ReaderSemanticTransportBindingResult.RetiredAndDisposed, f.bind())
            f.initialize()
            assertEquals(0, f.deliveries); assertFalse(f.custody.active)
        } finally { f.close() }
    }
    private class Fixture {
        private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        private val engine = Class.forName("paige.navic.ui.screens.reader.ReaderEngineWebView")
            .getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(activity.get()) as WebView
        val registry = ReaderSemanticExecutableRequestRegistry(1L)
        val executor = ReaderSemanticCommandExecutor(registry)
        lateinit var custody: ReaderEngineInitialDestinationConstructionCustody
        lateinit var origin: ReaderPresentationEventOrigin.SemanticCommand
        lateinit var evidence: ReaderSemanticMutationStartEvidence
        lateinit var registration: ReaderSemanticCommandRegistration
        private val children = mutableListOf<ReaderEngineCurrentDestinationContinuation>()
        var deliveries = 0; private set
        fun start(bind: Boolean): ReaderSemanticCommandResult {
            var binding: ReaderSemanticTransportBindingResult? = null
            var missingBinding = false
            val handle = registry.register { o, c, _ ->
                origin = o; evidence = c
                custody = ReaderEngineInitialDestinationConstructionCustody(o, c) { deliveries += 1 }
                if (bind) {
                    missingBinding = custody.javaClass.declaredMethods.none { it.name == "bindExecutor" }
                    if (!missingBinding) binding = bind()
                }
                ReaderSemanticExecutableResult.Accepted
            }
            val id = ReaderTransitionId(1L, 1L, 1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            val result = executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(id,
                ReaderBootstrapNativePageIntent(handle), handle), { registration = it }, {})
            assertFalse(missingBinding, "Construction custody must bind itself to the actual executor, not claim adoption authority")
            if (bind) assertEquals(ReaderSemanticTransportBindingResult.Bound, binding)
            return result
        }
        fun bind(): ReaderSemanticTransportBindingResult {
            val method = custody.javaClass.declaredMethods.singleOrNull { it.name == "bindExecutor" }
            assertNotNull(method, "Actual executor-binding producer required for construction custody")
            return method.apply { isAccessible = true }.invoke(custody) as ReaderSemanticTransportBindingResult
        }
        fun initialize() { custody.runtimeInitialized(engine); custody.mountCompleted() }
        fun child() = ReaderEngineCurrentDestinationContinuation(engine, "synthetic-construction-query", 0,
            "public-synthetic-construction", "public-synthetic-session", { true }, {}, initialBootstrap = true).also { children += it }
        fun close() {
            executor.clear(); registry.clear()
            if (::custody.isInitialized) custody.retire()
            children.forEach { it.retire() }; engine.destroy(); activity.pause().stop().destroy()
        }
    }
}
