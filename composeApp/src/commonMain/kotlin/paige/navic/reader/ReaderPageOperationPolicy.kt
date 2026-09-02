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

fun readerPageNewPointerDecision(
	presentationInputPolicy: ReaderPresentationInputPolicy,
	localSafetyPolicy: ReaderPageOperationPolicy
): ReaderPageNewPointerDecision = when (presentationInputPolicy) {
	is ReaderPresentationInputPolicy.NativePage -> when (
		val authorityDecision = presentationInputPolicy.policy.newPointer
	) {
		is ReaderPageNewPointerDecision.Reject -> authorityDecision
		ReaderPageNewPointerDecision.Accept -> localSafetyPolicy.newPointer
	}
	is ReaderPresentationInputPolicy.ClaimedCurl -> ReaderPageNewPointerDecision.Reject(
		ReaderPageGestureTerminalOutcome.RejectedSettling
	)
	ReaderPresentationInputPolicy.ShellCover,
	ReaderPresentationInputPolicy.ChromeOnly -> ReaderPageNewPointerDecision.Reject(
		ReaderPageGestureTerminalOutcome.RejectedPreparing
	)
	ReaderPresentationInputPolicy.RecoveryOnly,
	ReaderPresentationInputPolicy.LiveEngine -> ReaderPageNewPointerDecision.Reject(
		ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
	)
}

fun readerPageOperationPolicy(readiness: ReaderPageReadinessState): ReaderPageOperationPolicy {
	val deckPrepared = readiness.textureDeck == ReaderTextureDeckState.Ready
	val settling = readiness.textureDeck == ReaderTextureDeckState.Settling ||
		readiness.interaction == ReaderPageInteractionState.Settling
	val newPointer = when {
		settling -> ReaderPageNewPointerDecision.Reject(
			ReaderPageGestureTerminalOutcome.RejectedSettling
		)
		deckPrepared && (
			readiness.interaction == ReaderPageInteractionState.Ready ||
				readiness.interaction == ReaderPageInteractionState.BackgroundPrefetch
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
