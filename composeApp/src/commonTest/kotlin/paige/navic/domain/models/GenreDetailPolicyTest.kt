package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GenreDetailPolicyTest {
	@Test
	fun genreArtistsAreDeduplicatedByArtistId() {
		val genre = DomainGenre(
			name = "Classical Crossover",
			albumCount = 2,
			songCount = 2,
			albums = listOf(
				album(id = "a1", artistId = "artist", artistName = "Bond", songId = "s1"),
				album(id = "a2", artistId = "artist", artistName = "Bond", songId = "s2")
			)
		)

		val artists = genreArtists(genre)

		assertEquals(1, artists.size)
		assertEquals("artist", artists.single().id)
		assertEquals("Bond", artists.single().name)
		assertEquals(2, artists.single().albumCount)
	}

	@Test
	fun genrePlayableSongsAreDistinctAndUseAlbumOrder() {
		val duplicate = song("shared")
		val genre = DomainGenre(
			name = "Workout",
			albumCount = 2,
			songCount = 3,
			albums = listOf(
				album(id = "a1", songId = "first").copy(songs = listOf(song("first"), duplicate)),
				album(id = "a2", songId = "shared").copy(songs = listOf(duplicate, song("last")))
			)
		)

		assertEquals(listOf("first", "shared", "last"), genrePlayableSongs(genre).map { it.id })
	}

	@Test
	fun genreAlbumsUseExistingNewestYearPolicy() {
		val genre = DomainGenre(
			name = "Workout",
			albumCount = 3,
			songCount = 3,
			albums = listOf(
				album(id = "unknown", songId = "unknown").copy(name = "No Year", year = null),
				album(id = "older", songId = "older").copy(name = "Older", year = 2001),
				album(id = "recent", songId = "recent").copy(name = "Recent", year = 2024)
			)
		)

		assertEquals(listOf("recent", "older", "unknown"), genreAlbums(genre).map { it.id })
	}

	@Test
	fun genrePlayableSongsUseSortedAlbumOrder() {
		val genre = DomainGenre(
			name = "Workout",
			albumCount = 2,
			songCount = 2,
			albums = listOf(
				album(id = "older", songId = "older").copy(year = 2001),
				album(id = "recent", songId = "recent").copy(year = 2024)
			)
		)

		assertEquals(listOf("recent", "older"), genrePlayableSongs(genre).map { it.id })
	}

	@Test
	fun genreTotalDurationSumsPlayableSongs() {
		val genre = DomainGenre(
			name = "Long",
			albumCount = 1,
			songCount = 2,
			albums = listOf(
				album(id = "a1", songId = "one").copy(
					songs = listOf(song("one", durationSeconds = 10), song("two", durationSeconds = 20))
				)
			)
		)

		assertEquals(30.seconds, genreTotalDuration(genre))
	}

	@Test
	fun genrePlaybackOriginUsesGenreName() {
		val genre = DomainGenre(
			name = "Fusion",
			albumCount = 0,
			songCount = 0,
			albums = emptyList()
		)

		val origin = genre.toPlaybackOrigin()

		assertEquals(PlaybackOriginType.Genre, origin.type)
		assertEquals("Fusion", origin.id)
		assertEquals("Fusion", origin.title)
	}

	private fun album(
		id: String,
		artistId: String = "artist-$id",
		artistName: String = "Artist $id",
		songId: String
	) = DomainAlbum(
		id = id,
		name = "Album $id",
		artistName = artistName,
		artistId = artistId,
		year = null,
		coverArtId = "cover-$id",
		genre = null,
		genres = emptyList(),
		songCount = 1,
		duration = 10.seconds,
		createdAt = Instant.DISTANT_PAST,
		starredAt = null,
		lastPlayedAt = null,
		userRating = null,
		version = null,
		musicBrainzId = null,
		songs = listOf(song(songId))
	)

	private fun song(
		id: String,
		durationSeconds: Int = 10
	) = DomainSong(
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
		duration = durationSeconds.seconds,
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
