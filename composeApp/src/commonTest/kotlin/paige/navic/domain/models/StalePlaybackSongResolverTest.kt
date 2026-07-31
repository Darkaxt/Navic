package paige.navic.domain.models

import dev.zt64.subsonic.api.model.SubsonicErrorCode
import dev.zt64.subsonic.api.model.SubsonicException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class StalePlaybackSongResolverTest {
	@Test
	fun currentServerIdDoesNotLoadTheCatalog() = runBlocking {
		var catalogLoads = 0
		val resolver = StalePlaybackSongResolver(
			fetchSongById = { },
			loadCurrentSongs = {
				catalogLoads += 1
				emptyList()
			}
		)

		assertEquals(StalePlaybackProbeResolution.Current, resolver.resolve(song("current")))
		assertEquals(0, catalogLoads)
	}

	@Test
	fun typedMissingIdUsesTheConservativeCatalogMatcher() = runBlocking {
		val replacement = song("new", musicBrainzId = "recording")
		val resolver = StalePlaybackSongResolver(
			fetchSongById = {
				throw SubsonicException("Song not found", SubsonicErrorCode.DATA_NOT_FOUND)
			},
			loadCurrentSongs = { listOf(replacement) }
		)

		assertEquals(
			StalePlaybackProbeResolution.Replacement(
				song = replacement,
				strength = StalePlaybackMatchStrength.MusicBrainz
			),
			resolver.resolve(song("old", musicBrainzId = "recording"))
		)
	}

	@Test
	fun typedMissingIdWithoutAMatchIsTerminalMissing() = runBlocking {
		val resolver = StalePlaybackSongResolver(
			fetchSongById = {
				throw SubsonicException("Song not found", SubsonicErrorCode.DATA_NOT_FOUND)
			},
			loadCurrentSongs = { emptyList() }
		)

		assertEquals(StalePlaybackProbeResolution.Missing, resolver.resolve(song("old")))
	}

	@Test
	fun catalogFailureAfterConfirmedMissingIdBecomesAnUnresolvedDecision() = runBlocking {
		val failure = IllegalArgumentException("catalog read failed")
		val resolver = StalePlaybackSongResolver(
			fetchSongById = {
				throw SubsonicException("Song not found", SubsonicErrorCode.DATA_NOT_FOUND)
			},
			loadCurrentSongs = { throw failure }
		)

		val resolution = resolver.resolve(song("old"))
		assertIs<StalePlaybackProbeResolution.Unresolved>(resolution)
		assertEquals(failure, resolution.error)
	}

	@Test
	fun serviceFailureRemainsAnOfflineFallbackDecision() = runBlocking {
		val failure = IllegalStateException("failed to connect to Navidrome")
		val resolver = StalePlaybackSongResolver(
			fetchSongById = { throw failure },
			loadCurrentSongs = { error("catalog must not load") }
		)

		val resolution = resolver.resolve(song("old"))
		assertIs<StalePlaybackProbeResolution.ServiceUnavailable>(resolution)
		assertEquals(failure, resolution.error)
	}

	@Test
	fun unknownProbeFailureDoesNotClaimTheSongIsMissing() = runBlocking {
		val failure = IllegalArgumentException("unexpected response")
		val resolver = StalePlaybackSongResolver(
			fetchSongById = { throw failure },
			loadCurrentSongs = { error("catalog must not load") }
		)

		val resolution = resolver.resolve(song("old"))
		assertIs<StalePlaybackProbeResolution.Unresolved>(resolution)
		assertEquals(failure, resolution.error)
	}

	private fun song(
		id: String,
		musicBrainzId: String? = null
	) = DomainSong(
		id = id,
		title = "Between Twilight",
		artistName = "Lindsey Stirling",
		artistId = "artist",
		albumTitle = "Artemis",
		albumId = "album",
		parentId = null,
		comment = null,
		trackNumber = 5,
		discNumber = 1,
		isrc = emptyList(),
		year = 2019,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 240.seconds,
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
		fileSize = 0L,
		fileExtension = "flac",
		mimeType = "audio/flac",
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = musicBrainzId,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
