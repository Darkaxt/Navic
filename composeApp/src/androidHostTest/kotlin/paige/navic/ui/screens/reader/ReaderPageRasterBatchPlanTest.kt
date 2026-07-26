package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageAdjacentChapterDirection
import paige.navic.reader.ReaderPageRasterPriority

class ReaderPageRasterBatchPlanTest {
	@Test
	fun parsesStableJsPriorityContract() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":30,"layoutMode":"spread","readerDirection":"rtl","step":2,"currentChapterIndex":2,"currentChapterPageStartIndex":6,"currentChapterPageCount":8,"previousChapterPageStartIndex":0,"previousChapterPageCount":6,"nextChapterPageStartIndex":14,"nextChapterPageCount":8},"targets":[{"pageIndex":8,"priority":"current"},{"pageIndex":10,"priority":"next-transition"},{"pageIndex":6,"priority":"previous-transition"},{"pageIndex":12,"priority":"current-chapter"},{"pageIndex":20,"priority":"next-chapter"},{"pageIndex":4,"priority":"previous-chapter"}]}"""
		)

		assertNotNull(plan)
		assertEquals(8, plan.centerPageIndex)
		assertEquals(2, plan.step)
		assertEquals(6, plan.currentChapterPageStartIndex)
		assertEquals(8, plan.currentChapterPageCount)
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
	fun preparedChapterRangeRecognizesOrdinaryTurnsAndChapterBoundaries() {
		val range = ReaderPageRasterPreparedChapterRange(startPageIndex = 6, pageCount = 8)

		assertEquals(true, range.contains(6))
		assertEquals(true, range.contains(13))
		assertEquals(false, range.contains(5))
		assertEquals(false, range.contains(14))
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
	fun blockingPreparationStopsAfterTheBoundedRendererLookahead() {
		val targets = listOf(
			ReaderPageRasterBatchTarget(14, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(16, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(12, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(18, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.PreviousLookahead)
		) + (20..76 step 2).map { pageIndex ->
			ReaderPageRasterBatchTarget(pageIndex, ReaderPageRasterPriority.CurrentChapter)
		} + ReaderPageRasterBatchTarget(78, ReaderPageRasterPriority.NextChapter)

		assertEquals(
			listOf(14, 16, 12, 18, 10),
			readerPageRasterBlockingTargets(targets).map { it.pageIndex }
		)
	}

	@Test
	fun completeCurrentChapterRemainsEligibleForDemandDrivenRepair() {
		val currentChapterPages = (10 until 44 step 2).toList()
		val immediatePages = setOf(14, 16, 12, 18, 10)
		val targets = listOf(
			ReaderPageRasterBatchTarget(14, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(16, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(12, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(18, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.PreviousLookahead)
		) + currentChapterPages
			.filterNot(immediatePages::contains)
			.map { pageIndex ->
				ReaderPageRasterBatchTarget(
					pageIndex,
					ReaderPageRasterPriority.CurrentChapter
				)
			} + ReaderPageRasterBatchTarget(44, ReaderPageRasterPriority.NextChapter)
		val plan = ReaderPageRasterPreparationPlan(
			centerPageIndex = 14,
			pageCount = 90,
			layoutMode = "spread",
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
			step = 2,
			currentChapterIndex = 3,
			currentChapterPageStartIndex = 10,
			currentChapterPageCount = 34,
			previousChapterPageStartIndex = 0,
			previousChapterPageCount = 10,
			nextChapterPageStartIndex = 44,
			nextChapterPageCount = 12,
			targets = targets
		)

		assertEquals(currentChapterPages.toSet(), plan.preparedRepairPageIndices())
		assertEquals(
			listOf(14, 16, 12, 18, 10),
			readerPageRasterBlockingTargets(plan.targets).map { target -> target.pageIndex }
		)
	}

	@Test
	fun onePageChapterBlocksUntilTheFivePageRendererDeckIsResident() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":5,"pageCount":12,"layoutMode":"single","readerDirection":"ltr","step":1,"currentChapterPageStartIndex":5,"currentChapterPageCount":1},"targets":[{"pageIndex":5,"priority":"current"},{"pageIndex":6,"priority":"next-transition"},{"pageIndex":4,"priority":"previous-transition"},{"pageIndex":7,"priority":"next-lookahead"},{"pageIndex":3,"priority":"previous-lookahead"},{"pageIndex":8,"priority":"next-chapter"},{"pageIndex":2,"priority":"previous-chapter"}]}"""
		)

		assertNotNull(plan)
		assertEquals(setOf(5, 6, 4, 7, 3), plan.preparedRepairPageIndices())
		assertEquals(
			listOf(5, 6, 4, 7, 3),
			readerPageRasterBlockingTargets(plan.targets).map { target -> target.pageIndex }
		)
		assertEquals(
			listOf(8, 2),
			readerPageRasterBackgroundTargets(plan.targets).map { target -> target.pageIndex }
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

	@Test
	fun adjacentChapterWindowsPrecedeIdleRemainders() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":40,"layoutMode":"single","readerDirection":"ltr","step":1,"currentChapterPageStartIndex":6,"currentChapterPageCount":6},"targets":[{"pageIndex":8,"priority":"current"},{"pageIndex":12,"priority":"next-chapter"},{"pageIndex":5,"priority":"previous-chapter"},{"pageIndex":15,"priority":"next-chapter-remainder"},{"pageIndex":2,"priority":"previous-chapter-remainder"}]}"""
		)

		assertNotNull(plan)
		assertEquals(
			listOf(
				ReaderPageRasterPriority.NextChapter,
				ReaderPageRasterPriority.PreviousChapter,
				ReaderPageRasterPriority.NextChapterRemainder,
				ReaderPageRasterPriority.PreviousChapterRemainder
			),
			readerPageRasterBackgroundTargets(plan.targets).map { target -> target.priority }
		)
	}

	@Test
	fun adjacentChapterTargetsBecomeSeparateIdentityQualifiedBatches() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":30,"layoutMode":"spread","readerDirection":"rtl","step":2,"currentChapterIndex":2,"currentChapterPageStartIndex":6,"currentChapterPageCount":8,"previousChapterPageStartIndex":0,"previousChapterPageCount":6,"nextChapterPageStartIndex":14,"nextChapterPageCount":8},"targets":[{"pageIndex":8,"priority":"current"},{"pageIndex":20,"priority":"next-chapter"},{"pageIndex":18,"priority":"next-chapter-remainder"},{"pageIndex":4,"priority":"previous-chapter"},{"pageIndex":2,"priority":"previous-chapter-remainder"}]}"""
		)

		assertNotNull(plan)
		val chapters = plan.adjacentChapterPrefetchChapters()
		assertEquals(
			listOf(
				ReaderPageAdjacentChapterDirection.Previous,
				ReaderPageAdjacentChapterDirection.Next
			),
			chapters.map { chapter -> chapter.identity.direction }
		)
		assertEquals(listOf(4, 2), chapters[0].targets.map { target -> target.pageIndex })
		assertEquals(listOf(20, 18), chapters[1].targets.map { target -> target.pageIndex })
		assertEquals(listOf(1, 3), chapters.map { chapter -> chapter.identity.chapterIndex })
	}

	@Test
	fun jsPlanBlocksOnOnePersistentRefillReserveBeyondTheDecodedWindow() {
		val source = File("src/androidMain/assets/reader/navic-reader-page-turn-preview.js").readText()
		val plan = source
			.substringAfter("function pageTurnRasterPreparationPlan(")
			.substringBefore("function pageTurnPreviewContext()")

		val nextReserve = plan.indexOf(
			"addTarget(centerPageIndex + step * 3, 'next-lookahead')"
		)
		val previousReserve = plan.indexOf(
			"addTarget(centerPageIndex - step * 3, 'previous-lookahead')"
		)
		val chapterRemainder = plan.indexOf("currentChapterPages.forEach")
		assertTrue(nextReserve >= 0)
		assertTrue(previousReserve > nextReserve)
		assertTrue(chapterRemainder > previousReserve)
	}

	@Test
	fun jsPlanBuildsAdjacentWindowsBeforeChapterRemainders() {
		val source = File("src/androidMain/assets/reader/navic-reader-page-turn-preview.js").readText()
		val plan = source
			.substringAfter("function pageTurnRasterPreparationPlan(")
			.substringBefore("function pageTurnPreviewContext()")

		val nextWindow = plan.indexOf("'next-chapter'")
		val previousWindow = plan.indexOf("'previous-chapter'")
		val nextRemainder = plan.indexOf("'next-chapter-remainder'")
		val previousRemainder = plan.indexOf("'previous-chapter-remainder'")
		assertTrue(nextWindow >= 0)
		assertTrue(previousWindow > nextWindow)
		assertTrue(nextRemainder > previousWindow)
		assertTrue(previousRemainder > nextRemainder)
		assertTrue(plan.contains("slice(0, 3)"))
		assertTrue(plan.contains("slice(-3).reverse()"))
	}
}
