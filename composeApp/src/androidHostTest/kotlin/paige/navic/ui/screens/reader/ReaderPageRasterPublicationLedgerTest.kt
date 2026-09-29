package paige.navic.ui.screens.reader

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
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
import paige.navic.reader.ReaderTransitionResourceKind

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

	@Test
	fun freezeRejectsLateCapacityListenerRegistrationBeforeOwnershipMutation() {
		val ledger = ReaderPageRasterPublicationLedger<String> { }
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 35L,
			freezeToken = ReaderLegacyFreezeToken(36L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)

		assertFailsWith<IllegalStateException> {
			ledger.setCapacityAvailableListener { }
		}
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun activeFrozenPublicationConfirmsCallbackAfterDispatchAndRasterAfterWorkerCompletion() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value: String -> events += "release-$value" }
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("active", "value") { events += "callback-$it" }
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 33L,
			freezeToken = ReaderLegacyFreezeToken(34L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(listOf("callback-false"), events)
		val callbackIdentity = rows.single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}.physicalIdentity
		assertEquals(setOf(callbackIdentity), confirmations.toSet())
		val workerRows = ledger.snapshotFrozenOwnership()
		assertEquals(3, workerRows.size)
		assertTrue(workerRows.all { row ->
			row.kind == ReaderTransitionResourceKind.Raster &&
				row.state == ReaderLegacyResourceState.ReleaseRequested
		})
		assertFalse(ledger.complete(started.request, persisted = true))
		assertEquals(listOf("callback-false", "release-value"), events)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun frozenInventorySeparatesEntryValueCallbacksAndCapacityListenerUntilExactDrain() {
		val events = mutableListOf<String>()
		val listener: () -> Unit = { events += "capacity" }
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 2,
			persistenceWorkerLimit = 1,
			callbackLimit = 4,
			release = { value: String -> events += "release-$value" }
		)
		ledger.setCapacityAvailableListener(listener)
		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "owner") { events += "owner-$it" }
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("digest", "waiter") { events += "waiter-$it" }
		)
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 31L,
			freezeToken = ReaderLegacyFreezeToken(32L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()

		assertEquals(6, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertTrue(rows.all { row ->
			row.physicalIdentity.domain == domain &&
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
		})
		assertEquals(3, rows.count { it.kind == ReaderTransitionResourceKind.Raster })
		assertEquals(3, rows.count { it.kind == ReaderTransitionResourceKind.CallbackRegistration })
		val fenced = assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("fenced", "fenced") { events += "fenced-$it" }
		)
		assertEquals(ReaderPageRasterPublicationRejection.ActivationFrozen, fenced.reason)
		val rowsAfterFencedCleanup = ledger.snapshotFrozenOwnership()
		assertEquals(8, rowsAfterFencedCleanup.size)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rowsAfterFencedCleanup.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}

		assertEquals(
			rowsAfterFencedCleanup.map { it.physicalIdentity }.toSet(),
			confirmations.toSet()
		)
		assertEquals(
			listOf(
				"release-waiter",
				"fenced-false",
				"release-fenced",
				"owner-false",
				"waiter-false",
				"release-owner"
			),
			events
		)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("restored", "restored") { }
		)
		ledger.invalidate()
	}

	@Test
	fun completionBeforeDrainRetainsEveryReleasedIdentityUntilExactConfirmation() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value: String -> events += "release-$value" }
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("completed", "value") { events += "callback-$it" }
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 37L,
			freezeToken = ReaderLegacyFreezeToken(38L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()

		assertTrue(ledger.complete(started.request, persisted = true))
		assertEquals(listOf("callback-true", "release-value"), events)
		val released = ledger.snapshotFrozenOwnership()
		assertEquals(rows.map { it.physicalIdentity }, released.map { it.physicalIdentity })
		assertTrue(released.all { it.state == ReaderLegacyResourceState.Released })
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		released.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun restorationDiscardsCompletedFrozenRowsBeforeTheNextFreeze() {
		val ledger = ReaderPageRasterPublicationLedger<String> { }
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("restored", "value") { }
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))
		val firstDomain = ReaderLegacyPhysicalDomain(39L, ReaderLegacyFreezeToken(40L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(firstDomain)
		)
		assertTrue(ledger.complete(started.request, persisted = true))
		assertTrue(ledger.snapshotFrozenOwnership().isNotEmpty())

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(firstDomain)
		)
		val secondDomain = ReaderLegacyPhysicalDomain(39L, ReaderLegacyFreezeToken(41L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(secondDomain)
		)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(secondDomain)
		)
	}

	@Test
	fun clearingFrozenCapacityListenerRetainsReleasedIdentityUntilDrain() {
		val ledger = ReaderPageRasterPublicationLedger<String> { }
		val listener: () -> Unit = { }
		ledger.setCapacityAvailableListener(listener)
		val domain = ReaderLegacyPhysicalDomain(43L, ReaderLegacyFreezeToken(44L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val identity = ledger.snapshotFrozenOwnership().single().physicalIdentity

		ledger.clearCapacityAvailableListener(listener)

		val released = ledger.snapshotFrozenOwnership().single()
		assertEquals(identity, released.physicalIdentity)
		assertEquals(ReaderLegacyResourceState.Released, released.state)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.drainFrozenOwnership(identity, confirmations::add)
		)
		assertEquals(listOf(identity), confirmations)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun queuedInvalidationRetainsEveryReleasedFrozenIdentityUntilDrain() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value: String -> events += "release-$value" }
		)
		assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("invalidated", "value") { events += "callback-$it" }
		)
		val domain = ReaderLegacyPhysicalDomain(45L, ReaderLegacyFreezeToken(46L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()

		ledger.invalidate()

		assertEquals(listOf("callback-false", "release-value"), events)
		val released = ledger.snapshotFrozenOwnership()
		assertEquals(rows.map { it.physicalIdentity }, released.map { it.physicalIdentity })
		assertTrue(released.all { it.state == ReaderLegacyResourceState.Released })
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		released.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun drainDuringQueuedInvalidationWaitsForCallbackAndReleasesValueOnce() {
		val callbackStarted = CountDownLatch(1)
		val allowCallbackReturn = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val releaseCount = AtomicInteger()
		val ledger = ReaderPageRasterPublicationLedger<String> {
			releaseCount.incrementAndGet()
		}
		ledger.begin("queued-drain", "value") {
			callbackStarted.countDown()
			callbackReturned.set(allowCallbackReturn.await(5, TimeUnit.SECONDS))
		}
		val domain = ReaderLegacyPhysicalDomain(49L, ReaderLegacyFreezeToken(50L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val invalidation = thread(name = "drained-publication-invalidation") {
			ledger.invalidate()
		}

		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			rows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertEquals(0, releaseCount.get())
			assertTrue(confirmations.isEmpty())
		} finally {
			allowCallbackReturn.countDown()
			invalidation.join(5_000L)
		}

		assertFalse(invalidation.isAlive)
		assertTrue(callbackReturned.get())
		assertEquals(1, releaseCount.get())
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun concurrentInvalidationsDispatchQueuedCallbackAndReleaseOnlyOnce() {
		val callbackStarted = CountDownLatch(1)
		val releaseCallback = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val callbackCount = AtomicInteger()
		val releaseCount = AtomicInteger()
		val ledger = ReaderPageRasterPublicationLedger<String> {
			releaseCount.incrementAndGet()
		}
		ledger.begin("queued-callback", "value") {
			if (callbackCount.incrementAndGet() == 1) {
				callbackStarted.countDown()
				callbackReturned.set(releaseCallback.await(5, TimeUnit.SECONDS))
			}
		}
		val firstInvalidation = thread(name = "first-publication-invalidation") {
			ledger.invalidate()
		}

		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			ledger.invalidate()
		} finally {
			releaseCallback.countDown()
			firstInvalidation.join(5_000L)
		}

		assertFalse(firstInvalidation.isAlive)
		assertTrue(callbackReturned.get())
		assertEquals(1, callbackCount.get())
		assertEquals(1, releaseCount.get())
	}

	@Test
	fun workerCompletionDuringInvalidationCallbackDoesNotDispatchCallbackTwice() {
		val callbackStarted = CountDownLatch(1)
		val releaseCallback = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val callbackCount = AtomicInteger()
		val ledger = ReaderPageRasterPublicationLedger<String> { }
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("active-callback", "value") {
				if (callbackCount.incrementAndGet() == 1) {
					callbackStarted.countDown()
					callbackReturned.set(releaseCallback.await(5, TimeUnit.SECONDS))
				}
			}
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))
		val invalidation = thread(name = "publication-invalidation") {
			ledger.invalidate()
		}

		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			assertFalse(ledger.complete(started.request, persisted = true))
		} finally {
			releaseCallback.countDown()
			invalidation.join(5_000L)
		}

		assertFalse(invalidation.isAlive)
		assertTrue(callbackReturned.get())
		assertEquals(1, callbackCount.get())
	}

	@Test
	fun activeInvalidationReleasesCallbackBeforeWorkerOwnersAndBothDrainOrdersConverge() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value: String -> events += "release-$value" }
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("active-invalidated", "value") { events += "callback-$it" }
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))
		val domain = ReaderLegacyPhysicalDomain(47L, ReaderLegacyFreezeToken(48L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		val callbackIdentity = rows.single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}.physicalIdentity
		val rasterIdentities = rows.filter {
			it.kind == ReaderTransitionResourceKind.Raster
		}.map { it.physicalIdentity }

		ledger.invalidate()

		assertEquals(listOf("callback-false"), events)
		val invalidated = ledger.snapshotFrozenOwnership()
		assertEquals(rows.map { it.physicalIdentity }.toSet(), invalidated.map { it.physicalIdentity }.toSet())
		assertEquals(
			ReaderLegacyResourceState.Released,
			invalidated.single { it.physicalIdentity == callbackIdentity }.state
		)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.drainFrozenOwnership(callbackIdentity, confirmations::add)
		)
		assertEquals(listOf(callbackIdentity), confirmations)
		rasterIdentities.forEach { identity ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(identity, confirmations::add)
			)
		}
		assertEquals(listOf(callbackIdentity), confirmations)

		assertFalse(ledger.complete(started.request, persisted = true))
		assertEquals(listOf("callback-false", "release-value"), events)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun normalCompletionKeepsEveryExactOwnerUntilCallbackAndReleaseReturn() {
		val domain = ReaderLegacyPhysicalDomain(51L, ReaderLegacyFreezeToken(52L))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val drainResults = mutableListOf<ReaderPortCommandResult>()
		var callbackRows = emptyList<ReaderFrozenLegacyResource>()
		var releaseRows = emptyList<ReaderFrozenLegacyResource>()
		var confirmationsDuringCallback = emptyList<ReaderLegacyPhysicalIdentity>()
		var confirmationsDuringRelease = emptyList<ReaderLegacyPhysicalIdentity>()
		var callbackReturned = false
		var releaseObservedCallbackReturn = false
		var releaseReturned = false
		lateinit var ledger: ReaderPageRasterPublicationLedger<String>
		ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 1,
			release = {
				releaseObservedCallbackReturn = callbackReturned
				releaseRows = ledger.snapshotFrozenOwnership()
				confirmationsDuringRelease = confirmations.toList()
				releaseReturned = true
			}
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("normal-completion", "value") {
				assertTrue(it)
				assertEquals(
					ReaderPortCommandResult.Accepted,
					ledger.freezeForTransitionActivation(domain)
				)
				callbackRows = ledger.snapshotFrozenOwnership()
				callbackRows.forEach { row ->
					drainResults += ledger.drainFrozenOwnership(
						row.physicalIdentity,
						confirmations::add
					)
				}
				confirmationsDuringCallback = confirmations.toList()
				callbackReturned = true
			}
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))

		assertTrue(ledger.complete(started.request, persisted = true))

		assertEquals(4, callbackRows.size)
		assertEquals(
			3,
			callbackRows.count { it.kind == ReaderTransitionResourceKind.Raster }
		)
		assertEquals(
			1,
			callbackRows.count {
				it.kind == ReaderTransitionResourceKind.CallbackRegistration
			}
		)
		assertTrue(drainResults.all { it == ReaderPortCommandResult.Accepted })
		assertTrue(confirmationsDuringCallback.isEmpty())
		assertTrue(releaseObservedCallbackReturn)
		assertEquals(
			callbackRows.map { it.physicalIdentity }.toSet(),
			releaseRows.map { it.physicalIdentity }.toSet()
		)
		assertTrue(
			releaseRows.all { it.state == ReaderLegacyResourceState.ReleaseRequested }
		)
		assertTrue(confirmationsDuringRelease.isEmpty())
		assertTrue(releaseReturned)
		assertEquals(
			callbackRows.map { it.physicalIdentity }.toSet(),
			confirmations.toSet()
		)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun frozenBeginCleanupRemainsExactlyOwnedUntilCallbackAndReleaseReturn() {
		val domain = ReaderLegacyPhysicalDomain(53L, ReaderLegacyFreezeToken(54L))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val drainResults = mutableListOf<ReaderPortCommandResult>()
		val callbackResults = mutableListOf<Boolean>()
		var callbackRows = emptyList<ReaderFrozenLegacyResource>()
		var releaseRows = emptyList<ReaderFrozenLegacyResource>()
		var confirmationsDuringCallback = emptyList<ReaderLegacyPhysicalIdentity>()
		var confirmationsDuringRelease = emptyList<ReaderLegacyPhysicalIdentity>()
		var callbackReturned = false
		var releaseObservedCallbackReturn = false
		lateinit var ledger: ReaderPageRasterPublicationLedger<String>
		ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 1,
			release = {
				releaseObservedCallbackReturn = callbackReturned
				releaseRows = ledger.snapshotFrozenOwnership()
				confirmationsDuringRelease = confirmations.toList()
			}
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)

		val registration = ledger.begin("frozen-rejection", "value") { result ->
			callbackResults += result
			callbackRows = ledger.snapshotFrozenOwnership()
			callbackRows.forEach { row ->
				drainResults += ledger.drainFrozenOwnership(
					row.physicalIdentity,
					confirmations::add
				)
			}
			confirmationsDuringCallback = confirmations.toList()
			callbackReturned = true
		}

		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.ActivationFrozen
			),
			registration
		)
		assertEquals(listOf(false), callbackResults)
		assertEquals(2, callbackRows.size)
		assertEquals(
			setOf(
				ReaderTransitionResourceKind.CallbackRegistration,
				ReaderTransitionResourceKind.Raster
			),
			callbackRows.map { it.kind }.toSet()
		)
		assertTrue(drainResults.all { it == ReaderPortCommandResult.Accepted })
		assertTrue(confirmationsDuringCallback.isEmpty())
		assertTrue(releaseObservedCallbackReturn)
		assertEquals(
			callbackRows.map { it.physicalIdentity }.toSet(),
			releaseRows.map { it.physicalIdentity }.toSet()
		)
		assertTrue(
			releaseRows.all { it.state == ReaderLegacyResourceState.ReleaseRequested }
		)
		assertTrue(confirmationsDuringRelease.isEmpty())
		assertEquals(
			callbackRows.map { it.physicalIdentity }.toSet(),
			confirmations.toSet()
		)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun coalescedValueCleanupRemainsExactlyOwnedUntilReleaseReturns() {
		val domain = ReaderLegacyPhysicalDomain(55L, ReaderLegacyFreezeToken(56L))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		var rowsDuringRelease = emptyList<ReaderFrozenLegacyResource>()
		var cleanupIdentity: ReaderLegacyPhysicalIdentity? = null
		var cleanupDrainResult: ReaderPortCommandResult? = null
		var confirmationsDuringRelease = emptyList<ReaderLegacyPhysicalIdentity>()
		lateinit var ledger: ReaderPageRasterPublicationLedger<String>
		ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = { value ->
				if (value == "duplicate") {
					assertEquals(
						ReaderPortCommandResult.Accepted,
						ledger.freezeForTransitionActivation(domain)
					)
					rowsDuringRelease = ledger.snapshotFrozenOwnership()
					cleanupIdentity = rowsDuringRelease.singleOrNull { row ->
						row.kind == ReaderTransitionResourceKind.Raster &&
							row.state == ReaderLegacyResourceState.Running
					}?.physicalIdentity
					cleanupDrainResult = cleanupIdentity?.let { identity ->
						ledger.drainFrozenOwnership(identity, confirmations::add)
					}
					confirmationsDuringRelease = confirmations.toList()
				}
			}
		)
		ledger.begin("coalesced-cleanup", "producer") { }

		val registration = ledger.begin("coalesced-cleanup", "duplicate") { }

		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(registration)
		assertEquals(6, rowsDuringRelease.size)
		val exactCleanupIdentity = assertNotNull(cleanupIdentity)
		assertEquals(ReaderPortCommandResult.Accepted, cleanupDrainResult)
		assertTrue(confirmationsDuringRelease.isEmpty())
		assertEquals(listOf(exactCleanupIdentity), confirmations)
		val remainingRows = ledger.snapshotFrozenOwnership()
		assertEquals(5, remainingRows.size)
		remainingRows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(
			rowsDuringRelease.map { it.physicalIdentity }.toSet(),
			confirmations.toSet()
		)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun lastDrainKeepsRejectedCallbacksExactlyOwnedUntilTheirDispatchReturns() {
		val callbackStarted = CountDownLatch(1)
		val allowCallbackReturn = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val callbackCount = AtomicInteger()
		val confirmations = Collections.synchronizedList(
			mutableListOf<ReaderLegacyPhysicalIdentity>()
		)
		fun confirmed(): List<ReaderLegacyPhysicalIdentity> =
			synchronized(confirmations) { confirmations.toList() }
		val onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit = confirmations::add
		val finalDrainResult = AtomicReference<ReaderPortCommandResult?>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = {}
		)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("last-drain-callbacks", "owner") {
				callbackCount.incrementAndGet()
				callbackStarted.countDown()
				callbackReturned.set(allowCallbackReturn.await(5, TimeUnit.SECONDS))
			}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("last-drain-callbacks", "waiter") {
				callbackCount.incrementAndGet()
			}
		)
		assertEquals("owner", ledger.acquireForPersistence(started.request))
		val domain = ReaderLegacyPhysicalDomain(57L, ReaderLegacyFreezeToken(58L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		val callbackIdentities = rows.filter {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}.map { it.physicalIdentity }
		assertEquals(5, rows.size)
		assertEquals(2, callbackIdentities.size)
		rows.dropLast(1).forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, onConfirmed)
			)
		}
		val finalDrain = thread(name = "last-publication-drain") {
			finalDrainResult.set(
				ledger.drainFrozenOwnership(rows.last().physicalIdentity, onConfirmed)
			)
		}

		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			val duringCallback = ledger.snapshotFrozenOwnership().filter {
				it.kind == ReaderTransitionResourceKind.CallbackRegistration
			}
			assertEquals(
				callbackIdentities.toSet(),
				duringCallback.map { it.physicalIdentity }.toSet()
			)
			assertTrue(
				duringCallback.all {
					it.state == ReaderLegacyResourceState.ReleaseRequested
				}
			)
			assertTrue(confirmed().isEmpty())
			duringCallback.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Rejected(
						paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
					),
					ledger.drainFrozenOwnership(row.physicalIdentity, onConfirmed)
				)
			}
		} finally {
			allowCallbackReturn.countDown()
			finalDrain.join(5_000L)
		}

		assertFalse(finalDrain.isAlive)
		assertEquals(ReaderPortCommandResult.Accepted, finalDrainResult.get())
		assertTrue(callbackReturned.get())
		assertEquals(2, callbackCount.get())
		assertEquals(callbackIdentities.toSet(), confirmed().toSet())
		assertEquals(callbackIdentities.size, confirmed().size)
		assertFalse(ledger.complete(started.request, persisted = true))
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed().toSet())
		assertEquals(rows.size, confirmed().size)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun capacityListenerDrainWaitsForTheOnStackCallbackToReturn() {
		val domain = ReaderLegacyPhysicalDomain(59L, ReaderLegacyFreezeToken(60L))
		val callbackStarted = CountDownLatch(1)
		val allowCallbackReturn = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val freezeResult = AtomicReference<ReaderPortCommandResult?>()
		val completionResult = AtomicReference<Boolean?>()
		val capacityIdentity = AtomicReference<ReaderLegacyPhysicalIdentity?>()
		val confirmations = Collections.synchronizedList(
			mutableListOf<ReaderLegacyPhysicalIdentity>()
		)
		lateinit var ledger: ReaderPageRasterPublicationLedger<String>
		val listener: () -> Unit = {
			freezeResult.set(ledger.freezeForTransitionActivation(domain))
			callbackStarted.countDown()
			callbackReturned.set(allowCallbackReturn.await(5, TimeUnit.SECONDS))
		}
		ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 1,
			release = {}
		)
		ledger.setCapacityAvailableListener(listener)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("capacity-owner", "owner") {}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Rejected>(
			ledger.begin("capacity-overflow", "overflow") {}
		)
		assertEquals("owner", ledger.acquireForPersistence(started.request))
		val completion = thread(name = "capacity-listener-completion") {
			completionResult.set(ledger.complete(started.request, persisted = true))
		}

		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			assertEquals(ReaderPortCommandResult.Accepted, freezeResult.get())
			val row = ledger.snapshotFrozenOwnership().single()
			capacityIdentity.set(row.physicalIdentity)
			assertEquals(ReaderTransitionResourceKind.CallbackRegistration, row.kind)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
			assertTrue(confirmations.isEmpty())
			val dispatching = ledger.snapshotFrozenOwnership().single()
			assertEquals(row.physicalIdentity, dispatching.physicalIdentity)
			assertEquals(
				ReaderLegacyResourceState.ReleaseRequested,
				dispatching.state
			)
			assertEquals(
				ReaderPortCommandResult.Rejected(
					paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
				),
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		} finally {
			allowCallbackReturn.countDown()
			completion.join(5_000L)
		}

		assertFalse(completion.isAlive)
		assertEquals(true, completionResult.get())
		assertTrue(callbackReturned.get())
		assertEquals(capacityIdentity.get(), confirmations.single())
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun queuedDrainTotalizesThrowingConfirmationAcrossEveryRemovedToken() {
		val callbackCount = AtomicInteger()
		val releaseCount = AtomicInteger()
		val ledger = ReaderPageRasterPublicationLedger<String> {
			releaseCount.incrementAndGet()
		}
		ledger.begin("throwing-confirmation", "value") {
			callbackCount.incrementAndGet()
		}
		val domain = ReaderLegacyPhysicalDomain(61L, ReaderLegacyFreezeToken(62L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		val throwingIdentity = rows.first().physicalIdentity
		val confirmationCounts = linkedMapOf<ReaderLegacyPhysicalIdentity, Int>()
		val onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit = { identity ->
			confirmationCounts[identity] = confirmationCounts.getOrDefault(identity, 0) + 1
			if (identity == throwingIdentity) error("first-confirmation-failed")
		}
		rows.dropLast(1).forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, onConfirmed)
			)
		}

		val finalDrain = runCatching {
			ledger.drainFrozenOwnership(rows.last().physicalIdentity, onConfirmed)
		}

		assertTrue(finalDrain.isSuccess)
		assertEquals(ReaderPortCommandResult.Accepted, finalDrain.getOrThrow())
		assertEquals(1, callbackCount.get())
		assertEquals(1, releaseCount.get())
		assertEquals(
			rows.map { it.physicalIdentity }.toSet(),
			confirmationCounts.keys
		)
		assertTrue(confirmationCounts.values.all { count -> count == 1 })
		assertNotNull(ledger.dispatchFailure())
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Rejected(
					paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
				),
				ledger.drainFrozenOwnership(row.physicalIdentity) {}
			)
		}
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}
	@Test
	fun invalidationCallbackDrainCannotResurrectItsConfirmedIdentityOnWorkerCompletion() {
		val ledger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 1,
			release = {}
		)
		val domain = ReaderLegacyPhysicalDomain(63L, ReaderLegacyFreezeToken(64L))
		val confirmationCounts = linkedMapOf<ReaderLegacyPhysicalIdentity, Int>()
		val onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit = { identity ->
			confirmationCounts[identity] = confirmationCounts.getOrDefault(identity, 0) + 1
		}
		lateinit var callbackIdentity: ReaderLegacyPhysicalIdentity
		var callbackFreezeResult: ReaderPortCommandResult? = null
		var callbackDrainResult: ReaderPortCommandResult? = null
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("reentrant-invalidation-drain", "value") { persisted ->
				assertFalse(persisted)
				callbackFreezeResult = ledger.freezeForTransitionActivation(domain)
				callbackDrainResult = ledger.drainFrozenOwnership(
					callbackIdentity,
					onConfirmed
				)
			}
		)
		assertEquals("value", ledger.acquireForPersistence(started.request))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		callbackIdentity = rows.single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}.physicalIdentity
		val rasterRows = rows.filter { it.kind == ReaderTransitionResourceKind.Raster }
		rasterRows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, onConfirmed)
			)
		}

		ledger.invalidate()

		assertEquals(ReaderPortCommandResult.Accepted, callbackFreezeResult)
		assertEquals(ReaderPortCommandResult.Accepted, callbackDrainResult)
		assertEquals(1, confirmationCounts[callbackIdentity])
		assertTrue(ledger.snapshotFrozenOwnership().none {
			it.physicalIdentity == callbackIdentity
		})
		assertFalse(ledger.complete(started.request, persisted = true))
		assertEquals(
			rows.map { it.physicalIdentity }.toSet(),
			confirmationCounts.keys
		)
		assertTrue(confirmationCounts.values.all { count -> count == 1 })
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Rejected(
				paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
			),
			ledger.drainFrozenOwnership(callbackIdentity, onConfirmed)
		)
		assertEquals(1, confirmationCounts[callbackIdentity])
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun invalidationTotalizesThrowingDrainConfirmationAcrossRemovedRows() {
		val ledger = ReaderPageRasterPublicationLedger<String> { }
		ledger.begin("invalidation-confirmations", "value") { }
		val domain = ReaderLegacyPhysicalDomain(65L, ReaderLegacyFreezeToken(66L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		val rows = ledger.snapshotFrozenOwnership()
		val rasterRows = rows.filter { it.kind == ReaderTransitionResourceKind.Raster }
		val throwingIdentity = rasterRows.first().physicalIdentity
		val confirmationCounts = linkedMapOf<ReaderLegacyPhysicalIdentity, Int>()
		val onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit = { identity ->
			confirmationCounts[identity] = confirmationCounts.getOrDefault(identity, 0) + 1
			if (identity == throwingIdentity) error("invalidation-confirmation-failed")
		}
		rasterRows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, onConfirmed)
			)
		}

		val invalidation = runCatching(ledger::invalidate)

		assertTrue(invalidation.isSuccess)
		assertEquals(
			rasterRows.map { it.physicalIdentity }.toSet(),
			confirmationCounts.keys
		)
		assertTrue(confirmationCounts.values.all { count -> count == 1 })
		assertNotNull(ledger.dispatchFailure())
		val callbackRow = ledger.snapshotFrozenOwnership().single()
		assertEquals(ReaderTransitionResourceKind.CallbackRegistration, callbackRow.kind)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.drainFrozenOwnership(callbackRow.physicalIdentity, onConfirmed)
		)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun frozenRejectedBeginsRetainExactOwnersAndBlockAtCapacity() {
		val callbackEvents = mutableListOf<String>()
		val released = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 2,
			release = released::add
		)
		val domain = ReaderLegacyPhysicalDomain(67L, ReaderLegacyFreezeToken(68L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.freezeForTransitionActivation(domain)
		)
		fun rejectedBegin(label: String) = ledger.begin(label, label) { persisted ->
			callbackEvents += "$label-$persisted"
		}

		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.ActivationFrozen
			),
			rejectedBegin("first")
		)
		val firstRows = ledger.snapshotFrozenOwnership()
		assertEquals(2, firstRows.size)
		assertTrue(firstRows.all { row -> row.state == ReaderLegacyResourceState.Released })
		val firstIdentities = firstRows.map { row -> row.physicalIdentity }.toSet()

		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.ActivationFrozen
			),
			rejectedBegin("second")
		)
		val fullRows = ledger.snapshotFrozenOwnership()
		val fullIdentities = fullRows.map { row -> row.physicalIdentity }.toSet()
		assertEquals(ledger.callbackLimit * 2, fullRows.size)
		assertEquals(fullRows.size, fullIdentities.size)
		assertTrue(fullIdentities.containsAll(firstIdentities))
		val secondIdentities = fullIdentities - firstIdentities
		assertEquals(2, secondIdentities.size)
		assertEquals(listOf("first-false", "second-false"), callbackEvents)
		assertEquals(listOf("first", "second"), released)

		assertFailsWith<IllegalStateException> {
			rejectedBegin("overflow")
		}
		assertEquals(fullIdentities, ledger.snapshotFrozenOwnership()
			.map { row -> row.physicalIdentity }.toSet())
		assertEquals(listOf("first-false", "second-false"), callbackEvents)
		assertEquals(listOf("first", "second"), released)

		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		firstRows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(firstIdentities, confirmations.toSet())
		assertEquals(
			secondIdentities,
			ledger.snapshotFrozenOwnership().map { row -> row.physicalIdentity }.toSet()
		)

		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.ActivationFrozen
			),
			rejectedBegin("after-drain")
		)
		val afterDrainRows = ledger.snapshotFrozenOwnership()
		val afterDrainIdentities = afterDrainRows.map { row -> row.physicalIdentity }.toSet()
		assertEquals(ledger.callbackLimit * 2, afterDrainRows.size)
		assertTrue(afterDrainIdentities.containsAll(secondIdentities))
		val freshIdentities = afterDrainIdentities - secondIdentities
		assertEquals(2, freshIdentities.size)
		assertTrue(freshIdentities.intersect(fullIdentities).isEmpty())
		assertTrue(afterDrainRows.all { row -> row.state == ReaderLegacyResourceState.Released })

		afterDrainRows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ledger.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		val allIdentities = firstIdentities + secondIdentities + freshIdentities
		assertEquals(allIdentities, confirmations.toSet())
		assertEquals(confirmations.size, confirmations.toSet().size)
		assertEquals(
			listOf("first-false", "second-false", "after-drain-false"),
			callbackEvents
		)
		assertEquals(listOf("first", "second", "after-drain"), released)
		assertTrue(ledger.snapshotFrozenOwnership().isEmpty())
		allIdentities.forEach { identity ->
			assertEquals(
				ReaderPortCommandResult.Rejected(
					paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
				),
				ledger.drainFrozenOwnership(identity) {}
			)
		}
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ledger.restoreAfterTransitionActivation(domain)
		)
	}
}
