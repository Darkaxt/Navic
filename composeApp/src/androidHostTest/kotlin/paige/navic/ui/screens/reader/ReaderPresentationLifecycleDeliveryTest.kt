package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPresentationLifecycleDeliveryTest {
	@Test
	fun productionDispatcherRejectsEveryReducerIncompatibleRendererLossReceipt() {
		val binding = completeBinding("reducer-exact-renderer-loss")
		val wrongBinding = completeBinding("reducer-wrong-publication")
		val cleanup = ReaderRendererCleanupOwnership(ReaderPresentationToken(40L), binding)
		val preState = settledState(binding, listOf(cleanup))
		val controller = ReaderController(
			state = ReaderControllerState(
				readerSessionGeneration = 30L,
				presentation = preState
			),
			presentationEventSequence = 10L
		)
		val preVersion = controller.presentationVersion
		val event = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.RendererLost
		)
		val exactReceipt = assertNotNull(
			controller.onPresentationEvent(event).presentationReceipt
		)
		val reporter = reporter(preVersion, binding, preState)
		val delivery = delivery(preVersion, binding)
		val decisions = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
		val dispatcher = ReaderPresentationReceiptDispatcher(reporter, delivery) { decision, _ ->
			decisions += decision
		}
		delivery.observe(event.event)
		val wrongFailure = ReaderDiagnosticPresentation.Failure(
			reason = ReaderPresentationFailureReason.PreparationFailed,
			retryable = true,
			cancellable = true
		)
		val invalidReceipts = listOf(
			exactReceipt.copy(
				postState = exactReceipt.postState.copy(authority = preState.authority)
			),
			exactReceipt.copy(
				postState = exactReceipt.postState.copy(
					rendererCleanupOwnership = listOf(cleanup)
				)
			),
			exactReceipt.copy(postState = exactReceipt.postState.copy(failure = null)),
			exactReceipt.copy(postState = exactReceipt.postState.copy(failure = wrongFailure)),
			exactReceipt.copy(
				postState = exactReceipt.postState.copy(
					lifecycle = ReaderPresentationLifecycleState.Background
				)
			),
			exactReceipt.copy(
				postState = exactReceipt.postState.copy(binding = wrongBinding)
			),
			exactReceipt.copy(
				effects = listOf(
					ReaderPresentationEffect.ReleaseStalePresentation(
						token = cleanup.token,
						binding = cleanup.binding
					)
				)
			),
			exactReceipt.copy(disposition = ReaderPresentationEventDisposition.Idempotent)
		)

		invalidReceipts.forEach { invalid ->
			assertNull(
				delivery.retry { attempted ->
					dispatcher.dispatch(attempted) { invalid }
				}
			)
			assertEquals(1, delivery.pendingEventCount)
			assertTrue(decisions.isEmpty())
			assertNull(reporter.lastReportedBinding)
			assertFalse(reporter.matchesAuthoritativePresentationVersion(invalid.version))
		}

		assertNotNull(
			delivery.retry { attempted ->
				dispatcher.dispatch(attempted) { exactReceipt }
			}
		)
		assertEquals(0, delivery.pendingEventCount)
		assertTrue(reporter.matchesAuthoritativePresentationVersion(exactReceipt.version))
		assertEquals(
			listOf(readerPresentationDecision(exactReceipt.postState)),
			decisions
		)
	}

	@Test
	fun productionDispatcherUsesExactReducerEquivalenceForEveryLifecycleFamily() {
		val lifecycleCases = listOf(
			ReaderPresentationLifecycleState.Foreground to
				ReaderPresentationLifecycleEvent.VisibilityLost,
			ReaderPresentationLifecycleState.Background to
				ReaderPresentationLifecycleEvent.VisibilityLost,
			ReaderPresentationLifecycleState.Background to
				ReaderPresentationLifecycleEvent.VisibilityRestored,
			ReaderPresentationLifecycleState.Foreground to
				ReaderPresentationLifecycleEvent.RunningMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Low
				),
			ReaderPresentationLifecycleState.Background to
				ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Complete
				),
			ReaderPresentationLifecycleState.Foreground to
				ReaderPresentationLifecycleEvent.PublicationClosed
		)

		lifecycleCases.forEachIndexed { index, (lifecycle, lifecycleEvent) ->
			val binding = completeBinding("generic-lifecycle-$index")
			val cleanup = ReaderRendererCleanupOwnership(
				ReaderPresentationToken(50L + index),
				binding
			)
			val preState = settledState(binding, listOf(cleanup)).copy(lifecycle = lifecycle)
			val controller = ReaderController(
				state = ReaderControllerState(
					readerSessionGeneration = 40L + index,
					presentation = preState
				),
				presentationEventSequence = 20L
			)
			val preVersion = controller.presentationVersion
			val event = ReaderPresentationEvent.Lifecycle(lifecycleEvent)
			val exactReceipt = assertNotNull(
				controller.onPresentationEvent(event).presentationReceipt
			)
			val reporter = reporter(preVersion, binding, preState)
			val delivery = delivery(preVersion, binding, preState.lifecycle)
			val decisions = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
			val dispatcher = ReaderPresentationReceiptDispatcher(reporter, delivery) { decision, _ ->
				decisions += decision
			}
			delivery.observe(lifecycleEvent)
			val wrongDisposition = when (exactReceipt.disposition) {
				ReaderPresentationEventDisposition.Idempotent ->
					ReaderPresentationEventDisposition.Accepted
				else -> ReaderPresentationEventDisposition.Idempotent
			}
			val wrongEffects = if (exactReceipt.effects.isEmpty()) {
				listOf(
					ReaderPresentationEffect.ReleaseStalePresentation(
						token = cleanup.token,
						binding = cleanup.binding
					)
				)
			} else {
				emptyList()
			}
			val invalidReceipts = listOf(
				exactReceipt.copy(
					postState = exactReceipt.postState.copy(
						nextTokenValue = exactReceipt.postState.nextTokenValue + 1L
					)
				),
				exactReceipt.copy(disposition = wrongDisposition),
				exactReceipt.copy(effects = wrongEffects)
			)

			invalidReceipts.forEach { invalid ->
				assertNull(
					delivery.retry { attempted ->
						dispatcher.dispatch(attempted) { invalid }
					}
				)
				assertEquals(1, delivery.pendingEventCount, "event=$lifecycleEvent")
				assertTrue(decisions.isEmpty(), "event=$lifecycleEvent")
				assertNull(reporter.lastReportedBinding, "event=$lifecycleEvent")
			}

			assertNotNull(
				delivery.retry { attempted ->
					dispatcher.dispatch(attempted) { exactReceipt }
				},
				"event=$lifecycleEvent"
			)
			assertEquals(0, delivery.pendingEventCount, "event=$lifecycleEvent")
			assertEquals(
				listOf(readerPresentationDecision(exactReceipt.postState)),
				decisions,
				"event=$lifecycleEvent"
			)
		}
	}

	@Test
	fun persistentOldHostFailureCannotStarveOrAccumulateSuccessorAuthority() {
		val normalBinding = completeBinding("persistent-normal-host-failure")
		val normalController = ControllerHarness(
			controller(normalBinding, readerSessionGeneration = 58L, eventSequence = 3L)
		)
		val normalReporter = reporter(
			normalController.controller.presentationVersion,
			normalBinding,
			normalController.controller.state.presentation
		)
		val normalDelivery = delivery(normalController.controller.presentationVersion, normalBinding)
		val normalAttempts = mutableListOf<Long>()
		val normalDispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = normalReporter,
			lifecycleDelivery = normalDelivery,
			applyHostEffect = { effect ->
				normalAttempts += effect.identity.version.eventSequence
				error("persistent normal host failure")
			}
		)
		var latestNormalReceipt: ReaderPresentationEventReceipt? = null

		repeat(5) {
			latestNormalReceipt = assertNotNull(
				normalDispatcher.dispatch(ReaderPresentationEvent.Retry) { event ->
					normalController.dispatch(event)
				}
			)
			assertEquals(1, normalDispatcher.pendingHostEffectCount)
		}

		val latestNormal = assertNotNull(latestNormalReceipt)
		assertTrue(normalReporter.matchesAuthoritativePresentationVersion(latestNormal.version))
		assertEquals(
			(4L..8L).toList(),
			normalAttempts
		)

		listOf(
			ReaderPresentationLifecycleEvent.RendererLost,
			ReaderPresentationLifecycleEvent.PublicationClosed
		).forEachIndexed { index, lifecycleEvent ->
			val binding = completeBinding("persistent-terminal-host-failure-$index")
			val controller = ControllerHarness(
				controller(binding, readerSessionGeneration = 59L + index, eventSequence = 10L)
			)
			val reporter = reporter(
				controller.controller.presentationVersion,
				binding,
				controller.controller.state.presentation
			)
			val delivery = delivery(controller.controller.presentationVersion, binding)
			val attempts = mutableListOf<ReaderPresentationHostEffect>()
			val dispatcher = ReaderPresentationReceiptDispatcher(
				bindingReporter = reporter,
				lifecycleDelivery = delivery,
				applyHostEffect = { effect ->
					attempts += effect
					if (
						effect.event == ReaderPresentationEvent.Retry ||
							effect.rendererLossCancellationIdentity != null
					) {
						error("persistent predecessor host failure")
					}
				}
			)
			assertNotNull(
				dispatcher.dispatch(ReaderPresentationEvent.Retry) { event ->
					controller.dispatch(event)
				}
			)
			assertEquals(1, dispatcher.pendingHostEffectCount)
			delivery.observe(lifecycleEvent)
			var terminalReceipt: ReaderPresentationEventReceipt? = null

			val admitted = delivery.retry { event ->
				dispatcher.dispatch(
					event = event,
					rendererLossCancellationIdentity =
						ReaderRendererLossCancellationIdentity(
							presentationEpoch = reporter.captureEpoch(),
							rendererLossEpoch = 1L,
							reason = ReaderPageLifecycleCancellationReason.UnsafeContextLoss
						).takeIf {
							lifecycleEvent == ReaderPresentationLifecycleEvent.RendererLost
						},
					onEvent = { attempted ->
						controller.dispatch(attempted).also { terminalReceipt = it }
					}
				)
			}

			val terminal = assertNotNull(admitted, "event=$lifecycleEvent")
			assertEquals(terminal, terminalReceipt, "event=$lifecycleEvent")
			assertTrue(
				reporter.matchesAuthoritativePresentationVersion(terminal.version),
				"event=$lifecycleEvent"
			)
			assertEquals(0, delivery.pendingEventCount, "event=$lifecycleEvent")
			if (lifecycleEvent == ReaderPresentationLifecycleEvent.RendererLost) {
				assertEquals(1, dispatcher.pendingHostEffectCount)
				val successor = assertNotNull(
					dispatcher.dispatch(ReaderPresentationEvent.Retry, controller::dispatch)
				)
				assertTrue(reporter.matchesAuthoritativePresentationVersion(successor.version))
				assertEquals(2, dispatcher.pendingHostEffectCount)
				assertEquals(
					listOf<ReaderPresentationEvent?>(
						ReaderPresentationEvent.Retry,
						terminal.event,
						ReaderPresentationEvent.Retry,
						terminal.event
					),
					attempts.map { it.event }
				)
				assertEquals(attempts[2].decision, attempts[3].decision)
			} else {
				assertEquals(0, dispatcher.pendingHostEffectCount)
				assertEquals(
					listOf<ReaderPresentationEvent?>(ReaderPresentationEvent.Retry, terminal.event),
					attempts.map { it.event }
				)
			}
		}
	}

	@Test
	fun hostEffectApplierReplaysAConvergentProjectionAfterAHostOperationActsThenThrows() {
		ReaderPresentationHostMutation.entries.forEachIndexed { index, failedMutation ->
			val mutations = mutableListOf<ReaderPresentationHostMutation>()
			var injected = false
			fun act(mutation: ReaderPresentationHostMutation) {
				mutations += mutation
				if (!injected && mutation == failedMutation) {
					injected = true
					error("host operation acted before failing: $mutation")
				}
			}
			val applier = ReaderPresentationHostEffectApplier(
				commitHostModel = { act(ReaderPresentationHostMutation.Model) },
				applyViewerDecision = { _, _ -> act(ReaderPresentationHostMutation.ViewerDecision) },
				applyShellCoverLayer = { act(ReaderPresentationHostMutation.ShellCoverLayer) },
				applyViewerCoverVisibility = {
					act(ReaderPresentationHostMutation.ViewerCoverVisibility)
				},
				applyNativeCoverVisibility = {
					act(ReaderPresentationHostMutation.NativeCoverVisibility)
				}
			)
			val effect = ReaderPresentationHostEffect(
				ReaderPresentationHostEffectIdentity(
					69L,
					ReaderPresentationReceiptVersion(69L, null, 1L + index)
				),
				null,
				readerPresentationDecision(ReaderPresentationState()),
				null
			)

			assertFailsWith<IllegalStateException> { applier.apply(effect) }
			applier.apply(effect)
			applier.apply(effect)

			assertEquals(
				ReaderPresentationHostMutation.entries.take(index + 1) +
					ReaderPresentationHostMutation.entries,
				mutations,
				"mutation=$failedMutation"
			)
		}
	}

	@Test
	fun authoritativeReceiptTransactionRetriesEffectsAndRejectsStaleComposeAfterEveryBoundary() {
		ReaderPresentationHostMutation.entries.forEachIndexed { index, failedMutation ->
			val binding = completeBinding("transaction-boundary-$index")
			val preState = ReaderPresentationState(binding = binding)
			val controller = ReaderController(
				state = ReaderControllerState(
					readerSessionGeneration = 70L + index,
					presentation = preState
				),
				presentationEventSequence = 5L
			)
			val preVersion = controller.presentationVersion
			val event = ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.VisibilityLost
			)
			val receipt = assertNotNull(
				controller.onPresentationEvent(event).presentationReceipt
			)
			val reporter = reporter(preVersion, binding, preState)
			val delivery = delivery(preVersion, binding, preState.lifecycle)
			val mutations = mutableListOf<ReaderPresentationHostMutation>()
			val committedApplications = mutableListOf<ReaderNativePresentationApplication>()
			var injected = false
			val applier = ReaderPresentationHostEffectApplier(
				commitHostModel = { application ->
					committedApplications += application
					mutations += ReaderPresentationHostMutation.Model
				},
				applyViewerDecision = { _, _ ->
					mutations += ReaderPresentationHostMutation.ViewerDecision
				},
				applyShellCoverLayer = {
					mutations += ReaderPresentationHostMutation.ShellCoverLayer
				},
				applyViewerCoverVisibility = {
					mutations += ReaderPresentationHostMutation.ViewerCoverVisibility
				},
				applyNativeCoverVisibility = {
					mutations += ReaderPresentationHostMutation.NativeCoverVisibility
				},
				afterMutation = { mutation ->
					if (!injected && mutation == failedMutation) {
						injected = true
						error("injected failure after $mutation")
					}
				}
			)
			val dispatcher = ReaderPresentationReceiptDispatcher(
				bindingReporter = reporter,
				lifecycleDelivery = delivery,
				applyHostEffect = applier::apply
			)
			delivery.observe(event.event)

			assertNotNull(
				delivery.retry { attempted ->
					dispatcher.dispatch(attempted) { receipt }
				}
			)
			assertTrue(injected, "mutation=$failedMutation")
			assertEquals(0, delivery.pendingEventCount, "mutation=$failedMutation")
			assertEquals(
				ReaderPresentationLifecycleState.Background,
				delivery.acknowledgedLifecycle,
				"mutation=$failedMutation"
			)
			assertTrue(
				reporter.matchesAuthoritativePresentationVersion(receipt.version),
				"mutation=$failedMutation"
			)
			assertEquals(1, dispatcher.pendingHostEffectCount, "mutation=$failedMutation")
			assertEquals(
				listOf(readerNativePresentationApplication(readerPresentationDecision(receipt.postState))),
				committedApplications,
				"mutation=$failedMutation"
			)

			assertFalse(
				dispatcher.synchronizeComposeModel(
					epoch = reporter.captureEpoch(),
					version = preVersion,
					state = preState,
					shellCoverVisible = false,
					decision = readerPresentationDecision(preState)
				),
				"mutation=$failedMutation"
			)
			assertEquals(0, dispatcher.pendingHostEffectCount, "mutation=$failedMutation")
			assertTrue(
				reporter.matchesAuthoritativePresentationVersion(receipt.version),
				"mutation=$failedMutation"
			)
			assertEquals(
				ReaderPresentationHostMutation.entries.take(index + 1) +
					ReaderPresentationHostMutation.entries,
				mutations,
				"mutation=$failedMutation"
			)
			assertTrue(
				dispatcher.synchronizeComposeModel(
					epoch = reporter.captureEpoch(),
					version = receipt.version,
					state = receipt.postState,
					shellCoverVisible = false,
					decision = readerPresentationDecision(receipt.postState)
				)
			)
			assertEquals(
				ReaderPresentationHostMutation.entries.take(index + 1) +
					ReaderPresentationHostMutation.entries,
				mutations,
				"mutation=$failedMutation"
			)
		}
	}

	@Test
	fun returnedReceiptSupersedesAnUnfinishedOlderVisualProjection() {
		val binding = completeBinding("returned-receipt-host-effect-order")
		val preState = ReaderPresentationState(binding = binding)
		val controller = ReaderController(
			state = ReaderControllerState(
				readerSessionGeneration = 79L,
				presentation = preState
			),
			presentationEventSequence = 4L
		)
		val firstStep = controller.onPresentationEvent(
			ReaderPresentationEvent.NativePageRequested
		)
		val firstReceipt = assertNotNull(firstStep.presentationReceipt)
		val secondReceipt = assertNotNull(
			firstStep.controller.onPresentationEvent(
				ReaderPresentationEvent.Cancel
			).presentationReceipt
		)
		val reporter = reporter(controller.presentationVersion, binding, preState)
		val delivery = delivery(controller.presentationVersion, binding, preState.lifecycle)
		val mutations = mutableListOf<Pair<Long, ReaderPresentationHostMutation>>()
		var applyingSequence = -1L
		var injected = false
		val applier = ReaderPresentationHostEffectApplier(
			commitHostModel = {
				mutations += applyingSequence to ReaderPresentationHostMutation.Model
			},
			applyViewerDecision = { _, _ ->
				mutations += applyingSequence to ReaderPresentationHostMutation.ViewerDecision
			},
			applyShellCoverLayer = {
				mutations += applyingSequence to ReaderPresentationHostMutation.ShellCoverLayer
			},
			applyViewerCoverVisibility = {
				mutations += applyingSequence to ReaderPresentationHostMutation.ViewerCoverVisibility
			},
			applyNativeCoverVisibility = {
				mutations += applyingSequence to ReaderPresentationHostMutation.NativeCoverVisibility
			},
			afterMutation = { mutation ->
				if (!injected && mutation == ReaderPresentationHostMutation.ViewerDecision) {
					injected = true
					error("interrupt first returned receipt")
				}
			}
		)
		val dispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = reporter,
			lifecycleDelivery = delivery,
			applyHostEffect = { effect ->
				applyingSequence = effect.identity.version.eventSequence
				applier.apply(effect)
			}
		)
		val epoch = reporter.captureEpoch()

		assertNotNull(dispatcher.consumeReturned(epoch, firstReceipt))
		assertEquals(1, dispatcher.pendingHostEffectCount)
		assertNotNull(dispatcher.consumeReturned(epoch, secondReceipt))

		assertEquals(0, dispatcher.pendingHostEffectCount)
		assertEquals(
			ReaderPresentationHostMutation.entries.take(2).map {
				firstReceipt.version.eventSequence to it
			} + ReaderPresentationHostMutation.entries.map {
				secondReceipt.version.eventSequence to it
			},
			mutations
		)
	}

	@Test
	fun composeSynchronizationRejectsAModelDecisionMismatchWithoutPartialCommit() {
		val binding = completeBinding("compose-model-decision-mismatch")
		val preState = ReaderPresentationState(binding = binding)
		val preVersion = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 82L,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = 3L
		)
		val reporter = reporter(preVersion, binding, preState)
		val delivery = delivery(preVersion, binding, preState.lifecycle)
		val applied = mutableListOf<ReaderPresentationHostEffect>()
		val dispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = reporter,
			lifecycleDelivery = delivery,
			applyHostEffect = applied::add
		)
		val nextState = preState.copy(
			lifecycle = ReaderPresentationLifecycleState.Background
		)
		val nextVersion = preVersion.copy(eventSequence = 4L)
		val epoch = reporter.captureEpoch()

		assertFalse(
			dispatcher.synchronizeComposeModel(
				epoch = epoch,
				version = nextVersion,
				state = nextState,
				shellCoverVisible = false,
				decision = readerPresentationDecision(preState)
			)
		)
		assertFalse(reporter.matchesAuthoritativePresentationVersion(nextVersion))
		assertTrue(applied.isEmpty())
		assertTrue(
			dispatcher.synchronizeComposeModel(
				epoch = epoch,
				version = nextVersion,
				state = nextState,
				shellCoverVisible = false,
				decision = readerPresentationDecision(nextState)
			)
		)
		assertTrue(reporter.matchesAuthoritativePresentationVersion(nextVersion))
		assertEquals(1, applied.size)
	}

	@Test
	fun dispatcherDropsFailedHostEffectsWhenTheAuthoritativeEpochChanges() {
		val oldBinding = completeBinding("old-host-effect-epoch")
		val oldState = ReaderPresentationState(binding = oldBinding)
		val oldVersion = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 80L,
			publicationIdentity = oldBinding.publicationIdentity,
			eventSequence = 5L
		)
		val reporter = reporter(oldVersion, oldBinding, oldState)
		val delivery = delivery(oldVersion, oldBinding, oldState.lifecycle)
		val attemptedEpochs = mutableListOf<Long>()
		var oldFailureInjected = false
		val dispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = reporter,
			lifecycleDelivery = delivery,
			applyHostEffect = { effect ->
				attemptedEpochs += effect.identity.hostEpoch
				if (!oldFailureInjected) {
					oldFailureInjected = true
					error("old epoch host failure")
				}
			}
		)
		val oldEpoch = reporter.captureEpoch()
		val event = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.VisibilityLost
		)
		val oldReceipt = assertNotNull(
			ReaderController(
				state = ReaderControllerState(
					readerSessionGeneration = oldVersion.readerSessionGeneration,
					presentation = oldState
				),
				presentationEventSequence = oldVersion.eventSequence
			).onPresentationEvent(event).presentationReceipt
		)
		delivery.observe(event.event)
		assertNotNull(
			delivery.retry { attempted ->
				dispatcher.dispatch(attempted) { oldReceipt }
			}
		)
		assertEquals(1, dispatcher.pendingHostEffectCount)

		val newBinding = completeBinding("new-host-effect-epoch")
		val newState = ReaderPresentationState(binding = newBinding)
		val newVersion = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 81L,
			publicationIdentity = newBinding.publicationIdentity,
			eventSequence = 0L
		)
		reporter.reset(
			expectedReaderSessionGeneration = newVersion.readerSessionGeneration,
			minimumComposeVersion = newVersion,
			initialPresentationState = newState
		)
		assertTrue(reporter.bindPublication(newBinding))
		delivery.reset(newVersion, observedWindowVisible = null, initialLifecycle = newState.lifecycle)
		assertTrue(delivery.bindPublication(newBinding.publicationIdentity))
		val newEpoch = reporter.captureEpoch()

		assertTrue(
			dispatcher.synchronizeComposeModel(
				epoch = newEpoch,
				version = newVersion,
				state = newState,
				shellCoverVisible = false,
				decision = readerPresentationDecision(newState)
			)
		)
		assertEquals(listOf(oldEpoch, newEpoch), attemptedEpochs)
		assertEquals(0, dispatcher.pendingHostEffectCount)
	}

	@Test
	fun hostEffectApplierNeverResumesPartialWorkFromAnOlderEpoch() {
		val oldDecision = readerPresentationDecision(ReaderPresentationState())
		val newDecision = readerPresentationDecision(
			ReaderPresentationState(lifecycle = ReaderPresentationLifecycleState.Background)
		)
		val mutations = mutableListOf<Pair<String, ReaderPresentationHostMutation>>()
		fun label(decision: paige.navic.reader.ReaderPresentationDecision): String =
			if (decision == oldDecision) "old" else "new"
		var injected = false
		val applier = ReaderPresentationHostEffectApplier(
			commitHostModel = { application ->
				mutations += label(application.decision) to ReaderPresentationHostMutation.Model
			},
			applyViewerDecision = { decision, _ ->
				mutations += label(decision) to ReaderPresentationHostMutation.ViewerDecision
			},
			applyShellCoverLayer = { application ->
				mutations += label(application.decision) to ReaderPresentationHostMutation.ShellCoverLayer
			},
			applyViewerCoverVisibility = { application ->
				mutations += label(application.decision) to ReaderPresentationHostMutation.ViewerCoverVisibility
			},
			applyNativeCoverVisibility = { application ->
				mutations += label(application.decision) to ReaderPresentationHostMutation.NativeCoverVisibility
			},
			afterMutation = { mutation ->
				if (!injected && mutation == ReaderPresentationHostMutation.Model) {
					injected = true
					error("old epoch interrupted")
				}
			}
		)
		val oldEffect = ReaderPresentationHostEffect(
			identity = ReaderPresentationHostEffectIdentity(
				hostEpoch = 90L,
				version = ReaderPresentationReceiptVersion(90L, null, 1L)
			),
			event = null,
			decision = oldDecision,
			rendererLossCancellationIdentity = null
		)
		val newEffect = oldEffect.copy(
			identity = ReaderPresentationHostEffectIdentity(
				hostEpoch = 91L,
				version = ReaderPresentationReceiptVersion(91L, null, 0L)
			),
			decision = newDecision
		)

		assertFailsWith<IllegalStateException> { applier.apply(oldEffect) }
		applier.apply(newEffect)
		applier.apply(oldEffect)

		assertEquals(
			listOf("old" to ReaderPresentationHostMutation.Model) +
				ReaderPresentationHostMutation.entries.map { "new" to it },
			mutations
		)
	}

	@Test
	fun productionDispatcherCommitsAuthoritativeModelBeforeFailedHostApplication() {
		val binding = completeBinding("model-before-host-effect")
		val preState = ReaderPresentationState(binding = binding)
		val controller = ReaderController(
			state = ReaderControllerState(
				readerSessionGeneration = 50L,
				presentation = preState
			),
			presentationEventSequence = 5L
		)
		val preVersion = controller.presentationVersion
		val event = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.VisibilityLost
		)
		val exactReceipt = assertNotNull(
			controller.onPresentationEvent(event).presentationReceipt
		)
		val reporter = reporter(preVersion, binding, preState)
		val delivery = delivery(preVersion, binding)
		val applied = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
		val dispatcher = ReaderPresentationReceiptDispatcher(reporter, delivery) { decision, _ ->
			applied += decision
			error("failure after host mutation")
		}
		delivery.observe(event.event)

		val committed = delivery.retry { attempted ->
			dispatcher.dispatch(attempted) { exactReceipt }
		}

		assertNotNull(committed)
		assertEquals(0, delivery.pendingEventCount)
		assertTrue(reporter.matchesAuthoritativePresentationVersion(exactReceipt.version))
		assertEquals(binding, reporter.lastReportedBinding)
		assertEquals(listOf(readerPresentationDecision(exactReceipt.postState)), applied)
	}

	@Test
	fun productionDispatcherRejectsInvalidVisibilityReceiptsWithoutPartialMutation() {
		val binding = completeBinding("production-invalid-visibility")
		val wrongBinding = completeBinding("production-invalid-publication")
		val floor = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 20L,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = 5L
		)
		val reporter = reporter(floor, binding)
		val delivery = delivery(floor, binding)
		val input = InputHarness(binding)
		val activeGestureId = input.beginActivePointer(downTimeMillis = 100L)
		val decisions = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
		val dispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = reporter,
			lifecycleDelivery = delivery
		) { decision, _ ->
			decisions += decision
			input.apply(decision)
		}
		val event = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.VisibilityLost
		)
		val background = ReaderPresentationState(
			binding = binding,
			lifecycle = ReaderPresentationLifecycleState.Background
		)
		val invalidReceipts = listOf(
			lifecycleReceipt(
				event,
				background,
				floor.copy(eventSequence = 6L),
				ReaderPresentationEventDisposition.Rejected
			),
			lifecycleReceipt(
				event,
				background,
				floor.copy(eventSequence = 6L),
				ReaderPresentationEventDisposition.Stale
			),
			lifecycleReceipt(
				event,
				background.copy(lifecycle = ReaderPresentationLifecycleState.Foreground),
				floor.copy(eventSequence = 6L)
			),
			lifecycleReceipt(
				ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost),
				background,
				floor.copy(eventSequence = 6L)
			),
			lifecycleReceipt(
				event,
				background,
				floor.copy(readerSessionGeneration = 19L, eventSequence = 6L)
			),
			lifecycleReceipt(
				event,
				background.copy(binding = wrongBinding),
				floor.copy(
					publicationIdentity = wrongBinding.publicationIdentity,
					eventSequence = 6L
				)
			),
			lifecycleReceipt(event, background, floor.copy(eventSequence = 4L)),
			lifecycleReceipt(
				ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost),
				background,
				floor
			)
		)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)

		invalidReceipts.forEach { invalid ->
			assertNull(
				delivery.retry { attempted ->
					assertEquals(event, attempted)
					dispatcher.dispatch(attempted) { invalid }
				}
			)
			assertEquals(1, delivery.pendingEventCount)
			assertTrue(decisions.isEmpty())
			assertNull(reporter.lastReportedBinding)
			assertFalse(reporter.matchesAuthoritativePresentationVersion(invalid.version))
			assertEquals(1, input.host.contentGestureTokenCount())
			assertTrue(input.published.isEmpty())
			assertTrue(input.cancellationPort.calls.isEmpty())
		}

		val controller = ControllerHarness(
			controller(binding, readerSessionGeneration = 20L, eventSequence = 5L)
		)
		val accepted = assertNotNull(
			delivery.retry { attempted ->
				dispatcher.dispatch(attempted) { event -> controller.dispatch(event) }
			}
		)
		assertEquals(ReaderPresentationEventDisposition.Accepted, accepted.disposition)
		assertEquals(0, delivery.pendingEventCount)
		assertEquals(binding, reporter.lastReportedBinding)
		assertTrue(reporter.matchesAuthoritativePresentationVersion(accepted.version))
		assertEquals(ReaderPresentationLifecycleState.Background, accepted.postState.lifecycle)
		assertEquals(
			listOf(readerPresentationDecision(accepted.postState)),
			decisions
		)
		assertEquals(
			listOf(activeGestureId to ReaderPageGestureTerminalOutcome.CancelledLifecycle),
			input.published
		)
		assertEquals(4, input.cancellationPort.calls.size)
	}

	@Test
	fun productionDispatcherRetainsInvalidRendererAndTerminalWorkThenCommitsExactlyOnce() {
		val rendererBinding = completeBinding("production-renderer-loss")
		val rendererCleanup = ReaderRendererCleanupOwnership(
			ReaderPresentationToken(30L),
			rendererBinding
		)
		val rendererController = ControllerHarness(
			controller(
				binding = rendererBinding,
				readerSessionGeneration = 21L,
				cleanupOwnership = listOf(rendererCleanup),
				eventSequence = 10L
			)
		)
		val rendererReporter = reporter(
			rendererController.controller.presentationVersion,
			rendererBinding,
			rendererController.controller.state.presentation
		)
		val rendererDelivery = delivery(rendererController.controller.presentationVersion, rendererBinding)
		val rendererDecisions = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
		val rendererDispatcher = ReaderPresentationReceiptDispatcher(
			rendererReporter,
			rendererDelivery
		) { decision, _ -> rendererDecisions += decision }
		val rendererEvent = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.RendererLost
		)
		rendererDelivery.observe(ReaderPresentationLifecycleEvent.RendererLost)
		val rejectedRenderer = lifecycleReceipt(
			event = rendererEvent,
			postState = rendererController.controller.state.presentation.copy(
				rendererCleanupOwnership = emptyList()
			),
			version = rendererController.controller.presentationVersion.copy(eventSequence = 11L),
			disposition = ReaderPresentationEventDisposition.Rejected
		)
		assertNull(
			rendererDelivery.retry { attempted ->
				rendererDispatcher.dispatch(attempted) { rejectedRenderer }
			}
		)
		assertEquals(1, rendererDelivery.pendingEventCount)
		assertTrue(rendererDecisions.isEmpty())
		assertNull(rendererReporter.lastReportedBinding)

		val acceptedRenderer = assertNotNull(
			rendererDelivery.retry { attempted ->
				rendererDispatcher.dispatch(attempted) { event ->
						rendererController.dispatch(event)
					}
			}
		)
		assertEquals(0, rendererDelivery.pendingEventCount)
		assertTrue(acceptedRenderer.postState.rendererCleanupOwnership.isEmpty())
		assertEquals(
			listOf(readerPresentationDecision(acceptedRenderer.postState)),
			rendererDecisions
		)
		assertTrue(rendererReporter.matchesAuthoritativePresentationVersion(acceptedRenderer.version))

		val terminalBinding = completeBinding("production-terminal")
		val terminalCleanup = ReaderRendererCleanupOwnership(
			ReaderPresentationToken(31L),
			terminalBinding
		)
		val terminalController = ControllerHarness(
			controller(
				binding = terminalBinding,
				readerSessionGeneration = 22L,
				cleanupOwnership = listOf(terminalCleanup),
				eventSequence = 20L
			)
		)
		val terminalReporter = reporter(
			terminalController.controller.presentationVersion,
			terminalBinding,
			terminalController.controller.state.presentation
		)
		val terminalDelivery = delivery(terminalController.controller.presentationVersion, terminalBinding)
		val terminalDecisions = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
		val terminalDispatcher = ReaderPresentationReceiptDispatcher(
			terminalReporter,
			terminalDelivery
		) { decision, _ -> terminalDecisions += decision }
		val terminalEvent = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.PublicationClosed
		)
		terminalDelivery.observe(ReaderPresentationLifecycleEvent.PublicationClosed)
		val invalidTerminal = lifecycleReceipt(
			event = terminalEvent,
			postState = terminalController.controller.state.presentation,
			version = terminalController.controller.presentationVersion.copy(eventSequence = 21L)
		)
		assertNull(
			terminalDelivery.retry { attempted ->
				terminalDispatcher.dispatch(attempted) { invalidTerminal }
			}
		)
		assertEquals(1, terminalDelivery.pendingEventCount)
		assertTrue(terminalDecisions.isEmpty())
		assertNull(terminalReporter.lastReportedBinding)

		val acceptedTerminal = assertNotNull(
			terminalDelivery.retry { attempted ->
				terminalDispatcher.dispatch(attempted) { event ->
						terminalController.dispatch(event)
					}
			}
		)
		assertEquals(ReaderPresentationLifecycleState.Destroyed, acceptedTerminal.postState.lifecycle)
		assertEquals(0, terminalDelivery.pendingEventCount)
		assertNull(terminalReporter.lastReportedBinding)
		assertEquals(
			listOf(readerPresentationDecision(acceptedTerminal.postState)),
			terminalDecisions
		)
		assertEquals(1, terminalController.effects.size)
		assertIs<ReaderPresentationEffect.ReleaseStalePresentation>(terminalController.effects.single())
		assertNull(terminalDelivery.retry { error("terminal event was replayed") })
	}

	@Test
	fun productionDispatcherContainsCallbackFailuresAndKeepsLifecycleAndGeneralReceiptsSeparate() {
		val binding = completeBinding("production-callback-failure")
		val floor = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 23L,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = 5L
		)
		val reporter = reporter(floor, binding)
		val delivery = delivery(floor, binding)
		val decisions = mutableListOf<paige.navic.reader.ReaderPresentationDecision>()
		val dispatcher = ReaderPresentationReceiptDispatcher(
			reporter,
			delivery
		) { decision, _ -> decisions += decision }
		val lifecycleEvent = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.VisibilityLost
		)
		val acceptedLifecycle = lifecycleReceipt(
			event = lifecycleEvent,
			postState = ReaderPresentationState(
				binding = binding,
				lifecycle = ReaderPresentationLifecycleState.Background
			),
			version = floor.copy(eventSequence = 7L)
		)
		delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)

		assertNull(
			delivery.retry { attempted ->
				dispatcher.dispatch(attempted) { error("controller callback failed") }
			}
		)
		assertEquals(1, delivery.pendingEventCount)
		assertTrue(decisions.isEmpty())
		assertNull(reporter.lastReportedBinding)

		val throwingApplyDispatcher = ReaderPresentationReceiptDispatcher(
			reporter,
			delivery
		) { _, _ -> error("decision application failed") }
		assertNull(
			delivery.retry { attempted ->
				throwingApplyDispatcher.dispatch(attempted) { acceptedLifecycle }
			}
		)
		assertEquals(1, delivery.pendingEventCount)
		assertTrue(decisions.isEmpty())
		assertNull(reporter.lastReportedBinding)
		assertFalse(reporter.matchesAuthoritativePresentationVersion(acceptedLifecycle.version))

		val generalEvent = ReaderPresentationEvent.Retry
		val generalReceipt = ReaderPresentationEventReceipt(
			event = generalEvent,
			preVersion = floor,
			version = floor.copy(eventSequence = 6L),
			disposition = ReaderPresentationEventDisposition.Rejected,
			postState = ReaderPresentationState(binding = binding),
			effects = emptyList()
		)
		val wrongGeneralReceipt = generalReceipt.copy(
			event = ReaderPresentationEvent.PublicationOpened(binding)
		)
		assertNull(dispatcher.dispatch(generalEvent) { wrongGeneralReceipt })
		assertEquals(1, delivery.pendingEventCount)
		assertTrue(decisions.isEmpty())
		assertNull(reporter.lastReportedBinding)
		assertNotNull(dispatcher.dispatch(generalEvent) { generalReceipt })
		assertEquals(1, delivery.pendingEventCount)
		assertEquals(
			listOf(readerPresentationDecision(generalReceipt.postState)),
			decisions
		)
		assertTrue(reporter.matchesAuthoritativePresentationVersion(generalReceipt.version))

		assertNotNull(
			delivery.retry { attempted ->
				dispatcher.dispatch(attempted) { acceptedLifecycle }
			}
		)
		assertEquals(0, delivery.pendingEventCount)
		assertEquals(
			listOf(
				readerPresentationDecision(generalReceipt.postState),
				readerPresentationDecision(acceptedLifecycle.postState)
			),
			decisions
		)
		assertTrue(reporter.matchesAuthoritativePresentationVersion(acceptedLifecycle.version))
	}

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
		val dispatch = source
			.substringAfter("fun dispatchPresentationEvent(")
			.substringBefore("private fun consumeReturnedReceipt(")
		val finalLifecycle = source
			.substringAfter("private fun beginFinalHostLifecycle(")
			.substringBefore("private fun closePhysicalPointerDelivery(")

		assertContains(reporter, "presentationLifecycleDelivery.observe(event)")
		assertContains(reporter, "retryPresentationLifecycleDelivery()")
		assertContains(unsafeRenderer, "ReaderPresentationLifecycleEvent.RendererLost")
		assertContains(
			unsafeRenderer,
			"val cancellationIdentity = ReaderRendererLossCancellationIdentity("
		)
		assertContains(
			unsafeRenderer,
			"pendingRendererLossCancellationIdentity = cancellationIdentity"
		)
		assertContains(
			unsafeRenderer,
			"rendererLossCancellationIdentity = cancellationIdentity"
		)
		assertContains(epoch, "presentationLifecycleDelivery.reset(")
		assertContains(callbackRebind, "retryPresentationLifecycleDelivery()")
		assertContains(dispatch, "presentationReceiptDispatcher.dispatch(")
		assertContains(
			dispatch,
			"rendererLossCancellationIdentity = pendingRendererLossCancellationIdentity.takeIf"
		)
		assertFalse(dispatch.contains("presentationBindingReporter.consumeReceipt("))
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

		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationEventReceipt {
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

		override fun cancelRendererWork(reason: ReaderPageLifecycleCancellationReason) = Unit
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
			apply(readerPresentationDecision(receipt.postState))
		}

		fun apply(decision: paige.navic.reader.ReaderPresentationDecision) {
			host.updateInputPolicies(
				presentationInputPolicy = decision.inputPolicy,
				localSafetyPolicy = policy,
				nativeTapContinuationIdentity = null
			)
		}
	}

	private fun reporter(
		version: ReaderPresentationReceiptVersion,
		binding: ReaderPresentationBinding,
		state: ReaderPresentationState = ReaderPresentationState(binding = binding),
		shellCoverVisible: Boolean = false
	): ReaderPresentationBindingReporter = ReaderPresentationBindingReporter().also { reporter ->
		reporter.reset(
			expectedReaderSessionGeneration = version.readerSessionGeneration,
			minimumComposeVersion = version,
			initialPresentationState = state,
			initialShellCoverVisible = shellCoverVisible
		)
		assertTrue(reporter.bindPublication(binding))
	}

	private fun delivery(
		version: ReaderPresentationReceiptVersion,
		binding: ReaderPresentationBinding,
		initialLifecycle: ReaderPresentationLifecycleState =
			ReaderPresentationLifecycleState.Foreground
	): ReaderPresentationLifecycleDelivery = ReaderPresentationLifecycleDelivery().also { delivery ->
		delivery.reset(
			version,
			observedWindowVisible = null,
			initialLifecycle = initialLifecycle
		)
		assertTrue(delivery.bindPublication(binding.publicationIdentity))
	}

	private fun delivery(
		controller: ReaderController,
		binding: ReaderPresentationBinding
	): ReaderPresentationLifecycleDelivery = delivery(controller.presentationVersion, binding)

	private fun settledState(
		binding: ReaderPresentationBinding,
		cleanupOwnership: List<ReaderRendererCleanupOwnership>
	): ReaderPresentationState {
		val proof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 1L,
			viewportWidth = 100,
			viewportHeight = 200,
			rasterGeneration = checkNotNull(binding.rasterGeneration),
			textureGeneration = checkNotNull(binding.textureGeneration)
		)
		return ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			binding = binding,
			rendererCleanupOwnership = cleanupOwnership
		)
	}

	private fun controller(
		binding: ReaderPresentationBinding,
		readerSessionGeneration: Long,
		cleanupOwnership: List<ReaderRendererCleanupOwnership> = emptyList(),
		eventSequence: Long = 0L
	): ReaderController = ReaderController(
		state = ReaderControllerState(
			readerSessionGeneration = readerSessionGeneration,
			presentation = ReaderPresentationState(
				binding = binding,
				rendererCleanupOwnership = cleanupOwnership
			)
		),
		presentationEventSequence = eventSequence
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
		preVersion = version.copy(
			eventSequence = (version.eventSequence - 1L).coerceAtLeast(0L)
		),
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
