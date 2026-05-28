package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackArtworkPolicyTest {
	@Test
	fun activeArtworkUrlPrefersExternalArtworkAndFallsBackToServerArtwork() {
		assertEquals(
			"https://coverartarchive.org/front-500.jpg",
			activeArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://coverartarchive.org/front-500.jpg"
			)
		)
		assertEquals(
			"https://navidrome.example/rest/getCoverArt?id=cover-1",
			activeArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = " "
			)
		)
		assertNull(activeArtworkUrl(serverArtworkUrl = " ", externalArtworkUrl = null))
	}

	@Test
	fun dominantColorArtworkUrlUsesExternalArtworkAsIsAndSizesServerArtwork() {
		assertEquals(
			"https://coverartarchive.org/front-500.jpg",
			dominantColorArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://coverartarchive.org/front-500.jpg"
			)
		)
		assertEquals(
			"https://navidrome.example/rest/getCoverArt?id=cover-1&size=128",
			dominantColorArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = null
			)
		)
		assertEquals(
			"https://navidrome.example/rest/getCoverArt?size=128",
			dominantColorArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt",
				externalArtworkUrl = null
			)
		)
	}

	@Test
	fun serverArtworkHeadersAreOnlyForServerArtworkRequests() {
		assertTrue(shouldSendServerArtworkHeaders(externalArtworkUrl = null))
		assertTrue(shouldSendServerArtworkHeaders(externalArtworkUrl = " "))
		assertFalse(shouldSendServerArtworkHeaders(externalArtworkUrl = "https://coverartarchive.org/front.jpg"))
	}
}
