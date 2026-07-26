package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ReaderPageTestBackground = 0xF5F0E8
private const val ReaderPageTestForeground = 0x34312D
private const val ReaderPageTestLowContrast = 0xE1DCD4

class ReaderPageTurnBitmapSourceTest {
	@Test
	fun successfulWindowCapturePublishesAnExplicitlyOpaqueBitmap() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val capture = source
			.substringAfter("private fun captureVisualState(")
			.substringBefore("internal fun parseGeometry(")
		val accepted = capture
			.substringAfter("foreground?.renderable == true")
			.substringBefore("onCaptured(ReaderPageTurnCaptureResult")

		assertTrue(capture.contains("bitmap.eraseColor(readerPageTurnOpaqueColor("))
		assertTrue(
			capture.contains(
				"readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb)"
			)
		)
		assertTrue(source.contains("bitmap.eraseColor(backgroundColorArgb)"))
		assertTrue(accepted.contains("bitmap.setHasAlpha(false)"))
		assertTrue(accepted.contains("bitmap.setPremultiplied(true)"))
	}

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
