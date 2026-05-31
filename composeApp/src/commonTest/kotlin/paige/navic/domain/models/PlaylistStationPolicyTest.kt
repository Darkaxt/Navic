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
		val regular = playlist(id = "playlist", name = "Training")

		assertEquals(listOf(station), listOf(station, regular).stationPlaylists())
		assertEquals(listOf(regular), listOf(station, regular).regularPlaylists())
	}

	@Test
	fun playlistDeletionFromDetailIsOfferedForRegularPlaylistsOnly() {
		assertTrue(canDeletePlaylistFromDetail(playlist(id = "playlist", name = "Training")))
		assertFalse(canDeletePlaylistFromDetail(playlist(id = "station", name = "[A] Training")))
		assertTrue(canDeletePlaylistFromDetail(playlist(id = "readonly", name = "Training").copy(readOnly = true)))
	}

	private fun playlist(id: String, name: String) = DomainPlaylist(
		id = id,
		name = name,
		owner = "owner",
		comment = null,
		coverArtId = null,
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
