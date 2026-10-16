package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.webkit.ValueCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowWebView
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector
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

/** Original Native allocation-source authentication with actual construction/executor and
 * synthetic bridge/test installation inputs. Not material generation or display proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderNativeInitialAllocationSourceHostTest {
    @Test fun originalQueryBasisRetainsExactCommandBindingLayoutAndDiesWithPhysicalInstallation() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            val resolved = assertIs<ReaderNativeInitialResolvedLayout>(f.capture())
            val basis = assertNotNull(resolved.captureBasis)
            assertEquals(f.resolvedQueryCommandId, basis.commandId)
            assertEquals(f.command().binding.destinationCommitIdentity, basis.destinationCommitIdentity)
            assertEquals(resolved.layout, basis.resolvedLayout)
            assertSame(assertNotNull(f.material()), resolved.scope.installation)
            assertTrue(resolved.sourceIsCurrent())
            f.call("closeReader"); f.drain()
            assertNull(f.material()?.initialResolvedLayout)
            assertFalse(resolved.sourceIsCurrent())
            assertNull(f.capture())
        } finally { f.dispose() }
    }

    @Test fun originalReporterConsumesGenuineInitialReceiptBeforeExecutorForwarding() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertTrue(f.versionsAtReceipt.single() == f.receipts.single().version,
                "Native must consume the same validated receipt, not await a Compose echo or mint a binding")
        } finally { f.dispose() }
    }
    @Test fun missingLayoutStillConsumesSemanticReceiptWithoutMaterialReadiness() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve(false)
            assertTrue(f.versionsAtReceipt.single() == f.receipts.single().version,
                "Absent layout cannot erase the genuine semantic receipt")
            assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun exactOriginalSourceAuthenticatesAfterSemanticRetirementWithoutClaimingDisplay() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            val physical = assertNotNull(f.material())
            assertFalse(physical.commandCurrency?.invoke() == true)
            assertNotNull(f.capture())
            assertNull(physical.binding); assertNull(physical.owner)
            assertEquals(ReaderLegacyResourceState.Registered, physical.state)
        } finally { f.dispose() }
    }
    @Test fun wrongTransitionCannotBorrowInitialAllocationSource() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            val command = f.command()
            assertNull(f.capture(command.copy(transitionId = command.transitionId.copy(sequence = 2L,
                parent = command.transitionId.parentIdentity()))))
        } finally { f.dispose() }
    }
    @Test fun wrongBindingCannotBorrowInitialAllocationSource() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            val command = f.command()
            assertNull(f.capture(command.copy(binding = command.binding.copy(viewportGeneration = command.binding.viewportGeneration + 1L))))
        } finally { f.dispose() }
    }
    @Test fun resizedNativePixelsCannotBorrowRetainedCssLayout() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.resize(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun replacedProfileIdentityCannotBorrowRetainedLayout() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.replaceProfileIdentity(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun replacedPublicationIdentityCannotBorrowRetainedLayout() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.replacePublicationIdentity(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun replacedViewportIdentityCannotBorrowRetainedLayout() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.replaceViewportIdentity(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun physicallyDetachedWrapperCannotBorrowOriginalAllocationSource() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.detachOriginalWrapper(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun closedCandidateCannotBorrowOriginalAllocationSource() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.closeCandidate(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun newerLiveSourceBeforeNativeEchoCannotBorrowInitialAllocationSource() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.postNewDestination(); assertNull(f.capture())
        } finally { f.dispose() }
    }
    @Test fun originalPhysicalRetirementCannotBorrowInitialAllocationSource() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertNotNull(f.capture()); f.call("closeReader"); f.drain(); assertNull(f.capture())
        } finally { f.dispose() }
    }

    @Test fun originalInitialAllocationQueriesLivePlanAndDoesNotManufactureReadiness() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            assertTrue(f.materialEvidence.isEmpty(), "Query submission cannot allocate material or publish readiness")
            f.returnPlan()
            assertEquals(2, f.materialEvidence.size)
            val profile = f.materialEvidence[0] as? ReaderTransitionFact.PaginationProfileReady
            val allocated = f.materialEvidence[1] as? ReaderTransitionFact.MaterialBindingAllocated
            assertNotNull(profile, "Original Curl profile must precede the strictly typed allocation callback")
            assertNotNull(allocated, "Exact original plan must allocate through the original material owners")
            assertEquals(f.command().transitionId, allocated.transitionId)
            assertTrue(profile.profileGeneration > 0L)
            assertEquals(profile.profileGeneration, allocated.allocation.allocatedBinding.profileGeneration)
            assertTrue(allocated.allocation.preparationGeneration > 0L)
            assertTrue(allocated.allocation.rasterGeneration > 0L)
            assertTrue(allocated.allocation.textureGeneration > 0L)
            val semantic = f.command().binding
            assertTrue(allocated.allocation.allocatedBinding.copy(profileGeneration = semantic.profileGeneration,
                preparationGeneration = null, rasterGeneration = null, textureGeneration = null) == semantic,
                "Physical generation allocation cannot replace the authentic semantic destination")
            val physical = assertNotNull(f.material())
            assertEquals(ReaderLegacyResourceState.Registered, physical.state)
            assertNull(physical.binding); assertNull(physical.owner)
        } finally { f.dispose() }
    }
    @Config(shadows = [Task407UnitWebViewMeasureBoundary::class])
    @Test fun originalInitialRasterWorkRetainsExactQueryBasisAfterAllocationAndFencesSourceChanges() = runTest {
        // Actual Native construction/allocation/preparation admission, with synthetic
        // query/plan inputs. Passive Ready, a receipt, display and pixels are not supplied.
        for (change in listOf("destination", "dimensions", "profile", "publication", "viewport", "detach", "candidate", "close", "retire")) {
            val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
            try {
                assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain()
                f.layoutOriginalRasterMaterial(); f.resolve()
                val original = assertIs<ReaderNativeInitialResolvedLayout>(f.capture())
                assertEquals(ReaderPortCommandResult.Accepted, f.allocate()); f.returnPlan()
                val work = f.prepareRaster()
                assertSame(original.captureBasis, work.initialCaptureBasis)
                assertTrue(work.isCurrent(), "An exact allocated command must not reuse retired semantic executor currency")
                assertFalse(assertNotNull(f.material()).commandCurrency?.invoke() == true)
                assertEquals(ReaderLegacyResourceState.Registered, assertNotNull(f.material()).state)
                when (change) {
                    "destination" -> f.postNewDestination()
                    "dimensions" -> f.resize()
                    "profile" -> f.replaceProfileIdentity()
                    "publication" -> f.replacePublicationIdentity()
                    "viewport" -> f.replaceViewportIdentity()
                    "detach" -> f.detachOriginalWrapper()
                    "candidate" -> f.closeCandidate()
                    "close" -> f.call("closeReader")
                    "retire" -> work.retire()
                }
                assertFalse(work.isCurrent(), change)
            } finally { f.dispose() }
        }
    }

    @Config(shadows = [Task407InitialPersistenceWebViewShadow::class])
    @Test fun originalInitialCurrentLiveDurabilityCrossesIoWithoutLosingItsLiveSource() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain()
            f.layoutOriginalRasterMaterial(); f.resolve()
            val basis = assertNotNull(assertIs<ReaderNativeInitialResolvedLayout>(f.capture()).captureBasis)
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate()); f.returnPlan()
            val work = f.prepareRaster()
            assertSame(basis, work.initialCaptureBasis)
            assertTrue(work.isCurrent())
            f.startOriginalCurrentLivePersistence()
            val progressed = withContext(Dispatchers.Default) {
                withTimeout(5_000L) { f.currentLiveEndpoint.await() }
            }
            assertTrue(progressed, "R118.CurrentLive.RealDurableMustAdvanceToFirstOffscreen")
            assertEquals(1, f.persistenceShadow().manifestRequests)
            assertTrue(work.isCurrent())
            assertSame(basis, work.initialCaptureBasis)
            assertFalse(f.materialFacts.any { it is ReaderTransitionFact.RasterProven || it is ReaderTransitionFact.RasterFailed })
        } finally { f.dispose() }
    }

    @Config(shadows = [Task407InitialPersistenceWebViewShadow::class])
    @Test fun originalInitialCurrentLiveWarmStorePersistsBeforeOneOffscreenRequest() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            val work = f.prepareOriginalInitialWork()
            f.warmOriginalPersistence(work)
            f.startOriginalCurrentLivePersistence()
            assertTrue(withContext(Dispatchers.Default) { withTimeout(5_000L) { f.currentLiveEndpoint.await() } },
                "R118.CurrentLive.WarmRealDurableMustAdvance")
            assertEquals(1, f.persistenceShadow().manifestRequests)
            assertTrue(f.bundle().rasterCacheMetrics().diskEntries > 0)
            assertTrue(work.isCurrent())
            assertFalse(f.materialFacts.any { it is ReaderTransitionFact.RasterProven || it is ReaderTransitionFact.RasterFailed })
        } finally { f.dispose() }
    }

    @Config(shadows = [Task407InitialPersistenceWebViewShadow::class])
    @Test fun originalInitialWorkerRejectsRevokedSourceAndExactStaleCommandBeforeIo() = runTest {
        for (change in listOf("destination", "dimensions", "profile", "publication", "viewport", "detach", "candidate", "retire", "command")) {
            val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
            val io = Task407OriginalInitializationWorker()
            try {
                f.controlOriginalInitialization(io)
                val work = f.prepareOriginalInitialWork()
                val originalShadow = f.persistenceShadow()
                f.startOriginalCurrentLivePersistence()
                withContext(Dispatchers.Default) { withTimeout(5_000L) { io.queued.await() } }
                when (change) {
                    "destination" -> f.postNewDestination()
                    "dimensions" -> f.resize()
                    "profile" -> f.replaceProfileIdentity()
                    "publication" -> f.replacePublicationIdentity()
                    "viewport" -> f.replaceViewportIdentity()
                    "detach" -> f.detachOriginalWrapper()
                    "candidate" -> f.closeCandidate()
                    "retire" -> work.retire()
                    "command" -> f.materialCommandCurrent = false
                }
                assertFalse(work.isCurrent(), "R118.MainSourceRevoked.$change")
                val workerCurrent = withContext(Dispatchers.IO) { work.isCurrent() }
                assertFalse(workerCurrent, "R118.WorkerCannotReviveRevokedSource.$change")
                io.returnOriginalWorker()
                f.drain()
                assertTrue(io.returnedOffMain)
                assertEquals(0, originalShadow.manifestRequests)
                assertFalse(f.materialFacts.any { it is ReaderTransitionFact.RasterProven })
                assertEquals(0, f.bundle().ownershipMetrics().stagedPublications)
            } finally {
                io.returnIfQueued()
                f.dispose()
            }
        }
    }

    @Config(shadows = [Task407InitialPersistenceWebViewShadow::class])
    @Test fun originalInitialOutstandingWorkerCancellationAndCloseDoNotFabricatePhysicalReturn() = runTest {
        for (close in listOf(false, true)) {
            val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
            val io = Task407OriginalInitializationWorker()
            try {
                f.controlOriginalInitialization(io)
                val work = f.prepareOriginalInitialWork()
                val originalShadow = f.persistenceShadow()
                f.startOriginalCurrentLivePersistence()
                withContext(Dispatchers.Default) { withTimeout(5_000L) { io.queued.await() } }
                var returns = 0
                assertEquals(ReaderPortCommandResult.Accepted, f.releaseOriginal(work) { returns++ })
                if (close) f.call("closeReader")
                f.drain()
                assertEquals(0, returns, "R118.LogicalCancelIsNotOriginalWorkerReturn")
                assertFalse(withContext(Dispatchers.IO) { work.isCurrent() })
                io.returnOriginalWorker()
                f.drain()
                assertTrue(io.returnedOffMain)
                assertEquals(1, returns, "R118.ExactOriginalWorkerReturnsOnce")
                f.drain(); assertEquals(1, returns)
                assertEquals(0, originalShadow.manifestRequests)
                assertFalse(f.materialFacts.any { it is ReaderTransitionFact.RasterProven })
            } finally {
                io.returnIfQueued()
                f.dispose()
            }
        }
    }

    @Config(shadows = [Task407InitialPersistenceWebViewShadow::class])
    @Test fun originalInitialPostWriteSourceInvalidationRollsBackBeforeDurable() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            val work = f.prepareOriginalInitialWork()
            f.warmOriginalPersistence(work)
            val baseline = f.bundle().rasterCacheMetrics().diskEntries
            val paused = f.pauseOriginalPublication()
            f.startOriginalCurrentLivePersistence()
            withContext(Dispatchers.Default) { withTimeout(5_000L) { paused.await() } }
            assertTrue(f.bundle().rasterCacheMetrics().diskEntries > baseline,
                "R118.OriginalIoWritePrecedesHeldPublicationCompletion")
            f.postNewDestination()
            // Do not sample work.isCurrent here: the original main publication
            // completion must itself discover the stale physical/semantic source.
            assertTrue(work.installationIsCurrent())
            assertTrue(f.releaseOriginalPublication())
            f.drain()
            assertFalse(withContext(Dispatchers.IO) { work.isCurrent() },
                "R118.MainPublicationRevokesOriginalSourceWithoutTestSampling")
            assertFalse(work.isCurrent(), "R118.MainPublicationRevalidatesOriginalSource")
            val returned = CompletableDeferred<Unit>()
            assertEquals(ReaderPortCommandResult.Accepted, f.releaseOriginal(work) { returned.complete(Unit) })
            withContext(Dispatchers.Default) { withTimeout(5_000L) { returned.await() } }
            assertEquals(baseline, f.bundle().rasterCacheMetrics().diskEntries,
                "R118.StaleOriginalReceiptRolledBackBeforePhysicalReturn")
            assertEquals(0, f.persistenceShadow().manifestRequests)
            assertFalse(f.materialFacts.any { it is ReaderTransitionFact.RasterProven })
        } finally { f.dispose() }
    }

    @Test fun originalInitialAllocationCannotRunBeforeOriginalOwnerFinalization() = runTest {
        val f = ConstructionFixture(testScheduler)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertTrue(f.allocate() is ReaderPortCommandResult.Rejected)
            assertTrue(f.materialEvidence.isEmpty())
        } finally { f.dispose() }
    }
    @Test fun retiredCommandCannotPublishLateOriginalPlanAllocation() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            f.materialCommandCurrent = false
            f.returnPlan()
            assertTrue(f.materialEvidence.isEmpty(), "A returned query owns release only after exact command retirement")
        } finally { f.dispose() }
    }
    @Test fun newerLiveDestinationCannotPublishLateOriginalPlanAllocation() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            val returned = f.planCallback()
            f.postNewDestination()
            f.returnPlan(callback = returned)
            assertTrue(f.materialEvidence.isEmpty(), "The actual Engine source, not a retained metadata copy, fences publication")
        } finally { f.dispose() }
    }
    @Test fun chapterLocalInitialLayoutQualifiesItsMappedGlobalRasterPlan() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve(globalPageIndex = 12, sectionIndex = 2)
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            f.returnPlan(globalStart = 12, totalPages = 40, sectionIndex = 2)
            assertTrue(f.materialEvidence.lastOrNull() is ReaderTransitionFact.MaterialBindingAllocated,
                "Chapter-local layout must use the plan's genuine chapter mapping, not global page-count equality")
        } finally { f.dispose() }
    }
    @Test fun wrongChapterMappingRejectsOriginalInitialMaterialWithoutReadiness() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            f.returnPlan(chapterPageCount = 7)
            assertTrue(f.materialEvidence.singleOrNull() is ReaderTransitionFact.CommandRejected,
                "An incompatible original plan must reject the allocation, not synthesize its material")
        } finally { f.dispose() }
    }

    @Test fun missingCurrentLiveLayoutCannotQualifyAnOtherwisePlausibleRasterPlan() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            f.returnPlan(layoutGeneration = null)
            assertTrue(f.materialEvidence.singleOrNull() is ReaderTransitionFact.CommandRejected,
                "A fallback plan cannot substitute for a still-current original Live layout")
        } finally { f.dispose() }
    }
    @Test fun changedLiveLayoutCannotQualifyRetainedInitialNumericCustody() = runTest {
        val f = ConstructionFixture(testScheduler, finalizeMaterialOwners = true)
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start()); f.drain(); f.resolve()
            assertEquals(ReaderPortCommandResult.Accepted, f.allocate())
            f.returnPlan(layoutGeneration = 8L)
            assertTrue(f.materialEvidence.singleOrNull() is ReaderTransitionFact.CommandRejected,
                "Equal geometry and chapter counts cannot resurrect an obsolete original layout")
        } finally { f.dispose() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class ConstructionFixture(private val scheduler: TestCoroutineScheduler,
        finalizeMaterialOwners: Boolean = false) {
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
        val versionsAtReceipt = mutableListOf<ReaderPresentationReceiptVersion?>()
        val materialFacts = mutableListOf<ReaderTransitionFact>()
        val currentLiveEndpoint = CompletableDeferred<Boolean>()
        private val syntheticDescriptorIdentity = java.util.UUID.randomUUID().toString()
        val materialEvidence get() = materialFacts.filter {
            it is ReaderTransitionFact.PaginationProfileReady || it is ReaderTransitionFact.MaterialBindingAllocated ||
                it is ReaderTransitionFact.CommandRejected
        }
        var materialCommandCurrent = true
        var resolvedQueryCommandId: String? = null; private set
        private var allocationPort: ReaderMaterialGenerationAllocationPort? = null
        private val confirmedMaterialOwners = mutableSetOf<ReaderLegacyPhysicalIdentity>()
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
            if (finalizeMaterialOwners) {
                assertEquals(ReaderPortCommandResult.Accepted, call("freezeOriginalPhysicalInventoryOwners", domain, executor))
                @Suppress("UNCHECKED_CAST")
                val sources = call("snapshotOriginalPhysicalInventoryOwners", domain, executor) as? List<ReaderLegacyConnectedSourceInventory>
                assertNotNull(sources, "Actual original Native sources must be connected before finalization")
                assertEquals(14, sources.size)
                assertEquals(14, sources.map { it.source }.toSet().size)
                // Original framework removal discovers additional settled return rows.
                // Drain each exact identity once, then rediscover before finalization.
                var inventory = assertNotNull(sources)
                for (round in 0 until 4) {
                    val pending = inventory.flatMap { it.resources }.filter {
                        it.physicalIdentity !in confirmedMaterialOwners
                    }
                    if (pending.isEmpty()) break
                    for (row in pending) {
                        assertEquals(ReaderPortCommandResult.Accepted,
                            call("drainOriginalPhysicalInventoryOwner", row.physicalIdentity, executor,
                                { identity: ReaderLegacyPhysicalIdentity -> confirmedMaterialOwners += identity; Unit }))
                    }
                    drain()
                    @Suppress("UNCHECKED_CAST")
                    val refreshed = call("snapshotOriginalPhysicalInventoryOwners", domain, executor)
                        as? List<ReaderLegacyConnectedSourceInventory>
                    inventory = assertNotNull(refreshed, "All original sources must remain connected during retirement")
                }
                assertTrue(inventory.flatMap { it.resources }.all {
                    it.physicalIdentity in confirmedMaterialOwners && it.state == ReaderLegacyResourceState.Released
                }, "Original constructor and late physical-return custody must actually retire before finalization")
            }
            candidate = checkNotNull(gateway.reserveActivatedCandidate(session, 6L, store, {}, {}, registry::register,
                registry::discardCandidateReservations, executor::clear))
            reservation = checkNotNull(registry.reserveNeutralBootstrap(domain, candidate) { o, c, cb ->
                body?.invoke(o, c, cb) ?: ReaderSemanticExecutableResult.Rejected
            })
            val decision = ReaderInitialActivationDecision(ReaderInitialCommittedPresentationOrigin.Neutral(session, 6L,
                ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly), null, null, reservation)
            if (finalizeMaterialOwners) assertEquals(ReaderPortCommandResult.Accepted,
                call("finalizeOriginalPhysicalInventoryOwners", domain, executor, null, confirmedMaterialOwners))
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
                { registration = it; it.bindAsyncOutcome { registration.retire() } }, { receipt ->
                    receipts += receipt
                    versionsAtReceipt += (field("presentationBindingReporter") as ReaderPresentationBindingReporter)
                        .captureSemanticContextVersionOrNull(assertNotNull(receipt.semanticReceipt).binding)
                })
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
        fun post(message: JsonObject) {
            (shadowOf(engine()).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as ReaderJavascriptBridge).postMessage(message.toString()); drain()
        }
        // Same controlled UNIT driver as the normal Root fixture: request through
        // Compose's original interop nodes, then place the stored original Engine.
        // The SDK-28 provider shadow forwards original View callbacks, not reader readiness.
        fun layoutOriginalRasterMaterial() {
            val material = assertNotNull(material(), "R407.Layout.OriginalMaterial")
            val content = material.wrapper
            assertTrue(content === wrapper && content is ComposeView, "R407.Layout.OriginalComposeWrapper")
            val parent = assertNotNull(content.parent as? ViewGroup, "R407.Layout.OriginalMaterialParent")
            assertTrue(parent === field("viewerContentContainer") && parent.isAttachedToWindow &&
                parent.width > 0 && parent.height > 0, "R407.Layout.OriginalParentHasPhysicalBounds")
            fun requestOriginalMeasurement(view: View) {
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    requestOriginalMeasurement(view.getChildAt(index))
                }
                view.requestLayout()
            }
            requestOriginalMeasurement(content)
            content.measure(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.EXACTLY))
            content.layout(0, 0, parent.width, parent.height)
            val target = engine()
            val holder = assertNotNull(target.parent as? ViewGroup, "R407.Layout.OriginalHolder")
            val handler = assertNotNull(holder.parent as? ViewGroup, "R407.Layout.OriginalHandler")
            val owner = assertNotNull(handler.parent as? ViewGroup, "R407.Layout.OriginalComposeOwner")
            assertTrue(owner.parent === content && holder.javaClass.simpleName == "ViewFactoryHolder" &&
                handler.javaClass.simpleName == "AndroidViewsHandler" && owner.javaClass.simpleName == "AndroidComposeView",
                "R407.Layout.OriginalInteropHierarchy")
            assertTrue(target.isAttachedToWindow, "R407.Layout.OriginalEngineAttached")
            assertTrue(holder.width > 0 && holder.height > 0 && holder.width == holder.measuredWidth &&
                holder.height == holder.measuredHeight, "R407.Layout.OriginalHolderMeasuredAndPlaced")
            val measured = shadowOf(target)
            assertTrue(when (measured) {
                is Task407UnitWebViewMeasureBoundary -> measured.originalExactSpecsObserved
                is Task407InitialPersistenceWebViewShadow -> measured.originalExactSpecsObserved
                else -> false
            }, "R407.Layout.OriginalExactMeasureSpecs")
            assertTrue(target.measuredWidth == holder.measuredWidth && target.measuredHeight == holder.measuredHeight,
                "R407.Layout.OriginalEngineMeasuredByHolder")
            val originalHolder = holder.javaClass.superclass
            assertTrue(originalHolder.simpleName == "AndroidViewHolder", "R407.Layout.OriginalPlacementCallbackOwner")
            val originalChild = originalHolder.getDeclaredField("view").apply { isAccessible = true }.get(holder)
            assertTrue(originalChild === target && holder.childCount == 1 && holder.getChildAt(0) === originalChild,
                "R407.Layout.OriginalStoredEngine")
            originalHolder.getDeclaredMethod("onLayout", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(holder, false, holder.left, holder.top, holder.right, holder.bottom)
            assertTrue(target.parent === holder && target.width == holder.width && target.height == holder.height,
                "R407.Layout.OriginalEnginePlacedByHolder")
        }
        fun resolve(includeLayout: Boolean = true, globalPageIndex: Int = 0, sectionIndex: Int = 0) {
            val target = engine()
            target.webViewClient.onPageFinished(target, ReaderWebRuntime.entrypointUrl)
            drain()
            post(buildJsonObject { put("type", "ready") })
            val opening = pending().pendingCommands.first()
            assertTrue(opening.dispatch.command is ReaderBridgeCommand.OpenPublication)
            post(buildJsonObject { put("type", "commandAck"); put("commandId", opening.dispatch.id) })
            val query = pending().pendingCommands.first().dispatch
            resolvedQueryCommandId = query.id
            val command = query.command as ReaderBridgeCommand.ResolveCurrentDestination
            post(buildJsonObject { put("type", "currentDestinationResolved"); put("commandId", query.id)
                put("foliateSessionId", command.foliateSessionId); put("destinationFoliateSessionId", command.foliateSessionId)
                put("destinationCommitSequence", 1L); put("pageIndex", globalPageIndex)
                if (includeLayout) put("resolvedLayout", buildJsonObject {
                    put("layoutGeneration", 7L); put("viewGeneration", 3L); put("navigationGeneration", 5L)
                    put("sectionIndex", sectionIndex); put("pageIndex", 0); put("pageCount", 8)
                    put("width", 560.0); put("height", 420.0)
                }) })
        }
        fun command(): ReaderTransitionCommand.AllocateMaterialBinding {
            val receipt = receipts.single()
            val semantic = assertNotNull(receipt.semanticReceipt)
            return ReaderTransitionCommand.AllocateMaterialBinding(assertNotNull(semantic.transitionId), semantic.binding)
        }
        fun capture(command: ReaderTransitionCommand.AllocateMaterialBinding = command()): Any? {
            val method = type.declaredMethods.singleOrNull {
                it.name == "captureInitialResolvedLayoutForAllocation" && it.parameterCount == 2
            }
            assertNotNull(method, "Original Native must authenticate exact physical/layout source before material allocation")
            return method.apply { isAccessible = true }.invoke(native, candidate, command)
        }
        fun allocate(): ReaderPortCommandResult {
            val port = allocationPort ?: run {
                val factory = type.declaredMethods.singleOrNull {
                    it.name == "createOriginalInitialMaterialAllocationPort" && it.parameterCount == 3
                }
                assertNotNull(factory, "Actual Native allocation must use the original Curl, preparation, and bundle owners")
                val currency: (ReaderTransitionCommand.AllocateMaterialBinding) -> Boolean = {
                    materialCommandCurrent && it == command()
                }
                val enqueue: (ReaderTransitionFact) -> Unit = { materialFacts += it }
                (factory.apply { isAccessible = true }.invoke(native, candidate, currency, enqueue)
                    as ReaderMaterialGenerationAllocationPort).also { allocationPort = it }
            }
            return port.allocate(command()) { materialFacts += it }
        }
        fun prepareRaster(): ReaderActivatedRasterWork {
            val allocation = (materialEvidence.last() as ReaderTransitionFact.MaterialBindingAllocated).allocation
            val command = ReaderTransitionCommand.RequestRasterPreparation(allocation.transitionId,
                allocation.allocatedBinding, allocation)
            val readPublication: () -> ReaderOwnerAndInputPublicationSnapshot? = { null }
            val current = { candidate.isSelected && store.snapshot?.commandEgressOpen == true && materialCommandCurrent }
            assertEquals(ReaderPortCommandResult.Accepted, call("bindInstalledPublicationReaders", readPublication, current))
            val currency: (ReaderRasterPreparationLease) -> Boolean = { lease ->
                current() && lease.transitionId == command.transitionId && lease.binding == command.binding && lease.allocation == allocation
            }
            val facts: (ReaderTransitionFact) -> Unit = {
                materialFacts += it
                if (it is ReaderTransitionFact.RasterFailed) currentLiveEndpoint.complete(false)
            }
            val original = assertNotNull(material()?.initialResolvedLayout, "R407.Raster.InitialLayoutPresent")
            assertTrue(original === capture(), "R407.Raster.ExactSourceCurrentBeforePreparation")
            assertTrue(original.scope.installedSnapshot === store.snapshot, "R407.Raster.ExactInstalledSnapshotBeforePreparation")
            assertTrue(current(), "R407.Raster.InstalledCommandCurrentBeforePreparation")
            assertTrue(original.binding == allocation.requestedBinding, "R407.Raster.ExactRequestedBindingBeforePreparation")
            assertTrue(original.transitionId == command.transitionId, "R407.Raster.ExactTransitionBeforePreparation")
            assertTrue(original.captureBasis?.destinationCommitIdentity == command.binding.destinationCommitIdentity,
                "R407.Raster.ExactCaptureDestinationBeforePreparation")
            val target = engine()
            assertTrue(target.width > 0 && target.height > 0, "R407.Raster.PositiveEngineSizeBeforePreparation")
            val controller = field("pageRasterPreparationController") as ReaderPageRasterPreparationController
            assertTrue(domain == controller.activatedMaterialDomain, "R407.Raster.ExactPreparationDomainBeforePreparation")
            val result = call("prepareInstalledRaster", command, domain, currency, facts)
            assertTrue(original.scope.installedSnapshot === store.snapshot, "R407.Raster.ExactInstalledSnapshotAfterPreparation")
            assertTrue(original === material()?.initialResolvedLayout, "R407.Raster.ExactInitialLayoutAfterPreparation")
            assertTrue(current(), "R407.Raster.InstalledCommandCurrentAfterPreparation")
            assertTrue(native.width == original.scope.width && native.height == original.scope.height,
                "R407.Raster.ExactNativeSizeAfterPreparation")
            assertTrue(target.width > 0 && target.height > 0, "R407.Raster.PositiveEngineSizeAfterPreparation")
            assertTrue(original.sourceIsCurrent(), "R407.Raster.EngineLayoutSourceCurrentAfterPreparation")
            assertTrue(original === capture(), "R407.Raster.ExactSourceCurrentAfterPreparation")
            assertEquals(ReaderPortCommandResult.Accepted, result)
            val key = ReaderTransitionResourceKey(command.transitionId, ReaderTransitionResourceKind.Raster, allocation.rasterGeneration)
            return assertNotNull(controller.installedWorkFor(key))
        }
        fun planCallback(): android.webkit.ValueCallback<String> {
            val source = shadowOf(engine())
            assertTrue(source.lastEvaluatedJavascript?.contains("pageTurnRasterPreparationPlan") == true,
                "Original material producer must query the actual retained Engine")
            return assertNotNull(source.lastEvaluatedJavascriptCallback)
        }
        fun returnPlan(globalStart: Int = 0, totalPages: Int = 8, chapterPageCount: Int = 8,
            sectionIndex: Int = 0, layoutGeneration: Long? = 7L,
            callback: android.webkit.ValueCallback<String> = planCallback()) {
            val plan = buildJsonObject {
                // The synthetic original-plan response must include the geometry
                // required for raster admission, not only allocation's layout metadata.
                put("captureGeometry", buildJsonObject {
                    put("viewportWidth", 560.0); put("viewportHeight", 420.0); put("mode", "single")
                    put("pages", buildJsonArray { add(buildJsonObject {
                        put("role", "full"); put("left", 0.0); put("top", 0.0)
                        put("width", 560.0); put("height", 420.0)
                    }) })
                })
                if (layoutGeneration != null) put("resolvedLayout", buildJsonObject {
                    put("layoutGeneration", layoutGeneration); put("viewGeneration", 3L); put("navigationGeneration", 5L)
                    put("sectionIndex", sectionIndex); put("pageIndex", 0); put("pageCount", 8)
                    put("width", 560.0); put("height", 420.0)
                })
                put("context", buildJsonObject {
                    put("centerPageIndex", globalStart); put("pageCount", totalPages); put("layoutMode", "single")
                    put("readerDirection", "ltr"); put("step", 1); put("currentChapterIndex", sectionIndex)
                    put("currentChapterPageStartIndex", globalStart); put("currentChapterPageCount", chapterPageCount)
                })
                // Match the original JS producer's single-page priority order: the
                // complete clipped blocking window, then unseen current-chapter pages.
                val targets = buildList {
                    add(globalStart to "current")
                    add(globalStart + 1 to "next-transition")
                    add(globalStart - 1 to "previous-transition")
                    for (offset in 2..5) add(globalStart + offset to "next-lookahead")
                    for (offset in 2..5) add(globalStart - offset to "previous-lookahead")
                    for (page in globalStart until globalStart + chapterPageCount) add(page to "current-chapter")
                }.filter { (page, _) -> page in 0 until totalPages }.distinctBy { (page, _) -> page }
                put("targets", buildJsonArray {
                    for ((page, priority) in targets) add(buildJsonObject {
                        put("pageIndex", page)
                        put("authority", if (page == globalStart) "CurrentLive" else "OffscreenPassive")
                        put("priority", priority)
                    })
                })
            }
            callback.onReceiveValue(JsonPrimitive(plan.toString()).toString()); drain()
        }
        fun bundle() = field("pageTurnBundleSource") as ReaderPageTurnBundleSource
        fun prepareOriginalInitialWork(): ReaderActivatedRasterWork {
            assertEquals(ReaderSemanticCommandResult.Accepted, start()); drain()
            layoutOriginalRasterMaterial(); resolve()
            assertEquals(ReaderPortCommandResult.Accepted, allocate()); returnPlan()
            return prepareRaster().also { assertTrue(it.isCurrent()) }
        }
        fun controlOriginalInitialization(dispatcher: CoroutineDispatcher) {
            ReaderPageTurnBundleSource::class.java.getDeclaredField("rasterInitializationDispatcher")
                .apply { isAccessible = true }.set(bundle(), dispatcher)
        }
        suspend fun warmOriginalPersistence(work: ReaderActivatedRasterWork) {
            // Invoke the original installed initializer, never install a cache/store or Durable.
            suspendCoroutine<Any> { continuation ->
                val method = ReaderPageTurnBundleSource::class.java.declaredMethods.single {
                    it.name == "rasterScheduler" && it.parameterCount == 4
                }.apply { isAccessible = true }
                val result = method.invoke(bundle(), engine(), null, work, continuation)
                if (result !== COROUTINE_SUSPENDED) continuation.resume(result)
            }
        }
        fun releaseOriginal(work: ReaderActivatedRasterWork, returned: () -> Unit) =
            (field("pageRasterPreparationController") as ReaderPageRasterPreparationController).releaseInstalled(work, returned)
        fun pauseOriginalPublication(): CompletableDeferred<Unit> {
            val paused = CompletableDeferred<Unit>()
            val registry = field("qaFaultRegistry") as ReaderPageQaFaultRegistry
            val sink = ReaderPageQaFaultRegistry::class.java.getDeclaredField("eventSink").apply { isAccessible = true }
            val original = sink.get(registry) as ReaderPageQaFaultEventSink
            sink.set(registry, ReaderPageQaFaultEventSink { event ->
                original.emit(event)
                if (event.ticket.fault == ReaderPageQaFault.PauseNextPublication && event.state == ReaderPageQaFaultState.Applied)
                    paused.complete(Unit)
            })
            assertTrue(registry.enqueue("r118-publication-pause", ReaderPageQaFault.PauseNextPublication))
            return paused
        }
        fun releaseOriginalPublication() = (field("qaFaultRegistry") as ReaderPageQaFaultRegistry)
            .releasePublication("r118-publication-return")
        fun persistenceShadow() = shadowOf(engine()) as Task407InitialPersistenceWebViewShadow
        fun startOriginalCurrentLivePersistence() {
            val shadow = persistenceShadow()
            shadow.onFirstManifest = { currentLiveEndpoint.complete(true) }
            val adapter = field("passiveRasterPreparationAdapter") as ReaderPassiveRasterPreparationAdapter
            fun member(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name)
                .apply { isAccessible = true }.get(owner)
            val runtime = member(member(adapter, "session"), "runtime")
            val passive = member(runtime, "webView") as WebView
            passive.webViewClient.onPageFinished(passive, passive.url)
            val passiveShadow = shadowOf(passive)
            assertTrue(passiveShadow.lastEvaluatedJavascript?.contains("NavicPassiveRasterPrototype?.ready") == true)
            assertNotNull(passiveShadow.lastEvaluatedJavascriptCallback).onReceiveValue("true")
            drain()
            assertEquals(1, shadow.visualReturns, "R118.CurrentLive.OriginalVisualStateReturned")
            assertTrue(shadow.syntheticDraws > 0, "R118.CurrentLive.OriginalDrawCaptured")
            assertTrue(shadow.lastEvaluatedJavascript?.contains("pageTurnRasterDescriptor") == true,
                "R118.CurrentLive.OriginalDescriptorRequested")
            assertEquals(0, shadow.manifestRequests, "CurrentLive must precede every offscreen request")
            val descriptor = buildJsonObject {
                put("publicationUrl", "public-synthetic-r118-$syntheticDescriptorIdentity")
                put("paginationFingerprint", "pagination-r118"); put("layoutFingerprint", "layout-r118")
                put("decorationFingerprint", "decoration-r118")
                put("viewportWidth", 560); put("viewportHeight", 420); put("pageCount", 8)
                put("spineIndex", 0); put("href", "fixture-r118"); put("chapterPageIndex", 0)
                put("chapterPageCount", 8); put("visualPageOrdinal", 0)
            }
            assertNotNull(shadow.lastEvaluatedJavascriptCallback).onReceiveValue(descriptor.toString())
            drain()
        }
        fun resize() { native.layout(0, 0, native.width + 1, native.height) }
        fun detachOriginalWrapper() {
            val lease = assertNotNull(material()); (lease.wrapper.parent as ViewGroup).removeView(lease.wrapper)
        }
        fun replaceProfileIdentity() { type.getDeclaredField("rasterProfileEpoch").apply { isAccessible = true }.set(native, 9L) }
        fun replacePublicationIdentity() { type.getDeclaredField("presentationPublicationGeneration").apply { isAccessible = true }
            .let { it.setLong(native, it.getLong(native) + 1L) } }
        fun replaceViewportIdentity() { type.getDeclaredField("presentationViewportGeneration").apply { isAccessible = true }
            .let { it.setLong(native, it.getLong(native) + 1L) } }
        fun closeCandidate() { candidate.close() }
        fun postNewDestination() {
            val sessionId = command().binding.foliateSessionId
            post(buildJsonObject { put("type", "locationChanged"); put("foliateSessionId", sessionId)
                put("destinationFoliateSessionId", sessionId); put("destinationCommitSequence", 2L); put("pageIndex", 1) })
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

/** Hold the original queued initializer without running IO on main or blocking a worker
 * on main. Returning it executes that exact dispatched Runnable on real Dispatchers.IO. */
internal class Task407OriginalInitializationWorker : CoroutineDispatcher() {
    private val gateNext = AtomicBoolean(true)
    private val pending = CompletableDeferred<Pair<CoroutineContext, Runnable>>()
    val queued = CompletableDeferred<Unit>()
    var returnedOffMain = false; private set
    private var returned = false
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (gateNext.compareAndSet(true, false)) {
            check(pending.complete(context to block))
            queued.complete(Unit)
        } else Dispatchers.IO.dispatch(context, block)
    }
    suspend fun returnOriginalWorker() {
        check(!returned)
        returned = true
        val (_, block) = pending.await()
        withContext(Dispatchers.IO) {
            check(Looper.myLooper() != Looper.getMainLooper())
            returnedOffMain = true
            block.run()
        }
    }
    suspend fun returnIfQueued() {
        if (queued.isCompleted && !returned) returnOriginalWorker()
    }
}

/** Framework-only unit inputs. The real Native work, capture, cache, descriptor owner,
 * CurrentLive adapter and IO store remain in the call chain. No Durable/ACK is supplied. */
@Implements(WebView::class)
internal class Task407InitialPersistenceWebViewShadow : ShadowWebView() {
    @RealObject private lateinit var original: WebView
    var originalExactSpecsObserved = false; private set
    var visualReturns = 0; private set
    var syntheticDraws = 0; private set
    var manifestRequests = 0; private set
    var onFirstManifest: (() -> Unit)? = null

    @Implementation protected fun onMeasure(width: Int, height: Int) {
        originalExactSpecsObserved = View.MeasureSpec.getMode(width) == View.MeasureSpec.EXACTLY &&
            View.MeasureSpec.getMode(height) == View.MeasureSpec.EXACTLY
        reflector(OriginalViewPlatform::class.java, original).onMeasure(width, height)
    }
    @Implementation(minSdk = Build.VERSION_CODES.P, maxSdk = Build.VERSION_CODES.P)
    protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        reflector(OriginalViewPlatform::class.java, original).setFrame(left, top, right, bottom)
    @Implementation fun postVisualStateCallback(requestId: Long, callback: WebView.VisualStateCallback) {
        original.post { visualReturns++; callback.onComplete(requestId) }
    }
    @Implementation override fun draw(canvas: Canvas) {
        syntheticDraws++
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        for (row in 0..12) canvas.drawRect(8f, 8f + row * 24f, original.width * .8f, 20f + row * 24f, paint)
    }
    @Implementation override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
        super.evaluateJavascript(script, callback)
        if (script.contains("pageTurnPassiveRasterManifestInputs")) {
            manifestRequests++
            onFirstManifest?.invoke()
            // The first offscreen request remains unresolved; this is not RasterProven.
        }
    }
    @ForType(View::class) interface OriginalViewPlatform {
        @Direct fun onMeasure(width: Int, height: Int)
        @Direct fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean
    }
}
