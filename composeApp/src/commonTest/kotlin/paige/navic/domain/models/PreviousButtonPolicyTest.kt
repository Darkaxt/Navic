package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviousButtonPolicyTest {
	@Test
	fun restartsCurrentSongWhenThereIsNoPreviousItem() {
		assertTrue(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = 3,
				hasPreviousMediaItem = false,
				currentPositionMs = 0L
			)
		)
	}

	@Test
	fun restartsCurrentSongAfterConfiguredThreshold() {
		assertTrue(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = 3,
				hasPreviousMediaItem = true,
				currentPositionMs = 3_001L
			)
		)
	}

	@Test
	fun goesToPreviousSongAtOrBeforeConfiguredThreshold() {
		assertFalse(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = 3,
				hasPreviousMediaItem = true,
				currentPositionMs = 3_000L
			)
		)
	}

	@Test
	fun clampsNegativeThresholdToZeroSeconds() {
		assertTrue(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = -5,
				hasPreviousMediaItem = true,
				currentPositionMs = 1L
			)
		)
		assertFalse(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = -5,
				hasPreviousMediaItem = true,
				currentPositionMs = 0L
			)
		)
	}
}
