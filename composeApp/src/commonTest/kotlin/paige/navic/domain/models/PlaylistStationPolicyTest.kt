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
	fun generatedMixPlaylistCoversUseFallbackArtwork() {
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
			"station-cover",
			playlist(id = "station", name = "[A] Discover", coverArtId = "station-cover").visiblePlaylistCoverArtId()
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
		coverArtId: String? = null
	) = DomainPlaylist(
		id = id,
		name = name,
		owner = "owner",
		comment = comment,
		coverArtId = coverArtId,
		songCount = 0,
		duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST,
		modifiedAt = Instant.DISTANT_PAST,
		public = null,
		readOnly = null,
		allowedUsers = emptyList(),
		validUntil = null,
		songs = emptyList()
	)
}
