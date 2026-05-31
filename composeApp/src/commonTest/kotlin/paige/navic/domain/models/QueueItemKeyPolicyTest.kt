package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds

class QueueItemKeyPolicyTest {
	@Test
	fun duplicateSongsReceiveDistinctStableKeys() {
		val song = song("song-1")

		val keys = queueItemKeys(listOf(song, song))

		assertEquals(2, keys.distinct().size)
		assertEquals("song-1:0", keys[0])
		assertEquals("song-1:1", keys[1])
	}

	@Test
	fun occurrenceIndexesFollowSongIdentityAfterReorder() {
		val first = song("first")
		val second = song("second")

		val original = queueItemKeys(listOf(first, second, first))
		val reordered = queueItemKeys(listOf(first, first, second))

		assertEquals("first:0", original[0])
		assertEquals("first:1", original[2])
		assertEquals("first:0", reordered[0])
		assertEquals("first:1", reordered[1])
		assertNotEquals(original[1], reordered[1])
	}

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
		duration = 1.seconds,
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
