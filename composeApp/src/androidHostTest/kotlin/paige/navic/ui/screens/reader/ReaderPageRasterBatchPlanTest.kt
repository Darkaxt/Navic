package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
	fun parsedPreparationPlanCarriesItsCaptureGeometry() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":30,"layoutMode":"single","readerDirection":"ltr","step":1},"captureGeometry":{"viewportWidth":1232,"viewportHeight":1974,"mode":"single","pages":[{"role":"full","left":0,"top":0,"width":1219.68,"height":1974}],"reverseFaceColorArgb":4294967295},"targets":[{"pageIndex":8,"priority":"current"}]}"""
		)

		assertNotNull(plan)
		val geometry = assertNotNull(plan.captureGeometry)
		assertEquals(1232.0, geometry.viewportWidth)
		assertEquals(1974.0, geometry.viewportHeight)
		assertEquals(1, geometry.pages.size)
		assertEquals(1219.68, geometry.pages.single().width)
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
	fun blockingWindowIsCurrentPlusFivePhysicalTurnsInEachDirection() {
		assertEquals(
			listOf(14, 16, 12, 18, 20, 22, 24, 10, 8, 6, 4),
			readerPageRasterBlockingWindow(
				centerPageIndex = 14,
				step = 2,
				pageCount = 90
			)
		)
	}

	@Test
	fun blockingWindowTruncatesAtPublicationBoundaries() {
		assertEquals(
			listOf(0, 1, 2, 3),
			readerPageRasterBlockingWindow(0, step = 1, pageCount = 4)
		)
		assertEquals(
			listOf(9, 8, 7, 6, 5, 4),
			readerPageRasterBlockingWindow(9, step = 1, pageCount = 10)
		)
	}

	@Test
	fun blockingPreparationExcludesCurrentChapterRemainder() {
		val blockingPages = listOf(14, 16, 12, 18, 20, 22, 24, 10, 8, 6, 4)
		val targets = blockingPages.mapIndexed { index, pageIndex ->
			ReaderPageRasterBatchTarget(
				pageIndex,
				when (index) {
					0 -> ReaderPageRasterPriority.Current
					1 -> ReaderPageRasterPriority.NextTransition
					2 -> ReaderPageRasterPriority.PreviousTransition
					in 3..6 -> ReaderPageRasterPriority.NextLookahead
					else -> ReaderPageRasterPriority.PreviousLookahead
				}
			)
		} + (26..76 step 2).map { pageIndex ->
			ReaderPageRasterBatchTarget(pageIndex, ReaderPageRasterPriority.CurrentChapter)
		}

		assertEquals(
			blockingPages,
			readerPageRasterBlockingTargets(targets).map { target -> target.pageIndex }
		)
		assertEquals(
			(26..76 step 2).toList(),
			readerPageRasterBackgroundTargets(targets).map { target -> target.pageIndex }
		)
	}

	@Test
	fun planRejectsAnIncompleteBlockingWindow() {
		val plan = ReaderPageRasterPreparationPlan(
			centerPageIndex = 5,
			pageCount = 20,
			layoutMode = "single",
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
			step = 1,
			currentChapterIndex = 0,
			currentChapterPageStartIndex = 0,
			currentChapterPageCount = 20,
			previousChapterPageStartIndex = -1,
			previousChapterPageCount = 0,
			nextChapterPageStartIndex = -1,
			nextChapterPageCount = 0,
			targets = listOf(
				ReaderPageRasterBatchTarget(5, ReaderPageRasterPriority.Current),
				ReaderPageRasterBatchTarget(6, ReaderPageRasterPriority.NextTransition),
				ReaderPageRasterBatchTarget(4, ReaderPageRasterPriority.PreviousTransition)
			)
		)

		assertNull(plan.blockingTargetsOrNull())
	}

	@Test
	fun currentForwardAndBackwardTargetsBecomeOrderedBackgroundBatches() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":30,"layoutMode":"spread","readerDirection":"rtl","step":2,"currentChapterIndex":2,"currentChapterPageStartIndex":6,"currentChapterPageCount":8,"previousChapterPageStartIndex":0,"previousChapterPageCount":6,"nextChapterPageStartIndex":14,"nextChapterPageCount":8},"targets":[{"pageIndex":8,"priority":"current"},{"pageIndex":12,"priority":"current-chapter"},{"pageIndex":20,"priority":"next-chapter"},{"pageIndex":18,"priority":"next-chapter-remainder"},{"pageIndex":4,"priority":"previous-chapter"},{"pageIndex":2,"priority":"previous-chapter-remainder"}]}"""
		)

		assertNotNull(plan)
		val batches = plan.adjacentChapterPrefetchChapters()
		assertEquals(
			listOf(
				ReaderPageAdjacentChapterDirection.Current,
				ReaderPageAdjacentChapterDirection.Next,
				ReaderPageAdjacentChapterDirection.Previous
			),
			batches.map { batch -> batch.identity.direction }
		)
		assertEquals(listOf(12), batches[0].targets.map { target -> target.pageIndex })
		assertEquals(listOf(20, 18), batches[1].targets.map { target -> target.pageIndex })
		assertEquals(listOf(4, 2), batches[2].targets.map { target -> target.pageIndex })
		assertEquals(listOf(2, 3, 1), batches.map { batch -> batch.identity.chapterIndex })
		assertEquals(listOf(6, 14, 0), batches.map { batch -> batch.identity.pageStartIndex })
		assertEquals(listOf(8, 16, 6), batches.map { batch -> batch.identity.pageCount })
	}

	@Test
	fun pagesBeforeTheFirstChapterRemainBackwardBackgroundWork() {
		val plan = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":4,"pageCount":12,"layoutMode":"single","readerDirection":"ltr","step":1,"currentChapterIndex":0,"currentChapterPageStartIndex":2,"currentChapterPageCount":6},"targets":[{"pageIndex":1,"priority":"previous-chapter"},{"pageIndex":0,"priority":"previous-chapter"}]}"""
		)

		assertNotNull(plan)
		val backward = plan.adjacentChapterPrefetchChapters().single()
		assertEquals(ReaderPageAdjacentChapterDirection.Previous, backward.identity.direction)
		assertEquals(0, backward.identity.chapterIndex)
		assertEquals(listOf(1, 0), backward.targets.map { target -> target.pageIndex })
	}

	@Test
	fun jsPlanBuildsBlockingWindowBeforeCurrentForwardAndBackwardBackgroundWork() {
		val source = File("src/androidMain/assets/reader/navic-reader-page-turn-preview.js").readText()
		val plan = source
			.substringAfter("function pageTurnRasterPreparationPlan(")
			.substringBefore("function pageTurnPreviewContext()")

		val nextFive = plan.indexOf("centerPageIndex + step * 5")
		val previousFive = plan.indexOf("centerPageIndex - step * 5")
		val currentChapter = plan.indexOf("'current-chapter'")
		val forward = plan.indexOf("'next-chapter'")
		val backward = plan.indexOf("'previous-chapter'")
		assertTrue(nextFive >= 0)
		assertTrue(previousFive > nextFive)
		assertTrue(currentChapter > previousFive)
		assertTrue(forward > currentChapter)
		assertTrue(backward > forward)
	}
}
