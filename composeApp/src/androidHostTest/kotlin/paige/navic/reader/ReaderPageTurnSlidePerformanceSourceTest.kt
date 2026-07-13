package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderPageTurnSlidePerformanceSourceTest {
	@Test
	fun rollingCacheRetainsFiveReusableSnapshotsAndCoalescesCaptures() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "private const val MaxCachedSnapshots = 5")
		assertContains(source, "LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>")
		assertContains(source, "inFlightSnapshotRequests")
		assertContains(source, "callbacks.add(onPrepared)")
		assertContains(source, "eldest.value.releaseCacheOwnership()")
		assertContains(source, "staleSnapshot?.releaseCacheOwnership()")
		assertContains(source, "ReaderPageTurnAnimationBitmapScale = 0.5f")
		assertFalse(source.contains("postDelayed"))
		assertFalse(source.contains("withTimeout"))
	}

	@Test
	fun rollingWindowPrioritizesCurrentAndImmediateNeighbors() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val window = source
			.substringAfter("internal fun readerPageSlideSnapshotWindow(")
			.substringBefore("private fun String?.isJavascriptTrue")

		assertContains(window, "centerPageIndex")
		assertContains(window, "centerPageIndex - step")
		assertContains(window, "centerPageIndex + step")
		assertContains(window, "centerPageIndex - (2 * step)")
		assertContains(window, "centerPageIndex + (2 * step)")
		assertContains(window, ".distinct()")
	}

	@Test
	fun routinePrewarmDoesNotDestroyThePassiveRenderer() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val finish = controller
			.substringAfter("private fun finishPrewarm()")
			.substringBefore("private fun cancelPrewarm()")
		val cancel = controller
			.substringAfter("private fun cancelPrewarm()")
			.substringBefore("private fun destroyPageTurnPreviewRenderer(")

		assertFalse(finish.contains("destroyPageTurnPreviewRenderer"))
		assertFalse(cancel.contains("destroyPageTurnPreviewRenderer"))
	}

	@Test
	fun controllerPlansFromVisualPositionAndSerializesLiveSettlement() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(controller, "ReaderPageSlideCoordinator")
		assertContains(controller, "slideCoordinator?.visualPageIndex")
		assertContains(
			controller,
			"pageTurnTransitionPlan?.('${'$'}physicalDirection', ${'$'}visualPageIndex)"
		)
		assertContains(controller, "coordinator.visualCommitted(plan.targetPageIndex)")
		assertContains(controller, "ReaderPageSlideCoordinatorEffect.SettleExact")
		assertContains(controller, "ReaderPageSlideCoordinatorEffect.RemoveFinalShield")
		assertContains(controller, "coordinator.settlementReported(")
		assertFalse(controller.contains("phase == paige.navic.reader.ReaderPageTurnPhase.Settling"))
	}

	@Test
	fun prewarmBuildsTheFiveSnapshotWindowFromTheVisualIndex() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val query = controller
			.substringAfter("private fun queryAdjacentPrewarmPlans(")
			.substringBefore("private fun expectedLayoutMode(")

		assertContains(query, "slideCoordinator?.visualPageIndex")
		assertContains(query, "pageTurnTransitionPlan?.('toward-left', center)")
		assertContains(query, "pageTurnTransitionPlan?.('toward-right', center)")
		assertContains(query, "center - step")
		assertContains(query, "center + step")
		assertContains(query, "readerPageSlideSnapshotWindow(")
		assertContains(query, "desiredTargets.mapNotNull")
	}

	@Test
	fun passivePrewarmNeverCoversAnExistingFinalSlideWithTheSettledWebView() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val attachShield = controller
			.substringAfter("private fun attachPreparationShield(")
			.substringBefore("private fun removePreparationShield(")

		assertContains(attachShield, "if (slideView != null) return")
	}

	@Test
	fun liveWebViewCaptureCannotBeMislabelledAsAnUnsettledVisualPage() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "currentCanRepresentSource: Boolean")
		assertContains(source, "cachedSnapshot(plan.sourcePageIndex, plan.kind)")
		assertContains(controller, "plan.sourcePageIndex == activePrewarmLivePageIndex")
		assertContains(controller, "plan.sourcePageIndex == slideCoordinator?.settledPageIndex")
	}
}
