package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
