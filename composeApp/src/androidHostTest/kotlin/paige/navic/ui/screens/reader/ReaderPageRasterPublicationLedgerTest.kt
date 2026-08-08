package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageRasterPublicationLedgerTest {
	@Test
	fun invalidationRejectsQueuedWorkAndReleasesItsStagedValue() {
		val released = mutableListOf<String>()
		val callbacks = mutableListOf<Boolean>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 2,
			callbackLimit = 4,
			release = released::add
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "queued", callbacks::add)
		)

		ledger.invalidate()

		assertNull(ledger.acquireForPersistence(started.request))
		assertEquals(listOf(false), callbacks)
		assertEquals(listOf("queued"), released)
	}

	@Test
	fun invalidationDoesNotReleaseValueWhileWorkerStillOwnsIt() {
		val released = mutableListOf<String>()
		val callbacks = mutableListOf<Boolean>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 2,
			callbackLimit = 4,
			release = released::add
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "active", callbacks::add)
		)
		assertEquals("active", ledger.acquireForPersistence(started.request))

		ledger.invalidate()

		assertEquals(listOf(false), callbacks)
		assertTrue(released.isEmpty())
		assertFalse(ledger.complete(started.request, persisted = true))
		assertEquals(listOf("active"), released)
	}

	@Test
	fun pausedOldCompletionCannotSatisfySameDigestInNewEpoch() {
		val released = mutableListOf<String>()
		val results = mutableListOf<Pair<String, Boolean>>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 2,
			callbackLimit = 4,
			release = released::add
		)
		val old = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "old") { results += "old" to it }
		)
		assertEquals("old", ledger.acquireForPersistence(old.request))

		ledger.invalidate()
		val current = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "new") { results += "new" to it }
		)
		assertEquals("new", ledger.acquireForPersistence(current.request))

		assertFalse(ledger.complete(old.request, persisted = true))
		assertTrue(ledger.complete(current.request, persisted = true))
		assertEquals(listOf("old" to false, "new" to true), results)
		assertEquals(listOf("old", "new"), released)
	}

	@Test
	fun duplicateRegistrationCoalescesWithoutSchedulingReleasedValue() {
		val released = mutableListOf<String>()
		val callbacks = mutableListOf<Pair<String, Boolean>>()
		val scheduled = mutableListOf<ReaderPageRasterPublicationRequest>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 2,
			callbackLimit = 4,
			release = released::add
		)

		val first = ledger.begin("digest", "producer") {
			callbacks += "producer" to it
		}
		if (first is ReaderPageRasterPublicationRegistration.Started) {
			scheduled += first.request
		}
		val duplicate = ledger.begin("digest", "duplicate") {
			callbacks += "duplicate" to it
		}
		if (duplicate is ReaderPageRasterPublicationRegistration.Started) {
			scheduled += duplicate.request
		}

		val request =
			assertIs<ReaderPageRasterPublicationRegistration.Started>(first).request
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(duplicate)
		assertEquals(listOf(request), scheduled)
		assertEquals("producer", ledger.acquireForPersistence(request))
		assertTrue(ledger.complete(request, persisted = true))
		assertEquals(
			listOf("producer" to true, "duplicate" to true),
			callbacks
		)
		assertEquals(listOf("duplicate", "producer"), released)
	}

	@Test
	fun coalescingIsScopedToTheForegroundMutationGeneration() {
		val callbacks = mutableListOf<Pair<String, Boolean>>()
		val released = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 1,
			callbackLimit = 4,
			release = released::add
		)
		val firstGeneration = ReaderForegroundWebViewMutationGeneration(1L)
		val successorGeneration = ReaderForegroundWebViewMutationGeneration(2L)

		val producer = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "producer", firstGeneration) {
				callbacks += "producer" to it
			}
		)
		val sameGeneration = ledger.begin("digest", "same-generation", firstGeneration) {
			callbacks += "same-generation" to it
		}
		val successor = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "successor", successorGeneration) {
				callbacks += "successor" to it
			}
		)

		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(sameGeneration)
		assertEquals("producer", ledger.acquireForPersistence(producer.request))
		assertTrue(ledger.complete(producer.request, persisted = false))
		assertEquals(
			listOf("producer" to false, "same-generation" to false),
			callbacks
		)

		assertEquals("successor", ledger.acquireForPersistence(successor.request))
		assertTrue(ledger.complete(successor.request, persisted = true))
		assertEquals(
			listOf(
				"producer" to false,
				"same-generation" to false,
				"successor" to true
			),
			callbacks
		)
		assertEquals(
			listOf("same-generation", "producer", "successor"),
			released
		)
	}

	@Test
	fun coalescedPublicationCannotConsumePendingFaultRetryCorrelation() {
		val ledger = ReaderPageRasterPublicationLedger<String> { }
		val correlation = ReaderPageQaFaultCorrelation(
			requestId = "persist-retry",
			appliedOperation = ReaderPageQaFaultOperationContext(
				persistenceAttemptId = 1L
			),
			relation = ReaderPageQaFaultRelation.AppliedOperation
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "producer") { }
		)
		assertEquals("producer", ledger.acquireForPersistence(started.request))
		val duplicate = assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("digest", "duplicate") { }
		)

		assertNull(
			readerPageRasterPublicationRetryCorrelation(duplicate, correlation)
		)
		assertTrue(ledger.complete(started.request, persisted = false))
		val retry = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "retry") { }
		)
		assertEquals(
			correlation.withRelation(ReaderPageQaFaultRelation.Retry),
			readerPageRasterPublicationRetryCorrelation(retry, correlation)
		)
	}

	@Test
	fun callbackAndReleaseFailuresDoNotStrandEntriesOrSuppressLaterDispatch() {
		val callbacks = mutableListOf<String>()
		val releases = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value ->
				releases += value
				if (value == "producer") error("release-failed")
			}
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "producer") {
				callbacks += "first"
				error("callback-failed")
			}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("digest", "duplicate") {
				callbacks += "second"
			}
		)
		assertEquals("producer", ledger.acquireForPersistence(started.request))

		assertTrue(ledger.complete(started.request, persisted = true))

		assertEquals(listOf("first", "second"), callbacks)
		assertEquals(listOf("duplicate", "producer"), releases)
		assertEquals(0, ledger.entryCount())
		assertNotNull(ledger.dispatchFailure())
	}

	@Test
	fun invalidationAttemptsEveryCallbackAndQueuedReleaseAfterFailure() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value ->
				events += "release-$value"
				if (value == "a") error("release-a")
			}
		)
		ledger.begin("a", "a") {
			events += "callback-a"
			error("callback-a")
		}
		ledger.begin("b", "b") {
			events += "callback-b"
		}

		ledger.invalidate()

		assertTrue("callback-a" in events)
		assertTrue("callback-b" in events)
		assertTrue("release-a" in events)
		assertTrue("release-b" in events)
		assertEquals(0, ledger.entryCount())
		assertNotNull(ledger.dispatchFailure())
	}

	@Test
	fun configuredCapacityRejectsAndReleasesWithoutAddingOwnership() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 1,
			release = { events += "release-$it" }
		)
		ledger.begin("first", "first") { events += "first-$it" }
		assertEquals(1, ledger.currentEpochEntryLimit)
		assertEquals(1, ledger.staleActiveDrainLimit)
		assertEquals(2, ledger.entryLimit)
		assertEquals(1, ledger.callbackLimit)
		assertEquals(1, ledger.currentEpochEntryCount())
		assertEquals(0, ledger.staleActiveEntryCount())
		assertEquals(1, ledger.callbackCount())

		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.CallbackCapacity
			),
			ledger.begin("first", "duplicate") {
				events += "duplicate-$it"
			}
		)
		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.EntryCapacity
			),
			ledger.begin("second", "second") { events += "second-$it" }
		)
		assertEquals(1, ledger.entryCount())
		assertEquals(1, ledger.currentEpochEntryCount())
		assertEquals(1, ledger.callbackCount())
		assertEquals(
			listOf(
				"duplicate-false",
				"release-duplicate",
				"second-false",
				"release-second"
			),
			events
		)
	}

	@Test
	fun acquiringBeyondPersistenceWorkerLimitIsAConfigurationError() {
		val released = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = released::add
		)
		val first = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("first", "first") {}
		)
		val second = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("second", "second") {}
		)
		assertEquals("first", ledger.acquireForPersistence(first.request))

		assertFailsWith<IllegalStateException> {
			ledger.acquireForPersistence(second.request)
		}

		assertTrue(ledger.complete(first.request, persisted = true))
		assertEquals("second", ledger.acquireForPersistence(second.request))
		assertTrue(ledger.complete(second.request, persisted = true))
		assertEquals(listOf("first", "second"), released)
	}

	@Test
	fun fullNewEpochFitsWhileMixedStaleWorkersStayBoundedAcrossInvalidations() {
		val released = mutableListOf<String>()
		val rejected = mutableListOf<Boolean>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 2,
			callbackLimit = 4,
			release = released::add
		)
		val oldA = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("old-a", "old-a") {}
		)
		val oldB = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("old-b", "old-b") {}
		)
		assertEquals("old-a", ledger.acquireForPersistence(oldA.request))
		assertEquals("old-b", ledger.acquireForPersistence(oldB.request))

		ledger.invalidate()

		assertEquals(2, ledger.staleActiveEntryCount())
		assertEquals(2, ledger.staleActiveDrainLimit)
		val currentA = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("current-a", "current-a") {}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("current-b", "current-b") {}
		)
		assertEquals(2, ledger.currentEpochEntryCount())
		assertEquals(ledger.entryLimit, ledger.entryCount())
		val overflow = assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("overflow", "overflow", rejected::add)
		)
		assertEquals(
			ReaderPageRasterPublicationRejection.EntryCapacity,
			overflow.reason
		)
		assertEquals(listOf(false), rejected)
		assertTrue("overflow" in released)

		assertFalse(ledger.complete(oldA.request, persisted = true))
		assertEquals(1, ledger.staleActiveEntryCount())
		assertEquals(
			"current-a",
			ledger.acquireForPersistence(currentA.request)
		)

		ledger.invalidate()

		assertEquals(2, ledger.staleActiveEntryCount())
		assertEquals(2, ledger.staleActiveDrainLimit)
		assertEquals(2, ledger.entryCount())
		val nextA = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("next-a", "next-a") {}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("next-b", "next-b") {}
		)
		assertEquals(2, ledger.currentEpochEntryCount())
		assertEquals(ledger.entryLimit, ledger.entryCount())

		assertFalse(ledger.complete(oldB.request, persisted = true))
		assertFalse(ledger.complete(currentA.request, persisted = true))
		assertEquals("next-a", ledger.acquireForPersistence(nextA.request))

		ledger.invalidate()

		assertEquals(1, ledger.staleActiveEntryCount())
		assertTrue(
			ledger.staleActiveEntryCount() <= ledger.staleActiveDrainLimit
		)
		val finalA = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("final-a", "final-a") {}
		)
		val finalB = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("final-b", "final-b") {}
		)
		assertEquals(2, ledger.currentEpochEntryCount())
		assertTrue(ledger.entryCount() <= ledger.entryLimit)

		assertFalse(ledger.complete(nextA.request, persisted = true))
		assertEquals("final-a", ledger.acquireForPersistence(finalA.request))
		assertEquals("final-b", ledger.acquireForPersistence(finalB.request))
		assertTrue(ledger.complete(finalA.request, persisted = true))
		assertTrue(ledger.complete(finalB.request, persisted = true))

		assertEquals(0, ledger.entryCount())
		assertEquals(released.size, released.distinct().size)
	}

	@Test
	fun productionSchedulerBoundAllowsFullEpochDuringRepeatedInvalidation() = runTest {
		val released = mutableListOf<String>()
		val scheduler = ReaderPageRasterPublicationScheduler(
			scope = this,
			maxConcurrentWorkers = 1
		)
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = scheduler.maxConcurrentWorkers,
			callbackLimit = 4,
			release = released::add
		)
		val oldStarted = CompletableDeferred<Unit>()
		val releaseOld = CompletableDeferred<Unit>()
		val oldFinished = CompletableDeferred<Unit>()
		val old = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("old", "old") {}
		)
		scheduler.schedule(old.request) {
			val value = ledger.acquireForPersistence(old.request)
				?: return@schedule
			assertEquals("old", value)
			oldStarted.complete(Unit)
			var persisted = false
			try {
				withContext(NonCancellable) {
					releaseOld.await()
				}
				persisted = true
			} finally {
				ledger.complete(old.request, persisted)
				oldFinished.complete(Unit)
			}
		}
		oldStarted.await()

		ledger.invalidate()
		scheduler.cancelBeforeEpoch(ledger.currentEpoch())
		assertEquals(1, ledger.staleActiveEntryCount())
		assertTrue(
			ledger.staleActiveEntryCount() <= scheduler.maxConcurrentWorkers
		)

		val currentA = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("current-a", "current-a") {}
		)
		val currentB = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("current-b", "current-b") {}
		)
		listOf(currentA.request, currentB.request).forEach { request ->
			scheduler.schedule(request) {
				val value = ledger.acquireForPersistence(request)
					?: return@schedule
				ledger.complete(request, persisted = value.isNotEmpty())
			}
		}
		assertEquals(ledger.entryLimit, ledger.entryCount())

		ledger.invalidate()
		scheduler.cancelBeforeEpoch(ledger.currentEpoch())
		assertEquals(1, ledger.staleActiveEntryCount())
		assertEquals(1, ledger.entryCount())

		val nextAResult = CompletableDeferred<Boolean>()
		val nextBResult = CompletableDeferred<Boolean>()
		val nextA = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("next-a", "next-a", nextAResult::complete)
		)
		val nextB = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("next-b", "next-b", nextBResult::complete)
		)
		listOf(nextA.request, nextB.request).forEach { request ->
			scheduler.schedule(request) {
				val value = ledger.acquireForPersistence(request)
					?: return@schedule
				ledger.complete(request, persisted = value.isNotEmpty())
			}
		}
		assertEquals(2, ledger.currentEpochEntryCount())
		assertEquals(ledger.entryLimit, ledger.entryCount())
		assertTrue(
			ledger.staleActiveEntryCount() <= scheduler.maxConcurrentWorkers
		)

		releaseOld.complete(Unit)
		oldFinished.await()
		assertTrue(nextAResult.await())
		assertTrue(nextBResult.await())
		scheduler.closeAndJoin()

		assertEquals(0, scheduler.activeWorkerCount())
		assertEquals(0, ledger.entryCount())
		assertEquals(released.size, released.distinct().size)
	}

	@Test
	fun coalescedWaitersStopAtCallbackLimitAndCompletionReturnsCapacity() {
		val results = mutableListOf<Boolean>()
		val released = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = released::add
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("page", "owner", results::add)
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("page", "coalesced", results::add)
		)
		val rejected = assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("page", "overflow", results::add)
		)

		assertEquals(
			ReaderPageRasterPublicationRejection.CallbackCapacity,
			rejected.reason
		)
		assertEquals(2, ledger.callbackCount())
		assertEquals(listOf(false), results)
		assertTrue("overflow" in released)
		assertEquals("owner", ledger.acquireForPersistence(started.request))
		assertTrue(ledger.complete(started.request, persisted = true))
		assertEquals(listOf(false, true, true), results)
		assertEquals(0, ledger.callbackCount())

		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("next", "next", results::add)
		)
		assertEquals(1, ledger.callbackCount())
		ledger.invalidate()
		assertEquals(0, ledger.callbackCount())
	}

	@Test
	fun hotDigestStopsAtTwoCallbacksWithoutStarvingOtherProductionEntries() {
		val released = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 11,
			persistenceWorkerLimit = 1,
			callbackLimit = 22,
			release = released::add
		)
		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("hot", "hot-owner") {}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("hot", "hot-waiter") {}
		)
		val rejected = assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("hot", "hot-overflow") {}
		)

		assertEquals(
			ReaderPageRasterPublicationRejection.CallbackCapacity,
			rejected.reason
		)
		(1..10).forEach { index ->
			assertIs<ReaderPageRasterPublicationRegistration.Started>(
				ledger.begin("cold-$index", "cold-$index") {}
			)
		}
		assertEquals(11, ledger.currentEpochEntryCount())
		assertEquals(12, ledger.callbackCount())
		assertEquals(listOf("hot-waiter", "hot-overflow"), released)
		ledger.invalidate()
	}

	@Test
	fun rejectedDemandRetriesFromTheCompletionCapacityEdge() {
		val events = mutableListOf<String>()
		val listener: () -> Unit = { events += "capacity" }
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { events += "release-$it" }
		)
		ledger.setCapacityAvailableListener(listener)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("page", "owner") { events += "owner-$it" }
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("page", "waiter") { events += "waiter-$it" }
		)
		assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("page", "overflow") { events += "overflow-$it" }
		)
		assertEquals("owner", ledger.acquireForPersistence(started.request))

		assertTrue(ledger.complete(started.request, persisted = true))

		assertEquals(
			listOf(
				"release-waiter",
				"overflow-false",
				"release-overflow",
				"owner-true",
				"waiter-true",
				"release-owner",
				"capacity"
			),
			events
		)
		ledger.clearCapacityAvailableListener(listener)
	}

	@Test
	fun ownershipObserverRunsOnlyAfterAcceptedOwnerMutations() {
		val ownershipCounts = mutableListOf<Pair<Int, Int>>()
		lateinit var ledger: ReaderPageRasterPublicationLedger<String>
		ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			onOwnershipMutated = {
				ownershipCounts += ledger.entryCount() to ledger.callbackCount()
			},
			release = {}
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("page", "owner") {}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("page", "waiter") {}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("page", "overflow") {}
		)
		assertEquals(listOf(1 to 1, 1 to 2), ownershipCounts)

		assertEquals("owner", ledger.acquireForPersistence(started.request))
		assertTrue(ledger.complete(started.request, persisted = true))
		assertEquals(0 to 0, ownershipCounts.last())

		ledger.begin("next", "next") {}
		ledger.invalidate()
		ledger.invalidate()

		assertEquals(
			listOf(1 to 1, 1 to 2, 0 to 0, 1 to 1, 0 to 0),
			ownershipCounts
		)
	}
}
