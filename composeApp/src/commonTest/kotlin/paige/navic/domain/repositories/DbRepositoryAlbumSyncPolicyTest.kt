package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DbRepositoryAlbumSyncPolicyTest {
	@Test
	fun albumSyncPreservesSongArtistWhenTrackHasArtistIdentity() {
		val overrides = albumSyncSongArtistOverrides(
			songArtistId = "song-artist-id",
			songArtistName = "Track Artist",
			albumArtistId = "album-artist-id",
			albumArtistName = "Album Artist"
		)

		assertNull(overrides.artistId)
		assertNull(overrides.artistName)
	}

	@Test
	fun albumSyncFallsBackToAlbumArtistWhenTrackArtistIdentityIsMissing() {
		val overrides = albumSyncSongArtistOverrides(
			songArtistId = null,
			songArtistName = " ",
			albumArtistId = "album-artist-id",
			albumArtistName = "Album Artist"
		)

		assertEquals("album-artist-id", overrides.artistId)
		assertEquals("Album Artist", overrides.artistName)
	}
}
