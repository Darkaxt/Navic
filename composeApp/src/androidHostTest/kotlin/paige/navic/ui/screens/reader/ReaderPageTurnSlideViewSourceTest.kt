package paige.navic.ui.screens.reader

import paige.navic.reader.readerAndroidFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageTurnSlideViewSourceTest {
	@Test
	fun slideRendererUsesOneNormalizedProgressContract() {
		val source = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()

		assertContains(source, "progress.coerceIn(0f, 1f)")
		assertFalse(source.contains("MaxTurnProgress"))
		assertFalse(source.contains("/ 2f"))
	}

	@Test
	fun forwardKeepsDestinationAtRestAndMovesCurrentOut() {
		val source = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()
		val forward = source.substringAfter("private fun drawForward(").substringBefore("private fun drawBackward(")

		assertTrue(
			forward.indexOf("canvas.drawBitmap(destination") < forward.indexOf("canvas.translate(-width * progress"),
			"The destination must be visible at rest before the current page moves."
		)
		assertContains(forward, "canvas.drawBitmap(current")
	}

	@Test
	fun backwardKeepsCurrentAtRestAndMovesDestinationIn() {
		val source = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()
		val backward = source.substringAfter("private fun drawBackward(").substringBefore("private fun drawMovingEdge(")

		assertTrue(
			backward.indexOf("canvas.drawBitmap(current") < backward.indexOf("canvas.translate(-width + width * progress"),
			"The current page must stay at rest underneath the incoming previous page."
		)
		assertContains(backward, "canvas.drawBitmap(destination")
	}

	@Test
	fun slideRendererHasNoCurlMeshOrReverseFace() {
		val source = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()

		assertFalse(source.contains("drawBitmapMesh"))
		assertFalse(source.contains("ReaderPageTurnEdgeFoldGeometry"))
		assertFalse(source.contains("turningReverse"))
		assertFalse(source.contains("underneath"))
	}

	@Test
	fun controllerAttachesTheFlatRenderer() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(controller, "ReaderPageTurnSlideView")
		assertFalse(controller.contains("ReaderPageTurnCurlView"))
	}
}
