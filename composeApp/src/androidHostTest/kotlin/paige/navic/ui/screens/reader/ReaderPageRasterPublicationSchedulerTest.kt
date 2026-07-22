package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageRasterPublicationSchedulerTest {
	@Test
	fun fixedWorkerLimitIsNeverExceeded() = runTest {
		val scheduler = ReaderPageRasterPublicationScheduler(
			scope = this,
			maxConcurrentWorkers = 2
		)
		val release = CompletableDeferred<Unit>()
		val started = List(3) { CompletableDeferred<Unit>() }
		var active = 0
		var peak = 0

		repeat(3) { index ->
			scheduler.schedule(
				ReaderPageRasterPublicationRequest("digest-$index", 0L)
			) {
				active += 1
				peak = maxOf(peak, active)
				started[index].complete(Unit)
				try {
					release.await()
				} finally {
					active -= 1
				}
			}
		}
		started[0].await()
		started[1].await()
		runCurrent()

		assertFalse(started[2].isCompleted)
		assertEquals(2, peak)
		release.complete(Unit)
		scheduler.closeAndJoin()
		assertEquals(0, scheduler.activeWorkerCount())
	}

	@Test
	fun cancelBeforeEpochNeverStartsQueuedWorkAndActiveWorkRunsFinally() =
		runTest {
			val scheduler = ReaderPageRasterPublicationScheduler(
				scope = this,
				maxConcurrentWorkers = 1
			)
			val activeStarted = CompletableDeferred<Unit>()
			val releaseActive = CompletableDeferred<Unit>()
			val activeFinally = CompletableDeferred<Unit>()
			var queuedStarted = false
			scheduler.schedule(
				ReaderPageRasterPublicationRequest("active", 0L)
			) {
				activeStarted.complete(Unit)
				try {
					withContext(NonCancellable) {
						releaseActive.await()
					}
				} finally {
					activeFinally.complete(Unit)
				}
			}
			activeStarted.await()
			scheduler.schedule(
				ReaderPageRasterPublicationRequest("queued", 0L)
			) {
				queuedStarted = true
			}

			scheduler.cancelBeforeEpoch(currentEpoch = 1L)
			releaseActive.complete(Unit)
			activeFinally.await()
			scheduler.closeAndJoin()

			assertFalse(queuedStarted)
			assertEquals(0, scheduler.activeWorkerCount())
		}

	@Test
	fun concurrentCloseCallersBothWaitForTheActiveWorkerFinally() = runTest {
		val scheduler = ReaderPageRasterPublicationScheduler(
			scope = this,
			maxConcurrentWorkers = 1
		)
		val activeStarted = CompletableDeferred<Unit>()
		val releaseActive = CompletableDeferred<Unit>()
		val activeFinally = CompletableDeferred<Unit>()
		scheduler.schedule(
			ReaderPageRasterPublicationRequest("active", 0L)
		) {
			activeStarted.complete(Unit)
			try {
				withContext(NonCancellable) {
					releaseActive.await()
				}
			} finally {
				activeFinally.complete(Unit)
			}
		}
		activeStarted.await()

		val firstClose = async { scheduler.closeAndJoin() }
		val secondClose = async { scheduler.closeAndJoin() }
		runCurrent()
		assertFalse(firstClose.isCompleted)
		assertFalse(secondClose.isCompleted)

		releaseActive.complete(Unit)
		activeFinally.await()
		firstClose.await()
		secondClose.await()
		assertEquals(0, scheduler.activeWorkerCount())
	}
}
