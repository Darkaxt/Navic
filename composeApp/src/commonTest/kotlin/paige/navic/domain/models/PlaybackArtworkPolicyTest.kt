package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.models.settings.ArtworkSourcePriority

class PlaybackArtworkPolicyTest {
	@Test
	fun activeArtworkUrlPrefersExternalArtworkAndFallsBackToServerArtwork() {
		assertEquals(
			"https://aurral.example/artists/iu.webp",
			activeArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://aurral.example/artists/iu.webp"
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
	fun dominantColorArtworkUrlPrefersExternalArtworkAndFallsBackToSizedServerArtwork() {
		assertEquals(
			"https://aurral.example/artists/iu.webp",
			dominantColorArtworkUrl(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://aurral.example/artists/iu.webp"
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
	fun externalArtworkUrlAppliesBeforeServerCoverArtWhenAvailable() {
		assertEquals(
			"https://aurral.example/artists/iu.webp",
			externalFallbackArtworkUrl(
				serverCoverArtId = "cover-1",
				externalArtworkUrl = "https://aurral.example/artists/iu.webp"
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
	fun externalArtworkCacheKeyAppliesBeforeServerCoverArtWhenAvailable() {
		assertEquals(
			"aurral:artist:iu",
			externalFallbackArtworkCacheKey(
				serverCoverArtId = "cover-1",
				externalArtworkCacheKey = "aurral:artist:iu"
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
	fun serverArtworkHeadersAreSkippedWhenExternalArtworkIsAvailable() {
		assertFalse(
			shouldSendServerArtworkHeaders(
				serverArtworkUrl = "https://navidrome.example/rest/getCoverArt?id=cover-1",
				externalArtworkUrl = "https://aurral.example/artists/iu.webp"
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

	@Test
	fun aurralFirstSuppressesVisibleServerArtworkWhileExternalHydrates() {
		assertNull(
			visiblePlaybackCoverArtId(
				serverCoverArtId = "navidrome-cover",
				externalArtworkUrl = null,
				priority = ArtworkSourcePriority.AurralFirst
			)
		)
		assertNull(
			visiblePlaybackCoverArtId(
				serverCoverArtId = "navidrome-cover",
				externalArtworkUrl = "https://aurral.example/artists/jason-ross.webp",
				priority = ArtworkSourcePriority.AurralFirst
			)
		)
		assertEquals(
			"https://aurral.example/artists/jason-ross.webp",
			visiblePlaybackImageUrl(
				serverCoverArtId = "navidrome-cover",
				externalArtworkUrl = " https://aurral.example/artists/jason-ross.webp ",
				priority = ArtworkSourcePriority.AurralFirst
			)
		)
		assertNull(
			visiblePlaybackImageUrl(
				serverCoverArtId = "navidrome-cover",
				externalArtworkUrl = null,
				priority = ArtworkSourcePriority.AurralFirst
			)
		)
	}

	@Test
	fun nativeFirstUsesServerArtworkBeforeExternalArtwork() {
		assertEquals(
			"navidrome-cover",
			visiblePlaybackCoverArtId(
				serverCoverArtId = " navidrome-cover ",
				externalArtworkUrl = "https://aurral.example/artists/jason-ross.webp",
				priority = ArtworkSourcePriority.NativeFirst
			)
		)
		assertNull(
			visiblePlaybackImageUrl(
				serverCoverArtId = "navidrome-cover",
				externalArtworkUrl = "https://aurral.example/artists/jason-ross.webp",
				priority = ArtworkSourcePriority.NativeFirst
			)
		)
		assertEquals(
			"https://aurral.example/artists/jason-ross.webp",
			visiblePlaybackImageUrl(
				serverCoverArtId = null,
				externalArtworkUrl = " https://aurral.example/artists/jason-ross.webp ",
				priority = ArtworkSourcePriority.NativeFirst
			)
		)
	}

	@Test
	fun nativeOnlyNeverUsesExternalPlaybackArtwork() {
		assertEquals(
			"navidrome-cover",
			visiblePlaybackCoverArtId(
				serverCoverArtId = " navidrome-cover ",
				externalArtworkUrl = "https://aurral.example/artists/jason-ross.webp",
				priority = ArtworkSourcePriority.NativeOnly
			)
		)
		assertNull(
			visiblePlaybackImageUrl(
				serverCoverArtId = "navidrome-cover",
				externalArtworkUrl = "https://aurral.example/artists/jason-ross.webp",
				priority = ArtworkSourcePriority.NativeOnly
			)
		)
	}

	@Test
	fun aurralEnabledForcesAurralFirstArtworkEvenWhenStoredPriorityIsNative() {
		assertEquals(
			ArtworkSourcePriority.AurralFirst,
			effectiveAurralArtworkPriority(
				aurralEnabled = true,
				configuredPriority = ArtworkSourcePriority.NativeFirst
			)
		)
		assertEquals(
			ArtworkSourcePriority.AurralFirst,
			effectiveAurralArtworkPriority(
				aurralEnabled = true,
				configuredPriority = ArtworkSourcePriority.NativeOnly
			)
		)
		assertEquals(
			ArtworkSourcePriority.NativeFirst,
			effectiveAurralArtworkPriority(
				aurralEnabled = false,
				configuredPriority = ArtworkSourcePriority.NativeFirst
			)
		)
	}
}
