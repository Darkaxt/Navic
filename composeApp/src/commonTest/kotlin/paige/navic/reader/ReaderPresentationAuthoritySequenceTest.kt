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
	fun platformHostContractCarriesDecisionTypedEventsAndStrictBindingFactsInShadowMode() {
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
			assertContains(source, "onPresentationEvent: (ReaderPresentationEvent) -> Unit")
			assertContains(source, "presentationEffects: List<ReaderPendingPresentationEffect>")
			assertContains(
				source,
				"onPresentationEffectHandled: (ReaderPresentationEffectIdentity) -> Unit"
			)
			assertContains(source, "destinationCommitIdentity: ReaderDestinationCommitIdentity?")
			assertContains(source, "pagePreparationCoverVisible: Boolean")
		}
		assertContains(screen, "presentationEffects = pendingPresentationEffects")
		assertContains(screen, "pendingPresentationEffectQueue.acknowledge(identity)")
		assertContains(android, "setPresentationShadow(")
		assertContains(android, "handlePresentationEffects(")
		assertContains(android, "releaseStalePresentationDeck(effect.binding)")
		assertContains(android, "reportPresentationShadowComparison()")
		assertContains(android, "Reader presentation shadow authority=")
		val shadowSetter = android.substringAfter("fun setPresentationShadow(")
			.substringBefore("\n\tfun ")
		assertFalse(shadowSetter.contains(".visibility ="))
		assertFalse(shadowSetter.contains("canAcceptNewPointer"))
		val legacyVisibility = android.substringAfter("private fun updateNativeCoverVisibility()")
			.substringBefore("\n\tfun setPageOperationPolicy")
		assertFalse(legacyVisibility.contains("presentationDecision"))
		val shadowDiagnostic = android.substringAfter("Reader presentation shadow authority=")
			.substringBefore("\n\t\t)")
		assertFalse(shadowDiagnostic.contains("foliateSessionId"))
		assertFalse(shadowDiagnostic.contains("destinationCommitIdentity"))
		assertFalse(shadowDiagnostic.contains("shellCoverUrl"))
		assertFalse(shadowDiagnostic.contains("viewerKey.identity"))
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
