package paige.navic.reader

import paige.navic.domain.manager.PreferenceManager
import kotlin.math.roundToInt

fun PreferenceManager.readerDefaultSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = readerFontFamily,
		fontSizePercent = readerFontSizePercent,
		lineHeightPercent = readerLineHeightPercent,
		marginPercent = readerMarginPercent,
		theme = readerTheme,
		paged = readerPaged
	)

fun PreferenceManager.setReaderDefaultSettings(settings: ReaderSettings) {
	val normalized = settings.normalizedReaderSettings()
	readerFontFamily = normalized.fontFamily ?: ReaderSansFontFamily
	readerFontSizePercent = normalized.fontSizePercent ?: 100
	readerLineHeightPercent = (((normalized.lineHeight ?: 1.55) * 100.0).roundToInt())
	readerMarginPercent = normalized.marginPercent ?: 0
	readerTheme = normalized.theme ?: ReaderLightTheme
	readerPaged = normalized.paged ?: true
}
