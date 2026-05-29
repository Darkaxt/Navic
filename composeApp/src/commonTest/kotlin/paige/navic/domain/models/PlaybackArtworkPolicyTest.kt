package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackArtworkPolicyTest {
	@Test
	fun activeArtworkUrlPrefersServerArtworkAndFallsBackToExternalArtwork() {
		assertEquals(
			"https://navidrome.example/rest/getCoverArt?id=cover-1",
			activeArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://coverartarchive.org/front-500.jpg"
			)
		)
		assertEquals(
			"https://coverartarchive.org/front-500.jpg",
			activeArtworkUrl(
				serverArtworkUrl = " ",
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
	fun dominantColorArtworkUrlPrefersSizedServerArtworkAndFallsBackToExternalArtwork() {
		assertEquals(
			"https://navidrome.example/rest/getCoverArt?id=cover-1&size=128",
			dominantColorArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://coverartarchive.org/front-500.jpg"
			)
		)
		assertEquals(
			"https://coverartarchive.org/front-500.jpg",
			dominantColorArtworkUrl(
				serverArtworkUrl = null,
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
	fun externalFallbackArtworkOnlyAppliesWhenServerCoverArtIsMissing() {
		assertNull(
			externalFallbackArtworkUrl(
				serverCoverArtId = "cover-1",
				externalArtworkUrl = "https://coverartarchive.org/front.jpg"
			)
		)
		assertEquals(
			"https://coverartarchive.org/front.jpg",
			externalFallbackArtworkUrl(
				serverCoverArtId = " ",
				externalArtworkUrl = " https://coverartarchive.org/front.jpg "
			)
		)
		assertNull(
			externalFallbackArtworkUrl(
				serverCoverArtId = null,
				externalArtworkUrl = " "
			)
		)
		assertEquals(
			"https://coverartarchive.org/front.jpg",
			externalFallbackArtworkUrl(
				serverCoverArtId = "cover-1",
				externalArtworkUrl = " https://coverartarchive.org/front.jpg ",
				serverCoverLoadFailed = true
			)
		)
	}

	@Test
	fun externalFallbackCacheKeyOnlyAppliesWhenServerCoverArtIsMissing() {
		assertNull(
			externalFallbackArtworkCacheKey(
				serverCoverArtId = "cover-1",
				externalArtworkCacheKey = "musicbrainz:release-1"
			)
		)
		assertEquals(
			"musicbrainz:release-1",
			externalFallbackArtworkCacheKey(
				serverCoverArtId = null,
				externalArtworkCacheKey = " musicbrainz:release-1 "
			)
		)
		assertEquals(
			"musicbrainz:release-1",
			externalFallbackArtworkCacheKey(
				serverCoverArtId = "cover-1",
				externalArtworkCacheKey = " musicbrainz:release-1 ",
				serverCoverLoadFailed = true
			)
		)
	}

	@Test
	fun serverArtworkHeadersAreOnlyForServerArtworkRequests() {
		assertTrue(
			shouldSendServerArtworkHeaders(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://coverartarchive.org/front.jpg"
			)
		)
		assertTrue(shouldSendServerArtworkHeaders(serverArtworkUrl = null, externalArtworkUrl = null))
		assertTrue(shouldSendServerArtworkHeaders(serverArtworkUrl = " ", externalArtworkUrl = " "))
		assertFalse(
			shouldSendServerArtworkHeaders(
				serverArtworkUrl = null,
				externalArtworkUrl = "https://coverartarchive.org/front.jpg"
			)
		)
	}
}
