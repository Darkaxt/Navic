package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongCoverArtPolicyTest {
	@Test
	fun songCoverArtTakesPrecedenceOverAlbumCoverArt() {
		assertEquals(
			"song-cover",
			songCoverArtIdWithAlbumFallback(
				songCoverArtId = "song-cover",
				albumCoverArtId = "album-cover"
			)
		)
	}

	@Test
	fun albumCoverArtIsUsedWhenSongCoverArtIsMissing() {
		assertEquals(
			"album-cover",
			songCoverArtIdWithAlbumFallback(
				songCoverArtId = null,
				albumCoverArtId = "album-cover"
			)
		)
	}

	@Test
	fun albumCoverArtIsUsedWhenSongCoverArtIsBlank() {
		assertEquals(
			"album-cover",
			songCoverArtIdWithAlbumFallback(
				songCoverArtId = " ",
				albumCoverArtId = "album-cover"
			)
		)
	}

	@Test
	fun missingSongAndAlbumCoverArtStaysMissing() {
		assertNull(
			songCoverArtIdWithAlbumFallback(
				songCoverArtId = null,
				albumCoverArtId = " "
			)
		)
	}
}
