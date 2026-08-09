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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(hiddenRequests::add),
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer { hideCount += 1 },
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
		assertEquals(1, hideCount)
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
			finalizePresentation = immediatePresentationFinalizer { hideCount += 1 },
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
	fun acceptedContentWaitsForCommittedWebViewExposure() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val finalizers = mutableListOf<(Boolean) -> Unit>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			finalizePresentation = { _, onFinalized -> finalizers += onFinalized },
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()

		assertEquals(1, finalizers.size)
		assertEquals(request, queue.head())
		assertTrue(completed.isEmpty())

		finalizers.single().invoke(true)
		finalizers.single().invoke(true)

		assertNull(queue.head())
		assertEquals(listOf(request), completed)
	}

	@Test
	fun failedWebViewExposureRetainsQueueHeadAndEntersRecovery() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val finalizers = mutableListOf<(Boolean) -> Unit>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			finalizePresentation = { _, onFinalized -> finalizers += onFinalized },
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = completed::add
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		finalizers.single().invoke(false)

		assertEquals(request, queue.head())
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.PresentationFailed),
			recoveries
		)
		assertTrue(completed.isEmpty())
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Attached(
					request.foliateSessionId,
					request.destinationOrdinal
				)
			)
		)
		assertEquals(2, finalizers.size)
		finalizers.last().invoke(true)
		assertNull(queue.head())
		assertEquals(listOf(request), completed)
	}

	@Test
	fun liveOwnershipSurvivesPresentationRecoveryUntilCurrentExposureCommits() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 2L)
		var passiveAvailableEdges = 0
		val ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableEdges += 1
		}
		val claim = ownership.acquireLive(request.gestureId)
		val exactDispatchGenerations = mutableListOf<
			ReaderForegroundWebViewMutationGeneration
		>()
		val liveDispatch = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, generation ->
				exactDispatchGenerations += generation
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, reason -> error("Unexpected rejection: $reason") }
		)
		assertTrue(liveDispatch.transfer(request, claim))
		assertTrue(liveDispatch.dispatch(request))
		val mutationGeneration = checkNotNull(liveDispatch.mutationGeneration(request))
		assertEquals(listOf(mutationGeneration), exactDispatchGenerations)
		acknowledgeForVisualHandoff(queue, request)

		val host = FakeVisualHandoffHost(attached = true)
		val finalizers = mutableListOf<(Boolean) -> Unit>()
		val recoveryEvents = mutableListOf<ReaderPageRelocationVisualRetryEvent>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { recoveryRequest, reason ->
				assertEquals(ReaderWebViewVisualHandoffFailure.PresentationFailed, reason)
				recoveryEvents += ReaderPageRelocationVisualRetryEvent.Reprepared(
					foliateSessionId = recoveryRequest.foliateSessionId,
					destinationOrdinal = recoveryRequest.destinationOrdinal,
					rasterGeneration = recoveryRequest.rasterGeneration,
					textureGeneration = recoveryRequest.textureGeneration
				)
			},
			finalizePresentation = { finalizingRequest, onFinalized ->
				assertEquals(mutationGeneration, liveDispatch.mutationGeneration(finalizingRequest))
				finalizers += onFinalized
			},
			validateContent = { validatingRequest, onValidated ->
				assertTrue(liveDispatch.isCurrent(validatingRequest, mutationGeneration))
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = { completedRequest ->
				assertTrue(liveDispatch.complete(completedRequest))
				completed += completedRequest
			}
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		assertEquals(1, finalizers.size)
		assertEquals(1, ownership.snapshot().liveClaims)
		assertNull(
			ownership.tryAcquirePassive(sessionId = 91L) {
				error("Passive ownership must stay blocked by the live claim")
			}
		)

		val failedFinalizer = finalizers.single()
		failedFinalizer(false)
		assertEquals(request, queue.head())
		assertEquals(1, recoveryEvents.size)
		assertEquals(1, ownership.snapshot().liveClaims)
		assertNull(
			ownership.tryAcquirePassive(sessionId = 92L) {
				error("Passive ownership must stay blocked during presentation recovery")
			}
		)

		failedFinalizer(true)
		assertEquals(request, queue.head())
		assertEquals(1, ownership.snapshot().liveClaims)
		assertEquals(0, passiveAvailableEdges)
		assertTrue(coordinator.onRetryEvent(recoveryEvents.single()))
		assertEquals(2, finalizers.size)
		assertEquals(mutationGeneration, liveDispatch.mutationGeneration(request))
		assertNull(
			ownership.tryAcquirePassive(sessionId = 93L) {
				error("Passive ownership must stay blocked while retry finalizes")
			}
		)

		val currentFinalizer = finalizers.last()
		currentFinalizer(true)
		currentFinalizer(true)

		assertNull(queue.head())
		assertEquals(listOf(request), completed)
		assertEquals(0, ownership.snapshot().liveClaims)
		assertEquals(1, passiveAvailableEdges)
		val passive = checkNotNull(
			ownership.tryAcquirePassive(sessionId = 94L) {
				error("Released passive lease must not be preempted")
			}
		)
		assertTrue(ownership.releasePassive(passive))
	}

	@Test
	fun differentGenerationPresentationRecoveryCannotReplaceExactLiveOwner() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 3L)
		var passiveAvailableEdges = 0
		val ownership = ReaderForegroundWebViewOwnership {
			passiveAvailableEdges += 1
		}
		val claim = ownership.acquireLive(request.gestureId)
		val liveDispatch = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { true },
			dispatchExact = { _, _ -> ReaderPageRelocationExactDispatchResult.Dispatched },
			onRejected = { _, reason -> error("Unexpected rejection: $reason") }
		)
		assertTrue(liveDispatch.transfer(request, claim))
		assertTrue(liveDispatch.dispatch(request))
		val mutationGeneration = checkNotNull(liveDispatch.mutationGeneration(request))
		acknowledgeForVisualHandoff(queue, request)

		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(request)
		val finalizers = mutableListOf<(Boolean) -> Unit>()
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { replacement ->
				dispatched += replacement
				assertTrue(liveDispatch.dispatch(replacement))
			},
			publishRecovery = { _, reason -> recoveries += reason },
			finalizePresentation = { finalizingRequest, onFinalized ->
				assertEquals(
					mutationGeneration,
					liveDispatch.mutationGeneration(finalizingRequest)
				)
				finalizers += onFinalized
			},
			validateContent = { validatingRequest, onValidated ->
				assertTrue(liveDispatch.isCurrent(validatingRequest, mutationGeneration))
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onCompleted = { completedRequest ->
				assertTrue(liveDispatch.complete(completedRequest))
				completed += completedRequest
			},
			onReplaced = { original, replacement ->
				assertTrue(liveDispatch.replace(original, replacement))
			}
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		val failedFinalizer = finalizers.single()
		state = state.copy(
			rasterGeneration = request.rasterGeneration + 1L,
			textureGeneration = request.textureGeneration + 1L
		)
		val differentGenerationEvent = ReaderPageRelocationVisualRetryEvent.Reprepared(
			foliateSessionId = request.foliateSessionId,
			destinationOrdinal = request.destinationOrdinal,
			rasterGeneration = checkNotNull(state.rasterGeneration),
			textureGeneration = checkNotNull(state.textureGeneration)
		)
		assertFalse(coordinator.onRetryEvent(differentGenerationEvent))
		failedFinalizer(false)
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.PresentationFailed),
			recoveries
		)
		assertFalse(coordinator.onRetryEvent(differentGenerationEvent))
		assertEquals(request, queue.head())
		assertTrue(dispatched.isEmpty())
		assertEquals(mutationGeneration, liveDispatch.mutationGeneration(request))
		assertEquals(1, ownership.snapshot().liveClaims)
		assertNull(
			ownership.tryAcquirePassive(sessionId = 95L) {
				error("Different-generation recovery must retain exact live ownership")
			}
		)
		assertTrue(completed.isEmpty())
		assertEquals(0, passiveAvailableEdges)

		failedFinalizer(true)
		assertEquals(request, queue.head())
		assertEquals(1, ownership.snapshot().liveClaims)
		state = visualStateFor(request)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					foliateSessionId = request.foliateSessionId,
					destinationOrdinal = request.destinationOrdinal,
					rasterGeneration = request.rasterGeneration,
					textureGeneration = request.textureGeneration
				)
			)
		)
		assertEquals(2, finalizers.size)
		assertEquals(mutationGeneration, liveDispatch.mutationGeneration(request))
		assertNull(
			ownership.tryAcquirePassive(sessionId = 96L) {
				error("Exact retry must retain live ownership until exposure commits")
			}
		)

		val currentFinalizer = finalizers.last()
		currentFinalizer(true)
		currentFinalizer(true)

		assertNull(queue.head())
		assertEquals(listOf(request), completed)
		assertEquals(0, ownership.snapshot().liveClaims)
		assertEquals(1, passiveAvailableEdges)
	}

	@Test
	fun sourceToDestinationRaceGateCoversEveryIndependentCallbackOrdering() {
		assertEquals(4, SourceToDestinationRaceOrderings.size)
		assertEquals(
			setOf(
				SourceToDestinationRaceOrdering(true, true),
				SourceToDestinationRaceOrdering(true, false),
				SourceToDestinationRaceOrdering(false, true),
				SourceToDestinationRaceOrdering(false, false)
			),
			SourceToDestinationRaceOrderings.toSet()
		)
	}

	@Test
	fun sourceToDestinationRaceGateFencesPassiveCaptureAndDelaysExposure() {
		SourceToDestinationRaceOrderings.forEach { ordering ->
			val fixture = SourceToDestinationRaceFixture()
			fixture.stagePassivePreview(7)
			fixture.reserveLiveDestination()
			assertFalse(fixture.attemptStalePassiveCapture(9))

			if (ordering.rendererSettlesBeforeRestoration) {
				fixture.settleNativeDestinationRenderer()
			}
			fixture.completePassiveRestoration()
			if (!ordering.rendererSettlesBeforeRestoration) {
				fixture.settleNativeDestinationRenderer()
			}
			fixture.acknowledgeExactDestination()

			if (ordering.visualCallbackBeforeStalePassiveCapture) {
				fixture.completeVisualState()
				assertFalse(fixture.attemptStalePassiveCapture(11))
			} else {
				assertFalse(fixture.attemptStalePassiveCapture(11))
				fixture.completeVisualState()
			}
			fixture.completeVisualFrame()
			fixture.acceptExactDestinationProof()
			fixture.assertPassiveCannotReacquire()

			assertFalse(fixture.commitExposedFrame())
			fixture.commitShield()
			assertFalse(fixture.commitExposedFrame())
			fixture.completeShieldFade()
			assertTrue(fixture.commitExposedFrame())
			fixture.invokeStaleFinalization()
			fixture.assertCommittedDestination()
		}
	}

	@Test
	fun sourceToDestinationRaceGateDrainsCancellationAndRejectsNewerEvents() {
		listOf(
			SourceToDestinationRaceBoundary.Restoring,
			SourceToDestinationRaceBoundary.LiveMutation,
			SourceToDestinationRaceBoundary.AwaitingVisual,
			SourceToDestinationRaceBoundary.DestinationProof,
			SourceToDestinationRaceBoundary.Finalizing
		).forEach { boundary ->
			val fixture = SourceToDestinationRaceFixture()
			fixture.stagePassivePreview(7)
			fixture.reserveLiveDestination()
			if (boundary == SourceToDestinationRaceBoundary.Restoring) {
				fixture.detachAndCancel()
				fixture.assertCancelledTerminal()
				return@forEach
			}

			fixture.completePassiveRestoration()
			if (boundary == SourceToDestinationRaceBoundary.LiveMutation) {
				fixture.detachAndCancel()
				fixture.assertCancelledTerminal()
				return@forEach
			}

			fixture.settleNativeDestinationRenderer()
			fixture.acknowledgeExactDestination()
			if (boundary == SourceToDestinationRaceBoundary.AwaitingVisual) {
				fixture.detachAndCancel()
				fixture.assertCancelledTerminal()
				return@forEach
			}

			fixture.completeVisualState()
			fixture.completeVisualFrame()
			if (boundary == SourceToDestinationRaceBoundary.DestinationProof) {
				fixture.detachAndCancel()
				fixture.invokeStaleDestinationProof()
				fixture.assertCancelledTerminal()
				return@forEach
			}

			fixture.acceptExactDestinationProof()
			fixture.publishNewerGeneration()
			fixture.invokeStaleFinalization()
			fixture.assertNotCompleted()
			fixture.detachAndCancel()
			fixture.assertCancelledTerminal()
		}

		val staleFinalization = SourceToDestinationRaceFixture()
		staleFinalization.stagePassivePreview(7)
		staleFinalization.reserveLiveDestination()
		staleFinalization.completePassiveRestoration()
		staleFinalization.settleNativeDestinationRenderer()
		staleFinalization.acknowledgeExactDestination()
		staleFinalization.completeVisualState()
		staleFinalization.completeVisualFrame()
		staleFinalization.acceptExactDestinationProof()
		staleFinalization.detachAndCancel()
		val newer = staleFinalization.enqueueNewerRequest()
		staleFinalization.invokeStaleFinalization()
		assertEquals(newer, staleFinalization.queueHead())
		staleFinalization.cancelQueuedRequests()
		staleFinalization.assertCancelledTerminal()
	}

	@Test
	fun presentationFailureAutomaticallyRequestsOnlyOneRecovery() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 3L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val finalizers = mutableListOf<(Boolean) -> Unit>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			finalizePresentation = { _, onFinalized -> finalizers += onFinalized },
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		finalizers.single().invoke(false)
		assertTrue(
			coordinator.onRetryEvent(
				ReaderPageRelocationVisualRetryEvent.Reprepared(
					foliateSessionId = request.foliateSessionId,
					destinationOrdinal = request.destinationOrdinal,
					rasterGeneration = request.rasterGeneration,
					textureGeneration = request.textureGeneration
				)
			)
		)
		finalizers.last().invoke(false)

		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.PresentationFailed),
			recoveries
		)
		assertEquals(request, queue.head())
	}

	@Test
	fun exposureCommitCannotCompleteAfterPresentationGenerationDrifts() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(request)
		lateinit var finalize: (Boolean) -> Unit
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = { error("No dispatch expected") },
			publishRecovery = { _, reason -> recoveries += reason },
			finalizePresentation = { _, onFinalized -> finalize = onFinalized },
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			}
		)

		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		state = state.copy(textureGeneration = request.textureGeneration + 1L)
		finalize(true)

		assertEquals(request, queue.head())
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.PresentationFailed),
			recoveries
		)
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
	fun exhaustedValidationSameGenerationRepreparationReleasesHeadAndDispatchesQueued() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, request)
		val host = FakeVisualHandoffHost(attached = true)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val terminalRejections = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { visualStateFor(request) },
			dispatch = dispatched::add,
			publishRecovery = { _, _ -> },
			finalizePresentation = immediatePresentationFinalizer(),
			validateContent = { _, onValidated ->
				onValidated(ReaderPageRelocationContentValidationResult.ContentRejected)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			onRejectedContentReleased = terminalRejections::add
		)
		assertTrue(coordinator.onAcknowledged(request))
		host.completeVisualState()
		host.runNextFrame()
		val queued = enqueueVisualRequest(
			queue = queue,
			gestureId = 2L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			rasterGeneration = 11L,
			textureGeneration = 21L
		)
		val sameGeneration = ReaderPageRelocationVisualRetryEvent.Reprepared(
			request.foliateSessionId,
			request.destinationOrdinal,
			request.rasterGeneration,
			request.textureGeneration
		)

		assertFalse(coordinator.onRetryEvent(sameGeneration))
		assertEquals(listOf(request), terminalRejections)
		assertEquals(queued, queue.head())
		assertEquals(1, queue.ownershipSnapshot().occupied)
		assertEquals(listOf(queued), dispatched)
		assertEquals(0, coordinator.pendingCallbackCount())

		assertFalse(coordinator.onRetryEvent(sameGeneration))
		assertEquals(listOf(request), terminalRejections)
		assertEquals(listOf(queued), dispatched)
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
	fun exhaustedReplacementBudgetReleasesAcknowledgedHeadAndDispatchesNextWithoutHidingShield() {
		val queue = ReaderPageRelocationQueue()
		val original = enqueueVisualRequest(queue, gestureId = 1L)
		acknowledgeForVisualHandoff(queue, original)
		val host = FakeVisualHandoffHost(attached = true)
		var state = visualStateFor(original)
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val hidden = mutableListOf<ReaderPageRelocationRequest>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val terminalRejections = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			finalizePresentation = immediatePresentationFinalizer(hidden::add),
			onRejectedContentReleased = terminalRejections::add,
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
		val queued = enqueueVisualRequest(
			queue = queue,
			gestureId = 2L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			rasterGeneration = 12L,
			textureGeneration = 22L
		)
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
		assertEquals(queued, queue.head())
		assertEquals(1, queue.ownershipSnapshot().occupied)
		assertEquals(listOf(replacement), terminalRejections)
		assertEquals(
			listOf(ReaderWebViewVisualHandoffFailure.ContentRejected),
			recoveries
		)
		assertEquals(listOf(replacement, queued), dispatched)
		assertEquals(0, coordinator.pendingCallbackCount())
		assertTrue(hidden.isEmpty())
	}

	@Test
	fun invalidatedExhaustedReplacementReleasesWithoutResettingBudget() {
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
		val terminalRejections = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { state },
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> recoveries += reason },
			finalizePresentation = immediatePresentationFinalizer(),
			onRejectedContentReleased = terminalRejections::add,
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
		val queued = enqueueVisualRequest(
			queue = queue,
			gestureId = 2L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			rasterGeneration = 13L,
			textureGeneration = 23L
		)
		assertFalse(coordinator.onRetryEvent(secondRepreparation))

		assertEquals(listOf(replacement), terminalRejections)
		assertEquals(queued, queue.head())
		assertEquals(listOf(replacement, queued), dispatched)
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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
			finalizePresentation = immediatePresentationFinalizer(),
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

	private val SourceToDestinationRaceOrderings = listOf(
		SourceToDestinationRaceOrdering(
			rendererSettlesBeforeRestoration = true,
			visualCallbackBeforeStalePassiveCapture = true
		),
		SourceToDestinationRaceOrdering(
			rendererSettlesBeforeRestoration = true,
			visualCallbackBeforeStalePassiveCapture = false
		),
		SourceToDestinationRaceOrdering(
			rendererSettlesBeforeRestoration = false,
			visualCallbackBeforeStalePassiveCapture = true
		),
		SourceToDestinationRaceOrdering(
			rendererSettlesBeforeRestoration = false,
			visualCallbackBeforeStalePassiveCapture = false
		)
	)

	private data class SourceToDestinationRaceOrdering(
		val rendererSettlesBeforeRestoration: Boolean,
		val visualCallbackBeforeStalePassiveCapture: Boolean
	)

	private enum class SourceToDestinationRaceBoundary {
		Restoring,
		LiveMutation,
		AwaitingVisual,
		DestinationProof,
		Finalizing
	}

	private class SourceToDestinationRaceFixture {
		private val queue = ReaderPageRelocationQueue()
		private val ownership = ReaderForegroundWebViewOwnership()
		private val host = FakeVisualHandoffHost(attached = true)
		private val validationCallbacks = mutableListOf<
			(ReaderPageRelocationContentValidationResult) -> Unit
		>()
		private val presentationFinalizers = mutableListOf<(Boolean) -> Unit>()
		private lateinit var request: ReaderPageRelocationRequest
		private var hasRequest = false
		private lateinit var liveClaim: ReaderForegroundWebViewLiveClaim
		private lateinit var passiveLease: ReaderForegroundWebViewPassiveLease
		private var passiveRestoration: (
			(ReaderPageRasterCancellationRestoration) -> Unit
		)? = null
		private var visualState: ReaderPageRelocationVisualState? = null
		private var rendererSettled = false
		private var exactDestinationDispatched = false
		private var destinationProof = false
		private var shieldCommitted = false
		private var shieldFadeCompleted = false
		private var exposedFrameCommitted = false
		private var dispatchCurrent = true
		private var completedWebViewOrdinal: Int? = null
		private var currentWebViewOrdinal = SourceOrdinal
		private val externallyExposedOrdinals = mutableListOf<Int>()
		private var passiveMutationPublishedWhileLiveOwned = false
		private var shieldReleasedBeforeDestinationProof = false
		private var sourceOrPreviewPresentationExposed = false
		private val liveDispatch = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = ownership,
			isDispatchCurrent = { candidate ->
				dispatchCurrent && hasRequest && candidate == request
			},
			dispatchExact = { dispatched, generation ->
				assertEquals(SourceOrdinal, dispatched.sourceOrdinal)
				assertEquals(DestinationOrdinal, dispatched.destinationOrdinal)
				assertTrue(ownership.isCurrent(liveClaim, generation))
				exactDestinationDispatched = true
				currentWebViewOrdinal = dispatched.destinationOrdinal
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, reason ->
				error("Unexpected synthetic relocation rejection: $reason")
			}
		)
		private val visualHandoff = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = { checkNotNull(visualState) },
			dispatch = { error("No queued race relocation is expected: $it") },
			publishRecovery = { _, _ -> },
			finalizePresentation = { finalizingRequest, onFinalized ->
				assertEquals(request, finalizingRequest)
				assertTrue(destinationProof)
				presentationFinalizers += onFinalized
			},
			validateContent = { validatingRequest, onValidated ->
				assertEquals(request, validatingRequest)
				assertTrue(rendererSettled)
				assertTrue(exactDestinationDispatched)
				assertEquals(DestinationOrdinal, currentWebViewOrdinal)
				assertTrue(liveDispatch.isCurrent(validatingRequest))
				destinationProof = true
				validationCallbacks += onValidated
				ReaderPageRelocationContentValidationHandle.Completed
			},
			canRecover = { false },
			onCompleted = { completedRequest ->
				if (!destinationProof || !exposedFrameCommitted) {
					shieldReleasedBeforeDestinationProof = true
				}
				if (currentWebViewOrdinal != DestinationOrdinal) {
					sourceOrPreviewPresentationExposed = true
				}
				assertTrue(liveDispatch.complete(completedRequest))
				completedWebViewOrdinal = currentWebViewOrdinal
				externallyExposedOrdinals += currentWebViewOrdinal
			}
		)

		fun stagePassivePreview(ordinal: Int) {
			passiveLease = checkNotNull(
				ownership.tryAcquirePassive(sessionId = 71L) { onRestored ->
					passiveRestoration = onRestored
				}
			)
			assertTrue(ownership.isCurrent(passiveLease))
			assertTrue(ordinal != SourceOrdinal && ordinal != DestinationOrdinal)
		}

		fun reserveLiveDestination() {
			val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
				queue.reserve(gestureId = 17L)
			).reservation
			liveClaim = ownership.acquireLive(reservation.gestureId)
			request = assertIs<ReaderPageRelocationTransferResult.Enqueued>(
				queue.enqueueReserved(
					reservation = reservation,
					rasterGeneration = 31L,
					textureGeneration = 47L,
					sourceOrdinal = SourceOrdinal,
					destinationOrdinal = DestinationOrdinal,
					logicalDirection = ReaderPageTurnDirection.Next,
					foliateSessionId = "synthetic-race"
				)
			).request
			hasRequest = true
			assertTrue(liveDispatch.transfer(request, liveClaim))
			assertTrue(liveDispatch.dispatch(request))
			assertFalse(ownership.isCurrent(passiveLease))
			assertEquals(1, ownership.snapshot().restorationCallbacks)
		}

		fun attemptStalePassiveCapture(ordinal: Int): Boolean {
			val published = ownership.isCurrent(passiveLease)
			if (published && ownership.snapshot().liveClaims > 0) {
				passiveMutationPublishedWhileLiveOwned = true
			}
			if (published && ordinal != DestinationOrdinal) {
				sourceOrPreviewPresentationExposed = true
			}
			return published
		}

		fun settleNativeDestinationRenderer() {
			rendererSettled = true
		}

		fun completePassiveRestoration() {
			checkNotNull(passiveRestoration)(
				ReaderPageRasterCancellationRestoration.Restored
			)
		}

		fun acknowledgeExactDestination() {
			assertTrue(rendererSettled)
			assertTrue(exactDestinationDispatched)
			assertEquals(request, queue.commandToDispatch())
			assertTrue(
				queue.acknowledge(
					request.token.value,
					DestinationOrdinal,
					request.foliateSessionId,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
			visualState = ReaderPageRelocationVisualState(
				attached = true,
				resumed = true,
				foliateSessionId = request.foliateSessionId,
				webViewOrdinal = request.destinationOrdinal,
				rasterGeneration = request.rasterGeneration,
				textureGeneration = request.textureGeneration
			)
			assertTrue(visualHandoff.onAcknowledged(request))
		}

		fun completeVisualState() = host.completeVisualState()

		fun completeVisualFrame() = host.runNextFrame()

		fun acceptExactDestinationProof() {
			validationCallbacks.single().invoke(
				ReaderPageRelocationContentValidationResult.Accepted
			)
			assertEquals(1, presentationFinalizers.size)
		}

		fun invokeStaleDestinationProof() {
			validationCallbacks.single().invoke(
				ReaderPageRelocationContentValidationResult.Accepted
			)
		}

		fun assertPassiveCannotReacquire() {
			assertNull(
				ownership.tryAcquirePassive(sessionId = 72L) {
					error("Live exact destination must exclude passive reacquisition")
				}
			)
		}

		fun commitShield() {
			assertTrue(destinationProof)
			shieldCommitted = true
		}

		fun completeShieldFade() {
			assertTrue(shieldCommitted)
			shieldFadeCompleted = true
		}

		fun commitExposedFrame(): Boolean {
			if (!shieldCommitted || !shieldFadeCompleted || !destinationProof) return false
			exposedFrameCommitted = true
			presentationFinalizers.single().invoke(true)
			return true
		}

		fun invokeStaleFinalization() {
			presentationFinalizers.lastOrNull()?.invoke(true)
		}

		fun publishNewerGeneration() {
			visualState = checkNotNull(visualState).copy(
				textureGeneration = request.textureGeneration + 1L
			)
		}

		fun detachAndCancel() {
			dispatchCurrent = false
			visualState = visualState?.copy(attached = false)
			host.detach()
			queue.cancelAll()
			visualHandoff.cancelForQueueInvalidation()
			liveDispatch.releaseAll()
			completePassiveRestoration()
			invokeStaleFinalization()
		}

		fun enqueueNewerRequest(): ReaderPageRelocationRequest {
			val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
				queue.reserve(gestureId = 18L)
			).reservation
			return assertIs<ReaderPageRelocationTransferResult.Enqueued>(
				queue.enqueueReserved(
					reservation = reservation,
					rasterGeneration = 32L,
					textureGeneration = 48L,
					sourceOrdinal = SourceOrdinal,
					destinationOrdinal = DestinationOrdinal,
					logicalDirection = ReaderPageTurnDirection.Next,
					foliateSessionId = "synthetic-race"
				)
			).request
		}

		fun queueHead(): ReaderPageRelocationRequest? = queue.head()

		fun cancelQueuedRequests() {
			queue.cancelAll()
		}

		fun assertNotCompleted() {
			assertEquals(request, queue.head())
			assertNull(completedWebViewOrdinal)
		}

		fun assertCommittedDestination() {
			assertNull(queue.head())
			assertEquals(DestinationOrdinal, completedWebViewOrdinal)
			assertEquals(listOf(DestinationOrdinal), externallyExposedOrdinals)
			assertFalse(passiveMutationPublishedWhileLiveOwned)
			assertFalse(shieldReleasedBeforeDestinationProof)
			assertFalse(sourceOrPreviewPresentationExposed)
			assertOwnershipDrained()
		}

		fun assertCancelledTerminal() {
			assertNull(completedWebViewOrdinal)
			assertFalse(passiveMutationPublishedWhileLiveOwned)
			assertFalse(shieldReleasedBeforeDestinationProof)
			assertFalse(sourceOrPreviewPresentationExposed)
			assertOwnershipDrained()
		}

		private fun assertOwnershipDrained() {
			val snapshot = ownership.snapshot()
			assertEquals(0, snapshot.passiveOwners)
			assertEquals(0, snapshot.liveClaims)
			assertEquals(0, snapshot.restorationCallbacks)
		}

		private companion object {
			const val SourceOrdinal = 0
			const val DestinationOrdinal = 2
		}
	}

	private fun immediatePresentationFinalizer(
		onFinalizing: (ReaderPageRelocationRequest) -> Unit = {}
	): (
		ReaderPageRelocationRequest,
		(Boolean) -> Unit
	) -> Unit = { request, onFinalized ->
		onFinalizing(request)
		onFinalized(true)
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

		fun detach() {
			attached = false
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
