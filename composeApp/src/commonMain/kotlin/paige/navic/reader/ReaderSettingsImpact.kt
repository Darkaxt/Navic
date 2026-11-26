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
		pageCacheScope = null,
		pageCacheLayouts = null,
		fullscreen = null,
		keepScreenOn = null,
		readaloudSyncEnabled = null,
		whispersyncHighlightLeadMs = null,
		whispersyncPageBoundary = null,
		volumeKeyPageTurns = null,
		webContentsDebuggingEnabled = null,
		animationLevel = null
	)

internal fun ReaderSettings.readerPageRasterSnapshotKey(): Int =
	readerEngineSettingsProjection().hashCode()

/**
 * Where the text falls on the pages. Only a change of this asks the reader to keep the new
 * layout; colours, paper, tap zones and highlights regenerate the rasters without asking.
 */
internal fun ReaderSettings.readerPageLayoutKey(): Int =
	readerEngineSettingsProjection().copy(
		theme = null,
		paperTextureEnabled = null,
		pageEdgesEnabled = null,
		paperStainsEnabled = null,
		coverBackdropEnabled = null,
		dragAnimationMode = null,
		tapZone = null,
		smallerTapZone = null,
		showTapZones = null,
		nativeTapZones = null,
		whispersyncHighlightColorArgb = null,
		whispersyncHighlightLoading = null,
		whispersyncHighlightStyle = null,
		// Whether a plate is drawn grey for its living overlay only changes its colours.
		illustrations = illustrations?.let { set ->
			set.copy(placements = set.placements.map { it.copy(livingPlayable = false) })
		}
	).hashCode()
