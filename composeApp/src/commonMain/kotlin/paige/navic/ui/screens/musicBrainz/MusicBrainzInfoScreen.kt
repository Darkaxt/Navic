package paige.navic.ui.screens.musicBrainz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_navigate_back
import navic.composeapp.generated.resources.action_view_on_musicbrainz
import navic.composeapp.generated.resources.info_musicbrainz_artist_credit
import navic.composeapp.generated.resources.info_musicbrainz_country
import navic.composeapp.generated.resources.info_musicbrainz_external_link
import navic.composeapp.generated.resources.info_musicbrainz_first_release_date
import navic.composeapp.generated.resources.info_musicbrainz_genres
import navic.composeapp.generated.resources.info_musicbrainz_isrcs
import navic.composeapp.generated.resources.info_musicbrainz_loading
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
import navic.composeapp.generated.resources.info_musicbrainz_unavailable
import navic.composeapp.generated.resources.title_musicbrainz_info
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.domain.repositories.MusicBrainzMetadataDisplayField
import paige.navic.domain.repositories.MusicBrainzMetadataField
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Info
import paige.navic.icons.outlined.KeyboardArrowDown
import paige.navic.ui.components.common.BlendBackground
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.MusicBrainzIntegrationServices
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
import paige.navic.ui.components.layouts.SheetScaffold
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.components.toolbars.SheetToolbar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.nowPlaying.components.ExtraScreenLidaClipBackground

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicBrainzInfoScreen(song: DomainSong?) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val uriHandler = LocalUriHandler.current
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsStateWithLifecycle()
	val musicBrainzMetadataBySongId by musicBrainzArtworkRepository.metadataBySongId.collectAsStateWithLifecycle()
	val serverCoverLoadFailedSongIds by musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.collectAsStateWithLifecycle()
	val resolvingMusicBrainzSongIds by musicBrainzArtworkRepository.resolvingMusicBrainzSongIds.collectAsStateWithLifecycle()

	val metadata = song?.id?.let(musicBrainzMetadataBySongId::get)
	val musicBrainzArtwork = song?.id?.let(musicBrainzArtworkBySongId::get)
	val serverCoverLoadFailed = song?.id?.let { it in serverCoverLoadFailedSongIds } == true
	val playbackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = musicBrainzArtwork?.imageUrl,
		musicBrainzArtworkCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	)
	val trackRows = remember(song, preferenceManager.replayGainMode) {
		song?.let { musicBrainzInfoTrackRows(it, preferenceManager.replayGainMode) }.orEmpty()
	}
	val metadataRows = remember(metadata) { musicBrainzInfoMetadataRows(metadata) }
	val resourceLinks = remember(metadata) { musicBrainzInfoResourceLinks(metadata) }
	val primaryMusicBrainzUrl = resourceLinks.firstOrNull { link ->
		link.url.startsWith("https://musicbrainz.org/", ignoreCase = true)
	}?.url ?: resourceLinks.firstOrNull()?.url
	val musicBrainzIntegrationIndicators = integrationLoadingIndicators(
		musicBrainzLoading = song?.id?.let { it in resolvingMusicBrainzSongIds } == true
	)

	LaunchedEffect(song?.id, preferenceManager.musicBrainzArtworkFallbackEnabled) {
		if (song != null && preferenceManager.musicBrainzArtworkFallbackEnabled) {
			musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)
		}
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			SheetToolbar(
				windowInsets = windowInsets,
				navigationIcon = {
					TopBarButton(
						onClick = { backStack.remove(Screen.MusicBrainzInfo) },
						content = {
							Icon(
								imageVector = Icons.Outlined.KeyboardArrowDown,
								contentDescription = stringResource(Res.string.action_navigate_back)
							)
						}
					)
				},
				actions = {
					TopBarButton(
						enabled = primaryMusicBrainzUrl != null,
						onClick = {
							primaryMusicBrainzUrl?.let(uriHandler::openUri)
						}
					) {
						Icon(
							imageVector = Icons.Outlined.Info,
							contentDescription = stringResource(Res.string.action_view_on_musicbrainz),
							modifier = Modifier.size(26.dp)
						)
					}
				}
			)
		},
		toolbarPosition = ToolbarPosition.Top
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			BlendBackground(
				coverArtId = playbackArtwork.coverArtId,
				imageUrl = playbackArtwork.imageUrl,
				imageCacheKey = playbackArtwork.imageCacheKey,
				imageRequestHeaders = playbackArtwork.imageRequestHeaders,
				isPaused = false,
				modifier = Modifier.fillMaxSize()
			)
			ExtraScreenLidaClipBackground(
				song = song,
				enabled = preferenceManager.lidaClipsMusicBrainzInfoVideoBackground,
				modifier = Modifier.fillMaxSize()
			)
			IntegrationLoadingIndicatorStrip(
				indicators = musicBrainzIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = musicBrainzIntegrationIndicators,
					relevantServices = MusicBrainzIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(
						start = 12.dp,
						top = contentPadding.calculateTopPadding() + 8.dp
					)
			)
			if (song == null) {
				ContentUnavailable(
					modifier = Modifier.fillMaxSize(),
					icon = Icons.Outlined.Info,
					label = stringResource(Res.string.info_musicbrainz_unavailable)
				)
				return@Box
			}
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = contentPadding
			) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 24.dp, bottom = 38.dp),
						contentAlignment = Alignment.Center
					) {
						CoverArt(
							coverArtId = playbackArtwork.coverArtId,
							imageUrl = playbackArtwork.imageUrl,
							imageCacheKey = playbackArtwork.imageCacheKey,
							imageRequestHeaders = playbackArtwork.imageRequestHeaders,
							contentDescription = song.title,
							onServerCoverLoadFailed = {
								musicBrainzArtworkRepository.reportServerCoverLoadFailed(song.id)
								musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)
							},
							modifier = Modifier.size(180.dp),
							shadowElevation = 6.dp
						)
					}
				}
				if (resourceLinks.isNotEmpty()) {
					item {
						MusicBrainzInfoResourceButtonRow(
							links = resourceLinks,
							onOpenUrl = uriHandler::openUri
						)
					}
				}
				trackRows.forEach { row ->
					item {
						MusicBrainzInfoTrackFieldRow(row)
					}
				}
				if (metadataRows.isEmpty()) {
					item {
						MusicBrainzInfoRow(
							label = stringResource(Res.string.title_musicbrainz_info),
							value = stringResource(
								if (preferenceManager.musicBrainzArtworkFallbackEnabled) {
									Res.string.info_musicbrainz_loading
								} else {
									Res.string.info_musicbrainz_unavailable
								}
							)
						)
					}
				} else {
					metadataRows.forEach { row ->
						item {
							MusicBrainzInfoFieldRow(row)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun MusicBrainzInfoResourceButtonRow(
	links: List<MusicBrainzInfoResourceLink>,
	onOpenUrl: (String) -> Unit
) {
	LazyRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 28.dp)
			.padding(bottom = 18.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		items(links, key = { it.url }) { link ->
			OutlinedButton(onClick = { onOpenUrl(link.url) }) {
				Text(
					text = link.label,
					maxLines = 1
				)
			}
		}
	}
}

@Composable
private fun MusicBrainzInfoTrackFieldRow(row: MusicBrainzInfoTrackRow) {
	MusicBrainzInfoRow(
		label = stringResource(row.title),
		value = row.value
	)
}

@Composable
private fun MusicBrainzInfoFieldRow(
	row: MusicBrainzMetadataDisplayField
) {
	MusicBrainzInfoRow(
		label = stringResource(row.field.stringResource),
		value = row.value
	)
}

@Composable
private fun MusicBrainzInfoRow(
	label: String,
	value: String,
	url: String? = null,
	onOpenUrl: (String) -> Unit = {}
) {
	val rowModifier = Modifier
		.fillMaxWidth()
		.padding(horizontal = 32.dp, vertical = 16.dp)
		.then(
			if (url == null) {
				Modifier
			} else {
				Modifier.clickable { onOpenUrl(url) }
			}
		)

	Column(modifier = rowModifier) {
		Text(
			text = label,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.ExtraBold,
			color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f)
		)
		Text(
			text = value,
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
			modifier = Modifier.padding(top = 8.dp)
		)
	}
}

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
