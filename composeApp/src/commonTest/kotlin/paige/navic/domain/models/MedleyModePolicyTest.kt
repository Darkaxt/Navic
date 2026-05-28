package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MedleyModePolicyTest {
	@Test
	fun durationIsClampedToNonNegativeMilliseconds() {
		assertEquals(0L, medleyModeDurationMs(-10))
		assertEquals(0L, medleyModeDurationMs(0))
		assertEquals(30_000L, medleyModeDurationMs(30))
	}

	@Test
	fun advancesOnlyAfterThresholdWhilePlayingWithANextItem() {
		assertTrue(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 30,
				isPlaying = true,
				hasNextMediaItem = true,
				currentPositionMs = 30_000L,
				alreadyAdvancedCurrentItem = false
			)
		)
		assertTrue(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 30,
				isPlaying = true,
				hasNextMediaItem = true,
				currentPositionMs = 35_000L,
				alreadyAdvancedCurrentItem = false
			)
		)
		assertFalse(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 0,
				isPlaying = true,
				hasNextMediaItem = true,
				currentPositionMs = 30_000L,
				alreadyAdvancedCurrentItem = false
			)
		)
		assertFalse(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 30,
				isPlaying = false,
				hasNextMediaItem = true,
				currentPositionMs = 30_000L,
				alreadyAdvancedCurrentItem = false
			)
		)
		assertFalse(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 30,
				isPlaying = true,
				hasNextMediaItem = false,
				currentPositionMs = 30_000L,
				alreadyAdvancedCurrentItem = false
			)
		)
		assertFalse(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 30,
				isPlaying = true,
				hasNextMediaItem = true,
				currentPositionMs = 29_999L,
				alreadyAdvancedCurrentItem = false
			)
		)
		assertFalse(
			shouldAdvanceMedleyMode(
				medleyModeSeconds = 30,
				isPlaying = true,
				hasNextMediaItem = true,
				currentPositionMs = 35_000L,
				alreadyAdvancedCurrentItem = true
			)
		)
	}
}
