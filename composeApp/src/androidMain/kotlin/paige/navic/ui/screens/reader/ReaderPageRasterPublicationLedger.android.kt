package paige.navic.ui.screens.reader

internal data class ReaderPageRasterPublicationRequest(
	val digest: String,
	val epoch: Long
)

internal enum class ReaderPageRasterPublicationRejection {
	EntryCapacity,
	CallbackCapacity
}

internal sealed interface ReaderPageRasterPublicationRegistration {
	data class Started(
		val request: ReaderPageRasterPublicationRequest
	) : ReaderPageRasterPublicationRegistration

	data class Coalesced(
		val request: ReaderPageRasterPublicationRequest
	) : ReaderPageRasterPublicationRegistration

	data class Rejected(
		val reason: ReaderPageRasterPublicationRejection
	) : ReaderPageRasterPublicationRegistration
}

internal class ReaderPageRasterPublicationLedger<T : Any>(
	val currentEpochEntryLimit: Int,
	private val persistenceWorkerLimit: Int,
	val callbackLimit: Int,
	private val onOwnershipMutated: () -> Unit = {},
	private val release: (T) -> Unit
) {
	constructor(release: (T) -> Unit) : this(
		currentEpochEntryLimit = Int.MAX_VALUE / 2,
		persistenceWorkerLimit = Int.MAX_VALUE / 2,
		callbackLimit = Int.MAX_VALUE,
		release = release
	)

	private enum class ProducerState { Queued, Active }

	private data class Entry<T : Any>(
		val value: T,
		val callbacks: MutableList<(Boolean) -> Unit>,
		var producerState: ProducerState = ProducerState.Queued,
		var stale: Boolean = false
	)

	private data class Completion<T : Any>(
		val value: T,
		val callbacks: List<(Boolean) -> Unit>,
		val accepted: Boolean,
		val persisted: Boolean
	)

	private var epoch = 0L
	private val entries =
		linkedMapOf<ReaderPageRasterPublicationRequest, Entry<T>>()
	private var capacityAvailableListener: (() -> Unit)? = null
	private var capacityRetryPending = false
	private var retainedDispatchFailure: Throwable? = null

	init {
		require(currentEpochEntryLimit > 0)
		require(persistenceWorkerLimit > 0)
		require(callbackLimit >= currentEpochEntryLimit)
	}

	fun setCapacityAvailableListener(listener: () -> Unit) {
		val notify = synchronized(this) {
			check(
				capacityAvailableListener == null ||
					capacityAvailableListener === listener
			) { "Publication capacity listener is already owned" }
			capacityAvailableListener = listener
			capacityRetryPending.also { pending ->
				if (pending) capacityRetryPending = false
			}
		}
		if (notify) dispatchCapacityAvailable(listener)
	}

	fun clearCapacityAvailableListener(listener: () -> Unit) {
		synchronized(this) {
			if (capacityAvailableListener === listener) {
				capacityAvailableListener = null
				capacityRetryPending = false
			}
		}
	}

	val staleActiveDrainLimit: Int
		get() = persistenceWorkerLimit

	val entryLimit: Int
		get() = currentEpochEntryLimit + staleActiveDrainLimit

	fun begin(
		digest: String,
		value: T,
		callback: (Boolean) -> Unit
	): ReaderPageRasterPublicationRegistration {
		var rejectedCallback: ((Boolean) -> Unit)? = null
		var detachedValue: T? = null
		val registration = synchronized(this) {
			val request = ReaderPageRasterPublicationRequest(digest, epoch)
			val existing = entries[request]
			val globalCallbackCapacityReached = callbackCountLocked() >= callbackLimit
			val entryCallbackCapacityReached =
				(existing?.callbacks?.size ?: 0) >= MaximumCallbacksPerEntry
			val result = when {
				existing != null &&
					(entryCallbackCapacityReached || globalCallbackCapacityReached) -> {
					capacityRetryPending = true
					rejectedCallback = callback
					detachedValue = value
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.CallbackCapacity
					)
				}
				existing != null -> {
					existing.callbacks += callback
					detachedValue = value
					ReaderPageRasterPublicationRegistration.Coalesced(request)
				}
				currentEpochEntryCountLocked() >= currentEpochEntryLimit -> {
					capacityRetryPending = true
					rejectedCallback = callback
					detachedValue = value
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.EntryCapacity
					)
				}
				globalCallbackCapacityReached -> {
					capacityRetryPending = true
					rejectedCallback = callback
					detachedValue = value
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.CallbackCapacity
					)
				}
				else -> {
					entries[request] = Entry(value, mutableListOf(callback))
					ReaderPageRasterPublicationRegistration.Started(request)
				}
			}
			if (result !is ReaderPageRasterPublicationRegistration.Rejected) {
				onOwnershipMutated()
			}
			result
		}
		dispatchBestEffort(
			callbacks = listOfNotNull(rejectedCallback),
			callbackResult = false,
			values = listOfNotNull(detachedValue)
		)
		return registration
	}

	@Synchronized
	fun acquireForPersistence(
		request: ReaderPageRasterPublicationRequest
	): T? {
		val entry = entries[request] ?: return null
		if (entry.stale || entry.producerState != ProducerState.Queued) {
			return null
		}
		check(activeWorkerCountLocked() < persistenceWorkerLimit) {
			"Publication scheduler exceeded its configured worker limit"
		}
		entry.producerState = ProducerState.Active
		return entry.value
	}

	@Synchronized
	fun entryCount(): Int = entries.size

	@Synchronized
	fun currentEpochEntryCount(): Int = currentEpochEntryCountLocked()

	private fun currentEpochEntryCountLocked(): Int =
		entries.count { (request, entry) ->
			request.epoch == epoch && !entry.stale
		}

	@Synchronized
	fun staleActiveEntryCount(): Int =
		entries.count { (_, entry) ->
			entry.stale && entry.producerState == ProducerState.Active
		}

	@Synchronized
	fun callbackCount(): Int = callbackCountLocked()

	private fun callbackCountLocked(): Int =
		entries.values.sumOf { entry -> entry.callbacks.size }

	private fun activeWorkerCountLocked(): Int =
		entries.count { (_, entry) ->
			entry.producerState == ProducerState.Active
		}

	@Synchronized
	fun currentEpoch(): Long = epoch

	@Synchronized
	fun dispatchFailure(): Throwable? = retainedDispatchFailure

	fun commitFence(
		request: ReaderPageRasterPublicationRequest
	): ReaderPageRasterCommitFence = ReaderPageRasterCommitFence { commit ->
		synchronized(this) {
			val entry = entries[request]
			if (
				entry == null ||
				entry.stale ||
				request.epoch != epoch ||
				entry.producerState != ProducerState.Active
			) {
				ReaderPageRasterWriteResult(
					persisted = false,
					ownership = ReaderPageRasterValueOwnership.Caller
				)
			} else {
				commit()
			}
		}
	}

	fun recordFailure(failure: Throwable) {
		synchronized(this) {
			val first = retainedDispatchFailure
			if (first == null) retainedDispatchFailure = failure
			else if (failure !== first) first.addSuppressed(failure)
		}
	}

	fun complete(
		request: ReaderPageRasterPublicationRequest,
		persisted: Boolean
	): Boolean {
		var capacityAvailable: (() -> Unit)? = null
		val completion = synchronized(this) {
			val entry = entries.remove(request) ?: return false
			check(entry.producerState == ProducerState.Active) {
				"Publication completed without active worker ownership"
			}
			val accepted = !entry.stale && request.epoch == epoch
			if (capacityRetryPending) {
				capacityAvailable = capacityAvailableListener
				if (capacityAvailable != null) capacityRetryPending = false
			}
			Completion(
				value = entry.value,
				callbacks = entry.callbacks.toList(),
				accepted = accepted,
				persisted = persisted
			).also { onOwnershipMutated() }
		}
		dispatchBestEffort(
			callbacks = completion.callbacks,
			callbackResult = completion.persisted && completion.accepted,
			values = listOf(completion.value)
		)
		capacityAvailable?.let(::dispatchCapacityAvailable)
		return completion.accepted
	}

	fun invalidate() {
		val callbacks = mutableListOf<(Boolean) -> Unit>()
		val queuedValues = mutableListOf<T>()
		synchronized(this) {
			epoch += 1L
			capacityRetryPending = false
			val ownershipChanged = entries.isNotEmpty()
			val iterator = entries.iterator()
			while (iterator.hasNext()) {
				val (_, entry) = iterator.next()
				entry.stale = true
				callbacks += entry.callbacks
				entry.callbacks.clear()
				if (entry.producerState == ProducerState.Queued) {
					queuedValues += entry.value
					iterator.remove()
				}
			}
			if (ownershipChanged) onOwnershipMutated()
		}
		dispatchBestEffort(callbacks, false, queuedValues)
	}

	private fun dispatchCapacityAvailable(listener: () -> Unit) {
		try {
			listener()
		} catch (failure: Throwable) {
			recordFailure(failure)
		}
	}

	private fun dispatchBestEffort(
		callbacks: List<(Boolean) -> Unit>,
		callbackResult: Boolean,
		values: List<T>
	) {
		var failure: Throwable? = null
		fun capture(action: () -> Unit) {
			try {
				action()
			} catch (next: Throwable) {
				val first = failure
				if (first == null) failure = next
				else if (next !== first) first.addSuppressed(next)
			}
		}
		callbacks.forEach { callback ->
			capture { callback(callbackResult) }
		}
		values.forEach { value ->
			capture { release(value) }
		}
		failure?.let(::recordFailure)
	}

	private companion object {
		const val MaximumCallbacksPerEntry = 2
	}
}
