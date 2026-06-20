package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadQueueNotificationPolicyTest {
	@Test
	fun notificationStateIsHiddenWhenQueueHasNoActiveRows() {
		assertNull(
			downloadQueueNotificationState(
				listOf(
					DownloadQueueNotificationRow(DownloadStatus.DOWNLOADED, 1f),
					DownloadQueueNotificationRow(DownloadStatus.FAILED, 0f)
				)
			)
		)
	}

	@Test
	fun notificationStateUsesSameActiveProgressRulesForEveryDownloadQueue() {
		val rows = listOf(
			DownloadQueueNotificationRow(DownloadStatus.DOWNLOADING, 0.5f),
			DownloadQueueNotificationRow(DownloadStatus.QUEUED, 0f),
			DownloadQueueNotificationRow(DownloadStatus.FAILED, 0f),
			DownloadQueueNotificationRow(DownloadStatus.DOWNLOADED, 1f)
		)

		val state = downloadQueueNotificationState(rows)

		assertEquals(
			DownloadQueueNotificationState(
				activeCount = 2,
				failedCount = 1,
				progress = 0.25f,
				indeterminate = false
			),
			state
		)
	}

	@Test
	fun notificationStateIsIndeterminateWhenEverythingActiveIsQueued() {
		val state = downloadQueueNotificationState(
			listOf(
				DownloadQueueNotificationRow(DownloadStatus.QUEUED, 0f),
				DownloadQueueNotificationRow(DownloadStatus.QUEUED, 0.7f)
			)
		)

		assertEquals(
			DownloadQueueNotificationState(
				activeCount = 2,
				failedCount = 0,
				progress = 0f,
				indeterminate = true
			),
			state
		)
	}

	@Test
	fun notificationStateClampsInvalidDownloadProgress() {
		val state = downloadQueueNotificationState(
			listOf(
				DownloadQueueNotificationRow(DownloadStatus.DOWNLOADING, -0.5f),
				DownloadQueueNotificationRow(DownloadStatus.DOWNLOADING, 1.5f)
			)
		)

		assertEquals(
			DownloadQueueNotificationState(
				activeCount = 2,
				failedCount = 0,
				progress = 0.5f,
				indeterminate = false
			),
			state
		)
	}
}
