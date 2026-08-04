package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val ReaderPageTestBackground = 0xF5F0E8
private const val ReaderPageTestForeground = 0x34312D
private const val ReaderPageTestLowContrast = 0xE1DCD4

class ReaderPageTurnBitmapSourceTest {
	@Test
	fun successfulWebViewCapturePublishesAnExplicitlyOpaqueBitmap() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val capture = source
			.substringAfter("private fun captureVisualState(")
			.substringBefore("internal fun parseGeometry(")
		val accepted = capture
			.substringAfter("foreground?.renderable == true")
			.substringBefore("onCaptured(ReaderPageTurnCaptureResult")

		assertTrue(
			capture.contains(
				"val backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb)"
			)
		)
		assertTrue(capture.contains("bitmap.eraseColor(backgroundColor)"))
		assertTrue(source.contains("bitmap.eraseColor(backgroundColorArgb)"))
		assertTrue(accepted.contains("bitmap.setHasAlpha(false)"))
		assertTrue(accepted.contains("bitmap.setPremultiplied(true)"))
	}

	@Test
	fun currentSurfaceCaptureExcludesWindowOverlays() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val request = source
			.substringAfter("private fun capture(")
			.substringBefore("private fun captureVisualState(")
		val capture = source
			.substringAfter("private fun captureVisualState(")
			.substringBefore("internal fun parseGeometry(")
		val helper = source
			.substringAfter("private fun drawWebViewIntoBitmap(")
			.substringBefore("private data class ReaderPageTurnForegroundAnalysis")
		val draw = "drawWebViewIntoBitmap("
		val analyze = "bitmap.analyzeRenderableForeground()"
		val visualFence = "webView.postVisualStateCallback("
		val nextFrame = "webView.postOnAnimation {"
		val captureVisualState = "captureVisualState("

		assertFalse(capture.contains("PixelCopy"))
		assertTrue(request.indexOf(visualFence) < request.indexOf(nextFrame))
		assertTrue(request.indexOf(nextFrame) < request.indexOf(captureVisualState))
		assertTrue(capture.contains(draw))
		assertTrue(capture.indexOf(draw) < capture.indexOf(analyze))
		assertTrue(capture.contains("previousRejectedSignature: ReaderPageTurnRejectedForegroundSignature? = null"))
		assertTrue(capture.contains("foreground?.settlementSignature(allowStableLowContrast)"))
		assertFalse(capture.contains("previousSparseSignature"))
		assertFalse(capture.contains("previousLowContrastSignature"))
		assertTrue(helper.contains("webView.draw(canvas)"))
		assertTrue(
			helper.contains(
				"webViewLocationInWindow[0] - sourceRectInWindow.left.toFloat()"
			)
		)
		assertTrue(
			helper.contains(
				"webViewLocationInWindow[1] - sourceRectInWindow.top.toFloat()"
			)
		)
	}

	@Test
	fun preparedGeometryCaptureAvoidsASecondRuntimeGeometryQuery() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val preparedCapture = source
			.substringAfter(
				"fun captureSurface(\n\t\twebView: WebView,\n\t\t" +
					"geometry: ReaderPageTurnCaptureGeometry,"
			)
			.substringBefore("fun capturePresentedSurface(")

		assertTrue(preparedCapture.contains("captureResolvedGeometry(webView, geometry, onCaptured)"))
		assertFalse(preparedCapture.contains("evaluateJavascript"))
	}

	@Test
	fun presentedSurfaceCaptureFencesTheExistingPipelineWithScopeSpecificReceipts() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val presented = source
			.substringAfter("fun capturePresentedSurface(")
			.substringBefore("suspend fun captureSurfaceAwait")
		val initialReceipt = presented.indexOf("queryPresentationReceipt(")
		val capture = presented.indexOf(
			"captureSurface(webView, allowStableLowContrast = true)"
		)
		val finalReceipt = presented.indexOf("queryPresentationReceipt(", initialReceipt + 1)

		assertTrue(initialReceipt >= 0 && initialReceipt < capture)
		assertTrue(capture < finalReceipt)
		assertTrue(presented.contains("ReaderPageTurnPresentedCaptureOwnership("))
		assertTrue(presented.contains("ownership.retain(candidate)"))
		assertTrue(presented.contains("ownership.complete()"))
		assertTrue(presented.contains("return ownership"))
		assertTrue(presented.contains("readerPageTurnPresentedSurfaceCandidate("))
		assertTrue(
			source.contains(
				"captureSurface(webView, allowStableLowContrast = false, onCaptured)"
			)
		)
		assertTrue(source.contains("pageTurnPreviewPresentationReceipt"))
		assertTrue(source.contains("pageTurnLivePresentationReceipt"))
	}

	@Test
	fun liveCaptureUsesPresentedPlayLikeCurlSurfacePixelCopy() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val liveCapture = source
			.substringAfter("fun captureLiveCompositedSurface(")
			.substringBefore("fun confirmLivePresentationReceipt(")

		val initialReceipt = liveCapture.indexOf("queryPresentationReceipt(webView, target)")
		val presentedFrame = liveCapture.indexOf("rendererSurface.requestNextPresentedFrame")
		val pixelCopy = liveCapture.indexOf("PixelCopy.request(")
		val finalReceipt = liveCapture.indexOf(
			"queryPresentationReceipt(webView, target)",
			initialReceipt + 1
		)
		val externalWrite = liveCapture.indexOf("ownership.beginExternalWrite()")
		val callback = liveCapture
			.substringAfter("{ copyResult ->")
			.substringBefore("mainHandler")
		val presentedCallback = liveCapture
			.substringAfter("rendererSurface.requestNextPresentedFrame {")
			.substringBefore("}.getOrNull()")
		assertTrue(externalWrite >= 0 && externalWrite < pixelCopy)
		assertTrue(
			callback.indexOf("ownership.endExternalWrite()") <
				callback.indexOf("queryPresentationReceipt(webView, target)")
		)
		assertTrue(
			callback.indexOf("ownership.runIfExternalWriteCurrent") <
				callback.indexOf("bitmap.setHasAlpha")
		)
		assertTrue(initialReceipt >= 0 && initialReceipt < presentedFrame)
		assertTrue(initialReceipt < pixelCopy)
		assertTrue(pixelCopy < finalReceipt)
		assertTrue(presentedCallback.contains("if (!environmentCurrent())"))
		assertTrue(presentedCallback.contains("requestPixelCopy()"))
		assertTrue(liveCapture.contains("acceptedReceipt = finalReceipt"))
		assertTrue(liveCapture.contains("LiveCaptureMaximumPresentationAuthorityRefreshes"))
		assertTrue(liveCapture.contains("readerPageTurnLivePresentationAuthorityChanged("))
		assertTrue(liveCapture.contains("ownership.releaseCandidateForRetry()"))
		assertTrue(liveCapture.contains("start.run()"))
		assertTrue(liveCapture.contains("rendererSurface.cancelPresentedFrameRequest"))
		assertTrue(liveCapture.contains("rendererSurface.holder.surface"))
		assertTrue(liveCapture.contains("sourceRectInSurface"))
		assertTrue(liveCapture.contains("copyResult == PixelCopy.SUCCESS"))
		assertTrue(liveCapture.contains("sourceRectInWindow"))
		assertTrue(liveCapture.contains("Bitmap.Config.ARGB_8888"))
		assertFalse(liveCapture.contains("activity.window"))
		assertFalse(liveCapture.contains("registerFrameCommitCallback"))
		assertFalse(liveCapture.contains("addOnPreDrawListener"))
		assertFalse(liveCapture.contains("readerPageTurnAnimationBitmapDimension("))
		assertFalse(liveCapture.contains("webView.draw("))
		assertFalse(liveCapture.contains("drawWebViewIntoBitmap("))
		assertFalse(liveCapture.contains("captureSurface("))
		assertFalse(liveCapture.contains("getPixel("))
		assertFalse(liveCapture.contains("hash"))
	}

	@Test
	fun liveCaptureUsesExpectedTargetDimensionsWhilePreviewUsesQualityScaling() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val liveCapture = source
			.substringAfter("fun captureLiveCompositedSurface(")
			.substringBefore("fun confirmLivePresentationReceipt(")
		val previewCapture = source
			.substringAfter("private fun captureVisualState(")
			.substringBefore("internal fun parseGeometry(")

		assertTrue(liveCapture.contains("expectedBitmapWidth: Int"))
		assertTrue(liveCapture.contains("expectedBitmapHeight: Int"))
		assertTrue(liveCapture.contains("expectedBitmapWidth <= 0"))
		assertTrue(liveCapture.contains("expectedBitmapHeight <= 0"))
		assertTrue(liveCapture.contains("val captureQuality = bitmapQuality"))
		assertTrue(liveCapture.contains("captureQuality == bitmapQuality"))
		assertTrue(
			liveCapture.contains(
				"Bitmap.createBitmap(\n\t\t\t\t\t\t\texpectedBitmapWidth,\n" +
					"\t\t\t\t\t\t\texpectedBitmapHeight,"
			)
		)
		assertFalse(liveCapture.contains("readerPageTurnAnimationBitmapDimension("))
		assertTrue(previewCapture.contains("readerPageTurnAnimationBitmapDimension(pixelRect.width"))
		assertTrue(previewCapture.contains("readerPageTurnAnimationBitmapDimension(pixelRect.height"))
	}

	@Test
	fun everyLiveCaptureAttemptRequiresFreshRendererPresentationEvidence() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val liveCapture = source
			.substringAfter("fun captureLiveCompositedSurface(")
			.substringBefore("fun confirmLivePresentationReceipt(")
		val attempt = liveCapture
			.substringAfter("start = Runnable start@{")
			.substringBefore("startRunnable = start")

		assertTrue(
			attempt.indexOf("queryPresentationReceipt(webView, target)") <
				attempt.indexOf("rendererSurface.requestNextPresentedFrame")
		)
		val presentedCallback = attempt
			.substringAfter("rendererSurface.requestNextPresentedFrame {")
			.substringBefore("}.getOrNull()")
		assertTrue(presentedCallback.contains("if (!environmentCurrent())"))
		assertTrue(presentedCallback.contains("requestPixelCopy()"))
		assertTrue(attempt.contains("if (!environmentCurrent())"))
		assertTrue(attempt.contains("start.run()"))
		assertFalse(liveCapture.contains("postDelayed"))
	}

	@Test
	fun rendererSurfaceRectMapsWindowCoordinatesIntoTheGlBuffer() {
		assertEquals(
			ReaderPageTurnRendererSurfaceRegion(10, 20, 210, 220),
			readerPageTurnRendererSurfaceRect(
				sourceLeftInWindow = 110,
				sourceTopInWindow = 220,
				sourceRightInWindow = 310,
				sourceBottomInWindow = 420,
				rendererWindowLeft = 100,
				rendererWindowTop = 200,
				rendererWidth = 400,
				rendererHeight = 600,
				bufferWidth = 400,
				bufferHeight = 600
			)
		)
		assertEquals(
			ReaderPageTurnRendererSurfaceRegion(20, 40, 420, 440),
			readerPageTurnRendererSurfaceRect(
				sourceLeftInWindow = 110,
				sourceTopInWindow = 220,
				sourceRightInWindow = 310,
				sourceBottomInWindow = 420,
				rendererWindowLeft = 100,
				rendererWindowTop = 200,
				rendererWidth = 400,
				rendererHeight = 600,
				bufferWidth = 800,
				bufferHeight = 1200
			)
		)
	}

	@Test
	fun rendererSurfaceRectRejectsGeometryOutsideTheCanonicalSurface() {
		assertNull(
			readerPageTurnRendererSurfaceRect(
				sourceLeftInWindow = 90,
				sourceTopInWindow = 220,
				sourceRightInWindow = 310,
				sourceBottomInWindow = 420,
				rendererWindowLeft = 100,
				rendererWindowTop = 200,
				rendererWidth = 400,
				rendererHeight = 600,
				bufferWidth = 400,
				bufferHeight = 600
			)
		)
		assertNull(
			readerPageTurnRendererSurfaceRect(
				sourceLeftInWindow = 110,
				sourceTopInWindow = 220,
				sourceRightInWindow = 310,
				sourceBottomInWindow = 420,
				rendererWindowLeft = 100,
				rendererWindowTop = 200,
				rendererWidth = 0,
				rendererHeight = 600,
				bufferWidth = 400,
				bufferHeight = 600
			)
		)
	}

	@Test
	fun changedMatchingLiveReceiptRequestsFreshCapture() {
		val target = liveTarget()
		val initial = liveReceipt()
		val refreshed = initial.copy(presentationSequence = initial.presentationSequence + 1)

		assertTrue(
			readerPageTurnLivePresentationAuthorityChanged(
				target = target,
				initialReceipt = initial,
				finalReceipt = refreshed,
				isStillCurrent = true
			)
		)
		assertFalse(
			readerPageTurnLivePresentationAuthorityChanged(
				target = target,
				initialReceipt = initial,
				finalReceipt = initial,
				isStillCurrent = true
			)
		)
		assertFalse(
			readerPageTurnLivePresentationAuthorityChanged(
				target = target,
				initialReceipt = initial,
				finalReceipt = refreshed.copy(pageIndex = target.pageIndex + 1),
				isStillCurrent = true
			)
		)
		assertFalse(
			readerPageTurnLivePresentationAuthorityChanged(
				target = target,
				initialReceipt = initial,
				finalReceipt = refreshed,
				isStillCurrent = false
			)
		)
	}

	@Test
	fun windowPixelCopySuccessTransfersCandidateWithoutRelease() {
		val candidate = PresentedCandidate()
		var releases = 0
		val ownership = ReaderPageTurnLiveCaptureOwnership<PresentedCandidate> {
			releases += 1
		}

		assertTrue(ownership.retain(candidate))
		assertTrue(ownership.beginExternalWrite())
		var bitmapTouches = 0
		assertTrue(ownership.externalWriteIsCurrent())
		assertTrue(ownership.runIfExternalWriteCurrent { bitmapTouches += 1 })
		assertEquals(1, bitmapTouches)
		assertTrue(ownership.endExternalWrite())
		assertSame(candidate, ownership.finish(accepted = true)?.candidate)
		assertEquals(0, releases)
		assertFalse(ownership.cancel())
	}

	@Test
	fun refreshedLivePresentationAuthorityReleasesCandidateAndKeepsCaptureOpen() {
		val first = PresentedCandidate()
		val second = PresentedCandidate()
		var releases = 0
		val ownership = ReaderPageTurnLiveCaptureOwnership<PresentedCandidate> {
			releases += 1
		}

		assertTrue(ownership.retain(first))
		assertTrue(ownership.releaseCandidateForRetry())
		assertEquals(1, releases)
		assertTrue(ownership.isOpen)
		assertTrue(ownership.retain(second))
		assertSame(second, ownership.finish(accepted = true)?.candidate)
		assertEquals(1, releases)
	}

	@Test
	fun windowPixelCopyErrorReleasesCandidateExactlyOnce() {
		val candidate = PresentedCandidate()
		var releases = 0
		val ownership = ReaderPageTurnLiveCaptureOwnership<PresentedCandidate> {
			releases += 1
		}

		assertTrue(ownership.retain(candidate))
		assertTrue(ownership.beginExternalWrite())
		assertTrue(ownership.endExternalWrite())
		assertNull(ownership.finish(accepted = false)?.candidate)
		assertEquals(1, releases)
		assertFalse(ownership.cancel())
		assertEquals(1, releases)
	}

	@Test
	fun callbackAfterLiveCaptureCancellationCannotPublishOrDoubleRelease() {
		val candidate = PresentedCandidate()
		var releases = 0
		val ownership = ReaderPageTurnLiveCaptureOwnership<PresentedCandidate> {
			releases += 1
		}

		assertTrue(ownership.retain(candidate))
		assertTrue(ownership.cancel())
		assertEquals(1, releases)
		assertNull(ownership.finish(accepted = true))
		assertEquals(1, releases)
	}

	@Test
	fun cancellationDuringWindowPixelCopyDefersReleaseUntilExternalWriteCompletes() {
		val candidate = PresentedCandidate()
		var releases = 0
		val ownership = ReaderPageTurnLiveCaptureOwnership<PresentedCandidate> {
			releases += 1
		}

		assertTrue(ownership.retain(candidate))
		assertTrue(ownership.beginExternalWrite())
		assertTrue(ownership.externalWriteIsCurrent())
		assertTrue(ownership.cancel())
		assertEquals(0, releases)
		assertFalse(ownership.externalWriteIsCurrent())
		var bitmapTouches = 0
		assertFalse(ownership.runIfExternalWriteCurrent { bitmapTouches += 1 })
		assertEquals(0, bitmapTouches)

		assertFalse(ownership.endExternalWrite())
		assertEquals(1, releases)
		assertNull(ownership.finish(accepted = true))
		assertFalse(ownership.cancel())
		assertEquals(1, releases)
	}

	@Test
	fun sourceContractKeepsApiOAvailabilityAndUsesCanonicalRendererSurface() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()

		assertTrue(source.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.O"))
		assertTrue(source.contains("import karacken.curl.PageSurfaceView"))
		assertFalse(source.contains("ContextWrapper"))
		assertFalse(source.contains("findActivity()"))
	}

	@Test
	fun rendererPresentationSchedulingExceptionsRejectLiveCapture() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val liveCapture = source
			.substringAfter("fun captureLiveCompositedSurface(")
			.substringBefore("fun confirmLivePresentationReceipt(")

		assertTrue(liveCapture.contains("val requestId = runCatching {"))
		assertTrue(liveCapture.contains("rendererSurface.requestNextPresentedFrame"))
		assertTrue(liveCapture.contains("requestId == null"))
		assertTrue(liveCapture.contains("reject()"))
	}

	@Test
	fun presentationReceiptJavascriptRequestExceptionIsRejected() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageTurnBitmapSource.android.kt"
		).readText()
		val query = source
			.substringAfter("private fun queryPresentationReceipt(")
			.substringBefore("private fun canCapture(")

		assertTrue(query.contains("try {"))
		assertTrue(query.contains("catch (_: Throwable)"))
		assertTrue(query.contains("onReceipt(null)"))
	}

	@Test
	fun canceledPresentedCaptureReleasesRetainedCandidateExactlyOnce() {
		val candidate = PresentedCandidate()
		var releases = 0
		val ownership = ReaderPageTurnPresentedCaptureOwnership<PresentedCandidate> {
			releases += 1
		}
		assertTrue(ownership.retain(candidate))
		assertEquals(1, ownership.retainedCandidateCount)

		assertTrue(ownership.cancel())
		assertEquals(0, ownership.retainedCandidateCount)
		assertEquals(1, releases)
		assertNull(ownership.complete())
		assertFalse(ownership.cancel())
		assertEquals(1, releases)
	}

	@Test
	fun threeTimedOutPresentedCapturesCannotRetainCandidates() {
		var releases = 0
		val owners = List(3) {
			ReaderPageTurnPresentedCaptureOwnership<PresentedCandidate> {
				releases += 1
			}.also { owner ->
				assertTrue(owner.retain(PresentedCandidate()))
			}
		}

		owners.forEach { owner -> assertTrue(owner.cancel()) }
		assertEquals(3, releases)
		assertEquals(0, owners.sumOf { it.retainedCandidateCount })
		owners.forEach { owner ->
			assertNull(owner.complete())
			assertFalse(owner.cancel())
		}
		assertEquals(3, releases)
	}

	@Test
	fun missingInitialReceiptRejectsAndRecyclesTheCandidateExactlyOnce() {
		val candidate = PresentedCandidate()
		var recycleCount = 0

		val accepted = readerPageTurnPresentedSurfaceCandidate(
			target = previewTarget(),
			initialReceipt = null,
			finalReceipt = previewReceipt(),
			candidate = candidate,
			foregroundSuccess = true,
			isStillCurrent = true,
			recycle = { recycleCount += 1 }
		)

		assertNull(accepted)
		assertEquals(1, recycleCount)
	}

	@Test
	fun receiptForAnotherTargetRejectsTheCandidate() {
		val candidate = PresentedCandidate()
		val mismatched = previewReceipt().copy(token = "neutral-preview-beta")
		var recycleCount = 0

		val accepted = readerPageTurnPresentedSurfaceCandidate(
			target = previewTarget(),
			initialReceipt = mismatched,
			finalReceipt = mismatched,
			candidate = candidate,
			foregroundSuccess = true,
			isStillCurrent = true,
			recycle = { recycleCount += 1 }
		)

		assertNull(accepted)
		assertEquals(1, recycleCount)
	}

	@Test
	fun changedFinalPresentationSequenceRejectsTheCandidate() {
		val candidate = PresentedCandidate()
		var recycleCount = 0

		val accepted = readerPageTurnPresentedSurfaceCandidate(
			target = previewTarget(),
			initialReceipt = previewReceipt(),
			finalReceipt = previewReceipt().copy(presentationSequence = 42),
			candidate = candidate,
			foregroundSuccess = true,
			isStillCurrent = true,
			recycle = { recycleCount += 1 }
		)

		assertNull(accepted)
		assertEquals(1, recycleCount)
	}

	@Test
	fun foregroundFailureRejectsTheCandidate() {
		val candidate = PresentedCandidate()
		var recycleCount = 0

		val accepted = readerPageTurnPresentedSurfaceCandidate(
			target = previewTarget(),
			initialReceipt = previewReceipt(),
			finalReceipt = previewReceipt(),
			candidate = candidate,
			foregroundSuccess = false,
			isStillCurrent = true,
			recycle = { recycleCount += 1 }
		)

		assertNull(accepted)
		assertEquals(1, recycleCount)
	}

	@Test
	fun canceledCaptureRejectsTheCandidate() {
		val candidate = PresentedCandidate()
		var recycleCount = 0

		val accepted = readerPageTurnPresentedSurfaceCandidate(
			target = previewTarget(),
			initialReceipt = previewReceipt(),
			finalReceipt = previewReceipt(),
			candidate = candidate,
			foregroundSuccess = true,
			isStillCurrent = false,
			recycle = { recycleCount += 1 }
		)

		assertNull(accepted)
		assertEquals(1, recycleCount)
	}

	@Test
	fun stableCurrentForegroundCandidateIsDeliveredWithoutRecycling() {
		val candidate = PresentedCandidate()
		var recycleCount = 0

		val accepted = readerPageTurnPresentedSurfaceCandidate(
			target = previewTarget(),
			initialReceipt = previewReceipt(),
			finalReceipt = previewReceipt(),
			candidate = candidate,
			foregroundSuccess = true,
			isStillCurrent = true,
			recycle = { recycleCount += 1 }
		)

		assertSame(candidate, accepted)
		assertEquals(0, recycleCount)
	}

	@Test
	fun uniformSurfaceIsRejected() {
		assertFalse(
			readerPageTurnPixelsContainForeground(
				IntArray(1_120) { ReaderPageTestBackground }
			)
		)
	}

	@Test
	fun sparseRenderedForegroundIsAccepted() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestForeground
		pixels[106] = ReaderPageTestForeground
		pixels[107] = ReaderPageTestForeground

		assertTrue(readerPageTurnPixelsContainForeground(pixels))
	}

	@Test
	fun fewerThanThreeForegroundSamplesAreRejected() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestForeground
		pixels[106] = ReaderPageTestForeground

		assertFalse(readerPageTurnPixelsContainForeground(pixels))
	}

	@Test
	fun stableSparseSurfaceIsAcceptedOnlyAfterASecondObservation() {
		val first = IntArray(1_120) { ReaderPageTestBackground }
		first[105] = ReaderPageTestForeground
		val second = first.copyOf()

		assertFalse(readerPageTurnPixelsContainForeground(first))
		assertTrue(readerPageTurnSparseForegroundSettled(first, second))
	}

	@Test
	fun changingSparseSurfaceIsNotAcceptedAsSettled() {
		val first = IntArray(1_120) { ReaderPageTestBackground }
		val second = first.copyOf()
		first[105] = ReaderPageTestForeground
		second[106] = ReaderPageTestForeground

		assertFalse(readerPageTurnSparseForegroundSettled(first, second))
	}

	@Test
	fun stableUniformSurfaceIsNotAcceptedAsSettled() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }

		assertFalse(readerPageTurnSparseForegroundSettled(pixels, pixels.copyOf()))
	}

	@Test
	fun lowContrastSurfaceIsRejected() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestLowContrast
		pixels[106] = ReaderPageTestLowContrast
		pixels[107] = ReaderPageTestLowContrast

		assertFalse(readerPageTurnPixelsContainForeground(pixels))
	}

	@Test
	fun stableLowContrastSurfaceIsAcceptedOnlyAfterASecondObservation() {
		val first = IntArray(1_120) { ReaderPageTestBackground }
		first[105] = ReaderPageTestLowContrast
		first[106] = ReaderPageTestLowContrast
		first[107] = ReaderPageTestLowContrast
		val second = first.copyOf()

		assertFalse(readerPageTurnPixelsContainForeground(first))
		assertTrue(readerPageTurnLowContrastForegroundSettled(first, second))
	}

	@Test
	fun changingLowContrastSurfaceIsNotAcceptedAsSettled() {
		val first = IntArray(1_120) { ReaderPageTestBackground }
		val second = first.copyOf()
		first[105] = ReaderPageTestLowContrast
		second[106] = ReaderPageTestLowContrast

		assertFalse(readerPageTurnLowContrastForegroundSettled(first, second))
	}

	@Test
	fun stableSinglePixelLowContrastArtifactIsRejected() {
		val pixels = IntArray(1_120) { ReaderPageTestBackground }
		pixels[105] = ReaderPageTestLowContrast

		assertFalse(readerPageTurnLowContrastForegroundSettled(pixels, pixels.copyOf()))
	}

	@Test
	fun sparseToLowContrastClassificationDoesNotSettle() {
		val sparse = IntArray(1_120) { ReaderPageTestBackground }
		val lowContrast = sparse.copyOf()
		sparse[105] = ReaderPageTestForeground
		lowContrast[105] = ReaderPageTestLowContrast
		lowContrast[106] = ReaderPageTestLowContrast
		lowContrast[107] = ReaderPageTestLowContrast

		assertFalse(
			readerPageTurnRejectedForegroundSettled(
				previousPixels = sparse,
				currentPixels = lowContrast,
				allowStableLowContrast = true
			)
		)
	}

	@Test
	fun shiftedSamplingFindsForegroundMissedByThePrimaryLandscapeGrid() {
		val width = 480
		val height = 320
		val pixels = IntArray(width * height) { ReaderPageTestBackground }
		listOf(30 to 30, 40 to 40, 50 to 50).forEach { (x, y) ->
			pixels[y * width + x] = ReaderPageTestForeground
		}

		assertTrue(
			readerPageTurnCaptureContainsForeground(width, height) { x, y ->
				pixels[y * width + x]
			}
		)
	}

	@Test
	fun shiftedSamplingStillRejectsLowContrastDecoration() {
		val width = 480
		val height = 320
		val pixels = IntArray(width * height) { ReaderPageTestBackground }
		listOf(30 to 30, 40 to 40, 50 to 50).forEach { (x, y) ->
			pixels[y * width + x] = ReaderPageTestLowContrast
		}

		assertFalse(
			readerPageTurnCaptureContainsForeground(width, height) { x, y ->
				pixels[y * width + x]
			}
		)
	}

	private fun previewTarget() = ReaderPageTurnPresentationTarget.Preview(
		token = "neutral-preview-alpha",
		pageIndex = 7,
		previewGeneration = 11
	)

	private fun previewReceipt() = ReaderPageTurnPresentationReceipt(
		scope = ReaderPageTurnPresentationScope.Preview,
		token = "neutral-preview-alpha",
		pageIndex = 7,
		previewGeneration = 11,
		presentationSequence = 41
	)

	private fun liveTarget() = ReaderPageTurnPresentationTarget.Live(
		token = "neutral-live-alpha",
		pageIndex = 7,
		foliateSessionId = "neutral-session-alpha",
		rasterGeneration = 13,
		textureGeneration = 17
	)

	private fun liveReceipt() = ReaderPageTurnPresentationReceipt(
		scope = ReaderPageTurnPresentationScope.Live,
		token = "neutral-live-alpha",
		pageIndex = 7,
		foliateSessionId = "neutral-session-alpha",
		rasterGeneration = 13,
		textureGeneration = 17,
		presentationSequence = 41
	)

	private class PresentedCandidate
}
