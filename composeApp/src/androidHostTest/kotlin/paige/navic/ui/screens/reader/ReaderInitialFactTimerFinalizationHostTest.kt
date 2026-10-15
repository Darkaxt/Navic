package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import java.time.Duration
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import paige.navic.reader.*

/** Actual Bridge-owned timer and scheduler route. Synthetic common state is not production
 * installation, Foliate/layout proof, or renderer readiness. No new clock is installed. */
@RunWith(RobolectricTestRunner::class)
class ReaderInitialFactTimerFinalizationHostTest {
    @Test fun exactDrainedFinalizationBindsOriginalTimerWithoutLegacyRestore() {
        val f = Fixture()
        try {
            val timer = f.timer()
            assertNull(timer.bindBeforeWork(f.id) {})
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            assertTrue(f.bridge.snapshotFrozenOwnership().isEmpty())
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            assertSame<ReaderTask6FactOnlyTimerPort>(timer, f.timer())
            val expired = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
            val registration = assertNotNull(timer.bindBeforeWork(f.id, expired::add))
            assertEquals(f.domain, registration.physicalIdentity.domain)
            assertEquals(ReaderLegacyInventorySource.DeadlineRegistration, registration.physicalIdentity.source)
            assertEquals(1, f.scheduler.scheduled.size)
            assertEquals(1, f.scheduler.active.size)
            f.bridge.update(f.pendingDecision)
            assertEquals(1, f.scheduler.scheduled.size)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(listOf(ReaderTransitionFact.DeadlineExpired(f.id)), expired)
            f.scheduler.scheduled.single().run()
            assertEquals(1, expired.size)
            assertEquals(0, f.legacyExpiryCount)
        } finally { f.bridge.dispose() }
    }

    @Test fun undrainedAndForeignDomainsCannotEraseExistingPhysicalDeadline() {
        val f = Fixture()
        try {
            val timer = f.timer()
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            f.bridge.update(f.pendingDecision)
            val legacyAction = f.scheduler.scheduled.single()
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            val row = f.bridge.snapshotFrozenOwnership().single { it.physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration }
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain.copy(freezeToken = ReaderLegacyFreezeToken(43L))))
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            assertEquals(1, f.scheduler.active.size)
            assertNull(timer.bindBeforeWork(f.id) {})
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.drainFrozenOwnership(row.physicalIdentity) {})
            assertTrue(f.scheduler.active.isEmpty())
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            legacyAction.run()
            assertEquals(0, f.legacyExpiryCount)
            val registration = assertNotNull(timer.bindBeforeWork(f.id) {})
            assertEquals(2, f.scheduler.scheduled.size)
            assertEquals(ReaderPortCommandResult.Accepted, timer.cancel(registration))
        } finally { f.bridge.dispose() }
    }

    @Test fun finalizationIrreversiblyFencesLegacyRestoreAndRebinding() {
        val f = Fixture()
        try {
            val timer = f.timer()
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            assertIs<ReaderPortCommandResult.Rejected>(f.bridge.restoreAfterTransitionActivation(f.domain))
            assertNull(timer.bindBeforeWork(f.id.copy(readerSessionGeneration = 23L)) {})
            val registration = assertNotNull(timer.bindBeforeWork(f.id) {})
            assertEquals(ReaderPortCommandResult.Accepted, timer.cancel(registration))
            f.bridge.update(f.pendingDecision)
            assertEquals(1, f.scheduler.scheduled.size)
            assertEquals(0, f.legacyExpiryCount)
        } finally { f.bridge.dispose() }
    }

    @Test fun exactInitialDomainStillUsesOriginalAttemptAndCloseSchedulerSlot() {
        val f = Fixture()
        try {
            val timer = f.timer()
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            val ordinary = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
            val attempt = assertNotNull(timer.bindBeforeWork(f.id, ordinary::add))
            val retired = f.scheduler.scheduled.single()
            val key = ReaderReleaseOnlyCleanupKey(17L, 19L, ReaderReleaseOnlyCleanupId(47L), ReaderReleaseOnlyCleanupGeneration(53L))
            val elapsed = mutableListOf<ReaderReleaseOnlyCleanupKey>()
            val close = assertNotNull(timer.bindReleaseOnlyCloseBudget(key, elapsed::add))
            assertEquals(1, f.scheduler.scheduled.size)
            assertEquals(ReaderPortCommandResult.Accepted, timer.commitReleaseOnlyCloseBudget(close))
            assertEquals(listOf(10_000L, 2_000L), f.scheduler.delays)
            assertEquals(1, f.scheduler.active.size)
            retired.run()
            assertTrue(ordinary.isEmpty())
            assertIs<ReaderPortCommandResult.Rejected>(timer.cancel(attempt))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertEquals(listOf(key), elapsed)
            assertNull(timer.bindBeforeWork(f.id) {})
            assertEquals(0, f.legacyExpiryCount)
        } finally { f.bridge.dispose() }
    }

    @Test fun disposedBridgeCannotFinalizeOrExposeReusableTimerAdmission() {
        for (finalized in listOf(false, true)) {
            val f = Fixture()
            val timer = f.timer()
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            if (finalized) assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            f.bridge.dispose()
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            assertNull(timer.bindBeforeWork(f.id) {})
            assertTrue(f.scheduler.scheduled.isEmpty())
        }
    }

    @Test fun expiryWhileFrozenMustBeExactlyDrainedBeforeFinalization() {
        val f = Fixture()
        try {
            f.timer()
            f.bridge.update(f.pendingDecision)
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
            f.scheduler.scheduled.single().run()
            val row = f.bridge.snapshotFrozenOwnership().single { it.physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration }
            assertEquals(ReaderLegacyResourceState.ReleaseRequested, row.state)
            assertIs<ReaderPortCommandResult.Rejected>(f.finalize(f.domain))
            var confirmed = 0
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.drainFrozenOwnership(row.physicalIdentity) { confirmed++ })
            assertEquals(1, confirmed)
            assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
            f.scheduler.scheduled.single().run()
            assertEquals(0, f.legacyExpiryCount)
        } finally { f.bridge.dispose() }
    }

    @Test fun disposedHostRetainsCommittedReleaseOnlyClockUntilExactTerminalCancellation() {
        val f = Fixture()
        val timer = f.timer()
        assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain))
        assertEquals(ReaderPortCommandResult.Accepted, f.finalize(f.domain))
        val elapsed = mutableListOf<ReaderReleaseOnlyCleanupKey>()
        val key = ReaderReleaseOnlyCleanupKey(17L, 19L, ReaderReleaseOnlyCleanupId(59L), ReaderReleaseOnlyCleanupGeneration(61L))
        val close = assertNotNull(timer.bindReleaseOnlyCloseBudget(key, elapsed::add))
        assertEquals(ReaderPortCommandResult.Accepted, timer.commitReleaseOnlyCloseBudget(close))
        f.bridge.dispose()
        assertNull(timer.bindBeforeWork(f.id) {})
        assertEquals(1, f.scheduler.active.size, "View disposal is not terminal resource accounting or timer retirement")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(key), elapsed)
        assertEquals(ReaderPortCommandResult.Accepted, timer.cancelReleaseOnlyCloseBudget(close))
        f.scheduler.scheduled.single().run()
        assertEquals(1, elapsed.size)
        assertEquals(0, f.legacyExpiryCount)
    }

    private class Scheduler : ReaderPageRelocationDispatchTimeoutScheduler {
        private val handler = Handler(Looper.getMainLooper())
        val scheduled = mutableListOf<Runnable>()
        val delays = mutableListOf<Long>()
        val active = linkedSetOf<Runnable>()
        override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
            scheduled += action; delays += delayMillis; active += action
            return handler.postDelayed(action, delayMillis)
        }
        override fun removeCallbacks(action: Runnable) { active -= action; handler.removeCallbacks(action) }
    }

    private class Fixture {
        val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(41L))
        private val binding = ReaderPresentationBinding("public-synthetic-timer", 1L, 2L, 3L,
            ReaderDestinationCommitIdentity("public-synthetic-timer", 1L))
        val id = ReaderTransitionId(17L, 19L, 1L, ReaderTransitionOperation.BootstrapNativePage,
            ReaderExpectedPresentationBinding.Exact(binding))
        private val state = readerPresentationReduce(ReaderPresentationState(), ReaderPresentationEvent.PublicationOpened(binding)).state
        val pendingDecision = readerPresentationReduce(state, ReaderPresentationEvent.NativePageRequested).decision
        val scheduler = Scheduler()
        var legacyExpiryCount = 0
        private val host = object : ReaderPresentationCommitHost {
            override val isAttachedToWindow = true
            override val currentPresentationBinding = binding
            override val currentShellCoverGeneration: Long? = null
            override val shellCoverSelected = false
            override val measuredViewportWidth = 100
            override val measuredViewportHeight = 200
            override fun prepareOpaqueShellCover(coverGeneration: Long) = error("Cover construction outside this timer slice")
            override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
            override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
            override fun registerShellCoverDrawListener(onDraw: () -> Unit) = ReaderPresentationDrawRegistration {}
            override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
        }
        val bridge = ReaderPresentationHostBridge(host, transitionTimeoutScheduler = scheduler) { event ->
            if (event is ReaderPresentationEvent.TimedOut) legacyExpiryCount++
            null
        }
        fun timer(): ReaderTask6FactOnlyTimerPort {
            val getter = bridge.javaClass.methods.singleOrNull { it.name == "getFactOnlyTimerPort" && it.parameterCount == 0 }
            assertNotNull(getter, "Actual Bridge must expose its original fact-only timer for complete package composition")
            return assertIs<ReaderTask6FactOnlyTimerPort>(getter.invoke(bridge))
        }
        fun finalize(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
            val method = bridge.javaClass.methods.singleOrNull { it.name == "finalizeActivatedDeadlineHandoff" && it.parameterCount == 1 }
            assertNotNull(method, "Actual Bridge must finalize drained deadline ownership without restoring legacy work")
            return assertIs<ReaderPortCommandResult>(method.invoke(bridge, domain))
        }
    }
}
