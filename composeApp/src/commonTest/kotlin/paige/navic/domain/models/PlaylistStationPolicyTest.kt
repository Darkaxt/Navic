package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlaylistStationPolicyTest {
	@Test
	fun aurralMarkedPlaylistsAreStationsAndHideMarkerForDisplay() {
		val station = playlist(id = "station", name = "[A] Discover")
		val regular = playlist(id = "playlist", name = "Discover")

		assertTrue(station.isStationPlaylist())
		assertFalse(regular.isStationPlaylist())
		assertEquals("Discover", station.stationDisplayName())
		assertEquals("Discover", regular.stationDisplayName())
	}

	@Test
	fun playlistsSplitIntoStationsAndRegularPlaylists() {
		val station = playlist(id = "station", name = "[A] Training")
		val moodMix = playlist(id = "mood", name = "Sleep Mix")
		val genreMix = playlist(id = "genre", name = "Electronic_Pop_Indie")
		val regular = playlist(id = "playlist", name = "Training")

		val playlists = listOf(station, moodMix, genreMix, regular)

		assertEquals(listOf(station), playlists.stationPlaylists())
		assertEquals(listOf(moodMix), playlists.moodMixPlaylists())
		assertEquals(listOf(genreMix), playlists.genreMixPlaylists())
		assertEquals(listOf(regular), playlists.userPlaylists())
	}

	@Test
	fun emptyPlaylistsAreHiddenFromEveryDisplayCategory() {
		val playlists = listOf(
			playlist(id = "station", name = "[A] Empty", songCount = 0),
			playlist(id = "mood", name = "Empty Mix", songCount = 0),
			playlist(id = "genre", name = "Empty_Genre", songCount = 0),
			playlist(id = "regular", name = "Empty", songCount = 0)
		)

		assertEquals(emptyList(), playlists.stationPlaylists())
		assertEquals(emptyList(), playlists.moodMixPlaylists())
		assertEquals(emptyList(), playlists.genreMixPlaylists())
		assertEquals(emptyList(), playlists.userPlaylists())
	}

	@Test
	fun declaredOrLocallyLoadedSongsKeepPlaylistsVisible() {
		val declared = playlist(id = "declared", name = "Declared", songCount = 2)
		val locallyLoaded = playlist(
			id = "loaded",
			name = "Loaded",
			songCount = 0,
			songs = listOf(song("song-1"))
		)

		assertEquals(listOf(declared, locallyLoaded), listOf(declared, locallyLoaded).userPlaylists())
	}

	@Test
	fun generatedPlaylistsSplitIntoMoodAndGenreMixes() {
		val moodMix = playlist(id = "mood", name = "Road Trip Mix")
		val genreMix = playlist(id = "genre", name = "Electronic_Pop_Indie")
		val imported = playlist(id = "imported", name = "Workout Motivation 2017 (Unmixed Workout Music Ideal for Gym)")

		assertTrue(moodMix.isMoodMixPlaylist())
		assertFalse(moodMix.isGenreMixPlaylist())
		assertTrue(genreMix.isGenreMixPlaylist())
		assertFalse(genreMix.isMoodMixPlaylist())
		assertFalse(imported.isMoodMixPlaylist())
		assertFalse(imported.isGenreMixPlaylist())
	}

	@Test
	fun playlistDisplayNameKeepsStationsCleanAndPrettifiesGenreMixes() {
		assertEquals("Discover", playlist(id = "station", name = "[A] Discover").playlistDisplayName())
		assertEquals("Chill", playlist(id = "mood", name = "Chill Mix").playlistDisplayName())
		assertEquals(
			"Electronic / Pop / Indie",
			playlist(
				id = "genre",
				name = "Electronic_Pop_Indie"
			).playlistDisplayName()
		)
		assertEquals("Training Plan", playlist(id = "manual", name = "Training Plan").playlistDisplayName())
	}

	@Test
	fun genreMixDisplayNameHidesGeneratorMetadata() {
		assertEquals(
			"Electronic / Pop / Rock",
			playlist(id = "genre", name = "Electronic_Pop_Rock_Medium_Danceable_Party_automatic").playlistDisplayName()
		)
		assertEquals(
			"Electronic / Pop / Indie",
			playlist(id = "genre", name = "Electronic_Pop_Indie_Medium_Danceable_Party_1_automatic").playlistDisplayName()
		)
	}

	@Test
	fun generatedMixAndFlowPlaylistCoversUseFallbackArtwork() {
		assertEquals(
			null,
			playlist(id = "mood", name = "Chill Mix", coverArtId = "generated-collage").visiblePlaylistCoverArtId()
		)
		assertEquals(
			null,
			playlist(id = "genre", name = "Electronic_Pop_Indie", coverArtId = "generated-collage").visiblePlaylistCoverArtId()
		)
		assertEquals(
			"manual-cover",
			playlist(id = "manual", name = "Road Trip", coverArtId = "manual-cover").visiblePlaylistCoverArtId()
		)
		assertEquals(
			null,
			playlist(id = "station", name = "[A] Discover", coverArtId = "station-cover").visiblePlaylistCoverArtId()
		)
	}

	@Test
	fun playlistFallbackKindMatchesGeneratedArtworkType() {
		assertEquals("Mix", playlist(id = "mood", name = "Chill Mix").playlistFallbackKind())
		assertEquals("Mix", playlist(id = "genre", name = "Electronic_Pop_Indie").playlistFallbackKind())
		assertEquals("Flow", playlist(id = "station", name = "[A] Discover").playlistFallbackKind())
		assertEquals("Playlist", playlist(id = "manual", name = "Training Plan").playlistFallbackKind())
	}

	@Test
	fun generatedMixArtworkLabelKeepsWholeTermsOnSeparateLines() {
		assertEquals(
			"Electronic\nPop\nRock",
			playlist(id = "genre", name = "Electronic_Pop_Rock_Medium_Danceable_Party_automatic").playlistArtworkLabel()
		)
		assertEquals(
			"Workout",
			playlist(id = "mood", name = "Workout Mix").playlistArtworkLabel()
		)
		assertEquals(
			"Discover",
			playlist(id = "station", name = "[A] Discover").playlistArtworkLabel()
		)
		assertEquals(
			"Training Plan",
			playlist(id = "manual", name = "Training Plan").playlistArtworkLabel()
		)
	}

	@Test
	fun playlistDeletionFromDetailIsOfferedForRegularPlaylistsOnly() {
		assertTrue(canDeletePlaylistFromDetail(playlist(id = "playlist", name = "Training")))
		assertFalse(canDeletePlaylistFromDetail(playlist(id = "station", name = "[A] Training")))
		assertFalse(canDeletePlaylistFromDetail(playlist(id = "mood", name = "Sleep Mix")))
		assertFalse(canDeletePlaylistFromDetail(playlist(id = "genre", name = "Electronic_Pop_Indie")))
		assertTrue(canDeletePlaylistFromDetail(playlist(id = "readonly", name = "Training").copy(readOnly = true)))
	}

	private fun playlist(
		id: String,
		name: String,
		comment: String? = null,
		coverArtId: String? = null,
		songCount: Int = 1,
		songs: List<DomainSong> = emptyList()
	) = DomainPlaylist(
		id = id,
		name = name,
		owner = "owner",
		comment = comment,
		coverArtId = coverArtId,
		songCount = songCount,
		duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST,
		modifiedAt = Instant.DISTANT_PAST,
		public = null,
		readOnly = null,
		allowedUsers = emptyList(),
		validUntil = null,
		songs = songs
	)

	private fun song(id: String) = DomainSong(
		id = id,
		title = "Song $id",
		artistName = "Artist",
		artistId = "artist",
		albumTitle = null,
		albumId = null,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 30.seconds,
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
		fileExtension = "mp3",
		mimeType = "audio/mpeg",
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
