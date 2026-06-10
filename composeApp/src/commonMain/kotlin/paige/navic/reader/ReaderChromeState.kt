package paige.navic.reader

import kotlin.math.roundToInt

private const val MinReaderFontSizePercent = 80
private const val MaxReaderFontSizePercent = 180
private const val DefaultReaderFontSizePercent = 100
private const val MinReaderLineHeight = 1.2
private const val MaxReaderLineHeight = 2.2
private const val DefaultReaderLineHeight = 1.55
private const val MinReaderParagraphSpacingPercent = 0
private const val MaxReaderParagraphSpacingPercent = 200
const val DefaultReaderParagraphSpacingPercent = 100
const val LegacyReaderParagraphSpacingPercent = 0
private const val MinReaderMarginPercent = 0
private const val MaxReaderMarginPercent = 24
private const val DefaultReaderMarginPercent = 0
private const val MinReaderDimOverlayPercent = 0
private const val MaxReaderDimOverlayPercent = 80
private const val DefaultReaderDimOverlayPercent = 0
const val ReaderSansFontFamily = "system-ui, sans-serif"
const val ReaderSerifFontFamily = "Georgia, serif"
const val ReaderBookFontFamily = "\"Navic Literata\", Literata, Bookerly, Georgia, serif"
const val ReaderHumanistFontFamily = "\"Navic Atkinson Hyperlegible\", \"Atkinson Hyperlegible\", Lexend, system-ui, sans-serif"
const val ReaderDyslexicFontFamily = "\"Navic OpenDyslexic\", OpenDyslexic, \"Navic Atkinson Hyperlegible\", system-ui, sans-serif"
const val ReaderMonoFontFamily = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
const val ReaderPublisherFontFamily = "inherit"
private const val LegacyReaderBookFontFamily = "Literata, Bookerly, Georgia, serif"
private const val LegacyReaderHumanistFontFamily = "Atkinson Hyperlegible, Lexend, system-ui, sans-serif"
private const val LegacyReaderDyslexicFontFamily = "OpenDyslexic, Atkinson Hyperlegible, Lexend, system-ui, sans-serif"
const val ReaderFontSourceNavic = "navic"
const val ReaderFontSourceSystem = "system"
const val ReaderFontSourcePublisher = "publisher"
const val ReaderLightTheme = "light"
const val ReaderSepiaTheme = "sepia"
const val ReaderDuskTheme = "dusk"
const val ReaderDarkTheme = "dark"
const val ReaderBlackTheme = "black"
const val ReaderDirectionDefault = "default"
const val ReaderDirectionLtr = "ltr"
const val ReaderDirectionRtl = "rtl"
const val ReaderFlowPaged = "paged"
const val ReaderFlowPagedVertical = "paged-vertical"
const val ReaderFlowScrolled = "scrolled"
const val ReaderFlowScrolledGaps = "scrolled-gaps"
const val ReaderTapZoneDefault = "default"
const val ReaderTapZoneEdge = "edge"
const val ReaderTapZoneKindle = "kindle"
const val ReaderTapZoneLShaped = "l-shaped"
const val ReaderTapZoneRightLeft = "right-left"
const val ReaderTapZoneDisabled = "disabled"
const val ReaderOrientationDefault = "default"
const val ReaderOrientationFree = "free"
const val ReaderOrientationPortrait = "portrait"
const val ReaderOrientationLandscape = "landscape"
const val ReaderOrientationLockedPortrait = "locked-portrait"
const val ReaderOrientationLockedLandscape = "locked-landscape"
const val ReaderOrientationReversePortrait = "reverse-portrait"

val ReaderSupportedFontFamilies: List<String> = listOf(
	ReaderSansFontFamily,
	ReaderSerifFontFamily,
	ReaderBookFontFamily,
	ReaderHumanistFontFamily,
	ReaderDyslexicFontFamily,
	ReaderMonoFontFamily,
	ReaderPublisherFontFamily
)

val ReaderSupportedFontSources: List<String> = listOf(
	ReaderFontSourceNavic,
	ReaderFontSourceSystem,
	ReaderFontSourcePublisher
)

val ReaderSupportedTapZones: List<String> = listOf(
	ReaderTapZoneDefault,
	ReaderTapZoneEdge,
	ReaderTapZoneKindle,
	ReaderTapZoneLShaped,
	ReaderTapZoneRightLeft,
	ReaderTapZoneDisabled
)

val ReaderSupportedThemes: List<String> = listOf(
	ReaderLightTheme,
	ReaderSepiaTheme,
	ReaderDuskTheme,
	ReaderDarkTheme,
	ReaderBlackTheme
)

val ReaderSupportedFlowModes: List<String> = listOf(
	ReaderFlowPaged,
	ReaderFlowPagedVertical,
	ReaderFlowScrolled,
	ReaderFlowScrolledGaps
)

val ReaderSupportedDirections: List<String> = listOf(
	ReaderDirectionDefault,
	ReaderDirectionLtr,
	ReaderDirectionRtl
)

val ReaderSupportedOrientations: List<String> = listOf(
	ReaderOrientationDefault,
	ReaderOrientationFree,
	ReaderOrientationPortrait,
	ReaderOrientationLandscape,
	ReaderOrientationLockedPortrait,
	ReaderOrientationLockedLandscape,
	ReaderOrientationReversePortrait
)

fun normalizedReaderFontFamily(fontFamily: String?): String =
	when (fontFamily) {
		LegacyReaderBookFontFamily -> ReaderBookFontFamily
		LegacyReaderHumanistFontFamily -> ReaderHumanistFontFamily
		LegacyReaderDyslexicFontFamily -> ReaderDyslexicFontFamily
		else -> ReaderSupportedFontFamilies.firstOrNull { supported -> supported == fontFamily } ?: ReaderSansFontFamily
	}

fun normalizedReaderFontSource(fontSource: String?): String =
	ReaderSupportedFontSources.firstOrNull { supported -> supported == fontSource } ?: ReaderFontSourceNavic

fun normalizedReaderTheme(theme: String?): String =
	ReaderSupportedThemes.firstOrNull { supported -> supported == theme } ?: ReaderLightTheme

fun normalizedReaderFlowMode(
	flowMode: String?,
	paged: Boolean?
): String =
	ReaderSupportedFlowModes.firstOrNull { supported -> supported == flowMode }
		?: if (paged == false) ReaderFlowScrolled else ReaderFlowPaged

fun normalizedReaderTapZone(tapZone: String?): String =
	ReaderSupportedTapZones.firstOrNull { supported -> supported == tapZone } ?: ReaderTapZoneDefault

fun normalizedReaderOrientation(orientation: String?): String =
	ReaderSupportedOrientations.firstOrNull { supported -> supported == orientation } ?: ReaderOrientationDefault

fun normalizedReaderDirection(direction: String?): String =
	ReaderSupportedDirections.firstOrNull { supported -> supported == direction } ?: ReaderDirectionDefault

enum class ReaderOptionsTab {
	Reading,
	General,
	Media
}

fun readerOptionsTabs(showReadaloudControls: Boolean): List<ReaderOptionsTab> =
	if (showReadaloudControls) {
		listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General, ReaderOptionsTab.Media)
	} else {
		listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General)
	}

fun normalizedReaderOptionsTab(
	tab: ReaderOptionsTab,
	showReadaloudControls: Boolean
): ReaderOptionsTab =
	readerOptionsTabs(showReadaloudControls).firstOrNull { supported -> supported == tab }
		?: ReaderOptionsTab.Reading

fun readerOptionsTabLabel(tab: ReaderOptionsTab): String =
	when (tab) {
		ReaderOptionsTab.Reading -> "Reading"
		ReaderOptionsTab.General -> "General"
		ReaderOptionsTab.Media -> "Media"
	}

fun nextReaderTheme(theme: String?): String {
	val normalized = normalizedReaderTheme(theme)
	val index = ReaderSupportedThemes.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedThemes[(index + 1) % ReaderSupportedThemes.size]
}

fun nextReaderFontFamily(fontFamily: String?): String {
	val normalized = normalizedReaderFontFamily(fontFamily)
	val index = ReaderSupportedFontFamilies.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedFontFamilies[(index + 1) % ReaderSupportedFontFamilies.size]
}

fun nextReaderFontSource(fontSource: String?): String {
	val normalized = normalizedReaderFontSource(fontSource)
	val index = ReaderSupportedFontSources.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedFontSources[(index + 1) % ReaderSupportedFontSources.size]
}

fun nextReaderFlowMode(flowMode: String?, paged: Boolean?): String {
	val normalized = normalizedReaderFlowMode(flowMode, paged)
	val index = ReaderSupportedFlowModes.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedFlowModes[(index + 1) % ReaderSupportedFlowModes.size]
}

fun nextReaderTapZone(tapZone: String?): String {
	val normalized = normalizedReaderTapZone(tapZone)
	val index = ReaderSupportedTapZones.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedTapZones[(index + 1) % ReaderSupportedTapZones.size]
}

fun nextReaderOrientation(orientation: String?): String {
	val normalized = normalizedReaderOrientation(orientation)
	val index = ReaderSupportedOrientations.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedOrientations[(index + 1) % ReaderSupportedOrientations.size]
}

fun nextReaderDirection(direction: String?): String {
	val normalized = normalizedReaderDirection(direction)
	val index = ReaderSupportedDirections.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedDirections[(index + 1) % ReaderSupportedDirections.size]
}

fun readerFontFamilyShortLabel(fontFamily: String?): String =
	when (normalizedReaderFontFamily(fontFamily)) {
		ReaderSerifFontFamily -> "Serif"
		ReaderBookFontFamily -> "Book"
		ReaderHumanistFontFamily -> "Human"
		ReaderDyslexicFontFamily -> "Dys"
		ReaderMonoFontFamily -> "Mono"
		ReaderPublisherFontFamily -> "Pub"
		else -> "Sans"
	}

fun readerFontSourceShortLabel(fontSource: String?): String =
	when (normalizedReaderFontSource(fontSource)) {
		ReaderFontSourceSystem -> "System"
		ReaderFontSourcePublisher -> "Book"
		else -> "Navic"
	}

fun readerThemeShortLabel(theme: String?): String =
	when (normalizedReaderTheme(theme)) {
		ReaderSepiaTheme -> "Sepia"
		ReaderDuskTheme -> "Dusk"
		ReaderDarkTheme -> "Dark"
		ReaderBlackTheme -> "Black"
		else -> "Light"
	}

fun readerFlowShortLabel(flowMode: String?, paged: Boolean?): String =
	when (normalizedReaderFlowMode(flowMode, paged)) {
		ReaderFlowPagedVertical -> "Vertical"
		ReaderFlowScrolled -> "Scroll"
		ReaderFlowScrolledGaps -> "Scroll gaps"
		else -> "Paged"
	}

fun readerTapZoneShortLabel(tapZone: String?): String =
	when (normalizedReaderTapZone(tapZone)) {
		ReaderTapZoneEdge -> "Edge"
		ReaderTapZoneKindle -> "Kindle"
		ReaderTapZoneLShaped -> "L-shaped"
		ReaderTapZoneRightLeft -> "R/L"
		ReaderTapZoneDisabled -> "Off"
		else -> "Default"
	}

fun readerOrientationShortLabel(orientation: String?): String =
	when (normalizedReaderOrientation(orientation)) {
		ReaderOrientationFree -> "Free"
		ReaderOrientationPortrait -> "Portrait"
		ReaderOrientationLandscape -> "Land"
		ReaderOrientationLockedPortrait -> "Lock P"
		ReaderOrientationLockedLandscape -> "Lock L"
		ReaderOrientationReversePortrait -> "Reverse"
		else -> "Default"
	}

fun readerDirectionShortLabel(direction: String?): String =
	when (normalizedReaderDirection(direction)) {
		ReaderDirectionLtr -> "LTR"
		ReaderDirectionRtl -> "RTL"
		else -> "Default"
	}

fun defaultReaderSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = ReaderSansFontFamily,
		fontSource = ReaderFontSourceNavic,
		fontSizePercent = DefaultReaderFontSizePercent,
		lineHeightPercent = (DefaultReaderLineHeight * 100).roundToInt(),
		paragraphSpacingPercent = DefaultReaderParagraphSpacingPercent,
		marginPercent = DefaultReaderMarginPercent,
		dimOverlayPercent = DefaultReaderDimOverlayPercent,
		orientation = ReaderOrientationDefault,
		theme = ReaderLightTheme,
		direction = ReaderDirectionDefault,
		flowMode = ReaderFlowPaged,
		paged = true,
		tapZone = ReaderTapZoneDefault,
		smallerTapZone = false,
		showTapZones = false,
		publisherStyles = false,
		fullscreen = true,
		keepScreenOn = false,
		readaloudSyncEnabled = true,
		volumeKeyPageTurns = false,
		webContentsDebuggingEnabled = false
	)

fun normalizedReaderSettings(
	fontFamily: String?,
	fontSource: String? = ReaderFontSourceNavic,
	fontSizePercent: Int,
	lineHeightPercent: Int,
	paragraphSpacingPercent: Int = DefaultReaderParagraphSpacingPercent,
	marginPercent: Int,
	dimOverlayPercent: Int = DefaultReaderDimOverlayPercent,
	orientation: String? = ReaderOrientationDefault,
	theme: String?,
	direction: String? = ReaderDirectionDefault,
	flowMode: String? = null,
	paged: Boolean,
	tapZone: String? = ReaderTapZoneDefault,
	smallerTapZone: Boolean = false,
	showTapZones: Boolean = false,
	publisherStyles: Boolean = false,
	fullscreen: Boolean = true,
	keepScreenOn: Boolean = false,
	readaloudSyncEnabled: Boolean = true,
	volumeKeyPageTurns: Boolean = false,
	webContentsDebuggingEnabled: Boolean = false
): ReaderSettings =
	ReaderSettings(
		fontFamily = normalizedReaderFontFamily(fontFamily),
		fontSource = normalizedReaderFontSource(fontSource),
		fontSizePercent = fontSizePercent.coerceIn(MinReaderFontSizePercent, MaxReaderFontSizePercent),
		lineHeight = (lineHeightPercent.coerceIn(
			(MinReaderLineHeight * 100).roundToInt(),
			(MaxReaderLineHeight * 100).roundToInt()
		) / 100.0),
		paragraphSpacingPercent = paragraphSpacingPercent.coerceIn(
			MinReaderParagraphSpacingPercent,
			MaxReaderParagraphSpacingPercent
		),
		marginPercent = marginPercent.coerceIn(MinReaderMarginPercent, MaxReaderMarginPercent),
		dimOverlayPercent = dimOverlayPercent.coerceIn(
			MinReaderDimOverlayPercent,
			MaxReaderDimOverlayPercent
		),
		orientation = normalizedReaderOrientation(orientation),
		theme = normalizedReaderTheme(theme),
		direction = normalizedReaderDirection(direction),
		flowMode = normalizedReaderFlowMode(flowMode, paged),
		paged = normalizedReaderFlowMode(flowMode, paged) != ReaderFlowScrolled &&
			normalizedReaderFlowMode(flowMode, paged) != ReaderFlowScrolledGaps,
		tapZone = normalizedReaderTapZone(tapZone),
		smallerTapZone = smallerTapZone,
		showTapZones = showTapZones,
		publisherStyles = publisherStyles,
		fullscreen = fullscreen,
		keepScreenOn = keepScreenOn,
		readaloudSyncEnabled = readaloudSyncEnabled,
		volumeKeyPageTurns = volumeKeyPageTurns,
		webContentsDebuggingEnabled = webContentsDebuggingEnabled
	)

fun ReaderSettings.normalizedReaderSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = fontFamily,
		fontSource = fontSource,
		fontSizePercent = fontSizePercent ?: DefaultReaderFontSizePercent,
		lineHeightPercent = (((lineHeight ?: DefaultReaderLineHeight) * 100.0).roundToInt()),
		paragraphSpacingPercent = paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
		marginPercent = marginPercent ?: DefaultReaderMarginPercent,
		dimOverlayPercent = dimOverlayPercent ?: DefaultReaderDimOverlayPercent,
		orientation = orientation,
		theme = theme,
		direction = direction,
		flowMode = flowMode,
		paged = paged ?: true,
		tapZone = tapZone,
		smallerTapZone = smallerTapZone ?: false,
		showTapZones = showTapZones ?: false,
		publisherStyles = publisherStyles ?: false,
		fullscreen = fullscreen ?: true,
		keepScreenOn = keepScreenOn ?: false,
		readaloudSyncEnabled = readaloudSyncEnabled ?: true,
		volumeKeyPageTurns = volumeKeyPageTurns ?: false,
		webContentsDebuggingEnabled = webContentsDebuggingEnabled ?: false
	)

data class ReaderChromeState(
	val currentLocator: ReaderLocator? = null,
	val currentSectionTitle: String? = null,
	val settings: ReaderSettings = defaultReaderSettings(),
	val readaloudPlayback: ReaderReadaloudPlaybackUiState = ReaderReadaloudPlaybackUiState()
) {
	val progressFraction: Float?
		get() = currentLocator?.progress
			?.takeIf(Double::isFinite)
			?.coerceIn(0.0, 1.0)
			?.toFloat()

	val progressLabel: String
		get() = currentLocator
			?.let(::readerPageProgressLabel)
			?: readerProgressPercentLabel(currentLocator?.progress)

	val pageNumberLabel: String?
		get() {
			val locator = currentLocator ?: return null
			val pageIndex = locator.pageIndex?.takeIf { it >= 0 } ?: return null
			val pageCount = locator.pageCount?.takeIf { it > 0 } ?: return null
			return "${pageIndex + 1} / $pageCount"
		}

	fun onReaderEvent(event: ReaderBridgeEvent): ReaderChromeState =
		when (event) {
			is ReaderBridgeEvent.LocationChanged -> copy(
				currentLocator = event.locator,
				currentSectionTitle = event.tocTitle?.trim()?.takeIf { it.isNotEmpty() } ?: currentSectionTitle
			)
			is ReaderBridgeEvent.TocItemChanged -> copy(
				currentSectionTitle = event.title?.trim()?.takeIf { it.isNotEmpty() } ?: currentSectionTitle
			)
			else -> this
		}

	fun onReadaloudPlaybackState(state: ReaderReadaloudPlaybackUiState): ReaderChromeState =
		copy(readaloudPlayback = state)

	fun adjustFontSize(deltaPercent: Int): ReaderChromeState =
		copy(
			settings = settings.copy(
				fontSizePercent = ((settings.fontSizePercent ?: 100) + deltaPercent)
					.coerceIn(MinReaderFontSizePercent, MaxReaderFontSizePercent)
			)
		)

	fun toggleFontFamily(): ReaderChromeState =
		copy(
			settings = settings.copy(
				fontFamily = nextReaderFontFamily(settings.fontFamily)
			)
		)

	fun toggleFontSource(): ReaderChromeState =
		copy(
			settings = settings.copy(
				fontSource = nextReaderFontSource(settings.fontSource)
			)
		)

	fun adjustLineHeight(delta: Double): ReaderChromeState =
		copy(
			settings = settings.copy(
				lineHeight = (((settings.lineHeight ?: DefaultReaderLineHeight) + delta)
					.coerceIn(MinReaderLineHeight, MaxReaderLineHeight) * 100.0)
					.roundToInt() / 100.0
			)
		)

	fun adjustMargin(deltaPercent: Int): ReaderChromeState =
		copy(
			settings = settings.copy(
				marginPercent = ((settings.marginPercent ?: 0) + deltaPercent)
					.coerceIn(MinReaderMarginPercent, MaxReaderMarginPercent)
			)
		)

	fun adjustParagraphSpacing(deltaPercent: Int): ReaderChromeState =
		copy(
			settings = settings.copy(
				paragraphSpacingPercent = ((settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent) + deltaPercent)
					.coerceIn(MinReaderParagraphSpacingPercent, MaxReaderParagraphSpacingPercent)
			)
		)

	fun adjustDimOverlay(deltaPercent: Int): ReaderChromeState =
		copy(
			settings = settings.copy(
				dimOverlayPercent = ((settings.dimOverlayPercent ?: DefaultReaderDimOverlayPercent) + deltaPercent)
					.coerceIn(MinReaderDimOverlayPercent, MaxReaderDimOverlayPercent)
			)
		)

	fun toggleTheme(): ReaderChromeState =
		copy(
			settings = settings.copy(
				theme = nextReaderTheme(settings.theme)
			)
		)

	fun togglePagedMode(): ReaderChromeState =
		copy(
			settings = settings.copy(
				flowMode = if (settings.paged == false) ReaderFlowPaged else ReaderFlowScrolled,
				paged = settings.paged != true
			)
		)

	fun toggleFlowMode(): ReaderChromeState {
		val nextFlowMode = nextReaderFlowMode(settings.flowMode, settings.paged)
		return copy(
			settings = settings.copy(
				flowMode = nextFlowMode,
				paged = nextFlowMode != ReaderFlowScrolled && nextFlowMode != ReaderFlowScrolledGaps
			)
		)
	}

	fun toggleTapZone(): ReaderChromeState =
		copy(settings = settings.copy(tapZone = nextReaderTapZone(settings.tapZone)))

	fun toggleSmallerTapZone(): ReaderChromeState =
		copy(settings = settings.copy(smallerTapZone = settings.smallerTapZone != true))

	fun toggleShowTapZones(): ReaderChromeState =
		copy(settings = settings.copy(showTapZones = settings.showTapZones != true))

	fun toggleOrientation(): ReaderChromeState =
		copy(settings = settings.copy(orientation = nextReaderOrientation(settings.orientation)))

	fun toggleDirection(): ReaderChromeState =
		copy(settings = settings.copy(direction = nextReaderDirection(settings.direction)))

	fun togglePublisherStyles(): ReaderChromeState =
		copy(settings = settings.copy(publisherStyles = settings.publisherStyles != true))

	fun toggleFullscreen(): ReaderChromeState =
		copy(settings = settings.copy(fullscreen = settings.fullscreen != true))

	fun toggleKeepScreenOn(): ReaderChromeState =
		copy(settings = settings.copy(keepScreenOn = settings.keepScreenOn != true))

	fun toggleVolumeKeyPageTurns(): ReaderChromeState =
		copy(settings = settings.copy(volumeKeyPageTurns = settings.volumeKeyPageTurns != true))

	fun toSettingsCommand(): ReaderBridgeCommand.ApplySettings =
		ReaderBridgeCommand.ApplySettings(settings)
}

private fun readerPageProgressLabel(locator: ReaderLocator): String? {
	val pageIndex = locator.pageIndex?.takeIf { it >= 0 } ?: return null
	val pageCount = locator.pageCount?.takeIf { it > 0 } ?: return null
	val pageLabel = "Page ${pageIndex + 1} of $pageCount"
	val progressLabel = readerProgressPercentLabel(locator.progress)
	return if (progressLabel == "Progress unavailable") pageLabel else "$pageLabel • $progressLabel"
}

private fun readerProgressPercentLabel(progress: Double?): String =
	progress
		?.takeIf(Double::isFinite)
		?.coerceIn(0.0, 1.0)
		?.let { fraction -> "${(fraction * 100).roundToInt().coerceIn(0, 100)}%" }
		?: "Progress unavailable"

data class ReaderReadaloudPlaybackUiState(
	val isAvailable: Boolean = false,
	val isPlaying: Boolean = false,
	val trackIndex: Int = 0,
	val positionMs: Long = 0L,
	val durationMs: Long? = null,
	val playbackSpeed: Float = 1f,
	val activeAudioLabel: String? = null,
	val activeAudioMetadata: ReadaloudPlaybackMetadataLabels? = null,
	val syncEnabled: Boolean = true
) {
	fun toggleCommand(): ReaderReadaloudPlaybackCommand? =
		if (!isAvailable) {
			null
		} else if (isPlaying) {
			ReaderReadaloudPlaybackCommand.Pause
		} else {
			ReaderReadaloudPlaybackCommand.Play
		}

	fun speedCommandFor(speed: Float): ReaderReadaloudPlaybackCommand? =
		if (!isAvailable) {
			null
		} else {
			ReaderReadaloudPlaybackCommand.SetSpeed(normalizedReadaloudPlaybackSpeed(speed))
		}

	fun adjustSpeedCommand(delta: Float): ReaderReadaloudPlaybackCommand? =
		speedCommandFor(playbackSpeed + delta)

	fun toggleSyncCommand(): ReaderReadaloudPlaybackCommand? =
		if (!isAvailable) {
			null
		} else {
			ReaderReadaloudPlaybackCommand.SetSyncEnabled(!syncEnabled)
		}
}

sealed interface ReaderReadaloudPlaybackCommand {
	data object Play : ReaderReadaloudPlaybackCommand
	data object Pause : ReaderReadaloudPlaybackCommand
	data class SeekTo(val positionMs: Long) : ReaderReadaloudPlaybackCommand
	data class SeekToTrack(val trackIndex: Int, val positionMs: Long = 0L) : ReaderReadaloudPlaybackCommand
	data class SetSpeed(val speed: Float) : ReaderReadaloudPlaybackCommand
	data class SetSyncEnabled(val enabled: Boolean) : ReaderReadaloudPlaybackCommand
}

fun readerReadaloudPlaybackSpeedLabel(playbackSpeed: Float): String {
	val quarters = (normalizedReadaloudPlaybackSpeed(playbackSpeed) * 4f).roundToInt()
	val whole = quarters / 4
	return when (quarters % 4) {
		0 -> "${whole}x"
		1 -> "$whole.25x"
		2 -> "$whole.5x"
		else -> "$whole.75x"
	}
}

fun readerReadaloudControlsVisible(
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean
): Boolean =
	kind == ReaderPublicationKind.Readaloud && mediaOverlayEnabled
