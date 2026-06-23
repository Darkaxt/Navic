package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderChapterNavigatorMappingTest {
	@Test
	fun chapterProgressSliderFollowsKomikkuVisibilityRule() {
		assertFalse(readerShouldShowChapterProgressSlider(totalPages = 0))
		assertFalse(readerShouldShowChapterProgressSlider(totalPages = 1))
		assertTrue(readerShouldShowChapterProgressSlider(totalPages = 2))
		assertTrue(readerShouldShowChapterProgressSlider(totalPages = 3))
		assertTrue(readerShouldShowChapterProgressSlider(totalPages = 12))
	}
}
