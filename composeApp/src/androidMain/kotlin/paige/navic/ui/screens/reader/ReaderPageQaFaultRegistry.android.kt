package paige.navic.ui.screens.reader

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class ReaderPageQaFault {
	FailNextPersistence,
	PauseNextPublication,
	MissNextRasterLoad,
	ForceRepairWithoutPreparedDeck,
	DeferContentNotReady,
	DeferLayoutUnstable,
	DeferPaginationNotReady,
	DeferWebViewDetached,
	DeferReaderPaused,
	DelayNextVisualStateCallback,
	DelayNextRelocationAcknowledgement
}

enum class ReaderPageQaFaultState {
	Enqueued,
	Consumed,
	Applied,
	Released,
	Cleared
}

private val readerPageQaRequestIdPattern = Regex("[A-Za-z0-9_-]{1,64}")

internal fun isReaderPageQaRequestId(value: String): Boolean =
	readerPageQaRequestIdPattern.matches(value) &&
		!value.equals("none", ignoreCase = true)

data class ReaderPageQaFaultTicket(
	val requestId: String,
	val fault: ReaderPageQaFault
) {
	init {
		require(isReaderPageQaRequestId(requestId))
	}
}

data class ReaderPageQaFaultOperationContext(
	val publicationEpoch: Long? = null,
	val persistenceAttemptId: Long? = null,
	val rasterRequestEpoch: Long? = null,
	val repairAttemptId: Long? = null,
	val preparationAttemptId: Long? = null,
	val relocationToken: String? = null,
	val handoffAttemptId: Long? = null
) {
	init {
		listOfNotNull(
			publicationEpoch,
			persistenceAttemptId,
			rasterRequestEpoch,
			repairAttemptId,
			preparationAttemptId,
			handoffAttemptId
		).forEach { require(it >= 0L) }
		relocationToken?.let {
			require(it.matches(Regex("[A-Za-z0-9_-]{1,64}")))
		}
	}
}

data class ReaderPageQaAppliedFault(
	val ticket: ReaderPageQaFaultTicket,
	val context: ReaderPageQaFaultOperationContext
) {
	fun correlation(
		relation: ReaderPageQaFaultRelation =
			ReaderPageQaFaultRelation.AppliedOperation
	): ReaderPageQaFaultCorrelation = ReaderPageQaFaultCorrelation(
		requestId = ticket.requestId,
		appliedOperation = context,
		relation = relation
	)
}

enum class ReaderPageQaFaultRelation {
	AppliedOperation,
	Retry,
	Recovery
}

data class ReaderPageQaFaultCorrelation(
	val requestId: String,
	val appliedOperation: ReaderPageQaFaultOperationContext,
	val relation: ReaderPageQaFaultRelation
) {
	init {
		require(isReaderPageQaRequestId(requestId))
	}

	fun withRelation(relation: ReaderPageQaFaultRelation): ReaderPageQaFaultCorrelation =
		copy(relation = relation)
}

internal class ReaderPageRelocationQaFaultCorrelationStore {
	private val correlations = mutableMapOf<String, ReaderPageQaFaultCorrelation>()

	operator fun get(relocationToken: String): ReaderPageQaFaultCorrelation? =
		correlations[relocationToken]

	operator fun set(
		relocationToken: String,
		correlation: ReaderPageQaFaultCorrelation
	) {
		correlations[relocationToken] = correlation
	}

	fun remove(relocationToken: String): ReaderPageQaFaultCorrelation? =
		correlations.remove(relocationToken)

	fun transfer(
		originalToken: String,
		replacementToken: String
	): ReaderPageQaFaultCorrelation? {
		require(originalToken != replacementToken)
		check(replacementToken !in correlations)
		val original = correlations.remove(originalToken) ?: return null
		return original.withRelation(ReaderPageQaFaultRelation.Recovery).also {
			correlations[replacementToken] = it
		}
	}

	fun clear() = correlations.clear()
}

data class ReaderPageQaFaultEvent(
	val ticket: ReaderPageQaFaultTicket,
	val seam: String,
	val state: ReaderPageQaFaultState,
	val operation: ReaderPageQaFaultOperationContext? = null,
	val releaseRequestId: String? = null,
	val result: String
)

fun interface ReaderPageQaFaultEventSink {
	fun emit(event: ReaderPageQaFaultEvent)
}

class ReaderPageQaFaultRegistry(
	private val eventSink: ReaderPageQaFaultEventSink =
		ReaderPageQaFaultEventSink { },
	private val onOwnershipMutated: () -> Unit = {}
) {
	private companion object {
		const val CALLBACK_SLOT_LIMIT = 3
		const val QUEUED_FAULT_LIMIT = 16
	}

	private val queued = ArrayDeque<ReaderPageQaFaultTicket>()
	private var pausedPublication: CompletableDeferred<Unit>? = null
	private var pausedPublicationFault: ReaderPageQaAppliedFault? = null
	private var pausedRelocationAck:
		((ReaderPageQaAppliedFault) -> Unit)? = null
	private var pausedRelocationFault: ReaderPageQaAppliedFault? = null

	private data class DelayedVisualState(
		val applied: ReaderPageQaAppliedFault,
		val registration: ReaderWebViewVisualDeliveryCell,
		val postPhysical: (ReaderWebViewVisualDeliveryCell) -> Unit
	)

	private var delayedVisualState: DelayedVisualState? = null
	private var closed = false

	val pendingCallbackLimit: Int
		get() = CALLBACK_SLOT_LIMIT

	val queuedFaultLimit: Int
		get() = QUEUED_FAULT_LIMIT

	@Synchronized
	fun queuedFaultCount(): Int = queued.size

	@Synchronized
	internal fun hasQueued(fault: ReaderPageQaFault): Boolean =
		!closed && queued.any { ticket -> ticket.fault == fault }

	@Synchronized
	fun enqueue(requestId: String, fault: ReaderPageQaFault): Boolean =
		enqueueTicket(
			ticket = ReaderPageQaFaultTicket(requestId, fault),
			emitEnqueued = true
		)

	@Synchronized
	internal fun restoreQueued(ticket: ReaderPageQaFaultTicket): Boolean =
		enqueueTicket(ticket = ticket, emitEnqueued = false)

	private fun enqueueTicket(
		ticket: ReaderPageQaFaultTicket,
		emitEnqueued: Boolean
	): Boolean {
		if (closed ||
			queued.size == QUEUED_FAULT_LIMIT ||
			queued.any { queuedTicket ->
				queuedTicket.requestId == ticket.requestId
			}
		) {
			return false
		}
		queued.addLast(ticket)
		if (emitEnqueued) {
			emit(ticket, "queue", ReaderPageQaFaultState.Enqueued, "accepted")
		}
		return true
	}

	@Synchronized
	internal fun takeQueued(
		requestIds: Set<String>
	): List<ReaderPageQaFaultTicket> {
		if (closed || requestIds.isEmpty()) return emptyList()
		val transferred = queued.filter { ticket ->
			ticket.requestId in requestIds
		}
		queued.removeAll(transferred.toSet())
		return transferred
	}

	fun enqueue(fault: ReaderPageQaFault): Boolean =
		enqueue("test-${fault.name}-${queuedFaultCount()}", fault)

	@Synchronized
	internal fun consumeAndApply(
		fault: ReaderPageQaFault,
		context: ReaderPageQaFaultOperationContext
	): ReaderPageQaAppliedFault? {
		if (closed) return null
		check(contextMatches(fault, context)) {
			"Fault operation context does not match $fault"
		}
		val index = queued.indexOfFirst { it.fault == fault }
		if (index < 0) return null
		val ticket = queued.removeAt(index)
		val seam = seamFor(fault)
		emit(ticket, seam, ReaderPageQaFaultState.Consumed, "matched")
		val applied = ReaderPageQaAppliedFault(ticket, context)
		emit(
			ticket = ticket,
			seam = seam,
			state = ReaderPageQaFaultState.Applied,
			result = "fault-applied",
			operation = context
		)
		return applied
	}

	private fun contextMatches(
		fault: ReaderPageQaFault,
		context: ReaderPageQaFaultOperationContext
	): Boolean {
		val present = buildSet {
			if (context.publicationEpoch != null) add("publicationEpoch")
			if (context.persistenceAttemptId != null) add("persistenceAttemptId")
			if (context.rasterRequestEpoch != null) add("rasterRequestEpoch")
			if (context.repairAttemptId != null) add("repairAttemptId")
			if (context.preparationAttemptId != null) add("preparationAttemptId")
			if (context.relocationToken != null) add("relocationToken")
			if (context.handoffAttemptId != null) add("handoffAttemptId")
		}
		val expected = when (fault) {
			ReaderPageQaFault.FailNextPersistence ->
				setOf("publicationEpoch", "persistenceAttemptId")
			ReaderPageQaFault.PauseNextPublication ->
				setOf("publicationEpoch")
			ReaderPageQaFault.MissNextRasterLoad ->
				setOf("rasterRequestEpoch")
			ReaderPageQaFault.ForceRepairWithoutPreparedDeck ->
				setOf("repairAttemptId")
			ReaderPageQaFault.DeferContentNotReady,
			ReaderPageQaFault.DeferLayoutUnstable,
			ReaderPageQaFault.DeferPaginationNotReady,
			ReaderPageQaFault.DeferWebViewDetached,
			ReaderPageQaFault.DeferReaderPaused ->
				setOf("preparationAttemptId")
			ReaderPageQaFault.DelayNextVisualStateCallback ->
				setOf("relocationToken", "handoffAttemptId")
			ReaderPageQaFault.DelayNextRelocationAcknowledgement ->
				setOf("relocationToken")
		}
		return present == expected
	}

	private fun seamFor(fault: ReaderPageQaFault): String = when (fault) {
		ReaderPageQaFault.FailNextPersistence -> "persistence"
		ReaderPageQaFault.PauseNextPublication -> "publication-worker"
		ReaderPageQaFault.MissNextRasterLoad -> "raster-resolver"
		ReaderPageQaFault.ForceRepairWithoutPreparedDeck -> "repair-role"
		ReaderPageQaFault.DeferContentNotReady,
		ReaderPageQaFault.DeferLayoutUnstable,
		ReaderPageQaFault.DeferPaginationNotReady,
		ReaderPageQaFault.DeferWebViewDetached,
		ReaderPageQaFault.DeferReaderPaused -> "deferred-retry"
		ReaderPageQaFault.DelayNextVisualStateCallback -> "visual-state"
		ReaderPageQaFault.DelayNextRelocationAcknowledgement -> "relocation-ack"
	}

	private fun emit(
		ticket: ReaderPageQaFaultTicket,
		seam: String,
		state: ReaderPageQaFaultState,
		result: String,
		operation: ReaderPageQaFaultOperationContext? = null,
		releaseRequestId: String? = null
	) {
		eventSink.emit(
			ReaderPageQaFaultEvent(
				ticket = ticket,
				seam = seam,
				state = state,
				operation = operation,
				releaseRequestId = releaseRequestId,
				result = result
			)
		)
	}

	suspend fun pausePublicationWithinWorker(
		publicationEpoch: Long
	): ReaderPageQaAppliedFault? {
		val owner = synchronized(this) {
			if (closed || pausedPublication != null) {
				null
			} else {
				val applied = consumeAndApply(
					ReaderPageQaFault.PauseNextPublication,
					ReaderPageQaFaultOperationContext(
						publicationEpoch = publicationEpoch
					)
				)
				if (applied == null) {
					null
				} else {
					val created = CompletableDeferred<Unit>()
					pausedPublication = created
					pausedPublicationFault = applied
					applied to created
				}
			}
		} ?: return null
		onOwnershipMutated()
		val (applied, gate) = owner
		try {
			withContext(NonCancellable) {
				gate.await()
			}
		} finally {
			val detached = synchronized(this) {
				if (pausedPublication === gate) {
					pausedPublication = null
					pausedPublicationFault = null
					true
				} else {
					false
				}
			}
			if (detached) {
				onOwnershipMutated()
				gate.complete(Unit)
			}
		}
		return applied
	}

	fun releasePublication(releaseRequestId: String): Boolean {
		require(isReaderPageQaRequestId(releaseRequestId))
		val released = synchronized(this) {
			val gate = pausedPublication ?: return@synchronized null
			val applied = checkNotNull(pausedPublicationFault)
			pausedPublication = null
			pausedPublicationFault = null
			applied to gate
		} ?: return false
		onOwnershipMutated()
		emit(
			ticket = released.first.ticket,
			seam = "publication-worker",
			state = ReaderPageQaFaultState.Released,
			result = "command-release",
			operation = released.first.context,
			releaseRequestId = releaseRequestId
		)
		released.second.complete(Unit)
		return true
	}

	fun pauseRelocationAck(
		relocationToken: String,
		completion: (ReaderPageQaAppliedFault) -> Unit
	): Boolean = pauseRelocationAck(
		relocationToken = relocationToken,
		onAdmitted = {},
		completion = completion
	)

	fun pauseRelocationAck(
		relocationToken: String,
		onAdmitted: (ReaderPageQaAppliedFault) -> Unit,
		completion: (ReaderPageQaAppliedFault) -> Unit
	): Boolean {
		val applied = synchronized(this) {
			if (pausedRelocationAck != null) return@synchronized null
			val owner = consumeAndApply(
				ReaderPageQaFault.DelayNextRelocationAcknowledgement,
				ReaderPageQaFaultOperationContext(
					relocationToken = relocationToken
				)
			) ?: return@synchronized null
			pausedRelocationAck = completion
			pausedRelocationFault = owner
			owner
		} ?: return false
		try {
			onAdmitted(applied)
		} finally {
			onOwnershipMutated()
		}
		return true
	}

	fun releaseRelocationAck(releaseRequestId: String): Boolean {
		require(isReaderPageQaRequestId(releaseRequestId))
		val released = synchronized(this) {
			val callback = pausedRelocationAck ?: return@synchronized null
			val applied = checkNotNull(pausedRelocationFault)
			pausedRelocationAck = null
			pausedRelocationFault = null
			applied to callback
		} ?: return false
		onOwnershipMutated()
		emit(
			ticket = released.first.ticket,
			seam = "relocation-ack",
			state = ReaderPageQaFaultState.Released,
			result = "command-release",
			operation = released.first.context,
			releaseRequestId = releaseRequestId
		)
		invokeOwnedCallback { released.second(released.first) }
		return true
	}

	internal fun delayVisualState(
		relocationToken: String,
		handoffAttemptId: Long,
		registration: ReaderWebViewVisualDeliveryCell,
		postPhysical: (ReaderWebViewVisualDeliveryCell) -> Unit
	): ReaderPageQaAppliedFault? {
		val applied = synchronized(this) {
			if (closed || delayedVisualState != null) {
				return@synchronized null
			}
			val owner = consumeAndApply(
				ReaderPageQaFault.DelayNextVisualStateCallback,
				ReaderPageQaFaultOperationContext(
					relocationToken = relocationToken,
					handoffAttemptId = handoffAttemptId
				)
			) ?: return@synchronized null
			check(registration.transferToQa()) {
				"Visual-state callback was not transferable"
			}
			delayedVisualState = DelayedVisualState(
				applied = owner,
				registration = registration,
				postPhysical = postPhysical
			)
			owner
		} ?: return null
		onOwnershipMutated()
		return applied
	}

	fun releaseVisualState(releaseRequestId: String): Boolean {
		require(isReaderPageQaRequestId(releaseRequestId))
		val released = synchronized(this) {
			val owner = delayedVisualState ?: return@synchronized null
			check(owner.registration.returnFromQaToPhysical()) {
				"QA visual-state owner could not return to physical delivery"
			}
			delayedVisualState = null
			owner
		} ?: return false
		onOwnershipMutated()
		emit(
			ticket = released.applied.ticket,
			seam = "visual-state",
			state = ReaderPageQaFaultState.Released,
			result = "command-release",
			operation = released.applied.context,
			releaseRequestId = releaseRequestId
		)
		try {
			released.postPhysical(released.registration)
		} catch (_: Throwable) {
			released.registration.abandonPhysicalOwnership()
			logIsolatedCallbackFailure()
		}
		return true
	}

	@Synchronized
	fun pendingCallbackCount(): Int =
		listOfNotNull(
			pausedPublication,
			pausedRelocationAck,
			delayedVisualState
		).size

	private data class DrainedFaultOwners(
		val queued: List<ReaderPageQaFaultTicket>,
		val publication:
			Pair<ReaderPageQaAppliedFault, CompletableDeferred<Unit>>?,
		val callbacks: List<Pair<
			ReaderPageQaAppliedFault,
			(ReaderPageQaAppliedFault) -> Unit
		>>,
		val visualStates: List<DelayedVisualState>
	) {
		val ownershipChanged: Boolean
			get() = publication != null ||
				callbacks.isNotEmpty() || visualStates.isNotEmpty()
	}

	fun clear(commandRequestId: String) {
		require(isReaderPageQaRequestId(commandRequestId))
		val owners = drainOwners(close = false)
		if (owners.ownershipChanged) onOwnershipMutated()
		releaseDrainedOwners(
			owners = owners,
			commandRequestId = commandRequestId,
			result = "command-clear",
			invokeRelocationCallbacks = true
		)
	}

	fun closeAndDrain() {
		val owners = drainOwners(close = true)
		if (owners.ownershipChanged) onOwnershipMutated()
		releaseDrainedOwners(
			owners = owners,
			commandRequestId = null,
			result = "host-closed-discarded",
			invokeRelocationCallbacks = false
		)
	}

	private fun drainOwners(close: Boolean): DrainedFaultOwners =
		synchronized(this) {
			if (close) closed = true
			DrainedFaultOwners(
				queued = queued.toList(),
				publication = pausedPublication?.let { gate ->
					checkNotNull(pausedPublicationFault) to gate
				},
				callbacks = pausedRelocationAck?.let { callback ->
					listOf(checkNotNull(pausedRelocationFault) to callback)
				}.orEmpty(),
				visualStates = listOfNotNull(delayedVisualState)
			).also {
				queued.clear()
				pausedPublication = null
				pausedPublicationFault = null
				pausedRelocationAck = null
				pausedRelocationFault = null
				delayedVisualState = null
			}
		}

	private fun releaseDrainedOwners(
		owners: DrainedFaultOwners,
		commandRequestId: String?,
		result: String,
		invokeRelocationCallbacks: Boolean
	) {
		owners.queued.forEach { ticket ->
			emit(
				ticket = ticket,
				seam = "queue",
				state = ReaderPageQaFaultState.Cleared,
				result = result,
				releaseRequestId = commandRequestId
			)
		}
		owners.publication?.let { (applied, gate) ->
			emit(
				ticket = applied.ticket,
				seam = "publication-worker",
				state = ReaderPageQaFaultState.Cleared,
				result = result,
				operation = applied.context,
				releaseRequestId = commandRequestId
			)
			gate.complete(Unit)
		}
		owners.callbacks.forEach { (applied, callback) ->
			emit(
				ticket = applied.ticket,
				seam = "relocation-ack",
				state = ReaderPageQaFaultState.Cleared,
				result = result,
				operation = applied.context,
				releaseRequestId = commandRequestId
			)
			if (invokeRelocationCallbacks) {
				invokeOwnedCallback { callback(applied) }
			}
		}
		owners.visualStates.forEach { owner ->
			emit(
				ticket = owner.applied.ticket,
				seam = "visual-state",
				state = ReaderPageQaFaultState.Cleared,
				result = result,
				operation = owner.applied.context,
				releaseRequestId = commandRequestId
			)
			owner.registration.abandonQaOwnership()
		}
	}

	@Synchronized
	fun isClosed(): Boolean = closed

	private fun invokeOwnedCallback(callback: () -> Unit) {
		try {
			callback()
		} catch (_: Throwable) {
			logIsolatedCallbackFailure()
		}
	}

	private fun logIsolatedCallbackFailure() {
		try {
			Log.e(
				"ReaderPageQaFault",
				"isolatedCallbackFailure=true"
			)
		} catch (_: Throwable) {
			// Host tests and shutdown must not depend on android.util.Log.
		}
	}
}

object ReaderPageQaFaultControl {
	class Registration internal constructor(
		internal val id: Long,
		internal val registry: ReaderPageQaFaultRegistry
	)

	private data class TrackedRequest(
		val ticket: ReaderPageQaFaultTicket,
		var registrationId: Long? = null,
		var enqueuedEventEmitted: Boolean = false,
		var completed: Boolean = false
	)

	private const val PENDING_FAULT_LIMIT = 16
	private const val TRACKED_REQUEST_LIMIT = 128
	private var nextRegistrationId = 1L
	private var nextPendingRequestId = 1L
	private var active: Registration? = null
	private val pending = ArrayDeque<TrackedRequest>()
	private val trackedRequests = linkedMapOf<String, TrackedRequest>()

	@Synchronized
	fun attach(
		registry: ReaderPageQaFaultRegistry
	): Registration {
		check(!registry.isClosed()) { "Cannot attach a closed fault registry" }
		active?.let(::reclaimUnconsumed)
		return Registration(nextRegistrationId++, registry).also { created ->
			active = created
			transferPendingTo(created)
		}
	}

	@Synchronized
	fun detach(registration: Registration) {
		if (active === registration) {
			reclaimUnconsumed(registration)
			active = null
		}
	}

	@Synchronized
	fun enqueue(requestId: String, fault: ReaderPageQaFault): Boolean {
		val ticket = ReaderPageQaFaultTicket(requestId, fault)
		if (requestId in trackedRequests ||
			trackedRequests.size >= TRACKED_REQUEST_LIMIT
		) {
			return false
		}
		val registration = active
		if (registration != null) {
			if (!registration.registry.enqueue(requestId, fault)) return false
			trackedRequests[requestId] = TrackedRequest(
				ticket = ticket,
				registrationId = registration.id,
				enqueuedEventEmitted = true
			)
			return true
		}
		if (pending.size >= PENDING_FAULT_LIMIT) return false
		TrackedRequest(ticket).also { tracked ->
			trackedRequests[requestId] = tracked
			pending.addLast(tracked)
		}
		return true
	}

	@Synchronized
	fun enqueue(fault: ReaderPageQaFault): Boolean = enqueue(
		requestId = "prearmed-${fault.name}-${nextPendingRequestId++}",
		fault = fault
	)

	private fun reclaimUnconsumed(registration: Registration) {
		val owned = trackedRequests.values.filter { tracked ->
			!tracked.completed && tracked.registrationId == registration.id
		}
		if (owned.isEmpty()) return
		val transferred = registration.registry.takeQueued(
			owned.mapTo(mutableSetOf()) { tracked ->
				tracked.ticket.requestId
			}
		).associateBy { ticket -> ticket.requestId }
		check(pending.size + transferred.size <= PENDING_FAULT_LIMIT) {
			"Reclaimed reader QA faults exceed pending capacity"
		}
		owned.forEach { tracked ->
			tracked.registrationId = null
			if (transferred.containsKey(tracked.ticket.requestId)) {
				pending.addLast(tracked)
			} else {
				tracked.completed = true
			}
		}
	}

	private fun transferPendingTo(registration: Registration) {
		while (pending.isNotEmpty()) {
			val tracked = pending.removeFirst()
			check(!tracked.completed && tracked.registrationId == null) {
				"Only unconsumed reader QA faults can transfer"
			}
			val accepted = if (tracked.enqueuedEventEmitted) {
				registration.registry.restoreQueued(tracked.ticket)
			} else {
				registration.registry.enqueue(
					tracked.ticket.requestId,
					tracked.ticket.fault
				)
			}
			check(accepted) { "Prearmed reader QA fault could not transfer" }
			tracked.registrationId = registration.id
			tracked.enqueuedEventEmitted = true
		}
	}

	@Synchronized
	fun releasePublication(requestId: String): Boolean =
		active?.registry?.releasePublication(requestId) == true

	@Synchronized
	fun releaseRelocationAck(requestId: String): Boolean =
		active?.registry?.releaseRelocationAck(requestId) == true

	@Synchronized
	fun releaseVisualState(requestId: String): Boolean =
		active?.registry?.releaseVisualState(requestId) == true

	@Synchronized
	fun clear(requestId: String): Boolean {
		require(isReaderPageQaRequestId(requestId))
		val registration = active
		val hadPending = pending.isNotEmpty()
		pending.forEach { tracked -> tracked.completed = true }
		pending.clear()
		if (registration != null) {
			registration.registry.clear(requestId)
			trackedRequests.values.forEach { tracked ->
				if (tracked.registrationId == registration.id) {
					tracked.registrationId = null
					tracked.completed = true
				}
			}
		}
		return registration != null || hadPending
	}
}
