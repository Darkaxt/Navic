package paige.navic.ui.screens.reader

import java.io.File
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
			validateContent = { _, onValidated ->
					onValidated(ReaderPageRelocationContentValidationResult.Accepted)
					ReaderPageRelocationContentValidationHandle.Completed
				},
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
		val hiddenRequests = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { error("No later request expected: $it") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = hiddenRequests::add,
			validateContent = { _, onValidated ->
					onValidated(ReaderPageRelocationContentValidationResult.Accepted)
					ReaderPageRelocationContentValidationHandle.Completed
				},
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
		assertEquals(0, hiddenRequests.size)
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
		assertEquals(listOf(request), hiddenRequests)
		assertFalse(host.redeliverLastVisualState())
		assertEquals(listOf(request), hiddenRequests)
	}

	@Test
	fun timedOutCapacityRecoveryRetriesAfterSameGenerationRepreparation() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(
			queue = queue,
			gestureId = 106L,
			rasterGeneration = 4L,
			textureGeneration = 2L
		)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No later request expected: $it") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
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

		host.completeVisualState()
		assertEquals(1, host.visualStateCount())
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, validations.size)
		validations.single().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)
		assertEquals(
			ReaderWebViewVisualHandoffFailure.Invalidated,
			recoveries.last()
		)
		assertEquals(request, queue.head())
		assertTrue(hidden.isEmpty())

		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					request.foliateSessionId,
					request.destinationOrdinal,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
		)
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(2, validations.size)
		validations.last().invoke(ReaderPageRelocationContentValidationResult.Accepted)

		assertNull(queue.head())
		assertEquals(listOf(request), completed)
		assertEquals(listOf(request), hidden)
		validations.first().invoke(ReaderPageRelocationContentValidationResult.Accepted)
		assertEquals(listOf(request), completed)
		assertEquals(listOf(request), hidden)
	}

	@Test
	fun sameGenerationRepreparationDuringValidationRetriesAfterInvalidation() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 107L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, _ -> },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, validations.size)

		val reprepared = ReaderPageRelocationVisualRetryEvent.Reprepared(
			request.foliateSessionId,
			request.destinationOrdinal,
			request.rasterGeneration,
			request.textureGeneration
		)
		repeat(2) {
			assertFalse(coordinator.onRetryEvent(reprepared))
		}
		validations.single().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)

		assertEquals(1, host.visualStateCount())
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(2, validations.size)
		validations.last().invoke(ReaderPageRelocationContentValidationResult.Accepted)
		assertNull(queue.head())
		assertEquals(listOf(request), hidden)
	}

	@Test
	fun sameGenerationRepreparationDuringAwaitingRetriesAfterInvalidation() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 108L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(request))

		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					request.foliateSessionId,
					request.destinationOrdinal,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
		)
		host.completeVisualState()
		host.runNextFrame()
		validations.single().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)

		assertEquals(1, host.visualStateCount())
	}

	@Test
	fun successfulValidationDiscardsRetainedSameGenerationEvidence() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 109L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		lateinit var validation:
			(ReaderPageRelocationContentValidationResult) -> Unit
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validation = onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					request.foliateSessionId,
					request.destinationOrdinal,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
		)

		validation(ReaderPageRelocationContentValidationResult.Accepted)

		assertNull(queue.head())
		assertEquals(listOf(request), hidden)
		assertEquals(0, host.visualStateCount())
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					request.foliateSessionId,
					request.destinationOrdinal,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
		)
	}

	@Test
	fun staleSameGenerationRepreparationIsRejected() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 110L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(request)
		lateinit var validation:
			(ReaderPageRelocationContentValidationResult) -> Unit
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = {},
			validateContent = { _, onValidated ->
				validation = onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(
			rasterGeneration = request.rasterGeneration + 1L,
			textureGeneration = request.textureGeneration + 1L
		)

		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					request.foliateSessionId,
					request.destinationOrdinal,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
		)
		state = visualStateFor(request)
		validation(ReaderPageRelocationContentValidationResult.Invalidated)

		assertEquals(listOf(ReaderWebViewVisualHandoffFailure.Invalidated), recoveries)
		assertEquals(request, queue.head())
		assertEquals(0, host.visualStateCount())
	}

	@Test
	fun replacementCancelsOriginalCallbackCapacityRetryAuthority() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		assertTrue(host.transferNextVisualStateToQa())
		host.runTimeout()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		host.completeVisualState()
		assertEquals(1, coordinator.pendingCapacityRetryEdgeCount())

		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val replacement = dispatched.single()
		assertEquals(0, coordinator.pendingCapacityRetryEdgeCount())
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()

		assertNull(queue.head())
		assertEquals(0, coordinator.pendingCapacityRetryEdgeCount())
		assertFalse(host.redeliverLastVisualState())
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
			hideSurface = { hideCount += 1 },
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)

		assertTrue(coordinator.onAcknowledged(first))
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(listOf(second), dispatched)
		assertEquals(second, queue.head())
		assertEquals(0, hideCount)
	}

	@Test
	fun productionCoordinatorRejectsReadyResultAfterGenerationDrift() {
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
			hideSurface = { hideCount += 1 },
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		state = state.copy(textureGeneration = request.textureGeneration + 1L)

		host.runNextFrame()

		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.Invalidated),
			recoveries
		)
		assertEquals(request, queue.head())
		assertEquals(0, hideCount)
	}

	@Test
	fun productionCoordinatorHasNoImplicitContentValidationAcceptance() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderWebViewVisualHandoff.android.kt"
		).readText()
		val constructor = source.substringAfter(
			"internal class ReaderPageRelocationVisualHandoffCoordinator("
		).substringBefore(") {")

		assertFalse(constructor.contains("ReaderPageRelocationContentValidationResult.Accepted"))
	}

	@Test
	fun visualCallbackAndFrameDoNotCompleteBeforeContentValidation() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(1, validations.size)
		assertEquals(request, queue.head())
		assertTrue(hidden.isEmpty())
		assertTrue(completed.isEmpty())
	}

	@Test
	fun acceptedContentValidationCompletesAndHidesExactlyOnce() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		validations.single().invoke(ReaderPageRelocationContentValidationResult.Accepted)
		validations.single().invoke(ReaderPageRelocationContentValidationResult.Accepted)

		assertNull(queue.head())
		assertEquals(listOf(request), hidden)
		assertEquals(listOf(request), completed)
	}

	@Test
	fun rejectedContentValidationRetriesTwiceThenFailsClosed() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		var validationAttempts = 0
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validationAttempts += 1
				onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(3, validationAttempts)
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.ContentRejected),
			recoveries
		)
		assertEquals(request, queue.head())
		assertTrue(hidden.isEmpty())
		assertTrue(dispatched.isEmpty())
	}

	@Test
	fun staleContentValidationCallbackCannotCompleteOrHide() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, _ -> },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		coordinator.cancelForQueueInvalidation()
		assertEquals(0, coordinator.pendingCallbackCount())
		validations.single().invoke(ReaderPageRelocationContentValidationResult.Accepted)

		assertEquals(request, queue.head())
		assertTrue(hidden.isEmpty())
		assertTrue(completed.isEmpty())
	}

	@Test
	fun terminalReadyWaitsForContentValidationAcceptance() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val events = mutableListOf<ReaderWebViewVisualHandoffAttemptEvent>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			hideSurface = {},
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink(events::add)
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		assertTrue(
			events.filterIsInstance<ReaderWebViewVisualHandoffAttemptEvent.Terminal>().isEmpty()
		)
		validations.single().invoke(ReaderPageRelocationContentValidationResult.Accepted)
		assertIs<ReaderWebViewVisualHandoffResult.Ready>(
			events.filterIsInstance<ReaderWebViewVisualHandoffAttemptEvent.Terminal>()
				.single()
				.result
		)
	}

	@Test
	fun invalidatedValidationDoesNotConsumeContentRejectionBudget() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		var rejectContent = false
		var validationAttempts = 0
		var invalidatedCallback: ((ReaderPageRelocationContentValidationResult) -> Unit)? = null
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = {},
			validateContent = { _, onValidated ->
				validationAttempts += 1
				if (rejectContent) {
					onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				} else {
					invalidatedCallback = onValidated
				}
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		invalidatedCallback?.invoke(ReaderPageRelocationContentValidationResult.Invalidated)
		assertEquals(listOf(ReaderWebViewVisualHandoffFailure.Invalidated), recoveries)
		assertEquals(0, coordinator.pendingCallbackCount())
		rejectContent = true
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Attached(
					request.foliateSessionId,
					request.destinationOrdinal
				)
			)
		)
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(4, validationAttempts)
		assertEquals(
			listOf(
				ReaderWebViewVisualHandoffFailure.Invalidated,
				ReaderWebViewVisualHandoffFailure.ContentRejected
			),
			recoveries
		)
		assertEquals(request, queue.head())
	}

	@Test
	fun validatorThatNeverCallsBackTimesOutWithBoundedFailure() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		var validationAttempts = 0
		var candidateReleases = 0
		val validationOwners = mutableListOf<
			ReaderPageTurnPresentedCaptureOwnership<Any>
		>()
		val lateCallbacks = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				validationAttempts += 1
				lateCallbacks += onValidated
				ReaderPageTurnPresentedCaptureOwnership<Any> {
					candidateReleases += 1
				}.also { owner ->
					assertTrue(owner.retain(Any()))
					validationOwners += owner
				}
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		repeat(3) { host.runTimeout() }

		assertEquals(3, validationAttempts)
		assertEquals(3, candidateReleases)
		assertEquals(0, validationOwners.sumOf { it.retainedCandidateCount })
		validationOwners.forEachIndexed { index, owner ->
			assertNull(owner.complete())
			lateCallbacks[index](ReaderPageRelocationContentValidationResult.Accepted)
		}
		assertEquals(3, candidateReleases)
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.ContentRejected),
			recoveries
		)
		assertEquals(request, queue.head())
		assertTrue(hidden.isEmpty())
		assertEquals(0, coordinator.pendingCallbackCount())
	}

	@Test
	fun lateValidationCallbackAfterTimeoutIsFenced() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			hideSurface = {},
			validateContent = { _, onValidated ->
				validations += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		host.runTimeout()
		assertEquals(2, validations.size)
		validations[0](ReaderPageRelocationContentValidationResult.Accepted)
		assertEquals(request, queue.head())
		assertTrue(completed.isEmpty())
		validations[1](ReaderPageRelocationContentValidationResult.Accepted)

		assertNull(queue.head())
		assertEquals(listOf(request), completed)
	}

	@Test
	fun exhaustedValidationReplacesAndCompletesOnDifferentGeneration() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val replacements = mutableListOf<
			Pair<ReaderPageRelocationRequest, ReaderPageRelocationRequest>
		>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = hidden::add,
			validateContent = { request, onValidated ->
				onValidated(
					if (request.textureGeneration == original.textureGeneration) {
						ReaderPageRelocationContentValidationResult.ContentRejected
					} else {
						ReaderPageRelocationContentValidationResult.Accepted
					}
				)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add,
			onReplaced = { old, new -> replacements += old to new }
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)

		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val replacement = dispatched.single()
		assertTrue(replacement.token != original.token)
		assertEquals(11L, replacement.rasterGeneration)
		assertEquals(21L, replacement.textureGeneration)
		assertEquals(replacement, queue.head())
		assertEquals(listOf(original to replacement), replacements)
		assertTrue(hidden.isEmpty())
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()

		assertNull(queue.head())
		assertEquals(listOf(replacement), completed)
		assertEquals(listOf(replacement), hidden)
	}

	private data class FaultedReplacementFixture(
		val queue: ReaderPageRelocationQueue,
		val original: ReaderPageRelocationRequest,
		val replacement: ReaderPageRelocationRequest,
		val host: FakeVisualHandoffHost,
		val coordinator: ReaderPageRelocationVisualHandoffCoordinator,
		val events: MutableList<ReaderWebViewVisualHandoffAttemptEvent>,
		val originalAttemptId: Long,
		val updateState: (ReaderPageRelocationRequest) -> Unit
	)

	private fun prepareFaultedReplacement(
		gestureId: Long,
		requestId: String,
		replacementRasterGeneration: Long,
		replacementTextureGeneration: Long
	): FaultedReplacementFixture {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = gestureId)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val events = mutableListOf<ReaderWebViewVisualHandoffAttemptEvent>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { request, onValidated ->
				onValidated(
					if (request.token == original.token) {
						ReaderPageRelocationContentValidationResult.ContentRejected
					} else {
						ReaderPageRelocationContentValidationResult.Accepted
					}
				)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink(events::add)
		)
		assertTrue(coordinator.onAcknowledged(original))
		val originalAttemptId = events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Started
		>().single().handoffAttemptId
		assertTrue(
			coordinator.attachQaFault(
				original.token.value,
				originalAttemptId,
				ReaderPageQaFaultCorrelation(
					requestId = requestId,
					appliedOperation = ReaderPageQaFaultOperationContext(
						relocationToken = original.token.value,
						handoffAttemptId = originalAttemptId
					),
					relation = ReaderPageQaFaultRelation.AppliedOperation
				)
			)
		)
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(
			rasterGeneration = replacementRasterGeneration,
			textureGeneration = replacementTextureGeneration
		)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					replacementRasterGeneration,
					replacementTextureGeneration
				)
			)
		)
		return FaultedReplacementFixture(
			queue = queue,
			original = original,
			replacement = dispatched.single(),
			host = host,
			coordinator = coordinator,
			events = events,
			originalAttemptId = originalAttemptId,
			updateState = { request -> state = visualStateFor(request) }
		)
	}

	private fun acknowledgeReplacement(fixture: FaultedReplacementFixture) {
		val replacement = fixture.replacement
		assertTrue(
			fixture.queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
	}

	@Test
	fun replacementHandoffRetainsOriginalVisualFaultAsRecovery() {
		val fixture = prepareFaultedReplacement(
			gestureId = 2L,
			requestId = "replacement-visual-fault",
			replacementRasterGeneration = 12L,
			replacementTextureGeneration = 22L
		)
		acknowledgeReplacement(fixture)
		val unrelatedRecovery = ReaderPageQaFaultCorrelation(
			requestId = "unrelated-recovery-fault",
			appliedOperation = ReaderPageQaFaultOperationContext(
				relocationToken = "unrelated-token",
				handoffAttemptId = fixture.originalAttemptId
			),
			relation = ReaderPageQaFaultRelation.Recovery
		)
		assertFalse(
			fixture.coordinator.onAcknowledged(
				fixture.replacement,
				unrelatedRecovery
			)
		)
		assertTrue(fixture.coordinator.onAcknowledged(fixture.replacement))
		fixture.host.completeVisualState()
		fixture.host.runNextFrame()

		val replacementTerminal = fixture.events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>().single { it.relocationToken == fixture.replacement.token.value }
		assertIs<ReaderWebViewVisualHandoffResult.Ready>(replacementTerminal.result)
		assertEquals(
			"replacement-visual-fault",
			replacementTerminal.qaFaultCorrelation?.requestId
		)
		assertEquals(
			ReaderPageQaFaultRelation.Recovery,
			replacementTerminal.qaFaultCorrelation?.relation
		)
		assertEquals(
			fixture.original.token.value,
			replacementTerminal.qaFaultCorrelation?.appliedOperation?.relocationToken
		)
		assertEquals(
			fixture.originalAttemptId,
			replacementTerminal.qaFaultCorrelation?.appliedOperation?.handoffAttemptId
		)
	}

	@Test
	fun replacementCanAttachItsOwnVisualFaultAfterInheritedRecovery() {
		val fixture = prepareFaultedReplacement(
			gestureId = 3L,
			requestId = "original-visual-fault",
			replacementRasterGeneration = 13L,
			replacementTextureGeneration = 23L
		)
		acknowledgeReplacement(fixture)
		assertTrue(fixture.coordinator.onAcknowledged(fixture.replacement))
		val replacementAttempt = fixture.events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Started
		>().single { it.relocationToken == fixture.replacement.token.value }
		val replacementCorrelation = ReaderPageQaFaultCorrelation(
			requestId = "replacement-own-visual-fault",
			appliedOperation = ReaderPageQaFaultOperationContext(
				relocationToken = fixture.replacement.token.value,
				handoffAttemptId = replacementAttempt.handoffAttemptId
			),
			relation = ReaderPageQaFaultRelation.AppliedOperation
		)
		assertTrue(
			fixture.coordinator.attachQaFault(
				fixture.replacement.token.value,
				replacementAttempt.handoffAttemptId,
				replacementCorrelation
			)
		)
		fixture.host.completeVisualState()
		fixture.host.runNextFrame()

		val replacementTerminal = fixture.events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>().single { it.relocationToken == fixture.replacement.token.value }
		assertEquals(
			"replacement-own-visual-fault",
			replacementTerminal.qaFaultCorrelation?.requestId
		)
		assertEquals(
			ReaderPageQaFaultRelation.AppliedOperation,
			replacementTerminal.qaFaultCorrelation?.relation
		)
	}

	@Test
	fun queueInvalidationClearsPendingReplacementFaultInheritance() {
		val fixture = prepareFaultedReplacement(
			gestureId = 4L,
			requestId = "invalidated-replacement-fault",
			replacementRasterGeneration = 14L,
			replacementTextureGeneration = 24L
		)
		fixture.coordinator.cancelForQueueInvalidation()
		fixture.queue.cancelAll()
		val fresh = enqueueVisualRequest(
			queue = fixture.queue,
			gestureId = 5L,
			rasterGeneration = 15L,
			textureGeneration = 25L
		)
		acknowledgeForVisualHandoff(fixture.queue, fresh)
		fixture.updateState(fresh)

		assertTrue(fixture.coordinator.onAcknowledged(fresh))
		fixture.host.completeVisualState()
		fixture.host.runNextFrame()
		val terminal = fixture.events.filterIsInstance<
			ReaderWebViewVisualHandoffAttemptEvent.Terminal
		>().single { it.relocationToken == fresh.token.value }
		assertIs<ReaderWebViewVisualHandoffResult.Ready>(terminal.result)
		assertNull(terminal.qaFaultCorrelation)
	}

	@Test
	fun repreparedDuringValidationIsConsumedOnInvalidationWithoutSecondEvent() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var activeRasterGeneration = original.rasterGeneration
		var activeTextureGeneration = original.textureGeneration
		val preparedDeckGenerations = mutableSetOf(activeTextureGeneration)
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		var deckPreparedCount = 0
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = {
				val preparedTextureGeneration = activeTextureGeneration.takeIf {
					it in preparedDeckGenerations
				}
				visualStateFor(original).copy(
					rasterGeneration = preparedTextureGeneration?.let {
						activeRasterGeneration
					},
					textureGeneration = preparedTextureGeneration
				)
			},
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = {},
			validateContent = { request, onValidated ->
				if (request.textureGeneration == original.textureGeneration) {
					validations += onValidated
				} else {
					onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				}
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, validations.size)

		activeRasterGeneration = 11L
		activeTextureGeneration = 21L
		deckPreparedCount += 1
		preparedDeckGenerations += activeTextureGeneration
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					activeRasterGeneration,
					activeTextureGeneration
				)
			)
		)
		validations.single().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)

		val replacement = dispatched.single()
		assertEquals(1, deckPreparedCount)
		assertTrue(recoveries.isEmpty())
		assertEquals(replacement, queue.head())
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()

		assertNull(queue.head())
		assertEquals(1, deckPreparedCount)
	}

	@Test
	fun preparedWhilePausedIsReplayedByProductionResumeExactlyOnce() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var resumed = true
		var activeRasterGeneration = original.rasterGeneration
		var activeTextureGeneration = original.textureGeneration
		val preparedDeckGenerations = mutableSetOf(activeTextureGeneration)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		var deckPreparedCount = 0
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = {
				val preparedTextureGeneration = activeTextureGeneration.takeIf {
					it in preparedDeckGenerations
				}
				visualStateFor(original).copy(
					resumed = resumed,
					rasterGeneration = preparedTextureGeneration?.let {
						activeRasterGeneration
					},
					textureGeneration = preparedTextureGeneration
				)
			},
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { request, onValidated ->
				onValidated(
					if (request.textureGeneration == original.textureGeneration) {
						ReaderPageRelocationContentValidationResult.ContentRejected
					} else {
						ReaderPageRelocationContentValidationResult.Accepted
					}
				)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()

		resumed = false
		activeRasterGeneration = 11L
		activeTextureGeneration = 21L
		fun onDeckPrepared(generationId: Long): Boolean {
			deckPreparedCount += 1
			preparedDeckGenerations += generationId
			return coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					activeRasterGeneration,
					generationId
				)
			)
		}
		assertFalse(onDeckPrepared(activeTextureGeneration))
		assertTrue(dispatched.isEmpty())

		fun onHostResumed(): Boolean {
			resumed = true
			return coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Resumed(
					original.foliateSessionId,
					original.destinationOrdinal
				)
			)
		}
		assertTrue(onHostResumed())
		val replacement = dispatched.single()
		assertEquals(1, deckPreparedCount)
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Attached(
					original.foliateSessionId,
					original.destinationOrdinal
				)
			)
		)
		assertEquals(listOf(replacement), dispatched)
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()

		assertNull(queue.head())
		assertEquals(1, deckPreparedCount)
	}

	@Test
	fun resumedEventCannotAuthorizeUnpreparedReplacementGeneration() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(
			resumed = false,
			rasterGeneration = null,
			textureGeneration = null
		)
		state = state.copy(resumed = true)
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Resumed(
					original.foliateSessionId,
					original.destinationOrdinal
				)
			)
		)
		assertTrue(dispatched.isEmpty())
		assertEquals(original, queue.head())
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)

		val replacement = dispatched.single()
		assertTrue(replacement.token != original.token)
		assertEquals(11L, replacement.rasterGeneration)
		assertEquals(21L, replacement.textureGeneration)
		assertEquals(replacement, queue.head())
	}

	@Test
	fun successiveRejectedGenerationsAreBoundedPerGesture() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = hidden::add,
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val replacement = dispatched.single()
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()

		state = state.copy(rasterGeneration = 12L, textureGeneration = 22L)
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					replacement.foliateSessionId,
					replacement.destinationOrdinal,
					12L,
					22L
				)
			)
		)
		assertEquals(replacement, queue.head())
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.ContentRejected),
			recoveries
		)
		assertEquals(listOf(replacement), dispatched)
		assertTrue(hidden.isEmpty())
	}

	@Test
	fun invalidationsDoNotResetContentRecoveryReplacementBudget() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = {},
			validateContent = { request, onValidated ->
				if (request.token == original.token) {
					onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				} else {
					validations += onValidated
				}
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val replacement = dispatched.single()
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, validations.size)

		state = state.copy(rasterGeneration = 12L, textureGeneration = 22L)
		val secondRepreparation = ReaderPageRelocationVisualRetryEvent.Reprepared(
			replacement.foliateSessionId,
			replacement.destinationOrdinal,
			12L,
			22L
		)
		assertFalse(coordinator.onRetryEvent(secondRepreparation))
		validations.single().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)
		assertFalse(coordinator.onRetryEvent(secondRepreparation))

		assertEquals(listOf(replacement), dispatched)
		assertEquals(replacement, queue.head())
		assertEquals(
			listOf(
				ReaderWebViewVisualHandoffFailure.ContentRejected,
				ReaderWebViewVisualHandoffFailure.Invalidated
			),
			recoveries
		)
	}

	@Test
	fun exhaustedReplacementBudgetRetainsSameGenerationInvalidationRetry() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = {},
			validateContent = { request, onValidated ->
				if (request.token == original.token) {
					onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				} else {
					validations += onValidated
				}
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val replacement = dispatched.single()
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, validations.size)
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					replacement.foliateSessionId,
					replacement.destinationOrdinal,
					replacement.rasterGeneration,
					replacement.textureGeneration
				)
			)
		)

		validations.first().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(2, validations.size)
		validations.last().invoke(
			ReaderPageRelocationContentValidationResult.Accepted
		)

		assertNull(queue.head())
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.ContentRejected),
			recoveries
		)
	}

	@Test
	fun exhaustedBudgetReplaysPausedSameGenerationRetryOnResume() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val validations = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			hideSurface = {},
			validateContent = { request, onValidated ->
				if (request.token == original.token) {
					onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				} else {
					validations += onValidated
				}
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val replacement = dispatched.single()
		assertTrue(
			queue.acknowledge(
				replacement.token.value,
				replacement.destinationOrdinal,
				replacement.foliateSessionId,
				replacement.rasterGeneration,
				replacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(replacement))
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, validations.size)

		state = state.copy(resumed = false)
		validations.first().invoke(
			ReaderPageRelocationContentValidationResult.Invalidated
		)
		assertFalse(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					replacement.foliateSessionId,
					replacement.destinationOrdinal,
					replacement.rasterGeneration,
					replacement.textureGeneration
				)
			)
		)
		state = state.copy(resumed = true)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Resumed(
					replacement.foliateSessionId,
					replacement.destinationOrdinal
				)
			)
		)
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(2, validations.size)
		validations.last().invoke(
			ReaderPageRelocationContentValidationResult.Accepted
		)

		assertNull(queue.head())
		assertEquals(
			listOf(
				ReaderWebViewVisualHandoffFailure.ContentRejected,
				ReaderWebViewVisualHandoffFailure.Invalidated
			),
			recoveries
		)
	}

	@Test
	fun successfulReplacementClearsBudgetForNextGesture() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { request, onValidated ->
				onValidated(
					if (request.gestureId == original.gestureId && request.token != original.token) {
						ReaderPageRelocationContentValidationResult.Accepted
					} else {
						ReaderPageRelocationContentValidationResult.ContentRejected
					}
				)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)
		val firstReplacement = dispatched.single()
		assertTrue(
			queue.acknowledge(
				firstReplacement.token.value,
				firstReplacement.destinationOrdinal,
				firstReplacement.foliateSessionId,
				firstReplacement.rasterGeneration,
				firstReplacement.textureGeneration
			)
		)
		assertTrue(coordinator.onAcknowledged(firstReplacement))
		host.completeVisualState()
		host.runNextFrame()
		assertNull(queue.head())

		val next = enqueueVisualRequest(
			queue = queue,
			gestureId = 2L,
			rasterGeneration = 30L,
			textureGeneration = 40L
		)
		acknowledgeForVisualHandoff(queue, next)
		state = visualStateFor(next)
		assertTrue(coordinator.onAcknowledged(next))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 31L, textureGeneration = 41L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					next.foliateSessionId,
					next.destinationOrdinal,
					31L,
					41L
				)
			)
		)

		assertEquals(2, dispatched.size)
		assertEquals(next.gestureId, dispatched.last().gestureId)
		assertEquals(dispatched.last(), queue.head())
	}

	@Test
	fun queueInvalidationClearsContentRecoveryReplacementBudget() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)
		assertTrue(coordinator.onAcknowledged(original))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 11L, textureGeneration = 21L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					original.foliateSessionId,
					original.destinationOrdinal,
					11L,
					21L
				)
			)
		)

		coordinator.cancelForQueueInvalidation()
		queue.cancelAll()
		val fresh = enqueueVisualRequest(
			queue = queue,
			gestureId = original.gestureId,
			rasterGeneration = 30L,
			textureGeneration = 40L
		)
		acknowledgeForVisualHandoff(queue, fresh)
		state = visualStateFor(fresh)
		assertTrue(coordinator.onAcknowledged(fresh))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(rasterGeneration = 31L, textureGeneration = 41L)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					fresh.foliateSessionId,
					fresh.destinationOrdinal,
					31L,
					41L
				)
			)
		)

		assertEquals(2, dispatched.size)
		assertEquals(fresh.gestureId, dispatched.last().gestureId)
		assertEquals(dispatched.last(), queue.head())
	}

	@Test
	fun exhaustedCoordinatorEmitsActualContentRejectedDiagnosticTerminal() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val diagnostics = mutableListOf<ReaderPageHandoffDiagnosticResult>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, _ -> },
			hideSurface = {},
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			attemptEventSink = ReaderWebViewVisualHandoffAttemptEventSink { event ->
				if (event is ReaderWebViewVisualHandoffAttemptEvent.Terminal) {
					diagnostics += event.result.toDiagnosticResult()
				}
			}
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(listOf(ReaderPageHandoffDiagnosticResult.ContentRejected), diagnostics)
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
		webViewOrdinal = request.destinationOrdinal,
		rasterGeneration = request.rasterGeneration,
		textureGeneration = request.textureGeneration
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
