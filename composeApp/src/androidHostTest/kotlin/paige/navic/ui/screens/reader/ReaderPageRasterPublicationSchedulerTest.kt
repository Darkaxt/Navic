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
import kotlin.test.assertTrue
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind

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

	@Test
	fun freezeSnapshotsAndDrainsExactPublicationJobsBeforeRestoration() = runTest {
		val scheduler = ReaderPageRasterPublicationScheduler(
			scope = this,
			maxConcurrentWorkers = 1
		)
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val completedFinally = CompletableDeferred<Unit>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(ReaderPageRasterPublicationRequest("owned", 3L)) {
				started.complete(Unit)
				try {
					release.await()
				} finally {
					completedFinally.complete(Unit)
				}
			}
		)
		started.await()
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 21L,
			freezeToken = ReaderLegacyFreezeToken(22L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(domain)
		)
		val row = scheduler.snapshotFrozenOwnership().single()

		assertEquals(domain, row.physicalIdentity.domain)
		assertEquals(ReaderLegacyInventorySource.RasterPublication, row.physicalIdentity.source)
		assertEquals(ReaderTransitionResourceKind.Raster, row.kind)
		assertEquals(ReaderLegacyResourceState.Running, row.state)
		assertEquals(
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			),
			scheduler.schedule(ReaderPageRasterPublicationRequest("fenced", 3L)) { }
		)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
		)
		completedFinally.await()
		runCurrent()
		assertEquals(listOf(row.physicalIdentity), confirmations)
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(domain)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(ReaderPageRasterPublicationRequest("restored", 3L)) { }
		)
		release.complete(Unit)
		scheduler.closeAndJoin()
	}
}
