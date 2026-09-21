package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderTransitionFailureReason

@JvmInline
internal value class ReaderForegroundWebViewMutationGeneration(val value: Long) {
	init {
		require(value in 1L..ReaderPageTurnPresentationMaximumSafeInteger)
	}
}

internal data class ReaderForegroundWebViewPassiveLease internal constructor(
	val leaseId: Long,
	val sessionId: Long,
	val mutationGeneration: ReaderForegroundWebViewMutationGeneration
)

internal data class ReaderForegroundWebViewLiveClaim internal constructor(
	val claimId: Long,
	val gestureId: Long
)

internal sealed interface ReaderForegroundWebViewLiveReadiness {
	data object Ready : ReaderForegroundWebViewLiveReadiness

	data class Failed(
		val restoration: ReaderPageRasterCancellationRestoration
	) : ReaderForegroundWebViewLiveReadiness

	data object Invalidated : ReaderForegroundWebViewLiveReadiness
}

internal data class ReaderForegroundWebViewOwnershipSnapshot(
	val passiveOwners: Int,
	val liveClaims: Int,
	val restorationCallbacks: Int,
	val closed: Boolean
)

internal class ReaderForegroundWebViewOwnership(
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val onPassiveMutationReleased: () -> Unit = {},
	private val onPassiveAvailable: () -> Unit = {}
) {
	private data class OwnedReadinessCallback(
		val activationToken: ReaderLegacySourceLocalOpaqueToken,
		val callback: (ReaderForegroundWebViewLiveReadiness) -> Unit
	)

	private data class LiveClaimState(
		val claim: ReaderForegroundWebViewLiveClaim,
		val blockedByExclusiveClaim: Boolean,
		var terminal: ReaderForegroundWebViewLiveReadiness?,
		val activationToken: ReaderLegacySourceLocalOpaqueToken,
		val callbacks: MutableList<OwnedReadinessCallback> = mutableListOf()
	)

	private data class RetiredClaimTerminal(
		val claim: ReaderForegroundWebViewLiveClaim,
		val terminal: ReaderForegroundWebViewLiveReadiness
	)

	private data class RestartPassive(
		val lease: ReaderForegroundWebViewPassiveLease,
		val cancelAndRestore: (((ReaderPageRasterCancellationRestoration) -> Unit) -> Unit),
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private data class RestartRestoration(
		val leaseId: Long,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private var mutationGeneration = 0L
	private var nextLeaseId = 0L
	private var nextClaimId = 0L
	private var passiveLease: ReaderForegroundWebViewPassiveLease? = null
	private var passiveActivationToken: ReaderLegacySourceLocalOpaqueToken? = null
	private var cancelAndRestore: (
		(
			(ReaderPageRasterCancellationRestoration) -> Unit
		) -> Unit
	)? = null
	private var restorationLeaseId: Long? = null
	private var restorationActivationToken: ReaderLegacySourceLocalOpaqueToken? = null
	private val liveClaims = linkedMapOf<Long, LiveClaimState>()
	private val retiredClaimTerminals =
		linkedMapOf<Long, RetiredClaimTerminal>()
	private var exclusiveClaimId: Long? = null
	private var currentMutationClaimId: Long? = null
	private var passiveAvailabilityVersion = 0L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private val restartClaims = linkedMapOf<Long, LiveClaimState>()
	private val restartExclusiveClaimIds = linkedSetOf<Long>()
	private var restartPassive: RestartPassive? = null
	private var restartRestoration: RestartRestoration? = null
	private val completedFrozen = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private var closed = false

	fun canAcquirePassive(): Boolean =
		!closed &&
			frozenDomain == null &&
			passiveLease == null &&
			restorationLeaseId == null &&
			liveClaims.isEmpty()

	fun tryAcquirePassive(
		sessionId: Long,
		cancelAndRestore: (
			(ReaderPageRasterCancellationRestoration) -> Unit
		) -> Unit
	): ReaderForegroundWebViewPassiveLease? {
		if (!canAcquirePassive()) return null
		val nextGeneration = nextMutationGeneration() ?: return null
		val leaseId = nextPositiveId(nextLeaseId)
		val lease = ReaderForegroundWebViewPassiveLease(
			leaseId = leaseId,
			sessionId = sessionId,
			mutationGeneration = ReaderForegroundWebViewMutationGeneration(
				nextGeneration
			)
		)
		nextLeaseId = leaseId
		mutationGeneration = nextGeneration
		currentMutationClaimId = null
		passiveLease = lease
		passiveActivationToken = tokenAllocator.allocate()
		this.cancelAndRestore = cancelAndRestore
		return lease
	}

	fun acquireLive(gestureId: Long): ReaderForegroundWebViewLiveClaim =
		acquireLive(gestureId, exclusive = false)

	fun acquireExclusiveLive(
		requestId: Long
	): ReaderForegroundWebViewLiveClaim =
		acquireLive(requestId, exclusive = true)

	private fun acquireLive(
		gestureId: Long,
		exclusive: Boolean
	) : ReaderForegroundWebViewLiveClaim {
		check(!closed) { "Foreground WebView ownership is closed" }
		check(frozenDomain == null) { "Foreground WebView ownership is frozen" }
		if (exclusive) {
			check(exclusiveClaimId == null) {
				"Foreground WebView exclusive claim already exists"
			}
		}
		val claimId = nextPositiveId(nextClaimId)
		nextClaimId = claimId
		val claim = ReaderForegroundWebViewLiveClaim(
			claimId = claimId,
			gestureId = gestureId
		)
		val blockedByExclusiveClaim = !exclusive && exclusiveClaimId != null
		val waitsForExistingLiveClaim = exclusive && liveClaims.isNotEmpty()
		val waitsForReadiness =
			restorationLeaseId != null ||
				passiveLease != null ||
				blockedByExclusiveClaim ||
				waitsForExistingLiveClaim
		liveClaims[claimId] = LiveClaimState(
			claim = claim,
			blockedByExclusiveClaim = blockedByExclusiveClaim,
			terminal = if (waitsForReadiness) {
				null
			} else {
				ReaderForegroundWebViewLiveReadiness.Ready
			},
			activationToken = tokenAllocator.allocate()
		)
		if (exclusive) exclusiveClaimId = claimId

		val preemptedLease = passiveLease ?: return claim
		val preemption = checkNotNull(cancelAndRestore)
		passiveLease = null
		passiveActivationToken = null
		cancelAndRestore = null
		restorationLeaseId = preemptedLease.leaseId
		restorationActivationToken = tokenAllocator.allocate()
		currentMutationClaimId = null
		preemption { restoration ->
			completeRestoration(preemptedLease.leaseId, restoration)
		}
		return claim
	}

	fun whenLiveReady(
		claim: ReaderForegroundWebViewLiveClaim,
		callback: (ReaderForegroundWebViewLiveReadiness) -> Unit
	) {
		if (closed || frozenDomain != null) {
			callback(ReaderForegroundWebViewLiveReadiness.Invalidated)
			return
		}
		val state = liveClaims[claim.claimId]
		if (state?.claim != claim) {
			val retired = retiredClaimTerminals.remove(claim.claimId)
			callback(
				if (retired?.claim == claim) {
					retired.terminal
				} else {
					ReaderForegroundWebViewLiveReadiness.Invalidated
				}
			)
			return
		}
		val terminal = state.terminal
		if (terminal == null) {
			state.callbacks += OwnedReadinessCallback(
				tokenAllocator.allocate(),
				callback
			)
		} else {
			callback(terminal)
		}
	}

	fun beginLiveMutation(
		claim: ReaderForegroundWebViewLiveClaim
	): ReaderForegroundWebViewMutationGeneration? {
		val state = liveClaims[claim.claimId]
		if (
			closed ||
			frozenDomain != null ||
			state?.claim != claim ||
			state.terminal != ReaderForegroundWebViewLiveReadiness.Ready ||
			restorationLeaseId != null
		) {
			return null
		}
		val nextGeneration = nextMutationGeneration() ?: return null
		mutationGeneration = nextGeneration
		currentMutationClaimId = claim.claimId
		return ReaderForegroundWebViewMutationGeneration(nextGeneration)
	}

	fun isCurrent(lease: ReaderForegroundWebViewPassiveLease): Boolean =
		!closed && passiveLease == lease &&
			lease.mutationGeneration.value == mutationGeneration

	fun isCurrent(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration
	): Boolean {
		val state = liveClaims[claim.claimId]
		return !closed &&
			state?.claim == claim &&
			state.terminal == ReaderForegroundWebViewLiveReadiness.Ready &&
			restorationLeaseId == null &&
			currentMutationClaimId == claim.claimId &&
			generation.value == mutationGeneration
	}

	fun isMutationGenerationCurrent(value: Long): Boolean =
		!closed &&
			restorationLeaseId == null &&
			value == mutationGeneration

	fun releasePassive(lease: ReaderForegroundWebViewPassiveLease): Boolean {
		if (closed || passiveLease != lease) return false
		if (frozenDomain != null) passiveActivationToken?.let(completedFrozen::add)
		passiveLease = null
		passiveActivationToken = null
		cancelAndRestore = null
		onPassiveMutationReleased()
		return true
	}

	fun releaseLive(claim: ReaderForegroundWebViewLiveClaim): Boolean {
		if (closed) return false
		val state = liveClaims[claim.claimId]
		if (state?.claim != claim) return false
		liveClaims.remove(claim.claimId)
		if (frozenDomain != null) {
			completedFrozen += state.activationToken
			completedFrozen += state.callbacks.map { it.activationToken }
		}
		if (exclusiveClaimId == claim.claimId) {
			exclusiveClaimId = null
		}
		if (currentMutationClaimId == claim.claimId) {
			currentMutationClaimId = null
		}
		val availabilityVersionBeforeCallbacks = passiveAvailabilityVersion
		if (state.terminal == null) {
			deliver(state, ReaderForegroundWebViewLiveReadiness.Invalidated)
		}
		publishReadyClaims()
		if (
			canAcquirePassive() &&
			passiveAvailabilityVersion == availabilityVersionBeforeCallbacks
		) {
			publishPassiveAvailable()
		}
		return true
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = when {
		closed -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		frozenDomain == null -> {
			frozenDomain = domain
			ReaderPortCommandResult.Accepted
		}
		frozenDomain == domain -> ReaderPortCommandResult.Accepted
		else -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		val domain = frozenDomain ?: return emptyList()
		val topLevel = buildList {
			passiveActivationToken?.let { token ->
				add(
					foregroundOwnershipRow(
						domain,
						token,
						paige.navic.reader.ReaderTransitionResourceKind.FrameHandoff,
						ReaderLegacyResourceOrigin.Owned,
						ReaderLegacyResourceState.Running
					)
				)
			}
			restorationActivationToken?.let { token ->
				add(
					foregroundOwnershipRow(
						domain,
						token,
						paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration,
						ReaderLegacyResourceOrigin.Pending,
						ReaderLegacyResourceState.Registered
					)
				)
			}
		}
		return topLevel + liveClaims.values.flatMap { state ->
			listOf(
				ReaderFrozenLegacyResource(
					freezeToken = domain.freezeToken,
					physicalIdentity = ReaderLegacyPhysicalIdentity(
						domain,
						ReaderLegacyInventorySource.ForegroundWebViewOwnership,
						state.activationToken
					),
					kind = paige.navic.reader.ReaderTransitionResourceKind.FrameHandoff,
					binding = null,
					visibleOwner = null,
					origin = ReaderLegacyResourceOrigin.Owned,
					state = if (state.terminal == null) {
						ReaderLegacyResourceState.Reserved
					} else {
						ReaderLegacyResourceState.Running
					},
					mayBeCommittedPredecessor = false
				)
			) + state.callbacks.map { callback ->
				ReaderFrozenLegacyResource(
					freezeToken = domain.freezeToken,
					physicalIdentity = ReaderLegacyPhysicalIdentity(
						domain,
						ReaderLegacyInventorySource.ForegroundWebViewOwnership,
						callback.activationToken
					),
					kind = paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration,
					binding = null,
					visibleOwner = null,
					origin = ReaderLegacyResourceOrigin.Pending,
					state = ReaderLegacyResourceState.Registered,
					mayBeCommittedPredecessor = false
				)
			}
		}
	}

	private fun foregroundOwnershipRow(
		domain: ReaderLegacyPhysicalDomain,
		token: ReaderLegacySourceLocalOpaqueToken,
		kind: paige.navic.reader.ReaderTransitionResourceKind,
		origin: ReaderLegacyResourceOrigin,
		state: ReaderLegacyResourceState
	) = ReaderFrozenLegacyResource(
		freezeToken = domain.freezeToken,
		physicalIdentity = ReaderLegacyPhysicalIdentity(
			domain,
			ReaderLegacyInventorySource.ForegroundWebViewOwnership,
			token
		),
		kind = kind,
		binding = null,
		visibleOwner = null,
		origin = origin,
		state = state,
		mayBeCommittedPredecessor = false
	)

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source !=
				ReaderLegacyInventorySource.ForegroundWebViewOwnership
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val token = physicalIdentity.sourceLocalToken
		if (passiveActivationToken == token) {
			restartPassive = RestartPassive(
				checkNotNull(passiveLease),
				checkNotNull(cancelAndRestore),
				token
			)
			passiveLease = null
			passiveActivationToken = null
			cancelAndRestore = null
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (restorationActivationToken == token) {
			restartRestoration = RestartRestoration(
				checkNotNull(restorationLeaseId),
				token
			)
			restorationLeaseId = null
			restorationActivationToken = null
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		liveClaims.values.forEach { state ->
			val callback = state.callbacks.firstOrNull { it.activationToken == token }
			if (callback != null) {
				state.callbacks.remove(callback)
				callback.callback(ReaderForegroundWebViewLiveReadiness.Invalidated)
				onConfirmed(physicalIdentity)
				return ReaderPortCommandResult.Accepted
			}
		}
		val claimEntry = liveClaims.entries.firstOrNull {
			it.value.activationToken == token
		}
		if (claimEntry != null) {
			val state = claimEntry.value
			liveClaims.remove(claimEntry.key)
			if (exclusiveClaimId == claimEntry.key) {
				restartExclusiveClaimIds += claimEntry.key
				exclusiveClaimId = null
			}
			if (currentMutationClaimId == claimEntry.key) currentMutationClaimId = null
			state.callbacks.forEach { callback ->
				completedFrozen += callback.activationToken
				callback.callback(ReaderForegroundWebViewLiveReadiness.Invalidated)
			}
			state.callbacks.clear()
			restartClaims[claimEntry.key] = state
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (completedFrozen.remove(token)) {
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (frozenDomain != domain) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		liveClaims.putAll(restartClaims)
		restartClaims.clear()
		restartPassive?.let { restart ->
			passiveLease = restart.lease
			cancelAndRestore = restart.cancelAndRestore
			passiveActivationToken = restart.activationToken
		}
		restartPassive = null
		restartRestoration?.let { restart ->
			restorationLeaseId = restart.leaseId
			restorationActivationToken = restart.activationToken
		}
		restartRestoration = null
		exclusiveClaimId = restartExclusiveClaimIds.singleOrNull()
		restartExclusiveClaimIds.clear()
		completedFrozen.clear()
		frozenDomain = null
		return ReaderPortCommandResult.Accepted
	}

	fun snapshot(): ReaderForegroundWebViewOwnershipSnapshot =
		ReaderForegroundWebViewOwnershipSnapshot(
			passiveOwners = if (passiveLease == null) 0 else 1,
			liveClaims = liveClaims.size,
			restorationCallbacks = if (restorationLeaseId == null) 0 else 1,
			closed = closed
		)

	fun close() {
		if (closed) return
		closed = true
		passiveLease = null
		passiveActivationToken = null
		cancelAndRestore = null
		restorationLeaseId = null
		restorationActivationToken = null
		exclusiveClaimId = null
		currentMutationClaimId = null
		val invalidatedClaims = liveClaims.values.toList()
		liveClaims.clear()
		retiredClaimTerminals.clear()
		invalidatedClaims.forEach { state ->
			if (state.terminal == null) {
				deliver(state, ReaderForegroundWebViewLiveReadiness.Invalidated)
			}
		}
	}

	private fun completeRestoration(
		leaseId: Long,
		restoration: ReaderPageRasterCancellationRestoration
	) {
		if (closed || restorationLeaseId != leaseId) return
		if (frozenDomain != null) restorationActivationToken?.let(completedFrozen::add)
		restorationLeaseId = null
		restorationActivationToken = null
		if (restoration == ReaderPageRasterCancellationRestoration.Restored) {
			if (liveClaims.isEmpty()) {
				publishPassiveAvailable()
				return
			}
			publishReadyClaims()
			return
		}

		currentMutationClaimId = null
		exclusiveClaimId = null
		val failedClaims = liveClaims.values.toList()
		liveClaims.clear()
		val terminal = ReaderForegroundWebViewLiveReadiness.Failed(restoration)
		val availabilityVersionBeforeCallbacks = passiveAvailabilityVersion
		val stagedDeliveries = failedClaims.map { state ->
			state.terminal = terminal
			retiredClaimTerminals[state.claim.claimId] =
				RetiredClaimTerminal(state.claim, terminal)
			val callbacks = state.callbacks.toList()
			state.callbacks.clear()
			state.claim to callbacks
		}
		stagedDeliveries.forEach { (claim, callbacks) ->
			callbacks.forEach { owned -> owned.callback(terminal) }
			if (callbacks.isNotEmpty()) {
				retiredClaimTerminals.remove(claim.claimId)
			}
		}
		if (
			canAcquirePassive() &&
			passiveAvailabilityVersion == availabilityVersionBeforeCallbacks
		) {
			publishPassiveAvailable()
		}
	}

	private fun publishReadyClaims() {
		if (
			closed ||
			restorationLeaseId != null ||
			passiveLease != null
		) return
		val exclusiveId = exclusiveClaimId
		if (exclusiveId != null) {
			val hasPrecedingClaim = liveClaims.any { (claimId, state) ->
				claimId != exclusiveId && !state.blockedByExclusiveClaim
			}
			if (!hasPrecedingClaim) {
				liveClaims[exclusiveId]?.let { state ->
					deliver(
						state,
						ReaderForegroundWebViewLiveReadiness.Ready
					)
				}
			}
			return
		}
		liveClaims.values.toList().forEach { state ->
			deliver(state, ReaderForegroundWebViewLiveReadiness.Ready)
		}
	}

	private fun deliver(
		state: LiveClaimState,
		terminal: ReaderForegroundWebViewLiveReadiness
	) {
		if (state.terminal != null) return
		state.terminal = terminal
		val callbacks = state.callbacks.toList()
		state.callbacks.clear()
		callbacks.forEach { owned -> owned.callback(terminal) }
	}

	private fun publishPassiveAvailable() {
		passiveAvailabilityVersion = Math.incrementExact(
			passiveAvailabilityVersion
		)
		onPassiveAvailable()
	}

	private fun nextMutationGeneration(): Long? {
		val next = Math.incrementExact(mutationGeneration)
		return next.takeIf {
			it <= ReaderPageTurnPresentationMaximumSafeInteger
		}
	}

	private fun nextPositiveId(current: Long): Long {
		val next = Math.incrementExact(current)
		check(next in 1L..ReaderPageTurnPresentationMaximumSafeInteger) {
			"Foreground WebView ownership identifier exhausted"
		}
		return next
	}
}
