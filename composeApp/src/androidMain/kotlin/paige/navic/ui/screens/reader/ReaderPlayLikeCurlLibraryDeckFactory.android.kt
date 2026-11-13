package paige.navic.ui.screens.reader

import karacken.curl.LandscapePageDeck
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PageImage
import karacken.curl.PortraitPageDeck

/**
 * Raster adapter keys for the Foliate ordinals within [radius] of the current ordinal. Portrait keys
 * are Foliate pages; landscape keys are the two leaf ordinals of each whole Foliate spread.
 */
private fun readerPlayLikeCurlWindowLeafOrdinals(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int,
	radius: Int
): List<Int> {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	val ordinals = (boundedCurrent - radius..boundedCurrent + radius)
		.map { ordinal -> ordinal.coerceIn(0, pageCount - 1) }
		.distinct()
	return when (orientation) {
		ReaderPlayLikeCurlOrientation.Portrait -> ordinals
		ReaderPlayLikeCurlOrientation.Landscape -> ordinals.flatMap { spread ->
			val readingFirst = readerPlayLikeCurlAnchorLeafOrdinal(orientation, spread)
			listOf(readingFirst, readingFirst + 1)
		}
	}
}

internal fun readerPlayLikeCurlLibraryDeckPageIndices(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int
): List<Int> = readerPlayLikeCurlWindowLeafOrdinals(
	orientation = orientation,
	currentOrdinal = currentOrdinal,
	pageCount = pageCount,
	radius = 1
)

internal fun readerPlayLikeCurlPreparedPageIndices(
	orientation: ReaderPlayLikeCurlOrientation,
	currentOrdinal: Int,
	pageCount: Int
): List<Int> = readerPlayLikeCurlWindowLeafOrdinals(
	orientation = orientation,
	currentOrdinal = currentOrdinal,
	pageCount = pageCount,
	radius = 2
)

/** Foliate destination of a committed turn: one page in portrait, one whole spread in landscape. */
internal fun readerPlayLikeCurlSettlementTargetOrdinal(
	currentOrdinal: Int,
	pageCount: Int,
	pageChange: PageChange
): Int? {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val boundedCurrent = currentOrdinal.coerceIn(0, pageCount - 1)
	val target = when (pageChange) {
		PageChange.PREVIOUS -> boundedCurrent - 1
		PageChange.NEXT -> boundedCurrent + 1
		PageChange.NONE -> return null
	}
	return target.takeIf { ordinal -> ordinal in 0 until pageCount }
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
		filler = { _, _, _, _, _ ->
			error("Compatibility overload admitted a partial deck")
		},
		page = page
	)
}

/**
 * Builds the curl deck around the Foliate ordinal [currentOrdinal]. [page] receives native leaf
 * ordinals: Foliate pages in portrait, `2 * spread + reading position` in landscape.
 */
internal fun <T : Any> readerPlayLikeCurlLibraryDeck(
	orientation: ReaderPlayLikeCurlOrientation,
	generationId: Long,
	currentOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection,
	requireCompleteMaterial: Boolean = false,
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
	fun requireMaterial(image: PageImage<T>): PageImage<T> = image.also { resource ->
		if (requireCompleteMaterial) {
			check(resource.hasCompleteMaterial()) {
				"Production PlayLikeCurl deck page omitted presentation material"
			}
			check(resource.material.generationId == generationId) {
				"Production PlayLikeCurl material generation does not match the deck"
			}
		}
	}

	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		fun boundedPage(ordinal: Int): PageImage<T> =
			requireMaterial(page(generationId, ordinal.coerceIn(0, pageCount - 1)))
		val canTurnPrevious = boundedCurrent > 0
		val canTurnNext = boundedCurrent < pageCount - 1
		val previous = if (canTurnPrevious) {
			boundedPage(boundedCurrent - 1)
		} else {
			requireMaterial(
				filler(
					generationId,
					ReaderPlayLikeCurlDeckSlotRole.Previous,
					boundedCurrent,
					ReaderPlayLikeCurlPhysicalLeaf.Left,
					boundedCurrent
				)
			).also { check(it.isFiller) }
		}
		val next = if (canTurnNext) {
			boundedPage(boundedCurrent + 1)
		} else {
			requireMaterial(
				filler(
					generationId,
					ReaderPlayLikeCurlDeckSlotRole.Next,
					boundedCurrent,
					ReaderPlayLikeCurlPhysicalLeaf.Right,
					boundedCurrent
				)
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
		readerDirection
	)
	fun slotPage(
		role: ReaderPlayLikeCurlDeckSlotRole,
		spread: ReaderPlayLikeCurlVisualSpread?,
		leaf: ReaderPlayLikeCurlPhysicalLeaf
	): PageImage<T> {
		if (spread != null) return requireMaterial(page(generationId, spread.leafOrdinal(leaf)))
		return requireMaterial(
			filler(
				generationId,
				role,
				window.current.sourcePageIndex,
				leaf,
				window.current.leafOrdinal(leaf)
			)
		).also { resource ->
			check(resource.isFiller) {
				"Missing physical leaves require background-only filler resources"
			}
		}
	}

	val previousLeft = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Previous,
		window.previous,
		ReaderPlayLikeCurlPhysicalLeaf.Left
	)
	val previousRight = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Previous,
		window.previous,
		ReaderPlayLikeCurlPhysicalLeaf.Right
	)
	val currentLeft = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Current,
		window.current,
		ReaderPlayLikeCurlPhysicalLeaf.Left
	)
	val currentRight = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Current,
		window.current,
		ReaderPlayLikeCurlPhysicalLeaf.Right
	)
	val nextLeft = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Next,
		window.next,
		ReaderPlayLikeCurlPhysicalLeaf.Left
	)
	val nextRight = slotPage(
		ReaderPlayLikeCurlDeckSlotRole.Next,
		window.next,
		ReaderPlayLikeCurlPhysicalLeaf.Right
	)
	// The reading-first leaf identifies its whole spread when the curl settles.
	fun settlementPage(
		spread: ReaderPlayLikeCurlVisualSpread?,
		left: PageImage<T>,
		right: PageImage<T>,
		fallback: PageImage<T>
	): PageImage<T> {
		val present = spread ?: return fallback
		return when (present.logicalOrdinals.first()) {
			present.physicalLeftOrdinal -> left
			present.physicalRightOrdinal -> right
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
