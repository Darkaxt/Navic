package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val ReaderBridgeJson = Json {
	ignoreUnknownKeys = true
	isLenient = true
}

data class ReaderLocator(
	val href: String? = null,
	val cfi: String? = null,
	val progress: Double? = null,
	val pageIndex: Int? = null,
	val pageCount: Int? = null
)

data class ReaderOverlayFragment(
	val resourceHref: String,
	val fragmentId: String? = null,
	val textHref: String? = null,
	val clipBeginSeconds: Double? = null,
	val clipEndSeconds: Double? = null,
	val label: String? = null
)

data class ReaderSearchResult(
	val id: String,
	val cfi: String? = null,
	val href: String? = null,
	val excerpt: String? = null,
	val sectionTitle: String? = null
)

data class ReaderTocItem(
	val id: String,
	val title: String,
	val href: String? = null,
	val level: Int = 0
)

data class ReaderSettings(
	val fontFamily: String? = null,
	val fontSource: String? = null,
	val customFontFamily: String? = null,
	val customFontUrl: String? = null,
	val fontSizePercent: Int? = null,
	val lineHeight: Double? = null,
	val paragraphSpacingPercent: Int? = null,
	val marginPercent: Int? = null,
	val dimOverlayPercent: Int? = null,
	val orientation: String? = null,
	val theme: String? = null,
	val direction: String? = null,
	val flowMode: String? = null,
	val paged: Boolean? = null,
	val tapZone: String? = null,
	val smallerTapZone: Boolean? = null,
	val showTapZones: Boolean? = null,
	val nativeTapZones: Boolean? = null,
	val pdfFitMode: String? = null,
	val pdfCropBorders: Boolean? = null,
	val pdfPageGapPercent: Int? = null,
	val publisherStyles: Boolean? = null,
	val fullscreen: Boolean? = null,
	val keepScreenOn: Boolean? = null,
	val readaloudSyncEnabled: Boolean? = null,
	val volumeKeyPageTurns: Boolean? = null,
	val webContentsDebuggingEnabled: Boolean? = null
)

enum class ReaderPublicationKind {
	Ebook,
	Readaloud
}

enum class ReaderPublicationFormat {
	Epub,
	Pdf
}

sealed interface ReaderBridgeCommand {
	val type: String

	fun toJsonObject(): JsonObject

	data class OpenPublication(
		val url: String,
		val mediaOverlayEnabled: Boolean = false,
		val externalShellCover: Boolean = false,
		val startLocator: ReaderLocator? = null,
		val settings: ReaderSettings? = null
	) : ReaderBridgeCommand {
		override val type: String = "openPublication"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("url", url)
				put("mediaOverlayEnabled", mediaOverlayEnabled)
				put("externalShellCover", externalShellCover)
				startLocator?.let { put("startLocator", it.toJsonObject()) }
				settings?.let { put("settings", it.toJsonObject()) }
			}
	}

	data class GoToCfi(val cfi: String) : ReaderBridgeCommand {
		override val type: String = "goToCfi"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("cfi", cfi)
			}
	}

	data class GoToHref(val href: String) : ReaderBridgeCommand {
		override val type: String = "goToHref"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("href", href)
			}
	}

	data class GoToProgress(val progress: Double) : ReaderBridgeCommand {
		override val type: String = "goToProgress"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("progress", normalizedProgress)
			}

		private val normalizedProgress: Double
			get() = progress.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
	}

	data object NextPage : ReaderBridgeCommand {
		override val type: String = "nextPage"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
			}
	}

	data object PreviousPage : ReaderBridgeCommand {
		override val type: String = "previousPage"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
			}
	}

	data class ApplyHighlight(
		val id: String,
		val cfi: String,
		val color: String? = null,
		val note: String? = null
	) : ReaderBridgeCommand {
		override val type: String = "applyHighlight"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("id", id)
				put("cfi", cfi)
				color?.let { put("color", it) }
				note?.let { put("note", it) }
			}
	}

	data class ApplyHighlights(
		val highlights: List<ReaderAnnotation>
	) : ReaderBridgeCommand {
		override val type: String = "applyHighlights"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put(
					"highlights",
					buildJsonArray {
						highlights.forEach { annotation -> add(annotation.toHighlightJsonObject()) }
					}
				)
			}
	}

	data class ApplyOverlayFragment(
		val fragment: ReaderOverlayFragment
	) : ReaderBridgeCommand {
		override val type: String = "applyOverlayFragment"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("fragment", fragment.toJsonObject())
			}
	}

	data object ClearOverlay : ReaderBridgeCommand {
		override val type: String = "clearOverlay"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
			}
	}

	data class ApplySettings(val settings: ReaderSettings) : ReaderBridgeCommand {
		override val type: String = "applySettings"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("settings", settings.toJsonObject())
			}
	}

	data class Search(val query: String) : ReaderBridgeCommand {
		override val type: String = "search"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("query", query)
			}
	}
}

sealed interface ReaderBridgeEvent {
	data object Ready : ReaderBridgeEvent
	data object PublicationReady : ReaderBridgeEvent
	data object CenterTap : ReaderBridgeEvent
	data object ContentTapHandled : ReaderBridgeEvent
	data class LocationChanged(
		val locator: ReaderLocator,
		val tocTitle: String? = null
	) : ReaderBridgeEvent
	data class CfiChanged(val cfi: String) : ReaderBridgeEvent
	data class TocItemChanged(
		val href: String? = null,
		val title: String? = null
	) : ReaderBridgeEvent
	data class SelectionChanged(
		val text: String? = null,
		val cfi: String? = null,
		val href: String? = null
	) : ReaderBridgeEvent
	data class OverlayFragmentActive(val fragment: ReaderOverlayFragment) : ReaderBridgeEvent
	data class OverlayFragmentInactive(val fragmentId: String? = null) : ReaderBridgeEvent
	data class SearchResults(
		val query: String,
		val results: List<ReaderSearchResult>
	) : ReaderBridgeEvent
	data class Toc(val items: List<ReaderTocItem>) : ReaderBridgeEvent
	data class Error(val message: String, val code: String? = null) : ReaderBridgeEvent
}

fun ReaderBridgeCommand.toJavaScript(): String =
	"window.NavicReaderBridge.dispatch(${ReaderBridgeJson.encodeToString(JsonObject.serializer(), toJsonObject())});"

fun decodeReaderBridgeEvent(message: String): ReaderBridgeEvent? =
	runCatching {
		val json = ReaderBridgeJson.parseToJsonElement(message).jsonObject
		when (json.stringValue("type")) {
			"ready" -> ReaderBridgeEvent.Ready
			"publicationReady" -> ReaderBridgeEvent.PublicationReady
			"readerCenterTap" -> ReaderBridgeEvent.CenterTap
			"readerContentTapHandled" -> ReaderBridgeEvent.ContentTapHandled
			"locationChanged" -> ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(
					href = json.stringValue("href"),
					cfi = json.stringValue("cfi"),
					progress = json.doubleValue("progress"),
					pageIndex = json.intValue("pageIndex"),
					pageCount = json.intValue("pageCount")
				),
				tocTitle = json.stringValue("tocTitle")
			)
			"cfiChanged" -> json.stringValue("cfi")?.let(ReaderBridgeEvent::CfiChanged)
			"tocItemChanged" -> ReaderBridgeEvent.TocItemChanged(
				href = json.stringValue("href"),
				title = json.stringValue("title")
			)
			"selectionChanged" -> ReaderBridgeEvent.SelectionChanged(
				text = json.stringValue("text"),
				cfi = json.stringValue("cfi"),
				href = json.stringValue("href")
			)
			"overlayFragmentActive" -> json.toOverlayFragment()
				?.let(ReaderBridgeEvent::OverlayFragmentActive)
			"overlayFragmentInactive" -> ReaderBridgeEvent.OverlayFragmentInactive(
				fragmentId = json.stringValue("fragmentId")
			)
			"searchResults" -> ReaderBridgeEvent.SearchResults(
				query = json.stringValue("query").orEmpty(),
				results = (json["results"] as? JsonArray).orEmpty()
					.mapNotNull { (it as? JsonObject)?.toSearchResult() }
			)
			"toc" -> ReaderBridgeEvent.Toc(
				items = (json["items"] as? JsonArray).orEmpty()
					.mapIndexedNotNull { index, item -> (item as? JsonObject)?.toTocItem(index) }
			)
			"error" -> json.stringValue("message")?.let { message ->
				ReaderBridgeEvent.Error(
					message = message,
					code = json.stringValue("code")
				)
			}
			else -> null
		}
	}.getOrNull()

private fun ReaderLocator.toJsonObject(): JsonObject =
	buildJsonObject {
		href?.let { put("href", it) }
		cfi?.let { put("cfi", it) }
		progress?.let { put("progress", it) }
		pageIndex?.let { put("pageIndex", it) }
		pageCount?.let { put("pageCount", it) }
	}

private fun ReaderOverlayFragment.toJsonObject(): JsonObject =
	buildJsonObject {
		put("resourceHref", resourceHref)
		fragmentId?.let { put("fragmentId", it) }
		textHref?.let { put("textHref", it) }
		clipBeginSeconds?.let { put("clipBeginSeconds", it) }
		clipEndSeconds?.let { put("clipEndSeconds", it) }
		label?.let { put("label", it) }
	}

private fun ReaderAnnotation.toHighlightJsonObject(): JsonObject =
	buildJsonObject {
		put("id", id)
		put("cfi", cfi)
		put("color", color)
		note?.let { put("note", it) }
	}

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
		orientation?.let { put("orientation", it) }
		theme?.let { put("theme", it) }
		direction?.let { put("direction", it) }
		flowMode?.let { put("flowMode", it) }
		paged?.let { put("paged", it) }
		tapZone?.let { put("tapZone", it) }
		smallerTapZone?.let { put("smallerTapZone", it) }
		showTapZones?.let { put("showTapZones", it) }
		nativeTapZones?.let { put("nativeTapZones", it) }
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

private fun JsonObject.toOverlayFragment(): ReaderOverlayFragment? {
	val resourceHref = stringValue("resourceHref") ?: return null
	return ReaderOverlayFragment(
		resourceHref = resourceHref,
		fragmentId = stringValue("fragmentId"),
		textHref = stringValue("textHref"),
		clipBeginSeconds = doubleValue("clipBeginSeconds"),
		clipEndSeconds = doubleValue("clipEndSeconds"),
		label = stringValue("label")
	)
}

private fun JsonObject.toSearchResult(): ReaderSearchResult? {
	val id = stringValue("id") ?: cfiOrHrefSearchResultId() ?: return null
	return ReaderSearchResult(
		id = id,
		cfi = stringValue("cfi"),
		href = stringValue("href"),
		excerpt = stringValue("excerpt"),
		sectionTitle = stringValue("sectionTitle")
	)
}

private fun JsonObject.toTocItem(index: Int): ReaderTocItem? {
	val title = stringValue("title") ?: stringValue("label") ?: stringValue("href") ?: return null
	val href = stringValue("href")
	val id = stringValue("id")
		?: listOfNotNull(href, title).joinToString(separator = "|").takeIf { it.isNotEmpty() }
		?: "toc-$index"
	return ReaderTocItem(
		id = id,
		title = title,
		href = href,
		level = intValue("level")?.coerceAtLeast(0) ?: 0
	)
}

private fun JsonObject.cfiOrHrefSearchResultId(): String? =
	listOfNotNull(stringValue("cfi"), stringValue("href"))
		.joinToString(separator = "|")
		.takeIf { it.isNotEmpty() }

private fun Map<String, JsonElement>.stringValue(key: String): String? =
	(entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonPrimitive)
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun Map<String, JsonElement>.doubleValue(key: String): Double? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }
		?.value
		?.jsonPrimitive
		?.doubleOrNull

private fun Map<String, JsonElement>.intValue(key: String): Int? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }
		?.value
		?.jsonPrimitive
		?.intOrNull
