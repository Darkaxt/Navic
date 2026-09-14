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

data class ReaderPresentationEventReceipt(
	val event: ReaderPresentationEvent,
	val preVersion: ReaderPresentationReceiptVersion,
	val version: ReaderPresentationReceiptVersion,
	val disposition: ReaderPresentationEventDisposition,
	val postState: ReaderPresentationState,
	val effects: List<ReaderPresentationEffect>,
	val originatingTransitionId: ReaderTransitionId? = null
) {
	init {
		require(
			originatingTransitionId == null ||
				originatingTransitionId.readerSessionGeneration == version.readerSessionGeneration
		)
	}

	val semanticReceipt: ReaderPresentationSemanticReceipt?
		get() {
			if (disposition != ReaderPresentationEventDisposition.Accepted) return null
			return when (val sourceEvent = event) {
				is ReaderPresentationEvent.FoliateRelocated -> sourceEvent.acknowledgement?.let { acknowledgement ->
					ReaderPresentationSemanticReceipt.Settlement(
							binding = sourceEvent.binding,
							acknowledgement = acknowledgement,
							transitionId = originatingTransitionId
						)
				} ?: ReaderPresentationSemanticReceipt.Destination(
						binding = sourceEvent.binding,
						transitionId = originatingTransitionId
					)
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

internal fun readerPresentationEventTransition(
	preState: ReaderPresentationState,
	preVersion: ReaderPresentationReceiptVersion,
	shellCoverVisible: Boolean,
	event: ReaderPresentationEvent
): ReaderPresentationEventTransition {
	val previousAuthority = preState.authority
	val primaryReduction = readerPresentationReduce(preState, event)
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
			effects = reduction.effects.toList()
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
