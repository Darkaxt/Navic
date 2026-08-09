package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

	@Test
	fun liveDispatchWaitsForAsynchronousRestorationBeforeMutationAndJavascript() {
		var restore: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(ownership.tryAcquirePassive(1L) { restore = it })
		val request = request("page-turn-41")
		val claim = ownership.acquireLive(request.gestureId)
		val dispatched = mutableListOf<ReaderForegroundWebViewMutationGeneration>()
		val coordinator = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { it == request },
			dispatchExact = { _, generation ->
				dispatched += generation
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, reason -> error("unexpected rejection $reason") }
		)
		assertTrue(coordinator.transfer(request, claim))

		assertTrue(coordinator.dispatch(request))

		assertTrue(dispatched.isEmpty())
		assertFalse(coordinator.isCurrent(request))
		checkNotNull(restore)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(1, dispatched.size)
		assertTrue(dispatched.single().value > 0L)
		assertTrue(coordinator.isCurrent(request))
	}

	@Test
	fun liveDispatchHandlesSynchronousRestorationWithoutLosingTheTerminal() {
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(1L) { restore ->
				restore(ReaderPageRasterCancellationRestoration.Restored)
			}
		)
		val request = request("page-turn-42")
		val claim = ownership.acquireLive(request.gestureId)
		var dispatches = 0
		val coordinator = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ ->
				dispatches += 1
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, reason -> error("unexpected rejection $reason") }
		)
		assertTrue(coordinator.transfer(request, claim))

		coordinator.dispatch(request)

		assertEquals(1, dispatches)
		assertTrue(coordinator.isCurrent(request))
	}

	@Test
	fun failedDetachedTimedOutAndInvalidatedReadinessNeverDispatch() {
		listOf(
			ReaderPageRasterCancellationRestoration.Detached,
			ReaderPageRasterCancellationRestoration.TimedOut
		).forEach { terminal ->
			var restore: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
			val ownership = ReaderForegroundWebViewOwnership()
			checkNotNull(ownership.tryAcquirePassive(1L) { restore = it })
			val request = request("page-turn-${terminal.name}")
			val claim = ownership.acquireLive(request.gestureId)
			val rejected = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
			val coordinator = ReaderPageRelocationLiveDispatchCoordinator(
				foregroundWebViewOwnership = ownership,
				isDispatchCurrent = { true },
				dispatchExact = { _, _ -> error("failed readiness dispatched") },
				onRejected = { _, reason -> rejected += reason }
			)
			coordinator.transfer(request, claim)
			coordinator.dispatch(request)

			checkNotNull(restore)(terminal)

			assertEquals(
				listOf(ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated),
				rejected
			)
			assertEquals(0, ownership.snapshot().liveClaims)
		}

		var restore: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(ownership.tryAcquirePassive(1L) { restore = it })
		val request = request("page-turn-invalidated")
		val claim = ownership.acquireLive(request.gestureId)
		val rejected = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
		val coordinator = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> error("invalidated readiness dispatched") },
			onRejected = { _, reason -> rejected += reason }
		)
		coordinator.transfer(request, claim)
		coordinator.dispatch(request)
		assertTrue(ownership.releaseLive(claim))
		checkNotNull(restore)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(
			listOf(ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated),
			rejected
		)
	}

	@Test
	fun missingOwnerAndWebViewUnavailableFailClosedWithTypedReasons() {
		val request = request("page-turn-43")
		val missingOwnership = ReaderForegroundWebViewOwnership()
		val missingRejections = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
		val missing = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = missingOwnership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> error("missing owner dispatched") },
			onRejected = { _, reason -> missingRejections += reason }
		)
		assertFalse(missing.dispatch(request))
		assertEquals(
			listOf(ReaderPageRelocationDiagnosticRejectionReason.OwnershipUnavailable),
			missingRejections
		)

		val ownership = ReaderForegroundWebViewOwnership()
		val claim = ownership.acquireLive(request.gestureId)
		val unavailableRejections = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
		val unavailable = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ ->
				ReaderPageRelocationExactDispatchResult.Rejected(
					ReaderPageRelocationDiagnosticRejectionReason.WebViewUnavailable
				)
			},
			onRejected = { _, reason -> unavailableRejections += reason }
		)
		unavailable.transfer(request, claim)
		unavailable.dispatch(request)
		assertEquals(
			listOf(ReaderPageRelocationDiagnosticRejectionReason.WebViewUnavailable),
			unavailableRejections
		)
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun staleReadinessAfterCancellationAndStaleTokenAfterReplacementCannotDispatch() {
		var restore: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(ownership.tryAcquirePassive(1L) { restore = it })
		val cancelledRequest = request("page-turn-44")
		val cancelledClaim = ownership.acquireLive(cancelledRequest.gestureId)
		var dispatches = 0
		val rejections = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
		val coordinator = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ ->
				dispatches += 1
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, reason -> rejections += reason }
		)
		coordinator.transfer(cancelledRequest, cancelledClaim)
		coordinator.dispatch(cancelledRequest)
		coordinator.releaseAll()
		checkNotNull(restore)(ReaderPageRasterCancellationRestoration.Restored)
		assertEquals(0, dispatches)
		assertTrue(rejections.isEmpty())

		val readyOwnership = ReaderForegroundWebViewOwnership()
		val original = request("page-turn-45")
		val replacement = original.copy(
			token = ReaderPageRelocationToken("page-turn-46"),
			rasterGeneration = original.rasterGeneration + 1L,
			textureGeneration = original.textureGeneration + 1L
		)
		val readyClaim = readyOwnership.acquireLive(original.gestureId)
		val dispatchedTokens = mutableListOf<String>()
		var currentRequest = original
		val replacementCoordinator = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = readyOwnership,
			isDispatchCurrent = { it == currentRequest },
			dispatchExact = { dispatched, _ ->
				dispatchedTokens += dispatched.token.value
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, reason -> error("unexpected rejection $reason") }
		)
		replacementCoordinator.transfer(original, readyClaim)
		replacementCoordinator.dispatch(original)
		assertTrue(replacementCoordinator.replace(original, replacement))
		currentRequest = replacement
		assertFalse(replacementCoordinator.dispatch(original))
		assertTrue(replacementCoordinator.dispatch(replacement))
		assertEquals(listOf("page-turn-45", "page-turn-46"), dispatchedTokens)
	}

	@Test
	fun dispatchExceptionReleasesExactlyOnceAndSuccessRetainsUntilExposureCompletes() {
		val request = request("page-turn-47")
		val failedOwnership = ReaderForegroundWebViewOwnership()
		val failedClaim = failedOwnership.acquireLive(request.gestureId)
		val rejected = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
		val failed = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = failedOwnership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> error("javascript") },
			onRejected = { _, reason -> rejected += reason }
		)
		failed.transfer(request, failedClaim)
		failed.dispatch(request)
		assertEquals(
			listOf(ReaderPageRelocationDiagnosticRejectionReason.JavascriptDispatchFailed),
			rejected
		)
		assertEquals(0, failedOwnership.snapshot().liveClaims)
		failed.releaseAll()
		assertEquals(0, failedOwnership.snapshot().liveClaims)

		val successOwnership = ReaderForegroundWebViewOwnership()
		val successClaim = successOwnership.acquireLive(request.gestureId)
		val success = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = successOwnership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> ReaderPageRelocationExactDispatchResult.Dispatched },
			onRejected = { _, reason -> error("unexpected rejection $reason") }
		)
		success.transfer(request, successClaim)
		success.dispatch(request)

		assertTrue(success.isCurrent(request))
		assertNull(
			successOwnership.tryAcquirePassive(2L) {
				error("successful live handoff must continue blocking passive work")
			}
		)
		assertTrue(success.complete(request))
		assertFalse(success.complete(request))
		assertEquals(0, successOwnership.snapshot().liveClaims)
		assertTrue(successOwnership.canAcquirePassive())
		success.releaseAll()
		assertEquals(0, successOwnership.snapshot().liveClaims)
	}

	@Test
	fun lifecycleDrainReleasesEveryTransferredAndRetainedClaim() {
		val ownership = ReaderForegroundWebViewOwnership()
		val coordinator = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> ReaderPageRelocationExactDispatchResult.Dispatched },
			onRejected = { _, reason -> error("unexpected rejection $reason") }
		)
		repeat(3) { index ->
			val request = request("page-turn-${50 + index}").copy(gestureId = 61L + index)
			val claim = ownership.acquireLive(request.gestureId)
			assertTrue(coordinator.transfer(request, claim))
			coordinator.dispatch(request)
		}
		assertEquals(3, ownership.snapshot().liveClaims)

		coordinator.releaseAll()

		assertEquals(0, ownership.snapshot().liveClaims)
		assertTrue(ownership.canAcquirePassive())
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
