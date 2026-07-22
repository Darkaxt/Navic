package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderPageRasterPreparationSourceTest {
	@Test
	fun batchProgressAdvancesOnlyFromDurablePublicationCallbacks() {
		val source = readerRasterBatchSource()
		val hydration = source.substringAfter(
			"private fun hydrateTarget("
		).substringBefore("private fun submitMissingTargets(")
		val capture = source.substringAfter(
			"private fun captureReadyItem("
		).substringBefore("private fun advancePageTurnPreviewBatch(")

		assertContains(hydration, "bundleSource.ensurePersistentSnapshot(")
		assertContains(hydration, "recordDurability(session, target, persisted)")
		assertContains(capture, "onCaptured = captured@{ persisted ->")
		assertContains(capture, "recordDurability(session, target, persisted)")
		assertContains(source, "stage = \"persistent-publication\"")
		assertContains(source, "reason = \"durable-write-failed\"")
		assertContains(source, "session.durabilityGate.retryPageIndices()")
		assertContains(source, "target.pageIndex in candidate.retryPageIndices")
		assertContains(source, "progressCompletedOffset + decision.completed")
		assertFalse(source.contains("private fun markCompleted("))
	}

	@Test
	fun preparationShieldIsReusedWithinOneRasterSession() {
		val source = readerRasterPreparationSource()

		assertContains(source, "reusePreparationShield(snapshot, session, batchLabel)")
		assertContains(source, "event = \"shield-reused\"")
		assertFalse(source.contains("private fun attachPreparationShield(snapshot: ReaderPageSlideSnapshot) {\n\t\tremovePreparationShield()"))
	}

	@Test
	fun preparationLifecycleLogsEveryRemovalAndInvalidationCause() {
		val source = readerRasterPreparationSource()

		assertContains(source, "cancelPrewarm(reason:")
		assertContains(source, "removePreparationShield(reason:")
		assertContains(source, "event = \"invalidated\"")
		assertContains(source, "\"shield-attached\"")
		assertContains(source, "event = \"shield-removed\"")
		assertContains(source, "event = \"session-finished\"")
	}

	@Test
	fun visualPageMovesRetainTheExistingRasterGeneration() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(function, "cancelPrewarm(reason = \"visual-index-changed:")
		assertContains(function, "currentVisualPageIndex = pageIndex")
		assertFalse(function.contains("bundleSource.invalidate("))
	}

	@Test
	fun exactTurnInsidePreparedChapterDoesNotRestartPassiveCapture() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(function, "preparedChapterRange?.contains(pageIndex) == true")
		assertContains(function, "event = \"ordinary-turn-reused\"")
		assertContains(function, "if (reason == \"page-turn:exact\"")
	}

	@Test
	fun passiveFollowUpPrewarmNeverCoversTheVisibleReader() {
		val source = readerRasterPreparationSource()
		val followUp = source.substringAfter(
			"private fun startRasterFollowUp("
		).substringBefore("\n\tprivate fun startRasterBatch(")

		assertContains(followUp, "protectForeground = false")
		assertContains(source, "if (protectForeground) {")
		assertContains(source, "event = \"shield-skipped\"")
	}

	@Test
	fun immediateDeckCannotPublishReadyBeforeTheCurrentChapterIsComplete() {
		val source = readerRasterPreparationSource()
		val initialDeck = source.substringAfter(
			"private fun startRasterCalibration("
		).substringBefore("\n\tprivate fun startRasterFollowUp(")
		val followUp = source.substringAfter(
			"private fun startRasterFollowUp("
		).substringBefore("\n\tprivate fun startRasterBatch(")
		val batch = source.substringAfter(
			"private fun startRasterBatch("
		).substringBefore("\n\tprivate fun obtainRasterReference(")
		val finish = source.substringAfter(
			"private fun finishPrewarm("
		).substringBefore("\n\tprivate fun failPreparation(")

		assertFalse(initialDeck.contains("hasPreparedBefore ="))
		assertFalse(batch.contains("hasPreparedBefore ="))
		assertContains(followUp, "readerPageRasterBlockingTargets(plan.targets)")
		assertContains(followUp, "totalRequired = blockingTargets.size")
		assertContains(finish, "hasPreparedBefore = rasterInteractiveRequired > 0 || hasPreparedBefore")
	}

	@Test
	fun memoryPressureTrimsWorkingSetsWithoutInvalidatingRasterGeneration() {
		val source = readerRasterPreparationSource()
		val callbacks = source.substringAfter(
			"private val memoryCallbacks = object : ComponentCallbacks2 {"
		).substringBefore("\n\t}\n\n\tinit {")

		assertContains(callbacks, "bundleSource.trimMemory(")
		assertFalse(callbacks.contains("invalidate("))
		assertFalse(callbacks.contains("bundleSource.invalidate("))
	}

	@Test
	fun singlePageRepairUsesAnIndependentBackgroundBatchWithoutChangingPresentationState() {
		val source = readerRasterPreparationSource()
		val repair = source.substringAfter(
			"fun repairRasterPage("
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(source, "private val rasterRepairBatchController")
		assertContains(repair, "pageIndex !in repairPages")
		assertContains(repair, "reason=outside-prepared-window")
		assertContains(repair, "ReaderPageRasterBatchTarget(pageIndex")
		assertContains(repair, "event = \"page-repair-requested\"")
		assertContains(repair, "cancelBackgroundPrefetch(\"page-repair\")")
		assertContains(repair, "\"page-repair-completed\"")
		assertContains(repair, "\"page-repair-failed\"")
		assertContains(repair, "ReaderPageRasterRepairResult.Repaired")
		assertFalse(repair.contains("publishPreparationState("))
		assertFalse(repair.contains("reusePreparationShield("))
		assertFalse(repair.contains("onRequestPrewarm()"))
	}

	@Test
	fun adjacentChapterPrefetchUsesAnIndependentIdleBatchWithoutChangingReadiness() {
		val source = readerRasterPreparationSource()
		val background = source.substringAfter(
			"private fun scheduleBackgroundPrefetch("
		).substringBefore("\n\tprivate fun cancelBackgroundPrefetch(")

		assertContains(source, "private val rasterBackgroundBatchController")
		assertContains(source, "Looper.myQueue().addIdleHandler")
		assertContains(background, "readerPageRasterBackgroundTargets(")
		assertContains(background, "rasterBackgroundBatchController.start(")
		assertContains(background, "event = \"background-prefetch-scheduled\"")
		assertContains(background, "event = \"background-prefetch-started\"")
		assertContains(background, "event = \"background-prefetch-progress\"")
		assertContains(background, "event = \"background-prefetch-completed\"")
		assertContains(background, "event = \"background-prefetch-failed\"")
		assertContains(background, "activeRasterRepairPageIndex == null")
		assertContains(background, "rasterRepairCallbacks.isEmpty()")
		assertFalse(background.contains("publishPreparationState("))
		assertFalse(background.contains("reusePreparationShield("))
	}
}

private fun readerRasterBatchSource(): String = readerSource(
	"ReaderPageRasterBatchController.android.kt"
)

private fun readerRasterPreparationSource(): String = readerSource(
	"ReaderPageRasterPreparationController.android.kt"
)

private fun readerSource(fileName: String): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate $fileName")
}
