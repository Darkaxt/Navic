package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPagePreparationPolicyTest {
	@Test
	fun rawPreparationProgressUsesNormalizedCompletedOverRequired() {
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

		assertEquals(3, state.completedCount)
		assertEquals(12, state.requiredCount)
		assertEquals(0.25f, state.progress)
	}

	@Test
	fun rendererReadinessCannotOverrideBlockingPreparationInteraction() {
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
		assertEquals(ReaderTextureDeckState.Ready, merged.readiness.textureDeck)
	}

	@Test
	fun rendererReadinessCannotOverridePreparationFailure() {
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
		assertEquals(true, merged.retryable)
	}

	@Test
	fun presentationFactsExposeOnlySanitizedFailureAndRawCounters() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			preparationGeneration = 9L,
			requiredCount = 4,
			completedCount = 1,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.Failed
			),
			activePageNumber = 8,
			error = "internal failure detail",
			retryable = true
		)

		assertEquals(
			ReaderPagePreparationFacts(
				phase = ReaderPagePreparationPhase.Failed,
				generation = 9L,
				completedCount = 1,
				requiredCount = 4,
				readiness = state.readiness,
				failure = ReaderPresentationFailureReason.PreparationFailed,
				retryable = true
			),
			state.toPresentationFacts()
		)
	}

	@Test
	fun nonfailurePresentationFactsCarryNoDiagnosticReason() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertNull(state.toPresentationFacts().failure)
	}
}
