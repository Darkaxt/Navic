package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderPageRelocationConcurrencyTest {
	@Test
	fun callbackCanRecursivelyReserveAndObserveSameQueue() {
		lateinit var queue: ReaderPageRelocationQueue
		var callbacks = 0
		queue = ReaderPageRelocationQueue(capacity = 2) {
			callbacks += 1
			assertEquals(callbacks, queue.occupiedCount())
			if (callbacks == 1) assertIs<ReaderPageRelocationReservationResult.Reserved>(queue.reserve(2))
		}
		runReaderQueueWorkers(1) {
			assertIs<ReaderPageRelocationReservationResult.Reserved>(queue.reserve(1))
		}
		assertEquals(2, callbacks)
		assertEquals(2, queue.reservedCount())
	}

	@Test
	fun throwingCallbackRetainsMutationAndUnlocksForAnotherThread() {
		val failure = CallbackFailure()
		var throwOnMutation = true
		val queue = ReaderPageRelocationQueue {
			if (throwOnMutation) throw failure
		}
		assertTrue(runCatching { queue.reserve(1) }.exceptionOrNull() === failure)
		throwOnMutation = false
		runReaderQueueWorkers(1) {
			assertEquals(1, queue.reservedCount())
			assertEquals(1, queue.cancelAll().reservations.size)
			val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(queue.reserve(2)).reservation
			assertTrue(queue.release(reservation))
		}
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun contendingReservationsNeverExceedCapacity() {
		val queue = ReaderPageRelocationQueue(capacity = 7)
		val accepted = Array(4) { mutableListOf<ReaderPageRelocationReservation>() }
		runReaderQueueWorkers { worker ->
			repeat(100) { iteration ->
				when (val result = queue.reserve((worker * 100 + iteration + 1).toLong())) {
					is ReaderPageRelocationReservationResult.Reserved -> accepted[worker] += result.reservation
					is ReaderPageRelocationReservationResult.CapacityReached -> assertEquals(7, result.occupied)
					is ReaderPageRelocationReservationResult.DuplicateGesture -> error("unexpected_duplicate_gesture")
				}
			}
		}
		val reservations = accepted.flatMap { it }
		assertEquals(7, reservations.size)
		assertEquals(7, reservations.map { it.id }.distinct().size)
		assertEquals(ReaderPageRelocationOwnershipSnapshot(7, 0, 7, 7), queue.ownershipSnapshot())
		assertEquals(7, queue.cancelAll().reservations.size)
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun duplicateGestureRaceIssuesOnlyOneReservation() {
		val queue = ReaderPageRelocationQueue()
		val accepted = BooleanArray(4)
		runReaderQueueWorkers { worker ->
			when (val result = queue.reserve(1)) {
				is ReaderPageRelocationReservationResult.Reserved -> accepted[worker] = true
				is ReaderPageRelocationReservationResult.DuplicateGesture ->
					assertEquals(ReaderPageRelocationGestureOccupancy.Reserved, result.occupancy)
				else -> error("unexpected_capacity_rejection")
			}
		}
		assertEquals(1, accepted.count { it })
		assertEquals(1, queue.occupiedCount())
	}

	@Test
	fun transferDispatchAckAndHandoffEachHaveOnlyOneWinner() {
		val queue = ReaderPageRelocationQueue(capacity = 2)
		val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(queue.reserve(1)).reservation
		val retained = assertIs<ReaderPageRelocationReservationResult.Reserved>(queue.reserve(2)).reservation
		val transfers = BooleanArray(4)
		runReaderQueueWorkers { worker ->
			transfers[worker] = queue.enqueueReserved(reservation, 10, 20, 0, 1,
				ReaderPageTurnDirection.Next, "test-session") is ReaderPageRelocationTransferResult.Enqueued
		}
		assertEquals(1, transfers.count { it })
		assertEquals(ReaderPageRelocationOwnershipSnapshot(1, 1, 2, 2), queue.ownershipSnapshot())
		assertFalse(queue.release(reservation))
		val dispatched = BooleanArray(4)
		runReaderQueueWorkers { worker -> dispatched[worker] = queue.commandToDispatch() != null }
		assertEquals(1, dispatched.count { it })
		val request = checkNotNull(queue.head())
		val acknowledged = BooleanArray(4)
		runReaderQueueWorkers { worker ->
			acknowledged[worker] = queue.acknowledge(request.token.value, 1, "test-session", 10, 20)
		}
		assertEquals(1, acknowledged.count { it })
		assertEquals(2, queue.occupiedCount())
		assertFalse(queue.cancelTransferred(request.token.value))
		val completed = BooleanArray(4)
		runReaderQueueWorkers { worker -> completed[worker] = queue.completeHandoff(request.token.value) }
		assertEquals(1, completed.count { it })
		assertEquals(ReaderPageRelocationOwnershipSnapshot(1, 0, 1, 2), queue.ownershipSnapshot())
		assertTrue(queue.release(retained))
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun parallelReserveReleaseKeepsCallbacksAndSnapshotsInsideMonitor() {
		lateinit var queue: ReaderPageRelocationQueue
		var callbacks = 0
		queue = ReaderPageRelocationQueue(capacity = 4) {
			callbacks += 1
			val snapshot = queue.ownershipSnapshot()
			assertEquals(snapshot.reserved + snapshot.queued, snapshot.occupied)
			assertTrue(snapshot.occupied in 0..snapshot.capacity)
		}
		runReaderQueueWorkers { worker ->
			repeat(500) { iteration ->
				val gesture = (worker * 500 + iteration + 1).toLong()
				val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(queue.reserve(gesture)).reservation
				assertTrue(queue.release(reservation))
			}
		}
		assertEquals(4_000, callbacks)
		assertEquals(0, queue.occupiedCount())
	}

	private class CallbackFailure : RuntimeException()
}
