package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SongRadioQueuePolicyTest {
	@Test
	fun songRadioQueueStartsWithSeedAndThenSimilarSongs() {
		val seed = song(
			id = "seed",
			artistId = "artist-a",
			albumId = "album-a",
			genres = listOf("house", "training")
		)
		val unrelated = song(
			id = "unrelated",
			artistId = "artist-z",
			albumId = "album-z",
			genres = listOf("jazz")
		)
		val sameGenre = song(
			id = "same-genre",
			artistId = "artist-y",
			albumId = "album-y",
			genres = listOf("training")
		)
		val sameArtist = song(
			id = "same-artist",
			artistId = "artist-a",
			albumId = "album-x",
			genres = listOf("ambient")
		)

		assertEquals(
			listOf("seed", "same-artist", "same-genre"),
			songRadioQueue(
				seedSong = seed,
				candidateSongs = listOf(unrelated, seed, sameGenre, sameArtist),
				limit = 3
			).map { it.id }
		)
	}

	@Test
	fun songRadioQueueSkipsRadioItemsAndClampsLimit() {
		val seed = song(id = "seed", artistId = "artist-a")
		val radio = song(id = "radio_live", artistId = "artist-a")
		val candidate = song(id = "candidate", artistId = "artist-a")

		assertEquals(
			listOf("seed", "candidate"),
			songRadioQueue(
				seedSong = seed,
				candidateSongs = listOf(radio, candidate),
				limit = 10
			).map { it.id }
		)
		assertEquals(listOf("seed"), songRadioQueue(seed, listOf(candidate), limit = 1).map { it.id })
		assertTrue(songRadioQueue(seed, listOf(candidate), limit = 0).isEmpty())
	}

	@Test
	fun songRadioQueueDoesNotStartFromRadioSeed() {
		assertTrue(
			songRadioQueue(
				seedSong = song(id = "radio_live", artistId = ""),
				candidateSongs = listOf(song(id = "candidate", artistId = "artist-a")),
				limit = 10
			).isEmpty()
		)
	}

	@Test
	fun songRadioQueuePrefersServerSimilarSongOrderBeforeLocalSimilarity() {
		val seed = song(
			id = "seed",
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
			listOf("seed", "server-first", "server-second", "strongest-local-match"),
			songRadioQueue(
				seedSong = seed,
				candidateSongs = listOf(serverSecond, strongestLocalMatch, serverFirst),
				limit = 4,
				preferredSongIds = listOf("server-first", "server-second")
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
