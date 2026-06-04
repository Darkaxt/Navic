package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType

class MostPlayedArtistPhotoCachePolicyTest {
	@Test
	fun cachedArtistPhotoMatchesMostPlayedArtistByLocalArtistId() {
		val shortcut = mostPlayedArtistShortcut(id = "local-iu", title = "IU", coverArtId = null)

		val resolved = mostPlayedArtistPhotoCacheArtworkForShortcut(
			shortcut = shortcut,
			entries = listOf(
				MostPlayedArtistPhotoCacheEntry(
					artistId = "local-iu",
					sourceArtistId = "musicbrainz-iu",
					name = "아이유",
					normalizedName = "iu",
					imageUrl = "https://aurral.example.com/artist/iu.webp"
				)
			)
		)

		assertEquals("https://aurral.example.com/artist/iu.webp", resolved?.artistImageUrl)
	}

	@Test
	fun cachedArtistPhotoMatchesMostPlayedArtistByNormalizedNameAfterRestart() {
		val shortcut = mostPlayedArtistShortcut(id = "different-local-id", title = "Lindsey   Stirling", coverArtId = null)

		val resolved = mostPlayedArtistPhotoCacheArtworkForShortcut(
			shortcut = shortcut,
			entries = listOf(
				MostPlayedArtistPhotoCacheEntry(
					artistId = null,
					sourceArtistId = "musicbrainz-lindsey",
					name = "Lindsey Stirling",
					normalizedName = "lindsey stirling",
					imageUrl = "https://aurral.example.com/artist/lindsey.webp"
				)
			)
		)

		assertEquals("https://aurral.example.com/artist/lindsey.webp", resolved?.artistImageUrl)
	}

	@Test
	fun cachedArtistPhotoIgnoresNonAbsoluteImageUrls() {
		val shortcut = mostPlayedArtistShortcut(id = "local-iu", title = "IU", coverArtId = null)

		val resolved = mostPlayedArtistPhotoCacheArtworkForShortcut(
			shortcut = shortcut,
			entries = listOf(
				MostPlayedArtistPhotoCacheEntry(
					artistId = "local-iu",
					sourceArtistId = null,
					name = "IU",
					normalizedName = "iu",
					imageUrl = "/rest/getArtistImage?id=iu"
				)
			)
		)

		assertNull(resolved)
	}

	@Test
	fun persistedArtistPhotoUsesShortcutTitleAsNormalizedName() {
		val shortcut = mostPlayedArtistShortcut(id = "local-iu", title = "IU", coverArtId = null)

		val entity = mostPlayedArtistPhotoCacheEntity(
			shortcut = shortcut,
			artist = MostPlayedShortcutArtistArtwork(
				id = "aurral-iu",
				name = "아이유",
				coverArtId = null,
				artistImageUrl = "https://aurral.example.com/artist/iu.webp"
			),
			nowMillis = 1_000L
		)

		assertNotNull(entity)
		assertEquals("iu", entity.normalizedName)
	}

	private fun mostPlayedArtistShortcut(
		id: String,
		title: String,
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
