package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWhispersyncAuthorityRestorationTest {
	@Test
	fun activeOverlayWithoutAnAnchorRequestsRestorationOnce() {
		assertTrue(
			readerWhispersyncAuthorityRestorationRequested(
				previousActive = false,
				previousAnchorAvailable = false,
				active = true,
				anchorAvailable = false
			)
		)
		assertFalse(
			readerWhispersyncAuthorityRestorationRequested(
				previousActive = true,
				previousAnchorAvailable = false,
				active = true,
				anchorAvailable = false
			)
		)
	}

	@Test
	fun losingAnEstablishedAnchorRequestsRestoration() {
		assertTrue(
			readerWhispersyncAuthorityRestorationRequested(
				previousActive = true,
				previousAnchorAvailable = true,
				active = true,
				anchorAvailable = false
			)
		)
		assertFalse(
			readerWhispersyncAuthorityRestorationRequested(
				previousActive = true,
				previousAnchorAvailable = true,
				active = false,
				anchorAvailable = false
			)
		)
	}
}
