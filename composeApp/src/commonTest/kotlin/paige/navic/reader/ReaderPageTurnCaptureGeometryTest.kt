package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageTurnCaptureGeometryTest {
	private val spread = ReaderPageTurnCaptureGeometry(
		viewportWidth = 1200.0,
		viewportHeight = 800.0,
		mode = ReaderPageTurnLayoutMode.Spread,
		pages = listOf(
			ReaderPageTurnPageRect(ReaderPageTurnPageRole.Left, 12.0, 0.0, 588.0, 800.0),
			ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 600.0, 0.0, 588.0, 800.0)
		)
	)

	@Test
	fun leftwardDragSelectsPhysicalRightPage() {
		assertEquals(ReaderPageTurnPageRole.Right, spread.pageFor(ReaderPageTurnPhysicalDirection.TowardLeft)?.role)
	}

	@Test
	fun rightwardDragSelectsPhysicalLeftPage() {
		assertEquals(ReaderPageTurnPageRole.Left, spread.pageFor(ReaderPageTurnPhysicalDirection.TowardRight)?.role)
	}

	@Test
	fun cssPageRectConvertsOnceToClippedWindowPixels() {
		assertEquals(
			ReaderPageTurnPixelRect(left = 920, top = 40, right = 1802, bottom = 1240),
			spread.sourceRectInWindow(
				direction = ReaderPageTurnPhysicalDirection.TowardLeft,
				webViewWindowLeft = 20,
				webViewWindowTop = 40,
				webViewWidth = 1800,
				webViewHeight = 1200
			)
		)
	}

	@Test
	fun sourceRectIsClippedToWebViewBounds() {
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 100.0,
			viewportHeight = 100.0,
			mode = ReaderPageTurnLayoutMode.Single,
			pages = listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, -5.0, -2.0, 110.0, 105.0))
		)
		assertEquals(
			ReaderPageTurnPixelRect(10, 20, 210, 220),
			geometry.sourceRectInWindow(
				direction = ReaderPageTurnPhysicalDirection.TowardLeft,
				webViewWindowLeft = 10,
				webViewWindowTop = 20,
				webViewWidth = 200,
				webViewHeight = 200
			)
		)
	}

	@Test
	fun spreadSurfaceRectUnionsBothPhysicalPages() {
		assertEquals(
			ReaderPageTurnPixelRect(left = 38, top = 40, right = 1802, bottom = 1240),
			spread.surfaceRectInWindow(
				webViewWindowLeft = 20,
				webViewWindowTop = 40,
				webViewWidth = 1800,
				webViewHeight = 1200
			)
		)
	}

	@Test
	fun physicalPageRoleConvertsWithinTheSameWindowCoordinateSystem() {
		assertEquals(
			ReaderPageTurnPixelRect(left = 38, top = 40, right = 920, bottom = 1240),
			spread.pageRectInWindow(
				role = ReaderPageTurnPageRole.Left,
				webViewWindowLeft = 20,
				webViewWindowTop = 40,
				webViewWidth = 1800,
				webViewHeight = 1200
			)
		)
	}

	@Test
	fun spreadLeafGeometryUsesResolvedPageSplitInsideAnimationBitmap() {
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 1200.0,
			viewportHeight = 800.0,
			mode = ReaderPageTurnLayoutMode.Spread,
			pages = listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Left, 12.0, 0.0, 570.0, 800.0),
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 618.0, 0.0, 570.0, 800.0)
			)
		)

		assertEquals(
			ReaderPageTurnLeafGeometry(
				fullLeafRect = null,
				leftLeafRect = ReaderPageTurnPixelRect(0, 0, 285, 400),
				gutterRect = ReaderPageTurnPixelRect(285, 0, 303, 400),
				rightLeafRect = ReaderPageTurnPixelRect(303, 0, 588, 400)
			),
			geometry.leafGeometry(bitmapWidth = 588, bitmapHeight = 400)
		)
	}

	@Test
	fun portraitLeafGeometryUsesTheResolvedFullPageWithoutInventingAGutter() {
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 600.0,
			viewportHeight = 800.0,
			mode = ReaderPageTurnLayoutMode.Single,
			pages = listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 30.0, 0.0, 540.0, 800.0)
			)
		)

		assertEquals(
			ReaderPageTurnLeafGeometry(
				fullLeafRect = ReaderPageTurnPixelRect(0, 0, 270, 400),
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null
			),
			geometry.leafGeometry(bitmapWidth = 270, bitmapHeight = 400)
		)
	}

	@Test
	fun terminalSpreadKeepsOnlyTheResolvedPhysicalLeaf() {
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 1200.0,
			viewportHeight = 800.0,
			mode = ReaderPageTurnLayoutMode.Spread,
			pages = listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 618.0, 0.0, 570.0, 800.0)
			)
		)

		assertEquals(
			ReaderPageTurnLeafGeometry(
				fullLeafRect = null,
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = ReaderPageTurnPixelRect(0, 0, 285, 400)
			),
			geometry.leafGeometry(bitmapWidth = 285, bitmapHeight = 400)
		)
	}
}
