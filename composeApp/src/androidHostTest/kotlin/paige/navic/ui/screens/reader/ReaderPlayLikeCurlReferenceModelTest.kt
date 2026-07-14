package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPlayLikeCurlReferenceModelTest {
	@Test
	fun persistentPagesKeepReferenceDrawOrderDepthAndBoundaryImages() {
		val model = ReaderPlayLikeCurlReferenceModel(pageCount = 4)
		val left = model.leftPage
		val front = model.frontPage
		val right = model.rightPage

		assertEquals(
			listOf(
				ReaderPlayLikeCurlPageRole.Left,
				ReaderPlayLikeCurlPageRole.Front,
				ReaderPlayLikeCurlPageRole.Right
			),
			model.drawOrder.map(ReaderPlayLikeCurlPageState::role)
		)
		assertEquals(-0.001f, left.depth)
		assertEquals(-0.002f, front.depth)
		assertEquals(-0.003f, right.depth)
		assertEquals(listOf(0, 0, 1), model.drawOrder.map(ReaderPlayLikeCurlPageState::pageIndex))
		assertEquals(ReaderPlayLikeCurlActivePage.Current, model.activePage)
		assertEquals(ReaderPlayLikeCurlReferenceModel.RightEndpointPosition, left.curlPosition)
		assertEquals(ReaderPlayLikeCurlReferenceModel.Grid.toFloat(), front.curlPosition)
		assertEquals(ReaderPlayLikeCurlReferenceModel.Grid.toFloat(), right.curlPosition)

		model.jumpTo(3)

		assertSame(left, model.leftPage)
		assertSame(front, model.frontPage)
		assertSame(right, model.rightPage)
		assertEquals(listOf(2, 3, 3), model.drawOrder.map(ReaderPlayLikeCurlPageState::pageIndex))
	}

	@Test
	fun forwardDragAndFlingCommitOnlyAfterReferenceSettlementCompletes() {
		val model = ReaderPlayLikeCurlReferenceModel(pageCount = 4)

		model.beginGesture(x = 100f)
		model.dragTo(x = 50f, width = 100f)

		assertEquals(ReaderPlayLikeCurlActivePage.Current, model.activePage)
		assertEquals(12.5f, model.frontPage.curlPosition)

		val settlement = model.flingTowardNext()
		assertEquals(-5, settlement.targetPercent)
		assertEquals(300L, settlement.durationMillis)
		assertEquals(ReaderPlayLikeCurlInterpolator.Decelerate, settlement.interpolator)
		assertEquals(ReaderPlayLikeCurlPageChange.Next, settlement.pageChange)
		assertEquals(0, model.currentPosition)

		model.completeSettlement(settlement)

		assertEquals(1, model.currentPosition)
		assertEquals(listOf(0, 1, 2), model.drawOrder.map(ReaderPlayLikeCurlPageState::pageIndex))
		assertEquals(ReaderPlayLikeCurlActivePage.Current, model.activePage)
		assertEquals(ReaderPlayLikeCurlReferenceModel.Grid.toFloat(), model.frontPage.curlPosition)
	}

	@Test
	fun backwardDragUsesLeftPageAndReleaseWithoutFlingDoesNotNavigate() {
		val model = ReaderPlayLikeCurlReferenceModel(pageCount = 4, initialPosition = 2)

		model.beginGesture(x = 0f)
		model.dragTo(x = 50f, width = 100f)

		assertEquals(ReaderPlayLikeCurlActivePage.Left, model.activePage)
		assertEquals(11.25f, model.leftPage.curlPosition)

		val settlement = model.release()
		assertEquals(-5, settlement.targetPercent)
		assertEquals(ReaderPlayLikeCurlInterpolator.AccelerateDecelerate, settlement.interpolator)
		assertEquals(ReaderPlayLikeCurlPageChange.None, settlement.pageChange)

		model.completeSettlement(settlement)

		assertEquals(2, model.currentPosition)
		assertEquals(ReaderPlayLikeCurlActivePage.Current, model.activePage)
	}

	@Test
	fun backwardFlingRotatesPageIdentitiesOnlyAfterSettlement() {
		val model = ReaderPlayLikeCurlReferenceModel(pageCount = 4, initialPosition = 2)
		model.beginGesture(x = 0f)
		model.dragTo(x = 50f, width = 100f)

		val settlement = model.flingTowardPrevious()

		assertEquals(100, settlement.targetPercent)
		assertEquals(ReaderPlayLikeCurlPageChange.Previous, settlement.pageChange)
		assertEquals(2, model.currentPosition)
		assertEquals(listOf(1, 2, 3), model.drawOrder.map(ReaderPlayLikeCurlPageState::pageIndex))

		model.completeSettlement(settlement)

		assertEquals(1, model.currentPosition)
		assertEquals(listOf(0, 1, 2), model.drawOrder.map(ReaderPlayLikeCurlPageState::pageIndex))
	}

	@Test
	fun referenceGeometryPreservesBitmapAspectCorrectionAndRoleSpecificDeformation() {
		val portrait = ReaderPlayLikeCurlReferenceGeometry.createPage(
			role = ReaderPlayLikeCurlPageRole.Front,
			bitmapWidth = 1000,
			bitmapHeight = 1500,
			orientation = ReaderPlayLikeCurlOrientation.Portrait
		)
		val landscape = ReaderPlayLikeCurlReferenceGeometry.createPage(
			role = ReaderPlayLikeCurlPageRole.Left,
			bitmapWidth = 1600,
			bitmapHeight = 1000,
			orientation = ReaderPlayLikeCurlOrientation.Landscape
		)

		assertEquals(-0.25f, portrait.positionY(column = 0, row = 0), absoluteTolerance = Tolerance)
		assertEquals(1.25f, portrait.positionY(column = 0, row = ReaderPlayLikeCurlReferenceModel.Grid), absoluteTolerance = Tolerance)
		assertEquals(-0.3f, landscape.positionY(column = 0, row = 0), absoluteTolerance = Tolerance)
		assertEquals(1.3f, landscape.positionY(column = 0, row = ReaderPlayLikeCurlReferenceModel.Grid), absoluteTolerance = Tolerance)

		ReaderPlayLikeCurlReferenceGeometry.update(
			page = portrait,
			curlPosition = 12.5f,
			active = true
		)
		ReaderPlayLikeCurlReferenceGeometry.update(
			page = landscape,
			curlPosition = 12.5f,
			active = true
		)

		assertFalse(portrait.positions.contentEquals(landscape.positions))
		assertTrue(portrait.positions.all(Float::isFinite))
		assertTrue(landscape.positions.all(Float::isFinite))
	}

	@Test
	fun projectionAspectMatchesTheReferenceOrientationRule() {
		assertEquals(
			0.5f,
			ReaderPlayLikeCurlReferenceGeometry.projectionAspect(width = 1000, height = 2000)
		)
		assertEquals(
			0.5f,
			ReaderPlayLikeCurlReferenceGeometry.projectionAspect(width = 2000, height = 1000)
		)
	}

	private companion object {
		const val Tolerance = 0.0001f
	}
}
