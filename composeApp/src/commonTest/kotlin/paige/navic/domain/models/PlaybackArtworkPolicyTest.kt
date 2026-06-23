package paige.navic.domain.models

import paige.navic.domain.models.settings.ArtworkSourcePriority
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
	fun resolvedPlaybackArtworkPrefersAurralBeforeNativeCoverWhenAurralFirst() {
		val resolved = resolvedPlaybackArtwork(
			serverCoverArtId = "navidrome-cover",
			aurralArtistImageUrl = " https://aurral.example/api/artists/jason-ross/image.jpg ",
			aurralArtistCacheKey = "artist:jason-ross",
			musicBrainzArtworkUrl = "https://coverartarchive.org/release/front.jpg",
			musicBrainzArtworkCacheKey = "musicbrainz:release-1",
			artworkSourcePriority = ArtworkSourcePriority.AurralFirst,
			aurralArtworkEnabled = true,
			musicBrainzArtworkEnabled = true
		)

		assertEquals(PlaybackArtworkSource.AurralArtist, resolved.source)
		assertNull(resolved.coverArtId)
		assertEquals("https://aurral.example/api/artists/jason-ross/image.jpg", resolved.imageUrl)
		assertEquals("aurral-artist:artist:jason-ross", resolved.imageCacheKey)
	}

	@Test
	fun resolvedPlaybackArtworkForcesAurralFirstWhenAurralIsEnabled() {
		val resolved = resolvedPlaybackArtwork(
			serverCoverArtId = "navidrome-cover",
			aurralArtistImageUrl = "https://aurral.example/api/artists/jason-ross/image.jpg",
			aurralArtistCacheKey = "artist:jason-ross",
			musicBrainzArtworkUrl = "https://coverartarchive.org/release/front.jpg",
			musicBrainzArtworkCacheKey = "musicbrainz:release-1",
			artworkSourcePriority = ArtworkSourcePriority.NativeOnly,
			aurralArtworkEnabled = true,
			musicBrainzArtworkEnabled = true
		)

		assertEquals(PlaybackArtworkSource.AurralArtist, resolved.source)
		assertNull(resolved.coverArtId)
		assertEquals("https://aurral.example/api/artists/jason-ross/image.jpg", resolved.imageUrl)
		assertEquals("aurral-artist:artist:jason-ross", resolved.imageCacheKey)
	}

	@Test
	fun resolvedPlaybackArtworkLetsNativeOnlyOptOutOfExternalArtworkWhenAurralIsDisabled() {
		val resolved = resolvedPlaybackArtwork(
			serverCoverArtId = "navidrome-cover",
			aurralArtistImageUrl = "https://aurral.example/api/artists/jason-ross/image.jpg",
			aurralArtistCacheKey = "artist:jason-ross",
			musicBrainzArtworkUrl = "https://coverartarchive.org/release/front.jpg",
			musicBrainzArtworkCacheKey = "musicbrainz:release-1",
			artworkSourcePriority = ArtworkSourcePriority.NativeOnly,
			aurralArtworkEnabled = false,
			musicBrainzArtworkEnabled = true
		)

		assertEquals(PlaybackArtworkSource.NativeCover, resolved.source)
		assertEquals("navidrome-cover", resolved.coverArtId)
		assertNull(resolved.imageUrl)
		assertNull(resolved.imageCacheKey)
	}

	@Test
	fun resolvedPlaybackArtworkFallsBackToNativeThenMusicBrainzWhenAurralMissing() {
		assertEquals(
			PlaybackArtworkResolution(
				coverArtId = "navidrome-cover",
				imageUrl = null,
				imageCacheKey = null,
				source = PlaybackArtworkSource.NativeCover
			),
			resolvedPlaybackArtwork(
				serverCoverArtId = " navidrome-cover ",
				aurralArtistImageUrl = " ",
				aurralArtistCacheKey = "artist:jason-ross",
				musicBrainzArtworkUrl = "https://coverartarchive.org/release/front.jpg",
				musicBrainzArtworkCacheKey = "musicbrainz:release-1",
				artworkSourcePriority = ArtworkSourcePriority.AurralFirst,
				aurralArtworkEnabled = true,
				musicBrainzArtworkEnabled = true
			)
		)
		assertEquals(
			PlaybackArtworkResolution(
				coverArtId = null,
				imageUrl = "https://coverartarchive.org/release/front.jpg",
				imageCacheKey = "musicbrainz:release-1",
				source = PlaybackArtworkSource.MusicBrainz
			),
			resolvedPlaybackArtwork(
				serverCoverArtId = null,
				aurralArtistImageUrl = null,
				aurralArtistCacheKey = null,
				musicBrainzArtworkUrl = " https://coverartarchive.org/release/front.jpg ",
				musicBrainzArtworkCacheKey = " musicbrainz:release-1 ",
				artworkSourcePriority = ArtworkSourcePriority.AurralFirst,
				aurralArtworkEnabled = true,
				musicBrainzArtworkEnabled = true
			)
		)
	}

	@Test
	fun resolvedPlaybackArtistPhotoMatchesSongArtistAndPrefersAurralSource() {
		val entries = listOf(
			PlaybackArtistPhotoCacheEntry(
				cacheKey = "artist:artist-1-lastfm",
				artistId = "artist-1",
				sourceArtistId = null,
				name = "Jason Ross",
				normalizedName = "jason ross",
				imageUrl = "https://lastfm.example/jason-ross.jpg",
				source = "Last.fm",
				updatedAtMillis = 200
			),
			PlaybackArtistPhotoCacheEntry(
				cacheKey = "artist:artist-1",
				artistId = "artist-1",
				sourceArtistId = null,
				name = "Jason Ross",
				normalizedName = "jason ross",
				imageUrl = "https://aurral.example/jason-ross.jpg",
				source = "Aurral",
				updatedAtMillis = 100
			)
		)

		val resolved = resolvedPlaybackArtistPhoto(
			artistId = " artist-1 ",
			artistName = "Jason Ross",
			entries = entries
		)

		assertEquals("https://aurral.example/jason-ross.jpg", resolved?.imageUrl)
		assertEquals("artist:artist-1", resolved?.cacheKey)
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
