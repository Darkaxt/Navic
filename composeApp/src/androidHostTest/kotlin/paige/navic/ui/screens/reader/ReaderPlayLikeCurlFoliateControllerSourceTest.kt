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
	fun productionControllerTracesRasterDeckLoadingProgressAndLatency() {
		val source = controllerFile.readText()
		val prepare = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("private fun submitLibraryDeck(")

		assertContains(prepare, "\"deck-load-started\"")
		assertContains(prepare, "\"deck-load-progress\"")
		assertContains(prepare, "\"deck-load-completed\"")
		assertContains(prepare, "\"deck-load-failed\"")
		assertContains(prepare, "pageIndices.joinToString")
		assertContains(prepare, "elapsedMillis")
		assertContains(prepare, "onProgress =")
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
	fun edgeTapsUseThePreparedImportedDeckWithoutFallingThroughToFoliate() {
		val controllerSource = controllerFile.readText()
		val hostSource = hostFile.readText()
		val dispatch = hostSource
			.substringAfter("private fun dispatchSingleTapAction(action: KomikkuNavigationRegion)")
			.substringBefore("override fun onInterceptTouchEvent")
		val importedBranch = dispatch
			.substringAfter("if (shouldRouteTapToPlayLikeCurl())")
			.substringBefore("if (action != KomikkuNavigationRegion.MENU)")

		assertContains(controllerSource, "fun turn(pageChange: PageChange, gestureId: Long): Boolean")
		assertContains(controllerSource, "surfaceView.turn(pageChange, gestureId)")
		assertContains(controllerSource, "PlayLikeCurl tap turn")
		assertContains(hostSource, "private fun shouldRouteTapToPlayLikeCurl()")
		assertContains(hostSource, "private fun playLikeCurlPageChangeFor(")
		assertContains(importedBranch, "playLikeCurlController.turn(pageChange, gestureId)")
		assertContains(importedBranch, "Reader PlayLikeCurl tap")
		assertContains(importedBranch, "return")
		assertFalse(
			importedBranch.contains("onAction("),
			"An imported tap must never fall through to an immediate Foliate relocation."
		)
	}

	@Test
	fun importedTapDirectionUsesTheReaderDirectionContract() {
		val source = hostFile.readText()
		val mapper = source
			.substringAfter("private fun playLikeCurlPageChangeFor(")
			.substringBefore("private fun shouldRouteTapToPlayLikeCurl()")

		assertContains(source, "pageTurnReadingDirection")
		assertContains(mapper, "readerTapZonePageTurnDirectionFor(")
		assertContains(mapper, "ReaderPageTurnDirection.Next -> PageChange.NEXT")
		assertContains(mapper, "ReaderPageTurnDirection.Previous -> PageChange.PREVIOUS")
	}

	@Test
	fun settlementKeepsTheAcceptedDeckUntilFoliateConfirmsTheTargetPage() {
		val source = controllerFile.readText()
		val settlementStarted = source
			.substringAfter("override fun onSettlementStarted(")
			.substringBefore("override fun onSettlementCompleted(")

		assertFalse(
			settlementStarted.contains("submitLibraryDeck("),
			"A settlement must not require the next adjacency raster before the accepted animation can finish."
		)
		assertFalse(
			settlementStarted.contains("ReaderTextureDeckState.Empty"),
			"The accepted imported surface must remain ready for the entire settlement."
		)
		assertContains(settlementStarted, "ReaderTextureDeckState.Settling")
		assertContains(settlementStarted, "ReaderPageInteractionState.Settling")
	}

	@Test
	fun rasterTextureAndInteractionReadinessHaveSeparateOwners() {
		val controllerSource = controllerFile.readText()
		val hostSource = hostFile.readText()

		assertContains(controllerSource, "ReaderPageRendererReadinessState")
		assertContains(controllerSource, "ReaderTextureDeckState")
		assertContains(controllerSource, "ReaderPageInteractionState.BackgroundPrefetch")
		assertFalse(
			controllerSource.contains("private var interactionReady"),
			"Interaction readiness must be an explicit state, not a second boolean authority."
		)
		assertContains(hostSource, "latestRasterPreparationState")
		assertContains(hostSource, "latestRendererReadinessState")
		assertContains(hostSource, "publishMergedPagePreparationState()")
		assertContains(hostSource, "raster.readiness.copy(")
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
	fun portraitAnimationSurfaceStopsBeforeTheStaticBackCoverBoard() {
		val source = controllerFile.readText()
		val submit = source
			.substringAfter("private fun submitLibraryDeck(")
			.substringBefore("private fun PreparedPages.page(")

		assertContains(source, "private fun updateSurfaceBounds(")
		assertContains(submit, "updateSurfaceBounds(pages, ordinal)")
		assertTrue(
			submit.indexOf("updateSurfaceBounds(pages, ordinal)") <
				submit.indexOf("surfaceView.submitDeck(deck)"),
			"The imported surface must match Foliate's page rectangle before the deck becomes visible."
		)
		assertContains(source, "readerPlayLikeCurlPortraitSurfaceWidth(")
		assertContains(source, "ReaderPlayLikeCurlOrientation.Landscape -> ViewGroup.LayoutParams.MATCH_PARENT")
	}

	@Test
	fun nativeHostMountsAndDrivesTheImportedSurface() {
		val source = hostFile.readText()

		assertContains(source, "ReaderPlayLikeCurlFoliateController")
		assertContains(source, "playLikeCurlController.surfaceView")
		assertContains(source, "playLikeCurlController.onHostContentReady()")
		assertContains(source, "playLikeCurlController.onPageTouchEvent(event, gestureId)")
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
		assertContains(dispatch, "playLikeCurlGestureOwned = shouldOwnPlayLikeCurlGesture()")
		assertContains(ownerBranch, "playLikeCurlController.onPageTouchEvent(event, gestureId)")
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
				.contains("playLikeCurlController.onPageTouchEvent(event, gestureId)"),
			"The fallback reader path must not send a second copy of the event to PlayLikeCurl."
		)
	}

	@Test
	fun rendererCallbacksCarryTheOriginatingGestureIdentityToOneTerminalLedger() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()

		assertContains(controller, "surfaceView.onPageTouchEvent(event, gestureId)")
		assertContains(controller, "surfaceView.turn(pageChange, gestureId)")
		assertContains(controller, "surfaceView.cancelGesture(gestureId)")
		assertTrue(
			Regex("override fun onGestureRejected\\(\\s*gestureId: Long,").containsMatchIn(controller),
			"Gesture rejection callbacks must preserve the originating gesture ID."
		)
		assertTrue(
			Regex("override fun onGestureCancelled\\(\\s*gestureId: Long,").containsMatchIn(controller),
			"Gesture cancellation callbacks must preserve the originating gesture ID."
		)
		assertTrue(
			Regex("override fun onSettlementCompleted\\(\\s*gestureId: Long,").containsMatchIn(controller),
			"Settlement callbacks must preserve the originating gesture ID."
		)
		assertContains(controller, "finishGesture(gestureId,")
		assertContains(host, "private val pageGestureLifecycle = ReaderPageGestureLifecycle()")
		assertContains(host, "pageGestureLifecycle.completeGesture(gestureId, outcome)")
	}
}
