package paige.navic.util.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricUtilsTest {
	@Test
	fun explicitTranslatedLinesUseParallelKaraokeProgress() {
		assertEquals(
			50f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				lineWidth = 100f,
				totalWidth = 200f,
				accumulatedWidth = 0f,
				feather = 0f,
				hasExplicitLineBreaks = true
			)
		)
		assertEquals(
			50f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				lineWidth = 100f,
				totalWidth = 200f,
				accumulatedWidth = 100f,
				feather = 0f,
				hasExplicitLineBreaks = true
			)
		)
	}

	@Test
	fun singleLineKaraokeProgressKeepsSequentialWrappedLineBehavior() {
		assertEquals(
			100f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				lineWidth = 100f,
				totalWidth = 200f,
				accumulatedWidth = 0f,
				feather = 0f,
				hasExplicitLineBreaks = false
			)
		)
		assertEquals(
			0f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				lineWidth = 100f,
				totalWidth = 200f,
				accumulatedWidth = 100f,
				feather = 0f,
				hasExplicitLineBreaks = false
			)
		)
	}
}
