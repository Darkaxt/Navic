package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import paige.navic.domain.repositories.BinderyWhispersyncIdentity
import kotlin.math.roundToInt

const val WordSyncIndexSchema = "bindery.whispersync.wordsync.index.v1"
const val WordSyncChapterSchema = "bindery.whispersync.wordsync.chapter.v1"
const val WordSyncTimeScale = 1000

private const val WordSyncExtractor = "bindery-epub-text"
private const val WordSyncExtractorVersion = "1"
private const val WordSyncNormalization = "raw-extracted-text-offsets"

private val WordSyncStatusEnum = mapOf(
	0 to "unmatched-audio",
	1 to "exact",
	2 to "normalized",
	3 to "fuzzy",
	4 to "semantic-number",
	5 to "review"
)

private val WordSyncMethodEnum = mapOf(
	0 to "asr-word-timestamp",
	1 to "forced-align-cue-window",
	2 to "cue-interpolated-review"
)

private val WordSyncJson = Json
private val WordSyncSha256 = Regex("sha256:[0-9a-fA-F]{64}")

data class WordSyncCoordinateBasis(
	val extractor: String,
	val extractorVersion: String,
	val normalization: String,
	val ebookTextHash: String
)

data class WordSyncIndex(
	val identity: BinderyWhispersyncIdentity,
	val generatedAt: String?,
	val timeScale: Int,
	val coordinateBasis: WordSyncCoordinateBasis,
	val statusEnum: Map<Int, String>,
	val methodEnum: Map<Int, String>,
	val chapters: List<WordSyncChapterSummary>,
	val unplaced: WordSyncUnplacedSummary?
)

data class WordSyncChapterSummary(
	val chapterKey: String,
	val spineIndex: Int,
	val ebookHref: String,
	val path: String,
	val href: String,
	val opdsHref: String?,
	val ebookStart: Int,
	val ebookEnd: Int,
	val audioRanges: List<WordSyncAudioRange>,
	val audioWordCount: Int,
	val matchedAudioWordCount: Int,
	val reviewAudioWordCount: Int,
	val unmatchedAudioWordCount: Int,
	val unmatchedEbookWordCount: Int,
	val minConfidence: Int,
	val meanConfidence: Int
)

data class WordSyncAudioRange(
	val audioResourceId: String,
	val audioTrackIndex: Int,
	val audioHref: String,
	val startMs: Long,
	val endMs: Long
)

data class WordSyncUnplacedSummary(
	val path: String,
	val href: String,
	val opdsHref: String?,
	val audioWordCount: Int
)

data class WordSyncChapter(
	val identity: BinderyWhispersyncIdentity,
	val chapterKey: String,
	val ebookHref: String,
	val spineIndex: Int,
	val ebookStart: Int,
	val ebookEnd: Int,
	val timeScale: Int,
	val tracks: List<WordSyncTrack>,
	val ebookLookup: List<WordSyncEbookLookupEntry>,
	val unmatchedEbook: List<WordSyncUnmatchedEbook>
) {
	fun wordAtAudioPosition(
		audioResourceId: String,
		audioTrackIndex: Int,
		positionMs: Long
	): WordSyncWord? = tracks
		.firstOrNull { track ->
			track.audioResourceId == audioResourceId &&
				track.audioTrackIndex == audioTrackIndex
		}
		?.words
		?.firstOrNull { word -> positionMs >= word.audioStartMs && positionMs < word.audioEndMs }

	fun wordAtEbookOffset(offset: Int): WordSyncWord? {
		var low = 0
		var high = ebookLookup.lastIndex
		var candidateIndex = -1
		while (low <= high) {
			val middle = (low + high).ushr(1)
			if (ebookLookup[middle].ebookStart <= offset) {
				candidateIndex = middle
				low = middle + 1
			} else {
				high = middle - 1
			}
		}
		if (candidateIndex < 0) return null
		var match: WordSyncWord? = null
		for (index in candidateIndex downTo 0) {
			val entry = ebookLookup[index]
			val word = tracks.getOrNull(entry.trackIndex)?.words?.getOrNull(entry.wordIndex) ?: continue
			if (offset >= word.ebookStart && offset < word.ebookEnd) {
				match = word
			}
		}
		return match
	}
}

data class WordSyncTrack(
	val audioResourceId: String,
	val audioTrackIndex: Int,
	val audioHref: String,
	val baseStartMs: Long,
	val words: List<WordSyncWord>
) {
	fun word(index: Int): WordSyncWord = words[index]
}

data class WordSyncWord(
	val audioResourceId: String,
	val audioTrackIndex: Int,
	val audioHref: String,
	val audioStartMs: Long,
	val audioEndMs: Long,
	val ebookHref: String,
	val spineIndex: Int,
	val ebookStart: Int,
	val ebookEnd: Int,
	val cueId: Int,
	val status: Int,
	val confidence: Int,
	val method: Int,
	val flags: Int
)

data class WordSyncEbookLookupEntry(
	val ebookStart: Int,
	val trackIndex: Int,
	val wordIndex: Int
)

data class WordSyncUnmatchedEbook(
	val ebookStart: Int,
	val ebookLen: Int,
	val reason: String
)

fun decodeWordSyncIndex(
	json: String,
	expectedIdentity: BinderyWhispersyncIdentity
): WordSyncIndex {
	val root = WordSyncJson.parseToJsonElement(json).jsonObject
	require(root.requiredString("schema") == WordSyncIndexSchema) { "Unsupported WordSync index schema." }
	require(root.requiredInt("version") == 1) { "Unsupported WordSync index version." }
	val identity = root.validatedIdentity(expectedIdentity)
	val timeScale = root.requiredInt("timeScale")
	require(timeScale == WordSyncTimeScale) { "Unsupported WordSync time scale." }
	val coordinateBasis = root.requiredObject("coordinateBasis").decodeCoordinateBasis()
	val statusEnum = root.optionalObject("statusEnum")
		?.requiredCodeMap("statusEnum")
		?: WordSyncStatusEnum
	val methodEnum = root.optionalObject("methodEnum")
		?.requiredCodeMap("methodEnum")
		?: WordSyncMethodEnum
	require(statusEnum == WordSyncStatusEnum) { "Unsupported WordSync status enum." }
	require(methodEnum == WordSyncMethodEnum) { "Unsupported WordSync method enum." }
	val chapters = root.requiredArray("chapters")
		.mapIndexed { index, element -> element.requiredObject("chapters[$index]").decodeChapterSummary(index) }
	require(chapters.map { it.chapterKey }.distinct().size == chapters.size) {
		"WordSync index contains duplicate chapter keys."
	}
	val unplaced = root.optionalObject("unplaced")?.decodeUnplacedSummary()
	return WordSyncIndex(
		identity = identity,
		generatedAt = root.optionalString("generatedAt"),
		timeScale = timeScale,
		coordinateBasis = coordinateBasis,
		statusEnum = statusEnum,
		methodEnum = methodEnum,
		chapters = chapters,
		unplaced = unplaced
	)
}

fun decodeWordSyncChapter(
	json: String,
	expectedIdentity: BinderyWhispersyncIdentity,
	expectedChapter: WordSyncChapterSummary
): WordSyncChapter {
	val root = WordSyncJson.parseToJsonElement(json).jsonObject
	require(root.requiredString("schema") == WordSyncChapterSchema) { "Unsupported WordSync chapter schema." }
	require(root.requiredInt("version") == 1) { "Unsupported WordSync chapter version." }
	val identity = root.validatedIdentity(expectedIdentity)
	val chapterKey = root.requiredString("chapterKey")
	val ebookHref = root.requiredString("ebookHref")
	val spineIndex = root.requiredNonNegativeInt("spineIndex")
	val ebookStart = root.requiredNonNegativeInt("ebookStart")
	val ebookEnd = root.requiredNonNegativeInt("ebookEnd")
	val timeScale = root.requiredInt("timeScale")
	require(timeScale == WordSyncTimeScale) { "Unsupported WordSync time scale." }
	require(
		chapterKey == expectedChapter.chapterKey &&
			ebookHref == expectedChapter.ebookHref &&
			spineIndex == expectedChapter.spineIndex &&
			ebookStart == expectedChapter.ebookStart &&
			ebookEnd == expectedChapter.ebookEnd
	) { "WordSync chapter does not match its index summary." }

	val tracks = root.requiredArray("tracks").mapIndexed { trackIndex, element ->
		element.requiredObject("tracks[$trackIndex]").decodeTrack(
			chapterHref = ebookHref,
			spineIndex = spineIndex,
			chapterStart = ebookStart,
			chapterEnd = ebookEnd,
			trackIndex = trackIndex
		)
	}
	require(tracks.isNotEmpty()) { "WordSync chapter has no tracks." }
	require(
		tracks.map { it.audioResourceId to it.audioTrackIndex }.distinct().size == tracks.size
	) { "WordSync chapter contains duplicate track identities." }
	validateTracksAgainstSummary(tracks, expectedChapter)

	val ebookLookup = root.requiredObject("ebookLookup").decodeLookup(tracks)
	val unmatchedEbook = root.optionalArray("unmatchedEbook")
		.orEmpty()
		.mapIndexed { index, element ->
			element.requiredObject("unmatchedEbook[$index]").decodeUnmatchedEbook(
				chapterStart = ebookStart,
				chapterEnd = ebookEnd
			)
		}
	require(unmatchedEbook.size == expectedChapter.unmatchedEbookWordCount) {
		"WordSync unmatched ebook count does not match its index summary."
	}
	return WordSyncChapter(
		identity = identity,
		chapterKey = chapterKey,
		ebookHref = ebookHref,
		spineIndex = spineIndex,
		ebookStart = ebookStart,
		ebookEnd = ebookEnd,
		timeScale = timeScale,
		tracks = tracks,
		ebookLookup = ebookLookup,
		unmatchedEbook = unmatchedEbook
	)
}

private fun JsonObject.decodeCoordinateBasis(): WordSyncCoordinateBasis {
	val basis = WordSyncCoordinateBasis(
		extractor = requiredString("extractor"),
		extractorVersion = requiredString("extractorVersion"),
		normalization = requiredString("normalization"),
		ebookTextHash = requiredString("ebookTextHash")
	)
	require(basis.extractor == WordSyncExtractor) { "Unsupported WordSync extractor." }
	require(basis.extractorVersion == WordSyncExtractorVersion) { "Unsupported WordSync extractor version." }
	require(basis.normalization == WordSyncNormalization) { "Unsupported WordSync normalization." }
	require(WordSyncSha256.matches(basis.ebookTextHash)) { "Invalid WordSync ebook text hash." }
	return basis
}

private fun JsonObject.decodeChapterSummary(index: Int): WordSyncChapterSummary {
	val ebookStart = requiredNonNegativeInt("ebookStart")
	val ebookEnd = requiredNonNegativeInt("ebookEnd")
	require(ebookEnd > ebookStart) { "WordSync chapter[$index] has an invalid ebook range." }
	val audioRanges = requiredArray("audioRanges").mapIndexed { rangeIndex, element ->
		element.requiredObject("audioRanges[$rangeIndex]").decodeAudioRange(index, rangeIndex)
	}
	require(audioRanges.isNotEmpty()) { "WordSync chapter[$index] has no audio ranges." }
	require(
		audioRanges.map { it.audioResourceId to it.audioTrackIndex }.distinct().size == audioRanges.size
	) { "WordSync chapter[$index] contains duplicate audio range identities." }
	val audioWordCount = requiredPositiveInt("audioWordCount")
	val matchedAudioWordCount = requiredNonNegativeInt("matchedAudioWordCount")
	val reviewAudioWordCount = requiredNonNegativeInt("reviewAudioWordCount")
	val unmatchedAudioWordCount = requiredNonNegativeInt("unmatchedAudioWordCount")
	require(matchedAudioWordCount + reviewAudioWordCount + unmatchedAudioWordCount == audioWordCount) {
		"WordSync chapter[$index] audio counts are inconsistent."
	}
	val minConfidence = requiredPercent("minConfidence")
	val meanConfidence = requiredPercent("meanConfidence")
	require(minConfidence <= meanConfidence) { "WordSync chapter[$index] confidence summary is inconsistent." }
	return WordSyncChapterSummary(
		chapterKey = requiredString("chapterKey"),
		spineIndex = requiredNonNegativeInt("spineIndex"),
		ebookHref = requiredString("ebookHref"),
		path = requiredString("path"),
		href = requiredString("href"),
		opdsHref = optionalString("opdsHref"),
		ebookStart = ebookStart,
		ebookEnd = ebookEnd,
		audioRanges = audioRanges,
		audioWordCount = audioWordCount,
		matchedAudioWordCount = matchedAudioWordCount,
		reviewAudioWordCount = reviewAudioWordCount,
		unmatchedAudioWordCount = unmatchedAudioWordCount,
		unmatchedEbookWordCount = requiredNonNegativeInt("unmatchedEbookWordCount"),
		minConfidence = minConfidence,
		meanConfidence = meanConfidence
	)
}

private fun JsonObject.decodeAudioRange(chapterIndex: Int, rangeIndex: Int): WordSyncAudioRange {
	val startMs = requiredNonNegativeLong("startMs")
	val endMs = requiredNonNegativeLong("endMs")
	require(endMs >= startMs) {
		"WordSync chapter[$chapterIndex] audioRanges[$rangeIndex] has an invalid range."
	}
	return WordSyncAudioRange(
		audioResourceId = requiredString("audioResourceId"),
		audioTrackIndex = requiredNonNegativeInt("audioTrackIndex"),
		audioHref = requiredString("audioHref"),
		startMs = startMs,
		endMs = endMs
	)
}

private fun JsonObject.decodeUnplacedSummary(): WordSyncUnplacedSummary = WordSyncUnplacedSummary(
	path = requiredString("path"),
	href = requiredString("href"),
	opdsHref = optionalString("opdsHref"),
	audioWordCount = requiredNonNegativeInt("audioWordCount")
)

private fun JsonObject.decodeTrack(
	chapterHref: String,
	spineIndex: Int,
	chapterStart: Int,
	chapterEnd: Int,
	trackIndex: Int
): WordSyncTrack {
	val audioResourceId = requiredString("audioResourceId")
	val audioTrackIndex = requiredNonNegativeInt("audioTrackIndex")
	val audioHref = requiredString("audioHref")
	val baseStartMs = requiredNonNegativeLong("baseStartMs")
	val audioStartDeltaMs = requiredIntArray("audioStartDeltaMs")
	val audioDurMs = requiredIntArray("audioDurMs")
	val ebookStartDelta = requiredIntArray("ebookStartDelta")
	val ebookLen = requiredIntArray("ebookLen")
	val cueId = requiredIntArray("cueId")
	val status = requiredIntArray("status")
	val confidence = requiredIntArray("confidence")
	val method = requiredIntArray("method")
	val flags = requiredIntArray("flags")
	val arrays = listOf(
		audioStartDeltaMs,
		audioDurMs,
		ebookStartDelta,
		ebookLen,
		cueId,
		status,
		confidence,
		method,
		flags
	)
	val wordCount = audioStartDeltaMs.size
	require(wordCount > 0 && arrays.all { it.size == wordCount }) {
		"WordSync track[$trackIndex] parallel arrays are inconsistent."
	}
	require(audioStartDeltaMs.first() == 0) { "WordSync track[$trackIndex] first audio delta is invalid." }
	var audioStartMs = baseStartMs
	val words = List(wordCount) { wordIndex ->
		val delta = audioStartDeltaMs[wordIndex]
		val duration = audioDurMs[wordIndex]
		val ebookDelta = ebookStartDelta[wordIndex]
		val wordEbookLen = ebookLen[wordIndex]
		require(delta >= 0) { "WordSync track[$trackIndex] has a negative audio delta." }
		require(duration >= 0) { "WordSync track[$trackIndex] has an invalid audio duration." }
		require(ebookDelta >= 0 && wordEbookLen > 0) {
			"WordSync track[$trackIndex] has an invalid ebook range."
		}
		if (wordIndex > 0) audioStartMs = audioStartMs.checkedAdd(delta, "audio start")
		val audioEndMs = audioStartMs.checkedAdd(duration, "audio end")
		val wordEbookStart = chapterStart.toLong() + ebookDelta
		val wordEbookEnd = wordEbookStart + wordEbookLen
		require(wordEbookStart >= chapterStart && wordEbookEnd <= chapterEnd) {
			"WordSync track[$trackIndex] ebook range is outside its chapter."
		}
		require(cueId[wordIndex] >= 0) { "WordSync track[$trackIndex] has an invalid cue identity." }
		require(status[wordIndex] in WordSyncStatusEnum) { "WordSync track[$trackIndex] has an unknown status." }
		require(confidence[wordIndex] in 0..100) { "WordSync track[$trackIndex] has invalid confidence." }
		require(method[wordIndex] in WordSyncMethodEnum) { "WordSync track[$trackIndex] has an unknown method." }
		require(flags[wordIndex] >= 0) { "WordSync track[$trackIndex] has invalid flags." }
		WordSyncWord(
			audioResourceId = audioResourceId,
			audioTrackIndex = audioTrackIndex,
			audioHref = audioHref,
			audioStartMs = audioStartMs,
			audioEndMs = audioEndMs,
			ebookHref = chapterHref,
			spineIndex = spineIndex,
			ebookStart = wordEbookStart.toInt(),
			ebookEnd = wordEbookEnd.toInt(),
			cueId = cueId[wordIndex],
			status = status[wordIndex],
			confidence = confidence[wordIndex],
			method = method[wordIndex],
			flags = flags[wordIndex]
		)
	}
	return WordSyncTrack(
		audioResourceId = audioResourceId,
		audioTrackIndex = audioTrackIndex,
		audioHref = audioHref,
		baseStartMs = baseStartMs,
		words = words
	)
}

private fun JsonObject.decodeLookup(tracks: List<WordSyncTrack>): List<WordSyncEbookLookupEntry> {
	val ebookStart = requiredIntArray("ebookStart")
	val trackIndex = requiredIntArray("trackIndex")
	val wordIndex = requiredIntArray("wordIndex")
	require(ebookStart.size == trackIndex.size && trackIndex.size == wordIndex.size) {
		"WordSync ebook lookup arrays are inconsistent."
	}
	val entries = ebookStart.indices.map { index ->
		val track = tracks.getOrNull(trackIndex[index])
			?: throw IllegalArgumentException("WordSync ebook lookup has an invalid track index.")
		val word = track.words.getOrNull(wordIndex[index])
			?: throw IllegalArgumentException("WordSync ebook lookup has an invalid word index.")
		require(ebookStart[index] == word.ebookStart) { "WordSync ebook lookup start is inconsistent." }
		WordSyncEbookLookupEntry(
			ebookStart = ebookStart[index],
			trackIndex = trackIndex[index],
			wordIndex = wordIndex[index]
		)
	}
	require(entries.zipWithNext().all { (first, second) -> first.ebookStart <= second.ebookStart }) {
		"WordSync ebook lookup is not sorted."
	}
	val expectedWords = tracks.sumOf { it.words.size }
	require(entries.size == expectedWords) { "WordSync ebook lookup is incomplete." }
	require(entries.map { it.trackIndex to it.wordIndex }.distinct().size == expectedWords) {
		"WordSync ebook lookup contains duplicate words."
	}
	return entries
}

private fun JsonObject.decodeUnmatchedEbook(
	chapterStart: Int,
	chapterEnd: Int
): WordSyncUnmatchedEbook {
	val ebookStart = requiredNonNegativeInt("ebookStart")
	val ebookLen = requiredPositiveInt("ebookLen")
	require(ebookStart >= chapterStart && ebookStart.toLong() + ebookLen <= chapterEnd) {
		"WordSync unmatched ebook range is outside its chapter."
	}
	return WordSyncUnmatchedEbook(
		ebookStart = ebookStart,
		ebookLen = ebookLen,
		reason = requiredString("reason")
	)
}

private fun validateTracksAgainstSummary(
	tracks: List<WordSyncTrack>,
	summary: WordSyncChapterSummary
) {
	val words = tracks.flatMap { it.words }
	require(words.size == summary.audioWordCount) { "WordSync chapter word count does not match its index summary." }
	val matched = words.count { it.status in 1..4 }
	val review = words.count { it.status == 5 }
	val unmatched = words.count { it.status == 0 }
	require(
		matched == summary.matchedAudioWordCount &&
			review == summary.reviewAudioWordCount &&
			unmatched == summary.unmatchedAudioWordCount
	) { "WordSync chapter status counts do not match its index summary." }
	val confidences = words.map { it.confidence }
	require(confidences.min() == summary.minConfidence) {
		"WordSync chapter minimum confidence does not match its index summary."
	}
	require(confidences.average().roundToInt() == summary.meanConfidence) {
		"WordSync chapter mean confidence does not match its index summary."
	}
	tracks.forEachIndexed { trackIndex, track ->
		val range = summary.audioRanges.singleOrNull {
			it.audioResourceId == track.audioResourceId &&
				it.audioTrackIndex == track.audioTrackIndex &&
				it.audioHref == track.audioHref
		} ?: throw IllegalArgumentException("WordSync track[$trackIndex] has no matching index audio range.")
		require(
			track.words.minOf { it.audioStartMs } == range.startMs &&
				track.words.maxOf { it.audioEndMs } == range.endMs
		) { "WordSync track[$trackIndex] does not match its index audio range." }
	}
	require(summary.audioRanges.size == tracks.size) {
		"WordSync index audio ranges do not match chapter tracks."
	}
}

private fun JsonObject.validatedIdentity(
	expectedIdentity: BinderyWhispersyncIdentity
): BinderyWhispersyncIdentity {
	require(requiredPositiveLong("bookId") == expectedIdentity.bookId) {
		"WordSync book identity mismatch."
	}
	require(requiredPositiveLong("ebookBookFileId") == expectedIdentity.ebookBookFileId) {
		"WordSync ebook identity mismatch."
	}
	require(requiredPositiveLong("audiobookBookFileId") == expectedIdentity.audiobookBookFileId) {
		"WordSync audiobook identity mismatch."
	}
	val artifactId = optionalNonNegativeLong("artifactId")
	require(artifactId == null || artifactId == 0L || artifactId == expectedIdentity.artifactId) {
		"WordSync artifact identity mismatch."
	}
	return expectedIdentity
}

private fun JsonObject.requiredObject(key: String): JsonObject =
	this[key] as? JsonObject ?: throw IllegalArgumentException("WordSync field '$key' must be an object.")

private fun JsonElement.requiredObject(field: String): JsonObject =
	this as? JsonObject ?: throw IllegalArgumentException("WordSync field '$field' must be an object.")

private fun JsonObject.optionalObject(key: String): JsonObject? {
	val element = this[key] ?: return null
	return element as? JsonObject ?: throw IllegalArgumentException("WordSync field '$key' must be an object.")
}

private fun JsonObject.requiredArray(key: String): JsonArray =
	this[key] as? JsonArray ?: throw IllegalArgumentException("WordSync field '$key' must be an array.")

private fun JsonObject.optionalArray(key: String): JsonArray? {
	val element = this[key] ?: return null
	return element as? JsonArray ?: throw IllegalArgumentException("WordSync field '$key' must be an array.")
}

private fun JsonObject.requiredString(key: String): String {
	val primitive = this[key] as? JsonPrimitive
	val value = primitive?.takeIf { it.isString }?.content
	require(!value.isNullOrEmpty() && value.isNotBlank() && value == value.trim()) {
		"WordSync field '$key' must be a non-empty string without surrounding whitespace."
	}
	return value
}

private fun JsonObject.optionalString(key: String): String? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive
	val value = primitive?.takeIf { it.isString }?.content
	require(!value.isNullOrEmpty() && value.isNotBlank() && value == value.trim()) {
		"WordSync field '$key' must be a non-empty string without surrounding whitespace."
	}
	return value
}

private fun JsonObject.requiredInt(key: String): Int {
	val primitive = this[key] as? JsonPrimitive
	return primitive?.takeUnless { it.isString || it.booleanOrNull != null }?.intOrNull
		?: throw IllegalArgumentException("WordSync field '$key' must be an integer.")
}

private fun JsonObject.requiredLong(key: String): Long {
	val primitive = this[key] as? JsonPrimitive
	return primitive?.takeUnless { it.isString || it.booleanOrNull != null }?.longOrNull
		?: throw IllegalArgumentException("WordSync field '$key' must be an integer.")
}

private fun JsonObject.optionalNonNegativeLong(key: String): Long? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive
	val value = primitive?.takeUnless { it.isString || it.booleanOrNull != null }?.longOrNull
		?: throw IllegalArgumentException("WordSync field '$key' must be an integer.")
	require(value >= 0L) { "WordSync field '$key' must be non-negative." }
	return value
}

private fun JsonObject.requiredNonNegativeInt(key: String): Int =
	requiredInt(key).also { require(it >= 0) { "WordSync field '$key' must be non-negative." } }

private fun JsonObject.requiredPositiveInt(key: String): Int =
	requiredInt(key).also { require(it > 0) { "WordSync field '$key' must be positive." } }

private fun JsonObject.requiredNonNegativeLong(key: String): Long =
	requiredLong(key).also { require(it >= 0L) { "WordSync field '$key' must be non-negative." } }

private fun JsonObject.requiredPositiveLong(key: String): Long =
	requiredLong(key).also { require(it > 0L) { "WordSync field '$key' must be positive." } }

private fun JsonObject.requiredPercent(key: String): Int =
	requiredInt(key).also { require(it in 0..100) { "WordSync field '$key' must be a percentage." } }

private fun JsonObject.requiredIntArray(key: String): List<Int> = requiredArray(key).mapIndexed { index, element ->
	val primitive = element as? JsonPrimitive
	primitive?.takeUnless { it.isString || it.booleanOrNull != null }?.intOrNull
		?: throw IllegalArgumentException("WordSync field '$key[$index]' must be an integer.")
}

private fun JsonObject.requiredCodeMap(field: String): Map<Int, String> = entries.map { (key, element) ->
	val code = key.toIntOrNull()
		?: throw IllegalArgumentException("WordSync field '$field' contains an invalid code.")
	val primitive = element as? JsonPrimitive
	val label = primitive?.takeIf { it.isString }?.content
	require(!label.isNullOrEmpty() && label.isNotBlank() && label == label.trim()) {
		"WordSync field '$field' contains an invalid label."
	}
	code to label
}.toMap()

private fun Long.checkedAdd(value: Int, field: String): Long {
	require(value >= 0 && this <= Long.MAX_VALUE - value) { "WordSync $field overflows." }
	return this + value
}
