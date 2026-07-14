package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.readerAndroidFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
	fun parsesSimplifiedLandscapeSpreadSlidePlan() {
		val plan = ReaderPageTurnTransitionPlan.parseOrThrow(
			encoded = """
				{
				  "kind": "landscape-spread-slide",
				  "logicalDirection": "next",
				  "sourcePageIndex": 16,
				  "targetPageIndex": 18,
				  "sourcePageSide": "left",
				  "targetPageSide": "left"
				}
			""".trimIndent(),
			token = "turn-16-18",
			generation = 7L
		)

		assertEquals(ReaderPageTurnTransitionKind.LandscapeSpreadSlide, plan.kind)
		assertEquals(ReaderPageTurnLogicalDirection.Next, plan.logicalDirection)
		assertEquals(18, plan.targetPageIndex)
		assertEquals(16, plan.sourcePageIndex)
		assertEquals(7L, plan.generation)
	}

	@Test
	fun parsesSimplifiedPortraitSlideWithoutCurlRoles() {
		val plan = ReaderPageTurnTransitionPlan.parse(
			encoded = """
				{
				  "kind": "portrait-slide",
				  "logicalDirection": "next",
				  "sourcePageIndex": 6,
				  "targetPageIndex": 7,
				  "sourcePageSide": "left",
				  "targetPageSide": "right"
				}
			""".trimIndent(),
			token = "portrait-6-7",
			generation = 8L
		)

		assertEquals(ReaderPageTurnTransitionKind.PortraitSlide, plan?.kind)
		assertEquals(6, plan?.sourcePageIndex)
		assertEquals(7, plan?.targetPageIndex)
	}

	@Test
	fun rejectsUnknownTransitionKind() {
		val plan = ReaderPageTurnTransitionPlan.parse(
			encoded = """{"kind":"curl","logicalDirection":"next","sourcePageIndex":6,"targetPageIndex":7,"sourcePageSide":"left","targetPageSide":"right"}""",
			token = "invalid",
			generation = 9L
		)

		assertNull(plan)
	}

	@Test
	fun slideTransitionsBorrowCacheOwnedSnapshotsWithoutCurlBitmaps() {
		val source = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val snapshot = source
			.substringAfter("internal class ReaderPageSlideSnapshot(")
			.substringBefore("internal class ReaderPageSlideTransition(")
		val transition = source
			.substringAfter("internal class ReaderPageSlideTransition(")
			.substringBefore("private fun JsonObject.requiredIndex")

		assertContains(source, "internal data class ReaderPageSlideSnapshotKey(")
		assertContains(snapshot, "val bitmap: Bitmap")
		assertContains(snapshot, "val surfaceRectInWindow: Rect")
		assertContains(snapshot, "val leafGeometry: ReaderPageTurnLeafGeometry")
		assertContains(snapshot, "fun retain()")
		assertContains(snapshot, "fun releaseCacheOwnership()")
		assertContains(transition, "val source: ReaderPageSlideSnapshot")
		assertContains(transition, "val destination: ReaderPageSlideSnapshot")
		assertContains(transition, "val leafGeometry: ReaderPageTurnLeafGeometry")
		assertContains(transition, "source.retain()")
		assertContains(transition, "destination.retain()")
		assertContains(transition, "fun close()")
		assertContains(transition, "source.release()")
		assertContains(transition, "destination.release()")
		assertFalse(transition.contains("bitmap.recycle()"))
		assertFalse(transition.contains("turningReverse"))
		assertFalse(transition.contains("underneath"))
	}
}
