package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderChapterNavigatorMappingTest {
	@Test
	fun chapterProgressSliderOnlyShowsForSectionsWithEnoughPages() {
		assertFalse(readerShouldShowChapterProgressSlider(totalPages = 0))
		assertFalse(readerShouldShowChapterProgressSlider(totalPages = 1))
		assertFalse(readerShouldShowChapterProgressSlider(totalPages = 2))
		assertTrue(readerShouldShowChapterProgressSlider(totalPages = 3))
		assertTrue(readerShouldShowChapterProgressSlider(totalPages = 12))
	}

	@Test
	fun verticalChapterProgressRailMapsPhysicalEndpointsToChapterEndpoints() {
		assertEquals(
			expected = 1,
			actual = readerPageForVerticalChapterProgressOffset(
				offsetY = 0f,
				railHeight = 1000f,
				totalPages = 44
			)
		)
		assertEquals(
			expected = 44,
			actual = readerPageForVerticalChapterProgressOffset(
				offsetY = 1000f,
				railHeight = 1000f,
				totalPages = 44
			)
		)
		assertEquals(
			expected = 23,
			actual = readerPageForVerticalChapterProgressOffset(
				offsetY = 500f,
				railHeight = 1000f,
				totalPages = 44
			)
		)
	}
}
