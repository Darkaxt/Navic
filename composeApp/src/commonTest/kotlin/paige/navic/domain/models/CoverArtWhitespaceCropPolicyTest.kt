package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverArtWhitespaceCropPolicyTest {
	@Test
	fun cropsEdgeConnectedWhiteMargins() {
		val crop = coverArtWhitespaceCropBounds(
			width = 10,
			height = 10
		) { x, y ->
			if (x in 2..7 && y in 3..8) CoverArtPixel(32, 64, 96, 255) else CoverArtPixel.White
		}

		assertEquals(CoverArtCropBounds(left = 2, top = 3, rightExclusive = 8, bottomExclusive = 9), crop)
	}

	@Test
	fun cropsTransparentMargins() {
		val crop = coverArtWhitespaceCropBounds(
			width = 12,
			height = 8
		) { x, y ->
			if (x in 1..10 && y in 2..5) CoverArtPixel(180, 60, 90, 255) else CoverArtPixel.Transparent
		}

		assertEquals(CoverArtCropBounds(left = 1, top = 2, rightExclusive = 11, bottomExclusive = 6), crop)
	}

	@Test
	fun preservesInternalWhiteAreas() {
		val crop = coverArtWhitespaceCropBounds(
			width = 8,
			height = 8
		) { x, y ->
			when {
				x == 0 || y == 0 || x == 7 || y == 7 -> CoverArtPixel.White
				x in 3..4 && y in 3..4 -> CoverArtPixel.White
				else -> CoverArtPixel(10, 20, 30, 255)
			}
		}

		assertEquals(CoverArtCropBounds(left = 1, top = 1, rightExclusive = 7, bottomExclusive = 7), crop)
	}

	@Test
	fun preservesColoredDetailsInsideOtherwiseWhiteMargins() {
		val crop = coverArtWhitespaceCropBounds(
			width = 12,
			height = 12
		) { x, y ->
			when {
				x == 1 && y == 3 -> CoverArtPixel(210, 40, 120, 255)
				x in 4..9 && y in 4..9 -> CoverArtPixel(40, 60, 80, 255)
				else -> CoverArtPixel.White
			}
		}

		assertEquals(CoverArtCropBounds(left = 1, top = 3, rightExclusive = 10, bottomExclusive = 10), crop)
	}

	@Test
	fun skipsCropWhenOnlyTinyBorderWouldBeRemoved() {
		val crop = coverArtWhitespaceCropBounds(
			width = 100,
			height = 100
		) { x, y ->
			if (x == 0 || y == 0) CoverArtPixel.White else CoverArtPixel(10, 20, 30, 255)
		}

		assertNull(crop)
	}

	@Test
	fun skipsCropWhenContentBoxWouldBeTooSmall() {
		val crop = coverArtWhitespaceCropBounds(
			width = 20,
			height = 20
		) { x, y ->
			if (x in 9..10 && y in 9..10) CoverArtPixel(10, 20, 30, 255) else CoverArtPixel.White
		}

		assertNull(crop)
	}
}
