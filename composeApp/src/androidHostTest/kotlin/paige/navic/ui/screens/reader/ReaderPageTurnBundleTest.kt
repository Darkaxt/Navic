package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReaderPageTurnBundleTest {
	@Test
	fun animationBitmapDimensionsFollowConfiguredQuality() {
		assertEquals(250, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Low))
		assertEquals(500, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Balanced))
		assertEquals(750, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.High))
		assertEquals(1_000, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Native))
	}

	@Test
	fun snapshotIdentityIncludesBitmapQuality() {
		val balanced = ReaderPageSlideSnapshotKey(
			visualPageIndex = 7,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			bitmapQuality = ReaderPageBitmapQuality.Balanced,
			bitmapWidth = 500,
			bitmapHeight = 800,
			surfaceWidth = 1_000,
			surfaceHeight = 1_600
		)
		val high = balanced.copy(bitmapQuality = ReaderPageBitmapQuality.High)

		assertNotEquals(balanced, high)
	}

	@Test
	fun snapshotWindowWarmsForwardReadingBeforeBackwardHistory() {
		assertEquals(
			listOf(6, 7, 8, 5, 4),
			readerPageSlideSnapshotWindow(centerPageIndex = 6, step = 1, pageCount = 12)
		)
	}

	@Test
	fun snapshotWindowUsesSpreadStepsAndClipsBookBoundaries() {
		assertEquals(
			listOf(2, 4, 6, 0),
			readerPageSlideSnapshotWindow(centerPageIndex = 2, step = 2, pageCount = 8)
		)
		assertEquals(
			listOf(6, 4, 2),
			readerPageSlideSnapshotWindow(centerPageIndex = 6, step = 2, pageCount = 8)
		)
	}

	@Test
	fun transientLiveValidationCandidateIsReleasedExactlyOnce() {
		val candidate = Any()
		var releases = 0

		assertEquals(
			ReaderPageRelocationContentValidationResult.Accepted,
			readerPageTransientLiveValidationResult(
				candidate = candidate,
				isStillCurrent = true,
				release = { releases += 1 }
			)
		)
		assertEquals(1, releases)
	}

	@Test
	fun staleTransientLiveValidationCandidateIsInvalidatedAndReleased() {
		val candidate = Any()
		var releases = 0

		assertEquals(
			ReaderPageRelocationContentValidationResult.Invalidated,
			readerPageTransientLiveValidationResult(
				candidate = candidate,
				isStillCurrent = false,
				release = { releases += 1 }
			)
		)
		assertEquals(1, releases)
	}

	@Test
	fun currentMissingLiveValidationCandidateIsContentRejected() {
		assertEquals(
			ReaderPageRelocationContentValidationResult.ContentRejected,
			readerPageTransientLiveValidationResult(
				candidate = null,
				isStillCurrent = true,
				release = { _: Any -> error("Missing candidate cannot be released") }
			)
		)
	}

	@Test
	fun liveValidatorBuildsExactTargetAndNeverPublishesTransientCapture() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBundleSource.android.kt"
		).readText()
		val validator = source.substringAfter(
			"fun validateLivePresentation("
		).substringBefore("fun capturePreparedRasterPage(")

		assertTrue(validator.contains("ReaderPageTurnPresentationTarget.Live("))
		assertTrue(validator.contains("token = request.token.value"))
		assertTrue(validator.contains("pageIndex = request.destinationOrdinal.toLong()"))
		assertTrue(validator.contains("foliateSessionId = request.foliateSessionId"))
		assertTrue(validator.contains("rasterGeneration = request.rasterGeneration"))
		assertTrue(validator.contains("textureGeneration = request.textureGeneration"))
		assertTrue(validator.contains("bitmapSource.capturePresentedSurface("))
		assertTrue(validator.contains("readerPageTransientLiveValidationResult("))
		assertTrue(validator.contains("?.recycle()"))
		listOf(
			"putSnapshot(",
			"cacheSnapshot(",
			"schedulePersistentSnapshot(",
			"publicationLedger.begin("
		).forEach { forbidden -> assertFalse(validator.contains(forbidden)) }
	}
}
