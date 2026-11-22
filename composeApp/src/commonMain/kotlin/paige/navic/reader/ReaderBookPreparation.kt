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
	val plannedChapters: Int = 0
) {
	val fraction: Float
		get() = if (plannedPages <= 0) 0f else (preparedPages.toFloat() / plannedPages).coerceIn(0f, 1f)
}
