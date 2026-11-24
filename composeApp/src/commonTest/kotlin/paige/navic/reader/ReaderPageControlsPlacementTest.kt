package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageControlsPlacementTest {
	private val auto = ReaderSettings()

	@Test
	fun landscapeTabletPagedShowsTwoPages() {
		assertTrue(readerPagedLayoutShowsTwoPages(auto, widthDp = 1480f, heightDp = 924f))
	}

	@Test
	fun portraitSquareScrolledAndSingleColumnShowOnePage() {
		assertFalse(readerPagedLayoutShowsTwoPages(auto, widthDp = 924f, heightDp = 1480f))
		assertFalse(readerPagedLayoutShowsTwoPages(auto, widthDp = 1000f, heightDp = 950f))
		assertFalse(readerPagedLayoutShowsTwoPages(auto.copy(flowMode = ReaderFlowScrolled), 1480f, 924f))
		assertFalse(readerPagedLayoutShowsTwoPages(auto.copy(maxColumnCount = 1), 1480f, 924f))
		// Auto columns: a landscape surface narrower than one column threshold stays single.
		assertFalse(readerPagedLayoutShowsTwoPages(auto, widthDp = 700f, heightDp = 400f))
		assertTrue(readerPagedLayoutShowsTwoPages(auto.copy(maxColumnCount = 2), 700f, 400f))
	}
}
