package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationPublicationIdentity
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.readerPresentationDecision

internal const val ReaderPresentationLifecyclePendingEventLimit = 14

internal data class ReaderPresentationLifecycleReceiptAdmission(
	val event: ReaderPresentationEvent.Lifecycle,
	val receipt: ReaderPresentationEventReceipt
)

internal class ReaderPresentationLifecycleDelivery {
	private var expectedReaderSessionGeneration: Long? = null
	private var expectedPublicationIdentity: ReaderPresentationPublicationIdentity? = null
	private var minimumEventSequence: Long = 0L
	private var acknowledgedVersion: ReaderPresentationReceiptVersion? = null
	private var initialLifecycle: ReaderPresentationLifecycleState =
		ReaderPresentationLifecycleState.Foreground
	var acknowledgedLifecycle: ReaderPresentationLifecycleState? = null
		private set
	private var observedWindowVisible: Boolean? = null
	private var visibilityLossRequired = false
	private var visibilityConfirmationRequired = false
	private val pendingMemoryPressure = linkedSetOf<ReaderPresentationLifecycleEvent>()
	private var rendererLossPending = false
	private var publicationClosePending = false
	private var publicationCloseAcknowledged = false
	private var retryInProgress = false

	val pendingEventCount: Int
		get() = visibilityPendingEventCount() +
			pendingMemoryPressure.size +
			(if (rendererLossPending) 1 else 0) +
			(if (publicationClosePending) 1 else 0)

	val hasPendingRendererLoss: Boolean
		get() = rendererLossPending

	fun reset(
		version: ReaderPresentationReceiptVersion,
		observedWindowVisible: Boolean?,
		initialLifecycle: ReaderPresentationLifecycleState =
			ReaderPresentationLifecycleState.Foreground
	) {
		expectedReaderSessionGeneration = version.readerSessionGeneration
		expectedPublicationIdentity = null
		minimumEventSequence = version.eventSequence
		acknowledgedVersion = null
		this.initialLifecycle = initialLifecycle
		acknowledgedLifecycle = null
		this.observedWindowVisible = observedWindowVisible
		visibilityLossRequired = observedWindowVisible == false
		visibilityConfirmationRequired = observedWindowVisible != null
		pendingMemoryPressure.clear()
		rendererLossPending = false
		publicationClosePending = false
		publicationCloseAcknowledged = false
		retryInProgress = false
	}

	fun bindPublication(identity: ReaderPresentationPublicationIdentity): Boolean {
		if (expectedReaderSessionGeneration == null || publicationCloseAcknowledged) return false
		val expected = expectedPublicationIdentity
		if (expected != null && expected != identity) return false
		expectedPublicationIdentity = identity
		return true
	}

	fun advanceComposeVersion(version: ReaderPresentationReceiptVersion): Boolean {
		val expectedSession = expectedReaderSessionGeneration ?: return false
		val expectedPublication = expectedPublicationIdentity ?: return false
		if (
			version.readerSessionGeneration != expectedSession ||
			version.publicationIdentity != expectedPublication
		) return false
		minimumEventSequence = maxOf(minimumEventSequence, version.eventSequence)
		return true
	}

	fun observe(event: ReaderPresentationLifecycleEvent) {
		if (publicationCloseAcknowledged) return
		if (event == ReaderPresentationLifecycleEvent.PublicationClosed) {
			publicationClosePending = true
			return
		}
		if (publicationClosePending) return
		when (event) {
			ReaderPresentationLifecycleEvent.VisibilityLost -> {
				observedWindowVisible = false
				if (acknowledgedLifecycle == ReaderPresentationLifecycleState.Background) {
					visibilityLossRequired = false
					visibilityConfirmationRequired = false
				} else {
					visibilityLossRequired = true
					visibilityConfirmationRequired = true
				}
			}
			ReaderPresentationLifecycleEvent.VisibilityRestored -> {
				observedWindowVisible = true
				visibilityConfirmationRequired = visibilityLossRequired ||
					acknowledgedLifecycle != ReaderPresentationLifecycleState.Foreground
			}
			is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
			is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure ->
				pendingMemoryPressure += event
			ReaderPresentationLifecycleEvent.RendererLost -> rendererLossPending = true
			ReaderPresentationLifecycleEvent.PublicationClosed -> error("Handled above")
		}
		check(pendingEventCount <= ReaderPresentationLifecyclePendingEventLimit)
	}

	fun retry(
		dispatch: (ReaderPresentationEvent.Lifecycle) -> ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? {
		if (retryInProgress || expectedPublicationIdentity == null) return null
		val lifecycleEvent = nextPendingEvent() ?: return null
		val event = ReaderPresentationEvent.Lifecycle(lifecycleEvent)
		retryInProgress = true
		val receipt = try {
			dispatch(event)
		} catch (_: Throwable) {
			null
		} finally {
			retryInProgress = false
		} ?: return null
		if (acknowledgedVersion == receipt.version && receipt.event == event) return receipt
		val admission = classifyReceipt(event, receipt) ?: return null
		commitReceipt(admission)
		return receipt
	}

	fun classifyReceipt(
		event: ReaderPresentationEvent.Lifecycle,
		receipt: ReaderPresentationEventReceipt?
	): ReaderPresentationLifecycleReceiptAdmission? {
		if (event.event != nextPendingEvent()) return null
		if (!receiptAcknowledges(event, receipt)) return null
		return ReaderPresentationLifecycleReceiptAdmission(event, checkNotNull(receipt))
	}

	fun commitReceipt(admission: ReaderPresentationLifecycleReceiptAdmission) {
		acknowledge(admission.event.event, admission.receipt)
	}

	private fun nextPendingEvent(): ReaderPresentationLifecycleEvent? {
		if (publicationClosePending) return ReaderPresentationLifecycleEvent.PublicationClosed
		val visibility = nextVisibilityEvent()
		if (visibility == ReaderPresentationLifecycleEvent.VisibilityLost) return visibility
		if (rendererLossPending) return ReaderPresentationLifecycleEvent.RendererLost
		if (visibility != null) return visibility
		return pendingMemoryPressure.firstOrNull()
	}

	private fun nextVisibilityEvent(): ReaderPresentationLifecycleEvent? {
		if (
			visibilityLossRequired &&
			acknowledgedLifecycle != ReaderPresentationLifecycleState.Background
		) return ReaderPresentationLifecycleEvent.VisibilityLost
		if (!visibilityConfirmationRequired) return null
		return when (observedWindowVisible) {
			false -> ReaderPresentationLifecycleEvent.VisibilityLost.takeIf {
				acknowledgedLifecycle != ReaderPresentationLifecycleState.Background
			}
			true -> ReaderPresentationLifecycleEvent.VisibilityRestored.takeIf {
				acknowledgedLifecycle != ReaderPresentationLifecycleState.Foreground
			}
			null -> null
		}
	}

	private fun visibilityPendingEventCount(): Int {
		val next = nextVisibilityEvent() ?: return 0
		return if (
			next == ReaderPresentationLifecycleEvent.VisibilityLost &&
			observedWindowVisible == true
		) 2 else 1
	}

	private fun receiptAcknowledges(
		event: ReaderPresentationEvent.Lifecycle,
		receipt: ReaderPresentationEventReceipt?
	): Boolean {
		val expectedSession = expectedReaderSessionGeneration ?: return false
		val expectedPublication = expectedPublicationIdentity ?: return false
		if (
			receipt == null ||
			receipt.event != event ||
			receipt.version.readerSessionGeneration != expectedSession ||
			receipt.version.publicationIdentity != expectedPublication ||
			receipt.version.eventSequence < minimumEventSequence ||
			receipt.version.eventSequence <= (acknowledgedVersion?.eventSequence ?: -1L)
		) return false
		val postPublicationMatches = when (event.event) {
			ReaderPresentationLifecycleEvent.PublicationClosed -> receipt.postState.binding == null
			else -> receipt.postState.binding?.let { binding ->
				ReaderPresentationPublicationIdentity(
					foliateSessionId = binding.foliateSessionId,
					publicationGeneration = binding.publicationGeneration
				)
			} == expectedPublication
		}
		if (!postPublicationMatches) return false
		val expectedLifecycle = expectedPostLifecycle(event.event)
		if (receipt.postState.lifecycle != expectedLifecycle) return false
		return when (event.event) {
			ReaderPresentationLifecycleEvent.PublicationClosed ->
				receipt.disposition == ReaderPresentationEventDisposition.Accepted ||
					receipt.disposition == ReaderPresentationEventDisposition.Idempotent ||
					receipt.disposition == ReaderPresentationEventDisposition.Destroyed
			else -> receipt.disposition == ReaderPresentationEventDisposition.Accepted ||
				receipt.disposition == ReaderPresentationEventDisposition.Idempotent
		}
	}

	private fun expectedPostLifecycle(
		event: ReaderPresentationLifecycleEvent
	): ReaderPresentationLifecycleState = when (event) {
		ReaderPresentationLifecycleEvent.VisibilityLost -> ReaderPresentationLifecycleState.Background
		ReaderPresentationLifecycleEvent.VisibilityRestored -> ReaderPresentationLifecycleState.Foreground
		ReaderPresentationLifecycleEvent.PublicationClosed -> ReaderPresentationLifecycleState.Destroyed
		is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
		is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure,
		ReaderPresentationLifecycleEvent.RendererLost ->
			acknowledgedLifecycle ?: initialLifecycle
	}

	private fun acknowledge(
		event: ReaderPresentationLifecycleEvent,
		receipt: ReaderPresentationEventReceipt
	) {
		acknowledgedVersion = receipt.version
		minimumEventSequence = maxOf(minimumEventSequence, receipt.version.eventSequence)
		acknowledgedLifecycle = receipt.postState.lifecycle
		when (event) {
			ReaderPresentationLifecycleEvent.VisibilityLost -> {
				visibilityLossRequired = false
				visibilityConfirmationRequired = observedWindowVisible == true
			}
			ReaderPresentationLifecycleEvent.VisibilityRestored ->
				visibilityConfirmationRequired = observedWindowVisible != true
			is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
			is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure ->
				pendingMemoryPressure.remove(event)
			ReaderPresentationLifecycleEvent.RendererLost -> rendererLossPending = false
			ReaderPresentationLifecycleEvent.PublicationClosed -> {
				publicationClosePending = false
				publicationCloseAcknowledged = true
				visibilityLossRequired = false
				visibilityConfirmationRequired = false
				pendingMemoryPressure.clear()
				rendererLossPending = false
			}
		}
	}
}

internal const val ReaderPresentationHostEffectLimit = 16

internal data class ReaderPresentationHostEffectIdentity(
	val hostEpoch: Long,
	val version: ReaderPresentationReceiptVersion
)

internal data class ReaderPresentationHostEffect(
	val identity: ReaderPresentationHostEffectIdentity,
	val event: ReaderPresentationEvent?,
	val decision: ReaderPresentationDecision,
	val rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?
) {
	val terminalPriority: Boolean
		get() = event is ReaderPresentationEvent.Lifecycle &&
			(
				event.event == ReaderPresentationLifecycleEvent.RendererLost ||
					event.event == ReaderPresentationLifecycleEvent.PublicationClosed
			)
}

internal enum class ReaderPresentationHostMutation {
	Model,
	ViewerDecision,
	ShellCoverLayer,
	ViewerCoverVisibility,
	NativeCoverVisibility
}

internal class ReaderPresentationHostEffectApplier(
	private val commitHostModel: (ReaderNativePresentationApplication) -> Unit,
	private val applyViewerDecision: (
		ReaderPresentationDecision,
		ReaderRendererLossCancellationIdentity?
	) -> Unit,
	private val applyShellCoverLayer: (ReaderNativePresentationApplication) -> Unit,
	private val applyViewerCoverVisibility: (ReaderNativePresentationApplication) -> Unit,
	private val applyNativeCoverVisibility: (ReaderNativePresentationApplication) -> Unit,
	private val afterMutation: (ReaderPresentationHostMutation) -> Unit = {}
) {
	private data class Progress(
		val effect: ReaderPresentationHostEffect,
		val application: ReaderNativePresentationApplication,
		var modelComplete: Boolean = false,
		var viewerComplete: Boolean = false,
		var shellCoverComplete: Boolean = false,
		var viewerCoverComplete: Boolean = false,
		var nativeCoverComplete: Boolean = false
	)

	private val active = linkedMapOf<ReaderPresentationHostEffectIdentity, Progress>()
	private val completed = linkedSetOf<ReaderPresentationHostEffectIdentity>()
	private var currentHostEpoch: Long = -1L

	fun apply(effect: ReaderPresentationHostEffect) {
		when {
			effect.identity.hostEpoch < currentHostEpoch -> return
			effect.identity.hostEpoch > currentHostEpoch -> {
				currentHostEpoch = effect.identity.hostEpoch
				active.clear()
				completed.clear()
			}
		}
		if (effect.identity in completed) return
		val progress = active.getOrPut(effect.identity) {
			Progress(effect, readerNativePresentationApplication(effect.decision))
		}
		check(progress.effect == effect)
		if (!progress.modelComplete) {
			commitHostModel(progress.application)
			progress.modelComplete = true
			afterMutation(ReaderPresentationHostMutation.Model)
		}
		if (!progress.viewerComplete) {
			applyViewerDecision(
				progress.application.decision,
				effect.rendererLossCancellationIdentity
			)
			progress.viewerComplete = true
			afterMutation(ReaderPresentationHostMutation.ViewerDecision)
		}
		if (!progress.shellCoverComplete) {
			applyShellCoverLayer(progress.application)
			progress.shellCoverComplete = true
			afterMutation(ReaderPresentationHostMutation.ShellCoverLayer)
		}
		if (!progress.viewerCoverComplete) {
			applyViewerCoverVisibility(progress.application)
			progress.viewerCoverComplete = true
			afterMutation(ReaderPresentationHostMutation.ViewerCoverVisibility)
		}
		if (!progress.nativeCoverComplete) {
			applyNativeCoverVisibility(progress.application)
			progress.nativeCoverComplete = true
			afterMutation(ReaderPresentationHostMutation.NativeCoverVisibility)
		}
		active.remove(effect.identity)
		completed += effect.identity
		while (completed.size > ReaderPresentationHostEffectLimit) {
			completed.remove(completed.first())
		}
	}
}

internal class ReaderPresentationReceiptDispatcher(
	private val bindingReporter: ReaderPresentationBindingReporter,
	private val lifecycleDelivery: ReaderPresentationLifecycleDelivery,
	private val applyHostEffect: (ReaderPresentationHostEffect) -> Unit
) {
	constructor(
		bindingReporter: ReaderPresentationBindingReporter,
		lifecycleDelivery: ReaderPresentationLifecycleDelivery,
		applyDecision: (
			ReaderPresentationDecision,
			ReaderRendererLossCancellationIdentity?
		) -> Unit
	) : this(
		bindingReporter = bindingReporter,
		lifecycleDelivery = lifecycleDelivery,
		applyHostEffect = { effect ->
			applyDecision(effect.decision, effect.rendererLossCancellationIdentity)
		}
	)

	private var dispatchInProgress = false
	private val pendingHostEffects =
		linkedMapOf<ReaderPresentationHostEffectIdentity, ReaderPresentationHostEffect>()

	val pendingHostEffectCount: Int
		get() = pendingHostEffects.size

	fun dispatch(
		event: ReaderPresentationEvent,
		onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? = dispatch(
		event = event,
		rendererLossCancellationIdentity = null,
		onEvent = onEvent
	)

	fun dispatch(
		event: ReaderPresentationEvent,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?,
		onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? {
		if (dispatchInProgress || !drainPresentationHostEffects()) return null
		val epoch = bindingReporter.captureEpoch()
		dispatchInProgress = true
		return try {
			val receipt = try {
				onEvent(event)
			} catch (_: Throwable) {
				null
			} ?: return null
			validateAndCommit(
				epoch = epoch,
				event = event,
				receipt = receipt,
				rendererLossCancellationIdentity = rendererLossCancellationIdentity
			)
		} finally {
			dispatchInProgress = false
		}
	}

	fun consumeReturned(
		epoch: Long,
		receipt: ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? {
		if (
			dispatchInProgress ||
				receipt == null ||
				receipt.event is ReaderPresentationEvent.Lifecycle ||
				!drainPresentationHostEffects()
		) {
			return null
		}
		dispatchInProgress = true
		return try {
			validateAndCommit(
				epoch = epoch,
				event = receipt.event,
				receipt = receipt,
				rendererLossCancellationIdentity = null
			)
		} finally {
			dispatchInProgress = false
		}
	}

	private fun validateAndCommit(
		epoch: Long,
		event: ReaderPresentationEvent,
		receipt: ReaderPresentationEventReceipt,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?
	): ReaderPresentationEventReceipt? {
		val reporterAdmission = bindingReporter.classifyReceipt(
			epoch = epoch,
			expectedEvent = event,
			receipt = receipt
		) ?: return null
		val lifecycleAdmission = if (event is ReaderPresentationEvent.Lifecycle) {
			lifecycleDelivery.classifyReceipt(event, receipt) ?: return null
		} else {
			null
		}
		val cancellationIdentity = rendererLossCancellationIdentity.takeIf {
			event is ReaderPresentationEvent.Lifecycle &&
				event.event == ReaderPresentationLifecycleEvent.RendererLost
		}
		commitAuthoritativeModel(
			epoch = epoch,
			event = event,
			reporterAdmission = reporterAdmission,
			lifecycleAdmission = lifecycleAdmission,
			rendererLossCancellationIdentity = cancellationIdentity
		)
		drainPresentationHostEffects()
		return receipt
	}

	private fun commitAuthoritativeModel(
		epoch: Long,
		event: ReaderPresentationEvent,
		reporterAdmission: ReaderPresentationReceiptAdmission,
		lifecycleAdmission: ReaderPresentationLifecycleReceiptAdmission?,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?
	) {
		val receipt = reporterAdmission.receipt
		enqueueHostEffect(
			ReaderPresentationHostEffect(
				identity = ReaderPresentationHostEffectIdentity(epoch, receipt.version),
				event = event,
				decision = readerPresentationDecision(receipt.postState),
				rendererLossCancellationIdentity = rendererLossCancellationIdentity
			)
		)
		bindingReporter.commitReceipt(reporterAdmission)
		lifecycleAdmission?.let(lifecycleDelivery::commitReceipt)
	}

	private fun enqueueHostEffect(effect: ReaderPresentationHostEffect) {
		if (!effect.terminalPriority) {
			pendingHostEffects.entries.removeAll { (_, pending) ->
				!pending.terminalPriority &&
					pending.identity.hostEpoch == effect.identity.hostEpoch &&
					pending.identity.version.eventSequence <
						effect.identity.version.eventSequence
			}
		}
		pendingHostEffects.putIfAbsent(effect.identity, effect)
		while (pendingHostEffects.size > ReaderPresentationHostEffectLimit) {
			val superseded = pendingHostEffects.entries.firstOrNull { (_, pending) ->
				!pending.terminalPriority && pending.identity != effect.identity
			} ?: pendingHostEffects.entries.first()
			pendingHostEffects.remove(superseded.key)
		}
	}

	fun synchronizeComposeModel(
		epoch: Long,
		version: ReaderPresentationReceiptVersion,
		state: ReaderPresentationState,
		shellCoverVisible: Boolean,
		decision: ReaderPresentationDecision
	): Boolean {
		if (
			dispatchInProgress ||
			epoch != bindingReporter.captureEpoch() ||
			!drainPresentationHostEffects()
		) return false
		if (bindingReporter.matchesAuthoritativePresentationVersion(version)) return true
		if (decision != readerPresentationDecision(state)) return false
		if (
			!bindingReporter.consumeComposeModel(
				version = version,
				state = state,
				shellCoverVisible = shellCoverVisible
			)
		) return false
		enqueueHostEffect(
			ReaderPresentationHostEffect(
				identity = ReaderPresentationHostEffectIdentity(epoch, version),
				event = null,
				decision = decision,
				rendererLossCancellationIdentity = null
			)
		)
		return drainPresentationHostEffects()
	}

	fun drainPresentationHostEffects(): Boolean {
		val currentHostEpoch = bindingReporter.captureEpoch()
		pendingHostEffects.entries.removeAll { (identity, _) ->
			identity.hostEpoch != currentHostEpoch
		}
		while (pendingHostEffects.isNotEmpty()) {
			val pending = pendingHostEffects.values.firstOrNull { it.terminalPriority }
				?: pendingHostEffects.values.first()
			try {
				applyHostEffect(pending)
			} catch (_: Throwable) {
				return false
			}
			pendingHostEffects.remove(pending.identity)
			if (pending.terminalPriority) {
				pendingHostEffects.entries.removeAll { (_, queued) ->
					!queued.terminalPriority &&
						queued.identity.hostEpoch == pending.identity.hostEpoch &&
						queued.identity.version.eventSequence <=
							pending.identity.version.eventSequence
				}
			}
		}
		return true
	}
}
