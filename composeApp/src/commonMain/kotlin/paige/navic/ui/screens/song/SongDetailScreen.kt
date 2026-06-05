package paige.navic.ui.screens.song

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_musicbrainz_artist_credit
import navic.composeapp.generated.resources.info_musicbrainz_country
import navic.composeapp.generated.resources.info_musicbrainz_external_link
import navic.composeapp.generated.resources.info_musicbrainz_first_release_date
import navic.composeapp.generated.resources.info_musicbrainz_genres
import navic.composeapp.generated.resources.info_musicbrainz_isrcs
import navic.composeapp.generated.resources.info_musicbrainz_recording_disambiguation
import navic.composeapp.generated.resources.info_musicbrainz_recording_title
import navic.composeapp.generated.resources.info_musicbrainz_recording_url
import navic.composeapp.generated.resources.info_musicbrainz_release_date
import navic.composeapp.generated.resources.info_musicbrainz_release_disambiguation
import navic.composeapp.generated.resources.info_musicbrainz_release_group_disambiguation
import navic.composeapp.generated.resources.info_musicbrainz_release_group_title
import navic.composeapp.generated.resources.info_musicbrainz_release_group_type
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
import paige.navic.domain.repositories.musicBrainzMetadataUrlOrNull
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.MusicIntegrationServices
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
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
	val uriHandler = LocalUriHandler.current
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val musicBrainzMetadataBySongId by musicBrainzArtworkRepository.metadataBySongId.collectAsStateWithLifecycle()
	val resolvingMusicBrainzSongIds by musicBrainzArtworkRepository.resolvingMusicBrainzSongIds.collectAsStateWithLifecycle()
	val musicBrainzMetadata = musicBrainzMetadataBySongId[songId]
	val songDetailIntegrationIndicators = integrationLoadingIndicators(
		musicBrainzLoading = songId in resolvingMusicBrainzSongIds
	)
	val info = remember(song, musicBrainzMetadata) {
		song?.let {
			listOf(
				SongDetailInfoRow(Res.string.info_track_name, song.title),
				SongDetailInfoRow(Res.string.info_track_artist, song.artistName),
				SongDetailInfoRow(Res.string.info_track_album, song.albumTitle),

				SongDetailInfoRow(Res.string.info_track_number, song.trackNumber),
				SongDetailInfoRow(Res.string.info_track_disc_number, song.discNumber),
				SongDetailInfoRow(Res.string.info_track_year, song.year),
				SongDetailInfoRow(Res.string.info_track_genre, song.genre),

				SongDetailInfoRow(Res.string.info_track_duration, song.duration.toHoursMinutesSeconds()),
				SongDetailInfoRow(Res.string.info_track_format, song.mimeType),
				SongDetailInfoRow(Res.string.info_track_bitrate, song.bitRate?.let { "$it kbps" }),
				SongDetailInfoRow(Res.string.info_track_bit_depth, song.bitDepth),
				SongDetailInfoRow(Res.string.info_track_sampling_rate, song.sampleRate?.let { "$it Hz" }),
				SongDetailInfoRow(Res.string.info_track_channel_count, song.audioChannelCount),

				SongDetailInfoRow(Res.string.info_track_file_size, song.fileSize.toFileSize()),
				SongDetailInfoRow(Res.string.info_track_path, song.filePath),

				SongDetailInfoRow(Res.string.info_track_replay_gain, song.replayGain?.trackGain?.let { "$it dB" }),
				SongDetailInfoRow(Res.string.info_album_replay_gain, song.replayGain?.albumGain?.let { "$it dB" }),
				SongDetailInfoRow(
					title = Res.string.info_track_replay_gain_effective,
					value = song.replayGain?.effectiveGain(preferenceManager.replayGainMode)
				)
			) + musicBrainzMetadataDisplayFields(musicBrainzMetadata).map { field ->
				SongDetailInfoRow(
					title = field.field.stringResource,
					value = field.value,
					musicBrainzField = field.field,
					musicBrainzUrl = field.url
				)
			}
		}.orEmpty()
	}
	val scrollState = rememberScrollState()
	BackToTopScrollHandler(scrollState)

	Scaffold(
		topBar = { NestedTopBar({ Text(song?.title.orEmpty()) }) }
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			Column(
				Modifier
					.verticalScroll(scrollState)
					.padding(
						top = contentPadding.calculateTopPadding() + 12.dp,
						start = 12.dp,
						end = 12.dp
					)
			) {
				Form {
					info.forEach { row ->
						val musicBrainzUrl = row.musicBrainzUrl
							?: musicBrainzMetadataUrlOrNull(row.musicBrainzField, row.value)
						FormRow(
							onClick = musicBrainzUrl?.let { url ->
								{ uriHandler.openUri(url) }
							}
						) {
							Column(Modifier.padding(vertical = 4.dp)) {
								Text(
									text = stringResource(row.title),
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.primary
								)
								SelectionContainer {
									Text(
										text = "${row.value ?: stringResource(Res.string.info_unknown)}",
										style = MaterialTheme.typography.bodyLarge,
										color = if (musicBrainzUrl != null) {
											MaterialTheme.colorScheme.primary
										} else {
											MaterialTheme.colorScheme.onSurface
										}
									)
								}
							}
						}
					}
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = songDetailIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = songDetailIntegrationIndicators,
					relevantServices = MusicIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = contentPadding.calculateTopPadding() + 8.dp)
			)
		}
	}
}

private data class SongDetailInfoRow(
	val title: StringResource,
	val value: Any?,
	val musicBrainzField: MusicBrainzMetadataField? = null,
	val musicBrainzUrl: String? = null
)

private val MusicBrainzMetadataField.stringResource: StringResource
	get() = when (this) {
		MusicBrainzMetadataField.RecordingTitle -> Res.string.info_musicbrainz_recording_title
		MusicBrainzMetadataField.RecordingDisambiguation -> Res.string.info_musicbrainz_recording_disambiguation
		MusicBrainzMetadataField.ArtistCredit -> Res.string.info_musicbrainz_artist_credit
		MusicBrainzMetadataField.FirstReleaseDate -> Res.string.info_musicbrainz_first_release_date
		MusicBrainzMetadataField.ReleaseTitle -> Res.string.info_musicbrainz_release_title
		MusicBrainzMetadataField.ReleaseDisambiguation -> Res.string.info_musicbrainz_release_disambiguation
		MusicBrainzMetadataField.ReleaseGroupTitle -> Res.string.info_musicbrainz_release_group_title
		MusicBrainzMetadataField.ReleaseGroupDisambiguation -> Res.string.info_musicbrainz_release_group_disambiguation
		MusicBrainzMetadataField.ReleaseGroupType -> Res.string.info_musicbrainz_release_group_type
		MusicBrainzMetadataField.ReleaseDate -> Res.string.info_musicbrainz_release_date
		MusicBrainzMetadataField.Country -> Res.string.info_musicbrainz_country
		MusicBrainzMetadataField.Status -> Res.string.info_musicbrainz_status
		MusicBrainzMetadataField.Genres -> Res.string.info_musicbrainz_genres
		MusicBrainzMetadataField.Tags -> Res.string.info_musicbrainz_tags
		MusicBrainzMetadataField.Isrcs -> Res.string.info_musicbrainz_isrcs
		MusicBrainzMetadataField.ExternalLink -> Res.string.info_musicbrainz_external_link
		MusicBrainzMetadataField.RecordingUrl -> Res.string.info_musicbrainz_recording_url
		MusicBrainzMetadataField.ReleaseUrl -> Res.string.info_musicbrainz_release_url
		MusicBrainzMetadataField.ReleaseGroupUrl -> Res.string.info_musicbrainz_release_group_url
	}
