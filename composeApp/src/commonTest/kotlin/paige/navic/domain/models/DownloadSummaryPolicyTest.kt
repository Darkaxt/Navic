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
}
