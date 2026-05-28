package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NowPlayingDurationLabelsTest {
	@Test
	fun finiteTracksCanIncludeRemainingTime() {
		val labels = nowPlayingDurationLabels(
			duration = 200.seconds,
			progress = 0.25f,
			showRemainingTime = true
		)

		assertEquals("00:50", labels.elapsed)
		assertEquals("02:30", labels.remaining)
		assertEquals("03:20", labels.total)
	}

	@Test
	fun currentTwoLabelBehaviorDoesNotIncludeRemainingTimeByDefault() {
		val labels = nowPlayingDurationLabels(
			duration = 200.seconds,
			progress = 0.25f,
			showRemainingTime = false
		)

		assertEquals("00:50", labels.elapsed)
		assertNull(labels.remaining)
		assertEquals("03:20", labels.total)
	}

	@Test
	fun liveAndUnknownDurationsDoNotShowRemainingTime() {
		val live = nowPlayingDurationLabels(
			duration = Duration.ZERO,
			progress = 0.5f,
			showRemainingTime = true
		)
		val unknown = nowPlayingDurationLabels(
			duration = null,
			progress = 0.5f,
			showRemainingTime = true
		)

		assertEquals("LIVE", live.elapsed)
		assertNull(live.remaining)
		assertEquals("∞", live.total)
		assertEquals("--:--", unknown.elapsed)
		assertNull(unknown.remaining)
		assertEquals("--:--", unknown.total)
	}
}
