package paige.navic.ui.screens.reader

import karacken.curl.PageSurfaceDisposalStage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ReaderPageTeardownFailureTest {
	@Test
	fun rendererFailureRetainsTypedStageAndNestedSuppressedCount() = runTest {
		val cause = IllegalStateException().apply {
			addSuppressed(IllegalArgumentException())
		}

		val failure = assertFailsWith<ReaderPageTeardownException> {
			readerPageTeardownStage(
				ReaderPageTeardownStage.RendererDisposal,
				PageSurfaceDisposalStage.GL_RENDERER_DISPOSE
			) {
				throw cause
			}
		}

		assertEquals(
			ReaderPageTeardownStage.RendererDisposal,
			failure.stage
		)
		assertEquals(
			PageSurfaceDisposalStage.GL_RENDERER_DISPOSE,
			failure.rendererStage
		)
		assertSame(cause, failure.cause)
		assertEquals(1, failure.totalSuppressedFailureCount())
	}

	@Test
	fun laterOwnerFailureIsSuppressedOnFirstTypedFailure() = runTest {
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = {},
			closeRendererAndAdapter = {
				throw IllegalStateException()
			},
			closeBundleOwners = {
				throw IllegalArgumentException()
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}
		assertEquals(
			ReaderPageTeardownStage.RendererDisposal,
			failure.stage
		)
		assertEquals(1, failure.suppressed.size)
		assertEquals(
			ReaderPageTeardownStage.BundleOwners,
			(failure.suppressed.single() as ReaderPageTeardownException).stage
		)
		assertEquals(1, failure.totalSuppressedFailureCount())
	}
}
