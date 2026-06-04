package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.ui.screens.library.components.mostPlayedShortcutArtwork

class MostPlayedShortcutArtworkPolicyTest {
	@Test
	fun absoluteArtistImageUrlsAreRenderedAsImageUrlsNotServerCoverIds() {
		val artwork = mostPlayedShortcutArtwork("https://example.com/artist.jpg")

		assertNull(artwork.coverArtId)
		assertEquals("https://example.com/artist.jpg", artwork.imageUrl)
	}

	@Test
	fun serverCoverIdsStayAsCoverArtIds() {
		val artwork = mostPlayedShortcutArtwork("cover-123")

		assertEquals("cover-123", artwork.coverArtId)
		assertNull(artwork.imageUrl)
	}

	@Test
	fun artistShortcutsPreferCachedExternalPhotoWhenAurralIsFirst() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "aurral-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://tadb.example.com/iu.webp",
					trustedExternalPhoto = true
				),
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = "ar-iu-native",
					artistImageUrl = "https://navidrome.example.com/protected/iu.jpg?token=expired"
				)
			),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			),
			artistArtworkPriority = ArtworkSourcePriority.AurralFirst,
			aurralArtworkEnabled = true
		).single()

		assertEquals("https://tadb.example.com/iu.webp", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsPreferNativeArtistCoverWhenNativeIsFirst() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "aurral-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://tadb.example.com/iu.webp",
					trustedExternalPhoto = true
				),
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = "ar-iu-native",
					artistImageUrl = "https://navidrome.example.com/protected/iu.jpg?token=expired"
				)
			),
			albums = emptyList(),
			artistArtworkPriority = ArtworkSourcePriority.NativeFirst,
			aurralArtworkEnabled = true
		).single()

		assertEquals("ar-iu-native", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsIgnoreCachedExternalPhotoWhenAurralArtworkIsDisabled() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "aurral-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://tadb.example.com/iu.webp",
					trustedExternalPhoto = true
				),
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = "ar-iu-native",
					artistImageUrl = null
				)
			),
			albums = emptyList(),
			artistArtworkPriority = ArtworkSourcePriority.AurralFirst,
			aurralArtworkEnabled = false
		).single()

		assertEquals("ar-iu-native", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsUseAlbumCoverWhenStoredSnapshotHasNoArtwork() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				)
			),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			)
		).single()

		assertEquals("iu-album-cover", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsPreferPlayedSongArtworkBeforeAlbumFallback() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = emptyList(),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			),
			songs = listOf(
				MostPlayedShortcutSongArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-song-cover",
					year = 2024,
					albumTitle = "IU Single",
					title = "Love wins all",
					playCount = 12
				)
			)
		).single()

		assertEquals("iu-song-cover", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsSkipNonAbsoluteArtistImageUrlsForVerifiedArtistPhoto() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "/rest/getArtistImage?id=iu"
				),
				MostPlayedShortcutArtistArtwork(
					id = "aurral-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://aurral.example.com/iu.webp",
					trustedExternalPhoto = true
				)
			),
			albums = emptyList(),
			songs = emptyList()
		).single()

		assertEquals("https://aurral.example.com/iu.webp", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsCanResolveAlbumArtworkByNormalizedArtistName() {
		val shortcut = mostPlayedArtistShortcut(id = "aurral-artist-id", title = "  Iu  ", coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = emptyList(),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "different-local-id",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			)
		).single()

		assertEquals("iu-album-cover", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsKeepVerifiedExternalArtistPhotoBeforeAlbumFallback() {
		val shortcut = mostPlayedArtistShortcut(
			coverArtId = " https://navidrome.example.com/protected/iu.jpg?token=expired "
		)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://aurral.example.com/iu.webp",
					trustedExternalPhoto = true
				)
			),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			)
		).single()

		assertEquals("https://aurral.example.com/iu.webp", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsDoNotLetAbsoluteServerArtistSnapshotsBlockAlbumFallback() {
		val shortcut = mostPlayedArtistShortcut(
			coverArtId = " https://navidrome.example.com/protected/iu.jpg?token=expired "
		)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://navidrome.example.com/protected/iu.jpg?token=expired"
				)
			),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			),
			songs = emptyList()
		).single()

		assertEquals("iu-album-cover", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsPreferNativeArtistCoverOverAlbumFallback() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = "artist-cover-that-falls-back")

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "iu",
					name = "IU",
					coverArtId = "artist-cover-that-falls-back",
					artistImageUrl = null
				)
			),
			albums = listOf(
				MostPlayedShortcutAlbumArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-album-cover",
					year = 2021,
					name = "IU Album"
				)
			),
			songs = emptyList()
		).single()

		assertEquals("artist-cover-that-falls-back", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsUseSongCoverWhenArtistAndAlbumArtworkAreMissing() {
		val shortcut = mostPlayedArtistShortcut(coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = emptyList(),
			albums = emptyList(),
			songs = listOf(
				MostPlayedShortcutSongArtwork(
					artistId = "iu",
					artistName = "IU",
					coverArtId = "iu-song-cover",
					year = 2021,
					albumTitle = "IU Single",
					title = "Celebrity",
					playCount = 12
				)
			)
		).single()

		assertEquals("iu-song-cover", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsCanResolveSongArtworkByNormalizedArtistName() {
		val shortcut = mostPlayedArtistShortcut(id = "aurral-artist-id", title = "  Iu  ", coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = emptyList(),
			albums = emptyList(),
			songs = listOf(
				MostPlayedShortcutSongArtwork(
					artistId = "different-local-id",
					artistName = "IU",
					coverArtId = "iu-song-cover",
					year = 2021,
					albumTitle = "IU Single",
					title = "Celebrity",
					playCount = 12
				)
			)
		).single()

		assertEquals("iu-song-cover", resolved.coverArtId)
	}

	@Test
	fun artistShortcutsCanResolveSongArtworkByArtistNameToken() {
		val shortcut = mostPlayedArtistShortcut(id = "aurral-artist-id", title = "IU", coverArtId = null)

		val resolved = mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = listOf(shortcut),
			artists = emptyList(),
			albums = emptyList(),
			songs = listOf(
				MostPlayedShortcutSongArtwork(
					artistId = "different-local-id",
					artistName = "IU (아이유)",
					coverArtId = "iu-song-cover",
					year = 2024,
					albumTitle = "IU Single",
					title = "Love wins all",
					playCount = 12
				)
			)
		).single()

		assertEquals("iu-song-cover", resolved.coverArtId)
	}

	private fun mostPlayedArtistShortcut(
		id: String = "iu",
		title: String = "IU",
		coverArtId: String?
	) =
		DomainMostPlayedShortcut(
			type = PlaybackOriginType.Artist,
			id = id,
			title = title,
			subtitle = null,
			coverArtId = coverArtId,
			totalPlayedMillis = 1_000L,
			lastPlayedAt = Instant.fromEpochMilliseconds(1_000L)
		)
}
