package paige.navic.ui.screens.reader

import android.os.Looper
import android.view.View
import android.webkit.WebView
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationToken
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class ReaderWebViewVisualHandoffFailure {
	Detached,
	TimedOut,
	Invalidated,
	CallbackCapacity,
	ContentRejected,
	PresentationFailed,
	Cancelled
}

internal sealed interface ReaderWebViewVisualHandoffResult {
	val token: String

	data class Ready(
		override val token: String,
		val presentedFrameSequence: Long
	) : ReaderWebViewVisualHandoffResult {
		init {
			require(presentedFrameSequence > 0L)
		}
	}

	data class Failed(
		override val token: String,
		val reason: ReaderWebViewVisualHandoffFailure
	) : ReaderWebViewVisualHandoffResult
}

internal data class ReaderPresentationWebViewVisualHandoffRequest(
	val token: ReaderPresentationToken,
	val binding: ReaderPresentationBinding
)

internal sealed interface ReaderPresentationWebViewVisualHandoffResult {
	val token: ReaderPresentationToken
	val binding: ReaderPresentationBinding

	data class Ready(
		override val token: ReaderPresentationToken,
		override val binding: ReaderPresentationBinding,
		val presentedFrameSequence: Long
	) : ReaderPresentationWebViewVisualHandoffResult {
		init {
			require(presentedFrameSequence > 0L)
		}
	}

	data class Failed(
		override val token: ReaderPresentationToken,
		override val binding: ReaderPresentationBinding,
		val reason: ReaderWebViewVisualHandoffFailure
	) : ReaderPresentationWebViewVisualHandoffResult
}

internal sealed interface ReaderWebViewVisualHandoffRetryEvent {
	data class CallbackCapacityAvailable(
		val token: String,
		val edgeVersion: Long
	) : ReaderWebViewVisualHandoffRetryEvent
}

internal sealed interface ReaderWebViewVisualHandoffAttemptEvent {
	val relocationToken: String
	val handoffAttemptId: Long

	data class Started(
		override val relocationToken: String,
		override val handoffAttemptId: Long
	) : ReaderWebViewVisualHandoffAttemptEvent

	data class Terminal(
		override val relocationToken: String,
		override val handoffAttemptId: Long,
		val result: ReaderWebViewVisualHandoffResult,
		val visualStateCompleted: Boolean = false,
		val nextFrameCompleted: Boolean = false,
		val qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) : ReaderWebViewVisualHandoffAttemptEvent

	data class StalePhysicalCallbackReleased(
		override val relocationToken: String,
		override val handoffAttemptId: Long,
		val qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) : ReaderWebViewVisualHandoffAttemptEvent
}

internal fun interface ReaderWebViewVisualHandoffAttemptEventSink {
	fun emit(event: ReaderWebViewVisualHandoffAttemptEvent)
}

internal interface ReaderWebViewVisualCallbackRegistration {
	val ownershipTransferredToQa: Boolean

	fun returnFromQaToPhysical(): Boolean

	fun detachLogicalDelivery(): Boolean

	fun abandonPhysicalOwnership(): Boolean
}

internal class ReaderWebViewVisualDeliveryCell(
	action: () -> Unit,
	private val onPhysicalOwnershipReleased: () -> Unit,
	private val onPhysicalOwnershipReleaseCompleted: () -> Unit = {}
) : ReaderWebViewVisualCallbackRegistration {
	private val action = AtomicReference<(() -> Unit)?>(action)
	private val transferred = AtomicBoolean()
	private val physicalOwnershipReleased = AtomicBoolean()

	override val ownershipTransferredToQa: Boolean
		get() = transferred.get()

	fun transferToQa(): Boolean =
		!physicalOwnershipReleased.get() &&
			action.get() != null &&
			transferred.compareAndSet(false, true)

	override fun returnFromQaToPhysical(): Boolean =
		!physicalOwnershipReleased.get() &&
			action.get() != null &&
			transferred.compareAndSet(true, false)

	fun deliver(): Boolean = releasePhysicalOwnership(deliver = true)

	override fun abandonPhysicalOwnership(): Boolean =
		if (transferred.get()) false else releasePhysicalOwnership(deliver = false)

	fun abandonQaOwnership(): Boolean {
		if (!transferred.compareAndSet(true, false)) return false
		return releasePhysicalOwnership(deliver = false)
	}

	private fun releasePhysicalOwnership(deliver: Boolean): Boolean {
		if (!physicalOwnershipReleased.compareAndSet(false, true)) return false
		val owned = action.getAndSet(null)
		try {
			onPhysicalOwnershipReleased()
			if (deliver) owned?.invoke()
		} finally {
			onPhysicalOwnershipReleaseCompleted()
		}
		return owned != null
	}

	override fun detachLogicalDelivery(): Boolean {
		if (transferred.get()) return false
		return action.getAndSet(null) != null
	}
}

internal interface ReaderWebViewVisualHandoffHost {
	val isAttachedToWindow: Boolean

	fun synchronizeVisualStateOwner()

	fun abandonVisualStateCallbacks()

	fun postVisualStateCallback(
		relocationToken: String,
		handoffAttemptId: Long,
		registration: ReaderWebViewVisualDeliveryCell
	)

	fun postOnAnimation(action: () -> Unit)

	fun postDelayed(delayMillis: Long, action: () -> Unit)

	fun removeCallbacks(action: () -> Unit)
}

internal class ReaderWebViewVisualHandoff(
	private val host: ReaderWebViewVisualHandoffHost,
	private val timeoutMillis: Long = 2_000L,
	private val onCapacityRetry: (ReaderWebViewVisualHandoffRetryEvent) -> Boolean = { true },
	private val attemptEventSink: ReaderWebViewVisualHandoffAttemptEventSink =
		ReaderWebViewVisualHandoffAttemptEventSink { },
	private val onOwnershipMutated: () -> Unit = {}
) {
	private enum class PendingCallbackKind {
		VisualState,
		NextFrame,
		Timeout
	}

	private data class Active(
		val requestId: Long,
		val token: String,
		val presentationRequest: ReaderPresentationWebViewVisualHandoffRequest?,
		val timeoutToken: Long,
		val visualStateToken: Long,
		val timeoutAction: () -> Unit,
		val onResult: (ReaderWebViewVisualHandoffResult) -> Unit,
		var nextFrameToken: Long? = null,
		var nextFrameAction: (() -> Unit)? = null,
		var visualStateCompleted: Boolean = false,
		var nextFrameCompleted: Boolean = false
	)

	private data class OwnershipState(
		val pendingCallbacks: List<Pair<Long, PendingCallbackKind>>,
		val visualRegistrationTokens: List<Long>,
		val callbackCapacityRetryToken: String?,
		val pendingCapacityEdge:
			ReaderWebViewVisualHandoffRetryEvent.CallbackCapacityAvailable?,
		val closed: Boolean
	)

	val hostCallbackLimit: Int = 2
	val capacityRetryEdgeLimit: Int = 1
	val pendingCallbackLimit: Int = hostCallbackLimit + capacityRetryEdgeLimit

	private var nextRequestId = 1L
	private var nextCallbackToken = 1L
	private var nextPresentedFrameSequence = 1L
	private var nextCapacityEdgeVersion = 1L
	private val pendingCallbacks = linkedMapOf<Long, PendingCallbackKind>()
	private val visualRegistrations =
		linkedMapOf<Long, ReaderWebViewVisualCallbackRegistration>()
	private var active: Active? = null
	private var callbackCapacityRetryToken: String? = null
	private var pendingCapacityEdge:
		ReaderWebViewVisualHandoffRetryEvent.CallbackCapacityAvailable? = null
	private var capacityDeliveryInProgress = false
	private var ownershipMutationDepth = 0
	private var ownershipStateBeforeMutation: OwnershipState? = null
	private var closed = false

	private fun ownershipState(): OwnershipState = OwnershipState(
		pendingCallbacks = pendingCallbacks.entries.map { it.key to it.value },
		visualRegistrationTokens = visualRegistrations.keys.toList(),
		callbackCapacityRetryToken = callbackCapacityRetryToken,
		pendingCapacityEdge = pendingCapacityEdge,
		closed = closed
	)

	private fun beginOwnershipMutation() {
		if (ownershipMutationDepth == 0) {
			ownershipStateBeforeMutation = ownershipState()
		}
		ownershipMutationDepth += 1
	}

	private fun endOwnershipMutation() {
		check(ownershipMutationDepth > 0)
		ownershipMutationDepth -= 1
		if (ownershipMutationDepth != 0) return
		val before = checkNotNull(ownershipStateBeforeMutation)
		ownershipStateBeforeMutation = null
		if (ownershipState() != before) onOwnershipMutated()
	}

	private inline fun <T> ownershipMutation(action: () -> T): T {
		beginOwnershipMutation()
		return try {
			action()
		} finally {
			endOwnershipMutation()
		}
	}

	fun pendingHostCallbackCount(): Int = pendingCallbacks.size

	fun pendingCapacityRetryEdgeCount(): Int = if (pendingCapacityEdge == null) 0 else 1

	fun pendingCallbackCount(): Int =
		pendingHostCallbackCount() + pendingCapacityRetryEdgeCount()

	fun awaitCallbackCapacity(token: String): Boolean = ownershipMutation {
		if (closed || active != null || canReserveInitialCallbacks()) return false
		callbackCapacityRetryToken = token
		return true
	}

	fun applicationOwnedCallbackCount(): Int =
		pendingCallbackCount() - visualRegistrations.values.count {
			it.ownershipTransferredToQa
		}

	private fun canReserveInitialCallbacks(): Boolean =
		hostCallbackLimit - pendingCallbacks.size >= 2

	private fun reserveCallback(kind: PendingCallbackKind): Long {
		check(pendingCallbacks.size < hostCallbackLimit)
		return nextCallbackToken++.also { callbackToken ->
			pendingCallbacks[callbackToken] = kind
		}
	}

	private fun reserveInitialCallbacks(): Pair<Long, Long>? {
		if (!canReserveInitialCallbacks()) return null
		val timeout = reserveCallback(PendingCallbackKind.Timeout)
		val visualState = reserveCallback(PendingCallbackKind.VisualState)
		return timeout to visualState
	}

	fun await(
		token: String,
		onResult: (ReaderWebViewVisualHandoffResult) -> Unit
	): Unit = awaitInternal(token, presentationRequest = null, onResult)

	fun await(
		token: ReaderPresentationToken,
		binding: ReaderPresentationBinding,
		onResult: (ReaderPresentationWebViewVisualHandoffResult) -> Unit
	) {
		val request = ReaderPresentationWebViewVisualHandoffRequest(token, binding)
		awaitInternal(
			token = "presentation-${token.value}",
			presentationRequest = request
		) { result ->
			onResult(
				when (result) {
					is ReaderWebViewVisualHandoffResult.Ready ->
						ReaderPresentationWebViewVisualHandoffResult.Ready(
							token = request.token,
							binding = request.binding,
							presentedFrameSequence = result.presentedFrameSequence
						)
					is ReaderWebViewVisualHandoffResult.Failed ->
						ReaderPresentationWebViewVisualHandoffResult.Failed(
							token = request.token,
							binding = request.binding,
							reason = result.reason
						)
				}
			)
		}
	}

	private fun awaitInternal(
		token: String,
		presentationRequest: ReaderPresentationWebViewVisualHandoffRequest?,
		onResult: (ReaderWebViewVisualHandoffResult) -> Unit
	): Unit = ownershipMutation {
		check(!closed) { "Visual handoff is closed" }
		invalidate()
		cancelPendingCapacityRetryEdge(token)
		val requestId = nextRequestId++
		attemptEventSink.emit(
			ReaderWebViewVisualHandoffAttemptEvent.Started(token, requestId)
		)
		host.synchronizeVisualStateOwner()
		if (!host.isAttachedToWindow) {
			publishTerminal(
				requestId,
				token,
				ReaderWebViewVisualHandoffResult.Failed(
					token,
					ReaderWebViewVisualHandoffFailure.Detached
				),
				onResult
			)
			return
		}
		val reservation = reserveInitialCallbacks()
		if (reservation == null) {
			callbackCapacityRetryToken = token
			publishTerminal(
				requestId,
				token,
				ReaderWebViewVisualHandoffResult.Failed(
					token,
					ReaderWebViewVisualHandoffFailure.CallbackCapacity
				),
				onResult
			)
			return
		}
		val (timeoutToken, visualStateToken) = reservation
		lateinit var timeoutAction: () -> Unit
		timeoutAction = {
			ownershipMutation {
				if (consumeCallback(timeoutToken, PendingCallbackKind.Timeout)) {
					finish(
						requestId,
						token,
						presentationRequest,
						ReaderWebViewVisualHandoffFailure.TimedOut
					)
				}
			}
		}
		val request = Active(
			requestId = requestId,
			token = token,
			presentationRequest = presentationRequest,
			timeoutToken = timeoutToken,
			visualStateToken = visualStateToken,
			timeoutAction = timeoutAction,
			onResult = onResult
		)
		active = request
		host.postDelayed(timeoutMillis, timeoutAction)
		lateinit var registration: ReaderWebViewVisualDeliveryCell
		registration = ReaderWebViewVisualDeliveryCell(
			action = visualState@{
				val current = active?.takeIf {
					it.requestId == requestId &&
						it.token == token &&
						it.presentationRequest == presentationRequest
				} ?: return@visualState
				if (!host.isAttachedToWindow) {
					finish(
						requestId,
						token,
						presentationRequest,
						ReaderWebViewVisualHandoffFailure.Detached
					)
					return@visualState
				}
				current.visualStateCompleted = true
				val nextFrameToken = reserveCallback(PendingCallbackKind.NextFrame)
				lateinit var nextFrameAction: () -> Unit
				nextFrameAction = nextFrame@{
					ownershipMutation {
						if (!consumeCallback(
								nextFrameToken,
								PendingCallbackKind.NextFrame
							)
						) return@ownershipMutation
						val ready = active?.takeIf {
							it.requestId == requestId &&
								it.token == token &&
								it.presentationRequest == presentationRequest
						} ?: return@ownershipMutation
						ready.nextFrameCompleted = true
						removeTimeout(ready)
						active = null
						val result = if (host.isAttachedToWindow) {
							val frameSequence = nextPresentedFrameSequence
							nextPresentedFrameSequence = Math.incrementExact(frameSequence)
							ReaderWebViewVisualHandoffResult.Ready(token, frameSequence)
						} else {
							ReaderWebViewVisualHandoffResult.Failed(
								token,
								ReaderWebViewVisualHandoffFailure.Detached
							)
						}
						publishTerminal(
							requestId,
							token,
							result,
							ready.onResult,
							ready.visualStateCompleted,
							ready.nextFrameCompleted
						)
					}
				}
				current.nextFrameToken = nextFrameToken
				current.nextFrameAction = nextFrameAction
				host.postOnAnimation(nextFrameAction)
			},
			onPhysicalOwnershipReleased = {
				beginOwnershipMutation()
				val wasLogicallyActive = active?.let {
					it.requestId == requestId &&
						it.token == token &&
						it.presentationRequest == presentationRequest
				} == true
				if (visualRegistrations.remove(visualStateToken) === registration) {
					check(
						consumeCallback(
							visualStateToken,
							PendingCallbackKind.VisualState
						)
					)
				}
				if (!wasLogicallyActive) {
					attemptEventSink.emit(
						ReaderWebViewVisualHandoffAttemptEvent
							.StalePhysicalCallbackReleased(token, requestId)
					)
				}
			},
			onPhysicalOwnershipReleaseCompleted = ::endOwnershipMutation
		)
		check(visualRegistrations.put(visualStateToken, registration) == null)
		try {
			host.postVisualStateCallback(token, requestId, registration)
		} catch (failure: Throwable) {
			registration.abandonPhysicalOwnership()
			finish(
				requestId,
				token,
				presentationRequest,
				ReaderWebViewVisualHandoffFailure.Invalidated
			)
			throw failure
		}
	}

	fun invalidate(): Unit = ownershipMutation {
		val request = active ?: return
		active = null
		removeTimeout(request)
		removeNextFrame(request)
		detachLogicalVisualState(request)
		publishTerminal(
			request.requestId,
			request.token,
			ReaderWebViewVisualHandoffResult.Failed(
				request.token,
				ReaderWebViewVisualHandoffFailure.Invalidated
			),
			request.onResult,
			request.visualStateCompleted,
			request.nextFrameCompleted
		)
	}

	fun cancelPendingCapacityRetryEdge(token: String): Boolean = ownershipMutation {
		var cancelled = false
		if (callbackCapacityRetryToken == token) {
			callbackCapacityRetryToken = null
			cancelled = true
		}
		val pending = pendingCapacityEdge
		if (pending?.token == token) {
			pendingCapacityEdge = null
			cancelled = true
		}
		return cancelled
	}

	fun redeliverPendingCapacityRetryEdge(): Boolean = ownershipMutation {
		val edge = pendingCapacityEdge ?: return false
		if (closed || capacityDeliveryInProgress) return false
		capacityDeliveryInProgress = true
		val accepted = try {
			onCapacityRetry(edge)
		} catch (_: Throwable) {
			false
		} finally {
			capacityDeliveryInProgress = false
		}
		if (accepted && pendingCapacityEdge?.edgeVersion == edge.edgeVersion) {
			pendingCapacityEdge = null
			if (callbackCapacityRetryToken == edge.token) {
				callbackCapacityRetryToken = null
			}
		}
		return accepted
	}

	private fun finish(
		requestId: Long,
		token: String,
		presentationRequest: ReaderPresentationWebViewVisualHandoffRequest?,
		reason: ReaderWebViewVisualHandoffFailure
	) {
		val request = active?.takeIf {
			it.requestId == requestId &&
				it.token == token &&
				it.presentationRequest == presentationRequest
		} ?: return
		active = null
		removeTimeout(request)
		removeNextFrame(request)
		detachLogicalVisualState(request)
		publishTerminal(
			requestId,
			token,
			ReaderWebViewVisualHandoffResult.Failed(token, reason),
			request.onResult,
			request.visualStateCompleted,
			request.nextFrameCompleted
		)
	}

	private fun publishTerminal(
		requestId: Long,
		token: String,
		result: ReaderWebViewVisualHandoffResult,
		onResult: (ReaderWebViewVisualHandoffResult) -> Unit,
		visualStateCompleted: Boolean = false,
		nextFrameCompleted: Boolean = false
	) {
		attemptEventSink.emit(
			ReaderWebViewVisualHandoffAttemptEvent.Terminal(
				relocationToken = token,
				handoffAttemptId = requestId,
				result = result,
				visualStateCompleted = visualStateCompleted,
				nextFrameCompleted = nextFrameCompleted
			)
		)
		onResult(result)
	}

	private fun removeTimeout(request: Active) {
		host.removeCallbacks(request.timeoutAction)
		consumeCallback(request.timeoutToken, PendingCallbackKind.Timeout)
	}

	private fun removeNextFrame(request: Active) {
		val callbackToken = request.nextFrameToken ?: return
		request.nextFrameAction?.let(host::removeCallbacks)
		consumeCallback(callbackToken, PendingCallbackKind.NextFrame)
		request.nextFrameToken = null
		request.nextFrameAction = null
	}

	private fun detachLogicalVisualState(request: Active) {
		val registration = visualRegistrations[request.visualStateToken] ?: return
		if (!registration.ownershipTransferredToQa) {
			registration.detachLogicalDelivery()
		}
	}

	private fun consumeCallback(
		callbackToken: Long,
		expected: PendingCallbackKind
	): Boolean = ownershipMutation {
		if (pendingCallbacks[callbackToken] != expected) return false
		val couldReserveBefore = canReserveInitialCallbacks()
		pendingCallbacks.remove(callbackToken)
		if (
			!closed &&
			!couldReserveBefore &&
			canReserveInitialCallbacks() &&
			active == null &&
			pendingCapacityEdge == null
		) {
			callbackCapacityRetryToken?.let { token ->
				pendingCapacityEdge =
					ReaderWebViewVisualHandoffRetryEvent.CallbackCapacityAvailable(
						token,
						nextCapacityEdgeVersion++
					)
				redeliverPendingCapacityRetryEdge()
			}
		}
		return true
	}

	fun close(): Unit = ownershipMutation {
		if (closed) return
		closed = true
		callbackCapacityRetryToken = null
		pendingCapacityEdge = null
		val request = active
		active = null
		if (request != null) {
			removeTimeout(request)
			removeNextFrame(request)
			publishTerminal(
				request.requestId,
				request.token,
				ReaderWebViewVisualHandoffResult.Failed(
					request.token,
					ReaderWebViewVisualHandoffFailure.Cancelled
				),
				request.onResult,
				request.visualStateCompleted,
				request.nextFrameCompleted
			)
		}
		visualRegistrations.values.toList().forEach { registration ->
			if (!registration.ownershipTransferredToQa) {
				registration.detachLogicalDelivery()
			}
		}
		host.abandonVisualStateCallbacks()
		check(visualRegistrations.isEmpty())
		check(pendingCallbacks.isEmpty())
	}
}

internal class ReaderWebViewVisualHandoffHostAdapter(
	private val webViewProvider: () -> WebView?,
	private val qaFaultRegistry: ReaderPageQaFaultRegistry? = null,
	private val onQaFaultApplied: (
		relocationToken: String,
		handoffAttemptId: Long,
		correlation: ReaderPageQaFaultCorrelation
	) -> Boolean = { _, _, _ -> false }
) : ReaderWebViewVisualHandoffHost {
	private data class PostedCallback(
		val owner: View,
		val runnable: Runnable
	)

	private data class PostedVisualState(
		val owner: WebView,
		val callback: WebView.VisualStateCallback
	)

	private val postedCallbacks = IdentityHashMap<() -> Unit, PostedCallback>()
	private val postedVisualStates =
		IdentityHashMap<ReaderWebViewVisualDeliveryCell, PostedVisualState>()
	private var visualStateOwner: WebView? = null

	override val isAttachedToWindow: Boolean
		get() = webViewProvider()?.isAttachedToWindow == true

	override fun synchronizeVisualStateOwner() {
		val current = webViewProvider()
		if (current === visualStateOwner) return
		val stale = postedVisualStates.entries
			.filter { (_, posted) -> posted.owner !== current }
			.map { (cell, _) -> cell }
		stale.forEach { cell ->
			postedVisualStates.remove(cell)
			cell.abandonPhysicalOwnership()
		}
		visualStateOwner = current
	}

	override fun abandonVisualStateCallbacks() {
		val abandoned = postedVisualStates.keys.toList()
		postedVisualStates.clear()
		visualStateOwner = null
		abandoned.forEach { cell -> cell.abandonPhysicalOwnership() }
	}

	override fun postVisualStateCallback(
		relocationToken: String,
		handoffAttemptId: Long,
		registration: ReaderWebViewVisualDeliveryCell
	) {
		check(Looper.myLooper() === Looper.getMainLooper()) {
			"Visual-state callback registration is Main-thread owned"
		}
		val applied = qaFaultRegistry?.delayVisualState(
			relocationToken = relocationToken,
			handoffAttemptId = handoffAttemptId,
			registration = registration,
			postPhysical = { ownedRegistration ->
				postPhysicalVisualStateCallback(
					handoffAttemptId,
					ownedRegistration
				)
			}
		)
		if (applied == null) {
			postPhysicalVisualStateCallback(handoffAttemptId, registration)
		} else {
			check(
				onQaFaultApplied(
					relocationToken,
					handoffAttemptId,
					applied.correlation()
				)
			) { "Visual-state QA fault did not match the active handoff" }
		}
	}

	private fun postPhysicalVisualStateCallback(
		handoffAttemptId: Long,
		registration: ReaderWebViewVisualDeliveryCell
	) {
		synchronizeVisualStateOwner()
		val webView = requireNotNull(webViewProvider())
		check(webView === visualStateOwner && webView.isAttachedToWindow)
		lateinit var callback: WebView.VisualStateCallback
		callback = object : WebView.VisualStateCallback() {
			override fun onComplete(requestId: Long) {
				val posted = postedVisualStates[registration] ?: return
				if (posted.callback !== callback) return
				postedVisualStates.remove(registration)
				registration.deliver()
			}
		}
		check(
			postedVisualStates.put(
				registration,
				PostedVisualState(webView, callback)
			) == null
		)
		try {
			webView.postVisualStateCallback(handoffAttemptId, callback)
		} catch (failure: Throwable) {
			if (postedVisualStates.remove(registration)?.callback === callback) {
				registration.abandonPhysicalOwnership()
			}
			throw failure
		}
	}

	override fun postOnAnimation(action: () -> Unit) {
		register(action) { owner, runnable ->
			owner.postOnAnimation(runnable)
			true
		}
	}

	override fun postDelayed(delayMillis: Long, action: () -> Unit) {
		register(action) { owner, runnable -> owner.postDelayed(runnable, delayMillis) }
	}

	private fun register(
		action: () -> Unit,
		post: (View, Runnable) -> Boolean
	) {
		val owner = requireNotNull(webViewProvider())
		lateinit var runnable: Runnable
		runnable = Runnable {
			val registered = postedCallbacks[action]
			if (registered?.runnable !== runnable) return@Runnable
			postedCallbacks.remove(action)
			action()
		}
		check(
			postedCallbacks.put(
				action,
				PostedCallback(owner, runnable)
			) == null
		)
		val accepted = try {
			post(owner, runnable)
		} catch (failure: Throwable) {
			postedCallbacks.remove(action)
			throw failure
		}
		if (!accepted) {
			postedCallbacks.remove(action)
			error("Visual handoff callback post was rejected")
		}
	}

	override fun removeCallbacks(action: () -> Unit) {
		val posted = postedCallbacks.remove(action) ?: return
		posted.owner.removeCallbacks(posted.runnable)
	}
}

internal data class ReaderPageRelocationVisualState(
	val attached: Boolean,
	val resumed: Boolean,
	val foliateSessionId: String?,
	val webViewOrdinal: Int?,
	val rasterGeneration: Long?,
	val textureGeneration: Long?
)

internal enum class ReaderPageRelocationContentValidationResult {
	Accepted,
	ContentRejected,
	Invalidated
}

internal fun interface ReaderPageRelocationContentValidationHandle {
	fun cancel(): Boolean

	companion object {
		val Completed = ReaderPageRelocationContentValidationHandle { false }
	}
}

internal sealed interface ReaderPageRelocationVisualRetryEvent {
	val foliateSessionId: String
	val destinationOrdinal: Int

	data class Attached(
		override val foliateSessionId: String,
		override val destinationOrdinal: Int
	) : ReaderPageRelocationVisualRetryEvent

	data class Resumed(
		override val foliateSessionId: String,
		override val destinationOrdinal: Int
	) : ReaderPageRelocationVisualRetryEvent

	data class Reprepared(
		override val foliateSessionId: String,
		override val destinationOrdinal: Int,
		val rasterGeneration: Long,
		val textureGeneration: Long
	) : ReaderPageRelocationVisualRetryEvent
}

private const val MaximumContentValidationAttempts = 3
private const val MaximumContentRecoveryReplacements = 1
private const val MaximumPresentationRecoveryRequests = 1

private class ReaderPageRelocationContentValidationHandleCell {
	private val lock = Any()
	private var cancelled = false
	private var handle: ReaderPageRelocationContentValidationHandle? = null

	fun attach(candidate: ReaderPageRelocationContentValidationHandle) {
		val cancelImmediately = synchronized(lock) {
			if (cancelled) {
				true
			} else {
				check(handle == null)
				handle = candidate
				false
			}
		}
		if (cancelImmediately) candidate.cancel()
	}

	fun cancel(): Boolean {
		val owned = synchronized(lock) {
			if (cancelled) return false
			cancelled = true
			handle.also { handle = null }
		}
		owned?.cancel()
		return true
	}
}

internal class ReaderPageRelocationVisualHandoffCoordinator(
	private val queue: ReaderPageRelocationQueue,
	private val host: ReaderWebViewVisualHandoffHost,
	private val currentState: () -> ReaderPageRelocationVisualState,
	private val dispatch: (ReaderPageRelocationRequest) -> Unit,
	private val publishRecovery: (
		ReaderPageRelocationRequest,
		ReaderWebViewVisualHandoffFailure
	) -> Unit,
	finalizePresentation: (
		ReaderPageRelocationRequest,
		(Boolean) -> Unit
	) -> Unit,
	private val validateContent: (
		ReaderPageRelocationRequest,
		(ReaderPageRelocationContentValidationResult) -> Unit
	) -> ReaderPageRelocationContentValidationHandle,
	private val canRecover: () -> Boolean = { true },
	timeoutMillis: Long = 2_000L,
	private val contentValidationTimeoutMillis: Long = timeoutMillis,
	private val onOwnershipMutated: () -> Unit = {},
	private val attemptEventSink: ReaderWebViewVisualHandoffAttemptEventSink =
		ReaderWebViewVisualHandoffAttemptEventSink { },
	private val onAwaiting: (ReaderPageRelocationRequest) -> Unit = {},
	private val onCompleted: (ReaderPageRelocationRequest) -> Unit = {},
	private val onRejectedContentReleased: (ReaderPageRelocationRequest) -> Unit = {},
	private val onReplaced: (
		ReaderPageRelocationRequest,
		ReaderPageRelocationRequest
	) -> Unit = { _, _ -> }
) {
	private enum class Phase {
		Idle,
		Awaiting,
		ValidatingContent,
		FinalizingPresentation,
		Recovering,
		Closed
	}

	private val presentationFinalizer = finalizePresentation

	private data class PendingQaFaultInheritance(
		val replacementToken: String,
		val correlation: ReaderPageQaFaultCorrelation
	) {
		init {
			require(correlation.relation == ReaderPageQaFaultRelation.Recovery)
			require(correlation.appliedOperation.relocationToken != null)
		}
	}

	private var phase = Phase.Idle
	private var head: ReaderPageRelocationRequest? = null
	private var contentValidationFailures = 0
	private var contentValidationEpoch = 0L
	private var presentationFinalizationEpoch = 0L
	private var presentationFinalizationPending = false
	private var presentationRecoveryPending = false
	private var presentationRecoveryRequests = 0
	private var contentValidationExhausted = false
	private var contentRecoveryGestureId: Long? = null
	private var contentRecoveryReplacements = 0
	private var contentValidationTimeoutAction: (() -> Unit)? = null
	private var contentValidationHandleCell:
		ReaderPageRelocationContentValidationHandleCell? = null
	private var retainedRepreparedEvidence:
		ReaderPageRelocationVisualRetryEvent.Reprepared? = null
	private var pendingVisualReadyTerminal:
		ReaderWebViewVisualHandoffAttemptEvent.Terminal? = null
	private var headQaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	private var inheritedHeadQaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	private var pendingQaFaultInheritance: PendingQaFaultInheritance? = null
	private var qaFaultAppliedHandoffAttemptId: Long? = null
	private var currentHandoffAttemptId: Long? = null
	private val correlatedAttemptEventSink =
		ReaderWebViewVisualHandoffAttemptEventSink { event ->
			when (event) {
				is ReaderWebViewVisualHandoffAttemptEvent.Started -> {
					currentHandoffAttemptId = event.handoffAttemptId
					val correlation = headQaFaultCorrelation
					if (
						correlation != null &&
						correlation.relation != ReaderPageQaFaultRelation.Recovery &&
						qaFaultAppliedHandoffAttemptId == null
					) {
						qaFaultAppliedHandoffAttemptId = event.handoffAttemptId
					}
					attemptEventSink.emit(event)
				}
				is ReaderWebViewVisualHandoffAttemptEvent.Terminal -> {
					val correlated = event.copy(
						qaFaultCorrelation = qaFaultCorrelationForAttempt(
							event.relocationToken,
							event.handoffAttemptId
						)
					)
					if (event.result is ReaderWebViewVisualHandoffResult.Ready) {
						check(pendingVisualReadyTerminal == null)
						pendingVisualReadyTerminal = correlated
					} else {
						attemptEventSink.emit(correlated)
					}
				}
				is ReaderWebViewVisualHandoffAttemptEvent.StalePhysicalCallbackReleased ->
					attemptEventSink.emit(
						event.copy(
							qaFaultCorrelation = qaFaultCorrelationForAttempt(
								event.relocationToken,
								event.handoffAttemptId
							)
						)
					)
			}
		}
	private val handoff = ReaderWebViewVisualHandoff(
		host = host,
		timeoutMillis = timeoutMillis,
		onCapacityRetry = ::onCapacityRetry,
		attemptEventSink = correlatedAttemptEventSink,
		onOwnershipMutated = onOwnershipMutated
	)

	fun pendingCallbackCount(): Int =
		handoff.applicationOwnedCallbackCount() +
			(if (contentValidationTimeoutAction != null) 1 else 0) +
			(if (contentValidationHandleCell != null) 1 else 0) +
			(if (presentationFinalizationPending) 1 else 0)

	fun pendingCallbackLimit(): Int = handoff.pendingCallbackLimit + 3

	fun pendingCapacityRetryEdgeCount(): Int =
		handoff.pendingCapacityRetryEdgeCount()

	fun onAcknowledged(
		request: ReaderPageRelocationRequest,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): Boolean {
		if (phase != Phase.Idle || !matchesAcknowledgedHead(request)) return false
		if (
			qaFaultCorrelation != null &&
			qaFaultCorrelation.appliedOperation.relocationToken != request.token.value
		) {
			return false
		}
		val pendingInheritance = pendingQaFaultInheritance
		if (
			pendingInheritance != null &&
			pendingInheritance.replacementToken != request.token.value
		) {
			return false
		}
		val inheritedCorrelation = pendingInheritance?.correlation
		if (contentRecoveryGestureId != request.gestureId) {
			contentRecoveryGestureId = request.gestureId
			contentRecoveryReplacements = 0
		}
		head = request
		contentValidationFailures = 0
		contentValidationExhausted = false
		contentValidationEpoch += 1L
		check(contentValidationTimeoutAction == null)
		check(contentValidationHandleCell == null)
		check(!presentationFinalizationPending)
		check(!presentationRecoveryPending)
		presentationRecoveryRequests = 0
		check(retainedRepreparedEvidence == null)
		check(pendingVisualReadyTerminal == null)
		headQaFaultCorrelation = qaFaultCorrelation
		inheritedHeadQaFaultCorrelation = inheritedCorrelation
		pendingQaFaultInheritance = null
		qaFaultAppliedHandoffAttemptId =
			qaFaultCorrelation?.appliedOperation?.handoffAttemptId
		return begin(request)
	}

	fun attachQaFault(
		relocationToken: String,
		handoffAttemptId: Long,
		correlation: ReaderPageQaFaultCorrelation
	): Boolean {
		val request = head ?: return false
		if (
			phase != Phase.Awaiting ||
			request.token.value != relocationToken ||
			currentHandoffAttemptId != handoffAttemptId ||
			headQaFaultCorrelation != null ||
			correlation.appliedOperation.relocationToken != relocationToken ||
			correlation.appliedOperation.handoffAttemptId != handoffAttemptId
		) {
			return false
		}
		headQaFaultCorrelation = correlation
		qaFaultAppliedHandoffAttemptId = handoffAttemptId
		return true
	}

	fun qaFaultCorrelation(
		relocationToken: String,
		handoffAttemptId: Long? = null
	): ReaderPageQaFaultCorrelation? {
		if (head?.token?.value != relocationToken) return null
		val root = headQaFaultCorrelation
		if (root == null) {
			return inheritedHeadQaFaultCorrelation?.withRelation(
				ReaderPageQaFaultRelation.Recovery
			)
		}
		if (root.appliedOperation.relocationToken != relocationToken) return null
		val attemptId = handoffAttemptId ?: currentHandoffAttemptId
		return attemptId?.let {
			qaFaultCorrelationForAttempt(relocationToken, it)
		} ?: root
	}

	private fun qaFaultCorrelationForAttempt(
		relocationToken: String,
		handoffAttemptId: Long
	): ReaderPageQaFaultCorrelation? {
		if (head?.token?.value != relocationToken) return null
		val root = headQaFaultCorrelation
		if (root == null) {
			return inheritedHeadQaFaultCorrelation?.withRelation(
				ReaderPageQaFaultRelation.Recovery
			)
		}
		if (root.appliedOperation.relocationToken != relocationToken) return null
		if (root.relation == ReaderPageQaFaultRelation.Recovery) {
			return root.withRelation(ReaderPageQaFaultRelation.Recovery)
		}
		val appliedAttempt = qaFaultAppliedHandoffAttemptId ?: return null
		return root.withRelation(
			if (handoffAttemptId == appliedAttempt) {
				ReaderPageQaFaultRelation.AppliedOperation
			} else {
				ReaderPageQaFaultRelation.Recovery
			}
		)
	}

	fun onRetryEvent(event: ReaderPageRelocationVisualRetryEvent): Boolean {
		if (!canRecover()) return false
		val request = head ?: return false
		discardRetainedRepreparedEvidenceIfStale(request)
		if (
			phase == Phase.Awaiting ||
			phase == Phase.ValidatingContent ||
			phase == Phase.FinalizingPresentation
		) {
			(event as? ReaderPageRelocationVisualRetryEvent.Reprepared)?.let {
				retainRepreparedEvidenceIfCurrent(request, it)
			}
			return false
		}
		if (phase != Phase.Recovering) return false
		if (presentationRecoveryPending) {
			if (!event.matches(request) || !currentStateMatches(request)) return false
			retainedRepreparedEvidence = null
			return finalizePresentation(request)
		}
		if (
			!contentValidationExhausted &&
			event.matches(request) &&
			currentStateMatches(request)
		) {
			retainedRepreparedEvidence = null
			return begin(request)
		}
		return when (event) {
			is ReaderPageRelocationVisualRetryEvent.Reprepared -> {
				if (replaceHeadForDifferentGeneration(request, event)) {
					true
				} else {
					val sameGeneration = event.matchesGenerations(request)
					if (
						contentRecoveryReplacementExhausted(request) &&
						!sameGeneration
					) {
						retainedRepreparedEvidence = null
						if (
							repreparedEvidenceMatches(
								request = request,
								reprepared = event,
								state = currentState(),
								requireLifecycleReady = true
							)
						) {
							releaseRejectedContent(request)
						}
					} else {
						retainRepreparedEvidenceIfCurrent(request, event)
					}
					consumeRetainedRepreparedEvidence(request)
				}
			}
			is ReaderPageRelocationVisualRetryEvent.Attached,
			is ReaderPageRelocationVisualRetryEvent.Resumed ->
				replayRetainedRepreparedEvidence(request, event)
		}
	}

	private fun retainRepreparedEvidenceIfCurrent(
		request: ReaderPageRelocationRequest,
		reprepared: ReaderPageRelocationVisualRetryEvent.Reprepared
	) {
		if (
			contentRecoveryReplacementExhausted(request) &&
			!reprepared.matchesGenerations(request)
		) {
			return
		}
		if (
			repreparedEvidenceMatches(
				request = request,
				reprepared = reprepared,
				state = currentState(),
				requireLifecycleReady = false
			)
		) {
			retainedRepreparedEvidence = reprepared
		}
	}

	private fun replayRetainedRepreparedEvidence(
		request: ReaderPageRelocationRequest,
		lifecycleEvent: ReaderPageRelocationVisualRetryEvent
	): Boolean {
		if (
			lifecycleEvent.foliateSessionId != request.foliateSessionId ||
			lifecycleEvent.destinationOrdinal != request.destinationOrdinal
		) {
			return false
		}
		return consumeRetainedRepreparedEvidence(request)
	}

	private fun consumeRetainedRepreparedEvidence(
		request: ReaderPageRelocationRequest
	): Boolean {
		val reprepared = retainedRepreparedEvidence ?: return false
		val state = currentState()
		if (
			!repreparedEvidenceMatches(
				request = request,
				reprepared = reprepared,
				state = state,
				requireLifecycleReady = false
			)
		) {
			retainedRepreparedEvidence = null
			return false
		}
		if (!state.attached || !state.resumed) return false
		val sameGeneration = reprepared.matchesGenerations(request)
		if (sameGeneration && contentValidationExhausted) {
			retainedRepreparedEvidence = null
			releaseRejectedContent(request)
			return false
		}
		retainedRepreparedEvidence = null
		if (presentationRecoveryPending) {
			return sameGeneration && finalizePresentation(request)
		}
		return if (sameGeneration) {
			begin(request)
		} else {
			replaceHeadForDifferentGeneration(request, reprepared)
		}
	}

	private fun discardRetainedRepreparedEvidenceIfStale(
		request: ReaderPageRelocationRequest
	) {
		val reprepared = retainedRepreparedEvidence ?: return
		if (
			!repreparedEvidenceMatches(
				request = request,
				reprepared = reprepared,
				state = currentState(),
				requireLifecycleReady = false
			)
		) {
			retainedRepreparedEvidence = null
		}
	}

	private fun repreparedEvidenceMatches(
		request: ReaderPageRelocationRequest,
		reprepared: ReaderPageRelocationVisualRetryEvent.Reprepared,
		state: ReaderPageRelocationVisualState,
		requireLifecycleReady: Boolean
	): Boolean =
		(!requireLifecycleReady || (state.attached && state.resumed)) &&
			matchesAcknowledgedHead(request) &&
			reprepared.foliateSessionId == request.foliateSessionId &&
			reprepared.destinationOrdinal == request.destinationOrdinal &&
			state.foliateSessionId == request.foliateSessionId &&
			state.webViewOrdinal == request.destinationOrdinal &&
			state.rasterGeneration == reprepared.rasterGeneration &&
			state.textureGeneration == reprepared.textureGeneration

	private fun ReaderPageRelocationVisualRetryEvent.Reprepared.matchesGenerations(
		request: ReaderPageRelocationRequest
	): Boolean =
		rasterGeneration == request.rasterGeneration &&
			textureGeneration == request.textureGeneration

	private fun replaceHeadForDifferentGeneration(
		request: ReaderPageRelocationRequest,
		reprepared: ReaderPageRelocationVisualRetryEvent.Reprepared
	): Boolean {
		if (
			reprepared.matchesGenerations(request) ||
			contentRecoveryReplacementExhausted(request)
		) {
			return false
		}
		if (
			!repreparedEvidenceMatches(
				request = request,
				reprepared = reprepared,
				state = currentState(),
				requireLifecycleReady = true
			)
		) {
			return false
		}
		val replacesRejectedContent = contentValidationExhausted
		val replacement = queue.replaceAcknowledgedHead(
			token = request.token.value,
			foliateSessionId = request.foliateSessionId,
			destinationOrdinal = request.destinationOrdinal,
			expectedRasterGeneration = request.rasterGeneration,
			expectedTextureGeneration = request.textureGeneration,
			replacementRasterGeneration = reprepared.rasterGeneration,
			replacementTextureGeneration = reprepared.textureGeneration
		) ?: return false
		if (replacesRejectedContent) {
			check(contentRecoveryGestureId == request.gestureId)
			contentRecoveryReplacements += 1
		}
		retainedRepreparedEvidence = null
		clearContentValidationAttempt()
		handoff.cancelPendingCapacityRetryEdge(request.token.value)
		check(handoff.pendingCapacityRetryEdgeCount() == 0)
		pendingQaFaultInheritance = qaFaultCorrelation(request.token.value)
			?.withRelation(ReaderPageQaFaultRelation.Recovery)
			?.let { correlation ->
				PendingQaFaultInheritance(
					replacementToken = replacement.token.value,
					correlation = correlation
				)
			}
		onReplaced(request, replacement)
		head = null
		phase = Phase.Idle
		contentValidationFailures = 0
		contentValidationExhausted = false
		presentationRecoveryPending = false
		presentationRecoveryRequests = 0
		contentValidationEpoch += 1L
		clearQaFaultCorrelation(clearPendingInheritance = false)
		val command = queue.commandToDispatch()
		check(command == replacement)
		dispatch(replacement)
		return true
	}

	private fun onCapacityRetry(event: ReaderWebViewVisualHandoffRetryEvent): Boolean {
		if (!canRecover() || contentValidationExhausted) return true
		val request = head ?: return false
		val edge = event as?
			ReaderWebViewVisualHandoffRetryEvent.CallbackCapacityAvailable
			?: return false
		if (
			phase != Phase.Recovering ||
			edge.token != request.token.value ||
			!currentStateMatches(request)
		) {
			return false
		}
		return begin(request)
	}

	private fun begin(request: ReaderPageRelocationRequest): Boolean {
		if (
			phase == Phase.Closed ||
			head != request ||
			!matchesAcknowledgedHead(request)
		) {
			return false
		}
		val firstAttempt = phase == Phase.Idle
		phase = Phase.Awaiting
		if (firstAttempt) onAwaiting(request)
		return try {
			handoff.await(request.token.value, ::onHandoffResult)
			true
		} catch (_: Throwable) {
			if (phase == Phase.Awaiting && head == request) {
				recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
			}
			false
		}
	}

	private fun onHandoffResult(result: ReaderWebViewVisualHandoffResult) {
		val request = head?.takeIf { it.token.value == result.token } ?: return
		if (phase != Phase.Awaiting) return
		when (result) {
			is ReaderWebViewVisualHandoffResult.Ready -> {
				if (
					matchesAcknowledgedHead(request) &&
					currentStateMatches(request)
				) {
					beginContentValidation(request)
				} else {
					recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
				}
			}
			is ReaderWebViewVisualHandoffResult.Failed -> {
				if (result.reason == ReaderWebViewVisualHandoffFailure.Cancelled) {
					check(phase == Phase.Closed) {
						"Visual handoff cancellation is teardown-only"
					}
				} else {
					recover(request, result.reason)
				}
			}
		}
	}

	private fun beginContentValidation(request: ReaderPageRelocationRequest) {
		if (
			head != request ||
			(phase != Phase.Awaiting && phase != Phase.ValidatingContent) ||
			contentValidationExhausted
		) {
			return
		}
		phase = Phase.ValidatingContent
		val validationEpoch = ++contentValidationEpoch
		val handleCell = ReaderPageRelocationContentValidationHandleCell()
		check(contentValidationHandleCell == null)
		contentValidationHandleCell = handleCell
		onOwnershipMutated()
		lateinit var timeoutAction: () -> Unit
		timeoutAction = {
			onContentValidationTimeout(request, validationEpoch, timeoutAction)
		}
		check(contentValidationTimeoutAction == null)
		contentValidationTimeoutAction = timeoutAction
		onOwnershipMutated()
		try {
			host.postDelayed(contentValidationTimeoutMillis, timeoutAction)
		} catch (_: Throwable) {
			clearContentValidationAttempt(
				expectedTimeout = timeoutAction,
				removeTimeoutFromHost = false
			)
			onContentValidated(
				request,
				validationEpoch,
				ReaderPageRelocationContentValidationResult.Invalidated
			)
			return
		}
		try {
			val handle = validateContent(request) { result ->
				onContentValidated(request, validationEpoch, result)
			}
			handleCell.attach(handle)
		} catch (_: Throwable) {
			onContentValidated(
				request,
				validationEpoch,
				ReaderPageRelocationContentValidationResult.Invalidated
			)
		}
	}

	private fun onContentValidated(
		request: ReaderPageRelocationRequest,
		validationEpoch: Long,
		result: ReaderPageRelocationContentValidationResult
	) {
		if (
			phase != Phase.ValidatingContent ||
			head != request ||
			contentValidationEpoch != validationEpoch
		) {
			return
		}
		clearContentValidationAttempt()
		when (result) {
			ReaderPageRelocationContentValidationResult.Accepted -> {
				if (validationIsCurrent(request)) {
					finalizePresentation(request)
				} else {
					recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
				}
			}
			ReaderPageRelocationContentValidationResult.ContentRejected -> {
				if (validationIsCurrent(request)) {
					recordContentValidationFailure(request)
				} else {
					recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
				}
			}
			ReaderPageRelocationContentValidationResult.Invalidated ->
				recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
		}
	}

	private fun onContentValidationTimeout(
		request: ReaderPageRelocationRequest,
		validationEpoch: Long,
		timeoutAction: () -> Unit
	) {
		if (
			phase != Phase.ValidatingContent ||
			head != request ||
			contentValidationEpoch != validationEpoch ||
			contentValidationTimeoutAction !== timeoutAction
		) {
			return
		}
		clearContentValidationAttempt(
			expectedTimeout = timeoutAction,
			removeTimeoutFromHost = false
		)
		contentValidationEpoch += 1L
		if (validationIsCurrent(request)) {
			recordContentValidationFailure(request)
		} else {
			recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
		}
	}

	private fun recordContentValidationFailure(request: ReaderPageRelocationRequest) {
		contentValidationFailures += 1
		if (contentValidationFailures < MaximumContentValidationAttempts) {
			beginContentValidation(request)
			return
		}
		contentValidationExhausted = true
		phase = Phase.Recovering
		publishPendingVisualTerminal(
			request,
			ReaderWebViewVisualHandoffFailure.ContentRejected
		)
		if (contentRecoveryReplacementExhausted(request)) {
			releaseRejectedContent(request)
			return
		}
		publishRecovery(request, ReaderWebViewVisualHandoffFailure.ContentRejected)
	}

	private fun releaseRejectedContent(request: ReaderPageRelocationRequest) {
		check(matchesAcknowledgedHead(request))
		check(queue.completeHandoff(request.token.value))
		phase = Phase.Idle
		retainedRepreparedEvidence = null
		contentValidationEpoch += 1L
		contentValidationFailures = 0
		contentValidationExhausted = false
		presentationRecoveryPending = false
		presentationRecoveryRequests = 0
		clearContentRecoveryLineage()
		head = null
		clearQaFaultCorrelation()
		onRejectedContentReleased(request)
		queue.commandToDispatch()?.let(dispatch)
	}

	private fun contentRecoveryReplacementExhausted(
		request: ReaderPageRelocationRequest
	): Boolean =
		contentRecoveryGestureId == request.gestureId &&
			contentRecoveryReplacements >= MaximumContentRecoveryReplacements

	private fun clearContentRecoveryLineage() {
		contentRecoveryGestureId = null
		contentRecoveryReplacements = 0
	}

	private fun validationIsCurrent(
		request: ReaderPageRelocationRequest
	): Boolean = matchesAcknowledgedHead(request) && currentStateMatches(request)

	private fun clearContentValidationAttempt(
		expectedTimeout: (() -> Unit)? = null,
		removeTimeoutFromHost: Boolean = true
	) {
		if (
			expectedTimeout != null &&
			contentValidationTimeoutAction !== expectedTimeout
		) {
			return
		}
		clearContentValidationTimeout(
			expected = expectedTimeout,
			removeFromHost = removeTimeoutFromHost
		)
		val handleCell = contentValidationHandleCell ?: return
		contentValidationHandleCell = null
		handleCell.cancel()
		onOwnershipMutated()
	}

	private fun clearContentValidationTimeout(
		expected: (() -> Unit)? = null,
		removeFromHost: Boolean = true
	) {
		val action = contentValidationTimeoutAction ?: return
		if (expected != null && action !== expected) return
		contentValidationTimeoutAction = null
		if (removeFromHost) host.removeCallbacks(action)
		onOwnershipMutated()
	}

	private fun finalizePresentation(request: ReaderPageRelocationRequest): Boolean {
		if (
			(
				phase != Phase.ValidatingContent &&
				!(phase == Phase.Recovering && presentationRecoveryPending)
			) ||
			head != request ||
			!validationIsCurrent(request)
		) {
			recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
			return false
		}
		presentationRecoveryPending = false
		phase = Phase.FinalizingPresentation
		val finalizationEpoch = ++presentationFinalizationEpoch
		check(!presentationFinalizationPending)
		presentationFinalizationPending = true
		onOwnershipMutated()
		try {
			presentationFinalizer(request) { exposedFrameCommitted ->
				onPresentationFinalized(
					request,
					finalizationEpoch,
					exposedFrameCommitted
				)
			}
		} catch (_: Throwable) {
			onPresentationFinalized(request, finalizationEpoch, false)
		}
		return true
	}

	private fun onPresentationFinalized(
		request: ReaderPageRelocationRequest,
		finalizationEpoch: Long,
		exposedFrameCommitted: Boolean
	) {
		if (
			phase != Phase.FinalizingPresentation ||
			head != request ||
			presentationFinalizationEpoch != finalizationEpoch ||
			!presentationFinalizationPending
		) {
			return
		}
		presentationFinalizationPending = false
		onOwnershipMutated()
		if (!exposedFrameCommitted || !validationIsCurrent(request)) {
			presentationRecoveryPending = true
			recover(request, ReaderWebViewVisualHandoffFailure.PresentationFailed)
			return
		}
		complete(request)
	}

	private fun clearPresentationFinalization() {
		presentationFinalizationEpoch += 1L
		if (!presentationFinalizationPending) return
		presentationFinalizationPending = false
		onOwnershipMutated()
	}

	private fun complete(request: ReaderPageRelocationRequest) {
		if (
			!matchesAcknowledgedHead(request) ||
			!currentStateMatches(request) ||
			!queue.completeHandoff(request.token.value)
		) {
			recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
			return
		}
		phase = Phase.Idle
		retainedRepreparedEvidence = null
		contentValidationEpoch += 1L
		contentValidationFailures = 0
		contentValidationExhausted = false
		presentationRecoveryPending = false
		presentationRecoveryRequests = 0
		clearContentRecoveryLineage()
		publishPendingVisualTerminal(request)
		onCompleted(request)
		head = null
		clearQaFaultCorrelation()
		val next = queue.commandToDispatch()
		next?.let(dispatch)
	}

	private fun recover(
		request: ReaderPageRelocationRequest,
		reason: ReaderWebViewVisualHandoffFailure
	) {
		if (phase == Phase.Closed || head != request) return
		clearContentValidationAttempt()
		clearPresentationFinalization()
		contentValidationEpoch += 1L
		phase = Phase.Recovering
		publishPendingVisualTerminal(request, reason)
		if (canRecover() && consumeRetainedRepreparedEvidence(request)) return
		if (reason == ReaderWebViewVisualHandoffFailure.PresentationFailed) {
			if (presentationRecoveryRequests >= MaximumPresentationRecoveryRequests) return
			presentationRecoveryRequests += 1
		}
		publishRecovery(request, reason)
		if (!canRecover()) return
		if (
			reason == ReaderWebViewVisualHandoffFailure.TimedOut &&
			currentStateMatches(request)
		) {
			val delayedByQa =
				headQaFaultCorrelation != null &&
				qaFaultAppliedHandoffAttemptId == currentHandoffAttemptId
			if (
				delayedByQa &&
				handoff.awaitCallbackCapacity(request.token.value)
			) {
				return
			}
			begin(request)
		}
	}

	private fun publishPendingVisualTerminal(
		request: ReaderPageRelocationRequest,
		failure: ReaderWebViewVisualHandoffFailure? = null
	) {
		val terminal = pendingVisualReadyTerminal ?: return
		check(terminal.relocationToken == request.token.value)
		pendingVisualReadyTerminal = null
		attemptEventSink.emit(
			if (failure == null) {
				terminal
			} else {
				terminal.copy(
					result = ReaderWebViewVisualHandoffResult.Failed(
						request.token.value,
						failure
					)
				)
			}
		)
	}

	private fun clearQaFaultCorrelation(
		clearPendingInheritance: Boolean = true
	) {
		headQaFaultCorrelation = null
		inheritedHeadQaFaultCorrelation = null
		if (clearPendingInheritance) pendingQaFaultInheritance = null
		qaFaultAppliedHandoffAttemptId = null
		currentHandoffAttemptId = null
	}

	private fun matchesAcknowledgedHead(
		request: ReaderPageRelocationRequest
	): Boolean = queue.matchesAcknowledgedHead(
		token = request.token.value,
		rasterGeneration = request.rasterGeneration,
		textureGeneration = request.textureGeneration,
		foliateSessionId = request.foliateSessionId,
		destinationOrdinal = request.destinationOrdinal
	)

	private fun currentStateMatches(
		request: ReaderPageRelocationRequest
	): Boolean = currentState().let { state ->
		state.attached &&
			state.resumed &&
			state.foliateSessionId == request.foliateSessionId &&
			state.webViewOrdinal == request.destinationOrdinal &&
			state.rasterGeneration == request.rasterGeneration &&
			state.textureGeneration == request.textureGeneration
	}

	private fun ReaderPageRelocationVisualRetryEvent.matches(
		request: ReaderPageRelocationRequest
	): Boolean {
		if (
			foliateSessionId != request.foliateSessionId ||
			destinationOrdinal != request.destinationOrdinal
		) {
			return false
		}
		return when (this) {
			is ReaderPageRelocationVisualRetryEvent.Attached,
			is ReaderPageRelocationVisualRetryEvent.Resumed -> true
			is ReaderPageRelocationVisualRetryEvent.Reprepared ->
				rasterGeneration == request.rasterGeneration &&
					textureGeneration == request.textureGeneration
		}
	}

	fun cancelForQueueInvalidation() {
		val request = head
		if (request == null) {
			clearContentRecoveryLineage()
			clearQaFaultCorrelation()
			return
		}
		clearContentValidationAttempt()
		clearPresentationFinalization()
		publishPendingVisualTerminal(
			request,
			ReaderWebViewVisualHandoffFailure.Invalidated
		)
		head = null
		phase = Phase.Idle
		retainedRepreparedEvidence = null
		contentValidationEpoch += 1L
		contentValidationFailures = 0
		contentValidationExhausted = false
		presentationRecoveryPending = false
		presentationRecoveryRequests = 0
		clearContentRecoveryLineage()
		handoff.cancelPendingCapacityRetryEdge(request.token.value)
		handoff.invalidate()
		clearQaFaultCorrelation()
	}

	fun close() {
		if (phase == Phase.Closed) return
		val request = head
		clearContentValidationAttempt()
		clearPresentationFinalization()
		request?.let {
			publishPendingVisualTerminal(
				it,
				ReaderWebViewVisualHandoffFailure.Cancelled
			)
		}
		head = null
		phase = Phase.Closed
		retainedRepreparedEvidence = null
		contentValidationEpoch += 1L
		contentValidationFailures = 0
		contentValidationExhausted = true
		presentationRecoveryPending = false
		presentationRecoveryRequests = 0
		clearContentRecoveryLineage()
		request?.let { handoff.cancelPendingCapacityRetryEdge(it.token.value) }
		handoff.close()
		clearQaFaultCorrelation()
	}
}
