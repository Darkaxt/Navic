package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind

internal class ReaderPagePendingCallbackOwners<T : Any>(
	private val retain: (T) -> Unit,
	private val release: (T) -> Unit,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) {
	internal class Lease<T : Any> internal constructor(
		internal val value: T,
		internal val onAbandoned: () -> Unit,
		internal val activationToken: ReaderLegacySourceLocalOpaqueToken
	) {
		internal var released = false
	}

	private val lock = Any()
	private val pending = linkedSetOf<Lease<T>>()
	private val claimed = linkedSetOf<Lease<T>>()
	private val pendingDrainConfirmations =
		linkedMapOf<ReaderLegacySourceLocalOpaqueToken, () -> Unit>()
	private val releasedFrozenOwnership =
		linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var closed = false
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	fun acquire(value: T, onAbandoned: () -> Unit): Lease<T>? = synchronized(lock) {
		if (closed || frozenDomain != null) return@synchronized null
		retain(value)
		Lease(value, onAbandoned, tokenAllocator.allocate()).also(pending::add)
	}

	fun claim(lease: Lease<T>): Lease<T>? = synchronized(lock) {
		if (frozenDomain != null) {
			null
		} else if (pending.remove(lease)) {
			lease.also(claimed::add)
		} else {
			null
		}
	}

	fun complete(lease: Lease<T>) {
		releaseLease(lease, notifyAbandoned = false)
	}

	fun abandon(lease: Lease<T>) {
		releaseLease(lease, notifyAbandoned = true)
	}

	private fun releaseLease(lease: Lease<T>, notifyAbandoned: Boolean) {
		val completesClaimedOwnership = synchronized(lock) {
			check(!lease.released) { "Pending callback owner was released twice" }
			lease.released = true
			lease in claimed
		}
		var failure: Throwable? = null
		try {
			release(lease.value)
		} catch (next: Throwable) {
			failure = next
		}
		if (notifyAbandoned) {
			try {
				lease.onAbandoned()
			} catch (next: Throwable) {
				val first = failure
				if (first == null) failure = next
				else if (next !== first) first.addSuppressed(next)
			}
		}
		var completeClosed = false
		val confirmation = if (completesClaimedOwnership) {
			synchronized(lock) {
				claimed.remove(lease)
				pendingDrainConfirmations.remove(lease.activationToken).also { pendingConfirmation ->
					if (pendingConfirmation == null && frozenDomain != null) {
						releasedFrozenOwnership += lease.activationToken
					}
					completeClosed = closed && pending.isEmpty() && claimed.isEmpty()
				}
			}
		} else {
			null
		}
		runCatching { confirmation?.invoke() }
		if (completeClosed) closedSignal.complete(Unit)
		failure?.let { throw it }
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
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

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		buildList {
			fun addRow(
				token: ReaderLegacySourceLocalOpaqueToken,
				state: ReaderLegacyResourceState
			) {
				add(
					ReaderFrozenLegacyResource(
						freezeToken = domain.freezeToken,
						physicalIdentity = ReaderLegacyPhysicalIdentity(
							domain = domain,
							source =
								ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
							sourceLocalToken = token
						),
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						binding = null,
						visibleOwner = null,
						origin = ReaderLegacyResourceOrigin.Pending,
						state = if (token in pendingDrainConfirmations) {
							ReaderLegacyResourceState.ReleaseRequested
						} else {
							state
						},
						mayBeCommittedPredecessor = false
					)
				)
			}
			pending.forEach { lease ->
				addRow(lease.activationToken, ReaderLegacyResourceState.Registered)
			}
			claimed.forEach { lease ->
				addRow(lease.activationToken, ReaderLegacyResourceState.Registered)
			}
			releasedFrozenOwnership.forEach { token ->
				addRow(token, ReaderLegacyResourceState.Released)
			}
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		var leaseToAbandon: Lease<T>? = null
		var confirmNow = false
		val accepted = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source !=
				ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
			) {
				return@synchronized false
			}
			val token = physicalIdentity.sourceLocalToken
			if (token in pendingDrainConfirmations) return@synchronized false
			if (releasedFrozenOwnership.remove(token)) {
				confirmNow = true
				return@synchronized true
			}
			val pendingLease = pending.firstOrNull { owned ->
				owned.activationToken == token
			}
			if (pendingLease != null) {
				pending.remove(pendingLease)
				claimed += pendingLease
				pendingDrainConfirmations[token] = { onConfirmed(physicalIdentity) }
				leaseToAbandon = pendingLease
				return@synchronized true
			}
			if (claimed.none { owned -> owned.activationToken == token }) {
				return@synchronized false
			}
			pendingDrainConfirmations[token] = { onConfirmed(physicalIdentity) }
			true
		}
		if (!accepted) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		leaseToAbandon?.let { lease ->
			runCatching { abandon(lease) }
		}
		if (confirmNow) runCatching { onConfirmed(physicalIdentity) }
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (
			frozenDomain != domain ||
			closed ||
			pendingDrainConfirmations.isNotEmpty()
		) {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		} else {
			releasedFrozenOwnership.clear()
			frozenDomain = null
			ReaderPortCommandResult.Accepted
		}
	}

	fun cancelAll() {
		drain(close = false)
	}

	fun close() {
		drain(close = true)
	}

	suspend fun awaitCloseCompletion() {
		closedSignal.await()
	}

	fun pendingCount(): Int = synchronized(lock) { pending.size + claimed.size }

	private fun drain(close: Boolean) {
		val leases = synchronized(lock) {
			if (close) closed = true
			pending.toList().also { removed ->
				pending.clear()
				if (frozenDomain != null) claimed.addAll(removed)
			}
		}
		var failure: Throwable? = null
		leases.forEach { lease ->
			try {
				abandon(lease)
			} catch (next: Throwable) {
				val first = failure
				if (first == null) failure = next
				else if (next !== first) first.addSuppressed(next)
			}
		}
		val completeClosed = synchronized(lock) {
			closed && pending.isEmpty() && claimed.isEmpty()
		}
		if (completeClosed) closedSignal.complete(Unit)
		failure?.let { throw it }
	}
}
