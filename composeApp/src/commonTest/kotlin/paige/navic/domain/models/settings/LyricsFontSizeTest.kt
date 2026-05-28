package paige.navic.domain.models.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsFontSizeTest {
	@Test
	fun mediumPreservesCurrentLyricsSize() {
		assertEquals(32, LyricsFontSize.Medium.sizeSp)
	}

	@Test
	fun fontSizeOptionsIncreaseInReadableSteps() {
		assertEquals(listOf(26, 32, 38, 44), LyricsFontSize.entries.map { it.sizeSp })
	}
}
