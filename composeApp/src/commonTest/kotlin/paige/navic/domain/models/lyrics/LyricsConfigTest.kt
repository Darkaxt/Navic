package paige.navic.domain.models.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsConfigTest {
	@Test
	fun defaultLrcLibEndpointUsesSearch() {
		assertEquals("https://lrclib.net/api/search", LyricsConfig().lrcLibBaseUrl)
	}

	@Test
	fun defaultPriorityUsesServerLyricsBeforeExternalProviders() {
		assertEquals(
			listOf(
				LyricsProvider.SUBSONIC,
				LyricsProvider.LYRICS_PLUS,
				LyricsProvider.LRCLIB
			),
			LyricsConfig().priority
		)
	}

	@Test
	fun legacyDefaultPriorityMigratesToServerFirst() {
		val config = normalizedLyricsConfig(
			LyricsConfig(
				priority = listOf(
					LyricsProvider.LYRICS_PLUS,
					LyricsProvider.SUBSONIC,
					LyricsProvider.LRCLIB
				)
			)
		)

		assertEquals(
			listOf(
				LyricsProvider.SUBSONIC,
				LyricsProvider.LYRICS_PLUS,
				LyricsProvider.LRCLIB
			),
			config.priority
		)
	}

	@Test
	fun customPriorityIsPreserved() {
		val priority = listOf(
			LyricsProvider.LRCLIB,
			LyricsProvider.SUBSONIC,
			LyricsProvider.LYRICS_PLUS
		)

		assertEquals(
			priority,
			normalizedLyricsConfig(LyricsConfig(priority = priority)).priority
		)
	}
}
