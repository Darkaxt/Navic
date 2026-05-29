package paige.navic.util.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricUtilsTest {
	@Test
	fun translatedSourceLinesProgressInParallelWhileWrappedLinesStaySequential() {
		val text = "original source line wraps here\ntranslated source line wraps here"
		val translationStart = text.indexOf('\n') + 1
		val scopes = karaokeLineProgressScopes(
			text = text,
			lineStartOffsets = listOf(0, 14, translationStart, translationStart + 17),
			lineWidths = listOf(100f, 100f, 120f, 120f)
		)

		assertEquals(
			listOf(
				KaraokeLineProgressScope(totalWidth = 200f, accumulatedWidth = 0f),
				KaraokeLineProgressScope(totalWidth = 200f, accumulatedWidth = 100f),
				KaraokeLineProgressScope(totalWidth = 240f, accumulatedWidth = 0f),
				KaraokeLineProgressScope(totalWidth = 240f, accumulatedWidth = 120f)
			),
			scopes
		)
		assertEquals(
			100f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				totalWidth = 200f,
				accumulatedWidth = 0f,
				feather = 0f
			)
		)
		assertEquals(
			0f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				totalWidth = 200f,
				accumulatedWidth = 100f,
				feather = 0f
			)
		)
		assertEquals(
			120f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				totalWidth = 240f,
				accumulatedWidth = 0f,
				feather = 0f
			)
		)
		assertEquals(
			0f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				totalWidth = 240f,
				accumulatedWidth = 120f,
				feather = 0f
			)
		)
	}

	@Test
	fun singleLineKaraokeProgressKeepsSequentialWrappedLineBehavior() {
		assertEquals(
			100f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				totalWidth = 200f,
				accumulatedWidth = 0f,
				feather = 0f
			)
		)
		assertEquals(
			0f,
			karaokeLinePixelTarget(
				progress = 0.5f,
				totalWidth = 200f,
				accumulatedWidth = 100f,
				feather = 0f
			)
		)
	}
}
