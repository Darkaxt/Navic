package paige.navic.reader

internal fun ReaderSettings.readerEngineSettingsProjection(): ReaderSettings =
	normalizedReaderSettings().copy(
		dimOverlayPercent = null,
		colorFilterEnabled = null,
		colorFilterArgb = null,
		colorFilterMode = null,
		grayscaleEnabled = null,
		invertedColors = null,
		orientation = null,
		navBarType = null,
		tapZoneInvertMode = null,
		pageBitmapQuality = null,
		fullscreen = null,
		keepScreenOn = null,
		readaloudSyncEnabled = null,
		whispersyncHighlightLeadMs = null,
		volumeKeyPageTurns = null,
		webContentsDebuggingEnabled = null
	)

internal fun ReaderSettings.readerPageRasterSnapshotKey(): Int =
	readerEngineSettingsProjection().hashCode()
