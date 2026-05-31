package paige.navic.domain.manager

import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.PlaybackOriginType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackOriginTrackerTest {
	@Test
	fun pausedTimeDoesNotAccrue() {
		val tracker = PlaybackOriginTracker()

		assertNull(tracker.setOrigin(artist, nowMillis = 0L))
		assertNull(tracker.onPlaybackState(isPlaying = false, nowMillis = 1_000L))
		assertNull(tracker.flush(nowMillis = 5_000L))
	}

	@Test
	fun playingTimeAccruesForCurrentOrigin() {
		val tracker = PlaybackOriginTracker()

		tracker.setOrigin(artist, nowMillis = 0L)
		tracker.onPlaybackState(isPlaying = true, nowMillis = 1_000L)
		val credit = tracker.flush(nowMillis = 4_500L)

		assertEquals(artist, credit?.origin)
		assertEquals(3_500L, credit?.durationMillis)
	}

	@Test
	fun changingOriginFlushesPreviousOriginFirst() {
		val tracker = PlaybackOriginTracker()

		tracker.setOrigin(artist, nowMillis = 0L)
		tracker.onPlaybackState(isPlaying = true, nowMillis = 1_000L)
		val oldCredit = tracker.setOrigin(genre, nowMillis = 3_000L)
		val newCredit = tracker.flush(nowMillis = 7_000L)

		assertEquals(artist, oldCredit?.origin)
		assertEquals(2_000L, oldCredit?.durationMillis)
		assertEquals(genre, newCredit?.origin)
		assertEquals(4_000L, newCredit?.durationMillis)
	}

	@Test
	fun clearingOriginFlushesAndStopsCredit() {
		val tracker = PlaybackOriginTracker()

		tracker.setOrigin(artist, nowMillis = 0L)
		tracker.onPlaybackState(isPlaying = true, nowMillis = 1_000L)
		val credit = tracker.setOrigin(null, nowMillis = 2_500L)
		val laterCredit = tracker.flush(nowMillis = 8_000L)

		assertEquals(artist, credit?.origin)
		assertEquals(1_500L, credit?.durationMillis)
		assertNull(laterCredit)
	}

	@Test
	fun checkpointCreditsAndContinuesPlaying() {
		val tracker = PlaybackOriginTracker()

		tracker.setOrigin(artist, nowMillis = 0L)
		tracker.onPlaybackState(isPlaying = true, nowMillis = 1_000L)
		val checkpoint = tracker.checkpoint(nowMillis = 4_000L)
		val finalCredit = tracker.flush(nowMillis = 6_000L)

		assertEquals(artist, checkpoint?.origin)
		assertEquals(3_000L, checkpoint?.durationMillis)
		assertEquals(artist, finalCredit?.origin)
		assertEquals(2_000L, finalCredit?.durationMillis)
	}

	@Test
	fun zeroDurationCheckpointKeepsAccruing() {
		val tracker = PlaybackOriginTracker()

		tracker.setOrigin(artist, nowMillis = 0L)
		tracker.onPlaybackState(isPlaying = true, nowMillis = 1_000L)
		assertNull(tracker.checkpoint(nowMillis = 1_000L))
		val finalCredit = tracker.flush(nowMillis = 1_500L)

		assertEquals(artist, finalCredit?.origin)
		assertEquals(500L, finalCredit?.durationMillis)
	}

	private val artist = PlaybackOrigin(
		type = PlaybackOriginType.Artist,
		id = "artist",
		title = "Artist"
	)

	private val genre = PlaybackOrigin(
		type = PlaybackOriginType.Genre,
		id = "genre",
		title = "Genre"
	)
}
