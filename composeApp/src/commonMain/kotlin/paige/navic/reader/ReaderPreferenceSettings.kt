package paige.navic.reader

import paige.navic.domain.manager.PreferenceManager
import kotlin.math.roundToInt

fun PreferenceManager.readerDefaultSettings(): ReaderSettings {
	val paragraphSpacingPercent = when {
		readerParagraphSpacingPercent == LegacyReaderParagraphSpacingPercent &&
			(!readerParagraphSpacingDefaultMigrated || !readerParagraphSpacingReadableDefaultMigrated) -> {
			readerParagraphSpacingPercent = DefaultReaderParagraphSpacingPercent
			readerParagraphSpacingDefaultMigrated = true
			readerParagraphSpacingReadableDefaultMigrated = true
			DefaultReaderParagraphSpacingPercent
		}
		else -> {
			if (!readerParagraphSpacingDefaultMigrated) readerParagraphSpacingDefaultMigrated = true
			if (!readerParagraphSpacingReadableDefaultMigrated) readerParagraphSpacingReadableDefaultMigrated = true
			readerParagraphSpacingPercent
		}
	}

	return normalizedReaderSettings(
		fontFamily = readerFontFamily,
		fontSizePercent = readerFontSizePercent,
		lineHeightPercent = readerLineHeightPercent,
		paragraphSpacingPercent = paragraphSpacingPercent,
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
		fullscreen = readerFullscreen,
		keepScreenOn = readerKeepScreenOn,
		readaloudSyncEnabled = readerReadaloudSyncEnabled,
		volumeKeyPageTurns = readerVolumeKeyPageTurns,
		webContentsDebuggingEnabled = readerWebContentsDebuggingEnabled
	)
}

fun PreferenceManager.setReaderDefaultSettings(settings: ReaderSettings) {
	val normalized = settings.normalizedReaderSettings()
	readerFontFamily = normalized.fontFamily ?: ReaderSansFontFamily
	readerFontSizePercent = normalized.fontSizePercent ?: 100
	readerLineHeightPercent = (((normalized.lineHeight ?: 1.55) * 100.0).roundToInt())
	readerParagraphSpacingPercent = normalized.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent
	readerParagraphSpacingDefaultMigrated = true
	readerParagraphSpacingReadableDefaultMigrated = true
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
	readerFullscreen = normalized.fullscreen ?: true
	readerKeepScreenOn = normalized.keepScreenOn ?: false
	readerReadaloudSyncEnabled = normalized.readaloudSyncEnabled ?: true
	readerVolumeKeyPageTurns = normalized.volumeKeyPageTurns ?: false
	readerWebContentsDebuggingEnabled = normalized.webContentsDebuggingEnabled ?: false
}
