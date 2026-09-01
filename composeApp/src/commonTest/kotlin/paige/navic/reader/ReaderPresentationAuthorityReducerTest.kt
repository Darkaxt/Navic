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
	private val readyReadiness = ReaderPageReadinessState(
		rasterGeneration = ReaderChapterRasterGenerationState.Ready,
		decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
		textureDeck = ReaderTextureDeckState.Ready,
		interaction = ReaderPageInteractionState.Ready
	)
	private val operationPolicy = readerPageOperationPolicy(readyReadiness)
	private val nativeProof = ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = null,
		presentedFrame = 10L,
		viewportWidth = 100,
		viewportHeight = 200,
		rasterGeneration = 4L,
		textureGeneration = 5L
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
	fun replayedAcceptedReceiptsAreNoOps() {
		val shellProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val shellCommitted = readerPresentationReduce(
			readerPresentationReduce(
				settledNativeState(nextTokenValue = 7L),
				ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
			).state,
			ReaderPresentationEvent.ShellCoverCommitted(shellProof)
		)
		assertNoOp(
			shellCommitted.state,
			readerPresentationReduce(
				shellCommitted.state,
				ReaderPresentationEvent.ShellCoverCommitted(shellProof)
			)
		)

		val nativePresented = readerPresentationReduce(
			ReaderPresentationState(
				binding = binding,
				preparationFacts = preparationFacts(readiness = readyReadiness)
			),
			ReaderPresentationEvent.NativePagePresented(nativeProof)
		)
		assertNoOp(
			nativePresented.state,
			readerPresentationReduce(
				nativePresented.state,
				ReaderPresentationEvent.NativePagePresented(nativeProof)
			)
		)

		val curl = curlFrame(ReaderPresentationToken(9L))
		val curlClaimed = readerPresentationReduce(
			settledNativeState(),
			ReaderPresentationEvent.CurlClaimed(curl.frame)
		)
		assertNoOp(
			curlClaimed.state,
			readerPresentationReduce(curlClaimed.state, ReaderPresentationEvent.CurlClaimed(curl.frame))
		)
		val terminalEvent = ReaderPresentationEvent.CurlTerminal(
			token = ReaderPresentationToken(9L),
			binding = binding,
			stage = ReaderCurlSettlementStage.AwaitingFoliate
		)
		val curlSettled = readerPresentationReduce(curlClaimed.state, terminalEvent)
		assertNoOp(curlSettled.state, readerPresentationReduce(curlSettled.state, terminalEvent))

		val liveHandoff = readerPresentationReduce(
			settledNativeState(),
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		)
		val liveProof = ReaderLiveEnginePresentationProof(
			token = ReaderPresentationToken(1L),
			binding = binding,
			presentedFrame = 12L,
			viewportWidth = 100,
			viewportHeight = 200,
			liveEngineGeneration = 7L
		)
		val liveExposed = readerPresentationReduce(
			liveHandoff.state,
			ReaderPresentationEvent.WebViewPresentationProven(liveProof)
		)
		assertNoOp(
			liveExposed.state,
			readerPresentationReduce(
				liveExposed.state,
				ReaderPresentationEvent.WebViewPresentationProven(liveProof)
			)
		)
	}

	@Test
	fun settledNativeInputTracksCurrentPreparationReadinessWithoutReplacingFrame() {
		val settlingReadiness = readyReadiness.copy(
			textureDeck = ReaderTextureDeckState.Settling,
			interaction = ReaderPageInteractionState.Settling
		)
		val failedReadiness = readyReadiness.copy(
			textureDeck = ReaderTextureDeckState.Failed,
			interaction = ReaderPageInteractionState.Failed
		)
		val state = settledNativeState()
		val initialOwner = readerPresentationDecision(state).frameOwner

		val settling = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationReported(
				binding,
				preparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					readiness = settlingReadiness
				)
			)
		)
		assertEquals(initialOwner, settling.decision.frameOwner)
		assertEquals(
			ReaderPresentationInputPolicy.NativePage(readerPageOperationPolicy(settlingReadiness)),
			settling.decision.inputPolicy
		)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			(settling.decision.inputPolicy as ReaderPresentationInputPolicy.NativePage).policy.newPointer
		)

		val failed = readerPresentationReduce(
			settling.state,
			ReaderPresentationEvent.PreparationReported(
				binding,
				preparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					readiness = failedReadiness
				)
			)
		)
		assertEquals(initialOwner, failed.decision.frameOwner)
		assertEquals(
			ReaderPresentationInputPolicy.NativePage(readerPageOperationPolicy(failedReadiness)),
			failed.decision.inputPolicy
		)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			(failed.decision.inputPolicy as ReaderPresentationInputPolicy.NativePage).policy.newPointer
		)

		val ready = readerPresentationReduce(
			failed.state,
			ReaderPresentationEvent.PreparationReported(
				binding,
				preparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					readiness = readyReadiness
				)
			)
		)
		assertEquals(initialOwner, ready.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), ready.decision.inputPolicy)
	}

	@Test
	fun nativeInputRejectsUntilSelectedProofGenerationHasCurrentFacts() {
		val nextBinding = binding.copy(preparationGeneration = 7L)
		val nextProof = ReaderNativePagePresentationProof(
			binding = nextBinding,
			transitionToken = null,
			presentedFrame = 11L,
			viewportWidth = 100,
			viewportHeight = 200,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		val generationOneReady = readerPresentationReduce(
			ReaderPresentationState(binding = binding),
			ReaderPresentationEvent.PreparationReported(
				binding,
				preparationFacts(phase = ReaderPagePreparationPhase.Ready, readiness = readyReadiness)
			)
		)
		val moved = readerPresentationReduce(
			readerPresentationReduce(
				generationOneReady.state,
				ReaderPresentationEvent.FoliateRelocated(nextBinding, acknowledgement = null)
			).state,
			ReaderPresentationEvent.NativePagePresented(nextProof)
		)

		assertEquals(ReaderPresentationFrameOwner.NativePage(nextProof), moved.decision.frameOwner)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			(moved.decision.inputPolicy as ReaderPresentationInputPolicy.NativePage).policy.newPointer
		)

		val current = readerPresentationReduce(
			moved.state,
			ReaderPresentationEvent.PreparationReported(
				nextBinding,
				preparationFacts(
					generation = 7L,
					phase = ReaderPagePreparationPhase.Ready,
					readiness = readyReadiness
				)
			)
		)

		assertEquals(moved.decision.frameOwner, current.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), current.decision.inputPolicy)
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
			ReaderPresentationAuthority.SettledNativePage(frame = nativeFrame),
			reduction.state.authority
		)
		assertEquals(nativeFrame, reduction.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.NativePage, reduction.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), reduction.decision.inputPolicy)
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
	fun matchingBackgroundPreparationKeepsStableAuthorityAndDiagnostic() {
		val curlToken = ReaderPresentationToken(7L)
		val coverProof = shellCoverProof(ReaderPresentationToken(8L), coverGeneration = 9L)
		val liveProof = ReaderLiveEnginePresentationProof(
			token = ReaderPresentationToken(10L),
			binding = binding,
			presentedFrame = 12L,
			viewportWidth = 100,
			viewportHeight = 200,
			liveEngineGeneration = 7L
		)
		val states = listOf(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding
			),
			settledNativeState(),
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.CurlGesture(curlFrame(curlToken)),
				binding = binding
			),
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCoverCommitPending(
					retainedFrame = nativeRetainedFrame,
					token = ReaderPresentationToken(11L),
					binding = binding,
					coverGeneration = 9L
				),
				binding = binding
			),
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.CurlSettlementPending(
					retainedFrame = curlFrame(curlToken),
					stage = ReaderCurlSettlementStage.AwaitingFoliate
				),
				binding = binding
			),
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.LiveEngineHandoffPending(
					retainedFrame = nativeFrame,
					token = ReaderPresentationToken(12L),
					binding = binding,
					direction = ReaderLiveEngineHandoffDirection.NativeToLiveEngine
				),
				binding = binding
			),
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.LiveEngineExposed(
					ReaderPresentationFrameOwner.LiveEngine(liveProof)
				),
				binding = binding
			)
		)

		states.forEach { state ->
			val expected = readerPresentationDecision(state)
			val failed = readerPresentationReduce(
				state,
				ReaderPresentationEvent.PreparationFailed(
					binding = binding,
					facts = preparationFacts(),
					reason = ReaderPresentationFailureReason.RendererUnavailable,
					cancellable = false
				)
			)
			val reported = readerPresentationReduce(
				failed.state,
				ReaderPresentationEvent.PreparationReported(binding, preparationFacts())
			)

			assertEquals(state.authority, failed.state.authority)
			assertEquals(state.authority, reported.state.authority)
			assertEquals(expected.frameOwner, reported.decision.frameOwner)
			assertEquals(expected.inputPolicy, reported.decision.inputPolicy)
			assertEquals(ReaderPreparationPresentation.Hidden, reported.decision.preparationPresentation)
			assertEquals(
				ReaderDiagnosticPresentation.Failure(
					ReaderPresentationFailureReason.RendererUnavailable,
					retryable = true,
					cancellable = false
				),
				reported.decision.diagnosticPresentation
			)
		}
	}

	@Test
	fun currentGenerationReadyClearsStableDiagnosticWithoutChangingAuthority() {
		val state = settledNativeState()
		val failed = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = preparationFacts(),
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				cancellable = true
			)
		)

		val ready = readerPresentationReduce(
			failed.state,
			ReaderPresentationEvent.PreparationReported(
				binding = binding,
				facts = preparationFacts(phase = ReaderPagePreparationPhase.Ready)
			)
		)

		assertEquals(state.authority, ready.state.authority)
		assertEquals(readerPresentationDecision(state).frameOwner, ready.decision.frameOwner)
		assertEquals(readerPresentationDecision(state).inputPolicy, ready.decision.inputPolicy)
		assertEquals(ReaderPreparationPresentation.Hidden, ready.decision.preparationPresentation)
		assertEquals(ReaderDiagnosticPresentation.Hidden, ready.decision.diagnosticPresentation)
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
				ReaderPresentationState(
					authority = authority,
					binding = binding,
					preparationFacts = preparationFacts(readiness = readyReadiness)
				)
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
	fun mismatchedPreparationEventsKeepActiveNullTokenNativePage() {
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
		assertEquals(emptyList(), mismatchedBinding.effects)
		assertEquals(state, mismatchedGeneration.state)
		assertEquals(before, mismatchedGeneration.decision)
		assertEquals(emptyList(), mismatchedGeneration.effects)
		listOf(mismatchedBinding, mismatchedGeneration).forEach { reduction ->
			assertEquals(nativeFrame, reduction.decision.frameOwner)
			assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), reduction.decision.inputPolicy)
			assertEquals(ReaderDiagnosticPresentation.Hidden, reduction.decision.diagnosticPresentation)
		}
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
	fun visibilityLossRetainsPresentationIdentityButRejectsPageInput() {
		val pending = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		).state
		val nativeState = settledNativeState()

		val hiddenPending = readerPresentationReduce(
			pending,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost)
		)
		val hiddenNative = readerPresentationReduce(
			nativeState,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost)
		)

		assertEquals(ReaderPresentationLifecycleState.Background, hiddenPending.state.lifecycle)
		assertEquals(pending.binding, hiddenPending.state.binding)
		assertEquals(pending.authority, hiddenPending.state.authority)
		assertEquals(pending.preparationFacts, hiddenPending.state.preparationFacts)
		assertEquals(pending.nextTokenValue, hiddenPending.state.nextTokenValue)
		assertEquals(readerPresentationDecision(pending).frameOwner, hiddenPending.decision.frameOwner)
		assertEquals(ReaderPreparationPresentation.Hidden, hiddenPending.decision.preparationPresentation)
		assertEquals(ReaderDiagnosticPresentation.Hidden, hiddenPending.decision.diagnosticPresentation)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, hiddenNative.decision.inputPolicy)
		assertEquals(nativeFrame, hiddenNative.decision.frameOwner)
	}

	@Test
	fun visibilityRestoreReturnsToTheSameVisibleAuthorityAndInput() {
		val state = settledNativeState()
		val hidden = readerPresentationReduce(
			state,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost)
		).state

		val restored = readerPresentationReduce(
			hidden,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored)
		)

		assertEquals(state, restored.state)
		assertEquals(readerPresentationDecision(state), restored.decision)
		assertEquals(emptyList(), restored.effects)
	}

	@Test
	fun typedMemoryPressureRetainsSelectedOwnerWithoutDiagnostic() {
		val state = settledNativeState()
		val events = listOf(
			ReaderPresentationLifecycleEvent.RunningMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Low
			),
			ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
				ReaderPresentationMemoryPressureLevel.Complete
			)
		)

		events.forEach { event ->
			val reduction = readerPresentationReduce(state, ReaderPresentationEvent.Lifecycle(event))
			assertEquals(state, reduction.state)
			assertEquals(nativeFrame, reduction.decision.frameOwner)
			assertEquals(ReaderDiagnosticPresentation.Hidden, reduction.decision.diagnosticPresentation)
			assertEquals(emptyList(), reduction.effects)
		}
	}

	@Test
	fun rendererLossRetainsStableFrameWithSanitizedDiagnostic() {
		val state = settledNativeState()

		val reduction = readerPresentationReduce(
			state,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost)
		)

		assertEquals(
			ReaderPresentationAuthority.BlockingPreparation(nativeFrame),
			reduction.state.authority
		)
		assertEquals(binding, reduction.state.binding)
		assertEquals(state.preparationFacts, reduction.state.preparationFacts)
		assertEquals(nativeFrame, reduction.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, reduction.decision.inputPolicy)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				reason = ReaderPresentationFailureReason.RendererLost,
				retryable = true,
				cancellable = false
			),
			reduction.decision.diagnosticPresentation
		)
	}

	@Test
	fun rendererLossWithoutFrameUsesNeutralRecoveryOnly() {
		val state = ReaderPresentationState(binding = binding)

		val reduction = readerPresentationReduce(
			state,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost)
		)

		assertEquals(
			ReaderPresentationAuthority.BlockingPreparation(ReaderPresentationFrameOwner.Neutral),
			reduction.state.authority
		)
		assertEquals(ReaderPresentationFrameOwner.Neutral, reduction.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.RecoveryOnly, reduction.decision.inputPolicy)
		assertEquals(
			ReaderPresentationFailureReason.RendererLost,
			(reduction.decision.diagnosticPresentation as ReaderDiagnosticPresentation.Failure).reason
		)
	}

	@Test
	fun publicationCloseIsTerminalUntilAnotherPublicationOpens() {
		val pending = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		).state
		val closed = readerPresentationReduce(
			pending,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.PublicationClosed)
		)

		assertEquals(ReaderPresentationAuthority.Unavailable, closed.state.authority)
		assertEquals(null, closed.state.binding)
		assertEquals(ReaderPresentationLifecycleState.Destroyed, closed.state.lifecycle)
		assertEquals(ReaderPagePreparationFacts(), closed.state.preparationFacts)
		assertEquals(null, closed.state.failure)
		assertEquals(pending.nextTokenValue, closed.state.nextTokenValue)
		assertEquals(ReaderPresentationFrameOwner.Neutral, closed.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.RecoveryOnly, closed.decision.inputPolicy)

		listOf<ReaderPresentationEvent>(
			ReaderPresentationEvent.NativePagePresented(nativeProof),
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored),
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost)
		).forEach { stale ->
			assertNoOp(closed.state, readerPresentationReduce(closed.state, stale))
		}

		val reopened = readerPresentationReduce(
			closed.state,
			ReaderPresentationEvent.PublicationOpened(binding)
		)
		assertEquals(binding, reopened.state.binding)
		assertEquals(ReaderPresentationLifecycleState.Foreground, reopened.state.lifecycle)
		assertEquals(ReaderPresentationAuthority.Unavailable, reopened.state.authority)
	}

	@Test
	fun preparationFactsAdapterKeepsRawFactsAndExcludesLegacyPresentationFields() {
		val state = ReaderPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			preparationGeneration = 9L,
			requiredCount = 5,
			completedCount = 2,
			activePageLabel = "sensitive-page-label",
			error = "sensitive failure detail",
			retryable = true,
			readiness = readyReadiness,
			presentation = ReaderPagePreparationPresentation.Hidden,
			gestureDisposition = ReaderPagePreparationGestureDisposition.Allow
		)

		val facts = state.toPresentationFacts()
		val withDifferentLegacyPresentation = state.copy(
			activePageLabel = "other-label",
			error = "other failure detail",
			presentation = ReaderPagePreparationPresentation.Cover,
			gestureDisposition = ReaderPagePreparationGestureDisposition.ConsumeWhilePreparing
		)

		assertEquals(ReaderPagePreparationPhase.Failed, facts.phase)
		assertEquals(9L, facts.generation)
		assertEquals(2, facts.completedCount)
		assertEquals(5, facts.requiredCount)
		assertEquals(readyReadiness, facts.readiness)
		assertEquals(ReaderPresentationFailureReason.PreparationFailed, facts.failure)
		assertEquals(true, facts.retryable)
		assertEquals(facts, withDifferentLegacyPresentation.toPresentationFacts())
	}

	private fun settledNativeState(nextTokenValue: Long = 1L) = ReaderPresentationState(
		authority = ReaderPresentationAuthority.SettledNativePage(nativeFrame),
		binding = binding,
		preparationFacts = preparationFacts(readiness = readyReadiness),
		nextTokenValue = nextTokenValue
	)

	private fun assertNoOp(
		state: ReaderPresentationState,
		reduction: ReaderPresentationReduction
	) {
		assertEquals(state, reduction.state)
		assertEquals(readerPresentationDecision(state), reduction.decision)
		assertEquals(emptyList(), reduction.effects)
	}

	private fun preparationFacts(
		generation: Long = 6L,
		phase: ReaderPagePreparationPhase = ReaderPagePreparationPhase.Preparing,
		readiness: ReaderPageReadinessState = readyReadiness
	) = ReaderPagePreparationFacts(
		phase = phase,
		generation = generation,
		completedCount = 1,
		requiredCount = 3,
		readiness = readiness
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
