package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
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
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderChromeState
import paige.navic.reader.ReaderController
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
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderSupportedDirections
import paige.navic.reader.ReaderSupportedFontFamilies
import paige.navic.reader.ReaderSupportedFontSources
import paige.navic.reader.ReaderSupportedOrientations
import paige.navic.reader.ReaderSupportedPdfFitModes
import paige.navic.reader.ReaderSupportedSettingsScopes
import paige.navic.reader.ReaderSupportedThemes
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
import paige.navic.reader.clearReaderBookSettings
import paige.navic.reader.normalizedReaderDirection
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.normalizedReaderFontFamily
import paige.navic.reader.normalizedReaderFontSource
import paige.navic.reader.normalizedReaderOrientation
import paige.navic.reader.normalizedReaderPdfFitMode
import paige.navic.reader.normalizedReaderSettings
import paige.navic.reader.normalizedReaderTapZone
import paige.navic.reader.normalizedReaderTheme
import paige.navic.reader.readerDefaultSettings
import paige.navic.reader.readerDefaultTapZoneMode
import paige.navic.reader.readerDirectionShortLabel
import paige.navic.reader.readerFontFamilyShortLabel
import paige.navic.reader.readerFontSourceShortLabel
import paige.navic.reader.readerOrientationShortLabel
import paige.navic.reader.readerPdfFitShortLabel
import paige.navic.reader.readerSettingsForBook
import paige.navic.reader.readerSettingsScopeLabel
import paige.navic.reader.readerThemeShortLabel
import paige.navic.reader.readerBookSettings
import paige.navic.reader.setReaderBookSettings
import paige.navic.reader.setReaderDefaultSettings
import paige.navic.reader.toReaderStartLocatorForReader
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

private const val ReaderScreenTag = "ReaderScreen"
private const val KomikkuReaderVerticalRailHeightFraction = 0.68f
private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

private enum class KomikkuNavBarType {
	VerticalRight,
	VerticalLeft,
	Bottom
}

private enum class KomikkuSettingsTab(val label: String) {
	Reading("Reading mode"),
	General("General"),
	PdfImage("PDF/Image"),
	CustomFilter("Custom filter")
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

private fun komikkuSettingsTabs(publicationFormat: ReaderPublicationFormat): List<KomikkuSettingsTab> =
	if (publicationFormat == ReaderPublicationFormat.Pdf) {
		listOf(
			KomikkuSettingsTab.Reading,
			KomikkuSettingsTab.General,
			KomikkuSettingsTab.PdfImage,
			KomikkuSettingsTab.CustomFilter
		)
	} else {
		listOf(
			KomikkuSettingsTab.Reading,
			KomikkuSettingsTab.General,
			KomikkuSettingsTab.CustomFilter
		)
	}

@Composable
fun ReaderScreen(reader: Screen.Reader) {
	val preferenceManager = koinInject<PreferenceManager>()
	val binderyRepository = koinInject<BinderyRepository>()
	val backStack = LocalNavStack.current
	val coroutineScope = rememberCoroutineScope()
	val hasReaderBookSettings = preferenceManager.readerBookSettings(reader.bookId) != null
	var readerSettingsScope by remember(reader.publicationUrl, reader.bookId) {
		mutableStateOf(
			if (hasReaderBookSettings) {
				ReaderSettingsScope.Book
			} else {
				ReaderSettingsScope.Global
			}
		)
	}
	val defaultReaderSettings = remember(
		reader.publicationUrl,
		reader.bookId,
		readerSettingsScope,
		preferenceManager.readerFontFamily,
		preferenceManager.readerFontSource,
		preferenceManager.readerCustomFontFamily,
		preferenceManager.readerCustomFontUrl,
		preferenceManager.readerFontSizePercent,
		preferenceManager.readerLineHeightPercent,
		preferenceManager.readerParagraphSpacingPercent,
		preferenceManager.readerMarginPercent,
		preferenceManager.readerDimOverlayPercent,
		preferenceManager.readerOrientation,
		preferenceManager.readerTheme,
		preferenceManager.readerDirection,
		preferenceManager.readerFlowMode,
		preferenceManager.readerPaged,
		preferenceManager.readerTapZone,
		preferenceManager.readerSmallerTapZone,
		preferenceManager.readerShowTapZones,
		preferenceManager.readerPublisherStylesEnabled,
		preferenceManager.readerFullscreen,
		preferenceManager.readerKeepScreenOn,
		preferenceManager.readerReadaloudSyncEnabled,
		preferenceManager.readerVolumeKeyPageTurns,
		preferenceManager.readerWebContentsDebuggingEnabled,
		preferenceManager.readerBookSettingsJson
	) {
		if (readerSettingsScope == ReaderSettingsScope.Book) {
			preferenceManager.readerSettingsForBook(reader.bookId)
		} else {
			preferenceManager.readerDefaultSettings()
		}
	}
	var coordinator by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(
			ReaderCoordinator(
				controller = ReaderController(
					state = ReaderControllerState(
						chrome = ReaderChromeState(settings = defaultReaderSettings)
					)
				)
			)
		)
	}
	val controllerState = coordinator.controller.state
	val settings = controllerState.chrome.settings
	val readerFocusRequester = remember { FocusRequester() }
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

	fun persistReaderSettings(nextSettings: ReaderSettings) {
		val normalized = nextSettings.normalizedReaderSettings()
		if (readerSettingsScope == ReaderSettingsScope.Book) {
			preferenceManager.setReaderBookSettings(reader.bookId, normalized)
		} else {
			preferenceManager.setReaderDefaultSettings(normalized)
		}
	}

	fun applyReaderSettings(nextSettings: ReaderSettings) {
		val normalized = nextSettings.normalizedReaderSettings()
		persistReaderSettings(normalized)
		applyCoordinatorStep(coordinator.applySettings(normalized))
	}

	fun selectReaderSettingsScope(scope: ReaderSettingsScope) {
		readerSettingsScope = scope
		val nextSettings = when (scope) {
			ReaderSettingsScope.Global -> preferenceManager.readerDefaultSettings()
			ReaderSettingsScope.Book -> {
				if (!hasReaderBookSettings) {
					preferenceManager.setReaderBookSettings(reader.bookId, settings)
				}
				preferenceManager.readerSettingsForBook(reader.bookId)
			}
		}
		applyCoordinatorStep(coordinator.applySettings(nextSettings))
	}

	fun resetReaderBookSettings() {
		preferenceManager.clearReaderBookSettings(reader.bookId)
		readerSettingsScope = ReaderSettingsScope.Global
		applyCoordinatorStep(coordinator.applySettings(preferenceManager.readerDefaultSettings()))
	}

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		readerFocusRequester.requestFocus()
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
		settingsScope = readerSettingsScope,
		hasBookSettings = hasReaderBookSettings,
		publicationFormat = reader.publicationFormat,
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
			applyReaderSettings(settings)
		},
		onSettingsScopeChange = { scope ->
			selectReaderSettingsScope(scope)
		},
		onResetBookSettings = {
			resetReaderBookSettings()
		},
		onDismissDialog = {
			applyCoordinatorStep(coordinator.closeDialog())
		},
		modifier = Modifier
			.fillMaxSize()
			.focusRequester(readerFocusRequester)
			.focusable()
			.onPreviewKeyEvent { event ->
				if (settings.volumeKeyPageTurns != true || event.type != KeyEventType.KeyDown) {
					return@onPreviewKeyEvent false
				}
				when (event.key) {
					Key.VolumeUp -> {
						applyCoordinatorStep(
							coordinator.onViewerAction(
								ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
							)
						)
						true
					}
					Key.VolumeDown -> {
						applyCoordinatorStep(
							coordinator.onViewerAction(
								ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
							)
						)
						true
					}
					else -> false
				}
			}
	)
}

@Composable
private fun KomikkuReaderRoot(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	viewState: ReaderEngineViewState,
	navigator: KomikkuReaderNavigator,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
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
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onDismissDialog: () -> Unit,
	modifier: Modifier = Modifier
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
		onContentLongPress = { x, y, width, height ->
			onViewerAction(
				ReaderViewerAction.ContentLongPressAt(
					x = x.toDouble(),
					y = y.toDouble(),
					viewWidth = width.toDouble(),
					viewHeight = height.toDouble()
				)
			)
		},
		modifier = modifier,
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
				settingsScope = settingsScope,
				hasBookSettings = hasBookSettings,
				publicationFormat = publicationFormat,
				onNavigateToTocItem = onNavigateToTocItem,
				onToggleCurrentBookmark = onToggleCurrentBookmark,
				onSettingsChange = onSettingsChange,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
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
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
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
				settingsScope = settingsScope,
				hasBookSettings = hasBookSettings,
				publicationFormat = publicationFormat,
				onSettingsChange = onSettingsChange,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.Settings -> KomikkuReaderSettingsDialog(
				settings = controllerState.chrome.settings,
				initialTab = 1,
				settingsScope = settingsScope,
				hasBookSettings = hasBookSettings,
				publicationFormat = publicationFormat,
				onSettingsChange = onSettingsChange,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
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
					Box(
						modifier = Modifier.fillMaxHeight(),
						contentAlignment = Alignment.CenterStart
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
							},
							modifier = Modifier.fillMaxHeight(KomikkuReaderVerticalRailHeightFraction)
						)
					}
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
					Box(
						modifier = Modifier.fillMaxHeight(),
						contentAlignment = Alignment.CenterEnd
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
							},
							modifier = Modifier.fillMaxHeight(KomikkuReaderVerticalRailHeightFraction)
						)
					}
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
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onDismissRequest: () -> Unit
) {
	// Ported from Komikku ReaderSettingsDialog: tabbed overlay above content, never a docked panel.
	val tabs = komikkuSettingsTabs(publicationFormat)
	val pagerState = rememberPagerState(
		initialPage = initialTab.coerceIn(tabs.indices),
		pageCount = { tabs.size }
	)
	val scope = rememberCoroutineScope()
	val chromeState = ReaderChromeState(settings = settings)

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
				KomikkuSettingsTabRow(
					tabs = tabs,
					selectedTab = pagerState.currentPage,
					onSelectTab = { index ->
						scope.launch { pagerState.animateScrollToPage(index) }
					}
				)
				HorizontalPager(
					modifier = Modifier.animateContentSize(),
					state = pagerState,
					verticalAlignment = Alignment.Top
				) { page ->
				when (tabs[page]) {
					KomikkuSettingsTab.Reading -> KomikkuSettingsDialogPage(
						title = "For this book"
					) {
						KomikkuSettingsChipRow(
							title = "Scope",
							options = ReaderSupportedSettingsScopes.map { scope -> scope.name to readerSettingsScopeLabel(scope) },
							selectedValue = settingsScope.name,
							onSelect = { scopeName ->
								ReaderSettingsScope.entries
									.firstOrNull { scope -> scope.name == scopeName }
									?.let(onSettingsScopeChange)
							}
						)
						if (hasBookSettings) {
							TextButton(onClick = onResetBookSettings) {
								Text("Reset book")
							}
						}
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
							title = "Direction",
							options = ReaderSupportedDirections.map { direction -> direction to readerDirectionShortLabel(direction) },
							selectedValue = normalizedReaderDirection(settings.direction),
							onSelect = { direction ->
								onSettingsChange(settings.copy(direction = direction))
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
					KomikkuSettingsTab.General -> KomikkuSettingsDialogPage(
						title = "General"
					) {
						KomikkuSettingsChipRow(
							title = "Font",
							options = ReaderSupportedFontFamilies.map { fontFamily ->
								fontFamily to readerFontFamilyShortLabel(fontFamily)
							},
							selectedValue = normalizedReaderFontFamily(settings.fontFamily),
							onSelect = { fontFamily ->
								onSettingsChange(settings.copy(fontFamily = fontFamily))
							}
						)
						KomikkuSettingsChipRow(
							title = "Font source",
							options = ReaderSupportedFontSources.map { fontSource ->
								fontSource to readerFontSourceShortLabel(fontSource)
							},
							selectedValue = normalizedReaderFontSource(settings.fontSource),
							onSelect = { fontSource ->
								onSettingsChange(settings.copy(fontSource = fontSource))
							}
						)
						KomikkuSettingsStepperRow(
							title = "Font size",
							value = "${settings.fontSizePercent ?: 100}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustFontSize(-8).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustFontSize(8).settings)
							}
						)
						KomikkuSettingsStepperRow(
							title = "Line height",
							value = "${settings.lineHeight ?: 1.55}",
							onDecrease = {
								onSettingsChange(chromeState.adjustLineHeight(-0.1).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustLineHeight(0.1).settings)
							}
						)
						KomikkuSettingsStepperRow(
							title = "Paragraph spacing",
							value = "${settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustParagraphSpacing(-25).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustParagraphSpacing(25).settings)
							}
						)
						KomikkuSettingsStepperRow(
							title = "Margins",
							value = "${settings.marginPercent ?: 0}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustMargin(-4).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustMargin(4).settings)
							}
						)
						KomikkuSettingsChipRow(
							title = "Theme",
							options = ReaderSupportedThemes.map { theme -> theme to readerThemeShortLabel(theme) },
							selectedValue = normalizedReaderTheme(settings.theme),
							onSelect = { theme ->
								onSettingsChange(settings.copy(theme = theme))
							}
						)
						KomikkuSettingsChipRow(
							title = "Rotation",
							options = ReaderSupportedOrientations.map { orientation ->
								orientation to readerOrientationShortLabel(orientation)
							},
							selectedValue = normalizedReaderOrientation(settings.orientation),
							onSelect = { orientation ->
								onSettingsChange(settings.copy(orientation = orientation))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Fullscreen",
							checked = settings.fullscreen == true,
							onCheckedChange = { fullscreen ->
								onSettingsChange(settings.copy(fullscreen = fullscreen))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Keep screen on",
							checked = settings.keepScreenOn == true,
							onCheckedChange = { keepScreenOn ->
								onSettingsChange(settings.copy(keepScreenOn = keepScreenOn))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Volume keys",
							checked = settings.volumeKeyPageTurns == true,
							onCheckedChange = { volumeKeyPageTurns ->
								onSettingsChange(settings.copy(volumeKeyPageTurns = volumeKeyPageTurns))
							}
						)
					}
					KomikkuSettingsTab.PdfImage -> KomikkuSettingsDialogPage(
						title = "PDF/Image"
					) {
						KomikkuSettingsChipRow(
							title = "Page fit",
							options = ReaderSupportedPdfFitModes.map { fitMode ->
								fitMode to readerPdfFitShortLabel(fitMode)
							},
							selectedValue = normalizedReaderPdfFitMode(settings.pdfFitMode),
							onSelect = { fitMode ->
								onSettingsChange(settings.copy(pdfFitMode = fitMode))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Crop borders",
							checked = settings.pdfCropBorders == true,
							onCheckedChange = { cropBorders ->
								onSettingsChange(settings.copy(pdfCropBorders = cropBorders))
							}
						)
						KomikkuSettingsStepperRow(
							title = "Page gap",
							value = "${settings.pdfPageGapPercent ?: 0}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustPdfPageGap(-4).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustPdfPageGap(4).settings)
							}
						)
					}
					KomikkuSettingsTab.CustomFilter -> KomikkuSettingsDialogPage(
						title = "Custom filter"
					) {
						KomikkuSettingsStepperRow(
							title = "Dim overlay",
							value = "${settings.dimOverlayPercent ?: 0}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustDimOverlay(-10).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustDimOverlay(10).settings)
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Publisher styles",
							checked = settings.publisherStyles == true,
							onCheckedChange = { publisherStyles ->
								onSettingsChange(settings.copy(publisherStyles = publisherStyles))
							}
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

@Composable
private fun KomikkuSettingsTabRow(
	tabs: List<KomikkuSettingsTab>,
	selectedTab: Int,
	onSelectTab: (Int) -> Unit
) {
	PrimaryTabRow(
		selectedTabIndex = selectedTab,
		containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
		divider = {},
		modifier = Modifier.fillMaxWidth()
	) {
		tabs.forEachIndexed { index, tab ->
			Tab(
				selected = selectedTab == index,
				onClick = { onSelectTab(index) },
				text = {
					Text(
						text = tab.label,
						style = MaterialTheme.typography.labelLarge,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				},
				unselectedContentColor = MaterialTheme.colorScheme.onSurface
			)
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
private fun KomikkuSettingsStepperRow(
	title: String,
	value: String,
	onDecrease: () -> Unit,
	onIncrease: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		IconButton(onClick = onDecrease) {
			Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		}
		Text(
			text = value,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
			maxLines = 1
		)
		IconButton(onClick = onIncrease) {
			Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		}
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
				val haptic = LocalHapticFeedback.current
				val interactionSource = remember { MutableInteractionSource() }
				val sliderDragged by interactionSource.collectIsDraggedAsState()
				LaunchedEffect(currentPage) {
					if (sliderDragged) {
						haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					}
				}
				KomikkuChapterProgressSlider(
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 8.dp),
					value = currentPage,
					valueRange = 1..totalPages,
					onValueChange = { page ->
						if (page != currentPage) {
							onPageIndexChange(page - 1)
						}
					},
					interactionSource = interactionSource
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
private fun KomikkuChapterProgressSlider(
	value: Int,
	valueRange: IntProgression,
	onValueChange: (Int) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
	Slider(
		value = value.toFloat(),
		onValueChange = { changedValue -> onValueChange(changedValue.roundToInt()) },
		modifier = modifier,
		enabled = enabled,
		valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
		steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
		interactionSource = interactionSource
	)
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
				val haptic = LocalHapticFeedback.current
				val interactionSource = remember { MutableInteractionSource() }
				val sliderDragged by interactionSource.collectIsDraggedAsState()
				LaunchedEffect(currentPage) {
					if (sliderDragged) {
						haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					}
				}
				KomikkuChapterProgressSlider(
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
					value = currentPage,
					valueRange = 1..totalPages,
					onValueChange = { page ->
						if (page != currentPage) {
							onPageIndexChange(page - 1)
						}
					},
					interactionSource = interactionSource
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
