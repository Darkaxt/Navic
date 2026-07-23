package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageTurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderPageRelocationGestureCoordinatorTest {
	@Test
	fun fullCapacityRejectsBeforeRendererAdmission() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val coordinator = ReaderPageRelocationGestureCoordinator(queue)
		var rendererCalls = 0
		assertEquals(
			ReaderPageRelocationStartResult.Admitted,
			coordinator.start(
				metadata(gestureId = 1L),
				protocolActionMasked = 0,
				rendererAdmission = { rendererCalls += 1; true },
				publishTerminal = { _, _ -> true }
			)
		)
		var terminalCount = 0

		val rejected = coordinator.start(
			metadata(gestureId = 2L),
			protocolActionMasked = 0,
			rendererAdmission = { error("saturated renderer call ran") },
			publishTerminal = { outcome, detail ->
				terminalCount += 1
				assertEquals(ReaderPageGestureTerminalOutcome.RejectedSettling, outcome)
				assertIs<ReaderPageGestureTerminalDetail.RelocationCapacityUnavailable>(detail)
				true
			}
		)

		assertIs<ReaderPageRelocationStartResult.TerminalPublished>(rejected)
		assertEquals(1, rendererCalls)
		assertEquals(1, terminalCount)
		assertEquals(1, coordinator.reservationCount())
	}

	@Test
	fun duplicateGestureRejectsWithoutSecondRendererCall() {
		val queue = ReaderPageRelocationQueue(capacity = 2)
		val coordinator = ReaderPageRelocationGestureCoordinator(queue)
		var rendererCalls = 0
		coordinator.start(
			metadata(gestureId = 3L),
			0,
			rendererAdmission = { rendererCalls += 1; true },
			publishTerminal = { _, _ -> true }
		)

		val duplicate = coordinator.start(
			metadata(gestureId = 3L),
			0,
			rendererAdmission = { rendererCalls += 1; true },
			publishTerminal = { _, detail ->
				assertIs<ReaderPageGestureTerminalDetail.RelocationReservationProtocolFailure>(detail)
				true
			}
		)

		assertIs<ReaderPageRelocationStartResult.TerminalPublished>(duplicate)
		assertEquals(1, rendererCalls)
		assertEquals(1, queue.occupiedCount())
	}

	@Test
	fun rendererFalseWithoutCallbackReleasesAndPublishesProtocolFailure() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val coordinator = ReaderPageRelocationGestureCoordinator(queue)
		var terminalCount = 0

		val result = coordinator.start(
			metadata(gestureId = 4L),
			protocolActionMasked = 0,
			rendererAdmission = { false },
			publishTerminal = { _, detail ->
				terminalCount += 1
				assertIs<ReaderPageGestureTerminalDetail.TouchProtocolFailure>(detail)
				true
			}
		)

		assertIs<ReaderPageRelocationStartResult.TerminalPublished>(result)
		assertEquals(1, terminalCount)
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun eachReservationIdentityDriftPublishesOneTerminalBeforeOwnerRelease() {
		val driftCases = listOf(
			Triple("session-b", 10L, 20L),
			Triple("session-a", 11L, 20L),
			Triple("session-a", 10L, 19L)
		)
		driftCases.forEachIndexed { index, (session, raster, sourceTexture) ->
			val queue = ReaderPageRelocationQueue(capacity = 1)
			val coordinator = ReaderPageRelocationGestureCoordinator(queue)
			val gestureId = index + 5L
			coordinator.start(
				metadata(gestureId),
				0,
				rendererAdmission = { true },
				publishTerminal = { _, _ -> true }
			)
			val terminals = mutableListOf<ReaderPageGestureTerminalOutcome>()

			val result = coordinator.commit(
				gestureId = gestureId,
				settledSourceTextureGeneration = sourceTexture,
				promotedRasterGeneration = raster,
				promotedTextureGeneration = 21L,
				destinationOrdinal = 4,
				logicalDirection = ReaderPageTurnDirection.Next,
				currentFoliateSessionId = session,
				publishDriftTerminal = { outcome, detail ->
					assertEquals(1, coordinator.reservationCount())
					assertIs<ReaderPageGestureTerminalDetail.RelocationGenerationOrSessionDrift>(detail)
					terminals += outcome
					true
				},
				publishCommittedTerminal = { error("committed terminal must not run") },
				dispatch = { error("dispatch must not run") }
			)

			assertEquals(ReaderPageRelocationCommitResult.GenerationOrSessionDrift, result)
			assertEquals(listOf(ReaderPageGestureTerminalOutcome.FailedRecovery), terminals)
			assertEquals(0, coordinator.reservationCount())
			assertEquals(0, queue.occupiedCount())
		}
	}

	@Test
	fun failedCommittedTerminalCasRollsBackTransferredRequest() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		var rejectedToken: String? = null
		val coordinator = ReaderPageRelocationGestureCoordinator(
			queue = queue,
			onRejected = { rejectedToken = it.token.value }
		)
		coordinator.start(
			metadata(gestureId = 9L),
			0,
			rendererAdmission = { true },
			publishTerminal = { _, _ -> true }
		)
		var dispatchCalls = 0

		val result = coordinator.commit(
			gestureId = 9L,
			settledSourceTextureGeneration = 20L,
			promotedRasterGeneration = 10L,
			promotedTextureGeneration = 21L,
			destinationOrdinal = 4,
			logicalDirection = ReaderPageTurnDirection.Next,
			currentFoliateSessionId = "session-a",
			publishDriftTerminal = { _, _ -> true },
			publishCommittedTerminal = { false },
			dispatch = { dispatchCalls += 1 }
		)

		assertEquals(ReaderPageRelocationCommitResult.TerminalNotPublished, result)
		assertEquals("page-turn-1", rejectedToken)
		assertEquals(0, dispatchCalls)
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun commitDispatchesOnlyAfterSuccessfulTerminalCas() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val events = mutableListOf<String>()
		val coordinator = ReaderPageRelocationGestureCoordinator(
			queue = queue,
			onQueued = { events += "queued:${it.token.value}" },
			onRejected = { events += "rejected:${it.token.value}" }
		)
		coordinator.start(
			metadata(gestureId = 10L),
			0,
			rendererAdmission = { true },
			publishTerminal = { _, _ -> true }
		)

		val result = coordinator.commit(
			gestureId = 10L,
			settledSourceTextureGeneration = 20L,
			promotedRasterGeneration = 10L,
			promotedTextureGeneration = 21L,
			destinationOrdinal = 4,
			logicalDirection = ReaderPageTurnDirection.Next,
			currentFoliateSessionId = "session-a",
			publishDriftTerminal = { _, _ -> true },
			publishCommittedTerminal = { events += "terminal"; true },
			dispatch = { events += "dispatch:${it.token.value}" }
		)

		val published = assertIs<ReaderPageRelocationCommitResult.Published>(result)
		assertEquals(21L, published.request.textureGeneration)
		assertEquals(
			listOf(
				"queued:${published.request.token.value}",
				"terminal",
				"dispatch:${published.request.token.value}"
			),
			events
		)
		assertEquals(1, queue.occupiedCount())
	}

	@Test
	fun throwingTerminalCasRollsBackBeforePropagating() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val coordinator = ReaderPageRelocationGestureCoordinator(queue)
		coordinator.start(
			metadata(gestureId = 11L),
			0,
			rendererAdmission = { true },
			publishTerminal = { _, _ -> true }
		)

		assertFailsWith<IllegalStateException> {
			coordinator.commit(
				11L,
				20L,
				10L,
				21L,
				4,
				ReaderPageTurnDirection.Next,
				"session-a",
				publishDriftTerminal = { _, _ -> true },
				publishCommittedTerminal = { error("terminal-failed") },
				dispatch = { error("dispatch must not run") }
			)
		}
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun synchronousRendererTerminalReturnsExactAlreadyPublishedResult() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		lateinit var coordinator: ReaderPageRelocationGestureCoordinator
		val expectedOutcome = ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
		val expectedDetail = ReaderPageGestureTerminalDetail.TouchProtocolFailure(9)
		var rendererTerminalPublications = 0
		var fallbackTerminalPublications = 0
		coordinator = ReaderPageRelocationGestureCoordinator(queue)

		val result = coordinator.start(
			metadata(gestureId = 12L),
			protocolActionMasked = 9,
			rendererAdmission = {
				rendererTerminalPublications += 1
				assertTrue(coordinator.finish(12L, expectedOutcome, expectedDetail))
				false
			},
			publishTerminal = { _, _ -> fallbackTerminalPublications += 1; true }
		)

		assertEquals(
			ReaderPageRelocationStartResult.TerminalPublished(expectedOutcome, expectedDetail),
			result
		)
		assertEquals(1, rendererTerminalPublications)
		assertEquals(0, fallbackTerminalPublications)
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun everyNonCommittedTerminalReleasesExactlyOnce() {
		repeat(7) { index ->
			val queue = ReaderPageRelocationQueue(capacity = 1)
			val coordinator = ReaderPageRelocationGestureCoordinator(queue)
			val gestureId = index + 20L
			coordinator.start(
				metadata(gestureId),
				0,
				rendererAdmission = { true },
				publishTerminal = { _, _ -> true }
			)
			val detail = ReaderPageGestureTerminalDetail.ControllerCancelled
			assertTrue(
				coordinator.finish(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledByUser,
					detail
				)
			)
			assertFalse(
				coordinator.finish(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledByUser,
					detail
				)
			)
			assertEquals(0, queue.occupiedCount())
		}
	}

	@Test
	fun cancelAllDrainsQueuedAndReservedOwnership() {
		val queue = ReaderPageRelocationQueue(capacity = 2)
		val coordinator = ReaderPageRelocationGestureCoordinator(queue)
		coordinator.start(
			metadata(gestureId = 40L),
			0,
			rendererAdmission = { true },
			publishTerminal = { _, _ -> true }
		)
		coordinator.start(
			metadata(gestureId = 41L),
			0,
			rendererAdmission = { true },
			publishTerminal = { _, _ -> true }
		)
		coordinator.commit(
			40L,
			20L,
			10L,
			21L,
			4,
			ReaderPageTurnDirection.Next,
			"session-a",
			publishDriftTerminal = { _, _ -> true },
			publishCommittedTerminal = { true },
			dispatch = {}
		)

		val drained = coordinator.cancelAll()

		assertEquals(1, drained.queued.size)
		assertEquals(1, drained.reservations.size)
		assertEquals(0, coordinator.reservationCount())
		assertEquals(0, queue.occupiedCount())
	}
}

private fun metadata(gestureId: Long): ReaderPageRelocationReservationMetadata =
	ReaderPageRelocationReservationMetadata(
		gestureId = gestureId,
		sourceOrdinal = 3,
		foliateSessionId = "session-a",
		reservedRasterGeneration = 10L,
		reservedTextureGeneration = 20L
	)
