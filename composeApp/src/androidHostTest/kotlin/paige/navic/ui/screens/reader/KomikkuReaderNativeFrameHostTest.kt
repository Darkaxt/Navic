package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
		assertContains(source, "nativeFrameRoot?.canAcceptNewPointer() == true")
		assertFalse(
			source.contains(
				"pageOperationPolicy.newPointer == ReaderPageNewPointerDecision.Accept"
			)
		)
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
