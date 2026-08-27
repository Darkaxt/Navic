package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReaderPageRasterProductionEventSourceTest {
	@Test
	fun preparationDeferralsRegisterAgainstPreAttemptEventVersions() {
		val source = deferredRetrySource("ReaderPageRasterPreparationController.android.kt")
		val prewarm = source.substringAfter("fun prewarmAdjacent(): Boolean {")
			.substringBefore("private fun queryRasterPreparationPlan(")
		val repair = source.substringAfter("private fun startNextRasterRepair() {")
			.substringBefore("private fun readerPageRasterDeferralReason(")

		assertContains(source, "private val deferredRetryCoordinator")
		assertContains(prewarm, "prewarmRetryAttempt = newRetryAttempt(pendingPrewarmRetryCount)")
		assertContains(repair, "val retryAttempt = newRetryAttempt()")
		assertContains(source, "observedVersion = retryAttempt.observedVersion(reason)")
		assertContains(source, "deferredRetryCoordinator.defer(")
		assertContains(source, "deferredRetryCoordinator::cancel")
		assertContains(source, "ReaderPageRasterMaxAutomaticRetries")
		assertContains(source, "retryCount = retryCount")
		assertContains(source, "pendingPrewarmRetryCount = retryAttempt.retryCount + 1")
		assertContains(source, "resumePrewarmAfterRasterRepairs = true")
		assertContains(source, "rasterRepairCallbacks.isEmpty() && resumePrewarmAfterRasterRepairs")
		assertFalse(source.contains("postOnAnimation { queryRasterPreparationPlan("))
	}

	@Test
	fun prewarmRequestedDuringRasterRepairIsResumedAfterRepair() {
		val source = deferredRetrySource("ReaderPageRasterPreparationController.android.kt")
		val prewarm = source.substringAfter("fun prewarmAdjacent(): Boolean {")
			.substringBefore("private fun initializeRasterCacheAndQueryPlan(")
		val repairGate = prewarm.substringAfter("if (prewarmInProgress) return true")
			.substringBefore("if (!readerPageTurnCanStartPassivePrewarm(")
		val repairCompletion = source.substringAfter("private fun finishRasterRepair(")
			.substringBefore("private fun readerPageRasterDeferralReason(")

		assertContains(repairGate, "activeRasterRepairPageIndex == null")
		assertContains(repairGate, "deferredRasterRepairPageIndex == null")
		assertContains(repairGate, "rasterRepairCallbacks.isNotEmpty()")
		assertContains(repairGate, "startNextRasterRepair()")
		val activeRepairGate = repairGate.substringAfter("startNextRasterRepair()")
		assertContains(activeRepairGate, "if (activeRasterRepairPageIndex != null)")
		assertContains(activeRepairGate, "resumePrewarmAfterRasterRepairs = true")
		assertContains(activeRepairGate, "return true")
		assertFalse(activeRepairGate.contains("deferredRasterRepairPageIndex != null"))
		assertFalse(activeRepairGate.contains("rasterRepairCallbacks.isNotEmpty()"))
		assertContains(
			repairCompletion,
			"rasterRepairCallbacks.isEmpty() && resumePrewarmAfterRasterRepairs"
		)
		assertContains(repairCompletion, "onRequestPrewarm()")
	}

	@Test
	fun oneHostControllerPublishesEveryAuthoritativeRisingEdgeAndClosesOnTeardown() {
		val host = deferredRetrySource("KomikkuReaderNativeFrameHost.android.kt")
		val preparation = deferredRetrySource("ReaderPageRasterPreparationController.android.kt")
		val foliate = deferredRetrySource("ReaderPlayLikeCurlFoliateController.android.kt")
		val root = deferredRetrySource(
			"ReaderRoot.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader"
		)

		assertEquals(
			1,
			host.split("ReaderPageRasterHostEventController(").size - 1
		)
		assertContains(host, "contentReadyKeyChanged(contentReadyKey)")
		assertContains(host, "paginationReadinessChanged(")
		assertContains(host, "layoutSignatureMeasured(signature)")
		assertContains(host, "webViewAttachmentChanged(")
		assertContains(host, "lifecycleResumedChanged(true)")
		assertContains(host, "pageRasterHostEventController.close()")
		val replaceViewer = host.substringAfter("fun replaceViewerContent(viewerView: View) {")
			.substringBefore("fun setVerticalPageDragPreview(")
		assertContains(replaceViewer, "removePageTurnPrewarmLayoutListener()")
		assertContains(host, "val supported = enabled && pageTurnBundleSource.isAvailable")
		assertContains(host, "pageRasterHostEventController.layoutStabilityInvalidated()")
		assertContains(host, "onRasterProfileEpochChanged = ::onRasterProfileEpochChanged")
		assertContains(host, "rasterProfileEpoch = epoch")
		assertContains(host, "if (epoch == null && !task4ResourceTeardownStarted)")
		assertContains(host, "private var rasterPaginationReady = false")
		assertContains(
			host,
			"canStartPreparation = { coldOwnershipAdmitted && rasterPaginationReady }"
		)
		assertContains(host, "readerPageActivePaginationReadiness(")
		assertContains(host, "readerPagePaginationReadiness(pageTurnPaginationStatus)")
		assertContains(host, "rasterPaginationReady = readiness.isReadyForRasterization")
		assertContains(host, "pageRasterPreparationController.onPaginationReadinessLost()")
		assertContains(host, "pageRasterPreparationController.onPaginationBootstrapFailed()")
		assertContains(host, "if (!rasterPaginationReady)")
		assertContains(preparation, "fun onPaginationReadinessLost()")
		assertContains(preparation, "cancelPrewarm(reason = \"pagination-not-ready\")")
		assertContains(preparation, "fun onPaginationBootstrapFailed()")
		assertContains(host, "pageRasterPreparationController::onRetryEvent")
		assertContains(host, "pageRasterPreparationController::cancelAllDeferredRetries")
		assertContains(foliate, "private var nextRasterProfileEpoch = 1L")
		assertContains(foliate, "if (profile == null) {")
		assertContains(foliate, "onRasterProfileEpochChanged(null)")
		assertContains(foliate, "publishRasterProfileEpoch(profile)")
		assertContains(foliate, "publishRasterProfileEpoch(null)")
		assertContains(host, "onProfileBootstrapFailed = {")
		assertContains(host, "pageRasterPreparationController.onProfileBootstrapFailed()")
		assertContains(foliate, "webView.postVisualStateCallback(")
		assertContains(foliate, "refreshPreparedDeck(planRetryAttempt = 1)")
		assertContains(foliate, "onProfileBootstrapFailed()")
		assertContains(foliate, "onPaginationReadinessChanged(readiness)")
		assertContains(root, "pageTurnPaginationStatus = controllerState.paginationProfile.status")
	}

	@Test
	fun stableWebViewBootstrapsProfileAndPaginationBeforeLayoutAdmission() {
		val host = deferredRetrySource("KomikkuReaderNativeFrameHost.android.kt")
		val listener = host.substringAfter("val listener = ViewTreeObserver.OnPreDrawListener {")
			.substringBefore("pageTurnPrewarmLayoutListener = listener")

		val refresh = listener.indexOf("playLikeCurlController.onHostContentReady()")
		val profileGate = listener.indexOf("if (profileEpoch == null)")
		val paginationGate = listener.indexOf("if (!rasterPaginationReady)")
		val stopWaiting = listener.indexOf("removePageTurnPrewarmLayoutListener()", paginationGate)
		val prewarm = listener.indexOf("pageRasterPreparationController.prewarmAdjacent()")
		assertEquals(true, refresh >= 0 && refresh < profileGate)
		assertEquals(true, profileGate < paginationGate)
		assertEquals(true, paginationGate < stopWaiting && stopWaiting < prewarm)
	}

	@Test
	fun passiveFailureAndCanonicalCommitUseCausalProductionRetryEdges() {
		val host = deferredRetrySource("KomikkuReaderNativeFrameHost.android.kt")
		val preparation = deferredRetrySource("ReaderPageRasterPreparationController.android.kt")
		val foliate = deferredRetrySource("ReaderPlayLikeCurlFoliateController.android.kt")

		assertContains(preparation, "ReaderPageRasterDeferralReason.PassiveHostUnavailable")
		assertContains(
			preparation,
			"ReaderPageRasterDeferralReason.CanonicalLiveCommitUnavailable"
		)
		assertContains(preparation, "ReaderPageRasterRetryEvent.PassiveHostAvailable")
		assertContains(preparation, "ReaderPageRasterRetryEvent.CanonicalLiveCommitIssued")
		assertContains(host, "reason == ReaderPageRasterDeferralReason.PassiveHostUnavailable")
		assertContains(
			host,
			"reason == ReaderPageRasterDeferralReason.CanonicalLiveCommitUnavailable"
		)
		assertContains(host, "playLikeCurlController.onPassiveManifestAuthorityUnavailable()")
		assertContains(host, "onCanonicalLiveCommitIssued = ::onCanonicalLiveCommitIssued")
		assertContains(foliate, "onCanonicalLiveCommitIssued: () -> Boolean")
		assertContains(foliate, "fun onPassiveManifestAuthorityUnavailable()")
		assertContains(foliate, "onCanonicalLiveCommitIssued()")
	}

	@Test
	fun deferredInitialPreparationStaysVisibleWhilePreparedDeckDeferralStaysHidden() {
		val source = deferredRetrySource("ReaderPageRasterDeferredRetryCoordinator.android.kt")

		assertContains(source, "if (hasPreparedDeck) {")
		assertContains(source, "ReaderPagePreparationPresentation.Hidden")
		assertContains(source, "ReaderPagePreparationPresentation.Cover")
	}
}

private fun deferredRetrySource(
	fileName: String,
	relativeDirectory: String =
		"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader"
): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(root, "$relativeDirectory/$fileName")
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate $fileName")
}
