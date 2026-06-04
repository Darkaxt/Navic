package paige.navic.domain.models

import paige.navic.domain.models.settings.CoverArtShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingArtworkRotationPolicyTest {
	@Test
	fun rotationDefaultsToInactive() {
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = false,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = true
			)
		)
	}

	@Test
	fun rotationRequiresPlayingActiveArtworkWithCoverArt() {
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = true,
				isActiveArtwork = true,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = false,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = false
			)
		)
	}

	@Test
	fun rotationCanRunForPlayingActiveArtworkWithCoverArt() {
		assertTrue(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = true
			)
		)
	}

	@Test
	fun rotatingArtworkUsesDiscShapeForVisibleMotion() {
		assertEquals(
			CoverArtShape.Circle,
			nowPlayingArtworkShapeForPlayback(
				configuredShape = CoverArtShape.Soft,
				isRotating = true
			)
		)
		assertEquals(
			CoverArtShape.Curved,
			nowPlayingArtworkShapeForPlayback(
				configuredShape = CoverArtShape.Curved,
				isRotating = false
			)
		)
	}

	@Test
	fun vinylOverlayOnlyAppearsForRotatingArtworkWithCoverArt() {
		assertTrue(
			shouldShowNowPlayingVinylOverlay(
				isRotatingArtwork = true,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldShowNowPlayingVinylOverlay(
				isRotatingArtwork = false,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldShowNowPlayingVinylOverlay(
				isRotatingArtwork = true,
				hasCoverArt = false
			)
		)
	}

	@Test
	fun turnTableWidgetUsesStaticVinylArtworkOnlyWithCoverArt() {
		assertTrue(shouldUseTurnTableWidgetVinylArtwork(hasCoverArt = true))
		assertFalse(shouldUseTurnTableWidgetVinylArtwork(hasCoverArt = false))
	}

	@Test
	fun rotatingFallbackArtworkUsesArcLabel() {
		assertEquals(
			NowPlayingFallbackLabelStyle.Arc,
			nowPlayingFallbackLabelStyle(isRotatingArtwork = true)
		)
		assertEquals(
			NowPlayingFallbackLabelStyle.Center,
			nowPlayingFallbackLabelStyle(isRotatingArtwork = false)
		)
	}

	@Test
	fun technicalInfoKeepsSquareArtworkPlacement() {
		assertEquals(
			NowPlayingTechnicalInfoPlacement(bottomPaddingDp = 8, verticalOffsetDp = 0),
			nowPlayingTechnicalInfoPlacement(isLandscape = false, isVinylArtwork = false)
		)
		assertEquals(
			NowPlayingTechnicalInfoPlacement(bottomPaddingDp = 16, verticalOffsetDp = 0),
			nowPlayingTechnicalInfoPlacement(isLandscape = true, isVinylArtwork = false)
		)
	}

	@Test
	fun technicalInfoMovesBelowVinylArtwork() {
		val portraitPlacement = nowPlayingTechnicalInfoPlacement(
			isLandscape = false,
			isVinylArtwork = true
		)
		val landscapePlacement = nowPlayingTechnicalInfoPlacement(
			isLandscape = true,
			isVinylArtwork = true
		)

		assertEquals(8, portraitPlacement.bottomPaddingDp)
		assertTrue(portraitPlacement.verticalOffsetDp > 0)
		assertEquals(16, landscapePlacement.bottomPaddingDp)
		assertTrue(landscapePlacement.verticalOffsetDp > 0)
		assertTrue(portraitPlacement.verticalOffsetDp > landscapePlacement.verticalOffsetDp)
	}

	@Test
	fun vinylOverlayGeometryKeepsCenterReadable() {
		assertTrue(NowPlayingVinylSpindleRadiusFraction > 0f)
		assertTrue(NowPlayingVinylLabelRadiusFraction > NowPlayingVinylSpindleRadiusFraction)
		assertTrue(NowPlayingVinylLabelRadiusFraction < NowPlayingVinylGrooveStartRadiusFraction)
		assertTrue(NowPlayingVinylGrooveStartRadiusFraction < NowPlayingVinylGrooveEndRadiusFraction)
		assertTrue(NowPlayingVinylGrooveEndRadiusFraction < 1f)
	}

	@Test
	fun lidaClipArtworkTransitionIsShortAndSubtle() {
		assertTrue(NowPlayingVideoArtworkCrossfadeDurationMs in 120..500)
		assertTrue(NowPlayingVideoArtworkCrossfadeInitialScale in 0.95f..1f)
	}
}
