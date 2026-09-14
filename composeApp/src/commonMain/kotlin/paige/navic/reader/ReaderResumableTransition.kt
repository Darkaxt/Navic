package paige.navic.reader

data class ReaderTransitionParentIdentity(
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	val sequence: Long
)

sealed interface ReaderExpectedPresentationBinding {
	data class Exact(
		val binding: ReaderPresentationBinding
	) : ReaderExpectedPresentationBinding

	data class SemanticSuccessor(
		val predecessor: ReaderPresentationBinding,
		val requestSequence: Long
	) : ReaderExpectedPresentationBinding {
		init {
			require(requestSequence > 0L)
		}
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
	}
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
	PreparedFrame,
	CoverPostDraw,
	WebViewExposure,
	VisibilityChanged,
	ResourceLost,
	DeadlineExpired,
	Retry,
	PublicationReplaced,
	PublicationClosed
}

data class ReaderTransitionPhaseContract(
	val awaitedProofs: Set<ReaderTransitionProofKind>,
	val deadlineOwner: ReaderTransitionId,
	val supersession: ReaderTransitionSupersession,
	val retainedOwner: ReaderPresentationFrameOwner,
	val inputLease: ReaderTransitionInputLease,
	val callbackSources: Set<ReaderTransitionFactKind>
) {
	init {
		require(awaitedProofs.isNotEmpty())
		require(callbackSources.isNotEmpty())
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

enum class ReaderTransitionCapabilityKind { Host, WebView, Renderer }

data class ReaderTransitionResourceKey(
	val transitionId: ReaderTransitionId,
	val kind: ReaderTransitionResourceKind,
	val opaqueId: Long
)

enum class ReaderTransitionProofKind {
	SemanticDestination,
	SettlementAcknowledgement,
	HostAvailable,
	PaginationProfile,
	RendererGeneration,
	Raster,
	DeckOwnership,
	DeckPrepared,
	PreparedFrame,
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

sealed interface ReaderTransitionUserIntent
sealed interface ReaderSemanticSynchronizationIntent : ReaderTransitionUserIntent

data class ReaderPageTurnIntent(
	val direction: ReaderPageTurnDirection,
	val gestureId: ReaderTransitionGestureId
) : ReaderSemanticSynchronizationIntent

data class ReaderExternalRelocationIntent(
	val source: ReaderExternalRelocationSource
) : ReaderSemanticSynchronizationIntent

data object ReaderCoverEntryIntent : ReaderSemanticSynchronizationIntent
data object ReaderCoverReturnIntent : ReaderTransitionUserIntent
data object ReaderRetryIntent : ReaderTransitionUserIntent
data object ReaderCancelIntent : ReaderTransitionUserIntent

sealed interface ReaderTransitionFrameRequest {
	data class ShellCover(val binding: ReaderPresentationBinding) : ReaderTransitionFrameRequest
	data class NativePage(val binding: ReaderPresentationBinding) : ReaderTransitionFrameRequest
	data class LiveExposure(val binding: ReaderPresentationBinding) : ReaderTransitionFrameRequest
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
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
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

	data class PreparedFrame(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val frameOwner: ReaderPresentationFrameOwner,
		val resourceKey: ReaderTransitionResourceKey
	) : ReaderTransitionFact {
		init {
			require(frameOwner.hasBinding(binding))
			require(resourceKey.transitionId == transitionId)
			require(resourceKey.kind == frameOwner.resourceKind())
		}
	}
	data class CoverPostDraw(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val frameOwner: ReaderPresentationFrameOwner,
		val resourceKey: ReaderTransitionResourceKey
	) : ReaderTransitionFact {
		init {
			require(frameOwner is ReaderPresentationFrameOwner.ShellCover)
			require(frameOwner.hasBinding(binding))
			require(resourceKey.transitionId == transitionId)
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
			require(resourceKey.transitionId == transitionId)
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
	data class Retry(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
	data class PublicationReplaced(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
	data class PublicationClosed(override val transitionId: ReaderTransitionId?) : ReaderTransitionFact
}

sealed interface ReaderTransitionCommand {
	val transitionId: ReaderTransitionId

	data class RequestSemanticSynchronization(
		override val transitionId: ReaderTransitionId,
		val intent: ReaderSemanticSynchronizationIntent
	) : ReaderTransitionCommand

	data class RequestRasterPreparation(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding
	) : ReaderTransitionCommand

	data class ReserveDeck(
		override val transitionId: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val role: ReaderTransitionDeckRole
	) : ReaderTransitionCommand

	data class RequestFramePresentation(
		override val transitionId: ReaderTransitionId,
		val request: ReaderTransitionFrameRequest
	) : ReaderTransitionCommand

	data class ApplyInputLease(
		override val transitionId: ReaderTransitionId,
		val lease: ReaderTransitionInputLease
	) : ReaderTransitionCommand

	data class ReleaseResource(
		override val transitionId: ReaderTransitionId,
		val key: ReaderTransitionResourceKey
	) : ReaderTransitionCommand

	data class CancelOwnedWork(
		override val transitionId: ReaderTransitionId
	) : ReaderTransitionCommand
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

object ReaderTransitionLivenessTable {
	fun forOperation(operation: ReaderTransitionOperation): ReaderTransitionLiveness = when (operation) {
		ReaderTransitionOperation.BootstrapNativePage -> materialLiveness(
			operation,
			ReaderTransitionFailureReason.MaterialTimeout,
			setOf(ReaderTransitionWakeKind.Retry)
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
		return ReaderTransitionPhase(
			kind = kind,
			contract = ReaderTransitionPhaseContract(
				awaitedProofs = awaitedProofs,
				deadlineOwner = id,
				supersession = liveness.supersession,
				retainedOwner = retainedOwner,
				inputLease = inputLeaseFor(id, gestureId),
				callbackSources = liveness.callbackSources
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
		ReaderTransitionProofKind.Raster,
		ReaderTransitionProofKind.DeckOwnership,
		ReaderTransitionProofKind.DeckPrepared,
		ReaderTransitionProofKind.PreparedFrame
	)

	private val materialCallbackSources = setOf(
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
	val preparedFrameOwner: ReaderPresentationFrameOwner? = null,
	val resolvedSuccessorBinding: ReaderPresentationBinding? = null,
	val predecessorResourceKey: ReaderTransitionResourceKey? = null,
	val ownedResourceKeys: Set<ReaderTransitionResourceKey> = emptySet(),
	val admittedDeckKey: ReaderTransitionResourceKey? = null,
	val pendingPreparedDeckKey: ReaderTransitionResourceKey? = null,
	val successorResourceKey: ReaderTransitionResourceKey? = null,
	val consumedSettlement: ReaderSettlementConsumptionKey? = null,
	val semanticIntent: ReaderSemanticSynchronizationIntent? = null,
	val authoritativeDestinationCommitted: Boolean = false
) {
	init {
		require(predecessorResourceKey == null || predecessorResourceKey.transitionId != id)
		require(ownedResourceKeys.all { it.transitionId == id })
		require(admittedDeckKey == null || admittedDeckKey.transitionId == id)
		require(pendingPreparedDeckKey == null || pendingPreparedDeckKey.kind == ReaderTransitionResourceKind.Deck)
		require(successorResourceKey == null || successorResourceKey.transitionId == id)
		require(consumedSettlement == null || consumedSettlement.transitionId == id)
		require(!authoritativeDestinationCommitted || resolvedSuccessorBinding != null)
	}
}

data class ReaderCommittedTransition(
	val id: ReaderTransitionId,
	val owner: ReaderPresentationFrameOwner,
	val binding: ReaderPresentationBinding,
	val resourceKey: ReaderTransitionResourceKey
) {
	init {
		require(owner.hasBinding(binding))
		require(resourceKey.transitionId == id)
		require(resourceKey.kind == owner.resourceKind())
	}
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
	val lastOutcome: ReaderTransitionOutcome? = null,
	val committed: ReaderCommittedTransition? = null,
	val retryableTransition: ReaderRetryableTransition? = null,
	val lastTransitionSequence: Long = 0L
) {
	fun reduce(
		fact: ReaderTransitionFact,
		nowMillis: Long = 0L
	): ReaderTransitionReduction = readerTransitionJournalReduce(this, fact, nowMillis)
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
		is ReaderTransitionFact.PreparedFrame -> ReaderTransitionResourceFactClassification(
			resourceKey,
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
		is ReaderTransitionFact.Retry,
		is ReaderTransitionFact.PublicationReplaced,
		is ReaderTransitionFact.PublicationClosed -> null
	}

fun readerTransitionJournalReduce(
	journal: ReaderTransitionJournal,
	fact: ReaderTransitionFact,
	nowMillis: Long
): ReaderTransitionReduction = when (fact) {
	is ReaderTransitionFact.Intent -> journal.reduceIntent(fact)
	is ReaderTransitionFact.SettlementAcknowledged -> journal.reduceSettlement(fact)
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
	is ReaderTransitionFact.PreparedFrame -> journal.reducePreparedFrame(fact)
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
	is ReaderTransitionFact.RasterDeferred -> journal.reduceDeferral(
		fact.transitionId,
		fact.reason,
		fact.resumeRecord
	)
	is ReaderTransitionFact.RasterFailed -> journal.reduceFailure(fact.transitionId, fact.reason)
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
	val predecessor = committed ?: return unchanged()
	val parentId = current?.id ?: predecessor.id
	val nextSequence = nextTransitionSequence()
	val nextId = ReaderTransitionId(
		readerSessionGeneration = parentId.readerSessionGeneration,
		coordinatorEpoch = parentId.coordinatorEpoch,
		sequence = nextSequence,
		operation = intent.operation(),
		expectedBinding = ReaderExpectedPresentationBinding.SemanticSuccessor(
			predecessor = predecessor.binding,
			requestSequence = nextSequence
		),
		parent = parentId.parentIdentity()
	)
	val retainedOwner = current?.phase?.contract?.retainedOwner ?: predecessor.owner
	val predecessorKey = current?.predecessorResourceKey ?: predecessor.resourceKey
	val gestureId = (intent as? ReaderPageTurnIntent)?.gestureId
	val next = ReaderActiveTransition(
		id = nextId,
		phase = ReaderTransitionLivenessTable.phase(
			id = nextId,
			kind = ReaderTransitionPhaseKind.Accepted,
			retainedOwner = retainedOwner,
			gestureId = gestureId
		),
		predecessorResourceKey = predecessorKey,
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
			lastTransitionSequence = nextSequence
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
			add(ReaderTransitionCommand.ApplyInputLease(nextId, next.phase.contract.inputLease))
			add(ReaderTransitionCommand.RequestSemanticSynchronization(nextId, intent))
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
			parent = retryable.id.parentIdentity()
		)
		authoritativeBinding != null -> retryable.id.copy(
			sequence = nextSequence,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(authoritativeBinding),
			parent = retryable.id.parentIdentity()
		)
		else -> retryable.id.copy(
			sequence = nextSequence,
			parent = retryable.id.parentIdentity()
		)
	}
	val satisfiedProofs = if (retryable.semanticDestinationCommitted) {
		setOf(ReaderTransitionProofKind.SemanticDestination)
	} else {
		emptySet()
	}
	val next = ReaderActiveTransition(
		id = nextId,
		phase = ReaderTransitionLivenessTable.phase(
			id = nextId,
			kind = ReaderTransitionPhaseKind.Accepted,
			retainedOwner = retryable.retainedOwner,
			satisfiedProofs = satisfiedProofs,
			gestureId = null
		),
		resolvedSuccessorBinding = authoritativeBinding,
		predecessorResourceKey = retryable.predecessorResourceKey,
		semanticIntent = retryable.semanticIntent.takeIf { authoritativeBinding == null },
		authoritativeDestinationCommitted = authoritativeBinding != null
	)
	val consequence = when {
		nextId.operation == ReaderTransitionOperation.RendererRecovery &&
			authoritativeBinding != null -> ReaderTransitionCommand.ReserveDeck(
			transitionId = nextId,
			binding = authoritativeBinding,
			role = ReaderTransitionDeckRole.Recovery
		)
		authoritativeBinding != null -> ReaderTransitionCommand.RequestRasterPreparation(
			nextId,
			authoritativeBinding
		)
		else -> retryable.semanticIntent?.let { intent ->
			ReaderTransitionCommand.RequestSemanticSynchronization(nextId, intent)
		}
	}
	return ReaderTransitionReduction(
		state = copy(
			active = next,
			retryableTransition = null,
			lastTransitionSequence = nextSequence
		),
		commands = buildList {
			add(ReaderTransitionCommand.ApplyInputLease(nextId, next.phase.contract.inputLease))
			consequence?.let(::add)
		}
	)
}

private fun ReaderSemanticSynchronizationIntent.operation(): ReaderTransitionOperation = when (this) {
	is ReaderPageTurnIntent -> ReaderTransitionOperation.CurlClaimAndSettlement
	is ReaderExternalRelocationIntent -> ReaderTransitionOperation.ExternalSemanticRelocation
	ReaderCoverEntryIntent -> ReaderTransitionOperation.CoverToPageEntry
}

private fun ReaderTransitionJournal.reduceResourceRegistration(
	fact: ReaderTransitionFact
): ReaderTransitionReduction {
	val classification = requireNotNull(fact.resourceClassificationOrNull())
	check(classification.meaning == ReaderTransitionResourceFactMeaning.RegistrationOrObservation)
	val current = active ?: return reduceRejectedResourceKey(classification.key)
	if (
		current.id != fact.transitionId ||
		classification.key.transitionId != current.id ||
		(fact is ReaderTransitionFact.DeckReserved &&
			classification.key.kind != ReaderTransitionResourceKind.Deck)
	) return reduceRejectedResourceKey(classification.key)
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

private fun ReaderTransitionJournal.reduceSettlement(
	fact: ReaderTransitionFact.SettlementAcknowledged
): ReaderTransitionReduction {
	val current = active ?: return unchanged()
	if (
		current.id.operation != ReaderTransitionOperation.CurlClaimAndSettlement ||
		fact.transitionId != current.id ||
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
		resolvedSuccessorBinding = fact.binding,
		consumedSettlement = key
	)
	val withConsumption = copy(active = resolved)
	if (remaining.isEmpty()) {
		val committedOwner = resolved.preparedFrameOwner ?: return unchanged()
		val resourceKey = resolved.successorResourceKey ?: return unchanged()
		return withConsumption.succeed(resolved, fact.binding, committedOwner, resourceKey)
	}
	val state = withConsumption.copy(active = resolved.withAwaitedProofs(remaining))
	return ReaderTransitionReduction(state, emptyList())
}

private fun ReaderTransitionJournal.lifecyclePublicationIdentities(): Set<ReaderPresentationPublicationIdentity> =
	buildSet {
		committed?.binding?.publicationIdentity?.let(::add)
		active?.id?.expectedBinding?.publicationIdentity()?.let(::add)
		active?.resolvedSuccessorBinding?.publicationIdentity?.let(::add)
		retryableTransition?.id?.expectedBinding?.publicationIdentity()?.let(::add)
		retryableTransition?.resolvedSuccessorBinding?.publicationIdentity?.let(::add)
	}

private fun ReaderExpectedPresentationBinding.publicationIdentity(): ReaderPresentationPublicationIdentity =
	when (this) {
		is ReaderExpectedPresentationBinding.Exact -> binding.publicationIdentity
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
		current.resolvedSuccessorBinding?.let { it != binding } == true
	) return unchanged()
	if (ReaderTransitionProofKind.SemanticDestination in current.phase.contract.awaitedProofs) {
		return reduceProof(current.id, ReaderTransitionProofKind.SemanticDestination, binding)
	}
	return ReaderTransitionReduction(
		state = copy(
			active = current.copy(
				resolvedSuccessorBinding = binding,
				authoritativeDestinationCommitted = true
			)
		),
		commands = listOf(ReaderTransitionCommand.RequestRasterPreparation(current.id, binding))
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
	val predecessorId = current?.id ?: committed?.id ?: return unchanged()
	val retainedOwner = current?.phase?.contract?.retainedOwner ?: requireNotNull(committed).owner
	val predecessorResourceKey = current?.predecessorResourceKey ?: committed?.resourceKey
	val nextSequence = nextTransitionSequence()
	val relocationId = ReaderTransitionId(
		readerSessionGeneration = predecessorId.readerSessionGeneration,
		coordinatorEpoch = predecessorId.coordinatorEpoch,
		sequence = nextSequence,
		operation = ReaderTransitionOperation.ExternalSemanticRelocation,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(fact.binding),
		parent = predecessorId.parentIdentity()
	)
	val phase = ReaderTransitionLivenessTable.phase(
		id = relocationId,
		kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
		retainedOwner = retainedOwner,
		satisfiedProofs = setOf(ReaderTransitionProofKind.SemanticDestination)
	)
	return ReaderTransitionReduction(
		state = copy(
			active = ReaderActiveTransition(
				relocationId,
				phase,
				resolvedSuccessorBinding = fact.binding,
				predecessorResourceKey = predecessorResourceKey,
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
			lastTransitionSequence = nextSequence
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
			add(
				ReaderTransitionCommand.ApplyInputLease(
					relocationId,
					ReaderTransitionInputLease.ChromeOnly
				)
			)
			add(ReaderTransitionCommand.RequestRasterPreparation(relocationId, fact.binding))
		}
	)
}

private fun ReaderTransitionJournal.reducePreparedFrame(
	fact: ReaderTransitionFact.PreparedFrame
): ReaderTransitionReduction {
	val current = active ?: return reduceRejectedResourceKey(fact.resourceKey)
	if (
		fact.transitionId != current.id ||
		!current.id.expectedBinding.matches(fact.binding) ||
		current.resolvedSuccessorBinding?.let { it != fact.binding } == true ||
		!current.id.operation.acceptsCommittedOwner(fact.frameOwner)
	) return reduceRejectedResourceKey(fact.resourceKey)
	if (ReaderTransitionProofKind.PreparedFrame !in current.phase.contract.awaitedProofs) {
		return if (
			current.successorResourceKey == fact.resourceKey &&
			current.preparedFrameOwner == fact.frameOwner &&
			current.resolvedSuccessorBinding == fact.binding
		) {
			unchanged()
		} else {
			reduceRejectedResourceKey(fact.resourceKey)
		}
	}
	if (current.admittedDeckKey != null && current.admittedDeckKey != fact.resourceKey) {
		return reduceRejectedResourceKey(fact.resourceKey)
	}
	val remaining = current.phase.contract.awaitedProofs - ReaderTransitionProofKind.PreparedFrame
	val resolved = current.copy(
		preparedFrameOwner = fact.frameOwner,
		resolvedSuccessorBinding = fact.binding,
		successorResourceKey = fact.resourceKey
	)
	return if (remaining.isEmpty()) {
		succeed(resolved, fact.binding, fact.frameOwner, fact.resourceKey)
	} else {
		ReaderTransitionReduction(
			copy(active = resolved.withAwaitedProofs(remaining)),
			emptyList()
		)
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
		succeed(current, binding, frameOwner, resourceKey)
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
		fact.key.transitionId != current.id ||
		fact.key.kind != ReaderTransitionResourceKind.Deck
	) return reduceRejectedResourceKey(fact.key)
	if (ReaderTransitionProofKind.DeckOwnership !in current.phase.contract.awaitedProofs) {
		return if (current.admittedDeckKey == fact.key) unchanged() else reduceRejectedResourceKey(fact.key)
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
	val updated = current.copy(
		admittedDeckKey = fact.key,
		pendingPreparedDeckKey = null
	)
	val reduction = completeOrContinue(updated, current.phase.contract.awaitedProofs - satisfied)
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
		fact.key.transitionId != current.id ||
		fact.key.kind != ReaderTransitionResourceKind.Deck
	) return reduceRejectedResourceKey(fact.key)
	if (ReaderTransitionProofKind.DeckPrepared !in current.phase.contract.awaitedProofs) {
		return if (current.admittedDeckKey == fact.key) unchanged() else reduceRejectedResourceKey(fact.key)
	}
	val admitted = current.admittedDeckKey
	if (admitted == null) {
		if (
			current.successorResourceKey?.takeIf { it.kind == ReaderTransitionResourceKind.Deck }
				?.let { it != fact.key } == true
		) return reduceRejectedResourceKey(fact.key)
		return ReaderTransitionReduction(
			copy(active = current.copy(pendingPreparedDeckKey = fact.key)),
			emptyList()
		)
	}
	if (admitted != fact.key) return reduceRejectedResourceKey(fact.key)
	return completeOrContinue(
		current,
		current.phase.contract.awaitedProofs - ReaderTransitionProofKind.DeckPrepared
	)
}

private fun ReaderTransitionJournal.completeOrContinue(
	current: ReaderActiveTransition,
	remaining: Set<ReaderTransitionProofKind>
): ReaderTransitionReduction {
	if (remaining.isNotEmpty()) {
		return ReaderTransitionReduction(
			copy(active = current.withAwaitedProofs(remaining)),
			emptyList()
		)
	}
	val owner = current.preparedFrameOwner ?: return unchanged()
	val binding = current.resolvedSuccessorBinding ?: return unchanged()
	val resourceKey = current.successorResourceKey ?: return unchanged()
	return succeed(current, binding, owner, resourceKey)
}

private fun ReaderTransitionJournal.knownResourceKeys(
	current: ReaderActiveTransition? = active,
	additional: List<ReaderTransitionResourceKey> = emptyList()
): List<ReaderTransitionResourceKey> = buildList {
	committed?.resourceKey?.let(::add)
	current?.predecessorResourceKey?.let(::add)
	current?.ownedResourceKeys?.let(::addAll)
	current?.admittedDeckKey?.let(::add)
	current?.pendingPreparedDeckKey?.let(::add)
	current?.successorResourceKey?.let(::add)
	addAll(additional)
}

private fun ReaderTransitionJournal.resourceDispositionCommands(
	transitionId: ReaderTransitionId,
	candidates: Iterable<ReaderTransitionResourceKey>,
	retention: ReaderTransitionResourceRetention,
	successorResourceKey: ReaderTransitionResourceKey? = null
): List<ReaderTransitionCommand.ReleaseResource> {
	val retainedKeys = when (retention) {
		ReaderTransitionResourceRetention.TruthfulPredecessor -> buildSet {
			active?.predecessorResourceKey?.let(::add)
			committed?.resourceKey?.let(::add)
		}
		ReaderTransitionResourceRetention.Successor ->
			setOfNotNull(successorResourceKey ?: active?.successorResourceKey)
		ReaderTransitionResourceRetention.None -> emptySet()
	}
	return candidates
		.distinct()
		.filterNot { it in retainedKeys }
		.map { ReaderTransitionCommand.ReleaseResource(transitionId, it) }
}

private fun ReaderTransitionJournal.reduceRejectedResourceFact(
	fact: ReaderTransitionFact
): ReaderTransitionReduction = reduceRejectedResourceKey(
	requireNotNull(fact.resourceClassificationOrNull()).key
)

private fun ReaderTransitionJournal.reduceRejectedResourceKey(
	key: ReaderTransitionResourceKey
): ReaderTransitionReduction = ReaderTransitionReduction(
	state = this,
	commands = resourceDispositionCommands(
		transitionId = active?.id ?: key.transitionId,
		candidates = listOf(key),
		retention = ReaderTransitionResourceRetention.TruthfulPredecessor
	)
)

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
	val remaining = current.phase.contract.awaitedProofs - proof
	val resolved = current.copy(
		resolvedSuccessorBinding = factBinding ?: current.resolvedSuccessorBinding,
		authoritativeDestinationCommitted = current.authoritativeDestinationCommitted ||
			(proof == ReaderTransitionProofKind.SemanticDestination && factBinding != null)
	)
	if (remaining.isEmpty()) return completeOrContinue(resolved, remaining)
	val next = resolved.withAwaitedProofs(remaining)
	val commands = when (proof) {
		ReaderTransitionProofKind.SemanticDestination -> factBinding?.let { binding ->
			listOf(ReaderTransitionCommand.RequestRasterPreparation(current.id, binding))
		} ?: emptyList()
		ReaderTransitionProofKind.Raster ->
			(current.id.expectedBinding.exactBindingOrNull() ?: resolved.resolvedSuccessorBinding)?.let { binding ->
				listOf(
					ReaderTransitionCommand.ReserveDeck(
						transitionId = current.id,
						binding = binding,
						role = current.id.operation.deckRole()
					)
				)
			} ?: emptyList()
		else -> emptyList()
	}
	return ReaderTransitionReduction(copy(active = next), commands)
}

private fun ReaderTransitionJournal.succeed(
	current: ReaderActiveTransition,
	binding: ReaderPresentationBinding,
	committedOwner: ReaderPresentationFrameOwner,
	successorResourceKey: ReaderTransitionResourceKey
): ReaderTransitionReduction {
	check(current.id.operation.acceptsCommittedOwner(committedOwner))
	check(committedOwner.hasBinding(binding))
	check(successorResourceKey.transitionId == current.id)
	check(successorResourceKey.kind == committedOwner.resourceKind())
	val commands = buildList {
		add(
			ReaderTransitionCommand.ApplyInputLease(
				current.id,
				committedOwner.committedInputLease(binding)
			)
		)
		addAll(
			resourceDispositionCommands(
				transitionId = current.id,
				candidates = knownResourceKeys(current),
				retention = ReaderTransitionResourceRetention.Successor,
				successorResourceKey = successorResourceKey
			)
		)
	}
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Succeeded(
				committedOwner = committedOwner,
				binding = binding
			),
			committed = ReaderCommittedTransition(
				current.id,
				committedOwner,
				binding,
				successorResourceKey
			),
			retryableTransition = null
		),
		commands = commands
	)
}

private fun ReaderTransitionJournal.reduceDeckRejected(
	fact: ReaderTransitionFact.DeckRejected
): ReaderTransitionReduction {
	val current = active
	if (
		current?.id != fact.transitionId ||
		fact.key.transitionId != fact.transitionId ||
		fact.key.kind != ReaderTransitionResourceKind.Deck
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
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Failed(
				liveness.timeoutFailureReason,
				liveness.timeoutRetryability,
				current.phase.contract.retainedOwner
			),
			retryableTransition = current.toRetryableTransition()
		),
		commands = terminalResourceCommands(current)
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
	return terminatePublication(ReaderTransitionCancellationReason.PublicationReplaced)
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
	return terminatePublication(ReaderTransitionCancellationReason.PublicationClosed)
}

private fun ReaderTransitionJournal.terminatePublication(
	reason: ReaderTransitionCancellationReason
): ReaderTransitionReduction {
	val current = active
	val committedState = committed
	val commandOwnerId = current?.id ?: committedState?.id ?: retryableTransition?.id ?: return unchanged()
	val resources = knownResourceKeys(current)
	return ReaderTransitionReduction(
		state = copy(
			active = null,
			lastOutcome = ReaderTransitionOutcome.Cancelled(
				reason,
				ReaderPresentationFrameOwner.Neutral
			),
			committed = null,
			retryableTransition = null
		),
		commands = buildList {
			current?.let { add(ReaderTransitionCommand.CancelOwnedWork(it.id)) }
			add(ReaderTransitionCommand.ApplyInputLease(commandOwnerId, ReaderTransitionInputLease.None))
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
	is ReaderExpectedPresentationBinding.SemanticSuccessor ->
		binding != predecessor &&
		binding.foliateSessionId == predecessor.foliateSessionId &&
		binding.publicationGeneration == predecessor.publicationGeneration
}

private fun ReaderExpectedPresentationBinding.exactBindingOrNull(): ReaderPresentationBinding? = when (this) {
	is ReaderExpectedPresentationBinding.Exact -> binding
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
	committed?.id?.sequence ?: 0L,
	retryableTransition?.id?.sequence ?: 0L
).incrementTransitionSequence()

private fun Long.incrementTransitionSequence(): Long {
	require(this < Long.MAX_VALUE)
	return this + 1L
}
