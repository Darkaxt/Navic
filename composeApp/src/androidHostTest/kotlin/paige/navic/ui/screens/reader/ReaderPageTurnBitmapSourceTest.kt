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
	fun successfulWebViewCapturePublishesAnExplicitlyOpaqueBitmap() {
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

		assertTrue(
			capture.contains(
				"val backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb)"
			)
		)
		assertTrue(capture.contains("bitmap.eraseColor(backgroundColor)"))
		assertTrue(source.contains("bitmap.eraseColor(backgroundColorArgb)"))
		assertTrue(accepted.contains("bitmap.setHasAlpha(false)"))
		assertTrue(accepted.contains("bitmap.setPremultiplied(true)"))
	}

	@Test
	fun currentSurfaceCaptureExcludesWindowOverlays() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val request = source
			.substringAfter("private fun capture(")
			.substringBefore("private fun captureVisualState(")
		val capture = source
			.substringAfter("private fun captureVisualState(")
			.substringBefore("internal fun parseGeometry(")
		val helper = source
			.substringAfter("private fun drawWebViewIntoBitmap(")
			.substringBefore("private data class ReaderPageTurnForegroundAnalysis")
		val draw = "drawWebViewIntoBitmap("
		val analyze = "bitmap.analyzeRenderableForeground()"
		val visualFence = "webView.postVisualStateCallback("
		val nextFrame = "webView.postOnAnimation {"
		val captureVisualState = "captureVisualState(webView, geometry, startedAt, onCaptured, resolveRect)"

		assertFalse(source.contains("PixelCopy"))
		assertTrue(request.indexOf(visualFence) < request.indexOf(nextFrame))
		assertTrue(request.indexOf(nextFrame) < request.indexOf(captureVisualState))
		assertTrue(capture.contains(draw))
		assertTrue(capture.indexOf(draw) < capture.indexOf(analyze))
		assertTrue(capture.contains("previousSparseSignature: Int? = null"))
		assertTrue(capture.contains("foreground?.sparseSignature == previousSparseSignature"))
		assertTrue(capture.contains("previousSparseSignature == null"))
		assertTrue(helper.contains("webView.draw(canvas)"))
		assertTrue(
			helper.contains(
				"webViewLocationInWindow[0] - sourceRectInWindow.left.toFloat()"
			)
		)
		assertTrue(
			helper.contains(
				"webViewLocationInWindow[1] - sourceRectInWindow.top.toFloat()"
			)
		)
	}

	@Test
	fun preparedGeometryCaptureAvoidsASecondRuntimeGeometryQuery() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val preparedCapture = source
			.substringAfter("geometry: ReaderPageTurnCaptureGeometry,")
			.substringBefore("suspend fun captureSurfaceAwait")

		assertTrue(preparedCapture.contains("captureResolvedGeometry(webView, geometry, onCaptured)"))
		assertFalse(preparedCapture.contains("evaluateJavascript"))
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
	fun stableSparseSurfaceIsAcceptedOnlyAfterASecondObservation() {
		val first = IntArray(1_120) { ReaderPageTestBackground }
		first[105] = ReaderPageTestForeground
		val second = first.copyOf()

		assertFalse(readerPageTurnPixelsContainForeground(first))
		assertTrue(readerPageTurnSparseForegroundSettled(first, second))
	}

	@Test
	fun changingSparseSurfaceIsNotAcceptedAsSettled() {
		val first = IntArray(1_120) { ReaderPageTestBackground }
		val second = first.copyOf()
		first[105] = ReaderPageTestForeground
		second[106] = ReaderPageTestForeground

		assertFalse(readerPageTurnSparseForegroundSettled(first, second))
	}

	@Test
	fun stableUniformSurfaceIsNotAcceptedAsSettled() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }

		assertFalse(readerPageTurnSparseForegroundSettled(pixels, pixels.copyOf()))
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
