package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.DomainPlaylist

class CollectionRepositoryTest {
	@Test
	fun playlistWithRemoteSongsButNoLocalSongsRequestsRefresh() {
		val playlist = playlist(songCount = 3)

		assertTrue(shouldRefreshCollectionOnLoad(fullRefresh = false, localData = playlist))
	}

	@Test
	fun playlistWithNoLocalSongsRequestsRefreshEvenWhenCachedSongCountIsZero() {
		val playlist = playlist(songCount = 0)

		assertTrue(shouldRefreshCollectionOnLoad(fullRefresh = false, localData = playlist))
	}

	@Test
	fun explicitFullRefreshStillRequestsRefresh() {
		val playlist = playlist(songCount = 0)

		assertTrue(shouldRefreshCollectionOnLoad(fullRefresh = true, localData = playlist))
	}

	private fun playlist(songCount: Int) = DomainPlaylist(
		id = "playlist-id",
		name = "Playlist",
		owner = "owner",
		comment = null,
		coverArtId = null,
		songCount = songCount,
		duration = 0.seconds,
		createdAt = Instant.fromEpochMilliseconds(0),
		modifiedAt = Instant.fromEpochMilliseconds(0),
		public = false,
		readOnly = false,
		allowedUsers = emptyList(),
		validUntil = null,
		songs = emptyList()
	)
}
