package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWordSyncBoundarySchedulerTest {
	@Test
	fun schedulesOnlyTheNextStrictlyFutureWordBoundary() {
		val harness = SchedulerHarness(positionMs = 1_050L)

		harness.scheduler.replaceTimeline(boundaries(1_000L, 1_100L, 1_200L, 1_300L))

		assertEquals(listOf(50L), harness.activeDelays())
		assertTrue(harness.dispatches.isEmpty())
	}

	@Test
	fun lateWakeCoalescesMissedWordsAndSchedulesTheNextBoundary() {
		val harness = SchedulerHarness(positionMs = 1_050L)
		harness.scheduler.replaceTimeline(boundaries(1_000L, 1_100L, 1_200L, 1_300L))

		harness.snapshot = harness.snapshot.copy(positionMs = 1_250L)
		harness.runNext()

		assertEquals(1, harness.dispatches.size)
		assertEquals(1_200L, harness.dispatches.single().boundary.audioStartMs)
		assertEquals(1, harness.dispatches.single().coalescedCount)
		assertEquals(listOf(50L), harness.activeDelays())
	}

	@Test
	fun playbackSpeedChangeCancelsAndRebuildsTheSingleWakeup() {
		val harness = SchedulerHarness(positionMs = 1_050L)
		harness.scheduler.replaceTimeline(boundaries(1_000L, 1_100L, 1_200L))
		val stale = harness.scheduled.single()

		harness.snapshot = harness.snapshot.copy(playbackSpeed = 2f)
		harness.scheduler.refreshTimeline()

		assertTrue(stale.cancelled)
		assertEquals(listOf(25L), harness.activeDelays())
		stale.action()
		assertTrue(harness.dispatches.isEmpty())
		assertEquals(1, harness.activeDelays().size)
	}

	@Test
	fun pauseAndSessionReplacementRejectStaleWakeups() {
		val harness = SchedulerHarness(positionMs = 1_050L)
		harness.scheduler.replaceTimeline(boundaries(1_000L, 1_100L, 1_200L))
		val beforePause = harness.scheduled.single()

		harness.snapshot = harness.snapshot.copy(isPlaying = false)
		harness.scheduler.refreshTimeline()

		assertTrue(beforePause.cancelled)
		assertTrue(harness.activeDelays().isEmpty())
		beforePause.action()
		assertTrue(harness.dispatches.isEmpty())

		harness.snapshot = harness.snapshot.copy(
			sessionGeneration = 2L,
			isPlaying = true
		)
		harness.scheduler.refreshTimeline()
		val beforeReplacement = harness.scheduled.last()
		harness.snapshot = harness.snapshot.copy(sessionGeneration = 3L)
		harness.scheduler.refreshTimeline()

		assertTrue(beforeReplacement.cancelled)
		beforeReplacement.action()
		assertTrue(harness.dispatches.isEmpty())
		assertEquals(1, harness.activeDelays().size)
	}

	@Test
	fun unavailableFreshTimelineCannotPublishOrRetainAWakeup() {
		var snapshot: ReaderWordSyncTimelineSnapshot? = null
		val scheduled = mutableListOf<ScheduledWakeup>()
		val dispatches = mutableListOf<ReaderWordSyncBoundaryDispatch>()
		val scheduler = ReaderWordSyncBoundaryScheduler(
			currentTimeline = { snapshot },
			schedule = { delayMs, action ->
				ScheduledWakeup(delayMs, action).also(scheduled::add)
			},
			onBoundary = dispatches::add
		)

		scheduler.replaceTimeline(boundaries(1_000L, 1_100L))
		assertTrue(scheduled.isEmpty())

		snapshot = ReaderWordSyncTimelineSnapshot(
			sessionGeneration = 1L,
			audioResourceId = "track-1",
			audioTrackIndex = 0,
			positionMs = 1_050L,
			playbackSpeed = 1f,
			isPlaying = true
		)
		scheduler.refreshTimeline()
		val pending = scheduled.single()
		snapshot = null
		pending.action()

		assertTrue(dispatches.isEmpty())
		assertTrue(scheduled.none { !it.cancelled && it !== pending })
	}

	@Test
	fun trackBoundariesExcludeWordsWithoutPresentableExactEndpoints() {
		val track = WordSyncTrack(
			audioResourceId = "track-1",
			audioTrackIndex = 0,
			audioHref = "audio.m4b",
			baseStartMs = 1_000L,
			words = listOf(
				word(startMs = 1_000L, ordinal = 0, status = 1),
				word(startMs = 1_100L, ordinal = 1, status = 0),
				word(startMs = 1_200L, ordinal = 2, status = 4),
				word(startMs = 1_300L, ordinal = 3, status = 5)
			)
		)

		val boundaries = track.readerWordSyncBoundaries()

		assertEquals(listOf(0, 2), boundaries.map { it.wordOrdinalWithinTrack })
		assertEquals(listOf(1_000L, 1_200L), boundaries.map { it.audioStartMs })
	}

	private class SchedulerHarness(positionMs: Long) {
		var snapshot = ReaderWordSyncTimelineSnapshot(
			sessionGeneration = 1L,
			audioResourceId = "track-1",
			audioTrackIndex = 0,
			positionMs = positionMs,
			playbackSpeed = 1f,
			isPlaying = true
		)
		val scheduled = mutableListOf<ScheduledWakeup>()
		val dispatches = mutableListOf<ReaderWordSyncBoundaryDispatch>()
		val scheduler = ReaderWordSyncBoundaryScheduler(
			currentTimeline = { snapshot },
			schedule = { delayMs, action ->
				ScheduledWakeup(delayMs, action).also(scheduled::add)
			},
			onBoundary = dispatches::add
		)

		fun activeDelays(): List<Long> = scheduled.filterNot { it.cancelled || it.ran }.map { it.delayMs }

		fun runNext() {
			val wakeup = scheduled.first { !it.cancelled && !it.ran }
			wakeup.ran = true
			wakeup.action()
		}
	}

	private class ScheduledWakeup(
		val delayMs: Long,
		val action: () -> Unit
	) : ReaderWordSyncBoundaryCancellation {
		var cancelled = false
		var ran = false

		override fun cancel() {
			cancelled = true
		}
	}

	private companion object {
		fun boundaries(vararg starts: Long): List<ReaderWordSyncBoundary> =
			starts.mapIndexed { index, start ->
				ReaderWordSyncBoundary(
					sequence = index.toLong(),
					wordOrdinalWithinTrack = index,
					word = word(startMs = start, ordinal = index, status = 1)
				)
			}

		fun word(startMs: Long, ordinal: Int, status: Int): WordSyncWord = WordSyncWord(
			audioResourceId = "track-1",
			audioTrackIndex = 0,
			audioHref = "audio.m4b",
			audioStartMs = startMs,
			audioEndMs = startMs + 80L,
			ebookHref = "chapter.xhtml",
			spineIndex = 0,
			ebookStart = ordinal * 10,
			ebookEnd = ordinal * 10 + 5,
			cueId = 1,
			status = status,
			confidence = 100,
			method = 0,
			flags = 0
		)
	}
}
