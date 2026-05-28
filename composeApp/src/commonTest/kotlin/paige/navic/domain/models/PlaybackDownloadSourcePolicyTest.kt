package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackDownloadSourcePolicyTest {
	@Test
	fun doesNotReplaceTheCurrentMediaItemWhenDownloadAvailabilityChanges() {
		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = true,
				hasDownloadedFile = true,
				isCurrentlyLocal = false
			)
		)

		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = true,
				hasDownloadedFile = false,
				isCurrentlyLocal = true
			)
		)
	}

	@Test
	fun replacesNonCurrentMediaItemsWhenDownloadAvailabilityChanges() {
		assertTrue(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = true,
				isCurrentlyLocal = false
			)
		)

		assertTrue(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = false,
				isCurrentlyLocal = true
			)
		)
	}

	@Test
	fun keepsNonCurrentMediaItemsWhenSourceAlreadyMatchesDownloadAvailability() {
		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = true,
				isCurrentlyLocal = true
			)
		)

		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = false,
				isCurrentlyLocal = false
			)
		)
	}
}
