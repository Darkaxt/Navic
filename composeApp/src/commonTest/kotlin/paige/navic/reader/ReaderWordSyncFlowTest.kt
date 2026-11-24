package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ReaderWordSyncFlowTest {
	@Test
	fun evenlyPacedSpeechKeepsItsStartsAndBecomesGapless() {
		val flow = boundaries(0L to 200L, 250L to 450L, 500L to 700L, 750L to 950L).withReaderWordSyncFlowTiming()

		assertEquals(listOf(0L, 250L, 500L, 750L), flow.map { it.flowStartMs })
		assertEquals(listOf(250L, 500L, 750L), flow.dropLast(1).map { it.flowEndMs })
		assertEquals(950L, flow.last().flowEndMs)
	}

	@Test
	fun aLateAsrStartIsPulledTowardsItsNeighboursWithinTheShiftLimit() {
		val flow = boundaries(
			0L to 200L,
			250L to 450L,
			900L to 1_000L,
			750L to 950L,
			1_000L to 1_200L
		).sortedBy { it.word.audioStartMs }.withReaderWordSyncFlowTiming()

		val starts = flow.map { it.flowStartMs }
		assertEquals(starts.sorted(), starts, "flow starts stay monotonic")
		flow.forEach { boundary ->
			assertTrue(kotlin.math.abs(boundary.flowStartMs - boundary.word.audioStartMs) <= ReaderWordSyncFlowMaxShiftMs)
		}
		flow.zipWithNext().forEach { (current, next) ->
			assertEquals(next.flowStartMs, current.flowEndMs, "no highlight gap inside a phrase")
		}
	}

	@Test
	fun aLongPauseEndsThePhraseAndTheHighlightRestsThere() {
		val flow = boundaries(0L to 200L, 250L to 260L, 2_000L to 2_300L).withReaderWordSyncFlowTiming()

		assertEquals(0L, flow[0].flowStartMs)
		assertEquals(2_000L, flow[2].flowStartMs, "no smoothing across the pause")
		val phraseEnd = flow[1]
		assertEquals(phraseEnd.flowStartMs + ReaderWordSyncFlowMinPhraseEndMs, phraseEnd.flowEndMs)
		assertTrue(phraseEnd.flowEndMs < flow[2].flowStartMs)
	}

	@Test
	fun zeroLengthWordsStillGetAFlowSpan() {
		val flow = boundaries(0L to 0L, 300L to 300L, 600L to 600L).withReaderWordSyncFlowTiming()

		flow.forEach { assertTrue(it.flowEndMs > it.flowStartMs) }
	}

	@Test
	fun clockProgressFollowsPlaybackAndHoldsWhenPaused() {
		val boundary = boundaries(1_000L to 1_400L).single()
		val timeline = ReaderWordSyncTimelineSnapshot(
			sessionGeneration = 0L,
			timelineRevision = 0L,
			audioResourceId = "track-1",
			audioTrackIndex = 0,
			positionMs = 1_200L,
			playbackSpeed = 1f,
			isPlaying = false
		)
		val paused = boundary.flowClock(timeline, TimeSource.Monotonic.markNow())

		assertEquals(0.5f, paused.progress())
		assertFalse(paused.animating())

		val otherTrack = boundary.flowClock(timeline.copy(audioTrackIndex = 1, isPlaying = true))
		assertEquals(1f, otherTrack.progress())
		assertFalse(otherTrack.animating())

		val playing = boundary.flowClock(timeline.copy(isPlaying = true))
		assertTrue(playing.progress() >= 0.5f)
		assertTrue(playing.animating())
	}

	private fun boundaries(vararg spans: Pair<Long, Long>): List<ReaderWordSyncBoundary> =
		spans.mapIndexed { index, (start, end) ->
			ReaderWordSyncBoundary(
				sequence = index.toLong(),
				wordOrdinalWithinTrack = index,
				word = WordSyncWord(
					audioResourceId = "track-1",
					audioTrackIndex = 0,
					audioHref = "audio.m4b",
					audioStartMs = start,
					audioEndMs = end,
					ebookHref = "chapter.xhtml",
					spineIndex = 0,
					ebookStart = index * 10,
					ebookEnd = index * 10 + 5,
					cueId = 1,
					status = 1,
					confidence = 100,
					method = 0,
					flags = 0
				)
			)
		}
}
