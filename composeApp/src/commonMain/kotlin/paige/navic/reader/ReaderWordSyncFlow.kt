package paige.navic.reader

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/** A silence at least this long between two words ends a phrase: the highlight may rest there. */
internal const val ReaderWordSyncFlowPauseMs = 700L

/** Words either side averaged into a word's flow start. */
internal const val ReaderWordSyncFlowSmoothingRadius = 2

/** Smoothing never moves a word start further than this from its ASR time. */
internal const val ReaderWordSyncFlowMaxShiftMs = 300L

/** The last word of a phrase keeps the highlight at least this long. */
internal const val ReaderWordSyncFlowMinPhraseEndMs = 150L

/**
 * Flow timing for word boundaries. ASR word stamps are noisy (measured on Alcatraz chapter 1: a
 * quarter of word starts 0.3 s or more late, word ends often early, 3% zero-length), so stepping
 * the highlight on raw stamps jumps and stalls. Inside a phrase (no silence of
 * [ReaderWordSyncFlowPauseMs]), each start becomes the mean of its neighbours' starts within
 * [ReaderWordSyncFlowSmoothingRadius] (symmetric, so evenly paced speech is unchanged), bounded by
 * [ReaderWordSyncFlowMaxShiftMs] and kept monotonic; each word then lasts until the next flow
 * start, so the highlight never drops between words. Returned in flow-start order.
 */
internal fun List<ReaderWordSyncBoundary>.withReaderWordSyncFlowTiming(): List<ReaderWordSyncBoundary> =
	groupBy { it.word.audioTrackIndex }
		.values
		.flatMap { track -> track.sortedBy { it.word.audioStartMs }.flowTimedTrack() }
		.sortedBy { it.flowStartMs }

private fun List<ReaderWordSyncBoundary>.flowTimedTrack(): List<ReaderWordSyncBoundary> {
	if (isEmpty()) return this
	val starts = map { it.word.audioStartMs }
	val ends = map { maxOf(it.word.audioEndMs, it.word.audioStartMs) }
	// phraseStart[i]/phraseEnd[i]: the index range of the phrase containing word i.
	val phraseBreakAfter = BooleanArray(size) { index ->
		index == lastIndex || starts[index + 1] - ends[index] >= ReaderWordSyncFlowPauseMs
	}
	val phraseStart = IntArray(size)
	val phraseEnd = IntArray(size)
	var first = 0
	for (index in indices) {
		if (phraseBreakAfter[index]) {
			for (member in first..index) {
				phraseStart[member] = first
				phraseEnd[member] = index
			}
			first = index + 1
		}
	}
	val flowStarts = LongArray(size)
	for (index in indices) {
		val radius = minOf(
			ReaderWordSyncFlowSmoothingRadius,
			index - phraseStart[index],
			phraseEnd[index] - index
		)
		var sum = 0L
		for (neighbour in index - radius..index + radius) sum += starts[neighbour]
		val mean = (sum.toDouble() / (2 * radius + 1)).roundToLong()
		val shift = (mean - starts[index]).coerceIn(-ReaderWordSyncFlowMaxShiftMs, ReaderWordSyncFlowMaxShiftMs)
		val previous = if (index > 0) flowStarts[index - 1] else 0L
		flowStarts[index] = maxOf(starts[index] + shift, previous, 0L)
	}
	return mapIndexed { index, boundary ->
		val start = flowStarts[index]
		val end = if (phraseBreakAfter[index]) {
			val phraseEndMs = maxOf(ends[index], start + ReaderWordSyncFlowMinPhraseEndMs, boundary.trailingSpeechEndMs())
			if (index < lastIndex) minOf(phraseEndMs, flowStarts[index + 1]) else phraseEndMs
		} else {
			flowStarts[index + 1]
		}
		boundary.copy(flowStartMs = start, flowEndMs = maxOf(end, start))
	}
}

/**
 * When the narration of the unmatched text painted after a phrase's last word ends: the real audio
 * end of unmatched words, else the word's own speaking rate over the extra bytes (capped at 1.5 s).
 */
private fun ReaderWordSyncBoundary.trailingSpeechEndMs(): Long {
	val wordEnd = maxOf(word.audioEndMs, word.audioStartMs)
	trailingAudioEndMs?.let { return maxOf(it, wordEnd) }
	val extraBytes = paintByteEnd - word.ebookEnd
	if (extraBytes <= 0) return wordEnd
	val wordBytes = maxOf(word.ebookEnd - word.ebookStart, 1)
	val msPerByte = (wordEnd - word.audioStartMs).toDouble() / wordBytes
	return wordEnd + minOf((msPerByte * extraBytes).roundToLong(), 1_500L)
}

/**
 * The audio clock for the native flow highlight of one word boundary: the head sweeps across the
 * word from [flowStartMs] to [flowEndMs], extrapolated from a playback position sampled at
 * [anchorMark]. Paused clocks hold their position.
 */
data class ReaderWordSyncFlowClock(
	val boundarySequence: Long,
	val flowStartMs: Long,
	val flowEndMs: Long,
	val anchorPositionMs: Long,
	val anchorMark: TimeSource.Monotonic.ValueTimeMark,
	val playbackSpeed: Float,
	val playing: Boolean
) {
	/** Reading progress through the word, 0 to 1. */
	fun progress(): Float {
		val span = flowEndMs - flowStartMs
		if (span <= 0L) return 1f
		val elapsedMs = if (playing && playbackSpeed.isFinite() && playbackSpeed > 0f) {
			anchorMark.elapsedNow().inWholeMilliseconds * playbackSpeed.toDouble()
		} else {
			0.0
		}
		val positionMs = anchorPositionMs + elapsedMs
		return ((positionMs - flowStartMs) / span).coerceIn(0.0, 1.0).toFloat()
	}

	/** True while the head can still move: a frame loop is only needed then. */
	fun animating(): Boolean = playing && progress() < 1f
}

internal fun ReaderWordSyncBoundary.flowClock(
	timeline: ReaderWordSyncTimelineSnapshot,
	anchorMark: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
): ReaderWordSyncFlowClock = ReaderWordSyncFlowClock(
	boundarySequence = sequence,
	flowStartMs = flowStartMs,
	flowEndMs = flowEndMs,
	anchorPositionMs = if (timeline.audioTrackIndex == word.audioTrackIndex) timeline.positionMs else flowEndMs,
	anchorMark = anchorMark,
	playbackSpeed = timeline.playbackSpeed,
	playing = timeline.isPlaying && timeline.audioTrackIndex == word.audioTrackIndex
)

/** Whether two clocks would draw the same head now (avoids recomposition-only churn). */
internal fun ReaderWordSyncFlowClock.sameMotionAs(other: ReaderWordSyncFlowClock?): Boolean =
	other != null &&
		other.boundarySequence == boundarySequence &&
		other.flowStartMs == flowStartMs &&
		other.flowEndMs == flowEndMs &&
		other.playing == playing &&
		other.playbackSpeed == playbackSpeed &&
		abs(other.progress() - progress()) < 0.02f
