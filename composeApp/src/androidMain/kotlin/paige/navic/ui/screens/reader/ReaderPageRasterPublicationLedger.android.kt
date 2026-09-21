package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind

internal data class ReaderPageRasterPublicationRequest(
	val digest: String,
	val epoch: Long,
	val mutationGeneration: ReaderForegroundWebViewMutationGeneration? = null
)

internal enum class ReaderPageRasterPublicationRejection {
	EntryCapacity,
	CallbackCapacity,
	ActivationFrozen
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

internal fun readerPageRasterPublicationRetryCorrelation(
	registration: ReaderPageRasterPublicationRegistration,
	correlation: ReaderPageQaFaultCorrelation?
): ReaderPageQaFaultCorrelation? =
	correlation
		?.takeIf { registration is ReaderPageRasterPublicationRegistration.Started }
		?.withRelation(ReaderPageQaFaultRelation.Retry)

internal class ReaderPageRasterPublicationLedger<T : Any>(
	val currentEpochEntryLimit: Int,
	private val persistenceWorkerLimit: Int,
	val callbackLimit: Int,
	private val onOwnershipMutated: () -> Unit = {},
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val release: (T) -> Unit
) {
	constructor(release: (T) -> Unit) : this(
		currentEpochEntryLimit = Int.MAX_VALUE / 2,
		persistenceWorkerLimit = Int.MAX_VALUE / 2,
		callbackLimit = Int.MAX_VALUE,
		release = release
	)

	private enum class ProducerState { Queued, Active }

	private data class OwnedCallback(
		val token: ReaderLegacySourceLocalOpaqueToken,
		val callback: (Boolean) -> Unit
	)

	private data class Entry<T : Any>(
		val entryToken: ReaderLegacySourceLocalOpaqueToken,
		val valueToken: ReaderLegacySourceLocalOpaqueToken,
		val value: T,
		val callbacks: MutableList<OwnedCallback>,
		var producerState: ProducerState = ProducerState.Queued,
		var stale: Boolean = false,
		var activationDrainTokens: List<ReaderLegacySourceLocalOpaqueToken>? = null
	) {
		fun ownershipTokens(): List<ReaderLegacySourceLocalOpaqueToken> =
			activationDrainTokens
				?: (listOf(entryToken, valueToken) + callbacks.map(OwnedCallback::token))
	}

	private data class Completion<T : Any>(
		val value: T,
		val callbacks: List<(Boolean) -> Unit>,
		val accepted: Boolean,
		val persisted: Boolean,
		val drainConfirmations: List<() -> Unit> = emptyList()
	)

	private var epoch = 0L
	private val entries =
		linkedMapOf<ReaderPageRasterPublicationRequest, Entry<T>>()
	private var capacityAvailableListener: (() -> Unit)? = null
	private var capacityAvailableListenerToken: ReaderLegacySourceLocalOpaqueToken? = null
	private var capacityRetryPending = false
	private var retainedDispatchFailure: Throwable? = null
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private val pendingDrainConfirmations =
		linkedMapOf<ReaderLegacySourceLocalOpaqueToken, () -> Unit>()

	init {
		require(currentEpochEntryLimit > 0)
		require(persistenceWorkerLimit > 0)
		require(callbackLimit >= currentEpochEntryLimit)
	}

	fun setCapacityAvailableListener(listener: () -> Unit) {
		val notify = synchronized(this) {
			check(frozenDomain == null) {
				"Publication capacity listener registration is frozen"
			}
			check(
				capacityAvailableListener == null ||
					capacityAvailableListener === listener
			) { "Publication capacity listener is already owned" }
			capacityAvailableListener = listener
			if (capacityAvailableListenerToken == null) {
				capacityAvailableListenerToken = nextOwnershipTokenLocked()
			}
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
				capacityAvailableListenerToken = null
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
	): ReaderPageRasterPublicationRegistration = begin(
		digest = digest,
		value = value,
		mutationGeneration = null,
		callback = callback
	)

	fun begin(
		digest: String,
		value: T,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration?,
		callback: (Boolean) -> Unit
	): ReaderPageRasterPublicationRegistration {
		var rejectedCallback: ((Boolean) -> Unit)? = null
		var detachedValue: T? = null
		val registration = synchronized(this) {
			val request = ReaderPageRasterPublicationRequest(
				digest = digest,
				epoch = epoch,
				mutationGeneration = mutationGeneration
			)
			val existing = entries[request]
			val globalCallbackCapacityReached = callbackCountLocked() >= callbackLimit
			val entryCallbackCapacityReached =
				(existing?.callbacks?.size ?: 0) >= MaximumCallbacksPerEntry
			val result = when {
				frozenDomain != null -> {
					rejectedCallback = callback
					detachedValue = value
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.ActivationFrozen
					)
				}
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
					existing.callbacks += OwnedCallback(
						token = nextOwnershipTokenLocked(),
						callback = callback
					)
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
					entries[request] = Entry(
						entryToken = nextOwnershipTokenLocked(),
						valueToken = nextOwnershipTokenLocked(),
						value = value,
						callbacks = mutableListOf(
							OwnedCallback(nextOwnershipTokenLocked(), callback)
						)
					)
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

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(this) {
		when {
			frozenDomain == null -> {
				frozenDomain = domain
				ReaderPortCommandResult.Accepted
			}
			frozenDomain == domain -> ReaderPortCommandResult.Accepted
			else -> ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(this) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		buildList {
			entries.values.forEach { entry ->
				fun addRow(
					token: ReaderLegacySourceLocalOpaqueToken,
					kind: ReaderTransitionResourceKind,
					state: ReaderLegacyResourceState
				) {
					add(
						ReaderFrozenLegacyResource(
							freezeToken = domain.freezeToken,
							physicalIdentity = ReaderLegacyPhysicalIdentity(
								domain = domain,
								source = ReaderLegacyInventorySource.RasterPublication,
								sourceLocalToken = token
							),
							kind = kind,
							binding = null,
							visibleOwner = null,
							origin = ReaderLegacyResourceOrigin.Owned,
							state = if (token in pendingDrainConfirmations) {
								ReaderLegacyResourceState.ReleaseRequested
							} else {
								state
							},
							mayBeCommittedPredecessor = false
						)
					)
				}
				val producerState = when (entry.producerState) {
					ProducerState.Queued -> ReaderLegacyResourceState.Reserved
					ProducerState.Active -> ReaderLegacyResourceState.Running
				}
				addRow(entry.entryToken, ReaderTransitionResourceKind.Raster, producerState)
				addRow(entry.valueToken, ReaderTransitionResourceKind.Raster, producerState)
				entry.callbacks.forEach { owned ->
					addRow(
						owned.token,
						ReaderTransitionResourceKind.CallbackRegistration,
						ReaderLegacyResourceState.Registered
					)
				}
			}
			capacityAvailableListenerToken?.let { token ->
				add(
					ReaderFrozenLegacyResource(
						freezeToken = domain.freezeToken,
						physicalIdentity = ReaderLegacyPhysicalIdentity(
							domain = domain,
							source = ReaderLegacyInventorySource.RasterPublication,
							sourceLocalToken = token
						),
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						binding = null,
						visibleOwner = null,
						origin = ReaderLegacyResourceOrigin.Owned,
						state = ReaderLegacyResourceState.Registered,
						mayBeCommittedPredecessor = false
					)
				)
			}
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		var completion: Completion<T>? = null
		var callbacksToReject = emptyList<(Boolean) -> Unit>()
		var directConfirmation: (() -> Unit)? = null
		val accepted = synchronized(this) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source != ReaderLegacyInventorySource.RasterPublication
			) {
				return@synchronized false
			}
			val token = physicalIdentity.sourceLocalToken
			if (token in pendingDrainConfirmations) return@synchronized false
			if (capacityAvailableListenerToken == token) {
				capacityAvailableListener = null
				capacityAvailableListenerToken = null
				capacityRetryPending = false
				directConfirmation = { onConfirmed(physicalIdentity) }
				return@synchronized true
			}
			val ownedEntry = entries.entries.firstOrNull { (_, entry) ->
				token in entry.ownershipTokens()
			} ?: return@synchronized false
			pendingDrainConfirmations[token] = { onConfirmed(physicalIdentity) }
			val ownershipTokens = ownedEntry.value.ownershipTokens()
			if (ownershipTokens.all(pendingDrainConfirmations::containsKey)) {
				val entry = ownedEntry.value
				entry.activationDrainTokens = ownershipTokens
				entry.stale = true
				callbacksToReject = entry.callbacks.map(OwnedCallback::callback)
				entry.callbacks.clear()
				if (entry.producerState == ProducerState.Queued) {
					entries.remove(ownedEntry.key)
					completion = Completion(
						value = entry.value,
						callbacks = emptyList(),
						accepted = false,
						persisted = false,
						drainConfirmations = ownershipTokens.mapNotNull(
							pendingDrainConfirmations::remove
						)
					)
					onOwnershipMutated()
				}
			}
			true
		}
		if (!accepted) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		dispatchBestEffort(callbacksToReject, false, emptyList())
		completion?.let { drained ->
			dispatchBestEffort(emptyList(), false, listOf(drained.value))
			drained.drainConfirmations.forEach { confirmation -> confirmation() }
		}
		directConfirmation?.invoke()
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(this) {
		if (frozenDomain != domain || pendingDrainConfirmations.isNotEmpty()) {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		} else {
			frozenDomain = null
			ReaderPortCommandResult.Accepted
		}
	}

	private fun nextOwnershipTokenLocked(): ReaderLegacySourceLocalOpaqueToken =
		tokenAllocator.allocate()

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
				callbacks = entry.callbacks.map(OwnedCallback::callback),
				accepted = accepted,
				persisted = persisted,
				drainConfirmations = entry.ownershipTokens().mapNotNull(
					pendingDrainConfirmations::remove
				)
			).also { onOwnershipMutated() }
		}
		dispatchBestEffort(
			callbacks = completion.callbacks,
			callbackResult = completion.persisted && completion.accepted,
			values = listOf(completion.value)
		)
		completion.drainConfirmations.forEach { confirmation -> confirmation() }
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
				callbacks += entry.callbacks.map(OwnedCallback::callback)
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
