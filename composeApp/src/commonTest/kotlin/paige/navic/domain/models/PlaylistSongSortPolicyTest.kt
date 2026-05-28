package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlaylistSongSortPolicyTest {
	@Test
	fun playlistSongSortOptionsKeepManualOrderFirst() {
		assertEquals(
			listOf(
				DomainPlaylistSongSortType.ManualOrder,
				DomainPlaylistSongSortType.Title,
				DomainPlaylistSongSortType.Artist,
				DomainPlaylistSongSortType.Album,
				DomainPlaylistSongSortType.Duration
			),
			playlistSongSortOptions()
		)
	}

	@Test
	fun manualOrderPreservesPlaylistOrderAndCanReverseIt() {
		val songs = listOf(
			song(id = "first", title = "Zulu"),
			song(id = "second", title = "Alpha"),
			song(id = "third", title = "Middle")
		)

		assertEquals(
			listOf("first", "second", "third"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.ManualOrder,
				reversed = false
			).map { it.id }
		)
		assertEquals(
			listOf("third", "second", "first"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.ManualOrder,
				reversed = true
			).map { it.id }
		)
	}

	@Test
	fun textSortsAreCaseInsensitiveAndStable() {
		val songs = listOf(
			song(id = "3", title = "beta", artistName = "Zed", albumTitle = "Second"),
			song(id = "1", title = "alpha", artistName = "Mira", albumTitle = "Same"),
			song(id = "2", title = "Alpha", artistName = "Ada", albumTitle = "Same")
		)

		assertEquals(
			listOf("2", "1", "3"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.Title,
				reversed = false
			).map { it.id }
		)
		assertEquals(
			listOf("3", "1", "2"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.Title,
				reversed = true
			).map { it.id }
		)
	}

	@Test
	fun artistAndAlbumSortsUseTrackMetadataAsTiebreakers() {
		val songs = listOf(
			song(id = "3", title = "Third", artistName = "B", albumTitle = "B Album", discNumber = 1, trackNumber = 2),
			song(id = "2", title = "Second", artistName = "A", albumTitle = "A Album", discNumber = 1, trackNumber = 2),
			song(id = "1", title = "First", artistName = "A", albumTitle = "A Album", discNumber = 1, trackNumber = 1)
		)

		assertEquals(
			listOf("1", "2", "3"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.Artist,
				reversed = false
			).map { it.id }
		)
		assertEquals(
			listOf("1", "2", "3"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.Album,
				reversed = false
			).map { it.id }
		)
	}

	@Test
	fun durationSortsShortestToLongestBeforeDirection() {
		val songs = listOf(
			song(id = "long", durationSeconds = 240),
			song(id = "short", durationSeconds = 90),
			song(id = "middle", durationSeconds = 180)
		)

		assertEquals(
			listOf("short", "middle", "long"),
			songs.sortedForPlaylistDetail(
				sortType = DomainPlaylistSongSortType.Duration,
				reversed = false
			).map { it.id }
		)
	}

	private fun song(
		id: String,
		title: String = id,
		artistName: String = "Artist",
		albumTitle: String? = "Album",
		discNumber: Int? = null,
		trackNumber: Int? = null,
		durationSeconds: Int = 180
	) = DomainSong(
		id = id,
		title = title,
		artistName = artistName,
		artistId = "artist-$artistName",
		albumTitle = albumTitle,
		albumId = albumTitle?.let { "album-$it" },
		parentId = null,
		comment = null,
		trackNumber = trackNumber,
		discNumber = discNumber,
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
