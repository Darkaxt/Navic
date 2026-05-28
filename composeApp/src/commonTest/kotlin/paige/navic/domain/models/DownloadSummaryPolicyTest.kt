package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSummaryPolicyTest {
	@Test
	fun pendingDownloadCountIncludesQueuedAndDownloadingSongsOnly() {
		assertEquals(
			2,
			pendingDownloadCount(
				listOf(
					DownloadEntity("queued", DownloadStatus.QUEUED),
					DownloadEntity("downloading", DownloadStatus.DOWNLOADING),
					DownloadEntity("downloaded", DownloadStatus.DOWNLOADED),
					DownloadEntity("failed", DownloadStatus.FAILED),
					DownloadEntity("not-downloaded", DownloadStatus.NOT_DOWNLOADED)
				)
			)
		)
	}

	@Test
	fun downloadQueueDownloadsIncludesPendingAndFailedRowsOnly() {
		assertEquals(
			listOf("downloading", "queued", "failed"),
			downloadQueueDownloads(
				listOf(
					DownloadEntity("downloaded", DownloadStatus.DOWNLOADED),
					DownloadEntity("failed", DownloadStatus.FAILED),
					DownloadEntity("queued", DownloadStatus.QUEUED),
					DownloadEntity("not-downloaded", DownloadStatus.NOT_DOWNLOADED),
					DownloadEntity("downloading", DownloadStatus.DOWNLOADING)
				)
			).map { it.songId }
		)
	}

	@Test
	fun retryableFailedDownloadSongIdsIncludesOnlyFailedSongsThatStillExistLocally() {
		assertEquals(
			listOf("failed-local"),
			retryableFailedDownloadSongIds(
				downloads = listOf(
					DownloadEntity("failed-local", DownloadStatus.FAILED),
					DownloadEntity("failed-missing", DownloadStatus.FAILED),
					DownloadEntity("queued-local", DownloadStatus.QUEUED),
					DownloadEntity("downloading-local", DownloadStatus.DOWNLOADING)
				),
				localSongIds = setOf("failed-local", "queued-local", "downloading-local")
			)
		)
	}

	@Test
	fun failedDownloadRetryPlanRetriesLocalFailuresAndDeletesStaleFailures() {
		val plan = failedDownloadRetryPlan(
			downloads = listOf(
				DownloadEntity("failed-local", DownloadStatus.FAILED),
				DownloadEntity("failed-missing", DownloadStatus.FAILED),
				DownloadEntity("queued-local", DownloadStatus.QUEUED),
				DownloadEntity("downloading-local", DownloadStatus.DOWNLOADING)
			),
			localSongIds = setOf("failed-local", "queued-local", "downloading-local")
		)

		assertEquals(listOf("failed-local"), plan.songIdsToRetry)
		assertEquals(listOf("failed-missing"), plan.staleSongIdsToDelete)
	}

	@Test
	fun downloadConcurrencyLimitStaysInSupportedRange() {
		assertEquals(1, downloadConcurrencyLimit(0))
		assertEquals(3, downloadConcurrencyLimit(3))
		assertEquals(10, downloadConcurrencyLimit(99))
	}

	@Test
	fun queuedDownloadStartPolicyRespectsTheConfiguredConcurrencyCap() {
		assertEquals(
			true,
			canStartQueuedDownload(
				activeDownloadSongIds = setOf("one", "two"),
				queuedSongId = "three",
				configuredLimit = 3
			)
		)
		assertEquals(
			false,
			canStartQueuedDownload(
				activeDownloadSongIds = setOf("one", "two", "three"),
				queuedSongId = "four",
				configuredLimit = 3
			)
		)
		assertEquals(
			false,
			canStartQueuedDownload(
				activeDownloadSongIds = setOf("one"),
				queuedSongId = "one",
				configuredLimit = 3
			)
		)
		assertEquals(
			false,
			canStartQueuedDownload(
				activeDownloadSongIds = setOf("one"),
				queuedSongId = "two",
				configuredLimit = 0
			)
		)
	}
}
