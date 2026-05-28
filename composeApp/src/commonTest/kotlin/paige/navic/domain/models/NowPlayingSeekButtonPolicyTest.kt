package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NowPlayingSeekButtonPolicyTest {
	@Test
	fun movesProgressByRequestedDuration() {
		assertEquals(
			0.25f,
			nowPlayingSeekProgress(
				currentProgress = 0.5f,
				duration = 2.minutes,
				adjustment = (-30).seconds
			)
		)

		assertEquals(
			0.75f,
			nowPlayingSeekProgress(
				currentProgress = 0.5f,
				duration = 2.minutes,
				adjustment = 30.seconds
			)
		)
	}

	@Test
	fun clampsToTrackBounds() {
		assertEquals(
			0f,
			nowPlayingSeekProgress(
				currentProgress = 0.05f,
				duration = 100.seconds,
				adjustment = (-30).seconds
			)
		)

		assertEquals(
			1f,
			nowPlayingSeekProgress(
				currentProgress = 0.95f,
				duration = 100.seconds,
				adjustment = 30.seconds
			)
		)
	}

	@Test
	fun returnsNullWhenDurationCannotBeSought() {
		assertNull(
			nowPlayingSeekProgress(
				currentProgress = 0.5f,
				duration = null,
				adjustment = 10.seconds
			)
		)
		assertNull(
			nowPlayingSeekProgress(
				currentProgress = 0.5f,
				duration = kotlin.time.Duration.ZERO,
				adjustment = 10.seconds
			)
		)
	}
}
