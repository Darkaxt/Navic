package paige.navic.ui.screens.bindery

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_navigate_back
import navic.composeapp.generated.resources.action_seek_backward_seconds
import navic.composeapp.generated.resources.action_seek_forward_seconds
import navic.composeapp.generated.resources.action_sleep_timer
import navic.composeapp.generated.resources.info_bindery_audiobook_unavailable
import navic.composeapp.generated.resources.option_playback_speed
import navic.composeapp.generated.resources.title_chapters
import navic.composeapp.generated.resources.title_current_chapter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.domain.repositories.binderyRequestHeadersForUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Audiobooks
import paige.navic.icons.outlined.Bedtime
import paige.navic.icons.outlined.KeyboardArrowDown
import paige.navic.icons.outlined.List
import paige.navic.icons.outlined.Speed
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.readerReadaloudPlaybackSpeedLabel
import paige.navic.ui.components.common.BlendBackground
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.PlaybackProgressSlider
import paige.navic.ui.components.layouts.SheetScaffold
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.components.sheets.SleepTimerSheet
import paige.navic.ui.components.toolbars.SheetActionButton
import paige.navic.ui.components.toolbars.SheetToolbar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderyAudiobookPlayerScreen(
	bookId: String,
	title: String,
	versionRowId: String
) {
	val viewModel = koinViewModel<BinderyAudiobookPlayerViewModel>(
		key = "bindery-audiobook-player-$bookId-$versionRowId",
		parameters = { parametersOf(bookId) }
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val manifestState by viewModel.manifestState.collectAsStateWithLifecycle()
	val manifest = manifestState.data
	val providerCoverUrlState by viewModel.providerCoverUrlState.collectAsStateWithLifecycle()
	val binderyConfigured = shouldLoadBinderyUi(
		binderyEnabled = preferenceManager.binderyEnabled,
		opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
		apiKey = preferenceManager.binderyApiKey
	)
	val binderyRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val findingsState by viewModel.findingsState.collectAsStateWithLifecycle()
	val findingsCatalog = findingsState.data
	val chapters = remember(manifest, versionRowId) {
		manifest?.let { binderyAudiobookChapters(it, versionRowId) }.orEmpty()
	}
	val resumeProgress = remember(bookId, versionRowId) {
		viewModel.rememberedProgress(versionRowId)
	}
	val playbackPlan = remember(manifest, versionRowId, preferenceManager.binderyOpdsBaseUrl, binderyRequestHeaders, resumeProgress) {
		manifest?.takeIf { chapters.isNotEmpty() }?.let {
			binderyAudiobookPlaybackPlan(
				manifest = it,
				versionRowId = versionRowId,
				opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
				requestHeaders = binderyRequestHeaders,
				resumeProgress = resumeProgress
			)
		}
	}
	val coverSelection = remember(manifest, bookId, versionRowId, findingsCatalog) {
		manifest?.let {
			binderyAudiobookCoverSelection(
				manifest = it,
				versionRowId = versionRowId,
				findingsCatalog = findingsCatalog,
				routeBookId = bookId
			)
		}
	}
	val fallbackCoverUrl = remember(coverSelection?.fallbackCoverHref, preferenceManager.binderyOpdsBaseUrl) {
		coverSelection?.fallbackCoverHref?.let { binderyEndpoint(preferenceManager.binderyOpdsBaseUrl, it) }
	}
	val coverUrl = providerCoverUrlState.data?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl
	val imageRequestHeaders = remember(coverUrl, preferenceManager.binderyOpdsBaseUrl, binderyRequestHeaders) {
		binderyRequestHeadersForUrl(
			baseUrl = preferenceManager.binderyOpdsBaseUrl,
			url = coverUrl,
			requestHeaders = binderyRequestHeaders
		)
	}
	val coverCacheKey = remember(manifest?.id, versionRowId, coverUrl) {
		"bindery-audiobook:${manifest?.id.orEmpty()}:$versionRowId:${coverUrl.orEmpty()}"
	}
	var playbackState by remember {
		mutableStateOf(ReaderReadaloudPlaybackUiState())
	}
	var playbackCommand by remember { mutableStateOf<ReaderReadaloudPlaybackCommand?>(null) }
	var playbackCommandKey by remember { mutableLongStateOf(0L) }
	var runtimeError by rememberSaveable { mutableStateOf<String?>(null) }
	var speedSheetOpen by rememberSaveable { mutableStateOf(false) }
	var chaptersSheetOpen by rememberSaveable { mutableStateOf(false) }
	var sleepTimerSheetOpen by rememberSaveable { mutableStateOf(false) }
	val screen = Screen.BinderyAudiobookPlayer(bookId, title, versionRowId)

	fun dispatch(command: ReaderReadaloudPlaybackCommand) {
		playbackCommand = command
		playbackCommandKey++
	}

	LaunchedEffect(binderyConfigured, bookId) {
		if (binderyConfigured) {
			viewModel.refreshManifest(fullRefresh = false)
		}
	}

	LaunchedEffect(coverSelection?.finding, coverSelection?.fallbackCoverHref) {
		if (coverSelection != null) {
			viewModel.refreshProviderCover(
				finding = coverSelection.finding,
				fallbackCoverHref = coverSelection.fallbackCoverHref
			)
		}
	}

	BinderyAudiobookRuntimeHost(
		playbackPlan = playbackPlan,
		bookId = bookId,
		bookTitle = title,
		versionRowId = versionRowId,
		coverUrl = coverUrl,
		coverCacheKey = coverCacheKey,
		imageRequestHeaders = imageRequestHeaders,
		playbackCommand = playbackCommand,
		playbackCommandKey = playbackCommandKey,
		onPlaybackState = { playbackState = it },
		onPlaybackPosition = { position -> viewModel.savePlaybackProgress(versionRowId, position) },
		onError = { runtimeError = it }
	)

	SheetScaffold(
		toolbar = { windowInsets ->
			SheetToolbar(
				windowInsets = windowInsets,
				navigationIcon = {},
				actions = {
					SheetActionButton(
						icon = Icons.Outlined.KeyboardArrowDown,
						contentDescription = stringResource(Res.string.action_navigate_back),
						isStartRounded = true,
						containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
						contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
						onClick = dropUnlessResumed { backStack.remove(screen) }
					)
					SheetActionButton(
						icon = Icons.Outlined.Speed,
						contentDescription = stringResource(Res.string.option_playback_speed),
						containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
						contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
						onClick = { speedSheetOpen = true }
					)
					SheetActionButton(
						icon = Icons.Outlined.List,
						contentDescription = stringResource(Res.string.title_chapters),
						containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
						contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
						onClick = { chaptersSheetOpen = true }
					)
					SheetActionButton(
						icon = Icons.Outlined.Bedtime,
						contentDescription = stringResource(Res.string.action_sleep_timer),
						isEndRounded = true,
						containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
						contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
						onClick = { sleepTimerSheetOpen = true }
					)
				}
			)
		}
	) { contentPadding ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background)
		) {
			if (coverUrl != null) {
				BlendBackground(
					coverArtId = null,
					imageUrl = coverUrl,
					imageCacheKey = coverCacheKey,
					imageRequestHeaders = imageRequestHeaders,
					isPaused = !playbackState.isPlaying,
					showBottomGradient = true
				)
			}
			BoxWithConstraints(
				modifier = Modifier
					.fillMaxSize()
					.padding(contentPadding)
					.padding(horizontal = 18.dp)
			) {
				val isLandscape = maxWidth > maxHeight
				when {
					!binderyConfigured -> {
						ContentUnavailable(
							icon = Icons.Outlined.Audiobooks,
							label = title,
							modifier = Modifier.align(Alignment.Center)
						)
					}
					manifest == null -> {
						ContentUnavailable(
							icon = Icons.Outlined.Audiobooks,
							label = title,
							modifier = Modifier.align(Alignment.Center)
						)
					}
					chapters.isEmpty() -> {
						ContentUnavailable(
							icon = Icons.Outlined.Audiobooks,
							label = stringResource(Res.string.info_bindery_audiobook_unavailable),
							modifier = Modifier.align(Alignment.Center)
						)
					}
					isLandscape -> {
						BinderyAudiobookLandscapeLayout(
							manifest = manifest,
							coverUrl = coverUrl,
							coverCacheKey = coverCacheKey,
							requestHeaders = imageRequestHeaders,
							playbackState = playbackState,
							chapters = chapters,
							onCommand = ::dispatch,
							onOpenSpeed = { speedSheetOpen = true },
							onOpenChapters = { chaptersSheetOpen = true },
							modifier = Modifier.fillMaxSize()
						)
					}
					else -> {
						BinderyAudiobookPortraitLayout(
							manifest = manifest,
							coverUrl = coverUrl,
							coverCacheKey = coverCacheKey,
							requestHeaders = imageRequestHeaders,
							playbackState = playbackState,
							chapters = chapters,
							onCommand = ::dispatch,
							onOpenSpeed = { speedSheetOpen = true },
							onOpenChapters = { chaptersSheetOpen = true },
							modifier = Modifier.fillMaxSize()
						)
					}
				}
			}
		}
	}

	if (speedSheetOpen) {
		BinderyAudiobookSpeedSheet(
			selectedSpeed = playbackState.playbackSpeed,
			onSelectSpeed = { speed ->
				dispatch(ReaderReadaloudPlaybackCommand.SetSpeed(speed))
				speedSheetOpen = false
			},
			onDismissRequest = { speedSheetOpen = false }
		)
	}
	if (chaptersSheetOpen) {
		BinderyAudiobookChaptersSheet(
			chapters = chapters,
			activeTrackIndex = playbackState.trackIndex,
			onChapterClick = { chapter ->
				dispatch(ReaderReadaloudPlaybackCommand.SeekToTrack(chapter.index, 0L))
				chaptersSheetOpen = false
			},
			onDismissRequest = { chaptersSheetOpen = false }
		)
	}
	if (sleepTimerSheetOpen) {
		SleepTimerSheet(onDismissRequest = { sleepTimerSheetOpen = false })
	}

	ErrorSnackbar(
		error = runtimeError?.let(::Exception) ?: (manifestState as? UiState.Error)?.error,
		onClearError = {
			runtimeError = null
			viewModel.clearError()
		}
	)
}

@Composable
private fun BinderyAudiobookPortraitLayout(
	manifest: BinderyManifest,
	coverUrl: String?,
	coverCacheKey: String,
	requestHeaders: Map<String, String>,
	playbackState: ReaderReadaloudPlaybackUiState,
	chapters: List<BinderyAudiobookChapter>,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onOpenSpeed: () -> Unit,
	onOpenChapters: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		BinderyAudiobookArtwork(
			manifest = manifest,
			coverUrl = coverUrl,
			coverCacheKey = coverCacheKey,
			requestHeaders = requestHeaders,
			modifier = Modifier
				.widthIn(max = 260.dp)
				.fillMaxWidth(.64f)
				.aspectRatio(2f / 3f)
				.binderyAudiobookEntrance(delayMillis = 50L)
		)
		Spacer(Modifier.height(28.dp))
		BinderyAudiobookControlsLayout(
			manifest = manifest,
			playbackState = playbackState,
			chapters = chapters,
			onCommand = onCommand,
			onOpenSpeed = onOpenSpeed,
			onOpenChapters = onOpenChapters,
			modifier = Modifier
				.fillMaxWidth()
				.binderyAudiobookEntrance(delayMillis = 180L)
		)
	}
}

@Composable
private fun BinderyAudiobookLandscapeLayout(
	manifest: BinderyManifest,
	coverUrl: String?,
	coverCacheKey: String,
	requestHeaders: Map<String, String>,
	playbackState: ReaderReadaloudPlaybackUiState,
	chapters: List<BinderyAudiobookChapter>,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onOpenSpeed: () -> Unit,
	onOpenChapters: () -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier.padding(horizontal = 24.dp),
		horizontalArrangement = Arrangement.spacedBy(36.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			modifier = Modifier
				.weight(.8f)
				.fillMaxHeight(),
			contentAlignment = Alignment.Center
		) {
			BinderyAudiobookArtwork(
				manifest = manifest,
				coverUrl = coverUrl,
				coverCacheKey = coverCacheKey,
				requestHeaders = requestHeaders,
				modifier = Modifier
					.fillMaxHeight(.74f)
					.aspectRatio(2f / 3f)
					.binderyAudiobookEntrance(delayMillis = 50L)
			)
		}
		BinderyAudiobookControlsLayout(
			manifest = manifest,
			playbackState = playbackState,
			chapters = chapters,
			onCommand = onCommand,
			onOpenSpeed = onOpenSpeed,
			onOpenChapters = onOpenChapters,
			modifier = Modifier
				.weight(1f)
				.binderyAudiobookEntrance(delayMillis = 180L)
		)
	}
}

@Composable
private fun BinderyAudiobookControlsLayout(
	manifest: BinderyManifest,
	playbackState: ReaderReadaloudPlaybackUiState,
	chapters: List<BinderyAudiobookChapter>,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onOpenSpeed: () -> Unit,
	onOpenChapters: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		BinderyAudiobookInfo(
			manifest = manifest,
			activeChapter = chapters.getOrNull(playbackState.trackIndex),
			modifier = Modifier.fillMaxWidth()
		)
		Spacer(Modifier.height(20.dp))
		BinderyAudiobookProgress(
			state = playbackState,
			activeChapter = chapters.getOrNull(playbackState.trackIndex),
			onCommand = onCommand
		)
		Spacer(Modifier.height(18.dp))
		BinderyAudiobookTransportRow(
			state = playbackState,
			onCommand = onCommand
		)
		Spacer(Modifier.height(18.dp))
		BinderyAudiobookActionRow(
			state = playbackState,
			onOpenSpeed = onOpenSpeed,
			onOpenChapters = onOpenChapters
		)
	}
}

@Composable
private fun BinderyAudiobookArtwork(
	manifest: BinderyManifest,
	coverUrl: String?,
	coverCacheKey: String,
	requestHeaders: Map<String, String>,
	modifier: Modifier = Modifier
) {
	val shape = ContinuousRoundedRectangle(14.dp)
	CoverArt(
		coverArtId = null,
		imageUrl = coverUrl,
		imageCacheKey = coverCacheKey,
		imageRequestHeaders = requestHeaders,
		contentDescription = manifest.title,
		fallbackKind = "Audiobook",
		modifier = modifier,
		square = false,
		shadowElevation = 8.dp,
		shape = shape,
		contentScale = ContentScale.Fit
	)
}

@Composable
private fun Modifier.binderyAudiobookEntrance(delayMillis: Long): Modifier {
	var visible by rememberSaveable { mutableStateOf(false) }
	val scaleFactor by animateFloatAsState(if (visible) 1f else .92f)
	val offset by animateDpAsState(if (visible) 0.dp else 56.dp)

	LaunchedEffect(delayMillis) {
		delay(delayMillis.milliseconds)
		visible = true
	}

	return scale(scaleFactor).offset {
		IntOffset(x = 0, y = offset.roundToPx())
	}
}

@Composable
private fun BinderyAudiobookInfo(
	manifest: BinderyManifest,
	activeChapter: BinderyAudiobookChapter?,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier.padding(horizontal = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Text(
			text = manifest.title,
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.Center,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis
		)
		manifest.author?.trim()?.takeIf { it.isNotEmpty() }?.let { author ->
			Text(
				text = author,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		activeChapter?.let { chapter ->
			Text(
				text = stringResource(Res.string.title_current_chapter),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary
			)
			Text(
				text = chapter.title,
				style = MaterialTheme.typography.titleMedium,
				textAlign = TextAlign.Center,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun BinderyAudiobookProgress(
	state: ReaderReadaloudPlaybackUiState,
	activeChapter: BinderyAudiobookChapter?,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val durationMs = state.durationMs ?: activeChapter?.durationMs
	val progress = if (durationMs != null && durationMs > 0L) {
		(state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
	} else {
		0f
	}
	Column(Modifier.fillMaxWidth()) {
		PlaybackProgressSlider(
			value = progress,
			onValueChange = { progress ->
				durationMs?.takeIf { it > 0L }?.let { duration ->
					onCommand(ReaderReadaloudPlaybackCommand.SeekTo((duration * progress).roundToLong()))
				}
			},
			isPlaying = state.isPlaying,
			enabled = state.isAvailable && durationMs != null && durationMs > 0L,
			sliderStyle = preferenceManager.nowPlayingSliderStyle,
			progressWidth = preferenceManager.nowPlayingProgressWidth,
			modifier = Modifier.fillMaxWidth()
		)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 4.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = state.positionMs.audiobookTimeLabel(),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			Spacer(Modifier.weight(1f))
			Text(
				text = durationMs?.let { remaining ->
					"-${(remaining - state.positionMs).coerceAtLeast(0L).audiobookTimeLabel()}"
				} ?: "",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

@Composable
private fun BinderyAudiobookTransportRow(
	state: ReaderReadaloudPlaybackUiState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.widthIn(max = 440.dp),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically
	) {
		binderyAudiobookTransportControls().forEach { control ->
			when (control) {
				BinderyAudiobookTransportControl.SeekBackward30 -> BinderyAudiobookSkipButton(
					label = "-30",
					contentDescription = stringResource(Res.string.action_seek_backward_seconds, 30),
					enabled = state.isAvailable,
					onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(state.positionMs - 30_000L)) }
				)
				BinderyAudiobookTransportControl.SeekBackward10 -> BinderyAudiobookSkipButton(
					label = "-10",
					contentDescription = stringResource(Res.string.action_seek_backward_seconds, 10),
					enabled = state.isAvailable,
					onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(state.positionMs - 10_000L)) }
				)
				BinderyAudiobookTransportControl.PlayPause -> BinderyAudiobookPlayButton(
					state = state,
					onClick = {
						onCommand(
							if (state.isPlaying) {
								ReaderReadaloudPlaybackCommand.Pause
							} else {
								ReaderReadaloudPlaybackCommand.Play
							}
						)
					}
				)
				BinderyAudiobookTransportControl.SeekForward10 -> BinderyAudiobookSkipButton(
					label = "+10",
					contentDescription = stringResource(Res.string.action_seek_forward_seconds, 10),
					enabled = state.isAvailable,
					onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(state.positionMs + 10_000L)) }
				)
				BinderyAudiobookTransportControl.SeekForward30 -> BinderyAudiobookSkipButton(
					label = "+30",
					contentDescription = stringResource(Res.string.action_seek_forward_seconds, 30),
					enabled = state.isAvailable,
					onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(state.positionMs + 30_000L)) }
				)
			}
		}
	}
}

@Composable
private fun BinderyAudiobookSkipButton(
	label: String,
	contentDescription: String,
	enabled: Boolean,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		enabled = enabled,
		shape = CircleShape,
		color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
		contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
		modifier = Modifier.size(52.dp)
	) {
		Box(contentAlignment = Alignment.Center) {
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
				textAlign = TextAlign.Center
			)
		}
	}
}

@Composable
private fun BinderyAudiobookPlayButton(
	state: ReaderReadaloudPlaybackUiState,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		enabled = state.isAvailable,
		shape = CircleShape,
		color = if (state.isAvailable) {
			MaterialTheme.colorScheme.primary
		} else {
			MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
		},
		contentColor = if (state.isAvailable) {
			MaterialTheme.colorScheme.onPrimary
		} else {
			MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
		},
		modifier = Modifier.size(72.dp)
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.Play,
				contentDescription = null,
				modifier = Modifier.size(42.dp)
			)
		}
	}
}

@Composable
private fun BinderyAudiobookActionRow(
	state: ReaderReadaloudPlaybackUiState,
	onOpenSpeed: () -> Unit,
	onOpenChapters: () -> Unit
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		BinderyAudiobookActionChip(
			label = readerReadaloudPlaybackSpeedLabel(state.playbackSpeed),
			icon = { Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(18.dp)) },
			onClick = onOpenSpeed
		)
		BinderyAudiobookActionChip(
			label = stringResource(Res.string.title_chapters),
			icon = { Icon(Icons.Outlined.List, null, modifier = Modifier.size(18.dp)) },
			onClick = onOpenChapters
		)
	}
}

@Composable
private fun BinderyAudiobookActionChip(
	label: String,
	icon: @Composable () -> Unit,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		shape = ContinuousRoundedRectangle(22.dp),
		color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
		contentColor = MaterialTheme.colorScheme.onSecondaryContainer
	) {
		Row(
			modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
			horizontalArrangement = Arrangement.spacedBy(7.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			icon()
			Text(
				text = label,
				style = MaterialTheme.typography.labelLarge,
				maxLines = 1
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinderyAudiobookSpeedSheet(
	selectedSpeed: Float,
	onSelectSpeed: (Float) -> Unit,
	onDismissRequest: () -> Unit
) {
	val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Text(
			text = stringResource(Res.string.option_playback_speed),
			style = MaterialTheme.typography.titleLarge,
			modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
		)
		Column(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			speeds.chunked(4).forEach { row ->
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					row.forEach { speed ->
						Surface(
							onClick = { onSelectSpeed(speed) },
							shape = ContinuousRoundedRectangle(12.dp),
							color = if ((selectedSpeed - speed).let { it * it } < .001f) {
								MaterialTheme.colorScheme.primary
							} else {
								MaterialTheme.colorScheme.surfaceContainerHigh
							},
							contentColor = if ((selectedSpeed - speed).let { it * it } < .001f) {
								MaterialTheme.colorScheme.onPrimary
							} else {
								MaterialTheme.colorScheme.onSurface
							},
							modifier = Modifier.weight(1f)
						) {
							Text(
								text = readerReadaloudPlaybackSpeedLabel(speed),
								textAlign = TextAlign.Center,
								style = MaterialTheme.typography.titleSmall,
								modifier = Modifier.padding(vertical = 14.dp)
							)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinderyAudiobookChaptersSheet(
	chapters: List<BinderyAudiobookChapter>,
	activeTrackIndex: Int,
	onChapterClick: (BinderyAudiobookChapter) -> Unit,
	onDismissRequest: () -> Unit
) {
	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Text(
			text = stringResource(Res.string.title_chapters),
			style = MaterialTheme.typography.titleLarge,
			modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
		)
		LazyColumn(
			contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			items(chapters, key = { chapter -> chapter.href }) { chapter ->
				val selected = chapter.index == activeTrackIndex
				Surface(
					onClick = { onChapterClick(chapter) },
					shape = ContinuousRoundedRectangle(12.dp),
					color = if (selected) {
						MaterialTheme.colorScheme.secondaryContainer
					} else {
						MaterialTheme.colorScheme.surfaceContainerHigh
					},
					contentColor = if (selected) {
						MaterialTheme.colorScheme.onSecondaryContainer
					} else {
						MaterialTheme.colorScheme.onSurface
					}
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 12.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = (chapter.index + 1).toString(),
							style = MaterialTheme.typography.labelLarge,
							color = MaterialTheme.colorScheme.primary,
							modifier = Modifier.width(28.dp),
							textAlign = TextAlign.Center
						)
						Column(Modifier.weight(1f)) {
							Text(
								text = chapter.title,
								style = MaterialTheme.typography.titleSmall,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis
							)
							chapter.subtitle?.let { subtitle ->
								Text(
									text = subtitle,
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
						}
						chapter.durationMs?.let { duration ->
							Text(
								text = duration.audiobookTimeLabel(),
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}
		}
	}
}

private fun Long.audiobookTimeLabel(): String {
	val safeSeconds = (coerceAtLeast(0L) / 1000L)
	val hours = safeSeconds / 3600L
	val minutes = (safeSeconds % 3600L) / 60L
	val seconds = safeSeconds % 60L
	return if (hours > 0L) {
		"$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
	} else {
		"$minutes:${seconds.toString().padStart(2, '0')}"
	}
}
