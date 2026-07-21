package paige.navic.ui.screens.reader

internal data class ReaderPlayLikeCurlVisualSpread(
	val sourcePageIndex: Int,
	val logicalOrdinals: List<Int>,
	val physicalLeftOrdinal: Int?,
	val physicalRightOrdinal: Int?
)

internal data class ReaderPlayLikeCurlVisualSpreadWindow(
	val previous: ReaderPlayLikeCurlVisualSpread?,
	val current: ReaderPlayLikeCurlVisualSpread,
	val next: ReaderPlayLikeCurlVisualSpread?
) {
	val previousAvailable: Boolean get() = previous != null
	val nextAvailable: Boolean get() = next != null
}

internal enum class ReaderPlayLikeCurlPhysicalLeaf {
	Left,
	Right
}

internal enum class ReaderPlayLikeCurlDeckSlotRole {
	Previous,
	Current,
	Next
}

internal data class ReaderPlayLikeCurlSpreadSlot(
	val sourcePageIndex: Int,
	val physicalLeaf: ReaderPlayLikeCurlPhysicalLeaf
)

internal fun readerPlayLikeCurlSpreadSlot(
	logicalOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection,
	spreadAnchorParity: Int
): ReaderPlayLikeCurlSpreadSlot {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val ordinal = logicalOrdinal.coerceIn(0, pageCount - 1)
	val relativeParity = Math.floorMod(
		ordinal - Math.floorMod(spreadAnchorParity, 2),
		2
	)
	val sourcePageIndex = (ordinal - relativeParity).coerceAtLeast(0)
	val anchorLeaf = when (readerDirection) {
		ReaderPlayLikeCurlReaderDirection.Ltr -> ReaderPlayLikeCurlPhysicalLeaf.Left
		ReaderPlayLikeCurlReaderDirection.Rtl -> ReaderPlayLikeCurlPhysicalLeaf.Right
	}
	val physicalLeaf = if (relativeParity == 0) {
		anchorLeaf
	} else if (anchorLeaf == ReaderPlayLikeCurlPhysicalLeaf.Left) {
		ReaderPlayLikeCurlPhysicalLeaf.Right
	} else {
		ReaderPlayLikeCurlPhysicalLeaf.Left
	}
	return ReaderPlayLikeCurlSpreadSlot(
		sourcePageIndex = sourcePageIndex,
		physicalLeaf = physicalLeaf
	)
}

internal fun readerPlayLikeCurlVisualSpreads(
	pageCount: Int,
	spreadAnchorParity: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection
): List<ReaderPlayLikeCurlVisualSpread> =
	(0 until pageCount)
		.map { ordinal ->
			ordinal to readerPlayLikeCurlSpreadSlot(
				logicalOrdinal = ordinal,
				pageCount = pageCount,
				readerDirection = readerDirection,
				spreadAnchorParity = spreadAnchorParity
			)
		}
		.groupBy { (_, slot) -> slot.sourcePageIndex }
		.values
		.map { entries ->
			ReaderPlayLikeCurlVisualSpread(
				sourcePageIndex = entries.first().second.sourcePageIndex,
				logicalOrdinals = entries.map { (ordinal, _) -> ordinal },
				physicalLeftOrdinal = entries.firstOrNull { (_, slot) ->
					slot.physicalLeaf == ReaderPlayLikeCurlPhysicalLeaf.Left
				}?.first,
				physicalRightOrdinal = entries.firstOrNull { (_, slot) ->
					slot.physicalLeaf == ReaderPlayLikeCurlPhysicalLeaf.Right
				}?.first
			)
		}

internal fun readerPlayLikeCurlVisualSpreadWindow(
	currentOrdinal: Int,
	pageCount: Int,
	spreadAnchorParity: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection
): ReaderPlayLikeCurlVisualSpreadWindow {
	val spreads = readerPlayLikeCurlVisualSpreads(
		pageCount,
		spreadAnchorParity,
		readerDirection
	)
	val bounded = currentOrdinal.coerceIn(0, pageCount - 1)
	val currentIndex = spreads.indexOfFirst { spread ->
		bounded in spread.logicalOrdinals
	}
	check(currentIndex >= 0) { "Current ordinal has no visual spread" }
	return ReaderPlayLikeCurlVisualSpreadWindow(
		previous = spreads.getOrNull(currentIndex - 1),
		current = spreads[currentIndex],
		next = spreads.getOrNull(currentIndex + 1)
	)
}
