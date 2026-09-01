package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ReaderPresentationAuthorityReducerTest {
	private val binding = ReaderPresentationBinding(
		foliateSessionId = "fixture-session",
		publicationGeneration = 1L,
		viewportGeneration = 2L,
		profileGeneration = 3L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(
			foliateSessionId = "fixture-session",
			commitSequence = 1L
		),
		rasterGeneration = 4L,
		textureGeneration = 5L,
		preparationGeneration = 6L
	)
	private val otherBinding = binding.copy(viewportGeneration = 4L)
	private val operationPolicy = ReaderPageOperationPolicy(
		newPointer = ReaderPageNewPointerDecision.Accept,
		continueActivePointer = true,
		continueSettlement = false
	)
	private val nativeProof = ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = null,
		presentedFrame = 10L,
		viewportWidth = 100,
		viewportHeight = 200,
		rasterGeneration = 4L,
		textureGeneration = 5L,
		operationPolicy = operationPolicy
	)
	private val nativeFrame = ReaderPresentationFrameOwner.NativePage(nativeProof)
	private val nativeRetainedFrame = ReaderShellCoverRetainedFrame.NativePage(nativeFrame)

	@Test
	fun shellCoverRequestRetainsNativePageAndRejectsNewPointersUntilCommit() {
		val reduction = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		)

		assertEquals(
			ReaderPresentationAuthority.ShellCoverCommitPending(
				retainedFrame = nativeRetainedFrame,
				token = ReaderPresentationToken(7L),
				binding = binding,
				coverGeneration = 8L
			),
			reduction.state.authority
		)
		assertEquals(nativeFrame, reduction.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, reduction.decision.inputPolicy)
		assertEquals(ReaderPreparationPresentation.Hidden, reduction.decision.preparationPresentation)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				token = ReaderPresentationToken(7L),
				binding = binding,
				coverGeneration = 8L
			),
			reduction.decision.requiredTransition
		)
	}

	@Test
	fun matchingShellCoverProofCommitsCover() {
		val pending = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		).state
		val proof = shellCoverProof(token = ReaderPresentationToken(7L), coverGeneration = 8L)

		val reduction = readerPresentationReduce(
			pending,
			ReaderPresentationEvent.ShellCoverCommitted(proof)
		)

		assertEquals(ReaderPresentationAuthority.ShellCover(proof), reduction.state.authority)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(proof), reduction.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.ShellCover, reduction.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ShellCover, reduction.decision.inputPolicy)
		assertEquals(ReaderRequiredTransition.None, reduction.decision.requiredTransition)
	}

	@Test
	fun mismatchedShellCoverProofKeepsPageAndReleasesStaleProofOnce() {
		val pending = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		).state
		val before = readerPresentationDecision(pending)
		val staleToken = ReaderPresentationToken(9L)

		val reduction = readerPresentationReduce(
			pending,
			ReaderPresentationEvent.ShellCoverCommitted(
				shellCoverProof(token = staleToken, coverGeneration = 8L)
			)
		)

		assertEquals(pending, reduction.state)
		assertEquals(before, reduction.decision)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(staleToken, binding)),
			reduction.effects
		)
	}

	@Test
	fun preparationFailureOverStablePageKeepsPageAndShowsRetryableDiagnostic() {
		val facts = preparationFacts()

		val reduction = readerPresentationReduce(
			settledNativeState(),
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = facts,
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				cancellable = false
			)
		)

		assertEquals(
			ReaderPresentationAuthority.BlockingPreparation(retainedFrame = nativeFrame),
			reduction.state.authority
		)
		assertEquals(nativeFrame, reduction.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.NativePage, reduction.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, reduction.decision.inputPolicy)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				retryable = true,
				cancellable = false
			),
			reduction.decision.diagnosticPresentation
		)
	}

	@Test
	fun committedCoverSuppressesBackgroundPreparation() {
		val proof = shellCoverProof(token = ReaderPresentationToken(7L), coverGeneration = 8L)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(proof),
			binding = binding
		)

		val reduction = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationReported(binding, preparationFacts())
		)

		assertEquals(state.authority, reduction.state.authority)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(proof), reduction.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.ShellCover, reduction.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ShellCover, reduction.decision.inputPolicy)
		assertEquals(ReaderPreparationPresentation.Hidden, reduction.decision.preparationPresentation)
	}

	@Test
	fun activeCurlOwnsFrameAndOnlyClaimedGestureInput() {
		val token = ReaderPresentationToken(7L)
		val curlFrame = curlFrame(token)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.CurlGesture(curlFrame),
			binding = binding
		)

		val decision = readerPresentationDecision(state)

		assertEquals(curlFrame, decision.frameOwner)
		assertEquals(ReaderPresentationLayer.Curl, decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ClaimedCurl(token), decision.inputPolicy)
		assertIs<ReaderPresentationInputPolicy.ClaimedCurl>(decision.inputPolicy)
	}

	@Test
	fun authorityAlwaysProjectsOneLayerAndOneCompatibleInputPolicy() {
		val shellToken = ReaderPresentationToken(7L)
		val shellProof = shellCoverProof(token = shellToken, coverGeneration = 8L)
		val curlToken = ReaderPresentationToken(9L)
		val curlFrame = curlFrame(curlToken)
		val liveProof = ReaderLiveEnginePresentationProof(
			token = ReaderPresentationToken(10L),
			binding = binding,
			presentedFrame = 12L,
			viewportWidth = 100,
			viewportHeight = 200,
			liveEngineGeneration = 7L
		)
		val liveFrame = ReaderPresentationFrameOwner.LiveEngine(liveProof)
		val fixtures = listOf(
			ReaderPresentationAuthority.Unavailable to ReaderPresentationFrameOwner.Neutral,
			ReaderPresentationAuthority.ShellCover(shellProof) to ReaderPresentationFrameOwner.ShellCover(shellProof),
			ReaderPresentationAuthority.ShellCoverCommitPending(
				retainedFrame = nativeRetainedFrame,
				token = shellToken,
				binding = binding,
				coverGeneration = 8L
			) to nativeFrame,
			ReaderPresentationAuthority.CurlGesture(curlFrame) to curlFrame,
			ReaderPresentationAuthority.CurlSettlementPending(
				retainedFrame = curlFrame,
				stage = ReaderCurlSettlementStage.AwaitingFoliate
			) to curlFrame,
			ReaderPresentationAuthority.SettledNativePage(nativeFrame) to nativeFrame,
			ReaderPresentationAuthority.LiveEngineHandoffPending(
				retainedFrame = nativeFrame,
				token = ReaderPresentationToken(10L),
				binding = binding,
				direction = ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			) to nativeFrame,
			ReaderPresentationAuthority.LiveEngineExposed(liveFrame) to liveFrame,
			ReaderPresentationAuthority.BlockingPreparation(nativeFrame) to nativeFrame
		)

		fixtures.forEach { (authority, expectedOwner) ->
			val decision = readerPresentationDecision(
				ReaderPresentationState(authority = authority, binding = binding)
			)

			assertEquals(expectedOwner, decision.frameOwner)
			assertEquals(expectedOwner.layer(), decision.layer)
			when (authority) {
				ReaderPresentationAuthority.Unavailable ->
					assertEquals(ReaderPresentationInputPolicy.RecoveryOnly, decision.inputPolicy)
				is ReaderPresentationAuthority.ShellCover ->
					assertEquals(ReaderPresentationInputPolicy.ShellCover, decision.inputPolicy)
				is ReaderPresentationAuthority.ShellCoverCommitPending,
				is ReaderPresentationAuthority.LiveEngineHandoffPending,
				is ReaderPresentationAuthority.BlockingPreparation ->
					assertEquals(ReaderPresentationInputPolicy.ChromeOnly, decision.inputPolicy)
				is ReaderPresentationAuthority.CurlGesture,
				is ReaderPresentationAuthority.CurlSettlementPending ->
					assertEquals(
						ReaderPresentationInputPolicy.ClaimedCurl(curlToken),
						decision.inputPolicy
					)
				is ReaderPresentationAuthority.SettledNativePage ->
					assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), decision.inputPolicy)
				is ReaderPresentationAuthority.LiveEngineExposed ->
					assertEquals(ReaderPresentationInputPolicy.LiveEngine, decision.inputPolicy)
			}
		}
	}

	@Test
	fun preparationEventsRejectMismatchedBindingAndGeneration() {
		val state = settledNativeState()
		val before = readerPresentationDecision(state)
		val mismatchedBinding = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationReported(otherBinding, preparationFacts())
		)
		val mismatchedGeneration = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = preparationFacts(generation = 7L),
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				cancellable = true
			)
		)

		assertEquals(state, mismatchedBinding.state)
		assertEquals(before, mismatchedBinding.decision)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(null, otherBinding)),
			mismatchedBinding.effects
		)
		assertEquals(state, mismatchedGeneration.state)
		assertEquals(before, mismatchedGeneration.decision)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(null, binding)),
			mismatchedGeneration.effects
		)
	}

	@Test
	fun preparationFailureDuringActiveCurlRetainsClaimUntilTerminal() {
		val token = ReaderPresentationToken(7L)
		val frame = curlFrame(token)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.CurlGesture(frame),
			binding = binding
		)
		val failed = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = preparationFacts(),
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				cancellable = false
			)
		)

		assertEquals(ReaderPresentationAuthority.CurlGesture(frame), failed.state.authority)
		assertEquals(frame, failed.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.ClaimedCurl(token), failed.decision.inputPolicy)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.RendererUnavailable,
				retryable = true,
				cancellable = false
			),
			failed.decision.diagnosticPresentation
		)

		val terminal = readerPresentationReduce(
			failed.state,
			ReaderPresentationEvent.CurlTerminal(
				token = token,
				binding = binding,
				stage = ReaderCurlSettlementStage.AwaitingFoliate
			)
		)

		assertEquals(ReaderPresentationAuthority.CurlSettlementPending(frame, ReaderCurlSettlementStage.AwaitingFoliate), terminal.state.authority)
		assertEquals(ReaderDiagnosticPresentation.Hidden, terminal.decision.diagnosticPresentation)
	}

	@Test
	fun coverPendingCanRetainTerminalCurl() {
		val token = ReaderPresentationToken(7L)
		val terminalCurl = ReaderShellCoverRetainedFrame.TerminalCurl(curlFrame(token))
		val authority = ReaderPresentationAuthority.ShellCoverCommitPending(
			retainedFrame = terminalCurl,
			token = ReaderPresentationToken(8L),
			binding = binding,
			coverGeneration = 9L
		)

		val decision = readerPresentationDecision(
			ReaderPresentationState(authority = authority, binding = binding)
		)

		assertEquals(terminalCurl.frameOwner, decision.frameOwner)
		assertEquals(ReaderPresentationLayer.Curl, decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, decision.inputPolicy)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(ReaderPresentationToken(8L), binding, 9L),
			decision.requiredTransition
		)
	}

	@Test
	fun proofGenerationContradictionsAreRejected() {
		assertFailsWith<IllegalArgumentException> {
			nativeProof.copy(rasterGeneration = 99L)
		}
		assertFailsWith<IllegalArgumentException> {
			curlFrame(ReaderPresentationToken(7L)).frame.copy(textureGeneration = 99L)
		}
	}

	@Test
	fun typedLifecycleEventsConstructAndReduceSafely() {
		val events = listOf(
			ReaderPresentationLifecycleEvent.VisibilityLost,
			ReaderPresentationLifecycleEvent.VisibilityRestored,
			ReaderPresentationLifecycleEvent.RunningMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Moderate
			),
			ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Critical
			),
			ReaderPresentationLifecycleEvent.RendererLost,
			ReaderPresentationLifecycleEvent.PublicationClosed
		)
		val state = settledNativeState()

		events.forEach { event ->
			val reduction = readerPresentationReduce(state, ReaderPresentationEvent.Lifecycle(event))
			assertEquals(state, reduction.state)
			assertEquals(readerPresentationDecision(state), reduction.decision)
			assertEquals(emptyList(), reduction.effects)
		}
	}

	private fun settledNativeState(nextTokenValue: Long = 1L) = ReaderPresentationState(
		authority = ReaderPresentationAuthority.SettledNativePage(nativeFrame),
		binding = binding,
		nextTokenValue = nextTokenValue
	)

	private fun preparationFacts(generation: Long = 6L) = ReaderPagePreparationFacts(
		phase = ReaderPagePreparationPhase.Preparing,
		generation = generation,
		completedCount = 1,
		requiredCount = 3,
		readiness = ReaderPageReadinessState()
	)

	private fun curlFrame(token: ReaderPresentationToken) = ReaderPresentationFrameOwner.Curl(
		ReaderCurlPresentationFrame(
			token = token,
			binding = binding,
			presentedFrame = 11L,
			viewportWidth = 100,
			viewportHeight = 200,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
	)

	private fun shellCoverProof(
		token: ReaderPresentationToken,
		coverGeneration: Long
	) = ReaderShellCoverCommitProof(
		token = token,
		binding = binding,
		coverGeneration = coverGeneration,
		presentedFrame = 13L,
		viewportWidth = 100,
		viewportHeight = 200
	)
}
