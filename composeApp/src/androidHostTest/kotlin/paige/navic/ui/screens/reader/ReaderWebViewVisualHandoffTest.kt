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
	fun ownershipObserverReportsOneMutationPerCallbackTransaction() {
		val host = FakeVisualHandoffHost(attached = true)
		var mutations = 0
		val handoff = ReaderWebViewVisualHandoff(
			host = host,
			onOwnershipMutated = { mutations += 1 }
		)

		handoff.await("token-1") {}
		assertEquals(1, mutations)
		assertEquals(2, handoff.pendingCallbackCount())
		host.completeVisualState()
		assertEquals(2, mutations)
		assertEquals(2, handoff.pendingCallbackCount())
		host.runNextFrame()
		assertEquals(3, mutations)
		assertEquals(0, handoff.pendingCallbackCount())
		host.redeliverLastVisualState()
		assertEquals(3, mutations)
		handoff.close()
		assertEquals(4, mutations)
		handoff.close()
		assertEquals(4, mutations)
	}

	@Test
	fun retainedCapacityEdgeMutationIsObservedWithoutDuplicateDelivery() {
		val host = FakeVisualHandoffHost(attached = true)
		var mutations = 0
		val handoff = ReaderWebViewVisualHandoff(
			host = host,
			onCapacityRetry = { false },
			onOwnershipMutated = { mutations += 1 }
		)
		handoff.await("token-1") {}
		host.runTimeout()
		handoff.await("token-2") {}
		val beforeRelease = mutations

		host.completeVisualState()

		assertEquals(beforeRelease + 1, mutations)
		assertEquals(1, handoff.pendingCapacityRetryEdgeCount())
		assertFalse(handoff.redeliverPendingCapacityRetryEdge())
		assertEquals(beforeRelease + 1, mutations)
		assertTrue(handoff.cancelPendingCapacityRetryEdge("token-2"))
		assertEquals(beforeRelease + 2, mutations)
	}

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
	fun attemptEventsCarryExactProgressAndOneTerminalPerAttempt() {
		val host = FakeVisualHandoffHost(attached = true)
		val events = mutableListOf<ReaderWebViewVisualHandoffAttemptEvent>()
		val handoff = ReaderWebViewVisualHandoff(
			host = host,
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink(events::add)
		)

		handoff.await("token-1") {}
		host.completeVisualState()
		host.runNextFrame()

		val started = assertIs<ReaderWebViewVisualHandoffAttemptEvent.Started>(
			events.single { it is ReaderWebViewVisualHandoffAttemptEvent.Started }
		)
		val terminal = assertIs<ReaderWebViewVisualHandoffAttemptEvent.Terminal>(
			events.single { it is ReaderWebViewVisualHandoffAttemptEvent.Terminal }
		)
		assertEquals(started.handoffAttemptId, terminal.handoffAttemptId)
		assertEquals(started.relocationToken, terminal.relocationToken)
		assertTrue(terminal.visualStateCompleted)
		assertTrue(terminal.nextFrameCompleted)
		assertIs<ReaderWebViewVisualHandoffResult.Ready>(terminal.result)
	}

	@Test
	fun closePreservesPartialProgressAndDoesNotPublishASecondTerminal() {
		val host = FakeVisualHandoffHost(attached = true)
		val events = mutableListOf<ReaderWebViewVisualHandoffAttemptEvent>()
		val handoff = ReaderWebViewVisualHandoff(
			host = host,
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink(events::add)
		)
		handoff.await("token-1") {}
		host.completeVisualState()

		handoff.close()
		handoff.close()

		val terminal = events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>().single()
		assertTrue(terminal.visualStateCompleted)
		assertFalse(terminal.nextFrameCompleted)
		assertEquals(
			ReaderWebViewVisualHandoffFailure.Cancelled,
			assertIs<ReaderWebViewVisualHandoffResult.Failed>(terminal.result).reason
		)
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
	fun timedOutQaAttemptReleasesStalePhysicalCallbackAndRecoversOnFreshAttempt() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 7L)
		acknowledgeForVisualHandoff(queue, request)
		val physicalHost = FakeVisualHandoffHost(attached = true)
		val registry = ReaderPageQaFaultRegistry()
		val events = mutableListOf<ReaderWebViewVisualHandoffAttemptEvent>()
		lateinit var coordinator: ReaderPageRelocationVisualHandoffCoordinator
		val qaHost = object : ReaderWebViewVisualHandoffHost {
			override val isAttachedToWindow: Boolean
				get() = physicalHost.isAttachedToWindow

			override fun synchronizeVisualStateOwner() =
				physicalHost.synchronizeVisualStateOwner()

			override fun abandonVisualStateCallbacks() =
				physicalHost.abandonVisualStateCallbacks()

			override fun postVisualStateCallback(
				relocationToken: String,
				handoffAttemptId: Long,
				registration: ReaderWebViewVisualDeliveryCell
			) {
				val applied = registry.delayVisualState(
					relocationToken = relocationToken,
					handoffAttemptId = handoffAttemptId,
					registration = registration,
					postPhysical = { ownedRegistration ->
						physicalHost.postVisualStateCallback(
							relocationToken,
							handoffAttemptId,
							ownedRegistration
						)
					}
				)
				if (applied == null) {
					physicalHost.postVisualStateCallback(
						relocationToken,
						handoffAttemptId,
						registration
					)
				} else {
					assertTrue(
						coordinator.attachQaFault(
							relocationToken = relocationToken,
							handoffAttemptId = handoffAttemptId,
							correlation = applied.correlation()
						)
					)
				}
			}

			override fun postOnAnimation(action: () -> Unit) =
				physicalHost.postOnAnimation(action)

			override fun postDelayed(delayMillis: Long, action: () -> Unit) =
				physicalHost.postDelayed(delayMillis, action)

			override fun removeCallbacks(action: () -> Unit) =
				physicalHost.removeCallbacks(action)
		}
		coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = qaHost,
			currentState = { visualStateFor(request) },
			dispatch = { error("No later relocation expected: $it") },
			publishRecovery = { _, _ -> },
			hideSurface = {},
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink(events::add)
		)
		assertTrue(
			registry.enqueue(
				"visual-delay",
				ReaderPageQaFault.DelayNextVisualStateCallback
			)
		)

		assertTrue(coordinator.onAcknowledged(request))
		assertEquals(0, physicalHost.visualStateCount())
		assertEquals(1, registry.pendingCallbackCount())
		physicalHost.runTimeout()

		val timedOut = events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>().single()
		assertEquals(1L, timedOut.handoffAttemptId)
		assertEquals(
			ReaderWebViewVisualHandoffFailure.TimedOut,
			assertIs<ReaderWebViewVisualHandoffResult.Failed>(timedOut.result).reason
		)
		assertEquals(
			ReaderPageQaFaultRelation.AppliedOperation,
			timedOut.qaFaultCorrelation?.relation
		)
		assertEquals(
			1L,
			timedOut.qaFaultCorrelation?.appliedOperation?.handoffAttemptId
		)

		assertTrue(registry.releaseVisualState("release-visual"))
		assertEquals(1, physicalHost.visualStateCount())
		physicalHost.completeVisualState()

		val stale = events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.StalePhysicalCallbackReleased
		>().single()
		assertEquals(1L, stale.handoffAttemptId)
		assertEquals(
			ReaderPageQaFaultRelation.AppliedOperation,
			stale.qaFaultCorrelation?.relation
		)
		assertEquals(1, physicalHost.visualStateCount())

		physicalHost.completeVisualState()
		physicalHost.runNextFrame()

		val terminals = events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>()
		assertEquals(listOf(1L, 2L), terminals.map { it.handoffAttemptId })
		val recovered = terminals.single { it.handoffAttemptId == 2L }
		assertIs<ReaderWebViewVisualHandoffResult.Ready>(recovered.result)
		assertEquals(
			ReaderPageQaFaultRelation.Recovery,
			recovered.qaFaultCorrelation?.relation
		)
		assertEquals(
			1L,
			recovered.qaFaultCorrelation?.appliedOperation?.handoffAttemptId
		)
		assertNull(queue.head())
		assertEquals(0, physicalHost.visualStateCount())
		assertEquals(0, registry.pendingCallbackCount())
		assertEquals(0, coordinator.pendingCallbackCount())
		registry.closeAndDrain()
	}

	@Test
	fun productionCoordinatorRetainsShieldAndRetriesCapacityForSameHead() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val state = visualStateFor(request)
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val awaiting = mutableListOf<ReaderPageRelocationRequest>()
		var hideCount = 0
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { error("No later request expected: $it") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = { hideCount += 1 },
			onAwaiting = awaiting::add
		)

		assertTrue(coordinator.onAcknowledged(request))
		assertEquals(listOf(request), awaiting)
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
		assertEquals(listOf(request), awaiting)
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
