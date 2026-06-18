package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderChapterNavigatorMappingTest {
	@Test
	fun verticalRailMapsFullTrackToEveryChapterPageEndpoint() {
		assertEquals(1, komikkuChapterRailPageForOffset(offsetY = 0f, heightPx = 1_200f, totalPages = 12))
		assertEquals(12, komikkuChapterRailPageForOffset(offsetY = 1_200f, heightPx = 1_200f, totalPages = 12))
		assertEquals(12, komikkuChapterRailPageForOffset(offsetY = 1_600f, heightPx = 1_200f, totalPages = 12))
		assertEquals(1, komikkuChapterRailPageForOffset(offsetY = -100f, heightPx = 1_200f, totalPages = 12))
		assertEquals(7, komikkuChapterRailPageForOffset(offsetY = 600f, heightPx = 1_200f, totalPages = 12))
	}

	@Test
	fun verticalRailKeepsTinyChaptersStable() {
		assertEquals(1, komikkuChapterRailPageForOffset(offsetY = 0f, heightPx = 1_200f, totalPages = 1))
		assertEquals(1, komikkuChapterRailPageForOffset(offsetY = 900f, heightPx = 0f, totalPages = 12))
		assertEquals(1, komikkuChapterRailPageForOffset(offsetY = 900f, heightPx = 1_200f, totalPages = 0))
	}
}
