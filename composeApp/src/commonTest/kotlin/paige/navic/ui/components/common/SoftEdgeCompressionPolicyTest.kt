package paige.navic.ui.components.common

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftEdgeCompressionPolicyTest {
	@Test
	fun horizontalCompressionUsesMultipleBandsWithoutTouchingTheFittedCenter() {
		val bands = softEdgeCompressionBands(
			axis = SoftEdgeCompressionAxis.Horizontal,
			canvasPrimarySize = 1000,
			canvasSecondarySize = 1000,
			imagePrimarySize = 1600,
			imageSecondarySize = 900,
			leadingGapSize = 80,
			trailingGapSize = 80
		)

		assertTrue(bands.size > 2)
		assertEquals(IntOffset(0, 0), bands.first().dstOffset)
		assertEquals(IntOffset(920, 0), bands[bands.size / 2].dstOffset)
		assertTrue(bands.all { it.dstOffset.x < 80 || it.dstOffset.x >= 920 })
		assertTrue(bands.all { it.dstSize.height == 1000 })
	}

	@Test
	fun verticalCompressionUsesMultipleBandsWithoutTouchingTheFittedCenter() {
		val bands = softEdgeCompressionBands(
			axis = SoftEdgeCompressionAxis.Vertical,
			canvasPrimarySize = 1000,
			canvasSecondarySize = 1000,
			imagePrimarySize = 1600,
			imageSecondarySize = 900,
			leadingGapSize = 90,
			trailingGapSize = 70
		)

		assertTrue(bands.size > 2)
		assertEquals(IntOffset(0, 0), bands.first().dstOffset)
		assertEquals(IntOffset(0, 930), bands[bands.count { it.dstOffset.y < 90 }].dstOffset)
		assertTrue(bands.all { it.dstOffset.y < 90 || it.dstOffset.y >= 930 })
		assertTrue(bands.all { it.dstSize.width == 1000 })
	}

	@Test
	fun compressionSamplesOnlyTheOuterSourceArea() {
		val bands = softEdgeCompressionBands(
			axis = SoftEdgeCompressionAxis.Horizontal,
			canvasPrimarySize = 1000,
			canvasSecondarySize = 1000,
			imagePrimarySize = 1600,
			imageSecondarySize = 900,
			leadingGapSize = 100,
			trailingGapSize = 100
		)

		val leadingBands = bands.filter { it.dstOffset.x < 100 }
		val trailingBands = bands.filter { it.dstOffset.x >= 900 }
		assertTrue(leadingBands.all { it.srcOffset.x < 352 })
		assertTrue(trailingBands.all { it.srcOffset.x >= 1248 })
		assertTrue(bands.all { it.srcSize.width >= 1 })
	}
}
