package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPlayLikeCurlVisualSpreadTest {
	@Test
	fun parityZeroKeepsLogicalSpreadsAndMirrorsOnlyPhysicalLeaves() {
		val ltr = readerPlayLikeCurlVisualSpreads(
			pageCount = 6,
			spreadAnchorParity = 0,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr
		)
		val rtl = readerPlayLikeCurlVisualSpreads(
			pageCount = 6,
			spreadAnchorParity = 0,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl
		)

		assertEquals(
			listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)),
			ltr.map(ReaderPlayLikeCurlVisualSpread::logicalOrdinals)
		)
		assertEquals(
			ltr.map(ReaderPlayLikeCurlVisualSpread::logicalOrdinals),
			rtl.map(ReaderPlayLikeCurlVisualSpread::logicalOrdinals)
		)
		assertEquals(2, ltr[1].physicalLeftOrdinal)
		assertEquals(3, ltr[1].physicalRightOrdinal)
		assertEquals(3, rtl[1].physicalLeftOrdinal)
		assertEquals(2, rtl[1].physicalRightOrdinal)
	}

	@Test
	fun parityOnePreservesLeadingAndTrailingPartialsInBothDirections() {
		val ltr = readerPlayLikeCurlVisualSpreads(
			pageCount = 6,
			spreadAnchorParity = 1,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr
		)
		val rtl = readerPlayLikeCurlVisualSpreads(
			pageCount = 6,
			spreadAnchorParity = 1,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl
		)

		val expected = listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5))
		assertEquals(expected, ltr.map(ReaderPlayLikeCurlVisualSpread::logicalOrdinals))
		assertEquals(expected, rtl.map(ReaderPlayLikeCurlVisualSpread::logicalOrdinals))
		assertNull(ltr.first().physicalLeftOrdinal)
		assertEquals(0, ltr.first().physicalRightOrdinal)
		assertEquals(5, ltr.last().physicalLeftOrdinal)
		assertNull(ltr.last().physicalRightOrdinal)
		assertEquals(0, rtl.first().physicalLeftOrdinal)
		assertNull(rtl.first().physicalRightOrdinal)
		assertNull(rtl.last().physicalLeftOrdinal)
		assertEquals(5, rtl.last().physicalRightOrdinal)
	}
}
