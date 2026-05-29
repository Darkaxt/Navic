package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class QuickPicksPolicyTest {
	@Test
	fun quickPicksPreferFrequentRatedAndRecentlyAddedSongsBeforeFallbacks() {
		val frequent = song(id = "frequent", albumId = "older", playCount = 10)
		val rated = song(id = "rated", albumId = "older", rating = 5)
		val recent = song(id = "recent", albumId = "newer")
		val fallback = song(id = "fallback", albumId = "older")

		assertEquals(
			listOf("frequent", "rated", "recent", "fallback"),
			quickPickSongs(
				songs = listOf(fallback, recent, rated, frequent),
				albums = listOf(
					album(id = "older", createdAt = Instant.parse("2025-01-01T00:00:00Z")),
					album(id = "newer", createdAt = Instant.parse("2026-01-01T00:00:00Z"))
				),
				limit = 10
			).map { it.id }
		)
	}

	@Test
	fun quickPicksSkipRadioRowsDeduplicateBySongIdAndClampLimit() {
		val first = song(id = "first", playCount = 5)
		val duplicate = song(id = "first", rating = 5)
		val second = song(id = "second", rating = 4)
		val radio = song(id = "radio_stream", playCount = 100)

		assertEquals(
			listOf("first", "second"),
			quickPickSongs(
				songs = listOf(radio, first, duplicate, second),
				albums = emptyList(),
				limit = 2
			).map { it.id }
		)
	}

	@Test
	fun quickPicksReturnEmptyListWhenLimitIsNotPositive() {
		assertEquals(
			emptyList(),
			quickPickSongs(
				songs = listOf(song(id = "song", playCount = 1)),
				albums = emptyList(),
				limit = 0
			)
		)
	}

	@Test
	fun quickPicksReturnEmptyListWhenDisabled() {
		assertEquals(
			emptyList(),
			quickPickSongs(
				songs = listOf(song(id = "song", playCount = 1)),
				albums = emptyList(),
				enabled = false,
				limit = 10
			)
		)
	}

	@Test
	fun quickPicksCanExcludeSongsShorterThanMinimumDuration() {
		val intro = song(id = "intro", playCount = 100, durationSeconds = 29)
		val fullSong = song(id = "full", playCount = 50, durationSeconds = 30)
		val fallback = song(id = "fallback", durationSeconds = 240)

		assertEquals(
			listOf("full", "fallback"),
			quickPickSongs(
				songs = listOf(intro, fullSong, fallback),
				albums = emptyList(),
				minDurationSeconds = 30,
				limit = 10
			).map { it.id }
		)
	}

	private fun song(
		id: String,
		albumId: String? = null,
		playCount: Int = 0,
		rating: Int? = null,
		durationSeconds: Int = 0
	) = DomainSong(
		id = id,
		title = "Song $id",
		artistName = "Artist",
		artistId = "artist",
		albumTitle = albumId,
		albumId = albumId,
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
		playCount = playCount,
		userRating = rating,
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

	private fun album(
		id: String,
		createdAt: Instant
	) = DomainAlbum(
		id = id,
		name = "Album $id",
		artistName = "Artist",
		artistId = "artist",
		year = null,
		coverArtId = id,
		genre = null,
		genres = emptyList(),
		songCount = 0,
		duration = 0.seconds,
		createdAt = createdAt,
		starredAt = null,
		lastPlayedAt = null,
		playCount = 0,
		userRating = null,
		version = null,
		musicBrainzId = null,
		songs = emptyList()
	)
}
