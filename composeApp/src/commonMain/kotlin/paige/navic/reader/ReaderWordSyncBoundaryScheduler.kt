package paige.navic.reader

import kotlin.math.ceil

fun interface ReaderWordSyncBoundaryCancellation {
	fun cancel()
}

internal data class ReaderWordSyncTimelineSnapshot(
	val sessionGeneration: Long,
	val timelineRevision: Long,
	val audioResourceId: String,
	val audioTrackIndex: Int,
	val positionMs: Long,
	val playbackSpeed: Float,
	val isPlaying: Boolean
) {
	init {
		require(sessionGeneration >= 0L)
		require(timelineRevision >= 0L)
		require(audioResourceId.isNotBlank() && audioResourceId == audioResourceId.trim())
		require(audioTrackIndex >= 0)
		require(positionMs >= 0L)
	}
}

internal data class ReaderWordSyncBoundary(
	val sequence: Long,
	val wordOrdinalWithinTrack: Int,
	val word: WordSyncWord
) {
	init {
		require(sequence >= 0L)
		require(wordOrdinalWithinTrack >= 0)
		require(word.status in 1..4)
	}

	val audioStartMs: Long
		get() = word.audioStartMs
}

internal data class ReaderWordSyncBoundaryDispatch(
	val boundary: ReaderWordSyncBoundary,
	val timeline: ReaderWordSyncTimelineSnapshot,
	val coalescedCount: Int
) {
	init {
		require(coalescedCount >= 0)
	}
}

internal fun WordSyncTrack.readerWordSyncBoundaries(): List<ReaderWordSyncBoundary> =
	words.mapIndexedNotNull { wordIndex, word ->
		word.takeIf { it.status in 1..4 }?.let {
			ReaderWordSyncBoundary(
				sequence = wordIndex.toLong(),
				wordOrdinalWithinTrack = wordIndex,
				word = it
			)
		}
	}

internal class ReaderWordSyncBoundaryScheduler(
	private val currentTimeline: () -> ReaderWordSyncTimelineSnapshot?,
	private val schedule: (
		delayMs: Long,
		action: () -> Unit
	) -> ReaderWordSyncBoundaryCancellation,
	private val onBoundary: (ReaderWordSyncBoundaryDispatch) -> Unit
) {
	private data class PublishedBoundary(
		val sessionGeneration: Long,
		val audioResourceId: String,
		val audioTrackIndex: Int,
		val sequence: Long,
		val wordOrdinalWithinTrack: Int,
		val audioStartMs: Long
	)

	private var boundaries: List<ReaderWordSyncBoundary> = emptyList()
	private var pending: ReaderWordSyncBoundaryCancellation? = null
	private var scheduleEpoch = 0L
	private var publishedBoundary: PublishedBoundary? = null

	fun replaceTimeline(boundaries: List<ReaderWordSyncBoundary>) {
		require(boundaries.zipWithNext().all { (first, second) ->
			first.audioStartMs <= second.audioStartMs
		}) { "WordSync boundaries must be ordered by audio start." }
		this.boundaries = boundaries
		if (boundaries.isEmpty()) publishedBoundary = null
		rebuild()
	}

	fun refreshTimeline() {
		rebuild()
	}

	fun stop() {
		boundaries = emptyList()
		publishedBoundary = null
		cancelPending()
	}

	private fun rebuild() {
		cancelPending()
		val snapshot = currentTimeline()
		if (snapshot == null) {
			publishedBoundary = null
			return
		}
		publishCurrentAndSchedule(snapshot)
	}

	private fun cancelPending() {
		scheduleEpoch += 1L
		pending?.cancel()
		pending = null
	}

	private fun publishCurrentAndSchedule(snapshot: ReaderWordSyncTimelineSnapshot) {
		if (!snapshot.canSchedule()) {
			publishedBoundary = null
			return
		}
		val currentIndex = boundaries.indexOfLast { boundary ->
			boundary.matches(snapshot) && boundary.audioStartMs <= snapshot.positionMs
		}
		if (currentIndex >= 0) {
			publishBoundary(currentIndex, snapshot, coalescedCount = 0)
		}
		scheduleNext(snapshot)
	}

	private fun publishBoundary(
		index: Int,
		snapshot: ReaderWordSyncTimelineSnapshot,
		coalescedCount: Int
	) {
		val boundary = boundaries[index]
		val publication = PublishedBoundary(
			sessionGeneration = snapshot.sessionGeneration,
			audioResourceId = snapshot.audioResourceId,
			audioTrackIndex = snapshot.audioTrackIndex,
			sequence = boundary.sequence,
			wordOrdinalWithinTrack = boundary.wordOrdinalWithinTrack,
			audioStartMs = boundary.audioStartMs
		)
		if (publication == publishedBoundary) return
		publishedBoundary = publication
		onBoundary(
			ReaderWordSyncBoundaryDispatch(
				boundary = boundary,
				timeline = snapshot,
				coalescedCount = coalescedCount
			)
		)
	}

	private fun scheduleNext(snapshot: ReaderWordSyncTimelineSnapshot) {
		if (!snapshot.canSchedule()) return
		val nextIndex = boundaries.indexOfFirst { boundary ->
			boundary.matches(snapshot) && boundary.audioStartMs > snapshot.positionMs
		}
		if (nextIndex < 0) return
		val boundary = boundaries[nextIndex]
		val delayMs = ceil(
			(boundary.audioStartMs - snapshot.positionMs).toDouble() /
				snapshot.playbackSpeed.toDouble()
		).toLong().coerceAtLeast(1L)
		val epoch = scheduleEpoch
		val sessionGeneration = snapshot.sessionGeneration
		val timelineRevision = snapshot.timelineRevision
		pending = schedule(delayMs) {
			if (epoch != scheduleEpoch) return@schedule
			scheduleEpoch += 1L
			pending = null
			val verified = currentTimeline() ?: run {
				publishedBoundary = null
				return@schedule
			}
			if (!verified.canSchedule()) {
				publishedBoundary = null
				return@schedule
			}
			if (
				verified.sessionGeneration != sessionGeneration ||
				verified.timelineRevision != timelineRevision ||
				!boundary.matches(verified)
			) {
				publishCurrentAndSchedule(verified)
				return@schedule
			}
			val currentIndex = boundaries.indexOfLast { candidate ->
				candidate.matches(verified) && candidate.audioStartMs <= verified.positionMs
			}
			if (currentIndex >= nextIndex) {
				publishBoundary(
					index = currentIndex,
					snapshot = verified,
					coalescedCount = currentIndex - nextIndex
				)
			}
			scheduleNext(verified)
		}
	}

	private fun ReaderWordSyncTimelineSnapshot.canSchedule(): Boolean =
		isPlaying && playbackSpeed.isFinite() && playbackSpeed > 0f

	private fun ReaderWordSyncBoundary.matches(
		snapshot: ReaderWordSyncTimelineSnapshot
	): Boolean =
		word.audioResourceId == snapshot.audioResourceId &&
			word.audioTrackIndex == snapshot.audioTrackIndex
}
