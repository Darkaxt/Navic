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
class ReaderNativeInitialConstructionProducerHostTest {
    @Test fun actualNativeBootstrapConstructsEngineAndOwnsOpeningThroughRealRuntimeInitializationCallback() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            assertEquals(1, f.constructions)
            f.drain() // Scheduling only, outside the executable; no injected construction notification.
            f.pageFinished(ReaderWebRuntime.entrypointUrl)
            val material = assertNotNull(f.material())
            assertEquals(ReaderLegacyResourceState.Registered, material.state)
            assertNull(material.binding); assertNull(material.owner); assertNull(material.imported)
            assertEquals(2, f.pending().pendingCommands.size)
            assertTrue(f.pending().pendingCommands.all { it.lastDispatchedGeneration == null })
            assertEquals(0, f.receipts.size)
            f.resolve()
            val receipt = f.receipts.single()
            assertNull(receipt.preVersion.publicationIdentity)
            assertTrue(receipt.event is ReaderPresentationEvent.FoliateRelocated)
            assertTrue(receipt.effects.isEmpty())
            assertEquals(ReaderPresentationFrameOwner.Neutral, readerPresentationDecision(receipt.postState).frameOwner)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
        } finally { f.dispose() }
    }
    @Test fun nonEntrypointPageFinishCannotInitializeInitialQuery() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.drain()
            assertTrue(f.pending().pendingCommands.isEmpty(),
                "Initial query must wait for this generation's exact entrypoint finish")
            f.pageFinished("https://example.invalid/stale-reader-runtime")
            assertTrue(f.pending().pendingCommands.isEmpty(),
                "A stale/non-entrypoint finish must not initialize the Native-owned query")
            f.pageFinished(null)
            assertTrue(f.pending().pendingCommands.isEmpty(),
                "A missing page-finish URL must not initialize the Native-owned query")
            assertEquals(0, f.receipts.size)
        } finally { f.dispose() }
    }

    @Test fun exactPageFinishQueuesOriginalOpeningAndQueryBeforeJsReady() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.pageFinished(ReaderWebRuntime.entrypointUrl)
            val pending = f.pending().pendingCommands
            assertEquals(2, pending.size)
            assertTrue(pending[0].dispatch.command is ReaderBridgeCommand.OpenPublication)
            assertTrue(pending[1].dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination)
            assertTrue(pending.all { it.lastDispatchedGeneration == null })
            assertNull(shadowOf(f.engine()).lastEvaluatedJavascript,
                "Exact load completion initializes custody; JS Ready alone owns dispatch")
            assertEquals(0, f.receipts.size)
        } finally { f.dispose() }
    }

    @Test fun jsReadyBeforeExactPageFinishCannotDispatchOpeningThenExactFinishCompletesOneIngress() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.ready()
            assertTrue(f.pending().pendingCommands.isEmpty())
            assertNull(shadowOf(f.engine()).lastEvaluatedJavascript,
                "JS Ready must remain latched without dispatching before exact load completion")
            f.pageFinished(ReaderWebRuntime.entrypointUrl)
            val opening = f.pending().pendingCommands.first()
            assertTrue(opening.dispatch.command is ReaderBridgeCommand.OpenPublication)
            assertNotNull(opening.lastDispatchedGeneration)
            assertEquals("openPublication", f.scriptType())
            f.post("commandAck", opening.dispatch.id)
            assertEquals("resolveCurrentDestination", f.scriptType())
            f.resolveDispatchedQuery()
            assertEquals(1, f.receipts.size)
            assertEquals(0, f.executor.activeSlotCount)
            assertEquals(0, f.registry.activeHandleCount)
        } finally { f.dispose() }
    }

    @Test fun wrongViewAndActualUrlCannotBorrowExactEntrypointCompletion() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.drain()
            val current = f.engine() as ReaderEngineWebView
            val foreign = ReaderEngineWebView(current.context)
            try {
                foreign.loadUrl(ReaderWebRuntime.entrypointUrl)
                current.webViewClient.onPageFinished(foreign, ReaderWebRuntime.entrypointUrl)
                assertTrue(f.pending().pendingCommands.isEmpty())
                assertFalse(current.runtimeInitializationCompleted)
                current.loadUrl("https://example.invalid/foreign-document")
                f.pageFinished(ReaderWebRuntime.entrypointUrl)
                assertTrue(f.pending().pendingCommands.isEmpty())
                assertFalse(current.runtimeInitializationCompleted)
            } finally { foreign.destroy() }
        } finally { f.dispose() }
    }

    @Test fun retiredEngineCannotCompleteInitializationOrDispatchLatchedReady() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.drain()
            val current = f.engine() as ReaderEngineWebView
            current.clearCurrentDestinationQueryDelegate()
            f.ready()
            f.pageFinished(ReaderWebRuntime.entrypointUrl)
            assertFalse(current.runtimeInitializationCompleted)
            assertTrue(f.pending().pendingCommands.isEmpty())
            assertEquals(0, f.receipts.size)
        } finally { f.dispose() }
    }

    @Test fun canceledConstructionCannotSubmitInitialQueryFromLaterActualEngineInitialization() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.registration.retire()
            f.drain()
            f.pageFinished(ReaderWebRuntime.entrypointUrl)
            assertEquals(0, f.receipts.size)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
            assertTrue(f.pending().pendingCommands.none { it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination })
        } finally { f.dispose() }
    }
    @Test fun nativeCloseBeforeEngineConstructionCompletionRetiresExactCommandAndOriginalMaterial() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            val material = assertNotNull(f.material())
            f.call("closeReader")
            f.drain()
            assertEquals(0, f.receipts.size)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
            assertFalse(material.owned); assertNull(material.wrapper.parent)
        } finally { f.dispose() }
    }
    @Test fun copiedReservationCannotConstructEngineOrAcquireNativeBootstrapMaterial() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertTrue(f.start(foreignReservation = true) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertEquals(0, f.constructions)
            assertNull(f.material())
            assertEquals(0, f.receipts.size)
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
        fun engine(): WebView {
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
        fun pageFinished(url: String?) {
            drain() // Let the genuine posted configure/loadUrl run before its framework callback.
            val target = engine()
            target.webViewClient.onPageFinished(target, url)
            drain()
        }
        fun ready() = post("ready")
        fun post(type: String, id: String? = null) = post(buildJsonObject {
            put("type", type)
            id?.let { put("commandId", it) }
        })
        fun scriptType(): String? {
            val script = shadowOf(engine()).lastEvaluatedJavascript ?: return null
            return (Json.parseToJsonElement(script.removePrefix("window.NavicReaderBridge.dispatch(").removeSuffix(");")) as JsonObject)["type"]
                ?.jsonPrimitive?.content
        }
        fun resolveDispatchedQuery() {
            val query = pending().pendingCommands.first().dispatch
            val command = query.command as ReaderBridgeCommand.ResolveCurrentDestination
            post(buildJsonObject { put("type", "currentDestinationResolved"); put("commandId", query.id)
                put("foliateSessionId", command.foliateSessionId); put("destinationFoliateSessionId", command.foliateSessionId)
                put("destinationCommitSequence", 1L); put("pageIndex", 0) })
        }
        private fun post(message: JsonObject) {
            (shadowOf(engine()).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as ReaderJavascriptBridge).postMessage(message.toString()); drain()
        }
        fun resolve() {
            post(buildJsonObject { put("type", "ready") })
            val opening = pending().pendingCommands.first()
            assertTrue(opening.dispatch.command is ReaderBridgeCommand.OpenPublication)
            post(buildJsonObject { put("type", "commandAck"); put("commandId", opening.dispatch.id) })
            val query = pending().pendingCommands.first().dispatch
            val command = query.command as ReaderBridgeCommand.ResolveCurrentDestination
            post(buildJsonObject { put("type", "currentDestinationResolved"); put("commandId", query.id)
                put("foliateSessionId", command.foliateSessionId); put("destinationFoliateSessionId", command.foliateSessionId)
                put("destinationCommitSequence", 1L); put("pageIndex", 0) })
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
