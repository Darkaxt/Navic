package paige.navic.ui.screens.reader

import kotlin.math.abs
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPageOperationPolicy
import paige.navic.reader.ReaderPagePointerBeginResult
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.readerPageNewPointerDecision

internal enum class ReaderPageHostLifecycleEvent {
	Detached,
	CanvasDisabled,
	WindowHidden,
	ShellCoverShown,
	Destroyed,
	ReaderClosed,
	RendererReplaced,
	ViewportChanged,
	ReaderSettingsChanged,
	ExternalRelocation,
	RasterProfileInvalidated,
	UnsafeContextLost,
	GlFailed
}

internal val readerPageFinalHostLifecycleEvents = setOf(
	ReaderPageHostLifecycleEvent.Detached,
	ReaderPageHostLifecycleEvent.Destroyed,
	ReaderPageHostLifecycleEvent.ReaderClosed
)

internal fun ReaderPageHostLifecycleEvent.cancellationReason():
	ReaderPageLifecycleCancellationReason = when (this) {
	ReaderPageHostLifecycleEvent.Detached ->
		ReaderPageLifecycleCancellationReason.HostDetached
	ReaderPageHostLifecycleEvent.CanvasDisabled,
	ReaderPageHostLifecycleEvent.WindowHidden,
	ReaderPageHostLifecycleEvent.ShellCoverShown ->
		ReaderPageLifecycleCancellationReason.CanvasDisabled
	ReaderPageHostLifecycleEvent.Destroyed ->
		ReaderPageLifecycleCancellationReason.HostDestroyed
	ReaderPageHostLifecycleEvent.ReaderClosed ->
		ReaderPageLifecycleCancellationReason.ReaderExit
	ReaderPageHostLifecycleEvent.RendererReplaced ->
		ReaderPageLifecycleCancellationReason.RendererReplaced
	ReaderPageHostLifecycleEvent.ViewportChanged,
	ReaderPageHostLifecycleEvent.ReaderSettingsChanged,
	ReaderPageHostLifecycleEvent.ExternalRelocation,
	ReaderPageHostLifecycleEvent.RasterProfileInvalidated ->
		ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
	ReaderPageHostLifecycleEvent.UnsafeContextLost ->
		ReaderPageLifecycleCancellationReason.UnsafeContextLoss
	ReaderPageHostLifecycleEvent.GlFailed ->
		ReaderPageLifecycleCancellationReason.GlFailure
}

internal enum class ReaderPageVisualLocationOrigin {
	ExactPageTurn,
	PendingExactPageTurn,
	External,
	StaleAcknowledgement
}

internal data class ReaderNativeTapContinuationIdentity(
	val binding: ReaderPresentationBinding,
	val presentationToken: ReaderPresentationToken?,
	val authorityPolicy: ReaderPageOperationPolicy,
	val localSafetyPolicy: ReaderPageOperationPolicy
)

internal fun readerNativeTapContinuationIdentity(
	decision: ReaderPresentationDecision,
	localSafetyPolicy: ReaderPageOperationPolicy
): ReaderNativeTapContinuationIdentity? {
	val authority = decision.authority as? ReaderPresentationAuthority.SettledNativePage
		?: return null
	val inputPolicy = decision.inputPolicy as? ReaderPresentationInputPolicy.NativePage
		?: return null
	val proof = authority.frame.proof
	if (
		decision.targetBinding != proof.binding ||
		proof.binding.rasterGeneration == null ||
		proof.binding.textureGeneration == null ||
		inputPolicy.policy.newPointer != ReaderPageNewPointerDecision.Accept ||
		localSafetyPolicy.newPointer != ReaderPageNewPointerDecision.Accept
	) {
		return null
	}
	return ReaderNativeTapContinuationIdentity(
		binding = proof.binding,
		presentationToken = proof.transitionToken,
		authorityPolicy = inputPolicy.policy,
		localSafetyPolicy = localSafetyPolicy
	)
}

internal data class ReaderPageContentGestureToken(
	val downTimeMillis: Long,
	val gestureId: Long,
	val x: Float,
	val y: Float,
	val continuationIdentity: ReaderNativeTapContinuationIdentity
)

internal sealed interface ReaderPageHostPointerEvent {
	data class Down(
		val x: Float,
		val y: Float,
		val downTimeMillis: Long
	) : ReaderPageHostPointerEvent
	data class Move(
		val x: Float,
		val y: Float,
		val touchSlop: Float
	) : ReaderPageHostPointerEvent
	data class PositionedUp(
		val x: Float,
		val y: Float,
		val touchSlop: Float,
		val eventTimeMillis: Long = Long.MIN_VALUE
	) : ReaderPageHostPointerEvent
	data object Up : ReaderPageHostPointerEvent
	data object Cancel : ReaderPageHostPointerEvent
	data object SecondaryPointerDown : ReaderPageHostPointerEvent
	data object SecondaryPointerUp : ReaderPageHostPointerEvent
}

internal data class ReaderPageHostPointerDispatchResult(
	val gestureId: Long?,
	val route: ReaderPagePointerRoute
)

internal interface ReaderPageHostCancellationPort {
	fun cancelForPointerInterruption(gestureId: Long)
	fun clearCompletedPointerOwnership(gestureId: Long)

	fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason)
	fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason)
	fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason)
	fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason)
}

private fun ReaderPresentationInputPolicy.keepsNativeCurlStream(): Boolean =
	this is ReaderPresentationInputPolicy.NativePage ||
		this is ReaderPresentationInputPolicy.ClaimedCurl

internal class ReaderPageInputSettlementHostController(
	initialPresentationInputPolicy: ReaderPresentationInputPolicy,
	initialLocalSafetyPolicy: ReaderPageOperationPolicy,
	initialNativeTapContinuationIdentity: ReaderNativeTapContinuationIdentity? = null,
	private val pointerRouter: ReaderPagePointerRouter,
	private val cancellationPort: ReaderPageHostCancellationPort,
	private val chromeToggleTarget: (Float, Float) -> Boolean = { _, _ -> false },
	private val onChromeToggle: () -> Unit = {},
	private val chromeTapTimeoutMillis: Long = 500L,
	private val publishLifecycleCancellation: (
		gestureId: Long,
		reason: ReaderPageLifecycleCancellationReason
	) -> Unit = { _, _ -> }
) {
	private data class PendingChromeTap(
		val downX: Float,
		val downY: Float,
		val downTimeMillis: Long
	)

	private var presentationInputPolicy = initialPresentationInputPolicy
	private var localSafetyPolicy = initialLocalSafetyPolicy
	private var nativeTapContinuationIdentity = initialNativeTapContinuationIdentity
	private var physicalStreamGestureId: Long? = null
	private var chromePhysicalStreamActive = false
	private var pendingChromeTap: PendingChromeTap? = null
	private var pointerAdmissionClosed = false
	private var pointerDeliveryClosed = false
	private var finalDeliveryReason: ReaderPageLifecycleCancellationReason? = null
	private val contentTokenByGestureId =
		linkedMapOf<Long, ReaderPageContentGestureToken>()

	init {
		require(chromeTapTimeoutMillis >= 0L)
	}

	fun updateInputPolicies(
		presentationInputPolicy: ReaderPresentationInputPolicy,
		localSafetyPolicy: ReaderPageOperationPolicy,
		nativeTapContinuationIdentity: ReaderNativeTapContinuationIdentity? = null
	) {
		val presentationAuthorityBecameIncompatible =
			physicalStreamGestureId != null &&
				this.presentationInputPolicy.keepsNativeCurlStream() &&
				!presentationInputPolicy.keepsNativeCurlStream()
		val localSafetyVetoedPendingChrome =
			localSafetyPolicy != this.localSafetyPolicy &&
				(
					!localSafetyPolicy.continueActivePointer ||
						localSafetyPolicy.cancelForReadinessChange
				)
		if (
			presentationInputPolicy != ReaderPresentationInputPolicy.ChromeOnly ||
			localSafetyVetoedPendingChrome
		) {
			pendingChromeTap = null
		}
		if (nativeTapContinuationIdentity != this.nativeTapContinuationIdentity) {
			revokeContentContinuations()
		}
		this.presentationInputPolicy = presentationInputPolicy
		this.localSafetyPolicy = localSafetyPolicy
		this.nativeTapContinuationIdentity = nativeTapContinuationIdentity
		if (presentationAuthorityBecameIncompatible) {
			cancelForLifecycle(
				ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
			)
		}
	}

	fun newPointerDecision(): ReaderPageNewPointerDecision {
		val policyDecision = readerPageNewPointerDecision(
			presentationInputPolicy = presentationInputPolicy,
			localSafetyPolicy = localSafetyPolicy
		)
		return if (
			policyDecision == ReaderPageNewPointerDecision.Accept &&
			presentationInputPolicy is ReaderPresentationInputPolicy.NativePage &&
			nativeTapContinuationIdentity == null
		) {
			ReaderPageNewPointerDecision.Reject(
				ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
			)
		} else {
			policyDecision
		}
	}

	fun dispatchChromeOnlyPointer(event: ReaderPageHostPointerEvent): Boolean = when (event) {
		is ReaderPageHostPointerEvent.Down -> {
			if (
				pointerDeliveryClosed ||
				pointerAdmissionClosed ||
				presentationInputPolicy != ReaderPresentationInputPolicy.ChromeOnly
			) {
				false
			} else {
				check(!chromePhysicalStreamActive) {
					"A ChromeOnly physical pointer stream is already active"
				}
				chromePhysicalStreamActive = true
				pendingChromeTap = PendingChromeTap(
					downX = event.x,
					downY = event.y,
					downTimeMillis = event.downTimeMillis
				)
				true
			}
		}
		is ReaderPageHostPointerEvent.Move -> {
			if (!chromePhysicalStreamActive) {
				false
			} else {
				pendingChromeTap?.let { pending ->
					if (
						abs(event.x - pending.downX) > event.touchSlop ||
						abs(event.y - pending.downY) > event.touchSlop
					) {
						pendingChromeTap = null
					}
				}
				true
			}
		}
		is ReaderPageHostPointerEvent.PositionedUp -> {
			if (!chromePhysicalStreamActive) {
				false
			} else {
				val pending = pendingChromeTap
				pendingChromeTap = null
				chromePhysicalStreamActive = false
				val elapsedMillis = pending?.let { event.eventTimeMillis - it.downTimeMillis }
				if (
					pending != null &&
					presentationInputPolicy == ReaderPresentationInputPolicy.ChromeOnly &&
					abs(event.x - pending.downX) <= event.touchSlop &&
					abs(event.y - pending.downY) <= event.touchSlop &&
					elapsedMillis != null &&
					elapsedMillis in 0L until chromeTapTimeoutMillis &&
					chromeToggleTarget(event.x, event.y)
				) {
					onChromeToggle()
				}
				true
			}
		}
		ReaderPageHostPointerEvent.Up -> finishChromeOnlyPointerStream()
		ReaderPageHostPointerEvent.Cancel -> finishChromeOnlyPointerStream()
		ReaderPageHostPointerEvent.SecondaryPointerDown -> {
			if (!chromePhysicalStreamActive) {
				false
			} else {
				pendingChromeTap = null
				true
			}
		}
		ReaderPageHostPointerEvent.SecondaryPointerUp -> chromePhysicalStreamActive
	}

	private fun finishChromeOnlyPointerStream(): Boolean {
		val consumed = chromePhysicalStreamActive
		pendingChromeTap = null
		chromePhysicalStreamActive = false
		return consumed
	}

	fun dispatchPointer(event: ReaderPageHostPointerEvent): ReaderPageHostPointerDispatchResult {
		if (
			pointerDeliveryClosed ||
			(pointerAdmissionClosed && event is ReaderPageHostPointerEvent.Down)
		) {
			return ReaderPageHostPointerDispatchResult(null, ReaderPagePointerRoute.Ignore)
		}
		return when (event) {
			is ReaderPageHostPointerEvent.Down -> {
				check(physicalStreamGestureId == null) {
					"A physical pointer stream is already active"
				}
				val begin = beginPointer(event.x, event.y)
				physicalStreamGestureId = begin.gestureId
				if (begin.route == ReaderPagePointerRoute.Content) {
					bindContentToken(
						downTimeMillis = event.downTimeMillis,
						gestureId = begin.gestureId,
						x = event.x,
						y = event.y
					)
				}
				ReaderPageHostPointerDispatchResult(begin.gestureId, begin.route)
			}
			is ReaderPageHostPointerEvent.Move -> {
				val route = movePointer(event.x, event.y, event.touchSlop)
				if (route is ReaderPagePointerRoute.ClaimCurl) {
					releaseContentToken(route.gestureId)
				}
				ReaderPageHostPointerDispatchResult(physicalStreamGestureId, route)
			}
			is ReaderPageHostPointerEvent.PositionedUp -> {
				val classification = movePointer(
					event.x,
					event.y,
					event.touchSlop
				)
				if (classification is ReaderPagePointerRoute.ClaimCurl) {
					releaseContentToken(classification.gestureId)
				}
				dispatchPointerUp(classification)
			}
			ReaderPageHostPointerEvent.Up -> dispatchPointerUp()
			ReaderPageHostPointerEvent.Cancel -> interruptPointer(finalStreamEvent = true)
			ReaderPageHostPointerEvent.SecondaryPointerDown ->
				interruptPointer(finalStreamEvent = false)
			ReaderPageHostPointerEvent.SecondaryPointerUp -> {
				val gestureId = physicalStreamGestureId
				ReaderPageHostPointerDispatchResult(
					gestureId,
					gestureId?.let(pointerRouter::secondaryPointerUp)
						?: ReaderPagePointerRoute.Ignore
				)
			}
		}
	}

	private fun beginPointer(x: Float, y: Float): ReaderPagePointerBeginResult =
		pointerRouter.begin(x, y, newPointerDecision())

	private fun movePointer(x: Float, y: Float, touchSlop: Float): ReaderPagePointerRoute =
		pointerRouter.move(x, y, touchSlop)

	private fun dispatchPointerUp(
		classification: ReaderPagePointerRoute? = null
	): ReaderPageHostPointerDispatchResult {
		val gestureId = physicalStreamGestureId
		if (gestureId == null) {
			return ReaderPageHostPointerDispatchResult(
				null,
				ReaderPagePointerRoute.Ignore
			)
		}
		val terminalRoute = endPointer(gestureId)
		val route = if (
			classification is ReaderPagePointerRoute.ClaimCurl &&
			terminalRoute is ReaderPagePointerRoute.Curl
		) {
			classification
		} else {
			terminalRoute
		}
		physicalStreamGestureId = null
		closeDeliveryAfterFinalPhysicalTail()
		return ReaderPageHostPointerDispatchResult(gestureId, route)
	}

	fun claimContentAction(downTimeMillis: Long): ReaderPageHostPointerDispatchResult {
		contentTokenByGestureId.values
			.asSequence()
			.filter { token ->
				token.downTimeMillis == downTimeMillis &&
					token.continuationIdentity == nativeTapContinuationIdentity
			}
			.forEach { token ->
				val route = pointerRouter.claimContentAction(token.gestureId)
				if (route != ReaderPagePointerRoute.Ignore) {
					return ReaderPageHostPointerDispatchResult(token.gestureId, route)
				}
			}
		return ReaderPageHostPointerDispatchResult(null, ReaderPagePointerRoute.Ignore)
	}

	fun takeDelayedTap(downTimeMillis: Long): ReaderPageContentGestureToken? =
		takeDelayedTapMatching { token -> token.downTimeMillis == downTimeMillis }

	fun takeOldestDelayedTap(): ReaderPageContentGestureToken? =
		takeDelayedTapMatching { true }

	private fun takeDelayedTapMatching(
		matches: (ReaderPageContentGestureToken) -> Boolean
	): ReaderPageContentGestureToken? {
		val entry = contentTokenByGestureId.entries.firstOrNull { (gestureId, token) ->
			matches(token) && pointerRouter.isDelayedTapPending(gestureId)
		} ?: return null
		if (
			entry.value.continuationIdentity != nativeTapContinuationIdentity ||
			newPointerDecision() != ReaderPageNewPointerDecision.Accept
		) {
			pointerRouter.cancel(
				entry.key,
				ReaderPageGestureTerminalOutcome.CancelledLifecycle
			)
			contentTokenByGestureId.remove(entry.key)
			return null
		}
		contentTokenByGestureId.remove(entry.key)
		return entry.value
	}

	private fun revokeContentContinuations() {
		contentTokenByGestureId.keys.toList().forEach { gestureId ->
			if (pointerRouter.isDelayedTapPending(gestureId)) {
				pointerRouter.cancel(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledLifecycle
				)
			} else {
				val completed = pointerRouter.complete(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledLifecycle
				)
				if (completed && physicalStreamGestureId == gestureId) {
					cancellationPort.cancelForPointerInterruption(gestureId)
				}
			}
			contentTokenByGestureId.remove(gestureId)
		}
	}

	fun contentGestureTokenCount(): Int = contentTokenByGestureId.size

	private fun bindContentToken(
		downTimeMillis: Long,
		gestureId: Long,
		x: Float,
		y: Float
	) {
		check(gestureId !in contentTokenByGestureId) {
			"Gesture already has a content token: $gestureId"
		}
		val continuationIdentity = checkNotNull(nativeTapContinuationIdentity) {
			"Native content gesture requires exact presentation continuity"
		}
		contentTokenByGestureId[gestureId] = ReaderPageContentGestureToken(
			downTimeMillis = downTimeMillis,
			gestureId = gestureId,
			x = x,
			y = y,
			continuationIdentity = continuationIdentity
		)
	}

	private fun releaseContentToken(gestureId: Long) {
		contentTokenByGestureId.remove(gestureId)
	}

	private fun endPointer(gestureId: Long): ReaderPagePointerRoute =
		pointerRouter.pointerUp(gestureId)

	private fun interruptPointer(finalStreamEvent: Boolean): ReaderPageHostPointerDispatchResult {
		val gestureId = physicalStreamGestureId
			?: return ReaderPageHostPointerDispatchResult(null, ReaderPagePointerRoute.Ignore)
		val route = pointerRouter.interruptPhysicalStream(gestureId, finalStreamEvent)
		if (route is ReaderPagePointerRoute.Terminal) {
			releaseContentToken(gestureId)
			cancellationPort.cancelForPointerInterruption(gestureId)
		}
		if (finalStreamEvent) {
			physicalStreamGestureId = null
			closeDeliveryAfterFinalPhysicalTail()
		}
		return ReaderPageHostPointerDispatchResult(gestureId, route)
	}

	fun complete(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean {
		val completed = pointerRouter.complete(gestureId, outcome)
		if (completed) {
			releaseContentToken(gestureId)
			if (physicalStreamGestureId == gestureId) {
				cancellationPort.clearCompletedPointerOwnership(gestureId)
			}
		}
		return completed
	}

	fun completeDelayedTap(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean {
		val completed = pointerRouter.completeDelayedTap(gestureId, outcome)
		if (completed) releaseContentToken(gestureId)
		return completed
	}

	fun onLifecycleEvent(event: ReaderPageHostLifecycleEvent): List<Long> {
		val reason = event.cancellationReason()
		if (event in readerPageFinalHostLifecycleEvents) {
			pointerAdmissionClosed = true
			finalDeliveryReason = reason
		}
		val cancelled = cancelForLifecycle(reason)
		closeDeliveryAfterFinalPhysicalTail()
		return cancelled
	}

	private fun cancelForLifecycle(reason: ReaderPageLifecycleCancellationReason): List<Long> {
		finishChromeOnlyPointerStream()
		val cancelled = pointerRouter.cancelAll(
			ReaderPageGestureTerminalOutcome.CancelledLifecycle
		)
		cancelled.forEach { gestureId ->
			releaseContentToken(gestureId)
			publishLifecycleCancellation(gestureId, reason)
		}
		cancellationPort.cancelActiveRendererGesture(reason)
		cancellationPort.cancelReadableViewerDragPreview(reason)
		cancellationPort.clearNativeTapState(reason)
		cancellationPort.clearSwipeTouchState(reason)
		return cancelled
	}

	private fun closeDeliveryAfterFinalPhysicalTail() {
		val reason = finalDeliveryReason ?: return
		if (physicalStreamGestureId != null || pointerDeliveryClosed) return
		closePointerDelivery(reason)
	}

	fun abandonPhysicalPointerStream(reason: ReaderPageLifecycleCancellationReason) {
		require(
			reason == ReaderPageLifecycleCancellationReason.HostDetached ||
				reason == ReaderPageLifecycleCancellationReason.HostDestroyed ||
				reason == ReaderPageLifecycleCancellationReason.ReaderExit
		) { "Pointer abandonment is unsafe for $reason" }
		if (pointerDeliveryClosed) return
		closePointerDelivery(reason)
	}

	private fun closePointerDelivery(reason: ReaderPageLifecycleCancellationReason) {
		val admissionWasClosed = pointerAdmissionClosed
		val previousReason = finalDeliveryReason
		pointerAdmissionClosed = true
		finalDeliveryReason = reason
		try {
			pointerRouter.abandonPhysicalPointerStream()
		} catch (failure: Throwable) {
			pointerAdmissionClosed = admissionWasClosed
			finalDeliveryReason = previousReason
			throw failure
		}
		pointerDeliveryClosed = true
		physicalStreamGestureId = null
		finishChromeOnlyPointerStream()
		contentTokenByGestureId.clear()
	}
}
