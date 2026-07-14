package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageRasterPolicyTest {
	@Test
	fun bitmapQualityDefaultsToHalfResolution() {
		assertEquals(ReaderPageBitmapQuality.Balanced, normalizeReaderPageBitmapQuality(null))
		assertEquals(0.5f, ReaderPageBitmapQuality.Balanced.scale)
	}

	@Test
	fun bitmapQualityExposesFourStableValues() {
		assertEquals(
			listOf(0.25f, 0.5f, 0.75f, 1f),
			ReaderPageBitmapQuality.entries.map { it.scale }
		)
	}

	@Test
	fun bitmapQualityNormalizesPersistedValues() {
		assertEquals(ReaderPageBitmapQuality.Low, normalizeReaderPageBitmapQuality("25"))
		assertEquals(ReaderPageBitmapQuality.Balanced, normalizeReaderPageBitmapQuality("50"))
		assertEquals(ReaderPageBitmapQuality.High, normalizeReaderPageBitmapQuality("75"))
		assertEquals(ReaderPageBitmapQuality.Native, normalizeReaderPageBitmapQuality("100"))
		assertEquals(ReaderPageBitmapQuality.Balanced, normalizeReaderPageBitmapQuality("invalid"))
	}

	@Test
	fun rasterPriorityIsStableAndTransitionFirst() {
		assertEquals(
			listOf(
				ReaderPageRasterPriority.Current,
				ReaderPageRasterPriority.NextTransition,
				ReaderPageRasterPriority.PreviousTransition,
				ReaderPageRasterPriority.CurrentChapter,
				ReaderPageRasterPriority.NextChapter,
				ReaderPageRasterPriority.PreviousChapter
			),
			ReaderPageRasterPriority.entries.sortedBy { priority -> priority.rank }
		)
	}

	@Test
	fun calibrationWaitsForThreeRepresentativeSamples() {
		assertEquals(
			ReaderPageRasterPreparationMode.RollingWindow,
			readerPageRasterPreparationMode(
				samples = List(2) { representativeRasterSample() },
				chapterPageCount = 12
			)
		)
	}

	@Test
	fun calibrationSelectsCompleteChapterWhenProjectedCostFitsBudget() {
		assertEquals(
			ReaderPageRasterPreparationMode.CompleteChapter,
			readerPageRasterPreparationMode(
				samples = List(3) { representativeRasterSample() },
				chapterPageCount = 12
			)
		)
	}

	@Test
	fun calibrationSelectsRollingWindowWhenProjectedCostExceedsBudget() {
		val expensive = ReaderPageRasterCalibrationSample(
			captureMillis = 800,
			encodeWriteMillis = 400,
			readDecodeMillis = 250,
			gpuUploadMillis = 150,
			encodedBytes = 10L * 1024L * 1024L
		)
		assertEquals(
			ReaderPageRasterPreparationMode.RollingWindow,
			readerPageRasterPreparationMode(
				samples = List(3) { expensive },
				chapterPageCount = 20
			)
		)
	}

	private fun representativeRasterSample() = ReaderPageRasterCalibrationSample(
		captureMillis = 80,
		encodeWriteMillis = 40,
		readDecodeMillis = 20,
		gpuUploadMillis = 10,
		encodedBytes = 512L * 1024L
	)
}
