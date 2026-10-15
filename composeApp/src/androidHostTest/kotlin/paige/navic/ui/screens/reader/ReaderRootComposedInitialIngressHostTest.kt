package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

private typealias InitialIngress = (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?

/** Genuine Native construction/runtime-load callback and owning Engine protocol. Synthetic
 * bridge messages and test installation are not Foliate/layout/production-reader proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderRootComposedInitialIngressHostTest {
    @Test fun absentInitialCompositionIngressRejectsBeforeActualRootConstruction() = runTest {
        val f = ComposedRootFixture()
        try {
            assertTrue(f.start(provideIngress = false) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertEquals(0, f.constructions); assertNull(f.rootWrapper()); assertNull(f.material())
            assertEquals(0, f.receipts.size)
        } finally { f.dispose() }
    }
    @Test fun actualCompositionLocalFeedsDistinctPureInitialReceiptBeforeExecutorForwarding() = runTest {
        val f = ComposedRootFixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start(provideIngress = true),
                "A407.ComposedIngress.OriginalBootstrapAccepted")
            f.drain(finishEntrypointBeforeReady = true); assertNotNull(f.rootWrapper())
            assertEquals(2, f.pending().pendingCommands.size)
            f.resolve()
            val receipt = f.receipts.single()
            assertTrue(f.commonAuthorityPublishedBeforeForward)
            assertTrue(receipt.event is ReaderPresentationEvent.FoliateRelocated)
            assertNull(receipt.preVersion.publicationIdentity); assertTrue(receipt.effects.isEmpty())
            assertEquals(ReaderPresentationFrameOwner.Neutral, readerPresentationDecision(receipt.postState).frameOwner)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(0, f.registry.activeHandleCount)
        } finally { f.dispose() }
    }
    @Test fun otherSessionCompositionIngressCannotBorrowCurrentNativeBootstrap() = runTest {
        val f = ComposedRootFixture()
        try {
            assertTrue(f.start(provideIngress = true, obsoleteSession = true) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertEquals(0, f.constructions); assertNull(f.rootWrapper()); assertNull(f.material())
            assertEquals(0, f.receipts.size)
        } finally { f.dispose() }
    }
    @Test fun rootCloseBeforeInitializationRetiresComposedRequestAndOriginalMaterial() = runTest {
        val f = ComposedRootFixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start(provideIngress = true),
                "A407.ComposedIngress.OriginalBootstrapAccepted")
            val material = assertNotNull(f.material())
            f.closeRoot(); f.drain()
            assertEquals(0, f.receipts.size); assertEquals(0, f.executor.activeSlotCount)
            assertEquals(0, f.registry.activeHandleCount); assertFalse(material.owned)
            assertNull(material.wrapper.parent); assertNull(f.rootWrapper()); assertNull(f.rootKey())
        } finally { f.dispose() }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class ComposedRootFixture {
        // One fixture-owned scheduler/context for the actual overlay and command-created
        // material. No lifecycle/global WindowRecomposer can outlive another test here.
        private val compositionScheduler = TestCoroutineScheduler()
        private val compositionFrameClock = BroadcastFrameClock()
        private val compositionScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(compositionScheduler) + compositionFrameClock)
        private val recomposer = Recomposer(compositionScope.coroutineContext)
        private val recompositionRunner = compositionScope.launch { recomposer.runRecomposeAndApplyChanges() }
        private var compositionFrame = 0L
        private val activity = run {
            val key = "robolectric.areWindowsMarkedVisible"; val old = System.getProperty(key)
            System.setProperty(key, "true")
            try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
            finally { if (old == null) System.clearProperty(key) else System.setProperty(key, old) }
        }
        private val type = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer")
        private val store = ReaderActivatedSessionSnapshotStore()
        private val rootType = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeFrameRoot")
        private val root = rootType.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
            .apply { isAccessible = true }.newInstance(activity.get(), store) as ViewGroup
        private val native = rootType.getDeclaredField("viewerContainer").apply { isAccessible = true }.get(root) as ViewGroup
        private val viewerKey = ReaderViewerKey(ReaderViewerKind.WebViewPublication, "public-synthetic-root-construction")
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
        var constructions = 0; private set
        lateinit var registration: ReaderSemanticCommandRegistration
        val receipts = mutableListOf<ReaderPresentationEventReceipt>()
        var commonAuthorityPublishedBeforeForward = false; private set
        init {
            // Install before attachment or setContent can create a WindowRecomposer.
            // The original Root creates/mounts its own ComposeView under this context.
            root.compositionContext = recomposer
            activity.get().setContentView(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 800, 600)
            drain()
            assertTrue(root.isAttachedToWindow && native.width > 0 && native.height > 0,
                "Original root and Native must actually be attached and measured")
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
        fun start(provideIngress: Boolean, obsoleteSession: Boolean = false): ReaderSemanticCommandResult {
            val factory = rootType.declaredMethods.singleOrNull { it.name == "createComposedInitialDestinationBootstrapRequest" }
            assertNotNull(factory, "Actual Root must consume the captured initial composition ingress, not bound confirmation")
            val content: @Composable () -> Unit = {
                constructions += 1
                ReaderEngineWebViewHost(publicationUrl = "https://example.invalid/fixture.epub", title = "Public synthetic root construction",
                    kind = ReaderPublicationKind.Ebook, mediaOverlayEnabled = false, externalShellCover = false,
                    suppressWebShellCover = false, nativeShellCoverTint = null, settings = ReaderSettings(), startCfi = null,
                    startHref = null, startProgress = null, onEvent = {}, modifier = Modifier.fillMaxSize())
            }
            val ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt? = { event, origin ->
                val step = model.reduceSemanticDestination(event, origin); model = step.coordinator; step.presentationReceipt
            }
            if (provideIngress) {
                val overlay = rootType.getDeclaredField("composeOverlay").apply { isAccessible = true }.get(root) as ComposeView
                assertTrue(root.isAttachedToWindow, "A407.ComposedIngress.RootAttached")
                assertTrue(root.width > 0 && root.height > 0, "A407.ComposedIngress.RootMeasured")
                assertTrue(overlay.isAttachedToWindow, "A407.ComposedIngress.OverlayAttached")
                assertSame(recomposer, root.compositionContext,
                    "A407.ComposedIngress.OriginalParentCompositionContext")
                overlay.setParentCompositionContext(recomposer)
                var captured: InitialIngress? = null
                var observed = false
                overlay.setContent {
                    CompositionLocalProvider(LocalReaderInitialSemanticReceiptIngress provides ingress) {
                        captured = LocalReaderInitialSemanticReceiptIngress.current
                        observed = true
                    }
                }
                // A real measured Compose traversal observes the local. Merely advancing
                // Robolectric's looper does not require the nested view to compose.
                overlay.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                overlay.layout(0, 0, root.width, root.height)
                drain()
                assertTrue(overlay.measuredWidth > 0 && overlay.measuredHeight > 0,
                    "A407.ComposedIngress.OverlayMeasured")
                assertTrue(observed, "A407.ComposedIngress.LocalObserved")
                assertTrue(captured === ingress, "A407.ComposedIngress.LocalIdentityPreserved")
                val setter = rootType.declaredMethods.singleOrNull { it.name == "setInitialSemanticReceiptIngress" }
                assertNotNull(setter, "Actual Root must capture the versioned initial composition ingress")
                val version = if (obsoleteSession) opened.presentationVersion.copy(readerSessionGeneration = session + 1L) else opened.presentationVersion
                setter.apply { isAccessible = true }.invoke(root, version, captured)
                val retained = rootType.getDeclaredField("initialSemanticIngress").apply { isAccessible = true }
                    .get(root) as? Pair<*, *>
                if (obsoleteSession) assertNull(retained, "A407.ComposedIngress.ForeignIngressNotCaptured")
                else assertTrue(retained != null && retained.first == version && retained.second === ingress,
                    "A407.ComposedIngress.CurrentIngressCaptured")
            }
            @Suppress("UNCHECKED_CAST")
            body = factory.apply { isAccessible = true }.invoke(root, viewerKey, candidate, reservation, content) as ReaderSemanticExecutableRequest
            val id = ReaderTransitionId(session, 6L, 1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            return executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(id,
                ReaderBootstrapNativePageIntent(reservation.handle), reservation.handle),
                { registration = it; it.bindAsyncOutcome { registration.retire() } }, { receipt ->
                    commonAuthorityPublishedBeforeForward = model.controller.presentationVersion == receipt.version &&
                        model.controller.state.presentation == receipt.postState
                    receipts += receipt
                })
        }
        fun field(name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(native)
        fun call(name: String, vararg args: Any?): Any? = type.declaredMethods.single {
            it.name == name && it.parameterCount == args.size
        }.apply { isAccessible = true }.invoke(native, *args)
        fun rootWrapper(): ComposeView? = rootType.getDeclaredField("currentViewerComposeView").apply { isAccessible = true }.get(root) as? ComposeView
        fun rootKey(): ReaderViewerKey? = rootType.getDeclaredField("currentViewerKey").apply { isAccessible = true }.get(root) as? ReaderViewerKey
        fun viewerKeyValue() = viewerKey
        fun closeRoot() { rootType.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(root) }
        fun material() = field("currentFrameInstallation") as? ReaderFramePhysicalOwnershipAdapter.Lease
        private fun commitCompositionFrame() {
            Snapshot.sendApplyNotifications()
            compositionScheduler.runCurrent()
            compositionFrameClock.sendFrame(++compositionFrame * 16_000_000L)
            compositionScheduler.runCurrent()
        }
        private fun layoutOriginalRoot() {
            assertTrue(root.measuredWidth > 0 && root.measuredHeight > 0,
                "A407.ComposedIngress.OriginalLayoutBoundsRetained")
            fun invalidateMeasurement(view: View) {
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    invalidateMeasurement(view.getChildAt(index))
                }
                view.forceLayout()
            }
            invalidateMeasurement(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(root.measuredWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.measuredHeight, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
        fun drain(finishEntrypointBeforeReady: Boolean = false) {
            // Consume only the known composition -> actual layout -> Engine configure
            // boundaries. Elapsed looper time is not observation of a composition local.
            commitCompositionFrame()
            layoutOriginalRoot()
            shadowOf(Looper.getMainLooper()).idle()
            if (finishEntrypointBeforeReady) {
                val target = engine() as ReaderEngineWebView
                assertEquals(ReaderWebRuntime.entrypointUrl, target.url)
                target.webViewClient.onPageFinished(target, target.url)
                assertTrue(target.runtimeInitializationCompleted)
                assertTrue(pending().pendingCommands.all { it.lastDispatchedGeneration == null })
                assertNull(shadowOf(target).lastEvaluatedJavascript)
                assertTrue(receipts.isEmpty())
            }
            commitCompositionFrame()
            layoutOriginalRoot()
            assertEquals(Recomposer.State.Idle, recomposer.currentState.value,
                "A407.ComposedIngress.CompositionIdle")
        }
        private fun engine(): WebView {
            fun visit(v: View): WebView? {
                if (v is WebView) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))?.let { return it }
                return null
            }
            return checkNotNull(visit(checkNotNull(rootWrapper())))
        }
        fun pending(): ReaderWebCommandDispatchState = engine().webViewClient.javaClass.declaredFields.firstNotNullOfOrNull {
            val value = it.apply { isAccessible = true }.get(engine().webViewClient)
            (value as? State<*>)?.value as? ReaderWebCommandDispatchState
        } ?: error("Actual owning Engine ledger required")
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
                closeRoot(); rootWrapper()?.disposeComposition(); shadowOf(Looper.getMainLooper()).idle()
                val failure = runCatching { withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } } }.exceptionOrNull()
                if (failure != null) check(failure is ReaderPageTeardownException && generateSequence(failure as Throwable?) { it.cause }
                    .any { it.message == "GL surface is unavailable for terminal disposal" }) { "Unexpected Native construction fixture terminal disposition" }
                check(closed.isCompleted)
            } finally {
                try {
                    (rootType.getDeclaredField("composeOverlay").apply { isAccessible = true }
                        .get(root) as ComposeView).disposeComposition()
                    activity.pause().stop().destroy()
                    shadowOf(Looper.getMainLooper()).idle()
                } finally {
                    root.compositionContext = null
                    recomposer.cancel()
                    recompositionRunner.cancel()
                    compositionScope.cancel()
                    compositionScheduler.runCurrent()
                    assertEquals(Recomposer.State.ShutDown, recomposer.currentState.value,
                        "A407.ComposedIngress.FixtureRecomposerClosed")
                    assertTrue(recompositionRunner.isCompleted,
                        "A407.ComposedIngress.FixtureRunnerClosed")
                }
            }
        }
    }
}
