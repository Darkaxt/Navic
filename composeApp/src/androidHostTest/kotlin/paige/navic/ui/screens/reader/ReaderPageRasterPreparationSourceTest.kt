package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageRasterPreparationSourceTest {
	@Test
	fun productionAcquisitionTriggerDistinguishesColdWarmAndLiveRefill() {
		assertEquals(
			ReaderPageRasterAcquisitionTrigger.InitialPreparation,
			readerPageRasterAcquisitionTrigger(
				hasPreparedBefore = false,
				persistentRasterEntries = 0
			)
		)
		assertEquals(
			ReaderPageRasterAcquisitionTrigger.WarmReopen,
			readerPageRasterAcquisitionTrigger(
				hasPreparedBefore = false,
				persistentRasterEntries = 1
			)
		)
		assertEquals(
			ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
			readerPageRasterAcquisitionTrigger(
				hasPreparedBefore = true,
				persistentRasterEntries = 1
			)
		)
		val preparation = readerRasterPreparationSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val initializationPath = preparation
			.substringAfter("private fun initializeRasterCacheAndQueryPlan(")
			.substringBefore("private fun consumeQaDeferral(")
		val initialization = "initializeRasterCache(webView)"
		val metrics = "bundleSource.rasterCacheMetrics().diskEntries"
		assertContains(bundle, "suspend fun initializeRasterCache(webView: WebView)")
		assertContains(initializationPath, initialization)
		assertContains(initializationPath, "if (!prewarmAcquisitionTriggerClassified)")
		assertContains(initializationPath, "prewarmAcquisitionTriggerClassified = true")
		assertContains(
			preparation,
			"if (resumedDiagnostic == null) {\n\t\t\tprewarmAcquisitionTriggerClassified = false"
		)
		assertContains(preparation, "persistentRasterEntries = $metrics")
		assertTrue(
			initializationPath.indexOf(initialization) < initializationPath.indexOf(metrics),
			"Persistent cache initialization must precede warm-reopen trigger selection"
		)
		assertContains(preparation, "trigger = activeAcquisitionTrigger")
	}

	@Test
	fun prewarmReferenceReusesTheActivePhysicalLayoutBeforeRecapturing() {
		val preparation = readerRasterPreparationSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val productionPort = preparation
			.substringAfter("private class ReaderPageBundleRasterCurrentReferencePort(")
			.substringBefore("internal fun readerPageTurnCanStartPassivePrewarm(")
		val reference = preparation
			.substringAfter("private fun obtainRasterReference(")
			.substringBefore("private fun isPrewarmSessionActive(")
		val retained = "currentLayoutSnapshot(pageIndex, kind)?.let"
		val fresh = "currentReferencePort.captureFresh("

		assertContains(productionPort, "bundleSource.captureCurrentSurface(webView, generation)")
		assertContains(productionPort, "bundleSource.cacheCurrentSnapshot(pageIndex, kind, current, generation)")
		assertContains(productionPort, "snapshot.retain()")
		assertContains(bundle, "fun retainedCurrentLayoutSnapshot(")
		assertContains(bundle, "physicalLayoutAuthority?.takeIf")
		assertContains(reference, retained)
		assertContains(reference, fresh)
		assertTrue(reference.indexOf(retained) < reference.indexOf(fresh))
		assertFalse(reference.contains("retainedSnapshot(pageIndex, kind)?.let"))
	}

	@Test
	fun publicationCapacityRetriesUseTheExactPreparationListener() {
		val preparation = readerRasterPreparationSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")

		assertContains(
			preparation,
			"bundleSource.setPublicationCapacityAvailableListener(onRequestPrewarm)"
		)
		assertContains(
			preparation,
			"bundleSource.clearPublicationCapacityAvailableListener(onRequestPrewarm)"
		)
		assertContains(bundle, "publicationLedger.setCapacityAvailableListener(listener)")
		assertContains(bundle, "publicationLedger.clearCapacityAvailableListener(listener)")
	}

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
		assertContains(source, "progressCompletedOffset + completed")
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
	fun preparationShieldWaitsForSessionFencedRestorationOnCancellation() {
		val source = readerRasterPreparationSource()
		val cancellation = source.substringAfter(
			"private fun cancelPrewarm(reason: String) {"
		).substringBefore("private fun reusePreparationShield(")
		val removal = source.substringAfter(
			"private fun removePreparationShield("
		).substringBefore("private fun shieldDetail(")

		assertContains(cancellation, "rasterBatchController.cancel(")
		assertContains(cancellation, "trackVisualRestoration {")
		assertContains(cancellation, "expectedSession = cancelledSession")
		assertContains(removal, "preparationShieldSession != expectedSession")
	}

	@Test
	fun shieldCleanupIsRestorationFencedAcrossDetachAndDestroy() {
		val source = readerRasterPreparationSource()
		val attachment = source.substringAfter(
			"fun onWebViewAttachmentChanged(attached: Boolean) {"
		).substringBefore("\n\tfun onPointerInteractionChanged(")
		val teardown = source.substringAfter(
			"private val teardown = ReaderPageReaderTeardown("
		).substringBefore("\n\tprivate val memoryCallbacks")
		val destroyFence = source.substringAfter(
			"private fun fenceForDestroy() {"
		).substringBefore("\n\tsuspend fun destroyAndJoin()")

		assertContains(attachment, "if (!attached)")
		assertContains(attachment, "cancelRasterRepairs(\"webview-detached\")")
		assertContains(attachment, "deferPrewarmForWebViewDetach()")
		assertContains(teardown, "awaitVisualRestorations()")
		assertFalse(destroyFence.contains("removePreparationShield("))
		assertFalse(destroyFence.contains("removeBackgroundPrefetchShield("))
	}

	@Test
	fun preparationLifecycleLogsEveryRemovalAndInvalidationCause() {
		val source = readerRasterPreparationSource()

		assertContains(source, "cancelPrewarm(reason:")
		assertContains(source, "removePreparationShield(")
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

		assertContains(function, "if (pageIndex == null) {")
		assertContains(function, "cancelRasterRepairs(\"visual-index-cleared:")
		assertFalse(function.contains("if (currentVisualPageIndex != null)"))
		assertContains(function, "currentVisualPageIndex = null")
		assertContains(function, "beginBlockingBackgroundPrefetchSession()")
		assertContains(function, "cancelRasterRepairs(\"visual-index-changed:")
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
	fun everyForegroundPreparedPreviewIsCoveredBeforeLiveExposure() {
		val source = readerRasterPreparationSource()
		val followUp = source.substringAfter(
			"private fun startRasterFollowUp("
		).substringBefore("\n\tprivate fun startRasterBatch(")
		val batch = source.substringAfter(
			"private fun startRasterBatch("
		).substringBefore("\n\tprivate fun obtainRasterReference(")

		assertContains(followUp, "targets = followUpTargets")
		assertContains(batch, "reusePreparationShield(snapshot, session, batchLabel)")
		assertFalse(source.contains("protectForeground"))
		assertFalse(source.contains("event = \"shield-skipped\""))
	}

	@Test
	fun preparedPreviewCaptureRequiresAnExplicitStagingGuard() {
		val batch = readerRasterBatchSource()
		val contract = batch.substringAfter(
			"internal interface ReaderPageRasterBatchPort"
		).substringBefore("internal class ReaderPageRasterBatchController")
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val capture = bundle.substringAfter(
			"fun capturePreparedRasterPage("
		).substringBefore(") {")
		val exposure = bundle.substringAfter(
			"private fun capturePreparedPage("
		).substringBefore("fun cacheCurrentSnapshot(")

		assertContains(contract, "onStagingStarted: (ReaderPageSlideSnapshot) -> Unit")
		assertFalse(contract.contains("onStagingStarted: (ReaderPageSlideSnapshot) -> Unit = {}"))
		assertContains(capture, "onStagingStarted: (ReaderPageSlideSnapshot) -> Unit")
		assertFalse(capture.contains("onStagingStarted: (ReaderPageSlideSnapshot) -> Unit = {}"))
		assertTrue(
			exposure.indexOf("onStagingStarted()") <
				exposure.indexOf("exposePageTurnPreviewFinal")
		)
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
	fun singlePageRepairUsesAnIndependentShieldedBatchWithoutChangingPresentationState() {
		val source = readerRasterPreparationSource()
		val repair = source.substringAfter(
			"fun repairRasterPage("
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(source, "private val rasterRepairBatchController")
		assertContains(repair, "pageIndex !in repairPages")
		assertContains(repair, "reason=outside-prepared-window")
		assertContains(repair, "ReaderPageRasterBatchTarget(pageIndex")
		assertContains(repair, "event = \"page-repair-requested\"")
		assertContains(repair, "adjacentChapterPrefetchCoordinator.suspendForForegroundWork()")
		assertContains(repair, "onStagingStarted = { snapshot ->")
		assertContains(repair, "reusePreparationShield(")
		assertContains(repair, "removePreparationShield(")
		assertContains(repair, "rasterRepairBatchController.cancel(")
		assertContains(repair, "trackVisualRestoration {")
		assertContains(repair, "\"page-repair-completed\"")
		assertContains(repair, "\"page-repair-failed\"")
		assertContains(repair, "ReaderPageRasterRepairResult.Repaired")
		assertFalse(repair.contains("publishPreparationState("))
		assertContains(
			repair,
			"detail = \"reference-unavailable:${'$'}centerOrdinal\""
		)
		assertContains(repair, "onRequestPrewarm()")
	}

	@Test
	fun coalescedRepairFaultUpgradesTheInFlightDiagnosticRoot() {
		val source = readerRasterPreparationSource()
		val attach = source.substringAfter(
			"fun attachRasterRepairQaFault("
		).substringBefore("\n\tfun repairRasterPage(")

		assertContains(attach, "rasterRepairQaFaultCorrelations.putIfAbsent(pageIndex, correlation)")
		assertContains(attach, "rasterRepairDiagnostics[pageIndex]?.let { operation ->")
		assertContains(attach, "if (operation.qaFaultCorrelation == null)")
		assertContains(attach, "operation.copy(qaFaultCorrelation = activeCorrelation)")
	}

	@Test
	fun adjacentChapterPrefetchUsesAnIndependentIdleBatchWithoutChangingReadiness() {
		val source = readerRasterPreparationSource()
		val background = source.substringAfter(
			"private fun scheduleBackgroundPrefetch("
		).substringBefore("\n\tprivate fun logPrewarmBoundary(")

		assertContains(source, "private val rasterBackgroundBatchController")
		assertContains(source, "Looper.myQueue().addIdleHandler")
		assertContains(background, "submission.targets")
		assertContains(background, "rasterBackgroundBatchController.start(")
		assertContains(background, "event = \"background-prefetch-scheduled\"")
		assertContains(background, "event = \"background-prefetch-started\"")
		assertContains(background, "event = \"background-prefetch-progress\"")
		assertContains(background, "\"background-prefetch-completed\"")
		assertContains(background, "event = \"background-prefetch-failed\"")
		assertContains(background, "activeRasterRepairPageIndex == null")
		assertContains(background, "rasterRepairCallbacks.isEmpty()")
		assertContains(background, "showBackgroundPrefetchShield(snapshot, submission)")
		assertContains(background, "removeBackgroundPrefetchShield(submission.sessionId)")
		assertContains(background, "rasterBackgroundBatchController.cancel(")
		assertContains(background, "trackVisualRestoration {")
		assertContains(background, "removeBackgroundPrefetchShield(submission.sessionId)")
		assertFalse(background.contains("publishPreparationState("))
		assertFalse(background.contains("reusePreparationShield("))
	}

	@Test
	fun adjacentChapterShieldHasIndependentSessionFencedLeaseOwnership() {
		val source = readerRasterPreparationSource()
		val shield = source.substringAfter(
			"private fun showBackgroundPrefetchShield("
		).substringBefore("\n\tprivate fun cancelBackgroundPrefetchSubmission(")

		assertContains(source, "private var backgroundPrefetchShieldSessionId: Long?")
		assertContains(shield, "backgroundBatchSubmission != submission")
		assertContains(shield, "adjacentChapterPrefetchCoordinator.isActive(submission)")
		assertContains(shield, "snapshot.retain()")
		assertContains(shield, "currentSnapshot?.release()")
		assertContains(shield, "backgroundPrefetchShieldSessionId != sessionId")
		assertContains(shield, "snapshot?.release()")
		assertContains(shield, "isClickable = false")
		assertContains(shield, "importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO")
		assertFalse(shield.contains("publishPreparationState("))
		assertFalse(shield.contains("reusePreparationShield("))
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
