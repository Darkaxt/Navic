package paige.navic.reader

@ConsistentCopyVisibility
data class ReaderLegacyLiveCompatibilityIdentity internal constructor(
	val readerSessionGeneration: Long,
	val publication: ReaderPublicationIdentity,
	val foliateSessionId: String?,
	val destinationCommitIdentity: ReaderDestinationCommitIdentity?
) {
	init {
		require(readerSessionGeneration > 0L)
	}
}

sealed interface ReaderLegacyLiveCompatibilityContext {
	val lifecycle: ReaderPresentationLifecycleState

	data class Denied(
		override val lifecycle: ReaderPresentationLifecycleState =
			ReaderPresentationLifecycleState.Foreground
	) : ReaderLegacyLiveCompatibilityContext

	@ConsistentCopyVisibility
	data class ColdSession internal constructor(
		val identity: ReaderLegacyLiveCompatibilityIdentity,
		override val lifecycle: ReaderPresentationLifecycleState
	) : ReaderLegacyLiveCompatibilityContext {
		init {
			require(lifecycle == ReaderPresentationLifecycleState.Foreground)
		}
	}
}

class ReaderLegacyLiveCompatibilityGate {
	private var observedReaderSessionGeneration: Long = 0L
	private var ownershipRetired: Boolean = false

	fun resolve(
		state: ReaderControllerState,
		pageTurnCanvasEnabled: Boolean
	): ReaderLegacyLiveCompatibilityContext {
		val lifecycle = state.presentation.lifecycle
		if (state.readerSessionGeneration <= 0L || state.publication == null) {
			return ReaderLegacyLiveCompatibilityContext.Denied(lifecycle)
		}
		if (state.readerSessionGeneration < observedReaderSessionGeneration) {
			return ReaderLegacyLiveCompatibilityContext.Denied(lifecycle)
		}
		if (state.readerSessionGeneration > observedReaderSessionGeneration) {
			observedReaderSessionGeneration = state.readerSessionGeneration
			ownershipRetired = false
		}
		if (pageTurnCanvasEnabled || state.hasNativePresentationOwnershipOrIntent()) {
			ownershipRetired = true
		}
		if (
			ownershipRetired ||
			lifecycle != ReaderPresentationLifecycleState.Foreground ||
			!state.hasExactColdLegacyLiveFacts()
		) {
			return ReaderLegacyLiveCompatibilityContext.Denied(lifecycle)
		}
		return ReaderLegacyLiveCompatibilityContext.ColdSession(
			identity = state.legacyLiveCompatibilityIdentity(),
			lifecycle = lifecycle
		)
	}
}

private fun ReaderControllerState.hasNativePresentationOwnershipOrIntent(): Boolean =
	shellCoverVisible ||
		!nativeShellCoverUrl.isNullOrBlank() ||
		canReturnToShellCover ||
		pendingShellCoverDismissal != null ||
		presentation.binding != null ||
		presentation.authority != ReaderPresentationAuthority.Unavailable

private fun ReaderControllerState.hasExactColdLegacyLiveFacts(): Boolean {
	val decision = presentationDecision
	return publication != null &&
		activeEngine != null &&
		presentation.authority == ReaderPresentationAuthority.Unavailable &&
		presentation.binding == null &&
		presentation.failure == null &&
		decision.frameOwner == ReaderPresentationFrameOwner.Neutral &&
		decision.requiredTransition == ReaderRequiredTransition.None &&
		decision.inputPolicy == ReaderPresentationInputPolicy.RecoveryOnly &&
		!shellCoverVisible &&
		nativeShellCoverUrl.isNullOrBlank() &&
		!canReturnToShellCover &&
		pendingShellCoverDismissal == null
}

private fun ReaderControllerState.legacyLiveCompatibilityIdentity() =
	ReaderLegacyLiveCompatibilityIdentity(
		readerSessionGeneration = readerSessionGeneration,
		publication = requireNotNull(publication),
		foliateSessionId = foliateSessionId,
		destinationCommitIdentity = destinationCommitIdentity
	)

private fun ReaderLegacyLiveCompatibilityContext.matches(
	state: ReaderControllerState
): Boolean = this is ReaderLegacyLiveCompatibilityContext.ColdSession &&
	lifecycle == ReaderPresentationLifecycleState.Foreground &&
	state.presentation.lifecycle == lifecycle &&
	state.hasExactColdLegacyLiveFacts() &&
	identity == state.legacyLiveCompatibilityIdentity()

internal fun readerViewerActionIsAdmitted(
	inputPolicy: ReaderPresentationInputPolicy,
	action: ReaderViewerAction,
	legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext =
		ReaderLegacyLiveCompatibilityContext.Denied()
): Boolean = when (inputPolicy) {
	ReaderPresentationInputPolicy.RecoveryOnly ->
		legacyLiveCompatibilityContext is ReaderLegacyLiveCompatibilityContext.ColdSession &&
			action != ReaderViewerAction.NativeShellPrepared
	is ReaderPresentationInputPolicy.ClaimedCurl -> false
	ReaderPresentationInputPolicy.ChromeOnly -> action == ReaderViewerAction.Menu
	ReaderPresentationInputPolicy.ShellCover -> when (action) {
		ReaderViewerAction.Menu,
		ReaderViewerAction.NativeShellPrepared,
		ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
		ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down) -> true
		else -> false
	}
	is ReaderPresentationInputPolicy.NativePage ->
		action == ReaderViewerAction.Menu ||
			(
				action != ReaderViewerAction.NativeShellPrepared &&
					inputPolicy.policy.newPointer == ReaderPageNewPointerDecision.Accept
			)
	ReaderPresentationInputPolicy.LiveEngine -> action != ReaderViewerAction.NativeShellPrepared
}

data class ReaderPendingPresentationEffect(
	val identity: ReaderPresentationEffectIdentity,
	val effect: ReaderPresentationEffect
) {
	init {
		require(identity == effect.identity())
	}
}

internal class ReaderPresentationEffectQueueOverflowException(
	capacity: Int,
	pendingCount: Int,
	incomingCount: Int
) : IllegalStateException(
	"Presentation effect queue capacity=$capacity pending=$pendingCount incoming=$incomingCount"
)

internal class ReaderPresentationEffectQueue(
	private val capacity: Int = 16
) {
	private val pending = linkedMapOf<
		ReaderPresentationEffectIdentity,
		ReaderPendingPresentationEffect
	>()
	private val acknowledged = mutableSetOf<ReaderPresentationEffectIdentity>()

	init {
		require(capacity > 0)
	}

	fun retain(effects: List<ReaderPresentationEffect>): List<ReaderPendingPresentationEffect> {
		val additions = linkedMapOf<
			ReaderPresentationEffectIdentity,
			ReaderPendingPresentationEffect
		>()
		effects.forEach { effect ->
			val identity = effect.identity()
			if (identity !in pending && identity !in acknowledged && identity !in additions) {
				additions[identity] = ReaderPendingPresentationEffect(identity, effect)
			}
		}
		if (pending.size + additions.size > capacity) {
			throw ReaderPresentationEffectQueueOverflowException(
				capacity = capacity,
				pendingCount = pending.size,
				incomingCount = additions.size
			)
		}
		pending.putAll(additions)
		return additions.values.toList()
	}

	fun pendingEffects(): List<ReaderPendingPresentationEffect> = pending.values.toList()

	fun acknowledge(identity: ReaderPresentationEffectIdentity): Boolean {
		if (pending.remove(identity) == null) return false
		acknowledged += identity
		return true
	}
}

internal object ReaderPresentationControllerReducer {
	fun onPresentationEvent(
		controller: ReaderController,
		event: ReaderPresentationEvent
	): ReaderControllerStep {
		val state = controller.state
		val eventSequence = Math.incrementExact(controller.presentationEventSequence)
		val previousAuthority = state.presentation.authority
		val primaryReduction = readerPresentationReduce(state.presentation, event)
		val startupReduction = if (
			event is ReaderPresentationEvent.PublicationOpened &&
			primaryReduction.state.binding == event.binding &&
			primaryReduction.state.authority == ReaderPresentationAuthority.Unavailable
		) {
			readerPresentationReduce(
				primaryReduction.state,
				if (state.shellCoverVisible) {
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
		val receipt = ReaderPresentationEventReceipt(
			event = event,
			version = ReaderPresentationReceiptVersion(
				readerSessionGeneration = state.readerSessionGeneration,
				eventSequence = eventSequence
			),
			disposition = reduction.disposition,
			postState = reduction.state,
			effects = reduction.effects.toList()
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
		return ReaderControllerStep(
			controller = controller.copy(
				presentationEventSequence = eventSequence,
				state = state.copy(
					presentation = reduction.state,
					shellCoverVisible = when {
						acceptedShellCoverDismissal -> false
						acceptedShellCoverCommit -> true
						else -> state.shellCoverVisible
					},
					pendingShellCoverDismissal = state.pendingShellCoverDismissal
						.takeUnless { acceptedShellCoverDismissal },
					nativeShellCoverReturnLocatorKey = if (acceptedShellCoverDismissal) {
						readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator)
					} else {
						state.nativeShellCoverReturnLocatorKey
					}
				)
			),
			engineCommands = listOfNotNull(
				ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")
					.takeIf {
						acceptedShellCoverDismissal && state.supportsReaderEngineCapability(
							ReaderEngineCapability.MediaOverlay
						)
					}
			),
			presentationEffects = receipt.effects,
			presentationReceipt = receipt
		)
	}

	fun onViewerAction(
		controller: ReaderController,
		action: ReaderViewerAction,
		legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext =
			ReaderLegacyLiveCompatibilityContext.Denied()
	): ReaderControllerStep {
		if (
			controller.state.presentation.lifecycle !=
			ReaderPresentationLifecycleState.Foreground
		) return ReaderControllerStep(controller)
		val inputPolicy = controller.state.presentationDecision.inputPolicy
		val exactCompatibilityContext = legacyLiveCompatibilityContext.takeIf {
			it.matches(controller.state)
		} ?: ReaderLegacyLiveCompatibilityContext.Denied(
			controller.state.presentation.lifecycle
		)
		if (!readerViewerActionIsAdmitted(
				inputPolicy,
				action,
				exactCompatibilityContext
			)
		) {
			return ReaderControllerStep(controller)
		}
		val admittedController = if (controller.state.lastContentActionClaim != null) {
			controller.copy(
				state = controller.state.copy(lastContentActionClaim = null)
			)
		} else {
			controller
		}
		return when (inputPolicy) {
			ReaderPresentationInputPolicy.ChromeOnly -> admittedController.toggleMenu()
			ReaderPresentationInputPolicy.ShellCover ->
				admittedController.onShellCoverViewerAction(action)
			else -> admittedController.onContentViewerAction(action)
		}
	}

	private fun ReaderController.onContentViewerAction(
		action: ReaderViewerAction
	): ReaderControllerStep = when (action) {
		ReaderViewerAction.Menu -> toggleMenu()
		ReaderViewerAction.NativeShellPrepared -> ReaderControllerStep(this)
		is ReaderViewerAction.TurnPage -> turnPage(action.direction)
		is ReaderViewerAction.PreviewPageDrag -> previewPageDrag(action)
		is ReaderViewerAction.ScrollViewport -> scrollViewport(action.direction)
		is ReaderViewerAction.NavigateTo -> navigateTo(action.locator)
		is ReaderViewerAction.ContentLongPressAt -> contentLongPressAt(action)
	}

	private fun ReaderController.toggleMenu(): ReaderControllerStep = ReaderControllerStep(
		copy(state = state.copy(menuVisible = !state.menuVisible))
	)

	private fun ReaderController.onShellCoverViewerAction(
		action: ReaderViewerAction
	): ReaderControllerStep = when {
		action == ReaderViewerAction.Menu -> toggleMenu()
		action == ReaderViewerAction.NativeShellPrepared -> ReaderControllerStep(
			controller = copy(state = state.copy(menuVisible = false))
		)
		action == ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next) ||
			action == ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down) -> {
			if (state.pendingShellCoverDismissal != null) return ReaderControllerStep(this)
			val presentationStep = onPresentationEvent(
				ReaderPresentationEvent.ShellCoverDismissalRequested
			)
			val presentationController = presentationStep.controller
			val presentationState = presentationController.state
			val locator = presentationState.pendingShellCoverDismissal?.locator
				?: presentationState.chrome.currentLocator
			val nextRequestId = presentationState.shellCoverDismissalRequestSequence + 1L
			val dismissalRequest = locator
				?.takeIf(ReaderLocator::hasFoliateNavigationIdentity)
				?.let {
					ReaderShellCoverDismissalRequest(
						requestId = nextRequestId,
						locator = it,
						foliateSessionId = presentationState.foliateSessionId
					)
				}
			ReaderControllerStep(
				controller = presentationController.copy(
					state = presentationState.copy(
						shellCoverDismissalRequestSequence = dismissalRequest
							?.requestId
							?: presentationState.shellCoverDismissalRequestSequence,
						pendingShellCoverDismissal = dismissalRequest,
						menuVisible = false
					)
				),
				engineCommands = listOfNotNull(
					dismissalRequest?.let {
						ReaderEngineCommand.NavigateTo(
							locator = it.locator,
							relocationReason = readerShellCoverDismissalReason(it.requestId)
						)
					}
				),
				presentationEffects = presentationStep.presentationEffects,
				presentationReceipt = presentationStep.presentationReceipt
			)
		}
		else -> ReaderControllerStep(this)
	}

	private fun ReaderController.turnPage(
		direction: ReaderPageTurnDirection
	): ReaderControllerStep = if (
		direction == ReaderPageTurnDirection.Previous &&
		state.canReturnToShellCover &&
		state.nativeShellCoverReturnLocatorKey ==
			readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator) &&
		readerShouldReturnToNativeShellCover(
			shellCoverUrl = state.nativeShellCoverUrl,
			shellCoverVisible = state.shellCoverVisible,
			locator = state.chrome.currentLocator
		)
	) {
		ReaderOverlayReducer.showNativeShellCover(this)
	} else {
		ReaderWhispersyncReducer.reserveUserNavigation(this).let { reserved ->
			ReaderControllerStep(
				controller = reserved,
				engineCommands = listOf(
					ReaderEngineCommand.TurnPage(
						direction = direction,
						causalSequence = reserved.pendingWhispersyncCausalSequence()
					)
				),
				readaloudPlaybackCommand = state.whispersync.navigationPauseCommand()
			)
		}
	}

	private fun ReaderController.scrollViewport(
		direction: ReaderViewportScrollDirection
	): ReaderControllerStep {
		val reserved = ReaderWhispersyncReducer.reserveUserNavigation(
			controller = this,
			requiresPageTurnSettlement = false
		)
		return ReaderControllerStep(
			controller = reserved,
			engineCommands = listOf(
				ReaderEngineCommand.ScrollViewport(
					direction = direction,
					causalSequence = reserved.pendingWhispersyncCausalSequence()
				)
			),
			readaloudPlaybackCommand = state.whispersync.navigationPauseCommand()
		)
	}

	private fun ReaderController.previewPageDrag(
		action: ReaderViewerAction.PreviewPageDrag
	): ReaderControllerStep = ReaderControllerStep(
		controller = this,
		engineCommands = listOf(
			ReaderEngineCommand.PreviewPageDrag(
				deltaX = action.deltaX,
				deltaY = action.deltaY,
				viewWidth = action.viewWidth,
				viewHeight = action.viewHeight,
				phase = action.phase
			)
		)
	)

	private fun ReaderController.contentLongPressAt(
		action: ReaderViewerAction.ContentLongPressAt
	): ReaderControllerStep {
		val reserved = ReaderWhispersyncReducer.reserveExplicitCueSelection(this)
		return ReaderControllerStep(
			controller = reserved,
			engineCommands = listOf(
				ReaderEngineCommand.ContentLongPressAt(
					x = action.x,
					y = action.y,
					viewWidth = action.viewWidth,
					viewHeight = action.viewHeight,
					selectText = !reserved.state.whispersyncOwnsTextSelection(),
					causalSequence = reserved.pendingWhispersyncCausalSequence()
				)
			)
		)
	}
}

private fun ReaderLocator.hasFoliateNavigationIdentity(): Boolean =
	!cfi.isNullOrBlank() || !href.isNullOrBlank() || progress?.isFinite() == true
