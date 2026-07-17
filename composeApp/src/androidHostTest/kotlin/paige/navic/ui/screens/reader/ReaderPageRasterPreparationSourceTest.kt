package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderPageRasterPreparationSourceTest {
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
			"fun repairRasterPage(pageIndex: Int, onComplete: (Boolean) -> Unit) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(source, "private val rasterRepairBatchController")
		assertContains(repair, "ReaderPageRasterBatchTarget(pageIndex")
		assertContains(repair, "event = \"page-repair-requested\"")
		assertContains(repair, "event = \"page-repair-completed\"")
		assertContains(repair, "event = \"page-repair-failed\"")
		assertFalse(repair.contains("publishPreparationState("))
		assertFalse(repair.contains("reusePreparationShield("))
		assertFalse(repair.contains("onRequestPrewarm()"))
	}
}

private fun readerRasterPreparationSource(): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageRasterPreparationController.android.kt"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate ReaderPageRasterPreparationController.android.kt")
}
