package paige.navic.reader

import kotlin.jvm.JvmInline

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
		require(profileGeneration != 0L || !hasAnyRendererIdentity())
	}
}

internal data class ReaderRendererDeckIdentity(
	val foliateSessionId: String,
	val publicationGeneration: Long,
	val rasterGeneration: Long,
	val textureGeneration: Long
)

data class ReaderRendererCleanupOwnership(
	val token: ReaderPresentationToken?,
	val binding: ReaderPresentationBinding
) {
	init {
		require(binding.rendererDeckIdentityOrNull() != null)
	}
}

internal fun ReaderPresentationBinding.rendererDeckIdentityOrNull(): ReaderRendererDeckIdentity? {
	val rasterGeneration = rasterGeneration ?: return null
	val textureGeneration = textureGeneration ?: return null
	return ReaderRendererDeckIdentity(
		foliateSessionId = foliateSessionId,
		publicationGeneration = publicationGeneration,
		rasterGeneration = rasterGeneration,
		textureGeneration = textureGeneration
	)
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
	val transitionToken: ReaderPresentationToken?,
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
		require(binding.rasterGeneration == rasterGeneration)
		require(binding.textureGeneration == textureGeneration)
	}
}

data class ReaderLiveEnginePresentationProof(
	val token: ReaderPresentationToken,
	val binding: ReaderPresentationBinding,
	val presentedFrameSequence: Long
) {
	init {
		require(presentedFrameSequence > 0L)
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
		require(binding.rasterGeneration == rasterGeneration)
		require(binding.textureGeneration == textureGeneration)
	}
}

sealed interface ReaderPresentationFrameOwner {
	data object Neutral : ReaderPresentationFrameOwner
	data class ShellCover(val proof: ReaderShellCoverCommitProof) : ReaderPresentationFrameOwner
	data class NativePage(val proof: ReaderNativePagePresentationProof) : ReaderPresentationFrameOwner
	data class Curl(val frame: ReaderCurlPresentationFrame) : ReaderPresentationFrameOwner
	data class LiveEngine(val proof: ReaderLiveEnginePresentationProof) : ReaderPresentationFrameOwner
}

sealed interface ReaderShellCoverRetainedFrame {
	val frameOwner: ReaderPresentationFrameOwner
	val binding: ReaderPresentationBinding

	data class Neutral(
		override val binding: ReaderPresentationBinding
	) : ReaderShellCoverRetainedFrame {
		override val frameOwner: ReaderPresentationFrameOwner = ReaderPresentationFrameOwner.Neutral
	}

	data class NativePage(
		val frame: ReaderPresentationFrameOwner.NativePage
	) : ReaderShellCoverRetainedFrame {
		override val frameOwner: ReaderPresentationFrameOwner = frame
		override val binding: ReaderPresentationBinding = frame.proof.binding
	}

	data class TerminalCurl(
		val frame: ReaderPresentationFrameOwner.Curl
	) : ReaderShellCoverRetainedFrame {
		override val frameOwner: ReaderPresentationFrameOwner = frame
		override val binding: ReaderPresentationBinding = frame.frame.binding
	}
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

data class ReaderNativePagePresentationRequest(
	val token: ReaderPresentationToken,
	val binding: ReaderPresentationBinding
)

sealed interface ReaderPresentationAuthority {
	data object Unavailable : ReaderPresentationAuthority
	data class ShellCover(val proof: ReaderShellCoverCommitProof) : ReaderPresentationAuthority
	data class ShellCoverCommitPending(
		val retainedFrame: ReaderShellCoverRetainedFrame,
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val coverGeneration: Long
	) : ReaderPresentationAuthority {
		init {
			require(retainedFrame.binding == binding)
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
	) : ReaderPresentationAuthority {
		init {
			require(
				when (direction) {
					ReaderLiveEngineHandoffDirection.NativeToLiveEngine ->
						retainedFrame is ReaderPresentationFrameOwner.NativePage ||
							retainedFrame is ReaderPresentationFrameOwner.ShellCover
					ReaderLiveEngineHandoffDirection.LiveEngineToNative ->
						retainedFrame is ReaderPresentationFrameOwner.LiveEngine
				}
			)
		}
	}
	data class LiveEngineExposed(
		val frame: ReaderPresentationFrameOwner.LiveEngine
	) : ReaderPresentationAuthority
	data class BlockingPreparation(
		val retainedFrame: ReaderPresentationFrameOwner,
		val nativePresentationRequest: ReaderNativePagePresentationRequest? = null
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
	PreparationFailed,
	PreparationUnavailable,
	RendererLost,
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

sealed interface ReaderRequiredTransition {
	data object None : ReaderRequiredTransition
	data class CommitShellCover(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val coverGeneration: Long
	) : ReaderRequiredTransition
	data class PresentNativePage(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val direction: ReaderLiveEngineHandoffDirection?
	) : ReaderRequiredTransition
	data class ExposeLiveEngine(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val direction: ReaderLiveEngineHandoffDirection
	) : ReaderRequiredTransition
}

data class ReaderPresentationDecision(
	val authority: ReaderPresentationAuthority,
	val frameOwner: ReaderPresentationFrameOwner,
	val layer: ReaderPresentationLayer,
	val inputPolicy: ReaderPresentationInputPolicy,
	val preparationPresentation: ReaderPreparationPresentation,
	val diagnosticPresentation: ReaderDiagnosticPresentation,
	val requiredTransition: ReaderRequiredTransition,
	val targetBinding: ReaderPresentationBinding?
) {
	internal fun targetsRendererDeckAlias(binding: ReaderPresentationBinding): Boolean =
		targetBinding?.let { target ->
			target != binding && target.sharesRendererDeckWith(binding)
		} == true
}

sealed interface ReaderPresentationLifecycleState {
	data object Foreground : ReaderPresentationLifecycleState
	data object Background : ReaderPresentationLifecycleState
	data object Destroyed : ReaderPresentationLifecycleState
}

enum class ReaderPresentationMemoryPressureLevel {
	Background,
	Moderate,
	Low,
	Critical,
	Complete
}

sealed interface ReaderPresentationLifecycleEvent {
	data object VisibilityLost : ReaderPresentationLifecycleEvent
	data object VisibilityRestored : ReaderPresentationLifecycleEvent
	data class RunningMemoryPressure(
		val level: ReaderPresentationMemoryPressureLevel
	) : ReaderPresentationLifecycleEvent
	data class BackgroundMemoryPressure(
		val level: ReaderPresentationMemoryPressureLevel
	) : ReaderPresentationLifecycleEvent
	data object RendererLost : ReaderPresentationLifecycleEvent
	data object PublicationClosed : ReaderPresentationLifecycleEvent
}

data class ReaderPagePreparationFacts(
	val phase: ReaderPagePreparationPhase = ReaderPagePreparationPhase.Idle,
	val generation: Long = 0L,
	val completedCount: Int = 0,
	val requiredCount: Int = 0,
	val readiness: ReaderPageReadinessState = ReaderPageReadinessState(),
	val failure: ReaderPresentationFailureReason? = null,
	val retryable: Boolean = false
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
	val rendererCleanupOwnership: List<ReaderRendererCleanupOwnership> = emptyList(),
	val lifecycle: ReaderPresentationLifecycleState = ReaderPresentationLifecycleState.Foreground,
	val preparationFacts: ReaderPagePreparationFacts = ReaderPagePreparationFacts(),
	val failure: ReaderDiagnosticPresentation.Failure? = null,
	val lastLiveEnginePresentedFrameSequence: Long = 0L,
	val nextTokenValue: Long = 1L
) {
	init {
		require(lastLiveEnginePresentedFrameSequence >= 0L)
		require(nextTokenValue > 0L)
		require(rendererCleanupOwnership.size <= 2)
		require(
			rendererCleanupOwnership.map { ownership ->
				ownership.binding.rendererDeckIdentityOrNull()
			}.distinct().size == rendererCleanupOwnership.size
		)
	}
}

sealed interface ReaderPresentationEvent {
	data class PublicationOpened(val binding: ReaderPresentationBinding) : ReaderPresentationEvent
	data class BindingCompleted(
		val previousBinding: ReaderPresentationBinding,
		val binding: ReaderPresentationBinding
	) : ReaderPresentationEvent {
		init {
			require(binding.isExactRendererCompletionOf(previousBinding))
		}
	}
	data class BindingReplaced(
		val previousBinding: ReaderPresentationBinding,
		val binding: ReaderPresentationBinding
	) : ReaderPresentationEvent {
		init {
			require(previousBinding.foliateSessionId == binding.foliateSessionId)
			require(previousBinding.publicationGeneration == binding.publicationGeneration)
			require(previousBinding != binding)
			require(
				previousBinding.hasCompleteRendererIdentity() ||
					!binding.hasAnyRendererIdentity()
			)
			require(
				!previousBinding.hasCompleteRendererIdentity() ||
					previousBinding.destinationCommitIdentity == binding.destinationCommitIdentity ||
					previousBinding.viewportGeneration != binding.viewportGeneration ||
					previousBinding.profileGeneration != binding.profileGeneration
			)
		}
	}
	data class FoliateRelocated(
		val binding: ReaderPresentationBinding,
		val acknowledgement: ReaderPageTurnSettlementAck?
	) : ReaderPresentationEvent
	data object NativePageRequested : ReaderPresentationEvent
	data class ShellCoverRequested(val coverGeneration: Long) : ReaderPresentationEvent {
		init {
			require(coverGeneration >= 0L)
		}
	}
	data class ShellCoverCommitted(val proof: ReaderShellCoverCommitProof) : ReaderPresentationEvent
	data object ShellCoverDismissalRequested : ReaderPresentationEvent
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
	data class LiveEngineExposureCommitted(
		val proof: ReaderLiveEnginePresentationProof
	) : ReaderPresentationEvent
	data class LiveEngineExposureFailed(
		val token: ReaderPresentationToken,
		val binding: ReaderPresentationBinding,
		val reason: ReaderPresentationFailureReason
	) : ReaderPresentationEvent {
		init {
			require(
				reason == ReaderPresentationFailureReason.LiveEngineUnavailable ||
					reason == ReaderPresentationFailureReason.NativePresentationUnavailable ||
					reason == ReaderPresentationFailureReason.TimedOut
			)
		}
	}
	data class PreparationReported(
		val binding: ReaderPresentationBinding,
		val facts: ReaderPagePreparationFacts
	) : ReaderPresentationEvent
	data class PreparationFailed(
		val binding: ReaderPresentationBinding,
		val facts: ReaderPagePreparationFacts,
		val reason: ReaderPresentationFailureReason,
		val cancellable: Boolean
	) : ReaderPresentationEvent
	data class TimedOut(val token: ReaderPresentationToken? = null) : ReaderPresentationEvent
	data object Retry : ReaderPresentationEvent
	data object Cancel : ReaderPresentationEvent
	data class Lifecycle(val event: ReaderPresentationLifecycleEvent) : ReaderPresentationEvent
}

enum class ReaderPresentationEffectKind {
	ReleaseStalePresentation,
	RetryPreparation
}

data class ReaderPresentationEffectIdentity(
	val kind: ReaderPresentationEffectKind,
	val token: ReaderPresentationToken?,
	val binding: ReaderPresentationBinding
)

sealed interface ReaderPresentationEffect {
	data class ReleaseStalePresentation(
		val token: ReaderPresentationToken?,
		val binding: ReaderPresentationBinding
	) : ReaderPresentationEffect

	data class RetryPreparation(
		val token: ReaderPresentationToken?,
		val binding: ReaderPresentationBinding
	) : ReaderPresentationEffect
}

fun ReaderPresentationEffect.identity(): ReaderPresentationEffectIdentity = when (this) {
	is ReaderPresentationEffect.ReleaseStalePresentation -> ReaderPresentationEffectIdentity(
		kind = ReaderPresentationEffectKind.ReleaseStalePresentation,
		token = token,
		binding = binding
	)
	is ReaderPresentationEffect.RetryPreparation -> ReaderPresentationEffectIdentity(
		kind = ReaderPresentationEffectKind.RetryPreparation,
		token = token,
		binding = binding
	)
}

enum class ReaderPresentationEventDisposition {
	Accepted,
	Idempotent,
	Rejected,
	Stale,
	Destroyed
}

data class ReaderPresentationReduction(
	val state: ReaderPresentationState,
	val decision: ReaderPresentationDecision,
	val effects: List<ReaderPresentationEffect>,
	val disposition: ReaderPresentationEventDisposition
)

fun readerPresentationDecision(state: ReaderPresentationState): ReaderPresentationDecision {
	val authority = state.authority
	val frameOwner = authority.frameOwner()
	val inputPolicy = when (state.lifecycle) {
		ReaderPresentationLifecycleState.Foreground -> authority.inputPolicy(
			state.binding,
			state.preparationFacts
		)
		ReaderPresentationLifecycleState.Background -> ReaderPresentationInputPolicy.ChromeOnly
		ReaderPresentationLifecycleState.Destroyed -> ReaderPresentationInputPolicy.RecoveryOnly
	}
	return ReaderPresentationDecision(
		authority = authority,
		frameOwner = frameOwner,
		layer = frameOwner.layer(),
		inputPolicy = inputPolicy,
		preparationPresentation = authority.preparationPresentation(state.preparationFacts, state.failure),
		diagnosticPresentation = state.failure ?: ReaderDiagnosticPresentation.Hidden,
		requiredTransition = if (authority.hasBlockingTransitionFailure(state.failure)) {
			ReaderRequiredTransition.None
		} else {
			authority.requiredTransition()
		},
		targetBinding = state.binding
	)
}

private fun ReaderPresentationAuthority.hasBlockingTransitionFailure(
	failure: ReaderDiagnosticPresentation.Failure?
): Boolean = failure?.reason == ReaderPresentationFailureReason.ShellCoverUnavailable ||
	(
		this is ReaderPresentationAuthority.LiveEngineHandoffPending &&
			(
				failure?.reason == ReaderPresentationFailureReason.LiveEngineUnavailable ||
					failure?.reason == ReaderPresentationFailureReason.NativePresentationUnavailable ||
					failure?.reason == ReaderPresentationFailureReason.TimedOut
			)
	)

fun readerPresentationReduce(
	state: ReaderPresentationState,
	event: ReaderPresentationEvent
): ReaderPresentationReduction {
	val result = if (state.lifecycle == ReaderPresentationLifecycleState.Destroyed) {
		event.closedResourceReleaseOrNull()?.let { effect ->
			destroyedPresentationResult(state, effects = listOf(effect))
		} ?: destroyedPresentationResult(state)
	} else when (event) {
		is ReaderPresentationEvent.PublicationOpened -> if (state.binding == event.binding) {
			idempotentPresentationResult(state)
		} else {
			acceptedPresentationResult(
				state = ReaderPresentationState(
					binding = event.binding,
					lifecycle = ReaderPresentationLifecycleState.Foreground,
					nextTokenValue = state.nextTokenValue
				)
			)
		}
		is ReaderPresentationEvent.BindingCompleted -> state.reduceBindingCompletion(event)
		is ReaderPresentationEvent.BindingReplaced -> state.reduceBindingReplacement(event)
		is ReaderPresentationEvent.FoliateRelocated -> state.reduceFoliateRelocation(event)
		ReaderPresentationEvent.NativePageRequested -> state.reduceNativePageRequest()
		is ReaderPresentationEvent.ShellCoverRequested -> {
			val retainedFrame = when (val authority = state.authority) {
				ReaderPresentationAuthority.Unavailable -> state.binding?.let { binding ->
					ReaderShellCoverRetainedFrame.Neutral(binding)
				}
				is ReaderPresentationAuthority.SettledNativePage ->
					ReaderShellCoverRetainedFrame.NativePage(authority.frame)
				is ReaderPresentationAuthority.CurlSettlementPending ->
					ReaderShellCoverRetainedFrame.TerminalCurl(authority.retainedFrame)
				else -> null
			}
			if (retainedFrame != null && state.binding == retainedFrame.binding) {
				val token = ReaderPresentationToken(state.nextTokenValue)
				acceptedPresentationResult(
					state.copy(
						authority = ReaderPresentationAuthority.ShellCoverCommitPending(
							retainedFrame = retainedFrame,
							token = token,
							binding = retainedFrame.binding,
							coverGeneration = event.coverGeneration
						),
						nextTokenValue = state.nextTokenValue + 1L
					)
				)
			} else {
				rejectedPresentationResult(state)
			}
		}
		is ReaderPresentationEvent.ShellCoverCommitted -> state.reduceShellCoverProof(event.proof)
		ReaderPresentationEvent.ShellCoverDismissalRequested -> state.reduceShellCoverDismissal()
		is ReaderPresentationEvent.ShellCoverFailed -> state.reduceShellCoverFailure(event)
		is ReaderPresentationEvent.ShellCoverEntered -> state.reduceShellCoverEntry(event.proof)
		is ReaderPresentationEvent.NativePagePresented -> state.reduceNativePageProof(event.proof)
		is ReaderPresentationEvent.CurlClaimed -> state.reduceCurlClaim(event.frame)
		is ReaderPresentationEvent.CurlTerminal -> state.reduceCurlTerminal(event)
		is ReaderPresentationEvent.WebViewHandoffRequested -> state.reduceWebViewHandoff(event.direction)
		is ReaderPresentationEvent.LiveEngineExposureCommitted ->
			state.reduceLiveEngineExposureProof(event.proof)
		is ReaderPresentationEvent.LiveEngineExposureFailed ->
			state.reduceLiveEngineExposureFailure(event)
		is ReaderPresentationEvent.PreparationReported -> state.reducePreparationReport(event)
		is ReaderPresentationEvent.PreparationFailed -> state.reducePreparationFailure(event)
		is ReaderPresentationEvent.TimedOut -> state.reduceTimeout(event)
		ReaderPresentationEvent.Retry -> state.reduceRetry()
		ReaderPresentationEvent.Cancel -> state.reduceCancel()
		is ReaderPresentationEvent.Lifecycle -> state.reduceLifecycle(event.event)
	}
	return ReaderPresentationReduction(
		state = result.state,
		decision = readerPresentationDecision(result.state),
		effects = result.effects,
		disposition = result.disposition
	)
}

private data class ReaderPresentationReducerResult(
	val state: ReaderPresentationState,
	val disposition: ReaderPresentationEventDisposition,
	val effects: List<ReaderPresentationEffect> = emptyList()
)

private fun acceptedPresentationResult(
	state: ReaderPresentationState,
	effects: List<ReaderPresentationEffect> = emptyList()
) = ReaderPresentationReducerResult(
	state = state,
	disposition = ReaderPresentationEventDisposition.Accepted,
	effects = effects
)

private fun idempotentPresentationResult(
	state: ReaderPresentationState,
	effects: List<ReaderPresentationEffect> = emptyList()
) = ReaderPresentationReducerResult(
	state = state,
	disposition = ReaderPresentationEventDisposition.Idempotent,
	effects = effects
)

private fun rejectedPresentationResult(
	state: ReaderPresentationState,
	effects: List<ReaderPresentationEffect> = emptyList()
) = ReaderPresentationReducerResult(
	state = state,
	disposition = ReaderPresentationEventDisposition.Rejected,
	effects = effects
)

private fun stalePresentationResult(
	state: ReaderPresentationState,
	effects: List<ReaderPresentationEffect> = emptyList()
) = ReaderPresentationReducerResult(
	state = state,
	disposition = ReaderPresentationEventDisposition.Stale,
	effects = effects
)

private fun destroyedPresentationResult(
	state: ReaderPresentationState,
	effects: List<ReaderPresentationEffect> = emptyList()
) = ReaderPresentationReducerResult(
	state = state,
	disposition = ReaderPresentationEventDisposition.Destroyed,
	effects = effects
)

private data class ReaderRendererCleanupTransition(
	val ownership: List<ReaderRendererCleanupOwnership>,
	val effects: List<ReaderPresentationEffect.ReleaseStalePresentation>
)

private fun ReaderPresentationState.trackRendererBindingTransition(
	previousBinding: ReaderPresentationBinding,
	binding: ReaderPresentationBinding,
	previousToken: ReaderPresentationToken?,
	bindingToken: ReaderPresentationToken?
): ReaderRendererCleanupTransition {
	val next = rendererCleanupOwnership.toMutableList()
	val releases = mutableListOf<ReaderPresentationEffect.ReleaseStalePresentation>()
	fun aliasIndex(candidate: ReaderPresentationBinding): Int = next.indexOfFirst { ownership ->
		ownership.binding.sharesRendererDeckWith(candidate)
	}
	if (previousBinding.hasCompleteRendererIdentity() && aliasIndex(previousBinding) < 0) {
		next += ReaderRendererCleanupOwnership(previousToken, previousBinding)
	}
	val previousIndex = aliasIndex(previousBinding)
	if (previousIndex >= 0 && next.size > 1) {
		val superseded = next.removeAt(previousIndex)
		if (!superseded.binding.sharesRendererDeckWith(binding)) {
			releases += ReaderPresentationEffect.ReleaseStalePresentation(
				token = superseded.token,
				binding = superseded.binding
			)
		}
	}
	if (binding.hasCompleteRendererIdentity()) {
		val alias = aliasIndex(binding)
		val ownership = ReaderRendererCleanupOwnership(bindingToken, binding)
		if (alias >= 0) {
			next[alias] = ownership
		} else {
			next += ownership
		}
	}
	check(next.size <= 2)
	return ReaderRendererCleanupTransition(next, releases.distinctByRendererDeck())
}

private fun ReaderPresentationState.adoptRendererBinding(
	binding: ReaderPresentationBinding
): ReaderRendererCleanupTransition = ReaderRendererCleanupTransition(
	ownership = emptyList(),
	effects = rendererCleanupOwnership
		.filterNot { ownership -> ownership.binding.sharesRendererDeckWith(binding) }
		.map { ownership ->
			ReaderPresentationEffect.ReleaseStalePresentation(
				token = ownership.token,
				binding = ownership.binding
			)
		}
		.distinctByRendererDeck()
)

private fun List<ReaderPresentationEffect.ReleaseStalePresentation>.distinctByRendererDeck():
	List<ReaderPresentationEffect.ReleaseStalePresentation> {
	val seen = mutableSetOf<ReaderRendererDeckIdentity>()
	return filter { effect ->
		effect.binding.rendererDeckIdentityOrNull()?.let(seen::add) == true
	}
}

private fun ReaderPresentationAuthority.rendererCleanupTokenFor(
	binding: ReaderPresentationBinding
): ReaderPresentationToken? = when (this) {
	is ReaderPresentationAuthority.BlockingPreparation -> {
		val cover = retainedFrame as? ReaderPresentationFrameOwner.ShellCover
		if (cover?.proof?.binding?.sharesRendererDeckWith(binding) == true) {
			cover.proof.token
		} else {
			nativePresentationRequest?.token ?: retainedFrame.presentationTokenOrNull()
		}
	}
	is ReaderPresentationAuthority.ShellCover -> proof.token
	else -> releaseIdentityTokenOrNull()
}

private fun ReaderPresentationAuthority.rendererCleanupTokenOrNull(): ReaderPresentationToken? =
	when (this) {
		is ReaderPresentationAuthority.BlockingPreparation ->
			nativePresentationRequest?.token ?: retainedFrame.presentationTokenOrNull()
		else -> releaseIdentityTokenOrNull()
	}

private fun ReaderPresentationState.rendererCleanupReleaseEffects():
	List<ReaderPresentationEffect.ReleaseStalePresentation> = rendererCleanupOwnership
	.map { ownership ->
		ReaderPresentationEffect.ReleaseStalePresentation(
			token = ownership.token,
			binding = ownership.binding
		)
	}
	.distinctByRendererDeck()

private fun ReaderPresentationState.reduceRetry(): ReaderPresentationReducerResult {
	val currentBinding = binding ?: return rejectedPresentationResult(this)
	val currentFailure = failure ?: return rejectedPresentationResult(this)
	val pendingCover = authority as? ReaderPresentationAuthority.ShellCoverCommitPending
	if (
		currentFailure.retryable &&
		pendingCover?.binding == currentBinding &&
		currentFailure.reason == ReaderPresentationFailureReason.ShellCoverUnavailable
	) {
		return acceptedPresentationResult(
			copy(
				authority = pendingCover.copy(
					token = ReaderPresentationToken(nextTokenValue)
				),
				failure = null,
				nextTokenValue = nextTokenValue + 1L
			)
		)
	}
	val pendingExposure = authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
	if (
		currentFailure.retryable &&
		pendingExposure?.binding == currentBinding &&
		(
			currentFailure.reason == ReaderPresentationFailureReason.LiveEngineUnavailable ||
				currentFailure.reason == ReaderPresentationFailureReason.NativePresentationUnavailable ||
				currentFailure.reason == ReaderPresentationFailureReason.TimedOut
		)
	) {
		return acceptedPresentationResult(
			copy(
				authority = pendingExposure.copy(
					token = ReaderPresentationToken(nextTokenValue)
				),
				failure = null,
				nextTokenValue = nextTokenValue + 1L
			)
		)
	}
	if (
		!currentFailure.retryable ||
		currentFailure.reason != ReaderPresentationFailureReason.PreparationFailed
	) {
		return rejectedPresentationResult(this)
	}
	val token = when (val transition = authority.requiredTransition()) {
		is ReaderRequiredTransition.CommitShellCover -> transition.token
		is ReaderRequiredTransition.PresentNativePage -> transition.token
		is ReaderRequiredTransition.ExposeLiveEngine -> transition.token
		ReaderRequiredTransition.None -> null
	}
	return acceptedPresentationResult(
		state = this,
		effects = listOf(
			ReaderPresentationEffect.RetryPreparation(
				token = token,
				binding = currentBinding
			)
		)
	)
}

private fun ReaderPresentationState.reduceCancel(): ReaderPresentationReducerResult {
	val pending = authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
		?: return rejectedPresentationResult(this)
	val restored = when (val retained = pending.retainedFrame) {
		is ReaderPresentationFrameOwner.NativePage ->
			ReaderPresentationAuthority.SettledNativePage(retained)
		is ReaderPresentationFrameOwner.LiveEngine ->
			ReaderPresentationAuthority.LiveEngineExposed(retained)
		is ReaderPresentationFrameOwner.ShellCover ->
			ReaderPresentationAuthority.ShellCover(retained.proof)
		else -> return rejectedPresentationResult(this)
	}
	return acceptedPresentationResult(
		copy(authority = restored, failure = null)
	)
}

private fun ReaderPresentationEvent.closedResourceReleaseOrNull():
	ReaderPresentationEffect.ReleaseStalePresentation? = when (this) {
	is ReaderPresentationEvent.BindingCompleted ->
		ReaderPresentationEffect.ReleaseStalePresentation(null, binding)
	is ReaderPresentationEvent.ShellCoverCommitted ->
		ReaderPresentationEffect.ReleaseStalePresentation(proof.token, proof.binding)
	is ReaderPresentationEvent.NativePagePresented ->
		ReaderPresentationEffect.ReleaseStalePresentation(
			proof.transitionToken,
			proof.binding
		)
	is ReaderPresentationEvent.CurlClaimed ->
		ReaderPresentationEffect.ReleaseStalePresentation(frame.token, frame.binding)
	is ReaderPresentationEvent.LiveEngineExposureCommitted ->
		ReaderPresentationEffect.ReleaseStalePresentation(proof.token, proof.binding)
	else -> null
}

private fun ReaderPresentationState.reduceFoliateRelocation(
	event: ReaderPresentationEvent.FoliateRelocated
): ReaderPresentationReducerResult {
	val authority = authority
	val currentBinding = binding
	return when {
		authority == ReaderPresentationAuthority.Unavailable ->
			acceptedPresentationResult(copy(binding = event.binding))
		currentBinding == event.binding -> idempotentPresentationResult(this)
		authority is ReaderPresentationAuthority.BlockingPreparation &&
			authority.nativePresentationRequest != null &&
			currentBinding != null &&
			event.binding.isCausalDestinationSuccessorOf(currentBinding) -> {
			val reboundAuthority = authority.rebindRequestedPresentation(
				previousBinding = currentBinding,
				binding = event.binding
			) ?: return rejectedPresentationResult(this)
			val cleanup = trackRendererBindingTransition(
				previousBinding = currentBinding,
				binding = event.binding,
				previousToken = authority.rendererCleanupTokenFor(currentBinding),
				bindingToken = reboundAuthority.rendererCleanupTokenOrNull()
			)
			acceptedPresentationResult(
				state = copy(
					authority = reboundAuthority,
					binding = event.binding,
					rendererCleanupOwnership = cleanup.ownership
				),
				effects = cleanup.effects
			)
		}
		authority is ReaderPresentationAuthority.SettledNativePage &&
			authority.frame.proof.binding == event.binding ->
			idempotentPresentationResult(this)
		authority is ReaderPresentationAuthority.SettledNativePage &&
			currentBinding?.let(event.binding::isCompleteDestinationSuccessorOf) == true ->
			acceptedPresentationResult(
				copy(
					binding = event.binding,
					preparationFacts = ReaderPagePreparationFacts(),
					failure = null
				)
			)
		!event.binding.hasCompleteRendererIdentity() -> rejectedPresentationResult(this)
		else -> stalePresentation(authority.tokenOrNull(), event.binding)
	}
}

private fun ReaderPresentationBinding.isCompleteDestinationSuccessorOf(
	predecessor: ReaderPresentationBinding
): Boolean = hasCompleteRendererIdentity() &&
	preparationGeneration != null &&
	isCausalDestinationSuccessorOf(predecessor)

private fun ReaderPresentationState.reduceNativePageRequest(): ReaderPresentationReducerResult {
	val currentBinding = binding ?: return rejectedPresentationResult(this)
	if (authority != ReaderPresentationAuthority.Unavailable) {
		return if (
			authority is ReaderPresentationAuthority.BlockingPreparation &&
			authority.nativePresentationRequest?.binding == currentBinding
		) {
			idempotentPresentationResult(this)
		} else {
			rejectedPresentationResult(this)
		}
	}
	val request = ReaderNativePagePresentationRequest(
		token = ReaderPresentationToken(nextTokenValue),
		binding = currentBinding
	)
	return acceptedPresentationResult(
		copy(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				retainedFrame = ReaderPresentationFrameOwner.Neutral,
				nativePresentationRequest = request
			),
			nextTokenValue = nextTokenValue + 1L
		)
	)
}

private fun ReaderPresentationState.reduceBindingCompletion(
	event: ReaderPresentationEvent.BindingCompleted
): ReaderPresentationReducerResult {
	if (binding == event.binding) return idempotentPresentationResult(this)
	if (binding != event.previousBinding) {
		return stalePresentation(token = null, binding = event.binding)
	}
	val reboundAuthority = authority.rebindPartialPresentation(
		previousBinding = event.previousBinding,
		binding = event.binding
	) ?: return stalePresentation(token = null, binding = event.binding)
	val cleanup = trackRendererBindingTransition(
		previousBinding = event.previousBinding,
		binding = event.binding,
		previousToken = authority.rendererCleanupTokenFor(event.previousBinding),
		bindingToken = reboundAuthority.rendererCleanupTokenOrNull()
	)
	return acceptedPresentationResult(
		state = copy(
			authority = reboundAuthority,
			binding = event.binding,
			rendererCleanupOwnership = cleanup.ownership
		),
		effects = cleanup.effects
	)
}

private fun ReaderPresentationAuthority.BlockingPreparation.rebindRequestedPresentation(
	previousBinding: ReaderPresentationBinding,
	binding: ReaderPresentationBinding
): ReaderPresentationAuthority.BlockingPreparation? {
	val request = nativePresentationRequest?.takeIf { it.binding == previousBinding } ?: return null
	val retained = when (val frame = retainedFrame) {
		ReaderPresentationFrameOwner.Neutral -> frame
		is ReaderPresentationFrameOwner.ShellCover -> when {
			frame.proof.binding.hasCompleteRendererIdentity() -> frame
			frame.proof.binding == previousBinding ||
				previousBinding.isExactRendererCompletionOf(frame.proof.binding) ->
				ReaderPresentationFrameOwner.ShellCover(
					frame.proof.copy(
						binding = binding.copy(rasterGeneration = null, textureGeneration = null)
					)
				)
			else -> return null
		}
		is ReaderPresentationFrameOwner.NativePage,
		is ReaderPresentationFrameOwner.Curl,
		is ReaderPresentationFrameOwner.LiveEngine -> return null
	}
	return copy(
		retainedFrame = retained,
		nativePresentationRequest = request.copy(binding = binding)
	)
}

private fun ReaderPresentationBinding.retainedCoverSuccessorBinding(
	binding: ReaderPresentationBinding
): ReaderPresentationBinding = if (!hasAnyRendererIdentity()) {
	binding.copy(rasterGeneration = null, textureGeneration = null)
} else {
	binding
}

private fun ReaderPresentationAuthority.rebindPartialPresentation(
	previousBinding: ReaderPresentationBinding,
	binding: ReaderPresentationBinding
): ReaderPresentationAuthority? = when (this) {
	ReaderPresentationAuthority.Unavailable -> this
	is ReaderPresentationAuthority.ShellCover -> proof
		.takeIf { it.binding == previousBinding }
		?.copy(binding = proof.binding.retainedCoverSuccessorBinding(binding))
		?.let(ReaderPresentationAuthority::ShellCover)
	is ReaderPresentationAuthority.ShellCoverCommitPending -> if (
		this.binding == previousBinding && retainedFrame.binding == previousBinding
	) {
		copy(
			retainedFrame = retainedFrame.rebindPartialPresentation(previousBinding, binding),
			binding = binding
		)
	} else {
		null
	}
	is ReaderPresentationAuthority.BlockingPreparation -> {
		val request = nativePresentationRequest
		if (request?.binding != previousBinding) {
			null
		} else {
			retainedFrame.rebindPartialPresentation(previousBinding, binding)?.let { retained ->
				copy(
					retainedFrame = retained,
					nativePresentationRequest = request.copy(binding = binding)
				)
			}
		}
	}
	is ReaderPresentationAuthority.CurlGesture,
	is ReaderPresentationAuthority.CurlSettlementPending,
	is ReaderPresentationAuthority.SettledNativePage,
	is ReaderPresentationAuthority.LiveEngineHandoffPending,
	is ReaderPresentationAuthority.LiveEngineExposed -> null
}

private fun ReaderShellCoverRetainedFrame.rebindPartialPresentation(
	previousBinding: ReaderPresentationBinding,
	binding: ReaderPresentationBinding
): ReaderShellCoverRetainedFrame = when (this) {
	is ReaderShellCoverRetainedFrame.Neutral -> {
		check(this.binding == previousBinding)
		copy(binding = binding)
	}
	is ReaderShellCoverRetainedFrame.NativePage,
	is ReaderShellCoverRetainedFrame.TerminalCurl -> error(
		"Renderer-backed frame cannot retain a partial presentation"
	)
}

private fun ReaderPresentationFrameOwner.rebindPartialPresentation(
	previousBinding: ReaderPresentationBinding,
	binding: ReaderPresentationBinding
): ReaderPresentationFrameOwner? = when (this) {
	ReaderPresentationFrameOwner.Neutral -> this
	is ReaderPresentationFrameOwner.ShellCover -> proof
		.takeIf { it.binding == previousBinding }
		?.copy(binding = proof.binding.retainedCoverSuccessorBinding(binding))
		?.let(ReaderPresentationFrameOwner::ShellCover)
	is ReaderPresentationFrameOwner.NativePage,
	is ReaderPresentationFrameOwner.Curl,
	is ReaderPresentationFrameOwner.LiveEngine -> null
}

private fun ReaderPresentationState.reduceBindingReplacement(
	event: ReaderPresentationEvent.BindingReplaced
): ReaderPresentationReducerResult {
	val requested = (authority as? ReaderPresentationAuthority.BlockingPreparation)
		?.takeIf { pending -> pending.nativePresentationRequest?.binding == event.previousBinding }
	if (
		binding == event.previousBinding &&
		requested != null &&
		event.binding.isSafeBindingReplacementOf(event.previousBinding)
	) {
		val reboundAuthority = requested.rebindRequestedPresentation(
			event.previousBinding,
			event.binding
		) ?: return rejectedPresentationResult(this)
		val cleanup = trackRendererBindingTransition(
			previousBinding = event.previousBinding,
			binding = event.binding,
			previousToken = authority.rendererCleanupTokenFor(event.previousBinding),
			bindingToken = reboundAuthority.rendererCleanupTokenOrNull()
		)
		val preservesPreparation =
			event.binding.isResolvedProfileReplacementOf(event.previousBinding)
		return acceptedPresentationResult(
			state = copy(
				authority = reboundAuthority,
				binding = event.binding,
				rendererCleanupOwnership = cleanup.ownership,
				preparationFacts = if (preservesPreparation) {
					preparationFacts
				} else {
					ReaderPagePreparationFacts()
				},
				failure = failure.takeIf { preservesPreparation }
			),
			effects = cleanup.effects
		)
	}
	if (
		binding == event.previousBinding &&
		!event.previousBinding.hasAnyRendererIdentity() &&
		!event.binding.hasAnyRendererIdentity()
	) {
		val reboundAuthority = authority.rebindPartialPresentation(
			event.previousBinding,
			event.binding
		) ?: return rejectedPresentationResult(this)
		return acceptedPresentationResult(
			copy(
				authority = reboundAuthority,
				binding = event.binding,
				preparationFacts = if (
					event.binding.isResolvedProfileReplacementOf(event.previousBinding)
				) {
					preparationFacts
				} else {
					ReaderPagePreparationFacts()
				},
				failure = if (
					event.binding.isResolvedProfileReplacementOf(event.previousBinding)
				) {
					failure
				} else {
					null
				}
			)
		)
	}
	return when {
		binding == event.binding -> idempotentPresentationResult(this)
		binding != event.previousBinding ||
			!event.binding.isCompleteBindingReplacementOf(event.previousBinding) ->
			rejectedPresentationResult(this)
		else -> acceptedPresentationResult(
			state = copy(
				authority = ReaderPresentationAuthority.Unavailable,
				binding = event.binding,
				preparationFacts = ReaderPagePreparationFacts(),
				failure = null
			),
			effects = if (authority.frameOwner() == ReaderPresentationFrameOwner.Neutral) {
				emptyList()
			} else {
				listOf(
					ReaderPresentationEffect.ReleaseStalePresentation(
						token = authority.releaseIdentityTokenOrNull(),
						binding = event.previousBinding
					)
				)
			}
		)
	}
}

private fun ReaderPresentationBinding.isResolvedProfileReplacementOf(
	predecessor: ReaderPresentationBinding
): Boolean = predecessor.profileGeneration == 0L &&
	profileGeneration > 0L &&
	foliateSessionId == predecessor.foliateSessionId &&
	publicationGeneration == predecessor.publicationGeneration &&
	viewportGeneration == predecessor.viewportGeneration &&
	destinationCommitIdentity == predecessor.destinationCommitIdentity &&
	preparationGeneration == predecessor.preparationGeneration &&
	!predecessor.hasAnyRendererIdentity() &&
	!hasAnyRendererIdentity()

private fun ReaderPresentationBinding.isCompleteBindingReplacementOf(
	predecessor: ReaderPresentationBinding
): Boolean {
	if (
		foliateSessionId != predecessor.foliateSessionId ||
		publicationGeneration != predecessor.publicationGeneration ||
		rasterGeneration == null ||
		textureGeneration == null ||
		preparationGeneration == null
	) {
		return false
	}
	val predecessorDestination = predecessor.destinationCommitIdentity
	val destination = destinationCommitIdentity
	if (destination == predecessorDestination) return this != predecessor
	return (viewportGeneration != predecessor.viewportGeneration ||
		profileGeneration != predecessor.profileGeneration) &&
		predecessorDestination != null &&
		destination != null &&
		destination.commitSequence > predecessorDestination.commitSequence
}

private fun ReaderPresentationState.reduceLifecycle(
	event: ReaderPresentationLifecycleEvent
): ReaderPresentationReducerResult = when (event) {
	ReaderPresentationLifecycleEvent.VisibilityLost -> if (
		lifecycle == ReaderPresentationLifecycleState.Background
	) {
		idempotentPresentationResult(this)
	} else {
		acceptedPresentationResult(copy(lifecycle = ReaderPresentationLifecycleState.Background))
	}
	ReaderPresentationLifecycleEvent.VisibilityRestored -> if (
		lifecycle == ReaderPresentationLifecycleState.Foreground
	) {
		idempotentPresentationResult(this)
	} else {
		acceptedPresentationResult(copy(lifecycle = ReaderPresentationLifecycleState.Foreground))
	}
	is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
	is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure -> acceptedPresentationResult(this)
	ReaderPresentationLifecycleEvent.RendererLost -> acceptedPresentationResult(
		copy(
			authority = if (
				authority is ReaderPresentationAuthority.BlockingPreparation &&
				authority.nativePresentationRequest != null
			) {
				authority
			} else {
				ReaderPresentationAuthority.BlockingPreparation(authority.frameOwner())
			},
			rendererCleanupOwnership = emptyList(),
			failure = ReaderDiagnosticPresentation.Failure(
				reason = ReaderPresentationFailureReason.RendererLost,
				retryable = true,
				cancellable = false
			)
		)
	)
	ReaderPresentationLifecycleEvent.PublicationClosed -> acceptedPresentationResult(
		state = ReaderPresentationState(
			lifecycle = ReaderPresentationLifecycleState.Destroyed,
			nextTokenValue = nextTokenValue
		),
		effects = rendererCleanupReleaseEffects()
	)
}

private fun ReaderPresentationState.reduceShellCoverDismissal(): ReaderPresentationReducerResult {
	val cover = authority as? ReaderPresentationAuthority.ShellCover
		?: return rejectedPresentationResult(this)
	val currentBinding = binding?.takeIf {
		it == cover.proof.binding || it.isExactRendererCompletionOf(cover.proof.binding)
	} ?: return rejectedPresentationResult(this)
	val request = ReaderNativePagePresentationRequest(
		token = ReaderPresentationToken(nextTokenValue),
		binding = currentBinding
	)
	return acceptedPresentationResult(
		copy(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				retainedFrame = ReaderPresentationFrameOwner.ShellCover(cover.proof),
				nativePresentationRequest = request
			),
			nextTokenValue = nextTokenValue + 1L,
			failure = null
		)
	)
}

private fun ReaderPresentationState.reduceShellCoverProof(
	proof: ReaderShellCoverCommitProof
): ReaderPresentationReducerResult {
	val authority = authority
	return when {
		authority is ReaderPresentationAuthority.ShellCoverCommitPending &&
			authority.token == proof.token &&
			authority.binding == proof.binding &&
			binding == proof.binding &&
			authority.coverGeneration == proof.coverGeneration ->
			acceptedPresentationResult(
				copy(authority = ReaderPresentationAuthority.ShellCover(proof))
			)
		authority is ReaderPresentationAuthority.ShellCover &&
			authority.proof == proof && binding == proof.binding -> idempotentPresentationResult(this)
		else -> staleProof(proof.token, proof.binding)
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
		acceptedPresentationResult(
			copy(
				failure = ReaderDiagnosticPresentation.Failure(
					reason = ReaderPresentationFailureReason.ShellCoverUnavailable,
					retryable = true,
					cancellable = false
				)
			)
		)
	} else {
		staleProof(event.token, event.binding)
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
		idempotentPresentationResult(this)
	} else {
		staleProof(proof.token, proof.binding)
	}
}

private fun ReaderPresentationState.reduceNativePageProof(
	proof: ReaderNativePagePresentationProof
): ReaderPresentationReducerResult = when (val authority = authority) {
	ReaderPresentationAuthority.Unavailable -> if (binding == null || binding == proof.binding) {
		val cleanup = adoptRendererBinding(proof.binding)
		acceptedPresentationResult(
			state = copy(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(proof)
				),
				binding = proof.binding,
				rendererCleanupOwnership = cleanup.ownership,
				failure = null
			),
			effects = cleanup.effects
		)
	} else {
		staleProof(proof.transitionToken, proof.binding)
	}
	is ReaderPresentationAuthority.SettledNativePage -> when {
		authority.frame.proof == proof && binding == proof.binding ->
			idempotentPresentationResult(this)
		proof.transitionToken == null &&
			binding == proof.binding &&
			proof.binding.isCompleteDestinationSuccessorOf(authority.frame.proof.binding) -> {
			val cleanup = adoptRendererBinding(proof.binding)
			val retainedRelease = ReaderPresentationEffect.ReleaseStalePresentation(
				token = authority.frame.proof.transitionToken,
				binding = authority.frame.proof.binding
			)
			acceptedPresentationResult(
				state = copy(
					authority = ReaderPresentationAuthority.SettledNativePage(
						ReaderPresentationFrameOwner.NativePage(proof)
					),
					rendererCleanupOwnership = cleanup.ownership,
					failure = null
				),
				effects = (cleanup.effects + retainedRelease).distinctByRendererDeck()
			)
		}
		else -> staleProof(proof.transitionToken, proof.binding)
	}
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> if (
		authority.direction == ReaderLiveEngineHandoffDirection.LiveEngineToNative &&
		authority.retainedFrame is ReaderPresentationFrameOwner.LiveEngine &&
		authority.token == proof.transitionToken &&
		authority.binding == proof.binding &&
		binding == proof.binding
	) {
		val cleanup = adoptRendererBinding(proof.binding)
		acceptedPresentationResult(
			state = copy(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(proof)
				),
				rendererCleanupOwnership = cleanup.ownership,
				failure = null
			),
			effects = cleanup.effects
		)
	} else {
		staleProof(proof.transitionToken, proof.binding)
	}
	is ReaderPresentationAuthority.BlockingPreparation -> {
		val request = authority.nativePresentationRequest
		val retainedCover = authority.retainedFrame as? ReaderPresentationFrameOwner.ShellCover
		if (
			request != null &&
			(retainedCover != null || authority.retainedFrame == ReaderPresentationFrameOwner.Neutral) &&
			request.token == proof.transitionToken &&
			request.binding == proof.binding &&
			binding == proof.binding
		) {
			val cleanup = adoptRendererBinding(proof.binding)
			val coverRelease = retainedCover
				?.takeIf { it.proof.binding.hasCompleteRendererIdentity() }
				?.let { cover ->
					ReaderPresentationEffect.ReleaseStalePresentation(
						token = cover.proof.token,
						binding = cover.proof.binding
					)
				}
			acceptedPresentationResult(
				state = copy(
					authority = ReaderPresentationAuthority.SettledNativePage(
						ReaderPresentationFrameOwner.NativePage(proof)
					),
					rendererCleanupOwnership = cleanup.ownership,
					failure = null
				),
				effects = (cleanup.effects + listOfNotNull(coverRelease))
					.distinctByRendererDeck()
			)
		} else {
			staleProof(proof.transitionToken, proof.binding)
		}
	}
	else -> staleProof(proof.transitionToken, proof.binding)
}

private fun ReaderPresentationState.reduceCurlClaim(
	frame: ReaderCurlPresentationFrame
): ReaderPresentationReducerResult {
	val authority = authority
	return when {
		authority is ReaderPresentationAuthority.SettledNativePage &&
			binding == frame.binding && authority.frame.proof.binding == frame.binding ->
			acceptedPresentationResult(
				copy(
					authority = ReaderPresentationAuthority.CurlGesture(
						ReaderPresentationFrameOwner.Curl(frame)
					),
					nextTokenValue = nextTokenAfter(frame.token)
				)
			)
		authority is ReaderPresentationAuthority.CurlGesture &&
			authority.frame.frame == frame && binding == frame.binding ->
			idempotentPresentationResult(this)
		else -> staleProof(frame.token, frame.binding)
	}
}

private fun ReaderPresentationState.reduceCurlTerminal(
	event: ReaderPresentationEvent.CurlTerminal
): ReaderPresentationReducerResult {
	val authority = authority
	return when {
		authority is ReaderPresentationAuthority.CurlGesture &&
			authority.frame.frame.token == event.token &&
			authority.frame.frame.binding == event.binding && binding == event.binding ->
			acceptedPresentationResult(
				copy(
					authority = ReaderPresentationAuthority.CurlSettlementPending(
						retainedFrame = authority.frame,
						stage = event.stage
					),
					preparationFacts = preparationFacts.copy(failure = null),
					failure = null
				)
			)
		authority is ReaderPresentationAuthority.CurlSettlementPending &&
			authority.retainedFrame.frame.token == event.token &&
			authority.retainedFrame.frame.binding == event.binding &&
			authority.stage == event.stage && binding == event.binding -> idempotentPresentationResult(this)
		else -> staleProof(event.token, event.binding)
	}
}

private fun ReaderPresentationState.reduceWebViewHandoff(
	direction: ReaderLiveEngineHandoffDirection
): ReaderPresentationReducerResult {
	val currentBinding = binding ?: return rejectedPresentationResult(this)
	val currentAuthority = authority
	if (currentAuthority is ReaderPresentationAuthority.LiveEngineHandoffPending) {
		if (currentAuthority.direction == direction) return idempotentPresentationResult(this)
		return when {
			direction == ReaderLiveEngineHandoffDirection.LiveEngineToNative &&
				currentAuthority.retainedFrame is ReaderPresentationFrameOwner.NativePage ->
				acceptedPresentationResult(
					copy(
						authority = ReaderPresentationAuthority.SettledNativePage(
							currentAuthority.retainedFrame
						),
						failure = null
					)
				)
			direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine &&
				currentAuthority.retainedFrame is ReaderPresentationFrameOwner.LiveEngine ->
				acceptedPresentationResult(
					copy(
						authority = ReaderPresentationAuthority.LiveEngineExposed(
							currentAuthority.retainedFrame
						),
						failure = null
					)
				)
			else -> rejectedPresentationResult(this)
		}
	}
	val retainedFrame = when {
		currentAuthority is ReaderPresentationAuthority.SettledNativePage &&
			direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine ->
			currentAuthority.frame
		currentAuthority is ReaderPresentationAuthority.ShellCover &&
			direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine ->
			ReaderPresentationFrameOwner.ShellCover(currentAuthority.proof)
		currentAuthority is ReaderPresentationAuthority.LiveEngineExposed &&
			direction == ReaderLiveEngineHandoffDirection.LiveEngineToNative ->
			currentAuthority.frame
		else -> return rejectedPresentationResult(this)
	}
	return acceptedPresentationResult(
		copy(
			authority = ReaderPresentationAuthority.LiveEngineHandoffPending(
				retainedFrame = retainedFrame,
				token = ReaderPresentationToken(nextTokenValue),
				binding = currentBinding,
				direction = direction
			),
			nextTokenValue = nextTokenValue + 1L,
			failure = null
		)
	)
}

private fun ReaderPresentationState.reduceLiveEngineExposureProof(
	proof: ReaderLiveEnginePresentationProof
): ReaderPresentationReducerResult {
	val currentAuthority = authority
	return when {
		currentAuthority is ReaderPresentationAuthority.LiveEngineHandoffPending &&
			currentAuthority.direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine &&
			currentAuthority.token == proof.token &&
			currentAuthority.binding == proof.binding &&
			binding == proof.binding &&
			proof.presentedFrameSequence > lastLiveEnginePresentedFrameSequence ->
			acceptedPresentationResult(
				copy(
					authority = ReaderPresentationAuthority.LiveEngineExposed(
						ReaderPresentationFrameOwner.LiveEngine(proof)
					),
					failure = null,
					lastLiveEnginePresentedFrameSequence = proof.presentedFrameSequence
				)
			)
		currentAuthority is ReaderPresentationAuthority.LiveEngineExposed &&
			currentAuthority.frame.proof == proof && binding == proof.binding ->
			idempotentPresentationResult(this)
		else -> staleProof(proof.token, proof.binding)
	}
}

private fun ReaderPresentationState.reduceLiveEngineExposureFailure(
	event: ReaderPresentationEvent.LiveEngineExposureFailed
): ReaderPresentationReducerResult {
	val currentAuthority = authority
	return if (
		currentAuthority is ReaderPresentationAuthority.LiveEngineHandoffPending &&
			currentAuthority.token == event.token &&
			currentAuthority.binding == event.binding &&
			binding == event.binding &&
			when (currentAuthority.direction) {
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine ->
					event.reason != ReaderPresentationFailureReason.NativePresentationUnavailable
				ReaderLiveEngineHandoffDirection.LiveEngineToNative ->
					event.reason != ReaderPresentationFailureReason.LiveEngineUnavailable
			}
	) {
		acceptedPresentationResult(
			copy(
				failure = ReaderDiagnosticPresentation.Failure(
					reason = event.reason,
					retryable = true,
					cancellable = true
				)
			)
		)
	} else {
		staleProof(event.token, event.binding)
	}
}

private fun ReaderPresentationState.reducePreparationReport(
	event: ReaderPresentationEvent.PreparationReported
): ReaderPresentationReducerResult {
	if (!matchesPreparation(event.binding, event.facts)) {
		return stalePresentationResult(this)
	}
	return acceptedPresentationResult(
		copy(
			preparationFacts = event.facts,
			failure = if (
				event.facts.phase == ReaderPagePreparationPhase.Ready &&
				!authority.hasBlockingTransitionFailure(failure)
			) {
				null
			} else {
				failure
			}
		)
	)
}

private fun ReaderPresentationState.reducePreparationFailure(
	event: ReaderPresentationEvent.PreparationFailed
): ReaderPresentationReducerResult {
	if (!matchesPreparation(event.binding, event.facts)) {
		return stalePresentationResult(this)
	}
	val diagnostic = ReaderDiagnosticPresentation.Failure(
		reason = event.reason,
		retryable = true,
		cancellable = event.cancellable
	)
	val nextAuthority = if (
		authority.hasTruthfulStableFrame() ||
			authority is ReaderPresentationAuthority.ShellCoverCommitPending ||
			authority is ReaderPresentationAuthority.BlockingPreparation &&
			authority.nativePresentationRequest != null
	) {
		authority
	} else {
		ReaderPresentationAuthority.BlockingPreparation(authority.frameOwner())
	}
	return acceptedPresentationResult(
		copy(
			authority = nextAuthority,
			preparationFacts = event.facts.copy(
				phase = ReaderPagePreparationPhase.Failed,
				failure = event.reason
			),
			failure = diagnostic
		)
	)
}

private fun ReaderPresentationState.matchesPreparation(
	eventBinding: ReaderPresentationBinding,
	facts: ReaderPagePreparationFacts
): Boolean = binding == eventBinding && eventBinding.preparationGeneration == facts.generation

private fun ReaderPresentationAuthority.hasTruthfulStableFrame(): Boolean = when (this) {
	ReaderPresentationAuthority.Unavailable,
	is ReaderPresentationAuthority.BlockingPreparation -> false
	is ReaderPresentationAuthority.ShellCoverCommitPending ->
		retainedFrame.frameOwner != ReaderPresentationFrameOwner.Neutral
	is ReaderPresentationAuthority.ShellCover,
	is ReaderPresentationAuthority.CurlGesture,
	is ReaderPresentationAuthority.CurlSettlementPending,
	is ReaderPresentationAuthority.SettledNativePage,
	is ReaderPresentationAuthority.LiveEngineHandoffPending,
	is ReaderPresentationAuthority.LiveEngineExposed -> true
}

private fun ReaderPresentationState.reduceTimeout(
	event: ReaderPresentationEvent.TimedOut
): ReaderPresentationReducerResult {
	val expectedToken = authority.tokenOrNull()
	val currentBinding = binding
	if (event.token != null && event.token != expectedToken && currentBinding != null) {
		return stalePresentation(event.token, currentBinding)
	}
	val pending = authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
	if (pending != null && event.token == pending.token && currentBinding == pending.binding) {
		return acceptedPresentationResult(
			copy(
				failure = ReaderDiagnosticPresentation.Failure(
					reason = ReaderPresentationFailureReason.TimedOut,
					retryable = true,
					cancellable = true
				)
			)
		)
	}
	return acceptedPresentationResult(this)
}

private fun ReaderPresentationState.staleProof(
	token: ReaderPresentationToken?,
	binding: ReaderPresentationBinding
): ReaderPresentationReducerResult = if (authority.matchesPresentationIdentity(token, binding)) {
	stalePresentationResult(this)
} else {
	stalePresentation(token, binding)
}

internal fun ReaderPresentationDecision.retainsPresentationIdentity(
	token: ReaderPresentationToken?,
	binding: ReaderPresentationBinding
): Boolean = authority.matchesPresentationIdentity(token, binding) ||
	frameOwner.matchesPresentationIdentity(token, binding) ||
	authority.retainsPresentationBinding(binding)

private fun ReaderPresentationAuthority.retainsPresentationBinding(
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	ReaderPresentationAuthority.Unavailable -> false
	is ReaderPresentationAuthority.ShellCover -> proof.binding == binding
	is ReaderPresentationAuthority.ShellCoverCommitPending ->
		this.binding == binding || retainedFrame.binding == binding
	is ReaderPresentationAuthority.CurlGesture -> frame.frame.binding == binding
	is ReaderPresentationAuthority.CurlSettlementPending -> retainedFrame.frame.binding == binding
	is ReaderPresentationAuthority.SettledNativePage -> frame.proof.binding == binding
	is ReaderPresentationAuthority.LiveEngineHandoffPending ->
		this.binding == binding || retainedFrame.retainsPresentationBinding(binding)
	is ReaderPresentationAuthority.LiveEngineExposed -> frame.proof.binding == binding
	is ReaderPresentationAuthority.BlockingPreparation ->
		this.nativePresentationRequest?.binding == binding ||
			retainedFrame.retainsPresentationBinding(binding)
}

private fun ReaderPresentationFrameOwner.retainsPresentationBinding(
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	ReaderPresentationFrameOwner.Neutral -> false
	is ReaderPresentationFrameOwner.ShellCover -> proof.binding == binding
	is ReaderPresentationFrameOwner.NativePage -> proof.binding == binding
	is ReaderPresentationFrameOwner.Curl -> frame.binding == binding
	is ReaderPresentationFrameOwner.LiveEngine -> proof.binding == binding
}

private fun ReaderPresentationAuthority.matchesPresentationIdentity(
	token: ReaderPresentationToken?,
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	ReaderPresentationAuthority.Unavailable -> false
	is ReaderPresentationAuthority.ShellCover ->
		proof.token == token && proof.binding == binding
	is ReaderPresentationAuthority.ShellCoverCommitPending ->
		(this.token == token && this.binding == binding) ||
			retainedFrame.frameOwner.matchesPresentationIdentity(token, binding)
	is ReaderPresentationAuthority.CurlGesture -> frame.matchesPresentationIdentity(token, binding)
	is ReaderPresentationAuthority.CurlSettlementPending ->
		retainedFrame.matchesPresentationIdentity(token, binding)
	is ReaderPresentationAuthority.SettledNativePage -> frame.matchesPresentationIdentity(token, binding)
	is ReaderPresentationAuthority.LiveEngineHandoffPending ->
		(this.token == token && this.binding == binding) ||
			retainedFrame.matchesPresentationIdentity(token, binding)
	is ReaderPresentationAuthority.LiveEngineExposed -> frame.matchesPresentationIdentity(token, binding)
	is ReaderPresentationAuthority.BlockingPreparation ->
		nativePresentationRequest?.let { request ->
			request.token == token && request.binding == binding
		} == true || retainedFrame.matchesPresentationIdentity(token, binding)
}

private fun ReaderPresentationFrameOwner.matchesPresentationIdentity(
	token: ReaderPresentationToken?,
	binding: ReaderPresentationBinding
): Boolean = when (this) {
	ReaderPresentationFrameOwner.Neutral -> false
	is ReaderPresentationFrameOwner.ShellCover ->
		proof.token == token && proof.binding == binding
	is ReaderPresentationFrameOwner.NativePage ->
		proof.transitionToken == token && proof.binding == binding
	is ReaderPresentationFrameOwner.Curl ->
		frame.token == token && frame.binding == binding
	is ReaderPresentationFrameOwner.LiveEngine ->
		proof.token == token && proof.binding == binding
}

private fun ReaderPresentationState.stalePresentation(
	token: ReaderPresentationToken?,
	binding: ReaderPresentationBinding
) = if (!binding.hasCompleteRendererIdentity()) {
	stalePresentationResult(this)
} else {
	stalePresentationResult(
		state = this,
		effects = listOf(ReaderPresentationEffect.ReleaseStalePresentation(token, binding))
	)
}

private fun ReaderPresentationAuthority.frameOwner(): ReaderPresentationFrameOwner = when (this) {
	ReaderPresentationAuthority.Unavailable -> ReaderPresentationFrameOwner.Neutral
	is ReaderPresentationAuthority.ShellCover -> ReaderPresentationFrameOwner.ShellCover(proof)
	is ReaderPresentationAuthority.ShellCoverCommitPending -> retainedFrame.frameOwner
	is ReaderPresentationAuthority.CurlGesture -> frame
	is ReaderPresentationAuthority.CurlSettlementPending -> retainedFrame
	is ReaderPresentationAuthority.SettledNativePage -> frame
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> retainedFrame
	is ReaderPresentationAuthority.LiveEngineExposed -> frame
	is ReaderPresentationAuthority.BlockingPreparation -> retainedFrame
}

private fun ReaderPresentationAuthority.inputPolicy(
	targetBinding: ReaderPresentationBinding?,
	preparationFacts: ReaderPagePreparationFacts
): ReaderPresentationInputPolicy = when (this) {
	ReaderPresentationAuthority.Unavailable -> ReaderPresentationInputPolicy.RecoveryOnly
	is ReaderPresentationAuthority.ShellCover -> ReaderPresentationInputPolicy.ShellCover
	is ReaderPresentationAuthority.ShellCoverCommitPending -> ReaderPresentationInputPolicy.ChromeOnly
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> retainedFrame.inputPolicy(
		targetBinding,
		preparationFacts
	)
	is ReaderPresentationAuthority.CurlGesture -> ReaderPresentationInputPolicy.ClaimedCurl(frame.frame.token)
	is ReaderPresentationAuthority.CurlSettlementPending ->
		ReaderPresentationInputPolicy.ClaimedCurl(retainedFrame.frame.token)
	is ReaderPresentationAuthority.SettledNativePage -> frame.inputPolicy(
		targetBinding,
		preparationFacts
	)
	is ReaderPresentationAuthority.LiveEngineExposed -> ReaderPresentationInputPolicy.LiveEngine
	is ReaderPresentationAuthority.BlockingPreparation -> if (
		retainedFrame == ReaderPresentationFrameOwner.Neutral
	) {
		ReaderPresentationInputPolicy.RecoveryOnly
	} else {
		ReaderPresentationInputPolicy.ChromeOnly
	}
}

private fun ReaderPresentationFrameOwner.inputPolicy(
	targetBinding: ReaderPresentationBinding?,
	preparationFacts: ReaderPagePreparationFacts
): ReaderPresentationInputPolicy = when (this) {
	is ReaderPresentationFrameOwner.NativePage -> ReaderPresentationInputPolicy.NativePage(
		readerPageOperationPolicy(
			if (
				proof.binding == targetBinding &&
				proof.binding.preparationGeneration == preparationFacts.generation
			) {
				preparationFacts.readiness
			} else {
				ReaderPageReadinessState()
			}
		)
	)
	is ReaderPresentationFrameOwner.LiveEngine -> ReaderPresentationInputPolicy.LiveEngine
	ReaderPresentationFrameOwner.Neutral -> ReaderPresentationInputPolicy.RecoveryOnly
	is ReaderPresentationFrameOwner.ShellCover -> ReaderPresentationInputPolicy.ShellCover
	is ReaderPresentationFrameOwner.Curl -> ReaderPresentationInputPolicy.ClaimedCurl(frame.token)
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
	is ReaderPresentationAuthority.LiveEngineExposed -> ReaderRequiredTransition.None
	is ReaderPresentationAuthority.BlockingPreparation -> nativePresentationRequest?.let { request ->
		ReaderRequiredTransition.PresentNativePage(
			token = request.token,
			binding = request.binding,
			direction = null
		)
	} ?: ReaderRequiredTransition.None
	is ReaderPresentationAuthority.ShellCoverCommitPending ->
		ReaderRequiredTransition.CommitShellCover(token, binding, coverGeneration)
	is ReaderPresentationAuthority.CurlSettlementPending -> when (stage) {
		ReaderCurlSettlementStage.AwaitingFoliate -> ReaderRequiredTransition.None
		ReaderCurlSettlementStage.AwaitingNativePresentation ->
			ReaderRequiredTransition.PresentNativePage(
				token = retainedFrame.frame.token,
				binding = retainedFrame.frame.binding,
				direction = null
			)
	}
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> when (direction) {
		ReaderLiveEngineHandoffDirection.NativeToLiveEngine ->
			ReaderRequiredTransition.ExposeLiveEngine(token, binding, direction)
		ReaderLiveEngineHandoffDirection.LiveEngineToNative ->
			ReaderRequiredTransition.PresentNativePage(token, binding, direction)
	}
}

private fun ReaderPresentationAuthority.releaseIdentityTokenOrNull(): ReaderPresentationToken? =
	when (this) {
		ReaderPresentationAuthority.Unavailable -> null
		is ReaderPresentationAuthority.ShellCover -> proof.token
		is ReaderPresentationAuthority.ShellCoverCommitPending -> token
		is ReaderPresentationAuthority.CurlGesture -> frame.frame.token
		is ReaderPresentationAuthority.CurlSettlementPending -> retainedFrame.frame.token
		is ReaderPresentationAuthority.SettledNativePage -> frame.proof.transitionToken
		is ReaderPresentationAuthority.LiveEngineHandoffPending -> token
		is ReaderPresentationAuthority.LiveEngineExposed -> frame.proof.token
		is ReaderPresentationAuthority.BlockingPreparation -> retainedFrame.presentationTokenOrNull()
	}

private fun ReaderPresentationFrameOwner.presentationTokenOrNull(): ReaderPresentationToken? =
	when (this) {
		ReaderPresentationFrameOwner.Neutral -> null
		is ReaderPresentationFrameOwner.ShellCover -> proof.token
		is ReaderPresentationFrameOwner.NativePage -> proof.transitionToken
		is ReaderPresentationFrameOwner.Curl -> frame.token
		is ReaderPresentationFrameOwner.LiveEngine -> proof.token
	}

private fun ReaderPresentationAuthority.tokenOrNull(): ReaderPresentationToken? = when (this) {
	is ReaderPresentationAuthority.ShellCoverCommitPending -> token
	is ReaderPresentationAuthority.CurlGesture -> frame.frame.token
	is ReaderPresentationAuthority.CurlSettlementPending -> retainedFrame.frame.token
	is ReaderPresentationAuthority.LiveEngineHandoffPending -> token
	is ReaderPresentationAuthority.BlockingPreparation -> nativePresentationRequest?.token
	else -> null
}

private fun ReaderPresentationState.nextTokenAfter(token: ReaderPresentationToken): Long = if (
	token.value >= nextTokenValue
) {
	token.value + 1L
} else {
	nextTokenValue
}
