package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRendererSuccessorLineageId
import paige.navic.reader.ReaderRendererSuccessorReceipt
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverRetainedFrame
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind

internal enum class ReaderDeckAdmissionSlot {
	Active,
	Pending
}

internal fun ReaderDeckSubmissionRole.admissionSlot(): ReaderDeckAdmissionSlot = when (this) {
	ReaderDeckSubmissionRole.Active -> ReaderDeckAdmissionSlot.Active
	ReaderDeckSubmissionRole.Pending -> ReaderDeckAdmissionSlot.Pending
}

internal enum class ReaderDeckAdmissionCurrency {
	Current,
	AwaitingCausalSuccessor,
	Revoked
}

internal data class ReaderDeckPromotionReceipt internal constructor(
	val admissionId: Long,
	val lineageId: ReaderRendererSuccessorLineageId,
	val fromSlot: ReaderDeckAdmissionSlot,
	val toSlot: ReaderDeckAdmissionSlot
) {
	init {
		require(fromSlot == ReaderDeckAdmissionSlot.Pending)
		require(toSlot == ReaderDeckAdmissionSlot.Active)
	}
}

internal enum class ReaderDeckAdmissionState {
	Reserved,
	RendererOwned,
	CallbackObserved,
	AdmittedRendererOwned,
	ReleaseRequested,
	Released
}

internal data class ReaderDeckAdmissionDecisionIdentity(
	val normalizedDecision: ReaderPresentationDecision
)

internal data class ReaderDeckAdmissionAuthoritySnapshot(
	val hostEpoch: Long,
	val authorityVersion: ReaderPresentationReceiptVersion,
	val viewerGeneration: Long,
	val lifecycle: ReaderPresentationLifecycleState,
	val bindingSeed: ReaderPresentationBinding,
	val presentationToken: ReaderPresentationToken?,
	val decision: ReaderPresentationDecision,
	val rendererSuccessorReceipt: ReaderRendererSuccessorReceipt? = null
)

internal data class ReaderDeckAdmissionRequest(
	val candidateDecision: ReaderPresentationDecision?,
	val profileGeneration: Long?,
	val preparationGeneration: Long,
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val role: ReaderDeckSubmissionRole,
	val slot: ReaderDeckAdmissionSlot = role.admissionSlot()
)

internal data class ReaderDeckAdmissionCapability internal constructor(
	val hostEpoch: Long,
	val admissionId: Long,
	val authorityVersion: ReaderPresentationReceiptVersion,
	val viewerGeneration: Long,
	val originDecisionIdentity: ReaderDeckAdmissionDecisionIdentity,
	val originBinding: ReaderPresentationBinding,
	val presentationToken: ReaderPresentationToken?,
	val lineageId: ReaderRendererSuccessorLineageId?,
	val profileGeneration: Long,
	val preparationGeneration: Long,
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val role: ReaderDeckSubmissionRole,
	val slot: ReaderDeckAdmissionSlot
) {
	init {
		require(admissionId > 0L)
		require(profileGeneration > 0L)
		require(slot == role.admissionSlot())
		require(originBinding.profileGeneration == profileGeneration)
		require(originBinding.preparationGeneration == preparationGeneration)
		require(originBinding.rasterGeneration == rasterGeneration)
		require(originBinding.textureGeneration == textureGeneration)
		require((role == ReaderDeckSubmissionRole.Pending) == (lineageId != null))
	}
}

internal enum class ReaderDeckAdmissionCallbackDisposition {
	AwaitingRendererOwnership,
	Validate,
	DuplicateOrTerminal
}

internal enum class ReaderDeckAdmissionOwnershipDisposition {
	AwaitingCallback,
	ValidateCallback,
	Release
}

internal class ReaderDeckAdmission internal constructor(
	val capability: ReaderDeckAdmissionCapability
) {
	private val ownerThread = Thread.currentThread()
	private var callbackPending = false
	private var rendererOwnershipAcknowledged = false

	var state: ReaderDeckAdmissionState = ReaderDeckAdmissionState.Reserved
		private set

	var promotionReceipt: ReaderDeckPromotionReceipt? = null
		private set

	fun promotePendingToActive(): Boolean {
		checkOwnerThread()
		val lineageId = capability.lineageId ?: return false
		if (
			capability.role != ReaderDeckSubmissionRole.Pending ||
			capability.slot != ReaderDeckAdmissionSlot.Pending ||
			promotionReceipt != null ||
			state == ReaderDeckAdmissionState.ReleaseRequested ||
			state == ReaderDeckAdmissionState.Released
		) return false
		promotionReceipt = ReaderDeckPromotionReceipt(
			admissionId = capability.admissionId,
			lineageId = lineageId,
			fromSlot = ReaderDeckAdmissionSlot.Pending,
			toSlot = ReaderDeckAdmissionSlot.Active
		)
		return true
	}

	fun acknowledgeRendererOwnership(): ReaderDeckAdmissionOwnershipDisposition {
		checkOwnerThread()
		if (rendererOwnershipAcknowledged) {
			return if (state == ReaderDeckAdmissionState.ReleaseRequested) {
				ReaderDeckAdmissionOwnershipDisposition.Release
			} else {
				ReaderDeckAdmissionOwnershipDisposition.AwaitingCallback
			}
		}
		rendererOwnershipAcknowledged = true
		return when (state) {
			ReaderDeckAdmissionState.Reserved -> {
				if (callbackPending) {
					callbackPending = false
					state = ReaderDeckAdmissionState.CallbackObserved
					ReaderDeckAdmissionOwnershipDisposition.ValidateCallback
				} else {
					state = ReaderDeckAdmissionState.RendererOwned
					ReaderDeckAdmissionOwnershipDisposition.AwaitingCallback
				}
			}
			ReaderDeckAdmissionState.ReleaseRequested ->
				ReaderDeckAdmissionOwnershipDisposition.Release
			ReaderDeckAdmissionState.RendererOwned,
			ReaderDeckAdmissionState.CallbackObserved,
			ReaderDeckAdmissionState.AdmittedRendererOwned,
			ReaderDeckAdmissionState.Released ->
				ReaderDeckAdmissionOwnershipDisposition.AwaitingCallback
		}
	}

	fun observeCallback(): ReaderDeckAdmissionCallbackDisposition {
		checkOwnerThread()
		return when (state) {
			ReaderDeckAdmissionState.Reserved -> {
				callbackPending = true
				ReaderDeckAdmissionCallbackDisposition.AwaitingRendererOwnership
			}
			ReaderDeckAdmissionState.RendererOwned -> {
				state = ReaderDeckAdmissionState.CallbackObserved
				ReaderDeckAdmissionCallbackDisposition.Validate
			}
			ReaderDeckAdmissionState.CallbackObserved,
			ReaderDeckAdmissionState.AdmittedRendererOwned,
			ReaderDeckAdmissionState.ReleaseRequested,
			ReaderDeckAdmissionState.Released ->
				ReaderDeckAdmissionCallbackDisposition.DuplicateOrTerminal
		}
	}

	fun markAdmittedRendererOwned(): Boolean {
		checkOwnerThread()
		if (
			state != ReaderDeckAdmissionState.CallbackObserved ||
			!rendererOwnershipAcknowledged
		) return false
		state = ReaderDeckAdmissionState.AdmittedRendererOwned
		return true
	}

	fun requestRelease(): Boolean {
		checkOwnerThread()
		return when (state) {
			ReaderDeckAdmissionState.Reserved,
			ReaderDeckAdmissionState.RendererOwned,
			ReaderDeckAdmissionState.CallbackObserved,
			ReaderDeckAdmissionState.AdmittedRendererOwned -> {
				state = ReaderDeckAdmissionState.ReleaseRequested
				rendererOwnershipAcknowledged
			}
			ReaderDeckAdmissionState.ReleaseRequested,
			ReaderDeckAdmissionState.Released -> false
		}
	}

	fun releaseReservationWithoutRenderer(): Boolean {
		checkOwnerThread()
		if (rendererOwnershipAcknowledged || state == ReaderDeckAdmissionState.Released) return false
		state = ReaderDeckAdmissionState.Released
		callbackPending = false
		return true
	}

	fun markReleased(): Boolean {
		checkOwnerThread()
		if (state == ReaderDeckAdmissionState.Released) return false
		state = ReaderDeckAdmissionState.Released
		callbackPending = false
		return true
	}

	private fun checkOwnerThread() {
		check(Thread.currentThread() === ownerThread) {
			"Deck admission state must remain on its issuing Android host context"
		}
	}
}

internal interface ReaderDeckAdmissionLeaseHost {
	fun reserve(request: ReaderDeckAdmissionRequest): ReaderDeckAdmission?
	fun currency(
		admission: ReaderDeckAdmission,
		observedDecision: ReaderPresentationDecision?
	): ReaderDeckAdmissionCurrency
	fun isCurrent(admission: ReaderDeckAdmissionCapability): Boolean
	fun isOwnerCurrent(admission: ReaderDeckAdmissionCapability): Boolean
}

internal object UnavailableReaderDeckAdmissionLeaseHost : ReaderDeckAdmissionLeaseHost {
	override fun reserve(request: ReaderDeckAdmissionRequest): ReaderDeckAdmission? = null
	override fun currency(
		admission: ReaderDeckAdmission,
		observedDecision: ReaderPresentationDecision?
	): ReaderDeckAdmissionCurrency = ReaderDeckAdmissionCurrency.Revoked
	override fun isCurrent(admission: ReaderDeckAdmissionCapability): Boolean = false
	override fun isOwnerCurrent(admission: ReaderDeckAdmissionCapability): Boolean = false
}

internal enum class ReaderDeckAdmissionProductionMode { LegacyOnly }

internal object ReaderDeckAdmissionProductionPolicy {
	val mode: ReaderDeckAdmissionProductionMode = ReaderDeckAdmissionProductionMode.LegacyOnly
	val coordinatorActivationAvailable: Boolean = false

	fun select(legacyHost: ReaderDeckAdmissionLeaseHost): ReaderDeckAdmissionLeaseHost = legacyHost
}

internal fun ReaderDeckAdmissionLeaseHost.selectedForProductionDeckAdmission():
	ReaderDeckAdmissionLeaseHost = ReaderDeckAdmissionProductionPolicy.select(this)

internal enum class ReaderLegacyDeckResourceOrigin {
	Owned,
	Pending,
	Discovered
}

internal enum class ReaderLegacyPredecessorEvidence {
	Truthful,
	Unprovable
}

internal data class ReaderLegacyDeckResource(
	val key: ReaderTransitionResourceKey,
	val origin: ReaderLegacyDeckResourceOrigin,
	val predecessorEvidence: ReaderLegacyPredecessorEvidence =
		ReaderLegacyPredecessorEvidence.Unprovable
) {
	init {
		require(key.kind == ReaderTransitionResourceKind.Deck)
	}
}

internal sealed interface ReaderLegacyDeckInventory {
	data object Incomplete : ReaderLegacyDeckInventory
	data class Complete(val resources: List<ReaderLegacyDeckResource>) : ReaderLegacyDeckInventory
}

internal class ReaderDeckResourceInventoryBoundary(
	private val inventoryIsComplete: () -> Boolean = { true }
) {
	private val resources = linkedSetOf<ReaderLegacyDeckResource>()

	fun register(resource: ReaderLegacyDeckResource): Boolean = resources.add(resource)

	fun freezeAndInventory(): ReaderLegacyDeckInventory =
		if (inventoryIsComplete()) {
			ReaderLegacyDeckInventory.Complete(resources.toList())
		} else {
			ReaderLegacyDeckInventory.Incomplete
		}
}

internal enum class ReaderDeckAdmissionCutoverOutcome {
	NotAttempted,
	InventoryIncomplete,
	Draining,
	Activated,
	ReleaseOnly
}

internal data class ReaderDeckAdmissionCutoverSnapshot(
	val inventoryCount: Int,
	val ownedCount: Int,
	val pendingCount: Int,
	val discoveredCount: Int,
	val adoptedCount: Int,
	val releaseCommandCount: Int,
	val releaseConfirmationCount: Int,
	val releaseOwnedCount: Int,
	val releaseCommandIssuedCount: Int,
	val releasedCount: Int,
	val legacyAdmissionOpen: Boolean,
	val coordinatorAdmissionOpen: Boolean,
	val activationOutcome: ReaderDeckAdmissionCutoverOutcome
)

internal class ReaderDeckAdmissionCutover(
	private val legacyAdmissionHost: ReaderDeckAdmissionLeaseHost,
	private val coordinatorAdmissionHost: ReaderDeckAdmissionLeaseHost,
	private val legacyInventory: () -> ReaderLegacyDeckInventory,
	private val releaseLedger: ReaderTransitionReleaseLedger,
	private val enqueueResourceFact: (ReaderTransitionFact) -> Unit
) : ReaderDeckAdmissionLeaseHost {
	private enum class WriterState { Legacy, Frozen, Coordinator, ReleaseOnly }

	private val origins = linkedMapOf<ReaderTransitionResourceKey, MutableSet<ReaderLegacyDeckResourceOrigin>>()
	private var writerState = WriterState.Legacy
	private var adoptedKey: ReaderTransitionResourceKey? = null
	private var activationOutcome = ReaderDeckAdmissionCutoverOutcome.NotAttempted

	val legacyAdmissionOpen: Boolean
		get() = writerState == WriterState.Legacy

	val coordinatorAdmissionOpen: Boolean
		get() = writerState == WriterState.Coordinator

	val coordinatorCommandsAllowed: Boolean
		get() = writerState == WriterState.Frozen ||
			writerState == WriterState.Coordinator ||
			writerState == WriterState.ReleaseOnly

	fun activate(): Boolean {
		if (writerState == WriterState.Coordinator) return true
		if (writerState != WriterState.Legacy) return false
		writerState = WriterState.Frozen
		val inventory = legacyInventory()
		if (inventory !is ReaderLegacyDeckInventory.Complete) {
			writerState = WriterState.Legacy
			activationOutcome = ReaderDeckAdmissionCutoverOutcome.InventoryIncomplete
			return false
		}

		activationOutcome = ReaderDeckAdmissionCutoverOutcome.Draining
		inventory.resources.forEach(::importResource)
		val truthfulCandidates = origins.keys.filter { key ->
			ReaderLegacyDeckResourceOrigin.Owned in origins.getValue(key) &&
				inventory.resources.any {
					it.key == key &&
						it.predecessorEvidence == ReaderLegacyPredecessorEvidence.Truthful
				}
		}
		adoptedKey = truthfulCandidates.singleOrNull()
		origins.keys.filterNot { it == adoptedKey }.forEach(::enqueueReleaseFact)
		completeActivationIfDrained()
		return writerState == WriterState.Coordinator
	}

	fun observeLegacyResource(resource: ReaderLegacyDeckResource) {
		if (writerState == WriterState.Legacy) return
		importResource(resource)
		if (resource.key != adoptedKey) enqueueReleaseFact(resource.key)
	}

	fun onCoordinatorResourceFactProcessed(fact: ReaderTransitionFact) {
		if (fact is ReaderTransitionFact.ResourceReleased) completeActivationIfDrained()
	}

	fun retainReleaseOnlySinkAfterCloseTimeout() {
		if (writerState == WriterState.Legacy) return
		writerState = WriterState.ReleaseOnly
		activationOutcome = ReaderDeckAdmissionCutoverOutcome.ReleaseOnly
		adoptedKey = null
		origins.keys.forEach(::enqueueReleaseFact)
	}

	fun snapshot(): ReaderDeckAdmissionCutoverSnapshot {
		val releaseSnapshot = releaseLedger.snapshot()
		return ReaderDeckAdmissionCutoverSnapshot(
			inventoryCount = origins.size,
			ownedCount = origins.values.count { ReaderLegacyDeckResourceOrigin.Owned in it },
			pendingCount = origins.values.count { ReaderLegacyDeckResourceOrigin.Pending in it },
			discoveredCount = origins.values.count {
				ReaderLegacyDeckResourceOrigin.Discovered in it
			},
			adoptedCount = if (adoptedKey == null) 0 else 1,
			releaseCommandCount = releaseSnapshot.issuedCount + releaseSnapshot.releasedCount,
			releaseConfirmationCount = releaseSnapshot.releasedCount,
			releaseOwnedCount = releaseSnapshot.ownedCount,
			releaseCommandIssuedCount = releaseSnapshot.issuedCount,
			releasedCount = releaseSnapshot.releasedCount,
			legacyAdmissionOpen = legacyAdmissionOpen,
			coordinatorAdmissionOpen = coordinatorAdmissionOpen,
			activationOutcome = activationOutcome
		)
	}

	override fun reserve(request: ReaderDeckAdmissionRequest): ReaderDeckAdmission? = when (writerState) {
		WriterState.Legacy -> legacyAdmissionHost.reserve(request)
		WriterState.Coordinator -> coordinatorAdmissionHost.reserve(request)
		WriterState.Frozen,
		WriterState.ReleaseOnly -> null
	}

	override fun currency(
		admission: ReaderDeckAdmission,
		observedDecision: ReaderPresentationDecision?
	): ReaderDeckAdmissionCurrency = currentHost().currency(admission, observedDecision)

	override fun isCurrent(admission: ReaderDeckAdmissionCapability): Boolean =
		currentHost().isCurrent(admission)

	override fun isOwnerCurrent(admission: ReaderDeckAdmissionCapability): Boolean =
		currentHost().isOwnerCurrent(admission)

	private fun currentHost(): ReaderDeckAdmissionLeaseHost = when (writerState) {
		WriterState.Legacy,
		WriterState.Frozen -> legacyAdmissionHost
		WriterState.Coordinator,
		WriterState.ReleaseOnly -> coordinatorAdmissionHost
	}

	private fun importResource(resource: ReaderLegacyDeckResource) {
		origins.getOrPut(resource.key, ::linkedSetOf).add(resource.origin)
		releaseLedger.register(resource.key)
	}

	private fun enqueueReleaseFact(key: ReaderTransitionResourceKey) {
		val resourceOrigins = origins.getValue(key)
		val fact = when {
			ReaderLegacyDeckResourceOrigin.Owned in resourceOrigins ->
				ReaderTransitionFact.DeckOwned(key.transitionId, key)
			ReaderLegacyDeckResourceOrigin.Pending in resourceOrigins ->
				ReaderTransitionFact.DeckReserved(key.transitionId, key)
			else -> ReaderTransitionFact.ResourceObserved(key.transitionId, key)
		}
		enqueueResourceFact(fact)
	}

	private fun completeActivationIfDrained() {
		if (writerState != WriterState.Frozen) return
		val allDrained = origins.keys
			.filterNot { it == adoptedKey }
			.all { releaseLedger.stateOf(it) == ReaderTransitionResourceState.Released }
		if (!allDrained) return
		writerState = WriterState.Coordinator
		activationOutcome = ReaderDeckAdmissionCutoverOutcome.Activated
	}
}

internal fun readerAcceptedDeckBindingOrNull(
	targetBinding: ReaderPresentationBinding?,
	profileGeneration: Long?,
	preparationGeneration: Long,
	rasterGeneration: Long,
	textureGeneration: Long
): ReaderPresentationBinding? {
	val binding = targetBinding ?: return null
	val resolvedProfileGeneration = profileGeneration?.takeIf { it > 0L } ?: return null
	if (
		binding.profileGeneration != 0L &&
		binding.profileGeneration != resolvedProfileGeneration
	) return null
	return binding.copy(
		profileGeneration = resolvedProfileGeneration,
		preparationGeneration = preparationGeneration,
		rasterGeneration = rasterGeneration,
		textureGeneration = textureGeneration
	)
}

internal fun ReaderPresentationDecision.rendererCallbackTokenOrNull(): ReaderPresentationToken? =
	when (val currentAuthority = authority) {
		is ReaderPresentationAuthority.CurlGesture -> currentAuthority.frame.frame.token
		is ReaderPresentationAuthority.CurlSettlementPending ->
			currentAuthority.retainedFrame.frame.token
		is ReaderPresentationAuthority.SettledNativePage ->
			currentAuthority.frame.proof.transitionToken
		else -> (requiredTransition as? ReaderRequiredTransition.PresentNativePage)?.token
	}

internal fun readerDeckAdmissionDecisionIdentityOrNull(
	decision: ReaderPresentationDecision?,
	binding: ReaderPresentationBinding
): ReaderDeckAdmissionDecisionIdentity? {
	val sourceBinding = decision?.targetBinding ?: return null
	val preparationGeneration = binding.preparationGeneration ?: return null
	if (
		readerAcceptedDeckBindingOrNull(
			targetBinding = sourceBinding,
			profileGeneration = binding.profileGeneration,
			preparationGeneration = preparationGeneration,
			rasterGeneration = binding.rasterGeneration ?: return null,
			textureGeneration = binding.textureGeneration ?: return null
		) != binding
	) return null
	fun normalized(candidate: ReaderPresentationBinding): ReaderPresentationBinding =
		if (candidate == sourceBinding) binding else candidate
	fun normalized(proof: ReaderNativePagePresentationProof): ReaderNativePagePresentationProof =
		if (proof.binding != sourceBinding) proof else proof.copy(
			binding = binding,
			rasterGeneration = requireNotNull(binding.rasterGeneration),
			textureGeneration = requireNotNull(binding.textureGeneration)
		)
	fun normalized(frame: paige.navic.reader.ReaderCurlPresentationFrame) =
		if (frame.binding != sourceBinding) frame else frame.copy(
			binding = binding,
			rasterGeneration = requireNotNull(binding.rasterGeneration),
			textureGeneration = requireNotNull(binding.textureGeneration)
		)
	fun normalized(owner: ReaderPresentationFrameOwner): ReaderPresentationFrameOwner = when (owner) {
		ReaderPresentationFrameOwner.Neutral -> owner
		is ReaderPresentationFrameOwner.ShellCover -> owner.copy(
			proof = owner.proof.copy(binding = normalized(owner.proof.binding))
		)
		is ReaderPresentationFrameOwner.NativePage -> owner.copy(proof = normalized(owner.proof))
		is ReaderPresentationFrameOwner.Curl -> owner.copy(frame = normalized(owner.frame))
		is ReaderPresentationFrameOwner.LiveEngine -> owner.copy(
			proof = owner.proof.copy(binding = normalized(owner.proof.binding))
		)
	}
	fun normalized(retained: ReaderShellCoverRetainedFrame): ReaderShellCoverRetainedFrame =
		when (retained) {
			is ReaderShellCoverRetainedFrame.Neutral -> retained.copy(
				binding = normalized(retained.binding)
			)
			is ReaderShellCoverRetainedFrame.NativePage -> retained.copy(
				frame = normalized(retained.frame) as ReaderPresentationFrameOwner.NativePage
			)
			is ReaderShellCoverRetainedFrame.TerminalCurl -> retained.copy(
				frame = normalized(retained.frame) as ReaderPresentationFrameOwner.Curl
			)
		}
	val authority = when (val current = decision.authority) {
		ReaderPresentationAuthority.Unavailable -> current
		is ReaderPresentationAuthority.ShellCover -> current.copy(
			proof = current.proof.copy(binding = normalized(current.proof.binding))
		)
		is ReaderPresentationAuthority.ShellCoverCommitPending -> current.copy(
			retainedFrame = normalized(current.retainedFrame),
			binding = normalized(current.binding)
		)
		is ReaderPresentationAuthority.CurlGesture -> current.copy(
			frame = normalized(current.frame) as ReaderPresentationFrameOwner.Curl
		)
		is ReaderPresentationAuthority.CurlSettlementPending -> current.copy(
			retainedFrame = normalized(current.retainedFrame) as ReaderPresentationFrameOwner.Curl,
			binding = normalized(current.binding)
		)
		is ReaderPresentationAuthority.SettledNativePage -> current.copy(
			frame = normalized(current.frame) as ReaderPresentationFrameOwner.NativePage
		)
		is ReaderPresentationAuthority.LiveEngineHandoffPending -> current.copy(
			retainedFrame = normalized(current.retainedFrame),
			binding = normalized(current.binding)
		)
		is ReaderPresentationAuthority.LiveEngineExposed -> current.copy(
			frame = normalized(current.frame) as ReaderPresentationFrameOwner.LiveEngine
		)
		is ReaderPresentationAuthority.BlockingPreparation -> current.copy(
			retainedFrame = normalized(current.retainedFrame),
			nativePresentationRequest = current.nativePresentationRequest?.copy(
				binding = normalized(current.nativePresentationRequest.binding)
			)
		)
	}
	val transition = when (val current = decision.requiredTransition) {
		ReaderRequiredTransition.None -> current
		is ReaderRequiredTransition.CommitShellCover -> current.copy(
			binding = normalized(current.binding)
		)
		is ReaderRequiredTransition.PresentNativePage -> current.copy(
			binding = normalized(current.binding)
		)
		is ReaderRequiredTransition.ExposeLiveEngine -> current.copy(
			binding = normalized(current.binding)
		)
	}
	return ReaderDeckAdmissionDecisionIdentity(
		decision.copy(
			authority = authority,
			frameOwner = normalized(decision.frameOwner),
			requiredTransition = transition,
			targetBinding = binding
		)
	)
}

internal fun readerDeckAdmissionCapabilityOrNull(
	authority: ReaderDeckAdmissionAuthoritySnapshot?,
	admissionId: Long,
	request: ReaderDeckAdmissionRequest
): ReaderDeckAdmissionCapability? {
	val current = authority ?: return null
	if (
		admissionId <= 0L ||
		current.lifecycle != ReaderPresentationLifecycleState.Foreground ||
		request.slot != request.role.admissionSlot()
	) return null
	val candidate = request.candidateDecision ?: return null
	if (candidate.lifecycle != ReaderPresentationLifecycleState.Foreground) return null
	val authoritativeBinding = readerAcceptedDeckBindingOrNull(
		targetBinding = current.bindingSeed,
		profileGeneration = request.profileGeneration,
		preparationGeneration = request.preparationGeneration,
		rasterGeneration = request.rasterGeneration,
		textureGeneration = request.textureGeneration
	) ?: return null
	val candidateBinding = readerAcceptedDeckBindingOrNull(
		targetBinding = candidate.targetBinding,
		profileGeneration = request.profileGeneration,
		preparationGeneration = request.preparationGeneration,
		rasterGeneration = request.rasterGeneration,
		textureGeneration = request.textureGeneration
	) ?: return null
	if (
		candidateBinding != authoritativeBinding ||
		candidate.rendererCallbackTokenOrNull() != current.presentationToken
	) return null
	val authoritativeIdentity = readerDeckAdmissionDecisionIdentityOrNull(
		current.decision,
		authoritativeBinding
	) ?: return null
	val candidateIdentity = readerDeckAdmissionDecisionIdentityOrNull(
		candidate,
		candidateBinding
	) ?: return null
	if (candidateIdentity != authoritativeIdentity) return null
	val lineageId = if (request.role == ReaderDeckSubmissionRole.Pending) {
		current.presentationToken?.let { ReaderRendererSuccessorLineageId(it.value) } ?: return null
	} else {
		null
	}
	return ReaderDeckAdmissionCapability(
		hostEpoch = current.hostEpoch,
		admissionId = admissionId,
		authorityVersion = current.authorityVersion,
		viewerGeneration = current.viewerGeneration,
		originDecisionIdentity = authoritativeIdentity,
		originBinding = authoritativeBinding,
		presentationToken = current.presentationToken,
		lineageId = lineageId,
		profileGeneration = authoritativeBinding.profileGeneration,
		preparationGeneration = request.preparationGeneration,
		rasterGeneration = request.rasterGeneration,
		textureGeneration = request.textureGeneration,
		role = request.role,
		slot = request.slot
	)
}

internal fun readerDeckAdmissionOwnerAuthorityMatches(
	authority: ReaderDeckAdmissionAuthoritySnapshot?,
	admission: ReaderDeckAdmissionCapability
): Boolean {
	val current = authority ?: return false
	return current.hostEpoch == admission.hostEpoch &&
		current.viewerGeneration == admission.viewerGeneration &&
		current.lifecycle == ReaderPresentationLifecycleState.Foreground &&
		current.authorityVersion.readerSessionGeneration ==
			admission.authorityVersion.readerSessionGeneration &&
		current.authorityVersion.publicationIdentity ==
			admission.authorityVersion.publicationIdentity &&
		current.authorityVersion.eventSequence >= admission.authorityVersion.eventSequence
}

internal fun readerDeckAdmissionAuthorityMatches(
	authority: ReaderDeckAdmissionAuthoritySnapshot?,
	admission: ReaderDeckAdmissionCapability
): Boolean {
	val current = authority ?: return false
	if (
		current.hostEpoch != admission.hostEpoch ||
		current.viewerGeneration != admission.viewerGeneration ||
		current.lifecycle != ReaderPresentationLifecycleState.Foreground ||
		current.presentationToken != admission.presentationToken ||
		current.authorityVersion.readerSessionGeneration !=
			admission.authorityVersion.readerSessionGeneration ||
		current.authorityVersion.publicationIdentity !=
			admission.authorityVersion.publicationIdentity ||
		current.authorityVersion.eventSequence < admission.authorityVersion.eventSequence
	) return false
	val binding = readerAcceptedDeckBindingOrNull(
		targetBinding = current.bindingSeed,
		profileGeneration = admission.profileGeneration,
		preparationGeneration = admission.preparationGeneration,
		rasterGeneration = admission.rasterGeneration,
		textureGeneration = admission.textureGeneration
	) ?: return false
	val decisionIdentity = readerDeckAdmissionDecisionIdentityOrNull(
		current.decision,
		binding
	) ?: return false
	return binding == admission.originBinding &&
		decisionIdentity == admission.originDecisionIdentity
}

internal fun readerDeckAdmissionAuthorityCurrency(
	authority: ReaderDeckAdmissionAuthoritySnapshot?,
	admission: ReaderDeckAdmission,
	observedDecision: ReaderPresentationDecision?
): ReaderDeckAdmissionCurrency {
	val current = authority ?: return ReaderDeckAdmissionCurrency.Revoked
	val capability = admission.capability
	if (
		!readerDeckAdmissionOwnerAuthorityMatches(current, capability) ||
		observedDecision == null ||
		current.decision != observedDecision
	) return ReaderDeckAdmissionCurrency.Revoked
	val promotion = admission.promotionReceipt
	if (promotion == null) {
		return if (readerDeckAdmissionAuthorityMatches(current, capability)) {
			ReaderDeckAdmissionCurrency.Current
		} else {
			ReaderDeckAdmissionCurrency.Revoked
		}
	}
	if (
		promotion.admissionId != capability.admissionId ||
		promotion.lineageId != capability.lineageId
	) return ReaderDeckAdmissionCurrency.Revoked
	val receipt = current.rendererSuccessorReceipt
	if (receipt == null) {
		return if (readerDeckAdmissionAuthorityMatches(current, capability)) {
			ReaderDeckAdmissionCurrency.AwaitingCausalSuccessor
		} else {
			ReaderDeckAdmissionCurrency.Revoked
		}
	}
	val normalizedOrigin = readerAcceptedDeckBindingOrNull(
		targetBinding = receipt.originBinding,
		profileGeneration = capability.profileGeneration,
		preparationGeneration = capability.preparationGeneration,
		rasterGeneration = capability.rasterGeneration,
		textureGeneration = capability.textureGeneration
	)
	return if (
		receipt.lineageId == promotion.lineageId &&
		receipt.token == capability.presentationToken &&
		receipt.rasterGeneration == capability.rasterGeneration &&
		receipt.textureGeneration == capability.textureGeneration &&
		normalizedOrigin == capability.originBinding &&
		current.bindingSeed == receipt.currentBinding
	) {
		ReaderDeckAdmissionCurrency.Current
	} else {
		ReaderDeckAdmissionCurrency.Revoked
	}
}
