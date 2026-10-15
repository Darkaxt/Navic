package paige.navic.ui.screens.reader

import kotlin.test.*
import kotlinx.coroutines.test.runTest

/** Original optional raster scheduler custody only; no rendering, store access or readiness. */
class ReaderOriginalSchedulerInventoryHostTest {
    @Test fun optionalSchedulerReportsItsActualDomainIncludingTruthfulEmptyCustody() = runTest {
        val scheduler = ReaderPageRasterScheduler(backgroundScope, UnusedStore,
            ReaderPageRasterGenerator<Any> { error("Unrequested generation") }, { error("Unrequested release") })
        val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(131L))
        try {
            assertNull(connected(scheduler))
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(domain))
            val first = assertNotNull(connected(scheduler))
            assertTrue(first.domain == domain, "Original frozen domain must match")
            assertEquals(ReaderLegacyInventorySource.RasterGenerationAndPersistence, first.source)
            assertTrue(first.resources.isEmpty())
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.restoreAfterTransitionActivation(domain))
            assertNull(connected(scheduler))
            val other = domain.copy(freezeToken = ReaderLegacyFreezeToken(133L))
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(other))
            assertTrue(assertNotNull(connected(scheduler)).domain == other, "Original refrozen domain must match")
        } finally { scheduler.closeAndJoin() }
    }

    @Test fun parentSchedulerDomainCannotHideAnUnfrozenMaintenanceOwner() = runTest {
        val scheduler = ReaderPageRasterScheduler(backgroundScope, UnusedStore,
            ReaderPageRasterGenerator<Any> { error("Unrequested generation") }, { error("Unrequested release") })
        val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(137L))
        try {
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(domain))
            assertNotNull(connected(scheduler))
            val maintenance = scheduler.javaClass.getDeclaredField("maintenanceOwnership").apply { isAccessible = true }
                .get(scheduler) as ReaderExactPhysicalOwnerRegistry
            assertEquals(ReaderPortCommandResult.Accepted, maintenance.restoreAfterTransitionActivation(domain))
            assertTrue(scheduler.isFrozen)
            assertNull(connected(scheduler))
        } finally { scheduler.closeAndJoin() }
    }

    @Test fun rejectedMaintenanceFenceCannotMakeParentFreezeAccepted() = runTest {
        val scheduler = ReaderPageRasterScheduler(backgroundScope, UnusedStore,
            ReaderPageRasterGenerator<Any> { error("Unrequested generation") }, { error("Unrequested release") })
        val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(139L))
        val foreign = domain.copy(freezeToken = ReaderLegacyFreezeToken(149L))
        try {
            val maintenance = maintenance(scheduler)
            assertEquals(ReaderPortCommandResult.Accepted, maintenance.freezeForTransitionActivation(foreign))
            assertIs<ReaderPortCommandResult.Rejected>(scheduler.freezeForTransitionActivation(domain))
            assertFalse(scheduler.isFrozen)
            assertNull(connected(scheduler))
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(foreign))
            assertTrue(assertNotNull(connected(scheduler)).domain == foreign, "Original maintenance domain must remain exact")
        } finally { scheduler.closeAndJoin() }
    }

    @Test fun rememberedParentDomainCannotAcceptRetryAfterMaintenanceReopens() = runTest {
        val scheduler = ReaderPageRasterScheduler(backgroundScope, UnusedStore,
            ReaderPageRasterGenerator<Any> { error("Unrequested generation") }, { error("Unrequested release") })
        val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(151L))
        try {
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(domain))
            val maintenance = maintenance(scheduler)
            assertEquals(ReaderPortCommandResult.Accepted, maintenance.restoreAfterTransitionActivation(domain))
            assertNull(connected(scheduler))
            assertIs<ReaderPortCommandResult.Rejected>(scheduler.freezeForTransitionActivation(domain))
            assertEquals(ReaderPortCommandResult.Accepted, maintenance.freezeForTransitionActivation(domain))
            assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(domain))
            assertTrue(assertNotNull(connected(scheduler)).domain == domain, "Original child must supply its exact fence on retry")
        } finally { scheduler.closeAndJoin() }
    }

    private fun maintenance(scheduler: ReaderPageRasterScheduler<Any>): ReaderExactPhysicalOwnerRegistry =
        scheduler.javaClass.getDeclaredField("maintenanceOwnership").apply { isAccessible = true }
            .get(scheduler) as ReaderExactPhysicalOwnerRegistry

    private fun connected(scheduler: ReaderPageRasterScheduler<Any>): ReaderLegacyConnectedSourceInventory? {
        val method = scheduler.javaClass.declaredMethods.singleOrNull { it.name == "connectedFrozenOwnership" && it.parameterCount == 0 }
        assertNotNull(method, "The original optional scheduler must report its current exact domain, not infer empty ownership from rows")
        return method.apply { isAccessible = true }.invoke(scheduler) as ReaderLegacyConnectedSourceInventory?
    }

    private object UnusedStore : ReaderPageRasterStore<Any> {
        override fun contains(key: ReaderPageRasterKey): Boolean = error("Unrequested store work")
        override fun <R : Any> readCopy(key: ReaderPageRasterKey, copy: (Any) -> R?): ReaderPageRaster<R>? = error("Unrequested store work")
        override fun write(key: ReaderPageRasterKey, metadata: ReaderPageRasterMetadata, value: Any): ReaderPageRasterWriteResult = error("Unrequested store work")
        override fun remove(key: ReaderPageRasterKey): Boolean = error("Unrequested store work")
        override fun rollbackPublication(receipt: ReaderPageRasterWriteReceipt): Boolean = error("Unrequested store work")
        override fun retainProfile(profile: ReaderPageRasterProfile): Int = error("Unrequested store work")
        override fun protectChapter(chapter: ReaderPageRasterChapterKey?) = error("Unrequested store work")
        override fun encodedBytes(key: ReaderPageRasterKey): Long = error("Unrequested store work")
    }
}
