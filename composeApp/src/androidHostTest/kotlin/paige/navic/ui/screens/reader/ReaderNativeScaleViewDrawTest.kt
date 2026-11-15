package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #54: the live WebView captured at open was software-drawn into a half-scale canvas, so the
 * opening page's capture differed from the prepared rasters and was rejected by live
 * validation after a back-turn. Views are drawn at 1:1 scale and resampled afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderNativeScaleViewDrawTest {
	private class RecordingView(context: Context) : View(context) {
		val drawScales = mutableListOf<Pair<Float, Float>>()

		override fun draw(canvas: Canvas) {
			val values = FloatArray(9)
			@Suppress("DEPRECATION")
			canvas.matrix.getValues(values)
			drawScales += values[Matrix.MSCALE_X] to values[Matrix.MSCALE_Y]
			canvas.drawColor(Color.BLACK)
			canvas.drawRect(20f, 0f, 40f, 20f, android.graphics.Paint().apply { color = Color.WHITE })
		}
	}

	@Test
	fun halfSizeTargetIsDrawnAtNativeScaleThenResampled() {
		val view = RecordingView(RuntimeEnvironment.getApplication())
		val target = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)

		val drawn = drawViewAtNativeScale(view, Rect(0, 0, 40, 20), target, Color.GRAY)

		assertTrue(drawn)
		assertEquals(listOf(1f to 1f), view.drawScales)
		assertEquals(Color.BLACK, target.getPixel(4, 5))
		assertEquals(Color.WHITE, target.getPixel(15, 5))
	}

	@Test
	fun offsetSourceRegionIsTranslatedNotScaled() {
		val view = RecordingView(RuntimeEnvironment.getApplication())
		val target = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

		assertTrue(drawViewAtNativeScale(view, Rect(20, 0, 40, 20), target, null))

		assertEquals(listOf(1f to 1f), view.drawScales)
		assertEquals(Color.WHITE, target.getPixel(10, 10))
	}

	@Test
	fun readerCapturesNeverDrawViewsIntoScaledCanvases() {
		val readerSources = File("src/androidMain/kotlin/paige/navic/ui/screens/reader")
			.listFiles { file -> file.name.endsWith(".kt") }
			.orEmpty()
		val viewDraws = readerSources.filter { it.readText().contains(".draw(canvas)") }.map { it.name }
		val scaledCanvases = readerSources.filter { Regex("""\bcanvas\.scale\(""").containsMatchIn(it.readText()) }

		assertEquals(listOf("ReaderNativeScaleViewDraw.android.kt"), viewDraws)
		assertEquals(emptyList(), scaledCanvases.map { it.name })
	}
}
