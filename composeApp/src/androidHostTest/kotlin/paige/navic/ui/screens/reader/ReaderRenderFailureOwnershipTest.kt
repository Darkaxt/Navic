package paige.navic.ui.screens.reader

import karacken.curl.RenderFailureReason
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRenderFailureOwnershipTest {
	@Test
	fun supersededRecoverableFailureCannotReplaceCurrentPresentation() {
		assertFalse(
			readerRenderFailureOwnsCurrentPresentation(
				generationId = 40L,
				activeGenerationId = 42L,
				pendingGenerationId = 43L,
				reason = RenderFailureReason.CONTEXT,
				isRecoverable = true
			)
		)
	}

	@Test
	fun activeAndPendingGenerationFailuresRemainActionable() {
		assertTrue(
			readerRenderFailureOwnsCurrentPresentation(
				generationId = 42L,
				activeGenerationId = 42L,
				pendingGenerationId = 43L,
				reason = RenderFailureReason.CONTEXT,
				isRecoverable = true
			)
		)
		assertTrue(
			readerRenderFailureOwnsCurrentPresentation(
				generationId = 43L,
				activeGenerationId = 42L,
				pendingGenerationId = 43L,
				reason = RenderFailureReason.CONTEXT,
				isRecoverable = true
			)
		)
	}

	@Test
	fun unrecoverableContextFailureInvalidatesPresentationAcrossGenerations() {
		assertTrue(
			readerRenderFailureOwnsCurrentPresentation(
				generationId = 40L,
				activeGenerationId = 42L,
				pendingGenerationId = 43L,
				reason = RenderFailureReason.CONTEXT,
				isRecoverable = false
			)
		)
	}
}
