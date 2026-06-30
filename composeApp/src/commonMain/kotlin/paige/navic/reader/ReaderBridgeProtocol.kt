package paige.navic.reader

// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:627-879
// (callback catalog, including translateText at 864)
// tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194 (relocation)
// :216-327 (link/image taxonomy)
// :335-397 (annotations)

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
	val pageCount: Int? = null,
	val chapterProgress: Double? = null,
	val chapterPageIndex: Int? = null,
	val chapterPageCount: Int? = null,
	val rangeCfi: String? = null,
	val reason: String? = null,
	val fraction: Double? = null,
	val size: Double? = null,
	val tocItemLabel: String? = null,
	val pageItemLabel: String? = null
)

data class ReaderPaginationProfileStatus(
	val status: String = "",
	val fingerprint: String? = null,
	val completedSections: Int? = null,
	val totalSections: Int? = null,
	val pageCount: Int? = null,
	val message: String? = null
) {
	val progressFraction: Float?
		get() {
			val total = totalSections?.takeIf { it > 0 } ?: return null
			val completed = completedSections?.coerceIn(0, total) ?: 0
			return completed.toFloat() / total.toFloat()
		}

	val label: String
		get() = when (status) {
			"measuring" -> {
				val completed = completedSections
				val total = totalSections
				if (completed != null && total != null && total > 0) {
					"Measuring pages $completed/$total"
				} else {
					"Measuring pages"
				}
			}
			"cached", "ready" -> pageCount?.let { "Pages ready: $it" } ?: "Pages ready"
			"failed" -> "Page measurement failed"
			else -> "Page measurement"
		}
}

data class ReaderOverlayFragment(
	val resourceHref: String,
	val fragmentId: String? = null,
	val textHref: String? = null,
	val clipBeginSeconds: Double? = null,
	val clipEndSeconds: Double? = null,
	val textStart: Int? = null,
	val textEnd: Int? = null,
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
	val fontWeight: Double? = null,
	val letterSpacing: Double? = null,
	val wordSpacing: Double? = null,
	val sideMargin: Double? = null,
	val topMargin: Double? = null,
	val bottomMargin: Double? = null,
	val indent: Double? = null,
	val headingFontSize: Double? = null,
	val maxColumnCount: Int? = null,
	val columnThreshold: Double? = null,
	val dimOverlayPercent: Int? = null,
	val colorFilterEnabled: Boolean? = null,
	val colorFilterArgb: Int? = null,
	val colorFilterMode: String? = null,
	val grayscaleEnabled: Boolean? = null,
	val invertedColors: Boolean? = null,
	val orientation: String? = null,
	val theme: String? = null,
	val direction: String? = null,
	val navBarType: String? = null,
	val flowMode: String? = null,
	val paged: Boolean? = null,
	val tapZone: String? = null,
	val tapZoneInvertMode: String? = null,
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
	Pdf,
	Azw3,
	Mobi,
	Cbz,
	Fb2
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

	data class GoToChapterProgress(
		val href: String,
		val progress: Double,
		val chapterPageIndex: Int? = null,
		val chapterPageCount: Int? = null
	) : ReaderBridgeCommand {
		override val type: String = "goToChapterProgress"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("href", href)
				put("progress", normalizedProgress)
				normalizedChapterPageIndex?.let { put("chapterPageIndex", it) }
				normalizedChapterPageCount?.let { put("chapterPageCount", it) }
			}

		private val normalizedProgress: Double
			get() = progress.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0

		private val normalizedChapterPageIndex: Int?
			get() = chapterPageIndex?.takeIf { it >= 0 }

		private val normalizedChapterPageCount: Int?
			get() = chapterPageCount?.takeIf { it > 0 }
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

	data object HistoryBack : ReaderBridgeCommand {
		override val type: String = "historyBack"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
			}
	}

	data object HistoryForward : ReaderBridgeCommand {
		override val type: String = "historyForward"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
			}
	}

	data class PreviewPageDrag(
		val deltaX: Double,
		val deltaY: Double = 0.0,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null,
		val phase: ReaderPageDragPreviewPhase = ReaderPageDragPreviewPhase.Update
	) : ReaderBridgeCommand {
		override val type: String = "previewPageDrag"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("deltaX", deltaX.takeIf(Double::isFinite) ?: 0.0)
				put("deltaY", deltaY.takeIf(Double::isFinite) ?: 0.0)
				viewWidth?.takeIf(Double::isFinite)?.let { put("viewWidth", it) }
				viewHeight?.takeIf(Double::isFinite)?.let { put("viewHeight", it) }
				put(
					"phase",
					when (phase) {
						ReaderPageDragPreviewPhase.Update -> "update"
						ReaderPageDragPreviewPhase.Release -> "release"
						ReaderPageDragPreviewPhase.Cancel -> "cancel"
					}
				)
			}
	}

	data class ScrollViewport(val direction: ReaderViewportScrollDirection) : ReaderBridgeCommand {
		override val type: String = "scrollViewport"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put(
					"direction",
					when (direction) {
						ReaderViewportScrollDirection.Up -> "up"
						ReaderViewportScrollDirection.Down -> "down"
					}
				)
			}
	}

	data class ContentLongPressAt(
		val x: Double,
		val y: Double,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null
	) : ReaderBridgeCommand {
		override val type: String = "contentLongPressAt"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("x", x)
				put("y", y)
				viewWidth?.let { put("viewWidth", it) }
				viewHeight?.let { put("viewHeight", it) }
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

	data object ClearSearch : ReaderBridgeCommand {
		override val type: String = "clearSearch"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
			}
	}
}

sealed interface ReaderBridgeEvent {
	data object Ready : ReaderBridgeEvent
	data object PublicationReady : ReaderBridgeEvent
	data object CenterTap : ReaderBridgeEvent
	data class ContentTapHandled(
		val claim: ReaderContentActionClaim = ReaderContentActionClaim()
	) : ReaderBridgeEvent {
		constructor(action: ReaderContentAction) : this(ReaderContentActionClaim(action = action))
		val action: ReaderContentAction
			get() = claim.action
	}
	data class InternalLinkRequested(
		val href: String? = null,
		val prevented: Boolean = false,
		val source: String? = null
	) : ReaderBridgeEvent
	data class ExternalLink(
		val href: String? = null,
		val anchorHref: String? = null
	) : ReaderBridgeEvent
	data class LocationChanged(
		val locator: ReaderLocator,
		val tocTitle: String? = null
	) : ReaderBridgeEvent
	data class CfiChanged(val cfi: String) : ReaderBridgeEvent
	data class TocItemChanged(
		val href: String? = null,
		val title: String? = null
	) : ReaderBridgeEvent
	data class PaginationProfileStatusChanged(
		val profile: ReaderPaginationProfileStatus
	) : ReaderBridgeEvent
	data class SelectionChanged(
		val text: String? = null,
		val cfi: String? = null,
		val href: String? = null,
		val footnote: Boolean? = null,
		val contextText: String? = null,
		val posLeft: Double? = null,
		val posTop: Double? = null,
		val posRight: Double? = null,
		val posBottom: Double? = null
	) : ReaderBridgeEvent
	data object SelectionCleared : ReaderBridgeEvent
	data class AnnotationClick(
		val value: String? = null,
		val index: Int? = null,
		val rangeCfi: String? = null
	) : ReaderBridgeEvent
	data class AnnotationDrawn(
		val value: String? = null,
		val index: Int? = null,
		val rangeCfi: String? = null
	) : ReaderBridgeEvent
	data class OverlayCreated(
		val index: Int? = null
	) : ReaderBridgeEvent
	data class LoadDoc(
		val index: Int? = null,
		val href: String? = null,
		val title: String? = null,
		val sectionId: String? = null
	) : ReaderBridgeEvent
	data class PushState(
		val canGoBack: Boolean = false,
		val canGoForward: Boolean = false
	) : ReaderBridgeEvent
	data class FootnoteOpen(
		val href: String? = null,
		val text: String? = null,
		val noteType: String? = null,
		val hidden: Boolean = false
	) : ReaderBridgeEvent
	data object FootnoteClose : ReaderBridgeEvent
	data class PullUp(val source: String? = null) : ReaderBridgeEvent
	data class VisibleTextRange(
		val textHref: String,
		val visibleStart: Int,
		val visibleEnd: Int,
		val rangeCfi: String? = null,
		val source: String? = null
	) : ReaderBridgeEvent
	data class OverlayFragmentActive(val fragment: ReaderOverlayFragment) : ReaderBridgeEvent
	data class OverlayFragmentInactive(val fragmentId: String? = null) : ReaderBridgeEvent
	data class SearchResults(
		val query: String,
		val results: List<ReaderSearchResult>,
		val progress: Double? = null,
		val complete: Boolean = false
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
			"readerContentTapHandled" -> ReaderBridgeEvent.ContentTapHandled(json.toContentActionClaim())
			"internalLink" -> ReaderBridgeEvent.InternalLinkRequested(
				href = json.stringValue("href"),
				prevented = json.booleanValue("prevented") ?: false,
				source = json.stringValue("source")
			)
			"externalLink" -> ReaderBridgeEvent.ExternalLink(
				href = json.stringValue("href"),
				anchorHref = json.stringValue("anchorHref")
			)
			"locationChanged" -> ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(
					href = json.stringValue("href"),
					cfi = json.stringValue("cfi"),
					progress = json.doubleValue("progress"),
					pageIndex = json.intValue("pageIndex"),
					pageCount = json.intValue("pageCount"),
					chapterProgress = json.doubleValue("chapterProgress"),
					chapterPageIndex = json.intValue("chapterPageIndex"),
					chapterPageCount = json.intValue("chapterPageCount"),
					rangeCfi = json.stringValue("rangeCfi"),
					reason = json.stringValue("reason"),
					fraction = json.doubleValue("fraction"),
					size = json.doubleValue("size"),
					tocItemLabel = json.stringValue("tocItemLabel"),
					pageItemLabel = json.stringValue("pageItemLabel")
				),
				tocTitle = json.stringValue("tocTitle")
			)
			"cfiChanged" -> json.stringValue("cfi")?.let(ReaderBridgeEvent::CfiChanged)
			"tocItemChanged" -> ReaderBridgeEvent.TocItemChanged(
				href = json.stringValue("href"),
				title = json.stringValue("title")
			)
			"paginationProfileStatus" -> ReaderBridgeEvent.PaginationProfileStatusChanged(
				ReaderPaginationProfileStatus(
					status = json.stringValue("status").orEmpty(),
					fingerprint = json.stringValue("fingerprint"),
					completedSections = json.intValue("completedSections"),
					totalSections = json.intValue("totalSections"),
					pageCount = json.intValue("pageCount"),
					message = json.stringValue("message")
				)
			)
			"selectionChanged" -> ReaderBridgeEvent.SelectionChanged(
				text = json.stringValue("text"),
				cfi = json.stringValue("cfi"),
				href = json.stringValue("href"),
				footnote = json.booleanValue("footnote"),
				contextText = json.stringValue("contextText"),
				posLeft = json["pos"]?.jsonObject?.doubleValue("left"),
				posTop = json["pos"]?.jsonObject?.doubleValue("top"),
				posRight = json["pos"]?.jsonObject?.doubleValue("right"),
				posBottom = json["pos"]?.jsonObject?.doubleValue("bottom")
			)
			"selectionCleared" -> ReaderBridgeEvent.SelectionCleared
			"annotationClick" -> ReaderBridgeEvent.AnnotationClick(
				value = json.stringValue("value"),
				index = json.intValue("index"),
				rangeCfi = json.stringValue("rangeCfi")
			)
			"annotationDrawn" -> ReaderBridgeEvent.AnnotationDrawn(
				value = json.stringValue("value"),
				index = json.intValue("index"),
				rangeCfi = json.stringValue("rangeCfi")
			)
			"overlayCreated" -> ReaderBridgeEvent.OverlayCreated(index = json.intValue("index"))
			"loadDoc" -> ReaderBridgeEvent.LoadDoc(
				index = json.intValue("index"),
				href = json.stringValue("href"),
				title = json.stringValue("title"),
				sectionId = json.stringValue("sectionId")
			)
			"pushState" -> ReaderBridgeEvent.PushState(
				canGoBack = json.booleanValue("canGoBack") ?: false,
				canGoForward = json.booleanValue("canGoForward") ?: false
			)
			"footnoteOpen" -> ReaderBridgeEvent.FootnoteOpen(
				href = json.stringValue("href"),
				text = json.stringValue("text"),
				noteType = json.stringValue("noteType"),
				hidden = json.booleanValue("hidden") ?: false
			)
			"footnoteClose" -> ReaderBridgeEvent.FootnoteClose
			"pullUp" -> ReaderBridgeEvent.PullUp(
				source = json.stringValue("source")
			)
			"visibleTextRange" -> json.toVisibleTextRange()
			"overlayFragmentActive" -> json.toOverlayFragment()
				?.let(ReaderBridgeEvent::OverlayFragmentActive)
			"overlayFragmentInactive" -> ReaderBridgeEvent.OverlayFragmentInactive(
				fragmentId = json.stringValue("fragmentId")
			)
			"searchResults" -> ReaderBridgeEvent.SearchResults(
				query = json.stringValue("query").orEmpty(),
				results = (json["results"] as? JsonArray).orEmpty()
					.mapNotNull { (it as? JsonObject)?.toSearchResult() },
				progress = json.doubleValue("progress") ?: json.doubleValue("process"),
				complete = json.booleanValue("complete")
					?: json.booleanValue("done")
					?: (json.doubleValue("process") == 1.0)
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

private fun JsonObject.toVisibleTextRange(): ReaderBridgeEvent.VisibleTextRange? {
	val textHref = stringValue("textHref") ?: stringValue("href") ?: return null
	val visibleStart = intValue("visibleStart") ?: intValue("start") ?: return null
	val visibleEnd = intValue("visibleEnd") ?: intValue("end") ?: return null
	return ReaderBridgeEvent.VisibleTextRange(
		textHref = textHref,
		visibleStart = minOf(visibleStart, visibleEnd),
		visibleEnd = maxOf(visibleStart, visibleEnd),
		rangeCfi = stringValue("rangeCfi"),
		source = stringValue("source") ?: stringValue("reason")
	)
}

private fun JsonObject.toContentActionClaim(): ReaderContentActionClaim {
	val source = stringValue("source")
	val src = stringValue("src") ?: stringValue("image") ?: stringValue("imageSrc")
	return ReaderContentActionClaim(
		action = readerContentActionFromBridgeValue(stringValue("action") ?: source),
		source = source,
		href = stringValue("href"),
		src = src,
		text = stringValue("text") ?: stringValue("alt") ?: stringValue("label"),
		cfi = stringValue("cfi"),
		x = doubleValue("x"),
		y = doubleValue("y")
	)
}

private fun readerContentActionFromBridgeValue(value: String?): ReaderContentAction =
	when (value?.trim()?.lowercase()) {
		"link", "link-touch", "external-link" -> ReaderContentAction.Link
		"image", "image-touch", "click-image" -> ReaderContentAction.Image
		"selection", "text-selection" -> ReaderContentAction.Selection
		"form", "form-control", "input", "editable" -> ReaderContentAction.FormControl
		"media", "media-touch", "media-anchor", "audio", "video" -> ReaderContentAction.MediaControl
		"annotation", "highlight", "note", "show-annotation" -> ReaderContentAction.Annotation
		"footnote", "noteref" -> ReaderContentAction.Footnote
		else -> ReaderContentAction.Generic
	}

private fun ReaderLocator.toJsonObject(): JsonObject =
	buildJsonObject {
		href?.let { put("href", it) }
		cfi?.let { put("cfi", it) }
		progress?.let { put("progress", it) }
		pageIndex?.let { put("pageIndex", it) }
		pageCount?.let { put("pageCount", it) }
		chapterProgress?.let { put("chapterProgress", it) }
		chapterPageIndex?.let { put("chapterPageIndex", it) }
		chapterPageCount?.let { put("chapterPageCount", it) }
		rangeCfi?.let { put("rangeCfi", it) }
		reason?.let { put("reason", it) }
		fraction?.let { put("fraction", it) }
		size?.let { put("size", it) }
		tocItemLabel?.let { put("tocItemLabel", it) }
		pageItemLabel?.let { put("pageItemLabel", it) }
	}

private fun ReaderOverlayFragment.toJsonObject(): JsonObject =
	buildJsonObject {
		put("resourceHref", resourceHref)
		fragmentId?.let { put("fragmentId", it) }
		textHref?.let { put("textHref", it) }
		clipBeginSeconds?.let { put("clipBeginSeconds", it) }
		clipEndSeconds?.let { put("clipEndSeconds", it) }
		textStart?.let { put("textStart", it) }
		textEnd?.let { put("textEnd", it) }
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
		fontWeight?.let { put("fontWeight", it) }
		letterSpacing?.let { put("letterSpacing", it) }
		wordSpacing?.let { put("wordSpacing", it) }
		sideMargin?.let { put("sideMargin", it) }
		topMargin?.let { put("topMargin", it) }
		bottomMargin?.let { put("bottomMargin", it) }
		indent?.let { put("indent", it) }
		headingFontSize?.let { put("headingFontSize", it) }
		maxColumnCount?.let { put("maxColumnCount", it) }
		columnThreshold?.let { put("columnThreshold", it) }
		dimOverlayPercent?.let { put("dimOverlayPercent", it) }
		colorFilterEnabled?.let { put("colorFilterEnabled", it) }
		colorFilterArgb?.let { put("colorFilterArgb", it) }
		colorFilterMode?.let { put("colorFilterMode", it) }
		grayscaleEnabled?.let { put("grayscaleEnabled", it) }
		invertedColors?.let { put("invertedColors", it) }
		orientation?.let { put("orientation", it) }
		theme?.let { put("theme", it) }
		direction?.let { put("direction", it) }
		navBarType?.let { put("navBarType", it) }
		flowMode?.let { put("flowMode", it) }
		paged?.let { put("paged", it) }
		tapZone?.let { put("tapZone", it) }
		tapZoneInvertMode?.let { put("tapZoneInvertMode", it) }
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
		textStart = intValue("textStart"),
		textEnd = intValue("textEnd"),
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

private fun Map<String, JsonElement>.booleanValue(key: String): Boolean? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }
		?.value
		?.jsonPrimitive
		?.booleanOrNull
