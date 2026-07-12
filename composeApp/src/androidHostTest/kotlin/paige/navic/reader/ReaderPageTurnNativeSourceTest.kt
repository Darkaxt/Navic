package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageTurnNativeSourceTest {
	@Test
	fun runtimeExposesResolvedPageRectanglesWithoutMovingFoliate() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		assertContains(runtime, "pageTurnCaptureGeometry: () => runtime.pageTurnCaptureGeometry()")
		assertContains(runtime, "pageTurnCaptureGeometry()")
		assertContains(runtime, "surfacePageDecorationGeometry")
		assertContains(runtime, "reverseFaceColorArgb")
		assertContains(runtime, "readerCssColorToArgb(background)")
		assertContains(runtime, "parseInt(normalized.slice(1), 16)")
		assertFalse(runtime.contains("pageTurnCaptureGeometry() {\n    this.view.renderer.scrollBy"))
	}

	@Test
	fun nativeCaptureUsesExactWindowRectangleAndApi26Gate() {
		val source = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()
		assertContains(source, "Build.VERSION.SDK_INT >= Build.VERSION_CODES.O")
		assertContains(source, "sourceRectInWindow")
		assertContains(source, "webView.getLocationInWindow")
		assertContains(source, "PixelCopy.request")
		assertContains(source, "window")
		assertFalse(source.contains("Rect(0, 0, host.width, host.height)"))
	}

	@Test
	fun overlayIsAttachedOnlyFromSuccessfulCaptureEffect() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val captureCallback = controller.substringAfter("capturePage(")
		val effectHandler = controller.substringAfter("ReaderPageTurnEffect.AttachOverlay")
		assertTrue(captureCallback.contains("captureSucceeded"))
		assertContains(effectHandler, "attachOverlay")
		assertFalse(
			controller.substringBefore("captureSucceeded").contains("addView(curlView"),
			"The animation overlay must not enter the hierarchy before capture succeeds."
		)
	}

	@Test
	fun staleCaptureResultIsRecycledBeforeItCanReachTheOverlay() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val captureCallback = controller.substringAfter("capturePage(webView, direction)")
		val effectsIndex = captureCallback.indexOf("val effects = state.captureSucceeded(generation)")
		val assignmentIndex = captureCallback.indexOf("captureResult = result")

		assertTrue(effectsIndex >= 0, "The controller must validate the capture generation first.")
		assertTrue(
			effectsIndex < assignmentIndex,
			"A stale capture must not become the active capture result."
		)
		assertContains(captureCallback, "if (effects.isEmpty())")
		assertContains(captureCallback, "result.bitmap.takeUnless { it.isRecycled }?.recycle()")
	}

	@Test
	fun staleCaptureFailureCannotDisableCanvasForTheSession() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val failureBranch = controller.substringAfter("if (result == null)").substringBefore("} else {")

		assertContains(failureBranch, "if (state.captureFailed(generation))")
		assertTrue(
			failureBranch.indexOf("if (state.captureFailed(generation))") <
				failureBranch.indexOf("enabledForSession = false"),
			"Only a failure from the active capture generation may disable Canvas mode."
		)
	}

	@Test
	fun rendererUsesNeutralReverseFaceAndCompletesAnimations() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		assertContains(renderer, "reverseFaceColor")
		assertFalse(renderer.contains("setBitmaps(front, front)"))
		assertTrue(
			renderer.indexOf("canvas.drawBitmapMesh") < renderer.indexOf("drawReverseFace(canvas"),
			"The neutral reverse face must cover the folded-away front pixels instead of sitting behind them."
		)
		assertContains(renderer, "canvas.drawRect(")
		assertTrue(
			renderer.indexOf("canvas.drawRect(") < renderer.indexOf("canvas.drawBitmapMesh"),
			"The uncovered turn area must reveal neutral paper rather than duplicate live-page text."
		)
		assertContains(renderer, "drawEdgeHighlight")
		assertContains(controller, "animateCommit")
		assertContains(controller, "animateRelax")
		assertContains(controller, "animationFinished")
	}

	@Test
	fun nativeGesturePreservesExactEdgeOriginAndLivePointerY() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		assertContains(host, "pageTurnController.update(dx, width, swipeStartY, event.y, height)")
		assertContains(host, "pageTurnController.release(dx, width, swipeStartY, event.y, height)")
		assertContains(controller, "edgeOriginY: Float")
		assertContains(controller, "pointerY: Float")
		assertContains(controller, "setGestureY")
		assertContains(controller, "pageAxisWidth(viewWidth)")
		assertContains(controller, "captureResult?.bitmap?.width")
	}

	@Test
	fun nativeHostCancelsAnActiveTurnWhenItsLayoutSizeChanges() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()

		assertContains(host, "override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)")
		assertContains(host, "if (oldw > 0 && oldh > 0 && (w != oldw || h != oldh))")
		assertContains(host, "pageTurnController.cancel()")
	}

	@Test
	fun commitWaitsForNativeAnimationAndFoliatePromiseWithoutDelay() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		assertContains(controller, "armNativePageTurnSettle")
		assertContains(controller, "nativePageTurnSettledToken")
		assertContains(controller, "destinationSettled")
		assertContains(runtime, "armNativePageTurnSettle")
		assertContains(runtime, "nativePageTurnSettledToken")
		assertContains(
			controller,
			"window.NavicReaderBridge?.nativePageTurnSettledToken?.() ?? null"
		)
		assertFalse(
			controller.contains("JSON.stringify(window.NavicReaderBridge?.nativePageTurnSettledToken"),
			"WebView already JSON-encodes evaluateJavascript results; stringifying the token first double-encodes it."
		)
		assertFalse(controller.contains("postDelayed"))
	}

	@Test
	fun spreadOverlayUsesTheWholeHostAndKeepsFinalNativeFrameUntilDetach() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()

		assertContains(controller, "FrameLayout.LayoutParams.MATCH_PARENT")
		assertContains(controller, "pageLeft = left")
		assertContains(controller, "pageTop = top")
		assertContains(controller, "ReaderPageTurnEffect.ShowFinalBase")
		assertContains(controller, "showFinalBase")
		assertFalse(controller.contains("curlView?.setDestinationSettled()"))
		assertContains(renderer, "pageLeft")
		assertContains(renderer, "pageTop")
		assertContains(renderer, "canvas.translate(pageLeft, pageTop)")
		assertContains(renderer, "if (!destinationSettled)")
		assertFalse(
			controller.contains("FrameLayout.LayoutParams(result.bitmap.width, result.bitmap.height)"),
			"A selected-page-sized overlay clips the folded sheet at the gutter."
		)
	}

	@Test
	fun committedTurnAnimatesContinuouslyThenShowsFinalBaseWhileFoliateSettles() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()

		assertContains(controller, "CommitEndProgress = 2f")
		assertContains(controller, "animate(fromProgress, CommitEndProgress, CommitAnimationDurationMs)")
		assertContains(controller, "ReaderPageTurnEffect.ShowFinalBase")
		assertContains(renderer, "showFinalBase")
		assertContains(renderer, "MaxTurnProgress = 2f")
		assertFalse(controller.contains("CommitHoldProgress"))
		assertFalse(controller.contains("commitHoldReached"))
		assertFalse(controller.contains("finishCommitAnimation"))
	}

	@Test
	fun canvasRendererUsesLocalizedTwoAxisFoldInsteadOfFullHeightCylinder() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val geometry = readerCommonFile("ReaderPageTurnEdgeFoldGeometry.kt").readText()
		assertContains(renderer, "ReaderPageTurnEdgeFoldGeometry")
		assertContains(renderer, "geometry.mapInto(baseX, y, vertices, index)")
		assertContains(geometry, "foldBoundarySegment")
		assertContains(renderer, "visibleCreaseSegment")
		assertContains(renderer, "foldedRegionOutline")
		assertContains(geometry, "curlBand")
		assertFalse(renderer.contains("curlBulge"))
		assertFalse(renderer.contains("canvas.drawRect(crease - radius, 0f, crease + radius, pageHeight"))
	}
}
