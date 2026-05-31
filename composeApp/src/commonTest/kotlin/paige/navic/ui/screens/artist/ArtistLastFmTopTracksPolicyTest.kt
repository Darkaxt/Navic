package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.LastFmTopTrack

class ArtistLastFmTopTracksPolicyTest {
	@Test
	fun matchesLastFmTopTracksToLocalSongsInPopularityOrder() {
		val localSongs = listOf(
			song(id = "deep-cut", title = "Deep Cut", playCount = 20),
			song(id = "demo", title = "Halo - Live", playCount = 2),
			song(id = "halo", title = "Halo", playCount = 10),
			song(id = "shallow", title = "Shallow", playCount = 5)
		)
		val tracks = listOf(
			LastFmTopTrack(name = "Shallow", rank = 1, playCount = 9000, url = null),
			LastFmTopTrack(name = "Halo", rank = 2, playCount = 8000, url = null),
			LastFmTopTrack(name = "Not Local", rank = 3, playCount = 7000, url = null)
		)

		assertEquals(
			listOf("shallow", "halo"),
			artistLastFmTopTrackSongs(tracks, localSongs).map { it.id }
		)
	}

	@Test
	fun deduplicatesRepeatedLastFmTrackNamesAndKeepsBestLocalCopy() {
		val localSongs = listOf(
			song(id = "low", title = "Intro", playCount = 1),
			song(id = "high", title = "Intro", playCount = 9)
		)
		val tracks = listOf(
			LastFmTopTrack(name = "Intro", rank = 1, playCount = 200, url = null),
			LastFmTopTrack(name = "intro", rank = 2, playCount = 150, url = null)
		)

		assertEquals(
			listOf("high"),
			artistLastFmTopTrackSongs(tracks, localSongs).map { it.id }
		)
	}

	private fun song(
		id: String,
		title: String,
		playCount: Int
	) = DomainSong(
		id = id,
		title = title,
		artistName = "Artist",
		artistId = "artist",
		albumTitle = "Album",
		albumId = "album",
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 180.seconds,
		bpm = null,
		contributors = emptyList(),
		playCount = playCount,
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
