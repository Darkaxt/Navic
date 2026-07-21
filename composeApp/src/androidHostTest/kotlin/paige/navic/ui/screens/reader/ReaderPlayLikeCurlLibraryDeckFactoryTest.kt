package paige.navic.ui.screens.reader

import karacken.curl.LandscapePageDeck
import karacken.curl.PageChange
import karacken.curl.PageImage
import karacken.curl.PortraitPageDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPlayLikeCurlLibraryDeckFactoryTest {
	@Test
	fun portraitPreparedWindowKeepsTwoPagesInEachDirection() {
		assertEquals(
			listOf(2, 3, 4, 5, 6),
			readerPlayLikeCurlPreparedPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 4,
				pageCount = 9
			)
		)
	}

	@Test
	fun landscapePreparedWindowKeepsTwoCompleteSpreadsInEachDirection() {
		assertEquals(
			(4..13).toList(),
			readerPlayLikeCurlPreparedPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 8,
				pageCount = 20
			)
		)
	}

	@Test
	fun settlementTargetUsesOnePageOrOneSpreadWithoutCrossingBoundaries() {
		assertEquals(
			5,
			readerPlayLikeCurlSettlementTargetOrdinal(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 4,
				pageCount = 9,
				pageChange = PageChange.NEXT
			)
		)
		assertEquals(
			2,
			readerPlayLikeCurlSettlementTargetOrdinal(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 4,
				pageCount = 9,
				pageChange = PageChange.PREVIOUS
			)
		)
		assertEquals(
			null,
			readerPlayLikeCurlSettlementTargetOrdinal(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 0,
				pageCount = 9,
				pageChange = PageChange.PREVIOUS
			)
		)
	}

	@Test
	fun portraitDeckWindowUsesPreviousCurrentAndNextLogicalPages() {
		assertEquals(
			listOf(3, 4, 5),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 4,
				pageCount = 9
			)
		)
	}

	@Test
	fun landscapeDeckWindowUsesThreeCompleteSpreads() {
		assertEquals(
			listOf(2, 3, 4, 5, 6, 7),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 4,
				pageCount = 10
			)
		)
	}

	@Test
	fun landscapeDeckWindowPreservesAnOddFoliateSpreadAnchor() {
		assertEquals(
			listOf(11, 12, 13, 14, 15, 16),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 13,
				pageCount = 30,
				spreadAnchorParity = 1
			)
		)
	}

	@Test
	fun deckWindowClampsAtPublicationBoundaries() {
		assertEquals(
			listOf(0, 1),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 0,
				pageCount = 2
			)
		)
		assertEquals(
			listOf(0, 1, 2, 3),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 0,
				pageCount = 4
			)
		)
	}

	@Test
	fun portraitMapsPreviousCurrentAndNextAroundTheRequestedOrdinal() {
		val deck = readerPlayLikeCurlLibraryDeck(
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			generationId = 7L,
			currentOrdinal = 3,
			pageCount = 8,
			page = ::page
		)

		val portrait = assertIs<PortraitPageDeck<String>>(deck)
		assertEquals(listOf(2, 3, 4), portrait.pages.map(PageImage<String>::getOrdinal))
		assertEquals(7L, portrait.generationId)
	}

	@Test
	fun portraitBoundariesUseFillersWithoutInventingNavigation() {
		val first = assertIs<PortraitPageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				generationId = 8L,
				currentOrdinal = 0,
				pageCount = 8
			)
		)
		val last = assertIs<PortraitPageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				generationId = 9L,
				currentOrdinal = 7,
				pageCount = 8
			)
		)

		assertTrue(first.previous.isFiller)
		assertFalse(first.canTurn(PageChange.PREVIOUS))
		assertTrue(first.canTurn(PageChange.NEXT))
		assertTrue(last.next.isFiller)
		assertTrue(last.canTurn(PageChange.PREVIOUS))
		assertFalse(last.canTurn(PageChange.NEXT))
	}

	@Test
	fun landscapeMapsThreeAdjacentSpreadsFromTheFoliateCurrentLeftLeaf() {
		val landscape = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 10L,
				currentOrdinal = 3,
				pageCount = 8,
				spreadAnchorParity = 1
			)
		)

		assertEquals(listOf(1, 2, 3, 4, 5, 6), landscape.pages.map(PageImage<String>::getOrdinal))
		assertEquals(3, landscape.currentLeft.ordinal)
		assertEquals(4, landscape.currentRight.ordinal)
	}

	@Test
	fun landscapeClampsTheFirstAndLastSpreadAtBookBoundaries() {
		val first = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 11L,
				currentOrdinal = 0,
				pageCount = 8,
				spreadAnchorParity = 1
			)
		)
		val last = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 12L,
				currentOrdinal = 7,
				pageCount = 8,
				spreadAnchorParity = 1
			)
		)

		assertEquals(
			listOf(
				"filler-Previous-0-Left",
				"filler-Previous-0-Right",
				"filler-Current-0-Left",
				"page-0",
				"page-1",
				"page-2"
			),
			first.pages.map(PageImage<String>::getLogicalPageId)
		)
		assertTrue(first.previousLeft.isFiller)
		assertTrue(first.previousRight.isFiller)
		assertTrue(first.currentLeft.isFiller)
		assertFalse(first.currentRight.isFiller)
		assertFalse(first.canTurn(PageChange.PREVIOUS))
		assertTrue(first.canTurn(PageChange.NEXT))
		assertEquals(0, first.getSettlementPage(PageChange.NONE).ordinal)
		assertEquals(1, first.getSettlementPage(PageChange.NEXT).ordinal)
		assertFalse(last.canTurn(PageChange.NEXT))
		assertEquals(7, last.getSettlementPage(PageChange.NONE).ordinal)
		assertTrue(last.nextLeft.isFiller)
		assertTrue(last.nextRight.isFiller)
	}

	@Test
	fun landscapeKeepsAnOddFinalPageReachableAsTheLastLeftLeaf() {
		val last = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 13L,
				currentOrdinal = 6,
				pageCount = 7
			)
		)

		assertEquals(
			listOf(
				"page-4",
				"page-5",
				"page-6",
				"filler-Current-6-Right",
				"filler-Next-6-Left",
				"filler-Next-6-Right"
			),
			last.pages.map(PageImage<String>::getLogicalPageId)
		)
		assertEquals(6, last.getSettlementPage(PageChange.NONE).ordinal)
		assertFalse(last.canTurn(PageChange.NEXT))
	}

	@Test
	fun parityZeroRtlUsesLogicalAnchorInsteadOfPhysicalLeftLeaf() {
		val deck = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 20L,
				currentOrdinal = 2,
				pageCount = 6,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl
			)
		)

		assertEquals(3, deck.currentLeft.ordinal)
		assertEquals(2, deck.currentRight.ordinal)
		assertSame(deck.currentRight, deck.getSettlementPage(PageChange.NONE))
		assertEquals(0, deck.getSettlementPage(PageChange.PREVIOUS).ordinal)
		assertEquals(4, deck.getSettlementPage(PageChange.NEXT).ordinal)
		assertTrue(deck.canTurn(PageChange.PREVIOUS))
		assertTrue(deck.canTurn(PageChange.NEXT))
	}

	@Test
	fun parityOnePartialBoundariesUseFillerWithoutInventingCapabilities() {
		val firstLtr = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 21L,
				currentOrdinal = 0,
				pageCount = 6,
				spreadAnchorParity = 1
			)
		)
		val lastRtl = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 22L,
				currentOrdinal = 5,
				pageCount = 6,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl,
				spreadAnchorParity = 1
			)
		)

		assertTrue(firstLtr.currentLeft.isFiller)
		assertEquals("filler-Current-0-Left", firstLtr.currentLeft.logicalPageId)
		assertFalse(firstLtr.currentRight.isFiller)
		assertFalse(firstLtr.canTurn(PageChange.PREVIOUS))
		assertTrue(firstLtr.canTurn(PageChange.NEXT))
		assertSame(firstLtr.currentRight, firstLtr.getSettlementPage(PageChange.NONE))
		assertEquals(1, firstLtr.getSettlementPage(PageChange.NEXT).ordinal)
		assertTrue(lastRtl.canTurn(PageChange.PREVIOUS))
		assertFalse(lastRtl.canTurn(PageChange.NEXT))
		assertTrue(lastRtl.currentLeft.isFiller)
		assertEquals("filler-Current-5-Left", lastRtl.currentLeft.logicalPageId)
		assertFalse(lastRtl.currentRight.isFiller)
		assertSame(lastRtl.currentRight, lastRtl.getSettlementPage(PageChange.NONE))
		assertEquals(3, lastRtl.getSettlementPage(PageChange.PREVIOUS).ordinal)
	}

	private fun deck(
		orientation: ReaderPlayLikeCurlOrientation,
		generationId: Long,
		currentOrdinal: Int,
		pageCount: Int,
		readerDirection: ReaderPlayLikeCurlReaderDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
		spreadAnchorParity: Int = 0
	) = readerPlayLikeCurlLibraryDeck(
		orientation = orientation,
		generationId = generationId,
		currentOrdinal = currentOrdinal,
		pageCount = pageCount,
		readerDirection = readerDirection,
		spreadAnchorParity = spreadAnchorParity,
		filler = ::fillerPage,
		page = ::page
	)

	private fun page(generationId: Long, ordinal: Int): PageImage<String> = PageImage(
		generationId,
		"page-$ordinal",
		ordinal,
		120,
		180,
		"content-$ordinal"
	)

	private fun fillerPage(
		generationId: Long,
		deckSlotRole: ReaderPlayLikeCurlDeckSlotRole,
		sourcePageIndex: Int,
		physicalLeaf: ReaderPlayLikeCurlPhysicalLeaf,
		fallbackOrdinal: Int
	): PageImage<String> = PageImage.filler(
		generationId,
		"filler-${deckSlotRole.name}-$sourcePageIndex-${physicalLeaf.name}",
		fallbackOrdinal,
		120,
		180,
		"borrowed-content-$fallbackOrdinal",
		0xFFF5F2EA.toInt()
	)
}
