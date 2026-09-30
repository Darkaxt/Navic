package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderForegroundWebViewOwnershipTest {
	@Test
	fun frozenOwnershipInventoriesPassiveAndRestorationOwnership() {
		val passiveOwnership = ReaderForegroundWebViewOwnership()
		val passive = assertNotNull(
			passiveOwnership.tryAcquirePassive(7L) { error("not preempted") }
		)
		val passiveDomain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			passiveOwnership.freezeForTransitionActivation(passiveDomain)
		)
		val passiveRow = passiveOwnership.snapshotFrozenOwnership().single()
		val passiveConfirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			passiveOwnership.drainFrozenOwnership(
				passiveRow.physicalIdentity,
				passiveConfirmed::add
			)
		)
		// Before: draining a reference pretended to stop/restart the passive producer.
		// After: only its natural terminal settles it; restoration never resurrects it.
		assertTrue(passiveConfirmed.isEmpty())
		assertTrue(passiveOwnership.releasePassive(passive))
		assertTrue(passiveConfirmed.single() == passiveRow.physicalIdentity)
		passiveOwnership.snapshotFrozenOwnership().forEach {
			passiveOwnership.drainFrozenOwnership(it.physicalIdentity) {}
		}
		assertEquals(
			ReaderPortCommandResult.Accepted,
			passiveOwnership.restoreAfterTransitionActivation(passiveDomain)
		)
		assertFalse(passiveOwnership.isCurrent(passive))

		var finishRestoration: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val restoringOwnership = ReaderForegroundWebViewOwnership()
		assertNotNull(
			restoringOwnership.tryAcquirePassive(8L) { finishRestoration = it }
		)
		restoringOwnership.acquireLive(24L)
		val restoringDomain = ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(29L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			restoringOwnership.freezeForTransitionActivation(restoringDomain)
		)
		val restorationRow = restoringOwnership.snapshotFrozenOwnership().single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}
		val restorationConfirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			restoringOwnership.drainFrozenOwnership(
				restorationRow.physicalIdentity,
				restorationConfirmed::add
			)
		)
		// Before: clearing restoration references confirmed a still-running callback.
		// After: the real terminal and external return precede exact confirmation.
		assertTrue(restorationConfirmed.isEmpty())
		assertTrue(restoringOwnership.restoreAfterTransitionActivation(restoringDomain) is ReaderPortCommandResult.Rejected)
		checkNotNull(finishRestoration)(ReaderPageRasterCancellationRestoration.Restored)
		assertTrue(restorationConfirmed.single() == restorationRow.physicalIdentity)
		restoringOwnership.snapshotFrozenOwnership().forEach {
			restoringOwnership.drainFrozenOwnership(it.physicalIdentity) {}
		}
		assertEquals(
			ReaderPortCommandResult.Accepted,
			restoringOwnership.restoreAfterTransitionActivation(restoringDomain)
		)
		assertEquals(0, restoringOwnership.snapshot().restorationCallbacks)
	}

	@Test
	fun frozenOwnershipInventoriesAndDrainsExactLiveClaimsAndReadinessCallbacks() {
		val ownership = ReaderForegroundWebViewOwnership()
		val first = ownership.acquireLive(14L)
		val exclusive = ownership.acquireExclusiveLive(15L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(exclusive, readiness::add)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(19L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.freezeForTransitionActivation(domain)
		)
		val rows = ownership.snapshotFrozenOwnership()
		assertEquals(3, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertTrue(rows.all {
			it.physicalIdentity.source ==
				ReaderLegacyInventorySource.ForegroundWebViewOwnership
		})
		assertFailsWith<IllegalStateException> { ownership.acquireLive(16L) }
		assertNull(ownership.beginLiveMutation(first))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.sortedBy { it.kind != paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration }
			.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					ownership.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
		assertTrue(rows.map { it.physicalIdentity }.toSet() == confirmations.toSet())
		// Before: transition drain destructively delivered Invalidated.
		// After: park the recipient and deliver its original Ready after the predecessor releases.
		assertTrue(readiness.isEmpty())
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.restoreAfterTransitionActivation(domain)
		)
		assertEquals(2, ownership.snapshot().liveClaims)
		assertTrue(readiness.isEmpty())
		assertTrue(ownership.releaseLive(first))
		assertEquals(listOf<ReaderForegroundWebViewLiveReadiness>(ReaderForegroundWebViewLiveReadiness.Ready), readiness)
		assertTrue(ownership.releaseLive(exclusive))
	}

	@Test
	fun freezeAloneKeepsPhysicalCurrencyButDeniesNewMutationAndAdmission() {
		val ownership = ReaderForegroundWebViewOwnership()
		val claim = ownership.acquireExclusiveLive(14L)
		val generation = assertNotNull(ownership.beginLiveMutation(claim))
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		assertTrue(ownership.isCurrent(claim, generation))
		assertNull(ownership.beginLiveMutation(claim))
		assertNull(ownership.tryAcquirePassive(7L) { error("no new admission") })
		assertFailsWith<IllegalStateException> { ownership.acquireExclusiveLive(15L) }
		assertEquals(1, ownership.snapshotFrozenOwnership().size)
	}

	@Test
	fun frozenRestorationDrainWaitsForTheActualTerminalAndPreservesRecipient() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val live = ownership.acquireLive(14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		ownership.freezeForTransitionActivation(domain)
		val row = ownership.snapshotFrozenOwnership().first {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}
		var confirmed = 0
		assertEquals(ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(row.physicalIdentity) { confirmed += 1 })
		assertEquals(0, confirmed, "a cancellation request is not terminal settlement")
		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		checkNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(1, confirmed)
		assertTrue(readiness.isEmpty(), "no readiness publication during freeze")
		ownership.snapshotFrozenOwnership().forEach {
			assertEquals(ReaderPortCommandResult.Accepted,
				ownership.drainFrozenOwnership(it.physicalIdentity) {})
		}
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		assertEquals(listOf<ReaderForegroundWebViewLiveReadiness>(ReaderForegroundWebViewLiveReadiness.Ready), readiness)
	}

	@Test
	fun frozenNaturalReleaseRemainsInventoriedUntilItsExactDrain() {
		val ownership = ReaderForegroundWebViewOwnership()
		val claim = ownership.acquireLive(14L)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		ownership.freezeForTransitionActivation(domain)
		val row = ownership.snapshotFrozenOwnership().single()
		assertTrue(ownership.releaseLive(claim))
		assertTrue(ownership.snapshotFrozenOwnership().any { it.physicalIdentity == row.physicalIdentity })
		var confirmed = 0
		val wrongSource = row.physicalIdentity.copy(source = ReaderLegacyInventorySource.RasterPreparation)
		val wrongDomain = row.physicalIdentity.copy(domain = domain.copy(readerSessionGeneration = 19L))
		assertTrue(ownership.drainFrozenOwnership(wrongSource) { confirmed += 1 } is ReaderPortCommandResult.Rejected)
		assertTrue(ownership.drainFrozenOwnership(wrongDomain) { confirmed += 1 } is ReaderPortCommandResult.Rejected)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(row.physicalIdentity) { confirmed += 1 })
		assertTrue(ownership.drainFrozenOwnership(row.physicalIdentity) { confirmed += 1 } is ReaderPortCommandResult.Rejected)
		assertEquals(1, confirmed)
	}

	@Test
	fun reentrantFreezeInsideReadinessKeepsRunningCallbackAndMutationTail() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val first = ownership.acquireLive(14L)
		val second = ownership.acquireLive(15L)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		var laterReady = 0
		var confirmed = 0
		ownership.whenLiveReady(first) {
			assertNotNull(ownership.beginLiveMutation(first))
			ownership.freezeForTransitionActivation(domain)
			val rows = ownership.snapshotFrozenOwnership()
			assertTrue(rows.any { it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration })
			val claimRow = rows.first { it.kind == paige.navic.reader.ReaderTransitionResourceKind.FrameHandoff }
			ownership.drainFrozenOwnership(claimRow.physicalIdentity) { confirmed += 1 }
			assertEquals(0, confirmed, "running dispatch and mutation tails have not returned")
		}
		ownership.whenLiveReady(second) { laterReady += 1 }
		checkNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(0, laterReady, "a reserved later callback must not start after reentrant freeze")
		assertNull(ownership.beginLiveMutation(second))
		assertTrue(ownership.releaseLive(first))
	}

	@Test
	fun synchronousCancelAndRestoreReturnTailCannotDisappearDuringFreeze() {
		lateinit var ownership: ReaderForegroundWebViewOwnership
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		var confirmations = 0
		ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish ->
			finish(ReaderPageRasterCancellationRestoration.Restored)
			ownership.freezeForTransitionActivation(domain)
			val rows = ownership.snapshotFrozenOwnership()
			assertTrue(rows.any { it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration },
				"the external cancellation closure has not returned")
			val tail = rows.first { it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration }
			ownership.drainFrozenOwnership(tail.physicalIdentity) { confirmations += 1 }
			assertEquals(0, confirmations)
		})
		ownership.acquireLive(14L)
		assertTrue(confirmations > 0)
	}

	@Test
	fun frozenRetiredFailureIsAnExactUndeliveredTerminalNotAnEmptySource() {
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish ->
			finish(ReaderPageRasterCancellationRestoration.TimedOut)
		})
		val live = ownership.acquireLive(14L)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		ownership.freezeForTransitionActivation(domain)
		val rows = ownership.snapshotFrozenOwnership()
		assertTrue(rows.isNotEmpty(), "returned claim still owes terminal delivery")
		rows.forEach { assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(it.physicalIdentity) {}) }
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)
		assertEquals(listOf<ReaderForegroundWebViewLiveReadiness>(ReaderForegroundWebViewLiveReadiness.Failed(
			ReaderPageRasterCancellationRestoration.TimedOut)), readiness)
	}

	@Test
	fun frozenPermanentCloseKeepsPendingRestorationDrainableWithoutReplay() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		ownership.acquireLive(14L)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		ownership.freezeForTransitionActivation(domain)
		val row = ownership.snapshotFrozenOwnership().first {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}
		ownership.close()
		var confirmations = 0
		assertEquals(ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(row.physicalIdentity) { confirmations += 1 })
		assertEquals(0, confirmations)
		checkNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(1, confirmations)
		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		assertFalse(ownership.canAcquirePassive())
	}

	@Test
	fun throwingReadinessCannotLoseLaterRecipientOrCompleteRunningTailEarly() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val first = ownership.acquireLive(14L)
		val second = ownership.acquireLive(15L)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		var secondDelivered = 0
		ownership.whenLiveReady(first) {
			ownership.freezeForTransitionActivation(domain)
			throw IllegalStateException("controlled callback failure")
		}
		ownership.whenLiveReady(second) { secondDelivered += 1 }
		assertFailsWith<IllegalStateException> { checkNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored) }
		assertTrue(ownership.snapshotFrozenOwnership().any {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}, "the later callback is still an exact obligation")
		assertEquals(0, secondDelivered)
		ownership.snapshotFrozenOwnership().forEach {
			ownership.drainFrozenOwnership(it.physicalIdentity) {}
		}
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty(), "all actual return tails settled")
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		assertEquals(1, secondDelivered, "the original recipient survives the throwing predecessor")
		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		assertEquals(1, secondDelivered)
	}

	@Test
	fun liveClaimWaitsForPassiveRestorationBeforeMutation() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		val passive = checkNotNull(
			ownership.tryAcquirePassive(sessionId = 7L) { onRestored ->
				finishRestoration = onRestored
			}
		)
		val live = ownership.acquireLive(gestureId = 14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)

		assertFalse(ownership.isCurrent(passive))
		assertNull(ownership.beginLiveMutation(live))
		assertTrue(readiness.isEmpty())
		assertEquals(
			ReaderForegroundWebViewOwnershipSnapshot(
				passiveOwners = 0,
				liveClaims = 1,
				restorationCallbacks = 1,
				closed = false
			),
			ownership.snapshot()
		)

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.Restored
		)

		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Ready
			),
			readiness
		)
		assertNotNull(ownership.beginLiveMutation(live))
	}

	@Test
	fun secondLiveClaimPreventsPassiveGapWhenFirstCompletes() {
		val ownership = ReaderForegroundWebViewOwnership()
		val first = ownership.acquireLive(gestureId = 14L)
		val second = ownership.acquireLive(gestureId = 15L)

		assertTrue(ownership.releaseLive(first))
		assertNull(
			ownership.tryAcquirePassive(8L) {
				error("must not preempt")
			}
		)
		assertNotNull(ownership.beginLiveMutation(second))
		assertTrue(ownership.releaseLive(second))
		assertNotNull(
			ownership.tryAcquirePassive(8L) {
				error("not preempted")
			}
		)
	}

	@Test
	fun restorationTimeoutFailsEveryWaitingLiveClaimClosed() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val live = ownership.acquireLive(14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.TimedOut
		)

		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Failed(
					ReaderPageRasterCancellationRestoration.TimedOut
				)
			),
			readiness
		)
		assertNull(ownership.beginLiveMutation(live))
		assertFalse(ownership.releaseLive(live))
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun detachedRestorationFailsWaitingClaimsClosed() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val first = ownership.acquireLive(14L)
		val second = ownership.acquireLive(15L)
		val firstReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		val secondReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(first, firstReadiness::add)
		ownership.whenLiveReady(second, secondReadiness::add)

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.Detached
		)

		val expected: List<ReaderForegroundWebViewLiveReadiness> = listOf(
			ReaderForegroundWebViewLiveReadiness.Failed(
				ReaderPageRasterCancellationRestoration.Detached
			)
		)
		assertEquals(expected, firstReadiness)
		assertEquals(expected, secondReadiness)
		assertNull(ownership.beginLiveMutation(first))
		assertNull(ownership.beginLiveMutation(second))
		assertEquals(0, ownership.snapshot().liveClaims)
		assertNotNull(
			ownership.tryAcquirePassive(8L) {
				error("not preempted")
			}
		)
	}

	@Test
	fun failedRestorationStagesEveryClaimBeforeInvokingCallbacks() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val first = ownership.acquireLive(14L)
		val second = ownership.acquireLive(15L)
		val firstReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		val secondReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(first) { readiness ->
			firstReadiness += readiness
			ownership.whenLiveReady(second, secondReadiness::add)
		}

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.TimedOut
		)

		val expected = listOf<ReaderForegroundWebViewLiveReadiness>(
			ReaderForegroundWebViewLiveReadiness.Failed(
				ReaderPageRasterCancellationRestoration.TimedOut
			)
		)
		assertEquals(expected, firstReadiness)
		assertEquals(expected, secondReadiness)
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun synchronousRestorationCallbackMakesTheClaimReady() {
		var cancellationCalls = 0
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(7L) { finishRestoration ->
				cancellationCalls += 1
				finishRestoration(ReaderPageRasterCancellationRestoration.Restored)
			}
		)

		val live = ownership.acquireLive(14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)

		assertEquals(1, cancellationCalls)
		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Ready
			),
			readiness
		)
		assertNotNull(ownership.beginLiveMutation(live))
		assertEquals(0, ownership.snapshot().restorationCallbacks)
	}

	@Test
	fun synchronousFailedRestorationRetainsItsTerminalForTheReturnedClaim() {
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(7L) { finishRestoration ->
				finishRestoration(ReaderPageRasterCancellationRestoration.TimedOut)
			}
		)

		val live = ownership.acquireLive(14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)

		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Failed(
					ReaderPageRasterCancellationRestoration.TimedOut
				)
			),
			readiness
		)
		assertNull(ownership.beginLiveMutation(live))
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun currentPassiveReleaseRearmsLiveOwnershipBeforePassiveReadmission() {
		var passiveMutationReleaseCalls = 0
		lateinit var live: ReaderForegroundWebViewLiveClaim
		lateinit var ownership: ReaderForegroundWebViewOwnership
		ownership = ReaderForegroundWebViewOwnership(
			onPassiveMutationReleased = {
				passiveMutationReleaseCalls += 1
				assertEquals(0, ownership.snapshot().passiveOwners)
				live = ownership.acquireExclusiveLive(requestId = 14L)
			}
		)
		val passive = checkNotNull(
			ownership.tryAcquirePassive(7L) {
				error("not preempted")
			}
		)

		assertTrue(ownership.releasePassive(passive))

		assertEquals(1, passiveMutationReleaseCalls)
		assertEquals(1, ownership.snapshot().liveClaims)
		assertNull(
			ownership.tryAcquirePassive(8L) {
				error("must not preempt rearmed live ownership")
			}
		)
		assertTrue(ownership.releaseLive(live))
	}

	@Test
	fun stalePassiveReleaseCannotReleaseTheCurrentLease() {
		var passiveMutationReleaseCalls = 0
		val ownership = ReaderForegroundWebViewOwnership(
			onPassiveMutationReleased = {
				passiveMutationReleaseCalls += 1
			}
		)
		val stale = checkNotNull(
			ownership.tryAcquirePassive(7L) {
				error("not preempted")
			}
		)
		assertTrue(ownership.releasePassive(stale))
		assertEquals(1, passiveMutationReleaseCalls)
		val current = checkNotNull(
			ownership.tryAcquirePassive(8L) {
				error("not preempted")
			}
		)

		assertFalse(ownership.releasePassive(stale))
		assertEquals(1, passiveMutationReleaseCalls)
		assertTrue(ownership.isCurrent(current))
		assertEquals(1, ownership.snapshot().passiveOwners)
		assertTrue(ownership.releasePassive(current))
		assertEquals(2, passiveMutationReleaseCalls)
	}

	@Test
	fun duplicateRestorationCallbackCannotChangeTheTerminalResult() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val live = ownership.acquireLive(14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.Restored
		)
		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.TimedOut
		)

		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Ready
			),
			readiness
		)
		assertNotNull(ownership.beginLiveMutation(live))
	}

	@Test
	fun mutationGenerationAdvancesMonotonicallyAndFencesStaleMutations() {
		val ownership = ReaderForegroundWebViewOwnership()
		val passive = checkNotNull(
			ownership.tryAcquirePassive(7L) {
				error("not preempted")
			}
		)
		assertEquals(1L, passive.mutationGeneration.value)
		assertTrue(ownership.releasePassive(passive))
		val live = ownership.acquireLive(14L)

		val first = assertNotNull(ownership.beginLiveMutation(live))
		val second = assertNotNull(ownership.beginLiveMutation(live))

		assertEquals(2L, first.value)
		assertEquals(3L, second.value)
		assertFalse(ownership.isCurrent(live, first))
		assertTrue(ownership.isCurrent(live, second))
	}

	@Test
	fun safeIntegerExhaustionNeverPublishesAnOutOfRangeGeneration() {
		val ownership = ReaderForegroundWebViewOwnership()
		val live = ownership.acquireLive(14L)
		ownership.setMutationGenerationForTest(
			ReaderPageTurnPresentationMaximumSafeInteger - 1L
		)

		val maximum = assertNotNull(ownership.beginLiveMutation(live))
		assertEquals(ReaderPageTurnPresentationMaximumSafeInteger, maximum.value)
		assertNull(ownership.beginLiveMutation(live))
		assertTrue(ownership.isCurrent(live, maximum))
		assertTrue(ownership.releaseLive(live))
		assertNull(
			ownership.tryAcquirePassive(7L) {
				error("must not acquire without a safe generation")
			}
		)
	}

	@Test
	fun passiveLeaseIdExhaustionNeverPublishesAnOutOfRangeIdentifier() {
		val ownership = ReaderForegroundWebViewOwnership()
		ownership.setLongFieldForTest(
			name = "nextLeaseId",
			value = ReaderPageTurnPresentationMaximumSafeInteger - 1L
		)
		val maximum = checkNotNull(
			ownership.tryAcquirePassive(7L) {
				error("not preempted")
			}
		)

		assertEquals(ReaderPageTurnPresentationMaximumSafeInteger, maximum.leaseId)
		assertTrue(ownership.releasePassive(maximum))
		assertFailsWith<IllegalStateException> {
			ownership.tryAcquirePassive(8L) {
				error("must not publish an unsafe lease ID")
			}
		}
		assertEquals(0, ownership.snapshot().passiveOwners)
	}

	@Test
	fun liveClaimIdExhaustionNeverPublishesAnOutOfRangeIdentifier() {
		val ownership = ReaderForegroundWebViewOwnership()
		ownership.setLongFieldForTest(
			name = "nextClaimId",
			value = ReaderPageTurnPresentationMaximumSafeInteger - 1L
		)
		val maximum = ownership.acquireLive(14L)

		assertEquals(ReaderPageTurnPresentationMaximumSafeInteger, maximum.claimId)
		assertTrue(ownership.releaseLive(maximum))
		assertFailsWith<IllegalStateException> {
			ownership.acquireLive(15L)
		}
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun releasingLastClaimWhileRestoringWaitsForRestorationBeforePassiveAdmission() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		var passiveAvailableCalls = 0
		val ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableCalls += 1
		}
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val live = ownership.acquireLive(14L)
		val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(live, readiness::add)

		assertTrue(ownership.releaseLive(live))
		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Invalidated
			),
			readiness
		)
		assertEquals(0, ownership.snapshot().liveClaims)
		assertEquals(1, ownership.snapshot().restorationCallbacks)
		assertEquals(0, passiveAvailableCalls)
		assertNull(
			ownership.tryAcquirePassive(8L) {
				error("restoration is still pending")
			}
		)

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.Restored
		)

		assertEquals(1, passiveAvailableCalls)
		assertEquals(0, ownership.snapshot().restorationCallbacks)
		assertNotNull(
			ownership.tryAcquirePassive(8L) {
				error("not preempted")
			}
		)
	}

	@Test
	fun synchronousRestorationFromInvalidationCallbackNotifiesExactlyOnce() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		var passiveAvailableCalls = 0
		val ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableCalls += 1
		}
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val live = ownership.acquireLive(14L)
		ownership.whenLiveReady(live) {
			checkNotNull(finishRestoration)(
				ReaderPageRasterCancellationRestoration.Restored
			)
		}

		assertTrue(ownership.releaseLive(live))

		assertEquals(1, passiveAvailableCalls)
		assertTrue(ownership.canAcquirePassive())
	}

	@Test
	fun reentrantClosePreventsAStalePassiveAvailableNotification() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		var passiveAvailableCalls = 0
		lateinit var ownership: ReaderForegroundWebViewOwnership
		ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableCalls += 1
		}
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val live = ownership.acquireLive(14L)
		ownership.whenLiveReady(live) {
			ownership.close()
			checkNotNull(finishRestoration)(
				ReaderPageRasterCancellationRestoration.Restored
			)
		}

		assertTrue(ownership.releaseLive(live))

		assertEquals(0, passiveAvailableCalls)
		assertTrue(ownership.snapshot().closed)
	}

	@Test
	fun reentrantPassiveAcquisitionPreventsADuplicateAvailabilityNotification() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		var passiveAvailableCalls = 0
		var acquiredPassive: ReaderForegroundWebViewPassiveLease? = null
		lateinit var ownership: ReaderForegroundWebViewOwnership
		ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableCalls += 1
			if (passiveAvailableCalls == 1) {
				acquiredPassive = ownership.tryAcquirePassive(8L) {
					error("not preempted")
				}
			}
		}
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val live = ownership.acquireLive(14L)
		ownership.whenLiveReady(live) {
			checkNotNull(finishRestoration)(
				ReaderPageRasterCancellationRestoration.Restored
			)
		}

		assertTrue(ownership.releaseLive(live))

		assertEquals(1, passiveAvailableCalls)
		assertTrue(ownership.isCurrent(checkNotNull(acquiredPassive)))
	}

	@Test
	fun lastLiveReleasePublishesPassiveAvailabilityExactlyOnce() {
		var passiveAvailableCalls = 0
		val ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableCalls += 1
		}
		val first = ownership.acquireLive(14L)
		val second = ownership.acquireLive(15L)

		assertTrue(ownership.releaseLive(first))
		assertEquals(0, passiveAvailableCalls)
		assertTrue(ownership.releaseLive(second))
		assertEquals(1, passiveAvailableCalls)
		assertFalse(ownership.releaseLive(second))
		assertEquals(1, passiveAvailableCalls)
	}

	@Test
	fun closeDrainsOwnersAndInvalidatesCallbacksWithoutSchedulingPassiveWork() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		var passiveAvailableCalls = 0
		val ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableCalls += 1
		}
		checkNotNull(
			ownership.tryAcquirePassive(7L) {
				finishRestoration = it
			}
		)
		val first = ownership.acquireLive(14L)
		val second = ownership.acquireLive(15L)
		val firstReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		val secondReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(first, firstReadiness::add)
		ownership.whenLiveReady(second, secondReadiness::add)

		ownership.close()

		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Invalidated
			),
			firstReadiness
		)
		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Invalidated
			),
			secondReadiness
		)
		assertEquals(
			ReaderForegroundWebViewOwnershipSnapshot(
				passiveOwners = 0,
				liveClaims = 0,
				restorationCallbacks = 0,
				closed = true
			),
			ownership.snapshot()
		)
		assertFalse(ownership.canAcquirePassive())
		assertEquals(0, passiveAvailableCalls)

		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.Restored
		)

		assertEquals(0, passiveAvailableCalls)
		assertEquals(
			ReaderForegroundWebViewOwnershipSnapshot(0, 0, 0, true),
			ownership.snapshot()
		)
	}

	@Test
	fun cancellationJoinUsesFailClosedPrecedenceAfterEveryCallbackCompletes() {
		val completions = mutableListOf<ReaderPageRasterCancellationRestoration>()
		val join = ReaderPageRasterCancellationJoin(
			expectedCallbackCount = 3,
			onComplete = completions::add
		)
		val restored = join.callback()
		val detached = join.callback()
		val timedOut = join.callback()

		restored(ReaderPageRasterCancellationRestoration.Restored)
		detached(ReaderPageRasterCancellationRestoration.Detached)
		assertTrue(completions.isEmpty())
		timedOut(ReaderPageRasterCancellationRestoration.TimedOut)

		assertEquals(
			listOf(ReaderPageRasterCancellationRestoration.TimedOut),
			completions
		)
	}

	@Test
	fun cancellationJoinFencesDuplicateCallbacksAndCompletesExactlyOnce() {
		assertFailsWith<IllegalArgumentException> {
			ReaderPageRasterCancellationJoin(0) { error("must not complete") }
		}
		val completions = mutableListOf<ReaderPageRasterCancellationRestoration>()
		val join = ReaderPageRasterCancellationJoin(
			expectedCallbackCount = 2,
			onComplete = completions::add
		)
		val first = join.callback()
		val second = join.callback()

		first(ReaderPageRasterCancellationRestoration.Restored)
		first(ReaderPageRasterCancellationRestoration.TimedOut)
		assertTrue(completions.isEmpty())
		second(ReaderPageRasterCancellationRestoration.Detached)
		second(ReaderPageRasterCancellationRestoration.TimedOut)

		assertEquals(
			listOf(ReaderPageRasterCancellationRestoration.Detached),
			completions
		)
		assertFailsWith<IllegalStateException> { join.callback() }
	}

	@Test
	fun freezeAloneFencesPreviouslyGrantedPassiveMutationAuthority() {
		val ownership = ReaderForegroundWebViewOwnership()
		val lease = assertNotNull(ownership.tryAcquirePassive(7L) {})
		assertTrue(ownership.isCurrent(lease))
		assertTrue(ownership.isMutationGenerationCurrent(lease.mutationGeneration.value))

		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(testDomain()))

		// Before: physical currency was mistaken for admission authority.
		// After: retain currency for accepted cleanup, independently deny new work.
		assertTrue(ownership.isCurrent(lease))
		assertTrue(ownership.isMutationGenerationCurrent(lease.mutationGeneration.value))
		assertNull(ownership.tryAcquirePassive(8L) { error("no frozen producer start") })
		assertEquals(1, ownership.snapshotFrozenOwnership().size)
	}

	@Test
	fun freezeAloneFencesPreviouslyGrantedExclusiveLiveMutationAuthority() {
		val ownership = ReaderForegroundWebViewOwnership()
		val claim = ownership.acquireExclusiveLive(11L)
		val generation = assertNotNull(ownership.beginLiveMutation(claim))

		ownership.freezeForTransitionActivation(testDomain())

		// Before: freeze erased accepted physical currency.
		// After: currency survives; mutation reservation and actual caller starts are separately fenced.
		assertTrue(ownership.isCurrent(claim, generation))
		assertTrue(ownership.isMutationGenerationCurrent(generation.value))
		assertNull(ownership.beginLiveMutation(claim))
		// Exclusive ownership and current mutation are aliases of this claim, not extra owners.
		assertEquals(1, ownership.snapshotFrozenOwnership().size)
	}

	@Test
	fun frozenConnectedEmptyDeniesNewWorkWithoutInventingOwners() {
		val ownership = ReaderForegroundWebViewOwnership()
		val domain = testDomain()
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty())
		assertFalse(ownership.canAcquirePassive())
		assertNull(ownership.tryAcquirePassive(7L) { error("unexpected cancellation") })
		assertFailsWith<IllegalStateException> { ownership.acquireLive(11L) }
		assertFailsWith<IllegalStateException> { ownership.acquireExclusiveLive(12L) }
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		assertTrue(ownership.canAcquirePassive())
	}

	@Test
	fun passiveDrainRequiresNaturalPhysicalSettlementNotReferenceRemoval() {
		var cancellationCalls = 0
		val ownership = ReaderForegroundWebViewOwnership()
		val lease = assertNotNull(ownership.tryAcquirePassive(7L) {
			cancellationCalls += 1
		})
		ownership.freezeForTransitionActivation(testDomain())
		val identity = ownership.snapshotFrozenOwnership().single().physicalIdentity
		var confirmations = 0
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) {
			assertTrue(it == identity)
			confirmations += 1
		})
		// Before: drain mandated cancellation without any producer-restart capability.
		// After: wait for natural settlement, not a fabricated restart of the canceled lease.
		assertEquals(0, confirmations)
		assertEquals(0, cancellationCalls)
		assertTrue(ownership.releasePassive(lease))
		assertEquals(1, confirmations)
		assertFalse(ownership.releasePassive(lease))
	}

	@Test
	fun reservedLiveGenerationWithoutAPrimitiveCanParkAndDrain() {
		val ownership = ReaderForegroundWebViewOwnership()
		val claim = ownership.acquireLive(11L)
		assertNotNull(ownership.beginLiveMutation(claim))
		ownership.freezeForTransitionActivation(testDomain())
		val identity = ownership.snapshotFrozenOwnership().single().physicalIdentity
		var confirmations = 0
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) {
			assertTrue(it == identity)
			confirmations += 1
		})

		// Before: a generation reservation was classified as a started external mutation.
		// After: unstarted demand parks; actual-started caller tails have separate regressions.
		assertEquals(1, confirmations)
		assertTrue(ownership.snapshotFrozenOwnership().none { it.physicalIdentity == identity })
		assertTrue(ownership.releaseLive(claim))
		assertEquals(1, confirmations)
	}

	@Test
	fun acceptedRestorationDrainWaitsForActualTerminalCallback() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		ownership.acquireLive(11L)
		ownership.freezeForTransitionActivation(testDomain())
		val identity = ownership.snapshotFrozenOwnership().single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}.physicalIdentity
		var confirmations = 0
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) {
			assertTrue(it == identity)
			confirmations += 1
		})

		assertEquals(0, confirmations)
		assertTrue(ownership.snapshotFrozenOwnership().any { it.physicalIdentity == identity })
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(1, confirmations)
	}

	@Test
	fun incompletePhysicalSettlementCannotReopenDuringPartialRestoration() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val claim = ownership.acquireLive(11L)
		val domain = testDomain()
		ownership.freezeForTransitionActivation(domain)
		val identity = ownership.snapshotFrozenOwnership().single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}.physicalIdentity
		ownership.drainFrozenOwnership(identity) {}

		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		assertNull(ownership.beginLiveMutation(claim))
		assertFalse(ownership.canAcquirePassive())
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
	}

	@Test
	fun naturalPassiveCompletionRemainsInventoriedUntilExactDrain() {
		val ownership = ReaderForegroundWebViewOwnership()
		val lease = assertNotNull(ownership.tryAcquirePassive(7L) {})
		ownership.freezeForTransitionActivation(testDomain())
		val identity = ownership.snapshotFrozenOwnership().single().physicalIdentity

		assertTrue(ownership.releasePassive(lease))

		val row = ownership.snapshotFrozenOwnership().singleOrNull { it.physicalIdentity == identity }
		assertNotNull(row)
		assertEquals(ReaderLegacyResourceState.Released, row.state)
		var confirmations = 0
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) { confirmations += 1 })
		assertEquals(1, confirmations)
		assertTrue(ownership.drainFrozenOwnership(identity) {} is ReaderPortCommandResult.Rejected)
	}

	@Test
	fun immediateReadinessDispatchIsInventoriedThroughItsExternalReturn() {
		val ownership = ReaderForegroundWebViewOwnership()
		val claim = ownership.acquireLive(11L)
		var confirmations = 0
		var callbackReturned = false
		var callbackIdentity: ReaderLegacyPhysicalIdentity? = null

		ownership.whenLiveReady(claim) {
			ownership.freezeForTransitionActivation(testDomain())
			val row = ownership.snapshotFrozenOwnership().singleOrNull {
				it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
			}
			assertNotNull(row)
			callbackIdentity = row.physicalIdentity
			assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(row.physicalIdentity) {
				assertTrue(callbackReturned)
				confirmations += 1
			})
			assertEquals(0, confirmations)
			callbackReturned = true
		}

		assertNotNull(callbackIdentity)
		assertEquals(1, confirmations)
	}

	@Test
	fun reentrantReadinessFreezeFencesTheNextRecipientAndRetainsItForRestore() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val first = ownership.acquireLive(11L)
		val second = ownership.acquireLive(12L)
		val domain = testDomain()
		var firstCalls = 0
		val secondResults = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(first) {
			firstCalls += 1
			ownership.freezeForTransitionActivation(domain)
		}
		ownership.whenLiveReady(second, secondResults::add)

		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)

		assertEquals(1, firstCalls)
		assertTrue(secondResults.isEmpty())
		assertTrue(ownership.snapshotFrozenOwnership().any {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		})
		// Before: restore skipped exact drain of naturally settled and parked frozen rows.
		// After: preserve recipients while draining every real row before restoration.
		ownership.snapshotFrozenOwnership().forEach {
			assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(it.physicalIdentity) {})
		}
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		assertEquals(listOf<ReaderForegroundWebViewLiveReadiness>(ReaderForegroundWebViewLiveReadiness.Ready), secondResults)
		assertEquals(1, firstCalls)
	}

	@Test
	fun freezeBeforeNaturalRestorationDefersReadinessUntilRestoreExactlyOnce() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		var availabilityCalls = 0
		val ownership = ReaderForegroundWebViewOwnership(onPassiveAvailable = { availabilityCalls += 1 })
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val claim = ownership.acquireLive(11L)
		val results = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(claim, results::add)
		val domain = testDomain()
		ownership.freezeForTransitionActivation(domain)

		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)

		assertTrue(results.isEmpty())
		assertEquals(0, availabilityCalls)
		// Before: late physical completion was allowed to bypass its exact drain.
		// After: drain settled work and park readiness, retaining its original outcome.
		ownership.snapshotFrozenOwnership().forEach {
			assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(it.physicalIdentity) {})
		}
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		assertEquals(listOf<ReaderForegroundWebViewLiveReadiness>(ReaderForegroundWebViewLiveReadiness.Ready), results)
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.TimedOut)
		assertEquals(1, results.size)
	}

	@Test
	fun synchronousCancellationResultDoesNotHideTheStillRunningClosureTail() {
		val ownership = ReaderForegroundWebViewOwnership()
		var closureReturned = false
		var confirmations = 0
		assertNotNull(ownership.tryAcquirePassive(7L) { finish ->
			finish(ReaderPageRasterCancellationRestoration.Restored)
			ownership.freezeForTransitionActivation(testDomain())
			val tail = ownership.snapshotFrozenOwnership().singleOrNull {
				it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
			}
			assertNotNull(tail)
			assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(tail.physicalIdentity) {
				assertTrue(closureReturned)
				confirmations += 1
			})
			assertEquals(0, confirmations)
			closureReturned = true
		})

		ownership.acquireLive(11L)

		assertTrue(closureReturned)
		assertEquals(1, confirmations)
	}

	@Test
	fun retiredFailureAwaitingItsRecipientHasAnExactFrozenIdentity() {
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish ->
			finish(ReaderPageRasterCancellationRestoration.TimedOut)
		})
		val claim = ownership.acquireLive(11L)
		val domain = testDomain()
		ownership.freezeForTransitionActivation(domain)

		// Before: invented a callback row although no recipient had registered.
		// After: the accepted claim itself owns the undelivered original terminal.
		assertEquals(1, ownership.snapshotFrozenOwnership().size)
		assertTrue(ownership.snapshotFrozenOwnership().single().kind ==
			paige.navic.reader.ReaderTransitionResourceKind.FrameHandoff)
		ownership.snapshotFrozenOwnership().forEach {
			ownership.drainFrozenOwnership(it.physicalIdentity) {}
		}
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		val results = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(claim, results::add)
		assertEquals(listOf<ReaderForegroundWebViewLiveReadiness>(
			ReaderForegroundWebViewLiveReadiness.Failed(ReaderPageRasterCancellationRestoration.TimedOut)
		), results)
	}

	@Test
	fun throwingReadinessRecipientCannotLoseAnotherAcceptedRecipient() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val claim = ownership.acquireLive(11L)
		var firstCalls = 0
		val results = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
		ownership.whenLiveReady(claim) {
			firstCalls += 1
			throw IllegalStateException("controlled callback failure")
		}
		ownership.whenLiveReady(claim, results::add)

		val failure = runCatching {
			assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		}.exceptionOrNull()

		assertTrue(failure is IllegalStateException)
		assertEquals(1, firstCalls)
		assertEquals(1, results.size)
		assertTrue(results.single() == ReaderForegroundWebViewLiveReadiness.Ready)
	}

	@Test
	fun frozenCloseKeepsUnsettledRestorationDrainableAndNeverReopens() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		ownership.acquireLive(11L)
		val domain = testDomain()
		ownership.freezeForTransitionActivation(domain)
		val identity = ownership.snapshotFrozenOwnership().single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}.physicalIdentity

		ownership.close()

		assertTrue(ownership.snapshotFrozenOwnership().any { it.physicalIdentity == identity })
		var confirmations = 0
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) { confirmations += 1 })
		assertEquals(0, confirmations)
		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(1, confirmations)
		assertFalse(ownership.canAcquirePassive())
	}

	@Test
	fun exactCompositeIdentityRejectsWrongDomainSourceTokenAndDuplicateDrain() {
		val ownership = ReaderForegroundWebViewOwnership()
		ownership.acquireLive(11L)
		val domain = testDomain()
		ownership.freezeForTransitionActivation(domain)
		val identity = ownership.snapshotFrozenOwnership().single().physicalIdentity
		var confirmations = 0
		listOf(
			identity.copy(domain = domain.copy(readerSessionGeneration = 18L)),
			identity.copy(domain = domain.copy(freezeToken = ReaderLegacyFreezeToken(29L))),
			identity.copy(source = ReaderLegacyInventorySource.SemanticCommandSlot),
			identity.copy(sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(999L))
		).forEach { wrong ->
			assertTrue(ownership.drainFrozenOwnership(wrong) { confirmations += 1 } is ReaderPortCommandResult.Rejected)
		}
		assertEquals(0, confirmations)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) {
			assertTrue(it == identity)
			confirmations += 1
		})
		assertTrue(ownership.drainFrozenOwnership(identity) { confirmations += 1 } is ReaderPortCommandResult.Rejected)
		assertEquals(1, confirmations)
	}

	@Test
	fun task397ReleasedWaitingRecipientSurvivesThrowAndRefreezeWithoutReadmission() {
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		val claim = ownership.acquireLive(11L)
		val domain = testDomain()
		val nextDomain = ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(29L))
		var first = 0
		var second = 0
		var confirmations = 0
		ownership.whenLiveReady(claim) { result ->
			assertTrue(result == ReaderForegroundWebViewLiveReadiness.Invalidated)
			first++
			assertNull(ownership.beginLiveMutation(claim))
			assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(nextDomain))
			val callback = ownership.snapshotFrozenOwnership().first {
				it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
			}
			ownership.drainFrozenOwnership(callback.physicalIdentity) { confirmations++ }
			assertEquals(0, confirmations, "accepted recipient has not returned")
			throw IllegalStateException("controlled recipient failure")
		}
		ownership.whenLiveReady(claim) { result ->
			assertTrue(result == ReaderForegroundWebViewLiveReadiness.Invalidated)
			second++
		}
		ownership.freezeForTransitionActivation(domain)
		assertTrue(ownership.releaseLive(claim))
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		ownership.snapshotFrozenOwnership().forEach { ownership.drainFrozenOwnership(it.physicalIdentity) {} }
		val failure = runCatching { ownership.restoreAfterTransitionActivation(domain) }.exceptionOrNull()
		assertTrue(failure is IllegalStateException, "original released recipient must actually run")
		assertEquals(1, first)
		assertEquals(1, confirmations)
		assertEquals(0, second)
		ownership.snapshotFrozenOwnership().forEach { ownership.drainFrozenOwnership(it.physicalIdentity) {} }
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty())
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(nextDomain))
		assertEquals(1, first)
		assertEquals(1, second)
		assertEquals(0, ownership.snapshot().liveClaims)
		assertNull(ownership.beginLiveMutation(claim))
		assertFalse(ownership.releaseLive(claim))
	}

	@Test
	fun task397CloseBeforeFreezeRetainsPassiveExactDrainWithoutReopening() {
		val ownership = ReaderForegroundWebViewOwnership()
		val lease = assertNotNull(ownership.tryAcquirePassive(7L) {})
		ownership.close()
		val domain = testDomain()
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		assertNotNull(ownership.connectedFrozenOwnership())
		val identity = ownership.snapshotFrozenOwnership().single().physicalIdentity
		var confirmations = 0
		listOf(
			identity.copy(domain = domain.copy(readerSessionGeneration = 19L)),
			identity.copy(source = ReaderLegacyInventorySource.SemanticCommandSlot),
			identity.copy(sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(999L))
		).forEach { wrong ->
			assertTrue(ownership.drainFrozenOwnership(wrong) { confirmations++ } is ReaderPortCommandResult.Rejected)
		}
		assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(identity) { confirmations++ })
		assertEquals(0, confirmations)
		assertTrue(ownership.releasePassive(lease))
		assertEquals(1, confirmations)
		assertTrue(ownership.drainFrozenOwnership(identity) { confirmations++ } is ReaderPortCommandResult.Rejected)
		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		assertFalse(ownership.canAcquirePassive())
		assertNull(ownership.tryAcquirePassive(8L) {})
	}

	@Test
	fun task397CloseBeforeFreezeRetainsAsyncRestorationAndInvocationReturnTails() {
		val ownership = ReaderForegroundWebViewOwnership()
		var finish: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		assertNotNull(ownership.tryAcquirePassive(7L) { finish = it })
		ownership.acquireLive(11L)
		ownership.close()
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(testDomain()))
		val rows = ownership.snapshotFrozenOwnership()
		assertEquals(2, rows.size)
		var confirmations = 0
		rows.forEach { ownership.drainFrozenOwnership(it.physicalIdentity) { confirmations++ } }
		assertEquals(0, confirmations)
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(2, confirmations)
		assertNotNull(finish)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(2, confirmations)
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty())

		val running = ReaderForegroundWebViewOwnership()
		val claim = running.acquireLive(12L)
		val generation = assertNotNull(running.beginLiveMutation(claim))
		var returned = false
		var tailConfirmations = 0
		assertTrue(running.invokeLiveMutationWithResult<Int>(claim, generation, { result ->
			result(1)
			assertEquals(0, tailConfirmations)
			returned = true
		}) {
			running.close()
			assertEquals(ReaderPortCommandResult.Accepted, running.freezeForTransitionActivation(testDomain()))
			val tails = running.snapshotFrozenOwnership()
			assertEquals(2, tails.size)
			tails.forEach { row -> running.drainFrozenOwnership(row.physicalIdentity) {
				assertTrue(returned, "outer primitive invocation must return before confirmation")
				tailConfirmations++
			} }
			assertEquals(0, tailConfirmations)
		})
		assertEquals(2, tailConfirmations)
		assertTrue(running.snapshotFrozenOwnership().isEmpty())
		assertTrue(running.restoreAfterTransitionActivation(testDomain()) is ReaderPortCommandResult.Rejected)
	}

	private fun testDomain() = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))

	private fun ReaderForegroundWebViewOwnership.setMutationGenerationForTest(
		value: Long
	) {
		setLongFieldForTest("mutationGeneration", value)
	}

	private fun ReaderForegroundWebViewOwnership.setLongFieldForTest(
		name: String,
		value: Long
	) {
		javaClass.getDeclaredField(name).apply {
			isAccessible = true
			setLong(this@setLongFieldForTest, value)
		}
	}
}
