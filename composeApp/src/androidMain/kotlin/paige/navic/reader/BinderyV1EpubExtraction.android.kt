package paige.navic.reader

import com.fleeksoft.ksoup.nodes.Entities
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class BinderyV1PrivateChapterText(
	private val extractedText: String,
	val sourceHash: String,
	val byteLength: Int,
	val textHash: String,
	private val tokenRanges: LongArray
) {
	val isReadable: Boolean
		get() = extractedText.isNotEmpty()

	val tokenCount: Int
		get() = tokenRanges.size

	fun containsTokenRange(start: Int, end: Int): Boolean =
		tokenRanges.binarySearch(binderyByteRangeKey(start, end)) >= 0
}

internal fun extractBinderyV1ChapterText(
	raw: ByteArray,
	maxExtractedBytes: Int,
	maxTokenCount: Int
): BinderyV1PrivateChapterText {
	val text = extractBinderyV1Text(raw.binderyStrictUtf8())
	val textBytes = text.encodeToByteArray()
	if (textBytes.size > maxExtractedBytes) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
	}
	val tokenized = text.binderyTokenRanges(maxTokenCount)
	if (tokenized.byteLength != textBytes.size) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
	}
	return BinderyV1PrivateChapterText(
		extractedText = text,
		sourceHash = "sha256:${raw.sha256LowerHex()}",
		byteLength = textBytes.size,
		textHash = "sha256:${textBytes.sha256LowerHex()}",
		tokenRanges = tokenized.ranges
	)
}

internal fun extractBinderyV1Text(source: String): String = source
	.replaceBinderyScriptAndStyleElements()
	.replaceBinderySelectedClosingTags()
	.replaceBinderyGenericTags()
	.goHtmlUnescapeString()
	.let { BinderyAsciiWhitespaceRegex.replace(it, " ") }
	.goTrimSpace()

private fun String.replaceBinderyScriptAndStyleElements(): String {
	val scriptClosings = binderyFoldedLiteralPositions(BinderyScriptClosingTag)
	val styleClosings = binderyFoldedLiteralPositions(BinderyStyleClosingTag)
	var scriptClosingIndex = 0
	var styleClosingIndex = 0
	var copyStart = 0
	var scanIndex = 0
	var replaced = false
	val output = StringBuilder(length)
	while (scanIndex < length) {
		if (this[scanIndex] != '<') {
			scanIndex += 1
			continue
		}
		val isScript = binderyMatchesFoldedLiteral(scanIndex, BinderyScriptOpeningTag)
		val isStyle = !isScript && binderyMatchesFoldedLiteral(scanIndex, BinderyStyleOpeningTag)
		if (!isScript && !isStyle) {
			scanIndex += 1
			continue
		}
		val openingTag = if (isScript) BinderyScriptOpeningTag else BinderyStyleOpeningTag
		val closingTag = if (isScript) BinderyScriptClosingTag else BinderyStyleClosingTag
		val closingPositions = if (isScript) scriptClosings else styleClosings
		var closingIndex = if (isScript) scriptClosingIndex else styleClosingIndex
		val minimumClosingIndex = scanIndex + openingTag.length
		while (closingIndex < closingPositions.size && closingPositions[closingIndex] < minimumClosingIndex) {
			closingIndex += 1
		}
		if (isScript) scriptClosingIndex = closingIndex
		else styleClosingIndex = closingIndex
		if (closingIndex >= closingPositions.size) {
			scanIndex += 1
			continue
		}
		output.append(this, copyStart, scanIndex)
		output.append(' ')
		scanIndex = closingPositions[closingIndex] + closingTag.length
		copyStart = scanIndex
		replaced = true
	}
	if (!replaced) return this
	output.append(this, copyStart, length)
	return output.toString()
}

private fun String.replaceBinderySelectedClosingTags(): String {
	var copyStart = 0
	var scanIndex = 0
	var replaced = false
	val output = StringBuilder(length)
	while (scanIndex < length) {
		if (this[scanIndex] != '<') {
			scanIndex += 1
			continue
		}
		val tag = BinderySelectedClosingTags.firstOrNull { candidate ->
			binderyMatchesFoldedLiteral(scanIndex, candidate)
		}
		if (tag == null) {
			scanIndex += 1
			continue
		}
		output.append(this, copyStart, scanIndex)
		output.append(". ")
		scanIndex += tag.length
		copyStart = scanIndex
		replaced = true
	}
	if (!replaced) return this
	output.append(this, copyStart, length)
	return output.toString()
}

private fun String.replaceBinderyGenericTags(): String {
	var copyStart = 0
	var scanIndex = 0
	var replaced = false
	val output = StringBuilder(length)
	while (scanIndex < length) {
		val openingIndex = indexOf('<', scanIndex)
		if (openingIndex < 0) break
		val closingIndex = indexOf('>', openingIndex + 1)
		if (closingIndex < 0) break
		if (closingIndex == openingIndex + 1) {
			scanIndex = closingIndex + 1
			continue
		}
		output.append(this, copyStart, openingIndex)
		output.append(' ')
		scanIndex = closingIndex + 1
		copyStart = scanIndex
		replaced = true
	}
	if (!replaced) return this
	output.append(this, copyStart, length)
	return output.toString()
}

private fun String.binderyFoldedLiteralPositions(literal: String): IntArray {
	val positions = BinderyIntArrayBuilder()
	var scanIndex = 0
	while (scanIndex < length) {
		val candidate = indexOf('<', scanIndex)
		if (candidate < 0) break
		if (binderyMatchesFoldedLiteral(candidate, literal)) positions.add(candidate)
		scanIndex = candidate + 1
	}
	return positions.toIntArray()
}

private fun String.binderyMatchesFoldedLiteral(index: Int, literal: String): Boolean =
	index >= 0 && index + literal.length <= length &&
		regionMatches(index, literal, 0, literal.length, ignoreCase = true)

private class BinderyIntArrayBuilder {
	private var values = IntArray(16)
	private var size = 0

	fun add(value: Int) {
		if (size == values.size) values = values.copyOf(values.size * 2)
		values[size] = value
		size += 1
	}

	fun toIntArray(): IntArray = values.copyOf(size)
}

internal fun ByteArray.binderyStrictUtf8(): String = try {
	StandardCharsets.UTF_8.newDecoder()
		.onMalformedInput(CodingErrorAction.REPORT)
		.onUnmappableCharacter(CodingErrorAction.REPORT)
		.decode(ByteBuffer.wrap(this))
		.toString()
} catch (_: Exception) {
	wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
}

internal fun binderyAggregateTextHash(documents: List<BinderyV1EpubDocument>): String {
	val digest = MessageDigest.getInstance("SHA-256")
	documents.forEach { document ->
		digest.update(document.href.encodeToByteArray())
		digest.update('\n'.code.toByte())
		digest.update(document.content.textHash.encodeToByteArray())
		digest.update('\n'.code.toByte())
	}
	return "sha256:${digest.digest().lowerHex()}"
}

private fun binderyByteRangeKey(start: Int, end: Int): Long =
	(start.toLong() shl 32) or (end.toLong() and 0xffffffffL)

private data class BinderyTokenRanges(
	val byteLength: Int,
	val ranges: LongArray
)

private fun String.binderyTokenRanges(maxTokenCount: Int): BinderyTokenRanges {
	val ranges = BinderyLongArrayBuilder(maxTokenCount)
	var characterIndex = 0
	var byteOffset = 0
	BinderyWordTokenRegex.findAll(this).forEach { match ->
		val tokenStart = match.range.first
		val tokenEnd = match.range.last + 1
		byteOffset += utf8Length(characterIndex, tokenStart)
		val startByteOffset = byteOffset
		byteOffset += utf8Length(tokenStart, tokenEnd)
		ranges.add(binderyByteRangeKey(startByteOffset, byteOffset))
		characterIndex = tokenEnd
	}
	byteOffset += utf8Length(characterIndex, length)
	return BinderyTokenRanges(byteLength = byteOffset, ranges = ranges.toLongArray())
}

private fun String.utf8Length(start: Int, end: Int): Int {
	var characterIndex = start
	var byteLength = 0
	while (characterIndex < end) {
		val codePoint = Character.codePointAt(this, characterIndex)
		byteLength += when {
			codePoint <= 0x7f -> 1
			codePoint <= 0x7ff -> 2
			codePoint <= 0xffff -> 3
			else -> 4
		}
		characterIndex += Character.charCount(codePoint)
	}
	return byteLength
}

private class BinderyLongArrayBuilder(
	private val maxSize: Int
) {
	private var values = LongArray(minOf(16, maxSize))
	private var size = 0

	fun add(value: Long) {
		if (size >= maxSize) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
		}
		if (size == values.size) {
			val newSize = minOf(maxSize.toLong(), maxOf(1L, values.size.toLong() * 2L)).toInt()
			values = values.copyOf(newSize)
		}
		values[size] = value
		size += 1
	}

	fun toLongArray(): LongArray = values.copyOf(size)
}

internal fun String.goHtmlUnescapeString(): String {
	val output = StringBuilder(length)
	var sourceIndex = 0
	BinderyNumericEntityRegex.findAll(this).forEach { match ->
		output.append(Entities.unescape(substring(sourceIndex, match.range.first)))
		output.append(match.value.decodeGoNumericEntity())
		sourceIndex = match.range.last + 1
	}
	output.append(Entities.unescape(substring(sourceIndex)))
	return output.toString()
}

private fun String.decodeGoNumericEntity(): String {
	val hexadecimal = length > 2 && (this[2] == 'x' || this[2] == 'X')
	val digitStart = if (hexadecimal) 3 else 2
	val digitEnd = if (endsWith(';')) lastIndex else length
	val base = if (hexadecimal) 16 else 10
	var value = 0L
	var overflow = false
	for (index in digitStart until digitEnd) {
		val digit = this[index].digitToInt(base)
		if (value > (0x10ffffL - digit) / base) {
			overflow = true
		} else if (!overflow) {
			value = value * base + digit
		}
	}
	val codePoint = when {
		overflow || value == 0L || value in 0xd800L..0xdfffL -> 0xfffd
		value in 0x80L..0x9fL -> BinderyGoC1ReplacementTable[(value - 0x80L).toInt()]
		else -> value.toInt()
	}
	return String(Character.toChars(codePoint))
}

internal fun String.goTrimSpace(): String {
	var start = 0
	var end = length
	while (start < end) {
		val codePoint = Character.codePointAt(this, start)
		if (!codePoint.isGoSpace()) break
		start += Character.charCount(codePoint)
	}
	while (end > start) {
		val codePoint = Character.codePointBefore(this, end)
		if (!codePoint.isGoSpace()) break
		end -= Character.charCount(codePoint)
	}
	return if (start == 0 && end == length) this else substring(start, end)
}

private fun Int.isGoSpace(): Boolean =
	this in 0x09..0x0d ||
		this == 0x20 ||
		this == 0x85 ||
		this == 0xa0 ||
		this == 0x1680 ||
		this in 0x2000..0x200a ||
		this == 0x2028 ||
		this == 0x2029 ||
		this == 0x202f ||
		this == 0x205f ||
		this == 0x3000

private fun ByteArray.sha256LowerHex(): String =
	MessageDigest.getInstance("SHA-256").digest(this).lowerHex()

private fun ByteArray.lowerHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val BinderyScriptOpeningTag = "<script"
private const val BinderyScriptClosingTag = "</script>"
private const val BinderyStyleOpeningTag = "<style"
private const val BinderyStyleClosingTag = "</style>"
private val BinderySelectedClosingTags = arrayOf(
	"</p>",
	"</h1>",
	"</h2>",
	"</h3>",
	"</h4>",
	"</h5>",
	"</h6>",
	"</div>",
	"</section>",
	"</li>",
	"</br>"
)
private val BinderyAsciiWhitespaceRegex = Regex("[\\t\\n\\u000c\\r ]+")
private val BinderyWordTokenRegex = Regex("[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?")
private val BinderyNumericEntityRegex = Regex("&#(?:[xX][0-9A-Fa-f]+|[0-9]+);?")
private val BinderyGoC1ReplacementTable = intArrayOf(
	0x20ac, 0x0081, 0x201a, 0x0192, 0x201e, 0x2026, 0x2020, 0x2021,
	0x02c6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008d, 0x017d, 0x008f,
	0x0090, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2013, 0x2014,
	0x02dc, 0x2122, 0x0161, 0x203a, 0x0153, 0x009d, 0x017e, 0x0178
)
