package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import java.time.Duration
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Genuine Native construction/runtime-load callback and owning Engine protocol. Synthetic
 * bridge messages and test installation are not Foliate/layout/production-reader proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderNativeInitialResolvedLayoutHostTest {
    @Test fun originalNativeInstallationRetainsNumericLayoutOnlyAfterItsValidatedInitialReceipt() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.drain()
            val material = assertNotNull(f.material())
            f.resolve()
            assertEquals(1, f.receipts.size)
            assertFalse(material.commandCurrency?.invoke() == true,
                "Retired semantic execution must not become later allocation authority")
            val field = material.javaClass.declaredFields.singleOrNull { it.name == "initialResolvedLayout" }
            assertNotNull(field, "Original Native physical installation needs its exact validated numeric layout association")
            val association = field.apply { isAccessible = true }.get(material)
            assertNotNull(association)
            assertNull(material.binding); assertNull(material.owner)
            assertEquals(ReaderLegacyResourceState.Registered, material.state)
            f.call("closeReader")
            f.drain()
            assertNull(field.get(material), "Original physical retirement discards allocation layout custody")
        } finally { f.dispose() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class ConstructionFixture(private val scheduler: TestCoroutineScheduler) {
        init { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
        private val activity = run {
            val key = "robolectric.areWindowsMarkedVisible"; val old = System.getProperty(key)
            System.setProperty(key, "true")
            try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
            finally { if (old == null) System.clearProperty(key) else System.setProperty(key, old) }
        }
        private val type = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer")
        private val store = ReaderActivatedSessionSnapshotStore()
        private val native = type.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
            .apply { isAccessible = true }.newInstance(activity.get(), store) as ViewGroup
        private val opened = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-native-construction", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        private var model = ReaderCoordinator(controller = opened)
        private val session = opened.state.readerSessionGeneration
        private val domain = ReaderLegacyPhysicalDomain(session, ReaderLegacyFreezeToken(8L))
        val registry = ReaderSemanticExecutableRequestRegistry(session, activationStore = store)
        val executor = ReaderSemanticCommandExecutor(registry)
        private val gateway = ReaderTransitionGateway()
        private val candidate: ReaderTransitionGatewayCandidate
        private val reservation: ReaderReservedNeutralBootstrapRequest
        private var body: ReaderSemanticExecutableRequest? = null
        private var wrapper: ComposeView? = null
        var constructions = 0; private set
        lateinit var registration: ReaderSemanticCommandRegistration
        val receipts = mutableListOf<ReaderPresentationEventReceipt>()
        init {
            activity.get().setContentView(native); drain()
            call("preparePresentationEpoch", opened.presentationVersion, opened.state.presentation, false, true, false)
            call("setPresentationDecision", readerPresentationDecision(opened.state.presentation), opened.state.presentation,
                opened.presentationVersion, false, null, { _: ReaderPresentationEvent -> null }, { _: ReaderPresentationHostEffect -> Unit })
            val source = field("framePhysicalOwnership") as ReaderFramePhysicalOwnershipAdapter
            val parent = field("viewerContentContainer") as ViewGroup
            val old = checkNotNull(source.reserve(parent, View(parent.context)) { true })
            check(source.mount(old)); check(source.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
            val identity = source.snapshotFrozenOwnership().single().physicalIdentity
            check(source.drainFrozenOwnership(identity) {} == ReaderPortCommandResult.Accepted)
            check(source.finalizeActivatedHandoff(domain, null, setOf(identity)) == ReaderPortCommandResult.Accepted)
            check(executor.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
            candidate = checkNotNull(gateway.reserveActivatedCandidate(session, 6L, store, {}, {}, registry::register,
                registry::discardCandidateReservations, executor::clear))
            reservation = checkNotNull(registry.reserveNeutralBootstrap(domain, candidate) { o, c, cb ->
                body?.invoke(o, c, cb) ?: ReaderSemanticExecutableResult.Rejected
            })
            val decision = ReaderInitialActivationDecision(ReaderInitialCommittedPresentationOrigin.Neutral(session, 6L,
                ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly), null, null, reservation)
            check(ReaderActivatedSessionInstallationBarrier(store).installTestActivatedSession(
                completeActivatedSessionPorts().copy(gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate), semantic = executor), decision
            ) == ReaderActivationInstallResult.Installed)
        }
        fun start(foreignReservation: Boolean = false): ReaderSemanticCommandResult {
            val factory = type.declaredMethods.singleOrNull { it.name == "createInitialDestinationBootstrapRequest" }
            assertNotNull(factory, "Actual Native bootstrap must own Engine construction and real runtime initialization")
            val create: () -> View = {
                check(wrapper == null); constructions += 1
                ComposeView(activity.get()).apply {
                    wrapper = this
                    setContent { ReaderEngineWebViewHost(publicationUrl = "https://example.invalid/fixture.epub", title = "Public synthetic construction",
                        kind = ReaderPublicationKind.Ebook, mediaOverlayEnabled = false, externalShellCover = false,
                        suppressWebShellCover = false, nativeShellCoverTint = null, settings = ReaderSettings(), startCfi = null,
                        startHref = null, startProgress = null, onEvent = {}, modifier = Modifier.fillMaxSize()) }
                }
            }
            val ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt? = { event, origin ->
                val step = model.reduceSemanticDestination(event, origin); model = step.coordinator; step.presentationReceipt
            }
            val supplied = if (foreignReservation) ReaderReservedNeutralBootstrapRequest(reservation.handle, session) else reservation
            @Suppress("UNCHECKED_CAST")
            body = factory.apply { isAccessible = true }.invoke(native, candidate, supplied, create, ingress) as ReaderSemanticExecutableRequest
            val id = ReaderTransitionId(session, 6L, 1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            return executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(id,
                ReaderBootstrapNativePageIntent(reservation.handle), reservation.handle),
                { registration = it; it.bindAsyncOutcome { registration.retire() } }, receipts::add)
        }
        fun field(name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(native)
        fun call(name: String, vararg args: Any?): Any? = type.declaredMethods.single {
            it.name == name && it.parameterCount == args.size
        }.apply { isAccessible = true }.invoke(native, *args)
        fun material() = field("currentFrameInstallation") as? ReaderFramePhysicalOwnershipAdapter.Lease
        fun drain() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48))
            scheduler.runCurrent()
        }
        private fun engine(): WebView {
            fun visit(v: View): WebView? {
                if (v is WebView) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))?.let { return it }
                return null
            }
            return checkNotNull(visit(checkNotNull(wrapper)))
        }
        fun pending(): ReaderWebCommandDispatchState = engine().webViewClient.javaClass.declaredFields.firstNotNullOfOrNull {
            val value = it.apply { isAccessible = true }.get(engine().webViewClient)
            (value as? State<*>)?.value as? ReaderWebCommandDispatchState
        } ?: error("Actual owning Engine ledger required")
        private fun post(message: JsonObject) {
            (shadowOf(engine()).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as ReaderJavascriptBridge).postMessage(message.toString()); drain()
        }
        fun resolve() {
            val target = engine()
            target.webViewClient.onPageFinished(target, ReaderWebRuntime.entrypointUrl)
            drain()
            post(buildJsonObject { put("type", "ready") })
            val opening = pending().pendingCommands.first()
            assertTrue(opening.dispatch.command is ReaderBridgeCommand.OpenPublication)
            post(buildJsonObject { put("type", "commandAck"); put("commandId", opening.dispatch.id) })
            val query = pending().pendingCommands.first().dispatch
            val command = query.command as ReaderBridgeCommand.ResolveCurrentDestination
            post(buildJsonObject { put("type", "currentDestinationResolved"); put("commandId", query.id)
                put("foliateSessionId", command.foliateSessionId); put("destinationFoliateSessionId", command.foliateSessionId)
                put("destinationCommitSequence", 1L); put("pageIndex", 0)
                put("resolvedLayout", buildJsonObject {
                    put("layoutGeneration", 7L); put("viewGeneration", 3L); put("navigationGeneration", 5L)
                    put("sectionIndex", 0); put("pageIndex", 0); put("pageCount", 8)
                    put("width", 560.0); put("height", 420.0)
                }) })
        }
        suspend fun dispose() {
            try {
                candidate.close(); executor.clear(); registry.clear()
                val closed = (field("pageRasterPreparationController") as ReaderPageRasterPreparationController).destroy()
                scheduler.runCurrent()
                call("closeReader"); wrapper?.disposeComposition(); shadowOf(Looper.getMainLooper()).idle()
                val failure = runCatching { withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } } }.exceptionOrNull()
                if (failure != null) check(failure is ReaderPageTeardownException && generateSequence(failure as Throwable?) { it.cause }
                    .any { it.message == "GL surface is unavailable for terminal disposal" }) { "Unexpected Native construction fixture terminal disposition: completed=${closed.isCompleted} timedOut=${failure is kotlinx.coroutines.TimeoutCancellationException} teardown=${failure is ReaderPageTeardownException}" }
                check(closed.isCompleted)
            } finally {
                try { activity.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
                finally { Dispatchers.resetMain() }
            }
        }
    }
}
