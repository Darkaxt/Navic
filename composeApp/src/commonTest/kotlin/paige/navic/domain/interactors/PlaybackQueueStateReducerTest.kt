package paige.navic.domain.interactors

import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PlaybackQueueStateReducerTest {
	@Test
	fun movePreservesCurrentSongForEveryValidIndexTuple() {
		for (size in 1..6) {
			val songs = (0 until size).map { index -> song("song-$index") }
			for (fromIndex in songs.indices) {
				for (toIndex in songs.indices) {
					for (currentIndex in songs.indices) {
						val currentSong = songs[currentIndex]
						val result = DefaultPlaybackQueueStateReducer.move(
							PlayerUiState(queue = songs, currentIndex = currentIndex, currentSong = currentSong),
							fromIndex,
							toIndex
						)

						assertEquals(currentSong, result.currentSong)
						assertEquals(currentSong, result.queue[result.currentIndex])
					}
					}
				}
			}
		}

	@Test
	fun appendRemoveAndInsertNextKeepQueueStateCoherent() {
		val first = song("first")
		val second = song("second")
		val third = song("third")
		val appended = DefaultPlaybackQueueStateReducer.append(PlayerUiState(), listOf(first, third))
		val inserted = DefaultPlaybackQueueStateReducer.insertNext(appended, listOf(second))
		val removed = DefaultPlaybackQueueStateReducer.removeAt(inserted, 0)

		assertEquals(listOf(first, second, third), inserted.queue)
		assertEquals(listOf(second, third), removed.queue)
		assertEquals(second, removed.currentSong)
		assertEquals(0, removed.currentIndex)
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
