package paige.navic.domain.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioDownloadPublicationTest {

	@Test
	fun staleAttemptDeletesOnlyItsOwnOutputAndPreservesCommittedBytes() = runTest {
		val files = mutableMapOf("winner.mp3" to "complete audio")
		val accepted = publishAudioDownload(
			finalPath = "stale.mp3",
			write = { files[it] = "stale bytes" },
			isUsable = { files[it]?.isNotEmpty() == true },
			move = { from, to -> files[to] = files.remove(from)!!; true },
			complete = { false },
			delete = { files.remove(it) }
		)
		assertFalse(accepted)
		assertEquals(mapOf("winner.mp3" to "complete audio"), files)
	}

	@Test
	fun publishesOnlyAfterTheCompleteAudioFileIsAvailable() = runTest {
		val files = mutableMapOf<String, String>()
		val accepted = publishAudioDownload(
			finalPath = "winner.mp3",
			write = { files[it] = "complete audio" },
			isUsable = { files[it]?.isNotEmpty() == true },
			move = { from, to -> files[to] = files.remove(from)!!; true },
			complete = { path -> assertEquals("complete audio", files[path]); true },
			delete = { files.remove(it) }
		)
		assertTrue(accepted)
		assertEquals(mapOf("winner.mp3" to "complete audio"), files)
	}

	@Test
	fun cancellationRemovesPartialBytesWithoutTouchingOtherDownloads() = runTest {
		val files = mutableMapOf("other.mp3" to "keep")
		assertFailsWith<CancellationException> {
			publishAudioDownload(
				finalPath = "cancelled.mp3",
				write = { files[it] = "partial"; throw CancellationException() },
				isUsable = { true },
				move = { _, _ -> error("Cancelled output must not move") },
				complete = { error("Cancelled output must not publish") },
				delete = { files.remove(it) }
			)
		}
		assertEquals(mapOf("other.mp3" to "keep"), files)
	}

	@Test
	fun emptyDownloadCannotBecomeACompletedRow() = runTest {
		val files = mutableMapOf<String, String>()
		assertFailsWith<IllegalStateException> {
			publishAudioDownload(
				finalPath = "empty.mp3", write = { files[it] = "" },
				isUsable = { files[it]?.isNotEmpty() == true },
				move = { _, _ -> error("Empty file") }, complete = { error("Empty file") },
				delete = { files.remove(it) }
			)
		}
		assertTrue(files.isEmpty())
	}

	@Test
	fun databaseFailureRemovesOnlyTheUncommittedAttempt() = runTest {
		val files = mutableMapOf("other.mp3" to "keep")
		assertFailsWith<IllegalStateException> {
			publishAudioDownload(
				finalPath = "attempt.mp3", write = { files[it] = "audio" },
				isUsable = { true },
				move = { from, to -> files[to] = files.remove(from)!!; true },
				complete = { error("Commit failed") }, delete = { files.remove(it) }
			)
		}
		assertEquals(mapOf("other.mp3" to "keep"), files)
	}

	@Test
	fun rejectedRowDeletionDoesNotRemoveItsStillRegisteredAudio() = runTest {
		var fileExists = true
		assertFalse(deletePublishedAudio(deleteRow = { false }, deleteFiles = { fileExists = false }))
		assertTrue(fileExists)
	}

	@Test
	fun cancelledDeletionWaitsForCommitAcknowledgmentBeforeRemovingBytes() = runTest {
		val events = mutableListOf<String>()
		val deleted = CompletableDeferred<Unit>()
		val acknowledged = CompletableDeferred<Unit>()
		val operation = launch {
			deletePublishedAudio(
				deleteRow = {
					events += "row-deleted"
					deleted.complete(Unit)
					acknowledged.await()
					true
				},
				deleteFiles = { events += "file-deleted" }
			)
		}
		deleted.await()
		operation.cancel()
		assertEquals(listOf("row-deleted"), events)
		acknowledged.complete(Unit)
		operation.join()
		assertEquals(listOf("row-deleted", "file-deleted"), events)
	}
}
