package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderOverlaySyncReducerTest {
	private val firstCue = ReaderOverlayCue(
		key = "cue-1",
		fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter.mp3",
			fragmentId = "cue-1",
			textHref = "text/chapter.xhtml"
		)
	)

	@Test
	fun staticPlaybackAppliesOnceThenClearsOnce() {
		val applied = ReaderOverlaySyncState().followPlaybackCue(firstCue)
		assertIs<ReaderEngineCommand.ApplyMediaOverlay>(applied.engineCommand)
		assertEquals(1L, applied.engineCommandKey)

		val repeated = applied.followPlaybackCue(firstCue)
		assertEquals(applied.engineCommand, repeated.engineCommand)
		assertEquals(1L, repeated.engineCommandKey)

		val cleared = repeated.followPlaybackCue(null)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, cleared.engineCommand)
		assertEquals(2L, cleared.engineCommandKey)
		assertNull(cleared.activeCueKey)

		val repeatedClear = cleared.followPlaybackCue(null)
		assertEquals(cleared.engineCommand, repeatedClear.engineCommand)
		assertEquals(2L, repeatedClear.engineCommandKey)
	}

	@Test
	fun unconfirmedCueSuppressesProgressUpdates() {
		val start = ReaderOverlaySyncState().followPlaybackCue(
			firstCue.copy(
				fragment = firstCue.fragment.copy(textProgressEnd = 10),
				progressTextEnd = 10
			)
		)

		val suppressed = start.followPlaybackCue(
			firstCue.copy(
				fragment = firstCue.fragment.copy(textProgressEnd = 15),
				progressTextEnd = 15
			)
		)

		assertEquals(start.engineCommand, suppressed.engineCommand)
		assertEquals(start.engineCommandKey, suppressed.engineCommandKey)
		assertEquals(10, suppressed.activeProgressTextEnd)
	}

	@Test
	fun progressiveCueUpdatesOnlyWhenProgressMarkerChanges() {
		val start = ReaderOverlaySyncState().followPlaybackCue(
			firstCue.copy(
				fragment = firstCue.fragment.copy(textProgressEnd = 10),
				progressTextEnd = 10
			)
		)
		val confirmed = start.confirmOverlay(start.activeOverlayRequestId)
		val progressedCue = firstCue.copy(
			fragment = firstCue.fragment.copy(textProgressEnd = 15),
			progressTextEnd = 15
		)
		val progressed = confirmed.followPlaybackCue(progressedCue)

		val command = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(progressed.engineCommand)
		assertEquals(15, command.fragment.textProgressEnd)
		assertEquals(2L, progressed.engineCommandKey)

		val repeated = progressed.followPlaybackCue(progressedCue)
		assertEquals(progressed.engineCommand, repeated.engineCommand)
		assertEquals(2L, repeated.engineCommandKey)
	}

	@Test
	fun readerTargetSuppressesRepeatUnlessAdapterAllowsRepeatSeek() {
		val target = ReaderOverlayReaderTarget(
			cue = firstCue,
			seekTarget = "seek-1"
		)
		val first = ReaderOverlaySyncState().followReaderTarget(target)
		assertEquals("seek-1", first.seekTarget)
		assertEquals(1L, first.state.engineCommandKey)

		val repeated = first.state.followReaderTarget(target)
		assertNull(repeated.seekTarget)
		assertEquals(1L, repeated.state.engineCommandKey)

		val repeatable = first.state.followReaderTarget(target.copy(repeatSeek = true))
		assertEquals("seek-1", repeatable.seekTarget)
		assertEquals(1L, repeatable.state.engineCommandKey)
	}

	@Test
	fun repeatedReaderTargetCanPreservePlaybackProgressWithoutSeeking() {
		val activeCue = firstCue.copy(
			fragment = firstCue.fragment.copy(textProgressEnd = 20),
			progressTextEnd = 20
		)
		val active = ReaderOverlaySyncState().followPlaybackCue(activeCue)
		val visibleRangeTarget = ReaderOverlayReaderTarget(
			cue = firstCue.copy(
				fragment = firstCue.fragment.copy(textProgressEnd = 10),
				progressTextEnd = 10
			),
			seekTarget = "visible-range",
			updateRepeatedCue = false
		)

		val repeated = active.followReaderTarget(visibleRangeTarget)

		assertNull(repeated.seekTarget)
		assertEquals(20, repeated.state.activeProgressTextEnd)
		assertEquals(active.engineCommandKey, repeated.state.engineCommandKey)
	}

	@Test
	fun disablingActiveSyncClearsExactlyOnceAndSuppressesPlayback() {
		val active = ReaderOverlaySyncState().followPlaybackCue(firstCue)
		val disabled = active.setSyncEnabled(false)

		assertEquals(ReaderEngineCommand.ClearMediaOverlay, disabled.engineCommand)
		assertEquals(2L, disabled.engineCommandKey)
		assertNull(disabled.activeCueKey)

		val repeatedDisable = disabled.setSyncEnabled(false)
		assertEquals(2L, repeatedDisable.engineCommandKey)
		val ignoredPlayback = repeatedDisable.followPlaybackCue(firstCue)
		assertEquals(2L, ignoredPlayback.engineCommandKey)
		assertNull(ignoredPlayback.activeCueKey)
	}
}
