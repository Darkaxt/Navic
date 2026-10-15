package paige.navic.reader

data class ReaderPresentationPublicationIdentity(
	val foliateSessionId: String,
	val publicationGeneration: Long
) {
	init {
		require(foliateSessionId.isNotBlank())
		require(publicationGeneration >= 0L)
	}
}

val ReaderPresentationBinding.publicationIdentity: ReaderPresentationPublicationIdentity
	get() = ReaderPresentationPublicationIdentity(
		foliateSessionId = foliateSessionId,
		publicationGeneration = publicationGeneration
	)

data class ReaderPresentationReceiptVersion(
	val readerSessionGeneration: Long,
	val publicationIdentity: ReaderPresentationPublicationIdentity?,
	val eventSequence: Long
) {
	init {
		require(readerSessionGeneration >= 0L)
		require(eventSequence >= 0L)
	}
}

@JvmInline
value class ReaderSemanticCommandSlotId(val value: Long) {
	init { require(value > 0L) }
}

sealed interface ReaderPresentationEventOrigin {
	data object NonSemantic : ReaderPresentationEventOrigin
	data object UnsolicitedFoliate : ReaderPresentationEventOrigin
	data class SemanticCommand(
		val transitionId: ReaderTransitionId,
		val slotId: ReaderSemanticCommandSlotId
	) : ReaderPresentationEventOrigin
}

data class ReaderPresentationEventReceipt(
	val event: ReaderPresentationEvent,
	val preVersion: ReaderPresentationReceiptVersion,
	val version: ReaderPresentationReceiptVersion,
	val disposition: ReaderPresentationEventDisposition,
	val postState: ReaderPresentationState,
	val effects: List<ReaderPresentationEffect>,
	val origin: ReaderPresentationEventOrigin
) {
	val originatingTransitionId: ReaderTransitionId?
		get() = (origin as? ReaderPresentationEventOrigin.SemanticCommand)?.transitionId

	init {
		val transitionId = originatingTransitionId
		require(
			transitionId == null ||
				transitionId.readerSessionGeneration == version.readerSessionGeneration
		)
	}

	val semanticReceipt: ReaderPresentationSemanticReceipt?
		get() {
			if (disposition != ReaderPresentationEventDisposition.Accepted) return null
			val commandOrigin = origin as? ReaderPresentationEventOrigin.SemanticCommand ?: return null
			return when (val sourceEvent = event) {
				is ReaderPresentationEvent.FoliateRelocated -> sourceEvent.acknowledgement?.let { acknowledgement ->
					ReaderPresentationSemanticReceipt.Settlement(
							binding = sourceEvent.binding,
							acknowledgement = acknowledgement,
							transitionId = commandOrigin.transitionId
						)
				} ?: ReaderPresentationSemanticReceipt.Destination(
						binding = sourceEvent.binding,
						transitionId = commandOrigin.transitionId
					)
				is ReaderPresentationEvent.CurrentDestinationConfirmed ->
					if (sourceEvent.expectedOrigin === commandOrigin) {
						ReaderPresentationSemanticReceipt.Destination(
							binding = sourceEvent.binding,
							transitionId = commandOrigin.transitionId
						)
					} else null
				else -> null
			}
		}
}

sealed interface ReaderPresentationSemanticReceipt {
	val transitionId: ReaderTransitionId?
	val binding: ReaderPresentationBinding

	data class Destination(
		override val binding: ReaderPresentationBinding,
		override val transitionId: ReaderTransitionId? = null
	) : ReaderPresentationSemanticReceipt

	data class Settlement(
		override val binding: ReaderPresentationBinding,
		val acknowledgement: ReaderPageTurnSettlementAck,
		override val transitionId: ReaderTransitionId? = null
	) : ReaderPresentationSemanticReceipt
}

internal class ReaderPresentationSemanticReceiptConsumption {
	private var readerSessionGeneration: Long = -1L
	private var eventSequence: Long = -1L

	fun consume(receipt: ReaderPresentationEventReceipt): ReaderPresentationSemanticReceipt? {
		val version = receipt.version
		if (version.readerSessionGeneration < readerSessionGeneration) return null
		if (
			version.readerSessionGeneration == readerSessionGeneration &&
			version.eventSequence <= eventSequence
		) return null
		readerSessionGeneration = version.readerSessionGeneration
		eventSequence = version.eventSequence
		return receipt.semanticReceipt
	}
}

internal data class ReaderPresentationEventTransition(
	val receipt: ReaderPresentationEventReceipt,
	val publicationIdentity: ReaderPresentationPublicationIdentity?,
	val shellCoverVisible: Boolean,
	val acceptedShellCoverDismissal: Boolean
)

private fun currentDestinationConfirmationReduction(
	state: ReaderPresentationState,
	version: ReaderPresentationReceiptVersion,
	event: ReaderPresentationEvent.CurrentDestinationConfirmed,
	origin: ReaderPresentationEventOrigin
): ReaderPresentationReduction {
	val commandOrigin = origin as? ReaderPresentationEventOrigin.SemanticCommand
	val disposition = when {
		state.lifecycle == ReaderPresentationLifecycleState.Destroyed ->
			ReaderPresentationEventDisposition.Destroyed
		commandOrigin == null || commandOrigin !== event.expectedOrigin ->
			ReaderPresentationEventDisposition.Rejected
		version != event.expectedVersion -> ReaderPresentationEventDisposition.Stale
		version.readerSessionGeneration <= 0L ||
			commandOrigin.transitionId.readerSessionGeneration != version.readerSessionGeneration ||
			version.publicationIdentity != event.binding.publicationIdentity ||
			state.binding != event.binding -> ReaderPresentationEventDisposition.Rejected
		state.lifecycle != ReaderPresentationLifecycleState.Foreground || state.failure != null ||
			event.binding.destinationCommitIdentity == null || event.binding.profileGeneration <= 0L ||
			(event.binding.rasterGeneration == null) != (event.binding.textureGeneration == null) ->
			ReaderPresentationEventDisposition.Rejected
		else -> ReaderPresentationEventDisposition.Accepted
	}
	return ReaderPresentationReduction(
		state = state,
		decision = readerPresentationDecision(state),
		effects = emptyList(),
		disposition = disposition
	)
}

/** Installed Curl's owning relocation has already reduced the source acknowledgement.
 * This command-scoped observation certifies semantics only; the predecessor's material,
 * cleanup ownership, frame and input policy remain exactly as they were. */
private fun commandScopedSettlementReduction(
	state: ReaderPresentationState,
	version: ReaderPresentationReceiptVersion,
	event: ReaderPresentationEvent.FoliateRelocated,
	origin: ReaderPresentationEventOrigin.SemanticCommand
): ReaderPresentationReduction {
	val expected = origin.transitionId.expectedBinding as? ReaderExpectedPresentationBinding.SemanticSuccessor
	val predecessor = expected?.takeUnless {
		it is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
			it is ReaderExpectedPresentationBinding.NoCommittedPresentation
	}?.predecessor
	val destination = event.binding.destinationCommitIdentity
	val predecessorDestination = predecessor?.destinationCommitIdentity
	val acknowledgement = event.acknowledgement
	val disposition = when {
		state.lifecycle == ReaderPresentationLifecycleState.Destroyed -> ReaderPresentationEventDisposition.Destroyed
		state.lifecycle != ReaderPresentationLifecycleState.Foreground || state.failure != null ->
			ReaderPresentationEventDisposition.Rejected
		version.readerSessionGeneration <= 0L ||
			origin.transitionId.readerSessionGeneration != version.readerSessionGeneration ||
			predecessor == null || version.publicationIdentity != predecessor.publicationIdentity ||
			state.binding != predecessor -> ReaderPresentationEventDisposition.Stale
		predecessorDestination == null || destination == null ||
			destination.foliateSessionId != predecessor.foliateSessionId ||
			destination.commitSequence <= predecessorDestination.commitSequence ||
			predecessor.profileGeneration <= 0L ||
			event.binding != predecessor.copy(destinationCommitIdentity = destination,
				preparationGeneration = null, rasterGeneration = null, textureGeneration = null) ||
			acknowledgement == null || acknowledgement.foliateSessionId != predecessor.foliateSessionId ||
			acknowledgement.token.isBlank() || acknowledgement.pageIndex < 0 ||
			acknowledgement.rasterGeneration < 0L || acknowledgement.textureGeneration < 0L ->
			ReaderPresentationEventDisposition.Rejected
		else -> ReaderPresentationEventDisposition.Accepted
	}
	return ReaderPresentationReduction(state, readerPresentationDecision(state), emptyList(), disposition)
}

internal fun readerPresentationEventTransition(
	preState: ReaderPresentationState,
	preVersion: ReaderPresentationReceiptVersion,
	shellCoverVisible: Boolean,
	event: ReaderPresentationEvent,
	origin: ReaderPresentationEventOrigin = ReaderPresentationEventOrigin.NonSemantic
): ReaderPresentationEventTransition {
	val previousAuthority = preState.authority
	val primaryReduction = if (event is ReaderPresentationEvent.CurrentDestinationConfirmed) {
		// Only this genuine versioned receipt boundary can confirm a command-scoped observation.
		// The ordinary reducer continues to reject it, and equal relocation stays Idempotent.
		currentDestinationConfirmationReduction(preState, preVersion, event, origin)
	} else if (event is ReaderPresentationEvent.FoliateRelocated &&
		origin is ReaderPresentationEventOrigin.SemanticCommand &&
		origin.transitionId.operation == ReaderTransitionOperation.CurlClaimAndSettlement &&
		event.acknowledgement != null && !event.binding.hasAnyRendererIdentity() &&
		event.binding.preparationGeneration == null
	) {
		commandScopedSettlementReduction(preState, preVersion, event, origin)
	} else {
		readerPresentationReduce(preState, event)
	}
	val startupReduction = if (
		event is ReaderPresentationEvent.PublicationOpened &&
		primaryReduction.state.binding == event.binding &&
		primaryReduction.state.authority == ReaderPresentationAuthority.Unavailable
	) {
		readerPresentationReduce(
			primaryReduction.state,
			if (shellCoverVisible) {
				ReaderPresentationEvent.ShellCoverRequested(
					coverGeneration = primaryReduction.state.nextTokenValue
				)
			} else {
				ReaderPresentationEvent.NativePageRequested
			}
		)
	} else {
		null
	}
	val reduction = startupReduction?.copy(
		effects = primaryReduction.effects + startupReduction.effects
	) ?: primaryReduction
	val publicationIdentity = reduction.state.binding?.publicationIdentity
		?: preState.binding?.publicationIdentity
		?: preVersion.publicationIdentity
	val nextEventSequence = preVersion.eventSequence
		.takeIf { it < Long.MAX_VALUE }
		?.plus(1L)
		?: throw ArithmeticException("Presentation event sequence overflow")
	val version = ReaderPresentationReceiptVersion(
		readerSessionGeneration = preVersion.readerSessionGeneration,
		publicationIdentity = publicationIdentity,
		eventSequence = nextEventSequence
	)
	val acceptedShellCoverCommit =
		event is ReaderPresentationEvent.ShellCoverCommitted &&
			(reduction.decision.frameOwner as? ReaderPresentationFrameOwner.ShellCover)
				?.proof == event.proof
	val acceptedShellCoverDismissal =
		event is ReaderPresentationEvent.NativePagePresented &&
			previousAuthority is ReaderPresentationAuthority.BlockingPreparation &&
			previousAuthority.retainedFrame is ReaderPresentationFrameOwner.ShellCover &&
			previousAuthority.nativePresentationRequest?.let { request ->
				request.token == event.proof.transitionToken &&
					request.binding == event.proof.binding
			} == true &&
			(reduction.decision.frameOwner as? ReaderPresentationFrameOwner.NativePage)
				?.proof == event.proof
	return ReaderPresentationEventTransition(
		receipt = ReaderPresentationEventReceipt(
			event = event,
			preVersion = preVersion,
			version = version,
			disposition = reduction.disposition,
			postState = reduction.state,
			effects = if (origin is ReaderPresentationEventOrigin.SemanticCommand) {
				emptyList()
			} else {
				reduction.effects.toList()
			},
			origin = origin
		),
		publicationIdentity = publicationIdentity,
		shellCoverVisible = when {
			acceptedShellCoverDismissal -> false
			acceptedShellCoverCommit -> true
			else -> shellCoverVisible
		},
		acceptedShellCoverDismissal = acceptedShellCoverDismissal
	)
}
