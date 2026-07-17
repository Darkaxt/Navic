package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import paige.navic.reader.ReaderPageRasterPriority

class ReaderPageRasterBatchPlanTest {
	@Test
	fun parsesStableJsPriorityContract() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":30,"layoutMode":"spread","readerDirection":"rtl","step":2,"currentChapterPageCount":8},"targets":[{"pageIndex":8,"priority":"current"},{"pageIndex":10,"priority":"next-transition"},{"pageIndex":6,"priority":"previous-transition"},{"pageIndex":12,"priority":"current-chapter"},{"pageIndex":20,"priority":"next-chapter"},{"pageIndex":4,"priority":"previous-chapter"}]}"""
		)

		assertNotNull(plan)
		assertEquals(8, plan.centerPageIndex)
		assertEquals(2, plan.step)
		assertEquals(ReaderPlayLikeCurlReaderDirection.Rtl, plan.readerDirection)
		assertEquals(
			listOf(
				ReaderPageRasterPriority.Current,
				ReaderPageRasterPriority.NextTransition,
				ReaderPageRasterPriority.PreviousTransition,
				ReaderPageRasterPriority.CurrentChapter,
				ReaderPageRasterPriority.NextChapter,
				ReaderPageRasterPriority.PreviousChapter
			),
			plan.targets.map { target -> target.priority }
		)
	}

	@Test
	fun defaultsMissingReaderDirectionToLtrForOlderCachedPlans() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":2,"pageCount":8,"layoutMode":"single","step":1,"currentChapterPageCount":4},"targets":[{"pageIndex":2,"priority":"current"}]}"""
		)

		assertNotNull(plan)
		assertEquals(ReaderPlayLikeCurlReaderDirection.Ltr, plan.readerDirection)
	}

	@Test
	fun calibrationStageStartsWithCurrentAndAdjacentTransitionsOnly() {
		val targets = listOf(
			ReaderPageRasterBatchTarget(8, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(6, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(12, ReaderPageRasterPriority.CurrentChapter)
		)

		assertEquals(listOf(8, 10, 6), readerPageRasterCalibrationTargets(targets).map { it.pageIndex })
	}

	@Test
	fun blockingPreparationIncludesImmediateDeckAndCompleteCurrentChapterOnly() {
		val targets = listOf(
			ReaderPageRasterBatchTarget(8, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(6, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(12, ReaderPageRasterPriority.CurrentChapter),
			ReaderPageRasterBatchTarget(4, ReaderPageRasterPriority.CurrentChapter),
			ReaderPageRasterBatchTarget(14, ReaderPageRasterPriority.CurrentChapter),
			ReaderPageRasterBatchTarget(20, ReaderPageRasterPriority.NextChapter)
		)

		assertEquals(
			listOf(8, 10, 6, 12, 4, 14),
			readerPageRasterBlockingTargets(targets).map { it.pageIndex }
		)
	}

	@Test
	fun adjacentChapterTargetsAreBackgroundWork() {
		val targets = listOf(
			ReaderPageRasterBatchTarget(8, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(6, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(12, ReaderPageRasterPriority.CurrentChapter),
			ReaderPageRasterBatchTarget(20, ReaderPageRasterPriority.NextChapter),
			ReaderPageRasterBatchTarget(4, ReaderPageRasterPriority.PreviousChapter)
		)

		assertEquals(
			listOf(20, 4),
			readerPageRasterBackgroundTargets(targets).map { it.pageIndex }
		)
	}
}
