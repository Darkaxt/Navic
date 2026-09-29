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
		val completionToken: ReaderLegacySourceLocalOpaqueToken,
		val value: T,
		val callbacks: MutableList<OwnedCallback>,
		var producerState: ProducerState = ProducerState.Queued,
		var stale: Boolean = false,
		var invalidationDispatching: Boolean = false,
		var activationDrainTokens: List<ReaderLegacySourceLocalOpaqueToken>? = null
	) {
		fun ownershipTokens(): List<ReaderLegacySourceLocalOpaqueToken> =
			activationDrainTokens
				?: (
					listOf(entryToken, valueToken, completionToken) +
						callbacks.map(OwnedCallback::token)
				)

		fun ownershipKind(
			token: ReaderLegacySourceLocalOpaqueToken
		): ReaderTransitionResourceKind = if (
			token == entryToken || token == valueToken || token == completionToken
		) {
			ReaderTransitionResourceKind.Raster
		} else {
			ReaderTransitionResourceKind.CallbackRegistration
		}

		fun ownershipState(
			token: ReaderLegacySourceLocalOpaqueToken
		): ReaderLegacyResourceState = when {
			token == completionToken -> ReaderLegacyResourceState.Reserved
			token == entryToken || token == valueToken -> when (producerState) {
				ProducerState.Queued -> ReaderLegacyResourceState.Reserved
				ProducerState.Active -> ReaderLegacyResourceState.Running
			}
			else -> ReaderLegacyResourceState.Registered
		}
	}

	private data class DispatchingOwnership(
		val kind: ReaderTransitionResourceKind,
		val state: ReaderLegacyResourceState
	)

	private class FrozenRejectedCleanupOwnership(
		val callbackToken: ReaderLegacySourceLocalOpaqueToken,
		val valueToken: ReaderLegacySourceLocalOpaqueToken
	) {
		val remainingTokens = linkedSetOf(callbackToken, valueToken)
		val tokens: List<ReaderLegacySourceLocalOpaqueToken>
			get() = listOf(callbackToken, valueToken)
	}

	private data class CapacityDispatch(
		val token: ReaderLegacySourceLocalOpaqueToken,
		val listener: () -> Unit
	)

	private data class Completion<T : Any>(
		val value: T,
		val callbacks: List<(Boolean) -> Unit>,
		val accepted: Boolean,
		val persisted: Boolean,
		val dispatchingTokens: List<ReaderLegacySourceLocalOpaqueToken> = emptyList(),
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
	private val releasedFrozenOwnership =
		linkedMapOf<ReaderLegacySourceLocalOpaqueToken, ReaderTransitionResourceKind>()
	private val dispatchingOwnership =
		linkedMapOf<ReaderLegacySourceLocalOpaqueToken, DispatchingOwnership>()
	private val dispatchingCallbackOwnership =
		linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private val dispatchingCapacityListenerTokens =
		linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private val frozenRejectedCleanupOwnership =
		mutableListOf<FrozenRejectedCleanupOwnership>()

	init {
		require(currentEpochEntryLimit > 0)
		require(persistenceWorkerLimit > 0)
		require(callbackLimit >= currentEpochEntryLimit)
	}

	fun setCapacityAvailableListener(listener: () -> Unit) {
		val dispatch = synchronized(this) {
			check(frozenDomain == null) {
				"Publication capacity listener registration is frozen"
			}
			check(
				capacityAvailableListener == null ||
					capacityAvailableListener === listener
			) { "Publication capacity listener is already owned" }
			capacityAvailableListener = listener
			val token = capacityAvailableListenerToken
				?: nextOwnershipTokenLocked().also { capacityAvailableListenerToken = it }
			if (capacityRetryPending) {
				capacityRetryPending = false
				CapacityDispatch(token, listener)
			} else {
				null
			}
		}
		dispatch?.let(::dispatchCapacityAvailable)
	}

	fun clearCapacityAvailableListener(listener: () -> Unit) {
		synchronized(this) {
			if (capacityAvailableListener === listener) {
				capacityAvailableListenerToken?.let { token ->
					if (token in dispatchingCapacityListenerTokens) {
						dispatchingOwnership[token] = DispatchingOwnership(
							kind = ReaderTransitionResourceKind.CallbackRegistration,
							state = ReaderLegacyResourceState.Registered
						)
					} else if (frozenDomain != null) {
						releasedFrozenOwnership[token] =
							ReaderTransitionResourceKind.CallbackRegistration
					}
				}
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
		var cleanupTokens = emptyList<ReaderLegacySourceLocalOpaqueToken>()
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
			fun ownRejectedCleanup() {
				val callbackToken = nextOwnershipTokenLocked()
				val valueToken = nextOwnershipTokenLocked()
				dispatchingOwnership[callbackToken] = DispatchingOwnership(
					kind = ReaderTransitionResourceKind.CallbackRegistration,
					state = ReaderLegacyResourceState.Registered
				)
				dispatchingOwnership[valueToken] = DispatchingOwnership(
					kind = ReaderTransitionResourceKind.Raster,
					state = ReaderLegacyResourceState.Running
				)
				cleanupTokens = listOf(callbackToken, valueToken)
			}
			fun ownFrozenRejectedCleanup() {
				check(frozenRejectedCleanupOwnership.size < callbackLimit) {
					"Frozen publication cleanup ownership capacity exhausted"
				}
				val ownership = FrozenRejectedCleanupOwnership(
					callbackToken = nextOwnershipTokenLocked(),
					valueToken = nextOwnershipTokenLocked()
				).also(frozenRejectedCleanupOwnership::add)
				dispatchingOwnership[ownership.callbackToken] = DispatchingOwnership(
					kind = ReaderTransitionResourceKind.CallbackRegistration,
					state = ReaderLegacyResourceState.Registered
				)
				dispatchingOwnership[ownership.valueToken] = DispatchingOwnership(
					kind = ReaderTransitionResourceKind.Raster,
					state = ReaderLegacyResourceState.Running
				)
				cleanupTokens = ownership.tokens
			}
			fun ownDetachedValueCleanup() {
				val valueToken = nextOwnershipTokenLocked()
				dispatchingOwnership[valueToken] = DispatchingOwnership(
					kind = ReaderTransitionResourceKind.Raster,
					state = ReaderLegacyResourceState.Running
				)
				cleanupTokens = listOf(valueToken)
			}
			val result = when {
				frozenDomain != null -> {
					rejectedCallback = callback
					detachedValue = value
					ownFrozenRejectedCleanup()
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.ActivationFrozen
					)
				}
				existing != null &&
					(entryCallbackCapacityReached || globalCallbackCapacityReached) -> {
					capacityRetryPending = true
					rejectedCallback = callback
					detachedValue = value
					ownRejectedCleanup()
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
					ownDetachedValueCleanup()
					ReaderPageRasterPublicationRegistration.Coalesced(request)
				}
				currentEpochEntryCountLocked() >= currentEpochEntryLimit -> {
					capacityRetryPending = true
					rejectedCallback = callback
					detachedValue = value
					ownRejectedCleanup()
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.EntryCapacity
					)
				}
				globalCallbackCapacityReached -> {
					capacityRetryPending = true
					rejectedCallback = callback
					detachedValue = value
					ownRejectedCleanup()
					ReaderPageRasterPublicationRegistration.Rejected(
						ReaderPageRasterPublicationRejection.CallbackCapacity
					)
				}
				else -> {
					entries[request] = Entry(
						entryToken = nextOwnershipTokenLocked(),
						valueToken = nextOwnershipTokenLocked(),
						completionToken = nextOwnershipTokenLocked(),
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
		dispatchConfirmationsBestEffort(
			finishDispatchingOwnership(cleanupTokens)
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
			val attachedOwnershipTokens = mutableSetOf<ReaderLegacySourceLocalOpaqueToken>()
			entries.values.forEach { entry ->
				val producerState = when (entry.producerState) {
					ProducerState.Queued -> ReaderLegacyResourceState.Reserved
					ProducerState.Active -> ReaderLegacyResourceState.Running
				}
				attachedOwnershipTokens += entry.ownershipTokens()
				addRow(entry.entryToken, ReaderTransitionResourceKind.Raster, producerState)
				addRow(entry.valueToken, ReaderTransitionResourceKind.Raster, producerState)
				addRow(
					entry.completionToken,
					ReaderTransitionResourceKind.Raster,
					ReaderLegacyResourceState.Reserved
				)
				entry.callbacks.forEach { owned ->
					addRow(
						owned.token,
						ReaderTransitionResourceKind.CallbackRegistration,
						ReaderLegacyResourceState.Registered
					)
				}
			}
			dispatchingOwnership
				.filterKeys { token -> token !in attachedOwnershipTokens }
				.forEach { (token, ownership) ->
					addRow(token, ownership.kind, ownership.state)
				}
			releasedFrozenOwnership.forEach { (token, kind) ->
				addRow(token, kind, ReaderLegacyResourceState.Released)
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
		var rejectedCallbackTokens = emptyList<ReaderLegacySourceLocalOpaqueToken>()
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
			if (releasedFrozenOwnership.remove(token) != null) {
				retireFrozenRejectedCleanupTokenLocked(token)
				directConfirmation = { onConfirmed(physicalIdentity) }
				return@synchronized true
			}
			if (capacityAvailableListenerToken == token) {
				capacityAvailableListener = null
				capacityAvailableListenerToken = null
				capacityRetryPending = false
				if (token in dispatchingCapacityListenerTokens) {
					dispatchingOwnership[token] = DispatchingOwnership(
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						state = ReaderLegacyResourceState.Registered
					)
					pendingDrainConfirmations[token] = {
						onConfirmed(physicalIdentity)
					}
				} else {
					directConfirmation = { onConfirmed(physicalIdentity) }
				}
				return@synchronized true
			}
			val ownedEntry = entries.entries.firstOrNull { (_, entry) ->
				token in entry.ownershipTokens()
			}
			if (ownedEntry == null) {
				if (token !in dispatchingOwnership) return@synchronized false
				pendingDrainConfirmations[token] = { onConfirmed(physicalIdentity) }
				return@synchronized true
			}
			pendingDrainConfirmations[token] = { onConfirmed(physicalIdentity) }
			val ownershipTokens = ownedEntry.value.ownershipTokens()
			if (ownershipTokens.all(pendingDrainConfirmations::containsKey)) {
				val entry = ownedEntry.value
				val rejectedCallbacks = entry.callbacks.filterNot { owned ->
					owned.token in dispatchingCallbackOwnership
				}
				rejectedCallbackTokens = rejectedCallbacks.map(OwnedCallback::token)
				callbacksToReject = rejectedCallbacks.map(OwnedCallback::callback)
				rejectedCallbackTokens.forEach { callbackToken ->
					dispatchingOwnership[callbackToken] = DispatchingOwnership(
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						state = ReaderLegacyResourceState.Registered
					)
				}
				entry.activationDrainTokens = ownershipTokens.filterNot {
					it in rejectedCallbackTokens
				}
				entry.callbacks.removeAll { owned ->
					owned.token in rejectedCallbackTokens
				}
				entry.stale = true
				if (
					entry.producerState == ProducerState.Queued &&
					!entry.invalidationDispatching
				) {
					entries.remove(ownedEntry.key)
					completion = Completion(
						value = entry.value,
						callbacks = emptyList(),
						accepted = false,
						persisted = false,
						drainConfirmations = entry.ownershipTokens()
							.filterNot(dispatchingCallbackOwnership::contains)
							.mapNotNull(pendingDrainConfirmations::remove)
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
		dispatchConfirmationsBestEffort(
			finishDispatchingOwnership(rejectedCallbackTokens)
		)
		completion?.let { drained ->
			dispatchBestEffort(emptyList(), false, listOf(drained.value))
			dispatchConfirmationsBestEffort(drained.drainConfirmations)
		}
		dispatchConfirmationsBestEffort(listOfNotNull(directConfirmation))
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
			releasedFrozenOwnership.clear()
			frozenRejectedCleanupOwnership.clear()
			frozenDomain = null
			ReaderPortCommandResult.Accepted
		}
	}

	private fun nextOwnershipTokenLocked(): ReaderLegacySourceLocalOpaqueToken =
		tokenAllocator.allocate()

	private fun retireFrozenRejectedCleanupTokenLocked(
		token: ReaderLegacySourceLocalOpaqueToken
	) {
		val ownership = frozenRejectedCleanupOwnership.firstOrNull { candidate ->
			token in candidate.remainingTokens
		} ?: return
		ownership.remainingTokens.remove(token)
		if (ownership.remainingTokens.isEmpty()) {
			frozenRejectedCleanupOwnership.remove(ownership)
		}
	}

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
		var capacityAvailable: CapacityDispatch? = null
		val completion = synchronized(this) {
			val entry = entries.remove(request) ?: return false
			check(entry.producerState == ProducerState.Active) {
				"Publication completed without active worker ownership"
			}
			val accepted = !entry.stale && request.epoch == epoch
			if (capacityRetryPending) {
				val listener = capacityAvailableListener
				val listenerToken = capacityAvailableListenerToken
				if (listener != null && listenerToken != null) {
					capacityAvailable = CapacityDispatch(listenerToken, listener)
					capacityRetryPending = false
				}
			}
			val dispatchingTokens = entry.ownershipTokens()
				.filterNot(dispatchingCallbackOwnership::contains)
			dispatchingTokens.forEach { token ->
				dispatchingOwnership[token] = DispatchingOwnership(
					kind = entry.ownershipKind(token),
					state = entry.ownershipState(token)
				)
			}
			Completion(
				value = entry.value,
				callbacks = entry.callbacks
					.filterNot { owned -> owned.token in dispatchingCallbackOwnership }
					.map(OwnedCallback::callback),
				accepted = accepted,
				persisted = persisted,
				dispatchingTokens = dispatchingTokens
			).also { onOwnershipMutated() }
		}
		dispatchBestEffort(
			callbacks = completion.callbacks,
			callbackResult = completion.persisted && completion.accepted,
			values = listOf(completion.value)
		)
		dispatchConfirmationsBestEffort(
			completion.drainConfirmations +
				finishDispatchingOwnership(completion.dispatchingTokens)
		)
		capacityAvailable?.let(::dispatchCapacityAvailable)
		return completion.accepted
	}

	fun invalidate() {
		val callbacks = mutableListOf<(Boolean) -> Unit>()
		val invalidatedCallbacks = mutableListOf<Pair<Entry<T>, OwnedCallback>>()
		val queuedValues = mutableListOf<T>()
		val queuedEntries = mutableListOf<
			Pair<ReaderPageRasterPublicationRequest, Entry<T>>
		>()
		synchronized(this) {
			epoch += 1L
			capacityRetryPending = false
			entries.forEach { (request, entry) ->
				if (entry.stale) return@forEach
				entry.stale = true
				entry.callbacks.forEach { owned ->
					callbacks += owned.callback
					invalidatedCallbacks += entry to owned
					dispatchingCallbackOwnership += owned.token
					dispatchingOwnership[owned.token] = DispatchingOwnership(
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						state = ReaderLegacyResourceState.Registered
					)
				}
				if (entry.producerState == ProducerState.Queued) {
					entry.invalidationDispatching = true
					queuedValues += entry.value
					queuedEntries += request to entry
				}
			}
		}
		dispatchBestEffort(callbacks, false, queuedValues)
		val confirmations = mutableListOf<() -> Unit>()
		synchronized(this) {
			fun completeOwnershipToken(
				kind: ReaderTransitionResourceKind,
				token: ReaderLegacySourceLocalOpaqueToken
			) {
				val confirmation = pendingDrainConfirmations.remove(token)
				if (confirmation != null) {
					confirmations += confirmation
				} else if (frozenDomain != null) {
					releasedFrozenOwnership[token] = kind
				}
			}
			queuedEntries.forEach { (request, entry) ->
				if (entries[request] !== entry) return@forEach
				entry.invalidationDispatching = false
				entries.remove(request)
				entry.ownershipTokens()
					.filterNot(dispatchingCallbackOwnership::contains)
					.forEach { token ->
						completeOwnershipToken(entry.ownershipKind(token), token)
					}
			}
			invalidatedCallbacks.forEach { (entry, owned) ->
				dispatchingCallbackOwnership.remove(owned.token)
				dispatchingOwnership.remove(owned.token)
				entry.activationDrainTokens = entry.activationDrainTokens?.filterNot { token ->
					token == owned.token
				}
				entry.callbacks.removeAll { callback -> callback.token == owned.token }
				completeOwnershipToken(
					ReaderTransitionResourceKind.CallbackRegistration,
					owned.token
				)
			}
			if (queuedEntries.isNotEmpty() || invalidatedCallbacks.isNotEmpty()) {
				onOwnershipMutated()
			}
		}
		dispatchConfirmationsBestEffort(confirmations)
	}

	private fun finishDispatchingOwnership(
		tokens: List<ReaderLegacySourceLocalOpaqueToken>
	): List<() -> Unit> = synchronized(this) {
		buildList {
			tokens.forEach { token ->
				val ownership = dispatchingOwnership.remove(token) ?: return@forEach
				val confirmation = pendingDrainConfirmations.remove(token)
				if (confirmation != null) {
					retireFrozenRejectedCleanupTokenLocked(token)
					add(confirmation)
				} else if (frozenDomain != null) {
					releasedFrozenOwnership[token] = ownership.kind
				}
			}
		}
	}

	private fun dispatchConfirmationsBestEffort(confirmations: List<() -> Unit>) {
		confirmations.forEach { confirmation ->
			runCatching(confirmation).onFailure(::recordFailure)
		}
	}

	private fun dispatchCapacityAvailable(dispatch: CapacityDispatch) {
		val admitted = synchronized(this) {
			if (
				capacityAvailableListenerToken != dispatch.token ||
				capacityAvailableListener !== dispatch.listener
			) {
				false
			} else {
				dispatchingCapacityListenerTokens.add(dispatch.token)
			}
		}
		if (!admitted) return
		try {
			dispatch.listener()
		} catch (failure: Throwable) {
			recordFailure(failure)
		} finally {
			val detached = synchronized(this) {
				dispatchingCapacityListenerTokens.remove(dispatch.token)
				capacityAvailableListenerToken != dispatch.token
			}
			if (detached) {
				dispatchConfirmationsBestEffort(
					finishDispatchingOwnership(listOf(dispatch.token))
				)
			}
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
