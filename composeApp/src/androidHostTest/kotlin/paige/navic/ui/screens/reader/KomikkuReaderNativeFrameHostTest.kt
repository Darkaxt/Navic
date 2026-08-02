package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderTextureDeckState
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
