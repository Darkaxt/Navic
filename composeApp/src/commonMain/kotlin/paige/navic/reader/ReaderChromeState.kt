package paige.navic.reader

import kotlin.math.roundToInt

private const val MinReaderFontSizePercent = 80
private const val MaxReaderFontSizePercent = 180
const val DefaultReaderFontSizePercent = 140
private const val MinReaderLineHeight = 1.2
private const val MaxReaderLineHeight = 2.2
const val DefaultReaderLineHeight = 1.8
const val DefaultReaderLineHeightPercent = 180
private const val MinReaderParagraphSpacingPercent = 0
private const val MaxReaderParagraphSpacingPercent = 200
const val DefaultReaderParagraphSpacingPercent = 100
const val LegacyReaderParagraphSpacingPercent = 0
private const val MinReaderMarginPercent = 0
private const val MaxReaderMarginPercent = 24
private const val DefaultReaderMarginPercent = 0
private const val MinReaderFontWeight = 100.0
private const val MaxReaderFontWeight = 900.0
private const val DefaultReaderFontWeight = 400.0
private const val MinReaderLetterSpacing = -3.0
private const val MaxReaderLetterSpacing = 7.0
private const val DefaultReaderLetterSpacing = 0.0
private const val MinReaderWordSpacing = -4.0
private const val MaxReaderWordSpacing = 12.0
private const val DefaultReaderWordSpacing = 0.0
private const val MinReaderSideMargin = 0.0
private const val MaxReaderSideMargin = 20.0
private const val DefaultReaderSideMargin = 6.0
private const val MinReaderTopBottomMargin = 0.0
private const val MaxReaderTopBottomMargin = 200.0
private const val DefaultReaderTopMargin = 90.0
private const val DefaultReaderBottomMargin = 50.0
private const val MinReaderTextIndent = -0.5
private const val MaxReaderTextIndent = 8.0
private const val DefaultReaderTextIndent = 0.0
private const val MinReaderHeadingFontSize = 0.5
private const val MaxReaderHeadingFontSize = 2.0
private const val DefaultReaderHeadingFontSize = 1.0
private const val MinReaderMaxColumnCount = 0
private const val MaxReaderMaxColumnCount = 2
private const val DefaultReaderMaxColumnCount = 0
private const val MinReaderColumnThreshold = 400.0
private const val MaxReaderColumnThreshold = 1200.0
private const val DefaultReaderColumnThreshold = 720.0
private const val MinReaderDimOverlayPercent = 0
private const val MaxReaderDimOverlayPercent = 80
private const val DefaultReaderDimOverlayPercent = 0
private const val DefaultReaderColorFilterArgb = 0
private const val MinReaderPdfPageGapPercent = 0
private const val MaxReaderPdfPageGapPercent = 48
private const val DefaultReaderPdfPageGapPercent = 0
const val MinReaderWhispersyncHighlightLeadMs = -1000
const val MaxReaderWhispersyncHighlightLeadMs = 2000
const val DefaultReaderWhispersyncHighlightLeadMs = 750
const val ReaderSansFontFamily = "system-ui, sans-serif"
const val ReaderSerifFontFamily = "Georgia, serif"
const val ReaderBookFontFamily = "\"Navic Literata\", Literata, Bookerly, Georgia, serif"
const val ReaderHumanistFontFamily = "\"Navic Atkinson Hyperlegible\", \"Atkinson Hyperlegible\", Lexend, system-ui, sans-serif"
const val ReaderDyslexicFontFamily = "\"Navic OpenDyslexic\", OpenDyslexic, \"Navic Atkinson Hyperlegible\", system-ui, sans-serif"
const val ReaderTypewriterFontFamily = "\"American Typewriter\", \"Courier Prime\", \"Courier New\", ui-monospace, monospace"
const val ReaderMonoFontFamily = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
const val ReaderPublisherFontFamily = "inherit"
private const val LegacyReaderBookFontFamily = "Literata, Bookerly, Georgia, serif"
private const val LegacyReaderHumanistFontFamily = "Atkinson Hyperlegible, Lexend, system-ui, sans-serif"
private const val LegacyReaderDyslexicFontFamily = "OpenDyslexic, Atkinson Hyperlegible, Lexend, system-ui, sans-serif"
const val ReaderFontSourceNavic = "navic"
const val ReaderFontSourceSystem = "system"
const val ReaderFontSourcePublisher = "publisher"
const val ReaderFontSourceCustom = "custom"
const val ReaderLightTheme = "light"
const val ReaderSepiaTheme = "sepia"
const val ReaderDuskTheme = "dusk"
const val ReaderDarkTheme = "dark"
const val ReaderBlackTheme = "black"
const val ReaderDirectionDefault = "default"
const val ReaderDirectionLtr = "ltr"
const val ReaderDirectionRtl = "rtl"
const val ReaderNavBarTypeVerticalRight = "vertical-right"
const val ReaderNavBarTypeVerticalLeft = "vertical-left"
const val ReaderNavBarTypeBottom = "bottom"
const val ReaderDragAnimationStandard = "standard"
const val ReaderDragAnimationCurl = "curl"
const val ReaderFlowPaged = "paged"
const val ReaderFlowPagedVertical = "paged-vertical"
const val ReaderFlowScrolled = "scrolled"
const val ReaderFlowScrolledGaps = "scrolled-gaps"
const val ReaderColorFilterModeSrcOver = "src-over"
const val ReaderColorFilterModeMultiply = "multiply"
const val ReaderColorFilterModeScreen = "screen"
const val ReaderColorFilterModeOverlay = "overlay"
const val ReaderColorFilterModeLighten = "lighten"
const val ReaderColorFilterModeDarken = "darken"
const val ReaderTapZoneDefault = "default"
const val ReaderTapZoneEdge = "edge"
const val ReaderTapZoneKindle = "kindle"
const val ReaderTapZoneLShaped = "l-shaped"
const val ReaderTapZoneRightLeft = "right-left"
const val ReaderTapZoneDisabled = "disabled"
const val ReaderTapZoneInvertNone = "none"
const val ReaderTapZoneInvertHorizontal = "horizontal"
const val ReaderTapZoneInvertVertical = "vertical"
const val ReaderTapZoneInvertBoth = "both"
const val ReaderOrientationDefault = "default"
const val ReaderOrientationFree = "free"
const val ReaderOrientationPortrait = "portrait"
const val ReaderOrientationLandscape = "landscape"
const val ReaderOrientationLockedPortrait = "locked-portrait"
const val ReaderOrientationLockedLandscape = "locked-landscape"
const val ReaderOrientationReversePortrait = "reverse-portrait"
const val ReaderPdfFitWidth = "width"
const val ReaderPdfFitPage = "page"
const val ReaderPdfFitHeight = "height"
const val ReaderPdfFitOriginal = "original"

private const val ReaderTapZoneNormalSize = 0.33f
private const val ReaderTapZoneSmallerSize = 0.25f
private const val ReaderTapZoneConstantMenuHeight = 0.05f

val ReaderSupportedFontFamilies: List<String> = listOf(
	ReaderSansFontFamily,
	ReaderSerifFontFamily,
	ReaderBookFontFamily,
	ReaderHumanistFontFamily,
	ReaderDyslexicFontFamily,
	ReaderTypewriterFontFamily,
	ReaderMonoFontFamily,
	ReaderPublisherFontFamily
)

val ReaderSupportedFontSources: List<String> = listOf(
	ReaderFontSourceNavic,
	ReaderFontSourceSystem,
	ReaderFontSourcePublisher,
	ReaderFontSourceCustom
)

val ReaderSupportedDragAnimationModes: List<String> = listOf(
	ReaderDragAnimationStandard,
	ReaderDragAnimationCurl
)

val ReaderSupportedTapZones: List<String> = listOf(
	ReaderTapZoneDefault,
	ReaderTapZoneEdge,
	ReaderTapZoneKindle,
	ReaderTapZoneLShaped,
	ReaderTapZoneRightLeft,
	ReaderTapZoneDisabled
)

val ReaderSupportedTapZoneInvertModes: List<String> = listOf(
	ReaderTapZoneInvertNone,
	ReaderTapZoneInvertHorizontal,
	ReaderTapZoneInvertVertical,
	ReaderTapZoneInvertBoth
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

val ReaderSupportedNavBarTypes: List<String> = listOf(
	ReaderNavBarTypeVerticalRight,
	ReaderNavBarTypeVerticalLeft,
	ReaderNavBarTypeBottom
)

val ReaderSupportedColorFilterModes: List<String> = listOf(
	ReaderColorFilterModeSrcOver,
	ReaderColorFilterModeMultiply,
	ReaderColorFilterModeScreen,
	ReaderColorFilterModeOverlay,
	ReaderColorFilterModeLighten,
	ReaderColorFilterModeDarken
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

val ReaderSupportedPdfFitModes: List<String> = listOf(
	ReaderPdfFitWidth,
	ReaderPdfFitPage,
	ReaderPdfFitHeight,
	ReaderPdfFitOriginal
)

enum class ReaderSettingsScope {
	Global,
	Book
}

val ReaderSupportedSettingsScopes: List<ReaderSettingsScope> = listOf(
	ReaderSettingsScope.Global,
	ReaderSettingsScope.Book
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

fun normalizedReaderCustomFontFamily(customFontFamily: String?): String? =
	customFontFamily
		?.replace(Regex("[^A-Za-z0-9 _-]"), " ")
		?.replace(Regex("\\s+"), " ")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.take(80)

fun normalizedReaderCustomFontUrl(customFontUrl: String?): String? =
	customFontUrl
		?.trim()
		?.takeIf { it.isNotEmpty() }

fun normalizedReaderTheme(theme: String?): String =
	ReaderSupportedThemes.firstOrNull { supported -> supported == theme } ?: ReaderLightTheme

fun normalizedReaderColorFilterMode(colorFilterMode: String?): String =
	ReaderSupportedColorFilterModes.firstOrNull { supported -> supported == colorFilterMode }
		?: ReaderColorFilterModeSrcOver

fun normalizedReaderFlowMode(
	flowMode: String?,
	paged: Boolean?
): String =
	ReaderSupportedFlowModes.firstOrNull { supported -> supported == flowMode }
		?: if (paged == false) ReaderFlowScrolled else ReaderFlowPaged

fun normalizedReaderTapZone(tapZone: String?): String =
	ReaderSupportedTapZones.firstOrNull { supported -> supported == tapZone } ?: ReaderTapZoneDefault

fun normalizedReaderTapZoneInvertMode(tapZoneInvertMode: String?): String =
	ReaderSupportedTapZoneInvertModes.firstOrNull { supported -> supported == tapZoneInvertMode }
		?: ReaderTapZoneInvertNone

fun normalizedReaderOrientation(orientation: String?): String =
	ReaderSupportedOrientations.firstOrNull { supported -> supported == orientation } ?: ReaderOrientationDefault

fun normalizedReaderDirection(direction: String?): String =
	ReaderSupportedDirections.firstOrNull { supported -> supported == direction } ?: ReaderDirectionDefault

fun normalizedReaderNavBarType(navBarType: String?): String =
	ReaderSupportedNavBarTypes.firstOrNull { supported -> supported == navBarType } ?: ReaderNavBarTypeVerticalRight

fun normalizedReaderDragAnimationMode(dragAnimationMode: String?): String =
	ReaderSupportedDragAnimationModes.firstOrNull { supported -> supported == dragAnimationMode }
		?: ReaderDragAnimationStandard

fun normalizedReaderPdfFitMode(pdfFitMode: String?): String =
	ReaderSupportedPdfFitModes.firstOrNull { supported -> supported == pdfFitMode } ?: ReaderPdfFitWidth

enum class ReaderTapZoneAction {
	Menu,
	Previous,
	Next,
	Left,
	Right
}

data class ReaderTapZoneRegion(
	val left: Float,
	val top: Float,
	val right: Float,
	val bottom: Float,
	val action: ReaderTapZoneAction
) {
	fun contains(x: Float, y: Float): Boolean =
		x >= left && x <= right && y >= top && y <= bottom
}

fun readerTapZoneSize(smallerTapZone: Boolean): Float =
	if (smallerTapZone) ReaderTapZoneSmallerSize else ReaderTapZoneNormalSize

fun readerDefaultTapZoneMode(flowMode: String?): String =
	when (normalizedReaderFlowMode(flowMode, paged = null)) {
		ReaderFlowPagedVertical,
		ReaderFlowScrolled,
		ReaderFlowScrolledGaps -> ReaderTapZoneLShaped
		else -> ReaderTapZoneRightLeft
	}

fun readerTapZoneRegions(
	tapZone: String?,
	smallerTapZone: Boolean = false,
	flowMode: String? = ReaderFlowPaged
): List<ReaderTapZoneRegion> {
	val mode = normalizedReaderTapZone(tapZone).let { normalized ->
		if (normalized == ReaderTapZoneDefault) readerDefaultTapZoneMode(flowMode) else normalized
	}
	val regionSize1 = readerTapZoneSize(smallerTapZone)
	val regionSize2 = 1f - regionSize1
	return when (mode) {
		ReaderTapZoneLShaped -> listOf(
			ReaderTapZoneRegion(0f, regionSize1, regionSize1, regionSize2, ReaderTapZoneAction.Previous),
			ReaderTapZoneRegion(0f, 0f, 1f, regionSize1, ReaderTapZoneAction.Previous),
			ReaderTapZoneRegion(regionSize2, regionSize1, 1f, regionSize2, ReaderTapZoneAction.Next),
			ReaderTapZoneRegion(0f, regionSize2, 1f, 1f, ReaderTapZoneAction.Next)
		)
		ReaderTapZoneKindle -> listOf(
			ReaderTapZoneRegion(regionSize1, regionSize1, 1f, 1f, ReaderTapZoneAction.Next),
			ReaderTapZoneRegion(0f, regionSize1, regionSize1, 1f, ReaderTapZoneAction.Previous)
		)
		ReaderTapZoneEdge -> listOf(
			ReaderTapZoneRegion(0f, 0f, regionSize1, 1f, ReaderTapZoneAction.Next),
			ReaderTapZoneRegion(regionSize1, regionSize2, regionSize2, 1f, ReaderTapZoneAction.Previous),
			ReaderTapZoneRegion(regionSize2, 0f, 1f, 1f, ReaderTapZoneAction.Next)
		)
		ReaderTapZoneRightLeft -> listOf(
			ReaderTapZoneRegion(0f, 0f, regionSize1, 1f, ReaderTapZoneAction.Left),
			ReaderTapZoneRegion(regionSize2, 0f, 1f, 1f, ReaderTapZoneAction.Right)
		)
		ReaderTapZoneDisabled -> emptyList()
		else -> emptyList()
	}
}

fun readerTapZoneInteractiveRegions(
	tapZone: String?,
	smallerTapZone: Boolean = false,
	flowMode: String? = ReaderFlowPaged
): List<ReaderTapZoneRegion> =
	if (normalizedReaderTapZone(tapZone) == ReaderTapZoneDisabled) {
		emptyList()
	} else {
		readerTapZoneRegions(tapZone, smallerTapZone, flowMode)
	}

fun readerTapZoneActionAt(
	tapZone: String?,
	xFraction: Float,
	yFraction: Float,
	smallerTapZone: Boolean = false,
	flowMode: String? = ReaderFlowPaged
): ReaderTapZoneAction {
	val x = xFraction.coerceIn(0f, 1f)
	val y = yFraction.coerceIn(0f, 1f)
	val regionAction = readerTapZoneRegions(tapZone, smallerTapZone, flowMode)
		.firstOrNull { region -> region.contains(x, y) }
		?.action
	if (regionAction != null) return regionAction
	if (y <= ReaderTapZoneConstantMenuHeight) return ReaderTapZoneAction.Menu
	return ReaderTapZoneAction.Menu
}

fun readerTapZoneActionTurnsPage(action: ReaderTapZoneAction): Boolean =
	action == ReaderTapZoneAction.Previous ||
		action == ReaderTapZoneAction.Next ||
		action == ReaderTapZoneAction.Left ||
		action == ReaderTapZoneAction.Right

fun readerTapZonePageTurnDirectionFor(
	action: ReaderTapZoneAction,
	direction: String?
): ReaderPageTurnDirection? =
	when (action) {
		ReaderTapZoneAction.Previous -> ReaderPageTurnDirection.Previous
		ReaderTapZoneAction.Next -> ReaderPageTurnDirection.Next
		ReaderTapZoneAction.Left ->
			if (normalizedReaderDirection(direction) == ReaderDirectionRtl) {
				ReaderPageTurnDirection.Next
			} else {
				ReaderPageTurnDirection.Previous
			}
		ReaderTapZoneAction.Right ->
			if (normalizedReaderDirection(direction) == ReaderDirectionRtl) {
				ReaderPageTurnDirection.Previous
			} else {
				ReaderPageTurnDirection.Next
			}
		ReaderTapZoneAction.Menu -> null
	}

fun readerShellCoverSwipeAction(
	deltaX: Float,
	deltaY: Float,
	thresholdPx: Float
): ReaderTapZoneAction? {
	if (kotlin.math.abs(deltaX) <= thresholdPx) return null
	return if (deltaX < 0f) {
		ReaderTapZoneAction.Right
	} else {
		ReaderTapZoneAction.Left
	}
}

fun readerNativeReaderSwipeAction(
	deltaX: Float,
	deltaY: Float,
	thresholdPx: Float,
	verticalPageDragPreview: Boolean = false
): ReaderTapZoneAction? {
	if (verticalPageDragPreview) {
		if (kotlin.math.abs(deltaY) <= thresholdPx) return null
		if (kotlin.math.abs(deltaY) <= kotlin.math.abs(deltaX)) return null
		return if (deltaY < 0f) {
			ReaderTapZoneAction.Right
		} else {
			ReaderTapZoneAction.Left
		}
	}
	if (kotlin.math.abs(deltaX) <= thresholdPx) return null
	if (kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY)) return null
	return if (deltaX < 0f) {
		ReaderTapZoneAction.Right
	} else {
		ReaderTapZoneAction.Left
	}
}

fun readerShouldReturnToNativeShellCover(
	shellCoverUrl: String?,
	shellCoverVisible: Boolean,
	locator: ReaderLocator?
): Boolean =
		!shellCoverVisible &&
		!shellCoverUrl.isNullOrBlank() &&
		readerLocatorCanRepresentNativeShellCoverBoundary(locator) &&
		readerLocatorIsNativeShellCoverPageBoundary(locator)

private fun readerLocatorIsNativeShellCoverPageBoundary(locator: ReaderLocator?): Boolean {
	if ((locator?.pageCount ?: 0) <= 0) return false
	if ((locator?.pageIndex ?: Int.MAX_VALUE) <= 1) return true
	return (locator?.progress ?: 1.0) <= 0.02
}

private fun readerLocatorCanRepresentNativeShellCoverBoundary(locator: ReaderLocator?): Boolean {
	val progress = locator?.progress
	if (progress != null) return progress <= 0.02
	val href = locator?.href?.trim().orEmpty()
	if (href.isBlank()) return true
	return readerHrefLooksLikeNativeShellCoverBoundary(href)
}

fun readerHrefLooksLikeNativeShellCoverBoundary(href: String): Boolean {
	val leaf = href
		.substringBefore('#')
		.substringBefore('?')
		.substringAfterLast('/')
		.lowercase()
	return listOf(
		"cover",
		"title",
		"titlepage",
		"title-page",
		"toc",
		"contents",
		"frontmatter",
		"front-matter"
	).any { marker -> leaf.contains(marker) }
}

enum class ReaderOptionsTab {
	Reading,
	General,
	Media,
	PdfImage
}

fun readerOptionsTabs(
	showReadaloudControls: Boolean,
	publicationFormat: ReaderPublicationFormat = ReaderPublicationFormat.Epub
): List<ReaderOptionsTab> =
	if (publicationFormat == ReaderPublicationFormat.Pdf) {
		listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General, ReaderOptionsTab.PdfImage)
	} else if (showReadaloudControls) {
		listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General, ReaderOptionsTab.Media)
	} else {
		listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General)
	}

fun normalizedReaderOptionsTab(
	tab: ReaderOptionsTab,
	showReadaloudControls: Boolean,
	publicationFormat: ReaderPublicationFormat = ReaderPublicationFormat.Epub
): ReaderOptionsTab =
	readerOptionsTabs(showReadaloudControls, publicationFormat).firstOrNull { supported -> supported == tab }
		?: ReaderOptionsTab.Reading

fun readerOptionsTabLabel(tab: ReaderOptionsTab): String =
	when (tab) {
		ReaderOptionsTab.Reading -> "Reading"
		ReaderOptionsTab.General -> "General"
		ReaderOptionsTab.Media -> "Media"
		ReaderOptionsTab.PdfImage -> "PDF/Image"
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

fun nextReaderTapZoneInvertMode(tapZoneInvertMode: String?): String {
	val normalized = normalizedReaderTapZoneInvertMode(tapZoneInvertMode)
	val index = ReaderSupportedTapZoneInvertModes.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedTapZoneInvertModes[(index + 1) % ReaderSupportedTapZoneInvertModes.size]
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

fun nextReaderNavBarType(navBarType: String?): String {
	val normalized = normalizedReaderNavBarType(navBarType)
	val index = ReaderSupportedNavBarTypes.indexOf(normalized).takeIf { it >= 0 } ?: 0
	return ReaderSupportedNavBarTypes[(index + 1) % ReaderSupportedNavBarTypes.size]
}

fun readerFontFamilyShortLabel(fontFamily: String?): String =
	when (normalizedReaderFontFamily(fontFamily)) {
		ReaderSerifFontFamily -> "Serif"
		ReaderBookFontFamily -> "Book"
		ReaderHumanistFontFamily -> "Human"
		ReaderDyslexicFontFamily -> "Dys"
		ReaderTypewriterFontFamily -> "Dyx"
		ReaderMonoFontFamily -> "Mono"
		ReaderPublisherFontFamily -> "Pub"
		else -> "Sans"
	}

fun readerFontSourceShortLabel(fontSource: String?): String =
	when (normalizedReaderFontSource(fontSource)) {
		ReaderFontSourceSystem -> "System"
		ReaderFontSourcePublisher -> "Book"
		ReaderFontSourceCustom -> "Imported"
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

fun readerTapZoneInvertModeShortLabel(tapZoneInvertMode: String?): String =
	when (normalizedReaderTapZoneInvertMode(tapZoneInvertMode)) {
		ReaderTapZoneInvertHorizontal -> "Horizontal"
		ReaderTapZoneInvertVertical -> "Vertical"
		ReaderTapZoneInvertBoth -> "Both"
		else -> "None"
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

fun readerNavBarTypeShortLabel(navBarType: String?): String =
	when (normalizedReaderNavBarType(navBarType)) {
		ReaderNavBarTypeVerticalLeft -> "Left"
		ReaderNavBarTypeBottom -> "Bottom"
		else -> "Right"
	}

fun readerDragAnimationModeShortLabel(dragAnimationMode: String?): String =
	when (normalizedReaderDragAnimationMode(dragAnimationMode)) {
		ReaderDragAnimationCurl -> "Curl"
		else -> "Standard"
	}

fun readerColorFilterModeShortLabel(colorFilterMode: String?): String =
	when (normalizedReaderColorFilterMode(colorFilterMode)) {
		ReaderColorFilterModeMultiply -> "Multiply"
		ReaderColorFilterModeScreen -> "Screen"
		ReaderColorFilterModeOverlay -> "Overlay"
		ReaderColorFilterModeLighten -> "Lighten"
		ReaderColorFilterModeDarken -> "Darken"
		else -> "Default"
	}

fun readerPdfFitShortLabel(pdfFitMode: String?): String =
	when (normalizedReaderPdfFitMode(pdfFitMode)) {
		ReaderPdfFitPage -> "Page"
		ReaderPdfFitHeight -> "Height"
		ReaderPdfFitOriginal -> "Original"
		else -> "Width"
	}

fun readerSettingsScopeLabel(scope: ReaderSettingsScope): String =
	when (scope) {
		ReaderSettingsScope.Global -> "Global"
		ReaderSettingsScope.Book -> "For this book"
	}

fun defaultReaderSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = ReaderSansFontFamily,
		fontSource = ReaderFontSourceNavic,
		fontSizePercent = DefaultReaderFontSizePercent,
		lineHeightPercent = DefaultReaderLineHeightPercent,
		paragraphSpacingPercent = DefaultReaderParagraphSpacingPercent,
		marginPercent = DefaultReaderMarginPercent,
		dimOverlayPercent = DefaultReaderDimOverlayPercent,
		colorFilterEnabled = false,
		colorFilterArgb = DefaultReaderColorFilterArgb,
		colorFilterMode = ReaderColorFilterModeSrcOver,
		grayscaleEnabled = false,
		invertedColors = false,
		orientation = ReaderOrientationDefault,
		theme = ReaderLightTheme,
		direction = ReaderDirectionDefault,
		paperTextureEnabled = true,
		pageEdgesEnabled = true,
		paperStainsEnabled = true,
		navBarType = ReaderNavBarTypeVerticalRight,
		dragAnimationMode = ReaderDragAnimationStandard,
		flowMode = ReaderFlowPaged,
		paged = true,
		tapZone = ReaderTapZoneDefault,
		tapZoneInvertMode = ReaderTapZoneInvertNone,
		smallerTapZone = false,
		showTapZones = false,
		publisherStyles = false,
		fullscreen = true,
		keepScreenOn = false,
		readaloudSyncEnabled = true,
		volumeKeyPageTurns = false,
		webContentsDebuggingEnabled = false,
		pdfFitMode = ReaderPdfFitWidth,
		pdfCropBorders = false,
		pdfPageGapPercent = DefaultReaderPdfPageGapPercent
	)

fun normalizedReaderSettings(
	fontFamily: String?,
	fontSource: String? = ReaderFontSourceNavic,
	customFontFamily: String? = null,
	customFontUrl: String? = null,
	fontSizePercent: Int,
	lineHeightPercent: Int,
	paragraphSpacingPercent: Int = DefaultReaderParagraphSpacingPercent,
	marginPercent: Int,
	fontWeight: Double = DefaultReaderFontWeight,
	letterSpacing: Double = DefaultReaderLetterSpacing,
	wordSpacing: Double = DefaultReaderWordSpacing,
	sideMargin: Double = DefaultReaderSideMargin,
	topMargin: Double = DefaultReaderTopMargin,
	bottomMargin: Double = DefaultReaderBottomMargin,
	indent: Double = DefaultReaderTextIndent,
	headingFontSize: Double = DefaultReaderHeadingFontSize,
	maxColumnCount: Int = DefaultReaderMaxColumnCount,
	columnThreshold: Double = DefaultReaderColumnThreshold,
	dimOverlayPercent: Int = DefaultReaderDimOverlayPercent,
	colorFilterEnabled: Boolean = false,
	colorFilterArgb: Int = DefaultReaderColorFilterArgb,
	colorFilterMode: String? = ReaderColorFilterModeSrcOver,
	grayscaleEnabled: Boolean = false,
	invertedColors: Boolean = false,
	orientation: String? = ReaderOrientationDefault,
	theme: String?,
	direction: String? = ReaderDirectionDefault,
	paperTextureEnabled: Boolean = true,
	pageEdgesEnabled: Boolean = true,
	paperStainsEnabled: Boolean = true,
	navBarType: String? = ReaderNavBarTypeVerticalRight,
	dragAnimationMode: String? = ReaderDragAnimationStandard,
	flowMode: String? = null,
	paged: Boolean,
	tapZone: String? = ReaderTapZoneDefault,
	tapZoneInvertMode: String? = ReaderTapZoneInvertNone,
	smallerTapZone: Boolean = false,
	showTapZones: Boolean = false,
	publisherStyles: Boolean = false,
	fullscreen: Boolean = true,
	keepScreenOn: Boolean = false,
	readaloudSyncEnabled: Boolean = true,
	whispersyncHighlightLeadMs: Int = DefaultReaderWhispersyncHighlightLeadMs,
	whispersyncHighlightColorArgb: Int = DefaultReaderWhispersyncHighlightColorArgb,
	whispersyncHighlightLoading: String? = ReaderWhispersyncHighlightLoading.CurrentCue.value,
	whispersyncHighlightStyle: String? = ReaderWhispersyncHighlightStyle.Selection.value,
	volumeKeyPageTurns: Boolean = false,
	webContentsDebuggingEnabled: Boolean = false,
	pdfFitMode: String? = ReaderPdfFitWidth,
	pdfCropBorders: Boolean = false,
	pdfPageGapPercent: Int = DefaultReaderPdfPageGapPercent
): ReaderSettings {
	val source = normalizedReaderFontSource(fontSource)
	val normalizedCustomFontFamily = if (source == ReaderFontSourceCustom) {
		normalizedReaderCustomFontFamily(customFontFamily)
	} else {
		null
	}
	val normalizedCustomFontUrl = if (source == ReaderFontSourceCustom) {
		normalizedReaderCustomFontUrl(customFontUrl)
	} else {
		null
	}
	return ReaderSettings(
		fontFamily = normalizedReaderFontFamily(fontFamily),
		fontSource = source,
		customFontFamily = normalizedCustomFontFamily,
		customFontUrl = normalizedCustomFontUrl,
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
		fontWeight = fontWeight.takeIf(Double::isFinite)
			?.coerceIn(MinReaderFontWeight, MaxReaderFontWeight)
			?: DefaultReaderFontWeight,
		letterSpacing = letterSpacing.takeIf(Double::isFinite)
			?.coerceIn(MinReaderLetterSpacing, MaxReaderLetterSpacing)
			?: DefaultReaderLetterSpacing,
		wordSpacing = wordSpacing.takeIf(Double::isFinite)
			?.coerceIn(MinReaderWordSpacing, MaxReaderWordSpacing)
			?: DefaultReaderWordSpacing,
		sideMargin = sideMargin.takeIf(Double::isFinite)
			?.coerceIn(MinReaderSideMargin, MaxReaderSideMargin)
			?: DefaultReaderSideMargin,
		topMargin = topMargin.takeIf(Double::isFinite)
			?.coerceIn(MinReaderTopBottomMargin, MaxReaderTopBottomMargin)
			?: DefaultReaderTopMargin,
		bottomMargin = bottomMargin.takeIf(Double::isFinite)
			?.coerceIn(MinReaderTopBottomMargin, MaxReaderTopBottomMargin)
			?: DefaultReaderBottomMargin,
		indent = indent.takeIf(Double::isFinite)
			?.coerceIn(MinReaderTextIndent, MaxReaderTextIndent)
			?: DefaultReaderTextIndent,
		headingFontSize = headingFontSize.takeIf(Double::isFinite)
			?.coerceIn(MinReaderHeadingFontSize, MaxReaderHeadingFontSize)
			?: DefaultReaderHeadingFontSize,
		maxColumnCount = maxColumnCount.coerceIn(MinReaderMaxColumnCount, MaxReaderMaxColumnCount),
		columnThreshold = columnThreshold.takeIf(Double::isFinite)
			?.coerceIn(MinReaderColumnThreshold, MaxReaderColumnThreshold)
			?: DefaultReaderColumnThreshold,
		dimOverlayPercent = dimOverlayPercent.coerceIn(
			MinReaderDimOverlayPercent,
			MaxReaderDimOverlayPercent
		),
		colorFilterEnabled = colorFilterEnabled,
		colorFilterArgb = colorFilterArgb,
		colorFilterMode = normalizedReaderColorFilterMode(colorFilterMode),
		grayscaleEnabled = grayscaleEnabled,
		invertedColors = invertedColors,
		orientation = normalizedReaderOrientation(orientation),
		theme = normalizedReaderTheme(theme),
		direction = normalizedReaderDirection(direction),
		paperTextureEnabled = paperTextureEnabled,
		pageEdgesEnabled = pageEdgesEnabled,
		paperStainsEnabled = paperStainsEnabled,
		navBarType = normalizedReaderNavBarType(navBarType),
		dragAnimationMode = normalizedReaderDragAnimationMode(dragAnimationMode),
		flowMode = normalizedReaderFlowMode(flowMode, paged),
		paged = normalizedReaderFlowMode(flowMode, paged) != ReaderFlowScrolled &&
			normalizedReaderFlowMode(flowMode, paged) != ReaderFlowScrolledGaps,
		tapZone = normalizedReaderTapZone(tapZone),
		tapZoneInvertMode = normalizedReaderTapZoneInvertMode(tapZoneInvertMode),
		smallerTapZone = smallerTapZone,
		showTapZones = showTapZones,
		publisherStyles = publisherStyles,
		fullscreen = fullscreen,
		keepScreenOn = keepScreenOn,
		readaloudSyncEnabled = readaloudSyncEnabled,
		whispersyncHighlightLeadMs = normalizedReaderWhispersyncHighlightLeadMs(whispersyncHighlightLeadMs),
		whispersyncHighlightColorArgb = whispersyncHighlightColorArgb,
		whispersyncHighlightLoading = normalizedReaderWhispersyncHighlightLoading(whispersyncHighlightLoading).value,
		whispersyncHighlightStyle = normalizedReaderWhispersyncHighlightStyle(whispersyncHighlightStyle).value,
		volumeKeyPageTurns = volumeKeyPageTurns,
		webContentsDebuggingEnabled = webContentsDebuggingEnabled,
		pdfFitMode = normalizedReaderPdfFitMode(pdfFitMode),
		pdfCropBorders = pdfCropBorders,
		pdfPageGapPercent = pdfPageGapPercent.coerceIn(
			MinReaderPdfPageGapPercent,
			MaxReaderPdfPageGapPercent
		)
	)
}

fun ReaderSettings.normalizedReaderSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = fontFamily,
		fontSource = fontSource,
		customFontFamily = customFontFamily,
		customFontUrl = customFontUrl,
		fontSizePercent = fontSizePercent ?: DefaultReaderFontSizePercent,
		lineHeightPercent = (((lineHeight ?: DefaultReaderLineHeight) * 100.0).roundToInt()),
		paragraphSpacingPercent = paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
		marginPercent = marginPercent ?: DefaultReaderMarginPercent,
		fontWeight = fontWeight ?: DefaultReaderFontWeight,
		letterSpacing = letterSpacing ?: DefaultReaderLetterSpacing,
		wordSpacing = wordSpacing ?: DefaultReaderWordSpacing,
		sideMargin = sideMargin ?: DefaultReaderSideMargin,
		topMargin = topMargin ?: DefaultReaderTopMargin,
		bottomMargin = bottomMargin ?: DefaultReaderBottomMargin,
		indent = indent ?: DefaultReaderTextIndent,
		headingFontSize = headingFontSize ?: DefaultReaderHeadingFontSize,
		maxColumnCount = maxColumnCount ?: DefaultReaderMaxColumnCount,
		columnThreshold = columnThreshold ?: DefaultReaderColumnThreshold,
		dimOverlayPercent = dimOverlayPercent ?: DefaultReaderDimOverlayPercent,
		colorFilterEnabled = colorFilterEnabled ?: false,
		colorFilterArgb = colorFilterArgb ?: DefaultReaderColorFilterArgb,
		colorFilterMode = colorFilterMode,
		grayscaleEnabled = grayscaleEnabled ?: false,
		invertedColors = invertedColors ?: false,
		orientation = orientation,
		theme = theme,
		direction = direction,
		paperTextureEnabled = paperTextureEnabled ?: true,
		pageEdgesEnabled = pageEdgesEnabled ?: true,
		paperStainsEnabled = paperStainsEnabled ?: true,
		navBarType = navBarType,
		dragAnimationMode = dragAnimationMode,
		flowMode = flowMode,
		paged = paged ?: true,
		tapZone = tapZone,
		tapZoneInvertMode = tapZoneInvertMode,
		smallerTapZone = smallerTapZone ?: false,
		showTapZones = showTapZones ?: false,
		publisherStyles = publisherStyles ?: false,
		fullscreen = fullscreen ?: true,
		keepScreenOn = keepScreenOn ?: false,
		readaloudSyncEnabled = readaloudSyncEnabled ?: true,
		whispersyncHighlightLeadMs = normalizedReaderWhispersyncHighlightLeadMs(whispersyncHighlightLeadMs),
		whispersyncHighlightColorArgb = whispersyncHighlightColorArgb ?: DefaultReaderWhispersyncHighlightColorArgb,
		whispersyncHighlightLoading = normalizedReaderWhispersyncHighlightLoading(whispersyncHighlightLoading).value,
		whispersyncHighlightStyle = normalizedReaderWhispersyncHighlightStyle(whispersyncHighlightStyle).value,
		volumeKeyPageTurns = volumeKeyPageTurns ?: false,
		webContentsDebuggingEnabled = webContentsDebuggingEnabled ?: false,
		pdfFitMode = pdfFitMode,
		pdfCropBorders = pdfCropBorders ?: false,
		pdfPageGapPercent = pdfPageGapPercent ?: DefaultReaderPdfPageGapPercent
	)

fun normalizedReaderWhispersyncHighlightLeadMs(value: Int?): Int =
	(value ?: DefaultReaderWhispersyncHighlightLeadMs).coerceIn(
		MinReaderWhispersyncHighlightLeadMs,
		MaxReaderWhispersyncHighlightLeadMs
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

	fun onLocationChanged(
		locator: ReaderLocator,
		tocTitle: String?
	): ReaderChromeState =
		copy(
			currentLocator = locator,
			currentSectionTitle = tocTitle?.trim()?.takeIf { it.isNotEmpty() } ?: currentSectionTitle
		)

	fun onTocItemChanged(title: String?): ReaderChromeState =
		copy(
			currentSectionTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: currentSectionTitle
		)

	fun onReadaloudPlaybackState(state: ReaderReadaloudPlaybackUiState): ReaderChromeState =
		copy(readaloudPlayback = state)

	fun adjustFontSize(deltaPercent: Int): ReaderChromeState =
		copy(
			settings = settings.copy(
				fontSizePercent = ((settings.fontSizePercent ?: DefaultReaderFontSizePercent) + deltaPercent)
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

	fun setPdfFitMode(pdfFitMode: String): ReaderChromeState =
		copy(settings = settings.copy(pdfFitMode = normalizedReaderPdfFitMode(pdfFitMode)))

	fun togglePdfCropBorders(): ReaderChromeState =
		copy(settings = settings.copy(pdfCropBorders = settings.pdfCropBorders != true))

	fun adjustPdfPageGap(deltaPercent: Int): ReaderChromeState =
		copy(
			settings = settings.copy(
				pdfPageGapPercent = ((settings.pdfPageGapPercent ?: DefaultReaderPdfPageGapPercent) + deltaPercent)
					.coerceIn(MinReaderPdfPageGapPercent, MaxReaderPdfPageGapPercent)
			)
		)

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
	val audioResource: String? = null,
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
	data object StopAndReset : ReaderReadaloudPlaybackCommand
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
