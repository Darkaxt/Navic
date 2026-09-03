package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationPublicationIdentity
import paige.navic.reader.ReaderPresentationReceiptVersion

internal const val ReaderPresentationLifecyclePendingEventLimit = 14

internal class ReaderPresentationLifecycleDelivery {
	private var expectedReaderSessionGeneration: Long? = null
	private var expectedPublicationIdentity: ReaderPresentationPublicationIdentity? = null
	private var minimumEventSequence: Long = 0L
	private var acknowledgedVersion: ReaderPresentationReceiptVersion? = null
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

	fun reset(
		version: ReaderPresentationReceiptVersion,
		observedWindowVisible: Boolean?
	) {
		expectedReaderSessionGeneration = version.readerSessionGeneration
		expectedPublicationIdentity = null
		minimumEventSequence = version.eventSequence
		acknowledgedVersion = null
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
		}
		if (!receiptAcknowledges(event, receipt)) return null
		acknowledge(lifecycleEvent, checkNotNull(receipt))
		return receipt
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
		val expectedLifecycle = expectedPostLifecycle(event.event, receipt)
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
		event: ReaderPresentationLifecycleEvent,
		receipt: ReaderPresentationEventReceipt
	): ReaderPresentationLifecycleState = when (event) {
		ReaderPresentationLifecycleEvent.VisibilityLost -> ReaderPresentationLifecycleState.Background
		ReaderPresentationLifecycleEvent.VisibilityRestored -> ReaderPresentationLifecycleState.Foreground
		ReaderPresentationLifecycleEvent.PublicationClosed -> ReaderPresentationLifecycleState.Destroyed
		is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
		is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure,
		ReaderPresentationLifecycleEvent.RendererLost ->
			acknowledgedLifecycle ?: receipt.postState.lifecycle
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
