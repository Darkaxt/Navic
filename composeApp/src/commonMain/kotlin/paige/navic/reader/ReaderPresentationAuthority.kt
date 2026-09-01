package paige.navic.reader

@JvmInline
value class ReaderPresentationToken(val value: Long) {
	init {
		require(value > 0L)
	}
}

enum class ReaderLiveEngineHandoffDirection {
	NativeToLiveEngine,
	LiveEngineToNative
}

data class ReaderPresentationBinding(
	val foliateSessionId: String,
	val publicationGeneration: Long,
	val viewportGeneration: Long,
	val profileGeneration: Long,
	val destinationCommitIdentity: ReaderDestinationCommitIdentity? = null,
	val rasterGeneration: Long? = null,
	val textureGeneration: Long? = null,
	val preparationGeneration: Long? = null
) {
	init {
		require(foliateSessionId.isNotBlank())
		require(publicationGeneration >= 0L)
		require(viewportGeneration >= 0L)
		require(profileGeneration >= 0L)
		require(destinationCommitIdentity?.foliateSessionId == null ||
			destinationCommitIdentity.foliateSessionId == foliateSessionId)
		require(rasterGeneration == null || rasterGeneration >= 0L)
		require(textureGeneration == null || textureGeneration >= 0L)
		require(preparationGeneration == null || preparationGeneration >= 0L)
	}
}

data class ReaderShellCoverCommitProof(
	val token: ReaderPresentationToken,
	val binding: ReaderPresentationBinding,
	val coverGeneration: Long,
	val presentedFrame: Long,
	val viewportWidth: Int,
	val viewportHeight: Int
) {
	init {
		require(coverGeneration >= 0L)
		require(presentedFrame > 0L)
		require(viewportWidth > 0)
		require(viewportHeight > 0)
	}
}

data class ReaderNativePagePresentationProof(
	val binding: ReaderPresentationBinding,
	val presentedFrame: Long,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val operationPolicy: ReaderPageOperationPolicy
) {
	init {
		require(presentedFrame > 0L)
		require(viewportWidth > 0)
		require(viewportHeight > 0)
		require(rasterGeneration >= 0L)
		require(textureGeneration >= 0L)
	}
}

data class ReaderLiveEnginePresentationProof(
	val token: ReaderPresentationToken,
	val binding: ReaderPresentationBinding,
	val presentedFrame: Long,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val liveEngineGeneration: Long
) {
	init {
		require(presentedFrame > 0L)
		require(viewportWidth > 0)
		require(viewportHeight > 0)
		require(liveEngineGeneration >= 0L)
	}
}

data class ReaderCurlPresentationFrame(
	val token: ReaderPresentationToken,
	val binding: ReaderPresentationBinding,
	val presentedFrame: Long,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val rasterGeneration: Long,
	val textureGeneration: Long
) {
	init {
		require(presentedFrame > 0L)
		require(viewportWidth > 0)
		require(viewportHeight > 0)
		require(rasterGeneration >= 0L)
		require(textureGeneration >= 0L)
	}
}

sealed interface ReaderPresentationFrameOwner {
	data object Neutral : ReaderPresentationFrameOwner
	data class ShellCover(val proof: ReaderShellCoverCommitProof) : ReaderPresentationFrameOwner
	data class NativePage(val proof: ReaderNativePagePresentationProof) : ReaderPresentationFrameOwner
	data class Curl(val frame: ReaderCurlPresentationFrame) : ReaderPresentationFrameOwner
	data class LiveEngine(val proof: ReaderLiveEnginePresentationProof) : ReaderPresentationFrameOwner
}

enum class ReaderPresentationLayer {
	Neutral,
	ShellCover,
	NativePage,
	Curl,
	LiveEngine
}

fun ReaderPresentationFrameOwner.layer(): ReaderPresentationLayer = when (this) {
	ReaderPresentationFrameOwner.Neutral -> ReaderPresentationLayer.Neutral
	is ReaderPresentationFrameOwner.ShellCover -> ReaderPresentationLayer.ShellCover
	is ReaderPresentationFrameOwner.NativePage -> ReaderPresentationLayer.NativePage
	is ReaderPresentationFrameOwner.Curl -> ReaderPresentationLayer.Curl
	is ReaderPresentationFrameOwner.LiveEngine -> ReaderPresentationLayer.LiveEngine
}

enum class ReaderCurlSettlementStage {
	AwaitingFoliate,
	AwaitingNativePresentation
}

sealed interface ReaderPresentationAuthority {
	data object Unavailable : ReaderPresentationAuthority
	data class ShellCover(val proof: ReaderShellCoverCommitProof) : ReaderPresentationAuthority
	data class ShellCoverCommitPending(
		val retainedFrame: ReaderPresentationFrameOwner.NativePage,
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val coverGeneration: Long
	) : ReaderPresentationAuthority {
		init {
			require(coverGeneration >= 0L)
		}
	}
	data class CurlGesture(
		val frame: ReaderPresentationFrameOwner.Curl
	) : ReaderPresentationAuthority
	data class CurlSettlementPending(
		val retainedFrame: ReaderPresentationFrameOwner.Curl,
		val stage: ReaderCurlSettlementStage
	) : ReaderPresentationAuthority
	data class SettledNativePage(
		val frame: ReaderPresentationFrameOwner.NativePage
	) : ReaderPresentationAuthority
	data class LiveEngineHandoffPending(
		val retainedFrame: ReaderPresentationFrameOwner,
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val direction: ReaderLiveEngineHandoffDirection
	) : ReaderPresentationAuthority
	data class LiveEngineExposed(
		val frame: ReaderPresentationFrameOwner.LiveEngine
	) : ReaderPresentationAuthority
	data class BlockingPreparation(
		val retainedFrame: ReaderPresentationFrameOwner
	) : ReaderPresentationAuthority
}

sealed interface ReaderPresentationInputPolicy {
	data object RecoveryOnly : ReaderPresentationInputPolicy
	data object ChromeOnly : ReaderPresentationInputPolicy
	data object ShellCover : ReaderPresentationInputPolicy
	data class ClaimedCurl(val token: ReaderPresentationToken) : ReaderPresentationInputPolicy
	data class NativePage(val policy: ReaderPageOperationPolicy) : ReaderPresentationInputPolicy
	data object LiveEngine : ReaderPresentationInputPolicy
}

sealed interface ReaderPreparationPresentation {
	data object Hidden : ReaderPreparationPresentation
	data class Blocking(
		val completedCount: Int,
		val requiredCount: Int,
		val determinate: Boolean
	) : ReaderPreparationPresentation {
		init {
			require(completedCount >= 0)
			require(requiredCount >= 0)
			require(completedCount <= requiredCount)
			require(determinate == (requiredCount > 0))
		}
	}
}

enum class ReaderPresentationFailureReason {
	PreparationUnavailable,
	RendererUnavailable,
	ShellCoverUnavailable,
	NativePresentationUnavailable,
	LiveEngineUnavailable,
	TimedOut
}

sealed interface ReaderDiagnosticPresentation {
	data object Hidden : ReaderDiagnosticPresentation
	data class Failure(
		val reason: ReaderPresentationFailureReason,
		val retryable: Boolean,
		val cancellable: Boolean
	) : ReaderDiagnosticPresentation
}

enum class ReaderRequiredTransition {
	None,
	CommitShellCover,
	PresentNativePage,
	ExposeLiveEngine
}

data class ReaderPresentationDecision(
	val authority: ReaderPresentationAuthority,
	val frameOwner: ReaderPresentationFrameOwner,
	val layer: ReaderPresentationLayer,
	val inputPolicy: ReaderPresentationInputPolicy,
	val preparationPresentation: ReaderPreparationPresentation,
	val diagnosticPresentation: ReaderDiagnosticPresentation,
	val requiredTransition: ReaderRequiredTransition
)

sealed interface ReaderPresentationLifecycleState {
	data object Foreground : ReaderPresentationLifecycleState
	data object Background : ReaderPresentationLifecycleState
	data object Destroyed : ReaderPresentationLifecycleState
}

sealed interface ReaderPresentationLifecycleEvent {
	data object EnteredForeground : ReaderPresentationLifecycleEvent
	data object EnteredBackground : ReaderPresentationLifecycleEvent
	data object Destroyed : ReaderPresentationLifecycleEvent
}

data class ReaderPagePreparationFacts(
	val phase: ReaderPagePreparationPhase = ReaderPagePreparationPhase.Idle,
	val generation: Long = 0L,
	val completedCount: Int = 0,
	val requiredCount: Int = 0,
	val readiness: ReaderPageReadinessState = ReaderPageReadinessState(),
	val failure: ReaderPresentationFailureReason? = null
) {
	init {
		require(generation >= 0L)
		require(completedCount >= 0)
		require(requiredCount >= 0)
		require(completedCount <= requiredCount)
	}
}

data class ReaderPresentationState(
	val authority: ReaderPresentationAuthority = ReaderPresentationAuthority.Unavailable,
	val binding: ReaderPresentationBinding? = null,
	val lifecycle: ReaderPresentationLifecycleState = ReaderPresentationLifecycleState.Foreground,
	val preparationFacts: ReaderPagePreparationFacts = ReaderPagePreparationFacts(),
	val failure: ReaderDiagnosticPresentation.Failure? = null,
	val nextTokenValue: Long = 1L
) {
	init {
		require(nextTokenValue > 0L)
	}
}

sealed interface ReaderPresentationEvent {
	data class PublicationOpened(val binding: ReaderPresentationBinding) : ReaderPresentationEvent
	data class FoliateRelocated(
		val binding: ReaderPresentationBinding,
		val acknowledgement: ReaderPageTurnSettlementAck?
	) : ReaderPresentationEvent
	data class ShellCoverRequested(val coverGeneration: Long) : ReaderPresentationEvent {
		init {
			require(coverGeneration >= 0L)
		}
	}
	data class ShellCoverCommitted(val proof: ReaderShellCoverCommitProof) : ReaderPresentationEvent
	data class ShellCoverFailed(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding
	) : ReaderPresentationEvent
	data class ShellCoverEntered(val proof: ReaderShellCoverCommitProof) : ReaderPresentationEvent
	data class NativePagePresented(val proof: ReaderNativePagePresentationProof) : ReaderPresentationEvent
	data class CurlClaimed(val frame: ReaderCurlPresentationFrame) : ReaderPresentationEvent
	data class CurlTerminal(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val stage: ReaderCurlSettlementStage
	) : ReaderPresentationEvent
	data class WebViewHandoffRequested(
		val direction: ReaderLiveEngineHandoffDirection
	) : ReaderPresentationEvent
	data class WebViewPresentationProven(
		val proof: ReaderLiveEnginePresentationProof
	) : ReaderPresentationEvent
	data class WebViewPresentationFailed(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding
	) : ReaderPresentationEvent
	data class PreparationReported(val facts: ReaderPagePreparationFacts) : ReaderPresentationEvent
	data class PreparationFailed(
		val facts: ReaderPagePreparationFacts,
		val reason: ReaderPresentationFailureReason,
		val retryable: Boolean,
		val cancellable: Boolean
	) : ReaderPresentationEvent
	data class TimedOut(val token: ReaderPresentationToken? = null) : ReaderPresentationEvent
	data object Retry : ReaderPresentationEvent
	data object Cancel : ReaderPresentationEvent
	data class Lifecycle(val event: ReaderPresentationLifecycleEvent) : ReaderPresentationEvent
}

sealed interface ReaderPresentationEffect {
	data object ReleaseStalePresentation : ReaderPresentationEffect
}

data class ReaderPresentationReduction(
	val state: ReaderPresentationState,
	val decision: ReaderPresentationDecision,
	val effects: List<ReaderPresentationEffect>
)

fun readerPresentationDecision(state: ReaderPresentationState): ReaderPresentationDecision {
	val authority = state.authority
	val frameOwner = authority.frameOwner()
	return ReaderPresentationDecision(
		authority = authority,
		frameOwner = frameOwner,
		layer = frameOwner.layer(),
		inputPolicy = authority.inputPolicy(),
		preparationPresentation = authority.preparationPresentation(state.preparationFacts, state.failure),
		diagnosticPresentation = state.failure ?: ReaderDiagnosticPresentation.Hidden,
		requiredTransition = authority.requiredTransition()
	)
}

fun readerPresentationReduce(
	state: ReaderPresentationState,
	event: ReaderPresentationEvent
): ReaderPresentationReduction {
	val result = when (event) {
		is ReaderPresentationEvent.PublicationOpened -> ReaderPresentationReducerResult(
			state = ReaderPresentationState(
				binding = event.binding,
				lifecycle = state.lifecycle,
				nextTokenValue = state.nextTokenValue
			)
		)
		is ReaderPresentationEvent.FoliateRelocated -> if (
			state.authority == ReaderPresentationAuthority.Unavailable
		) {
			ReaderPresentationReducerResult(state.copy(binding = event.binding))
		} else if (state.binding == event.binding) {
			ReaderPresentationReducerResult(state)
		} else {
			state.stalePresentation()
		}
		is ReaderPresentationEvent.ShellCoverRequested -> {
			val authority = state.authority
			if (authority is ReaderPresentationAuthority.SettledNativePage &&
				state.binding == authority.frame.proof.binding
			) {
				val token = ReaderPresentationToken(state.nextTokenValue)
				ReaderPresentationReducerResult(
					state.copy(
						authority = ReaderPresentationAuthority.ShellCoverCommitPending(
							retainedFrame = authority.frame,
							token = token,
							binding = authority.frame.proof.binding,
							coverGeneration = event.coverGeneration
						),
						nextTokenValue = state.nextTokenValue + 1L
					)
				)
			} else {
				ReaderPresentationReducerResult(state)
			}
		}
		is ReaderPresentationEvent.ShellCoverCommitted -> state.reduceShellCoverProof(event.proof)
		is ReaderPresentationEvent.ShellCoverFailed -> state.reduceShellCoverFailure(event)
		is ReaderPresentationEvent.ShellCoverEntered -> state.reduceShellCoverEntry(event.proof)
		is ReaderPresentationEvent.NativePagePresented -> state.reduceNativePageProof(event.proof)
		is ReaderPresentationEvent.CurlClaimed -> state.reduceCurlClaim(event.frame)
		is ReaderPresentationEvent.CurlTerminal -> state.reduceCurlTerminal(event)
		is ReaderPresentationEvent.WebViewHandoffRequested -> state.reduceWebViewHandoff(event.direction)
		is ReaderPresentationEvent.WebViewPresentationProven -> state.reduceWebViewProof(event.proof)
		is ReaderPresentationEvent.WebViewPresentationFailed -> state.reduceWebViewFailure(event)
		is ReaderPresentationEvent.PreparationReported -> state.reducePreparationReport(event.facts)
		is ReaderPresentationEvent.PreparationFailed -> state.reducePreparationFailure(event)
		is ReaderPresentationEvent.TimedOut -> state.reduceTimeout(event)
		ReaderPresentationEvent.Retry,
		ReaderPresentationEvent.Cancel,
		is ReaderPresentationEvent.Lifecycle -> ReaderPresentationReducerResult(state)
	}
	return ReaderPresentationReduction(
		state = result.state,
		decision = readerPresentationDecision(result.state),
		effects = result.effects
	)
}

private data class ReaderPresentationReducerResult(
	val state: ReaderPresentationState,
	val effects: List<ReaderPresentationEffect> = emptyList()
)

private fun ReaderPresentationState.reduceShellCoverProof(
	proof: ReaderShellCoverCommitProof
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.ShellCoverCommitPending &&
		authority.token == proof.token &&
		authority.binding == proof.binding &&
		binding == proof.binding &&
		authority.coverGeneration == proof.coverGeneration
	) {
		ReaderPresentationReducerResult(
			copy(authority = ReaderPresentationAuthority.ShellCover(proof))
		)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reduceShellCoverFailure(
	event: ReaderPresentationEvent.ShellCoverFailed
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.ShellCoverCommitPending &&
		authority.token == event.token &&
		authority.binding == event.binding &&
		binding == event.binding
	) {
		ReaderPresentationReducerResult(this)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reduceShellCoverEntry(
	proof: ReaderShellCoverCommitProof
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.ShellCover &&
		authority.proof == proof &&
		binding == proof.binding
	) {
		ReaderPresentationReducerResult(this)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reduceNativePageProof(
	proof: ReaderNativePagePresentationProof
): ReaderPresentationReducerResult = when (authority) {
	ReaderPresentationAuthority.Unavailable -> if (binding == null || binding == proof.binding) {
		ReaderPresentationReducerResult(
			copy(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(proof)
				),
				binding = proof.binding,
				failure = null
			)
		)
	} else {
		stalePresentation()
	}
	else -> stalePresentation()
}

private fun ReaderPresentationState.reduceCurlClaim(
	frame: ReaderCurlPresentationFrame
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.SettledNativePage &&
		binding == frame.binding &&
		authority.frame.proof.binding == frame.binding
	) {
		ReaderPresentationReducerResult(
			copy(
				authority = ReaderPresentationAuthority.CurlGesture(
					ReaderPresentationFrameOwner.Curl(frame)
				),
				nextTokenValue = nextTokenAfter(frame.token)
			)
		)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reduceCurlTerminal(
	event: ReaderPresentationEvent.CurlTerminal
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.CurlGesture &&
		authority.frame.frame.token == event.token &&
		authority.frame.frame.binding == event.binding &&
		binding == event.binding
	) {
		ReaderPresentationReducerResult(
			copy(
				authority = ReaderPresentationAuthority.CurlSettlementPending(
					retainedFrame = authority.frame,
					stage = event.stage
				)
			)
		)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reduceWebViewHandoff(
	direction: ReaderLiveEngineHandoffDirection
): ReaderPresentationReducerResult {
	val retainedFrame = when (val authority = authority) {
		is ReaderPresentationAuthority.SettledNativePage -> authority.frame
		is ReaderPresentationAuthority.LiveEngineExposed -> authority.frame
		else -> return ReaderPresentationReducerResult(this)
	}
	val currentBinding = binding ?: return ReaderPresentationReducerResult(this)
	return ReaderPresentationReducerResult(
		copy(
			authority = ReaderPresentationAuthority.LiveEngineHandoffPending(
				retainedFrame = retainedFrame,
				token = ReaderPresentationToken(nextTokenValue),
				binding = currentBinding,
				direction = direction
			),
			nextTokenValue = nextTokenValue + 1L
		)
	)
}

private fun ReaderPresentationState.reduceWebViewProof(
	proof: ReaderLiveEnginePresentationProof
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.LiveEngineHandoffPending &&
		authority.direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine &&
		authority.token == proof.token &&
		authority.binding == proof.binding &&
		binding == proof.binding
	) {
		ReaderPresentationReducerResult(
			copy(
				authority = ReaderPresentationAuthority.LiveEngineExposed(
					ReaderPresentationFrameOwner.LiveEngine(proof)
				),
				failure = null
			)
		)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reduceWebViewFailure(
	event: ReaderPresentationEvent.WebViewPresentationFailed
): ReaderPresentationReducerResult {
	val authority = authority
	return if (authority is ReaderPresentationAuthority.LiveEngineHandoffPending &&
		authority.token == event.token &&
		authority.binding == event.binding &&
		binding == event.binding
	) {
		ReaderPresentationReducerResult(this)
	} else {
		stalePresentation()
	}
}

private fun ReaderPresentationState.reducePreparationReport(
	facts: ReaderPagePreparationFacts
): ReaderPresentationReducerResult {
	val nextAuthority = if (
		authority == ReaderPresentationAuthority.Unavailable &&
		facts.phase == ReaderPagePreparationPhase.Preparing
	) {
		ReaderPresentationAuthority.BlockingPreparation(ReaderPresentationFrameOwner.Neutral)
	} else {
		authority
	}
	return ReaderPresentationReducerResult(
		copy(
			authority = nextAuthority,
			preparationFacts = facts,
			failure = if (facts.failure == null) null else failure
		)
	)
}

private fun ReaderPresentationState.reducePreparationFailure(
	event: ReaderPresentationEvent.PreparationFailed
): ReaderPresentationReducerResult {
	val diagnostic = ReaderDiagnosticPresentation.Failure(
		reason = event.reason,
		retryable = event.retryable,
		cancellable = event.cancellable
	)
	return ReaderPresentationReducerResult(
		copy(
			authority = ReaderPresentationAuthority.BlockingPreparation(authority.frameOwner()),
			preparationFacts = event.facts.copy(
				phase = ReaderPagePreparationPhase.Failed,
				failure = event.reason
			),
			failure = diagnostic
		)
	)
}

private fun ReaderPresentationState.reduceTimeout(
	event: ReaderPresentationEvent.TimedOut
): ReaderPresentationReducerResult {
	val expectedToken = authority.tokenOrNull()
	return if (event.token != null && event.token != expectedToken) {
		stalePresentation()
	} else {
		ReaderPresentationReducerResult(this)
	}
}

private fun ReaderPresentationState.stalePresentation() = ReaderPresentationReducerResult(
	state = this,
	effects = listOf(ReaderPresentationEffect.ReleaseStalePresentation)
)

private fun ReaderPresentationAuthority.frameOwner(): ReaderPresentationFrameOwner = when (this) {
	ReaderPresentationAuthority.Unavailable -> ReaderPresentationFrameOwner.Neutral
	is ReaderPresentationAuthority.ShellCover -> ReaderPresentationFrameOwner.ShellCover(proof)
	is ReaderPresentationAuthority.ShellCoverCommitPending -> retainedFrame
	is ReaderPresentationAuthority.CurlGesture -> frame
	is ReaderPresentationAuthority.CurlSettlementPending -> retainedFrame
	is ReaderPresentationAuthority.SettledNativePage -> frame
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> retainedFrame
	is ReaderPresentationAuthority.LiveEngineExposed -> frame
	is ReaderPresentationAuthority.BlockingPreparation -> retainedFrame
}

private fun ReaderPresentationAuthority.inputPolicy(): ReaderPresentationInputPolicy = when (this) {
	ReaderPresentationAuthority.Unavailable -> ReaderPresentationInputPolicy.RecoveryOnly
	is ReaderPresentationAuthority.ShellCover -> ReaderPresentationInputPolicy.ShellCover
	is ReaderPresentationAuthority.ShellCoverCommitPending,
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> ReaderPresentationInputPolicy.ChromeOnly
	is ReaderPresentationAuthority.CurlGesture -> ReaderPresentationInputPolicy.ClaimedCurl(frame.frame.token)
	is ReaderPresentationAuthority.CurlSettlementPending ->
		ReaderPresentationInputPolicy.ClaimedCurl(retainedFrame.frame.token)
	is ReaderPresentationAuthority.SettledNativePage ->
		ReaderPresentationInputPolicy.NativePage(frame.proof.operationPolicy)
	is ReaderPresentationAuthority.LiveEngineExposed -> ReaderPresentationInputPolicy.LiveEngine
	is ReaderPresentationAuthority.BlockingPreparation -> if (
		retainedFrame == ReaderPresentationFrameOwner.Neutral
	) {
		ReaderPresentationInputPolicy.RecoveryOnly
	} else {
		ReaderPresentationInputPolicy.ChromeOnly
	}
}

private fun ReaderPresentationAuthority.preparationPresentation(
	facts: ReaderPagePreparationFacts,
	failure: ReaderDiagnosticPresentation.Failure?
): ReaderPreparationPresentation = if (
	this is ReaderPresentationAuthority.BlockingPreparation &&
	failure == null &&
	facts.phase == ReaderPagePreparationPhase.Preparing
) {
	ReaderPreparationPresentation.Blocking(
		completedCount = facts.completedCount,
		requiredCount = facts.requiredCount,
		determinate = facts.requiredCount > 0
	)
} else {
	ReaderPreparationPresentation.Hidden
}

private fun ReaderPresentationAuthority.requiredTransition(): ReaderRequiredTransition = when (this) {
	ReaderPresentationAuthority.Unavailable,
	is ReaderPresentationAuthority.ShellCover,
	is ReaderPresentationAuthority.CurlGesture,
	is ReaderPresentationAuthority.SettledNativePage,
	is ReaderPresentationAuthority.LiveEngineExposed,
	is ReaderPresentationAuthority.BlockingPreparation -> ReaderRequiredTransition.None
	is ReaderPresentationAuthority.ShellCoverCommitPending -> ReaderRequiredTransition.CommitShellCover
	is ReaderPresentationAuthority.CurlSettlementPending -> when (stage) {
		ReaderCurlSettlementStage.AwaitingFoliate -> ReaderRequiredTransition.None
		ReaderCurlSettlementStage.AwaitingNativePresentation -> ReaderRequiredTransition.PresentNativePage
	}
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> when (direction) {
		ReaderLiveEngineHandoffDirection.NativeToLiveEngine -> ReaderRequiredTransition.ExposeLiveEngine
		ReaderLiveEngineHandoffDirection.LiveEngineToNative -> ReaderRequiredTransition.PresentNativePage
	}
}

private fun ReaderPresentationAuthority.tokenOrNull(): ReaderPresentationToken? = when (this) {
	is ReaderPresentationAuthority.ShellCoverCommitPending -> token
	is ReaderPresentationAuthority.CurlGesture -> frame.frame.token
	is ReaderPresentationAuthority.CurlSettlementPending -> retainedFrame.frame.token
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> token
	else -> null
}

private fun ReaderPresentationState.nextTokenAfter(token: ReaderPresentationToken): Long = if (
	token.value >= nextTokenValue
) {
	token.value + 1L
} else {
	nextTokenValue
}
