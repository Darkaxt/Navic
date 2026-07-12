package paige.navic.reader

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderPageTurnEdgeFoldGeometryTest {
	@Test
	fun middleEdgeGrabFoldsOneRigidSheetWithoutCornerSnapping() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.28f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 800f,
			pointerY = 800f
		)

		val grabbedEdge = fold.map(ReaderPageTurnPoint(1000f, 800f))
		val topEdge = fold.map(ReaderPageTurnPoint(1000f, 0f))
		val bottomEdge = fold.map(ReaderPageTurnPoint(1000f, 1600f))

		assertTrue(grabbedEdge.x < 730f, "The grabbed edge must follow the horizontal drag.")
		assertTrue(abs((bottomEdge.y - topEdge.y) - 1600f) < 0.01f, "A horizontal drag must preserve sheet height.")
		assertTrue(abs(topEdge.x - grabbedEdge.x) < 0.01f)
		assertTrue(abs(bottomEdge.x - grabbedEdge.x) < 0.01f)
	}

	@Test
	fun foldedRegionPreservesDistancesLikeAPlanarSheet() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.35f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 500f,
			pointerY = 720f
		)
		val first = ReaderPageTurnPoint(940f, 430f)
		val second = ReaderPageTurnPoint(960f, 520f)
		val firstMapped = fold.map(first)
		val secondMapped = fold.map(second)
		val before = squaredDistance(first, second)
		val after = squaredDistance(firstMapped, secondMapped)

		assertTrue(abs(before - after) < 0.1f)
	}

	@Test
	fun finiteCurlBandSmoothsTheCreaseWithoutMovingTheGrabbedEdge() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.5f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 800f,
			pointerY = 800f
		)

		val grabbedEdge = fold.map(ReaderPageTurnPoint(1000f, 800f))
		val beforeCrease = fold.map(ReaderPageTurnPoint(749f, 800f))
		val afterCrease = fold.map(ReaderPageTurnPoint(751f, 800f))
		val crease = fold.map(ReaderPageTurnPoint(750f, 800f))

		assertTrue(abs(grabbedEdge.x - 500f) < 0.01f, "The finger contact must remain exact outside the curl band.")
		assertTrue(abs(afterCrease.x - beforeCrease.x) < 5f, "The curl band must be continuous across the crease.")
		assertTrue(abs(crease.x - 750f) > 10f, "The crease neighborhood must bend instead of reflecting as a hard hinge.")
	}

	@Test
	fun edgeOriginIsNotSnappedAndVerticalPointerMotionIsPreserved() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.32f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 530f,
			pointerY = 690f
		)

		val grabbedPoint = fold.map(ReaderPageTurnPoint(1000f, 530f))

		assertTrue(abs(grabbedPoint.y - 690f) < 1f, "The source edge point must follow the live pointer Y.")
		assertTrue(fold.foldBoundaryX(0f) != fold.foldBoundaryX(1600f))
		val crease = fold.foldBoundarySegment()
		assertTrue(crease != null)
		assertTrue(crease.first.x in 0f..1000f && crease.first.y in 0f..1600f)
		assertTrue(crease.second.x in 0f..1000f && crease.second.y in 0f..1600f)
		assertTrue(fold.foldedRegionOutline().size >= 3)
	}

	@Test
	fun bindingEdgeRemainsFixedInBothDirections() {
		for (direction in ReaderPageTurnPhysicalDirection.entries) {
			val fold = ReaderPageTurnEdgeFoldGeometry(
				width = 1000f,
				height = 1600f,
				progress = 0.65f,
				direction = direction,
				edgeOriginY = 920f,
				pointerY = 760f
			)
			val bindingX = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) 0f else 1000f
			for (y in listOf(0f, 400f, 920f, 1600f)) {
				assertEquals(ReaderPageTurnPoint(bindingX, y), fold.map(ReaderPageTurnPoint(bindingX, y)))
			}
		}
	}

	@Test
	fun previousAndNextFoldsAreHorizontalMirrors() {
		val left = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.42f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 610f,
			pointerY = 740f
		)
		val right = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.42f,
			direction = ReaderPageTurnPhysicalDirection.TowardRight,
			edgeOriginY = 610f,
			pointerY = 740f
		)

		val leftMapped = left.map(ReaderPageTurnPoint(920f, 610f))
		val rightMapped = right.map(ReaderPageTurnPoint(80f, 610f))

		assertTrue(abs(leftMapped.x - (1000f - rightMapped.x)) < 0.01f)
		assertTrue(abs(leftMapped.y - rightMapped.y) < 0.01f)
	}

	@Test
	fun onePageWidthOfTravelBringsTheGrabbedEdgeToTheBinding() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 1f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 300f,
			pointerY = 300f
		)

		assertTrue(abs(fold.map(ReaderPageTurnPoint(1000f, 300f)).x) < 0.01f)
	}

	@Test
	fun twoPageWidthsOfTravelLayTheWholeSheetAcrossTheOppositePage() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 2f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 300f,
			pointerY = 300f
		)

		assertTrue(abs(fold.map(ReaderPageTurnPoint(1000f, 300f)).x + 1000f) < 0.01f)
		assertTrue(fold.map(ReaderPageTurnPoint(1000f, 0f)).x <= -999f)
		assertTrue(fold.map(ReaderPageTurnPoint(1000f, 1600f)).x <= -999f)
	}

	@Test
	fun reverseFaceKeepsDestinationReadingOrderWhileFollowingTheFoldPlane() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.62f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 260f,
			pointerY = 720f
		)

		val first = fold.mapReverseFace(ReaderPageTurnPoint(40f, 500f))
		val right = fold.mapReverseFace(ReaderPageTurnPoint(140f, 500f))
		val down = fold.mapReverseFace(ReaderPageTurnPoint(40f, 600f))
		val rightX = right.x - first.x
		val rightY = right.y - first.y
		val downX = down.x - first.x
		val downY = down.y - first.y
		val orientation = rightX * downY - rightY * downX

		assertTrue(orientation > 0f, "Destination glyph orientation must be preserved on the reverse face.")
		assertTrue(abs(rightY) > 1f, "Destination baselines must rotate with a slanted fold instead of staying horizontal.")
	}

	@Test
	fun reverseFaceNeverLeavesTheUnfoldedHalfOfItsBitmapFlat() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.62f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 260f,
			pointerY = 720f
		)
		val destinationPixel = ReaderPageTurnPoint(900f, 500f)
		val unreflectedSheetPixel = ReaderPageTurnPoint(100f, 500f)

		val mapped = fold.mapReverseFace(destinationPixel)

		assertTrue(
			squaredDistance(mapped, unreflectedSheetPixel) > 1f,
			"Every reverse-face pixel must be transformed by the fold; flat identity pixels leak straight text into the fold."
		)
	}

	@Test
	fun reverseFaceVisibilityExcludesDestinationPixelsOutsideTheFoldedSheetHalf() {
		val fold = ReaderPageTurnEdgeFoldGeometry(
			width = 1000f,
			height = 1600f,
			progress = 0.62f,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			edgeOriginY = 260f,
			pointerY = 720f
		)

		assertTrue(fold.isReverseFacePixelVisible(40f, 500f))
		assertTrue(!fold.isReverseFacePixelVisible(900f, 500f))
	}

	private fun squaredDistance(first: ReaderPageTurnPoint, second: ReaderPageTurnPoint): Float {
		val dx = first.x - second.x
		val dy = first.y - second.y
		return dx * dx + dy * dy
	}
}
