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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual Native/Engine ownership and protocol only. Test installation does not mint a
 * production capability; bridge ingress is synthetic, not live Foliate/material proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
        Task398CanonicalStoragePathShadow::class])
class ReaderNativeInitialLayoutProvenanceHostTest {
    @Test fun configuredRasterIdentityAndSemanticReceiptDoNotPublishLayoutOrMaterialReadiness() = runTest {
        val f = NativeInitialFixture()
        try {
            f.configureUnresolvedProfile = true
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            assertFalse(f.field("rasterPaginationReady") as Boolean)
            f.openAndResolve()
            val receipt = f.receipts.single()
            val binding = assertNotNull(receipt.postState.binding)
            assertEquals(1L, binding.profileGeneration, "Retain the actual configuration domain; identity is not readiness")
            assertFalse(f.field("rasterPaginationReady") as Boolean)
            assertNull(binding.preparationGeneration); assertNull(binding.rasterGeneration); assertNull(binding.textureGeneration)
            assertTrue(receipt.effects.isEmpty())
            assertEquals(ReaderPresentationFrameOwner.Neutral, readerPresentationDecision(receipt.postState).frameOwner)
            val material = assertNotNull(f.lease)
            assertEquals(ReaderLegacyResourceState.Registered, material.state); assertNull(material.owner); assertNull(material.binding)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
        } finally { f.dispose() }
    }

    @Test fun changingUnresolvedConfigurationEpochStillRejectsObsoleteInitialResult() = runTest {
        val f = NativeInitialFixture()
        try {
            f.configureUnresolvedProfile = true
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.openAndResolve(replaceProfileBeforeResult = true)
            assertEquals(0, f.receipts.size)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
        } finally { f.dispose() }
    }

    private class NativeInitialFixture {
        private val activity = run {
            val key = "robolectric.areWindowsMarkedVisible"; val old = System.getProperty(key)
            System.setProperty(key, "true")
            try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
            finally { if (old == null) System.clearProperty(key) else System.setProperty(key, old) }
        }
        val store = ReaderActivatedSessionSnapshotStore()
        val opened = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-native-initial", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        var model = ReaderCoordinator(controller = opened)
        private val type = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer")
        private val native = type.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
            .apply { isAccessible = true }.newInstance(activity.get(), store) as ViewGroup
        private val source = field("framePhysicalOwnership") as ReaderFramePhysicalOwnershipAdapter
        private val parent = field("viewerContentContainer") as ViewGroup
        private val wrapper = ComposeView(activity.get()).apply {
            setContent { ReaderEngineWebViewHost(publicationUrl = "https://example.invalid/fixture.epub", title = "Public synthetic Native initial",
                kind = ReaderPublicationKind.Ebook, mediaOverlayEnabled = false, externalShellCover = false,
                suppressWebShellCover = false, nativeShellCoverTint = null, settings = ReaderSettings(),
                startCfi = null, startHref = null, startProgress = null, onEvent = {}, modifier = Modifier.fillMaxSize()) }
        }
        private val session = opened.state.readerSessionGeneration
        private val domain = ReaderLegacyPhysicalDomain(session, ReaderLegacyFreezeToken(7L))
        val registry = ReaderSemanticExecutableRequestRegistry(session, activationStore = store)
        val executor = ReaderSemanticCommandExecutor(registry)
        private val gateway = ReaderTransitionGateway()
        private val candidate: ReaderTransitionGatewayCandidate
        private val reservation: ReaderReservedNeutralBootstrapRequest
        private var body: ReaderSemanticExecutableRequest? = null
        val receipts = mutableListOf<ReaderPresentationEventReceipt>()
        var lease: ReaderFramePhysicalOwnershipAdapter.Lease? = null
        var cancelInsideIngress = false
        var configureUnresolvedProfile = false
        private var reachedFactory = false
        private var missingFactory = false
        private var queryViewport: Any? = null
        private var querySize: Pair<Int, Int>? = null
        private var queryReporterEpoch: Long? = null
        lateinit var registration: ReaderSemanticCommandRegistration
        lateinit var origin: ReaderPresentationEventOrigin.SemanticCommand
        init {
            activity.get().setContentView(native)
            drain()
            call("preparePresentationEpoch", opened.presentationVersion, opened.state.presentation, false, true, false)
            call("setPresentationDecision", readerPresentationDecision(opened.state.presentation), opened.state.presentation,
                opened.presentationVersion, false, null, { _: ReaderPresentationEvent -> null }, { _: ReaderPresentationHostEffect -> Unit })
            assertNull(nativeBinding())
            val old = checkNotNull(source.reserve(parent, View(parent.context)) { true })
            check(source.mount(old))
            check(source.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
            val identity = source.snapshotFrozenOwnership().single().physicalIdentity
            check(source.drainFrozenOwnership(identity) {} == ReaderPortCommandResult.Accepted)
            check(source.finalizeActivatedHandoff(domain, null, setOf(identity)) == ReaderPortCommandResult.Accepted)
            check(executor.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
            candidate = checkNotNull(gateway.reserveActivatedCandidate(session, 5L, store, {}, {}, registry::register,
                registry::discardCandidateReservations, executor::clear))
            reservation = checkNotNull(registry.reserveNeutralBootstrap(domain, candidate) { o, c, cb ->
                body?.invoke(o, c, cb) ?: ReaderSemanticExecutableResult.Rejected
            })
            val decision = ReaderInitialActivationDecision(ReaderInitialCommittedPresentationOrigin.Neutral(session, 5L,
                ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly), null, null, reservation)
            check(ReaderActivatedSessionInstallationBarrier(store).installTestActivatedSession(
                completeActivatedSessionPorts().copy(gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate), semantic = executor), decision
            ) == ReaderActivationInstallResult.Installed)
        }
        fun start(foreignReservation: Boolean = false): ReaderSemanticCommandResult {
            body = { o, c, callback ->
                origin = o
                val mounted = call("mountNeutralSemanticViewerContent", wrapper, candidate, o, c) as Boolean
                check(mounted) { "Real Native command mount must be reached before initial semantic producer" }
                drain() // Host scheduling only; production must use actual construction/initialization callbacks.
                lease = field("currentFrameInstallation") as ReaderFramePhysicalOwnershipAdapter.Lease
                if (configureUnresolvedProfile) call("onRasterProfileEpochChanged", 1L) // Negative phase input, not real layout proof.
                reachedFactory = true
                val factory = type.declaredMethods.singleOrNull { it.name == "createInitialDestinationSemanticRequest" }
                missingFactory = factory == null
                assertNotNull(factory, "Actual Native owner must create initial context without confirmed binding")
                val ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt? = { event, commandOrigin ->
                    val step = model.reduceSemanticDestination(event, commandOrigin); model = step.coordinator
                    if (cancelInsideIngress) registration.retire()
                    step.presentationReceipt
                }
                val supplied = if (foreignReservation) ReaderReservedNeutralBootstrapRequest(reservation.handle, session) else reservation
                @Suppress("UNCHECKED_CAST")
                val request = factory.apply { isAccessible = true }.invoke(native, candidate, supplied, ingress) as ReaderSemanticExecutableRequest
                queryViewport = field("presentationViewportGeneration")
                querySize = native.width to native.height
                queryReporterEpoch = (field("presentationBindingReporter") as ReaderPresentationBindingReporter).captureEpoch()
                request(o, c, callback)
            }
            val id = ReaderTransitionId(session, 5L, 1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            val result = executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(id,
                ReaderBootstrapNativePageIntent(reservation.handle), reservation.handle),
                { registration = it; it.bindAsyncOutcome { registration.retire() } }, receipts::add)
            assertTrue(reachedFactory, "Native fixture must reach initial producer after actual physical mount")
            assertFalse(missingFactory, "Actual Native initial context factory is missing; executor swallowed the feature assertion")
            return result
        }
        fun field(name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(native)
        fun call(name: String, vararg args: Any?): Any? = type.declaredMethods.single {
            it.name == name && it.parameterCount == args.size
        }.apply { isAccessible = true }.invoke(native, *args)
        fun nativeBinding(): ReaderPresentationBinding? = call("currentPresentationBinding") as? ReaderPresentationBinding
        private fun engine(): WebView {
            fun visit(v: View): WebView? {
                if (v is WebView) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))?.let { return it }
                return null
            }
            return checkNotNull(visit(wrapper))
        }
        fun pending(): ReaderWebCommandDispatchState = engine().webViewClient.javaClass.declaredFields.firstNotNullOfOrNull {
            val value = it.apply { isAccessible = true }.get(engine().webViewClient)
            (value as? State<*>)?.value as? ReaderWebCommandDispatchState
        } ?: error("Actual Engine ledger required")
        fun drain() {
            // The actual Activity ViewRoot supplies layout; a second forced measurement
            // here would change the genuine captured viewport between command phases.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48))
        }
        private fun post(message: JsonObject) {
            (shadowOf(engine()).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as ReaderJavascriptBridge).postMessage(message.toString())
            drain()
            // Fixed booleans only: distinguish fixture layout churn from semantic authority.
            println("ROOT233_NATIVE_INITIAL_SCOPE viewportCurrent=${queryViewport == field("presentationViewportGeneration")} " +
                "sizeCurrent=${querySize == (native.width to native.height)} " +
                "reporterEpochCurrent=${queryReporterEpoch == (field("presentationBindingReporter") as ReaderPresentationBindingReporter).captureEpoch()} " +
                "slotActive=${executor.activeSlotCount > 0} handleActive=${registry.activeHandleCount > 0}")
        }
        fun openAndResolve(replaceProfileBeforeResult: Boolean = false) {
            val target = engine() as ReaderEngineWebView
            assertEquals(ReaderWebRuntime.entrypointUrl, target.url)
            target.webViewClient.onPageFinished(target, target.url)
            assertTrue(target.runtimeInitializationCompleted)
            assertTrue(pending().pendingCommands.all { it.lastDispatchedGeneration == null })
            assertNull(shadowOf(target).lastEvaluatedJavascript)
            assertTrue(receipts.isEmpty())
            post(buildJsonObject { put("type", "ready") })
            val opening = pending().pendingCommands.first()
            assertTrue(opening.dispatch.command is ReaderBridgeCommand.OpenPublication)
            assertEquals(pending().runtimeGeneration, opening.lastDispatchedGeneration)
            post(buildJsonObject { put("type", "commandAck"); put("commandId", opening.dispatch.id) })
            val query = pending().pendingCommands.first().dispatch
            val command = query.command as ReaderBridgeCommand.ResolveCurrentDestination
            if (replaceProfileBeforeResult) call("onRasterProfileEpochChanged", 2L)
            post(buildJsonObject {
                put("type", "currentDestinationResolved"); put("commandId", query.id)
                put("foliateSessionId", command.foliateSessionId); put("destinationFoliateSessionId", command.foliateSessionId)
                put("destinationCommitSequence", 1L); put("pageIndex", 0)
            })
        }
        suspend fun dispose() {
            try {
                candidate.close(); executor.clear(); registry.clear()
                val closed = (field("pageRasterPreparationController") as ReaderPageRasterPreparationController).destroy()
                call("closeReader"); wrapper.disposeComposition(); shadowOf(Looper.getMainLooper()).idle()
                val failure = runCatching { withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } } }.exceptionOrNull()
                if (failure != null) check(failure is ReaderPageTeardownException && generateSequence(failure as Throwable?) { it.cause }
                    .any { it.message == "GL surface is unavailable for terminal disposal" }) { "Unexpected Native fixture terminal disposition" }
                check(closed.isCompleted) { "Native fixture terminal disposition required" }
            } finally { activity.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle() }
        }
    }
}
