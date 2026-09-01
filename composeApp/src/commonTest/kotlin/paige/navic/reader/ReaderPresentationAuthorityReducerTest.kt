package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
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
	private val operationPolicy = ReaderPageOperationPolicy(
		newPointer = ReaderPageNewPointerDecision.Accept,
		continueActivePointer = true,
		continueSettlement = false
	)
	private val nativeProof = ReaderNativePagePresentationProof(
		binding = binding,
		presentedFrame = 10L,
		viewportWidth = 100,
		viewportHeight = 200,
		rasterGeneration = 4L,
		textureGeneration = 5L,
		operationPolicy = operationPolicy
	)
	private val nativeFrame = ReaderPresentationFrameOwner.NativePage(nativeProof)

	@Test
	fun shellCoverRequestRetainsNativePageAndRejectsNewPointersUntilCommit() {
		val reduction = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		)

		assertEquals(
			ReaderPresentationAuthority.ShellCoverCommitPending(
				retainedFrame = nativeFrame,
				token = ReaderPresentationToken(7L),
				binding = binding,
				coverGeneration = 8L
			),
			reduction.state.authority
		)
		assertEquals(nativeFrame, reduction.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, reduction.decision.inputPolicy)
		assertEquals(ReaderPreparationPresentation.Hidden, reduction.decision.preparationPresentation)
		assertEquals(ReaderRequiredTransition.CommitShellCover, reduction.decision.requiredTransition)
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

		val reduction = readerPresentationReduce(
			pending,
			ReaderPresentationEvent.ShellCoverCommitted(
				shellCoverProof(token = ReaderPresentationToken(9L), coverGeneration = 8L)
			)
		)

		assertEquals(pending, reduction.state)
		assertEquals(before, reduction.decision)
		assertEquals(listOf(ReaderPresentationEffect.ReleaseStalePresentation), reduction.effects)
	}

	@Test
	fun preparationFailureOverStablePageKeepsPageAndShowsRetryableDiagnostic() {
		val facts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Preparing,
			generation = 9L,
			completedCount = 1,
			requiredCount = 3,
			readiness = ReaderPageReadinessState()
		)

		val reduction = readerPresentationReduce(
			settledNativeState(),
			ReaderPresentationEvent.PreparationFailed(
				facts = facts,
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				retryable = true,
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
			ReaderPresentationEvent.PreparationReported(
				ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 9L,
					completedCount = 1,
					requiredCount = 3,
					readiness = ReaderPageReadinessState()
				)
			)
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
		val curlFrame = ReaderPresentationFrameOwner.Curl(
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
		val curlFrame = ReaderPresentationFrameOwner.Curl(
			ReaderCurlPresentationFrame(
				token = curlToken,
				binding = binding,
				presentedFrame = 11L,
				viewportWidth = 100,
				viewportHeight = 200,
				rasterGeneration = 4L,
				textureGeneration = 5L
			)
		)
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
				retainedFrame = nativeFrame,
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

	private fun settledNativeState(nextTokenValue: Long = 1L) = ReaderPresentationState(
		authority = ReaderPresentationAuthority.SettledNativePage(nativeFrame),
		binding = binding,
		nextTokenValue = nextTokenValue
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
