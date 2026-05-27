package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong

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

	@Test
	fun playlistWithNoCachedSongsRequestsRefreshBeforePlayback() {
		val playlist = playlist(songCount = 0)

		assertTrue(shouldRefreshPlaylistSongsBeforePlayback(playlist))
	}

	@Test
	fun playlistWithPartialCachedSongsRequestsRefreshBeforePlayback() {
		val playlist = playlist(songCount = 3, localSongCount = 1)

		assertTrue(shouldRefreshPlaylistSongsBeforePlayback(playlist))
	}

	@Test
	fun playlistWithCompleteCachedSongsDoesNotRequestRefreshBeforePlayback() {
		val playlist = playlist(songCount = 2, localSongCount = 2)

		assertFalse(shouldRefreshPlaylistSongsBeforePlayback(playlist))
	}

	private fun playlist(songCount: Int, localSongCount: Int = 0) = DomainPlaylist(
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
		songs = (1..localSongCount).map { song("song-$it") }
	)

	private fun song(id: String) = DomainSong(
		id = id,
		title = "Song $id",
		artistName = "Artist",
		artistId = "artist-id",
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
		duration = 0.seconds,
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
