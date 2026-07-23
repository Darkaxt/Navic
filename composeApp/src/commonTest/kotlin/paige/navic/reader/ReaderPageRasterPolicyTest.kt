package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageRasterPolicyTest {
	@Test
	fun rasterResidencyLimitsMatchRendererAndProtectedWindowContracts() {
		assertEquals(6, ReaderPageMaximumRasterImagesPerDeck)
		assertEquals(10, ReaderPageMaximumProtectedRasterEntriesPerLease)
	}

	@Test
	fun publicationLimitsMatchForegroundAndPrefetchOwnerArithmetic() {
		assertEquals(10, ReaderPageMaximumForegroundPublicationEntries)
		assertEquals(1, ReaderPageAdjacentPrefetchPublicationAllowance)
		assertEquals(
			(
				ReaderPageMaximumForegroundPublicationEntries +
					ReaderPageAdjacentPrefetchPublicationAllowance
			) * 2,
			ReaderPageMaximumPublicationCallbacks
		)
	}

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
				ReaderPageRasterPriority.NextLookahead,
				ReaderPageRasterPriority.PreviousLookahead,
				ReaderPageRasterPriority.CurrentChapter,
				ReaderPageRasterPriority.NextChapter,
				ReaderPageRasterPriority.PreviousChapter,
				ReaderPageRasterPriority.NextChapterRemainder,
				ReaderPageRasterPriority.PreviousChapterRemainder
			),
			ReaderPageRasterPriority.entries.sortedBy { priority -> priority.rank }
		)
	}

	@Test
	fun adjacentChapterPrioritiesExposeOneSchedulingDirection() {
		assertEquals(
			listOf(
				ReaderPageAdjacentChapterDirection.Next,
				ReaderPageAdjacentChapterDirection.Previous,
				ReaderPageAdjacentChapterDirection.Next,
				ReaderPageAdjacentChapterDirection.Previous
			),
			listOf(
				ReaderPageRasterPriority.NextChapter,
				ReaderPageRasterPriority.PreviousChapter,
				ReaderPageRasterPriority.NextChapterRemainder,
				ReaderPageRasterPriority.PreviousChapterRemainder
			).map { priority -> priority.adjacentChapterDirection }
		)
		assertEquals(
			null,
			ReaderPageRasterPriority.CurrentChapter.adjacentChapterDirection
		)
	}

}
