package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlSurfaceBoundsTest {
	@Test
	fun rendererSurfaceKeepsTheWholeFoliateCompositionVisible() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateController.android.kt"
		).readText()
		val update = source
			.substringAfter("private fun updateSurfaceBounds()")
			.substringBefore("private fun PreparedPages.page(")

		assertContains(update, "params.width = ViewGroup.LayoutParams.MATCH_PARENT")
		assertContains(update, "params.height = ViewGroup.LayoutParams.MATCH_PARENT")
		assertFalse(update.contains("page.bitmap.width"))
		assertFalse(update.contains("page.bitmap.height"))
		assertFalse(source.contains("readerPlayLikeCurlPortraitSurfaceWidth("))
		assertContains(source, "setZOrderOnTop(true)")
		assertFalse(source.contains("setZOrderMediaOverlay(true)"))
		assertTrue(
			update.indexOf("params.width = ViewGroup.LayoutParams.MATCH_PARENT") <
				update.indexOf("surfaceView.requestLayout()")
		)
	}

	@Test
	fun inlineHandoffWaitsPastSubmissionForTheWindowBufferToLatch() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageInlineRasterShield.android.kt"
		).readText()
		val api29Fence = source
			.substringAfter("observer.registerFrameCommitCallback {")
			.substringBefore("view.postInvalidateOnAnimation()")
		val displayLatch = source
			.substringAfter("private fun awaitDisplayLatch(request: Long)")
			.substringBefore("private fun cancelPendingPresentation()")

		assertContains(api29Fence, "awaitDisplayLatch(request)")
		assertEquals(
			2,
			"view.postOnAnimation".toRegex().findAll(displayLatch).count(),
			"The shield must survive two display frames after app-window submission before the curl surface is retired."
		)
		assertContains(displayLatch, "complete(request, true)")
	}

	@Test
	fun successfulHandoffCrossfadesValidatedRasterOnEverySupportedApi() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateController.android.kt"
		).readText()
		val handoff = source
			.substringAfter("private fun hideSurfaceAfterHandoff(")
			.substringBefore("private fun hideSurface()")
		val reveal = source
			.substringAfter("private fun revealSurfaceAfterNextPresentedFrame(")
			.substringBefore("fun cancelGestureAfterHostTerminal(")

		assertContains(source, "ReaderPageLiveHandoffCrossfadeMillis = 200L")
		assertContains(handoff, "inlineRasterShield.present(")
		assertContains(handoff, "hideSurfaceBehindInlineRasterShield()")
		assertContains(
			handoff,
			"inlineRasterShield.fadeOut(ReaderPageLiveHandoffCrossfadeMillis)"
		)
		assertFalse(handoff.contains("surfaceView.animate()"))
		assertContains(reveal, "surfaceView.animate().cancel()")
		assertContains(reveal, "presentedSurfaceGestureId == gestureId")
		assertContains(reveal, "surfaceView.alpha = 0f")
		assertContains(reveal, "surfaceView.requestNextPresentedFrame")
		assertContains(reveal, "surfaceView.alpha = 1f")
	}

	private fun sourceFile(relativePath: String): File {
		var directory = File(System.getProperty("user.dir")).absoluteFile
		repeat(8) {
			File(directory, relativePath).takeIf(File::isFile)?.let { return it }
			directory = directory.parentFile ?: return@repeat
		}
		error("Could not locate $relativePath from ${System.getProperty("user.dir")}")
	}
}
