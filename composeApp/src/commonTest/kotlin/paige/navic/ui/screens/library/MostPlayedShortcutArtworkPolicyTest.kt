package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
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
	fun artistShortcutsKeepVerifiedExternalSnapshotBeforeAlbumFallback() {
		val shortcut = mostPlayedArtistShortcut(
			coverArtId = " https://aurral.example.com/iu.webp "
		)

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

		assertEquals("https://aurral.example.com/iu.webp", resolved.coverArtId)
	}

	private fun mostPlayedArtistShortcut(coverArtId: String?) =
		DomainMostPlayedShortcut(
			type = PlaybackOriginType.Artist,
			id = "iu",
			title = "IU",
			subtitle = null,
			coverArtId = coverArtId,
			totalPlayedMillis = 1_000L,
			lastPlayedAt = Instant.fromEpochMilliseconds(1_000L)
		)
}
