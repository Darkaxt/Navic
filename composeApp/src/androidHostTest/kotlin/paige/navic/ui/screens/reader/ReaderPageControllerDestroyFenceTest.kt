package paige.navic.ui.screens.reader

import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import paige.navic.reader.ReaderPageRelocationDrain
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPageControllerDestroyFenceTest {
	@Test
	fun destroyFenceStopsAdmissionAndCancelsEveryOwnerBeforeRendererDisposal() = runTest {
		val queue = ReaderPageRelocationQueue(capacity = 3)
		val firstReservation = assertIs<
			ReaderPageRelocationReservationResult.Reserved
		>(queue.reserve(11L)).reservation
		val firstRequest = assertIs<
			ReaderPageRelocationTransferResult.Enqueued
		>(
			queue.enqueueReserved(
				reservation = firstReservation,
				rasterGeneration = 7L,
				textureGeneration = 9L,
				sourceOrdinal = 4,
				destinationOrdinal = 5,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "session"
			)
		).request
		assertSame(firstRequest, queue.commandToDispatch())
		assertTrue(
			queue.acknowledge(
				token = firstRequest.token.value,
				pageIndex = 5,
				foliateSessionId = "session",
				rasterGeneration = 7L,
				textureGeneration = 9L
			)
		)
		val secondReservation = assertIs<
			ReaderPageRelocationReservationResult.Reserved
		>(queue.reserve(12L)).reservation
		val thirdReservation = assertIs<
			ReaderPageRelocationReservationResult.Reserved
		>(queue.reserve(13L)).reservation
		val events = mutableListOf<String>()
		val fence = ReaderPageControllerDestroyFence(
			scope = this,
			fenceAdmission = { events += "fence" },
			advanceGenerations = { events += "advance" },
			cancelActiveGesture = { events += "gesture" },
			cancelRecovery = { events += "recovery" },
			closeVisualHandoff = { events += "handoff" },
			cancelRelocations = {
				events += "cancel-relocations"
				queue.cancelAll()
			},
			verifyRelocationsDrained = { drain ->
				events += "verify-relocations"
				assertEquals(listOf(firstRequest), drain.queued)
				assertEquals(
					listOf(secondReservation, thirdReservation),
					drain.reservations
				)
				assertEquals(0, queue.occupiedCount())
			},
			markPageSetsObsolete = { events += "obsolete" },
			hideSurface = { events += "hide" },
			disposeRendererAndOwners = { events += "dispose" }
		)

		fence.start().await()

		assertEquals(
			listOf(
				"fence",
				"advance",
				"gesture",
				"recovery",
				"handoff",
				"cancel-relocations",
				"verify-relocations",
				"obsolete",
				"hide",
				"dispose"
			),
			events
		)
	}

	@Test
	fun destroyFenceContinuesAfterFailuresAndStillDisposesOwners() = runTest {
		val events = mutableListOf<String>()
		val fence = ReaderPageControllerDestroyFence(
			scope = this,
			fenceAdmission = { events += "fence" },
			advanceGenerations = { events += "advance" },
			cancelActiveGesture = {
				events += "gesture"
				throw IllegalStateException("gesture")
			},
			cancelRecovery = { events += "recovery" },
			closeVisualHandoff = { events += "handoff" },
			cancelRelocations = {
				events += "relocations"
				ReaderPageRelocationDrain(emptyList(), emptyList())
			},
			verifyRelocationsDrained = { events += "verify" },
			markPageSetsObsolete = { events += "obsolete" },
			hideSurface = {
				events += "hide"
				throw IllegalArgumentException("hide")
			},
			disposeRendererAndOwners = {
				events += "dispose"
				throw UnsupportedOperationException("dispose")
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			fence.start().await()
		}

		assertEquals(ReaderPageTeardownStage.ControllerWorker, failure.stage)
		assertEquals(2, failure.suppressed.size)
		assertEquals(
			listOf(
				"fence",
				"advance",
				"gesture",
				"recovery",
				"handoff",
				"relocations",
				"verify",
				"obsolete",
				"hide",
				"dispose"
			),
			events
		)
	}

	@Test
	fun destroyFenceIsIdempotentUnderReentrantStart() = runTest {
		val invocations = mutableMapOf<String, Int>()
		fun invoked(name: String) {
			invocations[name] = invocations.getOrDefault(name, 0) + 1
		}
		lateinit var fence: ReaderPageControllerDestroyFence
		var reentrant: Deferred<Unit>? = null
		fence = ReaderPageControllerDestroyFence(
			scope = this,
			fenceAdmission = { invoked("fence") },
			advanceGenerations = { invoked("advance") },
			cancelActiveGesture = {
				invoked("gesture")
				reentrant = fence.start()
			},
			cancelRecovery = { invoked("recovery") },
			closeVisualHandoff = { invoked("handoff") },
			cancelRelocations = {
				invoked("relocations")
				ReaderPageRelocationDrain(emptyList(), emptyList())
			},
			verifyRelocationsDrained = { invoked("verify") },
			markPageSetsObsolete = { invoked("obsolete") },
			hideSurface = { invoked("hide") },
			disposeRendererAndOwners = { invoked("dispose") }
		)

		val first = fence.start()
		first.await()
		val later = fence.start()

		assertSame(first, reentrant)
		assertSame(first, later)
		assertEquals(
			setOf(
				"fence",
				"advance",
				"gesture",
				"recovery",
				"handoff",
				"relocations",
				"verify",
				"obsolete",
				"hide",
				"dispose"
			),
			invocations.keys
		)
		assertTrue(invocations.values.all { count -> count == 1 })
	}

	@Test
	fun hostTeardownClosesForegroundOwnershipAfterTheControllerDrainOnFailure() {
		val host = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val teardown = host.substringAfter("private fun teardownTask4Resources()")

		assertContains(teardown, "teardown.invokeOnCompletion")
		assertContains(teardown, "runCatching(foregroundWebViewOwnership::close)")
		assertContains(teardown, "typedFailure.addSuppressed(ownershipCloseFailure)")
		assertTrue(
			teardown.indexOf("val teardown = pageRasterPreparationController.destroy()") <
				teardown.indexOf("runCatching(foregroundWebViewOwnership::close)")
		)
	}

	@Test
	fun destroyRelocationCancellationDrainsTransferredLiveClaims() = runTest {
		val ownership = ReaderForegroundWebViewOwnership()
		val request = paige.navic.reader.ReaderPageRelocationRequest(
			token = paige.navic.reader.ReaderPageRelocationToken("page-turn-71"),
			gestureId = 71L,
			rasterGeneration = 7L,
			textureGeneration = 9L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			logicalDirection = ReaderPageTurnDirection.Next,
			foliateSessionId = "session"
		)
		val claim = ownership.acquireLive(request.gestureId)
		val liveDispatch = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> ReaderPageRelocationExactDispatchResult.Dispatched },
			onRejected = { _, reason -> error("unexpected rejection $reason") }
		)
		assertTrue(liveDispatch.transfer(request, claim))
		liveDispatch.dispatch(request)
		assertEquals(1, ownership.snapshot().liveClaims)
		val fence = ReaderPageControllerDestroyFence(
			scope = this,
			fenceAdmission = {},
			advanceGenerations = {},
			cancelActiveGesture = {},
			cancelRecovery = {},
			closeVisualHandoff = {},
			cancelRelocations = {
				liveDispatch.releaseAll()
				ReaderPageRelocationDrain(listOf(request), emptyList())
			},
			verifyRelocationsDrained = {
				assertEquals(0, ownership.snapshot().liveClaims)
			},
			markPageSetsObsolete = {},
			hideSurface = {},
			disposeRendererAndOwners = {}
		)

		fence.start().await()

		assertEquals(0, ownership.snapshot().liveClaims)
	}
}
