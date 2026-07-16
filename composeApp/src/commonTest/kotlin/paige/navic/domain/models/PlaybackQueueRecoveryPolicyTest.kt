package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueueRecoveryPolicyTest {
	@Test
	fun firstPlayableUpcomingIndexSkipsUnavailableItems() {
		assertEquals(
			3,
			firstPlayableUpcomingIndex(
				currentIndex = 0,
				queueSongIds = listOf("current", "missing-a", "missing-b", "ready", "later"),
				availableSongIds = setOf("ready", "later")
			)
		)
	}

	@Test
	fun firstPlayableUpcomingIndexReturnsNullWhenNothingAfterCurrentIsPlayable() {
		assertEquals(
			null,
			firstPlayableUpcomingIndex(
				currentIndex = 1,
				queueSongIds = listOf("previous", "current", "missing"),
				availableSongIds = setOf("previous")
			)
		)
	}

	@Test
	fun playbackFailureAdvancesOnlyWhenPreferenceAllowsIt() {
		assertEquals(
			3,
			playbackFailureTargetIndex(
				skipMediaOnError = true,
				nextPlayableIndex = 3
			)
		)
		assertEquals(
			null,
			playbackFailureTargetIndex(
				skipMediaOnError = false,
				nextPlayableIndex = 3
			)
		)
		assertEquals(
			null,
			playbackFailureTargetIndex(
				skipMediaOnError = true,
				nextPlayableIndex = null
			)
		)
	}

	@Test
	fun playbackFailureNeverInventsAQueueTarget() {
		assertEquals(
			null,
			playbackFailureTargetIndex(
				skipMediaOnError = true,
				nextPlayableIndex = null
			)
		)
	}

	@Test
	fun pendingRecoveryWaitsUntilTheSameCurrentItemHasAUsableLocalFile() {
		val pending = PendingPlaybackRecovery(
			songId = "song",
			queueIndex = 2,
			positionMs = 4_200L,
			shouldResume = true,
			reason = "source-error"
		)

		assertEquals(
			PlaybackRecoveryResolution.Wait,
			playbackRecoveryResolution(
				pending = pending,
				currentSongId = "song",
				currentIndex = 2,
				downloadStatus = DownloadStatus.DOWNLOADING,
				hasUsableLocalFile = false,
				skipMediaOnError = true,
				nextPlayableIndex = 3
			)
		)
		assertEquals(
			PlaybackRecoveryResolution.ResumeCurrent,
			playbackRecoveryResolution(
				pending = pending,
				currentSongId = "song",
				currentIndex = 2,
				downloadStatus = DownloadStatus.DOWNLOADED,
				hasUsableLocalFile = true,
				skipMediaOnError = true,
				nextPlayableIndex = 3
			)
		)
	}

	@Test
	fun staleRecoveryCancelsInsteadOfChangingAnotherQueueItem() {
		assertEquals(
			PlaybackRecoveryResolution.CancelStale,
			playbackRecoveryResolution(
				pending = PendingPlaybackRecovery(
					songId = "failed",
					queueIndex = 2,
					positionMs = 0L,
					shouldResume = true,
					reason = "source-error"
				),
				currentSongId = "different",
				currentIndex = 3,
				downloadStatus = DownloadStatus.DOWNLOADED,
				hasUsableLocalFile = true,
				skipMediaOnError = true,
				nextPlayableIndex = 4
			)
		)
	}

	@Test
	fun terminalFailureHoldsByDefaultAndAdvancesAtMostOnceWhenEnabled() {
		val pending = PendingPlaybackRecovery(
			songId = "failed",
			queueIndex = 2,
			positionMs = 0L,
			shouldResume = true,
			reason = "source-error"
		)

		assertEquals(
			PlaybackRecoveryResolution.HoldFailure,
			playbackRecoveryResolution(
				pending = pending,
				currentSongId = "failed",
				currentIndex = 2,
				downloadStatus = DownloadStatus.FAILED,
				hasUsableLocalFile = false,
				skipMediaOnError = false,
				nextPlayableIndex = 5
			)
		)
		assertEquals(
			PlaybackRecoveryResolution.Advance(5),
			playbackRecoveryResolution(
				pending = pending,
				currentSongId = "failed",
				currentIndex = 2,
				downloadStatus = DownloadStatus.FAILED,
				hasUsableLocalFile = false,
				skipMediaOnError = true,
				nextPlayableIndex = 5
			)
		)
		assertEquals(
			PlaybackRecoveryResolution.HoldFailure,
			playbackRecoveryResolution(
				pending = pending,
				currentSongId = "failed",
				currentIndex = 2,
				downloadStatus = DownloadStatus.FAILED,
				hasUsableLocalFile = false,
				skipMediaOnError = true,
				nextPlayableIndex = null
			)
		)
	}

	@Test
	fun explicitPauseAndResumeUpdateOnlyTheRetainedIntent() {
		val pending = PendingPlaybackRecovery(
			songId = "song",
			queueIndex = 2,
			positionMs = 4_200L,
			shouldResume = true,
			reason = "source-error"
		)

		assertEquals(false, pending.withPlaybackIntent(false).shouldResume)
		assertEquals(true, pending.withPlaybackIntent(false).withPlaybackIntent(true).shouldResume)
		assertEquals(4_200L, pending.withPlaybackIntent(false).positionMs)
	}
}
