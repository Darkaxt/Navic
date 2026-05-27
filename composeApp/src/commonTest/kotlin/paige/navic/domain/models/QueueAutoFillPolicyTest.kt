package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueAutoFillPolicyTest {
	@Test
	fun autoFillRunsOnlyForActiveNonRadioQueuesNearTheEndBelowTargetSize() {
		assertTrue(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = false,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = false,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = true,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 5,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 25,
				currentIndex = 22,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
	}

	@Test
	fun autoFillAppendCountClampsToTargetSize() {
		assertEquals(15, queueAutoFillAppendCount(queueSize = 10, targetSize = 25))
		assertEquals(0, queueAutoFillAppendCount(queueSize = 25, targetSize = 25))
		assertEquals(0, queueAutoFillAppendCount(queueSize = 30, targetSize = 25))
		assertEquals(0, queueAutoFillAppendCount(queueSize = -1, targetSize = -1))
	}

	@Test
	fun autoFillCandidatesSkipQueuedRadioAndDuplicateSongs() {
		assertEquals(
			listOf("song-2", "song-3"),
			queueAutoFillCandidateIds(
				candidateIds = listOf("song-1", "radio_live", "song-2", "song-2", "song-3"),
				queuedIds = setOf("song-1"),
				limit = 10
			)
		)
		assertEquals(
			listOf("song-2"),
			queueAutoFillCandidateIds(
				candidateIds = listOf("song-1", "song-2", "song-3"),
				queuedIds = setOf("song-1"),
				limit = 1
			)
		)
	}
}
