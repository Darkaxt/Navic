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
		assertContains(source, "setZOrderMediaOverlay(true)")
		assertFalse(source.contains("setZOrderOnTop(true)"))
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
	fun successfulHandoffCrossfadesValidatedRasterUntilWebViewExposureCommits() {
		val controller = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateController.android.kt"
		).readText()
		val shield = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageInlineRasterShield.android.kt"
		).readText()
		val handoff = controller
			.substringAfter("private fun finalizeHandoffPresentation(")
			.substringBefore("private fun hideSurface()")
		val fade = shield
			.substringAfter("fun fadeOut(")
			.substringBefore("private fun awaitExposedWebViewFrame(")
		val exposure = shield
			.substringAfter("private fun awaitExposedWebViewFrame(")
			.substringBefore("private fun completeFade(")
		val fadeCompletion = shield
			.substringAfter("private fun completeFade(")
			.substringBefore("private fun cancelPendingPresentation(")
		val reveal = controller
			.substringAfter("private fun revealSurfaceAfterNextPresentedFrame(")
			.substringBefore("fun cancelGestureAfterHostTerminal(")

		assertContains(controller, "ReaderPageLiveHandoffCrossfadeMillis = 200L")
		assertContains(handoff, "inlineRasterShield.present(")
		assertContains(handoff, "hideSurfaceBehindInlineRasterShield()")
		assertContains(handoff, "inlineRasterShield.fadeOut(")
		assertContains(handoff, "onFinalized(finalized)")
		assertFalse(handoff.contains("if (!presented) {\n\t\t\t\thideCurlSurface()"))
		assertFalse(handoff.contains("surfaceView.animate()"))
		assertContains(fade, "onExposedFrameCommitted: (Boolean) -> Unit")
		assertContains(fade, "awaitExposedWebViewFrame(request)")
		assertContains(exposure, "registerFrameCommitCallback")
		assertContains(exposure, "host.invalidate()")
		assertContains(exposure, "host.postOnAnimation")
		assertFalse(fadeCompletion.contains("clearPresentation()"))
		assertTrue(
			fadeCompletion.indexOf("callback?.invoke(effectiveCommit)") <
				fadeCompletion.indexOf("if (effectiveCommit && ownsPresentation()")
		)
		assertContains(fadeCompletion, "view.alpha = 1f")
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
