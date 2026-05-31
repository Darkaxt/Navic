package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingUpNextPolicyTest {
	@Test
	fun returnsUpcomingItemsAfterCurrentIndex() {
		val queue = listOf("current", "next", "after next", "later")

		assertEquals(
			listOf("next", "after next"),
			nowPlayingUpNextItems(
				queue = queue,
				currentIndex = 0,
				maxCount = 2
			)
		)
	}

	@Test
	fun usesPlayerProvidedUpcomingIndexesForShuffleOrRepeatOrder() {
		val queue = listOf("one", "two", "three", "four")

		assertEquals(
			listOf("four", "one"),
			nowPlayingUpNextItems(
				queue = queue,
				currentIndex = 1,
				maxCount = 2,
				upcomingIndexes = listOf(3, 0, 2)
			)
		)
	}

	@Test
	fun repeatAllWrapsFromQueueEndWhenNoPlayerOrderIsAvailable() {
		val queue = listOf("one", "two", "three")

		assertEquals(
			listOf("one", "two"),
			nowPlayingUpNextItems(
				queue = queue,
				currentIndex = 2,
				maxCount = 3,
				repeatMode = NowPlayingRepeatAll
			)
		)
	}

	@Test
	fun repeatOneShowsTheCurrentItemAsNext() {
		val queue = listOf("one", "two", "three")

		assertEquals(
			listOf("two"),
			nowPlayingUpNextItems(
				queue = queue,
				currentIndex = 1,
				maxCount = 3,
				repeatMode = NowPlayingRepeatOne
			)
		)
	}

	@Test
	fun returnsEmptyWhenCurrentIndexIsInvalidOrAtQueueEnd() {
		val queue = listOf("one", "two")

		assertTrue(nowPlayingUpNextItems(queue, currentIndex = -1, maxCount = 2).isEmpty())
		assertTrue(nowPlayingUpNextItems(queue, currentIndex = 2, maxCount = 2).isEmpty())
		assertTrue(nowPlayingUpNextItems(queue, currentIndex = 1, maxCount = 2).isEmpty())
	}

	@Test
	fun returnsEmptyWhenMaxCountIsNotPositive() {
		val queue = listOf("current", "next")

		assertTrue(nowPlayingUpNextItems(queue, currentIndex = 0, maxCount = 0).isEmpty())
		assertTrue(nowPlayingUpNextItems(queue, currentIndex = 0, maxCount = -1).isEmpty())
	}

	@Test
	fun artworkOnlyShowsWhenUpNextPreviewAndArtworkAreEnabled() {
		assertTrue(
			shouldShowNowPlayingUpNextArtwork(
				showNowPlayingUpNext = true,
				showNowPlayingUpNextArtwork = true
			)
		)
		assertFalse(
			shouldShowNowPlayingUpNextArtwork(
				showNowPlayingUpNext = false,
				showNowPlayingUpNextArtwork = true
			)
		)
		assertFalse(
			shouldShowNowPlayingUpNextArtwork(
				showNowPlayingUpNext = true,
				showNowPlayingUpNextArtwork = false
			)
		)
	}
}
