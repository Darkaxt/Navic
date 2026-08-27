package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.webkit.ValueCallback
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
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
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
	fun productionControllerStartsBackgroundRangesInPriorityOrderOnlyAfterReadiness() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			assertTrue(fixture.background.starts.isEmpty())
			fixture.completeCalibrationDurably()
			assertTrue(fixture.background.starts.isEmpty())

			fixture.completeBlockingWindowDurably()
			assertTrue(fixture.background.starts.isEmpty())
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

			assertEquals(
				listOf(listOf(36, 38)),
				fixture.background.starts.map { request ->
					request.targets.map { target -> target.pageIndex }
				}
			)
			fixture.background.completeReady(durably = true)
			fixture.drainMainLooper()
			assertEquals(
				listOf(listOf(36, 38), listOf(8, 6, 4)),
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
	fun encodedCapacityStopsBackgroundRefillWithoutFailingReaderReadiness() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

			val refill = assertNotNull(fixture.background.active)
			assertEquals(
				ReaderPageRasterCapacityPolicy.StopBackgroundRefill,
				refill.capacityPolicy
			)
			fixture.background.publishDurable(refill.targets.first())
			fixture.background.completeCapacityReached(refill.targets.last().pageIndex)
			fixture.drainMainLooper()

			assertEquals(1, fixture.background.starts.size)
			assertEquals(null, fixture.background.active)
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
	fun repairUsesPassiveCaptureWithoutForegroundOwnershipOrPreviewRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			val interrupted = assertNotNull(fixture.background.active)
			fixture.background.publishDurable(interrupted.targets.first())
			val ownershipBefore = fixture.ownership.snapshot()

			var repairResult: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(20) { repairResult = it }

			assertEquals(ReaderPagePreparationPresentation.Hidden, fixture.latestState.presentation)
			assertIs<ReaderPageNewPointerDecision.Accept>(
				fixture.latestState.operationPolicy.newPointer
			)
			assertEquals(1, fixture.background.cancellationCount)
			val passiveRepair = assertNotNull(fixture.prewarm.active)
			assertEquals(listOf(20), passiveRepair.targets.map { target -> target.pageIndex })
			assertEquals(ReaderPageRasterAcquisitionTrigger.Repair, passiveRepair.trigger)
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertTrue(fixture.foreground.starts.isEmpty())
			assertTrue(fixture.repair.starts.isEmpty())
			assertEquals(0, fixture.foreground.liveCompositionRestorationCount)
			assertEquals(0, fixture.repair.liveCompositionRestorationCount)
			assertFalse(fixture.controller.hasStaticRasterShieldOwnership())

			fixture.prewarm.completeReady(durably = true)
			assertIs<ReaderPageRasterRepairResult.Repaired>(repairResult)
			fixture.drainMainLooper()

			assertEquals(
				listOf(38),
				fixture.background.active?.targets?.map { target -> target.pageIndex }
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun exactSettlementCancelsPendingRepairWithoutRestoringForegroundProgress() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			autoStartRequestedPrewarm = true
		)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

			var repairResult: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(20) { repairResult = it }
			assertNotNull(fixture.prewarm.active)

			fixture.controller.synchronizeVisualPageIndex(22, "page-turn:exact")

			assertEquals(ReaderPageRasterRepairResult.Cancelled, repairResult)
			assertEquals(ReaderPagePreparationPresentation.Hidden, fixture.latestState.presentation)
			assertIs<ReaderPageNewPointerDecision.Accept>(
				fixture.latestState.operationPolicy.newPointer
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun exactSettlementWithIncompleteShiftedWindowKeepsPreparedDeckInteractive() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

			fixture.controller.synchronizeVisualPageIndex(36, "page-turn:exact")

			assertEquals(ReaderPagePreparationPresentation.Hidden, fixture.latestState.presentation)
			assertIs<ReaderPageNewPointerDecision.Accept>(
				fixture.latestState.operationPolicy.newPointer
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun postReadyHydrationMissDoesNotRestoreForegroundProgress() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

			fixture.startCurrentChapterPreparation()
			val refill = assertNotNull(fixture.prewarm.active)
			refill.onHydrationMiss(refill.targets.first())

			assertEquals(ReaderPagePreparationPresentation.Hidden, fixture.latestState.presentation)
			assertIs<ReaderPageNewPointerDecision.Accept>(
				fixture.latestState.operationPolicy.newPointer
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun passiveBackgroundCancellationNeverEntersForegroundRestorationRecovery() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			assertNotNull(fixture.background.active)
			val ownershipBefore = fixture.ownership.snapshot()

			fixture.controller.onPointerInteractionChanged(true)

			assertEquals(1, fixture.background.cancellationCount)
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertEquals(0, fixture.foreground.liveCompositionRestorationCount)
			assertTrue(fixture.foreground.starts.isEmpty())
			assertFalse(fixture.controller.hasStaticRasterShieldOwnership())
			assertEquals(
				ReaderPagePreparationPresentation.Hidden,
				fixture.latestState.presentation
			)

			fixture.controller.onPointerInteractionChanged(false)
			fixture.drainMainLooper()
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun failedCurrentPersistenceBlocksForwardWorkAndRetriesOnlyMissingCurrentPages() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			val failed = checkNotNull(fixture.background.active)
			fixture.background.publishDurable(failed.targets.first())
			fixture.background.completeFailed()
			fixture.drainMainLooper()

			assertEquals(null, fixture.background.active)
			failed.onTargetDurable(failed.targets.last())
			fixture.controller.onPointerInteractionChanged(true)
			fixture.controller.onPointerInteractionChanged(false)
			fixture.drainMainLooper()

			assertEquals(
				listOf(38),
				fixture.background.active?.targets?.map { target -> target.pageIndex }
			)
			assertEquals(ReaderPagePreparationPhase.Ready, fixture.latestState.phase)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun failedPreparationGenerationRejectsOldCallbacksAfterFreshRetryStarts() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			autoStartRequestedPrewarm = true
		)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			val failedRequest = assertNotNull(fixture.prewarm.active)
			fixture.prewarm.completeFailed()
			assertEquals(ReaderPagePreparationPhase.Failed, fixture.latestState.phase)
			val cacheBeforeRetry = fixture.bundleSource.rasterCacheMetrics()

			fixture.controller.retryPreparation()
			val freshRequest = assertNotNull(fixture.prewarm.active)
			val stateCountAfterRetryStarted = fixture.states.size
			val target = failedRequest.targets.first()

			assertFalse(failedRequest.isStillCurrent())
			failedRequest.onActiveTarget(target)
			failedRequest.onHydrationMiss(target)
			failedRequest.onTargetDurable(target)
			failedRequest.onProgress(failedRequest.targets.size, failedRequest.targets.size)
			failedRequest.onComplete(ReaderPageRasterBatchOutcome.Ready)

			assertEquals(stateCountAfterRetryStarted, fixture.states.size)
			assertEquals(ReaderPagePreparationPhase.Preparing, fixture.latestState.phase)
			assertEquals(cacheBeforeRetry, fixture.bundleSource.rasterCacheMetrics())
			assertEquals(freshRequest, fixture.prewarm.active)
			assertEquals(null, fixture.background.active)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun duplicateRetrySharesOneFreshGenerationManifestDeckAndReadyProof() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			autoStartRequestedPrewarm = true
		)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			val failedRequest = assertNotNull(fixture.prewarm.active)
			val failedGeneration = fixture.preparationGeneration()
			fixture.prewarm.completeFailed()
			val startsBeforeRetry = fixture.prewarm.starts.size
			val statesBeforeRetry = fixture.states.size

			val acceptedRetryGeneration = fixture.controller.retryPreparation()
			val duplicateRetryGeneration = fixture.controller.retryPreparation()

			val freshGeneration = fixture.preparationGeneration()
			val freshCalibration = assertNotNull(fixture.prewarm.active)
			assertEquals(1, fixture.retryProbe.freshManifestRequestCount)
			assertEquals(startsBeforeRetry + 1, fixture.prewarm.starts.size)
			assertEquals(assertNotNull(failedGeneration) + 1L, assertNotNull(freshGeneration))
			assertEquals<Any?>(freshGeneration, acceptedRetryGeneration)
			assertEquals<Any?>(null, duplicateRetryGeneration)
			assertTrue(freshCalibration.liveManifestSequence > failedRequest.liveManifestSequence)
			assertTrue(freshCalibration.isStillCurrent())

			fixture.prewarm.completeReady(durably = true)
			val freshBlocking = assertNotNull(fixture.prewarm.active)
			assertTrue(freshBlocking.isStillCurrent())
			fixture.prewarm.completeReady(durably = true)

			assertEquals(
				startsBeforeRetry + 2,
				fixture.prewarm.starts.size,
				"One retry must build one calibration-plus-blocking raster deck."
			)
			assertFalse(
				fixture.states.drop(statesBeforeRetry).any { state ->
					state.phase == ReaderPagePreparationPhase.Ready
				},
				"Raster completion cannot publish Ready before matching deck proof."
			)

			fixture.deliverMatchingActiveDeckPrepared(generationId = 42L)
			fixture.drainMainLooper()
			val ready = fixture.states.drop(statesBeforeRetry).filter { state ->
				state.phase == ReaderPagePreparationPhase.Ready
			}
			assertEquals(1, ready.size)
			assertEquals(freshGeneration, fixture.statePreparationGeneration(ready.single()))
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun retryPreservesLivePublicationLocationPlaybackAndPreparedAudioWithoutCommands() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			autoStartRequestedPrewarm = true
		)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.prewarm.completeFailed()
			val authorityBefore = fixture.webView.liveAuthoritySnapshot()
			val ownershipBefore = fixture.ownership.snapshot()
			fixture.webView.commands.clear()

			fixture.controller.retryPreparation()

			assertNotNull(fixture.prewarm.active)
			assertEquals(authorityBefore, fixture.webView.liveAuthoritySnapshot())
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertEquals(71L, fixture.webView.liveAuthoritySnapshot().publicationSession)
			assertEquals("chapter=4;page=20", fixture.webView.liveAuthoritySnapshot().committedLocation)
			assertEquals("Enabled", fixture.webView.liveAuthoritySnapshot().playbackIntent)
			assertEquals(
				"prepared-visible-target-20",
				fixture.webView.liveAuthoritySnapshot().preparedAudioTarget
			)
			assertTrue(fixture.webView.commands.isEmpty())
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun unavailablePassivePortFailsRetryablyWithoutChangingCurrentLiveAuthority() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			passiveInitiallyAvailable = false
		)
		try {
			val authorityBefore = fixture.webView.liveAuthoritySnapshot()
			val ownershipBefore = fixture.ownership.snapshot()
			fixture.webView.commands.clear()

			assertTrue(fixture.controller.prewarmAdjacent())

			val failed = assertNotNull(fixture.states.lastOrNull())
			assertEquals(ReaderPagePreparationPhase.Failed, failed.phase)
			assertTrue(failed.retryable)
			assertEquals(authorityBefore, fixture.webView.liveAuthoritySnapshot())
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertEquals(null, fixture.prewarm.active)
			assertTrue(fixture.foreground.starts.isEmpty())
			assertTrue(fixture.repair.starts.isEmpty())
			assertTrue(fixture.webView.commands.isEmpty())
			assertTrue(
				fixture.releaseReferenceCacheOwnership(),
				"Unavailable passive startup must release its retained current-page reference once."
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun passiveSessionMismatchReleasesBitmapOnceAndPublishesTruthfulFailureWithoutFallback() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			val authorityBefore = fixture.webView.liveAuthoritySnapshot()
			val ownershipBefore = fixture.ownership.snapshot()
			val cacheBefore = fixture.bundleSource.rasterCacheMetrics()
			fixture.webView.commands.clear()
			var bitmapReleases = 0
			val (context, capture) = task9PassiveSessionMismatch(
				rasterGeneration = fixture.rasterEpoch,
				onBitmapReleased = { bitmapReleases += 1 }
			)

			val rejected = assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(context, capture)
			)
			assertEquals(ReaderPassiveRasterRejection.PassiveSession, rejected.reason)
			assertEquals(1, bitmapReleases)
			assertFalse(capture.raster?.release() == true)

			fixture.prewarm.completeFailed()
			assertEquals(ReaderPagePreparationPhase.Failed, fixture.latestState.phase)
			assertTrue(fixture.latestState.retryable)
			assertEquals(cacheBefore, fixture.bundleSource.rasterCacheMetrics())
			assertEquals(authorityBefore, fixture.webView.liveAuthoritySnapshot())
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertTrue(fixture.foreground.starts.isEmpty())
			assertTrue(fixture.repair.starts.isEmpty())
			assertTrue(fixture.webView.commands.isEmpty())
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun lowMemoryCancelsPassiveWorkPreservesLiveAndCacheThenRecreatesFromNewManifest() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(
			testScheduler = testScheduler,
			retirePassiveSessionOnCancel = true,
			autoStartRequestedPrewarm = true
		)
		try {
			val validDiskEntries = fixture.persistValidCacheEntry()
			assertTrue(validDiskEntries > 0)
			fixture.startCurrentChapterPreparation()
			val oldPort = fixture.passiveOwner.currentPort
			val oldRequest = assertNotNull(oldPort.prewarm.active)
			val authorityBefore = fixture.webView.liveAuthoritySnapshot()
			val ownershipBefore = fixture.ownership.snapshot()
			fixture.webView.commands.clear()

			fixture.dispatchLowMemory()

			assertEquals(1, oldPort.prewarm.cancellationCount)
			assertTrue(oldPort.isRetired)
			assertEquals(1, fixture.passiveOwner.sessions.size)
			assertEquals(validDiskEntries, fixture.bundleSource.rasterCacheMetrics().diskEntries)
			assertTrue(
				fixture.bundleSource.hasSnapshot(
					20,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
			assertEquals(authorityBefore, fixture.webView.liveAuthoritySnapshot())
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertTrue(fixture.foreground.starts.isEmpty())
			assertTrue(fixture.webView.commands.isEmpty())

			fixture.controller.retryPreparation()
			val freshRequest = assertNotNull(fixture.prewarm.active)
			assertEquals(2, fixture.passiveOwner.sessions.size)
			assertTrue(freshRequest.passiveSessionGeneration > oldRequest.passiveSessionGeneration)
			assertTrue(freshRequest.liveManifestSequence > oldRequest.liveManifestSequence)
			assertEquals(validDiskEntries, fixture.bundleSource.rasterCacheMetrics().diskEntries)
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
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			assertEquals(1, fixture.background.starts.size)

			fixture.controller.onRasterProfileEpochChanged(8L)
			fixture.controller.onPreparedActiveDeckChanged(
				ReaderPagePreparedActiveDeck(
					rasterProfileEpoch = 8L,
					rasterEpoch = fixture.rasterEpoch,
					sourceCenterPageIndex = 20,
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
			fixture.prewarm.completeDeferred("pagination-not-ready")
			assertTrue(
				fixture.controller.onRetryEvent(ReaderPageRasterRetryEvent.PaginationReady)
			)
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()

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
	fun passiveCaptureDeferralResumesOnlyWhenThePassiveHostIsAvailable() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.prewarm.completeDeferred(
				reason = "capture-unavailable",
				stage = "passive-host"
			)

			assertFalse(
				fixture.controller.onRetryEvent(ReaderPageRasterRetryEvent.ContentReady)
			)
			assertEquals(0, fixture.retryProbe.freshManifestRequestCount)

			fixture.controller.onPassiveRasterPreparationAvailable()

			assertEquals(1, fixture.retryProbe.freshManifestRequestCount)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun suffixedPassiveBatchDeferralResumesWhenThePassiveHostIsAvailable() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.prewarm.completeDeferred(
				reason = "passive-raster-unavailable:calibration",
				stage = "passive-host"
			)

			assertFalse(
				fixture.controller.onRetryEvent(ReaderPageRasterRetryEvent.ContentReady)
			)
			assertEquals(0, fixture.retryProbe.freshManifestRequestCount)

			fixture.controller.onPassiveRasterPreparationAvailable()

			assertEquals(1, fixture.retryProbe.freshManifestRequestCount)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun canonicalCommitDeferralResumesOnlyAfterLiveAuthorityConfirmation() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.prewarm.completeDeferred(
				reason = "canonical-live-commit-unavailable",
				stage = "passive-manifest"
			)

			assertFalse(
				fixture.controller.onRetryEvent(ReaderPageRasterRetryEvent.ContentReady)
			)
			assertEquals(0, fixture.retryProbe.freshManifestRequestCount)

			assertTrue(fixture.controller.onCanonicalLiveCommitIssued())

			assertEquals(1, fixture.retryProbe.freshManifestRequestCount)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun failedCanonicalAuthorityRecoveryTerminatesTheDeferredGeneration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.prewarm.completeDeferred(
				reason = "canonical-live-commit-unavailable",
				stage = "passive-manifest"
			)

			assertTrue(fixture.controller.onCanonicalLiveCommitRecoveryFailed())
			assertEquals(ReaderPagePreparationPhase.Failed, fixture.latestState.phase)
			assertTrue(fixture.latestState.retryable)
			assertFalse(fixture.controller.onCanonicalLiveCommitRecoveryFailed())
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
	fun destroyCancelsPassiveRepairWithoutForegroundRestorationFence() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			val ownershipBefore = fixture.ownership.snapshot()
			fixture.controller.repairRasterPage(20) {}
			assertNotNull(fixture.prewarm.active)

			val destruction = fixture.controller.destroy()
			destruction.await()

			assertEquals(1, fixture.prewarm.cancellationCount)
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertEquals(0, fixture.foreground.liveCompositionRestorationCount)
			assertEquals(0, fixture.repair.liveCompositionRestorationCount)
		} finally {
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
			assertNotNull(fixture.prewarm.active)
		} finally {
			allowFirstInitialization.complete(Unit)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun repeatedPassivePrewarmPreservesCompleteForegroundOwnership() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		val live = fixture.ownership.acquireLive(gestureId = 501L)
		try {
			val ownershipBefore = fixture.ownership.snapshot()
			repeat(2) {
				fixture.startCurrentChapterPreparation()
				fixture.completeCalibrationDurably()
				fixture.completeBlockingWindowDurably()
				assertEquals(ownershipBefore, fixture.ownership.snapshot())
				assertTrue(fixture.foreground.starts.isEmpty())
				assertEquals(0, fixture.foreground.liveCompositionRestorationCount)
				assertFalse(fixture.controller.hasStaticRasterShieldOwnership())
			}
		} finally {
			fixture.ownership.releaseLive(live)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun liveClaimDoesNotPreemptPassivePrewarmOrCreateRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		var live: ReaderForegroundWebViewLiveClaim? = null
		try {
			fixture.startCurrentChapterPreparation()
			val passiveRequest = checkNotNull(fixture.prewarm.active)

			live = fixture.ownership.acquireLive(gestureId = 502L)
			val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
			fixture.ownership.whenLiveReady(live, readiness::add)

			assertEquals(0, fixture.prewarm.cancellationCount)
			assertTrue(passiveRequest.isStillCurrent())
			assertEquals(
				listOf<ReaderForegroundWebViewLiveReadiness>(
					ReaderForegroundWebViewLiveReadiness.Ready
				),
				readiness
			)
			assertEquals(0, fixture.ownership.snapshot().restorationCallbacks)
			assertTrue(fixture.foreground.starts.isEmpty())

			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			assertEquals(0, fixture.prewarm.cancellationCount)
		} finally {
			live?.let(fixture.ownership::releaseLive)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun liveDestinationMutationDoesNotBecomePassiveCaptureAuthority() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		var live: ReaderForegroundWebViewLiveClaim? = null
		try {
			fixture.startCurrentChapterPreparation()
			val passiveRequest = checkNotNull(fixture.prewarm.active)
			live = fixture.ownership.acquireLive(gestureId = 506L)
			val mutation = checkNotNull(fixture.ownership.beginLiveMutation(live))

			assertTrue(fixture.ownership.isCurrent(live, mutation))
			assertTrue(passiveRequest.isStillCurrent())
			assertEquals(0, fixture.ownership.snapshot().passiveOwners)
			assertTrue(fixture.foreground.starts.isEmpty())

			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()

			assertEquals(0, fixture.prewarm.cancellationCount)
			assertEquals(0, fixture.foreground.liveCompositionRestorationCount)
			assertFalse(fixture.controller.hasStaticRasterShieldOwnership())
		} finally {
			live?.let(fixture.ownership::releaseLive)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun ordinaryPassivePrewarmCancellationNeedsNoForegroundRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		var live: ReaderForegroundWebViewLiveClaim? = null
		try {
			fixture.startCurrentChapterPreparation()
			fixture.controller.invalidate("ordinary-passive-cancellation")

			assertEquals(1, fixture.prewarm.cancellationCount)
			assertEquals(0, fixture.ownership.snapshot().passiveOwners)
			assertEquals(0, fixture.ownership.snapshot().restorationCallbacks)
			assertEquals(0, fixture.foreground.cancellationCount)
			assertFalse(fixture.controller.hasStaticRasterShieldOwnership())

			live = fixture.ownership.acquireLive(gestureId = 505L)
			val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
			fixture.ownership.whenLiveReady(live, readiness::add)
			assertEquals(
				listOf<ReaderForegroundWebViewLiveReadiness>(
					ReaderForegroundWebViewLiveReadiness.Ready
				),
				readiness
			)
		} finally {
			live?.let(fixture.ownership::releaseLive)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun repairCancellationReleasesPassiveWorkWithoutForegroundRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			val ownershipBefore = fixture.ownership.snapshot()
			var result: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(20) { result = it }
			assertNotNull(fixture.prewarm.active)

			fixture.controller.invalidate("ordinary-repair-cancellation")

			assertEquals(ReaderPageRasterRepairResult.Cancelled, result)
			assertEquals(1, fixture.prewarm.cancellationCount)
			assertEquals(ownershipBefore, fixture.ownership.snapshot())
			assertEquals(0, fixture.foreground.cancellationCount)
			assertEquals(0, fixture.repair.cancellationCount)
			assertEquals(0, fixture.foreground.liveCompositionRestorationCount)
			assertEquals(0, fixture.repair.liveCompositionRestorationCount)
			assertFalse(fixture.controller.hasStaticRasterShieldOwnership())
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun liveClaimDoesNotPreemptOrRestoreAnIsolatedPassiveRepair() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		var live: ReaderForegroundWebViewLiveClaim? = null
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			var repairResult: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(20) { repairResult = it }
			val passiveRepair = assertNotNull(fixture.prewarm.active)

			live = fixture.ownership.acquireLive(gestureId = 503L)
			val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
			fixture.ownership.whenLiveReady(live, readiness::add)

			assertEquals(0, fixture.prewarm.cancellationCount)
			assertTrue(passiveRepair.isStillCurrent())
			assertEquals(
				listOf<ReaderForegroundWebViewLiveReadiness>(
					ReaderForegroundWebViewLiveReadiness.Ready
				),
				readiness
			)
			assertEquals(0, fixture.ownership.snapshot().restorationCallbacks)
			assertEquals(null, repairResult)
			assertTrue(fixture.foreground.starts.isEmpty())
			assertTrue(fixture.repair.starts.isEmpty())
		} finally {
			live?.let(fixture.ownership::releaseLive)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun liveClaimDoesNotPreemptPassiveBackgroundCapture() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val fixture = ReaderPageRasterPreparationControllerFixture.create(testScheduler)
		var live: ReaderForegroundWebViewLiveClaim? = null
		try {
			fixture.startCurrentChapterPreparation()
			fixture.completeCalibrationDurably()
			fixture.completeBlockingWindowDurably()
			fixture.deliverMatchingActiveDeckPrepared()
			fixture.drainMainLooper()
			val passiveRequest = assertNotNull(fixture.background.active)

			live = fixture.ownership.acquireLive(gestureId = 504L)
			val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
			fixture.ownership.whenLiveReady(live, readiness::add)

			assertEquals(0, fixture.background.cancellationCount)
			assertTrue(passiveRequest.isStillCurrent())
			assertEquals(
				listOf<ReaderForegroundWebViewLiveReadiness>(
					ReaderForegroundWebViewLiveReadiness.Ready
				),
				readiness
			)
			assertEquals(0, fixture.ownership.snapshot().restorationCallbacks)
			assertTrue(fixture.foreground.starts.isEmpty())
			assertFalse(fixture.controller.hasStaticRasterShieldOwnership())
		} finally {
			live?.let(fixture.ownership::releaseLive)
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
			fixture.completeBlockingWindowDurably()
			var result: ReaderPageRasterRepairResult? = null
			fixture.controller.repairRasterPage(20) { result = it }

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
			fixture.completeBlockingWindowDurably()
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
		assertContains(batch, "internal enum class ReaderPageRasterCancellationRestoration")
		assertContains(batch, "val canRevealContent: Boolean")
		assertContains(batch, "override fun cancel(")
		assertContains(batch, "postVisualStateCallback(")
		assertContains(batch, "addOnAttachStateChangeListener(attachmentListener)")
		assertContains(batch, "postOnAnimation {")
		assertContains(
			batch,
			"ReaderPageRasterCancellationRestoration.Restored"
		)
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
	fun pointerInputSuspendsHiddenCaptureUntilThePhysicalStreamTerminates() {
		val dispatch = host
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchPlayLikeCurlTouchEvent(")

		assertContains(dispatch, "MotionEvent.ACTION_DOWN")
		assertContains(dispatch, "onPointerInteractionChanged(true)")
		assertContains(dispatch, "event.actionMasked == MotionEvent.ACTION_UP ||")
		assertContains(dispatch, "event.actionMasked == MotionEvent.ACTION_CANCEL")
		assertFalse(
			dispatch.contains(
				"physicalDispatchMode != ReaderPagePhysicalDispatchMode.PlayLikeCurl"
			)
		)
		val interactionEnded = dispatch.indexOf("onPointerInteractionChanged(false)")
		val dispatchModeCleared = dispatch.indexOf("physicalDispatchMode = null")
		assertTrue(
			interactionEnded >= 0 && interactionEnded < dispatchModeCleared,
			"Every physical pointer tail must end preparation interaction before clearing its route"
		)
		val lifecycle = host
			.substringAfter("private fun dispatchPageHostLifecycleEvent(")
			.substringBefore("private fun completeHostGesture(")
		assertContains(lifecycle, "pageInputSettlementHostController.onLifecycleEvent(event)")
		assertFalse(lifecycle.contains("onPointerInteractionChanged(false)"))
		val gestureCompletion = host
			.substringAfter("private fun completeHostGesture(")
			.substringBefore("private fun emitGestureDiagnostic(")
		val delayedTapCompletion = host
			.substringAfter("private fun completeHostDelayedTap(")
			.substringBefore("private fun completePageGesture(")
		assertFalse(gestureCompletion.contains("onPointerInteractionChanged(false)"))
		assertFalse(delayedTapCompletion.contains("onPointerInteractionChanged(false)"))
		assertContains(preparation, "onInteractionActiveChanged(active)")
	}

	@Test
	fun backgroundCaptureUsesOnlyTheIsolatedPassiveAdapter() {
		val background = preparation
			.substringAfter("private fun startBackgroundPrefetch(")
			.substringBefore("private fun isBackgroundPrefetchActive(")

		assertContains(background, "passiveRasterPreparationPortProvider()")
		assertContains(background, "passiveRasterPreparationPort.start(")
		assertContains(
			background,
			"capacityPolicy = ReaderPageRasterCapacityPolicy.StopBackgroundRefill"
		)
		assertContains(background, "onTargetDurable = { target ->")
		listOf(
			"acquirePassiveRasterLease(",
			"foregroundMutationGeneration",
			"onStagingStarted",
			"showBackgroundPrefetchShield(",
			"restoreLiveComposition(",
			"rasterBackgroundBatchController.start("
		).forEach { forbidden -> assertFalse(background.contains(forbidden), forbidden) }
		assertFalse(foliate.contains("acquirePassiveCaptureCover"))
		assertFalse(host.contains("onAcquireBackgroundCaptureCover"))
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

private data class ReaderPassiveRasterPreparationRequest(
	val reference: ReaderPageSlideSnapshot,
	val targets: List<ReaderPageRasterBatchTarget>,
	val rasterGeneration: Long,
	val preparationGeneration: Long,
	val passiveSessionGeneration: Long,
	val liveManifestSequence: Long,
	val isStillCurrent: () -> Boolean,
	val trigger: ReaderPageRasterAcquisitionTrigger,
	val capacityPolicy: ReaderPageRasterCapacityPolicy,
	val onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
	val onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
	val onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
	val onProgress: (Int, Int) -> Unit,
	val onComplete: (ReaderPageRasterBatchOutcome) -> Unit
)

private class FakeReaderPassiveRasterSessionOwner(
	private val retireSessionOnCancel: Boolean = false,
	initiallyAvailable: Boolean = true
) {
	private var nextSessionGeneration = 0L
	private var nextLiveManifestSequence = 0L
	private var current: FakeReaderPassiveRasterPreparationPort? = null
	private var initiallyAvailableForNextSession = initiallyAvailable
	val sessions = mutableListOf<FakeReaderPassiveRasterPreparationPort>()

	fun port(): FakeReaderPassiveRasterPreparationPort {
		val active = current?.takeUnless { it.isRetired }
		if (active != null) return active
		val replacement = FakeReaderPassiveRasterPreparationPort(
			passiveSessionGeneration = ++nextSessionGeneration,
			retireSessionOnCancel = retireSessionOnCancel,
			initiallyAvailable = initiallyAvailableForNextSession,
			nextLiveManifestSequence = { ++nextLiveManifestSequence }
		)
		initiallyAvailableForNextSession = true
		current = replacement
		sessions += replacement
		return replacement
	}

	val currentPort: FakeReaderPassiveRasterPreparationPort
		get() = checkNotNull(current)
}

private class FakeReaderPassiveRasterPreparationPort(
	private val passiveSessionGeneration: Long = 1L,
	private val retireSessionOnCancel: Boolean = false,
	initiallyAvailable: Boolean = true,
	private val nextLiveManifestSequence: () -> Long = { 1L }
) : ReaderPassiveRasterPreparationPort {
	enum class Lane {
		Prewarm,
		Background
	}

	private val startsByLane = mutableMapOf(
		Lane.Prewarm to mutableListOf<ReaderPassiveRasterPreparationRequest>(),
		Lane.Background to mutableListOf()
	)
	private val cancellationsByLane = mutableMapOf(
		Lane.Prewarm to 0,
		Lane.Background to 0
	)
	private var activeRequest: Pair<Lane, ReaderPassiveRasterPreparationRequest>? = null
	private var closed = false
	private var paused = false
	private var available = initiallyAvailable

	val prewarm = LanePort(Lane.Prewarm)
	val background = LanePort(Lane.Background)

	override val isAvailable: Boolean
		get() = available && !closed && !paused
	override val isRetired: Boolean
		get() = closed

	fun setAvailable(value: Boolean) {
		available = value
	}

	override fun start(
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		rasterGeneration: Long,
		preparationGeneration: Long,
		isPreparationGenerationCurrent: (Long) -> Boolean,
		isStillCurrent: () -> Boolean,
		trigger: ReaderPageRasterAcquisitionTrigger,
		capacityPolicy: ReaderPageRasterCapacityPolicy,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean {
		if (
			!isAvailable ||
				activeRequest != null ||
				targets.isEmpty() ||
				!isPreparationGenerationCurrent(preparationGeneration) ||
				!isStillCurrent()
		) {
			reference.release()
			return false
		}
		val lane = if (capacityPolicy == ReaderPageRasterCapacityPolicy.StopBackgroundRefill) {
			Lane.Background
		} else {
			Lane.Prewarm
		}
		val request = ReaderPassiveRasterPreparationRequest(
			reference = reference,
			targets = targets,
			rasterGeneration = rasterGeneration,
			preparationGeneration = preparationGeneration,
			passiveSessionGeneration = passiveSessionGeneration,
			liveManifestSequence = nextLiveManifestSequence(),
			isStillCurrent = isStillCurrent,
			trigger = trigger,
			capacityPolicy = capacityPolicy,
			onActiveTarget = onActiveTarget,
			onHydrationMiss = onHydrationMiss,
			onTargetDurable = onTargetDurable,
			onProgress = onProgress,
			onComplete = onComplete
		)
		startsByLane.getValue(lane) += request
		activeRequest = lane to request
		onProgress(0, targets.size)
		onActiveTarget(targets.first())
		return true
	}

	override fun cancel() {
		val active = activeRequest
		if (active != null) {
			val (lane, request) = active
			activeRequest = null
			cancellationsByLane[lane] = cancellationsByLane.getValue(lane) + 1
			request.reference.release()
			request.onComplete(ReaderPageRasterBatchOutcome.Cancelled)
		}
		if (retireSessionOnCancel) closed = true
	}

	override fun pause() {
		cancel()
		paused = true
	}

	override fun resume() {
		if (!closed) paused = false
	}

	override fun close() {
		cancel()
		closed = true
	}

	inner class LanePort internal constructor(private val lane: Lane) {
		val starts: List<ReaderPassiveRasterPreparationRequest>
			get() = startsByLane.getValue(lane)
		val active: ReaderPassiveRasterPreparationRequest?
			get() = activeRequest?.takeIf { it.first == lane }?.second
		val cancellationCount: Int
			get() = cancellationsByLane.getValue(lane)

		fun publishDurable(target: ReaderPageRasterBatchTarget) {
			val request = checkNotNull(active)
			check(target in request.targets)
			request.onTargetDurable(target)
		}

		fun completeCapacityReached(pageIndex: Int) {
			complete(ReaderPageRasterBatchOutcome.CapacityReached(pageIndex))
		}

		fun completeReady(durably: Boolean) {
			val request = checkNotNull(active)
			if (durably) request.targets.forEach(request.onTargetDurable)
			request.onProgress(request.targets.size, request.targets.size)
			complete(ReaderPageRasterBatchOutcome.Ready)
		}

		fun completeFailed() {
			val request = checkNotNull(active)
			complete(
				ReaderPageRasterBatchOutcome.Failed(
					stage = "persistent-publication",
					pageIndex = request.targets.last().pageIndex,
					reason = "durable-write-failed"
				)
			)
		}

		fun completeDeferred(reason: String, stage: String = "batch") {
			val request = checkNotNull(active)
			complete(
				ReaderPageRasterBatchOutcome.Deferred(
					stage = stage,
					pageIndex = request.targets.last().pageIndex,
					reason = reason
				)
			)
		}

		private fun complete(outcome: ReaderPageRasterBatchOutcome) {
			val request = checkNotNull(active)
			activeRequest = null
			request.reference.release()
			request.onComplete(outcome)
		}
	}
}

private data class ReaderPageRasterBatchRequest(
	val reference: ReaderPageSlideSnapshot,
	val targets: List<ReaderPageRasterBatchTarget>,
	val mutationGeneration: ReaderForegroundWebViewMutationGeneration,
	val isStillCurrent: () -> Boolean,
	val trigger: ReaderPageRasterAcquisitionTrigger,
	val capacityPolicy: ReaderPageRasterCapacityPolicy,
	val onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
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
	private val pendingCancellationRestorations = mutableListOf<
		(ReaderPageRasterCancellationRestoration) -> Unit
	>()
	var delayLiveCompositionRestoration = false
	var liveCompositionRestorationCount = 0
		private set
	private var pendingLiveCompositionRestoration:
		((ReaderPageRasterCancellationRestoration) -> Unit)? = null

	override fun start(
		webView: WebView,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration,
		isStillCurrent: () -> Boolean,
		trigger: ReaderPageRasterAcquisitionTrigger,
		capacityPolicy: ReaderPageRasterCapacityPolicy,
		onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean {
		check(active == null)
		val request = ReaderPageRasterBatchRequest(
			reference = reference,
			targets = targets,
			mutationGeneration = mutationGeneration,
			isStillCurrent = isStillCurrent,
			trigger = trigger,
			capacityPolicy = capacityPolicy,
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

	fun stagePreview(onPresented: (Boolean) -> Unit = {}) {
		val request = checkNotNull(active)
		request.onStagingStarted(request.reference, onPresented)
	}

	fun completeCancellationRestoration(
		restoration: ReaderPageRasterCancellationRestoration =
			ReaderPageRasterCancellationRestoration.Restored
	) {
		val pending = pendingCancellationRestorations.toList()
		pendingCancellationRestorations.clear()
		pending.forEach { callback -> callback(restoration) }
	}

	fun completeLiveCompositionRestoration(
		restoration: ReaderPageRasterCancellationRestoration =
			ReaderPageRasterCancellationRestoration.Restored
	) {
		pendingLiveCompositionRestoration?.also {
			pendingLiveCompositionRestoration = null
			it(restoration)
		}
	}

	fun publishDurable(target: ReaderPageRasterBatchTarget) {
		val request = checkNotNull(active)
		check(target in request.targets)
		request.onTargetDurable(target)
	}

	fun completeCapacityReached(pageIndex: Int) {
		val request = checkNotNull(active)
		active = null
		request.reference.release()
		request.onComplete(ReaderPageRasterBatchOutcome.CapacityReached(pageIndex))
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

	override fun restoreLiveComposition(
		webView: WebView,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration,
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit
	) {
		liveCompositionRestorationCount += 1
		if (delayLiveCompositionRestoration) {
			check(pendingLiveCompositionRestoration == null)
			pendingLiveCompositionRestoration = onRestorationFinished
		} else {
			onRestorationFinished(ReaderPageRasterCancellationRestoration.Restored)
		}
	}

	override fun cancel(
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit
	) {
		val request = active
		if (request == null) {
			if (pendingCancellationRestorations.isNotEmpty()) {
				pendingCancellationRestorations += onRestorationFinished
			} else {
				onRestorationFinished(ReaderPageRasterCancellationRestoration.Restored)
			}
			return
		}
		active = null
		cancellationCount += 1
		request.reference.release()
		request.onComplete(ReaderPageRasterBatchOutcome.Cancelled)
		if (delayCancellationRestoration) {
			pendingCancellationRestorations += onRestorationFinished
		} else {
			onRestorationFinished(ReaderPageRasterCancellationRestoration.Restored)
		}
	}
}

private data class ReaderStage4LiveAuthoritySnapshot(
	val publicationSession: Long,
	val committedLocation: String,
	val playbackIntent: String,
	val preparedAudioTarget: String
)

private class ReaderStage4CommandSpyWebView(context: Context) : WebView(context) {
	private var publicationSession = 71L
	private var committedLocation = "chapter=4;page=20"
	private var playbackIntent = "Enabled"
	private var preparedAudioTarget = "prepared-visible-target-20"
	val commands = mutableListOf<String>()

	fun liveAuthoritySnapshot() = ReaderStage4LiveAuthoritySnapshot(
		publicationSession = publicationSession,
		committedLocation = committedLocation,
		playbackIntent = playbackIntent,
		preparedAudioTarget = preparedAudioTarget
	)

	override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
		if (script.contains("pageTurnRasterDescriptor")) {
			resultCallback?.onReceiveValue(
				"""{
					"publicationUrl":"stage4-cache-publication",
					"paginationFingerprint":"stage4-pagination",
					"layoutFingerprint":"stage4-layout",
					"decorationFingerprint":"stage4-decoration",
					"viewportWidth":20,
					"viewportHeight":30,
					"pageCount":44,
					"spineIndex":4,
					"href":"stage4-chapter",
					"chapterPageIndex":20,
					"chapterPageCount":24,
					"visualPageOrdinal":20
				}""".trimIndent()
			)
			return
		}
		when {
			script.contains("openPublication", ignoreCase = true) -> {
				commands += "publication-open"
				publicationSession = Math.incrementExact(publicationSession)
				committedLocation = "publication-reopened"
				preparedAudioTarget = "cleared-by-publication-open"
			}
			script.contains("goToVisualPage", ignoreCase = true) -> {
				commands += "navigation"
				committedLocation = "navigation-commanded"
				preparedAudioTarget = "cleared-by-navigation"
			}
			script.contains("playback", ignoreCase = true) ||
				script.contains("readaloud", ignoreCase = true) -> {
				commands += "playback"
				playbackIntent = "Commanded"
				preparedAudioTarget = "cleared-by-playback"
			}
		}
		super.evaluateJavascript(script, resultCallback)
	}
}

private class ReaderStage4RetryProbe {
	var freshManifestRequestCount = 0
		private set
	var latestLiveManifestSequence = 0L
		private set
	lateinit var startFreshAttempt: () -> Unit

	fun requestFreshManifest() {
		freshManifestRequestCount += 1
		latestLiveManifestSequence = Math.incrementExact(latestLiveManifestSequence)
		startFreshAttempt()
	}
}

private class ReaderPageRasterPreparationControllerFixture private constructor(
	private val activityController: org.robolectric.android.controller.ActivityController<Activity>,
	private val host: FrameLayout,
	val webView: ReaderStage4CommandSpyWebView,
	private val testScheduler: TestCoroutineScheduler,
	val bundleSource: ReaderPageTurnBundleSource,
	private val reference: ReaderPageSlideSnapshot,
	val passiveOwner: FakeReaderPassiveRasterSessionOwner,
	val retryProbe: ReaderStage4RetryProbe,
	val ownership: ReaderForegroundWebViewOwnership,
	val controller: ReaderPageRasterPreparationController,
	val foreground: FakeReaderPageRasterBatchPort,
	val repair: FakeReaderPageRasterBatchPort,
	val diagnosticMessages: MutableList<String>,
	val states: MutableList<ReaderPagePreparationState>
) {
	val prewarm: FakeReaderPassiveRasterPreparationPort.LanePort
		get() = passiveOwner.currentPort.prewarm
	val background: FakeReaderPassiveRasterPreparationPort.LanePort
		get() = passiveOwner.currentPort.background
	val latestState: ReaderPagePreparationState
		get() = states.last()
	val rasterEpoch: Long
		get() = bundleSource.currentGeneration()

	fun releaseReferenceCacheOwnership(): Boolean {
		reference.releaseCacheOwnership()
		return reference.bitmap.isRecycled
	}

	fun preparationGeneration(): Long? = runCatching {
		controller.javaClass.getDeclaredField("preparationGeneration").apply {
			isAccessible = true
		}.getLong(controller)
	}.getOrNull()

	fun statePreparationGeneration(state: ReaderPagePreparationState = latestState): Long? =
		runCatching {
			state.javaClass.getMethod("getPreparationGeneration").invoke(state) as Long
		}.getOrNull()

	fun dispatchLowMemory() {
		val callbacks = controller.javaClass.getDeclaredField("memoryCallbacks").apply {
			isAccessible = true
		}.get(controller) as ComponentCallbacks2
		callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
	}

	suspend fun persistValidCacheEntry(): Int {
		val snapshot = assertNotNull(
			bundleSource.cacheCurrentSnapshot(
				pageIndex = 20,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				current = task9PersistentCapture()
			)
		)
		val activeRegistration = CompletableDeferred<ReaderPageSlideSnapshot?>()
		val activeRequest = bundleSource.hydrateSnapshot(
			webView = webView,
			pageIndex = 20,
			kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
			reference = snapshot,
			onHydrated = activeRegistration::complete
		)
		assertNotNull(activeRegistration.await()).release()
		activeRequest.cancel()
		bundleSource.protectDecodedPageIndices(setOf(20))
		val persisted = CompletableDeferred<ReaderPageRasterPublicationResult>()
		bundleSource.ensurePersistentSnapshot(
			snapshot = snapshot,
			priority = ReaderPageRasterPriority.Current,
			onPersisted = persisted::complete
		)
		testScheduler.advanceUntilIdle()
		assertEquals(ReaderPageRasterPublicationResult.Durable, persisted.await())
		return bundleSource.rasterCacheMetrics().diskEntries
	}

	fun startCurrentChapterPreparation() {
		assertTrue(controller.prewarmAdjacent())
		testScheduler.advanceUntilIdle()
		assertNotNull(prewarm.active)
	}

	fun completeCalibrationDurably() {
		prewarm.completeReady(durably = true)
		assertNotNull(prewarm.active)
		assertTrue(background.starts.isEmpty())
	}

	fun completeBlockingWindowDurably() {
		prewarm.completeReady(durably = true)
	}

	fun deliverMatchingActiveDeckPrepared(generationId: Long = 41L) {
		controller.onPreparedActiveDeckChanged(
			ReaderPagePreparedActiveDeck(
				rasterProfileEpoch = 7L,
				rasterEpoch = bundleSource.currentGeneration(),
				sourceCenterPageIndex = 20,
				generationId = generationId,
				preparationGeneration = preparationGeneration() ?: 0L
			)
		)
	}

	fun drainMainLooper() {
		Shadows.shadowOf(Looper.getMainLooper()).idle()
	}

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
			initializeRasterCache: (suspend (WebView) -> Unit)? = null,
			retirePassiveSessionOnCancel: Boolean = false,
			passiveInitiallyAvailable: Boolean = true,
			autoStartRequestedPrewarm: Boolean = false
		): ReaderPageRasterPreparationControllerFixture {
			val activityController = Robolectric.buildActivity(Activity::class.java).setup()
			val activity = activityController.get()
			val host = FrameLayout(activity)
			val webView = ReaderStage4CommandSpyWebView(activity)
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
			val passiveOwner = FakeReaderPassiveRasterSessionOwner(
				retireSessionOnCancel = retirePassiveSessionOnCancel,
				initiallyAvailable = passiveInitiallyAvailable
			)
			passiveOwner.port()
			val foreground = FakeReaderPageRasterBatchPort()
			val repair = FakeReaderPageRasterBatchPort()
			val legacyBackground = FakeReaderPageRasterBatchPort()
			val ownership = ReaderForegroundWebViewOwnership()
			val states = mutableListOf<ReaderPagePreparationState>()
			val diagnosticMessages = mutableListOf<String>()
			val retryProbe = ReaderStage4RetryProbe()
			lateinit var controller: ReaderPageRasterPreparationController
			retryProbe.startFreshAttempt = {
				if (autoStartRequestedPrewarm) controller.prewarmAdjacent()
			}
			controller = ReaderPageRasterPreparationController(
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
												rasterBackgroundBatchController = legacyBackground,
				passiveRasterPreparationPortProvider = passiveOwner::port,
				onRequestPrewarm = retryProbe::requestFreshManifest,
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
				passiveOwner = passiveOwner,
				retryProbe = retryProbe,
				ownership = ownership,
				controller = controller,
				foreground = foreground,
				repair = repair,
				diagnosticMessages = diagnosticMessages,
				states = states
			)
		}
	}
}

private fun task9PreparationPlan(): ReaderPageRasterPreparationPlan =
	ReaderPageRasterPreparationPlan(
		centerPageIndex = 20,
		pageCount = 44,
		layoutMode = "spread",
		readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
		step = 2,
		currentChapterIndex = 4,
		currentChapterPageStartIndex = 12,
		currentChapterPageCount = 24,
		previousChapterPageStartIndex = 4,
		previousChapterPageCount = 8,
		nextChapterPageStartIndex = 36,
		nextChapterPageCount = 4,
		targets = listOf(
			ReaderPageRasterBatchTarget(20, ReaderPageRasterPriority.Current),
			ReaderPageRasterBatchTarget(22, ReaderPageRasterPriority.NextTransition),
			ReaderPageRasterBatchTarget(18, ReaderPageRasterPriority.PreviousTransition),
			ReaderPageRasterBatchTarget(24, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(26, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(28, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(30, ReaderPageRasterPriority.NextLookahead),
			ReaderPageRasterBatchTarget(16, ReaderPageRasterPriority.PreviousLookahead),
			ReaderPageRasterBatchTarget(14, ReaderPageRasterPriority.PreviousLookahead),
			ReaderPageRasterBatchTarget(12, ReaderPageRasterPriority.PreviousLookahead),
			ReaderPageRasterBatchTarget(10, ReaderPageRasterPriority.PreviousLookahead),
			ReaderPageRasterBatchTarget(32, ReaderPageRasterPriority.CurrentChapter),
			ReaderPageRasterBatchTarget(34, ReaderPageRasterPriority.CurrentChapter),
			ReaderPageRasterBatchTarget(36, ReaderPageRasterPriority.NextChapter),
			ReaderPageRasterBatchTarget(38, ReaderPageRasterPriority.NextChapter),
			ReaderPageRasterBatchTarget(40, ReaderPageRasterPriority.NextChapter),
			ReaderPageRasterBatchTarget(42, ReaderPageRasterPriority.NextChapter),
			ReaderPageRasterBatchTarget(8, ReaderPageRasterPriority.PreviousChapter),
			ReaderPageRasterBatchTarget(6, ReaderPageRasterPriority.PreviousChapter),
			ReaderPageRasterBatchTarget(4, ReaderPageRasterPriority.PreviousChapter),
			ReaderPageRasterBatchTarget(2, ReaderPageRasterPriority.PreviousChapter),
			ReaderPageRasterBatchTarget(0, ReaderPageRasterPriority.PreviousChapter)
		)
	)

private fun task9PassiveSessionMismatch(
	rasterGeneration: Long,
	onBitmapReleased: () -> Unit
): Pair<ReaderPassiveRasterAdmissionContext, ReaderPassiveRasterCaptureResult<Bitmap>> {
	val commit = ReaderPassiveRasterCanonicalCommit(
		captureEpoch = 9L,
		liveFoliateSessionId = "live-session-current",
		publicationSessionGeneration = 71L,
		destinationCommitToken = "chapter=4;page=20",
		rasterProfileKey = "stage4-profile",
		paginationFingerprint = "stage4-pagination",
		layoutFingerprint = "stage4-layout",
		decorationFingerprint = "stage4-decoration",
		viewportAndCaptureGeometry = ReaderPassiveRasterGeometry(
			viewportWidth = 20,
			viewportHeight = 30,
			captureLeft = 0,
			captureTop = 0,
			captureRight = 20,
			captureBottom = 30
		),
		rasterGeneration = rasterGeneration
	)
	val issuer = ReaderPassiveRasterManifestIssuer()
	val canonical = issuer.replaceCanonicalCommit(commit)
	val manifest = assertNotNull(
		issuer.issue(
			liveCommit = canonical,
			opaqueCaptureTarget = "stage4-target-20",
			visualPageOrdinal = 20
		)
	)
	val receipt = ReaderPassiveRasterCaptureReceipt(
		passiveSessionId = "passive-session-stale",
		echoedManifestSequence = manifest.manifestSequence,
		echoedCaptureEpoch = manifest.captureEpoch,
		echoedLiveFoliateSessionId = manifest.liveFoliateSessionId,
		echoedPublicationSessionGeneration = manifest.publicationSessionGeneration,
		echoedDestinationCommitToken = manifest.destinationCommitToken,
		observedCaptureTarget = manifest.opaqueCaptureTarget,
		observedVisualPageOrdinal = manifest.visualPageOrdinal,
		observedRasterProfileKey = manifest.rasterProfileKey,
		observedPaginationFingerprint = manifest.paginationFingerprint,
		observedLayoutFingerprint = manifest.layoutFingerprint,
		observedDecorationFingerprint = manifest.decorationFingerprint,
		observedViewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
		echoedRasterGeneration = manifest.rasterGeneration,
		passiveCommitSequence = 12L
	)
	val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
	val capture = ReaderPassiveRasterCaptureResult(
		manifest = manifest,
		receipt = receipt,
		raster = ReaderPassiveRasterOwnership(bitmap) { rejected ->
			onBitmapReleased()
			if (!rejected.isRecycled) rejected.recycle()
		}
	)
	val context = ReaderPassiveRasterAdmissionContext(
		expectedManifestSequence = manifest.manifestSequence,
		currentCaptureEpoch = manifest.captureEpoch,
		currentLiveFoliateSessionId = manifest.liveFoliateSessionId,
		activePublicationSessionGeneration = manifest.publicationSessionGeneration,
		currentDestinationCommitToken = manifest.destinationCommitToken,
		currentOpaqueCaptureTarget = manifest.opaqueCaptureTarget,
		currentVisualPageOrdinal = manifest.visualPageOrdinal,
		currentRasterProfileKey = manifest.rasterProfileKey,
		currentPaginationFingerprint = manifest.paginationFingerprint,
		currentLayoutFingerprint = manifest.layoutFingerprint,
		currentDecorationFingerprint = manifest.decorationFingerprint,
		currentViewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
		currentRasterGeneration = manifest.rasterGeneration,
		activePassiveSessionId = "passive-session-current",
		expectedPassiveCommitSequence = receipt.passiveCommitSequence
	)
	return context to capture
}

private fun task9PersistentCapture(): ReaderPageTurnCaptureResult = ReaderPageTurnCaptureResult(
	bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
	sourceRectInWindow = Rect(0, 0, 20, 30),
	geometry = ReaderPageTurnCaptureGeometry(
		viewportWidth = 20.0,
		viewportHeight = 30.0,
		mode = ReaderPageTurnLayoutMode.Spread,
		pages = listOf(
			ReaderPageTurnPageRect(
				role = ReaderPageTurnPageRole.Left,
				left = 0.0,
				top = 0.0,
				width = 10.0,
				height = 30.0
			),
			ReaderPageTurnPageRect(
				role = ReaderPageTurnPageRole.Right,
				left = 10.0,
				top = 0.0,
				width = 10.0,
				height = 30.0
			)
		)
	),
	elapsedMs = 1L
)

private fun task9ReferenceSnapshot(): ReaderPageSlideSnapshot = ReaderPageSlideSnapshot(
	key = ReaderPageSlideSnapshotKey(
		visualPageIndex = 20,
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
