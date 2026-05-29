package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.domain.models.lyrics.LyricsProvider

class LyricsRepositoryPolicyTest {
	@Test
	fun cachedLyricsAreUsedImmediatelyOnlyWhenTheyMatchTheFirstProvider() {
		assertTrue(
			shouldUseCachedLyricsBeforeFetch(
				cachedProvider = LyricsProvider.SUBSONIC,
				priority = listOf(
					LyricsProvider.SUBSONIC,
					LyricsProvider.LYRICS_PLUS,
					LyricsProvider.LRCLIB
				)
			)
		)
		assertFalse(
			shouldUseCachedLyricsBeforeFetch(
				cachedProvider = LyricsProvider.LYRICS_PLUS,
				priority = listOf(
					LyricsProvider.SUBSONIC,
					LyricsProvider.LYRICS_PLUS,
					LyricsProvider.LRCLIB
				)
			)
		)
	}
}
