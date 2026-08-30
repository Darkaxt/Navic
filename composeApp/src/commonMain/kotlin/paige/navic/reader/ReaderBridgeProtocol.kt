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
import kotlinx.serialization.json.longOrNull
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

enum class ReaderOverlayCoordinateMode {
	CueV1DomUtf16,
	WordSyncV1ExtractedUtf8
}

enum class RawTextProvenanceStatus {
	Pending,
	Ready,
	Rejected
}

enum class RawTextProvenanceReason {
	ContentNotLoaded,
	InvalidDescriptor,
	SectionMismatch,
	SourceUnavailable,
	SourceTooLarge,
	SourceHashMismatch,
	InvalidUtf8,
	ExtractedHashMismatch,
	ExtractedLengthMismatch,
	TokenCountMismatch,
	TokenSequenceMismatch,
	DocumentChanged
}

data class ReaderRawTextProvenanceDescriptor(
	val id: String,
	val href: String,
	val spineIndex: Int,
	val sourceHash: String,
	val extractedTextHash: String,
	val byteLength: Int,
	val tokenCount: Int
) {
	init {
		require(id.isNotBlank() && id == id.trim())
		require(href.isNotBlank() && href == href.trim())
		require(spineIndex >= 0)
		require(sourceHash.matches(ReaderCanonicalSha256))
		require(extractedTextHash.matches(ReaderCanonicalSha256))
		require(byteLength >= 0)
		require(tokenCount >= 0)
	}
}

data class ReaderOverlayFragment(
	val resourceHref: String,
	val coordinateMode: ReaderOverlayCoordinateMode = ReaderOverlayCoordinateMode.CueV1DomUtf16,
	val overlayRequestId: Long? = null,
	val wordBoundarySequence: Long? = null,
	val fragmentId: String? = null,
	val textHref: String? = null,
	val clipBeginSeconds: Double? = null,
	val clipEndSeconds: Double? = null,
	val textStart: Int? = null,
	val textEnd: Int? = null,
	val textProgressEnd: Int? = null,
	val textProgressFraction: Double? = null,
	val spokenText: String? = null,
	val ebookText: String? = null,
	val nextTextHref: String? = null,
	val nextTextStart: Int? = null,
	val nextTextEnd: Int? = null,
	val nextEbookText: String? = null,
	val playbackSpeed: Float? = null,
	val label: String? = null,
	val rawProvenanceId: String? = null,
	val rawSpineIndex: Int? = null,
	val rawByteStart: Int? = null,
	val rawByteEnd: Int? = null,
	val rawProgressByteEnd: Int? = null,
	val rawProgressFraction: Double? = null
) {
	init {
		val rawFields = listOf(
			rawProvenanceId,
			rawSpineIndex,
			rawByteStart,
			rawByteEnd,
			rawProgressByteEnd,
			rawProgressFraction
		)
		when (coordinateMode) {
			ReaderOverlayCoordinateMode.CueV1DomUtf16 -> {
				require(rawFields.all { it == null })
				require(wordBoundarySequence == null)
			}
			ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8 -> {
				require(wordBoundarySequence?.let { it >= 0L } != false)
				require(textHref?.isNotBlank() == true && textHref == textHref.trim())
				require(rawProvenanceId?.isNotBlank() == true && rawProvenanceId == rawProvenanceId.trim())
				require(rawSpineIndex?.let { it >= 0 } == true)
				val start = requireNotNull(rawByteStart)
				val end = requireNotNull(rawByteEnd)
				require(start >= 0 && end > start)
				require(rawProgressByteEnd?.let { it in start..end } != false)
				require(rawProgressFraction?.let { it.isFinite() && it in 0.0..1.0 } != false)
				require(
					listOf(
						fragmentId,
						textStart,
						textEnd,
						textProgressEnd,
						textProgressFraction,
						ebookText,
						nextTextHref,
						nextTextStart,
						nextTextEnd,
						nextEbookText
					).all { it == null }
				)
			}
		}
	}
}

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
	val paperTextureEnabled: Boolean? = null,
	val pageEdgesEnabled: Boolean? = null,
	val paperStainsEnabled: Boolean? = null,
	val coverBackdropEnabled: Boolean? = null,
	val navBarType: String? = null,
	val flowMode: String? = null,
	val dragAnimationMode: String? = null,
	val pageBitmapQuality: String? = null,
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
	val whispersyncHighlightLeadMs: Int? = null,
	val whispersyncHighlightColorArgb: Int? = null,
	val whispersyncHighlightLoading: String? = null,
	val whispersyncHighlightStyle: String? = null,
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

internal const val ReaderUnboundFoliateSessionId = "foliate-unbound"

sealed interface ReaderBridgeCommand {
	val type: String

	fun toJsonObject(): JsonObject

	data class OpenPublication(
		val url: String,
		val foliateSessionId: String,
		val mediaOverlayEnabled: Boolean = false,
		val externalShellCover: Boolean = false,
		val suppressWebShellCover: Boolean = false,
		val nativeShellCoverTint: String? = null,
		val startLocator: ReaderLocator? = null,
		val settings: ReaderSettings? = null
	) : ReaderBridgeCommand {
		init {
			require(foliateSessionId.isNotBlank())
		}

		override val type: String = "openPublication"

		override fun toJsonObject(): JsonObject {
			check(foliateSessionId != ReaderUnboundFoliateSessionId)
			return buildJsonObject {
				put("type", type)
				put("url", url)
				put("foliateSessionId", foliateSessionId)
				put("mediaOverlayEnabled", mediaOverlayEnabled)
				put("externalShellCover", externalShellCover)
				put("suppressWebShellCover", suppressWebShellCover)
				nativeShellCoverTint?.let { put("nativeShellCoverTint", it) }
				startLocator?.let { put("startLocator", it.toJsonObject()) }
				settings?.let { put("settings", it.toJsonObject()) }
			}
		}
	}

	data class GoToLocator(
		val locator: ReaderLocator,
		val reason: String,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
		init {
			require(reason.isNotBlank())
		}

		override val type: String = "goToLocator"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("locator", locator.toJsonObject())
				put("reason", reason)
				causalSequence?.let { put("causalSequence", it) }
			}
	}

	data class GoToCfi(
		val cfi: String,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
		override val type: String = "goToCfi"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("cfi", cfi)
				causalSequence?.let { put("causalSequence", it) }
			}
	}

	data class GoToHref(
		val href: String,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
		override val type: String = "goToHref"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("href", href)
				causalSequence?.let { put("causalSequence", it) }
			}
	}

	data class GoToProgress(
		val progress: Double,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
		override val type: String = "goToProgress"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("progress", normalizedProgress)
				causalSequence?.let { put("causalSequence", it) }
			}

		private val normalizedProgress: Double
			get() = progress.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
	}

	data class GoToChapterProgress(
		val href: String,
		val progress: Double,
		val chapterPageIndex: Int? = null,
		val chapterPageCount: Int? = null,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
		override val type: String = "goToChapterProgress"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("href", href)
				put("progress", normalizedProgress)
				normalizedChapterPageIndex?.let { put("chapterPageIndex", it) }
				normalizedChapterPageCount?.let { put("chapterPageCount", it) }
				causalSequence?.let { put("causalSequence", it) }
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
			buildJsonObject { put("type", type) }
	}

	data class CausalNextPage(val causalSequence: Long) : ReaderBridgeCommand {
		override val type: String = "nextPage"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("causalSequence", causalSequence)
			}
	}

	data object PreviousPage : ReaderBridgeCommand {
		override val type: String = "previousPage"

		override fun toJsonObject(): JsonObject =
			buildJsonObject { put("type", type) }
	}

	data class CausalPreviousPage(val causalSequence: Long) : ReaderBridgeCommand {
		override val type: String = "previousPage"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("causalSequence", causalSequence)
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

	data class ScrollViewport(
		val direction: ReaderViewportScrollDirection,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
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
				causalSequence?.let { put("causalSequence", it) }
			}
	}

	data class ContentLongPressAt(
		val x: Double,
		val y: Double,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null,
		val selectText: Boolean = true,
		val causalSequence: Long? = null
	) : ReaderBridgeCommand {
		override val type: String = "contentLongPressAt"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("x", x)
				put("y", y)
				viewWidth?.let { put("viewWidth", it) }
				viewHeight?.let { put("viewHeight", it) }
				put("selectText", selectText)
				causalSequence?.let { put("causalSequence", it) }
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

	data class RequestVisibleTextRange(
		val source: String
	) : ReaderBridgeCommand {
		override val type: String = "requestVisibleTextRange"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("source", source)
			}
	}

	data class InstallRawTextProvenance(
		val descriptor: ReaderRawTextProvenanceDescriptor
	) : ReaderBridgeCommand {
		override val type: String = "installRawTextProvenance"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("descriptor", descriptor.toJsonObject())
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

	data class UpdateOverlayFragmentProgress(
		val fragment: ReaderOverlayFragment
	) : ReaderBridgeCommand {
		override val type: String = "updateOverlayFragmentProgress"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("fragment", fragment.toJsonObject())
			}
	}

	data class ReplaceWhispersyncCueMap(
		val presentation: ReaderWhispersyncCueMapPresentation
	) : ReaderBridgeCommand {
		override val type: String = "replaceWhispersyncCueMap"

		override fun toJsonObject(): JsonObject =
			presentation.toCueMapJsonObject(type)
	}

	data class CancelWhispersyncCueMapHold(
		val reason: ReaderWhispersyncCueMapHoldOutcome
	) : ReaderBridgeCommand {
		override val type: String = "cancelWhispersyncCueMapHold"

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("reason", reason.bridgeValue)
			}
	}

	data class ClearOverlayPresentation(
		val overlayRequestId: Long,
		val clearedThroughBoundarySequence: Long
	) : ReaderBridgeCommand {
		override val type: String = "clearOverlayPresentation"

		init {
			require(overlayRequestId >= 0L)
			require(clearedThroughBoundarySequence >= 0L)
		}

		override fun toJsonObject(): JsonObject =
			buildJsonObject {
				put("type", type)
				put("overlayRequestId", overlayRequestId)
				put("clearedThroughBoundarySequence", clearedThroughBoundarySequence)
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

data class ReaderBridgeDispatchCommand(
	val id: String,
	val command: ReaderBridgeCommand
) {
	init {
		require(id.isNotBlank()) { "Reader bridge command ID must not be blank." }
	}

	fun toJsonObject(): JsonObject =
		buildJsonObject {
			put("commandId", id)
			command.toJsonObject().forEach { (key, value) -> put(key, value) }
		}
}

sealed interface ReaderBridgeEvent {
	data object Ready : ReaderBridgeEvent
	data class CommandAcknowledged(val commandId: String) : ReaderBridgeEvent
	data class CommandFailed(val commandId: String) : ReaderBridgeEvent
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
		val foliateSessionId: String,
		val tocTitle: String? = null,
		val pageTurnSettleToken: String? = null,
		val pageTurnSettleSessionId: String? = null,
		val pageTurnSettleRasterGeneration: Long? = null,
		val pageTurnSettleTextureGeneration: Long? = null,
		val causalSequence: Long? = null,
		val destinationCommitIdentity: ReaderDestinationCommitIdentity? = null
	) : ReaderBridgeEvent {
		init {
			require(foliateSessionId.isNotBlank())
		}
	}
	data class DuplicatePageSuspected(
		val currentPageOrdinal: Int,
		val previousPageOrdinal: Int,
		val plainTextSame: Boolean,
		val locatorSame: Boolean
	) : ReaderBridgeEvent {
		init {
			require(currentPageOrdinal > 0)
			require(previousPageOrdinal > 0)
			require(currentPageOrdinal != previousPageOrdinal)
			require(plainTextSame)
		}
	}
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
		val source: String? = null,
		val rawProvenanceId: String? = null,
		val rawSpineIndex: Int? = null,
		val rawByteStart: Int? = null,
		val rawByteEnd: Int? = null,
		val causalSequence: Long? = null,
		val destinationCommitIdentity: ReaderDestinationCommitIdentity? = null
	) : ReaderBridgeEvent
	data class TextPoint(
		val textHref: String,
		val textOffset: Int,
		val rangeCfi: String? = null,
		val source: String? = null,
		val rawProvenanceId: String? = null,
		val rawByteOffset: Int? = null,
		val causalSequence: Long? = null,
		val destinationCommitIdentity: ReaderDestinationCommitIdentity? = null
	) : ReaderBridgeEvent
	data class WhispersyncCueMapRendered(
		val sourceOrdinalsInDomReadingOrder: List<Int>,
		val revisionDigest: String,
		val presentationGeneration: Long,
		val destinationCommitIdentity: ReaderDestinationCommitIdentity,
		val markerReceipts: List<ReaderWhispersyncCueMapMarkerReceipt> = emptyList()
	) : ReaderBridgeEvent
	data class WhispersyncCueMapSeekRequested(
		val sourceOrdinal: Int,
		val revisionDigest: String,
		val presentationGeneration: Long,
		val destinationCommitIdentity: ReaderDestinationCommitIdentity
	) : ReaderBridgeEvent
	data class WhispersyncCueMapHoldOutcome(
		val sourceOrdinal: Int,
		val revisionDigest: String,
		val presentationGeneration: Long,
		val outcome: ReaderWhispersyncCueMapHoldOutcome
	) : ReaderBridgeEvent
	data class RawTextProvenanceStatusChanged(
		val provenanceId: String,
		val status: RawTextProvenanceStatus,
		val reason: RawTextProvenanceReason? = null
	) : ReaderBridgeEvent {
		init {
			require(provenanceId.isNotBlank() && provenanceId == provenanceId.trim())
			when (status) {
				RawTextProvenanceStatus.Pending ->
					require(reason == null || reason == RawTextProvenanceReason.ContentNotLoaded)
				RawTextProvenanceStatus.Ready -> require(reason == null)
				RawTextProvenanceStatus.Rejected -> require(
					reason != null && reason != RawTextProvenanceReason.ContentNotLoaded
				)
			}
		}
	}
	data class OverlayFragmentActive(
		val fragment: ReaderOverlayFragment,
		val anchorReceipt: ReaderWhispersyncAnchorReceipt? = null
	) : ReaderBridgeEvent
	data class OverlayFragmentInactive(
		val fragmentId: String? = null,
		val overlayRequestId: Long? = null,
		val coordinateMode: ReaderOverlayCoordinateMode? = null,
		val reason: String? = null
	) : ReaderBridgeEvent
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

fun ReaderBridgeDispatchCommand.toJavaScript(): String =
	"window.NavicReaderBridge.dispatch(${ReaderBridgeJson.encodeToString(JsonObject.serializer(), toJsonObject())});"

enum class ReaderBridgeDecodeFailure {
	MalformedJson,
	NonObjectPayload,
	MissingType,
	UnknownType,
	InvalidPayload
}

sealed interface ReaderBridgeDecodeResult {
	data class Decoded(val event: ReaderBridgeEvent) : ReaderBridgeDecodeResult
	data class Rejected(
		val failure: ReaderBridgeDecodeFailure,
		val rawMessage: String
	) : ReaderBridgeDecodeResult
}

private const val ReaderCueV1CoordinateMode = "cue-v1-dom-utf16"
private const val ReaderWordSyncV1CoordinateMode = "wordsync-v1-extracted-utf8"
private const val ReaderRedactedBridgePayload = "[redacted-reader-bridge-payload]"
private val ReaderCanonicalSha256 = Regex("sha256:[0-9a-f]{64}")

private val ReaderBridgeEventTypes = setOf(
	"ready",
	"commandAck",
	"commandFailed",
	"publicationReady",
	"readerCenterTap",
	"readerContentTapHandled",
	"internalLink",
	"externalLink",
	"locationChanged",
	"duplicatePageSuspected",
	"cfiChanged",
	"tocItemChanged",
	"paginationProfileStatus",
	"selectionChanged",
	"selectionCleared",
	"annotationClick",
	"annotationDrawn",
	"overlayCreated",
	"loadDoc",
	"footnoteOpen",
	"footnoteClose",
	"pullUp",
	"visibleTextRange",
	"textPoint",
	"whispersyncCueMapRendered",
	"whispersyncCueMapSeekRequested",
	"whispersyncCueMapHoldOutcome",
	"rawTextProvenanceStatus",
	"overlayFragmentActive",
	"overlayFragmentInactive",
	"searchResults",
	"toc",
	"error"
)

fun decodeReaderBridgeMessage(message: String): ReaderBridgeDecodeResult {
	val rawMessage = ReaderRedactedBridgePayload
	val element = runCatching { ReaderBridgeJson.parseToJsonElement(message) }
		.getOrElse {
			return ReaderBridgeDecodeResult.Rejected(
				failure = ReaderBridgeDecodeFailure.MalformedJson,
				rawMessage = rawMessage
			)
		}
	val json = element as? JsonObject
		?: return ReaderBridgeDecodeResult.Rejected(
			failure = ReaderBridgeDecodeFailure.NonObjectPayload,
			rawMessage = rawMessage
		)
	val type = json.stringValue("type")
		?: return ReaderBridgeDecodeResult.Rejected(
			failure = ReaderBridgeDecodeFailure.MissingType,
			rawMessage = rawMessage
		)
	if (type !in ReaderBridgeEventTypes) {
		return ReaderBridgeDecodeResult.Rejected(
			failure = ReaderBridgeDecodeFailure.UnknownType,
			rawMessage = rawMessage
		)
	}
	val event = runCatching { decodeReaderBridgeEventPayload(json, type) }
		.getOrNull()
		?: return ReaderBridgeDecodeResult.Rejected(
			failure = ReaderBridgeDecodeFailure.InvalidPayload,
			rawMessage = rawMessage
		)
	return ReaderBridgeDecodeResult.Decoded(event)
}

fun decodeReaderBridgeEvent(message: String): ReaderBridgeEvent? =
	(decodeReaderBridgeMessage(message) as? ReaderBridgeDecodeResult.Decoded)?.event

private fun decodeReaderBridgeEventPayload(json: JsonObject, type: String): ReaderBridgeEvent? =
		when (type) {
			"ready" -> ReaderBridgeEvent.Ready
			"commandAck" -> json.stringValue("commandId")
				?.takeIf { it.isNotBlank() }
				?.let(ReaderBridgeEvent::CommandAcknowledged)
			"commandFailed" -> json.stringValue("commandId")
				?.takeIf { it.isNotBlank() }
				?.let(ReaderBridgeEvent::CommandFailed)
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
			"locationChanged" -> json.toLocationChanged()
			"duplicatePageSuspected" -> json.toDuplicatePageSuspected()
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
			"textPoint" -> json.toTextPoint()
			"whispersyncCueMapRendered" -> json.toWhispersyncCueMapRendered()
			"whispersyncCueMapSeekRequested" -> json.toWhispersyncCueMapSeekRequested()
			"whispersyncCueMapHoldOutcome" -> json.toWhispersyncCueMapHoldOutcome()
			"rawTextProvenanceStatus" -> json.toRawTextProvenanceStatus()
			"overlayFragmentActive" -> json.toOverlayFragment()
				?.let { fragment ->
					val anchorReceipt = json["anchorReceipt"]
						?.let { it as? JsonObject }
						?.toWhispersyncAnchorReceipt(
							fragment.wordBoundarySequence ?: fragment.overlayRequestId
						)
					if (
						fragment.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8 &&
						anchorReceipt == null
					) {
						null
					} else {
						ReaderBridgeEvent.OverlayFragmentActive(
							fragment = fragment,
							anchorReceipt = anchorReceipt
						)
					}
				}
			"overlayFragmentInactive" -> ReaderBridgeEvent.OverlayFragmentInactive(
				fragmentId = json.stringValue("fragmentId"),
				overlayRequestId = json.longValue("overlayRequestId"),
				coordinateMode = when (json.stringValue("coordinateMode")) {
					ReaderCueV1CoordinateMode -> ReaderOverlayCoordinateMode.CueV1DomUtf16
					ReaderWordSyncV1CoordinateMode -> ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8
					else -> null
				},
				reason = json.stringValue("reason")
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

private fun JsonObject.toDuplicatePageSuspected(): ReaderBridgeEvent.DuplicatePageSuspected? {
	val currentPageOrdinal = intValue("currentPageOrdinal") ?: return null
	val previousPageOrdinal = intValue("previousPageOrdinal") ?: return null
	val plainTextSame = booleanValue("plainTextSame") ?: return null
	val locatorSame = booleanValue("locatorSame") ?: return null
	if (
		currentPageOrdinal <= 0 ||
		previousPageOrdinal <= 0 ||
		currentPageOrdinal == previousPageOrdinal ||
		!plainTextSame
	) return null
	return ReaderBridgeEvent.DuplicatePageSuspected(
		currentPageOrdinal = currentPageOrdinal,
		previousPageOrdinal = previousPageOrdinal,
		plainTextSame = plainTextSame,
		locatorSame = locatorSame
	)
}

private fun JsonObject.toLocationChanged(): ReaderBridgeEvent.LocationChanged? {
	val foliateSessionId = stringValue("foliateSessionId") ?: return null
	val settlementToken = stringValue("pageTurnSettleToken")
	val settlementSessionId = stringValue("pageTurnSettleSessionId")
	val settlementRasterGeneration = longValue("pageTurnSettleRasterGeneration")
	val settlementTextureGeneration = longValue("pageTurnSettleTextureGeneration")
	val settlementIsValid = settlementToken != null &&
		settlementSessionId == foliateSessionId &&
		settlementRasterGeneration?.let { it >= 0L } == true &&
		settlementTextureGeneration?.let { it >= 0L } == true
	return ReaderBridgeEvent.LocationChanged(
		locator = ReaderLocator(
			href = stringValue("href"),
			cfi = stringValue("cfi"),
			progress = doubleValue("progress"),
			pageIndex = intValue("pageIndex"),
			pageCount = intValue("pageCount"),
			chapterProgress = doubleValue("chapterProgress"),
			chapterPageIndex = intValue("chapterPageIndex"),
			chapterPageCount = intValue("chapterPageCount"),
			rangeCfi = stringValue("rangeCfi"),
			reason = stringValue("reason"),
			fraction = doubleValue("fraction"),
			size = doubleValue("size"),
			tocItemLabel = stringValue("tocItemLabel"),
			pageItemLabel = stringValue("pageItemLabel")
		),
		foliateSessionId = foliateSessionId,
		tocTitle = stringValue("tocTitle"),
		pageTurnSettleToken = settlementToken.takeIf { settlementIsValid },
		pageTurnSettleSessionId = settlementSessionId.takeIf { settlementIsValid },
		pageTurnSettleRasterGeneration = settlementRasterGeneration.takeIf { settlementIsValid },
		pageTurnSettleTextureGeneration = settlementTextureGeneration.takeIf { settlementIsValid },
		causalSequence = causalSequence(),
		destinationCommitIdentity = destinationCommitIdentity(foliateSessionId)
	)
}

private fun JsonObject.toVisibleTextRange(): ReaderBridgeEvent.VisibleTextRange? {
	val textHref = stringValue("textHref") ?: stringValue("href") ?: return null
	val visibleStart = intValue("visibleStart") ?: intValue("start") ?: return null
	val visibleEnd = intValue("visibleEnd") ?: intValue("end") ?: return null
	val rawProvenanceId = stringValue("rawProvenanceId")
	val rawSpineIndex = intValue("rawSpineIndex")
	val rawByteStart = intValue("rawByteStart")
	val rawByteEnd = intValue("rawByteEnd")
	val rawValues = listOf(rawProvenanceId, rawSpineIndex, rawByteStart, rawByteEnd)
	if (rawValues.any { it != null }) {
		if (rawValues.any { it == null }) return null
		if (rawSpineIndex!! < 0 || rawByteStart!! < 0 || rawByteEnd!! <= rawByteStart) return null
	}
	return ReaderBridgeEvent.VisibleTextRange(
		textHref = textHref,
		visibleStart = minOf(visibleStart, visibleEnd),
		visibleEnd = maxOf(visibleStart, visibleEnd),
		rangeCfi = stringValue("rangeCfi"),
		source = stringValue("source") ?: stringValue("reason"),
		rawProvenanceId = rawProvenanceId,
		rawSpineIndex = rawSpineIndex,
		rawByteStart = rawByteStart,
		rawByteEnd = rawByteEnd,
		causalSequence = causalSequence(),
		destinationCommitIdentity = destinationCommitIdentity()
	)
}

private fun JsonObject.toTextPoint(): ReaderBridgeEvent.TextPoint? {
	val textHref = stringValue("textHref") ?: stringValue("href") ?: return null
	val textOffset = intValue("textOffset") ?: intValue("offset") ?: return null
	val rawProvenanceId = stringValue("rawProvenanceId")
	val rawByteOffset = intValue("rawByteOffset")
	if ((rawProvenanceId == null) != (rawByteOffset == null) || rawByteOffset?.let { it < 0 } == true) {
		return null
	}
	return ReaderBridgeEvent.TextPoint(
		textHref = textHref,
		textOffset = textOffset.coerceAtLeast(0),
		rangeCfi = stringValue("rangeCfi"),
		source = stringValue("source") ?: stringValue("reason"),
		rawProvenanceId = rawProvenanceId,
		rawByteOffset = rawByteOffset,
		causalSequence = causalSequence(),
		destinationCommitIdentity = destinationCommitIdentity()
	)
}

private fun JsonObject.toWhispersyncCueMapRendered(): ReaderBridgeEvent.WhispersyncCueMapRendered? {
	val revisionDigest = cueMapRevisionDigest() ?: return null
	val generation = cueMapPresentationGeneration() ?: return null
	val destination = destinationCommitIdentity() ?: return null
	val ordinals = (get("sourceOrdinals") as? JsonArray)
		?.mapNotNull { element -> (element as? JsonPrimitive)?.intOrNull }
		?.takeIf { values ->
			values.size <= ReaderWhispersyncCueMapTransitionLimit &&
				values.all { it >= 0 } && values.distinct().size == values.size
		}
		?: return null
	val markerElements = (get("markerReceipts") as? JsonArray).orEmpty()
	val markerReceipts = markerElements.map { element ->
		(element as? JsonObject)?.toWhispersyncCueMapMarkerReceipt()
	}
	if (markerReceipts.any { it == null }) return null
	val resolvedMarkerReceipts = markerReceipts.filterNotNull()
	if (resolvedMarkerReceipts.map(ReaderWhispersyncCueMapMarkerReceipt::sourceOrdinal).let { values ->
		values.distinct().size != values.size
	}) return null
	return ReaderBridgeEvent.WhispersyncCueMapRendered(
		sourceOrdinalsInDomReadingOrder = ordinals,
		revisionDigest = revisionDigest,
		presentationGeneration = generation,
		destinationCommitIdentity = destination,
		markerReceipts = resolvedMarkerReceipts
	)
}

private fun JsonObject.toWhispersyncCueMapMarkerReceipt(): ReaderWhispersyncCueMapMarkerReceipt? {
	val sourceOrdinal = intValue("sourceOrdinal")?.takeIf { it >= 0 } ?: return null
	val anchorReceipt = (get("anchorReceipt") as? JsonObject)
		?.toWhispersyncAnchorReceipt(sourceOrdinal.toLong())
		?: return null
	return ReaderWhispersyncCueMapMarkerReceipt(
		sourceOrdinal = sourceOrdinal,
		prepared = booleanValue("prepared") ?: false,
		requested = booleanValue("requested") ?: false,
		audioActive = booleanValue("audioActive") ?: false,
		renderedHighlight = booleanValue("renderedHighlight") ?: false,
		anchorReceipt = anchorReceipt
	)
}

private fun JsonObject.toWhispersyncCueMapSeekRequested(): ReaderBridgeEvent.WhispersyncCueMapSeekRequested? {
	val sourceOrdinal = intValue("sourceOrdinal")?.takeIf { it >= 0 } ?: return null
	return ReaderBridgeEvent.WhispersyncCueMapSeekRequested(
		sourceOrdinal = sourceOrdinal,
		revisionDigest = cueMapRevisionDigest() ?: return null,
		presentationGeneration = cueMapPresentationGeneration() ?: return null,
		destinationCommitIdentity = destinationCommitIdentity() ?: return null
	)
}

private fun JsonObject.toWhispersyncCueMapHoldOutcome(): ReaderBridgeEvent.WhispersyncCueMapHoldOutcome? {
	val sourceOrdinal = intValue("sourceOrdinal")?.takeIf { it >= 0 } ?: return null
	val outcome = ReaderWhispersyncCueMapHoldOutcome.fromBridgeValue(stringValue("outcome")) ?: return null
	return ReaderBridgeEvent.WhispersyncCueMapHoldOutcome(
		sourceOrdinal = sourceOrdinal,
		revisionDigest = cueMapRevisionDigest() ?: return null,
		presentationGeneration = cueMapPresentationGeneration() ?: return null,
		outcome = outcome
	)
}

private fun JsonObject.cueMapRevisionDigest(): String? =
	stringValue("revisionDigest")?.takeIf { it.matches(Regex("[0-9a-f]{12}")) }

private fun JsonObject.cueMapPresentationGeneration(): Long? =
	longValue("presentationGeneration")?.takeIf { it > 0L }

private fun JsonObject.toRawTextProvenanceStatus(): ReaderBridgeEvent.RawTextProvenanceStatusChanged? {
	val provenanceId = stringValue("provenanceId") ?: return null
	val status = when (stringValue("status")) {
		"pending" -> RawTextProvenanceStatus.Pending
		"ready" -> RawTextProvenanceStatus.Ready
		"rejected" -> RawTextProvenanceStatus.Rejected
		else -> return null
	}
	val reason = stringValue("reason")?.toRawTextProvenanceReason() ?: run {
		if (stringValue("reason") != null) return null
		null
	}
	return ReaderBridgeEvent.RawTextProvenanceStatusChanged(
		provenanceId = provenanceId,
		status = status,
		reason = reason
	)
}

private fun String.toRawTextProvenanceReason(): RawTextProvenanceReason? = when (this) {
	"content-not-loaded" -> RawTextProvenanceReason.ContentNotLoaded
	"invalid-descriptor" -> RawTextProvenanceReason.InvalidDescriptor
	"section-mismatch" -> RawTextProvenanceReason.SectionMismatch
	"source-unavailable" -> RawTextProvenanceReason.SourceUnavailable
	"source-too-large" -> RawTextProvenanceReason.SourceTooLarge
	"source-hash-mismatch" -> RawTextProvenanceReason.SourceHashMismatch
	"invalid-utf8" -> RawTextProvenanceReason.InvalidUtf8
	"extracted-hash-mismatch" -> RawTextProvenanceReason.ExtractedHashMismatch
	"extracted-length-mismatch" -> RawTextProvenanceReason.ExtractedLengthMismatch
	"token-count-mismatch" -> RawTextProvenanceReason.TokenCountMismatch
	"token-sequence-mismatch" -> RawTextProvenanceReason.TokenSequenceMismatch
	"document-changed" -> RawTextProvenanceReason.DocumentChanged
	else -> null
}

private fun JsonObject.causalSequence(): Long? =
	longValue("causalSequence")?.takeIf { it > 0L }

private fun JsonObject.destinationCommitIdentity(
	fallbackFoliateSessionId: String? = null
): ReaderDestinationCommitIdentity? {
	val foliateSessionId = stringValue("destinationFoliateSessionId")
		?: fallbackFoliateSessionId
		?: return null
	val commitSequence = longValue("destinationCommitSequence")
		?.takeIf { it > 0L }
		?: return null
	return ReaderDestinationCommitIdentity(foliateSessionId, commitSequence)
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

private val ReaderOverlayCoordinateMode.wireValue: String
	get() = when (this) {
		ReaderOverlayCoordinateMode.CueV1DomUtf16 -> ReaderCueV1CoordinateMode
		ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8 -> ReaderWordSyncV1CoordinateMode
	}

private fun ReaderRawTextProvenanceDescriptor.toJsonObject(): JsonObject =
	buildJsonObject {
		put("id", id)
		put("href", href)
		put("spineIndex", spineIndex)
		put("sourceHash", sourceHash)
		put("extractedTextHash", extractedTextHash)
		put("byteLength", byteLength)
		put("tokenCount", tokenCount)
	}

private fun ReaderWhispersyncCueMapPresentation.toCueMapJsonObject(type: String): JsonObject =
	buildJsonObject {
		put("type", type)
		put("enabled", enabled)
		put("revisionDigest", revisionDigest)
		put("presentationGeneration", presentationGeneration)
		put(
			"destinationCommitIdentity",
			buildJsonObject {
				put("foliateSessionId", destinationCommitIdentity.foliateSessionId)
				put("commitSequence", destinationCommitIdentity.commitSequence)
			}
		)
		put(
			"cues",
			buildJsonArray {
				cues.forEach { cue -> add(cue.toJsonObject()) }
			}
		)
		preparedSourceOrdinal?.let { put("preparedSourceOrdinal", it) }
		requestedSourceOrdinal?.let { put("requestedSourceOrdinal", it) }
		audioActiveSourceOrdinal?.let { put("audioActiveSourceOrdinal", it) }
		renderedHighlightSourceOrdinal?.let { put("renderedHighlightSourceOrdinal", it) }
		put("transportAcknowledgementPending", transportAcknowledgementPending)
	}

private fun ReaderWhispersyncCueMapCue.toJsonObject(): JsonObject =
	buildJsonObject {
		put("sourceOrdinal", sourceOrdinal)
		put("textHref", textHref)
		put("textStart", textStart)
		put("textEnd", textEnd)
		ebookText?.let { put("ebookText", it) }
		nextTextHref?.let { put("nextTextHref", it) }
		nextTextStart?.let { put("nextTextStart", it) }
		nextTextEnd?.let { put("nextTextEnd", it) }
		nextEbookText?.let { put("nextEbookText", it) }
	}

private fun ReaderOverlayFragment.toJsonObject(): JsonObject =
	buildJsonObject {
		put("resourceHref", resourceHref)
		put("coordinateMode", coordinateMode.wireValue)
		overlayRequestId?.let { put("overlayRequestId", it) }
		wordBoundarySequence?.let { put("wordBoundarySequence", it) }
		fragmentId?.let { put("fragmentId", it) }
		textHref?.let { put("textHref", it) }
		clipBeginSeconds?.let { put("clipBeginSeconds", it) }
		clipEndSeconds?.let { put("clipEndSeconds", it) }
		textStart?.let { put("textStart", it) }
		textEnd?.let { put("textEnd", it) }
		textProgressEnd?.let { put("textProgressEnd", it) }
		textProgressFraction?.let { put("textProgressFraction", it) }
		spokenText?.let { put("spokenText", it) }
		ebookText?.let { put("ebookText", it) }
		nextTextHref?.let { put("nextTextHref", it) }
		nextTextStart?.let { put("nextTextStart", it) }
		nextTextEnd?.let { put("nextTextEnd", it) }
		nextEbookText?.let { put("nextEbookText", it) }
		playbackSpeed?.let { put("playbackSpeed", it.toDouble()) }
		label?.let { put("label", it) }
		rawProvenanceId?.let { put("rawProvenanceId", it) }
		rawSpineIndex?.let { put("rawSpineIndex", it) }
		rawByteStart?.let { put("rawByteStart", it) }
		rawByteEnd?.let { put("rawByteEnd", it) }
		rawProgressByteEnd?.let { put("rawProgressByteEnd", it) }
		rawProgressFraction?.let { put("rawProgressFraction", it) }
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
		paperTextureEnabled?.let { put("paperTextureEnabled", it) }
		pageEdgesEnabled?.let { put("pageEdgesEnabled", it) }
		paperStainsEnabled?.let { put("paperStainsEnabled", it) }
		coverBackdropEnabled?.let { put("coverBackdropEnabled", it) }
		navBarType?.let { put("navBarType", it) }
		flowMode?.let { put("flowMode", it) }
		dragAnimationMode?.let { put("dragAnimationMode", it) }
		pageBitmapQuality?.let { put("pageBitmapQuality", it) }
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
		whispersyncHighlightLeadMs?.let { put("whispersyncHighlightLeadMs", it) }
		whispersyncHighlightColorArgb?.let { put("whispersyncHighlightColorArgb", it) }
		whispersyncHighlightLoading?.let { put("whispersyncHighlightLoading", it) }
		whispersyncHighlightStyle?.let { put("whispersyncHighlightStyle", it) }
		volumeKeyPageTurns?.let { put("volumeKeyPageTurns", it) }
		webContentsDebuggingEnabled?.let { put("webContentsDebuggingEnabled", it) }
	}

private fun JsonObject.toOverlayFragment(): ReaderOverlayFragment? {
	val resourceHref = stringValue("resourceHref") ?: return null
	val coordinateMode = when (val wireValue = stringValue("coordinateMode")) {
		null, ReaderCueV1CoordinateMode -> ReaderOverlayCoordinateMode.CueV1DomUtf16
		ReaderWordSyncV1CoordinateMode -> ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8
		else -> return null
	}
	return ReaderOverlayFragment(
		resourceHref = resourceHref,
		coordinateMode = coordinateMode,
		overlayRequestId = longValue("overlayRequestId"),
		wordBoundarySequence = longValue("wordBoundarySequence"),
		fragmentId = stringValue("fragmentId"),
		textHref = stringValue("textHref"),
		clipBeginSeconds = doubleValue("clipBeginSeconds"),
		clipEndSeconds = doubleValue("clipEndSeconds"),
		textStart = intValue("textStart"),
		textEnd = intValue("textEnd"),
		textProgressEnd = intValue("textProgressEnd"),
		textProgressFraction = doubleValue("textProgressFraction"),
		spokenText = stringValue("spokenText") ?: stringValue("text"),
		ebookText = stringValue("ebookText") ?: stringValue("epubText"),
		nextTextHref = stringValue("nextTextHref"),
		nextTextStart = intValue("nextTextStart"),
		nextTextEnd = intValue("nextTextEnd"),
		nextEbookText = stringValue("nextEbookText"),
		playbackSpeed = doubleValue("playbackSpeed")?.toFloat(),
		label = stringValue("label"),
		rawProvenanceId = stringValue("rawProvenanceId"),
		rawSpineIndex = intValue("rawSpineIndex"),
		rawByteStart = intValue("rawByteStart"),
		rawByteEnd = intValue("rawByteEnd"),
		rawProgressByteEnd = intValue("rawProgressByteEnd"),
		rawProgressFraction = doubleValue("rawProgressFraction")
	)
}

private fun JsonObject.toWhispersyncAnchorReceipt(
	expectedBoundarySequence: Long?
): ReaderWhispersyncAnchorReceipt? {
	val expectedBoundary = expectedBoundarySequence ?: return null
	val boundarySequence = longValue("boundarySequence") ?: return null
	if (boundarySequence != expectedBoundary || boundarySequence < 0L) return null
	val captureGeometry = (get("captureGeometry") as? JsonObject)
		?.toWhispersyncCaptureGeometry()
		?: return null
	val encodedRects = get("pageLocalRects") as? JsonArray ?: return null
	val pageLocalRects = encodedRects.map { element ->
		(element as? JsonObject)?.toWhispersyncPageLocalRect()
	}
	if (pageLocalRects.isEmpty() || pageLocalRects.any { it == null }) return null
	return runCatching {
		ReaderWhispersyncAnchorReceipt(
			foliateSessionId = stringValue("foliateSessionId") ?: return null,
			destinationCommitToken = stringValue("destinationCommitToken") ?: return null,
			visualPageOrdinal = intValue("visualPageOrdinal") ?: return null,
			spineIndex = intValue("spineIndex") ?: return null,
			rasterGeneration = longValue("rasterGeneration") ?: return null,
			textureGeneration = longValue("textureGeneration") ?: return null,
			presentationMutationGeneration = longValue("presentationMutationGeneration") ?: return null,
			presentationSequence = longValue("presentationSequence") ?: return null,
			anchorGeneration = longValue("anchorGeneration") ?: return null,
			boundarySequence = boundarySequence,
			layoutGeneration = longValue("layoutGeneration") ?: return null,
			viewGeneration = longValue("viewGeneration") ?: return null,
			commitSequence = longValue("commitSequence") ?: return null,
			committedSpineIndex = intValue("committedSpineIndex") ?: return null,
			committedChapterPageIndex = intValue("committedChapterPageIndex") ?: return null,
			committedChapterPageCount = intValue("committedChapterPageCount") ?: return null,
			paginationFingerprint = stringValue("paginationFingerprint") ?: return null,
			layoutFingerprint = stringValue("layoutFingerprint") ?: return null,
			readerSettingsRasterKey = stringValue("readerSettingsRasterKey") ?: return null,
			captureGeometry = captureGeometry,
			pageLocalRects = pageLocalRects.filterNotNull()
		)
	}.getOrNull()
}

private fun JsonObject.toWhispersyncCaptureGeometry(): ReaderPageTurnCaptureGeometry? {
	val viewportWidth = doubleValue("viewportWidth")
		?.takeIf { it.isFinite() && it > 0.0 }
		?: return null
	val viewportHeight = doubleValue("viewportHeight")
		?.takeIf { it.isFinite() && it > 0.0 }
		?: return null
	val mode = when (stringValue("mode")) {
		"single" -> ReaderPageTurnLayoutMode.Single
		"spread" -> ReaderPageTurnLayoutMode.Spread
		else -> return null
	}
	val encodedPages = get("pages") as? JsonArray ?: return null
	val pages = encodedPages.map { element ->
		(element as? JsonObject)?.toWhispersyncPageRect()
	}
	if (pages.isEmpty() || pages.any { it == null }) return null
	val resolvedPages = pages.filterNotNull()
	if (resolvedPages.map { it.role }.distinct().size != resolvedPages.size) return null
	if (
		mode == ReaderPageTurnLayoutMode.Single &&
		resolvedPages.any { it.role != ReaderPageTurnPageRole.Full }
	) return null
	if (
		mode == ReaderPageTurnLayoutMode.Spread &&
		resolvedPages.any { it.role == ReaderPageTurnPageRole.Full }
	) return null
	return ReaderPageTurnCaptureGeometry(
		viewportWidth = viewportWidth,
		viewportHeight = viewportHeight,
		mode = mode,
		pages = resolvedPages
	)
}

private fun JsonObject.toWhispersyncPageRect(): ReaderPageTurnPageRect? {
	val role = stringValue("role").toReaderPageTurnPageRole() ?: return null
	val left = doubleValue("left")?.takeIf(Double::isFinite) ?: return null
	val top = doubleValue("top")?.takeIf(Double::isFinite) ?: return null
	val width = doubleValue("width")?.takeIf { it.isFinite() && it > 0.0 } ?: return null
	val height = doubleValue("height")?.takeIf { it.isFinite() && it > 0.0 } ?: return null
	return ReaderPageTurnPageRect(role, left, top, width, height)
}

private fun JsonObject.toWhispersyncPageLocalRect(): ReaderWhispersyncPageLocalRect? {
	val role = stringValue("role").toReaderPageTurnPageRole() ?: return null
	val left = doubleValue("left")?.takeIf(Double::isFinite) ?: return null
	val top = doubleValue("top")?.takeIf(Double::isFinite) ?: return null
	val width = doubleValue("width")?.takeIf { it.isFinite() && it > 0.0 } ?: return null
	val height = doubleValue("height")?.takeIf { it.isFinite() && it > 0.0 } ?: return null
	return ReaderWhispersyncPageLocalRect(role, left, top, width, height)
}

private fun String?.toReaderPageTurnPageRole(): ReaderPageTurnPageRole? = when (this) {
	"full" -> ReaderPageTurnPageRole.Full
	"left" -> ReaderPageTurnPageRole.Left
	"right" -> ReaderPageTurnPageRole.Right
	else -> null
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

private fun Map<String, JsonElement>.longValue(key: String): Long? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }
		?.value
		?.jsonPrimitive
		?.longOrNull

private fun Map<String, JsonElement>.booleanValue(key: String): Boolean? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }
		?.value
		?.jsonPrimitive
		?.booleanOrNull
