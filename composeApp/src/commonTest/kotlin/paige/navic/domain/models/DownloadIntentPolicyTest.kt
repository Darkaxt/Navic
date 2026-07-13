package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

class DownloadIntentPolicyTest {
	@Test
	fun cancelCreatesANewerDurableTombstone() {
		val queued = DownloadEntity(
			songId = "song",
			status = DownloadStatus.QUEUED,
			intentGeneration = 7,
			queuedAtEpochMs = 100
		)

		val cancelled = cancelledDownloadIntent(queued)

		assertTrue(cancelled.cancelled)
		assertTrue(cancelled.intentGeneration > queued.intentGeneration)
		assertTrue(cancelled.status == DownloadStatus.NOT_DOWNLOADED)
	}

	@Test
	fun staleRetryAndCompletionCannotOverrideCancellation() {
		val failed = DownloadEntity(
			songId = "song",
			status = DownloadStatus.FAILED,
			intentGeneration = 3
		)
		val observedGeneration = failed.intentGeneration
		val cancelled = cancelledDownloadIntent(failed)

		assertFalse(canRetryDownloadIntent(cancelled, observedGeneration))
		assertFalse(canApplyDownloadResult(cancelled, observedGeneration))
	}

	@Test
	fun currentGenerationCanRetryAndComplete() {
		val failed = DownloadEntity(
			songId = "song",
			status = DownloadStatus.FAILED,
			intentGeneration = 4
		)
		val downloading = failed.copy(status = DownloadStatus.DOWNLOADING)

		assertTrue(canRetryDownloadIntent(failed, 4))
		assertTrue(canApplyDownloadResult(downloading, 4))
	}
}
