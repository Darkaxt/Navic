package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibrarySyncDeletionPolicyTest {
	@Test
	fun completeDetailPassAllowsAlbumAndSongReconciliation() {
		val plan = librarySyncDeletionPlan(
			authoritativeAlbumIds = setOf("album-a", "album-b"),
			fetchedAlbumIds = setOf("album-a", "album-b"),
			fetchedSongIds = setOf("song-a", "song-b")
		)

		assertEquals(setOf("album-a", "album-b"), plan.albumIdsToKeep)
		assertEquals(setOf("song-a", "song-b"), plan.songIdsToKeep)
	}

	@Test
	fun skippedDetailPreservesSongsUntilACompletePass() {
		val plan = librarySyncDeletionPlan(
			authoritativeAlbumIds = setOf("album-a", "album-b"),
			fetchedAlbumIds = setOf("album-a"),
			fetchedSongIds = setOf("song-a")
		)

		assertEquals(setOf("album-a", "album-b"), plan.albumIdsToKeep)
		assertNull(plan.songIdsToKeep)
	}
}
