package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.math.abs
import kotlin.math.roundToLong

private val WhispersyncJson = Json {
	ignoreUnknownKeys = true
	isLenient = true
}

private const val WhispersyncBoundarySnapToleranceMs = 50L

@Serializable
data class WhispersyncSidecar(
	val artifactId: String? = null,
	val ebookBookFileId: String? = null,
	val audiobookBookFileId: String? = null,
	val ebookManifestHref: String? = null,
	val audiobookManifestHref: String? = null,
	val documentTextLength: Int? = null,
	val timeline: WhispersyncTimeline = WhispersyncTimeline(),
	val droppedSegmentCount: Int = 0,
	val droppedSegmentReasons: List<String> = emptyList()
)

@Serializable
data class WhispersyncTimeline(
	val segments: List<WhispersyncSegment> = emptyList()
) {
	fun activeSegment(
		audioResource: String,
		positionMs: Long,
		audioTrackIndex: Int? = null
	): WhispersyncSegment? {
		val normalizedAudioCandidates = audioResource.normalizedWhispersyncResourceCandidates()
		val position = positionMs.coerceAtLeast(0L)
		val matchingSegments = segments.filter { segment ->
			segment.matchesAudioResourceOrTrack(
				normalizedAudioCandidates = normalizedAudioCandidates,
				audioTrackIndex = audioTrackIndex
			)
		}
		return matchingSegments.firstOrNull { segment ->
			position >= segment.startMs &&
				position < segment.endMs
		}
			?: matchingSegments.nearestBoundarySnap(position)
	}

	fun seekTargetForVisibleTextRange(
		textHref: String,
		visibleStart: Int,
		visibleEnd: Int
	): WhispersyncAudioSeekTarget? {
		val normalizedText = normalizedMediaOverlayResource(textHref)
		val start = minOf(visibleStart, visibleEnd).coerceAtLeast(0)
		val end = maxOf(visibleStart, visibleEnd).coerceAtLeast(start)
		val visibleCenter = (start + end) / 2.0
		return segments
			.asSequence()
			.filter { segment -> normalizedMediaOverlayResource(segment.textHref) == normalizedText }
			.mapNotNull { segment ->
				segment.overlapScore(start, end, visibleCenter)
					?.let { score -> segment to score }
			}
			.sortedWith(
				compareByDescending<Pair<WhispersyncSegment, WhispersyncRangeScore>> { (_, score) -> score.overlap }
					.thenBy { (_, score) -> score.centerDistance }
			)
			.firstOrNull()
			?.first
			?.let { segment ->
				WhispersyncAudioSeekTarget(
					audioResource = segment.audioResource,
					positionMs = segment.startMs,
					segment = segment
				)
			}
	}
}

@Serializable
data class WhispersyncSegment(
	val id: String? = null,
	val audioResourceId: String? = null,
	val audioTrackIndex: Int? = null,
	val audioResource: String,
	val startMs: Long,
	val endMs: Long,
	val textHref: String,
	val fragmentId: String? = null,
	val rangeCfi: String? = null,
	val textStart: Int? = null,
	val textEnd: Int? = null,
	val label: String? = null
) {
	fun toReaderOverlayFragment(): ReaderOverlayFragment =
		ReaderOverlayFragment(
			resourceHref = audioResource,
			fragmentId = fragmentId,
			textHref = textHref,
			clipBeginSeconds = startMs / 1000.0,
			clipEndSeconds = endMs / 1000.0,
			textStart = textStart,
			textEnd = textEnd,
			label = label
		)
}

@Serializable
data class WhispersyncAudioSeekTarget(
	val audioResource: String,
	val positionMs: Long,
	val segment: WhispersyncSegment
)

fun encodeWhispersyncSidecar(sidecar: WhispersyncSidecar): String =
	WhispersyncJson.encodeToString(sidecar)

fun decodeWhispersyncSidecar(json: String): WhispersyncSidecar {
	val root = WhispersyncJson.parseToJsonElement(json).jsonObject
	val ebook = root.objectValue("ebook")
	val audiobook = root.objectValue("audiobook")
	val resources = root.objectValue("resources")
	val audioResources = audiobook
		?.arrayValue("resources")
		.orEmpty()
		.mapNotNull { (it as? JsonObject)?.stringValue("href") }
	val defaultAudioResource = audioResources.firstOrNull()
	val defaultTextHref = ebook?.stringValue("href") ?: ebook?.stringValue("textHref")
	val parsedSegments = (root.segmentArray() ?: root.objectValue("timeline")?.segmentArray())
		.orEmpty()
		.mapIndexed { index, element ->
			val segment = element as? JsonObject
				?: return@mapIndexed WhispersyncSegmentParseResult.dropped(index, "invalid-segment")
			segment.toWhispersyncSegmentResult(
				index = index,
				defaultAudioResource = defaultAudioResource,
				defaultTextHref = defaultTextHref
			)
		}
	val segments = parsedSegments.mapNotNull { it.segment }
	val droppedReasons = parsedSegments.mapNotNull { it.dropReason }

	return WhispersyncSidecar(
		artifactId = root.stringValue("artifactId") ?: root.stringValue("id"),
		ebookBookFileId = root.stringValue("ebookBookFileId")
			?: ebook?.stringValue("bookFileId")
			?: ebook?.stringValue("id"),
		audiobookBookFileId = root.stringValue("audiobookBookFileId")
			?: audiobook?.stringValue("bookFileId")
			?: audiobook?.stringValue("id"),
		ebookManifestHref = resources?.stringValue("ebookManifestHref"),
		audiobookManifestHref = resources?.stringValue("audiobookManifestHref"),
		documentTextLength = root.intValue("documentTextLength")
			?: ebook?.intValue("documentTextLength"),
		timeline = WhispersyncTimeline(segments = segments),
		droppedSegmentCount = droppedReasons.size,
		droppedSegmentReasons = droppedReasons
	)
}

private data class WhispersyncSegmentParseResult(
	val segment: WhispersyncSegment? = null,
	val dropReason: String? = null
) {
	companion object {
		fun dropped(index: Int, reason: String): WhispersyncSegmentParseResult =
			WhispersyncSegmentParseResult(dropReason = "segment[$index]: $reason")
	}
}

private data class WhispersyncRangeScore(
	val overlap: Int,
	val centerDistance: Double
)

private data class WhispersyncBoundarySnapCandidate(
	val segment: WhispersyncSegment,
	val distanceMs: Long,
	val priority: Int
)

private fun List<WhispersyncSegment>.nearestBoundarySnap(positionMs: Long): WhispersyncSegment? =
	asSequence()
		.mapNotNull { segment -> segment.boundarySnapCandidate(positionMs) }
		.sortedWith(
			compareBy<WhispersyncBoundarySnapCandidate> { candidate -> candidate.distanceMs }
				.thenBy { candidate -> candidate.priority }
				.thenBy { candidate -> candidate.segment.startMs }
		)
		.firstOrNull()
		?.segment

private fun WhispersyncSegment.boundarySnapCandidate(positionMs: Long): WhispersyncBoundarySnapCandidate? {
	val distanceToStart = startMs - positionMs
	if (distanceToStart in 1..WhispersyncBoundarySnapToleranceMs) {
		return WhispersyncBoundarySnapCandidate(
			segment = this,
			distanceMs = distanceToStart,
			priority = 0
		)
	}
	val distanceAfterEnd = positionMs - endMs
	if (distanceAfterEnd in 0..WhispersyncBoundarySnapToleranceMs) {
		return WhispersyncBoundarySnapCandidate(
			segment = this,
			distanceMs = distanceAfterEnd,
			priority = 1
		)
	}
	return null
}

private fun WhispersyncSegment.overlapScore(
	visibleStart: Int,
	visibleEnd: Int,
	visibleCenter: Double
): WhispersyncRangeScore? {
	val rangeStart = textStart
	val rangeEnd = textEnd
	if (rangeStart == null || rangeEnd == null) {
		return null
	}
	val start = minOf(rangeStart, rangeEnd)
	val end = maxOf(rangeStart, rangeEnd)
	val overlap = (minOf(end, visibleEnd) - maxOf(start, visibleStart)).coerceAtLeast(0)
	if (overlap == 0) return null
	val center = (start + end) / 2.0
	return WhispersyncRangeScore(
		overlap = overlap,
		centerDistance = abs(center - visibleCenter)
	)
}

private fun JsonObject.segmentArray(): List<JsonElement>? =
	arrayValue("segments")
		?: arrayValue("alignments")
		?: arrayValue("clips")
		?: arrayValue("cues")

private fun JsonObject.toWhispersyncSegmentResult(
	index: Int,
	defaultAudioResource: String?,
	defaultTextHref: String?
): WhispersyncSegmentParseResult {
	val audio = objectValue("audio")
	val text = objectValue("text") ?: objectValue("ebook")
	val audioResource = stringValue("audioResource")
		?: stringValue("audioHref")
		?: stringValue("audioUrl")
		?: audio?.stringValue("href")
		?: audio?.stringValue("resource")
		?: defaultAudioResource
		?: return WhispersyncSegmentParseResult.dropped(index, "missing-audio-resource")
	val textHref = stringValue("textHref")
		?: stringValue("textResource")
		?: stringValue("ebookHref")
		?: stringValue("href")
		?: text?.stringValue("href")
		?: text?.stringValue("resource")
		?: defaultTextHref
		?: return WhispersyncSegmentParseResult.dropped(index, "missing-text-href")
	val startMs = millisecondValue("startMs")
		?: millisecondValue("audioStartMs")
		?: secondsValue("audioStart")
		?: secondsValue("startSeconds")
		?: secondsValue("audioStartSeconds")
		?: secondsValue("start")
		?: return WhispersyncSegmentParseResult.dropped(index, "missing-audio-start")
	val endMs = millisecondValue("endMs")
		?: millisecondValue("audioEndMs")
		?: secondsValue("audioEnd")
		?: secondsValue("endSeconds")
		?: secondsValue("audioEndSeconds")
		?: secondsValue("end")
		?: return WhispersyncSegmentParseResult.dropped(index, "missing-audio-end")
	if (endMs <= startMs) {
		return WhispersyncSegmentParseResult.dropped(index, "invalid-audio-range")
	}
	return WhispersyncSegmentParseResult(
		segment = WhispersyncSegment(
			id = stringValue("id"),
			audioResourceId = stringValue("audioResourceId")
				?: stringValue("audioId")
				?: audio?.stringValue("resourceId")
				?: audio?.stringValue("id"),
			audioTrackIndex = intValue("audioTrackIndex")
				?: intValue("trackIndex")
				?: audio?.intValue("trackIndex"),
			audioResource = audioResource,
			startMs = startMs,
			endMs = endMs,
			textHref = textHref,
			fragmentId = stringValue("fragmentId"),
			rangeCfi = stringValue("rangeCfi") ?: stringValue("cfi"),
			textStart = intValue("textStart") ?: intValue("ebookStart") ?: intValue("startChar") ?: text?.intValue("start"),
			textEnd = intValue("textEnd") ?: intValue("ebookEnd") ?: intValue("endChar") ?: text?.intValue("end"),
			label = stringValue("label") ?: stringValue("chapterLabel") ?: stringValue("sectionLabel")
		)
	)
}

private fun JsonObject.objectValue(key: String): JsonObject? =
	valueFor(key) as? JsonObject

private fun JsonObject.arrayValue(key: String): List<JsonElement>? =
	(valueFor(key) as? JsonArray)?.toList()

private fun JsonObject.stringValue(key: String): String? =
	(valueFor(key) as? JsonPrimitive)
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun JsonObject.intValue(key: String): Int? =
	(valueFor(key) as? JsonPrimitive)
		?.intOrNull

private fun JsonObject.millisecondValue(key: String): Long? =
	(valueFor(key) as? JsonPrimitive)
		?.let { primitive ->
			primitive.longOrNull ?: primitive.doubleOrNull?.roundToLong()
		}

private fun JsonObject.secondsValue(key: String): Long? =
	(valueFor(key) as? JsonPrimitive)
		?.doubleOrNull
		?.let { seconds -> (seconds * 1000.0).roundToLong() }

private fun JsonObject.valueFor(key: String): JsonElement? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value

private fun WhispersyncSegment.matchesAudioResourceOrTrack(
	normalizedAudioCandidates: List<String>,
	audioTrackIndex: Int?
): Boolean {
	val resourceMatches = audioResourceCandidates().any { candidate ->
		candidate in normalizedAudioCandidates
	}
	if (resourceMatches) return true
	return audioTrackIndex != null &&
		this.audioTrackIndex != null &&
		this.audioTrackIndex == audioTrackIndex
}

internal fun WhispersyncSegment.audioResourceCandidates(): List<String> =
	listOfNotNull(audioResource, audioResourceId)
		.flatMap(String::normalizedWhispersyncResourceCandidates)
		.distinct()

internal fun String.normalizedWhispersyncResourceCandidates(): List<String> {
	val cleaned = trim()
		.substringBefore('#')
		.substringBefore('?')
		.replace('\\', '/')
		.trimStart('/')
		.takeIf { it.isNotBlank() }
		?: return emptyList()
	val hasScheme = cleaned.contains("://")
	val withoutScheme = cleaned.substringAfter("://", missingDelimiterValue = cleaned)
	val urlPath = if (hasScheme) {
		withoutScheme.substringAfter('/', missingDelimiterValue = withoutScheme)
	} else {
		null
	}
	return listOfNotNull(
		cleaned,
		withoutScheme,
		urlPath
	)
		.map(::normalizedMediaOverlayResource)
		.filter { it.isNotBlank() }
		.distinct()
}
