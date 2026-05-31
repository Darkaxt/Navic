package paige.navic.ui.screens.musicBrainz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_track_album
import navic.composeapp.generated.resources.info_track_artist
import navic.composeapp.generated.resources.info_track_bitrate
import navic.composeapp.generated.resources.info_track_disc_number
import navic.composeapp.generated.resources.info_track_duration
import navic.composeapp.generated.resources.info_track_format
import navic.composeapp.generated.resources.info_track_genre
import navic.composeapp.generated.resources.info_track_name
import navic.composeapp.generated.resources.info_track_number
import navic.composeapp.generated.resources.info_track_replay_gain_effective
import navic.composeapp.generated.resources.info_track_sampling_rate
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainReplayGain
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.repositories.MusicBrainzExternalLink
import paige.navic.domain.repositories.MusicBrainzMetadataField
import paige.navic.domain.repositories.MusicBrainzTrackMetadata

class MusicBrainzInfoDisplayPolicyTest {
	@Test
	fun trackRowsIncludePrimitiveTrackInformationAndSkipMissingValues() {
		val rows = musicBrainzInfoTrackRows(song(), ReplayGainMode.Track)

		assertEquals("Song Title", rows.first { it.title == Res.string.info_track_name }.value)
		assertEquals("Artist Name", rows.first { it.title == Res.string.info_track_artist }.value)
		assertEquals("Album Title", rows.first { it.title == Res.string.info_track_album }.value)
		assertEquals("7", rows.first { it.title == Res.string.info_track_number }.value)
		assertEquals("03:05", rows.first { it.title == Res.string.info_track_duration }.value)
		assertEquals("audio/flac", rows.first { it.title == Res.string.info_track_format }.value)
		assertEquals("320 kbps", rows.first { it.title == Res.string.info_track_bitrate }.value)
		assertEquals("44100 Hz", rows.first { it.title == Res.string.info_track_sampling_rate }.value)
		assertFalse(rows.any { it.title == Res.string.info_track_disc_number })
		assertFalse(rows.any { it.title == Res.string.info_track_genre })
		assertTrue(rows.any { it.title == Res.string.info_track_replay_gain_effective })
	}

	@Test
	fun metadataRowsHideUrlFieldsAndExposeUrlsAsResourceLinks() {
		val metadata = MusicBrainzTrackMetadata(
			recordingTitle = "Recording Title",
			externalLinks = listOf(
				MusicBrainzExternalLink(
					label = "Discogs",
					url = "https://www.discogs.com/release/123"
				)
			),
			recordingUrl = "https://musicbrainz.org/recording/recording-mbid",
			releaseUrl = "https://musicbrainz.org/release/release-mbid",
			releaseGroupUrl = "https://musicbrainz.org/release-group/release-group-mbid"
		)

		assertEquals(
			listOf(MusicBrainzMetadataField.RecordingTitle),
			musicBrainzInfoMetadataRows(metadata).map { it.field }
		)
		assertEquals(
			listOf("Discogs", "Recording", "Release", "Release group"),
			musicBrainzInfoResourceLinks(metadata).map { it.label }
		)
	}

	private fun song() = DomainSong(
		id = "song-id",
		title = "Song Title",
		artistName = "Artist Name",
		artistId = "artist-id",
		albumTitle = "Album Title",
		albumId = "album-id",
		parentId = null,
		comment = null,
		trackNumber = 7,
		discNumber = null,
		isrc = emptyList(),
		year = 2025,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 185.seconds,
		bpm = null,
		contributors = emptyList(),
		userRating = null,
		averageRating = null,
		bitRate = 320,
		bitDepth = 24,
		sampleRate = 44100,
		audioChannelCount = 2,
		replayGain = DomainReplayGain(
			albumGain = -4.5f,
			albumPeak = null,
			trackGain = -2.5f,
			trackPeak = null,
			baseGain = null,
			fallbackGain = null
		),
		fileSize = 12_345_678,
		fileExtension = "flac",
		mimeType = "audio/flac",
		filePath = "/music/song.flac",
		starredAt = null,
		coverArtId = null,
		musicBrainzId = "recording-mbid",
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
