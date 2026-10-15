package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import paige.navic.reader.ReaderSemanticAsyncFailure
import paige.navic.reader.ReaderSemanticTransportBindingResult
import paige.navic.reader.ReaderSemanticTransportRetirement
import paige.navic.reader.acceptsMaterialAllocation
import paige.navic.reader.parentIdentity
import paige.navic.reader.readerTransitionMaterialBindingIsValid
import paige.navic.reader.ReaderMaterialGenerationAllocation
import paige.navic.reader.ReaderOwnedWorkCancellationOutcome
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderBufferedReleaseCallbackState
import paige.navic.reader.ReaderReleaseBufferedViolationStatus
import paige.navic.reader.ReaderReleaseCommandAmbiguityReason
import paige.navic.reader.ReaderReleaseCommandRejectionReason
import paige.navic.reader.ReaderReleasePortContractViolationReason
import paige.navic.reader.ReaderResourceReleaseIssuer
import paige.navic.reader.ReaderSaturatingCallbackCount
import paige.navic.reader.ReaderSemanticCommandSlotId
import paige.navic.reader.ReaderSemanticExecutableRequest
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticMutationStartEvidence
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionCommandRejectionReason
import paige.navic.reader.ReaderTransitionCommandStage
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceProvenance


/** One existing FrameOrHandoff source; partitions route frozen protocol identities,
 * never own material or substitute a presentation authority. */
internal class ReaderFrameOrHandoffPhysicalSource(
	private val material: ReaderFramePhysicalOwnershipAdapter,
	private val publisher: ReaderNativePagePresentationPublisher,
	private val bridge: ReaderPresentationHostBridge
) {
	private var domain: ReaderLegacyPhysicalDomain? = null
	// A domain reservation is not proof that every original owner accepted freezing.
	private var freezeCompleted = false
	private var finalized = false
	private var materialIdentities = emptySet<ReaderLegacyPhysicalIdentity>()
	private var publisherIdentities = emptySet<ReaderLegacyPhysicalIdentity>()
	private var bridgeIdentities = emptySet<ReaderLegacyPhysicalIdentity>()
	internal fun usesBridge(other: ReaderPresentationHostBridge): Boolean = bridge === other
	fun freezeForTransitionActivation(next: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		if (finalized || (domain != null && domain != next)) return invalid()
		if (domain == next && freezeCompleted) return ReaderPortCommandResult.Accepted
		domain = next
		for (freeze in listOf(material::freezeForTransitionActivation, publisher::freezeForTransitionActivation, bridge::freezeForTransitionActivation)) {
			val result = freeze(next); if (result != ReaderPortCommandResult.Accepted) return result
		}
		materialIdentities = material.frozenIdentities(next)
		publisherIdentities = publisher.snapshotFrozenOwnership().map { it.physicalIdentity }.toSet()
		bridgeIdentities = bridge.snapshotFrozenOwnership().filter { it.physicalIdentity.source == ReaderLegacyInventorySource.FrameOrHandoff }
			.map { it.physicalIdentity }.toSet()
		if ((materialIdentities + publisherIdentities + bridgeIdentities).size != materialIdentities.size + publisherIdentities.size + bridgeIdentities.size) return invalid()
		freezeCompleted = true
		return ReaderPortCommandResult.Accepted
	}
	fun snapshotConnectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? {
		val exact = domain ?: return null
		if (!freezeCompleted || finalized) return null
		val parts = listOf(material.snapshotConnectedFrozenOwnership() ?: return null,
			publisher.snapshotConnectedFrozenFrameOwnership() ?: return null,
			bridge.snapshotConnectedFrozenFrameOwnership() ?: return null)
		if (parts.any { it.domain != exact }) return null
		val resources = parts.flatMap { it.resources }
		if (resources.map { it.physicalIdentity }.distinct().size != resources.size) return null
		// A frozen original can discover a callback/material return after the first
		// capture. Preserve its ORIGINAL routing partition through exact retirement.
		val nextMaterial = materialIdentities + parts[0].resources.map { it.physicalIdentity }
		val nextPublisher = publisherIdentities + parts[1].resources.map { it.physicalIdentity }
		val nextBridge = bridgeIdentities + parts[2].resources.map { it.physicalIdentity }
		if ((nextMaterial + nextPublisher + nextBridge).size != nextMaterial.size + nextPublisher.size + nextBridge.size) return null
		materialIdentities = nextMaterial
		publisherIdentities = nextPublisher
		bridgeIdentities = nextBridge
		return ReaderLegacyConnectedSourceInventory(ReaderLegacyInventorySource.FrameOrHandoff, exact, resources)
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		if (domain == null || finalized) return emptyList()
		return material.snapshotFrozenOwnership() + publisher.snapshotFrozenOwnership() +
			bridge.snapshotFrozenOwnership().filter { it.physicalIdentity.source == ReaderLegacyInventorySource.FrameOrHandoff }
	}
	fun drainFrozenOwnership(identity: ReaderLegacyPhysicalIdentity, onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult {
		if (finalized || identity.domain != domain || identity.source != ReaderLegacyInventorySource.FrameOrHandoff ||
			snapshotConnectedFrozenOwnership() == null) return invalid()
		return when (identity) {
			in materialIdentities -> material.drainFrozenOwnership(identity, onConfirmed)
			in publisherIdentities -> publisher.drainFrozenOwnership(identity, onConfirmed)
			in bridgeIdentities -> bridge.drainFrozenOwnership(identity, onConfirmed)
			else -> invalid()
		}
	}
	internal val originalLegacyAdmissionsRestored: Boolean
		get() = !finalized && domain == null && material.admissionOpen &&
			publisher.originalLegacyFrameAdmissionRestored && bridge.originalLegacyAdmissionsRestored

	fun restoreAfterTransitionActivation(next: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		if (finalized || domain != next) return invalid()
		for (restore in listOf(material::restoreAfterTransitionActivation, publisher::restoreAfterTransitionActivation, bridge::restoreAfterTransitionActivation)) {
			val result = restore(next); if (result != ReaderPortCommandResult.Accepted) return result
		}
		domain = null
		freezeCompleted = false
		materialIdentities = emptySet(); publisherIdentities = emptySet(); bridgeIdentities = emptySet()
		return ReaderPortCommandResult.Accepted
	}
	fun finalizeActivatedHandoff(token: ReaderLegacyFreezeToken, selected: ReaderImportedLegacyResourceRegistration?,
		confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>): ReaderPortCommandResult {
		val exact = domain ?: return invalid()
		if (finalized || token != exact.freezeToken || snapshotConnectedFrozenOwnership() == null ||
			(selected != null && selected.physicalIdentity !in materialIdentities)) return invalid()
		val expected = materialIdentities + publisherIdentities + bridgeIdentities - setOfNotNull(selected?.physicalIdentity)
		if (confirmedRetirements != expected || !publisher.frozenFrameRetirementComplete(exact) || !bridge.frozenFrameRetirementComplete(exact)) return invalid()
		val result = material.finalizeActivatedHandoff(exact, selected, confirmedRetirements.intersect(materialIdentities))
		if (result != ReaderPortCommandResult.Accepted) return result
		publisher.completeFrozenFrameRetirement(exact); bridge.completeFrozenFrameRetirement(exact)
		finalized = true
		materialIdentities = emptySet(); publisherIdentities = emptySet(); bridgeIdentities = emptySet()
		return ReaderPortCommandResult.Accepted
	}
	fun releaseLegacy(command: ReaderTransitionCommand.ReleaseResource, imported: ReaderImportedLegacyResourceRegistration,
		onConfirmed: (ReaderLegacyPhysicalIdentity, ReaderTransitionFact.ResourceReleased) -> Unit): ReaderPortCommandResult =
		if (finalized) material.releaseLegacy(command, imported, onConfirmed) else invalid()
	private fun invalid() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
}

internal enum class ReaderTransitionMode { Shadow, Active }

internal fun interface ReaderTransitionClockRegistration {
	fun cancel()
}

internal interface ReaderTransitionClock {
	fun nowMillis(): Long
	fun schedule(atMillis: Long, action: () -> Unit): ReaderTransitionClockRegistration?
}

internal class ReaderPhysicalReleaseCommandAdapter {
	private enum class Completion { Calling, Accepted, Rejected, Threw }

	private class Invocation(
		val command: ReaderTransitionCommand.ReleaseResource,
		val onFact: (ReaderTransitionFact) -> Unit
	) {
		var completion = Completion.Calling
		var callbacks = ReaderBufferedReleaseCallbackState(
			latestAuthoritativeConfirmation = null,
			callbackCount = ReaderSaturatingCallbackCount.Zero,
			violationStatus = ReaderReleaseBufferedViolationStatus.None
		)
		var publishedCallbackCount = ReaderSaturatingCallbackCount.Zero
		var completionWithoutCallbackPublished = false
	}

	fun dispatchGeneric(
		command: ReaderTransitionCommand.ReleaseResource,
		invoke: ((ReaderTransitionFact.ResourceReleased) -> Unit) -> ReaderPortCommandResult,
		onFact: (ReaderTransitionFact) -> Unit
	) = dispatch(command, invoke, onFact)

	fun dispatchLegacy(
		dispatch: ReaderImportedLegacyReleaseDispatch,
		invoke: ((ReaderLegacyPhysicalIdentity, ReaderTransitionFact.ResourceReleased) -> Unit) -> ReaderPortCommandResult,
		onFact: (ReaderTransitionFact) -> Unit
	) = dispatch(
		command = dispatch.command,
		invoke = { callback ->
			invoke { physical, fact ->
				if (physical == dispatch.imported.physicalIdentity) callback(fact)
			}
		},
		onFact = onFact
	)

	private fun dispatch(
		command: ReaderTransitionCommand.ReleaseResource,
		invoke: ((ReaderTransitionFact.ResourceReleased) -> Unit) -> ReaderPortCommandResult,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		val invocation = Invocation(command, onFact)
		val result = try {
			invoke { confirmation -> recordCallback(invocation, confirmation) }
		} catch (_: Throwable) {
			complete(invocation, Completion.Threw)
			return
		}
		complete(
			invocation,
			if (result == ReaderPortCommandResult.Accepted) Completion.Accepted else Completion.Rejected
		)
	}

	private fun recordCallback(
		invocation: Invocation,
		confirmation: ReaderTransitionFact.ResourceReleased
	) {
		if (confirmation.identity != invocation.command.identity) return
		val publications = synchronized(invocation) {
			invocation.callbacks = invocation.callbacks.record(confirmation)
			if (invocation.completion == Completion.Calling) emptyList() else publications(invocation, late = true)
		}
		publish(invocation, publications)
	}

	private fun complete(invocation: Invocation, completion: Completion) {
		val publications = synchronized(invocation) {
			if (invocation.completion != Completion.Calling) return
			invocation.completion = completion
			publications(invocation, late = false)
		}
		publish(invocation, publications)
	}

	private fun publish(invocation: Invocation, publications: List<ReaderTransitionFact>) {
		publications.forEach { fact ->
			try {
				invocation.onFact(fact)
			} catch (_: Throwable) {
				// Physical callbacks are total. Production ingress is non-throwing and owns
				// durable classification; an invalid external sink cannot escape the callback.
			}
		}
	}

	private fun publications(invocation: Invocation, late: Boolean): List<ReaderTransitionFact> {
		val callback = invocation.callbacks.latestAuthoritativeConfirmation
		val count = invocation.callbacks.callbackCount
		if (callback == null) {
			if (invocation.completionWithoutCallbackPublished) return emptyList()
			val publication = when (invocation.completion) {
				Completion.Rejected -> ReaderTransitionFact.ReleaseCommandRejected(
					invocation.command.cleanupKey,
					invocation.command.identity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
				Completion.Threw -> ReaderTransitionFact.ReleaseCommandThrew(
					invocation.command.cleanupKey,
					invocation.command.identity,
					ReaderReleaseCommandAmbiguityReason.ThrowAfterPhysicalEffectMayHaveOccurred
				)
				else -> null
			}
			if (publication != null) invocation.completionWithoutCallbackPublished = true
			return listOfNotNull(publication)
		}
		invocation.callbacks = invocation.callbacks.copy(latestAuthoritativeConfirmation = null)
		val priorPublishedCount = invocation.publishedCallbackCount
		if (count == priorPublishedCount) return emptyList()
		invocation.publishedCallbackCount = count
		val firstConfirmation = priorPublishedCount == ReaderSaturatingCallbackCount.Zero
		val completionViolation = when {
			invocation.completion == Completion.Rejected && late ->
				ReaderReleasePortContractViolationReason.LateCallbackAfterRejected
			invocation.completion == Completion.Threw && late ->
				ReaderReleasePortContractViolationReason.LateCallbackAfterAmbiguousFailure
			invocation.completion == Completion.Rejected ->
				ReaderReleasePortContractViolationReason.CallbackThenRejected
			invocation.completion == Completion.Threw ->
				ReaderReleasePortContractViolationReason.CallbackThenThrew
			else -> null
		}
		val duplicate = count != ReaderSaturatingCallbackCount.One
		val violation = when {
			duplicate && completionViolation != null ->
				ReaderReleasePortContractViolationReason.MultipleViolations
			duplicate -> ReaderReleasePortContractViolationReason.DuplicateConfirmation
			else -> completionViolation
		}
		return buildList {
			if (firstConfirmation) add(callback)
			violation?.let { reason ->
				add(
					ReaderTransitionFact.ReleasePortContractViolated(
						invocation.command.cleanupKey,
						invocation.command.identity,
						reason,
						count
					)
				)
			}
		}
	}
}

internal data class ReaderPhysicalReleaseRetentionSnapshot(
	val activeLeaseCount: Int,
	val issuedCount: Int,
	val confirmingCount: Int,
	val terminalTombstoneCount: Int,
	val terminalTombstoneCapacity: Int,
	val retirementFenceState: ReaderTransitionRetirementFenceState
)

private class ReaderPhysicalReleaseBookkeeper<Lease>(
	private val keyOf: (Lease) -> ReaderTransitionResourceKey,
	private val release: (Lease, () -> Unit) -> Unit
) {
	private val leases = linkedMapOf<ReaderTransitionResourceKey, Lease>()
	private val issued = linkedSetOf<ReaderTransitionResourceKey>()
	private val terminalTombstones = ReaderTransitionTerminalTombstones()

	fun register(lease: Lease): Boolean {
		val key = keyOf(lease)
		if (terminalTombstones.rejectsRegistration(key)) return false
		val existing = leases[key]
		check(existing == null || existing == lease) {
			"A transition resource key cannot identify two physical release leases"
		}
		if (existing != null) return false
		leases[key] = lease
		return true
	}

	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (ReaderTransitionFact) -> Unit
	): Boolean {
		when (command.issuer) {
			is ReaderResourceReleaseIssuer.Transition -> check(
				command.key.owningTransitionIdOrNull != null
			) { "Transition-authorized release requires transition-owned resource identity" }
			is ReaderResourceReleaseIssuer.Session -> check(
				command.key.owningTransitionIdOrNull == null
			) { "Session-authorized release requires an imported registration" }
		}
		val lease = leases[command.key]
		if (lease == null) {
			if (terminalTombstones.containsOrFenced(command.key)) return false
			error("Physical release requires an exact registered lease")
		}
		if (!issued.add(command.key)) return false
		release(lease) {
			if (issued.remove(command.key)) {
				check(leases.remove(command.key) == lease) {
					"Physical release confirmation must match its exact registered lease"
				}
				val transitionOwned = command.key.owningTransitionIdOrNull != null
				if (transitionOwned) {
					check(terminalTombstones.record(command.key, fromActiveRegistration = true)) {
						"Physical release confirmation cannot retire an already terminal resource"
					}
				} else {
					checkNotNull(command.registration) {
						"Adopted release requires its imported registration retirement order"
					}
				}
				onFact(ReaderTransitionFact.ResourceReleased(command.identity))
			}
		}
		return true
	}

	fun retentionSnapshot(): ReaderPhysicalReleaseRetentionSnapshot {
		val terminal = terminalTombstones.terminalSnapshot()
		return ReaderPhysicalReleaseRetentionSnapshot(
			activeLeaseCount = leases.size,
			issuedCount = issued.size,
			confirmingCount = 0,
			terminalTombstoneCount = terminal.terminalTombstoneCount,
			terminalTombstoneCapacity = terminal.terminalTombstoneCapacity,
			retirementFenceState = terminal.retirementFenceState
		)
	}
}

internal class ReaderDeckPhysicalReleasePort(
	release: (ReaderDeckLease, () -> Unit) -> Unit
) {
	private val bookkeeper = ReaderPhysicalReleaseBookkeeper(ReaderDeckLease::resourceKey, release)

	fun register(lease: ReaderDeckLease): Boolean = bookkeeper.register(lease)

	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (ReaderTransitionFact) -> Unit
	): Boolean = bookkeeper.release(command, onFact)

	fun retentionSnapshot(): ReaderPhysicalReleaseRetentionSnapshot = bookkeeper.retentionSnapshot()
}

internal class ReaderRasterPhysicalReleasePort(
	release: (ReaderRasterPreparationLease, () -> Unit) -> Unit
) {
	private val bookkeeper = ReaderPhysicalReleaseBookkeeper(
		ReaderRasterPreparationLease::resourceKey,
		release
	)

	fun register(lease: ReaderRasterPreparationLease): Boolean = bookkeeper.register(lease)

	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (ReaderTransitionFact) -> Unit
	): Boolean = bookkeeper.release(command, onFact)

	fun retentionSnapshot(): ReaderPhysicalReleaseRetentionSnapshot = bookkeeper.retentionSnapshot()
}

internal const val ReaderMaximumActiveSemanticCommandSlots = 8
internal const val ReaderMaximumPendingSemanticRequestHandles = 16
internal const val ReaderMaximumOutOfOrderSemanticSlotTombstones = 32

internal data class ReaderSemanticCommandSlotFenceSnapshot(
	val contiguousRetiredThrough: Long,
	val outOfOrderRetiredSequences: Set<Long>,
	val activeSlotCount: Int
) {
	init {
		require(contiguousRetiredThrough >= 0L)
		require(outOfOrderRetiredSequences.size <= ReaderMaximumOutOfOrderSemanticSlotTombstones)
		require(outOfOrderRetiredSequences.all { it > contiguousRetiredThrough })
		require(activeSlotCount in 0..ReaderMaximumActiveSemanticCommandSlots)
	}
}

internal class ReaderSemanticExecutableRequestRegistry(
	val readerSessionGeneration: Long,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val activationStore: ReaderActivatedSessionSnapshotStore? = null
) {
	fun reserveNeutralBootstrap(
		domain: ReaderLegacyPhysicalDomain,
		candidate: ReaderTransitionGatewayCandidate,
		request: ReaderSemanticExecutableRequest
	): ReaderReservedNeutralBootstrapRequest? = synchronized(lock) {
		if (frozenDomain != domain || domain.readerSessionGeneration != readerSessionGeneration ||
			activationStore == null || candidate.installationSource !== activationStore ||
			!activationStore.awaitingInstallation || !candidate.isSelected ||
			candidate.readerSessionGeneration != readerSessionGeneration ||
			requests.size >= ReaderMaximumPendingSemanticRequestHandles || nextHandleValue == Long.MAX_VALUE ||
			requests.values.any { it.candidate === candidate }
		) return@synchronized null
		val handle = ReaderSemanticRequestHandle(nextHandleValue++)
		val reservation = ReaderReservedNeutralBootstrapRequest(handle, readerSessionGeneration)
		requests[handle] = OwnedRequest(request, tokenAllocator.allocate(), candidate, reservation, domain)
		reservation
	}

	fun discardNeutralBootstrap(reservation: ReaderReservedNeutralBootstrapRequest): Boolean = synchronized(lock) {
		val owned = requests[reservation.handle] ?: return@synchronized false
		if (owned.neutralBootstrapReservation !== reservation || owned.everTaken) false
		else { requests.remove(reservation.handle); true }
	}

	fun discardCandidateReservations() = synchronized(lock) {
		requests.entries.removeAll { it.value.candidate != null && !it.value.everTaken }
	}

	internal fun validatesCandidateInstallation(
		candidate: ReaderTransitionGatewayCandidate,
		store: ReaderActivatedSessionSnapshotStore,
		decision: ReaderInitialActivationDecision,
		domain: ReaderLegacyPhysicalDomain
	): Boolean = synchronized(lock) {
		if (frozenDomain != domain || activationStore !== store || candidate.installationSource !== store || !store.awaitingInstallation ||
			!candidate.isSelected || frozenDomain?.readerSessionGeneration != readerSessionGeneration ||
			candidate.readerSessionGeneration != readerSessionGeneration ||
			decision.origin.readerSessionGeneration != readerSessionGeneration ||
			decision.origin.coordinatorEpoch != candidate.coordinatorEpoch
		) return@synchronized false
		val reservation = decision.neutralBootstrapReservation
		if (reservation == null) return@synchronized requests.values.none { it.candidate === candidate }
		val owned = requests[reservation.handle] ?: return@synchronized false
		owned.candidate === candidate && owned.neutralBootstrapReservation === reservation &&
			owned.bootstrapDomain == frozenDomain && !owned.everTaken && owned.lease == null
	}

	internal class RequestLease internal constructor(
		val executable: ReaderSemanticExecutableRequest,
		val slotId: ReaderSemanticCommandSlotId,
		val transitionId: ReaderTransitionId,
		val candidate: ReaderTransitionGatewayCandidate?
	) {
		val mutationStarted = AtomicBoolean(false)
		val receiptObserved = AtomicBoolean(false)
		internal var retryPermitted = false
	}

	private class OwnedRequest(
		val executable: ReaderSemanticExecutableRequest,
		val activationToken: ReaderLegacySourceLocalOpaqueToken,
		val candidate: ReaderTransitionGatewayCandidate? = null,
		val neutralBootstrapReservation: ReaderReservedNeutralBootstrapRequest? = null,
		val bootstrapDomain: ReaderLegacyPhysicalDomain? = null
	) {
		var lease: RequestLease? = null
		var everTaken = false
		var slotOwned = false
	}

	// Owns every request/freeze mutation; callbacks are invoked only after leaving it.
	private val lock = Any()
	private val requests = linkedMapOf<ReaderSemanticRequestHandle, OwnedRequest>()
	private val restartRequests = linkedMapOf<ReaderSemanticRequestHandle, OwnedRequest>()
	private var nextHandleValue = 1L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var legacyFinalized = false

	val activeHandleCount: Int get() = synchronized(lock) { requests.size }

	init { require(readerSessionGeneration > 0L) }

	private fun installedCandidateLocked(): ReaderTransitionGatewayCandidate? {
		val store = activationStore ?: return null
		val authority = store.snapshot?.portAuthority ?: return null
		val gateway = when (authority) {
			is ReaderActivatedSessionPortAuthority.Production -> authority.ports.gateway
			is ReaderActivatedSessionPortAuthority.Test -> authority.ports.gateway
		} as? ReaderSnapshotBackedActivatedGatewayPort ?: return null
		return gateway.candidate.takeIf { candidate ->
			candidate.isSelected && candidate.installationSource === store &&
				store.ownsInstalledCandidate(candidate.epoch, readerSessionGeneration, candidate.coordinatorEpoch)
		}
	}

	fun register(request: ReaderSemanticExecutableRequest): ReaderSemanticRequestHandle =
		synchronized(lock) {
			val candidate = installedCandidateLocked()
			check((frozenDomain == null && activationStore?.legacyConsequencesOpen != false) || candidate != null) {
				"Semantic request registration is frozen"
			}
			check(requests.size < ReaderMaximumPendingSemanticRequestHandles) {
				"Semantic request handle capacity exceeded"
			}
			val value = nextHandleValue
			check(value < Long.MAX_VALUE) { "Semantic request handle sequence exhausted" }
			nextHandleValue += 1L
			ReaderSemanticRequestHandle(value).also { handle ->
				requests[handle] = OwnedRequest(request, tokenAllocator.allocate(), candidate)
			}
		}

	private fun ownsAdmissionLocked(request: OwnedRequest): Boolean {
		val candidate = request.candidate ?: return frozenDomain == null && activationStore?.legacyConsequencesOpen != false
		return installedCandidateLocked() === candidate
	}

	private fun admitsCommandLocked(
		request: OwnedRequest,
		command: ReaderTransitionCommand.RequestSemanticSynchronization
	): Boolean {
		if (!ownsAdmissionLocked(request) || command.transitionId.readerSessionGeneration != readerSessionGeneration) return false
		val candidate = request.candidate ?: return true
		val id = command.transitionId
		if (id.coordinatorEpoch != candidate.coordinatorEpoch) return false
		val reservation = request.neutralBootstrapReservation ?: return true
		if (request.bootstrapDomain != frozenDomain ||
			activationStore?.snapshot?.reservedNeutralBootstrap !== reservation ||
			command.intent !is paige.navic.reader.ReaderBootstrapNativePageIntent ||
			id.operation != paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage
		) return false
		if (!request.everTaken) return id.sequence == 1L && id.parent == null
		val previous = request.lease ?: return false
		return previous.retryPermitted && id.sequence > previous.transitionId.sequence &&
			id.parent == previous.transitionId.parentIdentity()
	}

	internal fun hasActivationOwnership(handle: ReaderSemanticRequestHandle): Boolean = synchronized(lock) {
		requests[handle]?.candidate != null
	}

	internal fun admitsCommand(command: ReaderTransitionCommand.RequestSemanticSynchronization): Boolean = synchronized(lock) {
		requests[command.requestHandle]?.let { admitsCommandLocked(it, command) } == true
	}

	fun take(
		command: ReaderTransitionCommand.RequestSemanticSynchronization,
		slotId: ReaderSemanticCommandSlotId
	): RequestLease? = synchronized(lock) {
		val request = requests[command.requestHandle] ?: return@synchronized null
		if (!admitsCommandLocked(request, command)) return@synchronized null
		val previous = request.lease
		if (previous != null && (!previous.retryPermitted || previous.mutationStarted.get() || previous.receiptObserved.get())) {
			return@synchronized null
		}
		RequestLease(request.executable, slotId, command.transitionId, request.candidate).also {
			request.lease = it
			request.everTaken = true
			request.slotOwned = true
		}
	}

	internal fun owns(handle: ReaderSemanticRequestHandle, lease: RequestLease): Boolean =
		synchronized(lock) {
			requests[handle]?.let { it.lease === lease && ownsAdmissionLocked(it) } == true
		}

	internal fun completeAttempt(handle: ReaderSemanticRequestHandle, lease: RequestLease, preMutationFailure: Boolean) =
		synchronized(lock) {
			if (requests[handle]?.lease === lease) {
				lease.retryPermitted = preMutationFailure && ownsAdmissionLocked(requireNotNull(requests[handle])) &&
					!lease.mutationStarted.get() && !lease.receiptObserved.get()
			}
		}

	internal fun retainForRetry(handle: ReaderSemanticRequestHandle, lease: RequestLease): Boolean =
		synchronized(lock) {
			val request = requests[handle] ?: return@synchronized false
			if (!ownsAdmissionLocked(request) || request.lease !== lease || !lease.retryPermitted ||
				lease.mutationStarted.get() || lease.receiptObserved.get()) false
			else { request.slotOwned = false; true }
		}

	internal fun retireLease(handle: ReaderSemanticRequestHandle, lease: RequestLease): Boolean = synchronized(lock) {
		if (requests[handle]?.lease !== lease) false else { requests.remove(handle); true }
	}

	fun retire(handle: ReaderSemanticRequestHandle): Boolean = synchronized(lock) {
		(requests.remove(handle) ?: restartRequests.remove(handle)) != null
	}

	internal fun retireRequest(handle: ReaderSemanticRequestHandle, session: Long): Boolean = synchronized(lock) {
		if (session != readerSessionGeneration || (frozenDomain != null && requests[handle]?.candidate == null)) false
		else (requests.remove(handle) ?: restartRequests.remove(handle)) != null
	}

	fun clear() = synchronized(lock) {
		requests.clear()
		restartRequests.clear()
	}

	internal fun allocateActivationToken(): ReaderLegacySourceLocalOpaqueToken = synchronized(lock) {
		tokenAllocator.allocate()
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		when {
			domain.readerSessionGeneration != readerSessionGeneration ->
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
			frozenDomain == null -> {
				frozenDomain = domain
				// Freeze revokes attempted-work replay; only never-started legacy requests
				// may return through the existing restoration checkpoint.
				requests.values.forEach { it.lease?.retryPermitted = false }
				ReaderPortCommandResult.Accepted
			}
			frozenDomain == domain -> ReaderPortCommandResult.Accepted
			else -> ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
	}

	internal val originalLegacyAdmissionRestored: Boolean
		get() = synchronized(lock) { frozenDomain == null }

	fun snapshotConnectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized null
		ReaderLegacyConnectedSourceInventory(ReaderLegacyInventorySource.SemanticCommandSlot, domain, snapshotFrozenOwnership())
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		// A leased request is covered by its executor slot row. Retriable custody has
		// no slot and therefore keeps its own original physical request row.
		requests.values.filterNot { it.slotOwned || it.candidate != null }.map { request ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.SemanticCommandSlot,
					sourceLocalToken = request.activationToken
				),
				kind = ReaderTransitionResourceKind.CallbackRegistration,
				binding = null,
				visibleOwner = null,
				origin = if (request.everTaken) ReaderLegacyResourceOrigin.Owned else ReaderLegacyResourceOrigin.Pending,
				state = ReaderLegacyResourceState.Registered,
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): Boolean {
		val confirmed = drainFrozenOwnership(physicalIdentity)
		if (confirmed) onConfirmed(physicalIdentity)
		return confirmed
	}

	internal fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity
	): Boolean = synchronized(lock) {
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.SemanticCommandSlot
		) {
			false
		} else {
			val entry = requests.entries.firstOrNull {
				it.value.candidate == null && it.value.activationToken == physicalIdentity.sourceLocalToken
			}
			if (entry == null) {
				false
			} else {
				requests.remove(entry.key)
				if (!entry.value.everTaken) restartRequests[entry.key] = entry.value
				true
			}
		}
	}

	fun finalizeActivatedHandoff(domain: ReaderLegacyPhysicalDomain): Boolean = synchronized(lock) {
		if (legacyFinalized || frozenDomain != domain || requests.values.any { it.candidate == null }) false
		else {
			restartRequests.clear()
			legacyFinalized = true
			true
		}
	}

	fun restoreAfterTransitionActivation(domain: ReaderLegacyPhysicalDomain): Boolean =
		synchronized(lock) {
			if (legacyFinalized || frozenDomain != domain) {
				false
			} else {
				frozenDomain = null
				requests.putAll(restartRequests)
				restartRequests.clear()
				true
			}
		}
}

internal class ReaderSemanticCommandExecutor(
	private val registry: ReaderSemanticExecutableRequestRegistry,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator? = null
) : ReaderSemanticCommandPort {
	internal fun validatesCandidateInstallation(
		candidate: ReaderTransitionGatewayCandidate,
		store: ReaderActivatedSessionSnapshotStore,
		decision: ReaderInitialActivationDecision
	): Boolean = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized false
		registry.validatesCandidateInstallation(candidate, store, decision, domain)
	}

	private class Slot(
		val transitionId: ReaderTransitionId,
		val handle: ReaderSemanticRequestHandle,
		val activationToken: ReaderLegacySourceLocalOpaqueToken,
		val activationOwned: Boolean
	) {
		var requestLease: ReaderSemanticExecutableRequestRegistry.RequestLease? = null
		var executionStarted = false
		var admissionRetired = false
		var asyncOutcomeConsumed = false
		var asyncOutcomeBound = false
		var onAsyncOutcome: ((ReaderSemanticCommandResult) -> Unit)? = null
		var transportBound = false
		var transportRetirement: ReaderSemanticTransportRetirement? = null
		var retiredTransport: WeakReference<ReaderSemanticTransportRetirement>? = null
		var lateTransportConsumed = false
	}

	private data class PreparedExecution(
		val slotId: ReaderSemanticCommandSlotId,
		val slot: Slot,
		val lease: ReaderSemanticExecutableRequestRegistry.RequestLease
	)

	// Owns slots, capacity, freeze inventory, and retirement fences. No executable or external
	// callback is invoked while held.
	private val lock = Any()
	private val activeSlots = linkedMapOf<ReaderSemanticCommandSlotId, Slot>()
	private val completedFrozenSlots = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private val outOfOrderRetiredSlotValues = sortedSetOf<Long>()
	private var contiguousRetiredThrough = 0L
	private var nextSlotValue = 1L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	val activeSlotCount: Int get() = synchronized(lock) { activeSlots.size }

	fun retirementSnapshot() = synchronized(lock) {
		ReaderSemanticCommandSlotFenceSnapshot(
			contiguousRetiredThrough,
			outOfOrderRetiredSlotValues.toSet(),
			activeSlots.size
		)
	}

	override fun retireRequest(handle: ReaderSemanticRequestHandle, readerSessionGeneration: Long): Boolean {
		val retirements = mutableListOf<ReaderSemanticTransportRetirement>()
		val retired = synchronized(lock) {
			if (!registry.retireRequest(handle, readerSessionGeneration)) false
			else {
				activeSlots.filterValues { it.handle == handle }.toList().forEach { (id, slot) ->
					retireSlotLocked(id, slot)?.let(retirements::add)
				}
				true
			}
		}
		retirements.forEach(::disposeTransport)
		return retired
	}

	override fun synchronize(
		command: ReaderTransitionCommand.RequestSemanticSynchronization,
		onRegistration: (ReaderSemanticCommandRegistration) -> Unit,
		onReceipt: (ReaderPresentationEventReceipt) -> Unit
	): ReaderSemanticCommandResult {
		val handle = command.requestHandle
		val prepared = synchronized(lock) {
			val activationOwned = registry.hasActivationOwnership(handle)
			if ((frozenDomain != null && !activationOwned) ||
				(activationOwned && !registry.admitsCommand(command)) ||
				command.transitionId.readerSessionGeneration != registry.readerSessionGeneration
			) {
				return ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
			if (
				activeSlots.size >= ReaderMaximumActiveSemanticCommandSlots ||
				outOfOrderRetiredSlotValues.size >= ReaderMaximumOutOfOrderSemanticSlotTombstones
			) {
				registry.retire(handle)
				return ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
			val slotValue = nextSlotValue
			if (slotValue == Long.MAX_VALUE) {
				registry.retire(handle)
				return ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
			nextSlotValue += 1L
			val slotId = ReaderSemanticCommandSlotId(slotValue)
			val slot = Slot(
				command.transitionId,
				handle,
				tokenAllocator?.allocate() ?: registry.allocateActivationToken(),
				activationOwned
			)
			activeSlots[slotId] = slot
			val lease = registry.take(command, slotId)
			if (lease == null) {
				retireSlotLocked(slotId, slot)
				return ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
			slot.requestLease = lease
			PreparedExecution(slotId, slot, lease)
		}
		val registration = object : ReaderSemanticCommandRegistration {
			override fun retire() {
				val retirement = synchronized(lock) {
					registry.retireLease(handle, prepared.lease)
					retireSlotLocked(prepared.slotId, prepared.slot)
				}
				disposeTransport(retirement)
			}
			override fun retireForRetry() {
				val retirement = synchronized(lock) {
					if (!registry.retainForRetry(handle, prepared.lease)) {
						registry.retireLease(handle, prepared.lease)
					}
					retireSlotLocked(prepared.slotId, prepared.slot)
				}
				disposeTransport(retirement)
			}
			override fun bindAsyncOutcome(onOutcome: (ReaderSemanticCommandResult) -> Unit): Boolean = synchronized(lock) {
				if (!ownsExecutionLocked(prepared) || prepared.slot.executionStarted || prepared.slot.asyncOutcomeBound) false
				else {
					prepared.slot.asyncOutcomeBound = true
					prepared.slot.onAsyncOutcome = onOutcome
					true
				}
			}
		}
		try {
			onRegistration(registration)
		} catch (_: Throwable) {
			registration.retire()
			return ReaderSemanticCommandResult.ThrewBeforeMutation
		}
		// Registration is external and may synchronously retire this exact slot or freeze
		// its owner. Recheck only admission here; already-started executable callbacks
		// remain observable so the coordinator can classify late/duplicate violations.
		var admissionRetirement: ReaderSemanticTransportRetirement? = null
		val mayStart = synchronized(lock) {
			if (!ownsExecutionLocked(prepared)) {
				registry.retireLease(handle, prepared.lease)
				admissionRetirement = retireSlotLocked(prepared.slotId, prepared.slot)
				false
			} else {
				prepared.slot.executionStarted = true
				true
			}
		}
		disposeTransport(admissionRetirement)
		if (!mayStart) return ReaderSemanticCommandResult.RejectedBeforeMutation(
			ReaderTransitionFailureReason.PortRejected
		)
		val origin = ReaderPresentationEventOrigin.SemanticCommand(
			command.transitionId,
			prepared.slotId
		)
		val mutationStarted = prepared.lease.mutationStarted
		val mutationStartEvidence = object : ReaderSemanticMutationStartEvidence {
			override fun isExecutionCurrent(
				origin: ReaderPresentationEventOrigin.SemanticCommand,
				requestHandle: ReaderSemanticRequestHandle
			): Boolean = synchronized(lock) {
				requestHandle == handle && origin.transitionId == command.transitionId &&
					origin.slotId == prepared.slotId && prepared.slot.executionStarted &&
					ownsExecutionLocked(prepared)
			}

			override fun mutationStarted() {
				synchronized(lock) {
					if (ownsExecutionLocked(prepared)) mutationStarted.set(true)
				}
			}

			override fun bindTransportRetirement(
				origin: ReaderPresentationEventOrigin.SemanticCommand,
				retirement: ReaderSemanticTransportRetirement
			): ReaderSemanticTransportBindingResult {
				if (origin != ReaderPresentationEventOrigin.SemanticCommand(command.transitionId, prepared.slotId)) {
					return ReaderSemanticTransportBindingResult.Rejected
				}
				val result = synchronized(lock) {
					val slot = prepared.slot
					if (ownsExecutionLocked(prepared)) {
						if (slot.transportBound) ReaderSemanticTransportBindingResult.Rejected
						else {
							slot.transportBound = true
							slot.transportRetirement = retirement
							ReaderSemanticTransportBindingResult.Bound
						}
					} else if (slot.lateTransportConsumed || slot.retiredTransport?.get() === retirement) {
						ReaderSemanticTransportBindingResult.Rejected
					} else {
						// One late exact handoff is cleanup, never reactivation. Consume it before
						// external code so duplicate/reentrant binding remains caller-owned.
						slot.lateTransportConsumed = true
						slot.retiredTransport = WeakReference(retirement)
						ReaderSemanticTransportBindingResult.RetiredAndDisposed
					}
				}
				if (result == ReaderSemanticTransportBindingResult.RetiredAndDisposed) disposeTransport(retirement)
				return result
			}

			override fun reportAsyncFailure(
				origin: ReaderPresentationEventOrigin.SemanticCommand,
				failure: ReaderSemanticAsyncFailure
			): Boolean {
				if (origin != ReaderPresentationEventOrigin.SemanticCommand(command.transitionId, prepared.slotId)) return false
				val publication = synchronized(lock) {
					if (!ownsExecutionLocked(prepared) || !prepared.slot.executionStarted) return@synchronized null
					val onOutcome = prepared.slot.onAsyncOutcome ?: return@synchronized null
					prepared.slot.asyncOutcomeConsumed = true
					val started = mutationStarted.get()
					val outcome = when (failure) {
						ReaderSemanticAsyncFailure.Rejected -> if (started) ReaderSemanticCommandResult.RejectedAfterMutationStarted
							else ReaderSemanticCommandResult.RejectedBeforeMutation(ReaderTransitionFailureReason.PortRejected)
						ReaderSemanticAsyncFailure.Threw -> if (started) ReaderSemanticCommandResult.ThrewAfterMutationStarted
							else ReaderSemanticCommandResult.ThrewBeforeMutation
					}
					registry.completeAttempt(handle, prepared.lease, preMutationFailure = !started)
					if (!registry.retainForRetry(handle, prepared.lease)) registry.retireLease(handle, prepared.lease)
					Triple(onOutcome, outcome, retireSlotLocked(prepared.slotId, prepared.slot))
				} ?: return false
				// All custody, including exact Retry eligibility, precedes either reentrant effect.
				try { publication.first(publication.second) } catch (_: Throwable) { /* No fabricated receipt or acceptance. */ }
				disposeTransport(publication.third)
				return true
			}
		}
		val completion = try {
			val result = prepared.lease.executable(origin, mutationStartEvidence) { receipt ->
				if (receipt.origin != origin) return@executable
				prepared.lease.receiptObserved.set(true)
				val retirement = synchronized(lock) {
					registry.retireLease(handle, prepared.lease)
					retireSlotLocked(prepared.slotId, prepared.slot)
				}
				try {
					onReceipt(receipt)
				} finally {
					disposeTransport(retirement)
				}
			}
			when (result) {
				ReaderSemanticExecutableResult.Accepted -> ReaderSemanticCommandResult.Accepted
				ReaderSemanticExecutableResult.Rejected -> {
					if (mutationStarted.get()) {
						ReaderSemanticCommandResult.RejectedAfterMutationStarted
					} else {
						ReaderSemanticCommandResult.RejectedBeforeMutation(
							ReaderTransitionFailureReason.PortRejected
						)
					}
				}
			}
		} catch (_: Throwable) {
			if (mutationStarted.get()) {
				ReaderSemanticCommandResult.ThrewAfterMutationStarted
			} else {
				ReaderSemanticCommandResult.ThrewBeforeMutation
			}
		}
		val completionRetirement = synchronized(lock) {
			// A real asynchronous failure may have completed before this authentic return.
			// It already owns the outcome and Retry decision; do not revoke that custody.
			if (prepared.slot.asyncOutcomeConsumed) null else {
				registry.completeAttempt(handle, prepared.lease,
					completion is ReaderSemanticCommandResult.RejectedBeforeMutation ||
						completion == ReaderSemanticCommandResult.ThrewBeforeMutation)
				if (completion == ReaderSemanticCommandResult.Accepted) null else {
					prepared.slot.admissionRetired = true
					prepared.slot.onAsyncOutcome = null
					consumeTransportLocked(prepared.slot)
				}
			}
		}
		disposeTransport(completionRetirement)
		return completion
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		val retirements = mutableListOf<ReaderSemanticTransportRetirement>()
		val result = synchronized(lock) {
			if (
				domain.readerSessionGeneration != registry.readerSessionGeneration ||
				(frozenDomain != null && frozenDomain != domain)
			) {
				return@synchronized ReaderPortCommandResult.Rejected(
					ReaderTransitionFailureReason.InvalidLegacyResource
				)
			}
			val registryResult = registry.freezeForTransitionActivation(domain)
			if (registryResult is ReaderPortCommandResult.Rejected) return@synchronized registryResult
			frozenDomain = domain
			activeSlots.values.forEach { slot ->
				// Restore never resurrects an attempted executable or its retired sender.
				slot.admissionRetired = true
				slot.onAsyncOutcome = null
				consumeTransportLocked(slot)?.let(retirements::add)
			}
			ReaderPortCommandResult.Accepted
		}
		retirements.forEach(::disposeTransport)
		return result
	}

	internal val originalLegacyAdmissionRestored: Boolean
		get() = synchronized(lock) { frozenDomain == null && registry.originalLegacyAdmissionRestored }

	fun snapshotConnectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized null
		if (registry.snapshotConnectedFrozenOwnership()?.domain != domain) return@synchronized null
		ReaderLegacyConnectedSourceInventory(ReaderLegacyInventorySource.SemanticCommandSlot, domain, snapshotFrozenOwnership())
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		registry.snapshotFrozenOwnership() + activeSlots.values.filterNot { it.activationOwned }.map { slot ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.SemanticCommandSlot,
					sourceLocalToken = slot.activationToken
				),
				kind = ReaderTransitionResourceKind.CallbackRegistration,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = ReaderLegacyResourceState.Running,
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		var retirement: ReaderSemanticTransportRetirement? = null
		val confirmed = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source != ReaderLegacyInventorySource.SemanticCommandSlot
			) {
				false
			} else {
				val token = physicalIdentity.sourceLocalToken
				val slotEntry = activeSlots.entries.firstOrNull {
					!it.value.activationOwned && it.value.activationToken == token
				}
				when {
					slotEntry != null -> {
						activeSlots.remove(slotEntry.key)
						slotEntry.value.admissionRetired = true
						slotEntry.value.onAsyncOutcome = null
						retirement = consumeTransportLocked(slotEntry.value)
						slotEntry.value.requestLease?.let { registry.retireLease(slotEntry.value.handle, it) }
						retireSlotValueLocked(slotEntry.key.value)
						true
					}
					completedFrozenSlots.remove(token) -> true
					else -> registry.drainFrozenOwnership(physicalIdentity)
				}
			}
		}
		disposeTransport(retirement)
		if (!confirmed) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		onConfirmed(physicalIdentity)
		return ReaderPortCommandResult.Accepted
	}

	fun finalizeActivatedHandoff(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult = synchronized(lock) {
		if (frozenDomain != domain || activeSlots.values.any { !it.activationOwned } || !registry.finalizeActivatedHandoff(domain))
			return@synchronized ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
		completedFrozenSlots.clear()
		ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (frozenDomain != domain || !registry.restoreAfterTransitionActivation(domain)) {
			return@synchronized ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		frozenDomain = null
		completedFrozenSlots.clear()
		ReaderPortCommandResult.Accepted
	}

	fun retireTransition(transitionId: ReaderTransitionId) {
		val retirements = synchronized(lock) {
			activeSlots.filterValues { it.transitionId == transitionId }.toList().mapNotNull { (id, slot) ->
				slot.requestLease?.let { lease -> registry.retireLease(slot.handle, lease) }
				retireSlotLocked(id, slot)
			}
		}
		retirements.forEach(::disposeTransport)
	}

	fun clear() {
		val retirements = synchronized(lock) {
			val retired = activeSlots.toList().mapNotNull { (id, slot) -> retireSlotLocked(id, slot) }
			registry.clear()
			retired
		}
		retirements.forEach(::disposeTransport)
	}

	private fun ownsExecutionLocked(prepared: PreparedExecution): Boolean =
		(frozenDomain == null || prepared.slot.activationOwned) && !prepared.slot.admissionRetired && !prepared.slot.asyncOutcomeConsumed &&
			!prepared.lease.receiptObserved.get() && activeSlots[prepared.slotId] === prepared.slot &&
			registry.owns(prepared.slot.handle, prepared.lease)

	private fun consumeTransportLocked(slot: Slot): ReaderSemanticTransportRetirement? {
		val retirement = slot.transportRetirement ?: return null
		slot.transportRetirement = null
		slot.retiredTransport = WeakReference(retirement)
		return retirement
	}

	private fun disposeTransport(retirement: ReaderSemanticTransportRetirement?) {
		try { retirement?.retire() } catch (_: Throwable) {
			// Custody is already consumed. A throwing sender cannot manufacture a receipt,
			// revoke a fence, or trigger repeated disposal under either ownership lock.
		}
	}

	private fun retireSlotLocked(
		slotId: ReaderSemanticCommandSlotId,
		expected: Slot
	): ReaderSemanticTransportRetirement? {
		if (activeSlots[slotId] !== expected) return null
		activeSlots.remove(slotId)
		expected.admissionRetired = true
		expected.onAsyncOutcome = null
		if (frozenDomain != null && !expected.activationOwned) completedFrozenSlots += expected.activationToken
		retireSlotValueLocked(slotId.value)
		return consumeTransportLocked(expected)
	}

	private fun retireSlotValueLocked(value: Long) {
		if (value <= contiguousRetiredThrough) return
		if (value == contiguousRetiredThrough + 1L) {
			contiguousRetiredThrough = value
			while (outOfOrderRetiredSlotValues.remove(contiguousRetiredThrough + 1L)) {
				contiguousRetiredThrough += 1L
			}
			return
		}
		if (!outOfOrderRetiredSlotValues.add(value)) return
		check(
			outOfOrderRetiredSlotValues.size <= ReaderMaximumOutOfOrderSemanticSlotTombstones
		) { "Semantic slot tombstone capacity exceeded" }
	}
}

internal interface ReaderRasterPreparationCommandPort {
	fun prepare(
		lease: ReaderRasterPreparationLease,
		callbacks: ReaderRasterLeaseFactEmitter
	)

	fun release(lease: ReaderRasterPreparationLease, onReleased: () -> Unit)
	fun cancel(transitionId: ReaderTransitionId)
}

internal interface ReaderRendererDeckCommandPort {
	fun reserve(
		lease: ReaderDeckLease,
		callbacks: ReaderDeckLeaseFactEmitter
	)

	fun release(lease: ReaderDeckLease, onReleased: () -> Unit)
	fun cancelPreparation(transitionId: ReaderTransitionId)
}

internal class ReaderTask4TransitionPorts(
	override val clock: ReaderTransitionClock,
	private val raster: ReaderRasterPreparationCommandPort,
	private val renderer: ReaderRendererDeckCommandPort
) : ReaderResumableTransitionPorts {
	private val deckPhysicalRelease = ReaderDeckPhysicalReleasePort(renderer::release)
	private val rasterPhysicalRelease = ReaderRasterPhysicalReleasePort(raster::release)
	private var nextMaterialGeneration = 1L

	override fun acceptsFact(fact: ReaderTransitionFact): Boolean = fact.isTask4CoordinatorFact()

	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		when (command) {
			is ReaderTransitionCommand.AllocateMaterialBinding -> {
				if (!command.transitionId.expectedBinding.acceptsMaterialAllocation(command.binding)) {
					onFact(
						ReaderTransitionFact.CommandRejected(
							command.transitionId,
							ReaderTransitionCommandStage.MaterialAllocation,
							ReaderTransitionCommandRejectionReason.MaterialAllocationRejected
						)
					)
					return
				}
				check(nextMaterialGeneration <= Long.MAX_VALUE - 2L)
				val preparation = nextMaterialGeneration++
				val rasterGeneration = nextMaterialGeneration++
				val texture = nextMaterialGeneration++
				val allocatedBinding = command.binding.copy(
					preparationGeneration = preparation,
					rasterGeneration = rasterGeneration,
					textureGeneration = texture
				)
				onFact(
					ReaderTransitionFact.MaterialBindingAllocated(
						command.transitionId,
						ReaderMaterialGenerationAllocation(
							command.transitionId,
							allocatedBinding,
							preparation,
							rasterGeneration,
							texture
						)
					)
				)
			}
			is ReaderTransitionCommand.RequestRasterPreparation -> {
				val preparationGeneration = command.binding.preparationGeneration
				val rasterGeneration = command.binding.rasterGeneration
				if (
					preparationGeneration == null ||
					rasterGeneration == null ||
					!readerTransitionMaterialBindingIsValid(
						command.transitionId,
						command.binding,
						command.allocation
					)
				) {
					onFact(
						ReaderTransitionFact.CommandRejected(
							command.transitionId,
							ReaderTransitionCommandStage.RasterPreparation,
							ReaderTransitionCommandRejectionReason.RasterPreparationRejected
						)
					)
					return
				}
				val lease = ReaderRasterPreparationLease(
					transitionId = command.transitionId,
					binding = command.binding,
					preparationGeneration = preparationGeneration,
					rasterGeneration = rasterGeneration,
					resourceKey = ReaderTransitionResourceKey(
						command.transitionId,
						ReaderTransitionResourceKind.Raster,
						rasterGeneration
					),
					allocation = command.allocation
				)
				if (!rasterPhysicalRelease.register(lease)) return
				val callbacks = ReaderRasterLeaseFactEmitter(lease, onFact)
				callbacks.registerResource()
				raster.prepare(lease, callbacks)
			}
			is ReaderTransitionCommand.ReserveDeck -> {
				val textureGeneration = command.binding.textureGeneration
				if (textureGeneration == null) {
					onFact(
						ReaderTransitionFact.CommandRejected(
							command.transitionId,
							ReaderTransitionCommandStage.DeckReservation,
							ReaderTransitionCommandRejectionReason.DeckReservationRejected
						)
					)
					return
				}
				val key = ReaderTransitionResourceKey(
					command.transitionId,
					ReaderTransitionResourceKind.Deck,
					textureGeneration
				)
				val lease = readerDeckLeaseOrNull(command, key)
				if (lease == null) {
					onFact(
						ReaderTransitionFact.CommandRejected(
							command.transitionId,
							ReaderTransitionCommandStage.DeckReservation,
							ReaderTransitionCommandRejectionReason.DeckReservationRejected
						)
					)
					return
				}
				if (!deckPhysicalRelease.register(lease)) return
				val callbacks = ReaderDeckLeaseFactEmitter(lease, onFact)
				callbacks.onReserved(lease)
				renderer.reserve(lease, callbacks)
			}
			is ReaderTransitionCommand.ReleaseResource -> when (command.key.kind) {
				ReaderTransitionResourceKind.Deck -> deckPhysicalRelease.release(command, onFact)
				ReaderTransitionResourceKind.Raster -> rasterPhysicalRelease.release(command, onFact)
				ReaderTransitionResourceKind.CallbackRegistration,
				ReaderTransitionResourceKind.FrameHandoff -> error(
					"Task 4 ports cannot release unsupported resource kinds"
				)
			}
			is ReaderTransitionCommand.CancelOwnedWork -> {
				raster.cancel(command.transitionId)
				renderer.cancelPreparation(command.transitionId)
			}
			is ReaderTransitionCommand.RequestResourceRelease,
			is ReaderTransitionCommand.RequestSemanticSynchronization,
			is ReaderTransitionCommand.PrepareFrameTarget,
			is ReaderTransitionCommand.RequestFramePresentation,
			is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease,
			is ReaderTransitionCommand.CommitOwnerAndInputLease -> error(
				"Task 4 ports cannot execute semantic, frame, or input commands"
			)
		}
	}

	fun registerAdoptedLease(lease: ReaderDeckLease): Boolean {
		check(lease.provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
		return deckPhysicalRelease.register(lease)
	}
}

internal interface ReaderResumableTransitionPorts {
	val clock: ReaderTransitionClock
	val task6FactOnlyTimer: ReaderTask6FactOnlyTimerPort?
		get() = null
	val ownerAndInputPublication: ReaderOwnerAndInputPublicationPort?
		get() = null
	val semanticCommand: ReaderSemanticCommandPort?
		get() = null

	fun acceptsFact(fact: ReaderTransitionFact): Boolean = true

	fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	)

	fun issueImportedLegacyRelease(
		dispatch: ReaderImportedLegacyReleaseDispatch,
		onFact: (ReaderTransitionFact) -> Unit
	) = issue(dispatch.command, onFact)
}

internal class ReaderActivatedTransitionPorts(
	private val ports: ReaderProductionActivatedSessionPorts
) : ReaderResumableTransitionPorts {
	private val physicalRelease = ReaderPhysicalReleaseCommandAdapter()
	override val clock: ReaderTransitionClock = ReaderActivatedFactOnlyTransitionClock
	override val task6FactOnlyTimer: ReaderTask6FactOnlyTimerPort
		get() = ports.factOnlyTimer
	override val ownerAndInputPublication: ReaderOwnerAndInputPublicationPort
		get() = ports.ownerAndInput
	override val semanticCommand: ReaderSemanticCommandPort
		get() = ports.semantic

	override fun issueImportedLegacyRelease(
		dispatch: ReaderImportedLegacyReleaseDispatch,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		physicalRelease.dispatchLegacy(
			dispatch = dispatch,
			invoke = { callback ->
				ports.resources.releaseLegacy(
					dispatch.command,
					dispatch.imported,
					callback
				)
			},
			onFact = onFact
		)
	}

	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		if (command is ReaderTransitionCommand.ReleaseResource) {
			physicalRelease.dispatchGeneric(
				command,
				invoke = { callback -> ports.resources.release(command, callback) },
				onFact = onFact
			)
			return
		}
		if (command is ReaderTransitionCommand.CancelOwnedWork && command.cleanupKey != null) {
			val outcome = try {
				when (ports.resources.cancelOwnedWork(command)) {
					ReaderPortCommandResult.Accepted -> ReaderOwnedWorkCancellationOutcome.Applied
					is ReaderPortCommandResult.Rejected -> ReaderOwnedWorkCancellationOutcome.Rejected
				}
			} catch (_: Throwable) {
				ReaderOwnedWorkCancellationOutcome.Threw
			}
			onFact(
				ReaderTransitionFact.OwnedWorkCancellationCompleted(
					requireNotNull(command.cleanupKey),
					command.transitionId,
					outcome
				)
			)
			return
		}
		val result = when (command) {
			is ReaderTransitionCommand.AllocateMaterialBinding ->
				ports.materialAllocation.allocate(command) { onFact(it) }
			is ReaderTransitionCommand.RequestRasterPreparation ->
				ports.raster.prepare(command, onFact)
			is ReaderTransitionCommand.ReserveDeck ->
				ports.deck.reserve(command, onFact)
			is ReaderTransitionCommand.PrepareFrameTarget ->
				ports.frame.prepareTarget(command, onFact)
			is ReaderTransitionCommand.RequestFramePresentation ->
				ports.frame.present(command, onFact)
			is ReaderTransitionCommand.RequestResourceRelease,
			is ReaderTransitionCommand.ReleaseResource -> error(
				"Activated release requests must be ledger-accounted before dispatch"
			)
			is ReaderTransitionCommand.CancelOwnedWork -> ports.resources.cancelOwnedWork(command)
			is ReaderTransitionCommand.RequestSemanticSynchronization -> error(
				"Activated semantic commands must use the dedicated semantic port"
			)
			is ReaderTransitionCommand.CommitOwnerAndInputLease,
			is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> error(
				"Activated publication commands must use the atomic publication port"
			)
		}
		if (result is ReaderPortCommandResult.Rejected) {
			val failure = when (command) {
				is ReaderTransitionCommand.PrepareFrameTarget ->
					ReaderTransitionFact.FrameTargetPreparationRejected(
						command.transitionId,
						command.specification,
						command.registration,
						result.reason
					)
				is ReaderTransitionCommand.ReleaseResource -> command.transitionId?.let {
					ReaderTransitionFact.RasterFailed(it, result.reason)
				}
				is ReaderTransitionCommand.CancelOwnedWork -> ReaderTransitionFact.RasterFailed(
					command.transitionId,
					result.reason
				)
				else -> command.toCommandRejectedFact()
			}
			failure?.let(onFact)
		}
	}
}

private fun ReaderTransitionCommand.toCommandRejectedFact(): ReaderTransitionFact.CommandRejected {
	val (stage, reason) = when (this) {
		is ReaderTransitionCommand.RequestSemanticSynchronization ->
			ReaderTransitionCommandStage.SemanticSynchronization to
				ReaderTransitionCommandRejectionReason.SemanticExecutionRejected
		is ReaderTransitionCommand.AllocateMaterialBinding ->
			ReaderTransitionCommandStage.MaterialAllocation to
				ReaderTransitionCommandRejectionReason.MaterialAllocationRejected
		is ReaderTransitionCommand.RequestRasterPreparation ->
			ReaderTransitionCommandStage.RasterPreparation to
				ReaderTransitionCommandRejectionReason.RasterPreparationRejected
		is ReaderTransitionCommand.ReserveDeck ->
			ReaderTransitionCommandStage.DeckReservation to
				ReaderTransitionCommandRejectionReason.DeckReservationRejected
		is ReaderTransitionCommand.PrepareFrameTarget ->
			ReaderTransitionCommandStage.FrameTargetPreparation to
				ReaderTransitionCommandRejectionReason.FrameTargetRejected
		is ReaderTransitionCommand.RequestFramePresentation ->
			ReaderTransitionCommandStage.FramePresentation to
				ReaderTransitionCommandRejectionReason.FramePresentationRejected
		is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ->
			ReaderTransitionCommandStage.RetainedPublication to
				ReaderTransitionCommandRejectionReason.PublicationRejected
		is ReaderTransitionCommand.CommitOwnerAndInputLease ->
			ReaderTransitionCommandStage.SuccessorPublication to
				ReaderTransitionCommandRejectionReason.PublicationRejected
		is ReaderTransitionCommand.RequestResourceRelease,
		is ReaderTransitionCommand.ReleaseResource,
		is ReaderTransitionCommand.CancelOwnedWork -> error(
			"Release commands do not use ordinary command-stage rejection"
		)
	}
	return ReaderTransitionFact.CommandRejected(requireNotNull(transitionId), stage, reason)
}

internal class ReaderCutoverTransitionPorts(
	private val delegate: ReaderResumableTransitionPorts,
	private val deckCutover: ReaderDeckAdmissionCutover
) : ReaderResumableTransitionPorts {
	override val clock: ReaderTransitionClock
		get() = delegate.clock
	override val task6FactOnlyTimer: ReaderTask6FactOnlyTimerPort?
		get() = delegate.task6FactOnlyTimer
	override val ownerAndInputPublication: ReaderOwnerAndInputPublicationPort?
		get() = delegate.ownerAndInputPublication
	override val semanticCommand: ReaderSemanticCommandPort?
		get() = delegate.semanticCommand

	override fun acceptsFact(fact: ReaderTransitionFact): Boolean = delegate.acceptsFact(fact)

	override fun issueImportedLegacyRelease(
		dispatch: ReaderImportedLegacyReleaseDispatch,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		check(deckCutover.coordinatorCommandsAllowed) {
			"Coordinator resource release is not active"
		}
		delegate.issueImportedLegacyRelease(dispatch, onFact)
	}

	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		when (command) {
			is ReaderTransitionCommand.ReserveDeck -> check(deckCutover.coordinatorAdmissionOpen) {
				"Coordinator deck admission is not active"
			}
			is ReaderTransitionCommand.ReleaseResource -> check(deckCutover.coordinatorCommandsAllowed) {
				"Coordinator resource release is not active"
			}
			else -> Unit
		}
		delegate.issue(command, onFact)
	}
}

private object ReaderActivatedFactOnlyTransitionClock : ReaderTransitionClock {
	override fun nowMillis(): Long = SystemClock.uptimeMillis()

	override fun schedule(
		atMillis: Long,
		action: () -> Unit
	): ReaderTransitionClockRegistration = error(
		"Activated deadlines belong exclusively to the retained Task 6 fact timer"
	)
}

internal class AndroidReaderTransitionClock : ReaderTransitionClock {
	private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }

	override fun nowMillis(): Long = SystemClock.uptimeMillis()

	override fun schedule(
		atMillis: Long,
		action: () -> Unit
	): ReaderTransitionClockRegistration? {
		val runnable = Runnable(action)
		val accepted = handler.postAtTime(runnable, atMillis)
		return if (accepted) {
			ReaderTransitionClockRegistration { handler.removeCallbacks(runnable) }
		} else {
			null
		}
	}
}

internal class ReaderShadowTransitionPorts(
	override val clock: ReaderTransitionClock = AndroidReaderTransitionClock()
) : ReaderResumableTransitionPorts {
	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		error("Shadow transition ports cannot issue mutating commands")
	}
}
