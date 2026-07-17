package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderPageTurnBundleTest {
	@Test
	fun animationBitmapDimensionsFollowConfiguredQuality() {
		assertEquals(250, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Low))
		assertEquals(500, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Balanced))
		assertEquals(750, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.High))
		assertEquals(1_000, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Native))
	}

	@Test
	fun snapshotIdentityIncludesBitmapQuality() {
		val balanced = ReaderPageSlideSnapshotKey(
			visualPageIndex = 7,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			bitmapQuality = ReaderPageBitmapQuality.Balanced,
			bitmapWidth = 500,
			bitmapHeight = 800,
			surfaceWidth = 1_000,
			surfaceHeight = 1_600
		)
		val high = balanced.copy(bitmapQuality = ReaderPageBitmapQuality.High)

		assertNotEquals(balanced, high)
	}

	@Test
	fun snapshotWindowWarmsForwardReadingBeforeBackwardHistory() {
		assertEquals(
			listOf(6, 7, 8, 5, 4),
			readerPageSlideSnapshotWindow(centerPageIndex = 6, step = 1, pageCount = 12)
		)
	}

	@Test
	fun snapshotWindowUsesSpreadStepsAndClipsBookBoundaries() {
		assertEquals(
			listOf(2, 4, 6, 0),
			readerPageSlideSnapshotWindow(centerPageIndex = 2, step = 2, pageCount = 8)
		)
		assertEquals(
			listOf(6, 4, 2),
			readerPageSlideSnapshotWindow(centerPageIndex = 6, step = 2, pageCount = 8)
		)
	}

}
