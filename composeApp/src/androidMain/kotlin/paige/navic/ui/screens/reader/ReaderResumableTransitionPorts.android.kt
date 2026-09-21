package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import paige.navic.reader.acceptsMaterialAllocation
import paige.navic.reader.readerTransitionMaterialBindingIsValid
import paige.navic.reader.ReaderMaterialGenerationAllocation
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderResourceReleaseIssuer
import paige.navic.reader.ReaderSemanticCommandSlotId
import paige.navic.reader.ReaderSemanticExecutableRequest
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

internal enum class ReaderTransitionMode { Shadow, Active }

internal fun interface ReaderTransitionClockRegistration {
	fun cancel()
}

internal interface ReaderTransitionClock {
	fun nowMillis(): Long
	fun schedule(atMillis: Long, action: () -> Unit): ReaderTransitionClockRegistration?
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
				command.key.owningTransitionIdOrNull == null && command.registration != null
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
				onFact(
					ReaderTransitionFact.ResourceReleased(
						command.transitionId,
						command.key,
						command.registration
					)
				)
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
		ReaderLegacySourceLocalTokenAllocator()
) {
	private data class OwnedRequest(
		val executable: ReaderSemanticExecutableRequest,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private val requests = linkedMapOf<ReaderSemanticRequestHandle, OwnedRequest>()
	private val restartRequests = linkedMapOf<ReaderSemanticRequestHandle, OwnedRequest>()
	private var nextHandleValue = 1L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	val activeHandleCount: Int get() = requests.size

	init { require(readerSessionGeneration > 0L) }

	fun register(request: ReaderSemanticExecutableRequest): ReaderSemanticRequestHandle {
		check(frozenDomain == null) { "Semantic request registration is frozen" }
		check(requests.size < ReaderMaximumPendingSemanticRequestHandles) { "Semantic request handle capacity exceeded" }
		val value = nextHandleValue
		check(value < Long.MAX_VALUE) { "Semantic request handle sequence exhausted" }
		nextHandleValue += 1L
		return ReaderSemanticRequestHandle(value).also { handle ->
			requests[handle] = OwnedRequest(request, tokenAllocator.allocate())
		}
	}

	fun take(
		handle: ReaderSemanticRequestHandle,
		commandSessionGeneration: Long
	): ReaderSemanticExecutableRequest? {
		if (commandSessionGeneration != readerSessionGeneration || frozenDomain != null) return null
		return requests.remove(handle)?.executable
	}

	fun retire(handle: ReaderSemanticRequestHandle): Boolean = requests.remove(handle) != null
	fun clear() {
		requests.clear()
		restartRequests.clear()
	}

	internal fun allocateActivationToken(): ReaderLegacySourceLocalOpaqueToken =
		tokenAllocator.allocate()

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = when {
		domain.readerSessionGeneration != readerSessionGeneration ->
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
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
		return requests.values.map { request ->
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
				origin = ReaderLegacyResourceOrigin.Pending,
				state = ReaderLegacyResourceState.Registered,
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): Boolean {
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.SemanticCommandSlot
		) return false
		val entry = requests.entries.firstOrNull {
			it.value.activationToken == physicalIdentity.sourceLocalToken
		} ?: return false
		requests.remove(entry.key)
		restartRequests[entry.key] = entry.value
		onConfirmed(physicalIdentity)
		return true
	}

	fun restoreAfterTransitionActivation(domain: ReaderLegacyPhysicalDomain): Boolean {
		if (frozenDomain != domain) return false
		frozenDomain = null
		requests.putAll(restartRequests)
		restartRequests.clear()
		return true
	}
}

internal class ReaderSemanticCommandExecutor(
	private val registry: ReaderSemanticExecutableRequestRegistry,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator? = null
) {
	private data class Slot(
		val transitionId: ReaderTransitionId,
		val handle: ReaderSemanticRequestHandle,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private val activeSlots = linkedMapOf<ReaderSemanticCommandSlotId, Slot>()
	private val completedFrozenSlots = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private val outOfOrderRetiredSlotValues = sortedSetOf<Long>()
	private var contiguousRetiredThrough = 0L
	private var nextSlotValue = 1L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	val activeSlotCount: Int get() = activeSlots.size

	fun retirementSnapshot() = ReaderSemanticCommandSlotFenceSnapshot(
		contiguousRetiredThrough,
		outOfOrderRetiredSlotValues.toSet(),
		activeSlots.size
	)

	fun synchronize(
		command: ReaderTransitionCommand.RequestSemanticSynchronization,
		onReceipt: (ReaderPresentationEventReceipt) -> Unit
	): ReaderPortCommandResult {
		val handle = command.requestHandle
		if (frozenDomain != null) {
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		if (
			activeSlots.size >= ReaderMaximumActiveSemanticCommandSlots ||
			outOfOrderRetiredSlotValues.size >= ReaderMaximumOutOfOrderSemanticSlotTombstones
		) {
			registry.retire(handle)
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		val slotValue = nextSlotValue
		if (slotValue == Long.MAX_VALUE) {
			registry.retire(handle)
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		nextSlotValue += 1L
		val slotId = ReaderSemanticCommandSlotId(slotValue)
		activeSlots[slotId] = Slot(
			command.transitionId,
			handle,
			tokenAllocator?.allocate() ?: registry.allocateActivationToken()
		)
		val executable = registry.take(handle, command.transitionId.readerSessionGeneration)
		if (executable == null) {
			retireSlot(slotId)
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		val origin = ReaderPresentationEventOrigin.SemanticCommand(command.transitionId, slotId)
		return try {
			executable(origin) { receipt ->
				val slot = activeSlots[slotId] ?: return@executable
				if (receipt.origin != origin || slot.transitionId != command.transitionId) {
					return@executable
				}
				activeSlots.remove(slotId)
				if (frozenDomain != null) completedFrozenSlots += slot.activationToken
				retireSlotValue(slotId.value)
				onReceipt(receipt)
			}
			ReaderPortCommandResult.Accepted
		} catch (_: Throwable) {
			retireSlot(slotId)
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (
			domain.readerSessionGeneration != registry.readerSessionGeneration ||
			(frozenDomain != null && frozenDomain != domain)
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val registryResult = registry.freezeForTransitionActivation(domain)
		if (registryResult is ReaderPortCommandResult.Rejected) return registryResult
		frozenDomain = domain
		return ReaderPortCommandResult.Accepted
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		val domain = frozenDomain ?: return emptyList()
		return registry.snapshotFrozenOwnership() + activeSlots.values.map { slot ->
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
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.SemanticCommandSlot
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val token = physicalIdentity.sourceLocalToken
		val slotEntry = activeSlots.entries.firstOrNull { it.value.activationToken == token }
		if (slotEntry != null) {
			activeSlots.remove(slotEntry.key)
			retireSlotValue(slotEntry.key.value)
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (completedFrozenSlots.remove(token)) {
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		return if (registry.drainFrozenOwnership(physicalIdentity, onConfirmed)) {
			ReaderPortCommandResult.Accepted
		} else {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (frozenDomain != domain || !registry.restoreAfterTransitionActivation(domain)) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		frozenDomain = null
		completedFrozenSlots.clear()
		return ReaderPortCommandResult.Accepted
	}

	fun retireTransition(transitionId: ReaderTransitionId) {
		activeSlots.filterValues { it.transitionId == transitionId }.keys.toList().forEach(::retireSlot)
	}

	fun clear() {
		activeSlots.keys.toList().forEach(::retireSlot)
		registry.clear()
	}

	private fun retireSlot(slotId: ReaderSemanticCommandSlotId) {
		val slot = activeSlots.remove(slotId)
		if (slot != null && frozenDomain != null) {
			completedFrozenSlots += slot.activationToken
		}
		retireSlotValue(slotId.value)
	}

	private fun retireSlotValue(value: Long) {
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
}

internal class ReaderActivatedTransitionPorts(
	private val ports: ReaderProductionActivatedSessionPorts
) : ReaderResumableTransitionPorts {
	override val clock: ReaderTransitionClock = object : ReaderTransitionClock {
		override fun nowMillis(): Long = SystemClock.uptimeMillis()

		override fun schedule(
			atMillis: Long,
			action: () -> Unit
		): ReaderTransitionClockRegistration? = error(
			"Activated Task 6 transitions use only the fact-only timer"
		)
	}
	override val task6FactOnlyTimer: ReaderTask6FactOnlyTimerPort
		get() = ports.factOnlyTimer
	override val ownerAndInputPublication: ReaderOwnerAndInputPublicationPort
		get() = ports.ownerAndInput
	override val semanticCommand: ReaderSemanticCommandPort
		get() = ports.semantic

	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
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
			is ReaderTransitionCommand.ReleaseResource ->
				ports.resources.release(command) { onFact(it) }
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

internal class AndroidReaderTransitionClock(
	private val handler: Handler = Handler(Looper.getMainLooper())
) : ReaderTransitionClock {
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
