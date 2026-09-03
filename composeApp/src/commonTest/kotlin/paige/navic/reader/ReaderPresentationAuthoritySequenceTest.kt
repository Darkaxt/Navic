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

		assertEquals(1, queue.pendingEffects().size)
		assertEquals(
			ReaderPresentationEffect.RetryPreparation(request.token, provisional),
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
		assertContains(android, "presentationHostBridge.update(decision)")
		assertContains(android, "override fun applyPresentationDecision(")
		assertContains(android, "readerNativePresentationApplication(")
		assertContains(android, "viewerContainer.applyPresentationDecision(application.decision)")
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
