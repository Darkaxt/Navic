package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.icons.Icons
import paige.navic.icons.filled.Settings
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.filled.SkipPrevious
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.Bookmark
import paige.navic.icons.outlined.BookmarkBorder
import paige.navic.icons.outlined.List
import paige.navic.reader.ReaderControllerDialog
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderCoordinator
import paige.navic.reader.ReaderCoordinatorStep
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderEngineEvent
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderEngineOpenRequest
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneDisabled
import paige.navic.reader.ReaderTapZoneEdge
import paige.navic.reader.ReaderTapZoneKindle
import paige.navic.reader.ReaderTapZoneLShaped
import paige.navic.reader.ReaderTapZoneRightLeft
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.applyReaderCoordinatorStep
import paige.navic.reader.bestReaderStartLocator
import paige.navic.reader.defaultReaderSettings
import paige.navic.reader.normalizedReaderDirection
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.normalizedReaderTapZone
import paige.navic.reader.readerDefaultTapZoneMode
import paige.navic.reader.toReaderStartLocatorForReader
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

private const val ReaderScreenTag = "ReaderScreen"
private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

private enum class KomikkuNavBarType {
	VerticalRight,
	VerticalLeft,
	Bottom
}

private data class KomikkuReadingModeOption(
	val label: String,
	val flowMode: String,
	val paged: Boolean,
	val direction: String
)

private val KomikkuReadingModeOptions = listOf(
	KomikkuReadingModeOption(
		label = "Default",
		flowMode = ReaderFlowPaged,
		paged = true,
		direction = ReaderDirectionDefault
	),
	KomikkuReadingModeOption(
		label = "Paged (left to right)",
		flowMode = ReaderFlowPaged,
		paged = true,
		direction = ReaderDirectionLtr
	),
	KomikkuReadingModeOption(
		label = "Paged (right to left)",
		flowMode = ReaderFlowPaged,
		paged = true,
		direction = ReaderDirectionRtl
	),
	KomikkuReadingModeOption(
		label = "Paged (vertical)",
		flowMode = ReaderFlowPagedVertical,
		paged = true,
		direction = ReaderDirectionDefault
	),
	KomikkuReadingModeOption(
		label = "Long strip",
		flowMode = ReaderFlowScrolled,
		paged = false,
		direction = ReaderDirectionDefault
	),
	KomikkuReadingModeOption(
		label = "Long strip with gaps",
		flowMode = ReaderFlowScrolledGaps,
		paged = false,
		direction = ReaderDirectionDefault
	)
)

private val KomikkuTapZoneOptions = listOf(
	ReaderTapZoneDefault to "Default",
	ReaderTapZoneLShaped to "L shaped",
	ReaderTapZoneKindle to "Kindle-ish",
	ReaderTapZoneEdge to "Edge",
	ReaderTapZoneRightLeft to "Right and Left",
	ReaderTapZoneDisabled to "Disabled"
)

@Composable
fun ReaderScreen(reader: Screen.Reader) {
	var coordinator by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(ReaderCoordinator())
	}
	val binderyRepository = koinInject<BinderyRepository>()
	val backStack = LocalNavStack.current
	val coroutineScope = rememberCoroutineScope()
	val controllerState = coordinator.controller.state
	val settings = controllerState.chrome.settings
	val navigator = remember(settings.tapZone, settings.smallerTapZone, settings.flowMode) {
		komikkuNavigatorForReaderSettings(settings)
	}

	fun applyCoordinatorStep(step: ReaderCoordinatorStep) {
		applyReaderCoordinatorStep(
			step = step,
			updateCoordinator = { coordinator = it },
			saveProgress = { progress ->
				coroutineScope.launch(Dispatchers.IO) {
					binderyRepository.putReadingProgress(progress).onFailure { error ->
						Logger.w(ReaderScreenTag, "Reader progress save failed", error)
					}
				}
			}
		)
	}

	ReaderPublicationRuntimeHost(
		reader = reader,
		onPublicationReady = { publicationUrl, shellCoverUrl, savedProgress ->
			applyCoordinatorStep(
				coordinator.open(
					reader.toReaderEngineOpenRequest(
						publicationUrl = publicationUrl,
						shellCoverUrl = shellCoverUrl,
						savedProgress = savedProgress,
						settings = settings
					)
				)
			)
		},
		onError = { message ->
			applyCoordinatorStep(
				coordinator.onEngineEvent(
					ReaderEngineEvent.Error(
						message = message,
						code = "publication_runtime"
					)
				)
			)
		}
	)

	ReaderOrientationEffect(orientation = settings.orientation)
	ReaderSystemBarsEffect(
		fullscreen = settings.fullscreen != false,
		systemBarsVisible = controllerState.menuVisible || settings.fullscreen == false
	)

	KomikkuReaderRoot(
		reader = reader,
		controllerState = controllerState,
		viewState = coordinator.viewState,
		navigator = navigator,
		onEngineHostEvent = { event -> applyCoordinatorStep(coordinator.onEngineHostEvent(event)) },
		onViewerAction = { action -> applyCoordinatorStep(coordinator.onViewerAction(action)) },
		onPreviousPage = {
			applyCoordinatorStep(
				coordinator.onViewerAction(
					ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
				)
			)
		},
		onNextPage = {
			applyCoordinatorStep(
				coordinator.onViewerAction(
					ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
				)
			)
		},
		onGoToChapterPage = { pageIndex ->
			applyCoordinatorStep(coordinator.navigateToChapterPage(pageIndex))
		},
		onContents = {
			applyCoordinatorStep(coordinator.openContentsDialog())
		},
		onReadingMode = {
			applyCoordinatorStep(coordinator.openReadingModeDialog())
		},
		onNavigateBack = {
			if (backStack.size > 1) {
				backStack.removeLastOrNull()
			}
		},
		onSettings = {
			applyCoordinatorStep(coordinator.openSettingsDialog())
		},
		onNavigateToTocItem = { tocItem ->
			tocItem.href?.let { href ->
				val navigateStep = coordinator.navigateTo(ReaderLocator(href = href))
				applyCoordinatorStep(navigateStep)
				applyCoordinatorStep(navigateStep.coordinator.closeDialog())
			}
		},
		onToggleCurrentBookmark = {
			applyCoordinatorStep(coordinator.toggleCurrentBookmark())
		},
		onSettingsChange = { settings ->
			applyCoordinatorStep(coordinator.applySettings(settings))
		},
		onDismissDialog = {
			applyCoordinatorStep(coordinator.closeDialog())
		}
	)
}

@Composable
private fun KomikkuReaderRoot(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	viewState: ReaderEngineViewState,
	navigator: KomikkuReaderNavigator,
	onEngineHostEvent: (ReaderEngineHostEvent) -> Unit,
	onViewerAction: (ReaderViewerAction) -> Unit,
	onPreviousPage: () -> Unit,
	onNextPage: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onReadingMode: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onDismissDialog: () -> Unit
) {
	val viewerSlot = remember { ReaderViewerLifecycleSlot() }
	val viewer = remember(viewerSlot, viewState) { viewerSlot.update(viewState) }
	val shellCoverUrl = viewer.shellCoverUrl
	val shellCoverTitle = shellCoverTitleFor(reader, controllerState, viewer)

	DisposableEffect(viewerSlot) {
		onDispose { viewerSlot.dispose() }
	}

	KomikkuReaderNativeFrameHost(
		navigator = navigator,
		navigationOverlayVisible = controllerState.menuVisible && controllerState.chrome.settings.showTapZones == true,
		shellCoverVisible = controllerState.shellCoverVisible,
		shellCoverUrl = shellCoverUrl,
		shellCoverTitle = shellCoverTitle,
		viewerKey = viewer.key,
		onViewerAction = { action ->
			onViewerAction(viewer.viewerActionFor(action))
		},
		modifier = Modifier.fillMaxSize(),
		viewerContent = {
			ReaderViewerHost(
				readerTitle = reader.title,
				controllerState = controllerState,
				engineRenderer = viewer.engineRenderer,
				onEngineHostEvent = onEngineHostEvent,
				modifier = Modifier.fillMaxSize()
			)
		},
		composeOverlay = {
			KomikkuComposeOverlay(
				reader = reader,
				controllerState = controllerState,
				onPreviousPage = onPreviousPage,
				onNextPage = onNextPage,
				onGoToChapterPage = onGoToChapterPage,
				onContents = onContents,
				onReadingMode = onReadingMode,
				onNavigateBack = onNavigateBack,
				onSettings = onSettings,
				onNavigateToTocItem = onNavigateToTocItem,
				onToggleCurrentBookmark = onToggleCurrentBookmark,
				onSettingsChange = onSettingsChange,
				onDismissDialog = onDismissDialog,
				modifier = Modifier.fillMaxSize()
			)
		}
	)
}

private fun shellCoverTitleFor(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	viewer: ReaderViewer
): String =
	viewer.shellCoverTitle
		?: controllerState.publication?.title?.takeIf { it.isNotBlank() }
		?: reader.title

@Composable
private fun KomikkuComposeOverlay(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	onPreviousPage: () -> Unit,
	onNextPage: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onReadingMode: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onDismissDialog: () -> Unit,
	modifier: Modifier = Modifier
) {
	Box(modifier = modifier) {
		KomikkuReaderContentOverlay(
			brightness = -(controllerState.chrome.settings.dimOverlayPercent ?: 0),
			color = null,
			colorBlendMode = null,
			modifier = Modifier.matchParentSize()
		)
		KomikkuReaderAppBars(
			visible = controllerState.menuVisible,
			reader = reader,
			controllerState = controllerState,
			onPreviousPage = onPreviousPage,
			onNextPage = onNextPage,
			onGoToChapterPage = onGoToChapterPage,
			onContents = onContents,
			onReadingMode = onReadingMode,
			onNavigateBack = onNavigateBack,
			onSettings = onSettings,
			onToggleCurrentBookmark = onToggleCurrentBookmark,
			modifier = Modifier.matchParentSize()
		)
		when (controllerState.dialog) {
			ReaderControllerDialog.Contents -> KomikkuReaderContentsDialog(
				toc = controllerState.toc,
				onNavigateTo = onNavigateToTocItem,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.ReadingMode -> KomikkuReaderSettingsDialog(
				settings = controllerState.chrome.settings,
				initialTab = 0,
				onSettingsChange = onSettingsChange,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.Settings -> KomikkuReaderSettingsDialog(
				settings = controllerState.chrome.settings,
				initialTab = 1,
				onSettingsChange = onSettingsChange,
				onDismissRequest = onDismissDialog
			)
			null -> Unit
		}
	}
}

@Composable
private fun KomikkuReaderContentOverlay(
	brightness: Int,
	color: Color?,
	colorBlendMode: BlendMode?,
	modifier: Modifier = Modifier
) {
	// Ported from Komikku ReaderContentOverlay: full-size filter layer independent of content layout.
	if (brightness < 0) {
		Canvas(modifier = modifier) {
			drawRect(Color.Black.copy(alpha = kotlin.math.abs(brightness) / 100f))
		}
	}

	if (color != null) {
		Canvas(modifier = modifier) {
			drawRect(
				color = color,
				blendMode = colorBlendMode ?: BlendMode.SrcOver
			)
		}
	}
}

@Composable
private fun KomikkuReaderAppBars(
	visible: Boolean,
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	onPreviousPage: () -> Unit,
	onNextPage: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onReadingMode: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	modifier: Modifier = Modifier
) {
	// Ported from Komikku ReaderAppBars: all controls are overlays, never content padding.
	val chapterProgress = controllerState.chapterProgress
	val chapterTitle = when {
		controllerState.shellCoverVisible -> "Cover"
		!chapterProgress.title.isNullOrBlank() -> chapterProgress.title
		!controllerState.chrome.currentSectionTitle.isNullOrBlank() -> controllerState.chrome.currentSectionTitle
		else -> controllerState.chrome.progressLabel
	}
	val navBarType = KomikkuNavBarType.VerticalRight
	Column(modifier = modifier.fillMaxHeight()) {
		AnimatedVisibility(
			visible = visible,
			enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeIn(animationSpec = readerBarsFadeAnimationSpec),
			exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeOut(animationSpec = readerBarsFadeAnimationSpec)
		) {
			KomikkuReaderTopBar(
				title = reader.title,
				chapterTitle = chapterTitle,
				bookmarked = controllerState.currentLocationBookmarked,
				canBookmark = controllerState.canBookmarkCurrentLocation,
				onNavigateBack = onNavigateBack,
				onToggleBookmarked = onToggleCurrentBookmark,
				modifier = Modifier.fillMaxWidth()
			)
		}

		when (navBarType) {
			KomikkuNavBarType.VerticalLeft -> {
				AnimatedVisibility(
					visible = visible,
					enter = slideInHorizontally(
						initialOffsetX = { -it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeIn(animationSpec = readerBarsFadeAnimationSpec),
					exit = slideOutHorizontally(
						targetOffsetX = { -it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeOut(animationSpec = readerBarsFadeAnimationSpec),
					modifier = Modifier
						.weight(1f)
						.align(Alignment.Start)
				) {
					KomikkuChapterNavigator(
						isVerticalSlider = true,
						onNextChapter = onNextPage,
						enabledNext = true,
						onPreviousChapter = onPreviousPage,
						enabledPrevious = !controllerState.shellCoverVisible,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
			}
			KomikkuNavBarType.VerticalRight -> {
				AnimatedVisibility(
					visible = visible,
					enter = slideInHorizontally(
						initialOffsetX = { it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeIn(animationSpec = readerBarsFadeAnimationSpec),
					exit = slideOutHorizontally(
						targetOffsetX = { it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeOut(animationSpec = readerBarsFadeAnimationSpec),
					modifier = Modifier
						.weight(1f)
						.align(Alignment.End)
				) {
					KomikkuChapterNavigator(
						isVerticalSlider = true,
						onNextChapter = onNextPage,
						enabledNext = true,
						onPreviousChapter = onPreviousPage,
						enabledPrevious = !controllerState.shellCoverVisible,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
			}
			KomikkuNavBarType.Bottom -> {
				Spacer(modifier = Modifier.weight(1f))
			}
		}

		AnimatedVisibility(
			visible = visible,
			enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeIn(animationSpec = readerBarsFadeAnimationSpec),
			exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeOut(animationSpec = readerBarsFadeAnimationSpec)
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (navBarType == KomikkuNavBarType.Bottom) {
					KomikkuChapterNavigator(
						isVerticalSlider = false,
						onNextChapter = onNextPage,
						enabledNext = true,
						onPreviousChapter = onPreviousPage,
						enabledPrevious = !controllerState.shellCoverVisible,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
				KomikkuReaderBottomBar(
					onContents = onContents,
					onReadingMode = onReadingMode,
					onSettings = onSettings,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
	}
}

@Composable
private fun KomikkuReaderTopBar(
	title: String,
	chapterTitle: String,
	bookmarked: Boolean,
	canBookmark: Boolean,
	onNavigateBack: () -> Unit,
	onToggleBookmarked: () -> Unit,
	modifier: Modifier = Modifier
) {
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)

	Surface(
		color = backgroundColor,
		contentColor = MaterialTheme.colorScheme.onSurface,
		modifier = modifier
			.pointerInput(Unit) {}
	) {
		Row(
			modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp)
		) {
			IconButton(onClick = onNavigateBack) {
				Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
			}
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.headlineSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = chapterTitle,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			IconButton(
				enabled = canBookmark,
				onClick = onToggleBookmarked
			) {
				Icon(
					if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
					contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark"
				)
			}
		}
	}
}

@Composable
private fun KomikkuReaderBottomBar(
	onContents: () -> Unit,
	onReadingMode: () -> Unit,
	onSettings: () -> Unit,
	modifier: Modifier = Modifier
) {
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)
	val iconColor = MaterialTheme.colorScheme.primary

	Surface(
		color = backgroundColor,
		contentColor = iconColor,
		modifier = modifier
			.pointerInput(Unit) {}
	) {
		// Ported from Komikku ReaderBottomBar: centered, evenly distributed actions.
		Row(
			modifier = Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.SpaceEvenly,
			verticalAlignment = Alignment.CenterVertically
		) {
			IconButton(onClick = onContents) {
				Icon(Icons.Outlined.List, contentDescription = "Contents", tint = iconColor)
			}
			IconButton(onClick = onReadingMode) {
				Icon(Icons.Outlined.Book, contentDescription = "Reading mode", tint = iconColor)
			}
			IconButton(onClick = onSettings) {
				Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = iconColor)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KomikkuReaderContentsDialog(
	toc: List<ReaderTocItem>,
	onNavigateTo: (ReaderTocItem) -> Unit,
	onDismissRequest: () -> Unit
) {
	BasicAlertDialog(onDismissRequest = onDismissRequest) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.78f)
		) {
			Column(
				modifier = Modifier
					.heightIn(max = 520.dp)
					.padding(horizontal = 24.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Text(
					text = "Contents",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				Column(
					modifier = Modifier
						.weight(1f, fill = false)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					if (toc.isEmpty()) {
						KomikkuSettingsDialogLine("No table of contents available")
					} else {
						toc.forEach { item ->
							Text(
								text = item.title,
								style = MaterialTheme.typography.bodyLarge,
								color = if (item.href.isNullOrBlank()) {
									MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
								} else {
									MaterialTheme.colorScheme.onSurface
								},
								modifier = Modifier
									.fillMaxWidth()
									.clip(RoundedCornerShape(14.dp))
									.clickable(enabled = !item.href.isNullOrBlank()) {
										onNavigateTo(item)
									}
									.padding(
										start = (item.level.coerceAtLeast(0) * 16).dp,
										top = 10.dp,
										end = 10.dp,
										bottom = 10.dp
									)
							)
						}
					}
				}
				TextButton(
					onClick = onDismissRequest,
					modifier = Modifier.align(Alignment.End)
				) {
					Text("Close")
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KomikkuReaderSettingsDialog(
	settings: ReaderSettings,
	initialTab: Int,
	onSettingsChange: (ReaderSettings) -> Unit,
	onDismissRequest: () -> Unit
) {
	// Ported from Komikku ReaderSettingsDialog: tabbed overlay above content, never a docked panel.
	val tabs = listOf("Reading mode", "General", "Custom filter")
	var selectedTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(tabs.indices)) }

	BasicAlertDialog(onDismissRequest = onDismissRequest) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.78f)
		) {
			Column(
				modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.spacedBy(18.dp)
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					tabs.forEachIndexed { index, title ->
						Text(
							text = title,
							style = MaterialTheme.typography.titleMedium,
							fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.SemiBold,
							color = if (selectedTab == index) {
								MaterialTheme.colorScheme.primary
							} else {
								MaterialTheme.colorScheme.onSurface
							},
							modifier = Modifier
								.clip(RoundedCornerShape(18.dp))
								.clickable { selectedTab = index }
								.padding(horizontal = 10.dp, vertical = 8.dp)
						)
					}
				}
				when (selectedTab) {
					0 -> KomikkuSettingsDialogPage(
						title = "For this book"
					) {
						KomikkuSettingsReadingModeRow(
							settings = settings,
							onSelect = { option ->
								onSettingsChange(settings.copy(
									flowMode = option.flowMode,
									paged = option.paged,
									direction = option.direction
								))
							}
						)
						KomikkuSettingsChipRow(
							title = "Tap zones",
							options = KomikkuTapZoneOptions,
							selectedValue = normalizedReaderTapZone(settings.tapZone),
							onSelect = { tapZone ->
								onSettingsChange(settings.copy(tapZone = tapZone))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Smaller tap zones",
							checked = settings.smallerTapZone == true,
							onCheckedChange = { smallerTapZone ->
								onSettingsChange(settings.copy(smallerTapZone = smallerTapZone))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Show tap zones",
							checked = settings.showTapZones == true,
							onCheckedChange = { showTapZones ->
								onSettingsChange(settings.copy(showTapZones = showTapZones))
							}
						)
					}
					1 -> KomikkuSettingsDialogPage(
						title = "General"
					) {
						KomikkuSettingsDialogLine("Font: ${settings.fontFamily ?: "Default"}")
						KomikkuSettingsDialogLine("Font size: ${settings.fontSizePercent ?: 100}%")
						KomikkuSettingsDialogLine("Theme: ${settings.theme ?: "Default"}")
					}
					else -> KomikkuSettingsDialogPage(
						title = "Custom filter"
					) {
						KomikkuSettingsDialogLine("Dim overlay: ${settings.dimOverlayPercent ?: 0}%")
						KomikkuSettingsDialogLine("Publisher styles: ${if (settings.publisherStyles == false) "Off" else "On"}")
					}
				}
				TextButton(
					onClick = onDismissRequest,
					modifier = Modifier.align(Alignment.End)
				) {
					Text("Close")
				}
			}
		}
	}
}

@Composable
private fun KomikkuSettingsDialogPage(
	title: String,
	content: @Composable () -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold
		)
		content()
	}
}

@Composable
private fun KomikkuSettingsDialogLine(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyLarge,
		color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
	)
}

@Composable
private fun KomikkuSettingsReadingModeRow(
	settings: ReaderSettings,
	onSelect: (KomikkuReadingModeOption) -> Unit
) {
	val selectedOption = komikkuReadingModeOptionFor(settings)
	KomikkuSettingsChipRow(
		title = "Reading mode",
		options = KomikkuReadingModeOptions.map { option -> option.label to option.label },
		selectedValue = selectedOption.label,
		onSelect = { selectedLabel ->
			KomikkuReadingModeOptions
				.firstOrNull { option -> option.label == selectedLabel }
				?.let(onSelect)
		}
	)
}

private fun komikkuReadingModeOptionFor(settings: ReaderSettings): KomikkuReadingModeOption {
	val flowMode = normalizedReaderFlowMode(settings.flowMode, settings.paged)
	val direction = normalizedReaderDirection(settings.direction)
	return when {
		flowMode == ReaderFlowPaged && direction == ReaderDirectionLtr ->
			KomikkuReadingModeOptions[1]
		flowMode == ReaderFlowPaged && direction == ReaderDirectionRtl ->
			KomikkuReadingModeOptions[2]
		flowMode == ReaderFlowPagedVertical ->
			KomikkuReadingModeOptions[3]
		flowMode == ReaderFlowScrolled ->
			KomikkuReadingModeOptions[4]
		flowMode == ReaderFlowScrolledGaps ->
			KomikkuReadingModeOptions[5]
		else -> KomikkuReadingModeOptions[0]
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KomikkuSettingsChipRow(
	title: String,
	options: List<Pair<String, String>>,
	selectedValue: String,
	onSelect: (String) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurface
		)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			options.forEach { (value, label) ->
				FilterChip(
					selected = selectedValue == value,
					onClick = { onSelect(value) },
					label = { Text(label) }
				)
			}
		}
	}
}

@Composable
private fun KomikkuSettingsSwitchRow(
	title: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(18.dp))
			.clickable { onCheckedChange(!checked) }
			.padding(horizontal = 12.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface
		)
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange
		)
	}
}

@Composable
private fun KomikkuChapterNavigator(
	isVerticalSlider: Boolean,
	onNextChapter: () -> Unit,
	enabledNext: Boolean,
	onPreviousChapter: () -> Unit,
	enabledPrevious: Boolean,
	currentPage: Int,
	currentPageText: String,
	totalPages: Int,
	onPageIndexChange: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	if (isVerticalSlider) {
		KomikkuChapterNavigatorVertical(
			onNextChapter = onNextChapter,
			enabledNext = enabledNext,
			onPreviousChapter = onPreviousChapter,
			enabledPrevious = enabledPrevious,
			currentPage = currentPage,
			currentPageText = currentPageText,
			totalPages = totalPages,
			onPageIndexChange = onPageIndexChange,
			modifier = modifier
		)
		return
	}

	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)
	val buttonColor = IconButtonDefaults.filledIconButtonColors(
		containerColor = backgroundColor,
		disabledContainerColor = backgroundColor,
		contentColor = MaterialTheme.colorScheme.primary
	)

	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		FilledIconButton(
			enabled = enabledPrevious,
			onClick = onPreviousChapter,
			colors = buttonColor
		) {
			Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
		}

		if (totalPages > 1) {
			Row(
				modifier = Modifier
					.weight(1f)
					.clip(RoundedCornerShape(24.dp))
					.background(backgroundColor)
					.padding(horizontal = 16.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Box(contentAlignment = Alignment.CenterEnd) {
					Text(text = currentPageText)
					Text(text = totalPages.toString(), color = Color.Transparent)
				}
				Slider(
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 8.dp),
					value = currentPage.toFloat(),
					valueRange = 1f..totalPages.toFloat(),
					steps = (totalPages - 2).coerceAtLeast(0),
					onValueChange = { value ->
						if (value.toInt() != currentPage) {
							onPageIndexChange(value.toInt() - 1)
						}
					}
				)
				Text(text = totalPages.toString())
			}
		} else {
			Spacer(Modifier.weight(1f))
		}

		FilledIconButton(
			enabled = enabledNext,
			onClick = onNextChapter,
			colors = buttonColor
		) {
			Icon(Icons.Filled.SkipNext, contentDescription = "Next")
		}
	}
}

@Composable
private fun KomikkuChapterNavigatorVertical(
	onNextChapter: () -> Unit,
	enabledNext: Boolean,
	onPreviousChapter: () -> Unit,
	enabledPrevious: Boolean,
	currentPage: Int,
	currentPageText: String,
	totalPages: Int,
	onPageIndexChange: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)
	val buttonColor = IconButtonDefaults.filledIconButtonColors(
		containerColor = backgroundColor,
		disabledContainerColor = backgroundColor,
		contentColor = MaterialTheme.colorScheme.primary
	)

	Column(
		modifier = modifier
			.fillMaxHeight()
			.padding(vertical = 8.dp, horizontal = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		FilledIconButton(
			enabled = enabledPrevious,
			onClick = onPreviousChapter,
			colors = buttonColor
		) {
			Icon(
				Icons.Filled.SkipPrevious,
				contentDescription = "Previous",
				modifier = Modifier.rotate(90f)
			)
		}

		if (totalPages > 1) {
			Column(
				modifier = Modifier
					.weight(1f)
					.clip(RoundedCornerShape(24.dp))
					.background(backgroundColor)
					.padding(vertical = 16.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(text = currentPageText)
				Slider(
					modifier = Modifier
						.padding(vertical = 8.dp)
						.graphicsLayer {
							rotationZ = 90f
							transformOrigin = TransformOrigin(0f, 0f)
						}
						.layout { measurable, constraints ->
							val placeable = measurable.measure(
								Constraints(
									minWidth = constraints.minHeight,
									maxWidth = constraints.maxHeight,
									minHeight = constraints.minWidth,
									maxHeight = constraints.maxWidth
								)
							)
							layout(placeable.height, placeable.width) {
								placeable.place(0, -placeable.height)
							}
						}
						.weight(1f),
					value = currentPage.toFloat(),
					valueRange = 1f..totalPages.toFloat(),
					steps = (totalPages - 2).coerceAtLeast(0),
					onValueChange = { value ->
						if (value.toInt() != currentPage) {
							onPageIndexChange(value.toInt() - 1)
						}
					}
				)
				Text(text = totalPages.toString())
			}
		} else {
			Spacer(Modifier.weight(1f))
		}

		FilledIconButton(
			enabled = enabledNext,
			onClick = onNextChapter,
			colors = buttonColor
		) {
			Icon(
				Icons.Filled.SkipNext,
				contentDescription = "Next",
				modifier = Modifier.rotate(90f)
			)
		}
	}
}

internal fun Screen.Reader.toReaderEngineOpenRequest(
	publicationUrl: String,
	shellCoverUrl: String?,
	settings: ReaderSettings,
	savedProgress: BinderyReadingProgress? = null
): ReaderEngineOpenRequest {
	val hasShellCover = !shellCoverUrl.isNullOrBlank()
	val routeStartLocator = ReaderLocator(
		cfi = startCfi,
		href = startHref
	).takeIf { locator -> locator.cfi != null || locator.href != null }
	val savedStartLocator = savedProgress?.toReaderStartLocatorForReader(
		bookId = bookId,
		resourceHref = resourceHref,
		kind = kind
	)
	return ReaderEngineOpenRequest(
		publication = ReaderPublicationIdentity(
			bookId = bookId,
			title = title,
			resourceHref = resourceHref,
			kind = kind,
			format = publicationFormat
		),
		url = publicationUrl,
		mediaOverlayEnabled = mediaOverlayEnabled,
		externalShellCover = hasShellCover,
		startLocator = bestReaderStartLocator(
			remoteStartLocator = routeStartLocator,
			localStartLocator = savedStartLocator
		),
		settings = settings,
		nativeShellCoverUrl = shellCoverUrl,
		canReturnToShellCover = hasShellCover
	)
}

private fun komikkuNavigatorForReaderSettings(settings: ReaderSettings): KomikkuReaderNavigator {
	val smallerTapZone = settings.smallerTapZone == true
	val tapZone = normalizedReaderTapZone(settings.tapZone).let { normalized ->
		if (normalized == ReaderTapZoneDefault) {
			readerDefaultTapZoneMode(settings.flowMode)
		} else {
			normalized
		}
	}
	val navigation = when (tapZone) {
		ReaderTapZoneLShaped -> KomikkuLNavigation(smallerTapZone)
		ReaderTapZoneKindle -> KomikkuKindlishNavigation(smallerTapZone)
		ReaderTapZoneEdge -> KomikkuEdgeNavigation(smallerTapZone)
		ReaderTapZoneRightLeft -> KomikkuRightAndLeftNavigation(smallerTapZone)
		ReaderTapZoneDisabled -> KomikkuDisabledNavigation(smallerTapZone)
		else -> KomikkuRightAndLeftNavigation(smallerTapZone)
	}
	return KomikkuReaderNavigator(navigation)
}
