package paige.navic.ui.screens.reader

import android.view.View
import android.webkit.WebView
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class ReaderWebViewVisualHandoffFailure {
	Detached,
	TimedOut,
	Invalidated,
	CallbackCapacity,
	Cancelled
}

internal sealed interface ReaderWebViewVisualHandoffResult {
	val token: String

	data class Ready(override val token: String) : ReaderWebViewVisualHandoffResult

	data class Failed(
		override val token: String,
		val reason: ReaderWebViewVisualHandoffFailure
	) : ReaderWebViewVisualHandoffResult
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
		val result: ReaderWebViewVisualHandoffResult
	) : ReaderWebViewVisualHandoffAttemptEvent

	data class StalePhysicalCallbackReleased(
		override val relocationToken: String,
		override val handoffAttemptId: Long
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
	private val onPhysicalOwnershipReleased: () -> Unit
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
		releasePhysicalOwnership(deliver = false)

	private fun releasePhysicalOwnership(deliver: Boolean): Boolean {
		if (!physicalOwnershipReleased.compareAndSet(false, true)) return false
		onPhysicalOwnershipReleased()
		val owned = action.getAndSet(null)
		if (deliver) owned?.invoke()
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
		ReaderWebViewVisualHandoffAttemptEventSink { }
) {
	private enum class PendingCallbackKind {
		VisualState,
		NextFrame,
		Timeout
	}

	private data class Active(
		val requestId: Long,
		val token: String,
		val timeoutToken: Long,
		val visualStateToken: Long,
		val timeoutAction: () -> Unit,
		val onResult: (ReaderWebViewVisualHandoffResult) -> Unit,
		var nextFrameToken: Long? = null,
		var nextFrameAction: (() -> Unit)? = null
	)

	val hostCallbackLimit: Int = 2
	val capacityRetryEdgeLimit: Int = 1
	val pendingCallbackLimit: Int = hostCallbackLimit + capacityRetryEdgeLimit

	private var nextRequestId = 1L
	private var nextCallbackToken = 1L
	private var nextCapacityEdgeVersion = 1L
	private val pendingCallbacks = linkedMapOf<Long, PendingCallbackKind>()
	private val visualRegistrations =
		linkedMapOf<Long, ReaderWebViewVisualCallbackRegistration>()
	private var active: Active? = null
	private var callbackCapacityRetryToken: String? = null
	private var pendingCapacityEdge:
		ReaderWebViewVisualHandoffRetryEvent.CallbackCapacityAvailable? = null
	private var capacityDeliveryInProgress = false
	private var closed = false

	fun pendingHostCallbackCount(): Int = pendingCallbacks.size

	fun pendingCapacityRetryEdgeCount(): Int = if (pendingCapacityEdge == null) 0 else 1

	fun pendingCallbackCount(): Int =
		pendingHostCallbackCount() + pendingCapacityRetryEdgeCount()

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
	) {
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
			if (consumeCallback(timeoutToken, PendingCallbackKind.Timeout)) {
				finish(
					requestId,
					token,
					ReaderWebViewVisualHandoffFailure.TimedOut
				)
			}
		}
		val request = Active(
			requestId = requestId,
			token = token,
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
					it.requestId == requestId && it.token == token
				} ?: return@visualState
				if (!host.isAttachedToWindow) {
					finish(
						requestId,
						token,
						ReaderWebViewVisualHandoffFailure.Detached
					)
					return@visualState
				}
				val nextFrameToken = reserveCallback(PendingCallbackKind.NextFrame)
				lateinit var nextFrameAction: () -> Unit
				nextFrameAction = nextFrame@{
					if (!consumeCallback(
							nextFrameToken,
							PendingCallbackKind.NextFrame
						)
					) return@nextFrame
					val ready = active?.takeIf {
						it.requestId == requestId && it.token == token
					} ?: return@nextFrame
					removeTimeout(ready)
					active = null
					val result = if (host.isAttachedToWindow) {
						ReaderWebViewVisualHandoffResult.Ready(token)
					} else {
						ReaderWebViewVisualHandoffResult.Failed(
							token,
							ReaderWebViewVisualHandoffFailure.Detached
						)
					}
					publishTerminal(requestId, token, result, ready.onResult)
				}
				current.nextFrameToken = nextFrameToken
				current.nextFrameAction = nextFrameAction
				host.postOnAnimation(nextFrameAction)
			},
			onPhysicalOwnershipReleased = {
				val wasLogicallyActive = active?.let {
					it.requestId == requestId && it.token == token
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
			}
		)
		check(visualRegistrations.put(visualStateToken, registration) == null)
		try {
			host.postVisualStateCallback(token, requestId, registration)
		} catch (failure: Throwable) {
			registration.abandonPhysicalOwnership()
			finish(
				requestId,
				token,
				ReaderWebViewVisualHandoffFailure.Invalidated
			)
			throw failure
		}
	}

	fun invalidate() {
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
			request.onResult
		)
	}

	fun cancelPendingCapacityRetryEdge(token: String): Boolean {
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

	fun redeliverPendingCapacityRetryEdge(): Boolean {
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
		reason: ReaderWebViewVisualHandoffFailure
	) {
		val request = active?.takeIf {
			it.requestId == requestId && it.token == token
		} ?: return
		active = null
		removeTimeout(request)
		removeNextFrame(request)
		detachLogicalVisualState(request)
		publishTerminal(
			requestId,
			token,
			ReaderWebViewVisualHandoffResult.Failed(token, reason),
			request.onResult
		)
	}

	private fun publishTerminal(
		requestId: Long,
		token: String,
		result: ReaderWebViewVisualHandoffResult,
		onResult: (ReaderWebViewVisualHandoffResult) -> Unit
	) {
		attemptEventSink.emit(
			ReaderWebViewVisualHandoffAttemptEvent.Terminal(
				relocationToken = token,
				handoffAttemptId = requestId,
				result = result
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
	): Boolean {
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

	fun close() {
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
				request.onResult
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
	private val webViewProvider: () -> WebView?
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
	val webViewOrdinal: Int?
)

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

internal class ReaderPageRelocationVisualHandoffCoordinator(
	private val queue: ReaderPageRelocationQueue,
	host: ReaderWebViewVisualHandoffHost,
	private val currentState: () -> ReaderPageRelocationVisualState,
	private val dispatch: (ReaderPageRelocationRequest) -> Unit,
	private val publishRecovery: (
		ReaderPageRelocationRequest,
		ReaderWebViewVisualHandoffFailure
	) -> Unit,
	private val hideSurface: () -> Unit,
	timeoutMillis: Long = 2_000L
) {
	private enum class Phase {
		Idle,
		Awaiting,
		Recovering,
		Closed
	}

	private var phase = Phase.Idle
	private var head: ReaderPageRelocationRequest? = null
	private val handoff = ReaderWebViewVisualHandoff(
		host = host,
		timeoutMillis = timeoutMillis,
		onCapacityRetry = ::onCapacityRetry
	)

	fun onAcknowledged(request: ReaderPageRelocationRequest): Boolean {
		if (phase != Phase.Idle || !matchesAcknowledgedHead(request)) return false
		head = request
		return begin(request)
	}

	fun onRetryEvent(event: ReaderPageRelocationVisualRetryEvent): Boolean {
		val request = head ?: return false
		if (
			phase != Phase.Recovering ||
			!event.matches(request) ||
			!currentStateMatches(request)
		) {
			return false
		}
		return begin(request)
	}

	private fun onCapacityRetry(event: ReaderWebViewVisualHandoffRetryEvent): Boolean {
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
		phase = Phase.Awaiting
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
		if (phase == Phase.Closed) return
		when (result) {
			is ReaderWebViewVisualHandoffResult.Ready -> complete(request)
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

	private fun complete(request: ReaderPageRelocationRequest) {
		if (
			!matchesAcknowledgedHead(request) ||
			!currentStateMatches(request) ||
			!queue.completeHandoff(request.token.value)
		) {
			recover(request, ReaderWebViewVisualHandoffFailure.Invalidated)
			return
		}
		head = null
		phase = Phase.Idle
		val next = queue.commandToDispatch()
		if (next == null) hideSurface() else dispatch(next)
	}

	private fun recover(
		request: ReaderPageRelocationRequest,
		reason: ReaderWebViewVisualHandoffFailure
	) {
		if (phase == Phase.Closed || head != request) return
		phase = Phase.Recovering
		publishRecovery(request, reason)
		if (
			reason == ReaderWebViewVisualHandoffFailure.TimedOut &&
			currentStateMatches(request)
		) {
			begin(request)
		}
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
			state.webViewOrdinal == request.destinationOrdinal
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
		val request = head ?: return
		head = null
		phase = Phase.Idle
		handoff.cancelPendingCapacityRetryEdge(request.token.value)
		handoff.invalidate()
	}

	fun close() {
		if (phase == Phase.Closed) return
		val request = head
		head = null
		phase = Phase.Closed
		request?.let { handoff.cancelPendingCapacityRetryEdge(it.token.value) }
		handoff.close()
	}
}
