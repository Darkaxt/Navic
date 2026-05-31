package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListeningHistoryPolicyTest {
	@Test
	fun listeningHistorySubmissionRequiresScrobblingAndSongId() {
		assertFalse(
			shouldSubmitListeningHistory(
				enableScrobbling = false,
				pauseListeningHistory = false,
				songId = "song-1"
			)
		)
		assertFalse(
			shouldSubmitListeningHistory(
				enableScrobbling = true,
				pauseListeningHistory = false,
				songId = null
			)
		)
		assertTrue(
			shouldSubmitListeningHistory(
				enableScrobbling = true,
				pauseListeningHistory = false,
				songId = "song-1"
			)
		)
	}

	@Test
	fun pauseListeningHistoryBlocksListeningHistorySubmission() {
		assertFalse(
			shouldSubmitListeningHistory(
				enableScrobbling = true,
				pauseListeningHistory = true,
				songId = "song-1"
			)
		)
	}

	@Test
	fun listeningHistoryIgnoresTransientAurralFlowSongs() {
		assertFalse(
			shouldSubmitListeningHistory(
				enableScrobbling = true,
				pauseListeningHistory = false,
				songId = "${AurralFlowSongIdPrefix}job-1"
			)
		)
	}
}
