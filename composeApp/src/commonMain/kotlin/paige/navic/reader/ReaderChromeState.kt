package paige.navic.reader

import kotlin.math.roundToInt

private const val MinReaderFontSizePercent = 80
private const val MaxReaderFontSizePercent = 180
private const val DefaultReaderFontSizePercent = 100
private const val MinReaderLineHeight = 1.2
private const val MaxReaderLineHeight = 2.2
private const val DefaultReaderLineHeight = 1.55
private const val MinReaderMarginPercent = 0
private const val MaxReaderMarginPercent = 24
private const val DefaultReaderMarginPercent = 0
const val ReaderSansFontFamily = "system-ui, sans-serif"
const val ReaderSerifFontFamily = "Georgia, serif"
const val ReaderLightTheme = "light"
const val ReaderDarkTheme = "dark"

fun defaultReaderSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = ReaderSansFontFamily,
		fontSizePercent = DefaultReaderFontSizePercent,
		lineHeightPercent = (DefaultReaderLineHeight * 100).roundToInt(),
		marginPercent = DefaultReaderMarginPercent,
		theme = ReaderLightTheme,
		paged = true
	)

fun normalizedReaderSettings(
	fontFamily: String?,
	fontSizePercent: Int,
	lineHeightPercent: Int,
	marginPercent: Int,
	theme: String?,
	paged: Boolean
): ReaderSettings =
	ReaderSettings(
		fontFamily = when (fontFamily) {
			ReaderSerifFontFamily -> ReaderSerifFontFamily
			else -> ReaderSansFontFamily
		},
		fontSizePercent = fontSizePercent.coerceIn(MinReaderFontSizePercent, MaxReaderFontSizePercent),
		lineHeight = (lineHeightPercent.coerceIn(
			(MinReaderLineHeight * 100).roundToInt(),
			(MaxReaderLineHeight * 100).roundToInt()
		) / 100.0),
		marginPercent = marginPercent.coerceIn(MinReaderMarginPercent, MaxReaderMarginPercent),
		theme = when (theme) {
			ReaderDarkTheme -> ReaderDarkTheme
			else -> ReaderLightTheme
		},
		paged = paged
	)

fun ReaderSettings.normalizedReaderSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = fontFamily,
		fontSizePercent = fontSizePercent ?: DefaultReaderFontSizePercent,
		lineHeightPercent = (((lineHeight ?: DefaultReaderLineHeight) * 100.0).roundToInt()),
		marginPercent = marginPercent ?: DefaultReaderMarginPercent,
		theme = theme,
		paged = paged ?: true
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
		get() = progressFraction
			?.let { fraction -> "${(fraction * 100).roundToInt().coerceIn(0, 100)}%" }
			?: "Progress unavailable"

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
				fontFamily = if (settings.fontFamily == ReaderSerifFontFamily) {
					ReaderSansFontFamily
				} else {
					ReaderSerifFontFamily
				}
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

	fun toggleTheme(): ReaderChromeState =
		copy(
			settings = settings.copy(
				theme = if (settings.theme == ReaderDarkTheme) ReaderLightTheme else ReaderDarkTheme
			)
		)

	fun togglePagedMode(): ReaderChromeState =
		copy(settings = settings.copy(paged = settings.paged != true))

	fun toSettingsCommand(): ReaderBridgeCommand.ApplySettings =
		ReaderBridgeCommand.ApplySettings(settings)
}

data class ReaderReadaloudPlaybackUiState(
	val isAvailable: Boolean = false,
	val isPlaying: Boolean = false,
	val positionMs: Long = 0L,
	val durationMs: Long? = null,
	val playbackSpeed: Float = 1f
) {
	fun toggleCommand(): ReaderReadaloudPlaybackCommand? =
		if (!isAvailable) {
			null
		} else if (isPlaying) {
			ReaderReadaloudPlaybackCommand.Pause
		} else {
			ReaderReadaloudPlaybackCommand.Play
		}
}

enum class ReaderReadaloudPlaybackCommand {
	Play,
	Pause
}

fun readerReadaloudControlsVisible(
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean
): Boolean =
	kind == ReaderPublicationKind.Readaloud && mediaOverlayEnabled
