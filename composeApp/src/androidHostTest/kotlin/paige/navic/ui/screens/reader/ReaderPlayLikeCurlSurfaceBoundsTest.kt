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
	fun successfulHandoffKeepsNativePredecessorUntilCommonReceiptCommits() {
		val controller = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateController.android.kt"
		).readText()
		val visualHandoff = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderWebViewVisualHandoff.android.kt"
		).readText()
		val productionWiring = controller
			.substringAfter("ReaderPageRelocationVisualHandoffCoordinator(")
			.substringBefore("canRecover =")
		val commonCommit = visualHandoff
			.substringAfter("private fun commitCommonPresentationHandoff(")
			.substringBefore("private fun onPresentationFinalized(")

		assertContains(productionWiring, "requestPresentationHandoff =")
		assertContains(productionWiring, "commitLiveEngineExposure =")
		assertFalse(productionWiring.contains("finalizePresentation = ::"))
		assertFalse(controller.contains("private fun finalizeHandoffPresentation("))
		assertFalse(controller.contains("inlineRasterShield.fadeOut("))
		assertContains(commonCommit, "ReaderLiveEnginePresentationProof(")
		assertContains(commonCommit, "presentedFrameSequence = ready.presentedFrameSequence")
		assertContains(commonCommit, "receipt.authorizes(event)")
		assertContains(commonCommit, "onPresentationFinalized(request, finalizationEpoch, accepted)")
	}

	@Test
	fun composeTransportAndRetryRemainTopmostAndHitTestableAcrossSurfaceUpdates() {
		val frameHost = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val readerRoot = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val transport = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderWhispersyncStatusBadge.kt"
		).readText()
		val retry = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPagePreparationOverlay.kt"
		).readText()
		val frameRoot = frameHost
			.substringAfter("private class KomikkuReaderNativeFrameRoot")
			.substringBefore("private class KomikkuReaderNativeViewerContainer")
		val transportBody = transport
			.substringAfter("internal fun KomikkuWhispersyncPlaybackControl(")
			.substringBefore("internal fun KomikkuWhispersyncStatusBadge(")

		val readerLayer = Regex("""addView\(\s*readerContainer,""")
			.find(frameRoot)?.range?.first ?: -1
		val composeLayer = Regex("""addView\(\s*composeOverlay,""")
			.find(frameRoot)?.range?.first ?: -1
		assertTrue(readerLayer >= 0 && composeLayer > readerLayer)
		assertContains(frameRoot, "composeOverlay.elevation = 32f")
		assertContains(frameRoot, "composeOverlay.translationZ = 32f")
		assertContains(frameRoot, "composeOverlay.bringToFront()")
		assertContains(frameHost.substringAfter("update = { root ->"), "root.setComposeOverlay(hostedComposeOverlay)")
		assertContains(transportBody, ".semantics {")
		assertContains(transportBody, "onClick(label = accessibilityDescription)")
		assertContains(transportBody, ".pointerInput(Unit)")
		assertContains(transportBody, "detectTapGestures(")
		assertContains(retry, "TextButton(onClick = onRetry")
		assertTrue(
			readerRoot.indexOf("KomikkuComposeOverlay(") <
				readerRoot.indexOf("ReaderPagePreparationOverlay(")
		)
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
