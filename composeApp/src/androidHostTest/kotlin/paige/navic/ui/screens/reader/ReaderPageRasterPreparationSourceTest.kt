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
	fun batchProgressAdvancesOnlyFromVerifiedHydrationOrPublication() {
		val source = readerRasterBatchSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val hydration = source.substringAfter(
			"private fun hydrateTarget("
		).substringBefore("private fun submitMissingTargets(")
		val capture = source.substringAfter(
			"private fun captureReadyItem("
		).substringBefore("private fun advancePageTurnPreviewBatch(")

		assertContains(hydration, "bundleSource.hydrateSnapshotWithDurability(")
		assertContains(
			hydration,
			"ReaderPageRasterHydrationDurability.PersistentStoreVerified"
		)
		assertContains(
			hydration,
			"ReaderPageRasterPublicationResult.Durable"
		)
		assertContains(hydration, "bundleSource.ensurePersistentSnapshot(")
		assertContains(hydration, "recordDurability(session, target, publicationResult)")
		val ensurePersistent = bundle.substringAfter(
			"fun ensurePersistentSnapshot("
		).substringBefore("private fun cacheSnapshot(")
		assertContains(ensurePersistent, "persistCachedSnapshot(")
		assertContains(ensurePersistent, "isStillCurrent = isStillCurrent")
		val preparedCapture = bundle.substringAfter(
			"fun capturePreparedRasterPage("
		).substringBefore("private fun capturePreparedPage(")
		assertContains(preparedCapture, "persistCachedSnapshot(")
		assertContains(preparedCapture, "isStillCurrent = isStillCurrent")
		assertContains(capture, "onCaptured = captured@{ publicationResult ->")
		assertContains(capture, "recordDurability(session, target, publicationResult)")
		assertContains(source, "ReaderPageRasterPublicationResult.CapacityReached")
		assertContains(source, "ReaderPageRasterCapacityPolicy.StopBackgroundRefill")
		assertContains(source, "stage = \"persistent-publication\"")
		assertContains(source, "reason = \"durable-write-failed\"")
		assertContains(source, "session.durabilityGate.retryPageIndices()")
		assertContains(source, "target.pageIndex in candidate.retryPageIndices")
		assertContains(source, "progressCompletedOffset + completed")
		assertContains(bundle, "publicationCompletionResults[request] = publicationResult")
		assertContains(bundle, "ReaderPageRasterWriteFailureReason.DiskCapacity")
		assertFalse(source.contains("private fun markCompleted("))
	}

	@Test
	fun backgroundRefillReportsDiskCapacityAsABoundedCompletion() {
		val source = readerRasterPreparationSource()
		val background = source.substringAfter(
			"private fun startBackgroundPrefetch("
		).substringBefore("private fun isBackgroundPrefetchActive(")

		assertContains(
			background,
			"capacityPolicy = ReaderPageRasterCapacityPolicy.StopBackgroundRefill"
		)
		assertContains(
			background,
			"is ReaderPageRasterBatchOutcome.CapacityReached ->"
		)
		assertContains(
			background,
			"ReaderPagePrefetchDiagnosticState.CapacityReached"
		)
		assertContains(background, "\"background-prefetch-capacity-reached\"")
	}

	@Test
	fun staticRasterShieldPredicateFailsClosedForEveryOwnedAttachmentState() {
		val source = readerRasterPreparationSource()
		val predicate = source.substringAfter(
			"internal fun hasStaticRasterShieldOwnership()"
		).substringBefore("private fun trackVisualRestoration(")

		listOf(
			"preparationShield != null",
			"preparationShieldSnapshot != null",
			"preparationShieldSession != null",
			"preparationShieldBatchLabel != null",
			"activeRasterRepairShieldSession != null",
			"backgroundPrefetchShield != null",
			"backgroundPrefetchShieldSnapshot != null",
			"backgroundPrefetchShieldSessionId != null"
		).forEach { ownership -> assertContains(predicate, ownership) }
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

		assertContains(cancellation, "rasterBatchController.cancel { result ->")
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
			"closeRendererAndAdapter = {"
		).substringBefore("closeBundleOwners = closeBundleOwners")
		val destroyFence = source.substringAfter(
			"private fun fenceForDestroy() {"
		).substringBefore("\n\tsuspend fun destroyAndJoin()")

		assertContains(attachment, "if (!attached)")
		assertContains(attachment, "cancelRasterRepairs(\"webview-detached\")")
		assertContains(attachment, "deferPrewarmForWebViewDetach()")
		assertContains(teardown, "awaitVisualRestorations()")
		assertTrue(
			teardown.indexOf("awaitVisualRestorations()") <
				teardown.indexOf("removePreparationShield(")
		)
		assertTrue(
			teardown.indexOf("awaitVisualRestorations()") <
				teardown.indexOf("removeBackgroundPrefetchShield()")
		)
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
	fun cancelledWebViewRestorationFencesTheNextPreparation() {
		val preparation = readerRasterPreparationSource()
		val prewarm = preparation.substringAfter(
			"fun prewarmAdjacent(): Boolean {"
		).substringBefore("private fun initializeRasterCacheAndQueryPlan(")
		val restoration = preparation.substringAfter(
			"private fun trackVisualRestoration("
		).substringBefore("private fun deferPreparationForVisualRestoration()")
		val recovery = preparation.substringAfter(
			"private fun restoreTimedOutVisualComposition()"
		).substringBefore("private val teardown")
		val batch = readerRasterBatchSource()
		val cancellation = batch.substringAfter(
			"override fun restoreLiveComposition("
		).substringBefore("private fun startAcquisition(")

		val restorationFence =
			"if (deferPreparationForVisualRestoration()) return true"
		assertContains(prewarm, restorationFence)
		assertTrue(
			prewarm.indexOf(restorationFence) <
				prewarm.indexOf("val webView = webViewProvider()")
		)
		val prewarmCancellation = prewarm.indexOf("beginBlockingBackgroundPrefetchSession()")
		val prewarmPostCancellationFence = prewarm.indexOf(
			restorationFence,
			prewarmCancellation + 1
		)
		assertTrue(
			prewarmCancellation >= 0 &&
				prewarmPostCancellationFence > prewarmCancellation &&
				prewarmPostCancellationFence < prewarm.indexOf("cancelRasterRepairs(")
		)
		val repair = preparation.substringAfter(
			"private fun startNextRasterRepair() {"
		).substringBefore("private fun deferRasterRepair(")
		val repairCancellation = repair.indexOf(
			"adjacentChapterPrefetchCoordinator.suspendForForegroundWork()"
		)
		val repairFence = "if (deferPreparationForVisualRestoration()) return"
		val repairPostCancellationFence = repair.indexOf(
			repairFence,
			repairCancellation + 1
		)
		assertTrue(
			repairCancellation >= 0 &&
				repairPostCancellationFence > repairCancellation &&
				repairPostCancellationFence <
				repair.indexOf("rasterRepairBatchController.start(")
		)
		val synchronization = preparation.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("fun attachRasterRepairQaFault(")
		val centerChange = synchronization.substringAfter(
			"beginBlockingBackgroundPrefetchSession()"
		)
		assertTrue(
			centerChange.indexOf("cancelPrewarm(") <
				centerChange.indexOf("deferPreparationForVisualRestoration()") &&
				centerChange.indexOf("deferPreparationForVisualRestoration()") <
				centerChange.indexOf("onRequestPrewarm()")
		)
		assertContains(restoration, "if (pendingVisualRestorations.isEmpty() && !destroyed)")
		assertContains(restoration, "onRequestPrewarm()")
		assertContains(restoration, "if (restoration.canRevealContent || destroyed)")
		assertContains(
			restoration,
			"restoration == ReaderPageRasterCancellationRestoration.TimedOut"
		)
		assertContains(
			restoration,
			"timedOutPreparationShieldSession = preparationShieldSession"
		)
		assertContains(
			restoration,
			"timedOutBackgroundPrefetchShieldSessionId = backgroundPrefetchShieldSessionId"
		)
		assertContains(
			restoration,
			"timedOutVisualRestorationRecoveryRequired = true"
		)
		assertContains(restoration, "beginBlockingBackgroundPrefetchSession()")
		assertTrue(
			restoration.indexOf("beginBlockingBackgroundPrefetchSession()") <
				restoration.indexOf("onRequestPrewarm()")
		)
		assertContains(restoration, "enterBlockingPreparation(\"visual-restoration-timeout\")")
		assertContains(restoration, "resumePreparationAfterVisualRestoration = true")
		assertContains(prewarm, "if (timedOutVisualRestorationRecoveryRequired)")
		assertTrue(
			prewarm.indexOf("if (timedOutVisualRestorationRecoveryRequired)") <
				prewarm.indexOf("if (prewarmInProgress)")
		)
		assertContains(recovery, "rasterBatchController.restoreLiveComposition(webView)")
		assertContains(recovery, "if (restoration.canRevealContent)")
		assertContains(recovery, "timedOutVisualRestorationRecoveryRequired = false")
		assertContains(recovery, "removePreparationShield(")
		assertContains(recovery, "removeBackgroundPrefetchShield(backgroundSession)")
		assertContains(recovery, "ReaderPagePreparationPhase.Failed")
		assertContains(
			cancellation,
			"completeRestoration(ReaderPageRasterCancellationRestoration.TimedOut)"
		)
		assertContains(
			cancellation,
			"completeRestoration(ReaderPageRasterCancellationRestoration.Detached)"
		)
		assertContains(
			cancellation,
			"ReaderPageRasterCancellationRestoration.Restored"
		)
		assertContains(batch, "override fun restoreLiveComposition(")
		assertContains(cancellation, "restorePageTurnLiveComposition?.()")
		assertContains(cancellation, "removeCallbacks(restorationTimeout)")
		assertContains(cancellation, "webView.postDelayed(")
		assertContains(
			cancellation,
			"ReaderPageRasterCancellationRestorationTimeoutMillis"
		)
		assertTrue(
			cancellation.indexOf("webView.postDelayed(") <
				cancellation.indexOf("webView.evaluateJavascript(javascript)")
		)
	}

	@Test
	fun visualRestorationResumesOnlyDeferredEligibleWork() {
		val source = readerRasterPreparationSource()
		val restoration = source.substringAfter(
			"private fun trackVisualRestoration("
		).substringBefore("private fun deferPreparationForVisualRestoration()")
		val pointer = source.substringAfter(
			"fun onPointerInteractionChanged(active: Boolean) {"
		).substringBefore("fun cancelAllDeferredRetries()")
		val background = source.substringAfter(
			"private fun startBackgroundPrefetch("
		).substringBefore("private fun isBackgroundPrefetchActive(")
		val cancellation = source.substringAfter(
			"private fun cancelBackgroundPrefetchSubmission("
		).substringBefore("private fun cancelBackgroundPrefetch(reason: String)")

		assertContains(source, "private var resumePreparationAfterVisualRestoration = false")
		assertContains(source, "private var pointerInteractionActive = false")
		assertContains(restoration, "if (resumePreparationAfterVisualRestoration) {")
		assertContains(restoration, "!pointerInteractionActive")
		assertContains(restoration, "resumePreparationAfterVisualRestoration = false")
		assertContains(restoration, "onRequestPrewarm()")
		assertContains(restoration, "} else {")
		assertContains(restoration, "resumeDeferredBackgroundPrefetchStart()")
		assertTrue(
			restoration.indexOf("onRequestPrewarm()") <
				restoration.indexOf("resumeDeferredBackgroundPrefetchStart()"),
			"Pending foreground restoration demand must suppress background resumption"
		)
		assertContains(pointer, "pointerInteractionActive = active")
		assertContains(pointer, "pendingVisualRestorations.isEmpty()")
		assertContains(background, "pendingVisualRestorations.isNotEmpty()")
		assertTrue(
			background.indexOf("pendingVisualRestorations.isNotEmpty()") <
				background.indexOf("retainedSnapshot(")
		)
		assertContains(background, "deferredBackgroundPrefetchStart =")
		assertContains(cancellation, "deferredBackgroundPrefetchStart")
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
		assertContains(batch, "activePrewarmPassiveLease == leaseSession")
		assertContains(batch, "onPresented(false)")
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
	fun preparedPreviewReceiptIsConfirmedOnlyAfterTheExposedFrameFence() {
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val preparedPage = bundle.substringAfter(
			"private fun capturePreparedPage("
		).substringBefore("fun cacheCurrentSnapshot(")
		val frameFence = preparedPage.indexOf("webView.postVisualStateCallback(")
		val frame = preparedPage.indexOf("webView.postOnAnimation")
		val confirmation = preparedPage.indexOf(
			"confirmPageTurnPreviewPresentation"
		)
		val capture = preparedPage.indexOf("capturePreparedSurface(")

		assertTrue(frameFence >= 0 && frameFence < frame)
		assertTrue(frame < confirmation)
		assertTrue(confirmation < capture)
	}

	@Test
	fun preparedRasterCaptureUsesPresentedSurfaceWithoutTheDivergentCompositeRoute() {
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val preparedSurface = bundle.substringAfter(
			"private fun capturePreparedSurface("
		).substringBefore("private fun captureCompositedSurface(")
		val preparedPage = bundle.substringAfter(
			"private fun capturePreparedPage("
		).substringBefore("fun cacheCurrentSnapshot(")
		val captureCallback = preparedPage.indexOf("capturePreparedSurface(")
		val restore = preparedPage.indexOf("restoreLiveComposition(", captureCallback)

		assertContains(preparedSurface, "bitmapSource.capturePresentedSurface(")
		assertFalse(preparedSurface.contains("captureCompositedSurface("))
		assertContains(preparedPage, "ReaderPageTurnPresentationTarget.Preview(")
		assertContains(preparedPage, "previewGeneration = previewGeneration")
		assertTrue(captureCallback >= 0 && captureCallback < restore)
	}

	@Test
	fun rejectedPreparedSurfaceRepollsOnlyWhenJsRestartedTheSameBatchItem() {
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val capture = bundle.substringAfter(
			"fun capturePreparedRasterPage("
		).substringBefore("fun cacheCurrentSnapshot(")
		val rejection = capture.indexOf("captured == null")
		val failure = capture.indexOf("onCaptureFailed()", rejection)
		val publication = capture.indexOf("putSnapshot(")
		val controllerSource = readerRasterBatchSource()
		val controller = controllerSource.substringAfter(
			"private fun captureReadyItem("
		).substringBefore("private fun advancePageTurnPreviewBatch(")
		val failedCallback = controller.substringAfter(
			"onCaptureFailed = captureFailed@{"
		).substringBefore("onCaptured = captured@{")
		val repoll = controllerSource.substringAfter(
			"private fun repollInvalidatedBatchItem("
		).substringBefore("private fun advancePageTurnPreviewBatch(")

		assertTrue(rejection >= 0 && rejection < failure)
		assertTrue(failure < publication)
		assertContains(
			capture.substring(rejection, publication),
			"return@capturePreparedPage"
		)
		assertContains(
			failedCallback,
			"repollInvalidatedBatchItem(session, pageIndex, previewGeneration)"
		)
		assertContains(failedCallback, "ReaderPageRasterAcquisitionResult.Failed")
		assertContains(failedCallback, "prepared-raster-capture-failed")
		assertContains(repoll, "state != null")
		assertContains(
			repoll,
			"state.optString(\"status\") in setOf(\"preparing\", \"ready\")"
		)
		assertContains(repoll, "state.optInt(\"pageIndex\", -1) == pageIndex")
		assertContains(repoll, "state.optLong(\"generation\", -1L) > previewGeneration")
		assertContains(repoll, "pollBatchState(session)")
		assertContains(repoll, "onNotRestarted()")
		assertFalse(failedCallback.contains("advancePageTurnPreviewBatch("))
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
	fun persistenceRetryCorrelationStartsOnlyAfterLedgerAdmission() {
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val publication = bundle.substringAfter(
			"val persistenceAttemptId = ReaderPagePersistenceAttemptId("
		).substringBefore("publicationValueTransferred = true")
		val registration = publication.indexOf("val registration = publicationLedger.begin(")
		val correlation = publication.indexOf(
			"readerPageRasterPublicationRetryCorrelation("
		)

		assertTrue(registration >= 0 && registration < correlation)
		assertFalse(
			publication.contains(
				"persistenceRetryCorrelations[key.digest]?.withRelation("
			)
		)
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
		assertContains(repair, "rasterRepairBatchController.cancel { result ->")
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
		assertContains(background, "showBackgroundPrefetchShield(")
		assertContains(background, "activeBackgroundPassiveLease == leaseSession")
		assertContains(background, "removeBackgroundPrefetchShield(submission.sessionId)")
		assertContains(background, "rasterBackgroundBatchController.cancel { result ->")
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

	@Test
	fun applicationPanelShieldPreservesCoverAndProgressComposition() {
		val preparation = readerRasterPreparationSource()
		val host = readerSource("KomikkuReaderNativeFrameHost.android.kt")
		val window = readerSource("ReaderPageStaticWindowShield.android.kt")
		val preservationArgument =
			"preserveCurrentPresentation = shouldPreserveCurrentPresentation()"

		assertContains(preparation, "private val shouldPreserveCurrentPresentation")
		assertEquals(
			4,
			Regex(Regex.escape(preservationArgument)).findAll(preparation).count()
		)
		assertContains(host, "shouldPreserveCurrentPresentation = {")
		assertContains(host, "shellCoverVisible ||")
		assertContains(host, "latestRasterPreparationState.presentation ==")
		assertContains(host, "ReaderPagePreparationPresentation.Cover")
		assertContains(window, "preserveCurrentPresentation: Boolean = false")
		assertContains(window, "captureCurrentPresentation(")
		assertContains(window, "val presentationRoot = host.rootView")
		assertContains(window, "presentationRoot.draw(canvas)")
		assertFalse(window.contains("host.draw(canvas)"))
		assertContains(window, "private var ownedBitmap: Bitmap? = null")
		assertContains(window, "ownedBitmap?.recycle()")
		assertContains(preparation, "private var timedOutPreparationShieldSession: Long? = null")
		assertContains(
			preparation,
			"private var timedOutBackgroundPrefetchShieldSessionId: Long? = null"
		)
		val handoff = preparation.substringAfter(
			"private fun completePreparationShieldPresentation("
		).substringBefore("private fun removePreparationShield(")
		assertContains(handoff, "timedOutPreparationShieldSession = null")
		assertContains(handoff, "timedOutBackgroundPrefetchShieldSessionId")
		assertContains(handoff, "removeBackgroundPrefetchShield(timedOutBackgroundSession)")
	}
	@Test
	fun passivePersistenceRechecksExactLeaseCurrentnessBeforePublication() {
		val batch = readerRasterBatchSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val hydration = batch.substringAfter(
			"private fun hydrateTarget(session: Session, targetIndex: Int)"
		).substringBefore("private fun submitMissingTargets(")
		val ensure = bundle.substringAfter(
			"fun ensurePersistentSnapshot("
		).substringBefore("private fun cacheSnapshot(")
		val schedule = bundle.substringAfter(
			"private fun schedulePersistentSnapshot("
		).substringBefore("private fun scheduleRasterPublication(")

		assertContains(hydration, "isStillCurrent = { isSessionActive(session) }")
		assertContains(ensure, "isStillCurrent: () -> Boolean")
		assertContains(ensure, "isStillCurrent = isStillCurrent")
		assertContains(schedule, "runCatching(isStillCurrent).getOrDefault(false)")
		assertTrue(
			schedule.lastIndexOf("runCatching(isStillCurrent).getOrDefault(false)") <
				schedule.indexOf("publicationLedger.begin("),
			"Lease currentness must be rechecked before publication admission"
		)
	}

	@Test
	fun everyPassiveRasterPathUsesTheSharedAuthoritativeLeaseHelper() {
		val preparation = readerRasterPreparationSource()
		val batch = readerRasterBatchSource()
		val repair = preparation.substringAfter(
			"private fun startNextRasterRepair() {"
		).substringBefore("private fun deferRasterRepair(")
		val prewarm = preparation.substringAfter(
			"fun prewarmAdjacent(): Boolean {"
		).substringBefore("private fun initializeRasterCacheAndQueryPlan(")
		val background = preparation.substringAfter(
			"private fun startBackgroundPrefetch("
		).substringBefore("private fun isBackgroundPrefetchActive(")
		val acquisition = preparation.substringAfter(
			"private fun acquirePassiveRasterLease("
		).substringBefore("private fun trackVisualRestoration(")

		assertContains(
			preparation,
			"private val foregroundWebViewOwnership: ReaderForegroundWebViewOwnership ="
		)
		assertContains(preparation, "ReaderForegroundWebViewOwnership(),")
		assertContains(acquisition, "foregroundWebViewOwnership.tryAcquirePassive(")
		assertContains(acquisition, "ReaderPageRasterCancellationJoin(")
		assertContains(acquisition, "rasterBatchController.cancel(")
		assertContains(acquisition, "rasterRepairBatchController.cancel(")
		assertContains(acquisition, "rasterBackgroundBatchController.cancel(")
		assertContains(repair, "acquirePassiveRasterLease(")
		assertContains(prewarm, "acquirePassiveRasterLease(")
		assertContains(background, "acquirePassiveRasterLease(")
		assertFalse(preparation.contains("visualCommitPending = false"))
		assertContains(batch, "mutationGeneration: ReaderForegroundWebViewMutationGeneration")
		assertContains(batch, "isStillCurrent: () -> Boolean")
		assertContains(batch, "runCatching(session.isStillCurrent).getOrDefault(false)")
	}

	@Test
	fun deniedPassiveAdmissionRemainsTypedDeferredWithoutStartingABatch() {
		val preparation = readerRasterPreparationSource()
		val prewarm = preparation.substringAfter(
			"fun prewarmAdjacent(): Boolean {"
		).substringBefore("private fun initializeRasterCacheAndQueryPlan(")
		val repair = preparation.substringAfter(
			"private fun startNextRasterRepair() {"
		).substringBefore("private fun deferRasterRepair(")
		val background = preparation.substringAfter(
			"private fun startBackgroundPrefetch("
		).substringBefore("private fun isBackgroundPrefetchActive(")

		assertContains(prewarm, "stage = \"passive-ownership\"")
		assertContains(repair, "detail = \"passive-ownership-unavailable\"")
		assertContains(background, "deferredBackgroundPrefetchStart")
		assertFalse(prewarm.contains("while ("))
		assertFalse(repair.contains("while ("))
		assertFalse(background.contains("while ("))
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
