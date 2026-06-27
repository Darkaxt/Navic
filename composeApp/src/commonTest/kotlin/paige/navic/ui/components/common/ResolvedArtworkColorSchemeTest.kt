package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ResolvedArtworkColorSchemeTest {
	@Test
	fun artworkColorCacheKeySeparatesServerAndExternalArtwork() {
		val serverKey = artworkColorCacheKey(
			coverArtId = "cover-1",
			imageUrl = null,
			imageCacheKey = null
		)
		val externalKey = artworkColorCacheKey(
			coverArtId = "cover-1",
			imageUrl = "https://aurral.example/image.jpg",
			imageCacheKey = "aurral-artist:iu"
		)

		assertEquals("server:cover-1", serverKey)
		assertEquals("external:aurral-artist:iu", externalKey)
		assertNotEquals(serverKey, externalKey)
	}

	@Test
	fun artworkColorCacheKeyFallsBackToExternalUrlWhenNoCacheKeyExists() {
		assertEquals(
			"external:https://example.test/art.jpg",
			artworkColorCacheKey(
				coverArtId = null,
				imageUrl = "https://example.test/art.jpg",
				imageCacheKey = null
			)
		)
	}

	@Test
	fun artworkColorCacheKeyIgnoresBlankArtwork() {
		assertNull(
			artworkColorCacheKey(
				coverArtId = " ",
				imageUrl = "",
				imageCacheKey = null
			)
		)
	}
}
