package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWebViewVisualHandoffTest {
	@Test
	fun handoffCompletesOnlyAfterVisualStateAndNextFrame() {
		val host = FakeVisualHandoffHost(attached = true)
		var result: ReaderWebViewVisualHandoffResult? = null
		ReaderWebViewVisualHandoff(host).await("token-1") { result = it }

		assertNull(result)
		host.completeVisualState()
		assertNull(result)
		host.runNextFrame()
		assertEquals(ReaderWebViewVisualHandoffResult.Ready("token-1"), result)
	}

	@Test
	fun detachedOrTimedOutBarrierReturnsTypedFailure() {
		val detachedHost = FakeVisualHandoffHost(attached = false)
		var detached: ReaderWebViewVisualHandoffResult? = null
		ReaderWebViewVisualHandoff(detachedHost).await("token-1") { detached = it }
		assertEquals(
			ReaderWebViewVisualHandoffResult.Failed(
				"token-1",
				ReaderWebViewVisualHandoffFailure.Detached
			),
			detached
		)

		val timeoutHost = FakeVisualHandoffHost(attached = true)
		val timeoutHandoff = ReaderWebViewVisualHandoff(timeoutHost)
		var timedOut: ReaderWebViewVisualHandoffResult? = null
		timeoutHandoff.await("token-2") { timedOut = it }
		timeoutHost.runTimeout()
		assertEquals(
			ReaderWebViewVisualHandoffResult.Failed(
				"token-2",
				ReaderWebViewVisualHandoffFailure.TimedOut
			),
			timedOut
		)
		assertEquals(1, timeoutHandoff.pendingHostCallbackCount())
		timeoutHost.completeVisualState()
		assertEquals(0, timeoutHandoff.pendingHostCallbackCount())
		assertFalse(timeoutHost.redeliverLastVisualState())
	}

	@Test
	fun timedOutAttemptStaysTerminalAndCapacityRecoveryUsesFreshAttempt() {
		val host = FakeVisualHandoffHost(attached = true)
		val results = mutableListOf<ReaderWebViewVisualHandoffResult>()
		val events = mutableListOf<ReaderWebViewVisualHandoffAttemptEvent>()
		lateinit var handoff: ReaderWebViewVisualHandoff
		handoff = ReaderWebViewVisualHandoff(
			host = host,
			onCapacityRetry = { event ->
				val edge = assertIs<
					ReaderWebViewVisualHandoffRetryEvent.CallbackCapacityAvailable
				>(event)
				handoff.await(edge.token, results::add)
				true
			},
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink(events::add)
		)
		handoff.await("token-1", results::add)
		host.runTimeout()
		assertEquals(1, host.visualStateCount())
		assertEquals(1, handoff.pendingHostCallbackCount())

		handoff.await("token-1", results::add)
		assertEquals(
			ReaderWebViewVisualHandoffFailure.CallbackCapacity,
			assertIs<ReaderWebViewVisualHandoffResult.Failed>(results.last()).reason
		)
		host.completeVisualState()
		assertEquals(1, host.visualStateCount())
		host.completeVisualState()
		host.runNextFrame()

		val terminals = events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>()
		assertEquals(listOf(1L, 2L, 3L), terminals.map { it.handoffAttemptId })
		assertEquals(
			ReaderWebViewVisualHandoffFailure.TimedOut,
			assertIs<ReaderWebViewVisualHandoffResult.Failed>(terminals[0].result).reason
		)
		assertEquals(
			ReaderWebViewVisualHandoffFailure.CallbackCapacity,
			assertIs<ReaderWebViewVisualHandoffResult.Failed>(terminals[1].result).reason
		)
		assertIs<ReaderWebViewVisualHandoffResult.Ready>(terminals[2].result)
		assertEquals(
			listOf(1L),
			events.filterIsInstance<
				ReaderWebViewVisualHandoffAttemptEvent.StalePhysicalCallbackReleased
			>().map { it.handoffAttemptId }
		)
		assertEquals(0, handoff.pendingHostCallbackCount())
	}

	@Test
	fun productionCoordinatorRetainsShieldAndRetriesCapacityForSameHead() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val state = visualStateFor(request)
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		var hideCount = 0
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { error("No later request expected: $it") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = { hideCount += 1 }
		)

		assertTrue(coordinator.onAcknowledged(request))
		assertTrue(host.transferNextVisualStateToQa())
		host.runTimeout()
		assertEquals(
			listOf(
				ReaderWebViewVisualHandoffFailure.TimedOut,
				ReaderWebViewVisualHandoffFailure.CallbackCapacity
			),
			recoveries
		)
		assertEquals(request, queue.head())
		assertEquals(0, hideCount)
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					request.foliateSessionId,
					request.destinationOrdinal,
					request.rasterGeneration + 1L,
					request.textureGeneration
				)
			)
		)

		host.completeVisualState()
		assertEquals(1, host.visualStateCount())
		host.completeVisualState()
		host.runNextFrame()
		assertNull(queue.head())
		assertEquals(1, hideCount)
		assertFalse(host.redeliverLastVisualState())
		assertEquals(1, hideCount)
	}

	@Test
	fun productionCoordinatorCompletesHistoricalHeadThenDispatchesQueuedTurn() {
		val queue = ReaderPageRelocationQueue()
		val first = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, first)
		val second = enqueueVisualRequest(
			queue = queue,
			gestureId = 2L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			rasterGeneration = 11L,
			textureGeneration = 21L
		)
		val host = FakeVisualHandoffHost(attached = true)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		var hideCount = 0
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(first) },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			hideSurface = { hideCount += 1 }
		)

		assertTrue(coordinator.onAcknowledged(first))
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(listOf(second), dispatched)
		assertEquals(second, queue.head())
		assertEquals(0, hideCount)
	}

	@Test
	fun productionCoordinatorRejectsReadyResultAfterSessionDrift() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(request)
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		var hideCount = 0
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = { hideCount += 1 }
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		state = state.copy(foliateSessionId = "session-b")

		host.runNextFrame()

		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.Invalidated),
			recoveries
		)
		assertEquals(request, queue.head())
		assertEquals(0, hideCount)
	}

	@Test
	fun synchronousVisualCallbackCannotLeaveOrphanRegistration() {
		val host = FakeVisualHandoffHost(
			attached = true,
			completeSynchronously = true
		)
		val handoff = ReaderWebViewVisualHandoff(host)
		var result: ReaderWebViewVisualHandoffResult? = null

		handoff.await("token-sync") { result = it }

		assertNull(result)
		assertEquals(0, host.visualStateCount())
		assertEquals(2, handoff.pendingHostCallbackCount())
		host.runNextFrame()
		assertEquals(ReaderWebViewVisualHandoffResult.Ready("token-sync"), result)
		assertEquals(0, handoff.pendingHostCallbackCount())
	}

	private fun enqueueVisualRequest(
		queue: ReaderPageRelocationQueue,
		gestureId: Long,
		sourceOrdinal: Int = 3,
		destinationOrdinal: Int = 4,
		rasterGeneration: Long = 10L,
		textureGeneration: Long = 20L
	): ReaderPageRelocationRequest {
		val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId)
		).reservation
		return assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				reservation = reservation,
				rasterGeneration = rasterGeneration,
				textureGeneration = textureGeneration,
				sourceOrdinal = sourceOrdinal,
				destinationOrdinal = destinationOrdinal,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "session-a"
			)
		).request
	}

	private fun acknowledgeForVisualHandoff(
		queue: ReaderPageRelocationQueue,
		request: ReaderPageRelocationRequest
	) {
		assertEquals(request, queue.commandToDispatch())
		assertTrue(
			queue.acknowledge(
				request.token.value,
				request.destinationOrdinal,
				request.foliateSessionId,
				request.rasterGeneration,
				request.textureGeneration
			)
		)
	}

	private fun visualStateFor(
		request: ReaderPageRelocationRequest
	): ReaderPageRelocationVisualState = ReaderPageRelocationVisualState(
		attached = true,
		resumed = true,
		foliateSessionId = request.foliateSessionId,
		webViewOrdinal = request.destinationOrdinal
	)

	private class FakeVisualHandoffHost(
		private var attached: Boolean,
		private val completeSynchronously: Boolean = false
	) : ReaderWebViewVisualHandoffHost {
		private val visualStates = ArrayDeque<ReaderWebViewVisualDeliveryCell>()
		private var lastDeliveredVisualState: ReaderWebViewVisualDeliveryCell? = null
		private var nextFrame: (() -> Unit)? = null
		private var timeout: (() -> Unit)? = null

		override val isAttachedToWindow: Boolean get() = attached

		override fun synchronizeVisualStateOwner() = Unit

		override fun abandonVisualStateCallbacks() {
			while (visualStates.isNotEmpty()) {
				visualStates.removeFirst().abandonPhysicalOwnership()
			}
		}

		override fun postVisualStateCallback(
			relocationToken: String,
			handoffAttemptId: Long,
			registration: ReaderWebViewVisualDeliveryCell
		) {
			visualStates.addLast(registration)
			if (completeSynchronously) {
				visualStates.removeFirst().deliver()
			}
		}

		override fun postOnAnimation(action: () -> Unit) {
			nextFrame = action
		}

		override fun postDelayed(delayMillis: Long, action: () -> Unit) {
			timeout = action
		}

		override fun removeCallbacks(action: () -> Unit) {
			if (nextFrame === action) nextFrame = null
			if (timeout === action) timeout = null
		}

		fun visualStateCount(): Int = visualStates.size

		fun completeVisualState() {
			val registration = visualStates.removeFirst()
			lastDeliveredVisualState = registration
			registration.deliver()
		}

		fun redeliverLastVisualState(): Boolean =
			requireNotNull(lastDeliveredVisualState).deliver()

		fun transferNextVisualStateToQa(): Boolean =
			visualStates.first().transferToQa()

		fun runNextFrame() = requireNotNull(nextFrame).also {
			nextFrame = null
		}.invoke()

		fun runTimeout() = requireNotNull(timeout).also {
			timeout = null
		}.invoke()
	}
}
