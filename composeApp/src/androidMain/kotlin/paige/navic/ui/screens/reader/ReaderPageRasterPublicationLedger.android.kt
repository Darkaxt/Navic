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
	private val maxEntries: Int = Int.MAX_VALUE,
	private val maxCallbacksPerEntry: Int = Int.MAX_VALUE,
	private val release: (T) -> Unit
) {
	constructor(release: (T) -> Unit) : this(
		maxEntries = Int.MAX_VALUE,
		maxCallbacksPerEntry = Int.MAX_VALUE,
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
	private var retainedDispatchFailure: Throwable? = null

	init {
		require(maxEntries > 0)
		require(maxCallbacksPerEntry > 0)
	}

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
			when {
				existing != null &&
					existing.callbacks.size >= maxCallbacksPerEntry -> {
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
				entries.size >= maxEntries -> {
					rejectedCallback = callback
					detachedValue = value
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.EntryCapacity
					)
				}
				else -> {
					entries[request] = Entry(value, mutableListOf(callback))
					ReaderPageRasterPublicationRegistration.Started(request)
				}
			}
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
		entry.producerState = ProducerState.Active
		return entry.value
	}

	@Synchronized
	fun entryCount(): Int = entries.size

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
		val completion = synchronized(this) {
			val entry = entries.remove(request) ?: return false
			check(entry.producerState == ProducerState.Active) {
				"Publication completed without active worker ownership"
			}
			val accepted = !entry.stale && request.epoch == epoch
			Completion(
				value = entry.value,
				callbacks = entry.callbacks.toList(),
				accepted = accepted,
				persisted = persisted
			)
		}
		dispatchBestEffort(
			callbacks = completion.callbacks,
			callbackResult = completion.persisted && completion.accepted,
			values = listOf(completion.value)
		)
		return completion.accepted
	}

	fun invalidate() {
		val callbacks = mutableListOf<(Boolean) -> Unit>()
		val queuedValues = mutableListOf<T>()
		synchronized(this) {
			epoch += 1L
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
		}
		dispatchBestEffort(callbacks, false, queuedValues)
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
}
