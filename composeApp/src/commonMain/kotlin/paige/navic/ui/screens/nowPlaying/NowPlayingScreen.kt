package paige.navic.ui.screens.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_lyrics
import navic.composeapp.generated.resources.action_musicbrainz_info
import navic.composeapp.generated.resources.action_navigate_back
import navic.composeapp.generated.resources.action_play_music_video
import navic.composeapp.generated.resources.action_queue
import navic.composeapp.generated.resources.title_now_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.LidaClipsNowPlayingMusicVideoAction
import paige.navic.domain.models.NowPlayingArtworkTapDestination
import paige.navic.domain.models.externalFallbackArtworkCacheKey
import paige.navic.domain.models.externalFallbackArtworkUrl
import paige.navic.domain.models.lidaClipsNowPlayingMusicVideoAction
import paige.navic.domain.models.nowPlayingArtworkTapDestination
import paige.navic.domain.models.shouldReserveNowPlayingToolbarGap
import paige.navic.domain.models.shouldShowNowPlayingMusicBrainzInfoAction
import paige.navic.domain.models.shouldShowNowPlayingLyricsAction
import paige.navic.domain.models.shouldShowLidaClipBackgroundVideo
import paige.navic.domain.models.shouldShowNowPlayingBackgroundBottomGradient
import paige.navic.domain.models.settings.NowPlayingBackgroundStyle
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.icons.Icons
import paige.navic.icons.outlined.KeyboardArrowDown
import paige.navic.icons.outlined.Info
import paige.navic.icons.outlined.List
import paige.navic.icons.outlined.Lyrics
import paige.navic.icons.outlined.Movie
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.BlendBackground
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.SheetScaffold
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.components.toolbars.SheetActionButton
import paige.navic.ui.components.toolbars.SheetToolbar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.nowPlaying.components.NowPlayingLidaClipArtwork
import paige.navic.ui.screens.nowPlaying.components.NowPlayingLidaClipBackground
import paige.navic.ui.screens.nowPlaying.components.controls.NowPlayingArtworkPager
import paige.navic.ui.screens.nowPlaying.components.rows.NowPlayingControlsRow
import paige.navic.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val backStack = LocalNavStack.current

	val currentScreen = backStack.lastOrNull()
	val isPlayerCurrent = currentScreen is Screen.NowPlaying
		|| currentScreen is Screen.Queue
		|| currentScreen is Screen.PlaybackSpeed

	val playerState by player.uiState.collectAsStateWithLifecycle()
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsStateWithLifecycle()
	val serverCoverLoadFailedSongIds by musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.collectAsStateWithLifecycle()
	val resolvingMusicBrainzSongIds by musicBrainzArtworkRepository.resolvingMusicBrainzSongIds.collectAsStateWithLifecycle()
	val song = playerState.currentSong
	val currentMusicBrainzArtwork = song?.id?.let(musicBrainzArtworkBySongId::get)
	val serverCoverLoadFailed = song?.id?.let { it in serverCoverLoadFailedSongIds } == true
	val currentMusicBrainzFallbackArtworkUrl = externalFallbackArtworkUrl(
		serverCoverArtId = song?.coverArtId,
		externalArtworkUrl = currentMusicBrainzArtwork?.imageUrl,
		serverCoverLoadFailed = serverCoverLoadFailed
	)
	val currentMusicBrainzFallbackArtworkCacheKey = externalFallbackArtworkCacheKey(
		serverCoverArtId = song?.coverArtId,
		externalArtworkCacheKey = currentMusicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	)

	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()
	val songRating by viewModel.songRating.collectAsStateWithLifecycle()
	val lidaClipState by viewModel.lidaClipState.collectAsStateWithLifecycle()
	val lyricsAvailableState by viewModel.lyricsAvailableState.collectAsStateWithLifecycle()
	val lidaClip = lidaClipState.data
	val lyricsAvailable = lyricsAvailableState.data == true
	val nowPlayingIntegrationIndicators = integrationLoadingIndicators(
		lidaClipsLoading = lidaClipState is UiState.Loading,
		musicBrainzLoading = song?.id?.let { it in resolvingMusicBrainzSongIds } == true,
		lyricsLoading = lyricsAvailableState is UiState.Loading
	)
	var foregroundClipSongId by rememberSaveable { mutableStateOf<String?>(null) }
	val showClipInArtwork = lidaClip != null && foregroundClipSongId == song?.id
	val showArtwork = preferenceManager.showNowPlayingArtwork
	val isDynamicBackground = preferenceManager.nowPlayingBackgroundStyle == NowPlayingBackgroundStyle.Dynamic
	val artworkTapDestination = nowPlayingArtworkTapDestination(
		configuredAction = preferenceManager.nowPlayingArtworkTapAction,
		legacyTapArtworkForLyrics = preferenceManager.tapArtworkForLyrics,
		showNowPlayingArtwork = showArtwork,
		hasCurrentSong = song != null,
		hasResolvedLyrics = lyricsAvailable
	)
	val onArtworkTap: (() -> Unit)? = when (artworkTapDestination) {
		NowPlayingArtworkTapDestination.Lyrics -> {
			{ backStack.add(Screen.Lyrics) }
		}
		NowPlayingArtworkTapDestination.TrackInfo -> {
			{ backStack.add(Screen.MusicBrainzInfo) }
		}
		null -> null
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			val toolbarButtonContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f)
			val toolbarButtonContentColor = MaterialTheme.colorScheme.onSecondaryContainer
			SheetToolbar(
				modifier = Modifier.alpha(if (isPlayerCurrent) 1f else 0f),
				windowInsets = windowInsets,
				isBottomToolbar = preferenceManager.nowPlayingToolbarPosition == ToolbarPosition.Bottom,
				title = {
					Text(stringResource(Res.string.title_now_playing))
				},
				navigationIcon = {
					TopBarButton(
						onClick = { backStack.remove(Screen.NowPlaying) },
						containerColor = toolbarButtonContainerColor,
						contentColor = toolbarButtonContentColor,
						content = {
							Icon(
								imageVector = Icons.Outlined.KeyboardArrowDown,
								contentDescription = stringResource(Res.string.action_navigate_back)
							)
						}
					)
				},
				actions = {
					val showLyricsAction = shouldShowNowPlayingLyricsAction(
						userActionEnabled = preferenceManager.showNowPlayingLyricsAction,
						hasCurrentSong = song != null,
						hasResolvedLyrics = lyricsAvailable
					)
					val musicVideoAction = lidaClipsNowPlayingMusicVideoAction(
						lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
						lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
						userActionEnabled = preferenceManager.showNowPlayingMusicVideoAction,
						songId = song?.id,
						hasResolvedClip = lidaClip != null
					)
					val showMusicVideoAction = musicVideoAction != null
					val showMusicBrainzInfoAction =
						shouldShowNowPlayingMusicBrainzInfoAction(
							fallbackEnabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
							hasCurrentSong = song != null,
							artworkTapDestination = artworkTapDestination
						)
					val showQueueAction = preferenceManager.showNowPlayingQueueAction
					val visibleActionCount = listOf(
						showLyricsAction,
						showMusicBrainzInfoAction,
						showMusicVideoAction,
						showQueueAction
					).count { it }
					var actionIndex = 0

					if (showLyricsAction) {
						SheetActionButton(
							icon = Icons.Outlined.Lyrics,
							contentDescription = stringResource(Res.string.action_lyrics),
							onClick = dropUnlessResumed { backStack.add(Screen.Lyrics) },
							isStartRounded = actionIndex == 0,
							isEndRounded = actionIndex == visibleActionCount - 1,
							containerColor = toolbarButtonContainerColor,
							contentColor = toolbarButtonContentColor
						)
						actionIndex++
					}
					if (showMusicBrainzInfoAction) {
						SheetActionButton(
							icon = Icons.Outlined.Info,
							contentDescription = stringResource(Res.string.action_musicbrainz_info),
							onClick = dropUnlessResumed { backStack.add(Screen.MusicBrainzInfo) },
							isStartRounded = actionIndex == 0,
							isEndRounded = actionIndex == visibleActionCount - 1,
							containerColor = toolbarButtonContainerColor,
							contentColor = toolbarButtonContentColor
						)
						actionIndex++
					}
					if (showMusicVideoAction) {
						SheetActionButton(
							icon = Icons.Outlined.Movie,
							contentDescription = stringResource(Res.string.action_play_music_video),
							onClick = dropUnlessResumed {
								when (musicVideoAction) {
									LidaClipsNowPlayingMusicVideoAction.ToggleArtworkClip ->
										foregroundClipSongId = if (showClipInArtwork) null else song?.id

									LidaClipsNowPlayingMusicVideoAction.OpenPlayer ->
										song?.id?.let { backStack.add(Screen.LidaClipPlayer(it)) }
								}
							},
							isStartRounded = actionIndex == 0,
							isEndRounded = actionIndex == visibleActionCount - 1,
							containerColor = toolbarButtonContainerColor,
							contentColor = toolbarButtonContentColor
						)
						actionIndex++
					}
					if (showQueueAction) {
						SheetActionButton(
							icon = Icons.Outlined.List,
							contentDescription = stringResource(Res.string.action_queue),
							onClick = dropUnlessResumed { backStack.add(Screen.Queue) },
							isStartRounded = actionIndex == 0,
							isEndRounded = actionIndex == visibleActionCount - 1,
							containerColor = toolbarButtonContainerColor,
							contentColor = toolbarButtonContentColor
						)
					}
				}
			)
		}
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			if (isDynamicBackground) {
				BlendBackground(
					coverArtId = song?.coverArtId,
					imageUrl = currentMusicBrainzFallbackArtworkUrl,
					imageCacheKey = currentMusicBrainzFallbackArtworkCacheKey,
					isPaused = playerState.isPaused,
					showBottomGradient = shouldShowNowPlayingBackgroundBottomGradient(
						enabled = preferenceManager.nowPlayingBackgroundBottomGradient,
						isDynamicBackground = isDynamicBackground
					)
				)
			}
			if (
				lidaClip != null &&
				!showClipInArtwork &&
				shouldShowLidaClipBackgroundVideo(preferenceManager.lidaClipsBackgroundVideoMode)
			) {
				NowPlayingLidaClipBackground(
					clip = lidaClip,
					backgroundVideoMode = preferenceManager.lidaClipsBackgroundVideoMode,
					playerProgress = playerState.progress,
					musicIsPaused = playerState.isPaused,
					onRecoverablePlaybackError = {
						foregroundClipSongId = null
						viewModel.refreshLidaClip()
					},
					modifier = Modifier.fillMaxSize()
				)
			}
			if (!isPlayerCurrent) return@Box
			IntegrationLoadingIndicatorStrip(
				indicators = nowPlayingIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = nowPlayingIntegrationIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(
						start = 14.dp,
						top = contentPadding.calculateTopPadding() + 8.dp
					)
			)
			BoxWithConstraints(
				modifier = Modifier
					.padding(horizontal = 8.dp)
					.fillMaxSize()
			) {
				val isLandscape = maxWidth > maxHeight
				val toolbarPosition = preferenceManager.nowPlayingToolbarPosition
				val padding = when {
					isLandscape -> contentPadding
					!shouldReserveNowPlayingToolbarGap(toolbarPosition, isLandscape = isLandscape) -> contentPadding
					toolbarPosition == ToolbarPosition.Top -> contentPadding.plus(
						PaddingValues(
							bottom = 40.dp
						)
					)

					toolbarPosition == ToolbarPosition.Bottom -> contentPadding.plus(
						PaddingValues(
							top = 40.dp
						)
					)

					else -> contentPadding
				}
				if (isLandscape) {
					Row(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically
					) {
						if (showArtwork || showClipInArtwork) {
							NowPlayingMediaSlot(
								modifier = Modifier.weight(1f).fillMaxHeight(),
								isLandscape = true,
								clip = lidaClip,
								showClipInArtwork = showClipInArtwork,
								playerProgress = playerState.progress,
								musicIsPaused = playerState.isPaused,
								onArtworkTap = onArtworkTap,
								onLidaClipRecoverablePlaybackError = {
									foregroundClipSongId = null
									viewModel.refreshLidaClip()
								}
							)
						}
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							isLandscape = true,
							hasCurrentSong = song != null,
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) },
							songRating = songRating,
							onSetSongRating = { viewModel.rateSong(it) }
						)
					}
				} else {
					Column(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
						if (showArtwork || showClipInArtwork) {
							NowPlayingMediaSlot(
								modifier = Modifier.weight(1f).fillMaxWidth(),
								isLandscape = false,
								clip = lidaClip,
								showClipInArtwork = showClipInArtwork,
								playerProgress = playerState.progress,
								musicIsPaused = playerState.isPaused,
								onArtworkTap = onArtworkTap,
								onLidaClipRecoverablePlaybackError = {
									foregroundClipSongId = null
									viewModel.refreshLidaClip()
								}
							)
						}
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f),
							isLandscape = false,
							hasCurrentSong = song != null,
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) },
							songRating = songRating,
							onSetSongRating = { viewModel.rateSong(it) }
						)
					}
				}
			}
		}
	}
}

@Composable
private fun NowPlayingMediaSlot(
	clip: DomainLidaClip?,
	showClipInArtwork: Boolean,
	playerProgress: Float,
	musicIsPaused: Boolean,
	isLandscape: Boolean,
	onArtworkTap: (() -> Unit)?,
	onLidaClipRecoverablePlaybackError: () -> Unit,
	modifier: Modifier = Modifier
) {
	Box(modifier) {
		NowPlayingArtworkPager(
			modifier = Modifier.matchParentSize(),
			isLandscape = isLandscape,
			onArtworkTap = onArtworkTap
		)
		if (!showClipInArtwork || clip == null) return@Box
		NowPlayingLidaClipArtwork(
			clip = clip,
			playerProgress = playerProgress,
			musicIsPaused = musicIsPaused,
			onRecoverablePlaybackError = onLidaClipRecoverablePlaybackError,
			modifier = Modifier.matchParentSize()
		)
	}
}
