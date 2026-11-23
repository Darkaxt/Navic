package paige.navic.reader

internal const val ReaderPageMaximumRasterImagesPerDeck = 6
internal const val ReaderPageMaximumProtectedRasterEntriesPerLease = 10
internal const val ReaderPageMaximumForegroundPublicationEntries = 11
internal const val ReaderPageAdjacentPrefetchPublicationAllowance = 1
internal const val ReaderPageMaximumPublicationCallbacks =
	(ReaderPageMaximumForegroundPublicationEntries +
		ReaderPageAdjacentPrefetchPublicationAllowance) * 2

enum class ReaderPageBitmapQuality(
	val persistedValue: String,
	val scale: Float
) {
	Low(persistedValue = "25", scale = 0.25f),
	Balanced(persistedValue = "50", scale = 0.5f),
	High(persistedValue = "75", scale = 0.75f),
	Native(persistedValue = "100", scale = 1f)
}

enum class ReaderPageRasterPriority(val rank: Int) {
	Current(0),
	NextTransition(1),
	PreviousTransition(2),
	NextLookahead(3),
	PreviousLookahead(4),
	CurrentChapter(5),
	NextChapter(6),
	PreviousChapter(7),
	NextChapterRemainder(8),
	PreviousChapterRemainder(9),
	/** Chapters two or more ahead, prepared only in the "Whole book" cache scope (#57). */
	LaterChapter(10)
}

enum class ReaderPageAdjacentChapterDirection {
	Current,
	Next,
	Previous,
	/** A chapter two or more ahead of the current one ("Whole book" scope, #57). */
	Later
}

val ReaderPageRasterPriority.adjacentChapterDirection: ReaderPageAdjacentChapterDirection?
	get() = when (this) {
		ReaderPageRasterPriority.CurrentChapter ->
			ReaderPageAdjacentChapterDirection.Current
		ReaderPageRasterPriority.PreviousChapter,
		ReaderPageRasterPriority.PreviousChapterRemainder ->
			ReaderPageAdjacentChapterDirection.Previous
		ReaderPageRasterPriority.NextChapter,
		ReaderPageRasterPriority.NextChapterRemainder ->
			ReaderPageAdjacentChapterDirection.Next
		ReaderPageRasterPriority.LaterChapter ->
			ReaderPageAdjacentChapterDirection.Later
		else -> null
	}

fun normalizeReaderPageBitmapQuality(value: String?): ReaderPageBitmapQuality =
	ReaderPageBitmapQuality.entries.firstOrNull { quality ->
		quality.persistedValue == value?.trim()
	} ?: ReaderPageBitmapQuality.Native

/** How far ahead page rasters are prepared in the background while a book is open (#57). */
enum class ReaderPageCacheScope(val persistedValue: String) {
	PerChapter(persistedValue = "per-chapter"),
	FullBook(persistedValue = "full-book")
}

/** Which page layouts (single page / spread) get prepared rasters (#57). */
enum class ReaderPageCacheLayouts(val persistedValue: String) {
	Current(persistedValue = "current"),
	Both(persistedValue = "both")
}

fun normalizeReaderPageCacheScope(value: String?): ReaderPageCacheScope =
	ReaderPageCacheScope.entries.firstOrNull { scope ->
		scope.persistedValue == value?.trim()
	} ?: ReaderPageCacheScope.PerChapter

fun normalizeReaderPageCacheLayouts(value: String?): ReaderPageCacheLayouts =
	ReaderPageCacheLayouts.entries.firstOrNull { layouts ->
		layouts.persistedValue == value?.trim()
	} ?: ReaderPageCacheLayouts.Current

fun readerPageCacheScopeShortLabel(value: String?): String =
	when (normalizeReaderPageCacheScope(value)) {
		ReaderPageCacheScope.PerChapter -> "This + next chapter"
		ReaderPageCacheScope.FullBook -> "Whole book"
	}

fun readerPageCacheLayoutsShortLabel(value: String?): String =
	when (normalizeReaderPageCacheLayouts(value)) {
		ReaderPageCacheLayouts.Current -> "Current layout"
		ReaderPageCacheLayouts.Both -> "Both layouts"
	}
