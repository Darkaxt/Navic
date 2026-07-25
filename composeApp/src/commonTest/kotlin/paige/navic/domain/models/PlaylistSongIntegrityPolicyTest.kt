package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistSongIntegrityPolicyTest {
	@Test
	fun authoritativeSongDeletionRetainsPlaylistOnlySongs() {
		val plan = LibrarySyncDeletionPlan(
			albumIdsToKeep = setOf("album-a"),
			songIdsToKeep = setOf("library-song")
		)

		assertEquals(
			setOf("library-song", "playlist-song"),
			plan.withRetainedPlaylistSongs(setOf("playlist-song")).songIdsToKeep
		)
	}

	@Test
	fun suppressedSongDeletionRemainsSuppressed() {
		val plan = LibrarySyncDeletionPlan(
			albumIdsToKeep = setOf("album-a", "album-b"),
			songIdsToKeep = null
		)

		assertNull(plan.withRetainedPlaylistSongs(setOf("playlist-song")).songIdsToKeep)
	}
}
