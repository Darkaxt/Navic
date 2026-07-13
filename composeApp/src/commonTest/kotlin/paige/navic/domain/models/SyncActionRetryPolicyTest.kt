package paige.navic.domain.models

import dev.zt64.subsonic.api.model.SubsonicErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.data.database.entities.SyncActionEntity
import paige.navic.data.database.entities.SyncActionType

class SyncActionRetryPolicyTest {
	@Test
	fun clientAndNotFoundFailuresAreTerminal() {
		assertEquals(SyncFailureDisposition.Terminal, classifySyncFailure(httpStatus = 404))
		assertEquals(
			SyncFailureDisposition.Terminal,
			classifySyncFailure(subsonicErrorCode = SubsonicErrorCode.DATA_NOT_FOUND)
		)
	}

	@Test
	fun serverAndNetworkFailuresRemainRetryable() {
		assertEquals(SyncFailureDisposition.Transient, classifySyncFailure(httpStatus = 503))
		assertEquals(SyncFailureDisposition.Transient, classifySyncFailure())
	}

	@Test
	fun transientFailureSchedulesIncreasingBoundedBackoff() {
		val action = SyncActionEntity(
			actionType = SyncActionType.STAR,
			itemId = "song",
			attemptCount = 2
		)

		val failed = action.afterSyncFailure(
			disposition = SyncFailureDisposition.Transient,
			nowEpochMs = 1_000L,
			errorSummary = "server unavailable"
		)

		assertEquals(3, failed.attemptCount)
		assertTrue(failed.nextAttemptAtEpochMs > 1_000L)
		assertFalse(failed.deadLettered)
		assertTrue(syncRetryDelayMs(40) <= MAX_SYNC_RETRY_DELAY_MS)
	}

	@Test
	fun terminalFailureBecomesVisibleDeadLetterWithoutFutureRetry() {
		val failed = SyncActionEntity(
			actionType = SyncActionType.UNSTAR,
			itemId = "missing"
		).afterSyncFailure(
			disposition = SyncFailureDisposition.Terminal,
			nowEpochMs = 2_000L,
			errorSummary = "not found"
		)

		assertTrue(failed.deadLettered)
		assertEquals(0L, failed.nextAttemptAtEpochMs)
		assertEquals("not found", failed.lastError)
	}
}
