package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import android.content.Context
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows.shadowOf
import org.robolectric.Robolectric
import androidx.activity.ComponentActivity
import android.view.View
import android.widget.FrameLayout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import paige.navic.reader.*
import kotlin.test.*

/** Physical command-adapter contract, not normal-root or live-Foliate acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderNeutralPhysicalStartupHostTest {
    @Test fun installedBootstrapMountsFreshLifetimeWithoutReopeningLegacyOrClaimingVisibility() {
        val f = PhysicalBootstrapFixture()
        try {
            f.install()
            f.dispatch { origin, context ->
                f.lease = f.reserve(origin, context)
                f.mounted = f.lease?.let(f.source::mount) == true
            }
            assertTrue(f.mounted, "NEUTRAL_COMMAND_INSTALLATION_MUST_MOUNT")
            val lease = assertNotNull(f.lease)
            assertSame(f.parent, f.fresh.parent)
            assertTrue(lease.token != f.old.token, "NEUTRAL_NEW_PHYSICAL_LIFETIME")
            assertEquals(ReaderLegacyResourceState.Registered, lease.state)
            assertNull(lease.owner); assertNull(lease.binding); assertNull(lease.imported)
            assertFalse(f.source.admissionOpen)
            assertNull(f.source.reserve(f.parent, View(f.parent.context)) { true })
            assertFalse(f.source.mount(f.old)); assertNull(f.old.wrapper.parent)
            assertTrue(f.source.retire(lease).completed)
            assertTrue(f.source.retire(lease).completed)
            assertNull(f.fresh.parent)
        } finally { f.close() }
    }

    @Test fun staleExecutorOriginCannotReserveOrMountAfterExactSlotRetirement() {
        val f = PhysicalBootstrapFixture()
        try {
            f.install()
            f.dispatch { origin, context ->
                f.lease = f.reserve(origin, context)
                f.origin = origin; f.context = context
            }
            val lease = assertNotNull(f.lease, "NEUTRAL_RESERVE_BEFORE_PHYSICAL_ADD")
            f.registrations.single().retire()
            assertFalse(f.source.mount(lease))
            assertNull(f.reserve(assertNotNull(f.origin), assertNotNull(f.context)))
            assertNull(f.fresh.parent)
            assertTrue(f.source.retire(lease).completed)
        } finally { f.close() }
    }

    @Test fun closeReentrantlyDuringAddRetiresCapturedInstallationWithoutSecondAdd() {
        val f = PhysicalBootstrapFixture()
        try {
            f.install()
            f.duringPhysicalReturn = { f.candidate.close(); f.executor.clear() }
            f.dispatch { origin, context ->
                f.lease = f.reserve(origin, context)
                f.mounted = f.lease?.let(f.source::mount) == true
            }
            val lease = assertNotNull(f.lease, "NEUTRAL_REENTRANT_LEASE_EXISTS")
            assertFalse(f.mounted)
            assertEquals(1, f.addReturns)
            assertEquals(1, f.removeReturns)
            assertNull(f.fresh.parent)
            assertFalse(lease.owned)
            assertFalse(f.source.mount(lease))
            assertTrue(f.source.retire(lease).completed)
            assertEquals(1, f.removeReturns)
        } finally { f.close() }
    }

    @Test fun preinstallAndForeignOriginNeverStartPhysicalWork() {
        val f = PhysicalBootstrapFixture()
        try {
            val origin = ReaderPresentationEventOrigin.SemanticCommand(f.firstId(), ReaderSemanticCommandSlotId(1L))
            assertNull(f.reserve(origin, ReaderSemanticMutationStartEvidence {}))
            f.install()
            f.dispatch { actual, context ->
                f.lease = f.reserve(actual.copy(transitionId = actual.transitionId.copy(coordinatorEpoch = 6L)), context)
            }
            assertNull(f.lease); assertNull(f.fresh.parent)
            assertEquals(0, f.addReturns)
        } finally { f.close() }
    }

    @Test fun finalizedOldPhysicalViewCannotBeRemountedAsFreshBootstrapMaterial() {
        val f = PhysicalBootstrapFixture()
        try {
            f.install()
            f.dispatch { origin, context -> f.lease = f.reserve(origin, context, f.old.wrapper) }
            assertNull(f.lease); assertNull(f.old.wrapper.parent)
            assertEquals(0, f.addReturns)
        } finally { f.close() }
    }

    @Test fun unreservedExecutableCannotBorrowInstalledBootstrapPhysicalAuthority() {
        val f = PhysicalBootstrapFixture()
        try {
            f.install()
            val handle = f.registry.register { origin, context, _ ->
                f.lease = f.reserve(origin, context)
                ReaderSemanticExecutableResult.Accepted
            }
            f.executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(f.firstId(),
                ReaderBootstrapNativePageIntent(handle), handle), f.registrations::add, {})
            assertNull(f.lease); assertNull(f.fresh.parent)
            assertEquals(0, f.addReturns)
        } finally { f.close() }
    }

    @Test fun finalizationOfForeignSessionCannotAuthorizePhysicalInstallation() {
        val f = PhysicalBootstrapFixture(physicalSession = 4L)
        try {
            f.install()
            f.dispatch { origin, context -> f.lease = f.reserve(origin, context) }
            assertNull(f.lease); assertNull(f.fresh.parent)
            assertEquals(0, f.addReturns)
        } finally { f.close() }
    }

    @Test fun preparingExplicitResultRouteSuppressesLegacyBeforeRootConsequences() {
        val f = PhysicalBootstrapFixture()
        var legacy = 0
        try {
            val method = Class.forName("paige.navic.ui.screens.reader.ReaderPlatformHostsKt").declaredMethods
                .singleOrNull { it.name == "dispatchBeforeLegacy" && it.parameterCount == 4 }
            val value = method?.invoke(null, f.gateway, ReaderTransitionFact.Retry(null),
                { 17 }, { legacy++; 9 })
            assertEquals(17, value, "PREPARING_ROOT_EXPLICIT_RESULT")
            assertEquals(0, legacy); assertTrue(f.facts.isEmpty())
        } finally { f.close() }
    }

    @Test fun preparingViewerRouteSuppressesFrameActionsButPreservesChrome() {
        val f = PhysicalBootstrapFixture()
        var legacy = 0
        var registered = 0
        try {
            val method = Class.forName("paige.navic.ui.screens.reader.ReaderPlatformHostsKt").declaredMethods
                .singleOrNull { it.name.substringBefore('-') == "dispatchViewerActionBeforeLegacy" && it.parameterCount == 7 }
            fun route(action: ReaderViewerAction) = method?.invoke(null, f.gateway, ReaderControllerState(),
                action, if (method.parameterTypes[3] == java.lang.Long.TYPE) 1L else ReaderTransitionGestureId(1L),
                { registered++; Unit }, { 17 }, { legacy++; 9 })
            assertEquals(17, route(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)), "PREPARING_VIEWER_FRAME_ACTION")
            assertEquals(0, legacy); assertEquals(0, registered)
            assertEquals(9, route(ReaderViewerAction.Menu), "PREPARING_CHROME_ROUTE")
            assertEquals(1, legacy); assertEquals(0, registered)
            assertTrue(f.facts.isEmpty()); assertEquals(1, f.registry.activeHandleCount)
        } finally { f.close() }
    }

    @Test fun preparingPresentationEffectsStayClosedWhileControllerOpenStillProducesInitialization() {
        val f = PhysicalBootstrapFixture()
        var presentationEffects = 0
        try {
            val opened = ReaderController().open(ReaderEngineOpenRequest(
                publication = ReaderPublicationIdentity("public-neutral-routing", resourceHref = "fixture.epub"),
                url = "https://example.invalid/public-neutral-routing.epub"))
            assertTrue(opened.engineCommands.isNotEmpty(), "REAL_CONTROLLER_OPEN_INITIALIZATION_REMAINS_AVAILABLE")
            assertNull(opened.presentationReceipt); assertNull(opened.controller.state.presentation.binding)
            f.gateway.observeReceiptBeforeEffects(opened.presentationReceipt) { presentationEffects++ }
            assertEquals(0, presentationEffects, "PREPARING_PRESENTATION_EFFECTS_MUST_STAY_CLOSED")
        } finally { f.close() }
    }

    @Test fun preparingSemanticRoutingNeverBorrowsShadowHandleOrExecutesLegacyConsequences() {
        val f = PhysicalBootstrapFixture()
        var result: Int? = null
        var failure: Throwable? = null
        var legacy = 0
        try {
            try {
                result = f.gateway.dispatchSemanticActivatedOrLegacy(
                    intent = ::ReaderBootstrapNativePageIntent,
                    request = { _, _, _ -> ReaderSemanticExecutableResult.Rejected },
                    activatedResult = { 17 }, legacyDispatch = { legacy++; 9 })
            } catch (caught: Throwable) { failure = caught }
            assertNull(failure, "PREPARING_MUST_NOT_BORROW_SHADOW_HANDLE")
            assertEquals(17, result); assertEquals(0, legacy)
            assertEquals(1, f.registry.activeHandleCount)
            assertTrue(f.facts.isEmpty())
        } finally { f.close() }
    }

    @Test fun preparingNonsemanticRouteSuppressesLegacyAndCloseDiscardsExactReservationOnce() {
        val f = PhysicalBootstrapFixture()
        var legacy = 0
        try {
            val value = f.gateway.dispatchActivatedOrLegacy(ReaderTransitionFact.Retry(null),
                activatedResult = { 17 }, legacyDispatch = { legacy++; 9 })
            assertEquals(17, value, "PREPARING_PRESENTATION_CONSEQUENCES_CLOSED")
            assertEquals(0, legacy); assertTrue(f.facts.isEmpty())
            assertTrue(f.gateway.captureSelectedSemanticEpoch() === f.candidate.epoch)
            f.candidate.close(); f.candidate.close()
            assertEquals(0, f.registry.activeHandleCount)
            assertNull(f.gateway.captureSelectedSemanticEpoch())
            assertNull(f.store.snapshot); assertEquals(0, f.addReturns)
        } finally { f.close() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
        Task398CanonicalStoragePathShadow::class])
class ReaderNeutralNativeInstallationHostTest {
    @Test fun nativeConsumerMountsActualCapturedContainerWithoutLegacyReplacementOrVisibility() = runTest {
        val native = NativeInstallationFixture(testScheduler)
        val f = PhysicalBootstrapFixture(native.store, native.viewer)
        try {
            f.install()
            f.dispatch { origin, context -> f.mounted = native.mount(f.fresh, f.candidate, origin, context) }
            assertTrue(f.mounted, "NATIVE_NEUTRAL_COMMAND_MUST_REACH_PHYSICAL_OWNER")
            assertSame(f.parent, f.fresh.parent)
            val exact = assertIs<ReaderFramePhysicalOwnershipAdapter.Lease>(native.field("currentFrameInstallation"))
            f.lease = exact
            assertTrue(exact.token != f.old.token)
            assertEquals(ReaderLegacyResourceState.Registered, exact.state)
            assertNull(exact.binding); assertNull(exact.owner); assertNull(exact.imported)
            assertFalse(f.source.admissionOpen)
            assertFalse(native.call("replaceViewerContent", View(f.parent.context)) as Boolean)
            assertSame(f.parent, f.fresh.parent)
            f.candidate.close()
            native.call("detachViewerContent", f.fresh)
            assertNull(f.fresh.parent); assertFalse(exact.owned)
            native.call("detachViewerContent", f.fresh)
            assertNull(f.fresh.parent)
        } finally { f.close(); native.dispose() }
    }

    @Test fun nativeCloseRetiresExactCommandMaterialWithoutReopeningLegacy() = runTest {
        val native = NativeInstallationFixture(testScheduler)
        val f = PhysicalBootstrapFixture(native.store, native.viewer)
        try {
            f.install()
            f.dispatch { origin, context -> f.mounted = native.mount(f.fresh, f.candidate, origin, context) }
            assertTrue(f.mounted)
            val exact = assertIs<ReaderFramePhysicalOwnershipAdapter.Lease>(native.field("currentFrameInstallation"))
            f.lease = exact
            native.call("closeReader")
            assertTrue(f.fresh.parent == null, "NATIVE_CLOSE_MUST_REMOVE_EXACT_COMMAND_MATERIAL")
            assertFalse(exact.owned)
            assertNull(native.field("currentFrameInstallation"))
            assertFalse(f.source.admissionOpen)
            native.call("closeReader")
            assertTrue(f.source.retire(exact).completed)
        } finally { f.close(); native.dispose() }
    }

    @Test fun nativeConsumerRejectsPreparingAndRetiredSlotBeforeAnyMount() = runTest {
        val native = NativeInstallationFixture(testScheduler)
        val f = PhysicalBootstrapFixture(native.store, native.viewer)
        try {
            val origin = ReaderPresentationEventOrigin.SemanticCommand(f.firstId(), ReaderSemanticCommandSlotId(1L))
            assertFalse(native.mount(f.fresh, f.candidate, origin, ReaderSemanticMutationStartEvidence {}))
            f.install()
            f.dispatch { actual, context -> f.origin = actual; f.context = context }
            f.registrations.single().retire()
            assertFalse(native.mount(f.fresh, f.candidate, assertNotNull(f.origin), assertNotNull(f.context)))
            assertNull(f.fresh.parent)
        } finally { f.close(); native.dispose() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class NativeInstallationFixture(private val scheduler: TestCoroutineScheduler) {
    init { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
    val store = ReaderActivatedSessionSnapshotStore()
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val viewer = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer")
        .getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
        .apply { isAccessible = true }.newInstance(activity.get(), store) as ViewGroup
    init {
        activity.get().setContentView(viewer)
        viewer.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        viewer.layout(0, 0, 800, 600)
        shadowOf(Looper.getMainLooper()).idle()
    }
    fun field(name: String): Any? = viewer.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(viewer)
    fun call(name: String, vararg args: Any?): Any? = viewer.javaClass.declaredMethods.single {
        it.name == name && it.parameterCount == args.size
    }.apply { isAccessible = true }.invoke(viewer, *args)
    fun mount(wrapper: View, candidate: ReaderTransitionGatewayCandidate,
              origin: ReaderPresentationEventOrigin.SemanticCommand, context: ReaderSemanticMutationStartEvidence): Boolean {
        val method = viewer.javaClass.declaredMethods.singleOrNull { it.name == "mountNeutralSemanticViewerContent" }
            ?: return false
        return method.apply { isAccessible = true }.invoke(viewer, wrapper, candidate, origin, context) as Boolean
    }
    suspend fun dispose() {
        try {
            val closed = (field("pageRasterPreparationController") as ReaderPageRasterPreparationController).destroy()
            call("closeReader")
            shadowOf(Looper.getMainLooper()).idle()
            val failure = runCatching { withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } } }.exceptionOrNull()
            if (failure != null) check(failure is ReaderPageTeardownException && generateSequence(failure as Throwable?) { it.cause }
                .any { it.message == "GL surface is unavailable for terminal disposal" }) {
                    "UNEXPECTED_NATIVE_FIXTURE_DISPOSAL teardown=${failure is ReaderPageTeardownException} cancelled=${failure is kotlinx.coroutines.CancellationException} stage=${(failure as? ReaderPageTeardownException)?.stage}"
                }
            check(closed.isCompleted) { "NATIVE_FIXTURE_DISPOSAL_MUST_TERMINATE" }
        } finally {
            try {
                activity.pause().stop().destroy()
                shadowOf(Looper.getMainLooper()).idle()
                scheduler.runCurrent()
            } finally { Dispatchers.resetMain() }
        }
    }
}

private class PhysicalBootstrapFixture(
    val store: ReaderActivatedSessionSnapshotStore = ReaderActivatedSessionSnapshotStore(),
    private val nativeViewer: ViewGroup? = null,
    physicalSession: Long = 3L
) {
    val registry = ReaderSemanticExecutableRequestRegistry(3L, activationStore = store)
    val executor = ReaderSemanticCommandExecutor(registry)
    val gateway = ReaderTransitionGateway()
    val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(7L))
    private val physicalDomain = ReaderLegacyPhysicalDomain(physicalSession, ReaderLegacyFreezeToken(7L))
    val facts = mutableListOf<ReaderTransitionFact>()
    val registrations = mutableListOf<ReaderSemanticCommandRegistration>()
    val parent = nativeViewer?.javaClass?.getDeclaredField("viewerContentContainer")
        ?.apply { isAccessible = true }?.get(nativeViewer) as? FrameLayout
        ?: FrameLayout(RuntimeEnvironment.getApplication())
    val fresh = FrameLayout(parent.context)
    var addReturns = 0
    var removeReturns = 0
    var duringPhysicalReturn: (() -> Unit)? = null
    val source = nativeViewer?.javaClass?.getDeclaredField("framePhysicalOwnership")
        ?.apply { isAccessible = true }?.get(nativeViewer) as? ReaderFramePhysicalOwnershipAdapter
        ?: ReaderFramePhysicalOwnershipAdapter(physicalReturn = { action ->
        val before = parent.childCount
        action()
        if (parent.childCount > before) addReturns++ else removeReturns++
        val callback = duringPhysicalReturn; duringPhysicalReturn = null; callback?.invoke()
    })
    val old = checkNotNull(source.reserve(parent, View(parent.context)) { true })
    val candidate: ReaderTransitionGatewayCandidate
    val reservation: ReaderReservedNeutralBootstrapRequest
    var body: ((ReaderPresentationEventOrigin.SemanticCommand, ReaderSemanticMutationStartEvidence) -> Unit)? = null
    var lease: ReaderFramePhysicalOwnershipAdapter.Lease? = null
    var mounted = false
    var origin: ReaderPresentationEventOrigin.SemanticCommand? = null
    var context: ReaderSemanticMutationStartEvidence? = null
    init {
        check(source.mount(old))
        nativeViewer?.javaClass?.getDeclaredField("currentFrameInstallation")?.apply { isAccessible = true }?.set(nativeViewer, old)
        check(source.freezeForTransitionActivation(physicalDomain) == ReaderPortCommandResult.Accepted)
        val identity = source.snapshotFrozenOwnership().single().physicalIdentity
        check(source.drainFrozenOwnership(identity) {} == ReaderPortCommandResult.Accepted)
        check(source.finalizeActivatedHandoff(physicalDomain, null, setOf(identity)) == ReaderPortCommandResult.Accepted)
        addReturns = 0; removeReturns = 0
        check(executor.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
        candidate = checkNotNull(gateway.reserveActivatedCandidate(3L, 5L, store, facts::add, {}, registry::register,
            registry::discardCandidateReservations, executor::clear))
        reservation = checkNotNull(registry.reserveNeutralBootstrap(domain, candidate) { origin, context, _ ->
            body?.invoke(origin, context)
            ReaderSemanticExecutableResult.Accepted
        })
    }
    fun reserve(origin: ReaderPresentationEventOrigin.SemanticCommand, context: ReaderSemanticMutationStartEvidence,
                wrapper: View = fresh): ReaderFramePhysicalOwnershipAdapter.Lease? {
        val method = source.javaClass.declaredMethods.singleOrNull { it.name == "reserveNeutralSemanticInstallation" }
            ?: return null
        return method.invoke(source, parent, wrapper, candidate, store, origin, context, { true }) as? ReaderFramePhysicalOwnershipAdapter.Lease
    }
    fun install() {
        val decision = ReaderInitialActivationDecision(ReaderInitialCommittedPresentationOrigin.Neutral(3L, 5L,
            ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly), null, null, reservation)
        check(ReaderActivatedSessionInstallationBarrier(store).installTestActivatedSession(
            completeActivatedSessionPorts().copy(gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate), semantic = executor), decision
        ) == ReaderActivationInstallResult.Installed)
    }
    fun firstId() = ReaderTransitionId(3L, 5L, 1L, ReaderTransitionOperation.BootstrapNativePage,
        ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
    fun dispatch(action: (ReaderPresentationEventOrigin.SemanticCommand, ReaderSemanticMutationStartEvidence) -> Unit) {
        body = action
        executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(firstId(),
            ReaderBootstrapNativePageIntent(reservation.handle), reservation.handle), registrations::add, {})
        body = null
    }
    fun close() { candidate.close(); executor.clear(); lease?.let { source.retire(it) } }
}
