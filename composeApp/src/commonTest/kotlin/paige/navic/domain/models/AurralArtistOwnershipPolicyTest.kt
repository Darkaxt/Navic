package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AurralArtistOwnershipPolicyTest {
	@Test
	fun johnPowellSingleLocalTrackMarksMatchingAurralReleaseGroupPartial() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
			artistName = "John Powell",
			releaseGroups = listOf(
				releaseGroup(
					id = "bb0b5c4b-7e84-4d17-ae7d-e9a79b070cb0",
					title = "How to Train Your Dragon: Music From the Motion Picture",
					firstReleaseDate = "2010-03-23"
				),
				releaseGroup(
					id = "6233201d-be60-4ada-b0fa-b4179174866c",
					title = "Hubris: Choral Works by John Powell",
					firstReleaseDate = "2018-06-15"
				)
			)
		)
		val album = album(
			name = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
			songs = listOf(song(title = "Test Drive"))
		)

		val rows = aurralArtistOwnershipAlbumRows(
			enrichment = enrichment,
			localAlbums = listOf(album)
		)

		assertEquals(listOf("How to Train Your Dragon: Music From the Motion Picture"), rows.ownedOrPartial.map { it.title })
		assertEquals(AurralOwnershipStatus.Partial, rows.ownedOrPartial.single().ownershipStatus)
		assertEquals(album, rows.ownedOrPartial.single().localAlbum)
		assertEquals(listOf("Test Drive"), rows.ownedOrPartial.single().localSongs.map { it.title })
		assertEquals(listOf("Hubris: Choral Works by John Powell"), rows.missing.map { it.title })
	}

	@Test
	fun localAlbumWithSameReleaseGroupMbidIsOwnedWhenItHasMultipleTracks() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "Artist",
			releaseGroups = listOf(
				releaseGroup(id = "release-group-mbid", title = "Known Album", firstReleaseDate = "2024-01-01")
			)
		)
		val localAlbum = album(
			name = "Different local display",
			musicBrainzId = "release-group-mbid",
			songs = listOf(song("One"), song("Two"))
		)

		val rows = aurralArtistOwnershipAlbumRows(enrichment, listOf(localAlbum))

		assertEquals(1, rows.ownedOrPartial.size)
		assertEquals(AurralOwnershipStatus.Owned, rows.ownedOrPartial.single().ownershipStatus)
		assertTrue(rows.missing.isEmpty())
	}

	@Test
	fun missingReleaseGroupsCarryRequestProgress() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "Artist",
			releaseGroups = listOf(releaseGroup(id = "queued", title = "Queued Album")),
			requests = listOf(
				AurralAlbumRequest(
					albumMbid = "queued",
					albumName = "Queued Album",
					artistMbid = "artist-mbid",
					artistName = "Artist",
					status = "processing"
				)
			)
		)

		val row = aurralArtistOwnershipAlbumRows(enrichment, emptyList()).missing.single()

		assertEquals("processing", row.requestStatus)
		assertEquals(AurralOwnershipStatus.Partial, row.ownershipStatus)
		assertNotNull(row.acquisitionProgress)
	}

	private fun releaseGroup(
		id: String,
		title: String,
		firstReleaseDate: String? = null
	) = AurralReleaseGroup(
		id = id,
		title = title,
		firstReleaseDate = firstReleaseDate,
		primaryType = "Album",
		secondaryTypes = listOf("Soundtrack"),
		coverUrl = null
	)

	private fun album(
		name: String,
		musicBrainzId: String? = null,
		songs: List<DomainSong>
	) = DomainAlbum(
		id = name,
		name = name,
		artistName = "John Powell",
		artistId = "john-powell",
		year = 2010,
		coverArtId = "cover",
		genre = "Soundtrack",
		genres = listOf("Soundtrack"),
		songCount = songs.size,
		duration = songs.fold(0.seconds) { total, song -> total + song.duration },
		createdAt = Instant.fromEpochMilliseconds(0),
		starredAt = null,
		lastPlayedAt = null,
		playCount = 0,
		userRating = null,
		version = null,
		musicBrainzId = musicBrainzId,
		songs = songs
	)

	private fun song(title: String) = DomainSong(
		id = title,
		title = title,
		artistName = "John Powell",
		artistId = "john-powell",
		albumTitle = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
		albumId = "how-to-train-your-dragon-fyc",
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = 2010,
		genre = "Soundtrack",
		genres = listOf("Soundtrack"),
		moods = emptyList(),
		duration = 164.seconds,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = "flac",
		mimeType = "audio/flac",
		filePath = null,
		starredAt = null,
		coverArtId = "cover",
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
