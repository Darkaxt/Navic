package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderTransitionResourceKind

@OptIn(ExperimentalCoroutinesApi::class)
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

	@Test
	fun freezeFencesNewWorkersAndSnapshotsEveryOwnedJobWithStableExactIdentity() = runTest {
		val release = CompletableDeferred<Unit>()
		val firstStarted = CompletableDeferred<Unit>()
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		checkNotNull(scheduler.schedule {
			firstStarted.complete(Unit)
			release.await()
		})
		firstStarted.await()
		checkNotNull(scheduler.schedule { release.await() })
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 7L,
			freezeToken = ReaderLegacyFreezeToken(11L)
		)

		scheduler.freezeForTransitionActivation(domain)
		val first = scheduler.snapshotFrozenOwnership()
		val second = scheduler.snapshotFrozenOwnership()

		assertNull(scheduler.schedule { })
		assertEquals(first, second)
		assertEquals(2, first.size)
		assertTrue(first.all { row ->
			row.freezeToken == domain.freezeToken &&
				row.physicalIdentity.domain == domain &&
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterHydration &&
				row.kind == ReaderTransitionResourceKind.Raster &&
				!row.mayBeCommittedPredecessor
		})
		assertEquals(first.size, first.map { it.physicalIdentity }.toSet().size)
		release.complete(Unit)
		scheduler.closeAndJoin()
	}

	@Test
	fun exactFrozenJobDrainConfirmsOnlyItsCompletePhysicalIdentity() = runTest {
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
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 9L,
			freezeToken = ReaderLegacyFreezeToken(13L)
		)
		scheduler.freezeForTransitionActivation(domain)
		val identity = scheduler.snapshotFrozenOwnership().single().physicalIdentity
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.drainFrozenOwnership(identity, confirmations::add)
		)
		runCurrent()

		assertTrue(finallyReached)
		assertEquals(listOf(identity), confirmations)
		assertEquals(emptyList(), scheduler.snapshotFrozenOwnership())
		val wrongSource = identity.copy(source = ReaderLegacyInventorySource.RasterPublication)
		assertEquals(
			ReaderPortCommandResult.Rejected(
				paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
			),
			scheduler.drainFrozenOwnership(wrongSource, confirmations::add)
		)
		release.complete(Unit)
		scheduler.closeAndJoin()
	}

	@Test
	fun restorationReopensWorkerAdmissionOnlyForTheSameFrozenDomain() = runTest {
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 12L,
			freezeToken = ReaderLegacyFreezeToken(17L)
		)
		scheduler.freezeForTransitionActivation(domain)
		assertNull(scheduler.schedule { })

		assertEquals(
			ReaderPortCommandResult.Rejected(
				paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
			),
			scheduler.restoreAfterTransitionActivation(
				domain.copy(freezeToken = ReaderLegacyFreezeToken(18L))
			)
		)
		assertNull(scheduler.schedule { })
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(domain)
		)
		assertTrue(checkNotNull(scheduler.schedule { }).also { runCurrent() }.isCompleted)
		scheduler.closeAndJoin()
	}

	@Test
	fun completedFrozenJobRetainsItsReleasedIdentityUntilExactDrain() = runTest {
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val job = checkNotNull(scheduler.schedule {
			started.complete(Unit)
			release.await()
		})
		started.await()
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 19L,
			freezeToken = ReaderLegacyFreezeToken(20L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(domain)
		)
		val identity = scheduler.snapshotFrozenOwnership().single().physicalIdentity

		release.complete(Unit)
		job.join()
		runCurrent()

		val released = scheduler.snapshotFrozenOwnership().single()
		assertEquals(identity, released.physicalIdentity)
		assertEquals(ReaderLegacyResourceState.Released, released.state)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.drainFrozenOwnership(identity, confirmations::add)
		)
		assertEquals(listOf(identity), confirmations)
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(domain)
		)
		scheduler.closeAndJoin()
	}

	@Test
	fun restorationDiscardsACompletedFrozenJobBeforeTheNextFreeze() = runTest {
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		val release = CompletableDeferred<Unit>()
		val started = CompletableDeferred<Unit>()
		val job = checkNotNull(scheduler.schedule {
			started.complete(Unit)
			release.await()
		})
		started.await()
		val firstDomain = ReaderLegacyPhysicalDomain(25L, ReaderLegacyFreezeToken(26L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(firstDomain)
		)
		release.complete(Unit)
		job.join()
		runCurrent()
		assertEquals(
			ReaderLegacyResourceState.Released,
			scheduler.snapshotFrozenOwnership().single().state
		)

		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(firstDomain)
		)
		val secondDomain = ReaderLegacyPhysicalDomain(25L, ReaderLegacyFreezeToken(27L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(secondDomain)
		)
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(secondDomain)
		)
		scheduler.closeAndJoin()
	}

	@Test
	fun restorationWaitsForAnAcceptedDrainToPhysicallyComplete() = runTest {
		val scheduler = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val job = checkNotNull(scheduler.schedule {
			started.complete(Unit)
			withContext(NonCancellable) { release.await() }
		})
		started.await()
		val domain = ReaderLegacyPhysicalDomain(33L, ReaderLegacyFreezeToken(34L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(domain)
		)
		val identity = scheduler.snapshotFrozenOwnership().single().physicalIdentity
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.drainFrozenOwnership(identity, confirmations::add)
		)
		runCurrent()

		val prematureRestore = scheduler.restoreAfterTransitionActivation(domain)
		assertTrue(confirmations.isEmpty())
		release.complete(Unit)
		job.join()
		runCurrent()
		assertEquals(listOf(identity), confirmations)
		if (prematureRestore != ReaderPortCommandResult.Accepted) {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				scheduler.restoreAfterTransitionActivation(domain)
			)
		}
		scheduler.closeAndJoin()
		assertEquals(
			ReaderPortCommandResult.Rejected(
				paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
			),
			prematureRestore
		)
	}
}
