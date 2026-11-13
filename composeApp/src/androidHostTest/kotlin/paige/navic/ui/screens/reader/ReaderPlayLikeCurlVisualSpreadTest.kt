package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPlayLikeCurlVisualSpreadTest {
	@Test
	fun eachFoliateOrdinalIsOneWholeSpreadWithMirroredPhysicalLeaves() {
		val ltr = readerPlayLikeCurlVisualSpread(
			sourcePageIndex = 10,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr
		)
		val rtl = readerPlayLikeCurlVisualSpread(
			sourcePageIndex = 10,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl
		)

		assertEquals(10, ltr.sourcePageIndex)
		assertEquals(10, rtl.sourcePageIndex)
		assertEquals(listOf(20, 21), ltr.logicalOrdinals)
		assertEquals(listOf(20, 21), rtl.logicalOrdinals)
		assertEquals(20, ltr.physicalLeftOrdinal)
		assertEquals(21, ltr.physicalRightOrdinal)
		assertEquals(21, rtl.physicalLeftOrdinal)
		assertEquals(20, rtl.physicalRightOrdinal)
	}

	@Test
	fun leafOrdinalsResolveBackToTheirOwningSpreadSnapshot() {
		listOf(
			ReaderPlayLikeCurlReaderDirection.Ltr,
			ReaderPlayLikeCurlReaderDirection.Rtl
		).forEach { direction ->
			(0 until 6).forEach { spread ->
				val visual = readerPlayLikeCurlVisualSpread(spread, direction)
				ReaderPlayLikeCurlPhysicalLeaf.values().forEach { leaf ->
					val leafOrdinal = visual.leafOrdinal(leaf)
					assertEquals(
						ReaderPlayLikeCurlSpreadSlot(spread, leaf),
						readerPlayLikeCurlSpreadSlot(
							logicalOrdinal = leafOrdinal,
							pageCount = 6,
							readerDirection = direction
						)
					)
					assertEquals(
						spread,
						readerPlayLikeCurlFoliateOrdinal(
							ReaderPlayLikeCurlOrientation.Landscape,
							leafOrdinal
						)
					)
				}
				assertEquals(
					visual.logicalOrdinals.first(),
					readerPlayLikeCurlAnchorLeafOrdinal(
						ReaderPlayLikeCurlOrientation.Landscape,
						spread
					)
				)
			}
		}
		assertEquals(
			7,
			readerPlayLikeCurlFoliateOrdinal(ReaderPlayLikeCurlOrientation.Portrait, 7)
		)
		assertEquals(
			7,
			readerPlayLikeCurlAnchorLeafOrdinal(ReaderPlayLikeCurlOrientation.Portrait, 7)
		)
	}

	@Test
	fun windowStepsOneWholeSpreadAndStopsAtBookBoundaries() {
		val middle = readerPlayLikeCurlVisualSpreadWindow(
			currentOrdinal = 10,
			pageCount = 30,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr
		)
		val first = readerPlayLikeCurlVisualSpreadWindow(
			currentOrdinal = 0,
			pageCount = 30,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr
		)
		val last = readerPlayLikeCurlVisualSpreadWindow(
			currentOrdinal = 29,
			pageCount = 30,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl
		)

		assertEquals(9, middle.previous?.sourcePageIndex)
		assertEquals(10, middle.current.sourcePageIndex)
		assertEquals(11, middle.next?.sourcePageIndex)
		assertNull(first.previous)
		assertEquals(1, first.next?.sourcePageIndex)
		assertEquals(28, last.previous?.sourcePageIndex)
		assertNull(last.next)
	}
}
