package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class QueuedDownloadRecoveryPolicyTest {
	@Test
	fun queuedDownloadRecoveryResumesOnlyQueuedSongsThatStillExistLocally() {
		val recovery = queuedDownloadRecovery(
			downloads = listOf(
				DownloadEntity("downloaded", DownloadStatus.DOWNLOADED),
				DownloadEntity("first", DownloadStatus.QUEUED),
				DownloadEntity("active", DownloadStatus.DOWNLOADING),
				DownloadEntity("missing", DownloadStatus.QUEUED),
				DownloadEntity("second", DownloadStatus.QUEUED),
				DownloadEntity("failed", DownloadStatus.FAILED)
			),
			localSongIds = setOf("first", "second", "downloaded", "active", "failed")
		)

		assertEquals(listOf("first", "second"), recovery.songIdsToResume)
		assertEquals(listOf("missing"), recovery.songIdsToDelete)
	}

	@Test
	fun queuedDownloadRecoveryDeletesQueuedSongsWhenLocalLibraryHasNoMatchingSong() {
		val recovery = queuedDownloadRecovery(
			downloads = listOf(
				DownloadEntity("stale-1", DownloadStatus.QUEUED),
				DownloadEntity("stale-2", DownloadStatus.QUEUED)
			),
			localSongIds = emptySet()
		)

		assertEquals(emptyList(), recovery.songIdsToResume)
		assertEquals(listOf("stale-1", "stale-2"), recovery.songIdsToDelete)
	}
}
