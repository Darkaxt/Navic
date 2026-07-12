package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageSlideCoordinatorTest {
	@Test
	fun visualCommitDispatchesExactSettlementWithoutWaitingForIt() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)

		val effects = coordinator.visualCommitted(7)

		assertEquals(7, coordinator.visualPageIndex)
		assertEquals(6, coordinator.settledPageIndex)
		assertEquals(7, coordinator.activeSettlementTarget)
		assertIs<ReaderPageSlideCoordinatorEffect.SettleExact>(effects.single()).also {
			assertEquals(7, it.pageIndex)
		}
	}

	@Test
	fun repeatedVisualCommitsCoalesceBehindOneActiveSettlement() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)
		coordinator.visualCommitted(7)

		assertTrue(coordinator.visualCommitted(8).isEmpty())
		assertTrue(coordinator.visualCommitted(9).isEmpty())

		assertEquals(9, coordinator.visualPageIndex)
		assertEquals(7, coordinator.activeSettlementTarget)
		assertEquals(9, coordinator.pendingTargetPageIndex)
	}

	@Test
	fun completedIntermediateSettlementDispatchesOnlyLatestVisualTarget() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)
		val generation = coordinator.generation
		coordinator.visualCommitted(7)
		coordinator.visualCommitted(8)

		val effects = coordinator.settlementReported(generation, pageIndex = 7, renderable = true)

		assertEquals(7, coordinator.settledPageIndex)
		assertEquals(8, coordinator.activeSettlementTarget)
		assertNull(coordinator.pendingTargetPageIndex)
		assertIs<ReaderPageSlideCoordinatorEffect.SettleExact>(effects.single()).also {
			assertEquals(8, it.pageIndex)
		}
	}

	@Test
	fun latestRenderableSettlementRemovesFinalShield() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)
		val generation = coordinator.generation
		coordinator.visualCommitted(7)

		val effects = coordinator.settlementReported(generation, pageIndex = 7, renderable = true)

		assertEquals(7, coordinator.settledPageIndex)
		assertNull(coordinator.activeSettlementTarget)
		assertIs<ReaderPageSlideCoordinatorEffect.RemoveFinalShield>(effects.single())
	}

	@Test
	fun nonRenderableLatestSettlementKeepsShieldUntilFrameIsReady() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)
		val generation = coordinator.generation
		coordinator.visualCommitted(7)

		assertTrue(coordinator.settlementReported(generation, pageIndex = 7, renderable = false).isEmpty())
		assertTrue(coordinator.frameBecameRenderable(generation, pageIndex = 6).isEmpty())
		assertIs<ReaderPageSlideCoordinatorEffect.RemoveFinalShield>(
			coordinator.frameBecameRenderable(generation, pageIndex = 7).single()
		)
	}

	@Test
	fun staleGenerationSettlementCannotChangeCurrentSession() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)
		val staleGeneration = coordinator.generation
		coordinator.invalidate(newPageIndex = 12)

		assertTrue(coordinator.settlementReported(staleGeneration, pageIndex = 7, renderable = true).isEmpty())
		assertEquals(12, coordinator.visualPageIndex)
		assertEquals(12, coordinator.settledPageIndex)
	}

	@Test
	fun reportingAnUnexpectedPageDoesNotCompleteTheActiveSettlement() {
		val coordinator = ReaderPageSlideCoordinator(initialPageIndex = 6)
		val generation = coordinator.generation
		coordinator.visualCommitted(7)

		assertTrue(coordinator.settlementReported(generation, pageIndex = 5, renderable = true).isEmpty())
		assertEquals(6, coordinator.settledPageIndex)
		assertEquals(7, coordinator.activeSettlementTarget)
	}
}
