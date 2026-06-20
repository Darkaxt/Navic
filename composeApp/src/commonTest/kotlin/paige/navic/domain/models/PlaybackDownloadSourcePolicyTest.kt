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
				isCurrentlyLocal = false,
				isRecoveringFromSourceError = false
			)
		)

		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = true,
				hasDownloadedFile = false,
				isCurrentlyLocal = true,
				isRecoveringFromSourceError = false
			)
		)
	}

	@Test
	fun replacesTheCurrentMediaItemWhenRecoveringFromSourceError() {
		assertTrue(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = true,
				hasDownloadedFile = true,
				isCurrentlyLocal = false,
				isRecoveringFromSourceError = true
			)
		)

		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = true,
				hasDownloadedFile = true,
				isCurrentlyLocal = true,
				isRecoveringFromSourceError = true
			)
		)
	}

	@Test
	fun replacesNonCurrentMediaItemsWhenDownloadAvailabilityChanges() {
		assertTrue(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = true,
				isCurrentlyLocal = false,
				isRecoveringFromSourceError = false
			)
		)

		assertTrue(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = false,
				isCurrentlyLocal = true,
				isRecoveringFromSourceError = false
			)
		)
	}

	@Test
	fun keepsNonCurrentMediaItemsWhenSourceAlreadyMatchesDownloadAvailability() {
		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = true,
				isCurrentlyLocal = true,
				isRecoveringFromSourceError = false
			)
		)

		assertFalse(
			shouldReplaceQueuedMediaItemForDownloadAvailability(
				isCurrentItem = false,
				hasDownloadedFile = false,
				isCurrentlyLocal = false,
				isRecoveringFromSourceError = false
			)
		)
	}
}
