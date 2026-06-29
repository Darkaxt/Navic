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
	fun johnPowellSingleLocalTrackCanUseAurralReleaseTrackEvidenceWhenLocalAlbumTitleIsOnlyEditionText() {
		val howToTrainYourDragon = releaseGroup(
			id = "bb0b5c4b-7e84-4d17-ae7d-e9a79b070cb0",
			title = "How to Train Your Dragon: Music From the Motion Picture",
			firstReleaseDate = "2010-03-23"
		)
		val hubris = releaseGroup(
			id = "6233201d-be60-4ada-b0fa-b4179174866c",
			title = "Hubris: Choral Works by John Powell",
			firstReleaseDate = "2018-06-15"
		)
		val enrichment = AurralArtistEnrichment(
			artistMbid = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
			artistName = "John Powell",
			releaseGroups = listOf(howToTrainYourDragon, hubris)
		)
		val album = album(
			name = "For Your Consideration Best Original Score [2 CD]",
			songs = listOf(song(title = "Test Drive"))
		)

		val rows = aurralArtistOwnershipAlbumRows(
			enrichment = enrichment,
			localAlbums = listOf(album),
			releaseGroupTrackEvidence = mapOf(
				howToTrainYourDragon.id to listOf(AurralReleaseGroupTrackEvidence(title = "Test Drive")),
				hubris.id to listOf(AurralReleaseGroupTrackEvidence(title = "Agnus Dei"))
			)
		)

		assertEquals(listOf("How to Train Your Dragon: Music From the Motion Picture"), rows.ownedOrPartial.map { it.title })
		assertEquals(AurralOwnershipStatus.Partial, rows.ownedOrPartial.single().ownershipStatus)
		assertEquals(listOf("Test Drive"), rows.ownedOrPartial.single().localSongs.map { it.title })
		assertEquals(listOf("Hubris: Choral Works by John Powell"), rows.missing.map { it.title })
	}

	@Test
	fun trackEvidenceDoesNotAttachUnrelatedLocalAlbumTitleToAurralReleaseGroup() {
		val releaseGroup = releaseGroup(
			id = "bb0b5c4b-7e84-4d17-ae7d-e9a79b070cb0",
			title = "How to Train Your Dragon: Music From the Motion Picture",
			firstReleaseDate = "2010-03-23"
		)
		val enrichment = AurralArtistEnrichment(
			artistMbid = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
			artistName = "John Powell",
			releaseGroups = listOf(releaseGroup)
		)
		val album = album(
			name = "Workout Mix 150 BPM",
			songs = listOf(song(title = "Test Drive"))
		)

		val rows = aurralArtistOwnershipAlbumRows(
			enrichment = enrichment,
			localAlbums = listOf(album),
			releaseGroupTrackEvidence = mapOf(
				releaseGroup.id to listOf(AurralReleaseGroupTrackEvidence(title = "Test Drive"))
			)
		)

		assertTrue(rows.ownedOrPartial.isEmpty())
		assertEquals(listOf("How to Train Your Dragon: Music From the Motion Picture"), rows.missing.map { it.title })
	}

	@Test
	fun ownershipRowsUseAurralReleaseGroupsAsPrimaryCatalogAndIgnoreLocalOnlyAlbums() {
		val knownReleaseGroup = releaseGroup(
			id = "release-group-mbid",
			title = "Known Album",
			firstReleaseDate = "2024-01-01"
		)
		val missingReleaseGroup = releaseGroup(
			id = "missing-release-group",
			title = "Missing Album",
			firstReleaseDate = "2023-01-01"
		)
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "Artist",
			releaseGroups = listOf(knownReleaseGroup, missingReleaseGroup)
		)
		val localOnlyAlbum = album(
			name = "Local Bootleg",
			songs = listOf(song("Unmatched Local Track"))
		)
		val matchingAlbum = album(
			name = "Different local display",
			musicBrainzId = knownReleaseGroup.id,
			songs = listOf(song("One"), song("Two"))
		)

		val rows = aurralArtistOwnershipAlbumRows(enrichment, listOf(localOnlyAlbum, matchingAlbum))

		assertEquals(listOf("Known Album"), rows.ownedOrPartial.map { it.title })
		assertEquals(matchingAlbum, rows.ownedOrPartial.single().localAlbum)
		assertEquals(knownReleaseGroup, rows.ownedOrPartial.single().releaseGroup)
		assertEquals(listOf("Missing Album"), rows.missing.map { it.title })
		assertTrue(rows.ownedOrPartial.none { it.localAlbum == localOnlyAlbum })
		assertTrue(rows.missing.none { it.localAlbum == localOnlyAlbum })
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
	fun exactAlbumMatchWithOnlyDuplicateLocalTrackTitlesStaysPartial() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "John Powell",
			releaseGroups = listOf(
				releaseGroup(
					id = "httyd",
					title = "How to Train Your Dragon: Music From the Motion Picture",
					firstReleaseDate = "2010-03-23"
				)
			)
		)
		val localAlbum = album(
			name = "How to Train Your Dragon: Music From the Motion Picture",
			musicBrainzId = "httyd",
			songs = listOf(song("Test Drive"), song("Test Drive"))
		)

		val rows = aurralArtistOwnershipAlbumRows(enrichment, listOf(localAlbum))

		assertEquals(AurralOwnershipStatus.Partial, rows.ownedOrPartial.single().ownershipStatus)
	}

	@Test
	fun missingReleaseGroupsCarryProcessingProgress() {
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
		assertEquals(AurralOwnershipStatus.Processing, row.ownershipStatus)
		assertNotNull(row.acquisitionProgress)
	}

	@Test
	fun missingReleaseGroupsWithoutRequestStayMissing() {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "Artist",
			releaseGroups = listOf(releaseGroup(id = "missing", title = "Missing Album"))
		)

		val row = aurralArtistOwnershipAlbumRows(enrichment, emptyList()).missing.single()

		assertEquals(null, row.requestStatus)
		assertEquals(null, row.acquisitionProgress)
		assertEquals(AurralOwnershipStatus.Missing, row.ownershipStatus)
	}

	@Test
	fun requestStatusesUseSpecificOwnershipBuckets() {
		assertEquals(AurralOwnershipStatus.Requested, aurralOwnershipStatusForStatus("requested"))
		assertEquals(AurralOwnershipStatus.Requested, aurralOwnershipStatusForStatus("queued"))
		assertEquals(AurralOwnershipStatus.Processing, aurralOwnershipStatusForStatus("searching"))
		assertEquals(AurralOwnershipStatus.Processing, aurralOwnershipStatusForStatus("downloading"))
		assertEquals(AurralOwnershipStatus.Failed, aurralOwnershipStatusForStatus("failed"))
		assertEquals(AurralOwnershipStatus.Failed, aurralOwnershipStatusForStatus("download error"))
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
