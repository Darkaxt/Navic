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
	fun oneHostControllerPublishesEveryAuthoritativeRisingEdgeAndClosesOnTeardown() {
		val host = deferredRetrySource("KomikkuReaderNativeFrameHost.android.kt")
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
	fun stableWebViewBootstrapsProfileBeforeProfileQualifiedLayoutAdmission() {
		val host = deferredRetrySource("KomikkuReaderNativeFrameHost.android.kt")
		val listener = host.substringAfter("val listener = ViewTreeObserver.OnPreDrawListener {")
			.substringBefore("pageTurnPrewarmLayoutListener = listener")

		val refresh = listener.indexOf("playLikeCurlController.onHostContentReady()")
		val nullProfileGate = listener.indexOf("if (profileEpoch == null)")
		val prewarm = listener.indexOf("pageRasterPreparationController.prewarmAdjacent()")
		assertEquals(true, refresh >= 0 && refresh < nullProfileGate)
		assertEquals(true, nullProfileGate < prewarm)
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
