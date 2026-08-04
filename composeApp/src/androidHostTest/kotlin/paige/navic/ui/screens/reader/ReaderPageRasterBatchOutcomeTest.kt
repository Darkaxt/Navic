package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderPageRasterBatchOutcomeTest {
	@Test
	fun missingPreviewStateIsDeferredInsteadOfFailed() {
		assertEquals(
			ReaderPageRasterBatchOutcome.Deferred(
				stage = "preview-state",
				pageIndex = 7,
				reason = "preview-state-missing"
			),
			readerPageRasterPreviewOutcome(
				status = "missing",
				pageIndex = 7,
				message = null
			)
		)
	}

	@Test
	fun temporarilyUnavailableFoliatePageIsDeferredInsteadOfFailed() {
		assertEquals(
			ReaderPageRasterBatchOutcome.Deferred(
				stage = "preview-render",
				pageIndex = 1,
				reason = "Passive raster page 1 is unavailable"
			),
			readerPageRasterPreviewOutcome(
				status = "failed",
				pageIndex = 1,
				message = "Passive raster page 1 is unavailable",
				paginationReady = false
			)
		)
	}

	@Test
	fun unavailableFoliatePageIsARealFailureAfterPaginationIsComplete() {
		val outcome = readerPageRasterPreviewOutcome(
			status = "failed",
			pageIndex = 3,
			message = "Passive raster page 3 is unavailable",
			paginationReady = true
		)

		assertIs<ReaderPageRasterBatchOutcome.Failed>(outcome)
		assertEquals("preview-render", outcome.stage)
		assertEquals(3, outcome.pageIndex)
		assertEquals("Passive raster page 3 is unavailable", outcome.reason)
	}

	@Test
	fun rendererFailureRetainsExactContext() {
		val outcome = readerPageRasterPreviewOutcome(
			status = "failed",
			pageIndex = 4,
			message = "Renderer document detached"
		)

		assertIs<ReaderPageRasterBatchOutcome.Failed>(outcome)
		assertEquals("preview-render", outcome.stage)
		assertEquals(4, outcome.pageIndex)
		assertEquals("Renderer document detached", outcome.reason)
	}

	@Test
	fun backgroundRefillStopsCleanlyAtEncodedDiskCapacity() {
		assertEquals(
			ReaderPageRasterBatchOutcome.CapacityReached(pageIndex = 231),
			readerPageRasterPersistenceTerminalOutcome(
				trigger = ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
				capacityPolicy = ReaderPageRasterCapacityPolicy.StopBackgroundRefill,
				result = ReaderPageRasterPublicationResult.CapacityReached,
				pageIndex = 231
			)
		)
	}

	@Test
	fun blockingPreparationStillFailsClosedAtEncodedDiskCapacity() {
		val outcome = readerPageRasterPersistenceTerminalOutcome(
			trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
			capacityPolicy = ReaderPageRasterCapacityPolicy.FailClosed,
			result = ReaderPageRasterPublicationResult.CapacityReached,
			pageIndex = 4
		)

		assertIs<ReaderPageRasterBatchOutcome.Failed>(outcome)
		assertEquals("persistent-publication", outcome.stage)
		assertEquals(4, outcome.pageIndex)
		assertEquals("disk-capacity-reached", outcome.reason)
	}

	@Test
	fun foregroundWorkingSetRefillStillFailsClosedAtEncodedDiskCapacity() {
		val outcome = readerPageRasterPersistenceTerminalOutcome(
			trigger = ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
			capacityPolicy = ReaderPageRasterCapacityPolicy.FailClosed,
			result = ReaderPageRasterPublicationResult.CapacityReached,
			pageIndex = 9
		)

		assertIs<ReaderPageRasterBatchOutcome.Failed>(outcome)
		assertEquals("persistent-publication", outcome.stage)
		assertEquals(9, outcome.pageIndex)
		assertEquals("disk-capacity-reached", outcome.reason)
	}

	@Test
	fun cancelledPreviewStateIsNotAFailure() {
		assertEquals(
			ReaderPageRasterBatchOutcome.Cancelled,
			readerPageRasterPreviewOutcome(
				status = "cancelled",
				pageIndex = 3,
				message = null
			)
		)
	}
}
