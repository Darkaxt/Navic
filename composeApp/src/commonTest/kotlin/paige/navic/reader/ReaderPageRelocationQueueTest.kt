package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageRelocationQueueTest {
	@Test
	fun laterSettlementAppendsWhileHeadWaitsForVisualHandoff() {
		val queue = ReaderPageRelocationQueue()
		val first = enqueue(queue, gestureId = 1L, source = 3, destination = 4)
		val second = enqueue(queue, gestureId = 2L, source = 4, destination = 5)

		assertEquals(first, queue.commandToDispatch())
		assertTrue(queue.acknowledge(first.token.value, 4, "session-a", 10L, 20L))
		assertNull(queue.commandToDispatch())
		assertTrue(queue.completeHandoff(first.token.value))
		assertEquals(second, queue.commandToDispatch())
	}

	@Test
	fun staleTokenOrdinalOrSessionCannotCompleteCurrentHead() {
		val queue = ReaderPageRelocationQueue()
		val current = enqueue(queue, gestureId = 1L, source = 3, destination = 4)
		assertEquals(current, queue.commandToDispatch())

		assertFalse(queue.acknowledge("old-token", 4, "session-a", 10L, 20L))
		assertFalse(queue.acknowledge(current.token.value, 5, "session-a", 10L, 20L))
		assertFalse(queue.acknowledge(current.token.value, 4, "session-b", 10L, 20L))
		assertFalse(queue.acknowledge(current.token.value, 4, "session-a", 9L, 20L))
		assertFalse(queue.acknowledge(current.token.value, 4, "session-a", 10L, 19L))
		assertEquals(current, queue.head())
	}

	@Test
	fun acknowledgedHeadNoLongerMatchesASecondBridgeAck() {
		val queue = ReaderPageRelocationQueue()
		val current = enqueue(queue, gestureId = 1L, source = 3, destination = 4)
		assertEquals(current, queue.commandToDispatch())
		assertTrue(queue.matchesDispatchedHead(current.token.value, 10L, 20L, "session-a", 4))
		assertTrue(queue.acknowledge(current.token.value, 4, "session-a", 10L, 20L))

		assertTrue(queue.matchesAcknowledgedHead(current.token.value, 10L, 20L, "session-a", 4))
		assertFalse(queue.matchesDispatchedHead(current.token.value, 10L, 20L, "session-a", 4))
		assertTrue(queue.matchesHead(current.token.value, 10L, 20L, "session-a", 4))
	}

	@Test
	fun inFlightHeadRemainsOwnedUntilVisualHandoffCompletes() {
		val queue = ReaderPageRelocationQueue()
		val current = enqueue(queue, gestureId = 1L, source = 3, destination = 4)
		assertFalse(queue.hasDispatchedHead())
		assertFalse(queue.hasInFlightHead())

		assertEquals(current, queue.commandToDispatch())
		assertTrue(queue.hasDispatchedHead())
		assertTrue(queue.hasInFlightHead())
		assertTrue(queue.acknowledge(current.token.value, 4, "session-a", 10L, 20L))
		assertFalse(queue.hasDispatchedHead())
		assertTrue(queue.hasInFlightHead())

		assertTrue(queue.completeHandoff(current.token.value))
		assertFalse(queue.hasDispatchedHead())
		assertFalse(queue.hasInFlightHead())
	}

	@Test
	fun capacityReservationRejectsWithoutAdvancingIds() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val first = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 1L)
		).reservation

		assertEquals(
			ReaderPageRelocationReservationResult.CapacityReached(1, 1),
			queue.reserve(gestureId = 2L)
		)
		assertTrue(queue.release(first))
		assertFalse(queue.release(first))
		val second = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 2L)
		).reservation

		assertEquals(1L, first.id.value)
		assertEquals(2L, second.id.value)
	}

	@Test
	fun duplicateGestureIsTypedAndDoesNotMutateOrAdvanceIds() {
		val queue = ReaderPageRelocationQueue(capacity = 2)
		val first = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 4L)
		).reservation
		assertEquals(
			ReaderPageRelocationReservationResult.DuplicateGesture(
				4L,
				ReaderPageRelocationGestureOccupancy.Reserved
			),
			queue.reserve(gestureId = 4L)
		)
		val queued = assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				first,
				10L,
				20L,
				3,
				4,
				ReaderPageTurnDirection.Next,
				"session-a"
			)
		).request
		assertEquals(
			ReaderPageRelocationReservationResult.DuplicateGesture(
				4L,
				ReaderPageRelocationGestureOccupancy.Queued
			),
			queue.reserve(gestureId = 4L)
		)
		assertTrue(queue.cancelTransferred(queued.token.value))
		val second = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 5L)
		).reservation
		assertEquals(2L, second.id.value)
	}

	@Test
	fun forgedOrCancelledReservationCannotAllocateAToken() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val issued = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 7L)
		).reservation
		val forged = ReaderPageRelocationReservation(issued.id, issued.gestureId)

		assertEquals(
			ReaderPageRelocationTransferResult.ReservationNotOwned,
			queue.enqueueReserved(
				forged,
				10L,
				20L,
				3,
				4,
				ReaderPageTurnDirection.Next,
				"session-a"
			)
		)
		assertTrue(queue.release(issued))
		assertEquals(
			ReaderPageRelocationTransferResult.ReservationNotOwned,
			queue.enqueueReserved(
				issued,
				10L,
				20L,
				3,
				4,
				ReaderPageTurnDirection.Next,
				"session-a"
			)
		)
		val next = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 8L)
		).reservation
		val request = assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				next,
				10L,
				20L,
				4,
				5,
				ReaderPageTurnDirection.Next,
				"session-a"
			)
		).request
		assertEquals("page-turn-1", request.token.value)
	}

	@Test
	fun commitTransfersReservationWithoutChangingOccupiedCapacity() {
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 7L)
		).reservation
		assertEquals(1, queue.reservedCount())
		assertEquals(0, queue.queuedCount())

		val request = assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				reservation = reservation,
				rasterGeneration = 10L,
				textureGeneration = 20L,
				sourceOrdinal = 3,
				destinationOrdinal = 4,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "session-a"
			)
		).request

		assertEquals(0, queue.reservedCount())
		assertEquals(1, queue.queuedCount())
		assertEquals(1, queue.occupiedCount())
		assertEquals(request, queue.commandToDispatch())
		assertTrue(queue.acknowledge(request.token.value, 4, "session-a", 10L, 20L))
		assertTrue(queue.completeHandoff(request.token.value))
		assertEquals(0, queue.occupiedCount())
	}

	@Test
	fun ownershipSizeAndCapacityFollowReservationsAndQueuedTokens() {
		val queue = ReaderPageRelocationQueue(capacity = 2)
		assertEquals(0, queue.size())
		assertEquals(2, queue.capacity)
		assertTrue(queue.hasCapacity())

		val first = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 1L)
		).reservation
		assertEquals(1, queue.size())
		assertTrue(queue.hasCapacity())
		assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				reservation = first,
				rasterGeneration = 10L,
				textureGeneration = 20L,
				sourceOrdinal = 3,
				destinationOrdinal = 4,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "session-a"
			)
		)
		val second = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 2L)
		).reservation

		assertEquals(2, queue.size())
		assertFalse(queue.hasCapacity())
		assertTrue(queue.release(second))
		assertEquals(1, queue.size())
		assertTrue(queue.hasCapacity())
		queue.cancelAll()
		assertEquals(0, queue.size())
	}

	@Test
	fun cancelAllDrainsQueuedAndUncommittedReservations() {
		val queue = ReaderPageRelocationQueue(capacity = 2)
		val queued = enqueue(queue, gestureId = 1L, source = 3, destination = 4)
		val reserved = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId = 2L)
		).reservation

		val drained = queue.cancelAll()

		assertEquals(listOf(queued), drained.queued)
		assertEquals(listOf(reserved), drained.reservations)
		assertEquals(0, queue.occupiedCount())
		assertFalse(queue.release(reserved))
		assertFalse(queue.completeHandoff(queued.token.value))
	}

	private fun enqueue(
		queue: ReaderPageRelocationQueue,
		gestureId: Long,
		source: Int,
		destination: Int
	): ReaderPageRelocationRequest {
		val reserved = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId)
		)
		return assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				reservation = reserved.reservation,
				rasterGeneration = 10L,
				textureGeneration = 20L,
				sourceOrdinal = source,
				destinationOrdinal = destination,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "session-a"
			)
		).request
	}
}
