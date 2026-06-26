package paige.navic.ui.screens.album

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.AurralOwnershipStatus

class AlbumDownloadOwnershipPolicyTest {
	@Test
	fun albumDownloadOwnershipStatusIsNullWhenNothingIsDownloaded() {
		assertNull(
			albumDownloadOwnershipStatus(
				songIds = listOf("a", "b"),
				downloads = emptyList()
			)
		)
	}

	@Test
	fun albumDownloadOwnershipStatusMarksFullyDownloadedAlbumsOwned() {
		assertEquals(
			AurralOwnershipStatus.Owned,
			albumDownloadOwnershipStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(
					DownloadEntity("a", DownloadStatus.DOWNLOADED),
					DownloadEntity("b", DownloadStatus.DOWNLOADED)
				)
			)
		)
	}

	@Test
	fun albumDownloadOwnershipStatusMarksPartialAlbumsPartial() {
		assertEquals(
			AurralOwnershipStatus.Partial,
			albumDownloadOwnershipStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(DownloadEntity("a", DownloadStatus.DOWNLOADED))
			)
		)
	}

	@Test
	fun albumDownloadOwnershipStatusMarksActiveAlbumDownloadsProcessing() {
		assertEquals(
			AurralOwnershipStatus.Processing,
			albumDownloadOwnershipStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(
					DownloadEntity("a", DownloadStatus.DOWNLOADED),
					DownloadEntity("b", DownloadStatus.DOWNLOADING)
				)
			)
		)
	}

	@Test
	fun albumDownloadOwnershipStatusMarksFailedAlbumDownloadsFailed() {
		assertEquals(
			AurralOwnershipStatus.Failed,
			albumDownloadOwnershipStatus(
				songIds = listOf("a", "b"),
				downloads = listOf(DownloadEntity("a", DownloadStatus.FAILED))
			)
		)
	}
}
