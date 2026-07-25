package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationToken
import paige.navic.reader.ReaderPageTurnDirection

class ReaderPageRelocationDispatchTimeoutTest {
	private class FakeScheduler : ReaderPageRelocationDispatchTimeoutScheduler {
		val callbacks = linkedMapOf<Runnable, Long>()
		var acceptsCallbacks = true

		override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
			if (!acceptsCallbacks) return false
			callbacks[action] = delayMillis
			return true
		}

		override fun removeCallbacks(action: Runnable) {
			callbacks.remove(action)
		}

		fun fireNext() {
			val action = callbacks.keys.first()
			callbacks.remove(action)
			action.run()
		}
	}

	@Test
	fun exactDispatchedRequestTimesOutOnceAndReleasesCallbackOwnership() {
		val scheduler = FakeScheduler()
		val timedOut = mutableListOf<ReaderPageRelocationRequest>()
		val timeout = ReaderPageRelocationDispatchTimeout(
			scheduler = scheduler,
			timeoutMillis = 10_000L,
			onTimeout = timedOut::add
		)
		val request = request("page-turn-31")

		timeout.arm(request)

		assertEquals(1, timeout.pendingCallbackCount())
		assertEquals(1, timeout.pendingCallbackLimit)
		assertEquals(listOf(10_000L), scheduler.callbacks.values.toList())

		scheduler.fireNext()
		scheduler.callbacks.values.forEach { error("Timed-out callback remained scheduled: $it") }

		assertEquals(listOf(request), timedOut)
		assertEquals(0, timeout.pendingCallbackCount())
	}

	@Test
	fun exactAcknowledgementCancelsOnlyItsMatchingDispatchTimeout() {
		val scheduler = FakeScheduler()
		val timedOut = mutableListOf<ReaderPageRelocationRequest>()
		val timeout = ReaderPageRelocationDispatchTimeout(
			scheduler = scheduler,
			onTimeout = timedOut::add
		)
		val request = request("page-turn-31")

		timeout.arm(request)

		assertFalse(timeout.cancel(request("page-turn-30")))
		assertEquals(1, timeout.pendingCallbackCount())
		assertTrue(timeout.cancel(request))
		assertEquals(0, timeout.pendingCallbackCount())
		assertTrue(scheduler.callbacks.isEmpty())
		assertTrue(timedOut.isEmpty())
	}

	@Test
	fun stalePhysicalCallbackCannotTimeoutACancelledRequest() {
		val scheduler = FakeScheduler()
		val timedOut = mutableListOf<ReaderPageRelocationRequest>()
		val timeout = ReaderPageRelocationDispatchTimeout(
			scheduler = scheduler,
			onTimeout = timedOut::add
		)
		val request = request("page-turn-31")

		timeout.arm(request)
		val staleCallback = scheduler.callbacks.keys.single()
		assertTrue(timeout.cancel(request))

		staleCallback.run()

		assertTrue(timedOut.isEmpty())
		assertEquals(0, timeout.pendingCallbackCount())
	}

	@Test
	fun rejectedSchedulerAdmissionTerminatesSynchronouslyWithoutRetainingOwnership() {
		val scheduler = FakeScheduler().apply { acceptsCallbacks = false }
		val timedOut = mutableListOf<ReaderPageRelocationRequest>()
		val timeout = ReaderPageRelocationDispatchTimeout(
			scheduler = scheduler,
			onTimeout = timedOut::add
		)
		val request = request("page-turn-31")

		timeout.arm(request)

		assertEquals(listOf(request), timedOut)
		assertEquals(0, timeout.pendingCallbackCount())
		assertTrue(scheduler.callbacks.isEmpty())
	}

	@Test
	fun timeoutRecoveryKeepsAnyAuthoritativeSameSessionFoliateOrdinal() {
		val request = request("page-turn-31")

		assertEquals(
			12,
			readerPageRelocationDispatchRecoveryOrdinal(
				request = request,
				currentFoliateSessionId = "session-a",
				currentWebViewOrdinal = 12
			)
		)
		assertEquals(
			15,
			readerPageRelocationDispatchRecoveryOrdinal(
				request = request,
				currentFoliateSessionId = "session-a",
				currentWebViewOrdinal = 15
			)
		)
	}

	@Test
	fun timeoutRecoveryFallsBackToSourceWithoutASameSessionFoliateOrdinal() {
		val request = request("page-turn-31")

		assertEquals(
			16,
			readerPageRelocationDispatchRecoveryOrdinal(
				request = request,
				currentFoliateSessionId = "session-b",
				currentWebViewOrdinal = 12
			)
		)
		assertEquals(
			16,
			readerPageRelocationDispatchRecoveryOrdinal(
				request = request,
				currentFoliateSessionId = "session-a",
				currentWebViewOrdinal = null
			)
		)
	}

	private fun request(token: String): ReaderPageRelocationRequest =
		ReaderPageRelocationRequest(
			token = ReaderPageRelocationToken(token),
			gestureId = 61L,
			rasterGeneration = 4L,
			textureGeneration = 35L,
			sourceOrdinal = 16,
			destinationOrdinal = 15,
			logicalDirection = ReaderPageTurnDirection.Previous,
			foliateSessionId = "session-a"
		)
}
