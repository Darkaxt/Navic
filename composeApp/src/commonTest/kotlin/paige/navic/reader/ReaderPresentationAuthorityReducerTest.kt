package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
	fun initialShellCoverRequestOwnsANeutralTokenizedCommitUntilExactProof() {
		val opened = readerPresentationReduce(
			ReaderPresentationState(nextTokenValue = 7L),
			ReaderPresentationEvent.PublicationOpened(binding)
		)

		val requested = readerPresentationReduce(
			opened.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L)
		)
		val pending = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			requested.state.authority
		)
		assertEquals(ReaderPresentationFrameOwner.Neutral, pending.retainedFrame.frameOwner)
		assertEquals(binding, pending.retainedFrame.binding)
		assertEquals(ReaderPresentationToken(7L), pending.token)
		assertEquals(binding, pending.binding)
		assertEquals(8L, pending.coverGeneration)
		assertEquals(ReaderPresentationFrameOwner.Neutral, requested.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.Neutral, requested.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, requested.decision.inputPolicy)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				token = ReaderPresentationToken(7L),
				binding = binding,
				coverGeneration = 8L
			),
			requested.decision.requiredTransition
		)

		val staleToken = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.ShellCoverCommitted(
				shellCoverProof(ReaderPresentationToken(6L), coverGeneration = 8L)
			)
		)
		assertEquals(requested.state, staleToken.state)
		assertEquals(1, staleToken.effects.size)

		val staleBinding = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.ShellCoverCommitted(
				shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L).copy(
					binding = otherBinding
				)
			)
		)
		assertEquals(requested.state, staleBinding.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					token = ReaderPresentationToken(7L),
					binding = otherBinding
				)
			),
			staleBinding.effects
		)

		val staleGeneration = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.ShellCoverCommitted(
				shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 9L)
			)
		)
		assertEquals(requested.state, staleGeneration.state)
		assertTrue(staleGeneration.effects.isEmpty())

		val exactProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val committed = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.ShellCoverCommitted(exactProof)
		)
		assertEquals(ReaderPresentationAuthority.ShellCover(exactProof), committed.state.authority)
		assertEquals(ReaderPresentationLayer.ShellCover, committed.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ShellCover, committed.decision.inputPolicy)
	}

	@Test
	fun duplicateInitialOpenAndCoverRequestCoalesceWithoutAllocatingAnotherToken() {
		val opened = readerPresentationReduce(
			ReaderPresentationState(nextTokenValue = 17L),
			ReaderPresentationEvent.PublicationOpened(binding)
		)
		val requested = readerPresentationReduce(
			opened.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 18L)
		)

		val duplicateRequest = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 19L)
		)
		assertNoOp(requested.state, duplicateRequest)

		val duplicateOpen = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.PublicationOpened(binding)
		)
		assertNoOp(requested.state, duplicateOpen)
		assertEquals(18L, duplicateOpen.state.nextTokenValue)
	}

	@Test
	fun initialShellCoverFailureStaysNeutralAndRetryAllocatesANewExactToken() {
		val pending = readerPresentationReduce(
			readerPresentationReduce(
				ReaderPresentationState(nextTokenValue = 27L),
				ReaderPresentationEvent.PublicationOpened(binding)
			).state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 28L)
		).state
		val failed = readerPresentationReduce(
			pending,
			ReaderPresentationEvent.ShellCoverFailed(
				token = ReaderPresentationToken(27L),
				binding = binding
			)
		)

		assertEquals(ReaderPresentationFrameOwner.Neutral, failed.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.Neutral, failed.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, failed.decision.inputPolicy)
		assertEquals(ReaderRequiredTransition.None, failed.decision.requiredTransition)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				reason = ReaderPresentationFailureReason.ShellCoverUnavailable,
				retryable = true,
				cancellable = false
			),
			failed.decision.diagnosticPresentation
		)

		val readyPageFacts = readerPresentationReduce(
			failed.state,
			ReaderPresentationEvent.PreparationReported(
				binding = binding,
				facts = preparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					readiness = readyReadiness
				)
			)
		)
		assertEquals(failed.state.failure, readyPageFacts.state.failure)
		assertEquals(ReaderRequiredTransition.None, readyPageFacts.decision.requiredTransition)

		val retried = readerPresentationReduce(readyPageFacts.state, ReaderPresentationEvent.Retry)
		val retry = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			retried.state.authority
		)
		assertEquals(ReaderPresentationToken(28L), retry.token)
		assertEquals(binding, retry.binding)
		assertEquals(28L, retry.coverGeneration)
		assertEquals(29L, retried.state.nextTokenValue)
		assertEquals(ReaderDiagnosticPresentation.Hidden, retried.decision.diagnosticPresentation)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				token = retry.token,
				binding = binding,
				coverGeneration = 28L
			),
			retried.decision.requiredTransition
		)
	}

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
	fun settledNativeRelocationAdvancesTargetButRetainsFrameAndFencesInput() {
		val successor = binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = binding.foliateSessionId,
				commitSequence = 2L
			),
			rasterGeneration = 7L,
			textureGeneration = 8L,
			preparationGeneration = 6L
		)
		val settled = settledNativeState(nextTokenValue = 7L)

		val relocated = readerPresentationReduce(
			settled,
			ReaderPresentationEvent.FoliateRelocated(successor, acknowledgement = null)
		)

		assertEquals(successor, relocated.state.binding)
		assertEquals(settled.authority, relocated.state.authority)
		assertEquals(nativeFrame, relocated.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.NativePage, relocated.decision.layer)
		assertEquals(ReaderPagePreparationFacts(), relocated.state.preparationFacts)
		assertTrue(relocated.effects.isEmpty())
		assertIs<ReaderPageNewPointerDecision.Reject>(
			(relocated.decision.inputPolicy as ReaderPresentationInputPolicy.NativePage)
				.policy
				.newPointer
		)

		val lateOldFacts = readerPresentationReduce(
			relocated.state,
			ReaderPresentationEvent.PreparationReported(binding, preparationFacts())
		)
		assertNoOp(relocated.state, lateOldFacts)

		val successorFacts = preparationFacts(
			generation = 6L,
			phase = ReaderPagePreparationPhase.Ready,
			readiness = readyReadiness
		)
		val prepared = readerPresentationReduce(
			relocated.state,
			ReaderPresentationEvent.PreparationReported(successor, successorFacts)
		)
		assertEquals(successorFacts, prepared.state.preparationFacts)
		assertEquals(nativeFrame, prepared.decision.frameOwner)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			(prepared.decision.inputPolicy as ReaderPresentationInputPolicy.NativePage)
				.policy
				.newPointer
		)

		val ignoredCoverRequest = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 10L)
		)
		assertNoOp(prepared.state, ignoredCoverRequest)
		assertEquals(ReaderRequiredTransition.None, ignoredCoverRequest.decision.requiredTransition)

		val lateRetainedRelocation = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.FoliateRelocated(binding, acknowledgement = null)
		)
		assertNoOp(prepared.state, lateRetainedRelocation)

		assertNoOp(
			prepared.state,
			readerPresentationReduce(
				prepared.state,
				ReaderPresentationEvent.FoliateRelocated(successor, acknowledgement = null)
			)
		)
	}

	@Test
	fun settledNativeRelocationRejectsIncompleteRegressingOrDifferentReaderTargets() {
		val currentBinding = binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = binding.foliateSessionId,
				commitSequence = 4L
			)
		)
		val currentProof = nativeProof.copy(binding = currentBinding)
		val currentState = settledNativeState().copy(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(currentProof)
			),
			binding = currentBinding
		)
		val validShape = currentBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = currentBinding.foliateSessionId,
				commitSequence = 5L
			),
			rasterGeneration = 7L,
			textureGeneration = 8L,
			preparationGeneration = 9L
		)
		val otherSession = "other-fixture-session"
		val rejectedTargets = listOf(
			validShape.copy(
				foliateSessionId = otherSession,
				destinationCommitIdentity = ReaderDestinationCommitIdentity(otherSession, 5L)
			),
			validShape.copy(publicationGeneration = validShape.publicationGeneration + 1L),
			validShape.copy(viewportGeneration = validShape.viewportGeneration + 1L),
			validShape.copy(profileGeneration = validShape.profileGeneration + 1L),
			validShape.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					foliateSessionId = currentBinding.foliateSessionId,
					commitSequence = 4L
				)
			),
			validShape.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					foliateSessionId = currentBinding.foliateSessionId,
					commitSequence = 3L
				)
			),
			validShape.copy(rasterGeneration = null),
			validShape.copy(textureGeneration = null),
			validShape.copy(preparationGeneration = null)
		)

		rejectedTargets.forEach { rejected ->
			val reduction = readerPresentationReduce(
				currentState,
				ReaderPresentationEvent.FoliateRelocated(rejected, acknowledgement = null)
			)
			assertEquals(currentState, reduction.state)
			assertEquals(nativeFrame.copy(proof = currentProof), reduction.decision.frameOwner)
			assertEquals(
				listOf(
					ReaderPresentationEffect.ReleaseStalePresentation(
						token = currentProof.transitionToken,
						binding = rejected
					)
				),
				reduction.effects
			)
		}
	}

	@Test
	fun currentTargetNativeProofReplacesRetainedFrameAndReleasesOldIdentityOnce() {
		val successor = binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = binding.foliateSessionId,
				commitSequence = 2L
			),
			rasterGeneration = 7L,
			textureGeneration = 8L,
			preparationGeneration = 9L
		)
		val successorProof = nativeProofFor(successor, presentedFrame = 11L)
		val moved = readerPresentationReduce(
			settledNativeState(nextTokenValue = 7L),
			ReaderPresentationEvent.FoliateRelocated(successor, acknowledgement = null)
		)
		val prepared = readerPresentationReduce(
			moved.state,
			ReaderPresentationEvent.PreparationReported(
				successor,
				preparationFacts(
					generation = 9L,
					phase = ReaderPagePreparationPhase.Ready,
					readiness = readyReadiness
				)
			)
		)

		val wrongTokenProof = successorProof.copy(
			transitionToken = ReaderPresentationToken(99L)
		)
		val wrongToken = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.NativePagePresented(wrongTokenProof)
		)
		assertEquals(prepared.state, wrongToken.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					wrongTokenProof.transitionToken,
					successor
				)
			),
			wrongToken.effects
		)

		val presented = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.NativePagePresented(successorProof)
		)
		assertEquals(successor, presented.state.binding)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(successorProof)
			),
			presented.state.authority
		)
		assertEquals(ReaderPresentationFrameOwner.NativePage(successorProof), presented.decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), presented.decision.inputPolicy)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					nativeProof.transitionToken,
					binding
				)
			),
			presented.effects
		)

		assertNoOp(
			presented.state,
			readerPresentationReduce(
				presented.state,
				ReaderPresentationEvent.NativePagePresented(successorProof)
			)
		)

		val lateOld = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.NativePagePresented(nativeProof)
		)
		assertEquals(presented.state, lateOld.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					nativeProof.transitionToken,
					binding
				)
			),
			lateOld.effects
		)

		val untargetedBinding = successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = successor.foliateSessionId,
				commitSequence = 3L
			),
			rasterGeneration = 10L,
			textureGeneration = 11L,
			preparationGeneration = 12L
		)
		val untargetedProof = nativeProofFor(untargetedBinding, presentedFrame = 12L)
		val untargeted = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.NativePagePresented(untargetedProof)
		)
		assertEquals(presented.state, untargeted.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					untargetedProof.transitionToken,
					untargetedBinding
				)
			),
			untargeted.effects
		)

		val cover = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 10L)
		)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				token = ReaderPresentationToken(7L),
				binding = successor,
				coverGeneration = 10L
			),
			cover.decision.requiredTransition
		)
	}

	@Test
	fun combinedBindingReplacementRequiresCompleteExactSuccessorAndReleasesInvalidFrameOnce() {
		val combined = binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = binding.foliateSessionId,
				commitSequence = 2L
			),
			viewportGeneration = binding.viewportGeneration + 1L,
			profileGeneration = binding.profileGeneration + 1L,
			rasterGeneration = 17L,
			textureGeneration = 18L,
			preparationGeneration = 19L
		)
		val current = settledNativeState()

		val incomplete = readerPresentationReduce(
			current,
			ReaderPresentationEvent.BindingReplaced(
				previousBinding = binding,
				binding = combined.copy(textureGeneration = null)
			)
		)
		assertNoOp(current, incomplete)

		val replaced = readerPresentationReduce(
			current,
			ReaderPresentationEvent.BindingReplaced(binding, combined)
		)
		assertEquals(ReaderPresentationAuthority.Unavailable, replaced.state.authority)
		assertEquals(combined, replaced.state.binding)
		assertEquals(ReaderPagePreparationFacts(), replaced.state.preparationFacts)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					token = nativeProof.transitionToken,
					binding = binding
				)
			),
			replaced.effects
		)
		assertNoOp(
			replaced.state,
			readerPresentationReduce(
				replaced.state,
				ReaderPresentationEvent.BindingReplaced(binding, combined)
			)
		)

		val oldCallback = readerPresentationReduce(
			replaced.state,
			ReaderPresentationEvent.NativePagePresented(nativeProof)
		)
		assertEquals(replaced.state, oldCallback.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(null, binding)
			),
			oldCallback.effects
		)

		val combinedFacts = preparationFacts(
			generation = 19L,
			phase = ReaderPagePreparationPhase.Ready,
			readiness = readyReadiness
		)
		val prepared = readerPresentationReduce(
			replaced.state,
			ReaderPresentationEvent.PreparationReported(combined, combinedFacts)
		)
		val combinedProof = nativeProofFor(combined, presentedFrame = 21L)
		val presented = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.NativePagePresented(combinedProof)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(combinedProof)
			),
			presented.state.authority
		)
		assertTrue(presented.effects.isEmpty())
	}

	@Test
	fun shellCoverDismissalRetainsCoverUntilExactTokenizedNativeFrame() {
		val coverProof = shellCoverProof(
			token = ReaderPresentationToken(7L),
			coverGeneration = 8L
		)
		val coverState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(coverProof),
			binding = binding,
			preparationFacts = preparationFacts(readiness = readyReadiness),
			nextTokenValue = 8L
		)
		val target = binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = binding.foliateSessionId,
				commitSequence = 2L
			),
			rasterGeneration = 27L,
			textureGeneration = 28L,
			preparationGeneration = 29L
		)

		val dismissal = readerPresentationReduce(
			coverState,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			dismissal.state.authority
		)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), pending.retainedFrame)
		assertEquals(
			ReaderNativePagePresentationRequest(
				token = ReaderPresentationToken(8L),
				binding = binding
			),
			pending.nativePresentationRequest
		)
		assertEquals(9L, dismissal.state.nextTokenValue)
		assertEquals(ReaderPresentationLayer.ShellCover, dismissal.decision.layer)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, dismissal.decision.inputPolicy)
		assertEquals(
			ReaderRequiredTransition.PresentNativePage(
				token = ReaderPresentationToken(8L),
				binding = binding,
				direction = null
			),
			dismissal.decision.requiredTransition
		)

		val relocated = readerPresentationReduce(
			dismissal.state,
			ReaderPresentationEvent.FoliateRelocated(target, acknowledgement = null)
		)
		val relocatedPending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			relocated.state.authority
		)
		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(8L), target),
			relocatedPending.nativePresentationRequest
		)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), relocated.decision.frameOwner)
		assertEquals(target, relocated.state.binding)
		assertEquals(ReaderPagePreparationFacts(), relocated.state.preparationFacts)
		assertTrue(relocated.effects.isEmpty())

		val preparationFailure = readerPresentationReduce(
			relocated.state,
			ReaderPresentationEvent.PreparationFailed(
				binding = target,
				facts = preparationFacts(
					generation = 29L,
					phase = ReaderPagePreparationPhase.Failed
				),
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				cancellable = false
			)
		)
		assertEquals(relocatedPending, preparationFailure.state.authority)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), preparationFailure.decision.frameOwner)
		assertEquals(
			relocated.decision.requiredTransition,
			preparationFailure.decision.requiredTransition
		)

		val rendererLost = readerPresentationReduce(
			relocated.state,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost)
		)
		assertEquals(relocatedPending, rendererLost.state.authority)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), rendererLost.decision.frameOwner)
		assertEquals(
			relocated.decision.requiredTransition,
			rendererLost.decision.requiredTransition
		)

		val wrongTokenProof = nativeProofFor(target, presentedFrame = 31L).copy(
			transitionToken = ReaderPresentationToken(99L)
		)
		val wrongToken = readerPresentationReduce(
			rendererLost.state,
			ReaderPresentationEvent.NativePagePresented(wrongTokenProof)
		)
		assertEquals(rendererLost.state, wrongToken.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					ReaderPresentationToken(99L),
					target
				)
			),
			wrongToken.effects
		)

		val exactProof = nativeProofFor(target, presentedFrame = 32L).copy(
			transitionToken = ReaderPresentationToken(8L)
		)
		val presented = readerPresentationReduce(
			rendererLost.state,
			ReaderPresentationEvent.NativePagePresented(exactProof)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(exactProof)
			),
			presented.state.authority
		)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					token = coverProof.token,
					binding = coverProof.binding
				)
			),
			presented.effects
		)
		assertNoOp(
			presented.state,
			readerPresentationReduce(
				presented.state,
				ReaderPresentationEvent.NativePagePresented(exactProof)
			)
		)

		val lateCover = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.ShellCoverCommitted(coverProof)
		)
		assertEquals(presented.state, lateCover.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(coverProof.token, binding)
			),
			lateCover.effects
		)

		val nextCover = readerPresentationReduce(
			presented.state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 33L)
		)
		assertEquals(
			ReaderRequiredTransition.CommitShellCover(
				token = ReaderPresentationToken(9L),
				binding = target,
				coverGeneration = 33L
			),
			nextCover.decision.requiredTransition
		)
	}

	@Test
	fun shellCoverDismissalSupportsSameTargetAndFencesMultipleRelocations() {
		val coverProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val coverState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(coverProof),
			binding = binding,
			nextTokenValue = 8L
		)
		val pending = readerPresentationReduce(
			coverState,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val sameProof = nativeProof.copy(transitionToken = ReaderPresentationToken(8L))
		val samePresented = readerPresentationReduce(
			pending.state,
			ReaderPresentationEvent.NativePagePresented(sameProof)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(sameProof)
			),
			samePresented.state.authority
		)

		val bindingB = binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(binding.foliateSessionId, 2L),
			rasterGeneration = 37L,
			textureGeneration = 38L,
			preparationGeneration = 39L
		)
		val bindingC = bindingB.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(binding.foliateSessionId, 3L),
			rasterGeneration = 47L,
			textureGeneration = 48L,
			preparationGeneration = 49L
		)
		val movedB = readerPresentationReduce(
			pending.state,
			ReaderPresentationEvent.FoliateRelocated(bindingB, null)
		)
		val movedC = readerPresentationReduce(
			movedB.state,
			ReaderPresentationEvent.FoliateRelocated(bindingC, null)
		)
		val lateBProof = nativeProofFor(bindingB, 41L).copy(
			transitionToken = ReaderPresentationToken(8L)
		)
		val lateB = readerPresentationReduce(
			movedC.state,
			ReaderPresentationEvent.NativePagePresented(lateBProof)
		)
		assertEquals(movedC.state, lateB.state)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					ReaderPresentationToken(8L),
					bindingB
				)
			),
			lateB.effects
		)
		val proofC = nativeProofFor(bindingC, 42L).copy(
			transitionToken = ReaderPresentationToken(8L)
		)
		val presentedC = readerPresentationReduce(
			movedC.state,
			ReaderPresentationEvent.NativePagePresented(proofC)
		)
		assertEquals(bindingC, presentedC.state.binding)
		assertEquals(ReaderPresentationFrameOwner.NativePage(proofC), presentedC.decision.frameOwner)
	}

	@Test
	fun coverDismissalSurvivesEveryCompleteBindingReplacementShape() {
		val coverProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val replacements = listOf(
			binding.copy(
				viewportGeneration = 12L,
				profileGeneration = 13L,
				rasterGeneration = 14L,
				textureGeneration = 15L,
				preparationGeneration = 16L
			),
			binding.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					binding.foliateSessionId,
					2L
				),
				viewportGeneration = 22L,
				profileGeneration = 23L,
				rasterGeneration = 24L,
				textureGeneration = 25L,
				preparationGeneration = 26L
			),
			binding.copy(preparationGeneration = 36L)
		)

		replacements.forEachIndexed { index, replacement ->
			val coverState = ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding,
				preparationFacts = preparationFacts(readiness = readyReadiness),
				nextTokenValue = 8L
			)
			val dismissal = readerPresentationReduce(
				coverState,
				ReaderPresentationEvent.ShellCoverDismissalRequested
			)
			val replaced = readerPresentationReduce(
				dismissal.state,
				ReaderPresentationEvent.BindingReplaced(binding, replacement)
			)
			val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
				replaced.state.authority
			)

			assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), pending.retainedFrame)
			assertEquals(
				ReaderNativePagePresentationRequest(ReaderPresentationToken(8L), replacement),
				pending.nativePresentationRequest
			)
			assertEquals(replacement, replaced.state.binding)
			assertEquals(ReaderPagePreparationFacts(), replaced.state.preparationFacts)
			assertEquals(null, replaced.state.failure)
			assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), replaced.decision.frameOwner)
			assertEquals(
				ReaderRequiredTransition.PresentNativePage(
					ReaderPresentationToken(8L),
					replacement,
					direction = null
				),
				replaced.decision.requiredTransition
			)
			assertTrue(replaced.effects.isEmpty())

			val staleProof = nativeProofFor(binding, presentedFrame = 60L + index).copy(
				transitionToken = ReaderPresentationToken(8L)
			)
			val stale = readerPresentationReduce(
				replaced.state,
				ReaderPresentationEvent.NativePagePresented(staleProof)
			)
			assertEquals(replaced.state, stale.state)
			assertEquals(
				listOf(
					ReaderPresentationEffect.ReleaseStalePresentation(
						ReaderPresentationToken(8L),
						binding
					)
				),
				stale.effects
			)

			val ready = readerPresentationReduce(
				replaced.state,
				ReaderPresentationEvent.PreparationReported(
					replacement,
					preparationFacts(
						generation = requireNotNull(replacement.preparationGeneration),
						phase = ReaderPagePreparationPhase.Ready,
						readiness = readyReadiness
					)
				)
			)
			val exactProof = nativeProofFor(replacement, presentedFrame = 70L + index).copy(
				transitionToken = ReaderPresentationToken(8L)
			)
			val presented = readerPresentationReduce(
				ready.state,
				ReaderPresentationEvent.NativePagePresented(exactProof)
			)
			assertEquals(
				ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(exactProof)
				),
				presented.state.authority
			)
			assertEquals(
				listOf(
					ReaderPresentationEffect.ReleaseStalePresentation(
						coverProof.token,
						coverProof.binding
					)
				),
				presented.effects
			)
			val laterCover = readerPresentationReduce(
				presented.state,
				ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 90L + index)
			)
			assertEquals(
				ReaderPresentationToken(9L),
				assertIs<ReaderRequiredTransition.CommitShellCover>(
					laterCover.decision.requiredTransition
				).token
			)
		}
	}

	@Test
	fun coverDismissalReplacementChainPreservesTokenThroughFailureLossAndRetry() {
		val coverProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val dismissal = readerPresentationReduce(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding,
				nextTokenValue = 8L
			),
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val bindingB = binding.copy(
			viewportGeneration = 42L,
			rasterGeneration = 43L,
			textureGeneration = 44L,
			preparationGeneration = 45L
		)
		val bindingC = bindingB.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				binding.foliateSessionId,
				2L
			),
			profileGeneration = 52L,
			rasterGeneration = 53L,
			textureGeneration = 54L,
			preparationGeneration = 55L
		)
		val retryBinding = bindingC.copy(preparationGeneration = 56L)
		val eventAB = ReaderPresentationEvent.BindingReplaced(binding, bindingB)
		val movedB = readerPresentationReduce(dismissal.state, eventAB)
		assertNoOp(movedB.state, readerPresentationReduce(movedB.state, eventAB))
		val movedC = readerPresentationReduce(
			movedB.state,
			ReaderPresentationEvent.BindingReplaced(bindingB, bindingC)
		)
		val failed = readerPresentationReduce(
			movedC.state,
			ReaderPresentationEvent.PreparationFailed(
				binding = bindingC,
				facts = preparationFacts(
					generation = 55L,
					phase = ReaderPagePreparationPhase.Failed
				),
				reason = ReaderPresentationFailureReason.RendererUnavailable,
				cancellable = false
			)
		)
		val rendererLost = readerPresentationReduce(
			failed.state,
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost)
		)
		val retried = readerPresentationReduce(
			rendererLost.state,
			ReaderPresentationEvent.BindingReplaced(bindingC, retryBinding)
		)
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			retried.state.authority
		)

		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), pending.retainedFrame)
		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(8L), retryBinding),
			pending.nativePresentationRequest
		)
		assertEquals(ReaderPagePreparationFacts(), retried.state.preparationFacts)
		assertEquals(null, retried.state.failure)
		assertTrue(movedB.effects.isEmpty())
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					ReaderPresentationToken(8L),
					bindingB
				)
			),
			movedC.effects
		)
		assertTrue(retried.effects.isEmpty())

		val staleCProof = nativeProofFor(bindingC, 81L).copy(
			transitionToken = ReaderPresentationToken(8L)
		)
		val staleC = readerPresentationReduce(
			retried.state,
			ReaderPresentationEvent.NativePagePresented(staleCProof)
		)
		assertEquals(retried.state, staleC.state)
		val exactProof = nativeProofFor(retryBinding, 82L).copy(
			transitionToken = ReaderPresentationToken(8L)
		)
		val exact = readerPresentationReduce(
			retried.state,
			ReaderPresentationEvent.NativePagePresented(exactProof)
		)
		assertEquals(ReaderPresentationFrameOwner.NativePage(exactProof), exact.decision.frameOwner)
		assertEquals(
			listOf(
				ReaderPresentationEffect.ReleaseStalePresentation(
					coverProof.token,
					coverProof.binding
				)
			),
			exact.effects
		)
	}

	@Test
	fun rendererDeckIdentityUsesSessionPublicationRasterTextureTransitionMatrix() {
		val baseIdentity = binding.rendererDeckIdentityOrNull()
		assertEquals(
			ReaderRendererDeckIdentity(
				foliateSessionId = binding.foliateSessionId,
				publicationGeneration = binding.publicationGeneration,
				rasterGeneration = requireNotNull(binding.rasterGeneration),
				textureGeneration = requireNotNull(binding.textureGeneration)
			),
			baseIdentity
		)
		val transitions = listOf(
			"preparation alias" to (binding.copy(preparationGeneration = 7L) to true),
			"viewport alias" to (binding.copy(viewportGeneration = 7L) to true),
			"profile alias" to (binding.copy(profileGeneration = 7L) to true),
			"destination alias" to (
				binding.copy(
					viewportGeneration = 7L,
					destinationCommitIdentity = ReaderDestinationCommitIdentity(
						binding.foliateSessionId,
						2L
					)
				) to true
			),
			"raster replacement" to (binding.copy(rasterGeneration = 7L) to false),
			"texture replacement" to (binding.copy(textureGeneration = 7L) to false),
			"publication replacement" to (binding.copy(publicationGeneration = 7L) to false),
			"session replacement" to (
				binding.copy(
					foliateSessionId = "replacement-session",
					destinationCommitIdentity = ReaderDestinationCommitIdentity(
						"replacement-session",
						1L
					)
				) to false
			),
			"incomplete raster identity" to (binding.copy(rasterGeneration = null) to false),
			"incomplete texture identity" to (binding.copy(textureGeneration = null) to false)
		)

		transitions.forEach { (label, transition) ->
			val (candidate, expectedSameDeck) = transition
			assertEquals(
				expectedSameDeck,
				baseIdentity != null && baseIdentity == candidate.rendererDeckIdentityOrNull(),
				label
			)
		}
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
	fun viewportBindingReplacementFencesOldAuthorityAndAcceptsOnlyNewBindingFacts() {
		val replacement = binding.copy(viewportGeneration = 8L)
		val replaced = readerPresentationReduce(
			settledNativeState(),
			ReaderPresentationEvent.BindingReplaced(
				previousBinding = binding,
				binding = replacement
			)
		)

		assertEquals(replacement, replaced.state.binding)
		assertEquals(ReaderPresentationAuthority.Unavailable, replaced.state.authority)
		assertEquals(ReaderPresentationFrameOwner.Neutral, replaced.decision.frameOwner)
		assertEquals(ReaderPagePreparationFacts(), replaced.state.preparationFacts)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(null, binding)),
			replaced.effects
		)

		val lateOldFacts = readerPresentationReduce(
			replaced.state,
			ReaderPresentationEvent.PreparationReported(binding, preparationFacts())
		)
		assertNoOp(replaced.state, lateOldFacts)

		val newFacts = preparationFacts()
		val prepared = readerPresentationReduce(
			replaced.state,
			ReaderPresentationEvent.PreparationReported(replacement, newFacts)
		)
		assertEquals(newFacts, prepared.state.preparationFacts)

		val lateOldProof = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.NativePagePresented(nativeProof)
		)
		assertEquals(prepared.state, lateOldProof.state)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(null, binding)),
			lateOldProof.effects
		)

		val replacementProof = nativeProof.copy(binding = replacement)
		val presented = readerPresentationReduce(
			prepared.state,
			ReaderPresentationEvent.NativePagePresented(replacementProof)
		)
		assertEquals(replacement, presented.state.binding)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(replacementProof)
			),
			presented.state.authority
		)
		assertNoOp(
			presented.state,
			readerPresentationReduce(
				presented.state,
				ReaderPresentationEvent.NativePagePresented(replacementProof)
			)
		)
	}

	@Test
	fun bindingReplacementReleaseCarriesOldProofToken() {
		val token = ReaderPresentationToken(44L)
		val tokenizedProof = nativeProof.copy(transitionToken = token)
		val state = settledNativeState().copy(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(tokenizedProof)
			)
		)
		val replacement = binding.copy(viewportGeneration = 8L)

		val replaced = readerPresentationReduce(
			state,
			ReaderPresentationEvent.BindingReplaced(binding, replacement)
		)

		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(token, binding)),
			replaced.effects
		)
	}

	@Test
	fun profileDeckBindingReplacementIsIdempotentAndRejectsLateOldGeneration() {
		val replacement = binding.copy(
			profileGeneration = 9L,
			rasterGeneration = 10L,
			textureGeneration = 11L,
			preparationGeneration = 12L
		)
		val event = ReaderPresentationEvent.BindingReplaced(
			previousBinding = binding,
			binding = replacement
		)
		val replaced = readerPresentationReduce(settledNativeState(), event)

		assertEquals(replacement, replaced.state.binding)
		assertEquals(ReaderPresentationAuthority.Unavailable, replaced.state.authority)
		assertNoOp(replaced.state, readerPresentationReduce(replaced.state, event))

		val lateOld = readerPresentationReduce(
			replaced.state,
			ReaderPresentationEvent.PreparationReported(binding, preparationFacts())
		)
		assertNoOp(replaced.state, lateOld)

		val freshFacts = preparationFacts(generation = 12L)
		val fresh = readerPresentationReduce(
			replaced.state,
			ReaderPresentationEvent.PreparationReported(replacement, freshFacts)
		)
		assertEquals(freshFacts, fresh.state.preparationFacts)
		assertTrue(fresh.effects.isEmpty())
	}

	@Test
	fun bindingReplacementCannotChangeSemanticPublicationOrDestinationIdentity() {
		val incomplete = ReaderPresentationEvent.BindingReplaced(
			previousBinding = binding,
			binding = binding.copy(rasterGeneration = null)
		)
		assertNoOp(
			settledNativeState(),
			readerPresentationReduce(settledNativeState(), incomplete)
		)
		assertFailsWith<IllegalArgumentException> {
			ReaderPresentationEvent.BindingReplaced(
				previousBinding = binding,
				binding = binding.copy(publicationGeneration = 2L)
			)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderPresentationEvent.BindingReplaced(
				previousBinding = binding,
				binding = binding.copy(
					destinationCommitIdentity = ReaderDestinationCommitIdentity(
						foliateSessionId = binding.foliateSessionId,
						commitSequence = 2L
					)
				)
			)
		}
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
	fun returnedCommittedCoverSuppressesExactBackgroundPreparation() {
		val proof = shellCoverProof(token = ReaderPresentationToken(7L), coverGeneration = 8L)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(proof),
			binding = binding
		)
		val backgroundFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Preparing,
			generation = requireNotNull(binding.preparationGeneration),
			completedCount = 1,
			requiredCount = 4,
			readiness = readyReadiness.copy(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val reduction = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationReported(binding, backgroundFacts)
		)

		assertEquals(ReaderPresentationAuthority.ShellCover(proof), reduction.state.authority)
		assertEquals(backgroundFacts, reduction.state.preparationFacts)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(proof), reduction.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.ShellCover, reduction.decision.layer)
		assertEquals(ReaderPreparationPresentation.Hidden, reduction.decision.preparationPresentation)
		assertEquals(ReaderDiagnosticPresentation.Hidden, reduction.decision.diagnosticPresentation)
		assertEquals(ReaderPresentationInputPolicy.ShellCover, reduction.decision.inputPolicy)
		assertEquals(ReaderRequiredTransition.None, reduction.decision.requiredTransition)
	}

	@Test
	fun returnedCommittedCoverRetainsSanitizedPreparationFailureOverItsFrame() {
		val proof = shellCoverProof(token = ReaderPresentationToken(7L), coverGeneration = 8L)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(proof),
			binding = binding
		)
		val failedFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Failed,
			generation = requireNotNull(binding.preparationGeneration),
			completedCount = 1,
			requiredCount = 4,
			readiness = readyReadiness.copy(
				textureDeck = ReaderTextureDeckState.Failed,
				interaction = ReaderPageInteractionState.Failed
			),
			failure = ReaderPresentationFailureReason.PreparationFailed,
			retryable = true
		)

		val reduction = readerPresentationReduce(
			state,
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = failedFacts,
				reason = ReaderPresentationFailureReason.PreparationFailed,
				cancellable = false
			)
		)

		assertEquals(ReaderPresentationAuthority.ShellCover(proof), reduction.state.authority)
		assertEquals(ReaderPresentationFrameOwner.ShellCover(proof), reduction.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.ShellCover, reduction.decision.layer)
		assertEquals(ReaderPreparationPresentation.Hidden, reduction.decision.preparationPresentation)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.PreparationFailed,
				retryable = true,
				cancellable = false
			),
			reduction.decision.diagnosticPresentation
		)
		assertEquals(ReaderPresentationInputPolicy.ShellCover, reduction.decision.inputPolicy)
		assertNoOp(
			reduction.state,
			readerPresentationReduce(reduction.state, ReaderPresentationEvent.Cancel)
		)
	}

	@Test
	fun earlyCoverEntryCoalescesAndProjectsExactBlockingPreparation() {
		val coverProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val preparingFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Preparing,
			generation = requireNotNull(binding.preparationGeneration),
			completedCount = 1,
			requiredCount = 4,
			readiness = readyReadiness.copy(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)
		val coverState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(coverProof),
			binding = binding,
			preparationFacts = preparingFacts,
			nextTokenValue = 8L
		)

		val first = readerPresentationReduce(
			coverState,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val duplicate = readerPresentationReduce(
			first.state,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			first.state.authority
		)

		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), pending.retainedFrame)
		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(8L), binding),
			pending.nativePresentationRequest
		)
		assertEquals(9L, first.state.nextTokenValue)
		assertEquals(ReaderPresentationLayer.ShellCover, first.decision.layer)
		assertEquals(
			ReaderPreparationPresentation.Blocking(
				completedCount = 1,
				requiredCount = 4,
				determinate = true
			),
			first.decision.preparationPresentation
		)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, first.decision.inputPolicy)
		assertEquals(
			ReaderRequiredTransition.PresentNativePage(
				ReaderPresentationToken(8L),
				binding,
				direction = null
			),
			first.decision.requiredTransition
		)
		assertNoOp(first.state, duplicate)
	}

	@Test
	fun readyCoverEntryStillWaitsForExactPresentedNativeFrameWithoutBlockingOverlay() {
		val coverProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val coverState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(coverProof),
			binding = binding,
			preparationFacts = preparationFacts(
				phase = ReaderPagePreparationPhase.Ready,
				readiness = readyReadiness
			),
			nextTokenValue = 8L
		)
		val pending = readerPresentationReduce(
			coverState,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)

		assertEquals(ReaderPresentationLayer.ShellCover, pending.decision.layer)
		assertEquals(ReaderPreparationPresentation.Hidden, pending.decision.preparationPresentation)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, pending.decision.inputPolicy)

		val exactProof = nativeProof.copy(transitionToken = ReaderPresentationToken(8L))
		val presented = readerPresentationReduce(
			pending.state,
			ReaderPresentationEvent.NativePagePresented(exactProof)
		)

		assertEquals(ReaderPresentationFrameOwner.NativePage(exactProof), presented.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.NativePage, presented.decision.layer)
		assertEquals(ReaderPreparationPresentation.Hidden, presented.decision.preparationPresentation)
		assertEquals(ReaderPresentationInputPolicy.NativePage(operationPolicy), presented.decision.inputPolicy)
	}

	@Test
	fun coverBackedFailureKeepsPendingTokenThroughRetryAndFreshGenerationRebind() {
		val coverProof = shellCoverProof(ReaderPresentationToken(7L), coverGeneration = 8L)
		val pending = readerPresentationReduce(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding,
				nextTokenValue = 8L
			),
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val failed = readerPresentationReduce(
			pending.state,
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = preparationFacts(phase = ReaderPagePreparationPhase.Failed),
				reason = ReaderPresentationFailureReason.PreparationFailed,
				cancellable = false
			)
		)
		val retryRequested = readerPresentationReduce(failed.state, ReaderPresentationEvent.Retry)
		val retryBinding = binding.copy(preparationGeneration = 7L)
		val rebound = readerPresentationReduce(
			retryRequested.state,
			ReaderPresentationEvent.BindingReplaced(binding, retryBinding)
		)
		val reboundPending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			rebound.state.authority
		)

		assertEquals(ReaderPresentationFrameOwner.ShellCover(coverProof), failed.decision.frameOwner)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.PreparationFailed,
				retryable = true,
				cancellable = false
			),
			failed.decision.diagnosticPresentation
		)
		assertEquals(failed.state, retryRequested.state)
		assertEquals(failed.decision, retryRequested.decision)
		assertEquals(
			listOf(
				ReaderPresentationEffect.RetryPreparation(
					token = ReaderPresentationToken(8L),
					binding = binding
				)
			),
			retryRequested.effects
		)
		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(8L), retryBinding),
			reboundPending.nativePresentationRequest
		)
		assertEquals(ReaderDiagnosticPresentation.Hidden, rebound.decision.diagnosticPresentation)
		assertEquals(9L, rebound.state.nextTokenValue)
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
	fun publicationCloseIsAbsorbingForNonResourceEventsAndOpenReplays() {
		val closed = closedPresentationState()
		val newerBinding = binding.copy(
			foliateSessionId = "newer-fixture-session",
			publicationGeneration = 2L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = "newer-fixture-session",
				commitSequence = 2L
			)
		)

		listOf<ReaderPresentationEvent>(
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored),
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.RendererLost),
			ReaderPresentationEvent.PreparationReported(binding, preparationFacts()),
			ReaderPresentationEvent.PreparationFailed(
				binding = binding,
				facts = preparationFacts(),
				reason = ReaderPresentationFailureReason.PreparationFailed,
				cancellable = false
			),
			ReaderPresentationEvent.FoliateRelocated(binding, acknowledgement = null),
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 8L),
			ReaderPresentationEvent.PublicationOpened(binding),
			ReaderPresentationEvent.PublicationOpened(newerBinding)
		).forEach { stale ->
			assertNoOp(closed, readerPresentationReduce(closed, stale))
		}
	}

	@Test
	fun publicationCloseReleasesEveryDelayedResourceProofWithoutResurrection() {
		val closed = closedPresentationState()
		val shellToken = ReaderPresentationToken(7L)
		val curlToken = ReaderPresentationToken(9L)
		val liveToken = ReaderPresentationToken(10L)
		val liveProof = ReaderLiveEnginePresentationProof(
			token = liveToken,
			binding = binding,
			presentedFrame = 12L,
			viewportWidth = 100,
			viewportHeight = 200,
			liveEngineGeneration = 7L
		)
		val delayedResources: List<Triple<ReaderPresentationEvent, ReaderPresentationToken?, ReaderPresentationBinding>> =
			listOf(
				Triple(
					ReaderPresentationEvent.BindingCompleted(
						binding.copy(rasterGeneration = null, textureGeneration = null),
						binding
					),
					null,
					binding
				),
				Triple(
					ReaderPresentationEvent.ShellCoverCommitted(
						shellCoverProof(shellToken, coverGeneration = 8L)
					),
					shellToken,
					binding
				),
				Triple(
					ReaderPresentationEvent.NativePagePresented(nativeProof),
					null,
					binding
				),
				Triple(
					ReaderPresentationEvent.CurlClaimed(curlFrame(curlToken).frame),
					curlToken,
					binding
				),
				Triple(
					ReaderPresentationEvent.WebViewPresentationProven(liveProof),
					liveToken,
					binding
				)
			)

		delayedResources.forEach { (event, token, eventBinding) ->
			repeat(2) {
				assertClosedStaleRelease(
					closed,
					readerPresentationReduce(closed, event),
					token,
					eventBinding
				)
			}
		}
	}

	@Test
	fun freshPresentationStateAcceptsPublicationOpenedAfterAnotherStateCloses() {
		val closed = closedPresentationState()
		val fresh = ReaderPresentationState()

		val reopened = readerPresentationReduce(
			fresh,
			ReaderPresentationEvent.PublicationOpened(binding)
		)

		assertEquals(ReaderPresentationAuthority.Unavailable, closed.authority)
		assertEquals(null, closed.binding)
		assertEquals(ReaderPresentationLifecycleState.Destroyed, closed.lifecycle)
		assertEquals(binding, reopened.state.binding)
		assertEquals(ReaderPresentationLifecycleState.Foreground, reopened.state.lifecycle)
		assertEquals(ReaderPresentationAuthority.Unavailable, reopened.state.authority)
		assertEquals(emptyList(), reopened.effects)
	}
	@Test
	fun preparationFactsAdapterKeepsRawFactsAndExcludesLocalDiagnosticDetails() {
		val state = ReaderPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			preparationGeneration = 9L,
			requiredCount = 5,
			completedCount = 2,
			activePageLabel = "sensitive-page-label",
			error = "sensitive failure detail",
			retryable = true,
			readiness = readyReadiness
		)

		val facts = state.toPresentationFacts()
		val withDifferentLocalDetails = state.copy(
			activePageLabel = "other-label",
			error = "other failure detail"
		)

		assertEquals(ReaderPagePreparationPhase.Failed, facts.phase)
		assertEquals(9L, facts.generation)
		assertEquals(2, facts.completedCount)
		assertEquals(5, facts.requiredCount)
		assertEquals(readyReadiness, facts.readiness)
		assertEquals(ReaderPresentationFailureReason.PreparationFailed, facts.failure)
		assertEquals(true, facts.retryable)
		assertEquals(facts, withDifferentLocalDetails.toPresentationFacts())
	}

	@Test
	fun noCoverPartialBindingOwnsNeutralPreparationUntilExactNativeProof() {
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
		val opened = readerPresentationReduce(
			ReaderPresentationState(nextTokenValue = 21L),
			ReaderPresentationEvent.PublicationOpened(partial)
		)
		val requested = readerPresentationReduce(
			opened.state,
			ReaderPresentationEvent.NativePageRequested
		)
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			requested.state.authority
		)

		assertEquals(ReaderPresentationFrameOwner.Neutral, pending.retainedFrame)
		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(21L), partial),
			pending.nativePresentationRequest
		)
		assertEquals(
			ReaderRequiredTransition.PresentNativePage(
				ReaderPresentationToken(21L),
				partial,
				direction = null
			),
			requested.decision.requiredTransition
		)

		val preparing = readerPresentationReduce(
			requested.state,
			ReaderPresentationEvent.PreparationReported(partial, preparationFacts())
		)
		assertEquals(
			ReaderPreparationPresentation.Blocking(1, 3, determinate = true),
			preparing.decision.preparationPresentation
		)
		val failed = readerPresentationReduce(
			preparing.state,
			ReaderPresentationEvent.PreparationFailed(
				binding = partial,
				facts = preparationFacts(phase = ReaderPagePreparationPhase.Failed),
				reason = ReaderPresentationFailureReason.PreparationFailed,
				cancellable = false
			)
		)
		assertEquals(pending, failed.state.authority)
		assertEquals(
			listOf(
				ReaderPresentationEffect.RetryPreparation(
					ReaderPresentationToken(21L),
					partial
				)
			),
			readerPresentationReduce(failed.state, ReaderPresentationEvent.Retry).effects
		)

		val completed = readerPresentationReduce(
			failed.state,
			ReaderPresentationEvent.BindingCompleted(partial, binding)
		)
		val completedPending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			completed.state.authority
		)
		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(21L), binding),
			completedPending.nativePresentationRequest
		)
		assertTrue(completed.effects.isEmpty())

		val proof = nativeProof.copy(transitionToken = ReaderPresentationToken(21L))
		val presented = readerPresentationReduce(
			completed.state,
			ReaderPresentationEvent.NativePagePresented(proof)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			presented.state.authority
		)
	}

	@Test
	fun partialCompletionPreservesCoverCommitAndDismissalTokens() {
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
		val coverPending = readerPresentationReduce(
			readerPresentationReduce(
				ReaderPresentationState(nextTokenValue = 31L),
				ReaderPresentationEvent.PublicationOpened(partial)
			).state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 32L)
		).state
		val completedPending = readerPresentationReduce(
			coverPending,
			ReaderPresentationEvent.BindingCompleted(partial, binding)
		)
		val pending = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			completedPending.state.authority
		)
		assertEquals(ReaderPresentationToken(31L), pending.token)
		assertEquals(binding, pending.binding)
		assertEquals(binding, pending.retainedFrame.binding)
		assertTrue(completedPending.effects.isEmpty())

		val proof = shellCoverProof(ReaderPresentationToken(31L), coverGeneration = 32L)
		val cover = readerPresentationReduce(
			completedPending.state,
			ReaderPresentationEvent.ShellCoverCommitted(proof)
		).state
		val dismissal = readerPresentationReduce(
			cover,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val dismissalPending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			dismissal.state.authority
		)
		assertEquals(ReaderPresentationToken(32L), dismissalPending.nativePresentationRequest?.token)
		assertEquals(binding, dismissalPending.nativePresentationRequest?.binding)
	}

	@Test
	fun partialCompletionPreservesCommittedShellCoverProof() {
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
		val requested = readerPresentationReduce(
			readerPresentationReduce(
				ReaderPresentationState(nextTokenValue = 61L),
				ReaderPresentationEvent.PublicationOpened(partial)
			).state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 62L)
		).state
		val partialProof = shellCoverProof(
			ReaderPresentationToken(61L),
			coverGeneration = 62L
		).copy(binding = partial)
		val committed = readerPresentationReduce(
			requested,
			ReaderPresentationEvent.ShellCoverCommitted(partialProof)
		).state

		val completed = readerPresentationReduce(
			committed,
			ReaderPresentationEvent.BindingCompleted(partial, binding)
		)
		val cover = assertIs<ReaderPresentationAuthority.ShellCover>(completed.state.authority)

		assertEquals(partialProof.copy(binding = binding), cover.proof)
		assertEquals(ReaderPresentationToken(61L), cover.proof.token)
		assertTrue(completed.effects.isEmpty())
	}

	@Test
	fun partialCompletionPreservesBlockingShellCoverDismissal() {
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
		val requested = readerPresentationReduce(
			readerPresentationReduce(
				ReaderPresentationState(nextTokenValue = 71L),
				ReaderPresentationEvent.PublicationOpened(partial)
			).state,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 72L)
		).state
		val committed = readerPresentationReduce(
			requested,
			ReaderPresentationEvent.ShellCoverCommitted(
				shellCoverProof(
					ReaderPresentationToken(71L),
					coverGeneration = 72L
				).copy(binding = partial)
			)
		).state
		val dismissing = readerPresentationReduce(
			committed,
			ReaderPresentationEvent.ShellCoverDismissalRequested
		).state
		val partialPending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			dismissing.authority
		)

		val completed = readerPresentationReduce(
			dismissing,
			ReaderPresentationEvent.BindingCompleted(partial, binding)
		)
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			completed.state.authority
		)
		val retainedCover = assertIs<ReaderPresentationFrameOwner.ShellCover>(pending.retainedFrame)

		assertEquals(partialPending.nativePresentationRequest?.token, pending.nativePresentationRequest?.token)
		assertEquals(binding, pending.nativePresentationRequest?.binding)
		assertEquals(binding, retainedCover.proof.binding)
		assertTrue(completed.effects.isEmpty())
	}

	@Test
	fun partialPreparationGenerationReplacementKeepsNativeRequestTokenExact() {
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
		val requested = readerPresentationReduce(
			readerPresentationReduce(
				ReaderPresentationState(nextTokenValue = 51L),
				ReaderPresentationEvent.PublicationOpened(partial)
			).state,
			ReaderPresentationEvent.NativePageRequested
		).state
		val replacement = partial.copy(preparationGeneration = 7L)

		val rebound = readerPresentationReduce(
			requested,
			ReaderPresentationEvent.BindingReplaced(partial, replacement)
		)
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			rebound.state.authority
		)

		assertEquals(
			ReaderNativePagePresentationRequest(ReaderPresentationToken(51L), replacement),
			pending.nativePresentationRequest
		)
		assertEquals(52L, rebound.state.nextTokenValue)
		assertEquals(ReaderPagePreparationFacts(), rebound.state.preparationFacts)
		assertEquals(ReaderDiagnosticPresentation.Hidden, rebound.decision.diagnosticPresentation)
		assertTrue(rebound.effects.isEmpty())
	}

	@Test
	fun stalePartialCompletionReleasesOnlyIncomingCompleteResources() {
		val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
		val replacement = partial.copy(preparationGeneration = 7L)
		val staleComplete = binding
		val current = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				retainedFrame = ReaderPresentationFrameOwner.Neutral,
				nativePresentationRequest = ReaderNativePagePresentationRequest(
					ReaderPresentationToken(41L),
					replacement
				)
			),
			binding = replacement,
			nextTokenValue = 42L
		)

		val stale = readerPresentationReduce(
			current,
			ReaderPresentationEvent.BindingCompleted(partial, staleComplete)
		)

		assertEquals(current, stale.state)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(null, staleComplete)),
			stale.effects
		)
	}

	private fun settledNativeState(nextTokenValue: Long = 1L) = ReaderPresentationState(
		authority = ReaderPresentationAuthority.SettledNativePage(nativeFrame),
		binding = binding,
		preparationFacts = preparationFacts(readiness = readyReadiness),
		nextTokenValue = nextTokenValue
	)

	private fun nativeProofFor(
		proofBinding: ReaderPresentationBinding,
		presentedFrame: Long
	) = ReaderNativePagePresentationProof(
		binding = proofBinding,
		transitionToken = null,
		presentedFrame = presentedFrame,
		viewportWidth = 100,
		viewportHeight = 200,
		rasterGeneration = requireNotNull(proofBinding.rasterGeneration),
		textureGeneration = requireNotNull(proofBinding.textureGeneration)
	)

	private fun closedPresentationState(): ReaderPresentationState {
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
		return closed.state
	}

	private fun assertClosedStaleRelease(
		closed: ReaderPresentationState,
		reduction: ReaderPresentationReduction,
		token: ReaderPresentationToken?,
		eventBinding: ReaderPresentationBinding
	) {
		assertEquals(closed, reduction.state)
		assertEquals(readerPresentationDecision(closed), reduction.decision)
		assertEquals(
			listOf(ReaderPresentationEffect.ReleaseStalePresentation(token, eventBinding)),
			reduction.effects
		)
	}

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
