package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StalePlaybackSongPolicyTest {
	@Test
	fun parserProbeIsLimitedToRemoteContainerFailures() {
		assertTrue(
			shouldProbeStalePlaybackSong(
				errorCodeName = "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED",
				usesLocalFile = false
			)
		)
		assertFalse(
			shouldProbeStalePlaybackSong(
				errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
				usesLocalFile = false
			)
		)
		assertFalse(
			shouldProbeStalePlaybackSong(
				errorCodeName = "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED",
				usesLocalFile = true
			)
		)
	}

	@Test
	fun existingIdWinsWithoutReplacement() {
		val stale = song(id = "same")

		assertEquals(
			StalePlaybackSongResolution.Current,
			resolveStalePlaybackSong(stale, listOf(stale.copy(title = "Updated title")))
		)
	}

	@Test
	fun uniqueMusicBrainzIdentityWinsBeforeMetadata() {
		val stale = song(id = "old", musicBrainzId = "  Recording-ID ")
		val replacement = song(
			id = "new",
			title = "Renamed title",
			musicBrainzId = "recording-id"
		)

		assertEquals(
			StalePlaybackSongResolution.Replacement(
				song = replacement,
				strength = StalePlaybackMatchStrength.MusicBrainz
			),
			resolveStalePlaybackSong(stale, listOf(replacement))
		)
	}

	@Test
	fun uniqueIsrcIdentityIsAccepted() {
		val stale = song(id = "old", isrc = listOf(" cy-a01-12-34567 "))
		val replacement = song(id = "new", isrc = listOf("CY-A01-12-34567"))

		assertEquals(
			StalePlaybackSongResolution.Replacement(
				song = replacement,
				strength = StalePlaybackMatchStrength.Isrc
			),
			resolveStalePlaybackSong(stale, listOf(replacement))
		)
	}

	@Test
	fun uniqueExactMetadataAllowsSmallDurationDrift() {
		val stale = song(id = "old", durationSeconds = 240)
		val replacement = song(
			id = "new",
			title = "  BETWEEN   TWILIGHT ",
			artistName = "LINDSEY STIRLING",
			albumTitle = "Artemis ",
			durationSeconds = 242
		)

		assertEquals(
			StalePlaybackSongResolution.Replacement(
				song = replacement,
				strength = StalePlaybackMatchStrength.ExactMetadata
			),
			resolveStalePlaybackSong(stale, listOf(replacement))
		)
	}

	@Test
	fun ambiguousStrongIdentityNeverPicksTheFirstCandidate() {
		val stale = song(id = "old", musicBrainzId = "recording")
		val first = song(id = "new-a", musicBrainzId = "recording")
		val second = song(id = "new-b", musicBrainzId = "recording")

		assertEquals(
			StalePlaybackSongResolution.Ambiguous,
			resolveStalePlaybackSong(stale, listOf(first, second))
		)
	}

	@Test
	fun fuzzyOrIncompleteMetadataDoesNotReplace() {
		val stale = song(id = "old")
		val fuzzy = song(id = "new", title = "Between Twilight (Live)")
		val incomplete = song(id = "other", albumTitle = null)

		assertIs<StalePlaybackSongResolution.Missing>(
			resolveStalePlaybackSong(stale, listOf(fuzzy, incomplete))
		)
	}

	private fun song(
		id: String,
		title: String = "Between Twilight",
		artistName: String = "Lindsey Stirling",
		albumTitle: String? = "Artemis",
		durationSeconds: Int = 240,
		musicBrainzId: String? = null,
		isrc: List<String> = emptyList()
	) = DomainSong(
		id = id,
		title = title,
		artistName = artistName,
		artistId = "artist",
		albumTitle = albumTitle,
		albumId = "album",
		parentId = null,
		comment = null,
		trackNumber = 5,
		discNumber = 1,
		isrc = isrc,
		year = 2019,
		genre = "Classical Crossover",
		genres = emptyList(),
		moods = emptyList(),
		duration = durationSeconds.seconds,
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
