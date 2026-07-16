package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NowPlayingPagerIntentTest {
	@Test
	fun programmaticSettlementDoesNotSelectQueueItem() {
		val tracker = NowPlayingPagerIntentTracker()

		assertNull(
			tracker.onSettledPage(
				settledPage = 4,
				currentIndex = 2,
				queueSize = 8,
				isPaused = false
			)
		)
	}

	@Test
	fun completedUserDragSelectsExactlyOnceWithLatestPlaybackIntent() {
		val tracker = NowPlayingPagerIntentTracker()
		tracker.onUserDragStarted()

		assertEquals(
			QueueSelectionRequest(
				index = 4,
				playWhenReady = false,
				origin = QueueSelectionOrigin.NowPlayingArtworkSwipe
			),
			tracker.onSettledPage(
				settledPage = 4,
				currentIndex = 2,
				queueSize = 8,
				isPaused = true
			)
		)
		assertNull(
			tracker.onSettledPage(
				settledPage = 4,
				currentIndex = 2,
				queueSize = 8,
				isPaused = true
			)
		)
	}

	@Test
	fun userDragToCurrentOrInvalidPageIsConsumedWithoutSelection() {
		val tracker = NowPlayingPagerIntentTracker()

		tracker.onUserDragStarted()
		assertNull(
			tracker.onSettledPage(
				settledPage = 2,
				currentIndex = 2,
				queueSize = 8,
				isPaused = false
			)
		)

		tracker.onUserDragStarted()
		assertNull(
			tracker.onSettledPage(
				settledPage = 8,
				currentIndex = 2,
				queueSize = 8,
				isPaused = false
			)
		)
	}
}
