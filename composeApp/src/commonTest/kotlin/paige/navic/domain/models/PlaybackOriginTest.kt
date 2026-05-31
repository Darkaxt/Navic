package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlaybackOriginTest {
	@Test
	fun originKeysIncludeTypeAndId() {
		val artist = PlaybackOrigin(
			type = PlaybackOriginType.Artist,
			id = "shared-id",
			title = "Bond"
		)
		val genre = PlaybackOrigin(
			type = PlaybackOriginType.Genre,
			id = "shared-id",
			title = "Bond"
		)

		assertEquals("Artist:shared-id", artist.key)
		assertEquals("Genre:shared-id", genre.key)
		assertNotEquals(artist.key, genre.key)
	}

	@Test
	fun blankOriginIdsAreRejected() {
		assertFailsWith<IllegalArgumentException> {
			PlaybackOrigin(
				type = PlaybackOriginType.Album,
				id = " ",
				title = "Empty"
			)
		}
	}

	@Test
	fun playlistOriginClassifiesAurralStations() {
		val station = playlist(id = "station", name = "[A] Daily Flow")
		val regular = playlist(id = "playlist", name = "Daily Flow")

		assertEquals(PlaybackOriginType.Station, station.toPlaybackOriginType())
		assertEquals(PlaybackOriginType.Playlist, regular.toPlaybackOriginType())
		assertEquals("Daily Flow", station.toPlaybackOrigin().title)
	}

	@Test
	fun albumOriginKeepsAlbumAndArtistMetadata() {
		val album = DomainAlbum(
			id = "album",
			name = "Demos",
			artistName = "2CELLOS",
			artistId = "artist",
			year = 2011,
			coverArtId = "cover",
			genre = null,
			genres = emptyList(),
			songCount = 0,
			duration = 0.seconds,
			createdAt = Instant.DISTANT_PAST,
			starredAt = null,
			lastPlayedAt = null,
			userRating = null,
			version = null,
			musicBrainzId = null,
			songs = emptyList()
		)

		val origin = album.toPlaybackOrigin()

		assertEquals(PlaybackOriginType.Album, origin.type)
		assertEquals("album", origin.id)
		assertEquals("Demos", origin.title)
		assertEquals("2CELLOS", origin.subtitle)
		assertEquals("cover", origin.coverArtId)
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
