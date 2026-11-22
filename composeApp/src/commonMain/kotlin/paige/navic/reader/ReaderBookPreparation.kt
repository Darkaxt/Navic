package paige.navic.reader

/** #66 "Prepare pages": whole-book raster preparation reported by the reader host. */
enum class ReaderBookPreparationPhase {
	/** Waiting for the reader to open and plan the book. */
	Starting,
	Preparing,
	/** Every planned page is durable; the book is recorded as prepared. */
	Complete,
	/** The plan drained with pages still missing (failures, storage limit or a stale plan). */
	Incomplete
}

data class ReaderBookPreparationProgress(
	val phase: ReaderBookPreparationPhase = ReaderBookPreparationPhase.Starting,
	val preparedPages: Int = 0,
	val plannedPages: Int = 0,
	val preparedChapters: Int = 0,
	val plannedChapters: Int = 0,
	/** The layout this progress covers, once the reader has planned it. */
	val orientation: ReaderPreparationOrientation? = null
) {
	val fraction: Float
		get() = if (plannedPages <= 0) 0f else (preparedPages.toFloat() / plannedPages).coerceIn(0f, 1f)
}

/** Physical layout a preparation covers: landscape (spreads) or portrait (single pages). */
enum class ReaderPreparationOrientation(val persistedValue: String) {
	Landscape("landscape"),
	Portrait("portrait");

	val other: ReaderPreparationOrientation
		get() = if (this == Landscape) Portrait else Landscape

	companion object {
		fun fromPersisted(value: String?): ReaderPreparationOrientation? =
			entries.firstOrNull { it.persistedValue == value }

		fun forAspectRatio(widthOverHeight: Double?): ReaderPreparationOrientation? =
			widthOverHeight?.let { ratio -> if (ratio >= 1.0) Landscape else Portrait }
	}
}
