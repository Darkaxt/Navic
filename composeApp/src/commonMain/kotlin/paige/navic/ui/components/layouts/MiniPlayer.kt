package paige.navic.ui.components.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kyant.capsule.ContinuousRoundedRectangle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_queue
import navic.composeapp.generated.resources.info_not_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.shouldShowMiniPlayerQueueAction
import paige.navic.domain.models.settings.MiniPlayerProgressStyle
import paige.navic.domain.models.settings.MiniPlayerStyle
import paige.navic.domain.models.settings.NavbarConfig
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.icons.Icons
import paige.navic.icons.filled.Note
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.outlined.Queue
import paige.navic.icons.outlined.Radio
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.CoverArtNormalization
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.components.common.applyCoverArtNormalization
import paige.navic.ui.components.common.normalizedCoverArtCacheKey
import paige.navic.ui.components.common.playPauseIconPainter
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.settings.viewmodels.NavtabsViewModel
import paige.navic.util.core.toNetworkHeaders
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	val platformContext = LocalPlatformContext.current
	val player = koinInject<MediaPlayerViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()
	val navtabsViewModel = koinViewModel<NavtabsViewModel>()
	val navtabsState by navtabsViewModel.state.collectAsState()
	val tabs = ((navtabsState as? UiState.Success)?.data ?: NavbarConfig.default)
		.tabs.filter { tab -> tab.visible }
	val backStack = LocalNavStack.current
	val haptics = LocalHapticFeedback.current
	val navBarPadding = if (tabs.size < 2)
		with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
	else 0.dp

	val currentSong by player.currentSongFlow.collectAsState(null)
	val isPaused by player.isPausedFlow.collectAsState(false)
	val isLoading by player.isLoadingFlow.collectAsState(false)
	val playbackDownloadProgressRaw by player.playbackDownloadProgressFlow.collectAsState(null)
	val song = currentSong
	val playbackDownloadProgress = playbackDownloadProgressRaw?.coerceIn(0f, 1f)
	val scope = rememberCoroutineScope()

	val coilPlatformContext = LocalCoilPlatformContext.current
	val sessionManager = koinInject<SessionManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsState()
	val serverCoverLoadFailedSongIds by musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.collectAsState()
	val musicBrainzArtwork = song?.id?.let(musicBrainzArtworkBySongId::get)
	val serverCoverLoadFailed = song?.id?.let { it in serverCoverLoadFailedSongIds } == true
	val playbackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = musicBrainzArtwork?.imageUrl,
		musicBrainzArtworkCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	)
	val hasArtwork = playbackArtwork.hasArtwork
	val serverRequestHeaders = preferenceManager.serverRequestHeadersMap()
	val model = remember(
		playbackArtwork.coverArtId,
		playbackArtwork.imageUrl,
		playbackArtwork.imageCacheKey,
		playbackArtwork.imageRequestHeaders,
		serverRequestHeaders
	) {
		val usesServerCoverArt = playbackArtwork.imageUrl.isNullOrBlank()
		val imageCacheKey = normalizedCoverArtCacheKey(
			cacheKey = playbackArtwork.imageCacheKey ?: playbackArtwork.coverArtId,
			normalization = CoverArtNormalization.TrimWhitespace
		)
		ImageRequest.Builder(coilPlatformContext)
			.data(playbackArtwork.imageUrl ?: playbackArtwork.coverArtId?.let { sessionManager.getCoverArtUrl(it) })
			.memoryCacheKey(imageCacheKey)
			.diskCacheKey(imageCacheKey)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.applyCoverArtNormalization(CoverArtNormalization.TrimWhitespace)
			.apply {
				val requestHeaders = if (usesServerCoverArt) {
					serverRequestHeaders
				} else {
					playbackArtwork.imageRequestHeaders
				}
				if (requestHeaders.isNotEmpty()) {
					httpHeaders(requestHeaders.toNetworkHeaders())
				}
			}
			.build()
	}

	val detached = preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached

	val outerPadding = if (detached) 12.dp else 0.dp
	val coverRounding by animateDpAsState(
		if (isLoading)
			46.dp
		else 8.dp
	)
	val iconSize = if (detached) 24.dp else 32.dp

	val shape = ContinuousRoundedRectangle(
		if (detached) 16.dp else 0.dp
	)

	val onClick = dropUnlessResumed {
		if (!backStack.contains(Screen.NowPlaying)) {
			backStack.add(Screen.NowPlaying)
		}
	}

	val hasSong = song != null
	val isRadio = song?.id?.startsWith("radio_") == true
	val isInteractive = enabled && hasSong
	val showQueueAction = shouldShowMiniPlayerQueueAction(
		enabled = preferenceManager.showMiniPlayerQueueAction,
		hasCurrentSong = hasSong
	)

	fun openQueue() {
		if (backStack.lastOrNull() !is Screen.Queue) {
			platformContext.clickSound()
			backStack.add(Screen.Queue)
		}
	}

	Swiper(
		onSwipeLeft = {
			if (isInteractive) player.next()
		},
		onSwipeRight = {
			if (isInteractive) player.previous()
		},
		modifier = modifier,
		enabled = isInteractive
	) {
		Box(
			modifier = Modifier
				.widthIn(max = if (detached) 600.dp else Dp.Unspecified)
				.padding(
					bottom = if (detached) outerPadding + navBarPadding else 0.dp,
					start = outerPadding,
					end = outerPadding
				)
				.align(Alignment.Center)
		) {
			ListItem(
				modifier = Modifier
					.dropShadow(
						shape,
						Shadow(
							radius = if (detached) 10.dp else 8.dp,
							alpha = 0.25f
						)
					)
					.pointerInput(isInteractive) {
						if (!isInteractive) return@pointerInput
						var totalDrag = 0f
						detectVerticalDragGestures(
							onVerticalDrag = { _, dragAmount ->
								totalDrag += dragAmount
							},
							onDragEnd = {
								if (totalDrag < -150f) {
									onClick()
								}
								totalDrag = 0f
							}
						)
					},
				contentPadding = PaddingValues(
					start = if (detached) 10.dp else 16.dp,
					end = if (detached) 10.dp else 16.dp,
					top = if (detached) 10.dp else 16.dp,
					bottom = (if (detached) 10.dp else 12.dp) + if (detached) 0.dp else navBarPadding
				),
				verticalAlignment = Alignment.CenterVertically,
				colors = ListItemDefaults.colors(
					containerColor = NavigationBarDefaults.containerColor
				),
				shapes = ListItemDefaults.shapes(
					shape = shape,
					selectedShape = shape,
					pressedShape = shape,
					focusedShape = shape,
					hoveredShape = shape,
					draggedShape = shape
				),
				onClick = {
					platformContext.clickSound()
					onClick()
				},
				onLongClick = {
					haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
					onClick()
				},
				leadingContent = {
					Box(contentAlignment = Alignment.Center) {
						AsyncImage(
							model = model,
							contentDescription = null,
							contentScale = ContentScale.Crop,
							onError = {
								if (playbackArtwork.imageUrl.isNullOrBlank()) {
									song?.let { failedSong ->
										musicBrainzArtworkRepository.reportServerCoverLoadFailed(failedSong.id)
										scope.launch {
											musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(failedSong)
										}
									}
								}
							},
							modifier = Modifier
								.size(if (detached) 48.dp else 50.dp)
								.padding(if (isLoading) 8.dp else 0.dp)
								.clip(
									ContinuousRoundedRectangle(coverRounding)
								)
								.background(MaterialTheme.colorScheme.surfaceVariant)
						)
						if (!hasArtwork) {
							Icon(
								imageVector = if (isRadio) Icons.Outlined.Radio else Icons.Filled.Note,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
							)
						}
						AnimatedVisibility(
							isLoading,
							modifier = Modifier.matchParentSize(),
							enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec())
								+ fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
							exit = scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec())
								+ fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
						) {
							if (playbackDownloadProgress != null) {
								CircularProgressIndicator(
									progress = { playbackDownloadProgress },
									modifier = Modifier.matchParentSize(),
									trackColor = MaterialTheme.colorScheme.primaryContainer
								)
							} else {
								CircularProgressIndicator(
									Modifier.matchParentSize(),
									trackColor = MaterialTheme.colorScheme.primaryContainer
								)
							}
						}
					}
				},
				trailingContent = {
					Row(
						horizontalArrangement = Arrangement.spacedBy(
							if (detached) 8.dp else 12.dp
						)
					) {
						val colors = IconButtonDefaults.iconButtonVibrantColors()
						if (showQueueAction) {
							IconButton(
								onClick = {
									openQueue()
								},
								enabled = isInteractive,
								colors = colors
							) {
								Icon(
									imageVector = Icons.Outlined.Queue,
									contentDescription = stringResource(Res.string.action_queue),
									modifier = Modifier.size(iconSize)
								)
							}
						}
						IconButton(
							onClick = {
								platformContext.clickSound()
								if (isPaused) {
									player.resume()
								} else {
									player.pause()
								}
							},
							enabled = isInteractive,
							colors = colors
						) {
							val painter = playPauseIconPainter(isPaused)
							if (painter != null) {
								Icon(
									painter = painter,
									contentDescription = null,
									modifier = Modifier.size(iconSize)
								)
							} else {
								Icon(
									imageVector = if (isPaused)
										Icons.Filled.Play
									else Icons.Filled.Pause,
									contentDescription = null,
									modifier = Modifier.size(iconSize)
								)
							}
						}
						IconButton(
							onClick = {
								platformContext.clickSound()
								player.next()
							},
							enabled = isInteractive,
							colors = colors
						) {
							Icon(
								imageVector = Icons.Filled.SkipNext,
								contentDescription = null,
								modifier = Modifier.size(iconSize)
							)
						}
					}
				},
				content = {
					song?.title?.let { title ->
						MarqueeText(title)
					}
				},
				supportingContent = {
					if (song != null) {
						MarqueeText(song.artistName)
					} else {
						MarqueeText(stringResource(Res.string.info_not_playing))
					}
				},
				enabled = enabled
			)
			MiniPlayerProgressOverlay(
				player = player,
				hasSong = song != null,
				detached = detached,
				shape = shape,
				isInteractive = isInteractive,
				progressStyle = preferenceManager.miniPlayerProgressStyle
			)
		}
	}
}

@Composable
private fun BoxScope.MiniPlayerProgressOverlay(
	player: MediaPlayerViewModel,
	hasSong: Boolean,
	detached: Boolean,
	shape: Shape,
	isInteractive: Boolean,
	progressStyle: MiniPlayerProgressStyle
) {
	val haptics = LocalHapticFeedback.current
	if (progressStyle == MiniPlayerProgressStyle.Visible
		|| progressStyle == MiniPlayerProgressStyle.Seekable
	) {
		var dragging by remember { mutableStateOf(false) }
		val alpha by animateFloatAsState(if (dragging) 1f else .7f)
		val progressValue by player.progressFlow.collectAsState(0f)
		val progress by animateFloatAsState(progressValue.coerceIn(0f, 1f))
		val alignment = if (detached) Alignment.BottomStart else Alignment.TopStart
		Box(
			modifier = Modifier
				.matchParentSize()
				.clip(shape)
				.align(alignment),
			contentAlignment = alignment
		) {
			if (!detached) {
				Box(
					Modifier
						.background(MaterialTheme.colorScheme.surfaceBright)
						.fillMaxWidth()
						.height(3.dp)
				)
			}
			Box(
				Modifier
					.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
					.fillMaxWidth(if (hasSong) progress else 0f)
					.height(3.dp)
			)
			Box(
				Modifier
					.fillMaxWidth()
					.height(14.dp)
					.then(
						if (hasSong
							&& progressStyle == MiniPlayerProgressStyle.Seekable
							&& isInteractive
						)
							Modifier.pointerInput(Unit) {
								detectDragGestures(
									onDragStart = {
										dragging = true
										haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
									},
									onDragEnd = {
										dragging = false
										haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
									}
								) { change, _ ->
									player.seek(
										(change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
									)
									change.consume()
								}
							}
						else Modifier
					)
			)
		}
	}
}
