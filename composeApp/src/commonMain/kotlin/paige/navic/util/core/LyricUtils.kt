package paige.navic.util.core

import paige.navic.domain.models.lyrics.LyricsWord
import kotlin.time.Duration

fun List<LyricsWord>.calculateWordProgress(
	fullText: String,
	currentDuration: Duration
): Float {
	if (isEmpty() || fullText.isEmpty()) return 0f

	val currentMs = currentDuration.inWholeMilliseconds
	val totalChars = fullText.length.toFloat()

	if (currentMs < first().time.inWholeMilliseconds) return 0f

	var currentCharacterIndex = 0

	for (i in indices) {
		val word = get(i)
		val wordStartMs = word.time.inWholeMilliseconds
		val wordEndMs = wordStartMs + word.duration.inWholeMilliseconds

		val wordIndexInString =
			fullText.indexOf(word.text, startIndex = currentCharacterIndex, ignoreCase = true)

		if (wordIndexInString == -1) {
			continue
		}

		if (currentMs in wordStartMs until wordEndMs) {
			val wordProgress =
				(currentMs - wordStartMs).toFloat() / word.duration.inWholeMilliseconds.coerceAtLeast(
					1
				)
			val charProgressWithinWord = word.text.length * wordProgress

			return (wordIndexInString + charProgressWithinWord) / totalChars
		}

		if (currentMs < wordStartMs) {
			return wordIndexInString / totalChars
		}

		currentCharacterIndex = wordIndexInString + word.text.length
	}

	return 1f
}

data class KaraokeLineProgressScope(
	val totalWidth: Float,
	val accumulatedWidth: Float
)

fun karaokeLineProgressScopes(
	text: String,
	lineStartOffsets: List<Int>,
	lineWidths: List<Float>
): List<KaraokeLineProgressScope> {
	if (lineStartOffsets.isEmpty() || lineWidths.isEmpty()) return emptyList()

	val lineCount = minOf(lineStartOffsets.size, lineWidths.size)
	val sourceLineIndexes = (0 until lineCount).map { index ->
		sourceLineIndexForOffset(text, lineStartOffsets[index])
	}
	val totalWidthBySourceLine = linkedMapOf<Int, Float>()

	(0 until lineCount).forEach { index ->
		val sourceLineIndex = sourceLineIndexes[index]
		totalWidthBySourceLine[sourceLineIndex] =
			(totalWidthBySourceLine[sourceLineIndex] ?: 0f) + lineWidths[index]
	}

	val accumulatedWidthBySourceLine = mutableMapOf<Int, Float>()
	return (0 until lineCount).map { index ->
		val sourceLineIndex = sourceLineIndexes[index]
		val accumulatedWidth = accumulatedWidthBySourceLine[sourceLineIndex] ?: 0f
		accumulatedWidthBySourceLine[sourceLineIndex] = accumulatedWidth + lineWidths[index]
		KaraokeLineProgressScope(
			totalWidth = totalWidthBySourceLine[sourceLineIndex] ?: lineWidths[index],
			accumulatedWidth = accumulatedWidth
		)
	}
}

fun karaokeLinePixelTarget(
	progress: Float,
	totalWidth: Float,
	accumulatedWidth: Float,
	feather: Float
): Float {
	val target = ((totalWidth + (feather * 2)) * progress.coerceIn(0f, 1f)) - feather
	return target - accumulatedWidth
}

private fun sourceLineIndexForOffset(text: String, offset: Int): Int {
	val safeOffset = offset.coerceIn(0, text.length)
	return text.take(safeOffset).count { it == '\n' }
}
