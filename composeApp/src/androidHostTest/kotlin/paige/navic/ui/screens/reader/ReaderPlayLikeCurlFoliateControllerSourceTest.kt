package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlFoliateControllerSourceTest {
	private val controllerFile =
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt")
	private val hostFile =
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt")

	@Test
	fun productionControllerUsesFoliateRastersAndImportedSurface() {
		assertTrue(controllerFile.isFile, "Production PlayLikeCurl controller must exist")
		val source = controllerFile.readText()

		assertContains(source, "PageSurfaceView")
		assertContains(source, "ReaderPlayLikeCurlFoliateRasterLoader")
		assertContains(source, "pageTurnRasterPreparationPlan")
		assertContains(source, "ReaderPlayLikeCurlRasterAdapter")
		assertContains(source, "readerPlayLikeCurlLibraryDeck")
		assertContains(source, "type: 'goToVisualPage'")
		assertFalse(source.contains("ReaderPlayLikeCurlAssetBitmapSource"))
		assertFalse(source.contains("ReaderPlayLikeCurlDiagnosticBitmapSource"))
	}

	@Test
	fun productionControllerTracesEveryInteractionReadinessBoundary() {
		val source = controllerFile.readText()

		assertContains(source, "logActivationState(")
		assertContains(source, "\"enabled\"")
		assertContains(source, "\"host-attached\"")
		assertContains(source, "\"capabilities-available\"")
		assertContains(source, "\"preparation-ready\"")
		assertContains(source, "\"deck-submitted\"")
		assertContains(source, "\"deck-prepared\"")
		assertContains(source, "\"refresh-gated\"")
	}

	@Test
	fun productionControllerTracesGestureOwnershipAndExactSettlementWithoutMoveSpam() {
		val controllerSource = controllerFile.readText()
		val hostSource = hostFile.readText()

		assertContains(hostSource, "Reader PlayLikeCurl gesture owner")
		assertContains(controllerSource, "PlayLikeCurl settlement started")
		assertContains(controllerSource, "PlayLikeCurl settlement completed")
		assertContains(controllerSource, "PlayLikeCurl settlement cancelled")
		assertContains(controllerSource, "PlayLikeCurl exact page dispatched")
		assertFalse(hostSource.contains("Reader PlayLikeCurl gesture move"))
	}

	@Test
	fun productionControllerWaitsForTheActiveRasterProducerInsteadOfRequestingItAgain() {
		val source = controllerFile.readText()
		val refresh = source
			.substringAfter("private fun refreshPreparedDeck()")
			.substringBefore("private fun prepareProfile(")
		val unavailableDeck = source
			.substringAfter("if (deck == null)")
			.substringBefore("return@launch")

		assertContains(source, "private var preparationPhase = ReaderPagePreparationPhase.Idle")
		assertContains(source, "preparationPhase = state.phase")
		assertContains(refresh, "ReaderPagePreparationPhase.Preparing")
		assertContains(refresh, "\"preparation-in-progress\"")
		assertContains(refresh, "requestPrewarmIfIdle(")
		assertContains(unavailableDeck, "requestPrewarmIfIdle(")
		assertFalse(
			unavailableDeck.contains("onRequestPrewarm()"),
			"A missing deck while the raster producer is active must not recursively start another producer."
		)
	}

	@Test
	fun productionControllerPreparesOneDeckWindowAroundTheFoliateCenter() {
		val source = controllerFile.readText()
		val prepare = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("private fun submitLibraryDeck(")

		assertContains(
			prepare,
			"readerPlayLikeCurlLibraryDeckPageIndices(",
			ignoreCase = false,
			"The controller should prepare exactly the page window consumed by one imported deck."
		)
		assertFalse(
			prepare.contains("listOf(centerOrdinal - step, centerOrdinal, centerOrdinal + step)"),
			"Expanding three overlapping deck windows requests Foliate snapshots that were never produced."
		)
		assertFalse(
			prepare.contains(".flatMap"),
			"The real EPUB adapter must not recursively expand adjacent deck windows."
		)
	}

	@Test
	fun nativeHostMountsAndDrivesTheImportedSurface() {
		val source = hostFile.readText()

		assertContains(source, "ReaderPlayLikeCurlFoliateController")
		assertContains(source, "playLikeCurlController.surfaceView")
		assertContains(source, "playLikeCurlController.onHostContentReady()")
		assertContains(source, "playLikeCurlController.onPageTouchEvent(event)")
		assertContains(source, "playLikeCurlController.showSurfaceForGesture()")
		assertContains(source, "playLikeCurlController.synchronizeVisualPageIndex(normalized, reason)")
	}

	@Test
	fun productionGesturePathDoesNotDriveTheRetiredRenderer() {
		val source = hostFile.readText()
		val swipeHandler = source
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent)")
			.substringBefore("private fun dispatchHorizontalSwipeViewerAction")

		assertFalse(swipeHandler.contains("pageTurnController.update("))
		assertFalse(swipeHandler.contains("pageTurnController.release("))
	}

	@Test
	fun importedSurfaceExclusivelyOwnsAnAcceptedGestureBeforeAndroidChildDispatch() {
		val source = hostFile.readText()
		val dispatch = source
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean")
			.substringBefore("private fun handleSwipeTouchEvent(event: MotionEvent)")
		val ownerBranch = dispatch
			.substringAfter("if (playLikeCurlGestureOwned) {")
			.substringBefore("val handled = super.dispatchTouchEvent(event)")

		assertContains(source, "private var playLikeCurlGestureOwned: Boolean = false")
		assertContains(dispatch, "playLikeCurlGestureOwned = usesNativePageTurnCanvas()")
		assertContains(ownerBranch, "playLikeCurlController.onPageTouchEvent(event)")
		assertContains(ownerBranch, "handleSwipeTouchEvent(event)")
		assertContains(ownerBranch, "return true")
		assertTrue(
			dispatch.indexOf("if (playLikeCurlGestureOwned) {") <
				dispatch.indexOf("val handled = super.dispatchTouchEvent(event)"),
			"A prepared imported surface must claim DOWN before the WebView or a child view sees it."
		)
		assertFalse(
			dispatch
				.substringAfter("val handled = super.dispatchTouchEvent(event)")
				.contains("playLikeCurlController.onPageTouchEvent(event)"),
			"The fallback reader path must not send a second copy of the event to PlayLikeCurl."
		)
	}
}
