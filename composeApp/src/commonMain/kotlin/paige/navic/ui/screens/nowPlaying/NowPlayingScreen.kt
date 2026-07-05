package paige.navic.ui.screens.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.LidaClipsNowPlayingMusicVideoAction
import paige.navic.domain.models.NowPlayingArtworkTapDestination
import paige.navic.domain.models.NowPlayingMediaSlotMode
import paige.navic.domain.models.lidaClipsNowPlayingMusicVideoAction
import paige.navic.domain.models.nowPlayingArtworkTapDestination
import paige.navic.domain.models.nowPlayingMediaSlotMode
import paige.navic.domain.models.nowPlayingUpNextLayout
import paige.navic.domain.models.nowPlayingWideLandscapeContentLayout
import paige.navic.domain.models.retainedNowPlayingForegroundClipSongId
import paige.navic.domain.models.shouldReserveNowPlayingToolbarGap
import paige.navic.domain.models.shouldShowNowPlayingMusicBrainzInfoAction
import paige.navic.domain.models.shouldShowNowPlayingLyricsAction
import paige.navic.domain.models.shouldShowLidaClipBackgroundVideo
import paige.navic.domain.models.shouldShowNowPlayingBackgroundBottomGradient
import paige.navic.domain.models.shouldUseWideNowPlayingLandscapeLayout
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
import paige.navic.ui.components.common.MusicIntegrationServices
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
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
import paige.navic.ui.screens.nowPlaying.components.rows.NowPlayingWindowActions
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
	val currentPlaybackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = currentMusicBrainzArtwork?.imageUrl,
		musicBrainzArtworkCacheKey = currentMusicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	)
	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()
	val songRating by viewModel.songRating.collectAsStateWithLifecycle()
	val lidaClipState by viewModel.lidaClipState.collectAsStateWithLifecycle()
	val lyricsAvailableState by viewModel.lyricsAvailableState.collectAsStateWithLifecycle()
	val lidaClip = lidaClipState.data
	val lyricsAvailable = lyricsAvailableState.data == true
	val showTechnicalInfo = preferenceManager.nowPlayingSongInfo && song != null
	val nowPlayingIntegrationIndicators = integrationLoadingIndicators(
		lidaClipsLoading = lidaClipState is UiState.Loading,
		musicBrainzLoading = song?.id?.let { it in resolvingMusicBrainzSongIds } == true,
		lyricsLoading = lyricsAvailableState is UiState.Loading
	)
	var foregroundClipSongId by rememberSaveable { mutableStateOf<String?>(null) }
	val showArtwork = preferenceManager.showNowPlayingArtwork
	LaunchedEffect(song?.id) {
		foregroundClipSongId = retainedNowPlayingForegroundClipSongId(
			foregroundClipSongId = foregroundClipSongId,
			currentSongId = song?.id
		)
	}
	val mediaSlotMode = nowPlayingMediaSlotMode(
		showArtwork = showArtwork,
		currentSongId = song?.id,
		foregroundClipSongId = foregroundClipSongId,
		hasClip = lidaClip != null
	)
	val showClipInArtwork = mediaSlotMode == NowPlayingMediaSlotMode.ForegroundClip
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
				navigationIcon = {},
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
					coverArtId = currentPlaybackArtwork.coverArtId,
					imageUrl = currentPlaybackArtwork.imageUrl,
					imageCacheKey = currentPlaybackArtwork.imageCacheKey,
					imageRequestHeaders = currentPlaybackArtwork.imageRequestHeaders,
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
					loadingIndicators = nowPlayingIntegrationIndicators,
					relevantServices = MusicIntegrationServices
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
				val isWideLandscape = shouldUseWideNowPlayingLandscapeLayout(
					widthDp = maxWidth.value.toInt(),
					heightDp = maxHeight.value.toInt()
				)
				val collapseTopPadding = if (preferenceManager.nowPlayingToolbarPosition == ToolbarPosition.Top) {
					contentPadding.calculateTopPadding() + 8.dp
				} else {
					WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 8.dp
				}
				if (!isLandscape) {
					TopBarButton(
						onClick = { backStack.remove(Screen.NowPlaying) },
						containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
						contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
						shadowElevation = 4.dp,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(top = collapseTopPadding, end = 14.dp),
						content = {
							Icon(
								imageVector = Icons.Outlined.KeyboardArrowDown,
								contentDescription = stringResource(Res.string.action_navigate_back)
							)
						}
					)
				} else if (isWideLandscape) {
					NowPlayingWindowActions(
						onCollapse = { backStack.remove(Screen.NowPlaying) },
						songIsStarred = songIsStarred,
						onSetSongIsStarred = { viewModel.starSong(it) },
						songRating = songRating,
						onSetSongRating = { viewModel.rateSong(it) },
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(top = collapseTopPadding, end = 14.dp)
					)
				}
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
						if (mediaSlotMode != NowPlayingMediaSlotMode.Empty) {
							NowPlayingMediaSlot(
								modifier = Modifier.weight(1f).fillMaxHeight(),
								isLandscape = true,
								isWideLandscape = isWideLandscape,
								clip = lidaClip,
								mode = mediaSlotMode,
								playerProgress = playerState.progress,
								musicIsPaused = playerState.isPaused,
								onArtworkTap = onArtworkTap,
								onLidaClipRecoverablePlaybackError = {
									foregroundClipSongId = null
									viewModel.refreshLidaClip()
								}
							)
						}
						BoxWithConstraints(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							contentAlignment = Alignment.Center
						) {
							val controlsModifier = if (isWideLandscape) {
								val contentLayout = nowPlayingWideLandscapeContentLayout(
									contentPaneWidthDp = maxWidth.value.toInt()
								)
								Modifier.width(contentLayout.progressWidthDp.dp)
							} else {
								Modifier
									.fillMaxWidth()
									.widthIn(max = 560.dp)
							}
							NowPlayingControlsRow(
								modifier = controlsModifier,
								isLandscape = true,
								hasCurrentSong = song != null,
								showTechnicalInfo = showTechnicalInfo,
								onCollapse = if (isWideLandscape) null else ({ backStack.remove(Screen.NowPlaying) }),
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) },
								songRating = songRating,
								onSetSongRating = { viewModel.rateSong(it) },
								showInlineActions = !isWideLandscape,
								upNextLayout = nowPlayingUpNextLayout(wideLandscape = isWideLandscape)
							)
						}
					}
				} else {
					Column(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
						if (mediaSlotMode != NowPlayingMediaSlotMode.Empty) {
							NowPlayingMediaSlot(
								modifier = Modifier.weight(1f).fillMaxWidth(),
								isLandscape = false,
								isWideLandscape = false,
								clip = lidaClip,
								mode = mediaSlotMode,
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
							showTechnicalInfo = showTechnicalInfo,
							onCollapse = null,
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) },
							songRating = songRating,
							onSetSongRating = { viewModel.rateSong(it) },
							upNextLayout = nowPlayingUpNextLayout(wideLandscape = false)
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
	mode: NowPlayingMediaSlotMode,
	playerProgress: Float,
	musicIsPaused: Boolean,
	isLandscape: Boolean,
	isWideLandscape: Boolean,
	onArtworkTap: (() -> Unit)?,
	onLidaClipRecoverablePlaybackError: () -> Unit,
	modifier: Modifier = Modifier
) {
	Box(modifier) {
		when (mode) {
			NowPlayingMediaSlotMode.VinylArtwork -> {
				NowPlayingArtworkPager(
					modifier = Modifier.matchParentSize(),
					isLandscape = isLandscape,
					isWideLandscape = isWideLandscape,
					onArtworkTap = onArtworkTap
				)
			}

			NowPlayingMediaSlotMode.ForegroundClip -> {
				if (clip != null) {
					NowPlayingLidaClipArtwork(
						clip = clip,
						playerProgress = playerProgress,
						musicIsPaused = musicIsPaused,
						onRecoverablePlaybackError = onLidaClipRecoverablePlaybackError,
						modifier = Modifier
							.matchParentSize()
							.padding(if (isLandscape) 28.dp else 12.dp)
					)
				}
			}

			NowPlayingMediaSlotMode.Empty -> Unit
		}
	}
}
