package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
		assertContains(source, "readerPageTurnAnimationBitmapDimension")
		assertContains(source, "bitmapQuality")
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
	fun routineGesturesAndPrewarmShareOneSnapshotCacheEpoch() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val begin = controller
			.substringAfter("private fun begin(deltaX: Float)")
			.substringBefore("fun prewarmAdjacent()")
		val prewarm = controller
			.substringAfter("private fun startRasterBatch(")
			.substringBefore("private fun isPrewarmActive(")

		assertContains(source, "fun currentGeneration(): Long = activeGeneration")
		assertFalse(source.contains("fun beginGeneration()"))
		assertFalse(source.contains("fun cancelActivePreparation()"))
		assertContains(begin, "bundleSource.currentGeneration()")
		assertContains(prewarm, "bundleSource.currentGeneration()")
		assertFalse(
			begin.substringBefore("webView.evaluateJavascript(").contains("cancelPrewarm()"),
			"Resolving a routine gesture must not invalidate an in-flight passive snapshot"
		)
	}

	@Test
	fun gestureCancelsPassiveMutationThenUsesTheSharedMemoryOrDiskCache() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val begin = controller
			.substringAfter("private fun begin(deltaX: Float)")
			.substringBefore("fun prewarmAdjacent()")

		assertContains(begin, "bundleSource.cached(plan)")
		assertContains(begin, "if (prewarmInProgress) cancelPrewarm()")
		assertContains(begin, "prepareBundle(webView, plan, activeStateGeneration)")
		assertFalse(begin.contains("waitForActivePrewarmBundle"))
	}

	@Test
	fun passiveRasterBatchStopsBeforeACompetingGestureMutatesTheReader() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val begin = controller
			.substringAfter("private fun begin(deltaX: Float)")
			.substringBefore("fun prewarmAdjacent()")
		val cancel = controller
			.substringAfter("private fun cancelPrewarm()")
			.substringBefore("private fun destroyPageTurnPreviewRenderer(")

		assertTrue(begin.indexOf("cancelPrewarm()") < begin.indexOf("prepareBundle("))
		assertContains(cancel, "rasterBatchController.cancel()")
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
	fun prewarmBuildsTheOrderedChapterPlanFromTheVisualIndex() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val query = controller
			.substringAfter("private fun queryRasterPreparationPlan(")
			.substringBefore("private fun expectedLayoutMode(")

		assertContains(query, "slideCoordinator?.visualPageIndex")
		assertContains(query, "pageTurnRasterPreparationPlan")
		assertContains(query, "readerPageRasterPreparationPlan(encoded)")
		assertContains(query, "readerPageRasterCalibrationTargets(plan.targets)")
	}

	@Test
	fun passivePrewarmNeverCoversAnExistingFinalSlideWithTheSettledWebView() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val attachShield = controller
			.substringAfter("private fun attachPreparationShield(")
			.substringBefore("private fun removePreparationShield(")

		assertContains(attachShield, "if (overlayAttached) return")
	}

	@Test
	fun liveWebViewCaptureCannotBeMislabelledAsAnUnsettledVisualPage() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val prepareBundle = controller
			.substringAfter("private fun prepareBundle(")
			.substringBefore("private fun hydrateGestureDestination(")
		val passiveReference = controller
			.substringAfter("private fun obtainRasterReference(")
			.substringBefore("private fun isPrewarmActive(")

		assertContains(source, "currentCanRepresentSource: Boolean")
		assertContains(source, "cachedSnapshot(plan.sourcePageIndex, plan.kind)")
		assertContains(prepareBundle, "bundleSource.cacheCurrentSnapshot(plan, current)")
		assertContains(passiveReference, "cacheCurrentSnapshot(pageIndex, kind, current, generation)")
		assertFalse(passiveReference.contains("targetPageIndex"))
	}

	@Test
	fun gestureMachineDoesNotRetainTheObsoleteLiveSettlementPhase() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val stateMachine = readerCommonFile("ReaderPageTurnStateMachine.kt").readText()

		assertFalse(stateMachine.contains("Settling"))
		assertFalse(stateMachine.contains("destinationSettled"))
		assertFalse(controller.contains("markDestinationSettled"))
	}
}
