package paige.navic.domain.manager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadWorkCoordinatorTest {
	private val row = DownloadEntity("song", DownloadStatus.DOWNLOADING, ownerId = "owner")

	@Test
	fun cancellationFindsClaimedJobWhileSongMetadataIsStillLoading() = runTest {
		val coordinator = DownloadWorkCoordinator()
		val loading = CompletableDeferred<Unit>()
		val metadata = CompletableDeferred<Unit>()
		val cleanup = CompletableDeferred<Unit>()
		var cleanupFinished = false
		var downloaded = false
		val worker = launch {
			coordinator.runNext(claim = { row }) {
				try {
					loading.complete(Unit)
					metadata.await() // Suspended catalog lookup, before the transfer begins.
					downloaded = true
				} finally {
					withContext(NonCancellable) { cleanup.await(); cleanupFinished = true }
				}
			}
		}
		loading.await()
		val cancellation = async { coordinator.cancel(listOf("song")) { listOf(row) } }
		runCurrent()
		assertFalse(cancellation.isCompleted)
		assertFalse(coordinator.runNext(claim = { live ->
			assertEquals(setOf("song"), live)
			null
		}) { error("A replacement overlapped its cancelled predecessor") })
		cleanup.complete(Unit)
		cancellation.await()
		worker.join()
		assertTrue(cleanupFinished)
		assertFalse(downloaded)
		assertTrue(coordinator.runNext(claim = { live -> assertTrue(live.isEmpty()); row }) { downloaded = true })
		assertTrue(downloaded)
	}

	@Test
	fun concurrentWorkersCannotClaimSameSongAndNextSongRemainsAvailable() = runTest {
		val coordinator = DownloadWorkCoordinator()
		val queued = mutableListOf(row, row.copy(songId = "next"))
		val claimed = mutableListOf<String>()
		val release = CompletableDeferred<Unit>()
		val claim: suspend (Set<String>) -> DownloadEntity? = { live ->
			queued.firstOrNull { it.songId !in live }?.also { queued.remove(it) }
		}
		val first = launch { coordinator.runNext(claim) { claimed += it.songId; release.await() } }
		runCurrent()
		// Model a replacement intent admitted before the first transfer has drained.
		coordinator.serialized { queued.add(0, row.copy(intentGeneration = 1)) }
		val second = launch { coordinator.runNext(claim) { claimed += it.songId; release.await() } }
		runCurrent()
		assertEquals(listOf("song", "next"), claimed)
		assertFalse(coordinator.runNext(claim) { error("Duplicate song writer") })
		release.complete(Unit)
		first.join()
		second.join()
		assertTrue(coordinator.runNext(claim) { assertEquals(1L, it.intentGeneration); claimed += it.songId })
		assertEquals(listOf("song", "next", "song"), claimed)
	}

	@Test
	fun cancelledSessionJoinsWorkerAndReleasesItsClaimBeforeReturning() = runTest {
		val coordinator = DownloadWorkCoordinator()
		val started = CompletableDeferred<Unit>()
		var cleaned = false
		val session = launch {
			coordinator.runNext(claim = { row }) {
				try { started.complete(Unit); awaitCancellation() }
				finally { cleaned = true }
			}
		}
		started.await()
		session.cancelAndJoin()
		assertTrue(cleaned)
		assertTrue(coordinator.runNext(claim = { live -> assertTrue(live.isEmpty()); row }) {})
	}
}
