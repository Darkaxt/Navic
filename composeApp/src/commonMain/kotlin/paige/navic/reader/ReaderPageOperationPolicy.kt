package paige.navic.reader

sealed interface ReaderPageNewPointerDecision {
	data object Accept : ReaderPageNewPointerDecision
	data class Reject(val outcome: ReaderPageGestureTerminalOutcome) : ReaderPageNewPointerDecision
}

enum class ReaderPageLifecycleCancellationReason {
	HostDetached,
	CanvasDisabled,
	HostDestroyed,
	ReaderExit,
	RendererReplaced,
	RasterProfileInvalidated,
	UnsafeContextLoss,
	GlFailure
}

data class ReaderPageOperationPolicy(
	val newPointer: ReaderPageNewPointerDecision,
	val continueActivePointer: Boolean,
	val continueSettlement: Boolean,
	val cancelForReadinessChange: Boolean = false
)

fun readerPageOperationPolicy(readiness: ReaderPageReadinessState): ReaderPageOperationPolicy {
	val deckPrepared = readiness.textureDeck == ReaderTextureDeckState.Ready
	val settling = readiness.textureDeck == ReaderTextureDeckState.Settling ||
		readiness.interaction == ReaderPageInteractionState.Settling
	val newPointer = when {
		settling -> ReaderPageNewPointerDecision.Reject(
			ReaderPageGestureTerminalOutcome.RejectedSettling
		)
		// A refill follows every committed turn; the renderer queues swipes made during it behind
		// that turn (or rejects them itself), so they are not dropped at the host.
		deckPrepared && (
			readiness.interaction == ReaderPageInteractionState.Ready ||
				readiness.interaction == ReaderPageInteractionState.BackgroundPrefetch ||
				readiness.interaction == ReaderPageInteractionState.RefillingWorkingSet
			) -> ReaderPageNewPointerDecision.Accept
		readiness.interaction == ReaderPageInteractionState.Failed ||
			readiness.textureDeck == ReaderTextureDeckState.Failed -> ReaderPageNewPointerDecision.Reject(
			ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
		)
		else -> ReaderPageNewPointerDecision.Reject(
			ReaderPageGestureTerminalOutcome.RejectedPreparing
		)
	}
	return ReaderPageOperationPolicy(
		newPointer = newPointer,
		continueActivePointer = deckPrepared && !settling,
		continueSettlement = settling
	)
}
