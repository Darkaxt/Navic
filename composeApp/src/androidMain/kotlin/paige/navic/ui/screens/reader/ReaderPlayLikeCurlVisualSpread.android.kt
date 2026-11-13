package paige.navic.ui.screens.reader

/*
 * Foliate's spread paginator counts whole spreads: in landscape a Foliate ordinal (and its raster
 * snapshot index) is one complete two-leaf spread, exactly like JS `readerPageTurnPlan` steps ±1.
 * The curl library, the raster adapter and Whispersync overlays need one identity per physical
 * leaf, so landscape leaves use leaf ordinals: 2 * spread + reading position (0 for the
 * reading-first leaf, 1 for the second). Portrait leaf ordinals equal Foliate ordinals.
 */

internal data class ReaderPlayLikeCurlVisualSpread(
	/** Foliate spread ordinal; also the raster snapshot index that holds both leaves. */
	val sourcePageIndex: Int,
	val physicalLeftOrdinal: Int,
	val physicalRightOrdinal: Int
) {
	/** Leaf ordinals in reading order; the first one is the spread's settlement identity. */
	val logicalOrdinals: List<Int>
		get() = listOf(
			minOf(physicalLeftOrdinal, physicalRightOrdinal),
			maxOf(physicalLeftOrdinal, physicalRightOrdinal)
		)

	fun leafOrdinal(leaf: ReaderPlayLikeCurlPhysicalLeaf): Int = when (leaf) {
		ReaderPlayLikeCurlPhysicalLeaf.Left -> physicalLeftOrdinal
		ReaderPlayLikeCurlPhysicalLeaf.Right -> physicalRightOrdinal
	}
}

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

/** Number of native leaf ordinals (raster adapter keys and curl page ordinals). */
internal fun readerPlayLikeCurlLeafOrdinalCount(
	orientation: ReaderPlayLikeCurlOrientation,
	pageCount: Int
): Int = when (orientation) {
	ReaderPlayLikeCurlOrientation.Portrait -> pageCount
	ReaderPlayLikeCurlOrientation.Landscape -> Math.multiplyExact(pageCount, 2)
}

/** Foliate ordinal that owns a native leaf ordinal, e.g. a settled curl page. */
internal fun readerPlayLikeCurlFoliateOrdinal(
	orientation: ReaderPlayLikeCurlOrientation,
	leafOrdinal: Int
): Int = when (orientation) {
	ReaderPlayLikeCurlOrientation.Portrait -> leafOrdinal
	ReaderPlayLikeCurlOrientation.Landscape -> Math.floorDiv(leafOrdinal, 2)
}

/** Reading-first leaf ordinal of a Foliate ordinal: the curl settlement page identity. */
internal fun readerPlayLikeCurlAnchorLeafOrdinal(
	orientation: ReaderPlayLikeCurlOrientation,
	foliateOrdinal: Int
): Int = when (orientation) {
	ReaderPlayLikeCurlOrientation.Portrait -> foliateOrdinal
	ReaderPlayLikeCurlOrientation.Landscape -> Math.multiplyExact(foliateOrdinal, 2)
}

private fun readerPlayLikeCurlReadingFirstLeaf(
	readerDirection: ReaderPlayLikeCurlReaderDirection
): ReaderPlayLikeCurlPhysicalLeaf = when (readerDirection) {
	ReaderPlayLikeCurlReaderDirection.Ltr -> ReaderPlayLikeCurlPhysicalLeaf.Left
	ReaderPlayLikeCurlReaderDirection.Rtl -> ReaderPlayLikeCurlPhysicalLeaf.Right
}

private fun ReaderPlayLikeCurlPhysicalLeaf.opposite(): ReaderPlayLikeCurlPhysicalLeaf = when (this) {
	ReaderPlayLikeCurlPhysicalLeaf.Left -> ReaderPlayLikeCurlPhysicalLeaf.Right
	ReaderPlayLikeCurlPhysicalLeaf.Right -> ReaderPlayLikeCurlPhysicalLeaf.Left
}

/** Maps a landscape leaf ordinal to its spread snapshot and physical half. */
internal fun readerPlayLikeCurlSpreadSlot(
	logicalOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection
): ReaderPlayLikeCurlSpreadSlot {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val leafOrdinal = logicalOrdinal.coerceIn(
		0,
		readerPlayLikeCurlLeafOrdinalCount(ReaderPlayLikeCurlOrientation.Landscape, pageCount) - 1
	)
	val readingFirstLeaf = readerPlayLikeCurlReadingFirstLeaf(readerDirection)
	return ReaderPlayLikeCurlSpreadSlot(
		sourcePageIndex = leafOrdinal / 2,
		physicalLeaf = if (leafOrdinal % 2 == 0) readingFirstLeaf else readingFirstLeaf.opposite()
	)
}

internal fun readerPlayLikeCurlVisualSpread(
	sourcePageIndex: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection
): ReaderPlayLikeCurlVisualSpread {
	require(sourcePageIndex >= 0) { "PlayLikeCurl spread ordinal must not be negative" }
	val readingFirst = readerPlayLikeCurlAnchorLeafOrdinal(
		ReaderPlayLikeCurlOrientation.Landscape,
		sourcePageIndex
	)
	val readingSecond = readingFirst + 1
	return when (readerPlayLikeCurlReadingFirstLeaf(readerDirection)) {
		ReaderPlayLikeCurlPhysicalLeaf.Left -> ReaderPlayLikeCurlVisualSpread(
			sourcePageIndex = sourcePageIndex,
			physicalLeftOrdinal = readingFirst,
			physicalRightOrdinal = readingSecond
		)
		ReaderPlayLikeCurlPhysicalLeaf.Right -> ReaderPlayLikeCurlVisualSpread(
			sourcePageIndex = sourcePageIndex,
			physicalLeftOrdinal = readingSecond,
			physicalRightOrdinal = readingFirst
		)
	}
}

/** Previous, current and next whole spreads around a Foliate spread ordinal. */
internal fun readerPlayLikeCurlVisualSpreadWindow(
	currentOrdinal: Int,
	pageCount: Int,
	readerDirection: ReaderPlayLikeCurlReaderDirection
): ReaderPlayLikeCurlVisualSpreadWindow {
	require(pageCount > 0) { "PlayLikeCurl page count must be positive" }
	val bounded = currentOrdinal.coerceIn(0, pageCount - 1)
	fun spread(ordinal: Int): ReaderPlayLikeCurlVisualSpread? =
		ordinal.takeIf { it in 0 until pageCount }
			?.let { present -> readerPlayLikeCurlVisualSpread(present, readerDirection) }
	return ReaderPlayLikeCurlVisualSpreadWindow(
		previous = spread(bounded - 1),
		current = readerPlayLikeCurlVisualSpread(bounded, readerDirection),
		next = spread(bounded + 1)
	)
}
