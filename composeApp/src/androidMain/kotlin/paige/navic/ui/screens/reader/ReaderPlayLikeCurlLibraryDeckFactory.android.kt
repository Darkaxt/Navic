package paige.navic.ui.screens.reader

import karacken.curl.LandscapePageDeck
import karacken.curl.PageDeck
import karacken.curl.PageImage
import karacken.curl.PortraitPageDeck

internal fun readerPlayLikeCurlLibraryDeckPageIndices(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int
): List<Int> {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	return if (orientation == ReaderPlayLikeCurlOrientation.Landscape) {
		val currentLeft = currentOrdinal.coerceIn(0, pageCount - 1)
		(currentLeft - 2..currentLeft + 3)
			.map { ordinal -> ordinal.coerceIn(0, pageCount - 1) }
			.distinct()
	} else {
		val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
		listOf(boundedCurrent - 1, boundedCurrent, boundedCurrent + 1)
			.map { ordinal -> ordinal.coerceIn(0, pageCount - 1) }
			.distinct()
	}
}

internal fun <T : Any> readerPlayLikeCurlLibraryDeck(
	orientation: ReaderPlayLikeCurlOrientation,
	generationId: Long,
	currentOrdinal: Int,
	pageCount: Int,
	page: (generationId: Long, ordinal: Int) -> PageImage<T>
): PageDeck<T> {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	fun boundedPage(ordinal: Int): PageImage<T> =
		page(generationId, ordinal.coerceIn(0, pageCount - 1))

	if (orientation == ReaderPlayLikeCurlOrientation.Landscape) {
		val currentLeft = currentOrdinal.coerceIn(0, pageCount - 1)
		return LandscapePageDeck(
			boundedPage(currentLeft - 2),
			boundedPage(currentLeft - 1),
			boundedPage(currentLeft),
			boundedPage(currentLeft + 1),
			boundedPage(currentLeft + 2),
			boundedPage(currentLeft + 3)
		)
	}

	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	return PortraitPageDeck(
		boundedPage(boundedCurrent - 1),
		boundedPage(boundedCurrent),
		boundedPage(boundedCurrent + 1)
	)
}
