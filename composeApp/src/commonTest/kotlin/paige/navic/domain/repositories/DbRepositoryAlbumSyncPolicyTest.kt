package paige.navic.domain.repositories

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

	@Test
	fun albumSyncWorkersProcessEachSummaryExactlyOnce() = runBlocking {
		val summaries = (1..64).toList()
		val processed = mutableListOf<Int>()
		val processedLock = Mutex()

		runLibraryAlbumSyncWorkers(summaries) { summary ->
			processedLock.withLock {
				processed.add(summary)
			}
		}

		assertEquals(summaries, processed.sorted())
		assertEquals(summaries.size, processed.size)
	}
}
