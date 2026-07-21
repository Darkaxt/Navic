package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageOperationPolicyTest {
	@Test
	fun settlingRejectsNewPointersButContinuesSettlement() {
		val policy = readerPageOperationPolicy(
			ReaderPageReadinessState(
				textureDeck = ReaderTextureDeckState.Settling,
				interaction = ReaderPageInteractionState.Settling
			)
		)

		assertEquals(
			ReaderPageNewPointerDecision.Reject(ReaderPageGestureTerminalOutcome.RejectedSettling),
			policy.newPointer
		)
		assertTrue(policy.continueSettlement)
		assertFalse(policy.cancelForReadinessChange)
	}

	@Test
	fun preparingDeckRejectsNewPointersDuringBackgroundPrefetch() {
		val policy = readerPageOperationPolicy(
			ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BackgroundPrefetch
			)
		)

		assertEquals(
			ReaderPageNewPointerDecision.Reject(ReaderPageGestureTerminalOutcome.RejectedPreparing),
			policy.newPointer
		)
	}

	@Test
	fun readyDeckAcceptsNewPointersDuringBackgroundPrefetch() {
		val policy = readerPageOperationPolicy(
			ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Generating,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.BackgroundPrefetch
			)
		)

		assertEquals(ReaderPageNewPointerDecision.Accept, policy.newPointer)
	}

	@Test
	fun pendingDeckPreparationDoesNotBlockAnActiveReadyDeck() {
		val policy = readerPageOperationPolicy(
			ReaderPageReadinessState(
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				pendingTextureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.Ready
			)
		)

		assertEquals(ReaderPageNewPointerDecision.Accept, policy.newPointer)
		assertTrue(policy.continueActivePointer)
	}
}
