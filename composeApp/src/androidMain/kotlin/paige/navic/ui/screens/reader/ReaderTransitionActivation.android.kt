package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper

import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderCommittedPresentation
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.acceptsMaterialAllocation
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderInitialOriginKind
import paige.navic.reader.ReaderInitialOriginOwnerKind
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderMaterialGenerationAllocation
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderOwnerAndInputPublicationResult
import paige.navic.reader.ReaderOwnerAndInputPublicationSubject
import paige.navic.reader.ReaderReleaseLedgerCleanupStatus
import paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderTransitionOutcome
import paige.navic.reader.ReaderTransitionRetryability
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionInputLease
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceProvenance
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.deadlinePolicy
import paige.navic.reader.markReleaseOnlyCleanupDeadlineElapsed
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal enum class ReaderSessionActivationState {
	Legacy,
	Freezing,
	DrainingLegacy,
	ReadyToCommit,
	RestoringLegacy,
	ActivationBlocked,
	Activated,
	ReleaseOnly
}

@JvmInline
internal value class ReaderLegacyFreezeToken(val value: Long) {
	init { require(value > 0L) }
	override fun toString(): String = "ReaderLegacyFreezeToken(<redacted>)"
}

internal enum class ReaderLegacyInventorySource {
	Deck,
	RasterPreparation,
	RasterSnapshotCache,
	RasterDescriptorAndPendingCallback,
	RasterHydration,
	RasterPublication,
	RasterGenerationAndPersistence,
	RasterCaptureAndVisualState,
	RasterLiveValidation,
	RasterStoreAndCache,
	ForegroundWebViewOwnership,
	SemanticCommandSlot,
	LifecycleDelivery,
	DeadlineRegistration,
	FrameOrHandoff,
	Input
}

internal data class ReaderLegacyPhysicalDomain(
	val readerSessionGeneration: Long,
	val freezeToken: ReaderLegacyFreezeToken
) {
	init { require(readerSessionGeneration > 0L) }
	override fun hashCode(): Int = 0x524C5044
	override fun toString(): String = "ReaderLegacyPhysicalDomain(<redacted>)"
}

@JvmInline
internal value class ReaderLegacySourceLocalOpaqueToken(val value: Long) {
	init { require(value > 0L) }
	override fun toString(): String = "ReaderLegacySourceLocalOpaqueToken(<redacted>)"
}

internal class ReaderLegacySourceLocalTokenAllocator {
	private val nextToken = AtomicLong()

	fun allocate(): ReaderLegacySourceLocalOpaqueToken =
		ReaderLegacySourceLocalOpaqueToken(nextToken.incrementAndGet())
}

internal data class ReaderLegacyPhysicalIdentity(
	val domain: ReaderLegacyPhysicalDomain,
	val source: ReaderLegacyInventorySource,
	val sourceLocalToken: ReaderLegacySourceLocalOpaqueToken
) {
	override fun hashCode(): Int = 0x524C5049
	override fun toString(): String = "ReaderLegacyPhysicalIdentity(<redacted>)"
}

internal enum class ReaderLegacyResourceOrigin { Owned, Pending, Discovered }
internal enum class ReaderLegacyResourceState {
	Reserved, Running, Registered, RendererOwned, Prepared, Visible, ReleaseRequested, Released
}

internal data class ReaderFrozenLegacyResource(
	val freezeToken: ReaderLegacyFreezeToken,
	val physicalIdentity: ReaderLegacyPhysicalIdentity,
	val kind: ReaderTransitionResourceKind,
	val binding: ReaderPresentationBinding?,
	val visibleOwner: ReaderPresentationFrameOwner?,
	val origin: ReaderLegacyResourceOrigin,
	val state: ReaderLegacyResourceState,
	val mayBeCommittedPredecessor: Boolean,
	val provenance: ReaderTransitionResourceProvenance = ReaderTransitionResourceProvenance.AdoptedLegacy
) {
	init {
		require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
		require(physicalIdentity.domain.freezeToken == freezeToken)
		require(!mayBeCommittedPredecessor || state == ReaderLegacyResourceState.Visible)
		require(!mayBeCommittedPredecessor || (binding != null && visibleOwner != null))
		require(!mayBeCommittedPredecessor || kind == readerAdoptedResourceKindFor(requireNotNull(visibleOwner)))
	}
}

/** A source that participated in fencing, including the truthful zero-row case. */
internal data class ReaderLegacyConnectedSourceInventory(
	val source: ReaderLegacyInventorySource,
	val domain: ReaderLegacyPhysicalDomain,
	val resources: List<ReaderFrozenLegacyResource>
) {
	init {
		require(resources.all { resource ->
			resource.freezeToken == domain.freezeToken &&
				resource.physicalIdentity.domain == domain &&
				resource.physicalIdentity.source == source
		})
		require(resources.distinctBy { it.physicalIdentity }.size == resources.size)
	}

	override fun hashCode(): Int = 0x524C4353
	override fun toString(): String = "ReaderLegacyConnectedSourceInventory(<redacted>)"
}

internal data class ReaderExactPhysicalOwnerDescriptor(
	val kind: ReaderTransitionResourceKind,
	val binding: ReaderPresentationBinding? = null,
	val visibleOwner: ReaderPresentationFrameOwner? = null,
	val origin: ReaderLegacyResourceOrigin = ReaderLegacyResourceOrigin.Owned,
	val state: ReaderLegacyResourceState,
	val mayBeCommittedPredecessor: Boolean = false
) {
	init {
		require(!mayBeCommittedPredecessor || state == ReaderLegacyResourceState.Visible)
		require(!mayBeCommittedPredecessor || (binding != null && visibleOwner != null))
		require(
			!mayBeCommittedPredecessor ||
				kind == readerAdoptedResourceKindFor(requireNotNull(visibleOwner))
		)
	}
}

/** Exact per-owner bookkeeping shared by real physical source implementations. */
internal class ReaderExactPhysicalOwnerRegistry(
	private val source: ReaderLegacyInventorySource,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) {
	internal class Owner internal constructor(
		internal val token: ReaderLegacySourceLocalOpaqueToken,
		internal val descriptor: ReaderExactPhysicalOwnerDescriptor,
		internal val cancelPhysical: (() -> Boolean)?
	) {
		internal var state = descriptor.state
		internal var completed = false
		internal var drainRequested = false
		internal var cancellationIssued = false
		internal var drainIdentity: ReaderLegacyPhysicalIdentity? = null
		internal var drainConfirmation: ((ReaderLegacyPhysicalIdentity) -> Unit)? = null
	}

	private val lock = Any()
	private val owners = linkedSetOf<Owner>()
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	val isFrozen: Boolean
		get() = synchronized(lock) { frozenDomain != null }

	fun admit(
		descriptors: List<ReaderExactPhysicalOwnerDescriptor>,
		cancelPhysical: (() -> Boolean)? = null
	): List<Owner>? = synchronized(lock) {
		if (frozenDomain != null) return@synchronized null
		descriptors.map { descriptor ->
			Owner(tokenAllocator.allocate(), descriptor, cancelPhysical).also(owners::add)
		}
	}

	fun admit(
		descriptor: ReaderExactPhysicalOwnerDescriptor,
		cancelPhysical: (() -> Boolean)? = null
	): Owner? = admit(listOf(descriptor), cancelPhysical)?.single()

	fun admitLateDiscoveredDuringFrozenEpoch(
		domain: ReaderLegacyPhysicalDomain,
		descriptor: ReaderExactPhysicalOwnerDescriptor,
		cancelPhysical: (() -> Boolean)? = null
	): Owner? = synchronized(lock) {
		if (frozenDomain != domain) return@synchronized null
		Owner(tokenAllocator.allocate(), descriptor, cancelPhysical).also(owners::add)
	}

	fun updateState(owner: Owner, state: ReaderLegacyResourceState): Boolean =
		synchronized(lock) {
			if (owner !in owners || owner.completed) return@synchronized false
			if (owner.state != state) {
				owner.state = state
			}
			true
		}

	fun complete(owner: Owner): Boolean {
		var confirmation: (() -> Unit)? = null
		val completed = synchronized(lock) {
			if (owner !in owners || owner.completed) return@synchronized false
			owner.completed = true
			if (frozenDomain == null) {
				owners.remove(owner)
			} else if (owner.drainRequested) {
				owners.remove(owner)
				val identity = owner.drainIdentity
				val callback = owner.drainConfirmation
				owner.drainIdentity = null
				owner.drainConfirmation = null
				if (identity != null && callback != null) {
					confirmation = { callback(identity) }
				}
			}
			true
		}
		runCatching { confirmation?.invoke() }
		return completed
	}

	fun complete(owned: Collection<Owner>) {
		owned.forEach(::complete)
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
			else -> invalidResource()
		}
	}

	fun connectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized null
		ReaderLegacyConnectedSourceInventory(source, domain, frozenRowsLocked(domain))
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		frozenRowsLocked(domain)
	}

	private fun frozenRowsLocked(
		domain: ReaderLegacyPhysicalDomain
	): List<ReaderFrozenLegacyResource> = owners.map { owner ->
		ReaderFrozenLegacyResource(
			freezeToken = domain.freezeToken,
			physicalIdentity = ReaderLegacyPhysicalIdentity(domain, source, owner.token),
			kind = owner.descriptor.kind,
			binding = owner.descriptor.binding,
			visibleOwner = owner.descriptor.visibleOwner,
			origin = owner.descriptor.origin,
			state = when {
				owner.drainRequested -> ReaderLegacyResourceState.ReleaseRequested
				owner.completed -> ReaderLegacyResourceState.Released
				else -> owner.state
			},
			mayBeCommittedPredecessor = owner.descriptor.mayBeCommittedPredecessor
		)
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		var cancel: (() -> Boolean)? = null
		var confirmNow = false
		val owner = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source != source
			) return@synchronized null
			val matched = owners.firstOrNull { it.token == physicalIdentity.sourceLocalToken }
				?: return@synchronized null
			if (matched.drainRequested) return@synchronized null
			matched.drainRequested = true
			matched.drainIdentity = physicalIdentity
			matched.drainConfirmation = onConfirmed
			if (matched.completed) {
				owners.remove(matched)
				matched.drainIdentity = null
				matched.drainConfirmation = null
				confirmNow = true
			} else if (!matched.cancellationIssued && matched.cancelPhysical != null) {
				matched.cancellationIssued = true
				cancel = matched.cancelPhysical
			}
			matched
		} ?: return invalidResource()
		if (confirmNow) {
			runCatching { onConfirmed(physicalIdentity) }
			return ReaderPortCommandResult.Accepted
		}
		val cancellation = cancel ?: return ReaderPortCommandResult.Accepted
		val accepted = try {
			cancellation()
		} catch (_: Throwable) {
			false
		}
		if (accepted) return ReaderPortCommandResult.Accepted
		val rolledBack = synchronized(lock) {
			if (owner.completed || owner !in owners) return@synchronized false
			owner.drainRequested = false
			owner.cancellationIssued = false
			owner.drainIdentity = null
			owner.drainConfirmation = null
			true
		}
		return if (rolledBack) invalidResource() else ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (
			frozenDomain != domain ||
			owners.any { owner -> owner.drainRequested && !owner.completed }
		) return@synchronized invalidResource()
		owners.removeAll { owner -> owner.completed }
		frozenDomain = null
		ReaderPortCommandResult.Accepted
	}

	private fun invalidResource(): ReaderPortCommandResult = ReaderPortCommandResult.Rejected(
		ReaderTransitionFailureReason.InvalidLegacyResource
	)
}

internal sealed interface ReaderLegacyResourceInventory {
	data object Incomplete : ReaderLegacyResourceInventory
	data class Complete(
		val freezeToken: ReaderLegacyFreezeToken,
		val snapshotSequence: Long,
		val discoveryVersion: Long,
		val completedSources: Set<ReaderLegacyInventorySource>,
		val resources: List<ReaderFrozenLegacyResource>
	) : ReaderLegacyResourceInventory {
		init {
			require(snapshotSequence > 0L)
			require(discoveryVersion > 0L)
			require(completedSources == ReaderLegacyInventorySource.entries.toSet())
			require(resources.all { it.freezeToken == freezeToken })
			require(resources.distinctBy { it.physicalIdentity }.size == resources.size)
		}
	}
}

@JvmInline
internal value class ReaderLegacyRestorationCheckpointId(val value: Long) {
	init { require(value > 0L) }
	override fun toString(): String = "ReaderLegacyRestorationCheckpointId(<redacted>)"
}

internal data class ReaderLegacySourceRestartHandle(
	val source: ReaderLegacyInventorySource,
	val opaqueId: Long
) {
	init { require(opaqueId > 0L) }
	override fun hashCode(): Int = 0x52535248
	override fun toString(): String = "ReaderLegacySourceRestartHandle(<redacted>)"
}

internal data class ReaderLegacyRestorationCheckpoint(
	val id: ReaderLegacyRestorationCheckpointId,
	val freezeToken: ReaderLegacyFreezeToken,
	val routeGeneration: Long,
	val completedSources: Set<ReaderLegacyInventorySource>,
	val restartHandles: Map<ReaderLegacyInventorySource, ReaderLegacySourceRestartHandle>,
	val initialOwner: ReaderPresentationFrameOwner,
	val initialBinding: ReaderPresentationBinding?,
	val initialPhysicalIdentity: ReaderLegacyPhysicalIdentity?,
	val initialResourceKind: ReaderTransitionResourceKind?,
	val initialProvenance: ReaderTransitionResourceProvenance?,
	val requestedLease: ReaderInitialPresentationInputLease,
	val physicalLease: ReaderInitialPresentationInputLease
) {
	init {
		require(routeGeneration > 0L)
		require(completedSources == ReaderLegacyInventorySource.entries.toSet())
		require(restartHandles.keys == completedSources)
		require(restartHandles.all { (source, handle) -> handle.source == source })
		val neutral = initialOwner == ReaderPresentationFrameOwner.Neutral
		require(neutral == (
			initialBinding == null && initialPhysicalIdentity == null &&
				initialResourceKind == null && initialProvenance == null
		))
		require(initialResourceKind == readerAdoptedResourceKindFor(initialOwner))
		require(
			initialProvenance == if (neutral) null
			else ReaderTransitionResourceProvenance.AdoptedLegacy
		)
		require(initialPhysicalIdentity == null ||
			initialPhysicalIdentity.domain.freezeToken == freezeToken)
		require(readerInitialInputLeaseIsNoBroader(physicalLease, requestedLease))
	}

	override fun hashCode(): Int = 0x524C5243
	override fun toString(): String = "ReaderLegacyRestorationCheckpoint(<redacted>)"
}

internal sealed interface ReaderLegacyRestorationResult {
	data object Restored : ReaderLegacyRestorationResult
	data class Failed(val reason: ReaderTransitionFailureReason) : ReaderLegacyRestorationResult
}

internal sealed interface ReaderLegacyCommitRestoredResult {
	data object Applied : ReaderLegacyCommitRestoredResult
	data class Rejected(val reason: ReaderTransitionFailureReason) : ReaderLegacyCommitRestoredResult
}

internal interface ReaderLegacyFreezeAndInventoryPort {
	fun freeze(): ReaderLegacyFreezeToken
	fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken): ReaderLegacyRestorationCheckpoint?
	fun inventory(token: ReaderLegacyFreezeToken): ReaderLegacyResourceInventory
	fun drain(
		token: ReaderLegacyFreezeToken,
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult
	fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken): ReaderPortCommandResult
	fun restoreFromActivationCheckpoint(
		checkpoint: ReaderLegacyRestorationCheckpoint,
		source: ReaderLegacyInventorySource,
		onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
	): ReaderPortCommandResult
	fun commitRestoredLegacy(
		checkpoint: ReaderLegacyRestorationCheckpoint
	): ReaderLegacyCommitRestoredResult
	fun finalizeActivatedHandoff(
		token: ReaderLegacyFreezeToken,
		selected: ReaderImportedLegacyResourceRegistration?,
		confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
	): ReaderPortCommandResult = ReaderPortCommandResult.Rejected(
		ReaderTransitionFailureReason.ActivationPrerequisiteMissing
	)
}

internal class ReaderLegacyInventoryFixedPointTracker(
	private val freezeToken: ReaderLegacyFreezeToken
) {
	private var previous: ReaderLegacyResourceInventory.Complete? = null

	fun accept(snapshot: ReaderLegacyResourceInventory): Boolean {
		val complete = snapshot as? ReaderLegacyResourceInventory.Complete ?: run {
			previous = null
			return false
		}
		if (complete.freezeToken != freezeToken) {
			previous = null
			return false
		}
		val prior = previous
		previous = complete
		return prior != null &&
			complete.snapshotSequence == prior.snapshotSequence + 1L &&
			complete.discoveryVersion == prior.discoveryVersion &&
			complete.completedSources == prior.completedSources &&
			complete.resources == prior.resources
	}
}

internal sealed interface ReaderAdoptedPredecessorSelection {
	data object Neutral : ReaderAdoptedPredecessorSelection
	data class Selected(val resource: ReaderFrozenLegacyResource) : ReaderAdoptedPredecessorSelection
	data object Ambiguous : ReaderAdoptedPredecessorSelection
}

internal object ReaderAdoptedPredecessorSelector {
	fun select(resources: List<ReaderFrozenLegacyResource>): ReaderAdoptedPredecessorSelection {
		val visible = resources.filter { resource ->
			val binding = resource.binding
			val owner = resource.visibleOwner
			resource.mayBeCommittedPredecessor &&
				resource.state == ReaderLegacyResourceState.Visible &&
				binding != null &&
				owner != null &&
				owner.matchesAdoptedBinding(binding) &&
				resource.kind == readerAdoptedResourceKindFor(owner)
		}.distinctBy(ReaderFrozenLegacyResource::physicalIdentity)
		return when (visible.size) {
			0 -> ReaderAdoptedPredecessorSelection.Neutral
			1 -> ReaderAdoptedPredecessorSelection.Selected(visible.single())
			else -> ReaderAdoptedPredecessorSelection.Ambiguous
		}
	}
}

private fun ReaderPresentationFrameOwner.matchesAdoptedBinding(
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	ReaderPresentationFrameOwner.Neutral -> false
	is ReaderPresentationFrameOwner.ShellCover -> proof.binding == binding
	is ReaderPresentationFrameOwner.NativePage -> proof.binding == binding
	is ReaderPresentationFrameOwner.Curl -> frame.binding == binding
	is ReaderPresentationFrameOwner.LiveEngine -> proof.binding == binding
}

internal fun readerAdoptedResourceKindFor(owner: ReaderPresentationFrameOwner): ReaderTransitionResourceKind? =
	when (owner) {
		ReaderPresentationFrameOwner.Neutral -> null
		is ReaderPresentationFrameOwner.NativePage,
		is ReaderPresentationFrameOwner.Curl -> ReaderTransitionResourceKind.Deck
		is ReaderPresentationFrameOwner.ShellCover,
		is ReaderPresentationFrameOwner.LiveEngine -> ReaderTransitionResourceKind.FrameHandoff
	}

internal data class ReaderAdoptedPredecessorSeed(
	val id: ReaderAdoptedPredecessorSeedId,
	val physicalIdentity: ReaderLegacyPhysicalIdentity,
	val resourceKind: ReaderTransitionResourceKind,
	val binding: ReaderPresentationBinding,
	val owner: ReaderPresentationFrameOwner,
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	val provenance: ReaderTransitionResourceProvenance = ReaderTransitionResourceProvenance.AdoptedLegacy
) {
	init {
		require(readerSessionGeneration > 0L)
		require(coordinatorEpoch > 0L)
		require(owner != ReaderPresentationFrameOwner.Neutral)
		require(resourceKind == readerAdoptedResourceKindFor(owner))
		require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
		require(physicalIdentity.domain.readerSessionGeneration == readerSessionGeneration)
	}

	override fun hashCode(): Int = 0x52415053
	override fun toString(): String = "ReaderAdoptedPredecessorSeed(<redacted>)"
}

internal data class ReaderImportedLegacyResourceRegistration(
	val physicalIdentity: ReaderLegacyPhysicalIdentity,
	val registration: ReaderTransitionResourceRegistration
) {
	init {
		require(
			registration.key.ownerId is ReaderTransitionResourceOwnerId.AdoptedPredecessor
		)
		require(
			physicalIdentity.domain.readerSessionGeneration ==
				registration.retirementOrder.readerSessionGeneration
		)
	}

	override fun hashCode(): Int = 0x52494D50
	override fun toString(): String = "ReaderImportedLegacyResourceRegistration(<redacted>)"
}

internal data class ReaderImportedLegacyReleaseDispatch(
	val command: ReaderTransitionCommand.ReleaseResource,
	val imported: ReaderImportedLegacyResourceRegistration
) {
	init { require(command.identity.registration == imported.registration) }
	override fun hashCode(): Int = 0x52494C44
	override fun toString(): String = "ReaderImportedLegacyReleaseDispatch(<redacted>)"
}

internal enum class ReaderActivatedPort {
	Gateway,
	Semantic,
	MaterialAllocation,
	Raster,
	Deck,
	Frame,
	OwnerAndInput,
	InputSafety,
	LifecycleFact,
	FactOnlyTimer,
	Resources,
	ResourceSink
}

internal interface ReaderActivatedGatewayPort {
	fun routeIntent(fact: paige.navic.reader.ReaderTransitionFact.Intent): ReaderPortCommandResult
	fun routeReceipt(receipt: ReaderPresentationEventReceipt): ReaderPortCommandResult
	fun closeToReleaseOnly()
}

internal sealed interface ReaderSemanticCommandResult {
	data object Accepted : ReaderSemanticCommandResult
	data class RejectedBeforeMutation(
		val reason: ReaderTransitionFailureReason
	) : ReaderSemanticCommandResult
	data object ThrewBeforeMutation : ReaderSemanticCommandResult
	data object RejectedAfterMutationStarted : ReaderSemanticCommandResult
	data object ThrewAfterMutationStarted : ReaderSemanticCommandResult
}

internal fun interface ReaderSemanticCommandRegistration {
	fun retire()
}

internal fun interface ReaderSemanticCommandPort {
	fun synchronize(
		command: ReaderTransitionCommand.RequestSemanticSynchronization,
		onRegistration: (ReaderSemanticCommandRegistration) -> Unit,
		onReceipt: (ReaderPresentationEventReceipt) -> Unit
	): ReaderSemanticCommandResult
}

internal fun interface ReaderMaterialGenerationAllocationPort {
	fun allocate(
		command: ReaderTransitionCommand.AllocateMaterialBinding,
		onFact: (paige.navic.reader.ReaderTransitionFact.MaterialBindingAllocated) -> Unit
	): ReaderPortCommandResult
}

internal fun interface ReaderActivatedRasterPreparationPort {
	fun prepare(
		command: ReaderTransitionCommand.RequestRasterPreparation,
		onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
	): ReaderPortCommandResult
}

internal fun interface ReaderActivatedDeckPort {
	fun reserve(
		command: ReaderTransitionCommand.ReserveDeck,
		onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
	): ReaderPortCommandResult
}

internal interface ReaderFramePresentationPort {
	fun prepareTarget(
		command: ReaderTransitionCommand.PrepareFrameTarget,
		onFact: (ReaderTransitionFact) -> Unit
	): ReaderPortCommandResult

	fun present(
		command: ReaderTransitionCommand.RequestFramePresentation,
		onFact: (ReaderTransitionFact) -> Unit
	): ReaderPortCommandResult
}

internal fun interface ReaderInputLeasePort {
	fun narrowOrVeto(lease: ReaderTransitionInputLease): ReaderTransitionInputLease
}

internal interface ReaderTransitionResourcePort {
	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (paige.navic.reader.ReaderTransitionFact.ResourceReleased) -> Unit
	): ReaderPortCommandResult
	fun releaseLegacy(
		command: ReaderTransitionCommand.ReleaseResource,
		imported: ReaderImportedLegacyResourceRegistration,
		onConfirmed: (
			ReaderLegacyPhysicalIdentity,
			paige.navic.reader.ReaderTransitionFact.ResourceReleased
		) -> Unit
	): ReaderPortCommandResult
	fun cancelOwnedWork(command: ReaderTransitionCommand.CancelOwnedWork): ReaderPortCommandResult
}

internal interface ReaderReleaseOnlySinkPort {
	fun observe(fact: paige.navic.reader.ReaderTransitionFact.ResourceObserved): ReaderPortCommandResult
	fun observeLegacy(imported: ReaderImportedLegacyResourceRegistration): ReaderPortCommandResult
	fun confirm(fact: paige.navic.reader.ReaderTransitionFact.ResourceReleased): ReaderPortCommandResult
	fun confirmLegacy(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		fact: paige.navic.reader.ReaderTransitionFact.ResourceReleased
	): ReaderPortCommandResult
	fun release(command: ReaderTransitionCommand.ReleaseResource): ReaderPortCommandResult
}

internal interface ReaderOwnerAndInputPublicationPort {
	fun publish(
		command: ReaderTransitionCommand.CommitOwnerAndInputLease
	): ReaderOwnerAndInputPublicationResult

	fun publish(
		command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
	): ReaderOwnerAndInputPublicationResult
}

internal enum class ReaderTask6LifecycleSafetyKind {
	VisibilityLost,
	HostDetached,
	WebViewDetached,
	RendererUnavailable,
	PublicationClosed
}

internal data class ReaderTask6LifecycleSafetyFact(
	val deliverySequence: Long,
	val kind: ReaderTask6LifecycleSafetyKind
) {
	init { require(deliverySequence > 0L) }
}

internal fun interface ReaderTask6LifecycleFactPort {
	fun enqueueSafetyFact(fact: ReaderTask6LifecycleSafetyFact): ReaderPortCommandResult
}

internal data class ReaderTestActivatedSessionPorts(
	val gateway: ReaderActivatedGatewayPort?,
	val semantic: ReaderSemanticCommandPort?,
	val materialAllocation: ReaderMaterialGenerationAllocationPort?,
	val raster: ReaderActivatedRasterPreparationPort?,
	val deck: ReaderActivatedDeckPort?,
	val frame: ReaderFramePresentationPort?,
	val ownerAndInput: ReaderOwnerAndInputPublicationPort?,
	val inputSafety: ReaderInputLeasePort?,
	val resources: ReaderTransitionResourcePort?,
	val releaseSink: ReaderReleaseOnlySinkPort?,
	val lifecycleFacts: ReaderTask6LifecycleFactPort?,
	val factOnlyTimer: ReaderTask6FactOnlyTimerPort?
) {
	val complete: Boolean get() = listOf(
		gateway,
		semantic,
		materialAllocation,
		raster,
		deck,
		frame,
		ownerAndInput,
		inputSafety,
		resources,
		releaseSink,
		lifecycleFacts,
		factOnlyTimer
	).all { it != null }

	fun without(port: ReaderActivatedPort): ReaderTestActivatedSessionPorts = when (port) {
		ReaderActivatedPort.Gateway -> copy(gateway = null)
		ReaderActivatedPort.Semantic -> copy(semantic = null)
		ReaderActivatedPort.MaterialAllocation -> copy(materialAllocation = null)
		ReaderActivatedPort.Raster -> copy(raster = null)
		ReaderActivatedPort.Deck -> copy(deck = null)
		ReaderActivatedPort.Frame -> copy(frame = null)
		ReaderActivatedPort.OwnerAndInput -> copy(ownerAndInput = null)
		ReaderActivatedPort.InputSafety -> copy(inputSafety = null)
		ReaderActivatedPort.LifecycleFact -> copy(lifecycleFacts = null)
		ReaderActivatedPort.FactOnlyTimer -> copy(factOnlyTimer = null)
		ReaderActivatedPort.Resources -> copy(resources = null)
		ReaderActivatedPort.ResourceSink -> copy(releaseSink = null)
	}
}

internal sealed interface ReaderProductionActivatedPortCapability

internal data class ReaderReservedNeutralBootstrapRequest(
	val handle: ReaderSemanticRequestHandle,
	val readerSessionGeneration: Long
) {
	init { require(readerSessionGeneration > 0L) }
	override fun hashCode(): Int = 0x524E4252
	override fun toString(): String = "ReaderReservedNeutralBootstrapRequest(<redacted>)"
}

internal data class ReaderInitialActivationDecision(
	val origin: ReaderInitialCommittedPresentationOrigin,
	val adoptedSeed: ReaderAdoptedPredecessorSeed?,
	val adoptedResource: ReaderImportedLegacyResourceRegistration?,
	val neutralBootstrapReservation: ReaderReservedNeutralBootstrapRequest?
) {
	init {
		when (val initial = origin) {
			is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> {
				val seed = requireNotNull(adoptedSeed)
				val imported = requireNotNull(adoptedResource)
				require(initial.seedId == seed.id)
				require(initial.readerSessionGeneration == seed.readerSessionGeneration)
				require(initial.coordinatorEpoch == seed.coordinatorEpoch)
				require(initial.owner == seed.owner)
				require(initial.binding == seed.binding)
				require(initial.provenance == seed.provenance)
				require(imported.physicalIdentity == seed.physicalIdentity)
				require(initial.resource == imported.registration)
				require(neutralBootstrapReservation == null)
			}
			is ReaderInitialCommittedPresentationOrigin.Neutral -> {
				require(adoptedSeed == null)
				require(adoptedResource == null)
				val reservation = requireNotNull(neutralBootstrapReservation)
				require(reservation.readerSessionGeneration == initial.readerSessionGeneration)
			}
		}
		require(readerInitialInputLeaseIsNoBroader(origin.physicalLease, origin.requestedLease))
	}

	override fun hashCode(): Int = 0x52494144
	override fun toString(): String = "ReaderInitialActivationDecision(<redacted>)"
}

internal enum class ReaderInitialActivationLeaseKind { None, ChromeOnly, CoverActions, NativePage }

internal data class ReaderInitialActivationSanitizedProjection(
	val originKind: ReaderInitialOriginKind,
	val ownerKind: ReaderInitialOriginOwnerKind?,
	val resourceKind: ReaderTransitionResourceKind?,
	val requestedLeaseKind: ReaderInitialActivationLeaseKind,
	val physicalLeaseKind: ReaderInitialActivationLeaseKind,
	val hasNeutralBootstrapReservation: Boolean,
	val activationState: ReaderSessionActivationState?
)

internal val ReaderInitialActivationDecision.sanitizedProjection:
	ReaderInitialActivationSanitizedProjection
	get() {
		val adopted = origin as? ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor
		return ReaderInitialActivationSanitizedProjection(
			originKind = if (adopted == null) {
				ReaderInitialOriginKind.Neutral
			} else ReaderInitialOriginKind.AdoptedPredecessor,
			ownerKind = adopted?.owner?.activationOwnerKind(),
			resourceKind = adopted?.resource?.key?.kind,
			requestedLeaseKind = origin.requestedLease.activationLeaseKind(),
			physicalLeaseKind = origin.physicalLease.activationLeaseKind(),
			hasNeutralBootstrapReservation = neutralBootstrapReservation != null,
			activationState = null
		)
	}

private fun ReaderPresentationFrameOwner.activationOwnerKind(): ReaderInitialOriginOwnerKind =
	when (this) {
		ReaderPresentationFrameOwner.Neutral -> error("Neutral activation has no adopted owner kind")
		is ReaderPresentationFrameOwner.ShellCover -> ReaderInitialOriginOwnerKind.ShellCover
		is ReaderPresentationFrameOwner.NativePage -> ReaderInitialOriginOwnerKind.NativePage
		is ReaderPresentationFrameOwner.Curl -> ReaderInitialOriginOwnerKind.Curl
		is ReaderPresentationFrameOwner.LiveEngine -> ReaderInitialOriginOwnerKind.LiveEngine
	}

private fun ReaderInitialPresentationInputLease.activationLeaseKind():
	ReaderInitialActivationLeaseKind = when (this) {
		ReaderInitialPresentationInputLease.None -> ReaderInitialActivationLeaseKind.None
		ReaderInitialPresentationInputLease.ChromeOnly -> ReaderInitialActivationLeaseKind.ChromeOnly
		ReaderInitialPresentationInputLease.CoverActions -> ReaderInitialActivationLeaseKind.CoverActions
		is ReaderInitialPresentationInputLease.NativePage -> ReaderInitialActivationLeaseKind.NativePage
	}

internal data class ReaderActivatedSessionInstallation(
	val ports: ReaderProductionActivatedSessionPorts,
	val initialDecision: ReaderInitialActivationDecision,
	val releaseLedger: ReaderTransitionReleaseLedger
) {
	override fun hashCode(): Int = 0x52415349
	override fun toString(): String = "ReaderActivatedSessionInstallation(<redacted>)"
}

internal data class ReaderActivatedReleaseOnlyPortAuthority(
	val resources: ReaderTransitionResourcePort,
	val releaseSink: ReaderReleaseOnlySinkPort,
	val factOnlyTimer: ReaderTask6FactOnlyTimerPort
) {
	override fun hashCode(): Int = 0x52504F41
	override fun toString(): String = "ReaderActivatedReleaseOnlyPortAuthority(<redacted>)"
}

internal sealed interface ReaderActivatedSessionPortAuthority {
	data class Production(
		val ports: ReaderProductionActivatedSessionPorts
	) : ReaderActivatedSessionPortAuthority {
		override fun hashCode(): Int = 0x52415050
		override fun toString(): String = "ReaderActivatedSessionPortAuthority.Production(<redacted>)"
	}

	data class Test(
		val ports: ReaderTestActivatedSessionPorts
	) : ReaderActivatedSessionPortAuthority {
		override fun hashCode(): Int = 0x52415054
		override fun toString(): String = "ReaderActivatedSessionPortAuthority.Test(<redacted>)"
	}
}

private fun ReaderActivatedSessionPortAuthority.releaseOnlyAuthority():
	ReaderActivatedReleaseOnlyPortAuthority = when (this) {
	is ReaderActivatedSessionPortAuthority.Production -> ReaderActivatedReleaseOnlyPortAuthority(
		resources = ports.resources,
		releaseSink = ports.releaseSink,
		factOnlyTimer = ports.factOnlyTimer
	)
	is ReaderActivatedSessionPortAuthority.Test -> ReaderActivatedReleaseOnlyPortAuthority(
		resources = requireNotNull(ports.resources),
		releaseSink = requireNotNull(ports.releaseSink),
		factOnlyTimer = requireNotNull(ports.factOnlyTimer)
	)
}

internal data class ReaderActivatedSessionSnapshot(
	val portAuthority: ReaderActivatedSessionPortAuthority?,
	val initialDecision: ReaderInitialActivationDecision?,
	val reservedNeutralBootstrap: ReaderReservedNeutralBootstrapRequest?,
	val journal: ReaderTransitionJournal,
	val releaseLedger: ReaderTransitionReleaseLedger,
	val releaseOnlyPortAuthority: ReaderActivatedReleaseOnlyPortAuthority? = null,
	val state: ReaderSessionActivationState = ReaderSessionActivationState.Activated,
	val commandEgressOpen: Boolean = true
) {
	init {
		when (state) {
			ReaderSessionActivationState.Activated -> {
				require(portAuthority != null)
				require(releaseOnlyPortAuthority == null)
				require(commandEgressOpen)
				val decision = requireNotNull(initialDecision)
				require(reservedNeutralBootstrap === decision.neutralBootstrapReservation)
				require(
					journal.committed == ReaderCommittedPresentation.Initial(decision.origin)
				)
			}
			ReaderSessionActivationState.ReleaseOnly -> {
				require(portAuthority == null)
				require(releaseOnlyPortAuthority != null)
				require(initialDecision == null)
				require(reservedNeutralBootstrap == null)
				require(!commandEgressOpen)
			}
			else -> error("Installed session snapshot must be activated or release-only")
		}
		require(journal.lastTransitionSequence == 0L)
		require(journal.lastIssuedTransitionIdentity == null)
	}

	override fun hashCode(): Int = 0x52415353
	override fun toString(): String = "ReaderActivatedSessionSnapshot(<redacted>)"
}

internal val ReaderActivatedSessionSnapshot.sanitizedProjection:
	ReaderInitialActivationSanitizedProjection
	get() {
		initialDecision?.let { decision ->
			return decision.sanitizedProjection.copy(activationState = state)
		}
		check(state == ReaderSessionActivationState.ReleaseOnly)
		val initial = (journal.committed as ReaderCommittedPresentation.Initial).origin
		val adopted = initial as? ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor
		return ReaderInitialActivationSanitizedProjection(
			originKind = if (adopted == null) {
				ReaderInitialOriginKind.Neutral
			} else ReaderInitialOriginKind.AdoptedPredecessor,
			ownerKind = adopted?.owner?.activationOwnerKind(),
			resourceKind = adopted?.resource?.key?.kind,
			requestedLeaseKind = initial.requestedLease.activationLeaseKind(),
			physicalLeaseKind = initial.physicalLease.activationLeaseKind(),
			hasNeutralBootstrapReservation = false,
			activationState = state
		)
	}

internal sealed interface ReaderActivationInstallResult {
	data object Installed : ReaderActivationInstallResult
	data object Pending : ReaderActivationInstallResult
	data class Rejected(val reason: ReaderTransitionFailureReason) : ReaderActivationInstallResult
}

internal class ReaderActivatedSessionSnapshotStore(
	private val postFinalizationCleanupToMain: ((() -> Unit) -> Boolean) = { action ->
		Handler(Looper.getMainLooper()).post(action)
	}
) {
	private var installedSnapshot: ReaderActivatedSessionSnapshot? = null
	private val finalizationPhysicalRelease = ReaderPhysicalReleaseCommandAdapter()
	private val finalizationCleanupExpiryQueued = AtomicBoolean(false)
	private var finalizationCleanupTimer: ReaderTask6ReleaseOnlyTimerRegistration? = null
	var atomicWriteCount: Int = 0
		private set

	val state: ReaderSessionActivationState
		get() = installedSnapshot?.state ?: ReaderSessionActivationState.Legacy
	val commandEgressOpen: Boolean
		get() = installedSnapshot?.commandEgressOpen == true
	val initialDecision: ReaderInitialActivationDecision
		get() = requireNotNull(requireNotNull(installedSnapshot).initialDecision)
	val journal: ReaderTransitionJournal
		get() = requireNotNull(installedSnapshot).journal
	val reservedNeutralBootstrap: ReaderReservedNeutralBootstrapRequest?
		get() = requireNotNull(installedSnapshot).reservedNeutralBootstrap
	val snapshot: ReaderActivatedSessionSnapshot? get() = installedSnapshot

	internal fun install(snapshot: ReaderActivatedSessionSnapshot): Boolean {
		if (installedSnapshot != null) return false
		installedSnapshot = snapshot
		atomicWriteCount += 1
		return true
	}

	internal fun failClosedAfterFinalizationRejection(
		expectedDecision: ReaderInitialActivationDecision
	): Boolean {
		val current = installedSnapshot ?: return false
		if (
			current.state != ReaderSessionActivationState.Activated ||
			current.initialDecision !== expectedDecision
		) return false
		val authority = requireNotNull(current.portAuthority).releaseOnlyAuthority()
		val reduction = current.journal.reduce(ReaderTransitionFact.PublicationClosed(null))
		val cleanup = requireNotNull(reduction.state.releaseOnlyCleanup)
		val releaseCommands = reduction.commands
			.filterIsInstance<ReaderTransitionCommand.RequestResourceRelease>()
			.mapNotNull { request ->
				val registration = request.registration ?: return@mapNotNull null
				current.releaseLedger.requestRelease(
					request.issuer,
					registration,
					cleanup.key
				)
			}
		val cleanupStatus = current.releaseLedger.cleanupStatus(cleanup.key)
		val journal = if (cleanupStatus == ReaderReleaseLedgerCleanupStatus.EmptyReleased) {
			reduction.state.copy(
				releaseOnlyCleanup = cleanup.copy(
					deadlineStatus =
						ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting
				)
			)
		} else {
			reduction.state
		}
		installedSnapshot = ReaderActivatedSessionSnapshot(
			portAuthority = null,
			initialDecision = null,
			reservedNeutralBootstrap = null,
			journal = journal,
			releaseLedger = current.releaseLedger,
			releaseOnlyPortAuthority = authority,
			state = ReaderSessionActivationState.ReleaseOnly,
			commandEgressOpen = false
		)
		atomicWriteCount += 1
		if (cleanupStatus == ReaderReleaseLedgerCleanupStatus.EmptyReleased) return true

		val timer = try {
			authority.factOnlyTimer.bindReleaseOnlyCloseBudget(cleanup.key) { expiredKey ->
				if (
					expiredKey == cleanup.key &&
					finalizationCleanupExpiryQueued.compareAndSet(false, true)
				) {
					enqueueFinalizationCleanup {
						finalizationCleanupDeadlineElapsed(expiredKey)
					}
				}
			}
		} catch (_: Throwable) {
			finalizationCleanupBindingFailed(
				cleanup.key,
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
			)
			return true
		}
		if (timer == null) {
			finalizationCleanupBindingFailed(
				cleanup.key,
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected
			)
			return true
		}
		finalizationCleanupTimer = timer
		if (finalizationCleanupExpiryQueued.get()) return true
		if (
			installedSnapshot?.journal?.releaseOnlyCleanup?.takeIf { it.key == cleanup.key }
				?.deadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.Armed
		) {
			finalizationCleanupTimer = null
			try {
				authority.factOnlyTimer.cancelReleaseOnlyCloseBudget(timer)
			} catch (_: Throwable) {
			}
			return true
		}
		releaseCommands.forEach { command ->
			val imported = current.releaseLedger.importedFor(command.registration)
				?: return@forEach
			finalizationPhysicalRelease.dispatchLegacy(
				ReaderImportedLegacyReleaseDispatch(command, imported),
				invoke = { onConfirmed ->
					authority.resources.releaseLegacy(command, imported, onConfirmed)
				},
				onFact = { fact ->
					enqueueFinalizationCleanup {
						accountFinalizationCleanupReleaseFact(
							cleanup.key,
							imported.physicalIdentity,
							fact
						)
					}
				}
			)
		}
		return true
	}

	private fun enqueueFinalizationCleanup(action: () -> Unit) {
		try {
			postFinalizationCleanupToMain {
				try {
					action()
				} catch (_: Throwable) {
				}
			}
		} catch (_: Throwable) {
		}
	}

	private fun finalizationCleanupBindingFailed(
		cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
		status: ReaderReleaseOnlyCleanupDeadlineStatus
	) {
		val snapshot = installedSnapshot ?: return
		val cleanup = snapshot.journal.releaseOnlyCleanup?.takeIf { it.key == cleanupKey }
			?: return
		snapshot.releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
		installedSnapshot = snapshot.copy(
			journal = snapshot.journal.copy(
				releaseOnlyCleanup = cleanup.copy(deadlineStatus = status),
				lastOutcome = ReaderTransitionOutcome.Failed(
					ReaderTransitionFailureReason.CloseDrainTimeout,
					ReaderTransitionRetryability.NonRetryable,
					ReaderPresentationFrameOwner.Neutral
				),
				retryableTransition = null
			)
		)
		atomicWriteCount += 1
	}

	private fun finalizationCleanupDeadlineElapsed(
		cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey
	) {
		val snapshot = installedSnapshot ?: return
		val cleanup = snapshot.journal.releaseOnlyCleanup?.takeIf { it.key == cleanupKey }
			?: return
		if (
			cleanup.deadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.Armed &&
			cleanup.deadlineStatus !=
				ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected &&
			cleanup.deadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
		) return
		finalizationCleanupTimer = null
		snapshot.releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
		installedSnapshot = snapshot.copy(
			journal = snapshot.journal.markReleaseOnlyCleanupDeadlineElapsed(cleanupKey)
		)
		atomicWriteCount += 1
	}

	private fun accountFinalizationCleanupReleaseFact(
		cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		fact: ReaderTransitionFact
	) {
		val snapshot = installedSnapshot ?: return
		if (snapshot.journal.releaseOnlyCleanup?.key != cleanupKey) return
		val accounted = when (fact) {
			is ReaderTransitionFact.ResourceReleased ->
				snapshot.releaseLedger.confirmLegacyReleased(physicalIdentity, fact)
			is ReaderTransitionFact.ReleaseCommandRejected ->
				snapshot.releaseLedger.recordRejectedNoEffect(fact)
			is ReaderTransitionFact.ReleaseCommandThrew ->
				snapshot.releaseLedger.recordAmbiguousFailure(fact)
			is ReaderTransitionFact.ReleasePortContractViolated ->
				snapshot.releaseLedger.recordPortContractViolation(fact)
			else -> false
		}
		if (
			!accounted ||
			snapshot.releaseLedger.cleanupStatus(cleanupKey) !=
				ReaderReleaseLedgerCleanupStatus.EmptyReleased
		) return
		completeFinalizationCleanup(cleanupKey)
	}

	private fun completeFinalizationCleanup(
		cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey
	) {
		val snapshot = installedSnapshot ?: return
		val cleanup = snapshot.journal.releaseOnlyCleanup?.takeIf { it.key == cleanupKey }
			?: return
		if (cleanup.deadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.Armed) return
		val timer = finalizationCleanupTimer ?: return
		val authority = requireNotNull(snapshot.releaseOnlyPortAuthority)
		val disposition = try {
			when (authority.factOnlyTimer.cancelReleaseOnlyCloseBudget(timer)) {
				ReaderPortCommandResult.Accepted -> null
				is ReaderPortCommandResult.Rejected ->
					ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
			}
		} catch (_: Throwable) {
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
		}
		val latest = installedSnapshot ?: return
		val latestCleanup = latest.journal.releaseOnlyCleanup?.takeIf { it.key == cleanupKey }
			?: return
		if (latestCleanup.deadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.Armed) return
		if (disposition == null) {
			finalizationCleanupTimer = null
			installedSnapshot = latest.copy(
				journal = latest.journal.copy(
					releaseOnlyCleanup = latestCleanup.copy(
						deadlineStatus =
							ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting
					)
				)
			)
		} else {
			installedSnapshot = latest.copy(
				journal = latest.journal.copy(
					releaseOnlyCleanup = latestCleanup.copy(deadlineStatus = disposition),
					lastOutcome = ReaderTransitionOutcome.Failed(
						ReaderTransitionFailureReason.CloseDrainTimeout,
						ReaderTransitionRetryability.NonRetryable,
						ReaderPresentationFrameOwner.Neutral
					),
					retryableTransition = null
				)
			)
		}
		atomicWriteCount += 1
	}
}

internal class ReaderActivatedSessionInstallationBarrier(
	private val store: ReaderActivatedSessionSnapshotStore
) {
	fun installActivatedSession(installation: ReaderActivatedSessionInstallation): ReaderActivationInstallResult {
		val journal = initialJournal(installation.initialDecision)
		val snapshot = ReaderActivatedSessionSnapshot(
			portAuthority = ReaderActivatedSessionPortAuthority.Production(installation.ports),
			initialDecision = installation.initialDecision,
			reservedNeutralBootstrap = installation.initialDecision.neutralBootstrapReservation,
			journal = journal,
			releaseLedger = installation.releaseLedger
		)
		return installResult(store.install(snapshot))
	}

	fun installTestActivatedSession(
		ports: ReaderTestActivatedSessionPorts,
		initialDecision: ReaderInitialActivationDecision,
		releaseLedger: ReaderTransitionReleaseLedger = ReaderTransitionReleaseLedger()
	): ReaderActivationInstallResult {
		if (!ports.complete) {
			return ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.ActivationPrerequisiteMissing
			)
		}
		val journal = initialJournal(initialDecision)
		val snapshot = ReaderActivatedSessionSnapshot(
			portAuthority = ReaderActivatedSessionPortAuthority.Test(ports),
			initialDecision = initialDecision,
			reservedNeutralBootstrap = initialDecision.neutralBootstrapReservation,
			journal = journal,
			releaseLedger = releaseLedger
		)
		return installResult(store.install(snapshot))
	}

	fun failClosedAfterFinalizationRejection(
		expectedDecision: ReaderInitialActivationDecision
	): Boolean = store.failClosedAfterFinalizationRejection(expectedDecision)

	private fun initialJournal(decision: ReaderInitialActivationDecision) = ReaderTransitionJournal(
		committed = ReaderCommittedPresentation.Initial(decision.origin),
		lastTransitionSequence = 0L,
		lastIssuedTransitionIdentity = null
	)

	private fun installResult(installed: Boolean): ReaderActivationInstallResult =
		if (installed) {
			ReaderActivationInstallResult.Installed
		} else {
			ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.AtomicPublicationRejected
			)
		}
}

internal fun interface ReaderActivationRestorationDeadlineRegistration {
	fun cancel()
}

internal interface ReaderActivationRestorationDeadlinePort {
	fun schedule(
		delayMillis: Long,
		onExpired: () -> Unit
	): ReaderActivationRestorationDeadlineRegistration?
}

private object ReaderInertActivationRestorationDeadlinePort :
	ReaderActivationRestorationDeadlinePort {
	override fun schedule(
		delayMillis: Long,
		onExpired: () -> Unit
	): ReaderActivationRestorationDeadlineRegistration =
		ReaderActivationRestorationDeadlineRegistration {}
}

internal class ReaderSessionActivationCoordinator(
	private val readerSessionGeneration: Long,
	private val coordinatorEpoch: Long,
	private val legacy: ReaderLegacyFreezeAndInventoryPort,
	private val installationBarrier: ReaderActivatedSessionInstallationBarrier,
	private val narrowInitialLease: (
		ReaderInitialPresentationInputLease
	) -> ReaderInitialPresentationInputLease,
	private val reserveNeutralBootstrap: () -> ReaderReservedNeutralBootstrapRequest? = { null },
	private val discardNeutralBootstrap: (ReaderReservedNeutralBootstrapRequest) -> Unit = {},
	private val fenceAdoptedCurlGesture: (ReaderPresentationFrameOwner.Curl) -> Boolean = { true },
	private val restorationDeadline: ReaderActivationRestorationDeadlinePort =
		ReaderInertActivationRestorationDeadlinePort,
	private val cancelPhysicalWork: () -> Unit = {},
	private val releaseLedger: ReaderTransitionReleaseLedger = ReaderTransitionReleaseLedger()
) {
	private var nextSeedId = 1L
	private var freezeToken: ReaderLegacyFreezeToken? = null
	private var checkpoint: ReaderLegacyRestorationCheckpoint? = null
	private var selectedPredecessor: ReaderFrozenLegacyResource? = null
	private var neutralBootstrapReservation: ReaderReservedNeutralBootstrapRequest? = null
	private var installActivatedSession: ((
		ReaderInitialActivationDecision,
		ReaderTransitionReleaseLedger
	) -> ReaderActivationInstallResult)? = null
	private var requestedLease: ReaderInitialPresentationInputLease? = null
	private var validatedPhysicalLease: ReaderInitialPresentationInputLease? = null
	private var nextActivationAttempt = 1L
	private var activeActivationAttempt = 0L
	private val confirmedDrains = linkedSetOf<ReaderLegacyPhysicalIdentity>()
	private val pendingDrains = linkedSetOf<ReaderLegacyPhysicalIdentity>()
	private var pendingDrainFailure: ReaderTransitionFailureReason? = null
	private var issuingDrains = false
	private var destructiveDrainStarted = false
	private var terminalResult: ReaderActivationInstallResult? = null
	private var restorationDeadlineRegistration: ReaderActivationRestorationDeadlineRegistration? = null
	private val pendingRestorationSources = linkedSetOf<ReaderLegacyInventorySource>()
	private var issuingRestorationRequests = false

	var state: ReaderSessionActivationState = ReaderSessionActivationState.Legacy
		private set

	init {
		require(readerSessionGeneration > 0L)
		require(coordinatorEpoch > 0L)
	}

	fun activate(
		ports: ReaderProductionActivatedSessionPorts,
		requestedLease: ReaderInitialPresentationInputLease
	): ReaderActivationInstallResult = activateInternal(requestedLease) { decision, ledger ->
		installationBarrier.installActivatedSession(
			ReaderActivatedSessionInstallation(ports, decision, ledger)
		)
	}

	fun activateForTest(
		ports: ReaderTestActivatedSessionPorts,
		requestedLease: ReaderInitialPresentationInputLease
	): ReaderActivationInstallResult {
		if (!ports.complete) {
			return ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.ActivationPrerequisiteMissing
			)
		}
		return activateInternal(requestedLease) { decision, ledger ->
			installationBarrier.installTestActivatedSession(ports, decision, ledger)
		}
	}

	private fun activateInternal(
		requestedLease: ReaderInitialPresentationInputLease,
		install: (
			ReaderInitialActivationDecision,
			ReaderTransitionReleaseLedger
		) -> ReaderActivationInstallResult
	): ReaderActivationInstallResult {
		if (state != ReaderSessionActivationState.Legacy) {
			return ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.ActivationPrerequisiteMissing
			)
		}
		terminalResult = null
		activeActivationAttempt = nextActivationAttempt++
		freezeToken = null
		checkpoint = null
		selectedPredecessor = null
		neutralBootstrapReservation = null
		confirmedDrains.clear()
		pendingDrains.clear()
		pendingDrainFailure = null
		pendingRestorationSources.clear()
		restorationDeadlineRegistration = null
		issuingRestorationRequests = false
		destructiveDrainStarted = false
		installActivatedSession = install
		this.requestedLease = requestedLease
		validatedPhysicalLease = null
		state = ReaderSessionActivationState.Freezing
		val token = legacy.freeze()
		freezeToken = token
		val initialInventory = readFixedPoint(token)
		if (initialInventory == null) {
			return failBeforeDrain(ReaderTransitionFailureReason.InventoryIncomplete)
		}
		val capturedCheckpoint = legacy.checkpointBeforeDrain(token)
		if (capturedCheckpoint == null || capturedCheckpoint.freezeToken != token) {
			return failBeforeDrain(ReaderTransitionFailureReason.ActivationPrerequisiteMissing)
		}
		checkpoint = capturedCheckpoint
		if (!validInventory(initialInventory, token)) {
			return failBeforeDrain(ReaderTransitionFailureReason.InvalidLegacyResource)
		}
		when (val selection = ReaderAdoptedPredecessorSelector.select(initialInventory.resources)) {
			ReaderAdoptedPredecessorSelection.Ambiguous ->
				return failBeforeDrain(ReaderTransitionFailureReason.AmbiguousPredecessor)
			ReaderAdoptedPredecessorSelection.Neutral -> {
				selectedPredecessor = null
				val reservation = reserveNeutralBootstrap()
				if (reservation == null || reservation.readerSessionGeneration != readerSessionGeneration) {
					reservation?.let(discardNeutralBootstrap)
					return failBeforeDrain(ReaderTransitionFailureReason.ActivationPrerequisiteMissing)
				}
				neutralBootstrapReservation = reservation
			}
			is ReaderAdoptedPredecessorSelection.Selected -> {
				selectedPredecessor = selection.resource
				val curl = selection.resource.visibleOwner as? ReaderPresentationFrameOwner.Curl
				if (curl != null && !fenceAdoptedCurlGesture(curl)) {
					return failBeforeDrain(ReaderTransitionFailureReason.ActivationPrerequisiteMissing)
				}
			}
		}
		val physicalLease = narrowInitialLease(requestedLease)
		if (
			!readerInitialInputLeaseIsNoBroader(physicalLease, requestedLease) ||
			!checkpointMatchesSelection(
				capturedCheckpoint,
				selectedPredecessor,
				requestedLease,
				physicalLease
			)
		) {
			return failBeforeDrain(ReaderTransitionFailureReason.ActivationPrerequisiteMissing)
		}
		validatedPhysicalLease = physicalLease
		state = ReaderSessionActivationState.DrainingLegacy
		return drainInventory(initialInventory)
	}

	fun closeToReleaseOnly() {
		if (state == ReaderSessionActivationState.ReleaseOnly) return
		if (state != ReaderSessionActivationState.Activated) discardReservedNeutralBootstrap()
		state = ReaderSessionActivationState.ReleaseOnly
		restorationDeadlineRegistration?.cancel()
		restorationDeadlineRegistration = null
		pendingRestorationSources.clear()
		cancelPhysicalWork()
	}

	private fun readFixedPoint(
		token: ReaderLegacyFreezeToken
	): ReaderLegacyResourceInventory.Complete? {
		val tracker = ReaderLegacyInventoryFixedPointTracker(token)
		repeat(64) {
			val snapshot = legacy.inventory(token)
			if (tracker.accept(snapshot)) return snapshot as ReaderLegacyResourceInventory.Complete
		}
		return null
	}

	private fun validInventory(
		inventory: ReaderLegacyResourceInventory.Complete,
		token: ReaderLegacyFreezeToken
	): Boolean = inventory.resources.all { row ->
		row.freezeToken == token &&
			row.physicalIdentity.domain.freezeToken == token &&
			row.physicalIdentity.domain.readerSessionGeneration == readerSessionGeneration &&
			row.provenance == ReaderTransitionResourceProvenance.AdoptedLegacy
	}

	private fun checkpointMatchesSelection(
		checkpoint: ReaderLegacyRestorationCheckpoint,
		selected: ReaderFrozenLegacyResource?,
		requestedLease: ReaderInitialPresentationInputLease,
		physicalLease: ReaderInitialPresentationInputLease
	): Boolean {
		if (
			checkpoint.requestedLease != requestedLease ||
			checkpoint.physicalLease != physicalLease ||
			!readerInitialInputLeaseIsCompatibleWithOwner(
				checkpoint.initialOwner,
				checkpoint.initialBinding,
				checkpoint.requestedLease
			) ||
			!readerInitialInputLeaseIsCompatibleWithOwner(
				checkpoint.initialOwner,
				checkpoint.initialBinding,
				checkpoint.physicalLease
			)
		) return false
		return if (selected == null) {
			checkpoint.initialOwner == ReaderPresentationFrameOwner.Neutral
		} else {
			checkpoint.initialOwner == selected.visibleOwner &&
				checkpoint.initialBinding == selected.binding &&
				checkpoint.initialPhysicalIdentity == selected.physicalIdentity &&
				checkpoint.initialResourceKind == selected.kind &&
				checkpoint.initialProvenance == selected.provenance
		}
	}

	private fun drainInventory(
		inventory: ReaderLegacyResourceInventory.Complete
	): ReaderActivationInstallResult {
		val selectedIdentity = selectedPredecessor?.physicalIdentity
		val required = inventory.resources
			.map(ReaderFrozenLegacyResource::physicalIdentity)
			.filterTo(linkedSetOf()) { it != selectedIdentity }
		val outstanding = required - confirmedDrains - pendingDrains
		val attempt = activeActivationAttempt
		issuingDrains = true
		for (identity in outstanding) {
			if (
				activeActivationAttempt != attempt ||
				state != ReaderSessionActivationState.DrainingLegacy ||
				pendingDrainFailure != null
			) break
			destructiveDrainStarted = true
			var commandReturned = false
			var synchronousConfirmation: ReaderLegacyPhysicalIdentity? = null
			var malformedSynchronousConfirmation = false
			val result = legacy.drain(requireNotNull(freezeToken), identity) { confirmed ->
				if (activeActivationAttempt != attempt) return@drain
				if (!commandReturned) {
					if (synchronousConfirmation != null || confirmed != identity) {
						malformedSynchronousConfirmation = true
					} else {
						synchronousConfirmation = confirmed
					}
					return@drain
				}
				onAcceptedDrainConfirmed(attempt, identity, confirmed)
			}
			commandReturned = true
			if (activeActivationAttempt != attempt || state != ReaderSessionActivationState.DrainingLegacy) {
				break
			}
			if (result is ReaderPortCommandResult.Rejected) {
				issuingDrains = false
				return failDrainIssuance(ReaderTransitionFailureReason.LegacyDrainFailed)
			}
			if (malformedSynchronousConfirmation) {
				issuingDrains = false
				return blockUnquiescedDrain(ReaderTransitionFailureReason.LegacyDrainFailed)
			}
			if (synchronousConfirmation == identity) {
				confirmedDrains += identity
			} else {
				pendingDrains += identity
			}
		}
		issuingDrains = false
		terminalResult?.let { return it }
		if (pendingDrains.isNotEmpty()) return ReaderActivationInstallResult.Pending
		return continueAfterDrain()
	}

	private fun onAcceptedDrainConfirmed(
		attempt: Long,
		expected: ReaderLegacyPhysicalIdentity,
		confirmed: ReaderLegacyPhysicalIdentity
	) {
		if (attempt != activeActivationAttempt || expected !in pendingDrains) return
		if (confirmed != expected) {
			blockUnquiescedDrain(ReaderTransitionFailureReason.LegacyDrainFailed)
			return
		}
		pendingDrains.remove(expected)
		confirmedDrains += confirmed
		if (issuingDrains || pendingDrains.isNotEmpty()) return
		if (pendingDrainFailure != null) {
			beginCheckpointRestoration()
		} else if (state == ReaderSessionActivationState.DrainingLegacy) {
			continueAfterDrain()
		}
	}

	private fun failDrainIssuance(
		reason: ReaderTransitionFailureReason
	): ReaderActivationInstallResult {
		pendingDrainFailure = reason
		discardReservedNeutralBootstrap()
		val result = ReaderActivationInstallResult.Rejected(reason)
		terminalResult = result
		if (pendingDrains.isEmpty()) {
			beginCheckpointRestoration()
		} else {
			state = ReaderSessionActivationState.ActivationBlocked
		}
		return result
	}

	private fun blockUnquiescedDrain(
		reason: ReaderTransitionFailureReason
	): ReaderActivationInstallResult {
		pendingDrainFailure = reason
		discardReservedNeutralBootstrap()
		state = ReaderSessionActivationState.ActivationBlocked
		return ReaderActivationInstallResult.Rejected(reason).also { terminalResult = it }
	}

	private fun continueAfterDrain(): ReaderActivationInstallResult {
		terminalResult?.let { return it }
		if (state != ReaderSessionActivationState.DrainingLegacy) {
			return terminalResult ?: ReaderActivationInstallResult.Pending
		}
		val token = requireNotNull(freezeToken)
		val fixed = readFixedPoint(token)
		if (fixed == null || !validInventory(fixed, token)) {
			return failAfterDrain(ReaderTransitionFailureReason.InventoryIncomplete)
		}
		val selected = selectedPredecessor
		if (
			selected != null &&
			fixed.resources.singleOrNull {
				it.physicalIdentity == selected.physicalIdentity
			} != selected
		) {
			return failAfterDrain(ReaderTransitionFailureReason.InvalidLegacyResource)
		}
		val selectedIdentity = selected?.physicalIdentity
		val outstanding = fixed.resources.any {
			it.physicalIdentity != selectedIdentity && it.physicalIdentity !in confirmedDrains
		}
		if (outstanding) return drainInventory(fixed)
		state = ReaderSessionActivationState.ReadyToCommit
		return commitActivated()
	}

	private fun commitActivated(): ReaderActivationInstallResult {
		val selected = selectedPredecessor
		val requested = requireNotNull(requestedLease)
		val physical = requireNotNull(validatedPhysicalLease)
		val decision = try {
			if (selected == null) {
				ReaderInitialActivationDecision(
					origin = ReaderInitialCommittedPresentationOrigin.Neutral(
						readerSessionGeneration = readerSessionGeneration,
						coordinatorEpoch = coordinatorEpoch,
						requestedLease = requested,
						physicalLease = physical
					),
					adoptedSeed = null,
					adoptedResource = null,
					neutralBootstrapReservation = requireNotNull(neutralBootstrapReservation)
				)
			} else {
				val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(nextSeedId++)
				val owner = requireNotNull(selected.visibleOwner)
				val binding = requireNotNull(selected.binding)
				val seed = ReaderAdoptedPredecessorSeed(
					id = seedId,
					physicalIdentity = selected.physicalIdentity,
					resourceKind = selected.kind,
					binding = binding,
					owner = owner,
					readerSessionGeneration = readerSessionGeneration,
					coordinatorEpoch = coordinatorEpoch
				)
				val imported = releaseLedger.importLegacy(
					physicalIdentity = selected.physicalIdentity,
					ownerId = ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
					kind = selected.kind,
					coordinatorEpoch = coordinatorEpoch
				) ?: return failAfterDrain(ReaderTransitionFailureReason.AtomicPublicationRejected)
				val registration = imported.registration
				ReaderInitialActivationDecision(
					origin = ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
						seedId = seedId,
						readerSessionGeneration = readerSessionGeneration,
						coordinatorEpoch = coordinatorEpoch,
						owner = owner,
						binding = binding,
						resource = registration,
						requestedLease = requested,
						physicalLease = physical
					),
					adoptedSeed = seed,
					adoptedResource = imported,
					neutralBootstrapReservation = null
				)
			}
		} catch (_: IllegalArgumentException) {
			return failAfterDrain(ReaderTransitionFailureReason.AtomicPublicationRejected)
		}
		val result = requireNotNull(installActivatedSession)(decision, releaseLedger)
		terminalResult = result
		if (result == ReaderActivationInstallResult.Installed) {
			val finalization = try {
				legacy.finalizeActivatedHandoff(
					token = requireNotNull(freezeToken),
					selected = decision.adoptedResource,
					confirmedRetirements = confirmedDrains.toSet()
				)
			} catch (_: Throwable) {
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
			}
			if (finalization is ReaderPortCommandResult.Rejected) {
				check(
					installationBarrier.failClosedAfterFinalizationRejection(decision)
				) { "Installed activation changed before fail-closed finalization" }
				state = ReaderSessionActivationState.ReleaseOnly
				try {
					discardReservedNeutralBootstrap()
				} catch (_: Throwable) {
					neutralBootstrapReservation = null
				}
				try {
					cancelPhysicalWork()
				} catch (_: Throwable) {
				}
				return ReaderActivationInstallResult.Rejected(finalization.reason).also {
					terminalResult = it
				}
			}
			neutralBootstrapReservation = null
			state = ReaderSessionActivationState.Activated
		} else {
			val rejected = result as ReaderActivationInstallResult.Rejected
			val imported = decision.adoptedResource
			if (imported != null && !releaseLedger.rollbackUnpublishedImport(imported)) {
				state = ReaderSessionActivationState.ReleaseOnly
				cancelPhysicalWork()
				return rejected.also { terminalResult = it }
			}
			failAfterDrain(rejected.reason)
		}
		return terminalResult ?: result
	}

	private fun discardReservedNeutralBootstrap() {
		neutralBootstrapReservation?.let(discardNeutralBootstrap)
		neutralBootstrapReservation = null
	}

	private fun failBeforeDrain(reason: ReaderTransitionFailureReason): ReaderActivationInstallResult {
		discardReservedNeutralBootstrap()
		val token = requireNotNull(freezeToken)
		val unfreeze = legacy.cancelFreezeBeforeDrain(token)
		state = if (unfreeze == ReaderPortCommandResult.Accepted) {
			ReaderSessionActivationState.Legacy
		} else ReaderSessionActivationState.ActivationBlocked
		return ReaderActivationInstallResult.Rejected(reason).also { terminalResult = it }
	}

	private fun failAfterDrain(reason: ReaderTransitionFailureReason): ReaderActivationInstallResult {
		if (!destructiveDrainStarted) return failBeforeDrain(reason)
		discardReservedNeutralBootstrap()
		if (state == ReaderSessionActivationState.RestoringLegacy ||
			state == ReaderSessionActivationState.ActivationBlocked
		) return ReaderActivationInstallResult.Rejected(reason)
		if (state == ReaderSessionActivationState.ReleaseOnly) {
			return ReaderActivationInstallResult.Rejected(reason)
		}
		val result = ReaderActivationInstallResult.Rejected(reason)
		terminalResult = result
		beginCheckpointRestoration()
		return result
	}

	private fun beginCheckpointRestoration() {
		val restorationCheckpoint = checkpoint ?: run {
			state = ReaderSessionActivationState.ActivationBlocked
			return
		}
		val restorationFreezeToken = freezeToken ?: run {
			state = ReaderSessionActivationState.ActivationBlocked
			return
		}
		val restorationAttempt = activeActivationAttempt
		if (restorationCheckpoint.freezeToken != restorationFreezeToken) {
			state = ReaderSessionActivationState.ActivationBlocked
			return
		}
		state = ReaderSessionActivationState.RestoringLegacy
		pendingRestorationSources.clear()
		pendingRestorationSources += ReaderLegacyInventorySource.entries
		val deadlineRegistration = restorationDeadline.schedule(3_000L) {
			if (
				isCurrentRestoration(
					restorationAttempt,
					restorationCheckpoint,
					restorationFreezeToken
				) && state == ReaderSessionActivationState.RestoringLegacy
			) {
				state = ReaderSessionActivationState.ActivationBlocked
				pendingRestorationSources.clear()
				restorationDeadlineRegistration = null
			}
		}
		if (
			deadlineRegistration == null ||
			!isCurrentRestoration(
				restorationAttempt,
				restorationCheckpoint,
				restorationFreezeToken
			) ||
			state != ReaderSessionActivationState.RestoringLegacy
		) {
			deadlineRegistration?.cancel()
			if (isCurrentRestoration(restorationAttempt, restorationCheckpoint, restorationFreezeToken)) {
				state = ReaderSessionActivationState.ActivationBlocked
				pendingRestorationSources.clear()
			}
			return
		}
		restorationDeadlineRegistration = deadlineRegistration
		issuingRestorationRequests = true
		for (source in ReaderLegacyInventorySource.entries) {
			if (
				state != ReaderSessionActivationState.RestoringLegacy ||
				!isCurrentRestoration(
					restorationAttempt,
					restorationCheckpoint,
					restorationFreezeToken
				)
			) break
			val result = legacy.restoreFromActivationCheckpoint(
				restorationCheckpoint,
				source
			) { confirmed, outcome ->
				onRestorationConfirmed(
					restorationAttempt,
					restorationCheckpoint,
					restorationFreezeToken,
					source,
					confirmed,
					outcome
				)
			}
			if (result is ReaderPortCommandResult.Rejected) {
				blockRestoration(restorationAttempt, restorationCheckpoint, restorationFreezeToken)
				break
			}
		}
		issuingRestorationRequests = false
		completeRestorationIfReady(
			restorationAttempt,
			restorationCheckpoint,
			restorationFreezeToken
		)
	}

	private fun onRestorationConfirmed(
		attempt: Long,
		checkpoint: ReaderLegacyRestorationCheckpoint,
		freezeToken: ReaderLegacyFreezeToken,
		expected: ReaderLegacyInventorySource,
		confirmed: ReaderLegacyInventorySource,
		outcome: ReaderLegacyRestorationResult
	) {
		if (
			state != ReaderSessionActivationState.RestoringLegacy ||
			!isCurrentRestoration(attempt, checkpoint, freezeToken)
		) return
		if (
			confirmed != expected ||
			confirmed !in pendingRestorationSources ||
			outcome is ReaderLegacyRestorationResult.Failed
		) {
			blockRestoration(attempt, checkpoint, freezeToken)
			return
		}
		pendingRestorationSources.remove(confirmed)
		if (!issuingRestorationRequests) {
			completeRestorationIfReady(attempt, checkpoint, freezeToken)
		}
	}

	private fun completeRestorationIfReady(
		attempt: Long,
		checkpoint: ReaderLegacyRestorationCheckpoint,
		freezeToken: ReaderLegacyFreezeToken
	) {
		if (
			state != ReaderSessionActivationState.RestoringLegacy ||
			!isCurrentRestoration(attempt, checkpoint, freezeToken) ||
			issuingRestorationRequests ||
			pendingRestorationSources.isNotEmpty()
		) return
		val result = legacy.commitRestoredLegacy(checkpoint)
		if (
			state != ReaderSessionActivationState.RestoringLegacy ||
			!isCurrentRestoration(attempt, checkpoint, freezeToken)
		) return
		state = if (result == ReaderLegacyCommitRestoredResult.Applied) {
			ReaderSessionActivationState.Legacy
		} else {
			ReaderSessionActivationState.ActivationBlocked
		}
		restorationDeadlineRegistration?.cancel()
		restorationDeadlineRegistration = null
	}

	private fun blockRestoration(
		attempt: Long,
		checkpoint: ReaderLegacyRestorationCheckpoint,
		freezeToken: ReaderLegacyFreezeToken
	) {
		if (
			state != ReaderSessionActivationState.RestoringLegacy ||
			!isCurrentRestoration(attempt, checkpoint, freezeToken)
		) return
		state = ReaderSessionActivationState.ActivationBlocked
		pendingRestorationSources.clear()
		restorationDeadlineRegistration?.cancel()
		restorationDeadlineRegistration = null
	}

	private fun isCurrentRestoration(
		attempt: Long,
		checkpoint: ReaderLegacyRestorationCheckpoint,
		freezeToken: ReaderLegacyFreezeToken
	): Boolean = activeActivationAttempt == attempt &&
		this.checkpoint === checkpoint &&
		this.freezeToken == freezeToken &&
		checkpoint.freezeToken == freezeToken
}

@JvmInline
internal value class ReaderTask6FactOnlyTimerRegistrationId(val value: Long) {
	init { require(value > 0L) }
}

internal data class ReaderTask6FactOnlyTimerRegistration(
	val id: ReaderTask6FactOnlyTimerRegistrationId,
	val transitionId: paige.navic.reader.ReaderTransitionId,
	val physicalIdentity: ReaderLegacyPhysicalIdentity,
	val hardExpiresAtMillis: Long,
	val noProgressIntervalMillis: Long?,
	val permitsMatchingProgressRearm: Boolean
) {
	init {
		require(physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration)
		require(hardExpiresAtMillis >= 0L)
		require(noProgressIntervalMillis == null || noProgressIntervalMillis > 0L)
		require(permitsMatchingProgressRearm == (noProgressIntervalMillis != null))
	}
}

internal data class ReaderTask6FactOnlyTimerTransferSnapshot(
	val registration: ReaderTask6FactOnlyTimerRegistration,
	val nextExpiresAtMillis: Long
) {
	init {
		require(nextExpiresAtMillis >= 0L)
		require(nextExpiresAtMillis <= registration.hardExpiresAtMillis)
	}
}

internal data class ReaderTask6ReleaseOnlyTimerRegistration(
	val id: ReaderTask6FactOnlyTimerRegistrationId,
	val cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
	val physicalIdentity: ReaderLegacyPhysicalIdentity,
	val expiresAtMillis: Long
) {
	init {
		require(physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration)
		require(expiresAtMillis >= 0L)
	}

	override fun hashCode(): Int = 0x5252364F
	override fun toString(): String =
		"ReaderTask6ReleaseOnlyTimerRegistration(<redacted>)"
}

internal interface ReaderTask6FactOnlyTimerPort {
	fun bindBeforeWork(
		transitionId: paige.navic.reader.ReaderTransitionId,
		onExpired: (paige.navic.reader.ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration?

	fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult

	fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot?

	fun cancel(registration: ReaderTask6FactOnlyTimerRegistration): ReaderPortCommandResult

	fun bindReleaseOnlyCloseBudget(
		cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
		onExpired: (paige.navic.reader.ReaderReleaseOnlyCleanupKey) -> Unit
	): ReaderTask6ReleaseOnlyTimerRegistration? = null

	fun cancelReleaseOnlyCloseBudget(
		registration: ReaderTask6ReleaseOnlyTimerRegistration
	): ReaderPortCommandResult = ReaderPortCommandResult.Rejected(
		ReaderTransitionFailureReason.PortRejected
	)
}

internal class ReaderRetainedFactOnlyTimer(
	private val domain: ReaderLegacyPhysicalDomain,
	private val nowMillis: () -> Long = android.os.SystemClock::uptimeMillis,
	private val schedule: (Long, () -> Unit) -> ReaderTransitionClockRegistration? =
		{ atMillis, action -> AndroidReaderTransitionClock().schedule(atMillis, action) },
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) : ReaderTask6FactOnlyTimerPort {
	private data class Entry(
		val registration: ReaderTask6FactOnlyTimerRegistration,
		val onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit,
		var nextExpiresAtMillis: Long,
		var clockRegistration: ReaderTransitionClockRegistration? = null
	)

	private data class ReleaseOnlyEntry(
		val registration: ReaderTask6ReleaseOnlyTimerRegistration,
		val onExpired: (paige.navic.reader.ReaderReleaseOnlyCleanupKey) -> Unit,
		var clockRegistration: ReaderTransitionClockRegistration? = null
	)

	private val entries = linkedMapOf<ReaderTask6FactOnlyTimerRegistrationId, Entry>()
	private val releaseOnlyEntries = linkedMapOf<ReaderTask6FactOnlyTimerRegistrationId, ReleaseOnlyEntry>()
	private val restartEntries = linkedMapOf<ReaderTask6FactOnlyTimerRegistrationId, Entry>()
	private val completedFrozen = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private var nextRegistrationId = 1L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	override fun bindBeforeWork(
		transitionId: paige.navic.reader.ReaderTransitionId,
		onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration? {
		if (
			frozenDomain != null ||
			transitionId.readerSessionGeneration != domain.readerSessionGeneration ||
			nextRegistrationId == Long.MAX_VALUE
		) return null
		val policy = transitionId.operation.deadlinePolicy()
		val now = nowMillis()
		val hardExpiry = now.saturatingAddForFactOnlyTimer(policy.hardMillis)
		val nextExpiry = policy.noProgressMillis?.let { interval ->
			minOf(hardExpiry, now.saturatingAddForFactOnlyTimer(interval))
		} ?: hardExpiry
		val id = ReaderTask6FactOnlyTimerRegistrationId(nextRegistrationId++)
		val registration = ReaderTask6FactOnlyTimerRegistration(
			id = id,
			transitionId = transitionId,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain = domain,
				source = ReaderLegacyInventorySource.DeadlineRegistration,
				sourceLocalToken = tokenAllocator.allocate()
			),
			hardExpiresAtMillis = hardExpiry,
			noProgressIntervalMillis = policy.noProgressMillis,
			permitsMatchingProgressRearm = policy.noProgressMillis != null
		)
		val entry = Entry(registration, onExpired, nextExpiry)
		entries[id] = entry
		if (!scheduleEntry(entry)) {
			entries.remove(id)
			return null
		}
		return registration
	}

	override fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult {
		val entry = entries[registration.id]
		val interval = registration.noProgressIntervalMillis
		if (
			frozenDomain != null ||
			entry?.registration != registration ||
			interval == null
		) return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		entry.clockRegistration?.cancel()
		entry.clockRegistration = null
		entry.nextExpiresAtMillis = minOf(
			registration.hardExpiresAtMillis,
			nowMillis.saturatingAddForFactOnlyTimer(interval)
		)
		return if (scheduleEntry(entry)) ReaderPortCommandResult.Accepted else {
			if (entries.remove(registration.id) === entry) {
				entry.onExpired(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
			}
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
	}

	override fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot? = entries[registration.id]
		?.takeIf { it.registration == registration }
		?.let { ReaderTask6FactOnlyTimerTransferSnapshot(registration, it.nextExpiresAtMillis) }

	override fun cancel(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderPortCommandResult {
		val entry = entries[registration.id]
		if (entry?.registration != registration) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		entry.clockRegistration?.cancel()
		if (entries.remove(registration.id) === entry && frozenDomain != null) {
			completedFrozen += registration.physicalIdentity.sourceLocalToken
		}
		entry.clockRegistration = null
		return ReaderPortCommandResult.Accepted
	}

	override fun bindReleaseOnlyCloseBudget(
		cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
		onExpired: (paige.navic.reader.ReaderReleaseOnlyCleanupKey) -> Unit
	): ReaderTask6ReleaseOnlyTimerRegistration? {
		if (
			frozenDomain != null ||
			cleanupKey.readerSessionGeneration != domain.readerSessionGeneration ||
			nextRegistrationId == Long.MAX_VALUE ||
			releaseOnlyEntries.isNotEmpty()
		) return null
		val id = ReaderTask6FactOnlyTimerRegistrationId(nextRegistrationId++)
		val registration = ReaderTask6ReleaseOnlyTimerRegistration(
			id = id,
			cleanupKey = cleanupKey,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain = domain,
				source = ReaderLegacyInventorySource.DeadlineRegistration,
				sourceLocalToken = tokenAllocator.allocate()
			),
			expiresAtMillis = nowMillis().saturatingAddForFactOnlyTimer(
				ReleaseOnlyCloseBudgetMillis
			)
		)
		val entry = ReleaseOnlyEntry(registration, onExpired)
		releaseOnlyEntries[id] = entry
		val clockRegistration = try {
			schedule(registration.expiresAtMillis) {
				val current = releaseOnlyEntries[id]
				if (current === entry) {
					releaseOnlyEntries.remove(id)
					if (frozenDomain != null) {
						completedFrozen += registration.physicalIdentity.sourceLocalToken
					}
					entry.onExpired(registration.cleanupKey)
				}
			}
		} catch (throwable: Throwable) {
			if (releaseOnlyEntries[id] === entry) releaseOnlyEntries.remove(id)
			throw throwable
		}
		if (releaseOnlyEntries[id] !== entry) {
			clockRegistration?.cancel()
			return registration
		}
		entry.clockRegistration = clockRegistration ?: run {
			releaseOnlyEntries.remove(id)
			return null
		}
		return registration
	}

	override fun cancelReleaseOnlyCloseBudget(
		registration: ReaderTask6ReleaseOnlyTimerRegistration
	): ReaderPortCommandResult {
		val entry = releaseOnlyEntries[registration.id]
		if (entry?.registration != registration) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		entry.clockRegistration?.cancel()
		if (releaseOnlyEntries.remove(registration.id) !== entry) {
			entry.clockRegistration = null
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.PortRejected
			)
		}
		if (frozenDomain != null) {
			completedFrozen += registration.physicalIdentity.sourceLocalToken
		}
		entry.clockRegistration = null
		return ReaderPortCommandResult.Accepted
	}

	fun freezeForTransitionActivation(
		requestedDomain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = when {
		requestedDomain != domain -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		frozenDomain == null -> {
			frozenDomain = requestedDomain
			ReaderPortCommandResult.Accepted
		}
		frozenDomain == requestedDomain -> ReaderPortCommandResult.Accepted
		else -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		val frozen = frozenDomain ?: return emptyList()
		return entries.values.map { entry ->
			ReaderFrozenLegacyResource(
				freezeToken = frozen.freezeToken,
				physicalIdentity = entry.registration.physicalIdentity,
				kind = ReaderTransitionResourceKind.CallbackRegistration,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = ReaderLegacyResourceState.Registered,
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val frozen = frozenDomain
		if (
			frozen == null ||
			physicalIdentity.domain != frozen ||
			physicalIdentity.source != ReaderLegacyInventorySource.DeadlineRegistration
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val token = physicalIdentity.sourceLocalToken
		val entry = entries.values.firstOrNull {
			it.registration.physicalIdentity.sourceLocalToken == token
		}
		if (entry != null) {
			entries.remove(entry.registration.id)
			entry.clockRegistration?.cancel()
			entry.clockRegistration = null
			restartEntries[entry.registration.id] = entry
		} else if (!completedFrozen.remove(token)) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		onConfirmed(physicalIdentity)
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		requestedDomain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (frozenDomain != requestedDomain) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		frozenDomain = null
		completedFrozen.clear()
		val restart = restartEntries.values.toList()
		restartEntries.clear()
		for (entry in restart) {
			entries[entry.registration.id] = entry
			if (!scheduleEntry(entry)) {
				entries.remove(entry.registration.id)
				return ReaderPortCommandResult.Rejected(
					ReaderTransitionFailureReason.PortRejected
				)
			}
		}
		return ReaderPortCommandResult.Accepted
	}

	private fun scheduleEntry(entry: Entry): Boolean {
		val id = entry.registration.id
		val clockRegistration = schedule(entry.nextExpiresAtMillis) {
			val current = entries[id]
			if (current === entry) {
				entries.remove(id)
				if (frozenDomain != null) {
					completedFrozen += entry.registration.physicalIdentity.sourceLocalToken
				}
				entry.onExpired(
					ReaderTransitionFact.DeadlineExpired(entry.registration.transitionId)
				)
			}
		}
		if (entries[id] !== entry) {
			clockRegistration?.cancel()
			return true
		}
		entry.clockRegistration = clockRegistration ?: return false
		return true
	}

	private companion object {
		const val ReleaseOnlyCloseBudgetMillis = 2_000L
	}
}

private fun Long.saturatingAddForFactOnlyTimer(increment: Long): Long =
	if (this > Long.MAX_VALUE - increment) Long.MAX_VALUE else this + increment

internal enum class ReaderReleaseOnlyIngress {
	ResourceObservation,
	ReleaseConfirmation,
	ReleaseCommand,
	SemanticConsequence,
	MaterialConsequence,
	FrameConsequence,
	Restoration
}

internal class ReaderSessionActivationStateMachine(
	initialState: ReaderSessionActivationState = ReaderSessionActivationState.Legacy,
	private val cancelPhysicalWork: () -> Unit = {}
) {
	var state: ReaderSessionActivationState = initialState
		private set

	fun closeToReleaseOnly(publish: () -> Unit) {
		if (state == ReaderSessionActivationState.ReleaseOnly) return
		publish()
		state = ReaderSessionActivationState.ReleaseOnly
		cancelPhysicalWork()
	}

	fun accepts(ingress: ReaderReleaseOnlyIngress): Boolean =
		state != ReaderSessionActivationState.ReleaseOnly || ingress in setOf(
			ReaderReleaseOnlyIngress.ResourceObservation,
			ReaderReleaseOnlyIngress.ReleaseConfirmation,
			ReaderReleaseOnlyIngress.ReleaseCommand
		)
}

internal sealed interface ReaderPortCommandResult {
	data object Accepted : ReaderPortCommandResult
	data class Rejected(val reason: ReaderTransitionFailureReason) : ReaderPortCommandResult
}

internal class ReaderMaterialGenerationAllocator(
	private val readerSessionGeneration: Long,
	private val prepare: (ReaderMaterialGenerationAllocation) -> Unit = {}
) {
	private var nextGeneration = 1L
	var lastAllocation: ReaderMaterialGenerationAllocation? = null
		private set

	init { require(readerSessionGeneration > 0L) }

	fun allocate(command: ReaderTransitionCommand.AllocateMaterialBinding): ReaderPortCommandResult {
		if (
			command.transitionId.readerSessionGeneration != readerSessionGeneration ||
			!command.transitionId.expectedBinding.acceptsMaterialAllocation(command.binding)
		) return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.MaterialAllocationRejected)
		if (nextGeneration > Long.MAX_VALUE - 2L) {
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.MaterialAllocationRejected)
		}
		val preparation = nextGeneration++
		val raster = nextGeneration++
		val texture = nextGeneration++
		val allocated = command.binding.copy(
			preparationGeneration = preparation,
			rasterGeneration = raster,
			textureGeneration = texture
		)
		lastAllocation = ReaderMaterialGenerationAllocation(
			command.transitionId,
			allocated,
			preparation,
			raster,
			texture
		)
		return ReaderPortCommandResult.Accepted
	}
}

internal data class ReaderOwnerAndInputPublicationSnapshot(
	val owner: ReaderPresentationFrameOwner,
	val binding: ReaderPresentationBinding,
	val resource: ReaderTransitionResourceRegistration,
	val lease: ReaderTransitionInputLease
)

internal class ReaderOwnerAndInputPublicationBarrier(
	private val narrowOrVeto: (ReaderTransitionInputLease) -> ReaderTransitionInputLease = { it },
	private val publishAtomically: (ReaderOwnerAndInputPublicationSnapshot) -> Boolean = { true }
) {
	var snapshot: ReaderOwnerAndInputPublicationSnapshot? = null
		private set
	var atomicCommitCount: Int = 0
		private set
	val observedPartialApplication: Boolean get() = false

	fun publish(
		command: ReaderTransitionCommand.CommitOwnerAndInputLease
	): ReaderOwnerAndInputPublicationResult {
		val subject = ReaderOwnerAndInputPublicationSubject.Successor(
			command.targetHandle,
			command.preparedFrameResource
		)
		return publishExact(
			transitionId = command.transitionId,
			subject = subject,
			owner = command.owner,
			binding = command.binding,
			resource = command.preparedFrameResource,
			requestedLease = command.requestedLease,
			publicationIdentity = command.publicationIdentity
		)
	}

	fun publish(
		command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
	): ReaderOwnerAndInputPublicationResult = publishExact(
		transitionId = command.transitionId,
		subject = ReaderOwnerAndInputPublicationSubject.Retained(command.retainedResource),
		owner = command.retainedOwner,
		binding = command.retainedBinding,
		resource = command.retainedResource,
		requestedLease = command.requestedLease,
		publicationIdentity = command.publicationIdentity
	)

	private fun publishExact(
		transitionId: paige.navic.reader.ReaderTransitionId,
		subject: ReaderOwnerAndInputPublicationSubject,
		owner: ReaderPresentationFrameOwner,
		binding: ReaderPresentationBinding,
		resource: ReaderTransitionResourceRegistration,
		requestedLease: ReaderTransitionInputLease,
		publicationIdentity: paige.navic.reader.ReaderOwnerAndInputPublicationIdentity
	): ReaderOwnerAndInputPublicationResult {
		fun rejected() = ReaderOwnerAndInputPublicationResult.Rejected(
			transitionId,
			subject,
			publicationIdentity,
			ReaderTransitionFailureReason.AtomicPublicationRejected
		)
		if (!readerOwnerMatchesBinding(owner, binding)) return rejected()
		if (resource.key.kind != readerAdoptedResourceKindFor(owner)) return rejected()
		val physicalLease = narrowOrVeto(requestedLease)
		if (!readerInputLeaseIsNoBroader(physicalLease, requestedLease)) return rejected()
		val candidate = ReaderOwnerAndInputPublicationSnapshot(
			owner,
			binding,
			resource,
			physicalLease
		)
		if (!publishAtomically(candidate)) return rejected()
		snapshot = candidate
		atomicCommitCount += 1
		return ReaderOwnerAndInputPublicationResult.Applied(
			transitionId,
			subject,
			owner,
			binding,
			physicalLease,
			publicationIdentity
		)
	}
}

private fun readerOwnerMatchesBinding(
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding
): Boolean = when (owner) {
	ReaderPresentationFrameOwner.Neutral -> false
	is ReaderPresentationFrameOwner.ShellCover -> owner.proof.binding == binding
	is ReaderPresentationFrameOwner.NativePage -> owner.proof.binding == binding
	is ReaderPresentationFrameOwner.Curl -> owner.frame.binding == binding
	is ReaderPresentationFrameOwner.LiveEngine -> owner.proof.binding == binding
}

private fun readerInputLeaseIsCompatibleWithOwner(
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding?,
	lease: ReaderTransitionInputLease
): Boolean = when (owner) {
	ReaderPresentationFrameOwner.Neutral ->
		binding == null && lease in setOf(ReaderTransitionInputLease.None, ReaderTransitionInputLease.ChromeOnly)
	is ReaderPresentationFrameOwner.ShellCover ->
		binding == owner.proof.binding && lease in setOf(
			ReaderTransitionInputLease.None,
			ReaderTransitionInputLease.ChromeOnly,
			ReaderTransitionInputLease.CoverActions
		)
	is ReaderPresentationFrameOwner.NativePage ->
		binding == owner.proof.binding && (
			lease == ReaderTransitionInputLease.None ||
				lease == ReaderTransitionInputLease.ChromeOnly ||
				lease == ReaderTransitionInputLease.NativePage(
					owner.proof.binding,
					owner.proof.textureGeneration
				)
		)
	is ReaderPresentationFrameOwner.Curl ->
		binding == owner.frame.binding && (
			lease == ReaderTransitionInputLease.None ||
				lease == ReaderTransitionInputLease.ChromeOnly ||
				lease is ReaderTransitionInputLease.ClaimedGesture
		)
	is ReaderPresentationFrameOwner.LiveEngine ->
		binding == owner.proof.binding && lease in setOf(
			ReaderTransitionInputLease.None,
			ReaderTransitionInputLease.ChromeOnly
		)
}

private fun readerInitialInputLeaseIsCompatibleWithOwner(
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding?,
	lease: ReaderInitialPresentationInputLease
): Boolean = when (owner) {
	ReaderPresentationFrameOwner.Neutral ->
		binding == null && lease in setOf(
			ReaderInitialPresentationInputLease.None,
			ReaderInitialPresentationInputLease.ChromeOnly
		)
	is ReaderPresentationFrameOwner.ShellCover ->
		binding == owner.proof.binding && lease in setOf(
			ReaderInitialPresentationInputLease.None,
			ReaderInitialPresentationInputLease.ChromeOnly,
			ReaderInitialPresentationInputLease.CoverActions
		)
	is ReaderPresentationFrameOwner.NativePage ->
		binding == owner.proof.binding && (
			lease == ReaderInitialPresentationInputLease.None ||
				lease == ReaderInitialPresentationInputLease.ChromeOnly ||
				lease == ReaderInitialPresentationInputLease.NativePage(
					owner.proof.binding,
					owner.proof.textureGeneration
				)
		)
	is ReaderPresentationFrameOwner.Curl ->
		binding == owner.frame.binding && lease in setOf(
			ReaderInitialPresentationInputLease.None,
			ReaderInitialPresentationInputLease.ChromeOnly
		)
	is ReaderPresentationFrameOwner.LiveEngine ->
		binding == owner.proof.binding && lease in setOf(
			ReaderInitialPresentationInputLease.None,
			ReaderInitialPresentationInputLease.ChromeOnly
		)
}

private fun readerInitialInputLeaseIsNoBroader(
	physical: ReaderInitialPresentationInputLease,
	requested: ReaderInitialPresentationInputLease
): Boolean = physical == requested || physical == ReaderInitialPresentationInputLease.None ||
	(physical == ReaderInitialPresentationInputLease.ChromeOnly &&
		requested != ReaderInitialPresentationInputLease.None)

private fun readerInputLeaseIsNoBroader(
	physical: ReaderTransitionInputLease,
	requested: ReaderTransitionInputLease
): Boolean = physical == requested || physical == ReaderTransitionInputLease.None ||
	(physical == ReaderTransitionInputLease.ChromeOnly && requested != ReaderTransitionInputLease.None)
