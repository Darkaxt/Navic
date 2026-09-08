package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadedAudioSnapshotTest {
	private val completed = DownloadEntity(
		songId = "song", status = DownloadStatus.DOWNLOADED,
		filePath = "audio.mp3", ownerId = "account-a"
	)

	@Test
	fun completedSnapshotResolvesWithoutWaitingForADerivedDownloadMap() {
		val derivedMap = emptyMap<String, String>()
		assertNull(derivedMap[completed.songId])
		val path = downloadedAudioPath(completed, "account-a") { it == "audio.mp3" }
		assertEquals("audio.mp3", path)
		assertEquals(PlaybackRecoveryResolution.ResumeCurrent, resolve(path))
	}

	@Test
	fun completedRowsWithRealMissingFilesRemainUnusable() {
		val path = downloadedAudioPath(completed, "account-a") { false }
		assertNull(path)
		assertEquals(PlaybackRecoveryResolution.HoldFailure, resolve(path))
		assertNull(downloadedAudioPath(completed.copy(filePath = null), "account-a") { true })
	}

	@Test
	fun anOutgoingSnapshotCannotSupplyAnotherAccountsSong() {
		assertNull(downloadedAudioPath(completed, "account-b") { true })
		assertNull(downloadedAudioPath(completed, null) { true })
		assertNull(downloadedAudioPath(completed.copy(status = DownloadStatus.DOWNLOADING), "account-a") { true })
	}

	@Test
	fun completedSnapshotCannotResumeASupersedingQueueSelection() {
		val path = downloadedAudioPath(completed, "account-a") { true }
		assertEquals(PlaybackRecoveryResolution.CancelStale, resolve(path, "new-selection"))
	}

	private fun resolve(path: String?, currentSongId: String = completed.songId): PlaybackRecoveryResolution =
		playbackRecoveryResolution(
			pending = PendingPlaybackRecovery(completed.songId, 0, 0L, true, "download"),
			currentSongId = currentSongId,
			currentIndex = 0,
			downloadStatus = completed.status,
			hasUsableLocalFile = path != null,
			skipMediaOnError = false,
			nextPlayableIndex = null
		)
}
