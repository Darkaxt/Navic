package paige.navic.reader

import paige.navic.domain.manager.PreferenceManager
import kotlin.math.roundToInt

fun PreferenceManager.readerDefaultSettings(): ReaderSettings =
	normalizedReaderSettings(
		fontFamily = readerFontFamily,
		fontSizePercent = readerFontSizePercent,
		lineHeightPercent = readerLineHeightPercent,
		paragraphSpacingPercent = readerParagraphSpacingPercent,
		marginPercent = readerMarginPercent,
		dimOverlayPercent = readerDimOverlayPercent,
		orientation = readerOrientation,
		theme = readerTheme,
		direction = readerDirection,
		flowMode = readerFlowMode,
		paged = readerPaged,
		tapZone = readerTapZone,
		smallerTapZone = readerSmallerTapZone,
		publisherStyles = readerPublisherStylesEnabled,
		keepScreenOn = readerKeepScreenOn,
		readaloudSyncEnabled = readerReadaloudSyncEnabled,
		volumeKeyPageTurns = readerVolumeKeyPageTurns,
		webContentsDebuggingEnabled = readerWebContentsDebuggingEnabled
	)

fun PreferenceManager.setReaderDefaultSettings(settings: ReaderSettings) {
	val normalized = settings.normalizedReaderSettings()
	readerFontFamily = normalized.fontFamily ?: ReaderSansFontFamily
	readerFontSizePercent = normalized.fontSizePercent ?: 100
	readerLineHeightPercent = (((normalized.lineHeight ?: 1.55) * 100.0).roundToInt())
	readerParagraphSpacingPercent = normalized.paragraphSpacingPercent ?: 0
	readerMarginPercent = normalized.marginPercent ?: 0
	readerDimOverlayPercent = normalized.dimOverlayPercent ?: 0
	readerOrientation = normalized.orientation ?: ReaderOrientationDefault
	readerTheme = normalized.theme ?: ReaderLightTheme
	readerDirection = normalized.direction ?: ReaderDirectionDefault
	readerFlowMode = normalized.flowMode ?: ReaderFlowPaged
	readerPaged = normalized.paged ?: true
	readerTapZone = normalized.tapZone ?: ReaderTapZoneDefault
	readerSmallerTapZone = normalized.smallerTapZone ?: false
	readerPublisherStylesEnabled = normalized.publisherStyles ?: false
	readerKeepScreenOn = normalized.keepScreenOn ?: false
	readerReadaloudSyncEnabled = normalized.readaloudSyncEnabled ?: true
	readerVolumeKeyPageTurns = normalized.volumeKeyPageTurns ?: false
	readerWebContentsDebuggingEnabled = normalized.webContentsDebuggingEnabled ?: false
}
