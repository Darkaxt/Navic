package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPagePreparationPolicyTest {
	@Test
	fun blockingCoverShowsIndeterminateProgressBeforeRasterCountsExist() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertTrue(state.showsProgress)
		assertFalse(state.hasDeterminateProgress)
	}

	@Test
	fun failedCoverDoesNotShowPreparationProgress() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.Failed
			)
		)

		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertFalse(state.showsProgress)
	}

	@Test
	fun rendererFinalizationUsesIndeterminateProgressAfterRasterCountsFinish() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Ready,
			requiredCount = 3,
			completedCount = 3,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 3,
			readiness = ReaderPageReadinessState(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertTrue(state.showsProgress)
		assertFalse(state.hasDeterminateProgress)
	}

	@Test
	fun progressUsesCompletedOverRequired() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 12,
			completedCount = 3,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Hydrating,
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(0.25f, state.progress)
		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertTrue(state.showsProgress)
		assertTrue(state.hasDeterminateProgress)
	}

	@Test
	fun currentNextAndPreviousMakeReaderInteractive() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 12,
			completedCount = 3,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 3,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.BackgroundPrefetch
			)
		)

		assertTrue(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Hidden, state.presentation)
	}

	@Test
	fun rendererDeckCannotReleaseCoverWhileTheBlockingWindowIsIncomplete() {
		val preparing = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 11,
			completedCount = 3,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 3,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Empty,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = preparing.withRendererReadiness(
			ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready
			)
		)

		assertEquals(
			ReaderPageInteractionState.BlockingInitialPreparation,
			merged.readiness.interaction
		)
		assertEquals(ReaderPagePreparationPresentation.Cover, merged.presentation)
		assertFalse(merged.interactiveReady)
		assertEquals(
			ReaderPagePreparationGestureDisposition.ConsumeWhilePreparing,
			merged.gestureDisposition
		)
	}

	@Test
	fun availableBoundaryNeighborsAreEnoughForInteractiveReadiness() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Ready,
			requiredCount = 2,
			completedCount = 2,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 2,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Ready,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready
			)
		)

		assertTrue(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Hidden, state.presentation)
	}

	@Test
	fun backgroundPrefetchKeepsTheVisibleDeckInteractive() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 6,
			completedCount = 0,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.BackgroundPrefetch
			),
			activePageNumber = 8
		)

		assertTrue(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Hidden, state.presentation)
		assertFalse(state.showsProgress)
		assertEquals("Page 8", state.activePageLabel)
		assertEquals(ReaderPagePreparationGestureDisposition.Allow, state.gestureDisposition)
	}

	@Test
	fun profileRegenerationIsExplicitlyBlockingEvenAfterEarlierPreparation() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 12,
			completedCount = 0,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Hydrating,
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingProfileRegeneration
			)
		)

		assertFalse(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertEquals(
			ReaderPagePreparationGestureDisposition.ConsumeWhilePreparing,
			state.gestureDisposition
		)
	}

	@Test
	fun subsequentFailureRemainsVisibleAndRetryable() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Failed,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Failed
			),
			error = "Page preparation failed",
			retryable = true
		)

		assertEquals(ReaderPagePreparationPresentation.Compact, state.presentation)
		assertTrue(state.retryable)
	}

	@Test
	fun initialFailureStaysVisibleAndRetryable() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Failed,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Failed,
				textureDeck = ReaderTextureDeckState.Empty,
				interaction = ReaderPageInteractionState.Failed
			),
			error = "Page preparation failed",
			retryable = true
		)

		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertTrue(state.retryable)
		assertEquals("Page preparation failed", state.error)
	}

	@Test
	fun rendererReadyCannotHideInitialPersistenceFailure() {
		val failed = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 2,
			completedCount = 0,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Failed,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Failed,
				textureDeck = ReaderTextureDeckState.Empty,
				interaction = ReaderPageInteractionState.Failed
			),
			error = "Page preparation failed",
			retryable = true
		)

		val merged = failed.withRendererReadiness(
			ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready
			)
		)

		assertEquals(ReaderPageInteractionState.Failed, merged.readiness.interaction)
		assertEquals(ReaderTextureDeckState.Ready, merged.readiness.textureDeck)
		assertEquals(ReaderPagePreparationPresentation.Cover, merged.presentation)
		assertTrue(merged.retryable)
		assertEquals(
			ReaderPageNewPointerDecision.Reject(
				ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
			),
			merged.operationPolicy.newPointer
		)
	}

	@Test
	fun rendererDeckMakesSubsequentPersistenceFailureCompactButStillVisible() {
		val failed = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 3,
			completedCount = 2,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 2,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Failed,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Empty,
				interaction = ReaderPageInteractionState.Failed
			),
			retryable = true
		)

		val merged = failed.withRendererReadiness(
			ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready
			)
		)

		assertEquals(ReaderPageInteractionState.Failed, merged.readiness.interaction)
		assertEquals(ReaderTextureDeckState.Ready, merged.readiness.textureDeck)
		assertEquals(ReaderPagePreparationPresentation.Compact, merged.presentation)
		assertTrue(merged.retryable)
	}
}
