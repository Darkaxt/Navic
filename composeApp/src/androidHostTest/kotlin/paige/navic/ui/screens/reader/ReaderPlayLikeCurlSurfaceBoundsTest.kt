package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPlayLikeCurlSurfaceBoundsTest {
	@Test
	fun portraitSurfaceStopsAtTheRenderedPageEdge() {
		assertEquals(
			expected = 1830,
			actual = readerPlayLikeCurlPortraitSurfaceWidth(
				hostWidth = 1848,
				hostHeight = 2960,
				pageBitmapWidth = 915,
				pageBitmapHeight = 1480
			)
		)
	}

	@Test
	fun portraitSurfaceNeverExceedsTheHost() {
		assertEquals(
			expected = 1848,
			actual = readerPlayLikeCurlPortraitSurfaceWidth(
				hostWidth = 1848,
				hostHeight = 2960,
				pageBitmapWidth = 1000,
				pageBitmapHeight = 1480
			)
		)
	}
}
