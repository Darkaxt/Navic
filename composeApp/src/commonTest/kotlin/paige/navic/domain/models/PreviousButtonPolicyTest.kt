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
	fun disabledSmartRewindAlwaysGoesToPreviousSongWhenAvailable() {
		assertFalse(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = -1,
				hasPreviousMediaItem = true,
				currentPositionMs = 90_000L
			)
		)
	}

	@Test
	fun disabledSmartRewindStillRestartsWhenThereIsNoPreviousItem() {
		assertTrue(
			shouldRestartCurrentOnPrevious(
				smartRewindSeconds = -1,
				hasPreviousMediaItem = false,
				currentPositionMs = 0L
			)
		)
	}
}
