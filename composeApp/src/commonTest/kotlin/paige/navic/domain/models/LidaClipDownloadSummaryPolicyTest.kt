package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity

class LidaClipDownloadSummaryPolicyTest {
	@Test
	fun lidaClipDownloadQueueIncludesActiveFailedAndCompletedRows() {
		val rows = lidaClipDownloadQueueDownloads(
			listOf(
				lidaClipDownload("song-downloading", DownloadStatus.DOWNLOADING),
				lidaClipDownload("song-downloaded", DownloadStatus.DOWNLOADED),
				lidaClipDownload("song-queued", DownloadStatus.QUEUED),
				lidaClipDownload("song-failed", DownloadStatus.FAILED)
			)
		)

		assertEquals(
			listOf("song-downloading", "song-queued", "song-failed", "song-downloaded"),
			rows.map { it.songId }
		)
	}

	@Test
	fun lidaClipQueueControlsMirrorSongDownloadQueueControls() {
		val rows = listOf(
			lidaClipDownload("song-downloading", DownloadStatus.DOWNLOADING),
			lidaClipDownload("song-queued", DownloadStatus.QUEUED),
			lidaClipDownload("song-failed", DownloadStatus.FAILED)
		)

		val controls = lidaClipDownloadQueueControls(rows)

		assertEquals(1, controls.failedCount)
		assertEquals(true, controls.canRetryFailedDownloads)
		assertEquals(true, controls.canDiscardFailedDownloads)
		assertEquals(true, controls.canClearDownloadQueue)
		assertEquals(
			listOf("song-downloading", "song-queued", "song-failed"),
			clearLidaClipDownloadQueueSongIds(rows)
		)
	}

	@Test
	fun lidaClipClearQueueIncludesCompletedRowsShownInActivity() {
		val rows = listOf(
			lidaClipDownload("song-downloaded", DownloadStatus.DOWNLOADED)
		)

		assertEquals(
			listOf("song-downloaded"),
			clearLidaClipDownloadQueueSongIds(rows)
		)
		assertEquals(true, lidaClipDownloadQueueControls(rows).canClearDownloadQueue)
	}

	private fun lidaClipDownload(
		songId: String,
		status: DownloadStatus
	) = LidaClipDownloadEntity(
		songId = songId,
		clipId = songId.hashCode(),
		title = "Clip $songId",
		artist = "Artist",
		album = "Album",
		track = "Track",
		durationSeconds = 180,
		mimeType = "video/mp4",
		qualityTier = "official",
		fileName = "$songId.mp4",
		streamUrl = "https://clips.example.com/api/v1/stream/$songId",
		status = status,
		progress = if (status == DownloadStatus.DOWNLOADED) 1f else 0f,
		filePath = null,
		persistOffline = false,
		updatedAtMillis = 1_000L
	)
}
