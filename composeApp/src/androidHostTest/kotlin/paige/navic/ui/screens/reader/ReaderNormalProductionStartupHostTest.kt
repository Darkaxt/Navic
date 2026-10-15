package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.PopupWindow
import androidx.activity.ComponentActivity
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowWebView
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector
import paige.navic.reader.*

/**
 * Calls the actual normal Android composition, not an installation fixture. Robolectric
 * does not execute Foliate: missing-ingress controls assert preinstall custody, while
 * initial-ingress tests require the original production installation/runtime/bootstrap
 * owners. Neither group claims semantic readiness, raster, display, or a readable reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    instrumentedPackages = ["androidx.webkit.internal"],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
        Task398CanonicalStoragePathShadow::class, Task407UnitWebViewMeasureBoundary::class])
class ReaderNormalProductionStartupHostTest {
    @Test
    fun earlyControllerOpenSelectsNormalProductionCandidateBeforeSemanticReadiness() {
        val fixture = NormalRootFixture()
        try {
            fixture.mount()
            assertTrue(fixture.opened.controller.state.readerSessionGeneration > 0L,
                "The normal root receives a genuine early Controller.open session")
            assertTrue(fixture.opened.presentationReceipt == null,
                "Opening a session must not fabricate a presentation receipt")
            assertEquals(ReaderTransitionGatewayMode.Preparing, fixture.gateway.mode,
                "Normal production startup must select its private candidate, not an empty Shadow attachment")
            assertNotNull(fixture.gateway.captureSelectedSemanticEpoch(),
                "The normal composition must own an exact pre-install candidate")
            assertFalse(fixture.gateway.observeLegacyPresentationEvent(ReaderPresentationEvent.NativePageRequested),
                "Selected startup must not dispatch a competing legacy presentation consequence")
            assertEquals(0, fixture.semanticIngressCount,
                "Host composition without live Foliate cannot manufacture bootstrap authority")
            assertNull(fixture.store().snapshot, "Missing initial ingress must keep production installation fail-closed")
            assertNull(fixture.runtime())
            assertTrue(fixture.constructionObservations.isEmpty(), "Missing ingress cannot construct the owning Engine")
            assertFalse(fixture.canAcceptNewPointer(), "Default startup must not grant page-turn input")
        } finally { fixture.close() }
    }

    @Test
    fun sameSessionNormalRecompositionKeepsPreinstallCandidateAndOriginalPhysicalMount() {
        val fixture = NormalRootFixture()
        try {
            fixture.mount()
            val originalEpoch = fixture.gateway.captureSelectedSemanticEpoch()
            assertNotNull(originalEpoch, "Normal startup must own a candidate before refresh")
            val originalRoot = fixture.nativeRoot()
            fixture.refresh()
            assertSame(originalEpoch, fixture.gateway.captureSelectedSemanticEpoch(),
                "Same-session recomposition must not replace the selected attachment")
            assertSame(originalRoot, fixture.nativeRoot(),
                "Same-session recomposition must preserve the original normal Native root")
            assertEquals(ReaderTransitionGatewayMode.Preparing, fixture.gateway.mode,
                "Refresh without Foliate readiness must not install or fall back")
            assertEquals(0, fixture.semanticIngressCount,
                "Refresh cannot manufacture a semantic receipt")
        } finally { fixture.close() }
    }

    @Test
    fun closeBeforeSemanticReadinessRetiresNormalCandidateWithoutExecutingBootstrap() {
        val fixture = NormalRootFixture()
        try {
            fixture.mount()
            assertNotNull(fixture.gateway.captureSelectedSemanticEpoch(),
                "Close coverage requires an actual normal pre-install candidate")
            fixture.closeComposition()
            assertTrue(fixture.gateway.captureSelectedSemanticEpoch() == null,
                "Close must retire the exact untransferred candidate")
            assertEquals(0, fixture.semanticIngressCount,
                "Closing before live Foliate readiness cannot execute or fabricate bootstrap authority")
        } finally { fixture.close() }
    }

    @Test
    fun initialIngressInstallsOriginalRuntimeBeforeCommandConstruction() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val installed = assertNotNull(fixture.store().snapshot,
                "Attached measured normal root with genuine initial ingress must install the actual production ports")
            assertTrue(installed.portAuthority is ReaderActivatedSessionPortAuthority.Production,
                "Test-port installation cannot qualify normal startup")
            assertEquals(1, fixture.store().atomicWriteCount)
            assertEquals(ReaderTransitionGatewayMode.Activated, fixture.gateway.mode)
            val runtime = assertNotNull(fixture.runtime(), "The installed root must retain its original runtime")
            val construction = fixture.constructionObservations.single()
            assertSame(installed, construction.snapshot,
                "Command-created Engine content must see the original installed snapshot")
            assertSame(runtime, construction.runtime,
                "Runtime must be assigned before bootstrap construction can reenter")
            assertTrue(construction.originalFinalizationReturned,
                "Bootstrap cannot construct content inside original finalization")
            assertNotNull(fixture.engine(), "Actual owning Engine must be mounted by the reserved bootstrap")
            assertNotNull(installed.reservedNeutralBootstrap)
            assertEquals(ReaderTransitionOperation.BootstrapNativePage, runtime.snapshot().activeOperation)
            assertEquals(1, runtime.snapshot().activeTransitionsRegistered)
            assertEquals(1, fixture.executor().activeSlotCount)
            assertEquals(1, fixture.requests().activeHandleCount)
            assertEquals(0, fixture.initialIngressCount,
                "Engine construction without runtime readiness cannot manufacture Foliate authority")
            assertEquals(ReaderPresentationFrameOwner.Neutral, fixture.opened.controller.state.presentationDecision.frameOwner,
                "Installation and a real WebView are not successor display acknowledgement")
            assertFalse(fixture.canAcceptNewPointer(), "No physical frame proof means no page-turn input")
        } finally { fixture.close() }
    }

    @Test
    fun sameSessionRefreshKeepsOneInstalledBootstrap() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val installed = assertNotNull(fixture.store().snapshot,
                "Refresh coverage requires actual normal production installation")
            val epoch = assertNotNull(fixture.gateway.captureActivatedSemanticEpoch())
            val runtime = assertNotNull(fixture.runtime())
            val engine = assertNotNull(fixture.engine())
            val material = assertNotNull(fixture.material())
            val reservation = assertNotNull(installed.reservedNeutralBootstrap)
            fixture.refresh()
            assertSame(installed, fixture.store().snapshot)
            assertSame(epoch, fixture.gateway.captureActivatedSemanticEpoch())
            assertSame(runtime, fixture.runtime())
            assertSame(engine, fixture.engine())
            assertSame(material, fixture.material())
            assertSame(reservation, fixture.store().reservedNeutralBootstrap)
            assertEquals(1, fixture.store().atomicWriteCount)
            assertEquals(1, fixture.constructionObservations.size)
            assertEquals(1, runtime.snapshot().activeTransitionsRegistered,
                "Same-session refresh cannot admit another bootstrap")
            assertEquals(1, fixture.executor().activeSlotCount)
            assertEquals(1, fixture.requests().activeHandleCount)
            assertEquals(0, fixture.initialIngressCount)
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    @Test
    fun lateInitialIngressStartsTheRetainedMeasuredRootExactlyOnce() {
        val fixture = NormalRootFixture(provideInitialIngress = true, startWithInitialIngress = false)
        try {
            fixture.mount()
            val root = fixture.nativeRoot()
            val epoch = assertNotNull(fixture.gateway.captureSelectedSemanticEpoch())
            assertNull(fixture.store().snapshot)
            assertNull(fixture.runtime())
            assertNull(fixture.material())
            assertTrue(fixture.constructionObservations.isEmpty())
            assertFalse(fixture.canAcceptNewPointer())
            fixture.deliverInitialIngress()
            val installed = assertNotNull(fixture.store().snapshot)
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            assertSame(root, fixture.nativeRoot())
            assertSame(epoch, fixture.gateway.captureActivatedSemanticEpoch())
            assertEquals(1, fixture.constructionObservations.size)
            assertSame(runtime, fixture.constructionObservations.single().runtime)
            assertTrue(fixture.constructionObservations.single().originalFinalizationReturned)
            val originalCommands = fixture.engineCommands()
            assertEquals(1, originalCommands.pendingCommands.count { it.dispatch.command is ReaderBridgeCommand.OpenPublication },
                "Late authentic ingress must reach original Engine configuration, not just install a runtime")
            assertEquals(1, originalCommands.pendingCommands.count { it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination },
                "The retained bootstrap must queue the genuine original destination query exactly once")
            assertFalse(originalCommands.pendingCommands.any { it.lastDispatchedGeneration != null },
                "Configure completion is not real JS Ready; the original Engine dispatch gate must remain closed")
            fixture.refresh()
            assertSame(installed, fixture.store().snapshot)
            assertSame(runtime, fixture.runtime())
            assertSame(material, fixture.material())
            assertEquals(1, fixture.store().atomicWriteCount)
            assertEquals(1, runtime.snapshot().activeTransitionsRegistered)
            assertEquals(0, fixture.initialIngressCount)
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    /** Deliberate next-gate contracts, not turn/highlight acceptance. These exercise the
     * actual installed Root in one continuing session without fake settlement/frame ACKs.
     * An installed command consumer must replace these legacy consequences before the
     * real repeated-turn and captured-versus-displayed WordSync gate can be admitted. */
    @Test
    fun continuousInstalledVisualObservationsCannotReopenTheLegacyTurnSettlementWriter() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val runtime = assertNotNull(fixture.runtime())
            val installed = assertNotNull(fixture.store().snapshot)
            val material = assertNotNull(fixture.material())
            val foliateSession = fixture.engineCommands().pendingCommands.map { it.dispatch.command }
                .filterIsInstance<ReaderBridgeCommand.ResolveCurrentDestination>().single().foliateSessionId
            val originalOrdinal = fixture.originalCurlField("currentOrdinal")
            fixture.deliverVisualLocation(1, foliateSession)
            fixture.deliverVisualLocation(2, foliateSession)
            assertSame(installed, fixture.store().snapshot)
            assertSame(runtime, fixture.runtime())
            assertSame(material, fixture.material())
            assertEquals(originalOrdinal, fixture.originalCurlField("currentOrdinal"),
                "Installed observations need original command/settlement custody; they cannot revive the frozen legacy turn writer")
            assertEquals(1, runtime.snapshot().activeTransitionsRegistered)
            assertEquals(ReaderTransitionOperation.BootstrapNativePage, runtime.snapshot().activeOperation)
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    @Test
    fun installedWordSyncWithoutDisplayedAuthorityCannotWriteTheLegacyOverlay() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            val originalColor = fixture.originalCurlField("whispersyncHighlightColorArgb")
            fixture.deliverUnboundWordSync(0xFF445566.toInt())
            fixture.deliverUnboundWordSync(0xFF667788.toInt())
            assertSame(runtime, fixture.runtime())
            assertSame(material, fixture.material())
            assertEquals(originalColor, fixture.originalCurlField("whispersyncHighlightColorArgb"),
                "Installed WordSync needs original displayed-deck association, not the legacy highlight writer")
            assertNull(fixture.originalCurlField("latestWhispersyncAnchorReceipt"))
            assertNull(fixture.originalCurlField("publishedWhispersyncOverlay"))
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    /** New, execution-unqualified positive ROOT contract. The original installed
     * allocation supplies all material identities; canonical inputs exercise the existing
     * proof/target/mask consumers. Captured masks are not displayed WordSync acceptance. */
    @Test
    fun canonicalWordSyncBoundariesRetainOriginalInstalledMaterialWhilePassivePreparationWaits() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val installed = assertNotNull(fixture.store().snapshot)
            val material = assertNotNull(fixture.material())
            fixture.returnOriginalInitialLayoutAndPlan()
            val work = fixture.originalInstalledWork()
            val binding = work.lease.binding
            val profile = fixture.originalCurlField("publishedRasterProfile") as ReaderPlayLikeCurlRasterProfile
            assertEquals(binding.rasterGeneration, profile.rasterGeneration)
            val engine = assertNotNull(fixture.engine())
            val geometry = ReaderPageTurnCaptureGeometry(engine.width.toDouble(), engine.height.toDouble(),
                ReaderPageTurnLayoutMode.Single, listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full,
                    0.0, 0.0, engine.width.toDouble(), engine.height.toDouble())))
            var targetCount = 0
            var maskCount = 0
            repeat(3) { boundary ->
                val receipt = ReaderWhispersyncAnchorReceipt(
                    foliateSessionId = binding.foliateSessionId,
                    destinationCommitToken = "public-synthetic-canonical-boundary",
                    visualPageOrdinal = 0, spineIndex = 0,
                    rasterGeneration = work.lease.rasterGeneration,
                    textureGeneration = assertNotNull(binding.textureGeneration),
                    presentationMutationGeneration = 1L, presentationSequence = 1L,
                    anchorGeneration = 1L, boundarySequence = boundary.toLong() + 1L,
                    layoutGeneration = 7L, viewGeneration = 3L,
                    commitSequence = assertNotNull(binding.destinationCommitIdentity).commitSequence,
                    committedSpineIndex = 0, committedChapterPageIndex = 0, committedChapterPageCount = 8,
                    paginationFingerprint = "public-pagination", layoutFingerprint = "public-layout",
                    readerSettingsRasterKey = "public-settings", captureGeometry = geometry,
                    pageLocalRects = listOf(ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Full,
                        20.0, 30.0, 40.0, 20.0)))
                val proof = receipt.nativePresentationProof()
                val targets = assertNotNull(readerWhispersyncNativeOverlayTargets(receipt, proof,
                    binding.foliateSessionId, profile, currentOrdinal = 0,
                    textureGeneration = assertNotNull(binding.textureGeneration)))
                targetCount += targets.size
                for (target in targets) {
                    val mask = assertNotNull(readerWhispersyncHighlightMask(receipt, target, 80, 60, 0x66336699))
                    try { assertFalse(mask.isRecycled); maskCount++ } finally { mask.recycle() }
                }
                fixture.deliverWordSync(receipt, 0x66336699)
                assertSame(receipt, fixture.viewerField("whispersyncAnchorReceipt"))
                assertSame(installed, fixture.store().snapshot)
                assertSame(material, fixture.material())
                assertSame(work, fixture.originalInstalledWork())
                assertTrue(work.isCurrent())
                assertNull(fixture.originalCurlField("publishedWhispersyncOverlay"),
                    "Canonical capture input is not displayed-deck authority")
            }
            assertEquals(3, targetCount)
            assertEquals(3, maskCount)
            assertEquals(1, assertNotNull(fixture.runtime()).snapshot().activeTransitionsRegistered)
            assertFalse(fixture.canAcceptNewPointer())
            // No passive Ready, deck/frame/input ACK or Jump is invented. The actual
            // ready/deck/overlay-during-curl/next-turn publication gate is still required.
        } finally { fixture.close() }
    }

    /** Original ROOT bindings only. Controlled UNIT measurement reaches the existing
     * publisher/decoration consumers, but is not a Curl producer or displayed acceptance. */
    @Test
    fun originalInstalledConsumersBindWithoutCertifyingAnUnreadiedPhysicalDeck() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val installed = assertNotNull(fixture.store().snapshot)
            val material = assertNotNull(fixture.material())
            val engine = assertNotNull(fixture.engine())
            assertTrue((shadowOf(engine) as Task407UnitWebViewMeasureBoundary).originalExactSpecsObserved,
                "A407.UnitPlatform.OriginalEngineMeasureInvoked")
            assertTrue(engine.measuredWidth > 0 && engine.measuredHeight > 0,
                "A407.UnitPlatform.OriginalEngineMeasured")
            fixture.assertOriginalInstalledFrameResolversBound()
            assertTrue(fixture.refreshOriginalInstalledDecoration() is ReaderPortCommandResult.Rejected,
                "A407.Decoration.NoDisplayedAssociationRejected")
            assertSame(installed, fixture.store().snapshot)
            assertSame(material, fixture.material())
            assertNull(fixture.originalCurlField("publishedWhispersyncOverlay"),
                "A407.Decoration.NoSelfCertifiedDisplay")
            assertFalse(fixture.canAcceptNewPointer(), "A407.Terminal.NoProducerDoesNotGrantNextTurn")
        } finally { fixture.close() }
    }

    /** Original ROOT prerequisite controls, not a successful installed turn fixture.
     * The passive producer has not returned Ready/capture, so pointer gestures must NOT
     * be converted into a fabricated typed turn or competing legacy consequence.
     * Prospective post-r36 qualification: retain the original three-frame startup and
     * pointer sequence, supplement ONLY the observed attached popup's owned entrance
     * demand. r36's unchanged final Idle RED remains historical, not relabeled GREEN. */
    @Test
    fun originalRootPointerCannotBypassItsDelayedBootstrapPreparation() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            synchronizationProfile = SynchronizationProfile.ProspectiveAttachedBusyEntrance)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            val installed = assertNotNull(fixture.store().snapshot, "C408.Root.OriginalInstallation")
            val runtime = assertNotNull(fixture.runtime(), "C408.Root.OriginalCoordinator")
            val work = fixture.originalInstalledWork()
            val commandCount = fixture.engineCommands().pendingCommands.size
            val before = runtime.snapshot()
            repeat(3) {
                fixture.dispatchOriginalRootPointer(100L + it * 10L)
                fixture.assertOriginalPointerUsedPlayLikeCurl()
                fixture.refresh()
                assertSame(installed, fixture.store().snapshot, "C408.Root.DelayedPreparationKeepsInstallation")
                assertSame(work, fixture.originalInstalledWork(), "C408.Root.DelayedPreparationKeepsOneWork")
                assertEquals(before.activeTransitionsRegistered, runtime.snapshot().activeTransitionsRegistered,
                    "C408.Root.NoAutomaticTurnBeforeDisplayedBootstrap")
                assertEquals(commandCount, fixture.engineCommands().pendingCommands.size,
                    "C408.Root.NoLegacyNavigationWhileBootstrapWaits")
                assertFalse(fixture.canAcceptNewPointer(), "C408.RootPrerequisite.PassiveProducerAndDisplayRequired")
            }
            assertEquals(1, fixture.initialIngressCount, "C408.Root.InitialSemanticIngressOnce")
            assertEquals(0, fixture.semanticIngressCount, "C408.Root.NoUntaggedSemanticReplay")
            assertEquals(ReaderTransitionOperation.BootstrapNativePage, runtime.snapshot().activeOperation)
        } finally { fixture.close() }
    }

    /** Refresh-only sibling of the ORIGINAL pointer control: same startup/plan and
     * three refreshes, without a pointer. The post-r36 profile must add ZERO frames
     * without actual popup demand; the original three-frame work remains unchanged.
     * Neither control supplies passive Ready or certifies a displayed reader. */
    @Test
    fun originalRootRefreshWithoutPointerKeepsDelayedBootstrapCompositionIdle() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            synchronizationProfile = SynchronizationProfile.ProspectiveAttachedBusyEntrance)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            val installed = assertNotNull(fixture.store().snapshot)
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            val work = fixture.originalInstalledWork()
            val commandCount = fixture.engineCommands().pendingCommands.size
            val before = runtime.snapshot()
            repeat(3) {
                fixture.refresh()
                assertSame(installed, fixture.store().snapshot, "C408.RefreshOnly.OriginalInstallationRetained")
                assertSame(material, fixture.material(), "C408.RefreshOnly.OriginalMaterialRetained")
                assertSame(work, fixture.originalInstalledWork(), "C408.RefreshOnly.OriginalPreparationRetained")
                assertTrue(before == runtime.snapshot(), "C408.RefreshOnly.NoCoordinatorProgress")
                assertEquals(commandCount, fixture.engineCommands().pendingCommands.size,
                    "C408.RefreshOnly.NoLegacyNavigation")
                assertFalse(fixture.canAcceptNewPointer(), "C408.RefreshOnly.NoInputAuthority")
            }
            assertEquals(1, fixture.initialIngressCount, "C408.RefreshOnly.InitialSemanticIngressOnce")
            assertEquals(0, fixture.semanticIngressCount, "C408.RefreshOnly.NoUntaggedSemanticReplay")
            assertEquals(0, fixture.busyEntranceDemandedFrameCount,
                "C408.RefreshOnly.NoBusyEntranceSupplementWithoutPointer")
            fixture.assertNoBusyPopupOrFrameDemand()
        } finally { fixture.close() }
    }

    /** Post-r36 prospective profile, NOT relabeled r32 evidence. Same original startup,
     * plan and first pointer as the original control; no extra pre-pointer refresh,
     * feedback invocation, replacement popup, passive Ready or fabricated display ACK. */
    @Test
    fun originalRejectedPointerAttachedPopupDemandEndsWithoutAdvancingPreparation() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            synchronizationProfile = SynchronizationProfile.ProspectiveAttachedBusyEntrance)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            fixture.assertNoBusyPopupOrFrameDemand()
            assertEquals(0, fixture.busyEntranceDemandedFrameCount,
                "C408.AttachedBusyEntrance.NoStartupSupplement")
            val root = assertNotNull(fixture.nativeRoot())
            val installed = assertNotNull(fixture.store().snapshot)
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            val engine = assertNotNull(fixture.engine())
            val work = fixture.originalInstalledWork()
            val lease = work.lease
            val source = assertNotNull(fixture.viewerField("passiveRasterPreparationAdapter")
                as? ReaderPassiveRasterPreparationAdapter)
            assertFalse(source.isAvailable, "C408.AttachedBusyEntrance.OriginalPassiveSourceUnready")
            val originalCommands = fixture.engineCommands().pendingCommands.map { it.dispatch.id }
            val before = runtime.snapshot()
            fixture.assertOriginalWindowOwnsEveryPlacement {
                fixture.dispatchOriginalRootPointer(100L)
                fixture.assertOriginalPointerUsedPlayLikeCurl()
                fixture.refreshWithBusyAnimationDemandWitness()
            }
            assertTrue(fixture.busyEntranceDemandedFrameCount in 1..12,
                "C408.AttachedBusyEntrance.SourceBoundedDemandActuallyServiced")
            assertSame(root, fixture.nativeRoot(), "C408.AttachedBusyEntrance.OriginalRootRetained")
            assertSame(installed, fixture.store().snapshot, "C408.AttachedBusyEntrance.OriginalInstallationRetained")
            assertSame(runtime, fixture.runtime(), "C408.AttachedBusyEntrance.OriginalCoordinatorRetained")
            assertSame(material, fixture.material(), "C408.AttachedBusyEntrance.OriginalMaterialRetained")
            assertSame(engine, fixture.engine(), "C408.AttachedBusyEntrance.OriginalEngineRetained")
            assertSame(work, fixture.originalInstalledWork(), "C408.AttachedBusyEntrance.OriginalPreparationRetained")
            assertSame(lease, fixture.originalInstalledWork().lease, "C408.AttachedBusyEntrance.OriginalLeaseRetained")
            assertSame(source, fixture.viewerField("passiveRasterPreparationAdapter"),
                "C408.AttachedBusyEntrance.OriginalSourceRetained")
            assertTrue(work.isCurrent(), "C408.AttachedBusyEntrance.OriginalPreparationCurrent")
            assertFalse(source.isAvailable, "C408.AttachedBusyEntrance.AnimationCannotSupplyPassiveReady")
            assertTrue(material.owned && material.wrapper.parent != null,
                "C408.AttachedBusyEntrance.OriginalMaterialStillOwnedAndAttached")
            assertTrue(before == runtime.snapshot(), "C408.AttachedBusyEntrance.NoCoordinatorProgress")
            assertTrue(originalCommands == fixture.engineCommands().pendingCommands.map { it.dispatch.id },
                "C408.AttachedBusyEntrance.NoLegacyNavigation")
            assertEquals(1, fixture.initialIngressCount, "C408.AttachedBusyEntrance.NoInitialSemanticReplay")
            assertEquals(0, fixture.semanticIngressCount, "C408.AttachedBusyEntrance.NoUntaggedSemanticReplay")
            assertFalse(fixture.canAcceptNewPointer(), "C408.AttachedBusyEntrance.NoInputOrDisplayAuthority")
        } finally { fixture.close() }
    }

    /** Quarantined prospective profile, not a shipping prerequisite or causal RED.
     * r32 failed at OriginalRejectedPointerPopupShowing, before animation/Idle; the
     * original callback/token and popup demand were NOT qualified. This test also has
     * an extra pre-pointer refresh absent from the original pointer control above.
     * Retain its body/evidence; explicit opt-in must not be counted as original-profile
     * acceptance, and no feedback callback is invoked to manufacture applicability. */
    @Test
    fun originalRejectedPointerBusyPopupConsumesOwnedAnimationFramesWithoutAdvancingPreparation() {
        org.junit.Assume.assumeTrue(
            "C408.BusyAnimation.Quarantined.PopupAndDemandCausalityUnqualified",
            System.getProperty("navic.reader.hostTest.profile") == "prospective-busy-animation")
        val fixture = NormalRootFixture(provideInitialIngress = true,
            synchronizationProfile = SynchronizationProfile.ProspectiveBusyAnimation)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            fixture.refresh()
            fixture.assertNoBusyPopupOrFrameDemand()
            val root = assertNotNull(fixture.nativeRoot())
            val installed = assertNotNull(fixture.store().snapshot)
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            val engine = assertNotNull(fixture.engine())
            val work = fixture.originalInstalledWork()
            val lease = work.lease
            val source = assertNotNull(fixture.viewerField("passiveRasterPreparationAdapter")
                as? ReaderPassiveRasterPreparationAdapter)
            assertFalse(source.isAvailable, "C408.BusyAnimation.OriginalPassiveSourceStillUnready")
            val originalCommands = fixture.engineCommands().pendingCommands.map { it.dispatch.id }
            val before = runtime.snapshot()
            fixture.assertOriginalWindowOwnsEveryPlacement {
                fixture.dispatchOriginalRootPointer(100L)
                fixture.refreshWithBusyAnimationDemandWitness()
            }
            assertSame(root, fixture.nativeRoot(), "C408.BusyAnimation.OriginalRootRetained")
            assertSame(installed, fixture.store().snapshot, "C408.BusyAnimation.OriginalInstallationRetained")
            assertSame(runtime, fixture.runtime(), "C408.BusyAnimation.OriginalCoordinatorRetained")
            assertSame(material, fixture.material(), "C408.BusyAnimation.OriginalMaterialRetained")
            assertSame(engine, fixture.engine(), "C408.BusyAnimation.OriginalEngineRetained")
            assertSame(work, fixture.originalInstalledWork(), "C408.BusyAnimation.OriginalPreparationRetained")
            assertSame(lease, fixture.originalInstalledWork().lease, "C408.BusyAnimation.OriginalLeaseRetained")
            assertSame(source, fixture.viewerField("passiveRasterPreparationAdapter"),
                "C408.BusyAnimation.OriginalSourceRetained")
            assertTrue(work.isCurrent(), "C408.BusyAnimation.OriginalPreparationCurrent")
            assertFalse(source.isAvailable, "C408.BusyAnimation.AnimationCannotSupplyPassiveReady")
            assertTrue(material.owned && material.wrapper.parent != null,
                "C408.BusyAnimation.OriginalMaterialStillOwnedAndAttached")
            assertTrue(before == runtime.snapshot(), "C408.BusyAnimation.NoCoordinatorProgressFromAnimation")
            assertTrue(originalCommands == fixture.engineCommands().pendingCommands.map { it.dispatch.id },
                "C408.BusyAnimation.NoLegacyNavigationFromRejectedPointer")
            assertEquals(1, fixture.initialIngressCount, "C408.BusyAnimation.NoInitialSemanticReplay")
            assertEquals(0, fixture.semanticIngressCount, "C408.BusyAnimation.NoUntaggedSemanticReplay")
            assertFalse(fixture.canAcceptNewPointer(), "C408.BusyAnimation.NoInputOrDisplayAuthority")
        } finally { fixture.close() }
    }

    @Test
    fun originalRootCloseBeforeItsDelayedAllocationPlanFencesTheActualEngineCallback() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan(deferPlan = true)
            val runtime = assertNotNull(fixture.runtime(), "C408.RootClose.OriginalCoordinator")
            val material = assertNotNull(fixture.material(), "C408.RootClose.OriginalMaterial")
            // Disposal removes the Compose hierarchy. Observe the same original owners
            // afterward, not a fresh hierarchy lookup or replacement authority.
            val root = assertNotNull(fixture.nativeRoot(), "C408.RootClose.OriginalRoot")
            val executor = fixture.executor()
            val requests = fixture.requests()
            assertEquals(1, fixture.initialIngressCount, "C408.RootClose.GenuineInitialIngress")
            fixture.closeComposition()
            val beforeLatePlan = runtime.snapshot()
            fixture.deliverDeferredInitialPlan()
            assertEquals(beforeLatePlan, runtime.snapshot(), "C408.RootClose.LatePlanCannotAdvanceClosedCoordinator")
            assertTrue(runtime.snapshot().releaseOnlySink, "C408.RootClose.PermanentFence")
            assertEquals(0, executor.activeSlotCount, "C408.RootClose.OriginalExecutionRetired")
            assertEquals(0, requests.activeHandleCount, "C408.RootClose.OriginalRegistryRetired")
            assertFalse(material.owned, "C408.RootClose.OriginalMaterialReturned")
            assertNull(material.wrapper.parent, "C408.RootClose.OriginalMaterialDetached")
            assertEquals(1, fixture.initialIngressCount, "C408.RootClose.NoSemanticReplay")
            assertFalse(fixture.canAcceptNewPointer(root), "C408.RootClose.NoImmediateNextAction")
        } finally { fixture.close() }
    }

    @Test
    fun lateInitialIngressReservesOneOriginalPreparationBeforeItsRuntimeReadyEdge() {
        val fixture = NormalRootFixture(provideInitialIngress = true, startWithInitialIngress = false)
        try {
            fixture.mount()
            val root = fixture.nativeRoot()
            fixture.deliverInitialIngress()
            fixture.returnOriginalInitialLayoutAndPlan()
            val work = fixture.originalInstalledWork()
            repeat(3) { fixture.refresh() }
            assertSame(root, fixture.nativeRoot())
            assertSame(work, fixture.originalInstalledWork())
            assertTrue(work.isCurrent())
            assertEquals(1, fixture.store().atomicWriteCount)
            assertEquals(1, fixture.initialIngressCount)
            assertEquals(1, fixture.constructionObservations.size)
            assertEquals(1, assertNotNull(fixture.runtime()).snapshot().activeTransitionsRegistered)
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    /** Execution-unqualified prerequisite localization. Preserve the four original
     * InitialPlan tests and their qualified AfterOpeningAckCount boundary unchanged.
     * These assertions use only the exact original query/installation custody and
     * source-static finite labels; no failure payload or closure/object dump is read. */
    @Test
    fun originalBootstrapQueryRetainsAdmissionAcrossPlacementReadyAndOpeningAck() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            fixture.witnessOriginalOpeningTransport()
            assertEquals(0, fixture.initialIngressCount,
                "A407.OpeningTransport.ReadyAndAckCannotProduceSemanticReceipt")
            assertFalse(fixture.canAcceptNewPointer(),
                "A407.OpeningTransport.TransportCannotPublishInput")
        } finally { fixture.close() }
    }

    /** Read-only geometry control: late bootstrap and refresh must use the content
     * parent's actual allocation, including intermediate placements before looper drain.
     * A final settled-size check alone misses the old forced-viewport/traversal overwrite. */
    @Test
    fun lateBootstrapNeverPlacesTheOuterCompositionOutsideItsOriginalWindowAllocation() {
        val fixture = NormalRootFixture(provideInitialIngress = true, startWithInitialIngress = false)
        try {
            fixture.mount()
            val root = fixture.nativeRoot()
            val epoch = assertNotNull(fixture.gateway.captureSelectedSemanticEpoch())
            assertNull(fixture.store().snapshot)
            fixture.assertOriginalWindowOwnsEveryPlacement {
                fixture.deliverInitialIngress()
                fixture.refresh()
            }
            assertSame(root, fixture.nativeRoot())
            assertSame(epoch, fixture.gateway.captureActivatedSemanticEpoch())
            assertEquals(1, fixture.store().atomicWriteCount)
            assertEquals(1, fixture.constructionObservations.size)
            fixture.witnessOriginalOpeningTransport()
            assertEquals(0, fixture.initialIngressCount,
                "A407.WindowGeometry.TransportIsNotSemanticReadiness")
            assertFalse(fixture.canAcceptNewPointer(),
                "A407.WindowGeometry.PlacementIsNotDisplayAuthority")
        } finally { fixture.close() }
    }

    @Test
    fun originalEngineRetainsTheSoleBundleDestructionConsumerAcrossRefresh() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val engine = assertNotNull(fixture.engine()) as ReaderEngineWebView
            val source = engine as ReaderEnginePhysicalDestructionSource
            val original = assertNotNull(fixture.destructionConsumer(engine),
                "Actual Native attachment must bind the original Bundle destruction consumer")
            assertTrue(source.bindPhysicalDestruction(original), "Rebinding the same consumer is idempotent")
            assertFalse(source.bindPhysicalDestruction { }, "No second observer may replace original custody")
            fixture.refresh()
            assertSame(engine, fixture.engine())
            assertSame(original, fixture.destructionConsumer(engine))
            fixture.closeComposition()
            assertNull(fixture.destructionConsumer(engine),
                "Only actual Engine.destroy may consume the exact destruction callback")
        } finally { fixture.close() }
    }

    @Test
    fun wrongSessionInitialIngressCannotStartOrReplaceTheRetainedCandidate() {
        val fixture = NormalRootFixture(provideInitialIngress = true, startWithInitialIngress = false)
        try {
            fixture.mount()
            val root = fixture.nativeRoot()
            val epoch = assertNotNull(fixture.gateway.captureSelectedSemanticEpoch())
            fixture.deliverWrongSessionInitialIngress()
            assertSame(root, fixture.nativeRoot())
            assertSame(epoch, fixture.gateway.captureSelectedSemanticEpoch())
            assertNull(fixture.store().snapshot)
            assertNull(fixture.runtime())
            assertNull(fixture.material())
            assertTrue(fixture.constructionObservations.isEmpty())
            assertEquals(0, fixture.initialIngressCount)
            fixture.deliverInitialIngress()
            assertEquals(1, fixture.store().atomicWriteCount)
            assertEquals(1, fixture.constructionObservations.size)
        } finally { fixture.close() }
    }

    /** Original port wiring controls only; no frame request, target, release ACK or
     * installation is seeded. MAIN must execution-qualify these new controls. */
    @Test
    fun originalResourceCancellationCannotAckTheUnfinalizedPublisher() {
        val fixture = NormalRootFixture()
        try {
            fixture.mount()
            val candidate = fixture.selectedCandidate()
            val id = ReaderTransitionId(candidate.readerSessionGeneration, candidate.coordinatorEpoch,
                1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            val result = fixture.originalResourcePort().cancelOwnedWork(ReaderTransitionCommand.CancelOwnedWork(id))
            assertTrue(result is ReaderPortCommandResult.Rejected &&
                result.reason == ReaderTransitionFailureReason.InvalidLegacyResource,
                "A407.Cancellation.UnfinalizedPublisherRejected")
            assertSame(candidate, fixture.selectedCandidate())
            assertNull(fixture.store().snapshot)
            assertNull(fixture.runtime())
            assertNull(fixture.material())
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    @Test
    fun originalResourceCancellationRetiresOnlyItsCommandAndNotItsMaterial() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            val installed = assertNotNull(fixture.store().snapshot)
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            val engine = assertNotNull(fixture.engine())
            val journal = runtime.javaClass.getDeclaredField("journal").apply { isAccessible = true }
                .get(runtime) as ReaderTransitionJournal
            val id = assertNotNull(journal.active, "A407.Cancellation.OriginalCommandPresent").id
            assertEquals(1L, id.sequence, "A407.Cancellation.OriginalBootstrapSequence")
            assertNull(id.parent, "A407.Cancellation.OriginalBootstrapHasNoParent")
            val resources = fixture.originalResourcePort()
            assertEquals(1, fixture.executor().activeSlotCount)
            // This is a foreign command control, not admission of another transition.
            // A valid successor identity still requires the actual immediate parent.
            val foreignCommand = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
            assertEquals(ReaderPortCommandResult.Accepted,
                resources.cancelOwnedWork(ReaderTransitionCommand.CancelOwnedWork(foreignCommand)),
                "A407.Cancellation.ForeignCommandAcceptedWithoutPhysicalClaim")
            assertEquals(1, fixture.executor().activeSlotCount,
                "A407.Cancellation.ForeignCommandRetainsOriginalSlot")
            assertEquals(ReaderPortCommandResult.Accepted,
                resources.cancelOwnedWork(ReaderTransitionCommand.CancelOwnedWork(id)),
                "A407.Cancellation.OriginalCommandAccepted")
            assertEquals(0, fixture.executor().activeSlotCount)
            assertEquals(0, fixture.requests().activeHandleCount)
            assertSame(installed, fixture.store().snapshot)
            assertSame(runtime, fixture.runtime())
            assertSame(material, fixture.material())
            assertSame(engine, fixture.engine())
            assertTrue(material.owned && material.wrapper.parent != null,
                "A407.Cancellation.LogicalRetirementRetainsPhysicalMaterial")
            assertEquals(0, fixture.initialIngressCount)
            assertFalse(fixture.canAcceptNewPointer())
        } finally { fixture.close() }
    }

    @Test
    fun closeDuringBootstrapInitializationRetiresExactMaterial() {
        val fixture = NormalRootFixture(provideInitialIngress = true)
        try {
            fixture.mount()
            assertNotNull(fixture.store().snapshot,
                "Close-during-initialization coverage requires actual normal production installation")
            val runtime = assertNotNull(fixture.runtime())
            val material = assertNotNull(fixture.material())
            assertNotNull(fixture.engine())
            val executor = fixture.executor()
            val requests = fixture.requests()
            fixture.closeComposition()
            assertNull(fixture.gateway.captureSelectedSemanticEpoch())
            assertFalse(fixture.gateway.admitsNormalSession(fixture.opened.controller.state.readerSessionGeneration))
            assertEquals(0, executor.activeSlotCount)
            assertEquals(0, requests.activeHandleCount)
            assertFalse(material.owned, "Only the exact original material's physical return retires its custody")
            assertNull(material.wrapper.parent)
            assertTrue(runtime.snapshot().releaseOnlySink, "Root close must permanently fence installed commands")
            assertEquals(0, fixture.initialIngressCount)
            assertEquals(1, fixture.constructionObservations.size,
                "Queued callbacks after close cannot construct a replacement bootstrap")
        } finally { fixture.close() }
    }

    /** New profile, not a rewrite of the original frozen-controller startup evidence.
     * This reaches the initial owning model and recomposes THAT controller back into the
     * same Native root. The original plan/currency and physical owners remain mandatory. */
    @Test
    fun originalRootReactiveOwningIngressRetainsInitialOnlyLayoutAndPreparation() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            val root = assertNotNull(fixture.nativeRoot(), "E407.OwningRoot.OriginalRoot")
            val installed = assertNotNull(fixture.store().snapshot, "E407.OwningRoot.OriginalInstallation")
            val runtime = assertNotNull(fixture.runtime(), "E407.OwningRoot.OriginalCoordinator")
            val material = assertNotNull(fixture.material(), "E407.OwningRoot.OriginalMaterial")
            val engine = assertNotNull(fixture.engine(), "E407.OwningRoot.OriginalEngine")
            fixture.returnOriginalInitialLayoutAndPlan()
            val work = fixture.originalInstalledWork()
            fixture.assertOriginalInitialOnlyPlan(work)
            assertTrue(fixture.owningController !== fixture.opened.controller,
                "E407.OwningRoot.InitialIngressUpdatesActualComposedOwner")
            assertTrue(fixture.owningController.state.presentation.binding?.destinationCommitIdentity ==
                work.lease.binding.destinationCommitIdentity, "E407.OwningRoot.InitialSemanticBinding")
            fixture.refresh()
            assertTrue(root === fixture.nativeRoot(), "E407.OwningRoot.ReactiveRefreshKeepsOriginalRoot")
            assertTrue(installed === fixture.store().snapshot, "E407.OwningRoot.ReactiveRefreshKeepsInstallation")
            assertTrue(runtime === fixture.runtime(), "E407.OwningRoot.ReactiveRefreshKeepsCoordinator")
            assertTrue(material === fixture.material(), "E407.OwningRoot.ReactiveRefreshKeepsMaterial")
            assertTrue(engine === fixture.engine(), "E407.OwningRoot.ReactiveRefreshKeepsEngine")
            assertTrue(work === fixture.originalInstalledWork(), "E407.OwningRoot.ReactiveRefreshKeepsInvocation")
            assertTrue(work.isCurrent(), "E407.OwningRoot.ReactiveProjectionCannotRetireBootstrapPreparation")
            assertEquals(1, fixture.initialIngressCount, "E407.OwningRoot.InitialOnlyIngress")
            assertEquals(0, fixture.semanticIngressCount, "E407.OwningRoot.NoLegacySemanticReplay")
        } finally { fixture.close() }
    }

    @Test
    fun originalReactiveRootCloseFencesItsDelayedOriginalPlanWithoutLegacyReplay() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan(deferPlan = true)
            val root = assertNotNull(fixture.nativeRoot(), "E407.OwningRootClose.OriginalRoot")
            val runtime = assertNotNull(fixture.runtime(), "E407.OwningRootClose.OriginalCoordinator")
            val material = assertNotNull(fixture.material(), "E407.OwningRootClose.OriginalMaterial")
            val executor = fixture.executor()
            val requests = fixture.requests()
            val owningController = fixture.owningController
            fixture.closeComposition()
            val closed = runtime.snapshot()
            fixture.deliverDeferredInitialPlan()
            assertTrue(closed == runtime.snapshot() && closed.releaseOnlySink,
                "E407.OwningRootClose.OriginalPlanCannotAdvanceClosedCoordinator")
            assertTrue(owningController === fixture.owningController,
                "E407.OwningRootClose.NoObsoleteOwningModelWrite")
            assertEquals(0, executor.activeSlotCount, "E407.OwningRootClose.OriginalExecutionReturned")
            assertEquals(0, requests.activeHandleCount, "E407.OwningRootClose.OriginalRequestsReturned")
            assertTrue(!material.owned && material.wrapper.parent == null,
                "E407.OwningRootClose.OriginalPhysicalMaterialReturned")
            assertFalse(fixture.canAcceptNewPointer(root), "E407.OwningRootClose.NoInputAfterClose")
            assertEquals(1, fixture.initialIngressCount, "E407.OwningRootClose.NoInitialReplay")
            assertEquals(0, fixture.semanticIngressCount, "E407.OwningRootClose.NoLegacyReplay")
            assertTrue(fixture.lastInstalledTurnReceipt == null, "E407.OwningRootClose.NoInventedTurnReceipt")
        } finally { fixture.close() }
    }

    /** Reachable ORIGINAL allocation consumers only. Public plan responses are delivered to
     * the allocation's actual Engine callback. No passive Ready, captured raster, prepared
     * deck, publication or successor gesture is injected. These are not full Root turn proof. */
    @Test
    fun originalNativeAllocationRejectsInconsistentPlanBeforeOriginalCounters() {
        for (mismatch in 0..4) {
            val fixture = NormalRootFixture(provideInitialIngress = true,
                owningProfile = OwningProfile.ReactiveOwningCoordinator)
            try {
                fixture.mount()
                val probe = fixture.originalAllocationProbe()
                val context = probe.plan.getValue("context") as JsonObject
                val layout = probe.plan.getValue("resolvedLayout") as JsonObject
                val invalid = when (mismatch) {
                    0 -> JsonObject(probe.plan + ("resolvedLayout" to
                        JsonObject(layout + ("layoutGeneration" to JsonPrimitive(8L)))))
                    1 -> JsonObject(probe.plan + ("context" to
                        JsonObject(context + ("currentChapterIndex" to JsonPrimitive(1)))))
                    2 -> JsonObject(probe.plan + ("context" to
                        JsonObject(context + ("currentChapterPageCount" to JsonPrimitive(7)))))
                    3 -> JsonObject(probe.plan + ("context" to
                        JsonObject(context + ("centerPageIndex" to JsonPrimitive(1)))))
                    else -> JsonObject(probe.plan + ("context" to
                        JsonObject(context + ("currentChapterPageStartIndex" to JsonPrimitive(1)))))
                }
                val counters = fixture.originalAllocationCounters()
                fixture.deliverOriginalAllocationPlan(probe, invalid)
                assertTrue(counters == fixture.originalAllocationCounters(),
                    "A407.Allocation.InconsistentPlanBeforeOriginalCounters")
                assertTrue(fixture.viewerField("pendingInstalledRaster") == null,
                    "A407.Allocation.InconsistentPlanCannotCreateInvocation")
                assertFalse(fixture.canAcceptNewPointer(), "A407.Allocation.RejectionCannotPublishInput")
            } finally { fixture.close() }
        }
    }

    @Test
    fun originalNativeAllocationRejectsIncompleteChapterPlanBeforeOriginalCounters() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            val probe = fixture.originalAllocationProbe()
            val targets = probe.plan.getValue("targets") as JsonArray
            val incomplete = JsonObject(probe.plan + ("targets" to JsonArray(targets.dropLast(1))))
            val counters = fixture.originalAllocationCounters()
            fixture.deliverOriginalAllocationPlan(probe, incomplete)
            assertTrue(counters == fixture.originalAllocationCounters(),
                "A407.Allocation.CompleteChapterRequiredBeforeOriginalCounters")
            assertTrue(fixture.viewerField("pendingInstalledRaster") == null,
                "A407.Allocation.IncompletePlanCannotCreateInvocation")
        } finally { fixture.close() }
    }

    @Test
    fun originalNativeAllocationConsumesItsActualPlanCallbackOnlyOnce() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            val probe = fixture.originalAllocationProbe()
            fixture.deliverOriginalAllocationPlan(probe, probe.plan)
            val work = fixture.originalInstalledWork()
            val counters = fixture.originalAllocationCounters()
            val before = assertNotNull(fixture.runtime(), "A407.Allocation.OriginalCoordinator").snapshot()
            fixture.deliverOriginalAllocationPlan(probe, probe.plan)
            assertTrue(work === fixture.originalInstalledWork() && work.isCurrent(),
                "A407.Allocation.DuplicateCallbackKeepsExactInvocation")
            assertTrue(counters == fixture.originalAllocationCounters(),
                "A407.Allocation.DuplicateCallbackCannotReserveCounters")
            assertTrue(before == fixture.runtime()?.snapshot(),
                "A407.Allocation.DuplicateCallbackCannotReplayFacts")
            assertFalse(fixture.canAcceptNewPointer(), "A407.Allocation.PlanIsNotDisplayAuthority")
        } finally { fixture.close() }
    }

    @Test
    fun originalNativeCanceledAllocationCannotConsumeItsDelayedPlan() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            val probe = fixture.originalAllocationProbe()
            val runtime = assertNotNull(fixture.runtime(), "A407.AllocationCancel.OriginalCoordinator")
            runtime.enqueue(ReaderTransitionFact.Intent(probe.command.transitionId, ReaderCancelIntent))
            val counters = fixture.originalAllocationCounters()
            val before = runtime.snapshot()
            fixture.deliverOriginalAllocationPlan(probe, probe.plan)
            fixture.deliverOriginalAllocationPlan(probe, probe.plan)
            assertTrue(counters == fixture.originalAllocationCounters(),
                "A407.AllocationCancel.AdmissionFencedBeforeCounterReservation")
            assertTrue(before == runtime.snapshot(), "A407.AllocationCancel.DelayedCallbackCannotReplayFacts")
            assertTrue(fixture.viewerField("pendingInstalledRaster") == null,
                "A407.AllocationCancel.NoLatePhysicalInvocation")
            assertFalse(fixture.canAcceptNewPointer(), "A407.AllocationCancel.NoSyntheticTerminalInput")
        } finally { fixture.close() }
    }

    @Test
    fun originalNativeAllocationRejectsRetiredEngineLayoutCurrencyBeforeCounters() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            val probe = fixture.originalAllocationProbe()
            val resolved = assertNotNull(fixture.material()?.initialResolvedLayout,
                "A407.AllocationCurrency.OriginalResolvedLayout")
            assertTrue(resolved.sourceIsCurrent(), "A407.AllocationCurrency.OriginalQueryCurrencyInitiallyCurrent")
            val engine = assertNotNull(fixture.engine(), "A407.AllocationCurrency.OriginalEngine")
                as ReaderEngineCurrentDestinationQuerySource
            engine.closeCurrentDestinationQueries() // Original retirement fences the source before external work.
            assertFalse(resolved.sourceIsCurrent(), "A407.AllocationCurrency.OriginalEngineRetirementInvalidatesCurrency")
            val counters = fixture.originalAllocationCounters()
            val before = assertNotNull(fixture.runtime(), "A407.AllocationCurrency.OriginalCoordinator").snapshot()
            fixture.deliverOriginalAllocationPlan(probe, probe.plan)
            assertTrue(counters == fixture.originalAllocationCounters(),
                "A407.AllocationCurrency.StaleLayoutCannotReserveOriginalCounters")
            assertTrue(before == fixture.runtime()?.snapshot(), "A407.AllocationCurrency.StaleCallbackCannotReplayFacts")
            assertTrue(fixture.viewerField("pendingInstalledRaster") == null,
                "A407.AllocationCurrency.StaleCallbackCannotCreateInvocation")
        } finally { fixture.close() }
    }

    @Test
    fun originalNativeDeckExactRasterKeyReachesItsUnreadiedOriginalConsumer() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            val work = fixture.originalInstalledWork()
            val command = ReaderTransitionCommand.ReserveDeck(work.lease.transitionId, work.lease.binding,
                ReaderTransitionDeckRole.Initial, work.lease.allocation)
            var facts = 0
            var declarations = 0
            val result = fixture.reserveOriginalNativeDeck(command, { facts++ }) { _, _ ->
                declarations++
                ReaderPortCommandResult.Accepted
            }
            assertTrue(result is ReaderPortCommandResult.Rejected && result.reason == ReaderTransitionFailureReason.PortRejected,
                "A407.KeyedDeck.ExactKeyReachesOriginalUnreadiedCurlNotNativeKeyRejection")
            assertTrue(facts == 0 && declarations == 0, "A407.KeyedDeck.UnreadyOriginalCannotRegisterDeckOrTarget")
            assertTrue(work === fixture.originalInstalledWork() && work.isCurrent(),
                "A407.KeyedDeck.UnreadyConsumerRetainsOriginalPreparation")
        } finally { fixture.close() }
    }

    /** Key is derived from the command, not the latest invocation. The original Curl
     * producer is deliberately unready: its PortRejected is not Native keyed admission.
     * Require Native's own StaleProof boundary, before that producer can be consulted. */
    @Test
    fun originalNativeDeckRejectsForeignRasterKeyBeforeUnreadiedCurlConsumer() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            val work = fixture.originalInstalledWork()
            val allocation = assertNotNull(work.lease.allocation, "A407.KeyedDeck.OriginalAllocation")
            val binding = work.lease.binding.copy(rasterGeneration = allocation.rasterGeneration + 1L)
            val foreign = allocation.copy(allocatedBinding = binding, rasterGeneration = allocation.rasterGeneration + 1L)
            val command = ReaderTransitionCommand.ReserveDeck(work.lease.transitionId, binding,
                ReaderTransitionDeckRole.Initial, foreign)
            var facts = 0
            var declarations = 0
            val result = fixture.reserveOriginalNativeDeck(command, { facts++ }) { _, _ ->
                declarations++
                ReaderPortCommandResult.Accepted
            }
            assertTrue(result is ReaderPortCommandResult.Rejected && result.reason == ReaderTransitionFailureReason.StaleProof,
                "A407.KeyedDeck.CommandRasterKeyRejectedByOriginalNativeBeforeCurl")
            assertTrue(facts == 0 && declarations == 0, "A407.KeyedDeck.ForeignKeyCannotRegisterDeckOrTarget")
            assertTrue(work === fixture.originalInstalledWork() && work.isCurrent(),
                "A407.KeyedDeck.ForeignKeyRetainsExactPhysicalPreparation")
        } finally { fixture.close() }
    }

    @Test
    fun originalNativeRasterRetirementKeepsExactKeyUntilOriginalPhysicalReturn() {
        val fixture = NormalRootFixture(provideInitialIngress = true,
            owningProfile = OwningProfile.ReactiveOwningCoordinator)
        try {
            fixture.mount()
            fixture.returnOriginalInitialLayoutAndPlan()
            val work = fixture.originalInstalledWork()
            val owner = fixture.viewerField("pageRasterPreparationController") as ReaderPageRasterPreparationController
            val material = assertNotNull(fixture.material(), "A407.InvocationCustody.OriginalMaterial")
            val resources = fixture.originalResourcePort()
            val canceled = resources.cancelOwnedWork(ReaderTransitionCommand.CancelOwnedWork(work.lease.transitionId))
            assertTrue(canceled == ReaderPortCommandResult.Accepted, "A407.InvocationCustody.OriginalCancellationAccepted")
            assertFalse(work.isCurrent(), "A407.InvocationCustody.LogicalInvocationRetired")
            assertTrue(owner.installedWorkFor(work.lease.resourceKey) === work,
                "A407.InvocationCustody.ExactKeyRetainsPhysicalReturnCustody")
            assertTrue(owner.installedWorkFor(work.lease.resourceKey.copy(opaqueId = work.lease.rasterGeneration + 1L)) == null,
                "A407.InvocationCustody.ForeignKeyCannotBorrowLatestInvocation")
            assertTrue(material === fixture.material() && material.owned && material.wrapper.parent != null,
                "A407.InvocationCustody.LogicalCancelIsNotPhysicalMaterialReturn")
            // Prospective RED: Native's original single pending slot is an invocation
            // admission blocker, NOT the keyed controller's physical-return custody.
            // A successor cannot be invoked while this old slot still occupies it.
            // No successor allocation, Ready, owner-map write or physical ACK is seeded.
            assertTrue(fixture.viewerField("pendingInstalledRaster") == null,
                "A407.InvocationCustody.LogicalCancelDetachesPendingInvocationBeforeSuccessor")
            // Disposal invokes original physical release. Do not clear an owner map or the
            // pending field to mimic that return, and do not qualify a readable predecessor.
            fixture.closeComposition()
            assertTrue(owner.installedWorkFor(work.lease.resourceKey) == null,
                "A407.InvocationCustody.OriginalPhysicalReturnForgetsExactKey")
        } finally { fixture.close() }
    }

    /** SOURCE wiring regression only, paired with the original controlled Curl/barrier
     * consumer test. A structural copy is not the committed barrier object. Reading that
     * object may validate/carry the accepted command/result, never authenticate a sample.
     * These source assertions are unexecuted; they are NOT positive Root acceptance. */
    @Test
    fun originalNativeAcceptedWiringMustCarryBarrierSnapshotObjectInsteadOfConstructedCopy() {
        val body = originalNativeSourceBody("fun refreshInstalledPresentationConsumers(",
            "private fun refreshInstalledWhispersyncConsumer(")
        assertFalse(body.contains("ReaderOwnerAndInputPublicationSnapshot("),
            "A407.NativeIdentity.Source.ConstructedCopyCannotReachOriginalIdentityConsumer")
        val read = body.indexOf("readers()")
        val delivery = body.indexOf("onInstalledOwnerAndInputPublished(publication)")
        assertTrue(read >= 0 && delivery > read &&
            body.substring(read, delivery).contains("applied.publishedOwner") &&
            body.substring(read, delivery).contains("applied.publishedBinding") &&
            body.substring(read, delivery).contains("applied.finalPhysicalInputLease") &&
            body.substring(read, delivery).contains("resource"),
            "A407.NativeIdentity.Source.OriginalSnapshotCarriedOnlyAfterAcceptedContextValidation")
        assertTrue(body.contains("CommitOwnerAndInputLease") && body.contains("PublishRetainedOwnerAndInputLease") &&
            body.contains("refreshInstalledWhispersyncConsumer()"),
            "A407.NativeIdentity.Source.OriginalAcceptedContextAndIndependentDecorationRemain")
    }

    /** SOURCE ordering contracts, not passive Ready/failure execution. Native's admission
     * slot must detach before either original emitter forwards terminal facts reentrantly;
     * the controller's keyed custody remains for actual return/deck use. No Ready is seeded. */
    @Test
    fun originalNativeTerminalForwardingSourceMustDetachInvocationBeforeReadyAndFailure() {
        val body = originalNativeSourceBody("fun prepareInstalledRaster(", "fun reserveInstalledDeck(")
        for (name in listOf("onReady", "onFailed")) {
            val callback = Regex("$name\\s*=\\s*\\{([\\s\\S]*?)invocation\\.callbacks\\.")
                .find(body)?.groupValues?.get(1)
            assertTrue(callback != null && callback.contains("pendingInstalledRaster === invocation") &&
                callback.contains("pendingInstalledRaster = null"),
                "A407.InvocationCustody.Source.DetachBeforeOriginalTerminalForwarding")
            assertFalse(callback.orEmpty().contains("releaseInstalled(") || callback.orEmpty().contains("forgetReturnedInstalled("),
                "A407.InvocationCustody.Source.TerminalDetachCannotFabricatePhysicalReturn")
        }
        val lostRenderer = originalNativeSourceBody("}, onRendererGone = {", "val session = ReaderPassiveRasterPrototypeSession")
        val capture = lostRenderer.indexOf("val invocation = pendingInstalledRaster")
        val detach = lostRenderer.indexOf("pendingInstalledRaster = null")
        val close = lostRenderer.indexOf("closePassiveRasterPreparationAdapter()")
        val forward = lostRenderer.indexOf("invocation.callbacks.onFailed")
        assertTrue(capture >= 0 && detach > capture && close > detach && forward > detach &&
            lostRenderer.contains("pendingInstalledRaster === invocation"),
            "A407.InvocationCustody.Source.RendererLossDetachesBeforeCloseAndOriginalFailureForwarding")
    }

    /** SOURCE cancellation/reentry guard supplements, never replaces, the existing
     * qualified LogicalCancelDetachesPendingInvocationBeforeSuccessor RED. Capture and
     * detach the exact old slot before publisher/executor/query outward calls. Cancellation
     * still cannot release the keyed physical work or substitute a new readable owner. */
    @Test
    fun originalNativeCancellationSourceMustDetachExactInvocationBeforeOutwardReentry() {
        val body = originalNativeSourceBody("override fun cancelOwnedWork(command: ReaderTransitionCommand.CancelOwnedWork)",
            "private fun installedRejected()")
        val capture = body.indexOf("pendingInstalledRaster?.takeIf { it.work.lease.transitionId == command.transitionId }")
        val detach = body.indexOf("pendingInstalledRaster = null")
        val retire = body.indexOf("pending?.work?.retire()")
        val publisher = body.indexOf("nativePagePresentationPublisher.cancelOwnedWork")
        val executor = body.indexOf("executor.retireTransition")
        val query = body.indexOf("closeCurrentDestinationQueries()")
        assertTrue(capture >= 0 && detach > capture && retire > detach &&
            publisher > retire && executor > retire && query > retire &&
            (body.contains("pendingInstalledRaster === pending") || body.contains("if (pending != null)")),
            "A407.InvocationCustody.Source.ExactDetachAndLogicalRetirementBeforeCancelOutwardReentry")
        assertFalse(body.contains("releaseInstalled(") || body.contains("forgetReturnedInstalled(") ||
            body.contains("onInstalledOwnerAndInputPublished(") || body.contains("ResourceReleased("),
            "A407.InvocationCustody.Source.CancelPreservesOriginalKeyedPhysicalAndReadableCustody")
    }

    /** SOURCE-only admission-cycle guard. The original barrier narrows before it writes
     * its candidate: neither initial Native nor a differently-bound successor can depend
     * on an already-committed matching publication. This supplies no Root input authority;
     * retained gesture custody must still check the actual predecessor/source. */
    @Test
    fun originalNativeInputNarrowingCannotRequireItsOwnCommittedSuccessorSnapshot() {
        val body = originalNativeSourceBody("fun narrowInstalledInputLease(",
            "fun canPublishInstalledOwnerAndInput(")
        val nativeStart = body.indexOf("is ReaderTransitionInputLease.NativePage ->")
        val retainedStart = body.indexOf("is ReaderTransitionInputLease.ClaimedGesture ->")
        val retainedEnd = body.indexOf("ReaderTransitionInputLease.None, ReaderTransitionInputLease.ChromeOnly ->",
            retainedStart.coerceAtLeast(0))
        assertTrue(nativeStart >= 0 && retainedStart > nativeStart && retainedEnd > retainedStart,
            "A407.Input.Source.OriginalNativeAndRetainedBranchesRequired")
        assertFalse(body.substring(0, retainedStart).contains("readInstalledPublication"),
            "A407.Input.Source.NativeCannotRequirePreexistingMatchingCommittedSnapshot")
        val retained = body.substring(retainedStart, retainedEnd)
        assertTrue(retained.contains("readInstalledPublication?.invoke()") &&
            retained.contains("expected?.predecessor != publication.binding") &&
            retained.contains("publication.resource.retirementOrder.readerSessionGeneration") &&
            retained.contains("publication.resource.retirementOrder.coordinatorEpoch") &&
            retained.contains("pageInputSettlementHostController.narrowOrVeto(lease)"),
            "A407.Input.Source.RetainedClaimStillRequiresOriginalPublicationAndSourceChecks")
    }

    // Established public-source-reading convention. Only the exact original Native file
    // is read; neither source text nor runtime/private fixture values are failure output.
    private fun originalNativeSourceBody(start: String, end: String): String {
        val root = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { java.io.File(it, "composeApp/src").isDirectory }
        assertNotNull(root, "A407.SourcePrerequisite.OriginalWorkspaceMissing")
        val source = java.io.File(root,
            "composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt").readText()
        val from = source.indexOf(start)
        val to = if (from >= 0) source.indexOf(end, from + start.length) else -1
        assertTrue(from >= 0 && to > from, "A407.SourcePrerequisite.OriginalNativeSeamMissing")
        return source.substring(from, to)
    }

    private data class ConstructionObservation(
        val snapshot: ReaderActivatedSessionSnapshot?,
        val runtime: ReaderResumableTransitionCoordinator?,
        val originalFinalizationReturned: Boolean
    )

    private enum class OwningProfile {
        OriginalFrozenOpenController,
        ReactiveOwningCoordinator
    }

    private enum class SynchronizationProfile {
        OriginalThreeFrame,
        ProspectiveBusyAnimation, // Historical r32 opt-in probe remains quarantined.
        ProspectiveAttachedBusyEntrance // New post-r36 source-backed qualification.
    }
    private enum class SettleEntry { Startup, InitialIngress, Refresh }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class NormalRootFixture(private val provideInitialIngress: Boolean = false,
        startWithInitialIngress: Boolean = provideInitialIngress,
        private val synchronizationProfile: SynchronizationProfile = SynchronizationProfile.OriginalThreeFrame,
        private val owningProfile: OwningProfile = OwningProfile.OriginalFrozenOpenController) {
        val gateway = ReaderTransitionGateway()
        val opened = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-startup", resourceHref = "fixture.epub"),
            url = "https://example.invalid/public-synthetic-startup.epub"
        ))
        // One owning common coordinator in either profile. The historical profile still
        // composes opened.controller; only the explicit new profile reads reactive ownership.
        private var model by mutableStateOf(ReaderCoordinator(controller = opened.controller))
        val owningController: ReaderController get() = model.controller
        var lastInstalledTurnReceipt: ReaderPresentationEventReceipt? = null
            private set
        private val installedTurnIngress: ReaderInstalledPageTurnModelIngress = { event, predecessor, origin ->
            val step = model.onInstalledPageTurnRelocation(event, predecessor, origin)
            model = step.coordinator
            lastInstalledTurnReceipt = step.presentationReceipt
            step.presentationReceipt
        }
        private val activity = if (!provideInitialIngress) {
            Robolectric.buildActivity(ComponentActivity::class.java).setup()
        } else {
            // Let the actual nested Compose/Engine attachment run; do not simulate its callbacks.
            val key = "robolectric.areWindowsMarkedVisible"
            val old = System.getProperty(key)
            System.setProperty(key, "true")
            try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
            finally { if (old == null) System.clearProperty(key) else System.setProperty(key, old) }
        }
        // Own the actual View composition's frame boundary. Robolectric looper idleness
        // alone does not await GlobalSnapshotManager's background apply notification or
        // require the lifecycle-created WindowRecomposer to consume a new state value.
        private val compositionScheduler = TestCoroutineScheduler()
        private val compositionFrameClock = BroadcastFrameClock()
        private val compositionScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(compositionScheduler) + compositionFrameClock)
        private val recomposer = Recomposer(compositionScope.coroutineContext)
        private val recompositionRunner = compositionScope.launch { recomposer.runRecomposeAndApplyChanges() }
        private var compositionFrame = 0L
        private val compose = ComposeView(activity.get()).apply {
            // Parent context owns this composition; the View-tree context owns the
            // actual command-created sibling/nested ComposeViews beneath this mount.
            compositionContext = recomposer
            setParentCompositionContext(recomposer)
        }
        private val revision = mutableStateOf(0)
        private val initialIngressAvailable = mutableStateOf(startWithInitialIngress)
        private var initialIngressObservedInComposition = false
        var semanticIngressCount = 0
            private set
        var initialIngressCount = 0
            private set
        val constructionObservations = mutableListOf<ConstructionObservation>()
        private val initialIngress: ReaderInitialSemanticReceiptIngress = { event, origin ->
            initialIngressCount++
            val step = model.reduceSemanticDestination(event, origin)
            model = step.coordinator
            step.presentationReceipt
        }
        private var compositionClosed = false
        private var deferredInitialPlan: (() -> Unit)? = null
        private var originalPointerWitness = "NoRootPointerDispatched"
        private var refreshEntryWitness = "NoRefreshEntered"
        var busyEntranceDemandedFrameCount = 0
            private set

        private fun physicalModeLabel(mode: ReaderPagePhysicalDispatchMode?): String = when (mode) {
            null -> "NoActiveViewerModeObserved"
            ReaderPagePhysicalDispatchMode.CueMap -> "CueMap"
            ReaderPagePhysicalDispatchMode.ChromeOnly -> "ChromeOnly"
            ReaderPagePhysicalDispatchMode.Denied -> "Denied"
            ReaderPagePhysicalDispatchMode.Legacy -> "Legacy"
            ReaderPagePhysicalDispatchMode.LegacyLive -> "LegacyLive"
            ReaderPagePhysicalDispatchMode.PlayLikeCurl -> "PlayLikeCurl"
            ReaderPagePhysicalDispatchMode.ShellCover -> "ShellCover"
            ReaderPagePhysicalDispatchMode.LiveEngine -> "LiveEngine"
        }

        fun dispatchOriginalRootPointer(downTime: Long) {
            val root = assertNotNull(nativeRoot(), "C408.Root.OriginalPointerTarget")
            assertTrue(root.width > 0 && root.height > 0, "C408.Root.OriginalPointerBounds")
            // Read only the original routing booleans/mode at call returns. A handled
            // Root event is NOT evidence of a terminal rejection or busy callback; a
            // null mode after DOWN may mean the viewer was never the touch target.
            val canvas = if (viewerField("pageTurnCanvasEnabled") == true) "CanvasActive" else "CanvasInactive"
            val preview = if (viewerField("verticalPageDragPreview") == true) "VerticalPreview" else "NoVerticalPreview"
            originalPointerWitness = "$canvas.$preview"
            val steps = listOf(android.view.MotionEvent.ACTION_DOWN to root.width * 0.85f,
                android.view.MotionEvent.ACTION_MOVE to root.width * 0.15f,
                android.view.MotionEvent.ACTION_UP to root.width * 0.15f)
            steps.forEachIndexed { index, (action, x) ->
                val event = android.view.MotionEvent.obtain(downTime, downTime + index + 1L,
                    action, x, root.height * 0.5f, 0)
                try {
                    val handled = root.dispatchTouchEvent(event)
                    val phase = when (index) { 0 -> "DownReturned"; 1 -> "MoveReturned"; else -> "UpReturned" }
                    val mode = physicalModeLabel(viewerField("physicalDispatchMode") as? ReaderPagePhysicalDispatchMode)
                    val result = if (handled) "Handled" else "Unhandled"
                    originalPointerWitness += ".$phase.$result.$mode"
                } finally { event.recycle() }
            }
            originalPointerWitness += "." + compositionBoundaryLabel()
        }

        fun assertOriginalPointerUsedPlayLikeCurl() {
            assertTrue(originalPointerWitness.startsWith("CanvasActive.NoVerticalPreview."),
                "C408.Root.OriginalCanvasRouting")
            assertTrue(originalPointerWitness.contains(".DownReturned.Handled.PlayLikeCurl.") &&
                originalPointerWitness.contains(".MoveReturned.Handled.PlayLikeCurl.") &&
                originalPointerWitness.contains(".UpReturned.Handled."),
                "C408.Root.OriginalDownMoveUpHandledByRealRoute")
        }

        fun deliverDeferredInitialPlan() {
            val delivery = assertNotNull(deferredInitialPlan, "C408.RootClose.OriginalPlanCallbackRetained")
            deferredInitialPlan = null
            delivery()
            shadowOf(Looper.getMainLooper()).idle()
        }

        fun mount() {
            activity.get().setContentView(compose)
            compose.setContent {
                revision.value
                val reactiveOwner = owningProfile == OwningProfile.ReactiveOwningCoordinator
                val controller = if (reactiveOwner) model.controller else opened.controller
                val state = controller.state
                val suppliedInitialIngress = if (initialIngressAvailable.value) initialIngress else null
                SideEffect { initialIngressObservedInComposition = suppliedInitialIngress === initialIngress }
                CompositionLocalProvider(
                    LocalReaderTransitionGateway provides gateway,
                    LocalReaderSemanticReceiptIngress provides { _, _ -> semanticIngressCount++; null },
                    LocalReaderInitialSemanticReceiptIngress provides suppliedInitialIngress,
                    LocalReaderInstalledPageTurnModelIngress provides if (reactiveOwner) installedTurnIngress else null
                ) {
                    KomikkuReaderNativeFrameHost(
                        navigator = KomikkuReaderNavigator(KomikkuDisabledNavigation()),
                        navigationOverlayVisible = false,
                        chromeOverlayVisible = false,
                        presentationDecision = state.presentationDecision,
                        presentationState = state.presentation,
                        presentationVersion = controller.presentationVersion,
                        presentationShellCoverVisible = false,
                        legacyLiveCompatibilityContext = ReaderLegacyLiveCompatibilityContext.Denied(),
                        presentationEffects = emptyList(),
                        onPresentationEffectHandled = {},
                        onPresentationEvent = { event ->
                            if (!reactiveOwner) null else gateway.dispatchBeforeLegacy(null, selectedResult = { null }) {
                                val step = model.onPresentationEvent(event)
                                model = step.coordinator
                                step.presentationReceipt
                            }
                        },
                        destinationCommitIdentity = if (reactiveOwner) state.destinationCommitIdentity else null,
                        shellCoverUrl = null,
                        shellCoverTitle = "Public synthetic startup",
                        coverBackdropEnabled = false,
                        viewerKey = ReaderViewerKey(ReaderViewerKind.WebViewPublication, "public-synthetic-startup"),
                        grayscaleEnabled = false,
                        invertedColors = false,
                        verticalPageDragPreview = false,
                        pageTurnCanvasEnabled = true,
                        pageTurnReadingDirection = "ltr",
                        pageTurnBitmapQuality = "balanced",
                        pageTurnSnapshotKey = 0,
                        pageTurnContentReadyKey = if (reactiveOwner) readerPageTurnContentReadyKey(state.paginationProfile) else null,
                        pageTurnPaginationStatus = if (reactiveOwner) state.paginationProfile.status else null,
                        pageTurnVisualPageIndex = if (reactiveOwner) state.chrome.currentLocator?.pageIndex else null,
                        pageTurnVisualLocationReason = if (reactiveOwner) state.chrome.currentLocator?.reason else null,
                        pageTurnFoliateSessionId = if (reactiveOwner) state.foliateSessionId else null,
                        pageTurnSettlementAck = if (reactiveOwner) state.pageTurnSettlementAck else null,
                        whispersyncOverlayActive = false,
                        whispersyncAnchorReceipt = null,
                        whispersyncHighlightColorArgb = 0,
                        whispersyncCueMapState = ReaderWhispersyncCueMapState(),
                        onWhispersyncCueMapHoldOutcome = { _, _ -> },
                        onWhispersyncCueMapSeekRequested = {},
                        onStartupShellPrepared = {},
                        onViewerAction = { null },
                        onPageTurnBoundary = { null },
                        onReadableDragPreview = { _, _, _, _, _ -> },
                        onContentLongPress = { _, _, _, _ -> },
                        modifier = Modifier.fillMaxSize(),
                        viewerContent = {
                            remember {
                                ConstructionObservation(
                                    store().snapshot, runtime(), originalFinalizationReturned()
                                ).also(constructionObservations::add)
                            }
                            ReaderEngineWebViewHost(
                                publicationUrl = "https://example.invalid/public-synthetic-startup.epub",
                                title = "Public synthetic startup",
                                kind = ReaderPublicationKind.Ebook,
                                mediaOverlayEnabled = false,
                                externalShellCover = false,
                                suppressWebShellCover = false,
                                nativeShellCoverTint = null,
                                settings = defaultReaderSettings(),
                                startCfi = null,
                                startHref = null,
                                startProgress = null,
                                onEvent = { event ->
                                    if (reactiveOwner) model = model.onEngineHostEvent(event).coordinator
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        composeOverlay = {}
                    )
                }
            }
            settle()
            assertNotNull(nativeRoot(), "The test must exercise the actual normal Native composition")
        }

        fun nativeRoot(): View? {
            fun visit(view: View): View? {
                if (view.javaClass.simpleName == "KomikkuReaderNativeFrameRoot") return view
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))?.let { return it }
                }
                return null
            }
            return visit(compose)
        }

        // Read custody from the actual private root; never inject ports, runtime, or owner records.
        private fun rootField(name: String): Any? {
            val root = checkNotNull(nativeRoot())
            return root.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(root)
        }

        fun store() = rootField("activatedSessionStore") as ReaderActivatedSessionSnapshotStore
        fun runtime() = rootField("activatedTransitionCoordinator") as? ReaderResumableTransitionCoordinator
        fun requests() = rootField("normalSemanticRequests") as ReaderSemanticExecutableRequestRegistry
        fun executor() = rootField("normalSemanticExecutor") as ReaderSemanticCommandExecutor
        fun selectedCandidate() = rootField("normalProductionCandidate") as ReaderTransitionGatewayCandidate

        fun originalResourcePort(): ReaderTransitionResourcePort {
            val authority = store().snapshot?.portAuthority as? ReaderActivatedSessionPortAuthority.Production
            if (authority != null) return authority.ports.resources
            // Negative preinstall command control borrows the already-frozen original source.
            // It creates no installation, physical ownership or replacement publisher.
            val frameSource = (rootField("frameOrHandoffPhysicalSource\$delegate") as Lazy<*>).value
                as ReaderFrameOrHandoffPhysicalSource
            val native = assertNotNull(rootField("viewerContainer"))
            return native.javaClass.declaredMethods.single { it.name == "createInstalledResourcePort" }
                .apply { isAccessible = true }.invoke(native, frameSource, executor()) as ReaderTransitionResourcePort
        }

        fun material(): ReaderFramePhysicalOwnershipAdapter.Lease? {
            val native = rootField("viewerContainer") as ViewGroup
            return native.javaClass.getDeclaredField("currentFrameInstallation")
                .apply { isAccessible = true }.get(native) as? ReaderFramePhysicalOwnershipAdapter.Lease
        }

        private fun originalFinalizationReturned(): Boolean {
            val port = rootField("normalOriginalPort") as? ReaderOriginalLegacyFreezeAndInventoryPort ?: return false
            fun read(name: String) = port.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(port)
            return read("finished") == true && read("physicalCommandInProgress") == false && read("checkpoint") == null
        }

        fun canAcceptNewPointer(root: View = checkNotNull(nativeRoot())): Boolean {
            return root.javaClass.getDeclaredMethod("canAcceptNewPointer").apply { isAccessible = true }.invoke(root) as Boolean
        }

        fun engine(): WebView? {
            fun visit(view: View): WebView? {
                if (view is WebView) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))?.let { return it }
                }
                return null
            }
            return material()?.wrapper?.let(::visit)
        }

        private fun rootCall(name: String, vararg arguments: Any?) {
            val root = checkNotNull(nativeRoot())
            root.javaClass.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
                .apply { isAccessible = true }.invoke(root, *arguments)
        }

        fun originalCurlField(name: String): Any? {
            val native = rootField("viewerContainer")
            val controller = checkNotNull(native).javaClass.getDeclaredField("playLikeCurlController")
                .apply { isAccessible = true }.get(native)
            return controller.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(controller)
        }

        fun deliverVisualLocation(page: Int, foliateSession: String) =
            rootCall("setPageTurnVisualLocation", page, "public-synthetic-continuous-observation", foliateSession, null)

        fun deliverUnboundWordSync(color: Int) = rootCall("setWhispersyncOverlay", true, null, color)

        fun engineCommands(): ReaderWebCommandDispatchState {
            val engine = checkNotNull(engine())
            return engine.webViewClient.javaClass.declaredFields.firstNotNullOfOrNull {
                val value = it.apply { isAccessible = true }.get(engine.webViewClient)
                (value as? State<*>)?.value as? ReaderWebCommandDispatchState
            } ?: error("Original Engine command ledger required")
        }

        fun viewerField(name: String): Any? {
            val native = checkNotNull(rootField("viewerContainer"))
            return native.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(native)
        }

        fun assertOriginalInstalledFrameResolversBound() {
            val publisher = (viewerField("nativePagePresentationPublisher\$delegate") as Lazy<*>).value
                as ReaderNativePagePresentationPublisher
            fun hasResolver(name: String) = publisher.javaClass.getDeclaredField(name)
                .apply { isAccessible = true }.get(publisher) != null
            assertTrue(hasResolver("commandTargetResolver"), "A407.Frame.OriginalNativeResolverBound")
            assertTrue(hasResolver("curlTerminalTargetResolver"), "A407.Frame.OriginalTypedCurlResolverBound")
        }

        fun refreshOriginalInstalledDecoration(): ReaderPortCommandResult {
            val native = assertNotNull(rootField("viewerContainer"))
            return native.javaClass.getDeclaredMethod("refreshInstalledWhispersyncConsumer")
                .apply { isAccessible = true }.invoke(native) as ReaderPortCommandResult
        }

        fun originalInstalledWork(): ReaderActivatedRasterWork {
            val invocation = assertNotNull(viewerField("pendingInstalledRaster"),
                "A407.InitialPlan.OriginalPreparationInvocation")
            val work = invocation.javaClass.getDeclaredField("work").apply { isAccessible = true }
                .get(invocation) as ReaderActivatedRasterWork
            val controller = viewerField("pageRasterPreparationController") as ReaderPageRasterPreparationController
            assertSame(work, controller.installedWorkFor(work.lease.resourceKey),
                "A407.InitialPlan.OriginalPreparationLease")
            return work
        }

        fun originalJournal(): ReaderTransitionJournal {
            val runtime = assertNotNull(runtime(), "E407.ContinuousRoot.OriginalJournalOwner")
            return runtime.javaClass.getDeclaredField("journal").apply { isAccessible = true }
                .get(runtime) as ReaderTransitionJournal
        }

        private fun originalCurl(): ReaderPlayLikeCurlFoliateController =
            viewerField("playLikeCurlController") as ReaderPlayLikeCurlFoliateController

        private fun originalInstalledPlan(): ReaderPageRasterPreparationPlan {
            val invocation = assertNotNull(viewerField("pendingInstalledRaster"),
                "E407.ContinuousRoot.OriginalPendingInvocation")
            return invocation.javaClass.getDeclaredField("plan").apply { isAccessible = true }
                .get(invocation) as ReaderPageRasterPreparationPlan
        }

        fun assertOriginalInitialOnlyPlan(work: ReaderActivatedRasterWork) {
            val plan = originalInstalledPlan()
            assertTrue(plan.centerPageIndex == 0 && plan.pageCount == 8 && plan.step == 1 &&
                plan.targets.map { it.pageIndex } == (0..7).toList(), "E407.InitialOnly.CompleteEightTargetPlan")
            val layout = assertNotNull(plan.resolvedLayout, "E407.InitialOnly.OriginalCapturedLayout")
            assertTrue(layout.layoutGeneration == 7L && layout.viewGeneration == 3L && layout.pageIndex == 0,
                "E407.InitialOnly.OriginalQueryLayoutRetained")
            assertTrue(work.lease.transitionId.operation == ReaderTransitionOperation.BootstrapNativePage &&
                work.lease.transitionId.expectedBinding is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial,
                "E407.InitialOnly.OriginalBootstrapAllocation")
            assertTrue((engine() as ReaderEngineCurrentDestinationQuerySource).captureResolvedLayoutCurrency() == null,
                "E407.InitialOnly.NoLayoutCurrencyOutsideOriginalQueryDelivery")
            assertTrue(lastInstalledTurnReceipt == null, "E407.InitialOnly.NoManufacturedInstalledTurnReceipt")
            assertTrue(installedTurnIngress === viewerField("installedPageTurnModelIngress"),
                "E407.OwningRoot.OriginalInstalledIngressActuallyBound")
        }

        fun requireOriginalPassiveProducer() {
            val source = assertNotNull(viewerField("passiveRasterPreparationAdapter") as? ReaderPassiveRasterPreparationAdapter,
                "E407.ProducerPrerequisite.OriginalPassiveAdapterRequired")
            assertTrue(source.isAvailable, "E407.ProducerPrerequisite.OriginalPassiveRuntimeExecutionMissing")
            // Availability alone does not stand in for canonical commit/capture/prepared targets.
            assertTrue(originalCurl().installedWorkFor(originalInstalledWork().lease.resourceKey) != null,
                "E407.ProducerPrerequisite.OriginalCaptureAndPreparationMissing")
        }

        @Suppress("UNCHECKED_CAST")
        private fun originalPublication(): ReaderOwnerAndInputPublicationSnapshot? =
            (viewerField("readInstalledPublication") as? (() -> ReaderOwnerAndInputPublicationSnapshot?))?.invoke()

        fun requireOriginalDisplayedPublication(): ReaderOwnerAndInputPublicationSnapshot {
            val publication = assertNotNull(originalPublication(),
                "E407.ProducerPrerequisite.OriginalSourceDrawAndAtomicPublicationMissing")
            assertTrue(publication.owner is ReaderPresentationFrameOwner.NativePage,
                "E407.ContinuousRoot.NativeOwnerFromOriginalPublication")
            assertTrue(publication.lease is ReaderTransitionInputLease.NativePage && canAcceptNewPointer(),
                "E407.ContinuousRoot.AtomicNativeInputNotChromeOnly")
            val deck = assertNotNull(originalCurl().installedDeckFor(publication.resource.key),
                "E407.ContinuousRoot.OriginalDisplayedDeckReadable")
            val association = assertNotNull(originalCurl().installedDecorationAssociationFor(deck),
                "E407.ContinuousRoot.OriginalDisplayedAssociation")
            assertTrue(association.displayedProof == (publication.owner as ReaderPresentationFrameOwner.NativePage).proof &&
                deck.binding == publication.binding, "E407.ContinuousRoot.DisplayAndDeckShareExactPublication")
            assertTrue(owningController.state.presentationDecision.frameOwner == publication.owner &&
                owningController.state.presentation.binding == publication.binding,
                "E407.ContinuousRoot.CommonOwnerProjectsOriginalAtomicPublication")
            assertNull(assertNotNull(runtime(), "E407.ContinuousRoot.TerminalCoordinator").snapshot().activeOperation,
                "E407.ContinuousRoot.OriginalTransitionTerminalBeforeNextPointer")
            assertEquals(0, executor().activeSlotCount, "E407.ContinuousRoot.OriginalSemanticSlotRetired")
            assertEquals(0, requests().activeHandleCount, "E407.ContinuousRoot.OriginalRequestRetired")
            return publication
        }

        private fun postOriginalEngine(message: JsonObject) {
            val engine = assertNotNull(engine(), "E407.ContinuousRoot.OriginalWireEngine")
            val bridge = shadowOf(engine).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as ReaderJavascriptBridge
            bridge.postMessage(message.toString())
            shadowOf(Looper.getMainLooper()).idle()
        }

        /** Only a command actually dispatched by the ORIGINAL installed source may supply
         * correlated public-fixture input. No renderer listener, completion constructor,
         * mutation counter, settlement fact or presentation receipt is invoked/seeded. */
        fun returnOriginalInstalledTurnSettlement(predecessor: ReaderOwnerAndInputPublicationSnapshot): Int {
            val ledger = engineCommands()
            val pending = assertNotNull(ledger.pendingCommands.singleOrNull {
                it.dispatch.command is ReaderBridgeCommand.GoToVisualPage
            }, "E407.ProducerPrerequisite.OriginalRendererSettlementAndTypedDispatchMissing")
            val command = pending.dispatch.command as ReaderBridgeCommand.GoToVisualPage
            assertTrue(pending.lastDispatchedGeneration == ledger.runtimeGeneration &&
                command.settleSessionId == predecessor.binding.foliateSessionId &&
                command.settleRasterGeneration == predecessor.binding.rasterGeneration &&
                command.settleTextureGeneration == predecessor.binding.textureGeneration,
                "E407.ContinuousRoot.OriginalCommandCorrelatedToDisplayedSource")
            val receiptBefore = lastInstalledTurnReceipt
            val location = buildJsonObject {
                put("type", "locationChanged"); put("commandId", pending.dispatch.id)
                put("foliateSessionId", command.settleSessionId); put("destinationFoliateSessionId", command.settleSessionId)
                put("destinationCommitSequence", command.sourceDestinationCommitSequence + 1L)
                put("pageIndex", command.pageIndex); put("pageTurnSettleToken", command.settleToken)
                put("pageTurnSettleSessionId", command.settleSessionId)
                put("pageTurnSettleRasterGeneration", command.settleRasterGeneration)
                put("pageTurnSettleTextureGeneration", command.settleTextureGeneration)
            }
            postOriginalEngine(location)
            val receipt = assertNotNull(lastInstalledTurnReceipt, "E407.ContinuousRoot.ActualOwningTurnReceipt")
            assertTrue(receipt !== receiptBefore && receipt.disposition == ReaderPresentationEventDisposition.Accepted,
                "E407.ContinuousRoot.OwningIngressAcceptsOriginalSettlement")
            assertTrue(engineCommands().pendingCommands.none { it.dispatch.id == pending.dispatch.id },
                "E407.ContinuousRoot.OriginalHeadConsumedBeforeOwningReceipt")
            val beforeReplay = assertNotNull(runtime(), "E407.ContinuousRoot.ReplayCoordinator").snapshot()
            postOriginalEngine(location)
            assertTrue(receipt === lastInstalledTurnReceipt, "E407.ContinuousRoot.ObsoleteLocationCannotReplayOwningIngress")
            assertTrue(beforeReplay == runtime()?.snapshot(), "E407.ContinuousRoot.ObsoleteLocationCannotAdvanceCoordinator")
            return command.pageIndex
        }

        /** Successor currency must be allocation-owned, not another transport binding to
         * the turn's original semantic slot or the initial scope captured at page zero. */
        fun requireOriginalSuccessorAllocation(predecessor: ReaderOwnerAndInputPublicationSnapshot,
            settledPage: Int, bootstrapWork: ReaderActivatedRasterWork) {
            val query = assertNotNull(engineCommands().pendingCommands.singleOrNull {
                it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
            }, "E407.SuccessorAllocation.OriginalAllocationOwnedLayoutQueryMissing")
            val token = assertNotNull(query.currentDestinationContinuation as? ReaderEngineCurrentDestinationContinuation,
                "E407.SuccessorAllocation.OriginalQueryContinuation")
            assertFalse(token.initialBootstrap, "E407.SuccessorAllocation.NoBootstrapCurrencyReplay")
            assertTrue(query.lastDispatchedGeneration == engineCommands().runtimeGeneration,
                "E407.SuccessorAllocation.OriginalReadOnlyQueryDispatched")
            val engine = assertNotNull(engine(), "E407.SuccessorAllocation.OriginalEngine")
            val before = shadowOf(engine).lastEvaluatedJavascriptCallback
            val layout = buildJsonObject {
                put("layoutGeneration", 7L); put("viewGeneration", 3L); put("navigationGeneration", 5L + settledPage)
                put("sectionIndex", 0); put("pageIndex", settledPage); put("pageCount", 8)
                put("width", engine.width.toDouble()); put("height", engine.height.toDouble())
            }
            postOriginalEngine(buildJsonObject {
                put("type", "currentDestinationResolved"); put("commandId", query.dispatch.id)
                put("foliateSessionId", predecessor.binding.foliateSessionId)
                put("destinationFoliateSessionId", predecessor.binding.foliateSessionId)
                put("destinationCommitSequence", assertNotNull(owningController.state.presentation.binding?.destinationCommitIdentity,
                    "E407.SuccessorAllocation.OwningDestination").commitSequence)
                put("pageIndex", settledPage); put("resolvedLayout", layout)
            })
            val callback = assertNotNull(shadowOf(engine).lastEvaluatedJavascriptCallback,
                "E407.SuccessorAllocation.OriginalPlanCallback")
            assertTrue(callback !== before && shadowOf(engine).lastEvaluatedJavascript ==
                "JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(null) ?? null)",
                "E407.SuccessorAllocation.NewCallbackFromOriginalAllocationExpression")
            callback.onReceiveValue(JsonPrimitive(publicSuccessorPlan(engine, layout, settledPage).toString()).toString())
            shadowOf(Looper.getMainLooper()).idle()
            val work = originalInstalledWork()
            assertTrue(work !== bootstrapWork && work.lease.transitionId.operation == ReaderTransitionOperation.CurlClaimAndSettlement &&
                work.lease.binding.destinationCommitIdentity == owningController.state.presentation.binding?.destinationCommitIdentity,
                "E407.SuccessorAllocation.PendingInvocationMutexMustAdmitOriginalSuccessor")
            assertTrue(work.lease.rasterGeneration != bootstrapWork.lease.rasterGeneration &&
                work.lease.binding.textureGeneration != predecessor.binding.textureGeneration,
                "E407.SuccessorAllocation.OriginalCountersAllocateNewMaterial")
            assertTrue(originalInstalledPlan().resolvedLayout?.pageIndex == settledPage,
                "E407.SuccessorAllocation.SuccessorOwnsNewCapturedLayout")
            assertTrue((engine as ReaderEngineCurrentDestinationQuerySource).captureResolvedLayoutCurrency() == null,
                "E407.SuccessorAllocation.NoCurrencyOutsideOriginalSuccessorQuery")
        }

        /** Public eight-page wire fixture; source order is navic-reader-page-turn-preview.js
         * pageTurnRasterPreparationPlan. This never populates a cache/deck or reports Ready. */
        private fun publicSuccessorPlan(engine: WebView, layout: JsonObject, center: Int): JsonObject {
            val targets = linkedMapOf<Int, String>()
            fun add(page: Int, priority: String) { if (page in 0..7 && page !in targets) targets[page] = priority }
            add(center, "current"); add(center + 1, "next-transition"); add(center - 1, "previous-transition")
            for (offset in 2..5) add(center + offset, "next-lookahead")
            for (offset in 2..5) add(center - offset, "previous-lookahead")
            for (page in 0..7) add(page, "current-chapter")
            return buildJsonObject {
                put("resolvedLayout", layout)
                put("captureGeometry", buildJsonObject {
                    put("viewportWidth", engine.width.toDouble()); put("viewportHeight", engine.height.toDouble()); put("mode", "single")
                    put("pages", buildJsonArray { add(buildJsonObject {
                        put("role", "full"); put("left", 0.0); put("top", 0.0)
                        put("width", engine.width.toDouble()); put("height", engine.height.toDouble())
                    }) })
                })
                put("context", buildJsonObject {
                    put("centerPageIndex", center); put("pageCount", 8); put("layoutMode", "single")
                    put("readerDirection", "ltr"); put("step", 1); put("currentChapterIndex", 0)
                    put("currentChapterPageStartIndex", 0); put("currentChapterPageCount", 8)
                })
                put("targets", buildJsonArray { targets.forEach { (page, priority) -> add(buildJsonObject {
                    put("pageIndex", page); put("priority", priority)
                    put("authority", if (page == center) "CurrentLive" else "OffscreenPassive")
                }) } })
            }
        }

        fun requireOriginalTypedTerminalTarget(predecessor: ReaderOwnerAndInputPublicationSnapshot):
            ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame {
            val active = assertNotNull(originalJournal().active, "E407.ContinuousRoot.OriginalSuccessorTransition")
            val specification = assertNotNull((active.frameTarget?.specification ?: active.pendingFrameTargetSpecification)
                as? ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame,
                "E407.ProducerPrerequisite.OriginalSuccessorDeckAndTypedTargetMissing")
            val identity = assertNotNull(specification.terminalIdentity,
                "E407.ProducerPrerequisite.OriginalCompletedSettlementRendererIdentityMissing")
            assertTrue(identity.sourceDeckKey == predecessor.resource.key && identity.terminalSequence > 0L &&
                identity.sourceDeckTarget.rendererGeneration == originalCurl().surfaceView.rendererGeneration,
                "E407.ContinuousRoot.TypedTargetPreservesOriginalCompletionAndRendererContext")
            assertNotNull(originalCurl().installedCurlTerminalCandidate(specification),
                "E407.ContinuousRoot.TypedTargetAuthenticatesOriginalCompletionNotNativeFallback")
            return specification
        }

        fun deliverWordSync(receipt: ReaderWhispersyncAnchorReceipt, color: Int) =
            rootCall("setWhispersyncOverlay", true, receipt, color)

        private enum class OpeningTransportBoundary { BeforePlacement, BeforeReady, AfterReady }

        /** Exhaustive fixed public-fixture labels only: no enum.name, Throwable content,
         * captured identity, dimension, version or context is exported by this witness. */
        private fun initialAdmissionFailureLabel(stage: ReaderInitialAdmissionStage, predicateThrew: Boolean): String =
            when (stage) {
                ReaderInitialAdmissionStage.NotEvaluated -> "A407.InitialAdmission.NotEvaluated"
                ReaderInitialAdmissionStage.ExecutionBefore -> if (predicateThrew)
                    "A407.InitialAdmission.ExecutionBefore.PredicateThrew" else "A407.InitialAdmission.ExecutionBefore"
                ReaderInitialAdmissionStage.ConstructionInactive -> if (predicateThrew)
                    "A407.InitialAdmission.ConstructionInactive.PredicateThrew" else "A407.InitialAdmission.ConstructionInactive"
                ReaderInitialAdmissionStage.NativePhysicalEligibility -> if (predicateThrew)
                    "A407.InitialAdmission.NativePhysicalEligibility.PredicateThrew" else "A407.InitialAdmission.NativePhysicalEligibility"
                ReaderInitialAdmissionStage.CandidateReservationTransitionEligibility -> if (predicateThrew)
                    "A407.InitialAdmission.CandidateReservationTransitionEligibility.PredicateThrew" else "A407.InitialAdmission.CandidateReservationTransitionEligibility"
                ReaderInitialAdmissionStage.ExecutionAtActivation -> if (predicateThrew)
                    "A407.InitialAdmission.ExecutionAtActivation.PredicateThrew" else "A407.InitialAdmission.ExecutionAtActivation"
                ReaderInitialAdmissionStage.InstallationStructure -> if (predicateThrew)
                    "A407.InitialAdmission.InstallationStructure.PredicateThrew" else "A407.InitialAdmission.InstallationStructure"
                ReaderInitialAdmissionStage.InstallationCurrentness -> if (predicateThrew)
                    "A407.InitialAdmission.InstallationCurrentness.PredicateThrew" else "A407.InitialAdmission.InstallationCurrentness"
                ReaderInitialAdmissionStage.InstallationCurrency -> if (predicateThrew)
                    "A407.InitialAdmission.InstallationCurrency.PredicateThrew" else "A407.InitialAdmission.InstallationCurrency"
                ReaderInitialAdmissionStage.InstallationCurrencyCandidateStore -> if (predicateThrew)
                    "A407.InitialAdmission.InstallationCurrencyCandidateStore.PredicateThrew" else "A407.InitialAdmission.InstallationCurrencyCandidateStore"
                ReaderInitialAdmissionStage.InstallationCurrencyExecution -> if (predicateThrew)
                    "A407.InitialAdmission.InstallationCurrencyExecution.PredicateThrew" else "A407.InitialAdmission.InstallationCurrencyExecution"
                ReaderInitialAdmissionStage.EngineAncestry -> if (predicateThrew)
                    "A407.InitialAdmission.EngineAncestry.PredicateThrew" else "A407.InitialAdmission.EngineAncestry"
                ReaderInitialAdmissionStage.ReporterVersion -> if (predicateThrew)
                    "A407.InitialAdmission.ReporterVersion.PredicateThrew" else "A407.InitialAdmission.ReporterVersion"
                ReaderInitialAdmissionStage.ReporterGenerationValidity -> if (predicateThrew)
                    "A407.InitialAdmission.ReporterGenerationValidity.PredicateThrew" else "A407.InitialAdmission.ReporterGenerationValidity"
                ReaderInitialAdmissionStage.ExecutionFinal -> if (predicateThrew)
                    "A407.InitialAdmission.ExecutionFinal.PredicateThrew" else "A407.InitialAdmission.ExecutionFinal"
                ReaderInitialAdmissionStage.CapturedScopeIdentity -> if (predicateThrew)
                    "A407.InitialAdmission.CapturedScopeIdentity.PredicateThrew" else "A407.InitialAdmission.CapturedScopeIdentity"
                ReaderInitialAdmissionStage.CapturedScopeVersion -> if (predicateThrew)
                    "A407.InitialAdmission.CapturedScopeVersion.PredicateThrew" else "A407.InitialAdmission.CapturedScopeVersion"
                ReaderInitialAdmissionStage.CapturedScopeDimensions -> if (predicateThrew)
                    "A407.InitialAdmission.CapturedScopeDimensions.PredicateThrew" else "A407.InitialAdmission.CapturedScopeDimensions"
                ReaderInitialAdmissionStage.CapturedScopeGenerations -> if (predicateThrew)
                    "A407.InitialAdmission.CapturedScopeGenerations.PredicateThrew" else "A407.InitialAdmission.CapturedScopeGenerations"
                ReaderInitialAdmissionStage.ExecutionAfter -> if (predicateThrew)
                    "A407.InitialAdmission.ExecutionAfter.PredicateThrew" else "A407.InitialAdmission.ExecutionAfter"
                ReaderInitialAdmissionStage.AuthorityUnavailable -> "A407.InitialAdmission.AuthorityUnavailable"
                ReaderInitialAdmissionStage.AuthorityChanged -> "A407.InitialAdmission.AuthorityChanged"
                ReaderInitialAdmissionStage.Admitted -> "A407.InitialAdmission.Admitted"
            }

        /** The first actual Native onSizeChanged after authentic scope capture, categorized
         * by the synchronous original fixture edge. This does not infer geometry values,
         * the Android caller, or a repair from the earlier dimension-inequality RED. */
        private fun initialGeometryFailureLabel(boundary: ReaderInitialGeometryWitnessBoundary?): String = when (boundary) {
            null -> "A407.InitialAdmission.CapturedScopeDimensions.NoPostCaptureSizeCallback"
            ReaderInitialGeometryWitnessBoundary.OutsideFixture ->
                "A407.InitialAdmission.CapturedScopeDimensions.PostCaptureSizeChange.OutsideFixture"
            ReaderInitialGeometryWitnessBoundary.CompositionFrame ->
                "A407.InitialAdmission.CapturedScopeDimensions.PostCaptureSizeChange.CompositionFrame"
            ReaderInitialGeometryWitnessBoundary.OuterLayout ->
                "A407.InitialAdmission.CapturedScopeDimensions.PostCaptureSizeChange.OuterLayout"
            ReaderInitialGeometryWitnessBoundary.MaterialLayout ->
                "A407.InitialAdmission.CapturedScopeDimensions.PostCaptureSizeChange.MaterialLayout"
            ReaderInitialGeometryWitnessBoundary.LooperDrain ->
                "A407.InitialAdmission.CapturedScopeDimensions.PostCaptureSizeChange.LooperDrain"
        }

        /** Inspect only typed original custody in memory. The finite boundary differentiates
         * a Native admission loss from Engine runtime/publication/session loss without
         * exposing captured closures, identities, exceptions or reader payload. */
        fun witnessOriginalOpeningTransport() {
            val originalEngine = assertNotNull(engine(), "A407.OpeningTransport.OriginalEngine")
            val originalMaterial = assertNotNull(material(), "A407.OpeningTransport.OriginalMaterial")
            val queued = engineCommands()
            val opening = assertNotNull(queued.pendingCommands.singleOrNull {
                it.dispatch.command is ReaderBridgeCommand.OpenPublication
            }, "A407.OpeningTransport.OriginalOpening")
            val query = assertNotNull(queued.pendingCommands.singleOrNull {
                it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
            }, "A407.OpeningTransport.OriginalQuery")
            val token = assertNotNull(query.currentDestinationContinuation as? ReaderEngineCurrentDestinationContinuation,
                "A407.OpeningTransport.OriginalQueryToken")
            assertTrue(token.initialBootstrap && query.requiresDispatchedOpening,
                "A407.OpeningTransport.AuthenticInitialCustody")
            assertFalse(queued.pendingCommands.any { it.lastDispatchedGeneration != null },
                "A407.OpeningTransport.NotDispatchedBeforeReady")

            fun witness(boundary: OpeningTransportBoundary) {
                val ledger = engineCommands()
                val prefix = "A407.OpeningTransport.${boundary.name}"
                assertTrue(originalEngine === engine(), "$prefix.SameEngine")
                assertTrue(originalMaterial === material(), "$prefix.SameMaterial")
                assertTrue(originalEngine.isAttachedToWindow && originalEngine.windowVisibility == View.VISIBLE,
                    "$prefix.AttachedVisible")
                assertTrue(originalEngine.url == ReaderWebRuntime.entrypointUrl, "$prefix.Entrypoint")
                assertFalse((originalEngine as ReaderEngineWebView).runtimeRetired, "$prefix.RuntimeNotRetired")
                assertTrue(originalMaterial.owned && !originalMaterial.operating && !originalMaterial.permanent &&
                    originalMaterial.state == ReaderLegacyResourceState.Registered,
                    "$prefix.OriginalRegisteredMaterial")
                assertTrue(originalMaterial.isCurrent() && originalMaterial.commandCurrency?.invoke() == true,
                    "$prefix.OriginalInstallationCurrent")
                assertTrue(token.belongsTo(originalEngine) && token.active && !token.retired,
                    "$prefix.OriginalTokenActive")
                assertTrue(token.expectedGeneration == ledger.runtimeGeneration, "$prefix.ExactRuntime")
                assertTrue(token.expectedPublicationKey == ledger.publicationKey, "$prefix.ExactPublication")
                assertTrue(token.expectedFoliateSessionId == ledger.foliateSessionId,
                    "$prefix.ExactOpeningSession")
                assertTrue(ledger.pendingCommands.any { it.currentDestinationContinuation === token &&
                    it.dispatch.id == query.dispatch.id }, "$prefix.OriginalQueryRetained")
                // This is the ORIGINAL producer's admission predicate, not an independently
                // computed substitute. Snapshot finite markers immediately after this ONE
                // actual call, then assert outside the predicate/Engine callback isolation.
                val admissionWitness = token.initialAdmissionWitness
                val admitted = token.admits()
                val stage = admissionWitness?.initialAdmissionFirstFailure
                    ?: admissionWitness?.initialAdmissionEvaluatedStage ?: ReaderInitialAdmissionStage.NotEvaluated
                val predicateThrew = admissionWitness?.initialAdmissionFirstFailurePredicateThrew == true
                val evaluatedStage = admissionWitness?.initialAdmissionEvaluatedStage
                val stillEvaluating = admissionWitness?.initialAdmissionEvaluationActive == true
                assertTrue(stage != ReaderInitialAdmissionStage.NotEvaluated,
                    "A407.InitialAdmission.WitnessEvaluated")
                assertFalse(stillEvaluating, "A407.InitialAdmission.EvaluationClosed")
                assertTrue(admitted == (evaluatedStage == ReaderInitialAdmissionStage.Admitted) &&
                    (!predicateThrew || !admitted), "A407.InitialAdmission.ResultPreserved")
                val failureLabel = if (stage == ReaderInitialAdmissionStage.CapturedScopeDimensions && !predicateThrew)
                    initialGeometryFailureLabel(admissionWitness?.initialGeometryFirstChange)
                else initialAdmissionFailureLabel(stage, predicateThrew)
                assertTrue(admitted, failureLabel)
                assertTrue(admitted, "$prefix.OriginalNativeAdmission")
            }
            fun post(message: JsonObject) {
                val bridge = shadowOf(originalEngine).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)
                    as ReaderJavascriptBridge
                bridge.postMessage(message.toString())
                shadowOf(Looper.getMainLooper()).idle()
            }

            witness(OpeningTransportBoundary.BeforePlacement)
            deliverOriginalUnitInteropPlacement()
            witness(OpeningTransportBoundary.BeforeReady)
            post(buildJsonObject { put("type", "ready") })
            witness(OpeningTransportBoundary.AfterReady)
            val readyHead = assertNotNull(engineCommands().pendingCommands.firstOrNull(),
                "A407.OpeningTransport.ReadyHeadPresent")
            assertTrue(readyHead.dispatch.id == opening.dispatch.id &&
                readyHead.lastDispatchedGeneration == engineCommands().runtimeGeneration,
                "A407.OpeningTransport.OriginalOpeningActuallyDispatched")
            post(buildJsonObject { put("type", "commandAck"); put("commandId", opening.dispatch.id) })
            val afterAck = engineCommands()
            assertEquals(1, afterAck.pendingCommands.size, "A407.OpeningTransport.AfterOpeningAckCount")
            val dispatchedQuery = assertNotNull(afterAck.pendingCommands.singleOrNull(),
                "A407.OpeningTransport.AfterOpeningAckQuery")
            assertTrue(dispatchedQuery.currentDestinationContinuation === token &&
                dispatchedQuery.dispatch.id == query.dispatch.id &&
                dispatchedQuery.lastDispatchedGeneration == afterAck.runtimeGeneration,
                "A407.OpeningTransport.OriginalQueryActuallyDispatched")
        }

        /** Controlled public protocol responses to the ORIGINAL registered Engine query
         * and plan callback. No test installation, seeded allocation/deck/frame/input ACK,
         * or passive Ready is supplied. This is not actual Foliate/GL acceptance. */
        fun returnOriginalInitialLayoutAndPlan(deferPlan: Boolean = false) {
            val engine = assertNotNull(engine())
            fun post(message: JsonObject) {
                val bridge = shadowOf(engine).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)
                    as ReaderJavascriptBridge
                bridge.postMessage(message.toString())
                shadowOf(Looper.getMainLooper()).idle()
            }
            val queued = engineCommands()
            assertEquals(2, queued.pendingCommands.size, "A407.InitialPlan.OriginalQueuedCount")
            assertEquals(1, queued.pendingCommands.count { it.dispatch.command is ReaderBridgeCommand.OpenPublication },
                "A407.InitialPlan.OriginalOpeningCount")
            assertEquals(1, queued.pendingCommands.count { it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination },
                "A407.InitialPlan.OriginalQueryCount")
            assertFalse(queued.pendingCommands.any { it.lastDispatchedGeneration != null },
                "A407.InitialPlan.NotDispatchedBeforeReady")
            assertTrue(engine.isAttachedToWindow, "A407.InitialPlan.EngineAttached")
            // Static type/bounds guards identify the original interop boundary without
            // exporting a hierarchy, arbitrary class names, dimensions or reader payload.
            val wrapper = assertNotNull(material(), "A407.InitialPlan.MaterialRetained").wrapper
            val holder = assertNotNull(engine.parent as? ViewGroup, "A407.InitialPlan.EngineHolderPresent")
            val handler = assertNotNull(holder.parent as? ViewGroup, "A407.InitialPlan.ViewsHandlerPresent")
            val owner = assertNotNull(handler.parent as? ViewGroup, "A407.InitialPlan.ComposeOwnerPresent")
            assertTrue(wrapper is ComposeView, "A407.InitialPlan.OriginalWrapperType")
            assertTrue(holder.javaClass.simpleName == "ViewFactoryHolder", "A407.InitialPlan.EngineHolderType")
            assertTrue(handler.javaClass.simpleName == "AndroidViewsHandler", "A407.InitialPlan.ViewsHandlerType")
            assertTrue(owner.javaClass.simpleName == "AndroidComposeView", "A407.InitialPlan.ComposeOwnerType")
            assertTrue(owner.parent === wrapper, "A407.InitialPlan.OriginalInteropHierarchyRetained")
            assertTrue(wrapper.width > 0 && wrapper.height > 0, "A407.InitialPlan.WrapperMeasured")
            assertTrue(owner.width > 0 && owner.height > 0, "A407.InitialPlan.ComposeOwnerMeasured")
            assertTrue(handler.width > 0 && handler.height > 0, "A407.InitialPlan.ViewsHandlerMeasured")
            assertTrue(holder.width > 0 && holder.height > 0, "A407.InitialPlan.EngineHolderMeasured")
            val platform = shadowOf(engine) as Task407UnitWebViewMeasureBoundary
            assertTrue(platform.originalExactSpecsObserved, "A407.InitialPlan.UnitProviderOriginalExactSpecs")
            assertTrue(engine.measuredWidth > 0 && engine.measuredHeight > 0,
                "A407.InitialPlan.EngineMeasureResultPresent")
            assertEquals(holder.measuredWidth, engine.measuredWidth, "A407.InitialPlan.UnitProviderParentWidth")
            assertEquals(holder.measuredHeight, engine.measuredHeight, "A407.InitialPlan.UnitProviderParentHeight")
            deliverOriginalUnitInteropPlacement()
            assertTrue(engine.width > 0 && engine.height > 0, "A407.InitialPlan.EngineMeasured")
            assertEquals(holder.width, engine.width, "A407.InitialPlan.UnitOriginalPlacementWidth")
            assertEquals(holder.height, engine.height, "A407.InitialPlan.UnitOriginalPlacementHeight")
            post(buildJsonObject { put("type", "ready") }) // Public-fixture JS Ready only.
            val opening = assertNotNull(engineCommands().pendingCommands.firstOrNull(),
                "A407.InitialPlan.ReadyOpeningPresent")
            assertTrue(opening.dispatch.command is ReaderBridgeCommand.OpenPublication,
                "A407.InitialPlan.ReadyHeadIsOpening")
            assertEquals(engineCommands().runtimeGeneration, opening.lastDispatchedGeneration,
                "A407.InitialPlan.OpeningDispatchedAfterReady")
            post(buildJsonObject { put("type", "commandAck"); put("commandId", opening.dispatch.id) })
            val queried = engineCommands()
            assertEquals(1, queried.pendingCommands.size, "A407.InitialPlan.AfterOpeningAckCount")
            val query = assertNotNull(queried.pendingCommands.firstOrNull(),
                "A407.InitialPlan.QueryPresentAfterOpeningAck").dispatch
            assertTrue(query.command is ReaderBridgeCommand.ResolveCurrentDestination,
                "A407.InitialPlan.AfterOpeningAckHeadIsQuery")
            val session = (query.command as ReaderBridgeCommand.ResolveCurrentDestination).foliateSessionId
            val layout = buildJsonObject {
                put("layoutGeneration", 7L); put("viewGeneration", 3L); put("navigationGeneration", 5L)
                put("sectionIndex", 0); put("pageIndex", 0); put("pageCount", 8)
                put("width", engine.width.toDouble()); put("height", engine.height.toDouble())
            }
            val callbackBeforeResolution = shadowOf(engine).lastEvaluatedJavascriptCallback
            post(buildJsonObject {
                put("type", "currentDestinationResolved"); put("commandId", query.id)
                put("foliateSessionId", session); put("destinationFoliateSessionId", session)
                put("destinationCommitSequence", 1L); put("pageIndex", 0); put("resolvedLayout", layout)
            })
            assertEquals(1, initialIngressCount, "A407.InitialPlan.AfterResolvedInitialIngressCount")
            val callback = assertNotNull(shadowOf(engine).lastEvaluatedJavascriptCallback,
                "A407.InitialPlan.AllocationPlanCallbackRegistered")
            // ShadowWebView 4.16.1 replaces its script/callback pair on EVERY evaluation.
            // A retained non-null callback alone may belong to earlier Engine work. Require
            // fresh registration on this exact Engine and the original allocation expression
            // before supplying this public fixture response; export only fixed verdict labels.
            assertTrue(callback !== callbackBeforeResolution,
                "A407.InitialPlan.AllocationCallbackNewAfterOriginalResolution")
            assertTrue(shadowOf(engine).lastEvaluatedJavascript ==
                "JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(null) ?? null)",
                "A407.InitialPlan.AllocationCallbackOriginalExpression")
            val geometry = buildJsonObject {
                put("viewportWidth", engine.width.toDouble()); put("viewportHeight", engine.height.toDouble())
                put("mode", "single")
                put("pages", buildJsonArray { add(buildJsonObject {
                    put("role", "full"); put("left", 0.0); put("top", 0.0)
                    put("width", engine.width.toDouble()); put("height", engine.height.toDouble())
                }) })
            }
            val plan = buildJsonObject {
                put("resolvedLayout", layout); put("captureGeometry", geometry)
                put("context", buildJsonObject {
                    put("centerPageIndex", 0); put("pageCount", 8); put("layoutMode", "single")
                    put("readerDirection", "ltr"); put("step", 1); put("currentChapterIndex", 0)
                    put("currentChapterPageStartIndex", 0); put("currentChapterPageCount", 8)
                })
                // JS pageTurnRasterPreparationPlan adds current, +/- transition, +/-
                // lookahead, then unseen current-chapter pages. At center 0/step 1 in
                // this eight-page chapter, negative targets are omitted and chapter
                // pages 0..5 are already seen. Preserve that producer order/priority;
                // only current is Live. No passive readiness is supplied by this plan.
                put("targets", buildJsonArray {
                    listOf(
                        0 to "current", 1 to "next-transition",
                        2 to "next-lookahead", 3 to "next-lookahead",
                        4 to "next-lookahead", 5 to "next-lookahead",
                        6 to "current-chapter", 7 to "current-chapter"
                    ).forEach { (pageIndex, priority) -> add(buildJsonObject {
                        put("pageIndex", pageIndex)
                        put("authority", if (pageIndex == 0) "CurrentLive" else "OffscreenPassive")
                        put("priority", priority)
                    }) }
                })
            }
            val delivery = { callback.onReceiveValue(JsonPrimitive(plan.toString()).toString()) }
            if (deferPlan) {
                assertNull(deferredInitialPlan, "C408.RootClose.NoSecondPlanContinuation")
                deferredInitialPlan = delivery
            } else delivery()
            shadowOf(Looper.getMainLooper()).idle()
        }

        class OriginalAllocationProbe(
            val command: ReaderTransitionCommand.AllocateMaterialBinding,
            val callback: android.webkit.ValueCallback<String>,
            val plan: JsonObject
        )

        fun originalAllocationProbe(): OriginalAllocationProbe {
            // Reuse the original, previously qualified query/initial-ingress path unchanged.
            // Deferred public input belongs to THAT actual Engine evaluation only.
            returnOriginalInitialLayoutAndPlan(deferPlan = true)
            val engine = assertNotNull(engine(), "A407.AllocationProbe.OriginalEngine")
            val resolved = assertNotNull(material()?.initialResolvedLayout,
                "A407.AllocationProbe.OriginalInitialResolvedLayout")
            val command = ReaderTransitionCommand.AllocateMaterialBinding(resolved.transitionId, resolved.binding)
            assertTrue(runtime()?.materialCommandIsCurrent(command) == true,
                "A407.AllocationProbe.ActualAllocationCommandCurrent")
            assertTrue(viewerField("pendingInstalledRaster") == null,
                "A407.AllocationProbe.NoInvocationBeforeOriginalCallback")
            assertTrue(shadowOf(engine).lastEvaluatedJavascript ==
                "JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(null) ?? null)",
                "A407.AllocationProbe.OriginalAllocationExpression")
            val callback = assertNotNull(shadowOf(engine).lastEvaluatedJavascriptCallback,
                "A407.AllocationProbe.OriginalCallbackRegistered")
            val layout = buildJsonObject {
                put("layoutGeneration", 7L); put("viewGeneration", 3L); put("navigationGeneration", 5L)
                put("sectionIndex", 0); put("pageIndex", 0); put("pageCount", 8)
                put("width", engine.width.toDouble()); put("height", engine.height.toDouble())
            }
            return OriginalAllocationProbe(command, callback, publicSuccessorPlan(engine, layout, 0))
        }

        fun deliverOriginalAllocationPlan(probe: OriginalAllocationProbe, plan: JsonObject) {
            probe.callback.onReceiveValue(JsonPrimitive(plan.toString()).toString())
            shadowOf(Looper.getMainLooper()).idle()
        }

        /** Only the original source-local scalar counters, never owner maps/cache/heap.
         * Comparison is in-memory and callers export a fixed verdict label, not values. */
        fun originalAllocationCounters(): List<Long> {
            val preparation = viewerField("pageRasterPreparationController") as ReaderPageRasterPreparationController
            val bundle = viewerField("pageTurnBundleSource") as ReaderPageTurnBundleSource
            val curl = originalCurl()
            fun counter(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
                .apply { isAccessible = true }.getLong(owner)
            return listOf(counter(preparation, "preparationGeneration"), bundle.currentGeneration(),
                counter(curl, "nextDeckGeneration"), counter(curl, "nextRasterProfileEpoch"))
        }

        fun reserveOriginalNativeDeck(command: ReaderTransitionCommand.ReserveDeck,
            onFact: (ReaderTransitionFact) -> Unit,
            declareTarget: (ReaderTransitionFrameTargetSpecification, ReaderTransitionResourceKey) -> ReaderPortCommandResult
        ): ReaderPortCommandResult {
            val native = assertNotNull(rootField("viewerContainer"), "A407.KeyedDeck.OriginalNativeConsumer")
            return try {
                native.javaClass.declaredMethods.single { it.name == "reserveInstalledDeck" && it.parameterCount == 3 }
                    .apply { isAccessible = true }.invoke(native, command, onFact, declareTarget) as ReaderPortCommandResult
            } catch (_: java.lang.reflect.InvocationTargetException) {
                throw AssertionError("A407.KeyedDeck.OriginalConsumerInvocationThrew")
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun destructionConsumer(engine: ReaderEngineWebView): ((WebView) -> Unit)? =
            ReaderEngineWebView::class.java.getDeclaredField("physicalDestructionConfirmation")
                .apply { isAccessible = true }.get(engine) as? ((WebView) -> Unit)

        fun deliverWrongSessionInitialIngress() {
            val root = checkNotNull(nativeRoot())
            root.javaClass.declaredMethods.single { it.name == "setInitialSemanticReceiptIngress" }
                .apply { isAccessible = true }.invoke(root,
                    opened.controller.presentationVersion.copy(
                        readerSessionGeneration = opened.controller.state.readerSessionGeneration + 1L), initialIngress)
        }

        fun deliverInitialIngress() {
            check(provideInitialIngress)
            initialIngressAvailable.value = true
            settle(entry = SettleEntry.InitialIngress)
            assertTrue(initialIngressObservedInComposition,
                "A407.InitialIngress.ComposeObserved")
            val captured = rootField("initialSemanticIngress") as? Pair<*, *>
            assertTrue(captured != null && captured.first == opened.controller.presentationVersion && captured.second === initialIngress,
                "A407.InitialIngress.RootCaptured")
            assertEquals(1, store().atomicWriteCount, "A407.InitialIngress.InstallationCount")
            assertNotNull(runtime(), "A407.InitialIngress.RuntimeRetained")
            assertEquals(1, constructionObservations.size, "A407.InitialIngress.ConstructionCount")
            assertNotNull(engine(), "A407.InitialIngress.EngineConstructed")
        }

        /** Observe real placement callbacks without throwing inside layout or changing
         * geometry. Only the boolean verdict escapes; actual fixture dimensions stay local. */
        fun assertOriginalWindowOwnsEveryPlacement(action: () -> Unit) {
            val parent = assertNotNull(compose.parent as? ViewGroup,
                "A407.WindowGeometry.OriginalContentParent")
            assertSame(activity.get().findViewById<ViewGroup>(android.R.id.content), parent,
                "A407.WindowGeometry.OriginalActivityContent")
            val params = compose.layoutParams
            assertTrue(params.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                params.height == ViewGroup.LayoutParams.MATCH_PARENT,
                "A407.WindowGeometry.OriginalMatchParentAllocation")
            val margins = params as? ViewGroup.MarginLayoutParams
            fun parentOwnsPlacement(): Boolean = parent.width > 0 && parent.height > 0 &&
                compose.width == parent.width - parent.paddingLeft - parent.paddingRight -
                    (margins?.leftMargin ?: 0) - (margins?.rightMargin ?: 0) &&
                compose.height == parent.height - parent.paddingTop - parent.paddingBottom -
                    (margins?.topMargin ?: 0) - (margins?.bottomMargin ?: 0)
            assertTrue(parentOwnsPlacement(), "A407.WindowGeometry.OriginalAllocationEstablished")
            var everyPlacementWindowOwned = true
            val observer = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                everyPlacementWindowOwned = everyPlacementWindowOwned && parentOwnsPlacement()
            }
            compose.addOnLayoutChangeListener(observer)
            try { action() }
            finally { compose.removeOnLayoutChangeListener(observer) }
            assertSame(parent, compose.parent, "A407.WindowGeometry.OriginalParentRetained")
            assertTrue(everyPlacementWindowOwned && parentOwnsPlacement(),
                "A407.WindowGeometry.NoForeignViewportBeforeTraversal")
        }

        fun refresh() {
            refreshEntryWitness = compositionBoundaryLabel()
            revision.value++
            settle(entry = SettleEntry.Refresh)
        }

        fun refreshWithBusyAnimationDemandWitness() {
            check(synchronizationProfile != SynchronizationProfile.OriginalThreeFrame)
            refreshEntryWitness = compositionBoundaryLabel()
            revision.value++
            settle(requireBusyAnimationDemand = true, entry = SettleEntry.Refresh)
        }

        /** Finite read-only source boundaries only. ShadowApplication's latest popup
         * records invokePopup, not every remembered PopupWindow or rejection token.
         * NoRecordedPopup must NOT be interpreted as no owner/token/busy callback.
         * Conversely a showing popup and clock demand alone do not prove causality. */
        private fun compositionBoundaryLabel(): String {
            val state = when (recomposer.currentState.value) {
                Recomposer.State.ShutDown -> "ShutDown"
                Recomposer.State.ShuttingDown -> "ShuttingDown"
                Recomposer.State.Inactive -> "Inactive"
                Recomposer.State.InactivePendingWork -> "InactivePendingWork"
                Recomposer.State.Idle -> "Idle"
                Recomposer.State.PendingWork -> "PendingWork"
            }
            val demand = if (compositionFrameClock.hasAwaiters) "FrameAwaiters" else "NoFrameAwaiters"
            val popup = shadowOf(activity.get().application).latestPopupWindow
            val popupState = when {
                popup == null -> "NoRecordedPopup"
                !popup.isShowing -> "RecordedPopupNotShowing"
                !popup.contentView.isAttachedToWindow -> "RecordedPopupShowingDetached"
                else -> "RecordedPopupShowingAttached"
            }
            return "$state.$demand.$popupState"
        }

        // Robolectric 4.16.1 ShadowPopupWindow.invokePopup records the ORIGINAL real
        // window in ShadowApplication. This public shadow seam neither mounts a popup
        // nor reads remembered Compose slots, visibility state, or callback closures.
        private fun originalShowingBusyPopup(): PopupWindow? {
            val popup = shadowOf(activity.get().application).latestPopupWindow ?: return null
            if (!popup.isShowing) return null
            val content = popup.contentView
            assertTrue(content is ComposeView, "C408.BusyAnimation.OriginalPopupComposeContent")
            assertSame(compose.context, content.context, "C408.BusyAnimation.OriginalPopupAndroidContext")
            assertTrue(content.isAttachedToWindow && content.hasComposition,
                "C408.BusyAnimation.OriginalPopupCompositionAttached")
            assertFalse(popup.isTouchable || popup.isFocusable || popup.isOutsideTouchable,
                "C408.BusyAnimation.PopupCannotTakeInput")
            assertTrue(popup.inputMethodMode == PopupWindow.INPUT_METHOD_NOT_NEEDED,
                "C408.BusyAnimation.PopupCannotTakeInputMethod")
            assertTrue(compose.findViewTreeLifecycleOwner() != null &&
                content.findViewTreeLifecycleOwner() === compose.findViewTreeLifecycleOwner(),
                "C408.BusyAnimation.OriginalLifecycleInherited")
            assertTrue(compose.findViewTreeSavedStateRegistryOwner() != null &&
                content.findViewTreeSavedStateRegistryOwner() === compose.findViewTreeSavedStateRegistryOwner(),
                "C408.BusyAnimation.OriginalSavedStateInherited")
            assertTrue(compose.findViewTreeViewModelStoreOwner() != null &&
                content.findViewTreeViewModelStoreOwner() === compose.findViewTreeViewModelStoreOwner(),
                "C408.BusyAnimation.OriginalViewModelStoreInherited")
            assertTrue(compose.findViewTreeOnBackPressedDispatcherOwner() != null &&
                content.findViewTreeOnBackPressedDispatcherOwner() === compose.findViewTreeOnBackPressedDispatcherOwner(),
                "C408.BusyAnimation.OriginalBackDispatcherInherited")
            // ReaderRendererBusyPopup sets the remembered parent composition context
            // before setContent; the demanded frame below must use THIS fixture's clock.
            return popup
        }

        fun assertNoBusyPopupOrFrameDemand() {
            assertNull(originalShowingBusyPopup(), "C408.BusyAnimation.NoPopupBeforeRejectedPointer")
            assertFalse(compositionFrameClock.hasAwaiters, "C408.BusyAnimation.NoFrameDemandBeforeRejectedPointer")
            assertEquals(Recomposer.State.Idle, recomposer.currentState.value,
                "C408.BusyAnimation.NoPopupPathCompositionIdle")
        }

        private fun commitCompositionFrame(requireOwnedDemand: Boolean = false) {
            Snapshot.sendApplyNotifications()
            compositionScheduler.runCurrent() // Register this invalidation with the real Recomposer.
            if (requireOwnedDemand) assertTrue(compositionFrameClock.hasAwaiters,
                "C408.BusyAnimation.NoFrameWithoutOriginalDemand")
            compositionFrameClock.sendFrame(++compositionFrame * 16_000_000L)
            compositionScheduler.runCurrent() // Apply changes and SideEffects from that exact frame.
        }

        private fun layoutOriginalMaterial() {
            val wrapper = material()?.wrapper ?: return
            val parent = assertNotNull(wrapper.parent as? ViewGroup,
                "A407.InitialIngress.MaterialParentRetained")
            assertTrue(parent.width > 0 && parent.height > 0,
                "A407.InitialIngress.MaterialParentMeasured")
            // AndroidViewsHandler.requestLayout translates its children's layout requests
            // into Compose LayoutNode.requestRemeasure. forceLayout only marks Android View
            // flags: its cached holder specs/placement can remain zero even when this wrapper
            // has real bounds. Request through every original node, bottom-up, including that
            // interop boundary; never measure/layout the Engine with invented geometry.
            fun requestOriginalMeasurement(view: View) {
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    requestOriginalMeasurement(view.getChildAt(index))
                }
                view.requestLayout()
            }
            requestOriginalMeasurement(wrapper)
            wrapper.measure(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.EXACTLY))
            wrapper.layout(0, 0, parent.width, parent.height)
        }

        /** Controlled UNIT placement edge, separate from the provider's measure edge.
         * AndroidViewHolder.onLayout places its original child using (r-l,b-t). A parent
         * whose bounds did not change need not redeliver that callback after UNIT WebView
         * measurement. Invoke that ORIGINAL holder callback with its retained actual bounds;
         * never measure/layout the Engine directly, replace a parent or invent child geometry.
         * This is not Chromium/Foliate/device layout or readiness qualification. */
        private fun deliverOriginalUnitInteropPlacement() {
            val engine = engine() ?: return
            val wrapper = assertNotNull(material()).wrapper
            val holder = assertNotNull(engine.parent as? ViewGroup, "A407.UnitPlacement.OriginalHolderPresent")
            val handler = assertNotNull(holder.parent as? ViewGroup, "A407.UnitPlacement.OriginalHandlerPresent")
            val owner = assertNotNull(handler.parent as? ViewGroup, "A407.UnitPlacement.OriginalOwnerPresent")
            assertTrue(wrapper is ComposeView && owner.parent === wrapper,
                "A407.UnitPlacement.OriginalWrapperHierarchy")
            assertTrue(holder.javaClass.simpleName == "ViewFactoryHolder" &&
                handler.javaClass.simpleName == "AndroidViewsHandler" &&
                owner.javaClass.simpleName == "AndroidComposeView", "A407.UnitPlacement.OriginalInteropTypes")
            assertTrue(engine.isAttachedToWindow, "A407.UnitPlacement.OriginalEngineAttached")
            assertTrue(holder.width > 0 && holder.height > 0 &&
                holder.width == holder.measuredWidth && holder.height == holder.measuredHeight,
                "A407.UnitPlacement.OriginalHolderMeasuredAndPlaced")
            assertTrue((shadowOf(engine) as Task407UnitWebViewMeasureBoundary).originalExactSpecsObserved,
                "A407.UnitPlacement.OriginalExactMeasureSpecs")
            assertEquals(holder.measuredWidth, engine.measuredWidth, "A407.UnitPlacement.OriginalMeasuredWidth")
            assertEquals(holder.measuredHeight, engine.measuredHeight, "A407.UnitPlacement.OriginalMeasuredHeight")
            val originalHolder = holder.javaClass.superclass
            assertEquals("AndroidViewHolder", originalHolder.simpleName, "A407.UnitPlacement.OriginalCallbackOwner")
            // AndroidViewHolder.onLayout addresses its stored `view`, not whichever
            // descendant a lookup happened to find. Establish that ORIGINAL association
            // before assigning the failed bounds to the platform placement edge.
            val originalChild = originalHolder.getDeclaredField("view").apply { isAccessible = true }.get(holder)
            assertSame(engine, originalChild, "A407.UnitPlacement.OriginalStoredChildIsEngine")
            assertEquals(1, holder.childCount, "A407.UnitPlacement.OriginalSingleChild")
            assertSame(originalChild, holder.getChildAt(0), "A407.UnitPlacement.OriginalStoredChildInHierarchy")
            originalHolder.getDeclaredMethod("onLayout", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(holder, false, holder.left, holder.top, holder.right, holder.bottom)
            assertTrue(engine.parent === holder, "A407.UnitPlacement.OriginalParentRetained")
            assertEquals(holder.width, engine.width, "A407.UnitPlacement.OriginalPlacedWidth")
            assertEquals(holder.height, engine.height, "A407.UnitPlacement.OriginalPlacedHeight")
        }

        /** Bracket unchanged synchronous fixture work with one finite source-local category.
         * No callback, dimensions or reader payload is retained; finally cannot leak the
         * category into a later transport return or another fixture operation. */
        private fun withInitialGeometryBoundary(boundary: ReaderInitialGeometryWitnessBoundary, action: () -> Unit) {
            val native = nativeRoot()?.let { rootField("viewerContainer") } ?: return action()
            val field = native.javaClass.getDeclaredField("initialGeometryWitnessBoundary").apply { isAccessible = true }
            field.set(native, boundary)
            try { action() }
            finally { field.set(native, ReaderInitialGeometryWitnessBoundary.OutsideFixture) }
        }

        /** ActivityController.setup has already attached and traversed the real Decor.
         * Lay out through that original parent hierarchy with its established window size.
         * Measuring only the child at a forced 800x600 previously admitted bootstrap under
         * foreign constraints; the queued ViewRoot traversal then replaced those bounds.
         * No display/configuration is changed and no extra looper pass/frame is introduced. */
        private fun layoutOriginalWindowContent() {
            val decor = activity.get().window.decorView
            assertTrue(decor.isAttachedToWindow && decor.width > 0 && decor.height > 0,
                "A407.WindowGeometry.OriginalTraversedDecor")
            assertTrue(compose.rootView === decor && compose.parent is ViewGroup,
                "A407.WindowGeometry.OriginalWindowHierarchy")
            val left = decor.left
            val top = decor.top
            val right = decor.right
            val bottom = decor.bottom
            // Propagate through the real AndroidView/Compose and Activity content parents.
            // Do not force-layout or separately size the Native root outside its owner.
            compose.requestLayout()
            decor.measure(View.MeasureSpec.makeMeasureSpec(right - left, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(bottom - top, View.MeasureSpec.EXACTLY))
            decor.layout(left, top, right, bottom)
        }

        /** Post-r36 prospective service, not extra startup/geometry ticks. The original
         * NestedFrame showed a detached popup and the original current-time looper drain
         * attached it; no new traversal, popup mount, or looper pass is introduced here.
         * ReaderRendererBusyIndicator.fadeIn uses tween(120ms), no size/infinite animation.
         * At the ORIGINAL 16ms cadence: ceil(120/16)=8 elapsed ticks, one transition
         * registration/apply tick, one start timestamp, then one fully-visible observer
         * and one token apply tick (NativeFrameHost's two LaunchedEffects): max 12.
         * The actual parent context has no duration-scale override. This extends only
         * witnessed attached-popup demand; it does not qualify or relabel r32 evidence.
         * No dispatcher time, timer service, sleep or until-idle pumping is permitted.
         * Robolectric's existing unpaused native vsync may auto-advance Android time
         * when a draw is scheduled; no platform-time freeze/equivalence is claimed.
         * No looper drain is added here, and the causal control requires the full
         * original coordinator snapshot and preparation custody to remain unchanged. */
        private fun synchronizeOriginalBusyPopupFrameDemand(popup: PopupWindow) {
            val maximumDemandedFrames = (120 + 16 - 1) / 16 + 2 + 2
            val originalDispatcherTime = compositionScheduler.currentTime
            val originalContent = popup.contentView
            var demandedFrames = 0
            while (compositionFrameClock.hasAwaiters && demandedFrames < maximumDemandedFrames) {
                assertSame(popup, originalShowingBusyPopup(), "C408.BusyAnimation.OriginalShowingPopupRetained")
                assertSame(originalContent, popup.contentView, "C408.BusyAnimation.OriginalPopupContentRetained")
                withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.CompositionFrame) {
                    commitCompositionFrame(requireOwnedDemand = true)
                }
                // Publish only mutations from that actual animation/observer frame before
                // asking for its next demand. In particular, the fully-visible token's
                // final apply must not be left to background snapshot timing.
                Snapshot.sendApplyNotifications()
                compositionScheduler.runCurrent()
                demandedFrames++
                busyEntranceDemandedFrameCount++
            }
            assertTrue(demandedFrames > 0, "C408.BusyAnimation.ObservedDemandActuallyServiced")
            assertFalse(compositionFrameClock.hasAwaiters, "C408.BusyAnimation.OwnedFrameDemandEndsWithinTweenBound")
            assertSame(popup, originalShowingBusyPopup(), "C408.BusyAnimation.NoSyntheticDismissalToReachIdle")
            assertTrue(originalDispatcherTime == compositionScheduler.currentTime,
                "C408.BusyAnimation.NoDelayOrCoordinatorTimerTimeAdvanced")
        }

        private fun settle(requireBusyAnimationDemand: Boolean = false, entry: SettleEntry = SettleEntry.Startup) {
            val entryLabel = when (entry) {
                SettleEntry.Startup -> "A407.Startup.CompositionIdle"
                SettleEntry.InitialIngress -> "A407.InitialIngress.CompositionIdle"
                SettleEntry.Refresh -> "C408.RootRefresh.CompositionIdle"
            }
            // Observe returns from the ORIGINAL work; do not add frame service, change
            // order/geometry, inspect slots/closures, or replace a callback to get a label.
            val before = compositionBoundaryLabel()
            // State delivery is a Compose frame, not a requestLayout or a guessed looper delay.
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.CompositionFrame) { commitCompositionFrame() }
            val firstFrame = compositionBoundaryLabel()
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.OuterLayout) { layoutOriginalWindowContent() }
            val outerLayout = compositionBoundaryLabel()
            // This frame consumes the command-created nested content. Its measurement
            // must follow construction, rather than precede the owning Engine child.
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.CompositionFrame) { commitCompositionFrame() }
            val nestedFrame = compositionBoundaryLabel()
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.MaterialLayout) { layoutOriginalMaterial() }
            val materialLayout = compositionBoundaryLabel()
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.LooperDrain) {
                shadowOf(Looper.getMainLooper()).idle() // Actual Engine configure/attachment callbacks only.
                val target = engine() as? ReaderEngineWebView
                if (target != null && !target.runtimeInitializationCompleted) {
                    assertTrue(target.url == ReaderWebRuntime.entrypointUrl,
                        "A407.EntrypointFinish.OriginalLoadedEntrypoint")
                    target.webViewClient.onPageFinished(target, target.url)
                    assertTrue(target.runtimeInitializationCompleted,
                        "A407.EntrypointFinish.OriginalCallbackInitializedRuntime")
                }
            }
            val configureReturn = compositionBoundaryLabel()
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.CompositionFrame) { commitCompositionFrame() }
            val configuredNestedFrame = compositionBoundaryLabel()
            // Configure/attachment can invalidate the nested Compose measurement. Give
            // that exact completed frame its real parent-bounded layout, not a JS size.
            withInitialGeometryBoundary(ReaderInitialGeometryWitnessBoundary.MaterialLayout) { layoutOriginalMaterial() }
            val boundaryLabels = "$entryLabel.BeforeSettle.$before.FirstFrame.$firstFrame" +
                ".OuterLayout.$outerLayout.NestedFrame.$nestedFrame.MaterialLayout.$materialLayout" +
                ".ConfigureReturn.$configureReturn.ConfiguredNestedFrame.$configuredNestedFrame" +
                ".FinalMaterialLayout.${compositionBoundaryLabel()}"
            val refreshLabels = if (entry == SettleEntry.Refresh)
                ".BeforeRevision.$refreshEntryWitness.RootPointer.$originalPointerWitness" else ""
            if (synchronizationProfile != SynchronizationProfile.OriginalThreeFrame) {
                val popup = originalShowingBusyPopup()
                if (requireBusyAnimationDemand) {
                    assertNotNull(popup,
                        "C408.BusyAnimation.OriginalRejectedPointerPopupShowing.$boundaryLabels$refreshLabels")
                    assertTrue(compositionFrameClock.hasAwaiters,
                        "C408.BusyAnimation.OriginalOwnedClockDemandObserved.$boundaryLabels$refreshLabels")
                }
                // r32's opt-in body remains quarantined. The distinct post-r36
                // profile must be qualified against the original pointer route and a
                // no-pointer/no-supplement sibling; neither invokes feedback or
                // writes visibility/token/preparation. Only attached original popup
                // demand can enter the finite source-duration service below.
                if (popup != null && compositionFrameClock.hasAwaiters) {
                    synchronizeOriginalBusyPopupFrameDemand(popup)
                }
            }
            assertEquals(Recomposer.State.Idle, recomposer.currentState.value,
                "$boundaryLabels$refreshLabels")
        }

        fun closeComposition() {
            if (compositionClosed) return
            compositionClosed = true
            compose.disposeComposition()
            shadowOf(Looper.getMainLooper()).idle() // Original nested-disposal queue, no elapsed-time guess.
        }

        fun close() {
            try { closeComposition() }
            finally {
                try {
                    // Lifecycle disposal must run while this fixture's context is still
                    // alive, including the original overlay's nested composition.
                    activity.pause().stop().destroy()
                    shadowOf(Looper.getMainLooper()).idle()
                } finally {
                    compose.compositionContext = null
                    recomposer.cancel()
                    recompositionRunner.cancel()
                    compositionScope.cancel()
                    compositionScheduler.runCurrent()
                    assertEquals(Recomposer.State.ShutDown, recomposer.currentState.value,
                        "A407.InitialIngress.FixtureRecomposerClosed")
                    assertTrue(recompositionRunner.isCompleted,
                        "A407.InitialIngress.FixtureRunnerClosed")
                }
            }
        }
    }
}

/** Controlled UNIT platform boundary, never Chromium/Foliate/GL geometry proof.
 * Source trace: Robolectric 4.16.1 DefaultSdkProvider selects android-all
 * 9-robolectric-4913185-2 for SDK 28. Its WebView.onMeasure DOES call super.onMeasure;
 * the retained measure shim forwards the original specs and is not a placement fix.
 * Its WebView.setFrame, however, delegates ONLY to WebViewProvider.ViewDelegate.
 * The super.setFrame call lives in WebView.PrivateAccess.super_setFrame, reached by
 * Chromium 69 WebViewChromium.setFrame, not directly by WebView.setFrame itself.
 * ShadowWebView.ensureProviderCreated installs ReflectionHelpers.createDeepProxy;
 * that delegate returns false without invoking PrivateAccess or assigning View bounds.
 * Thus the ORIGINAL AndroidViewHolder.onLayout -> stored view.layout -> setFrame
 * dispatch can leave width zero even with exact positive measurement and real bounds.
 * SDK-28-only UNIT forwarding restores that missing superclass placement call with
 * the ORIGINAL callback arguments. It is not a reader frame fallback or readiness ACK.
 * Prospective correction of the executed OriginalPlacedWidth RED; MAIN must rerun it.
 */
@Implements(WebView::class)
class Task407UnitWebViewMeasureBoundary : ShadowWebView() {
    @RealObject private lateinit var originalWebView: WebView
    var originalExactSpecsObserved = false
        private set

    @Implementation
    protected fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        originalExactSpecsObserved = View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY &&
            View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY
        reflector(OriginalViewPlatform::class.java, originalWebView).onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    @Implementation(minSdk = Build.VERSION_CODES.P, maxSdk = Build.VERSION_CODES.P)
    protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        reflector(OriginalViewPlatform::class.java, originalWebView).setFrame(left, top, right, bottom)

    @ForType(View::class)
    interface OriginalViewPlatform {
        @Direct fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
        @Direct fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean
    }
}
