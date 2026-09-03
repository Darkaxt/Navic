package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationMemoryPressureLevel
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRendererCleanupOwnership
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.publicationIdentity
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.reader.readerPresentationDecision
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPresentationLifecycleDeliveryTest {
	@Test
	fun visibilityLossRetriesUntilExactReceiptThenRevokesPointerAndDelayedTapOnce() {
		val binding = completeBinding("visibility-loss")
		val controller = ControllerHarness(controller(binding, readerSessionGeneration = 11L))
		val delivery = delivery(controller.controller, binding)
		val input = InputHarness(binding)
		val delayedGestureId = input.beginDelayedTap(downTimeMillis = 100L)
		val activeGestureId = input.beginActivePointer(downTimeMillis = 200L)

		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		assertNull(delivery.retry { null })
		assertEquals(ReaderPresentationLifecycleState.Foreground, controller.lifecycle)
		assertEquals(2, input.host.contentGestureTokenCount())
		assertTrue(input.published.isEmpty())

		val receipt = assertNotNull(delivery.retry(controller::dispatch))
		input.apply(receipt)

		assertEquals(ReaderPresentationLifecycleState.Background, controller.lifecycle)
		assertEquals(0, delivery.pendingEventCount)
		assertEquals(0, input.host.contentGestureTokenCount())
		assertNull(input.host.takeDelayedTap(100L))
		assertEquals(
			setOf(delayedGestureId, activeGestureId),
			input.published.map { it.first }.toSet()
		)
		assertTrue(
			input.published.all { it.second == ReaderPageGestureTerminalOutcome.CancelledLifecycle }
		)
		assertEquals(2, input.published.size)
		assertEquals(4, input.cancellationPort.calls.size)

		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		assertNull(delivery.retry(controller::dispatch))
		input.apply(receipt)
		assertEquals(2, input.published.size)
		assertEquals(4, input.cancellationPort.calls.size)
		assertEquals(ReaderPagePointerRoute.Consume, input.host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
	}

	@Test
	fun callbackFailureIsContainedAndTheSameLifecycleFactRemainsRetryable() {
		val binding = completeBinding("throw-retry")
		val controller = ControllerHarness(controller(binding, readerSessionGeneration = 12L))
		val delivery = delivery(controller.controller, binding)
		var attempts = 0

		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		assertNull(
			delivery.retry {
				attempts += 1
				error("delivery failed")
			}
		)
		assertEquals(1, attempts)
		assertEquals(1, delivery.pendingEventCount)
		assertEquals(ReaderPresentationLifecycleState.Foreground, controller.lifecycle)

		assertNotNull(delivery.retry(controller::dispatch))
		assertEquals(ReaderPresentationLifecycleState.Background, controller.lifecycle)
		assertEquals(0, delivery.pendingEventCount)
		assertNull(delivery.retry(controller::dispatch))
	}

	@Test
	fun staleWrongPublicationAndOlderReceiptsCannotAcknowledgePendingLifecycle() {
		val binding = completeBinding("receipt-fence")
		val wrongBinding = completeBinding("wrong-publication")
		val delivery = ReaderPresentationLifecycleDelivery()
		val floor = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 13L,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = 5L
		)
		delivery.reset(floor, observedWindowVisible = null)
		assertTrue(delivery.bindPublication(binding.publicationIdentity))
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		val event = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.VisibilityLost
		)
		val background = ReaderPresentationState(
			binding = binding,
			lifecycle = ReaderPresentationLifecycleState.Background
		)

		val rejected = listOf(
			lifecycleReceipt(
				event,
				background,
				floor.copy(readerSessionGeneration = 12L, eventSequence = 6L)
			),
			lifecycleReceipt(
				event,
				background.copy(binding = wrongBinding),
				floor.copy(
					publicationIdentity = wrongBinding.publicationIdentity,
					eventSequence = 6L
				)
			),
			lifecycleReceipt(
				event,
				background.copy(binding = wrongBinding),
				floor.copy(eventSequence = 6L)
			),
			lifecycleReceipt(
				event,
				background,
				floor.copy(eventSequence = 6L),
				disposition = ReaderPresentationEventDisposition.Rejected
			),
			lifecycleReceipt(
				event,
				background,
				floor.copy(eventSequence = 6L),
				disposition = ReaderPresentationEventDisposition.Stale
			),
			lifecycleReceipt(event, background, floor.copy(eventSequence = 4L))
		)
		rejected.forEach { receipt ->
			assertNull(delivery.retry { receipt })
			assertEquals(1, delivery.pendingEventCount)
		}

		assertNotNull(
			delivery.retry {
				lifecycleReceipt(event, background, floor.copy(eventSequence = 6L))
			}
		)
		assertEquals(ReaderPresentationLifecycleState.Background, delivery.acknowledgedLifecycle)
		assertEquals(0, delivery.pendingEventCount)
	}

	@Test
	fun unboundWrongPublicationComposeVersionCannotRaiseTheCurrentReceiptFloor() {
		val binding = completeBinding("unbound-floor")
		val wrongBinding = completeBinding("unbound-wrong-publication")
		val delivery = ReaderPresentationLifecycleDelivery()
		val floor = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 131L,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = 5L
		)
		delivery.reset(floor, observedWindowVisible = null)

		assertFalse(
			delivery.advanceComposeVersion(
				floor.copy(
					publicationIdentity = wrongBinding.publicationIdentity,
					eventSequence = 100L
				)
			)
		)
		assertTrue(delivery.bindPublication(binding.publicationIdentity))
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		val event = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.VisibilityLost
		)
		assertNotNull(
			delivery.retry {
				lifecycleReceipt(
					event = event,
					postState = ReaderPresentationState(
						binding = binding,
						lifecycle = ReaderPresentationLifecycleState.Background
					),
					version = floor.copy(eventSequence = 6L)
				)
			}
		)
		assertEquals(0, delivery.pendingEventCount)
	}

	@Test
	fun visibilityEdgesPreserveLossBeforeRestoreAndSafelyCollapseHideShowHide() {
		val binding = completeBinding("visibility-order")
		val controller = ControllerHarness(controller(binding, readerSessionGeneration = 14L))
		val delivery = delivery(controller.controller, binding)
		val delivered = mutableListOf<ReaderPresentationLifecycleEvent>()

		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		assertNull(delivery.retry { event ->
			delivered += event.event
			null
		})
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityRestored)
		assertNotNull(delivery.retry { event ->
			delivered += event.event
			controller.dispatch(event)
		})
		assertEquals(ReaderPresentationLifecycleState.Background, controller.lifecycle)
		assertEquals(1, delivery.pendingEventCount)
		assertNotNull(delivery.retry { event ->
			delivered += event.event
			controller.dispatch(event)
		})
		assertEquals(ReaderPresentationLifecycleState.Foreground, controller.lifecycle)
		assertEquals(
			listOf(
				ReaderPresentationLifecycleEvent.VisibilityLost,
				ReaderPresentationLifecycleEvent.VisibilityLost,
				ReaderPresentationLifecycleEvent.VisibilityRestored
			),
			delivered
		)

		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		assertNull(delivery.retry { null })
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityRestored)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		assertNotNull(delivery.retry(controller::dispatch))
		assertEquals(ReaderPresentationLifecycleState.Background, controller.lifecycle)
		assertEquals(0, delivery.pendingEventCount)
		assertNull(delivery.retry(controller::dispatch))
	}

	@Test
	fun memoryPressureAndRendererLossUseFiniteDeduplicatedPendingState() {
		val binding = completeBinding("finite-inventory")
		val controller = ControllerHarness(controller(binding, readerSessionGeneration = 15L))
		val delivery = delivery(controller.controller, binding)
		val inventory = memoryPressureEvents + ReaderPresentationLifecycleEvent.RendererLost

		repeat(20) {
			inventory.forEach(delivery::observe)
		}
		assertEquals(inventory.size, delivery.pendingEventCount)
		assertTrue(delivery.pendingEventCount <= ReaderPresentationLifecyclePendingEventLimit)

		val delivered = mutableListOf<ReaderPresentationLifecycleEvent>()
		repeat(inventory.size) {
			assertNotNull(delivery.retry { event ->
				delivered += event.event
				controller.dispatch(event)
			})
		}

		assertEquals(inventory.toSet(), delivered.toSet())
		assertEquals(inventory.size, delivered.size)
		assertEquals(0, delivery.pendingEventCount)
		assertTrue(controller.controller.state.presentation.rendererCleanupOwnership.isEmpty())
	}

	@Test
	fun pendingLimitCoversTheCompleteTypedLifecycleStateSpace() {
		val binding = completeBinding("complete-state-space")
		val delivery = delivery(controller(binding, readerSessionGeneration = 151L), binding)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityRestored)
		delivery.observe(ReaderPresentationLifecycleEvent.RendererLost)
		ReaderPresentationMemoryPressureLevel.entries.forEach { level ->
			delivery.observe(ReaderPresentationLifecycleEvent.RunningMemoryPressure(level))
			delivery.observe(ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(level))
		}
		delivery.observe(ReaderPresentationLifecycleEvent.PublicationClosed)

		assertEquals(14, delivery.pendingEventCount)
		assertTrue(delivery.pendingEventCount <= ReaderPresentationLifecyclePendingEventLimit)
	}

	@Test
	fun publicationCloseHasPriorityAndDestroyedReplayAcknowledgesCleanupExactlyOnce() {
		val binding = completeBinding("terminal-priority")
		val cleanup = ReaderRendererCleanupOwnership(ReaderPresentationToken(7L), binding)
		val controller = ControllerHarness(
			controller(
				binding = binding,
				readerSessionGeneration = 16L,
				cleanupOwnership = listOf(cleanup)
			)
		)
		val delivery = delivery(controller.controller, binding)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		delivery.observe(memoryPressureEvents.first())
		delivery.observe(ReaderPresentationLifecycleEvent.RendererLost)
		delivery.observe(ReaderPresentationLifecycleEvent.PublicationClosed)

		assertNull(delivery.retry { event ->
			assertEquals(ReaderPresentationLifecycleEvent.PublicationClosed, event.event)
			controller.dispatch(event)
			null
		})
		assertEquals(ReaderPresentationLifecycleState.Destroyed, controller.lifecycle)
		assertTrue(delivery.pendingEventCount > 1)
		assertEquals(1, controller.effects.size)
		assertIs<ReaderPresentationEffect.ReleaseStalePresentation>(controller.effects.single())

		val replay = assertNotNull(delivery.retry(controller::dispatch))
		assertEquals(ReaderPresentationEventDisposition.Destroyed, replay.disposition)
		assertEquals(ReaderPresentationLifecycleState.Destroyed, replay.postState.lifecycle)
		assertEquals(0, delivery.pendingEventCount)
		assertEquals(1, controller.effects.size)

		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityRestored)
		delivery.observe(ReaderPresentationLifecycleEvent.RendererLost)
		delivery.observe(memoryPressureEvents.last())
		assertEquals(0, delivery.pendingEventCount)
		assertNull(delivery.retry(controller::dispatch))
	}

	@Test
	fun epochResetDropsOldTerminalWorkAndRequiresCurrentPublicationReceipt() {
		val oldBinding = completeBinding("old-epoch")
		val oldController = ControllerHarness(controller(oldBinding, readerSessionGeneration = 17L))
		val delivery = delivery(oldController.controller, oldBinding)
		delivery.observe(ReaderPresentationLifecycleEvent.PublicationClosed)
		val oldReceipt = oldController.dispatch(
			ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.PublicationClosed
			)
		)

		val newBinding = completeBinding("new-epoch")
		val newController = ControllerHarness(controller(newBinding, readerSessionGeneration = 18L))
		delivery.reset(
			version = newController.controller.presentationVersion,
			observedWindowVisible = false
		)
		assertTrue(delivery.bindPublication(newBinding.publicationIdentity))
		assertEquals(1, delivery.pendingEventCount)
		assertNull(delivery.retry { oldReceipt })
		assertEquals(1, delivery.pendingEventCount)

		val currentReceipt = assertNotNull(delivery.retry(newController::dispatch))
		assertEquals(ReaderPresentationLifecycleState.Background, currentReceipt.postState.lifecycle)
		assertEquals(newBinding.publicationIdentity, currentReceipt.version.publicationIdentity)
		assertEquals(0, delivery.pendingEventCount)
	}

	@Test
	fun retryDispatchesAtMostOneEventAndRejectsReentrantRecursion() {
		val binding = completeBinding("single-retry")
		val controller = ControllerHarness(controller(binding, readerSessionGeneration = 19L))
		val delivery = delivery(controller.controller, binding)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityRestored)
		var callbacks = 0

		assertNotNull(delivery.retry { event ->
			callbacks += 1
			assertNull(delivery.retry(controller::dispatch))
			controller.dispatch(event)
		})
		assertEquals(1, callbacks)
		assertEquals(1, delivery.pendingEventCount)
		assertNotNull(delivery.retry { event ->
			callbacks += 1
			controller.dispatch(event)
		})
		assertEquals(2, callbacks)
		assertEquals(0, delivery.pendingEventCount)
	}

	@Test
	fun productionRoutesEveryLifecycleProducerThroughTypedDeliveryAndReachableRetries() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val reporter = source
			.substringAfter("private fun reportPresentationLifecycleEvent(")
			.substringBefore("private fun currentPresentationBindingOrNull(")
		val unsafeRenderer = source
			.substringAfter("onUnsafeLifecycleEvent = { event ->")
			.substringBefore("onOwnershipMutated =")
		val epoch = source
			.substringAfter("fun preparePresentationEpoch(")
			.substringBefore("fun completePresentationViewerReplacement(")
		val callbackRebind = source
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun releaseStalePresentation(")
		val finalLifecycle = source
			.substringAfter("private fun beginFinalHostLifecycle(")
			.substringBefore("private fun closePhysicalPointerDelivery(")

		assertContains(reporter, "presentationLifecycleDelivery.observe(event)")
		assertContains(reporter, "retryPresentationLifecycleDelivery()")
		assertContains(unsafeRenderer, "ReaderPresentationLifecycleEvent.RendererLost")
		assertContains(epoch, "presentationLifecycleDelivery.reset(")
		assertContains(callbackRebind, "retryPresentationLifecycleDelivery()")
		assertContains(finalLifecycle, "ReaderPresentationLifecycleEvent.PublicationClosed")
		assertContains(finalLifecycle, "retryPresentationLifecycleDelivery()")
		assertFalse(reporter.contains("lastPresentationWindowVisible == visible) return"))
	}

	private class ControllerHarness(
		var controller: ReaderController
	) {
		val effects = mutableListOf<ReaderPresentationEffect>()
		val lifecycle: ReaderPresentationLifecycleState
			get() = controller.state.presentation.lifecycle

		fun dispatch(event: ReaderPresentationEvent.Lifecycle): ReaderPresentationEventReceipt {
			val step = controller.onPresentationEvent(event)
			controller = step.controller
			effects += step.presentationEffects
			return assertNotNull(step.presentationReceipt)
		}
	}

	private class RecordingCancellationPort : ReaderPageHostCancellationPort {
		val calls = mutableListOf<ReaderPageLifecycleCancellationReason>()

		override fun cancelForPointerInterruption(gestureId: Long) = Unit
		override fun clearCompletedPointerOwnership(gestureId: Long) = Unit
		override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) {
			calls += reason
		}
		override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) {
			calls += reason
		}
		override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) {
			calls += reason
		}
		override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) {
			calls += reason
		}
	}

	private class InputHarness(binding: ReaderPresentationBinding) {
		private val ready = ReaderPageReadinessState(
			textureDeck = ReaderTextureDeckState.Ready,
			interaction = ReaderPageInteractionState.Ready
		)
		private val policy = readerPageOperationPolicy(ready)
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val cancellationPort = RecordingCancellationPort()
		private val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle()) { id, outcome ->
			published += id to outcome
		}
		val host = ReaderPageInputSettlementHostController(
			initialPresentationInputPolicy = ReaderPresentationInputPolicy.NativePage(policy),
			initialLocalSafetyPolicy = policy,
			initialNativeTapContinuationIdentity = ReaderNativeTapContinuationIdentity(
				binding = binding,
				presentationToken = ReaderPresentationToken(5L),
				authorityPolicy = policy,
				localSafetyPolicy = policy
			),
			pointerRouter = router,
			cancellationPort = cancellationPort
		)

		fun beginDelayedTap(downTimeMillis: Long): Long {
			val id = assertNotNull(
				host.dispatchPointer(
					ReaderPageHostPointerEvent.Down(20f, 30f, downTimeMillis)
				).gestureId
			)
			assertEquals(ReaderPagePointerRoute.Content, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			return id
		}

		fun beginActivePointer(downTimeMillis: Long): Long = assertNotNull(
			host.dispatchPointer(
				ReaderPageHostPointerEvent.Down(20f, 30f, downTimeMillis)
			).gestureId
		)

		fun apply(receipt: ReaderPresentationEventReceipt) {
			host.updateInputPolicies(
				presentationInputPolicy = readerPresentationDecision(receipt.postState).inputPolicy,
				localSafetyPolicy = policy,
				nativeTapContinuationIdentity = null
			)
		}
	}

	private fun delivery(
		controller: ReaderController,
		binding: ReaderPresentationBinding
	): ReaderPresentationLifecycleDelivery = ReaderPresentationLifecycleDelivery().also { delivery ->
		delivery.reset(controller.presentationVersion, observedWindowVisible = null)
		assertTrue(delivery.bindPublication(binding.publicationIdentity))
	}

	private fun controller(
		binding: ReaderPresentationBinding,
		readerSessionGeneration: Long,
		cleanupOwnership: List<ReaderRendererCleanupOwnership> = emptyList()
	): ReaderController = ReaderController(
		ReaderControllerState(
			readerSessionGeneration = readerSessionGeneration,
			presentation = ReaderPresentationState(
				binding = binding,
				rendererCleanupOwnership = cleanupOwnership
			)
		)
	)

	private fun completeBinding(sessionId: String): ReaderPresentationBinding =
		ReaderPresentationBinding(
			foliateSessionId = sessionId,
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)

	private fun lifecycleReceipt(
		event: ReaderPresentationEvent.Lifecycle,
		postState: ReaderPresentationState,
		version: ReaderPresentationReceiptVersion,
		disposition: ReaderPresentationEventDisposition = ReaderPresentationEventDisposition.Accepted
	): ReaderPresentationEventReceipt = ReaderPresentationEventReceipt(
		event = event,
		version = version,
		disposition = disposition,
		postState = postState,
		effects = emptyList()
	)

	private companion object {
		val memoryPressureEvents = listOf(
			ReaderPresentationLifecycleEvent.RunningMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Moderate
			),
			ReaderPresentationLifecycleEvent.RunningMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Low
			),
			ReaderPresentationLifecycleEvent.RunningMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Critical
			),
			ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Background
			),
			ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Moderate
			),
			ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Complete
			)
		)
	}
}
