package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageTurnBundleTest {
	@Test
	fun animationBitmapDimensionsFollowConfiguredQuality() {
		assertEquals(250, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Low))
		assertEquals(500, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Balanced))
		assertEquals(750, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.High))
		assertEquals(1_000, readerPageTurnAnimationBitmapDimension(1_000, ReaderPageBitmapQuality.Native))
	}

	@Test
	fun snapshotIdentityIncludesBitmapQuality() {
		val balanced = ReaderPageSlideSnapshotKey(
			visualPageIndex = 7,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			bitmapQuality = ReaderPageBitmapQuality.Balanced,
			bitmapWidth = 500,
			bitmapHeight = 800,
			surfaceWidth = 1_000,
			surfaceHeight = 1_600
		)
		val high = balanced.copy(bitmapQuality = ReaderPageBitmapQuality.High)

		assertNotEquals(balanced, high)
	}

	@Test
	fun snapshotWindowWarmsForwardReadingBeforeBackwardHistory() {
		assertEquals(
			listOf(6, 7, 8, 5, 4),
			readerPageSlideSnapshotWindow(centerPageIndex = 6, step = 1, pageCount = 12)
		)
	}

	@Test
	fun snapshotWindowUsesSpreadStepsAndClipsBookBoundaries() {
		assertEquals(
			listOf(2, 4, 6, 0),
			readerPageSlideSnapshotWindow(centerPageIndex = 2, step = 2, pageCount = 8)
		)
		assertEquals(
			listOf(6, 4, 2),
			readerPageSlideSnapshotWindow(centerPageIndex = 6, step = 2, pageCount = 8)
		)
	}

	@Test
	fun staleForegroundSourceIsRejectedAgainstDifferentExpectedTarget() {
		val source = authoredPage(accentX = 13, accentColor = 0xff204060.toInt())
		val target = authoredPage(accentX = 51, accentColor = 0xffa03020.toInt())
		var releases = 0

		assertEquals(
			ReaderPageRelocationContentValidationResult.ContentRejected,
			readerPageSemanticLiveValidationResult(
				candidate = source,
				expectedTarget = target,
				expectedSource = source,
				isStillCurrent = true,
				releaseCandidate = { releases += 1 }
			)
		)
		assertEquals(1, releases)
	}

	@Test
	fun expectedTargetRasterIsAcceptedWithMinorCaptureNoise() {
		val target = authoredPage(accentX = 51, accentColor = 0xffa03020.toInt())
		var releases = 0

		assertEquals(
			ReaderPageRelocationContentValidationResult.Accepted,
			readerPageSemanticLiveValidationResult(
				candidate = target.withChannelNoise(3),
				expectedTarget = target,
				expectedSource = authoredPage(
					accentX = 13,
					accentColor = 0xff204060.toInt()
				),
				isStillCurrent = true,
				releaseCandidate = { releases += 1 }
			)
		)
		assertEquals(1, releases)
	}

	@Test
	fun moreThanFourBoundedGlyphEdgeOutliersRequireDistinctSourceDirection() {
		val target = authoredPage(accentX = 51, accentColor = 0xffa03020.toInt())
		val source = target.copy().apply {
			fillRect(left = 10, top = 44, right = 16, bottom = 48, color = 0xff202020.toInt())
		}
		val resampledTarget = target.copy().apply {
			fillRect(left = 3, top = 3, right = 4, bottom = 4, color = 0xff202020.toInt())
			fillRect(left = 76, top = 3, right = 77, bottom = 4, color = 0xff202020.toInt())
			fillRect(left = 3, top = 56, right = 4, bottom = 57, color = 0xff202020.toInt())
			fillRect(left = 40, top = 3, right = 41, bottom = 4, color = 0xff202020.toInt())
			fillRect(left = 40, top = 56, right = 41, bottom = 57, color = 0xff202020.toInt())
			fillRect(left = 76, top = 56, right = 77, bottom = 57, color = 0xff202020.toInt())
		}

		assertTrue(readerPageLiveRasterMatchesExpected(resampledTarget, target, source))
		assertFalse(readerPageLiveRasterMatchesExpected(resampledTarget, target, null))
		assertFalse(readerPageLiveRasterMatchesExpected(source, target, source))
	}

	@Test
	fun blankAndUnrelatedRastersAreRejected() {
		val source = authoredPage(accentX = 13, accentColor = 0xff204060.toInt())
		val target = authoredPage(accentX = 51, accentColor = 0xffa03020.toInt())
		val blank = TestRaster.solid(target.width, target.height, PaperColor)
		val unrelated = authoredPage(accentX = 31, accentColor = 0xff188050.toInt())

		assertFalse(readerPageLiveRasterMatchesExpected(blank, target, source))
		assertFalse(readerPageLiveRasterMatchesExpected(unrelated, target, source))
	}

	@Test
	fun sparseTargetStillRejectsBlankCandidateWithoutSourceDirection() {
		val target = TestRaster.solid(80, 60, PaperColor).apply {
			fillRect(
				left = 20,
				top = 20,
				right = 23,
				bottom = 23,
				color = 0xff202020.toInt()
			)
		}
		val blank = TestRaster.solid(target.width, target.height, PaperColor)

		assertFalse(
			readerPageLiveRasterMatchesExpected(
				candidate = blank,
				expectedTarget = target,
				expectedSource = null
			)
		)
	}

	@Test
	fun authoredIdenticalSparseSourceAndTargetStillRejectBlankCandidate() {
		val target = TestRaster.solid(80, 60, PaperColor).apply {
			fillRect(
				left = 20,
				top = 20,
				right = 23,
				bottom = 23,
				color = 0xff202020.toInt()
			)
		}
		val source = target.copy()
		val blank = TestRaster.solid(target.width, target.height, PaperColor)

		assertFalse(
			readerPageLiveRasterMatchesExpected(
				candidate = blank,
				expectedTarget = target,
				expectedSource = source
			)
		)
	}

	@Test
	fun productionRasterDifferenceBetweenLegacySamplePointsRejectsStaleSource() {
		val source = TestRaster.solid(1_080, 1_920, PaperColor)
		val target = source.copy().apply {
			fillRect(
				left = 20,
				top = 20,
				right = 25,
				bottom = 25,
				color = 0xff202020.toInt()
			)
		}

		assertFalse(
			readerPageLiveRasterMatchesExpected(
				candidate = source,
				expectedTarget = target,
				expectedSource = source
			)
		)
		assertTrue(
			readerPageLiveRasterMatchesExpected(
				candidate = target.withChannelNoise(3),
				expectedTarget = target,
				expectedSource = source
			)
		)
	}

	@Test
	fun identicalAuthoredSourceAndTargetStillAcceptAbsoluteTargetMatch() {
		val source = authoredPage(accentX = 37, accentColor = 0xff604090.toInt())
		val target = source.copy()

		assertTrue(
			readerPageLiveRasterMatchesExpected(
				candidate = target.withChannelNoise(2),
				expectedTarget = target,
				expectedSource = source
			)
		)
	}

	@Test
	fun semanticLiveValidationReleasesCandidateAndPrioritizesInvalidation() {
		val target = authoredPage(accentX = 51, accentColor = 0xffa03020.toInt())
		var releases = 0

		assertEquals(
			ReaderPageRelocationContentValidationResult.Invalidated,
			readerPageSemanticLiveValidationResult(
				candidate = target,
				expectedTarget = target,
				expectedSource = null,
				isStillCurrent = false,
				releaseCandidate = { releases += 1 }
			)
		)
		assertEquals(1, releases)
	}

	@Test
	fun cancelBeforeWorkerAttachDefersReleaseUntilWorkerTerminal() {
		val target = Any()
		val source = Any()
		val candidate = Any()
		val released = mutableListOf<Any>()
		var workerCancellations = 0
		var terminalCalls = 0
		val ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = target,
			expectedSource = source,
			releaseExpected = released::add,
			releaseCandidate = released::add,
			onTerminal = { terminalCalls += 1 }
		)

		val work = assertNotNull(ownership.beginWorker(candidate))
		assertTrue(work.expectedTarget === target)
		assertTrue(work.expectedSource === source)
		assertTrue(work.candidate === candidate)
		assertTrue(ownership.cancel())
		assertTrue(released.isEmpty())
		ownership.attachWorker(
			ReaderPageRelocationContentValidationHandle {
				workerCancellations += 1
				true
			}
		)
		assertEquals(1, workerCancellations)
		assertFalse(
			ownership.recordWorkerResult(
				ReaderPageRelocationContentValidationResult.Accepted
			)
		)
		assertFalse(ownership.workerFinished(cancelled = true))
		assertEquals(listOf(target, source, candidate), released)
		assertEquals(1, terminalCalls)
		assertFalse(ownership.cancel())
	}

	@Test
	fun cancelDuringWorkerCancelsItAndReleasesAfterWorkerTerminal() {
		val target = Any()
		val source = Any()
		val candidate = Any()
		val released = mutableListOf<Any>()
		var workerCancellations = 0
		val ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = target,
			expectedSource = source,
			releaseExpected = released::add,
			releaseCandidate = released::add
		)

		assertNotNull(ownership.beginWorker(candidate))
		ownership.attachWorker(
			ReaderPageRelocationContentValidationHandle {
				workerCancellations += 1
				true
			}
		)

		assertTrue(ownership.cancel())
		assertEquals(1, workerCancellations)
		assertTrue(released.isEmpty())
		assertFalse(ownership.workerFinished(cancelled = true))
		assertEquals(listOf(target, source, candidate), released)
		assertFalse(ownership.workerFinished(cancelled = true))
		assertEquals(listOf(target, source, candidate), released)
	}

	@Test
	fun workerCompletionAndCancellationArbitrateBeforeMainPublication() {
		val target = Any()
		val source = Any()
		val candidate = Any()
		val released = mutableListOf<Any>()
		var publications = 0
		val ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = target,
			expectedSource = source,
			releaseExpected = released::add,
			releaseCandidate = released::add
		)

		assertNotNull(ownership.beginWorker(candidate))
		assertTrue(
			ownership.recordWorkerResult(
				ReaderPageRelocationContentValidationResult.Accepted
			)
		)
		assertTrue(ownership.workerFinished(cancelled = false))
		assertTrue(ownership.cancel())
		assertFalse(ownership.publish { _, _, _, _ -> publications += 1 })
		assertEquals(0, publications)
		assertEquals(listOf(target, source, candidate), released)
	}

	@Test
	fun workerCompletionBeforeHandleAttachCanPublishExactlyOnce() {
		val target = Any()
		val source = Any()
		val candidate = Any()
		val released = mutableListOf<Any>()
		var lateWorkerCancellations = 0
		var publishedResult: ReaderPageRelocationContentValidationResult? = null
		val ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = target,
			expectedSource = source,
			releaseExpected = released::add,
			releaseCandidate = released::add
		)

		assertNotNull(ownership.beginWorker(candidate))
		assertTrue(
			ownership.recordWorkerResult(
				ReaderPageRelocationContentValidationResult.Accepted
			)
		)
		assertTrue(ownership.workerFinished(cancelled = false))
		ownership.attachWorker(
			ReaderPageRelocationContentValidationHandle {
				lateWorkerCancellations += 1
				true
			}
		)
		assertEquals(1, lateWorkerCancellations)
		assertTrue(ownership.publish { completedTarget, completedSource, completedCandidate, result ->
			assertTrue(completedTarget === target)
			assertTrue(completedSource === source)
			assertTrue(completedCandidate === candidate)
			publishedResult = result
		})
		assertEquals(ReaderPageRelocationContentValidationResult.Accepted, publishedResult)
		assertEquals(listOf(target, source, candidate), released)
		assertFalse(ownership.publish { _, _, _, _ -> publishedResult = null })
		assertFalse(ownership.cancel())
	}

	@Test
	fun lateCaptureAndLateMainResultReleaseWithoutPublication() {
		val target = Any()
		val source = Any()
		val lateWorkerCandidate = Any()
		val lateCompletedCandidate = Any()
		val released = mutableListOf<Any>()
		var lateCaptureCancellations = 0
		var publications = 0
		val ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = target,
			expectedSource = source,
			releaseExpected = released::add,
			releaseCandidate = released::add
		)

		assertTrue(ownership.cancel())
		ownership.attachCapture(
			ReaderPageRelocationContentValidationHandle {
				lateCaptureCancellations += 1
				true
			}
		)
		assertEquals(1, lateCaptureCancellations)
		assertNull(ownership.beginWorker(lateWorkerCandidate))
		assertFalse(
			ownership.completeCapture(
				candidate = lateCompletedCandidate,
				result = ReaderPageRelocationContentValidationResult.ContentRejected
			)
		)
		assertFalse(ownership.publish { _, _, _, _ -> publications += 1 })
		assertEquals(0, publications)
		assertEquals(
			listOf(target, source, lateWorkerCandidate, lateCompletedCandidate),
			released
		)
	}

	@Test
	fun liveValidationCurrentnessRequiresOriginalOpenBundleGenerationAndCaller() {
		assertTrue(
			readerPageLiveValidationIsCurrent(
				expectedGeneration = 8,
				currentGeneration = 8,
				closed = false,
				callerCurrent = true
			)
		)
		assertFalse(
			readerPageLiveValidationIsCurrent(
				expectedGeneration = 8,
				currentGeneration = 9,
				closed = false,
				callerCurrent = true
			)
		)
		assertFalse(
			readerPageLiveValidationIsCurrent(
				expectedGeneration = 8,
				currentGeneration = 8,
				closed = true,
				callerCurrent = true
			)
		)
		assertFalse(
			readerPageLiveValidationIsCurrent(
				expectedGeneration = 8,
				currentGeneration = 8,
				closed = false,
				callerCurrent = false
			)
		)
	}

	@Test
	fun acceptedWorkerRequiresTheExactThirdLiveReceipt() {
		val target = liveTarget()
		val acceptedReceipt = liveReceipt()

		assertEquals(
			ReaderPageRelocationContentValidationResult.Accepted,
			readerPageLiveValidationReceiptFencedResult(
				workerResult = ReaderPageRelocationContentValidationResult.Accepted,
				target = target,
				acceptedReceipt = acceptedReceipt,
				currentReceipt = acceptedReceipt,
				isStillCurrent = true
			)
		)
	}

	@Test
	fun receiptSequenceChangeAfterWorkerComparisonInvalidatesAcceptance() {
		assertEquals(
			ReaderPageRelocationContentValidationResult.Invalidated,
			readerPageLiveValidationReceiptFencedResult(
				workerResult = ReaderPageRelocationContentValidationResult.Accepted,
				target = liveTarget(),
				acceptedReceipt = liveReceipt(),
				currentReceipt = liveReceipt().copy(presentationSequence = 72),
				isStillCurrent = true
			)
		)
	}

	@Test
	fun targetOrCurrentnessChangeAtThirdReceiptInvalidatesAcceptance() {
		assertEquals(
			ReaderPageRelocationContentValidationResult.Invalidated,
			readerPageLiveValidationReceiptFencedResult(
				workerResult = ReaderPageRelocationContentValidationResult.Accepted,
				target = liveTarget(),
				acceptedReceipt = liveReceipt(),
				currentReceipt = liveReceipt().copy(token = "live-beta"),
				isStillCurrent = true
			)
		)
		assertEquals(
			ReaderPageRelocationContentValidationResult.Invalidated,
			readerPageLiveValidationReceiptFencedResult(
				workerResult = ReaderPageRelocationContentValidationResult.Accepted,
				target = liveTarget(),
				acceptedReceipt = liveReceipt(),
				currentReceipt = liveReceipt(),
				isStillCurrent = false
			)
		)
	}

	@Test
	fun rejectedWorkerResultDoesNotBecomeAcceptedAtReceiptFence() {
		assertEquals(
			ReaderPageRelocationContentValidationResult.ContentRejected,
			readerPageLiveValidationReceiptFencedResult(
				workerResult = ReaderPageRelocationContentValidationResult.ContentRejected,
				target = liveTarget(),
				acceptedReceipt = liveReceipt(),
				currentReceipt = liveReceipt(),
				isStillCurrent = true
			)
		)
	}

	@Test
	fun cancellationBetweenWorkerComparisonAndThirdReceiptCancelsFenceAndReleasesOnce() {
		val target = Any()
		val candidate = Any()
		val released = mutableListOf<Any>()
		var finalFenceCancellations = 0
		var publications = 0
		val ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = target,
			expectedSource = null,
			releaseExpected = released::add,
			releaseCandidate = released::add
		)
		assertNotNull(ownership.beginWorker(candidate))
		assertTrue(
			ownership.recordWorkerResult(
				ReaderPageRelocationContentValidationResult.Accepted
			)
		)
		assertTrue(ownership.workerFinished(cancelled = false))
		ownership.attachFinalFence(
			ReaderPageRelocationContentValidationHandle {
				finalFenceCancellations += 1
				true
			}
		)

		assertTrue(ownership.cancel())
		assertEquals(1, finalFenceCancellations)
		assertFalse(ownership.publish { _, _, _, _ -> publications += 1 })
		assertEquals(0, publications)
		assertEquals(listOf(target, candidate), released)
	}

	@Test
	fun liveValidatorUsesCompositorCaptureThenSynchronousThirdReceiptPublication() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBundleSource.android.kt"
		).readText()
		val validator = source.substringAfter(
			"fun validateLivePresentation("
		).substringBefore("fun capturePreparedRasterPage(")

		val publication = source.substringAfter(
			"private fun postLiveValidationResult("
		).substringBefore("fun validateLivePresentation(")

		assertTrue(validator.contains("bitmapSource.captureLiveCompositedSurface("))
		assertFalse(validator.contains("bitmapSource.capturePresentedSurface("))
		assertTrue(publication.contains("bitmapSource.confirmLivePresentationReceipt("))
		val thirdReceiptCallback = publication
			.substringAfter("bitmapSource.confirmLivePresentationReceipt(")
			.substringAfter(") { currentReceipt ->")
			.substringBefore("ownership.attachFinalFence(")
		assertTrue(thirdReceiptCallback.contains("ownership.publish"))
		assertTrue(thirdReceiptCallback.contains("onValidated("))
		assertFalse(thirdReceiptCallback.contains("mainHandler.post"))
	}

	@Test
	fun liveValidatorUsesExpectedRastersAndNeverPublishesTransientCapture() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBundleSource.android.kt"
		).readText()
		val validator = source.substringAfter(
			"fun validateLivePresentation("
		).substringBefore("fun capturePreparedRasterPage(")

		assertTrue(validator.contains("expectedTarget: ReaderPageSlideSnapshot"))
		assertTrue(validator.contains("expectedSource: ReaderPageSlideSnapshot?"))
		assertTrue(validator.contains("ReaderPageTurnPresentationTarget.Live("))
		assertTrue(validator.contains("token = request.token.value"))
		assertTrue(validator.contains("pageIndex = request.destinationOrdinal.toLong()"))
		assertTrue(validator.contains("foliateSessionId = request.foliateSessionId"))
		assertTrue(validator.contains("rasterGeneration = request.rasterGeneration"))
		assertTrue(validator.contains("textureGeneration = request.textureGeneration"))
		assertTrue(validator.contains("ReaderPageLiveValidationSnapshotOwnership("))
		assertTrue(validator.contains("bitmapSource.captureLiveCompositedSurface("))
		assertTrue(validator.contains("val expectedTargetBitmapWidth = expectedTarget.bitmap.width"))
		assertTrue(validator.contains("val expectedTargetBitmapHeight = expectedTarget.bitmap.height"))
		assertTrue(validator.contains("expectedBitmapWidth = expectedTargetBitmapWidth"))
		assertTrue(validator.contains("expectedBitmapHeight = expectedTargetBitmapHeight"))
		assertTrue(validator.contains("isStillCurrent = ::validationIsCurrent"))
		assertFalse(validator.contains("isStillCurrent = isStillCurrent"))
		assertTrue(validator.contains("rasterScope.launch(Dispatchers.Default)"))
		val worker = validator.substringAfter("rasterScope.launch(Dispatchers.Default)")
			.substringBefore("ownership.attachWorker(")
		assertTrue(worker.contains("readerPageLiveCaptureValidationResult("))
		assertTrue(worker.contains("workerContext.ensureActive()"))
		val publication = source.substringAfter("private fun postLiveValidationResult(")
			.substringBefore("fun validateLivePresentation(")
		assertTrue(publication.contains("mainHandler.post"))
		assertTrue(publication.contains("ownership.publish"))
		assertTrue(source.contains("private fun readerPageLiveCaptureMatchesExpected("))
		assertTrue(source.contains("cancellationCheck()"))
		assertFalse(validator.contains("readerPageSemanticLiveValidationResult("))
		assertFalse(validator.contains("readerPageTransientLiveValidationResult("))
		listOf(
			"putSnapshot(",
			"cacheSnapshot(",
			"schedulePersistentSnapshot(",
			"publicationLedger.begin("
		).forEach { forbidden -> assertFalse(validator.contains(forbidden)) }
	}

	@Test
	fun liveValidationFencesBundleGenerationAndCloseDrainsItsWorkers() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBundleSource.android.kt"
		).readText()
		val validator = source.substringAfter(
			"fun validateLivePresentation("
		).substringBefore("fun capturePreparedRasterPage(")
		val worker = validator.substringAfter("rasterScope.launch(Dispatchers.Default)")
			.substringBefore("ownership.attachWorker(")
		val hydrationClose = source.substringAfter("closeRasterHydrationWorkers = {")
			.substringBefore("},\n\t\tclosePersistentStore")
		val invalidation = source.substringAfter("fun invalidate(reason: String) {")
			.substringBefore("fun fenceForClose()")

		assertTrue(validator.contains("val validationGeneration = synchronized(closeFenceLock)"))
		assertTrue(validator.contains("fun validationIsCurrent(): Boolean"))
		assertTrue(validator.contains("expectedGeneration = validationGeneration"))
		assertTrue(validator.contains("currentGeneration = activeGeneration"))
		assertTrue(validator.contains("closed = closed"))
		assertTrue(validator.contains("callerCurrent = callerCurrent"))
		assertTrue(worker.contains("liveValidationGenerationIsCurrent()"))
		assertTrue(
			invalidation.contains(
				"synchronized(closeFenceLock) { activeGeneration += 1 }"
			)
		)
		assertTrue(hydrationClose.indexOf("hydrationScheduler.closeAndJoin()") >= 0)
		assertTrue(
			hydrationClose.indexOf("hydrationScheduler.closeAndJoin()") <
				hydrationClose.indexOf("rasterJob.join()")
		)
		assertTrue(hydrationClose.contains("activeLiveValidations.isEmpty()"))
		assertTrue(hydrationClose.contains("synchronized(closeFenceLock)"))
	}

	@Test
	fun currentLayoutSnapshotLookupFencesGenerationAndQuality() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBundleSource.android.kt"
		).readText()
		val lookup = source.substringAfter(
			"fun retainedCurrentLayoutSnapshot("
		).substringBefore("private fun retainedSnapshot(")

		assertTrue(lookup.contains("expectedGeneration: Long"))
		assertTrue(lookup.contains("expectedQuality: ReaderPageBitmapQuality"))
		assertTrue(lookup.contains("expectedGeneration != activeGeneration"))
		assertTrue(lookup.contains("expectedQuality != bitmapQuality"))
		assertTrue(lookup.contains("key.bitmapQuality == expectedQuality"))
	}

	private fun liveTarget() = ReaderPageTurnPresentationTarget.Live(
		token = "live-alpha",
		pageIndex = 9,
		foliateSessionId = "session-alpha",
		rasterGeneration = 13,
		textureGeneration = 17
	)

	private fun liveReceipt() = ReaderPageTurnPresentationReceipt(
		scope = ReaderPageTurnPresentationScope.Live,
		token = "live-alpha",
		pageIndex = 9,
		foliateSessionId = "session-alpha",
		rasterGeneration = 13,
		textureGeneration = 17,
		presentationSequence = 71
	)

	private companion object {
		val PaperColor: Int = 0xffead9ae.toInt()

		fun authoredPage(accentX: Int, accentColor: Int): TestRaster =
			TestRaster.create(width = 80, height = 60, color = PaperColor).apply {
				fillRect(left = 8, top = 8, right = 72, bottom = 12, color = 0xff303030.toInt())
				fillRect(left = 8, top = 20, right = 64, bottom = 23, color = 0xff505050.toInt())
				fillRect(left = 8, top = 30, right = 70, bottom = 33, color = 0xff404040.toInt())
				fillRect(left = accentX, top = 39, right = accentX + 12, bottom = 53, color = accentColor)
			}
	}

	private class TestRaster private constructor(
		override val width: Int,
		override val height: Int,
		private val pixels: IntArray
	) : ReaderPageRasterPixels {
		override fun readRows(top: Int, rowCount: Int, destination: IntArray) {
			pixels.copyInto(
				destination = destination,
				destinationOffset = 0,
				startIndex = top * width,
				endIndex = (top + rowCount) * width
			)
		}

		fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
			for (y in top until bottom) {
				for (x in left until right) pixels[y * width + x] = color
			}
		}

		fun copy(): TestRaster = TestRaster(width, height, pixels.copyOf())

		fun withChannelNoise(delta: Int): TestRaster = TestRaster(
			width = width,
			height = height,
			pixels = IntArray(pixels.size) { index ->
				val color = pixels[index]
				val red = ((color ushr 16 and 0xff) + delta).coerceAtMost(0xff)
				val green = ((color ushr 8 and 0xff) + delta).coerceAtMost(0xff)
				val blue = ((color and 0xff) + delta).coerceAtMost(0xff)
				0xff000000.toInt() or (red shl 16) or (green shl 8) or blue
			}
		)

		companion object {
			fun create(width: Int, height: Int, color: Int): TestRaster =
				TestRaster(width, height, IntArray(width * height) { color })

			fun solid(width: Int, height: Int, color: Int): TestRaster =
				create(width, height, color)
		}
	}
}
