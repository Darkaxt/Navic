package paige.navic.ui.screens.reader

import karacken.curl.DeckRejectionReason
import karacken.curl.PageSurfaceDeckSubmissionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageDeckSubmissionCallbackFenceTest {
	@Test
	fun returnedCapacityRejectionIsCapturedWithoutLegacyReleaseHandling() {
		val fence = ReaderPageDeckSubmissionCallbackFence()

		val result = fence.submit(7L) {
			assertTrue(
				fence.onDeckRejected(7L, DeckRejectionReason.RESOURCE_CAPACITY)
			)
			PageSurfaceDeckSubmissionResult.rejected(
				DeckRejectionReason.RESOURCE_CAPACITY
			)
		}

		assertEquals(PageSurfaceDeckSubmissionResult.Status.REJECTED, result.status)
		assertEquals(DeckRejectionReason.RESOURCE_CAPACITY, result.rejectionReason)
	}

	@Test
	fun submissionResultAndSynchronousCallbackMustAgree() {
		val fence = ReaderPageDeckSubmissionCallbackFence()

		assertFailsWith<IllegalStateException> {
			fence.submit(8L) {
				assertTrue(fence.onDeckRejected(8L, DeckRejectionReason.INVALID_CONTENT))
				PageSurfaceDeckSubmissionResult.rejected(
					DeckRejectionReason.RESOURCE_CAPACITY
				)
			}
		}
	}

	@Test
	fun unrelatedRejectionPassesThrough() {
		val fence = ReaderPageDeckSubmissionCallbackFence()

		val result = fence.submit(9L) {
			assertFalse(fence.onDeckRejected(10L, DeckRejectionReason.INVALID_CONTENT))
			PageSurfaceDeckSubmissionResult.accepted()
		}

		assertEquals(PageSurfaceDeckSubmissionResult.Status.ACCEPTED, result.status)
	}

	@Test
	fun duplicateMatchingCallbackIsRejected() {
		val fence = ReaderPageDeckSubmissionCallbackFence()

		assertFailsWith<IllegalStateException> {
			fence.submit(11L) {
				assertTrue(fence.onDeckRejected(11L, DeckRejectionReason.INVALID_CONTENT))
				fence.onDeckRejected(11L, DeckRejectionReason.INVALID_CONTENT)
				PageSurfaceDeckSubmissionResult.rejected(DeckRejectionReason.INVALID_CONTENT)
			}
		}
	}

	@Test
	fun fenceCanBeReusedAfterSubmissionThrows() {
		val fence = ReaderPageDeckSubmissionCallbackFence()

		assertFailsWith<IllegalArgumentException> {
			fence.submit(12L) { throw IllegalArgumentException("submission") }
		}
		val result = fence.submit(13L) {
			PageSurfaceDeckSubmissionResult.unchanged()
		}

		assertEquals(PageSurfaceDeckSubmissionResult.Status.UNCHANGED, result.status)
	}
}
