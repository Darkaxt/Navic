package paige.navic.reader

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
	CurrentChapter(3),
	NextChapter(4),
	PreviousChapter(5)
}

fun normalizeReaderPageBitmapQuality(value: String?): ReaderPageBitmapQuality =
	ReaderPageBitmapQuality.entries.firstOrNull { quality ->
		quality.persistedValue == value?.trim()
	} ?: ReaderPageBitmapQuality.Balanced
