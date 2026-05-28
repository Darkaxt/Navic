package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionDownloadStatusPolicyTest {
	@Test
	fun collectionStatusShowsQueuedWhenAnyCollectionSongIsQueued() {
		assertEquals(
			DownloadStatus.QUEUED,
			collectionDownloadStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(
					DownloadEntity("a", DownloadStatus.QUEUED),
					DownloadEntity("b", DownloadStatus.NOT_DOWNLOADED)
				)
			)
		)
	}

	@Test
	fun collectionStatusPrefersActiveDownloadOverQueuedDownload() {
		assertEquals(
			DownloadStatus.DOWNLOADING,
			collectionDownloadStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(
					DownloadEntity("a", DownloadStatus.QUEUED),
					DownloadEntity("b", DownloadStatus.DOWNLOADING)
				)
			)
		)
	}

	@Test
	fun collectionStatusStillRequiresEverySongDownloadedForDownloadedState() {
		assertEquals(
			DownloadStatus.NOT_DOWNLOADED,
			collectionDownloadStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(DownloadEntity("a", DownloadStatus.DOWNLOADED))
			)
		)
	}

	@Test
	fun collectionSongIdsToQueueSkipsSongsAlreadyDownloadedDownloadingOrQueued() {
		assertEquals(
			listOf("missing", "failed"),
			collectionSongIdsToQueue(
				songIds = listOf("downloaded", "downloading", "queued", "missing", "failed"),
				downloads = listOf(
					DownloadEntity("downloaded", DownloadStatus.DOWNLOADED),
					DownloadEntity("downloading", DownloadStatus.DOWNLOADING),
					DownloadEntity("queued", DownloadStatus.QUEUED),
					DownloadEntity("failed", DownloadStatus.FAILED)
				)
			)
		)
	}
}
