package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GenreGroupingPolicyTest {
	@Test
	fun genreGroupsMergeCaseAndPluralVariants() {
		val groups = genreGroupsFromAlbums(
			listOf(
				album(id = "game", genre = "Game"),
				album(id = "games", genre = "games")
			)
		)

		assertEquals(listOf("Game"), groups.map { it.name })
		assertEquals(listOf("game", "games"), groups.single().albums.map { it.id })
	}

	@Test
	fun genreGroupsExpandCompoundGenresIntoParentGenres() {
		val groups = genreGroupsFromAlbums(
			listOf(
				album(id = "soundtrack", genre = "Soundtracks"),
				album(id = "game-soundtrack", genre = "Soundtracks - Games")
			)
		)

		assertEquals(
			listOf("Soundtracks", "Game"),
			groups.map { it.name }
		)
		assertEquals(
			listOf("game-soundtrack", "soundtrack"),
			groups.first { it.name == "Soundtracks" }.albums.map { it.id }
		)
		assertEquals(
			listOf("game-soundtrack"),
			groups.first { it.name == "Game" }.albums.map { it.id }
		)
	}

	@Test
	fun genreGroupsExcludeLowSignalOtherBucket() {
		val groups = genreGroupsFromAlbums(
			listOf(
				album(id = "other", genre = "other"),
				album(id = "game", genre = "Game")
			)
		)

		assertEquals(listOf("Game"), groups.map { it.name })
	}

	private fun album(
		id: String,
		genre: String
	) = DomainAlbum(
		id = id,
		name = id,
		artistName = "Artist",
		artistId = "artist",
		year = if (id.contains("game")) 2024 else 2001,
		coverArtId = "cover-$id",
		genre = genre,
		genres = listOf(genre),
		songCount = 1,
		duration = 10.seconds,
		createdAt = Instant.DISTANT_PAST,
		starredAt = null,
		lastPlayedAt = null,
		userRating = null,
		version = null,
		musicBrainzId = null,
		songs = listOf(song(id))
	)

	private fun song(id: String) = DomainSong(
		id = id,
		title = id,
		artistName = "Artist",
		artistId = "artist",
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
		duration = 10.seconds,
		bpm = null,
		contributors = emptyList(),
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0L,
		fileExtension = "mp3",
		mimeType = "audio/mpeg",
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
