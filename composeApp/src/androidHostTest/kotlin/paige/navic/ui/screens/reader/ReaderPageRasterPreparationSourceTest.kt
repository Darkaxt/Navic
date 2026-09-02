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
		assertContains(hydration, "recordDurability(session, target, publicationCompletion)")
		val ensurePersistent = bundle.substringAfter(
			"fun ensurePersistentSnapshot("
		).substringBefore("private fun cacheSnapshot(")
		assertContains(ensurePersistent, "persistCachedSnapshot(")
		assertContains(ensurePersistent, "isStillCurrent = isStillCurrent")
		assertContains(capture, "onCaptured = captured@{ publicationCompletion ->")
		assertContains(capture, "recordDurability(session, target, publicationCompletion)")
		assertContains(source, "ReaderPageRasterPublicationResult.CapacityReached")
		assertContains(source, "ReaderPageRasterCapacityPolicy.StopBackgroundRefill")
		assertContains(source, "stage = \"persistent-publication\"")
		assertContains(source, "reason = \"durable-write-failed\"")
		assertContains(source, "session.durabilityGate.retryPageIndices()")
		assertContains(source, "target.pageIndex in candidate.retryPageIndices")
		assertContains(source, "progressCompletedOffset + completed")
		assertContains(bundle, "publicationCompletionResults[request] = publicationCompletion")
		assertContains(bundle, "ReaderPageRasterWriteFailureReason.DiskCapacity")
		assertFalse(source.contains("private fun markCompleted("))
	}

	@Test
	fun passiveResolutionFallsBackOnlyForUntypedPublicationFailure() {
		val adapter = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val resolve = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "private fun resolveTarget(",
			endDelimiter = "private fun captureTarget("
		)
		val failedBranch = resolve.substringAfter(
			"ReaderPageRasterPublicationResult.Failed -> {"
		).substringBefore("\n\t\t\tnull ->")

		assertContains(failedBranch, "val writeFailureReason = completion.writeFailureReason")
		assertContains(failedBranch, "if (writeFailureReason == null)")
		assertContains(failedBranch, "captureTarget(batch, target, inputs)")
		assertContains(failedBranch, "finish(")
		assertContains(failedBranch, "persistentWriteFailureReason = writeFailureReason")
		assertContains(resolve, "null -> captureTarget(batch, target, inputs)")
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
	fun staticRasterShieldPredicateExcludesIsolatedPassiveRepairOwnership() {
		val source = readerRasterPreparationSource()
		val predicate = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "internal fun hasStaticRasterShieldOwnership()",
			endDelimiter = "\n\n\tprivate "
		)

		listOf(
			"preparationShield != null",
			"preparationShieldSnapshot != null",
			"preparationShieldSession != null",
			"preparationShieldBatchLabel != null",
			"backgroundPrefetchShield != null",
			"backgroundPrefetchShieldSnapshot != null",
			"backgroundPrefetchShieldSessionId != null"
		).forEach { ownership -> assertContains(predicate, ownership) }
		assertFalse(predicate.contains("activeRasterRepairShieldSession"))
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
	fun passivePrewarmCancellationDoesNotWaitForForegroundRestoration() {
		val source = readerRasterPreparationSource()
		val cancellation = source.substringAfter(
			"private fun cancelPrewarm(reason: String) {"
		).substringBefore("private fun reusePreparationShield(")

		assertContains(cancellation, "passiveRasterPreparationPortProvider()?.cancel()")
		assertContains(cancellation, "removePreparationShield(")
		assertFalse(cancellation.contains("rasterBatchController.cancel"))
		assertFalse(cancellation.contains("trackVisualRestoration("))
		assertFalse(cancellation.contains("restoreLiveComposition("))
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
	fun hydrationMissFallsThroughToPassiveCaptureWithoutChangingPresentationMode() {
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
		assertContains(foreground, "onHydrationMiss = {}")
		assertFalse(foreground.contains("enterBlockingPreparation(\"required-cache-miss"))
	}

	@Test
	fun isolatedPassiveAvailabilityResumesDeferredWorkWithoutForegroundOwnership() {
		val source = readerRasterPreparationSource()
		val passiveAvailable = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "fun onPassiveRasterPreparationAvailable() {",
			endDelimiter = "fun onRasterProfileEpochChanged("
		)

		assertTrue(passiveAvailable.isNotBlank(), "The passive host needs its own availability edge")
		assertContains(passiveAvailable, "passivePrewarmDeferral")
		assertContains(passiveAvailable, "deferredRasterRepairPageIndex")
		assertContains(passiveAvailable, "resumeDeferredBackgroundPrefetchStart()")
		assertContains(passiveAvailable, "adjacentChapterPrefetchCoordinator.onPassiveAvailable()")
		assertFalse(passiveAvailable.contains("foregroundWebViewOwnership"))
		assertFalse(passiveAvailable.contains("canAcquirePassive("))
		assertFalse(source.contains("fun onForegroundWebViewPassiveAvailable()"))
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
	fun passivePrewarmCannotRequestForegroundPreviewCoverage() {
		val source = readerRasterPreparationSource()
		val followUp = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterFollowUp(",
			endDelimiter = "private fun startRasterBatch("
		)
		val batch = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)

		assertContains(followUp, "targets = followUpTargets")
		assertContains(batch, "passiveRasterPreparationPort.start(")
		listOf(
			"reusePreparationShield(",
			"activePrewarmPassiveLease",
			"onStagingStarted",
			"ReaderForegroundWebViewMutationGeneration",
			"rasterBatchController.start(",
			"restoreLiveComposition("
		).forEach { forbidden -> assertFalse(batch.contains(forbidden), forbidden) }
	}

	@Test
	fun immediateDeckCannotPublishReadyBeforeTheCurrentChapterIsDurable() {
		val source = readerRasterPreparationSource()
		val initialDeck = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterCalibration(",
			endDelimiter = "private fun startRasterFollowUp("
		)
		val followUp = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterFollowUp(",
			endDelimiter = "private fun startRasterBatch("
		)
		val batch = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)
		val finish = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun finishPrewarm(",
			endDelimiter = "private fun deferPrewarm("
		)

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
	fun retryRetiresFailedWorkAndAllocatesOneFreshAttemptWithoutClearingValidCache() {
		val source = readerRasterPreparationSource()
		val retry = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "fun retryPreparation(): Long? {",
			endDelimiter = "fun onProfileBootstrapFailed()"
		)

		assertContains(retry, "failedPreparationGeneration")
		assertEquals(1, Regex("Math\\.incrementExact\\(").findAll(retry).count())
		assertContains(retry, "return preparationGeneration")
		assertContains(retry, "cancelPrewarm(")
		assertContains(retry, "cancelRasterRepairs(")
		assertContains(retry, "cancelBackgroundPrefetch(")
		assertContains(retry, "passiveRasterPreparationPortProvider()?.cancel()")
		assertEquals(1, Regex("onRequestPrewarm\\(\\)").findAll(retry).count())
		assertFalse(retry.contains("bundleSource.invalidate("))
		assertFalse(retry.contains("bundleSource.invalidatePage("))
		assertFalse(retry.contains("bundleSource.trimMemory("))
	}

	@Test
	fun preparationGenerationFencesManifestCapturePublicationStateAndCompletionCallbacks() {
		val preparation = readerRasterPreparationSource()
		val passive = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val batch = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)
		val passiveBatch = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private class Batch(",
			endDelimiter = "private val manifestIssuer"
		)
		val liveManifestPort = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "internal fun interface ReaderPassiveRasterLiveManifestPort",
			endDelimiter = "internal interface ReaderPassiveRasterPreparationPort"
		)
		val manifest = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private fun prepareTarget(batch: Batch) {",
			endDelimiter = "private fun resolveTarget("
		)
		val capture = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private fun captureTarget(",
			endDelimiter = "private fun admitCapturedTarget("
		)
		val admission = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private fun admitCapturedTarget(",
			endDelimiter = "private fun completeTarget("
		)
		val bundleAdmission = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun admitPassiveRasterCapture(",
			endDelimiter = "fun protectEncodedWindow("
		)

		assertContains(preparation, "failedPreparationGeneration")
		assertContains(batch, "preparationGeneration")
		assertContains(passiveBatch, "preparationGeneration")
		assertContains(liveManifestPort, "preparationGeneration: Long")
		assertContains(manifest, "preparationGeneration = batch.preparationGeneration")
		listOf(batch, manifest, capture, admission).forEach { callbackBoundary ->
			assertContains(callbackBoundary, "isPreparationGenerationCurrent")
		}
		assertContains(admission, "preparationGeneration = batch.preparationGeneration")
		assertContains(bundleAdmission, "preparationGeneration")
		assertContains(bundleAdmission, "isPreparationGenerationCurrent")
		assertTrue(
			bundleAdmission.lastIndexOf("isPreparationGenerationCurrent") <
				bundleAdmission.indexOf("putSnapshot("),
			"Persistent completion must reject a terminal preparation before cache publication."
		)
	}

	@Test
	fun lowMemoryRetiresAllPassiveWorkWithoutInvalidatingLiveOrPersistentCache() {
		val source = readerRasterPreparationSource()
		val callbacks = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private val memoryCallbacks = object : ComponentCallbacks2 {",
			endDelimiter = "\tinit {"
		)
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val trim = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun trimMemory(reason: String) {",
			endDelimiter = "fun retainedSnapshot("
		)

		assertContains(callbacks, "cancelPrewarm(")
		assertContains(callbacks, "cancelRasterRepairs(")
		assertContains(callbacks, "cancelBackgroundPrefetch(")
		assertContains(callbacks, "onPassiveRasterMemoryPressure(")
		assertContains(callbacks, "bundleSource.trimMemory(")
		assertContains(trim, "removeCachedSnapshot(")
		assertContains(trim, "trimDecodedToProtectedWindow()")
		assertFalse(callbacks.contains("bundleSource.invalidate("))
		assertFalse(callbacks.contains("foregroundWebViewOwnership"))
		assertFalse(callbacks.contains("webViewProvider("))
		assertFalse(trim.contains("rasterCache.clear"))
		assertFalse(trim.contains("persistentRasterCache.clear"))
	}

	@Test
	fun singlePageRepairUsesOnlyTheIsolatedPassiveRasterPort() {
		val source = readerRasterPreparationSource()
		val repair = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)

		assertContains(repair, "passiveRasterPreparationPortProvider()")
		assertContains(repair, "passiveRasterPreparationPort.start(")
		assertContains(repair, "ReaderPageRasterAcquisitionTrigger.Repair")
		assertContains(repair, "ReaderPageRasterBatchTarget(")
		assertContains(repair, "pageIndex = pageIndex")
		assertContains(repair, "authority = ReaderPageRasterTargetAuthority.OffscreenPassive")
		assertContains(repair, "readerPageRasterRepairedResult(")
		assertContains(repair, "passive-raster-unavailable")
		listOf(
			"acquirePassiveRasterLease(",
			"foregroundWebViewOwnership",
			"ReaderForegroundWebViewMutationGeneration",
			"rasterRepairBatchController.start(",
			"rasterRepairBatchController.cancel",
			"onStagingStarted",
			"reusePreparationShield(",
			"removePreparationShield(",
			"restoreLiveComposition(",
			"exposePageTurnPreview",
			"publishPreparationState("
		).forEach { forbidden -> assertFalse(repair.contains(forbidden), forbidden) }
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
	fun adjacentChapterPrefetchUsesTheIndependentPassiveIdleAdapter() {
		val source = readerRasterPreparationSource()
		val background = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startBackgroundPrefetch(",
			endDelimiter = "private fun isBackgroundPrefetchActive("
		)
		val eligibility = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun isBackgroundPrefetchActive(",
			endDelimiter = "\n\n\tprivate "
		)

		assertContains(source, "Looper.myQueue().addIdleHandler")
		assertContains(background, "submission.targets")
		assertContains(background, "passiveRasterPreparationPort.start(")
		assertContains(
			background,
			"capacityPolicy = ReaderPageRasterCapacityPolicy.StopBackgroundRefill"
		)
		assertContains(background, "event = \"background-prefetch-started\"")
		assertContains(background, "event = \"background-prefetch-progress\"")
		assertContains(background, "\"background-prefetch-completed\"")
		assertContains(eligibility, "activeRasterRepairPageIndex == null")
		assertContains(eligibility, "rasterRepairCallbacks.isEmpty()")
		listOf(
			"rasterBackgroundBatchController.start(",
			"showBackgroundPrefetchShield(",
			"activeBackgroundPassiveLease",
			"trackVisualRestoration(",
			"onStagingStarted",
			"foregroundMutationGeneration",
			"publishPreparationState(",
			"reusePreparationShield("
		).forEach { forbidden -> assertFalse(background.contains(forbidden), forbidden) }
	}

	@Test
	fun adjacentChapterPassiveContractCannotAcceptShieldOrForegroundOwnership() {
		val adapter = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val contract = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "internal interface ReaderPassiveRasterPreparationPort",
			endDelimiter = "internal class ReaderPassiveRasterPreparationAdapter"
		)

		assertContains(contract, "targets: List<ReaderPageRasterBatchTarget>")
		assertContains(contract, "rasterGeneration: Long")
		assertContains(contract, "isStillCurrent: () -> Boolean")
		assertContains(contract, "onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit")
		assertContains(contract, "fun cancel()")
		listOf(
			"WebView",
			"ReaderForegroundWebViewOwnership",
			"ReaderForegroundWebViewMutationGeneration",
			"ReaderPageStaticWindowShield",
			"onStagingStarted",
			"restoreLiveComposition",
			"preview"
		).forEach { forbidden -> assertFalse(contract.contains(forbidden), forbidden) }
	}

	@Test
	fun applicationPanelShieldPreservesAuthoritySelectedFrameComposition() {
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
		assertContains(
			host,
			"presentationDecision?.layer?.let { it != ReaderPresentationLayer.Neutral } == true"
		)
		assertFalse(host.contains("latestRasterPreparationState.presentation"))
		assertContains(window, "preserveCurrentPresentation: Boolean = false")
		assertContains(window, "captureCurrentPresentation(")
		assertContains(window, "val presentationRoot = host.rootView")
		assertContains(window, "presentationRoot.draw(canvas)")
		assertFalse(window.contains("host.draw(canvas)"))
		assertContains(window, "private var ownedBitmap: Bitmap? = null")
		assertContains(window, "ownedBitmap?.recycle()")
	}
	@Test
	fun passivePersistenceRechecksExactLeaseCurrentnessBeforePublication() {
		val batch = readerRasterBatchSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val hydration = requiredReaderSourceSlice(
			source = batch,
			startDelimiter = "private fun hydrateTarget(session: Session, targetIndex: Int)",
			endDelimiter = "private fun submitMissingTargets("
		)
		val ensure = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun ensurePersistentSnapshot(",
			endDelimiter = "private fun cacheSnapshot("
		)
		val schedule = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "private fun schedulePersistentSnapshot(",
			endDelimiter = "private fun scheduleRasterPublication("
		)

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
	fun repairCannotAcquireForegroundPassiveOwnershipOrRestoreAPreview() {
		val preparation = readerRasterPreparationSource()
		val repair = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)
		val cancellation = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun cancelRasterRepairs(reason: String) {",
			endDelimiter = "private fun beginBlockingBackgroundPrefetchSession()"
		)

		assertContains(repair, "passiveRasterPreparationPort.start(")
		assertContains(cancellation, "passiveRasterPreparationPortProvider()?.cancel()")
		listOf(
			"acquirePassiveRasterLease(",
			"isPassiveRasterLeaseCurrent(",
			"foregroundWebViewOwnership",
			"mutationGeneration",
			"trackVisualRestoration(",
			"restoreLiveComposition(",
			"preparationShield"
		).forEach { forbidden ->
			assertFalse(repair.contains(forbidden), forbidden)
			assertFalse(cancellation.contains(forbidden), forbidden)
		}
	}

	@Test
	fun unavailablePassiveHostFailsRetryablyWithoutForegroundFallback() {
		val preparation = readerRasterPreparationSource()
		val prewarm = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "fun prewarmAdjacent(): Boolean {",
			endDelimiter = "private fun initializeRasterCacheAndQueryPlan("
		)
		val repair = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)
		val background = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startBackgroundPrefetch(",
			endDelimiter = "private fun isBackgroundPrefetchActive("
		)

		assertContains(prewarm, "passive-raster-unavailable")
		assertContains(prewarm, "ReaderPagePreparationPhase.Failed")
		assertContains(prewarm, "retryable = true")
		assertContains(repair, "passive-raster-unavailable")
		assertContains(repair, "ReaderPageRasterRepairResult.Failed")
		assertContains(background, "deferredBackgroundPrefetchStart")
		listOf(prewarm, repair, background).forEach { passivePath ->
			assertFalse(passivePath.contains("rasterBatchController.start("))
			assertFalse(passivePath.contains("rasterRepairBatchController.start("))
			assertFalse(passivePath.contains("rasterBackgroundBatchController.start("))
			assertFalse(passivePath.contains("acquirePassiveRasterLease("))
			assertFalse(passivePath.contains("restoreLiveComposition("))
			assertFalse(passivePath.contains("while ("))
		}
	}

	@Test
	fun everyOffscreenRasterPathUsesTheIsolatedPassiveAdapter() {
		val preparation = readerRasterPreparationSource()
		val prewarm = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "fun prewarmAdjacent(): Boolean {",
			endDelimiter = "private fun initializeRasterCacheAndQueryPlan("
		)
		val prewarmBatch = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)
		val repair = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)
		val background = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startBackgroundPrefetch(",
			endDelimiter = "private fun isBackgroundPrefetchActive("
		)
		val currentReference = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private class ReaderPageBundleRasterCurrentReferencePort(",
			endDelimiter = "internal fun readerPageTurnCanStartPassivePrewarm("
		)

		assertContains(preparation, "ReaderPassiveRasterPreparationPort")
		assertContains(prewarm, "passiveRasterPreparationPortProvider()")
		assertContains(prewarmBatch, "passiveRasterPreparationPort.start(")
		assertContains(background, "passiveRasterPreparationPort.start(")
		listOf(
			"acquirePassiveRasterLease(",
			"ReaderForegroundWebViewMutationGeneration",
			"rasterBatchController.start(",
			"rasterBackgroundBatchController.start(",
			"reusePreparationShield(",
			"showBackgroundPrefetchShield(",
			"onStagingStarted"
		).forEach { forbidden ->
			assertFalse(prewarm.contains(forbidden), forbidden)
			assertFalse(prewarmBatch.contains(forbidden), forbidden)
			assertFalse(background.contains(forbidden), forbidden)
		}
		assertContains(repair, "passiveRasterPreparationPortProvider()")
		assertContains(repair, "passiveRasterPreparationPort.start(")
		listOf(
			"acquirePassiveRasterLease(",
			"foregroundWebViewOwnership",
			"rasterRepairBatchController.start(",
			"reusePreparationShield(",
			"onStagingStarted",
			"restoreLiveComposition("
		).forEach { forbidden -> assertFalse(repair.contains(forbidden), forbidden) }
		assertContains(currentReference, "bundleSource.captureCurrentSurface(")
		listOf(
			"acquirePassiveRasterLease(",
			"rasterRepairBatchController.start(",
			"rasterBatchController.restoreLiveComposition(",
			"foregroundWebViewOwnership.canAcquirePassive()",
			"exposePageTurnPreview("
		).forEach { sharedPreviewRoute ->
			assertFalse(
				preparation.contains(sharedPreviewRoute),
				"Shared-foreground preview route remains reachable: $sharedPreviewRoute"
			)
		}

		val adapter = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val adapterContract = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "internal interface ReaderPassiveRasterPreparationPort",
			endDelimiter = "internal class ReaderPassiveRasterPreparationAdapter"
		)
		assertContains(adapter, "ReaderPassiveRasterManifestIssuer")
		assertContains(adapter, "ReaderPassiveRasterPrototypeSession<Bitmap>")
		assertContains(adapter, "session.commit(manifest")
		assertContains(adapter, "committed.capture captured@{")
		assertContains(adapter, "bundleSource.admitPassiveRasterCapture(")
		assertContains(adapter, "fun pause()")
		assertContains(adapter, "fun resume()")
		assertContains(adapter, "fun close()")
		val durableCommittedAuthority = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "private fun confirmDurableCommittedTarget(",
			endDelimiter = "private fun authorityMismatch("
		)
		val committedCaptureTransfer = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "private fun captureCommittedTarget(",
			endDelimiter = "private fun admitCapturedTarget("
		)
		assertContains(
			durableCommittedAuthority,
			"if (!batch.releaseCommittedCapture(committed))"
		)
		assertContains(durableCommittedAuthority, "ReaderPageRasterBatchOutcome.Failed(")
		assertContains(
			committedCaptureTransfer,
			"if (!batch.transferCommittedCapture(committed))"
		)
		assertContains(committedCaptureTransfer, "ReaderPageRasterBatchOutcome.Failed(")
		listOf(
			"WebView",
			"ReaderForegroundWebViewMutationGeneration",
			"ReaderPageStaticWindowShield",
			"onStagingStarted",
			"exposePageTurnPreviewFinal",
			"confirmPageTurnPreviewPresentation",
			"restorePageTurnLiveComposition",
			"ReaderBridgeEvent",
			"putSnapshot(",
			"submitDeck"
		).forEach { forbidden -> assertFalse(adapterContract.contains(forbidden), forbidden) }

		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val admission = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun admitPassiveRasterCapture(",
			endDelimiter = "fun protectEncodedWindow("
		)
		assertContains(admission, "ReaderPassiveRasterAdmissionContext(")
		assertContains(admission, "readerAdmitPassiveRaster(")
		assertContains(admission, "admitted.transferRaster()")
		assertContains(admission, "putSnapshot(")
		assertContains(admission, "generation == activeGeneration")
		assertContains(admission, "physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get()")
		assertFalse(admission.contains("capturePreparedRasterPage("))
		assertFalse(admission.contains("restoreLiveComposition("))

		val host = readerSource("KomikkuReaderNativeFrameHost.android.kt")
		assertContains(host, "ReaderPassiveRasterWebViewHost(")
		assertContains(host, "ReaderPassiveRasterPrototypeSession(")
		assertContains(host, "ReaderPassiveRasterPreparationAdapter(")
		assertContains(host, "passiveRasterPreparationAdapter?.pause()")
		assertContains(host, "passiveRasterPreparationAdapter?.resume()")
		assertContains(host, "replacePassiveRasterPreparationAdapter(")
		assertContains(host, "closePassiveRasterPreparationAdapter()")

		val pageTurns = readerAssetSource("navic-reader-page-turns.js")
		val bridge = readerAssetSource("navic-reader.js")
		val location = readerAssetSource("navic-reader-location.js")
		val committedRelocation = requiredReaderSourceSlice(
			source = location,
			startDelimiter = "function postLocationChanged(",
			endDelimiter = "function captureDuplicatePageBaselines("
		)
		val publicationOpen = requiredReaderSourceSlice(
			source = bridge,
			startDelimiter = "async openPublication(",
			endDelimiter = "async resolveReaderNavigationTarget("
		)
		val manifestInputs = requiredReaderSourceSlice(
			source = pageTurns,
			startDelimiter = "function pageTurnPassiveRasterManifestInputs(",
			endDelimiter = "function pageTurnLivePresentationTargetMatchesCurrent("
		)
		assertFalse(committedRelocation.contains("schedulePageTurnPassiveRasterCanonicalCommit("))
		assertContains(publicationOpen, "ensureCompletePaginationProfile(")
		assertFalse(publicationOpen.contains("schedulePageTurnPassiveRasterCanonicalCommit("))
		assertFalse(pageTurns.contains("function schedulePageTurnPassiveRasterCanonicalCommit("))
		assertFalse(pageTurns.contains("ReaderPassiveRasterCanonicalCommitScope"))
		assertFalse(pageTurns.contains("passiveRasterCanonicalCommitTargetValue"))
		assertFalse(pageTurns.contains("passiveRasterCanonicalForegroundGenerationIsCurrent"))
		assertContains(manifestInputs, "const target = this.pageTurnLivePresentationTargetValue")
		assertContains(manifestInputs, "pageTurnLivePresentationTargetCanonicalCommit.call(")
		assertFalse(manifestInputs.contains("readerCommitTextPage("))
		assertFalse(manifestInputs.contains("passiveRasterCanonicalCommitTargetValue"))
		assertContains(manifestInputs, "opaqueCaptureTarget")
		assertContains(manifestInputs, "visualPageOrdinal")
		assertContains(manifestInputs, "paginationFingerprint")
		assertContains(manifestInputs, "layoutFingerprint")
		assertContains(manifestInputs, "decorationFingerprint")
		assertContains(manifestInputs, "rasterGeneration: generation")
		assertFalse(manifestInputs.contains("target.rasterGeneration"))
		assertFalse(bridge.contains("passiveRasterCanonicalCommitTargetValue"))
		assertContains(bridge, "pageTurnPassiveRasterManifestInputs:")
	}
}

private fun requiredReaderSourceSlice(
	source: String,
	startDelimiter: String,
	endDelimiter: String
): String {
	val afterStart = source.substringAfter(startDelimiter, missingDelimiterValue = "")
	assertTrue(afterStart.isNotBlank(), "Missing exact start delimiter: $startDelimiter")
	val slice = afterStart.substringBefore(endDelimiter, missingDelimiterValue = "")
	assertTrue(slice.isNotBlank(), "Missing exact end delimiter: $endDelimiter")
	return slice
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

private fun readerAssetSource(fileName: String): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/assets/reader/$fileName"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate reader asset $fileName")
}
