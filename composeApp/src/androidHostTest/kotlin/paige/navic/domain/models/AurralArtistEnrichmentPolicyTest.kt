package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AurralArtistEnrichmentPolicyTest {
	@Test
	fun missingAlbumRowsExcludeOwnedAlbumsByMbidAndNormalizedTitle() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "The Artist",
			releaseGroups = listOf(
				releaseGroup(id = "rg-owned-mbid", title = "Owned by MBID"),
				releaseGroup(id = "rg-owned-title", title = "Acoustic Sessions"),
				releaseGroup(id = "rg-missing", title = "Future Record")
			)
		)
		val localAlbums = listOf(
			album(name = "Different local title", musicBrainzId = "rg-owned-mbid"),
			album(name = " acoustic sessions ", musicBrainzId = null)
		)

		val rows = aurralMissingAlbumRows(enrichment, localAlbums)

		assertEquals(listOf("rg-missing"), rows.map { it.releaseGroup.id })
		assertEquals("Future Record", rows.single().title)
		assertTrue(rows.single().requestable)
		assertNull(rows.single().requestStatus)
	}

	@Test
	fun missingAlbumRowsSurfaceRequestStatusAndCoverUrl() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "The Artist",
			releaseGroups = listOf(
				releaseGroup(
					id = "rg-requested",
					title = "Requested Record",
					firstReleaseDate = "2026-05-31",
					coverUrl = "https://aurral.example.com/api/artists/release-group/rg-requested/cover"
				)
			),
			requests = listOf(
				AurralAlbumRequest(
					albumMbid = "rg-requested",
					albumName = "Requested Record",
					artistMbid = "artist-mbid",
					artistName = "The Artist",
					status = "processing"
				)
			)
		)

		val row = aurralMissingAlbumRows(enrichment, localAlbums = emptyList()).single()

		assertEquals("https://aurral.example.com/api/artists/release-group/rg-requested/cover", row.coverUrl)
		assertEquals("2026", row.year)
		assertEquals("processing", row.requestStatus)
		assertFalse(row.requestable)
		assertEquals(AurralAcquisitionProgress("processing", active = true, completed = false, failed = false), row.acquisitionProgress)
		assertEquals(AurralAcquisitionProgress("available", active = false, completed = true, failed = false), aurralAcquisitionProgress("available"))
		assertEquals(AurralAcquisitionProgress("failed", active = false, completed = false, failed = true), aurralAcquisitionProgress("failed"))
	}

	@Test
	fun similarArtistRowsMarkLocalMatchesByMbidAndName() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "The Artist",
			similarArtists = listOf(
				AurralSimilarArtist(
					id = "local-mbid",
					name = "Different Display",
					imageUrl = "https://aurral.example.com/local.jpg",
					matchPercent = 92
				),
				AurralSimilarArtist(
					id = "external-mbid",
					name = "External Artist",
					imageUrl = "https://aurral.example.com/external.jpg",
					matchPercent = 81
				),
				AurralSimilarArtist(
					id = "missing-mbid",
					name = "Name Matched Artist",
					matchPercent = 75
				)
			)
		)
		val localArtists = listOf(
			DomainArtist(
				id = "local-id",
				name = "Local Artist",
				musicBrainzId = "local-mbid"
			),
			DomainArtist(
				id = "name-match-id",
				name = " name matched artist "
			)
		)

		val rows = aurralSimilarArtistRows(enrichment, localArtists)

		assertEquals(listOf("local-id", null, "name-match-id"), rows.map { it.localArtistId })
		assertEquals(listOf(true, false, true), rows.map { it.inLibrary })
		assertEquals(listOf(92, 81, 75), rows.map { it.matchPercent })
	}

	@Test
	fun albumAcquisitionProgressMatchesMusicBrainzId() {
		val progress = aurralAlbumAcquisitionProgress(
			albumMusicBrainzId = "ALBUM-MBID",
			albumName = "Local title",
			artistName = "Local artist",
			requests = listOf(
				AurralAlbumRequest(
					albumMbid = "album-mbid",
					albumName = "Different title",
					artistName = "Different artist",
					status = "processing"
				)
			)
		)

		assertTrue(progress?.active == true)
		assertFalse(progress.completed)
		assertFalse(progress.failed)
	}

	@Test
	fun albumAcquisitionProgressFallsBackToArtistAndAlbumName() {
		val progress = aurralAlbumAcquisitionProgress(
			albumMusicBrainzId = null,
			albumName = "  You'll Be Alright, Kid ",
			artistName = "Alex Warren",
			requests = listOf(
				AurralAlbumRequest(
					albumName = "you'll be alright, kid",
					artistName = " alex   warren ",
					status = "available"
				)
			)
		)

		assertTrue(progress?.completed == true)
		assertFalse(progress.active)
		assertFalse(progress.failed)
	}

	@Test
	fun albumAcquisitionProgressDoesNotMatchSameAlbumForDifferentArtist() {
		val progress = aurralAlbumAcquisitionProgress(
			albumMusicBrainzId = null,
			albumName = "Greatest Hits",
			artistName = "Artist A",
			requests = listOf(
				AurralAlbumRequest(
					albumName = "Greatest Hits",
					artistName = "Artist B",
					status = "processing"
				)
			)
		)

		assertNull(progress)
	}

	private fun releaseGroup(
		id: String,
		title: String,
		firstReleaseDate: String? = null,
		coverUrl: String? = null
	) = AurralReleaseGroup(
		id = id,
		title = title,
		firstReleaseDate = firstReleaseDate,
		primaryType = "Album",
		secondaryTypes = emptyList(),
		coverUrl = coverUrl
	)

	private fun album(
		name: String,
		musicBrainzId: String?
	) = DomainAlbum(
		id = name,
		name = name,
		artistName = "The Artist",
		artistId = "artist-id",
		year = null,
		coverArtId = name,
		genre = null,
		genres = emptyList(),
		songCount = 0,
		duration = 0.seconds,
		createdAt = Instant.fromEpochMilliseconds(0),
		starredAt = null,
		lastPlayedAt = null,
		playCount = 0,
		userRating = null,
		version = null,
		musicBrainzId = musicBrainzId,
		songs = emptyList()
	)
}
