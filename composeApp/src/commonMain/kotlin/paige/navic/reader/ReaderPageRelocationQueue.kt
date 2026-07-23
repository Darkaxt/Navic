package paige.navic.reader

@JvmInline
value class ReaderPageRelocationToken(val value: String)

@JvmInline
value class ReaderPageRelocationReservationId(val value: Long)

class ReaderPageRelocationReservation internal constructor(
	val id: ReaderPageRelocationReservationId,
	val gestureId: Long
)

enum class ReaderPageRelocationGestureOccupancy {
	Reserved,
	Queued
}

sealed interface ReaderPageRelocationReservationResult {
	data class Reserved(
		val reservation: ReaderPageRelocationReservation
	) : ReaderPageRelocationReservationResult

	data class CapacityReached(
		val occupied: Int,
		val capacity: Int
	) : ReaderPageRelocationReservationResult

	data class DuplicateGesture(
		val gestureId: Long,
		val occupancy: ReaderPageRelocationGestureOccupancy
	) : ReaderPageRelocationReservationResult
}

sealed interface ReaderPageRelocationTransferResult {
	data class Enqueued(
		val request: ReaderPageRelocationRequest
	) : ReaderPageRelocationTransferResult

	data object ReservationNotOwned : ReaderPageRelocationTransferResult
}

data class ReaderPageRelocationOwnershipSnapshot(
	val reserved: Int,
	val queued: Int,
	val occupied: Int,
	val capacity: Int
)

data class ReaderPageRelocationDrain(
	val queued: List<ReaderPageRelocationRequest>,
	val reservations: List<ReaderPageRelocationReservation>
)

data class ReaderPageRelocationRequest(
	val token: ReaderPageRelocationToken,
	val gestureId: Long,
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val sourceOrdinal: Int,
	val destinationOrdinal: Int,
	val logicalDirection: ReaderPageTurnDirection,
	val foliateSessionId: String
)

class ReaderPageRelocationQueue(
	val capacity: Int = 4,
	private val onOwnershipMutated: () -> Unit = {}
) {
	private val lock = Any()
	private var nextReservationId = 1L
	private var nextToken = 1L
	private val reservations = linkedMapOf<
		ReaderPageRelocationReservationId,
		ReaderPageRelocationReservation
	>()
	private val requests = mutableListOf<ReaderPageRelocationRequest>()
	private var dispatchedToken: ReaderPageRelocationToken? = null
	private var acknowledgedToken: ReaderPageRelocationToken? = null

	init {
		require(capacity > 0)
	}

	fun reserve(gestureId: Long): ReaderPageRelocationReservationResult = synchronized(lock) {
		require(gestureId > 0L)
		if (reservations.values.any { it.gestureId == gestureId }) {
			return@synchronized ReaderPageRelocationReservationResult.DuplicateGesture(
				gestureId,
				ReaderPageRelocationGestureOccupancy.Reserved
			)
		}
		if (requests.any { it.gestureId == gestureId }) {
			return@synchronized ReaderPageRelocationReservationResult.DuplicateGesture(
				gestureId,
				ReaderPageRelocationGestureOccupancy.Queued
			)
		}
		val occupied = reservations.size + requests.size
		if (occupied >= capacity) {
			return@synchronized ReaderPageRelocationReservationResult.CapacityReached(
				occupied,
				capacity
			)
		}
		val reservation = ReaderPageRelocationReservation(
			id = ReaderPageRelocationReservationId(nextReservationId++),
			gestureId = gestureId
		)
		reservations[reservation.id] = reservation
		onOwnershipMutated()
		ReaderPageRelocationReservationResult.Reserved(reservation)
	}

	fun enqueueReserved(
		reservation: ReaderPageRelocationReservation,
		rasterGeneration: Long,
		textureGeneration: Long,
		sourceOrdinal: Int,
		destinationOrdinal: Int,
		logicalDirection: ReaderPageTurnDirection,
		foliateSessionId: String
	): ReaderPageRelocationTransferResult = synchronized(lock) {
		require(rasterGeneration >= 0L)
		require(textureGeneration >= 0L)
		require(sourceOrdinal >= 0)
		require(destinationOrdinal >= 0)
		require(sourceOrdinal != destinationOrdinal)
		require(foliateSessionId.isNotBlank())
		val owned = reservations[reservation.id]
		if (owned !== reservation) {
			return@synchronized ReaderPageRelocationTransferResult.ReservationNotOwned
		}
		check(requests.none { it.gestureId == reservation.gestureId })
		val request = ReaderPageRelocationRequest(
			token = ReaderPageRelocationToken("page-turn-${nextToken++}"),
			gestureId = reservation.gestureId,
			rasterGeneration = rasterGeneration,
			textureGeneration = textureGeneration,
			sourceOrdinal = sourceOrdinal,
			destinationOrdinal = destinationOrdinal,
			logicalDirection = logicalDirection,
			foliateSessionId = foliateSessionId
		)
		check(reservations.remove(reservation.id) === reservation)
		requests += request
		onOwnershipMutated()
		ReaderPageRelocationTransferResult.Enqueued(request)
	}

	fun release(reservation: ReaderPageRelocationReservation): Boolean = synchronized(lock) {
		if (reservations[reservation.id] !== reservation) return@synchronized false
		reservations.remove(reservation.id)
		onOwnershipMutated()
		true
	}

	fun cancelTransferred(token: String): Boolean = synchronized(lock) {
		val index = requests.indexOfFirst { it.token.value == token }
		if (
			index < 0 ||
			dispatchedToken?.value == token ||
			acknowledgedToken?.value == token
		) return@synchronized false
		requests.removeAt(index)
		onOwnershipMutated()
		true
	}

	fun commandToDispatch(): ReaderPageRelocationRequest? = synchronized(lock) {
		val head = requests.firstOrNull() ?: return@synchronized null
		if (dispatchedToken != null || acknowledgedToken != null) return@synchronized null
		dispatchedToken = head.token
		head
	}

	fun acknowledge(
		token: String,
		pageIndex: Int,
		foliateSessionId: String,
		rasterGeneration: Long,
		textureGeneration: Long
	): Boolean = synchronized(lock) {
		val head = requests.firstOrNull() ?: return@synchronized false
		if (
			head.token.value != token ||
			head.destinationOrdinal != pageIndex ||
			head.foliateSessionId != foliateSessionId ||
			head.rasterGeneration != rasterGeneration ||
			head.textureGeneration != textureGeneration ||
			dispatchedToken != head.token
		) return@synchronized false
		dispatchedToken = null
		acknowledgedToken = head.token
		true
	}

	fun matchesDispatchedHead(
		token: String,
		rasterGeneration: Long,
		textureGeneration: Long,
		foliateSessionId: String,
		destinationOrdinal: Int
	): Boolean = synchronized(lock) {
		val head = requests.firstOrNull() ?: return@synchronized false
		dispatchedToken == head.token && matches(
			head,
			token,
			rasterGeneration,
			textureGeneration,
			foliateSessionId,
			destinationOrdinal
		)
	}

	fun matchesAcknowledgedHead(
		token: String,
		rasterGeneration: Long,
		textureGeneration: Long,
		foliateSessionId: String,
		destinationOrdinal: Int
	): Boolean = synchronized(lock) {
		val head = requests.firstOrNull() ?: return@synchronized false
		acknowledgedToken == head.token && matches(
			head,
			token,
			rasterGeneration,
			textureGeneration,
			foliateSessionId,
			destinationOrdinal
		)
	}

	fun matchesHead(
		token: String,
		rasterGeneration: Long,
		textureGeneration: Long,
		foliateSessionId: String,
		destinationOrdinal: Int
	): Boolean = synchronized(lock) {
		requests.firstOrNull()?.let { head ->
			matches(
				head,
				token,
				rasterGeneration,
				textureGeneration,
				foliateSessionId,
				destinationOrdinal
			)
		} ?: false
	}

	private fun matches(
		head: ReaderPageRelocationRequest,
		token: String,
		rasterGeneration: Long,
		textureGeneration: Long,
		foliateSessionId: String,
		destinationOrdinal: Int
	): Boolean = head.token.value == token &&
		head.rasterGeneration == rasterGeneration &&
		head.textureGeneration == textureGeneration &&
		head.foliateSessionId == foliateSessionId &&
		head.destinationOrdinal == destinationOrdinal

	fun completeHandoff(token: String): Boolean = synchronized(lock) {
		val head = requests.firstOrNull() ?: return@synchronized false
		if (head.token.value != token || acknowledgedToken != head.token) {
			return@synchronized false
		}
		requests.removeAt(0)
		acknowledgedToken = null
		onOwnershipMutated()
		true
	}

	fun head(): ReaderPageRelocationRequest? = synchronized(lock) { requests.firstOrNull() }

	fun hasQueuedAfterHead(): Boolean = synchronized(lock) { requests.size > 1 }

	fun ownershipSnapshot(): ReaderPageRelocationOwnershipSnapshot = synchronized(lock) {
		ReaderPageRelocationOwnershipSnapshot(
			reserved = reservations.size,
			queued = requests.size,
			occupied = reservations.size + requests.size,
			capacity = capacity
		)
	}

	fun reservedCount(): Int = ownershipSnapshot().reserved
	fun queuedCount(): Int = ownershipSnapshot().queued
	fun occupiedCount(): Int = ownershipSnapshot().occupied
	fun size(): Int = ownershipSnapshot().occupied
	fun hasCapacity(): Boolean = ownershipSnapshot().occupied < capacity

	fun cancelAll(): ReaderPageRelocationDrain = synchronized(lock) {
		val drain = ReaderPageRelocationDrain(
			queued = requests.toList(),
			reservations = reservations.values.toList()
		)
		if (drain.queued.isEmpty() && drain.reservations.isEmpty()) return@synchronized drain
		requests.clear()
		reservations.clear()
		dispatchedToken = null
		acknowledgedToken = null
		onOwnershipMutated()
		drain
	}
}
