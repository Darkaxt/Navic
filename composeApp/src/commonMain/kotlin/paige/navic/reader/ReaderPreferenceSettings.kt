package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import paige.navic.domain.manager.PreferenceManager
import kotlin.math.roundToInt

private val ReaderPreferenceSettingsJson = Json {
	ignoreUnknownKeys = true
	isLenient = true
}

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
		fontSource = readerFontSource,
		customFontFamily = readerCustomFontFamily,
		customFontUrl = readerCustomFontUrl,
		fontSizePercent = readerFontSizePercent,
		lineHeightPercent = readerLineHeightPercent,
		paragraphSpacingPercent = paragraphSpacingPercent,
		marginPercent = readerMarginPercent,
		dimOverlayPercent = readerDimOverlayPercent,
		colorFilterEnabled = readerColorFilterEnabled,
		colorFilterArgb = readerColorFilterArgb,
		colorFilterMode = readerColorFilterMode,
		grayscaleEnabled = readerGrayscaleEnabled,
		invertedColors = readerInvertedColors,
		orientation = readerOrientation,
		theme = readerTheme,
		direction = readerDirection,
		flowMode = readerFlowMode,
		paged = readerPaged,
		tapZone = readerTapZone,
		smallerTapZone = readerSmallerTapZone,
		showTapZones = readerShowTapZones,
		pdfFitMode = readerPdfFitMode,
		pdfCropBorders = readerPdfCropBorders,
		pdfPageGapPercent = readerPdfPageGapPercent,
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
	readerFontSource = normalized.fontSource ?: ReaderFontSourceNavic
	readerCustomFontFamily = normalized.customFontFamily.orEmpty()
	readerCustomFontUrl = normalized.customFontUrl.orEmpty()
	readerFontSizePercent = normalized.fontSizePercent ?: 100
	readerLineHeightPercent = (((normalized.lineHeight ?: 1.55) * 100.0).roundToInt())
	readerParagraphSpacingPercent = normalized.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent
	readerParagraphSpacingDefaultMigrated = true
	readerParagraphSpacingReadableDefaultMigrated = true
	readerMarginPercent = normalized.marginPercent ?: 0
	readerDimOverlayPercent = normalized.dimOverlayPercent ?: 0
	readerColorFilterEnabled = normalized.colorFilterEnabled ?: false
	readerColorFilterArgb = normalized.colorFilterArgb ?: 0
	readerColorFilterMode = normalized.colorFilterMode ?: ReaderColorFilterModeSrcOver
	readerGrayscaleEnabled = normalized.grayscaleEnabled ?: false
	readerInvertedColors = normalized.invertedColors ?: false
	readerOrientation = normalized.orientation ?: ReaderOrientationDefault
	readerTheme = normalized.theme ?: ReaderLightTheme
	readerDirection = normalized.direction ?: ReaderDirectionDefault
	readerFlowMode = normalized.flowMode ?: ReaderFlowPaged
	readerPaged = normalized.paged ?: true
	readerTapZone = normalized.tapZone ?: ReaderTapZoneDefault
	readerSmallerTapZone = normalized.smallerTapZone ?: false
	readerShowTapZones = normalized.showTapZones ?: false
	readerPdfFitMode = normalized.pdfFitMode ?: ReaderPdfFitWidth
	readerPdfCropBorders = normalized.pdfCropBorders ?: false
	readerPdfPageGapPercent = normalized.pdfPageGapPercent ?: 0
	readerPublisherStylesEnabled = normalized.publisherStyles ?: false
	readerFullscreen = normalized.fullscreen ?: true
	readerKeepScreenOn = normalized.keepScreenOn ?: false
	readerReadaloudSyncEnabled = normalized.readaloudSyncEnabled ?: true
	readerVolumeKeyPageTurns = normalized.volumeKeyPageTurns ?: false
	readerWebContentsDebuggingEnabled = normalized.webContentsDebuggingEnabled ?: false
}

fun PreferenceManager.readerBookSettings(bookId: String): ReaderSettings? {
	val key = readerBookSettingsKey(bookId) ?: return null
	return decodeReaderBookSettings(readerBookSettingsJson)[key]
}

fun PreferenceManager.readerSettingsForBook(bookId: String): ReaderSettings {
	val defaults = readerDefaultSettings()
	val override = readerBookSettings(bookId) ?: return defaults
	return defaults.withReaderSettingsOverride(override).normalizedReaderSettings()
}

fun PreferenceManager.setReaderBookSettings(bookId: String, settings: ReaderSettings) {
	val key = readerBookSettingsKey(bookId) ?: return
	val nextSettings = decodeReaderBookSettings(readerBookSettingsJson).toMutableMap()
	nextSettings[key] = settings.normalizedReaderOverrideSettings()
	readerBookSettingsJson = encodeReaderBookSettings(nextSettings)
}

fun PreferenceManager.clearReaderBookSettings(bookId: String) {
	val key = readerBookSettingsKey(bookId) ?: return
	val nextSettings = decodeReaderBookSettings(readerBookSettingsJson).toMutableMap()
	nextSettings.remove(key)
	readerBookSettingsJson = encodeReaderBookSettings(nextSettings)
}

private fun readerBookSettingsKey(bookId: String): String? =
	bookId.trim().takeIf { it.isNotEmpty() }

private fun ReaderSettings.withReaderSettingsOverride(override: ReaderSettings): ReaderSettings =
	copy(
		fontFamily = override.fontFamily ?: fontFamily,
		fontSource = override.fontSource ?: fontSource,
		customFontFamily = override.customFontFamily ?: customFontFamily,
		customFontUrl = override.customFontUrl ?: customFontUrl,
		fontSizePercent = override.fontSizePercent ?: fontSizePercent,
		lineHeight = override.lineHeight ?: lineHeight,
		paragraphSpacingPercent = override.paragraphSpacingPercent ?: paragraphSpacingPercent,
		marginPercent = override.marginPercent ?: marginPercent,
		dimOverlayPercent = override.dimOverlayPercent ?: dimOverlayPercent,
		colorFilterEnabled = override.colorFilterEnabled ?: colorFilterEnabled,
		colorFilterArgb = override.colorFilterArgb ?: colorFilterArgb,
		colorFilterMode = override.colorFilterMode ?: colorFilterMode,
		grayscaleEnabled = override.grayscaleEnabled ?: grayscaleEnabled,
		invertedColors = override.invertedColors ?: invertedColors,
		orientation = override.orientation ?: orientation,
		theme = override.theme ?: theme,
		direction = override.direction ?: direction,
		flowMode = override.flowMode ?: flowMode,
		paged = override.paged ?: paged,
		tapZone = override.tapZone ?: tapZone,
		smallerTapZone = override.smallerTapZone ?: smallerTapZone,
		showTapZones = override.showTapZones ?: showTapZones,
		pdfFitMode = override.pdfFitMode ?: pdfFitMode,
		pdfCropBorders = override.pdfCropBorders ?: pdfCropBorders,
		pdfPageGapPercent = override.pdfPageGapPercent ?: pdfPageGapPercent,
		publisherStyles = override.publisherStyles ?: publisherStyles,
		fullscreen = override.fullscreen ?: fullscreen,
		keepScreenOn = override.keepScreenOn ?: keepScreenOn,
		readaloudSyncEnabled = override.readaloudSyncEnabled ?: readaloudSyncEnabled,
		volumeKeyPageTurns = override.volumeKeyPageTurns ?: volumeKeyPageTurns,
		webContentsDebuggingEnabled = override.webContentsDebuggingEnabled ?: webContentsDebuggingEnabled
	)

private fun ReaderSettings.normalizedReaderOverrideSettings(): ReaderSettings {
	val normalized = defaultReaderSettings()
		.withReaderSettingsOverride(this)
		.normalizedReaderSettings()
	return ReaderSettings(
		fontFamily = if (fontFamily != null) normalized.fontFamily else null,
		fontSource = if (fontSource != null) normalized.fontSource else null,
		customFontFamily = if (customFontFamily != null) normalized.customFontFamily else null,
		customFontUrl = if (customFontUrl != null) normalized.customFontUrl else null,
		fontSizePercent = if (fontSizePercent != null) normalized.fontSizePercent else null,
		lineHeight = if (lineHeight != null) normalized.lineHeight else null,
		paragraphSpacingPercent = if (paragraphSpacingPercent != null) normalized.paragraphSpacingPercent else null,
		marginPercent = if (marginPercent != null) normalized.marginPercent else null,
		dimOverlayPercent = if (dimOverlayPercent != null) normalized.dimOverlayPercent else null,
		colorFilterEnabled = if (colorFilterEnabled != null) normalized.colorFilterEnabled else null,
		colorFilterArgb = if (colorFilterArgb != null) normalized.colorFilterArgb else null,
		colorFilterMode = if (colorFilterMode != null) normalized.colorFilterMode else null,
		grayscaleEnabled = if (grayscaleEnabled != null) normalized.grayscaleEnabled else null,
		invertedColors = if (invertedColors != null) normalized.invertedColors else null,
		orientation = if (orientation != null) normalized.orientation else null,
		theme = if (theme != null) normalized.theme else null,
		direction = if (direction != null) normalized.direction else null,
		flowMode = if (flowMode != null) normalized.flowMode else null,
		paged = if (paged != null) normalized.paged else null,
		tapZone = if (tapZone != null) normalized.tapZone else null,
		smallerTapZone = if (smallerTapZone != null) normalized.smallerTapZone else null,
		showTapZones = if (showTapZones != null) normalized.showTapZones else null,
		pdfFitMode = if (pdfFitMode != null) normalized.pdfFitMode else null,
		pdfCropBorders = if (pdfCropBorders != null) normalized.pdfCropBorders else null,
		pdfPageGapPercent = if (pdfPageGapPercent != null) normalized.pdfPageGapPercent else null,
		publisherStyles = if (publisherStyles != null) normalized.publisherStyles else null,
		fullscreen = if (fullscreen != null) normalized.fullscreen else null,
		keepScreenOn = if (keepScreenOn != null) normalized.keepScreenOn else null,
		readaloudSyncEnabled = if (readaloudSyncEnabled != null) normalized.readaloudSyncEnabled else null,
		volumeKeyPageTurns = if (volumeKeyPageTurns != null) normalized.volumeKeyPageTurns else null,
		webContentsDebuggingEnabled = if (webContentsDebuggingEnabled != null) {
			normalized.webContentsDebuggingEnabled
		} else {
			null
		}
	)
}

private fun decodeReaderBookSettings(json: String): Map<String, ReaderSettings> =
	if (json.isBlank()) {
		emptyMap()
	} else {
		runCatching {
			val root = ReaderPreferenceSettingsJson.parseToJsonElement(json).jsonObject
			val books = root["books"]?.jsonObject ?: root
			books.mapNotNull { (bookId, value) ->
				val key = readerBookSettingsKey(bookId) ?: return@mapNotNull null
				val settings = runCatching { value.jsonObject.toReaderSettings() }.getOrNull()
					?: return@mapNotNull null
				key to settings
			}.toMap()
		}.getOrDefault(emptyMap())
	}

private fun encodeReaderBookSettings(settingsByBook: Map<String, ReaderSettings>): String =
	if (settingsByBook.isEmpty()) {
		""
	} else {
		buildJsonObject {
			put("version", 1)
			put(
				"books",
				buildJsonObject {
					settingsByBook.toSortedMap().forEach { (bookId, settings) ->
						put(bookId, settings.toJsonObject())
					}
				}
			)
		}.toString()
	}

private fun JsonObject.toReaderSettings(): ReaderSettings =
	ReaderSettings(
		fontFamily = stringValue("fontFamily"),
		fontSource = stringValue("fontSource"),
		customFontFamily = stringValue("customFontFamily"),
		customFontUrl = stringValue("customFontUrl"),
		fontSizePercent = intValue("fontSizePercent"),
		lineHeight = doubleValue("lineHeight"),
		paragraphSpacingPercent = intValue("paragraphSpacingPercent"),
		marginPercent = intValue("marginPercent"),
		dimOverlayPercent = intValue("dimOverlayPercent"),
		colorFilterEnabled = booleanValue("colorFilterEnabled"),
		colorFilterArgb = intValue("colorFilterArgb"),
		colorFilterMode = stringValue("colorFilterMode"),
		grayscaleEnabled = booleanValue("grayscaleEnabled"),
		invertedColors = booleanValue("invertedColors"),
		orientation = stringValue("orientation"),
		theme = stringValue("theme"),
		direction = stringValue("direction"),
		flowMode = stringValue("flowMode"),
		paged = booleanValue("paged"),
		tapZone = stringValue("tapZone"),
		smallerTapZone = booleanValue("smallerTapZone"),
		showTapZones = booleanValue("showTapZones"),
		pdfFitMode = stringValue("pdfFitMode"),
		pdfCropBorders = booleanValue("pdfCropBorders"),
		pdfPageGapPercent = intValue("pdfPageGapPercent"),
		publisherStyles = booleanValue("publisherStyles"),
		fullscreen = booleanValue("fullscreen"),
		keepScreenOn = booleanValue("keepScreenOn"),
		readaloudSyncEnabled = booleanValue("readaloudSyncEnabled"),
		volumeKeyPageTurns = booleanValue("volumeKeyPageTurns"),
		webContentsDebuggingEnabled = booleanValue("webContentsDebuggingEnabled")
	)

private fun ReaderSettings.toJsonObject(): JsonObject =
	buildJsonObject {
		fontFamily?.let { put("fontFamily", it) }
		fontSource?.let { put("fontSource", it) }
		customFontFamily?.let { put("customFontFamily", it) }
		customFontUrl?.let { put("customFontUrl", it) }
		fontSizePercent?.let { put("fontSizePercent", it) }
		lineHeight?.let { put("lineHeight", it) }
		paragraphSpacingPercent?.let { put("paragraphSpacingPercent", it) }
		marginPercent?.let { put("marginPercent", it) }
		dimOverlayPercent?.let { put("dimOverlayPercent", it) }
		colorFilterEnabled?.let { put("colorFilterEnabled", it) }
		colorFilterArgb?.let { put("colorFilterArgb", it) }
		colorFilterMode?.let { put("colorFilterMode", it) }
		grayscaleEnabled?.let { put("grayscaleEnabled", it) }
		invertedColors?.let { put("invertedColors", it) }
		orientation?.let { put("orientation", it) }
		theme?.let { put("theme", it) }
		direction?.let { put("direction", it) }
		flowMode?.let { put("flowMode", it) }
		paged?.let { put("paged", it) }
		tapZone?.let { put("tapZone", it) }
		smallerTapZone?.let { put("smallerTapZone", it) }
		showTapZones?.let { put("showTapZones", it) }
		pdfFitMode?.let { put("pdfFitMode", it) }
		pdfCropBorders?.let { put("pdfCropBorders", it) }
		pdfPageGapPercent?.let { put("pdfPageGapPercent", it) }
		publisherStyles?.let { put("publisherStyles", it) }
		fullscreen?.let { put("fullscreen", it) }
		keepScreenOn?.let { put("keepScreenOn", it) }
		readaloudSyncEnabled?.let { put("readaloudSyncEnabled", it) }
		volumeKeyPageTurns?.let { put("volumeKeyPageTurns", it) }
		webContentsDebuggingEnabled?.let { put("webContentsDebuggingEnabled", it) }
	}

private fun JsonObject.stringValue(key: String): String? =
	get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.intValue(key: String): Int? =
	get(key)?.jsonPrimitive?.intOrNull

private fun JsonObject.doubleValue(key: String): Double? =
	get(key)?.jsonPrimitive?.doubleOrNull

private fun JsonObject.booleanValue(key: String): Boolean? =
	get(key)?.jsonPrimitive?.booleanOrNull
