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
	fun stalePassiveReleaseCannotReleaseTheCurrentLease() {
		val ownership = ReaderForegroundWebViewOwnership()
		val stale = checkNotNull(
			ownership.tryAcquirePassive(7L) {
				error("not preempted")
			}
		)
		assertTrue(ownership.releasePassive(stale))
		val current = checkNotNull(
			ownership.tryAcquirePassive(8L) {
				error("not preempted")
			}
		)

		assertFalse(ownership.releasePassive(stale))
		assertTrue(ownership.isCurrent(current))
		assertEquals(1, ownership.snapshot().passiveOwners)
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
