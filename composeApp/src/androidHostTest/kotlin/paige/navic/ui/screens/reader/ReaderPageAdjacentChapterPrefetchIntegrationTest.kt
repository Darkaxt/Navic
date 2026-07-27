package paige.navic.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageAdjacentChapterPrefetchIntegrationTest {
	private val preparation = readerTask9Source(
		"ReaderPageRasterPreparationController.android.kt"
	)
	private val batch = readerTask9Source(
		"ReaderPageRasterBatchController.android.kt"
	)
	private val foliate = readerTask9Source(
		"ReaderPlayLikeCurlFoliateController.android.kt"
	)
	private val host = readerTask9Source(
		"KomikkuReaderNativeFrameHost.android.kt"
	)
	private val bundleSource = readerTask9Source(
		"ReaderPageTurnBundleSource.android.kt"
	)

	@Test
	fun productionControllerStartsAndPersistsBothChaptersOnlyAfterReadiness() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			assertTrue(fixture.background.starts.isEmpty())
			fixture.completeCalibrationDurably()
			assertTrue(fixture.background.starts.isEmpty())

			fixture.completeCurrentChapterDurably()
			assertTrue(fixture.background.starts.isEmpty())
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

			assertEquals(
				listOf(listOf(7, 6)),
				fixture.background.starts.map { request ->
					request.targets.map { target -> target.pageIndex }
				}
			)
			fixture.background.completeReady(durably = true)
			fixture.drainMainLooper()
			assertEquals(
				listOf(listOf(7, 6), listOf(14, 15)),
				fixture.background.starts.map { request ->
					request.targets.map { target -> target.pageIndex }
				}
			)
			fixture.background.completeReady(durably = true)
			fixture.drainMainLooper()

			fixture.controller.onPointerInteractionChanged(true)
			fixture.controller.onPointerInteractionChanged(false)
			fixture.drainMainLooper()
			assertEquals(2, fixture.background.starts.size)
			assertIs<ReaderPageNewPointerDecision.Accept>(
				fixture.latestState.operationPolicy.newPointer
			)
			assertEquals(
				ReaderPagePreparationPresentation.Hidden,
				fixture.latestState.presentation
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun foregroundRepairCancelsThenResumesProductionBackgroundPrefetch() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeCurrentChapterDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			val interrupted = fixture.background.active
			assertNotNull(interrupted)
			fixture.background.publishDurable(interrupted.targets.first())

			var repairResult: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(8) { repairResult = it }
			assertEquals(1, fixture.background.cancellationCount)
			assertIs<ReaderPageNewPointerDecision.Accept>(
				fixture.latestState.operationPolicy.newPointer
			)
			assertEquals(
				ReaderPagePreparationPresentation.Hidden,
				fixture.latestState.presentation
			)

			fixture.repair.completeReady(durably = true)
			assertIs<ReaderPageRasterRepairResult.Repaired>(repairResult)
			fixture.deliverMatchingActiveDeckPrepared(generationId = 42L)
			fixture.drainMainLooper()

			assertEquals(
				listOf(6),
				fixture.background.active?.targets?.map { target -> target.pageIndex }
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun failedPersistenceAdvancesTheOtherDirectionAndRetriesOnlyMissingWork() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeCurrentChapterDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			val failed = checkNotNull(fixture.background.active)
			fixture.background.publishDurable(failed.targets.first())
			fixture.background.completeFailed()
			fixture.drainMainLooper()

			assertEquals(
				listOf(14, 15),
				fixture.background.active?.targets?.map { target -> target.pageIndex }
			)
			failed.onTargetDurable(failed.targets.last())
			fixture.background.completeReady(durably = true)
			fixture.controller.onPointerInteractionChanged(true)
			fixture.controller.onPointerInteractionChanged(false)
			fixture.drainMainLooper()

			assertEquals(
				listOf(6),
				fixture.background.active?.targets?.map { target -> target.pageIndex }
			)
			assertEquals(ReaderPagePreparationPhase.Ready, fixture.latestState.phase)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun profileReplacementCannotRelabelAnOldDurablePlan() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeCurrentChapterDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			assertEquals(1, fixture.background.starts.size)

			fixture.controller.onRasterProfileEpochChanged(8L)
			fixture.controller.onPreparedActiveDeckChanged(
				ReaderPagePreparedActiveDeck(
					rasterProfileEpoch = 8L,
					rasterEpoch = fixture.rasterEpoch,
					sourceCenterPageIndex = 8,
					generationId = 42L
				)
			)
			fixture.drainMainLooper()

			assertEquals(1, fixture.background.starts.size)
			assertEquals(1, fixture.background.cancellationCount)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun preparationDeferralResumesOnStrictlyNewerEventAndTerminatesOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.foreground.completeDeferred("pagination-not-ready")
			assertTrue(
				fixture.controller.onRetryEvent(ReaderPageRasterRetryEvent.PaginationReady)
			)
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeCurrentChapterDurably()

			val preparation = fixture.diagnosticMessages.filter {
				it.startsWith("reader-preparation ")
			}
			assertEquals(
				listOf("Attempted", "Deferred", "Resumed", "Attempted", "Ready"),
				preparation.map { message ->
					message.substringAfter("state=").substringBefore(' ')
				}
			)
			val attempts = preparation.map { message ->
				message.substringAfter("attempt=").substringBefore(' ').toLong()
			}
			assertEquals(attempts[0], attempts[1])
			assertEquals(attempts[0], attempts[2])
			assertEquals(attempts[3], attempts[4])
			assertTrue(attempts[3] > attempts[0])
			val deferredVersion = preparation[1]
				.substringAfter("eventVersion=").substringBefore(' ').toLong()
			val resumedVersion = preparation[2]
				.substringAfter("eventVersion=").substringBefore(' ').toLong()
			assertTrue(resumedVersion > deferredVersion)
			assertEquals(
				1,
				preparation.count { message ->
					listOf("Ready", "Failed", "Cancelled").any { state ->
						message.contains("state=$state")
					}
				}
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun destroyWaitsForRasterCacheInitializationCleanup() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val initializationStarted = CompletableDeferred<Unit>()
		val allowInitializationCleanup = CompletableDeferred<Unit>()
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			initializeRasterCache = {
				initializationStarted.complete(Unit)
				try {
					awaitCancellation()
				} finally {
					withContext(NonCancellable) {
						allowInitializationCleanup.await()
					}
				}
			}
		)
		try {
			assertTrue(fixture.controller.prewarmAdjacent())
			initializationStarted.await()

			val destruction = fixture.controller.destroy()
			val returnedBeforeInitializationCleanup = withTimeoutOrNull(1_000L) {
				destruction.await()
				true
			} ?: false

			assertFalse(returnedBeforeInitializationCleanup)
			allowInitializationCleanup.complete(Unit)
			destruction.await()
		} finally {
			allowInitializationCleanup.complete(Unit)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun destroyWaitsForForegroundPreviewRestorationBeforeRemovingItsShield() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.foreground.stagePreview()
			fixture.foreground.delayCancellationRestoration = true
			assertEquals(2, fixture.hostChildCount())

			val destruction = fixture.controller.destroy()

			assertFalse(destruction.isCompleted)
			assertEquals(2, fixture.hostChildCount())
			fixture.foreground.completeCancellationRestoration()
			destruction.await()
			assertEquals(1, fixture.hostChildCount())
		} finally {
			fixture.foreground.completeCancellationRestoration()
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cacheInitializationCompletingWhileDetachedDefersAndCanRetry() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val allowFirstInitialization = CompletableDeferred<Unit>()
		var initializationCount = 0
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			initializeRasterCache = {
				initializationCount += 1
				if (initializationCount == 1) allowFirstInitialization.await()
			}
		)
		try {
			assertTrue(fixture.controller.prewarmAdjacent())
			fixture.detachWebView()
			allowFirstInitialization.complete(Unit)
			testScheduler.advanceUntilIdle()

			fixture.attachWebView()
			assertTrue(
				fixture.controller.onRetryEvent(ReaderPageRasterRetryEvent.WebViewAttached)
			)
			assertTrue(fixture.controller.prewarmAdjacent())
			testScheduler.advanceUntilIdle()

			assertEquals(2, initializationCount)
			assertNotNull(fixture.foreground.active)
		} finally {
			allowFirstInitialization.complete(Unit)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun repairCancellationRetainsOneAttemptAndOneTerminalDiagnostic() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeCurrentChapterDurably()
			var result: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(8) { result = it }

			fixture.controller.invalidate("diagnostic-cancel")

			assertEquals(ReaderPageRasterRepairResult.Cancelled, result)
			val repair = fixture.diagnosticMessages.filter {
				it.startsWith("reader-repair ")
			}
			assertEquals(
				listOf("Started", "Cancelled"),
				repair.map { message ->
					message.substringAfter("state=").substringBefore(' ')
				}
			)
			assertEquals(
				1,
				repair.map { message ->
					message.substringAfter("attempt=").substringBefore(' ')
				}.distinct().size
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun backgroundPrefetchPublishesOneTerminalForEachDiagnosticSession() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeCurrentChapterDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			val firstQueued = fixture.diagnosticMessages.first { message ->
				message.startsWith("reader-prefetch ") && message.contains("state=Queued")
			}
			val session = firstQueued
				.substringAfter("prefetchSession=").substringBefore(' ')

			fixture.background.completeReady(durably = true)

			val firstSessionMessages = fixture.diagnosticMessages.filter { message ->
				message.startsWith("reader-prefetch ") &&
					message.contains("prefetchSession=$session ")
			}
			assertEquals(
				listOf("Queued", "Running", "Completed"),
				firstSessionMessages.map { message ->
					message.substringAfter("state=").substringBefore(' ')
				}
			)
			assertEquals(
				1,
				firstSessionMessages.count { message ->
					listOf("Completed", "Cancelled", "Failed").any { state ->
						message.contains("state=$state")
					}
				}
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun parsedDestinationPlanConsumesThePreparedDeckPublishedBeforeBlockingPrewarm() {
		val parsed = readerPageRasterPreparationPlan(
			"""{"context":{"centerPageIndex":8,"pageCount":20,"layoutMode":"single","readerDirection":"ltr","step":1,"currentChapterIndex":4,"currentChapterPageStartIndex":8,"currentChapterPageCount":3,"previousChapterPageStartIndex":4,"previousChapterPageCount":4,"nextChapterPageStartIndex":11,"nextChapterPageCount":3},"targets":[{"pageIndex":8,"priority":"current"},{"pageIndex":7,"priority":"previous-chapter"},{"pageIndex":11,"priority":"next-chapter"}]}"""
		)
		assertNotNull(parsed)
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		coordinator.onPreparedActiveDeckChanged(
			ReaderPagePreparedActiveDeck(
				rasterProfileEpoch = 7L,
				rasterEpoch = 11L,
				sourceCenterPageIndex = 8,
				generationId = 41L
			)
		)

		coordinator.beginBlockingSession()
		coordinator.replaceDurablePlan(
			ReaderPageAdjacentChapterPrefetchPlan(
				key = ReaderPageAdjacentChapterPrefetchKey(
					currentChapterIndex = parsed.currentChapterIndex,
					currentChapterPageStartIndex = parsed.currentChapterPageStartIndex,
					currentChapterPageCount = parsed.currentChapterPageCount,
					rasterProfileEpoch = 7L,
					rasterEpoch = 11L
				),
				chapters = parsed.adjacentChapterPrefetchChapters()
			)
		)

		assertEquals(1, submissions.size)
		val previous = submissions.single()
		previous.targets.forEach { target ->
			coordinator.onTargetDurable(previous, target.pageIndex)
		}
		coordinator.onBatchFinished(previous)
		assertEquals(2, submissions.size)
	}

	@Test
	fun productionPathJoinsDurableCurrentChapterWithPreparedActiveDeck() {
		assertContains(preparation, "ReaderPageAdjacentChapterPrefetchCoordinator(")
		assertContains(preparation, "publishDurableAdjacentChapterPlan(")
		assertContains(preparation, "adjacentChapterPrefetchCoordinator.beginBlockingSession()")
		assertContains(preparation, "fun onRasterProfileEpochChanged(epoch: Long?)")
		assertContains(preparation, "fun onPreparedActiveDeckChanged(")
		assertContains(foliate, "onPreparedActiveDeckChanged:")
		assertContains(foliate, "publishPreparedActiveDeck()")
		assertContains(host, "onPreparedActiveDeckChanged =")
		assertContains(host, "pageRasterPreparationController.onRasterProfileEpochChanged(epoch)")
	}

	@Test
	fun windowHideInvalidatesThePreparedDeckBeforeRasterRegeneration() {
		val hidden = foliate
			.substringAfter("fun onHostWindowHidden() {")
			.substringBefore("fun onPageTouchEvent(")

		assertContains(hidden, "invalidate(")
		assertContains(hidden, "reason = \"window-hidden\"")
		assertContains(hidden, "profileRegeneration = true")
		assertContains(host, "playLikeCurlController.onHostWindowHidden()")
		assertContains(host, "pageRasterPreparationController.invalidate(\"window-hidden\")")
	}

	@Test
	fun productionBatchReportsOnlyDurablyPublishedTargetsToExactSubmission() {
		val background = preparation
			.substringAfter("private fun startBackgroundPrefetch(")
			.substringBefore("private fun isBackgroundPrefetchActive(")

		assertContains(batch, "onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit")
		assertContains(batch, "session.onTargetDurable(target)")
		assertContains(background, "onTargetDurable = { target ->")
		assertContains(background, "adjacentChapterPrefetchCoordinator.onTargetDurable(")
		assertContains(background, "adjacentChapterPrefetchCoordinator.onBatchFinished(")
		assertContains(background, "submission = submission")
	}

	@Test
	fun cancellationFencesCaptureBeforeCacheAndPersistentPublication() {
		val capture = bundleSource
			.substringAfter("fun capturePreparedRasterPage(")
			.substringBefore("fun cacheCurrentSnapshot(")

		val outerCapture = capture.substringBefore("private fun capturePreparedPage(")
		val nativeCapture = capture.substringAfter("private fun capturePreparedPage(")
		assertContains(batch, "isStillCurrent = { isSessionActive(session) }")
		assertContains(batch, "override fun cancel(onRestored: () -> Unit)")
		assertContains(batch, "postVisualStateCallback(")
		assertContains(batch, "addOnAttachStateChangeListener(attachmentListener)")
		assertContains(batch, "postOnAnimation(::completeRestoration)")
		assertContains(capture, "isStillCurrent: () -> Boolean")
		assertContains(capture, "!isStillCurrent()")
		assertTrue(
			outerCapture.lastIndexOf("!isStillCurrent()") < outerCapture.indexOf("putSnapshot(")
		)
		assertTrue(
			nativeCapture.lastIndexOf("!isStillCurrent()") <
				nativeCapture.indexOf("ReaderPageSlideSnapshot(")
		)
	}

	@Test
	fun attachmentAndBlockingBoundariesCancelNativeBatchAndRetryState() {
		assertContains(
			preparation,
			"adjacentChapterPrefetchCoordinator.onHostAvailabilityChanged(attached)"
		)
		assertContains(host, "onWebViewAttachmentChanged =")
		assertContains(preparation, "backgroundPrefetchAttachmentListener")
		assertContains(preparation, "addOnAttachStateChangeListener(")
		assertContains(preparation, "removeOnAttachStateChangeListener(")
		assertContains(preparation, "rasterBackgroundBatchController.resetRetryState()")
		assertContains(batch, "fun resetRetryState()")
	}

	@Test
	fun repairCancellationPreservesMissingWorkUntilAReplacementDeckIsPrepared() {
		val repairStart = preparation
			.substringAfter("private fun startNextRasterRepair() {")
			.substringBefore("private fun deferRasterRepair(")
		val repairFinish = preparation
			.substringAfter("private fun finishRasterRepair(")
			.substringBefore("private fun readerPageRasterDeferralReason(")

		assertContains(repairStart, "suspendForForegroundWork()")
		assertContains(repairFinish, "resumeAfterForegroundWork()")
		assertContains(foliate, "ReaderPagePreparedActiveDeck(")
	}

	@Test
	fun pointerInputSuspendsHiddenCaptureUntilTheGestureTerminates() {
		val dispatch = host
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchPlayLikeCurlTouchEvent(")

		assertContains(dispatch, "MotionEvent.ACTION_DOWN")
		assertContains(dispatch, "onPointerInteractionChanged(true)")
		assertContains(dispatch, "event.actionMasked == MotionEvent.ACTION_UP ||")
		assertContains(dispatch, "event.actionMasked == MotionEvent.ACTION_CANCEL")
		assertContains(dispatch, "physicalDispatchMode != ReaderPagePhysicalDispatchMode.PlayLikeCurl")
		assertContains(dispatch, "onPointerInteractionChanged(false)")
		val terminals = host
			.substringAfter("private fun dispatchPageHostLifecycleEvent(")
			.substringBefore("private fun logGestureTerminal(")
		assertContains(terminals, "if (cancelled.isNotEmpty()) {")
		assertContains(terminals, "private fun completeHostDelayedTap(")
		assertContains(terminals, "if (won) {")
		assertContains(terminals, "pageRasterPreparationController.onPointerInteractionChanged(false)")
		assertContains(terminals, "val won = completeHostGesture(")
		assertContains(preparation, "onInteractionActiveChanged(active)")
	}

	@Test
	fun backgroundCallbacksUseOnlyTheDedicatedSessionFencedShield() {
		val background = preparation
			.substringAfter("private fun scheduleBackgroundPrefetch(")
			.substringBefore("private fun logPrewarmBoundary(")

		assertContains(background, "showBackgroundPrefetchShield(snapshot, submission)")
		assertContains(background, "removeBackgroundPrefetchShield(submission.sessionId)")
		assertContains(background, "backgroundPrefetchShieldSessionId != sessionId")
		assertFalse(background.contains("publishPreparationState("))
		assertFalse(background.contains("reusePreparationShield("))
	}

	@Test
	fun backgroundCallbacksNeverPublishPreparationOrAttachTheColdShield() {
		val background = preparation
			.substringAfter("private fun scheduleBackgroundPrefetch(")
			.substringBefore("private fun logPrewarmBoundary(")

		assertFalse(background.contains("publishPreparationState("))
		assertFalse(background.contains("reusePreparationShield("))
		assertTrue(background.contains("Looper.myQueue().addIdleHandler"))
	}
}

private data class ReaderPageRasterBatchRequest(
	val reference: ReaderPageSlideSnapshot,
	val targets: List<ReaderPageRasterBatchTarget>,
	val trigger: ReaderPageRasterAcquisitionTrigger,
	val onStagingStarted: (ReaderPageSlideSnapshot) -> Unit,
	val onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
	val onProgress: (Int, Int) -> Unit,
	val onComplete: (ReaderPageRasterBatchOutcome) -> Unit
)

private class FakeReaderPageRasterBatchPort : ReaderPageRasterBatchPort {
	val starts = mutableListOf<ReaderPageRasterBatchRequest>()
	var active: ReaderPageRasterBatchRequest? = null
		private set
	var cancellationCount = 0
		private set
	var delayCancellationRestoration = false
	private var pendingCancellationRestoration: (() -> Unit)? = null

	override fun start(
		webView: WebView,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		trigger: ReaderPageRasterAcquisitionTrigger,
		onStagingStarted: (ReaderPageSlideSnapshot) -> Unit,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean {
		check(active == null)
		val request = ReaderPageRasterBatchRequest(
			reference = reference,
			targets = targets,
			trigger = trigger,
			onStagingStarted = onStagingStarted,
			onTargetDurable = onTargetDurable,
			onProgress = onProgress,
			onComplete = onComplete
		)
		starts += request
		active = request
		onProgress(0, targets.size)
		return true
	}

	fun stagePreview() {
		val request = checkNotNull(active)
		request.onStagingStarted(request.reference)
	}

	fun completeCancellationRestoration() {
		pendingCancellationRestoration?.also {
			pendingCancellationRestoration = null
			it()
		}
	}

	fun publishDurable(target: ReaderPageRasterBatchTarget) {
		val request = checkNotNull(active)
		check(target in request.targets)
		request.onTargetDurable(target)
	}

	fun completeReady(durably: Boolean) {
		val request = checkNotNull(active)
		active = null
		if (durably) {
			request.targets.forEach(request.onTargetDurable)
		}
		request.onProgress(request.targets.size, request.targets.size)
		request.reference.release()
		request.onComplete(ReaderPageRasterBatchOutcome.Ready)
	}

	fun completeFailed() {
		val request = checkNotNull(active)
		active = null
		request.reference.release()
		request.onComplete(
			ReaderPageRasterBatchOutcome.Failed(
				stage = "persistent-publication",
				pageIndex = request.targets.last().pageIndex,
				reason = "durable-write-failed"
			)
		)
	}

	fun completeDeferred(reason: String) {
		val request = checkNotNull(active)
		active = null
		request.reference.release()
		request.onComplete(
			ReaderPageRasterBatchOutcome.Deferred(
				stage = "batch",
				pageIndex = request.targets.last().pageIndex,
				reason = reason
			)
		)
	}

	override fun resetRetryState() = Unit

	override fun cancel(onRestored: () -> Unit) {
		val request = active
		if (request == null) {
			onRestored()
			return
		}
		active = null
		cancellationCount += 1
		request.reference.release()
		request.onComplete(ReaderPageRasterBatchOutcome.Cancelled)
		if (delayCancellationRestoration) {
			check(pendingCancellationRestoration == null)
			pendingCancellationRestoration = onRestored
		} else {
			onRestored()
		}
	}
}

private class ReaderPageRasterPreparationControllerFixture private constructor(
	private val activityController: org.robolectric.android.controller.ActivityController<Activity>,
	private val host: FrameLayout,
	private val webView: WebView,
	private val testScheduler: TestCoroutineScheduler,
	private val bundleSource: ReaderPageTurnBundleSource,
	private val reference: ReaderPageSlideSnapshot,
	val controller: ReaderPageRasterPreparationController,
	val foreground: FakeReaderPageRasterBatchPort,
	val repair: FakeReaderPageRasterBatchPort,
	val background: FakeReaderPageRasterBatchPort,
	val diagnosticMessages: MutableList<String>,
	private val states: MutableList<ReaderPagePreparationState>
) {
	val latestState: ReaderPagePreparationState
		get() = states.last()
	val rasterEpoch: Long
		get() = bundleSource.currentGeneration()

	fun startCurrentChapterPreparation() {
		assertTrue(controller.prewarmAdjacent())
		testScheduler.advanceUntilIdle()
		assertNotNull(foreground.active)
	}

	fun completeCalibrationDurably() {
		foreground.completeReady(durably = true)
		assertNotNull(foreground.active)
		assertTrue(background.starts.isEmpty())
	}

	fun completeCurrentChapterDurably() {
		foreground.completeReady(durably = true)
		assertEquals(ReaderPagePreparationPhase.Ready, latestState.phase)
	}

	fun deliverMatchingActiveDeckPrepared(generationId: Long = 41L) {
		controller.onPreparedActiveDeckChanged(
			ReaderPagePreparedActiveDeck(
				rasterProfileEpoch = 7L,
				rasterEpoch = bundleSource.currentGeneration(),
				sourceCenterPageIndex = 8,
				generationId = generationId
			)
		)
	}

	fun drainMainLooper() {
		Shadows.shadowOf(Looper.getMainLooper()).idle()
	}

	fun hostChildCount(): Int = host.childCount

	fun detachWebView() {
		host.removeView(webView)
		controller.onWebViewAttachmentChanged(false)
		drainMainLooper()
	}

	fun attachWebView() {
		host.addView(webView)
		drainMainLooper()
		controller.onWebViewAttachmentChanged(true)
	}

	suspend fun close() {
		controller.destroyAndJoin()
		bundleSource.closeAndJoin()
		reference.releaseCacheOwnership()
		activityController.destroy()
	}

	companion object {
		suspend fun create(
			testScheduler: TestCoroutineScheduler,
			initializeRasterCache: (suspend (WebView) -> Unit)? = null
		): ReaderPageRasterPreparationControllerFixture {
			val activityController = Robolectric.buildActivity(Activity::class.java).setup()
			val activity = activityController.get()
			val host = FrameLayout(activity)
			val webView = WebView(activity)
			host.addView(webView)
			activity.setContentView(host)
			Shadows.shadowOf(Looper.getMainLooper()).idle()
			val bundleSource = ReaderPageTurnBundleSource().also {
				it.invalidate("task9-integration")
			}
			if (initializeRasterCache == null) {
				bundleSource.initializeRasterCache(webView)
			}
			val reference = task9ReferenceSnapshot()
			val foreground = FakeReaderPageRasterBatchPort()
			val repair = FakeReaderPageRasterBatchPort()
			val background = FakeReaderPageRasterBatchPort()
			val states = mutableListOf<ReaderPagePreparationState>()
			val diagnosticMessages = mutableListOf<String>()
			val controller = ReaderPageRasterPreparationController(
				host = host,
				webViewProvider = { webView },
				bundleSource = bundleSource,
				fenceBundleOwners = if (initializeRasterCache == null) {
					bundleSource::fenceForClose
				} else {
					{}
				},
				closeBundleOwners = if (initializeRasterCache == null) {
					bundleSource::closeAndJoin
				} else {
					{}
				},
				diagnostics = ReaderPageRuntimeDiagnostics(
					readerSession = 19L,
					nowMs = { 30L },
					emit = diagnosticMessages::add
				),
				onPreparationStateChange = states::add,
				rasterBatchController = foreground,
				rasterRepairBatchController = repair,
				rasterBackgroundBatchController = background,
				rasterPlanPort = ReaderPageRasterPreparationPlanPort { _, _, onPlan ->
					onPlan(task9PreparationPlan())
				},
				currentReferencePort = ReaderPageRasterCurrentReferencePort {
					_, _, _, _, generation, isCurrent, onResolved ->
					if (generation == bundleSource.currentGeneration() && isCurrent()) {
						reference.retain()
						onResolved(reference)
					} else {
						onResolved(null)
					}
				},
				initializeRasterCache = initializeRasterCache
					?: bundleSource::initializeRasterCache,
				retainedSnapshot = { _, _ ->
					reference.retain()
					reference
				}
			)
			controller.onRasterProfileEpochChanged(7L)
			return ReaderPageRasterPreparationControllerFixture(
				activityController = activityController,
				host = host,
				webView = webView,
				testScheduler = testScheduler,
				bundleSource = bundleSource,
				reference = reference,
				controller = controller,
				foreground = foreground,
				repair = repair,
				background = background,
				diagnosticMessages = diagnosticMessages,
				states = states
			)
		}
	}
}

private fun task9PreparationPlan(): ReaderPageRasterPreparationPlan =
	ReaderPageRasterPreparationPlan(
		centerPageIndex = 8,
		pageCount = 20,
		layoutMode = "spread",
		readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
		step = 1,
		currentChapterIndex = 4,
		currentChapterPageStartIndex = 8,
		currentChapterPageCount = 6,
		previousChapterPageStartIndex = 4,
		previousChapterPageCount = 4,
		nextChapterPageStartIndex = 14,
		nextChapterPageCount = 3,
		targets = listOf(
			ReaderPageRasterBatchTarget(8, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(9, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(11, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(7, ReaderPageRasterPriority.PreviousChapter),
			ReaderPageRasterBatchTarget(6, ReaderPageRasterPriority.PreviousChapterRemainder),
			ReaderPageRasterBatchTarget(14, ReaderPageRasterPriority.NextChapter),
			ReaderPageRasterBatchTarget(15, ReaderPageRasterPriority.NextChapterRemainder)
		)
	)

private fun task9ReferenceSnapshot(): ReaderPageSlideSnapshot = ReaderPageSlideSnapshot(
	key = ReaderPageSlideSnapshotKey(
		visualPageIndex = 8,
		kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
		bitmapQuality = ReaderPageBitmapQuality.Balanced,
		bitmapWidth = 20,
		bitmapHeight = 30,
		surfaceWidth = 20,
		surfaceHeight = 30
	),
	bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
	surfaceRectInWindow = Rect(0, 0, 20, 30),
	leafGeometry = ReaderPageTurnLeafGeometry(
		fullLeafRect = ReaderPageTurnPixelRect(0, 0, 20, 30),
		leftLeafRect = ReaderPageTurnPixelRect(0, 0, 9, 30),
		gutterRect = ReaderPageTurnPixelRect(9, 0, 11, 30),
		rightLeafRect = ReaderPageTurnPixelRect(11, 0, 20, 30)
	),
	reverseFaceColor = 0xffead9ae.toInt()
)

private fun readerTask9Source(fileName: String): String {
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
