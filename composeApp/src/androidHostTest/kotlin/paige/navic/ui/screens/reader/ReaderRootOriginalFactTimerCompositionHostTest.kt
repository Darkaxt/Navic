package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual Root construction and its original Bridge timer. No publication/view content,
 * Foliate bridge success, complete port package, layout, or presented pixels are injected. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderRootOriginalFactTimerCompositionHostTest {
    @Test fun actualRootBorrowsOriginalBridgeTimerAndFinalizesItsExactDomain() = runTest {
        val f = Fixture(testScheduler)
        try {
            val timer = f.timer()
            assertSame<ReaderTask6FactOnlyTimerPort>(f.bridge.factOnlyTimerPort, timer)
            assertNull(timer.bindBeforeWork(f.id) {})
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            assertTrue(f.bridge.snapshotFrozenOwnership().isEmpty())
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain.copy(freezeToken = ReaderLegacyFreezeToken(71L))))
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            val expired = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
            val registration = assertNotNull(timer.bindBeforeWork(f.id, expired::add))
            assertEquals(f.domain, registration.physicalIdentity.domain)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(listOf(ReaderTransitionFact.DeadlineExpired(f.id)), expired)
            assertSame<ReaderTask6FactOnlyTimerPort>(timer, f.timer())
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
        } finally { f.dispose() }
    }

    @Test fun actualRootCloseFencesOrdinaryWorkWithoutErasingPermanentCloseBudget() = runTest {
        val f = Fixture(testScheduler)
        try {
            val timer = f.timer()
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            val key = ReaderReleaseOnlyCleanupKey(17L, 19L, ReaderReleaseOnlyCleanupId(73L), ReaderReleaseOnlyCleanupGeneration(79L))
            val elapsed = mutableListOf<ReaderReleaseOnlyCleanupKey>()
            val close = assertNotNull(timer.bindReleaseOnlyCloseBudget(key, elapsed::add))
            assertEquals(ReaderPortCommandResult.Accepted, timer.commitReleaseOnlyCloseBudget(close))
            f.closeRoot()
            assertNull(timer.bindBeforeWork(f.id) {})
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertEquals(listOf(key), elapsed)
            assertEquals(ReaderPortCommandResult.Accepted, timer.cancelReleaseOnlyCloseBudget(close))
        } finally { f.dispose() }
    }

    @Test fun actualRootDeadlineFinalizationIsNotAnImplicitLegacyRestoreOrInstallation() = runTest {
        val f = Fixture(testScheduler)
        try {
            assertNull(f.store.snapshot)
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            assertIs<ReaderPortCommandResult.Rejected>(f.bridge.restoreAfterTransitionActivation(f.domain))
            assertNull(f.store.snapshot, "Original-owner deadline finalization is not complete package installation")
        } finally { f.dispose() }
    }

    @Test fun actualRootFrameSourceCannotTurnPartialFreezeFailureIntoSuccessfulRetry() = runTest {
        val f = Fixture(testScheduler)
        try {
            val source = f.frameSource()
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            val attempted = f.domain.copy(freezeToken = ReaderLegacyFreezeToken(89L))
            assertIs<ReaderPortCommandResult.Rejected>(source.freezeForTransitionActivation(attempted))
            assertIs<ReaderPortCommandResult.Rejected>(source.freezeForTransitionActivation(attempted),
                "The first failure left the actual Bridge in another domain; repeating the composite request must not report all owners frozen")
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class Fixture(private val scheduler: TestCoroutineScheduler) {
        init { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
        private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val store = ReaderActivatedSessionSnapshotStore()
        private val type = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeFrameRoot")
        private val root = type.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
            .apply { isAccessible = true }.newInstance(activity.get(), store) as ViewGroup
        val bridge = type.getDeclaredField("presentationHostBridge").apply { isAccessible = true }.get(root) as ReaderPresentationHostBridge
        val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(67L))
        val id = ReaderTransitionId(17L, 19L, 1L, ReaderTransitionOperation.BootstrapNativePage,
            ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
        init { activity.get().setContentView(root); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
        fun timer(): ReaderTask6FactOnlyTimerPort {
            val getter = type.declaredMethods.singleOrNull { it.name == "getFactOnlyTimerPort" && it.parameterCount == 0 }
            assertNotNull(getter, "Actual Native Root must compose its original Bridge timer instead of constructing another owner")
            return assertIs<ReaderTask6FactOnlyTimerPort>(getter.apply { isAccessible = true }.invoke(root))
        }
        fun finalize(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
            val method = type.declaredMethods.singleOrNull { it.name == "finalizeActivatedDeadlineHandoff" && it.parameterCount == 1 }
            assertNotNull(method, "Actual Native Root must route exact deadline finalization to its original Bridge")
            return assertIs<ReaderPortCommandResult>(method.apply { isAccessible = true }.invoke(root, domain))
        }
        fun frameSource(): ReaderFrameOrHandoffPhysicalSource = type.getDeclaredMethod("getFrameOrHandoffPhysicalSource")
            .apply { isAccessible = true }.invoke(root) as ReaderFrameOrHandoffPhysicalSource
        fun closeRoot() { type.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(root) }
        suspend fun dispose() {
            try {
                val native = type.getDeclaredField("viewerContainer").apply { isAccessible = true }.get(root)
                val controller = native.javaClass.getDeclaredField("pageRasterPreparationController").apply { isAccessible = true }
                    .get(native) as ReaderPageRasterPreparationController
                val closed = controller.destroy()
                scheduler.runCurrent()
                closeRoot(); shadowOf(Looper.getMainLooper()).idle()
                val failure = runCatching { withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } } }.exceptionOrNull()
                if (failure != null) check(failure is ReaderPageTeardownException && generateSequence(failure as Throwable?) { it.cause }
                    .any { it.message == "GL surface is unavailable for terminal disposal" }) { "Unexpected Root fixture terminal disposition: completed=${closed.isCompleted} timedOut=${failure is kotlinx.coroutines.TimeoutCancellationException} teardown=${failure is ReaderPageTeardownException}" }
                check(closed.isCompleted)
            } finally {
                try { activity.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
                finally { Dispatchers.resetMain() }
            }
        }
    }
}
