package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ReaderPageTestBackground = 0xF5F0E8
private const val ReaderPageTestForeground = 0x34312D
private const val ReaderPageTestLowContrast = 0xE1DCD4

class ReaderPageTurnBitmapSourceTest {
	@Test
	fun uniformSurfaceIsRejected() {
		assertFalse(
			readerPageTurnPixelsContainForeground(
				IntArray(1_120) { ReaderPageTestBackground }
			)
		)
	}

	@Test
	fun sparseRenderedForegroundIsAccepted() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestForeground
		pixels[106] = ReaderPageTestForeground
		pixels[107] = ReaderPageTestForeground

		assertTrue(readerPageTurnPixelsContainForeground(pixels))
	}

	@Test
	fun fewerThanThreeForegroundSamplesAreRejected() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestForeground
		pixels[106] = ReaderPageTestForeground

		assertFalse(readerPageTurnPixelsContainForeground(pixels))
	}

	@Test
	fun lowContrastSurfaceIsRejected() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestLowContrast
		pixels[106] = ReaderPageTestLowContrast
		pixels[107] = ReaderPageTestLowContrast

		assertFalse(readerPageTurnPixelsContainForeground(pixels))
	}
}
