package paige.navic.ui.screens.reader

import karacken.curl.LandscapePageDeck
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PageImage
import karacken.curl.PortraitPageDeck

internal fun readerPlayLikeCurlLibraryDeckPageIndices(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection =
		ReaderPlayLikeCurlReaderDirection.Ltr,
	spreadAnchorParity: Int = 0
): List<Int> {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		return listOf(
			boundedCurrent - 1,
			boundedCurrent,
			boundedCurrent + 1
		)
			.map { ordinal -> ordinal.coerceIn(0, pageCount - 1) }
			.distinct()
	}
	val window = readerPlayLikeCurlVisualSpreadWindow(
		boundedCurrent,
		pageCount,
		spreadAnchorParity,
		readerDirection
	)
	return listOfNotNull(window.previous, window.current, window.next)
		.flatMap(ReaderPlayLikeCurlVisualSpread::logicalOrdinals)
		.distinct()
}

internal fun readerPlayLikeCurlPreparedPageIndices(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection =
		ReaderPlayLikeCurlReaderDirection.Ltr,
	spreadAnchorParity: Int = 0
): List<Int> {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		return (boundedCurrent - 2..boundedCurrent + 2)
			.map { ordinal -> ordinal.coerceIn(0, pageCount - 1) }
			.distinct()
	}
	val spreads = readerPlayLikeCurlVisualSpreads(
		pageCount,
		spreadAnchorParity,
		readerDirection
	)
	val currentIndex = spreads.indexOfFirst { spread ->
		boundedCurrent in spread.logicalOrdinals
	}
	check(currentIndex >= 0) { "Current ordinal has no visual spread" }
	return (currentIndex - 2..currentIndex + 2)
		.mapNotNull(spreads::getOrNull)
		.flatMap(ReaderPlayLikeCurlVisualSpread::logicalOrdinals)
		.distinct()
}

internal fun readerPlayLikeCurlSettlementTargetOrdinal(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int,
	pageChange: PageChange,
	readerDirection: ReaderPlayLikeCurlReaderDirection =
		ReaderPlayLikeCurlReaderDirection.Ltr,
	spreadAnchorParity: Int = 0
): Int? {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		val target = when (pageChange) {
			PageChange.PREVIOUS -> boundedCurrent - 1
			PageChange.NEXT -> boundedCurrent + 1
			PageChange.NONE -> return null
		}
		return target.takeIf { ordinal -> ordinal in 0 until pageCount }
	}
	val window = readerPlayLikeCurlVisualSpreadWindow(
		boundedCurrent,
		pageCount,
		spreadAnchorParity,
		readerDirection
	)
	return when (pageChange) {
		PageChange.PREVIOUS -> window.previous?.logicalOrdinals?.first()
		PageChange.NEXT -> window.next?.logicalOrdinals?.first()
		PageChange.NONE -> null
	}
}

internal fun <T : Any> readerPlayLikeCurlLibraryDeck(
	orientation: ReaderPlayLikeCurlOrientation,
	generationId: Long,
	currentOrdinal: Int,
	pageCount: Int,
	page: (generationId: Long, ordinal: Int) -> PageImage<T>
): PageDeck<T> {
	require(
		orientation == ReaderPlayLikeCurlOrientation.Portrait &&
			currentOrdinal in 1 until (pageCount - 1)
	) { "Boundary or landscape decks require an explicit filler resource" }
	return readerPlayLikeCurlLibraryDeck(
		orientation = orientation,
		generationId = generationId,
		currentOrdinal = currentOrdinal,
		pageCount = pageCount,
		readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
		spreadAnchorParity = 0,
		filler = { _, _, _, _, _ ->
			error("Compatibility overload admitted a partial deck")
		},
		page = page
	)
}

internal fun <T : Any> readerPlayLikeCurlLibraryDeck(
	orientation: ReaderPlayLikeCurlOrientation,
	generationId: Long,
	currentOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection,
	spreadAnchorParity: Int,
	filler: (
		generationId: Long,
		deckSlotRole: ReaderPlayLikeCurlDeckSlotRole,
		sourcePageIndex: Int,
		physicalLeaf: ReaderPlayLikeCurlPhysicalLeaf,
		fallbackOrdinal: Int
	) -> PageImage<T>,
	page: (generationId: Long, ordinal: Int) -> PageImage<T>
): PageDeck<T> {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	fun boundedPage(ordinal: Int): PageImage<T> =
		page(generationId, ordinal.coerceIn(0, pageCount - 1))

	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		val canTurnPrevious = boundedCurrent > 0
		val canTurnNext = boundedCurrent < pageCount - 1
		val previous = if (canTurnPrevious) {
			boundedPage(boundedCurrent - 1)
		} else {
			filler(
				generationId,
				ReaderPlayLikeCurlDeckSlotRole.Previous,
				boundedCurrent,
				ReaderPlayLikeCurlPhysicalLeaf.Left,
				boundedCurrent
			).also { check(it.isFiller) }
		}
		val next = if (canTurnNext) {
			boundedPage(boundedCurrent + 1)
		} else {
			filler(
				generationId,
				ReaderPlayLikeCurlDeckSlotRole.Next,
				boundedCurrent,
				ReaderPlayLikeCurlPhysicalLeaf.Right,
				boundedCurrent
			).also { check(it.isFiller) }
		}
		return PortraitPageDeck(
			previous,
			boundedPage(boundedCurrent),
			next,
			canTurnPrevious,
			canTurnNext
		)
	}

	val window = readerPlayLikeCurlVisualSpreadWindow(
		boundedCurrent,
		pageCount,
		spreadAnchorParity,
		readerDirection
	)
	val currentAnchorOrdinal = window.current.logicalOrdinals.first()
	fun slotPage(
		role: ReaderPlayLikeCurlDeckSlotRole,
		spread: ReaderPlayLikeCurlVisualSpread?,
		leaf: ReaderPlayLikeCurlPhysicalLeaf,
		fallbackOrdinal: Int
	): PageImage<T> {
		val ordinal = when (leaf) {
			ReaderPlayLikeCurlPhysicalLeaf.Left -> spread?.physicalLeftOrdinal
			ReaderPlayLikeCurlPhysicalLeaf.Right -> spread?.physicalRightOrdinal
		}
		if (ordinal != null) return boundedPage(ordinal)
		val sourcePageIndex = spread?.sourcePageIndex
			?: window.current.sourcePageIndex
		return filler(
			generationId,
			role,
			sourcePageIndex,
			leaf,
			fallbackOrdinal.coerceIn(0, pageCount - 1)
		).also { resource ->
			check(resource.isFiller) {
				"Missing physical leaves require background-only filler resources"
			}
		}
	}

	val previousLeft = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Previous,
		window.previous,
		ReaderPlayLikeCurlPhysicalLeaf.Left,
		window.current.physicalLeftOrdinal ?: currentAnchorOrdinal
	)
	val previousRight = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Previous,
		window.previous,
		ReaderPlayLikeCurlPhysicalLeaf.Right,
		window.current.physicalRightOrdinal ?: currentAnchorOrdinal
	)
	val currentLeft = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Current,
		window.current,
		ReaderPlayLikeCurlPhysicalLeaf.Left,
		currentAnchorOrdinal
	)
	val currentRight = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Current,
		window.current,
		ReaderPlayLikeCurlPhysicalLeaf.Right,
		currentAnchorOrdinal
	)
	val nextLeft = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Next,
		window.next,
		ReaderPlayLikeCurlPhysicalLeaf.Left,
		window.current.physicalLeftOrdinal ?: currentAnchorOrdinal
	)
	val nextRight = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Next,
		window.next,
		ReaderPlayLikeCurlPhysicalLeaf.Right,
		window.current.physicalRightOrdinal ?: currentAnchorOrdinal
	)
	fun settlementPage(
		spread: ReaderPlayLikeCurlVisualSpread?,
		left: PageImage<T>,
		right: PageImage<T>,
		fallback: PageImage<T>
	): PageImage<T> {
		val present = spread ?: return fallback
		val ordinal = present.logicalOrdinals.first()
		return when {
			present.physicalLeftOrdinal == ordinal -> left
			present.physicalRightOrdinal == ordinal -> right
			else -> error("Settlement ordinal has no physical leaf")
		}
	}
	val currentSettlementPage = settlementPage(
		window.current,
		currentLeft,
		currentRight,
		currentLeft
	)
	return LandscapePageDeck(
		previousLeft,
		previousRight,
		currentLeft,
		currentRight,
		nextLeft,
		nextRight,
		window.previousAvailable,
		window.nextAvailable,
		settlementPage(
			window.previous,
			previousLeft,
			previousRight,
			currentSettlementPage
		),
		currentSettlementPage,
		settlementPage(
			window.next,
			nextLeft,
			nextRight,
			currentSettlementPage
		)
	)
}
