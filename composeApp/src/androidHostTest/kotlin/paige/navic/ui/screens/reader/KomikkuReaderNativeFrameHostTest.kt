package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.readerPagePreparationState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KomikkuReaderNativeFrameHostTest {
	private val hostFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"KomikkuReaderNativeFrameHost.android.kt"
	)

	@Test
	fun disabledCanvasStaysBlockedUntilPublicationContentBecomesReady() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val pending = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = false,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)
		val ready = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Idle, pending.phase)
		assertEquals(ReaderPagePreparationPresentation.Cover, pending.presentation)
		assertFalse(pending.interactiveReady)
		assertTrue(pending.showsProgress)
		assertEquals(ReaderPagePreparationPhase.Ready, ready.phase)
		assertEquals(ReaderPagePreparationPresentation.Hidden, ready.presentation)
		assertTrue(ready.interactiveReady)
	}

	@Test
	fun disabledCanvasCannotPublishAStuckPreparationCover() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Ready, merged.phase)
		assertEquals(ReaderPagePreparationPresentation.Hidden, merged.presentation)
		assertTrue(merged.operationPolicy.newPointer is ReaderPageNewPointerDecision.Accept)
	}

	@Test
	fun enabledCanvasStillPublishesBlockingRasterPreparation() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = true,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPhase.Preparing, merged.phase)
		assertEquals(ReaderPagePreparationPresentation.Cover, merged.presentation)
		assertEquals(1, merged.completedCount)
	}
	@Test
	fun canvasAndContentReadinessTransitionsRepublishTheInputGate() {
		val source = hostFile.readText()
		val canvasTransition = source
			.substringAfterLast("fun setPageTurnCanvasEnabled(enabled: Boolean)")
			.substringBefore("fun setPageTurnReadingDirection(")
		val contentTransition = source
			.substringAfterLast("fun setPageTurnContentReadyKey(contentReadyKey: String?)")
			.substringBefore("fun setPageTurnPaginationStatus(")

		assertContains(canvasTransition, "publishMergedPagePreparationState()")
		assertContains(contentTransition, "publishMergedPagePreparationState()")
		assertTrue(
			contentTransition.indexOf("publishMergedPagePreparationState()") <
				contentTransition.indexOf("if (contentReadyKey == null) return")
		)
	}

	@Test
	fun busyFeedbackUsesTheOneTerminalPublisherWithoutInterceptingPreparationGestures() {
		val source = hostFile.readText()

		assertContains(source, "readerPageGestureShouldShowBusyFeedback(outcome)")
		assertContains(source, "onRendererBusyGestureRejected()")
		assertFalse(source.contains("composeOverlay.isClickable = visible"))
	}

	@Test
	fun busyFeedbackClearsOnlyAfterActualRendererPointerAdmission() {
		val source = hostFile.readText()

		assertContains(
			source,
			"fun canAcceptNewPointer(): Boolean = playLikeCurlController.isAvailable"
		)
		assertContains(source, "currentNativeFrameRoot?.canAcceptNewPointer() == true")
		assertFalse(
			source.contains(
				"pageOperationPolicy.newPointer == ReaderPageNewPointerDecision.Accept"
			)
		)
	}

	@Test
	fun busyFeedbackUsesANonTouchableWindowAboveTheOnTopCurlSurface() {
		val hostSource = hostFile.readText()
		val popupSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderRendererBusyPopup.android.kt"
		).readText()

		assertContains(hostSource, "ReaderRendererBusyPopup(")
		assertContains(popupSource, "PopupWindow(")
		assertContains(popupSource, "isFocusable = false")
		assertContains(popupSource, "isTouchable = false")
		assertContains(
			popupSource,
			"windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL"
		)
		assertFalse(hostSource.contains("androidx.compose.ui.window.Popup"))
	}

	@Test
	fun retainedValidatedPresentationSurvivesOnlyTransientRendererReadiness() {
		val ownership = ReaderRetainedValidatedPresentationOwnership()

		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Settling)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Empty)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = true))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Failed)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
	}

	@Test
	fun busyFeedbackMinimumTimerIsOwnedByFullyVisibleNestedOverlay() {
		val source = hostFile.readText()

		assertContains(
			source,
			"val rendererBusyFeedbackVisibility = remember { MutableTransitionState(false) }"
		)
		assertContains(source, "fullyVisibleRejectionToken = activeToken")
		assertContains(
			source,
			"readerRendererBusyFeedbackCanStartMinimumTimer("
		)
		assertFalse(source.contains("var rendererBusyFeedbackVisible by remember"))
	}

	@Test
	fun publicationShellArtworkIsVisibleOnlyForControllerOwnedShellCover() {
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = true,
				preparationShield = false
			),
			readerNativePresentationLayerVisibility(
				shellCoverVisible = true,
				pagePreparationCoverVisible = true,
				hasValidatedRasterPresentation = false
			)
		)
	}

	@Test
	fun preparationRetainsValidatedRasterWithoutPublicationCoverOrNeutralShield() {
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationLayerVisibility(
				shellCoverVisible = false,
				pagePreparationCoverVisible = true,
				hasValidatedRasterPresentation = true
			)
		)
	}

	@Test
	fun preparationWithoutValidatedRasterUsesNeutralFailClosedShield() {
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = true
			),
			readerNativePresentationLayerVisibility(
				shellCoverVisible = false,
				pagePreparationCoverVisible = true,
				hasValidatedRasterPresentation = false
			)
		)
	}
}
