package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AlbumSortPolicyTest {
	@Test
	fun albumsSortByYearFromRecentToOldestWithUnknownYearsLast() {
		val albums = listOf(
			album(id = "unknown", name = "No Year", year = null),
			album(id = "2020-b", name = "Beta", year = 2020),
			album(id = "2024", name = "Recent", year = 2024),
			album(id = "2020-a", name = "Alpha", year = 2020),
			album(id = "1999", name = "Old", year = 1999)
		)

		assertEquals(
			listOf("2024", "2020-a", "2020-b", "1999", "unknown"),
			albums.sortedByAlbumYearDescending().map { it.id }
		)
	}

	@Test
	fun mixedAurralAlbumRowsSortByYearFromRecentToOldest() {
		val localOld = album(id = "local-old", name = "Local Old", year = 2001)
		val localRecent = album(id = "local-recent", name = "Local Recent", year = 2024)
		val missingMiddle = missingAlbum(id = "missing-middle", title = "Missing Middle", year = "2016")
		val missingUnknown = missingAlbum(id = "missing-unknown", title = "Missing Unknown", year = null)

		assertEquals(
			listOf("Local Recent", "Missing Middle", "Local Old", "Missing Unknown"),
			aurralArtistAlbumRows(
				localAlbums = listOf(localOld, localRecent),
				missingAlbums = listOf(missingUnknown, missingMiddle)
			).map { it.title }
		)
	}

	@Test
	fun aurralMissingAlbumRowsSortByYearFromRecentToOldest() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist",
			artistName = "Artist",
			releaseGroups = listOf(
				releaseGroup(id = "old", title = "Old", firstReleaseDate = "2001-02-03"),
				releaseGroup(id = "unknown", title = "Unknown", firstReleaseDate = null),
				releaseGroup(id = "recent", title = "Recent", firstReleaseDate = "2024")
			)
		)

		assertEquals(
			listOf("Recent", "Old", "Unknown"),
			aurralMissingAlbumRows(enrichment, localAlbums = emptyList()).map { it.title }
		)
	}

	private fun album(
		id: String,
		name: String,
		year: Int?
	) = DomainAlbum(
		id = id,
		name = name,
		artistName = "Artist",
		artistId = "artist",
		year = year,
		coverArtId = id,
		genre = null,
		genres = emptyList(),
		songCount = 0,
		duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST,
		starredAt = null,
		lastPlayedAt = null,
		playCount = 0,
		userRating = null,
		version = null,
		musicBrainzId = null,
		songs = emptyList()
	)

	private fun missingAlbum(
		id: String,
		title: String,
		year: String?
	) = AurralMissingAlbumRow(
		releaseGroup = AurralReleaseGroup(
			id = id,
			title = title,
			firstReleaseDate = year
		),
		title = title,
		year = year,
		coverUrl = null,
		requestStatus = null,
		requestable = true
	)

	private fun releaseGroup(
		id: String,
		title: String,
		firstReleaseDate: String?
	) = AurralReleaseGroup(
		id = id,
		title = title,
		firstReleaseDate = firstReleaseDate
	)
}
