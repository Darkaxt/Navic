package paige.navic.ui.screens.reader

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
	private val onPassiveMutationReleased: () -> Unit = {},
	private val onPassiveAvailable: () -> Unit = {}
) {
	private data class LiveClaimState(
		val claim: ReaderForegroundWebViewLiveClaim,
		val blockedByExclusiveClaim: Boolean,
		var terminal: ReaderForegroundWebViewLiveReadiness?,
		val callbacks: MutableList<(ReaderForegroundWebViewLiveReadiness) -> Unit> =
			mutableListOf()
	)

	private data class RetiredClaimTerminal(
		val claim: ReaderForegroundWebViewLiveClaim,
		val terminal: ReaderForegroundWebViewLiveReadiness
	)

	private var mutationGeneration = 0L
	private var nextLeaseId = 0L
	private var nextClaimId = 0L
	private var passiveLease: ReaderForegroundWebViewPassiveLease? = null
	private var cancelAndRestore: (
		(
			(ReaderPageRasterCancellationRestoration) -> Unit
		) -> Unit
	)? = null
	private var restorationLeaseId: Long? = null
	private val liveClaims = linkedMapOf<Long, LiveClaimState>()
	private val retiredClaimTerminals =
		linkedMapOf<Long, RetiredClaimTerminal>()
	private var exclusiveClaimId: Long? = null
	private var currentMutationClaimId: Long? = null
	private var passiveAvailabilityVersion = 0L
	private var closed = false

	fun canAcquirePassive(): Boolean =
		!closed &&
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
	): ReaderForegroundWebViewLiveClaim {
		check(!closed) { "Foreground WebView ownership is closed" }
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
			}
		)
		if (exclusive) exclusiveClaimId = claimId

		val preemptedLease = passiveLease ?: return claim
		val preemption = checkNotNull(cancelAndRestore)
		passiveLease = null
		cancelAndRestore = null
		restorationLeaseId = preemptedLease.leaseId
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
		if (closed) {
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
			state.callbacks += callback
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
		passiveLease = null
		cancelAndRestore = null
		onPassiveMutationReleased()
		return true
	}

	fun releaseLive(claim: ReaderForegroundWebViewLiveClaim): Boolean {
		if (closed) return false
		val state = liveClaims[claim.claimId]
		if (state?.claim != claim) return false
		liveClaims.remove(claim.claimId)
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
		cancelAndRestore = null
		restorationLeaseId = null
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
		restorationLeaseId = null
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
			callbacks.forEach { callback -> callback(terminal) }
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
		callbacks.forEach { callback -> callback(terminal) }
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
