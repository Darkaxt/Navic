package paige.navic.ui.core

import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class PlayerUiStateQueueRepairTest {
	@Test
	fun replacementUpdatesTheSameQueueIndexAndCurrentSong() {
		val first = song("first")
		val stale = song("stale")
		val last = song("last")
		val replacement = song("replacement")

		val repaired = PlayerUiState(
			queue = listOf(first, stale, last),
			currentSong = stale,
			currentIndex = 1
		).withQueueSongReplacement(index = 1, replacement = replacement)

		assertEquals(listOf(first, replacement, last), repaired.queue)
		assertEquals(replacement, repaired.currentSong)
		assertEquals(1, repaired.currentIndex)
	}

	@Test
	fun invalidReplacementIndexLeavesStateUnchanged() {
		val state = PlayerUiState(queue = listOf(song("only")))

		assertSame(state, state.withQueueSongReplacement(index = 5, replacement = song("new")))
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
