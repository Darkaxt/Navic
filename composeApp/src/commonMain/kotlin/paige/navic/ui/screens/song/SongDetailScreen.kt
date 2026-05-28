package paige.navic.ui.screens.song

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_musicbrainz_artist_credit
import navic.composeapp.generated.resources.info_musicbrainz_country
import navic.composeapp.generated.resources.info_musicbrainz_first_release_date
import navic.composeapp.generated.resources.info_musicbrainz_genres
import navic.composeapp.generated.resources.info_musicbrainz_isrcs
import navic.composeapp.generated.resources.info_musicbrainz_recording_title
import navic.composeapp.generated.resources.info_musicbrainz_recording_url
import navic.composeapp.generated.resources.info_musicbrainz_release_date
import navic.composeapp.generated.resources.info_musicbrainz_release_group_title
import navic.composeapp.generated.resources.info_musicbrainz_release_group_url
import navic.composeapp.generated.resources.info_musicbrainz_release_title
import navic.composeapp.generated.resources.info_musicbrainz_release_url
import navic.composeapp.generated.resources.info_musicbrainz_status
import navic.composeapp.generated.resources.info_musicbrainz_tags
import navic.composeapp.generated.resources.info_album_replay_gain
import navic.composeapp.generated.resources.info_track_album
import navic.composeapp.generated.resources.info_track_artist
import navic.composeapp.generated.resources.info_track_bit_depth
import navic.composeapp.generated.resources.info_track_bitrate
import navic.composeapp.generated.resources.info_track_channel_count
import navic.composeapp.generated.resources.info_track_disc_number
import navic.composeapp.generated.resources.info_track_duration
import navic.composeapp.generated.resources.info_track_file_size
import navic.composeapp.generated.resources.info_track_format
import navic.composeapp.generated.resources.info_track_genre
import navic.composeapp.generated.resources.info_track_name
import navic.composeapp.generated.resources.info_track_number
import navic.composeapp.generated.resources.info_track_path
import navic.composeapp.generated.resources.info_track_replay_gain
import navic.composeapp.generated.resources.info_track_replay_gain_effective
import navic.composeapp.generated.resources.info_track_sampling_rate
import navic.composeapp.generated.resources.info_track_year
import navic.composeapp.generated.resources.info_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.MusicBrainzMetadataField
import paige.navic.domain.repositories.musicBrainzMetadataDisplayFields
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.song.viewmodels.SongDetailViewModel
import paige.navic.util.core.effectiveGain
import paige.navic.util.core.toFileSize
import paige.navic.util.core.toHoursMinutesSeconds

@Composable
fun SongDetailScreen(songId: String) {
	val viewModel = koinViewModel<SongDetailViewModel>(
		key = songId,
		parameters = { parametersOf(songId) }
	)

	val songState by viewModel.songState.collectAsStateWithLifecycle()
	val song = songState.data

	val preferenceManager = koinInject<PreferenceManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val musicBrainzMetadataBySongId by musicBrainzArtworkRepository.metadataBySongId.collectAsStateWithLifecycle()
	val musicBrainzMetadata = musicBrainzMetadataBySongId[songId]
	val info = remember(song, musicBrainzMetadata) {
		song?.let {
			listOf(
				Res.string.info_track_name to song.title,
				Res.string.info_track_artist to song.artistName,
				Res.string.info_track_album to song.albumTitle,

				Res.string.info_track_number to song.trackNumber,
				Res.string.info_track_disc_number to song.discNumber,
				Res.string.info_track_year to song.year,
				Res.string.info_track_genre to song.genre,

				Res.string.info_track_duration to song.duration.toHoursMinutesSeconds(),
				Res.string.info_track_format to song.mimeType,
				Res.string.info_track_bitrate to song.bitRate?.let { "$it kbps" },
				Res.string.info_track_bit_depth to song.bitDepth,
				Res.string.info_track_sampling_rate to song.sampleRate?.let { "$it Hz" },
				Res.string.info_track_channel_count to song.audioChannelCount,

				Res.string.info_track_file_size to song.fileSize.toFileSize(),
				Res.string.info_track_path to song.filePath,

				Res.string.info_track_replay_gain to song.replayGain?.trackGain?.let { "$it dB" },
				Res.string.info_album_replay_gain to song.replayGain?.albumGain?.let { "$it dB" },
				Res.string.info_track_replay_gain_effective to song.replayGain?.effectiveGain(preferenceManager.replayGainMode)
			) + musicBrainzMetadataDisplayFields(musicBrainzMetadata).map { field ->
				field.field.stringResource to field.value
			}
		}.orEmpty()
	}

	Scaffold(
		topBar = { NestedTopBar({ Text(song?.title.orEmpty()) }) }
	) { contentPadding ->
		Column(
			Modifier
				.verticalScroll(rememberScrollState())
				.padding(
					top = contentPadding.calculateTopPadding() + 12.dp,
					start = 12.dp,
					end = 12.dp
				)
		) {
			Form {
				info.forEach { (key, value) ->
					FormRow {
						Column(Modifier.padding(vertical = 4.dp)) {
							Text(
								text = stringResource(key),
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.primary
							)
							SelectionContainer {
								Text(
									text = "${value ?: stringResource(Res.string.info_unknown)}",
									style = MaterialTheme.typography.bodyLarge
								)
							}
						}
					}
				}
			}
		}
	}
}

private val MusicBrainzMetadataField.stringResource: StringResource
	get() = when (this) {
		MusicBrainzMetadataField.RecordingTitle -> Res.string.info_musicbrainz_recording_title
		MusicBrainzMetadataField.ArtistCredit -> Res.string.info_musicbrainz_artist_credit
		MusicBrainzMetadataField.FirstReleaseDate -> Res.string.info_musicbrainz_first_release_date
		MusicBrainzMetadataField.ReleaseTitle -> Res.string.info_musicbrainz_release_title
		MusicBrainzMetadataField.ReleaseGroupTitle -> Res.string.info_musicbrainz_release_group_title
		MusicBrainzMetadataField.ReleaseDate -> Res.string.info_musicbrainz_release_date
		MusicBrainzMetadataField.Country -> Res.string.info_musicbrainz_country
		MusicBrainzMetadataField.Status -> Res.string.info_musicbrainz_status
		MusicBrainzMetadataField.Genres -> Res.string.info_musicbrainz_genres
		MusicBrainzMetadataField.Tags -> Res.string.info_musicbrainz_tags
		MusicBrainzMetadataField.Isrcs -> Res.string.info_musicbrainz_isrcs
		MusicBrainzMetadataField.RecordingUrl -> Res.string.info_musicbrainz_recording_url
		MusicBrainzMetadataField.ReleaseUrl -> Res.string.info_musicbrainz_release_url
		MusicBrainzMetadataField.ReleaseGroupUrl -> Res.string.info_musicbrainz_release_group_url
	}
