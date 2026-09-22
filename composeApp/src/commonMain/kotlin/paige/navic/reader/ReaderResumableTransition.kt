package paige.navic.reader

data class ReaderTransitionParentIdentity(
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	val sequence: Long
) {
	override fun hashCode(): Int = 0x50415245
	override fun toString(): String = "ReaderTransitionParentIdentity(<redacted>)"
}

sealed interface ReaderExpectedPresentationBinding {
	data class Exact(
		val binding: ReaderPresentationBinding
	) : ReaderExpectedPresentationBinding {
		override fun hashCode(): Int = 0x45584143
		override fun toString(): String = "ReaderExpectedPresentationBinding.Exact(<redacted>)"
	}

	sealed class SemanticSuccessor : ReaderExpectedPresentationBinding {
		abstract val predecessor: ReaderPresentationBinding
		abstract val requestSequence: Long

		companion object {
			operator fun invoke(
				predecessor: ReaderPresentationBinding,
				requestSequence: Long
			): SemanticSuccessor = FromPredecessor(predecessor, requestSequence)
		}

		private data class FromPredecessor(
			override val predecessor: ReaderPresentationBinding,
			override val requestSequence: Long
		) : SemanticSuccessor() {
			init {
				require(requestSequence > 0L)
			}

			override fun hashCode(): Int = 0x53454D53

			override fun toString(): String =
				"ReaderExpectedPresentationBinding.SemanticSuccessor(<redacted>)"
		}
	}

	data class FoliateAuthoritativeInitial(
		override val requestSequence: Long
	) : SemanticSuccessor() {
		init {
			require(requestSequence > 0L)
		}

		override val predecessor: ReaderPresentationBinding
			get() = error("Initial Foliate authority has no predecessor binding")

		override fun hashCode(): Int = 0x464F4C49

		override fun toString(): String =
			"ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(<redacted>)"
	}

	data class NoCommittedPresentation(
		override val requestSequence: Long
	) : SemanticSuccessor() {
		init {
			require(requestSequence > 0L)
		}

		override val predecessor: ReaderPresentationBinding
			get() = error("Neutral close has no predecessor binding")

		override fun hashCode(): Int = 0x4E4F434F

		override fun toString(): String =
			"ReaderExpectedPresentationBinding.NoCommittedPresentation(<redacted>)"
	}
}

enum class ReaderTransitionOperation {
	BootstrapNativePage,
	ShellCoverCommit,
	CoverToPageEntry,
	CurlClaimAndSettlement,
	NativeToLiveHandoff,
	LiveToNativeHandback,
	ExternalSemanticRelocation,
	ReflowProfileReplacement,
	VisibilityRestore,
	RendererRecovery,
	PublicationClose
}

data class ReaderTransitionDeadlinePolicy(
	val noProgressMillis: Long?,
	val hardMillis: Long
) {
	init {
		require(noProgressMillis == null || noProgressMillis > 0L)
		require(hardMillis > 0L)
	}
}

fun ReaderTransitionOperation.deadlinePolicy(): ReaderTransitionDeadlinePolicy = when (this) {
	ReaderTransitionOperation.BootstrapNativePage,
	ReaderTransitionOperation.CoverToPageEntry,
	ReaderTransitionOperation.ExternalSemanticRelocation,
	ReaderTransitionOperation.ReflowProfileReplacement,
	ReaderTransitionOperation.VisibilityRestore,
	ReaderTransitionOperation.RendererRecovery -> ReaderTransitionDeadlinePolicy(10_000L, 30_000L)

	ReaderTransitionOperation.ShellCoverCommit,
	ReaderTransitionOperation.NativeToLiveHandoff,
	ReaderTransitionOperation.LiveToNativeHandback -> ReaderTransitionDeadlinePolicy(null, 2_000L)

	ReaderTransitionOperation.CurlClaimAndSettlement -> ReaderTransitionDeadlinePolicy(null, 5_000L)
	ReaderTransitionOperation.PublicationClose -> ReaderTransitionDeadlinePolicy(null, 2_000L)
}

data class ReaderTransitionId(
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	val sequence: Long,
	val operation: ReaderTransitionOperation,
	val expectedBinding: ReaderExpectedPresentationBinding,
	val parent: ReaderTransitionParentIdentity? = null
) {
	init {
		require(readerSessionGeneration > 0L)
		require(coordinatorEpoch > 0L)
		require(sequence > 0L)
		if (sequence == 1L) {
			require(parent == null)
		} else {
			val immediateParent = requireNotNull(parent)
			require(immediateParent.readerSessionGeneration == readerSessionGeneration)
			require(immediateParent.coordinatorEpoch == coordinatorEpoch)
			require(immediateParent.sequence == sequence - 1L)
		}
	}

	override fun hashCode(): Int = 0x54524944
	override fun toString(): String = "ReaderTransitionId(<redacted>)"
}

fun ReaderTransitionId.parentIdentity() = ReaderTransitionParentIdentity(
	readerSessionGeneration = readerSessionGeneration,
	coordinatorEpoch = coordinatorEpoch,
	sequence = sequence
)

enum class ReaderTransitionWakeKind {
	Restored,
	HostAvailable,
	WebViewAvailable,
	PaginationProfileReady,
	FoliateDestinationCommitted,
	RasterProofAvailable,
	RendererCapacityAvailable,
	PresentationCommandApplied,
	VisibilityRestored,
	Retry
}

enum class ReaderTransitionPhaseKind {
	Accepted,
	AwaitingPrerequisites,
	CommandIssued,
	AwaitingProof,
	Committing
}

enum class ReaderTransitionFactKind {
	Intent,
	FoliateDestinationCommitted,
	SettlementAcknowledged,
	MaterialBindingAllocated,
	ViewportProfileReplaced,
	RasterProgress,
	RasterProven,
	RasterDeferred,
	RasterFailed,
	ResourceObserved,
	DeckReserved,
	DeckOwned,
	DeckPrepared,
	DeckRejected,
	ResourceReleased,
	RendererCapacityAvailable,
	HostAvailable,
	PaginationProfileReady,
	RendererGenerationReady,
	FrameTargetPrepared,
	FrameTargetPreparationRejected,
	PreparedFrame,
	OwnerAndInputPublicationApplied,
	OwnerAndInputPublicationRejected,
	CoverPostDraw,
	WebViewExposure,
	VisibilityChanged,
	ResourceLost,
	DeadlineExpired,
	CommandRejected,
	SemanticPortContractViolated,
	Retry,
	PublicationReplaced,
	PublicationClosed
}

enum class ReaderTransitionCommandStage {
	SemanticSynchronization,
	MaterialAllocation,
	RasterPreparation,
	DeckReservation,
	FrameTargetPreparation,
	FramePresentation,
	RetainedPublication,
	SuccessorPublication,
	TimerBinding
}

enum class ReaderTransitionCommandRejectionReason {
	MissingHandle,
	ExpiredHandle,
	ConsumedHandle,
	WrongSessionHandle,
	SemanticSlotCapacity,
	SemanticRegistryRejected,
	SemanticExecutionRejected,
	MaterialAllocationRejected,
	RasterPreparationRejected,
	DeckReservationRejected,
	FrameTargetRejected,
	FramePresentationRejected,
	PublicationRejected,
	TimerBindingRejected,
	TimerOwnershipConflict,
	CommandPortRejected,
	CommandThrew
}

private fun ReaderTransitionCommandRejectionReason.isCompatibleWith(
	stage: ReaderTransitionCommandStage
): Boolean = when (this) {
	ReaderTransitionCommandRejectionReason.MissingHandle,
	ReaderTransitionCommandRejectionReason.ExpiredHandle,
	ReaderTransitionCommandRejectionReason.ConsumedHandle,
	ReaderTransitionCommandRejectionReason.WrongSessionHandle,
	ReaderTransitionCommandRejectionReason.SemanticSlotCapacity,
	ReaderTransitionCommandRejectionReason.SemanticRegistryRejected,
	ReaderTransitionCommandRejectionReason.SemanticExecutionRejected ->
		stage == ReaderTransitionCommandStage.SemanticSynchronization
	ReaderTransitionCommandRejectionReason.MaterialAllocationRejected ->
		stage == ReaderTransitionCommandStage.MaterialAllocation
	ReaderTransitionCommandRejectionReason.RasterPreparationRejected ->
		stage == ReaderTransitionCommandStage.RasterPreparation
	ReaderTransitionCommandRejectionReason.DeckReservationRejected ->
		stage == ReaderTransitionCommandStage.DeckReservation
	ReaderTransitionCommandRejectionReason.FrameTargetRejected ->
		stage == ReaderTransitionCommandStage.FrameTargetPreparation
	ReaderTransitionCommandRejectionReason.FramePresentationRejected ->
		stage == ReaderTransitionCommandStage.FramePresentation
	ReaderTransitionCommandRejectionReason.PublicationRejected ->
		stage == ReaderTransitionCommandStage.RetainedPublication ||
			stage == ReaderTransitionCommandStage.SuccessorPublication
	ReaderTransitionCommandRejectionReason.TimerBindingRejected,
	ReaderTransitionCommandRejectionReason.TimerOwnershipConflict ->
		stage == ReaderTransitionCommandStage.TimerBinding
	ReaderTransitionCommandRejectionReason.CommandPortRejected,
	ReaderTransitionCommandRejectionReason.CommandThrew -> true
}

enum class ReaderSaturatingCallbackCount {
	Zero,
	One,
	Two,
	ThreeOrMore;

	fun increment(): ReaderSaturatingCallbackCount = when (this) {
		Zero -> One
		One -> Two
		Two, ThreeOrMore -> ThreeOrMore
	}
}

enum class ReaderSemanticPortContractViolationReason {
	CallbackThenRejected,
	CallbackThenThrew,
	RejectedThenLateCallback,
	DuplicateCallback,
	DuplicateCallbackThenRejected,
	DuplicateCallbackThenThrew,
	CallbackAfterTerminalDisposition,
	RejectedAfterMutationStarted,
	ThrowAfterMutationStarted
}

data class ReaderTransitionPhaseContract(
	val awaitedProofs: Set<ReaderTransitionProofKind>,
	val deadlineOwner: ReaderTransitionId,
	val supersession: ReaderTransitionSupersession,
	val retainedOwner: ReaderPresentationFrameOwner,
	val inputLease: ReaderTransitionInputLease,
	val callbackSources: Set<ReaderTransitionFactKind>,
	val admissibleCommandStages: Set<ReaderTransitionCommandStage>
) {
	init {
		require(awaitedProofs.isNotEmpty())
		require(callbackSources.isNotEmpty())
		require(
			(ReaderTransitionFactKind.CommandRejected in callbackSources) ==
				admissibleCommandStages.isNotEmpty()
		)
	}
}

data class ReaderTransitionPhase(
	val kind: ReaderTransitionPhaseKind,
	val contract: ReaderTransitionPhaseContract
)

sealed interface ReaderTransitionOutcome {
	data class Succeeded(
		val committedOwner: ReaderPresentationFrameOwner,
		val binding: ReaderPresentationBinding
	) : ReaderTransitionOutcome

	data class Failed(
		val reason: ReaderTransitionFailureReason,
		val retryability: ReaderTransitionRetryability,
		val retainedOwner: ReaderPresentationFrameOwner
	) : ReaderTransitionOutcome

	data class Cancelled(
		val reason: ReaderTransitionCancellationReason,
		val retainedOwner: ReaderPresentationFrameOwner
	) : ReaderTransitionOutcome

	data class Deferred(
		val resumeRecord: ReaderTransitionResumeRecord,
		val retainedOwner: ReaderPresentationFrameOwner
	) : ReaderTransitionOutcome
}

enum class ReaderTransitionDeckRole {
	Initial,
	PageEntry,
	Settlement,
	Reflow,
	Recovery
}

enum class ReaderTransitionResourceKind {
	Deck,
	Raster,
	CallbackRegistration,
	FrameHandoff
}

enum class ReaderTransitionResourceProvenance {
	CoordinatorIssued,
	AdoptedLegacy
}

enum class ReaderTransitionCapabilityKind { Host, WebView, Renderer }

class ReaderAdoptedPredecessorSeedId private constructor(val value: Long) {
	init {
		require(value > 0L)
	}

	companion object {
		internal fun fromValidatedImport(value: Long): ReaderAdoptedPredecessorSeedId =
			ReaderAdoptedPredecessorSeedId(value)
	}

	override fun equals(other: Any?): Boolean =
		other is ReaderAdoptedPredecessorSeedId && value == other.value

	override fun hashCode(): Int = 0x52415053

	override fun toString(): String = "ReaderAdoptedPredecessorSeedId(<redacted>)"
}

sealed interface ReaderTransitionResourceOwnerId {
	data class TransitionOwned(
		val transitionId: ReaderTransitionId
	) : ReaderTransitionResourceOwnerId {
		override fun hashCode(): Int = 0x54524F57
		override fun toString(): String =
			"ReaderTransitionResourceOwnerId.TransitionOwned(<redacted>)"
	}

	data class AdoptedPredecessor(
		val seedId: ReaderAdoptedPredecessorSeedId
	) : ReaderTransitionResourceOwnerId {
		override fun hashCode(): Int = 0x41444F50
		override fun toString(): String =
			"ReaderTransitionResourceOwnerId.AdoptedPredecessor(<redacted>)"
	}
}

data class ReaderTransitionResourceKey(
	val ownerId: ReaderTransitionResourceOwnerId,
	val kind: ReaderTransitionResourceKind,
	val opaqueId: Long
) {
	constructor(
		transitionId: ReaderTransitionId,
		kind: ReaderTransitionResourceKind,
		opaqueId: Long
	) : this(ReaderTransitionResourceOwnerId.TransitionOwned(transitionId), kind, opaqueId)

	init {
		require(opaqueId > 0L)
	}

	fun copy(
		transitionId: ReaderTransitionId,
		kind: ReaderTransitionResourceKind = this.kind,
		opaqueId: Long = this.opaqueId
	): ReaderTransitionResourceKey = ReaderTransitionResourceKey(transitionId, kind, opaqueId)

	val owningTransitionIdOrNull: ReaderTransitionId?
		get() = when (val owner = ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned -> owner.transitionId
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> null
		}

	override fun hashCode(): Int = 0x5254524B

	override fun toString(): String = "ReaderTransitionResourceKey(<redacted>)"
}

data class ReaderResourceRetirementOrder(
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	val sequence: Long
) : Comparable<ReaderResourceRetirementOrder> {
	init {
		require(readerSessionGeneration > 0L)
		require(coordinatorEpoch > 0L)
		require(sequence > 0L)
	}

	override fun compareTo(other: ReaderResourceRetirementOrder): Int = compareValuesBy(
		this,
		other,
		ReaderResourceRetirementOrder::readerSessionGeneration,
		ReaderResourceRetirementOrder::coordinatorEpoch,
		ReaderResourceRetirementOrder::sequence
	)

	override fun hashCode(): Int = 0x52524F52

	override fun toString(): String = "ReaderResourceRetirementOrder(<redacted>)"
}

data class ReaderTransitionResourceRegistration(
	val key: ReaderTransitionResourceKey,
	val retirementOrder: ReaderResourceRetirementOrder
) {
	override fun hashCode(): Int = 0x52545247
	override fun toString(): String = "ReaderTransitionResourceRegistration(<redacted>)"
}

enum class ReaderTransitionProofKind {
	SemanticDestination,
	SettlementAcknowledgement,
	HostAvailable,
	PaginationProfile,
	RendererGeneration,
	MaterialBindingAllocation,
	Raster,
	DeckOwnership,
	DeckPrepared,
	FrameTargetPreparation,
	PreparedFrame,
	OwnerAndInputPublicationAcknowledgement,
	CoverPostDraw,
	WebViewExposure,
	Cancellation,
	ReleaseDrain
}

enum class ReaderTransitionSupersession {
	CancelAndRetainOwner,
	CancelAndReleaseOwner,
	RejectSuccessor
}

@JvmInline
value class ReaderTransitionGestureId(val value: Long) {
	init {
		require(value > 0L)
	}
}

sealed interface ReaderTransitionInputLease {
	data object None : ReaderTransitionInputLease
	data object ChromeOnly : ReaderTransitionInputLease
	data object CoverActions : ReaderTransitionInputLease
	data class NativePage(
		val binding: ReaderPresentationBinding,
		val textureGeneration: Long
	) : ReaderTransitionInputLease
	data class ClaimedGesture(
		val transitionId: ReaderTransitionId,
		val gestureId: ReaderTransitionGestureId
	) : ReaderTransitionInputLease
}

sealed interface ReaderInitialPresentationInputLease {
	data object None : ReaderInitialPresentationInputLease
	data object ChromeOnly : ReaderInitialPresentationInputLease
	data object CoverActions : ReaderInitialPresentationInputLease

	data class NativePage(
		val binding: ReaderPresentationBinding,
		val textureGeneration: Long
	) : ReaderInitialPresentationInputLease {
		init {
			require(textureGeneration >= 0L)
		}

		override fun hashCode(): Int = 0x494E504C

		override fun toString(): String =
			"ReaderInitialPresentationInputLease.NativePage(<redacted>)"
	}
}

sealed interface ReaderInitialCommittedPresentationOrigin {
	val readerSessionGeneration: Long
	val coordinatorEpoch: Long
	val requestedLease: ReaderInitialPresentationInputLease
	val physicalLease: ReaderInitialPresentationInputLease

	data class AdoptedPredecessor(
		val seedId: ReaderAdoptedPredecessorSeedId,
		override val readerSessionGeneration: Long,
		override val coordinatorEpoch: Long,
		val owner: ReaderPresentationFrameOwner,
		val binding: ReaderPresentationBinding,
		val resource: ReaderTransitionResourceRegistration,
		override val requestedLease: ReaderInitialPresentationInputLease,
		override val physicalLease: ReaderInitialPresentationInputLease,
		val provenance: ReaderTransitionResourceProvenance =
			ReaderTransitionResourceProvenance.AdoptedLegacy
	) : ReaderInitialCommittedPresentationOrigin {
		init {
			require(readerSessionGeneration > 0L)
			require(coordinatorEpoch > 0L)
			require(owner != ReaderPresentationFrameOwner.Neutral)
			require(owner.hasBinding(binding))
			require(resource.key.ownerId == ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId))
			require(resource.key.kind == owner.resourceKind())
			require(resource.retirementOrder.readerSessionGeneration == readerSessionGeneration)
			require(resource.retirementOrder.coordinatorEpoch == coordinatorEpoch)
			require(provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
			require(readerInitialLeaseIsNoBroaderThan(physicalLease, requestedLease))
			require(readerInitialLeaseIsCompatibleWithOwner(owner, binding, requestedLease))
			require(readerInitialLeaseIsCompatibleWithOwner(owner, binding, physicalLease))
		}

			override fun hashCode(): Int = 0x41444F52

		override fun toString(): String =
			"ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(<redacted>)"
	}

	data class Neutral(
		override val readerSessionGeneration: Long,
		override val coordinatorEpoch: Long,
		override val requestedLease: ReaderInitialPresentationInputLease,
		override val physicalLease: ReaderInitialPresentationInputLease
	) : ReaderInitialCommittedPresentationOrigin {
		init {
			require(readerSessionGeneration > 0L)
			require(coordinatorEpoch > 0L)
			require(requestedLease == ReaderInitialPresentationInputLease.None ||
				requestedLease == ReaderInitialPresentationInputLease.ChromeOnly)
			require(physicalLease == ReaderInitialPresentationInputLease.None ||
				physicalLease == ReaderInitialPresentationInputLease.ChromeOnly)
			require(readerInitialLeaseIsNoBroaderThan(physicalLease, requestedLease))
		}

		override fun hashCode(): Int = 0x4E455554

		override fun toString(): String =
			"ReaderInitialCommittedPresentationOrigin.Neutral(<redacted>)"
	}
}

enum class ReaderInitialOriginKind { AdoptedPredecessor, Neutral }

enum class ReaderInitialOriginOwnerKind { ShellCover, NativePage, Curl, LiveEngine }

enum class ReaderInitialOriginMismatchKind {
	Variant,
	Session,
	Epoch,
	Seed,
	Owner,
	Binding,
	ResourceOwner,
	ResourceKind,
	ResourceOpaqueIdentity,
	RetirementDomain,
	RetirementSequence,
	RequestedLease,
	PhysicalLease,
	Provenance
}

data class ReaderInitialOriginEqualityDiagnostic(
	val originKind: ReaderInitialOriginKind,
	val ownerKind: ReaderInitialOriginOwnerKind?,
	val resourceKind: ReaderTransitionResourceKind?,
	val mismatch: ReaderInitialOriginMismatchKind
)

fun ReaderInitialCommittedPresentationOrigin.equalityDiagnostic(
	other: ReaderInitialCommittedPresentationOrigin
): ReaderInitialOriginEqualityDiagnostic? {
	val kind = when (this) {
		is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor ->
			ReaderInitialOriginKind.AdoptedPredecessor
		is ReaderInitialCommittedPresentationOrigin.Neutral -> ReaderInitialOriginKind.Neutral
	}
	val adopted = this as? ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor
	val ownerKind = adopted?.owner?.initialOriginOwnerKind()
	val resourceKind = adopted?.resource?.key?.kind
	fun mismatch(category: ReaderInitialOriginMismatchKind) = ReaderInitialOriginEqualityDiagnostic(
		originKind = kind,
		ownerKind = ownerKind,
		resourceKind = resourceKind,
		mismatch = category
	)
	return when (this) {
		is ReaderInitialCommittedPresentationOrigin.Neutral -> {
			val actual = other as? ReaderInitialCommittedPresentationOrigin.Neutral
				?: return mismatch(ReaderInitialOriginMismatchKind.Variant)
			when {
				readerSessionGeneration != actual.readerSessionGeneration ->
					mismatch(ReaderInitialOriginMismatchKind.Session)
				coordinatorEpoch != actual.coordinatorEpoch ->
					mismatch(ReaderInitialOriginMismatchKind.Epoch)
				requestedLease != actual.requestedLease ->
					mismatch(ReaderInitialOriginMismatchKind.RequestedLease)
				physicalLease != actual.physicalLease ->
					mismatch(ReaderInitialOriginMismatchKind.PhysicalLease)
				else -> null
			}
		}
		is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> {
			val actual = other as? ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor
				?: return mismatch(ReaderInitialOriginMismatchKind.Variant)
			when {
				readerSessionGeneration != actual.readerSessionGeneration ->
					mismatch(ReaderInitialOriginMismatchKind.Session)
				coordinatorEpoch != actual.coordinatorEpoch ->
					mismatch(ReaderInitialOriginMismatchKind.Epoch)
				seedId != actual.seedId -> mismatch(ReaderInitialOriginMismatchKind.Seed)
				owner != actual.owner -> mismatch(ReaderInitialOriginMismatchKind.Owner)
				binding != actual.binding -> mismatch(ReaderInitialOriginMismatchKind.Binding)
				resource.key.ownerId != actual.resource.key.ownerId ->
					mismatch(ReaderInitialOriginMismatchKind.ResourceOwner)
				resource.key.kind != actual.resource.key.kind ->
					mismatch(ReaderInitialOriginMismatchKind.ResourceKind)
				resource.key.opaqueId != actual.resource.key.opaqueId ->
					mismatch(ReaderInitialOriginMismatchKind.ResourceOpaqueIdentity)
				resource.retirementOrder.readerSessionGeneration !=
					actual.resource.retirementOrder.readerSessionGeneration ||
					resource.retirementOrder.coordinatorEpoch !=
					actual.resource.retirementOrder.coordinatorEpoch ->
					mismatch(ReaderInitialOriginMismatchKind.RetirementDomain)
				resource.retirementOrder.sequence != actual.resource.retirementOrder.sequence ->
					mismatch(ReaderInitialOriginMismatchKind.RetirementSequence)
				requestedLease != actual.requestedLease ->
					mismatch(ReaderInitialOriginMismatchKind.RequestedLease)
				physicalLease != actual.physicalLease ->
					mismatch(ReaderInitialOriginMismatchKind.PhysicalLease)
				provenance != actual.provenance -> mismatch(ReaderInitialOriginMismatchKind.Provenance)
				else -> null
			}
		}
	}
}

private fun ReaderPresentationFrameOwner.initialOriginOwnerKind(): ReaderInitialOriginOwnerKind = when (this) {
	ReaderPresentationFrameOwner.Neutral -> error("Neutral cannot be an adopted initial owner")
	is ReaderPresentationFrameOwner.ShellCover -> ReaderInitialOriginOwnerKind.ShellCover
	is ReaderPresentationFrameOwner.NativePage -> ReaderInitialOriginOwnerKind.NativePage
	is ReaderPresentationFrameOwner.Curl -> ReaderInitialOriginOwnerKind.Curl
	is ReaderPresentationFrameOwner.LiveEngine -> ReaderInitialOriginOwnerKind.LiveEngine
}

enum class ReaderTransitionFailureReason {
	MaterialTimeout,
	CoverCommitTimeout,
	PageEntryTimeout,
	SettlementTimeout,
	LiveExposureTimeout,
	NativeHandbackTimeout,
	ExternalRelocationTimeout,
	ReflowTimeout,
	RestoreTimeout,
	RendererRecoveryTimeout,
	CloseDrainTimeout,
	ActivationPrerequisiteMissing,
	InventoryIncomplete,
	InvalidLegacyResource,
	LegacyDrainFailed,
	AmbiguousPredecessor,
	MaterialAllocationRejected,
	AtomicPublicationRejected,
	PortRejected,
	StaleProof
}

enum class ReaderTransitionRetryability { Retryable, NonRetryable }

enum class ReaderTransitionCancellationReason {
	Superseded,
	VisibilityLost,
	ReflowStarted,
	RendererLost,
	PublicationReplaced,
	PublicationClosed,
	UserCancelled
}

enum class ReaderReleaseOnlyCleanupTrigger {
	PublicationClose,
	PublicationReplacement,
	ProcessClose
}

enum class ReaderReleaseOnlyCancellationStatus {
	NotRequired,
	Pending,
	Applied,
	Rejected,
	ProcessClosedPending
}

enum class ReaderReleaseOnlyCleanupDeadlineStatus {
	Armed,
	CancelledAfterTerminalAccounting,
	Elapsed,
	BindingRejected,
	BindingThrew,
	CancellationRejected,
	CancellationThrew,
	ProcessClosed
}

@JvmInline
value class ReaderReleaseOnlyCleanupId internal constructor(val value: Long) {
	init { require(value > 0L) }
	override fun toString(): String = "ReaderReleaseOnlyCleanupId(<redacted>)"
}

@JvmInline
value class ReaderReleaseOnlyCleanupGeneration internal constructor(val value: Long) {
	init { require(value > 0L) }
	override fun toString(): String = "ReaderReleaseOnlyCleanupGeneration(<redacted>)"
}

data class ReaderReleaseOnlyCleanupKey(
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	val cleanupId: ReaderReleaseOnlyCleanupId,
	val generation: ReaderReleaseOnlyCleanupGeneration
) {
	init {
		require(readerSessionGeneration > 0L)
		require(coordinatorEpoch > 0L)
	}

	override fun hashCode(): Int = 0x524F434B
	override fun toString(): String = "ReaderReleaseOnlyCleanupKey(<redacted>)"
}

data class ReaderReleaseOnlySemanticAuthority(
	val binding: ReaderPresentationBinding
) {
	override fun hashCode(): Int = 0x524F5341
	override fun toString(): String = "ReaderReleaseOnlySemanticAuthority(<redacted>)"
}

data class ReaderReleaseOnlyCleanupState(
	val key: ReaderReleaseOnlyCleanupKey,
	val trigger: ReaderReleaseOnlyCleanupTrigger,
	val closeOperationId: ReaderTransitionId?,
	val preCloseTransitionId: ReaderTransitionId?,
	val authoritativeSemanticDestination: ReaderReleaseOnlySemanticAuthority? = null,
	val cancellationStatus: ReaderReleaseOnlyCancellationStatus,
	val deadlineStatus: ReaderReleaseOnlyCleanupDeadlineStatus
) {
	init {
		require(trigger != ReaderReleaseOnlyCleanupTrigger.PublicationReplacement || closeOperationId == null)
		require(closeOperationId == null || closeOperationId.operation == ReaderTransitionOperation.PublicationClose)
		require((preCloseTransitionId == null) ==
			(cancellationStatus == ReaderReleaseOnlyCancellationStatus.NotRequired))
		require(closeOperationId == null ||
			closeOperationId.readerSessionGeneration == key.readerSessionGeneration)
		require(closeOperationId == null || closeOperationId.coordinatorEpoch == key.coordinatorEpoch)
		require(preCloseTransitionId == null ||
			preCloseTransitionId.readerSessionGeneration == key.readerSessionGeneration)
		require(preCloseTransitionId == null ||
			preCloseTransitionId.coordinatorEpoch == key.coordinatorEpoch)
		require(closeOperationId == null || preCloseTransitionId == null ||
			closeOperationId != preCloseTransitionId)
	}

	override fun hashCode(): Int = 0x524F4353
	override fun toString(): String = "ReaderReleaseOnlyCleanupState(<redacted>)"
}

enum class ReaderTransitionDeferralReason {
	VisibilityRestore,
	HostUnavailable,
	WebViewUnavailable,
	PaginationUnavailable,
	RendererCapacityUnavailable
}

data class ReaderTransitionNonce(val high: Long, val low: Long)

data class ReaderTransitionResumeRecord(
	val operation: ReaderTransitionOperation,
	val reason: ReaderTransitionDeferralReason,
	val nonce: ReaderTransitionNonce,
	val issuedAtMillis: Long,
	val expiresAtMillis: Long,
	val remainingRestorations: Int,
	val requiredWake: ReaderTransitionWakeKind
) {
	init {
		require(expiresAtMillis > issuedAtMillis)
		require(remainingRestorations == 1)
	}
}

enum class ReaderExternalRelocationSource { Toc, Search, Bookmark, Annotation, Jump }

@JvmInline
value class ReaderSemanticRequestHandle(val value: Long) {
	init { require(value > 0L) }
}

internal enum class ReaderSemanticExecutableResult { Accepted, Rejected }

internal fun interface ReaderSemanticMutationStartEvidence {
	fun mutationStarted()
}

internal typealias ReaderSemanticExecutableRequest = (
	origin: ReaderPresentationEventOrigin.SemanticCommand,
	mutationStart: ReaderSemanticMutationStartEvidence,
	onReceipt: (ReaderPresentationEventReceipt) -> Unit
) -> ReaderSemanticExecutableResult

sealed interface ReaderTransitionUserIntent
sealed interface ReaderSemanticSynchronizationIntent : ReaderTransitionUserIntent {
	val requestHandle: ReaderSemanticRequestHandle
}

data class ReaderPageTurnIntent(
	val direction: ReaderPageTurnDirection,
	val gestureId: ReaderTransitionGestureId,
	override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data class ReaderExternalRelocationIntent(
	val source: ReaderExternalRelocationSource,
	override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data class ReaderCoverEntryIntent(
	override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data class ReaderBootstrapNativePageIntent(
	override val requestHandle: ReaderSemanticRequestHandle
) : ReaderSemanticSynchronizationIntent

data object ReaderCoverReturnIntent : ReaderTransitionUserIntent
data object ReaderRetryIntent : ReaderTransitionUserIntent
data object ReaderCancelIntent : ReaderTransitionUserIntent

internal const val ReaderMaximumPendingFrameTargets = 8

data class ReaderTransitionFrameTargetHandle(
	val readerSessionGeneration: Long,
	val publicationGeneration: Long,
	val opaqueId: Long
) {
	init {
		require(readerSessionGeneration > 0L)
		require(publicationGeneration > 0L)
		require(opaqueId > 0L)
	}
}

@JvmInline
value class ReaderShellCoverHostToken(val value: Long) {
	init { require(value > 0L) }
}

@JvmInline
value class ReaderNativePageHostToken(val value: Long) {
	init { require(value > 0L) }
}

sealed interface ReaderNativePageHostTokenState {
	data class Present(val token: ReaderNativePageHostToken) : ReaderNativePageHostTokenState
	data object AuthoritativeAbsent : ReaderNativePageHostTokenState
}

@JvmInline
value class ReaderLiveHandoffToken(val value: Long) {
	init { require(value > 0L) }
}

@JvmInline
value class ReaderLiveHandoffClaimIdentity(val value: Long) {
	init { require(value > 0L) }
}

data class ReaderTransitionFrameGeometry(
	val viewportGeneration: Long,
	val layoutProfileGeneration: Long,
	val targetLeftPx: Int,
	val targetTopPx: Int,
	val targetWidthPx: Int,
	val targetHeightPx: Int
) {
	init {
		require(viewportGeneration > 0L)
		require(layoutProfileGeneration > 0L)
		require(targetWidthPx > 0)
		require(targetHeightPx > 0)
	}
}

data class ReaderPlayLikeCurlDeckTargetIdentity(
	val rendererGeneration: Long,
	val deckGeneration: Long,
	val role: ReaderTransitionDeckRole
) {
	init {
		require(rendererGeneration > 0L)
		require(deckGeneration > 0L)
	}
}

enum class ReaderLiveHandoffDirection { NativeToLive, LiveToNative }

sealed interface ReaderTransitionFrameTargetSpecification {
	val transitionId: ReaderTransitionId
	val readerSessionGeneration: Long
	val publicationGeneration: Long
	val binding: ReaderPresentationBinding
	val geometry: ReaderTransitionFrameGeometry
	val requestSequence: Long

	data class ShellCover(
		override val transitionId: ReaderTransitionId,
		override val readerSessionGeneration: Long,
		override val publicationGeneration: Long,
		override val binding: ReaderPresentationBinding,
		val hostToken: ReaderShellCoverHostToken,
		val coverGeneration: Long,
		val viewportGeneration: Long,
		override val geometry: ReaderTransitionFrameGeometry,
		override val requestSequence: Long
	) : ReaderTransitionFrameTargetSpecification

	data class NativePage(
		override val transitionId: ReaderTransitionId,
		override val readerSessionGeneration: Long,
		override val publicationGeneration: Long,
		override val binding: ReaderPresentationBinding,
		val allocation: ReaderMaterialGenerationAllocation,
		val hostToken: ReaderNativePageHostTokenState,
		val deckTarget: ReaderPlayLikeCurlDeckTargetIdentity,
		override val geometry: ReaderTransitionFrameGeometry,
		override val requestSequence: Long
	) : ReaderTransitionFrameTargetSpecification

	data class CurlSettlementTerminalFrame(
		override val transitionId: ReaderTransitionId,
		override val readerSessionGeneration: Long,
		override val publicationGeneration: Long,
		override val binding: ReaderPresentationBinding,
		val allocation: ReaderMaterialGenerationAllocation,
		val gestureId: ReaderTransitionGestureId,
		val settlement: ReaderPageTurnSettlementAck,
		val deckTarget: ReaderPlayLikeCurlDeckTargetIdentity,
		override val geometry: ReaderTransitionFrameGeometry,
		override val requestSequence: Long
	) : ReaderTransitionFrameTargetSpecification

	data class LiveWebView(
		override val transitionId: ReaderTransitionId,
		override val readerSessionGeneration: Long,
		override val publicationGeneration: Long,
		override val binding: ReaderPresentationBinding,
		val handoffToken: ReaderLiveHandoffToken,
		val direction: ReaderLiveHandoffDirection,
		val claimIdentity: ReaderLiveHandoffClaimIdentity,
		val viewportGeneration: Long,
		override val geometry: ReaderTransitionFrameGeometry,
		override val requestSequence: Long
	) : ReaderTransitionFrameTargetSpecification
}

sealed interface ReaderTransitionFrameTarget {
	val handle: ReaderTransitionFrameTargetHandle
	val specification: ReaderTransitionFrameTargetSpecification
	val resource: ReaderTransitionResourceRegistration

	data class ShellCover(
		override val handle: ReaderTransitionFrameTargetHandle,
		override val specification: ReaderTransitionFrameTargetSpecification.ShellCover,
		override val resource: ReaderTransitionResourceRegistration
	) : ReaderTransitionFrameTarget

	data class NativePage(
		override val handle: ReaderTransitionFrameTargetHandle,
		override val specification: ReaderTransitionFrameTargetSpecification.NativePage,
		override val resource: ReaderTransitionResourceRegistration
	) : ReaderTransitionFrameTarget

	data class CurlSettlementTerminalFrame(
		override val handle: ReaderTransitionFrameTargetHandle,
		override val specification: ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame,
		override val resource: ReaderTransitionResourceRegistration
	) : ReaderTransitionFrameTarget

	data class LiveWebView(
		override val handle: ReaderTransitionFrameTargetHandle,
		override val specification: ReaderTransitionFrameTargetSpecification.LiveWebView,
		override val resource: ReaderTransitionResourceRegistration
	) : ReaderTransitionFrameTarget
}

@JvmInline
value class ReaderOwnerAndInputPublicationIdentity(val value: Long) {
	init { require(value > 0L) }
}

sealed interface ReaderOwnerAndInputPublicationSubject {
	data class Successor(
		val targetHandle: ReaderTransitionFrameTargetHandle,
		val preparedFrameResource: ReaderTransitionResourceRegistration
	) : ReaderOwnerAndInputPublicationSubject

	data class Retained(
		val retainedResource: ReaderTransitionResourceRegistration
	) : ReaderOwnerAndInputPublicationSubject
}

sealed interface ReaderOwnerAndInputPublicationResult {
	data class Applied(
		val transitionId: ReaderTransitionId,
		val subject: ReaderOwnerAndInputPublicationSubject,
		val publishedOwner: ReaderPresentationFrameOwner,
		val publishedBinding: ReaderPresentationBinding,
		val finalPhysicalInputLease: ReaderTransitionInputLease,
		val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
	) : ReaderOwnerAndInputPublicationResult

	data class Rejected(
		val transitionId: ReaderTransitionId,
		val subject: ReaderOwnerAndInputPublicationSubject,
		val publicationIdentity: ReaderOwnerAndInputPublicationIdentity,
		val reason: ReaderTransitionFailureReason
	) : ReaderOwnerAndInputPublicationResult
}

data class ReaderMaterialGenerationAllocation(
	val transitionId: ReaderTransitionId,
	val allocatedBinding: ReaderPresentationBinding,
	val preparationGeneration: Long,
	val rasterGeneration: Long,
	val textureGeneration: Long
) {
	init {
		require(preparationGeneration > 0L)
		require(rasterGeneration > 0L)
		require(textureGeneration > 0L)
		require(allocatedBinding.preparationGeneration == preparationGeneration)
		require(allocatedBinding.rasterGeneration == rasterGeneration)
		require(allocatedBinding.textureGeneration == textureGeneration)
	}
}

sealed interface ReaderTransitionFact {
	val transitionId: ReaderTransitionId?

	data class Intent(
		override val transitionId: ReaderTransitionId?,
		val intent: ReaderTransitionUserIntent
	) : ReaderTransitionFact

	data class FoliateDestinationCommitted(
		override val transitionId: ReaderTransitionId?,
		val binding: ReaderPresentationBinding
	) : ReaderTransitionFact

	data class SettlementAcknowledged(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val acknowledgement: ReaderPageTurnSettlementAck
	) : ReaderTransitionFact

	data class MaterialBindingAllocated(
		override val transitionId: ReaderTransitionId,
		val allocation: ReaderMaterialGenerationAllocation
	) : ReaderTransitionFact {
		init {
			require(allocation.transitionId == transitionId)
		}
	}

	data class ViewportProfileReplaced(
		override val transitionId: ReaderTransitionId?,
		val profileGeneration: Long
	) : ReaderTransitionFact

	data class RasterProgress(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
	data class RasterProven(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
	data class RasterDeferred(
		override val transitionId: ReaderTransitionId,
		val reason: ReaderTransitionDeferralReason,
		val resumeRecord: ReaderTransitionResumeRecord
	) : ReaderTransitionFact {
		init {
			val liveness = ReaderTransitionLivenessTable.forOperation(transitionId.operation)
			require(ReaderTransitionProofKind.Raster in liveness.requiredProofs)
			require(resumeRecord.operation == transitionId.operation)
			require(resumeRecord.reason == reason)
			require(resumeRecord.requiredWake == reason.wakeKind())
			require(resumeRecord.requiredWake in liveness.wakeKinds)
		}
	}
	data class RasterFailed(
		override val transitionId: ReaderTransitionId,
		val reason: ReaderTransitionFailureReason
	) : ReaderTransitionFact

	data class ResourceObserved(
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
	) : ReaderTransitionFact
	data class DeckReserved(
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
	) : ReaderTransitionFact
	data class DeckOwned(
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
	) : ReaderTransitionFact
	data class DeckPrepared(
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
	) : ReaderTransitionFact
	data class DeckRejected(
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
	) : ReaderTransitionFact
	data class ResourceReleased(
		override val transitionId: ReaderTransitionId?,
		val key: ReaderTransitionResourceKey,
		val registration: ReaderTransitionResourceRegistration? = null
	) : ReaderTransitionFact
	data class RendererCapacityAvailable(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
	data class HostAvailable(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
	data class PaginationProfileReady(
		override val transitionId: ReaderTransitionId,
		val profileGeneration: Long
	) : ReaderTransitionFact {
		init {
			require(profileGeneration > 0L)
		}
	}
	data class RendererGenerationReady(
		override val transitionId: ReaderTransitionId,
		val rendererGeneration: Long
	) : ReaderTransitionFact {
		init {
			require(rendererGeneration > 0L)
		}
	}

	data class FrameTargetPrepared(
		override val transitionId: ReaderTransitionId,
		val target: ReaderTransitionFrameTarget
	) : ReaderTransitionFact {
		init {
			require(target.specification.transitionId == transitionId)
			require(target.specification.readerSessionGeneration == transitionId.readerSessionGeneration)
			require(target.specification.publicationGeneration ==
				target.specification.binding.publicationGeneration)
			require(target.handle.readerSessionGeneration == transitionId.readerSessionGeneration)
			require(target.handle.publicationGeneration == target.specification.publicationGeneration)
			require(target.resource.key.ownerId == ReaderTransitionResourceOwnerId.TransitionOwned(transitionId))
			require(target.resource.key.kind == target.requiredResourceKind())
		}
	}

	data class FrameTargetPreparationRejected(
		override val transitionId: ReaderTransitionId,
		val specification: ReaderTransitionFrameTargetSpecification,
		val resource: ReaderTransitionResourceRegistration,
		val reason: ReaderTransitionFailureReason
	) : ReaderTransitionFact

	data class PreparedFrame(
		override val transitionId: ReaderTransitionId,
		val target: ReaderTransitionFrameTarget,
		val frameOwner: ReaderPresentationFrameOwner,
		val resource: ReaderTransitionResourceRegistration
	) : ReaderTransitionFact {
		init {
			require(target.specification.transitionId == transitionId)
			require(resource == target.resource)
			require(frameOwner.hasBinding(target.specification.binding))
			require(resource.key.kind == frameOwner.resourceKind())
		}
	}

	data class OwnerAndInputPublicationApplied(
		override val transitionId: ReaderTransitionId,
		val subject: ReaderOwnerAndInputPublicationSubject,
		val publishedOwner: ReaderPresentationFrameOwner,
		val publishedBinding: ReaderPresentationBinding,
		val finalPhysicalInputLease: ReaderTransitionInputLease,
		val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
	) : ReaderTransitionFact

	data class OwnerAndInputPublicationRejected(
		override val transitionId: ReaderTransitionId,
		val subject: ReaderOwnerAndInputPublicationSubject,
		val publicationIdentity: ReaderOwnerAndInputPublicationIdentity,
		val reason: ReaderTransitionFailureReason
	) : ReaderTransitionFact
	data class CoverPostDraw(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val frameOwner: ReaderPresentationFrameOwner,
		val resourceKey: ReaderTransitionResourceKey
	) : ReaderTransitionFact {
		init {
			require(frameOwner is ReaderPresentationFrameOwner.ShellCover)
			require(frameOwner.hasBinding(binding))
			require(resourceKey.owningTransitionIdOrNull == transitionId)
			require(resourceKey.kind == ReaderTransitionResourceKind.FrameHandoff)
		}
	}
	data class WebViewExposure(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val frameOwner: ReaderPresentationFrameOwner,
		val resourceKey: ReaderTransitionResourceKey
	) : ReaderTransitionFact {
		init {
			require(frameOwner is ReaderPresentationFrameOwner.LiveEngine)
			require(frameOwner.hasBinding(binding))
			require(resourceKey.owningTransitionIdOrNull == transitionId)
			require(resourceKey.kind == ReaderTransitionResourceKind.FrameHandoff)
		}
	}

	data class VisibilityChanged(
		override val transitionId: ReaderTransitionId?,
		val visible: Boolean
	) : ReaderTransitionFact
	data class ResourceLost(
		override val transitionId: ReaderTransitionId?,
		val kind: ReaderTransitionCapabilityKind
	) : ReaderTransitionFact
	data class DeadlineExpired(override val transitionId: ReaderTransitionId) : ReaderTransitionFact
	data class CommandRejected(
		override val transitionId: ReaderTransitionId,
		val stage: ReaderTransitionCommandStage,
		val reason: ReaderTransitionCommandRejectionReason
	) : ReaderTransitionFact {
		init { require(reason.isCompatibleWith(stage)) }
	}
	data class SemanticPortContractViolated(
		override val transitionId: ReaderTransitionId,
		val reason: ReaderSemanticPortContractViolationReason,
		val callbackCount: ReaderSaturatingCallbackCount
	) : ReaderTransitionFact {
		override fun hashCode(): Int = 0x53504356
		override fun toString(): String =
			"ReaderTransitionFact.SemanticPortContractViolated(<redacted>)"
	}
	data class Retry(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
	data class PublicationReplaced(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
	data class PublicationClosed(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
}

sealed interface ReaderResourceReleaseIssuer {
	data class Transition(
		val transitionId: ReaderTransitionId
	) : ReaderResourceReleaseIssuer {
		override fun hashCode(): Int = 0x5252544E
		override fun toString(): String = "ReaderResourceReleaseIssuer.Transition(<redacted>)"
	}

	data class Session(
		val readerSessionGeneration: Long,
		val coordinatorEpoch: Long
	) : ReaderResourceReleaseIssuer {
		init {
			require(readerSessionGeneration > 0L)
			require(coordinatorEpoch > 0L)
		}

		override fun hashCode(): Int = 0x52525353
		override fun toString(): String = "ReaderResourceReleaseIssuer.Session(<redacted>)"
	}
}

sealed interface ReaderTransitionCommand {
	val transitionId: ReaderTransitionId?

	data class RequestSemanticSynchronization(
		override val transitionId: ReaderTransitionId,
		val intent: ReaderSemanticSynchronizationIntent,
		val requestHandle: ReaderSemanticRequestHandle
	) : ReaderTransitionCommand {
		init {
			require(requestHandle == intent.requestHandle)
		}

		override fun hashCode(): Int = 0x53454D43
		override fun toString(): String =
			"ReaderTransitionCommand.RequestSemanticSynchronization(<redacted>)"
	}

	data class AllocateMaterialBinding(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding
	) : ReaderTransitionCommand

	data class RequestRasterPreparation(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val allocation: ReaderMaterialGenerationAllocation? = null
	) : ReaderTransitionCommand

	data class ReserveDeck(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val role: ReaderTransitionDeckRole,
		val allocation: ReaderMaterialGenerationAllocation? = null
	) : ReaderTransitionCommand

	data class PrepareFrameTarget(
		override val transitionId: ReaderTransitionId,
		val specification: ReaderTransitionFrameTargetSpecification,
		val registration: ReaderTransitionResourceRegistration
	) : ReaderTransitionCommand {
		init {
			require(specification.transitionId == transitionId)
			require(registration.key.ownerId == ReaderTransitionResourceOwnerId.TransitionOwned(transitionId))
			require(registration.key.kind == specification.requiredResourceKind())
		}
	}

	data class RequestFramePresentation(
		override val transitionId: ReaderTransitionId,
		val target: ReaderTransitionFrameTarget
	) : ReaderTransitionCommand {
		init {
			require(target.specification.transitionId == transitionId)
		}
	}

	data class CommitOwnerAndInputLease(
		override val transitionId: ReaderTransitionId,
		val targetHandle: ReaderTransitionFrameTargetHandle,
		val owner: ReaderPresentationFrameOwner,
		val binding: ReaderPresentationBinding,
		val preparedFrameResource: ReaderTransitionResourceRegistration,
		val requestedLease: ReaderTransitionInputLease,
		val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
	) : ReaderTransitionCommand {
		init {
			require(preparedFrameResource.key.ownerId == ReaderTransitionResourceOwnerId.TransitionOwned(transitionId))
			require(owner.hasBinding(binding))
			require(targetHandle.readerSessionGeneration == transitionId.readerSessionGeneration)
			require(targetHandle.publicationGeneration == binding.publicationGeneration)
		}

		override fun hashCode(): Int = 0x434F4D50
		override fun toString(): String =
			"ReaderTransitionCommand.CommitOwnerAndInputLease(<redacted>)"
	}

	data class PublishRetainedOwnerAndInputLease(
		override val transitionId: ReaderTransitionId,
		val retainedOwner: ReaderPresentationFrameOwner,
		val retainedBinding: ReaderPresentationBinding,
		val retainedResource: ReaderTransitionResourceRegistration,
		val requestedLease: ReaderTransitionInputLease,
		val publicationIdentity: ReaderOwnerAndInputPublicationIdentity
	) : ReaderTransitionCommand {
		init {
			require(retainedOwner.hasBinding(retainedBinding))
			require(retainedResource.key.kind == retainedOwner.resourceKind())
		}

		override fun hashCode(): Int = 0x52455450
		override fun toString(): String =
			"ReaderTransitionCommand.PublishRetainedOwnerAndInputLease(<redacted>)"
	}

	data class ReleaseResource(
		val issuer: ReaderResourceReleaseIssuer,
		val key: ReaderTransitionResourceKey,
		val registration: ReaderTransitionResourceRegistration? = null
	) : ReaderTransitionCommand {
		override val transitionId: ReaderTransitionId?
		get() = when (val authority = issuer) {
			is ReaderResourceReleaseIssuer.Transition -> authority.transitionId
			is ReaderResourceReleaseIssuer.Session -> null
		}

		val issuerTransitionId: ReaderTransitionId?
			get() = transitionId

		init {
			when (val authority = issuer) {
				is ReaderResourceReleaseIssuer.Transition -> {
					val owner = key.ownerId as? ReaderTransitionResourceOwnerId.TransitionOwned
						?: error("Transition release cannot issue for an adopted resource")
					require(
						owner.transitionId.readerSessionGeneration == authority.transitionId.readerSessionGeneration &&
							owner.transitionId.coordinatorEpoch == authority.transitionId.coordinatorEpoch
					)
				}
				is ReaderResourceReleaseIssuer.Session -> {
					require(key.ownerId is ReaderTransitionResourceOwnerId.AdoptedPredecessor)
					val imported = requireNotNull(registration)
					require(imported.key == key)
					require(imported.retirementOrder.readerSessionGeneration == authority.readerSessionGeneration)
					require(imported.retirementOrder.coordinatorEpoch == authority.coordinatorEpoch)
				}
			}
			require(registration == null || registration.key == key)
		}

		constructor(
			issuerTransitionId: ReaderTransitionId?,
			key: ReaderTransitionResourceKey,
			registration: ReaderTransitionResourceRegistration? = null
		) : this(
			issuer = issuerTransitionId?.let { ReaderResourceReleaseIssuer.Transition(it) }
				?: requireNotNull(registration).retirementOrder.let {
					ReaderResourceReleaseIssuer.Session(
						readerSessionGeneration = it.readerSessionGeneration,
						coordinatorEpoch = it.coordinatorEpoch
					)
				},
			key = key,
			registration = registration
		)

		constructor(registration: ReaderTransitionResourceRegistration) : this(
			issuer = when (val owner = registration.key.ownerId) {
				is ReaderTransitionResourceOwnerId.TransitionOwned ->
					ReaderResourceReleaseIssuer.Transition(owner.transitionId)
				is ReaderTransitionResourceOwnerId.AdoptedPredecessor ->
					ReaderResourceReleaseIssuer.Session(
						registration.retirementOrder.readerSessionGeneration,
						registration.retirementOrder.coordinatorEpoch
					)
			},
			key = registration.key,
			registration = registration
		)

		override fun hashCode(): Int = 0x52454C53
		override fun toString(): String = "ReaderTransitionCommand.ReleaseResource(<redacted>)"
	}

	data class CancelOwnedWork(
		override val transitionId: ReaderTransitionId,
		val cleanupKey: ReaderReleaseOnlyCleanupKey? = null
	) : ReaderTransitionCommand {
		override fun hashCode(): Int = 0x434F574B
		override fun toString(): String = "ReaderTransitionCommand.CancelOwnedWork(<redacted>)"
	}
}

data class ReaderTransitionLiveness(
	val requiredProofs: Set<ReaderTransitionProofKind>,
	val deadlinePolicy: ReaderTransitionDeadlinePolicy,
	val timeoutFailureReason: ReaderTransitionFailureReason,
	val timeoutRetryability: ReaderTransitionRetryability,
	val supersession: ReaderTransitionSupersession,
	val wakeKinds: Set<ReaderTransitionWakeKind>,
	val callbackSources: Set<ReaderTransitionFactKind>
) {
	init {
		require(requiredProofs.isNotEmpty())
		require(callbackSources.isNotEmpty())
	}
}

private fun admissibleStagesFor(
	physicalStage: ReaderTransitionCommandStage
): Set<ReaderTransitionCommandStage> = if (
	physicalStage == ReaderTransitionCommandStage.RetainedPublication
) {
	setOf(physicalStage)
} else {
	setOf(physicalStage, ReaderTransitionCommandStage.TimerBinding)
}

object ReaderTransitionLivenessTable {
	fun forOperation(operation: ReaderTransitionOperation): ReaderTransitionLiveness = when (operation) {
		ReaderTransitionOperation.BootstrapNativePage -> materialLiveness(
			operation,
			ReaderTransitionFailureReason.MaterialTimeout,
			setOf(ReaderTransitionWakeKind.Retry),
			extraProofs = setOf(ReaderTransitionProofKind.SemanticDestination),
			extraSources = setOf(ReaderTransitionFactKind.FoliateDestinationCommitted)
		)
		ReaderTransitionOperation.ShellCoverCommit -> liveness(
			operation = operation,
			proofs = setOf(ReaderTransitionProofKind.CoverPostDraw),
			timeout = ReaderTransitionFailureReason.CoverCommitTimeout,
			wakes = setOf(ReaderTransitionWakeKind.HostAvailable, ReaderTransitionWakeKind.Retry),
			sources = setOf(
				ReaderTransitionFactKind.CoverPostDraw,
				ReaderTransitionFactKind.ResourceLost,
				ReaderTransitionFactKind.DeadlineExpired
			)
		)
		ReaderTransitionOperation.CoverToPageEntry -> materialLiveness(
			operation,
			ReaderTransitionFailureReason.PageEntryTimeout,
			setOf(
				ReaderTransitionWakeKind.RasterProofAvailable,
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.Retry
			)
		)
		ReaderTransitionOperation.CurlClaimAndSettlement -> liveness(
			operation = operation,
			proofs = setOf(
				ReaderTransitionProofKind.SettlementAcknowledgement,
				ReaderTransitionProofKind.PreparedFrame
			),
			timeout = ReaderTransitionFailureReason.SettlementTimeout,
			wakes = setOf(
				ReaderTransitionWakeKind.FoliateDestinationCommitted,
				ReaderTransitionWakeKind.Retry
			),
			sources = setOf(
				ReaderTransitionFactKind.FoliateDestinationCommitted,
				ReaderTransitionFactKind.SettlementAcknowledged,
				ReaderTransitionFactKind.PreparedFrame,
				ReaderTransitionFactKind.ResourceLost,
				ReaderTransitionFactKind.DeadlineExpired
			)
		)
		ReaderTransitionOperation.NativeToLiveHandoff -> liveness(
			operation = operation,
			proofs = setOf(ReaderTransitionProofKind.WebViewExposure),
			timeout = ReaderTransitionFailureReason.LiveExposureTimeout,
			wakes = setOf(ReaderTransitionWakeKind.WebViewAvailable, ReaderTransitionWakeKind.Retry),
			sources = setOf(
				ReaderTransitionFactKind.WebViewExposure,
				ReaderTransitionFactKind.ResourceLost,
				ReaderTransitionFactKind.DeadlineExpired
			)
		)
		ReaderTransitionOperation.LiveToNativeHandback -> liveness(
			operation = operation,
			proofs = setOf(ReaderTransitionProofKind.PreparedFrame),
			timeout = ReaderTransitionFailureReason.NativeHandbackTimeout,
			wakes = setOf(
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.Retry
			),
			sources = frameCallbackSources
		)
		ReaderTransitionOperation.ExternalSemanticRelocation -> materialLiveness(
			operation,
			ReaderTransitionFailureReason.ExternalRelocationTimeout,
			setOf(
				ReaderTransitionWakeKind.FoliateDestinationCommitted,
				ReaderTransitionWakeKind.RasterProofAvailable,
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.Retry
			),
			extraProofs = setOf(ReaderTransitionProofKind.SemanticDestination),
			extraSources = setOf(ReaderTransitionFactKind.FoliateDestinationCommitted)
		)
		ReaderTransitionOperation.ReflowProfileReplacement -> materialLiveness(
			operation,
			ReaderTransitionFailureReason.ReflowTimeout,
			setOf(
				ReaderTransitionWakeKind.PaginationProfileReady,
				ReaderTransitionWakeKind.RasterProofAvailable,
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.Retry
			),
			extraSources = setOf(ReaderTransitionFactKind.ViewportProfileReplaced)
		)
		ReaderTransitionOperation.VisibilityRestore -> materialLiveness(
			operation,
			ReaderTransitionFailureReason.RestoreTimeout,
			setOf(
				ReaderTransitionWakeKind.HostAvailable,
				ReaderTransitionWakeKind.WebViewAvailable,
				ReaderTransitionWakeKind.PaginationProfileReady,
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.Retry
			),
			extraProofs = setOf(
				ReaderTransitionProofKind.HostAvailable,
				ReaderTransitionProofKind.SemanticDestination,
				ReaderTransitionProofKind.PaginationProfile
			),
			extraSources = setOf(
				ReaderTransitionFactKind.HostAvailable,
				ReaderTransitionFactKind.FoliateDestinationCommitted,
				ReaderTransitionFactKind.PaginationProfileReady
			)
		)
		ReaderTransitionOperation.RendererRecovery -> liveness(
			operation = operation,
			proofs = setOf(
				ReaderTransitionProofKind.RendererGeneration,
				ReaderTransitionProofKind.MaterialBindingAllocation,
				ReaderTransitionProofKind.DeckOwnership,
				ReaderTransitionProofKind.DeckPrepared,
				ReaderTransitionProofKind.PreparedFrame
			),
			timeout = ReaderTransitionFailureReason.RendererRecoveryTimeout,
			wakes = setOf(
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.Retry
			),
			sources = setOf(
				ReaderTransitionFactKind.MaterialBindingAllocated,
				ReaderTransitionFactKind.RendererGenerationReady,
				ReaderTransitionFactKind.DeckReserved,
				ReaderTransitionFactKind.DeckOwned,
				ReaderTransitionFactKind.DeckPrepared,
				ReaderTransitionFactKind.DeckRejected,
				ReaderTransitionFactKind.RendererCapacityAvailable,
				ReaderTransitionFactKind.PreparedFrame,
				ReaderTransitionFactKind.ResourceLost,
				ReaderTransitionFactKind.DeadlineExpired
			)
		)
		ReaderTransitionOperation.PublicationClose -> liveness(
			operation = operation,
			proofs = setOf(
				ReaderTransitionProofKind.Cancellation,
				ReaderTransitionProofKind.ReleaseDrain
			),
			timeout = ReaderTransitionFailureReason.CloseDrainTimeout,
			wakes = emptySet(),
			sources = setOf(
				ReaderTransitionFactKind.ResourceReleased,
				ReaderTransitionFactKind.PublicationClosed,
				ReaderTransitionFactKind.DeadlineExpired
			),
			retryability = ReaderTransitionRetryability.NonRetryable,
			supersession = ReaderTransitionSupersession.RejectSuccessor
		)
	}

	fun phase(
		id: ReaderTransitionId,
		kind: ReaderTransitionPhaseKind,
		retainedOwner: ReaderPresentationFrameOwner,
		satisfiedProofs: Set<ReaderTransitionProofKind> = emptySet(),
		gestureId: ReaderTransitionGestureId? = null
	): ReaderTransitionPhase {
		val liveness = forOperation(id.operation)
		val awaitedProofs = liveness.requiredProofs - satisfiedProofs
		require(awaitedProofs.isNotEmpty())
		val admissibleCommandStages = when {
			id.operation == ReaderTransitionOperation.PublicationClose ->
				setOf(ReaderTransitionCommandStage.TimerBinding)
			ReaderTransitionProofKind.SemanticDestination in satisfiedProofs ->
				admissibleStagesFor(ReaderTransitionCommandStage.MaterialAllocation)
			else -> admissibleStagesFor(ReaderTransitionCommandStage.SemanticSynchronization)
		}
		return ReaderTransitionPhase(
			kind = kind,
			contract = ReaderTransitionPhaseContract(
				awaitedProofs = awaitedProofs,
				deadlineOwner = id,
				supersession = liveness.supersession,
				retainedOwner = retainedOwner,
				inputLease = inputLeaseFor(id, gestureId),
				callbackSources = if (admissibleCommandStages.isEmpty()) {
					liveness.callbackSources
				} else {
					liveness.callbackSources + ReaderTransitionFactKind.CommandRejected
				},
				admissibleCommandStages = admissibleCommandStages
			)
		)
	}

	fun phases(
		id: ReaderTransitionId,
		retainedOwner: ReaderPresentationFrameOwner,
		gestureId: ReaderTransitionGestureId? = null
	): List<ReaderTransitionPhase> = ReaderTransitionPhaseKind.entries.map { kind ->
		phase(id, kind, retainedOwner, gestureId = gestureId)
	}

	private fun inputLeaseFor(
		id: ReaderTransitionId,
		gestureId: ReaderTransitionGestureId?
	): ReaderTransitionInputLease = when (id.operation) {
		ReaderTransitionOperation.CurlClaimAndSettlement -> gestureId?.let { gesture ->
			ReaderTransitionInputLease.ClaimedGesture(id, gesture)
		} ?: ReaderTransitionInputLease.ChromeOnly
		ReaderTransitionOperation.PublicationClose -> ReaderTransitionInputLease.None
		else -> ReaderTransitionInputLease.ChromeOnly
	}

	private fun materialLiveness(
		operation: ReaderTransitionOperation,
		timeout: ReaderTransitionFailureReason,
		wakes: Set<ReaderTransitionWakeKind>,
		extraProofs: Set<ReaderTransitionProofKind> = emptySet(),
		extraSources: Set<ReaderTransitionFactKind> = emptySet()
	): ReaderTransitionLiveness = liveness(
		operation = operation,
		proofs = materialProofs + extraProofs,
		timeout = timeout,
		wakes = wakes,
		sources = materialCallbackSources + extraSources
	)

	private fun liveness(
		operation: ReaderTransitionOperation,
		proofs: Set<ReaderTransitionProofKind>,
		timeout: ReaderTransitionFailureReason,
		wakes: Set<ReaderTransitionWakeKind>,
		sources: Set<ReaderTransitionFactKind>,
		retryability: ReaderTransitionRetryability = ReaderTransitionRetryability.Retryable,
		supersession: ReaderTransitionSupersession = ReaderTransitionSupersession.CancelAndRetainOwner
	) = ReaderTransitionLiveness(
		requiredProofs = proofs,
		deadlinePolicy = operation.deadlinePolicy(),
		timeoutFailureReason = timeout,
		timeoutRetryability = retryability,
		supersession = supersession,
		wakeKinds = wakes,
		callbackSources = sources
	)

	private val materialProofs = setOf(
		ReaderTransitionProofKind.MaterialBindingAllocation,
		ReaderTransitionProofKind.Raster,
		ReaderTransitionProofKind.DeckOwnership,
		ReaderTransitionProofKind.DeckPrepared,
		ReaderTransitionProofKind.PreparedFrame
	)

	private val materialCallbackSources = setOf(
		ReaderTransitionFactKind.MaterialBindingAllocated,
		ReaderTransitionFactKind.RasterProgress,
		ReaderTransitionFactKind.RasterProven,
		ReaderTransitionFactKind.RasterDeferred,
		ReaderTransitionFactKind.RasterFailed,
		ReaderTransitionFactKind.DeckReserved,
		ReaderTransitionFactKind.DeckOwned,
		ReaderTransitionFactKind.DeckPrepared,
		ReaderTransitionFactKind.DeckRejected,
		ReaderTransitionFactKind.RendererCapacityAvailable,
		ReaderTransitionFactKind.PreparedFrame,
		ReaderTransitionFactKind.ResourceLost,
		ReaderTransitionFactKind.DeadlineExpired
	)

	private val frameCallbackSources = setOf(
		ReaderTransitionFactKind.PreparedFrame,
		ReaderTransitionFactKind.ResourceLost,
		ReaderTransitionFactKind.DeadlineExpired
	)
}

data class ReaderActiveTransition(
	val id: ReaderTransitionId,
	val phase: ReaderTransitionPhase,
	val pendingCommandStages: Set<ReaderTransitionCommandStage> = emptySet(),
	val preparedFrameOwner: ReaderPresentationFrameOwner? = null,
	val resolvedSuccessorBinding: ReaderPresentationBinding? = null,
	val predecessorResourceKey: ReaderTransitionResourceKey? = null,
	val predecessorResourceRegistration: ReaderTransitionResourceRegistration? = null,
	val ownedResourceKeys: Set<ReaderTransitionResourceKey> = emptySet(),
	val admittedDeckKey: ReaderTransitionResourceKey? = null,
	val pendingPreparedDeckKey: ReaderTransitionResourceKey? = null,
	val pendingFrameTargetSpecification: ReaderTransitionFrameTargetSpecification? = null,
	val pendingFrameTargetRegistration: ReaderTransitionResourceRegistration? = null,
	val frameTarget: ReaderTransitionFrameTarget? = null,
	val successorResourceKey: ReaderTransitionResourceKey? = null,
	val successorResourceRegistration: ReaderTransitionResourceRegistration? = null,
	val pendingPublicationIdentity: ReaderOwnerAndInputPublicationIdentity? = null,
	val consumedSettlement: ReaderSettlementConsumptionKey? = null,
	val semanticIntent: ReaderSemanticSynchronizationIntent? = null,
	val materialAllocation: ReaderMaterialGenerationAllocation? = null,
	val authoritativeDestinationCommitted: Boolean = false
) {
	init {
		require(pendingCommandStages.size <= 1)
		require(pendingCommandStages.all { it in phase.contract.admissibleCommandStages })
		require(pendingCommandStages.isEmpty() ||
			ReaderTransitionFactKind.CommandRejected in phase.contract.callbackSources)
		require(predecessorResourceKey?.owningTransitionIdOrNull != id)
		require(predecessorResourceRegistration == null ||
			predecessorResourceRegistration.key == predecessorResourceKey)
		require(ownedResourceKeys.all { it.owningTransitionIdOrNull == id })
		require(admittedDeckKey == null || admittedDeckKey.owningTransitionIdOrNull == id)
		require(pendingPreparedDeckKey == null || pendingPreparedDeckKey.kind == ReaderTransitionResourceKind.Deck)
		require(pendingFrameTargetSpecification == null || pendingFrameTargetSpecification.transitionId == id)
		require(pendingFrameTargetRegistration == null ||
			pendingFrameTargetRegistration.key.ownerId == ReaderTransitionResourceOwnerId.TransitionOwned(id))
		require(frameTarget == null || frameTarget.specification.transitionId == id)
		require(successorResourceKey == null || successorResourceKey.owningTransitionIdOrNull == id)
		require(successorResourceRegistration == null || successorResourceRegistration.key == successorResourceKey)
		require(consumedSettlement == null || consumedSettlement.transitionId == id)
		require(materialAllocation == null || materialAllocation.transitionId == id)
		require(!authoritativeDestinationCommitted || resolvedSuccessorBinding != null)
	}
}

data class ReaderCommittedTransition(
	val id: ReaderTransitionId,
	val owner: ReaderPresentationFrameOwner,
	val binding: ReaderPresentationBinding,
	val resourceKey: ReaderTransitionResourceKey,
	val resourceRegistration: ReaderTransitionResourceRegistration
) {
	init {
		require(owner.hasBinding(binding))
		require(resourceKey.owningTransitionIdOrNull == id)
		require(resourceKey.kind == owner.resourceKind())
		require(resourceRegistration.key == resourceKey)
	}

	override fun hashCode(): Int = 0x434F4D54
	override fun toString(): String = "ReaderCommittedTransition(<redacted>)"
}

sealed interface ReaderCommittedPresentation {
	val readerSessionGeneration: Long
	val coordinatorEpoch: Long

	data class Initial(
		val origin: ReaderInitialCommittedPresentationOrigin
	) : ReaderCommittedPresentation {
		override val readerSessionGeneration: Long = origin.readerSessionGeneration
		override val coordinatorEpoch: Long = origin.coordinatorEpoch

		override fun hashCode(): Int = 0x494E4954
		override fun toString(): String = "ReaderCommittedPresentation.Initial(<redacted>)"
	}

	data class Transition(
		val committed: ReaderCommittedTransition
	) : ReaderCommittedPresentation {
		override val readerSessionGeneration: Long = committed.id.readerSessionGeneration
		override val coordinatorEpoch: Long = committed.id.coordinatorEpoch

		override fun hashCode(): Int = 0x5452414E
		override fun toString(): String = "ReaderCommittedPresentation.Transition(<redacted>)"
	}
}

private sealed interface ReaderCommittedPresentationAuthority {
	val readerSessionGeneration: Long
	val coordinatorEpoch: Long

	data class Retained(
		override val readerSessionGeneration: Long,
		override val coordinatorEpoch: Long,
		val owner: ReaderPresentationFrameOwner,
		val binding: ReaderPresentationBinding,
		val resource: ReaderTransitionResourceRegistration
	) : ReaderCommittedPresentationAuthority

	data class Neutral(
		override val readerSessionGeneration: Long,
		override val coordinatorEpoch: Long
	) : ReaderCommittedPresentationAuthority
}

private fun ReaderCommittedPresentation.authority(): ReaderCommittedPresentationAuthority = when (this) {
	is ReaderCommittedPresentation.Initial -> when (val initial = origin) {
		is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor ->
			ReaderCommittedPresentationAuthority.Retained(
				readerSessionGeneration = initial.readerSessionGeneration,
				coordinatorEpoch = initial.coordinatorEpoch,
				owner = initial.owner,
				binding = initial.binding,
				resource = initial.resource
			)
		is ReaderInitialCommittedPresentationOrigin.Neutral -> ReaderCommittedPresentationAuthority.Neutral(
			readerSessionGeneration = initial.readerSessionGeneration,
			coordinatorEpoch = initial.coordinatorEpoch
		)
	}
	is ReaderCommittedPresentation.Transition -> ReaderCommittedPresentationAuthority.Retained(
		readerSessionGeneration = committed.id.readerSessionGeneration,
		coordinatorEpoch = committed.id.coordinatorEpoch,
		owner = committed.owner,
		binding = committed.binding,
		resource = committed.resourceRegistration
	)
}

data class ReaderSettlementConsumptionKey(
	val transitionId: ReaderTransitionId,
	val expectedBinding: ReaderExpectedPresentationBinding,
	val acknowledgement: ReaderPageTurnSettlementAck
)

data class ReaderRetryableTransition(
	val id: ReaderTransitionId,
	val retainedOwner: ReaderPresentationFrameOwner,
	val predecessorResourceKey: ReaderTransitionResourceKey?,
	val semanticIntent: ReaderSemanticSynchronizationIntent?,
	val resolvedSuccessorBinding: ReaderPresentationBinding?,
	val gestureId: ReaderTransitionGestureId?,
	val settlementConsumed: Boolean,
	val semanticDestinationCommitted: Boolean
) {
	init {
		require(!settlementConsumed || resolvedSuccessorBinding != null)
		require(!semanticDestinationCommitted || resolvedSuccessorBinding != null)
	}
}

data class ReaderTransitionJournal(
	val active: ReaderActiveTransition? = null,
	val releaseOnlyCleanup: ReaderReleaseOnlyCleanupState? = null,
	val lastOutcome: ReaderTransitionOutcome? = null,
	val committed: ReaderCommittedPresentation,
	val retryableTransition: ReaderRetryableTransition? = null,
	val lastTransitionSequence: Long = 0L,
	val lastIssuedTransitionIdentity: ReaderTransitionParentIdentity? = null,
	val lastPublicationSequence: Long = 0L
) {
	constructor(
		active: ReaderActiveTransition? = null,
		lastOutcome: ReaderTransitionOutcome? = null,
		committed: ReaderCommittedTransition,
		retryableTransition: ReaderRetryableTransition? = null,
		lastPublicationSequence: Long = 0L
	) : this(
		active = active,
		lastOutcome = lastOutcome,
		committed = ReaderCommittedPresentation.Transition(committed),
		retryableTransition = retryableTransition,
		lastTransitionSequence = maxOf(
			active?.id?.sequence ?: 0L,
			retryableTransition?.id?.sequence ?: 0L,
			committed.id.sequence
		),
		lastIssuedTransitionIdentity = listOfNotNull(
			active?.id,
			retryableTransition?.id,
			committed.id
		).maxBy { it.sequence }.parentIdentity(),
		lastPublicationSequence = lastPublicationSequence
	)

	init {
		require(lastTransitionSequence >= 0L)
		require(releaseOnlyCleanup == null || active == null)
		require(releaseOnlyCleanup == null || retryableTransition == null)
		require(releaseOnlyCleanup == null ||
			releaseOnlyCleanup.key.readerSessionGeneration == committed.readerSessionGeneration)
		require(releaseOnlyCleanup == null ||
			releaseOnlyCleanup.key.coordinatorEpoch == committed.coordinatorEpoch)
		if (lastTransitionSequence == 0L) {
			require(lastIssuedTransitionIdentity == null)
			require(committed is ReaderCommittedPresentation.Initial)
			require(active == null)
			require(retryableTransition == null)
		} else {
			val identity = requireNotNull(lastIssuedTransitionIdentity)
			require(identity.readerSessionGeneration == committed.readerSessionGeneration)
			require(identity.coordinatorEpoch == committed.coordinatorEpoch)
			require(identity.sequence == lastTransitionSequence)
		}
		val knownIds = buildList {
			active?.id?.let(::add)
			retryableTransition?.id?.let(::add)
			(committed as? ReaderCommittedPresentation.Transition)?.committed?.id?.let(::add)
		}
		require(knownIds.all {
			it.readerSessionGeneration == committed.readerSessionGeneration &&
				it.coordinatorEpoch == committed.coordinatorEpoch &&
				it.sequence <= lastTransitionSequence
		})
		knownIds.maxByOrNull { it.sequence }?.takeIf {
			it.sequence == lastTransitionSequence
		}?.let { latest ->
			require(lastIssuedTransitionIdentity == latest.parentIdentity())
		}
		if (
			committed is ReaderCommittedPresentation.Initial &&
			active == null &&
			retryableTransition == null &&
			lastOutcome == null
		) {
			require(lastTransitionSequence == 0L)
			require(lastIssuedTransitionIdentity == null)
		}
		if (committed is ReaderCommittedPresentation.Transition) {
			val committedId = committed.committed.id
			active?.let { require(it.id.sequence > committedId.sequence) }
			retryableTransition?.let { require(it.id.sequence > committedId.sequence) }
			if (active == null && retryableTransition == null && lastOutcome == null) {
				require(lastTransitionSequence == committedId.sequence)
				require(lastIssuedTransitionIdentity == committedId.parentIdentity())
			}
		}
	}

	fun reduce(
		fact: ReaderTransitionFact,
		nowMillis: Long = 0L
	): ReaderTransitionReduction = readerTransitionJournalReduce(this, fact, nowMillis)

	override fun hashCode(): Int = 0x4A4F5552
	override fun toString(): String = "ReaderTransitionJournal(<redacted>)"
}

data class ReaderTransitionReduction(
	val state: ReaderTransitionJournal,
	val commands: List<ReaderTransitionCommand>
)

private enum class ReaderTransitionResourceRetention {
	TruthfulPredecessor,
	Successor,
	None
}

private enum class ReaderTransitionResourceFactMeaning {
	RegistrationOrObservation,
	ReleaseConfirmation,
	AcceptedProof,
	RejectedDisposition
}

private data class ReaderTransitionResourceFactClassification(
	val key: ReaderTransitionResourceKey,
	val meaning: ReaderTransitionResourceFactMeaning
)

private fun ReaderTransitionFact.resourceClassificationOrNull(): ReaderTransitionResourceFactClassification? =
	when (this) {
		is ReaderTransitionFact.ResourceObserved -> ReaderTransitionResourceFactClassification(
			key,
			ReaderTransitionResourceFactMeaning.RegistrationOrObservation
		)
		is ReaderTransitionFact.DeckReserved -> ReaderTransitionResourceFactClassification(
			key,
			ReaderTransitionResourceFactMeaning.RegistrationOrObservation
		)
		is ReaderTransitionFact.ResourceReleased -> ReaderTransitionResourceFactClassification(
			key,
			ReaderTransitionResourceFactMeaning.ReleaseConfirmation
		)
		is ReaderTransitionFact.DeckOwned -> ReaderTransitionResourceFactClassification(
			key,
			ReaderTransitionResourceFactMeaning.AcceptedProof
		)
		is ReaderTransitionFact.DeckPrepared -> ReaderTransitionResourceFactClassification(
			key,
			ReaderTransitionResourceFactMeaning.AcceptedProof
		)
		is ReaderTransitionFact.FrameTargetPrepared -> ReaderTransitionResourceFactClassification(
			target.resource.key,
			ReaderTransitionResourceFactMeaning.AcceptedProof
		)
		is ReaderTransitionFact.FrameTargetPreparationRejected -> ReaderTransitionResourceFactClassification(
			resource.key,
			ReaderTransitionResourceFactMeaning.RejectedDisposition
		)
		is ReaderTransitionFact.PreparedFrame -> ReaderTransitionResourceFactClassification(
			resource.key,
			ReaderTransitionResourceFactMeaning.AcceptedProof
		)
		is ReaderTransitionFact.CoverPostDraw -> ReaderTransitionResourceFactClassification(
			resourceKey,
			ReaderTransitionResourceFactMeaning.AcceptedProof
		)
		is ReaderTransitionFact.WebViewExposure -> ReaderTransitionResourceFactClassification(
			resourceKey,
			ReaderTransitionResourceFactMeaning.AcceptedProof
		)
		is ReaderTransitionFact.DeckRejected -> ReaderTransitionResourceFactClassification(
			key,
			ReaderTransitionResourceFactMeaning.RejectedDisposition
		)
		is ReaderTransitionFact.Intent,
		is ReaderTransitionFact.FoliateDestinationCommitted,
		is ReaderTransitionFact.SettlementAcknowledged,
		is ReaderTransitionFact.MaterialBindingAllocated,
		is ReaderTransitionFact.OwnerAndInputPublicationApplied,
		is ReaderTransitionFact.OwnerAndInputPublicationRejected,
		is ReaderTransitionFact.ViewportProfileReplaced,
		is ReaderTransitionFact.RasterProgress,
		is ReaderTransitionFact.RasterProven,
		is ReaderTransitionFact.RasterDeferred,
		is ReaderTransitionFact.RasterFailed,
		is ReaderTransitionFact.RendererCapacityAvailable,
		is ReaderTransitionFact.HostAvailable,
		is ReaderTransitionFact.PaginationProfileReady,
		is ReaderTransitionFact.RendererGenerationReady,
		is ReaderTransitionFact.VisibilityChanged,
		is ReaderTransitionFact.ResourceLost,
		is ReaderTransitionFact.DeadlineExpired,
		is ReaderTransitionFact.CommandRejected,
		is ReaderTransitionFact.SemanticPortContractViolated,
		is ReaderTransitionFact.Retry,
		is ReaderTransitionFact.PublicationReplaced,
		is ReaderTransitionFact.PublicationClosed -> null
	}

private fun ReaderTransitionJournal.isReleaseOnly(): Boolean = releaseOnlyCleanup != null

fun readerTransitionJournalReduce(
	journal: ReaderTransitionJournal,
	fact: ReaderTransitionFact,
	nowMillis: Long
): ReaderTransitionReduction {
	if (journal.isReleaseOnly() && fact !is ReaderTransitionFact.ResourceReleased) {
		return ReaderTransitionReduction(journal, emptyList())
	}
	val reduction = when (fact) {
	is ReaderTransitionFact.Intent -> journal.reduceIntent(fact)
	is ReaderTransitionFact.SettlementAcknowledged -> journal.reduceSettlement(fact)
	is ReaderTransitionFact.MaterialBindingAllocated -> journal.reduceMaterialAllocation(fact)
	is ReaderTransitionFact.FoliateDestinationCommitted -> journal.reduceDestination(fact)
	is ReaderTransitionFact.HostAvailable -> journal.reduceProof(
		fact.transitionId,
		ReaderTransitionProofKind.HostAvailable,
		factBinding = null
	)
	is ReaderTransitionFact.PaginationProfileReady -> journal.reduceProof(
		fact.transitionId,
		ReaderTransitionProofKind.PaginationProfile,
		factBinding = null
	)
	is ReaderTransitionFact.RendererGenerationReady -> journal.reduceProof(
		fact.transitionId,
		ReaderTransitionProofKind.RendererGeneration,
		factBinding = null
	)
	is ReaderTransitionFact.RasterProven -> journal.reduceProof(
		fact.transitionId,
		ReaderTransitionProofKind.Raster,
		factBinding = null
	)
	is ReaderTransitionFact.DeckOwned -> journal.reduceDeckOwned(fact)
	is ReaderTransitionFact.DeckPrepared -> journal.reduceDeckPrepared(fact)
	is ReaderTransitionFact.FrameTargetPrepared -> journal.reduceFrameTargetPrepared(fact)
	is ReaderTransitionFact.FrameTargetPreparationRejected -> journal.reduceFrameTargetPreparationRejected(fact)
	is ReaderTransitionFact.PreparedFrame -> journal.reducePreparedFrame(fact)
	is ReaderTransitionFact.OwnerAndInputPublicationApplied -> journal.reduceOwnerAndInputPublicationApplied(fact)
	is ReaderTransitionFact.OwnerAndInputPublicationRejected -> journal.reduceOwnerAndInputPublicationRejected(fact)
	is ReaderTransitionFact.CoverPostDraw -> journal.reduceOwnerProof(
		fact.transitionId,
		ReaderTransitionProofKind.CoverPostDraw,
		fact.binding,
		fact.frameOwner,
		fact.resourceKey
	)
	is ReaderTransitionFact.WebViewExposure -> journal.reduceOwnerProof(
		fact.transitionId,
		ReaderTransitionProofKind.WebViewExposure,
		fact.binding,
		fact.frameOwner,
		fact.resourceKey
	)
	is ReaderTransitionFact.DeadlineExpired -> journal.reduceTimeout(fact.transitionId)
	is ReaderTransitionFact.CommandRejected -> journal.reduceCommandRejected(fact)
	is ReaderTransitionFact.SemanticPortContractViolated ->
		journal.reduceSemanticPortContractViolation(fact)
	is ReaderTransitionFact.RasterDeferred -> journal.reduceRasterDeferral(fact)
	is ReaderTransitionFact.RasterFailed -> journal.reduceRasterFailure(fact)
	is ReaderTransitionFact.DeckRejected -> journal.reduceDeckRejected(fact)
	is ReaderTransitionFact.ResourceObserved,
	is ReaderTransitionFact.DeckReserved -> journal.reduceResourceRegistration(fact)
	is ReaderTransitionFact.ResourceReleased -> journal.reduceResourceReleased(fact)
	is ReaderTransitionFact.VisibilityChanged -> journal.unchanged()
	is ReaderTransitionFact.PublicationReplaced -> journal.reducePublicationReplacement(fact.transitionId)
	is ReaderTransitionFact.PublicationClosed -> journal.reducePublicationClose(fact.transitionId)
	is ReaderTransitionFact.Retry -> journal.reduceRetry(fact.transitionId)
	is ReaderTransitionFact.ViewportProfileReplaced,
	is ReaderTransitionFact.RasterProgress,
	is ReaderTransitionFact.RendererCapacityAvailable,
	is ReaderTransitionFact.ResourceLost -> journal.unchanged()
	}
	return reduction.withPendingCommandAuthority()
}

private fun ReaderTransitionReduction.withPendingCommandAuthority(): ReaderTransitionReduction {
	val stages = commands.mapNotNull(ReaderTransitionCommand::pendingStageOrNull).distinct()
	if (stages.isEmpty()) return this
	check(stages.size == 1) { "A reduction cannot emit multiple rejectable command stages" }
	val current = state.active ?: error("Rejectable command emission requires an active transition")
	check(current.pendingCommandStages.isEmpty()) {
		"Rejectable command emission requires cleared prior command authority"
	}
	val stage = stages.single()
	val admissibleCommandStages = admissibleStagesFor(stage)
	return copy(
		state = state.copy(
			active = current.copy(
				phase = current.phase.copy(
					contract = current.phase.contract.copy(
						callbackSources = current.phase.contract.callbackSources +
							ReaderTransitionFactKind.CommandRejected,
						admissibleCommandStages = admissibleCommandStages
					)
				),
				pendingCommandStages = setOf(stage)
			)
		)
	)
}

internal fun ReaderTransitionCommand.pendingStageOrNull(): ReaderTransitionCommandStage? = when (this) {
	is ReaderTransitionCommand.RequestSemanticSynchronization ->
		ReaderTransitionCommandStage.SemanticSynchronization
	is ReaderTransitionCommand.AllocateMaterialBinding -> ReaderTransitionCommandStage.MaterialAllocation
	is ReaderTransitionCommand.RequestRasterPreparation -> ReaderTransitionCommandStage.RasterPreparation
	is ReaderTransitionCommand.ReserveDeck -> ReaderTransitionCommandStage.DeckReservation
	is ReaderTransitionCommand.PrepareFrameTarget -> ReaderTransitionCommandStage.FrameTargetPreparation
	is ReaderTransitionCommand.RequestFramePresentation -> ReaderTransitionCommandStage.FramePresentation
	is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ->
		ReaderTransitionCommandStage.RetainedPublication
	is ReaderTransitionCommand.CommitOwnerAndInputLease ->
		ReaderTransitionCommandStage.SuccessorPublication
	is ReaderTransitionCommand.ReleaseResource,
	is ReaderTransitionCommand.CancelOwnedWork -> null
}

private fun ReaderTransitionJournal.reduceIntent(
	fact: ReaderTransitionFact.Intent
): ReaderTransitionReduction = when (val intent = fact.intent) {
	ReaderCancelIntent -> reduceUserCancellation(fact.transitionId)
	ReaderRetryIntent -> reduceRetry(fact.transitionId)
	ReaderCoverReturnIntent -> unchanged()
	is ReaderSemanticSynchronizationIntent -> beginSemanticSynchronization(fact.transitionId, intent)
}

private fun ReaderTransitionJournal.beginSemanticSynchronization(
	requestedTransitionId: ReaderTransitionId?,
	intent: ReaderSemanticSynchronizationIntent
): ReaderTransitionReduction {
	val current = active
	if (requestedTransitionId != null && requestedTransitionId != current?.id) return unchanged()
	if (current?.phase?.contract?.supersession == ReaderTransitionSupersession.RejectSuccessor) {
		return unchanged()
	}
	val predecessor = committed.authority()
	val nextSequence = nextTransitionSequence()
	val operation = intent.operation()
	val expectedBinding = when (predecessor) {
		is ReaderCommittedPresentationAuthority.Retained ->
			ReaderExpectedPresentationBinding.SemanticSuccessor(predecessor.binding, nextSequence)
		is ReaderCommittedPresentationAuthority.Neutral ->
			ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(nextSequence)
	}
	val nextId = ReaderTransitionId(
		readerSessionGeneration = predecessor.readerSessionGeneration,
		coordinatorEpoch = predecessor.coordinatorEpoch,
		sequence = nextSequence,
		operation = operation,
		expectedBinding = expectedBinding,
		parent = lastIssuedTransitionIdentity
	)
	val retainedOwner = current?.phase?.contract?.retainedOwner ?: when (predecessor) {
		is ReaderCommittedPresentationAuthority.Retained -> predecessor.owner
		is ReaderCommittedPresentationAuthority.Neutral -> ReaderPresentationFrameOwner.Neutral
	}
	val predecessorKey = current?.predecessorResourceKey ?: when (predecessor) {
		is ReaderCommittedPresentationAuthority.Retained -> predecessor.resource.key
		is ReaderCommittedPresentationAuthority.Neutral -> null
	}
	val predecessorRegistration = current?.predecessorResourceRegistration ?: when (predecessor) {
		is ReaderCommittedPresentationAuthority.Retained -> predecessor.resource
		is ReaderCommittedPresentationAuthority.Neutral -> null
	}
	val gestureId = (intent as? ReaderPageTurnIntent)?.gestureId
	val basePhase = ReaderTransitionLivenessTable.phase(
		id = nextId,
		kind = ReaderTransitionPhaseKind.Accepted,
		retainedOwner = retainedOwner,
		gestureId = gestureId
	)
	val requiresRetainedPublication = predecessorRegistration != null &&
		retainedOwner != ReaderPresentationFrameOwner.Neutral
	val publicationIdentity = nextPublicationIdentity()
	val phase = if (requiresRetainedPublication) {
		basePhase.copy(
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
				callbackSources = setOf(
					ReaderTransitionFactKind.OwnerAndInputPublicationApplied,
					ReaderTransitionFactKind.OwnerAndInputPublicationRejected,
					ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(ReaderTransitionCommandStage.RetainedPublication)
			)
		)
	} else {
		basePhase
	}
	val next = ReaderActiveTransition(
		id = nextId,
		phase = phase,
		predecessorResourceKey = predecessorKey,
		predecessorResourceRegistration = predecessorRegistration,
		pendingPublicationIdentity = publicationIdentity.takeIf { requiresRetainedPublication },
		semanticIntent = intent
	)
	return ReaderTransitionReduction(
		state = copy(
			active = next,
			lastOutcome = current?.let {
				ReaderTransitionOutcome.Cancelled(
					ReaderTransitionCancellationReason.Superseded,
					retainedOwner
				)
			},
			retryableTransition = null,
			lastTransitionSequence = nextSequence,
			lastIssuedTransitionIdentity = nextId.parentIdentity(),
			lastPublicationSequence = if (requiresRetainedPublication) {
				publicationIdentity.value
			} else {
				lastPublicationSequence
			}
		),
		commands = buildList {
			if (current != null) {
				add(ReaderTransitionCommand.CancelOwnedWork(current.id))
				addAll(
					resourceDispositionCommands(
						transitionId = current.id,
						candidates = knownResourceKeys(current),
						retention = ReaderTransitionResourceRetention.TruthfulPredecessor
					)
				)
			}
			if (requiresRetainedPublication) {
				add(
					ReaderTransitionCommand.PublishRetainedOwnerAndInputLease(
						transitionId = nextId,
						retainedOwner = retainedOwner,
						retainedBinding = (predecessor as ReaderCommittedPresentationAuthority.Retained).binding,
						retainedResource = requireNotNull(predecessorRegistration),
						requestedLease = phase.contract.inputLease,
						publicationIdentity = publicationIdentity
					)
				)
			} else {
				add(ReaderTransitionCommand.RequestSemanticSynchronization(nextId, intent, intent.requestHandle))
			}
		}
	)
}

private fun ReaderTransitionJournal.reduceUserCancellation(
	transitionId: ReaderTransitionId?
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (transitionId != null && transitionId != current.id) return unchanged()
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Cancelled(
				ReaderTransitionCancellationReason.UserCancelled,
				current.phase.contract.retainedOwner
			),
			retryableTransition = null
		),
		commands = terminalResourceCommands(current)
	)
}

private fun ReaderTransitionJournal.reduceRetry(
	transitionId: ReaderTransitionId?
): ReaderTransitionReduction {
	if (active != null) return unchanged()
	val retryable = retryableTransition ?: return unchanged()
	if (transitionId != null && transitionId != retryable.id) return unchanged()
	val nextSequence = nextTransitionSequence()
	val immediateParent = requireNotNull(lastIssuedTransitionIdentity)
	val settledPageBinding = retryable.resolvedSuccessorBinding.takeIf {
		retryable.settlementConsumed
	}
	val authoritativeBinding = retryable.resolvedSuccessorBinding.takeIf {
		retryable.settlementConsumed || retryable.semanticDestinationCommitted
	}
	val nextId = when {
		settledPageBinding != null -> retryable.id.copy(
			sequence = nextSequence,
			operation = ReaderTransitionOperation.RendererRecovery,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(settledPageBinding),
			parent = immediateParent
		)
		authoritativeBinding != null -> retryable.id.copy(
			sequence = nextSequence,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(authoritativeBinding),
			parent = immediateParent
		)
		else -> retryable.id.copy(
			sequence = nextSequence,
			expectedBinding = when (retryable.id.expectedBinding) {
				is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ->
					ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(nextSequence)
				else -> retryable.id.expectedBinding
			},
			parent = immediateParent
		)
	}
	val satisfiedProofs = if (retryable.semanticDestinationCommitted) {
		setOf(ReaderTransitionProofKind.SemanticDestination)
	} else {
		emptySet()
	}
	val basePhase = ReaderTransitionLivenessTable.phase(
		id = nextId,
		kind = ReaderTransitionPhaseKind.Accepted,
		retainedOwner = retryable.retainedOwner,
		satisfiedProofs = satisfiedProofs,
		gestureId = null
	)
	val retainedCommitted = committed.authority()
	val predecessorRegistration = when (retainedCommitted) {
		is ReaderCommittedPresentationAuthority.Retained -> retainedCommitted.resource
		is ReaderCommittedPresentationAuthority.Neutral -> null
	}
	val requiresRetainedPublication = predecessorRegistration != null &&
		retryable.retainedOwner != ReaderPresentationFrameOwner.Neutral
	val publicationIdentity = nextPublicationIdentity()
	val phase = if (requiresRetainedPublication) {
		basePhase.copy(
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
				callbackSources = setOf(
					ReaderTransitionFactKind.OwnerAndInputPublicationApplied,
					ReaderTransitionFactKind.OwnerAndInputPublicationRejected,
					ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(ReaderTransitionCommandStage.RetainedPublication)
			)
		)
	} else {
		basePhase
	}
	val next = ReaderActiveTransition(
		id = nextId,
		phase = phase,
		resolvedSuccessorBinding = authoritativeBinding,
		predecessorResourceKey = retryable.predecessorResourceKey,
		predecessorResourceRegistration = predecessorRegistration,
		pendingPublicationIdentity = publicationIdentity.takeIf { requiresRetainedPublication },
		semanticIntent = retryable.semanticIntent.takeIf { authoritativeBinding == null },
		authoritativeDestinationCommitted = authoritativeBinding != null
	)
	return ReaderTransitionReduction(
		state = copy(
			active = next,
			retryableTransition = null,
			lastTransitionSequence = nextSequence,
			lastIssuedTransitionIdentity = nextId.parentIdentity(),
			lastPublicationSequence = if (requiresRetainedPublication) {
				publicationIdentity.value
			} else {
				lastPublicationSequence
			}
		),
		commands = if (requiresRetainedPublication) {
			listOf(
				ReaderTransitionCommand.PublishRetainedOwnerAndInputLease(
					nextId,
					retryable.retainedOwner,
					(retainedCommitted as ReaderCommittedPresentationAuthority.Retained).binding,
					requireNotNull(predecessorRegistration),
					phase.contract.inputLease,
					publicationIdentity
				)
			)
		} else {
			when {
				authoritativeBinding != null -> listOf(
					ReaderTransitionCommand.AllocateMaterialBinding(
						nextId,
						authoritativeBinding.withoutMaterialGenerations()
					)
				)
				next.semanticIntent != null -> listOf(
					ReaderTransitionCommand.RequestSemanticSynchronization(
						nextId,
						next.semanticIntent,
						next.semanticIntent.requestHandle
					)
				)
				else -> emptyList()
			}
		}
	)
}

private fun ReaderSemanticSynchronizationIntent.operation(): ReaderTransitionOperation = when (this) {
	is ReaderBootstrapNativePageIntent -> ReaderTransitionOperation.BootstrapNativePage
	is ReaderPageTurnIntent -> ReaderTransitionOperation.CurlClaimAndSettlement
	is ReaderExternalRelocationIntent -> ReaderTransitionOperation.ExternalSemanticRelocation
	is ReaderCoverEntryIntent -> ReaderTransitionOperation.CoverToPageEntry
}

private fun ReaderTransitionJournal.reduceResourceRegistration(
	fact: ReaderTransitionFact
): ReaderTransitionReduction {
	val classification = requireNotNull(fact.resourceClassificationOrNull())
	check(classification.meaning == ReaderTransitionResourceFactMeaning.RegistrationOrObservation)
	val current = active ?: return reduceRejectedResourceKey(classification.key)
	if (
		current.id != fact.transitionId ||
		classification.key.owningTransitionIdOrNull != current.id ||
		(fact is ReaderTransitionFact.DeckReserved &&
			classification.key.kind != ReaderTransitionResourceKind.Deck)
	) return reduceRejectedResourceKey(classification.key)
	val expectedStage = when (classification.key.kind) {
		ReaderTransitionResourceKind.Raster -> ReaderTransitionCommandStage.RasterPreparation
		ReaderTransitionResourceKind.Deck -> ReaderTransitionCommandStage.DeckReservation
		ReaderTransitionResourceKind.CallbackRegistration,
		ReaderTransitionResourceKind.FrameHandoff -> null
	}
	if (expectedStage != null && current.pendingCommandStages != setOf(expectedStage)) {
		return reduceRejectedResourceKey(classification.key)
	}
	return ReaderTransitionReduction(
		copy(
			active = current.copy(
				ownedResourceKeys = current.ownedResourceKeys + classification.key
			)
		),
		emptyList()
	)
}

private fun ReaderTransitionJournal.reduceResourceReleased(
	fact: ReaderTransitionFact.ResourceReleased
): ReaderTransitionReduction {
	val classification = requireNotNull(fact.resourceClassificationOrNull())
	check(classification.meaning == ReaderTransitionResourceFactMeaning.ReleaseConfirmation)
	val current = active?.takeIf { it.id == fact.transitionId } ?: return unchanged()
	val key = classification.key
	return ReaderTransitionReduction(
		copy(
			active = current.copy(
				ownedResourceKeys = current.ownedResourceKeys - key,
				admittedDeckKey = current.admittedDeckKey.takeUnless { it == key },
				pendingPreparedDeckKey = current.pendingPreparedDeckKey.takeUnless { it == key },
				successorResourceKey = current.successorResourceKey.takeUnless { it == key },
				successorResourceRegistration = current.successorResourceRegistration?.takeUnless {
					it.key == key
				},
				preparedFrameOwner = current.preparedFrameOwner.takeUnless {
					current.successorResourceKey == key
				},
				resolvedSuccessorBinding = current.resolvedSuccessorBinding.takeUnless {
					current.successorResourceKey == key
				},
				authoritativeDestinationCommitted = current.authoritativeDestinationCommitted &&
					current.successorResourceKey != key
			)
		),
		emptyList()
	)
}

private fun ReaderTransitionJournal.reduceMaterialAllocation(
	fact: ReaderTransitionFact.MaterialBindingAllocated
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	val allocation = fact.allocation
	if (
		fact.transitionId != current.id ||
		!readerTransitionMaterialBindingIsValid(
			current.id,
			allocation.allocatedBinding,
			allocation
		) ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.MaterialAllocation) ||
		ReaderTransitionProofKind.MaterialBindingAllocation !in current.phase.contract.awaitedProofs ||
		current.resolvedSuccessorBinding?.let { !it.matchesAllocation(allocation.allocatedBinding) } == true
	) return unchanged()
	val remaining = current.phase.contract.awaitedProofs - ReaderTransitionProofKind.MaterialBindingAllocation
	val updated = current.copy(
		pendingCommandStages = current.pendingCommandStages - ReaderTransitionCommandStage.MaterialAllocation,
		resolvedSuccessorBinding = allocation.allocatedBinding,
		materialAllocation = allocation
	)
	val nextCommand = if (current.id.operation == ReaderTransitionOperation.RendererRecovery) {
		ReaderTransitionCommand.ReserveDeck(
			current.id,
			allocation.allocatedBinding,
			ReaderTransitionDeckRole.Recovery,
			allocation
		)
	} else {
		ReaderTransitionCommand.RequestRasterPreparation(
			current.id,
			allocation.allocatedBinding,
			allocation
		)
	}
	return ReaderTransitionReduction(
		copy(active = updated.withAwaitedProofs(remaining)),
		listOf(nextCommand)
	)
}

private fun ReaderTransitionJournal.reduceSettlement(
	fact: ReaderTransitionFact.SettlementAcknowledged
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (
		current.id.operation != ReaderTransitionOperation.CurlClaimAndSettlement ||
		fact.transitionId != current.id ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.SemanticSynchronization) ||
		!current.id.expectedBinding.matches(fact.binding) ||
		current.resolvedSuccessorBinding?.let { it != fact.binding } == true ||
		ReaderTransitionProofKind.SettlementAcknowledgement !in current.phase.contract.awaitedProofs
	) return unchanged()
	val key = ReaderSettlementConsumptionKey(
		transitionId = current.id,
		expectedBinding = current.id.expectedBinding,
		acknowledgement = fact.acknowledgement
	)
	if (current.consumedSettlement != null) return unchanged()
	val remaining = current.phase.contract.awaitedProofs - ReaderTransitionProofKind.SettlementAcknowledgement
	val resolved = current.copy(
		pendingCommandStages = current.pendingCommandStages -
			ReaderTransitionCommandStage.SemanticSynchronization,
		resolvedSuccessorBinding = fact.binding,
		consumedSettlement = key
	)
	val withConsumption = copy(active = resolved)
	if (remaining.isEmpty()) {
		val committedOwner = resolved.preparedFrameOwner ?: return unchanged()
		val registration = resolved.successorResourceRegistration ?: return unchanged()
		return withConsumption.beginSuccessorCommit(
			resolved,
			fact.binding,
			committedOwner,
			registration
		)
	}
	return withConsumption.completeOrContinue(resolved, remaining)
}

private fun ReaderTransitionJournal.lifecyclePublicationIdentities(): Set<ReaderPresentationPublicationIdentity> =
	buildSet {
		when (val authority = committed.authority()) {
			is ReaderCommittedPresentationAuthority.Retained -> add(authority.binding.publicationIdentity)
			is ReaderCommittedPresentationAuthority.Neutral -> Unit
		}
		active?.id?.expectedBinding?.publicationIdentity()?.let(::add)
		active?.resolvedSuccessorBinding?.publicationIdentity?.let(::add)
		retryableTransition?.id?.expectedBinding?.publicationIdentity()?.let(::add)
		retryableTransition?.resolvedSuccessorBinding?.publicationIdentity?.let(::add)
	}

private fun ReaderExpectedPresentationBinding.publicationIdentity(): ReaderPresentationPublicationIdentity? =
	when (this) {
		is ReaderExpectedPresentationBinding.Exact -> binding.publicationIdentity
		is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial,
		is ReaderExpectedPresentationBinding.NoCommittedPresentation -> null
		is ReaderExpectedPresentationBinding.SemanticSuccessor -> predecessor.publicationIdentity
	}

private enum class ReaderUntaggedDestinationArbitration {
	ConsumeCurrentIntent,
	CoalesceCurrentDestination,
	SupersedeCurrentTransition
}

private fun ReaderActiveTransition?.arbitrateUntaggedDestination(
	binding: ReaderPresentationBinding
): ReaderUntaggedDestinationArbitration {
	val current = this ?: return ReaderUntaggedDestinationArbitration.SupersedeCurrentTransition
	if (current.authoritativeDestinationCommitted) {
		return if (current.resolvedSuccessorBinding == binding) {
			ReaderUntaggedDestinationArbitration.CoalesceCurrentDestination
		} else {
			ReaderUntaggedDestinationArbitration.SupersedeCurrentTransition
		}
	}
	return if (
		current.semanticIntent != null &&
		current.id.operation in setOf(
			ReaderTransitionOperation.ExternalSemanticRelocation,
			ReaderTransitionOperation.CoverToPageEntry
		) &&
		current.id.expectedBinding.matches(binding)
	) {
		ReaderUntaggedDestinationArbitration.ConsumeCurrentIntent
	} else {
		ReaderUntaggedDestinationArbitration.SupersedeCurrentTransition
	}
}

private fun ReaderTransitionJournal.reduceAcceptedSemanticDestination(
	current: ReaderActiveTransition,
	binding: ReaderPresentationBinding
): ReaderTransitionReduction {
	if (
		current.authoritativeDestinationCommitted ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.SemanticSynchronization) ||
		current.resolvedSuccessorBinding?.let { it != binding } == true
	) return unchanged()
	if (ReaderTransitionProofKind.SemanticDestination in current.phase.contract.awaitedProofs) {
		return reduceProof(current.id, ReaderTransitionProofKind.SemanticDestination, binding)
	}
	val awaitingRetainedPublication =
		current.pendingCommandStages == setOf(ReaderTransitionCommandStage.RetainedPublication)
	return ReaderTransitionReduction(
		state = copy(
			active = current.copy(
				pendingCommandStages = if (awaitingRetainedPublication) {
					current.pendingCommandStages
				} else {
					current.pendingCommandStages -
						ReaderTransitionCommandStage.SemanticSynchronization
				},
				resolvedSuccessorBinding = binding,
				authoritativeDestinationCommitted = true
			)
		),
		commands = if (awaitingRetainedPublication) {
			emptyList()
		} else {
			listOf(
				ReaderTransitionCommand.AllocateMaterialBinding(
					current.id,
					binding.withoutMaterialGenerations()
				)
			)
		}
	)
}

private fun ReaderTransitionJournal.reduceDestination(
	fact: ReaderTransitionFact.FoliateDestinationCommitted
): ReaderTransitionReduction {
	val current = active
	if (fact.transitionId != null) {
		if (current?.id != fact.transitionId || !current.id.expectedBinding.matches(fact.binding)) {
			return unchanged()
		}
		return if (
			current.semanticIntent != null &&
			current.id.operation in setOf(
				ReaderTransitionOperation.BootstrapNativePage,
				ReaderTransitionOperation.ExternalSemanticRelocation,
				ReaderTransitionOperation.CoverToPageEntry
			)
		) {
			reduceAcceptedSemanticDestination(current, fact.binding)
		} else {
			reduceProof(current.id, ReaderTransitionProofKind.SemanticDestination, fact.binding)
		}
	}
	val lifecyclePublications = lifecyclePublicationIdentities()
	if (
		lifecyclePublications.isNotEmpty() &&
		(lifecyclePublications.size != 1 || fact.binding.publicationIdentity !in lifecyclePublications)
	) {
		return unchanged()
	}
	when (current.arbitrateUntaggedDestination(fact.binding)) {
		ReaderUntaggedDestinationArbitration.ConsumeCurrentIntent ->
			return reduceAcceptedSemanticDestination(requireNotNull(current), fact.binding)
		ReaderUntaggedDestinationArbitration.CoalesceCurrentDestination -> return unchanged()
		ReaderUntaggedDestinationArbitration.SupersedeCurrentTransition -> Unit
	}
	if (current?.phase?.contract?.supersession == ReaderTransitionSupersession.RejectSuccessor) {
		return unchanged()
	}
	val retainedCommitted = committed.authority()
	val retainedOwner = current?.phase?.contract?.retainedOwner ?: when (retainedCommitted) {
		is ReaderCommittedPresentationAuthority.Retained -> retainedCommitted.owner
		is ReaderCommittedPresentationAuthority.Neutral -> ReaderPresentationFrameOwner.Neutral
	}
	val predecessorResourceKey = current?.predecessorResourceKey ?: when (retainedCommitted) {
		is ReaderCommittedPresentationAuthority.Retained -> retainedCommitted.resource.key
		is ReaderCommittedPresentationAuthority.Neutral -> null
	}
	val predecessorRegistration = current?.predecessorResourceRegistration ?: when (retainedCommitted) {
		is ReaderCommittedPresentationAuthority.Retained -> retainedCommitted.resource
		is ReaderCommittedPresentationAuthority.Neutral -> null
	}
	val nextSequence = nextTransitionSequence()
	val relocationId = ReaderTransitionId(
		readerSessionGeneration = retainedCommitted.readerSessionGeneration,
		coordinatorEpoch = retainedCommitted.coordinatorEpoch,
		sequence = nextSequence,
		operation = ReaderTransitionOperation.ExternalSemanticRelocation,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(fact.binding),
		parent = lastIssuedTransitionIdentity
	)
	val basePhase = ReaderTransitionLivenessTable.phase(
		id = relocationId,
		kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
		retainedOwner = retainedOwner,
		satisfiedProofs = setOf(ReaderTransitionProofKind.SemanticDestination)
	)
	val requiresRetainedPublication = predecessorRegistration != null &&
		retainedOwner != ReaderPresentationFrameOwner.Neutral
	val publicationIdentity = nextPublicationIdentity()
	val phase = if (requiresRetainedPublication) {
		basePhase.copy(
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
				callbackSources = setOf(
					ReaderTransitionFactKind.OwnerAndInputPublicationApplied,
					ReaderTransitionFactKind.OwnerAndInputPublicationRejected,
					ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(ReaderTransitionCommandStage.RetainedPublication)
			)
		)
	} else {
		basePhase
	}
	return ReaderTransitionReduction(
		state = copy(
			active = ReaderActiveTransition(
				relocationId,
				phase,
				resolvedSuccessorBinding = fact.binding,
				predecessorResourceKey = predecessorResourceKey,
				predecessorResourceRegistration = predecessorRegistration,
				pendingPublicationIdentity = publicationIdentity.takeIf { requiresRetainedPublication },
				authoritativeDestinationCommitted = true
			),
			lastOutcome = if (current != null) {
				ReaderTransitionOutcome.Cancelled(
					ReaderTransitionCancellationReason.Superseded,
					retainedOwner
				)
			} else {
				lastOutcome
			},
			retryableTransition = null,
			lastTransitionSequence = nextSequence,
			lastIssuedTransitionIdentity = relocationId.parentIdentity(),
			lastPublicationSequence = if (requiresRetainedPublication) {
				publicationIdentity.value
			} else {
				lastPublicationSequence
			}
		),
		commands = buildList {
			if (current != null) {
				add(ReaderTransitionCommand.CancelOwnedWork(current.id))
				addAll(
					resourceDispositionCommands(
						transitionId = current.id,
						candidates = knownResourceKeys(current),
						retention = ReaderTransitionResourceRetention.TruthfulPredecessor
					)
				)
			}
			if (requiresRetainedPublication) {
				add(
					ReaderTransitionCommand.PublishRetainedOwnerAndInputLease(
						transitionId = relocationId,
						retainedOwner = retainedOwner,
						retainedBinding =
							(retainedCommitted as ReaderCommittedPresentationAuthority.Retained).binding,
						retainedResource = requireNotNull(predecessorRegistration),
						requestedLease = ReaderTransitionInputLease.ChromeOnly,
						publicationIdentity = publicationIdentity
					)
				)
			} else {
				add(
					ReaderTransitionCommand.AllocateMaterialBinding(
						relocationId,
						fact.binding.withoutMaterialGenerations()
					)
				)
			}
		}
	)
}

private fun ReaderTransitionJournal.reduceFrameTargetPrepared(
	fact: ReaderTransitionFact.FrameTargetPrepared
): ReaderTransitionReduction {
	val current = active ?: return reduceRejectedResourceKey(fact.target.resource.key)
	if (fact.transitionId != current.id) {
		return reduceRejectedResourceKey(fact.target.resource.key)
	}
	if (current.pendingCommandStages != setOf(ReaderTransitionCommandStage.FrameTargetPreparation)) {
		return reduceRejectedResourceKey(fact.target.resource.key)
	}
	if (
		ReaderTransitionProofKind.FrameTargetPreparation !in current.phase.contract.awaitedProofs ||
		current.pendingFrameTargetSpecification != fact.target.specification ||
		current.pendingFrameTargetRegistration != fact.target.resource ||
		!current.id.expectedBinding.acceptsMaterialAllocation(fact.target.specification.binding)
	) return reduceRejectedResourceKey(fact.target.resource.key)
	val next = current.copy(
		pendingCommandStages = emptySet(),
		phase = current.phase.copy(
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			contract = current.phase.contract.copy(
				awaitedProofs = setOf(ReaderTransitionProofKind.PreparedFrame),
				callbackSources = setOf(
					ReaderTransitionFactKind.PreparedFrame,
					ReaderTransitionFactKind.ResourceLost,
					ReaderTransitionFactKind.DeadlineExpired,
					ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = admissibleStagesFor(
					ReaderTransitionCommandStage.FramePresentation
				)
			)
		),
		frameTarget = fact.target,
		pendingFrameTargetSpecification = null,
		pendingFrameTargetRegistration = null
	)
	return ReaderTransitionReduction(
		copy(active = next),
		listOf(ReaderTransitionCommand.RequestFramePresentation(current.id, fact.target))
	)
}

private fun ReaderTransitionJournal.reduceFrameTargetPreparationRejected(
	fact: ReaderTransitionFact.FrameTargetPreparationRejected
): ReaderTransitionReduction {
	val current = active
	if (
		current?.id != fact.transitionId ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.FrameTargetPreparation) ||
		ReaderTransitionProofKind.FrameTargetPreparation !in current.phase.contract.awaitedProofs ||
		current.pendingFrameTargetSpecification != fact.specification ||
		current.pendingFrameTargetRegistration != fact.resource
	) return reduceRejectedResourceKey(fact.resource.key)
	return reduceFailure(fact.transitionId, fact.reason, listOf(fact.resource.key))
}

private fun ReaderTransitionJournal.reducePreparedFrame(
	fact: ReaderTransitionFact.PreparedFrame
): ReaderTransitionReduction {
	val current = active ?: return reduceRejectedResourceKey(fact.resource.key)
	if (fact.transitionId != current.id) {
		return reduceRejectedResourceKey(fact.resource.key)
	}
	if (current.pendingCommandStages != setOf(ReaderTransitionCommandStage.FramePresentation)) {
		return reduceRejectedResourceKey(fact.resource.key)
	}
	if (
		current.phase.kind == ReaderTransitionPhaseKind.Committing &&
		current.frameTarget == fact.target &&
		current.preparedFrameOwner == fact.frameOwner &&
		current.successorResourceRegistration == fact.resource
	) return unchanged()
	val target = current.frameTarget
	if (
		ReaderTransitionProofKind.PreparedFrame !in current.phase.contract.awaitedProofs ||
		target == null ||
		fact.target != target ||
		fact.resource != target.resource ||
		!current.id.operation.acceptsCommittedOwner(fact.frameOwner)
	) return reduceRejectedResourceKey(fact.resource.key)
	val binding = target.specification.binding
	val resolved = current.copy(
		pendingCommandStages = current.pendingCommandStages - ReaderTransitionCommandStage.FramePresentation,
		preparedFrameOwner = fact.frameOwner,
		resolvedSuccessorBinding = binding,
		successorResourceKey = fact.resource.key,
		successorResourceRegistration = fact.resource
	)
	val remaining = current.phase.contract.awaitedProofs - ReaderTransitionProofKind.PreparedFrame
	if (remaining.isNotEmpty()) {
		return ReaderTransitionReduction(
			copy(active = resolved.withAwaitedProofs(remaining)),
			emptyList()
		)
	}
	return beginSuccessorCommit(resolved, binding, fact.frameOwner, fact.resource)
}

private fun ReaderTransitionJournal.beginSuccessorCommit(
	current: ReaderActiveTransition,
	binding: ReaderPresentationBinding,
	owner: ReaderPresentationFrameOwner,
	registration: ReaderTransitionResourceRegistration
): ReaderTransitionReduction {
	val target = current.frameTarget ?: return unchanged()
	val publicationIdentity = nextPublicationIdentity()
	val committingPhase = current.phase.copy(
		kind = ReaderTransitionPhaseKind.Committing,
		contract = current.phase.contract.copy(
			awaitedProofs = setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
			callbackSources = setOf(
				ReaderTransitionFactKind.OwnerAndInputPublicationApplied,
				ReaderTransitionFactKind.OwnerAndInputPublicationRejected,
				ReaderTransitionFactKind.CommandRejected
			),
			admissibleCommandStages = admissibleStagesFor(
				ReaderTransitionCommandStage.SuccessorPublication
			)
		)
	)
	val committing = current.copy(
		phase = committingPhase,
		pendingCommandStages = emptySet(),
		preparedFrameOwner = owner,
		resolvedSuccessorBinding = binding,
		successorResourceKey = registration.key,
		successorResourceRegistration = registration,
		pendingPublicationIdentity = publicationIdentity
	)
	return ReaderTransitionReduction(
		state = copy(
			active = committing,
			lastPublicationSequence = publicationIdentity.value
		),
		commands = listOf(
			ReaderTransitionCommand.CommitOwnerAndInputLease(
				transitionId = current.id,
				targetHandle = target.handle,
				owner = owner,
				binding = binding,
				preparedFrameResource = registration,
				requestedLease = owner.committedInputLease(binding),
				publicationIdentity = publicationIdentity
			)
		)
	)
}

private fun ReaderTransitionJournal.reduceOwnerAndInputPublicationApplied(
	fact: ReaderTransitionFact.OwnerAndInputPublicationApplied
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	val expectedStage = when (fact.subject) {
		is ReaderOwnerAndInputPublicationSubject.Retained ->
			ReaderTransitionCommandStage.RetainedPublication
		is ReaderOwnerAndInputPublicationSubject.Successor ->
			ReaderTransitionCommandStage.SuccessorPublication
	}
	if (
		fact.transitionId != current.id ||
		current.pendingCommandStages != setOf(expectedStage) ||
		ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement !in
			current.phase.contract.awaitedProofs ||
		fact.publicationIdentity != current.pendingPublicationIdentity
	) return unchanged()
	return when (val subject = fact.subject) {
		is ReaderOwnerAndInputPublicationSubject.Retained -> {
			if (
				subject.retainedResource != current.predecessorResourceRegistration ||
				fact.publishedOwner != current.phase.contract.retainedOwner ||
				!fact.publishedOwner.hasBinding(fact.publishedBinding) ||
				!readerInputLeaseIsNoBroaderThan(
					fact.finalPhysicalInputLease,
					current.phase.contract.inputLease
				)
			) return unchanged()
			val satisfiedProofs = if (current.authoritativeDestinationCommitted) {
				setOf(ReaderTransitionProofKind.SemanticDestination)
			} else {
				emptySet()
			}
			val phase = ReaderTransitionLivenessTable.phase(
				id = current.id,
				kind = ReaderTransitionPhaseKind.Accepted,
				retainedOwner = fact.publishedOwner,
				satisfiedProofs = satisfiedProofs,
				gestureId = (current.semanticIntent as? ReaderPageTurnIntent)?.gestureId
			)
			val resumed = current.copy(
				phase = phase,
				pendingCommandStages = emptySet(),
				pendingPublicationIdentity = null
			)
			val command = when {
				current.authoritativeDestinationCommitted && current.resolvedSuccessorBinding != null ->
					ReaderTransitionCommand.AllocateMaterialBinding(
						current.id,
						current.resolvedSuccessorBinding.withoutMaterialGenerations()
					)
				current.semanticIntent != null -> ReaderTransitionCommand.RequestSemanticSynchronization(
					current.id,
					current.semanticIntent,
					current.semanticIntent.requestHandle
				)
				else -> null
			}
			ReaderTransitionReduction(copy(active = resumed), listOfNotNull(command))
		}
		is ReaderOwnerAndInputPublicationSubject.Successor -> {
			val target = current.frameTarget ?: return unchanged()
			val owner = current.preparedFrameOwner ?: return unchanged()
			val binding = current.resolvedSuccessorBinding ?: return unchanged()
			val registration = current.successorResourceRegistration ?: return unchanged()
			if (
				current.phase.kind != ReaderTransitionPhaseKind.Committing ||
				subject.targetHandle != target.handle ||
				subject.preparedFrameResource != registration ||
				fact.publishedOwner != owner ||
				fact.publishedBinding != binding ||
				!readerInputLeaseIsNoBroaderThan(
					fact.finalPhysicalInputLease,
					owner.committedInputLease(binding)
				)
			) return unchanged()
			succeedAfterPublication(current, binding, owner, registration)
		}
	}
}

private fun ReaderTransitionJournal.reduceOwnerAndInputPublicationRejected(
	fact: ReaderTransitionFact.OwnerAndInputPublicationRejected
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	val expectedStage = when (fact.subject) {
		is ReaderOwnerAndInputPublicationSubject.Retained ->
			ReaderTransitionCommandStage.RetainedPublication
		is ReaderOwnerAndInputPublicationSubject.Successor ->
			ReaderTransitionCommandStage.SuccessorPublication
	}
	if (
		fact.transitionId != current.id ||
		current.pendingCommandStages != setOf(expectedStage) ||
		ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement !in
			current.phase.contract.awaitedProofs ||
		fact.publicationIdentity != current.pendingPublicationIdentity
	) return unchanged()
	return when (val subject = fact.subject) {
		is ReaderOwnerAndInputPublicationSubject.Retained -> {
			if (subject.retainedResource != current.predecessorResourceRegistration) return unchanged()
			copy(
				active = null,
				lastOutcome = ReaderTransitionOutcome.Failed(
					fact.reason,
					ReaderTransitionRetryability.Retryable,
					current.phase.contract.retainedOwner
				),
				retryableTransition = current.toRetryableTransition()
			).let { ReaderTransitionReduction(it, emptyList()) }
		}
		is ReaderOwnerAndInputPublicationSubject.Successor -> {
			val target = current.frameTarget ?: return unchanged()
			val registration = current.successorResourceRegistration ?: return unchanged()
			if (
				current.phase.kind != ReaderTransitionPhaseKind.Committing ||
				subject.targetHandle != target.handle ||
				subject.preparedFrameResource != registration
			) return unchanged()
			ReaderTransitionReduction(
				state = copy(
					active = null,
					lastOutcome = ReaderTransitionOutcome.Failed(
						fact.reason,
						ReaderTransitionRetryability.Retryable,
						current.phase.contract.retainedOwner
					),
					retryableTransition = current.toRetryableTransition()
				),
				commands = terminalResourceCommands(current)
			)
		}
	}
}

private fun ReaderTransitionJournal.reduceOwnerProof(
	transitionId: ReaderTransitionId,
	proof: ReaderTransitionProofKind,
	binding: ReaderPresentationBinding,
	frameOwner: ReaderPresentationFrameOwner,
	resourceKey: ReaderTransitionResourceKey
): ReaderTransitionReduction {
	val current = active ?: return reduceRejectedResourceKey(resourceKey)
	if (
		current.id != transitionId ||
		proof !in current.phase.contract.awaitedProofs ||
		!current.id.expectedBinding.matches(binding) ||
		!current.id.operation.acceptsCommittedOwner(frameOwner) ||
		!frameOwner.hasBinding(binding)
	) return reduceRejectedResourceKey(resourceKey)
	val remaining = current.phase.contract.awaitedProofs - proof
	return if (remaining.isEmpty()) {
		reduceRejectedResourceKey(resourceKey)
	} else {
		ReaderTransitionReduction(
			copy(
				active = current.copy(
					preparedFrameOwner = frameOwner,
					resolvedSuccessorBinding = binding,
					successorResourceKey = resourceKey
				).withAwaitedProofs(remaining)
			),
			emptyList()
		)
	}
}

private fun ReaderTransitionJournal.reduceDeckOwned(
	fact: ReaderTransitionFact.DeckOwned
): ReaderTransitionReduction {
	val current = active ?: return reduceRejectedResourceKey(fact.key)
	if (
		fact.transitionId != current.id ||
		fact.key.owningTransitionIdOrNull != current.id ||
		fact.key.kind != ReaderTransitionResourceKind.Deck
	) return reduceRejectedResourceKey(fact.key)
	if (ReaderTransitionProofKind.DeckOwnership !in current.phase.contract.awaitedProofs) {
		return if (current.admittedDeckKey == fact.key) unchanged() else reduceRejectedResourceKey(fact.key)
	}
	if (current.pendingCommandStages != setOf(ReaderTransitionCommandStage.DeckReservation)) {
		return reduceRejectedResourceKey(fact.key)
	}
	if (
		current.admittedDeckKey?.let { it != fact.key } == true ||
		current.successorResourceKey?.takeIf { it.kind == ReaderTransitionResourceKind.Deck }
			?.let { it != fact.key } == true
	) return reduceRejectedResourceKey(fact.key)

	val pendingMatches = current.pendingPreparedDeckKey == fact.key
	val stalePending = current.pendingPreparedDeckKey?.takeIf { it != fact.key }
	val satisfied = buildSet {
		add(ReaderTransitionProofKind.DeckOwnership)
		if (pendingMatches) add(ReaderTransitionProofKind.DeckPrepared)
	}
	val remaining = current.phase.contract.awaitedProofs - satisfied
	val updated = current.copy(
		pendingCommandStages = if (
			ReaderTransitionProofKind.RendererGeneration !in remaining &&
			ReaderTransitionProofKind.DeckOwnership !in remaining &&
			ReaderTransitionProofKind.DeckPrepared !in remaining
		) {
			emptySet()
		} else {
			current.pendingCommandStages
		},
		admittedDeckKey = fact.key,
		pendingPreparedDeckKey = null
	)
	val reduction = completeOrContinue(updated, remaining)
	return if (stalePending == null) {
		reduction
	} else {
		reduction.copy(
			commands = resourceDispositionCommands(
				transitionId = current.id,
				candidates = listOf(stalePending),
				retention = ReaderTransitionResourceRetention.TruthfulPredecessor
			) + reduction.commands
		)
	}
}

private fun ReaderTransitionJournal.reduceDeckPrepared(
	fact: ReaderTransitionFact.DeckPrepared
): ReaderTransitionReduction {
	val current = active ?: return reduceRejectedResourceKey(fact.key)
	if (
		fact.transitionId != current.id ||
		fact.key.owningTransitionIdOrNull != current.id ||
		fact.key.kind != ReaderTransitionResourceKind.Deck
	) return reduceRejectedResourceKey(fact.key)
	if (ReaderTransitionProofKind.DeckPrepared !in current.phase.contract.awaitedProofs) {
		return if (current.admittedDeckKey == fact.key) unchanged() else reduceRejectedResourceKey(fact.key)
	}
	if (current.pendingCommandStages != setOf(ReaderTransitionCommandStage.DeckReservation)) {
		return reduceRejectedResourceKey(fact.key)
	}
	val admitted = current.admittedDeckKey
	if (admitted == null) {
		if (
			current.successorResourceKey?.takeIf { it.kind == ReaderTransitionResourceKind.Deck }
				?.let { it != fact.key } == true
		) return reduceRejectedResourceKey(fact.key)
		return ReaderTransitionReduction(
			copy(
				active = current.copy(pendingPreparedDeckKey = fact.key)
			),
			emptyList()
		)
	}
	if (admitted != fact.key) return reduceRejectedResourceKey(fact.key)
	val remaining = current.phase.contract.awaitedProofs - ReaderTransitionProofKind.DeckPrepared
	return completeOrContinue(
		current.copy(
			pendingCommandStages = if (
				ReaderTransitionProofKind.RendererGeneration !in remaining &&
				ReaderTransitionProofKind.DeckOwnership !in remaining
			) {
				current.pendingCommandStages - ReaderTransitionCommandStage.DeckReservation
			} else {
				current.pendingCommandStages
			}
		),
		remaining
	)
}

private fun ReaderTransitionJournal.completeOrContinue(
	current: ReaderActiveTransition,
	remaining: Set<ReaderTransitionProofKind>
): ReaderTransitionReduction {
	if (remaining.isNotEmpty()) {
		if (
			remaining == setOf(ReaderTransitionProofKind.PreparedFrame) &&
			current.frameTarget == null &&
			current.pendingFrameTargetSpecification != null &&
			current.pendingFrameTargetRegistration != null
		) {
			val awaitingTarget = current.copy(
				pendingCommandStages = emptySet(),
				phase = current.phase.copy(
					kind = ReaderTransitionPhaseKind.AwaitingProof,
					contract = current.phase.contract.copy(
						awaitedProofs = setOf(ReaderTransitionProofKind.FrameTargetPreparation),
						callbackSources = setOf(
							ReaderTransitionFactKind.FrameTargetPrepared,
							ReaderTransitionFactKind.FrameTargetPreparationRejected,
							ReaderTransitionFactKind.CommandRejected
						),
						admissibleCommandStages = admissibleStagesFor(
							ReaderTransitionCommandStage.FrameTargetPreparation
						)
					)
				)
			)
			return ReaderTransitionReduction(
				copy(active = awaitingTarget),
				listOf(
					ReaderTransitionCommand.PrepareFrameTarget(
						current.id,
						current.pendingFrameTargetSpecification,
						current.pendingFrameTargetRegistration
					)
				)
			)
		}
		return ReaderTransitionReduction(
			copy(active = current.withAwaitedProofs(remaining)),
			emptyList()
		)
	}
	val owner = current.preparedFrameOwner ?: return unchanged()
	val binding = current.resolvedSuccessorBinding ?: return unchanged()
	val registration = current.successorResourceRegistration ?: return unchanged()
	return beginSuccessorCommit(current, binding, owner, registration)
}

private fun ReaderTransitionJournal.knownResourceKeys(
	current: ReaderActiveTransition? = active,
	additional: List<ReaderTransitionResourceKey> = emptyList()
): List<ReaderTransitionResourceKey> = buildList {
	when (val authority = committed.authority()) {
		is ReaderCommittedPresentationAuthority.Retained -> add(authority.resource.key)
		is ReaderCommittedPresentationAuthority.Neutral -> Unit
	}
	current?.predecessorResourceKey?.let(::add)
	current?.ownedResourceKeys?.let(::addAll)
	current?.admittedDeckKey?.let(::add)
	current?.pendingPreparedDeckKey?.let(::add)
	current?.pendingFrameTargetRegistration?.key?.let(::add)
	current?.frameTarget?.resource?.key?.let(::add)
	current?.successorResourceKey?.let(::add)
	addAll(additional)
}

private fun ReaderTransitionJournal.resourceDispositionCommands(
	transitionId: ReaderTransitionId?,
	candidates: Iterable<ReaderTransitionResourceKey>,
	retention: ReaderTransitionResourceRetention,
	successorResourceKey: ReaderTransitionResourceKey? = null
): List<ReaderTransitionCommand.ReleaseResource> {
	val retainedKeys = when (retention) {
		ReaderTransitionResourceRetention.TruthfulPredecessor -> buildSet {
			active?.predecessorResourceKey?.let(::add)
			when (val authority = committed.authority()) {
				is ReaderCommittedPresentationAuthority.Retained -> add(authority.resource.key)
				is ReaderCommittedPresentationAuthority.Neutral -> Unit
			}
		}
		ReaderTransitionResourceRetention.Successor ->
			setOfNotNull(successorResourceKey ?: active?.successorResourceKey)
		ReaderTransitionResourceRetention.None -> emptySet()
	}
	return candidates
		.distinct()
		.filterNot { it in retainedKeys }
		.map { key ->
			ReaderTransitionCommand.ReleaseResource(
				issuerTransitionId = transitionId.takeIf {
					key.ownerId is ReaderTransitionResourceOwnerId.TransitionOwned
				},
				key = key,
				registration = when (val authority = committed.authority()) {
					is ReaderCommittedPresentationAuthority.Retained ->
						authority.resource.takeIf { it.key == key }
					is ReaderCommittedPresentationAuthority.Neutral -> null
				}
			)
		}
}

private fun ReaderTransitionJournal.reduceRejectedResourceFact(
	fact: ReaderTransitionFact
): ReaderTransitionReduction = reduceRejectedResourceKey(
	requireNotNull(fact.resourceClassificationOrNull()).key
)

private fun ReaderTransitionJournal.reduceRejectedResourceKey(
	key: ReaderTransitionResourceKey
): ReaderTransitionReduction {
	if (key in knownResourceKeys()) return unchanged()
	return ReaderTransitionReduction(
		state = this,
		commands = resourceDispositionCommands(
			transitionId = active?.id ?: key.owningTransitionIdOrNull,
			candidates = listOf(key),
			retention = ReaderTransitionResourceRetention.TruthfulPredecessor
		)
	)
}

private fun ReaderTransitionJournal.reduceProof(
	transitionId: ReaderTransitionId,
	proof: ReaderTransitionProofKind,
	factBinding: ReaderPresentationBinding?
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (
		current.id != transitionId ||
		proof !in current.phase.contract.awaitedProofs ||
		(factBinding != null && !current.id.expectedBinding.matches(factBinding)) ||
		(current.resolvedSuccessorBinding?.let { factBinding != null && it != factBinding } == true)
	) return unchanged()
	val completedStage = when (proof) {
		ReaderTransitionProofKind.SemanticDestination ->
			ReaderTransitionCommandStage.SemanticSynchronization
		ReaderTransitionProofKind.Raster -> ReaderTransitionCommandStage.RasterPreparation
		ReaderTransitionProofKind.RendererGeneration -> ReaderTransitionCommandStage.DeckReservation
		else -> null
	}
	if (completedStage != null && current.pendingCommandStages != setOf(completedStage)) {
		return unchanged()
	}
	val remaining = current.phase.contract.awaitedProofs - proof
	val commandStageComplete = when (completedStage) {
		ReaderTransitionCommandStage.DeckReservation -> remaining.none {
			it == ReaderTransitionProofKind.RendererGeneration ||
				it == ReaderTransitionProofKind.DeckOwnership ||
				it == ReaderTransitionProofKind.DeckPrepared
		}
		null -> false
		else -> true
	}
	val resolved = current.copy(
		pendingCommandStages = if (commandStageComplete) {
			current.pendingCommandStages - requireNotNull(completedStage)
		} else {
			current.pendingCommandStages
		},
		resolvedSuccessorBinding = factBinding ?: current.resolvedSuccessorBinding,
		authoritativeDestinationCommitted = current.authoritativeDestinationCommitted ||
			(proof == ReaderTransitionProofKind.SemanticDestination && factBinding != null)
	)
	val commands = when (proof) {
		ReaderTransitionProofKind.SemanticDestination -> factBinding?.let { binding ->
			listOf(
				ReaderTransitionCommand.AllocateMaterialBinding(
					current.id,
					binding.withoutMaterialGenerations()
				)
			)
		} ?: emptyList()
		ReaderTransitionProofKind.Raster -> resolved.materialAllocation?.takeIf {
			ReaderTransitionProofKind.DeckOwnership in remaining ||
				ReaderTransitionProofKind.DeckPrepared in remaining
		}?.let { allocation ->
			listOf(
				ReaderTransitionCommand.ReserveDeck(
					transitionId = current.id,
					binding = allocation.allocatedBinding,
					role = current.id.operation.deckRole(),
					allocation = allocation
				)
			)
		} ?: emptyList()
		else -> emptyList()
	}
	val continuation = completeOrContinue(resolved, remaining)
	return continuation.copy(commands = commands + continuation.commands)
}

private fun ReaderTransitionJournal.succeedAfterPublication(
	current: ReaderActiveTransition,
	binding: ReaderPresentationBinding,
	committedOwner: ReaderPresentationFrameOwner,
	successorRegistration: ReaderTransitionResourceRegistration
): ReaderTransitionReduction {
	check(current.id.operation.acceptsCommittedOwner(committedOwner))
	check(committedOwner.hasBinding(binding))
	check(successorRegistration.key.ownerId == ReaderTransitionResourceOwnerId.TransitionOwned(current.id))
	check(successorRegistration.key.kind == committedOwner.resourceKind())
	val commands = resourceDispositionCommands(
		transitionId = current.id,
		candidates = knownResourceKeys(current),
		retention = ReaderTransitionResourceRetention.Successor,
		successorResourceKey = successorRegistration.key
	)
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Succeeded(
				committedOwner = committedOwner,
				binding = binding
			),
			committed = ReaderCommittedPresentation.Transition(
				ReaderCommittedTransition(
					current.id,
					committedOwner,
					binding,
					successorRegistration.key,
					successorRegistration
				)
			),
			retryableTransition = null
		),
		commands = commands
	)
}

private fun ReaderTransitionJournal.reduceRasterFailure(
	fact: ReaderTransitionFact.RasterFailed
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (
		current.id != fact.transitionId ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.RasterPreparation)
	) return unchanged()
	return reduceFailure(fact.transitionId, fact.reason)
}

private fun ReaderTransitionJournal.reduceRasterDeferral(
	fact: ReaderTransitionFact.RasterDeferred
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (
		current.id != fact.transitionId ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.RasterPreparation)
	) return unchanged()
	return reduceDeferral(fact.transitionId, fact.reason, fact.resumeRecord)
}

private fun ReaderTransitionJournal.reduceDeckRejected(
	fact: ReaderTransitionFact.DeckRejected
): ReaderTransitionReduction {
	val current = active
	if (
		current?.id != fact.transitionId ||
		fact.key.owningTransitionIdOrNull != fact.transitionId ||
		fact.key.kind != ReaderTransitionResourceKind.Deck ||
		current.pendingCommandStages != setOf(ReaderTransitionCommandStage.DeckReservation)
	) return reduceRejectedResourceFact(fact)
	return reduceFailure(
		transitionId = fact.transitionId,
		reason = ReaderTransitionFailureReason.PortRejected,
		suppliedResources = listOf(fact.key)
	)
}

private fun ReaderTransitionJournal.reduceTimeout(
	transitionId: ReaderTransitionId
): ReaderTransitionReduction {
	val current = active?.takeIf { it.id == transitionId } ?: return unchanged()
	val liveness = ReaderTransitionLivenessTable.forOperation(current.id.operation)
	val outcome = ReaderTransitionOutcome.Failed(
		liveness.timeoutFailureReason,
		liveness.timeoutRetryability,
		current.phase.contract.retainedOwner
	)
	if (current.id.operation == ReaderTransitionOperation.PublicationClose) {
		return terminatePublication(
			reason = ReaderTransitionCancellationReason.PublicationClosed,
			trigger = ReaderReleaseOnlyCleanupTrigger.PublicationClose,
			closeOperationId = current.id,
			outcomeOverride = outcome,
			deadlineStatus = ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed
		)
	}
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = outcome,
			retryableTransition = current.toRetryableTransition()
		),
		commands = terminalResourceCommands(current)
	)
}

private fun ReaderTransitionJournal.reduceCommandRejected(
	fact: ReaderTransitionFact.CommandRejected
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (
		current.id != fact.transitionId ||
		current.pendingCommandStages != setOf(fact.stage)
	) return unchanged()
	if (
		current.id.operation == ReaderTransitionOperation.PublicationClose &&
		fact.stage == ReaderTransitionCommandStage.TimerBinding
	) {
		return terminatePublication(
			reason = ReaderTransitionCancellationReason.PublicationClosed,
			trigger = ReaderReleaseOnlyCleanupTrigger.PublicationClose,
			closeOperationId = current.id,
			outcomeOverride = ReaderTransitionOutcome.Failed(
				reason = fact.reason.toFailureReason(),
				retryability = ReaderTransitionRetryability.NonRetryable,
				retainedOwner = current.phase.contract.retainedOwner
			),
			deadlineStatus = if (
				fact.reason == ReaderTransitionCommandRejectionReason.CommandThrew
			) {
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
			} else {
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected
			}
		)
	}
	return reduceFailure(
		transitionId = fact.transitionId,
		reason = fact.reason.toFailureReason()
	)
}

private fun ReaderTransitionCommandRejectionReason.toFailureReason(): ReaderTransitionFailureReason =
	when (this) {
		ReaderTransitionCommandRejectionReason.MaterialAllocationRejected ->
			ReaderTransitionFailureReason.MaterialAllocationRejected
		ReaderTransitionCommandRejectionReason.PublicationRejected ->
			ReaderTransitionFailureReason.AtomicPublicationRejected
		else -> ReaderTransitionFailureReason.PortRejected
	}

private fun ReaderTransitionJournal.reduceSemanticPortContractViolation(
	fact: ReaderTransitionFact.SemanticPortContractViolated
): ReaderTransitionReduction = reduceSemanticPortContractViolation(
	fact = fact,
	trustedSemanticAuthority = null,
	trustedInvocation = false
)

internal fun ReaderTransitionJournal.reduceTrustedSemanticPortContractViolation(
	fact: ReaderTransitionFact.SemanticPortContractViolated,
	authority: ReaderReleaseOnlySemanticAuthority?
): ReaderTransitionReduction = reduceSemanticPortContractViolation(
	fact = fact,
	trustedSemanticAuthority = authority,
	trustedInvocation = true
)

internal fun ReaderTransitionJournal.retainTrustedSemanticAuthority(
	authority: ReaderReleaseOnlySemanticAuthority
): ReaderTransitionJournal {
	val cleanup = releaseOnlyCleanup ?: return this
	if (cleanup.authoritativeSemanticDestination == authority) return this
	return copy(
		releaseOnlyCleanup = cleanup.copy(authoritativeSemanticDestination = authority)
	)
}

private fun ReaderTransitionJournal.reduceSemanticPortContractViolation(
	fact: ReaderTransitionFact.SemanticPortContractViolated,
	trustedSemanticAuthority: ReaderReleaseOnlySemanticAuthority?,
	trustedInvocation: Boolean
): ReaderTransitionReduction {
	if (releaseOnlyCleanup != null) {
		return if (trustedInvocation && trustedSemanticAuthority != null) {
			ReaderTransitionReduction(
				retainTrustedSemanticAuthority(trustedSemanticAuthority),
				emptyList()
			)
		} else {
			unchanged()
		}
	}
	val exactRetainedOwner = active?.takeIf { it.id == fact.transitionId }
		?.phase
		?.contract
		?.retainedOwner
		?: retryableTransition?.takeIf { it.id == fact.transitionId }?.retainedOwner
		?: (committed as? ReaderCommittedPresentation.Transition)
			?.committed
			?.takeIf { it.id == fact.transitionId }
			?.owner
	val retainedOwner = exactRetainedOwner ?: if (trustedInvocation) {
		active?.phase?.contract?.retainedOwner ?: when (val authority = committed.authority()) {
			is ReaderCommittedPresentationAuthority.Retained -> authority.owner
			is ReaderCommittedPresentationAuthority.Neutral -> ReaderPresentationFrameOwner.Neutral
		}
	} else {
		return unchanged()
	}
	return terminatePublication(
		reason = ReaderTransitionCancellationReason.PublicationClosed,
		trigger = ReaderReleaseOnlyCleanupTrigger.ProcessClose,
		outcomeOverride = ReaderTransitionOutcome.Failed(
			reason = ReaderTransitionFailureReason.PortRejected,
			retryability = ReaderTransitionRetryability.NonRetryable,
			retainedOwner = retainedOwner
		),
		authoritativeSemanticDestination = trustedSemanticAuthority
	)
}

private fun ReaderTransitionJournal.reduceFailure(
	transitionId: ReaderTransitionId,
	reason: ReaderTransitionFailureReason,
	suppliedResources: List<ReaderTransitionResourceKey> = emptyList()
): ReaderTransitionReduction {
	val current = active?.takeIf { it.id == transitionId }
		?: return suppliedResources.firstOrNull()?.let(::reduceRejectedResourceKey) ?: unchanged()
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Failed(
				reason,
				ReaderTransitionRetryability.Retryable,
				current.phase.contract.retainedOwner
			),
			retryableTransition = current.toRetryableTransition()
		),
		commands = terminalResourceCommands(current, suppliedResources)
	)
}

private fun ReaderTransitionJournal.reduceDeferral(
	transitionId: ReaderTransitionId,
	reason: ReaderTransitionDeferralReason,
	resumeRecord: ReaderTransitionResumeRecord
): ReaderTransitionReduction {
	val current = active?.takeIf { it.id == transitionId } ?: return unchanged()
	check(resumeRecord.operation == current.id.operation)
	check(resumeRecord.reason == reason)
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Deferred(
				resumeRecord = resumeRecord,
				retainedOwner = current.phase.contract.retainedOwner
			),
			retryableTransition = null
		),
		commands = terminalResourceCommands(current)
	)
}

private fun ReaderActiveTransition.toRetryableTransition() = ReaderRetryableTransition(
	id = id,
	retainedOwner = phase.contract.retainedOwner,
	predecessorResourceKey = predecessorResourceKey,
	semanticIntent = semanticIntent,
	resolvedSuccessorBinding = resolvedSuccessorBinding,
	gestureId = (phase.contract.inputLease as? ReaderTransitionInputLease.ClaimedGesture)?.gestureId,
	settlementConsumed = consumedSettlement != null,
	semanticDestinationCommitted = authoritativeDestinationCommitted
)

private fun ReaderTransitionJournal.terminalResourceCommands(
	current: ReaderActiveTransition,
	suppliedResources: List<ReaderTransitionResourceKey> = emptyList()
): List<ReaderTransitionCommand> = buildList {
	add(ReaderTransitionCommand.CancelOwnedWork(current.id))
	addAll(
		resourceDispositionCommands(
			transitionId = current.id,
			candidates = knownResourceKeys(current, suppliedResources),
			retention = ReaderTransitionResourceRetention.TruthfulPredecessor
		)
	)
}

private fun ReaderTransitionJournal.reducePublicationReplacement(
	transitionId: ReaderTransitionId?
): ReaderTransitionReduction {
	val current = active
	if (transitionId != null && current?.id != transitionId) return unchanged()
	return terminatePublication(
		reason = ReaderTransitionCancellationReason.PublicationReplaced,
		trigger = ReaderReleaseOnlyCleanupTrigger.PublicationReplacement
	)
}

private fun ReaderTransitionJournal.reducePublicationClose(
	transitionId: ReaderTransitionId?
): ReaderTransitionReduction {
	val current = active
	if (transitionId != null) {
		if (
			current == null ||
			current.id != transitionId ||
			current.id.operation != ReaderTransitionOperation.PublicationClose
		) return unchanged()
	}
	return terminatePublication(
		reason = ReaderTransitionCancellationReason.PublicationClosed,
		trigger = ReaderReleaseOnlyCleanupTrigger.PublicationClose,
		closeOperationId = current?.id?.takeIf {
			it.operation == ReaderTransitionOperation.PublicationClose
		}
	)
}

private fun ReaderTransitionJournal.terminatePublication(
	reason: ReaderTransitionCancellationReason,
	trigger: ReaderReleaseOnlyCleanupTrigger,
	closeOperationId: ReaderTransitionId? = null,
	outcomeOverride: ReaderTransitionOutcome? = null,
	deadlineStatus: ReaderReleaseOnlyCleanupDeadlineStatus =
		ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
	authoritativeSemanticDestination: ReaderReleaseOnlySemanticAuthority? = null
): ReaderTransitionReduction {
	if (releaseOnlyCleanup != null) return unchanged()
	val current = active
	val preCloseTransitionId = current?.id?.takeUnless { it == closeOperationId }
	val cleanupValue = maxOf(lastTransitionSequence, lastPublicationSequence).let { value ->
		require(value < Long.MAX_VALUE)
		value + 1L
	}
	val cleanupKey = ReaderReleaseOnlyCleanupKey(
		readerSessionGeneration = committed.readerSessionGeneration,
		coordinatorEpoch = committed.coordinatorEpoch,
		cleanupId = ReaderReleaseOnlyCleanupId(cleanupValue),
		generation = ReaderReleaseOnlyCleanupGeneration(cleanupValue)
	)
	val cleanup = ReaderReleaseOnlyCleanupState(
		key = cleanupKey,
		trigger = trigger,
		closeOperationId = closeOperationId,
		preCloseTransitionId = preCloseTransitionId,
		authoritativeSemanticDestination = authoritativeSemanticDestination ?: current
			?.resolvedSuccessorBinding
			?.takeIf {
				current.authoritativeDestinationCommitted || current.consumedSettlement != null
			}
			?.let(::ReaderReleaseOnlySemanticAuthority),
		cancellationStatus = if (preCloseTransitionId == null) {
			ReaderReleaseOnlyCancellationStatus.NotRequired
		} else {
			ReaderReleaseOnlyCancellationStatus.Pending
		},
		deadlineStatus = deadlineStatus
	)
	val commandOwnerId = preCloseTransitionId ?: retryableTransition?.id ?: when (val state = committed) {
		is ReaderCommittedPresentation.Initial -> null
		is ReaderCommittedPresentation.Transition -> state.committed.id
	}
	val resources = knownResourceKeys(current)
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			releaseOnlyCleanup = cleanup,
			lastOutcome = outcomeOverride ?: ReaderTransitionOutcome.Cancelled(
				reason,
				ReaderPresentationFrameOwner.Neutral
			),
			retryableTransition = null
		),
		commands = buildList {
			preCloseTransitionId?.let {
				add(ReaderTransitionCommand.CancelOwnedWork(it, cleanupKey))
			}
			addAll(
				resourceDispositionCommands(
					transitionId = commandOwnerId,
					candidates = resources,
					retention = ReaderTransitionResourceRetention.None
				)
			)
		}
	)
}

private fun ReaderActiveTransition.withAwaitedProofs(
	awaitedProofs: Set<ReaderTransitionProofKind>,
	preparedFrameOwner: ReaderPresentationFrameOwner? = this.preparedFrameOwner
): ReaderActiveTransition {
	require(awaitedProofs.isNotEmpty())
	return copy(
		phase = phase.copy(
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			contract = phase.contract.copy(awaitedProofs = awaitedProofs)
		),
		preparedFrameOwner = preparedFrameOwner
	)
}

private fun ReaderTransitionJournal.unchanged() = ReaderTransitionReduction(this, emptyList())

private fun ReaderExpectedPresentationBinding.matches(binding: ReaderPresentationBinding): Boolean = when (this) {
	is ReaderExpectedPresentationBinding.Exact -> this.binding == binding
	is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial -> true
	is ReaderExpectedPresentationBinding.NoCommittedPresentation -> false
	is ReaderExpectedPresentationBinding.SemanticSuccessor ->
		binding != predecessor &&
		binding.foliateSessionId == predecessor.foliateSessionId &&
		binding.publicationGeneration == predecessor.publicationGeneration
}

internal fun ReaderExpectedPresentationBinding.acceptsMaterialAllocation(
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	is ReaderExpectedPresentationBinding.Exact -> this.binding.withoutMaterialGenerations() ==
		binding.withoutMaterialGenerations()
	is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial -> true
	is ReaderExpectedPresentationBinding.NoCommittedPresentation -> false
	is ReaderExpectedPresentationBinding.SemanticSuccessor -> matches(binding)
}

internal fun readerTransitionMaterialBindingIsValid(
	transitionId: ReaderTransitionId,
	binding: ReaderPresentationBinding,
	allocation: ReaderMaterialGenerationAllocation?
): Boolean = if (allocation == null) {
	transitionId.expectedBinding == ReaderExpectedPresentationBinding.Exact(binding)
} else {
	allocation.transitionId == transitionId &&
		allocation.allocatedBinding == binding &&
		transitionId.expectedBinding.acceptsMaterialAllocation(binding)
}

private fun ReaderPresentationBinding.matchesAllocation(other: ReaderPresentationBinding): Boolean =
	withoutMaterialGenerations() == other.withoutMaterialGenerations()

private fun ReaderPresentationBinding.withoutMaterialGenerations(): ReaderPresentationBinding = copy(
	preparationGeneration = null,
	rasterGeneration = null,
	textureGeneration = null
)

private fun ReaderExpectedPresentationBinding.exactBindingOrNull(): ReaderPresentationBinding? = when (this) {
	is ReaderExpectedPresentationBinding.Exact -> binding
	is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial,
	is ReaderExpectedPresentationBinding.NoCommittedPresentation,
	is ReaderExpectedPresentationBinding.SemanticSuccessor -> null
}

private fun ReaderTransitionOperation.deckRole(): ReaderTransitionDeckRole = when (this) {
	ReaderTransitionOperation.BootstrapNativePage -> ReaderTransitionDeckRole.Initial
	ReaderTransitionOperation.CoverToPageEntry -> ReaderTransitionDeckRole.PageEntry
	ReaderTransitionOperation.CurlClaimAndSettlement,
	ReaderTransitionOperation.ExternalSemanticRelocation -> ReaderTransitionDeckRole.Settlement
	ReaderTransitionOperation.ReflowProfileReplacement -> ReaderTransitionDeckRole.Reflow
	ReaderTransitionOperation.VisibilityRestore,
	ReaderTransitionOperation.RendererRecovery,
	ReaderTransitionOperation.ShellCoverCommit,
	ReaderTransitionOperation.NativeToLiveHandoff,
	ReaderTransitionOperation.LiveToNativeHandback,
	ReaderTransitionOperation.PublicationClose -> ReaderTransitionDeckRole.Recovery
}

private fun ReaderTransitionDeferralReason.wakeKind(): ReaderTransitionWakeKind = when (this) {
	ReaderTransitionDeferralReason.VisibilityRestore -> ReaderTransitionWakeKind.VisibilityRestored
	ReaderTransitionDeferralReason.HostUnavailable -> ReaderTransitionWakeKind.HostAvailable
	ReaderTransitionDeferralReason.WebViewUnavailable -> ReaderTransitionWakeKind.WebViewAvailable
	ReaderTransitionDeferralReason.PaginationUnavailable -> ReaderTransitionWakeKind.PaginationProfileReady
	ReaderTransitionDeferralReason.RendererCapacityUnavailable ->
		ReaderTransitionWakeKind.RendererCapacityAvailable
}

private fun ReaderTransitionFrameTarget.requiredResourceKind(): ReaderTransitionResourceKind =
	specification.requiredResourceKind()

private fun ReaderTransitionFrameTargetSpecification.requiredResourceKind(): ReaderTransitionResourceKind =
	when (this) {
		is ReaderTransitionFrameTargetSpecification.ShellCover,
		is ReaderTransitionFrameTargetSpecification.LiveWebView -> ReaderTransitionResourceKind.FrameHandoff
		is ReaderTransitionFrameTargetSpecification.NativePage,
		is ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame -> ReaderTransitionResourceKind.Deck
	}

private fun ReaderTransitionJournal.nextPublicationIdentity(): ReaderOwnerAndInputPublicationIdentity {
	require(lastPublicationSequence < Long.MAX_VALUE)
	return ReaderOwnerAndInputPublicationIdentity(lastPublicationSequence + 1L)
}

private fun readerInputLeaseIsNoBroaderThan(
	physical: ReaderTransitionInputLease,
	requested: ReaderTransitionInputLease
): Boolean = physical == requested || physical == ReaderTransitionInputLease.None ||
	(physical == ReaderTransitionInputLease.ChromeOnly && requested != ReaderTransitionInputLease.None)

private fun readerInitialLeaseIsNoBroaderThan(
	physical: ReaderInitialPresentationInputLease,
	requested: ReaderInitialPresentationInputLease
): Boolean = physical == requested || physical == ReaderInitialPresentationInputLease.None ||
	(physical == ReaderInitialPresentationInputLease.ChromeOnly &&
		requested != ReaderInitialPresentationInputLease.None)

private fun readerInitialLeaseIsCompatibleWithOwner(
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding,
	lease: ReaderInitialPresentationInputLease
): Boolean = when (owner) {
	ReaderPresentationFrameOwner.Neutral ->
		lease == ReaderInitialPresentationInputLease.None ||
			lease == ReaderInitialPresentationInputLease.ChromeOnly
	is ReaderPresentationFrameOwner.ShellCover ->
		lease == ReaderInitialPresentationInputLease.None ||
			lease == ReaderInitialPresentationInputLease.ChromeOnly ||
			lease == ReaderInitialPresentationInputLease.CoverActions
	is ReaderPresentationFrameOwner.NativePage -> when (lease) {
		ReaderInitialPresentationInputLease.None,
		ReaderInitialPresentationInputLease.ChromeOnly -> true
		is ReaderInitialPresentationInputLease.NativePage ->
			lease.binding == binding && lease.textureGeneration == owner.proof.textureGeneration
		ReaderInitialPresentationInputLease.CoverActions -> false
	}
	is ReaderPresentationFrameOwner.Curl,
	is ReaderPresentationFrameOwner.LiveEngine ->
		lease == ReaderInitialPresentationInputLease.None ||
			lease == ReaderInitialPresentationInputLease.ChromeOnly
}

private fun ReaderPresentationFrameOwner.hasBinding(
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	ReaderPresentationFrameOwner.Neutral -> false
	is ReaderPresentationFrameOwner.ShellCover -> proof.binding == binding
	is ReaderPresentationFrameOwner.NativePage -> proof.binding == binding
	is ReaderPresentationFrameOwner.Curl -> frame.binding == binding
	is ReaderPresentationFrameOwner.LiveEngine -> proof.binding == binding
}

private fun ReaderPresentationFrameOwner.resourceKind(): ReaderTransitionResourceKind = when (this) {
	ReaderPresentationFrameOwner.Neutral -> error("Neutral owner has no presentation resource")
	is ReaderPresentationFrameOwner.ShellCover,
	is ReaderPresentationFrameOwner.LiveEngine -> ReaderTransitionResourceKind.FrameHandoff
	is ReaderPresentationFrameOwner.NativePage,
	is ReaderPresentationFrameOwner.Curl -> ReaderTransitionResourceKind.Deck
}

private fun ReaderPresentationFrameOwner.committedInputLease(
	binding: ReaderPresentationBinding
): ReaderTransitionInputLease = when (this) {
	ReaderPresentationFrameOwner.Neutral -> ReaderTransitionInputLease.None
	is ReaderPresentationFrameOwner.ShellCover -> ReaderTransitionInputLease.CoverActions
	is ReaderPresentationFrameOwner.NativePage -> ReaderTransitionInputLease.NativePage(
		binding,
		proof.textureGeneration
	)
	is ReaderPresentationFrameOwner.Curl,
	is ReaderPresentationFrameOwner.LiveEngine -> ReaderTransitionInputLease.None
}

private fun ReaderTransitionOperation.acceptsCommittedOwner(
	owner: ReaderPresentationFrameOwner
): Boolean = when (this) {
	ReaderTransitionOperation.ShellCoverCommit -> owner is ReaderPresentationFrameOwner.ShellCover
	ReaderTransitionOperation.NativeToLiveHandoff -> owner is ReaderPresentationFrameOwner.LiveEngine
	ReaderTransitionOperation.BootstrapNativePage,
	ReaderTransitionOperation.CoverToPageEntry,
	ReaderTransitionOperation.CurlClaimAndSettlement,
	ReaderTransitionOperation.LiveToNativeHandback,
	ReaderTransitionOperation.ExternalSemanticRelocation,
	ReaderTransitionOperation.ReflowProfileReplacement,
	ReaderTransitionOperation.VisibilityRestore,
	ReaderTransitionOperation.RendererRecovery -> owner is ReaderPresentationFrameOwner.NativePage
	ReaderTransitionOperation.PublicationClose -> owner is ReaderPresentationFrameOwner.Neutral
}

private fun ReaderTransitionJournal.nextTransitionSequence(): Long = maxOf(
	lastTransitionSequence,
	active?.id?.sequence ?: 0L,
	(committed as? ReaderCommittedPresentation.Transition)?.committed?.id?.sequence ?: 0L,
	retryableTransition?.id?.sequence ?: 0L
).incrementTransitionSequence()

private fun Long.incrementTransitionSequence(): Long {
	require(this < Long.MAX_VALUE)
	return this + 1L
}
