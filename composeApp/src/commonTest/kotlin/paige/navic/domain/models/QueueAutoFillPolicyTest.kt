package paige.navic.domain.models

import paige.navic.domain.models.settings.AutoFillQueueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class QueueAutoFillPolicyTest {
	@Test
	fun autoFillRunsOnlyForActiveNonRadioQueuesNearTheEndBelowTargetSize() {
		assertTrue(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = false,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = false,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = true,
				queueSize = 12,
				currentIndex = 7,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 12,
				currentIndex = 5,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
		assertFalse(
			shouldAutoFillQueue(
				autoFillQueue = true,
				isPlaying = true,
				isRadioQueue = false,
				queueSize = 25,
				currentIndex = 22,
				remainingTrigger = 5,
				targetSize = 25
			)
		)
	}

	@Test
	fun autoFillAppendCountClampsToTargetSize() {
		assertEquals(15, queueAutoFillAppendCount(queueSize = 10, targetSize = 25))
		assertEquals(0, queueAutoFillAppendCount(queueSize = 25, targetSize = 25))
		assertEquals(0, queueAutoFillAppendCount(queueSize = 30, targetSize = 25))
		assertEquals(0, queueAutoFillAppendCount(queueSize = -1, targetSize = -1))
	}

	@Test
	fun autoFillCandidatesSkipQueuedRadioAndDuplicateSongs() {
		assertEquals(
			listOf("song-2", "song-3"),
			queueAutoFillCandidateIds(
				candidateIds = listOf("song-1", "radio_live", "song-2", "song-2", "song-3"),
				queuedIds = setOf("song-1"),
				limit = 10
			)
		)
		assertEquals(
			listOf("song-2"),
			queueAutoFillCandidateIds(
				candidateIds = listOf("song-1", "song-2", "song-3"),
				queuedIds = setOf("song-1"),
				limit = 1
			)
		)
	}

	@Test
	fun randomAutoFillSourcePreservesCandidateOrderAfterFiltering() {
		val songs = listOf(
			song(id = "song-1", artistId = "artist-a"),
			song(id = "radio_live", artistId = "artist-a"),
			song(id = "song-2", artistId = "artist-b"),
			song(id = "song-3", artistId = "artist-c")
		)

		assertEquals(
			listOf("song-2", "song-3"),
			queueAutoFillCandidateSongs(
				candidateSongs = songs,
				queuedIds = setOf("song-1"),
				limit = 10,
				source = AutoFillQueueSource.RandomLibrary,
				currentSong = songs.first()
			).map { it.id }
		)
	}

	@Test
	fun similarAutoFillSourcePrefersCurrentArtistGenreAndAlbum() {
		val current = song(
			id = "song-current",
			artistId = "artist-a",
			albumId = "album-a",
			genres = listOf("house", "training")
		)
		val unrelated = song(
			id = "song-unrelated",
			artistId = "artist-x",
			albumId = "album-x",
			genres = listOf("jazz")
		)
		val sameGenre = song(
			id = "song-same-genre",
			artistId = "artist-y",
			albumId = "album-y",
			genres = listOf("house")
		)
		val sameArtist = song(
			id = "song-same-artist",
			artistId = "artist-a",
			albumId = "album-z",
			genres = listOf("ambient")
		)
		val sameAlbumAndGenre = song(
			id = "song-same-album-genre",
			artistId = "artist-z",
			albumId = "album-a",
			genres = listOf("training")
		)

		assertEquals(
			listOf(
				"song-same-artist",
				"song-same-album-genre",
				"song-same-genre",
				"song-unrelated"
			),
			queueAutoFillCandidateSongs(
				candidateSongs = listOf(unrelated, sameGenre, sameArtist, sameAlbumAndGenre),
				queuedIds = setOf(current.id),
				limit = 10,
				source = AutoFillQueueSource.SimilarToCurrentSong,
				currentSong = current
			).map { it.id }
		)
	}

	@Test
	fun similarAutoFillSourcePrefersServerSimilarSongOrderBeforeLocalSimilarity() {
		val current = song(
			id = "song-current",
			artistId = "artist-a",
			albumId = "album-a",
			genres = listOf("training")
		)
		val serverSecond = song(id = "server-second", artistId = "artist-x")
		val strongestLocalMatch = song(
			id = "strongest-local-match",
			artistId = "artist-a",
			albumId = "album-a",
			genres = listOf("training")
		)
		val serverFirst = song(id = "server-first", artistId = "artist-y")

		assertEquals(
			listOf("server-first", "server-second", "strongest-local-match"),
			queueAutoFillCandidateSongs(
				candidateSongs = listOf(serverSecond, strongestLocalMatch, serverFirst),
				queuedIds = setOf(current.id),
				limit = 10,
				source = AutoFillQueueSource.SimilarToCurrentSong,
				currentSong = current,
				preferredSongIds = listOf("server-first", "server-second")
			).map { it.id }
		)
	}

	@Test
	fun recentGenresAutoFillSourcePrefersGenresFromRecentQueueHistory() {
		val recentHouse = song(id = "recent-house", artistId = "artist-a", genres = listOf("house"))
		val recentTraining = song(id = "recent-training", artistId = "artist-b", genres = listOf("training"))
		val currentAmbient = song(id = "current-ambient", artistId = "artist-c", genres = listOf("ambient"))
		val jazz = song(id = "candidate-jazz", artistId = "artist-x", genres = listOf("jazz"))
		val house = song(id = "candidate-house", artistId = "artist-y", genres = listOf("house"))
		val trainingAndHouse = song(
			id = "candidate-training-house",
			artistId = "artist-z",
			genres = listOf("training", "house")
		)

		assertEquals(
			listOf("candidate-training-house", "candidate-house", "candidate-jazz"),
			queueAutoFillCandidateSongs(
				candidateSongs = listOf(jazz, house, trainingAndHouse),
				queuedIds = setOf(recentHouse.id, recentTraining.id, currentAmbient.id),
				limit = 10,
				source = AutoFillQueueSource.RecentGenres,
				currentSong = currentAmbient,
				recentSongs = listOf(recentHouse, recentTraining, currentAmbient)
			).map { it.id }
		)
	}

	private fun song(
		id: String,
		artistId: String,
		albumId: String? = null,
		genres: List<String> = emptyList()
	) = DomainSong(
		id = id,
		title = "Song $id",
		artistName = "Artist",
		artistId = artistId,
		albumTitle = null,
		albumId = albumId,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = genres.firstOrNull(),
		genres = genres,
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
