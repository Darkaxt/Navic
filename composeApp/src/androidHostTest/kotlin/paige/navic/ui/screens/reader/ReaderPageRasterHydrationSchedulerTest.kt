package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageRasterHydrationSchedulerTest {
	@Test
	fun workersNeverExceedConfiguredConcurrency() = runTest {
		val release = CompletableDeferred<Unit>()
		val twoStarted = CompletableDeferred<Unit>()
		var active = 0
		var maximum = 0
		val scheduler = ReaderPageRasterHydrationScheduler(
			scope = backgroundScope,
			maxConcurrentWorkers = 2
		)
		val jobs = List(4) {
			checkNotNull(scheduler.schedule {
				active += 1
				maximum = maxOf(maximum, active)
				if (active == 2) twoStarted.complete(Unit)
				try {
					release.await()
				} finally {
					active -= 1
				}
			})
		}

		twoStarted.await()
		assertEquals(2, scheduler.activeWorkerCount)
		assertEquals(2, maximum)
		release.complete(Unit)
		jobs.joinAll()
		assertEquals(0, scheduler.activeWorkerCount)
		scheduler.closeAndJoin()
	}

	@Test
	fun cancellingQueuedJobPreventsItsWorkerFromStarting() = runTest {
		val activeStarted = CompletableDeferred<Unit>()
		val releaseActive = CompletableDeferred<Unit>()
		var queuedStarted = false
		val scheduler = ReaderPageRasterHydrationScheduler(
			scope = backgroundScope,
			maxConcurrentWorkers = 1
		)
		val active = checkNotNull(scheduler.schedule {
			activeStarted.complete(Unit)
			releaseActive.await()
		})
		activeStarted.await()
		val queued = checkNotNull(scheduler.schedule { queuedStarted = true })

		queued.cancel()
		releaseActive.complete(Unit)
		joinAll(active, queued)

		assertFalse(queuedStarted)
		scheduler.closeAndJoin()
	}

	@Test
	fun closeCancelsActiveWorkerAndWaitsForFinally() = runTest {
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		var finallyReached = false
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		checkNotNull(scheduler.schedule {
			try {
				started.complete(Unit)
				release.await()
			} finally {
				finallyReached = true
			}
		})
		started.await()

		scheduler.closeAndJoin()

		assertTrue(finallyReached)
		assertEquals(0, scheduler.activeWorkerCount)
		assertNull(scheduler.schedule { })
	}

	@Test
	fun concurrentCloseCallersCompleteAfterTheSameWorkers() = runTest {
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		checkNotNull(scheduler.schedule {
			started.complete(Unit)
			release.await()
		})
		started.await()

		val closers = listOf(
			async { scheduler.closeAndJoin() },
			async { scheduler.closeAndJoin() }
		)
		runCurrent()
		closers.awaitAll()

		assertTrue(closers.all { it.isCompleted })
	}
}
