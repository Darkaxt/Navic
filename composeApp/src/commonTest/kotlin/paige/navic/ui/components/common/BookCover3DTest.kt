package paige.navic.ui.components.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookCover3DTest {
	private fun assertNear(expected: Offset, actual: Offset) {
		assertTrue(abs(expected.x - actual.x) < 0.01f && abs(expected.y - actual.y) < 0.01f, "expected $expected, was $actual")
	}

	@Test
	fun thicknessFollowsPageCountWithinTheAgreedRange() {
		assertEquals(0.06f, bookThicknessFraction(64), 1e-6f)
		assertEquals(0.18f, bookThicknessFraction(300), 1e-6f)
		assertEquals(0.18f, bookThicknessFraction(null), 1e-6f)
		assertEquals(0.6f, bookThicknessFraction(1007), 1e-6f)
		assertEquals(0.6f, bookThicknessFraction(5000), 1e-6f)
	}

	@Test
	fun portraitCoversStandOnTheSlotFloorShowingThePageSide() {
		val slot = Size(300f, 390f)
		val geometry = bookCoverGeometry(slot, coverAspectRatio = 2f / 3f, pages = 1007)

		assertEquals(240f, geometry.coverSize.width, 0.01f)
		assertEquals(360f, geometry.coverSize.height, 0.01f)
		assertNotNull(geometry.right, "the page side faces the viewer")
		assertNull(geometry.top, "at a 0 degree top angle the eye is below the top")
		// Nothing leaves the slot.
		val points = listOfNotNull(geometry.front, geometry.right).flatMap {
			listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft)
		}
		assertTrue(points.all { it.x in 0f..slot.width && it.y in 0f..slot.height }, "$points")
		// Thick books have a wider page side than thin ones.
		val thin = bookCoverGeometry(slot, 2f / 3f, pages = 64)
		val thickWidth = geometry.right!!.topRight.x - geometry.right.topLeft.x
		val thinWidth = thin.right!!.topRight.x - thin.right.topLeft.x
		assertTrue(thickWidth > thinWidth * 3f, "thick $thickWidth thin $thinWidth")
	}

	@Test
	fun squareCoversStayFlat() {
		val geometry = bookCoverGeometry(Size(300f, 390f), coverAspectRatio = 1f, pages = 300)

		assertEquals(0f, geometry.thickness)
		assertNull(geometry.right)
		assertNull(geometry.top)
		assertNear(Offset(0f, 90f), geometry.front.topLeft)
		assertNear(Offset(300f, 390f), geometry.front.bottomRight)
	}

	@Test
	fun faceMatrixMapsTheTextureCornersOntoTheQuad() {
		val quad = BookFaceQuad(Offset(10f, 20f), Offset(200f, 35f), Offset(190f, 300f), Offset(12f, 320f))
		val matrix = bookFaceMatrix(Size(50f, 80f), quad)

		assertNear(quad.topLeft, matrix.map(Offset(0f, 0f)))
		assertNear(quad.topRight, matrix.map(Offset(50f, 0f)))
		assertNear(quad.bottomRight, matrix.map(Offset(50f, 80f)))
		assertNear(quad.bottomLeft, matrix.map(Offset(0f, 80f)))
	}
}
