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

enum class ReaderPageRasterPreparationMode {
	CompleteChapter,
	RollingWindow
}

data class ReaderPageRasterCalibrationSample(
	val captureMillis: Long,
	val encodeWriteMillis: Long,
	val readDecodeMillis: Long,
	val gpuUploadMillis: Long,
	val encodedBytes: Long
) {
	val totalMillis: Long
		get() = captureMillis.coerceAtLeast(0L) +
			encodeWriteMillis.coerceAtLeast(0L) +
			readDecodeMillis.coerceAtLeast(0L) +
			gpuUploadMillis.coerceAtLeast(0L)
}

private const val ReaderPageRasterCalibrationSampleCount = 3
private const val ReaderPageRasterEagerDurationBudgetMillis = 12_000L
private const val ReaderPageRasterEagerByteBudget = 96L * 1024L * 1024L

fun readerPageRasterPreparationMode(
	samples: List<ReaderPageRasterCalibrationSample>,
	chapterPageCount: Int,
	maxEagerDurationMillis: Long = ReaderPageRasterEagerDurationBudgetMillis,
	maxEagerBytes: Long = ReaderPageRasterEagerByteBudget
): ReaderPageRasterPreparationMode {
	if (chapterPageCount <= 0 || samples.size < ReaderPageRasterCalibrationSampleCount) {
		return ReaderPageRasterPreparationMode.RollingWindow
	}
	val representative = samples.take(ReaderPageRasterCalibrationSampleCount)
	val averageMillis = representative.sumOf { sample -> sample.totalMillis } / representative.size
	val averageBytes = representative.sumOf { sample -> sample.encodedBytes.coerceAtLeast(0L) } / representative.size
	val projectedMillis = averageMillis.saturatedTimes(chapterPageCount)
	val projectedBytes = averageBytes.saturatedTimes(chapterPageCount)
	return if (projectedMillis <= maxEagerDurationMillis && projectedBytes <= maxEagerBytes) {
		ReaderPageRasterPreparationMode.CompleteChapter
	} else {
		ReaderPageRasterPreparationMode.RollingWindow
	}
}

fun normalizeReaderPageBitmapQuality(value: String?): ReaderPageBitmapQuality =
	ReaderPageBitmapQuality.entries.firstOrNull { quality ->
		quality.persistedValue == value?.trim()
	} ?: ReaderPageBitmapQuality.Balanced

private fun Long.saturatedTimes(multiplier: Int): Long {
	if (this <= 0L || multiplier <= 0) return 0L
	return if (this > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else this * multiplier
}
