package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind

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
	data class Failed(val restoration: ReaderPageRasterCancellationRestoration) :
		ReaderForegroundWebViewLiveReadiness
	data object Invalidated : ReaderForegroundWebViewLiveReadiness
}

internal data class ReaderForegroundWebViewOwnershipSnapshot(
	val passiveOwners: Int,
	val liveClaims: Int,
	val restorationCallbacks: Int,
	val closed: Boolean
)

/** Main-thread ownership. External work and recipients never run inside the physical registry lock. */
internal class ReaderForegroundWebViewOwnership(
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator = ReaderLegacySourceLocalTokenAllocator(),
	private val onPassiveMutationReleased: () -> Unit = {},
	private val onPassiveAvailable: () -> Unit = {}
) {
	private class OwnedReadinessCallback(
		val callback: (ReaderForegroundWebViewLiveReadiness) -> Unit
	) {
		lateinit var owner: ReaderExactPhysicalOwnerRegistry.Owner
		var running = false
		var parked = false
		var delivered = false
	}

	private class LiveClaimState(
		val claim: ReaderForegroundWebViewLiveClaim,
		val blockedByExclusiveClaim: Boolean,
		var terminal: ReaderForegroundWebViewLiveReadiness?
	) {
		lateinit var owner: ReaderExactPhysicalOwnerRegistry.Owner
		val callbacks = mutableListOf<OwnedReadinessCallback>()
		// A generation is a reservation. Only an adjacent primitive fence marks actual work.
		var mutationStarted = false
		var invocationDepth = 0
		val results = mutableListOf<OwnedResult>()
		var continuation: LiveContinuation? = null
		// Accepted terminal demand survives removal from live admission, without mutation permission.
		var afterRelease: (() -> Unit)? = null
		var parked = false
		var released = false
		var returningAcquisition = false
	}

	private class PassiveState(
		val lease: ReaderForegroundWebViewPassiveLease,
		val cancelAndRestore: ((ReaderPageRasterCancellationRestoration) -> Unit) -> Unit,
		val owner: ReaderExactPhysicalOwnerRegistry.Owner
	)

	private class RestorationState(
		val passiveOwner: ReaderExactPhysicalOwnerRegistry.Owner,
		val leaseId: Long,
		val owner: ReaderExactPhysicalOwnerRegistry.Owner
	) {
		var dispatchingCancellation = true
		var deliveringResult = false
		var result: ReaderPageRasterCancellationRestoration? = null
	}

	private class OwnedResult(val owner: ReaderExactPhysicalOwnerRegistry.Owner) {
		var invoking = true
		var delivering = false
		var terminal = false
	}

	private data class LiveContinuation(
		val claim: ReaderForegroundWebViewLiveClaim,
		val generation: ReaderForegroundWebViewMutationGeneration,
		val invoke: () -> Unit
	)

	/** Values only: no mutable physical state handles in the pre-drain logical checkpoint. */
	private data class RestartClaim(
		val claim: ReaderForegroundWebViewLiveClaim,
		val terminal: ReaderForegroundWebViewLiveReadiness?,
		val recipients: List<(ReaderForegroundWebViewLiveReadiness) -> Unit>,
		val continuation: LiveContinuation?,
		val afterRelease: (() -> Unit)?
	)
	private data class RestartCheckpoint(
		val passiveLease: ReaderForegroundWebViewPassiveLease?,
		val restorationLeaseId: Long?,
		val claims: List<RestartClaim>,
		val exclusiveClaimId: Long?,
		val mutationClaimId: Long?
	)
	// Outcomes discovered after freeze never mutate the immutable restart values.
	private val lateClaimOutcomes = linkedMapOf<Long, ReaderForegroundWebViewLiveReadiness>()
	private val lateContinuations = linkedMapOf<Long, LiveContinuation>()
	private val closedTerminalDispositions = linkedMapOf<Long, ReaderForegroundWebViewLiveReadiness>()
	private var destructiveDrain = false

	private val physical = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.ForegroundWebViewOwnership, tokenAllocator
	)
	private var mutationGeneration = 0L
	private var nextLeaseId = 0L
	private var nextClaimId = 0L
	private var passive: PassiveState? = null
	private var restoration: RestorationState? = null
	private val liveClaims = linkedMapOf<Long, LiveClaimState>()
	// A returned failed claim still owes its exact terminal to a later readiness registration.
	private val retiredClaimTerminals = linkedMapOf<Long, LiveClaimState>()
	// Only original accepted recipients/terminal continuations, never live admission or a new generation.
	private val releasedClaimObligations = linkedMapOf<Long, LiveClaimState>()
	private var exclusiveClaimId: Long? = null
	private var currentMutationClaimId: Long? = null
	private var passiveAvailabilityVersion = 0L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var restartCheckpoint: RestartCheckpoint? = null
	private val pendingPassiveReleaseNotifications = mutableListOf<ReaderExactPhysicalOwnerRegistry.Owner>()
	private var restoring = false
	private var closed = false

	fun canAcquirePassive(): Boolean = !closed && frozenDomain == null && !restoring &&
		passive == null && restoration == null && liveClaims.isEmpty()

	fun tryAcquirePassive(
		sessionId: Long,
		cancelAndRestore: ((ReaderPageRasterCancellationRestoration) -> Unit) -> Unit
	): ReaderForegroundWebViewPassiveLease? {
		if (!canAcquirePassive()) return null
		val generation = nextMutationGeneration() ?: return null
		val leaseId = nextPositiveId(nextLeaseId)
		val owner = physical.admit(ownerDescriptor()) ?: return null
		val lease = ReaderForegroundWebViewPassiveLease(
			leaseId, sessionId, ReaderForegroundWebViewMutationGeneration(generation)
		)
		nextLeaseId = leaseId
		mutationGeneration = generation
		currentMutationClaimId = null
		passive = PassiveState(lease, cancelAndRestore, owner)
		return lease
	}

	fun acquireLive(gestureId: Long): ReaderForegroundWebViewLiveClaim = acquireLive(gestureId, false)
	fun acquireExclusiveLive(requestId: Long): ReaderForegroundWebViewLiveClaim = acquireLive(requestId, true)

	private fun acquireLive(gestureId: Long, exclusive: Boolean): ReaderForegroundWebViewLiveClaim {
		check(!closed) { "Foreground WebView ownership is closed" }
		check(frozenDomain == null && !restoring) { "Foreground WebView ownership is frozen" }
		if (exclusive) check(exclusiveClaimId == null) { "Foreground WebView exclusive claim already exists" }
		val claimId = nextPositiveId(nextClaimId)
		val claim = ReaderForegroundWebViewLiveClaim(claimId, gestureId)
		val blocked = !exclusive && exclusiveClaimId != null
		val waits = restoration != null || passive != null || blocked || (exclusive && liveClaims.isNotEmpty())
		val state = LiveClaimState(claim, blocked, if (waits) null else ReaderForegroundWebViewLiveReadiness.Ready)
		state.owner = checkNotNull(physical.admit(ownerDescriptor(
			if (waits) ReaderLegacyResourceState.Reserved else ReaderLegacyResourceState.Running
		)) { parkClaim(state) })
		nextClaimId = claimId
		liveClaims[claimId] = state
		if (exclusive) exclusiveClaimId = claimId
		val preempted = passive ?: return claim
		val restorationOwner = checkNotNull(physical.admit(callbackDescriptor()))
		val restoringLease = RestorationState(preempted.owner, preempted.lease.leaseId, restorationOwner)
		passive = null
		restoration = restoringLease
		currentMutationClaimId = null
		state.returningAcquisition = true
		try {
			preempted.cancelAndRestore { result -> completeRestoration(restoringLease, result) }
		} finally {
			restoringLease.dispatchingCancellation = false
			state.returningAcquisition = false
			settleRestoration(restoringLease)
			settleClaim(state)
		}
		return claim
	}

	fun whenLiveReady(
		claim: ReaderForegroundWebViewLiveClaim,
		callback: (ReaderForegroundWebViewLiveReadiness) -> Unit
	) {
		if (frozenDomain != null || restoring) return // No registration, and no fictitious frozen callback row.
		if (closed) {
			callback(ReaderForegroundWebViewLiveReadiness.Invalidated)
			return
		}
		val state = (liveClaims[claim.claimId] ?: retiredClaimTerminals[claim.claimId])
			?.takeIf { it.claim == claim }
		if (state == null) {
			callback(ReaderForegroundWebViewLiveReadiness.Invalidated)
			return
		}
		val owned = OwnedReadinessCallback(callback)
		owned.owner = checkNotNull(physical.admit(callbackDescriptor()) {
			if (!owned.running && !owned.delivered) {
				owned.parked = true
				physical.complete(owned.owner)
			}
			true
		})
		state.callbacks += owned
		state.terminal?.let { dispatchReadiness(state, owned, it) }
	}

	fun beginLiveMutation(claim: ReaderForegroundWebViewLiveClaim): ReaderForegroundWebViewMutationGeneration? {
		val state = liveClaims[claim.claimId]
		if (closed || frozenDomain != null || restoring || state?.claim != claim || state.parked ||
			state.terminal != ReaderForegroundWebViewLiveReadiness.Ready || restoration != null) return null
		val generation = nextMutationGeneration() ?: return null
		mutationGeneration = generation
		currentMutationClaimId = claim.claimId
		// Exclusive/current-mutation fields alias this same claim, not additional fictitious owners.
		return ReaderForegroundWebViewMutationGeneration(generation)
	}

	/** Final admission fence. Callers resolve all external authority/provider callbacks first. */
	fun invokeLiveMutation(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration,
		invoke: () -> Unit
	): Boolean = invokeLiveBoundary(claim, generation, mutation = true, invoke)

	fun invokeLivePublication(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration,
		invoke: () -> Unit
	): Boolean = invokeLiveBoundary(claim, generation, mutation = false, invoke)

	private fun invokeLiveBoundary(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration,
		mutation: Boolean,
		invoke: () -> Unit
	): Boolean {
		if (!canStartLive(claim, generation)) return false
		val state = checkNotNull(liveClaims[claim.claimId])
		if (mutation) state.mutationStarted = true
		state.invocationDepth++
		try { invoke() }
		finally { state.invocationDepth--; settleClaim(state) }
		return true
	}

	/** The callback row exists only when a real primitive accepting this callback is invoked. */
	fun <T> invokeLiveMutationWithResult(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration,
		invoke: ((T) -> Unit) -> Unit,
		onResult: (T) -> Unit
	): Boolean {
		if (!canStartLive(claim, generation)) return false
		val state = checkNotNull(liveClaims[claim.claimId])
		val result = OwnedResult(checkNotNull(physical.admit(callbackDescriptor())))
		state.results += result
		state.mutationStarted = true
		state.invocationDepth++
		try {
			invoke { value ->
				if (!result.terminal) {
					result.terminal = true
					result.delivering = true
					try { onResult(value) }
					finally { result.delivering = false; settleResult(state, result) }
				}
			}
		} catch (failure: Throwable) {
			result.terminal = true // The registration failed, not a synthetic async completion.
			throw failure
		} finally {
			result.invoking = false
			state.invocationDepth--
			settleResult(state, result)
			settleClaim(state)
		}
		return true
	}

	private fun settleResult(state: LiveClaimState, result: OwnedResult) {
		if (!result.terminal || result.invoking || result.delivering) return
		physical.complete(result.owner)
		state.results.remove(result)
		settleClaim(state)
	}

	private fun canStartLive(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration
	): Boolean = !closed && frozenDomain == null && !restoring && isCurrent(claim, generation)

	/** Caller has settled its preceding phase; only an UNSTARTED continuation is parked. */
	fun deferLiveContinuation(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration,
		invoke: () -> Unit
	): Boolean {
		val state = liveClaims[claim.claimId]?.takeIf { it.claim == claim && !it.released } ?: return false
		if (closed || currentMutationClaimId != claim.claimId || generation.value != mutationGeneration) return false
		if (state.continuation == null) state.continuation = LiveContinuation(claim, generation, invoke)
		if (frozenDomain != null) lateContinuations[claim.claimId] = checkNotNull(state.continuation)
		state.mutationStarted = false
		settleClaim(state)
		return true
	}

	fun isFrozenForTransitionActivation(): Boolean = frozenDomain != null || restoring

	// Physical currency remains available to accepted pre-fence cleanup and result tails.
	fun isCurrent(lease: ReaderForegroundWebViewPassiveLease): Boolean = !closed &&
		passive?.lease == lease && lease.mutationGeneration.value == mutationGeneration

	fun isCurrent(claim: ReaderForegroundWebViewLiveClaim, generation: ReaderForegroundWebViewMutationGeneration): Boolean {
		val state = liveClaims[claim.claimId]
		return !closed && state?.claim == claim && !state.released && !state.parked &&
			state.terminal == ReaderForegroundWebViewLiveReadiness.Ready && restoration == null &&
			currentMutationClaimId == claim.claimId && generation.value == mutationGeneration
	}

	fun isMutationGenerationCurrent(value: Long): Boolean = !closed && restoration == null && value == mutationGeneration

	fun releasePassive(lease: ReaderForegroundWebViewPassiveLease): Boolean {
		val state = passive?.takeIf { it.lease == lease } ?: return false
		passive = null
		val notification = if (frozenDomain == null) physical.admit(callbackDescriptor()) else
			physical.admitLateDiscoveredDuringFrozenEpoch(checkNotNull(frozenDomain), callbackDescriptor())
		try {
			if (notification != null && !closed) {
				if (frozenDomain == null) onPassiveMutationReleased()
				else pendingPassiveReleaseNotifications += notification
			}
		} finally {
			if (notification != null && notification !in pendingPassiveReleaseNotifications) physical.complete(notification)
			physical.complete(state.owner)
		}
		return true
	}

	fun releaseLive(claim: ReaderForegroundWebViewLiveClaim): Boolean = releaseLive(claim, null)

	/** Release logical exclusivity before successor demand; already accepted physical tails remain owned. */
	fun releaseLive(claim: ReaderForegroundWebViewLiveClaim, afterRelease: (() -> Unit)?): Boolean {
		val state = liveClaims[claim.claimId]?.takeIf { it.claim == claim && !it.released } ?: return false
		liveClaims.remove(claim.claimId)
		state.released = true
		state.continuation = null
		state.afterRelease = afterRelease
		if (state.callbacks.isNotEmpty() || afterRelease != null) releasedClaimObligations[claim.claimId] = state
		if (exclusiveClaimId == claim.claimId) exclusiveClaimId = null
		if (currentMutationClaimId == claim.claimId) currentMutationClaimId = null
		val version = passiveAvailabilityVersion
		var failure: Throwable? = null
		try {
			try {
				if (state.terminal == null) deliver(state, ReaderForegroundWebViewLiveReadiness.Invalidated)
			} catch (thrown: Throwable) { failure = thrown }
			try { publishReadyClaims() }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
			try {
				if (canAcquirePassive() && passiveAvailabilityVersion == version) publishPassiveAvailable()
			} catch (thrown: Throwable) { if (failure == null) failure = thrown }
			// Final fence AFTER release/availability callbacks. A refreeze parks, rather than consumes, demand.
			try { dispatchReleasedContinuation(state) }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
		} finally {
			settleClaim(state)
		}
		failure?.let { throw it }
		return true
	}

	private fun dispatchReleasedContinuation(state: LiveClaimState) {
		val callback = state.afterRelease ?: return
		if (closed || frozenDomain != null || restoring || releasedClaimObligations[state.claim.claimId] !== state) return
		val owner = physical.admit(callbackDescriptor()) ?: return
		state.afterRelease = null // Consume only at actual entry, independently of any successor generation.
		state.invocationDepth++
		try { callback() }
		finally {
			state.invocationDepth--
			physical.complete(owner)
			settleClaim(state)
		}
	}

	fun freezeForTransitionActivation(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		if (frozenDomain != null) return if (frozenDomain == domain) ReaderPortCommandResult.Accepted else invalidResource()
		frozenDomain = domain
		val states = (liveClaims.values + retiredClaimTerminals.values + releasedClaimObligations.values).distinct()
		destructiveDrain = false
		lateClaimOutcomes.clear()
		lateContinuations.clear()
		restartCheckpoint = RestartCheckpoint(passive?.lease, restoration?.leaseId, states.map { state ->
			RestartClaim(state.claim, state.terminal, state.callbacks.map { it.callback }, state.continuation, state.afterRelease)
		}, exclusiveClaimId, currentMutationClaimId)
		return physical.freezeForTransitionActivation(domain)
	}

	fun connectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? = physical.connectedFrozenOwnership()
	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = physical.snapshotFrozenOwnership()

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		// Passive mutations and restoration have no separate cancellation/physical restart hook here.
		// Their rows stay pending until their real producer calls release/terminal, never a reference clear.
		if (physical.snapshotFrozenOwnership().any {
			it.physicalIdentity == physicalIdentity && it.state != ReaderLegacyResourceState.ReleaseRequested
		}) destructiveDrain = true
		val result = physical.drainFrozenOwnership(physicalIdentity, onConfirmed)
		if (result == ReaderPortCommandResult.Accepted) {
			pendingPassiveReleaseNotifications.firstOrNull { it.token == physicalIdentity.sourceLocalToken }
				?.let(physical::complete)
		}
		return result
	}

	fun restoreAfterTransitionActivation(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		if (closed || restoring || frozenDomain != domain) return invalidResource()
		val rows = physical.snapshotFrozenOwnership()
		// Untouched freeze cancellation is distinct from rebuilding after destructive drain.
		// Once drain begins EVERY actual obligation, not merely requested rows, must be settled exactly.
		if (destructiveDrain && rows.isNotEmpty()) return invalidResource()
		if (rows.any { it.state == ReaderLegacyResourceState.Released ||
			it.state == ReaderLegacyResourceState.ReleaseRequested }) return invalidResource()
		val result = physical.restoreAfterTransitionActivation(domain)
		if (result != ReaderPortCommandResult.Accepted) return result
		restoring = true
		try {
			val checkpoint = restartCheckpoint
			checkpoint?.claims?.forEach { restart ->
				val state = (liveClaims[restart.claim.claimId] ?: retiredClaimTerminals[restart.claim.claimId]
					?: releasedClaimObligations[restart.claim.claimId])
					?.takeIf { it.claim == restart.claim } ?: return@forEach
				state.terminal = lateClaimOutcomes[restart.claim.claimId] ?: restart.terminal ?: state.terminal
				if (!state.released) {
					state.continuation = lateContinuations[restart.claim.claimId] ?: state.continuation ?: restart.continuation
				} else state.afterRelease = state.afterRelease ?: restart.afterRelease
				if (state.parked) {
					if (!state.released) state.owner = checkNotNull(physical.admit(ownerDescriptor()) { parkClaim(state) })
					state.parked = false
				}
				state.callbacks.filter { it.parked && !it.delivered }.forEach { owned ->
					owned.owner = checkNotNull(physical.admit(callbackDescriptor()) {
						if (!owned.running && !owned.delivered) {
							owned.parked = true
							physical.complete(owned.owner)
						}
						true
					})
					owned.parked = false
				}
			}
			restartCheckpoint = null
			lateClaimOutcomes.clear()
			lateContinuations.clear()
			destructiveDrain = false
			frozenDomain = null
		} finally {
			restoring = false
		}
		var failure: Throwable? = null
		pendingPassiveReleaseNotifications.toList().forEach { notification ->
			if (closed || frozenDomain != null) return@forEach
			try {
				// Consume only immediately before actual invocation. Reentrant freeze preserves later demand.
				pendingPassiveReleaseNotifications.remove(notification)
				dispatchNotification(onPassiveMutationReleased)
			} catch (thrown: Throwable) { if (failure == null) failure = thrown }
			finally { physical.complete(notification) }
		}
		try { publishReadyClaims() } catch (thrown: Throwable) { if (failure == null) failure = thrown }
		retiredClaimTerminals.values.toList().forEach { state ->
			try { state.terminal?.let { deliver(state, it) } }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
		}
		releasedClaimObligations.values.toList().forEach { state ->
			try { state.terminal?.let { deliver(state, it) } }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
			try { dispatchReleasedContinuation(state) }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
		}
		liveClaims.values.toList().forEach { state ->
			val continuation = state.continuation ?: return@forEach
			try {
				invokeLivePublication(continuation.claim, continuation.generation) {
					state.continuation = null
					continuation.invoke()
				}
			} catch (thrown: Throwable) { if (failure == null) failure = thrown }
		}
		failure?.let { throw it }
		return ReaderPortCommandResult.Accepted
	}

	fun snapshot(): ReaderForegroundWebViewOwnershipSnapshot = ReaderForegroundWebViewOwnershipSnapshot(
		if (passive == null) 0 else 1, liveClaims.size, if (restoration == null || closed) 0 else 1, closed
	)

	fun close() {
		if (closed) return
		closed = true
		exclusiveClaimId = null
		currentMutationClaimId = null
		val states = (liveClaims.values + retiredClaimTerminals.values + releasedClaimObligations.values).distinct()
		liveClaims.clear()
		retiredClaimTerminals.clear()
		releasedClaimObligations.clear()
		var failure: Throwable? = null
		states.forEach { state ->
			state.released = true
			state.continuation = null
			state.afterRelease = null
			closedTerminalDispositions[state.claim.claimId] = ReaderForegroundWebViewLiveReadiness.Invalidated
			try { deliver(state, ReaderForegroundWebViewLiveReadiness.Invalidated, closing = true) }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
			finally { settleClaim(state) }
		}
		// An outstanding cancellation callback remains physical until it actually returns.
		passive?.let { /* No physical cancellation hook for permanent close; retain until releasePassive. */ }
		restartCheckpoint = null
		lateClaimOutcomes.clear()
		lateContinuations.clear()
		pendingPassiveReleaseNotifications.forEach(physical::complete)
		pendingPassiveReleaseNotifications.clear()
		failure?.let { throw it }
	}

	private fun completeRestoration(state: RestorationState, result: ReaderPageRasterCancellationRestoration) {
		if (restoration !== state || state.result != null) return
		state.result = result
		state.deliveringResult = true
		// Clear the lease for legitimate pre-fence synchronous readiness, but retain its physical row.
		restoration = null
		try {
			if (closed) return
			if (result == ReaderPageRasterCancellationRestoration.Restored) {
				publishReadyClaims()
				if (liveClaims.isEmpty() && canAcquirePassive()) publishPassiveAvailable()
			} else {
				currentMutationClaimId = null
				exclusiveClaimId = null
				val failed = liveClaims.values.toList()
				liveClaims.clear()
				val terminal = ReaderForegroundWebViewLiveReadiness.Failed(result)
				failed.forEach { claim ->
					claim.terminal = terminal
					retiredClaimTerminals[claim.claim.claimId] = claim
				}
				var failure: Throwable? = null
				failed.forEach { claim ->
					try { deliver(claim, terminal) }
					catch (thrown: Throwable) { if (failure == null) failure = thrown }
				}
				if (canAcquirePassive()) publishPassiveAvailable()
				failure?.let { throw it }
			}
		} finally {
			state.deliveringResult = false
			settleRestoration(state)
		}
	}

	private fun settleRestoration(state: RestorationState) {
		if (state.result != null && !state.dispatchingCancellation && !state.deliveringResult) {
			physical.complete(state.owner)
			physical.complete(state.passiveOwner)
		}
	}

	private fun parkClaim(state: LiveClaimState): Boolean {
		if (!state.mutationStarted && !state.returningAcquisition && state.invocationDepth == 0 &&
			state.results.isEmpty() && state.callbacks.none { it.running }) {
			state.parked = true
			physical.complete(state.owner)
		}
		return true
	}

	private fun settleClaim(state: LiveClaimState) {
		if (state.returningAcquisition || state.invocationDepth != 0 || state.results.isNotEmpty() ||
			state.callbacks.any { it.running }) return
		if (state.released || (retiredClaimTerminals[state.claim.claimId] !== state &&
			liveClaims[state.claim.claimId] !== state)) physical.complete(state.owner)
		else if (state.owner.drainRequested && !state.mutationStarted) parkClaim(state)
		if (state.released && state.callbacks.isEmpty() && state.afterRelease == null &&
			releasedClaimObligations[state.claim.claimId] === state) releasedClaimObligations.remove(state.claim.claimId)
	}

	private fun publishReadyClaims() {
		if (closed || restoration != null || passive != null) return
		val exclusiveId = exclusiveClaimId
		val states = if (exclusiveId != null) {
			if (liveClaims.any { (id, state) -> id != exclusiveId && !state.blockedByExclusiveClaim }) emptyList()
			else listOfNotNull(liveClaims[exclusiveId])
		} else liveClaims.values.toList()
		var failure: Throwable? = null
		states.forEach { state ->
			try { deliver(state, ReaderForegroundWebViewLiveReadiness.Ready) }
			catch (thrown: Throwable) { if (failure == null) failure = thrown }
		}
		failure?.let { throw it }
	}

	private fun deliver(state: LiveClaimState, terminal: ReaderForegroundWebViewLiveReadiness, closing: Boolean = false) {
		if (state.terminal == null || closing) state.terminal = terminal
		if (frozenDomain != null && !closed) lateClaimOutcomes[state.claim.claimId] = checkNotNull(state.terminal)
		var failure: Throwable? = null
		state.callbacks.toList().forEach { owned ->
			try {
				if (closing && !owned.running && !owned.delivered) {
					if (frozenDomain == null) dispatchReadiness(state, owned, terminal, closing = true)
					else { owned.delivered = true; state.callbacks.remove(owned); physical.complete(owned.owner) }
				} else dispatchReadiness(state, owned, checkNotNull(state.terminal))
			} catch (thrown: Throwable) { if (failure == null) failure = thrown }
		}
		failure?.let { throw it }
	}

	private fun dispatchReadiness(
		state: LiveClaimState, owned: OwnedReadinessCallback,
		terminal: ReaderForegroundWebViewLiveReadiness, closing: Boolean = false
	) {
		if (owned.delivered || owned.running || owned.parked || (state.parked && !state.released) || frozenDomain != null ||
			(closed && !closing)) return
		owned.running = true
		try { owned.callback(terminal) }
		finally {
			owned.running = false
			owned.delivered = true
			state.callbacks.remove(owned)
			physical.complete(owned.owner)
			if (retiredClaimTerminals[state.claim.claimId] === state && state.callbacks.isEmpty()) {
				retiredClaimTerminals.remove(state.claim.claimId)
			}
			settleClaim(state)
		}
	}

	private fun publishPassiveAvailable() {
		if (!canAcquirePassive()) return
		passiveAvailabilityVersion = Math.incrementExact(passiveAvailabilityVersion)
		dispatchNotification(onPassiveAvailable)
	}

	private fun dispatchNotification(callback: () -> Unit) {
		if (closed || frozenDomain != null) return
		val owner = physical.admit(callbackDescriptor()) ?: return
		try { callback() } finally { physical.complete(owner) }
	}

	private fun ownerDescriptor(state: ReaderLegacyResourceState = ReaderLegacyResourceState.Running) =
		ReaderExactPhysicalOwnerDescriptor(ReaderTransitionResourceKind.FrameHandoff, state = state)
	private fun callbackDescriptor() = ReaderExactPhysicalOwnerDescriptor(
		ReaderTransitionResourceKind.CallbackRegistration,
		origin = ReaderLegacyResourceOrigin.Pending, state = ReaderLegacyResourceState.Registered
	)
	private fun invalidResource(): ReaderPortCommandResult = ReaderPortCommandResult.Rejected(
		ReaderTransitionFailureReason.InvalidLegacyResource
	)
	private fun nextMutationGeneration(): Long? = Math.incrementExact(mutationGeneration).takeIf {
		it <= ReaderPageTurnPresentationMaximumSafeInteger
	}
	private fun nextPositiveId(current: Long): Long = Math.incrementExact(current).also {
		check(it in 1L..ReaderPageTurnPresentationMaximumSafeInteger) {
			"Foreground WebView ownership identifier exhausted"
		}
	}
}
