package paige.navic.ui.screens.reader

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
	private var closed = false
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	fun acquire(value: T, onAbandoned: () -> Unit): Lease<T>? = synchronized(lock) {
		if (closed || frozenDomain != null) return@synchronized null
		retain(value)
		Lease(value, onAbandoned, tokenAllocator.allocate()).also(pending::add)
	}

	fun claim(lease: Lease<T>): Lease<T>? = synchronized(lock) {
		if (frozenDomain != null) null
		else if (pending.remove(lease)) lease else null
	}

	fun complete(lease: Lease<T>) {
		check(!lease.released) { "Pending callback owner was released twice" }
		lease.released = true
		release(lease.value)
	}

	fun abandon(lease: Lease<T>) {
		var failure: Throwable? = null
		try {
			complete(lease)
		} catch (next: Throwable) {
			failure = next
		}
		try {
			lease.onAbandoned()
		} catch (next: Throwable) {
			val first = failure
			if (first == null) failure = next
			else if (next !== first) first.addSuppressed(next)
		}
		failure?.let { throw it }
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		when {
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
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		pending.map { lease ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
					sourceLocalToken = lease.activationToken
				),
				kind = ReaderTransitionResourceKind.CallbackRegistration,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Pending,
				state = ReaderLegacyResourceState.Registered,
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val lease = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source !=
				ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
			) {
				return@synchronized null
			}
			pending.firstOrNull { owned ->
				owned.activationToken == physicalIdentity.sourceLocalToken
			}?.also(pending::remove)
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		abandon(lease)
		onConfirmed(physicalIdentity)
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (frozenDomain != domain || closed) {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		} else {
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

	fun pendingCount(): Int = synchronized(lock) { pending.size }

	private fun drain(close: Boolean) {
		val leases = synchronized(lock) {
			if (close) closed = true
			pending.toList().also { pending.clear() }
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
		failure?.let { throw it }
	}
}
