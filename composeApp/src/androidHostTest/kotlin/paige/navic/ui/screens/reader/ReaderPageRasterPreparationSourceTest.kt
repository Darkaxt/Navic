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
		val prewarm = preparation
			.substringAfter("fun prewarmAdjacent(): Boolean {")
			.substringBefore("private fun initializeRasterCacheAndQueryPlan(")
		val initialization = "initializeRasterCache(webView)"
		val metrics = "bundleSource.rasterCacheMetrics().diskEntries"
		assertContains(bundle, "suspend fun initializeRasterCache(webView: WebView)")
		assertContains(initializationPath, initialization)
		assertContains(initializationPath, "if (!prewarmAcquisitionTriggerClassified)")
		assertContains(initializationPath, "prewarmAcquisitionTriggerClassified = true")
		assertContains(prewarm, "if (resumedDiagnostic == null)")
		assertContains(prewarm, "prewarmAcquisitionTriggerClassified = false")
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

		assertContains(
			productionPort,
			"bundleSource.captureCurrentSurface(webView, generation, captureGeometry)"
		)
		assertContains(productionPort, "bundleSource.cacheCurrentSnapshot(pageIndex, kind, current, generation)")
		assertContains(productionPort, "snapshot.retain()")
		assertContains(bundle, "fun retainedCurrentLayoutSnapshot(")
		assertContains(bundle, "physicalLayoutAuthority?.takeIf")
		assertContains(reference, retained)
		assertContains(reference, fresh)
		assertContains(preparation, "plan.captureGeometry")
		assertContains(reference, "captureGeometry = captureGeometry")
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

		assertContains(source, "reusePreparationShield(")
		assertContains(source, "batchLabel = batchLabel")
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
	fun invalidationImmediatelyRestoresTheFullPreparationCover() {
		val source = readerRasterPreparationSource()
		val invalidation = source.substringAfter(
			"fun invalidate(reason: String, clearVisualPageIndex: Boolean = false) {"
		).substringBefore("\n\tfun invalidateCurrentVisualSnapshot(")

		assertContains(invalidation, "hasPreparedBefore = false")
		assertContains(invalidation, "durableRasterPageIndices.clear()")
		assertContains(
			invalidation,
			"publishPreparationState(ReaderPagePreparationPhase.Idle)"
		)
	}

	@Test
	fun requiredRasterHydrationMissRestoresCoverBeforePassiveCapture() {
		val batch = readerRasterBatchSource()
		val contract = batch.substringAfter(
			"internal interface ReaderPageRasterBatchPort"
		).substringBefore("internal class ReaderPageRasterBatchController")
		val hydration = batch.substringAfter(
			"private fun hydrateTarget(session: Session, targetIndex: Int)"
		).substringBefore("private fun submitMissingTargets(")
		val preparation = readerRasterPreparationSource()
		val foreground = preparation.substringAfter(
			"private fun startRasterBatch("
		).substringBefore("private fun obtainRasterReference(")

		assertContains(contract, "onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit")
		assertTrue(
			hydration.indexOf("session.onHydrationMiss(target)") <
				hydration.indexOf("session.missingTargets += target")
		)
		assertContains(foreground, "enterBlockingPreparation(\"required-cache-miss")
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
	fun exactTurnInsideDurableBlockingWindowStartsAnInvisibleWindowValidation() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(function, "requiredWindow.all(durableRasterPageIndices::contains)")
		assertContains(function, "event = \"ordinary-turn-reused\"")
		assertContains(function, "readerPageCanReusePreparedWindow(")
		assertContains(function, "onRequestPrewarm()")
		assertTrue(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = true,
				rasterRepairPending = false,
				prewarmPending = false
			)
		)
	}

	@Test
	fun exactTurnWithPendingRasterRepairUsesCenterChangeRecovery() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")
		val reuseDecision = "readerPageCanReusePreparedWindow("
		val repairCancellation = "cancelRasterRepairs(\"visual-index-changed:"

		assertContains(
			function,
			"rasterRepairPending = rasterRepairCallbacks.isNotEmpty()"
		)
		assertContains(function, repairCancellation)
		assertTrue(
			function.indexOf(reuseDecision) < function.indexOf(repairCancellation),
			"A non-reusable exact turn must fall through to center-change repair cancellation"
		)
		assertFalse(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = true,
				rasterRepairPending = true,
				prewarmPending = false
			)
		)
	}

	@Test
	fun exactTurnWithDeferredPrewarmUsesCenterChangeRecovery() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(
			function,
			"visualCenterChanging = currentVisualPageIndex != pageIndex"
		)
		assertContains(
			function,
			"prewarmPending = prewarmInProgress || deferredPrewarmSessionId != null"
		)
		assertFalse(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = true,
				rasterRepairPending = false,
				prewarmPending = true
			)
		)
	}

	@Test
	fun sameCenterExactUpdatePreservesDeferredPrewarmOwnership() {
		assertTrue(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = false,
				rasterRepairPending = false,
				prewarmPending = true
			)
		)
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
		assertContains(batch, "reusePreparationShield(")
		assertContains(batch, "onPresented = onPresented")
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

		assertContains(
			contract,
			"onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit"
		)
		assertContains(
			capture,
			"onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit"
		)
		val stagingGate = exposure.indexOf("onStagingStarted staging@")
		val presentationCheck = exposure.indexOf("!presented")
		val liveExposure = exposure.indexOf("exposePageTurnPreviewFinal")
		assertTrue(stagingGate >= 0 && stagingGate < presentationCheck)
		assertTrue(presentationCheck < liveExposure)
	}

	@Test
	fun immediateDeckCannotPublishReadyBeforeTheBlockingWindowIsDurable() {
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
		assertContains(followUp, "checkNotNull(plan.blockingTargetsOrNull())")
		assertContains(followUp, "totalRequired = blockingTargets.size")
		assertContains(finish, "hasPreparedBefore = candidateBlockingPageIndices.isNotEmpty()")
		assertContains(finish, "durableRasterPageIndices += candidateBlockingPageIndices")
	}

	@Test
	fun memoryPressureTrimsWorkingSetsWithoutInvalidatingRasterGeneration() {
		val source = readerRasterPreparationSource()
		val callbacks = source.substringAfter(
			"private val memoryCallbacks = object : ComponentCallbacks2 {"
		).substringBefore("\tinit {")

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
		assertContains(repair, "onStagingStarted = { snapshot, onPresented ->")
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
		assertContains(
			background,
			"showBackgroundPrefetchShield(snapshot, submission, onPresented)"
		)
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
		val window = readerSource("ReaderPageStaticWindowShield.android.kt")
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
		assertContains(shield, "ReaderPageStaticWindowShield(host)")
		assertContains(shield, "surfaceRectInWindow = snapshot.surfaceRectInWindow")
		assertContains(shield, "shield?.dismiss()")
		assertContains(shield, "onPresented(false)")
		assertFalse(shield.contains("host.addView(shield)"))
		assertContains(window, "WindowManager.LayoutParams.TYPE_APPLICATION_PANEL")
		assertContains(window, "WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE")
		assertContains(window, "WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE")
		assertContains(window, "windowManager.addView(imageView, params)")
		assertContains(window, "windowManager.updateViewLayout(imageView, params)")
		assertContains(window, "imageView.setImageBitmap(bitmap)")
		assertContains(window, "imageView.addOnAttachStateChangeListener(listener)")
		assertContains(window, "observer.registerFrameCommitCallback")
		assertContains(window, "ReaderPageStaticWindowShieldTimeoutMillis")
		assertContains(window, "windowManager.removeViewImmediate(imageView)")
		assertContains(window, "isClickable = false")
		assertContains(window, "importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO")
		assertTrue(
			window.indexOf("imageView.setImageBitmap(bitmap)") <
				window.indexOf("awaitCommittedWindowFrame(request)")
		)
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
