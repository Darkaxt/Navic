package paige.navic.reader

internal const val ReaderPageMaximumRasterImagesPerDeck = 6
internal const val ReaderPageMaximumProtectedRasterEntriesPerLease = 10

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
	PreviousChapterRemainder(9)
}

enum class ReaderPageAdjacentChapterDirection {
	Previous,
	Next
}

val ReaderPageRasterPriority.adjacentChapterDirection: ReaderPageAdjacentChapterDirection?
	get() = when (this) {
		ReaderPageRasterPriority.PreviousChapter,
		ReaderPageRasterPriority.PreviousChapterRemainder ->
			ReaderPageAdjacentChapterDirection.Previous
		ReaderPageRasterPriority.NextChapter,
		ReaderPageRasterPriority.NextChapterRemainder ->
			ReaderPageAdjacentChapterDirection.Next
		else -> null
	}

fun normalizeReaderPageBitmapQuality(value: String?): ReaderPageBitmapQuality =
	ReaderPageBitmapQuality.entries.firstOrNull { quality ->
		quality.persistedValue == value?.trim()
	} ?: ReaderPageBitmapQuality.Balanced
