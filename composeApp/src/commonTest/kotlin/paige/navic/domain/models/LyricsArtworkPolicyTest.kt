package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsArtworkPolicyTest {
	@Test
	fun lyricsArtworkRequiresSettingAndArtwork() {
		assertFalse(shouldShowLyricsArtwork(showLyricsArtwork = false, coverArtId = "cover-1"))
		assertFalse(shouldShowLyricsArtwork(showLyricsArtwork = true, coverArtId = null))
		assertFalse(shouldShowLyricsArtwork(showLyricsArtwork = true, coverArtId = " "))

		assertTrue(shouldShowLyricsArtwork(showLyricsArtwork = true, coverArtId = "cover-1"))
		assertTrue(
			shouldShowLyricsArtwork(
				showLyricsArtwork = true,
				coverArtId = null,
				imageUrl = "https://coverartarchive.org/front.jpg"
			)
		)
	}
}
