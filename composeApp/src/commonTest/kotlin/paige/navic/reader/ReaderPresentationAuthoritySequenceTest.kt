package paige.navic.reader

import java.io.File
import paige.navic.ui.screens.reader.readerPreparationCancelCallback
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPresentationAuthoritySequenceTest {
	@Test
	fun semanticShellIntentRetainsSettledNativeShadowUntilCoverCommitProof() {
		val locator = ReaderLocator(
			href = "chapter.xhtml",
			progress = 0.0,
			pageIndex = 0,
			pageCount = 10
		)
		val settled = settledNativePresentationState()
		val controller = ReaderController(
			state = ReaderControllerState(
				chrome = ReaderChromeState(currentLocator = locator),
				shellCoverVisible = false,
				nativeShellCoverUrl = "cover://available",
				nativeShellCoverReturnLocatorKey = readerNativeShellCoverReturnLocatorKey(locator),
				canReturnToShellCover = true,
				presentation = settled
			)
		)

		val step = controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
		)

		val pending = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			step.controller.state.presentation.authority
		)
		assertEquals(settled.binding, pending.binding)
		assertEquals(
			assertIs<ReaderPresentationAuthority.SettledNativePage>(settled.authority).frame,
			pending.retainedFrame.frameOwner
		)
		assertTrue(step.controller.state.shellCoverVisible, "Legacy shell behavior remains active in shadow mode")
		assertEquals(ReaderPresentationLayer.NativePage, step.controller.state.presentationDecision.layer)
		assertIs<ReaderPresentationInputPolicy.ChromeOnly>(
			step.controller.state.presentationDecision.inputPolicy
		)
	}

	@Test
	fun typedVisibilityRoundTripRetainsPresentationIdentity() {
		val settled = settledNativePresentationState()
		val controller = ReaderController(ReaderControllerState(presentation = settled))

		val hidden = controller.onPresentationEvent(
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost)
		).controller
		val restored = hidden.onPresentationEvent(
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored)
		).controller

		assertEquals(settled.binding, hidden.state.presentation.binding)
		assertEquals(settled.authority, hidden.state.presentation.authority)
		assertIs<ReaderPresentationInputPolicy.ChromeOnly>(hidden.state.presentationDecision.inputPolicy)
		assertEquals(settled.binding, restored.state.presentation.binding)
		assertEquals(settled.authority, restored.state.presentation.authority)
		assertIs<ReaderPresentationInputPolicy.NativePage>(restored.state.presentationDecision.inputPolicy)
	}

	@Test
	fun ordinaryRelocationRetainsPageUntilTargetProofThenCommitsCoverAgainstTarget() {
		val settled = settledNativePresentationState()
		val bindingA = requireNotNull(settled.binding)
		val frameA = assertIs<ReaderPresentationAuthority.SettledNativePage>(settled.authority).frame
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = bindingA.foliateSessionId,
				commitSequence = 5L
			),
			rasterGeneration = 23L,
			textureGeneration = 29L,
			preparationGeneration = 31L
		)
		val controllerA = ReaderController(ReaderControllerState(presentation = settled))

		val moved = controllerA.onPresentationEvent(
			ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		)
		assertEquals(bindingB, moved.controller.state.presentation.binding)
		assertEquals(frameA, moved.controller.state.presentationDecision.frameOwner)
		assertEquals(ReaderPagePreparationFacts(), moved.controller.state.presentation.preparationFacts)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			assertIs<ReaderPresentationInputPolicy.NativePage>(
				moved.controller.state.presentationDecision.inputPolicy
			).policy.newPointer
		)
		assertTrue(moved.presentationEffects.isEmpty())

		val ignoredCover = moved.controller.onPresentationEvent(
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 37L)
		)
		assertEquals(moved.controller.state.presentation, ignoredCover.controller.state.presentation)
		assertEquals(ReaderRequiredTransition.None, ignoredCover.controller.state.presentationDecision.requiredTransition)

		val prepared = ignoredCover.controller.onPresentationEvent(
			ReaderPresentationEvent.PreparationReported(
				bindingB,
				ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					generation = 31L,
					completedCount = 3,
					requiredCount = 3,
					readiness = ReaderPageReadinessState(
						rasterGeneration = ReaderChapterRasterGenerationState.Ready,
						decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
						textureDeck = ReaderTextureDeckState.Ready,
						pendingTextureDeck = ReaderTextureDeckState.Ready,
						interaction = ReaderPageInteractionState.Ready
					)
				)
			)
		)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			assertIs<ReaderPresentationInputPolicy.NativePage>(
				prepared.controller.state.presentationDecision.inputPolicy
			).policy.newPointer
		)

		val proofB = nativeProof(bindingB, frame = 12L)
		val presented = prepared.controller.onPresentationEvent(
			ReaderPresentationEvent.NativePagePresented(proofB)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofB)
			),
			presented.controller.state.presentation.authority
		)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					token = frameA.proof.transitionToken,
					binding = bindingA
				)
			),
			presented.presentationEffects
		)
		assertIs<ReaderPageNewPointerDecision.Accept>(
			assertIs<ReaderPresentationInputPolicy.NativePage>(
				presented.controller.state.presentationDecision.inputPolicy
			).policy.newPointer
		)

		val cover = presented.controller.onPresentationEvent(
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 37L)
		)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				token = ReaderPresentationToken(20L),
				binding = bindingB,
				coverGeneration = 37L
			),
			cover.controller.state.presentationDecision.requiredTransition
		)
	}

	@Test
	fun committedCoverDismissalWaitsForTargetFrameAndLaterReturnUsesFreshTransaction() {
		val settled = settledNativePresentationState()
		val bindingA = requireNotNull(settled.binding)
		val coverPending = readerPresentationReduce(
			settled,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 37L)
		)
		val coverTransition = assertIs<ReaderRequiredTransition.CommitShellCover>(
			coverPending.decision.requiredTransition
		)
		val coverProof = ReaderShellCoverCommitProof(
			token = coverTransition.token,
			binding = bindingA,
			coverGeneration = coverTransition.coverGeneration,
			presentedFrame = 12L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		val cover = readerPresentationReduce(
			coverPending.state,
			ReaderPresentationEvent.ShellCoverCommitted(coverProof)
		)
		val dismissal = readerPresentationReduce(
			cover.state,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			dismissal.decision.requiredTransition
		)
		assertEquals(ReaderPresentationToken(21L), nativeTransition.token)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), dismissal.decision.frameOwner)

		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(bindingA.foliateSessionId, 5L),
			rasterGeneration = 23L,
			textureGeneration = 29L,
			preparationGeneration = 31L
		)
		val moved = readerPresentationReduce(
			dismissal.state,
			ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		)
		assertEquals(bindingB, moved.state.binding)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), moved.decision.frameOwner)
		assertEquals(
			ReaderRequiredTransition.PresentNativePage(
				ReaderPresentationToken(21L),
				bindingB,
				direction = null
			),
			moved.decision.requiredTransition
		)

		val proofB = nativeProof(bindingB, frame = 13L).copy(
			transitionToken = ReaderPresentationToken(21L)
		)
		val presented = readerPresentationReduce(
			moved.state,
			ReaderPresentationEvent.NativePagePresented(proofB)
		)
		assertEquals(ReaderPresentationFrameOwner.NativePage(proofB), presented.decision.frameOwner)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					token = coverProof.token,
					binding = bindingA
				)
			),
			presented.effects
		)

		val staleCover = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.ShellCoverEntered(coverProof)
		)
		assertEquals(presented.state, staleCover.state)
		val returned = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 38L)
		)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				ReaderPresentationToken(22L),
				bindingB,
				coverGeneration = 38L
			),
			returned.decision.requiredTransition
		)
	}

	@Test
	fun preparationAliasRetriesStayBoundedThroughControllerEffectQueueAndLatestProof() {
		val bindingD = presentationBinding(session = "session-current", destinationSequence = 4L)
		val coverProof = ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(20L),
			binding = bindingD,
			coverGeneration = 37L,
			presentedFrame = 12L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		var controller = ReaderController(
			ReaderControllerState(
				shellCoverVisible = true,
				presentation = ReaderPresentationState(
					authority = ReaderPresentationAuthority.ShellCover(coverProof),
					binding = bindingD,
					nextTokenValue = 21L
				)
			)
		)
		val queue = ReaderPresentationEffectQueue()
		var maximumPendingEffects = 0

		fun dispatch(event: ReaderPresentationEvent): ReaderControllerStep {
			val step = controller.onPresentationEvent(event)
			controller = step.controller
			queue.retain(step.presentationEffects)
			maximumPendingEffects = maxOf(maximumPendingEffects, queue.pendingEffects().size)
			return step
		}

		val dismissal = dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
		val transition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			dismissal.controller.state.presentationDecision.requiredTransition
		)
		var latestBinding = bindingD
		repeat(32) {
			val replacement = latestBinding.copy(
				preparationGeneration = requireNotNull(latestBinding.preparationGeneration) + 1L
			)
			dispatch(ReaderPresentationEvent.BindingReplaced(latestBinding, replacement))
			latestBinding = replacement
		}

		assertEquals(0, maximumPendingEffects)
		assertTrue(queue.pendingEffects().isEmpty())
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			controller.state.presentation.authority
		)
		assertEquals(
			ReaderNativePagePresentationRequest(transition.token, latestBinding),
			pending.nativePresentationRequest
		)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), pending.retainedFrame)

		val latestProof = nativeProof(latestBinding, frame = 13L).copy(
			transitionToken = transition.token
		)
		val presented = dispatch(
			ReaderPresentationEvent.NativePagePresented(latestProof)
		)
		assertEquals(
			ReaderPresentationFrameOwner.NativePage(latestProof),
			presented.controller.state.presentationDecision.frameOwner
		)
		assertFalse(presented.controller.state.shellCoverVisible)
		assertEquals(1, queue.pendingEffects().size)
	}

	@Test
	fun stalePartialRelocationsCannotFillPresentationEffectQueue() {
		val current = presentationBinding(
			session = "session-current",
			destinationSequence = 40L
		).copy(rasterGeneration = null, textureGeneration = null)
		val request = ReaderNativePagePresentationRequest(
			ReaderPresentationToken(41L),
			current
		)
		var controller = ReaderController(
			ReaderControllerState(
				presentation = ReaderPresentationState(
					authority = ReaderPresentationAuthority.BlockingPreparation(
						retainedFrame = ReaderPresentationFrameOwner.Neutral,
						nativePresentationRequest = request
					),
					binding = current,
					nextTokenValue = 42L
				)
			)
		)
		val queue = ReaderPresentationEffectQueue(capacity = 2)

		repeat(32) { offset ->
			val stale = current.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					"session-current",
					offset.toLong() + 1L
				)
			)
			val step = controller.onPresentationEvent(
				ReaderPresentationEvent.FoliateRelocated(stale, acknowledgement = null)
			)
			controller = step.controller
			queue.retain(step.presentationEffects)
		}

		assertEquals(current, controller.state.presentation.binding)
		assertEquals(request, (controller.state.presentation.authority as
			ReaderPresentationAuthority.BlockingPreparation).nativePresentationRequest)
		assertTrue(queue.pendingEffects().isEmpty())
	}

	@Test
	fun repeatedProvisionalProfileFailureRetriesStayCoalesced() {
		val provisional = presentationBinding(
			session = "session-bootstrap",
			destinationSequence = 1L
		).copy(
			profileGeneration = 0L,
			rasterGeneration = null,
			textureGeneration = null
		)
		val request = ReaderNativePagePresentationRequest(
			ReaderPresentationToken(51L),
			provisional
		)
		val failedFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Failed,
			generation = requireNotNull(provisional.preparationGeneration),
			failure = ReaderPresentationFailureReason.PreparationFailed,
			retryable = true
		)
		var controller = ReaderController(
			ReaderControllerState(
				presentation = ReaderPresentationState(
					authority = ReaderPresentationAuthority.BlockingPreparation(
						retainedFrame = ReaderPresentationFrameOwner.Neutral,
						nativePresentationRequest = request
					),
					binding = provisional,
					preparationFacts = failedFacts,
					failure = ReaderDiagnosticPresentation.Failure(
						reason = ReaderPresentationFailureReason.PreparationFailed,
						retryable = true,
						cancellable = false
					),
					nextTokenValue = 52L
				)
			)
		)
		val queue = ReaderPresentationEffectQueue(capacity = 2)

		repeat(32) {
			val retried = controller.onPresentationEvent(ReaderPresentationEvent.Retry)
			controller = retried.controller
			queue.retain(retried.presentationEffects)
		}

		val freshRequest = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			controller.state.presentation.authority).nativePresentationRequest)
		assertEquals(ReaderPresentationToken(52L), freshRequest.token)
		assertEquals(provisional.preparationGeneration, freshRequest.retryAfterPreparationGeneration)
		assertEquals(1, queue.pendingEffects().size)
		assertEquals(
			ReaderPresentationEffect.RetryPreparation(freshRequest.token, provisional),
			queue.pendingEffects().single().effect
		)
	}

	@Test
	fun presentationEffectsDeduplicateByStableIdentityAndRequireSuccessfulAcknowledgement() {
		val settled = settledNativePresentationState()
		val staleBinding = presentationBinding(session = "session-stale", destinationSequence = 9L)
		val staleProof = nativeProof(staleBinding, frame = 12L)
		val step = ReaderController(ReaderControllerState(presentation = settled)).onPresentationEvent(
			ReaderPresentationEvent.NativePagePresented(staleProof)
		)
		val expectedEffect = ReaderPresentationEffect.ReleaseStalePresentation(
			token = staleProof.transitionToken,
			binding = staleBinding
		)
		assertEquals(listOf(expectedEffect), step.presentationEffects)

		val queue = ReaderPresentationEffectQueue(capacity = 2)
		val pending = queue.retain(step.presentationEffects).single()

		assertEquals(expectedEffect.identity(), pending.identity)
		assertEquals(expectedEffect, pending.effect)
		assertTrue(queue.retain(step.presentationEffects).isEmpty())
		assertEquals(listOf(pending), queue.pendingEffects())
		assertFalse(queue.acknowledge(pending.identity.copy(token = ReaderPresentationToken(99L))))
		assertEquals(listOf(pending), queue.pendingEffects())
		assertTrue(queue.acknowledge(pending.identity))
		assertFalse(queue.acknowledge(pending.identity))
		assertTrue(queue.retain(step.presentationEffects).isEmpty())
		assertTrue(queue.pendingEffects().isEmpty())
	}

	@Test
	fun presentationEffectQueueFailsExplicitlyWithoutDiscardingUnacknowledgedEffects() {
		val first = ReaderPresentationEffect.ReleaseStalePresentation(
			token = ReaderPresentationToken(30L),
			binding = presentationBinding(session = "session-first", destinationSequence = 30L)
		)
		val second = ReaderPresentationEffect.ReleaseStalePresentation(
			token = ReaderPresentationToken(31L),
			binding = presentationBinding(session = "session-second", destinationSequence = 31L)
		)
		val queue = ReaderPresentationEffectQueue(capacity = 1)
		val retained = queue.retain(listOf(first)).single()

		assertFailsWith<ReaderPresentationEffectQueueOverflowException> {
			queue.retain(listOf(second))
		}

		assertEquals(listOf(retained), queue.pendingEffects())
	}

	@Test
	fun platformHostContractCarriesAtomicDecisionTypedEventsAndStrictBindingFacts() {
		val common = sourceFile(
			"src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val screen = sourceFile(
			"src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val android = sourceFile(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val ios = sourceFile(
			"src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.ios.kt"
		).readText()

		listOf(common, android, ios).forEach { source ->
			assertContains(source, "presentationDecision: ReaderPresentationDecision")
			assertContains(source, "presentationState: ReaderPresentationState")
			assertContains(source, "presentationVersion: ReaderPresentationReceiptVersion")
			assertContains(source, "presentationShellCoverVisible: Boolean")
			assertContains(
				source,
				"onPresentationEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?"
			)
			assertContains(
				source,
				"onViewerAction: (KomikkuNavigationRegion) -> ReaderPresentationEventReceipt?"
			)
			assertContains(
				source,
				"onPageTurnBoundary: (ReaderPageTurnDirection) -> ReaderPresentationEventReceipt?"
			)
			assertContains(source, "presentationEffects: List<ReaderPendingPresentationEffect>")
			assertContains(
				source,
				"onPresentationEffectHandled: (ReaderPresentationEffectIdentity) -> Unit"
			)
			assertContains(source, "destinationCommitIdentity: ReaderDestinationCommitIdentity?")
			assertFalse(source.contains("pagePreparationCoverVisible"))
			assertFalse(source.contains("pagePreparationRetryKey"))
			assertFalse(source.contains("onPagePreparationStateChange"))
		}
		assertContains(screen, "presentationEffects = pendingPresentationEffects")
		assertContains(screen, "step.presentationReceipt")
		assertContains(screen, "pendingPresentationEffectQueue.acknowledge(identity)")
		assertContains(android, "fun setPresentationDecision(")
		assertContains(android, "presentationDecision?.let(presentationHostBridge::update)")
		assertContains(
			android,
			"onAuthoritativeHostEffect = presentationHostEffectApplier::apply"
		)
		assertFalse(android.contains("override fun applyPresentationDecision("))
		assertContains(android, "readerNativePresentationApplication(")
		assertContains(android, "applyViewerDecision = viewerContainer::applyPresentationDecision")
		assertContains(android, "presentationDecision = application.decision")
		assertContains(
			android,
			"rendererLossCancellationIdentity = rendererLossCancellationIdentity"
		)
		assertContains(android, "handlePresentationEffects(")
		assertContains(android, "releaseStalePresentationDeck(effect.binding)")
		assertFalse(android.contains("setPresentationShadow("))
		assertFalse(android.contains("reportPresentationShadowComparison("))
		assertFalse(android.contains("latestRasterPreparationState.presentation"))
		val iosBody = ios.substringAfter(") {")
		assertContains(iosBody, "LaunchedEffect(presentationEffects)")
		assertContains(iosBody, "onPresentationEffectHandled(pending.identity)")
		assertFalse(iosBody.contains("onPresentationEvent("))
	}

	@Test
	fun boundedCoverEntryFailureRetryAndCancelNeverHideFailureOrLeavePendingWork() {
		var controller = ReaderController(ReaderControllerState(presentation = settledNativePresentationState()))
		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationDecision {
			controller = controller.onPresentationEvent(event).controller
			controller.state.presentation.assertSequenceInvariants()
			return controller.state.presentationDecision
		}
		val retained = controller.state.presentationDecision.frameOwner
		val first = assertIs<ReaderRequiredTransition.CommitShellCover>(
			dispatch(ReaderPresentationEvent.ShellCoverRequested(37L)).requiredTransition)
		val failed = dispatch(ReaderPresentationEvent.TimedOut(first.token))
		assertEquals(retained, failed.frameOwner)
		val failure = assertIs<ReaderDiagnosticPresentation.Failure>(failed.diagnosticPresentation)
		assertTrue(failure.retryable)
		assertTrue(failure.cancellable)
		assertEquals(ReaderRequiredTransition.None, failed.requiredTransition)
		val retry = assertIs<ReaderRequiredTransition.CommitShellCover>(
			dispatch(ReaderPresentationEvent.Retry).requiredTransition)
		assertTrue(retry.token.value > first.token.value)
		repeat(3) { assertEquals(retry, dispatch(ReaderPresentationEvent.Retry).requiredTransition) }
		val cancelled = dispatch(ReaderPresentationEvent.Cancel)
		assertEquals(retained, cancelled.frameOwner)
		assertEquals(null, cancelled.pendingTransitionToken)
		assertEquals(ReaderRequiredTransition.None, cancelled.requiredTransition)
		val stale = ReaderShellCoverCommitProof(first.token, first.binding, first.coverGeneration,
			12L, 1200, 800)
		assertEquals(cancelled, dispatch(ReaderPresentationEvent.ShellCoverCommitted(stale)))
	}

	@Test
	fun textureOnlyReplacementRetainsOriginalCoverThenDismissesOnlyForExactSuccessor() {
		var state = committedCoverState()
		val cover = readerPresentationDecision(state).frameOwner
		val original = requireNotNull(state.binding)
		var latest = original
		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationReduction =
			readerPresentationReduce(state, event).also {
				state = it.state
				state.assertSequenceInvariants()
			}
		// Repeated page-deck completions cannot manufacture a newer shell receipt.
		repeat(3) {
			val successor = latest.copy(textureGeneration = requireNotNull(latest.textureGeneration) + 1L)
			val replacement = dispatch(ReaderPresentationEvent.BindingReplaced(latest, successor))
			assertEquals(cover, replacement.decision.frameOwner)
			assertEquals(successor, replacement.decision.targetBinding)
			assertEquals(ReaderPresentationInputPolicy.ShellCover, replacement.decision.inputPolicy)
			assertEquals(ReaderPreparationPresentation.Hidden, replacement.decision.preparationPresentation)
			assertEquals(ReaderRequiredTransition.None, replacement.decision.requiredTransition)
			assertEquals(null, replacement.decision.pendingTransitionToken)
			assertTrue(state.rendererCleanupOwnership.size <= 2)
			latest = successor
		}
		val firstRequest = dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
		val cancelled = dispatch(ReaderPresentationEvent.Cancel)
		assertEquals(cover, cancelled.decision.frameOwner)
		assertEquals(latest, cancelled.state.binding)
		assertEquals(ReaderRequiredTransition.None, cancelled.decision.requiredTransition)
		val requested = dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
		assertTrue(requireNotNull(requested.decision.pendingTransitionToken).value >
			requireNotNull(firstRequest.decision.pendingTransitionToken).value)
		val transition = assertIs<ReaderRequiredTransition.PresentNativePage>(requested.decision.requiredTransition)
		assertEquals(latest, transition.binding)
		assertEquals(cover, requested.decision.frameOwner)
		val exact = nativeProof(latest, 30L).copy(transitionToken = transition.token)
		listOf(nativeProof(original, 29L).copy(transitionToken = transition.token),
			exact.copy(transitionToken = ReaderPresentationToken(99L)),
			exact.copy(transitionToken = null)).forEach { wrong ->
			assertEquals(cover, dispatch(ReaderPresentationEvent.NativePagePresented(wrong)).decision.frameOwner)
			assertEquals(transition, readerPresentationDecision(state).requiredTransition)
		}
		val settled = dispatch(ReaderPresentationEvent.NativePagePresented(exact))
		assertEquals(ReaderPresentationFrameOwner.NativePage(exact), settled.decision.frameOwner)
		assertEquals(ReaderRequiredTransition.None, settled.decision.requiredTransition)
		assertTrue(state.rendererCleanupOwnership.isEmpty())
		assertEquals(1, settled.effects.filterIsInstance<ReaderPresentationEffect.ReleaseStalePresentation>()
			.count { it.binding == original })
	}

	@Test
	fun textureOnlyCoverReplacementDoesNotEraseRealPreparationFailure() {
		val cover = committedCoverState()
		val original = requireNotNull(cover.binding)
		val failedFacts = cover.preparationFacts.copy(phase = ReaderPagePreparationPhase.Failed,
			failure = ReaderPresentationFailureReason.PreparationFailed, retryable = true)
		val failed = readerPresentationReduce(cover, ReaderPresentationEvent.PreparationFailed(
			original, failedFacts, ReaderPresentationFailureReason.PreparationFailed, cancellable = false))
		val replacement = readerPresentationReduce(failed.state, ReaderPresentationEvent.BindingReplaced(
			original, original.copy(textureGeneration = requireNotNull(original.textureGeneration) + 1L)))
		assertEquals(failed.decision.frameOwner, replacement.decision.frameOwner)
		assertEquals(failed.state.failure, replacement.state.failure)
		assertEquals(failedFacts, replacement.state.preparationFacts)
		assertIs<ReaderDiagnosticPresentation.Failure>(replacement.decision.diagnosticPresentation)
		replacement.state.assertSequenceInvariants()
	}

	@Test
	fun invalidationAfterTextureReplacementReleasesAbandonedOwnersBeforeRecommittedCoverRefresh() {
		var state = committedCoverState()
		val proofA = assertIs<ReaderPresentationAuthority.ShellCover>(state.authority).proof
		val readyFacts = state.preparationFacts
		val bindingA = requireNotNull(state.binding)
		val bindingB = bindingA.copy(textureGeneration = requireNotNull(bindingA.textureGeneration) + 1L)
		val bindingC = bindingB.copy(viewportGeneration = bindingB.viewportGeneration + 1L,
			textureGeneration = requireNotNull(bindingB.textureGeneration) + 1L)
		val bindingD = bindingC.copy(textureGeneration = requireNotNull(bindingC.textureGeneration) + 1L)
		val reductions = mutableListOf<ReaderPresentationReduction>()
		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationReduction =
			readerPresentationReduce(state, event).also {
				state = it.state
				state.assertSequenceInvariants()
				reductions += it
			}
		dispatch(ReaderPresentationEvent.BindingReplaced(bindingA, bindingB))
		assertEquals(listOf(bindingA, bindingB), state.rendererCleanupOwnership.map { it.binding })
		val invalidationEvent = ReaderPresentationEvent.BindingReplaced(bindingB, bindingC)
		val invalidated = dispatch(invalidationEvent)
		assertEquals(ReaderPresentationFrameOwner.Neutral, invalidated.decision.frameOwner)
		val replayed = dispatch(invalidationEvent)
		assertEquals(ReaderPresentationEventDisposition.Idempotent, replayed.disposition)
		assertTrue(replayed.effects.isEmpty())
		val transition = assertIs<ReaderRequiredTransition.CommitShellCover>(
			dispatch(ReaderPresentationEvent.ShellCoverRequested(38L)).decision.requiredTransition)
		val proofC = ReaderShellCoverCommitProof(transition.token, bindingC,
			transition.coverGeneration, 20L, 1200, 800)
		dispatch(ReaderPresentationEvent.ShellCoverCommitted(proofC))
		val refreshed = dispatch(ReaderPresentationEvent.BindingReplaced(bindingC, bindingD))
		assertEquals(ReaderPresentationFrameOwner.ShellCover(proofC), refreshed.decision.frameOwner)
		assertEquals(listOf(bindingC, bindingD), state.rendererCleanupOwnership.map { it.binding })
		assertTrue(invalidated.state.rendererCleanupOwnership.isEmpty())
		assertEquals(listOf(
			ReaderPresentationEffect.ReleaseStalePresentation(proofA.token, bindingA),
			ReaderPresentationEffect.ReleaseStalePresentation(null, bindingB)), invalidated.effects)
		dispatch(ReaderPresentationEvent.PreparationReported(bindingD, readyFacts))
		val native = assertIs<ReaderRequiredTransition.PresentNativePage>(
			dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested).decision.requiredTransition)
		dispatch(ReaderPresentationEvent.NativePagePresented(
			nativeProof(bindingD, 21L).copy(transitionToken = native.token)))
		assertTrue(state.rendererCleanupOwnership.isEmpty())
		assertEquals(ReaderRequiredTransition.None, readerPresentationDecision(state).requiredTransition)
		assertEquals(bindingD, assertIs<ReaderPresentationAuthority.SettledNativePage>(state.authority).frame.proof.binding)
		assertIs<ReaderPageNewPointerDecision.Accept>(assertIs<ReaderPresentationInputPolicy.NativePage>(
			readerPresentationDecision(state).inputPolicy).policy.newPointer)
		assertEquals(listOf(bindingA, bindingB, bindingC), reductions.flatMap { it.effects }
			.filterIsInstance<ReaderPresentationEffect.ReleaseStalePresentation>().map { it.binding })
	}

	@Test
	fun textureOnlyCoverExceptionDoesNotRelaxOtherIdentityInvalidations() {
		val cover = committedCoverState()
		val original = requireNotNull(cover.binding)
		val texture = requireNotNull(original.textureGeneration) + 1L
		listOf(original.copy(textureGeneration = texture, viewportGeneration = original.viewportGeneration + 1L),
			original.copy(textureGeneration = texture, profileGeneration = original.profileGeneration + 1L),
			original.copy(textureGeneration = texture, rasterGeneration = requireNotNull(original.rasterGeneration) + 1L),
			original.copy(textureGeneration = texture, preparationGeneration = requireNotNull(original.preparationGeneration) + 1L)
		).forEach { invalid ->
			val result = readerPresentationReduce(cover, ReaderPresentationEvent.BindingReplaced(original, invalid))
			assertEquals(ReaderPresentationFrameOwner.Neutral, result.decision.frameOwner)
			assertEquals(invalid, result.state.binding)
			result.state.assertSequenceInvariants()
		}
		listOf(original.copy(publicationGeneration = original.publicationGeneration + 1L),
			original.copy(foliateSessionId = "sequence-other", destinationCommitIdentity =
				ReaderDestinationCommitIdentity("sequence-other", 1L))).forEach { invalid ->
			assertEquals(ReaderPresentationFrameOwner.Neutral,
				readerPresentationReduce(cover, ReaderPresentationEvent.PublicationOpened(invalid)).decision.frameOwner)
		}
		val unrelated = original.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(
			original.foliateSessionId, requireNotNull(original.destinationCommitIdentity).commitSequence + 1L))
		assertEquals(cover, readerPresentationReduce(cover,
			ReaderPresentationEvent.FoliateRelocated(unrelated, acknowledgement = null)).state)
	}

	@Test
	fun cancelAfterTextureReplacementCannotRestoreCoverWithOtherIdentityMismatch() {
		val cover = committedCoverState()
		val original = requireNotNull(cover.binding)
		val pending = readerPresentationReduce(cover, ReaderPresentationEvent.ShellCoverDismissalRequested).state
		val authority = assertIs<ReaderPresentationAuthority.BlockingPreparation>(pending.authority)
		val request = requireNotNull(authority.nativePresentationRequest)
		val successor = original.copy(textureGeneration = requireNotNull(original.textureGeneration) + 1L)
		listOf(
			successor.copy(foliateSessionId = "sequence-other", destinationCommitIdentity =
				ReaderDestinationCommitIdentity("sequence-other", 1L)),
			successor.copy(publicationGeneration = original.publicationGeneration + 1L),
			successor.copy(viewportGeneration = original.viewportGeneration + 1L),
			successor.copy(profileGeneration = original.profileGeneration + 1L),
			successor.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(original.foliateSessionId,
				requireNotNull(original.destinationCommitIdentity).commitSequence + 1L)),
			successor.copy(rasterGeneration = requireNotNull(original.rasterGeneration) + 1L),
			successor.copy(preparationGeneration = requireNotNull(original.preparationGeneration) + 1L)
		).forEach { invalid ->
			// A retained receipt is not evidence that an independently changed target is compatible.
			val mismatched = pending.copy(binding = invalid,
				authority = authority.copy(nativePresentationRequest = request.copy(binding = invalid)))
			val cancelled = readerPresentationReduce(mismatched, ReaderPresentationEvent.Cancel)
			assertEquals(ReaderPresentationFrameOwner.Neutral, cancelled.decision.frameOwner)
			assertIs<ReaderDiagnosticPresentation.Failure>(cancelled.decision.diagnosticPresentation)
			cancelled.state.assertSequenceInvariants()
		}
	}

	@Test
	fun attributedFreshCoverRetryAdmitsPartialFactsWithoutRewritingOriginalCoverProof() {
		var state = committedCoverState()
		val original = requireNotNull(state.binding)
		val cover = readerPresentationDecision(state).frameOwner
		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationReduction = readerPresentationReduce(state, event).also {
			state = it.state
			state.assertSequenceInvariants()
		}
		val target = original.copy(textureGeneration = requireNotNull(original.textureGeneration) + 1L)
		dispatch(ReaderPresentationEvent.BindingReplaced(original, target))
		dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
		dispatch(ReaderPresentationEvent.TimedOut(requireNotNull(readerPresentationDecision(state).pendingTransitionToken)))
		val retry = dispatch(ReaderPresentationEvent.Retry)
		val token = requireNotNull(retry.decision.pendingTransitionToken)
		val floor = requireNotNull(target.preparationGeneration)
		val partial = target.copy(rasterGeneration = null, textureGeneration = null,
			preparationGeneration = floor + 1L, profileGeneration = target.profileGeneration + 1L)
		val event = ReaderPresentationEvent.BindingReplaced(target, partial,
			ReaderPresentationRetryBindingAttribution(token, floor + 1L, partial.profileGeneration))
		val admitted = dispatch(event)
		assertEquals(ReaderPresentationEventDisposition.Accepted, admitted.disposition)
		assertEquals(cover, admitted.decision.frameOwner)
		val request = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(state.authority).nativePresentationRequest)
		assertEquals(token, request.token)
		assertEquals(floor, request.retryAfterPreparationGeneration)
		assertEquals(partial, request.binding)
		val replay = dispatch(event)
		assertEquals(admitted.state, replay.state)
		assertTrue(replay.effects.isEmpty())
		dispatch(ReaderPresentationEvent.PreparationReported(partial,
			ReaderPagePreparationFacts(phase = ReaderPagePreparationPhase.Idle, generation = floor + 1L)))
		val preparing = dispatch(ReaderPresentationEvent.PreparationReported(partial,
			ReaderPagePreparationFacts(phase = ReaderPagePreparationPhase.Preparing, generation = floor + 1L,
				completedCount = 1, requiredCount = 3)))
		assertIs<ReaderPreparationPresentation.Blocking>(preparing.decision.preparationPresentation)
		val completed = partial.copy(rasterGeneration = requireNotNull(target.rasterGeneration) + 1L,
			textureGeneration = requireNotNull(target.textureGeneration) + 1L)
		dispatch(ReaderPresentationEvent.BindingCompleted(partial, completed))
		assertEquals(cover, readerPresentationDecision(state).frameOwner)
		dispatch(ReaderPresentationEvent.PreparationReported(completed,
			settledNativePresentationState().preparationFacts.copy(generation = floor + 1L)))
		dispatch(ReaderPresentationEvent.NativePagePresented(nativeProof(completed, 25L).copy(transitionToken = token)))
		assertIs<ReaderPresentationAuthority.SettledNativePage>(state.authority)
		assertTrue(state.rendererCleanupOwnership.isEmpty())
		assertEquals(ReaderRequiredTransition.None, readerPresentationDecision(state).requiredTransition)
	}

	@Test
	fun attributedCoverRetryPartialRejectsLateAttemptsAndUnprovenIdentityChanges() {
		val cover = committedCoverState()
		val binding = requireNotNull(cover.binding)
		val entry = readerPresentationReduce(cover, ReaderPresentationEvent.ShellCoverDismissalRequested)
		val failed = readerPresentationReduce(entry.state,
			ReaderPresentationEvent.TimedOut(requireNotNull(entry.decision.pendingTransitionToken)))
		val retry1 = readerPresentationReduce(failed.state, ReaderPresentationEvent.Retry)
		val token1 = requireNotNull(retry1.decision.pendingTransitionToken)
		val failed1 = readerPresentationReduce(retry1.state, ReaderPresentationEvent.TimedOut(token1))
		val retry2 = readerPresentationReduce(failed1.state, ReaderPresentationEvent.Retry)
		val token2 = requireNotNull(retry2.decision.pendingTransitionToken)
		val floor = requireNotNull(binding.preparationGeneration)
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null,
			preparationGeneration = floor + 1L, profileGeneration = binding.profileGeneration + 1L)
		val attribution = ReaderPresentationRetryBindingAttribution(token2, floor + 1L, partial.profileGeneration)
		val exact = ReaderPresentationEvent.BindingReplaced(binding, partial, attribution)
		fun rejected(state: ReaderPresentationState, event: ReaderPresentationEvent.BindingReplaced) {
			val result = readerPresentationReduce(state, event)
			assertEquals(state, result.state)
			assertTrue(result.effects.isEmpty())
		}
		for (event in listOf(
			exact.copy(retryAttribution = null),
			exact.copy(retryAttribution = attribution.copy(token = token1)),
			exact.copy(retryAttribution = attribution.copy(preparationGeneration = floor + 2L)),
			exact.copy(retryAttribution = attribution.copy(profileGeneration = partial.profileGeneration + 1L)),
			exact.copy(binding = partial.copy(preparationGeneration = floor)),
			exact.copy(binding = partial.copy(rasterGeneration = binding.rasterGeneration)),
			exact.copy(binding = partial.copy(textureGeneration = binding.textureGeneration)),
			exact.copy(previousBinding = binding.copy(publicationGeneration = binding.publicationGeneration + 1L),
				binding = partial.copy(publicationGeneration = binding.publicationGeneration + 1L)),
			exact.copy(binding = partial.copy(viewportGeneration = binding.viewportGeneration + 1L)),
			exact.copy(binding = partial.copy(destinationCommitIdentity = binding.destinationCommitIdentity?.copy(commitSequence = 9L))),
			exact.copy(previousBinding = binding.copy(foliateSessionId = "retry-other",
				destinationCommitIdentity = binding.destinationCommitIdentity?.copy(foliateSessionId = "retry-other")),
				binding = partial.copy(foliateSessionId = "retry-other",
					destinationCommitIdentity = partial.destinationCommitIdentity?.copy(foliateSessionId = "retry-other")))
		)) rejected(retry2.state, event)
		val request = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(retry2.state.authority).nativePresentationRequest)
		for (state in listOf(
			retry2.state.copy(lifecycle = ReaderPresentationLifecycleState.Background),
			retry2.state.copy(lifecycle = ReaderPresentationLifecycleState.Destroyed),
			failed1.state,
			readerPresentationReduce(retry2.state, ReaderPresentationEvent.Cancel).state,
			retry2.state.copy(authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral, request)),
			retry2.state.copy(authority = ReaderPresentationAuthority.BlockingPreparation(
				readerPresentationDecision(cover).frameOwner, request.copy(retryAfterPreparationGeneration = null)))
		)) rejected(state, exact)
		assertEquals(ReaderPresentationEventDisposition.Accepted,
			readerPresentationReduce(retry2.state, exact).disposition)
	}

	private enum class RetryCoverCompletion { AfterReentry, BeforeCancel, AfterCancel }

	@Test
	fun cancellingAttributedPreparingRetryRetainsOriginalCoverAndAllowsFreshEntry() {
		for (latestTexture in listOf(false, true)) for (completion in RetryCoverCompletion.entries) {
			var state = preparingAttributedCoverRetry(latestTexture)
			val cover = assertIs<ReaderPresentationFrameOwner.ShellCover>(readerPresentationDecision(state).frameOwner)
			val partial = requireNotNull(state.binding)
			val completed = partial.copy(rasterGeneration = requireNotNull(cover.proof.binding.rasterGeneration) + 10L,
				textureGeneration = requireNotNull(cover.proof.binding.textureGeneration) + 10L)
			val retryToken = requireNotNull(readerPresentationDecision(state).pendingTransitionToken)
			fun dispatch(event: ReaderPresentationEvent): ReaderPresentationReduction = readerPresentationReduce(state, event).also {
				assertEquals(ReaderPresentationEventDisposition.Accepted, it.disposition)
				state = it.state
				state.assertSequenceInvariants()
			}
			if (completion == RetryCoverCompletion.BeforeCancel) dispatch(ReaderPresentationEvent.BindingCompleted(partial, completed))
			dispatch(ReaderPresentationEvent.TimedOut(retryToken))
			val cleanup = state.rendererCleanupOwnership
			val cancelled = dispatch(ReaderPresentationEvent.Cancel)
			assertEquals(cover.proof, assertIs<ReaderPresentationAuthority.ShellCover>(state.authority).proof,
				"Cancel must retain the original cover after attributed Preparing; completion=$completion latestTexture=$latestTexture")
			assertEquals(if (completion == RetryCoverCompletion.BeforeCancel) completed else partial, state.binding)
			assertEquals(cleanup, state.rendererCleanupOwnership)
			assertTrue(cancelled.effects.isEmpty())
			assertNull(cancelled.decision.pendingTransitionToken)
			assertNull(state.failure)
			assertEquals(ReaderRequiredTransition.None, cancelled.decision.requiredTransition)
			if (completion == RetryCoverCompletion.AfterCancel) {
				dispatch(ReaderPresentationEvent.BindingCompleted(partial, completed))
				assertEquals(cover.proof, assertIs<ReaderPresentationAuthority.ShellCover>(state.authority).proof)
			}
			val reentry = dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
			var request = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(state.authority).nativePresentationRequest)
			assertTrue(request.token != retryToken)
			assertEquals(state.binding, request.binding)
			assertEquals(cover.proof.binding.preparationGeneration, request.retryAfterPreparationGeneration)
			assertTrue(reentry.effects.isEmpty(), "Reentry must not allocate another preparation")
			assertEquals(cover, reentry.decision.frameOwner)
			var previousToken = retryToken
			repeat(2) {
				val staleTimeout = readerPresentationReduce(state, ReaderPresentationEvent.TimedOut(previousToken))
				assertEquals(state, staleTimeout.state)
				assertTrue(staleTimeout.effects.isEmpty())
				val staleProof = readerPresentationReduce(state,
					ReaderPresentationEvent.NativePagePresented(nativeProof(completed, 30L).copy(transitionToken = previousToken)))
				assertEquals(state, staleProof.state)
				val staleFacts = readerPresentationReduce(state, ReaderPresentationEvent.PreparationReported(requireNotNull(state.binding),
					settledNativePresentationState().preparationFacts.copy(generation = requireNotNull(request.retryAfterPreparationGeneration))))
				assertEquals(state, staleFacts.state)
				assertTrue(staleFacts.effects.isEmpty())
				dispatch(ReaderPresentationEvent.TimedOut(request.token))
				val beforeRepeatCancel = state
				val repeatCancel = dispatch(ReaderPresentationEvent.Cancel)
				assertEquals(cover.proof, assertIs<ReaderPresentationAuthority.ShellCover>(state.authority).proof)
				assertEquals(beforeRepeatCancel.binding, state.binding)
				assertEquals(beforeRepeatCancel.rendererCleanupOwnership, state.rendererCleanupOwnership)
				assertNull(repeatCancel.decision.pendingTransitionToken)
				assertTrue(repeatCancel.effects.isEmpty())
				previousToken = request.token
				val nextEntry = dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
				request = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(state.authority).nativePresentationRequest)
				assertTrue(request.token != previousToken)
				assertEquals(cover.proof.binding.preparationGeneration, request.retryAfterPreparationGeneration)
				assertEquals(beforeRepeatCancel.binding, request.binding)
				assertTrue(nextEntry.effects.isEmpty())
			}
			if (completion == RetryCoverCompletion.AfterReentry) dispatch(ReaderPresentationEvent.BindingCompleted(partial, completed))
			assertEquals(cover, readerPresentationDecision(state).frameOwner)
			val failedAgain = readerPresentationReduce(state, ReaderPresentationEvent.TimedOut(request.token))
			val futureRetry = readerPresentationReduce(failedAgain.state, ReaderPresentationEvent.Retry)
			val futureRequest = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(futureRetry.state.authority).nativePresentationRequest)
			assertEquals(completed.preparationGeneration, futureRequest.retryAfterPreparationGeneration)
			assertEquals(completed, futureRequest.binding)
			assertTrue(futureRequest.token != request.token)
			assertEquals(listOf(ReaderPresentationEffect.RetryPreparation(futureRequest.token, completed)), futureRetry.effects)
			val oldFacts = readerPresentationReduce(futureRetry.state, ReaderPresentationEvent.PreparationReported(completed,
				settledNativePresentationState().preparationFacts.copy(generation = requireNotNull(completed.preparationGeneration))))
			assertEquals(futureRetry.state, oldFacts.state)
			assertTrue(oldFacts.effects.isEmpty())
			val floorProof = readerPresentationReduce(futureRetry.state,
				ReaderPresentationEvent.NativePagePresented(nativeProof(completed, 31L).copy(transitionToken = futureRequest.token)))
			assertEquals(futureRetry.state, floorProof.state)
			// A real Retry resets the floor before allocation. Cancelling that attempt
			// retains the valid cover even though current preparation equals its floor.
			val futureTimeout = readerPresentationReduce(futureRetry.state, ReaderPresentationEvent.TimedOut(futureRequest.token))
			assertEquals(ReaderPresentationEventDisposition.Accepted, futureTimeout.disposition)
			futureTimeout.state.assertSequenceInvariants()
			val futureCancel = readerPresentationReduce(futureTimeout.state, ReaderPresentationEvent.Cancel)
			assertEquals(ReaderPresentationEventDisposition.Accepted, futureCancel.disposition)
			futureCancel.state.assertSequenceInvariants()
			assertEquals(cover.proof, assertIs<ReaderPresentationAuthority.ShellCover>(futureCancel.state.authority).proof,
				"Cancel before the next Retry allocation must retain the original cover; completion=$completion latestTexture=$latestTexture")
			assertEquals(completed, futureCancel.state.binding)
			assertEquals(futureRetry.state.rendererCleanupOwnership, futureCancel.state.rendererCleanupOwnership)
			assertNull(futureCancel.state.failure)
			assertNull(futureCancel.decision.pendingTransitionToken)
			assertEquals(ReaderRequiredTransition.None, futureCancel.decision.requiredTransition)
			assertEquals(ReaderPresentationInputPolicy.ShellCover, futureCancel.decision.inputPolicy)
			assertTrue(futureCancel.effects.isEmpty())
			val futureReentry = readerPresentationReduce(futureCancel.state, ReaderPresentationEvent.ShellCoverDismissalRequested)
			assertEquals(ReaderPresentationEventDisposition.Accepted, futureReentry.disposition)
			futureReentry.state.assertSequenceInvariants()
			val resumedRequest = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(futureReentry.state.authority).nativePresentationRequest)
			assertTrue(resumedRequest.token.value > futureRequest.token.value)
			assertEquals(completed, resumedRequest.binding)
			assertEquals(cover.proof.binding.preparationGeneration, resumedRequest.retryAfterPreparationGeneration)
			assertEquals(cover, futureReentry.decision.frameOwner)
			assertEquals(ReaderRequiredTransition.PresentNativePage(resumedRequest.token, completed, null), futureReentry.decision.requiredTransition)
			assertEquals(futureCancel.state.rendererCleanupOwnership, futureReentry.state.rendererCleanupOwnership)
			assertTrue(futureReentry.effects.isEmpty(), "Reentry after an unallocated Retry must not allocate preparation")
			assertEquals(futureReentry.state, readerPresentationReduce(futureReentry.state,
				ReaderPresentationEvent.TimedOut(futureRequest.token)).state)
			assertEquals(futureReentry.state, readerPresentationReduce(futureReentry.state,
				ReaderPresentationEvent.NativePagePresented(nativeProof(completed, 32L).copy(transitionToken = futureRequest.token))).state)
			dispatch(ReaderPresentationEvent.PreparationReported(completed,
				settledNativePresentationState().preparationFacts.copy(generation = requireNotNull(completed.preparationGeneration))))
			dispatch(ReaderPresentationEvent.NativePagePresented(nativeProof(completed, 31L).copy(transitionToken = request.token)))
			assertIs<ReaderPresentationAuthority.SettledNativePage>(state.authority)
			assertTrue(state.rendererCleanupOwnership.isEmpty())
			assertEquals(ReaderRequiredTransition.None, readerPresentationDecision(state).requiredTransition)
		}
	}

	@Test
	fun cancellingAttributedRetryCannotRestoreCoverAcrossGenuineReaderInvalidation() {
		for (latestTexture in listOf(false, true)) for (completeFirst in listOf(false, true)) {
			var prepared = preparingAttributedCoverRetry(latestTexture)
			val partial = requireNotNull(prepared.binding)
			if (completeFirst) prepared = readerPresentationReduce(prepared,
				ReaderPresentationEvent.BindingCompleted(partial, partial.copy(rasterGeneration = 91L, textureGeneration = 92L))).state
			val current = requireNotNull(prepared.binding)
			val destination = requireNotNull(current.destinationCommitIdentity)
			for (invalidation in listOf(
				ReaderPresentationEvent.BindingReplaced(current, current.copy(viewportGeneration = current.viewportGeneration + 1L)),
				ReaderPresentationEvent.FoliateRelocated(current.copy(destinationCommitIdentity = destination.copy(commitSequence = destination.commitSequence + 1L)), null),
				ReaderPresentationEvent.PublicationOpened(current.copy(publicationGeneration = current.publicationGeneration + 1L)),
				ReaderPresentationEvent.PublicationOpened(current.copy(foliateSessionId = "cancel-other",
					destinationCommitIdentity = destination.copy(foliateSessionId = "cancel-other")))
			)) {
				val changed = readerPresentationReduce(prepared, invalidation)
				assertEquals(ReaderPresentationEventDisposition.Accepted, changed.disposition)
				val failed = changed.decision.pendingTransitionToken?.let {
					readerPresentationReduce(changed.state, ReaderPresentationEvent.TimedOut(it)).state
				} ?: changed.state
				val cancelled = readerPresentationReduce(failed, ReaderPresentationEvent.Cancel)
				assertFalse(cancelled.decision.frameOwner is ReaderPresentationFrameOwner.ShellCover)
				assertEquals(changed.state.binding, cancelled.state.binding)
				assertNull(cancelled.decision.pendingTransitionToken)
				assertEquals(ReaderPresentationEventDisposition.Rejected,
					readerPresentationReduce(cancelled.state, ReaderPresentationEvent.ShellCoverDismissalRequested).disposition)
			}
			// Receiving-state guard controls, not alternate valid producer traces.
			val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(prepared.authority)
			val coverBinding = assertIs<ReaderPresentationFrameOwner.ShellCover>(pending.retainedFrame).proof.binding
			val request = requireNotNull(pending.nativePresentationRequest)
			val coverFloor = requireNotNull(coverBinding.preparationGeneration)
			val currentGeneration = requireNotNull(current.preparationGeneration)
			for (unprovenRequest in listOf(
				request.copy(retryAfterPreparationGeneration = null),
				request.copy(retryAfterPreparationGeneration = coverFloor - 1L),
				request.copy(retryAfterPreparationGeneration = currentGeneration + 1L),
				request.copy(binding = current.copy(preparationGeneration = currentGeneration + 1L))
			)) {
				val invalid = prepared.copy(authority = pending.copy(nativePresentationRequest = unprovenRequest))
				val cancelled = readerPresentationReduce(invalid, ReaderPresentationEvent.Cancel)
				assertFalse(cancelled.decision.frameOwner is ReaderPresentationFrameOwner.ShellCover)
				assertNull(cancelled.decision.pendingTransitionToken)
			}
			for (unproven in listOf(
				current.copy(preparationGeneration = coverBinding.preparationGeneration),
				current.copy(preparationGeneration = null),
				current.copy(profileGeneration = coverBinding.profileGeneration - 1L),
				current.copy(rasterGeneration = 91L, textureGeneration = null),
				current.copy(rasterGeneration = null, textureGeneration = 92L)
			)) {
				val invalid = prepared.copy(binding = unproven, authority = pending.copy(
					nativePresentationRequest = requireNotNull(pending.nativePresentationRequest).copy(binding = unproven)))
				val cancelled = readerPresentationReduce(invalid, ReaderPresentationEvent.Cancel)
				assertFalse(cancelled.decision.frameOwner is ReaderPresentationFrameOwner.ShellCover)
				assertNull(cancelled.decision.pendingTransitionToken)
			}
		}
	}

	@Test
	fun viewportReplacementAfterRetryCancelInvalidatesCoverAndCompletesCurrentTarget() {
		for (latestTexture in listOf(false, true)) {
			val preparing = preparingAttributedCoverRetry(latestTexture)
			val cancelled = readerPresentationReduce(preparing, ReaderPresentationEvent.Cancel)
			assertEquals(ReaderPresentationEventDisposition.Accepted, cancelled.disposition)
			val restored = cancelled.state
			val cover = assertIs<ReaderPresentationAuthority.ShellCover>(restored.authority).proof
			val partial = requireNotNull(restored.binding)
			val resized = partial.copy(viewportGeneration = partial.viewportGeneration + 1L)
			val completed = resized.copy(rasterGeneration = 91L, textureGeneration = 92L)
			val sameIdentity = readerPresentationReduce(restored,
				ReaderPresentationEvent.BindingCompleted(partial, partial.copy(rasterGeneration = 81L, textureGeneration = 82L)))
			assertEquals(ReaderPresentationEventDisposition.Accepted, sameIdentity.disposition)
			assertEquals(cover, assertIs<ReaderPresentationAuthority.ShellCover>(sameIdentity.state.authority).proof)
			val wrongPrevious = partial.copy(profileGeneration = partial.profileGeneration + 1L)
			for (stale in listOf(
				ReaderPresentationEvent.BindingReplaced(wrongPrevious, resized.copy(profileGeneration = wrongPrevious.profileGeneration)),
				ReaderPresentationEvent.BindingReplaced(partial, partial.copy(viewportGeneration = partial.viewportGeneration - 1L)),
				ReaderPresentationEvent.BindingReplaced(partial, resized.copy(profileGeneration = cover.binding.profileGeneration - 1L)),
				ReaderPresentationEvent.BindingReplaced(partial, resized.copy(preparationGeneration = cover.binding.preparationGeneration)),
				ReaderPresentationEvent.BindingReplaced(partial, resized.copy(destinationCommitIdentity =
					requireNotNull(resized.destinationCommitIdentity).copy(commitSequence = 99L)))
			)) {
				val ignored = readerPresentationReduce(restored, stale)
				assertEquals(restored, ignored.state)
				assertTrue(ignored.effects.isEmpty())
			}
			val invalidated = readerPresentationReduce(restored, ReaderPresentationEvent.BindingReplaced(partial, resized))
			assertEquals(ReaderPresentationEventDisposition.Accepted, invalidated.disposition)
			invalidated.state.assertSequenceInvariants()
			assertEquals(resized, invalidated.state.binding)
			assertEquals(ReaderPresentationAuthority.Unavailable, invalidated.state.authority)
			assertEquals(ReaderPresentationFrameOwner.Neutral, invalidated.decision.frameOwner)
			assertTrue(invalidated.state.rendererCleanupOwnership.isEmpty())
			assertEquals(restored.rendererCleanupOwnership.map { ReaderPresentationEffect.ReleaseStalePresentation(it.token, it.binding) },
				invalidated.effects)
			assertEquals(ReaderPagePreparationFacts(), invalidated.state.preparationFacts)
			assertNull(invalidated.decision.pendingTransitionToken)
			val duplicate = readerPresentationReduce(invalidated.state, ReaderPresentationEvent.BindingReplaced(partial, resized))
			assertEquals(invalidated.state, duplicate.state)
			assertTrue(duplicate.effects.isEmpty())
			val completion = readerPresentationReduce(invalidated.state, ReaderPresentationEvent.BindingCompleted(resized, completed))
			assertEquals(ReaderPresentationEventDisposition.Accepted, completion.disposition)
			assertEquals(completed, completion.state.binding)
			val requested = readerPresentationReduce(completion.state, ReaderPresentationEvent.NativePageRequested)
			val transition = assertIs<ReaderRequiredTransition.PresentNativePage>(requested.decision.requiredTransition)
			assertEquals(completed, transition.binding)
			val settled = readerPresentationReduce(requested.state,
				ReaderPresentationEvent.NativePagePresented(nativeProof(completed, 40L).copy(transitionToken = transition.token)))
			assertEquals(ReaderPresentationEventDisposition.Accepted, settled.disposition)
			assertIs<ReaderPresentationAuthority.SettledNativePage>(settled.state.authority)
			assertTrue(settled.state.rendererCleanupOwnership.isEmpty())
			assertNull(settled.decision.pendingTransitionToken)
			settled.state.assertSequenceInvariants()
		}
	}

	@Test
	fun preparationCancelCallbackTerminatesCoverTimeoutWithOriginalCoverRetained() {
		val fixture = PreparationCancelFixture(committedCoverState())
		val cover = fixture.decision.frameOwner
		fixture.dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
		fixture.timeout()
		assertTrue(assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).cancellable)

		fixture.callback().invoke()

		assertEquals(listOf<ReaderPresentationEvent>(ReaderPresentationEvent.Cancel), fixture.emitted,
			"The visible cover timeout Cancel callback must dispatch generic Cancel")
		fixture.assertCancelledCover(cover)
	}

	@Test
	fun preparationCancelCallbackRetainsAttributedPartialCoverAndUnallocatedRetryFloor() {
		for (latestTexture in listOf(false, true)) {
			val fixture = PreparationCancelFixture(preparingAttributedCoverRetry(latestTexture))
			val cover = fixture.decision.frameOwner
			val partial = requireNotNull(fixture.state.binding)
			val cleanup = fixture.state.rendererCleanupOwnership
			assertNull(partial.rasterGeneration)
			assertNull(partial.textureGeneration)
			fixture.timeout()
			fixture.callback().invoke()
			assertEquals(listOf<ReaderPresentationEvent>(ReaderPresentationEvent.Cancel), fixture.emitted,
				"Cancel must be reachable after attributed fresh Retry with original cover A and partial B")
			fixture.assertCancelledCover(cover)
			assertEquals(partial, fixture.state.binding)
			assertEquals(cleanup, fixture.state.rendererCleanupOwnership)

			fixture.dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
			fixture.timeout()
			fixture.dispatch(ReaderPresentationEvent.Retry)
			val request = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(
				fixture.state.authority).nativePresentationRequest)
			assertEquals(partial.preparationGeneration, request.retryAfterPreparationGeneration)
			fixture.timeout()
			fixture.callback().invoke()
			assertEquals(listOf<ReaderPresentationEvent>(ReaderPresentationEvent.Cancel, ReaderPresentationEvent.Cancel), fixture.emitted)
			fixture.assertCancelledCover(cover)
			assertEquals(partial, fixture.state.binding)
			assertEquals(cleanup, fixture.state.rendererCleanupOwnership)
		}
	}

	@Test
	fun preparationCancelCallbackRoutesCancellablePreparationFailuresWithoutChangingPolicy() {
		for (retainCover in listOf(false, true)) {
			val initial = if (retainCover) committedCoverState() else ReaderPresentationState(
				binding = requireNotNull(settledNativePresentationState().binding))
			val fixture = PreparationCancelFixture(initial)
			val retained = fixture.decision.frameOwner
			fixture.dispatch(if (retainCover) ReaderPresentationEvent.ShellCoverDismissalRequested
				else ReaderPresentationEvent.NativePageRequested)
			fixture.dispatch(ReaderPresentationEvent.PreparationFailed(
				requireNotNull(fixture.state.binding), settledNativePresentationState().preparationFacts,
				ReaderPresentationFailureReason.PreparationFailed, cancellable = true))
			fixture.callback().invoke()
			assertEquals(listOf<ReaderPresentationEvent>(ReaderPresentationEvent.Cancel), fixture.emitted,
				"Cancellable preparation failure must dispatch Cancel; retainCover=$retainCover")
			assertEquals(retained, fixture.decision.frameOwner)
			assertEquals(ReaderDiagnosticPresentation.Hidden, fixture.decision.diagnosticPresentation)
			assertNull(fixture.decision.pendingTransitionToken)
			assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
		}
	}

	@Test
	fun preparationCancelCallbackTerminatesCurlAttemptButKeepsTruthfulRetryableFailure() {
		val fixture = PreparationCancelFixture(settledNativePresentationState())
		val binding = requireNotNull(fixture.state.binding)
		val frame = ReaderCurlPresentationFrame(ReaderPresentationToken(40L), binding, 21L,
			1200, 800, requireNotNull(binding.rasterGeneration), requireNotNull(binding.textureGeneration))
		fixture.dispatch(ReaderPresentationEvent.CurlClaimed(frame))
		fixture.dispatch(ReaderPresentationEvent.CurlTerminal(frame.token, binding, expectedAcknowledgement = null))
		fixture.timeout()
		fixture.callback().invoke()
		assertEquals(listOf<ReaderPresentationEvent>(ReaderPresentationEvent.Cancel), fixture.emitted,
			"Cancellable curl timeout must dispatch Cancel")
		assertEquals(ReaderPresentationFrameOwner.Curl(frame), fixture.decision.frameOwner)
		assertIs<ReaderPresentationAuthority.BlockingPreparation>(fixture.state.authority)
		assertNull(fixture.decision.pendingTransitionToken)
		assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
		val failure = assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation)
		assertTrue(failure.retryable)
		assertFalse(failure.cancellable)
		fixture.callback().invoke()
		assertEquals(1, fixture.emitted.size, "Terminal curl diagnostic must not authorize another Cancel")
	}

	@Test
	fun preparationCancelCallbackPreservesExactHandoffDirectionTokenAndBinding() {
		for (direction in ReaderLiveEngineHandoffDirection.entries) {
			val fixture = PreparationCancelFixture(failedHandoffState(direction))
			val retained = fixture.decision.frameOwner
			val pending = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(fixture.state.authority)
			val exact = ReaderPresentationEvent.LiveEngineHandoffCancelled(direction, pending.token, pending.binding)
			for (wrong in listOf(
				exact.copy(direction = ReaderLiveEngineHandoffDirection.entries.first { it != direction }),
				exact.copy(token = ReaderPresentationToken(pending.token.value + 1L)),
				exact.copy(binding = pending.binding.copy(viewportGeneration = pending.binding.viewportGeneration + 1L))
			)) {
				val rejected = readerPresentationReduce(fixture.state, wrong)
				assertEquals(ReaderPresentationEventDisposition.Stale, rejected.disposition)
				assertEquals(fixture.state, rejected.state)
				assertTrue(rejected.effects.isEmpty())
			}
			fixture.callback().invoke()
			assertEquals(listOf<ReaderPresentationEvent>(exact), fixture.emitted)
			assertEquals(retained, fixture.decision.frameOwner)
			assertEquals(ReaderDiagnosticPresentation.Hidden, fixture.decision.diagnosticPresentation)
			assertNull(fixture.decision.pendingTransitionToken)
			assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
		}
	}

	@Test
	fun preparationCancelCallbackRejectsStaleClicksBeforeComposeCatchesUp() {
		val cover = PreparationCancelFixture(committedCoverState()).apply {
			dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
			timeout()
		}.state
		for (failed in listOf(cover) + ReaderLiveEngineHandoffDirection.entries.map(::failedHandoffState)) {
			for (change in listOf("retry", "successor-timeout", "cancel-reentry", "background", "closed", "replacement")) {
				val fixture = PreparationCancelFixture(failed)
				val oldClick = fixture.callback()
				when (change) {
					"retry", "successor-timeout" -> {
						fixture.dispatch(ReaderPresentationEvent.Retry)
						if (change == "successor-timeout") fixture.timeout()
					}
					"cancel-reentry" -> {
						fixture.dispatch(ReaderPresentationEvent.Cancel)
						val handoff = failed.authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
						fixture.dispatch(handoff?.let { ReaderPresentationEvent.WebViewHandoffRequested(it.direction) }
							?: ReaderPresentationEvent.ShellCoverDismissalRequested)
						fixture.timeout()
					}
					"background" -> fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
					"closed" -> fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.PublicationClosed))
					"replacement" -> fixture.dispatch(ReaderPresentationEvent.PublicationOpened(
						requireNotNull(failed.binding).copy(publicationGeneration = requireNotNull(failed.binding).publicationGeneration + 1L)))
				}
				val current = fixture.state
				oldClick()
				assertTrue(fixture.emitted.isEmpty(), "A stale $change callback must not dispatch cancellation")
				assertEquals(current, fixture.state)
			}
		}
	}

	@Test
	fun preparationCancelCallbackRejectsHiddenNoncancellableAndSuspendedDecisions() {
		val pendingCover = readerPresentationReduce(committedCoverState(),
			ReaderPresentationEvent.ShellCoverDismissalRequested).state
		val failedCover = readerPresentationReduce(pendingCover,
			ReaderPresentationEvent.TimedOut(readerPresentationDecision(pendingCover).pendingTransitionToken)).state
		for (failed in listOf(failedCover) + ReaderLiveEngineHandoffDirection.entries.map(::failedHandoffState)) {
			for (denied in listOf(
				failed.copy(failure = requireNotNull(failed.failure).copy(cancellable = false)),
				failed.copy(failure = null),
				failed.copy(lifecycle = ReaderPresentationLifecycleState.Background),
				failed.copy(lifecycle = ReaderPresentationLifecycleState.Destroyed)
			)) {
				val fixture = PreparationCancelFixture(denied)
				fixture.callback().invoke()
				assertTrue(fixture.emitted.isEmpty(), "Hidden, noncancellable or suspended decisions cannot dispatch Cancel")
				assertEquals(denied, fixture.state)
			}
		}
	}

	@Test
	fun preparationCancelCallbackIsTheComposeButtonRouteAndReadsCurrentCoordinatorAuthority() {
		val root = sourceFile("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt").readText()
		val screen = sourceFile("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()
		val overlay = sourceFile("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt").readText()
		val route = root.substringAfter("ReaderPagePreparationOverlay(").substringBefore("modifier = Modifier.matchParentSize()")
		assertContains(route, "onCancel = readerPreparationCancelCallback(")
		assertContains(route, "decision = presentationDecision")
		assertContains(route, "currentDecision = currentPresentationDecision")
		assertContains(route, "onPresentationEvent = onPresentationEvent")
		assertContains(screen, "currentPresentationDecision = { coordinator.controller.state.presentationDecision }")
		assertContains(overlay, "if (failure.cancellable)")
		assertContains(overlay, "TextButton(onClick = onCancel)")
	}

	private fun failedHandoffState(direction: ReaderLiveEngineHandoffDirection): ReaderPresentationState {
		val fixture = PreparationCancelFixture(settledNativePresentationState())
		fixture.dispatch(ReaderPresentationEvent.WebViewHandoffRequested(ReaderLiveEngineHandoffDirection.NativeToLiveEngine))
		if (direction == ReaderLiveEngineHandoffDirection.LiveEngineToNative) {
			val pending = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(fixture.state.authority)
			fixture.dispatch(ReaderPresentationEvent.LiveEngineExposureCommitted(
				ReaderLiveEnginePresentationProof(pending.token, pending.binding, 1L)))
			fixture.dispatch(ReaderPresentationEvent.WebViewHandoffRequested(direction))
		}
		fixture.timeout()
		return fixture.state
	}

	private class PreparationCancelFixture(initial: ReaderPresentationState) {
		private var controller = ReaderController(ReaderControllerState(presentation = initial,
			shellCoverVisible = readerPresentationDecision(initial).frameOwner is ReaderPresentationFrameOwner.ShellCover))
		val emitted = mutableListOf<ReaderPresentationEvent>()
		val state get() = controller.state.presentation
		val decision get() = controller.state.presentationDecision

		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationEventReceipt? {
			val step = controller.onPresentationEvent(event)
			controller = step.controller
			return step.presentationReceipt
		}

		fun timeout() {
			val pending = decision.authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
			dispatch(pending?.let { ReaderPresentationEvent.LiveEngineHandoffTimedOut(it.direction, it.token, it.binding) }
				?: ReaderPresentationEvent.TimedOut(requireNotNull(decision.pendingTransitionToken)))
			assertEquals(ReaderPresentationFailureReason.TimedOut, state.failure?.reason)
		}

		fun callback(): () -> Unit = readerPreparationCancelCallback(
			decision = decision,
			currentDecision = { decision },
			onPresentationEvent = { event ->
				emitted += event
				dispatch(event)
			}
		)

		fun assertCancelledCover(cover: ReaderPresentationFrameOwner) {
			assertEquals(cover, decision.frameOwner)
			assertEquals(assertIs<ReaderPresentationFrameOwner.ShellCover>(cover).proof,
				assertIs<ReaderPresentationAuthority.ShellCover>(state.authority).proof)
			assertEquals(ReaderPresentationInputPolicy.ShellCover, decision.inputPolicy)
			assertEquals(ReaderDiagnosticPresentation.Hidden, decision.diagnosticPresentation)
			assertEquals(ReaderPreparationPresentation.Hidden, decision.preparationPresentation)
			assertEquals(ReaderRequiredTransition.None, decision.requiredTransition)
			assertNull(decision.pendingTransitionToken)
			assertNull(state.failure)
			assertTrue(controller.state.shellCoverVisible)
		}
	}

	private fun preparingAttributedCoverRetry(latestTexture: Boolean): ReaderPresentationState {
		var state = committedCoverState()
		fun dispatch(event: ReaderPresentationEvent): ReaderPresentationReduction = readerPresentationReduce(state, event).also {
			assertEquals(ReaderPresentationEventDisposition.Accepted, it.disposition)
			state = it.state
			state.assertSequenceInvariants()
		}
		val original = requireNotNull(state.binding)
		if (latestTexture) dispatch(ReaderPresentationEvent.BindingReplaced(original,
			original.copy(textureGeneration = requireNotNull(original.textureGeneration) + 1L)))
		val predecessor = requireNotNull(state.binding)
		val entry = dispatch(ReaderPresentationEvent.ShellCoverDismissalRequested)
		dispatch(ReaderPresentationEvent.TimedOut(requireNotNull(entry.decision.pendingTransitionToken)))
		val retry = dispatch(ReaderPresentationEvent.Retry)
		val generation = requireNotNull(predecessor.preparationGeneration) + 1L
		val partial = predecessor.copy(preparationGeneration = generation, profileGeneration = predecessor.profileGeneration + 1L,
			rasterGeneration = null, textureGeneration = null)
		dispatch(ReaderPresentationEvent.BindingReplaced(predecessor, partial,
			ReaderPresentationRetryBindingAttribution(requireNotNull(retry.decision.pendingTransitionToken), generation, partial.profileGeneration)))
		val preparing = dispatch(ReaderPresentationEvent.PreparationReported(partial,
			ReaderPagePreparationFacts(phase = ReaderPagePreparationPhase.Preparing, generation = generation,
				completedCount = 1, requiredCount = 3)))
		assertIs<ReaderPreparationPresentation.Blocking>(preparing.decision.preparationPresentation)
		return state
	}

	private fun committedCoverState(): ReaderPresentationState {
		val pending = readerPresentationReduce(settledNativePresentationState(),
			ReaderPresentationEvent.ShellCoverRequested(37L))
		val transition = assertIs<ReaderRequiredTransition.CommitShellCover>(pending.decision.requiredTransition)
		return readerPresentationReduce(pending.state, ReaderPresentationEvent.ShellCoverCommitted(
			ReaderShellCoverCommitProof(transition.token, transition.binding, transition.coverGeneration,
				12L, 1200, 800))).state
	}

	private fun settledNativePresentationState(): ReaderPresentationState {
		val binding = presentationBinding(session = "session-current", destinationSequence = 4L)
		return ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof(binding, frame = 11L))
			),
			binding = binding,
			preparationFacts = ReaderPagePreparationFacts(
				phase = ReaderPagePreparationPhase.Ready,
				generation = requireNotNull(binding.preparationGeneration),
				completedCount = 3,
				requiredCount = 3,
				readiness = ReaderPageReadinessState(
					rasterGeneration = ReaderChapterRasterGenerationState.Ready,
					decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
					textureDeck = ReaderTextureDeckState.Ready,
					pendingTextureDeck = ReaderTextureDeckState.Ready,
					interaction = ReaderPageInteractionState.Ready
				)
			),
			nextTokenValue = 20L
		)
	}

	private fun presentationBinding(
		session: String,
		destinationSequence: Long
	): ReaderPresentationBinding = ReaderPresentationBinding(
		foliateSessionId = session,
		publicationGeneration = 2L,
		viewportGeneration = 3L,
		profileGeneration = 5L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(session, destinationSequence),
		rasterGeneration = 13L,
		textureGeneration = 17L,
		preparationGeneration = 19L
	)

	private fun nativeProof(
		binding: ReaderPresentationBinding,
		frame: Long
	): ReaderNativePagePresentationProof = ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = null,
		presentedFrame = frame,
		viewportWidth = 1200,
		viewportHeight = 800,
		rasterGeneration = requireNotNull(binding.rasterGeneration),
		textureGeneration = requireNotNull(binding.textureGeneration)
	)

	private fun sourceFile(relativePath: String): File = listOf(
		File(relativePath),
		File("composeApp/$relativePath")
	).firstOrNull(File::isFile) ?: error("Could not locate $relativePath")
}

/** Shared event-by-event contract; retained predecessor bindings are allowed only with input fenced. */
internal fun ReaderPresentationState.assertSequenceInvariants() {
	val decision = readerPresentationDecision(this)
	assertEquals(authority, decision.authority)
	assertEquals(binding, decision.targetBinding)
	assertEquals(decision.frameOwner.layer(), decision.layer)
	when (val input = decision.inputPolicy) {
		ReaderPresentationInputPolicy.RecoveryOnly -> assertEquals(ReaderPresentationLayer.Neutral, decision.layer)
		ReaderPresentationInputPolicy.ChromeOnly -> Unit
		ReaderPresentationInputPolicy.ShellCover -> assertEquals(ReaderPresentationLayer.ShellCover, decision.layer)
		ReaderPresentationInputPolicy.LiveEngine -> assertEquals(ReaderPresentationLayer.LiveEngine, decision.layer)
		is ReaderPresentationInputPolicy.ClaimedCurl -> assertEquals(ReaderPresentationLayer.Curl, decision.layer)
		is ReaderPresentationInputPolicy.NativePage -> {
			val frame = assertIs<ReaderPresentationFrameOwner.NativePage>(decision.frameOwner)
			if (input.policy.newPointer == ReaderPageNewPointerDecision.Accept) {
				assertEquals(binding, frame.proof.binding)
			}
		}
	}
	if (failure != null || preparationFacts.failure != null) {
		assertIs<ReaderDiagnosticPresentation.Failure>(decision.diagnosticPresentation)
	}
}
