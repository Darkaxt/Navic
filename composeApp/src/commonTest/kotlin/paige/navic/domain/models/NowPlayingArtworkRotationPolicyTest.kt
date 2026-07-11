package paige.navic.domain.models

import paige.navic.domain.models.settings.CoverArtShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingArtworkRotationPolicyTest {
	@Test
	fun successfulArtworkRevealFadeIsShortAndSubtle() {
		assertTrue(NowPlayingArtworkRevealDurationMs in 120..300)
	}

	@Test
	fun realCoverMustResolveForTheExactSongAndArtworkRequestBeforeVinylAppears() {
		val previousRequest = NowPlayingArtworkRequestIdentity(
			songId = "song-1",
			coverArtId = "cover-1",
			imageUrl = null,
			imageCacheKey = "cover-1"
		)
		val currentRequest = NowPlayingArtworkRequestIdentity(
			songId = "song-2",
			coverArtId = "cover-2",
			imageUrl = null,
			imageCacheKey = "cover-2"
		)

		assertFalse(
			isNowPlayingVinylArtworkReady(
				hasCoverArt = true,
				hasGeneratedArtwork = true,
				requestedArtwork = currentRequest,
				resolvedArtwork = null
			)
		)
		assertFalse(
			isNowPlayingVinylArtworkReady(
				hasCoverArt = true,
				hasGeneratedArtwork = true,
				requestedArtwork = currentRequest,
				resolvedArtwork = previousRequest
			)
		)
		assertTrue(
			isNowPlayingVinylArtworkReady(
				hasCoverArt = true,
				hasGeneratedArtwork = true,
				requestedArtwork = currentRequest,
				resolvedArtwork = currentRequest
			)
		)
	}

	@Test
	fun generatedArtworkIsImmediatelyVinylReadyOnlyWhenNoRealCoverIsRequested() {
		val request = NowPlayingArtworkRequestIdentity(
			songId = "song-1",
			coverArtId = null,
			imageUrl = null,
			imageCacheKey = null
		)

		assertTrue(
			isNowPlayingVinylArtworkReady(
				hasCoverArt = false,
				hasGeneratedArtwork = true,
				requestedArtwork = request,
				resolvedArtwork = null
			)
		)
		assertFalse(
			isNowPlayingVinylArtworkReady(
				hasCoverArt = false,
				hasGeneratedArtwork = false,
				requestedArtwork = request,
				resolvedArtwork = null
			)
		)
	}

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
	fun generatedFallbackCanUseRotatingVinylInNowPlaying() {
		assertTrue(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = false,
				hasGeneratedArtwork = true
			)
		)
		assertTrue(
			shouldShowNowPlayingVinylOverlay(
				isVinylPresentation = true,
				hasCoverArt = false,
				hasGeneratedArtwork = true
			)
		)
	}

	@Test
	fun wideLandscapeKeepsGeneratedArtworkInVinylPresentationEvenWhenStatic() {
		assertTrue(
			shouldUseNowPlayingVinylPresentation(
				isWideLandscape = true,
				isRotatingArtwork = false,
				hasCoverArt = false,
				hasGeneratedArtwork = true
			)
		)
		assertFalse(
			shouldUseNowPlayingVinylPresentation(
				isWideLandscape = false,
				isRotatingArtwork = false,
				hasCoverArt = false,
				hasGeneratedArtwork = true
			)
		)
	}

	@Test
	fun staleForegroundClipIsClearedWhenSongChanges() {
		assertEquals(
			"song-1",
			retainedNowPlayingForegroundClipSongId(
				foregroundClipSongId = "song-1",
				currentSongId = "song-1"
			)
		)
		assertEquals(
			null,
			retainedNowPlayingForegroundClipSongId(
				foregroundClipSongId = "song-1",
				currentSongId = "song-2"
			)
		)
	}

	@Test
	fun mediaSlotShowsOnlyOneForegroundSurface() {
		assertEquals(
			NowPlayingMediaSlotMode.ForegroundClip,
			nowPlayingMediaSlotMode(
				showArtwork = true,
				currentSongId = "song-1",
				foregroundClipSongId = "song-1",
				hasClip = true
			)
		)
		assertEquals(
			NowPlayingMediaSlotMode.VinylArtwork,
			nowPlayingMediaSlotMode(
				showArtwork = true,
				currentSongId = "song-2",
				foregroundClipSongId = "song-1",
				hasClip = true
			)
		)
		assertEquals(
			NowPlayingMediaSlotMode.Empty,
			nowPlayingMediaSlotMode(
				showArtwork = false,
				currentSongId = null,
				foregroundClipSongId = "song-1",
				hasClip = true
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
	fun vinylOverlayOnlyAppearsForVinylPresentationWithArtwork() {
		assertTrue(
			shouldShowNowPlayingVinylOverlay(
				isVinylPresentation = true,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldShowNowPlayingVinylOverlay(
				isVinylPresentation = false,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldShowNowPlayingVinylOverlay(
				isVinylPresentation = true,
				hasCoverArt = false
			)
		)
	}

	@Test
	fun vinylOverlayUsesArtworkRotationWhileSpinning() {
		assertEquals(
			137f,
			nowPlayingVinylOverlayRotationDegrees(
				isRotatingArtwork = true,
				artworkRotationDegrees = 137f
			)
		)
		assertEquals(
			0f,
			nowPlayingVinylOverlayRotationDegrees(
				isRotatingArtwork = false,
				artworkRotationDegrees = 137f
			)
		)
	}

	@Test
	fun rotationDegreesAdvanceFromElapsedFrameTime() {
		val durationMillis = NowPlayingArtworkRotationDurationMs.toLong()
		assertEquals(
			0f,
			nowPlayingArtworkRotationDegreesForElapsedMillis(0L),
			absoluteTolerance = 0.001f
		)
		assertEquals(
			90f,
			nowPlayingArtworkRotationDegreesForElapsedMillis(durationMillis / 4L),
			absoluteTolerance = 0.001f
		)
		assertEquals(
			0f,
			nowPlayingArtworkRotationDegreesForElapsedMillis(durationMillis),
			absoluteTolerance = 0.001f
		)
		assertEquals(
			180f,
			nowPlayingArtworkRotationDegreesForElapsedMillis(durationMillis + durationMillis / 2L),
			absoluteTolerance = 0.001f
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

	@Test
	fun wideLandscapeVinylUsesSoftEdgeCompressForRealArtwork() {
		assertEquals(
			NowPlayingDiscFitMode.SoftEdgeCompress,
			nowPlayingDiscFitMode(
				isWideLandscape = true,
				isVinylArtwork = true,
				hasRealArtwork = true
			)
		)
		assertEquals(
			NowPlayingDiscFitMode.Crop,
			nowPlayingDiscFitMode(
				isWideLandscape = false,
				isVinylArtwork = true,
				hasRealArtwork = true
			)
		)
	}

	@Test
	fun softEdgeCompressKeepsSquareCoversCroppedAndFitsWideCovers() {
		assertEquals(
			NowPlayingDiscContentScale.Crop,
			nowPlayingDiscContentScale(
				fitMode = NowPlayingDiscFitMode.SoftEdgeCompress,
				imageWidth = 1000,
				imageHeight = 1000
			)
		)
		assertEquals(
			NowPlayingDiscContentScale.Fit,
			nowPlayingDiscContentScale(
				fitMode = NowPlayingDiscFitMode.SoftEdgeCompress,
				imageWidth = 1600,
				imageHeight = 900
			)
		)
		assertEquals(
			NowPlayingDiscContentScale.Crop,
			nowPlayingDiscContentScale(
				fitMode = NowPlayingDiscFitMode.Crop,
				imageWidth = 1600,
				imageHeight = 900
			)
		)
	}
}
