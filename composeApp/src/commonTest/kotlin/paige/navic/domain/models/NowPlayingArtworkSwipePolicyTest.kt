package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingArtworkSwipePolicyTest {
	@Test
	fun artworkSwipeRequiresGlobalSwipeToSkip() {
		assertFalse(
			shouldEnableNowPlayingArtworkSwipe(
				swipeToSkip = false,
				artworkSwipeToSkip = true
			)
		)
	}

	@Test
	fun artworkSwipeCanBeDisabledIndependently() {
		assertFalse(
			shouldEnableNowPlayingArtworkSwipe(
				swipeToSkip = true,
				artworkSwipeToSkip = false
			)
		)
	}

	@Test
	fun artworkSwipeKeepsCurrentBehaviorWhenBothSettingsAllowIt() {
		assertTrue(
			shouldEnableNowPlayingArtworkSwipe(
				swipeToSkip = true,
				artworkSwipeToSkip = true
			)
		)
	}
}
