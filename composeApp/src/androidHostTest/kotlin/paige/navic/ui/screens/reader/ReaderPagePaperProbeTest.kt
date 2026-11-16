package paige.navic.ui.screens.reader

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPagePaperProbeTest {
	private val foreground = 0xFF1C1B1A.toInt()
	private val background = 0xFFF4EFE6.toInt()

	@Test
	fun multipliedTextRoundTripsThroughCoverageAndPaper() {
		val size = 64 * 64
		val paper = IntArray(size) { index ->
			val grain = 200 + (index * 37 % 50)
			(0xFF shl 24) or (grain shl 16) or ((grain - 6) shl 8) or (grain - 18)
		}
		val coverage = IntArray(size) { index -> if (index % 7 == 0) 255 else index * 13 % 256 }
		val tint = readerPaperSplitTint(foreground, background)
		val composite = IntArray(size) { readerPaperSplitRecomposePixel(paper[it], coverage[it], tint) }

		val split = readerPaperSplitAnalyze(composite, paper, foreground, background)

		for (index in 0 until size) {
			val recovered = split.alpha[index].toInt() and 0xFF
			assertTrue(abs(recovered - coverage[index]) <= 2, "coverage $index: $recovered vs ${coverage[index]}")
		}
		assertTrue(split.stats.maxError <= 2, "max error ${split.stats.maxError}")
		assertEquals(0, split.stats.over48Count)
	}

	@Test
	fun colouredPixelsThatTextCannotExplainAreReported() {
		val paper = IntArray(4) { 0xFFE0E0E0.toInt() }
		val composite = intArrayOf(0xFFE0E0E0.toInt(), 0xFFE0E0E0.toInt(), 0xFF2050E0.toInt(), 0xFFE0E0E0.toInt())

		val stats = readerPaperSplitAnalyze(composite, paper, foreground, background).stats

		assertTrue(stats.over48Count >= 1)
		assertTrue(stats.maxError > 48)
	}

	@Test
	fun probeColoursParseFromJavascriptResult() {
		val encoded = "\"{\\\"hidden\\\":2,\\\"foreground\\\":\\\"rgb(28, 27, 26)\\\",\\\"background\\\":\\\"rgb(244, 239, 230)\\\"}\""
		val colors = assertNotNull(readerPaperProbeColors(encoded))
		assertEquals(foreground, colors.first)
		assertEquals(background, colors.second)
		assertNull(readerPaperProbeColors("null"))
		assertNull(readerPaperProbeColors("\"{\\\"hidden\\\":0}\""))
	}
}
