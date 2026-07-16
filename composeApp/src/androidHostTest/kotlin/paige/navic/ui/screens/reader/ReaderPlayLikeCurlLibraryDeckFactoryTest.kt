package paige.navic.ui.screens.reader

import karacken.curl.LandscapePageDeck
import karacken.curl.PageImage
import karacken.curl.PortraitPageDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderPlayLikeCurlLibraryDeckFactoryTest {
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
	fun portraitDuplicatesTheBoundaryPageWithoutInventingAnInvalidOrdinal() {
		val first = assertIs<PortraitPageDeck<String>>(
			readerPlayLikeCurlLibraryDeck(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				generationId = 8L,
				currentOrdinal = 0,
				pageCount = 8,
				page = ::page
			)
		)
		val last = assertIs<PortraitPageDeck<String>>(
			readerPlayLikeCurlLibraryDeck(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				generationId = 9L,
				currentOrdinal = 7,
				pageCount = 8,
				page = ::page
			)
		)

		assertEquals(listOf(0, 0, 1), first.pages.map(PageImage<String>::getOrdinal))
		assertEquals(listOf(6, 7, 7), last.pages.map(PageImage<String>::getOrdinal))
	}

	@Test
	fun landscapeMapsThreeAdjacentSpreadsAndKeepsCurrentLeftEven() {
		val deck = readerPlayLikeCurlLibraryDeck(
			orientation = ReaderPlayLikeCurlOrientation.Landscape,
			generationId = 10L,
			currentOrdinal = 3,
			pageCount = 8,
			page = ::page
		)

		val landscape = assertIs<LandscapePageDeck<String>>(deck)
		assertEquals(listOf(0, 1, 2, 3, 4, 5), landscape.pages.map(PageImage<String>::getOrdinal))
		assertEquals(2, landscape.currentLeft.ordinal)
		assertEquals(3, landscape.currentRight.ordinal)
	}

	@Test
	fun landscapeClampsTheFirstAndLastSpreadAtBookBoundaries() {
		val first = assertIs<LandscapePageDeck<String>>(
			readerPlayLikeCurlLibraryDeck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 11L,
				currentOrdinal = 0,
				pageCount = 8,
				page = ::page
			)
		)
		val last = assertIs<LandscapePageDeck<String>>(
			readerPlayLikeCurlLibraryDeck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 12L,
				currentOrdinal = 7,
				pageCount = 8,
				page = ::page
			)
		)

		assertEquals(listOf(0, 0, 0, 1, 2, 3), first.pages.map(PageImage<String>::getOrdinal))
		assertEquals(listOf(4, 5, 6, 7, 7, 7), last.pages.map(PageImage<String>::getOrdinal))
	}

	@Test
	fun landscapeKeepsAnOddFinalPageReachableAsTheLastLeftLeaf() {
		val last = assertIs<LandscapePageDeck<String>>(
			readerPlayLikeCurlLibraryDeck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 13L,
				currentOrdinal = 6,
				pageCount = 7,
				page = ::page
			)
		)

		assertEquals(listOf(4, 5, 6, 6, 6, 6), last.pages.map(PageImage<String>::getOrdinal))
		assertEquals(6, last.currentLeft.ordinal)
		assertEquals(6, last.currentRight.ordinal)
	}

	private fun page(generationId: Long, ordinal: Int): PageImage<String> = PageImage(
		generationId,
		"page-$ordinal",
		ordinal,
		120,
		180,
		"content-$ordinal"
	)
}
