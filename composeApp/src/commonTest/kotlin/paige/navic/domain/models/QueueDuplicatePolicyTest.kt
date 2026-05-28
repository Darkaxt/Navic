package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueDuplicatePolicyTest {
	@Test
	fun duplicateConfirmationIsRequiredOnlyForAlreadyQueuedSongs() {
		assertTrue(
			requiresQueueDuplicateConfirmation(
				queueSongIds = listOf("song-1", "song-2"),
				songId = "song-2"
			)
		)
		assertFalse(
			requiresQueueDuplicateConfirmation(
				queueSongIds = listOf("song-1", "song-2"),
				songId = "song-3"
			)
		)
	}

	@Test
	fun duplicateQueueActionPreservesTheRequestedQueueOperation() {
		assertEquals(
			QueueDuplicateAction.PlayNext,
			duplicateQueueActionFor(
				queueSongIds = listOf("song-1"),
				songId = "song-1",
				action = QueueDuplicateAction.PlayNext
			)
		)
		assertEquals(
			QueueDuplicateAction.AddToQueue,
			duplicateQueueActionFor(
				queueSongIds = listOf("song-1"),
				songId = "song-1",
				action = QueueDuplicateAction.AddToQueue
			)
		)
		assertNull(
			duplicateQueueActionFor(
				queueSongIds = listOf("song-1"),
				songId = "song-2",
				action = QueueDuplicateAction.PlayNext
			)
		)
	}
}
