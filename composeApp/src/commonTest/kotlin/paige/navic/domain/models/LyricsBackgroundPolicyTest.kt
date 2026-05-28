package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsBackgroundPolicyTest {
	@Test
	fun accentBackgroundKeepsCurrentTransparentDefault() {
		assertEquals(0f, lyricsAccentBackgroundAlpha(enabled = false))
	}

	@Test
	fun accentBackgroundUsesSubtleTintWhenEnabled() {
		assertEquals(DefaultLyricsAccentBackgroundAlpha, lyricsAccentBackgroundAlpha(enabled = true))
	}
}
