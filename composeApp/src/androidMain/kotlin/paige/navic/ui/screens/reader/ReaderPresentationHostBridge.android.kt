package paige.navic.ui.screens.reader

import karacken.curl.PageSurfaceView
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderLiveEnginePresentationProof
import paige.navic.reader.ReaderLiveEngineHandoffDirection
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFrameTarget
import paige.navic.reader.ReaderTransitionFrameTargetHandle
import paige.navic.reader.ReaderTransitionFrameTargetSpecification
import paige.navic.reader.ReaderNativePageHostTokenState
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderShellCoverRetainedFrame
import paige.navic.reader.readerPresentationDecision

internal fun readerDispatchPageHostLifecycleEvent(
	event: ReaderPageHostLifecycleEvent,
	preserveDestinationDeck: Boolean,
	clearDestinationDeckPrewarm: () -> Unit,
	dispatchInputLifecycle: (ReaderPageHostLifecycleEvent) -> List<Long>
): List<Long> {
	if (!preserveDestinationDeck) clearDestinationDeckPrewarm()
	return dispatchInputLifecycle(event)
}

internal fun interface ReaderPresentationDrawRegistration {
	fun unregister()
}

internal interface ReaderPresentationCommitHost {
	val isAttachedToWindow: Boolean
	val currentPresentationBinding: ReaderPresentationBinding?
	val currentShellCoverGeneration: Long?
	val shellCoverSelected: Boolean
	val measuredViewportWidth: Int
	val measuredViewportHeight: Int

	fun prepareOpaqueShellCover(coverGeneration: Long)
	fun cancelOpaqueShellCoverPreparation(coverGeneration: Long)
	fun completeOpaqueShellCoverPreparation(coverGeneration: Long)
	fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration
	fun postShellCoverAnimationFrame(onFrame: () -> Unit)
	fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) = Unit
}

internal data class ReaderNativePagePresentationCandidate(
	val binding: ReaderPresentationBinding,
	val transitionToken: ReaderPresentationToken?,
	val visualPageIndex: Int,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val preparationFacts: ReaderPagePreparationFacts,
	val handoffDirection: ReaderLiveEngineHandoffDirection? = null
)

internal fun ReaderPresentationDecision.authoritativeLiveEngineToNativeTransitionOrNull():
	ReaderRequiredTransition.PresentNativePage? {
	if (lifecycle != ReaderPresentationLifecycleState.Foreground ||
		diagnosticPresentation is ReaderDiagnosticPresentation.Failure
	) return null
	val pending = authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
		?: return null
	if (pending.direction != ReaderLiveEngineHandoffDirection.LiveEngineToNative) return null
	return ReaderRequiredTransition.PresentNativePage(
		token = pending.token,
		binding = pending.binding,
		direction = pending.direction
	)
}

internal data class ReaderNativePagePresentationHostSnapshot(
	val binding: ReaderPresentationBinding?,
	val transitionToken: ReaderPresentationToken?,
	val deck: ReaderPagePreparedActiveDeck?,
	val preparationFacts: ReaderPagePreparationFacts,
	val visualPageIndex: Int?,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val hostAttached: Boolean,
	val windowVisible: Boolean,
	val viewerReplacementAdmitted: Boolean,
	val rendererDeckReady: Boolean,
	val nativePresentationVisible: Boolean,
	val shellCoverSelected: Boolean,
	val handoffDirection: ReaderLiveEngineHandoffDirection? = null
) {
	fun currentCandidateOrNull(): ReaderNativePagePresentationCandidate? {
		val binding = binding ?: return null
		val deck = deck ?: return null
		val visualPageIndex = visualPageIndex ?: return null
		if (
			!hostAttached ||
			!windowVisible ||
			!viewerReplacementAdmitted ||
			!rendererDeckReady ||
			!nativePresentationVisible ||
			(shellCoverSelected && transitionToken == null) ||
			preparationFacts.phase != ReaderPagePreparationPhase.Ready ||
			preparationFacts.generation != binding.preparationGeneration ||
			viewportWidth <= 0 ||
			viewportHeight <= 0 ||
			visualPageIndex != deck.sourceCenterPageIndex ||
			binding.profileGeneration != deck.rasterProfileEpoch ||
			binding.rasterGeneration != deck.rasterEpoch ||
			binding.textureGeneration != deck.generationId ||
			binding.preparationGeneration != deck.preparationGeneration
		) return null
		return ReaderNativePagePresentationCandidate(
			binding = binding,
			transitionToken = transitionToken,
			visualPageIndex = visualPageIndex,
			viewportWidth = viewportWidth,
			viewportHeight = viewportHeight,
			preparationFacts = preparationFacts,
			handoffDirection = handoffDirection
		)
	}
}

internal interface ReaderNativePagePresentedFrameSource {
	fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long
	fun requestCandidatePresentedFrame(
		candidate: ReaderNativePagePresentationCandidate,
		onPresented: (Long) -> Unit
	): Long = requestNextPresentedFrame(onPresented)
	fun cancelPresentedFrameRequest(requestId: Long): Boolean
}

internal class ReaderPageSurfacePresentedFrameSource(
	private val surface: PageSurfaceView
) : ReaderNativePagePresentedFrameSource {
	override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = surface.requestNextPresentedFrame { onPresented(requestId) }
		return requestId
	}

	override fun requestCandidatePresentedFrame(
		candidate: ReaderNativePagePresentationCandidate,
		onPresented: (Long) -> Unit
	): Long {
		val generation = candidate.binding.textureGeneration
			?: return PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = surface.requestNativePagePresentedFrame(generation) { onPresented(requestId) }
		return requestId
	}

	override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
		surface.cancelPresentedFrameRequest(requestId)
}

internal class ReaderNativePagePresentationPublisher(
	private val frameSource: ReaderNativePagePresentedFrameSource,
	private val currentCandidate: () -> ReaderNativePagePresentationCandidate?,
	private val currentHandoffTransition: () -> ReaderRequiredTransition.PresentNativePage? = {
		null
	},
	private val handoffTimeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler? = null,
	private val handoffTimeoutMillis: Long = 10_000L,
	private val handoffNowMillis: () -> Long = android.os.SystemClock::uptimeMillis,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
) {
	private data class PendingFrame(
		val requestId: Long,
		val candidate: ReaderNativePagePresentationCandidate,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private data class PreparedCommandTarget(
		val target: ReaderTransitionFrameTarget.NativePage,
		val candidate: ReaderNativePagePresentationCandidate,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private data class PendingCommandFrame(
		val requestId: Long,
		val command: ReaderTransitionCommand.RequestFramePresentation,
		val prepared: PreparedCommandTarget,
		val onFact: (ReaderTransitionFact) -> Unit,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private data class PendingHandoffTimeout(
		var transition: ReaderRequiredTransition.PresentNativePage,
		val action: Runnable,
		val expiresAtMillis: Long,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	)

	private var pendingFrame: PendingFrame? = null
	private var pendingCommandFrame: PendingCommandFrame? = null
	private var commandTargetResolver: ((ReaderTransitionFrameTargetSpecification.NativePage) ->
		ReaderNativePagePresentationCandidate?)? = null
	private val preparedCommandTargets = linkedMapOf<ReaderTransitionFrameTargetHandle, PreparedCommandTarget>()
	private var nextFrameTargetOpaqueId = 1L
	private var pendingHandoffTimeout: PendingHandoffTimeout? = null
	private var failedHandoffTransition: ReaderRequiredTransition.PresentNativePage? = null
	private var lastPublishedCandidate: ReaderNativePagePresentationCandidate? = null
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private val restartPreparedCommandTargets =
		linkedMapOf<ReaderTransitionFrameTargetHandle, PreparedCommandTarget>()
	private var restartPendingCommandFrame: PendingCommandFrame? = null
	private var restartPendingFrame: PendingFrame? = null
	private var restartHandoffTimeout: PendingHandoffTimeout? = null
	private val completedFrozen = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private var disposed = false

	init {
		require(handoffTimeoutMillis > 0L)
	}

	fun activateCommandOnly(
		resolveTarget: (ReaderTransitionFrameTargetSpecification.NativePage) ->
			ReaderNativePagePresentationCandidate?
	) {
		check(!disposed)
		check(frozenDomain == null) { "Native publisher activation is frozen" }
		check(commandTargetResolver == null) { "Native publisher activation is irreversible" }
		pendingFrame?.let { frameSource.cancelPresentedFrameRequest(it.requestId) }
		pendingFrame = null
		cancelHandoffTimeout()
		failedHandoffTransition = null
		commandTargetResolver = resolveTarget
	}

	fun prepareTarget(
		command: ReaderTransitionCommand.PrepareFrameTarget,
		onFact: (ReaderTransitionFact) -> Unit
	): ReaderPortCommandResult {
		if (disposed || frozenDomain != null || commandTargetResolver == null ||
			preparedCommandTargets.size >= paige.navic.reader.ReaderMaximumPendingFrameTargets
		) return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		val specification = command.specification as? ReaderTransitionFrameTargetSpecification.NativePage
			?: return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		if (
			command.transitionId != specification.transitionId ||
			command.registration.key.kind != ReaderTransitionResourceKind.Deck ||
			command.registration.key.ownerId != ReaderTransitionResourceOwnerId.TransitionOwned(command.transitionId)
		) return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.StaleProof)
		val candidate = commandTargetResolver?.invoke(specification)
			?: return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.StaleProof)
		val tokenMatches = when (val token = specification.hostToken) {
			is ReaderNativePageHostTokenState.Present ->
				candidate.transitionToken?.value == token.token.value
			ReaderNativePageHostTokenState.AuthoritativeAbsent -> candidate.transitionToken == null
		}
		if (
			candidate.binding != specification.binding ||
			!tokenMatches ||
			candidate.preparationFacts.phase != ReaderPagePreparationPhase.Ready ||
			candidate.preparationFacts.failure != null ||
			candidate.viewportWidth != specification.geometry.targetWidthPx ||
			candidate.viewportHeight != specification.geometry.targetHeightPx ||
			specification.geometry.viewportGeneration != specification.binding.viewportGeneration ||
			specification.geometry.layoutProfileGeneration != specification.binding.profileGeneration
		) return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.StaleProof)
		val handle = ReaderTransitionFrameTargetHandle(
			readerSessionGeneration = specification.readerSessionGeneration,
			publicationGeneration = specification.publicationGeneration,
			opaqueId = nextFrameTargetOpaqueId++
		)
		val target = ReaderTransitionFrameTarget.NativePage(handle, specification, command.registration)
		preparedCommandTargets[handle] = PreparedCommandTarget(
			target,
			candidate,
			tokenAllocator.allocate()
		)
		onFact(ReaderTransitionFact.FrameTargetPrepared(command.transitionId, target))
		return ReaderPortCommandResult.Accepted
	}

	fun present(
		command: ReaderTransitionCommand.RequestFramePresentation,
		onFact: (ReaderTransitionFact) -> Unit
	): ReaderPortCommandResult {
		if (
			disposed || frozenDomain != null || commandTargetResolver == null ||
			pendingCommandFrame != null
		) {
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		val target = command.target as? ReaderTransitionFrameTarget.NativePage
			?: return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		val prepared = preparedCommandTargets.remove(target.handle)
		if (prepared?.target != target || target.specification.transitionId != command.transitionId) {
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.StaleProof)
		}

		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		var callbackBeforeBind: Long? = null
		var bound = false
		requestId = frameSource.requestCandidatePresentedFrame(prepared.candidate) { presentedRequestId ->
			if (bound) onCommandPresentedFrame(requestId, presentedRequestId)
			else callbackBeforeBind = presentedRequestId
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) {
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		pendingCommandFrame = PendingCommandFrame(
			requestId,
			command,
			prepared,
			onFact,
			tokenAllocator.allocate()
		)
		bound = true
		callbackBeforeBind?.let { onCommandPresentedFrame(requestId, it) }
		return ReaderPortCommandResult.Accepted
	}

	fun update() {
		if (disposed || frozenDomain != null || commandTargetResolver != null) return
		val handoffTransition = currentLiveEngineToNativeTransition()
		failedHandoffTransition?.let { failed ->
			failedHandoffTransition = when {
				handoffTransition == null -> failed
				failed.isSameHandoffAttemptAs(handoffTransition) -> handoffTransition
				else -> null
			}
		}
		pendingHandoffTimeout?.let { timeout ->
			when {
				handoffTransition == null -> cancelHandoffTimeout()
				timeout.transition.isSameHandoffAttemptAs(handoffTransition) ->
					timeout.transition = handoffTransition
				else -> cancelHandoffTimeout()
			}
		}
		val failedTransition = failedHandoffTransition
		if (
			handoffTransition != null &&
			failedTransition != handoffTransition &&
			pendingHandoffTimeout == null
		) {
			armHandoffTimeout(handoffTransition)
		}

		val candidate = currentCandidate()
		val published = lastPublishedCandidate
		// Progress counters can reset after Ready without changing the accepted page.
		// Only an authorizing receipt can supply this deduplication/retired-token anchor.
		if (candidate != null && published != null && candidate.matchesAcceptedPublication(published)) {
			lastPublishedCandidate = candidate
		}
		val pending = pendingFrame
		if (pending != null && (pending.candidate != candidate || candidate == lastPublishedCandidate)) {
			pendingFrame = null
			frameSource.cancelPresentedFrameRequest(pending.requestId)
		}
		if (
			(failedTransition != null &&
				(
					failedTransition == handoffTransition ||
					candidate?.matches(failedTransition) == true ||
					(candidate?.binding == failedTransition.binding &&
						candidate.transitionToken == null)
				)) ||
			candidate == null ||
			candidate == lastPublishedCandidate ||
			pendingFrame?.candidate == candidate ||
			(handoffTransition != null && !candidate.matches(handoffTransition))
		) return

		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = frameSource.requestCandidatePresentedFrame(candidate) { presentedRequestId ->
			onPresentedFrame(requestId, presentedRequestId, candidate)
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) {
			if (handoffTransition != null && candidate.matches(handoffTransition)) {
				failHandoffRegistration(handoffTransition)
			}
			return
		}
		pendingFrame = PendingFrame(requestId, candidate, tokenAllocator.allocate())
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = when {
		disposed -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		frozenDomain == null -> {
			frozenDomain = domain
			ReaderPortCommandResult.Accepted
		}
		frozenDomain == domain -> ReaderPortCommandResult.Accepted
		else -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		val domain = frozenDomain ?: return emptyList()
		val prepared = preparedCommandTargets.values.map { target ->
			publisherOwnershipRow(
				domain,
				target.activationToken,
				ReaderTransitionResourceKind.FrameHandoff,
				ReaderLegacyResourceOrigin.Owned,
				ReaderLegacyResourceState.Prepared
			)
		}
		val pending = pendingCommandFrame?.let { frame ->
			listOf(
				publisherOwnershipRow(
					domain,
					frame.activationToken,
					ReaderTransitionResourceKind.CallbackRegistration,
					ReaderLegacyResourceOrigin.Pending,
					ReaderLegacyResourceState.Registered
				)
			)
		}.orEmpty()
		val legacyPending = pendingFrame?.let { frame ->
			listOf(
				publisherOwnershipRow(
					domain,
					frame.activationToken,
					ReaderTransitionResourceKind.CallbackRegistration,
					ReaderLegacyResourceOrigin.Pending,
					ReaderLegacyResourceState.Registered
				)
			)
		}.orEmpty()
		val timeout = pendingHandoffTimeout?.let { registration ->
			listOf(
				publisherOwnershipRow(
					domain,
					registration.activationToken,
					ReaderTransitionResourceKind.CallbackRegistration,
					ReaderLegacyResourceOrigin.Pending,
					ReaderLegacyResourceState.Registered
				)
			)
		}.orEmpty()
		return prepared + pending + legacyPending + timeout
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.FrameOrHandoff
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val token = physicalIdentity.sourceLocalToken
		val prepared = preparedCommandTargets.entries.firstOrNull {
			it.value.activationToken == token
		}
		if (prepared != null) {
			preparedCommandTargets.remove(prepared.key)
			restartPreparedCommandTargets[prepared.key] = prepared.value
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		val pending = pendingCommandFrame
		if (pending?.activationToken == token) {
			pendingCommandFrame = null
			frameSource.cancelPresentedFrameRequest(pending.requestId)
			restartPendingCommandFrame = pending
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		val legacyPending = pendingFrame
		if (legacyPending?.activationToken == token) {
			pendingFrame = null
			frameSource.cancelPresentedFrameRequest(legacyPending.requestId)
			restartPendingFrame = legacyPending
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		val timeout = pendingHandoffTimeout
		if (timeout?.activationToken == token) {
			pendingHandoffTimeout = null
			handoffTimeoutScheduler?.removeCallbacks(timeout.action)
			restartHandoffTimeout = timeout
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (completedFrozen.remove(token)) {
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (frozenDomain != domain) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		preparedCommandTargets.putAll(restartPreparedCommandTargets)
		restartPreparedCommandTargets.clear()
		val pending = restartPendingCommandFrame
		frozenDomain = null
		completedFrozen.clear()
		if (pending != null && !restartCommandFrame(pending)) {
			frozenDomain = domain
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		restartPendingCommandFrame = null
		val legacyPending = restartPendingFrame
		if (legacyPending != null && !restartLegacyFrame(legacyPending)) {
			frozenDomain = domain
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
		restartPendingFrame = null
		restartHandoffTimeout?.let { timeout ->
			armHandoffTimeout(timeout.transition, timeout.expiresAtMillis)
		}
		restartHandoffTimeout = null
		return ReaderPortCommandResult.Accepted
	}

	private fun restartLegacyFrame(previous: PendingFrame): Boolean {
		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = frameSource.requestCandidatePresentedFrame(previous.candidate) {
			presentedRequestId ->
			onPresentedFrame(requestId, presentedRequestId, previous.candidate)
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) return false
		pendingFrame = previous.copy(requestId = requestId)
		return true
	}

	private fun restartCommandFrame(previous: PendingCommandFrame): Boolean {
		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		var callbackBeforeBind: Long? = null
		var bound = false
		requestId = frameSource.requestCandidatePresentedFrame(previous.prepared.candidate) {
			presentedRequestId ->
			if (bound) onCommandPresentedFrame(requestId, presentedRequestId)
			else callbackBeforeBind = presentedRequestId
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) return false
		pendingCommandFrame = previous.copy(requestId = requestId)
		bound = true
		callbackBeforeBind?.let { onCommandPresentedFrame(requestId, it) }
		return true
	}

	private fun publisherOwnershipRow(
		domain: ReaderLegacyPhysicalDomain,
		token: ReaderLegacySourceLocalOpaqueToken,
		kind: ReaderTransitionResourceKind,
		origin: ReaderLegacyResourceOrigin,
		state: ReaderLegacyResourceState
	) = ReaderFrozenLegacyResource(
		freezeToken = domain.freezeToken,
		physicalIdentity = ReaderLegacyPhysicalIdentity(
			domain,
			ReaderLegacyInventorySource.FrameOrHandoff,
			token
		),
		kind = kind,
		binding = null,
		visibleOwner = null,
		origin = origin,
		state = state,
		mayBeCommittedPredecessor = false
	)

	fun dispose() {
		if (disposed) return
		disposed = true
		pendingFrame?.let { frameSource.cancelPresentedFrameRequest(it.requestId) }
		pendingFrame = null
		pendingCommandFrame?.let { frameSource.cancelPresentedFrameRequest(it.requestId) }
		pendingCommandFrame = null
		preparedCommandTargets.clear()
		commandTargetResolver = null
		cancelHandoffTimeout()
		failedHandoffTransition = null
	}

	private fun ReaderNativePagePresentationCandidate.matchesAcceptedPublication(
		published: ReaderNativePagePresentationCandidate
	): Boolean {
		val comparison = copy(preparationFacts = preparationFacts.copy(
			completedCount = published.preparationFacts.completedCount,
			requiredCount = published.preparationFacts.requiredCount
		))
		return comparison == published || (
			published.transitionToken != null && transitionToken == null && handoffDirection == null &&
				comparison == published.copy(transitionToken = null, handoffDirection = null)
			)
	}

	private fun currentLiveEngineToNativeTransition():
		ReaderRequiredTransition.PresentNativePage? = currentHandoffTransition()?.takeIf {
		it.direction == ReaderLiveEngineHandoffDirection.LiveEngineToNative
	}

	private fun ReaderRequiredTransition.PresentNativePage.isSameHandoffAttemptAs(
		other: ReaderRequiredTransition.PresentNativePage
	): Boolean = token == other.token && direction == other.direction

	private fun ReaderNativePagePresentationCandidate.matches(
		transition: ReaderRequiredTransition.PresentNativePage
	): Boolean = transitionToken == transition.token &&
		handoffDirection == transition.direction &&
		binding == transition.binding

	private fun armHandoffTimeout(
		transition: ReaderRequiredTransition.PresentNativePage,
		expiresAtMillis: Long = handoffNowMillis().let { now ->
			if (now > Long.MAX_VALUE - handoffTimeoutMillis) Long.MAX_VALUE
			else now + handoffTimeoutMillis
		}
	) {
		val scheduler = handoffTimeoutScheduler ?: return
		lateinit var action: Runnable
		action = Runnable { onHandoffTimeout(action) }
		pendingHandoffTimeout = PendingHandoffTimeout(
			transition,
			action,
			expiresAtMillis,
			tokenAllocator.allocate()
		)
		val remainingMillis = maxOf(0L, expiresAtMillis - handoffNowMillis())
		if (!scheduler.postDelayed(action, remainingMillis)) action.run()
	}

	private fun onHandoffTimeout(action: Runnable) {
		val timeout = pendingHandoffTimeout ?: return
		val transition = timeout.transition
		if (
			disposed || timeout.action !== action ||
			currentLiveEngineToNativeTransition() != transition
		) return
		if (frozenDomain != null) {
			pendingHandoffTimeout = null
			restartHandoffTimeout = timeout
			completedFrozen += timeout.activationToken
			return
		}
		pendingHandoffTimeout = null
		pendingFrame?.takeIf { it.candidate.matches(transition) }?.let { pending ->
			pendingFrame = null
			frameSource.cancelPresentedFrameRequest(pending.requestId)
		}
		failedHandoffTransition = transition
		onEvent(
			ReaderPresentationEvent.LiveEngineHandoffTimedOut(
				direction = ReaderLiveEngineHandoffDirection.LiveEngineToNative,
				token = transition.token,
				binding = transition.binding
			)
		)
	}

	private fun failHandoffRegistration(
		transition: ReaderRequiredTransition.PresentNativePage
	) {
		cancelHandoffTimeout()
		failedHandoffTransition = transition
		onEvent(
			ReaderPresentationEvent.LiveEngineExposureFailed(
				direction = ReaderLiveEngineHandoffDirection.LiveEngineToNative,
				token = transition.token,
				binding = transition.binding,
				reason = ReaderPresentationFailureReason.NativePresentationUnavailable
			)
		)
	}

	private fun cancelHandoffTimeout() {
		val timeout = pendingHandoffTimeout ?: return
		pendingHandoffTimeout = null
		handoffTimeoutScheduler?.removeCallbacks(timeout.action)
	}

	private fun onCommandPresentedFrame(
		expectedRequestId: Long,
		presentedRequestId: Long
	) {
		if (disposed || expectedRequestId != presentedRequestId) return
		val pending = pendingCommandFrame ?: return
		if (pending.requestId != expectedRequestId) return
		if (frozenDomain != null) {
			pendingCommandFrame = null
			restartPendingCommandFrame = pending
			completedFrozen += pending.activationToken
			return
		}
		pendingCommandFrame = null
		val candidate = pending.prepared.candidate
		val target = pending.prepared.target
		val proof = ReaderNativePagePresentationProof(
			binding = target.specification.binding,
			transitionToken = candidate.transitionToken,
			presentedFrame = presentedRequestId,
			viewportWidth = target.specification.geometry.targetWidthPx,
			viewportHeight = target.specification.geometry.targetHeightPx,
			rasterGeneration = target.specification.allocation.rasterGeneration,
			textureGeneration = target.specification.allocation.textureGeneration
		)
		pending.onFact(
			ReaderTransitionFact.PreparedFrame(
				pending.command.transitionId,
				target,
				ReaderPresentationFrameOwner.NativePage(proof),
				target.resource
			)
		)
	}

	private fun onPresentedFrame(
		expectedRequestId: Long,
		presentedRequestId: Long,
		armedCandidate: ReaderNativePagePresentationCandidate
	) {
		if (disposed || expectedRequestId != presentedRequestId) return
		val pending = pendingFrame
		if (pending?.requestId != expectedRequestId || pending.candidate != armedCandidate) return
		if (frozenDomain != null) {
			pendingFrame = null
			restartPendingFrame = pending
			completedFrozen += pending.activationToken
			return
		}
		pendingFrame = null
		currentLiveEngineToNativeTransition()?.takeIf { transition ->
			armedCandidate.matches(transition)
		}?.let { cancelHandoffTimeout() }
		val candidate = currentCandidate()
		if (candidate != armedCandidate || candidate == lastPublishedCandidate) return
		val event = ReaderPresentationEvent.NativePagePresented(
			ReaderNativePagePresentationProof(
				binding = candidate.binding,
				transitionToken = candidate.transitionToken,
				presentedFrame = presentedRequestId,
				viewportWidth = candidate.viewportWidth,
				viewportHeight = candidate.viewportHeight,
				rasterGeneration = requireNotNull(candidate.binding.rasterGeneration),
				textureGeneration = requireNotNull(candidate.binding.textureGeneration)
			)
		)
		var receipt: ReaderPresentationEventReceipt? = null
		try {
			receipt = onEvent(event)
		} finally {
			if (receipt.authorizes(event)) {
				lastPublishedCandidate = candidate
			} else {
				update()
			}
		}
	}
}

internal enum class ReaderShellCoverHostLayer {
	Hidden,
	PreparedBehindPredecessor,
	Selected
}

internal class ReaderShellCoverLayerController(
	private val onPrepareBehindPredecessor: () -> Unit,
	private val onHidePreparedCover: () -> Unit,
	private val onSelectCover: () -> Unit,
	private val onInvalidateRasterDeck: () -> Unit,
	private val onInvalidatePreparation: () -> Unit
) {
	var currentLayer: ReaderShellCoverHostLayer = ReaderShellCoverHostLayer.Hidden
		private set

	fun prepareCoverBehindPredecessor() {
		if (currentLayer == ReaderShellCoverHostLayer.PreparedBehindPredecessor) return
		onPrepareBehindPredecessor()
		currentLayer = ReaderShellCoverHostLayer.PreparedBehindPredecessor
	}

	fun hidePreparedCover() {
		if (currentLayer != ReaderShellCoverHostLayer.PreparedBehindPredecessor) return
		onHidePreparedCover()
		currentLayer = ReaderShellCoverHostLayer.Hidden
	}

	fun coverHidden() {
		if (currentLayer == ReaderShellCoverHostLayer.Selected) {
			currentLayer = ReaderShellCoverHostLayer.Hidden
		}
	}

	fun selectCover(preserveNativePresentationProof: Boolean) {
		if (currentLayer == ReaderShellCoverHostLayer.Selected) return
		onSelectCover()
		currentLayer = ReaderShellCoverHostLayer.Selected
		if (!preserveNativePresentationProof) {
			onInvalidateRasterDeck()
			onInvalidatePreparation()
		}
	}
}

internal class ReaderPresentationHostBridge(
	private val host: ReaderPresentationCommitHost,
	private val liveEngineVisualHandoff: ReaderWebViewVisualHandoff? = null,
	private val liveEngineExposureRequired: () -> Boolean = { false },
	transitionTimeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
	transitionNowMillis: () -> Long = android.os.SystemClock::uptimeMillis,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
) {
	private data class ViewportGeometry(
		val width: Int,
		val height: Int
	)

	private class PendingCoverCommit(
		val transition: ReaderRequiredTransition.CommitShellCover,
		val geometry: ViewportGeometry,
		val ownershipToken: ReaderLegacySourceLocalOpaqueToken,
		val callbackToken: ReaderLegacySourceLocalOpaqueToken
	) {
		var registration: ReaderPresentationDrawRegistration? = null
		var registrationRemoved = false
		var callbackOwned = true
		var callbackEpoch = 0L
		var frameScheduled = false
		var acceptedReceipt: ReaderPresentationEventReceipt? = null
		var emittedProof: ReaderShellCoverCommitProof? = null

		fun unregisterOnce() {
			if (registrationRemoved) return
			registrationRemoved = true
			registration?.unregister()
		}
	}

	private class PendingLiveEngineExposure(
		var transition: ReaderRequiredTransition.ExposeLiveEngine
	) {
		var emittedEvent: ReaderPresentationEvent? = null
		var acceptedReceipt: ReaderPresentationEventReceipt? = null
		var receiptDispatchInProgress = false
	}

	private data class CancelledLiveEngineHandoff(
		val direction: ReaderLiveEngineHandoffDirection,
		val binding: ReaderPresentationBinding
	)

	private var currentDecision: ReaderPresentationDecision? = null
	private var pendingCoverCommit: PendingCoverCommit? = null
	private var pendingLiveEngineExposure: PendingLiveEngineExposure? = null
	private var cancelledLiveEngineHandoff: CancelledLiveEngineHandoff? = null
	private var lastLiveEngineExposureRequired: Boolean? = null
	private var committedTransition: ReaderRequiredTransition.CommitShellCover? = null
	private var presentedFrame = 0L
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var restartPendingCoverCommit: PendingCoverCommit? = null
	private val completedFrozen = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private var disposed = false
	private val transitionTimeout = ReaderPresentationTransitionTimeout(
		scheduler = transitionTimeoutScheduler,
		nowMillis = transitionNowMillis
	) { event ->
		val receipt = onEvent(event).takeIf { it.authorizes(event) }
		if (receipt != null) update(readerPresentationDecision(receipt.postState))
		receipt != null
	}

	fun update(decision: ReaderPresentationDecision) {
		if (disposed || frozenDomain != null) return
		val liveEngineRequired = liveEngineExposureRequired()
		synchronizeLiveEngineHandoffIntent(decision, liveEngineRequired)
		currentDecision = decision
		transitionTimeout.update(decision)
		if (currentDecision != decision) return
		host.applyPresentationFrameOwner(decision)
		if (decision.lifecycle != ReaderPresentationLifecycleState.Foreground) {
			cancelPendingCoverCommit()
			cancelPendingLiveEngineExposure()
			return
		}
		if (requestInitialShellCoverIfRequired(decision, liveEngineRequired)) return
		if (requestLiveEngineExposureIfRequired(decision, liveEngineRequired)) return
		if (requestNativePageExposureIfRequired(decision, liveEngineRequired)) return
		updateLiveEngineExposure(decision, liveEngineRequired)
		val pending = pendingCoverCommit
		if (pending?.emittedProof != null && pending.acceptedReceipt == null) {
			dispatchCoverReceipt(
				pending,
				ReaderPresentationEvent.ShellCoverCommitted(pending.emittedProof!!)
			)
			return
		}
		if (pending?.acceptedReceipt != null) {
			if (acceptedShellCoverDecisionMatches(decision, pending)) {
				completePendingCoverCommit(pending)
				return
			}
			val emittedTransition = pending.transition
			cancelPendingCoverCommit()
			committedTransition = emittedTransition
			val replacement =
				decision.requiredTransition as? ReaderRequiredTransition.CommitShellCover
			if (replacement == null || replacement == emittedTransition) return
			committedTransition = null
			beginCoverCommit(decision, replacement)
			return
		}

		val transition = decision.requiredTransition as? ReaderRequiredTransition.CommitShellCover
		if (transition == null) {
			cancelPendingCoverCommit()
			return
		}
		if (pending?.transition == transition) {
			val geometry = currentGeometryOrNull()
			if (
				geometry == pending.geometry &&
				hostFactsMatch(
					decision,
					transition,
					geometry,
					requirePreparedCover = true
				)
			) return
		}
		if (committedTransition == transition) return
		cancelPendingCoverCommit()
		committedTransition = null
		beginCoverCommit(decision, transition)
	}

	fun onHostDetached() {
		if (frozenDomain != null) return
		cancelPendingCoverCommit()
		cancelPendingLiveEngineExposure()
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (disposed || (frozenDomain != null && frozenDomain != domain)) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		val timeoutResult = transitionTimeout.freezeForTransitionActivation(domain)
		if (timeoutResult != ReaderPortCommandResult.Accepted) return timeoutResult
		frozenDomain = domain
		return ReaderPortCommandResult.Accepted
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		val domain = frozenDomain ?: return emptyList()
		val coverRows = pendingCoverCommit?.let { pending ->
			buildList {
				add(hostBridgeOwnershipRow(
					domain,
					pending.ownershipToken,
					ReaderTransitionResourceKind.FrameHandoff,
					ReaderLegacyResourceState.Running
				))
				if (pending.callbackOwned) {
					add(hostBridgeOwnershipRow(
						domain,
						pending.callbackToken,
						ReaderTransitionResourceKind.CallbackRegistration,
						ReaderLegacyResourceState.Registered
					))
				}
			}
		}.orEmpty()
		return coverRows + transitionTimeout.snapshotFrozenOwnership()
	}

	private fun hostBridgeOwnershipRow(
		domain: ReaderLegacyPhysicalDomain,
		token: ReaderLegacySourceLocalOpaqueToken,
		kind: ReaderTransitionResourceKind,
		state: ReaderLegacyResourceState
	) = ReaderFrozenLegacyResource(
		freezeToken = domain.freezeToken,
		physicalIdentity = ReaderLegacyPhysicalIdentity(
			domain,
			ReaderLegacyInventorySource.FrameOrHandoff,
			token
		),
		kind = kind,
		binding = null,
		visibleOwner = null,
		origin = ReaderLegacyResourceOrigin.Owned,
		state = state,
		mayBeCommittedPredecessor = false
	)

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		if (physicalIdentity.source == ReaderLegacyInventorySource.DeadlineRegistration) {
			return transitionTimeout.drainFrozenOwnership(physicalIdentity, onConfirmed)
		}
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.FrameOrHandoff
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val token = physicalIdentity.sourceLocalToken
		val pending = pendingCoverCommit
		if (pending?.callbackToken == token && pending.callbackOwned) {
			pending.callbackOwned = false
			pending.callbackEpoch = Math.incrementExact(pending.callbackEpoch)
			pending.unregisterOnce()
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (pending?.ownershipToken == token) {
			pendingCoverCommit = null
			if (pending.callbackOwned) {
				pending.callbackOwned = false
				pending.callbackEpoch = Math.incrementExact(pending.callbackEpoch)
				pending.unregisterOnce()
				completedFrozen += pending.callbackToken
			}
			host.cancelOpaqueShellCoverPreparation(pending.transition.coverGeneration)
			restartPendingCoverCommit = pending
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (completedFrozen.remove(token)) {
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (frozenDomain != domain) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val restart = restartPendingCoverCommit
		try {
			if (restart != null) {
				host.prepareOpaqueShellCover(restart.transition.coverGeneration)
				restart.registration = null
				restart.registrationRemoved = false
				restart.callbackOwned = true
				restart.frameScheduled = false
				pendingCoverCommit = restart
				registerCoverDrawListener(restart)
			} else {
				pendingCoverCommit?.takeIf { !it.callbackOwned }?.let { pending ->
					pending.registration = null
					pending.registrationRemoved = false
					pending.callbackOwned = true
					registerCoverDrawListener(pending)
				}
			}
		} catch (_: Throwable) {
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.PortRejected
			)
		}
		val timeoutResult = transitionTimeout.restoreAfterTransitionActivation(domain)
		if (timeoutResult != ReaderPortCommandResult.Accepted) return timeoutResult
		restartPendingCoverCommit = null
		completedFrozen.clear()
		frozenDomain = null
		return ReaderPortCommandResult.Accepted
	}

	fun dispose() {
		if (disposed) return
		disposed = true
		transitionTimeout.cancel()
		cancelPendingCoverCommit()
		cancelPendingLiveEngineExposure()
		liveEngineVisualHandoff?.close()
		currentDecision = null
		cancelledLiveEngineHandoff = null
		lastLiveEngineExposureRequired = null
		committedTransition = null
	}

	private fun synchronizeLiveEngineHandoffIntent(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	) {
		val previousMode = lastLiveEngineExposureRequired
		if (previousMode != null && previousMode != liveEngineRequired) {
			cancelledLiveEngineHandoff = null
		}
		lastLiveEngineExposureRequired = liveEngineRequired
		if (cancelledLiveEngineHandoff?.binding != decision.targetBinding) {
			cancelledLiveEngineHandoff = null
		}

		val previousPending = currentDecision?.authority as?
			ReaderPresentationAuthority.LiveEngineHandoffPending
		val pending = decision.authority as?
			ReaderPresentationAuthority.LiveEngineHandoffPending
		if (pending != null) {
			if (pending != previousPending) cancelledLiveEngineHandoff = null
			return
		}
		if (previousPending != null && decision.restoresPredecessorFor(previousPending)) {
			cancelledLiveEngineHandoff = CancelledLiveEngineHandoff(
				previousPending.direction,
				previousPending.binding
			)
		}
	}

	private fun cancelledHandoffMatches(
		direction: ReaderLiveEngineHandoffDirection,
		binding: ReaderPresentationBinding?
	): Boolean = cancelledLiveEngineHandoff == binding?.let {
		CancelledLiveEngineHandoff(direction, it)
	}

	private fun ReaderPresentationDecision.restoresPredecessorFor(
		pending: ReaderPresentationAuthority.LiveEngineHandoffPending
	): Boolean {
		if (
			targetBinding != pending.binding ||
			requiredTransition != ReaderRequiredTransition.None ||
			diagnosticPresentation != ReaderDiagnosticPresentation.Hidden
		) return false
		return when (val retained = pending.retainedFrame) {
			is ReaderPresentationFrameOwner.NativePage ->
				authority == ReaderPresentationAuthority.SettledNativePage(retained) &&
					frameOwner == retained
			is ReaderPresentationFrameOwner.LiveEngine ->
				authority == ReaderPresentationAuthority.LiveEngineExposed(retained) &&
					frameOwner == retained
			is ReaderPresentationFrameOwner.ShellCover ->
				authority == ReaderPresentationAuthority.ShellCover(retained.proof) &&
					frameOwner == retained
			else -> false
		}
	}

	private fun requestInitialShellCoverIfRequired(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	): Boolean {
		val binding = decision.targetBinding ?: return false
		if (
			!liveEngineRequired ||
			!host.isAttachedToWindow ||
			host.currentPresentationBinding != binding ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
			decision.authority != ReaderPresentationAuthority.Unavailable ||
			decision.frameOwner != ReaderPresentationFrameOwner.Neutral
		) return false
		val event = ReaderPresentationEvent.ShellCoverRequested(
			coverGeneration = binding.publicationGeneration
		)
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return false
		update(readerPresentationDecision(receipt.postState))
		return true
	}

	private fun requestLiveEngineExposureIfRequired(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	): Boolean {
		if (!liveEngineRequired) return false
		if (
			!host.isAttachedToWindow ||
			host.currentPresentationBinding != decision.targetBinding ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
			cancelledHandoffMatches(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine,
				decision.targetBinding
			) ||
			(
				decision.authority !is ReaderPresentationAuthority.ShellCover &&
					decision.authority !is ReaderPresentationAuthority.SettledNativePage
			)
		) return false
		val event = ReaderPresentationEvent.WebViewHandoffRequested(
			ReaderLiveEngineHandoffDirection.NativeToLiveEngine
		)
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return false
		update(readerPresentationDecision(receipt.postState))
		return true
	}

	private fun requestNativePageExposureIfRequired(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	): Boolean {
		if (liveEngineRequired) return false
		if (
			!host.isAttachedToWindow ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
			cancelledHandoffMatches(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative,
				decision.targetBinding
			) ||
			decision.authority !is ReaderPresentationAuthority.LiveEngineExposed
		) return false
		val event = ReaderPresentationEvent.WebViewHandoffRequested(
			ReaderLiveEngineHandoffDirection.LiveEngineToNative
		)
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return false
		update(readerPresentationDecision(receipt.postState))
		return true
	}

	private fun updateLiveEngineExposure(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	) {
		if (!liveEngineRequired) {
			cancelPendingLiveEngineExposure()
			return
		}
		val transition = decision.requiredTransition as? ReaderRequiredTransition.ExposeLiveEngine
		val pending = pendingLiveEngineExposure
		if (pending != null) {
			when {
				pending.acceptedReceipt != null -> {
					if (acceptedLiveEngineDecisionMatches(decision, pending)) {
						pendingLiveEngineExposure = null
						return
					}
					cancelPendingLiveEngineExposure()
				}
				pending.transition != transition -> {
					if (transition?.token == pending.transition.token) {
						if (
							liveEngineVisualHandoff?.rebindPresentationRequest(
								pending.transition,
								transition
							) == true
						) {
							pending.transition = transition
							return
						}
						cancelPendingLiveEngineExposure()
						PendingLiveEngineExposure(transition).also { failed ->
							pendingLiveEngineExposure = failed
							publishLiveEngineExposureFailure(
								failed,
								ReaderWebViewVisualHandoffFailure.Invalidated
							)
						}
						return
					}
					cancelPendingLiveEngineExposure()
				}
				pending.emittedEvent != null -> {
					dispatchLiveEngineReceipt(pending, pending.emittedEvent!!)
					return
				}
				else -> return
			}
		}
		if (transition == null || !liveEngineHostFactsMatch(decision, transition)) return

		val next = PendingLiveEngineExposure(transition)
		pendingLiveEngineExposure = next
		val handoff = liveEngineVisualHandoff
		if (handoff == null) {
			publishLiveEngineExposureFailure(
				next,
				ReaderWebViewVisualHandoffFailure.Detached
			)
			return
		}
		try {
			handoff.await(transition.token, transition.binding, deadlineDelegated = true) { result ->
				onLiveEngineVisualHandoffResult(next, result)
			}
		} catch (_: Throwable) {
			if (pendingLiveEngineExposure === next && next.emittedEvent == null) {
				publishLiveEngineExposureFailure(
					next,
					ReaderWebViewVisualHandoffFailure.Invalidated
				)
			}
		}
	}

	private fun onLiveEngineVisualHandoffResult(
		pending: PendingLiveEngineExposure,
		result: ReaderPresentationWebViewVisualHandoffResult
	) {
		if (pendingLiveEngineExposure !== pending || disposed) return
		if (
			result.token != pending.transition.token ||
			result.binding != pending.transition.binding
		) return
		when (result) {
			is ReaderPresentationWebViewVisualHandoffResult.Ready -> {
				val decision = currentDecision ?: return
				if (!liveEngineHostFactsMatch(decision, pending.transition)) return
				val event = ReaderPresentationEvent.LiveEngineExposureCommitted(
					ReaderLiveEnginePresentationProof(
						token = result.token,
						binding = result.binding,
						presentedFrameSequence = result.presentedFrameSequence
					)
				)
				pending.emittedEvent = event
				dispatchLiveEngineReceipt(pending, event)
			}
			is ReaderPresentationWebViewVisualHandoffResult.Failed ->
				publishLiveEngineExposureFailure(pending, result.reason)
		}
	}

	private fun publishLiveEngineExposureFailure(
		pending: PendingLiveEngineExposure,
		reason: ReaderWebViewVisualHandoffFailure
	) {
		if (pendingLiveEngineExposure !== pending || pending.emittedEvent != null) return
		val event = ReaderPresentationEvent.LiveEngineExposureFailed(
			direction = pending.transition.direction,
			token = pending.transition.token,
			binding = pending.transition.binding,
			reason = if (reason == ReaderWebViewVisualHandoffFailure.TimedOut) {
				ReaderPresentationFailureReason.TimedOut
			} else {
				ReaderPresentationFailureReason.LiveEngineUnavailable
			}
		)
		pending.emittedEvent = event
		dispatchLiveEngineReceipt(pending, event)
	}

	private fun dispatchLiveEngineReceipt(
		pending: PendingLiveEngineExposure,
		event: ReaderPresentationEvent
	) {
		if (pendingLiveEngineExposure !== pending || pending.receiptDispatchInProgress) return
		pending.receiptDispatchInProgress = true
		val receipt = try {
			onEvent(event).takeIf { it.authorizes(event) }
		} finally {
			pending.receiptDispatchInProgress = false
		} ?: return
		pending.acceptedReceipt = receipt
		update(readerPresentationDecision(receipt.postState))
	}

	private fun acceptedLiveEngineDecisionMatches(
		decision: ReaderPresentationDecision,
		pending: PendingLiveEngineExposure
	): Boolean {
		val event = pending.emittedEvent as? ReaderPresentationEvent.LiveEngineExposureCommitted
			?: return false
		val proof = event.proof
		val authority = decision.authority as? ReaderPresentationAuthority.LiveEngineExposed
		return authority?.frame?.proof == proof &&
			decision.frameOwner == ReaderPresentationFrameOwner.LiveEngine(proof) &&
			decision.targetBinding == pending.transition.binding &&
			decision.requiredTransition == ReaderRequiredTransition.None &&
			proof.token == pending.transition.token &&
			proof.binding == pending.transition.binding
	}

	private fun liveEngineHostFactsMatch(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.ExposeLiveEngine
	): Boolean {
		val authority = decision.authority as?
			ReaderPresentationAuthority.LiveEngineHandoffPending ?: return false
		return host.isAttachedToWindow &&
			host.currentPresentationBinding == transition.binding &&
			decision.targetBinding == transition.binding &&
			decision.requiredTransition == transition &&
			(
				decision.frameOwner is ReaderPresentationFrameOwner.NativePage ||
					decision.frameOwner is ReaderPresentationFrameOwner.ShellCover
			) &&
			authority.retainedFrame == decision.frameOwner &&
			authority.direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine &&
			authority.token == transition.token &&
			authority.binding == transition.binding
	}

	private fun cancelPendingLiveEngineExposure() {
		if (pendingLiveEngineExposure == null) return
		pendingLiveEngineExposure = null
		liveEngineVisualHandoff?.invalidate()
	}

	private fun beginCoverCommit(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.CommitShellCover
	) {
		val geometry = currentGeometryOrNull() ?: return
		if (!hostFactsMatch(decision, transition, geometry, requirePreparedCover = false)) return

		try {
			host.prepareOpaqueShellCover(transition.coverGeneration)
		} catch (_: Throwable) {
			failCoverCommit(transition)
			return
		}
		if (!hostFactsMatch(decision, transition, geometry, requirePreparedCover = true)) {
			host.cancelOpaqueShellCoverPreparation(transition.coverGeneration)
			return
		}

		val pending = PendingCoverCommit(
			transition,
			geometry,
			tokenAllocator.allocate(),
			tokenAllocator.allocate()
		)
		pendingCoverCommit = pending
		try {
			registerCoverDrawListener(pending)
		} catch (_: Throwable) {
			pendingCoverCommit = null
			failCoverCommit(transition)
		}
	}

	private fun registerCoverDrawListener(pending: PendingCoverCommit) {
		val callbackEpoch = pending.callbackEpoch
		val registration = host.registerShellCoverDrawListener {
			onCoverDrawn(pending, callbackEpoch)
		}
		pending.registration = registration
		when {
			pending.registrationRemoved -> registration.unregister()
			pendingCoverCommit !== pending -> pending.unregisterOnce()
		}
	}

	private fun onCoverDrawn(
		pending: PendingCoverCommit,
		callbackEpoch: Long
	) {
		if (
			pendingCoverCommit !== pending ||
			pending.frameScheduled ||
			!pending.callbackOwned ||
			pending.callbackEpoch != callbackEpoch
		) return
		if (frozenDomain != null) {
			pending.callbackOwned = false
			pending.callbackEpoch = Math.incrementExact(pending.callbackEpoch)
			pending.unregisterOnce()
			completedFrozen += pending.callbackToken
			return
		}
		val decision = currentDecision
		if (
			decision == null ||
			!hostFactsMatch(
				decision,
				pending.transition,
				pending.geometry,
				requirePreparedCover = true
			)
		) {
			cancelPendingCoverCommit()
			return
		}

		pending.frameScheduled = true
		pending.unregisterOnce()
		host.postShellCoverAnimationFrame {
			onCoverAnimationFrame(pending, callbackEpoch)
		}
	}

	private fun onCoverAnimationFrame(
		pending: PendingCoverCommit,
		callbackEpoch: Long
	) {
		if (
			pendingCoverCommit !== pending ||
			!pending.callbackOwned ||
			pending.callbackEpoch != callbackEpoch
		) return
		pending.callbackOwned = false
		if (frozenDomain != null) {
			completedFrozen += pending.callbackToken
			return
		}
		val decision = currentDecision
		if (
			decision == null ||
			!hostFactsMatch(
				decision,
				pending.transition,
				pending.geometry,
				requirePreparedCover = true
			)
		) {
			cancelPendingCoverCommit()
			return
		}

		presentedFrame = Math.incrementExact(presentedFrame)
		val proof = ReaderShellCoverCommitProof(
			token = pending.transition.token,
			binding = pending.transition.binding,
			coverGeneration = pending.transition.coverGeneration,
			presentedFrame = presentedFrame,
			viewportWidth = pending.geometry.width,
			viewportHeight = pending.geometry.height
		)
		pending.emittedProof = proof
		dispatchCoverReceipt(
			pending,
			ReaderPresentationEvent.ShellCoverCommitted(proof)
		)
	}

	private fun dispatchCoverReceipt(
		pending: PendingCoverCommit,
		event: ReaderPresentationEvent
	) {
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return
		pending.acceptedReceipt = receipt
		val receiptDecision = readerPresentationDecision(receipt.postState)
		currentDecision = receiptDecision
		transitionTimeout.update(receiptDecision)
		if (acceptedShellCoverDecisionMatches(receiptDecision, pending)) {
			completePendingCoverCommit(pending)
		}
	}

	private fun acceptedShellCoverDecisionMatches(
		decision: ReaderPresentationDecision,
		pending: PendingCoverCommit
	): Boolean {
		val proof = (decision.frameOwner as? ReaderPresentationFrameOwner.ShellCover)?.proof
		val authorityProof = (decision.authority as? ReaderPresentationAuthority.ShellCover)?.proof
		return proof != null &&
			proof == pending.emittedProof &&
			authorityProof == proof &&
			proof.token == pending.transition.token &&
			proof.binding == pending.transition.binding &&
			proof.coverGeneration == pending.transition.coverGeneration &&
			proof.viewportWidth == pending.geometry.width &&
			proof.viewportHeight == pending.geometry.height &&
			decision.requiredTransition !is ReaderRequiredTransition.CommitShellCover &&
			host.shellCoverSelected &&
			host.isAttachedToWindow &&
			host.currentPresentationBinding == pending.transition.binding &&
			host.currentShellCoverGeneration == pending.transition.coverGeneration &&
			host.measuredViewportWidth == pending.geometry.width &&
			host.measuredViewportHeight == pending.geometry.height
	}

	private fun completePendingCoverCommit(pending: PendingCoverCommit) {
		if (pendingCoverCommit !== pending) return
		pendingCoverCommit = null
		pending.unregisterOnce()
		committedTransition = pending.transition
		host.completeOpaqueShellCoverPreparation(pending.transition.coverGeneration)
	}

	private fun cancelPendingCoverCommit() {
		val pending = pendingCoverCommit ?: return
		pendingCoverCommit = null
		pending.unregisterOnce()
		host.cancelOpaqueShellCoverPreparation(pending.transition.coverGeneration)
	}

	private fun failCoverCommit(
		transition: ReaderRequiredTransition.CommitShellCover
	) {
		try {
			host.cancelOpaqueShellCoverPreparation(transition.coverGeneration)
		} finally {
			onEvent(
				ReaderPresentationEvent.ShellCoverFailed(
					token = transition.token,
					binding = transition.binding
				)
			)
		}
	}

	private fun currentGeometryOrNull(): ViewportGeometry? {
		val width = host.measuredViewportWidth
		val height = host.measuredViewportHeight
		return if (width > 0 && height > 0) ViewportGeometry(width, height) else null
	}

	private fun hostFactsMatch(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.CommitShellCover,
		geometry: ViewportGeometry,
		requirePreparedCover: Boolean
	): Boolean =
		host.isAttachedToWindow &&
			decision.requiredTransition == transition &&
			decision.retainsPredecessor(transition.token, transition.binding) &&
			host.currentPresentationBinding == transition.binding &&
			(!requirePreparedCover ||
				host.currentShellCoverGeneration == transition.coverGeneration) &&
			host.measuredViewportWidth == geometry.width &&
			host.measuredViewportHeight == geometry.height
}

internal fun ReaderPresentationEventReceipt?.authorizes(
	event: ReaderPresentationEvent
): Boolean = this != null &&
	this.event == event &&
	(
		disposition == ReaderPresentationEventDisposition.Accepted ||
			disposition == ReaderPresentationEventDisposition.Idempotent
	)

private fun ReaderPresentationDecision.retainsPredecessor(
	token: ReaderPresentationToken,
	binding: ReaderPresentationBinding
): Boolean {
	val required = requiredTransition as? ReaderRequiredTransition.CommitShellCover
	if (required?.token != token || required.binding != binding) return false
	return when (val owner = frameOwner) {
		ReaderPresentationFrameOwner.Neutral -> {
			val pending = authority as? ReaderPresentationAuthority.ShellCoverCommitPending
			pending?.token == token &&
				pending.binding == binding &&
				pending.retainedFrame == ReaderShellCoverRetainedFrame.Neutral(binding)
		}
		is ReaderPresentationFrameOwner.NativePage -> owner.proof.binding == binding
		is ReaderPresentationFrameOwner.Curl -> owner.frame.binding == binding
		else -> false
	}
}
